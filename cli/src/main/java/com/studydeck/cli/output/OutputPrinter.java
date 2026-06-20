package com.studydeck.cli.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.PrintWriter;

/**
 * Handles CLI output formatting: either human-readable text or raw JSON (when --json is active).
 */
public class OutputPrinter {

  private final PrintWriter out;
  private final PrintWriter err;
  private final boolean jsonMode;
  private final ObjectMapper prettyMapper;

  public OutputPrinter(PrintWriter out, PrintWriter err, boolean jsonMode) {
    this.out = out;
    this.err = err;
    this.jsonMode = jsonMode;
    this.prettyMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Print a success message or JSON body. */
  public void printSuccess(String humanMessage, JsonNode jsonBody) {
    if (jsonMode && jsonBody != null) {
      out.println(prettyPrint(jsonBody));
    } else {
      out.println(humanMessage);
    }
    out.flush();
  }

  /** Print a plain text success message (no JSON body). */
  public void printSuccess(String humanMessage) {
    out.println(humanMessage);
    out.flush();
  }

  /** Print an error message to stderr. */
  public void printError(String message) {
    err.println("Error: " + message);
    err.flush();
  }

  /** Print raw JSON to stdout regardless of jsonMode. */
  public void printJson(JsonNode node) {
    out.println(prettyPrint(node));
    out.flush();
  }

  private String prettyPrint(JsonNode node) {
    try {
      return prettyMapper.writeValueAsString(node);
    } catch (Exception e) {
      return node.toString();
    }
  }

  public boolean isJsonMode() {
    return jsonMode;
  }
}
