package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link CardJpaEntity}. */
interface CardJpaRepository extends JpaRepository<CardJpaEntity, UUID> {

  List<CardJpaEntity> findByNoteIdOrderByOrdinalAsc(UUID noteId);

  @Query(
      """
      SELECT c FROM CardJpaEntity c
      WHERE (:noteIds) IS NULL OR c.noteId IN :noteIds
        AND (:suspended IS NULL OR c.suspended = :suspended)
      ORDER BY c.createdAt ASC
      """)
  List<CardJpaEntity> findByNoteIds(
      @Param("noteIds") List<UUID> noteIds, @Param("suspended") Boolean suspended);

  void deleteByNoteId(UUID noteId);

  @Query(
      """
      SELECT count(c) FROM CardJpaEntity c
      WHERE c.noteId IN (SELECT n.id FROM NoteJpaEntity n WHERE n.deckId = :deckId)
        AND (:suspended IS NULL OR c.suspended = :suspended)
      """)
  long countByDeckId(@Param("deckId") UUID deckId, @Param("suspended") Boolean suspended);

  @Query(
      """
      SELECT c FROM CardJpaEntity c
      WHERE c.noteId IN (SELECT n.id FROM NoteJpaEntity n WHERE n.deckId = :deckId)
        AND (:suspended IS NULL OR c.suspended = :suspended)
      ORDER BY c.createdAt ASC
      """)
  List<CardJpaEntity> findByDeckId(
      @Param("deckId") UUID deckId, @Param("suspended") Boolean suspended);

  /**
   * Finds cards owned by {@code ownerId}, joining card -> note -> deck.
   *
   * <p>When {@code deckId} is non-null the result is further restricted to that specific deck.
   * Pagination is via explicit OFFSET/LIMIT parameters to avoid page-number arithmetic.
   */
  @Query(
      value =
          """
          SELECT c.* FROM card c
          JOIN note n ON n.id = c.note_id
          JOIN deck d ON d.id = n.deck_id
          WHERE d.owner_id = :ownerId
            AND (:deckId IS NULL OR n.deck_id = :deckId)
            AND (:suspended IS NULL OR c.suspended = :suspended)
          ORDER BY c.created_at ASC
          LIMIT :lim OFFSET :off
          """,
      nativeQuery = true)
  List<CardJpaEntity> findByOwner(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("suspended") Boolean suspended,
      @Param("off") int offset,
      @Param("lim") int limit);

  /** Counts cards owned by {@code ownerId}, optionally restricted to a specific deck. */
  @Query(
      """
      SELECT count(c) FROM CardJpaEntity c
      JOIN NoteJpaEntity n ON n.id = c.noteId
      JOIN DeckJpaEntity d ON d.id = n.deckId
      WHERE d.ownerId = :ownerId
        AND (:deckId IS NULL OR n.deckId = :deckId)
        AND (:suspended IS NULL OR c.suspended = :suspended)
      """)
  long countByOwner(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("suspended") Boolean suspended);
}
