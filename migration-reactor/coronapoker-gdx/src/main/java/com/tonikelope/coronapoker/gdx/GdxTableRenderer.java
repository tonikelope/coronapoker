package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.tonikelope.coronapoker.table.TableCommandSink;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Event adapter for the table scene hosted by the single GDX window. */
public final class GdxTableRenderer implements TableRenderer {

    private final TableCommandSink commands;
    private final AtomicBoolean opened = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile CoronaPokerGdxTable table;

    public GdxTableRenderer(TableCommandSink commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    @Override
    public CompletionStage<Void> open(TableSnapshot initialState) {
        Objects.requireNonNull(initialState, "initialState");
        if (closed.get()) {
            throw new IllegalStateException("GDX table renderer is closed");
        }
        if (!opened.compareAndSet(false, true)) {
            throw new IllegalStateException("GDX table renderer is already open");
        }
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try {
            GdxApplicationShell.requireActive().openTable(initialState, commands,
                    openedTable -> {
                        table = openedTable;
                        ready.complete(null);
                    }, ready);
        } catch (Throwable error) {
            opened.set(false);
            ready.completeExceptionally(error);
        }
        return ready;
    }

    @Override
    public CompletionStage<Void> render(TableVisualEvent event) {
        Objects.requireNonNull(event, "event");
        if (!opened.get() || closed.get() || table == null) {
            throw new IllegalStateException("GDX table renderer is not open");
        }
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        Gdx.app.postRunnable(() -> {
            try {
                table.acceptEvent(event, barrier);
            } catch (Throwable error) {
                barrier.completeExceptionally(error);
            }
        });
        return barrier;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && opened.getAndSet(false)) {
            GdxApplicationShell shell = GdxApplicationShell.active();
            if (shell != null) {
                shell.closeTable(table);
            }
            table = null;
        }
    }
}
