package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for the authenticated principal, matching the OpenAPI AuthPrincipal schema.
 *
 * <p>id is the internal OwnerId (UUID). subject is the JWT sub claim. email and displayName come
 * from JWT claims. roles and scopes are populated from the resolved JWT authorities.
 */
public record AuthPrincipalResponse(
    UUID id,
    String subject,
    String email,
    String displayName,
    List<String> roles,
    List<String> scopes,
    Instant createdAt,
    Instant updatedAt) {}
