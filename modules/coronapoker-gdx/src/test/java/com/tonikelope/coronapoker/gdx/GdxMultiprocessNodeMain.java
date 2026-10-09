package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * One independently running GDX peer for the multiprocess scenario lane.
 * Scenario behaviour is defined by the historical Swing GOLD suite; this
 * node only exposes the equivalent production GDX commands and observations.
 */
public final class GdxMultiprocessNodeMain {

    private GdxMultiprocessNodeMain() {
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            failure.printStackTrace(System.err);
            marker("FAIL", "uncaught=" + failure.getClass().getName());
        });
        Config config = Config.parse(args);
        System.setProperty("coronapoker.qa.scenarioSeedKey",
                config.scenario + "|" + config.role + "|"
                        + config.nickname + "|" + config.phase);
        if ("spectator-rebuy-cycle".equals(config.scenario)) {
            System.setProperty("coronapoker.qa.spectatorOnBrokeNicks",
                    "client1,client2");
        } else if ("spectator-recovery-mix".equals(config.scenario)) {
            System.setProperty("coronapoker.qa.spectatorOnBrokeNicks",
                    "client1,client2,client3,client4");
        } else if ("human-bust-exit-rejoin-rebuy".equals(config.scenario)) {
            System.setProperty("coronapoker.qa.spectatorOnBrokeNicks",
                    "client1,client2");
        } else if ("spectator-double-recovery-crash-mix".equals(
                config.scenario)) {
            System.setProperty("coronapoker.qa.spectatorOnBrokeNicks",
                    "client1,client2");
        } else if (config.scenario.startsWith("bot-bust-recover-")) {
            System.setProperty("coronapoker.qa.forceBotAllInNicks",
                    "CoronaBot$1,CoronaBot$2");
        }
        Path home = Path.of(System.getProperty("user.home"))
                .toAbsolutePath().normalize();
        Files.createDirectories(home);
        try (DatabaseService database = new DatabaseService(
                    home.resolve("coronapoker-gdx-e2e.sqlite").toString())) {
            database.start();
            if ("crash-rejoin-recover".equals(config.scenario)) {
                runCrashRejoinRecover(config, home, database);
                return;
            }
            if ("spectator-recovery-mix".equals(config.scenario)) {
                runSpectatorRecoveryMix(config, home, database);
                return;
            }
            if ("human-bust-exit-rejoin-rebuy".equals(config.scenario)) {
                runHumanBustExitRejoinRebuy(config, home, database);
                return;
            }
            if ("spectator-double-recovery-crash-mix".equals(
                    config.scenario)) {
                runSpectatorDoubleRecoveryCrashMix(config, home, database);
                return;
            }
            if ("transport-chaos".equals(config.scenario)) {
                runTransportChaos(config, home, database);
                return;
            }
            if ("lifecycle-chaos".equals(config.scenario)) {
                runLifecycleChaos(config, home, database);
                return;
            }
            if (config.scenario.startsWith("bot-bust-recover-")) {
                runBotBustRecovery(config, home, database);
                return;
            }
            if ("force-recover".equals(config.scenario)
                    || "double-force-recover".equals(config.scenario)
                    || "reconnect-force-recover".equals(config.scenario)
                    || "force-recover-add-client".equals(config.scenario)
                    || "force-recover-add-two".equals(config.scenario)
                    || "force-recover-swap-client".equals(config.scenario)) {
                runForceRecover(config, home, database);
                return;
            }
            if ("live-hot-join".equals(config.scenario)
                    || "live-hot-join-stop".equals(config.scenario)
                    || "live-hot-join-exit".equals(config.scenario)
                    || "live-hot-join-bootstrap-exit".equals(config.scenario)
                    || "live-hot-join-admission-exit".equals(config.scenario)
                    || "live-hot-join-reentry".equals(config.scenario)
                    || "live-hot-join-reentry-later".equals(config.scenario)
                    || "live-hot-join-crash-reentry".equals(config.scenario)
                    || "live-hot-join-two".equals(config.scenario)
                    || "live-hot-join-two-exit".equals(config.scenario)) {
                if ("late-impostor".equals(config.phase)) {
                    runRejectedHotJoinIdentity(config, home, database);
                    return;
                }
                runLiveHotJoin(config, home, database);
                return;
            }
            if ("active-player-hot-reentry".equals(config.scenario)) {
                runActivePlayerHotReentry(config, home, database);
                return;
            }
            if ("admitted-hot-join-active-reentry".equals(
                    config.scenario)) {
                runAdmittedHotJoinActiveReentry(config, home, database);
                return;
            }
            AtomicReference<CoronaPokerGdxTable> productTable
                    = new AtomicReference<>();
            AtomicInteger runItTwiceVotes = new AtomicInteger();
            AtomicInteger runItTwiceDialogResolutions = new AtomicInteger();
            AtomicReference<GdxTableDialog> runItTwiceDialog
                    = new AtomicReference<>();
            AtomicReference<GdxScenarioRenderer> scenarioRenderer
                    = new AtomicReference<>();
            try (NetworkLobbyGateway gateway
                    = gateway(config, home.resolve("network"), database,
                            productTable, runItTwiceVotes, runItTwiceDialog,
                            scenarioRenderer);
                 LobbySession lobby = gateway.open(request(config))
                         .get(15, TimeUnit.SECONDS)) {
                marker("READY", "role=" + config.role + " nick="
                        + config.nickname + " port=" + config.port);
                if (config.host()) {
                    await(() -> lobby.snapshot().participants().size()
                                    == config.clients + 1,
                            Duration.ofSeconds(30), "human participants");
                    for (int index = 0; index < config.bots; index++) {
                        lobby.submit(new LobbyCommand.AddBot())
                                .toCompletableFuture().get(10,
                                        TimeUnit.SECONDS);
                    }
                }
                int expectedPlayers = config.clients + config.bots + 1;
                await(() -> lobby.snapshot().participants().size()
                                == expectedPlayers,
                        Duration.ofSeconds(30), "complete lobby");
                marker("LOBBY_READY", "players=" + expectedPlayers);
                if (config.host()) {
                    awaitStartCommand();
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);
                    marker("GAME_START_REQUESTED", "hands=" + config.hands);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        expectedPlayers, productTable, lobby);
                scenarioRenderer.set(renderer);
                if ("raise-mix".equals(config.scenario)) {
                    renderer.enableRaiseMix();
                }
                if ("allin-rebuy".equals(config.scenario)) {
                    renderer.enableAllInEveryHand();
                }
                if ("spectator-rebuy-cycle".equals(config.scenario)) {
                    if ("client1".equals(config.nickname)
                            || "client2".equals(config.nickname)) {
                        renderer.allInOnHand(1L);
                    }
                    renderer.requestImmediateRebuyOnHand(4L);
                }
                if ("allin-rit".equals(config.scenario)
                        || "rit-network-cut".equals(config.scenario)) {
                    renderer.allInOnHand(1L);
                }
                if ("allin-single-board".equals(config.scenario)
                        && "client1".equals(config.nickname)) {
                    renderer.allInOnHand(1L);
                }
                if (config.reconnectEveryStreetClient()) {
                    renderer.gateActionOnStreet(1L,
                            TableSnapshot.Street.PREFLOP);
                } else if (config.actionGateNode()) {
                    renderer.gateActionOnHand(config.gatedHand());
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                if ("pause-resume".equals(config.scenario)) {
                    runPauseResume(config, table, renderer);
                } else if ("rit-network-cut".equals(config.scenario)) {
                    runRitNetworkCut(config, lobby, renderer, productTable,
                            runItTwiceVotes, runItTwiceDialog,
                            runItTwiceDialogResolutions);
                } else if ("straddle-network-cut".equals(config.scenario)) {
                    runStraddleNetworkCut(config, lobby);
                } else if ("allin-reconnect".equals(config.scenario)) {
                    runAllInReconnect(config, lobby, renderer);
                } else if ("allin-controlled-exit".equals(config.scenario)
                        || "allin-abrupt-exit".equals(config.scenario)) {
                    runAllInExit(config, table, renderer);
                } else if (config.reconnectScenario()) {
                    runReconnectScenario(config, lobby, renderer);
                } else if (config.disruptedClient()) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(30),
                            "disruption action gate");
                    marker("ACTION_GATE_REACHED", "scenario="
                            + config.scenario + " nick=" + config.nickname);
                    String command = readCommand();
                    if ("CONTROLLED_EXIT".equals(command)) {
                        table.commands().submit(new TableCommand.ExitGame());
                        marker("CONTROLLED_EXIT_SENT", "nick="
                                + config.nickname);
                    } else if (!"CRASH_PROCESS".equals(command)) {
                        throw new IllegalStateException(
                                "unexpected disruption command " + command);
                    }
                }
                if ("allin-rit".equals(config.scenario)) {
                    driveRunItTwiceDialog(renderer, productTable,
                            runItTwiceVotes, runItTwiceDialogResolutions);
                }
                await(renderer::isClosed,
                        Duration.ofSeconds(Math.max(150L,
                                config.hands * 30L)), "table completion");
                assertOutcome(config, renderer);
                if (("controlled-exit".equals(config.scenario)
                        || "allin-controlled-exit".equals(config.scenario))
                        && !config.disruptedClient()) {
                    marker("DEPARTURE", "nick=client1 label="
                            + renderer.departureLabel("client1"));
                }
                marker("LEDGER", renderer.balancesByNickname().entrySet()
                        .stream()
                        .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                        .map(entry -> entry.getKey() + "="
                                + Math.round(entry.getValue() * 100.0d))
                        .collect(java.util.stream.Collectors.joining(",")));
                marker("CAPITAL", renderer.summary().balances().stream()
                        .sorted(Comparator.comparing(
                                TableSessionSummary.PlayerBalance::nickname))
                        .map(balance -> balance.nickname() + "="
                                + Math.round(balance.finalStack() * 100.0d)
                                + "/"
                                + Math.round(balance.totalBuyin() * 100.0d)
                                + "/" + balance.rebuyCount())
                        .collect(java.util.stream.Collectors.joining(",")));
                marker("HANDS_COMPLETE", "hands=" + renderer.completedHands()
                        + " durableHands=" + renderer.summary().handCount()
                        + " reason=" + renderer.summary().reason());
            }
        }
    }

    private static void runLiveHotJoin(Config config, Path home,
            DatabaseService database) throws Exception {
        boolean stopWhileWarming = "live-hot-join-stop".equals(
                config.scenario);
        boolean concurrentExit = "live-hot-join-two-exit".equals(
                config.scenario);
        boolean exitDuringBootstrap = "live-hot-join-bootstrap-exit".equals(
                config.scenario);
        boolean exitAtAdmission = "live-hot-join-admission-exit".equals(
                config.scenario);
        boolean exitWhileWarming = "live-hot-join-exit".equals(
                config.scenario) || exitDuringBootstrap || concurrentExit;
        boolean crashReentry = "live-hot-join-crash-reentry".equals(
                config.scenario);
        boolean laterHandReentry = "live-hot-join-reentry-later".equals(
                config.scenario);
        boolean twoLate = "live-hot-join-two".equals(config.scenario)
                || concurrentExit;
        boolean reenterWhileWarming = crashReentry
                || laterHandReentry
                || "live-hot-join-reentry".equals(config.scenario);
        Duration warmingRemovalTimeout = Duration.ofSeconds(
                crashReentry ? 75 : 45);
        boolean firstReentryIncarnation = reenterWhileWarming
                && "late-exit".equals(config.phase);
        int visibleExpectedPlayers = config.clients + config.bots + 1;
        int finalExpectedPlayers = config.clients + config.bots
                + (exitWhileWarming ? 0 : 1);
        String newcomerNickname = "client" + config.clients;
        boolean thisWarmingIncarnationExits = exitWhileWarming
                && (!twoLate || newcomerNickname.equals(config.nickname));
        GdxGameLogSink gameLog = new GdxGameLogSink();
        AtomicInteger bootstrapLogLines = new AtomicInteger();
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        AtomicReference<GdxScenarioRenderer> scenarioRenderer
                = new AtomicReference<>();
        try (NetworkLobbyGateway gateway = gateway(config,
                    home.resolve("network"), database, productTable,
                    new AtomicInteger(), new AtomicReference<>(),
                    scenarioRenderer, gameLog);
             LobbySession lobby = gateway.open(request(config))
                     .get(20, TimeUnit.SECONDS)) {
            marker("READY", "role=" + config.role + " nick="
                    + config.nickname + " phase=" + config.phase);
            boolean late = config.phase.startsWith("late");
            if (config.host()) {
                // config.clients includes the one human that joins late.
                // Wait for any incumbent humans, then create the requested
                // bots before starting the real table.
                int initialHumans = config.clients - (twoLate ? 1 : 0);
                await(() -> lobby.snapshot().participants().size()
                                == initialHumans,
                        Duration.ofSeconds(30),
                        "initial hot-join human lobby");
                for (int index = 0; index < config.bots; index++) {
                    lobby.submit(new LobbyCommand.AddBot())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
                int initialPlayers = initialHumans + config.bots;
                await(() -> lobby.snapshot().participants().size()
                                == initialPlayers,
                        Duration.ofSeconds(30),
                        "initial hot-join complete lobby");
                marker("LOBBY_READY", "players=" + initialPlayers);
                awaitStartCommand();
                lobby.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                marker("GAME_START_REQUESTED", "hands=" + config.hands);
            }

            TableSession table = lobby.tableSession().toCompletableFuture()
                    .get(45, TimeUnit.SECONDS);
            GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                    visibleExpectedPlayers, productTable, lobby);
            scenarioRenderer.set(renderer);
            if (config.host()) renderer.gateActionAtOrAfterHand(1L);
            table.attach(renderer).toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);

            if (config.host()) {
                await(renderer::hasHeldAction, Duration.ofSeconds(60),
                        "live hot-join action gate");
                marker("HOT_JOIN_GATE", "hand=" + renderer.currentHand());
                requireCommand(stopWhileWarming ? "STOP_HOT_JOIN"
                        : exitWhileWarming || reenterWhileWarming
                                ? "EXIT_HOT_JOIN"
                        : "RELEASE_HOT_JOIN");
                await(() -> renderer.warmingNicknames().contains(
                                newcomerNickname),
                        Duration.ofSeconds(45),
                        "server hot-join notification");
                double warmingStack = renderer.stackOf(newcomerNickname);
                marker("HOT_JOIN_SERVER_NOTIFIED", "nick="
                        + newcomerNickname);
                if (!renderer.localHoleCardsRemainRevealed()) {
                    throw new AssertionError(
                            "hot join roster hid the host pocket cards");
                }
                marker("HOT_JOIN_LOCAL_CARDS_INTACT", "nick="
                        + config.nickname);
                if (stopWhileWarming) {
                    table.commands().submit(new TableCommand.StopGame());
                } else if (exitWhileWarming || reenterWhileWarming) {
                    await(() -> !renderer.activeNicknames().contains(
                                    newcomerNickname),
                            warmingRemovalTimeout,
                            "server warming-seat removal");
                    marker("HOT_JOIN_EXIT_OBSERVED", "nick="
                            + newcomerNickname);
                    if (reenterWhileWarming) {
                        if (!crashReentry) {
                            // The first incarnation leaves at the initial
                            // decision. Let the real table advance, then hold
                            // the turn before admitting the second process.
                            // This exercises bootstrap over an existing board
                            // instead of repeating the easy preflop case.
                            renderer.releaseHeldActionAndGate(
                                    laterHandReentry ? 2L : 1L,
                                    laterHandReentry
                                            ? TableSnapshot.Street.PREFLOP
                                            : TableSnapshot.Street.TURN);
                            await(renderer::hasHeldAction,
                                    Duration.ofSeconds(90),
                                    "same-identity reentry gate");
                            marker("HOT_JOIN_REENTRY_GATE",
                                    laterHandReentry ? "hand=2"
                                            : "street=TURN");
                        }
                        requireCommand("REENTER_HOT_JOIN");
                        await(() -> renderer.warmingNicknames().contains(
                                        newcomerNickname),
                                Duration.ofSeconds(60),
                                "same-identity hot-join reentry");
                        if (Math.abs(renderer.stackOf(newcomerNickname)
                                - warmingStack) > 0.000_001d) {
                            throw new AssertionError(
                                    "same-identity reentry changed stack: "
                                    + warmingStack + " -> "
                                    + renderer.stackOf(newcomerNickname));
                        }
                        marker("HOT_JOIN_REENTRY_WARMING", "nick="
                                + newcomerNickname + " stack="
                                + renderer.stackOf(newcomerNickname));
                    }
                    renderer.releaseHeldAction();
                } else {
                    renderer.releaseHeldAction();
                }
                if (exitAtAdmission) {
                    await(() -> !renderer.activeNicknames().contains(
                                    newcomerNickname),
                            Duration.ofSeconds(90),
                            "server newly-admitted seat removal");
                    marker("HOT_JOIN_EXIT_OBSERVED", "nick="
                            + newcomerNickname + " phase=admission");
                    await(() -> renderer.sawPlayerAction(newcomerNickname,
                                    TableVisualEvent.PlayerAction.ActionKind.FOLD),
                            Duration.ofSeconds(90),
                            "admitted hot-join exit fold projection");
                    await(() -> gameLog.snapshot().lines().stream()
                                    .anyMatch(line -> line.startsWith(
                                            newcomerNickname + " FOLD (")),
                            Duration.ofSeconds(30),
                            "admitted hot-join exit fold game log");
                    marker("HOT_JOIN_EXIT_FOLD_AUDITED", "nick="
                            + newcomerNickname);
                }
            } else if (late) {
                if (exitDuringBootstrap) {
                    /*
                     * Deliberately leave before waiting for HOTJOIN_STATE, the
                     * history replay or the synthetic current-turn timer. This
                     * exercises cancellation while those frames can still be
                     * queued/in flight instead of merely testing the settled
                     * warming state used by the ordinary exit scenario.
                     */
                    marker("HOT_JOIN_BOOTSTRAP_ATTACHED", "nick="
                            + config.nickname);
                    requireCommand("EXIT_DURING_BOOTSTRAP");
                    table.commands().submit(new TableCommand.ExitGame());
                    await(renderer::isClosed, Duration.ofSeconds(45),
                            "hot-join exit during bootstrap");
                    if (renderer.summary() == null
                            || renderer.summary().reason()
                            != TableSessionSummary.CloseReason.EXITED) {
                        throw new AssertionError(
                                "bootstrap hot join did not close as EXITED");
                    }
                    if (gameLog.snapshot().lines().stream()
                            .map(line -> line.toUpperCase(
                                    java.util.Locale.ROOT))
                            .anyMatch(line -> line.contains(
                                    "NO QUEDAN JUGADORES")
                            || line.contains("NO PLAYERS LEFT"))) {
                        throw new AssertionError(
                                "bootstrap exit displayed a false no-players message");
                    }
                    marker("HOT_JOIN_EXITED", "nick=" + config.nickname
                            + " phase=bootstrap");
                    return;
                }
                try {
                    await(() -> renderer.sawHotJoinState()
                                    && renderer.sawLocalSpectator()
                                    && renderer.warmingNicknames().contains(
                                            config.nickname)
                                    && !gameLog.snapshot().lines().isEmpty(),
                            Duration.ofSeconds(45), "public warming state");
                } catch (IllegalStateException timeout) {
                    throw new IllegalStateException(timeout.getMessage()
                            + " [snapshot=" + renderer.sawHotJoinState()
                            + ", spectator=" + renderer.sawLocalSpectator()
                            + ", warming=" + renderer.warmingNicknames()
                            + ", players=" + renderer.activeNicknames()
                            + ", history="
                            + gameLog.snapshot().lines().size() + "]",
                            timeout);
                }
                await(() -> renderer.activeNicknames().size()
                                == visibleExpectedPlayers,
                        Duration.ofSeconds(45),
                        "complete hot-join roster convergence");
                if (renderer.activeNicknames().size()
                        != visibleExpectedPlayers) {
                    throw new AssertionError("hot-join roster mismatch: expected "
                            + visibleExpectedPlayers + " players but saw "
                            + renderer.activeNicknames() + " warming="
                            + renderer.warmingNicknames());
                }
                if (!renderer.localCardsArePubliclyHidden()) {
                    throw new AssertionError(
                            "hot joiner received concealed pocket data");
                }
                try {
                    await(renderer::activeRemoteCardSlotsAreSafeBacks,
                            Duration.ofSeconds(30),
                            "visible remote card backs in hot-join bootstrap");
                } catch (IllegalStateException timeout) {
                    throw new IllegalStateException(timeout.getMessage()
                            + " [" + renderer.remoteCardSlotDiagnostic()
                            + "]", timeout);
                }
                marker("HOT_JOIN_REMOTE_CARD_BACKS", "nick="
                        + config.nickname);
                bootstrapLogLines.set(gameLog.snapshot().lines().size());
                await(renderer::sawHotJoinTimerStart,
                        Duration.ofSeconds(30),
                        "current turn timer in hot-join bootstrap");
                marker("HOT_JOIN_WARMING", "nick=" + config.nickname
                        + " historyLines=" + bootstrapLogLines.get());
                if (thisWarmingIncarnationExits || firstReentryIncarnation) {
                    requireCommand("EXIT_HOT_JOIN");
                    table.commands().submit(new TableCommand.ExitGame());
                    await(renderer::isClosed, Duration.ofSeconds(45),
                            "warming observer voluntary exit");
                    if (renderer.summary() == null
                            || renderer.summary().reason()
                            != TableSessionSummary.CloseReason.EXITED) {
                        throw new AssertionError(
                                "warming observer did not close as EXITED");
                    }
                    if (gameLog.snapshot().lines().stream()
                            .map(line -> line.toUpperCase(
                                    java.util.Locale.ROOT))
                            .anyMatch(line -> line.contains(
                                    "NO QUEDAN JUGADORES")
                            || line.contains("NO PLAYERS LEFT"))) {
                        throw new AssertionError(
                                "warming exit displayed a false no-players message");
                    }
                    marker("HOT_JOIN_EXITED", "nick=" + config.nickname);
                    return;
                }
                if (!stopWhileWarming && !thisWarmingIncarnationExits) {
                    await(renderer::sawHotJoinPlayerAction,
                            Duration.ofSeconds(90),
                            "ordinary player-action event while warming");
                    marker("HOT_JOIN_VISUAL_STREAM", "nick="
                            + config.nickname);
                    int expectedFaceUp = crashReentry || laterHandReentry
                            ? 0 : 4;
                    if (reenterWhileWarming && expectedFaceUp > 0
                            && !renderer.firstCommunityCardsAreFaceUp(
                                    expectedFaceUp)) {
                        throw new AssertionError(
                                "same-identity hot-join reentry rewound the "
                                + "public flop: "
                                + renderer.communityCardDiagnostic());
                    }
                    if (reenterWhileWarming) {
                        marker("HOT_JOIN_REENTRY_BOARD_STABLE", "faceUp="
                                + expectedFaceUp);
                    }
                    if (config.clients() > 1) {
                        await(renderer::sawHotJoinCommunityReveal,
                                Duration.ofSeconds(90),
                                "ordinary community reveal while warming");
                        marker("HOT_JOIN_COMMUNITY_REVEAL", "nick="
                                + config.nickname);
                    }
                    if (renderer.hotJoinStateCount() != 1) {
                        throw new AssertionError(
                                "warming renderer received more than one "
                                + "bootstrap snapshot");
                    }
                    marker("HOT_JOIN_TIMER_SYNC", "nick="
                            + config.nickname);
                    if (exitAtAdmission) {
                        await(() -> renderer.playingNicknames().contains(
                                        config.nickname)
                                        && renderer.hasLocalDealAfter(1L),
                                Duration.ofSeconds(120),
                                "hot admission active-hand deal");
                        marker("HOT_JOIN_ADMISSION_BOUNDARY", "nick="
                                + config.nickname);
                        requireCommand("EXIT_AFTER_ADMISSION");
                        table.commands().submit(new TableCommand.ExitGame());
                        await(renderer::isClosed, Duration.ofSeconds(60),
                                "newly admitted player exit");
                        if (renderer.summary() == null
                                || renderer.summary().reason()
                                != TableSessionSummary.CloseReason.EXITED) {
                            throw new AssertionError(
                                    "newly admitted player did not close as EXITED");
                        }
                        if (gameLog.snapshot().lines().stream()
                                .map(line -> line.toUpperCase(
                                        java.util.Locale.ROOT))
                                .anyMatch(line -> line.contains(
                                        "NO QUEDAN JUGADORES")
                                || line.contains("NO PLAYERS LEFT"))) {
                            throw new AssertionError(
                                    "admission-boundary exit displayed a false no-players message");
                        }
                        marker("HOT_JOIN_EXITED", "nick=" + config.nickname
                                + " phase=admission");
                        return;
                    }
                    await(() -> renderer.playingNicknames().contains(
                                config.nickname)
                                && renderer.hasLocalDealAfter(1L),
                            Duration.ofSeconds(120),
                            "next-hand hot admission");
                    marker("HOT_JOIN_ADMITTED", "nick=" + config.nickname);
                    if (reenterWhileWarming) {
                        marker("HOT_JOIN_REENTRY_ADMITTED", "nick="
                                + config.nickname + " stack="
                                + renderer.stackOf(config.nickname));
                    }
                }
            } else {
                await(() -> renderer.warmingNicknames().contains(
                                newcomerNickname),
                        Duration.ofSeconds(90),
                        "incumbent hot-join notification");
                marker("HOT_JOIN_PEER_NOTIFIED", "nick="
                        + newcomerNickname);
                if (exitAtAdmission) {
                    await(() -> !renderer.activeNicknames().contains(
                                    newcomerNickname),
                            Duration.ofSeconds(90),
                            "incumbent newly-admitted seat removal");
                    marker("HOT_JOIN_EXIT_OBSERVED", "nick="
                            + newcomerNickname + " phase=admission");
                } else if (exitWhileWarming || reenterWhileWarming) {
                    await(() -> !renderer.activeNicknames().contains(
                                    newcomerNickname),
                            warmingRemovalTimeout,
                            "incumbent warming-seat removal");
                    marker("HOT_JOIN_EXIT_OBSERVED", "nick="
                            + newcomerNickname);
                    if (reenterWhileWarming) {
                        await(() -> renderer.warmingNicknames().contains(
                                        newcomerNickname),
                                Duration.ofSeconds(60),
                                "incumbent same-identity hot-join reentry");
                        marker("HOT_JOIN_REENTRY_WARMING", "nick="
                                + newcomerNickname);
                    }
                }
            }

            if (stopWhileWarming) {
                await(renderer::isClosed, Duration.ofSeconds(90),
                        "hot-join recoverable stop");
                if (renderer.summary() == null
                        || renderer.summary().reason()
                        != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                    throw new AssertionError(
                            "warming peer did not receive recoverable stop");
                }
                marker("HOT_JOIN_STOPPED", "role=" + config.role
                        + " nick=" + config.nickname);
                return;
            }

            await(renderer::isClosed,
                    Duration.ofSeconds(Math.max(180L,
                            config.hands * 45L)), "hot-join completion");
            if (renderer.summary() == null
                    || renderer.summary().balances().size()
                    != finalExpectedPlayers) {
                throw new AssertionError(
                        "hot-join settlement does not contain all players");
            }
            if (late && gameLog.snapshot().lines().size()
                    <= bootstrapLogLines.get()) {
                throw new AssertionError(
                        "hot joiner did not receive live log updates");
            }
            marker("HOT_JOIN_COMPLETE", "role=" + config.role
                    + " nick=" + config.nickname + " hands="
                    + renderer.summary().handCount() + " logLines="
                    + gameLog.snapshot().lines().size());
        }
    }

    /**
     * Exercises the lifecycle that used to be most vulnerable to stale-seat
     * races: an already seated network player leaves during a live decision,
     * its process terminates, and a fresh process with the same persistent
     * identity comes back through the ordinary hot-join path.  No recovery
     * shortcut is used; the running table and all surviving peers stay alive.
     */
    private static void runActivePlayerHotReentry(Config config, Path home,
            DatabaseService database) throws Exception {
        final String owner = "client1";
        final int expectedPlayers = config.clients + config.bots + 1;
        final boolean reenteredOwner = owner.equals(config.nickname)
                && "late-reentry".equals(config.phase);
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        AtomicReference<GdxScenarioRenderer> scenarioRenderer
                = new AtomicReference<>();
        GdxGameLogSink gameLog = new GdxGameLogSink();
        try (NetworkLobbyGateway gateway = gateway(config,
                    home.resolve("network"), database, productTable,
                    new AtomicInteger(), new AtomicReference<>(),
                    scenarioRenderer, gameLog);
             LobbySession lobby = gateway.open(request(config))
                     .get(20, TimeUnit.SECONDS)) {
            marker("READY", "role=" + config.role + " nick="
                    + config.nickname + " phase=" + config.phase);
            if (config.host()) {
                await(() -> lobby.snapshot().participants().size()
                                == config.clients,
                        Duration.ofSeconds(30),
                        "active-reentry initial humans");
                for (int index = 0; index < config.bots; index++) {
                    lobby.submit(new LobbyCommand.AddBot())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
                await(() -> lobby.snapshot().participants().size()
                                == expectedPlayers,
                        Duration.ofSeconds(30),
                        "active-reentry complete lobby");
                marker("LOBBY_READY", "players=" + expectedPlayers);
                awaitStartCommand();
                lobby.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                marker("GAME_START_REQUESTED", "hands=" + config.hands);
            }

            TableSession table = lobby.tableSession().toCompletableFuture()
                    .get(45, TimeUnit.SECONDS);
            GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                    expectedPlayers, productTable, lobby);
            scenarioRenderer.set(renderer);
            if (owner.equals(config.nickname)
                    && "initial".equals(config.phase)) {
                renderer.gateActionAtOrAfterHand(1L);
            }
            table.attach(renderer).toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);

            if (owner.equals(config.nickname)
                    && "initial".equals(config.phase)) {
                await(renderer::hasHeldAction, Duration.ofSeconds(75),
                        "active owner decision gate");
                marker("ACTIVE_REENTRY_EXIT_GATE", "nick=" + owner
                        + " stack=" + renderer.effectiveStackOf(owner));
                requireCommand("EXIT_ACTIVE_PLAYER");
                table.commands().submit(new TableCommand.ExitGame());
                await(renderer::isClosed, Duration.ofSeconds(60),
                        "active owner acknowledged exit");
                if (renderer.summary() == null
                        || renderer.summary().reason()
                        != TableSessionSummary.CloseReason.EXITED) {
                    throw new AssertionError(
                            "active owner did not close as EXITED");
                }
                marker("ACTIVE_REENTRY_EXITED", "nick=" + owner);
                return;
            }

            if (config.host()) {
                requireCommand("EXPECT_ACTIVE_EXIT");
                await(() -> !renderer.activeNicknames().contains(owner),
                        Duration.ofSeconds(60),
                        "active owner removal at host");
                // Chips already committed to the abandoned hand stay in its
                // pot.  The identity owns the remaining stack after EXIT,
                // not stack + a blind/bet that was legitimately spent.
                double preservedStack = renderer.stackOf(owner);
                marker("ACTIVE_REENTRY_EXIT_OBSERVED", "nick=" + owner
                        + " stack=" + preservedStack);
                await(() -> renderer.sawPlayerAction(owner,
                                TableVisualEvent.PlayerAction.ActionKind.FOLD),
                        Duration.ofSeconds(75),
                        "active owner exit fold projection");
                await(() -> gameLog.snapshot().lines().stream()
                                .anyMatch(line -> line.startsWith(
                                        owner + " FOLD (")),
                        Duration.ofSeconds(30),
                        "active owner exit fold game log");
                marker("ACTIVE_REENTRY_EXIT_FOLD_AUDITED", "nick=" + owner);
                // Hold a genuine subsequent local decision so the returning
                // process is guaranteed to observe at least one ordinary live
                // action after its public bootstrap. Without this semantic
                // gate a very fast hand can end between process launch and
                // authentication, making the animation assertion depend on
                // scheduler luck rather than product behaviour.
                renderer.gateActionAtOrAfterHand(renderer.currentHand());
                await(renderer::hasHeldAction, Duration.ofSeconds(90),
                        "active reentry live-action gate");
                requireCommand("EXPECT_ACTIVE_REENTRY");
                await(() -> renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(75),
                        "active owner warming reentry at host");
                double restoredStack = renderer.stackOf(owner);
                if (Math.abs(restoredStack - preservedStack) > 0.000_001d) {
                    throw new AssertionError(
                            "active-owner reentry changed effective stack: "
                            + preservedStack + " -> " + restoredStack);
                }
                marker("ACTIVE_REENTRY_WARMING", "nick=" + owner
                        + " stack=" + restoredStack);
                renderer.releaseHeldAction();
            } else if (reenteredOwner) {
                await(() -> renderer.sawHotJoinState()
                                && renderer.warmingNicknames().contains(owner)
                                && renderer.sawLocalSpectator(),
                        Duration.ofSeconds(60),
                        "reentered owner public warming state");
                if (!renderer.localCardsArePubliclyHidden()) {
                    throw new AssertionError(
                            "reentered warming owner received private cards");
                }
                marker("ACTIVE_REENTRY_WARMING", "nick=" + owner
                        + " stack=" + renderer.effectiveStackOf(owner));
                await(renderer::sawHotJoinPlayerAction,
                        Duration.ofSeconds(120),
                        "reentered owner live action stream");
                await(renderer::sawHotJoinTimerStart,
                        Duration.ofSeconds(120),
                        "reentered owner future turn timer");
                await(() -> renderer.playingNicknames().contains(owner)
                                && renderer.hasLocalDealAfter(1L),
                        Duration.ofSeconds(180),
                        "reentered owner next-hand admission");
                marker("ACTIVE_REENTRY_ADMITTED", "nick=" + owner
                        + " stack=" + renderer.effectiveStackOf(owner));
            } else {
                await(() -> !renderer.activeNicknames().contains(owner),
                        Duration.ofSeconds(60),
                        "incumbent observes active owner exit");
                marker("ACTIVE_REENTRY_EXIT_OBSERVED", "nick=" + owner);
                await(() -> renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(75),
                        "incumbent observes owner warming reentry");
                marker("ACTIVE_REENTRY_WARMING", "nick=" + owner);
            }

            await(renderer::isClosed,
                    Duration.ofSeconds(Math.max(240L,
                            config.hands * 45L)),
                    "active owner reentry completion");
            if (renderer.summary() == null
                    || renderer.summary().balances().size()
                    != expectedPlayers) {
                throw new AssertionError(
                        "active-owner reentry settlement roster mismatch");
            }
            long ownerRows = renderer.summary().balances().stream()
                    .filter(balance -> owner.equals(balance.nickname()))
                    .count();
            if (ownerRows != 1L) {
                throw new AssertionError(
                        "active-owner reentry duplicated settlement rows: "
                        + ownerRows);
            }
            marker("ACTIVE_REENTRY_COMPLETE", "role=" + config.role
                    + " nick=" + config.nickname + " hands="
                    + renderer.summary().handCount());
        }
    }

    /**
     * Proves the full lifecycle of a late identity across three independent
     * transports: warming observer, admitted player and warming reentry. The
     * first active departure is folded and logged by the running table; the
     * replacement process cannot resume that hand and owns exactly the stack
     * left by its authenticated predecessor.
     */
    private static void runAdmittedHotJoinActiveReentry(Config config,
            Path home, DatabaseService database) throws Exception {
        final String owner = "client2";
        final int expectedPlayers = config.clients + config.bots + 1;
        final boolean firstOwner = owner.equals(config.nickname)
                && "late-first".equals(config.phase);
        final boolean reenteredOwner = owner.equals(config.nickname)
                && "late-reentry".equals(config.phase);
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        AtomicReference<GdxScenarioRenderer> scenarioRenderer
                = new AtomicReference<>();
        GdxGameLogSink gameLog = new GdxGameLogSink();
        try (NetworkLobbyGateway gateway = gateway(config,
                    home.resolve("network"), database, productTable,
                    new AtomicInteger(), new AtomicReference<>(),
                    scenarioRenderer, gameLog);
             LobbySession lobby = gateway.open(request(config))
                     .get(20, TimeUnit.SECONDS)) {
            marker("READY", "role=" + config.role + " nick="
                    + config.nickname + " phase=" + config.phase);
            if (config.host()) {
                // config.clients counts both remote identities, but client2
                // deliberately arrives only after the table has started.
                await(() -> lobby.snapshot().participants().size()
                                == config.clients,
                        Duration.ofSeconds(30),
                        "admitted-reentry initial humans");
                for (int index = 0; index < config.bots; index++) {
                    lobby.submit(new LobbyCommand.AddBot())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
                await(() -> lobby.snapshot().participants().size()
                                == expectedPlayers - 1,
                        Duration.ofSeconds(30),
                        "admitted-reentry initial roster");
                marker("LOBBY_READY", "players=" + (expectedPlayers - 1));
                awaitStartCommand();
                lobby.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                marker("GAME_START_REQUESTED", "hands=" + config.hands);
            }

            TableSession table = lobby.tableSession().toCompletableFuture()
                    .get(45, TimeUnit.SECONDS);
            GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                    expectedPlayers, productTable, lobby);
            scenarioRenderer.set(renderer);
            if (config.host()) renderer.gateActionAtOrAfterHand(1L);
            if (firstOwner) renderer.gateActionAtOrAfterHand(2L);
            table.attach(renderer).toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);

            if (config.host()) {
                await(renderer::hasHeldAction, Duration.ofSeconds(75),
                        "admitted-reentry initial host gate");
                marker("ADMITTED_REENTRY_INITIAL_GATE", "hand="
                        + renderer.currentHand());
                requireCommand("EXPECT_LATE_OWNER");
                await(() -> renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(75),
                        "late owner first warming incarnation");
                marker("ADMITTED_REENTRY_FIRST_WARMING", "nick=" + owner);
                renderer.releaseHeldAction();
                await(() -> renderer.playingNicknames().contains(owner),
                        Duration.ofSeconds(150),
                        "late owner first admission");
                marker("ADMITTED_REENTRY_FIRST_ADMITTED", "nick=" + owner);

                requireCommand("EXPECT_ADMITTED_EXIT");
                await(() -> !renderer.activeNicknames().contains(owner),
                        Duration.ofSeconds(90),
                        "admitted late owner removal");
                double preservedStack = renderer.stackOf(owner);
                marker("ADMITTED_REENTRY_EXIT_OBSERVED", "nick=" + owner
                        + " stack=" + preservedStack);
                await(() -> renderer.sawPlayerAction(owner,
                                TableVisualEvent.PlayerAction.ActionKind.FOLD),
                        Duration.ofSeconds(90),
                        "admitted late owner exit fold projection");
                await(() -> gameLog.snapshot().lines().stream()
                                .anyMatch(line -> line.startsWith(
                                        owner + " FOLD (")),
                        Duration.ofSeconds(30),
                        "admitted late owner exit fold game log");
                marker("ADMITTED_REENTRY_EXIT_FOLD_AUDITED", "nick="
                        + owner);

                renderer.gateActionAtOrAfterHand(renderer.currentHand());
                await(renderer::hasHeldAction, Duration.ofSeconds(120),
                        "admitted owner return gate");
                marker("ADMITTED_REENTRY_RETURN_GATE", "hand="
                        + renderer.currentHand());
                requireCommand("EXPECT_ADMITTED_REENTRY");
                await(() -> renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(90),
                        "admitted owner warming reentry");
                double restoredStack = renderer.stackOf(owner);
                if (Math.abs(restoredStack - preservedStack) > 0.000_001d) {
                    throw new AssertionError(
                            "admitted-owner reentry changed effective stack: "
                            + preservedStack + " -> " + restoredStack);
                }
                marker("ADMITTED_REENTRY_WARMING", "nick=" + owner
                        + " stack=" + restoredStack);
                renderer.releaseHeldAction();
            } else if (firstOwner) {
                await(() -> renderer.sawHotJoinState()
                                && renderer.sawLocalSpectator()
                                && renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(75),
                        "late owner initial warming state");
                if (!renderer.localCardsArePubliclyHidden()) {
                    throw new AssertionError(
                            "initial warming owner received private cards");
                }
                await(renderer::activeRemoteCardSlotsAreSafeBacks,
                        Duration.ofSeconds(45),
                        "initial warming owner remote card backs");
                marker("ADMITTED_REENTRY_FIRST_WARMING", "nick=" + owner);
                await(renderer::hasHeldAction, Duration.ofSeconds(180),
                        "late owner first active decision");
                marker("ADMITTED_REENTRY_EXIT_GATE", "nick=" + owner
                        + " stack=" + renderer.effectiveStackOf(owner));
                requireCommand("EXIT_ADMITTED_OWNER");
                table.commands().submit(new TableCommand.ExitGame());
                await(renderer::isClosed, Duration.ofSeconds(75),
                        "admitted late owner acknowledged exit");
                if (renderer.summary() == null
                        || renderer.summary().reason()
                        != TableSessionSummary.CloseReason.EXITED) {
                    throw new AssertionError(
                            "admitted late owner did not close as EXITED");
                }
                if (gameLog.snapshot().lines().stream()
                        .map(line -> line.toUpperCase(
                                java.util.Locale.ROOT))
                        .anyMatch(line -> line.contains("NO QUEDAN JUGADORES")
                        || line.contains("NO PLAYERS LEFT"))) {
                    throw new AssertionError(
                            "admitted late-owner exit displayed a false no-players message");
                }
                marker("ADMITTED_REENTRY_EXITED", "nick=" + owner);
                return;
            } else if (reenteredOwner) {
                await(() -> renderer.sawHotJoinState()
                                && renderer.sawLocalSpectator()
                                && renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(90),
                        "admitted owner public warming reentry");
                if (!renderer.localCardsArePubliclyHidden()) {
                    throw new AssertionError(
                            "reentered admitted owner received private cards");
                }
                await(renderer::activeRemoteCardSlotsAreSafeBacks,
                        Duration.ofSeconds(45),
                        "reentered admitted owner remote card backs");
                marker("ADMITTED_REENTRY_WARMING", "nick=" + owner
                        + " stack=" + renderer.effectiveStackOf(owner));
                await(renderer::sawHotJoinPlayerAction,
                        Duration.ofSeconds(150),
                        "reentered admitted owner live action stream");
                await(renderer::sawHotJoinTimerStart,
                        Duration.ofSeconds(150),
                        "reentered admitted owner future turn timer");
                await(() -> renderer.playingNicknames().contains(owner)
                                && renderer.hasLocalDealAfter(2L),
                        Duration.ofSeconds(210),
                        "reentered admitted owner next-hand admission");
                marker("ADMITTED_REENTRY_ADMITTED", "nick=" + owner
                        + " stack=" + renderer.effectiveStackOf(owner));
            } else {
                await(() -> renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(90),
                        "incumbent sees first warming owner");
                marker("ADMITTED_REENTRY_FIRST_WARMING", "nick=" + owner);
                await(() -> renderer.playingNicknames().contains(owner),
                        Duration.ofSeconds(150),
                        "incumbent sees first owner admission");
                await(() -> !renderer.activeNicknames().contains(owner),
                        Duration.ofSeconds(120),
                        "incumbent sees admitted owner exit");
                marker("ADMITTED_REENTRY_EXIT_OBSERVED", "nick=" + owner);
                await(() -> renderer.warmingNicknames().contains(owner),
                        Duration.ofSeconds(120),
                        "incumbent sees admitted owner warming reentry");
                marker("ADMITTED_REENTRY_WARMING", "nick=" + owner);
                await(() -> renderer.playingNicknames().contains(owner),
                        Duration.ofSeconds(210),
                        "incumbent sees admitted owner return to play");
                marker("ADMITTED_REENTRY_ADMITTED", "nick=" + owner);
            }

            await(renderer::isClosed,
                    Duration.ofSeconds(Math.max(300L,
                            config.hands * 60L)),
                    "admitted hot-join active reentry completion");
            if (renderer.summary() == null
                    || renderer.summary().balances().size()
                    != expectedPlayers) {
                throw new AssertionError(
                        "admitted hot-join reentry settlement roster mismatch");
            }
            long ownerRows = renderer.summary().balances().stream()
                    .filter(balance -> owner.equals(balance.nickname()))
                    .count();
            if (ownerRows != 1L) {
                throw new AssertionError(
                        "admitted hot-join reentry duplicated settlement rows: "
                        + ownerRows);
            }
            marker("ADMITTED_REENTRY_COMPLETE", "role=" + config.role
                    + " nick=" + config.nickname + " hands="
                    + renderer.summary().handCount());
        }
    }

    private static void runRejectedHotJoinIdentity(Config config, Path home,
            DatabaseService database) throws Exception {
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        AtomicReference<GdxScenarioRenderer> scenarioRenderer
                = new AtomicReference<>();
        try (NetworkLobbyGateway gateway = gateway(config,
                home.resolve("network"), database, productTable,
                new AtomicInteger(), new AtomicReference<>(),
                scenarioRenderer, new GdxGameLogSink())) {
            try {
                LobbySession unexpected = gateway.open(request(config))
                        .get(20, TimeUnit.SECONDS);
                unexpected.close();
                throw new AssertionError(
                        "same nickname with a different identity was accepted");
            } catch (java.util.concurrent.ExecutionException rejected) {
                Throwable cause = rejected.getCause();
                String message = cause == null ? "" : String.valueOf(
                        cause.getMessage());
                if (!message.contains("identidad")) {
                    throw rejected;
                }
                marker("HOT_JOIN_IMPERSONATION_REJECTED", "nick="
                        + config.nickname);
            }
        }
    }

    private static void runCrashRejoinRecover(Config config, Path home,
            DatabaseService database) throws Exception {
        int expectedPlayers = config.clients + config.bots + 1;
        if (!config.host() && config.restarted()) {
            runRecoveredClient(config, home, database, expectedPlayers);
            return;
        }

        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        try (NetworkLobbyGateway gateway
                    = GdxNetworkHumanProjectionIntegrationTest.gateway(
                            home.resolve("network"), database);
             LobbySession lobby = gateway.open(request(config))
                     .get(15, TimeUnit.SECONDS)) {
            marker("READY", "role=" + config.role + " nick="
                    + config.nickname + " port=" + config.port
                    + " phase=initial");
            prepareLobby(config, lobby, expectedPlayers);
            if (config.host()) {
                awaitStartCommand();
                lobby.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                marker("GAME_START_REQUESTED", "hands=" + config.hands);
            }

            TableSession table = lobby.tableSession().toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                    expectedPlayers, productTable, lobby);
            if (!config.host()) {
                renderer.gateActionOnHand(1L);
            }
            table.attach(renderer).toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            if (!config.host()) {
                await(renderer::hasHeldAction, Duration.ofSeconds(45),
                        "crash-rejoin action gate");
                marker("ACTION_GATE_REACHED",
                        "scenario=crash-rejoin-recover nick="
                        + config.nickname + " hand=1");
                await(renderer::isClosed, Duration.ofMinutes(5),
                        "initial crashed table closure");
                return;
            }
            await(renderer::isClosed, Duration.ofMinutes(3),
                    "recoverable stop after client crash");
            assertPhaseReconnects(config, renderer, Set.of("client1"));
            if (renderer.summary().reason()
                    != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                throw new AssertionError("crash-rejoin initial table was not "
                        + "recoverable: " + renderer.summary().reason());
            }
            marker("RECOVERABLE_STOP", "hand=1");
        }

        RecoverableGameRepository.RecoverableGame recovered
                = new RecoverableGameRepository(database)
                        .latestLocal().orElseThrow();
        productTable.set(null);
        try (NetworkLobbyGateway gateway
                    = GdxNetworkHumanProjectionIntegrationTest.gateway(
                            home.resolve("network"), database);
             LobbySession lobby = gateway.open(recoveryRequest(config,
                     recovered)).get(20, TimeUnit.SECONDS)) {
            await(() -> lobby.snapshot().participants().size()
                            == expectedPlayers,
                    Duration.ofSeconds(90), "recovered lobby roster");
            marker("RECOVERY_LOBBY_READY", "players=" + expectedPlayers);
            requireCommand("START_RECOVERED_GAME");
            lobby.submit(new LobbyCommand.StartGame())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            marker("RECOVERED_GAME_START_REQUESTED", "hand=2");
            GdxScenarioRenderer renderer = attachRenderer(lobby,
                    expectedPlayers, productTable);
            await(renderer::isClosed, Duration.ofMinutes(3),
                    "recovered host table completion");
            assertRecoveredCrashRejoin(config, renderer);
            emitFinalOutcome(renderer);
        }
    }

    private static void runForceRecover(Config config, Path home,
            DatabaseService database) throws Exception {
        int finalExpectedPlayers = config.clients + config.bots + 1;
        int newcomerCount = "force-recover-add-client".equals(config.scenario)
                ? 1 : "force-recover-add-two".equals(config.scenario) ? 2 : 0;
        boolean swapClientScenario
                = "force-recover-swap-client".equals(config.scenario);
        boolean newcomer = !config.host()
                && ((newcomerCount > 0
                && Integer.parseInt(config.nickname.substring("client".length()))
                        > config.clients - newcomerCount)
                || (swapClientScenario && "client3".equals(config.nickname)));
        int recoveryCycles = "double-force-recover".equals(config.scenario)
                ? 2 : 1;
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        for (int phase = newcomer ? 1 : 0;
                phase <= recoveryCycles; phase++) {
            int cycle = phase;
            boolean recovering = phase > 0;
            boolean stopThisSession = phase < recoveryCycles;
            long interruptedHand = phase * 2L + 1L;
            int expectedPlayers = newcomerCount > 0 && !recovering
                    ? finalExpectedPlayers - newcomerCount
                    : finalExpectedPlayers;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? recoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow())
                    : request(config);
            try (NetworkLobbyGateway gateway
                        = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (!recovering) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial");
                    if (newcomerCount > 0) {
                        prepareInitialRecoveryGrowthLobby(config, lobby,
                                expectedPlayers, newcomerCount);
                    } else {
                        prepareLobby(config, lobby, expectedPlayers);
                    }
                } else {
                    if (config.host()) {
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=" + cycle);
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == expectedPlayers,
                            Duration.ofSeconds(90),
                            "force-recovery lobby roster " + cycle);
                    marker("RECOVERY_LOBBY_READY",
                            "players=" + expectedPlayers + " cycle=" + cycle);
                }
                if (config.host()) {
                    requireCommand(recovering
                            ? "START_RECOVERED_GAME" : "START_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);
                    marker(recovering ? "RECOVERED_GAME_START_REQUESTED"
                            : "GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + cycle);
                }
                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        expectedPlayers, productTable, lobby);
                if (config.host() && stopThisSession) {
                    renderer.gateActionOnHand(interruptedHand);
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                if ("reconnect-force-recover".equals(config.scenario)
                        && !recovering && "client1".equals(config.nickname)) {
                    requireCommand("DROP_SOCKET");
                    closeNativeClientSocket(lobby);
                    marker("SOCKET_DROP_REQUESTED",
                            "nick=client1 hand=1 before=force-recover");
                    await(() -> clientReconnectStarted(lobby),
                            Duration.ofSeconds(45),
                            "reconnect attempt before force recovery");
                    marker("RECONNECT_STARTED",
                            "peer=server hand=1 before=force-recover");
                }
                if (config.host() && stopThisSession) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(90),
                            "force-recover action gate " + interruptedHand);
                    marker("ACTION_GATE_REACHED", "scenario="
                            + config.scenario + " nick=" + config.nickname
                            + " hand=" + interruptedHand + " cycle="
                            + (cycle + 1));
                    requireCommand("FORCE_RECOVER");
                    table.commands().submit(new TableCommand.StopGame());
                    marker("FORCE_RECOVER_REQUESTED", "hand="
                            + interruptedHand + " cycle=" + (cycle + 1));
                }
                await(renderer::isClosed, Duration.ofMinutes(3),
                        stopThisSession ? "forced recoverable stop"
                                : "force-recovered table completion");
                assertPhaseReconnects(config, renderer,
                        "reconnect-force-recover".equals(config.scenario)
                                && !recovering
                                ? Set.of("client1") : Set.of());
                if (stopThisSession) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("force-recover phase "
                                + (cycle + 1) + " was not recoverable: "
                                + renderer.summary().reason());
                    }
                    marker("RECOVERABLE_STOP", "hand=" + interruptedHand
                            + " cycle=" + (cycle + 1));
                    if (swapClientScenario
                            && "client1".equals(config.nickname)) {
                        return;
                    }
                    continue;
                }
                int expectedRenderedHands = newcomer || swapClientScenario ? 1
                        : config.hands - phase * 2 + 2;
                marker("FORCE_RECOVER_TABLE_CLOSED", "nick="
                        + config.nickname + " renderedHands="
                        + renderer.completedHands() + " durableHands="
                        + renderer.summary().handCount() + " reason="
                        + renderer.summary().reason() + " newcomer="
                        + newcomer);
                if (swapClientScenario) {
                    if (newcomer) {
                        renderer.assertCompleteAsPassiveObserver(
                                expectedRenderedHands, 4, 5);
                    } else {
                        renderer.assertCompleteWithHistoricalBalances(
                                expectedRenderedHands, 4, 5);
                    }
                } else {
                    renderer.assertComplete(expectedRenderedHands);
                }
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("force-recover durable hand count: "
                            + "expected " + config.hands + " but was "
                            + renderer.summary().handCount());
                }
                emitFinalOutcome(renderer);
            }
        }
    }

    private static void runSpectatorRecoveryMix(Config config, Path home,
            DatabaseService database) throws Exception {
        int newcomerCount = 2;
        int finalExpectedPlayers = config.clients + config.bots + 1;
        int initialExpectedPlayers = finalExpectedPlayers - newcomerCount;
        int clientNumber = config.host() ? 0 : Integer.parseInt(
                config.nickname.substring("client".length()));
        boolean newcomer = !config.host()
                && clientNumber > config.clients - newcomerCount;
        boolean localSpectator = false;
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();

        for (int phase = newcomer ? 1 : 0; phase <= 1; phase++) {
            boolean recovering = phase == 1;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? recoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow())
                    : request(config);
            try (NetworkLobbyGateway gateway
                        = immediateRebuyGateway(config,
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (!recovering) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial");
                    prepareInitialRecoveryGrowthLobby(config, lobby,
                            initialExpectedPlayers, newcomerCount);
                } else {
                    if (config.host()) {
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=1");
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == finalExpectedPlayers,
                            Duration.ofSeconds(120),
                            "spectator recovery lobby roster");
                    marker("RECOVERY_LOBBY_READY",
                            "players=" + finalExpectedPlayers + " cycle=1");
                }
                if (config.host()) {
                    requireCommand(recovering
                            ? "START_RECOVERED_GAME" : "START_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    marker(recovering ? "RECOVERED_GAME_START_REQUESTED"
                            : "GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + phase);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        recovering ? finalExpectedPlayers
                                : initialExpectedPlayers,
                        productTable, lobby);
                if (!recovering && !config.host()) {
                    renderer.allInOnHand(1L);
                }
                if (config.host()) {
                    renderer.gateActionOnHand(4L);
                }
                if (recovering && localSpectator) {
                    renderer.requestImmediateRebuyOnHand(4L);
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                if (config.host()) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(120),
                            recovering ? "recovered rebuy action gate"
                                    : "initial spectator action gate");
                    marker(recovering ? "RECOVERY_REBUY_GATE_REACHED"
                            : "ACTION_GATE_REACHED",
                            "scenario=spectator-recovery-mix nick="
                            + config.nickname + " hand=4");
                    requireCommand(recovering
                            ? "RELEASE_RECOVERY_ACTION" : "FORCE_RECOVER");
                    if (recovering) {
                        renderer.releaseHeldAction();
                        marker("RECOVERY_REBUY_GATE_RELEASED", "hand=4");
                    } else {
                        table.commands().submit(new TableCommand.StopGame());
                        marker("FORCE_RECOVER_REQUESTED", "hand=4 cycle=1");
                    }
                }

                await(renderer::isClosed, Duration.ofMinutes(4),
                        recovering ? "spectator recovery completion"
                                : "spectator recoverable stop");
                assertPhaseReconnects(config, renderer, Set.of());
                if (!recovering) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("spectator recovery phase was "
                                + "not recoverable: "
                                + renderer.summary().reason());
                    }
                    localSpectator = renderer.sawLocalSpectator();
                    marker("RECOVERABLE_STOP", "hand=4 cycle=1 spectator="
                            + localSpectator + " nick=" + config.nickname);
                    continue;
                }

                renderer.assertComplete(newcomer ? 3 : 4);
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("spectator recovery durable hand "
                            + "count: expected " + config.hands + " but was "
                            + renderer.summary().handCount());
                }
                boolean requested = renderer.requestedImmediateRebuy();
                boolean returned = renderer.returnedAfterSpectating();
                marker("SPECTATOR_RECOVERY", "nick=" + config.nickname
                        + " initialSpectator=" + localSpectator
                        + " requested=" + requested + " returned="
                        + returned);
                if (localSpectator && (!requested || !returned)) {
                    throw new AssertionError("spectator did not complete the "
                            + "recovery rebuy cycle: " + config.nickname);
                }
                emitFinalOutcome(renderer);
                // Keep every real network node alive until all peers have
                // finished their visible-state oracles.  Without this barrier
                // the first successful JVM closed its socket while slower
                // peers were still validating the terminal frame, manufacturing
                // a RECONNECTING projection that can never belong to gameplay.
                marker("VALIDATION_READY",
                        "scenario=spectator-recovery-mix nick="
                        + config.nickname);
                requireCommand("FINISH_SCENARIO");
            }
        }
    }

    private static void runBotBustRecovery(Config config, Path home,
            DatabaseService database) throws Exception {
        int expectedPlayers = config.clients + config.bots + 1;
        boolean enableBotRebuy
                = "bot-bust-recover-regrow".equals(config.scenario);
        Set<String> bustedBots = Set.of();
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();

        for (int phase = 0; phase <= 1; phase++) {
            boolean recovering = phase == 1;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? botRecoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow(),
                            enableBotRebuy)
                    : request(config);
            try (NetworkLobbyGateway gateway
                        = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (!recovering) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial");
                    prepareLobby(config, lobby, expectedPlayers);
                } else {
                    if (config.host()) {
                        lobby.submit(new LobbyCommand.AddBot())
                                .toCompletableFuture().get(10,
                                        TimeUnit.SECONDS);
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=1 botRebuy=" + enableBotRebuy);
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == expectedPlayers,
                            Duration.ofSeconds(120),
                            "bot recovery lobby roster");
                    marker("RECOVERY_LOBBY_READY",
                            "players=" + expectedPlayers + " cycle=1");
                }
                if (config.host()) {
                    requireCommand(recovering
                            ? "START_RECOVERED_GAME" : "START_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    marker(recovering ? "RECOVERED_GAME_START_REQUESTED"
                            : "GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + phase);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        expectedPlayers, productTable, lobby);
                if (!recovering) {
                    renderer.foldAutomatically(true);
                    if (config.host()) {
                        renderer.gateActionOnHand(4L);
                    }
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                if (!recovering) {
                    await(() -> renderer.spectatorNicknames().stream()
                                    .anyMatch(nick
                                            -> nick.startsWith("CoronaBot$")),
                            Duration.ofSeconds(120), "busted bot spectator");
                    bustedBots = renderer.spectatorNicknames().stream()
                            .filter(nick -> nick.startsWith("CoronaBot$"))
                            .collect(java.util.stream.Collectors
                                    .toUnmodifiableSet());
                    System.clearProperty(
                            "coronapoker.qa.forceBotAllInNicks");
                    renderer.foldAutomatically(false);
                    marker("BUSTED_BOTS", "nicks=" + bustedBots.stream()
                            .sorted().collect(java.util.stream.Collectors
                                    .joining(",")));
                    if (config.host()) {
                        await(renderer::hasHeldAction,
                                Duration.ofSeconds(120),
                                "bot recovery action gate");
                        marker("ACTION_GATE_REACHED", "scenario="
                                + config.scenario + " nick="
                                + config.nickname + " hand=4");
                        requireCommand("FORCE_RECOVER");
                        table.commands().submit(new TableCommand.StopGame());
                        marker("FORCE_RECOVER_REQUESTED",
                                "hand=4 cycle=1");
                    }
                }

                await(renderer::isClosed, Duration.ofMinutes(4),
                        recovering ? "bot recovery completion"
                                : "bot recoverable stop");
                assertPhaseReconnects(config, renderer, Set.of());
                if (!recovering) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("bot recovery phase was not "
                                + "recoverable: "
                                + renderer.summary().reason());
                    }
                    marker("RECOVERABLE_STOP", "hand=4 cycle=1");
                    continue;
                }

                renderer.assertCompleteWithHistoricalBalances(4,
                        enableBotRebuy ? 5 : 4, 5);
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("bot recovery durable hand count: "
                            + "expected " + config.hands + " but was "
                            + renderer.summary().handCount());
                }
                boolean reactivated = bustedBots.stream().allMatch(
                        renderer::sawSpectatorReactivated);
                boolean playing = bustedBots.stream().anyMatch(
                        renderer.playingNicknames()::contains);
                marker("BOT_RECOVERY", "nick=" + config.nickname
                        + " mode=" + (enableBotRebuy ? "regrow" : "drop")
                        + " reactivated=" + reactivated + " playing="
                        + playing);
                if (enableBotRebuy != reactivated
                        || enableBotRebuy != playing) {
                    throw new AssertionError("busted bot recovery mismatch: "
                            + config.scenario + " " + bustedBots);
                }
                emitFinalOutcome(renderer);
            }
        }
    }

    private static void runHumanBustExitRejoinRebuy(Config config, Path home,
            DatabaseService database) throws Exception {
        int expectedPlayers = config.clients + config.bots + 1;
        boolean candidate = "client1".equals(config.nickname)
                || "client2".equals(config.nickname);
        boolean selected = false;
        String originalIdentity = HexFormat.of().formatHex(
                PlayerIdentity.loadOrCreate(home.resolve("network"),
                        config.nickname).publicKey());
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();

        for (int phase = 0; phase <= 1; phase++) {
            boolean recovering = phase == 1;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? recoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow())
                    : request(config);
            try (NetworkLobbyGateway gateway = recovering
                        ? immediateRebuyGateway(config,
                                home.resolve("network"), database)
                        : GdxNetworkHumanProjectionIntegrationTest.gateway(
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (!recovering) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial");
                    prepareLobby(config, lobby, expectedPlayers);
                } else {
                    if (config.host()) {
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=1");
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == expectedPlayers,
                            Duration.ofSeconds(120),
                            "human rejoin recovery lobby roster");
                    String recoveredIdentity = HexFormat.of().formatHex(
                            PlayerIdentity.loadOrCreate(
                                    home.resolve("network"),
                                    config.nickname).publicKey());
                    if (!originalIdentity.equals(recoveredIdentity)) {
                        throw new AssertionError("persistent identity changed: "
                                + config.nickname);
                    }
                    marker("RECOVERY_LOBBY_READY", "players="
                            + expectedPlayers + " cycle=1 identity="
                            + recoveredIdentity);
                }
                if (config.host()) {
                    requireCommand(recovering
                            ? "START_RECOVERED_GAME" : "START_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    marker(recovering ? "RECOVERED_GAME_START_REQUESTED"
                            : "GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + phase);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        expectedPlayers, productTable, lobby);
                if (!recovering && candidate) {
                    renderer.allInOnHand(1L);
                }
                if (config.host()) {
                    renderer.gateActionOnHand(4L);
                }
                if (recovering && selected) {
                    renderer.requestImmediateRebuyOnHand(4L);
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                if (!recovering && candidate) {
                    await(() -> renderer.currentHand() >= 4L,
                            Duration.ofSeconds(150),
                            "human bust selection boundary");
                    marker("LOCAL_SPECTATOR", "nick=" + config.nickname
                            + " value=" + renderer.sawLocalSpectator());
                    String selection = readCommand();
                    if ("EXIT_AS_SPECTATOR".equals(selection)) {
                        if (!renderer.sawLocalSpectator()) {
                            throw new AssertionError("selected departing human "
                                    + "was not a spectator");
                        }
                        selected = true;
                        table.commands().submit(new TableCommand.ExitGame());
                        await(renderer::isClosed, Duration.ofSeconds(45),
                                "controlled spectator exit");
                        assertPhaseReconnects(config, renderer, Set.of());
                        if (renderer.summary().reason()
                                != TableSessionSummary.CloseReason.EXITED) {
                            throw new AssertionError("spectator exit reason: "
                                    + renderer.summary().reason());
                        }
                        marker("CONTROLLED_SPECTATOR_EXIT", "nick="
                                + config.nickname + " identity="
                                + originalIdentity);
                    } else if (!"STAY_FOR_RECOVERY".equals(selection)) {
                        throw new IllegalStateException("unexpected bust "
                                + "selection command: " + selection);
                    }
                }

                if (config.host()) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(150),
                            recovering ? "human rejoin rebuy action gate"
                                    : "human bust recovery action gate");
                    marker(recovering ? "RECOVERY_REBUY_GATE_REACHED"
                            : "ACTION_GATE_REACHED", "scenario="
                            + config.scenario + " nick=" + config.nickname
                            + " hand=4");
                    requireCommand(recovering
                            ? "RELEASE_RECOVERY_ACTION" : "FORCE_RECOVER");
                    if (recovering) {
                        renderer.releaseHeldAction();
                        marker("RECOVERY_REBUY_GATE_RELEASED", "hand=4");
                    } else {
                        table.commands().submit(new TableCommand.StopGame());
                        marker("FORCE_RECOVER_REQUESTED",
                                "hand=4 cycle=1");
                    }
                }

                if (!recovering && selected) {
                    continue;
                }
                await(renderer::isClosed, Duration.ofMinutes(4),
                        recovering ? "human rejoin recovery completion"
                                : "human bust recoverable stop");
                assertPhaseReconnects(config, renderer, Set.of());
                if (!recovering) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("human bust recovery phase was "
                                + "not recoverable: "
                                + renderer.summary().reason());
                    }
                    marker("RECOVERABLE_STOP", "hand=4 cycle=1 nick="
                            + config.nickname);
                    continue;
                }

                if (candidate && !selected
                        && renderer.sawLocalSpectator()) {
                    renderer.assertCompleteAsPassiveObserver(4,
                            expectedPlayers, expectedPlayers);
                } else {
                    renderer.assertComplete(4);
                }
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("human rejoin durable hand count: "
                            + "expected " + config.hands + " but was "
                            + renderer.summary().handCount());
                }
                boolean requested = renderer.requestedImmediateRebuy();
                boolean returned = renderer.returnedAfterSpectating();
                boolean reactivated = renderer.sawSpectatorReactivated(
                        config.nickname);
                boolean playing = renderer.playingNicknames().contains(
                        config.nickname);
                marker("HUMAN_REJOIN_RECOVERY", "nick=" + config.nickname
                        + " selected=" + selected + " requested=" + requested
                        + " returned=" + returned + " reactivated="
                        + reactivated + " playing=" + playing);
                if (selected && (!renderer.sawLocalSpectator() || !requested
                        || !returned || !reactivated || !playing)) {
                    throw new AssertionError("rejoined spectator did not "
                            + "complete the rebuy cycle: " + config.nickname);
                }
                emitFinalOutcome(renderer);
            }
        }
    }

    private static void runSpectatorDoubleRecoveryCrashMix(Config config,
            Path home, DatabaseService database) throws Exception {
        int newcomerCount = 2;
        int expectedPlayers = config.clients + config.bots + 1;
        int clientNumber = config.host() ? 0 : Integer.parseInt(
                config.nickname.substring("client".length()));
        boolean newcomer = !config.host()
                && clientNumber > config.clients - newcomerCount;
        int firstPhase = "second-recovery".equals(config.phase) ? 2
                : newcomer ? 1 : 0;
        String identity = HexFormat.of().formatHex(
                PlayerIdentity.loadOrCreate(home.resolve("network"),
                        config.nickname).publicKey());
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();

        for (int phase = firstPhase; phase <= 2; phase++) {
            boolean recovering = phase > 0;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? recoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow())
                    : request(config);
            try (NetworkLobbyGateway gateway
                        = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (phase == 0) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial identity=" + identity);
                    prepareInitialRecoveryGrowthLobby(config, lobby,
                            expectedPlayers - newcomerCount, newcomerCount);
                } else {
                    if (config.host()) {
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=" + phase);
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == expectedPlayers,
                            Duration.ofSeconds(150),
                            "double recovery lobby roster");
                    marker("RECOVERY_LOBBY_READY", "players="
                            + expectedPlayers + " cycle=" + phase
                            + " identity=" + identity);
                }
                if (config.host()) {
                    requireCommand(phase == 0
                            ? "START_GAME" : "START_RECOVERED_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    marker(phase == 0 ? "GAME_START_REQUESTED"
                            : "RECOVERED_GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + phase);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        phase == 0 ? expectedPlayers - newcomerCount
                                : expectedPlayers,
                        productTable, lobby);
                if (phase == 0) {
                    if ("client1".equals(config.nickname)
                            || "client2".equals(config.nickname)) {
                        renderer.allInAtFirstOpportunity();
                    } else {
                        renderer.foldAutomatically(true);
                    }
                    if ("client3".equals(config.nickname)) {
                        renderer.gateActionOnHand(4L);
                    }
                } else if ("client3".equals(config.nickname)) {
                    renderer.gateActionOnHand(phase == 1 ? 4L : 5L);
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                if (phase == 0) {
                    await(() -> renderer.spectatorNicknames().stream()
                                    .anyMatch(nick -> "client1".equals(nick)
                                    || "client2".equals(nick)),
                            Duration.ofSeconds(150),
                            "forced human spectator before recovery");
                    renderer.foldAutomatically(false);
                    marker("SPECTATOR_SETUP", "nick=" + config.nickname
                            + " spectators="
                            + renderer.spectatorNicknames().stream()
                                    .sorted().collect(java.util.stream
                                            .Collectors.joining(",")));
                }
                if (phase == 0 && "client3".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(150),
                            "first spectator recovery boundary");
                    marker("ACTION_GATE_REACHED", "scenario="
                            + config.scenario + " nick=client3 hand=4 cycle=0");
                }
                if (phase == 0 && config.host()) {
                    requireCommand("FORCE_RECOVER");
                    table.commands().submit(new TableCommand.StopGame());
                    marker("FORCE_RECOVER_REQUESTED", "hand=4 cycle=1");
                }
                if (phase == 1 && "client3".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(150),
                            "crash recovery action boundary");
                    marker("ACTION_GATE_REACHED", "scenario="
                            + config.scenario + " nick=client3 hand=4 cycle=1");
                    requireCommand("CRASH_NOW");
                    Runtime.getRuntime().halt(23);
                }
                if (phase == 2 && "client3".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(150),
                            "restarted client action boundary");
                    marker("ACTION_GATE_REACHED", "scenario="
                            + config.scenario + " nick=client3 hand=5 cycle=2");
                    requireCommand("RELEASE_FINAL_ACTION");
                    renderer.releaseHeldAction();
                    marker("FINAL_ACTION_RELEASED", "nick=client3 hand=5");
                }

                await(renderer::isClosed, Duration.ofMinutes(5),
                        phase == 2 ? "double recovery completion"
                                : "double recovery stop");
                assertPhaseReconnects(config, renderer,
                        phase == 1 ? Set.of("client3") : Set.of());
                if (phase < 2) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("double recovery phase "
                                + phase + " was not recoverable: "
                                + renderer.summary().reason());
                    }
                    marker("RECOVERABLE_STOP", "hand=4 cycle="
                            + (phase + 1) + " nick=" + config.nickname);
                    continue;
                }

                if (renderer.sawLocalSpectator()
                        && !renderer.returnedAfterSpectating()) {
                    renderer.assertCompleteAsPassiveObserver(4,
                            expectedPlayers, expectedPlayers);
                } else {
                    renderer.assertComplete(4);
                }
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("double recovery durable hand "
                            + "count: expected " + config.hands + " but was "
                            + renderer.summary().handCount());
                }
                marker("DOUBLE_RECOVERY_FINAL", "nick=" + config.nickname
                        + " spectator=" + renderer.sawLocalSpectator()
                        + " playing=" + renderer.playingNicknames().contains(
                                config.nickname));
                emitFinalOutcome(renderer);
            }
        }
    }

    private static void runTransportChaos(Config config, Path home,
            DatabaseService database) throws Exception {
        int expectedPlayers = config.clients + config.bots + 1;
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        for (int phase = 0; phase <= 1; phase++) {
            boolean recovering = phase == 1;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? recoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow())
                    : request(config);
            try (NetworkLobbyGateway gateway
                        = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (!recovering) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial");
                    prepareLobby(config, lobby, expectedPlayers);
                } else {
                    if (config.host()) {
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=1");
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == expectedPlayers,
                            Duration.ofSeconds(120),
                            "transport chaos recovery lobby");
                    marker("RECOVERY_LOBBY_READY", "players="
                            + expectedPlayers + " cycle=1");
                }
                if (config.host()) {
                    requireCommand(recovering
                            ? "START_RECOVERED_GAME" : "START_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    marker(recovering ? "RECOVERED_GAME_START_REQUESTED"
                            : "GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + phase);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        expectedPlayers, productTable, lobby);
                if (!recovering && config.host()) {
                    renderer.gateActionOnHand(2L);
                } else if (!recovering
                        && "client1".equals(config.nickname)) {
                    renderer.gateActionOnHand(1L);
                } else if (recovering
                        && "client3".equals(config.nickname)) {
                    renderer.gateActionOnHand(4L);
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                if (!recovering && "client1".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(90),
                            "transport chaos first action gate");
                    marker("ACTION_GATE_REACHED", "scenario=transport-chaos"
                            + " nick=client1 hand=1");
                    requireCommand("DROP_SOCKET");
                    closeNativeClientSocket(lobby);
                    await(() -> peerReconnectionCount(lobby, "server") == 1,
                            Duration.ofSeconds(60), "first reconnection");
                    marker("RECONNECTED", "peer=server count=1 nick=client1");
                    requireCommand("DROP_SOCKET_AGAIN");
                    closeNativeClientSocket(lobby);
                    await(() -> peerReconnectionCount(lobby, "server") == 2,
                            Duration.ofSeconds(60), "relapse reconnection");
                    marker("RECONNECTED", "peer=server count=2 nick=client1");
                    renderer.releaseHeldAction();
                } else if (!recovering
                        && "client2".equals(config.nickname)) {
                    requireCommand("DROP_SOCKET");
                    closeNativeClientSocket(lobby);
                    await(() -> peerReconnectionCount(lobby, "server") == 1,
                            Duration.ofSeconds(60), "second peer reconnect");
                    marker("RECONNECTED", "peer=server count=1 nick=client2");
                } else if (!recovering && config.host()) {
                    await(() -> peerReconnectionCount(lobby, "client1") == 2
                                    && peerReconnectionCount(lobby,
                                            "client2") == 1,
                            Duration.ofSeconds(120),
                            "host dual cut and relapse counts");
                    marker("TRANSPORT_CUTS_COMPLETE",
                            "client1=2 client2=1");
                    await(renderer::hasHeldAction, Duration.ofSeconds(120),
                            "transport chaos host hand two gate");
                    marker("ACTION_GATE_REACHED", "scenario=transport-chaos"
                            + " nick=server hand=2");
                    requireCommand("PAUSE_RESUME_RECOVER");
                    table.commands().submit(new TableCommand.TogglePause());
                    await(() -> renderer.sawPaused() && renderer.isPaused(),
                            Duration.ofSeconds(45), "transport pause");
                    marker("PAUSE_STATE", "paused=true");
                    table.commands().submit(new TableCommand.TogglePause());
                    await(() -> renderer.resumedAfterPause()
                                    && !renderer.isPaused(),
                            Duration.ofSeconds(45), "transport resume");
                    marker("PAUSE_STATE", "paused=false");
                    table.commands().submit(new TableCommand.StopGame());
                    marker("FORCE_RECOVER_REQUESTED", "hand=2 cycle=1");
                } else if (recovering
                        && "client3".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(180),
                            "transport chaos later cut gate");
                    marker("ACTION_GATE_REACHED", "scenario=transport-chaos"
                            + " nick=client3 hand=4");
                    requireCommand("DROP_SOCKET");
                    closeNativeClientSocket(lobby);
                    await(() -> peerReconnectionCount(lobby, "server") == 1,
                            Duration.ofSeconds(60), "later reconnection");
                    marker("RECONNECTED", "peer=server count=1 nick=client3");
                    renderer.releaseHeldAction();
                }

                await(renderer::isClosed, Duration.ofMinutes(5),
                        recovering ? "transport chaos completion"
                                : "transport chaos recoverable stop");
                assertPhaseReconnects(config, renderer,
                        recovering ? Set.of("client3")
                                : Set.of("client1", "client2"));
                if (!recovering) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("transport chaos was not "
                                + "recoverable: "
                                + renderer.summary().reason());
                    }
                    marker("RECOVERABLE_STOP", "hand=2 cycle=1 nick="
                            + config.nickname);
                    continue;
                }
                renderer.assertComplete(4);
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("transport chaos durable hands: "
                            + renderer.summary().handCount());
                }
                emitFinalOutcome(renderer);
            }
        }
    }

    private static void runLifecycleChaos(Config config, Path home,
            DatabaseService database) throws Exception {
        int expectedPlayers = config.clients + config.bots + 1;
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        for (int phase = 0; phase <= 2; phase++) {
            boolean recovering = phase > 0;
            productTable.set(null);
            if (recovering && !config.host()) {
                requireCommand("REOPEN_RECOVERY_LOBBY");
            }
            NewGameRequest phaseRequest = config.host() && recovering
                    ? recoveryRequest(config,
                            new RecoverableGameRepository(database)
                                    .latestLocal().orElseThrow())
                    : request(config);
            try (NetworkLobbyGateway gateway
                        = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                home.resolve("network"), database);
                 LobbySession lobby = gateway.open(phaseRequest)
                         .get(30, TimeUnit.SECONDS)) {
                if (!recovering) {
                    marker("READY", "role=" + config.role + " nick="
                            + config.nickname + " port=" + config.port
                            + " phase=initial");
                    prepareLobby(config, lobby, expectedPlayers);
                } else {
                    if (config.host()) {
                        marker("RECOVERY_HOST_OPEN", "port=" + config.port
                                + " cycle=" + phase);
                    }
                    await(() -> lobby.snapshot().participants().size()
                                    == expectedPlayers,
                            Duration.ofSeconds(120),
                            "lifecycle recovery lobby " + phase);
                    marker("RECOVERY_LOBBY_READY", "players="
                            + expectedPlayers + " cycle=" + phase);
                }
                if (config.host()) {
                    requireCommand(recovering
                            ? "START_RECOVERED_GAME" : "START_GAME");
                    lobby.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    marker(recovering ? "RECOVERED_GAME_START_REQUESTED"
                            : "GAME_START_REQUESTED",
                            "hands=" + config.hands + " cycle=" + phase);
                }

                TableSession table = lobby.tableSession().toCompletableFuture()
                        .get(45, TimeUnit.SECONDS);
                GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                        expectedPlayers, productTable, lobby);
                if (phase == 0 && config.host()) {
                    renderer.gateActionOnHand(2L);
                } else if (phase == 0
                        && "client1".equals(config.nickname)) {
                    renderer.gateActionOnHand(1L);
                } else if (phase == 0
                        && "client2".equals(config.nickname)) {
                    renderer.gateActionOnHand(3L);
                } else if (phase == 1 && (config.host()
                        || "client1".equals(config.nickname))) {
                    renderer.gateActionOnHand(6L);
                } else if (phase == 1
                        && "client2".equals(config.nickname)) {
                    renderer.gateActionOnHand(5L);
                }
                table.attach(renderer).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                if (phase == 1 && (config.host()
                        || "client1".equals(config.nickname))) {
                    announceHeldAction(renderer, "scenario=lifecycle-chaos"
                            + " nick=" + config.nickname + " hand=6");
                } else if (phase == 0
                        && "client2".equals(config.nickname)) {
                    announceHeldAction(renderer, "scenario=lifecycle-chaos"
                            + " nick=client2 hand=3");
                }

                if (phase == 0 && "client1".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(90),
                            "lifecycle first action gate");
                    marker("ACTION_GATE_REACHED", "scenario=lifecycle-chaos"
                            + " nick=client1 hand=1");
                    requireCommand("DROP_SOCKET");
                    closeNativeClientSocket(lobby);
                    await(() -> peerReconnectionCount(lobby, "server") == 1,
                            Duration.ofSeconds(60), "lifecycle reconnect one");
                    marker("RECONNECTED", "peer=server count=1 nick=client1");
                    await(renderer::nativeCheckOrCallReady,
                            Duration.ofSeconds(30),
                            "lifecycle first reconnect native action control");
                    renderer.releaseHeldActionAndGate(3L,
                            TableSnapshot.Street.PREFLOP);
                    announceHeldAction(renderer, "scenario=lifecycle-chaos"
                            + " nick=client1 hand=3");
                } else if (phase == 0 && config.host()) {
                    await(() -> peerReconnectionCount(lobby, "client1") == 1,
                            Duration.ofSeconds(120),
                            "lifecycle host saw first reconnect");
                    await(renderer::hasHeldAction, Duration.ofSeconds(120),
                            "lifecycle host hand two gate");
                    marker("ACTION_GATE_REACHED", "scenario=lifecycle-chaos"
                            + " nick=server hand=2");
                    requireCommand("PAUSE_RESUME_CONTINUE");
                    table.commands().submit(new TableCommand.TogglePause());
                    await(() -> renderer.sawPaused() && renderer.isPaused(),
                            Duration.ofSeconds(45), "lifecycle pause");
                    marker("PAUSE_STATE", "paused=true cycle=1");
                    table.commands().submit(new TableCommand.TogglePause());
                    await(() -> renderer.resumedAfterPause()
                                    && !renderer.isPaused(),
                            Duration.ofSeconds(45), "lifecycle resume");
                    marker("PAUSE_STATE", "paused=false cycle=1");
                    renderer.releaseHeldActionAndGate(3L,
                            TableSnapshot.Street.PREFLOP);
                    announceHeldAction(renderer, "scenario=lifecycle-chaos"
                            + " nick=server hand=3");
                    marker("HAND_THREE_STOP_ARMED", "cycle=1");
                    requireCommand("STOP_RECOVERY");
                    table.commands().submit(new TableCommand.StopGame());
                    marker("FORCE_RECOVER_REQUESTED", "hand=3 cycle=1");
                } else if (phase == 1
                        && "client2".equals(config.nickname)) {
                    await(renderer::hasHeldAction, Duration.ofSeconds(180),
                            "lifecycle second client hand five gate");
                    marker("ACTION_GATE_REACHED", "scenario=lifecycle-chaos"
                            + " nick=client2 hand=5");
                    requireCommand("DROP_SOCKET");
                    closeNativeClientSocket(lobby);
                    await(() -> peerReconnectionCount(lobby, "server") == 1,
                            Duration.ofSeconds(60), "lifecycle reconnect two");
                    marker("RECONNECTED", "peer=server count=1 nick=client2");
                    await(renderer::nativeCheckOrCallReady,
                            Duration.ofSeconds(30),
                            "lifecycle second reconnect native action control");
                    renderer.releaseHeldActionAndGate(6L,
                            TableSnapshot.Street.PREFLOP);
                    announceHeldAction(renderer, "scenario=lifecycle-chaos"
                            + " nick=client2 hand=6");
                } else if (phase == 1 && config.host()) {
                    await(() -> peerReconnectionCount(lobby, "client2") == 1,
                            Duration.ofSeconds(180),
                            "lifecycle host saw second reconnect");
                    marker("SECOND_RECONNECT_SEEN", "nick=client2 count=1");
                    requireCommand("STOP_RECOVERY");
                    table.commands().submit(new TableCommand.StopGame());
                    marker("FORCE_RECOVER_REQUESTED", "hand=6 cycle=2");
                }

                await(renderer::isClosed, Duration.ofMinutes(6),
                        phase == 2 ? "lifecycle completion"
                                : "lifecycle recoverable stop " + phase);
                assertPhaseReconnects(config, renderer,
                        phase == 0 ? Set.of("client1")
                                : phase == 1 ? Set.of("client2") : Set.of());
                if (phase < 2) {
                    if (renderer.summary().reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                        throw new AssertionError("lifecycle cycle "
                                + (phase + 1) + " was not recoverable: "
                                + renderer.summary().reason());
                    }
                    marker("RECOVERABLE_STOP", "hand="
                            + (phase == 0 ? 3 : 6) + " cycle=" + (phase + 1)
                            + " nick=" + config.nickname);
                    continue;
                }
                renderer.assertComplete(2);
                if (renderer.summary().handCount() != config.hands) {
                    throw new AssertionError("lifecycle durable hands: "
                            + renderer.summary().handCount());
                }
                emitFinalOutcome(renderer);
            }
        }
    }

    private static void announceHeldAction(GdxScenarioRenderer renderer,
            String detail) {
        Thread watcher = new Thread(() -> {
            try {
                while (!renderer.hasHeldAction() && !renderer.isClosed()) {
                    Thread.sleep(10L);
                }
                if (renderer.hasHeldAction()) {
                    marker("ACTION_GATE_REACHED", detail);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "gdx-scenario-held-action");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void runRecoveredClient(Config config, Path home,
            DatabaseService database, int expectedPlayers) throws Exception {
        AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        try (NetworkLobbyGateway gateway
                    = GdxNetworkHumanProjectionIntegrationTest.gateway(
                            home.resolve("network"), database);
             LobbySession lobby = gateway.open(request(config))
                     .get(30, TimeUnit.SECONDS)) {
            marker("READY", "role=" + config.role + " nick="
                    + config.nickname + " port=" + config.port
                    + " phase=restarted");
            await(() -> lobby.snapshot().participants().size()
                            == expectedPlayers,
                    Duration.ofSeconds(90), "restarted client roster");
            marker("RECOVERY_LOBBY_READY", "players=" + expectedPlayers);
            GdxScenarioRenderer renderer = attachRenderer(lobby,
                    expectedPlayers, productTable);
            await(renderer::isClosed, Duration.ofMinutes(3),
                    "restarted client table completion");
            assertRecoveredCrashRejoin(config, renderer);
            emitFinalOutcome(renderer);
        }
    }

    private static void prepareLobby(Config config, LobbySession lobby,
            int expectedPlayers) throws Exception {
        if (config.host()) {
            await(() -> lobby.snapshot().participants().size()
                            == config.clients + 1,
                    Duration.ofSeconds(30), "human participants");
            for (int index = 0; index < config.bots; index++) {
                lobby.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
            }
        }
        await(() -> lobby.snapshot().participants().size()
                        == expectedPlayers,
                Duration.ofSeconds(30), "complete lobby");
        marker("LOBBY_READY", "players=" + expectedPlayers);
    }

    private static void prepareInitialRecoveryGrowthLobby(Config config,
            LobbySession lobby, int expectedPlayers, int newcomerCount)
            throws Exception {
        if (config.host()) {
            await(() -> lobby.snapshot().participants().size()
                            == config.clients + 1 - newcomerCount,
                    Duration.ofSeconds(30), "initial human participants");
            for (int index = 0; index < config.bots; index++) {
                lobby.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
            }
        }
        await(() -> lobby.snapshot().participants().size()
                        == expectedPlayers,
                Duration.ofSeconds(30), "initial lobby before newcomer");
        marker("LOBBY_READY", "players=" + expectedPlayers);
    }

    private static GdxScenarioRenderer attachRenderer(LobbySession lobby,
            int expectedPlayers,
            AtomicReference<CoronaPokerGdxTable> productTable)
            throws Exception {
        TableSession table = lobby.tableSession().toCompletableFuture()
                .get(45, TimeUnit.SECONDS);
        GdxScenarioRenderer renderer = new GdxScenarioRenderer(table,
                expectedPlayers, productTable, lobby);
        table.attach(renderer).toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        return renderer;
    }

    private static void assertRecoveredCrashRejoin(Config config,
            GdxScenarioRenderer renderer) {
        assertPhaseReconnects(config, renderer, Set.of());
        renderer.assertComplete(1);
        if (renderer.summary().handCount() != config.hands) {
            throw new AssertionError("crash-rejoin durable hand count: expected "
                    + config.hands + " but was "
                    + renderer.summary().handCount());
        }
    }

    private static void emitFinalOutcome(GdxScenarioRenderer renderer) {
        marker("LEDGER", renderer.balancesByNickname().entrySet().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .map(entry -> entry.getKey() + "="
                        + Math.round(entry.getValue() * 100.0d))
                .collect(java.util.stream.Collectors.joining(",")));
        marker("CAPITAL", renderer.summary().balances().stream()
                .sorted(Comparator.comparing(
                        TableSessionSummary.PlayerBalance::nickname))
                .map(balance -> balance.nickname() + "="
                        + Math.round(balance.finalStack() * 100.0d) + "/"
                        + Math.round(balance.totalBuyin() * 100.0d) + "/"
                        + balance.rebuyCount())
                .collect(java.util.stream.Collectors.joining(",")));
        marker("HANDS_COMPLETE", "hands=" + renderer.completedHands()
                + " durableHands=" + renderer.summary().handCount()
                + " reason=" + renderer.summary().reason());
    }

    private static NetworkLobbyGateway gateway(Config config, Path data,
            DatabaseService database,
            AtomicReference<CoronaPokerGdxTable> productTable,
            AtomicInteger runItTwiceVotes,
            AtomicReference<GdxTableDialog> runItTwiceDialog,
            AtomicReference<GdxScenarioRenderer> scenarioRenderer) {
        return gateway(config, data, database, productTable, runItTwiceVotes,
                runItTwiceDialog, scenarioRenderer, null);
    }

    private static NetworkLobbyGateway gateway(Config config, Path data,
            DatabaseService database,
            AtomicReference<CoronaPokerGdxTable> productTable,
            AtomicInteger runItTwiceVotes,
            AtomicReference<GdxTableDialog> runItTwiceDialog,
            AtomicReference<GdxScenarioRenderer> scenarioRenderer,
            GdxGameLogSink gameLog) {
        if ("allin-rebuy".equals(config.scenario)) {
            return GdxNetworkHumanProjectionIntegrationTest
                    .automaticRebuyGateway(data, database);
        }
        if ("spectator-rebuy-cycle".equals(config.scenario)) {
            return immediateRebuyGateway(config, data, database);
        }
        if ("spectator-recovery-mix".equals(config.scenario)) {
            return immediateRebuyGateway(config, data, database);
        }
        if ("allin-rit".equals(config.scenario)
                || "rit-network-cut".equals(config.scenario)) {
            GameDecisionSink decisions = new GdxGameDecisionSink(
                    GameText.keys(), dialog -> {
                        if (!"RUN IT TWICE".equals(dialog.title())) {
                            throw new AssertionError(
                                    "unexpected GDX decision dialog: "
                                    + dialog.title());
                        }
                        CoronaPokerGdxTable table = productTable.get();
                        if (table == null) {
                            throw new AssertionError(
                                    "RIT vote arrived before GDX table opened");
                        }
                        runItTwiceVotes.incrementAndGet();
                        runItTwiceDialog.set(dialog);
                        table.showDialog(dialog);
                    });
            return GdxNetworkHumanProjectionIntegrationTest.gateway(
                    data, database, decisions);
        }
        if ("straddle-post".equals(config.scenario)
                || "straddle-network-cut".equals(config.scenario)) {
            GameDecisionSink decisions = new GdxGameDecisionSink(
                    GameText.keys(), dialog -> {
                        if (!"STRADDLE".equals(dialog.title())) {
                            throw new AssertionError(
                                    "unexpected GDX decision dialog: "
                                    + dialog.title());
                        }
                        CoronaPokerGdxTable table = productTable.get();
                        GdxScenarioRenderer renderer = scenarioRenderer.get();
                        if (table == null || renderer == null) {
                            throw new AssertionError(
                                    "straddle arrived before GDX table opened");
                        }
                        renderer.recordLocalStraddleDecision();
                        table.showDialog(dialog);
                        if (!table.resolveActiveDialogChoice(true)) {
                            throw new AssertionError(
                                    "native GDX straddle dialog did not accept");
                        }
                        table.advanceDialogState();
                        marker("STRADDLE_ACCEPTED", "nick="
                                + config.nickname + " hand="
                                + renderer.currentHand());
                    });
            return GdxNetworkHumanProjectionIntegrationTest.gateway(
                    data, database, decisions);
        }
        if (config.scenario.startsWith("allin-")) {
            return GdxNetworkHumanProjectionIntegrationTest.cinematicGateway(
                    data, database);
        }
        return gameLog == null
                ? GdxNetworkHumanProjectionIntegrationTest.gateway(data,
                        database)
                : GdxNetworkHumanProjectionIntegrationTest.gateway(data,
                        database, gameLog);
    }

    private static NetworkLobbyGateway immediateRebuyGateway(Config config,
            Path data, DatabaseService database) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        GameDecisionSink decisions = (GameDecisionSink)
                java.lang.reflect.Proxy.newProxyInstance(
                        GameDecisionSink.class.getClassLoader(),
                        new Class<?>[]{GameDecisionSink.class},
                        (proxy, method, arguments) -> {
                            if ("showRebuy".equals(method.getName())) {
                                GameDecisionSink.RebuyRequest request
                                        = (GameDecisionSink.RebuyRequest)
                                                arguments[0];
                                marker("REBUY_CHOICE_ACCEPTED", "nick="
                                        + config.nickname + " amount="
                                        + request.defaultAmount());
                                GameDecisionSink.RebuyResult accepted
                                        = new GameDecisionSink.RebuyResult(
                                                true,
                                                request.defaultAmount());
                                return new GameDecisionSink.RebuyHandle() {
                                    @Override
                                    public java.util.concurrent.CompletionStage<
                                            GameDecisionSink.RebuyResult>
                                            result() {
                                        return java.util.concurrent
                                                .CompletableFuture
                                                .completedFuture(accepted);
                                    }

                                    @Override
                                    public void close() {
                                    }
                                };
                            }
                            return method.invoke(fallback, arguments);
                        });
        return GdxNetworkHumanProjectionIntegrationTest.gateway(
                data, database, decisions);
    }

    private static void driveRunItTwiceDialog(GdxScenarioRenderer renderer,
            AtomicReference<CoronaPokerGdxTable> productTable,
            AtomicInteger votes, AtomicInteger resolutions) throws Exception {
        await(() -> productTable.get() != null, Duration.ofSeconds(10),
                "native GDX table");
        CoronaPokerGdxTable table = productTable.get();
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (!renderer.isClosed()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                        "allin-rit table did not close after GDX vote");
            }
            table.advanceDialogState();
            if (table.hasActiveDialog()
                    && table.resolveActiveDialogChoice(true)) {
                resolutions.incrementAndGet();
                table.advanceDialogState();
            }
            Thread.sleep(10L);
        }
        table.advanceDialogState();
        if (table.hasActiveDialog()) {
            throw new AssertionError("RIT dialog remained open after table close");
        }
        if (resolutions.get() != 1) {
            throw new AssertionError("expected one native GDX RIT choice, got "
                    + resolutions.get());
        }
        if (votes.get() != 1) {
            throw new AssertionError("expected one native GDX RIT vote, got "
                    + votes.get());
        }
    }

    private static void runRitNetworkCut(Config config, LobbySession lobby,
            GdxScenarioRenderer renderer,
            AtomicReference<CoronaPokerGdxTable> productTable,
            AtomicInteger votes,
            AtomicReference<GdxTableDialog> dialog,
            AtomicInteger resolutions) throws Exception {
        await(() -> productTable.get() != null, Duration.ofSeconds(10),
                "native GDX table");
        CoronaPokerGdxTable table = productTable.get();
        await(() -> votes.get() == 1 && table.hasActiveDialog(),
                Duration.ofSeconds(60), "native GDX RIT dialog");
        table.advanceDialogState();
        if ("client2".equals(config.nickname)) {
            await(() -> {
                table.advanceDialogState();
                return table.hasActiveDialog()
                        && dialog.get() != null
                        && dialog.get().message().contains(
                                "2 DOS VECES");
            }, Duration.ofSeconds(60), "two accepted RIT votes");
            marker("RIT_VOTE_GATE_REACHED", "nick=client2 votes=2");
            requireCommand("RELEASE_RIT_VOTE");
            resolveRunItTwiceDialog(table, resolutions);
            marker("RIT_VOTE_GATE_RELEASED", "nick=client2");
            return;
        }
        resolveRunItTwiceDialog(table, resolutions);
        marker("RIT_VOTE_ACCEPTED", "nick=" + config.nickname);
        if ("client1".equals(config.nickname)) {
            requireCommand("DROP_SOCKET");
            closeNativeClientSocket(lobby);
            marker("SOCKET_DROP_REQUESTED",
                    "nick=client1 phase=rit-vote");
            await(() -> peerReconnectionCount(lobby, "server") == 1,
                    Duration.ofSeconds(60), "RIT voter reconnection");
            marker("RECONNECTED",
                    "peer=server count=1 phase=rit-vote");
        } else if (config.host()) {
            await(() -> peerReconnectionCount(lobby, "client1") == 1,
                    Duration.ofSeconds(120), "host RIT voter reconnection");
            marker("RECONNECTED",
                    "peer=client1 count=1 phase=rit-vote");
        }
    }

    private static void resolveRunItTwiceDialog(CoronaPokerGdxTable table,
            AtomicInteger resolutions) {
        table.advanceDialogState();
        if (!table.hasActiveDialog()
                || !table.resolveActiveDialogChoice(true)) {
            throw new AssertionError(
                    "native GDX RIT dialog did not accept");
        }
        resolutions.incrementAndGet();
        table.advanceDialogState();
    }

    private static void runStraddleNetworkCut(Config config,
            LobbySession lobby) throws Exception {
        if (!config.host()) {
            String command = readCommand();
            if ("NO_DROP".equals(command)) {
                return;
            }
            if (!"DROP_SOCKET".equals(command)) {
                throw new IllegalStateException(
                        "unexpected straddle network command " + command);
            }
            closeNativeClientSocket(lobby);
            marker("SOCKET_DROP_REQUESTED", "nick=" + config.nickname
                    + " phase=straddle");
            await(() -> peerReconnectionCount(lobby, "server") == 1,
                    Duration.ofSeconds(60), "straddler reconnection");
            marker("RECONNECTED",
                    "peer=server count=1 phase=straddle");
            return;
        }
        await(() -> peerReconnectionCount(lobby, "client1") == 1
                        || peerReconnectionCount(lobby, "client2") == 1,
                Duration.ofSeconds(120), "host straddler reconnection");
        String peer = peerReconnectionCount(lobby, "client1") == 1
                ? "client1" : "client2";
        marker("RECONNECTED", "peer=" + peer
                + " count=1 phase=straddle");
    }

    private static void runAllInExit(Config config, TableSession table,
            GdxScenarioRenderer renderer) throws Exception {
        if (!"client1".equals(config.nickname)) {
            return;
        }
        await(renderer::hasHeldAction, Duration.ofSeconds(45),
                "ordered all-in exit action gate");
        marker("ACTION_GATE_REACHED", "scenario=" + config.scenario
                + " nick=client1 hand=1");
        String expectedCommand = "allin-controlled-exit".equals(config.scenario)
                ? "ALLIN_THEN_CONTROLLED_EXIT" : "ALLIN_THEN_CRASH";
        requireCommand(expectedCommand);
        renderer.submitHeldAllIn(() -> { });
        marker("ORDERED_ALLIN_ACTION_CLICKED", "nick=client1 hand=1");
        if ("allin-abrupt-exit".equals(config.scenario)) {
            Runtime.getRuntime().halt(23);
        }
        table.commands().submit(new TableCommand.ExitGame());
        marker("CONTROLLED_EXIT_SENT", "nick=client1 after=all-in");
    }

    private static void runAllInReconnect(Config config, LobbySession lobby,
            GdxScenarioRenderer renderer) throws Exception {
        if ("client1".equals(config.nickname)) {
            await(renderer::hasHeldAction, Duration.ofSeconds(45),
                    "ordered all-in action gate");
            marker("ACTION_GATE_REACHED", "scenario=allin-reconnect"
                    + " nick=client1 hand=1");
            requireCommand("ALLIN_THEN_DROP_SOCKET");
            renderer.submitHeldAllIn(() -> { });
            marker("ORDERED_ALLIN_ACTION_CLICKED", "nick=client1 hand=1");
            closeNativeClientSocket(lobby);
            marker("SOCKET_DROP_REQUESTED",
                    "nick=client1 hand=1 after=all-in");
            await(() -> peerReconnectionCount(lobby, "server") == 1,
                    Duration.ofSeconds(60), "all-in client reconnection");
            marker("RECONNECTED",
                    "peer=server count=1 hand=1 after=all-in");
        } else if (config.host()) {
            await(() -> peerReconnectionCount(lobby, "client1") == 1,
                    Duration.ofSeconds(120), "host all-in client reconnect");
            marker("RECONNECTED",
                    "peer=client1 count=1 hand=1 after=all-in");
        }
    }

    private static void runReconnectScenario(Config config, LobbySession lobby,
            GdxScenarioRenderer renderer) throws Exception {
        if ("reconnect-every-street".equals(config.scenario)) {
            runReconnectEveryStreet(config, lobby, renderer);
            return;
        }
        if ("reconnect-storm".equals(config.scenario)) {
            runReconnectStorm(config, lobby, renderer);
            return;
        }
        if ("dual-reconnect".equals(config.scenario)
                || "host-channel-flap".equals(config.scenario)) {
            runConcurrentReconnect(config, lobby, renderer);
            return;
        }
        int reconnectingClients = "reconnect-twice".equals(config.scenario)
                ? 2 : 1;
        int clientNumber = config.nickname.startsWith("client")
                ? Integer.parseInt(config.nickname.substring("client".length()))
                : 0;
        if (clientNumber >= 1 && clientNumber <= reconnectingClients) {
            int hand = clientNumber;
            await(renderer::hasHeldAction, Duration.ofSeconds(30),
                    "reconnect action gate");
            marker("ACTION_GATE_REACHED",
                    "scenario=" + config.scenario + " nick="
                    + config.nickname + " hand=" + hand);
            requireCommand("DROP_SOCKET");
            closeNativeClientSocket(lobby);
            marker("SOCKET_DROP_REQUESTED", "nick=" + config.nickname
                    + " hand=" + hand);
            await(() -> peerReconnectionCount(lobby, "server") == 1,
                    Duration.ofSeconds(45), "client reconnection");
            marker("RECONNECTED", "peer=server count=1 hand=" + hand);
            renderer.releaseHeldAction();
        } else if (config.host()) {
            for (int index = 1; index <= reconnectingClients; index++) {
                String peer = "client" + index;
                int hand = index;
                await(() -> peerReconnectionCount(lobby, peer) == 1,
                        Duration.ofSeconds(90), "host reconnection " + peer);
                marker("RECONNECTED", "peer=" + peer
                        + " count=1 hand=" + hand);
            }
        }
    }

    private static void runConcurrentReconnect(Config config, LobbySession lobby,
            GdxScenarioRenderer renderer) throws Exception {
        int reconnectingClients = "host-channel-flap".equals(config.scenario)
                ? config.clients : 2;
        int clientNumber = config.nickname.startsWith("client")
                ? Integer.parseInt(config.nickname.substring("client".length()))
                : 0;
        boolean first = clientNumber == 1;
        if (clientNumber >= 1 && clientNumber <= reconnectingClients) {
            if (first) {
                await(renderer::hasHeldAction, Duration.ofSeconds(45),
                        config.scenario + " action gate");
                marker("ACTION_GATE_REACHED", "scenario=" + config.scenario
                        + " nick=client1 hand=1");
            }
            requireCommand("DROP_SOCKET");
            closeNativeClientSocket(lobby);
            marker("SOCKET_DROP_REQUESTED", "nick=" + config.nickname
                    + " hand=1 count=1");
            await(() -> peerReconnectionCount(lobby, "server") == 1,
                    Duration.ofSeconds(60), "concurrent client reconnection "
                            + config.nickname);
            marker("RECONNECTED", "peer=server count=1 hand=1 nick="
                    + config.nickname);
            if (first) {
                renderer.releaseHeldAction();
            }
        } else if (config.host()) {
            for (int index = 1; index <= reconnectingClients; index++) {
                String peer = "client" + index;
                await(() -> peerReconnectionCount(lobby, peer) == 1,
                        Duration.ofSeconds(120), "host concurrent " + peer);
                marker("RECONNECTED", "peer=" + peer + " count=1 hand=1");
            }
        }
    }

    private static void runReconnectStorm(Config config, LobbySession lobby,
            GdxScenarioRenderer renderer) throws Exception {
        int clientNumber = config.nickname.startsWith("client")
                ? Integer.parseInt(config.nickname.substring("client".length()))
                : 0;
        if (clientNumber == 1 || clientNumber == 2) {
            int hand = clientNumber;
            int drops = clientNumber == 1 ? 2 : 1;
            await(renderer::hasHeldAction, Duration.ofSeconds(45),
                    "reconnect-storm action gate");
            marker("ACTION_GATE_REACHED", "scenario=" + config.scenario
                    + " nick=" + config.nickname + " hand=" + hand);
            for (int occurrence = 1; occurrence <= drops; occurrence++) {
                requireCommand("DROP_SOCKET");
                closeNativeClientSocket(lobby);
                marker("SOCKET_DROP_REQUESTED", "nick=" + config.nickname
                        + " hand=" + hand + " count=" + occurrence);
                int expectedCount = occurrence;
                await(() -> peerReconnectionCount(lobby, "server")
                                == expectedCount,
                        Duration.ofSeconds(60), "storm client reconnection "
                                + config.nickname + " #" + expectedCount);
                marker("RECONNECTED", "peer=server count=" + expectedCount
                        + " hand=" + hand + " nick=" + config.nickname);
            }
            renderer.releaseHeldAction();
        } else if (config.host()) {
            await(() -> peerReconnectionCount(lobby, "client1") == 2,
                    Duration.ofSeconds(120), "host storm client1 #2");
            marker("RECONNECTED", "peer=client1 count=2 hand=1");
            await(() -> peerReconnectionCount(lobby, "client2") == 1,
                    Duration.ofSeconds(120), "host storm client2 #1");
            marker("RECONNECTED", "peer=client2 count=1 hand=2");
        }
    }

    private static void runReconnectEveryStreet(Config config,
            LobbySession lobby, GdxScenarioRenderer renderer) throws Exception {
        TableSnapshot.Street[] streets = {
            TableSnapshot.Street.PREFLOP,
            TableSnapshot.Street.FLOP,
            TableSnapshot.Street.TURN,
            TableSnapshot.Street.RIVER
        };
        if (config.reconnectEveryStreetClient()) {
            for (int index = 0; index < streets.length; index++) {
                int hand = index + 1;
                await(renderer::hasHeldAction, Duration.ofSeconds(45),
                        "reconnect action gate at " + streets[index]);
                marker("ACTION_GATE_REACHED", "scenario=" + config.scenario
                        + " nick=" + config.nickname + " hand=" + hand
                        + " street=" + streets[index]);
                requireCommand("DROP_SOCKET");
                closeNativeClientSocket(lobby);
                marker("SOCKET_DROP_REQUESTED", "nick=" + config.nickname
                        + " hand=" + hand + " street=" + streets[index]);
                int occurrence = hand;
                await(() -> peerReconnectionCount(lobby, "server")
                                == occurrence,
                        Duration.ofSeconds(60), "client reconnection " + hand);
                marker("RECONNECTED", "peer=server count=" + occurrence
                        + " hand=" + hand + " street=" + streets[index]);
                await(renderer::nativeCheckOrCallReady,
                        Duration.ofSeconds(30),
                        "native action control after reconnect at "
                                + streets[index]);
                if (index + 1 < streets.length) {
                    renderer.releaseHeldActionAndGate(hand + 1L,
                            streets[index + 1]);
                } else {
                    renderer.releaseHeldAction();
                }
            }
        } else if (config.host()) {
            for (int occurrence = 1; occurrence <= streets.length;
                    occurrence++) {
                int count = occurrence;
                await(() -> peerReconnectionCount(lobby, "client1") == count,
                        Duration.ofSeconds(120),
                        "host reconnection client1 #" + count);
                marker("RECONNECTED", "peer=client1 count=" + count
                        + " hand=" + count + " street="
                        + streets[count - 1]);
            }
        }
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

    private static boolean clientReconnectStarted(LobbySession session) {
        try {
            Object transport = field(session, "resource");
            Object connection = field(transport, "serverConnection");
            return (boolean) field(connection, "reconnecting")
                    || (int) field(connection, "reconnectionCount") > 0;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot inspect reconnect attempt",
                    failure);
        }
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void runPauseResume(Config config, TableSession table,
            GdxScenarioRenderer renderer) throws Exception {
        if (config.host()) {
            await(renderer::hasHeldAction, Duration.ofSeconds(30),
                    "pause action gate");
            marker("ACTION_GATE_REACHED", "scenario=pause-resume nick="
                    + config.nickname);
            requireCommand("PAUSE_TOGGLE");
            table.commands().submit(new TableCommand.TogglePause());
            await(() -> renderer.sawPaused() && renderer.isPaused(),
                    Duration.ofSeconds(15), "paused state");
            marker("PAUSE_STATE", "paused=true");
            requireCommand("PAUSE_TOGGLE");
            table.commands().submit(new TableCommand.TogglePause());
            await(() -> renderer.resumedAfterPause() && !renderer.isPaused(),
                    Duration.ofSeconds(15), "resumed state");
            marker("PAUSE_STATE", "paused=false");
            renderer.releaseHeldAction();
            return;
        }
        await(() -> renderer.sawPaused() && renderer.isPaused(),
                Duration.ofSeconds(45), "remote paused state");
        marker("PAUSE_STATE", "paused=true");
        await(() -> renderer.resumedAfterPause() && !renderer.isPaused(),
                Duration.ofSeconds(45), "remote resumed state");
        marker("PAUSE_STATE", "paused=false");
    }

    private static void requireCommand(String expected) throws Exception {
        String command = readCommand();
        if (!expected.equals(command)) {
            throw new IllegalStateException("expected " + expected
                    + ", received " + command);
        }
    }

    private static NewGameRequest request(Config config) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        config.host() ? NewGameConnectionDraft.Mode.CREATE
                                : NewGameConnectionDraft.Mode.JOIN,
                        config.nickname, "", "127.0.0.1",
                        Integer.toString(config.port), null, false, false,
                        null);
        if (!config.host()) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(config.hands);
        table.setThinkTime(false);
        if ("reconnect-every-street".equals(config.scenario)) {
            // This scenario must reach a live decision on each street. Its
            // oracle is transport reconnection, not short-stack elimination;
            // keep the real no-limit game and bot, but give every seat enough
            // depth that a seeded four-hand run cannot accidentally consume
            // the requested river action before the socket cut is exercised.
            table.setMaxBuyinBb(NewGameTableDraft.MAX_BUYIN_BB);
            table.setBuyin(table.maximumBuyin());
        }
        if ("allin-rebuy".equals(config.scenario)
                || "spectator-rebuy-cycle".equals(config.scenario)
                || "spectator-recovery-mix".equals(config.scenario)
                || "human-bust-exit-rejoin-rebuy".equals(config.scenario)
                || "spectator-double-recovery-crash-mix".equals(
                        config.scenario)
                || config.scenario.startsWith("bot-bust-recover-")) {
            table.setRebuy(true);
        }
        if (config.scenario.startsWith("bot-bust-recover-")) {
            table.setBotRebuy(false);
        }
        if ("allin-rit".equals(config.scenario)
                || "rit-network-cut".equals(config.scenario)) {
            table.setRunItTwice(true);
        }
        if ("straddle-post".equals(config.scenario)
                || "straddle-network-cut".equals(config.scenario)) {
            table.setStraddle(true);
        }
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest recoveryRequest(Config config,
            RecoverableGameRepository.RecoverableGame recovered) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        NewGameConnectionDraft.Mode.RECOVER,
                        config.nickname, "", "127.0.0.1",
                        Integer.toString(config.port), null, false, true,
                        recovered.id());
        NewGameTableDraft table = NewGameTableDraft.from(
                recovered.settings());
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest botRecoveryRequest(Config config,
            RecoverableGameRepository.RecoverableGame recovered,
            boolean enableBotRebuy) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        NewGameConnectionDraft.Mode.RECOVER,
                        config.nickname, "", "127.0.0.1",
                        Integer.toString(config.port), null, false, true,
                        recovered.id());
        NewGameTableDraft table = NewGameTableDraft.from(
                recovered.settings());
        table.setRebuy(true);
        table.setBotRebuy(enableBotRebuy);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static void awaitStartCommand() throws Exception {
        String command = readCommand();
        if (!"START_GAME".equals(command)) {
            throw new IllegalStateException(
                    "expected START_GAME, received " + command);
        }
    }

    private static String readCommand() throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(
                System.in, StandardCharsets.UTF_8));
        return input.readLine();
    }

    private static void assertOutcome(Config config,
            GdxScenarioRenderer renderer) {
        // An EXITED renderer is already leaving its own table while the other
        // JVMs independently tear down their sockets. Connectivity snapshots
        // observed by that departing process are not user-visible outcomes.
        // Survivors remain strict: they must project the voluntary departure
        // and may never misclassify it as a reconnect.
        if (renderer.summary().reason()
                != TableSessionSummary.CloseReason.EXITED) {
            Set<String> allowedReconnects = allowedVisibleReconnects(config);
            renderer.assertNoUnexpectedReconnects(allowedReconnects);
            renderer.assertShowedExpectedReconnects(
                    requiredVisibleReconnects(config));
        }
        if ("normal".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            return;
        }
        if ("raise-mix".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            if (renderer.acceptedRaiseActions() < 3) {
                throw new AssertionError("raise-mix accepted only "
                        + renderer.acceptedRaiseActions() + " raises");
            }
            return;
        }
        if ("allin-single-board".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            if (!renderer.sawAllInAction()
                    || !renderer.sawAllInCinematic()) {
                throw new AssertionError(
                        "allin-single-board omitted action or cinematic");
            }
            if ("client1".equals(config.nickname)
                    && !renderer.hasAcceptedLocalAllIn()) {
                throw new AssertionError(
                        "allin-single-board client ALL-IN was not accepted");
            }
            if (renderer.completedRunItTwiceBoards()) {
                throw new AssertionError(
                        "allin-single-board unexpectedly dealt two boards");
            }
            return;
        }
        if ("allin-rebuy".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            if (!renderer.sawAllInOnEveryHand(config.hands)) {
                throw new AssertionError(
                        "allin-rebuy omitted ALL-IN in one or more hands");
            }
            TableSessionSummary finalSummary = renderer.summary();
            if (finalSummary.balances().stream()
                    .mapToInt(TableSessionSummary.PlayerBalance::rebuyCount)
                    .sum() < 1
                    || finalSummary.balances().stream().noneMatch(balance
                            -> balance.totalBuyin() > 10d)) {
                throw new AssertionError(
                        "allin-rebuy never carried a rebuy into a later hand");
            }
            return;
        }
        if ("spectator-rebuy-cycle".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            boolean sawSpectator = renderer.sawLocalSpectator();
            marker("SPECTATOR_CYCLE", "nick=" + config.nickname
                    + " saw=" + sawSpectator
                    + " requested=" + renderer.requestedImmediateRebuy()
                    + " returned=" + renderer.returnedAfterSpectating());
            if (sawSpectator && (!renderer.requestedImmediateRebuy()
                    || !renderer.returnedAfterSpectating())) {
                throw new AssertionError("spectator-rebuy-cycle did not return "
                        + config.nickname + " to the active ring");
            }
            return;
        }
        if ("allin-rit".equals(config.scenario)
                || "rit-network-cut".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            if (!renderer.sawAllInAction()
                    || !renderer.hasAcceptedLocalAllIn()) {
                throw new AssertionError(
                        "allin-rit omitted the local accepted ALL-IN");
            }
            if (!renderer.completedRunItTwiceBoards()) {
                throw new AssertionError(
                        "allin-rit did not complete both boards");
            }
            return;
        }
        if ("straddle-post".equals(config.scenario)
                || "straddle-network-cut".equals(config.scenario)) {
            renderer.assertStraddleComplete(config.hands);
            return;
        }
        if ("pause-resume".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            if (!renderer.sawPaused() || !renderer.resumedAfterPause()) {
                throw new AssertionError(
                        "pause-resume transitions were not projected");
            }
            return;
        }
        if (config.reconnectScenario()) {
            renderer.assertComplete(config.hands);
            return;
        }
        if ("allin-reconnect".equals(config.scenario)) {
            renderer.assertComplete(config.hands);
            if (!renderer.sawAllInAction()) {
                throw new AssertionError(
                        "allin-reconnect did not project the accepted ALL-IN");
            }
            return;
        }
        if ("allin-controlled-exit".equals(config.scenario)) {
            TableSessionSummary.CloseReason expected = config.host()
                    ? TableSessionSummary.CloseReason.COMPLETED
                    : config.disruptedClient()
                            ? TableSessionSummary.CloseReason.EXITED
                            : TableSessionSummary.CloseReason.COMPLETED;
            if (renderer.summary().reason() != expected) {
                throw new AssertionError("allin-controlled-exit reason: expected "
                        + expected + " but was " + renderer.summary().reason());
            }
            if (!config.disruptedClient()) {
                renderer.assertCompleteWithHistoricalBalances(
                        config.hands, config.clients + config.bots + 1);
                renderer.assertHandStartedWithPlayers(2, Set.of(
                        "server", "client2", "CoronaBot$1"));
                if (!renderer.sawAllInAction()
                        || !renderer.sawAllInCinematic()) {
                    throw new AssertionError(
                            "all-in exit lost its accepted action or cinematic");
                }
                if (!renderer.sawDeparture("client1")) {
                    throw new AssertionError(
                            "all-in exit did not project SE VA/LEAVES");
                }
                renderer.assertDepartureLabel("client1", "SE VA");
                renderer.assertNeverShowedReconnectFor("client1");
            }
            return;
        }
        if ("allin-abrupt-exit".equals(config.scenario)) {
            if (renderer.summary().reason()
                    != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                throw new AssertionError("allin-abrupt-exit must be recoverable: "
                        + renderer.summary().reason());
            }
            return;
        }
        if ("controlled-exit".equals(config.scenario)) {
            TableSessionSummary.CloseReason expected = config.disruptedClient()
                    ? TableSessionSummary.CloseReason.EXITED
                    : TableSessionSummary.CloseReason.COMPLETED;
            if (renderer.summary().reason() != expected) {
                throw new AssertionError("controlled-exit reason: expected "
                        + expected + " but was " + renderer.summary().reason());
            }
            if (!config.disruptedClient()) {
                renderer.assertCompleteWithHistoricalBalances(
                        config.hands,
                        config.clients + config.bots + 1);
                renderer.assertHandStartedWithPlayers(2, Set.of(
                        "server", "client2", "CoronaBot$1"));
                if (!renderer.sawDeparture("client1")) {
                    throw new AssertionError(
                            "controlled-exit did not project SE VA/LEAVES");
                }
                renderer.assertDepartureLabel("client1", "SE VA");
                renderer.assertNeverShowedReconnectFor("client1");
            }
            return;
        }
        if ("abrupt-exit".equals(config.scenario)) {
            if (renderer.summary().reason()
                    != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                throw new AssertionError("abrupt-exit must be recoverable: "
                        + renderer.summary().reason());
            }
            return;
        }
        if ("dual-abrupt-exit".equals(config.scenario)) {
            if (renderer.summary().reason()
                    != TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
                throw new AssertionError("dual-abrupt-exit must be recoverable: "
                        + renderer.summary().reason());
            }
            return;
        }
        if ("mixed-exit-crash".equals(config.scenario)) {
            TableSessionSummary.CloseReason expected = config.disruptedClient()
                    ? TableSessionSummary.CloseReason.EXITED
                    : TableSessionSummary.CloseReason.RECOVERABLE_STOP;
            if (renderer.summary().reason() != expected) {
                throw new AssertionError("mixed-exit-crash reason: expected "
                        + expected + " but was " + renderer.summary().reason());
            }
            return;
        }
        throw new IllegalArgumentException("unsupported scenario "
                + config.scenario);
    }

    private static Set<String> allowedVisibleReconnects(Config config) {
        return visibleReconnectsForNode(config,
                expectedDisconnectedPeers(config));
    }

    private static Set<String> visibleReconnectsForNode(Config config,
            Set<String> disconnectedPeers) {
        if (config.host() || !disconnectedPeers.contains(config.nickname)) {
            return disconnectedPeers;
        }
        java.util.HashSet<String> visible = new java.util.HashSet<>(
                disconnectedPeers);
        visible.remove(config.nickname);
        visible.add("server");
        return Set.copyOf(visible);
    }

    private static Set<String> requiredVisibleReconnects(Config config) {
        if ("straddle-network-cut".equals(config.scenario)) {
            return Set.of();
        }
        return requiredReconnectsForNode(config,
                expectedDisconnectedPeers(config));
    }

    private static Set<String> requiredReconnectsForNode(Config config,
            Set<String> disconnectedPeers) {
        if (config.host()) {
            return disconnectedPeers;
        }
        return disconnectedPeers.contains(config.nickname)
                ? Set.of("server") : Set.of();
    }

    private static void assertPhaseReconnects(Config config,
            GdxScenarioRenderer renderer, Set<String> disconnectedPeers) {
        renderer.assertNoUnexpectedReconnects(
                visibleReconnectsForNode(config, disconnectedPeers));
        renderer.assertShowedExpectedReconnects(
                requiredReconnectsForNode(config, disconnectedPeers));
    }

    private static Set<String> expectedDisconnectedPeers(Config config) {
        return switch (config.scenario) {
            case "reconnect-midhand", "reconnect-every-street",
                    "allin-reconnect", "rit-network-cut" -> Set.of("client1");
            case "reconnect-twice", "reconnect-storm",
                    "dual-reconnect" -> Set.of("client1", "client2");
            case "host-channel-flap" -> java.util.stream.IntStream.rangeClosed(
                    1, config.clients).mapToObj(index -> "client" + index)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            // The accepted straddler is selected by the real position rotation.
            // The outer oracle proves that exactly that peer performed the cut;
            // until the node receives that identity, either remote human is a
            // legitimate transient observation.
            case "straddle-network-cut" -> Set.of("client1", "client2");
            case "abrupt-exit", "allin-abrupt-exit" -> Set.of("client1");
            case "dual-abrupt-exit" -> Set.of("client1", "client2");
            case "mixed-exit-crash" -> Set.of("client2");
            default -> Set.of();
        };
    }

    private static void await(BooleanSupplier condition, Duration timeout,
            String description) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("timeout waiting for "
                        + description);
            }
            Thread.sleep(10L);
        }
    }

    private static void marker(String type, String detail) {
        System.out.println("CP_GDX_E2E_" + type + " " + detail);
        System.out.flush();
    }

    private record Config(String role, String nickname, int port,
            int clients, int bots, int hands, String scenario, String phase) {

        static Config parse(String[] args) {
            if (args.length != 7 && args.length != 8) {
                throw new IllegalArgumentException("expected: role nick port "
                        + "clients bots hands scenario [phase]");
            }
            Config value = new Config(args[0], args[1],
                    Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]),
                    args[6], args.length == 8 ? args[7] : "initial");
            if (!value.host() && !"client".equals(value.role)) {
                throw new IllegalArgumentException("role must be host or client");
            }
            if (value.clients < 1 || value.clients + value.bots + 1 > 10
                    || value.hands < 1) {
                throw new IllegalArgumentException("invalid table topology");
            }
            return value;
        }

        boolean host() {
            return "host".equals(role);
        }

        boolean restarted() {
            return "restarted".equals(phase);
        }

        boolean disruptedClient() {
            return !host() && "client1".equals(nickname)
                    && ("controlled-exit".equals(scenario)
                    || "allin-controlled-exit".equals(scenario)
                    || "abrupt-exit".equals(scenario)
                    || "dual-abrupt-exit".equals(scenario)
                    || "mixed-exit-crash".equals(scenario));
        }

        boolean actionGateNode() {
            return disruptedClient()
                    || (!host() && "allin-reconnect".equals(scenario)
                    && "client1".equals(nickname))
                    || (!host() && ("allin-controlled-exit".equals(scenario)
                    || "allin-abrupt-exit".equals(scenario))
                    && "client1".equals(nickname))
                    || (!host() && reconnectScenario()
                    && ("client1".equals(nickname)
                    || (("reconnect-twice".equals(scenario)
                    || "reconnect-storm".equals(scenario))
                    && "client2".equals(nickname))))
                    || (host() && "pause-resume".equals(scenario));
        }

        boolean reconnectScenario() {
            return "reconnect-midhand".equals(scenario)
                    || "reconnect-twice".equals(scenario)
                    || "reconnect-every-street".equals(scenario)
                    || "reconnect-storm".equals(scenario)
                    || "dual-reconnect".equals(scenario)
                    || "host-channel-flap".equals(scenario);
        }

        boolean reconnectEveryStreetClient() {
            return "reconnect-every-street".equals(scenario)
                    && "client1".equals(nickname);
        }

        long gatedHand() {
            return ("reconnect-twice".equals(scenario)
                    || "reconnect-storm".equals(scenario))
                    && "client2".equals(nickname) ? 2L : 1L;
        }
    }
}
