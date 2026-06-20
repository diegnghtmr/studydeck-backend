package com.studydeck.cli.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for the StudyDeck REST API.
 *
 * <p>Uses {@link HttpTransport} for HTTP execution so tests can provide an in-memory stub without
 * mocking sealed JDK classes. Does NOT depend on any backend domain classes — all communication is
 * over HTTP.
 */
public class ApiClient {

  private final HttpTransport transport;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String token;

  /** Production constructor — uses JDK HttpClient via {@link JdkHttpTransport}. */
  public ApiClient(String baseUrl, String token) {
    this(baseUrl, token, new JdkHttpTransport());
  }

  /** Package-visible constructor for testing with a custom transport. */
  ApiClient(String baseUrl, String token, HttpTransport transport) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.token = token;
    this.transport = transport;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  /** GET request returning the full response body as an ApiResponse. */
  public ApiResponse get(String path) throws ApiException {
    HttpRequest request = buildRequest(path).GET().build();
    return execute(request);
  }

  /** POST request with a JSON body serialized from the given object. */
  public ApiResponse post(String path, Object body) throws ApiException {
    try {
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          buildRequest(path)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      return execute(request);
    } catch (Exception e) {
      throw new ApiException("Failed to serialize request body: " + e.getMessage(), e);
    }
  }

  /** DELETE request. */
  public ApiResponse delete(String path) throws ApiException {
    HttpRequest request = buildRequest(path).DELETE().build();
    return execute(request);
  }

  private HttpRequest.Builder buildRequest(String path) {
    String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "studydeck-cli/0.1.0");
    if (token != null && !token.isBlank()) {
      builder.header("Authorization", "Bearer " + token);
    }
    return builder;
  }

  private ApiResponse execute(HttpRequest request) throws ApiException {
    try {
      HttpResponse<String> response = transport.send(request);
      int status = response.statusCode();
      String rawBody = response.body();
      JsonNode json = null;
      if (rawBody != null && !rawBody.isBlank()) {
        try {
          json = objectMapper.readTree(rawBody);
        } catch (Exception ignored) {
          // Body is not JSON — keep raw string only
        }
      }
      return new ApiResponse(status, rawBody, json);
    } catch (TransportException e) {
      throw new ApiException("HTTP request failed: " + e.getMessage(), e);
    }
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
