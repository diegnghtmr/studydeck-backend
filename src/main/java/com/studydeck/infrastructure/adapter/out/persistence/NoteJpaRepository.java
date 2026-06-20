package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link NoteJpaEntity}. */
interface NoteJpaRepository extends JpaRepository<NoteJpaEntity, UUID> {

  @Query(
      """
      SELECT n FROM NoteJpaEntity n
      WHERE (:deckId IS NULL OR n.deckId = :deckId)
        AND (:noteType IS NULL OR n.noteType = :noteType)
        AND (:search IS NULL OR :search = ''
             OR cast(n.content as string) LIKE concat('%', :search, '%'))
      ORDER BY n.createdAt ASC
      """)
  List<NoteJpaEntity> findWithFilters(
      @Param("deckId") UUID deckId,
      @Param("noteType") String noteType,
      @Param("search") String search);

  @Query(
      """
      SELECT count(n) FROM NoteJpaEntity n
      WHERE (:deckId IS NULL OR n.deckId = :deckId)
        AND (:noteType IS NULL OR n.noteType = :noteType)
        AND (:search IS NULL OR :search = ''
             OR cast(n.content as string) LIKE concat('%', :search, '%'))
      """)
  long countWithFilters(
      @Param("deckId") UUID deckId,
      @Param("noteType") String noteType,
      @Param("search") String search);

  void deleteByDeckId(UUID deckId);

  /** Returns all non-null content hashes for a given deck and note type (for import dedup). */
  @Query(
      """
      SELECT n.contentHash FROM NoteJpaEntity n
      WHERE n.deckId = :deckId
        AND n.noteType = :noteType
        AND n.contentHash IS NOT NULL
      """)
  List<String> findContentHashes(@Param("deckId") UUID deckId, @Param("noteType") String noteType);

  /** Updates the content_hash of a single note row by id. */
  @org.springframework.data.jpa.repository.Modifying
  @Query("UPDATE NoteJpaEntity n SET n.contentHash = :hash WHERE n.id = :id")
  void updateContentHash(@Param("id") UUID id, @Param("hash") String hash);
}
