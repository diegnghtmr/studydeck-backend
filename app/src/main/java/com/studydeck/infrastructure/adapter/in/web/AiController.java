package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort.AiChatUnavailableException;
import com.studydeck.domain.port.out.AiSchemaValidationPort.AiOutputSchemaViolationException;
import java.net.URI;
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
    try {
      var cmd =
          new GenerateFlashcardsUseCase.Command(
              ownerId,
              request.sourceText(),
              request.deckContext(),
              request.noteTypes(),
              request.maxCards() != null ? request.maxCards() : 10);
      var result = generateFlashcards.execute(cmd);

      // Parse the validated JSON string and return as structured JSON
      JsonNode proposalsNode = objectMapper.readTree(result.proposalsJson());
      return ResponseEntity.ok(
          Map.of("proposals", proposalsNode, "requiresApproval", result.requiresApproval()));
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
    try {
      String contentJson = objectMapper.writeValueAsString(request.content());
      var cmd =
          new ImproveFlashcardUseCase.Command(
              ownerId, request.noteType(), contentJson, request.instruction());
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

  record GenerateFlashcardsRequestDto(
      String sourceText, String deckContext, List<String> noteTypes, Integer maxCards) {}

  record ImproveFlashcardRequestDto(
      String noteType, Map<String, Object> content, String instruction) {}
}
