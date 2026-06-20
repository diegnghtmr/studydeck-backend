package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.TextEmbeddingPort.SearchHit;
import java.util.List;

/**
 * Input port: perform vector similarity search over the owner's corpus.
 *
 * <p>Results are ALWAYS filtered by ownerId — no cross-owner data leakage possible.
 */
public interface RagSearchUseCase {

  Result execute(Command command);

  record Command(
      String query,
      OwnerId ownerId,
      List<DocumentId> documentIds,
      int topK,
      Double minScore,
      boolean includeContent) {}

  record Result(List<SearchHit> hits) {}
}
