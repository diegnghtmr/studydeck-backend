package com.studydeck.application.support;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.ImportJobRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory test double for {@link ImportJobRepository}. */
public final class InMemoryImportJobRepository implements ImportJobRepository {

  public record JobRecord(
      UUID id,
      UUID ownerId,
      UUID deckId,
      String schemaVersion,
      int importedNotes,
      int importedCards,
      int duplicateNotes,
      int rejectedNotes,
      List<String> warnings) {}

  private final List<JobRecord> jobs = new ArrayList<>();

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
    jobs.add(
        new JobRecord(
            id,
            ownerId.value(),
            deckId != null ? deckId.value() : null,
            schemaVersion,
            importedNotes,
            importedCards,
            duplicateNotes,
            rejectedNotes,
            new ArrayList<>(warnings)));
    return id;
  }

  public List<JobRecord> all() {
    return List.copyOf(jobs);
  }

  public void clear() {
    jobs.clear();
  }

  public int size() {
    return jobs.size();
  }
}
