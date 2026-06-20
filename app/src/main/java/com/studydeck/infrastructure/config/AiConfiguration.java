package com.studydeck.infrastructure.config;

import com.studydeck.application.service.CorpusQueryService;
import com.studydeck.application.service.DeleteDocumentService;
import com.studydeck.application.service.DocumentService;
import com.studydeck.application.service.GenerateFlashcardsService;
import com.studydeck.application.service.GetDocumentService;
import com.studydeck.application.service.ImproveFlashcardService;
import com.studydeck.application.service.RagChatService;
import com.studydeck.application.service.RagSearchService;
import com.studydeck.domain.port.in.CreateDocumentUseCase;
import com.studydeck.domain.port.in.DeleteDocumentUseCase;
import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.in.GetDocumentQuery;
import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.in.IngestDocumentUseCase;
import com.studydeck.domain.port.in.ListChunksQuery;
import com.studydeck.domain.port.in.ListDocumentChunksQuery;
import com.studydeck.domain.port.in.ListDocumentsQuery;
import com.studydeck.domain.port.in.ListEmbeddingsQuery;
import com.studydeck.domain.port.in.RagChatUseCase;
import com.studydeck.domain.port.in.RagSearchUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.domain.port.out.AsyncIngestPort;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DocumentChunkRepository;
import com.studydeck.domain.port.out.EmbeddingRecordRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.IngestJobRepository2;
import com.studydeck.domain.port.out.SourceDocumentRepository;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import com.studydeck.infrastructure.adapter.in.web.ImportSchemaValidator;
import com.studydeck.infrastructure.adapter.out.ai.ImportSchemaValidationAdapter;
import com.studydeck.infrastructure.adapter.out.ai.SpringAiChatAdapter;
import com.studydeck.infrastructure.adapter.out.ai.SpringAiEmbeddingAdapter;
import com.studydeck.infrastructure.adapter.out.persistence.CorpusPersistenceConfiguration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring configuration for all AI/RAG components.
 *
 * <p>Wires:
 *
 * <ul>
 *   <li>Corpus persistence ports (via {@link CorpusPersistenceConfiguration})
 *   <li>Spring AI adapters (embedding, chat)
 *   <li>Application services (domain layer, framework-free)
 *   <li>Graceful no-provider chat behavior (isAvailable=false when ChatModel absent)
 * </ul>
 *
 * <p>Spring AI ONNX Transformers embedding auto-configures when the starter is on the classpath.
 * pgvector VectorStore auto-configures via {@code spring.ai.vectorstore.pgvector.*} properties.
 * Chat providers auto-configure only when their api-key/base-url is set.
 */
@Configuration
@EnableAsync
@Import(CorpusPersistenceConfiguration.class)
public class AiConfiguration {

  // ---------------------------------------------------------------
  // Embedding model info (configurable, defaults match all-MiniLM-L6-v2)
  // ---------------------------------------------------------------

  @Value("${studydeck.ai.embedding.model-name:" + SpringAiEmbeddingAdapter.DEFAULT_MODEL + "}")
  private String embeddingModelName;

  @Value("${studydeck.ai.embedding.dimensions:" + SpringAiEmbeddingAdapter.DEFAULT_DIMENSIONS + "}")
  private int embeddingDimensions;

  // ---------------------------------------------------------------
  // AI output ports (adapters)
  // ---------------------------------------------------------------

  @Bean
  TextEmbeddingPort textEmbeddingPort(VectorStore vectorStore) {
    return new SpringAiEmbeddingAdapter(vectorStore, embeddingModelName, embeddingDimensions);
  }

  /**
   * AiChatPort wraps an optional ChatModel.
   *
   * <p>When multiple ChatModel starters are on the classpath (ollama + openai auto-config), we use
   * ObjectProvider to get the "unique or null" bean — avoiding {@link
   * org.springframework.beans.factory.NoUniqueBeanDefinitionException}. If no unique bean resolves,
   * the adapter degrades gracefully (isAvailable()=false).
   */
  @Bean
  AiChatPort aiChatPort(
      org.springframework.beans.factory.ObjectProvider<ChatModel> chatModelProvider) {
    // getIfUnique() returns null when there are 0 or 2+ ChatModel beans.
    ChatModel model = chatModelProvider.getIfUnique();
    return new SpringAiChatAdapter(model);
  }

  @Bean
  AiSchemaValidationPort aiSchemaValidationPort(
      ImportSchemaValidator schemaValidator, ObjectMapper objectMapper) {
    return new ImportSchemaValidationAdapter(schemaValidator, objectMapper);
  }

  // ---------------------------------------------------------------
  // Application services (use case implementations)
  // ---------------------------------------------------------------

  @Bean
  CreateDocumentUseCase createDocumentUseCase(
      SourceDocumentRepository documentRepository,
      IngestJobRepository2 ingestJobRepository,
      IdGenerator idGenerator,
      ClockPort clock,
      AsyncIngestPort asyncIngestPort) {
    return new DocumentService(
        documentRepository, ingestJobRepository, idGenerator, clock, asyncIngestPort);
  }

  @Bean
  ListDocumentsQuery listDocumentsQuery(
      SourceDocumentRepository documentRepository,
      IngestJobRepository2 ingestJobRepository,
      IdGenerator idGenerator,
      ClockPort clock,
      AsyncIngestPort asyncIngestPort) {
    return new DocumentService(
        documentRepository, ingestJobRepository, idGenerator, clock, asyncIngestPort);
  }

  @Bean
  IngestDocumentUseCase ingestDocumentUseCase(
      SourceDocumentRepository documentRepository,
      IngestJobRepository2 ingestJobRepository,
      IdGenerator idGenerator,
      ClockPort clock,
      AsyncIngestPort asyncIngestPort) {
    return new DocumentService(
        documentRepository, ingestJobRepository, idGenerator, clock, asyncIngestPort);
  }

  @Bean
  GetDocumentQuery getDocumentQuery(SourceDocumentRepository documentRepository) {
    return new GetDocumentService(documentRepository);
  }

  @Bean
  DeleteDocumentUseCase deleteDocumentUseCase(SourceDocumentRepository documentRepository) {
    return new DeleteDocumentService(documentRepository);
  }

  @Bean
  ListDocumentChunksQuery listDocumentChunksQuery(
      SourceDocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      EmbeddingRecordRepository embeddingRepository) {
    return corpusQueryService(documentRepository, chunkRepository, embeddingRepository);
  }

  @Bean
  ListChunksQuery listChunksQuery(
      SourceDocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      EmbeddingRecordRepository embeddingRepository) {
    return corpusQueryService(documentRepository, chunkRepository, embeddingRepository);
  }

  @Bean
  ListEmbeddingsQuery listEmbeddingsQuery(
      SourceDocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      EmbeddingRecordRepository embeddingRepository) {
    return corpusQueryService(documentRepository, chunkRepository, embeddingRepository);
  }

  private CorpusQueryService corpusQueryService(
      SourceDocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      EmbeddingRecordRepository embeddingRepository) {
    return new CorpusQueryService(documentRepository, chunkRepository, embeddingRepository);
  }

  @Bean
  RagSearchUseCase ragSearchUseCase(TextEmbeddingPort embeddingPort) {
    return new RagSearchService(embeddingPort);
  }

  @Bean
  RagChatUseCase ragChatUseCase(TextEmbeddingPort embeddingPort, AiChatPort aiChatPort) {
    return new RagChatService(embeddingPort, aiChatPort);
  }

  @Bean
  GenerateFlashcardsUseCase generateFlashcardsUseCase(
      AiChatPort aiChatPort, AiSchemaValidationPort schemaValidator) {
    return new GenerateFlashcardsService(aiChatPort, schemaValidator);
  }

  @Bean
  ImproveFlashcardUseCase improveFlashcardUseCase(
      AiChatPort aiChatPort, AiSchemaValidationPort schemaValidator) {
    return new ImproveFlashcardService(aiChatPort, schemaValidator);
  }
}
