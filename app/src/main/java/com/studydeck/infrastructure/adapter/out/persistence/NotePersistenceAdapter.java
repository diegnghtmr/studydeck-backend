package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.port.out.NoteRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link NoteRepository}. */
@Transactional
class NotePersistenceAdapter implements NoteRepository {

  private final NoteJpaRepository jpaRepo;
  private final PersistenceMapper mapper;

  NotePersistenceAdapter(NoteJpaRepository jpaRepo, PersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(Note note) {
    jpaRepo.save(mapper.toJpa(note));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Note> findById(NoteId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Note> findAll(
      DeckId deckId, NoteType noteType, String tag, String search, int offset, int limit) {
    var deckUuid = (deckId != null) ? deckId.value() : null;
    var noteTypeName = (noteType != null) ? noteType.name() : null;
    String searchParam = (search == null || search.isBlank()) ? null : search;

    List<NoteJpaEntity> entities = jpaRepo.findWithFilters(deckUuid, noteTypeName, searchParam);

    // Tag filter applied in memory (text[] containment — could be pushed to DB via native query)
    return entities.stream()
        .filter(e -> tag == null || e.getTags().contains(tag))
        .skip(offset)
        .limit(limit)
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countAll(DeckId deckId, NoteType noteType, String tag, String search) {
    var deckUuid = (deckId != null) ? deckId.value() : null;
    var noteTypeName = (noteType != null) ? noteType.name() : null;
    String searchParam = (search == null || search.isBlank()) ? null : search;

    if (tag != null) {
      // Need in-memory count when tag filter is active
      return jpaRepo.findWithFilters(deckUuid, noteTypeName, searchParam).stream()
          .filter(e -> e.getTags().contains(tag))
          .count();
    }
    return jpaRepo.countWithFilters(deckUuid, noteTypeName, searchParam);
  }

  @Override
  public void deleteById(NoteId id) {
    jpaRepo.deleteById(id.value());
  }
}
