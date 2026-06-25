package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProviderId;
import com.studydeck.domain.port.in.DeleteUserAiProviderUseCase;
import com.studydeck.domain.port.in.ListUserAiProvidersQuery;
import com.studydeck.domain.port.in.SaveUserAiProviderUseCase;
import com.studydeck.domain.port.out.CryptoPort.CryptoUnavailableException;
import com.studydeck.infrastructure.adapter.in.web.dto.AiProviderCreateRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.AiProviderResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.AiProviderUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for user AI provider CRUD operations.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /v1/account/ai-providers — create a new provider (returns 201 with masked metadata)
 *   <li>GET /v1/account/ai-providers — list all providers (masked key hint only, never plaintext)
 *   <li>PUT /v1/account/ai-providers/{id} — update a provider
 *   <li>PATCH /v1/account/ai-providers/{id}/activate — set as active (deactivates others)
 *   <li>DELETE /v1/account/ai-providers/{id} — delete (404 if absent or cross-owner)
 * </ul>
 *
 * <p>Scope enforcement: {@code SCOPE_study.write} on all endpoints (consistent with
 * AccountController).
 *
 * <p>IDOR policy: cross-owner access yields 404, never 403, to avoid confirming resource existence.
 *
 * <p>Key policy: plaintext {@code apiKey} is accepted on write but NEVER returned in any response.
 */
@RestController
@RequestMapping("/v1/account/ai-providers")
@PreAuthorize("hasAuthority('SCOPE_study.write')")
class UserAiProviderController {

  private final SaveUserAiProviderUseCase saveUserAiProvider;
  private final ListUserAiProvidersQuery listUserAiProviders;
  private final DeleteUserAiProviderUseCase deleteUserAiProvider;

  UserAiProviderController(
      @Qualifier("saveUserAiProviderUseCase") SaveUserAiProviderUseCase saveUserAiProvider,
      @Qualifier("listUserAiProvidersQuery") ListUserAiProvidersQuery listUserAiProviders,
      @Qualifier("deleteUserAiProviderUseCase") DeleteUserAiProviderUseCase deleteUserAiProvider) {
    this.saveUserAiProvider = saveUserAiProvider;
    this.listUserAiProviders = listUserAiProviders;
    this.deleteUserAiProvider = deleteUserAiProvider;
  }

  // ---------------------------------------------------------------
  // POST /v1/account/ai-providers — create
  // ---------------------------------------------------------------

  @PostMapping
  ResponseEntity<?> createProvider(
      @Valid @RequestBody AiProviderCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    try {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              ownerId,
              null, // null = create
              request.label(),
              request.baseUrl(),
              request.model(),
              request.apiKey(),
              false);
      var result = saveUserAiProvider.save(cmd);
      return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    } catch (CryptoUnavailableException ex) {
      return cryptoUnavailableResponse(ex);
    }
  }

  // ---------------------------------------------------------------
  // GET /v1/account/ai-providers — list
  // ---------------------------------------------------------------

  @GetMapping
  ResponseEntity<List<AiProviderResponse>> listProviders(@AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    List<ListUserAiProvidersQuery.Masked> results = listUserAiProviders.list(ownerId);
    List<AiProviderResponse> response =
        results.stream().map(UserAiProviderController::toResponse).toList();
    return ResponseEntity.ok(response);
  }

  // ---------------------------------------------------------------
  // PUT /v1/account/ai-providers/{id} — update
  // ---------------------------------------------------------------

  @PutMapping("/{id}")
  ResponseEntity<?> updateProvider(
      @PathVariable UUID id,
      @Valid @RequestBody AiProviderUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    try {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              ownerId,
              new UserAiProviderId(id),
              request.label(),
              request.baseUrl(),
              request.model(),
              request.apiKey(), // null = preserve existing key
              false);
      var result = saveUserAiProvider.save(cmd);
      return ResponseEntity.ok(toResponse(result));
    } catch (NotFoundException ex) {
      throw ex; // GlobalExceptionHandler → 404
    } catch (CryptoUnavailableException ex) {
      return cryptoUnavailableResponse(ex);
    }
  }

  // ---------------------------------------------------------------
  // PATCH /v1/account/ai-providers/{id}/activate
  // ---------------------------------------------------------------

  @PatchMapping("/{id}/activate")
  ResponseEntity<?> activateProvider(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    // We need the existing provider's data to pass back — load current state via SaveUseCase
    // with setActive=true. If id is not owned by this user → NotFoundException → 404.
    try {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              ownerId,
              new UserAiProviderId(id),
              null, // preserve current label
              null, // preserve current baseUrl
              null, // preserve current model
              null, // preserve current key
              true); // setActive = true
      var result = saveUserAiProvider.save(cmd);
      return ResponseEntity.ok(toResponse(result));
    } catch (NotFoundException ex) {
      throw ex; // GlobalExceptionHandler → 404
    } catch (CryptoUnavailableException ex) {
      return cryptoUnavailableResponse(ex);
    }
  }

  // ---------------------------------------------------------------
  // DELETE /v1/account/ai-providers/{id}
  // ---------------------------------------------------------------

  @DeleteMapping("/{id}")
  ResponseEntity<Void> deleteProvider(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = ownerIdFrom(jwt);
    var cmd = new DeleteUserAiProviderUseCase.Command(ownerId, new UserAiProviderId(id));
    deleteUserAiProvider.execute(cmd); // throws NotFoundException if absent/cross-owner → 404
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private OwnerId ownerIdFrom(Jwt jwt) {
    return new OwnerId(UUID.fromString(jwt.getSubject()));
  }

  private static AiProviderResponse toResponse(SaveUserAiProviderUseCase.Result result) {
    return new AiProviderResponse(
        result.id().value(),
        result.label(),
        result.baseUrl(),
        result.model(),
        result.keyHint(),
        result.active(),
        result.createdAt(),
        result.updatedAt());
  }

  private static AiProviderResponse toResponse(ListUserAiProvidersQuery.Masked masked) {
    return new AiProviderResponse(
        masked.id().value(),
        masked.label(),
        masked.baseUrl(),
        masked.model(),
        masked.keyHint(),
        masked.active(),
        masked.createdAt(),
        masked.updatedAt());
  }

  private ResponseEntity<ProblemDetail> cryptoUnavailableResponse(CryptoUnavailableException ex) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    pd.setTitle("Encryption Not Configured");
    pd.setType(URI.create("https://studydeck.ai/errors/crypto-unavailable"));
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }
}
