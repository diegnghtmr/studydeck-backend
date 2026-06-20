package com.studydeck.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object wrapping the UUID primary key for a Card entity. */
public record CardId(UUID value) {

  public CardId {
    Objects.requireNonNull(value, "CardId.value must not be null");
  }

  public static CardId generate() {
    return new CardId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
