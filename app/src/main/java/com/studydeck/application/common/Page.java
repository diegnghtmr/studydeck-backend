package com.studydeck.application.common;

import java.util.List;
import java.util.Objects;

/**
 * Application-layer paginated result.
 *
 * @param <T> the element type
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

  public Page {
    Objects.requireNonNull(content, "content must not be null");
    content = List.copyOf(content);
  }

  public static <T> Page<T> of(List<T> content, int page, int size, long totalElements) {
    return new Page<>(content, page, size, totalElements);
  }

  /** Total number of pages given this page size. */
  public long totalPages() {
    return size == 0 ? 0 : (totalElements + size - 1) / size;
  }

  /** Whether there is a next page. */
  public boolean hasNext() {
    return (long) page < totalPages() - 1;
  }
}
