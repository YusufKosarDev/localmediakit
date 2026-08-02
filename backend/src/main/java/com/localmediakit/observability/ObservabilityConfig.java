package com.localmediakit.observability;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the request-id filter first in the chain.
 *
 * <p>Order matters more than it looks: the rate-limit filter rejects requests
 * before Spring Security sees them, and a 429 with no id in its log line is a
 * request nobody can trace. Running ahead of everything means every request
 * that reaches the application is identifiable, including the ones it refuses.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> bean =
                new FilterRegistrationBean<>(new RequestIdFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
