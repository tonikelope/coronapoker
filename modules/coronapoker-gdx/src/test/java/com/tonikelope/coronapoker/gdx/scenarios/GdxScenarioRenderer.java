package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.EnumSet;
import java.util.HashSet;
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
    private final LobbySession lobby;
    private final AtomicReference<AutoCloseable> lobbySubscription
            = new AtomicReference<>();
    private final AtomicReference<GdxTableViewState> state
            = new AtomicReference<>();
    private final AtomicReference<TableSessionSummary> summary
            = new AtomicReference<>();
    private final AtomicInteger endedHands = new AtomicInteger();
    private final AtomicLong lastEventSequence = new AtomicLong();
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
    private final AtomicBoolean allInAtFirstOpportunity
            = new AtomicBoolean();
    private final AtomicBoolean allInCommandSent = new AtomicBoolean();
    private final AtomicBoolean acceptedLocalAllIn = new AtomicBoolean();
    private final AtomicBoolean sawAllInAction = new AtomicBoolean();
    private final AtomicBoolean sawAllInCinematic = new AtomicBoolean();
    private final AtomicLong runItTwiceSideASequence = new AtomicLong();
    private final AtomicLong runItTwiceSideBSequence = new AtomicLong();
    private final AtomicReference<Double> runItTwiceSideAPot
            = new AtomicReference<>();
    private final AtomicReference<Double> runItTwiceSideBPot
            = new AtomicReference<>();
    private final AtomicReference<String> runItTwiceSideAPrefix
            = new AtomicReference<>();
    private final AtomicReference<String> runItTwiceSideBPrefix
            = new AtomicReference<>();
    private final AtomicBoolean runItTwiceSideASettled
            = new AtomicBoolean();
    private final AtomicBoolean runItTwiceSideBSettled
            = new AtomicBoolean();
    private final AtomicBoolean runItTwiceSideBSettledAtEnd
            = new AtomicBoolean();
    private final List<Integer> runItTwiceSideBDeals
            = new CopyOnWriteArrayList<>();
    private final AtomicLong immediateRebuyHand = new AtomicLong(-1L);
    private final AtomicBoolean immediateRebuyRequested = new AtomicBoolean();
    private final AtomicBoolean foldAutomatically = new AtomicBoolean();
    private final AtomicBoolean allInEveryHand = new AtomicBoolean();
    private final AtomicBoolean raiseMix = new AtomicBoolean();
    private final AtomicInteger localRaiseSubmissions = new AtomicInteger();
    private final AtomicInteger acceptedRaiseActions = new AtomicInteger();
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
    private final Set<Long> allInActionHands
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> spectatorsEver
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> reactivatedSpectators
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, String> departures
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> reconnectingPlayersEver
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Long> straddleHands
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Long, List<TableSnapshot.CardSnapshot>> localDeals
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> localRevealSequences
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> firstActionSequences
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Set<String>> playersAtHandStart
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> preparedHandIds
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Long> skippedRecoveredHandIds
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Long> endedHandIds
            = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicLong lastPreparedHand = new AtomicLong();
    private final AtomicInteger localStraddleDecisions = new AtomicInteger();
    private final AtomicLong localStraddleHand = new AtomicLong(-1L);
    private final AtomicInteger checkedActionLabels = new AtomicInteger();
    private final AtomicInteger checkedTableInfoLabels = new AtomicInteger();
    private final AtomicInteger checkedPotLabels = new AtomicInteger();
    private final AtomicInteger checkedResultLabels = new AtomicInteger();

    GdxScenarioRenderer(TableSession table, int expectedPlayers) {
        this(table, expectedPlayers, new AtomicReference<>());
    }

    GdxScenarioRenderer(TableSession table, int expectedPlayers,
            AtomicReference<CoronaPokerGdxTable> productTable) {
        this(table, expectedPlayers, productTable, null);
    }

    GdxScenarioRenderer(TableSession table, int expectedPlayers,
            AtomicReference<CoronaPokerGdxTable> productTable,
            LobbySession lobby) {
        this.table = table;
        this.expectedPlayers = expectedPlayers;
        this.productTable = productTable;
        this.lobby = lobby;
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

    void allInAtFirstOpportunity() {
        allInAtFirstOpportunity.set(true);
        allInCommandSent.set(false);
        acceptedLocalAllIn.set(false);
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

    void enableRaiseMix() {
        raiseMix.set(true);
    }

    void enableAllInEveryHand() {
        allInEveryHand.set(true);
        allInCommandSent.set(false);
    }

    @Override
    public synchronized CompletionStage<Void> open(TableSnapshot initialState) {
        GdxTableViewState projection = new GdxTableViewState(initialState);
        state.set(projection);
        productTable.set(new CoronaPokerGdxTable(60, projection,
                table.commands(), () -> { }, new GdxGameLogSink(), null,
                lobby));
        if (lobby != null) {
            lobbySubscription.set(lobby.subscribe(ignored -> {
                GdxTableViewState current = state.get();
                if (current != null && !closed.get()) {
                    observeLiveConnectivity(current.snapshot());
                }
            }));
        }
        observeLiveConnectivity(initialState);
        streets.add(initialState.street());
        rememberSpectatorTransitions(initialState);
        assertCanonicalSpectatorPresentation(initialState, projection);
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
        assertFalse(closed.get(), "event arrived after CloseTable: " + event);
        long previousSequence = lastEventSequence.getAndSet(event.sequence());
        assertTrue(event.sequence() > previousSequence,
                "GDX event sequence must increase strictly: previous="
                + previousSequence + ", current=" + event.sequence());
        GdxTableViewState projection = state.get();
        assertNotNull(projection, "renderer must open before events arrive");
        projection.apply(event);
        assertProjectedLabelContract(event, projection);
        if (event instanceof TableVisualEvent.HandBoundary boundary) {
            if (boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.PREPARE) {
                long previousHand = lastPreparedHand.getAndSet(
                        boundary.handId());
                assertTrue(boundary.handId() > 0L,
                        "hand ids must be positive");
                assertTrue(previousHand == 0L
                        || boundary.handId() == previousHand + 1L,
                        "observed hand ids must be consecutive: previous="
                        + previousHand + ", current=" + boundary.handId());
                assertTrue(preparedHandIds.add(boundary.handId()),
                        "duplicate PREPARE for hand " + boundary.handId());
                assertFalse(endedHandIds.contains(boundary.handId()),
                        "PREPARE arrived after END for hand "
                        + boundary.handId());
                currentHand.set(boundary.handId());
                if (allInEveryHand.get()) {
                    allInCommandSent.set(false);
                }
            } else if (boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.SKIP_RECOVERED) {
                assertTrue(preparedHandIds.contains(boundary.handId()),
                        "SKIP_RECOVERED arrived without PREPARE for hand "
                        + boundary.handId());
                assertFalse(endedHandIds.contains(boundary.handId()),
                        "SKIP_RECOVERED arrived after END for hand "
                        + boundary.handId());
                assertEquals(currentHand.get(), boundary.handId(),
                        "SKIP_RECOVERED closed a different hand than active");
                assertTrue(skippedRecoveredHandIds.add(boundary.handId()),
                        "duplicate SKIP_RECOVERED for hand "
                        + boundary.handId());
            } else if (boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                assertTrue(preparedHandIds.contains(boundary.handId()),
                        "END arrived without PREPARE for hand "
                        + boundary.handId());
                assertEquals(currentHand.get(), boundary.handId(),
                        "END closed a different hand than the active one");
                assertTrue(endedHandIds.add(boundary.handId()),
                        "duplicate END for hand " + boundary.handId());
                endedHands.incrementAndGet();
                if (runItTwiceSideBSequence.get() > 0L) {
                    assertTrue(runItTwiceSideBSettled.get(),
                            "RIT END arrived before CARA B paid its winner");
                    assertEquals(0d, projection.snapshot().pot(),
                            "RIT END retained unpaid CARA B chips");
                    assertEquals(runItTwiceSideBPrefix.get(),
                            projection.runItTwicePotPrefix(),
                            "RIT END replaced the settled CARA B label");
                    runItTwiceSideBSettledAtEnd.set(true);
                }
            }
        }
        TableSnapshot snapshot = projection.snapshot();
        observeLiveConnectivity(snapshot);
        if (event instanceof TableVisualEvent.HandBoundary boundary
                && boundary.phase()
                == TableVisualEvent.HandBoundary.Phase.PREPARE) {
            playersAtHandStart.put(boundary.handId(), snapshot.players().stream()
                    .filter(player -> !player.exited())
                    .map(TableSnapshot.PlayerSnapshot::nickname)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        if (event instanceof TableVisualEvent.DealHoleCard deal
                && deal.nickname().equals(snapshot.localNickname())) {
            localDeals.computeIfAbsent(currentHand.get(), ignored
                    -> new CopyOnWriteArrayList<>()).add(deal.card());
        }
        if (event instanceof TableVisualEvent.RevealHoleCards reveal
                && reveal.nickname().equals(snapshot.localNickname())
                && reveal.handName().isBlank()) {
            localRevealSequences.putIfAbsent(currentHand.get(),
                    reveal.sequence());
        }
        if (event instanceof TableVisualEvent.PositionRotation rotation
                && rotation.transfers().stream().anyMatch(transfer
                -> transfer.position() == TableSnapshot.Position.STRADDLE
                || transfer.position()
                == TableSnapshot.Position.DEALER_STRADDLE)) {
            straddleHands.add(currentHand.get());
        }
        rememberSpectatorTransitions(snapshot);
        assertCanonicalSpectatorPresentation(snapshot, projection);
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
            firstActionSequences.putIfAbsent(currentHand.get(),
                    event.sequence());
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
            if (raiseMix.get()
                    && controls.state().raiseAction()
                    != ActionControlState.RaiseAction.DISABLED
                    && localRaiseSubmissions.getAndIncrement() < 2) {
                assertTrue(productTable().activateBetAction(),
                        "native GDX raise-mix control did not submit");
            } else if (foldAutomatically.get()
                    && controls.state().foldEnabled()) {
                assertTrue(productTable().activateFoldAction(),
                        "native GDX fold control did not submit");
            } else if ((allInAtFirstOpportunity.get()
                    || allInEveryHand.get()
                    || allInHand.get() == currentHand.get())
                    && controls.state().allInEnabled()
                    && allInCommandSent.compareAndSet(false, true)) {
                assertTrue(productTable().activateAllInAction(),
                        "native GDX ALL-IN control did not arm");
                assertTrue(productTable().activateAllInAction(),
                        "native GDX ALL-IN control did not submit");
                allInAtFirstOpportunity.set(false);
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
                allInActionHands.add(currentHand.get());
            }
            if (action.kind() == TableVisualEvent.PlayerAction.ActionKind.BET
                    || action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.RAISE
                    || action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.RERAISE) {
                acceptedRaiseActions.incrementAndGet();
            }
            if (!action.nickname().equals(snapshot.localNickname())) {
                sawRemoteAction.set(true);
            } else if (action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
                acceptedLocalAllIn.set(true);
            }
        } else if (event instanceof TableVisualEvent.PlayerDeparture departure) {
            departures.put(departure.nickname(), departure.label());
        } else if (event instanceof TableVisualEvent.ImmediateRebuyStatus status) {
            if (status.enabled()) {
                immediateRebuys.put(status.nickname(), status.amount());
            } else {
                immediateRebuys.remove(status.nickname());
            }
        } else if (event instanceof TableVisualEvent.RunItTwiceBoard board) {
            assertTrue(board.potAmount() > 0d,
                    "each RIT board must project a positive half-pot");
            String tag = presentationText().translate(board.side()
                    == TableVisualEvent.RunItTwiceBoard.Side.A
                            ? "runittwice.pot_label_a"
                            : "runittwice.pot_label_b");
            assertEquals(presentationText().translate(
                    "runittwice.pot_label_full", tag), board.potPrefix(),
                    "RIT board label must match the visible GDX caption");
            assertEquals(board.potAmount(), projection.snapshot().pot(),
                    "RIT board amount was not projected into the HUD");
            assertEquals(board.potPrefix(), projection.runItTwicePotPrefix(),
                    "RIT board prefix was not projected into the HUD");
            if (board.side() == TableVisualEvent.RunItTwiceBoard.Side.A) {
                runItTwiceSideASequence.compareAndSet(0L, board.sequence());
                runItTwiceSideAPot.compareAndSet(null, board.potAmount());
                runItTwiceSideAPrefix.compareAndSet(null,
                        board.potPrefix());
            } else {
                assertTrue(runItTwiceSideASettled.get(),
                        "CARA B started before CARA A paid its winner");
                runItTwiceSideBSequence.compareAndSet(0L, board.sequence());
                runItTwiceSideBPot.compareAndSet(null, board.potAmount());
                runItTwiceSideBPrefix.compareAndSet(null,
                        board.potPrefix());
            }
        } else if (event instanceof TableVisualEvent.Payout payout
                && runItTwiceSideASequence.get() > 0L) {
            assertEquals(0, endedHands.get(),
                    "RIT payout arrived after the hand had already ended");
            if (runItTwiceSideBSequence.get() == 0L) {
                assertEquals(runItTwiceSideAPrefix.get(),
                        projection.runItTwicePotPrefix(),
                        "CARA A payout lost its board label");
                if (payout.potAfter() == 0d) {
                    runItTwiceSideASettled.set(true);
                }
            } else {
                assertEquals(runItTwiceSideBPrefix.get(),
                        projection.runItTwicePotPrefix(),
                        "CARA B payout lost its board label");
                if (payout.potAfter() == 0d) {
                    runItTwiceSideBSettled.set(true);
                }
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

    void submitHeldAllIn(Runnable postAction) {
        if (!heldAction.compareAndSet(true, false)) {
            throw new AssertionError("no held GDX action for ordered all-in");
        }
        gateConsumed.set(true);
        allInCommandSent.set(true);
        assertTrue(productTable().activateAllInAction(),
                "native GDX ordered ALL-IN control did not arm");
        assertTrue(productTable().activateAllInAction(),
                "native GDX ordered ALL-IN control did not submit");
        postAction.run();
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

    boolean nativeCheckOrCallReady() {
        return productTable().canActivateCheckOrCallAction();
    }

    int acceptedRaiseActions() {
        return acceptedRaiseActions.get();
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

    boolean sawAllInOnEveryHand(int expectedHands) {
        for (long hand = 1L; hand <= expectedHands; hand++) {
            if (!allInActionHands.contains(hand)) {
                return false;
            }
        }
        return true;
    }

    boolean completedRunItTwiceBoards() {
        return runItTwiceSideASequence.get() > 0L
                && runItTwiceSideBSequence.get()
                > runItTwiceSideASequence.get()
                && runItTwiceSideAPot.get() != null
                && runItTwiceSideBPot.get() != null
                && runItTwiceSideAPrefix.get() != null
                && runItTwiceSideBPrefix.get() != null
                && !runItTwiceSideAPrefix.get().equals(
                        runItTwiceSideBPrefix.get())
                && runItTwiceSideASettled.get()
                && runItTwiceSideBSettled.get()
                && runItTwiceSideBSettledAtEnd.get()
                && runItTwiceSideBDeals.equals(List.of(0, 1, 2, 3, 4));
    }

    /**
     * Shared oracle for every real GDX scenario.  Besides state/accounting,
     * assert the semantic text that the product table can actually paint.
     * This prevents a valid event sequence from hiding broken labels such as
     * "CoronaBot$4 ALL IN (...)" or a zero-valued CARA B pot.
     */
    private void assertProjectedLabelContract(TableVisualEvent event,
            GdxTableViewState projection) {
        GdxFunctionalLabelOracle.assertProjectedLabelContract(event,
                projection, presentationText());
        if (event instanceof TableVisualEvent.PlayerAction action) {
            checkedActionLabels.incrementAndGet();
        } else if (event instanceof TableVisualEvent.HandResult result) {
            checkedResultLabels.incrementAndGet();
        } else if (event instanceof TableVisualEvent.TableInfo info) {
            checkedTableInfoLabels.incrementAndGet();
        }
        checkedPotLabels.incrementAndGet();
    }

    private GdxGameText presentationText() {
        CoronaPokerGdxTable table = productTable.get();
        return table == null ? new GdxGameText("es")
                : table.presentationText();
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

    boolean sawDeparture(String nickname) {
        return departures.containsKey(nickname);
    }

    boolean sawReconnectingPlayer(String nickname) {
        return reconnectingPlayersEver.contains(nickname);
    }

    void assertNeverShowedReconnectFor(String nickname) {
        assertFalse(sawReconnectingPlayer(nickname),
                nickname + " was visibly projected as reconnecting");
    }

    void assertNoUnexpectedReconnects(Set<String> allowed) {
        Set<String> unexpected = new java.util.HashSet<>(
                reconnectingPlayersEver);
        unexpected.removeAll(allowed);
        assertTrue(unexpected.isEmpty(),
                "unexpected visible reconnect projection: " + unexpected);
    }

    void assertShowedExpectedReconnects(Set<String> expected) {
        assertTrue(reconnectingPlayersEver.containsAll(expected),
                "missing visible reconnect projection: expected=" + expected
                + ", observed=" + reconnectingPlayersEver);
    }

    String departureLabel(String nickname) {
        return departures.getOrDefault(nickname, "");
    }

    void assertDepartureLabel(String nickname, String expected) {
        assertEquals(expected, departureLabel(nickname),
                "wrong visible departure label for " + nickname);
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

    private void observeLiveConnectivity(TableSnapshot snapshot) {
        if (closed.get()) return;
        CoronaPokerGdxTable liveTable = productTable.get();
        if (liveTable == null || lobby == null) return;
        snapshot.players().stream()
                .map(TableSnapshot.PlayerSnapshot::nickname)
                .filter(liveTable::isLiveReconnectingPlayer)
                .forEach(reconnectingPlayersEver::add);
    }

    void recordLocalStraddleDecision() {
        localStraddleDecisions.incrementAndGet();
        localStraddleHand.set(currentHand.get());
    }

    void assertStraddleComplete(int expectedHands) {
        assertComplete(expectedHands);
        assertEquals(Set.of(1L, 2L, 3L), straddleHands);
        assertEquals(1, localStraddleDecisions.get(),
                "each human must receive exactly one straddle decision");
        assertEquals(Set.of(1L, 2L, 3L), localDeals.keySet());
        assertTrue(localDeals.values().stream().allMatch(cards
                -> cards.size() == 2));
        long hand = localStraddleHand.get();
        List<TableSnapshot.CardSnapshot> cards = localDeals.get(hand);
        assertNotNull(cards);
        assertTrue(cards.stream().noneMatch(
                TableSnapshot.CardSnapshot::faceUp),
                "straddler cards must stay hidden until the decision");
        Long reveal = localRevealSequences.get(hand);
        Long action = firstActionSequences.get(hand);
        assertNotNull(reveal,
                "accepted straddle must reveal the deferred pocket cards");
        assertNotNull(action,
                "accepted straddle must reach a betting decision");
        assertTrue(action > reveal,
                "betting cannot overtake the accepted straddle reveal");
    }

    private void assertCanonicalSpectatorPresentation(TableSnapshot snapshot,
            GdxTableViewState projection) {
        for (TableSnapshot.PlayerSnapshot player : snapshot.players()) {
            List<TableSnapshot.CardSnapshot> cards
                    = projection.presentedHoleCards(player.nickname());
            if (player.spectator() && !player.exited()) {
                assertEquals(2, cards.size(),
                        player.nickname() + " spectator card count");
                assertTrue(cards.stream().allMatch(card
                        -> "joker".equals(card.code())
                        && card.faceUp() && card.visible()),
                        player.nickname()
                        + " spectator must show two visible jokers");
                if (player.nickname().equals(snapshot.localNickname())) {
                    String status = CoronaPokerGdxTable.localHudTurnStatus(
                            false, false, player, presentationText());
                    assertFalse(presentationText().translate(
                            "gdx.table.hud.waiting_turn")
                            .equalsIgnoreCase(status),
                            "a local spectator cannot be waiting for a turn");
                    assertFalse(status.isBlank(),
                            "a local spectator needs a canonical status");
                }
            } else if (reactivatedSpectators.contains(player.nickname())) {
                assertFalse(cards.size() == 2 && cards.stream().allMatch(
                        card -> "joker".equals(card.code())),
                        player.nickname()
                        + " kept spectator jokers after reactivation");
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

    void assertCompleteWithHistoricalBalances(int expectedHands,
            int expectedBalanceRows) {
        assertCompleteState(expectedHands, null, null,
                expectedBalanceRows, true);
    }

    void assertHandStartedWithPlayers(long handId, Set<String> expected) {
        assertEquals(expected, playersAtHandStart.get(handId),
                "unexpected active roster at start of hand " + handId);
    }

    void assertCompleteAsPassiveObserver(int expectedHands,
            int expectedSnapshotPlayers, int expectedBalanceRows) {
        // A passive observer may receive a peer's terminal EXIT while the
        // independent JVMs are closing after the final summary.  The roster
        // must still be complete, but requiring every row to remain non-exited
        // at that instant makes process teardown order part of the game oracle.
        // Assert the authoritative visible roster and durable balances instead.
        assertCompleteState(expectedHands, expectedSnapshotPlayers, null,
                expectedBalanceRows, false);
    }

    private void assertCompleteState(int expectedHands,
            Integer expectedSnapshotPlayers, Integer expectedActivePlayers,
            int expectedBalanceRows, boolean requireLocalControls) {
        GdxTableViewState projection = state.get();
        assertNotNull(projection);
        assertEquals(expectedHands, endedHands.get());
        assertEquals(expectedHands, endedHandIds.size(),
                "completed-hand count must represent distinct hands");
        Set<Long> closedHandIds = new HashSet<>(endedHandIds);
        closedHandIds.addAll(skippedRecoveredHandIds);
        assertEquals(preparedHandIds, closedHandIds,
                "a completed table cannot leave a prepared hand open");
        if (requireLocalControls) {
            assertTrue(sawLocalControls.get(), () -> localNickname()
                    + " never received enabled local action controls; trace="
                    + actionTrace);
        }
        assertTrue(checkedActionLabels.get() > 0,
                "completed GDX scenario never checked an action label");
        assertTrue(checkedTableInfoLabels.get() > 0,
                "completed GDX scenario never checked blinds/hand labels");
        assertTrue(checkedPotLabels.get() > 0,
                "completed GDX scenario never checked the pot label");
        assertTrue(checkedResultLabels.get() > 0,
                "completed GDX scenario never checked showdown result labels");
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
                    projection.snapshot().players().size(),
                    this::playerStateDiagnostic);
        }
        if (expectedActivePlayers != null) {
            assertEquals(expectedActivePlayers, activeNicknames().size(),
                    this::playerStateDiagnostic);
        }
        assertEquals(5, projection.snapshot().communityCards().size());
        assertTrue(projection.snapshot().communityCards().stream()
                .allMatch(card -> card.visible() && card.faceUp()));
        TableSessionSummary finalSummary = summary.get();
        assertNotNull(finalSummary);
        assertEquals(TableSessionSummary.CloseReason.COMPLETED,
                finalSummary.reason());
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
        AutoCloseable subscription = lobbySubscription.getAndSet(null);
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception failure) {
                throw new AssertionError(
                        "could not detach scenario lobby observer", failure);
            }
        }
    }
}
