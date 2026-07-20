package com.localmediakit.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.user.Plan;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint + wiring test. DnsResolver is mocked so verification is
 * deterministic and no real DNS is touched (the scheduled job, if it fired,
 * would also use this mock).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DomainFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private DnsResolver dnsResolver;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Domain Owner"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private void makePro(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.changePlan(Plan.PRO);
        userRepository.save(user);
    }

    private long createKit(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private JsonNode addDomain(String token, long kitId, String domain) throws Exception {
        String res = mockMvc.perform(post("/api/mediakits/" + kitId + "/domains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"%s\"}".formatted(domain)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res);
    }

    @Test
    void addingADomainIsAProFeature() throws Exception {
        String token = register("dom-free@example.com");
        long kitId = createKit(token, "Free Domain Kit");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"brand.example\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addReturnsPendingWithDnsInstructions() throws Exception {
        String token = register("dom-add@example.com");
        makePro("dom-add@example.com");
        long kitId = createKit(token, "Add Domain Kit");

        JsonNode node = addDomain(token, kitId, "Brand.Example");
        // normalized to lowercase, PENDING, TXT instructions present
        org.junit.jupiter.api.Assertions.assertEquals("brand.example", node.get("domain").asText());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", node.get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("TXT", node.get("dnsRecordType").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "_localmediakit-verify.brand.example", node.get("dnsRecordHost").asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                node.get("dnsRecordValue").asText().startsWith("lmk-verify-"));
    }

    @Test
    void invalidDomainIsRejected() throws Exception {
        String token = register("dom-invalid@example.com");
        makePro("dom-invalid@example.com");
        long kitId = createKit(token, "Invalid Domain Kit");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"not a domain\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameDomainOnSameKitIsIdempotentOtherKitConflicts() throws Exception {
        String token = register("dom-dup@example.com");
        makePro("dom-dup@example.com");
        long kit1 = createKit(token, "Dup Kit One");
        long kit2 = createKit(token, "Dup Kit Two");

        long firstId = addDomain(token, kit1, "dup.example").get("id").asLong();
        // Same domain, same kit -> returns the same row (idempotent).
        long againId = addDomain(token, kit1, "dup.example").get("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(firstId, againId);

        // Same domain, different kit -> conflict.
        mockMvc.perform(post("/api/mediakits/" + kit2 + "/domains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"dup.example\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void checkNowVerifiesWhenTxtMatches() throws Exception {
        String token = register("dom-verify@example.com");
        makePro("dom-verify@example.com");
        long kitId = createKit(token, "Verify Kit");
        JsonNode added = addDomain(token, kitId, "verify.example");
        long domainId = added.get("id").asLong();
        String txtToken = added.get("dnsRecordValue").asText();

        // DNS now returns the expected token.
        when(dnsResolver.lookupTxt(anyString())).thenReturn(List.of(txtToken));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains/" + domainId + "/check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // Idempotent: checking a VERIFIED domain again stays VERIFIED.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains/" + domainId + "/check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void checkNowStaysPendingWhenTxtMissing() throws Exception {
        String token = register("dom-pending@example.com");
        makePro("dom-pending@example.com");
        long kitId = createKit(token, "Pending Kit");
        long domainId = addDomain(token, kitId, "pending.example").get("id").asLong();

        when(dnsResolver.lookupTxt(anyString())).thenReturn(List.of());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains/" + domainId + "/check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.lastCheckedAt").isNotEmpty());
    }

    @Test
    void domainsAreOwnerScoped() throws Exception {
        String owner = register("dom-owner@example.com");
        makePro("dom-owner@example.com");
        String stranger = register("dom-stranger@example.com");
        long kitId = createKit(owner, "Owned Domain Kit");
        long domainId = addDomain(owner, kitId, "owned.example").get("id").asLong();

        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"x.example\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/domains")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/domains/" + domainId + "/check")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/domains/" + domainId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // Owner can delete.
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/domains/" + domainId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());
    }
}
