package com.example.docs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The ONLYOFFICE editor configuration object, as passed to {@code new DocsAPI.DocEditor(id, config)}.
 * See https://api.onlyoffice.com/docs/docs-api/usage-api/config/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorConfig(Document document,
                           String documentType,
                           EditorSettings editorConfig,
                           String type,
                           String width,
                           String height,
                           String token) {

    public EditorConfig withToken(String newToken) {
        return new EditorConfig(document, documentType, editorConfig, type, width, height, newToken);
    }

    /**
     * @param key unique id of this document version; Document Server caches by it, so it must change
     *            whenever the stored file changes (max 128 chars, [0-9a-zA-Z.=_-]).
     * @param url where Document Server downloads the file from.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Document(String fileType, String key, String title, String url, Permissions permissions) {
    }

    public record Permissions(boolean edit, boolean download, boolean print, boolean comment, boolean review) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EditorSettings(String callbackUrl, String mode, String lang, User user, Customization customization) {
    }

    public record User(String id, String name) {
    }

    /** forcesave=true makes Ctrl+S / the Save button send a status 6 callback immediately. */
    public record Customization(boolean forcesave, boolean autosave) {
    }
}
