package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.domain.port.in.LogoutAllSessionsUseCase;
import com.studydeck.domain.port.in.UpdateUserPreferencesUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload;
import com.studydeck.infrastructure.adapter.in.web.dto.AccountExportResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.UserPreferencesPatchRequest;
import com.studydeck.infrastructure.adapter.in.web.mapper.ImportExportMapper;
import jakarta.validation.Valid;
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
 * </ul>
 */
@RestController
@RequestMapping("/v1/account")
class AccountController {

  private final ExportAccountUseCase exportAccount;
  private final DeleteAccountUseCase deleteAccount;
  private final UpdateUserPreferencesUseCase updateUserPreferences;
  private final LogoutAllSessionsUseCase logoutAllSessions;
  private final ImportExportMapper importExportMapper;
  private final ObjectMapper objectMapper;

  AccountController(
      @Qualifier("exportAccountUseCase") ExportAccountUseCase exportAccount,
      @Qualifier("deleteAccountUseCase") DeleteAccountUseCase deleteAccount,
      @Qualifier("updateUserPreferencesUseCase") UpdateUserPreferencesUseCase updateUserPreferences,
      @Qualifier("logoutAllSessionsUseCase") LogoutAllSessionsUseCase logoutAllSessions,
      ImportExportMapper importExportMapper,
      ObjectMapper objectMapper) {
    this.exportAccount = exportAccount;
    this.deleteAccount = deleteAccount;
    this.updateUserPreferences = updateUserPreferences;
    this.logoutAllSessions = logoutAllSessions;
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
            request.timezone()));
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
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    return new OwnerId(UUID.fromString(jwt.getSubject()));
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
