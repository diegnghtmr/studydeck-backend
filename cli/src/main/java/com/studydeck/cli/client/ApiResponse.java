package com.studydeck.cli.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Encapsulates an HTTP response from the StudyDeck API: status code, raw body, and parsed JSON body
 * (may be null if the response body is empty or not valid JSON).
 */
public record ApiResponse(int status, String rawBody, JsonNode json) {

  /** Returns true if the HTTP status is in the 2xx range. */
  public boolean isSuccess() {
    return status >= 200 && status < 300;
  }

  /** Returns true if the HTTP status is 404. */
  public boolean isNotFound() {
    return status == 404;
  }

  /** Returns true if the HTTP status is 401. */
  public boolean isUnauthorized() {
    return status == 401;
  }

  /** Returns true if the HTTP status is 403. */
  public boolean isForbidden() {
    return status == 403;
  }

  /** Extracts the error message from the response body if present. */
  public String errorMessage() {
    if (json != null) {
      JsonNode detail = json.get("detail");
      if (detail != null && !detail.isNull()) return detail.asText();
      JsonNode message = json.get("message");
      if (message != null && !message.isNull()) return message.asText();
      JsonNode title = json.get("title");
      if (title != null && !title.isNull()) return title.asText();
    }
    return rawBody != null ? rawBody : "Unknown error (HTTP " + status + ")";
  }
}
