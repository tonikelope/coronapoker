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
    private static final Set<NewGameDialog> RECOVERY_DIALOGS_SUBMITTED
            = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final CountDownLatch PARENT_CLOSED = new CountDownLatch(1);
    private static volatile int CONFIGURED_BOTS;
    private static volatile int ALL_IN_OBSERVED_HAND = -1;

    private RealGameNodeMain() {
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace(System.err);
            marker("FAIL", "uncaught=" + error.getClass().getName());
        });

        NodeConfig config = NodeConfig.parse(args);
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

        if (config.host) {
            await("server socket", START_TIMEOUT,
                    () -> room.getNet_server().getServer_socket() != null
                    && !room.getNet_server().getServer_socket().isClosed());
            marker("READY", "role=host port=" + config.port
                    + " testMode=" + GameFrame.TEST_MODE
                    + " windowMode=" + WINDOW_MODE.name().toLowerCase(Locale.ROOT));
            await("human clients", START_TIMEOUT,
                    () -> room.getParticipantes().size() >= config.clients + 1);
            addBots(room, config.bots);
            await("bots", START_TIMEOUT,
                    () -> room.getParticipantes().size() >= config.clients + config.bots + 1);
            invokeStartGame(room);
        } else {
            marker("READY", "role=client nick=" + config.nick
                    + " testMode=" + GameFrame.TEST_MODE
                    + " windowMode=" + WINDOW_MODE.name().toLowerCase(Locale.ROOT));
        }

        await("mounted GameFrame", START_TIMEOUT, () -> localPlayerIfMounted() != null);
        startControlThread();

        Thread actionDriver = new Thread(() -> driveLocalActions(config.seed), "qa-real-game-action-driver");
        actionDriver.setDaemon(true);
        actionDriver.start();

        Duration gameTimeout = Duration.ofSeconds(Math.max(180L, config.hands * 45L));
        await("completed hands", gameTimeout, () -> completedHands() >= config.hands);

        Crupier crupier = GameFrame.getInstance().getCrupier();
        marker("HANDS_COMPLETE", "role=" + (config.host ? "host" : "client")
                + " nick=" + config.nick
                + " requested=" + config.hands
                + " crupierHand=" + crupier.getMano()
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
        Helpers.CSPRNG_GENERATOR = SecureRandom.getInstanceStrong();
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
                EventQueue.invokeAndWait(() -> {
                    GameFrame frame = GameFrame.getInstance();
                    LocalPlayer local = localPlayerIfMounted();
                    if (frame == null || local == null) {
                        return;
                    }
                    if (isAllInScenario() && local.getDecision() == LocalPlayer.ALLIN) {
                        int hand = frame.getCrupier().getMano();
                        if (ALL_IN_OBSERVED_HAND != hand) {
                            ALL_IN_OBSERVED_HAND = hand;
                            marker("ALLIN_ACTION_CLICKED", "nick="
                                    + frame.getNick_local() + " hand=" + hand);
                        }
                    }
                    if (!local.isTurno()) {
                        return;
                    }
                    if (isAllInScenario()
                            && local.getPlayer_allin().isEnabled()) {
                        local.getPlayer_allin().doClick();
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
                    } else if (command.equals("FORCE_RECOVER")) {
                        invokeForceRecover();
                        marker("FORCE_RECOVER_REQUESTED", "role=host");
                    } else if (command.equals("START_RECOVERED_GAME")) {
                        invokeRecoveredGameStart();
                        marker("RECOVERED_GAME_START_REQUESTED", "role=host");
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

    private static void invokeControlledClientExit() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(() -> {
            try {
                GameFrame frame = GameFrame.getInstance();
                if (frame == null || frame.isPartida_local()) {
                    throw new IllegalStateException("controlled EXIT requires a mounted client table");
                }
                Method exit = GameFrame.class.getDeclaredMethod("performControlledClientExit");
                exit.setAccessible(true);
                exit.invoke(frame);
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException("cannot invoke production controlled EXIT", failure.get());
        }
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

    private static void applyWindowPolicyToAll() throws Exception {
        if (EventQueue.isDispatchThread()) {
            for (Window window : Window.getWindows()) {
                applyWindowPolicy(window);
            }
            return;
        }
        EventQueue.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                applyWindowPolicy(window);
            }
        });
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
            voteRunItTwice(dialog);
            return;
        }
        if (isForceRecoverScenario() && window instanceof NewGameDialog dialog
                && RECOVERY_DIALOGS_SUBMITTED.add(dialog)) {
            submitRecoveryDialog(dialog);
        }
    }

    private static boolean isForceRecoverScenario() {
        return SCENARIO.equals("force-recover") || SCENARIO.equals("double-force-recover");
    }

    private static boolean isAllInScenario() {
        return SCENARIO.equals("allin-rit") || SCENARIO.equals("allin-controlled-exit");
    }

    private static boolean isRitScenario() {
        return SCENARIO.equals("allin-rit");
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
                return 0;
            }
        }
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

    private static synchronized void marker(String type, String detail) {
        System.out.println("CP_E2E_" + type + " " + detail);
        System.out.flush();
    }

    @FunctionalInterface
    private interface CheckedBoolean {

        boolean getAsBoolean() throws Exception;
    }

    private record NodeConfig(boolean host, String nick, int port, int clients, int bots, int hands, long seed) {

        private static NodeConfig parse(String[] args) {
            if (args.length != 7) {
                throw new IllegalArgumentException(
                        "usage: <host|client> <nick> <port> <clients> <bots> <hands> <seed>");
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
            if (port < 1 || port > 65535 || clients < 1 || bots < 0 || hands < 1
                    || clients + bots + 1 > WaitingRoomFrame.MAX_PARTICIPANTES) {
                throw new IllegalArgumentException("invalid E2E topology");
            }
            return new NodeConfig(host, args[1], port, clients, bots, hands, seed);
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
