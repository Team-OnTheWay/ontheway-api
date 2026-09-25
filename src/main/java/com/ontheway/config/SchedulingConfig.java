package com.ontheway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 를 켠다. {@link com.ontheway.scheduler.OrderScheduler} 가 이걸 쓴다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
