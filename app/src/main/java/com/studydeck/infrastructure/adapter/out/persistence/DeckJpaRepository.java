package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link DeckJpaEntity}. */
interface DeckJpaRepository extends JpaRepository<DeckJpaEntity, UUID> {

  @Query(
      """
      SELECT d FROM DeckJpaEntity d
      WHERE d.ownerId = :ownerId
        AND (:includeArchived = true OR d.archived = false)
        AND (:search IS NULL OR :search = ''
             OR lower(d.title) LIKE lower(concat('%', :search, '%'))
             OR lower(d.description) LIKE lower(concat('%', :search, '%')))
      ORDER BY d.createdAt ASC
      """)
  List<DeckJpaEntity> findByOwner(
      @Param("ownerId") UUID ownerId,
      @Param("includeArchived") boolean includeArchived,
      @Param("search") String search);

  @Query(
      """
      SELECT count(d) FROM DeckJpaEntity d
      WHERE d.ownerId = :ownerId
        AND (:includeArchived = true OR d.archived = false)
        AND (:search IS NULL OR :search = ''
             OR lower(d.title) LIKE lower(concat('%', :search, '%'))
             OR lower(d.description) LIKE lower(concat('%', :search, '%')))
      """)
  long countByOwner(
      @Param("ownerId") UUID ownerId,
      @Param("includeArchived") boolean includeArchived,
      @Param("search") String search);
}
