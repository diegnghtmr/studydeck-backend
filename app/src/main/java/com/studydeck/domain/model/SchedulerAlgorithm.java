package com.studydeck.domain.model;

/**
 * Spaced-repetition algorithm identifier.
 *
 * <p>FSRS is the default algorithm. SM2 is provided for compatibility with legacy collections.
 * Mirrors the SchedulerAlgorithm enum in the OpenAPI contract.
 */
public enum SchedulerAlgorithm {
  /** Free Spaced Repetition Scheduler — the default, evidence-based algorithm. */
  FSRS,

  /** SuperMemo 2 — classic algorithm for legacy compatibility. */
  SM2
}
