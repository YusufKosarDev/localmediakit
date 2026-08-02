package com.localmediakit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Refuses to start production while a development secret is still in place.
 *
 * <p>Every security-critical secret in application.yml has a working default, so
 * that a clone of this repository runs with zero setup. That convenience has a
 * sharp edge: if an environment variable is missing in production — never set,
 * renamed, dropped while editing the host's dashboard — nothing fails. The
 * application starts, serves traffic, and signs its session tokens with a secret
 * that is committed to a public repository. Anyone could then mint a token for
 * any account. The same silence applies to the revalidation secret (a stranger
 * could evict any page from the edge) and to the analytics salt (visitor hashes
 * become guessable, which is the one property that makes them anonymous).
 *
 * <p>A missing secret must therefore be an outage, not a quiet downgrade: an
 * instance that will not boot is visible in seconds, an instance running on a
 * published key can go unnoticed indefinitely.
 *
 * <p>The check is a marker, not a list of known values. Copying each default
 * here would mean two places to edit and one of them silently going stale;
 * instead every development default is prefixed {@value #DEV_MARKER} and this
 * class rejects the prefix. A secret added later inherits the protection just by
 * following the convention — which is why the prefix is spelled out in
 * application.yml rather than left as an accident of naming.
 */
@Configuration
@Profile("prod")
public class ProductionSecretsCheck {

    /** Prefix carried by every development default in application.yml. */
    static final String DEV_MARKER = "local-dev-";

    public ProductionSecretsCheck(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.revalidate.secret}") String revalidateSecret,
            @Value("${app.analytics.salt}") String analyticsSalt) {
        // Ordered, so the message reads the same way twice and a fix can be
        // checked off against it.
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("JWT_SECRET", jwtSecret);
        secrets.put("REVALIDATE_SECRET", revalidateSecret);
        secrets.put("ANALYTICS_SALT", analyticsSalt);
        requireRealSecrets(secrets);
    }

    /**
     * Package-private and static so the rule can be tested without booting a
     * production context.
     *
     * <p>Every variable is checked before anything is thrown, and the failure
     * names all of them. Stopping at the first one is correct but expensive to
     * act on: a deploy on a free tier is minutes of image build, so one missing
     * variable per attempt turns a five-minute fix into an afternoon of
     * discovering them one at a time. The operator should be able to read the
     * list once, set what is on it, and deploy once.
     *
     * <p>Names only. A startup log is not a place to print a secret, including
     * the wrong one.
     */
    static void requireRealSecrets(Map<String, String> valuesByEnvironmentVariable) {
        List<String> missing = valuesByEnvironmentVariable.entrySet().stream()
                .filter(entry -> isUnsetOrDevelopmentDefault(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start: " + String.join(", ", missing)
                            + (missing.size() == 1 ? " is" : " are")
                            + " unset or still holding the development default. Set "
                            + (missing.size() == 1 ? "it" : "them")
                            + " to a real secret in this environment.");
        }
    }

    private static boolean isUnsetOrDevelopmentDefault(String value) {
        return value == null || value.isBlank() || value.startsWith(DEV_MARKER);
    }
}
