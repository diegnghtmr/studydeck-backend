package com.studydeck.domain.port.in;

import com.studydeck.domain.model.IngestStatus;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import java.util.List;

/** Input port: list source documents with optional ingest-status filter. */
public interface ListDocumentsQuery {

  Result execute(OwnerId ownerId, IngestStatus ingestStatus, int offset, int limit);

  record Result(List<SourceDocument> items, long total) {}
}
