package com.studydeck.infrastructure.adapter.in.web.dto;

/**
 * REST response DTO for the agent skill document, matching the OpenAPI {@code SkillDocument}
 * schema.
 *
 * @param name skill identifier
 * @param version skill version
 * @param contentType media type of {@code content} (default {@code text/markdown})
 * @param content the skill document body (markdown)
 */
public record SkillDocumentResponse(
    String name, String version, String contentType, String content) {}
