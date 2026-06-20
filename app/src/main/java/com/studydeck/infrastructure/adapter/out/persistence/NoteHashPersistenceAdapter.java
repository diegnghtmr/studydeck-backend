package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.port.out.NoteHashRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of {@link NoteHashRepository}.
 *
 * <p>Reads/writes the {@code content_hash} column on the {@code note} table.
 */
@Transactional
class NoteHashPersistenceAdapter implements NoteHashRepository {

  private final NoteJpaRepository jpaRepo;

  NoteHashPersistenceAdapter(NoteJpaRepository jpaRepo) {
    this.jpaRepo = jpaRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> findExistingHashes(DeckId deckId, NoteType noteType) {
    return new HashSet<>(jpaRepo.findContentHashes(deckId.value(), noteType.name()));
  }

  @Override
  public void saveHash(NoteId noteId, String hash) {
    jpaRepo.updateContentHash(noteId.value(), hash);
  }
}
