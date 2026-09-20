package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrupierTableEventBridgeTest {

    @Test
    void recoveryCinematicIsExclusiveToAnInterruptedReplayableHand() {
        assertTrue(Crupier.shouldShowRecoveryPresentation(2, false));
        assertTrue(Crupier.shouldShowRecoveryPresentation(10, false));

        assertFalse(Crupier.shouldShowRecoveryPresentation(2, true));
        assertFalse(Crupier.shouldShowRecoveryPresentation(1, false));
        assertFalse(Crupier.shouldShowRecoveryPresentation(0, false));
    }

    @Test
    void foldedLocalPlayerCanKeepAutoChoiceForTheNextHand() {
        assertTrue(Crupier.localPreActionsEligible(false, Player.FOLD,
                false, false, false));
        assertFalse(Crupier.localPreActionsEligible(true, Player.FOLD,
                false, false, false));
        assertTrue(Crupier.shouldPresentLocalPreActions(true, false,
                Player.FOLD, false, false, false));
        assertTrue(Crupier.shouldPresentLocalPreActions(true, false,
                Player.NODEC, false, false, false));

        assertFalse(Crupier.shouldPresentLocalPreActions(false, false,
                Player.FOLD, false, false, false));
        assertFalse(Crupier.shouldPresentLocalPreActions(true, true,
                Player.FOLD, false, false, false));
        assertFalse(Crupier.shouldPresentLocalPreActions(true, false,
                Player.ALLIN, false, false, false));
        assertFalse(Crupier.shouldPresentLocalPreActions(true, false,
                Player.FOLD, true, false, false));
        assertFalse(Crupier.shouldPresentLocalPreActions(true, false,
                Player.FOLD, false, true, false));
        assertFalse(Crupier.shouldPresentLocalPreActions(true, false,
                Player.FOLD, false, false, true));
    }

    @Test
    void rendererBarrierStopsWaitingAsSoonAsTableTerminationIsRequested()
            throws Exception {
        CompletableFuture<Void> animation = new CompletableFuture<>();
        AtomicInteger cancellationPolls = new AtomicInteger();

        long started = System.nanoTime();
        boolean completed = Crupier.awaitPresentationBarrier(animation,
                () -> cancellationPolls.incrementAndGet() >= 2, 4_000L);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started);

        assertFalse(completed);
        assertFalse(animation.isDone());
        assertTrue(elapsedMillis < 500L,
                "Exit waited for the renderer timeout instead of cancelling");
    }

    @Test
    void rendererBarrierStillBlocksNormalPlayUntilAnimationCompletes()
            throws Exception {
        CompletableFuture<Void> animation = new CompletableFuture<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> wait = executor.submit(() ->
                    Crupier.awaitPresentationBarrier(animation,
                            () -> false, 4_000L));

            assertFalse(wait.isDone());
            animation.complete(null);
            assertTrue(wait.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ordinaryHoleCardSortingNeverBlocksTheDealer() throws Exception {
        TableEventBridge bridge = new TableEventBridge();
        BlockingRenderer renderer = new BlockingRenderer();
        bridge.attach(renderer, emptyTable()).toCompletableFuture().join();
        Crupier dealer = new Crupier(bridge);

        try {
            long started = System.nanoTime();
            assertTrue(dealer.presentHoleCardSwapToAttachedRenderer(
                    "local", false));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - started);

            assertTrue(renderer.eventReceived.await(1, TimeUnit.SECONDS));
            TableVisualEvent.SwapHoleCards event = assertInstanceOf(
                    TableVisualEvent.SwapHoleCards.class, renderer.event);
            assertFalse(event.blocking());
            assertFalse(renderer.animation.isDone());
            assertTrue(elapsedMillis < 250L,
                    "Cosmetic hole-card sorting blocked the dealer");
        } finally {
            renderer.animation.complete(null);
            bridge.close();
        }
    }

    @Test
    void positionRotationIsAlsoARealDealerBarrier() throws Exception {
        TableEventBridge bridge = new TableEventBridge();
        BlockingRenderer renderer = new BlockingRenderer();
        bridge.attach(renderer, emptyTable()).toCompletableFuture().join();
        Crupier dealer = new Crupier(bridge);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<TableVisualEvent.PositionTransfer> transfers = List.of(
                new TableVisualEvent.PositionTransfer(
                        "old-bb", "new-bb", TableSnapshot.Position.BIG_BLIND, false));

        try {
            Future<Boolean> presented = executor.submit(() ->
                    dealer.presentPositionRotationToAttachedRenderer(transfers, 240L));

            assertTrue(renderer.eventReceived.await(1, TimeUnit.SECONDS));
            assertFalse(presented.isDone(),
                    "The dealer crossed the position-flight barrier before landing");
            TableVisualEvent.PositionRotation event = assertInstanceOf(
                    TableVisualEvent.PositionRotation.class, renderer.event);
            assertEquals(transfers, event.transfers());
            assertEquals(240L, event.durationMillis());

            renderer.animation.complete(null);
            assertTrue(presented.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            bridge.close();
        }
    }

    @Test
    void forcedBetsBlockShuffleProgressUntilTheRendererCompletesItsImpact() throws Exception {
        TableEventBridge bridge = new TableEventBridge();
        BlockingRenderer renderer = new BlockingRenderer();
        bridge.attach(renderer, emptyTable()).toCompletableFuture().join();
        Crupier dealer = new Crupier(bridge);
        AtomicBoolean smallDeferred = new AtomicBoolean(true);
        AtomicBoolean bigDeferred = new AtomicBoolean(true);
        Player small = player("small", 50d, smallDeferred);
        Player big = player("big", 100d, bigDeferred);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> presented = executor.submit(() ->
                    dealer.presentForcedBetsToAttachedRenderer(
                            List.of(small, big), 0d, 150d));

            assertTrue(renderer.eventReceived.await(1, TimeUnit.SECONDS));
            assertFalse(presented.isDone(),
                    "The dealer crossed the visual barrier before chip impact");
            TableVisualEvent.CollectBets event = assertInstanceOf(
                    TableVisualEvent.CollectBets.class, renderer.event);
            assertEquals(List.of(
                    new TableVisualEvent.ChipTransfer("small", 50d,
                            0d, 0d, 50d),
                    new TableVisualEvent.ChipTransfer("big", 100d,
                            0d, 0d, 100d)), event.transfers());
            assertEquals(0d, event.potBefore());
            assertEquals(150d, event.potAfterLanding());

            renderer.animation.complete(null);
            assertTrue(presented.get(1, TimeUnit.SECONDS));
            assertFalse(smallDeferred.get());
            assertFalse(bigDeferred.get());
        } finally {
            executor.shutdownNow();
            bridge.close();
        }
    }

    @Test
    void shuffleFinishIsTheBarrierBeforeDealing() throws Exception {
        TableEventBridge bridge = new TableEventBridge();
        ShuffleRenderer renderer = new ShuffleRenderer();
        bridge.attach(renderer, emptyTable()).toCompletableFuture().join();
        Crupier dealer = new Crupier(bridge);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            assertTrue(dealer.presentShufflePhaseToAttachedRenderer(
                    TableVisualEvent.Shuffle.Phase.START));
            assertEquals(TableVisualEvent.Shuffle.Phase.START, renderer.lastPhase);

            Future<Boolean> finished = executor.submit(() ->
                    dealer.presentShufflePhaseToAttachedRenderer(
                            TableVisualEvent.Shuffle.Phase.FINISH));
            assertTrue(renderer.finishReceived.await(1, TimeUnit.SECONDS));
            assertFalse(finished.isDone(),
                    "The dealer crossed the shuffle barrier before its final frame/audio");

            renderer.finishAnimation.complete(null);
            assertTrue(finished.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            bridge.close();
        }
    }

    private static Player player(String nickname, double pot,
            AtomicBoolean counterDeferred) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNickname" -> nickname;
                    case "getBote" -> pot;
                    case "getStack", "getBet" -> 0d;
                    case "setCounterRollDeferred" -> {
                        counterDeferred.set((boolean) args[0]);
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> nickname;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return 0;
    }

    private static TableSnapshot emptyTable() {
        return new TableSnapshot(0L, "local", TableSnapshot.Street.WAITING,
                0d, "", false, List.of(), List.of());
    }

    private static final class BlockingRenderer implements TableRenderer {

        private final CountDownLatch eventReceived = new CountDownLatch(1);
        private final CompletableFuture<Void> animation = new CompletableFuture<>();
        private volatile TableVisualEvent event;

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            this.event = event;
            eventReceived.countDown();
            return animation;
        }

        @Override
        public void close() {
        }
    }

    private static final class ShuffleRenderer implements TableRenderer {

        private final CountDownLatch finishReceived = new CountDownLatch(1);
        private final CompletableFuture<Void> finishAnimation = new CompletableFuture<>();
        private volatile TableVisualEvent.Shuffle.Phase lastPhase;

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            TableVisualEvent.Shuffle shuffle = assertInstanceOf(
                    TableVisualEvent.Shuffle.class, event);
            lastPhase = shuffle.phase();
            if (shuffle.phase() == TableVisualEvent.Shuffle.Phase.FINISH) {
                finishReceived.countDown();
                return finishAnimation;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }
}
