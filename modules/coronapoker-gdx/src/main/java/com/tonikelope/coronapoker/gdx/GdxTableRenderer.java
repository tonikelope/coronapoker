package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.tonikelope.coronapoker.table.TableCommandSink;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Event adapter for the table scene hosted by the single GDX window. */
public final class GdxTableRenderer implements TableRenderer {

    private static final int MAX_EVENTS_PER_FRAME = 8;
    private static final long EVENT_FRAME_BUDGET_NANOS = 2_000_000L;

    /**
     * A final visual barrier may release controller/network cleanup that waits
     * for owned executors.  It must never run inline on libGDX's render thread.
     * Table closure is rare, so a short-lived daemon avoids retaining another
     * application executor after the native window has closed.
     */
    private static final Executor TABLE_CLOSE_EXECUTOR = command -> {
        Thread worker = new Thread(command, "coronapoker-gdx-table-close");
        worker.setDaemon(true);
        worker.start();
    };

    private final TableCommandSink commands;
    private final GdxFrameTaskPump eventPump = new GdxFrameTaskPump(
            runnable -> Gdx.app.postRunnable(runnable), System::nanoTime,
            MAX_EVENTS_PER_FRAME, EVENT_FRAME_BUDGET_NANOS);
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
        CompletableFuture<Void> presentationBarrier;
        if (event instanceof TableVisualEvent.CloseTable) {
            presentationBarrier = new CompletableFuture<>();
            transferCompletionAsync(presentationBarrier, barrier,
                    TABLE_CLOSE_EXECUTOR);
        } else {
            presentationBarrier = barrier;
        }
        CompletableFuture<Void> queuedBarrier = presentationBarrier;
        eventPump.submit(() -> {
            try {
                CoronaPokerGdxTable current = table;
                if (current == null) {
                    throw new IllegalStateException(
                            "GDX table renderer closed before event delivery");
                }
                current.acceptEvent(event, queuedBarrier);
            } catch (Throwable error) {
                queuedBarrier.completeExceptionally(error);
            }
        }, queuedBarrier::completeExceptionally);
        return barrier;
    }

    static void transferCompletionAsync(CompletionStage<Void> source,
            CompletableFuture<Void> target, Executor executor) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(executor, "executor");
        source.whenCompleteAsync((ignored, failure) -> {
            if (failure == null) target.complete(null);
            else target.completeExceptionally(failure);
        }, executor);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && opened.getAndSet(false)) {
            eventPump.close();
            GdxApplicationShell shell = GdxApplicationShell.active();
            if (shell != null) {
                shell.closeTable(table);
            }
            table = null;
        }
    }
}
