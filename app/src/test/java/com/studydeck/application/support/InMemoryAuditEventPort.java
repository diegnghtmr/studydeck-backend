package com.studydeck.application.support;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AuditEventPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-memory test double for {@link AuditEventPort}. Captures recorded events for assertions. */
public final class InMemoryAuditEventPort implements AuditEventPort {

  public record AuditEntry(OwnerId actorId, String action, String targetType, String targetId) {}

  private final List<AuditEntry> events = new ArrayList<>();

  @Override
  public void record(OwnerId actorId, String action, String targetType, String targetId) {
    events.add(new AuditEntry(actorId, action, targetType, targetId));
  }

  /** Returns an unmodifiable view of all recorded audit events. */
  public List<AuditEntry> recorded() {
    return Collections.unmodifiableList(events);
  }

  /** Clears all captured events. */
  public void clear() {
    events.clear();
  }

  /** Returns the number of recorded events. */
  public int size() {
    return events.size();
  }

  /** Returns true if any event matches the given action. */
  public boolean hasAction(String action) {
    return events.stream().anyMatch(e -> e.action().equals(action));
  }
}
