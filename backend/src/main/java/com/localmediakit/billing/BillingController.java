package com.localmediakit.billing;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** Dashboard-only upgrade: returns the hosted Checkout URL to redirect to. */
    @PostMapping("/checkout")
    public Map<String, String> checkout(Authentication authentication) {
        String url = billingService.createCheckoutUrl((String) authentication.getPrincipal());
        return Map.of("url", url);
    }

    @GetMapping
    public BillingStatusResponse status(Authentication authentication) {
        return billingService.statusFor((String) authentication.getPrincipal());
    }

    /** Stripe webhook: public path, but every request must carry a valid signature. */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(@RequestBody String payload,
                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        billingService.processWebhook(payload, signature);
    }
}
