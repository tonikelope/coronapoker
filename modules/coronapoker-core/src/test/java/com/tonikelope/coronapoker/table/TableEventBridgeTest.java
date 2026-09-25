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
                        new TableVisualEvent.ChipTransfer("small", 50d,
                                950d, 50d, 50d),
                        new TableVisualEvent.ChipTransfer("big", 100d,
                                900d, 100d, 100d)),
                        0d, 150d));

        assertFalse(barrier.toCompletableFuture().isDone());
        assertEquals(List.of(new TableVisualEvent.CollectBets(1L, List.of(
                new TableVisualEvent.ChipTransfer("small", 50d,
                        950d, 50d, 50d),
                new TableVisualEvent.ChipTransfer("big", 100d,
                        900d, 100d, 100d)),
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
                sequence, 7L, TableVisualEvent.HandBoundary.Phase.PREPARE,
                emptyPreflopTable()));
        renderer.finishCurrentAnimation();
        bridge.publish(sequence -> new TableVisualEvent.Shuffle(
                sequence, "default", TableVisualEvent.Shuffle.Phase.START));

        assertEquals(List.of(1L, 2L), renderer.events.stream()
                .map(TableVisualEvent::sequence).toList());
    }

    @Test
    void eventsRacingRendererOpeningWaitUntilTheNativeTableIsReady() {
        TableEventBridge bridge = new TableEventBridge();
        CompletableFuture<Void> rendererReady = new CompletableFuture<>();
        List<TableVisualEvent> events = new ArrayList<>();
        TableRenderer renderer = new TableRenderer() {
            @Override
            public CompletionStage<Void> open(TableSnapshot initialState) {
                return rendererReady;
            }

            @Override
            public CompletionStage<Void> render(TableVisualEvent event) {
                events.add(event);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void close() { }
        };

        CompletionStage<Void> opening = bridge.attach(renderer, emptyTable());
        CompletionStage<Void> first = bridge.publish(sequence ->
                new TableVisualEvent.PreparationStatus(sequence,
                        TableVisualEvent.PreparationStatus.Phase.DRAWING_SEATS));
        CompletionStage<Void> second = bridge.publish(sequence ->
                new TableVisualEvent.PreparationStatus(sequence,
                        TableVisualEvent.PreparationStatus.Phase.READY));

        assertTrue(events.isEmpty(),
                "no event may reach GDX while its table reference is still null");
        assertFalse(first.toCompletableFuture().isDone());
        assertFalse(second.toCompletableFuture().isDone());

        rendererReady.complete(null);
        opening.toCompletableFuture().join();

        assertEquals(List.of(1L, 2L), events.stream()
                .map(TableVisualEvent::sequence).toList());
        assertTrue(first.toCompletableFuture().isDone());
        assertTrue(second.toCompletableFuture().isDone());
    }

    @Test
    void closingDuringNativeOpeningReleasesEveryBarrierImmediately() {
        TableEventBridge bridge = new TableEventBridge();
        CompletableFuture<Void> rendererReady = new CompletableFuture<>();
        boolean[] rendererClosed = {false};
        TableRenderer renderer = new TableRenderer() {
            @Override
            public CompletionStage<Void> open(TableSnapshot initialState) {
                return rendererReady;
            }

            @Override
            public CompletionStage<Void> render(TableVisualEvent event) {
                throw new AssertionError("closed opening must not render events");
            }

            @Override
            public void close() {
                rendererClosed[0] = true;
            }
        };

        CompletionStage<Void> opening = bridge.attach(renderer, emptyTable());
        CompletionStage<Void> pending = bridge.publish(sequence ->
                new TableVisualEvent.PreparationStatus(sequence,
                        TableVisualEvent.PreparationStatus.Phase.DRAWING_SEATS));

        bridge.close();

        assertTrue(rendererClosed[0]);
        assertTrue(opening.toCompletableFuture().isCompletedExceptionally(),
                "closing must release the native-opening barrier");
        assertTrue(pending.toCompletableFuture().isCompletedExceptionally(),
                "closing must release every event queued behind opening");
        assertFalse(rendererReady.isDone(),
                "presentation close must not fake renderer initialization");
    }

    @Test
    void invalidPotTransitionIsRejectedBeforeItReachesARenderer() {
        assertThrows(IllegalArgumentException.class, () ->
                new TableVisualEvent.CollectBets(1L, List.of(
                        new TableVisualEvent.ChipTransfer("player", 10d,
                                90d, 10d, 10d)),
                        100d, 90d));
    }

    @Test
    void communityRevealCarriesItsCanonicalStreet() {
        TableSnapshot.CardSnapshot card = new TableSnapshot.CardSnapshot(
                "A_C", true, false);

        TableVisualEvent.RevealCommunityCards river
                = new TableVisualEvent.RevealCommunityCards(1L,
                        TableSnapshot.Street.RIVER, 4, List.of(card), 0L);

        assertEquals(TableSnapshot.Street.RIVER, river.street());
        assertThrows(IllegalArgumentException.class, () ->
                new TableVisualEvent.RevealCommunityCards(2L,
                        TableSnapshot.Street.FLOP, 4, List.of(card), 0L));
    }

    @Test
    void detachedBridgeReportsThatNoFrontendConsumedTheEvent() {
        TableEventBridge bridge = new TableEventBridge();
        assertTrue(bridge.publishIfAttached(sequence ->
                new TableVisualEvent.CloseTable(sequence,
                        TableSessionSummary.empty(),
                        TableSnapshot.Street.FINISHED)).isEmpty());
    }

    @Test
    void positionRotationIsOneParallelTimedEvent() {
        TableVisualEvent.PositionRotation rotation = new TableVisualEvent.PositionRotation(
                3L, List.of(
                        new TableVisualEvent.PositionTransfer(
                                "old-bb", "new-bb", TableSnapshot.Position.BIG_BLIND, false),
                        new TableVisualEvent.PositionTransfer(
                                "old-sb", "new-sb", TableSnapshot.Position.SMALL_BLIND, false)),
                240L);

        assertEquals(2, rotation.transfers().size());
        assertEquals(240L, rotation.durationMillis());
        assertThrows(IllegalArgumentException.class, () ->
                new TableVisualEvent.PositionRotation(4L, List.of(), 240L));
    }

    private static TableSnapshot emptyTable() {
        return new TableSnapshot(0L, "local", TableSnapshot.Street.WAITING,
                0d, "", false, List.of(), List.of());
    }

    private static TableSnapshot emptyPreflopTable() {
        return new TableSnapshot(0L, "local", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(), List.of());
    }

    private static TableSnapshot finishedTable() {
        return new TableSnapshot(0L, "local", TableSnapshot.Street.FINISHED,
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
