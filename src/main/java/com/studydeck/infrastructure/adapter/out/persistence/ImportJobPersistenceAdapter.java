package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.ImportJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link ImportJobRepository}. */
@Transactional
class ImportJobPersistenceAdapter implements ImportJobRepository {

  private final ImportJobJpaRepository jpaRepo;

  ImportJobPersistenceAdapter(ImportJobJpaRepository jpaRepo) {
    this.jpaRepo = jpaRepo;
  }

  @Override
  public UUID save(
      OwnerId ownerId,
      DeckId deckId,
      String schemaVersion,
      int importedNotes,
      int importedCards,
      int duplicateNotes,
      int rejectedNotes,
      List<String> warnings) {
    UUID id = UUID.randomUUID();
    var entity = new ImportJobJpaEntity();
    entity.setId(id);
    entity.setOwnerId(ownerId.value());
    entity.setDeckId(deckId != null ? deckId.value() : null);
    entity.setSchemaVersion(schemaVersion != null ? schemaVersion : "1.0");
    entity.setStatus("completed");
    entity.setImportedNotes(importedNotes);
    entity.setImportedCards(importedCards);
    entity.setDuplicateNotes(duplicateNotes);
    entity.setRejectedNotes(rejectedNotes);
    entity.setWarnings(warnings != null ? warnings : List.of());
    entity.setCreatedAt(Instant.now());
    jpaRepo.save(entity);
    return id;
  }
}
