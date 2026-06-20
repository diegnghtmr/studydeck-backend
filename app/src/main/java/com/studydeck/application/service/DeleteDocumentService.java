package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.DeleteDocumentUseCase;
import com.studydeck.domain.port.out.SourceDocumentRepository;

/**
 * Application service implementing the DeleteDocument use case.
 *
 * <p>Separate from {@link DocumentService} due to {@code execute(DocumentId, OwnerId)} return-type
 * collision with {@link GetDocumentService}.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class DeleteDocumentService implements DeleteDocumentUseCase {

  private final SourceDocumentRepository documentRepository;

  public DeleteDocumentService(SourceDocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  @Override
  public void execute(DocumentId id, OwnerId ownerId) {
    var doc =
        documentRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Document", id.toString()));
    if (!doc.getOwnerId().equals(ownerId)) {
      // Treat as not found to avoid disclosing document existence to non-owners.
      throw new NotFoundException("Document", id.toString());
    }
    documentRepository.deleteById(id);
  }
}
