package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;

/**
 * Input port — irreversible hard-delete of the caller's own account and all associated data (GDPR
 * right to erasure).
 *
 * <p>The cascade is handled at the database level (ON DELETE CASCADE). The service only needs to
 * delete the {@code user_account} row; all child rows are removed automatically.
 */
public interface DeleteAccountUseCase {

  void execute(OwnerId ownerId);
}
