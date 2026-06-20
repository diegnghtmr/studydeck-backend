package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SchedulerAlgorithm;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link CardScheduleStateRepository}. */
@Transactional
class CardScheduleStatePersistenceAdapter implements CardScheduleStateRepository {

  private final CardScheduleStateJpaRepository jpaRepo;
  private final CardJpaRepository cardJpaRepository;
  private final NoteJpaRepository noteJpaRepository;

  CardScheduleStatePersistenceAdapter(
      CardScheduleStateJpaRepository jpaRepo,
      CardJpaRepository cardJpaRepository,
      NoteJpaRepository noteJpaRepository) {
    this.jpaRepo = jpaRepo;
    this.cardJpaRepository = cardJpaRepository;
    this.noteJpaRepository = noteJpaRepository;
  }

  @Override
  public void save(OwnerId ownerId, CardId cardId, CardScheduleState state) {
    CardJpaEntity card = cardJpaRepository.findById(cardId.value()).orElseThrow();
    NoteJpaEntity note = noteJpaRepository.findById(card.getNoteId()).orElseThrow();

    CardScheduleStateJpaEntity entity =
        jpaRepo.findById(cardId.value()).orElseGet(CardScheduleStateJpaEntity::new);
    entity.setCardId(cardId.value());
    entity.setOwnerId(ownerId.value());
    entity.setDeckId(note.getDeckId());
    entity.setAlgorithm(state.algorithm().name());
    entity.setState(state.state().name());
    entity.setStability(state.stability());
    entity.setDifficulty(state.difficulty());
    entity.setDesiredRetention(state.desiredRetention());
    entity.setReps(state.reps());
    entity.setLapses(state.lapses());
    entity.setScheduledDays(state.scheduledDays());
    entity.setDueAt(state.dueAt());
    entity.setLastReviewedAt(state.lastReviewedAt());
    jpaRepo.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CardScheduleState> findByCardId(CardId cardId) {
    return jpaRepo.findById(cardId.value()).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CardId> findDueCardIds(OwnerId ownerId, DeckId deckId, Instant dueAt, int limit) {
    UUID deckUuid = (deckId != null) ? deckId.value() : null;
    List<UUID> uuids = jpaRepo.findDueCardIds(ownerId.value(), deckUuid, dueAt, limit);
    return uuids.stream().map(CardId::new).toList();
  }

  private CardScheduleState toDomain(CardScheduleStateJpaEntity e) {
    return new CardScheduleState(
        SchedulerAlgorithm.valueOf(e.getAlgorithm()),
        CardState.valueOf(e.getState()),
        e.getStability(),
        e.getDifficulty(),
        e.getDesiredRetention(),
        e.getReps(),
        e.getLapses(),
        e.getScheduledDays(),
        e.getDueAt(),
        e.getLastReviewedAt());
  }
}
