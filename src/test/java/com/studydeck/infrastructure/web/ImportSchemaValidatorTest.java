package com.studydeck.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Schema contract test — verifies that the committed JSON Schema file validates expected payloads
 * correctly and rejects invalid ones.
 */
class ImportSchemaValidatorTest {

  private static com.networknt.schema.JsonSchema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    InputStream stream =
        new ClassPathResource("schemas/flashcard-import-v1.schema.json").getInputStream();
    schema = factory.getSchema(stream);
  }

  @Test
  @DisplayName("valid basic note passes schema validation")
  void validBasicNote() {
    String payload =
        """
        {
          "schemaVersion": "1.0",
          "deck": {"title": "Test"},
          "notes": [
            {"noteType": "basic", "front": "Front", "back": "Back"}
          ]
        }
        """;

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    if (!messages.isEmpty()) {
      messages.forEach(m -> System.out.println("SCHEMA ERROR: " + m.getMessage()));
    }
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("all 5 note types in a single payload pass schema validation")
  void allFiveNoteTypes() {
    String payload =
        """
        {
          "schemaVersion": "1.0",
          "deck": {"title": "Biology 101", "description": "A biology deck", "tags": ["bio"]},
          "notes": [
            {"noteType": "basic", "front": "What is a cell?", "back": "The basic unit of life.", "tags": ["cell"]},
            {"noteType": "reversed", "front": "Nucleus", "back": "Controls cell activities.", "tags": ["nucleus"]},
            {"noteType": "cloze", "text": "The {{c1::mitochondria}} is the powerhouse of the {{c2::cell}}."},
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

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    if (!messages.isEmpty()) {
      messages.forEach(m -> System.out.println("SCHEMA ERROR: " + m.getMessage()));
    }
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("missing schemaVersion fails schema validation")
  void missingSchemaVersion() {
    String payload =
        """
        {
          "deck": {"title": "Test"},
          "notes": [{"noteType": "basic", "front": "F", "back": "B"}]
        }
        """;

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    assertThat(messages).isNotEmpty();
  }

  @Test
  @DisplayName("wrong schemaVersion fails schema validation")
  void wrongSchemaVersion() {
    String payload =
        """
        {
          "schemaVersion": "2.0",
          "deck": {"title": "Test"},
          "notes": [{"noteType": "basic", "front": "F", "back": "B"}]
        }
        """;

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    assertThat(messages).isNotEmpty();
  }

  @Test
  @DisplayName("empty notes array fails schema validation")
  void emptyNotesArray() {
    String payload =
        """
        {
          "schemaVersion": "1.0",
          "deck": {"title": "Test"},
          "notes": []
        }
        """;

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    assertThat(messages).isNotEmpty();
  }

  @Test
  @DisplayName("MC note with only 3 options fails schema validation")
  void mcWith3OptionsFails() {
    String payload =
        """
        {
          "schemaVersion": "1.0",
          "deck": {"title": "Test"},
          "notes": [{
            "noteType": "multiple-choice",
            "question": "Q?",
            "options": [
              {"key": "A", "text": "One"},
              {"key": "B", "text": "Two"},
              {"key": "C", "text": "Three"}
            ],
            "correctOptionKeys": ["A"]
          }]
        }
        """;

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    assertThat(messages).isNotEmpty();
  }

  @Test
  @DisplayName("cloze note without deletion pattern fails schema validation")
  void clozeWithoutPattern() {
    String payload =
        """
        {
          "schemaVersion": "1.0",
          "deck": {"title": "Test"},
          "notes": [{"noteType": "cloze", "text": "No deletion markers here."}]
        }
        """;

    Set<ValidationMessage> messages = schema.validate(payload, InputFormat.JSON);
    assertThat(messages).isNotEmpty();
  }
}
