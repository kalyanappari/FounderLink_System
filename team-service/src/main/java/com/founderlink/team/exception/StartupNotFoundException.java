package com.founderlink.team.exception;

public class StartupNotFoundException
        extends RuntimeException {

    public StartupNotFoundException(String message) {
        super(message);
    }
}