package com.tonikelope.coronapoker.core;

/** Shared construction point used by every CoronaPoker frontend launcher. */
public final class CoronaPokerBootstrap {

    private CoronaPokerBootstrap() {
    }

    /**
     * Creates one application process. Further identity, preferences and audio
     * services will be added here as they are extracted from Swing.
     */
    public static CoronaPokerApplication createApplication() {
        String database = java.nio.file.Path.of(
                System.getProperty("user.home"), ".coronapoker", "coronapoker.db").toString();
        return new CoronaPokerApplication(java.util.List.of(
                new SecureRandomService(),
                new DatabaseService(database)));
    }
}
