package com.studydeck.infrastructure.adapter.in.web.mcp.dto;

import java.util.List;

/**
 * REST DTO — describes a single MCP prompt exposed by this server.
 *
 * <p>Matches the {@code McpPromptDescriptor} schema in the parent openapi.yaml.
 */
public record McpPromptDescriptor(String name, String description, List<String> arguments) {}
