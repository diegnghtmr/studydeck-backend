package com.studydeck.cli.command;

import com.studydeck.cli.auth.TokenNotFoundException;
import com.studydeck.cli.auth.TokenResolver;
import com.studydeck.cli.client.ApiClient;
import com.studydeck.cli.client.ApiException;
import com.studydeck.cli.client.ApiResponse;
import com.studydeck.cli.output.OutputPrinter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Export commands. */
@Command(
    name = "export",
    description = "Export commands",
    subcommands = {ExportCommand.DeckExportCommand.class},
    mixinStandardHelpOptions = true)
public class ExportCommand implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  // ── export deck ────────────────────────────────────────────────────────────

  @Command(
      name = "deck",
      description = "Export a deck to JSON (GET /v1/exports/decks/{id}.json)",
      mixinStandardHelpOptions = true)
  static class DeckExportCommand implements Runnable {

    @Parameters(index = "0", description = "Deck ID (UUID) to export")
    String deckId;

    @Option(
        names = {"--out", "-o"},
        description = "Output file path (defaults to stdout)")
    File outFile;

    @Option(
        names = "--json",
        description = "Output raw JSON (always true for this command; flag kept for consistency)",
        defaultValue = "true")
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

      String resolvedToken;
      try {
        resolvedToken = new TokenResolver().resolve(token);
      } catch (TokenNotFoundException e) {
        printer.printError(e.getMessage());
        System.exit(1);
        return;
      }

      ApiClient client = new ApiClient(baseUrl, resolvedToken);
      try {
        ApiResponse response = client.get("/v1/exports/decks/" + deckId + ".json");
        if (response.isSuccess()) {
          String content = response.rawBody();
          if (outFile != null) {
            try {
              Files.writeString(outFile.toPath(), content);
              out.println("Exported deck " + deckId + " to " + outFile.getAbsolutePath());
            } catch (IOException e) {
              printer.printError("Failed to write file: " + e.getMessage());
              System.exit(1);
            }
          } else {
            out.println(content);
          }
        } else if (response.isNotFound()) {
          printer.printError("Deck not found: " + deckId);
          System.exit(1);
        } else {
          printer.printError(
              "Export failed (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }
  }
}
