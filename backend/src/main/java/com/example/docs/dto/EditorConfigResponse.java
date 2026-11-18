package com.example.docs.dto;

/** Response of {@code GET /api/documents/{id}/config}: where to load the editor from, and what to open. */
public record EditorConfigResponse(String documentServerUrl, EditorConfig config) {
}
