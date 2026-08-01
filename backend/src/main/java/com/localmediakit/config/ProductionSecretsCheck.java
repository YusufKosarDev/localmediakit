package com.localmediakit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
        requireRealSecret("JWT_SECRET", jwtSecret);
        requireRealSecret("REVALIDATE_SECRET", revalidateSecret);
        requireRealSecret("ANALYTICS_SALT", analyticsSalt);
    }

    /**
     * Package-private and static so the rule can be tested without booting a
     * production context. The failure names the environment variable to set and
     * never echoes the value — a startup log is not a place to print secrets,
     * including the wrong one.
     */
    static void requireRealSecret(String environmentVariable, String value) {
        if (value == null || value.isBlank() || value.startsWith(DEV_MARKER)) {
            throw new IllegalStateException(
                    "Refusing to start: " + environmentVariable + " is unset or still holds the "
                            + "development default. Set it to a real secret in this environment.");
        }
    }
}
