package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.RagChatUseCase;
import com.studydeck.domain.port.in.RagSearchUseCase;
import com.studydeck.domain.port.out.AiChatPort.AiChatUnavailableException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for RAG search and RAG chat endpoints.
 *
 * <p>Scope enforcement: rag.query
 */
@RestController
@RequestMapping("/v1/rag")
class RagController {

  private final RagSearchUseCase ragSearch;
  private final RagChatUseCase ragChat;

  RagController(
      @Qualifier("ragSearchUseCase") RagSearchUseCase ragSearch,
      @Qualifier("ragChatUseCase") RagChatUseCase ragChat) {
    this.ragSearch = ragSearch;
    this.ragChat = ragChat;
  }

  // ---------------------------------------------------------------
  // POST /v1/rag/search
  // ---------------------------------------------------------------

  @PostMapping("/search")
  @PreAuthorize("hasAuthority('SCOPE_rag.query')")
  ResponseEntity<Map<String, Object>> ragSearch(
      @AuthenticationPrincipal Jwt jwt, @RequestBody RagSearchRequest request) {
    var ownerId = ownerIdFrom(jwt);
    var cmd =
        new RagSearchUseCase.Command(
            request.query(),
            ownerId,
            toDocumentIds(request.documentIds()),
            request.topK() != null ? request.topK() : 5,
            request.minScore(),
            request.includeContent() == null || request.includeContent());

    var result = ragSearch.execute(cmd);
    var hits =
        result.hits().stream()
            .map(
                h ->
                    Map.of(
                        "chunkId",
                        (Object) h.chunkId().value(),
                        "documentId",
                        h.documentId().value(),
                        "score",
                        h.score(),
                        "content",
                        h.content() != null ? h.content() : ""))
            .toList();
    return ResponseEntity.ok(Map.of("hits", hits));
  }

  // ---------------------------------------------------------------
  // POST /v1/rag/chat
  // ---------------------------------------------------------------

  @PostMapping("/chat")
  @PreAuthorize("hasAuthority('SCOPE_rag.query')")
  ResponseEntity<?> ragChat(
      @AuthenticationPrincipal Jwt jwt, @RequestBody RagChatRequestDto request) {
    var ownerId = ownerIdFrom(jwt);
    try {
      AiProviderConfig providerConfig = null;
      if (request.providerOverride() != null) {
        var dto = request.providerOverride();
        if (dto.baseUrl() == null
            || dto.baseUrl().isBlank()
            || dto.apiKey() == null
            || dto.apiKey().isBlank()
            || dto.model() == null
            || dto.model().isBlank()) {
          throw new IllegalArgumentException(
              "providerOverride requires all fields: baseUrl, apiKey, model");
        }
        providerConfig = new AiProviderConfig(dto.baseUrl(), dto.apiKey(), dto.model());
      }
      var cmd =
          new RagChatUseCase.Command(
              request.message(),
              ownerId,
              toDocumentIds(request.documentIds()),
              request.topK() != null ? request.topK() : 5,
              providerConfig);
      var answer = ragChat.execute(cmd);

      var citations =
          answer.citations().stream()
              .map(
                  c ->
                      Map.of(
                          "chunkId",
                          (Object) c.chunkId(),
                          "documentId",
                          c.documentId(),
                          "score",
                          c.score(),
                          "content",
                          c.content() != null ? c.content() : ""))
              .toList();

      return ResponseEntity.ok(Map.of("answer", answer.answer(), "citations", citations));
    } catch (AiChatUnavailableException ex) {
      return chatUnavailableResponse(ex);
    }
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    return new OwnerId(UUID.fromString(jwt.getSubject()));
  }

  private List<DocumentId> toDocumentIds(List<UUID> uuids) {
    if (uuids == null || uuids.isEmpty()) return List.of();
    return uuids.stream().map(DocumentId::of).toList();
  }

  private ResponseEntity<ProblemDetail> chatUnavailableResponse(AiChatUnavailableException ex) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    pd.setTitle("AI Chat Provider Not Configured");
    pd.setType(URI.create("https://studydeck.ai/errors/ai-chat-unavailable"));
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }

  // ---------------------------------------------------------------
  // Inner request records
  // ---------------------------------------------------------------

  record RagSearchRequest(
      String query,
      Integer topK,
      List<UUID> documentIds,
      Double minScore,
      Boolean includeContent) {}

  record RagChatRequestDto(
      String message,
      List<UUID> documentIds,
      Integer topK,
      Boolean stream,
      AiProviderConfigDto providerOverride) {}

  /** Per-request AI provider config for BYOK (Bring Your Own Key) support. */
  record AiProviderConfigDto(String baseUrl, String apiKey, String model) {}
}
