package com.studydeck.domain.exception;

/** Thrown when a domain invariant is violated during creation or mutation. */
public final class DomainValidationException extends RuntimeException {

  public DomainValidationException(String message) {
    super(message);
  }

  public DomainValidationException(String field, String reason) {
    super("Invariant violated for field '%s': %s".formatted(field, reason));
  }
}
