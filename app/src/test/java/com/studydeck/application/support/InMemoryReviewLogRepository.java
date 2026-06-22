package com.studydeck.application.support;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.port.out.ReviewLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory test double for {@link ReviewLogRepository}. */
public final class InMemoryReviewLogRepository implements ReviewLogRepository {

  private final List<StoredEntry> entries = new ArrayList<>();

  @Override
  public UUID save(OwnerId ownerId, UUID sessionId, ReviewLog log) {
    UUID id = UUID.randomUUID();
    entries.add(new StoredEntry(id, ownerId, sessionId, log));
    return id;
  }

  @Override
  public List<ReviewLog> findHistory(
      OwnerId ownerId,
      DeckId deckId,
      CardId cardId,
      Instant from,
      Instant to,
      int offset,
      int limit) {
    return entries.stream()
        .filter(e -> e.ownerId().equals(ownerId))
        .filter(e -> cardId == null || e.log().cardId().equals(cardId))
        .filter(e -> from == null || !e.log().reviewedAt().isBefore(from))
        .filter(e -> to == null || !e.log().reviewedAt().isAfter(to))
        .map(StoredEntry::log)
        .sorted((a, b) -> b.reviewedAt().compareTo(a.reviewedAt()))
        .skip(offset)
        .limit(limit)
        .toList();
  }

  @Override
  public long countHistory(
      OwnerId ownerId, DeckId deckId, CardId cardId, Instant from, Instant to) {
    return entries.stream()
        .filter(e -> e.ownerId().equals(ownerId))
        .filter(e -> cardId == null || e.log().cardId().equals(cardId))
        .filter(e -> from == null || !e.log().reviewedAt().isBefore(from))
        .filter(e -> to == null || !e.log().reviewedAt().isAfter(to))
        .count();
  }

  @Override
  public int countReviewedToday(OwnerId ownerId, DeckId deckId, Instant dayStart, Instant dayEnd) {
    return (int)
        entries.stream()
            .filter(e -> e.ownerId().equals(ownerId))
            .filter(e -> !e.log().reviewedAt().isBefore(dayStart))
            .filter(e -> e.log().reviewedAt().isBefore(dayEnd))
            .count();
  }

  @Override
  public Double againRate7d(OwnerId ownerId, DeckId deckId, Instant since) {
    List<ReviewLog> relevant =
        entries.stream()
            .filter(e -> e.ownerId().equals(ownerId))
            .filter(e -> !e.log().reviewedAt().isBefore(since))
            .map(StoredEntry::log)
            .toList();
    if (relevant.isEmpty()) return null;
    long againCount = relevant.stream().filter(l -> l.rating() == ReviewRating.AGAIN).count();
    return (double) againCount / relevant.size();
  }

  @Override
  public Double averageRetention30d(OwnerId ownerId, DeckId deckId, Instant since) {
    List<ReviewLog> relevant =
        entries.stream()
            .filter(e -> e.ownerId().equals(ownerId))
            .filter(e -> !e.log().reviewedAt().isBefore(since))
            .map(StoredEntry::log)
            .toList();
    if (relevant.isEmpty()) return null;
    long nonAgain = relevant.stream().filter(l -> l.rating() != ReviewRating.AGAIN).count();
    return (double) nonAgain / relevant.size();
  }

  @Override
  public long countReviewedTodayGlobal(OwnerId ownerId, Instant dayStart, Instant dayEnd) {
    return entries.stream()
        .filter(e -> e.ownerId().equals(ownerId))
        .filter(e -> !e.log().reviewedAt().isBefore(dayStart))
        .filter(e -> e.log().reviewedAt().isBefore(dayEnd))
        .count();
  }

  @Override
  public java.util.List<java.time.LocalDate> distinctReviewDays(
      OwnerId ownerId, java.time.ZoneId zone) {
    return entries.stream()
        .filter(e -> e.ownerId().equals(ownerId))
        .map(e -> e.log().reviewedAt().atZone(zone).toLocalDate())
        .distinct()
        .sorted(java.util.Comparator.reverseOrder())
        .toList();
  }

  @Override
  public Double averageRetentionGlobal(OwnerId ownerId, Instant since) {
    List<ReviewLog> relevant =
        entries.stream()
            .filter(e -> e.ownerId().equals(ownerId))
            .filter(e -> !e.log().reviewedAt().isBefore(since))
            .map(StoredEntry::log)
            .toList();
    if (relevant.isEmpty()) return null;
    long nonAgain = relevant.stream().filter(l -> l.rating() != ReviewRating.AGAIN).count();
    return (double) nonAgain / relevant.size();
  }

  // Test helpers

  public List<ReviewLog> all() {
    return entries.stream().map(StoredEntry::log).toList();
  }

  public int size() {
    return entries.size();
  }

  public void clear() {
    entries.clear();
  }

  private record StoredEntry(UUID id, OwnerId ownerId, UUID sessionId, ReviewLog log) {}
}
