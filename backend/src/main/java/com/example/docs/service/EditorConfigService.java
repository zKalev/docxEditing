package com.example.docs.service;

import com.example.docs.config.OnlyOfficeProperties;
import com.example.docs.dto.EditorConfig;
import com.example.docs.dto.EditorConfigResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Service
public class EditorConfigService {

    private final DocumentStorageService storage;
    private final JwtService jwtService;
    private final OnlyOfficeProperties properties;
    private final JsonMapper jsonMapper;

    public EditorConfigService(DocumentStorageService storage, JwtService jwtService,
                               OnlyOfficeProperties properties, JsonMapper jsonMapper) {
        this.storage = storage;
        this.jwtService = jwtService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public EditorConfigResponse buildConfig(String id, String userId, String userName) {
        var info = storage.info(id);
        String base = properties.backendUrlForDocumentServer();

        // Both URLs are fetched by the Document Server container, not by the browser.
        String fileUrl = UriComponentsBuilder.fromUriString(base)
                .path("/api/documents/{id}/file").buildAndExpand(id).encode().toUriString();
        String callbackUrl = UriComponentsBuilder.fromUriString(base)
                .path("/api/documents/track").queryParam("id", "{id}").buildAndExpand(id).encode().toUriString();

        EditorConfig config = new EditorConfig(
                new EditorConfig.Document("docx", storage.documentKey(id), info.title(), fileUrl,
                        new EditorConfig.Permissions(true, true, true, true, true)),
                "word",
                new EditorConfig.EditorSettings(callbackUrl, "edit", "en",
                        new EditorConfig.User(userId, userName),
                        new EditorConfig.Customization(true, true)),
                "desktop",
                "100%",
                "100%",
                null);

        if (jwtService.enabled()) {
            // The token must carry the same config that is passed to the editor.
            Map<String, Object> claims = jsonMapper.convertValue(config, new TypeReference<>() {});
            config = config.withToken(jwtService.sign(claims));
        }
        return new EditorConfigResponse(properties.documentServerUrl(), config);
    }
}
