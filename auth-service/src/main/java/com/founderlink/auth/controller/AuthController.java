package com.founderlink.auth.controller;

import com.founderlink.auth.config.RefreshTokenProperties;
import com.founderlink.auth.dto.*;
import com.founderlink.auth.exception.InvalidRefreshTokenException;
import com.founderlink.auth.service.AuthService;
import com.founderlink.auth.service.AuthSession;
import com.founderlink.auth.service.GoogleOAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and session management")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenProperties refreshTokenProperties;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns registration details.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed — invalid request body"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates a user and returns an authentication response.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User logged in successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed — invalid request body"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        var authSession = authService.login(request);
        addRefreshTokenCookie(response, authSession.refreshToken());
        return ResponseEntity.ok(authSession.authResponse());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh authentication token", description = "Refreshes the authentication token using a valid refresh token.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
        @ApiResponse(responseCode = "403", description = "Refresh token has been revoked")
    })
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(request);
        var authSession = authService.refresh(refreshToken);
        addRefreshTokenCookie(response, authSession.refreshToken());
        return ResponseEntity.ok(authSession.authResponse());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logs out the user and clears the refresh token cookie.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User logged out successfully")
    })
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = resolveRefreshToken(request);
            authService.logout(refreshToken);
        } catch (InvalidRefreshTokenException ex) {
            log.debug("Logout request completed without a valid refresh token");
        }

        clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        String cookieToken = extractRefreshTokenFromCookie(request);
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorizationHeader)) {
            if (authorizationHeader.startsWith("Bearer ")) {
                return authorizationHeader.substring(7).trim();
            }
            return authorizationHeader.trim();
        }

        throw new InvalidRefreshTokenException("Refresh token is missing");
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (refreshTokenProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.getCookieName(), refreshToken)
                .httpOnly(true)
                .secure(refreshTokenProperties.isCookieSecure())
                .path(refreshTokenProperties.getCookiePath())
                .maxAge(refreshTokenProperties.getExpiration())
                .sameSite(refreshTokenProperties.getCookieSameSite())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(refreshTokenProperties.isCookieSecure())
                .path(refreshTokenProperties.getCookiePath())
                .maxAge(0)
                .sameSite(refreshTokenProperties.getCookieSameSite())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a 6-digit PIN to the user's email for password reset.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "PIN sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid email format"),
        @ApiResponse(responseCode = "401", description = "Email not found")
    })
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with PIN", description = "Resets the user's password using the PIN sent to their email.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid PIN, expired PIN, or validation error"),
        @ApiResponse(responseCode = "401", description = "User not found")
    })
    public ResponseEntity<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordResponse response = authService.resetPassword(
                request.getEmail(),
                request.getPin(),
                request.getNewPassword()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with OTP",
               description = "Verifies a user's email address using the 6-digit OTP sent after registration.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email verified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used OTP")
    })
    public ResponseEntity<Map<String, String>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification OTP",
               description = "Generates and sends a fresh OTP to the user's email. "
                           + "Previous OTPs are invalidated.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP resent successfully"),
        @ApiResponse(responseCode = "400", description = "Email already verified or not found")
    })
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "A new verification OTP has been sent to your email."));
    }

    // ──────────────────────────────────────────
    // Google OAuth
    // ──────────────────────────────────────────

    /**
     * Step 1 — POST /auth/oauth/google
     *
     * Verifies the Google ID token.
     * • Existing user  → HTTP 200 + full AuthResponse (access token in body, refresh in cookie)
     * • New user       → HTTP 202 + OAuthPendingResponse (oauthToken, email, name)
     *                    Frontend navigates to /auth/oauth/role-picker
     */
    @PostMapping("/oauth/google")
    @Operation(summary = "Sign in with Google",
               description = "Verifies a Google ID Token. Returns a JWT for existing users (200) "
                           + "or an OAuth pending token for new users (202).")
    public ResponseEntity<?> googleOAuth(
            @Valid @RequestBody OAuthGoogleRequest request,
            HttpServletResponse response) {

        Object result = googleOAuthService.handleGoogleLogin(request.getIdToken());

        if (result instanceof AuthSession session) {
            addRefreshTokenCookie(response, session.refreshToken());
            return ResponseEntity.ok(session.authResponse());
        } else {
            // New user — needs role selection
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
    }

    /**
     * Step 2 — POST /auth/oauth/google/complete
     *
     * Called from the role-picker page after a new Google user chooses their role.
     * Completes registration and returns a full JWT.
     */
    @PostMapping("/oauth/google/complete")
    @Operation(summary = "Complete Google OAuth registration",
               description = "Creates the user account with the selected role and returns a JWT.")
    public ResponseEntity<AuthResponse> googleOAuthComplete(
            @Valid @RequestBody OAuthRoleRequest request,
            HttpServletResponse response) {

        AuthSession session = googleOAuthService.completeGoogleRegistration(
                request.getOauthToken(), request.getRole());
        addRefreshTokenCookie(response, session.refreshToken());
        return ResponseEntity.ok(session.authResponse());
    }
}
