package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ScreenUtils;
import com.tonikelope.coronapoker.table.TableCommandSink;
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
    private CoronaPokerGdxTable table;

    GdxApplicationShell(int refreshRate) {
        this.refreshRate = refreshRate;
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
    }

    @Override
    public void render() {
        CoronaPokerGdxTable current = table;
        if (current == null) {
            ScreenUtils.clear(0.01f, 0.025f, 0.055f, 1f);
        } else {
            current.render();
        }
    }

    @Override
    public void resize(int width, int height) {
        CoronaPokerGdxTable current = table;
        if (current != null) {
            current.resize(width, height);
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
                CoronaPokerGdxTable candidate = new CoronaPokerGdxTable(
                        refreshRate, new GdxTableViewState(initialState),
                        () -> opened.accept(table));
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
        ACTIVE.compareAndSet(this, null);
    }
}
