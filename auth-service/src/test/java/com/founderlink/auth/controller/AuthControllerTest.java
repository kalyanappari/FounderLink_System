package com.founderlink.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.auth.config.RefreshTokenProperties;
import com.founderlink.auth.dto.*;
import com.founderlink.auth.entity.Role;
import com.founderlink.auth.exception.GlobalExceptionHandler;
import com.founderlink.auth.exception.EmailNotVerifiedException;
import com.founderlink.auth.exception.InvalidRefreshTokenException;
import com.founderlink.auth.service.AuthService;
import com.founderlink.auth.service.AuthSession;
import com.founderlink.auth.service.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private GoogleOAuthService googleOAuthService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RefreshTokenProperties props = new RefreshTokenProperties();
        props.setExpiration(Duration.ofDays(30));
        props.setCookieName("refresh_token");
        props.setCookiePath("/auth");
        props.setCookieSameSite("Strict");
        props.setCookieSecure(true);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, props, googleOAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void registerShouldReturnSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@test.com").password("password123")
                .name("New User").role(Role.FOUNDER).build();

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(RegisterResponse.builder()
                        .email("new@test.com").message("User registered successfully").build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@test.com"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void loginShouldReturnTokens() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "pass");
        AuthSession session = new AuthSession(AuthResponse.builder().token("at").build(), "rt");
        when(authService.login(any(LoginRequest.class))).thenReturn(session);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refresh_token", "rt"));
    }

    @Test
    void loginShouldReturn403WhenEmailNotVerified() throws Exception {
        LoginRequest request = new LoginRequest("unverified@test.com", "pass");
        when(authService.login(any())).thenThrow(
                new EmailNotVerifiedException("Please verify your email before logging in."));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // refresh
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void refreshShouldWorkWithAuthHeader() throws Exception {
        AuthSession session = new AuthSession(AuthResponse.builder().token("new-at").build(), "new-rt");
        when(authService.refresh("rt-from-header")).thenReturn(session);

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer rt-from-header"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refresh_token", "new-rt"));
    }

    @Test
    void refreshShouldWorkWithCookie() throws Exception {
        AuthSession session = new AuthSession(AuthResponse.builder().token("new-at").build(), "new-rt");
        when(authService.refresh("rt-from-cookie")).thenReturn(session);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "rt-from-cookie")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refresh_token", "new-rt"));
    }

    @Test
    void refreshShouldWorkWithAuthHeaderWithoutBearer() throws Exception {
        AuthSession session = new AuthSession(AuthResponse.builder().token("new-at").build(), "new-rt");
        when(authService.refresh("rt-without-prefix")).thenReturn(session);

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "rt-without-prefix"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshShouldThrowWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void logoutShouldBeNoContent() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "to-revoke")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(authService).logout("to-revoke");
    }

    @Test
    void logoutShouldWorkWhenNoTokenInCookieButInHeader() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer to-revoke"))
                .andExpect(status().isNoContent());

        verify(authService).logout("to-revoke");
    }

    @Test
    void logoutShouldProceedGracefullyWhenNoTokenPresent() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutShouldProceedWhenTokenIsInvalid() throws Exception {
        doThrow(new InvalidRefreshTokenException("invalid")).when(authService).logout(any());

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer invalid-rt"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void extractRefreshTokenFromCookieShouldReturnNullWhenMissing() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("wrong_cookie", "some-val")))
                .andExpect(status().isNoContent());

        verifyNoInteractions(authService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // forgot / reset password
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void forgotPasswordShouldReturnSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@test.com");
        when(authService.forgotPassword("user@test.com"))
                .thenReturn(new ForgotPasswordResponse("PIN sent"));

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordShouldReturnSuccess() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("user@test.com", "123456", "NewPassword123");
        when(authService.resetPassword("user@test.com", "123456", "NewPassword123"))
                .thenReturn(new ResetPasswordResponse("Password reset success"));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset success"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verify-email  (new)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void verifyEmailShouldReturn200OnSuccess() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("alice@test.com");
        request.setOtp("123456");
        doNothing().when(authService).verifyEmail("alice@test.com", "123456");

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resend-verification  (new)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resendVerificationShouldReturn200OnSuccess() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("alice@test.com");
        doNothing().when(authService).resendVerification("alice@test.com");

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A new verification OTP has been sent to your email."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Google OAuth  (new)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void googleOAuthShouldReturn200ForExistingUser() throws Exception {
        OAuthGoogleRequest request = new OAuthGoogleRequest();
        request.setIdToken("valid-google-id-token");
        AuthSession session = new AuthSession(
                AuthResponse.builder().token("jwt").email("g@gmail.com").role("FOUNDER").userId(1L).build(),
                "refresh-token");

        when(googleOAuthService.handleGoogleLogin("valid-google-id-token")).thenReturn(session);

        mockMvc.perform(post("/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(cookie().value("refresh_token", "refresh-token"));
    }

    @Test
    void googleOAuthShouldReturn202ForNewUser() throws Exception {
        OAuthGoogleRequest request = new OAuthGoogleRequest();
        request.setIdToken("new-user-id-token");
        OAuthPendingResponse pending = OAuthPendingResponse.builder()
                .oauthToken("oauth-tmp-token")
                .email("new@gmail.com")
                .name("New User")
                .message("Please select your role to complete registration.")
                .build();

        when(googleOAuthService.handleGoogleLogin("new-user-id-token")).thenReturn(pending);

        mockMvc.perform(post("/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.oauthToken").value("oauth-tmp-token"))
                .andExpect(jsonPath("$.email").value("new@gmail.com"));
    }

    @Test
    void googleOAuthCompleteShouldReturn200AndJwt() throws Exception {
        OAuthRoleRequest request = new OAuthRoleRequest();
        request.setOauthToken("oauth-tmp-token");
        request.setRole(com.founderlink.auth.entity.Role.FOUNDER);

        AuthSession session = new AuthSession(
                AuthResponse.builder().token("final-jwt").email("new@gmail.com").role("FOUNDER").userId(2L).build(),
                "refresh-token");

        when(googleOAuthService.completeGoogleRegistration("oauth-tmp-token", com.founderlink.auth.entity.Role.FOUNDER))
                .thenReturn(session);

        mockMvc.perform(post("/auth/oauth/google/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("final-jwt"))
                .andExpect(cookie().value("refresh_token", "refresh-token"));
    }
}
