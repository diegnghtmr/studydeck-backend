package com.studydeck.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.port.out.AiSchemaValidationPort.AiOutputSchemaViolationException;
import com.studydeck.infrastructure.adapter.out.ai.ImportSchemaValidationAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Focused unit test for the bare-note validation path of {@link ImportSchemaValidationAdapter}.
 *
 * <p>The improve-flashcard prompt returns BARE note content (e.g. {@code {"front":"Q","back":"A"}})
 * with NO {@code noteType} discriminator and NO FlashcardImportV1 envelope. {@code
 * validateNoteAndReturn} must wrap the bare content into a minimal valid envelope, inject the
 * discriminator, validate the envelope, and return the original bare content unchanged.
 *
 * <p>This test lives in the {@code ...adapter.in.web} package to access the package-private {@link
 * ImportSchemaValidator} constructor, validating against the REAL committed schema.
 */
class ImportSchemaValidationAdapterNoteTest {

  private ImportSchemaValidationAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ImportSchemaValidationAdapter(new ImportSchemaValidator(), new JsonMapper());
  }

  @Test
  @DisplayName("bare basic note validates OK and returns the original bare content")
  void bareBasicNoteIsValid() {
    String bare = "{\"front\":\"Q\",\"back\":\"A\"}";

    String result = adapter.validateNoteAndReturn("basic", bare);

    assertThat(result).isEqualTo(bare);
  }

  @Test
  @DisplayName("bare cloze note validates OK")
  void bareClozeNoteIsValid() {
    String bare = "{\"text\":\"a {{c1::b}} c\"}";

    String result = adapter.validateNoteAndReturn("cloze", bare);

    assertThat(result).isEqualTo(bare);
  }

  @Test
  @DisplayName("basic note missing required 'back' is REJECTED")
  void basicNoteMissingFieldIsRejected() {
    String bare = "{\"front\":\"Q\"}";

    assertThatThrownBy(() -> adapter.validateNoteAndReturn("basic", bare))
        .isInstanceOf(AiOutputSchemaViolationException.class);
  }

  @Test
  @DisplayName("basic note with extra unknown key is REJECTED (additionalProperties:false)")
  void basicNoteWithExtraKeyIsRejected() {
    String bare = "{\"front\":\"Q\",\"back\":\"A\",\"type\":\"basic\"}";

    assertThatThrownBy(() -> adapter.validateNoteAndReturn("basic", bare))
        .isInstanceOf(AiOutputSchemaViolationException.class);
  }

  // ---- LLM output sanitization (reasoning models / code fences) --------------

  private static final String VALID_ENVELOPE =
      "{\"schemaVersion\":\"1.0\",\"deck\":{\"title\":\"T\"},"
          + "\"notes\":[{\"noteType\":\"basic\",\"front\":\"Q\",\"back\":\"A\"}]}";

  @Test
  @DisplayName("generate: <think> reasoning block is stripped before parsing (Qwen3)")
  void generateThinkBlockIsStripped() {
    // The think block contains stray braces to ensure the block is REMOVED, not merely trimmed.
    String raw = "<think>I will build it. {not: real}</think>\n" + VALID_ENVELOPE;

    String result = adapter.validateAndReturn(raw);

    assertThat(result).isEqualTo(VALID_ENVELOPE);
  }

  @Test
  @DisplayName("generate: markdown ```json code fence is stripped before parsing")
  void generateCodeFenceIsStripped() {
    String raw = "```json\n" + VALID_ENVELOPE + "\n```";

    String result = adapter.validateAndReturn(raw);

    assertThat(result).isEqualTo(VALID_ENVELOPE);
  }

  @Test
  @DisplayName("improve: <think> reasoning block is stripped before validating bare note")
  void improveThinkBlockIsStripped() {
    String inner = "{\"front\":\"Q\",\"back\":\"A\"}";
    String raw = "<think>reasoning {x:1}</think>" + inner;

    String result = adapter.validateNoteAndReturn("basic", raw);

    assertThat(result).isEqualTo(inner);
  }

  @Test
  @DisplayName("a literal <think>...</think> INSIDE card content is preserved, not stripped")
  void thinkTagInsideContentIsPreserved() {
    // A flashcard legitimately about reasoning tags must keep its content intact.
    String bare = "{\"front\":\"What does <think>x</think> mean?\",\"back\":\"A reasoning block\"}";

    String result = adapter.validateNoteAndReturn("basic", bare);

    assertThat(result).isEqualTo(bare);
  }

  @Test
  @DisplayName("blank / whitespace-only output is rejected cleanly (no NPE)")
  void blankOutputIsRejectedCleanly() {
    assertThatThrownBy(() -> adapter.validateNoteAndReturn("basic", "   "))
        .isInstanceOf(AiOutputSchemaViolationException.class);
  }
}
