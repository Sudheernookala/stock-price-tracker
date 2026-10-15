package com.solactive.stocktracker.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the system clock as a bean so time-dependent code can be tested with a fixed clock.
 */
@Configuration
public class ClockConfig {

    /**
     * Clock used for all processing timestamps.
     *
     * @return UTC system clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
