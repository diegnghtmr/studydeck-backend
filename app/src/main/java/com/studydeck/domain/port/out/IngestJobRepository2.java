package com.studydeck.domain.port.out;

import com.studydeck.domain.model.IngestJob;
import com.studydeck.domain.model.IngestJobId;
import java.util.Optional;

/**
 * Output port for persisting and querying RAG {@link IngestJob} entities.
 *
 * <p>Named {@code IngestJobRepository2} to avoid collision with the existing {@code
 * ImportJobRepository} (which tracks flashcard import jobs).
 */
public interface IngestJobRepository2 {

  void save(IngestJob job);

  Optional<IngestJob> findById(IngestJobId id);
}
