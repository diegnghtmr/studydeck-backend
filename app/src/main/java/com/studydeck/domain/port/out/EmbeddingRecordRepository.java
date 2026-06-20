package com.studydeck.domain.port.out;

import com.studydeck.domain.model.EmbeddingRecord;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Output port for persisting and querying {@link EmbeddingRecord} metadata. */
public interface EmbeddingRecordRepository {

  void save(EmbeddingRecord record);

  List<EmbeddingRecord> findByOwner(OwnerId ownerId, int offset, int limit);

  long countByOwner(OwnerId ownerId);
}
