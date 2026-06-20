package com.studydeck.infrastructure.adapter.in.web;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Validates a raw import JSON body against the committed JSON Schema Draft 2020-12.
 *
 * <p>This is the FIRST validation layer (structural). Domain validation (per-type rules) is the
 * SECOND layer, handled by {@link com.studydeck.domain.port.in.ValidateImportUseCase}.
 *
 * <p>The networknt validator uses {@code com.fasterxml.jackson.databind.JsonNode} internally, while
 * Spring Boot 4 uses {@code tools.jackson.databind.JsonNode}. To avoid cross-Jackson coupling, the
 * node is serialized to a JSON string and passed as {@link InputFormat#JSON}.
 */
@Component
class ImportSchemaValidator {

  private final com.networknt.schema.JsonSchema schema;

  ImportSchemaValidator() {
    try {
      var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
      InputStream schemaStream =
          new ClassPathResource("schemas/flashcard-import-v1.schema.json").getInputStream();
      this.schema = factory.getSchema(schemaStream);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load flashcard import JSON schema", e);
    }
  }

  /**
   * Returns the list of validation messages. Empty list means the JSON is structurally valid.
   *
   * @param json the parsed JSON node to validate (tools.jackson type, from Spring MVC)
   */
  List<String> validate(JsonNode json) {
    Set<ValidationMessage> messages = schema.validate(json.toString(), InputFormat.JSON);
    return messages.stream().map(ValidationMessage::getMessage).sorted().toList();
  }

  /** Returns true when the JSON passes schema validation with zero errors. */
  boolean isValid(JsonNode json) {
    return schema.validate(json.toString(), InputFormat.JSON).isEmpty();
  }
}
