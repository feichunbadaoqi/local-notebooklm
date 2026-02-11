package com.flamingo.ai.notebooklm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for application context loading. Requires external dependencies (OpenAI API key,
 * Elasticsearch) to be configured. Run with: OPENAI_API_KEY=... ./gradlew test --tests
 * NotebooklmApplicationTests
 */
@SpringBootTest
@Disabled("Requires external configuration (API keys, Elasticsearch) - run manually")
class NotebooklmApplicationTests {

  @Test
  void contextLoads() {}
}
