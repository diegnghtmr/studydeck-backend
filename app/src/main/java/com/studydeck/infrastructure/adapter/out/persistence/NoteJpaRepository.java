package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link NoteJpaEntity}. */
interface NoteJpaRepository extends JpaRepository<NoteJpaEntity, UUID> {

  /**
   * Native query that filters notes by owner (via deck join), with optional deck/type/tag/search
   * filters, PostgreSQL array-containment for tags, and explicit OFFSET/LIMIT pagination — all
   * executed in a single database round-trip.
   */
  @Query(
      value =
          """
          SELECT n.* FROM note n
          JOIN deck d ON d.id = n.deck_id
          WHERE d.owner_id = :ownerId
            AND (:deckId IS NULL OR n.deck_id = :deckId)
            AND (:noteType IS NULL OR n.note_type = :noteType)
            AND (:search IS NULL OR cast(n.content as text) LIKE concat('%', :search, '%'))
            AND (:tag IS NULL OR n.tags @> ARRAY[:tag]::text[])
          ORDER BY n.created_at ASC
          LIMIT :lim OFFSET :off
          """,
      nativeQuery = true)
  List<NoteJpaEntity> findWithFiltersNative(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("noteType") String noteType,
      @Param("search") String search,
      @Param("tag") String tag,
      @Param("off") int offset,
      @Param("lim") int limit);

  @Query(
      value =
          """
          SELECT count(*) FROM note n
          JOIN deck d ON d.id = n.deck_id
          WHERE d.owner_id = :ownerId
            AND (:deckId IS NULL OR n.deck_id = :deckId)
            AND (:noteType IS NULL OR n.note_type = :noteType)
            AND (:search IS NULL OR cast(n.content as text) LIKE concat('%', :search, '%'))
            AND (:tag IS NULL OR n.tags @> ARRAY[:tag]::text[])
          """,
      nativeQuery = true)
  long countWithFiltersNative(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("noteType") String noteType,
      @Param("search") String search,
      @Param("tag") String tag);

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
