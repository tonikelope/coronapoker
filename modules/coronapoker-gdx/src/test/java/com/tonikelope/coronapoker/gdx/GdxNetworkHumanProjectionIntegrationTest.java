package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.CoreGameTableFactory;
import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.AutoActionResolver;
import com.tonikelope.coronapoker.core.game.GameCinematicAssets;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameLogSink;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a real authenticated human game through the same renderer-neutral view
 * projection consumed by {@link CoronaPokerGdxTable}. Core-only network tests
 * deliberately use lightweight event collectors, so they cannot detect a GDX
 * projection that rejects an ordered event or loses the active local turn.
 */
class GdxNetworkHumanProjectionIntegrationTest {

    @TempDir Path temporary;

    private static int autoTarget(int selection, ActionControlState controls,
            boolean preflop, double bigBlind, boolean autoCallEnabled,
            double autoCallMaximum) {
        AutoActionResolver.QueuedAction queued = switch (selection) {
            case 1 -> AutoActionResolver.QueuedAction.FOLD_OR_CHECK;
            case 2 -> AutoActionResolver.QueuedAction.CHECK_OR_CALL;
            default -> AutoActionResolver.QueuedAction.NONE;
        };
        return switch (AutoActionResolver.resolve(queued, controls, preflop,
                bigBlind, autoCallEnabled, autoCallMaximum)) {
            case FOLD -> 1;
            case CHECK_OR_CALL -> 2;
            case ALL_IN -> 6;
            case NONE -> 0;
        };
    }

    @Test
    void nativeGdxAllInButtonArmsBeforeSubmittingTheRealCommand() {
        TableSnapshot snapshot = new TableSnapshot(1L, "Anfitrion",
                TableSnapshot.Street.PREFLOP, 0.3d, "Anfitrion", false,
                List.of(playerSnapshot("Anfitrion"),
                        playerSnapshot("Invitado")), List.of());
        GdxTableViewState state = new GdxTableViewState(snapshot);
        state.apply(new TableVisualEvent.ActionControls(1L,
                ActionControlState.forTurn(0.2d, 0.1d, 0.1d,
                        0.1d, 0.2d, 10d, 2, true, 0)));
        List<TableCommand> submitted = new CopyOnWriteArrayList<>();
        CoronaPokerGdxTable table = new CoronaPokerGdxTable(60, state,
                submitted::add, () -> { }, new GdxGameLogSink(), null);

        assertTrue(table.activateAllInAction());
        assertEquals(0, submitted.size(),
                "the first native ALL-IN activation must only arm the button");
        assertTrue(table.activateAllInAction());
        assertEquals(1, submitted.size());
        assertTrue(submitted.get(0) instanceof TableCommand.AllIn);
    }

    @Test
    void tableChatMediaCrossTheRealNetworkDuringAGame()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("chat-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("chat-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("chat-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("chat-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(request(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer hostRenderer = new ProjectionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer clientRenderer = new ProjectionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                try (GdxTableChatSession hostChat =
                        new GdxTableChatSession(host);
                     GdxTableChatSession clientChat =
                        new GdxTableChatSession(client)) {
                    String image = "https://example.invalid/reaccion.gif";
                    String wireImage = "imgs://example.invalid/reaccion.gif";
                    byte[] voice = validGdxVoiceWav();
                    String wireVoice = Base64.getEncoder().encodeToString(voice);
                    hostChat.sendText("hola #12#").toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    clientChat.sendImage(image).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    hostChat.sendVoice(voice).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    assertThrows(java.util.concurrent.ExecutionException.class,
                            () -> clientChat.sendVoice(new byte[]{1, 2, 3})
                                    .toCompletableFuture()
                                    .get(5, TimeUnit.SECONDS));

                    await(() -> containsChat(hostChat,
                                    LobbyChatMessage.Type.TEXT, "hola #12#")
                                    && containsChat(clientChat,
                                    LobbyChatMessage.Type.TEXT, "hola #12#")
                            && containsChat(hostChat,
                                    LobbyChatMessage.Type.IMAGE, wireImage)
                            && containsChat(clientChat,
                                    LobbyChatMessage.Type.IMAGE, wireImage)
                            && containsChat(hostChat,
                                    LobbyChatMessage.Type.VOICE, wireVoice)
                            && containsChat(clientChat,
                                    LobbyChatMessage.Type.VOICE, wireVoice),
                            Duration.ofSeconds(5));
                }
                hostRenderer.releaseHeldAction();
                clientRenderer.releaseHeldAction();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private static byte[] validGdxVoiceWav() throws Exception {
        byte[] pcm = new byte[(int) GdxVoiceRecorder.SAMPLE_RATE * 2 / 5];
        for (int index = 0; index + 1 < pcm.length; index += 2) {
            short sample = (short) (Math.sin(index / 11d) * 10_000);
            pcm[index] = (byte) sample;
            pcm[index + 1] = (byte) (sample >>> 8);
        }
        return GdxVoiceRecorder.encodePcm(pcm);
    }

    private static boolean containsChat(GdxTableChatSession chat,
            LobbyChatMessage.Type type, String content) {
        return chat.history().stream().anyMatch(message ->
                message.type() == type && message.content().equals(content));
    }

    @Test
    void nativeGdxCheckCallControlsCompleteARealTwoHumanHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("native-action-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("native-action-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("native-action-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("native-action-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(request(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer hostRenderer = new ProjectionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession()
                        .toCompletableFuture().get(5, TimeUnit.SECONDS);
                ProjectionRenderer clientRenderer
                        = new ProjectionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                boolean hostReleased = false;
                boolean clientReleased = false;
                Instant deadline = Instant.now().plusSeconds(16);
                while ((!hostReleased || !clientReleased)
                        && Instant.now().isBefore(deadline)) {
                    if (!hostReleased && hostRenderer.heldAction.get()) {
                        hostRenderer.releaseHeldActionThroughNativeGdx();
                        hostReleased = true;
                    }
                    if (!clientReleased && clientRenderer.heldAction.get()) {
                        clientRenderer.releaseHeldActionThroughNativeGdx();
                        clientReleased = true;
                    }
                    Thread.sleep(10L);
                }
                assertTrue(hostReleased && clientReleased,
                        "both human turns must cross the native GDX check/call "
                                + "control during the hand");
                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(18));
                hostRenderer.assertComplete();
                clientRenderer.assertComplete();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void nativeGdxPauseResumeCompletesARealTwoHumanHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("client"), clientDatabase)) {
            LobbySession host = hostGateway.open(request(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer hostRenderer = new ProjectionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer clientRenderer = new ProjectionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.heldAction.get()
                                || clientRenderer.heldAction.get(),
                        Duration.ofSeconds(6));
                TableSession pausingTable = hostRenderer.heldAction.get()
                        ? hostTable : clientTable;
                ProjectionRenderer pausingRenderer = hostRenderer.heldAction.get()
                        ? hostRenderer : clientRenderer;
                CoronaPokerGdxTable pauseTable = new CoronaPokerGdxTable(60,
                        new GdxTableViewState(
                                pausingRenderer.state.get().snapshot()),
                        pausingTable.commands(), () -> { },
                        new GdxGameLogSink(), null);
                assertTrue(pauseTable.togglePauseAction(),
                        "the native GDX pause control did not submit");
                await(() -> hostRenderer.sawPaused.get()
                                && clientRenderer.sawPaused.get(),
                        Duration.ofSeconds(6));
                assertTrue(pauseTable.togglePauseAction(),
                        "the native GDX resume control did not submit");
                await(() -> hostRenderer.sawResumed.get()
                                && clientRenderer.sawResumed.get(),
                        Duration.ofSeconds(6));
                hostRenderer.releaseHeldActionThroughNativeGdx();
                clientRenderer.releaseHeldActionThroughNativeGdx();

                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(18));
                hostRenderer.assertComplete();
                clientRenderer.assertComplete();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void nativeGdxFoldedLocalStillSeesRemoteMonteCarloRevealsAndShowdownResults()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("folded-host.sqlite").toString());
        DatabaseService firstDatabase = new DatabaseService(
                temporary.resolve("folded-first.sqlite").toString());
        DatabaseService secondDatabase = new DatabaseService(
                temporary.resolve("folded-second.sqlite").toString());
        hostDatabase.start();
        firstDatabase.start();
        secondDatabase.start();
        try (hostDatabase; firstDatabase; secondDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("folded-host"), hostDatabase);
             NetworkLobbyGateway firstGateway = gateway(
                     temporary.resolve("folded-first"), firstDatabase);
             NetworkLobbyGateway secondGateway = gateway(
                     temporary.resolve("folded-second"), secondDatabase)) {
            LobbySession host = hostGateway.open(request(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(request(true,
                    "Invitado1", port)).get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(request(true,
                    "Invitado2", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> List.of(host, first, second).stream().allMatch(
                                session -> session.snapshot().participants()
                                        .size() == 3),
                        Duration.ofSeconds(10));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                GdxTableViewState hostState = new GdxTableViewState(
                        hostTable.initialState());
                CoronaPokerGdxTable hostProductTable = new CoronaPokerGdxTable(
                        60, hostState, hostTable.commands(), () -> { },
                        new GdxGameLogSink(), null, host);
                FoldedObserverProjectionRenderer hostRenderer
                        = new FoldedObserverProjectionRenderer(hostTable, true,
                                hostState, hostProductTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession firstTable = first.tableSession().toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                GdxTableViewState firstState = new GdxTableViewState(
                        firstTable.initialState());
                CoronaPokerGdxTable firstProductTable = new CoronaPokerGdxTable(
                        60, firstState, firstTable.commands(), () -> { },
                        new GdxGameLogSink(), null, first);
                FoldedObserverProjectionRenderer firstRenderer
                        = new FoldedObserverProjectionRenderer(firstTable, false,
                                firstState, firstProductTable);
                firstTable.attach(firstRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession secondTable = second.tableSession().toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                GdxTableViewState secondState = new GdxTableViewState(
                        secondTable.initialState());
                CoronaPokerGdxTable secondProductTable = new CoronaPokerGdxTable(
                        60, secondState, secondTable.commands(), () -> { },
                        new GdxGameLogSink(), null, second);
                FoldedObserverProjectionRenderer secondRenderer
                        = new FoldedObserverProjectionRenderer(secondTable, false,
                                secondState, secondProductTable);
                secondTable.attach(secondRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closed.get()
                                && firstRenderer.closed.get()
                                && secondRenderer.closed.get(),
                        Duration.ofSeconds(25));
                hostRenderer.assertFoldedObserverComplete();
                firstRenderer.assertPlayingProjectionComplete();
                secondRenderer.assertPlayingProjectionComplete();
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    @Test
    void networkTimeoutStopsTheGdxTimerAndAdvancesBothTables()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("timeout-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("timeout-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("timeout-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("timeout-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(timeoutRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(timeoutRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer hostRenderer = new ProjectionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ProjectionRenderer clientRenderer = new ProjectionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.heldAction.get()
                                || clientRenderer.heldAction.get(),
                        Duration.ofSeconds(8));
                await(() -> hostRenderer.sawTimeoutCue.get()
                                || clientRenderer.sawTimeoutCue.get(),
                        Duration.ofSeconds(15));
                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(10));

                hostRenderer.assertTimeoutComplete();
                clientRenderer.assertTimeoutComplete();
                assertTrue(hostRenderer.sawLocalControls.get()
                                || clientRenderer.sawLocalControls.get(),
                        "the timed-out decision must belong to one local GDX HUD");
                assertTrue(hostRenderer.sawHurryCue.get()
                                || clientRenderer.sawHurryCue.get());
                assertTrue(hostRenderer.sawHurryStop.get()
                                || clientRenderer.sawHurryStop.get());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void networkAllInCinematicBlocksTheFollowingTurnInBothGdxProjections()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("all-in-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("all-in-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = cinematicGateway(
                     temporary.resolve("all-in-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = cinematicGateway(
                     temporary.resolve("all-in-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(request(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AllInProjectionRenderer hostRenderer
                        = new AllInProjectionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AllInProjectionRenderer clientRenderer
                        = new AllInProjectionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.cinematicPending.get()
                                && clientRenderer.cinematicPending.get(),
                        Duration.ofSeconds(8));
                Thread.sleep(250L);
                assertFalse(hostRenderer.sawControlsDuringCinematic.get(),
                        "the host GDX turn overtook its all-in cinematic");
                assertFalse(clientRenderer.sawControlsDuringCinematic.get(),
                        "the client GDX turn overtook its all-in cinematic");
                hostRenderer.releaseCinematic();
                clientRenderer.releaseCinematic();

                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(20));
                hostRenderer.assertComplete();
                clientRenderer.assertComplete();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void nativeGdxAllInRunItTwiceCompletesBothBoardsAndConservesBalances()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("rit-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("rit-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        AtomicInteger runItTwiceVotes = new AtomicInteger();
        AtomicInteger nativeDialogResolutions = new AtomicInteger();
        AtomicReference<CoronaPokerGdxTable> hostGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> clientGdx = new AtomicReference<>();
        GameDecisionSink hostDecisions = acceptingRunItTwiceDecisions(
                hostGdx, runItTwiceVotes);
        GameDecisionSink clientDecisions = acceptingRunItTwiceDecisions(
                clientGdx, runItTwiceVotes);
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("rit-host"), hostDatabase,
                     hostDecisions);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("rit-client"), clientDatabase,
                     clientDecisions)) {
            LobbySession host = hostGateway.open(runItTwiceRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(runItTwiceRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState hostState = new GdxTableViewState(
                        hostTable.initialState());
                CoronaPokerGdxTable hostProductTable = new CoronaPokerGdxTable(
                        60, hostState, hostTable.commands(), () -> { },
                        new GdxGameLogSink(), null, host);
                hostGdx.set(hostProductTable);
                RunItTwiceProjectionRenderer hostRenderer
                        = new RunItTwiceProjectionRenderer(hostTable,
                                hostState, hostProductTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState clientState = new GdxTableViewState(
                        clientTable.initialState());
                CoronaPokerGdxTable clientProductTable = new CoronaPokerGdxTable(
                        60, clientState, clientTable.commands(), () -> { },
                        new GdxGameLogSink(), null, client);
                clientGdx.set(clientProductTable);
                RunItTwiceProjectionRenderer clientRenderer
                        = new RunItTwiceProjectionRenderer(clientTable,
                                clientState, clientProductTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                driveNativeGdxDialogsUntilClosed(hostProductTable,
                        clientProductTable, hostRenderer.closed,
                        clientRenderer.closed, nativeDialogResolutions,
                        Duration.ofSeconds(25));
                Thread.sleep(250L);
                assertEquals(2, host.snapshot().participants().size(),
                        "closing the table must not evict the authenticated client");
                assertEquals(2, client.snapshot().participants().size(),
                        "closing the table must preserve the final-screen roster");
                assertEquals(2, runItTwiceVotes.get(),
                        "every GDX human seat must accept run-it-twice");
                assertEquals(2, nativeDialogResolutions.get(),
                        "every RIT vote must use its real GDX table dialog");
                assertFalse(hostProductTable.hasActiveDialog());
                assertFalse(clientProductTable.hasActiveDialog());
                hostRenderer.assertComplete();
                clientRenderer.assertComplete();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void rejectedRunItTwiceFallsBackToOneBoardOnBothGdxProjections()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("rit-decline-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("rit-decline-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        AtomicInteger acceptedVotes = new AtomicInteger();
        AtomicInteger declinedVotes = new AtomicInteger();
        GameDecisionSink hostDecisions = fixedRunItTwiceDecisions(
                GameDecisionSink.VOTE_RUN_IT_TWICE, acceptedVotes);
        GameDecisionSink clientDecisions = fixedRunItTwiceDecisions(
                GameDecisionSink.VOTE_NORMAL, declinedVotes);
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("rit-decline-host"), hostDatabase,
                     hostDecisions);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("rit-decline-client"), clientDatabase,
                     clientDecisions)) {
            LobbySession host = hostGateway.open(runItTwiceRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(runItTwiceRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                RunItTwiceProjectionRenderer hostRenderer
                        = new RunItTwiceProjectionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                RunItTwiceProjectionRenderer clientRenderer
                        = new RunItTwiceProjectionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(25));
                assertEquals(1, acceptedVotes.get());
                assertEquals(1, declinedVotes.get());
                hostRenderer.assertSingleBoardComplete();
                clientRenderer.assertSingleBoardComplete();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void allInRebuyCompletesFiveHandsAndCarriesARebuyForward()
            throws Exception {
        assertNetworkRebuy(true, 5);
    }

    @Test
    void nativeGdxManualRebuyKeepsBothNetworkTablesAliveForTheNextHand()
            throws Exception {
        assertNetworkRebuy(false, 3);
    }

    @Test
    void nativeGdxSpectatorChoiceReleasesTheRealNetworkDealerEvenWhenAudioCallbackIsLost()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("spectator-choice-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("spectator-choice-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        AtomicInteger spectatorChoices = new AtomicInteger();
        AtomicInteger choiceFrames = new AtomicInteger();
        AtomicInteger finalFrames = new AtomicInteger();
        AtomicReference<CoronaPokerGdxTable> hostGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> clientGdx = new AtomicReference<>();
        GameDecisionSink hostDecisions = spectatorDecisions(hostGdx,
                choiceFrames, finalFrames);
        GameDecisionSink clientDecisions = spectatorDecisions(clientGdx,
                choiceFrames, finalFrames);
        GamePresentationSettings settings = rebuySettings(false);
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("spectator-choice-host"), hostDatabase,
                     hostDecisions, settings);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("spectator-choice-client"), clientDatabase,
                     clientDecisions, settings)) {
            LobbySession host = hostGateway.open(rebuyRequest(false,
                    "Anfitrion", port, 3)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(rebuyRequest(true,
                    "Invitado", port, 3)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState hostState = new GdxTableViewState(
                        hostTable.initialState());
                CoronaPokerGdxTable hostProductTable = new CoronaPokerGdxTable(
                        60, hostState, hostTable.commands(), () -> { },
                        new GdxGameLogSink(), null, host);
                hostGdx.set(hostProductTable);
                RebuyProjectionRenderer hostRenderer
                        = new RebuyProjectionRenderer(hostTable, 3, hostState,
                                hostProductTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState clientState = new GdxTableViewState(
                        clientTable.initialState());
                CoronaPokerGdxTable clientProductTable = new CoronaPokerGdxTable(
                        60, clientState, clientTable.commands(), () -> { },
                        new GdxGameLogSink(), null, client);
                clientGdx.set(clientProductTable);
                RebuyProjectionRenderer clientRenderer
                        = new RebuyProjectionRenderer(clientTable, 3,
                                clientState, clientProductTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> choiceFrames.get() >= 1,
                        Duration.ofSeconds(60));
                CoronaPokerGdxTable spectatorTable
                        = java.util.stream.Stream.of(hostGdx.get(),
                                clientGdx.get())
                                .filter(Objects::nonNull)
                                .filter(CoronaPokerGdxTable::hasActiveDialog)
                                .findFirst().orElseThrow(() ->
                                new AssertionError(
                                        "the presented GDX spectator dialog was not active"));
                assertTrue(spectatorTable.resolveActiveDialogChoice(false),
                        "the real GDX spectator button did not resolve");
                spectatorTable.advanceDialogState();
                spectatorChoices.incrementAndGet();
                await(() -> spectatorChoices.get() >= 1,
                        Duration.ofSeconds(5));
                await(() -> java.util.stream.Stream.of(hostRenderer,
                                clientRenderer)
                                .map(renderer -> renderer.state.get())
                                .filter(state -> state != null)
                                .flatMap(state -> state.snapshot().players().stream())
                                .anyMatch(TableSnapshot.PlayerSnapshot::spectator),
                        Duration.ofSeconds(10));
                assertTrue(finalFrames.get() >= 1,
                        "the native final GAME OVER frame was never presented");
                await(() -> {
                    hostProductTable.advanceDialogState();
                    clientProductTable.advanceDialogState();
                    return !hostProductTable.hasActiveDialog()
                            && !clientProductTable.hasActiveDialog();
                }, Duration.ofSeconds(5));

                if (!hostRenderer.closed.get() || !clientRenderer.closed.get()) {
                    hostTable.commands().submit(new TableCommand.StopGame());
                }
                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(20));
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private static GameDecisionSink spectatorDecisions(
            AtomicReference<CoronaPokerGdxTable> table,
            AtomicInteger choiceFrames,
            AtomicInteger finalFrames) {
        return new GdxGameDecisionSink(GameText.keys(), dialog -> {
            if (!dialog.isGameOver()) {
                dialog.accept();
                return;
            }
            CoronaPokerGdxTable productTable = table.get();
            assertNotNull(productTable,
                    "the network decision arrived before its GDX table opened");
            productTable.showDialog(dialog);
            if (dialog.isExternallyControlled()) {
                finalFrames.incrementAndGet();
            } else if (dialog.showsNegative() && dialog.showsPositive()) {
                choiceFrames.incrementAndGet();
            }
        }, ignored -> { }, cue ->
                cue == GdxGameDecisionSink.GameOverAudioCue.SPECTATOR
                        ? new CompletableFuture<>()
                        : CompletableFuture.completedFuture(null),
                20, TimeUnit.MILLISECONDS);
    }

    private static TableSnapshot.PlayerSnapshot playerSnapshot(
            String nickname) {
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                true, false, false, false, -1, -1, 0, 0L, false,
                TableSnapshot.Position.NONE, "", "", List.of());
    }

    @Test
    void persistentGdxAutoActionSurvivesARealNetworkHandBoundary()
            throws Exception {
        assertNetworkAutoActionPersistence(true);
    }

    @Test
    void nonPersistentGdxAutoActionIsClearedAtARealNetworkHandBoundary()
            throws Exception {
        assertNetworkAutoActionPersistence(false);
    }

    private void assertNetworkAutoActionPersistence(boolean persistent)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String prefix = persistent ? "auto-mode-persistent"
                : "auto-mode-one-hand";
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve(prefix + "-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve(prefix + "-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        GamePresentationSettings settings = autoActionSettings(persistent);
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve(prefix + "-host"), hostDatabase,
                     GameDecisionSink.noop(), settings);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve(prefix + "-client"), clientDatabase,
                     GameDecisionSink.noop(), settings)) {
            LobbySession host = hostGateway.open(autoActionRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(autoActionRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                PersistentAutoActionRenderer hostRenderer
                        = new PersistentAutoActionRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                PersistentAutoActionRenderer clientRenderer
                        = new PersistentAutoActionRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(25));
                boolean crossedBoundary = hostRenderer.provesPersistence()
                        || clientRenderer.provesPersistence();
                assertEquals(persistent, crossedBoundary,
                        persistent
                                ? "no native GDX AUTO choice executed on both sides of a hand boundary"
                                : "a non-persistent GDX AUTO choice leaked into a later hand");
                assertEquals(3, hostRenderer.endedHands.get());
                assertEquals(3, clientRenderer.endedHands.get());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void gdxAutoCallHonorsItsCapAndSurvivesARealStreetChange()
            throws Exception {
        runAutoCallMatrix(false);
    }

    @Test
    void gdxAutoCallUsesAllInWhenTheRealCallConsumesTheStack()
            throws Exception {
        runAutoCallMatrix(true);
    }

    private void runAutoCallMatrix(boolean forceAllIn) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String prefix = forceAllIn ? "auto-call-allin" : "auto-call-cap";
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve(prefix + "-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve(prefix + "-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        GamePresentationSettings settings = autoActionSettings();
        AutoCallMatrix coordinator = new AutoCallMatrix(forceAllIn);
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve(prefix + "-host"), hostDatabase,
                     GameDecisionSink.noop(), settings);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve(prefix + "-client"), clientDatabase,
                     GameDecisionSink.noop(), settings)) {
            LobbySession host = hostGateway.open(autoCallRequest(false,
                    "Anfitrion", port, forceAllIn ? 1 : 2,
                    forceAllIn ? 2 : 10)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(autoCallRequest(true,
                    "Invitado", port, forceAllIn ? 1 : 2,
                    forceAllIn ? 2 : 10)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallMatrixRenderer hostRenderer
                        = new AutoCallMatrixRenderer(hostTable, coordinator);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallMatrixRenderer clientRenderer
                        = new AutoCallMatrixRenderer(clientTable, coordinator);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(25));
                if (forceAllIn) {
                    assertTrue(coordinator.sawAllInAutoCall.get(),
                            "AUTO Call never selected the real all-in call");
                } else {
                    assertTrue(coordinator.sawCappedRejection.get(),
                            "AUTO Call never rejected a real call above its cap");
                    assertTrue(coordinator.sawAcceptedCall.get(),
                            "AUTO Call never accepted a real call within its cap");
                    assertTrue(coordinator.sawFreeCheckAfterCall.get(),
                            "AUTO selection did not survive into a later street");
                }
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private void assertNetworkRebuy(boolean automatic, int hands)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String prefix = automatic ? "automatic-rebuy" : "manual-rebuy";
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve(prefix + "-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve(prefix + "-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        AtomicInteger rebuyChoices = new AtomicInteger();
        AtomicInteger gameOverChoiceFrames = new AtomicInteger();
        AtomicInteger nativeDialogResolutions = new AtomicInteger();
        AtomicReference<CoronaPokerGdxTable> hostGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> clientGdx = new AtomicReference<>();
        GameDecisionSink hostDecisions = automatic
                ? acceptingRebuyDecisions(true, rebuyChoices)
                : nativeGdxRebuyDecisions(hostGdx, gameOverChoiceFrames,
                        rebuyChoices);
        GameDecisionSink clientDecisions = automatic
                ? acceptingRebuyDecisions(true, rebuyChoices)
                : nativeGdxRebuyDecisions(clientGdx, gameOverChoiceFrames,
                        rebuyChoices);
        GamePresentationSettings settings = rebuySettings(automatic);
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve(prefix + "-host"), hostDatabase,
                     hostDecisions, settings);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve(prefix + "-client"), clientDatabase,
                     clientDecisions, settings)) {
            LobbySession host = hostGateway.open(rebuyRequest(false,
                    "Anfitrion", port, hands)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(rebuyRequest(true,
                    "Invitado", port, hands)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState hostState = automatic ? null
                        : new GdxTableViewState(hostTable.initialState());
                CoronaPokerGdxTable hostProductTable = automatic ? null
                        : new CoronaPokerGdxTable(60, hostState,
                                hostTable.commands(), () -> { },
                                new GdxGameLogSink(), null, host);
                hostGdx.set(hostProductTable);
                RebuyProjectionRenderer hostRenderer
                        = new RebuyProjectionRenderer(hostTable, hands,
                                hostState, hostProductTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState clientState = automatic ? null
                        : new GdxTableViewState(clientTable.initialState());
                CoronaPokerGdxTable clientProductTable = automatic ? null
                        : new CoronaPokerGdxTable(60, clientState,
                                clientTable.commands(), () -> { },
                                new GdxGameLogSink(), null, client);
                clientGdx.set(clientProductTable);
                RebuyProjectionRenderer clientRenderer
                        = new RebuyProjectionRenderer(clientTable, hands,
                                clientState, clientProductTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                if (automatic) {
                    await(() -> hostRenderer.closed.get()
                                    && clientRenderer.closed.get(),
                            Duration.ofSeconds(90));
                } else {
                    driveNativeGdxDialogsUntilClosed(hostProductTable,
                            clientProductTable, hostRenderer.closed,
                            clientRenderer.closed, nativeDialogResolutions,
                            Duration.ofSeconds(90));
                    assertTrue(gameOverChoiceFrames.get() >= 1,
                            "the real GDX GAME OVER choice was never presented");
                    assertTrue(nativeDialogResolutions.get() >= 2,
                            "GAME OVER and REBUY must both be resolved through "
                                    + "the real GDX table dialog state machine");
                    assertFalse(hostProductTable.hasActiveDialog(),
                            "host GDX table retained a blocking dialog");
                    assertFalse(clientProductTable.hasActiveDialog(),
                            "client GDX table retained a blocking dialog");
                }
                assertTrue(rebuyChoices.get() >= 1,
                        "one busted human must complete the rebuy path");
                hostRenderer.assertComplete();
                clientRenderer.assertComplete();
                Set<Integer> allInHands = new HashSet<>(
                        hostRenderer.allInHands);
                allInHands.addAll(clientRenderer.allInHands);
                for (int hand = 1; hand <= hands; hand++) {
                    assertTrue(allInHands.contains(hand),
                            "no real GDX all-in control was submitted in hand "
                                    + hand);
                }
                assertEquals(hostRenderer.balancesByNickname(),
                        clientRenderer.balancesByNickname(),
                        "both GDX peers must close with identical balances");
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void nativeGdxStraddlePostRotatesAllThreeHumansAcrossThreeHands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("straddle-host.sqlite").toString());
        DatabaseService firstDatabase = new DatabaseService(
                temporary.resolve("straddle-first.sqlite").toString());
        DatabaseService secondDatabase = new DatabaseService(
                temporary.resolve("straddle-second.sqlite").toString());
        hostDatabase.start();
        firstDatabase.start();
        secondDatabase.start();
        AtomicInteger choices = new AtomicInteger();
        CopyOnWriteArrayList<String> straddlers = new CopyOnWriteArrayList<>();
        AtomicReference<CoronaPokerGdxTable> hostGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> firstGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> secondGdx = new AtomicReference<>();
        try (hostDatabase; firstDatabase; secondDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                      temporary.resolve("straddle-host"), hostDatabase,
                       acceptingNativeGdxStraddleDecisions("Anfitrion",
                               hostGdx, choices, straddlers));
             NetworkLobbyGateway firstGateway = gateway(
                      temporary.resolve("straddle-first"), firstDatabase,
                       acceptingNativeGdxStraddleDecisions("Invitado1",
                               firstGdx, choices, straddlers));
             NetworkLobbyGateway secondGateway = gateway(
                      temporary.resolve("straddle-second"), secondDatabase,
                       acceptingNativeGdxStraddleDecisions("Invitado2",
                               secondGdx, choices, straddlers))) {
            LobbySession host = hostGateway.open(straddleRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(straddleRequest(true,
                    "Invitado1", port)).get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(straddleRequest(true,
                    "Invitado2", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 3,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState hostState = new GdxTableViewState(
                        hostTable.initialState());
                CoronaPokerGdxTable hostProductTable = new CoronaPokerGdxTable(
                        60, hostState, hostTable.commands(), () -> { },
                        new GdxGameLogSink(), null, host);
                hostGdx.set(hostProductTable);
                StraddleProjectionRenderer hostRenderer
                        = new StraddleProjectionRenderer(hostTable, hostState,
                                hostProductTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession firstTable = first.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState firstState = new GdxTableViewState(
                        firstTable.initialState());
                CoronaPokerGdxTable firstProductTable = new CoronaPokerGdxTable(
                        60, firstState, firstTable.commands(), () -> { },
                        new GdxGameLogSink(), null, first);
                firstGdx.set(firstProductTable);
                StraddleProjectionRenderer firstRenderer
                        = new StraddleProjectionRenderer(firstTable, firstState,
                                firstProductTable);
                firstTable.attach(firstRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession secondTable = second.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxTableViewState secondState = new GdxTableViewState(
                        secondTable.initialState());
                CoronaPokerGdxTable secondProductTable = new CoronaPokerGdxTable(
                        60, secondState, secondTable.commands(), () -> { },
                        new GdxGameLogSink(), null, second);
                secondGdx.set(secondProductTable);
                StraddleProjectionRenderer secondRenderer
                        = new StraddleProjectionRenderer(secondTable,
                                secondState, secondProductTable);
                secondTable.attach(secondRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closed.get()
                                && firstRenderer.closed.get()
                                && secondRenderer.closed.get(),
                        Duration.ofSeconds(35));
                assertEquals(3, choices.get(),
                        "exactly one canonical UTG human must decide each hand");
                assertEquals(Set.of("Anfitrion", "Invitado1", "Invitado2"),
                        new HashSet<>(straddlers),
                        "the UTG straddle decision must rotate through every seat");
                for (StraddleProjectionRenderer renderer : List.of(
                        hostRenderer, firstRenderer, secondRenderer)) {
                    renderer.assertComplete();
                }
                List<StraddleProjectionRenderer> renderers = List.of(
                        hostRenderer, firstRenderer, secondRenderer);
                for (int hand = 1; hand <= straddlers.size(); hand++) {
                    String straddler = straddlers.get(hand - 1);
                    StraddleProjectionRenderer decidingRenderer = renderers.stream()
                            .filter(renderer -> renderer.localNickname.equals(straddler))
                            .findFirst().orElseThrow();
                    decidingRenderer.assertLocalStraddleOrdering(hand);
                }
                assertEquals(hostRenderer.balancesByNickname(),
                        firstRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        secondRenderer.balancesByNickname());
                assertFalse(hostProductTable.hasActiveDialog());
                assertFalse(firstProductTable.hasActiveDialog());
                assertFalse(secondProductTable.hasActiveDialog());
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    /**
     * Exact GDX homologue of Swing's {@code straddle-network-cut} scenario.
     * The accepted signed remote response is observed at the same canonical
     * dealer log point used by Swing's process scenario, and that client's
     * native transport generation is cut synchronously before the dealer can
     * release the deferred pocket-card cascade.
     */
    @Test
    void nativeGdxStraddleAcceptedResponseSurvivesReconnectBeforeDeferredPocketDelivery()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("straddle-cut-host.sqlite").toString());
        DatabaseService firstDatabase = new DatabaseService(
                temporary.resolve("straddle-cut-first.sqlite").toString());
        DatabaseService secondDatabase = new DatabaseService(
                temporary.resolve("straddle-cut-second.sqlite").toString());
        hostDatabase.start();
        firstDatabase.start();
        secondDatabase.start();
        AtomicInteger choices = new AtomicInteger();
        CopyOnWriteArrayList<String> straddlers = new CopyOnWriteArrayList<>();
        AtomicReference<CoronaPokerGdxTable> hostGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> firstGdx = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> secondGdx = new AtomicReference<>();
        AtomicReference<Map<String, LobbySession>> sessionsByNickname
                = new AtomicReference<>(Map.of());
        AtomicReference<String> cutNickname = new AtomicReference<>();
        AtomicReference<Throwable> cutFailure = new AtomicReference<>();
        AtomicBoolean cutStarted = new AtomicBoolean();
        Logger dealerLogger = Logger.getLogger(Crupier.class.getName());
        Handler cutOnAcceptedRemoteStraddle = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (!"QA STRADDLE_RESP_ACCEPTED nick={0} decision={1}"
                        .equals(record.getMessage())) {
                    return;
                }
                Object[] parameters = record.getParameters();
                if (parameters == null || parameters.length < 2
                        || !(parameters[0] instanceof String nickname)
                        || "Anfitrion".equals(nickname)
                        || !cutStarted.compareAndSet(false, true)) {
                    return;
                }
                try {
                    LobbySession target = sessionsByNickname.get().get(nickname);
                    if (target == null) {
                        throw new IllegalStateException(
                                "accepted straddler has no bound GDX session: "
                                        + nickname);
                    }
                    closeNativeClientSocket(target);
                    cutNickname.set(nickname);
                } catch (Throwable failure) {
                    cutFailure.set(failure);
                }
            }

            @Override public void flush() { }
            @Override public void close() { }
        };
        cutOnAcceptedRemoteStraddle.setLevel(java.util.logging.Level.ALL);
        dealerLogger.addHandler(cutOnAcceptedRemoteStraddle);
        try {
            try (hostDatabase; firstDatabase; secondDatabase;
                 NetworkLobbyGateway hostGateway = gateway(
                         temporary.resolve("straddle-cut-host"), hostDatabase,
                         acceptingNativeGdxStraddleDecisions("Anfitrion",
                                 hostGdx, choices, straddlers));
                 NetworkLobbyGateway firstGateway = gateway(
                         temporary.resolve("straddle-cut-first"), firstDatabase,
                         acceptingNativeGdxStraddleDecisions("Invitado1",
                                 firstGdx, choices, straddlers));
                 NetworkLobbyGateway secondGateway = gateway(
                         temporary.resolve("straddle-cut-second"), secondDatabase,
                         acceptingNativeGdxStraddleDecisions("Invitado2",
                                 secondGdx, choices, straddlers))) {
                LobbySession host = hostGateway.open(straddleRequest(false,
                        "Anfitrion", port)).get(5, TimeUnit.SECONDS);
                LobbySession first = firstGateway.open(straddleRequest(true,
                        "Invitado1", port)).get(5, TimeUnit.SECONDS);
                LobbySession second = secondGateway.open(straddleRequest(true,
                        "Invitado2", port)).get(5, TimeUnit.SECONDS);
                sessionsByNickname.set(Map.of(
                        "Anfitrion", host,
                        "Invitado1", first,
                        "Invitado2", second));
                try {
                    await(() -> host.snapshot().participants().size() == 3,
                            Duration.ofSeconds(5));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);

                    TableSession hostTable = host.tableSession()
                            .toCompletableFuture().get(5, TimeUnit.SECONDS);
                    GdxTableViewState hostState = new GdxTableViewState(
                            hostTable.initialState());
                    CoronaPokerGdxTable hostProductTable
                            = new CoronaPokerGdxTable(60, hostState,
                                    hostTable.commands(), () -> { },
                                    new GdxGameLogSink(), null, host);
                    hostGdx.set(hostProductTable);
                    StraddleProjectionRenderer hostRenderer
                            = new StraddleProjectionRenderer(hostTable,
                                    hostState, hostProductTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession firstTable = first.tableSession()
                            .toCompletableFuture().get(5, TimeUnit.SECONDS);
                    GdxTableViewState firstState = new GdxTableViewState(
                            firstTable.initialState());
                    CoronaPokerGdxTable firstProductTable
                            = new CoronaPokerGdxTable(60, firstState,
                                    firstTable.commands(), () -> { },
                                    new GdxGameLogSink(), null, first);
                    firstGdx.set(firstProductTable);
                    StraddleProjectionRenderer firstRenderer
                            = new StraddleProjectionRenderer(firstTable,
                                    firstState, firstProductTable);
                    firstTable.attach(firstRenderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession secondTable = second.tableSession()
                            .toCompletableFuture().get(5, TimeUnit.SECONDS);
                    GdxTableViewState secondState = new GdxTableViewState(
                            secondTable.initialState());
                    CoronaPokerGdxTable secondProductTable
                            = new CoronaPokerGdxTable(60, secondState,
                                    secondTable.commands(), () -> { },
                                    new GdxGameLogSink(), null, second);
                    secondGdx.set(secondProductTable);
                    StraddleProjectionRenderer secondRenderer
                            = new StraddleProjectionRenderer(secondTable,
                                    secondState, secondProductTable);
                    secondTable.attach(secondRenderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);

                    await(() -> cutNickname.get() != null
                                    || cutFailure.get() != null,
                            Duration.ofSeconds(20));
                    assertEquals(null, cutFailure.get(),
                            "the accepted straddler transport cut must succeed");
                    String disconnected = cutNickname.get();
                    LobbySession disconnectedSession
                            = sessionsByNickname.get().get(disconnected);
                    await(() -> peerReconnectionCount(host, disconnected) == 1
                                    && peerReconnectionCount(disconnectedSession,
                                    "Anfitrion") == 1,
                            Duration.ofSeconds(25));

                    await(() -> hostRenderer.closed.get()
                                    && firstRenderer.closed.get()
                                    && secondRenderer.closed.get(),
                            Duration.ofSeconds(40));
                    assertEquals(3, choices.get());
                    assertEquals(Set.of("Anfitrion", "Invitado1", "Invitado2"),
                            new HashSet<>(straddlers));
                    List<StraddleProjectionRenderer> renderers = List.of(
                            hostRenderer, firstRenderer, secondRenderer);
                    for (StraddleProjectionRenderer renderer : renderers) {
                        renderer.assertComplete();
                    }
                    int cutHand = straddlers.indexOf(disconnected) + 1;
                    assertTrue(cutHand > 0,
                            "the cut peer must be the accepted remote straddler");
                    renderers.stream()
                            .filter(renderer -> renderer.localNickname.equals(
                                    disconnected))
                            .findFirst().orElseThrow()
                            .assertLocalStraddleOrdering(cutHand);
                    assertEquals(hostRenderer.balancesByNickname(),
                            firstRenderer.balancesByNickname());
                    assertEquals(hostRenderer.balancesByNickname(),
                            secondRenderer.balancesByNickname());
                    assertFalse(hostProductTable.hasActiveDialog());
                    assertFalse(firstProductTable.hasActiveDialog());
                    assertFalse(secondProductTable.hasActiveDialog());
                } finally {
                    second.close();
                    first.close();
                    host.close();
                }
            }
        } finally {
            dealerLogger.removeHandler(cutOnAcceptedRemoteStraddle);
            cutOnAcceptedRemoteStraddle.close();
        }
    }

    @Test
    void hostLiveRulesAndLastHandReachBothNetworkGdxTables()
            throws Exception {
        Bot.Difficulty previousDifficulty = Bot.DIFFICULTY;
        AtomicReference<Throwable> lateFossilPersistence
                = new AtomicReference<>();
        Logger dealerLogger = Logger.getLogger(Crupier.class.getName());
        Handler fossilFailureCapture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if ("Error saving SRA fossil to disk".equals(
                        record.getMessage())) {
                    lateFossilPersistence.compareAndSet(null,
                            record.getThrown());
                }
            }

            @Override public void flush() { }
            @Override public void close() { }
        };
        dealerLogger.addHandler(fossilFailureCapture);
        try {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("last-hand-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("last-hand-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("last-hand-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("last-hand-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(manualLastHandRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(manualLastHandRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                LastHandProjectionRenderer hostRenderer
                        = new LastHandProjectionRenderer(hostTable, true);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                LastHandProjectionRenderer clientRenderer
                        = new LastHandProjectionRenderer(clientTable, false);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> clientRenderer.configuration.get() != null,
                        Duration.ofSeconds(5));
                GameConfigCodecV1.Configuration clientConfiguration =
                        clientRenderer.configuration.get();
                assertThrows(IllegalStateException.class, () ->
                        clientTable.commands().submit(
                                new TableCommand.SetLastHand(true)));
                assertThrows(IllegalStateException.class, () ->
                        clientTable.commands().submit(
                                new TableCommand.SetHandLimit(9)));
                assertThrows(IllegalStateException.class, () ->
                        clientTable.commands().submit(
                                new TableCommand.ApplyGameConfiguration(
                                        clientConfiguration.withHands(9))));
                assertThrows(IllegalStateException.class, () ->
                        clientTable.commands().submit(
                                new TableCommand.SetBotDifficulty(
                                        NewGameTableDraft.BotDifficulty.EASY)));
                assertThrows(IllegalStateException.class, () ->
                        clientTable.commands().submit(
                                new TableCommand.SetCommunicationRules(
                                        false, false)));
                assertThrows(IllegalStateException.class, () ->
                        clientTable.commands().submit(
                                new TableCommand.ForceReconnectPlayers()));

                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(20));
                hostRenderer.assertComplete();
                clientRenderer.assertComplete();
                assertEquals(hostRenderer.configuration.get(),
                        clientRenderer.configuration.get());
                assertEquals(Bot.Difficulty.HARD, Bot.DIFFICULTY,
                        "the GDX host must apply its bot difficulty locally");
            } finally {
                client.close();
                host.close();
                Bot.DIFFICULTY = previousDifficulty;
            }
        }
        } finally {
            dealerLogger.removeHandler(fossilFailureCapture);
            fossilFailureCapture.close();
        }
        assertEquals(null, lateFossilPersistence.get(),
                "closing a GDX network table must finish owned crypto work "
                        + "before its database closes");
    }

    @Test
    void hostExitClosesBothNetworkGdxTablesDuringARealDecision()
            throws Exception {
        assertHostExit(false, false);
    }

    @Test
    void hostExitWhilePausedCannotLeaveEitherNetworkGdxTableBlocked()
            throws Exception {
        assertHostExit(true, false);
    }

    @Test
    void nativeGdxExitConfirmationClosesRealNetworkTableWhilePaused()
            throws Exception {
        assertHostExit(true, true);
    }

    @Test
    void hostExitClosesALocalNineBotGdxTableDuringARealHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService database = new DatabaseService(
                temporary.resolve("local-bot-exit.sqlite").toString());
        database.start();
        try (database;
             NetworkLobbyGateway gateway = gateway(
                     temporary.resolve("local-bot-exit"), database)) {
            LobbySession host = gateway.open(terminationRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            try {
                for (int bot = 0; bot < 8; bot++) {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                }
                await(() -> host.snapshot().participants().size() == 9,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession table = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalProjectionRenderer renderer
                        = new TerminalProjectionRenderer();
                table.attach(renderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(renderer.handStarted::get, Duration.ofSeconds(8));

                table.commands().submit(new TableCommand.ExitGame());

                await(renderer.closed::get, Duration.ofSeconds(10));
                renderer.assertClosed();
                assertEquals(TableSessionSummary.CloseReason.EXITED,
                        renderer.summary.get().reason());
            } finally {
                host.close();
            }
        }
    }

    @Test
    void clientExitClosesBothNetworkGdxTablesDuringARealDecision()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve("client-exit-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve("client-exit-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("client-exit-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("client-exit-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(terminationRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(terminationRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalProjectionRenderer hostRenderer
                        = new TerminalProjectionRenderer();
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalProjectionRenderer clientRenderer
                        = new TerminalProjectionRenderer();
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.ready.get()
                                || clientRenderer.ready.get(),
                        Duration.ofSeconds(8));
                if (!hostRenderer.ready.get()) {
                    clientTable.commands().submit(
                            new TableCommand.CheckOrCall());
                    await(hostRenderer.ready::get, Duration.ofSeconds(8));
                }
                clientTable.commands().submit(new TableCommand.ExitGame());
                await(clientRenderer.closed::get,
                        Duration.ofSeconds(10));
                await(hostRenderer.closed::get, Duration.ofSeconds(10));
                clientRenderer.assertClosed();
                hostRenderer.assertClosed();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private void assertHostExit(boolean pauseFirst, boolean nativeDialog)
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        String prefix = pauseFirst ? "paused-exit" : "active-exit";
        DatabaseService hostDatabase = new DatabaseService(
                temporary.resolve(prefix + "-host.sqlite").toString());
        DatabaseService clientDatabase = new DatabaseService(
                temporary.resolve(prefix + "-client.sqlite").toString());
        hostDatabase.start();
        clientDatabase.start();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve(prefix + "-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve(prefix + "-client"), clientDatabase)) {
            LobbySession host = hostGateway.open(terminationRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(terminationRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalProjectionRenderer hostRenderer
                        = new TerminalProjectionRenderer();
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalProjectionRenderer clientRenderer
                        = new TerminalProjectionRenderer();
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.ready.get()
                                || clientRenderer.ready.get(),
                        Duration.ofSeconds(8));
                if (pauseFirst) {
                    hostTable.commands().submit(new TableCommand.TogglePause());
                    await(() -> hostRenderer.sawPaused.get()
                                    && clientRenderer.sawPaused.get(),
                            Duration.ofSeconds(6));
                }
                if (nativeDialog) {
                    CoronaPokerGdxTable gdxTable = new CoronaPokerGdxTable(60,
                            new GdxTableViewState(
                                    hostRenderer.state.get().snapshot()),
                            hostTable.commands(), () -> { },
                            new GdxGameLogSink(), null);
                    assertNotNull(gdxTable.requestExit());
                    assertTrue(gdxTable.resolveActiveDialogChoice(true),
                            "the native GDX confirmation did not submit exit");
                } else {
                    hostTable.commands().submit(new TableCommand.ExitGame());
                }
                await(() -> hostRenderer.closed.get()
                                && clientRenderer.closed.get(),
                        Duration.ofSeconds(10));
                hostRenderer.assertClosed();
                clientRenderer.assertClosed();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    static NetworkLobbyGateway gateway(Path data, DatabaseService database) {
        return gateway(data, database, GameDecisionSink.noop());
    }

    static NetworkLobbyGateway gateway(Path data, DatabaseService database,
            GameDecisionSink decisions) {
        return gateway(data, database, decisions, acceleratedSettings());
    }

    private static NetworkLobbyGateway gateway(Path data, DatabaseService database,
            GameDecisionSink decisions, GamePresentationSettings settings) {
        CoreGameTableFactory tables = new CoreGameTableFactory(database,
                (key, arguments) -> key, GameLogSink.noop(),
                GameDialogSink.noop(), decisions,
                settings, GameCinematicAssets.none());
        return new NetworkLobbyGateway(data, tables,
                new RecoverableGameRepository(database));
    }

    static NetworkLobbyGateway cinematicGateway(Path data,
            DatabaseService database) {
        CoreGameTableFactory tables = new CoreGameTableFactory(database,
                (key, arguments) -> key, GameLogSink.noop(),
                GameDialogSink.noop(), GameDecisionSink.noop(),
                acceleratedSettings(), new GameCinematicAssets() {
                    @Override public long durationMillis(String filename) {
                        return 1_000L;
                    }

                    @Override public boolean hasCinematic(String filename) {
                        return true;
                    }

                    @Override public boolean hasCompanionAudio(String filename) {
                        return false;
                    }
                });
        return new NetworkLobbyGateway(data, tables,
                new RecoverableGameRepository(database));
    }

    private static GamePresentationSettings acceleratedSettings() {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, arguments) -> "testMode".equals(method.getName())
                        ? true : method.invoke(defaults, arguments));
    }

    private static GamePresentationSettings rebuySettings(boolean automatic) {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "autoRebuyOnBroke" -> automatic;
                    case "ambientMusic", "cinematics", "gameOverCinematics",
                            "blindDealerAnimation", "betAnimation",
                            "counterAnimation", "shuffleAnimation",
                            "dealAnimation", "flipAnimation", "swapAnimation",
                            "callSound", "betSound", "blindSound",
                            "shuffleSound", "dealSound", "flipSound",
                            "cashSound", "iwtsthSound", "startSound",
                            "warningSound", "errorSound" -> false;
                    default -> method.invoke(defaults, arguments);
                });
    }

    private static GamePresentationSettings autoActionSettings() {
        return autoActionSettings(true);
    }

    private static GamePresentationSettings autoActionSettings(
            boolean persistent) {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "testMode", "autoActionButtons" -> true;
                    case "autoActionPersist" -> persistent;
                    default -> method.invoke(defaults, arguments);
                });
    }

    static NewGameRequest request(boolean joining, String nickname,
            int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest rebuyRequest(boolean joining,
            String nickname, int port, int hands) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(hands);
        table.setThinkTime(false);
        table.setRebuy(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest autoActionRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(3);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest autoCallRequest(boolean joining,
            String nickname, int port, int hands, int buyin) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(hands);
        table.setThinkTime(false);
        table.setBuyin(buyin);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest timeoutRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(true);
        table.setThinkSeconds(10);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest runItTwiceRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        table.setRunItTwice(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static GameDecisionSink acceptingRunItTwiceDecisions(
            AtomicReference<CoronaPokerGdxTable> table,
            AtomicInteger votes) {
        return new GdxGameDecisionSink(GameText.keys(), dialog -> {
            if (!"RUN IT TWICE".equals(dialog.title())) {
                throw new AssertionError("unexpected GDX decision dialog: "
                        + dialog.title());
            }
            votes.incrementAndGet();
            CoronaPokerGdxTable productTable = table.get();
            assertNotNull(productTable,
                    "the RIT vote arrived before its GDX table opened");
            productTable.showDialog(dialog);
        });
    }

    private static GameDecisionSink fixedRunItTwiceDecisions(int vote,
            AtomicInteger votes) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, arguments) -> {
                    if ("showRunItTwice".equals(method.getName())) {
                        votes.incrementAndGet();
                        java.util.function.IntConsumer listener
                                = (java.util.function.IntConsumer) arguments[3];
                        if (listener != null) {
                            CompletableFuture.runAsync(() -> listener.accept(vote));
                        }
                        return new GameDecisionSink.RunItTwiceHandle() {
                            @Override public int currentVote() {
                                return vote;
                            }

                            @Override public void updateTally(int normal,
                                    int runItTwice) { }

                            @Override public void close() { }
                        };
                    }
                    return method.invoke(fallback, arguments);
                });
    }

    private static GameDecisionSink acceptingRebuyDecisions(boolean automatic,
            AtomicInteger choices) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, arguments) -> {
                    if ("showRebuy".equals(method.getName())) {
                        GameDecisionSink.RebuyRequest request
                                = (GameDecisionSink.RebuyRequest) arguments[0];
                        assertEquals(automatic, request.automatic());
                        if (!automatic) return method.invoke(fallback, arguments);
                        choices.incrementAndGet();
                        GameDecisionSink.RebuyResult accepted
                                = new GameDecisionSink.RebuyResult(true,
                                        request.defaultAmount());
                        return new GameDecisionSink.RebuyHandle() {
                            @Override
                            public CompletionStage<GameDecisionSink.RebuyResult>
                                    result() {
                                return CompletableFuture.completedFuture(accepted);
                            }

                            @Override public void close() { }
                        };
                    }
                    if ("showGameOver".equals(method.getName())) {
                        GameDecisionSink.GameOverRequest request
                                = (GameDecisionSink.GameOverRequest) arguments[0];
                        if (automatic) {
                            assertTrue(request.direct());
                        } else {
                            assertFalse(request.direct());
                            choices.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    new GameDecisionSink.GameOverResult(true,
                                            request.defaultAmount()));
                        }
                    }
                    return method.invoke(fallback, arguments);
                });
    }

    private static GameDecisionSink nativeGdxRebuyDecisions(
            AtomicReference<CoronaPokerGdxTable> table,
            AtomicInteger choiceFrames, AtomicInteger rebuyFrames) {
        return new GdxGameDecisionSink(GameText.keys(), dialog -> {
            CoronaPokerGdxTable productTable = table.get();
            assertNotNull(productTable,
                    "the network decision arrived before its GDX table opened");
            if (dialog.isGameOver() && dialog.showsPositive()) {
                choiceFrames.incrementAndGet();
            } else if (dialog.kind() == GdxTableDialog.Kind.REBUY) {
                rebuyFrames.incrementAndGet();
            } else {
                throw new AssertionError("unexpected GDX decision dialog: "
                        + dialog.title());
            }
            productTable.showDialog(dialog);
        });
    }

    private static void driveNativeGdxDialogsUntilClosed(
            CoronaPokerGdxTable hostTable,
            CoronaPokerGdxTable clientTable,
            AtomicBoolean hostClosed,
            AtomicBoolean clientClosed,
            AtomicInteger resolutions, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!(hostClosed.get() && clientClosed.get())) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                        "the network tables did not close after native GDX rebuy");
            }
            for (CoronaPokerGdxTable table : List.of(hostTable, clientTable)) {
                table.advanceDialogState();
                if (table.hasActiveDialog()
                        && table.resolveActiveDialogChoice(true)) {
                    resolutions.incrementAndGet();
                    table.advanceDialogState();
                }
            }
            Thread.sleep(10L);
        }
        for (CoronaPokerGdxTable table : List.of(hostTable, clientTable)) {
            table.advanceDialogState();
        }
    }

    private static GameDecisionSink acceptingNativeGdxStraddleDecisions(
            String local,
            AtomicReference<CoronaPokerGdxTable> table,
            AtomicInteger choices, List<String> straddlers) {
        return new GdxGameDecisionSink(GameText.keys(), dialog -> {
            if (!"STRADDLE".equals(dialog.title())) {
                throw new AssertionError("unexpected GDX decision dialog: "
                        + dialog.title());
            }
            choices.incrementAndGet();
            straddlers.add(local);
            CoronaPokerGdxTable productTable = table.get();
            assertNotNull(productTable,
                    "the straddle vote arrived before its GDX table opened");
            productTable.showDialog(dialog);
            if (!productTable.resolveActiveDialogChoice(true)) {
                throw new AssertionError(
                        "the native GDX straddle dialog did not accept");
            }
            productTable.advanceDialogState();
        });
    }

    private static NewGameRequest straddleRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(3);
        table.setThinkTime(false);
        table.setStraddle(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest manualLastHandRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(20);
        table.setThinkTime(false);
        table.setBotDifficulty(NewGameTableDraft.BotDifficulty.EASY);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest terminationRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(20);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static void closeNativeClientSocket(LobbySession session)
            throws Exception {
        Object transport = field(session, "resource");
        Object connection = field(transport, "serverConnection");
        Object generation = field(connection, "generation");
        ((Socket) field(generation, "socket")).close();
    }

    private static int peerReconnectionCount(LobbySession session,
            String nickname) {
        try {
            Object transport = field(session, "resource");
            Object channel = field(transport, "gameChannel");
            var method = channel.getClass().getDeclaredMethod(
                    "peerReconnectionCount", String.class);
            method.setAccessible(true);
            return (int) method.invoke(channel, nickname);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot inspect reconnect count", failure);
        }
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void await(BooleanSupplier condition, Duration timeout)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "timed out waiting for GDX network state");
    }

    static final class ProjectionRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicReference<GdxTableViewState> state = new AtomicReference<>();
        private final AtomicReference<TableSessionSummary> summary = new AtomicReference<>();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean sawLocalControls = new AtomicBoolean();
        private final AtomicBoolean sawRemoteAction = new AtomicBoolean();
        private final AtomicBoolean holdFirstAction = new AtomicBoolean(true);
        private final AtomicBoolean heldAction = new AtomicBoolean();
        private final AtomicBoolean sawPaused = new AtomicBoolean();
        private final AtomicBoolean sawResumed = new AtomicBoolean();
        private final AtomicBoolean sawHurryCue = new AtomicBoolean();
        private final AtomicBoolean sawHurryStop = new AtomicBoolean();
        private final AtomicBoolean sawTimeoutCue = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final EnumSet<TableSnapshot.Street> streets
                = EnumSet.noneOf(TableSnapshot.Street.class);

        ProjectionRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public synchronized CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            streets.add(initialState.street());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public synchronized CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection, "renderer must be opened before events arrive");
            projection.apply(event);
            TableSnapshot snapshot = projection.snapshot();
            streets.add(snapshot.street());
            assertFalse(snapshot.localNickname().isBlank());
            assertTrue(snapshot.pot() >= 0d);
            assertTrue(snapshot.players().stream().allMatch(player ->
                    player.stack() >= 0d && player.streetBet() >= 0d
                            && player.potContribution() >= 0d));

            if (event instanceof TableVisualEvent.PauseStatus pause) {
                if (pause.paused()) {
                    sawPaused.set(true);
                } else if (sawPaused.get()) {
                    sawResumed.set(true);
                }
            }
            if (event instanceof TableVisualEvent.AudioCue cue) {
                if ("misc/hurryup.wav".equals(cue.resource())) {
                    if (cue.operation()
                            == TableVisualEvent.AudioCue.Operation.STOP) {
                        sawHurryStop.set(true);
                    } else if (cue.operation()
                            == TableVisualEvent.AudioCue.Operation.PLAY) {
                        sawHurryCue.set(true);
                    }
                } else if ("misc/timeout.wav".equals(cue.resource())) {
                    sawTimeoutCue.set(true);
                }
            }

            if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                assertEquals(snapshot.localNickname(),
                        snapshot.currentTurnNickname(),
                        "GDX controls may only activate for the local turn");
                sawLocalControls.set(true);
                if (holdFirstAction.get()) {
                    heldAction.set(true);
                    return CompletableFuture.completedFuture(null);
                }
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.PlayerAction action
                    && !action.nickname().equals(snapshot.localNickname())) {
                sawRemoteAction.set(true);
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertComplete(1);
        }

        void assertComplete(int expectedHands) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            assertEquals(expectedHands, endedHands.get());
            assertTrue(sawLocalControls.get());
            assertTrue(sawRemoteAction.get());
            assertTrue(streets.containsAll(EnumSet.of(
                    TableSnapshot.Street.PREFLOP,
                    TableSnapshot.Street.FLOP,
                    TableSnapshot.Street.TURN,
                    TableSnapshot.Street.RIVER,
                    TableSnapshot.Street.SHOWDOWN)));
            assertEquals(2, projection.snapshot().players().size());
            assertEquals(5, projection.snapshot().communityCards().size());
            assertTrue(projection.snapshot().communityCards().stream()
                    .allMatch(card -> card.visible() && card.faceUp()));
            TableSessionSummary finalSummary = summary.get();
            assertNotNull(finalSummary);
            assertEquals(table.initialState().localNickname(),
                    finalSummary.localNickname());
            assertEquals(2, finalSummary.balances().size());
            double stacks = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                    .sum();
            double buyins = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            assertEquals(buyins, stacks, 0.001d);
        }

        void assertTimeoutComplete() {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            assertEquals(1, endedHands.get());
            assertEquals(TableSnapshot.Street.FINISHED,
                    projection.snapshot().street());
            assertEquals("", projection.snapshot().currentTurnNickname());
            assertEquals(ActionControlState.disabled(),
                    projection.actionControls());
            assertNotNull(summary.get());
        }

        void releaseHeldAction() {
            holdFirstAction.set(false);
            if (heldAction.get()) {
                table.commands().submit(new TableCommand.CheckOrCall());
            }
        }

        void releaseHeldActionThroughNativeGdx() {
            holdFirstAction.set(false);
            if (!heldAction.get()) return;
            GdxTableViewState projection = state.get();
            assertNotNull(projection,
                    "native GDX action requires the current table projection");
            CoronaPokerGdxTable nativeTable = new CoronaPokerGdxTable(60,
                    projection, table.commands(), () -> { },
                    new GdxGameLogSink(), null);
            assertTrue(nativeTable.activateCheckOrCallAction(),
                    "native GDX check/call control did not submit");
        }

        boolean hasHeldAction() {
            return heldAction.get();
        }

        boolean isClosed() {
            return closed.get();
        }

        int completedHands() {
            return endedHands.get();
        }

        @Override public void close() { }
    }

    private static final class FoldedObserverProjectionRenderer
            implements TableRenderer {
        private final TableSession table;
        private final boolean foldLocal;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicBoolean actionSubmitted = new AtomicBoolean();
        private final AtomicBoolean localFoldObserved = new AtomicBoolean();
        private final Set<String> revealedPlayers
                = ConcurrentHashMap.newKeySet();
        private final Set<String> monteCarloPlayers
                = ConcurrentHashMap.newKeySet();
        private final Map<String, Boolean> results
                = new ConcurrentHashMap<>();
        private final AtomicBoolean preservedAtEnd = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final GdxTableViewState productState;
        private final CoronaPokerGdxTable productTable;

        FoldedObserverProjectionRenderer(TableSession table,
                boolean foldLocal, GdxTableViewState productState,
                CoronaPokerGdxTable productTable) {
            this.table = table;
            this.foldLocal = foldLocal;
            this.productState = Objects.requireNonNull(productState,
                    "productState");
            this.productTable = Objects.requireNonNull(productTable,
                    "productTable");
        }

        @Override
        public synchronized CompletionStage<Void> open(
                TableSnapshot initialState) {
            state.set(productState);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public synchronized CompletionStage<Void> render(
                TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            String local = projection.snapshot().localNickname();

            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (foldLocal && controls.state().foldEnabled()) {
                    assertTrue(productTable.activateFoldAction(),
                            "the folded observer did not use the real GDX fold control");
                } else if (!foldLocal && controls.state().allInEnabled()
                        && actionSubmitted.compareAndSet(false, true)) {
                    assertTrue(productTable.activateAllInAction());
                    assertTrue(productTable.activateAllInAction());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    assertTrue(productTable.activateCheckOrCallAction());
                }
            } else if (event instanceof TableVisualEvent.PlayerAction action
                    && action.nickname().equals(local)
                    && action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.FOLD) {
                localFoldObserved.set(true);
            } else if (event instanceof TableVisualEvent.RevealHoleCards reveal
                    && !reveal.nickname().equals(local)
                    && !reveal.left().code().isBlank()
                    && !reveal.right().code().isBlank()) {
                revealedPlayers.add(reveal.nickname());
            } else if (event instanceof TableVisualEvent.PartialHand partial
                    && !partial.nickname().equals(local)) {
                monteCarloPlayers.add(partial.nickname());
            } else if (event instanceof TableVisualEvent.HandResult result
                    && !result.nickname().equals(local)) {
                assertFalse(result.handName().isBlank());
                results.put(result.nickname(), result.winner());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                preservedAtEnd.set(revealedPlayers.stream().allMatch(nickname
                        -> projection.presentedHoleCards(nickname).size() == 2
                        && projection.presentedHoleCards(nickname).stream()
                                .allMatch(card -> card.visible()
                                && card.faceUp())));
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertFoldedObserverComplete() {
            assertTrue(localFoldObserved.get(),
                    "the observing GDX local player never folded");
            assertEquals(2, revealedPlayers.size(),
                    "the folded local view must reveal both remaining humans");
            assertEquals(revealedPlayers, monteCarloPlayers,
                    "Monte Carlo must cover every remotely revealed contender");
            assertEquals(revealedPlayers, results.keySet(),
                    "showdown must label every remotely revealed contender");
            assertTrue(results.containsValue(Boolean.TRUE));
            assertTrue(preservedAtEnd.get(),
                    "the END snapshot covered accepted remote hole-card reveals");
        }

        void assertPlayingProjectionComplete() {
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
            assertEquals(1, results.size(),
                    "each contender must receive its remote rival's result");
        }

        @Override public void close() { }
    }

    /**
     * Drives the same GDX AUTO selection/target state used by the live table.
     * It arms once, then relies exclusively on the dealer's clearSelection
     * signal; selecting again on later hands would hide the regression this
     * test is intended to catch.
     */
    private static final class PersistentAutoActionRenderer
            implements TableRenderer {
        private final TableSession table;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final Set<Integer> automaticHands = new HashSet<>();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean armedOnce = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private int queuedSelection;

        PersistentAutoActionRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public synchronized CompletionStage<Void> open(
                TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public synchronized CompletionStage<Void> render(
                TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.PreActionControls controls) {
                queuedSelection = CoronaPokerGdxTable
                        .queuedPreActionAfterControlsEvent(queuedSelection,
                                controls.active(), controls.clearSelection());
                if (controls.active() && armedOnce.compareAndSet(false, true)) {
                    queuedSelection = 2;
                }
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                TableSnapshot snapshot = projection.snapshot();
                int selection = queuedSelection;
                if (selection != 0) {
                    int target = autoTarget(selection, controls.state(),
                            snapshot.street() == TableSnapshot.Street.PREFLOP,
                            projection.bigBlind(), true, 0d);
                    queuedSelection = CoronaPokerGdxTable
                            .queuedPreActionAfterTargetResolution(selection,
                                    target);
                    if (target != 0) {
                        // Production ordering: retain before command dispatch
                        // so a synchronous hand boundary can clear it.
                        queuedSelection = CoronaPokerGdxTable.retainedPreAction(
                                selection, target, true);
                        automaticHands.add(projection.handNumber());
                        table.commands().submit(target == 1
                                ? new TableCommand.Fold()
                                : target == 6 ? new TableCommand.AllIn()
                                        : new TableCommand.CheckOrCall());
                        return CompletableFuture.completedFuture(null);
                    }
                }
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        synchronized boolean provesPersistence() {
            if (automaticHands.size() < 2) return false;
            int first = automaticHands.stream().mapToInt(Integer::intValue)
                    .min().orElseThrow();
            int last = automaticHands.stream().mapToInt(Integer::intValue)
                    .max().orElseThrow();
            return last > first;
        }

        @Override public void close() { }
    }

    /**
     * Runs the production AUTO target resolver against controls emitted by a
     * real two-human network table.  The coordinator only replaces physical
     * pointer presses; all betting legality, street changes and settlement stay
     * in the canonical dealer.
     */
    private static final class AutoCallMatrixRenderer implements TableRenderer {
        private final TableSession table;
        private final AutoCallMatrix coordinator;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private int queuedSelection;

        AutoCallMatrixRenderer(TableSession table,
                AutoCallMatrix coordinator) {
            this.table = table;
            this.coordinator = coordinator;
        }

        @Override
        public synchronized CompletionStage<Void> open(
                TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public synchronized CompletionStage<Void> render(
                TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.PreActionControls controls) {
                queuedSelection = CoronaPokerGdxTable
                        .queuedPreActionAfterControlsEvent(queuedSelection,
                                controls.active(), controls.clearSelection());
                if (controls.active()) queuedSelection = 2;
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && projection.snapshot().localNickname().equals(
                            projection.snapshot().currentTurnNickname())) {
                TableCommand command = coordinator.commandFor(projection,
                        controls.state(), queuedSelection);
                int target = coordinator.lastAutoTarget();
                if (target != Integer.MIN_VALUE) {
                    int selection = queuedSelection;
                    queuedSelection = CoronaPokerGdxTable
                            .queuedPreActionAfterTargetResolution(selection,
                                    target);
                    if (target != 0) {
                        queuedSelection = CoronaPokerGdxTable.retainedPreAction(
                                selection, target, true);
                    }
                }
                table.commands().submit(command);
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override public void close() { }
    }

    private static final class AutoCallMatrix {
        private final boolean forceAllIn;
        private final Map<Integer, String> aggressors = new ConcurrentHashMap<>();
        private final Set<Integer> resolvedPaidCalls = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean sawCappedRejection = new AtomicBoolean();
        private final AtomicBoolean sawAcceptedCall = new AtomicBoolean();
        private final AtomicBoolean sawFreeCheckAfterCall = new AtomicBoolean();
        private final AtomicBoolean sawAllInAutoCall = new AtomicBoolean();
        private final ThreadLocal<Integer> lastAutoTarget
                = ThreadLocal.withInitial(() -> Integer.MIN_VALUE);

        AutoCallMatrix(boolean forceAllIn) {
            this.forceAllIn = forceAllIn;
        }

        synchronized TableCommand commandFor(GdxTableViewState projection,
                ActionControlState controls, int queuedSelection) {
            lastAutoTarget.set(Integer.MIN_VALUE);
            TableSnapshot snapshot = projection.snapshot();
            int hand = projection.handNumber();
            String local = snapshot.localNickname();
            String aggressor = aggressors.get(hand);

            if (aggressor == null) {
                if (forceAllIn && controls.allInEnabled()) {
                    aggressors.put(hand, local);
                    return new TableCommand.AllIn();
                }
                if (!forceAllIn && controls.raiseAction()
                        != ActionControlState.RaiseAction.DISABLED) {
                    aggressors.put(hand, local);
                    return new TableCommand.Bet(controls.raiseAmount());
                }
            }

            aggressor = aggressors.get(hand);
            boolean paidCall = aggressor != null
                    && !aggressor.equals(local)
                    && !resolvedPaidCalls.contains(hand)
                    && (controls.callAction()
                            == ActionControlState.CallAction.CALL
                        || controls.callAction()
                            == ActionControlState.CallAction.DISABLED
                        && controls.allInEnabled());
            if (paidCall) {
                assertEquals(2, queuedSelection,
                        "AUTO Call was not armed before the remote wager");
                double cap = forceAllIn || hand >= 2 ? 100d : 0.01d;
                int target = autoTarget(queuedSelection, controls,
                        snapshot.street() == TableSnapshot.Street.PREFLOP,
                        projection.bigBlind(), true, cap);
                lastAutoTarget.set(target);
                resolvedPaidCalls.add(hand);
                if (forceAllIn) {
                    assertEquals(6, target);
                    sawAllInAutoCall.set(true);
                    return new TableCommand.AllIn();
                }
                if (hand == 1) {
                    assertEquals(0, target);
                    sawCappedRejection.set(true);
                    return new TableCommand.Fold();
                }
                assertEquals(2, target);
                sawAcceptedCall.set(true);
                return new TableCommand.CheckOrCall();
            }

            if (!forceAllIn && hand >= 2 && sawAcceptedCall.get()
                    && snapshot.street() != TableSnapshot.Street.PREFLOP
                    && controls.callAction()
                    == ActionControlState.CallAction.CHECK
                    && queuedSelection == 2) {
                int target = autoTarget(queuedSelection, controls, false,
                        projection.bigBlind(), true, 100d);
                assertEquals(2, target);
                lastAutoTarget.set(target);
                sawFreeCheckAfterCall.set(true);
            }
            return new TableCommand.CheckOrCall();
        }

        int lastAutoTarget() {
            return lastAutoTarget.get();
        }

    }

    private static final class AllInProjectionRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final CompletableFuture<Void> firstCinematic
                = new CompletableFuture<>();
        private final AtomicBoolean cinematicStarted = new AtomicBoolean();
        private final AtomicBoolean cinematicPending = new AtomicBoolean();
        private final AtomicBoolean sawControlsDuringCinematic
                = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger endedHands = new AtomicInteger();

        AllInProjectionRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            assertTrue(projection.snapshot().players().stream().allMatch(player ->
                    player.stack() >= 0d && player.streetBet() >= 0d
                            && player.potContribution() >= 0d));
            if (event instanceof TableVisualEvent.Cinematic cinematic
                    && cinematic.type() == TableVisualEvent.Cinematic.Type.ALL_IN
                    && cinematic.phase() == TableVisualEvent.Cinematic.Phase.START
                    && cinematicStarted.compareAndSet(false, true)) {
                cinematicPending.set(true);
                return firstCinematic;
            }
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (cinematicPending.get()) {
                    sawControlsDuringCinematic.set(true);
                }
                if (controls.state().allInEnabled()) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                }
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void releaseCinematic() {
            cinematicPending.set(false);
            firstCinematic.complete(null);
        }

        void assertComplete() {
            assertTrue(cinematicStarted.get());
            assertFalse(sawControlsDuringCinematic.get());
            assertEquals(1, endedHands.get());
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
        }

        @Override public void close() { }
    }

    private static final class RunItTwiceProjectionRenderer
            implements TableRenderer {
        private final TableSession table;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicLong sideASequence = new AtomicLong();
        private final AtomicLong sideBSequence = new AtomicLong();
        private final CopyOnWriteArrayList<Integer> sideBDeals
                = new CopyOnWriteArrayList<>();
        private final Set<String> revealedPlayers
                = ConcurrentHashMap.newKeySet();
        private final Set<String> monteCarloPlayers
                = ConcurrentHashMap.newKeySet();
        private final Map<String, Boolean> results
                = new ConcurrentHashMap<>();
        private final AtomicBoolean preservedRevealsAtEnd
                = new AtomicBoolean();
        private final AtomicBoolean allInSubmitted = new AtomicBoolean();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final GdxTableViewState productState;
        private final CoronaPokerGdxTable productTable;

        RunItTwiceProjectionRenderer(TableSession table) {
            this(table, null, null);
        }

        RunItTwiceProjectionRenderer(TableSession table,
                GdxTableViewState productState,
                CoronaPokerGdxTable productTable) {
            this.table = table;
            this.productState = productState;
            this.productTable = productTable;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(productState == null
                    ? new GdxTableViewState(initialState) : productState);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (controls.state().allInEnabled()) {
                    allInSubmitted.set(true);
                    if (productTable == null) {
                        table.commands().submit(new TableCommand.AllIn());
                    } else {
                        assertTrue(productTable.activateAllInAction());
                        assertTrue(productTable.activateAllInAction());
                    }
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    if (productTable == null) {
                        table.commands().submit(new TableCommand.CheckOrCall());
                    } else {
                        assertTrue(productTable.activateCheckOrCallAction());
                    }
                }
            } else if (event instanceof TableVisualEvent.RunItTwiceBoard board) {
                if (board.side() == TableVisualEvent.RunItTwiceBoard.Side.A) {
                    sideASequence.compareAndSet(0L, board.sequence());
                } else {
                    sideBSequence.compareAndSet(0L, board.sequence());
                }
            } else if (event instanceof TableVisualEvent.DealCommunityCard deal
                    && sideBSequence.get() > 0L
                    && deal.sequence() > sideBSequence.get()) {
                sideBDeals.add(deal.slot());
            } else if (event instanceof TableVisualEvent.RevealHoleCards reveal
                    && !reveal.left().code().isBlank()
                    && !reveal.right().code().isBlank()) {
                revealedPlayers.add(reveal.nickname());
            } else if (event instanceof TableVisualEvent.PartialHand partial) {
                monteCarloPlayers.add(partial.nickname());
            } else if (event instanceof TableVisualEvent.HandResult result) {
                assertFalse(result.handName().isBlank());
                results.put(result.nickname(), result.winner());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
                preservedRevealsAtEnd.set(revealedPlayers.stream().allMatch(
                        nickname -> projection.presentedHoleCards(nickname)
                                .size() == 2
                        && projection.presentedHoleCards(nickname).stream()
                                .allMatch(card -> card.visible()
                                && card.faceUp())));
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            assertTrue(projection.snapshot().players().stream().allMatch(player ->
                    player.stack() >= 0d && player.streetBet() >= 0d
                            && player.potContribution() >= 0d));
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertTrue(allInSubmitted.get());
            assertTrue(sideASequence.get() > 0L);
            assertTrue(sideBSequence.get() > sideASequence.get());
            assertEquals(List.of(0, 1, 2, 3, 4), sideBDeals);
            assertEquals(Set.of("Anfitrion", "Invitado"), revealedPlayers);
            assertEquals(revealedPlayers, monteCarloPlayers);
            assertEquals(revealedPlayers, results.keySet());
            assertTrue(results.containsValue(Boolean.TRUE));
            assertTrue(preservedRevealsAtEnd.get());
            assertEquals(1, endedHands.get());
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
            assertEquals(5, state.get().snapshot().communityCards().size());
            TableSessionSummary finalSummary = summary.get();
            assertNotNull(finalSummary);
            assertEquals(2, finalSummary.balances().size());
            double stacks = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                    .sum();
            double buyins = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            assertEquals(buyins, stacks, 0.001d);
        }

        void assertSingleBoardComplete() {
            assertTrue(allInSubmitted.get());
            assertEquals(0L, sideASequence.get(),
                    "a declined vote must not start RIT side A");
            assertEquals(0L, sideBSequence.get(),
                    "a declined vote must not start RIT side B");
            assertTrue(sideBDeals.isEmpty());
            assertEquals(Set.of("Anfitrion", "Invitado"), revealedPlayers);
            assertEquals(revealedPlayers, monteCarloPlayers);
            assertEquals(revealedPlayers, results.keySet());
            assertTrue(results.containsValue(Boolean.TRUE));
            assertTrue(preservedRevealsAtEnd.get());
            assertEquals(1, endedHands.get());
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
            assertEquals(5, state.get().snapshot().communityCards().size());
            TableSessionSummary finalSummary = summary.get();
            assertNotNull(finalSummary);
            assertEquals(2, finalSummary.balances().size());
            double stacks = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                    .sum();
            double buyins = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            assertEquals(buyins, stacks, 0.001d);
        }

        @Override public void close() { }
    }

    private static final class RebuyProjectionRenderer implements TableRenderer {
        private final TableSession table;
        private final int expectedHands;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final Set<Integer> allInHands = ConcurrentHashMap.newKeySet();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final GdxTableViewState productState;
        private final CoronaPokerGdxTable productTable;

        RebuyProjectionRenderer(TableSession table, int expectedHands) {
            this(table, expectedHands, null, null);
        }

        RebuyProjectionRenderer(TableSession table, int expectedHands,
                GdxTableViewState productState,
                CoronaPokerGdxTable productTable) {
            this.table = table;
            this.expectedHands = expectedHands;
            this.productState = productState;
            this.productTable = productTable;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(productState == null
                    ? new GdxTableViewState(initialState) : productState);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (controls.state().allInEnabled()) {
                    allInHands.add(projection.handNumber());
                    if (productTable == null) {
                        table.commands().submit(new TableCommand.AllIn());
                    } else {
                        assertTrue(productTable.activateAllInAction());
                        assertTrue(productTable.activateAllInAction());
                    }
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    if (productTable == null) {
                        table.commands().submit(new TableCommand.CheckOrCall());
                    } else {
                        assertTrue(productTable.activateCheckOrCallAction());
                    }
                }
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            assertTrue(projection.snapshot().players().stream().allMatch(player ->
                    player.stack() >= 0d && player.streetBet() >= 0d
                            && player.potContribution() >= 0d));
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertEquals(expectedHands, endedHands.get());
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
            TableSessionSummary finalSummary = summary.get();
            assertNotNull(finalSummary);
            assertTrue(finalSummary.balances().stream()
                    .mapToInt(TableSessionSummary.PlayerBalance::rebuyCount)
                    .sum() >= 1);
            assertTrue(finalSummary.balances().stream().anyMatch(balance
                    -> balance.totalBuyin() > 10d),
                    "a busted seat's rebuy never reached a later hand");
            double stacks = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                    .sum();
            double buyins = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            assertEquals(buyins, stacks, 0.001d);
        }

        Map<String, TableSessionSummary.PlayerBalance> balancesByNickname() {
            Map<String, TableSessionSummary.PlayerBalance> balances
                    = new java.util.TreeMap<>();
            summary.get().balances().forEach(balance
                    -> balances.put(balance.nickname(), balance));
            return balances;
        }

        @Override public void close() { }
    }

    private static final class StraddleProjectionRenderer
            implements TableRenderer {
        private final TableSession table;
        private final String localNickname;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final Set<Integer> straddleHands = ConcurrentHashMap.newKeySet();
        private final Map<Integer, Long> localRevealSequences
                = new ConcurrentHashMap<>();
        private final Map<Integer, Long> firstActionSequences
                = new ConcurrentHashMap<>();
        private final Map<Integer, CopyOnWriteArrayList<TableSnapshot.CardSnapshot>>
                localDeals = new ConcurrentHashMap<>();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final GdxTableViewState productState;
        private final CoronaPokerGdxTable productTable;

        StraddleProjectionRenderer(TableSession table) {
            this(table, null, null);
        }

        StraddleProjectionRenderer(TableSession table,
                GdxTableViewState productState,
                CoronaPokerGdxTable productTable) {
            this.table = table;
            this.localNickname = table.initialState().localNickname();
            this.productState = productState;
            this.productTable = productTable;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(productState == null
                    ? new GdxTableViewState(initialState) : productState);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.DealHoleCard deal
                    && deal.nickname().equals(localNickname)) {
                localDeals.computeIfAbsent(projection.handNumber(), ignored
                        -> new CopyOnWriteArrayList<>()).add(deal.card());
            } else if (event instanceof TableVisualEvent.RevealHoleCards reveal
                    && reveal.nickname().equals(localNickname)
                    && reveal.handName().isBlank()) {
                localRevealSequences.putIfAbsent(projection.handNumber(),
                        reveal.sequence());
            } else if (event instanceof TableVisualEvent.PositionRotation rotation
                    && rotation.transfers().stream().anyMatch(transfer
                    -> transfer.position() == TableSnapshot.Position.STRADDLE)) {
                straddleHands.add(projection.handNumber());
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                firstActionSequences.putIfAbsent(projection.handNumber(),
                        event.sequence());
                if (productTable == null) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                } else {
                    assertTrue(productTable.activateCheckOrCallAction());
                }
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertEquals(3, endedHands.get());
            assertEquals(Set.of(1, 2, 3), straddleHands);
            assertEquals(3, state.get().snapshot().players().size());
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
            assertEquals(Set.of(1, 2, 3), localDeals.keySet());
            assertTrue(localDeals.values().stream().allMatch(cards
                    -> cards.size() == 2),
                    () -> localNickname + " local deal stream was " + localDeals);
            TableSessionSummary finalSummary = summary.get();
            assertNotNull(finalSummary);
            assertEquals(3, finalSummary.balances().size());
            double stacks = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                    .sum();
            double buyins = finalSummary.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            assertEquals(buyins, stacks, 0.001d);
        }

        void assertLocalStraddleOrdering(int hand) {
            List<TableSnapshot.CardSnapshot> cards = localDeals.get(hand);
            Long reveal = localRevealSequences.get(hand);
            Long action = firstActionSequences.get(hand);
            assertNotNull(cards,
                    "the straddler must receive two local cards in hand " + hand);
            assertEquals(2, cards.size());
            assertTrue(cards.stream().noneMatch(
                    TableSnapshot.CardSnapshot::faceUp),
                    "the local straddler's cards must remain hidden until its decision");
            assertNotNull(reveal,
                    "the straddler's local cards must be revealed in hand " + hand);
            assertNotNull(action,
                    "the straddler's table must reach betting in hand " + hand);
            assertTrue(action > reveal,
                    "betting cannot overtake the accepted straddle reveal in hand "
                            + hand);
        }

        Map<String, TableSessionSummary.PlayerBalance> balancesByNickname() {
            Map<String, TableSessionSummary.PlayerBalance> balances
                    = new java.util.TreeMap<>();
            summary.get().balances().forEach(balance
                    -> balances.put(balance.nickname(), balance));
            return balances;
        }

        @Override public void close() { }
    }

    private static final class LastHandProjectionRenderer
            implements TableRenderer {
        private final TableSession table;
        private final boolean host;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicReference<GameConfigCodecV1.Configuration>
                configuration = new AtomicReference<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean sawLastHand = new AtomicBoolean();
        private final AtomicInteger maximumHands = new AtomicInteger(-1);
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        LastHandProjectionRenderer(TableSession table, boolean host) {
            this.table = table;
            this.host = host;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.LastHandStatus status) {
                sawLastHand.set(status.enabled());
            } else if (event instanceof TableVisualEvent.HandLimitStatus status) {
                maximumHands.set(status.maximumHands());
            } else if (event instanceof TableVisualEvent.GameConfigurationStatus status) {
                configuration.set(status.configuration());
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                if (host && scheduled.compareAndSet(false, true)) {
                    GameConfigCodecV1.Configuration current = configuration.get();
                    assertNotNull(current,
                            "live rules must be published before betting controls");
                    table.commands().submit(new TableCommand.ApplyGameConfiguration(
                            current.withHands(3)
                                    .withAnte(true)
                                    .withStraddle(true)
                                    .withIwtsth(true)
                                    .withRunItTwice(true)
                                    .withRabbitHunting(2)
                                    .withBotRebuy(true)
                                    .withBotBalanceToHumans(true)));
                    table.commands().submit(new TableCommand.SetBotDifficulty(
                            NewGameTableDraft.BotDifficulty.HARD));
                    table.commands().submit(
                            new TableCommand.SetCommunicationRules(false, false));
                    table.commands().submit(new TableCommand.SetLastHand(true));
                }
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertTrue(sawLastHand.get());
            assertEquals(3, maximumHands.get());
            assertEquals(1, endedHands.get());
            GameConfigCodecV1.Configuration live = configuration.get();
            assertNotNull(live);
            assertTrue(live.ante());
            assertTrue(live.straddle());
            assertTrue(live.iwtsth());
            assertTrue(live.runItTwice());
            assertEquals(2, live.rabbitHunting());
            assertTrue(live.botRebuy());
            assertTrue(live.botBalanceToHumans());
            assertFalse(state.get().textToSpeechEnabled());
            assertFalse(state.get().voiceMessagesEnabled());
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
        }

        @Override public void close() { }
    }

    private static final class TerminalProjectionRenderer
            implements TableRenderer {
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicBoolean ready = new AtomicBoolean();
        private final AtomicBoolean handStarted = new AtomicBoolean();
        private final AtomicBoolean sawPaused = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection);
            projection.apply(event);
            if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.PREPARE) {
                handStarted.set(true);
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                ready.set(true);
            } else if (event instanceof TableVisualEvent.PauseStatus pause
                    && pause.paused()) {
                sawPaused.set(true);
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertClosed() {
            assertEquals(TableSnapshot.Street.FINISHED,
                    state.get().snapshot().street());
            assertEquals("", state.get().snapshot().currentTurnNickname());
            assertEquals(ActionControlState.disabled(),
                    state.get().actionControls());
        }

        @Override public void close() { }
    }
}
