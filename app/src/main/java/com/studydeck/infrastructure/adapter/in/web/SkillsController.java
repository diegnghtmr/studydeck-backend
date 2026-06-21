package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.infrastructure.adapter.in.web.dto.CliCommandCatalogResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.SkillDocumentResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Serves the machine-readable skill artifacts that let AI agents and CLI clients self-describe the
 * StudyDeck API.
 *
 * <ul>
 *   <li>{@code GET /v1/skills/agent} — the agent SKILL.md (how to drive the API + MCP).
 *   <li>{@code GET /v1/skills/cli} — the CLI command catalog.
 * </ul>
 *
 * <p>Both require the {@code study.read} scope and match the openapi {@code SkillDocument} /{@code
 * CliCommandCatalog} schemas. The artifacts are static classpath resources baked into the image.
 */
@RestController
@RequestMapping("/v1/skills")
class SkillsController {

  private static final String SKILL_NAME = "studydeck-agent";
  private static final String SKILL_VERSION = "1.0";

  private final ObjectMapper objectMapper;

  SkillsController(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @GetMapping("/agent")
  @PreAuthorize("hasAuthority('SCOPE_study.read')")
  SkillDocumentResponse getAgentSkill() {
    String content = readResource("skill/agent/SKILL.md");
    return new SkillDocumentResponse(SKILL_NAME, SKILL_VERSION, "text/markdown", content);
  }

  @GetMapping("/cli")
  @PreAuthorize("hasAuthority('SCOPE_study.read')")
  CliCommandCatalogResponse getCliSkill() {
    String json = readResource("skill/cli/catalog.json");
    try {
      return objectMapper.readValue(json, CliCommandCatalogResponse.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse CLI catalog resource", e);
    }
  }

  private String readResource(String path) {
    var resource = new ClassPathResource(path);
    try (var in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Missing skill artifact resource: " + path, e);
    }
  }
}
