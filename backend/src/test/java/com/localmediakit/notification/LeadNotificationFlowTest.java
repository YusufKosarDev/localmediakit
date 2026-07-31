package com.localmediakit.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.lead.KitLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lead notifications end to end: a brand submits the public contact form, a
 * row is queued in the same transaction as the lead, and a background batch
 * delivers it.
 *
 * <p>The mail sender is stubbed — these tests are about the outbox's
 * behaviour, especially what happens when delivery fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeadNotificationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeadNotificationService notificationService;

    @Autowired
    private LeadNotificationRepository notificationRepository;

    @Autowired
    private KitLeadRepository leadRepository;

    @MockBean
    private MailSender mailSender;

    @BeforeEach
    void mailerIsConfigured() {
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        // The dispatch batch reads a bounded page of due rows, so notifications
        // left behind by an earlier method could crowd out the one the next
        // method is asserting on — which made this class intermittently fail.
        // Each method now starts from an empty queue and reasons only about the
        // row it created itself.
        notificationRepository.deleteAll();
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Uretici"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    /** Creates a kit and publishes it, so the public contact form resolves. */
    private String publishedKit(String token, String title, String slug) throws Exception {
        String response = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","slug":"%s"}
                                """.formatted(title, slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return slug;
    }

    private void submitLead(String slug, String brand) throws Exception {
        mockMvc.perform(post("/api/public/kits/" + slug + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0")
                        .content("""
                                {"brandName":"%s","email":"marka@ornek.com","message":"Isbirligi teklifimiz var."}
                                """.formatted(brand)))
                .andExpect(status().isAccepted());
    }

    @Test
    void aSubmittedLeadIsQueuedAndThenDelivered() throws Exception {
        String token = register("bildirim@example.com");
        String slug = publishedKit(token, "Kanal", "bildirim-kiti");

        submitLead(slug, "Marka A");

        List<LeadNotification> queued = notificationRepository.findAll().stream()
                .filter(n -> "bildirim@example.com".equals(n.getRecipientEmail()))
                .toList();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);

        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
        notificationService.runDispatchBatch();

        LeadNotification sent = notificationRepository.findById(queued.get(0).getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        verify(mailSender).send(eqTo("bildirim@example.com"), anyString(), anyString());
    }

    private static String eqTo(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    /**
     * The reason the outbox exists. The provider being down must cost nothing
     * but a delayed email.
     */
    @Test
    void aMailOutageNeverCostsTheLead() throws Exception {
        String token = register("dayanikli@example.com");
        String slug = publishedKit(token, "Kanal", "dayanikli-kiti");

        doThrow(new MailDeliveryException("smtp unreachable", new RuntimeException()))
                .when(mailSender).send(anyString(), anyString(), anyString());

        submitLead(slug, "Marka B");

        // The lead is on disk regardless of anything the mailer does.
        long kitId = notificationRepository.findAll().stream()
                .filter(n -> "dayanikli@example.com".equals(n.getRecipientEmail()))
                .findFirst().orElseThrow().getLeadId();
        assertThat(leadRepository.findById(kitId)).isPresent();

        notificationService.runDispatchBatch();

        LeadNotification after = notificationRepository.findAll().stream()
                .filter(n -> "dayanikli@example.com".equals(n.getRecipientEmail()))
                .findFirst().orElseThrow();
        // Still queued for another go, with the reason recorded.
        assertThat(after.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(after.getLastError()).contains("smtp unreachable");
        // And the lead is untouched.
        assertThat(leadRepository.findById(after.getLeadId())).isPresent();
    }

    @Test
    void deliveryIsGivenUpOnAfterTheRetryBudget() throws Exception {
        String token = register("pesetmez@example.com");
        String slug = publishedKit(token, "Kanal", "pesetmez-kiti");

        doThrow(new MailDeliveryException("permanent reject", new RuntimeException()))
                .when(mailSender).send(anyString(), anyString(), anyString());
        submitLead(slug, "Marka C");

        LeadNotification row = notificationRepository.findAll().stream()
                .filter(n -> "pesetmez@example.com".equals(n.getRecipientEmail()))
                .findFirst().orElseThrow();

        // Drive the attempts directly: the batch honours a backoff that would
        // otherwise make this test wait minutes between tries.
        for (int i = 0; i < LeadNotification.MAX_ATTEMPTS; i++) {
            row.markAttemptFailed("permanent reject");
        }
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(LeadNotification.MAX_ATTEMPTS);
    }

    @Test
    void backoffGrowsBetweenAttempts() {
        assertThat(LeadNotification.backoffFor(1)).isLessThan(LeadNotification.backoffFor(2));
        assertThat(LeadNotification.backoffFor(2)).isLessThan(LeadNotification.backoffFor(3));
    }

    /** One visitor is already capped by the lead form itself; this is the
     *  second layer, against many visitors adding up to a flooded mailbox. */
    @Test
    void anHourlyCapStopsAMailboxFlood() throws Exception {
        String token = register("tavan@example.com");
        String slug = publishedKit(token, "Kanal", "tavan-kiti");

        // Distinct user agents produce distinct visitor fingerprints, so the
        // per-visitor submission cap does not mask what is being tested here.
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/public/kits/" + slug + "/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("User-Agent", "Mozilla/5.0 (Visitor " + i + ")")
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .content("""
                                    {"brandName":"Marka %d","email":"m%d@ornek.com","message":"Merhaba"}
                                    """.formatted(i, i)))
                    .andExpect(status().isAccepted());
        }

        List<LeadNotification> rows = notificationRepository.findAll().stream()
                .filter(n -> "tavan@example.com".equals(n.getRecipientEmail()))
                .toList();
        long pending = rows.stream().filter(n -> n.getStatus() == NotificationStatus.PENDING).count();
        long suppressed = rows.stream().filter(n -> n.getStatus() == NotificationStatus.SUPPRESSED).count();

        // Capped, and the overflow is on record rather than silently dropped.
        assertThat(pending).isLessThanOrEqualTo(12);
        assertThat(suppressed).isPositive();

        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
        notificationService.runDispatchBatch();
        // Suppressed rows are never handed to the mailer.
        verify(mailSender, times((int) pending)).send(anyString(), anyString(), anyString());
    }

    @Test
    void switchingNotificationsOffStopsQueuingThemButKeepsTheLeads() throws Exception {
        String token = register("kapali@example.com");
        String slug = publishedKit(token, "Kanal", "kapali-kiti");

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Uretici","leadNotificationsEnabled":false}
                                """))
                .andExpect(status().isOk());

        submitLead(slug, "Marka D");

        assertThat(notificationRepository.findAll().stream()
                .filter(n -> "kapali@example.com".equals(n.getRecipientEmail())))
                .isEmpty();
        // The lead itself still arrives — only the email was declined.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"kapali@example.com","password":"supersecret"}
                                """))
                .andExpect(status().isOk());
        assertThat(leadRepository.findAll().stream()
                .anyMatch(l -> "Marka D".equals(l.getBrandName()))).isTrue();
    }

    @Test
    void nothingIsQueuedWhenSmtpIsNotConfigured() throws Exception {
        when(mailSender.available()).thenReturn(false);
        String token = register("kapalismtp@example.com");
        String slug = publishedKit(token, "Kanal", "kapalismtp-kiti");

        submitLead(slug, "Marka E");

        assertThat(notificationRepository.findAll().stream()
                .filter(n -> "kapalismtp@example.com".equals(n.getRecipientEmail())))
                .isEmpty();
        assertThat(notificationService.runDispatchBatch()).isZero();
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
        // The lead is still captured — the feature being dark changes nothing
        // about ingestion.
        assertThat(leadRepository.findAll().stream()
                .anyMatch(l -> "Marka E".equals(l.getBrandName()))).isTrue();
    }
}
