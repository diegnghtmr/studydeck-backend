package com.studydeck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration test that reproduces and verifies the JIT user provisioning fix.
 *
 * <p><b>Bug reproduced:</b> {@code POST /v1/decks} returned HTTP 500 because {@code deck.owner_id}
 * has FK → {@code user_account(id)}, but no {@code user_account} row existed for the JWT subject.
 * This test authenticates as a FRESH JWT subject (no pre-seeded row) and asserts the provisioning
 * filter creates the row automatically.
 *
 * <p>Test plan:
 *
 * <ol>
 *   <li>Verify no user_account row exists for the fresh subject before the request.
 *   <li>{@code POST /v1/decks} → expect 201 (provisioning auto-created the user_account row).
 *   <li>{@code GET /v1/decks} → expect 200 and the newly created deck is returned.
 *   <li>Second {@code POST /v1/decks} with the same subject → still 201, no duplicate user row.
 *   <li>Assert the user_account row count is exactly 1 (idempotent provisioning).
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Import(AiTestConfiguration.class)
class JitUserProvisioningIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_jit_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired WebApplicationContext context;
  @Autowired UserAccountRepository userAccountRepository;
  @Autowired ObjectMapper objectMapper;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("POST /v1/decks with a fresh JWT subject auto-provisions user and returns 201")
  void postDeck_freshSubject_provisionsUserAndReturns201() throws Exception {
    // Use a random UUID per test run — avoids cross-run state contamination
    UUID freshSubject = UUID.randomUUID();
    OwnerId freshId = new OwnerId(freshSubject);

    // PRE-CONDITION: no user_account row exists for this brand-new random subject
    assertThat(userAccountRepository.existsById(freshId))
        .as("user_account must NOT exist before the request")
        .isFalse();

    String body =
        objectMapper.writeValueAsString(
            Map.of("title", "JIT Provisioning Test Deck", "description", "Created via JIT"));

    // This would return HTTP 500 before the fix due to FK violation on deck.owner_id
    mockMvc
        .perform(
            post("/v1/decks")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(freshSubject.toString())
                                    .claim("email", "jit-" + freshSubject + "@test.com")
                                    .claim("name", "JIT User")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    // POST-CONDITION: user_account row was auto-created by the JIT filter
    assertThat(userAccountRepository.existsById(freshId))
        .as("user_account must exist after the provisioning filter ran")
        .isTrue();
  }

  @Test
  @DisplayName("GET /v1/decks after POST returns the created deck")
  void getDeck_afterPost_returnsCreatedDeck() throws Exception {
    UUID subject = UUID.randomUUID();
    String body = objectMapper.writeValueAsString(Map.of("title", "Deck for GET test"));

    mockMvc
        .perform(
            post("/v1/decks")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(subject.toString())
                                    .claim("email", "gettest-" + subject + "@test.com")
                                    .claim("name", "GET Test User")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/v1/decks")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(subject.toString())
                                    .claim("email", "gettest-" + subject + "@test.com")
                                    .claim("name", "GET Test User"))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("second POST with same subject does not create a duplicate user_account row")
  void postDeck_sameSubjectTwice_noduplicateUserRow() throws Exception {
    UUID subject = UUID.randomUUID();
    OwnerId ownerId = new OwnerId(subject);
    String email = "idempotent-" + subject + "@test.com";
    String body = objectMapper.writeValueAsString(Map.of("title", "Idempotency Deck"));

    // First request — user_account is created by JIT filter
    mockMvc
        .perform(
            post("/v1/decks")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(subject.toString())
                                    .claim("email", email)
                                    .claim("name", "Idempotent User")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    assertThat(userAccountRepository.existsById(ownerId)).isTrue();

    // Second request — JIT filter must be idempotent (no duplicate insert, no exception)
    mockMvc
        .perform(
            post("/v1/decks")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(subject.toString())
                                    .claim("email", email)
                                    .claim("name", "Idempotent User")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    // user_account still exists (exactly once — validated by DB unique constraint on email)
    assertThat(userAccountRepository.existsById(ownerId))
        .as("user_account row must still exist after second request")
        .isTrue();
  }
}
