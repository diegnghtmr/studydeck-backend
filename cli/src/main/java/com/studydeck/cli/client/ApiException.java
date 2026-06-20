package com.studydeck.cli.client;

/** Thrown when an API call cannot be completed (network error, serialization failure, etc.). */
public class ApiException extends Exception {
  public ApiException(String message) {
    super(message);
  }

  public ApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
