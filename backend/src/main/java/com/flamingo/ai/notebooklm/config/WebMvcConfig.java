package com.flamingo.ai.notebooklm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC configuration for async request handling (SSE streaming). */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  /**
   * Configures async support for SSE streaming with a proper thread pool executor.
   *
   * <p>This replaces the default SimpleAsyncTaskExecutor which is not suitable for production use
   * under load. The ThreadPoolTaskExecutor provides better resource management and performance.
   */
  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(asyncTaskExecutor());
    configurer.setDefaultTimeout(120000); // 2 minutes timeout for SSE streams
  }

  /**
   * Creates a thread pool task executor for async request handling.
   *
   * <p>Pool sizing rationale:
   *
   * <ul>
   *   <li>Core pool size: 5 - handles typical concurrent SSE streams
   *   <li>Max pool size: 20 - accommodates burst traffic
   *   <li>Queue capacity: 50 - buffers requests during high load
   * </ul>
   *
   * @return configured thread pool task executor
   */
  @Bean(name = "asyncTaskExecutor")
  public AsyncTaskExecutor asyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("async-sse-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }
}
