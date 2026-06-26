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
   * Extracts the JSON payload from raw LLM output. Reasoning models (e.g. Qwen3) interleave their
   * answer with chain-of-thought prose — sometimes inside {@code <think>...</think>} blocks, but on
   * sparse prompts often as UNWRAPPED prose that may contain stray braces (e.g. "the answer is
   * {tricky}. Here: {...}"). A naive first-'{'..last-'}' window then captures that prose and fails
   * to parse. Some models also wrap the JSON in markdown {@code ```json} code fences.
   *
   * <p>Strategy: strip code fences, then scan TOP-LEVEL {@code {...}} groups (balance-matched with
   * JSON string-literal awareness so braces inside string values never count) and return the LAST
   * group that parses as JSON. Reasoning models think FIRST and emit the answer LAST, so the final
   * top-level object is the real payload; decoy objects they emit earlier — inside {@code <think>}
   * blocks or as unwrapped prose, and possibly LARGER than the answer (a bare improve note is tiny)
   * — are discarded. Prose braces that do not form valid JSON are skipped, and braces inside
   * legitimate string values are preserved intact. Falls back to the legacy leading-{@code <think>}
   * strip + first-'{'..last-'}' window so inputs that worked before never regress. Idempotent on
   * already-clean JSON.
   *
   * <p>Known limitation: if a model emits the real answer FIRST and then a TRAILING parseable JSON
   * object (e.g. a summary), "last wins" would pick the trailing object. This does not occur with
   * the reasoning models we target (they think first and answer last), and the schema-violation
   * retry re-asks the model, so it is accepted rather than worked around here.
   */
  private String extractJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String s = stripCodeFences(raw);

    // Balanced, string-aware scan over TOP-LEVEL '{...}' groups: the interior of a matched group is
    // skipped (nested objects are never separate candidates). Keep the LAST group that parses as
    // JSON — reasoning models emit decoys first and the real answer last, so the final top-level
    // object is the payload for BOTH the large generate envelope and the small improve note.
    String best = null;
    int i = 0;
    while (i < s.length()) {
      if (s.charAt(i) != '{') {
        i++;
        continue;
      }
      int end = findMatchingBrace(s, i);
      if (end < 0) {
        // Unbalanced from here — advance and keep looking for a later balanced group.
        i++;
        continue;
      }
      String candidate = s.substring(i, end + 1);
      try {
        objectMapper.readTree(candidate);
        best = candidate; // last parseable top-level object wins
      } catch (Exception ignored) {
        // Not valid JSON (e.g. a stray reasoning brace) — keep scanning.
      }
      i = end + 1; // skip this group's interior; only top-level objects are candidates
    }
    if (best != null) {
      return best;
    }

    // Fallback: legacy behavior so previously-working inputs never regress.
    String legacy = s.replaceFirst("(?s)^\\s*<think>.*?</think>", "");
    int start = legacy.indexOf('{');
    int last = legacy.lastIndexOf('}');
    if (start >= 0 && last > start) {
      return legacy.substring(start, last + 1);
    }
    return legacy.strip();
  }

  /**
   * Returns the index of the '}' that matches the '{' at {@code openIdx}, accounting for JSON
   * string literals (braces inside strings are ignored) and backslash escapes. Returns -1 if
   * unbalanced.
   */
  private static int findMatchingBrace(String s, int openIdx) {
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = openIdx; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /** Strips a surrounding markdown code fence ({@code ```json ... ```} or {@code ``` ... ```}). */
  private static String stripCodeFences(String s) {
    String t = s.strip();
    if (!t.startsWith("```")) {
      return t;
    }
    int firstNewline = t.indexOf('\n');
    if (firstNewline >= 0) {
      t = t.substring(firstNewline + 1);
    }
    int closingFence = t.lastIndexOf("```");
    if (closingFence >= 0) {
      t = t.substring(0, closingFence);
    }
    return t.strip();
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
