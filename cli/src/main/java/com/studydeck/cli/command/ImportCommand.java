package com.studydeck.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studydeck.cli.auth.TokenNotFoundException;
import com.studydeck.cli.auth.TokenResolver;
import com.studydeck.cli.client.ApiClient;
import com.studydeck.cli.client.ApiException;
import com.studydeck.cli.client.ApiResponse;
import com.studydeck.cli.output.OutputPrinter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Import commands. */
@Command(
    name = "import",
    description = "Import commands",
    subcommands = {ImportCommand.JsonImportCommand.class},
    mixinStandardHelpOptions = true)
public class ImportCommand implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  // ── import json ────────────────────────────────────────────────────────────

  @Command(
      name = "json",
      description = "Import flashcards from a JSON file (POST /v1/imports/flashcards)",
      mixinStandardHelpOptions = true)
  static class JsonImportCommand implements Runnable {

    @Option(
        names = {"--file", "-f"},
        description = "Path to the JSON import file",
        required = true)
    File file;

    @Option(
        names = {"--deck"},
        description = "Target deck ID (UUID) — overrides deck specified in the JSON file")
    String deckId;

    @Option(names = "--json", description = "Output raw JSON", defaultValue = "false")
    boolean jsonOutput;

    @Option(names = "--base-url", defaultValue = "${STUDYDECK_BASE_URL:-http://localhost:8080}")
    String baseUrl;

    @Option(names = "--token", defaultValue = "${STUDYDECK_TOKEN:-}")
    String token;

    @Override
    public void run() {
      PrintWriter out = new PrintWriter(System.out, true);
      PrintWriter err = new PrintWriter(System.err, true);
      OutputPrinter printer = new OutputPrinter(out, err, jsonOutput);

      if (!file.exists() || !file.isFile()) {
        printer.printError("File not found: " + file.getAbsolutePath());
        System.exit(1);
        return;
      }

      String resolvedToken;
      try {
        resolvedToken = new TokenResolver().resolve(token);
      } catch (TokenNotFoundException e) {
        printer.printError(e.getMessage());
        System.exit(1);
        return;
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode importPayload;
      try {
        importPayload = mapper.readTree(file);
      } catch (IOException e) {
        printer.printError("Failed to parse JSON file: " + e.getMessage());
        System.exit(1);
        return;
      }

      // Optionally override deckId in the payload
      if (deckId != null && importPayload.isObject()) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) importPayload.get("deck"))
            .put("id", deckId);
      }

      ApiClient client = new ApiClient(baseUrl, resolvedToken);
      try {
        ApiResponse response = client.post("/v1/imports/flashcards", importPayload);
        if (response.isSuccess()) {
          if (jsonOutput) {
            printer.printJson(response.json());
          } else {
            String imported = DeckCommand.fieldText(response.json(), "importedNotes");
            String skipped = DeckCommand.fieldText(response.json(), "skippedNotes");
            out.println(
                "Import successful."
                    + (imported != null ? " Notes imported: " + imported : "")
                    + (skipped != null ? ", skipped: " + skipped : ""));
          }
        } else {
          printer.printError(
              "Import failed (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }
  }
}
