package com.studydeck.cli.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutputPrinterTest {

  private StringWriter outWriter;
  private StringWriter errWriter;

  @BeforeEach
  void setUp() {
    outWriter = new StringWriter();
    errWriter = new StringWriter();
  }

  private OutputPrinter humanPrinter() {
    return new OutputPrinter(new PrintWriter(outWriter), new PrintWriter(errWriter), false);
  }

  private OutputPrinter jsonPrinter() {
    return new OutputPrinter(new PrintWriter(outWriter), new PrintWriter(errWriter), true);
  }

  private ObjectNode sampleJson() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("id", "deck-1");
    node.put("title", "Biology");
    return node;
  }

  // ── Human mode ──────────────────────────────────────────────────────────────

  @Test
  void shouldPrintHumanMessageInHumanMode() {
    OutputPrinter printer = humanPrinter();
    printer.printSuccess("Deck created: Biology (id: deck-1)", sampleJson());
    assertThat(outWriter.toString()).contains("Deck created: Biology");
    assertThat(outWriter.toString()).doesNotContain("\"id\"");
  }

  @Test
  void shouldPrintPlainSuccessMessage() {
    OutputPrinter printer = humanPrinter();
    printer.printSuccess("Logged out successfully");
    assertThat(outWriter.toString()).contains("Logged out successfully");
  }

  @Test
  void shouldPrintErrorToStderr() {
    OutputPrinter printer = humanPrinter();
    printer.printError("Connection refused");
    assertThat(errWriter.toString()).contains("Error: Connection refused");
    assertThat(outWriter.toString()).isEmpty();
  }

  // ── JSON mode ────────────────────────────────────────────────────────────────

  @Test
  void shouldPrintJsonBodyWhenJsonModeActive() {
    OutputPrinter printer = jsonPrinter();
    printer.printSuccess("Deck created", sampleJson());
    assertThat(outWriter.toString()).contains("\"id\"");
    assertThat(outWriter.toString()).contains("\"title\"");
    assertThat(outWriter.toString()).contains("Biology");
    assertThat(outWriter.toString()).doesNotContain("Deck created");
  }

  @Test
  void shouldFallbackToHumanMessageWhenJsonBodyIsNullInJsonMode() {
    OutputPrinter printer = jsonPrinter();
    printer.printSuccess("Operation completed", null);
    assertThat(outWriter.toString()).contains("Operation completed");
  }

  @Test
  void shouldPrintJsonDirectly() {
    OutputPrinter printer = humanPrinter();
    printer.printJson(sampleJson());
    assertThat(outWriter.toString()).contains("\"id\"").contains("Biology");
  }

  // ── isJsonMode ───────────────────────────────────────────────────────────────

  @Test
  void shouldReportJsonModeCorrectly() {
    assertThat(humanPrinter().isJsonMode()).isFalse();
    assertThat(jsonPrinter().isJsonMode()).isTrue();
  }
}
