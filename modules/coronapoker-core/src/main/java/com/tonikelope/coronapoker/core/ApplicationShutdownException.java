package com.tonikelope.coronapoker.core;

/** Raised after shutdown if one or more process-level services could not close. */
public final class ApplicationShutdownException extends RuntimeException {

    public ApplicationShutdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
