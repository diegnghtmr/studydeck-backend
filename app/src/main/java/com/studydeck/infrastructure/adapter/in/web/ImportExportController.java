package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ExecuteImportUseCase;
import com.studydeck.domain.port.in.ExportDeckUseCase;
import com.studydeck.domain.port.in.PreviewImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload;
import com.studydeck.infrastructure.adapter.in.web.mapper.ImportExportMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Driving adapter — REST controller for Import/Export operations.
 *
 * <p>Operations:
 *
 * <ul>
 *   <li>POST /v1/imports/flashcards:validate — validate only, no side effects
 *   <li>POST /v1/imports/flashcards:preview — validate + dedup preview
 *   <li>POST /v1/imports/flashcards — execute import
 *   <li>GET /v1/exports/decks/{deckId}.json — export deck
 * </ul>
 *
 * <p>Two-layer validation: JSON Schema (structural) → domain (per-type). JSON Schema errors return
 * 400. Domain validation / semantic errors return 422.
 */
@RestController
class ImportExportController {

  private final ValidateImportUseCase validateImport;
  private final PreviewImportUseCase previewImport;
  private final ExecuteImportUseCase executeImport;
  private final ExportDeckUseCase exportDeck;
  private final ImportExportMapper mapper;
  private final ImportSchemaValidator schemaValidator;
  private final ObjectMapper objectMapper;

  ImportExportController(
      @Qualifier("validateImportUseCase") ValidateImportUseCase validateImport,
      @Qualifier("previewImportUseCase") PreviewImportUseCase previewImport,
      @Qualifier("executeImportUseCase") ExecuteImportUseCase executeImport,
      @Qualifier("exportDeckUseCase") ExportDeckUseCase exportDeck,
      ImportExportMapper mapper,
      ImportSchemaValidator schemaValidator,
      ObjectMapper objectMapper) {
    this.validateImport = validateImport;
    this.previewImport = previewImport;
    this.executeImport = executeImport;
    this.exportDeck = exportDeck;
    this.mapper = mapper;
    this.schemaValidator = schemaValidator;
    this.objectMapper = objectMapper;
  }

  // ---------------------------------------------------------------
  // POST /v1/imports/flashcards:validate
  // ---------------------------------------------------------------

  @PostMapping("/v1/imports/flashcards:validate")
  ResponseEntity<?> validateImport(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
    // Layer 1: JSON Schema validation
    List<String> schemaErrors = schemaValidator.validate(body);
    if (!schemaErrors.isEmpty()) {
      return schemaErrorResponse(schemaErrors);
    }

    OwnerId ownerId = ownerIdFrom(jwt);
    ImportPayload payload = mapper.toPayload(body);

    ValidateImportUseCase.Result result =
        validateImport.execute(new ValidateImportUseCase.Command(ownerId, payload));

    return ResponseEntity.ok(mapper.toValidationResponse(result));
  }

  // ---------------------------------------------------------------
  // POST /v1/imports/flashcards:preview
  // ---------------------------------------------------------------

  @PostMapping("/v1/imports/flashcards:preview")
  ResponseEntity<?> previewImport(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
    // Layer 1: JSON Schema validation
    List<String> schemaErrors = schemaValidator.validate(body);
    if (!schemaErrors.isEmpty()) {
      return schemaErrorResponse(schemaErrors);
    }

    OwnerId ownerId = ownerIdFrom(jwt);
    ImportPayload payload = mapper.toPayload(body);

    PreviewImportUseCase.Result result =
        previewImport.execute(new PreviewImportUseCase.Command(ownerId, payload, null));

    return ResponseEntity.ok(mapper.toPreviewResponse(result));
  }

  // ---------------------------------------------------------------
  // POST /v1/imports/flashcards
  // ---------------------------------------------------------------

  @PostMapping("/v1/imports/flashcards")
  ResponseEntity<?> importFlashcards(
      @RequestBody JsonNode body,
      @RequestParam(name = "deckId", required = false) UUID targetDeckId,
      @AuthenticationPrincipal Jwt jwt) {
    // Layer 1: JSON Schema validation
    List<String> schemaErrors = schemaValidator.validate(body);
    if (!schemaErrors.isEmpty()) {
      return schemaErrorResponse(schemaErrors);
    }

    OwnerId ownerId = ownerIdFrom(jwt);
    ImportPayload payload = mapper.toPayload(body);

    // Layer 2: domain validation (run first, return 422 on failure)
    ValidateImportUseCase.Result validation =
        validateImport.execute(new ValidateImportUseCase.Command(ownerId, payload));
    if (!validation.valid()) {
      var pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.resolve(422), "Import payload failed domain validation");
      pd.setTitle("Import Validation Failed");
      pd.setType(URI.create("https://studydeck.ai/errors/import-validation"));
      pd.setProperty("errors", validation.errors());
      return ResponseEntity.status(422)
          .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
          .body(pd);
    }

    DeckId deckIdParam = targetDeckId != null ? new DeckId(targetDeckId) : null;
    ExecuteImportUseCase.ImportResult result =
        executeImport.execute(new ExecuteImportUseCase.Command(ownerId, payload, deckIdParam));

    URI location = URI.create("/v1/imports/flashcards/" + result.importId());
    return ResponseEntity.created(location).body(mapper.toResultResponse(result));
  }

  // ---------------------------------------------------------------
  // GET /v1/exports/decks/{deckId}.json
  // ---------------------------------------------------------------

  /**
   * Exports a deck as JSON. The payload is serialized through the mapper to exclude null fields, so
   * the result is valid against the JSON Schema (each note type only contains its own fields).
   */
  @GetMapping("/v1/exports/decks/{deckId}.json")
  ResponseEntity<JsonNode> exportDeck(@PathVariable UUID deckId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    ImportPayload exported =
        exportDeck.execute(new ExportDeckUseCase.Command(ownerId, new DeckId(deckId)));
    JsonNode exportedJson = mapper.toJson(exported, objectMapper);
    return ResponseEntity.ok(exportedJson);
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    return new OwnerId(UUID.fromString(jwt.getSubject()));
  }

  private ResponseEntity<ProblemDetail> schemaErrorResponse(List<String> schemaErrors) {
    var pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "JSON Schema validation failed");
    pd.setTitle("Schema Validation Failed");
    pd.setType(URI.create("https://studydeck.ai/errors/schema-validation"));
    pd.setProperty("schemaErrors", schemaErrors);
    return ResponseEntity.badRequest()
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }
}
