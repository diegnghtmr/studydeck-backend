package com.studydeck.infrastructure.adapter.in.web;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the static OpenAPI contract in dev profile.
 *
 * <p>In production this controller is not loaded (@Profile("dev")), so Swagger UI and the raw YAML
 * are not exposed. A proper springdoc integration will replace this once springdoc-openapi releases
 * a Spring Boot 4-compatible version.
 */
@RestController
@Profile("dev")
public class OpenApiController {

  @GetMapping(value = "/v3/api-docs.yaml", produces = "application/vnd.oai.openapi")
  public ResponseEntity<Resource> openApiYaml() {
    Resource resource = new ClassPathResource("openapi.yaml");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/vnd.oai.openapi"))
        .body(resource);
  }
}
