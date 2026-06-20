package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import java.util.Map;

/** Input port: register a new source document for RAG ingestion. */
public interface CreateDocumentUseCase {

  SourceDocument execute(Command command);

  record Command(
      OwnerId ownerId,
      String title,
      String sourceType,
      String mimeType,
      String originalFilename,
      String textContent,
      String externalUrl,
      Long sizeBytes,
      Map<String, Object> metadata) {}
}
