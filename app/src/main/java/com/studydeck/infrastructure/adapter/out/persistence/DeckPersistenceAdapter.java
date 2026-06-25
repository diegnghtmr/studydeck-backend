package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.DeckRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of {@link DeckRepository}.
 *
 * <p>Transactions are managed at the adapter boundary — domain and application remain
 * framework-free.
 */
@Transactional
class DeckPersistenceAdapter implements DeckRepository {

  private final DeckJpaRepository jpaRepo;
  private final PersistenceMapper mapper;

  DeckPersistenceAdapter(DeckJpaRepository jpaRepo, PersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(Deck deck) {
    jpaRepo.save(mapper.toJpa(deck));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Deck> findById(DeckId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Deck> findByOwner(
      OwnerId ownerId, boolean includeArchived, String search, int offset, int limit) {
    String searchParam = (search == null || search.isBlank()) ? null : search;
    // Delegate OFFSET/LIMIT to the database — avoids loading all owner decks into JVM memory.
    return jpaRepo
        .findByOwner(ownerId.value(), includeArchived, searchParam, limit, offset)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByOwner(OwnerId ownerId, boolean includeArchived, String search) {
    String searchParam = (search == null || search.isBlank()) ? null : search;
    return jpaRepo.countByOwner(ownerId.value(), includeArchived, searchParam);
  }

  @Override
  public void deleteById(DeckId id) {
    jpaRepo.deleteById(id.value());
  }
}
