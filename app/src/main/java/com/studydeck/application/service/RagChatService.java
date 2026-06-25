package com.studydeck.application.service;

import com.studydeck.domain.port.in.RagChatUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiChatPort.ContextChunk;
import com.studydeck.domain.port.out.AiChatPort.RagAnswer;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import com.studydeck.domain.port.out.TextEmbeddingPort.SearchHit;
import java.util.List;

/**
 * Application service implementing the RagChat use case.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class RagChatService implements RagChatUseCase {

  private final TextEmbeddingPort embeddingPort;
  private final AiChatPort chatPort;

  public RagChatService(TextEmbeddingPort embeddingPort, AiChatPort chatPort) {
    this.embeddingPort = embeddingPort;
    this.chatPort = chatPort;
  }

  @Override
  public RagAnswer execute(Command command) {
    if (!chatPort.isAvailable()) {
      throw new AiChatPort.AiChatUnavailableException();
    }

    // 1. Retrieve relevant chunks (owner-scoped)
    List<SearchHit> hits =
        embeddingPort.search(
            command.message(),
            command.ownerId(),
            command.documentIds() != null ? command.documentIds() : List.of(),
            command.topK(),
            null);

    // 2. Convert hits to context chunks
    List<ContextChunk> context =
        hits.stream()
            .map(
                h ->
                    new ContextChunk(
                        h.chunkId().toString(), h.documentId().toString(), h.content()))
            .toList();

    // 3. Delegate to chat port
    return chatPort.ragChat(
        command.message(), command.ownerId(), context, command.providerConfig());
  }
}
