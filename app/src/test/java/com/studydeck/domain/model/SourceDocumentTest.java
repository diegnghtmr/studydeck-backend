package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceDocumentTest {

  private final OwnerId owner = OwnerId.generate();
  private final Instant now = Instant.now();

  @Test
  void create_setsStatusToPending() {
    var doc =
        SourceDocument.create(
            DocumentId.generate(),
            owner,
            "Test Doc",
            "pasted-text",
            null,
            null,
            "content",
            null,
            null,
            Map.of(),
            now);
    assertThat(doc.getIngestStatus()).isEqualTo(IngestStatus.PENDING);
  }

  @Test
  void create_requiresNonBlankTitle() {
    assertThatThrownBy(
            () ->
                SourceDocument.create(
                    DocumentId.generate(),
                    owner,
                    "",
                    "pasted-text",
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void markRunning_transitionsStatusToRunning() {
    var doc =
        SourceDocument.create(
            DocumentId.generate(),
            owner,
            "Doc",
            "pasted-text",
            null,
            null,
            "text",
            null,
            null,
            Map.of(),
            now);
    doc.markRunning(now);
    assertThat(doc.getIngestStatus()).isEqualTo(IngestStatus.RUNNING);
  }

  @Test
  void markCompleted_transitionsStatusToCompleted() {
    var doc =
        SourceDocument.create(
            DocumentId.generate(),
            owner,
            "Doc",
            "pasted-text",
            null,
            null,
            "text",
            null,
            null,
            Map.of(),
            now);
    doc.markCompleted(now);
    assertThat(doc.getIngestStatus()).isEqualTo(IngestStatus.COMPLETED);
  }

  @Test
  void markFailed_transitionsStatusToFailed() {
    var doc =
        SourceDocument.create(
            DocumentId.generate(),
            owner,
            "Doc",
            "pasted-text",
            null,
            null,
            "text",
            null,
            null,
            Map.of(),
            now);
    doc.markFailed(now);
    assertThat(doc.getIngestStatus()).isEqualTo(IngestStatus.FAILED);
  }
}
