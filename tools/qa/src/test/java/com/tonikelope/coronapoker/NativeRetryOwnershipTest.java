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
        CountDownLatch started = new CountDownLatch(5);
        List<Endpoint> endpoints = List.of(new Endpoint(started), new Endpoint(started),
                new Endpoint(started), new Endpoint(started), new Endpoint(started));
        List<Crupier> dealers = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            dealers.add(dealer(endpoint));
        }

        var workers = Executors.newFixedThreadPool(5);
        try {
            List<Runnable> operations = List.of(
                    () -> dealers.get(0).broadcastGAMECommandFromServer("DUALLOCK_BUNDLE#proof", null, true),
                    () -> dealers.get(1).sendGAMECommandToServer("HANDVERIFY#signed-receipt", true),
                    () -> dealers.get(2).enviarDatosClaveRecuperados(
                            new ArrayList<>(List.of("bob")), RecoverySnapshotFixtures.validMap()),
                    () -> dealers.get(3).enviarAccionesRecuperadas(new ArrayList<>(List.of("bob")), "actions"),
                    () -> assertTrue(criticalUnicast(dealers.get(4), endpoints.get(4))));
            var pending = operations.stream().map(action -> CompletableFuture.runAsync(action, workers)).toList();
            assertTrue(started.await(5, TimeUnit.SECONDS), "all five production paths must attempt their first write");
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
    void interruptedBroadcastClientAndRecoveryWaitsReleaseTheirRequestsWithoutAnAck() throws Exception {
        List<Endpoint> endpoints = List.of(new Endpoint(new CountDownLatch(1)),
                new Endpoint(new CountDownLatch(1)), new Endpoint(new CountDownLatch(1)),
                new Endpoint(new CountDownLatch(1)), new Endpoint(new CountDownLatch(1)));
        Crupier unicast = dealer(endpoints.get(4));
        Crupier broadcast = dealer(endpoints.get(3));
        Crupier client = dealer(endpoints.get(0));
        Crupier snapshot = dealer(endpoints.get(1));
        Crupier actions = dealer(endpoints.get(2));
        List<Runnable> operations = List.of(
                () -> client.sendGAMECommandToServer("HANDVERIFY#signed-receipt", true),
                () -> snapshot.enviarDatosClaveRecuperados(
                        new ArrayList<>(List.of("bob")), RecoverySnapshotFixtures.validMap()),
                () -> actions.enviarAccionesRecuperadas(new ArrayList<>(List.of("bob")), "actions"),
                () -> broadcast.broadcastGAMECommandFromServer("POSITIONS#frame", null, true),
                () -> assertFalse(criticalUnicast(unicast, endpoints.get(4))));
        var workers = Executors.newFixedThreadPool(5);
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
                assertEquals(0, endpoint.exits.get(), "cancellation must not accuse a peer of withholding an ACK");
                assertEquals(0, endpoint.confirmations.pendingRequestCount());
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void initialPositionsMayArriveAfterTheShortConfirmationInterval() throws Exception {
        Crupier dealer = dealer(new Endpoint(new CountDownLatch(1)));
        var receive = Crupier.class.getDeclaredMethod("recibirPosiciones");
        receive.setAccessible(true);
        var receiver = Executors.newSingleThreadExecutor();
        var sender = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch receiving = new CountDownLatch(1);
        try {
            var result = receiver.submit(() -> {
                receiving.countDown();
                receive.invoke(dealer);
                return null;
            });
            assertTrue(receiving.await(5, TimeUnit.SECONDS));
            String alice = java.util.Base64.getEncoder().encodeToString(
                    "alice".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String bob = java.util.Base64.getEncoder().encodeToString(
                    "bob".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sender.schedule(() -> {
                synchronized (dealer.getReceived_commands()) {
                    dealer.getReceived_commands().add(String.join("#", "GAME", "7",
                            "POSITIONS", bob, bob, alice, alice, "0", "0"));
                    dealer.getReceived_commands().notifyAll();
                }
            }, GameTiming.CLIENT_RECEPTION_TIMEOUT_MILLIS + 1_000L, TimeUnit.MILLISECONDS);
            result.get(20, TimeUnit.SECONDS);
            assertFalse(dealer.isFin_de_la_transmision(), "honest slow positions must not stop the table");
            assertEquals("alice", dealer.getDealer_nick());
        } finally {
            sender.shutdownNow();
            receiver.shutdownNow();
        }
    }

    @Test
    void recoverySnapshotAndActionsMayArriveAfterTheShortMessageInterval() throws Exception {
        Crupier snapshot = dealer(new Endpoint(new CountDownLatch(1)));
        Crupier actions = dealer(new Endpoint(new CountDownLatch(1)));
        var snapshotWire = RecoverySnapshotV1.fromMap(RecoverySnapshotFixtures.validMap(), "session-a");
        assertTrue(snapshotWire.isOk());
        String snapshotFrame = "GAME#7#RECOVERDATA#" + java.util.Base64.getEncoder()
                .encodeToString(snapshotWire.value().encode());
        String actionText = "V1#signed-record";
        String actionsFrame = "GAME#9#ACTIONDATA#" + java.util.Base64.getEncoder()
                .encodeToString(actionText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var receiveSnapshot = Crupier.class.getDeclaredMethod("recibirDatosClaveRecuperados");
        var receiveActions = Crupier.class.getDeclaredMethod("recibirAccionesRecuperadas");
        receiveSnapshot.setAccessible(true);
        receiveActions.setAccessible(true);
        var receivers = Executors.newFixedThreadPool(2);
        var sender = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch started = new CountDownLatch(2);
        try {
            var receivedSnapshot = receivers.submit(() -> {
                started.countDown();
                return receiveSnapshot.invoke(snapshot);
            });
            var receivedActions = receivers.submit(() -> {
                started.countDown();
                return receiveActions.invoke(actions);
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            sender.schedule(() -> {
                for (var entry : Map.of(snapshot, snapshotFrame, actions, actionsFrame).entrySet()) {
                    synchronized (entry.getKey().getReceived_commands()) {
                        entry.getKey().getReceived_commands().add(entry.getValue());
                        entry.getKey().getReceived_commands().notifyAll();
                    }
                }
            }, GameTiming.CLIENT_RECEPTION_TIMEOUT_MILLIS + 1_000L, TimeUnit.MILLISECONDS);
            Map<?, ?> recovered = (Map<?, ?>) receivedSnapshot.get(20, TimeUnit.SECONDS);
            assertEquals(42, recovered.get("hand_id"));
            assertEquals(actionText, receivedActions.get(20, TimeUnit.SECONDS));
            assertFalse(snapshot.isFin_de_la_transmision());
            assertFalse(actions.isFin_de_la_transmision());
        } finally {
            sender.shutdownNow();
            receivers.shutdownNow();
        }
    }

    private static boolean criticalUnicast(Crupier dealer, Endpoint endpoint) {
        try {
            var send = Crupier.class.getDeclaredMethod("sendGAMECommandToParticipant",
                    GamePeerController.class, String.class);
            send.setAccessible(true);
            return (boolean) send.invoke(dealer, endpoint.peer(), "POCKET_DEFERRED#bob");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
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
                    case "isActivo" -> true;
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
        private final AtomicInteger exits = new AtomicInteger();
        private final CountDownLatch started;

        Endpoint(CountDownLatch started) {
            this.started = started;
        }

        GamePeerController peer() {
            return (GamePeerController) Proxy.newProxyInstance(GamePeerController.class.getClassLoader(),
                    new Class<?>[]{GamePeerController.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getNick" -> "bob";
                        case "retriesGameCommands" -> true;
                        case "getAes_key", "getHmac_key", "socketClose" -> null;
                        case "markExitAndNotify" -> { exits.incrementAndGet(); yield null; }
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
