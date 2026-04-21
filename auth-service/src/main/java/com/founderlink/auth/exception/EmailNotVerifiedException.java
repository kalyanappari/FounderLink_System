package com.founderlink.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a LOCAL user attempts to log in before verifying their email.
 * Maps to HTTP 403 with error code EMAIL_NOT_VERIFIED so the frontend
 * can redirect the user to the verify-email page.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
