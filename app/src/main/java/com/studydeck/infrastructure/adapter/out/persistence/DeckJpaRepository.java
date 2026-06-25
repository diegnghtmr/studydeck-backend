package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link DeckJpaEntity}. */
interface DeckJpaRepository extends JpaRepository<DeckJpaEntity, UUID> {

  /**
   * Returns decks for an owner with server-side OFFSET/LIMIT filtering — no in-memory pagination.
   *
   * <p>Native query is used so that the LIMIT and OFFSET parameters are pushed all the way to the
   * database engine, avoiding loading all owner rows into JVM memory.
   */
  @Query(
      value =
          "SELECT * FROM deck d "
              + "WHERE d.owner_id = :ownerId "
              + "AND (:includeArchived = TRUE OR d.archived = FALSE) "
              + "AND (:search IS NULL "
              + "     OR lower(d.title) LIKE lower(concat('%', :search, '%')) "
              + "     OR lower(d.description) LIKE lower(concat('%', :search, '%'))) "
              + "ORDER BY d.created_at ASC "
              + "LIMIT :limit OFFSET :offset",
      nativeQuery = true)
  List<DeckJpaEntity> findByOwner(
      @Param("ownerId") UUID ownerId,
      @Param("includeArchived") boolean includeArchived,
      @Param("search") String search,
      @Param("limit") int limit,
      @Param("offset") int offset);

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
