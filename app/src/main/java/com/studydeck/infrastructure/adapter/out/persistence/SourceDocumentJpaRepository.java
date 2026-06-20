package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link SourceDocumentJpaEntity}. */
interface SourceDocumentJpaRepository extends JpaRepository<SourceDocumentJpaEntity, UUID> {

  @Query(
      """
      SELECT d FROM SourceDocumentJpaEntity d
      WHERE d.ownerId = :ownerId
        AND (:ingestStatus IS NULL OR d.ingestStatus = :ingestStatus)
      ORDER BY d.createdAt DESC
      """)
  List<SourceDocumentJpaEntity> findByOwnerWithFilter(
      @Param("ownerId") UUID ownerId, @Param("ingestStatus") String ingestStatus);

  @Query(
      """
      SELECT COUNT(d) FROM SourceDocumentJpaEntity d
      WHERE d.ownerId = :ownerId
        AND (:ingestStatus IS NULL OR d.ingestStatus = :ingestStatus)
      """)
  long countByOwnerWithFilter(
      @Param("ownerId") UUID ownerId, @Param("ingestStatus") String ingestStatus);
}
