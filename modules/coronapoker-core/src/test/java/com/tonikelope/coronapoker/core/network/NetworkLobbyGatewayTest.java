package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.IdentityTrustStore;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.StatsSyncService;
import com.tonikelope.coronapoker.core.game.CoreGameDatabase;
import com.tonikelope.coronapoker.core.game.GameLaunchContext;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameTableFactory;
import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkLobbyGatewayTest {
    @TempDir Path temporary;

    @Test void closeInterruptsAndJoinsItsNetworkWorkers() throws Exception {
        NetworkLobbyGateway gateway = new NetworkLobbyGateway(
                temporary.resolve("deterministic-close"));
        ExecutorService executor = (ExecutorService) field(gateway, "executor");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        gateway.close();

        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertTrue(executor.isTerminated(),
                "close must not leak native-network workers into the next scenario");
    }

    @Test void authenticatedHandshakeObservesBothRemoteIdentities() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        TrackingIdentityTrust hostTrust = new TrackingIdentityTrust();
        TrackingIdentityTrust clientTrust = new TrackingIdentityTrust();
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("trust-host"), GameTableFactory.unavailable(),
                    null, hostTrust);
             NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(
                    temporary.resolve("trust-client"), GameTableFactory.unavailable(),
                    null, clientTrust)) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true, "Invitado", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> hostTrust.observed.get() != null
                        && clientTrust.observed.get() != null);
                assertEquals("Invitado", hostTrust.observed.get());
                assertEquals("Anfitrion", clientTrust.observed.get());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test void encryptedLobbySyncConvergesTwoIndependentStatisticsDatabases()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = statsDatabase("stats-host.db");
        DatabaseService clientDb = statsDatabase("stats-client.db");
        DatabaseService relayDb = statsDatabase("stats-relay.db");
        seedFinishedGame(hostDb, "HOST-UGI");
        seedFinishedGame(clientDb, "CLIENT-UGI");
        StatsSyncService hostStats = new StatsSyncService(hostDb,
                () -> true, () -> java.util.Set.of());
        StatsSyncService clientStats = new StatsSyncService(clientDb,
                () -> true, () -> java.util.Set.of());
        StatsSyncService relayStats = new StatsSyncService(relayDb,
                () -> true, () -> java.util.Set.of());
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("stats-host"), GameTableFactory.unavailable(),
                    null, IdentityTrustStore.unavailable(), () -> true,
                    () -> true, hostStats);
             NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(
                    temporary.resolve("stats-client"), GameTableFactory.unavailable(),
                    null, IdentityTrustStore.unavailable(), () -> true,
                    () -> true, clientStats);
             NetworkLobbyGateway relayGateway = new NetworkLobbyGateway(
                    temporary.resolve("stats-relay"), GameTableFactory.unavailable(),
                    null, IdentityTrustStore.unavailable(), () -> true,
                    () -> true, relayStats)) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession relay = relayGateway.open(request(true, "Observador", port))
                    .get(5, TimeUnit.SECONDS);
            await(() -> hasGame(relayDb, "HOST-UGI"));
            LobbySession client = clientGateway.open(request(true, "Invitado", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> hasGame(hostDb, "CLIENT-UGI")
                        && hasGame(clientDb, "HOST-UGI")
                        && hasGame(relayDb, "CLIENT-UGI"));
                assertEquals("Invitado", importedFrom(hostDb, "CLIENT-UGI"));
                assertEquals("Anfitrion", importedFrom(clientDb, "HOST-UGI"));
                assertEquals("Anfitrion", importedFrom(relayDb, "CLIENT-UGI"));
                assertEquals(2, gameCount(hostDb));
                assertEquals(2, gameCount(clientDb));
                assertEquals(2, gameCount(relayDb));
            } finally {
                client.close();
                relay.close();
                host.close();
            }
        } finally {
            relayDb.close();
            clientDb.close();
            hostDb.close();
        }
    }

    @Test void twoNativeSessionsHandshakeChatManageBotsAndLeave() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(temporary.resolve("host"));
             NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(temporary.resolve("client"));
             NetworkLobbyGateway lateGateway = new NetworkLobbyGateway(temporary.resolve("late"))) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true, "Invitado", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2);
                assertEquals(2, client.snapshot().participants().size());
                assertEquals(host.snapshot().tableSettings(), client.snapshot().tableSettings());
                NewGameTableDraft changedDraft = NewGameTableDraft.from(
                        host.snapshot().tableSettings());
                changedDraft.setBlindStructure("Turbo amigos",
                        List.of(new NewGameTableDraft.BlindLevel(0.1, 0.2),
                                new NewGameTableDraft.BlindLevel(0.2, 0.4),
                                new NewGameTableDraft.BlindLevel(0.5, 1)), 1);
                changedDraft.setAnte(true);
                changedDraft.setStraddle(true);
                changedDraft.setHandLimit(true);
                changedDraft.setHandLimitCount(37);
                changedDraft.setThinkSeconds(55);
                NewGameTableDraft.Settings changedSettings = changedDraft.snapshot();
                host.submit(new LobbyCommand.UpdateTableSettings(changedSettings))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> changedSettings.equals(client.snapshot().tableSettings()));
                assertEquals(changedSettings, host.snapshot().tableSettings());
                await(() -> host.snapshot().participants().stream()
                                .filter(participant -> !participant.local()
                                        && !participant.bot())
                                .allMatch(participant -> participant.latency() >= 0
                                        && participant.previousLatency() >= 0)
                        && client.snapshot().participants().stream()
                                .filter(participant -> !participant.local()
                                        && !participant.bot())
                                .allMatch(participant -> participant.latency() >= 0
                                        && participant.previousLatency() >= 0));
                assertTrue(host.snapshot().participants().stream()
                        .filter(participant -> !participant.local()
                                && !participant.bot())
                        .allMatch(participant -> participant.identityPublicKey()
                                != null));
                assertTrue(client.snapshot().participants().stream()
                        .filter(participant -> !participant.local()
                                && !participant.bot())
                        .allMatch(participant -> participant.identityPublicKey()
                                != null));
                byte[] hostView = host.snapshot().participants().stream()
                        .filter(participant -> participant.nickname().equals("Invitado"))
                        .findFirst().orElseThrow().sessionFingerprint();
                byte[] clientView = client.snapshot().participants().stream()
                        .filter(participant -> participant.nickname().equals("Anfitrion"))
                        .findFirst().orElseThrow().sessionFingerprint();
                assertEquals(32, hostView.length);
                assertArrayEquals(hostView, clientView,
                        "both encrypted-channel ends must publish the same irreversible fingerprint");
                assertTrue(host.snapshot().participants().stream()
                        .filter(participant -> participant.local()
                                || participant.bot())
                        .allMatch(participant -> participant.sessionFingerprint() == null));

                LobbySession late = lateGateway.open(request(true, "Ultimo", port))
                        .get(5, TimeUnit.SECONDS);
                try {
                    await(() -> late.snapshot().participants().size() == 3);
                    assertTrue(late.snapshot().participants().stream()
                            .anyMatch(participant -> participant.nickname().equals("Invitado")
                                    && participant.secure()),
                            "USERSLIST must preserve the existing peer's verified identity");
                } finally {
                    late.close();
                }
                await(() -> host.snapshot().participants().size() == 2);

                client.submit(new LobbyCommand.SendText("  hola mesa  ")).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().chat().stream()
                        .anyMatch(item -> item.nickname().equals("Invitado")
                                && item.content().equals("  hola mesa  ")));

                String imageUrl = "https://example.invalid/mesa.gif";
                client.submit(new LobbyCommand.SendImage(imageUrl)).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().chat().stream()
                        .anyMatch(item -> item.nickname().equals("Invitado")
                                && item.type() == LobbyChatMessage.Type.IMAGE
                                && item.content().equals(
                                        "imgs://example.invalid/mesa.gif")));

                byte[] voice = validVoiceWav();
                client.submit(new LobbyCommand.SendVoice(voice)).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().chat().stream()
                        .anyMatch(item -> item.nickname().equals("Invitado")
                                && item.type() == LobbyChatMessage.Type.VOICE));
                LobbyChatMessage receivedVoice = host.snapshot().chat().stream()
                        .filter(item -> item.nickname().equals("Invitado")
                                && item.type() == LobbyChatMessage.Type.VOICE)
                        .findFirst().orElseThrow();
                assertArrayEquals(voice, Base64.getDecoder().decode(receivedVoice.content()));
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> client.submit(new LobbyCommand.SendVoice(new byte[]{1, 2, 3}))
                                .toCompletableFuture().get(2, TimeUnit.SECONDS));

                host.submit(new LobbyCommand.AddBot()).toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> client.snapshot().participants().stream().anyMatch(p -> p.bot()));
                assertTrue(host.snapshot().participants().stream().anyMatch(p -> p.bot()));

                client.submit(new LobbyCommand.Leave()).toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 2);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test void hostLeaveClosesEveryNativeClientGracefully() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("host-leave-host"));
             NetworkLobbyGateway firstGateway = new NetworkLobbyGateway(
                    temporary.resolve("host-leave-first"));
             NetworkLobbyGateway secondGateway = new NetworkLobbyGateway(
                    temporary.resolve("host-leave-second"))) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(request(true, "Primero", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(request(true, "Segundo", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 3
                        && first.snapshot().participants().size() == 3
                        && second.snapshot().participants().size() == 3);
                assertTrue(List.of(host, first, second).stream()
                        .flatMap(lobby -> lobby.snapshot().participants()
                                .stream())
                        .allMatch(participant -> participant.connected()),
                        "host-relayed peers are present, not disconnected");

                host.submit(new LobbyCommand.Leave()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);

                await(() -> host.snapshot().phase()
                                == com.tonikelope.coronapoker.core.LobbySnapshot.Phase.CLOSED
                        && first.snapshot().phase()
                                == com.tonikelope.coronapoker.core.LobbySnapshot.Phase.CLOSED
                        && second.snapshot().phase()
                                == com.tonikelope.coronapoker.core.LobbySnapshot.Phase.CLOSED);
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    @Test void startTransfersTheAuthenticatedChannelToBothTableSessions() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        AtomicReference<GameLaunchContext> hostContext = new AtomicReference<>();
        AtomicReference<GameLaunchContext> clientContext = new AtomicReference<>();
        AtomicReference<String> hostInitCommand = new AtomicReference<>();
        GameTableFactory tables = context -> {
            (context.lobby().host() ? hostContext : clientContext).set(context);
            TableEventBridge events = new TableEventBridge();
            return new TableSession(emptyTable(context.lobby().localNickname()), command -> { },
                    events, () -> {
                        if (context.lobby().host()) {
                            try {
                                String initCommand = "INIT#"
                                        + GameConfigCodecV1.encodeBase64(
                                                GameConfigCodecV1.fromSettings(
                                                        context.lobby().tableSettings(),
                                                        false, "native-handoff"));
                                hostInitCommand.set(initCommand);
                                return context.channel().broadcastFromHost(
                                        initCommand, null);
                            } catch (java.io.IOException failure) {
                                return CompletableFuture.failedFuture(failure);
                            }
                        }
                        return CompletableFuture.completedFuture(null);
                    });
        };
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("handoff-host"), tables);
             NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(
                    temporary.resolve("handoff-client"), tables)) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true, "Invitado", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2);
                NewGameTableDraft updated = NewGameTableDraft.from(
                        host.snapshot().tableSettings());
                updated.setAnte(true);
                updated.setRunItTwice(true);
                updated.setIwtsth(true);
                updated.setIncreaseBlinds(true);
                updated.setBlindIncreaseType(
                        NewGameTableDraft.BlindIncreaseType.HANDS);
                updated.setBlindInterval(3);
                updated.setBlindCap(true);
                updated.setBlindCapRaises(2);
                updated.setThinkSeconds(55);
                NewGameTableDraft.Settings expectedSettings = updated.snapshot();
                host.submit(new LobbyCommand.UpdateTableSettings(expectedSettings))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> expectedSettings.equals(
                        client.snapshot().tableSettings()));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                hostTable.attach(immediateRenderer()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);

                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals("Anfitrion", hostTable.initialState().localNickname());
                assertEquals("Invitado", clientTable.initialState().localNickname());
                assertTrue(client.snapshot().startingOrStarted());

                AtomicReference<String> bufferedInit = new AtomicReference<>();
                clientContext.get().channel().subscribe(inbound ->
                        bufferedInit.set(inbound.command()));
                await(() -> hostInitCommand.get() != null
                        && hostInitCommand.get().equals(bufferedInit.get()));
                assertTrue(hostContext.get().lobby().host());
                assertEquals(expectedSettings,
                        hostContext.get().lobby().tableSettings());
                assertEquals(expectedSettings,
                        clientContext.get().lobby().tableSettings());
                assertEquals("native-handoff",
                        clientContext.get().initialConfiguration().sessionId());
                assertTrue(clientContext.get().initialConfiguration().ante());
                assertTrue(clientContext.get().initialConfiguration().runItTwice());
                assertTrue(clientContext.get().initialConfiguration().iwtsth());
                assertEquals(3, clientContext.get().initialConfiguration()
                        .blindsDouble());
                assertEquals(2, clientContext.get().initialConfiguration()
                        .blindsDoubleType());
                assertEquals(0.6d, clientContext.get().initialConfiguration()
                        .blindCap(), 0.000_001d);
                assertEquals(55,
                        clientContext.get().initialConfiguration().thinkTime());

                AtomicReference<String> hostInbound = new AtomicReference<>();
                hostContext.get().channel().subscribe(inbound ->
                        hostInbound.set(inbound.peerNickname() + ":" + inbound.command()));
                clientContext.get().channel().sendToHost("ACTION#payload")
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> "Invitado:ACTION#payload".equals(hostInbound.get()));

                hostContext.get().channel().sendFromHost("Invitado", "PAUSE#0#host")
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> "PAUSE#0#host".equals(bufferedInit.get()));
                assertEquals(-1, hostContext.get().recoveryGameId());
                assertEquals(-1, clientContext.get().recoveryGameId());

                // Once the local table channel has crossed its terminal
                // barrier, the host socket EOF is a normal close rather than
                // a transient disconnect that should start reconnection.
                clientContext.get().channel().close();
                host.close();
                await(() -> client.snapshot().phase()
                        == com.tonikelope.coronapoker.core.LobbySnapshot.Phase.CLOSED);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test void botOnlyStartedTableAcceptsItsFirstRemoteHumanAsWarmingObserver()
            throws Exception {
        assertStartedTableAcceptsLateHuman(false, 1, "bot-only");
    }

    @Test void humanStartedTableAcceptsAnotherRemoteHumanAsWarmingObserver()
            throws Exception {
        assertStartedTableAcceptsLateHuman(true, 0, "human-only");
    }

    @Test void mixedStartedTableAcceptsAnotherRemoteHumanAsWarmingObserver()
            throws Exception {
        assertStartedTableAcceptsLateHuman(true, 1, "mixed");
    }

    @Test void retiringWarmingPeerPurgesItsQueuedBoundaryWithoutClosingTable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        AtomicReference<GameLaunchContext> hostContext = new AtomicReference<>();
        AtomicReference<GameLaunchContext> incumbentContext
                = new AtomicReference<>();
        AtomicReference<GameLaunchContext> warmingContext
                = new AtomicReference<>();
        GameTableFactory tables = context -> {
            if (context.lobby().host()) {
                hostContext.set(context);
            } else if ("Calentando".equals(
                    context.lobby().localNickname())) {
                warmingContext.set(context);
            } else {
                incumbentContext.set(context);
            }
            return new TableSession(emptyTable(
                    context.lobby().localNickname()), command -> { },
                    new TableEventBridge(), () -> {
                        if (!context.lobby().host()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        try {
                            return context.channel().broadcastFromHost(
                                    "INIT#" + GameConfigCodecV1.encodeBase64(
                                            context.initialConfiguration()),
                                    null);
                        } catch (java.io.IOException failure) {
                            return CompletableFuture.failedFuture(failure);
                        }
                    });
        };
        CountDownLatch exitEntered = new CountDownLatch(1);
        CountDownLatch allowRetire = new CountDownLatch(1);
        CountDownLatch exitNotice = new CountDownLatch(1);
        CountDownLatch incumbentAction = new CountDownLatch(1);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("queued-exit-host"), tables);
                NetworkLobbyGateway incumbentGateway = new NetworkLobbyGateway(
                    temporary.resolve("queued-exit-incumbent"), tables);
                NetworkLobbyGateway warmingGateway = new NetworkLobbyGateway(
                    temporary.resolve("queued-exit-warming"), tables)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port)).get(5,
                            TimeUnit.SECONDS);
            LobbySession incumbent = incumbentGateway.open(
                    request(true, "Invitado", port)).get(5,
                            TimeUnit.SECONDS);
            LobbySession warming = null;
            try {
                await(() -> host.snapshot().participants().size() == 2);
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.tableSession().toCompletableFuture().get(2,
                        TimeUnit.SECONDS).attach(immediateRenderer())
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                incumbent.tableSession().toCompletableFuture().get(2,
                        TimeUnit.SECONDS);

                hostContext.get().channel().subscribe(inbound -> {
                    received.add(inbound.peerNickname() + ":"
                            + inbound.command());
                    if ("Calentando".equals(inbound.peerNickname())
                            && "HOTJOIN_EXIT".equals(inbound.command())) {
                        exitEntered.countDown();
                        awaitUnchecked(allowRetire);
                        try {
                            hostContext.get().channel().sendFromHost(
                                    inbound.peerNickname(),
                                    "HOTJOIN_EXITED").toCompletableFuture()
                                    .join();
                        } catch (java.io.IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                        hostContext.get().channel().retirePeerAfterExit(
                                inbound.peerNickname());
                    } else if ("Invitado".equals(inbound.peerNickname())
                            && "ACTION#table-survives".equals(
                                    inbound.command())) {
                        incumbentAction.countDown();
                    }
                });

                warming = warmingGateway.open(
                        request(true, "Calentando", port)).get(5,
                                TimeUnit.SECONDS);
                warming.tableSession().toCompletableFuture().get(5,
                        TimeUnit.SECONDS);
                await(() -> warmingContext.get() != null
                        && host.snapshot().participants().size() == 3);
                warmingContext.get().channel().subscribe(inbound -> {
                    if ("HOTJOIN_EXITED".equals(inbound.command())) {
                        exitNotice.countDown();
                        // Production closes this local terminal table from its
                        // listener. The transport must have emitted CONF first
                        // or the host's ordered consumer stalls until the
                        // reconnect deadline.
                        warmingContext.get().channel()
                                .closeLocalHostConnection();
                    }
                });

                warmingContext.get().channel().sendToHost("HOTJOIN_EXIT")
                        .toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertTrue(exitEntered.await(2, TimeUnit.SECONDS));

                // Model the real race: the observer's dealer crosses the hand
                // boundary just after its UI thread requested exit. The frame
                // is already authenticated and ACKed, but remains queued
                // behind HOTJOIN_EXIT while retirement is deliberately held.
                warmingContext.get().channel().sendToHost("HAND_READY#2")
                        .toCompletableFuture().get(5, TimeUnit.SECONDS);
                allowRetire.countDown();

                await(() -> host.snapshot().participants().size() == 2);
                assertTrue(exitNotice.await(2, TimeUnit.SECONDS));
                incumbentContext.get().channel().sendToHost(
                        "ACTION#table-survives").toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                assertTrue(incumbentAction.await(2, TimeUnit.SECONDS),
                        "retiring one warming peer must keep the shared game channel alive");
                assertFalse(received.contains("Calentando:HAND_READY#2"),
                        "retirement must purge an already queued boundary frame from that incarnation");
            } finally {
                allowRetire.countDown();
                if (warming != null) warming.close();
                incumbent.close();
                host.close();
            }
        }
    }

    @Test void startedTableRejectsLateHumanWhenHotJoinIsDisabled()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        GameTableFactory tables = context -> new TableSession(emptyTable(
                context.lobby().localNickname()), command -> { },
                new TableEventBridge(),
                () -> CompletableFuture.completedFuture(null));
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        NewGameConnectionDraft.Mode.CREATE, "Anfitrion", "",
                        "127.0.0.1", Integer.toString(port), null, false,
                        false, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setAllowHotJoin(false);
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("late-disabled-host"), tables);
                NetworkLobbyGateway lateGateway = new NetworkLobbyGateway(
                    temporary.resolve("late-disabled-client"), tables);
                LobbySession host = hostGateway.open(new NewGameRequest(
                        connection, table.snapshot())).get(5,
                                TimeUnit.SECONDS)) {
            host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            await(() -> host.snapshot().participants().size() == 2);
            host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            host.tableSession().toCompletableFuture().get(2, TimeUnit.SECONDS);

            Exception rejected = assertThrows(Exception.class,
                    () -> lateGateway.open(request(true, "Tardio", port))
                            .get(5, TimeUnit.SECONDS));
            Throwable cause = rejected;
            while (cause.getCause() != null) cause = cause.getCause();
            assertEquals("La entrada con la timba empezada está desactivada",
                    cause.getMessage());
            assertEquals(2, host.snapshot().participants().size());
        }
    }

    @Test void hostCanChangeHotJoinAdmissionDuringTheGameAndPeersConverge()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        GameTableFactory tables = context -> new TableSession(emptyTable(
                context.lobby().localNickname()), command -> { },
                new TableEventBridge(), () -> {
                    if (!context.lobby().host()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    try {
                        return context.channel().broadcastFromHost("INIT#"
                                + GameConfigCodecV1.encodeBase64(
                                        context.initialConfiguration()), null);
                    } catch (java.io.IOException failure) {
                        return CompletableFuture.failedFuture(failure);
                    }
                });
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("live-policy-host"), tables);
                NetworkLobbyGateway incumbentGateway = new NetworkLobbyGateway(
                    temporary.resolve("live-policy-incumbent"), tables);
                NetworkLobbyGateway rejectedGateway = new NetworkLobbyGateway(
                    temporary.resolve("live-policy-rejected"), tables);
                NetworkLobbyGateway warmingGateway = new NetworkLobbyGateway(
                    temporary.resolve("live-policy-warming"), tables);
                NetworkLobbyGateway finalRejectedGateway = new NetworkLobbyGateway(
                    temporary.resolve("live-policy-final-rejected"), tables)) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession incumbent = incumbentGateway.open(
                    request(true, "Invitado", port)).get(5, TimeUnit.SECONDS);
            LobbySession warming = null;
            try {
                await(() -> host.snapshot().participants().size() == 2);
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.tableSession().toCompletableFuture().get(2,
                        TimeUnit.SECONDS).attach(immediateRenderer())
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                incumbent.tableSession().toCompletableFuture().get(2,
                        TimeUnit.SECONDS);

                host.submit(new LobbyCommand.SetHotJoinPolicy(false))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> !host.snapshot().tableSettings().allowHotJoin()
                        && !incumbent.snapshot().tableSettings().allowHotJoin());
                Exception rejected = assertThrows(Exception.class,
                        () -> rejectedGateway.open(
                                request(true, "Bloqueado", port))
                                .get(5, TimeUnit.SECONDS));
                assertEquals("La entrada con la timba empezada está desactivada",
                        rootCause(rejected).getMessage());

                host.submit(new LobbyCommand.SetHotJoinPolicy(true))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().tableSettings().allowHotJoin()
                        && incumbent.snapshot().tableSettings().allowHotJoin());
                warming = warmingGateway.open(request(true, "Calentando", port))
                        .get(5, TimeUnit.SECONDS);
                warming.tableSession().toCompletableFuture().get(5,
                        TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().stream().anyMatch(
                        participant -> participant.nickname().equals(
                                "Calentando")));

                host.submit(new LobbyCommand.SetHotJoinPolicy(false))
                        .toCompletableFuture().get(2, TimeUnit.SECONDS);
                LobbySession admitted = warming;
                await(() -> !host.snapshot().tableSettings().allowHotJoin()
                        && !incumbent.snapshot().tableSettings().allowHotJoin()
                        && !admitted.snapshot().tableSettings().allowHotJoin());
                assertTrue(host.snapshot().participants().stream().anyMatch(
                        participant -> participant.nickname().equals(
                                "Calentando")),
                        "changing policy must not evict an already admitted peer");
                Exception finalRejected = assertThrows(Exception.class,
                        () -> finalRejectedGateway.open(
                                request(true, "DemasiadoTarde", port))
                                .get(5, TimeUnit.SECONDS));
                assertEquals("La entrada con la timba empezada está desactivada",
                        rootCause(finalRejected).getMessage());
            } finally {
                if (warming != null) warming.close();
                incumbent.close();
                host.close();
            }
        }
    }

    @Test void livePolicyChangeAndJoinRaceHasOnlyCompleteOutcomes()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        GameTableFactory tables = context -> new TableSession(emptyTable(
                context.lobby().localNickname()), command -> { },
                new TableEventBridge(),
                () -> CompletableFuture.completedFuture(null));
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("race-policy-host"), tables);
                NetworkLobbyGateway racingGateway = new NetworkLobbyGateway(
                    temporary.resolve("race-policy-client"), tables);
                NetworkLobbyGateway afterGateway = new NetworkLobbyGateway(
                    temporary.resolve("race-policy-after"), tables)) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession racing = null;
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.tableSession().toCompletableFuture().get(2,
                        TimeUnit.SECONDS);

                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<Void> disable = CompletableFuture.runAsync(() -> {
                    awaitUnchecked(start);
                    host.submit(new LobbyCommand.SetHotJoinPolicy(false))
                            .toCompletableFuture().join();
                });
                CompletableFuture<LobbySession> join = CompletableFuture
                        .supplyAsync(() -> {
                            awaitUnchecked(start);
                            return racingGateway.open(
                                    request(true, "EnCarrera", port)).join();
                        });
                start.countDown();
                disable.get(5, TimeUnit.SECONDS);
                Throwable rejection = null;
                try {
                    racing = join.get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    rejection = rootCause(failure);
                }
                await(() -> !host.snapshot().tableSettings().allowHotJoin());
                boolean admitted = host.snapshot().participants().stream()
                        .anyMatch(participant -> participant.nickname().equals(
                                "EnCarrera"));
                assertEquals(racing != null, admitted,
                        "a racing connection must be fully admitted or absent");
                if (racing == null) {
                    assertEquals("La entrada con la timba empezada está desactivada",
                            rejection == null ? null : rejection.getMessage());
                } else {
                    assertTrue(racing.snapshot().startingOrStarted());
                }

                Exception after = assertThrows(Exception.class,
                        () -> afterGateway.open(request(true, "Despues", port))
                                .get(5, TimeUnit.SECONDS));
                assertEquals("La entrada con la timba empezada está desactivada",
                        rootCause(after).getMessage());
            } finally {
                if (racing != null) racing.close();
                host.close();
            }
        }
    }

    private void assertStartedTableAcceptsLateHuman(boolean incumbentHuman,
            int bots, String topology) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        AtomicReference<GameLaunchContext> hostContext = new AtomicReference<>();
        AtomicReference<GameLaunchContext> incumbentContext
                = new AtomicReference<>();
        AtomicReference<GameLaunchContext> lateContext
                = new AtomicReference<>();
        GameTableFactory tables = context -> {
            if (context.lobby().host()) {
                hostContext.set(context);
            } else if ("Tardio".equals(context.lobby().localNickname())) {
                lateContext.set(context);
            } else {
                incumbentContext.set(context);
            }
            TableEventBridge events = new TableEventBridge();
            return new TableSession(emptyTable(
                    context.lobby().localNickname()), command -> { }, events,
                    () -> {
                        if (!context.lobby().host() || !incumbentHuman) {
                            return CompletableFuture.completedFuture(null);
                        }
                        try {
                            return context.channel().broadcastFromHost(
                                    "INIT#" + GameConfigCodecV1.encodeBase64(
                                            context.initialConfiguration()),
                                    null);
                        } catch (java.io.IOException failure) {
                            return CompletableFuture.failedFuture(failure);
                        }
                    });
        };
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("late-host-" + topology), tables);
             NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(
                    temporary.resolve("late-client-" + topology), tables);
            NetworkLobbyGateway lateGateway = new NetworkLobbyGateway(
                    temporary.resolve("late-warming-" + topology), tables)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = incumbentHuman
                    ? clientGateway.open(request(true, "Invitado", port))
                            .get(5, TimeUnit.SECONDS)
                    : null;
            try {
                for (int index = 0; index < bots; index++) {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(2, TimeUnit.SECONDS);
                }
                int initialPlayers = 1 + bots + (incumbentHuman ? 1 : 0);
                await(() -> host.snapshot().participants().size()
                        == initialPlayers);
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.tableSession().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS)
                        .attach(immediateRenderer()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                if (client != null) {
                    client.tableSession().toCompletableFuture()
                            .get(2, TimeUnit.SECONDS);
                }

                AtomicReference<String> hostNotice = new AtomicReference<>();
                AtomicReference<String> clientNotice = new AtomicReference<>();
                hostContext.get().channel().subscribe(inbound
                        -> hostNotice.set(inbound.command()));
                if (incumbentContext.get() != null) {
                    incumbentContext.get().channel().subscribe(inbound
                            -> clientNotice.set(inbound.command()));
                }

                LobbySession late = lateGateway.open(
                        request(true, "Tardio", port)).get(5, TimeUnit.SECONDS);
                assertTrue(late.snapshot().startingOrStarted(),
                        "a hot join must block the lobby from its first frame");
                late.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> lateContext.get() != null);
                String encodedNickname = Base64.getEncoder().encodeToString(
                        "Tardio".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String hotJoinPrefix = "HOTJOIN#" + encodedNickname + "#";
                await(() -> hostNotice.get() != null
                        && hostNotice.get().startsWith(hotJoinPrefix)
                        && (!incumbentHuman || (clientNotice.get() != null
                        && clientNotice.get().startsWith(hotJoinPrefix))));
                if (incumbentHuman) {
                    assertEquals(hostNotice.get(), clientNotice.get());
                }
                assertTrue(lateContext.get().hotJoining());
                assertTrue(lateContext.get().initialConfiguration().recover());
                assertTrue(late.snapshot().startingOrStarted());
                assertEquals(initialPlayers + 1,
                        host.snapshot().participants().size());
                assertTrue(host.snapshot().startingOrStarted());
                late.close();
            } finally {
                if (client != null) client.close();
                host.close();
            }
        }
    }

    @Test void recoveryIdReachesOnlyTheRecoveringHostLaunchContext() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        AtomicReference<GameLaunchContext> hostContext = new AtomicReference<>();
        AtomicReference<GameLaunchContext> clientContext = new AtomicReference<>();
        String recoveryInit = "INIT#" + GameConfigCodecV1.encodeBase64(
                GameConfigCodecV1.fromSettings(
                        new NewGameTableDraft().snapshot(), true,
                        "persisted-ugi"));
        GameTableFactory tables = context -> {
            (context.lobby().host() ? hostContext : clientContext).set(context);
            return new TableSession(emptyTable(context.lobby().localNickname()),
                    command -> { }, new TableEventBridge(), () -> {
                        if (context.lobby().host()) {
                            try {
                                return context.channel().broadcastFromHost(
                                        recoveryInit, null);
                            } catch (java.io.IOException failure) {
                                return CompletableFuture.failedFuture(failure);
                            }
                        }
                        return CompletableFuture.completedFuture(null);
                    });
        };
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("recovery-host"), tables);
                NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(
                    temporary.resolve("recovery-client"), tables)) {
            NewGameConnectionDraft.Submission connection
                    = new NewGameConnectionDraft.Submission(
                            NewGameConnectionDraft.Mode.CREATE, "Anfitrion", "",
                            "127.0.0.1", Integer.toString(port), null, false,
                            true, 37);
            LobbySession host = hostGateway.open(new NewGameRequest(connection,
                    new NewGameTableDraft().snapshot())).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2);
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.tableSession().toCompletableFuture().get(2, TimeUnit.SECONDS)
                        .attach(immediateRenderer()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                client.tableSession().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals(37, hostContext.get().recoveryGameId());
                assertTrue(hostContext.get().lobby().recovering());
                assertEquals(-1, clientContext.get().recoveryGameId());
                assertTrue(clientContext.get().initialConfiguration().recover());
                assertEquals("persisted-ugi",
                        clientContext.get().initialConfiguration().sessionId());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test void activeNativeGameReconnectsWithoutLosingOrDuplicatingCommands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        AtomicReference<GameLaunchContext> hostContext = new AtomicReference<>();
        AtomicReference<GameLaunchContext> clientContext = new AtomicReference<>();
        String initCommand = "INIT#" + GameConfigCodecV1.encodeBase64(
                GameConfigCodecV1.fromSettings(
                        new NewGameTableDraft().snapshot(), false,
                        "native-reconnect"));
        GameTableFactory tables = context -> {
            (context.lobby().host() ? hostContext : clientContext).set(context);
            return new TableSession(emptyTable(context.lobby().localNickname()),
                    command -> { }, new TableEventBridge(), () -> {
                        if (context.lobby().host()) {
                            try {
                                return context.channel().broadcastFromHost(
                                        initCommand, null);
                            } catch (java.io.IOException failure) {
                                return CompletableFuture.failedFuture(failure);
                            }
                        }
                        return CompletableFuture.completedFuture(null);
                    });
        };
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(
                    temporary.resolve("reconnect-host"), tables);
                NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(
                    temporary.resolve("reconnect-client"), tables)) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true, "Invitado", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2);
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                host.tableSession().toCompletableFuture().get(2, TimeUnit.SECONDS)
                        .attach(immediateRenderer()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                client.tableSession().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);

                CopyOnWriteArrayList<String> hostInbound
                        = new CopyOnWriteArrayList<>();
                CopyOnWriteArrayList<String> clientInbound
                        = new CopyOnWriteArrayList<>();
                hostContext.get().channel().subscribe(inbound ->
                        hostInbound.add(inbound.command()));
                clientContext.get().channel().subscribe(inbound ->
                        clientInbound.add(inbound.command()));
                await(() -> clientInbound.contains(initCommand));

                closeClientSocket(client);
                CompletionStage<Void> queued = clientContext.get().channel()
                        .sendToHost("ACTION#survives-reconnect");
                queued.toCompletableFuture().get(12, TimeUnit.SECONDS);
                await(() -> hostContext.get().channel()
                        .peerReconnectionCount("Invitado") == 1
                        && clientContext.get().channel()
                                .peerReconnectionCount("Anfitrion") == 1);
                await(() -> hostInbound.stream().filter(
                        "ACTION#survives-reconnect"::equals).count() == 1L);

                assertEquals(1, hostContext.get().channel()
                        .forceReconnectRemotePeers());
                await(() -> hostContext.get().channel()
                        .peerReconnectionCount("Invitado") == 2
                        && clientContext.get().channel()
                                .peerReconnectionCount("Anfitrion") == 2);
                assertThrows(IllegalStateException.class,
                        () -> clientContext.get().channel()
                                .forceReconnectRemotePeers(),
                        "only the authenticated host may replace peer sockets");

                hostContext.get().channel().sendFromHost("Invitado",
                        "PAUSE#0#after-reconnect").toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> clientInbound.stream().filter(
                        "PAUSE#0#after-reconnect"::equals).count() == 1L);
                assertEquals(1L, hostInbound.stream().filter(
                        "ACTION#survives-reconnect"::equals).count());
                assertEquals(1L, clientInbound.stream().filter(
                        "PAUSE#0#after-reconnect"::equals).count());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private static TableSnapshot emptyTable(String nickname) {
        return new TableSnapshot(0L, nickname, TableSnapshot.Street.WAITING,
                0d, "", false, List.of(), List.of());
    }

    private DatabaseService statsDatabase(String name) throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve(name).toString());
        database.start();
        new CoreGameDatabase(database);
        return database;
    }

    private static void seedFinishedGame(DatabaseService database, String ugi)
            throws Exception {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "INSERT INTO game(start,end,server,players,buyin,sb,"
                    + "blinds_time,rebuy,blinds_time_type,ugi,local,private) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setLong(1, 1_000L);
                statement.setLong(2, 2_000L);
                statement.setString(3, "server");
                statement.setString(4, "");
                statement.setInt(5, 10);
                statement.setDouble(6, 0.1d);
                statement.setInt(7, 5);
                statement.setInt(8, 1);
                statement.setInt(9, 0);
                statement.setString(10, ugi);
                statement.setInt(11, 1);
                statement.setInt(12, 0);
                statement.executeUpdate();
            }
        }
    }

    private static boolean hasGame(DatabaseService database, String ugi) {
        return importedFrom(database, ugi) != null;
    }

    private static String importedFrom(DatabaseService database, String ugi) {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement("SELECT imported_from FROM game WHERE ugi=?")) {
                statement.setString(1, ugi);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getString(1) : null;
                }
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static int gameCount(DatabaseService database) {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection()
                    .prepareStatement("SELECT COUNT(*) FROM game");
                    ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static TableRenderer immediateRenderer() {
        return new TableRenderer() {
            @Override public CompletionStage<Void> open(TableSnapshot initialState) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public CompletionStage<Void> render(TableVisualEvent event) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void close() { }
        };
    }

    private static NewGameRequest request(boolean joining, String nickname, int port) {
        NewGameConnectionDraft.Submission connection = new NewGameConnectionDraft.Submission(
                joining ? NewGameConnectionDraft.Mode.JOIN : NewGameConnectionDraft.Mode.CREATE,
                nickname, "", "127.0.0.1", Integer.toString(port), null,
                false, false, null);
        return new NewGameRequest(connection,
                joining ? null : new NewGameTableDraft().snapshot());
    }

    private static byte[] validVoiceWav() throws Exception {
        byte[] pcm = new byte[16_000 * 2 / 5];
        for (int index = 0; index + 1 < pcm.length; index += 2) {
            short sample = (short) (Math.sin(index / 11d) * 10_000);
            pcm[index] = (byte) sample;
            pcm[index + 1] = (byte) (sample >>> 8);
        }
        AudioFormat source = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                16_000f, 16, 1, 2, 16_000f, false);
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.ULAW,
                16_000f, 8, 1, 1, 16_000f, false);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (AudioInputStream input = new AudioInputStream(
                new ByteArrayInputStream(pcm), source, pcm.length / 2);
                AudioInputStream ulaw = AudioSystem.getAudioInputStream(target, input)) {
            AudioSystem.write(ulaw, AudioFileFormat.Type.WAVE, encoded);
        }
        return encoded.toByteArray();
    }

    private static void closeClientSocket(LobbySession client) throws Exception {
        Object transport = field(client, "resource");
        Object connection = field(transport, "serverConnection");
        Object generation = field(connection, "generation");
        ((Socket) field(generation, "socket")).close();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        while (result.getCause() != null) result = result.getCause();
        return result;
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CompletionException(interrupted);
        }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
    }

    private static final class TrackingIdentityTrust
            implements IdentityTrustStore {
        private final AtomicReference<String> observed = new AtomicReference<>();

        @Override public Observation observe(String nickname, byte[] publicKey) {
            assertEquals(32, publicKey.length);
            observed.set(nickname);
            return Observation.NEW;
        }
        @Override public boolean markVerified(String nickname, byte[] publicKey) {
            return false;
        }
        @Override public boolean isVerified(String nickname, byte[] publicKey) {
            return false;
        }
    }
}
