package com.studydeck.infrastructure.adapter.out.idp;

/**
 * Infrastructure exception thrown when an IdP administrative operation fails.
 *
 * <p>This is an unchecked exception — callers that want best-effort behavior should catch it
 * explicitly.
 */
public class IdpAdminException extends RuntimeException {

  public IdpAdminException(String message) {
    super(message);
  }

  public IdpAdminException(String message, Throwable cause) {
    super(message, cause);
  }
}
