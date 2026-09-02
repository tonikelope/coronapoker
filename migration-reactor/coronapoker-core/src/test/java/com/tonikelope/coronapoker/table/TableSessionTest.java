package com.tonikelope.coronapoker.table;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableSessionTest {

    @Test
    void engineStartsOnlyAfterRendererOpeningBarrier() {
        CompletableFuture<Void> rendererReady = new CompletableFuture<>();
        AtomicInteger starts = new AtomicInteger();
        List<String> order = new ArrayList<>();
        TableEventBridge bridge = new TableEventBridge();
        TableSession session = new TableSession(emptyTable(), command -> { }, bridge,
                () -> {
                    order.add("engine");
                    starts.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                });

        CompletionStage<Void> attached = session.attach(new TableRenderer() {
            @Override public CompletionStage<Void> open(TableSnapshot initialState) {
                order.add("renderer");
                return rendererReady;
            }
            @Override public CompletionStage<Void> render(TableVisualEvent event) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void close() { order.add("closed"); }
        });

        assertEquals(List.of("renderer"), order);
        assertEquals(0, starts.get());
        rendererReady.complete(null);
        attached.toCompletableFuture().join();
        assertEquals(List.of("renderer", "engine"), order);
        assertEquals(1, starts.get());
        assertThrows(java.util.concurrent.CompletionException.class, () ->
                session.attach(new NoOpRenderer()).toCompletableFuture().join());
    }

    private static TableSnapshot emptyTable() {
        return new TableSnapshot(0, "ana", TableSnapshot.Street.WAITING,
                0, "", false, List.of(), List.of());
    }

    private static final class NoOpRenderer implements TableRenderer {
        @Override public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> render(TableVisualEvent event) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public void close() { }
    }
}
