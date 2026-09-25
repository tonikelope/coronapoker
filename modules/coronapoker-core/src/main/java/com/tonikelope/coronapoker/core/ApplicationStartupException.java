package com.tonikelope.coronapoker.core;

/** Raised when a process-level service cannot start. */
public final class ApplicationStartupException extends RuntimeException {

    public ApplicationStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
