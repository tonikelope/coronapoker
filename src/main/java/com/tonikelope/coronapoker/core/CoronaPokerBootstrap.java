package com.tonikelope.coronapoker.core;

/** Shared construction point used by every CoronaPoker frontend launcher. */
public final class CoronaPokerBootstrap {

    private CoronaPokerBootstrap() {
    }

    /**
     * Creates one application process. Concrete DB, crypto, preferences and
     * audio services will be added here as they are extracted from Swing.
     */
    public static CoronaPokerApplication createApplication() {
        return CoronaPokerApplication.withoutServices();
    }
}
