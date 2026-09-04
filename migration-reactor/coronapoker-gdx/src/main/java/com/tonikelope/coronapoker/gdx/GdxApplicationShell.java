package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.table.TableCommandSink;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
    private GdxFrontendScreen menu;
    private LobbySession lobby;
    private CoronaPokerGdxTable table;

    GdxApplicationShell(int refreshRate, CoronaPokerApplication application,
            NewGameSessionGateway sessionGateway, GdxGameLogSink gameLog) {
        this.refreshRate = refreshRate;
        this.application = Objects.requireNonNull(application, "application");
        this.sessionGateway = Objects.requireNonNull(sessionGateway, "sessionGateway");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
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

    @Override
    public void create() {
        if (!ACTIVE.compareAndSet(null, this)) {
            throw new IllegalStateException("Only one GDX application shell may be active");
        }
        menu = new GdxFrontendScreen(
                application.service(PreferencesService.class), sessionGateway,
                opened -> {
                    application.sessionOpened();
                    lobby = opened.lobby();
                    menu.openLobby(lobby);
                    awaitTable(lobby);
                }, () -> {
                    lobby = null;
                    application.returnedToMenu();
                });
        menu.create();
        // Re-apply the swap interval after the native window and its target
        // monitor exist. On mixed-refresh Windows desktops the configuration
        // flag alone can otherwise remain tied to the primary display.
        Gdx.graphics.setVSync(true);
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
            menu.showSessionError("Ya hay una mesa GDX abierta");
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
        CoronaPokerGdxTable current = table;
        if (current == null) {
            menu.render();
        } else {
            current.render();
        }
    }

    @Override
    public void resize(int width, int height) {
        // A window can cross to another refresh-rate monitor. Rebinding VSync
        // here keeps GLFW's swap interval attached to the active context.
        Gdx.graphics.setVSync(true);
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
            if (table != null) {
                openingBarrier.completeExceptionally(
                        new IllegalStateException("A GDX table scene is already open"));
                return;
            }
            try {
                gameLog.reset();
                CoronaPokerGdxTable candidate = new CoronaPokerGdxTable(
                        refreshRate, new GdxTableViewState(initialState), commands,
                        () -> opened.accept(table), gameLog);
                table = candidate;
                candidate.create();
                candidate.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            } catch (Throwable error) {
                table = null;
                openingBarrier.completeExceptionally(error);
            }
        });
    }

    void closeTable(CoronaPokerGdxTable expected) {
        Gdx.app.postRunnable(() -> {
            if (table == expected && table != null) {
                table.dispose();
                table = null;
                Gdx.input.setInputProcessor(menu);
                LobbySession completedLobby = lobby;
                if (completedLobby != null) {
                    menu.returnFromTable(completedLobby);
                }
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

    @Override
    public void dispose() {
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
