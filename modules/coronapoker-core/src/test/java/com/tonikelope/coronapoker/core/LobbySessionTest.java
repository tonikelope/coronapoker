package com.tonikelope.coronapoker.core;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LobbySessionTest {

    @Test
    void snapshotIsImmutableAndRejectsCanonicalNicknameCollisions() {
        List<LobbyParticipant> participants = new ArrayList<>();
        participants.add(participant("Host", true, true, false));
        LobbySnapshot snapshot = snapshot(true, participants,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS);
        participants.clear();

        assertEquals(1, snapshot.participants().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.participants().clear());

        String decomposed = Normalizer.normalize("José", Normalizer.Form.NFD);
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(
                "José", "José", "localhost:7234", true,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(participant("José", true, true, false),
                        participant(decomposed, false, false, false)),
                List.of(), null, false, true));
    }

    @Test
    void publishesOrderedImmutableStateToSubscribersWithoutUiTypes() throws Exception {
        LobbySnapshot first = snapshot(true,
                List.of(participant("Host", true, true, false)),
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS);
        LobbySession session = new LobbySession(first,
                command -> CompletableFuture.completedFuture(null));
        AtomicInteger deliveries = new AtomicInteger();
        AtomicReference<LobbySnapshot> observed = new AtomicReference<>();
        AutoCloseable subscription = session.subscribe(next -> {
            deliveries.incrementAndGet();
            observed.set(next);
        });
        LobbySnapshot second = snapshot(true,
                List.of(participant("Host", true, true, false),
                        participant("Alice", false, false, false)),
                LobbySnapshot.Phase.CONNECTED);

        session.publish(second);
        subscription.close();
        session.publish(first);

        assertEquals(2, deliveries.get());
        assertNotSame(first, observed.get());
        assertEquals(LobbySnapshot.Phase.CONNECTED, observed.get().phase());
    }

    @Test
    void hostCommandsPreserveCapacityStartAndKickRules() {
        AtomicReference<LobbyCommand> sent = new AtomicReference<>();
        LobbySession host = new LobbySession(snapshot(true,
                List.of(participant("Host", true, true, false)),
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS), command -> {
                    sent.set(command);
                    return CompletableFuture.completedFuture(null);
                });

        assertThrows(IllegalStateException.class,
                () -> host.submit(new LobbyCommand.StartGame()));
        assertThrows(IllegalArgumentException.class,
                () -> host.submit(new LobbyCommand.Kick("Host")));
        host.submit(new LobbyCommand.AddBot()).toCompletableFuture().join();
        assertTrue(sent.get() instanceof LobbyCommand.AddBot);

        List<LobbyParticipant> full = new ArrayList<>();
        full.add(participant("Host", true, true, false));
        for (int i = 1; i < LobbySnapshot.MAX_PARTICIPANTS; i++) {
            full.add(participant("CoronaBot$" + i, false, false, true));
        }
        host.publish(snapshot(true, full, LobbySnapshot.Phase.WAITING_FOR_PLAYERS));
        assertThrows(IllegalStateException.class,
                () -> host.submit(new LobbyCommand.AddBot()));
    }

    @Test
    void clientsCannotUseHostControlsButChatRemainsAvailableInGame() {
        List<LobbyParticipant> roster = List.of(
                participant("Alice", true, false, false),
                participant("Host", false, true, false));
        AtomicReference<LobbyCommand> sent = new AtomicReference<>();
        LobbySession client = new LobbySession(snapshot(false, roster,
                LobbySnapshot.Phase.IN_GAME), command -> {
                    sent.set(command);
                    return CompletableFuture.completedFuture(null);
                });

        assertThrows(IllegalStateException.class,
                () -> client.submit(new LobbyCommand.AddBot()));
        assertThrows(IllegalStateException.class,
                () -> client.submit(new LobbyCommand.UpdateTableSettings(
                        new NewGameTableDraft().snapshot())));
        client.submit(new LobbyCommand.SendText("hola #1#"))
                .toCompletableFuture().join();
        assertTrue(sent.get() instanceof LobbyCommand.SendText);
        assertFalse(client.snapshot().host());
    }

    private static LobbySnapshot snapshot(boolean host,
            List<LobbyParticipant> participants, LobbySnapshot.Phase phase) {
        String local = host ? "Host" : "Alice";
        return new LobbySnapshot(local, "Host", "localhost:7234", host,
                phase, "", participants,
                List.of(new LobbyChatMessage(0L, Instant.EPOCH, "Host",
                        LobbyChatMessage.Type.TEXT, "Bienvenido")),
                null, false, true);
    }

    private static LobbyParticipant participant(String nickname, boolean local,
            boolean host, boolean bot) {
        return new LobbyParticipant(nickname, null, local, host, bot,
                true, false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
    }
}
