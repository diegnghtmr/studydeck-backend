package com.studydeck.domain.port.out;

import com.studydeck.domain.model.ChunkId;
import com.studydeck.domain.model.DocumentChunk;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.Optional;

/** Output port for persisting and querying {@link DocumentChunk} entities. */
public interface DocumentChunkRepository {

  void save(DocumentChunk chunk);

  void saveAll(List<DocumentChunk> chunks);

  Optional<DocumentChunk> findById(ChunkId id);

  List<DocumentChunk> findByDocumentId(DocumentId documentId, int offset, int limit);

  long countByDocumentId(DocumentId documentId);

  List<DocumentChunk> findByOwner(OwnerId ownerId, String search, int offset, int limit);

  long countByOwner(OwnerId ownerId, String search);

  void deleteByDocumentId(DocumentId documentId);
}
