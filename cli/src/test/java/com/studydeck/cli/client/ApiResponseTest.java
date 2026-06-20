package com.studydeck.cli.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void shouldBeSuccessFor200() {
    ApiResponse response = new ApiResponse(200, "{}", null);
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void shouldBeSuccessFor201() {
    ApiResponse response = new ApiResponse(201, "{}", null);
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void shouldBeSuccessFor204() {
    ApiResponse response = new ApiResponse(204, "", null);
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void shouldNotBeSuccessFor400() {
    ApiResponse response = new ApiResponse(400, "{}", null);
    assertThat(response.isSuccess()).isFalse();
  }

  @Test
  void shouldNotBeSuccessFor500() {
    ApiResponse response = new ApiResponse(500, "{}", null);
    assertThat(response.isSuccess()).isFalse();
  }

  @Test
  void shouldIdentify401AsUnauthorized() {
    ApiResponse response = new ApiResponse(401, "{}", null);
    assertThat(response.isUnauthorized()).isTrue();
    assertThat(response.isForbidden()).isFalse();
  }

  @Test
  void shouldIdentify403AsForbidden() {
    ApiResponse response = new ApiResponse(403, "{}", null);
    assertThat(response.isForbidden()).isTrue();
    assertThat(response.isUnauthorized()).isFalse();
  }

  @Test
  void shouldIdentify404AsNotFound() {
    ApiResponse response = new ApiResponse(404, "{}", null);
    assertThat(response.isNotFound()).isTrue();
  }

  @Test
  void shouldExtractDetailFromErrorBody() {
    ObjectNode json = mapper.createObjectNode();
    json.put("detail", "Deck not found");
    ApiResponse response = new ApiResponse(404, json.toString(), json);
    assertThat(response.errorMessage()).isEqualTo("Deck not found");
  }

  @Test
  void shouldExtractMessageFromErrorBodyWhenDetailMissing() {
    ObjectNode json = mapper.createObjectNode();
    json.put("message", "Validation failed");
    ApiResponse response = new ApiResponse(422, json.toString(), json);
    assertThat(response.errorMessage()).isEqualTo("Validation failed");
  }

  @Test
  void shouldExtractTitleFromErrorBodyWhenDetailAndMessageMissing() {
    ObjectNode json = mapper.createObjectNode();
    json.put("title", "Bad Request");
    ApiResponse response = new ApiResponse(400, json.toString(), json);
    assertThat(response.errorMessage()).isEqualTo("Bad Request");
  }

  @Test
  void shouldFallbackToRawBodyForErrorMessage() {
    ApiResponse response = new ApiResponse(500, "Internal Server Error", null);
    assertThat(response.errorMessage()).isEqualTo("Internal Server Error");
  }

  @Test
  void shouldReturnDefaultErrorMessageWhenBodyIsNull() {
    ApiResponse response = new ApiResponse(500, null, null);
    assertThat(response.errorMessage()).contains("500");
  }
}
