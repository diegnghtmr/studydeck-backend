package com.studydeck.application.exception;

/**
 * Thrown when a requested resource does not exist or is not visible to the requesting user.
 *
 * <p>By design this exception conflates "not found" and "forbidden" for resources the caller does
 * not own: we never reveal that a resource exists to an unauthorized caller (information hiding).
 * The REST adapter translates this to HTTP 404.
 */
public final class NotFoundException extends RuntimeException {

  private final String resourceType;
  private final String resourceId;

  public NotFoundException(String resourceType, String resourceId) {
    super("%s with id '%s' not found".formatted(resourceType, resourceId));
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }
}
