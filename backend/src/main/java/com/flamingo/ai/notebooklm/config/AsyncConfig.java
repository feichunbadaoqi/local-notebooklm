package com.flamingo.ai.notebooklm.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Configuration for async operations. */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "documentProcessingExecutor")
  public Executor documentProcessingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("doc-proc-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "sseExecutor")
  public Executor sseExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("sse-");
    executor.initialize();
    return executor;
  }
}
