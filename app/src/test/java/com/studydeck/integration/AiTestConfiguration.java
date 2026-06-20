package com.studydeck.integration;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.domain.port.out.AsyncIngestPort;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing stub AI beans so integration tests don't require network access
 * (HuggingFace ONNX model download) or a running Ollama/OpenAI service.
 *
 * <p>Loaded via {@code @Import(AiTestConfiguration.class)} on each integration test class, or
 * placed in the auto-detected test configuration path.
 */
@TestConfiguration
public class AiTestConfiguration {

  /** No-op VectorStore — prevents ONNX model download and pgvector schema issues in tests. */
  @Bean
  @Primary
  VectorStore vectorStore() {
    return new VectorStore() {
      @Override
      public void add(List<Document> documents) {
        // no-op
      }

      @Override
      public void delete(List<String> idList) {
        // no-op
      }

      @Override
      public void delete(Filter.Expression filterExpression) {
        // no-op
      }

      @Override
      public List<Document> similaritySearch(SearchRequest request) {
        return List.of();
      }
    };
  }

  /** No-op EmbeddingModel — prevents ONNX model download in tests. */
  @Bean
  @Primary
  EmbeddingModel embeddingModel() {
    return new EmbeddingModel() {

      private static final int DIMS = 384;

      @Override
      public org.springframework.ai.embedding.EmbeddingResponse call(
          org.springframework.ai.embedding.EmbeddingRequest request) {
        var data =
            request.getInstructions().stream()
                .map(t -> new org.springframework.ai.embedding.Embedding(new float[DIMS], 0))
                .toList();
        return new org.springframework.ai.embedding.EmbeddingResponse(data);
      }

      @Override
      public float[] embed(Document document) {
        return new float[DIMS];
      }

      @Override
      public int dimensions() {
        return DIMS;
      }
    };
  }

  /** Stub AsyncIngestPort — no-op; tests don't need real ETL pipeline. */
  @Bean
  @Primary
  AsyncIngestPort asyncIngestPort() {
    return (jobId, documentId, ownerId, targetChunkSize, chunkOverlap) -> {
      // no-op: integration tests do not test the ETL pipeline
    };
  }

  /** Stub TextEmbeddingPort — always returns empty results. */
  @Bean
  @Primary
  TextEmbeddingPort textEmbeddingPort() {
    return new TextEmbeddingPort() {
      @Override
      public String modelName() {
        return "stub-model";
      }

      @Override
      public int dimensions() {
        return 384;
      }

      @Override
      public void embedAndStore(List<ChunkToEmbed> chunks) {
        // no-op
      }

      @Override
      public List<SearchHit> search(
          String query, OwnerId ownerId, List<DocumentId> documentIds, int topK, Double minScore) {
        return List.of();
      }

      @Override
      public void deleteByDocumentId(DocumentId documentId, OwnerId ownerId) {
        // no-op
      }
    };
  }

  /** Stub AiChatPort — always unavailable (no chat provider in tests). */
  @Bean
  @Primary
  AiChatPort aiChatPort() {
    return new AiChatPort() {
      @Override
      public boolean isAvailable() {
        return false;
      }

      @Override
      public RagAnswer ragChat(String question, OwnerId ownerId, List<ContextChunk> contextChunks) {
        throw new AiChatUnavailableException();
      }

      @Override
      public String generateFlashcardsRaw(
          String sourceText, String deckContext, List<String> noteTypes, int maxCards) {
        throw new AiChatUnavailableException();
      }

      @Override
      public String improveFlashcardRaw(
          String noteType, String currentContent, String instruction) {
        throw new AiChatUnavailableException();
      }
    };
  }

  /** Stub AiSchemaValidationPort — always valid (pass-through). */
  @Bean
  @Primary
  AiSchemaValidationPort aiSchemaValidationPort() {
    return new AiSchemaValidationPort() {
      @Override
      public String validateAndReturn(String json) {
        return json;
      }

      @Override
      public List<String> validate(String json) {
        return List.of();
      }
    };
  }
}
