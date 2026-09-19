package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Product-bound GDX scenario driver with a deterministic action gate.
 *
 * <p>It consumes the real peer snapshots/events, keeps the same
 * {@link GdxTableViewState} observed by a production
 * {@link CoronaPokerGdxTable}, and activates that table's controls. The gate
 * can stop one chosen hand at the local decision boundary so a scenario can
 * cut the real socket without adding sleeps or changing dealer timing.</p>
 */
final class GdxScenarioRenderer implements TableRenderer {

    private final TableSession table;
    private final int expectedPlayers;
    private final AtomicReference<CoronaPokerGdxTable> productTable;
    private final AtomicReference<GdxTableViewState> state
            = new AtomicReference<>();
    private final AtomicReference<TableSessionSummary> summary
            = new AtomicReference<>();
    private final AtomicInteger endedHands = new AtomicInteger();
    private final AtomicLong currentHand = new AtomicLong(1L);
    private final AtomicLong gatedHand = new AtomicLong(-1L);
    private final AtomicReference<TableSnapshot.Street> gatedStreet
            = new AtomicReference<>();
    private final AtomicBoolean gateConsumed = new AtomicBoolean();
    private final AtomicBoolean heldAction = new AtomicBoolean();
    private final AtomicBoolean sawLocalControls = new AtomicBoolean();
    private final AtomicBoolean sawRemoteAction = new AtomicBoolean();
    private final AtomicBoolean sawPaused = new AtomicBoolean();
    private final AtomicBoolean resumedAfterPause = new AtomicBoolean();
    private final AtomicLong allInHand = new AtomicLong(-1L);
    private final AtomicBoolean allInCommandSent = new AtomicBoolean();
    private final AtomicBoolean acceptedLocalAllIn = new AtomicBoolean();
    private final AtomicBoolean sawAllInAction = new AtomicBoolean();
    private final AtomicBoolean sawAllInCinematic = new AtomicBoolean();
    private final AtomicLong runItTwiceSideASequence = new AtomicLong();
    private final AtomicLong runItTwiceSideBSequence = new AtomicLong();
    private final List<Integer> runItTwiceSideBDeals
            = new CopyOnWriteArrayList<>();
    private final AtomicLong immediateRebuyHand = new AtomicLong(-1L);
    private final AtomicBoolean immediateRebuyRequested = new AtomicBoolean();
    private final AtomicBoolean foldAutomatically = new AtomicBoolean();
    private final AtomicBoolean sawLocalSpectator = new AtomicBoolean();
    private final AtomicBoolean returnedAfterSpectating = new AtomicBoolean();
    private final java.util.concurrent.ConcurrentHashMap<String, Integer>
            immediateRebuys = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicReference<Runnable> afterAllInCommand
            = new AtomicReference<>(() -> { });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final EnumSet<TableSnapshot.Street> streets
            = EnumSet.noneOf(TableSnapshot.Street.class);
    private final List<String> actionTrace = new CopyOnWriteArrayList<>();
    private final Set<String> spectatorsEver
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> reactivatedSpectators
            = java.util.concurrent.ConcurrentHashMap.newKeySet();

    GdxScenarioRenderer(TableSession table, int expectedPlayers) {
        this(table, expectedPlayers, new AtomicReference<>());
    }

    GdxScenarioRenderer(TableSession table, int expectedPlayers,
            AtomicReference<CoronaPokerGdxTable> productTable) {
        this.table = table;
        this.expectedPlayers = expectedPlayers;
        this.productTable = productTable;
    }

    void gateActionOnHand(long handId) {
        gatedHand.set(handId);
        gatedStreet.set(null);
        gateConsumed.set(false);
        heldAction.set(false);
    }

    void gateActionOnStreet(long handId, TableSnapshot.Street street) {
        gatedHand.set(handId);
        gatedStreet.set(street);
        gateConsumed.set(false);
        heldAction.set(false);
    }

    void allInOnHand(long handId) {
        allInOnHand(handId, () -> { });
    }

    void allInOnHand(long handId, Runnable postAction) {
        allInHand.set(handId);
        allInCommandSent.set(false);
        acceptedLocalAllIn.set(false);
        afterAllInCommand.set(postAction);
    }

    void requestImmediateRebuyOnHand(long handId) {
        immediateRebuyHand.set(handId);
        immediateRebuyRequested.set(false);
    }

    void requestImmediateRebuyNow() {
        if (immediateRebuyRequested.compareAndSet(false, true)) {
            table.commands().submit(new TableCommand.ToggleImmediateRebuy());
        }
    }

    void foldAutomatically(boolean enabled) {
        foldAutomatically.set(enabled);
    }

    @Override
    public synchronized CompletionStage<Void> open(TableSnapshot initialState) {
        GdxTableViewState projection = new GdxTableViewState(initialState);
        state.set(projection);
        productTable.set(new CoronaPokerGdxTable(60, projection,
                table.commands(), () -> { }, new GdxGameLogSink(), null));
        streets.add(initialState.street());
        rememberSpectatorTransitions(initialState);
        initialState.players().stream()
                .filter(player -> player.nickname().equals(
                        initialState.localNickname()))
                .findFirst()
                .filter(TableSnapshot.PlayerSnapshot::spectator)
                .ifPresent(ignored -> sawLocalSpectator.set(true));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<Void> render(TableVisualEvent event) {
        GdxTableViewState projection = state.get();
        assertNotNull(projection, "renderer must open before events arrive");
        projection.apply(event);
        if (event instanceof TableVisualEvent.HandBoundary boundary) {
            if (boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.PREPARE) {
                currentHand.set(boundary.handId());
            } else if (boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            }
        }
        TableSnapshot snapshot = projection.snapshot();
        rememberSpectatorTransitions(snapshot);
        TableSnapshot.PlayerSnapshot local = snapshot.players().stream()
                .filter(player -> player.nickname().equals(
                        snapshot.localNickname()))
                .findFirst().orElse(null);
        if (local != null && local.spectator()) {
            sawLocalSpectator.set(true);
            if (currentHand.get() == immediateRebuyHand.get()
                    && immediateRebuyRequested.compareAndSet(false, true)) {
                table.commands().submit(
                        new TableCommand.ToggleImmediateRebuy());
            }
        } else if (local != null && sawLocalSpectator.get()
                && immediateRebuyRequested.get()) {
            returnedAfterSpectating.set(true);
        }
        if (snapshot.paused()) {
            sawPaused.set(true);
        } else if (sawPaused.get()) {
            resumedAfterPause.set(true);
        }
        streets.add(snapshot.street());
        assertFalse(snapshot.localNickname().isBlank());
        assertTrue(snapshot.pot() >= 0d);
        assertTrue(snapshot.players().stream().allMatch(player ->
                player.stack() >= 0d && player.streetBet() >= 0d
                        && player.potContribution() >= 0d));

        if (event instanceof TableVisualEvent.Cinematic cinematic
                && cinematic.type() == TableVisualEvent.Cinematic.Type.ALL_IN
                && cinematic.phase()
                == TableVisualEvent.Cinematic.Phase.START) {
            sawAllInCinematic.set(true);
        }
        if (event instanceof TableVisualEvent.ActionControls controls
                && (controls.state().callAction()
                != ActionControlState.CallAction.DISABLED
                || controls.state().allInEnabled())) {
            assertEquals(snapshot.localNickname(), snapshot.currentTurnNickname(),
                    "GDX controls may only activate for the local turn");
            sawLocalControls.set(true);
            if (!gateConsumed.get()
                    && gatedHand.get() == currentHand.get()
                    && (gatedStreet.get() == null
                    || gatedStreet.get() == snapshot.street())) {
                heldAction.set(true);
                return CompletableFuture.completedFuture(null);
            }
            if (foldAutomatically.get() && controls.state().foldEnabled()) {
                assertTrue(productTable().activateFoldAction(),
                        "native GDX fold control did not submit");
            } else if (allInHand.get() == currentHand.get()
                    && controls.state().allInEnabled()
                    && allInCommandSent.compareAndSet(false, true)) {
                assertTrue(productTable().activateAllInAction(),
                        "native GDX ALL-IN control did not arm");
                assertTrue(productTable().activateAllInAction(),
                        "native GDX ALL-IN control did not submit");
                afterAllInCommand.get().run();
            } else if (controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                assertTrue(productTable().activateCheckOrCallAction(),
                        "native GDX check/call control did not submit");
            } else {
                assertTrue(productTable().activateAllInAction(),
                        "native GDX fallback ALL-IN control did not arm");
                assertTrue(productTable().activateAllInAction(),
                        "native GDX fallback ALL-IN control did not submit");
            }
        } else if (event instanceof TableVisualEvent.PlayerAction action) {
            actionTrace.add(currentHand.get() + ":" + snapshot.street() + ":"
                    + action.nickname() + ":" + action.kind() + ":"
                    + action.amount() + ":" + action.contributionDelta());
            if (action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
                sawAllInAction.set(true);
            }
            if (!action.nickname().equals(snapshot.localNickname())) {
                sawRemoteAction.set(true);
            } else if (action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
                acceptedLocalAllIn.set(true);
            }
        } else if (event instanceof TableVisualEvent.ImmediateRebuyStatus status) {
            if (status.enabled()) {
                immediateRebuys.put(status.nickname(), status.amount());
            } else {
                immediateRebuys.remove(status.nickname());
            }
        } else if (event instanceof TableVisualEvent.RunItTwiceBoard board) {
            if (board.side() == TableVisualEvent.RunItTwiceBoard.Side.A) {
                runItTwiceSideASequence.compareAndSet(0L, board.sequence());
            } else {
                runItTwiceSideBSequence.compareAndSet(0L, board.sequence());
            }
        } else if (event instanceof TableVisualEvent.DealCommunityCard deal
                && runItTwiceSideBSequence.get() > 0L
                && deal.sequence() > runItTwiceSideBSequence.get()) {
            runItTwiceSideBDeals.add(deal.slot());
        } else if (event instanceof TableVisualEvent.CloseTable close) {
            summary.set(close.summary());
            closed.set(true);
        }
        return CompletableFuture.completedFuture(null);
    }

    void releaseHeldAction() {
        if (heldAction.compareAndSet(true, false)) {
            gateConsumed.set(true);
            assertTrue(productTable().activateCheckOrCallAction(),
                    "native GDX gated check/call control did not submit");
        }
    }

    void releaseHeldActionAndGate(long nextHand,
            TableSnapshot.Street nextStreet) {
        if (heldAction.compareAndSet(true, false)) {
            gatedHand.set(nextHand);
            gatedStreet.set(nextStreet);
            gateConsumed.set(false);
            assertTrue(productTable().activateCheckOrCallAction(),
                    "native GDX chained check/call control did not submit");
        }
    }

    CoronaPokerGdxTable productTable() {
        CoronaPokerGdxTable current = productTable.get();
        assertNotNull(current, "native GDX table must be created on open");
        return current;
    }

    boolean hasHeldAction() {
        return heldAction.get();
    }

    boolean isClosed() {
        return closed.get();
    }

    boolean hasAcceptedLocalAllIn() {
        return acceptedLocalAllIn.get();
    }

    boolean sawAllInCinematic() {
        return sawAllInCinematic.get();
    }

    boolean sawAllInAction() {
        return sawAllInAction.get();
    }

    boolean completedRunItTwiceBoards() {
        return runItTwiceSideASequence.get() > 0L
                && runItTwiceSideBSequence.get()
                > runItTwiceSideASequence.get()
                && runItTwiceSideBDeals.equals(List.of(0, 1, 2, 3, 4));
    }

    boolean sawLocalSpectator() {
        return sawLocalSpectator.get();
    }

    List<String> actionTrace() {
        return List.copyOf(actionTrace);
    }

    boolean requestedImmediateRebuy() {
        return immediateRebuyRequested.get();
    }

    boolean returnedAfterSpectating() {
        return returnedAfterSpectating.get();
    }

    String localNickname() {
        GdxTableViewState projection = state.get();
        return projection == null ? "" : projection.snapshot().localNickname();
    }

    boolean hasImmediateRebuy(String nickname) {
        return immediateRebuys.getOrDefault(nickname, 0) > 0;
    }

    boolean isPaused() {
        GdxTableViewState projection = state.get();
        return projection != null && projection.snapshot().paused();
    }

    boolean sawPaused() {
        return sawPaused.get();
    }

    boolean resumedAfterPause() {
        return resumedAfterPause.get();
    }

    int completedHands() {
        return endedHands.get();
    }

    TableSessionSummary summary() {
        return summary.get();
    }

    Map<String, Double> balancesByNickname() {
        TableSessionSummary finalSummary = summary.get();
        assertNotNull(finalSummary);
        Map<String, Double> balances = new TreeMap<>();
        finalSummary.balances().forEach(balance -> balances.put(
                balance.nickname(), balance.finalStack()));
        return balances;
    }

    Set<String> activeNicknames() {
        GdxTableViewState projection = state.get();
        assertNotNull(projection);
        return projection.snapshot().players().stream()
                .filter(player -> !player.exited())
                .map(TableSnapshot.PlayerSnapshot::nickname)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    Set<String> spectatorNicknames() {
        GdxTableViewState projection = state.get();
        assertNotNull(projection);
        return projection.snapshot().players().stream()
                .filter(TableSnapshot.PlayerSnapshot::spectator)
                .map(TableSnapshot.PlayerSnapshot::nickname)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    Set<String> playingNicknames() {
        GdxTableViewState projection = state.get();
        assertNotNull(projection);
        return projection.snapshot().players().stream()
                .filter(player -> !player.exited() && !player.spectator())
                .map(TableSnapshot.PlayerSnapshot::nickname)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    String playerStateDiagnostic() {
        GdxTableViewState projection = state.get();
        if (projection == null) {
            return "<renderer not opened>";
        }
        return projection.snapshot().players().stream()
                .map(player -> player.nickname()
                + "{stack=" + player.stack()
                + ",active=" + player.active()
                + ",spectator=" + player.spectator()
                + ",exit=" + player.exited() + "}")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    String configurationDiagnostic() {
        GdxTableViewState projection = state.get();
        if (projection == null || projection.gameConfiguration() == null) {
            return "<no configuration event>";
        }
        return "botRebuy=" + projection.gameConfiguration().botRebuy()
                + ",rebuy=" + projection.gameConfiguration().rebuy()
                + ",hands=" + projection.gameConfiguration().hands();
    }

    boolean sawSpectator(String nickname) {
        return spectatorsEver.contains(nickname);
    }

    boolean sawSpectatorReactivated(String nickname) {
        return reactivatedSpectators.contains(nickname);
    }

    long currentHand() {
        return currentHand.get();
    }

    private void rememberSpectatorTransitions(TableSnapshot snapshot) {
        for (TableSnapshot.PlayerSnapshot player : snapshot.players()) {
            if (player.spectator()) {
                spectatorsEver.add(player.nickname());
            } else if (!player.exited()
                    && spectatorsEver.contains(player.nickname())) {
                reactivatedSpectators.add(player.nickname());
            }
        }
    }

    void assertComplete(int expectedHands) {
        assertCompleteState(expectedHands, expectedPlayers, null,
                expectedPlayers, true);
    }

    void assertCompleteWithHistoricalBalances(int expectedHands,
            int expectedActivePlayers, int expectedBalanceRows) {
        assertCompleteState(expectedHands, null, expectedActivePlayers,
                expectedBalanceRows, true);
    }

    void assertCompleteAsPassiveObserver(int expectedHands,
            int expectedActivePlayers, int expectedBalanceRows) {
        assertCompleteState(expectedHands, null, expectedActivePlayers,
                expectedBalanceRows, false);
    }

    private void assertCompleteState(int expectedHands,
            Integer expectedSnapshotPlayers, Integer expectedActivePlayers,
            int expectedBalanceRows, boolean requireLocalControls) {
        GdxTableViewState projection = state.get();
        assertNotNull(projection);
        assertEquals(expectedHands, endedHands.get());
        if (requireLocalControls) {
            assertTrue(sawLocalControls.get(), () -> localNickname()
                    + " never received enabled local action controls; trace="
                    + actionTrace);
        }
        assertTrue(sawRemoteAction.get(), () -> localNickname()
                + " never observed a remote action; trace=" + actionTrace);
        assertTrue(streets.containsAll(EnumSet.of(
                TableSnapshot.Street.PREFLOP,
                TableSnapshot.Street.FLOP,
                TableSnapshot.Street.TURN,
                TableSnapshot.Street.RIVER,
                TableSnapshot.Street.SHOWDOWN)));
        if (expectedSnapshotPlayers != null) {
            assertEquals(expectedSnapshotPlayers,
                    projection.snapshot().players().size());
        }
        if (expectedActivePlayers != null) {
            assertEquals(expectedActivePlayers, activeNicknames().size());
        }
        assertEquals(5, projection.snapshot().communityCards().size());
        assertTrue(projection.snapshot().communityCards().stream()
                .allMatch(card -> card.visible() && card.faceUp()));
        TableSessionSummary finalSummary = summary.get();
        assertNotNull(finalSummary);
        assertEquals(table.initialState().localNickname(),
                finalSummary.localNickname());
        assertEquals(expectedBalanceRows, finalSummary.balances().size());
        double stacks = finalSummary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                .sum();
        double buyins = finalSummary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                .sum();
        assertEquals(buyins, stacks, 0.001d);
    }

    @Override
    public void close() {
    }
}
