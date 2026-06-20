package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiFlashcardService}.
 *
 * <p>TDD contract:
 *
 * <ol>
 *   <li>generate-flashcards with a STUB ChatModel returns schema-VALID proposals
 *       (requiresApproval=true)
 *   <li>A malformed stub output IS REJECTED by the schema validator
 *   <li>When no chat provider, throws AiChatUnavailableException
 * </ol>
 */
class AiFlashcardServiceTest {

  private AiChatPort chatPort;
  private AiSchemaValidationPort schemaValidator;
  private GenerateFlashcardsService generateService;
  private ImproveFlashcardService improveService;
  private final OwnerId ownerId = OwnerId.generate();

  private static final String VALID_FLASHCARD_JSON =
      """
      {
        "schemaVersion": "1.0",
        "deck": {"title": "Biology"},
        "notes": [
          {"noteType": "basic", "front": "What is mitosis?", "back": "Cell division process"}
        ]
      }
      """;

  private static final String INVALID_FLASHCARD_JSON =
      """
      {
        "schemaVersion": "2.0",
        "deck": {},
        "notes": []
      }
      """;

  @BeforeEach
  void setUp() {
    chatPort = mock(AiChatPort.class);
    schemaValidator = mock(AiSchemaValidationPort.class);
    generateService = new GenerateFlashcardsService(chatPort, schemaValidator);
    improveService = new ImproveFlashcardService(chatPort, schemaValidator);
  }

  @Nested
  @DisplayName("generate-flashcards")
  class GenerateFlashcards {

    @Test
    @DisplayName("valid AI output returns schema-valid proposals with requiresApproval=true")
    void validOutputReturnsProposals() {
      when(chatPort.isAvailable()).thenReturn(true);
      when(chatPort.generateFlashcardsRaw(anyString(), any(), anyList(), anyInt()))
          .thenReturn(VALID_FLASHCARD_JSON);
      when(schemaValidator.validateAndReturn(VALID_FLASHCARD_JSON))
          .thenReturn(VALID_FLASHCARD_JSON);

      var result =
          generateService.execute(
              new GenerateFlashcardsUseCase.Command(
                  ownerId, "Cell biology text", "Biology", List.of("basic"), 5));

      assertThat(result.proposalsJson()).isEqualTo(VALID_FLASHCARD_JSON);
      assertThat(result.requiresApproval()).isTrue();
    }

    @Test
    @DisplayName("malformed AI output is REJECTED by schema validator")
    void malformedOutputIsRejected() {
      when(chatPort.isAvailable()).thenReturn(true);
      when(chatPort.generateFlashcardsRaw(anyString(), any(), anyList(), anyInt()))
          .thenReturn(INVALID_FLASHCARD_JSON);
      when(schemaValidator.validateAndReturn(INVALID_FLASHCARD_JSON))
          .thenThrow(
              new AiSchemaValidationPort.AiOutputSchemaViolationException(
                  List.of("schemaVersion must be 1.0", "notes: minItems 1")));

      assertThatThrownBy(
              () ->
                  generateService.execute(
                      new GenerateFlashcardsUseCase.Command(
                          ownerId, "some text", null, List.of("basic"), 5)))
          .isInstanceOf(AiSchemaValidationPort.AiOutputSchemaViolationException.class)
          .hasMessageContaining("FlashcardImportV1 schema validation");
    }

    @Test
    @DisplayName("throws AiChatUnavailableException when no provider configured")
    void throwsWhenNoChatProvider() {
      when(chatPort.isAvailable()).thenReturn(false);

      assertThatThrownBy(
              () ->
                  generateService.execute(
                      new GenerateFlashcardsUseCase.Command(
                          ownerId, "text", null, List.of("basic"), 5)))
          .isInstanceOf(AiChatPort.AiChatUnavailableException.class);
    }
  }

  @Nested
  @DisplayName("improve-flashcard")
  class ImproveFlashcard {

    @Test
    @DisplayName("valid improved output is returned with noteType")
    void validImprovedOutputReturned() {
      String improvedJson = "{\"front\": \"Better Q?\", \"back\": \"Better A.\"}";
      when(chatPort.isAvailable()).thenReturn(true);
      when(chatPort.improveFlashcardRaw(anyString(), anyString(), anyString()))
          .thenReturn(improvedJson);
      when(schemaValidator.validateAndReturn(improvedJson)).thenReturn(improvedJson);

      var result =
          improveService.execute(
              new com.studydeck.domain.port.in.ImproveFlashcardUseCase.Command(
                  ownerId, "basic", "{\"front\":\"Q?\",\"back\":\"A.\"}", "Make it clearer"));

      assertThat(result.noteType()).isEqualTo("basic");
      assertThat(result.improvedJson()).isEqualTo(improvedJson);
    }
  }
}
