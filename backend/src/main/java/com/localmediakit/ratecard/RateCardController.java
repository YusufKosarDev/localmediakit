package com.localmediakit.ratecard;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mediakits/{kitId}/ratecard")
public class RateCardController {

    private final RateCardService rateCardService;

    public RateCardController(RateCardService rateCardService) {
        this.rateCardService = rateCardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RateCardResponse create(Authentication authentication,
                                   @PathVariable Long kitId,
                                   @Valid @RequestBody RateCardRequest request) {
        return rateCardService.create(email(authentication), kitId, request);
    }

    @GetMapping
    public List<RateCardResponse> list(Authentication authentication, @PathVariable Long kitId) {
        return rateCardService.list(email(authentication), kitId);
    }

    @PutMapping("/{itemId}")
    public RateCardResponse update(Authentication authentication,
                                   @PathVariable Long kitId,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody RateCardRequest request) {
        return rateCardService.update(email(authentication), kitId, itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable Long kitId,
                       @PathVariable Long itemId) {
        rateCardService.delete(email(authentication), kitId, itemId);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
