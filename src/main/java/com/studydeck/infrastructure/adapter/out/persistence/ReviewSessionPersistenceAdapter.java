package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.ReviewSessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link ReviewSessionRepository}. */
@Transactional
class ReviewSessionPersistenceAdapter implements ReviewSessionRepository {

  private final ReviewSessionJpaRepository jpaRepo;

  ReviewSessionPersistenceAdapter(ReviewSessionJpaRepository jpaRepo) {
    this.jpaRepo = jpaRepo;
  }

  @Override
  public UUID create(OwnerId ownerId, DeckId deckId, int maxCards, Instant startedAt) {
    UUID id = UUID.randomUUID();
    ReviewSessionJpaEntity entity = new ReviewSessionJpaEntity();
    entity.setId(id);
    entity.setOwnerId(ownerId.value());
    entity.setDeckId(deckId != null ? deckId.value() : null);
    entity.setMaxCards(maxCards);
    entity.setStatus("started");
    entity.setPresentedCount(0);
    entity.setAnsweredCount(0);
    entity.setStartedAt(startedAt);
    jpaRepo.save(entity);
    return id;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReviewSessionView> findById(UUID sessionId) {
    return jpaRepo.findById(sessionId).map(this::toView);
  }

  @Override
  public void incrementPresentedCount(UUID sessionId) {
    jpaRepo.incrementPresentedCount(sessionId);
  }

  @Override
  public void incrementAnsweredCount(UUID sessionId) {
    jpaRepo.incrementAnsweredCount(sessionId);
  }

  @Override
  public void complete(UUID sessionId, Instant endedAt) {
    jpaRepo
        .findById(sessionId)
        .ifPresent(
            e -> {
              e.setStatus("completed");
              e.setEndedAt(endedAt);
              jpaRepo.save(e);
            });
  }

  private ReviewSessionView toView(ReviewSessionJpaEntity e) {
    return new ReviewSessionView(
        e.getId(),
        new OwnerId(e.getOwnerId()),
        e.getDeckId() != null ? new DeckId(e.getDeckId()) : null,
        e.getMaxCards(),
        e.getStatus(),
        e.getStartedAt(),
        e.getEndedAt(),
        e.getPresentedCount(),
        e.getAnsweredCount());
  }
}
