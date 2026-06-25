package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of {@link UserAccountRepository}.
 *
 * <p>Transactions are managed at the adapter boundary — domain and application remain
 * framework-free.
 */
@Transactional
class UserAccountPersistenceAdapter implements UserAccountRepository {

  private final UserAccountJpaRepository jpaRepo;
  private final PersistenceMapper mapper;

  UserAccountPersistenceAdapter(UserAccountJpaRepository jpaRepo, PersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findById(OwnerId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public void save(UserAccount userAccount) {
    try {
      jpaRepo.save(mapper.toJpa(userAccount));
    } catch (DataIntegrityViolationException e) {
      // Concurrent insert by another thread/node — treat as success (idempotent provision).
      // The user_account row already exists; no further action needed.
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsById(OwnerId id) {
    return jpaRepo.existsById(id.value());
  }

  @Override
  public void deleteById(OwnerId id) {
    // Idempotent: deleteById on Spring Data JPA is a no-op when the entity does not exist.
    jpaRepo.deleteById(id.value());
  }
}
