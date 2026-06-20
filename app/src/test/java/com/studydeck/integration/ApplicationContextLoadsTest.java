package com.studydeck.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring Boot context-loads integration test.
 *
 * <p>TDD contract (P0):
 *
 * <ol>
 *   <li>Boots a real PostgreSQL container (pgvector image).
 *   <li>Runs Flyway V1 migration (pgvector extension + user_account + deck tables).
 *   <li>Asserts the Spring context loads without errors.
 *   <li>Implicitly validates: datasource config, JPA config, Flyway migration, actuator wiring.
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
@Import(AiTestConfiguration.class)
class ApplicationContextLoadsTest {

  /**
   * pgvector image — provides PostgreSQL + vector extension pre-installed. Version pg17 matches the
   * vector extension used in V1 migration.
   */
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    // Flyway must use same connection
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Test
  void contextLoads() {
    // If the context fails to load, Spring Boot will throw an exception before reaching this line.
    // An empty test body is intentional — the assertion is the context startup itself.
  }
}
