package com.localmediakit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profile editing and credential changes on {@code /api/me}.
 *
 * @see AccountDeletionFlowTest for the deletion half of the settings page
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountSettingsFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Test User"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void newAccountStartsWithNoAvatarAndTheLightTheme() throws Exception {
        String token = register("defaults@example.com", "supersecret");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.theme").value("LIGHT"));
    }

    @Test
    void updatesDisplayNameAvatarAndTheme() throws Exception {
        String token = register("profile@example.com", "supersecret");

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Yeni Ad","avatarUrl":"https://cdn.example.com/a.png","theme":"DARK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Yeni Ad"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example.com/a.png"))
                .andExpect(jsonPath("$.theme").value("DARK"));

        // Persisted, not just echoed back.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.displayName").value("Yeni Ad"))
                .andExpect(jsonPath("$.theme").value("DARK"));
    }

    @Test
    void blankAvatarClearsItAndNonHttpsIsRejected() throws Exception {
        String token = register("avatar@example.com", "supersecret");

        // An http:// avatar would be a mixed-content image on an https page.
        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Test User","avatarUrl":"http://insecure.example.com/a.png"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Test User","avatarUrl":"https://cdn.example.com/a.png"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Test User","avatarUrl":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void profileUpdateNeedsASessionAndCannotNameAnotherUser() throws Exception {
        register("victim@example.com", "victimsecret");
        String attackerToken = register("attacker@example.com", "attackersecret");

        // No token at all -> 401.
        mockMvc.perform(put("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Hijacked"}
                                """))
                .andExpect(status().isUnauthorized());

        // The route takes no user id, so the closest thing to an IDOR attempt
        // is smuggling one in the body. Unknown fields are ignored and the
        // subject still comes from the token: the attacker edits only itself.
        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Hijacked","id":1,"userId":1,"email":"victim@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("attacker@example.com"));

        // The victim is untouched: same name, same address.
        String victimResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"victim@example.com","password":"victimsecret"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String victimToken = objectMapper.readTree(victimResponse).get("token").asText();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + victimToken))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.email").value("victim@example.com"));
    }

    @Test
    void changesPasswordOnlyWithTheCurrentOne() throws Exception {
        String token = register("pw@example.com", "oldsecret1");

        // Wrong current password -> 401, nothing changes.
        mockMvc.perform(post("/api/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"notmypassword","newPassword":"newsecret1"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pw@example.com","password":"oldsecret1"}
                                """))
                .andExpect(status().isOk());

        // Too short a new password is rejected by the same rule as signup.
        mockMvc.perform(post("/api/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldsecret1","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest());

        // Correct current password -> 204.
        mockMvc.perform(post("/api/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"oldsecret1","newPassword":"newsecret1"}
                                """))
                .andExpect(status().isNoContent());

        // The old password is dead, the new one works.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pw@example.com","password":"oldsecret1"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pw@example.com","password":"newsecret1"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void changesEmailAndHandsBackAWorkingToken() throws Exception {
        String token = register("before@example.com", "supersecret");

        // Wrong password -> 401.
        mockMvc.perform(post("/api/me/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrongone","newEmail":"after@example.com"}
                                """))
                .andExpect(status().isUnauthorized());

        String response = mockMvc.perform(post("/api/me/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","newEmail":"After@Example.com"}
                                """))
                .andExpect(status().isOk())
                // Normalized on the way in, exactly like registration.
                .andExpect(jsonPath("$.user.email").value("after@example.com"))
                .andReturn().getResponse().getContentAsString();

        // The old token names the old address, so it must stop resolving —
        // otherwise the user is silently signed out with a bare 401.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        String newToken = objectMapper.readTree(response).get("token").asText();
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("after@example.com"));

        // And the new address is the one that logs in.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"after@example.com","password":"supersecret"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void refusesAnEmailAlreadyTakenByAnotherAccount() throws Exception {
        register("taken@example.com", "supersecret");
        String token = register("mover@example.com", "supersecret");

        mockMvc.perform(post("/api/me/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","newEmail":"taken@example.com"}
                                """))
                .andExpect(status().isConflict());

        // Case differences must not slip past the uniqueness check either.
        mockMvc.perform(post("/api/me/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","newEmail":"TAKEN@example.com"}
                                """))
                .andExpect(status().isConflict());

        // The account still answers on its original address.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.email").value("mover@example.com"));
    }

    @Test
    void keepingYourOwnEmailIsNotAConflict() throws Exception {
        String token = register("same@example.com", "supersecret");

        mockMvc.perform(post("/api/me/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","newEmail":"same@example.com"}
                                """))
                .andExpect(status().isOk());
    }
}
