package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.IngestStatus;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.CreateDocumentUseCase;
import com.studydeck.domain.port.in.DeleteDocumentUseCase;
import com.studydeck.domain.port.in.GetDocumentQuery;
import com.studydeck.domain.port.in.IngestDocumentUseCase;
import com.studydeck.domain.port.in.ListChunksQuery;
import com.studydeck.domain.port.in.ListDocumentChunksQuery;
import com.studydeck.domain.port.in.ListDocumentsQuery;
import com.studydeck.domain.port.in.ListEmbeddingsQuery;
import com.studydeck.infrastructure.adapter.in.web.dto.corpus.ChunkResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.corpus.DocumentCreateRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.corpus.DocumentResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.corpus.EmbeddingMetaResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.corpus.IngestJobResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.corpus.RagIngestRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for document corpus, chunks, embeddings, and ingest endpoints.
 *
 * <p>Scope enforcement: documents.read / documents.write
 */
@RestController
@RequestMapping("/v1")
class DocumentController {

  private final CreateDocumentUseCase createDocument;
  private final GetDocumentQuery getDocument;
  private final ListDocumentsQuery listDocuments;
  private final DeleteDocumentUseCase deleteDocument;
  private final IngestDocumentUseCase ingestDocument;
  private final ListDocumentChunksQuery listDocumentChunks;
  private final ListChunksQuery listChunks;
  private final ListEmbeddingsQuery listEmbeddings;

  DocumentController(
      @Qualifier("createDocumentUseCase") CreateDocumentUseCase createDocument,
      @Qualifier("getDocumentQuery") GetDocumentQuery getDocument,
      @Qualifier("listDocumentsQuery") ListDocumentsQuery listDocuments,
      @Qualifier("deleteDocumentUseCase") DeleteDocumentUseCase deleteDocument,
      @Qualifier("ingestDocumentUseCase") IngestDocumentUseCase ingestDocument,
      @Qualifier("listDocumentChunksQuery") ListDocumentChunksQuery listDocumentChunks,
      @Qualifier("listChunksQuery") ListChunksQuery listChunks,
      @Qualifier("listEmbeddingsQuery") ListEmbeddingsQuery listEmbeddings) {
    this.createDocument = createDocument;
    this.getDocument = getDocument;
    this.listDocuments = listDocuments;
    this.deleteDocument = deleteDocument;
    this.ingestDocument = ingestDocument;
    this.listDocumentChunks = listDocumentChunks;
    this.listChunks = listChunks;
    this.listEmbeddings = listEmbeddings;
  }

  // ---------------------------------------------------------------
  // GET /v1/documents
  // ---------------------------------------------------------------

  @GetMapping("/documents")
  @PreAuthorize("hasAuthority('SCOPE_documents.read')")
  ResponseEntity<Map<String, Object>> listDocuments(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String ingestStatus) {
    var ownerId = ownerIdFrom(jwt);
    IngestStatus statusFilter = parseStatus(ingestStatus);
    var result = listDocuments.execute(ownerId, statusFilter, page * size, size);
    var items = result.items().stream().map(DocumentResponse::from).toList();
    return ResponseEntity.ok(
        Map.of(
            "items", items, "page", Map.of("number", page, "size", size, "total", result.total())));
  }

  // ---------------------------------------------------------------
  // POST /v1/documents
  // ---------------------------------------------------------------

  @PostMapping("/documents")
  @PreAuthorize("hasAuthority('SCOPE_documents.write')")
  ResponseEntity<DocumentResponse> createDocument(
      @AuthenticationPrincipal Jwt jwt, @RequestBody DocumentCreateRequest request) {
    var ownerId = ownerIdFrom(jwt);
    var cmd =
        new CreateDocumentUseCase.Command(
            ownerId,
            request.title(),
            request.sourceType(),
            request.mimeType(),
            request.originalFilename(),
            request.textContent(),
            request.externalUrl(),
            request.sizeBytes(),
            request.metadata());
    var doc = createDocument.execute(cmd);
    return ResponseEntity.created(URI.create("/v1/documents/" + doc.getId().value()))
        .body(DocumentResponse.from(doc));
  }

  // ---------------------------------------------------------------
  // GET /v1/documents/{documentId}
  // ---------------------------------------------------------------

  @GetMapping("/documents/{documentId}")
  @PreAuthorize("hasAuthority('SCOPE_documents.read')")
  ResponseEntity<DocumentResponse> getDocument(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
    var ownerId = ownerIdFrom(jwt);
    var doc = getDocument.execute(new DocumentId(documentId), ownerId);
    return ResponseEntity.ok(DocumentResponse.from(doc));
  }

  // ---------------------------------------------------------------
  // DELETE /v1/documents/{documentId}
  // ---------------------------------------------------------------

  @DeleteMapping("/documents/{documentId}")
  @PreAuthorize("hasAuthority('SCOPE_documents.write')")
  ResponseEntity<Void> deleteDocument(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
    var ownerId = ownerIdFrom(jwt);
    deleteDocument.execute(new DocumentId(documentId), ownerId);
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------
  // POST /v1/documents/{documentId}/ingest
  // ---------------------------------------------------------------

  @PostMapping("/documents/{documentId}/ingest")
  @PreAuthorize("hasAuthority('SCOPE_documents.write')")
  ResponseEntity<IngestJobResponse> ingestDocument(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID documentId,
      @RequestBody(required = false) RagIngestRequest request) {
    var ownerId = ownerIdFrom(jwt);
    int targetSize =
        (request != null && request.chunking() != null && request.chunking().targetSize() != null)
            ? request.chunking().targetSize()
            : 400;
    int overlap =
        (request != null && request.chunking() != null && request.chunking().overlap() != null)
            ? request.chunking().overlap()
            : 40;
    var extraMeta =
        (request != null && request.metadata() != null)
            ? request.metadata()
            : Map.<String, Object>of();

    var cmd =
        new IngestDocumentUseCase.Command(
            new DocumentId(documentId), ownerId, targetSize, overlap, extraMeta);
    // execute() persists the PENDING job and triggers async ETL via AsyncIngestPort.
    var job = ingestDocument.execute(cmd);

    return ResponseEntity.accepted().body(IngestJobResponse.from(job));
  }

  // ---------------------------------------------------------------
  // GET /v1/documents/{documentId}/chunks
  // ---------------------------------------------------------------

  @GetMapping("/documents/{documentId}/chunks")
  @PreAuthorize("hasAuthority('SCOPE_documents.read')")
  ResponseEntity<Map<String, Object>> listDocumentChunks(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID documentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var ownerId = ownerIdFrom(jwt);
    var result = listDocumentChunks.execute(new DocumentId(documentId), ownerId, page * size, size);
    var items = result.items().stream().map(ChunkResponse::from).toList();
    return ResponseEntity.ok(
        Map.of(
            "items", items, "page", Map.of("number", page, "size", size, "total", result.total())));
  }

  // ---------------------------------------------------------------
  // GET /v1/chunks
  // ---------------------------------------------------------------

  @GetMapping("/chunks")
  @PreAuthorize("hasAuthority('SCOPE_documents.read')")
  ResponseEntity<Map<String, Object>> listChunks(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search) {
    var ownerId = ownerIdFrom(jwt);
    var result = listChunks.execute(ownerId, search, page * size, size);
    var items = result.items().stream().map(ChunkResponse::from).toList();
    return ResponseEntity.ok(
        Map.of(
            "items", items, "page", Map.of("number", page, "size", size, "total", result.total())));
  }

  // ---------------------------------------------------------------
  // GET /v1/embeddings
  // ---------------------------------------------------------------

  @GetMapping("/embeddings")
  @PreAuthorize("hasAuthority('SCOPE_documents.read')")
  ResponseEntity<Map<String, Object>> listEmbeddings(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var ownerId = ownerIdFrom(jwt);
    var result = listEmbeddings.execute(ownerId, page * size, size);
    var items = result.items().stream().map(EmbeddingMetaResponse::from).toList();
    return ResponseEntity.ok(
        Map.of(
            "items", items, "page", Map.of("number", page, "size", size, "total", result.total())));
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    String sub = jwt.getSubject();
    return new OwnerId(UUID.fromString(sub));
  }

  private IngestStatus parseStatus(String status) {
    if (status == null || status.isBlank()) return null;
    try {
      return IngestStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
