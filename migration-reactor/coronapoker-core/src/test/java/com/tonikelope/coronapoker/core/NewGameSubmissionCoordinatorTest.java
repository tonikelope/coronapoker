package com.tonikelope.coronapoker.core;

import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NewGameSubmissionCoordinatorTest {

    @TempDir
    Path temporaryDirectory;

    private PreferencesService preferences;

    @BeforeEach
    void startPreferences() {
        preferences = new PreferencesService(
                temporaryDirectory.resolve("coronapoker.properties"));
        preferences.start();
    }

    @AfterEach
    void closePreferences() throws Exception {
        preferences.close();
    }

    @Test
    void hostRequestSnapshotsAllSettingsAndCommitsOnlyAfterLobbyAcceptsIt() {
        NewGameConnectionDraft connection = draft(NewGameConnectionDraft.Mode.CREATE);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setRunItTwice(true);
        CompletableFuture<Void> opening = new CompletableFuture<>();
        AtomicReference<NewGameRequest> observed = new AtomicReference<>();
        NewGameSubmissionCoordinator coordinator = new NewGameSubmissionCoordinator(
                preferences, request -> {
                    observed.set(request);
                    return opening;
                });

        CompletableFuture<NewGameRequest> result = coordinator.submit(connection, table);

        assertTrue(coordinator.submitting());
        assertNull(preferences.properties().getProperty("nick"));
        assertTrue(observed.get().table().runItTwice());
        opening.complete(null);

        assertSame(observed.get(), result.join());
        assertFalse(coordinator.submitting());
        assertEquals("Alice", preferences.properties().getProperty("nick"));
    }

    @Test
    void joinCarriesNoHostSettingsAndCannotBeSubmittedTwice() {
        NewGameConnectionDraft connection = draft(NewGameConnectionDraft.Mode.JOIN);
        CompletableFuture<Void> opening = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        NewGameSubmissionCoordinator coordinator = new NewGameSubmissionCoordinator(
                preferences, request -> {
                    calls.incrementAndGet();
                    assertTrue(request.joining());
                    assertNull(request.table());
                    return opening;
                });

        coordinator.submit(connection, new NewGameTableDraft());

        assertThrows(IllegalStateException.class,
                () -> coordinator.submit(connection, new NewGameTableDraft()));
        assertEquals(1, calls.get());
        opening.complete(null);
    }

    @Test
    void asynchronousFailureUnlocksTheSameDraftForRetry() {
        NewGameConnectionDraft connection = draft(NewGameConnectionDraft.Mode.CREATE);
        CompletableFuture<Void> first = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        NewGameSubmissionCoordinator coordinator = new NewGameSubmissionCoordinator(
                preferences, request -> calls.getAndIncrement() == 0
                        ? first : CompletableFuture.completedFuture(null));

        CompletableFuture<NewGameRequest> failed = coordinator.submit(
                connection, new NewGameTableDraft());
        first.completeExceptionally(new IllegalStateException("offline"));

        CompletionException error = assertThrows(CompletionException.class, failed::join);
        assertEquals("offline", error.getCause().getMessage());
        assertFalse(coordinator.submitting());
        assertTrue(connection.canSubmit());
        coordinator.submit(connection, new NewGameTableDraft()).join();
        assertEquals(2, calls.get());
    }

    @Test
    void cancellationCompletesTheAttemptAndLeavesPreferencesUntouched() {
        NewGameConnectionDraft connection = draft(NewGameConnectionDraft.Mode.CREATE);
        NewGameSubmissionCoordinator coordinator = new NewGameSubmissionCoordinator(
                preferences, request -> new CompletableFuture<>());
        CompletableFuture<NewGameRequest> result = coordinator.submit(
                connection, new NewGameTableDraft());

        assertTrue(coordinator.cancel());
        assertTrue(result.isCompletedExceptionally());
        assertFalse(coordinator.submitting());
        assertTrue(connection.canSubmit());
        assertNull(preferences.properties().getProperty("nick"));
    }

    private static NewGameConnectionDraft draft(NewGameConnectionDraft.Mode mode) {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(new Properties(), mode);
        draft.setNickname("Alice");
        return draft;
    }
}
