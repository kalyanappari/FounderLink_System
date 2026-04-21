package com.founderlink.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Step 2 — POST /auth/oauth/google/complete
 *
 * Sent from the role-picker page after a new Google user selects their role.
 * The oauthToken ties this request to the verified Google identity from Step 1
 * (avoids re-verifying the ID token a second time).
 */
@Data
public class OAuthRoleRequest {

    /** Short-lived token returned by /auth/oauth/google for new users. */
    @NotBlank(message = "OAuth token is required")
    private String oauthToken;

    /** The role the new user selected: FOUNDER | INVESTOR | COFOUNDER */
    @NotNull(message = "Role is required")
    private com.founderlink.auth.entity.Role role;
}
