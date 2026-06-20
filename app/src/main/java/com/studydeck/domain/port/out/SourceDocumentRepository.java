package com.studydeck.domain.port.out;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.IngestStatus;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import java.util.List;
import java.util.Optional;

/** Output port for persisting and querying {@link SourceDocument} entities. */
public interface SourceDocumentRepository {

  void save(SourceDocument document);

  Optional<SourceDocument> findById(DocumentId id);

  List<SourceDocument> findAll(OwnerId ownerId, IngestStatus ingestStatus, int offset, int limit);

  long countAll(OwnerId ownerId, IngestStatus ingestStatus);

  void deleteById(DocumentId id);
}
