package com.studydeck.infrastructure.adapter.in.web.mcp.dto;

import java.util.Map;

/**
 * REST DTO — response for {@code POST /v1/mcp/tools/{toolName}:invoke}.
 *
 * <p>Matches the {@code McpInvokeResponse} schema in the parent openapi.yaml.
 */
public record McpInvokeResponse(
    String toolName, boolean success, Map<String, Object> content, String error) {

  public static McpInvokeResponse success(String toolName, Map<String, Object> content) {
    return new McpInvokeResponse(toolName, true, content, null);
  }

  public static McpInvokeResponse failure(String toolName, String error) {
    return new McpInvokeResponse(toolName, false, null, error);
  }
}
