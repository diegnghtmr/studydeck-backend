package com.studydeck.infrastructure.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.integration.AiTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Web-layer integration test for {@link SkillsController}.
 *
 * <p>Verifies the skill artifacts are served per the openapi {@code SkillDocument} / {@code
 * CliCommandCatalog} schemas and that {@code study.read} scope is enforced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class SkillsControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_test")
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
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static RequestPostProcessor readJwt() {
    return jwt()
        .jwt(j -> j.subject("00000000-0000-0000-0000-000000000001"))
        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"));
  }

  @Test
  void agentSkill_returnsSkillDocument() throws Exception {
    mockMvc
        .perform(get("/v1/skills/agent").with(readJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("studydeck-agent"))
        .andExpect(jsonPath("$.version").value("1.0"))
        .andExpect(jsonPath("$.contentType").value("text/markdown"))
        .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("StudyDeck")));
  }

  @Test
  void cliSkill_returnsCommandCatalog() throws Exception {
    mockMvc
        .perform(get("/v1/skills/cli").with(readJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commands").isArray())
        .andExpect(jsonPath("$.commands[0].name").exists())
        .andExpect(jsonPath("$.commands[0].description").exists())
        .andExpect(
            jsonPath(
                "$.commands[?(@.name == 'deck create')].description",
                org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Create"))));
  }

  @Test
  void skills_withoutReadScope_returns403() throws Exception {
    mockMvc
        .perform(get("/v1/skills/agent").with(jwt().jwt(j -> j.subject("u"))))
        .andExpect(status().isForbidden());
  }
}
