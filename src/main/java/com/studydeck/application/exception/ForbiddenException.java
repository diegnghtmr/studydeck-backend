package com.studydeck.application.exception;

/**
 * Thrown when an authenticated user attempts an action on a resource they do not own.
 *
 * <p>Note: ownership violations on read operations use {@link NotFoundException} (information
 * hiding). This exception is reserved for write operations where the resource exists but the caller
 * has no write privilege. The REST adapter translates this to HTTP 403.
 */
public final class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }

  public ForbiddenException(String resourceType, String resourceId) {
    super("Access denied to %s '%s'".formatted(resourceType, resourceId));
  }
}
