package com.studydeck.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object wrapping the UUID primary key for a UserAiProvider aggregate. */
public record UserAiProviderId(UUID value) {

  public UserAiProviderId {
    Objects.requireNonNull(value, "UserAiProviderId.value must not be null");
  }

  public static UserAiProviderId generate() {
    return new UserAiProviderId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
