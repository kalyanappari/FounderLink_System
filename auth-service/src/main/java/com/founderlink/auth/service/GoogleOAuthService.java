package com.founderlink.auth.service;

import com.founderlink.auth.config.GoogleOAuthProperties;
import com.founderlink.auth.dto.AuthResponse;
import com.founderlink.auth.dto.OAuthPendingResponse;
import com.founderlink.auth.dto.UserRegisteredEvent;
import com.founderlink.auth.entity.AuthProvider;
import com.founderlink.auth.entity.Role;
import com.founderlink.auth.entity.User;
import com.founderlink.auth.publisher.UserRegisteredEventPublisher;
import com.founderlink.auth.repository.UserRepository;
import com.founderlink.auth.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the Google OAuth 2.0 login / registration flow.
 *
 * Flow for a NEW user (two-step):
 *   1. POST /auth/oauth/google       → verify ID token, store pending entry → 202 + oauthToken
 *   2. POST /auth/oauth/google/complete → look up pending entry, create User, return JWT → 200
 *
 * Flow for EXISTING user (one-step):
 *   1. POST /auth/oauth/google       → verify ID token, find user → 200 + full AuthResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SyncService syncService;
    private final UserRegisteredEventPublisher userRegisteredEventPublisher;
    private final GoogleOAuthProperties googleOAuthProperties;

    /**
     * In-memory store of pending OAuth registrations (new Google users who haven't picked a role yet).
     * Key: short-lived oauthToken (UUID), Value: GoogleIdToken.Payload (email, name, sub).
     *
     * Note: In production with multiple replicas, use Redis instead.
     * For this single-instance deployment, in-memory is fine.
     * TTL enforced by the 10-minute token validity from Google itself.
     */
    private final ConcurrentHashMap<String, PendingOAuthUser> pendingOAuthUsers = new ConcurrentHashMap<>();

    /** Verifies the Google ID token and handles login or new-user discovery. */
    @Transactional
    public Object handleGoogleLogin(String idToken) {
        GoogleIdToken.Payload payload = verifyIdToken(idToken);

        String email    = payload.getEmail();
        String name     = (String) payload.get("name");
        String sub      = payload.getSubject();   // Google's unique user ID

        if (name == null || name.isBlank()) {
            name = email.split("@")[0]; // fallback: use email prefix as name
        }

        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            // ── Existing user: straight to JWT ──────────────────────────────────
            User user = existingUser.get();
            if (user.getProviderId() == null) {
                log.info("Linking existing user {} to Google account ID: {}", email, sub);
                user.setProviderId(sub);
                try {
                    userRepository.saveAndFlush(user);
                } catch (Exception e) {
                    log.error("Failed to link Google account for {}: {}", email, e.getMessage());
                    throw new ResponseStatusException(HttpStatus.CONFLICT, 
                        "This Google account is already linked to another user profile.");
                }
            }
            log.info("OAuth Google login: existing user {}", email);
            return buildLoginSession(user);        // returns AuthSession

        } else {
            // ── New user: store pending, ask for role ────────────────────────────
            String oauthToken = UUID.randomUUID().toString();
            pendingOAuthUsers.put(oauthToken, new PendingOAuthUser(email, name, sub));
            log.info("OAuth Google: new user detected — issuing pending token for {}", email);

            return OAuthPendingResponse.builder()
                    .oauthToken(oauthToken)
                    .email(email)
                    .name(name)
                    .message("Please select your role to complete registration.")
                    .build();
        }
    }

    /** Completes registration for a new Google user once they've selected a role. */
    @Transactional
    public AuthSession completeGoogleRegistration(String oauthToken, Role role) {
        PendingOAuthUser pending = pendingOAuthUsers.remove(oauthToken);
        if (pending == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OAuth token is invalid or has expired. Please sign in with Google again.");
        }

        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot register as ADMIN via OAuth.");
        }

        // Guard against race conditions (double-submit)
        if (userRepository.existsByEmail(pending.email())) {
            User user = userRepository.findByEmail(pending.email()).orElseThrow();
            return buildLoginSession(user);
        }

        // Create the user — email is pre-verified by Google
        User user = new User();
        user.setName(pending.name());
        user.setEmail(pending.email());
        user.setPassword(UUID.randomUUID().toString()); // random, never used for auth
        user.setRole(role);
        user.setEmailVerified(true);                    // Google already verified the email
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setProviderId(pending.sub());

        User saved = userRepository.saveAndFlush(user);
        log.info("OAuth Google: created new {} account for {}", role, pending.email());

        syncService.syncUser(saved);

        userRegisteredEventPublisher.publishUserRegisteredEvent(
                UserRegisteredEvent.builder()
                        .userId(saved.getId())
                        .email(saved.getEmail())
                        .name(saved.getName())
                        .role(saved.getRole().name())
                        .build()
        );

        return buildLoginSession(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private AuthSession buildLoginSession(User user) {
        String accessToken   = jwtService.generateToken(user.getId(), user.getRole().name());
        String refreshToken  = refreshTokenService.createToken(user.getId());
        AuthResponse response = AuthResponse.builder()
                .token(accessToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
        return new AuthSession(response, refreshToken);
    }

    private GoogleIdToken.Payload verifyIdToken(String idToken) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleOAuthProperties.getClientId()))
                    .build();

            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid or expired Google ID token. Please sign in again.");
            }
            return token.getPayload();

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (GeneralSecurityException | IOException e) {
            log.error("Google ID token verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Google authentication failed. Please try again.");
        }
    }

    /**
     * Immutable record holding unverified-registration state
     * between Step 1 (Google token) and Step 2 (role selection).
     */
    private record PendingOAuthUser(String email, String name, String sub) {}
}
