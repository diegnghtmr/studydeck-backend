package com.studydeck.domain.port.out;

/**
 * Output port for Identity Provider administrative operations.
 *
 * <p>Framework-free: no Spring annotations. Wired in infrastructure configuration.
 */
public interface IdpAdminPort {

  /**
   * Permanently deletes a user from the Identity Provider.
   *
   * @param idpUserId the user's IdP identifier (JWT {@code sub} claim)
   */
  void deleteUser(String idpUserId);

  /**
   * Revokes all active sessions for a user in the Identity Provider.
   *
   * @param idpUserId the user's IdP identifier (JWT {@code sub} claim)
   */
  void logoutAllSessions(String idpUserId);
}
