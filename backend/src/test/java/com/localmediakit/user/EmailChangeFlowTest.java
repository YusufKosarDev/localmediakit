package com.localmediakit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changing the address an account signs in with, and proving somebody can read
 * mail there first.
 *
 * <p>What this protects against is not an attacker — the current password
 * already answers that, and is still required. It is a typo. The address is the
 * login, so mistyping it hands the account to a mailbox nobody reads, and no
 * amount of re-authentication catches that.
 *
 * <p>Every batch here is driven explicitly. The scheduler is off for the whole
 * suite (see src/test/resources/application.properties) because the contexts
 * share one in-memory database, so a job ticking anywhere would be draining
 * this outbox while these assertions run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailChangeFlowTest {

    private static final Pattern LINK = Pattern.compile("/confirm-email/(\\S+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailChangeNotificationService dispatcher;

    @Autowired
    private EmailChangeRequestRepository requests;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private MailSender mailSender;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** The outbox is shared state and the batch drains all of it. */
    @BeforeEach
    void emptyQueueAndConfiguredMailer() {
        transactionTemplate.executeWithoutResult(status -> requests.deleteAllInBatch());
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void theAccountDoesNotMoveUntilTheLinkIsOpened() throws Exception {
        String token = register("move-me@example.com");

        requestChange(token, "moved@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true))
                // No replacement session: the account is still the old address.
                .andExpect(jsonPath("$.token").doesNotExist());

        // Nothing has changed, and the old session still works.
        assertThat(users.findByEmail("move-me@example.com")).isPresent();
        assertThat(users.findByEmail("moved@example.com")).isEmpty();
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("move-me@example.com"));
    }

    @Test
    void theLinkGoesToTheNewAddressAndMovesTheAccount() throws Exception {
        String token = register("typo-check@example.com");
        requestChange(token, "correct@example.com");

        dispatchOne();

        // The proof is delivered to the address being claimed, not the one that
        // asked -- an address nobody can read never receives it.
        verify(mailSender).send(eq("correct@example.com"), anyString(), anyString());

        confirm(linkTokenFromMail()).andExpect(status().isNoContent());

        assertThat(users.findByEmail("correct@example.com")).isPresent();
        assertThat(users.findByEmail("typo-check@example.com")).isEmpty();
    }

    /**
     * The old session dies with the address it names, and confirmation does not
     * replace it. Redeeming the link proves control of a mailbox, not ownership
     * of the account; handing back a token would turn a forwarded mail into
     * full access.
     */
    @Test
    void confirmingReturnsNoSessionAndInvalidatesTheOldOne() throws Exception {
        String token = register("session-gone@example.com");
        requestChange(token, "session-new@example.com");
        dispatchOne();

        confirm(linkTokenFromMail())
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("nothing that could act as a session").isEmpty());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLinkWorksOnceOnly() throws Exception {
        String token = register("once@example.com");
        requestChange(token, "once-new@example.com");
        dispatchOne();
        String link = linkTokenFromMail();

        confirm(link).andExpect(status().isNoContent());
        confirm(link).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownLinkIsRefusedWithoutSayingWhy() throws Exception {
        confirm("kesinlikle-gecerli-olmayan-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_CHANGE_TOKEN"));
    }

    /**
     * Asking again supersedes the first ask. Two live links in two inboxes
     * would both move the account and the owner could not say which they meant.
     */
    @Test
    void asecondRequestRetiresTheFirstLink() throws Exception {
        String token = register("twice@example.com");
        requestChange(token, "first-target@example.com");
        dispatchOne();
        String firstLink = linkTokenFromMail();

        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
        requestChange(token, "second-target@example.com");
        dispatchOne();
        String secondLink = linkTokenFromMail();

        confirm(firstLink).andExpect(status().isBadRequest());
        confirm(secondLink).andExpect(status().isNoContent());
        assertThat(users.findByEmail("second-target@example.com")).isPresent();
    }

    /** An address another account already holds is refused before any mail. */
    @Test
    void aTakenAddressIsRefusedBeforeAnythingIsSent() throws Exception {
        register("squatter@example.com");
        String token = register("wants-it@example.com");

        requestChange(token, "squatter@example.com")
                .andExpect(status().isConflict());

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
        assertThat(requests.findAll()).isEmpty();
    }

    /**
     * The reason the token is minted at send time rather than when the change
     * was asked for: the last retry lands half an hour into the backoff, and a
     * link that expired while it queued is worse than no mail at all.
     */
    @Test
    void eachDeliveryAttemptCarriesAFreshLink() throws Exception {
        String token = register("rotate@example.com");
        doThrow(new MailDeliveryException("smtp down", new RuntimeException()))
                .when(mailSender).send(anyString(), anyString(), anyString());
        requestChange(token, "rotate-new@example.com");

        dispatchOne();
        String firstAttempt = linkTokenFromMail();

        EmailChangeRequest row = requests.findAll().get(0);
        makeDueNow(row.getId());

        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());

        dispatchOne();
        String secondAttempt = linkTokenFromMail();

        assertThat(secondAttempt).isNotEqualTo(firstAttempt);
        // The dead attempt is dead, and this is what proves the rotation: the
        // row hashes a different secret now. Asserted before the confirm below,
        // because redeeming a link retires the request either way.
        confirm(firstAttempt).andExpect(status().isBadRequest());
        confirm(secondAttempt).andExpect(status().isNoContent());
    }

    @Test
    void aProviderOutageLeavesTheAccountWhereItWas() throws Exception {
        String token = register("outage@example.com");
        doThrow(new MailDeliveryException("smtp down", new RuntimeException()))
                .when(mailSender).send(anyString(), anyString(), anyString());

        requestChange(token, "outage-new@example.com").andExpect(status().isOk());
        dispatchOne();

        assertThat(requests.findAll().get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(users.findByEmail("outage@example.com"))
                .as("the account is untouched while the mail is stuck").isPresent();
    }

    /* --- helpers --- */

    /**
     * Brings a backed-off row forward instead of sleeping through the real 1,
     * 5 and 25 minutes. A query rather than a setter, so the entity keeps no
     * method that exists only for a test to move its clock.
     */
    private void makeDueNow(Long requestId) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("""
                        update EmailChangeRequest r
                        set r.nextAttemptAt = :past where r.id = :id""")
                .setParameter("past", Instant.now().minusSeconds(60))
                .setParameter("id", requestId)
                .executeUpdate());
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Owner"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private ResultActions requestChange(String sessionToken, String newEmail) throws Exception {
        return mockMvc.perform(post("/api/me/email")
                .header("Authorization", "Bearer " + sessionToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"supersecret","newEmail":"%s"}
                        """.formatted(newEmail)));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/email-change/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    /**
     * Drains the queue and insists it actually had something to drain.
     *
     * <p>Asserting the batch's own return value rather than only its side
     * effects, because the three ways this can go wrong are otherwise
     * indistinguishable at the mock: 1 is a delivery attempted, 0 is an empty
     * queue, and -1 is the in-JVM guard refusing a re-entrant run. Without
     * this, all three surface as "wanted but not invoked" on the mail sender,
     * which names the symptom and hides which of the three it was.
     */
    private void dispatchOne() {
        assertThat(dispatcher.runDispatchBatch())
                .as("the batch should have found exactly one queued row "
                        + "(0 = nothing due, -1 = a run was already in progress); rows were %s",
                        requests.findAll().stream()
                                .map(r -> r.getNewEmail() + "/" + r.getStatus()
                                        + "/next=" + r.getNextAttemptAt())
                                .toList())
                .isEqualTo(1);
    }

    private String linkTokenFromMail() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), body.capture());
        List<String> bodies = body.getAllValues();
        Matcher matcher = LINK.matcher(bodies.get(bodies.size() - 1));
        assertThat(matcher.find()).as("the mail carries a confirmation link").isTrue();
        return matcher.group(1);
    }
}
