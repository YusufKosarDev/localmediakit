package com.localmediakit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled support (the domain verification job). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
