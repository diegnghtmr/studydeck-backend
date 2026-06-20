package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IngestJobTest {

  private final Instant now = Instant.now();

  @Test
  void create_statusIsPending() {
    var job =
        IngestJob.create(IngestJobId.generate(), DocumentId.generate(), OwnerId.generate(), now);
    assertThat(job.getStatus()).isEqualTo(IngestStatus.PENDING);
    assertThat(job.getStartedAt()).isNull();
    assertThat(job.getFinishedAt()).isNull();
  }

  @Test
  void markRunning_setsStatusAndStartedAt() {
    var job =
        IngestJob.create(IngestJobId.generate(), DocumentId.generate(), OwnerId.generate(), now);
    job.markRunning(now);
    assertThat(job.getStatus()).isEqualTo(IngestStatus.RUNNING);
    assertThat(job.getStartedAt()).isEqualTo(now);
  }

  @Test
  void markCompleted_setsChunksAndStatus() {
    var job =
        IngestJob.create(IngestJobId.generate(), DocumentId.generate(), OwnerId.generate(), now);
    job.markRunning(now);
    job.markCompleted(42, now);
    assertThat(job.getStatus()).isEqualTo(IngestStatus.COMPLETED);
    assertThat(job.getChunksProduced()).isEqualTo(42);
    assertThat(job.getFinishedAt()).isEqualTo(now);
  }

  @Test
  void markFailed_setsErrorAndStatus() {
    var job =
        IngestJob.create(IngestJobId.generate(), DocumentId.generate(), OwnerId.generate(), now);
    job.markRunning(now);
    job.markFailed("something went wrong", now);
    assertThat(job.getStatus()).isEqualTo(IngestStatus.FAILED);
    assertThat(job.getErrorMessage()).isEqualTo("something went wrong");
    assertThat(job.getFinishedAt()).isEqualTo(now);
  }
}
