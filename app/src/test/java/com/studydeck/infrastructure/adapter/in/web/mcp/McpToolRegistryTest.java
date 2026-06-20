package com.studydeck.infrastructure.adapter.in.web.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpToolDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link McpToolRegistry}.
 *
 * <p>Verifies that all expected tools are registered with valid input schemas.
 */
class McpToolRegistryTest {

  private final McpToolRegistry registry = new McpToolRegistry();

  @Test
  @DisplayName("All 8 tools are registered")
  void allToolsRegistered() {
    List<McpToolDescriptor> tools = registry.all();
    assertThat(tools).hasSize(8);
    List<String> names = tools.stream().map(McpToolDescriptor::name).toList();
    assertThat(names)
        .containsExactly(
            "deck_list",
            "deck_create",
            "note_create",
            "import_json",
            "export_deck",
            "study_get_queue",
            "review_submit",
            "capabilities_get");
  }

  @Test
  @DisplayName("Each tool has a non-blank title, description, and non-null inputSchema")
  void toolsHaveValidMetadata() {
    for (McpToolDescriptor tool : registry.all()) {
      assertThat(tool.name()).as("name for %s", tool.name()).isNotBlank();
      assertThat(tool.title()).as("title for %s", tool.name()).isNotBlank();
      assertThat(tool.description()).as("description for %s", tool.name()).isNotBlank();
      assertThat(tool.inputSchema()).as("inputSchema for %s", tool.name()).isNotNull();
      assertThat(tool.inputSchema().get("type"))
          .as("inputSchema.type for %s", tool.name())
          .isEqualTo("object");
    }
  }

  @Test
  @DisplayName("deck_create has required=[title] in inputSchema")
  void deckCreateHasRequiredTitle() {
    McpToolDescriptor deckCreate = registry.find("deck_create").orElseThrow();
    Object required = deckCreate.inputSchema().get("required");
    assertThat(required).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> requiredList = (List<Object>) required;
    assertThat(requiredList).contains("title");
  }

  @Test
  @DisplayName("review_submit has required=[cardId, rating] in inputSchema")
  void reviewSubmitHasRequiredCardIdAndRating() {
    McpToolDescriptor reviewSubmit = registry.find("review_submit").orElseThrow();
    Object required = reviewSubmit.inputSchema().get("required");
    assertThat(required).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> requiredList = (List<Object>) required;
    assertThat(requiredList).containsExactlyInAnyOrder("cardId", "rating");
  }

  @Test
  @DisplayName("find() returns empty for unknown tool name")
  void findUnknownToolReturnsEmpty() {
    Optional<McpToolDescriptor> result = registry.find("nonexistent_tool");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("find() returns present for known tool name")
  void findKnownToolReturnsPresent() {
    Optional<McpToolDescriptor> result = registry.find("deck_list");
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("import_json tool has required=[schemaVersion, deck, notes]")
  void importJsonHasRequiredFields() {
    McpToolDescriptor importTool = registry.find("import_json").orElseThrow();
    Object required = importTool.inputSchema().get("required");
    assertThat(required).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> requiredList = (List<Object>) required;
    assertThat(requiredList).containsExactlyInAnyOrder("schemaVersion", "deck", "notes");
  }

  @Test
  @DisplayName("All tools have TOOL_SCOPE mapping")
  void allToolsHaveScopeMapping() {
    for (McpToolDescriptor tool : registry.all()) {
      assertThat(McpToolExecutor.TOOL_SCOPE)
          .as("TOOL_SCOPE must contain entry for tool '%s'", tool.name())
          .containsKey(tool.name());
    }
  }

  @Test
  @DisplayName("inputSchema properties are of type object (maps)")
  void inputSchemaPropertiesAreMap() {
    for (McpToolDescriptor tool : registry.all()) {
      Map<String, Object> schema = tool.inputSchema();
      if (schema.containsKey("properties")) {
        assertThat(schema.get("properties"))
            .as("properties for tool %s should be a Map", tool.name())
            .isInstanceOf(Map.class);
      }
    }
  }
}
