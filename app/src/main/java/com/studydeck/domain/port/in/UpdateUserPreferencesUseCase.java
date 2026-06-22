package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import java.util.Objects;

/** Input port — updates per-user preferences (currently the daily study goal). */
public interface UpdateUserPreferencesUseCase {

  /**
   * Updates the authenticated user's preferences.
   *
   * @param command the preference values (non-null)
   * @throws com.studydeck.application.exception.NotFoundException if the user is not provisioned
   */
  void execute(Command command);

  /**
   * Self-validating command record.
   *
   * @param ownerId authenticated user (non-null)
   * @param dailyGoal new daily study goal; 1–1000 inclusive
   */
  record Command(OwnerId ownerId, int dailyGoal) {
    public Command {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      if (dailyGoal < 1 || dailyGoal > 1000) {
        throw new IllegalArgumentException("dailyGoal must be between 1 and 1000 inclusive");
      }
    }
  }
}
