package com.studydeck.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GenerateFlashcardsServiceProviderConfigTest {

  private AiChatPort chatPort;
  private AiSchemaValidationPort schemaValidator;
  private GenerateFlashcardsService service;
  private final OwnerId ownerId = OwnerId.generate();

  private static final String VALID_JSON =
      """
      {"schemaVersion":"1.0","deck":{"title":"T"},"notes":[{"noteType":"basic","front":"Q","back":"A"}]}
      """;

  @BeforeEach
  void setUp() {
    chatPort = mock(AiChatPort.class);
    schemaValidator = mock(AiSchemaValidationPort.class);
    service = new GenerateFlashcardsService(chatPort, schemaValidator);
    when(chatPort.isAvailable()).thenReturn(true);
    when(chatPort.generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any()))
        .thenReturn(VALID_JSON);
    when(schemaValidator.validateAndReturn(anyString())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Nested
  @DisplayName("providerConfig forwarding")
  class ProviderConfigForwarding {

    @Test
    @DisplayName("when providerConfig is set on Command, it is forwarded to port")
    void providerConfigIsForwarded() {
      var config = new AiProviderConfig("https://api.groq.com/v1", "sk-groq-key", "llama3-70b");
      var cmd =
          new GenerateFlashcardsUseCase.Command(
              ownerId, "source", null, List.of("basic"), 5, config);

      service.execute(cmd);

      verify(chatPort).generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), eq(config));
    }

    @Test
    @DisplayName("when providerConfig is null on Command, port is called with null")
    void nullProviderConfigForwarded() {
      var cmd =
          new GenerateFlashcardsUseCase.Command(ownerId, "source", null, List.of("basic"), 5, null);

      service.execute(cmd);

      verify(chatPort).generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), isNull());
    }
  }
}
