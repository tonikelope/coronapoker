package com.tonikelope.coronapoker.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class CoronaPokerApplicationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void followsStartupSessionTableReturnAndShutdownLifecycle() {
        CoronaPokerApplication application = CoronaPokerApplication.withoutServices();

        application.start();
        assertEquals(ApplicationLifecycle.State.STARTING, application.lifecycle().state());
        application.menuReady();
        application.sessionOpened();
        application.tableEntered();
        application.returnedToMenu();
        application.close();
        application.close();

        assertEquals(ApplicationLifecycle.State.TERMINATED, application.lifecycle().state());
    }

    @Test
    void startsInOrderAndClosesInReverseOrderExactlyOnce() {
        List<String> calls = new ArrayList<>();
        CoronaPokerApplication application = new CoronaPokerApplication(List.of(
                service("database", calls), service("audio", calls)));

        application.start();
        application.close();
        application.close();

        assertEquals(List.of("start database", "start audio", "close audio", "close database"), calls);
    }

    @Test
    void rollsBackStartedServicesWhenStartupFails() {
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new RuntimeException("crypto failed");
        ApplicationService database = service("database", calls);
        ApplicationService crypto = new ApplicationService() {
            @Override
            public void start() {
                calls.add("start crypto");
                throw failure;
            }

            @Override
            public void close() {
                calls.add("close crypto");
            }
        };
        CoronaPokerApplication application = new CoronaPokerApplication(List.of(database, crypto));

        ApplicationStartupException thrown = assertThrows(ApplicationStartupException.class, application::start);

        assertSame(failure, thrown.getCause());
        assertSame(failure, application.lifecycle().failure());
        assertEquals(ApplicationLifecycle.State.FAILED, application.lifecycle().state());
        assertEquals(List.of("start database", "start crypto", "close database"), calls);
    }

    @Test
    void rejectsFrontendEventsThatBreakCausalOrder() {
        CoronaPokerApplication application = CoronaPokerApplication.withoutServices();

        application.start();

        assertThrows(IllegalStateException.class, application::tableEntered);
    }

    @Test
    void bootstrapOwnsAndStartsItsTypedProcessServices() {
        CoronaPokerApplication application = CoronaPokerBootstrap.createApplication(tempDirectory);
        SecureRandomService secureRandom = application.service(SecureRandomService.class);
        DatabaseService database = application.service(DatabaseService.class);
        PreferencesService preferences = application.service(PreferencesService.class);
        AudioService audio = application.service(AudioService.class);
        UpdateService updates = application.service(UpdateService.class);

        assertThrows(IllegalStateException.class, secureRandom::generator);
        assertThrows(IllegalStateException.class, database::connection);
        application.start();

        assertNotNull(secureRandom.generator());
        assertSame(secureRandom, application.service(SecureRandomService.class));
        assertSame(database, application.service(DatabaseService.class));
        assertSame(preferences, application.service(PreferencesService.class));
        assertSame(audio, application.service(AudioService.class));
        assertSame(updates, application.service(UpdateService.class));
        application.close();
        assertThrows(IllegalStateException.class, database::connection);
    }

    @Test
    void databaseConnectionCanBeReleasedBetweenGamesButNotAfterProcessClose() throws Exception {
        DatabaseService database = new DatabaseService(":memory:");
        database.start();

        java.sql.Connection first = database.connection();
        database.releaseConnection();
        java.sql.Connection second = database.connection();

        assertNotSame(first, second);
        database.close();
        assertThrows(IllegalStateException.class, database::connection);
    }

    @Test
    void applicationFailurePermanentlyClosesItsDatabaseService() throws Exception {
        DatabaseService database = new DatabaseService(":memory:");
        CoronaPokerApplication application = new CoronaPokerApplication(List.of(database));
        application.start();
        database.connection();

        IllegalStateException failure = new IllegalStateException("schema initialization failed");
        application.fail(failure);

        assertSame(failure, application.lifecycle().failure());
        assertEquals(ApplicationLifecycle.State.FAILED, application.lifecycle().state());
        assertThrows(IllegalStateException.class, database::connection);
    }

    @Test
    void preferencesLoadAndSaveThroughTheProcessService() throws Exception {
        Path file = tempDirectory.resolve("settings/coronapoker.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "nick=alice\n");
        PreferencesService preferences = new PreferencesService(file);

        assertEquals("alice", preferences.properties().getProperty("nick"));
        preferences.start();
        preferences.properties().setProperty("nick", "bob");
        preferences.save();
        preferences.close();

        java.util.Properties persisted = new java.util.Properties();
        try (var input = Files.newInputStream(file)) {
            persisted.load(input);
        }
        assertEquals("bob", persisted.getProperty("nick"));
    }

    @Test
    void preferencesCloseFlushesADeferredWrite() throws Exception {
        Path file = tempDirectory.resolve("deferred/coronapoker.properties");
        PreferencesService preferences = new PreferencesService(file);
        preferences.start();
        preferences.properties().setProperty("master_volume", "0.55");

        preferences.saveDeferred();
        preferences.close();

        java.util.Properties persisted = new java.util.Properties();
        try (var input = Files.newInputStream(file)) {
            persisted.load(input);
        }
        assertEquals("0.55", persisted.getProperty("master_volume"));
    }

    @Test
    void unreadablePreferencesKeepARescueCopyAndParsedPrefix() throws Exception {
        Path file = tempDirectory.resolve("corrupt/coronapoker.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "nick=alice\nbroken=\\u00ZZ\n");
        PreferencesService preferences = new PreferencesService(file);

        assertEquals("alice", preferences.properties().getProperty("nick"));
        assertNotNull(preferences.rescueCopy());
        assertEquals(Files.readString(file), Files.readString(preferences.rescueCopy()));
    }

    @Test
    void audioBackendActivatesAndClosesExactlyOnce() throws Exception {
        List<String> calls = new ArrayList<>();
        AudioService audio = new AudioService();
        audio.configure(new AudioService.Backend() {
            @Override
            public void start() {
                calls.add("start");
            }

            @Override
            public void close() {
                calls.add("close");
            }
        });

        audio.start();
        audio.activate();
        audio.activate();
        audio.close();
        audio.close();

        assertEquals(List.of("start", "close"), calls);
        assertTrue(audio.isActivated());
    }

    @Test
    void failedAudioActivationCannotRetryAndStillClosesItsBackend() throws Exception {
        List<String> calls = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("audio device unavailable");
        AudioService audio = new AudioService();
        audio.configure(new AudioService.Backend() {
            @Override
            public void start() {
                calls.add("start");
                throw failure;
            }

            @Override
            public void close() {
                calls.add("close");
            }
        });
        CoronaPokerApplication application = new CoronaPokerApplication(List.of(audio));
        application.start();

        assertSame(failure, assertThrows(IllegalStateException.class, audio::activate));
        IllegalStateException retryFailure = assertThrows(IllegalStateException.class, audio::activate);
        assertEquals("Audio backend activation previously failed", retryFailure.getMessage());
        application.fail(failure);

        assertEquals(List.of("start", "close"), calls);
        assertEquals(ApplicationLifecycle.State.FAILED, application.lifecycle().state());
    }

    @Test
    void updateServiceRetriesAndReturnsATypedAvailableVersion() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        UpdateService updates = new UpdateService("24.10", 3, () -> {
            if (attempts.incrementAndGet() < 3) {
                return null;
            }
            return "24.11";
        });
        updates.start();

        UpdateService.CheckResult result = updates.checkLatest().get(2, TimeUnit.SECONDS);

        assertEquals(UpdateService.Status.UPDATE_AVAILABLE, result.status());
        assertEquals("24.11", result.version());
        assertEquals(3, attempts.get());
        updates.close();
        assertThrows(IllegalStateException.class, updates::checkLatest);
    }

    @Test
    void updateServiceDistinguishesCurrentFromUnavailable() throws Exception {
        UpdateService current = new UpdateService("24.10", 3, () -> "24.10");
        current.start();
        assertEquals(UpdateService.Status.CURRENT,
                current.checkLatest().get(2, TimeUnit.SECONDS).status());
        current.close();

        AtomicInteger attempts = new AtomicInteger();
        UpdateService unavailable = new UpdateService("24.10", 3, () -> {
            attempts.incrementAndGet();
            return null;
        });
        unavailable.start();
        assertEquals(UpdateService.Status.UNAVAILABLE,
                unavailable.checkLatest().get(2, TimeUnit.SECONDS).status());
        assertEquals(3, attempts.get());
        unavailable.close();
    }

    private static ApplicationService service(String name, List<String> calls) {
        return new ApplicationService() {
            @Override
            public void start() {
                calls.add("start " + name);
            }

            @Override
            public void close() {
                calls.add("close " + name);
            }
        };
    }
}
