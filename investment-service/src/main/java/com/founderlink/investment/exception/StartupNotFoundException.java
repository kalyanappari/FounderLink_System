package com.founderlink.investment.exception;


public class StartupNotFoundException
        extends RuntimeException {

    public StartupNotFoundException(String message) {
        super(message);
    }
}