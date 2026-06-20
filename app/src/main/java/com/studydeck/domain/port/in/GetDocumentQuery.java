package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;

/** Input port: retrieve a single source document by ID (owner-scoped). */
public interface GetDocumentQuery {

  SourceDocument execute(DocumentId id, OwnerId ownerId);
}
