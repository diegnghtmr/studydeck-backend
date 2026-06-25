package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SchedulerAlgorithm;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.domain.port.in.ListSessionsQuery;
import com.studydeck.domain.port.in.LogoutAllSessionsUseCase;
import com.studydeck.domain.port.in.RevokeSessionUseCase;
import com.studydeck.domain.port.in.UpdateUserPreferencesUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload;
import com.studydeck.infrastructure.adapter.in.web.dto.AccountExportResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.SessionResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.UserPreferencesPatchRequest;
import com.studydeck.infrastructure.adapter.in.web.mapper.ImportExportMapper;
import jakarta.validation.Valid;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Driving adapter — REST controller for self-service account operations.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /v1/account/export — GDPR data portability (scope: export.read)
 *   <li>DELETE /v1/account — GDPR right to erasure (scope: study.write)
 *   <li>PATCH /v1/account/preferences — update user preferences (scope: study.write)
 *   <li>POST /v1/account/logout-all — revoke all sessions (scope: study.write)
 *   <li>GET /v1/account/sessions — list active sessions (scope: study.write)
 *   <li>DELETE /v1/account/sessions/{sessionId} — revoke a session (scope: study.write)
 * </ul>
 */
@RestController
@RequestMapping("/v1/account")
class AccountController {

  private static final DateTimeFormatter ISO_INSTANT =
      DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  private final ExportAccountUseCase exportAccount;
  private final DeleteAccountUseCase deleteAccount;
  private final UpdateUserPreferencesUseCase updateUserPreferences;
  private final LogoutAllSessionsUseCase logoutAllSessions;
  private final ListSessionsQuery listSessions;
  private final RevokeSessionUseCase revokeSession;
  private final ImportExportMapper importExportMapper;
  private final ObjectMapper objectMapper;

  AccountController(
      @Qualifier("exportAccountUseCase") ExportAccountUseCase exportAccount,
      @Qualifier("deleteAccountUseCase") DeleteAccountUseCase deleteAccount,
      @Qualifier("updateUserPreferencesUseCase") UpdateUserPreferencesUseCase updateUserPreferences,
      @Qualifier("logoutAllSessionsUseCase") LogoutAllSessionsUseCase logoutAllSessions,
      @Qualifier("listSessionsQuery") ListSessionsQuery listSessions,
      @Qualifier("revokeSessionUseCase") RevokeSessionUseCase revokeSession,
      ImportExportMapper importExportMapper,
      ObjectMapper objectMapper) {
    this.exportAccount = exportAccount;
    this.deleteAccount = deleteAccount;
    this.updateUserPreferences = updateUserPreferences;
    this.logoutAllSessions = logoutAllSessions;
    this.listSessions = listSessions;
    this.revokeSession = revokeSession;
    this.importExportMapper = importExportMapper;
    this.objectMapper = objectMapper;
  }

  // ---------------------------------------------------------------
  // GET /v1/account/export
  // ---------------------------------------------------------------

  @GetMapping("/export")
  @PreAuthorize("hasAuthority('SCOPE_export.read')")
  ResponseEntity<AccountExportResponse> exportAccount(@AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    ExportAccountUseCase.Result result = exportAccount.execute(ownerId);

    AccountExportResponse.AccountSummary accountSummary =
        new AccountExportResponse.AccountSummary(
            result.account().getId().value(),
            jwt.getSubject(),
            result.account().getEmail(),
            result.account().getDisplayName(),
            result.account().getCreatedAt());

    List<Object> deckJsons = toDeckJsonList(result.decks());

    List<AccountExportResponse.DocumentSummary> docSummaries =
        result.documents().stream().map(this::toDocumentSummary).toList();

    AccountExportResponse response =
        new AccountExportResponse(accountSummary, deckJsons, docSummaries, result.exportedAt());

    return ResponseEntity.ok(response);
  }

  // ---------------------------------------------------------------
  // DELETE /v1/account
  // ---------------------------------------------------------------

  @DeleteMapping
  @PreAuthorize("hasAuthority('SCOPE_study.write')")
  ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    deleteAccount.execute(ownerId);
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------
  // PATCH /v1/account/preferences
  // ---------------------------------------------------------------

  @PatchMapping("/preferences")
  @PreAuthorize("hasAuthority('SCOPE_study.write')")
  ResponseEntity<Void> updatePreferences(
      @Valid @RequestBody UserPreferencesPatchRequest request, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    updateUserPreferences.execute(
        new UpdateUserPreferencesUseCase.Command(
            ownerId,
            request.dailyGoal(),
            request.desiredRetention(),
            request.newCardsPerDay(),
            request.language(),
            request.timezone(),
            request.schedulerAlgorithm() != null
                ? SchedulerAlgorithm.valueOf(request.schedulerAlgorithm())
                : null));
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------
  // POST /v1/account/logout-all
  // ---------------------------------------------------------------

  @PostMapping("/logout-all")
  @PreAuthorize("hasAuthority('SCOPE_study.write')")
  ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    logoutAllSessions.execute(ownerId);
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------
  // GET /v1/account/sessions
  // ---------------------------------------------------------------

  @GetMapping("/sessions")
  @PreAuthorize("hasAuthority('SCOPE_study.write')")
  ResponseEntity<List<SessionResponse>> listSessions(@AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    String currentSid = jwt.getClaimAsString("sid");

    List<IdpSession> sessions = listSessions.execute(new ListSessionsQuery.Query(ownerId));

    List<SessionResponse> response =
        sessions.stream().map(s -> toSessionResponse(s, currentSid)).toList();

    return ResponseEntity.ok(response);
  }

  // ---------------------------------------------------------------
  // DELETE /v1/account/sessions/{sessionId}
  // ---------------------------------------------------------------

  @DeleteMapping("/sessions/{sessionId}")
  @PreAuthorize("hasAuthority('SCOPE_study.write')")
  ResponseEntity<Void> revokeSession(
      @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    revokeSession.execute(new RevokeSessionUseCase.Command(ownerId, sessionId));
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    return new OwnerId(UUID.fromString(jwt.getSubject()));
  }

  private SessionResponse toSessionResponse(IdpSession session, String currentSid) {
    String device = session.clients().isEmpty() ? "Unknown" : String.join(", ", session.clients());
    String startedAt = ISO_INSTANT.format(session.started());
    String lastAccessAt = ISO_INSTANT.format(session.lastAccess());
    boolean current = session.id().equals(currentSid);
    return new SessionResponse(
        session.id(), session.ipAddress(), device, startedAt, lastAccessAt, current);
  }

  private List<Object> toDeckJsonList(List<ImportPayload> payloads) {
    List<Object> result = new ArrayList<>();
    for (ImportPayload payload : payloads) {
      tools.jackson.databind.JsonNode node = importExportMapper.toJson(payload, objectMapper);
      result.add(node);
    }
    return result;
  }

  private AccountExportResponse.DocumentSummary toDocumentSummary(SourceDocument doc) {
    return new AccountExportResponse.DocumentSummary(
        doc.getId().value(),
        doc.getTitle(),
        doc.getSourceType(),
        doc.getIngestStatus().name(),
        doc.getCreatedAt());
  }
}
