package com.studydeck.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object wrapping the UUID primary key for a Deck aggregate. */
public record DeckId(UUID value) {

  public DeckId {
    Objects.requireNonNull(value, "DeckId.value must not be null");
  }

  public static DeckId generate() {
    return new DeckId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
