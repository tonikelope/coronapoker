/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GamePeerController;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import com.tonikelope.coronapoker.core.game.GameSession;
import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import com.tonikelope.coronapoker.core.game.GameTiming;
import com.tonikelope.coronapoker.core.network.GameTransport;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeRetryOwnershipTest {
    @Test
    void slowAcksCannotResubmitNativeBroadcastClientOrRecoveryOperations() throws Exception {
        CountDownLatch started = new CountDownLatch(4);
        List<Endpoint> endpoints = List.of(new Endpoint(started), new Endpoint(started),
                new Endpoint(started), new Endpoint(started));
        List<Crupier> dealers = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            dealers.add(dealer(endpoint));
        }

        var workers = Executors.newFixedThreadPool(4);
        try {
            List<Runnable> operations = List.of(
                    () -> dealers.get(0).broadcastGAMECommandFromServer("DUALLOCK_BUNDLE#proof", null, true),
                    () -> dealers.get(1).sendGAMECommandToServer("HANDVERIFY#signed-receipt", true),
                    () -> dealers.get(2).enviarDatosClaveRecuperados(
                            new ArrayList<>(List.of("bob")), RecoverySnapshotFixtures.validMap()),
                    () -> dealers.get(3).enviarAccionesRecuperadas(new ArrayList<>(List.of("bob")), "actions"));
            var pending = operations.stream().map(action -> CompletableFuture.runAsync(action, workers)).toList();
            assertTrue(started.await(5, TimeUnit.SECONDS), "all four production paths must attempt their first write");
            CompletableFuture.allOf(pending.toArray(CompletableFuture<?>[]::new)).get(30, TimeUnit.SECONDS);
            for (int index = 0; index < endpoints.size(); index++) {
                assertEquals(1, endpoints.get(index).writes.get(), "path " + index + " enqueued a retry with a fresh ID");
                assertTrue(endpoints.get(index).acknowledged.get(), "path " + index + " returned before the real ACK");
                assertEquals(0, endpoints.get(index).confirmations.pendingRequestCount(),
                        "path " + index + " leaked an acknowledgement request");
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void interruptedClientAndRecoveryWaitsReleaseTheirRequestsWithoutAnAck() throws Exception {
        List<Endpoint> endpoints = List.of(new Endpoint(new CountDownLatch(1)),
                new Endpoint(new CountDownLatch(1)), new Endpoint(new CountDownLatch(1)));
        Crupier client = dealer(endpoints.get(0));
        Crupier snapshot = dealer(endpoints.get(1));
        Crupier actions = dealer(endpoints.get(2));
        List<Runnable> operations = List.of(
                () -> client.sendGAMECommandToServer("HANDVERIFY#signed-receipt", true),
                () -> snapshot.enviarDatosClaveRecuperados(
                        new ArrayList<>(List.of("bob")), RecoverySnapshotFixtures.validMap()),
                () -> actions.enviarAccionesRecuperadas(new ArrayList<>(List.of("bob")), "actions"));
        var workers = Executors.newFixedThreadPool(3);
        try {
            var pending = operations.stream().map(action -> CompletableFuture.runAsync(() -> {
                Thread.currentThread().interrupt();
                try {
                    action.run();
                } finally {
                    Thread.interrupted();
                }
            }, workers)).toList();
            CompletableFuture.allOf(pending.toArray(CompletableFuture<?>[]::new)).get(5, TimeUnit.SECONDS);
            for (Endpoint endpoint : endpoints) {
                assertEquals(1, endpoint.writes.get());
                assertFalse(endpoint.acknowledged.get());
                assertEquals(0, endpoint.confirmations.pendingRequestCount());
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static Crupier dealer(Endpoint endpoint) throws ReflectiveOperationException {
        Crupier dealer = new Crupier();
        set(dealer, "game_session", new GameSession("alice", true,
                GameConfigCodecV1.fromSettings(new NewGameTableDraft().snapshot(), false, "session-a")));
        GamePlayerController alice = player("alice");
        GamePlayerController bob = player("bob");
        set(dealer, "player_controllers", List.of(alice, bob));
        set(dealer, "nick2player", new java.util.concurrent.ConcurrentHashMap<>(Map.of("alice", alice, "bob", bob)));
        set(dealer, "peer_controllers", Map.of("bob", endpoint.peer()));
        set(dealer, "game_transport", endpoint);
        return dealer;
    }

    private static GamePlayerController player(String nickname) {
        return (GamePlayerController) Proxy.newProxyInstance(GamePlayerController.class.getClassLoader(),
                new Class<?>[]{GamePlayerController.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getNickname" -> nickname;
                    case "isExit" -> false;
                    case "setTimeout" -> null;
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static void set(Crupier dealer, String name, Object value) throws ReflectiveOperationException {
        var field = Crupier.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(dealer, value);
    }

    private static final class Endpoint implements GameTransport {
        private final ConfirmationTracker confirmations = new ConfirmationTracker();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicBoolean acknowledged = new AtomicBoolean();
        private final CountDownLatch started;

        Endpoint(CountDownLatch started) {
            this.started = started;
        }

        GamePeerController peer() {
            return (GamePeerController) Proxy.newProxyInstance(GamePeerController.class.getClassLoader(),
                    new Class<?>[]{GamePeerController.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getNick" -> "bob";
                        case "retriesGameCommands" -> true;
                        case "isCpu", "isExit", "isForce_reset_socket", "isSocketDownOrReconnecting" -> false;
                        case "writeGameCommandFromServer" -> {
                            sendCommandToHost((String) args[0]);
                            yield false;
                        }
                        default -> throw new AssertionError(method.getName());
                    });
        }

        @Override public String hostNickname() { return "bob"; }
        @Override public String tablePassword() { return null; }
        @Override public boolean gameStarted() { return true; }
        @Override public boolean retriesGameCommands() { return true; }
        @Override public ConfirmationTracker confirmations() { return confirmations; }
        @Override public void closeHostConnection() { }

        @Override
        public void sendCommandToHost(String command) {
            if (writes.incrementAndGet() != 1) return;
            started.countDown();
            int confirmationId = Integer.parseInt(command.split("#", 3)[1]) + 1;
            CompletableFuture.delayedExecutor(GameTiming.CONFIRMATION_TIMEOUT_MILLIS + 1_000L,
                    TimeUnit.MILLISECONDS).execute(() -> {
                        synchronized (confirmations) {
                            acknowledged.set(confirmations.confirm("bob", confirmationId));
                        }
                    });
        }
    }
}
