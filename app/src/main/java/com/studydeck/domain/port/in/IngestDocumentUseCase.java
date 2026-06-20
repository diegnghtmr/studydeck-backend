package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.IngestJob;
import com.studydeck.domain.model.OwnerId;
import java.util.Map;

/**
 * Input port: trigger async ETL ingestion of a source document.
 *
 * <p>Returns immediately with an {@link IngestJob} in PENDING state (202 Accepted). The actual ETL
 * (split → embed → store) runs asynchronously.
 */
public interface IngestDocumentUseCase {

  IngestJob execute(Command command);

  record Command(
      DocumentId documentId,
      OwnerId ownerId,
      int targetChunkSize,
      int chunkOverlap,
      Map<String, Object> extraMetadata) {}
}
