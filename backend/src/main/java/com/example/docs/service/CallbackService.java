package com.example.docs.service;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Handles Document Server callbacks. Status codes
 * (https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/):
 * 1 editing, 2 ready for saving (all editors closed), 3 saving error, 4 closed without changes,
 * 6 force save (Ctrl+S / Save button), 7 force save error.
 */
@Service
public class CallbackService {

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    private final DocumentStorageService storage;
    private final JwtService jwtService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CallbackService(DocumentStorageService storage, JwtService jwtService) {
        this.storage = storage;
        this.jwtService = jwtService;
    }

    /**
     * @param body          the raw JSON body Document Server posted
     * @param authorization the Authorization header, if any
     * @return the payload to act on (the verified JWT claims when JWT is enabled)
     * @throws SecurityException if JWT is enabled and the request carries no valid token
     */
    public Map<String, Object> authenticate(Map<String, Object> body, String authorization) {
        if (!jwtService.enabled()) {
            return body;
        }
        try {
            // Default Document Server setup puts the token in the body; the header variant wraps it in "payload".
            if (body.get("token") instanceof String bodyToken) {
                return jwtService.verify(bodyToken);
            }
            if (authorization != null && authorization.startsWith("Bearer ")) {
                Map<String, Object> claims = jwtService.verify(authorization.substring("Bearer ".length()));
                if (claims.get("payload") instanceof Map<?, ?> payload) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) payload;
                    return typed;
                }
                return claims;
            }
        } catch (JwtException e) {
            throw new SecurityException("Invalid callback token: " + e.getMessage(), e);
        }
        throw new SecurityException("Callback has no token");
    }

    /** @return true if the callback was handled; false makes the controller report an error to Document Server. */
    public boolean handle(String id, Map<String, Object> payload) {
        int status = payload.get("status") instanceof Number n ? n.intValue() : -1;
        log.info("Callback for {}: status={} key={}", id, status, payload.get("key"));

        return switch (status) {
            case 2, 6 -> save(id, (String) payload.get("url"));
            case 3, 7 -> {
                log.warn("Document Server reported a save error for {}: {}", id, payload);
                yield true;
            }
            default -> true; // 1 (editing), 4 (closed, no changes): nothing to do
        };
    }

    private boolean save(String id, String url) {
        if (url == null || url.isBlank()) {
            log.error("Save callback for {} has no download url", id);
            return false;
        }
        storage.resolve(id); // fail fast on unknown ids before downloading
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    log.error("Downloading edited {} from {} failed: HTTP {}", id, url, response.statusCode());
                    return false;
                }
                storage.overwrite(id, in);
            }
            log.info("Saved edited version of {}", id);
            return true;
        } catch (IOException e) {
            log.error("Saving {} from {} failed", id, url, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
