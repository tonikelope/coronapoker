package com.tonikelope.coronapoker.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/** Shared construction point used by every CoronaPoker frontend launcher. */
public final class CoronaPokerBootstrap {

    private CoronaPokerBootstrap() {
    }

    /**
     * Creates one application process. Further identity and audio services
     * will be added here as they are extracted from Swing.
     */
    public static CoronaPokerApplication createApplication() {
        return createApplication(Path.of(System.getProperty("user.home")),
                DevelopmentMode.ENABLED);
    }

    /** Creates an isolated application rooted at the supplied user home. */
    public static CoronaPokerApplication createApplication(Path userHome) {
        return createApplication(userHome, false);
    }

    /** Creates an application with an explicitly selected development mode. */
    public static CoronaPokerApplication createApplication(Path userHome,
            boolean developmentMode) {
        Path coronaDirectory = Objects.requireNonNull(userHome, "userHome")
                .toAbsolutePath().normalize().resolve(".coronapoker");
        return new CoronaPokerApplication(java.util.List.of(
                new PreferencesService(coronaDirectory.resolve("coronapoker.properties")),
                new SecureRandomService(),
                new DatabaseService(databaseLocation(coronaDirectory,
                        developmentMode).toString()),
                new FrontendRuntimeService(),
                UpdateService.forLatestRelease(
                        ApplicationMetadata.LATEST_RELEASE_URI,
                        ApplicationMetadata.VERSION,
                        3,
                        Duration.ofSeconds(10)),
                UpdaterService.production(
                        ApplicationMetadata.UPDATER_URI,
                        java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
                        10_000,
                        60_000),
                new AudioService()));
    }

    private static Path databaseLocation(Path coronaDirectory,
            boolean developmentMode) {
        Path persistent = coronaDirectory.resolve("coronapoker.db");
        if (!developmentMode) {
            return persistent;
        }
        try {
            Path disposable = Files.createTempFile("coronapoker-dev-", ".db");
            if (Files.isRegularFile(persistent)) {
                Files.copy(persistent, disposable,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            disposable.toFile().deleteOnExit();
            return disposable;
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "DEV_MODE could not create an isolated database", failure);
        }
    }
}
