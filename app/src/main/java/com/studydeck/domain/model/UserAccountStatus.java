package com.studydeck.domain.model;

/**
 * Status of a {@link UserAccount}.
 *
 * <p>Values must match the {@code CHECK} constraint on the {@code user_account.status} column in
 * V1__init_core.sql.
 */
public enum UserAccountStatus {
  ACTIVE,
  SUSPENDED,
  DELETED
}
