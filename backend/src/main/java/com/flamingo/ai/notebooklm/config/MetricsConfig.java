package com.flamingo.ai.notebooklm.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for application metrics. */
@Configuration
public class MetricsConfig {

  /**
   * Enables the @Timed annotation for method-level timing metrics.
   *
   * @param registry the meter registry
   * @return the timed aspect bean
   */
  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }
}
