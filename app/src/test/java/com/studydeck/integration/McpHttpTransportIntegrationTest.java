package com.studydeck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 * Integration tests for the MCP Streamable HTTP transport at {@code /mcp}.
 *
 * <p>Test plan:
 *
 * <ol>
 *   <li>GET /mcp — returns server info and capabilities JSON.
 *   <li>POST /mcp with initialize method — returns protocol handshake response.
 *   <li>POST /mcp with tools/list — returns all 8 tools.
 *   <li>POST /mcp with tools/call deck_create — creates a deck and returns content.
 *   <li>POST /mcp with resources/list — returns resources.
 *   <li>POST /mcp with resources/read note-types — returns note type descriptors.
 *   <li>POST /mcp with resources/read json-schema — returns JSON Schema text.
 *   <li>POST /mcp with unknown method — JSON-RPC error -32601.
 *   <li>POST /mcp unauthenticated — 401.
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class McpHttpTransportIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_mcp_transport_test")
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

  MockMvc mockMvc;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  // ---------------------------------------------------------------
  // Test 1: GET /mcp — discovery
  // ---------------------------------------------------------------

  @Test
  @DisplayName("GET /mcp returns server info and capabilities")
  void getDiscovery_returnsServerInfoAndCapabilities() throws Exception {
    mockMvc
        .perform(
            get("/mcp")
                .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serverInfo.name").value("studydeck-mcp"))
        .andExpect(jsonPath("$.protocolVersion").value("2024-11-05"))
        .andExpect(jsonPath("$.capabilities.tools").exists());
  }

  // ---------------------------------------------------------------
  // Test 2: initialize
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp initialize returns protocol handshake")
  void postInitialize_returnsHandshake() throws Exception {
    Map<String, Object> request =
        Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of());

    mockMvc
        .perform(
            post("/mcp")
                .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
        .andExpect(jsonPath("$.result.serverInfo.name").value("studydeck-mcp"));
  }

  // ---------------------------------------------------------------
  // Test 3: tools/list
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp tools/list returns all 8 tools")
  void postToolsList_returnsAllTools() throws Exception {
    Map<String, Object> request =
        Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list", "params", Map.of());

    MvcResult result =
        mockMvc
            .perform(
                post("/mcp")
                    .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.tools").isArray())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("result").path("tools").size()).isEqualTo(8);
  }

  // ---------------------------------------------------------------
  // Test 4: tools/call deck_create
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp tools/call deck_create creates a deck via JSON-RPC")
  void postToolsCall_deckCreate_createsRealDeck() throws Exception {
    Map<String, Object> request =
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            3,
            "method",
            "tools/call",
            "params",
            Map.of("name", "deck_create", "arguments", Map.of("title", "JSON-RPC Created Deck")));

    MvcResult result =
        mockMvc
            .perform(
                post("/mcp")
                    .with(
                        jwt()
                            .jwt(
                                b ->
                                    b.subject(userId.toString())
                                        .claim("scope", "mcp.invoke study.write")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("result").path("result").path("title").asText())
        .isEqualTo("JSON-RPC Created Deck");
    assertThat(body.path("result").path("result").path("id").asText()).isNotBlank();
    // No error
    assertThat(body.path("error").isMissingNode()).isTrue();
  }

  // ---------------------------------------------------------------
  // Test 5: resources/list
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp resources/list returns all 3 resources")
  void postResourcesList_returnsAllResources() throws Exception {
    Map<String, Object> request =
        Map.of("jsonrpc", "2.0", "id", 4, "method", "resources/list", "params", Map.of());

    MvcResult result =
        mockMvc
            .perform(
                post("/mcp")
                    .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("result").path("resources").size()).isEqualTo(3);
  }

  // ---------------------------------------------------------------
  // Test 6: resources/read note-types
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp resources/read note-types returns note type descriptors")
  void postResourcesRead_noteTypes_returnsDescriptors() throws Exception {
    Map<String, Object> request =
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            5,
            "method",
            "resources/read",
            "params",
            Map.of("uri", "studydeck://note-types"));

    MvcResult result =
        mockMvc
            .perform(
                post("/mcp")
                    .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("result").path("contents").size()).isGreaterThan(0);
    JsonNode firstContent = body.path("result").path("contents").get(0);
    assertThat(firstContent.path("uri").asText()).isEqualTo("studydeck://note-types");
    assertThat(firstContent.path("noteTypes").isArray()).isTrue();
    assertThat(firstContent.path("noteTypes").size()).isGreaterThan(0);
  }

  // ---------------------------------------------------------------
  // Test 7: resources/read json-schema
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp resources/read json-schema returns non-empty schema text")
  void postResourcesRead_jsonSchema_returnsSchema() throws Exception {
    Map<String, Object> request =
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            6,
            "method",
            "resources/read",
            "params",
            Map.of("uri", "studydeck://json-schema/current"));

    MvcResult result =
        mockMvc
            .perform(
                post("/mcp")
                    .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    String schemaText = body.path("result").path("contents").get(0).path("text").asText();
    assertThat(schemaText).isNotBlank();
    // Schema should be valid JSON (start with '{')
    assertThat(schemaText.trim()).startsWith("{");
  }

  // ---------------------------------------------------------------
  // Test 8: Unknown method
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp with unknown method returns JSON-RPC error -32601")
  void postUnknownMethod_returnsMethodNotFoundError() throws Exception {
    Map<String, Object> request =
        Map.of("jsonrpc", "2.0", "id", 7, "method", "unknown/method", "params", Map.of());

    mockMvc
        .perform(
            post("/mcp")
                .with(jwt().jwt(b -> b.subject(userId.toString()).claim("scope", "mcp.invoke")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error.code").value(-32601));
  }

  // ---------------------------------------------------------------
  // Test 9: Unauthenticated → 401
  // ---------------------------------------------------------------

  @Test
  @DisplayName("POST /mcp without authentication returns 401")
  void postMcpUnauthenticated_returns401() throws Exception {
    Map<String, Object> request =
        Map.of("jsonrpc", "2.0", "id", 8, "method", "tools/list", "params", Map.of());

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }
}
