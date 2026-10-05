package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.SecureRandomService;
import com.tonikelope.coronapoker.core.StatsRepository;
import com.tonikelope.coronapoker.core.UpdateService;
import com.tonikelope.coronapoker.core.UpdaterService;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.IdentityTrustStore;
import com.tonikelope.coronapoker.table.TableCommandSink;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Single-window host for every native GDX screen and overlay. */
final class GdxApplicationShell extends ApplicationAdapter {

    private static final AtomicReference<GdxApplicationShell> ACTIVE =
            new AtomicReference<>();

    private final int refreshRate;
    private final CoronaPokerApplication application;
    private final NewGameSessionGateway sessionGateway;
    private final GdxGameLogSink gameLog;
    private final GdxGamePresentationSettings presentationSettings;
    private final GdxGameText gameText;
    private final Consumer<String> languageChanged;
    private final IdentityTrustStore identityTrust;
    private GdxScreenWakeLock screenWakeLock;
    private final GdxAudioDevices.OutputHotplugMonitor outputHotplugMonitor =
            new GdxAudioDevices.OutputHotplugMonitor();
    private Boolean independentWakeLockRequired;
    private PreferencesService preferences;
    private GdxFrontendScreen menu;
    private LobbySession lobby;
    private CoronaPokerGdxTable startupIntro;
    private volatile CoronaPokerGdxTable table;
    private PendingTableOpen pendingTableOpen;
    private CoronaPokerGdxTable suspendedFinalTable;
    private boolean splashCloseScheduled;

    GdxApplicationShell(int refreshRate, CoronaPokerApplication application,
            NewGameSessionGateway sessionGateway, GdxGameLogSink gameLog,
            GdxGamePresentationSettings presentationSettings,
            GdxGameText gameText, Consumer<String> languageChanged,
            IdentityTrustStore identityTrust) {
        this.refreshRate = refreshRate;
        this.application = Objects.requireNonNull(application, "application");
        this.sessionGateway = Objects.requireNonNull(sessionGateway, "sessionGateway");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
        this.presentationSettings = Objects.requireNonNull(
                presentationSettings, "presentationSettings");
        this.gameText = Objects.requireNonNull(gameText, "gameText");
        this.languageChanged = Objects.requireNonNull(languageChanged,
                "languageChanged");
        this.identityTrust = Objects.requireNonNull(identityTrust,
                "identityTrust");
    }

    static GdxApplicationShell active() {
        return ACTIVE.get();
    }

    static GdxApplicationShell requireActive() {
        GdxApplicationShell shell = active();
        if (shell == null) {
            throw new IllegalStateException("The native GDX application shell is not active");
        }
        return shell;
    }

    /**
     * Native window-close requests must respect the same table-exit contract
     * as the quick bar and keyboard shortcut. Returning {@code false} keeps
     * GLFW alive while the table displays its confirmation dialog.
     */
    boolean requestWindowClose() {
        CoronaPokerGdxTable current = table;
        if (current == null) return true;
        Gdx.app.postRunnable(current::requestWindowClose);
        return false;
    }

    @Override
    public void create() {
        if (!ACTIVE.compareAndSet(null, this)) {
            throw new IllegalStateException("Only one GDX application shell may be active");
        }
        preferences = application.service(PreferencesService.class);
        DatabaseService database = application.service(DatabaseService.class);
        menu = new GdxFrontendScreen(
                preferences, sessionGateway, new RecoverableGameRepository(
                        database),
                identityTrust, application.service(SecureRandomService.class).generator(),
                opened -> {
                    application.sessionOpened();
                    lobby = opened.lobby();
                    menu.openLobby(lobby);
                    awaitTable(lobby);
                }, () -> {
                    lobby = null;
                    application.returnedToMenu();
        }, presentationSettings, gameText, languageChanged,
                application.service(UpdateService.class),
                application.service(UpdaterService.class),
                new StatsRepository(database));
        menu.holdStartupAudio();
        menu.create();
        startupIntro = new CoronaPokerGdxTable(refreshRate,
                () -> Gdx.app.postRunnable(this::finishStartupIntro),
                menu::releaseStartupAudio, preferences, presentationSettings);
        startupIntro.create();
        startupIntro.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(null);
        // Re-apply the swap interval after the native window and its target
        // monitor exist. On mixed-refresh Windows desktops the configuration
        // flag alone can otherwise remain tied to the primary display.
        Gdx.graphics.setVSync(true);
        verifyGrantedBackBufferQuality();
        synchronizeScreenWakeLock();
    }

    private void verifyGrantedBackBufferQuality() {
        java.nio.IntBuffer samples = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_SAMPLES, samples);
        int grantedSamples = samples.get(0);
        presentationSettings.setActualMsaaSamples(grantedSamples);
        System.out.println("GDX back buffer: requested MSAA "
                + presentationSettings.requestedMsaaSamples()
                + "x, driver granted " + grantedSamples + " sample(s)");
    }

    private void awaitTable(LobbySession lobby) {
        lobby.tableSession().whenComplete((session, failure) ->
                Gdx.app.postRunnable(() -> {
                    // A Leave/failed connection may retire this lobby while
                    // its table future is completing on a network thread.  A
                    // stale completion must never open a table over the menu
                    // (or over a newer lobby).  Close the orphaned table
                    // session so its controller/network workers are released.
                    if (this.lobby != lobby) {
                        if (session != null) session.close();
                        return;
                    }
                    if (failure != null) {
                        if (menu != null && lobby.snapshot().startingOrStarted()) {
                            menu.showSessionError(rootMessage(failure));
                        }
                        return;
                    }
                    attachTable(lobby, session);
                }));
    }

    private void attachTable(LobbySession owner, TableSession session) {
        if (lobby != owner) {
            session.close();
            return;
        }
        if (table != null) {
            session.close();
            menu.showSessionError(gameText.translate(
                    "gdx.table.already_open"));
            return;
        }
        GdxTableRenderer renderer = new GdxTableRenderer(session.commands());
        session.attach(renderer).whenComplete((ignored, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (failure == null) {
                        application.tableEntered();
                    } else {
                        renderer.close();
                        menu.showSessionError(rootMessage(failure));
                    }
                }));
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public void render() {
        synchronizeScreenWakeLock();
        if (preferences != null) {
            outputHotplugMonitor.update(preferences.properties(),
                    Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
        }
        CoronaPokerGdxTable intro = startupIntro;
        if (intro != null) {
            intro.render();
            scheduleJvmSplashClose();
            return;
        }
        CoronaPokerGdxTable current = table;
        if (current == null) {
            menu.render();
            advancePendingTableOpen();
        } else {
            current.render();
        }
        scheduleJvmSplashClose();
    }

    /**
     * GLFW owns display-sleep inhibition while its window is attached to a
     * monitor in exclusive fullscreen. Borderless fullscreen is still a
     * windowed GLFW surface, so CoronaPoker supplies the native inhibitor for
     * that mode and for an ordinary window.
     */
    private void synchronizeScreenWakeLock() {
        boolean required = GdxDisplayModeController
                .requiresIndependentWakeLock(Gdx.graphics.isFullscreen());
        if (Boolean.valueOf(required).equals(independentWakeLockRequired)) {
            return;
        }
        independentWakeLockRequired = required;
        if (!required) {
            if (screenWakeLock != null) screenWakeLock.close();
            screenWakeLock = null;
            return;
        }
        screenWakeLock = GdxScreenWakeLock.forCurrentPlatform();
        screenWakeLock.acquire();
    }

    private void scheduleJvmSplashClose() {
        if (splashCloseScheduled) return;
        splashCloseScheduled = true;
        // Close on the next render-loop turn so the first GDX frame has already
        // reached the swap chain. This is the same official GIF declared by Swing.
        Gdx.app.postRunnable(() -> {
            try {
                java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();
                if (splash != null) splash.close();
            } catch (RuntimeException ignored) {
                // Launchers without JVM splash support simply have nothing to close.
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        // A window can cross to another refresh-rate monitor. Rebinding VSync
        // here keeps GLFW's swap interval attached to the active context.
        Gdx.graphics.setVSync(true);
        CoronaPokerGdxTable intro = startupIntro;
        if (intro != null) {
            intro.resize(width, height);
            return;
        }
        CoronaPokerGdxTable current = table;
        if (current != null) {
            current.resize(width, height);
        } else if (menu != null) {
            menu.resize(width, height);
        }
    }

    void openTable(TableSnapshot initialState, TableCommandSink commands,
            Consumer<CoronaPokerGdxTable> opened,
            CompletableFuture<Void> openingBarrier) {
        Objects.requireNonNull(initialState, "initialState");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(openingBarrier, "openingBarrier");
        Gdx.app.postRunnable(() -> {
            if (table != null || pendingTableOpen != null) {
                openingBarrier.completeExceptionally(
                        new IllegalStateException("A GDX table scene is already open"));
                return;
            }
            CoronaPokerGdxTable candidate = null;
            try {
                gameLog.reset();
                candidate = new CoronaPokerGdxTable(
                        refreshRate, new GdxTableViewState(initialState), commands,
                        () -> opened.accept(table), gameLog, preferences, lobby,
                        presentationSettings, identityTrust);
                candidate.shareBackgroundMusic(menu.tableBackgroundMusic());
                candidate.beginCreate();
                pendingTableOpen = new PendingTableOpen(candidate,
                        openingBarrier);
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                if (candidate != null) {
                    try {
                        candidate.disposeFailedCreation();
                    } catch (Throwable cleanupError) {
                        error.addSuppressed(cleanupError);
                    }
                }
                table = null;
                Gdx.input.setInputProcessor(menu);
                menu.showSessionError(gameText.translate(
                        "gdx.table.open_failed_detail", rootMessage(error)));
                openingBarrier.completeExceptionally(error);
            }
        });
    }

    private void advancePendingTableOpen() {
        PendingTableOpen pending = pendingTableOpen;
        if (pending == null) return;
        try {
            if (!pending.table.createNextPhase()) return;
            pendingTableOpen = null;
            menu.suspendForTable(true);
            pending.table.activateBackgroundMusic();
            pending.table.resize(Gdx.graphics.getWidth(),
                    Gdx.graphics.getHeight());
            table = pending.table;
            // The lobby keeps its last hit map while the table is visible.
            // Leaving it installed would make one table click reach the raw
            // table input on press and a hidden lobby control on release.
            Gdx.input.setInputProcessor(pending.table.inputProcessor());
        } catch (Throwable error) {
            pendingTableOpen = null;
            error.printStackTrace(System.err);
            try {
                pending.table.disposeFailedCreation();
            } catch (Throwable cleanupError) {
                error.addSuppressed(cleanupError);
            }
            table = null;
            Gdx.input.setInputProcessor(menu);
            menu.resumeMusic();
            menu.showSessionError(gameText.translate(
                    "gdx.table.open_failed_detail", rootMessage(error)));
            pending.openingBarrier.completeExceptionally(error);
        }
    }

    void closeTable(CoronaPokerGdxTable expected) {
        Gdx.app.postRunnable(() -> {
            if (table == expected && table != null) {
                long closeStarted = System.nanoTime();
                boolean continueRequested = table.finalContinueRequested();
                boolean statsRequested = table.finalStatsRequested();
                boolean applicationExitRequested =
                        table.finalApplicationExitRequested();
                table = null;
                menu.refreshFeltFromSettings();
                Gdx.input.setInputProcessor(menu);
                long menuReady = System.nanoTime();
                LobbySession completedLobby = lobby;
                if (statsRequested && completedLobby != null) {
                    suspendFinalSummaryForStats(expected);
                    return;
                }
                if (completedLobby != null) {
                    if (continueRequested) {
                        menu.continueLastGameFromTable(completedLobby);
                    } else {
                        menu.returnFromTable(completedLobby);
                    }
                }
                // Menu and table deliberately share one persistent decoder.
                // Resuming the menu only changes ownership of the same stream:
                // there is no pause, seek or decoder replacement to expose an
                // audible discontinuity.
                menu.resumeMusic();
                long sessionClosed = System.nanoTime();
                System.out.printf(java.util.Locale.ROOT,
                        "GDX table close timings: menu=%.1f ms, session=%.1f ms%n",
                        (menuReady - closeStarted) / 1_000_000d,
                        (sessionClosed - menuReady) / 1_000_000d);
                if (applicationExitRequested) {
                    expected.dispose();
                    Gdx.app.exit();
                } else {
                    disposeTableAfterAudioHandoff(expected);
                }
            }
        });
    }

    private void suspendFinalSummaryForStats(CoronaPokerGdxTable expected) {
        suspendedFinalTable = expected;
        expected.retainFinalSummary(
                () -> finishRetainedFinalSummary(expected, false, false),
                () -> showStatsFromRetainedFinalSummary(expected),
                () -> finishRetainedFinalSummary(expected, true, false),
                () -> finishRetainedFinalSummary(expected, false, true));
        expected.suspendRetainedFinalSummary();
        Gdx.input.setInputProcessor(menu);
        menu.openStatsFromTable(() -> restoreRetainedFinalSummary(expected));
        menu.resumeMusic();
    }

    private void showStatsFromRetainedFinalSummary(
            CoronaPokerGdxTable expected) {
        if (table != expected) return;
        table = null;
        suspendedFinalTable = expected;
        expected.suspendRetainedFinalSummary();
        Gdx.input.setInputProcessor(menu);
        menu.openStatsFromTable(() -> restoreRetainedFinalSummary(expected));
        menu.resumeMusic();
    }

    private void restoreRetainedFinalSummary(CoronaPokerGdxTable expected) {
        if (suspendedFinalTable != expected) return;
        suspendedFinalTable = null;
        menu.suspendForTable(true);
        expected.resumeRetainedFinalSummary();
        table = expected;
        expected.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(expected.inputProcessor());
    }

    private void finishRetainedFinalSummary(CoronaPokerGdxTable expected,
            boolean continueRequested, boolean applicationExitRequested) {
        if (table != expected && suspendedFinalTable != expected) return;
        table = null;
        suspendedFinalTable = null;
        Gdx.input.setInputProcessor(menu);
        LobbySession completedLobby = lobby;
        if (completedLobby != null) {
            if (continueRequested) {
                menu.continueLastGameFromTable(completedLobby);
            } else {
                menu.returnFromTable(completedLobby);
            }
        }
        menu.resumeMusic();
        if (applicationExitRequested) {
            expected.dispose();
            Gdx.app.exit();
        } else {
            disposeTableAfterAudioHandoff(expected);
        }
    }

    private void disposeTableAfterAudioHandoff(
            CoronaPokerGdxTable retiredTable) {
        // The persistent background decoder belongs to the frontend and is not
        // disposed with the table. Defer the remaining OpenAL/resource cleanup
        // for two frames so it cannot starve the first clean menu frame.
        Gdx.app.postRunnable(() -> {
            Gdx.app.postRunnable(retiredTable::dispose);
        });
    }

    void showDialog(GdxTableDialog request) {
        Objects.requireNonNull(request, "request");
        Gdx.app.postRunnable(() -> {
            CoronaPokerGdxTable current = table;
            if (current == null) {
                request.dismiss();
            } else {
                current.showDialog(request);
            }
        });
    }

    CompletionStage<Void> playGameOverAudio(
            GdxGameDecisionSink.GameOverAudioCue cue) {
        Objects.requireNonNull(cue, "cue");
        CompletableFuture<Void> result = new CompletableFuture<>();
        Gdx.app.postRunnable(() -> {
            CoronaPokerGdxTable current = table;
            if (current == null) {
                result.complete(null);
                return;
            }
            try {
                current.playGameOverAudio(cue).whenComplete(
                        (ignored, failure) -> {
                            if (failure == null) result.complete(null);
                            else result.completeExceptionally(failure);
                        });
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    boolean gameOverCinematicsEnabled() {
        CoronaPokerGdxTable current = table;
        return current != null && current.gameOverCinematicsEnabled();
    }

    /** Replays one dealer-approved recovered action through the live table. */
    void submitTableCommand(com.tonikelope.coronapoker.table.TableCommand command) {
        Objects.requireNonNull(command, "command");
        CoronaPokerGdxTable current = table;
        if (current == null) {
            throw new IllegalStateException(
                    "No active GDX table can receive the recovered action");
        }
        current.submitExternal(command);
    }

    private void finishStartupIntro() {
        CoronaPokerGdxTable intro = startupIntro;
        boolean skipped = intro != null && intro.startupSequenceSkipped();
        startupIntro = null;
        if (intro != null) intro.dispose();
        if (menu != null) {
            if (skipped) menu.completeStartupReveal();
            else menu.beginStartupReveal();
            menu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            Gdx.input.setInputProcessor(menu);
        }
    }

    @Override
    public void dispose() {
        // Release the native per-thread execution state before any resource
        // teardown can fail. Windows also clears it automatically if this
        // process terminates abnormally.
        if (screenWakeLock != null) screenWakeLock.close();
        screenWakeLock = null;
        CoronaPokerGdxTable intro = startupIntro;
        startupIntro = null;
        if (intro != null) intro.dispose();
        CoronaPokerGdxTable current = table;
        table = null;
        if (current != null) {
            current.dispose();
        }
        PendingTableOpen pending = pendingTableOpen;
        pendingTableOpen = null;
        if (pending != null) {
            pending.table.disposeFailedCreation();
            pending.openingBarrier.completeExceptionally(
                    new java.util.concurrent.CancellationException(
                            "GDX table creation was cancelled"));
        }
        CoronaPokerGdxTable suspended = suspendedFinalTable;
        suspendedFinalTable = null;
        if (suspended != null && suspended != current) suspended.dispose();
        if (menu != null) {
            menu.dispose();
            menu = null;
        }
        ACTIVE.compareAndSet(this, null);
    }

    private record PendingTableOpen(CoronaPokerGdxTable table,
            CompletableFuture<Void> openingBarrier) {
    }
}
