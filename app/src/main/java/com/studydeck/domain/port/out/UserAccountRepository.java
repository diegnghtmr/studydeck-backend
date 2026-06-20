package com.studydeck.domain.port.out;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import java.util.Optional;

/**
 * Output port — persistence contract for {@link UserAccount}.
 *
 * <p>Named by need (not by technology). Implementations live in the infrastructure layer.
 */
public interface UserAccountRepository {

  /**
   * Finds a user account by id.
   *
   * @param id non-null
   * @return the account, or empty if not found
   */
  Optional<UserAccount> findById(OwnerId id);

  /**
   * Persists a user account (insert or update).
   *
   * <p>Idempotent: if the account already exists, it is updated with the latest values.
   *
   * @param userAccount non-null
   */
  void save(UserAccount userAccount);

  /**
   * Checks whether a user account exists for the given id.
   *
   * <p>Cheaper than {@link #findById} when only existence needs to be tested (no data read).
   *
   * @param id non-null
   * @return {@code true} if a row exists
   */
  boolean existsById(OwnerId id);
}
