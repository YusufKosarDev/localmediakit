package com.localmediakit.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the rate-limit filter ahead of Spring Security. Limits (per minute,
 * per client IP) and the on/off switch are env-configurable; tests disable it.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterRegistry registry,
            @Value("${app.ratelimit.enabled:true}") boolean enabled,
            @Value("${app.ratelimit.login-capacity:10}") long loginCapacity,
            @Value("${app.ratelimit.register-capacity:30}") long registerCapacity,
            @Value("${app.ratelimit.track-capacity:120}") long trackCapacity,
            @Value("${app.ratelimit.unlock-capacity:20}") long unlockCapacity,
            @Value("${app.ratelimit.contact-capacity:10}") long contactCapacity,
            @Value("${app.ratelimit.account-capacity:10}") long accountCapacity) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
                new RateLimitFilter(registry, enabled, loginCapacity, registerCapacity,
                        trackCapacity, unlockCapacity, contactCapacity, accountCapacity));
        bean.addUrlPatterns("/api/*");
        // Still ahead of Spring Security, but one step behind the request-id
        // filter: a rejected request has to carry an id too, or a 429 is a log
        // line nobody can trace back to a caller.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }
}
