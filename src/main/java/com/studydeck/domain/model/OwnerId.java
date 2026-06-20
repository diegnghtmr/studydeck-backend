package com.studydeck.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object representing the UUID of the user who owns a Deck. */
public record OwnerId(UUID value) {

  public OwnerId {
    Objects.requireNonNull(value, "OwnerId.value must not be null");
  }

  public static OwnerId generate() {
    return new OwnerId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
