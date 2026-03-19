package com.founderlink.investment.exception;

public class ForbiddenAccessException
        extends RuntimeException {

    public ForbiddenAccessException(String message) {
        super(message);
    }
}