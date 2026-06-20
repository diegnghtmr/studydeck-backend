package com.studydeck.cli.client;

/** Thrown when the HTTP transport layer fails (network error, timeout, etc.). */
public class TransportException extends Exception {
  public TransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
