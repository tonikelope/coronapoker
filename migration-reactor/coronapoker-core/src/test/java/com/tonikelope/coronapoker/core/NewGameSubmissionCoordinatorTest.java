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
        CompletableFuture<LobbySession> opening = new CompletableFuture<>();
        AtomicReference<NewGameRequest> observed = new AtomicReference<>();
        NewGameSubmissionCoordinator coordinator = new NewGameSubmissionCoordinator(
                preferences, request -> {
                    observed.set(request);
                    return opening;
                });

        CompletableFuture<NewGameSubmissionCoordinator.OpenedSession> result
                = coordinator.submit(connection, table);

        assertTrue(coordinator.submitting());
        assertNull(preferences.properties().getProperty("nick"));
        assertTrue(observed.get().table().runItTwice());
        LobbySession lobby = lobby(true, "Alice");
        opening.complete(lobby);

        assertSame(observed.get(), result.join().request());
        assertSame(lobby, result.join().lobby());
        assertFalse(coordinator.submitting());
        assertEquals("Alice", preferences.properties().getProperty("nick"));
    }

    @Test
    void joinCarriesNoHostSettingsAndCannotBeSubmittedTwice() {
        NewGameConnectionDraft connection = draft(NewGameConnectionDraft.Mode.JOIN);
        CompletableFuture<LobbySession> opening = new CompletableFuture<>();
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
        opening.complete(lobby(false, "Alice"));
    }

    @Test
    void asynchronousFailureUnlocksTheSameDraftForRetry() {
        NewGameConnectionDraft connection = draft(NewGameConnectionDraft.Mode.CREATE);
        CompletableFuture<LobbySession> first = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        NewGameSubmissionCoordinator coordinator = new NewGameSubmissionCoordinator(
                preferences, request -> calls.getAndIncrement() == 0
                        ? first : CompletableFuture.completedFuture(lobby(true, "Alice")));

        CompletableFuture<NewGameSubmissionCoordinator.OpenedSession> failed
                = coordinator.submit(connection, new NewGameTableDraft());
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
        CompletableFuture<NewGameSubmissionCoordinator.OpenedSession> result
                = coordinator.submit(connection, new NewGameTableDraft());

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

    private static LobbySession lobby(boolean host, String localNick) {
        LobbyParticipant local = new LobbyParticipant(localNick, null, true,
                host, false, true, false, true,
                LobbyParticipant.NO_LATENCY, LobbyParticipant.NO_LATENCY);
        LobbyParticipant remoteHost = new LobbyParticipant("Host", null, false,
                true, false, true, false, true, 12, 15);
        LobbySnapshot snapshot = new LobbySnapshot(localNick,
                host ? localNick : "Host", "localhost:7234", host,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                host ? java.util.List.of(local)
                        : java.util.List.of(local, remoteHost),
                java.util.List.of(), null, false, true);
        return new LobbySession(snapshot,
                command -> CompletableFuture.completedFuture(null));
    }
}
