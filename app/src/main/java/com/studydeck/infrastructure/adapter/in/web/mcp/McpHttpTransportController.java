package com.studydeck.infrastructure.adapter.in.web.mcp;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.infrastructure.adapter.in.web.mcp.McpToolExecutor.McpToolException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Streamable HTTP transport endpoint at {@code /mcp}.
 *
 * <p>Implements a minimal spec-compliant MCP-over-HTTP transport (JSON-RPC 2.0, MCP protocol
 * version 2024-11-05). This is the machine-callable entry point for MCP clients (LLMs, agents).
 *
 * <p>Supported JSON-RPC methods:
 *
 * <ul>
 *   <li>{@code initialize} — handshake; returns server info and capabilities
 *   <li>{@code tools/list} — returns all registered tools
 *   <li>{@code tools/call} — calls a tool by name with arguments
 *   <li>{@code resources/list} — returns all registered resources
 *   <li>{@code resources/read} — reads a resource by URI
 *   <li>{@code prompts/list} — returns empty list (prompts not yet implemented)
 * </ul>
 *
 * <p>Note: Spring AI 2.x MCP server (which would provide full SSE-based Streamable HTTP with
 * session management) is available only as {@code 2.0.0-SNAPSHOT} as of 2026-06-20 — not GA. This
 * manual implementation provides a production-grade synchronous request/response transport until
 * the Spring AI 2.x MCP server reaches GA status on Spring Boot 4. STDIO transport is deferred for
 * the same reason.
 *
 * <p>Deferred: SSE streaming (MCP 2024-11-05 §4.2), session management (MCP-Session-Id header),
 * server-initiated notifications. All are defined by the spec but require the Spring AI starter or
 * a full async framework.
 *
 * <p>Security: Requires {@code SCOPE_mcp.invoke}. Tool invocations additionally enforce their
 * secondary scope via {@link McpToolExecutor#TOOL_SCOPE}.
 */
@RestController
@RequestMapping("/mcp")
class McpHttpTransportController {

  private static final String JSONRPC = "2.0";
  private static final String PROTOCOL_VERSION = "2024-11-05";
  private static final String SCOPE_MCP_INVOKE = "SCOPE_mcp.invoke";

  private final McpToolRegistry toolRegistry;
  private final McpToolExecutor toolExecutor;
  private final McpResourceRegistry resourceRegistry;

  McpHttpTransportController(
      McpToolRegistry toolRegistry,
      McpToolExecutor toolExecutor,
      McpResourceRegistry resourceRegistry) {
    this.toolRegistry = toolRegistry;
    this.toolExecutor = toolExecutor;
    this.resourceRegistry = resourceRegistry;
  }

  // ---------------------------------------------------------------
  // GET /mcp — capability discovery (server info)
  // ---------------------------------------------------------------

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> discovery(
      @AuthenticationPrincipal Jwt jwt, Authentication auth) {
    requireScope(auth, SCOPE_MCP_INVOKE);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("serverInfo", Map.of("name", "studydeck-mcp", "version", "1.0"));
    result.put("protocolVersion", PROTOCOL_VERSION);
    result.put(
        "capabilities",
        Map.of(
            "tools", Map.of("listChanged", false),
            "resources", Map.of("listChanged", false),
            "prompts", Map.of("listChanged", false)));
    return ResponseEntity.ok(result);
  }

  // ---------------------------------------------------------------
  // POST /mcp — JSON-RPC 2.0 message handler
  // ---------------------------------------------------------------

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> handle(
      @RequestBody Map<String, Object> rpcRequest,
      @AuthenticationPrincipal Jwt jwt,
      Authentication auth) {

    requireScope(auth, SCOPE_MCP_INVOKE);

    Object id = rpcRequest.get("id");
    String method = stringOrNull(rpcRequest.get("method"));

    if (method == null) {
      return ResponseEntity.ok(errorResponse(id, -32600, "Invalid Request: missing method"));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> params =
        rpcRequest.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();

    try {
      Map<String, Object> resultPayload =
          switch (method) {
            case "initialize" -> handleInitialize(params);
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolsCall(params, jwt, auth);
            case "resources/list" -> handleResourcesList();
            case "resources/read" -> handleResourcesRead(params, jwt);
            case "prompts/list" -> handlePromptsList();
            default -> throw new RpcException(-32601, "Method not found: " + method);
          };
      return ResponseEntity.ok(successResponse(id, resultPayload));
    } catch (RpcException rpe) {
      return ResponseEntity.ok(errorResponse(id, rpe.code, rpe.getMessage()));
    } catch (AccessDeniedException ade) {
      return ResponseEntity.ok(errorResponse(id, -32603, "Forbidden: " + ade.getMessage()));
    } catch (McpToolException mte) {
      return ResponseEntity.ok(
          errorResponse(id, -32603, "[" + mte.getCode() + "] " + mte.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.ok(errorResponse(id, -32603, "Internal error: " + e.getMessage()));
    }
  }

  // ---------------------------------------------------------------
  // JSON-RPC method handlers
  // ---------------------------------------------------------------

  private Map<String, Object> handleInitialize(Map<String, Object> params) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("protocolVersion", PROTOCOL_VERSION);
    result.put("serverInfo", Map.of("name", "studydeck-mcp", "version", "1.0"));
    result.put(
        "capabilities",
        Map.of(
            "tools", Map.of("listChanged", false),
            "resources", Map.of("listChanged", false),
            "prompts", Map.of("listChanged", false)));
    return result;
  }

  private Map<String, Object> handleToolsList() {
    List<Map<String, Object>> tools =
        toolRegistry.all().stream()
            .map(
                t -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("name", t.name());
                  m.put("description", t.description());
                  m.put("inputSchema", t.inputSchema());
                  return m;
                })
            .toList();
    return Map.of("tools", tools);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> handleToolsCall(
      Map<String, Object> params, Jwt jwt, Authentication auth) {
    String toolName = stringOrNull(params.get("name"));
    if (toolName == null) {
      throw new RpcException(-32602, "Invalid params: missing name");
    }
    if (toolRegistry.find(toolName).isEmpty()) {
      throw new RpcException(-32602, "Unknown tool: " + toolName);
    }

    // Secondary scope check
    String secondaryScope = McpToolExecutor.TOOL_SCOPE.get(toolName);
    if (secondaryScope != null && !secondaryScope.isBlank()) {
      requireScope(auth, "SCOPE_" + secondaryScope);
    }

    Map<String, Object> args =
        params.get("arguments") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    OwnerId actorId = new OwnerId(UUID.fromString(jwt.getSubject()));

    Map<String, Object> content = toolExecutor.execute(toolName, args, actorId);

    // MCP tools/call response: {content: [{type: "text", text: "..."}]}
    String textContent = content.toString();
    return Map.of(
        "content", List.of(Map.of("type", "text", "text", textContent)), "result", content);
  }

  private Map<String, Object> handleResourcesList() {
    List<Map<String, Object>> resources =
        resourceRegistry.list().stream()
            .map(
                r -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("uri", r.uri());
                  m.put("name", r.name());
                  if (r.description() != null) m.put("description", r.description());
                  if (r.mimeType() != null) m.put("mimeType", r.mimeType());
                  return m;
                })
            .toList();
    return Map.of("resources", resources);
  }

  private Map<String, Object> handleResourcesRead(Map<String, Object> params, Jwt jwt) {
    String uri = stringOrNull(params.get("uri"));
    if (uri == null) {
      throw new RpcException(-32602, "Invalid params: missing uri");
    }
    OwnerId actorId = jwt != null ? new OwnerId(UUID.fromString(jwt.getSubject())) : null;
    var content = resourceRegistry.read(uri, actorId);
    if (content.isEmpty()) {
      throw new RpcException(-32602, "Resource not found: " + uri);
    }
    return Map.of("contents", List.of(content.get()));
  }

  private Map<String, Object> handlePromptsList() {
    return Map.of("prompts", List.of());
  }

  // ---------------------------------------------------------------
  // JSON-RPC response builders
  // ---------------------------------------------------------------

  private Map<String, Object> successResponse(Object id, Map<String, Object> result) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", JSONRPC);
    response.put("id", id);
    response.put("result", result);
    return response;
  }

  private Map<String, Object> errorResponse(Object id, int code, String message) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", JSONRPC);
    response.put("id", id);
    response.put("error", Map.of("code", code, "message", message));
    return response;
  }

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  private void requireScope(Authentication auth, String requiredAuthority) {
    if (auth == null) {
      throw new AccessDeniedException("Authentication required");
    }
    boolean hasScope =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(requiredAuthority::equals);
    if (!hasScope) {
      throw new AccessDeniedException("Missing required scope: " + requiredAuthority);
    }
  }

  private String stringOrNull(Object v) {
    return v != null ? v.toString() : null;
  }

  /** JSON-RPC 2.0 error container. */
  private static class RpcException extends RuntimeException {
    final int code;

    RpcException(int code, String message) {
      super(message);
      this.code = code;
    }
  }
}
