package com.studydeck.domain.port.out;

import com.studydeck.domain.model.OwnerId;

/**
 * Output port — publishes audit events for create/update/delete operations.
 *
 * <p>Implementations may write to a DB table, message queue, or structured log.
 */
public interface AuditEventPort {

  /**
   * Records an audit event.
   *
   * @param actorId the user who performed the action (non-null)
   * @param action a verb describing the action (e.g. "deck.created", "note.deleted")
   * @param targetType the type of the affected resource (e.g. "Deck", "Note", "Card")
   * @param targetId string representation of the resource id
   */
  void record(OwnerId actorId, String action, String targetType, String targetId);
}
