package com.studydeck.domain.port.out;

/**
 * Output port: trigger an asynchronous ETL ingest pipeline for a document.
 *
 * <p>The adapter implementation (EtlIngestService) is Spring {@code @Async} and returns
 * immediately. The caller (IngestDocumentUseCase) persists the IngestJob in PENDING state before
 * calling this port so that the job is queryable before ETL starts.
 */
public interface AsyncIngestPort {

  /**
   * Starts an async ingest job for the given document.
   *
   * @param jobId string form of the IngestJobId UUID
   * @param documentId string form of the DocumentId UUID
   * @param ownerId string form of the OwnerId UUID
   * @param targetChunkSize target character size per chunk
   * @param chunkOverlap character overlap between adjacent chunks
   */
  void startIngest(
      String jobId, String documentId, String ownerId, int targetChunkSize, int chunkOverlap);
}
