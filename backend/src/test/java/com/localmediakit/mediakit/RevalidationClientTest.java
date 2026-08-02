package com.localmediakit.mediakit;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failed revalidation is the most misleading state this system can reach: the
 * publish committed, the dashboard says it worked, and the public page may
 * still be the previous snapshot. The log line is the only thing that says so,
 * which makes what it contains worth a test.
 */
class RevalidationClientTest {

    @Test
    void namesTheFailureTypeWhenTheExceptionCarriesNoMessage() {
        // The common case here. Left to getMessage() this logged the word
        // "null", which distinguishes nothing from nothing.
        assertThat(RevalidationClient.describe(new ConnectException()))
                .isEqualTo("ConnectException");
    }

    @Test
    void keepsBothTheTypeAndTheMessageWhenThereIsOne() {
        assertThat(RevalidationClient.describe(new HttpTimeoutException("request timed out")))
                .isEqualTo("HttpTimeoutException: request timed out");
    }

    @Test
    void treatsABlankMessageAsNoMessage() {
        assertThat(RevalidationClient.describe(new ConnectException("   ")))
                .isEqualTo("ConnectException");
    }
}
