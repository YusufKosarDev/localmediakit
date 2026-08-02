package com.localmediakit.lead;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Owner-facing lead inbox. */
@RestController
@RequestMapping("/api/mediakits/{kitId}/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public List<LeadResponse> list(Authentication authentication, @PathVariable Long kitId) {
        return leadService.list(email(authentication), kitId);
    }

    /**
     * The inbox as a downloadable file.
     *
     * <p>The byte-order mark is not decoration: without it a spreadsheet opens
     * a UTF-8 file as the local codepage, and every Turkish character in a
     * brand name or a message arrives broken. It is the difference between an
     * export somebody can use and one they have to repair.
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> export(Authentication authentication, @PathVariable Long kitId) {
        byte[] body = withBom(leadService.exportCsv(email(authentication), kitId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"leads-" + kitId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    private static byte[] withBom(String csv) {
        byte[] text = csv.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[3 + text.length];
        out[0] = (byte) 0xEF;
        out[1] = (byte) 0xBB;
        out[2] = (byte) 0xBF;
        System.arraycopy(text, 0, out, 3, text.length);
        return out;
    }

    @PutMapping("/{leadId}/status")
    public LeadResponse changeStatus(Authentication authentication,
                                     @PathVariable Long kitId,
                                     @PathVariable Long leadId,
                                     @Valid @RequestBody LeadStatusRequest request) {
        return leadService.changeStatus(email(authentication), kitId, leadId, request.status());
    }

    @DeleteMapping("/{leadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable Long kitId,
                       @PathVariable Long leadId) {
        leadService.delete(email(authentication), kitId, leadId);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
