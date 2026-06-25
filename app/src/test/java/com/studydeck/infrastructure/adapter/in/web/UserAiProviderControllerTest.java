package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.UserAiProviderId;
import com.studydeck.domain.port.in.DeleteUserAiProviderUseCase;
import com.studydeck.domain.port.in.ListUserAiProvidersQuery;
import com.studydeck.domain.port.in.SaveUserAiProviderUseCase;
import com.studydeck.domain.port.out.CryptoPort.CryptoUnavailableException;
import com.studydeck.integration.AiTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Web-layer integration test for {@link UserAiProviderController}.
 *
 * <p>Verifies owner-scoping, write-only key (no apiKey in responses), activate path, 404 on
 * absent/cross-owner, CryptoUnavailable surfacing, and 400 on invalid payloads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class UserAiProviderControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_ai_provider_test")
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

  @MockitoBean
  @Qualifier("saveUserAiProviderUseCase")
  SaveUserAiProviderUseCase saveUserAiProviderUseCase;

  @MockitoBean
  @Qualifier("listUserAiProvidersQuery")
  ListUserAiProvidersQuery listUserAiProvidersQuery;

  @MockitoBean
  @Qualifier("deleteUserAiProviderUseCase")
  DeleteUserAiProviderUseCase deleteUserAiProviderUseCase;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PROVIDER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
    return jwt()
        .jwt(j -> j.subject(OWNER_ID.toString()))
        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"));
  }

  private SaveUserAiProviderUseCase.Result saveResult(boolean active) {
    return new SaveUserAiProviderUseCase.Result(
        new UserAiProviderId(PROVIDER_ID),
        "My OpenAI",
        "https://api.openai.com",
        "gpt-4o",
        "sk-t…st99",
        active,
        NOW,
        NOW);
  }

  // ---------------------------------------------------------------
  // POST /v1/account/ai-providers — create
  // ---------------------------------------------------------------

  @Test
  void create_validPayload_returns201WithMaskedResponse() throws Exception {
    when(saveUserAiProviderUseCase.save(any())).thenReturn(saveResult(false));

    String body =
        """
        {"label":"My OpenAI","baseUrl":"https://api.openai.com","model":"gpt-4o","apiKey":"sk-testkey99"}
        """;

    mockMvc
        .perform(
            post("/v1/account/ai-providers")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(PROVIDER_ID.toString()))
        .andExpect(jsonPath("$.label").value("My OpenAI"))
        .andExpect(jsonPath("$.keyHint").value("sk-t…st99"))
        .andExpect(jsonPath("$.active").value(false))
        // plaintext apiKey must NEVER appear in the response
        .andExpect(jsonPath("$.apiKey").doesNotExist());
  }

  @Test
  void create_missingApiKey_returns400() throws Exception {
    String body =
        """
        {"label":"My OpenAI","baseUrl":"https://api.openai.com","model":"gpt-4o"}
        """;

    mockMvc
        .perform(
            post("/v1/account/ai-providers")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verify(saveUserAiProviderUseCase, never()).save(any());
  }

  @Test
  void create_blankLabel_returns400() throws Exception {
    String body =
        """
        {"label":"","baseUrl":"https://api.openai.com","model":"gpt-4o","apiKey":"sk-testkey99"}
        """;

    mockMvc
        .perform(
            post("/v1/account/ai-providers")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_cryptoUnavailable_returns503() throws Exception {
    when(saveUserAiProviderUseCase.save(any()))
        .thenThrow(new CryptoUnavailableException("encryption not configured"));

    String body =
        """
        {"label":"My OpenAI","baseUrl":"https://api.openai.com","model":"gpt-4o","apiKey":"sk-testkey99"}
        """;

    mockMvc
        .perform(
            post("/v1/account/ai-providers")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void create_requiresStudyWriteScope() throws Exception {
    String body =
        """
        {"label":"My OpenAI","baseUrl":"https://api.openai.com","model":"gpt-4o","apiKey":"sk-testkey99"}
        """;

    mockMvc
        .perform(
            post("/v1/account/ai-providers")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_ai.generate")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------
  // GET /v1/account/ai-providers — list
  // ---------------------------------------------------------------

  @Test
  void list_returnsProvidersWithMaskedKeyHintOnly() throws Exception {
    var masked =
        new ListUserAiProvidersQuery.Masked(
            new UserAiProviderId(PROVIDER_ID),
            "My OpenAI",
            "https://api.openai.com",
            "gpt-4o",
            "sk-t…st99",
            true,
            NOW,
            NOW);
    when(listUserAiProvidersQuery.list(any())).thenReturn(List.of(masked));

    mockMvc
        .perform(get("/v1/account/ai-providers").with(writeJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(PROVIDER_ID.toString()))
        .andExpect(jsonPath("$[0].keyHint").value("sk-t…st99"))
        .andExpect(jsonPath("$[0].active").value(true))
        // apiKey must NEVER appear in any response
        .andExpect(jsonPath("$[0].apiKey").doesNotExist());
  }

  @Test
  void list_empty_returns200EmptyArray() throws Exception {
    when(listUserAiProvidersQuery.list(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/v1/account/ai-providers").with(writeJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  // ---------------------------------------------------------------
  // PATCH /v1/account/ai-providers/{id}/activate
  // ---------------------------------------------------------------

  @Test
  void activate_existingProvider_returns200WithActiveTrue() throws Exception {
    when(saveUserAiProviderUseCase.save(any())).thenReturn(saveResult(true));

    mockMvc
        .perform(
            patch("/v1/account/ai-providers/{id}/activate", PROVIDER_ID.toString())
                .with(writeJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.apiKey").doesNotExist());
  }

  @Test
  void activate_providerNotOwned_returns404() throws Exception {
    when(saveUserAiProviderUseCase.save(any()))
        .thenThrow(new NotFoundException("UserAiProvider", PROVIDER_ID.toString()));

    mockMvc
        .perform(
            patch("/v1/account/ai-providers/{id}/activate", PROVIDER_ID.toString())
                .with(writeJwt()))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------
  // DELETE /v1/account/ai-providers/{id}
  // ---------------------------------------------------------------

  @Test
  void delete_existingOwnedProvider_returns204() throws Exception {
    doNothing().when(deleteUserAiProviderUseCase).execute(any());

    mockMvc
        .perform(delete("/v1/account/ai-providers/{id}", PROVIDER_ID.toString()).with(writeJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void delete_providerNotFound_returns404() throws Exception {
    doThrow(new NotFoundException("UserAiProvider", PROVIDER_ID.toString()))
        .when(deleteUserAiProviderUseCase)
        .execute(any());

    mockMvc
        .perform(delete("/v1/account/ai-providers/{id}", PROVIDER_ID.toString()).with(writeJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void delete_crossOwnerProvider_returns404NotForbidden() throws Exception {
    // Cross-owner: service throws NotFoundException (IDOR guard — never 403)
    doThrow(new NotFoundException("UserAiProvider", PROVIDER_ID.toString()))
        .when(deleteUserAiProviderUseCase)
        .execute(any());

    mockMvc
        .perform(delete("/v1/account/ai-providers/{id}", PROVIDER_ID.toString()).with(writeJwt()))
        .andExpect(status().isNotFound());
  }
}
