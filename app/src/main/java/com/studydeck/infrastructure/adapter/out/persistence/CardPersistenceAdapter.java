package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.port.out.CardRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link CardRepository}. */
@Transactional
class CardPersistenceAdapter implements CardRepository {

  private final CardJpaRepository jpaRepo;
  private final PersistenceMapper mapper;

  CardPersistenceAdapter(CardJpaRepository jpaRepo, PersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void saveAll(List<Card> cards) {
    jpaRepo.saveAll(cards.stream().map(mapper::toJpa).toList());
  }

  @Override
  public void save(Card card) {
    jpaRepo.save(mapper.toJpa(card));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Card> findById(CardId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Card> findByNoteId(NoteId noteId) {
    return jpaRepo.findByNoteIdOrderByOrdinalAsc(noteId.value()).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Card> findAll(DeckId deckId, Boolean suspended, int offset, int limit) {
    var deckUuid = (deckId != null) ? deckId.value() : null;
    if (deckUuid == null) {
      // No deck filter — search all (rare; for admin-level queries)
      return jpaRepo.findAll().stream()
          .filter(c -> suspended == null || c.isSuspended() == suspended)
          .skip(offset)
          .limit(limit)
          .map(mapper::toDomain)
          .toList();
    }
    return jpaRepo.findByDeckId(deckUuid, suspended).stream()
        .skip(offset)
        .limit(limit)
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countAll(DeckId deckId, Boolean suspended) {
    var deckUuid = (deckId != null) ? deckId.value() : null;
    if (deckUuid == null) {
      return jpaRepo.count();
    }
    return jpaRepo.countByDeckId(deckUuid, suspended);
  }

  @Override
  public void deleteByNoteId(NoteId noteId) {
    jpaRepo.deleteByNoteId(noteId.value());
  }

  @Override
  public void deleteById(CardId id) {
    jpaRepo.deleteById(id.value());
  }
}
