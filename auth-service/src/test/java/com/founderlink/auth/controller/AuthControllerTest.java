package com.founderlink.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.auth.config.RefreshTokenProperties;
import com.founderlink.auth.dto.*;
import com.founderlink.auth.exception.GlobalExceptionHandler;
import com.founderlink.auth.exception.InvalidRefreshTokenException;
import com.founderlink.auth.service.AuthService;
import com.founderlink.auth.service.AuthSession;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RefreshTokenProperties refreshTokenProperties = new RefreshTokenProperties();
        refreshTokenProperties.setExpiration(Duration.ofDays(30));
        refreshTokenProperties.setCookieName("refresh_token");
        refreshTokenProperties.setCookiePath("/auth");
        refreshTokenProperties.setCookieSameSite("Strict");
        refreshTokenProperties.setCookieSecure(true);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, refreshTokenProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerShouldReturnSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@test.com")
                .password("password123")
                .name("New User")
                .role(com.founderlink.auth.entity.Role.FOUNDER)
                .build();

        RegisterResponse response = RegisterResponse.builder()
                .email("new@test.com")
                .message("User registered successfully")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@test.com"));
    }

    @Test
    void loginShouldReturnTokens() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "pass");
        AuthResponse authResponse = AuthResponse.builder().token("at").build();
        AuthSession session = new AuthSession(authResponse, "rt");

        when(authService.login(any(LoginRequest.class))).thenReturn(session);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refresh_token", "rt"));
    }

    @Test
    void refreshShouldWorkWithAuthHeader() throws Exception {
        AuthResponse authResponse = AuthResponse.builder().token("new-at").build();
        when(authService.refresh("rt-from-header")).thenReturn(new AuthSession(authResponse, "new-rt"));

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer rt-from-header")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refresh_token", "new-rt"));
    }

    @Test
    void logoutShouldBeNoContent() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "to-revoke")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(authService).logout("to-revoke");
    }

    @Test
    void forgotPasswordShouldReturnSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@test.com");
        when(authService.forgotPassword("user@test.com")).thenReturn(new ForgotPasswordResponse("PIN sent"));

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshShouldWorkWithAuthHeaderWithoutBearer() throws Exception {
        AuthResponse authResponse = AuthResponse.builder().token("new-at").build();
        when(authService.refresh("rt-without-prefix")).thenReturn(new AuthSession(authResponse, "new-rt"));

        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "rt-without-prefix")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void refreshShouldThrowWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldWorkWhenNoTokenInCookieButInHeader() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer to-revoke"))
                .andExpect(status().isNoContent());

        verify(authService).logout("to-revoke");
    }

    @Test
    void resolveRefreshTokenShouldThrowWhenNoTokenPresent() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void extractRefreshTokenFromCookieShouldReturnNullWhenMissing() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("wrong_cookie", "some-val")))
                .andExpect(status().isNoContent());

        verifyNoInteractions(authService);
    }

    @Test
    void logoutShouldProceedWhenTokenIsInvalid() throws Exception {
        doThrow(new InvalidRefreshTokenException("invalid")).when(authService).logout(any());

        // Trigger resolveRefreshToken logic then checking logout catch block
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer invalid-rt"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
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

    @Test
    void refreshShouldWorkWithCookie() throws Exception {
        AuthResponse authResponse = AuthResponse.builder().token("new-at").build();
        when(authService.refresh("rt-from-cookie")).thenReturn(new AuthSession(authResponse, "new-rt"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "rt-from-cookie"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refresh_token", "new-rt"));
    }

    @Test
    void logoutShouldProceedGracefullyWhenNoTokenPresent() throws Exception {
        // No cookie, no auth header — InvalidRefreshTokenException is caught silently in logout
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
