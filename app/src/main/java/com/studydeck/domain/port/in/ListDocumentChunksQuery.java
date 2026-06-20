package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DocumentChunk;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Input port: list chunks for a specific document (owner-scoped). */
public interface ListDocumentChunksQuery {

  Result execute(DocumentId documentId, OwnerId ownerId, int offset, int limit);

  record Result(List<DocumentChunk> items, long total) {}
}
