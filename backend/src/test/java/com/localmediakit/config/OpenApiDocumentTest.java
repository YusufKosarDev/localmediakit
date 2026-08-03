package com.localmediakit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API document has to be generated, not merely offered.
 *
 * <p>springdoc is pinned against a Boot generation, and when the two drift the
 * failure is quiet in the worst way: the application starts, every endpoint
 * works, {@code /swagger-ui.html} still answers 200 because it is a static
 * shell — and the shell then fetches {@code /v3/api-docs}, which answers 500.
 * The README hands that URL to readers as the way to see the API, so the only
 * person who finds out is the one it was written for.
 *
 * <p>That is exactly what a Boot upgrade did here, with the whole suite green
 * either side of it. Nothing had ever asked the generator to run. This does:
 * a 200 with paths in it means the document was built, not just routed to.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesTheOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // Named endpoints rather than a bare 200: an empty document is
                // also a 200, and would mean the scan silently found nothing.
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/mediakits']").exists());
    }
}
