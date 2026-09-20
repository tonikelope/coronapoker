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
import com.tonikelope.coronapoker.core.LobbySession;
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
    private PreferencesService preferences;
    private GdxFrontendScreen menu;
    private LobbySession lobby;
    private CoronaPokerGdxTable startupIntro;
    private volatile CoronaPokerGdxTable table;
    private PendingTableOpen pendingTableOpen;
    private boolean splashCloseScheduled;

    private record PendingTableOpen(CoronaPokerGdxTable candidate,
            CompletableFuture<Void> openingBarrier) {
    }

    GdxApplicationShell(int refreshRate, CoronaPokerApplication application,
            NewGameSessionGateway sessionGateway, GdxGameLogSink gameLog,
            GdxGamePresentationSettings presentationSettings,
            GdxGameText gameText, Consumer<String> languageChanged) {
        this.refreshRate = refreshRate;
        this.application = Objects.requireNonNull(application, "application");
        this.sessionGateway = Objects.requireNonNull(sessionGateway, "sessionGateway");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
        this.presentationSettings = Objects.requireNonNull(
                presentationSettings, "presentationSettings");
        this.gameText = Objects.requireNonNull(gameText, "gameText");
        this.languageChanged = Objects.requireNonNull(languageChanged,
                "languageChanged");
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
        menu = new GdxFrontendScreen(
                preferences, sessionGateway, new RecoverableGameRepository(
                        application.service(DatabaseService.class)),
                opened -> {
                    application.sessionOpened();
                    lobby = opened.lobby();
                    menu.openLobby(lobby);
                    awaitTable(lobby);
                }, () -> {
                    lobby = null;
                    application.returnedToMenu();
        }, presentationSettings, gameText, languageChanged);
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
                    if (failure != null) {
                        if (menu != null && lobby.snapshot().startingOrStarted()) {
                            menu.showSessionError(rootMessage(failure));
                        }
                        return;
                    }
                    attachTable(session);
                }));
    }

    private void attachTable(TableSession session) {
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
        CoronaPokerGdxTable intro = startupIntro;
        if (intro != null) {
            intro.render();
            scheduleJvmSplashClose();
            return;
        }
        CoronaPokerGdxTable current = table;
        if (current == null) {
            menu.render();
            // Create one bounded group of table resources after each rendered
            // lobby frame. PREPARANDO LA MESA therefore keeps reaching the
            // swap chain instead of freezing during one monolithic create().
            advancePendingTableOpen();
        } else {
            current.render();
        }
        scheduleJvmSplashClose();
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
                menu.suspendForTable();
                candidate = new CoronaPokerGdxTable(
                        refreshRate, new GdxTableViewState(initialState), commands,
                        () -> opened.accept(table), gameLog, preferences, lobby,
                        presentationSettings);
                candidate.beginIncrementalCreate();
                pendingTableOpen = new PendingTableOpen(candidate,
                        openingBarrier);
            } catch (Throwable error) {
                failPendingTableOpen(candidate, openingBarrier, error);
            }
        });
    }

    private void advancePendingTableOpen() {
        PendingTableOpen pending = pendingTableOpen;
        if (pending == null) return;
        try {
            if (!pending.candidate().advanceIncrementalCreate()) return;
            pending.candidate().resize(Gdx.graphics.getWidth(),
                    Gdx.graphics.getHeight());
            table = pending.candidate();
            pendingTableOpen = null;
            // The lobby keeps its last hit map while the table is visible.
            // Leaving it installed would make one table click reach the raw
            // table input on press and a hidden lobby control on release.
            Gdx.input.setInputProcessor(table.inputProcessor());
        } catch (Throwable error) {
            pendingTableOpen = null;
            failPendingTableOpen(pending.candidate(),
                    pending.openingBarrier(), error);
        }
    }

    private void failPendingTableOpen(CoronaPokerGdxTable candidate,
            CompletableFuture<Void> openingBarrier, Throwable error) {
        error.printStackTrace(System.err);
        if (candidate != null) {
            try {
                candidate.dispose();
            } catch (Throwable cleanupError) {
                error.addSuppressed(cleanupError);
            }
        }
        table = null;
        Gdx.input.setInputProcessor(menu);
        // Opening paused whichever menu/lobby track was active. If table
        // creation fails, restore that same surface without restarting or
        // replacing its decoder.
        menu.resumeMusic();
        menu.showSessionError(gameText.translate(
                "gdx.table.open_failed_detail", rootMessage(error)));
        openingBarrier.completeExceptionally(error);
    }

    void closeTable(CoronaPokerGdxTable expected) {
        Gdx.app.postRunnable(() -> {
            if (table == expected && table != null) {
                long closeStarted = System.nanoTime();
                boolean continueRequested = table.finalContinueRequested();
                float musicPosition = table.backgroundMusicPosition();
                table = null;
                menu.refreshFeltFromSettings();
                Gdx.input.setInputProcessor(menu);
                long menuReady = System.nanoTime();
                LobbySession completedLobby = lobby;
                if (completedLobby != null) {
                    if (continueRequested) {
                        menu.continueLastGameFromTable(completedLobby);
                    } else {
                        menu.returnFromTable(completedLobby);
                    }
                }
                // Start the menu decoder before releasing the table decoder.
                // Disposal performs substantial resource cleanup; doing it
                // first created an audible gap in the continuous soundtrack.
                menu.resumeBackgroundMusicAt(musicPosition);
                long sessionClosed = System.nanoTime();
                expected.dispose();
                long disposedAt = System.nanoTime();
                System.out.printf(java.util.Locale.ROOT,
                        "GDX table close timings: menu=%.1f ms, session=%.1f ms, resources=%.1f ms%n",
                        (menuReady - closeStarted) / 1_000_000d,
                        (sessionClosed - menuReady) / 1_000_000d,
                        (disposedAt - sessionClosed) / 1_000_000d);
            }
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
        startupIntro = null;
        if (intro != null) intro.dispose();
        if (menu != null) {
            menu.beginStartupReveal();
            menu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            Gdx.input.setInputProcessor(menu);
        }
    }

    @Override
    public void dispose() {
        CoronaPokerGdxTable intro = startupIntro;
        startupIntro = null;
        if (intro != null) intro.dispose();
        CoronaPokerGdxTable current = table;
        table = null;
        if (current != null) {
            current.dispose();
        }
        if (menu != null) {
            menu.dispose();
            menu = null;
        }
        ACTIVE.compareAndSet(this, null);
    }
}
