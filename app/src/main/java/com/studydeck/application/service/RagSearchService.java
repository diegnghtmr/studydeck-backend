package com.studydeck.application.service;

import com.studydeck.domain.port.in.RagSearchUseCase;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import com.studydeck.domain.port.out.TextEmbeddingPort.SearchHit;
import java.util.List;

/**
 * Application service implementing the RagSearch use case.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class RagSearchService implements RagSearchUseCase {

  private final TextEmbeddingPort embeddingPort;

  public RagSearchService(TextEmbeddingPort embeddingPort) {
    this.embeddingPort = embeddingPort;
  }

  @Override
  public Result execute(Command command) {
    List<SearchHit> hits =
        embeddingPort.search(
            command.query(),
            command.ownerId(),
            command.documentIds() != null ? command.documentIds() : List.of(),
            command.topK(),
            command.minScore());
    return new Result(hits);
  }
}
