package com.studydeck.infrastructure.adapter.in.web.dto;

/** Response DTO for a single active session (GET /v1/account/sessions). */
public record SessionResponse(
    String id,
    String ipAddress,
    String device,
    String startedAt,
    String lastAccessAt,
    boolean current) {}
