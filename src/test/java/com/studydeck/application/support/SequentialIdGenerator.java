package com.studydeck.application.support;

import com.studydeck.domain.port.out.IdGenerator;
import java.util.UUID;

/**
 * Test double for {@link IdGenerator} that returns a fixed UUID or cycles through pre-set UUIDs.
 *
 * <p>When no UUIDs are pre-set, falls back to {@link UUID#randomUUID()} so tests that don't need
 * determinism still work without extra setup.
 */
public final class SequentialIdGenerator implements IdGenerator {

  private final java.util.Queue<UUID> queue = new java.util.ArrayDeque<>();

  /** Configures the sequence of UUIDs to return (in order). */
  public void enqueue(UUID... ids) {
    for (UUID id : ids) {
      queue.offer(id);
    }
  }

  @Override
  public UUID generate() {
    UUID next = queue.poll();
    return next != null ? next : UUID.randomUUID();
  }

  public void clear() {
    queue.clear();
  }
}
