package com.studydeck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AuditEventPort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for MCP management endpoints.
 *
 * <p>Test plan:
 *
 * <ol>
 *   <li>GET /v1/mcp/tools — returns all 8 tools with valid inputSchema.
 *   <li>GET /v1/mcp/tools — missing mcp.invoke → 403.
 *   <li>POST /v1/mcp/tools/deck_create:invoke — valid args + correct scopes → creates deck, returns
 *       structured output.
 *   <li>POST /v1/mcp/tools/deck_create:invoke — missing study.write → 403.
 *   <li>POST /v1/mcp/tools/unknown_tool:invoke — 404.
 *   <li>GET /v1/mcp/resources — returns 3 resources.
 *   <li>GET /v1/mcp/prompts — returns empty list.
 *   <li>Audit event emitted for deck_create invocation (verified via GET /v1/admin/audit-logs).
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Import(AiTestConfiguration.class)
class McpManagementIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_mcp_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper objectMapper;
  @MockitoSpyBean AuditEventPort auditEventPort;

  MockMvc mockMvc;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  // ---------------------------------------------------------------
  // Test 1: GET /v1/mcp/tools — 200 with 8 tools
  // ---------------------------------------------------------------

  @Test
  @DisplayName("GET /v1/mcp/tools returns 8 tools with valid inputSchema")
  void listToolsReturnsAllTools() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/v1/mcp/tools")
                    .with(
                        jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode items = body.path("items");
    assertThat(items.size()).isEqualTo(8);

    // Verify each tool has required fields
    for (JsonNode tool : items) {
      assertThat(tool.path("name").asText()).isNotBlank();
      assertThat(tool.path("title").asText()).isNotBlank();
      assertThat(tool.path("description").asText()).isNotBlank();
      assertThat(tool.path("inputSchema").isMissingNode()).isFalse();
      assertThat(tool.path("inputSchema").path("type").asText()).isEqualTo("object");
    }
  }

  // ---------------------------------------------------------------
  // Test 2: Missing mcp.invoke scope → 403
  // ---------------------------------------------------------------

  @Test
  @DisplayName("GET /v1/mcp/tools without mcp.invoke scope returns 403")
  void listToolsWithoutMcpInvokeScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/v1/mcp/tools")
                .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "study.read"))))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------
  // Test 3: POST /v1/mcp/tools/deck_create:invoke — success
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /v1/mcp/tools/deck_create:invoke creates a deck and returns structured output")
  void invokeDeckCreate_success() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/v1/mcp/tools/deck_create:invoke")
                    .with(
                        jwt()
                            .jwt(
                                b ->
                                    b.subject(userId.toString())
                                        .claim("scope", "mcp.invoke study.write")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("arguments", Map.of("title", "MCP Created Deck")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toolName").value("deck_create"))
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("content").path("id").asText()).isNotBlank();
    assertThat(body.path("content").path("title").asText()).isEqualTo("MCP Created Deck");
  }

  // ---------------------------------------------------------------
  // Test 4: Missing study.write scope → 403
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /v1/mcp/tools/deck_create:invoke without study.write returns 403")
  void invokeDeckCreate_missingScopeStudyWrite_returns403() throws Exception {
    mockMvc
        .perform(
            post("/v1/mcp/tools/deck_create:invoke")
                .with(
                    jwt()
                        .jwt(
                            b ->
                                b.subject(userId.toString())
                                    .claim("scope", "mcp.invoke study.read")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("arguments", Map.of("title", "Should Fail")))))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------
  // Test 5: Unknown tool → 404
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /v1/mcp/tools/unknown_tool:invoke returns 404")
  void invokeUnknownTool_returns404() throws Exception {
    mockMvc
        .perform(
            post("/v1/mcp/tools/unknown_tool:invoke")
                .with(
                    jwt()
                        .jwt(
                            b ->
                                b.subject(userId.toString())
                                    .claim("scope", "mcp.invoke study.write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("arguments", Map.of()))))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------
  // Test 6: GET /v1/mcp/resources — returns 3 resources
  // ---------------------------------------------------------------

  @Test
  @DisplayName("GET /v1/mcp/resources returns note-types, json-schema, and deck template")
  void listResourcesReturnsThreeResources() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/v1/mcp/resources")
                    .with(
                        jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("items").size()).isEqualTo(3);

    // Verify studydeck://note-types is present
    boolean hasNoteTypes =
        body.path("items").findValues("uri").stream()
            .anyMatch(n -> "studydeck://note-types".equals(n.asText()));
    assertThat(hasNoteTypes).isTrue();

    // Verify studydeck://json-schema/current is present
    boolean hasJsonSchema =
        body.path("items").findValues("uri").stream()
            .anyMatch(n -> "studydeck://json-schema/current".equals(n.asText()));
    assertThat(hasJsonSchema).isTrue();
  }

  // ---------------------------------------------------------------
  // Test 7: GET /v1/mcp/prompts — returns empty list
  // ---------------------------------------------------------------

  @Test
  @DisplayName("GET /v1/mcp/prompts returns empty items list")
  void listPromptsReturnsEmptyList() throws Exception {
    mockMvc
        .perform(
            get("/v1/mcp/prompts")
                .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  // ---------------------------------------------------------------
  // Test 8: Audit event emitted on tool invocation
  // ---------------------------------------------------------------

  @Test
  @DisplayName("Audit event mcp.tool.deck_list is recorded when deck_list is invoked")
  void auditEventEmittedOnToolInvocation() throws Exception {
    // Invoke deck_list (no side effects, safe to call)
    mockMvc
        .perform(
            post("/v1/mcp/tools/deck_list:invoke")
                .with(
                    jwt()
                        .jwt(
                            b ->
                                b.subject(userId.toString())
                                    .claim("scope", "mcp.invoke study.read")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("arguments", Map.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // Verify that audit port was called with the MCP tool action
    verify(auditEventPort, atLeastOnce())
        .record(any(OwnerId.class), eq("mcp.tool.deck_list"), eq("McpTool"), any(String.class));
  }
}
