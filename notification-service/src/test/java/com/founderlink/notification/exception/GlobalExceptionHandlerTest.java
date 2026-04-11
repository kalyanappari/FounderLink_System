package com.founderlink.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesNotificationNotFoundException() {
        NotificationNotFoundException ex = new NotificationNotFoundException(42L);

        ResponseEntity<ErrorResponse> response = handler.handleNotificationNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).contains("42");
    }

    @Test
    void handlesIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handlesGenericException() {
        Exception ex = new Exception("something broke");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).contains("something broke");
    }

    @Test
    void errorResponseAllArgsConstructorWorks() {
        ErrorResponse er = new ErrorResponse(400, "test", java.time.LocalDateTime.now());
        assertThat(er.getStatus()).isEqualTo(400);
        assertThat(er.getMessage()).isEqualTo("test");
        assertThat(er.getTimestamp()).isNotNull();
    }

    @Test
    void errorResponseNoArgsConstructorWorks() {
        ErrorResponse er = new ErrorResponse();
        er.setStatus(500);
        er.setMessage("err");
        assertThat(er.getStatus()).isEqualTo(500);
        assertThat(er.getMessage()).isEqualTo("err");
    }
}
