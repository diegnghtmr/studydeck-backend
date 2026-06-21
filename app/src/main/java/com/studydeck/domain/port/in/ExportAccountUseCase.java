package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.model.UserAccount;
import java.time.Instant;
import java.util.List;

/**
 * Input port — exports all data owned by the authenticated user (GDPR data portability).
 *
 * <p>The result aggregates the account record, per-deck flashcard payloads, and source documents.
 * No web or Spring types leak into this interface.
 */
public interface ExportAccountUseCase {

  Result execute(OwnerId ownerId);

  /**
   * Aggregated export result.
   *
   * @param account the user account
   * @param decks per-deck flashcard payloads (same shape as {@link ExportDeckUseCase} output)
   * @param documents source documents owned by the user
   * @param exportedAt timestamp of the export
   */
  record Result(
      UserAccount account,
      List<ValidateImportUseCase.ImportPayload> decks,
      List<SourceDocument> documents,
      Instant exportedAt) {}
}
