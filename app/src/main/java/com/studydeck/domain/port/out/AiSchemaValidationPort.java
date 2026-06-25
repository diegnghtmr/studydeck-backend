package com.studydeck.domain.port.out;

import java.util.List;

/**
 * Output port for validating AI-generated structured output against the FlashcardImportV1 JSON
 * Schema.
 *
 * <p>This wraps the existing {@link
 * com.studydeck.infrastructure.adapter.in.web.ImportSchemaValidator} so that the application
 * service can call schema validation without depending on infrastructure.
 */
public interface AiSchemaValidationPort {

  /**
   * Validates the AI-generated JSON string against the FlashcardImportV1 schema.
   *
   * @param json the raw JSON string produced by the LLM
   * @return the same JSON string if valid
   * @throws AiOutputSchemaViolationException if the JSON does not conform to the schema
   */
  String validateAndReturn(String json);

  /**
   * Returns a list of schema violation messages, or empty if valid.
   *
   * @param json the raw JSON string produced by the LLM
   */
  List<String> validate(String json);

  /**
   * Validates a BARE single-note content JSON (as produced by the improve-flashcard prompt) against
   * the FlashcardImportV1 schema.
   *
   * <p>The improve prompt returns only the note body (e.g. {@code {"front":"...","back":"..."}})
   * without the {@code noteType} discriminator or the import envelope. The adapter injects the
   * discriminator, wraps the note into a minimal valid envelope, and validates THAT envelope.
   *
   * @param noteType the note type discriminator (basic, reversed, cloze, multiple-choice,
   *     free-text)
   * @param contentJson the bare note content JSON produced by the LLM
   * @return the original bare {@code contentJson} unchanged when valid
   * @throws AiOutputSchemaViolationException if the wrapped note does not conform to the schema
   */
  String validateNoteAndReturn(String noteType, String contentJson);

  /**
   * Returns a list of schema violation messages for a bare single-note content JSON, or empty if
   * valid. See {@link #validateNoteAndReturn(String, String)} for the wrapping behavior.
   *
   * @param noteType the note type discriminator
   * @param contentJson the bare note content JSON produced by the LLM
   */
  List<String> validateNote(String noteType, String contentJson);

  /** Thrown when AI-generated output does not conform to the FlashcardImportV1 schema. */
  class AiOutputSchemaViolationException extends RuntimeException {
    private final List<String> violations;

    public AiOutputSchemaViolationException(List<String> violations) {
      super("AI output failed FlashcardImportV1 schema validation: " + violations);
      this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
      return violations;
    }
  }
}
