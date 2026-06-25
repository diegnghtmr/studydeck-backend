package com.studydeck.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ImproveFlashcardServiceProviderConfigTest {

  private AiChatPort chatPort;
  private AiSchemaValidationPort schemaValidator;
  private ImproveFlashcardService service;
  private final OwnerId ownerId = OwnerId.generate();

  private static final String IMPROVED_JSON = "{\"front\": \"Better Q?\", \"back\": \"Better A.\"}";

  @BeforeEach
  void setUp() {
    chatPort = mock(AiChatPort.class);
    schemaValidator = mock(AiSchemaValidationPort.class);
    service = new ImproveFlashcardService(chatPort, schemaValidator);
    when(chatPort.isAvailable()).thenReturn(true);
    when(chatPort.improveFlashcardRaw(anyString(), anyString(), anyString(), any()))
        .thenReturn(IMPROVED_JSON);
    when(schemaValidator.validateNoteAndReturn(anyString(), anyString()))
        .thenAnswer(inv -> inv.getArgument(1));
  }

  @Nested
  @DisplayName("providerConfig forwarding")
  class ProviderConfigForwarding {

    @Test
    @DisplayName("when providerConfig is set on Command, it is forwarded to port")
    void providerConfigIsForwarded() {
      var config = new AiProviderConfig("https://api.groq.com/v1", "sk-groq-key", "llama3-70b");
      var cmd =
          new ImproveFlashcardUseCase.Command(
              ownerId, "basic", "{\"front\":\"Q?\",\"back\":\"A.\"}", "Make it clearer", config);

      service.execute(cmd);

      verify(chatPort).improveFlashcardRaw(anyString(), anyString(), anyString(), eq(config));
    }

    @Test
    @DisplayName("when providerConfig is null on Command, port is called with null")
    void nullProviderConfigForwarded() {
      var cmd =
          new ImproveFlashcardUseCase.Command(
              ownerId, "basic", "{\"front\":\"Q?\",\"back\":\"A.\"}", "Make it clearer", null);

      service.execute(cmd);

      verify(chatPort).improveFlashcardRaw(anyString(), anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName(
        "when providerConfig is set, it bypasses the global-provider availability check (BYOK)")
    void providerConfigBypassesGlobalAvailabilityCheck() {
      // No global provider configured: the BYOK override is the only way to reach a model.
      when(chatPort.isAvailable()).thenReturn(false);
      var config =
          new AiProviderConfig(
              "https://api.groq.com/openai/v1", "sk-groq-key", "llama-3.3-70b-versatile");
      var cmd =
          new ImproveFlashcardUseCase.Command(
              ownerId, "basic", "{\"front\":\"Q?\",\"back\":\"A.\"}", "Make it clearer", config);

      service.execute(cmd);

      verify(chatPort).improveFlashcardRaw(anyString(), anyString(), anyString(), eq(config));
    }

    @Test
    @DisplayName("when providerConfig is null and no global provider, it throws unavailable")
    void nullProviderConfigWithoutGlobalThrows() {
      when(chatPort.isAvailable()).thenReturn(false);
      var cmd =
          new ImproveFlashcardUseCase.Command(
              ownerId, "basic", "{\"front\":\"Q?\",\"back\":\"A.\"}", "Make it clearer", null);

      org.junit.jupiter.api.Assertions.assertThrows(
          AiChatPort.AiChatUnavailableException.class, () -> service.execute(cmd));
    }
  }
}
