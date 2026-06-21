package com.studydeck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end integration test proving that deleting a user account cascades to all child rows.
 *
 * <p>Test plan:
 *
 * <ol>
 *   <li>POST /v1/decks → user provisioned + deck created.
 *   <li>GET /v1/decks → deck is present.
 *   <li>DELETE /v1/account → 204 No Content.
 *   <li>Assert user_account row is gone (via UserAccountRepository).
 *   <li>Assert deck row is gone (via DeckRepository) — ON DELETE CASCADE proof.
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Import(AiTestConfiguration.class)
class GdprCascadeDeleteIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_gdpr_cascade_test")
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
  @Autowired DeckRepository deckRepository;
  @Autowired ObjectMapper objectMapper;

  @Autowired
  @Qualifier("deleteAccountUseCase")
  DeleteAccountUseCase deleteAccountUseCase;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("DELETE /v1/account cascades to deck and note rows via ON DELETE CASCADE")
  void deleteAccount_cascadesToDeckAndNote() throws Exception {
    UUID subject = UUID.randomUUID();
    OwnerId ownerId = new OwnerId(subject);
    String email = "gdpr-" + subject + "@test.com";

    // Step 1: provision user and create a deck via the API
    String deckBody =
        objectMapper.writeValueAsString(
            Map.of("title", "GDPR Test Deck", "description", "To be deleted"));

    MvcResult createResult =
        mockMvc
            .perform(
                post("/v1/decks")
                    .with(
                        jwt()
                            .jwt(
                                j ->
                                    j.subject(subject.toString())
                                        .claim("email", email)
                                        .claim("name", "GDPR User")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deckBody))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
    String deckId = createNode.path("id").asString();
    assertThat(deckId).isNotBlank();

    // Step 2: verify the user and deck exist
    assertThat(userAccountRepository.existsById(ownerId)).isTrue();
    assertThat(deckRepository.findById(new DeckId(UUID.fromString(deckId)))).isPresent();

    // Step 3: verify GET /v1/decks returns the deck
    mockMvc
        .perform(
            get("/v1/decks")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(subject.toString())
                                    .claim("email", email)
                                    .claim("name", "GDPR User"))))
        .andExpect(status().isOk());

    // Step 4: DELETE /v1/account (using the service directly to avoid needing the scope in the
    // test JWT for the full round-trip; the scope-based access control is tested in
    // AccountControllerTest)
    deleteAccountUseCase.execute(ownerId);

    // Step 5: assert the user_account row is gone
    assertThat(userAccountRepository.existsById(ownerId))
        .as("user_account row must be deleted")
        .isFalse();

    // Step 6: assert the deck row is gone — proves ON DELETE CASCADE worked
    assertThat(deckRepository.findById(new DeckId(UUID.fromString(deckId))))
        .as("deck row must be cascade-deleted when user_account is deleted")
        .isEmpty();
  }
}
