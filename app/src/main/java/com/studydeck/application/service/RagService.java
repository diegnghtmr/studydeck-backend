package com.studydeck.application.service;

import com.studydeck.domain.port.in.RagChatUseCase;
import com.studydeck.domain.port.in.RagSearchUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiChatPort.ContextChunk;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import com.studydeck.domain.port.out.TextEmbeddingPort.SearchHit;
import java.util.List;

/**
 * Application service for RAG search and RAG chat use cases.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.AiConfiguration}.
 */
public final class RagService implements RagSearchUseCase, RagChatUseCase {

  private final TextEmbeddingPort embeddingPort;
  private final AiChatPort chatPort;

  public RagService(TextEmbeddingPort embeddingPort, AiChatPort chatPort) {
    this.embeddingPort = embeddingPort;
    this.chatPort = chatPort;
  }

  // ---------------------------------------------------------------
  // RagSearchUseCase
  // ---------------------------------------------------------------

  @Override
  public RagSearchUseCase.Result execute(RagSearchUseCase.Command command) {
    List<SearchHit> hits =
        embeddingPort.search(
            command.query(),
            command.ownerId(),
            command.documentIds() != null ? command.documentIds() : List.of(),
            command.topK(),
            command.minScore());
    return new RagSearchUseCase.Result(hits);
  }

  // ---------------------------------------------------------------
  // RagChatUseCase
  // ---------------------------------------------------------------

  @Override
  public AiChatPort.RagAnswer execute(RagChatUseCase.Command command) {
    // A per-request BYOK override (providerConfig) carries its own baseUrl + apiKey + model,
    // so it can reach a model even when no global provider is configured. Only reject when
    // there is no override AND no global provider available.
    if (command.providerConfig() == null && !chatPort.isAvailable()) {
      throw new AiChatPort.AiChatUnavailableException();
    }

    // 1. Retrieve relevant chunks from the vector store (owner-scoped)
    List<SearchHit> hits =
        embeddingPort.search(
            command.message(),
            command.ownerId(),
            command.documentIds() != null ? command.documentIds() : List.of(),
            command.topK(),
            null);

    // 2. Convert hits to context chunks for the chat port
    List<ContextChunk> context =
        hits.stream()
            .map(
                h ->
                    new ContextChunk(
                        h.chunkId().toString(), h.documentId().toString(), h.content()))
            .toList();

    // 3. Delegate to chat port (Spring AI adapter)
    return chatPort.ragChat(
        command.message(), command.ownerId(), context, command.providerConfig());
  }
}
