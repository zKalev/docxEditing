package com.example.docs.dto;

import java.time.Instant;

/** One entry of {@code GET /api/documents}. The id is the file name. */
public record DocumentInfo(String id, String title, long size, Instant lastModified) {
}
