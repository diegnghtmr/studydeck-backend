package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;

/**
 * Input port — returns the authenticated principal from the security context.
 *
 * <p>This is a thin delegation point; the real resolution happens in the security layer (B3). At
 * this layer, the use case simply takes the authenticated {@link OwnerId} and returns it as part of
 * a principal result — useful for the {@code GET /v1/auth/me} endpoint.
 */
public interface GetCurrentPrincipalQuery {

  /**
   * Returns the current principal.
   *
   * @param query (non-null)
   * @return the principal descriptor
   */
  Principal execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId the authenticated user's id, resolved by the security layer (non-null)
   * @param email the authenticated user's email (non-null)
   * @param displayName the user's display name (may be null or empty)
   */
  record Query(OwnerId ownerId, String email, String displayName) {

    public Query {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (email == null || email.isBlank()) {
        throw new IllegalArgumentException("email must not be blank");
      }
    }
  }

  /**
   * Principal result.
   *
   * @param ownerId the user's id
   * @param email the user's email
   * @param displayName the user's display name (may be null)
   */
  record Principal(OwnerId ownerId, String email, String displayName) {}
}
