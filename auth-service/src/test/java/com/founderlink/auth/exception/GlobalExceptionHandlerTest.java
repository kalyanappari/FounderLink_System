package com.founderlink.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAllShouldReturnGenericMessageForAdminSeedingException() {

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/test");

        ResponseEntity<ApiError> response =
                handler.handleAll(new AdminSeedingException("admin seed failed"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);

        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Something went wrong");
        assertThat(body.path()).isEqualTo("/test");
    }

    @Test
    void handleAllShouldReturnGenericMessageForGenericException() {

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/error");

        ResponseEntity<ApiError> response =
                handler.handleAll(new IllegalStateException("unexpected failure"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);

        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Something went wrong");
        assertThat(body.path()).isEqualTo("/error");
    }

    @Test
    void handleEmailExistsShouldReturn409() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleEmailExists(new EmailAlreadyExistsException("Email already registered"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Email already registered");
        assertThat(response.getBody().path()).isEqualTo("/auth/register");
    }

    @Test
    void handleBadCredentialsShouldReturn401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/login");

        ResponseEntity<ApiError> response =
                handler.handleBadCredentials(request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
        assertThat(response.getBody().path()).isEqualTo("/auth/login");
    }

    @Test
    void handleAccessDeniedShouldReturn403() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/logout");

        AccessDeniedException ex = new AccessDeniedException("Requested role is not allowed");

        ResponseEntity<ApiError> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Requested role is not allowed");
        assertThat(response.getBody().path()).isEqualTo("/auth/logout");
    }

    @Test
    void handleAccessDeniedShouldFallbackMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/logout");

        AccessDeniedException ex = new AccessDeniedException(null);

        ResponseEntity<ApiError> response = handler.handleAccessDenied(ex, request);

        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().message()).isEqualTo("Access denied");
    }

    // ── NEW TESTS FOR MISSING COVERAGE ───────────────────────────────────────

    @Test
    void handleValidationShouldReturn400WithFieldErrorMessages() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        org.springframework.web.bind.MethodArgumentNotValidException ex =
                mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult =
                mock(org.springframework.validation.BindingResult.class);
        org.springframework.validation.FieldError fieldError =
                new org.springframework.validation.FieldError("registerRequest", "email", "Email is required");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Email is required");
    }

    @Test
    void handleValidationShouldFallbackWhenNoMessages() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        org.springframework.web.bind.MethodArgumentNotValidException ex =
                mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult =
                mock(org.springframework.validation.BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of());

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Request validation failed");
    }

    @Test
    void handleBadRequestShouldReturn400ForIllegalArgument() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleBadRequest(new IllegalArgumentException("bad arg"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
    }

    @Test
    void handleBadRequestShouldReturn400ForHttpMessageNotReadable() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/login");

        org.springframework.http.converter.HttpMessageNotReadableException ex =
                new org.springframework.http.converter.HttpMessageNotReadableException("unreadable",
                        (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ApiError> response = handler.handleBadRequest(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
    }

    @Test
    void handleUnauthorizedRefreshTokenShouldReturn401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/refresh");

        ResponseEntity<ApiError> response = handler.handleUnauthorizedRefreshToken(request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid or expired refresh token");
    }

    @Test
    void handleRevokedRefreshTokenShouldReturn403() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/refresh");

        ResponseEntity<ApiError> response = handler.handleRevokedRefreshToken(request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Refresh token has been revoked");
    }

    @Test
    void handleInvalidOrExpiredPinShouldReturn400ForInvalidPin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/reset-password");

        ResponseEntity<ApiError> response =
                handler.handleInvalidOrExpiredPin(new InvalidPasswordResetPinException("Invalid PIN"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid PIN");
    }

    @Test
    void handleInvalidOrExpiredPinShouldReturn400ForExpiredPin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/reset-password");

        ResponseEntity<ApiError> response =
                handler.handleInvalidOrExpiredPin(new ExpiredPasswordResetPinException("PIN expired"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("PIN expired");
    }

    @Test
    void handleUsedPinShouldReturn400() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/reset-password");

        ResponseEntity<ApiError> response =
                handler.handleUsedPin(new UsedPasswordResetPinException("PIN already used"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("PIN already used");
    }

    @Test
    void handleUserServiceBadRequestShouldReturn400() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleUserServiceBadRequest(
                        new UserServiceBadRequestException("UserClient#createUser", "bad request"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void handleUserServiceNotFoundShouldReturn404() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleUserServiceNotFound(
                        new UserServiceNotFoundException("UserClient#createUser", "not found"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void handleUserServiceConflictShouldReturn409() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleUserServiceConflict(
                        new UserServiceConflictException("UserClient#createUser", "conflict"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void handleUserServiceUnavailableShouldReturn503() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleUserServiceUnavailable(
                        new UserServiceUnavailableException("UserClient#createUser", "Service unavailable"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void handleUserServiceFailureShouldReturn503ForClientException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/register");

        ResponseEntity<ApiError> response =
                handler.handleUserServiceFailure(
                        new UserServiceClientException("UserClient#createUser", 422, "Unprocessable"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().message()).isEqualTo("Dependent service is unavailable");
    }
}
