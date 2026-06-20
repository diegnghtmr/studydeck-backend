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
}
