package com.localmediakit.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mediakits/{kitId}/domains")
public class DomainController {

    private final DomainVerificationService service;

    public DomainController(DomainVerificationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse add(Authentication authentication,
                              @PathVariable Long kitId,
                              @Valid @RequestBody AddDomainRequest request) {
        return DomainResponse.from(
                service.addDomain(email(authentication), kitId, request.domain()),
                service.verifyHostPrefix());
    }

    @GetMapping
    public List<DomainResponse> list(Authentication authentication, @PathVariable Long kitId) {
        return service.listDomains(email(authentication), kitId).stream()
                .map(d -> DomainResponse.from(d, service.verifyHostPrefix()))
                .toList();
    }

    /** Owner-triggered "check now": runs the same verification synchronously. */
    @PostMapping("/{domainId}/check")
    public DomainResponse check(Authentication authentication,
                                @PathVariable Long kitId,
                                @PathVariable Long domainId) {
        return DomainResponse.from(
                service.checkNow(email(authentication), kitId, domainId),
                service.verifyHostPrefix());
    }

    @DeleteMapping("/{domainId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication,
                       @PathVariable Long kitId,
                       @PathVariable Long domainId) {
        service.removeDomain(email(authentication), kitId, domainId);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
