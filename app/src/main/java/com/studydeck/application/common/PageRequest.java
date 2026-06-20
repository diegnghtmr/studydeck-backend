package com.studydeck.application.common;

/**
 * Application-layer pagination parameters (offset-based).
 *
 * <p>Matches the OpenAPI pagination contract: {@code page=0, size=20, maxSize=100}.
 */
public record PageRequest(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public PageRequest {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0, got: " + page);
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be >= 1, got: " + size);
    }
    if (size > MAX_SIZE) {
      throw new IllegalArgumentException("size must be <= %d, got: %d".formatted(MAX_SIZE, size));
    }
  }

  public static PageRequest of(int page, int size) {
    return new PageRequest(page, size);
  }

  public static PageRequest defaultPage() {
    return new PageRequest(0, DEFAULT_SIZE);
  }

  /** Returns the 0-based row offset for this page. */
  public int offset() {
    return page * size;
  }
}
