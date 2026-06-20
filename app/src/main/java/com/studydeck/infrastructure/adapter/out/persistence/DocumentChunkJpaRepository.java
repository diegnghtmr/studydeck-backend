package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link DocumentChunkJpaEntity}. */
interface DocumentChunkJpaRepository extends JpaRepository<DocumentChunkJpaEntity, UUID> {

  @Query(
      """
      SELECT c FROM DocumentChunkJpaEntity c
      WHERE c.documentId = :documentId
      ORDER BY c.ordinal ASC
      """)
  List<DocumentChunkJpaEntity> findByDocumentIdOrderByOrdinal(@Param("documentId") UUID documentId);

  long countByDocumentId(UUID documentId);

  @Query(
      """
      SELECT c FROM DocumentChunkJpaEntity c
      WHERE c.ownerId = :ownerId
        AND (:search IS NULL OR LOWER(c.content) LIKE LOWER(CONCAT('%', :search, '%')))
      ORDER BY c.createdAt DESC
      """)
  List<DocumentChunkJpaEntity> findByOwnerWithSearch(
      @Param("ownerId") UUID ownerId, @Param("search") String search);

  @Query(
      """
      SELECT COUNT(c) FROM DocumentChunkJpaEntity c
      WHERE c.ownerId = :ownerId
        AND (:search IS NULL OR LOWER(c.content) LIKE LOWER(CONCAT('%', :search, '%')))
      """)
  long countByOwnerWithSearch(@Param("ownerId") UUID ownerId, @Param("search") String search);

  void deleteByDocumentId(UUID documentId);
}
