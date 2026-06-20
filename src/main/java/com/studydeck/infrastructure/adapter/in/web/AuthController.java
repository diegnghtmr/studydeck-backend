package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GetCurrentPrincipalQuery;
import com.studydeck.infrastructure.adapter.in.web.dto.AuthPrincipalResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for auth/me endpoint.
 *
 * <p>Resolves the authenticated principal from the JWT and delegates to {@link
 * GetCurrentPrincipalQuery}.
 */
@RestController
@RequestMapping("/v1/auth")
class AuthController {

  private final GetCurrentPrincipalQuery getCurrentPrincipal;

  AuthController(GetCurrentPrincipalQuery getCurrentPrincipal) {
    this.getCurrentPrincipal = getCurrentPrincipal;
  }

  @GetMapping("/me")
  ResponseEntity<AuthPrincipalResponse> me(@AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    String email = jwt.getClaimAsString("email");
    String displayName = jwt.getClaimAsString("name");

    var query =
        new GetCurrentPrincipalQuery.Query(
            ownerId, email != null ? email : jwt.getSubject(), displayName);
    GetCurrentPrincipalQuery.Principal principal = getCurrentPrincipal.execute(query);

    // Extract roles and scopes from JWT scopes claim
    List<String> scopes =
        jwt.getClaimAsStringList("scope") != null ? jwt.getClaimAsStringList("scope") : List.of();

    // Roles are mapped from SCOPE_ authorities
    List<String> roles = List.of();

    Instant now = Instant.now();
    var response =
        new AuthPrincipalResponse(
            principal.ownerId().value(),
            jwt.getSubject(),
            principal.email(),
            principal.displayName(),
            roles,
            scopes,
            now,
            now);
    return ResponseEntity.ok(response);
  }
}
