package com.studydeck.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Value object representing an active Identity Provider session.
 *
 * <p>Framework-free: no Spring or Jakarta annotations.
 */
public record IdpSession(
    String id, String ipAddress, Instant started, Instant lastAccess, List<String> clients) {

  public IdpSession {
    Objects.requireNonNull(id, "IdpSession.id must not be null");
    Objects.requireNonNull(ipAddress, "IdpSession.ipAddress must not be null");
    Objects.requireNonNull(started, "IdpSession.started must not be null");
    Objects.requireNonNull(lastAccess, "IdpSession.lastAccess must not be null");
    Objects.requireNonNull(clients, "IdpSession.clients must not be null");
    clients = List.copyOf(clients);
  }
}
