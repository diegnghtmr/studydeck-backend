package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/**
 * REST response DTO for the machine-readable CLI catalog, matching the OpenAPI {@code
 * CliCommandCatalog} schema.
 */
public record CliCommandCatalogResponse(List<CliCommand> commands) {

  /**
   * A single CLI command descriptor.
   *
   * @param name command path (e.g. {@code deck create})
   * @param description what the command does
   * @param arguments accepted flags/parameters
   * @param examples sample invocations
   */
  public record CliCommand(
      String name, String description, List<String> arguments, List<String> examples) {}
}
