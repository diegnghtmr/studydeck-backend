package com.studydeck.infrastructure.adapter.in.web.mcp.dto;

/**
 * REST DTO — describes a single MCP resource exposed by this server.
 *
 * <p>Matches the {@code McpResourceDescriptor} schema in the parent openapi.yaml.
 */
public record McpResourceDescriptor(String uri, String name, String description, String mimeType) {}
