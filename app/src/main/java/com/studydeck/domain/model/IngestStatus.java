package com.studydeck.domain.model;

/** ETL ingest lifecycle for a {@link SourceDocument} or {@link IngestJob}. */
public enum IngestStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED
}
