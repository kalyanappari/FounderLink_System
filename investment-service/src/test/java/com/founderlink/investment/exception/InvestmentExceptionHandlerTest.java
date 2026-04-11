package com.founderlink.investment.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestmentExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInvestmentNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleInvestmentNotFoundException(new InvestmentNotFoundException("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleStartupNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleStartupNotFoundException(new StartupNotFoundException("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleDuplicateInvestmentException() {
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateInvestmentException(new DuplicateInvestmentException("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleInvalidStatusTransitionException() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidStatusTransitionException(new InvalidStatusTransitionException("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleForbiddenAccessException() {
        ResponseEntity<ErrorResponse> response = handler.handleForbiddenAccessException(new ForbiddenAccessException("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleStartupServiceUnavailableException() {
        ResponseEntity<ErrorResponse> response = handler.handleStartupServiceUnavailableException(new StartupServiceUnavailableException("method", "reason"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handleStartupServiceServerException() {
        ResponseEntity<ErrorResponse> response = handler.handleStartupServiceServerException(new StartupServiceServerException("method", 500, "reason"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void handleValidationException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(new FieldError("obj", "field", "msg")));

        ResponseEntity<Map<String, String>> response = handler.handleValidationException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("field");
    }

    @Test
    void handleIllegalStateException() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(new IllegalStateException("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleHttpMessageNotReadableException() {
        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(mock(HttpMessageNotReadableException.class));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGlobalException() {
        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(new Exception("msg"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testExceptionGetters() {
        StartupServiceUnavailableException ex1 = new StartupServiceUnavailableException("method", "reason");
        assertThat(ex1.getMethodKey()).isEqualTo("method");

        StartupServiceServerException ex2 = new StartupServiceServerException("method", 500, "reason");
        assertThat(ex2.getMethodKey()).isEqualTo("method");
        assertThat(ex2.getStatus()).isEqualTo(500);
    }

    @Test
    void testErrorResponseters() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ErrorResponse er = new ErrorResponse(400, "msg", now);
        assertThat(er.getStatus()).isEqualTo(400);
        assertThat(er.getMessage()).isEqualTo("msg");
        assertThat(er.getTimestamp()).isEqualTo(now);
    }
}
