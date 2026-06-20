package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.port.out.DocumentChunkRepository;
import com.studydeck.domain.port.out.EmbeddingRecordRepository;
import com.studydeck.domain.port.out.IngestJobRepository2;
import com.studydeck.domain.port.out.SourceDocumentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires corpus persistence adapters for the AI/RAG subsystem.
 *
 * <p>Lives in the same package as the adapters so it can access package-private classes. Follows
 * the same pattern as {@link PersistenceConfiguration} for existing domain repositories.
 */
@Configuration
public class CorpusPersistenceConfiguration {

  @Bean
  CorpusPersistenceMapper corpusPersistenceMapper(ObjectMapper objectMapper) {
    return new CorpusPersistenceMapper(objectMapper);
  }

  @Bean
  SourceDocumentRepository sourceDocumentRepository(
      SourceDocumentJpaRepository jpaRepo, CorpusPersistenceMapper mapper) {
    return new SourceDocumentPersistenceAdapter(jpaRepo, mapper);
  }

  @Bean
  DocumentChunkRepository documentChunkRepository(
      DocumentChunkJpaRepository jpaRepo, CorpusPersistenceMapper mapper) {
    return new DocumentChunkPersistenceAdapter(jpaRepo, mapper);
  }

  @Bean
  EmbeddingRecordRepository embeddingRecordRepository(
      EmbeddingRecordJpaRepository jpaRepo, CorpusPersistenceMapper mapper) {
    return new EmbeddingRecordPersistenceAdapter(jpaRepo, mapper);
  }

  @Bean
  IngestJobRepository2 ingestJobRepository2(
      IngestJobJpaRepository2 jpaRepo, CorpusPersistenceMapper mapper) {
    return new IngestJobPersistenceAdapter2(jpaRepo, mapper);
  }
}
