package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code import_job} table.
 *
 * <p>Tracks each import operation (POST /v1/imports/flashcards).
 */
@Entity
@Table(name = "import_job")
class ImportJobJpaEntity {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "deck_id")
  private UUID deckId;

  @Column(name = "schema_version", nullable = false)
  private String schemaVersion;

  @Column(nullable = false)
  private String status;

  @Column(name = "imported_notes", nullable = false)
  private int importedNotes;

  @Column(name = "imported_cards", nullable = false)
  private int importedCards;

  @Column(name = "duplicate_notes", nullable = false)
  private int duplicateNotes;

  @Column(name = "rejected_notes", nullable = false)
  private int rejectedNotes;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "text[]")
  private List<String> warnings = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ImportJobJpaEntity() {}

  // --- Getters and setters ---

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  UUID getDeckId() {
    return deckId;
  }

  void setDeckId(UUID deckId) {
    this.deckId = deckId;
  }

  String getSchemaVersion() {
    return schemaVersion;
  }

  void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  String getStatus() {
    return status;
  }

  void setStatus(String status) {
    this.status = status;
  }

  int getImportedNotes() {
    return importedNotes;
  }

  void setImportedNotes(int importedNotes) {
    this.importedNotes = importedNotes;
  }

  int getImportedCards() {
    return importedCards;
  }

  void setImportedCards(int importedCards) {
    this.importedCards = importedCards;
  }

  int getDuplicateNotes() {
    return duplicateNotes;
  }

  void setDuplicateNotes(int duplicateNotes) {
    this.duplicateNotes = duplicateNotes;
  }

  int getRejectedNotes() {
    return rejectedNotes;
  }

  void setRejectedNotes(int rejectedNotes) {
    this.rejectedNotes = rejectedNotes;
  }

  List<String> getWarnings() {
    return warnings;
  }

  void setWarnings(List<String> warnings) {
    this.warnings = (warnings != null) ? new ArrayList<>(warnings) : new ArrayList<>();
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
