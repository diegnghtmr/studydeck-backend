package com.studydeck.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Jackson 3 ObjectMapper customization for Spring Boot 4.
 *
 * <p>Key settings applied via {@link JsonMapperBuilderCustomizer} to the auto-configured {@code
 * jacksonJsonMapper} bean:
 *
 * <ul>
 *   <li>Fail on unknown properties: enforces the {@code additionalProperties: false} contract from
 *       the OpenAPI spec, producing 400 Bad Request for unexpected fields.
 *   <li>ISO-8601 date/time serialization: Jackson 3.x serializes {@code java.time} types as
 *       ISO-8601 strings by default (no {@code WRITE_DATES_AS_TIMESTAMPS} feature needed — it was
 *       removed in Jackson 3).
 * </ul>
 *
 * <p>NoteType kebab-case serialization is handled by the {@link
 * com.studydeck.infrastructure.adapter.in.web.dto.NoteTypeValue} enum's {@code @JsonValue}.
 *
 * <p>We do NOT define a custom {@code ObjectMapper} bean to avoid conflicting with the
 * auto-configured {@code jacksonJsonMapper} bean. Instead, we customize the builder so only one
 * primary {@code tools.jackson.databind.ObjectMapper} bean exists in the context.
 */
@Configuration
public class JacksonConfiguration {

  @Bean
  JsonMapperBuilderCustomizer studyDeckJacksonCustomizer() {
    return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
