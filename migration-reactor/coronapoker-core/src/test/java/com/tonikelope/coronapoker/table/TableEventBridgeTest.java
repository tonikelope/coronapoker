package com.tonikelope.coronapoker.table;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TableEventBridgeTest {

    @Test
    void forcedBetCollectionCarriesExactAmountsAndExposesTheRendererBarrier() {
        TableEventBridge bridge = new TableEventBridge();
        RecordingRenderer renderer = new RecordingRenderer();
        bridge.attach(renderer, emptyTable()).toCompletableFuture().join();

        CompletionStage<Void> barrier = bridge.publish(sequence ->
                new TableVisualEvent.CollectBets(sequence, List.of(
                        new TableVisualEvent.ChipTransfer("small", 50d),
                        new TableVisualEvent.ChipTransfer("big", 100d)),
                        0d, 150d));

        assertFalse(barrier.toCompletableFuture().isDone());
        assertEquals(List.of(new TableVisualEvent.CollectBets(1L, List.of(
                new TableVisualEvent.ChipTransfer("small", 50d),
                new TableVisualEvent.ChipTransfer("big", 100d)),
                0d, 150d)), renderer.events);

        renderer.finishCurrentAnimation();
        assertTrue(barrier.toCompletableFuture().isDone());
        bridge.close();
        assertTrue(renderer.closed);
    }

    @Test
    void visualSequenceIsAssignedOnlyByThePresentationBoundary() {
        TableEventBridge bridge = new TableEventBridge();
        RecordingRenderer renderer = new RecordingRenderer();
        bridge.attach(renderer, emptyTable()).toCompletableFuture().join();

        bridge.publish(sequence -> new TableVisualEvent.HandBoundary(
                sequence, 7L, TableVisualEvent.HandBoundary.Phase.START));
        renderer.finishCurrentAnimation();
        bridge.publish(sequence -> new TableVisualEvent.Shuffle(
                sequence, "default", TableVisualEvent.Shuffle.Phase.START));

        assertEquals(List.of(1L, 2L), renderer.events.stream()
                .map(TableVisualEvent::sequence).toList());
    }

    @Test
    void invalidPotTransitionIsRejectedBeforeItReachesARenderer() {
        assertThrows(IllegalArgumentException.class, () ->
                new TableVisualEvent.CollectBets(1L, List.of(
                        new TableVisualEvent.ChipTransfer("player", 10d)),
                        100d, 90d));
    }

    @Test
    void detachedBridgeReportsThatNoFrontendConsumedTheEvent() {
        TableEventBridge bridge = new TableEventBridge();
        assertTrue(bridge.publishIfAttached(sequence ->
                new TableVisualEvent.CloseTable(sequence)).isEmpty());
    }

    private static TableSnapshot emptyTable() {
        return new TableSnapshot(0L, "local", TableSnapshot.Street.WAITING,
                0d, "", false, List.of(), List.of());
    }

    private static final class RecordingRenderer implements TableRenderer {

        private final List<TableVisualEvent> events = new ArrayList<>();
        private CompletableFuture<Void> currentAnimation;
        private boolean closed;

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event);
            currentAnimation = new CompletableFuture<>();
            return currentAnimation;
        }

        void finishCurrentAnimation() {
            currentAnimation.complete(null);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
