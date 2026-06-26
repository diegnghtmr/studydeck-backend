package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort.AiOutputSchemaViolationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the schema-violation RETRY contract of {@link ImproveFlashcardService}. Mirrors {@link
 * GenerateFlashcardsServiceRetryTest}: re-ask the model on {@link AiOutputSchemaViolationException}
 * only, up to 3 total attempts; any other failure propagates immediately.
 */
class ImproveFlashcardServiceRetryTest {

  private AiChatPort chatPort;
  private AiSchemaValidationPort schemaValidator;
  private ImproveFlashcardService service;
  private final OwnerId ownerId = OwnerId.generate();

  private static final String IMPROVED_JSON = "{\"front\":\"Better Q?\",\"back\":\"Better A.\"}";

  private ImproveFlashcardUseCase.Command command() {
    return new ImproveFlashcardUseCase.Command(
        ownerId, "basic", "{\"front\":\"Q?\",\"back\":\"A.\"}", "Make it clearer", null);
  }

  @BeforeEach
  void setUp() {
    chatPort = mock(AiChatPort.class);
    schemaValidator = mock(AiSchemaValidationPort.class);
    service = new ImproveFlashcardService(chatPort, schemaValidator);
    when(chatPort.isAvailable()).thenReturn(true);
    when(chatPort.improveFlashcardRaw(anyString(), anyString(), anyString(), any()))
        .thenReturn(IMPROVED_JSON);
  }

  @Test
  @DisplayName("happy path: valid on first attempt → chat port called exactly once")
  void happyPathChatPortCalledOnce() {
    when(schemaValidator.validateNoteAndReturn(anyString(), anyString())).thenReturn(IMPROVED_JSON);

    var result = service.execute(command());

    assertThat(result.noteType()).isEqualTo("basic");
    assertThat(result.improvedJson()).isEqualTo(IMPROVED_JSON);
    verify(chatPort, times(1)).improveFlashcardRaw(anyString(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("schema violation on first attempt → re-asks model and succeeds on second")
  void schemaViolationOnFirstAttemptSucceedsOnSecond() {
    when(schemaValidator.validateNoteAndReturn(anyString(), anyString()))
        .thenThrow(new AiOutputSchemaViolationException(List.of("bad")))
        .thenReturn(IMPROVED_JSON);

    var result = service.execute(command());

    assertThat(result.improvedJson()).isEqualTo(IMPROVED_JSON);
    verify(chatPort, times(2)).improveFlashcardRaw(anyString(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("a non-schema exception from the model propagates immediately (not retried)")
  void nonSchemaExceptionIsNotRetried() {
    when(chatPort.improveFlashcardRaw(anyString(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");
    verify(chatPort, times(1)).improveFlashcardRaw(anyString(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("schema violation on all 3 attempts → rethrows after exactly 3 model calls")
  void schemaViolationOnAllAttemptsThrowsAfterThreeCalls() {
    when(schemaValidator.validateNoteAndReturn(anyString(), anyString()))
        .thenThrow(new AiOutputSchemaViolationException(List.of("bad")));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(AiOutputSchemaViolationException.class);
    verify(chatPort, times(3)).improveFlashcardRaw(anyString(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("unavailable provider is NOT retried — chat port is never called")
  void aiChatUnavailableIsNotRetried() {
    when(chatPort.isAvailable()).thenReturn(false);

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(AiChatPort.AiChatUnavailableException.class);
    verify(chatPort, never()).improveFlashcardRaw(anyString(), anyString(), anyString(), any());
  }
}
