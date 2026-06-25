package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SchedulerAlgorithm;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.domain.port.in.GetUserStatsQuery;
import com.studydeck.domain.port.in.ListSessionsQuery;
import com.studydeck.domain.port.in.LogoutAllSessionsUseCase;
import com.studydeck.domain.port.in.RevokeSessionUseCase;
import com.studydeck.domain.port.in.UpdateUserPreferencesUseCase;
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
 * Web-layer integration test for {@link AccountController}.
 *
 * <p>Uses a full Spring context with Testcontainers PostgreSQL. Input ports are replaced by Mockito
 * beans to isolate the web layer. JWT scope authorities are injected via the jwt() post-processor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class AccountControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_account_test")
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
  @Qualifier("exportAccountUseCase")
  ExportAccountUseCase exportAccount;

  @MockitoBean
  @Qualifier("deleteAccountUseCase")
  DeleteAccountUseCase deleteAccount;

  @MockitoBean
  @Qualifier("updateUserPreferencesUseCase")
  UpdateUserPreferencesUseCase updateUserPreferences;

  @MockitoBean
  @Qualifier("logoutAllSessionsUseCase")
  LogoutAllSessionsUseCase logoutAllSessions;

  @MockitoBean
  @Qualifier("getUserStatsQuery")
  GetUserStatsQuery getUserStats;

  @MockitoBean
  @Qualifier("listSessionsQuery")
  ListSessionsQuery listSessions;

  @MockitoBean
  @Qualifier("revokeSessionUseCase")
  RevokeSessionUseCase revokeSession;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void export_withExportReadScope_returns200WithAccountData() throws Exception {
    OwnerId ownerId = new OwnerId(OWNER_ID);
    UserAccount account = UserAccount.provision(ownerId, "user@example.com", "Test User");
    ExportAccountUseCase.Result result =
        new ExportAccountUseCase.Result(account, List.of(), List.of(), Instant.now());

    when(exportAccount.execute(any())).thenReturn(result);

    mockMvc
        .perform(
            get("/v1/account/export")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_export.read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.id").value(OWNER_ID.toString()))
        .andExpect(jsonPath("$.account.email").value("user@example.com"))
        .andExpect(jsonPath("$.decks").isArray())
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.exportedAt").isNotEmpty());
  }

  @Test
  void export_withoutExportReadScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/v1/account/export")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void delete_withStudyWriteScope_returns204() throws Exception {
    doNothing().when(deleteAccount).execute(any());

    mockMvc
        .perform(
            delete("/v1/account")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void delete_withoutStudyWriteScope_returns403() throws Exception {
    mockMvc
        .perform(
            delete("/v1/account")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void export_withoutAuth_returns401() throws Exception {
    mockMvc.perform(get("/v1/account/export")).andExpect(status().isUnauthorized());
  }

  @Test
  void delete_withoutAuth_returns401() throws Exception {
    mockMvc.perform(delete("/v1/account")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------
  // PATCH /v1/account/preferences
  // ---------------------------------------------------------------

  @Test
  void patchPreferences_withNewFields_returns204() throws Exception {
    doNothing().when(updateUserPreferences).execute(any());

    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dailyGoal": 30,
                      "desiredRetention": 0.85,
                      "newCardsPerDay": 20,
                      "language": "es",
                      "timezone": "America/New_York"
                    }
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void patchPreferences_partialPatch_onlyDailyGoal_returns204() throws Exception {
    doNothing().when(updateUserPreferences).execute(any());

    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dailyGoal": 50}
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void patchPreferences_invalidLanguage_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"language": "de"}
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchPreferences_retentionOutOfRange_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"desiredRetention": 1.0}
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchPreferences_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dailyGoal": 30}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void patchPreferences_withoutScope_returns403() throws Exception {
    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dailyGoal": 30}
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------
  // POST /v1/account/logout-all
  // ---------------------------------------------------------------

  @Test
  void logoutAll_withStudyWriteScope_returns204() throws Exception {
    doNothing().when(logoutAllSessions).execute(any());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/v1/account/logout-all")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void logoutAll_withoutStudyWriteScope_returns403() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/v1/account/logout-all")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void logoutAll_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/v1/account/logout-all"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void patchPreferences_withSchedulerAlgorithmSm2_returns204() throws Exception {
    doNothing().when(updateUserPreferences).execute(any());

    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"schedulerAlgorithm": "SM2"}
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void patchPreferences_withInvalidSchedulerAlgorithm_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/v1/account/preferences")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"schedulerAlgorithm": "INVALID"}
                    """)
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------
  // GET /v1/account/sessions
  // ---------------------------------------------------------------

  @Test
  void listSessions_withStudyWriteScope_returnsSessionList() throws Exception {
    IdpSession session =
        new IdpSession(
            "sess-abc",
            "192.168.1.1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T11:00:00Z"),
            java.util.List.of("StudyDeck Web"));

    when(listSessions.execute(any())).thenReturn(java.util.List.of(session));

    mockMvc
        .perform(
            get("/v1/account/sessions")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()).claim("sid", "other-sess"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("sess-abc"))
        .andExpect(jsonPath("$[0].ipAddress").value("192.168.1.1"))
        .andExpect(jsonPath("$[0].device").value("StudyDeck Web"))
        .andExpect(jsonPath("$[0].startedAt").isNotEmpty())
        .andExpect(jsonPath("$[0].lastAccessAt").isNotEmpty())
        .andExpect(jsonPath("$[0].current").value(false));
  }

  @Test
  void listSessions_marksCurrentSession_whenSidMatches() throws Exception {
    String currentSid = "sess-current";
    IdpSession session =
        new IdpSession(
            currentSid,
            "10.0.0.1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T11:00:00Z"),
            java.util.List.of());

    when(listSessions.execute(any())).thenReturn(java.util.List.of(session));

    mockMvc
        .perform(
            get("/v1/account/sessions")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()).claim("sid", currentSid))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(currentSid))
        .andExpect(jsonPath("$[0].current").value(true))
        .andExpect(jsonPath("$[0].device").value("Unknown"));
  }

  @Test
  void listSessions_withoutStudyWriteScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/v1/account/sessions")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void listSessions_withoutAuth_returns401() throws Exception {
    mockMvc.perform(get("/v1/account/sessions")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------
  // DELETE /v1/account/sessions/{sessionId}
  // ---------------------------------------------------------------

  @Test
  void revokeSession_withStudyWriteScope_returns204() throws Exception {
    doNothing().when(revokeSession).execute(any());

    mockMvc
        .perform(
            delete("/v1/account/sessions/sess-abc")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void revokeSession_withoutStudyWriteScope_returns403() throws Exception {
    mockMvc
        .perform(
            delete("/v1/account/sessions/sess-abc")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void revokeSession_withoutAuth_returns401() throws Exception {
    mockMvc.perform(delete("/v1/account/sessions/sess-abc")).andExpect(status().isUnauthorized());
  }

  @Test
  void getStats_returnsSchedulerAlgorithm() throws Exception {
    when(getUserStats.execute(any()))
        .thenReturn(
            new GetUserStatsQuery.UserStatsResult(
                5L, 3L, 2L, 1, 0.88, 40, 0.90, 10, "en", "UTC", SchedulerAlgorithm.SM2));

    mockMvc
        .perform(
            get("/v1/stats")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedulerAlgorithm").value("SM2"));
  }
}
