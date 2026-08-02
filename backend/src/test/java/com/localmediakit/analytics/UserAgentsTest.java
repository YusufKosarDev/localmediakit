package com.localmediakit.analytics;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bot gate shared by every anonymous public write path -- the view beacon
 * and the brand contact form.
 *
 * <p>It is the only thing standing between a creator's analytics and the
 * crawlers that hit a public page far more often than people do, and between
 * their inbox and the same traffic filling in a form. A mutant that weakened it
 * would not fail any flow test: the numbers would simply be wrong.
 */
class UserAgentsTest {

    @Test
    void obviousAutomationIsRejected() {
        assertThat(UserAgents.isBot("Googlebot/2.1 (+http://www.google.com/bot.html)")).isTrue();
        assertThat(UserAgents.isBot("Mozilla/5.0 (compatible; bingbot/2.0)")).isTrue();
        assertThat(UserAgents.isBot("Twitterbot/1.0")).isTrue();
        assertThat(UserAgents.isBot("curl/8.4.0")).isTrue();
        assertThat(UserAgents.isBot("Wget/1.21.3")).isTrue();
        assertThat(UserAgents.isBot("python-requests/2.31.0")).isTrue();
        assertThat(UserAgents.isBot("HeadlessChrome/120.0.0.0")).isTrue();
    }

    @Test
    void linkPreviewFetchersAreNotVisits() {
        // A message with a kit link in it makes chat apps fetch the page. Nobody
        // has looked at it yet, so counting it would report an audience that
        // does not exist.
        assertThat(UserAgents.isBot("WhatsApp/2.23 Preview")).isTrue();
    }

    @Test
    void aMissingUserAgentIsTreatedAsAutomation() {
        // Real browsers always send one. Defaulting the other way would make an
        // empty header the cheapest way to inflate someone's numbers.
        assertThat(UserAgents.isBot(null)).isTrue();
        assertThat(UserAgents.isBot("")).isTrue();
        assertThat(UserAgents.isBot("   ")).isTrue();
    }

    @Test
    void realBrowsersGetThrough() {
        assertThat(UserAgents.isBot(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")).isFalse();
        assertThat(UserAgents.isBot(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                        + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"))
                .isFalse();
    }

    @Test
    void detectionDoesNotDependOnTheServersLocale() {
        // "I" folds to a dotless "i" on a Turkish JVM, so a locale-sensitive
        // fold here would change which agents match on which server.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(UserAgents.isBot("SOME-CRAWLER/1.0")).isTrue();
            assertThat(UserAgents.isBot("SPIDER")).isTrue();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void devicesAreSplitByTheMarkersBrowsersActuallySend() {
        assertThat(UserAgents.device("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")).isEqualTo("MOBILE");
        assertThat(UserAgents.device("Mozilla/5.0 (Linux; Android 14) Mobile Safari")).isEqualTo("MOBILE");
        assertThat(UserAgents.device("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).isEqualTo("DESKTOP");
        assertThat(UserAgents.device("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)")).isEqualTo("DESKTOP");
    }
}
