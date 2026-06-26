package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort.AiOutputSchemaViolationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the schema-violation RETRY contract of {@link GenerateFlashcardsService}. Reasoning
 * models are non-deterministic, so the service re-asks the model (up to 3 total attempts) when the
 * output violates the FlashcardImportV1 schema. Only {@link AiOutputSchemaViolationException} is
 * retried; any other failure propagates immediately.
 */
class GenerateFlashcardsServiceRetryTest {

  private AiChatPort chatPort;
  private AiSchemaValidationPort schemaValidator;
  private GenerateFlashcardsService service;
  private final OwnerId ownerId = OwnerId.generate();

  private static final String VALID_JSON =
      "{\"schemaVersion\":\"1.0\",\"deck\":{\"title\":\"T\"},"
          + "\"notes\":[{\"noteType\":\"basic\",\"front\":\"Q\",\"back\":\"A\"}]}";

  private GenerateFlashcardsUseCase.Command command() {
    return new GenerateFlashcardsUseCase.Command(
        ownerId, "source", null, List.of("basic"), 5, null);
  }

  @BeforeEach
  void setUp() {
    chatPort = mock(AiChatPort.class);
    schemaValidator = mock(AiSchemaValidationPort.class);
    service = new GenerateFlashcardsService(chatPort, schemaValidator);
    when(chatPort.isAvailable()).thenReturn(true);
    when(chatPort.generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any()))
        .thenReturn(VALID_JSON);
  }

  @Test
  @DisplayName("happy path: valid on first attempt → chat port called exactly once")
  void happyPathChatPortCalledOnce() {
    when(schemaValidator.validateAndReturn(anyString())).thenReturn(VALID_JSON);

    var result = service.execute(command());

    assertThat(result.proposalsJson()).isEqualTo(VALID_JSON);
    verify(chatPort, times(1))
        .generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any());
  }

  @Test
  @DisplayName("schema violation on first attempt → re-asks model and succeeds on second")
  void schemaViolationOnFirstAttemptSucceedsOnSecond() {
    when(schemaValidator.validateAndReturn(anyString()))
        .thenThrow(new AiOutputSchemaViolationException(List.of("bad")))
        .thenReturn(VALID_JSON);

    var result = service.execute(command());

    assertThat(result.proposalsJson()).isEqualTo(VALID_JSON);
    verify(chatPort, times(2))
        .generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any());
  }

  @Test
  @DisplayName("schema violation on all 3 attempts → rethrows after exactly 3 model calls")
  void schemaViolationOnAllAttemptsThrowsAfterThreeCalls() {
    when(schemaValidator.validateAndReturn(anyString()))
        .thenThrow(new AiOutputSchemaViolationException(List.of("bad")));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(AiOutputSchemaViolationException.class);
    verify(chatPort, times(3))
        .generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any());
  }

  @Test
  @DisplayName("unavailable provider is NOT retried — chat port is never called")
  void aiChatUnavailableIsNotRetried() {
    when(chatPort.isAvailable()).thenReturn(false);

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(AiChatPort.AiChatUnavailableException.class);
    verify(chatPort, never()).generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any());
  }

  @Test
  @DisplayName("a non-schema exception from the model propagates immediately (not retried)")
  void nonSchemaExceptionIsNotRetried() {
    when(chatPort.generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any()))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");
    verify(chatPort, times(1))
        .generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any());
  }
}
