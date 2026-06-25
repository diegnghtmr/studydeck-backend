package com.studydeck.domain.port.in;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AiChatPort.RagAnswer;
import java.util.List;

/**
 * Input port: grounded RAG chat with source citations.
 *
 * <p>The response cites the source chunks used to ground the answer. If no chat provider is
 * configured, returns a typed 503 error (never crashes on startup).
 */
public interface RagChatUseCase {

  RagAnswer execute(Command command);

  record Command(
      String message,
      OwnerId ownerId,
      List<DocumentId> documentIds,
      int topK,
      AiProviderConfig providerConfig) {}
}
