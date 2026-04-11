package com.founderlink.auth.security;

import com.founderlink.auth.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtProperties jwtProperties;

    private Clock clock;
    private JwtService jwtService;

    // A valid 256-bit secret for testing (Base64 encoded
    // "extremely-secure-secret-key-that-is-long-enough")
    private static final String BASE64_SECRET = "ZXh0cmVtZWx5LXNlY3VyZS1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtYmFzZTY0";
    private static final String PLAIN_SECRET = "extremely-secure-secret-key-that-is-long-enough-plain-text";

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-18T10:15:30Z"), ZoneOffset.UTC);
        jwtService = new JwtService(jwtProperties, clock);
    }

    @Test
    void initShouldWorkWithBase64Secret() {
        when(jwtProperties.getSecret()).thenReturn(BASE64_SECRET);
        jwtService.init();
        // Should not throw
    }

    @Test
    void initShouldWorkWithPlainSecret() {
        when(jwtProperties.getSecret()).thenReturn(PLAIN_SECRET);
        jwtService.init();
        // Should not throw
    }

    @Test
    void initShouldThrowWhenSecretTooShort() {
        when(jwtProperties.getSecret()).thenReturn("short");
        assertThrows(IllegalArgumentException.class, () -> jwtService.init());
    }

    @Test
    void generateTokenAndValidateShouldWork() {
        when(jwtProperties.getSecret()).thenReturn(BASE64_SECRET);
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        jwtService.init();

        String token = jwtService.generateToken(123L, "FOUNDER");
        assertThat(token).isNotBlank();

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(123L);
        assertThat(jwtService.extractRole(token)).isEqualTo("FOUNDER");
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    void validateTokenShouldReturnFalseForExpiredToken() {
        when(jwtProperties.getSecret()).thenReturn(BASE64_SECRET);
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        jwtService.init();

        String token = jwtService.generateToken(123L, "FOUNDER");

        // Advance clock past expiration
        Clock futureClock = Clock.fixed(Instant.parse("2026-03-18T10:35:30Z"), ZoneOffset.UTC);
        JwtService futureService = new JwtService(jwtProperties, futureClock);
        futureService.init();

        assertThat(futureService.validateToken(token)).isFalse();
    }

    @Test
    void validateTokenShouldReturnFalseForMalformedToken() {
        when(jwtProperties.getSecret()).thenReturn(BASE64_SECRET);
        jwtService.init();

        assertThat(jwtService.validateToken("not.a.valid.token")).isFalse();
    }

    @Test
    void validateTokenShouldReturnFalseWhenRoleMissing() {
        when(jwtProperties.getSecret()).thenReturn(BASE64_SECRET);
        jwtService.init();

        String token = io.jsonwebtoken.Jwts.builder()
                .subject("123")
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(15))))
                .signWith(new javax.crypto.spec.SecretKeySpec(
                        "extremely-secure-secret-key-that-is-long-enough-plain-text".getBytes(), "HmacSHA256"))
                .compact();

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateTokenShouldReturnFalseWhenSubjectMissing() {
        when(jwtProperties.getSecret()).thenReturn(BASE64_SECRET);
        jwtService.init();

        String token = io.jsonwebtoken.Jwts.builder()
                .claim("role", "FOUNDER")
                .issuedAt(java.util.Date.from(clock.instant()))
                .expiration(java.util.Date.from(clock.instant().plus(Duration.ofMinutes(15))))
                .signWith(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode(BASE64_SECRET),
                        "HmacSHA256"))
                .compact();

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateTokenShouldReturnFalseWhenMalformed() {
        assertThat(jwtService.validateToken("not-a-valid-token")).isFalse();
    }
}
