package com.studydeck.infrastructure.adapter.out.ai;

import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.infrastructure.adapter.in.web.ImportSchemaValidator;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    List<String> violations = validate(json);
    if (!violations.isEmpty()) {
      throw new AiOutputSchemaViolationException(violations);
    }
    return json;
  }

  @Override
  public List<String> validate(String json) {
    try {
      JsonNode node = objectMapper.readTree(json);
      return schemaValidator.validate(node);
    } catch (Exception e) {
      return List.of("Failed to parse AI output as JSON: " + e.getMessage());
    }
  }
}
