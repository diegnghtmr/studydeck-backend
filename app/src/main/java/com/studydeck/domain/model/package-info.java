/**
 * Domain model layer — pure Java, zero framework dependencies.
 *
 * <p>Contains entities, value objects, aggregates, and domain exceptions. No Spring annotations
 * allowed here. No jakarta.persistence annotations. Business invariants live here.
 *
 * <p>Key aggregates (P1+): Deck, Note, Card, CardScheduleState, ReviewLog, UserAccount.
 */
package com.studydeck.domain.model;
