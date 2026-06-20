package com.studydeck.infrastructure.adapter.out.persistence;

/**
 * Thrown when JSONB serialization or deserialization fails in the persistence layer.
 *
 * <p>This is an infrastructure-layer exception; it never escapes to the domain or application
 * layers.
 */
class PersistenceMappingException extends RuntimeException {

  PersistenceMappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
