package com.studydeck.domain.port.in;

import com.studydeck.domain.model.EmbeddingRecord;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Input port: list embedding metadata for an owner. */
public interface ListEmbeddingsQuery {

  Result execute(OwnerId ownerId, int offset, int limit);

  record Result(List<EmbeddingRecord> items, long total) {}
}
