package com.founderlink.team.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleInvitationNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleInvitationNotFoundException(new InvitationNotFoundException("Not found"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleTeamMemberNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleTeamMemberNotFoundException(new TeamMemberNotFoundException("Not found"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleStartupNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleStartupNotFoundException(new StartupNotFoundException("Not found"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleDuplicateInvitationException() {
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateInvitationException(new DuplicateInvitationException("Conflict"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleAlreadyTeamMemberException() {
        ResponseEntity<ErrorResponse> response = handler.handleAlreadyTeamMemberException(new AlreadyTeamMemberException("Conflict"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleInvalidInvitationStatusException() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidInvitationStatusException(new InvalidInvitationStatusException("Bad Request"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleUnauthorizedAccessException() {
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedAccessException(new UnauthorizedAccessException("Forbidden"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleForbiddenAccessException() {
        ResponseEntity<ErrorResponse> response = handler.handleForbiddenAccessException(new ForbiddenAccessException("Forbidden"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleStartupServiceUnavailableException() {
        ResponseEntity<ErrorResponse> response = handler.handleStartupServiceUnavailableException(new StartupServiceUnavailableException("SVC", "Down"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handleStartupServiceServerException() {
        ResponseEntity<ErrorResponse> response = handler.handleStartupServiceServerException(new StartupServiceServerException("SVC", 500, "Error"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void handleValidationException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getAllErrors()).thenReturn(List.of(new FieldError("obj", "field", "message")));

        ResponseEntity<Map<String, String>> response = handler.handleValidationException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("field", "message");
    }

    @Test
    void handleHttpMessageNotReadableException() {
        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(mock(HttpMessageNotReadableException.class));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGlobalException() {
        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(new Exception("Fail"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
