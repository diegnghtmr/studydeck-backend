package com.studydeck.infrastructure.adapter.in.web.mcp;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.infrastructure.adapter.in.web.mcp.McpToolExecutor.McpToolException;
import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpInvokeRequest;
import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpInvokeResponse;
import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpPromptDescriptor;
import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpResourceDescriptor;
import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpToolDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST management wrappers for MCP tools, resources, and prompts.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /v1/mcp/tools — list all registered tools with input schemas
 *   <li>POST /v1/mcp/tools/{toolName}:invoke — invoke a tool via REST
 *   <li>GET /v1/mcp/resources — list all registered resources
 *   <li>GET /v1/mcp/prompts — list all registered prompts (empty for now)
 * </ul>
 *
 * <p>Security: All endpoints require the {@code SCOPE_mcp.invoke} authority. Individual tool
 * invocations additionally require the tool-specific secondary scope (e.g. {@code
 * SCOPE_study.write} for {@code deck_create}). Access denied returns 403 Problem Detail.
 */
@RestController
@RequestMapping("/v1/mcp")
class McpManagementController {

  private static final String SCOPE_MCP_INVOKE = "SCOPE_mcp.invoke";
  private static final String PROBLEM_BASE = "https://studydeck.ai/errors";

  private final McpToolRegistry toolRegistry;
  private final McpToolExecutor toolExecutor;
  private final McpResourceRegistry resourceRegistry;

  McpManagementController(
      McpToolRegistry toolRegistry,
      McpToolExecutor toolExecutor,
      McpResourceRegistry resourceRegistry) {
    this.toolRegistry = toolRegistry;
    this.toolExecutor = toolExecutor;
    this.resourceRegistry = resourceRegistry;
  }

  // ---------------------------------------------------------------
  // GET /v1/mcp/tools
  // ---------------------------------------------------------------

  @GetMapping("/tools")
  ResponseEntity<Map<String, Object>> listTools(
      @AuthenticationPrincipal Jwt jwt, Authentication auth) {
    requireScope(auth, SCOPE_MCP_INVOKE);
    List<McpToolDescriptor> tools = toolRegistry.all();
    return ResponseEntity.ok(Map.of("items", tools));
  }

  // ---------------------------------------------------------------
  // POST /v1/mcp/tools/{toolName}:invoke
  // ---------------------------------------------------------------

  @PostMapping("/tools/{toolName}:invoke")
  ResponseEntity<McpInvokeResponse> invokeTool(
      @PathVariable String toolName,
      @RequestBody McpInvokeRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication auth) {

    // 1. mcp.invoke scope (global gate)
    requireScope(auth, SCOPE_MCP_INVOKE);

    // 2. Tool must exist
    if (toolRegistry.find(toolName).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(McpInvokeResponse.failure(toolName, "Tool not found: " + toolName));
    }

    // 3. Tool-specific secondary scope
    String secondaryScope = McpToolExecutor.TOOL_SCOPE.get(toolName);
    if (secondaryScope != null && !secondaryScope.isBlank()) {
      requireScope(auth, "SCOPE_" + secondaryScope);
    }

    // 4. Execute
    OwnerId actorId = new OwnerId(UUID.fromString(jwt.getSubject()));
    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();

    try {
      Map<String, Object> content = toolExecutor.execute(toolName, args, actorId);
      return ResponseEntity.ok(McpInvokeResponse.success(toolName, content));
    } catch (McpToolException mte) {
      return ResponseEntity.status(422)
          .body(McpInvokeResponse.failure(toolName, "[" + mte.getCode() + "] " + mte.getMessage()));
    }
  }

  // ---------------------------------------------------------------
  // GET /v1/mcp/resources
  // ---------------------------------------------------------------

  @GetMapping("/resources")
  ResponseEntity<Map<String, Object>> listResources(
      @AuthenticationPrincipal Jwt jwt, Authentication auth) {
    requireScope(auth, SCOPE_MCP_INVOKE);
    List<McpResourceDescriptor> resources = resourceRegistry.list();
    return ResponseEntity.ok(Map.of("items", resources));
  }

  // ---------------------------------------------------------------
  // GET /v1/mcp/prompts
  // ---------------------------------------------------------------

  @GetMapping("/prompts")
  ResponseEntity<Map<String, Object>> listPrompts(
      @AuthenticationPrincipal Jwt jwt, Authentication auth) {
    requireScope(auth, SCOPE_MCP_INVOKE);
    // Prompts are not implemented; return an empty list per spec.
    List<McpPromptDescriptor> prompts = List.of();
    return ResponseEntity.ok(Map.of("items", prompts));
  }

  // ---------------------------------------------------------------
  // Scope enforcement helper
  // ---------------------------------------------------------------

  private void requireScope(Authentication auth, String requiredAuthority) {
    if (auth == null) {
      throw new AccessDeniedException("Authentication required");
    }
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
    boolean hasScope =
        authorities.stream().anyMatch(a -> requiredAuthority.equals(a.getAuthority()));
    if (!hasScope) {
      throw new AccessDeniedException("Missing required scope: " + requiredAuthority);
    }
  }
}
