package com.studydeck.infrastructure.adapter.in.web.dto;

import com.studydeck.domain.port.in.GetPreviewIntervalsQuery;
import java.util.UUID;

/** Response for GET /v1/review-sessions/{id}/next — mirrors the OpenAPI NextReviewCard schema. */
public record NextReviewCardResponse(
    UUID sessionId,
    CardResponse card,
    GetPreviewIntervalsQuery.PreviewIntervals previewIntervals) {}
