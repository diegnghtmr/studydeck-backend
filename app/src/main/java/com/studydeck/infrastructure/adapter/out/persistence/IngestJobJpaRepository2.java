package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link IngestJobJpaEntity2}. */
interface IngestJobJpaRepository2 extends JpaRepository<IngestJobJpaEntity2, UUID> {}
