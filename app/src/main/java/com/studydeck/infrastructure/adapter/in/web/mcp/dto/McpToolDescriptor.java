package com.studydeck.infrastructure.adapter.in.web.mcp.dto;

import java.util.Map;

/**
 * REST DTO — describes a single MCP tool exposed by this server.
 *
 * <p>Matches the {@code McpToolDescriptor} schema in the parent openapi.yaml.
 */
public record McpToolDescriptor(
    String name, String title, String description, Map<String, Object> inputSchema) {}
