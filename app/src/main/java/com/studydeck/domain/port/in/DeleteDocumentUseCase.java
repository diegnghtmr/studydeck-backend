package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;

/** Input port: delete a source document and all associated chunks/embeddings. */
public interface DeleteDocumentUseCase {

  void execute(DocumentId id, OwnerId ownerId);
}
