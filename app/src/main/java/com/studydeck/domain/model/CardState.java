package com.studydeck.domain.model;

/**
 * Card learning-state in the spaced-repetition state machine.
 *
 * <p>State transitions:
 *
 * <pre>
 * NEW → LEARNING (first review, any rating)
 * LEARNING → REVIEW (after graduating steps, or EASY on first review)
 * REVIEW → REVIEW (HARD / GOOD — rescheduled in Review)
 * REVIEW → RELEARNING (AGAIN — lapse; stability/interval reset)
 * RELEARNING → REVIEW (after relearning step, or EASY)
 * </pre>
 */
public enum CardState {
  /** Card has never been reviewed. */
  NEW,

  /** Card is in the initial learning phase (short relearning steps). */
  LEARNING,

  /**
   * Card has graduated to the mature review phase. Intervals are computed from stability and
   * desired retention.
   */
  REVIEW,

  /** Card was forgotten during REVIEW; undergoing relearning before re-entering REVIEW. */
  RELEARNING
}
