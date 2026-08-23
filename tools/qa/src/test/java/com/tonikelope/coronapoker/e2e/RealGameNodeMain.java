package com.tonikelope.coronapoker.e2e;

import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.EmojiPanel;
import com.tonikelope.coronapoker.GameFrame;
import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.IdentityManager;
import com.tonikelope.coronapoker.Init;
import com.tonikelope.coronapoker.LocalPlayer;
import com.tonikelope.coronapoker.NewGameDialog;
import com.tonikelope.coronapoker.RunItTwiceDialog;
import com.tonikelope.coronapoker.VoluntaryStraddleDialog;
import com.tonikelope.coronapoker.WaitingRoomFrame;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

/**
 * One real CoronaPoker peer for the opt-in loopback E2E lane.
 *
 * This is deliberately a test-source entry point, not another poker engine. It
 * mounts the production waiting room/table, lets the production socket stack
 * connect the peers and starts the production Crupier. Swing components exist
 * because Crupier is currently coupled to them, but no frame is made visible
 * and actions are selected through the real LocalPlayer buttons.
 */
public final class RealGameNodeMain {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration ACCELERATED_STALL_TIMEOUT = Duration.ofSeconds(
            Long.getLong("coronapoker.qa.stallSeconds", 120L));
    private static final WindowMode WINDOW_MODE = WindowMode.parse(
            System.getProperty("coronapoker.qa.windowMode", "hidden"));
    private static final int SCREEN_NUMBER = Integer.getInteger("coronapoker.qa.screen", 2);
    private static final boolean ANIMATIONS = Boolean.getBoolean("coronapoker.qa.animations");
    private static final String SCENARIO = System.getProperty(
            "coronapoker.qa.scenario", "normal");
    private static final Set<Window> POSITIONED_WINDOWS = java.util.Collections.newSetFromMap(
            new WeakHashMap<>());
    private static final Set<RunItTwiceDialog> RIT_DIALOGS_VOTED
            = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<VoluntaryStraddleDialog> STRADDLE_DIALOGS_ACCEPTED
            = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<NewGameDialog> RECOVERY_DIALOGS_SUBMITTED
            = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final AtomicReference<RunItTwiceDialog> GATED_RIT_DIALOG
            = new AtomicReference<>();
    private static final CountDownLatch PARENT_CLOSED = new CountDownLatch(1);
    private static final CountDownLatch LOBBY_CHECK_REQUESTED = new CountDownLatch(1);
    private static final CountDownLatch GAME_START_REQUESTED = new CountDownLatch(1);
    private static final Set<String> ARMED_ACTION_GATES
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> REACHED_ACTION_GATES
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Object ACTION_GATE_LOCK = new Object();
    private static final AtomicBoolean LOBBY_READY_REPORTED = new AtomicBoolean();
    private static volatile int CONFIGURED_CLIENTS;
    private static volatile int CONFIGURED_BOTS;
    private static volatile int ALL_IN_OBSERVED_HAND = -1;
    private static volatile Boolean LAST_PAUSE_STATE;

    private RealGameNodeMain() {
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace(System.err);
            marker("FAIL", "uncaught=" + error.getClass().getName());
        });

        NodeConfig config = NodeConfig.parse(args);
        CONFIGURED_CLIENTS = config.clients;
        CONFIGURED_BOTS = config.bots;
        configureRuntime(config);

        // Force-recover returns through the real launcher. Ordinary E2E startup
        // bypasses it only to configure the waiting room deterministically, so
        // mount a production Init now and keep it hidden by the window policy.
        if (isForceRecoverScenario()) {
            AtomicReference<Init> launcherRef = new AtomicReference<>();
            EventQueue.invokeAndWait(() -> {
                launcherRef.set(new Init());
                Init.VENTANA_INICIO = launcherRef.get();
                applyWindowPolicy(launcherRef.get());
            });
        }

        // The production launcher publishes WaitingRoomFrame immediately after
        // its constructor returns. Its constructor also schedules servidor()/
        // cliente() as its final step, which can win that tiny race on a fast
        // test machine. Quiesce the shared executor while constructing, publish
        // the singleton exactly like NewGameDialog, then start the same private
        // production network loop explicitly.
        Helpers.SHUTDOWN_THREAD_POOL();
        AtomicReference<WaitingRoomFrame> roomRef = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            roomRef.set(new WaitingRoomFrame(
                    config.host,
                    config.nick,
                    "127.0.0.1:" + config.port,
                    null,
                    null,
                    false));
            // Establish the requested monitor before the production game frame
            // derives its monitor from the waiting room. This also prevents a
            // hidden-mode startup flash from landing on the primary display.
            applyWindowPolicy(roomRef.get());
        });
        WaitingRoomFrame room = roomRef.get();
        WaitingRoomFrame.setInstance(room);
        Helpers.CREATE_THREAD_POOL();
        invokeNetworkStart(room, config.host);
        startControlThread();

        if (config.host) {
            await("server socket", START_TIMEOUT,
                    () -> room.getNet_server().getServer_socket() != null
                    && !room.getNet_server().getServer_socket().isClosed());
            int boundPort = room.getNet_server().getServer_socket().getLocalPort();
            // Port zero removes the parent-side reserve/close/bind race. Persist
            // the actual port so production force-recovery reopens the same
            // endpoint and existing clients reconnect to it normally.
            Helpers.PROPERTIES.setProperty("local_port", Integer.toString(boundPort));
            marker("READY", "role=host port=" + boundPort
                    + " testMode=" + GameFrame.TEST_MODE
                    + " windowMode=" + WINDOW_MODE.name().toLowerCase(Locale.ROOT));
            awaitParentBarrier(LOBBY_CHECK_REQUESTED, "initial lobby check");
            await("human clients", START_TIMEOUT,
                    () -> participantCounts(room).humans() == config.clients + 1);
            addBots(room, config.bots);
            await("bots", START_TIMEOUT,
                    () -> participantCounts(room).total()
                    == config.clients + config.bots + 1);
            LOBBY_READY_REPORTED.set(true);
            marker("LOBBY_READY", "clients=" + config.clients + " bots=" + config.bots);
            if (!GAME_START_REQUESTED.await(START_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("parent did not authorize game start");
            }
            invokeStartGame(room);
        } else {
            marker("READY", "role=client nick=" + config.nick
                    + " testMode=" + GameFrame.TEST_MODE
                    + " windowMode=" + WINDOW_MODE.name().toLowerCase(Locale.ROOT));
            if (config.lateRecoveryJoiner) {
                await("complete recovery human roster", Duration.ofMinutes(2), () -> {
                    ParticipantCounts counts = participantCounts(room);
                    return lobbyTopologyComplete(counts.humans(), config.clients + 1,
                            counts.total(), config.clients + config.bots + 1, true);
                });
                reportLobbyReady(room);
            } else {
                awaitParentBarrier(LOBBY_CHECK_REQUESTED, "initial lobby check");
            }
        }

        await("mounted GameFrame",
                config.lateRecoveryJoiner ? Duration.ofMinutes(2) : START_TIMEOUT,
                () -> localPlayerIfMounted() != null);

        Thread actionDriver = new Thread(() -> driveLocalActions(config.seed), "qa-real-game-action-driver");
        actionDriver.setDaemon(true);
        actionDriver.start();

        Duration gameTimeout = Duration.ofSeconds(Math.max(180L, config.hands * 45L));
        boolean handsComplete = awaitCompletedHands(config.hands, gameTimeout,
                expectsPermanentLocalExit(config));
        if (!handsComplete) {
            marker("EXPECTED_EXIT_COMPLETE", "role=client nick=" + config.nick
                    + " sqlCompleted=" + completedHands());
            PARENT_CLOSED.await();
            return;
        }

        GameFrame completedFrame = GameFrame.getInstance();
        Crupier crupier = completedFrame == null ? null : completedFrame.getCrupier();
        if (crupier == null
                && !RealGameScenarioContract.expectsTerminalMisdeal(SCENARIO)) {
            throw new IllegalStateException(
                    "game table disappeared after SQL completion outside a terminal MISDEAL");
        }
        marker("HANDS_COMPLETE", "role=" + (config.host ? "host" : "client")
                + " nick=" + config.nick
                + " requested=" + config.hands
                + " crupierHand=" + (crupier == null ? "unmounted" : crupier.getMano())
                + " tableState=" + (completedFrame == null
                        ? "closed-after-misdeal" : "mounted")
                + " sqlCompleted=" + completedHands());
        marker("LEDGER", latestLedgerSummary());

        // The parent owns the lifetime of all peers and stops them together once
        // every independent SQLite ledger has observed the completed hand(s).
        PARENT_CLOSED.await();
    }

    private static void configureRuntime(NodeConfig config) throws Exception {
        Path home = Path.of(System.getProperty("user.home"));
        Files.createDirectories(home);
        Init.SQL_FILE = home.resolve("coronapoker-e2e.db").toString();
        installWindowPolicy();

        Helpers.PROPERTIES.setProperty("sonidos", "false");
        Helpers.PROPERTIES.setProperty("musica", "false");
        Helpers.PROPERTIES.setProperty("musica_sala_espera", "false");
        Helpers.PROPERTIES.setProperty("animaciones", Boolean.toString(ANIMATIONS));
        Helpers.PROPERTIES.setProperty("auto_fullscreen", "false");
        // Direct initial startup bypasses NewGameDialog, but force-recover
        // returns through it. Persist the same connection fields a real user
        // entered so that recovery validates and reconnects to this E2E table.
        Helpers.PROPERTIES.setProperty("nick", config.nick);
        Helpers.PROPERTIES.setProperty("local_ip", "127.0.0.1");
        Helpers.PROPERTIES.setProperty("server_ip", "127.0.0.1");
        Helpers.PROPERTIES.setProperty("local_port", Integer.toString(config.port));
        Helpers.PROPERTIES.setProperty("server_port", Integer.toString(config.port));
        if (Boolean.getBoolean("coronapoker.qa.deterministicCrypto")) {
            // Explicit diagnostic mode only. Production and normal certification
            // keep the platform CSPRNG and have no dependency on this test class.
            Helpers.CSPRNG_GENERATOR = new SeededSecureRandom(config.seed);
            marker("CRYPTO_SEED", "mode=deterministic seed=" + config.seed);
        } else {
            Helpers.CSPRNG_GENERATOR = SecureRandom.getInstanceStrong();
            marker("CRYPTO_SEED", "mode=platform");
        }
        Helpers.setCoronaLocale();
        for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                javax.swing.UIManager.setLookAndFeel(info.getClassName());
                break;
            }
        }
        EmojiPanel.initClass();
        Helpers.GUI_FONT = Helpers.createAndRegisterFont(
                Helpers.class.getResourceAsStream("/fonts/McLaren-Regular.ttf"));
        if (!Helpers.initSQLITE()) {
            throw new IllegalStateException("cannot initialize isolated SQLite at " + Init.SQL_FILE);
        }
        IdentityManager identity = IdentityManager.initializeForNick(config.nick);
        if (!identity.isReady()) {
            throw new IllegalStateException("cannot initialize identity for " + config.nick
                    + ": " + identity.getLoadError());
        }

        GameFrame.SONIDOS = false;
        GameFrame.MUSICA = false;
        GameFrame.MUSICA_SALA = false;
        GameFrame.AUTO_FULLSCREEN = false;
        GameFrame.CONFIRM_ACTIONS = false;
        GameFrame.ANIMACIONES = ANIMATIONS;
        GameFrame.CINEMATICAS_PREF = ANIMATIONS;
        GameFrame.ANIMACION_REPARTO_PREF = ANIMATIONS;
        GameFrame.ANIMACION_CIEGAS_DEALER_PREF = ANIMATIONS;
        GameFrame.ANIMACION_APUESTAS_PREF = ANIMATIONS;
        GameFrame.ANIMACION_CONTADORES_PREF = ANIMATIONS;
        GameFrame.ANIMACION_CONTADOR_FINAL_PREF = ANIMATIONS;
        GameFrame.MANOS = config.hands;
        GameFrame.THINK_TIME = 10;
        GameFrame.THINK_TIME_ENABLED = true;
        GameFrame.SHOWDOWN_TIME = 5;
        GameFrame.RUN_IT_TWICE = isRitScenario();
        GameFrame.STRADDLE = SCENARIO.equals("straddle-post")
                || SCENARIO.equals("straddle-network-cut");
        GameFrame.RECOVER = false;
    }

    private static void addBots(WaitingRoomFrame room, int count) throws Exception {
        Method addBot = WaitingRoomFrame.class.getDeclaredMethod(
                "new_bot_buttonActionPerformed", java.awt.event.ActionEvent.class);
        addBot.setAccessible(true);
        for (int i = 0; i < count; i++) {
            int expected = room.getParticipantes().size() + 1;
            EventQueue.invokeAndWait(() -> {
                try {
                    addBot.invoke(room, new Object[]{null});
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            });
            await("bot " + (i + 1), START_TIMEOUT,
                    () -> room.getParticipantes().size() >= expected);
        }
    }

    private static void invokeStartGame(WaitingRoomFrame room) throws Exception {
        Method start = WaitingRoomFrame.class.getDeclaredMethod("startGameWork");
        start.setAccessible(true);
        start.invoke(room);
    }

    private static void invokeNetworkStart(WaitingRoomFrame room, boolean host) throws Exception {
        Method network = WaitingRoomFrame.class.getDeclaredMethod(host ? "servidor" : "cliente");
        network.setAccessible(true);
        network.invoke(room);
    }

    private static void driveLocalActions(long seed) {
        // The seed deliberately influences the harmless choice between a legal
        // check/call and fold. Raises/all-ins get dedicated scenarios later.
        java.util.Random random = new java.util.Random(seed);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                awaitArmedActionGate();
                EventQueue.invokeAndWait(() -> {
                    GameFrame frame = GameFrame.getInstance();
                    LocalPlayer local = localPlayerIfMounted();
                    if (frame == null || local == null) {
                        return;
                    }
                    if (!local.isTurno()) {
                        return;
                    }
                    // Gate inspection and action selection must be atomic with
                    // respect to the EDT. Otherwise the turn can become
                    // actionable between two snapshots and the automatic driver
                    // can steal an action owned by a causal scenario command.
                    if (automatedActionBlocked(ARMED_ACTION_GATES,
                            frame.getCrupier().getMano(), frame.getCrupier().getStreet())) {
                        return;
                    }
                    if (isAllInScenario()
                            && local.getPlayer_allin().isEnabled()) {
                        int hand = frame.getCrupier().getMano();
                        local.getPlayer_allin().doClick();
                        if (ALL_IN_OBSERVED_HAND != hand) {
                            ALL_IN_OBSERVED_HAND = hand;
                            marker("ALLIN_ACTION_CLICKED", "nick="
                                    + frame.getNick_local() + " hand=" + hand);
                        }
                    } else if ((requiresLiveStreetAfterPeerLoss() || isActionGateScenario())
                            && local.getPlayer_check().isEnabled()) {
                        // Destructive-exit scenarios must reach a street that needs the
                        // departed peer's crypto testament. Random preflop folds can end
                        // the hand normally and turn the promised MISDEAL into a no-op.
                        local.getPlayer_check().doClick();
                        marker("KEEPALIVE_ACTION_CLICKED", "nick="
                                + frame.getNick_local() + " hand="
                                + frame.getCrupier().getMano());
                    } else if (SCENARIO.equals("raise-mix")
                            && local.getPlayer_bet_button().isEnabled()
                            && random.nextInt(3) != 0) {
                        local.getPlayer_bet_button().doClick();
                        marker("BET_ACTION_CLICKED", "nick="
                                + frame.getNick_local() + " hand="
                                + frame.getCrupier().getMano());
                    } else if (local.getPlayer_check().isEnabled()
                            && (!local.getPlayer_fold().isEnabled() || random.nextInt(5) != 0)) {
                        local.getPlayer_check().doClick();
                    } else if (local.getPlayer_fold().isEnabled()) {
                        local.getPlayer_fold().doClick();
                    } else if (local.getPlayer_allin().isEnabled()) {
                        local.getPlayer_allin().doClick();
                    }
                });
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                marker("FAIL", "actionDriver=" + error.getClass().getName());
                return;
            }
        }
    }

    private static void startControlThread() {
        Thread controls = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    System.in, StandardCharsets.UTF_8))) {
                String command;
                while ((command = input.readLine()) != null) {
                    if (command.equals("CONTROLLED_EXIT")) {
                        invokeControlledClientExit();
                        marker("CONTROLLED_EXIT_SENT", "role=client");
                    } else if (command.equals("ALLIN_THEN_CONTROLLED_EXIT")) {
                        invokeAllInThen(PostAction.CONTROLLED_EXIT);
                    } else if (command.equals("ALLIN_THEN_DROP_SOCKET")) {
                        invokeAllInThen(PostAction.DROP_SOCKET);
                    } else if (command.equals("ALLIN_THEN_CRASH")) {
                        invokeAllInThen(PostAction.CRASH_PROCESS);
                    } else if (command.equals("RELEASE_RIT_VOTE")) {
                        releaseRitVoteGate();
                    } else if (command.equals("FORCE_RECOVER")) {
                        invokeForceRecover();
                        marker("FORCE_RECOVER_REQUESTED", "role=host");
                    } else if (command.equals("START_RECOVERED_GAME")) {
                        invokeRecoveredGameStart();
                        marker("RECOVERED_GAME_START_REQUESTED", "role=host");
                    } else if (command.equals("PAUSE_TOGGLE")) {
                        invokePauseToggle();
                    } else if (command.equals("DROP_SOCKET")) {
                        marker("SOCKET_DROP_ARMED", "role=client");
                        invokeSocketDrop();
                        marker("SOCKET_DROP_REQUESTED", "role=client");
                    } else if (command.startsWith("ARM_ACTION_GATE#")) {
                        String gate = parseActionGateCommand(command, "ARM_ACTION_GATE");
                        ARMED_ACTION_GATES.add(gate);
                        marker("ACTION_GATE_ARMED", "gate=" + gate);
                    } else if (command.startsWith("RELEASE_ACTION_GATE#")) {
                        String gate = parseActionGateCommand(command, "RELEASE_ACTION_GATE");
                        synchronized (ACTION_GATE_LOCK) {
                            ARMED_ACTION_GATES.remove(gate);
                            ACTION_GATE_LOCK.notifyAll();
                        }
                        marker("ACTION_GATE_RELEASED", "gate=" + gate);
                    } else if (command.equals("CHECK_LOBBY")) {
                        LOBBY_CHECK_REQUESTED.countDown();
                        marker("LOBBY_CHECK_RELEASED", "role=parent");
                    } else if (command.equals("START_GAME")) {
                        GAME_START_REQUESTED.countDown();
                        marker("GAME_START_REQUESTED", "role=host");
                    } else if (command.equals("STOP")) {
                        marker("STOPPING", "role=test-harness");
                        Runtime.getRuntime().halt(0);
                    } else if (!command.isBlank()) {
                        throw new IllegalArgumentException("unknown parent control: " + command);
                    }
                }
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                marker("FAIL", "control=" + error.getClass().getName());
            } finally {
                PARENT_CLOSED.countDown();
            }
        }, "qa-real-game-parent-controls");
        controls.setDaemon(true);
        controls.start();
    }

    private static String parseActionGateCommand(String command, String verb) {
        String[] parts = command.split("#", -1);
        if (parts.length != 3 || !verb.equals(parts[0])) {
            throw new IllegalArgumentException("invalid " + verb + " command");
        }
        int hand = Integer.parseInt(parts[1]);
        int street = Integer.parseInt(parts[2]);
        if (hand < 1 || street < Crupier.PREFLOP || street > Crupier.RIVER) {
            throw new IllegalArgumentException("invalid action gate hand/street");
        }
        return hand + "#" + street;
    }

    private static void awaitArmedActionGate() throws InterruptedException {
        ActionGateSnapshot snapshot = currentActionGateSnapshot();
        if (snapshot == null || !snapshot.inputReady()) {
            return;
        }
        String gate = snapshot.hand() + "#" + snapshot.street();
        if (!ARMED_ACTION_GATES.contains(gate)) {
            return;
        }
        if (REACHED_ACTION_GATES.add(gate)) {
            marker("ACTION_GATE_REACHED", "gate=" + gate
                    + " nick=" + snapshot.nick());
        }
        synchronized (ACTION_GATE_LOCK) {
            while (ARMED_ACTION_GATES.contains(gate)) {
                ACTION_GATE_LOCK.wait(100L);
            }
        }
    }

    private static ActionGateSnapshot currentActionGateSnapshot()
            throws InterruptedException {
        AtomicReference<ActionGateSnapshot> snapshot = new AtomicReference<>();
        try {
            EventQueue.invokeAndWait(() -> {
                GameFrame frame = GameFrame.getInstance();
                LocalPlayer local = localPlayerIfMounted();
                if (frame == null || local == null || !local.isTurno()) {
                    return;
                }
                Crupier crupier = frame.getCrupier();
                boolean inputReady = actionInputReady(isOrderedAllInScenario(),
                        local.getPlayer_check().isEnabled(),
                        local.getPlayer_fold().isEnabled(),
                        local.getPlayer_allin().isEnabled(),
                        local.getPlayer_bet_button().isEnabled());
                snapshot.set(new ActionGateSnapshot(crupier.getMano(),
                        crupier.getStreet(), frame.getNick_local(), inputReady));
            });
        } catch (java.lang.reflect.InvocationTargetException ex) {
            throw new IllegalStateException("cannot inspect action gate on EDT", ex.getCause());
        }
        return snapshot.get();
    }

    private static boolean isOrderedAllInScenario() {
        return RealGameScenarioContract.isOrderedAllIn(SCENARIO);
    }

    static boolean actionInputReady(boolean orderedAllIn, boolean checkEnabled,
            boolean foldEnabled, boolean allInEnabled, boolean betEnabled) {
        return orderedAllIn ? allInEnabled
                : checkEnabled || foldEnabled || allInEnabled || betEnabled;
    }

    static boolean automatedActionBlocked(Set<String> armedGates, int hand, int street) {
        return armedGates.contains(hand + "#" + street);
    }

    private static void invokeControlledClientExit() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            try {
                performControlledClientExitOnEdt();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException("cannot invoke production controlled EXIT", failure.get());
        }
    }

    private static void invokeAllInThen(PostAction postAction) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            try {
                GameFrame frame = GameFrame.getInstance();
                LocalPlayer local = localPlayerIfMounted();
                if (frame == null || local == null || frame.isPartida_local()) {
                    throw new IllegalStateException(
                            "ordered all-in disruption requires a mounted client table");
                }
                if (!local.isTurno() || !local.getPlayer_allin().isEnabled()) {
                    throw new IllegalStateException(
                            "ordered all-in disruption requires the client's enabled all-in turn");
                }
                int hand = frame.getCrupier().getMano();
                local.getPlayer_allin().doClick();
                ALL_IN_OBSERVED_HAND = hand;
                marker("ORDERED_ALLIN_ACTION_CLICKED", "nick="
                        + frame.getNick_local() + " hand=" + hand);

                switch (postAction) {
                    case CONTROLLED_EXIT -> {
                        performControlledClientExitOnEdt();
                        marker("CONTROLLED_EXIT_SENT", "role=client after=all-in");
                    }
                    case DROP_SOCKET -> {
                        marker("SOCKET_DROP_ARMED", "role=client after=all-in");
                        invokeSocketDrop();
                        marker("SOCKET_DROP_REQUESTED", "role=client after=all-in");
                    }
                    case CRASH_PROCESS -> {
                        marker("PROCESS_CRASH_REQUESTED", "role=client after=all-in");
                    }
                }
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException(
                    "cannot execute ordered all-in disruption", failure.get());
        }
        if (postAction == PostAction.CRASH_PROCESS) {
            // The marker above is flushed before halting. Runtime.halt models a
            // power/process loss without running cleanup hooks that would turn
            // this into a controlled EXIT.
            Runtime.getRuntime().halt(0);
        }
    }

    private static void performControlledClientExitOnEdt() throws Exception {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("controlled EXIT must run on the EDT");
        }
        GameFrame frame = GameFrame.getInstance();
        if (frame == null || frame.isPartida_local()) {
            throw new IllegalStateException("controlled EXIT requires a mounted client table");
        }
        Method exit = GameFrame.class.getDeclaredMethod("performControlledClientExit");
        exit.setAccessible(true);
        exit.invoke(frame);
    }

    private static void invokeForceRecover() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            try {
                GameFrame frame = GameFrame.getInstance();
                if (frame == null || !frame.isPartida_local()) {
                    throw new IllegalStateException("force recover requires a mounted host table");
                }
                frame.getCrupier().setForce_recover(true);
                Method exit = GameFrame.class.getDeclaredMethod("performImmediateHostExit");
                exit.setAccessible(true);
                exit.invoke(frame);
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException("cannot invoke production force recover", failure.get());
        }
    }

    private static void invokePauseToggle() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            try {
                GameFrame frame = GameFrame.getInstance();
                if (frame == null || !frame.isPartida_local()) {
                    throw new IllegalStateException("pause toggle requires a mounted host table");
                }
                frame.pauseTimba(null);
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException("cannot toggle production pause", failure.get());
        }
    }

    private static void invokeSocketDrop() {
        WaitingRoomFrame room = WaitingRoomFrame.getInstance();
        if (room == null || room.isServer()) {
            throw new IllegalStateException("socket drop requires a mounted client");
        }
        room.closeClientSocket();
    }

    private static void invokeRecoveredGameStart() throws Exception {
        WaitingRoomFrame room = WaitingRoomFrame.getInstance();
        if (room == null || !room.isServer() || !GameFrame.isRECOVER()) {
            throw new IllegalStateException(
                    "recovered start requires the mounted host recovery lobby");
        }
        long existingBots;
        synchronized (room.getParticipantes()) {
            existingBots = room.getParticipantes().values().stream()
                    .filter(participant -> participant != null && participant.isCpu())
                    .count();
        }
        int missingBots = CONFIGURED_BOTS - Math.toIntExact(existingBots);
        if (missingBots < 0) {
            throw new IllegalStateException("recovery lobby has unexpected extra bots");
        }
        addBots(room, missingBots);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            try {
                Method start = WaitingRoomFrame.class.getDeclaredMethod(
                        "continueStartGame", String.class);
                start.setAccessible(true);
                start.invoke(room, "");
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException(
                    "cannot invoke production recovered-game start", failure.get());
        }
    }

    private static void installWindowPolicy() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent windowEvent
                    && (windowEvent.getID() == WindowEvent.WINDOW_OPENED
                    || windowEvent.getID() == WindowEvent.WINDOW_ACTIVATED)) {
                applyWindowPolicy(windowEvent.getWindow());
                if (windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                    applyScenarioWindowAction(windowEvent.getWindow());
                }
            }
        }, java.awt.AWTEvent.WINDOW_EVENT_MASK);

        Thread keeper = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    applyWindowPolicyToAll();
                    Thread.sleep(25L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    marker("FAIL", "windowPolicy=" + ex.getClass().getName());
                    return;
                }
            }
        }, "qa-window-policy");
        keeper.setDaemon(true);
        keeper.start();
    }

    private static void acceptPendingStraddle() {
        if (!(SCENARIO.equals("straddle-post")
                || SCENARIO.equals("straddle-network-cut"))) {
            return;
        }
        GameFrame frame = GameFrame.getInstance();
        if (frame == null || frame.getCrupier() == null) {
            return;
        }
        VoluntaryStraddleDialog dialog = frame.getCrupier().getStraddle_local_dialog();
        if (dialog != null && STRADDLE_DIALOGS_ACCEPTED.add(dialog)) {
            dialog.accept();
            marker("STRADDLE_ACCEPTED", "nick=" + frame.getNick_local()
                    + " hand=" + frame.getCrupier().getMano());
        }
    }

    private static void applyWindowPolicyToAll() throws Exception {
        if (EventQueue.isDispatchThread()) {
            for (Window window : Window.getWindows()) {
                applyWindowPolicy(window);
            }
            observeScenarioStateOnEdt();
            return;
        }
        EventQueue.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                applyWindowPolicy(window);
            }
            observeScenarioStateOnEdt();
        });
    }

    private static void observeScenarioStateOnEdt() {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("scenario state must be observed on the EDT");
        }
        acceptPendingStraddle();
        observeLobbyTopology();
        GameFrame frame = GameFrame.getInstance();
        if (frame == null) {
            return;
        }
        boolean paused = frame.isTimba_pausada();
        if (LAST_PAUSE_STATE == null) {
            LAST_PAUSE_STATE = paused;
        } else if (LAST_PAUSE_STATE != paused) {
            LAST_PAUSE_STATE = paused;
            marker("PAUSE_STATE", "paused=" + paused);
        }
    }

    private static void observeLobbyTopology() {
        WaitingRoomFrame room = WaitingRoomFrame.getInstance();
        if (room == null || room.isServer() || LOBBY_READY_REPORTED.get()
                || LOBBY_CHECK_REQUESTED.getCount() != 0) {
            return;
        }
        ParticipantCounts counts = participantCounts(room);
        int expectedHumans = CONFIGURED_CLIENTS + 1;
        int expectedSeats = expectedHumans + CONFIGURED_BOTS;
        if (lobbyTopologyComplete(counts.humans(), expectedHumans,
                counts.total(), expectedSeats, GameFrame.isRECOVER())
                && LOBBY_READY_REPORTED.compareAndSet(false, true)) {
            markerLobbyReady(counts);
        }
    }

    private static void reportLobbyReady(WaitingRoomFrame room) {
        ParticipantCounts counts = participantCounts(room);
        if (LOBBY_READY_REPORTED.compareAndSet(false, true)) {
            markerLobbyReady(counts);
        }
    }

    private static void markerLobbyReady(ParticipantCounts counts) {
        marker("LOBBY_READY", "clients=" + CONFIGURED_CLIENTS
                + " bots=" + CONFIGURED_BOTS + " humans=" + counts.humans()
                + " seats=" + counts.total());
    }

    private static ParticipantCounts participantCounts(WaitingRoomFrame room) {
        synchronized (room.getParticipantes()) {
            int total = room.getParticipantes().size();
            int humans = (int) room.getParticipantes().values().stream()
                    // The local seat is represented by a null Participant in
                    // this production map. It is still a human seat; only an
                    // explicit CPU Participant is a bot.
                    .filter(participant -> participant == null || !participant.isCpu())
                    .count();
            return new ParticipantCounts(humans, total);
        }
    }

    static boolean lobbyTopologyComplete(int actualHumans, int expectedHumans,
            int actualSeats, int expectedSeats, boolean recoveryLobby) {
        if (expectedHumans < 2 || actualHumans != expectedHumans) {
            return false;
        }
        return recoveryLobby
                ? actualSeats >= expectedHumans && actualSeats <= expectedSeats
                : actualSeats == expectedSeats;
    }

    private static void applyWindowPolicy(Window window) {
        // Monitor selection applies to every mode. In hidden mode this is what
        // keeps any unavoidable native peer creation flash off the primary
        // display before the window is made invisible again.
        placeOnRequestedScreen(window);
        switch (WINDOW_MODE) {
            case HIDDEN -> window.setVisible(false);
            case MINIMIZED -> {
                if (window instanceof Frame frame) {
                    frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED);
                } else {
                    window.setVisible(false);
                }
            }
            case VISIBLE -> {
                // Positioning above is the whole policy for visible diagnostics.
            }
        }
    }

    private static void applyScenarioWindowAction(Window window) {
        if (isRitScenario() && window instanceof RunItTwiceDialog dialog
                && RIT_DIALOGS_VOTED.add(dialog)) {
            GameFrame frame = GameFrame.getInstance();
            if (shouldGateRitVote(SCENARIO,
                    frame == null ? null : frame.getNick_local(),
                    frame != null && frame.isPartida_local())) {
                if (!GATED_RIT_DIALOG.compareAndSet(null, dialog)) {
                    throw new IllegalStateException("more than one RIT dialog reached the gate");
                }
                marker("RIT_VOTE_GATE_REACHED", "nick=client2");
                return;
            }
            voteRunItTwice(dialog);
            return;
        }
        if (isForceRecoverScenario() && window instanceof NewGameDialog dialog
                && RECOVERY_DIALOGS_SUBMITTED.add(dialog)) {
            submitRecoveryDialog(dialog);
        }
    }

    private static boolean isForceRecoverScenario() {
        return SCENARIO.equals("force-recover") || SCENARIO.equals("double-force-recover")
                || SCENARIO.equals("crash-rejoin-recover")
                || SCENARIO.equals("force-recover-add-client")
                || SCENARIO.equals("force-recover-add-two")
                || SCENARIO.equals("force-recover-swap-client")
                || SCENARIO.equals("lifecycle-chaos")
                || SCENARIO.equals("transport-chaos")
                || SCENARIO.equals("reconnect-force-recover")
                || SCENARIO.equals("abrupt-exit")
                || SCENARIO.equals("dual-abrupt-exit")
                || SCENARIO.equals("mixed-exit-crash")
                || SCENARIO.equals("allin-abrupt-exit");
    }

    private static boolean isAllInScenario() {
        return SCENARIO.equals("allin-rit") || SCENARIO.equals("rit-network-cut")
                || SCENARIO.equals("allin-controlled-exit")
                || SCENARIO.equals("allin-single-board")
                || SCENARIO.equals("allin-rebuy")
                || SCENARIO.equals("allin-reconnect")
                || SCENARIO.equals("allin-abrupt-exit");
    }

    private static boolean isRitScenario() {
        return SCENARIO.equals("allin-rit") || SCENARIO.equals("rit-network-cut");
    }

    static boolean shouldGateRitVote(String scenario, String nick, boolean host) {
        return "rit-network-cut".equals(scenario) && !host && "client2".equals(nick);
    }

    private static void releaseRitVoteGate() throws Exception {
        RunItTwiceDialog dialog = GATED_RIT_DIALOG.getAndSet(null);
        if (dialog == null) {
            throw new IllegalStateException("RIT vote gate was not reached");
        }
        EventQueue.invokeAndWait(() -> voteRunItTwice(dialog));
        marker("RIT_VOTE_GATE_RELEASED", "nick=client2");
    }

    private static boolean requiresLiveStreetAfterPeerLoss() {
        return SCENARIO.equals("abrupt-exit")
                || SCENARIO.equals("dual-abrupt-exit")
                || SCENARIO.equals("mixed-exit-crash")
                || SCENARIO.equals("reconnect-every-street");
    }

    private static boolean isActionGateScenario() {
        return RealGameScenarioContract.isActionGated(SCENARIO);
    }

    private static void voteRunItTwice(RunItTwiceDialog dialog) {
        try {
            java.lang.reflect.Field buttonField
                    = RunItTwiceDialog.class.getDeclaredField("rit_button");
            buttonField.setAccessible(true);
            javax.swing.JButton button = (javax.swing.JButton) buttonField.get(dialog);
            button.doClick();
            marker("RIT_VOTE", "decision=run-it-twice");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot drive production RIT vote button", ex);
        }
    }

    private static void submitRecoveryDialog(NewGameDialog dialog) {
        try {
            Method submit = NewGameDialog.class.getDeclaredMethod(
                    "vamosActionPerformed", java.awt.event.ActionEvent.class);
            submit.setAccessible(true);
            submit.invoke(dialog, new Object[]{null});
            marker("RECOVERY_DIALOG_SUBMITTED", "role=production-dialog");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot submit production recovery dialog", ex);
        }
    }

    private static void placeOnRequestedScreen(Window window) {
        if (!POSITIONED_WINDOWS.add(window)) {
            return;
        }
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        int index = Math.max(0, Math.min(SCREEN_NUMBER - 1, screens.length - 1));
        Rectangle bounds = screens[index].getDefaultConfiguration().getBounds();
        int offset = Math.floorMod(POSITIONED_WINDOWS.size() * 32, 224);
        window.setLocation(bounds.x + 24 + offset, bounds.y + 24 + offset);
    }

    private static LocalPlayer localPlayerIfMounted() {
        GameFrame frame = GameFrame.getInstance();
        if (frame == null) {
            return null;
        }
        try {
            return frame.getLocalPlayer();
        } catch (NullPointerException notMountedYet) {
            // GameFrame publishes its singleton as the first constructor step;
            // tapete is assigned shortly afterwards on the EDT.
            return null;
        }
    }

    private static int completedHands() {
        synchronized (GameFrame.SQL_LOCK) {
            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(
                    "SELECT count(*) FROM hand WHERE end>0");
                    ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (Exception ex) {
                throw new IllegalStateException("cannot read completed-hand progress", ex);
            }
        }
    }

    private static boolean awaitCompletedHands(int requested, Duration timeout,
            boolean permanentExitExpected) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        long lastProgressAt = System.nanoTime();
        int lastCompleted = -1;
        GameFrame lastFrame = GameFrame.getInstance();
        while (System.nanoTime() < deadline) {
            int completed = completedHands();
            if (completed != lastCompleted) {
                lastCompleted = completed;
                lastProgressAt = System.nanoTime();
                marker("PROGRESS", "completed=" + completed + " requested=" + requested);
            }
            if (completed >= requested) {
                return true;
            }
            GameFrame frame = GameFrame.getInstance();
            if (permanentExitExpected && lastFrame != null && frame == null) {
                return false;
            }
            if (isForceRecoverScenario() && frame != null && frame != lastFrame) {
                lastFrame = frame;
                lastProgressAt = System.nanoTime();
                marker("TABLE_INSTANCE_CHANGED", "completed=" + completed
                        + " requested=" + requested);
            }
            Crupier crupier = frame == null ? null : frame.getCrupier();
            if (permanentExitExpected && crupier != null
                    && crupier.isFin_de_la_transmision()) {
                return false;
            }
            String failure = progressFailure(completed, requested,
                    crupier != null && crupier.isFin_de_la_transmision(),
                    crupier == null ? -1 : crupier.getMano(),
                    isForceRecoverScenario(),
                    GameFrame.TEST_MODE,
                    System.nanoTime() - lastProgressAt,
                    ACCELERATED_STALL_TIMEOUT.toNanos());
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            Thread.sleep(25L);
        }
        throw new IllegalStateException("stalled game: completed=" + lastCompleted
                + " requested=" + requested);
    }

    static boolean expectsPermanentLocalExit(NodeConfig config) {
        return !config.host && "client1".equals(config.nick)
                && (SCENARIO.equals("controlled-exit")
                || SCENARIO.equals("allin-controlled-exit"));
    }

    static String progressFailure(int completed, int requested, boolean tableFinished,
            int crupierHand, boolean tableReplacementExpected, boolean accelerated,
            long idleNanos, long stallNanos) {
        if (tableFinished && !tableReplacementExpected) {
            return "premature table end: completed=" + completed
                    + " requested=" + requested + " crupierHand=" + crupierHand;
        }
        if (accelerated && idleNanos >= stallNanos) {
            return "accelerated game made no completed-hand progress for "
                    + Duration.ofNanos(idleNanos).toSeconds() + "s: completed="
                    + completed + " requested=" + requested
                    + " crupierHand=" + crupierHand;
        }
        return null;
    }

    private static String latestLedgerSummary() {
        synchronized (GameFrame.SQL_LOCK) {
            String sql = "SELECT h.id,h.end,h.pot,COUNT(b.id),COALESCE(SUM(b.stack),0) "
                    + "FROM hand h LEFT JOIN balance b ON b.id_hand=h.id "
                    + "GROUP BY h.id ORDER BY h.id DESC LIMIT 1";
            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);
                    ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return "missing=true";
                }
                return "handId=" + rs.getLong(1)
                        + " end=" + rs.getLong(2)
                        + " potCents=" + Math.round(rs.getDouble(3) * 100.0d)
                        + " balanceRows=" + rs.getInt(4)
                        + " stackCents=" + Math.round(rs.getDouble(5) * 100.0d);
            } catch (Exception ex) {
                marker("FAIL", "ledger=" + ex.getClass().getName());
                return "error=true";
            }
        }
    }

    private static void await(String description, Duration timeout, CheckedBoolean condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new IllegalStateException("timeout waiting for " + description);
    }

    private static void awaitParentBarrier(CountDownLatch barrier, String description)
            throws Exception {
        while (!barrier.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
            if (PARENT_CLOSED.getCount() == 0) {
                throw new IllegalStateException(
                        "parent closed before releasing " + description);
            }
        }
    }

    private static synchronized void marker(String type, String detail) {
        System.out.println("CP_E2E_" + type + " " + detail);
        System.out.flush();
    }

    @FunctionalInterface
    private interface CheckedBoolean {

        boolean getAsBoolean() throws Exception;
    }

    private enum PostAction {
        CONTROLLED_EXIT,
        DROP_SOCKET,
        CRASH_PROCESS
    }

    private record ActionGateSnapshot(int hand, int street, String nick,
            boolean inputReady) {
    }

    private record ParticipantCounts(int humans, int total) {
    }

    private record NodeConfig(boolean host, String nick, int port, int clients,
            int bots, int hands, long seed, boolean lateRecoveryJoiner) {

        private static NodeConfig parse(String[] args) {
            if (args.length != 8) {
                throw new IllegalArgumentException(
                        "usage: <host|client> <nick> <port> <clients> <bots> <hands> <seed> <lateRecoveryJoiner>");
            }
            boolean host = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "host" -> true;
                case "client" -> false;
                default -> throw new IllegalArgumentException("role must be host or client");
            };
            int port = Integer.parseInt(args[2]);
            int clients = Integer.parseInt(args[3]);
            int bots = Integer.parseInt(args[4]);
            int hands = Integer.parseInt(args[5]);
            long seed = Long.parseLong(args[6]);
            boolean lateRecoveryJoiner = Boolean.parseBoolean(args[7]);
            if (port < 0 || port > 65535 || (!host && port == 0)
                    || clients < 1 || bots < 0 || hands < 1
                    || clients + bots + 1 > WaitingRoomFrame.MAX_PARTICIPANTES
                    || (host && lateRecoveryJoiner)) {
                throw new IllegalArgumentException("invalid E2E topology");
            }
            return new NodeConfig(host, args[1], port, clients, bots, hands, seed,
                    lateRecoveryJoiner);
        }
    }

    private enum WindowMode {
        HIDDEN,
        MINIMIZED,
        VISIBLE;

        private static WindowMode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(
                        "coronapoker.qa.windowMode must be hidden, minimized or visible", ex);
            }
        }
    }
}
