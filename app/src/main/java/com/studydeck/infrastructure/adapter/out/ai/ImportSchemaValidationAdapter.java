package com.studydeck.infrastructure.adapter.out.ai;

import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.infrastructure.adapter.in.web.ImportSchemaValidator;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adapter wrapping {@link ImportSchemaValidator} to implement {@link AiSchemaValidationPort}.
 *
 * <p>This enables the application layer to validate AI-generated JSON against the FlashcardImportV1
 * JSON Schema without depending on the infrastructure {@code ImportSchemaValidator} directly.
 *
 * <p>Note: The networknt JSON Schema validator works on the string representation of the JSON; the
 * {@code tools.jackson} ObjectMapper (Spring Boot 4 / Jackson 3.x) is used for parsing.
 */
public class ImportSchemaValidationAdapter implements AiSchemaValidationPort {

  private final ImportSchemaValidator schemaValidator;
  private final ObjectMapper objectMapper;

  public ImportSchemaValidationAdapter(
      ImportSchemaValidator schemaValidator, ObjectMapper objectMapper) {
    this.schemaValidator = schemaValidator;
    this.objectMapper = objectMapper;
  }

  @Override
  public String validateAndReturn(String json) {
    String clean = extractJson(json);
    List<String> violations = validate(clean);
    if (!violations.isEmpty()) {
      throw new AiOutputSchemaViolationException(violations);
    }
    // Return the sanitized JSON so downstream parsing never sees reasoning/fence noise.
    return clean;
  }

  @Override
  public List<String> validate(String json) {
    try {
      JsonNode node = objectMapper.readTree(extractJson(json));
      return schemaValidator.validate(node);
    } catch (Exception e) {
      return List.of("Failed to parse AI output as JSON: " + e.getMessage());
    }
  }

  @Override
  public String validateNoteAndReturn(String noteType, String contentJson) {
    String clean = extractJson(contentJson);
    List<String> violations = validateNote(noteType, clean);
    if (!violations.isEmpty()) {
      throw new AiOutputSchemaViolationException(violations);
    }
    // The improve response contract stays BARE — return the sanitized content.
    return clean;
  }

  /**
   * Extracts the JSON payload from raw LLM output. Reasoning models (e.g. Qwen3) wrap their answer
   * in {@code <think>...</think>} blocks, and some models add markdown {@code ```json} code fences
   * or surrounding prose. This strips reasoning blocks first (they may contain stray braces), then
   * narrows to the outermost JSON object — from the first '{' to the last '}'. Idempotent: calling
   * it on already-clean JSON returns it unchanged.
   */
  private static String extractJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    // Drop a LEADING chain-of-thought block (reasoning models emit it as the very first tokens).
    // Anchored to the start so a literal "<think>...</think>" appearing INSIDE card content (e.g. a
    // flashcard about reasoning tags) is never stripped.
    String s = raw.replaceFirst("(?s)^\\s*<think>.*?</think>", "");
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return s.substring(start, end + 1);
    }
    return s.strip();
  }

  @Override
  public List<String> validateNote(String noteType, String contentJson) {
    try {
      JsonNode parsed = objectMapper.readTree(extractJson(contentJson));
      if (!parsed.isObject()) {
        return List.of("Improve output must be a JSON object, got: " + parsed.getNodeType());
      }
      // Inject the discriminator and wrap into a minimal valid FlashcardImportV1 envelope so the
      // existing envelope schema (root requires schemaVersion/deck/notes) can validate the note.
      ObjectNode note = (ObjectNode) parsed;
      note.put("noteType", noteType);

      ObjectNode envelope = objectMapper.createObjectNode();
      envelope.put("schemaVersion", "1.0");
      envelope.set("deck", objectMapper.createObjectNode().put("title", "Improve"));
      ArrayNode notes = objectMapper.createArrayNode();
      notes.add(note);
      envelope.set("notes", notes);

      return schemaValidator.validate(envelope);
    } catch (Exception e) {
      return List.of("Failed to parse AI output as JSON: " + e.getMessage());
    }
  }
}
