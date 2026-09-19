package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.CoreGameTableFactory;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameCinematicAssets;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameLogSink;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves consecutive human-vs-human hands over the real native channel. */
class NetworkHumanGameTableIntegrationTest {

    @TempDir Path temporary;

    @Test
    void twoHumanCoreTablesCompleteAuthenticatedMultiHandGame() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("host"), hostDb);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("client"), clientDb)) {
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
                AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                // INIT is emitted by the canonical host dealer after its renderer
                // attaches; that authenticated frame creates the client table.
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallRenderer clientRenderer = new AutoCallRenderer(clientTable);

                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                // Hold the first real decision so PAUSE is exercised while the
                // canonical dealer is waiting for a human, not between hands.
                await(() -> hostRenderer.heldAction.get()
                        || clientRenderer.heldAction.get(), Duration.ofSeconds(5));
                TableSession pausingTable = hostRenderer.heldAction.get()
                        ? hostTable : clientTable;
                pausingTable.commands().submit(new TableCommand.TogglePause());
                await(() -> hostRenderer.sawPaused.get()
                        && clientRenderer.sawPaused.get(), Duration.ofSeconds(5),
                        () -> "host=" + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                pausingTable.commands().submit(new TableCommand.TogglePause());
                await(() -> hostRenderer.sawResumed.get()
                        && clientRenderer.sawResumed.get(), Duration.ofSeconds(5),
                        () -> "host=" + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                hostRenderer.releaseHeldAction();
                clientRenderer.releaseHeldAction();

                await(() -> hostRenderer.completedHands.get() >= 2
                        && clientRenderer.completedHands.get() >= 2,
                        Duration.ofSeconds(18), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic() + "; dealers="
                                + dealerStacks());
                await(() -> hostRenderer.closedByGame.get()
                        && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(4), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                assertEquals(2, hostRenderer.rosterSize.get());
                assertEquals(2, clientRenderer.rosterSize.get());
                assertTrue(hostRenderer.sawRemoteAction.get());
                assertTrue(clientRenderer.sawRemoteAction.get());
                assertFinalSummary(hostRenderer.finalSummary.get(),
                        "Anfitrion", 2);
                assertFinalSummary(clientRenderer.finalSummary.get(),
                        "Invitado", 2);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void dealerManagedTimeoutFoldsHeldHumanAndClosesNetworkHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("timeout-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("timeout-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("timeout-host"), hostDb);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("timeout-client"), clientDb)) {
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
                AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallRenderer clientRenderer = new AutoCallRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.heldAction.get()
                        || clientRenderer.heldAction.get(),
                        Duration.ofSeconds(8));
                await(() -> hostRenderer.sawTimeoutCue.get()
                                || clientRenderer.sawTimeoutCue.get(),
                        Duration.ofSeconds(15), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                await(() -> hostRenderer.closedByGame.get()
                                && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(8), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                assertEquals(1, hostRenderer.completedHands.get());
                assertEquals(1, clientRenderer.completedHands.get());
                assertTrue(hostRenderer.sawHurryCue.get()
                        || clientRenderer.sawHurryCue.get(),
                        "the warning cue must precede the automatic timeout");
                assertTrue(hostRenderer.sawHurryStop.get()
                        || clientRenderer.sawHurryStop.get(),
                        "the warning cue must stop before the hand advances");
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void runItTwicePublishesBothBoardsAndRedealsSideBOverTheRealNetwork()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("rit-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("rit-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        GameDecisionSink decisions = acceptingRunItTwiceDecisions();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("rit-host"), hostDb, decisions,
                     acceleratedSettings());
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("rit-client"), clientDb, decisions,
                     acceleratedSettings())) {
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
                RunItTwiceRenderer hostRenderer
                        = new RunItTwiceRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                RunItTwiceRenderer clientRenderer
                        = new RunItTwiceRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closedByGame.get()
                                && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(25), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                hostRenderer.assertCompletePreflopRunItTwice();
                clientRenderer.assertCompletePreflopRunItTwice();
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void variableInitialBuyinsSelectedByBothHumansReachFinalBalances()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("variable-buyin-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("variable-buyin-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        AtomicInteger hostChoices = new AtomicInteger();
        AtomicInteger clientChoices = new AtomicInteger();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("variable-buyin-host"), hostDb,
                     acceptingInitialBuyinDecision(7, hostChoices),
                     acceleratedSettings());
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("variable-buyin-client"), clientDb,
                     acceptingInitialBuyinDecision(13, clientChoices),
                     acceleratedSettings())) {
            LobbySession host = hostGateway.open(variableBuyinRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(variableBuyinRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                hostRenderer.releaseHeldAction();
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallRenderer clientRenderer = new AutoCallRenderer(clientTable);
                clientRenderer.releaseHeldAction();
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closedByGame.get()
                        && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(15), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                assertEquals(1, hostChoices.get());
                assertEquals(1, clientChoices.get());
                assertBuyin(hostRenderer.finalSummary.get(), "Anfitrion", 7d);
                assertBuyin(hostRenderer.finalSummary.get(), "Invitado", 13d);
                assertBuyin(clientRenderer.finalSummary.get(), "Anfitrion", 7d);
                assertBuyin(clientRenderer.finalSummary.get(), "Invitado", 13d);
                assertFinalSummary(hostRenderer.finalSummary.get(),
                        "Anfitrion", 1);
                assertFinalSummary(clientRenderer.finalSummary.get(),
                        "Invitado", 1);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void automaticHumanRebuyKeepsBothNetworkTablesAliveForNextHand()
            throws Exception {
        assertHumanRebuyKeepsBothNetworkTablesAlive(true);
    }

    @Test
    void acceptedGameOverRebuyKeepsBothNetworkTablesAliveForNextHand()
            throws Exception {
        assertHumanRebuyKeepsBothNetworkTablesAlive(false);
    }

    private void assertHumanRebuyKeepsBothNetworkTablesAlive(
            boolean automatic) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("rebuy-" + automatic
                        + "-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("rebuy-" + automatic
                        + "-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        AtomicInteger rebuyChoices = new AtomicInteger();
        GameDecisionSink decisions = acceptingRebuyDecisions(
                automatic, rebuyChoices);
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("rebuy-" + automatic + "-host"),
                     hostDb, decisions,
                     liveAcceleratedRebuySettings(automatic));
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("rebuy-" + automatic + "-client"),
                     clientDb, decisions,
                     liveAcceleratedRebuySettings(automatic))) {
            LobbySession host = hostGateway.open(rebuyRequest(false,
                    "Anfitrion", port)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(rebuyRequest(true,
                    "Invitado", port)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AllInRenderer hostRenderer = new AllInRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AllInRenderer clientRenderer = new AllInRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.cinematicStarted.get()
                        && clientRenderer.cinematicStarted.get(),
                        Duration.ofSeconds(8), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                // Reproduce the real GDX condition: the START event stays
                // pending until the GIF reaches its last frame. No peer may
                // receive the following ActionControls behind that barrier.
                Thread.sleep(250L);
                assertTrue(!hostRenderer.sawControlsDuringCinematic.get()
                                && !clientRenderer.sawControlsDuringCinematic.get(),
                        () -> "a turn overtook the all-in cinematic: host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                hostRenderer.releaseCinematic();
                clientRenderer.releaseCinematic();

                // A fair heads-up all-in can tie. Do not make this integration
                // scenario depend on the first random deal producing a busted
                // player: keep playing real hands until the real rebuy path is
                // reached, then require one complete post-rebuy hand.
                await(() -> rebuyChoices.get() >= 1,
                        Duration.ofSeconds(45), () -> "choices="
                                + rebuyChoices + "; host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                int preparedAtRebuy = Math.max(
                        hostRenderer.preparedHands.get(),
                        clientRenderer.preparedHands.get());
                int completedAtRebuy = Math.max(
                        hostRenderer.completedHands.get(),
                        clientRenderer.completedHands.get());
                await(() -> hostRenderer.preparedHands.get() > preparedAtRebuy
                        && clientRenderer.preparedHands.get() > preparedAtRebuy,
                        Duration.ofSeconds(20), () -> "post-rebuy hand did not "
                                + "start: host=" + hostRenderer.diagnostic()
                                + "; client=" + clientRenderer.diagnostic());
                hostTable.commands().submit(new TableCommand.SetLastHand(true));
                await(() -> hostRenderer.completedHands.get() > completedAtRebuy
                        && clientRenderer.completedHands.get() > completedAtRebuy,
                        Duration.ofSeconds(30), () -> "post-rebuy hand did not "
                                + "finish: host=" + hostRenderer.diagnostic()
                                + "; client=" + clientRenderer.diagnostic());
                await(() -> hostRenderer.closedByGame.get()
                        && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(20), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void threeHumanTablesResolveBlindStraddleBeforeCardsAndBetting()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("straddle-host.sqlite").toString());
        DatabaseService firstDb = new DatabaseService(
                temporary.resolve("straddle-first.sqlite").toString());
        DatabaseService secondDb = new DatabaseService(
                temporary.resolve("straddle-second.sqlite").toString());
        hostDb.start();
        firstDb.start();
        secondDb.start();
        AtomicInteger choices = new AtomicInteger();
        AtomicReference<String> straddler = new AtomicReference<>();
        try (hostDb; firstDb; secondDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("straddle-host"), hostDb,
                     acceptingStraddleDecisions("Anfitrion", choices, straddler),
                     acceleratedSettings());
             NetworkLobbyGateway firstGateway = gateway(
                     temporary.resolve("straddle-first"), firstDb,
                     acceptingStraddleDecisions("Invitado1", choices, straddler),
                     acceleratedSettings());
             NetworkLobbyGateway secondGateway = gateway(
                     temporary.resolve("straddle-second"), secondDb,
                     acceptingStraddleDecisions("Invitado2", choices, straddler),
                     acceleratedSettings())) {
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
                StraddleRenderer hostRenderer = new StraddleRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession firstTable = first.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                StraddleRenderer firstRenderer = new StraddleRenderer(firstTable);
                firstTable.attach(firstRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession secondTable = second.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                StraddleRenderer secondRenderer = new StraddleRenderer(secondTable);
                secondTable.attach(secondRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closedByGame.get()
                        && firstRenderer.closedByGame.get()
                        && secondRenderer.closedByGame.get(),
                        Duration.ofSeconds(35), () -> "host="
                                + hostRenderer.diagnostic() + "; first="
                                + firstRenderer.diagnostic() + "; second="
                                + secondRenderer.diagnostic());

                assertEquals(1, choices.get(),
                        "exactly the human UTG must decide the straddle");
                assertTrue(straddler.get() != null);
                for (StraddleRenderer renderer : List.of(hostRenderer,
                        firstRenderer, secondRenderer)) {
                    assertEquals(1, renderer.completedHands.get());
                    assertTrue(renderer.sawStraddlePosition.get(),
                            () -> renderer.localNickname + " missed straddle position");
                }
                StraddleRenderer decidingRenderer = List.of(hostRenderer,
                        firstRenderer, secondRenderer).stream()
                        .filter(renderer -> renderer.localNickname.equals(
                                straddler.get()))
                        .findFirst().orElseThrow();
                assertEquals(2, decidingRenderer.localDeals.size());
                assertTrue(decidingRenderer.localDeals.stream()
                        .noneMatch(TableSnapshot.CardSnapshot::faceUp),
                        "the UTG must receive both cards face down before deciding");
                assertTrue(decidingRenderer.localRevealSequence.get() > 0L,
                        "the accepted decision must reveal the local hand in GDX");
                assertTrue(decidingRenderer.firstActionSequence.get()
                        > decidingRenderer.localRevealSequence.get(),
                        "betting controls cannot overtake the straddle reveal barrier");
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    @Test
    void hostManualLastHandClosesEveryHumanTableAfterCurrentHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("last-hand-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("last-hand-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("last-hand-host"), hostDb);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("last-hand-client"), clientDb)) {
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
                ManualLastHandRenderer hostRenderer
                        = new ManualLastHandRenderer(hostTable, true, true);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                ManualLastHandRenderer clientRenderer
                        = new ManualLastHandRenderer(clientTable, false);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closedByGame.get()
                        && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(18), () -> "host="
                                + hostRenderer.diagnostic() + "; client="
                                + clientRenderer.diagnostic());
                assertEquals(1, hostRenderer.completedHands.get());
                assertEquals(1, clientRenderer.completedHands.get());
                assertTrue(hostRenderer.sawLastHand.get());
                assertTrue(clientRenderer.sawLastHand.get());
                assertEquals(3, hostRenderer.maximumHands.get());
                assertEquals(3, clientRenderer.maximumHands.get());
                assertEquals(hostRenderer.configuration.get(),
                        clientRenderer.configuration.get());
                assertTrue(hostRenderer.configuration.get().ante());
                assertTrue(hostRenderer.configuration.get().straddle());
                assertTrue(hostRenderer.configuration.get().iwtsth());
                assertTrue(hostRenderer.configuration.get().runItTwice());
                assertEquals(2,
                        hostRenderer.configuration.get().rabbitHunting());
                assertTrue(hostRenderer.configuration.get().botRebuy());
                assertTrue(hostRenderer.configuration.get()
                        .botBalanceToHumans());
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void hostImmediateExitReachesAndClosesEveryHumanTable() throws Exception {
        assertHostTermination(new TableCommand.ExitGame(), "exit", null);
    }

    @Test
    void hostImmediateExitWhilePausedCannotLeaveEitherTableBlocked()
            throws Exception {
        assertHostTermination(new TableCommand.ExitGame(), "paused-exit",
                null, true);
    }

    @Test
    void hostStopForRecoveryReachesPasswordProtectedHumanTable()
            throws Exception {
        assertHostTermination(new TableCommand.StopGame(), "recover",
                "clave de prueba");
    }

    @Test
    void stoppedBotHandCanBeLoadedAndCompletedFromPersistedRecovery()
            throws Exception {
        int firstPort;
        try (ServerSocket reservation = new ServerSocket(0)) {
            firstPort = reservation.getLocalPort();
        }
        Path data = temporary.resolve("bot-recovery-data");
        DatabaseService database = new DatabaseService(
                temporary.resolve("bot-recovery.sqlite").toString());
        database.start();
        try (database) {
            try (NetworkLobbyGateway gateway = gateway(data, database)) {
                LobbySession host = gateway.open(terminationRequest(false,
                        "Anfitrion", firstPort, "clave recuperacion"))
                        .get(5, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 2,
                            Duration.ofSeconds(5));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession table = host.tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TerminalRenderer renderer = new TerminalRenderer();
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(renderer.ready::get, Duration.ofSeconds(8));
                    table.commands().submit(new TableCommand.StopGame());
                    await(renderer.closedByGame::get, Duration.ofSeconds(8));
                } finally {
                    host.close();
                }
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(database).latestLocal()
                            .orElseThrow();
            assertTrue(recovered.id() > 0);
            assertEquals(20, recovered.settings().handLimitCount());

            int recoveryPort;
            try (ServerSocket reservation = new ServerSocket(0)) {
                recoveryPort = reservation.getLocalPort();
            }
            NewGameConnectionDraft.Submission connection
                    = new NewGameConnectionDraft.Submission(
                            NewGameConnectionDraft.Mode.RECOVER, "Anfitrion",
                            "clave recuperacion", "127.0.0.1",
                            Integer.toString(recoveryPort), null, false, true,
                            recovered.id());
            try (NetworkLobbyGateway gateway = gateway(data, database)) {
                LobbySession host = gateway.open(new NewGameRequest(connection,
                        recovered.settings())).get(5, TimeUnit.SECONDS);
                try {
                    assertTrue(host.snapshot().recovering());
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 2,
                            Duration.ofSeconds(5));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession table = host.tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    ManualLastHandRenderer renderer
                            = new ManualLastHandRenderer(table, true);
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(renderer.closedByGame::get, Duration.ofSeconds(25),
                            renderer::diagnostic);
                    assertTrue(renderer.completedHands.get() >= 1,
                            "the recovered open hand must complete");
                    assertTrue(renderer.sawLastHand.get());
                } finally {
                    host.close();
                }
            }
        }
    }

    @Test
    void completedBotGameCanContinueFromFinalScreenRecovery() throws Exception {
        int firstPort;
        try (ServerSocket reservation = new ServerSocket(0)) {
            firstPort = reservation.getLocalPort();
        }
        Path data = temporary.resolve("bot-continue-data");
        DatabaseService database = new DatabaseService(
                temporary.resolve("bot-continue.sqlite").toString());
        database.start();
        try (database) {
            try (NetworkLobbyGateway gateway = gateway(data, database)) {
                LobbySession host = gateway.open(oneHandBotRequest(
                        "Anfitrion", firstPort, "clave continuar"))
                        .get(5, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 2,
                            Duration.ofSeconds(5));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession table = host.tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    AutoCallRenderer renderer = new AutoCallRenderer(table);
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderer.releaseHeldAction();
                    await(renderer.closedByGame::get, Duration.ofSeconds(18),
                            renderer::diagnostic);
                    assertEquals(1, renderer.completedHands.get());
                } finally {
                    host.close();
                }
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(database).latestLocal()
                            .orElseThrow();
            int recoveryPort;
            try (ServerSocket reservation = new ServerSocket(0)) {
                recoveryPort = reservation.getLocalPort();
            }
            NewGameConnectionDraft.Submission connection
                    = new NewGameConnectionDraft.Submission(
                            NewGameConnectionDraft.Mode.RECOVER, "Anfitrion",
                            "clave continuar", "127.0.0.1",
                            Integer.toString(recoveryPort), null, false, true,
                            recovered.id());
            try (NetworkLobbyGateway gateway = gateway(data, database)) {
                LobbySession host = gateway.open(new NewGameRequest(connection,
                        recovered.settings())).get(5, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 2,
                            Duration.ofSeconds(5));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession table = host.tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    AutoCallRenderer renderer = new AutoCallRenderer(table);
                    // The final-screen action has already recovered a game whose
                    // configured one-hand limit was consumed. End the first
                    // continued hand explicitly; reusing the unrelated dynamic
                    // hand-limit renderer can miss it when the bot folds before
                    // the host receives controls.
                    table.commands().submit(new TableCommand.SetLastHand(true));
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderer.releaseHeldAction();
                    await(renderer.closedByGame::get, Duration.ofSeconds(25),
                            renderer::diagnostic);
                    assertTrue(renderer.completedHands.get() >= 1,
                            "continue must complete the next hand");
                } finally {
                    host.close();
                }
            }
        }
    }

    @Test
    void clientControlledExitDeliversTestamentBeforeClosingItsTable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve("client-exit-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve("client-exit-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("client-exit-host"), hostDb);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("client-exit-client"), clientDb)) {
            LobbySession host = hostGateway.open(terminationRequest(false,
                    "Anfitrion", port, null)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(terminationRequest(true,
                    "Invitado", port, null)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                AutoCallRenderer clientRenderer = new AutoCallRenderer(clientTable);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.heldAction.get()
                        || clientRenderer.heldAction.get(),
                        Duration.ofSeconds(8));
                clientTable.commands().submit(new TableCommand.ExitGame());
                hostRenderer.releaseHeldAction();

                await(() -> clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(8), clientRenderer::diagnostic);
                await(() -> hostRenderer.closedByGame.get(),
                        Duration.ofSeconds(8), hostRenderer::diagnostic);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private void assertHostTermination(TableCommand command, String suffix,
            String password) throws Exception {
        assertHostTermination(command, suffix, password, false);
    }

    private void assertHostTermination(TableCommand command, String suffix,
            String password, boolean pauseFirst) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDb = new DatabaseService(
                temporary.resolve(suffix + "-host.sqlite").toString());
        DatabaseService clientDb = new DatabaseService(
                temporary.resolve(suffix + "-client.sqlite").toString());
        hostDb.start();
        clientDb.start();
        try (hostDb; clientDb;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve(suffix + "-host"), hostDb);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve(suffix + "-client"), clientDb)) {
            LobbySession host = hostGateway.open(terminationRequest(false,
                    "Anfitrion", port, password)).get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(terminationRequest(true,
                    "Invitado", port, password)).get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalRenderer hostRenderer = new TerminalRenderer();
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TerminalRenderer clientRenderer = new TerminalRenderer();
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.ready.get()
                        || clientRenderer.ready.get(), Duration.ofSeconds(8));
                if (pauseFirst) {
                    hostTable.commands().submit(new TableCommand.TogglePause());
                    await(() -> hostRenderer.sawPaused.get()
                                    && clientRenderer.sawPaused.get(),
                            Duration.ofSeconds(5));
                }
                hostTable.commands().submit(command);
                await(() -> hostRenderer.closedByGame.get()
                        && clientRenderer.closedByGame.get(),
                        Duration.ofSeconds(8), () -> "host="
                                + hostRenderer.events + "; client="
                                + clientRenderer.events);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private NetworkLobbyGateway gateway(Path data, DatabaseService database) {
        return gateway(data, database, GameDecisionSink.noop(),
                acceleratedSettings());
    }

    private NetworkLobbyGateway gateway(Path data, DatabaseService database,
            GameDecisionSink decisions, GamePresentationSettings settings) {
        CoreGameTableFactory tables = new CoreGameTableFactory(database,
                GameText.keys(), GameLogSink.noop(), GameDialogSink.noop(),
                decisions, settings, testCinematicAssets());
        return new NetworkLobbyGateway(data, tables);
    }

    private static GameCinematicAssets testCinematicAssets() {
        return new GameCinematicAssets() {
            @Override public long durationMillis(String filename) {
                return 1000L;
            }
            @Override public boolean hasCinematic(String filename) {
                return true;
            }
            @Override public boolean hasCompanionAudio(String filename) {
                return false;
            }
        };
    }

    private static GamePresentationSettings acceleratedSettings() {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, args) -> "testMode".equals(method.getName())
                        ? true : method.invoke(defaults, args));
    }

    private static GamePresentationSettings liveAcceleratedRebuySettings(
            boolean automatic) {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "autoRebuyOnBroke" -> automatic;
                    case "ambientMusic", "cinematics",
                            "gameOverCinematics", "blindDealerAnimation",
                            "betAnimation", "counterAnimation",
                            "shuffleAnimation", "dealAnimation",
                            "flipAnimation", "swapAnimation", "callSound",
                            "betSound", "blindSound", "shuffleSound",
                            "dealSound", "flipSound", "cashSound",
                            "iwtsthSound", "startSound", "warningSound",
                            "errorSound" -> false;
                    default -> method.invoke(defaults, args);
                });
    }

    private static GameDecisionSink acceptingRebuyDecisions(
            boolean automatic, AtomicInteger choices) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showRebuy".equals(method.getName())) {
                        GameDecisionSink.RebuyRequest request
                                = (GameDecisionSink.RebuyRequest) args[0];
                        assertEquals(automatic, request.automatic(),
                                "broke-player path must use automatic rebuy");
                        if (!automatic) return method.invoke(fallback, args);
                        choices.incrementAndGet();
                        GameDecisionSink.RebuyResult accepted
                                = new GameDecisionSink.RebuyResult(true,
                                        request.defaultAmount());
                        return new GameDecisionSink.RebuyHandle() {
                            @Override
                            public CompletionStage<GameDecisionSink.RebuyResult>
                                    result() {
                                return CompletableFuture.completedFuture(
                                        accepted);
                            }

                            @Override public void close() { }
                        };
                    }
                    if ("showGameOver".equals(method.getName())) {
                        GameDecisionSink.GameOverRequest request
                                = (GameDecisionSink.GameOverRequest) args[0];
                        if (automatic) {
                            assertTrue(request.direct(),
                                    "automatic rebuy must bypass the game-over choice");
                        } else {
                            assertTrue(!request.direct(),
                                    "manual rebuy must use the game-over choice");
                            choices.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    new GameDecisionSink.GameOverResult(true,
                                            request.defaultAmount()));
                        }
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static GameDecisionSink acceptingInitialBuyinDecision(int amount,
            AtomicInteger choices) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showRebuy".equals(method.getName())) {
                        GameDecisionSink.RebuyRequest request
                                = (GameDecisionSink.RebuyRequest) args[0];
                        assertTrue(!request.cancelAllowed());
                        assertTrue(!request.automatic());
                        assertTrue(request.deferClose());
                        assertEquals("rebuy.compra_inicial", request.headerKey());
                        assertTrue(amount >= request.minimum()
                                && amount <= request.maximum());
                        choices.incrementAndGet();
                        GameDecisionSink.RebuyResult accepted
                                = new GameDecisionSink.RebuyResult(true, amount);
                        return new GameDecisionSink.RebuyHandle() {
                            @Override
                            public CompletionStage<GameDecisionSink.RebuyResult>
                                    result() {
                                return CompletableFuture.completedFuture(accepted);
                            }

                            @Override public void close() { }
                        };
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static GameDecisionSink acceptingRunItTwiceDecisions() {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showRunItTwice".equals(method.getName())) {
                        java.util.function.IntConsumer listener
                                = (java.util.function.IntConsumer) args[3];
                        if (listener != null) {
                            CompletableFuture.runAsync(() -> listener.accept(
                                    GameDecisionSink.VOTE_RUN_IT_TWICE));
                        }
                        return new GameDecisionSink.RunItTwiceHandle() {
                            @Override public int currentVote() {
                                return GameDecisionSink.VOTE_RUN_IT_TWICE;
                            }
                            @Override public void updateTally(int normal,
                                    int runItTwice) { }
                            @Override public void close() { }
                        };
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static GameDecisionSink acceptingStraddleDecisions(String local,
            AtomicInteger choices, AtomicReference<String> straddler) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showStraddle".equals(method.getName())) {
                        choices.incrementAndGet();
                        if (!straddler.compareAndSet(null, local)) {
                            throw new AssertionError(
                                    "more than one peer requested the straddle");
                        }
                        CompletableFuture<Integer> answer
                                = CompletableFuture.completedFuture(
                                        GameDecisionSink.POST_STRADDLE);
                        return new GameDecisionSink.StraddleHandle() {
                            @Override public CompletionStage<Integer> decision() {
                                return answer;
                            }
                            @Override public boolean isOpen() {
                                return !answer.isDone();
                            }
                            @Override public void accept() { }
                            @Override public void decline() { }
                            @Override public void refreshLayout() { }
                        };
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static NewGameRequest request(boolean joining, String nickname,
            int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(2);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest rebuyRequest(boolean joining, String nickname,
            int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        // Safety cap only. The test schedules the last hand immediately after
        // observing a genuine rebuy, so random ties cannot decide its result.
        table.setHandLimitCount(20);
        table.setThinkTime(false);
        table.setRebuy(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest variableBuyinRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setFixedBuyin(false);
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
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
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        table.setRunItTwice(true);
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
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(true);
        table.setThinkSeconds(10);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest straddleRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
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
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(20);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest terminationRequest(boolean joining,
            String nickname, int port, String password) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, password == null ? "" : password,
                        "127.0.0.1", Integer.toString(port), null,
                        false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(20);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest oneHandBotRequest(String nickname, int port,
            String password) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        NewGameConnectionDraft.Mode.CREATE, nickname,
                        password == null ? "" : password, "127.0.0.1",
                        Integer.toString(port), null, false, false, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static void await(BooleanSupplier condition, Duration timeout)
            throws Exception {
        await(condition, timeout, () -> "");
    }

    private static void await(BooleanSupplier condition, Duration timeout,
            java.util.function.Supplier<String> diagnostic) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for network hand: "
                        + diagnostic.get());
            }
            Thread.sleep(10L);
        }
    }

    private static final class AutoCallRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicInteger completedHands = new AtomicInteger();
        private final AtomicInteger rosterSize = new AtomicInteger();
        private final AtomicBoolean sawRemoteAction = new AtomicBoolean();
        private final AtomicBoolean closedByGame = new AtomicBoolean();
        private final AtomicBoolean holdFirstAction = new AtomicBoolean(true);
        private final AtomicBoolean heldAction = new AtomicBoolean();
        private final AtomicBoolean sawPaused = new AtomicBoolean();
        private final AtomicBoolean sawResumed = new AtomicBoolean();
        private final AtomicBoolean sawHurryCue = new AtomicBoolean();
        private final AtomicBoolean sawHurryStop = new AtomicBoolean();
        private final AtomicBoolean sawTimeoutCue = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> finalSummary
                = new AtomicReference<>();
        private final CopyOnWriteArrayList<String> events
                = new CopyOnWriteArrayList<>();

        AutoCallRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.SeatRoster roster) {
                rosterSize.set(roster.players().size());
            } else if (event instanceof TableVisualEvent.PauseStatus pause) {
                if (pause.paused()) {
                    sawPaused.set(true);
                } else if (sawPaused.get()) {
                    sawResumed.set(true);
                }
            } else if (event instanceof TableVisualEvent.PlayerAction action
                    && !action.nickname().equals(
                            table.initialState().localNickname())) {
                sawRemoteAction.set(true);
            } else if (event instanceof TableVisualEvent.AudioCue cue) {
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
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                if (holdFirstAction.get()
                        && heldAction.compareAndSet(false, true)) {
                    return CompletableFuture.completedFuture(null);
                }
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                completedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                finalSummary.set(close.summary());
                closedByGame.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        String diagnostic() {
            return "hands=" + completedHands + ", roster=" + rosterSize
                    + ", remoteAction=" + sawRemoteAction + ", closed="
                    + closedByGame + ", held=" + heldAction + ", paused="
                    + sawPaused + ", resumed=" + sawResumed + ", hurry="
                    + sawHurryCue + ", hurryStop=" + sawHurryStop
                    + ", timeout=" + sawTimeoutCue
                    + ", events=" + events;
        }

        void releaseHeldAction() {
            holdFirstAction.set(false);
            if (heldAction.get()) {
                table.commands().submit(new TableCommand.CheckOrCall());
            }
        }

        @Override public void close() { }
    }

    private static void assertFinalSummary(TableSessionSummary summary,
            String localNickname, int minimumHands) {
        assertTrue(summary != null && summary.hasBalances(),
                "close event must carry the final balances");
        assertEquals(localNickname, summary.localNickname());
        assertTrue(summary.handCount() >= minimumHands);
        assertEquals(2, summary.balances().size());
        assertTrue(summary.localBalance() != null);
        double finalStacks = summary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                .sum();
        double totalBuyins = summary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                .sum();
        assertEquals(totalBuyins, finalStacks, 0.001d,
                "final balance must conserve the table money");
    }

    private static void assertBuyin(TableSessionSummary summary,
            String nickname, double expected) {
        TableSessionSummary.PlayerBalance balance = summary.balances().stream()
                .filter(candidate -> candidate.nickname().equals(nickname))
                .findFirst().orElseThrow();
        assertEquals(expected, balance.totalBuyin(), 0.001d);
    }

    private static final class AllInRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicInteger preparedHands = new AtomicInteger();
        private final AtomicInteger completedHands = new AtomicInteger();
        private final AtomicBoolean closedByGame = new AtomicBoolean();
        private final AtomicBoolean cinematicStarted = new AtomicBoolean();
        private final AtomicBoolean cinematicPending = new AtomicBoolean();
        private final AtomicBoolean sawControlsDuringCinematic =
                new AtomicBoolean();
        private final CompletableFuture<Void> firstCinematic =
                new CompletableFuture<>();
        private final CopyOnWriteArrayList<String> events
                = new CopyOnWriteArrayList<>();

        AllInRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.Cinematic cinematic
                    && cinematic.type()
                    == TableVisualEvent.Cinematic.Type.ALL_IN
                    && cinematic.phase()
                    == TableVisualEvent.Cinematic.Phase.START
                    && cinematicStarted.compareAndSet(false, true)) {
                cinematicPending.set(true);
                return firstCinematic;
            } else if (event instanceof TableVisualEvent.ActionControls controls) {
                if (cinematicPending.get()) {
                    sawControlsDuringCinematic.set(true);
                }
                if (controls.state().allInEnabled()) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                }
            } else if (event instanceof TableVisualEvent.HandBoundary boundary) {
                if (boundary.phase()
                        == TableVisualEvent.HandBoundary.Phase.PREPARE) {
                    preparedHands.incrementAndGet();
                } else if (boundary.phase()
                        == TableVisualEvent.HandBoundary.Phase.END) {
                    completedHands.incrementAndGet();
                }
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closedByGame.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        String diagnostic() {
            return "prepared=" + preparedHands + ", hands=" + completedHands
                    + ", closed=" + closedByGame
                    + ", cinematicStarted=" + cinematicStarted
                    + ", cinematicPending=" + cinematicPending
                    + ", controlsDuringCinematic="
                    + sawControlsDuringCinematic + ", events=" + events;
        }

        void releaseCinematic() {
            cinematicPending.set(false);
            firstCinematic.complete(null);
        }

        @Override public void close() { }
    }

    private static final class RunItTwiceRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicBoolean closedByGame = new AtomicBoolean();
        private final AtomicLong sideASequence = new AtomicLong();
        private final AtomicLong sideBSequence = new AtomicLong();
        private final CopyOnWriteArrayList<Integer> sideBDeals
                = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> events
                = new CopyOnWriteArrayList<>();

        RunItTwiceRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (controls.state().allInEnabled()) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
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
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closedByGame.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertCompletePreflopRunItTwice() {
            assertTrue(sideASequence.get() > 0L, diagnostic());
            assertTrue(sideBSequence.get() > sideASequence.get(), diagnostic());
            assertEquals(List.of(0, 1, 2, 3, 4), sideBDeals, diagnostic());
        }

        String diagnostic() {
            return "sideA=" + sideASequence + ", sideB=" + sideBSequence
                    + ", deals=" + sideBDeals + ", closed=" + closedByGame
                    + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class StraddleRenderer implements TableRenderer {
        private final TableSession table;
        private final String localNickname;
        private final AtomicInteger completedHands = new AtomicInteger();
        private final AtomicBoolean closedByGame = new AtomicBoolean();
        private final AtomicBoolean sawStraddlePosition = new AtomicBoolean();
        private final AtomicLong localRevealSequence = new AtomicLong();
        private final AtomicLong firstActionSequence = new AtomicLong();
        private final CopyOnWriteArrayList<TableSnapshot.CardSnapshot>
                localDeals = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> events
                = new CopyOnWriteArrayList<>();

        StraddleRenderer(TableSession table) {
            this.table = table;
            this.localNickname = table.initialState().localNickname();
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.DealHoleCard deal
                    && deal.nickname().equals(localNickname)) {
                localDeals.add(deal.card());
            } else if (event instanceof TableVisualEvent.RevealHoleCards reveal
                    && reveal.nickname().equals(localNickname)
                    && reveal.handName().isBlank()) {
                localRevealSequence.compareAndSet(0L, reveal.sequence());
            } else if (event instanceof TableVisualEvent.PositionRotation rotation
                    && rotation.transfers().stream().anyMatch(transfer
                    -> transfer.position() == TableSnapshot.Position.STRADDLE)) {
                sawStraddlePosition.set(true);
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                firstActionSequence.compareAndSet(0L, event.sequence());
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                completedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closedByGame.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        String diagnostic() {
            return "local=" + localNickname + ", hands=" + completedHands
                    + ", closed=" + closedByGame + ", position="
                    + sawStraddlePosition + ", localDeals=" + localDeals
                    + ", reveal=" + localRevealSequence + ", action="
                    + firstActionSequence + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class ManualLastHandRenderer implements TableRenderer {
        private final TableSession table;
        private final boolean schedulesLastHand;
        private final boolean updatesLiveRules;
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean sawLastHand = new AtomicBoolean();
        private final AtomicBoolean closedByGame = new AtomicBoolean();
        private final AtomicInteger completedHands = new AtomicInteger();
        private final AtomicInteger maximumHands = new AtomicInteger(-1);
        private final AtomicReference<GameConfigCodecV1.Configuration>
                configuration = new AtomicReference<>();
        private final CopyOnWriteArrayList<String> events
                = new CopyOnWriteArrayList<>();

        ManualLastHandRenderer(TableSession table, boolean schedulesLastHand) {
            this(table, schedulesLastHand, false);
        }

        ManualLastHandRenderer(TableSession table, boolean schedulesLastHand,
                boolean updatesLiveRules) {
            this.table = table;
            this.schedulesLastHand = schedulesLastHand;
            this.updatesLiveRules = updatesLiveRules;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.LastHandStatus status) {
                sawLastHand.set(status.enabled());
            } else if (event instanceof TableVisualEvent.HandLimitStatus status) {
                maximumHands.set(status.maximumHands());
            } else if (event instanceof TableVisualEvent.GameConfigurationStatus status) {
                configuration.set(status.configuration());
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                if (schedulesLastHand && scheduled.compareAndSet(false, true)) {
                    if (updatesLiveRules) {
                        GameConfigCodecV1.Configuration current
                                = configuration.get();
                        if (current == null) {
                            throw new AssertionError(
                                    "missing initial game configuration event");
                        }
                        table.commands().submit(
                                new TableCommand.ApplyGameConfiguration(current
                                        .withHands(3)
                                        .withAnte(true)
                                        .withStraddle(true)
                                        .withIwtsth(true)
                                        .withRunItTwice(true)
                                        .withRabbitHunting(2)
                                        .withBotRebuy(true)
                                        .withBotBalanceToHumans(true)));
                    } else {
                        table.commands().submit(
                                new TableCommand.SetHandLimit(3));
                    }
                    table.commands().submit(new TableCommand.SetLastHand(true));
                }
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                completedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closedByGame.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        String diagnostic() {
            return "scheduled=" + scheduled + ", lastHand=" + sawLastHand
                    + ", maxHands=" + maximumHands + ", hands="
                    + completedHands + ", config=" + configuration
                    + ", closed="
                    + closedByGame + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class TerminalRenderer implements TableRenderer {
        private final AtomicBoolean ready = new AtomicBoolean();
        private final AtomicBoolean closedByGame = new AtomicBoolean();
        private final AtomicBoolean sawPaused = new AtomicBoolean();
        private final CopyOnWriteArrayList<String> events
                = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                ready.set(true);
            } else if (event instanceof TableVisualEvent.PauseStatus pause
                    && pause.paused()) {
                sawPaused.set(true);
            } else if (event instanceof TableVisualEvent.CloseTable) {
                closedByGame.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override public void close() { }
    }

    private static String dealerStacks() {
        StringBuilder result = new StringBuilder();
        Thread.getAllStackTraces().forEach((thread, stack) -> {
            if (thread.getName().contains("GDX-dealer")) {
                result.append(thread.getName()).append('=');
                for (StackTraceElement frame : stack) {
                    result.append(frame.getMethodName()).append('@')
                            .append(frame.getLineNumber()).append('/');
                }
            }
        });
        return result.toString();
    }
}
