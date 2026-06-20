package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.util.Optional;
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
    jpaRepo.save(mapper.toJpa(userAccount));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsById(OwnerId id) {
    return jpaRepo.existsById(id.value());
  }
}
