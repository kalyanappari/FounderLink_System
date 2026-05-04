package com.founderlink.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the google.oauth.* block from config-server.
 * The clientId is used to verify Google ID tokens server-side.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "google.oauth")
public class GoogleOAuthProperties {

    /**
     * The OAuth 2.0 Client ID from Google Cloud Console.
     * Injected via config-server: google.oauth.client-id: ${GOOGLE_CLIENT_ID}
     */
    private String clientId;
}
