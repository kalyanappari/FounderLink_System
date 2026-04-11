package com.founderlink.gateway.service;

import com.founderlink.gateway.security.AuthenticatedUser;
import com.founderlink.gateway.security.Role;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "FounderLinkSecretKeyForJWTTokenGeneration2024SecureKey";

    private final JwtService jwtService = new JwtService(SECRET);
    private final SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));

    @Test
    void authenticatesValidToken() {
        String token = tokenBuilder()
                .subject("user-123")
                .claim("role", "FOUNDER")
                .compact();

        AuthenticatedUser user = jwtService.authenticate(token);

        assertThat(user.userId()).isEqualTo("user-123");
        assertThat(user.role()).isEqualTo(Role.FOUNDER);
    }

    @Test
    void rejectsMissingToken() {
        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate("   ")
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Missing bearer token");
    }

    @Test
    void rejectsInvalidToken() {
        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate("not-a-jwt")
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Malformed token");
    }

    @Test
    void rejectsExpiredToken() {
        String token = tokenBuilder()
                .subject("user-123")
                .claim("role", "FOUNDER")
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token has expired");
    }

    @Test
    void rejectsTokenWithoutSubject() {
        String token = tokenBuilder()
                .claim("role", "FOUNDER")
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token subject is missing");
    }

    @Test
    void rejectsTokenWithoutRole() {
        String token = tokenBuilder()
                .subject("user-123")
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token role is missing");
    }

    @Test
    void authenticatesTokenWithMultipleRolesCollectionTakingFirst() {
        String token = tokenBuilder()
                .subject("user-123")
                .claim("role", List.of("FOUNDER", "ADMIN"))
                .compact();

        // Service picks first role from collection
        AuthenticatedUser user = jwtService.authenticate(token);
        assertThat(user.userId()).isEqualTo("user-123");
        assertThat(user.role()).isEqualTo(Role.FOUNDER);
    }

    @Test
    void rejectsTokenWithEmptyRoleCollection() {
        String token = tokenBuilder()
                .subject("user-123")
                .claim("role", List.of())
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token role collection is empty");
    }

    @Test
    void rejectsTokenWithCommaSeparatedRoleClaim() {
        String token = tokenBuilder()
                .subject("user-123")
                .claim("role", "FOUNDER,ADMIN")
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token must contain exactly one role");
    }

    @Test
    void rejectsTokenWithInvalidRole() {
        String token = tokenBuilder()
                .subject("user-123")
                .claim("role", "SUPER_ADMIN")
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token role is invalid: SUPER_ADMIN");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        // Build token with a different 32-byte secret
        String otherSecret = "DifferentSecretKeyFor256BitMinimumPaddingToBeValid"; // 50 chars
        SecretKey otherKey = Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-123")
                .claim("role", "FOUNDER")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Signature mismatch maps to either specific or broad handler depending on JJWT version
        assertThat(exception.getReason()).isIn("Invalid token signature", "Invalid or expired token");
    }

    @Test
    void rejectsConstructionWhenSecretIsTooShort() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService("tooshort")
        );
    }

    @Test
    void constructsWithNonBase64RawBytesSecret() {
        // "not!base64==encoded$%" is not valid Base64 → triggers catch(IllegalArgumentException)
        // and falls back to raw UTF-8 bytes (must be >= 32 bytes)
        String rawSecret = "This_Is_A_Raw_UTF8_Secret_Not_Base64_At_All_123";
        JwtService serviceWithRawSecret = new JwtService(rawSecret);

        // Should be able to build and authenticate a token signed with the same raw bytes
        SecretKey rawKey = Keys.hmacShaKeyFor(rawSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-raw")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(rawKey)
                .compact();

        AuthenticatedUser user = serviceWithRawSecret.authenticate(token);
        assertThat(user.userId()).isEqualTo("user-raw");
        assertThat(user.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsTokenWhereRoleClaimIsBlankAfterTrim() {
        // Encode a payload with role=" " by building a JWT using the secret key directly
        // and injecting a whitespace-only role claim
        String token = tokenBuilder()
                .subject("user-whitespace")
                .claim("role", "   ")  // whitespace-only role string
                .compact();

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> jwtService.authenticate(token)
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getReason()).isEqualTo("Token role is empty");
    }

    @Test
    void authenticatesTokenWithNoExpirationClaim() {
        // Build a token with NO expiration → parseClaims hits expiration == null branch
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String token = Jwts.builder()
                .subject("user-noexp")
                .claim("role", "INVESTOR")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                // No .expiration() call → expiration is null in claims
                .signWith(key)
                .compact();

        AuthenticatedUser user = jwtService.authenticate(token);
        assertThat(user.userId()).isEqualTo("user-noexp");
        assertThat(user.role()).isEqualTo(Role.INVESTOR);
    }

    private JwtBuilder tokenBuilder() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(secretKey);
    }
}
