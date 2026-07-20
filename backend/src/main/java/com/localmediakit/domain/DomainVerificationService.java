package com.localmediakit.domain;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Custom-domain verification: registration, the async batch that the scheduled
 * job drives, and the owner-triggered manual re-check. All domain lookups go
 * through {@link DnsResolver}, so the state-machine logic is testable without
 * touching real DNS.
 */
@Service
public class DomainVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DomainVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");

    private final CustomDomainRepository repository;
    private final MediaKitAccess access;
    private final PlanPolicy planPolicy;
    private final DnsResolver dnsResolver;
    private final TransactionTemplate transactionTemplate;
    private final ReentrancyGuard batchGuard;
    private final String verifyHostPrefix;
    private final int maxAttempts;
    private final Duration recheckInterval;

    public DomainVerificationService(CustomDomainRepository repository,
                                     MediaKitAccess access,
                                     PlanPolicy planPolicy,
                                     DnsResolver dnsResolver,
                                     TransactionTemplate transactionTemplate,
                                     ReentrancyGuard batchGuard,
                                     @Value("${app.domains.verify-host-prefix:_localmediakit-verify}") String verifyHostPrefix,
                                     @Value("${app.domains.max-attempts:30}") int maxAttempts,
                                     @Value("${app.domains.recheck-interval-ms:60000}") long recheckIntervalMs) {
        this.repository = repository;
        this.access = access;
        this.planPolicy = planPolicy;
        this.dnsResolver = dnsResolver;
        this.transactionTemplate = transactionTemplate;
        this.batchGuard = batchGuard;
        this.verifyHostPrefix = verifyHostPrefix;
        this.maxAttempts = maxAttempts;
        this.recheckInterval = Duration.ofMillis(recheckIntervalMs);
    }

    public String verifyHostPrefix() {
        return verifyHostPrefix;
    }

    // --- registration / management (PRO, owner-scoped) ---

    @Transactional
    public CustomDomain addDomain(String userEmail, Long kitId, String rawDomain) {
        User user = access.requireUser(userEmail);
        planPolicy.assertCustomDomainAllowed(user.getPlan());
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);

        String domain = normalize(rawDomain);
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new InvalidDomainException("Not a valid domain name: " + rawDomain);
        }
        // Idempotent for the same kit; conflict if another kit owns it.
        var existing = repository.findByDomain(domain);
        if (existing.isPresent()) {
            if (existing.get().getMediaKitId().equals(kit.getId())) {
                return existing.get();
            }
            throw new DomainAlreadyExistsException();
        }
        return repository.save(new CustomDomain(kit.getId(), domain, newToken()));
    }

    @Transactional(readOnly = true)
    public List<CustomDomain> listDomains(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return repository.findByMediaKitIdOrderByCreatedAtDesc(kit.getId());
    }

    @Transactional
    public void removeDomain(String userEmail, Long kitId, Long domainId) {
        repository.delete(requireOwnedDomain(userEmail, kitId, domainId));
    }

    /** Owner-triggered manual re-check. Reuses the exact batch verification logic. */
    @Transactional
    public CustomDomain checkNow(String userEmail, Long kitId, Long domainId) {
        CustomDomain domain = requireOwnedDomain(userEmail, kitId, domainId);
        if (domain.getStatus() == DomainStatus.VERIFIED) {
            return domain; // idempotent: nothing to do
        }
        if (domain.getStatus() == DomainStatus.FAILED) {
            domain.resetForRetry();
        }
        evaluate(domain);
        return domain;
    }

    private CustomDomain requireOwnedDomain(String userEmail, Long kitId, Long domainId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return repository.findByIdAndMediaKitId(domainId, kit.getId())
                .orElseThrow(DomainNotFoundException::new);
    }

    // --- async batch (driven by the scheduled job) ---

    /**
     * Verifies all PENDING domains due for a check. Overlap-guarded, and each
     * domain runs in its own transaction with its own try/catch, so one broken
     * domain neither aborts the batch nor rolls back the others.
     *
     * @return how many domains were processed, or -1 if skipped (already running).
     */
    public int runVerificationBatch() {
        int[] processed = {0};
        boolean ran = batchGuard.tryRun(() -> {
            List<Long> dueIds = transactionTemplate.execute(status ->
                    repository.findPendingDue(Instant.now().minus(recheckInterval))
                            .stream().map(CustomDomain::getId).toList());
            if (dueIds == null) {
                return;
            }
            for (Long id : dueIds) {
                try {
                    transactionTemplate.executeWithoutResult(status -> evaluateById(id));
                    processed[0]++;
                } catch (Exception e) {
                    // One domain must never bring the batch down.
                    log.warn("Domain verification failed for id {}: {}", id, e.getMessage());
                }
            }
        });
        return ran ? processed[0] : -1;
    }

    private void evaluateById(Long id) {
        repository.findById(id)
                .filter(d -> d.getStatus() == DomainStatus.PENDING)
                .ifPresent(this::evaluate);
    }

    /**
     * The pure state transition: look up the TXT record and move the domain to
     * VERIFIED, or record a failed attempt (which may exhaust the budget into
     * FAILED). DNS infrastructure errors are treated as "not found yet", never
     * as a crash.
     */
    void evaluate(CustomDomain domain) {
        boolean matched;
        try {
            List<String> txtRecords = dnsResolver.lookupTxt(verifyHostPrefix + "." + domain.getDomain());
            matched = txtRecords.stream().anyMatch(v -> v.equals(domain.getVerificationToken()));
        } catch (DnsLookupException e) {
            log.debug("DNS lookup error for {}: {}", domain.getDomain(), e.getMessage());
            matched = false;
        }
        if (matched) {
            domain.markVerified();
        } else {
            domain.recordFailedAttempt(maxAttempts);
        }
    }

    // --- helpers ---

    private String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^https?://", "");
        s = s.replaceFirst("/.*$", "");
        s = s.replaceFirst("\\.$", "");
        return s;
    }

    private String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "lmk-verify-" + HexFormat.of().formatHex(bytes);
    }
}
