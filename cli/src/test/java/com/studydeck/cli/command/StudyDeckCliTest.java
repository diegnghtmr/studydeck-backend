package com.studydeck.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.cli.StudyDeckCli;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Tests for CLI command parsing, help text, and version output.
 *
 * <p>Uses Picocli's CommandLine directly to verify command structure and exit codes without
 * spawning a subprocess.
 */
class StudyDeckCliTest {

  private CommandLine buildCli() {
    return new CommandLine(new StudyDeckCli());
  }

  // ── Help ────────────────────────────────────────────────────────────────────

  @Test
  void shouldReturnExitCode0ForHelp() {
    CommandLine cli = buildCli();
    int exitCode = cli.execute("--help");
    assertThat(exitCode).isZero();
  }

  @Test
  void shouldReturnExitCode0ForVersion() {
    CommandLine cli = buildCli();
    int exitCode = cli.execute("--version");
    assertThat(exitCode).isZero();
  }

  // ── Subcommand presence ──────────────────────────────────────────────────────

  @Test
  void shouldHaveAuthSubcommand() {
    CommandLine cli = buildCli();
    assertThat(cli.getSubcommands()).containsKey("auth");
  }

  @Test
  void shouldHaveDeckSubcommand() {
    CommandLine cli = buildCli();
    assertThat(cli.getSubcommands()).containsKey("deck");
  }

  @Test
  void shouldHaveNoteSubcommand() {
    CommandLine cli = buildCli();
    assertThat(cli.getSubcommands()).containsKey("note");
  }

  @Test
  void shouldHaveImportSubcommand() {
    CommandLine cli = buildCli();
    assertThat(cli.getSubcommands()).containsKey("import");
  }

  @Test
  void shouldHaveExportSubcommand() {
    CommandLine cli = buildCli();
    assertThat(cli.getSubcommands()).containsKey("export");
  }

  @Test
  void shouldHaveStudySubcommand() {
    CommandLine cli = buildCli();
    assertThat(cli.getSubcommands()).containsKey("study");
  }

  // ── Auth subcommands ─────────────────────────────────────────────────────────

  @Test
  void shouldHaveAuthLoginSubcommand() {
    CommandLine cli = buildCli();
    CommandLine authCmd = cli.getSubcommands().get("auth");
    assertThat(authCmd.getSubcommands()).containsKey("login");
  }

  @Test
  void shouldHaveAuthLogoutSubcommand() {
    CommandLine cli = buildCli();
    CommandLine authCmd = cli.getSubcommands().get("auth");
    assertThat(authCmd.getSubcommands()).containsKey("logout");
  }

  @Test
  void shouldHaveAuthWhoAmISubcommand() {
    CommandLine cli = buildCli();
    CommandLine authCmd = cli.getSubcommands().get("auth");
    assertThat(authCmd.getSubcommands()).containsKey("whoami");
  }

  // ── Deck subcommands ─────────────────────────────────────────────────────────

  @Test
  void shouldHaveDeckCreateSubcommand() {
    CommandLine cli = buildCli();
    CommandLine deckCmd = cli.getSubcommands().get("deck");
    assertThat(deckCmd.getSubcommands()).containsKey("create");
  }

  @Test
  void shouldHaveDeckLsSubcommand() {
    CommandLine cli = buildCli();
    CommandLine deckCmd = cli.getSubcommands().get("deck");
    assertThat(deckCmd.getSubcommands()).containsKey("ls");
  }

  // ── Study subcommands ─────────────────────────────────────────────────────────

  @Test
  void shouldHaveStudyNextSubcommand() {
    CommandLine cli = buildCli();
    CommandLine studyCmd = cli.getSubcommands().get("study");
    assertThat(studyCmd.getSubcommands()).containsKey("next");
  }

  @Test
  void shouldHaveStudyReviewSubcommand() {
    CommandLine cli = buildCli();
    CommandLine studyCmd = cli.getSubcommands().get("study");
    assertThat(studyCmd.getSubcommands()).containsKey("review");
  }

  // ── Import/Export subcommands ─────────────────────────────────────────────────

  @Test
  void shouldHaveImportJsonSubcommand() {
    CommandLine cli = buildCli();
    CommandLine importCmd = cli.getSubcommands().get("import");
    assertThat(importCmd.getSubcommands()).containsKey("json");
  }

  @Test
  void shouldHaveExportDeckSubcommand() {
    CommandLine cli = buildCli();
    CommandLine exportCmd = cli.getSubcommands().get("export");
    assertThat(exportCmd.getSubcommands()).containsKey("deck");
  }

  // ── Validation: missing required args ────────────────────────────────────────

  @Test
  void shouldReturnNonZeroExitCodeWhenDeckCreateMissingTitle() {
    CommandLine cli = buildCli();
    // Missing --title
    int exitCode = cli.execute("deck", "create");
    assertThat(exitCode).isNotZero();
  }

  @Test
  void shouldReturnNonZeroExitCodeWhenNoteCreateMissingDeck() {
    CommandLine cli = buildCli();
    int exitCode = cli.execute("note", "create", "--type", "basic");
    assertThat(exitCode).isNotZero();
  }

  @Test
  void shouldReturnNonZeroExitCodeWhenNoteCreateMissingType() {
    CommandLine cli = buildCli();
    int exitCode = cli.execute("note", "create", "--deck", "some-id");
    assertThat(exitCode).isNotZero();
  }
}
