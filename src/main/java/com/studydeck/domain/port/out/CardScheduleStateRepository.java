package com.studydeck.domain.port.out;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Output port — persistence contract for CardScheduleState (1:1 with Card).
 *
 * <p>Each card has exactly one schedule-state row. When a card has no row it is treated as NEW and
 * due immediately (backfill strategy).
 */
public interface CardScheduleStateRepository {

  /** Persists (upsert) the schedule state for a card. */
  void save(OwnerId ownerId, CardId cardId, CardScheduleState state);

  /** Loads the schedule state for a card. Returns empty when no row exists. */
  Optional<CardScheduleState> findByCardId(CardId cardId);

  /**
   * Loads card IDs whose schedule state is due at or before {@code dueAt}, owned by the given user,
   * optionally filtered by deck. Results are ordered by due_at asc and limited to {@code limit}.
   */
  List<CardId> findDueCardIds(OwnerId ownerId, DeckId deckId, Instant dueAt, int limit);
}
