package com.tonikelope.coronapoker.core;

/** Shared construction point used by every CoronaPoker frontend launcher. */
public final class CoronaPokerBootstrap {

    private CoronaPokerBootstrap() {
    }

    /**
     * Creates one application process. Further identity and audio services
     * will be added here as they are extracted from Swing.
     */
    public static CoronaPokerApplication createApplication() {
        return createApplication(java.nio.file.Path.of(System.getProperty("user.home")));
    }

    /** Creates an isolated application rooted at the supplied user home. */
    public static CoronaPokerApplication createApplication(java.nio.file.Path userHome) {
        java.nio.file.Path coronaDirectory = java.util.Objects.requireNonNull(userHome, "userHome")
                .toAbsolutePath().normalize().resolve(".coronapoker");
        return new CoronaPokerApplication(java.util.List.of(
                new PreferencesService(coronaDirectory.resolve("coronapoker.properties")),
                new SecureRandomService(),
                new DatabaseService(coronaDirectory.resolve("coronapoker.db").toString())));
    }
}
