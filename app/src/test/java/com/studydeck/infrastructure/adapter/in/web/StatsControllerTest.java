package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.port.in.GetUserStatsQuery;
import com.studydeck.domain.port.in.GetUserStatsQuery.UserStatsResult;
import com.studydeck.integration.AiTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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

/** Web-layer integration test for {@link StatsController}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class StatsControllerTest {

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

  @MockitoBean
  @Qualifier("getUserStatsQuery")
  GetUserStatsQuery getUserStats;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void getUserStats_returns200WithExpectedFields() throws Exception {
    when(getUserStats.execute(any()))
        .thenReturn(new UserStatsResult(5L, 10L, 3L, 7, 0.85, 50, 0.90, 10, "en", "UTC", null));

    mockMvc
        .perform(get("/v1/stats").with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueToday").value(5))
        .andExpect(jsonPath("$.newCards").value(10))
        .andExpect(jsonPath("$.reviewedToday").value(3))
        .andExpect(jsonPath("$.dayStreak").value(7))
        .andExpect(jsonPath("$.retention30d").value(0.85))
        .andExpect(jsonPath("$.dailyGoal").value(50))
        .andExpect(jsonPath("$.desiredRetention").value(0.90))
        .andExpect(jsonPath("$.newCardsPerDay").value(10))
        .andExpect(jsonPath("$.language").value("en"))
        .andExpect(jsonPath("$.timezone").value("UTC"));
  }

  @Test
  void getUserStats_withoutAuth_returns401() throws Exception {
    mockMvc.perform(get("/v1/stats")).andExpect(status().isUnauthorized());
  }

  @Test
  void getUserStats_withTzParam_returns200() throws Exception {
    when(getUserStats.execute(any()))
        .thenReturn(new UserStatsResult(0L, 0L, 0L, 0, null, 40, 0.90, 10, "en", "UTC", null));

    mockMvc
        .perform(
            get("/v1/stats")
                .param("tz", "America/Argentina/Buenos_Aires")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueToday").value(0))
        .andExpect(jsonPath("$.retention30d").doesNotExist());
  }

  @Test
  void getUserStats_withInvalidTz_defaultsToUtc_returns200() throws Exception {
    when(getUserStats.execute(any()))
        .thenReturn(new UserStatsResult(1L, 2L, 0L, 1, null, 40, 0.90, 10, "en", "UTC", null));

    mockMvc
        .perform(
            get("/v1/stats")
                .param("tz", "NotAValidZone")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueToday").value(1));
  }

  @Test
  void getUserStats_includesPreferenceFields() throws Exception {
    when(getUserStats.execute(any()))
        .thenReturn(
            new UserStatsResult(
                2L, 5L, 1L, 3, 0.78, 30, 0.85, 20, "es", "America/Sao_Paulo", null));

    mockMvc
        .perform(get("/v1/stats").with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.desiredRetention").value(0.85))
        .andExpect(jsonPath("$.newCardsPerDay").value(20))
        .andExpect(jsonPath("$.language").value("es"))
        .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
  }

  @Test
  void corsPreflight_fromAllowedSpaOrigin_isPermittedWithoutAuth() throws Exception {
    // The browser sends an unauthenticated OPTIONS preflight before the real cross-origin call.
    // It must succeed and echo the allowed origin — otherwise the SPA can load no data at all.
    mockMvc
        .perform(
            options("/v1/stats")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
  }

  @Test
  void corsPreflight_fromDisallowedOrigin_isRejected() throws Exception {
    mockMvc
        .perform(
            options("/v1/stats")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }
}
