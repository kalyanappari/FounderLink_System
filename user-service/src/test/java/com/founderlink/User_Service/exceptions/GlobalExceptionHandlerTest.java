package com.founderlink.User_Service.exceptions;

import com.founderlink.User_Service.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleUserNotFoundException_shouldReturn404() {
        UserNotFoundException ex = new UserNotFoundException("Not found");
        ResponseEntity<ErrorResponse> response = handler.handleUserNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getError()).isEqualTo("Not found");
    }

    @Test
    void handleConflictException_shouldReturn409() {
        ConflictException ex = new ConflictException("Conflict");
        ResponseEntity<ErrorResponse> response = handler.handleConflictException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
    }

    @Test
    void handleException_shouldReturn500() {
        Exception ex = new Exception("Internal error");
        ResponseEntity<ErrorResponse> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getError()).isEqualTo("Internal error");
    }

    @Test
    void handleValidation_shouldReturn400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "default message");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldError()).thenReturn(fieldError);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getError()).isEqualTo("default message");
    }
}
