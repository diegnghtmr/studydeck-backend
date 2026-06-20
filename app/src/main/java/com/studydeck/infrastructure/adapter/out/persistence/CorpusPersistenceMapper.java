package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.ChunkId;
import com.studydeck.domain.model.DocumentChunk;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.EmbeddingRecord;
import com.studydeck.domain.model.EmbeddingRecordId;
import com.studydeck.domain.model.IngestJob;
import com.studydeck.domain.model.IngestJobId;
import com.studydeck.domain.model.IngestStatus;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Mapper between domain corpus models and JPA entities.
 *
 * <p>Metadata is serialized/deserialized as JSON string ↔ {@code Map<String, Object>} using the
 * Spring Boot 4 / Jackson 3.x {@link ObjectMapper}.
 */
class CorpusPersistenceMapper {

  private final ObjectMapper objectMapper;

  CorpusPersistenceMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // ---------------------------------------------------------------
  // SourceDocument
  // ---------------------------------------------------------------

  SourceDocumentJpaEntity toJpa(SourceDocument doc) {
    var entity = new SourceDocumentJpaEntity();
    entity.setId(doc.getId().value());
    entity.setOwnerId(doc.getOwnerId().value());
    entity.setTitle(doc.getTitle());
    entity.setSourceType(doc.getSourceType());
    entity.setMimeType(doc.getMimeType());
    entity.setOriginalFilename(doc.getOriginalFilename());
    entity.setTextContent(doc.getTextContent());
    entity.setExternalUrl(doc.getExternalUrl());
    entity.setSizeBytes(doc.getSizeBytes());
    entity.setIngestStatus(doc.getIngestStatus().name());
    entity.setMetadata(toJson(doc.getMetadata()));
    entity.setCreatedAt(doc.getCreatedAt());
    entity.setUpdatedAt(doc.getUpdatedAt());
    return entity;
  }

  SourceDocument toDomain(SourceDocumentJpaEntity entity) {
    return SourceDocument.reconstitute(
        new DocumentId(entity.getId()),
        new OwnerId(entity.getOwnerId()),
        entity.getTitle(),
        entity.getSourceType(),
        entity.getMimeType(),
        entity.getOriginalFilename(),
        entity.getTextContent(),
        entity.getExternalUrl(),
        entity.getSizeBytes(),
        IngestStatus.valueOf(entity.getIngestStatus()),
        fromJson(entity.getMetadata()),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  // ---------------------------------------------------------------
  // DocumentChunk
  // ---------------------------------------------------------------

  DocumentChunkJpaEntity toJpa(DocumentChunk chunk) {
    var entity = new DocumentChunkJpaEntity();
    entity.setId(chunk.getId().value());
    entity.setDocumentId(chunk.getDocumentId().value());
    entity.setOwnerId(chunk.getOwnerId().value());
    entity.setOrdinal(chunk.getOrdinal());
    entity.setContent(chunk.getContent());
    entity.setTokenCount(chunk.getTokenCount());
    entity.setMetadata(toJson(chunk.getMetadata()));
    entity.setCreatedAt(chunk.getCreatedAt());
    return entity;
  }

  DocumentChunk toDomain(DocumentChunkJpaEntity entity) {
    return DocumentChunk.reconstitute(
        new ChunkId(entity.getId()),
        new DocumentId(entity.getDocumentId()),
        new OwnerId(entity.getOwnerId()),
        entity.getOrdinal(),
        entity.getContent(),
        entity.getTokenCount(),
        fromJson(entity.getMetadata()),
        entity.getCreatedAt());
  }

  // ---------------------------------------------------------------
  // EmbeddingRecord
  // ---------------------------------------------------------------

  EmbeddingRecordJpaEntity toJpa(EmbeddingRecord record) {
    var entity = new EmbeddingRecordJpaEntity();
    entity.setId(record.getId().value());
    entity.setChunkId(record.getChunkId().value());
    entity.setOwnerId(record.getOwnerId().value());
    entity.setEmbeddingModel(record.getEmbeddingModel());
    entity.setDimensions(record.getDimensions());
    entity.setProvider(record.getProvider());
    entity.setCreatedAt(record.getCreatedAt());
    return entity;
  }

  EmbeddingRecord toDomain(EmbeddingRecordJpaEntity entity) {
    return EmbeddingRecord.reconstitute(
        new EmbeddingRecordId(entity.getId()),
        new ChunkId(entity.getChunkId()),
        new OwnerId(entity.getOwnerId()),
        entity.getEmbeddingModel(),
        entity.getDimensions(),
        entity.getProvider(),
        entity.getCreatedAt());
  }

  // ---------------------------------------------------------------
  // IngestJob
  // ---------------------------------------------------------------

  IngestJobJpaEntity2 toJpa(IngestJob job) {
    var entity = new IngestJobJpaEntity2();
    entity.setId(job.getId().value());
    entity.setDocumentId(job.getDocumentId().value());
    entity.setOwnerId(job.getOwnerId().value());
    entity.setStatus(job.getStatus().name());
    entity.setErrorMessage(job.getErrorMessage());
    entity.setChunksProduced(job.getChunksProduced());
    entity.setStartedAt(job.getStartedAt());
    entity.setFinishedAt(job.getFinishedAt());
    entity.setCreatedAt(job.getCreatedAt());
    return entity;
  }

  IngestJob toDomain(IngestJobJpaEntity2 entity) {
    return IngestJob.reconstitute(
        new IngestJobId(entity.getId()),
        new DocumentId(entity.getDocumentId()),
        new OwnerId(entity.getOwnerId()),
        IngestStatus.valueOf(entity.getStatus()),
        entity.getErrorMessage(),
        entity.getChunksProduced(),
        entity.getStartedAt(),
        entity.getFinishedAt(),
        entity.getCreatedAt());
  }

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private Map<String, Object> fromJson(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String toJson(Map<String, Object> map) {
    if (map == null || map.isEmpty()) return "{}";
    try {
      return objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      return "{}";
    }
  }
}
