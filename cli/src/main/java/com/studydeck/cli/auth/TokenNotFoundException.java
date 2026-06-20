package com.studydeck.cli.auth;

/** Thrown when no bearer token can be resolved from any source. */
public class TokenNotFoundException extends RuntimeException {
  public TokenNotFoundException(String message) {
    super(message);
  }
}
