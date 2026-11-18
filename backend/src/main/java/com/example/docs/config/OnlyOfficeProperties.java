package com.example.docs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param documentServerUrl            Document Server URL as the browser reaches it.
 * @param backendUrlForDocumentServer  this backend's URL as the Document Server container reaches it
 *                                     (used for the file download URL and the callback URL).
 * @param jwtSecret                    secret shared with Document Server; blank disables JWT.
 */
@ConfigurationProperties(prefix = "onlyoffice")
public record OnlyOfficeProperties(String documentServerUrl,
                                   String backendUrlForDocumentServer,
                                   String jwtSecret) {

    public boolean jwtEnabled() {
        return jwtSecret != null && !jwtSecret.isBlank();
    }
}
