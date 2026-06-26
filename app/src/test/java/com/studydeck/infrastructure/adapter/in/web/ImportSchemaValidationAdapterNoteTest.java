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
  @DisplayName("generate: a <think> reasoning block before the JSON is ignored (Qwen3)")
  void generateThinkBlockIsIgnored() {
    // The think block holds a non-JSON brace group; the real envelope is the last parseable
    // top-level object and wins (the block is ignored, not textually stripped from the primary
    // path).
    String raw = "<think>I will build it. {not: real}</think>\n" + VALID_ENVELOPE;

    String result = adapter.validateAndReturn(raw);

    assertThat(result).isEqualTo(VALID_ENVELOPE);
  }

  @Test
  @DisplayName("improve: a LARGE valid JSON inside <think> does NOT beat the small bare note")
  void improveLargeValidThinkJsonDoesNotBeatNote() {
    // Regression for the 'longest object' flaw: the reasoning JSON is far larger than the real
    // payload. "Longest" would return the reasoning (schema fail → 3 retries exhausted); "last
    // top-level parseable" correctly returns the small note.
    String note = "{\"front\":\"Q\",\"back\":\"A\"}";
    String raw =
        "<think>{\"analysis\":\"the card is ambiguous and should be clarified\","
            + "\"recommendation\":\"rewrite the front to be more specific and concise\","
            + "\"confidence\":0.9}</think>"
            + note;

    String result = adapter.validateNoteAndReturn("basic", raw);

    assertThat(result).isEqualTo(note);
  }

  @Test
  @DisplayName("generate: a LARGE valid reasoning object before the envelope is ignored")
  void generateLargeValidReasoningObjectBeforeEnvelopeIsIgnored() {
    String raw =
        "{\"plan\":\"outline\",\"steps\":[\"intro\",\"detail\",\"summary\"],"
            + "\"rationale\":\"a verbose reasoning object that is longer than the envelope itself\"}\n"
            + VALID_ENVELOPE;

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

  // ---- The actual 422 bug: unwrapped reasoning prose with stray braces -------

  @Test
  @DisplayName("generate: a stray '{' in reasoning prose BEFORE the JSON is skipped, not captured")
  void generateProseBraceBeforeJsonIsSkipped() {
    // Reproduces the production 422: a naive first-'{'..last-'}' window would capture
    // "{tricky}. Here:\n{envelope}" and fail to parse on the '.'. Balanced matching skips the
    // non-JSON prose brace and finds the real envelope.
    String raw = "Hmm, the format is {tricky}. Here:\n" + VALID_ENVELOPE;

    String result = adapter.validateAndReturn(raw);

    assertThat(result).isEqualTo(VALID_ENVELOPE);
  }

  @Test
  @DisplayName("generate: trailing reasoning prose after the JSON is ignored")
  void generateTrailingProseIsIgnored() {
    String raw = VALID_ENVELOPE + "\n\nThat should cover the basics!";

    String result = adapter.validateAndReturn(raw);

    assertThat(result).isEqualTo(VALID_ENVELOPE);
  }

  @Test
  @DisplayName("generate: a small valid decoy object in reasoning is ignored for the real envelope")
  void generateDecoyObjectBeforeEnvelopeIsIgnored() {
    // Reproduces the residual 422: reasoning models emit a small PARSEABLE object (e.g. the deck
    // title) in their chain-of-thought before the full envelope. "First parseable" would grab the
    // decoy {"title":...} (failing schema with "required property 'deck' not found"); the LAST
    // top-level parseable object is the real envelope.
    String raw =
        "Let me plan the deck: {\"title\": \"Photosynthesis\"}. Now the full output:\n"
            + VALID_ENVELOPE;

    String result = adapter.validateAndReturn(raw);

    assertThat(result).isEqualTo(VALID_ENVELOPE);
  }

  @Test
  @DisplayName("generate: a '{' inside a JSON string value is preserved (string-aware matching)")
  void generateBraceInsideStringValueIsPreserved() {
    String envelopeWithBraceInString =
        "{\"schemaVersion\":\"1.0\",\"deck\":{\"title\":\"Use {x} maps\"},"
            + "\"notes\":[{\"noteType\":\"basic\",\"front\":\"What is {{c1}}?\",\"back\":\"A marker\"}]}";

    String result = adapter.validateAndReturn(envelopeWithBraceInString);

    assertThat(result).isEqualTo(envelopeWithBraceInString);
  }
}
