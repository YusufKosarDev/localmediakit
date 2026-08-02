package com.localmediakit.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.notification.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * Forgotten-password recovery, which the README listed as a known limitation
 * until there was a way to send mail.
 *
 * <p>The assertions that matter are not the happy path. They are the ones about
 * what the endpoint refuses to tell a caller, and about a token being usable
 * exactly once -- a reset flow that leaks whether an address is registered, or
 * that accepts a replayed link, is worse than not having one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MailSender mailSender;

    @BeforeEach
    void mailerIsConfigured() {
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        doNothing().when(mailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void aResetLinkLetsSomeoneChooseANewPasswordAndSignInWithIt() throws Exception {
        register("reset-happy@example.com", "eskisifre123");

        requestReset("reset-happy@example.com");
        String token = tokenFromMail();

        confirm(token, "yenisifre456").andExpect(status().isNoContent());

        login("reset-happy@example.com", "yenisifre456").andExpect(status().isOk());
        login("reset-happy@example.com", "eskisifre123").andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownAddressGetsTheSameAnswerAsARealOne() throws Exception {
        // The whole point. Anything else turns this into a free membership
        // check for anyone holding a list of email addresses.
        register("reset-known@example.com", "sifre12345");

        requestReset("reset-known@example.com").andExpect(status().isAccepted());
        requestReset("kesinlikle-yok@example.com").andExpect(status().isAccepted());

        // ...and only the real one produced a mail.
        verify(mailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void aTokenWorksOnceAndNotTwice() throws Exception {
        register("reset-replay@example.com", "sifre12345");
        requestReset("reset-replay@example.com");
        String token = tokenFromMail();

        confirm(token, "birincisifre1").andExpect(status().isNoContent());
        // A link reaching a shared inbox, or sitting in browser history, must
        // not still be a key.
        confirm(token, "ikincisifre2").andExpect(status().isBadRequest());
        login("reset-replay@example.com", "birincisifre1").andExpect(status().isOk());
    }

    @Test
    void usingOneLinkKillsTheOthers() throws Exception {
        // A reset says the account may be compromised. Whoever prompted it must
        // not still be holding a working link.
        register("reset-multi@example.com", "sifre12345");
        requestReset("reset-multi@example.com");
        String first = tokenFromMail();
        reset(mailSender);
        when(mailSender.available()).thenReturn(true);
        requestReset("reset-multi@example.com");
        String second = tokenFromMail();

        confirm(second, "yenisifre789").andExpect(status().isNoContent());

        confirm(first, "baskasifre000").andExpect(status().isBadRequest());
    }

    @Test
    void anInventedTokenIsRefused() throws Exception {
        confirm("kesinlikle-gecerli-olmayan-token", "yenisifre123")
                .andExpect(status().isBadRequest());
    }

    @Test
    void nothingIsIssuedWhenThereIsNoWayToSendIt() throws Exception {
        // A token nobody receives is a row that exists only to be guessed at.
        when(mailSender.available()).thenReturn(false);
        register("reset-nomail@example.com", "sifre12345");

        requestReset("reset-nomail@example.com").andExpect(status().isAccepted());

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void theMailNeverContainsThePasswordAndSaysWhatToDoIfItWasNotYou() throws Exception {
        register("reset-body@example.com", "gizlisifre12");
        requestReset("reset-body@example.com");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), body.capture());

        assertThat(body.getValue()).doesNotContain("gizlisifre12");
        assertThat(body.getValue()).contains("/reset/");
        // An unrequested reset mail is the first sign somebody is trying an
        // address; "ignore it" is the useful advice, and it has to be there.
        assertThat(body.getValue().toLowerCase()).containsAnyOf("yok sayabilirsiniz", "you can ignore");
    }

    @Test
    void aFailedSendIsNotVisibleToTheCaller() throws Exception {
        // Surfacing it would also confirm the address exists.
        doThrow(new com.localmediakit.notification.MailDeliveryException("smtp down", new RuntimeException()))
                .when(mailSender).send(anyString(), anyString(), anyString());
        register("reset-smtp@example.com", "sifre12345");

        requestReset("reset-smtp@example.com").andExpect(status().isAccepted());
    }

    /* --- helpers --- */

    private String tokenFromMail() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), body.capture());
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
                .content(objectMapper.writeValueAsString(
                        new PasswordResetConfirmRequest(token, password))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Unutkan"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
    }
}
