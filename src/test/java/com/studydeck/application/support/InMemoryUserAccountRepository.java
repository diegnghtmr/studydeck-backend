package com.studydeck.application.support;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double for {@link UserAccountRepository}.
 *
 * <p>Not a Spring bean — instantiated directly in unit tests.
 */
public class InMemoryUserAccountRepository implements UserAccountRepository {

  private final Map<OwnerId, UserAccount> store = new ConcurrentHashMap<>();

  @Override
  public Optional<UserAccount> findById(OwnerId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void save(UserAccount userAccount) {
    store.put(userAccount.getId(), userAccount);
  }

  @Override
  public boolean existsById(OwnerId id) {
    return store.containsKey(id);
  }

  /** Test helper: number of stored accounts. */
  public int size() {
    return store.size();
  }

  /** Test helper: clears all stored accounts. */
  public void clear() {
    store.clear();
  }
}
