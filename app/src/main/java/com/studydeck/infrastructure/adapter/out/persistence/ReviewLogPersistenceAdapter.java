package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.port.out.ReviewLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link ReviewLogRepository}. */
@Transactional
class ReviewLogPersistenceAdapter implements ReviewLogRepository {

  private final ReviewLogJpaRepository jpaRepo;
  private final CardJpaRepository cardJpaRepo;
  private final NoteJpaRepository noteJpaRepo;

  ReviewLogPersistenceAdapter(
      ReviewLogJpaRepository jpaRepo,
      CardJpaRepository cardJpaRepo,
      NoteJpaRepository noteJpaRepo) {
    this.jpaRepo = jpaRepo;
    this.cardJpaRepo = cardJpaRepo;
    this.noteJpaRepo = noteJpaRepo;
  }

  @Override
  public UUID save(OwnerId ownerId, UUID sessionId, ReviewLog log) {
    // Resolve deck_id from card → note chain (denormalized for stats queries)
    CardJpaEntity card = cardJpaRepo.findById(log.cardId().value()).orElseThrow();
    NoteJpaEntity note = noteJpaRepo.findById(card.getNoteId()).orElseThrow();

    UUID id = UUID.randomUUID();
    ReviewLogJpaEntity entity = new ReviewLogJpaEntity();
    entity.setId(id);
    entity.setOwnerId(ownerId.value());
    entity.setSessionId(sessionId);
    entity.setCardId(log.cardId().value());
    entity.setDeckId(note.getDeckId());
    entity.setRating(log.rating().name());
    entity.setStateBefore(log.stateBefore().name());
    entity.setElapsedDays(log.elapsedDays());
    entity.setScheduledDays(log.scheduledDays());
    entity.setResponseTimeMs(log.responseTimeMs());
    entity.setReviewedAt(log.reviewedAt());
    jpaRepo.save(entity);
    return id;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReviewLog> findHistory(
      OwnerId ownerId,
      DeckId deckId,
      CardId cardId,
      Instant from,
      Instant to,
      int offset,
      int limit) {
    UUID deckUuid = (deckId != null) ? deckId.value() : null;
    UUID cardUuid = (cardId != null) ? cardId.value() : null;
    return jpaRepo
        .findHistory(ownerId.value(), deckUuid, cardUuid, from, to, offset, limit)
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countHistory(
      OwnerId ownerId, DeckId deckId, CardId cardId, Instant from, Instant to) {
    UUID deckUuid = (deckId != null) ? deckId.value() : null;
    UUID cardUuid = (cardId != null) ? cardId.value() : null;
    return jpaRepo.countHistory(ownerId.value(), deckUuid, cardUuid, from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public int countReviewedToday(OwnerId ownerId, DeckId deckId, Instant dayStart, Instant dayEnd) {
    return jpaRepo.countReviewedToday(ownerId.value(), deckId.value(), dayStart, dayEnd);
  }

  @Override
  @Transactional(readOnly = true)
  public Double againRate7d(OwnerId ownerId, DeckId deckId, Instant since) {
    return jpaRepo.againRate7d(ownerId.value(), deckId.value(), since);
  }

  @Override
  @Transactional(readOnly = true)
  public Double averageRetention30d(OwnerId ownerId, DeckId deckId, Instant since) {
    return jpaRepo.averageRetention30d(ownerId.value(), deckId.value(), since);
  }

  @Override
  @Transactional(readOnly = true)
  public long countReviewedTodayGlobal(OwnerId ownerId, Instant dayStart, Instant dayEnd) {
    return jpaRepo.countReviewedTodayGlobal(ownerId.value(), dayStart, dayEnd);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<java.time.LocalDate> distinctReviewDays(
      OwnerId ownerId, java.time.ZoneId zone) {
    return jpaRepo.distinctReviewDays(ownerId.value(), zone.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public Double averageRetentionGlobal(OwnerId ownerId, Instant since) {
    return jpaRepo.averageRetentionGlobal(ownerId.value(), since);
  }

  @Override
  @Transactional(readOnly = true)
  public int countNewCardsIntroducedToday(OwnerId ownerId, Instant dayStart) {
    return jpaRepo.countNewCardsIntroducedToday(ownerId.value(), dayStart);
  }

  private ReviewLog toDomain(ReviewLogJpaEntity e) {
    return new ReviewLog(
        new CardId(e.getCardId()),
        ReviewRating.valueOf(e.getRating()),
        CardState.valueOf(e.getStateBefore()),
        e.getReviewedAt(),
        e.getElapsedDays(),
        e.getScheduledDays(),
        e.getResponseTimeMs());
  }
}
