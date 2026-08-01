package com.localmediakit.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rule is only armed in the prod profile, which no test boots, so it is
 * exercised directly here — and the last test checks the other half of the
 * arrangement: that the defaults it looks for still carry the marker.
 */
class ProductionSecretsCheckTest {

    @Test
    void acceptsARealSecret() {
        assertThatCode(() -> ProductionSecretsCheck.requireRealSecret(
                "JWT_SECRET", "0f2c9a4e7b1d8365f0a2c4e6b8d0f2a4"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTheDevelopmentDefault() {
        assertThatThrownBy(() -> ProductionSecretsCheck.requireRealSecret(
                "JWT_SECRET", "local-dev-jwt-secret-change-me-at-least-32-bytes-long"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsAnUnsetOrBlankVariable() {
        assertThatThrownBy(() -> ProductionSecretsCheck.requireRealSecret("ANALYTICS_SALT", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionSecretsCheck.requireRealSecret("ANALYTICS_SALT", "   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neverEchoesTheValueItRejected() {
        // A startup log is not a place to print a secret, not even a wrong one.
        assertThatThrownBy(() -> ProductionSecretsCheck.requireRealSecret(
                "REVALIDATE_SECRET", "local-dev-secret"))
                .hasMessageNotContaining("local-dev-secret");
    }

    /**
     * The check recognises development defaults by their prefix, so a default
     * that quietly loses it would disarm the check without failing anything.
     * This reads the shipped configuration and asserts the convention holds.
     */
    @Test
    void everyGuardedDefaultStillCarriesTheMarker() throws IOException {
        String config = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(config).contains("${JWT_SECRET:" + ProductionSecretsCheck.DEV_MARKER);
        assertThat(config).contains("${REVALIDATE_SECRET:" + ProductionSecretsCheck.DEV_MARKER);
        assertThat(config).contains("${ANALYTICS_SALT:" + ProductionSecretsCheck.DEV_MARKER);
    }

    /* --- the wiring, not just the rule --- */

    private ApplicationContextRunner prodContext() {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(ProductionSecretsCheck.class);
    }

    /**
     * A mistyped property key would make the bean fail to resolve its
     * placeholders — which looks like a working guard while actually refusing
     * every configuration, correct ones included. Booting it against real values
     * is what tells the two apart.
     */
    @Test
    void startsWhenTheEnvironmentIsConfigured() {
        prodContext()
                .withPropertyValues(
                        "app.jwt.secret=0f2c9a4e7b1d8365f0a2c4e6b8d0f2a4",
                        "app.revalidate.secret=a-real-revalidate-secret",
                        "app.analytics.salt=a-real-analytics-salt")
                .run(context -> assertThat(context).hasSingleBean(ProductionSecretsCheck.class));
    }

    @Test
    void refusesToStartWhenASecretIsStillTheDevelopmentDefault() {
        prodContext()
                .withPropertyValues(
                        "app.jwt.secret=0f2c9a4e7b1d8365f0a2c4e6b8d0f2a4",
                        "app.revalidate.secret=a-real-revalidate-secret",
                        "app.analytics.salt=local-dev-analytics-salt")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        // Spring wraps it, so the operator reads the reason in the
                        // cause chain rather than in the first line.
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("ANALYTICS_SALT"));
    }

    /** Outside prod the guard must stay out of the way: local dev has no secrets. */
    @Test
    void isNotArmedOutsideProduction() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionSecretsCheck.class)
                .withPropertyValues(
                        "app.jwt.secret=local-dev-jwt-secret-change-me-at-least-32-bytes-long",
                        "app.revalidate.secret=local-dev-secret",
                        "app.analytics.salt=local-dev-analytics-salt")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
