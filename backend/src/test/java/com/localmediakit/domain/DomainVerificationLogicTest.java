package com.localmediakit.domain;

import com.localmediakit.user.PlanPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure verification-logic tests: a controlled DnsResolver drives the state
 * machine without ever touching real DNS. No Spring context.
 */
class DomainVerificationLogicTest {

    /** Fake resolver: returns configured TXT records, or throws for chosen hosts. */
    static class FakeDns implements DnsResolver {
        Map<String, List<String>> records = Map.of();
        String throwFor = null;

        @Override
        public List<String> lookupTxt(String host) {
            if (host.equals(throwFor)) {
                throw new DnsLookupException("boom", new RuntimeException());
            }
            return records.getOrDefault(host, List.of());
        }
    }

    private DomainVerificationService service(FakeDns dns, int maxAttempts) {
        return new DomainVerificationService(
                Mockito.mock(CustomDomainRepository.class),
                Mockito.mock(com.localmediakit.mediakit.MediaKitAccess.class),
                new PlanPolicy(),
                dns,
                Mockito.mock(TransactionTemplate.class),
                new ReentrancyGuard(),
                "_localmediakit-verify",
                maxAttempts,
                60000);
    }

    private CustomDomain pendingDomain() {
        // token is fixed via reflection-free constructor path
        return new CustomDomain(1L, "brand.example", "lmk-verify-abc123");
    }

    @Test
    void matchingTxtRecordVerifies() {
        FakeDns dns = new FakeDns();
        dns.records = Map.of("_localmediakit-verify.brand.example", List.of("lmk-verify-abc123"));
        CustomDomain d = pendingDomain();

        service(dns, 30).evaluate(d);

        assertEquals(DomainStatus.VERIFIED, d.getStatus());
        assertEquals(0, d.getAttempts());
    }

    @Test
    void missingRecordStaysPendingAndCountsAttempt() {
        FakeDns dns = new FakeDns();
        dns.records = Map.of(); // nothing
        CustomDomain d = pendingDomain();

        service(dns, 30).evaluate(d);

        assertEquals(DomainStatus.PENDING, d.getStatus());
        assertEquals(1, d.getAttempts());
    }

    @Test
    void wrongTokenStaysPending() {
        FakeDns dns = new FakeDns();
        dns.records = Map.of("_localmediakit-verify.brand.example", List.of("some-other-token"));
        CustomDomain d = pendingDomain();

        service(dns, 30).evaluate(d);

        assertEquals(DomainStatus.PENDING, d.getStatus());
        assertEquals(1, d.getAttempts());
    }

    @Test
    void attemptsExhaustedTransitionToFailed() {
        FakeDns dns = new FakeDns();
        CustomDomain d = pendingDomain();
        DomainVerificationService svc = service(dns, 3);

        svc.evaluate(d);
        assertEquals(DomainStatus.PENDING, d.getStatus());
        svc.evaluate(d);
        assertEquals(DomainStatus.PENDING, d.getStatus());
        svc.evaluate(d); // 3rd attempt hits the max
        assertEquals(DomainStatus.FAILED, d.getStatus());
        assertEquals(3, d.getAttempts());
    }

    @Test
    void dnsErrorIsToleratedAsNotFound() {
        FakeDns dns = new FakeDns();
        dns.throwFor = "_localmediakit-verify.brand.example";
        CustomDomain d = pendingDomain();

        // Must not propagate; treated as "not verified yet".
        service(dns, 30).evaluate(d);

        assertEquals(DomainStatus.PENDING, d.getStatus());
        assertEquals(1, d.getAttempts());
    }

    @Test
    void multiChunkTxtRecordsAreConsidered() {
        FakeDns dns = new FakeDns();
        dns.records = Map.of("_localmediakit-verify.brand.example",
                List.of("unrelated", "lmk-verify-abc123"));
        CustomDomain d = pendingDomain();

        service(dns, 30).evaluate(d);

        assertEquals(DomainStatus.VERIFIED, d.getStatus());
    }
}
