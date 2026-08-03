package com.localmediakit.recovery;

import com.localmediakit.notification.MailDeliveryException;
import com.localmediakit.notification.MailSender;
import com.localmediakit.notification.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reset mail as an outbox: queued by the request, delivered by a batch.
 *
 * <p>Two properties are the reason this exists, and neither is "the mail
 * arrives". The first is that the request does no network work, so an address
 * with an account and one without take the same time to answer — the inline
 * send made them measurably different, which is a membership oracle. The
 * second is that nothing which can log in is ever written down: the queue holds
 * a reference to the token row, and the secret is minted at send time.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetOutboxTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordResetNotificationService dispatcher;

    @Autowired
    private PasswordResetNotificationRepository notifications;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @MockBean
    private MailSender mailSender;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * An outbox is shared state, and the batch drains all of it. Without this,
     * a test's {@code runDispatchBatch} would deliver rows queued by whichever
     * tests happened to run before it, and the counts every assertion here
     * rests on would be measuring the class rather than the case.
     */
    @BeforeEach
    void emptyQueueAndConfiguredMailer() {
        transactionTemplate.executeWithoutResult(status -> {
            notifications.deleteAllInBatch();
            tokens.deleteAllInBatch();
        });
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
    }

    /**
     * Brings a backed-off row forward instead of sleeping through 1, 5 and 25
     * real minutes. Done with a query rather than a setter so the entity keeps
     * no method that exists only for tests to move its clock.
     */
    private void makeDueNow(Long notificationId) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("""
                        update PasswordResetNotification n
                        set n.nextAttemptAt = :past where n.id = :id""")
                .setParameter("past", Instant.now().minusSeconds(60))
                .setParameter("id", notificationId)
                .executeUpdate());
    }

    @Test
    void theRequestQueuesAndSendsNothing() throws Exception {
        register("outbox-queue@example.com");

        requestReset("outbox-queue@example.com").andExpect(status().isAccepted());

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
        PasswordResetNotification queued = onlyPending();
        assertThat(queued.getRecipientEmail()).isEqualTo("outbox-queue@example.com");
        assertThat(queued.getAttempts()).isZero();
    }

    @Test
    void theBatchDeliversItAndTheRowGoesQuiet() throws Exception {
        register("outbox-send@example.com");
        requestReset("outbox-send@example.com");

        assertThat(dispatcher.runDispatchBatch()).isEqualTo(1);

        verify(mailSender, times(1)).send(anyString(), anyString(), anyString());
        PasswordResetNotification row = notifications.findAll().stream()
                .filter(n -> n.getRecipientEmail().equals("outbox-send@example.com"))
                .findFirst().orElseThrow();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
        // A drained queue is not work: the next run must find nothing.
        assertThat(dispatcher.runDispatchBatch()).isZero();
    }

    @Test
    void aRefusedSendIsRetriedBehindABackoff() throws Exception {
        smtpIsDown();
        register("outbox-retry@example.com");
        requestReset("outbox-retry@example.com");

        dispatcher.runDispatchBatch();

        PasswordResetNotification row = rowFor("outbox-retry@example.com");
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).contains("smtp down");
        // Still queued, but not immediately: a provider that just refused will
        // refuse again a millisecond later, and hammering it is how a blip
        // becomes a ban.
        assertThat(row.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void theBudgetRunsOutAndTheRowStopsRetrying() throws Exception {
        smtpIsDown();
        register("outbox-terminal@example.com");
        requestReset("outbox-terminal@example.com");

        // Drive the attempts directly: the point is the terminal state, and
        // waiting out 1 + 5 + 25 real minutes to see it would be a test nobody
        // runs.
        PasswordResetNotification row = rowFor("outbox-terminal@example.com");
        for (int i = 0; i < PasswordResetNotification.MAX_ATTEMPTS; i++) {
            row.markAttemptFailed("smtp down");
        }

        assertThat(row.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(PasswordResetNotification.MAX_ATTEMPTS);
        notifications.save(row);
        // Terminal means terminal: the batch must not pick it up again.
        assertThat(dispatcher.runDispatchBatch()).isZero();
    }

    /**
     * The reason a late mail is still worth sending.
     *
     * <p>Had the token been minted with the request and stored, the last retry
     * would land around thirty-one minutes later carrying a link that expired
     * at thirty. Minting per attempt means the clock starts when the mail does.
     */
    @Test
    void eachAttemptMailsAFreshTokenThatStillWorks() throws Exception {
        smtpIsDown();
        register("outbox-rotate@example.com");
        requestReset("outbox-rotate@example.com");

        dispatcher.runDispatchBatch();
        String firstAttempt = tokenFromLastMail();

        // Make the row due again without waiting out the backoff, then let a
        // working provider have it.
        PasswordResetNotification row = rowFor("outbox-rotate@example.com");
        Instant expiryBefore = tokens.findById(row.getTokenId()).orElseThrow().getExpiresAt();
        makeDueNow(row.getId());
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
        dispatcher.runDispatchBatch();
        String secondAttempt = tokenFromLastMail();

        assertThat(secondAttempt).isNotEqualTo(firstAttempt);
        // The retry's link is the live one, and the dead attempt's is dead.
        confirm(secondAttempt, "yenisifre999").andExpect(status().isNoContent());
        assertThat(tokens.findById(row.getTokenId()).orElseThrow().getExpiresAt())
                .isAfter(expiryBefore);
    }

    @Test
    void aRotationDoesNotSpendTheHourlyBudget() throws Exception {
        // One row per request, rotated in place. Minting a new row per attempt
        // would count against the per-account cap, so a provider outage would
        // lock somebody out of asking again at the exact moment they needed to.
        smtpIsDown();
        register("outbox-cap@example.com");
        requestReset("outbox-cap@example.com");
        long afterRequest = tokens.count();

        dispatcher.runDispatchBatch();
        makeDueNow(rowFor("outbox-cap@example.com").getId());
        dispatcher.runDispatchBatch();

        assertThat(tokens.count()).isEqualTo(afterRequest);
    }

    @Test
    void theQueueHoldsAReferenceRatherThanASecret() throws Exception {
        register("outbox-secret@example.com");
        requestReset("outbox-secret@example.com");
        dispatcher.runDispatchBatch();
        String mailed = tokenFromLastMail();

        PasswordResetNotification row = rowFor("outbox-secret@example.com");
        // Everything the row carries, and none of it is usable as a link.
        assertThat(row.getRecipientEmail()).doesNotContain(mailed);
        assertThat(row.getLocale()).doesNotContain(mailed);
        assertThat(String.valueOf(row.getLastError())).doesNotContain(mailed);
        // What it does carry is a pointer to the row that stores only a hash.
        assertThat(tokens.findById(row.getTokenId())).isPresent();
        assertThat(tokens.findByTokenHash(PasswordResetService.hash(mailed)))
                .map(PasswordResetToken::getId)
                .contains(row.getTokenId());
    }

    @Test
    void aTokenRedeemedBeforeTheQueueDrainsIsNotReissued() throws Exception {
        register("outbox-used@example.com");
        requestReset("outbox-used@example.com");
        dispatcher.runDispatchBatch();
        String mailed = tokenFromLastMail();
        confirm(mailed, "yenisifre123").andExpect(status().isNoContent());

        // Force the drained row back into the queue: a stale attempt must not
        // mint a working secret for a reset the account has already completed.
        PasswordResetNotification row = rowFor("outbox-used@example.com");
        row.markAttemptFailed("forced retry");
        notifications.save(row);
        makeDueNow(row.getId());
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);

        dispatcher.runDispatchBatch();

        assertThat(rowFor("outbox-used@example.com").getStatus())
                .isEqualTo(NotificationStatus.SUPPRESSED);
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    /* --- helpers --- */

    private void smtpIsDown() {
        doThrow(new MailDeliveryException("smtp down", new RuntimeException()))
                .when(mailSender).send(anyString(), anyString(), anyString());
    }

    private PasswordResetNotification onlyPending() {
        List<PasswordResetNotification> pending = notifications.findAll().stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING)
                .toList();
        assertThat(pending).hasSize(1);
        return pending.get(0);
    }

    private PasswordResetNotification rowFor(String email) {
        return notifications.findAll().stream()
                .filter(n -> n.getRecipientEmail().equals(email))
                .reduce((a, b) -> b)
                .orElseThrow();
    }

    private String tokenFromLastMail() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce())
                .send(anyString(), anyString(), body.capture());
        String text = body.getValue();
        int start = text.indexOf("/reset/") + "/reset/".length();
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"eskisifre123","displayName":"Unutkan"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }
}
