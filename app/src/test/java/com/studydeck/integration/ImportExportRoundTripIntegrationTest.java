package com.studydeck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * End-to-end integration test for the import/export round-trip.
 *
 * <p>Test plan:
 *
 * <ol>
 *   <li>POST /v1/imports/flashcards:validate — valid payload returns 200 + valid=true.
 *   <li>POST /v1/imports/flashcards:preview — valid payload returns 200 + summary.
 *   <li>POST /v1/imports/flashcards (all 5 note types) — 201, importedNotes=5, importedCards &ge;
 *       5, duplicateNotes=0, deckId is set.
 *   <li>GET /v1/cards/due — at least 5 cards are due (NEW state).
 *   <li>POST /v1/imports/flashcards (same payload to same deck) — 201, importedNotes=0,
 *       duplicateNotes=5 (full dedup).
 *   <li>GET /v1/exports/decks/{deckId}.json — 200, schemaVersion=1.0, notes.size=5.
 *   <li>POST /v1/imports/flashcards:validate (re-validate exported JSON) — valid=true.
 *   <li>POST /v1/imports/flashcards (re-import exported JSON to new deck) — 201, importedNotes=5
 *       (no duplicates in new deck).
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class ImportExportRoundTripIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_import_test")
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
  @DisplayName(
      "Full import/export round-trip: all 5 types → due cards → re-import = duplicates → export → re-validate → re-import new deck")
  void importExportRoundTrip() throws Exception {
    String validPayload = buildValidPayload();

    // ----------------------------------------------------------------
    // Step 1: validate
    // ----------------------------------------------------------------
    MvcResult validateResult =
        mockMvc
            .perform(
                post("/v1/imports/flashcards:validate")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPayload))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode validateNode =
        objectMapper.readTree(validateResult.getResponse().getContentAsString());
    assertThat(validateNode.path("valid").booleanValue()).isTrue();
    assertThat(validateNode.path("errors").isArray()).isTrue();
    assertThat(validateNode.path("errors").size()).isEqualTo(0);

    // ----------------------------------------------------------------
    // Step 2: preview
    // ----------------------------------------------------------------
    MvcResult previewResult =
        mockMvc
            .perform(
                post("/v1/imports/flashcards:preview")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPayload))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode previewNode = objectMapper.readTree(previewResult.getResponse().getContentAsString());
    assertThat(previewNode.path("valid").booleanValue()).isTrue();
    assertThat(previewNode.path("summary").path("totalNotes").intValue()).isEqualTo(5);

    // ----------------------------------------------------------------
    // Step 3: execute import — all 5 note types
    // ----------------------------------------------------------------
    MvcResult importResult =
        mockMvc
            .perform(
                post("/v1/imports/flashcards")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPayload))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode importNode = objectMapper.readTree(importResult.getResponse().getContentAsString());
    assertThat(importNode.path("importedNotes").intValue()).isEqualTo(5);
    assertThat(importNode.path("importedCards").intValue()).isGreaterThanOrEqualTo(5);
    assertThat(importNode.path("duplicateNotes").intValue()).isEqualTo(0);
    assertThat(importNode.path("deckId").isNull()).isFalse();

    String deckId = importNode.path("deckId").asString();
    assertThat(deckId).isNotBlank();

    // ----------------------------------------------------------------
    // Step 4: cards are due (NEW state)
    // ----------------------------------------------------------------
    MvcResult dueResult =
        mockMvc
            .perform(
                get("/v1/cards/due")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .param("deckId", deckId))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode dueNode = objectMapper.readTree(dueResult.getResponse().getContentAsString());
    assertThat(dueNode.path("items").isArray()).isTrue();
    assertThat(dueNode.path("items").size()).isGreaterThanOrEqualTo(5);

    // ----------------------------------------------------------------
    // Step 5: re-import same payload to same deck → all duplicates
    // ----------------------------------------------------------------
    MvcResult reimportResult =
        mockMvc
            .perform(
                post("/v1/imports/flashcards")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .param("deckId", deckId)
                    .content(validPayload))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode reimportNode =
        objectMapper.readTree(reimportResult.getResponse().getContentAsString());
    assertThat(reimportNode.path("importedNotes").intValue()).isEqualTo(0);
    assertThat(reimportNode.path("duplicateNotes").intValue()).isEqualTo(5);

    // ----------------------------------------------------------------
    // Step 6: export deck
    // ----------------------------------------------------------------
    MvcResult exportResult =
        mockMvc
            .perform(
                get("/v1/exports/decks/{deckId}.json", deckId)
                    .with(jwt().jwt(b -> b.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode exportNode = objectMapper.readTree(exportResult.getResponse().getContentAsString());
    assertThat(exportNode.path("schemaVersion").asString()).isEqualTo("1.0");
    assertThat(exportNode.path("notes").size()).isEqualTo(5);
    assertThat(exportNode.path("deck").path("title").asString()).isEqualTo("Biology 101");

    // Verify all 5 types are present in export
    List<String> exportedTypes = new ArrayList<>();
    for (JsonNode noteNode : exportNode.path("notes")) {
      exportedTypes.add(noteNode.path("noteType").asString());
    }
    exportedTypes.sort(null);
    assertThat(exportedTypes)
        .contains("basic", "reversed", "cloze", "multiple-choice", "free-text");

    // ----------------------------------------------------------------
    // Step 7: re-validate the exported payload
    // ----------------------------------------------------------------
    String exportedJson = exportResult.getResponse().getContentAsString();
    MvcResult revalidateResult =
        mockMvc
            .perform(
                post("/v1/imports/flashcards:validate")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(exportedJson))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode revalidateNode =
        objectMapper.readTree(revalidateResult.getResponse().getContentAsString());
    assertThat(revalidateNode.path("valid").booleanValue()).isTrue();

    // ----------------------------------------------------------------
    // Step 8: re-import the exported JSON to a new deck (no duplicates)
    // ----------------------------------------------------------------
    MvcResult freshImportResult =
        mockMvc
            .perform(
                post("/v1/imports/flashcards")
                    .with(jwt().jwt(b -> b.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(exportedJson))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode freshImportNode =
        objectMapper.readTree(freshImportResult.getResponse().getContentAsString());
    assertThat(freshImportNode.path("importedNotes").intValue()).isEqualTo(5);
    assertThat(freshImportNode.path("duplicateNotes").intValue()).isEqualTo(0);
  }

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  private String buildValidPayload() {
    return """
        {
          "schemaVersion": "1.0",
          "deck": {
            "title": "Biology 101",
            "description": "A biology flashcard deck",
            "tags": ["bio"]
          },
          "notes": [
            {
              "noteType": "basic",
              "front": "What is a cell?",
              "back": "The basic unit of life.",
              "tags": ["cell"]
            },
            {
              "noteType": "reversed",
              "front": "Nucleus",
              "back": "Controls cell activities.",
              "tags": ["nucleus"]
            },
            {
              "noteType": "cloze",
              "text": "The {{c1::mitochondria}} is the powerhouse of the {{c2::cell}}."
            },
            {
              "noteType": "multiple-choice",
              "question": "Which organelle produces ATP?",
              "options": [
                {"key": "A", "text": "Mitochondria"},
                {"key": "B", "text": "Nucleus"},
                {"key": "C", "text": "Ribosome"},
                {"key": "D", "text": "Vacuole"}
              ],
              "correctOptionKeys": ["A"],
              "explanation": "Mitochondria are the powerhouse."
            },
            {
              "noteType": "free-text",
              "prompt": "Explain the structure of a cell membrane.",
              "expectedAnswer": "Phospholipid bilayer with embedded proteins.",
              "gradingGuidance": "Look for phospholipid bilayer mention."
            }
          ]
        }
        """;
  }
}
