package com.studydeck.domain.model;

/**
 * User-facing review rating for a card study session.
 *
 * <p>Follows the Anki-like four-button convention. Maps directly to the OpenAPI ReviewRating enum
 * (again, hard, good, easy). The scheduling engine converts this into algorithm-specific quality
 * scores internally — no AI determines this value; it is always an explicit user action.
 */
public enum ReviewRating {
  /** Complete blackout / forgot completely. Resets interval. */
  AGAIN,

  /** Significant difficulty; recalled with effort. */
  HARD,

  /** Recalled with minor hesitation — the target outcome. */
  GOOD,

  /** Perfect recall with no hesitation. */
  EASY
}
