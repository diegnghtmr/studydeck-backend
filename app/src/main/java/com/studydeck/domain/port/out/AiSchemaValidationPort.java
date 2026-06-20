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
