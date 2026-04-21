package com.founderlink.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Step 1 — POST /auth/oauth/google
 *
 * The Angular frontend sends the Google ID Token (credential) obtained
 * from the Google Identity Services popup.
 * If the user has no account, the server returns HTTP 202 + a temporary
 * oauthToken so the frontend can proceed to the role-picker screen.
 * If the user already exists, the server returns HTTP 200 + a full AuthResponse.
 */
@Data
public class OAuthGoogleRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
