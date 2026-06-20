package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.port.in.GetDocumentQuery;
import com.studydeck.domain.port.out.SourceDocumentRepository;

/**
 * Application service implementing the GetDocument query.
 *
 * <p>Separate from {@link DocumentService} due to {@code execute(DocumentId, OwnerId)} return-type
 * collision with {@link DeleteDocumentService}.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class GetDocumentService implements GetDocumentQuery {

  private final SourceDocumentRepository documentRepository;

  public GetDocumentService(SourceDocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  @Override
  public SourceDocument execute(DocumentId id, OwnerId ownerId) {
    var doc =
        documentRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Document", id.toString()));
    if (!doc.getOwnerId().equals(ownerId)) {
      // Treat as not found to avoid disclosing document existence to non-owners.
      throw new NotFoundException("Document", id.toString());
    }
    return doc;
  }
}
