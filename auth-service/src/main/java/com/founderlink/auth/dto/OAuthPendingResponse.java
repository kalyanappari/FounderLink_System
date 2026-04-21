package com.founderlink.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by POST /auth/oauth/google when the Google user is new
 * (no existing account). Status 202 Accepted.
 *
 * The frontend stores oauthToken + email + name in sessionStorage,
 * navigates to the role-picker page, and then POSTs to
 * /auth/oauth/google/complete with { oauthToken, role }.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthPendingResponse {

    /** Short-lived token (UUID) linking this pending registration to the verified Google identity. */
    private String oauthToken;

    /** Pre-filled from Google profile — shown on the role-picker page. */
    private String email;
    private String name;

    private String message;
}
