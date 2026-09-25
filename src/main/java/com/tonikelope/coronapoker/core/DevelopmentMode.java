package com.tonikelope.coronapoker.core;

/** Build-time switch for disposable local development state. */
public final class DevelopmentMode {

    /**
     * Development builds isolate every process from the user's real database.
     * This also permits several local GDX instances to run concurrently.
     */
    public static final boolean ENABLED = false;

    private DevelopmentMode() {
    }
}
