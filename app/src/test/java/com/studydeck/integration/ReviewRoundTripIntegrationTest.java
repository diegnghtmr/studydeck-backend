package com.studydeck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end integration test for the review scheduling round-trip.
 *
 * <p>Test plan:
 *
 * <ol>
 *   <li>Create deck → 201.
 *   <li>Create BASIC note (generates cards with NEW schedule state) → 201.
 *   <li>GET /v1/cards/due → card appears as due (NEW state = always due).
 *   <li>POST /v1/review-sessions → 201.
 *   <li>GET /v1/review-sessions/{id}/next → 200 card returned.
 *   <li>POST /v1/reviews with rating=good → 200, nextState.scheduledDays > 0.
 *   <li>GET /v1/reviews/history → log entry present.
 *   <li>GET /v1/decks/{id}/stats → reviewedToday = 1.
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Import(AiTestConfiguration.class)
class ReviewRoundTripIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_review_test")
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
  @Autowired ObjectMapper objectMapper;

  MockMvc mockMvc;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("Full review round-trip: create note → due → session → review → history → stats")
  void reviewRoundTrip() throws Exception {
    // 1. Create deck
    MvcResult deckResult =
        mockMvc
            .perform(
                post("/v1/decks")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("title", "Test Deck"))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode deckNode = objectMapper.readTree(deckResult.getResponse().getContentAsString());
    UUID deckId = UUID.fromString(deckNode.path("id").asText());

    // 2. Create BASIC note → should initialize card schedule states
    // NoteController is at /v1/notes; deckId and noteType are top-level fields.
    MvcResult noteResult =
        mockMvc
            .perform(
                post("/v1/notes")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "deckId",
                                deckId,
                                "noteType",
                                "basic",
                                "content",
                                Map.of(
                                    "front", "What is spaced repetition?",
                                    "back", "A learning technique.")))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode noteNode = objectMapper.readTree(noteResult.getResponse().getContentAsString());
    String noteId = noteNode.path("id").asText();

    // 3. GET /v1/cards/due → card should appear (NEW = always due)
    MvcResult dueResult =
        mockMvc
            .perform(
                get("/v1/cards/due")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .param("deckId", deckId.toString())
                    .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode dueNode = objectMapper.readTree(dueResult.getResponse().getContentAsString());
    assertThat(dueNode.path("items").size()).isGreaterThan(0);
    String cardId = dueNode.path("items").get(0).path("id").asText();

    // 4. POST /v1/review-sessions → 201
    MvcResult sessionResult =
        mockMvc
            .perform(
                post("/v1/review-sessions")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(Map.of("deckId", deckId, "maxCards", 20))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode sessionNode = objectMapper.readTree(sessionResult.getResponse().getContentAsString());
    String sessionId = sessionNode.path("id").asText();

    // 5. GET /v1/review-sessions/{id}/next → 200, card returned
    MvcResult nextResult =
        mockMvc
            .perform(
                get("/v1/review-sessions/{sessionId}/next", sessionId)
                    .with(jwt().jwt(b -> b.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andReturn();

    // Verify the card is the one we expect
    JsonNode nextNode = objectMapper.readTree(nextResult.getResponse().getContentAsString());
    assertThat(nextNode.path("card").path("id").asText()).isEqualTo(cardId);

    // 6. POST /v1/reviews with rating=good → 200
    MvcResult reviewResult =
        mockMvc
            .perform(
                post("/v1/reviews")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "cardId", cardId,
                                "rating", "good",
                                "sessionId", sessionId))))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode reviewNode = objectMapper.readTree(reviewResult.getResponse().getContentAsString());
    // nextState.scheduledDays > 0 means card graduated
    int scheduledDays = reviewNode.path("nextState").path("scheduledDays").asInt(0);
    assertThat(scheduledDays).isGreaterThan(0);
    // nextState.dueAt is in the future
    assertThat(reviewNode.path("nextState").path("dueAt").asText()).isNotBlank();

    // 7. GET /v1/reviews/history → log entry present
    MvcResult historyResult =
        mockMvc
            .perform(
                get("/v1/reviews/history")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .param("cardId", cardId))
            .andReturn();
    // Print body for debugging if status is not 200
    String historyBody = historyResult.getResponse().getContentAsString();
    int historyStatus = historyResult.getResponse().getStatus();
    assertThat(historyStatus).as("GET /v1/reviews/history body: " + historyBody).isEqualTo(200);
    JsonNode historyNode = objectMapper.readTree(historyBody);
    assertThat(historyNode.path("items").size()).isGreaterThan(0);
    assertThat(historyNode.path("items").get(0).path("cardId").asText()).isEqualTo(cardId);
    assertThat(historyNode.path("items").get(0).path("rating").asText()).isEqualTo("good");

    // 8. GET /v1/decks/{id}/stats → reviewedToday = 1
    mockMvc
        .perform(
            get("/v1/decks/{deckId}/stats", deckId)
                .with(jwt().jwt(b -> b.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewedToday").value(1))
        .andExpect(jsonPath("$.totalCards").value(org.hamcrest.Matchers.greaterThan(0)));
  }
}
