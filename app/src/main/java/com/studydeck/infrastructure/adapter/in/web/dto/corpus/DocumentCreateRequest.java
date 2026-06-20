package com.studydeck.infrastructure.adapter.in.web.dto.corpus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** REST request DTO for creating a source document. */
public record DocumentCreateRequest(
    @NotBlank @Size(min = 1, max = 255) String title,
    @NotBlank String sourceType,
    String mimeType,
    String originalFilename,
    String textContent,
    String externalUrl,
    Long sizeBytes,
    Map<String, Object> metadata) {}
