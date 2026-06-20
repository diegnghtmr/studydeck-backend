package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.Objects;

/**
 * UserAccount aggregate root.
 *
 * <p>Represents a platform user whose identity is managed externally (OIDC). The {@code id} maps
 * 1-to-1 to the JWT {@code sub} claim (a UUID). The row is provisioned just-in-time on the first
 * authenticated API call via {@link com.studydeck.domain.port.in.ProvisionUserUseCase}.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>id: non-null OwnerId (matches JWT sub)
 *   <li>email: non-blank, max 254 chars (RFC 5321 limit)
 *   <li>displayName: non-blank when provided; null allowed — falls back to email prefix
 *   <li>status: one of ACTIVE, SUSPENDED, DELETED; defaults to ACTIVE
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public final class UserAccount {

  private static final int MAX_EMAIL_LENGTH = 254;

  private final OwnerId id;
  private String email;
  private String displayName;
  private UserAccountStatus status;
  private final Instant createdAt;
  private Instant updatedAt;

  private UserAccount(
      OwnerId id,
      String email,
      String displayName,
      UserAccountStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.email = email;
    this.displayName = displayName;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Factory — provisions a new user account from an OIDC principal.
   *
   * @param id non-null; must equal the JWT sub claim as a UUID
   * @param email non-blank, max 254 chars
   * @param displayName optional; null is accepted (stored as-is or derived by callers)
   */
  public static UserAccount provision(OwnerId id, String email, String displayName) {
    Objects.requireNonNull(id, "UserAccount id must not be null");
    validateEmail(email);
    Instant now = Instant.now();
    return new UserAccount(
        id, email, coerceDisplayName(displayName, email), UserAccountStatus.ACTIVE, now, now);
  }

  /**
   * Reconstitution constructor for persistence adapters.
   *
   * @param id non-null
   * @param email non-blank
   * @param displayName may be null
   * @param status non-null
   * @param createdAt non-null
   * @param updatedAt non-null
   */
  public static UserAccount reconstitute(
      OwnerId id,
      String email,
      String displayName,
      UserAccountStatus status,
      Instant createdAt,
      Instant updatedAt) {
    Objects.requireNonNull(id, "UserAccount id must not be null");
    validateEmail(email);
    Objects.requireNonNull(status, "UserAccount status must not be null");
    Objects.requireNonNull(createdAt, "UserAccount createdAt must not be null");
    Objects.requireNonNull(updatedAt, "UserAccount updatedAt must not be null");
    return new UserAccount(id, email, displayName, status, createdAt, updatedAt);
  }

  /**
   * Updates mutable identity fields when the OIDC provider returns new values.
   *
   * @param newEmail non-blank, max 254 chars
   * @param newDisplayName optional; null accepted
   */
  public void updateIdentity(String newEmail, String newDisplayName) {
    validateEmail(newEmail);
    this.email = newEmail;
    this.displayName = coerceDisplayName(newDisplayName, newEmail);
    this.updatedAt = Instant.now();
  }

  // ---------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------

  public OwnerId getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public UserAccountStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // ---------------------------------------------------------------
  // Invariant helpers
  // ---------------------------------------------------------------

  private static void validateEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new DomainValidationException("email", "must not be blank");
    }
    if (email.length() > MAX_EMAIL_LENGTH) {
      throw new DomainValidationException(
          "email",
          "exceeds %d character limit (got %d)".formatted(MAX_EMAIL_LENGTH, email.length()));
    }
  }

  private static String coerceDisplayName(String displayName, String email) {
    if (displayName != null && !displayName.isBlank()) {
      return displayName;
    }
    // Derive a reasonable display name from the email local-part when none is provided
    int atIdx = email.indexOf('@');
    return atIdx > 0 ? email.substring(0, atIdx) : email;
  }
}
