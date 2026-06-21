package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort.AiChatUnavailableException;
import com.studydeck.domain.port.out.AiSchemaValidationPort.AiOutputSchemaViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * REST controller for AI flashcard generation and improvement endpoints.
 *
 * <p>Scope enforcement: ai.generate
 *
 * <p>Both endpoints validate AI output against FlashcardImportV1 JSON Schema before returning.
 * Generated cards are PROPOSALS — they are never auto-persisted.
 */
@RestController
@RequestMapping("/v1/ai")
class AiController {

  private final GenerateFlashcardsUseCase generateFlashcards;
  private final ImproveFlashcardUseCase improveFlashcard;
  private final ObjectMapper objectMapper;

  AiController(
      @Qualifier("generateFlashcardsUseCase") GenerateFlashcardsUseCase generateFlashcards,
      @Qualifier("improveFlashcardUseCase") ImproveFlashcardUseCase improveFlashcard,
      ObjectMapper objectMapper) {
    this.generateFlashcards = generateFlashcards;
    this.improveFlashcard = improveFlashcard;
    this.objectMapper = objectMapper;
  }

  // ---------------------------------------------------------------
  // POST /v1/ai/generate-flashcards
  // ---------------------------------------------------------------

  @PostMapping("/generate-flashcards")
  @PreAuthorize("hasAuthority('SCOPE_ai.generate')")
  ResponseEntity<?> generateFlashcards(
      @AuthenticationPrincipal Jwt jwt, @RequestBody GenerateFlashcardsRequestDto request) {
    var ownerId = ownerIdFrom(jwt);
    if (request.source() == null
        || request.source().content() == null
        || request.source().content().isBlank()) {
      throw new IllegalArgumentException("source.content is required and must not be blank");
    }
    try {
      var cmd =
          new GenerateFlashcardsUseCase.Command(
              ownerId,
              request.source().content(),
              request.deckId() != null ? request.deckId().toString() : null,
              request.preferredTypes(),
              request.maxItems() != null ? request.maxItems() : 10);
      var result = generateFlashcards.execute(cmd);

      // Map the validated FlashcardImportV1 envelope to the contract GenerateFlashcardsResponse.
      return ResponseEntity.ok(toGenerateResponse(result.proposalsJson()));
    } catch (AiChatUnavailableException ex) {
      return chatUnavailableResponse(ex);
    } catch (AiOutputSchemaViolationException ex) {
      return schemaViolationResponse(ex);
    } catch (Exception ex) {
      var pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse AI output: " + ex.getMessage());
      pd.setTitle("AI Output Parse Error");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(pd);
    }
  }

  // ---------------------------------------------------------------
  // POST /v1/ai/improve-flashcard
  // ---------------------------------------------------------------

  @PostMapping("/improve-flashcard")
  @PreAuthorize("hasAuthority('SCOPE_ai.generate')")
  ResponseEntity<?> improveFlashcard(
      @AuthenticationPrincipal Jwt jwt, @RequestBody ImproveFlashcardRequestDto request) {
    var ownerId = ownerIdFrom(jwt);
    if (request.noteType() == null || request.noteType().isBlank() || request.content() == null) {
      throw new IllegalArgumentException("noteType and content are required");
    }
    try {
      String contentJson = objectMapper.writeValueAsString(request.content());
      var cmd =
          new ImproveFlashcardUseCase.Command(
              ownerId,
              request.noteType(),
              contentJson,
              instructionFrom(request.objective(), request.preserveMeaning()));
      var result = improveFlashcard.execute(cmd);

      JsonNode improvedNode = objectMapper.readTree(result.improvedJson());
      return ResponseEntity.ok(Map.of("noteType", result.noteType(), "content", improvedNode));
    } catch (AiChatUnavailableException ex) {
      return chatUnavailableResponse(ex);
    } catch (AiOutputSchemaViolationException ex) {
      return schemaViolationResponse(ex);
    } catch (Exception ex) {
      var pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse AI output: " + ex.getMessage());
      pd.setTitle("AI Output Parse Error");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(pd);
    }
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    return new OwnerId(UUID.fromString(jwt.getSubject()));
  }

  /**
   * Maps the validated FlashcardImportV1 envelope ({@code {schemaVersion, deck, notes:[...]}}) to
   * the openapi {@code GenerateFlashcardsResponse} ({@code {generated:[draft...], warnings}}).
   *
   * <p>Each note's per-type fields become the typed {@code content} object — the envelope-only keys
   * ({@code noteType}, {@code tags}, {@code source}) are lifted to the draft level and stripped
   * from {@code content} so it matches the BasicNoteContent/ClozeNoteContent/... schemas exactly.
   */
  private Map<String, Object> toGenerateResponse(String proposalsJson) {
    JsonNode root = objectMapper.readTree(proposalsJson);
    var generated = new ArrayList<Map<String, Object>>();
    for (JsonNode note : root.path("notes")) {
      var draft = new LinkedHashMap<String, Object>();
      draft.put("noteType", note.path("noteType").asString());
      if (note.has("tags")) {
        draft.put("tags", note.get("tags"));
      }
      ObjectNode content = (ObjectNode) note.deepCopy();
      content.remove(List.of("noteType", "tags", "source"));
      draft.put("content", content);
      generated.add(draft);
    }
    var response = new LinkedHashMap<String, Object>();
    response.put("generated", generated);
    response.put("warnings", List.of());
    return response;
  }

  /**
   * Derives a free-text improvement instruction from the contract's structured fields. The contract
   * models intent as an {@code objective} enum plus a {@code preserveMeaning} flag; the use case
   * consumes a single instruction string.
   */
  private String instructionFrom(String objective, Boolean preserveMeaning) {
    String goal = (objective != null && !objective.isBlank()) ? objective : "clarity";
    String base = "Improve this flashcard to optimize for " + goal + ".";
    return Boolean.FALSE.equals(preserveMeaning) ? base : base + " Preserve the original meaning.";
  }

  private ResponseEntity<ProblemDetail> chatUnavailableResponse(AiChatUnavailableException ex) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    pd.setTitle("AI Chat Provider Not Configured");
    pd.setType(URI.create("https://studydeck.ai/errors/ai-chat-unavailable"));
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }

  private ResponseEntity<ProblemDetail> schemaViolationResponse(
      AiOutputSchemaViolationException ex) {
    var pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.resolve(422), "AI output did not conform to FlashcardImportV1 schema");
    pd.setTitle("AI Output Schema Violation");
    pd.setType(URI.create("https://studydeck.ai/errors/ai-schema-violation"));
    pd.setProperty("violations", ex.getViolations());
    return ResponseEntity.status(HttpStatus.resolve(422))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }

  // ---------------------------------------------------------------
  // Inner request records
  // ---------------------------------------------------------------

  /** Matches the openapi {@code GenerateFlashcardsRequest} schema (nested {@code source}). */
  record GenerateFlashcardsRequestDto(
      UUID deckId,
      SourceDto source,
      List<String> preferredTypes,
      Integer maxItems,
      String language,
      String difficulty) {

    record SourceDto(String type, String content, List<UUID> documentIds) {}
  }

  /** Matches the openapi {@code ImproveFlashcardRequest} schema. */
  record ImproveFlashcardRequestDto(
      String noteType, Map<String, Object> content, String objective, Boolean preserveMeaning) {}
}
