package com.studydeck.infrastructure.adapter.in.web.mcp.dto;

import java.util.Map;

/**
 * REST DTO — request body for {@code POST /v1/mcp/tools/{toolName}:invoke}.
 *
 * <p>Matches the {@code McpInvokeRequest} schema in the parent openapi.yaml.
 */
public record McpInvokeRequest(Map<String, Object> arguments) {}
