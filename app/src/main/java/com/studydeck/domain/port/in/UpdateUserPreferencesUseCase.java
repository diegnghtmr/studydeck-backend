package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import java.util.Objects;

/** Input port — updates per-user preferences (partial PATCH semantics). */
public interface UpdateUserPreferencesUseCase {

  /**
   * Updates the authenticated user's preferences. Only non-null fields are applied.
   *
   * @param command the preference values (non-null); any field except ownerId may be null to leave
   *     it unchanged
   * @throws com.studydeck.application.exception.NotFoundException if the user is not provisioned
   */
  void execute(Command command);

  /**
   * Self-validating command record supporting partial updates. All preference fields are nullable —
   * null means "leave unchanged".
   *
   * @param ownerId authenticated user (non-null)
   * @param dailyGoal new daily study goal (1–1000), or null to leave unchanged
   * @param desiredRetention new target retention fraction (0.50–0.99), or null to leave unchanged
   * @param newCardsPerDay new max new cards per day (0–999), or null to leave unchanged
   * @param language new UI language (en, es, fr, pt), or null to leave unchanged
   * @param timezone new IANA timezone string, or null to leave unchanged
   */
  record Command(
      OwnerId ownerId,
      Integer dailyGoal,
      Double desiredRetention,
      Integer newCardsPerDay,
      String language,
      String timezone) {
    public Command {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
    }
  }
}
