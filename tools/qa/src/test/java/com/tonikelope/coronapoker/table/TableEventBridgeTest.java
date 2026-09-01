package com.tonikelope.coronapoker.table;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TableEventBridgeTest {

    private static final TableSnapshot EMPTY = new TableSnapshot(
            1L, "local", TableSnapshot.Street.WAITING, 0d, "", false,
            List.of(), List.of());

    @Test
    void detachedSwingBridgeIsAnImmediateNoOp() {
        TableEventBridge bridge = new TableEventBridge();

        CompletionStage<Void> completion = bridge.publish(TableVisualEvent.CloseTable::new);

        assertTrue(completion.toCompletableFuture().isDone());
        assertFalse(bridge.isAttached());
    }

    @Test
    void attachedRendererReceivesMonotonicEventsAndItsBarrier() {
        RecordingRenderer renderer = new RecordingRenderer();
        TableEventBridge bridge = new TableEventBridge();
        bridge.attach(renderer, EMPTY).toCompletableFuture().join();

        CompletionStage<Void> barrier = bridge.publish(TableVisualEvent.CloseTable::new);

        assertSame(renderer.barrier, barrier);
        assertEquals(List.of(1L), renderer.sequences);
        assertTrue(bridge.isAttached());
    }

    @Test
    void aTableCanNeverOwnTwoRenderers() {
        TableEventBridge bridge = new TableEventBridge();
        bridge.attach(new RecordingRenderer(), EMPTY).toCompletableFuture().join();

        assertThrows(IllegalStateException.class,
                () -> bridge.attach(new RecordingRenderer(), EMPTY));
    }

    private static final class RecordingRenderer implements TableRenderer {

        private final java.util.ArrayList<Long> sequences = new java.util.ArrayList<>();
        private final CompletableFuture<Void> barrier = new CompletableFuture<>();

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            sequences.add(event.sequence());
            return barrier;
        }

        @Override
        public void close() {
        }
    }
}
