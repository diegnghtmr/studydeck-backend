package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProvider;
import com.studydeck.domain.model.UserAiProviderId;
import com.studydeck.domain.port.out.UserAiProviderRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of {@link UserAiProviderRepository}.
 *
 * <p>Transactions managed at the adapter boundary — domain and application remain framework-free.
 * All mutating operations are transactional. Read operations use read-only transactions.
 */
@Transactional
class UserAiProviderPersistenceAdapter implements UserAiProviderRepository {

  private final UserAiProviderJpaRepository jpaRepo;
  private final UserAiProviderMapper mapper;

  UserAiProviderPersistenceAdapter(
      UserAiProviderJpaRepository jpaRepo, UserAiProviderMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(UserAiProvider provider) {
    jpaRepo.save(mapper.toJpa(provider));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAiProvider> findByIdAndOwner(UserAiProviderId id, OwnerId owner) {
    return jpaRepo.findByIdAndOwnerId(id.value(), owner.value()).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserAiProvider> findAllByOwner(OwnerId owner) {
    return jpaRepo.findAllByOwnerId(owner.value()).stream().map(mapper::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAiProvider> findActiveByOwner(OwnerId owner) {
    return jpaRepo.findByOwnerIdAndActiveTrue(owner.value()).map(mapper::toDomain);
  }

  @Override
  public void deleteByIdAndOwner(UserAiProviderId id, OwnerId owner) {
    jpaRepo.deleteByIdAndOwnerId(id.value(), owner.value());
  }

  @Override
  public void deactivateAllForOwner(OwnerId owner) {
    jpaRepo.deactivateAllByOwnerId(owner.value());
  }
}
