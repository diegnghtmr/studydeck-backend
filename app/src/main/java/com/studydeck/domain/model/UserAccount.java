package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

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
 *   <li>dailyGoal: 1..1000
 *   <li>desiredRetention: 0.50..0.99
 *   <li>newCardsPerDay: 0..999
 *   <li>language: one of en, es, fr, pt
 *   <li>timezone: valid IANA timezone string
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public final class UserAccount {

  private static final int MAX_EMAIL_LENGTH = 254;
  private static final int DEFAULT_DAILY_GOAL = 40;
  private static final int MIN_DAILY_GOAL = 1;
  private static final int MAX_DAILY_GOAL = 1000;

  private static final double DEFAULT_DESIRED_RETENTION = 0.90;
  private static final double MIN_DESIRED_RETENTION = 0.50;
  private static final double MAX_DESIRED_RETENTION = 0.99;

  private static final int DEFAULT_NEW_CARDS_PER_DAY = 10;
  private static final int MIN_NEW_CARDS_PER_DAY = 0;
  private static final int MAX_NEW_CARDS_PER_DAY = 999;

  private static final String DEFAULT_LANGUAGE = "en";
  private static final Set<String> ALLOWED_LANGUAGES = Set.of("en", "es", "fr", "pt");

  private static final String DEFAULT_TIMEZONE = "UTC";

  private final OwnerId id;
  private String email;
  private String displayName;
  private UserAccountStatus status;
  private int dailyGoal;
  private double desiredRetention;
  private int newCardsPerDay;
  private String language;
  private String timezone;
  private final Instant createdAt;
  private Instant updatedAt;

  private UserAccount(
      OwnerId id,
      String email,
      String displayName,
      UserAccountStatus status,
      int dailyGoal,
      double desiredRetention,
      int newCardsPerDay,
      String language,
      String timezone,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.email = email;
    this.displayName = displayName;
    this.status = status;
    this.dailyGoal = dailyGoal;
    this.desiredRetention = desiredRetention;
    this.newCardsPerDay = newCardsPerDay;
    this.language = language;
    this.timezone = timezone;
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
        id,
        email,
        coerceDisplayName(displayName, email),
        UserAccountStatus.ACTIVE,
        DEFAULT_DAILY_GOAL,
        DEFAULT_DESIRED_RETENTION,
        DEFAULT_NEW_CARDS_PER_DAY,
        DEFAULT_LANGUAGE,
        DEFAULT_TIMEZONE,
        now,
        now);
  }

  /**
   * Backward-compatible reconstitution constructor (7-arg) — sets new preference fields to their
   * defaults.
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
    return reconstitute(id, email, displayName, status, DEFAULT_DAILY_GOAL, createdAt, updatedAt);
  }

  /**
   * Backward-compatible reconstitution constructor (7-arg with dailyGoal) — sets new preference
   * fields to their defaults.
   */
  public static UserAccount reconstitute(
      OwnerId id,
      String email,
      String displayName,
      UserAccountStatus status,
      int dailyGoal,
      Instant createdAt,
      Instant updatedAt) {
    return reconstitute(
        id,
        email,
        displayName,
        status,
        dailyGoal,
        DEFAULT_DESIRED_RETENTION,
        DEFAULT_NEW_CARDS_PER_DAY,
        DEFAULT_LANGUAGE,
        DEFAULT_TIMEZONE,
        createdAt,
        updatedAt);
  }

  /**
   * Full reconstitution constructor (11-arg) — used by the persistence adapter.
   *
   * @param id non-null
   * @param email non-blank
   * @param displayName may be null
   * @param status non-null
   * @param dailyGoal 1..1000
   * @param desiredRetention 0.50..0.99
   * @param newCardsPerDay 0..999
   * @param language one of en, es, fr, pt
   * @param timezone valid IANA timezone string
   * @param createdAt non-null
   * @param updatedAt non-null
   */
  public static UserAccount reconstitute(
      OwnerId id,
      String email,
      String displayName,
      UserAccountStatus status,
      int dailyGoal,
      double desiredRetention,
      int newCardsPerDay,
      String language,
      String timezone,
      Instant createdAt,
      Instant updatedAt) {
    Objects.requireNonNull(id, "UserAccount id must not be null");
    validateEmail(email);
    Objects.requireNonNull(status, "UserAccount status must not be null");
    validateDailyGoal(dailyGoal);
    validateDesiredRetention(desiredRetention);
    validateNewCardsPerDay(newCardsPerDay);
    validateLanguage(language);
    validateTimezone(timezone);
    Objects.requireNonNull(createdAt, "UserAccount createdAt must not be null");
    Objects.requireNonNull(updatedAt, "UserAccount updatedAt must not be null");
    return new UserAccount(
        id,
        email,
        displayName,
        status,
        dailyGoal,
        desiredRetention,
        newCardsPerDay,
        language,
        timezone,
        createdAt,
        updatedAt);
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

  /**
   * Updates the user's daily study goal (number of cards/day).
   *
   * @param newDailyGoal between 1 and 1000 inclusive
   */
  public void updateDailyGoal(int newDailyGoal) {
    validateDailyGoal(newDailyGoal);
    this.dailyGoal = newDailyGoal;
    this.updatedAt = Instant.now();
  }

  /**
   * Updates the user's desired retention fraction.
   *
   * @param newRetention between 0.50 and 0.99 inclusive
   */
  public void updateDesiredRetention(double newRetention) {
    validateDesiredRetention(newRetention);
    this.desiredRetention = newRetention;
    this.updatedAt = Instant.now();
  }

  /**
   * Updates the maximum number of new cards introduced per day.
   *
   * @param newCardsPerDay between 0 and 999 inclusive
   */
  public void updateNewCardsPerDay(int newCardsPerDay) {
    validateNewCardsPerDay(newCardsPerDay);
    this.newCardsPerDay = newCardsPerDay;
    this.updatedAt = Instant.now();
  }

  /**
   * Updates the UI language preference.
   *
   * @param newLanguage one of en, es, fr, pt
   */
  public void updateLanguage(String newLanguage) {
    validateLanguage(newLanguage);
    this.language = newLanguage;
    this.updatedAt = Instant.now();
  }

  /**
   * Updates the IANA timezone preference.
   *
   * @param newTimezone valid IANA timezone string (e.g., "America/New_York", "UTC")
   */
  public void updateTimezone(String newTimezone) {
    validateTimezone(newTimezone);
    this.timezone = newTimezone;
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

  public int getDailyGoal() {
    return dailyGoal;
  }

  public double getDesiredRetention() {
    return desiredRetention;
  }

  public int getNewCardsPerDay() {
    return newCardsPerDay;
  }

  public String getLanguage() {
    return language;
  }

  public String getTimezone() {
    return timezone;
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

  private static void validateDailyGoal(int dailyGoal) {
    if (dailyGoal < MIN_DAILY_GOAL || dailyGoal > MAX_DAILY_GOAL) {
      throw new DomainValidationException(
          "dailyGoal",
          "must be between %d and %d inclusive (got %d)"
              .formatted(MIN_DAILY_GOAL, MAX_DAILY_GOAL, dailyGoal));
    }
  }

  private static void validateDesiredRetention(double desiredRetention) {
    if (desiredRetention < MIN_DESIRED_RETENTION || desiredRetention > MAX_DESIRED_RETENTION) {
      throw new DomainValidationException(
          "desiredRetention",
          "must be between %.2f and %.2f inclusive (got %.4f)"
              .formatted(MIN_DESIRED_RETENTION, MAX_DESIRED_RETENTION, desiredRetention));
    }
  }

  private static void validateNewCardsPerDay(int newCardsPerDay) {
    if (newCardsPerDay < MIN_NEW_CARDS_PER_DAY || newCardsPerDay > MAX_NEW_CARDS_PER_DAY) {
      throw new DomainValidationException(
          "newCardsPerDay",
          "must be between %d and %d inclusive (got %d)"
              .formatted(MIN_NEW_CARDS_PER_DAY, MAX_NEW_CARDS_PER_DAY, newCardsPerDay));
    }
  }

  private static void validateLanguage(String language) {
    if (language == null || !ALLOWED_LANGUAGES.contains(language)) {
      throw new DomainValidationException(
          "language", "must be one of %s (got %s)".formatted(ALLOWED_LANGUAGES, language));
    }
  }

  private static void validateTimezone(String timezone) {
    if (timezone == null) {
      throw new DomainValidationException("timezone", "must not be null");
    }
    try {
      ZoneId.of(timezone);
    } catch (java.time.DateTimeException e) {
      throw new DomainValidationException(
          "timezone", "must be a valid IANA timezone (got '%s')".formatted(timezone));
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
