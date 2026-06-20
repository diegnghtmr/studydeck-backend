package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DocumentChunk;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Input port: list chunks across all documents for an owner, with optional text search. */
public interface ListChunksQuery {

  Result execute(OwnerId ownerId, String search, int offset, int limit);

  record Result(List<DocumentChunk> items, long total) {}
}
