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
import com.studydeck.domain.port.in.RagChatUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RagChatServiceProviderConfigTest {

  private TextEmbeddingPort embeddingPort;
  private AiChatPort chatPort;
  private RagChatService service;
  private final OwnerId ownerId = OwnerId.generate();

  @BeforeEach
  void setUp() {
    embeddingPort = mock(TextEmbeddingPort.class);
    chatPort = mock(AiChatPort.class);
    service = new RagChatService(embeddingPort, chatPort);
    when(chatPort.isAvailable()).thenReturn(true);
    when(embeddingPort.search(anyString(), any(), anyList(), anyInt(), any()))
        .thenReturn(List.of());
    when(chatPort.ragChat(anyString(), any(), anyList(), any()))
        .thenReturn(new AiChatPort.RagAnswer("answer", List.of()));
  }

  @Nested
  @DisplayName("providerConfig forwarding")
  class ProviderConfigForwarding {

    @Test
    @DisplayName("when providerConfig is set on Command, it is forwarded to port")
    void providerConfigIsForwarded() {
      var config = new AiProviderConfig("https://api.groq.com/v1", "sk-groq-key", "llama3-70b");
      var cmd = new RagChatUseCase.Command("question", ownerId, List.of(), 5, config);

      service.execute(cmd);

      verify(chatPort).ragChat(anyString(), any(), anyList(), eq(config));
    }

    @Test
    @DisplayName("when providerConfig is null on Command, port is called with null")
    void nullProviderConfigForwarded() {
      var cmd = new RagChatUseCase.Command("question", ownerId, List.of(), 5, null);

      service.execute(cmd);

      verify(chatPort).ragChat(anyString(), any(), anyList(), isNull());
    }
  }
}
