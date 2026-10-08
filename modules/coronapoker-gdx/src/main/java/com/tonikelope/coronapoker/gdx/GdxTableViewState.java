package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Render-thread-owned projection. It contains no rules and never reads Swing. */
final class GdxTableViewState {

    private static final TableSnapshot.CardSnapshot HIDDEN_CARD =
            new TableSnapshot.CardSnapshot("", false, false);
    private static final TableSnapshot.CardSnapshot ABSENT_CARD =
            new TableSnapshot.CardSnapshot("", false, false, false);
    private static final TableSnapshot.CardSnapshot SPECTATOR_CARD =
            new TableSnapshot.CardSnapshot("joker", true, false);

    private TableSnapshot snapshot;
    private long lastSequence;
    private boolean hotJoinStateApplied;
    private long turnTotalMillis;
    private long turnRemainingMillis;
    private long turnTimerUpdatedNanos;
    private long turnTimerPausedAtNanos;
    private TableVisualEvent.SharedProgress.Mode sharedProgressMode
            = TableVisualEvent.SharedProgress.Mode.RESET;
    private long sharedProgressTotalMillis;
    private long sharedProgressRemainingMillis;
    private long sharedProgressUpdatedNanos;
    private long sharedProgressPausedAtNanos;
    private final LongSupplier nanoTime;
    private double smallBlind;
    private double bigBlind;
    private int handNumber;
    private int blindIncreaseInterval;
    private int blindIncreaseType = 1;
    private int blindIncreaseCount;
    private String callCostText = "";
    private String callCostAggressorNickname = "";
    private String runItTwicePotPrefix = "";
    private ActionControlState actionControls = ActionControlState.disabled();
    private long actionControlsSequence;
    private boolean preActionControlsActive;
    private boolean newStreetActionResetPending;
    private boolean lastHand;
    private int maximumHands = -1;
    private GameConfigCodecV1.Configuration gameConfiguration;
    // Fail closed until the dealer publishes the authoritative table rules.
    // The renderer must never grant a capability from a local/demo default.
    private boolean runItTwiceLocked = true;
    private boolean textToSpeechEnabled;
    private boolean voiceMessagesEnabled;
    private long playTimeSeconds;
    private final Map<String, TableVisualEvent.ShowdownHighlight>
            showdownHighlights = new HashMap<>();
    private final Map<String, TableVisualEvent.PlayerAction.ActionKind>
            actionKinds = new HashMap<>();
    private final Map<String, TableVisualEvent.RebuyDecision.Phase>
            rebuyDecisions = new HashMap<>();
    private final Map<String, String> actionLabels = new HashMap<>();
    private final Map<String, Float> partialHandPercentages = new HashMap<>();
    private final Map<String, Integer> immediateRebuys = new HashMap<>();
    private final java.util.Set<String> resolvedHandResults = new HashSet<>();
    private final java.util.Set<String> lateShownHands = new HashSet<>();
    private final java.util.Set<String> iwtsthCandidates = new HashSet<>();
    private final java.util.Set<Integer> rabbitCardSlots = new HashSet<>();
    private boolean rabbitRequestable;
    private String rabbitNoticeNickname = "";
    private long rabbitNoticeUntilNanos;
    /*
     * Fold is ordered presentation state, just like a reveal.  END snapshots
     * may already contain the roster prepared for the following hand, so their
     * active/card flags must not resurrect a folded seat while the previous
     * showdown is still on screen.  PREPARE is the only event that clears it.
     */
    private final java.util.Set<String> foldedThisHand = new HashSet<>();
    /*
     * A HandBoundary.END snapshot is the dealer's exact logical state after
     * payout/reset and therefore legitimately has no transient showdown label.
     * Keep the already ordered HandResult presentation alongside (not merged
     * back into the authoritative snapshot) until PREPARE starts the next hand.
     */
    private final Map<String, String> resolvedHandNames = new HashMap<>();
    private final Map<String, Boolean> resolvedHandWinners = new HashMap<>();
    private final Map<String, List<Integer>> resolvedWonPotIndexes
            = new HashMap<>();
    private final java.util.Set<String> returnedSidePotPlayers
            = new HashSet<>();
    /*
     * RevealHoleCards is also ordered presentation state. A later exact
     * HandBoundary/roster snapshot may still contain the legacy controller's
     * face-down card flags (notably while the local player has already folded
     * and only remote players reach showdown). Keep the accepted reveal until
     * PREPARE instead of allowing such a snapshot to visually cover it again.
     */
    private final Map<String, List<TableSnapshot.CardSnapshot>>
            revealedHoleCards = new HashMap<>();

    GdxTableViewState(TableSnapshot initialState) {
        this(initialState, System::nanoTime);
    }

    GdxTableViewState(TableSnapshot initialState, LongSupplier nanoTime) {
        snapshot = Objects.requireNonNull(initialState, "initialState");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    TableSnapshot snapshot() {
        return snapshot;
    }

    long lastSequence() {
        return lastSequence;
    }

    long turnTotalMillis() {
        return turnTotalMillis;
    }

    boolean turnTimerVisible() {
        return turnTimerUpdatedNanos > 0L && turnTotalMillis > 0L;
    }

    long turnRemainingMillis() {
        if (turnTotalMillis <= 0L || turnRemainingMillis <= 0L) {
            return 0L;
        }
        long now = turnTimerPausedAtNanos > 0L
                ? turnTimerPausedAtNanos : nanoTime.getAsLong();
        long elapsedMillis = Math.max(0L,
                (now - turnTimerUpdatedNanos) / 1_000_000L);
        return Math.max(0L, turnRemainingMillis - elapsedMillis);
    }

    boolean sharedProgressVisible() {
        return sharedProgressMode
                == TableVisualEvent.SharedProgress.Mode.INDETERMINATE
                || sharedProgressTotalMillis > 0L;
    }

    boolean sharedProgressIndeterminate() {
        return sharedProgressMode
                == TableVisualEvent.SharedProgress.Mode.INDETERMINATE;
    }

    float sharedProgressFraction() {
        if (sharedProgressIndeterminate()) return 1f;
        if (sharedProgressTotalMillis <= 0L) return 0f;
        if (sharedProgressMode == TableVisualEvent.SharedProgress.Mode.RESET) {
            return 1f;
        }
        long now = sharedProgressPausedAtNanos > 0L
                ? sharedProgressPausedAtNanos : nanoTime.getAsLong();
        long elapsedMillis = Math.max(0L,
                (now - sharedProgressUpdatedNanos) / 1_000_000L);
        long remaining = Math.max(0L,
                sharedProgressRemainingMillis - elapsedMillis);
        return Math.max(0f, Math.min(1f,
                (float) remaining / (float) sharedProgressTotalMillis));
    }

    ActionControlState actionControls() {
        return actionControls;
    }

    long actionControlsSequence() {
        return actionControlsSequence;
    }

    boolean preActionControlsActive() {
        return preActionControlsActive;
    }

    boolean lastHand() {
        return lastHand;
    }

    int maximumHands() {
        return maximumHands;
    }

    boolean immediateRebuyEnabled(String nickname) {
        return immediateRebuys.getOrDefault(nickname, 0) > 0;
    }

    int immediateRebuyAmount(String nickname) {
        return Math.max(0, immediateRebuys.getOrDefault(nickname, 0));
    }

    GameConfigCodecV1.Configuration gameConfiguration() {
        return gameConfiguration;
    }

    boolean runItTwiceLocked() {
        return runItTwiceLocked;
    }

    boolean textToSpeechEnabled() {
        return textToSpeechEnabled;
    }

    boolean voiceMessagesEnabled() {
        return voiceMessagesEnabled;
    }

    long playTimeSeconds() {
        return playTimeSeconds;
    }

    double smallBlind() {
        return smallBlind;
    }

    double bigBlind() {
        return bigBlind;
    }

    int blindIncreaseInterval() {
        return blindIncreaseInterval;
    }

    int blindIncreaseType() {
        return blindIncreaseType;
    }

    int blindIncreaseCount() {
        return blindIncreaseCount;
    }

    boolean anteEnabled() {
        return gameConfiguration != null && gameConfiguration.ante();
    }

    boolean straddleEnabled() {
        return gameConfiguration != null && gameConfiguration.straddle();
    }

    int handNumber() {
        return handNumber;
    }

    String callCostText() {
        return callCostText;
    }

    String callCostAggressorNickname() {
        return callCostAggressorNickname;
    }

    String runItTwicePotPrefix() {
        return runItTwicePotPrefix;
    }

    TableVisualEvent.ShowdownHighlight showdownHighlight(String nickname) {
        return showdownHighlights.get(nickname);
    }

    boolean hasShowdownHighlights() {
        return !showdownHighlights.isEmpty();
    }

    boolean hasHandResult(String nickname) {
        return resolvedHandResults.contains(nickname);
    }

    boolean hasLateShownHand(String nickname) {
        return lateShownHands.contains(nickname);
    }

    boolean foldedThisHand(String nickname) {
        return foldedThisHand.contains(nickname);
    }

    String resolvedHandName(String nickname) {
        return resolvedHandNames.getOrDefault(nickname, "");
    }

    Boolean resolvedHandWinner(String nickname) {
        return resolvedHandWinners.get(nickname);
    }

    List<Integer> resolvedWonPotIndexes(String nickname) {
        return resolvedWonPotIndexes.getOrDefault(nickname, List.of());
    }

    boolean returnedSidePot(String nickname) {
        return returnedSidePotPlayers.contains(nickname);
    }

    List<TableSnapshot.CardSnapshot> presentedHoleCards(String nickname) {
        TableSnapshot.PlayerSnapshot player = snapshot.players().stream()
                .filter(candidate -> candidate.nickname().equals(nickname))
                .findFirst().orElse(null);
        if (player != null && player.spectator() && !player.exited()) {
            // Swing resets both cards to the deck joker for every spectator:
            // busted players, mid-hand joiners warming up and recovered
            // players waiting for the next hand.
            return List.of(SPECTATOR_CARD, SPECTATOR_CARD);
        }
        List<TableSnapshot.CardSnapshot> revealed = revealedHoleCards.get(
                nickname);
        if (revealed != null) {
            return revealed;
        }
        if (foldedThisHand.contains(nickname)) {
            // Swing removes a folded rival's pocket but leaves the local
            // player's own cards on the table in their disabled state.  The
            // FoldHoleCards projection above already marks both cards as
            // disabled, so preserve only that local visual information here.
            // Never use this exception for a remote player: hidden rival cards
            // must still disappear immediately after the fold.
            return player != null && nickname.equals(snapshot.localNickname())
                    ? player.holeCards().stream()
                            .map(GdxTableViewState::disabledCard)
                            .toList()
                    : List.of();
        }
        return player == null ? List.of() : player.holeCards();
    }

    private static TableSnapshot.CardSnapshot disabledCard(
            TableSnapshot.CardSnapshot card) {
        return card.disabled() ? card : new TableSnapshot.CardSnapshot(
                card.code(), card.faceUp(), true, card.visible());
    }

    private static TableSnapshot.CardSnapshot enabledCard(
            TableSnapshot.CardSnapshot card) {
        return card.disabled() ? new TableSnapshot.CardSnapshot(
                card.code(), card.faceUp(), false, card.visible()) : card;
    }

    boolean hasRevealedHoleCards(String nickname) {
        return revealedHoleCards.containsKey(nickname);
    }

    Float partialHandPercentage(String nickname) {
        return partialHandPercentages.get(nickname);
    }

    /**
     * Returns the canonical post-showdown focus for a visible card, or
     * {@code null} while no winning hand has been resolved. This is separate
     * from the temporary yellow hover highlight: Swing permanently dims every
     * shown card except the five cards in the winning combination once the
     * verdicts are presented.
     */
    Boolean showdownCardSelected(String nickname, int slot,
            boolean communityCard) {
        java.util.Set<Integer> winningCommunitySlots = new HashSet<>();
        boolean resolvedWinnerWithCards = false;
        for (TableSnapshot.PlayerSnapshot player : snapshot.players()) {
            if (!Boolean.TRUE.equals(resolvedHandWinner(player.nickname()))
                    || !resolvedHandResults.contains(player.nickname())) {
                continue;
            }
            TableVisualEvent.ShowdownHighlight highlight
                    = showdownHighlights.get(player.nickname());
            if (highlight == null || !highlight.enabled()) {
                continue;
            }
            resolvedWinnerWithCards = true;
            winningCommunitySlots.addAll(highlight.communityCardSlots());
        }
        if (!resolvedWinnerWithCards) {
            return null;
        }
        if (communityCard) {
            return winningCommunitySlots.contains(slot);
        }
        if (!resolvedHandResults.contains(nickname)) {
            return null;
        }
        TableSnapshot.PlayerSnapshot player = snapshot.players().stream()
                .filter(candidate -> candidate.nickname().equals(nickname))
                .findFirst().orElse(null);
        if (player == null
                || !Boolean.TRUE.equals(resolvedHandWinner(nickname))) {
            return false;
        }
        TableVisualEvent.ShowdownHighlight highlight
                = showdownHighlights.get(nickname);
        return highlight != null && highlight.enabled()
                && highlight.holeCardSlots().contains(slot);
    }

    TableVisualEvent.PlayerAction.ActionKind actionKind(String nickname) {
        return actionKinds.get(nickname);
    }

    TableVisualEvent.RebuyDecision.Phase rebuyDecision(String nickname) {
        return rebuyDecisions.get(nickname);
    }

    String actionLabel(String nickname) {
        return actionLabels.getOrDefault(nickname, "");
    }

    boolean isIwtsthCandidate(String nickname) {
        return iwtsthCandidates.contains(nickname);
    }

    java.util.Set<String> iwtsthCandidates() {
        return java.util.Set.copyOf(iwtsthCandidates);
    }

    void dismissIwtsthCandidates() {
        iwtsthCandidates.clear();
    }

    boolean rabbitRequestable() {
        return rabbitRequestable;
    }

    boolean isRabbitCard(int slot) {
        return rabbitCardSlots.contains(slot);
    }

    void dismissRabbitRequest() {
        rabbitRequestable = false;
    }

    boolean rabbitNoticeActive(String nickname) {
        return nickname != null && nickname.equals(rabbitNoticeNickname)
                && nanoTime.getAsLong() < rabbitNoticeUntilNanos;
    }

    void apply(TableVisualEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.sequence() <= lastSequence) {
            throw new IllegalArgumentException("Non-monotonic GDX table event "
                    + event.sequence() + " after " + lastSequence);
        }
        lastSequence = event.sequence();

        if (event instanceof TableVisualEvent.HotJoinState state) {
            if (!hotJoinStateApplied) {
                applyHotJoinState(state.snapshot());
                hotJoinStateApplied = true;
            }
        } else if (event instanceof TableVisualEvent.PauseStatus pause) {
            boolean wasPaused = snapshot.paused();
            snapshot = new TableSnapshot(snapshot.revision(),
                    snapshot.localNickname(), snapshot.street(), snapshot.pot(),
                    snapshot.currentTurnNickname(), pause.paused(),
                    snapshot.players(), snapshot.communityCards());
            updateTurnTimerPause(wasPaused, snapshot.paused());
            updateSharedProgressPause(wasPaused, snapshot.paused());
        } else if (event instanceof TableVisualEvent.TelemetryStatus telemetry) {
            for (TableVisualEvent.PlayerTelemetry update : telemetry.players()) {
                replacePlayer(update.nickname(), player
                        -> copyPlayerTelemetry(player, update));
            }
        } else if (event instanceof TableVisualEvent.PlayerTimeout timeout) {
            replacePlayer(timeout.nickname(), player
                    -> copyPlayerTimeout(player, timeout.timedOut()));
        } else if (event instanceof TableVisualEvent.PlayerDeparture departure) {
            actionKinds.remove(departure.nickname());
            actionLabels.put(departure.nickname(), departure.label());
            replacePlayer(departure.nickname(), player
                    -> copyPlayerDeparture(player, departure.label()));
            if (departure.nickname().equals(snapshot.currentTurnNickname())) {
                stopTurn();
            }
        } else if (event instanceof TableVisualEvent.SeatRoster roster) {
            List<TableSnapshot.PlayerSnapshot> players = roster.players()
                    .stream().map(this::preserveWarmingUntilAdmission)
                    .toList();
            snapshot = copySnapshot(snapshot, snapshot.pot(),
                    snapshot.currentTurnNickname(), players,
                    snapshot.communityCards());
            players.stream()
                    .filter(TableSnapshot.PlayerSnapshot::spectator)
                    .map(TableSnapshot.PlayerSnapshot::nickname)
                    .forEach(this::clearCompletedHandPresentation);
        } else if (event instanceof TableVisualEvent.HandBoundary boundary) {
            applyHandBoundary(boundary);
        } else if (event instanceof TableVisualEvent.CollectBets collect) {
            applyCollection(collect);
        } else if (event instanceof TableVisualEvent.DealHoleCard deal) {
            replacePlayer(deal.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), player.winner(), player.position(),
                    player.lastAction(), player.handName(),
                    replaceCard(player.holeCards(), deal.slot(), deal.card(),
                            deal.slot() + 1)));
        } else if (event instanceof TableVisualEvent.DealCommunityCard deal) {
            snapshot = copySnapshot(snapshot, snapshot.pot(),
                    snapshot.currentTurnNickname(), snapshot.players(),
                    replaceCard(snapshot.communityCards(), deal.slot(), HIDDEN_CARD,
                            deal.slot() + 1));
        } else if (event instanceof TableVisualEvent.RunItTwiceBoard board) {
            if (board.side() == TableVisualEvent.RunItTwiceBoard.Side.B) {
                resetRunItTwiceBoardPresentation();
            }
            List<TableSnapshot.CardSnapshot> community
                    = snapshot.communityCards();
            for (Integer slot : board.redealSlots()) {
                community = replaceCard(community, slot, ABSENT_CARD, 5);
            }
            snapshot = copySnapshot(snapshot, board.potAmount(),
                    snapshot.currentTurnNickname(), snapshot.players(), community);
            runItTwicePotPrefix = board.potPrefix();
        } else if (event instanceof TableVisualEvent.SwapHoleCards swap) {
            // RevealHoleCards is intentionally preserved across later legacy
            // snapshots. Keep that presentation copy in the same order as the
            // authoritative player snapshot or a deferred straddle reveal
            // will visually undo its subsequent sort.
            List<TableSnapshot.CardSnapshot> revealed =
                    revealedHoleCards.get(swap.nickname());
            if (revealed != null) {
                List<TableSnapshot.CardSnapshot> cards = padded(revealed, 2);
                revealedHoleCards.put(swap.nickname(),
                        List.of(cards.get(1), cards.get(0)));
            }
            replacePlayer(swap.nickname(), player -> {
                List<TableSnapshot.CardSnapshot> cards = padded(player.holeCards(), 2);
                return copyPlayer(player, player.stack(), player.streetBet(),
                        player.potContribution(), player.active(), player.winner(),
                        player.position(), player.lastAction(), player.handName(),
                        List.of(cards.get(1), cards.get(0)));
            });
        } else if (event instanceof TableVisualEvent.FoldHoleCards fold) {
            foldedThisHand.add(fold.nickname());
            revealedHoleCards.remove(fold.nickname());
            replacePlayer(fold.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    false, player.winner(), player.position(), player.lastAction(),
                    player.handName(), player.holeCards().stream()
                            .map(card -> new TableSnapshot.CardSnapshot(
                            card.code(), card.faceUp(), true,
                            card.visible())).toList()));
        } else if (event instanceof TableVisualEvent.RevealCommunityCards reveal) {
            // Keep the completed street visible for the whole reveal. The
            // first START timer of the next street is the authoritative point
            // at which a player can act again, and therefore the first point
            // at which the old actions may disappear.
            newStreetActionResetPending = true;
            List<TableSnapshot.CardSnapshot> board = snapshot.communityCards();
            for (int offset = 0; offset < reveal.cards().size(); offset++) {
                board = replaceCard(board, reveal.firstSlot() + offset,
                        reveal.cards().get(offset), 5);
            }
            snapshot = copySnapshot(snapshot,
                    reveal.street(), snapshot.pot(),
                    snapshot.currentTurnNickname(), snapshot.players(), board);
        } else if (event instanceof TableVisualEvent.TurnTimer timer) {
            if (timer.phase() == TableVisualEvent.TurnTimer.Phase.START
                    && newStreetActionResetPending) {
                resetActionsForNewStreet();
                newStreetActionResetPending = false;
            }
            applyTurnTimer(timer);
        } else if (event instanceof TableVisualEvent.SharedProgress progress) {
            applySharedProgress(progress);
        } else if (event instanceof TableVisualEvent.TableInfo info) {
            smallBlind = info.smallBlind();
            bigBlind = info.bigBlind();
            handNumber = info.handNumber();
            blindIncreaseInterval = info.blindIncreaseInterval();
            blindIncreaseType = info.blindIncreaseType();
            blindIncreaseCount = info.blindIncreaseCount();
        } else if (event instanceof TableVisualEvent.CallCost callCost) {
            callCostText = callCost.text();
            callCostAggressorNickname = callCost.aggressorNickname();
        } else if (event instanceof TableVisualEvent.ActionControls controls) {
            actionControls = controls.state();
            actionControlsSequence = controls.sequence();
        } else if (event instanceof TableVisualEvent.PreActionControls controls) {
            preActionControlsActive = controls.active();
        } else if (event instanceof TableVisualEvent.PlayerAction action) {
            actionKinds.put(action.nickname(), action.kind());
            actionLabels.put(action.nickname(), action.label());
            replacePlayer(action.nickname(), player -> copyPlayer(player,
                    action.stackAfter(), action.streetBetAfter(),
                    action.potContributionAfter(),
                    player.active(),
                    player.winner(), player.position(), action.label(),
                    player.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.AllInRunoutPause) {
            // Sequence-only presentation barrier. Keeping the state untouched
            // is what preserves the accepted ALL IN/CALL label for its full
            // dramatic beat before reveals and partial hands arrive.
        } else if (event instanceof TableVisualEvent.RevealHoleCards reveal) {
            if (!reveal.handName().isBlank()
                    && (resolvedHandResults.contains(reveal.nickname())
                    || resolvedHandWinners.containsKey(reveal.nickname())
                    || foldedThisHand.contains(reveal.nickname()))) {
                lateShownHands.add(reveal.nickname());
            }
            // A voluntary or forced late reveal is no longer a valid IWTSTH
            // target. Remove it immediately instead of waiting for another
            // full candidate-list publication.
            iwtsthCandidates.remove(reveal.nickname());
            revealedHoleCards.put(reveal.nickname(),
                    List.of(reveal.left(), reveal.right()));
            // A mucked loser reaches showdown with an intentionally blank
            // HandResult so its hand is not leaked.  IWTSTH (or a later
            // voluntary SHOW) subsequently publishes the authorized cards and
            // evaluated hand. Complete that existing result now: the winner
            // flag remains false, but the seat caption becomes PAREJA, COLOR,
            // etc. instead of staying on the stale generic PIERDE verdict.
            if (resolvedHandResults.contains(reveal.nickname())
                    && !reveal.handName().isBlank()) {
                resolvedHandNames.put(reveal.nickname(), reveal.handName());
            }
            replacePlayer(reveal.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), player.winner(), player.position(),
                     player.lastAction(), reveal.handName(),
                     List.of(reveal.left(), reveal.right())));
        } else if (event instanceof TableVisualEvent.PartialHand partial) {
            partialHandPercentages.put(partial.nickname(),
                    partial.winPercentage());
            replacePlayer(partial.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), partial.winner(), player.position(),
                    player.lastAction(), partial.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.HandResult result) {
            partialHandPercentages.remove(result.nickname());
            resolvedHandResults.add(result.nickname());
            resolvedHandNames.put(result.nickname(), result.handName());
            resolvedHandWinners.put(result.nickname(), result.winner());
            resolvedWonPotIndexes.put(result.nickname(),
                    result.wonPotIndexes());
            // The sole-survivor event settles every peer's local HUD without
            // fabricating HandResult rows for the other seats. Their captions
            // therefore keep the real action (for example NO VA), while only
            // the local player receives the derived losing outcome.
            String localNickname = snapshot.localNickname();
            if (result.soleSurvivor() && !localNickname.isBlank()
                    && !localNickname.equals(result.nickname())) {
                resolvedHandWinners.put(localNickname, false);
            }
            snapshot = copySnapshot(snapshot, result.street(),
                    snapshot.pot(), snapshot.currentTurnNickname(),
                    snapshot.players(), snapshot.communityCards());
            replacePlayer(result.nickname(), player -> copyPlayer(player,
                    player.stack(), player.streetBet(), player.potContribution(),
                    player.active(), result.winner(), player.position(),
                    player.lastAction(), result.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.IwtsthCandidates candidates) {
            iwtsthCandidates.clear();
            iwtsthCandidates.addAll(candidates.nicknames());
        } else if (event instanceof TableVisualEvent.RabbitCards rabbit) {
            List<TableSnapshot.CardSnapshot> board
                    = snapshot.communityCards();
            for (TableVisualEvent.RabbitCard card : rabbit.cards()) {
                board = replaceCard(board, card.slot(), card.card(), 5);
                rabbitCardSlots.add(card.slot());
            }
            snapshot = copySnapshot(snapshot, snapshot.pot(),
                    snapshot.currentTurnNickname(), snapshot.players(), board);
            rabbitRequestable = rabbit.requestable();
        } else if (event instanceof TableVisualEvent.RabbitResult result) {
            replacePlayer(result.nickname(), player -> copyPlayer(player,
                    result.stackAfter(), player.streetBet(),
                    player.potContribution(), player.active(),
                    player.winner(), player.position(), player.lastAction(),
                    player.handName(), player.holeCards()));
        } else if (event instanceof TableVisualEvent.RabbitNotice notice) {
            rabbitNoticeNickname = notice.nickname();
            rabbitNoticeUntilNanos = nanoTime.getAsLong()
                    + notice.durationMillis() * 1_000_000L;
        } else if (event instanceof TableVisualEvent.ShowdownHighlight highlight) {
            if (highlight.enabled()) {
                showdownHighlights.put(highlight.nickname(), highlight);
            } else {
                showdownHighlights.remove(highlight.nickname());
            }
        } else if (event instanceof TableVisualEvent.PayoutBatch payout) {
            for (TableVisualEvent.PayoutBatch.Transfer transfer
                    : payout.transfers()) {
                if (transfer.returnsResidualSidePot()) {
                    returnedSidePotPlayers.add(transfer.nickname());
                }
                boolean won = MoneyMath.compare(
                        transfer.winningsAmount(), 0d) > 0;
                if (won) {
                    resolvedHandWinners.put(transfer.nickname(), true);
                }
                replacePlayer(transfer.nickname(), player -> copyPlayer(player,
                        transfer.stackAfter(), player.streetBet(),
                        player.potContribution(), player.active(),
                        won || player.winner(), player.position(),
                        player.lastAction(), player.handName(),
                        player.holeCards()));
            }
            snapshot = copySnapshot(snapshot, payout.potAfter(),
                    snapshot.currentTurnNickname(), snapshot.players(),
                    snapshot.communityCards());
        } else if (event instanceof TableVisualEvent.Payout payout) {
            // Run It Twice now publishes one payout stream per board. Preserve
            // the active CARA-A/CARA-B prefix while that board's chips leave
            // the pot; the next board or hand boundary owns the transition.
            // Keep payout as a defensive winner signal as well. Canonical
            // single-survivor hands now publish HandResult before the
            // between-hands wait, but recovery/older producers may still only
            // provide the positive payout event.
            resolvedHandWinners.put(payout.nickname(), true);
            if (payout.potIndex() > 0) {
                recordWonPotIndex(payout.nickname(), payout.potIndex());
            }
            replacePlayer(payout.nickname(), player -> copyPlayer(player,
                    payout.stackAfter(), player.streetBet(),
                    player.potContribution(), player.active(), true,
                    player.position(), player.lastAction(), player.handName(),
                    player.holeCards()));
            snapshot = copySnapshot(snapshot,
                    payout.potAfter(),
                    snapshot.currentTurnNickname(), snapshot.players(),
                    snapshot.communityCards());
        } else if (event instanceof TableVisualEvent.Rebuy rebuy) {
            for (TableVisualEvent.ChipTransfer transfer : rebuy.transfers()) {
                replacePlayer(transfer.nickname(), player -> copyPlayerRebuy(
                        player, transfer));
            }
        } else if (event instanceof TableVisualEvent.RebuyDecision decision) {
            if (decision.phase()
                    == TableVisualEvent.RebuyDecision.Phase.CLEARED) {
                rebuyDecisions.remove(decision.nickname());
            } else {
                rebuyDecisions.put(decision.nickname(), decision.phase());
            }
        } else if (event instanceof TableVisualEvent.ImmediateRebuyStatus status) {
            if (status.enabled()) {
                immediateRebuys.put(status.nickname(), status.amount());
            } else {
                immediateRebuys.remove(status.nickname());
            }
        } else if (event instanceof TableVisualEvent.LastHandStatus status) {
            lastHand = status.enabled();
        } else if (event instanceof TableVisualEvent.HandLimitStatus status) {
            maximumHands = status.maximumHands();
        } else if (event instanceof TableVisualEvent.GameConfigurationStatus status) {
            gameConfiguration = status.configuration();
            smallBlind = gameConfiguration.smallBlind();
            bigBlind = gameConfiguration.bigBlind();
            blindIncreaseInterval = gameConfiguration.blindsDouble();
            blindIncreaseType = gameConfiguration.blindsDoubleType();
            maximumHands = gameConfiguration.hands();
        } else if (event instanceof TableVisualEvent.RunItTwiceLockStatus status) {
            runItTwiceLocked = status.locked();
        } else if (event instanceof TableVisualEvent.CommunicationRulesStatus status) {
            textToSpeechEnabled = status.textToSpeech();
            voiceMessagesEnabled = status.voiceMessages();
        } else if (event instanceof TableVisualEvent.GameClock clock) {
            playTimeSeconds = clock.playTimeSeconds();
        } else if (event instanceof TableVisualEvent.CloseTable close) {
            snapshot = copySnapshot(snapshot, close.terminalStreet(),
                    snapshot.pot(), "", snapshot.players(),
                    snapshot.communityCards());
            stopTurn();
        } else if (event instanceof TableVisualEvent.UnderTheGunStatus status) {
            List<TableSnapshot.PlayerSnapshot> players = snapshot.players()
                    .stream()
                    .map(player -> copyPlayerUnderTheGun(player,
                            player.nickname().equals(status.nickname())))
                    .toList();
            snapshot = copySnapshot(snapshot, snapshot.street(),
                    snapshot.pot(), snapshot.currentTurnNickname(), players,
                    snapshot.communityCards());
        } else if (event instanceof TableVisualEvent.PositionRotation rotation) {
            // Normal blind/dealer rotations are transient flights and the
            // canonical snapshot already owns their final state.  A voluntary
            // straddle is different: it is announced after that snapshot, so
            // persist that transfer or the chip falls back to ordinary DEALER
            // as soon as the flight finishes.
            for (TableVisualEvent.PositionTransfer transfer
                    : rotation.transfers()) {
                if (transfer.position() == TableSnapshot.Position.STRADDLE
                        || transfer.position()
                                == TableSnapshot.Position.DEALER_STRADDLE) {
                    replacePlayer(transfer.toNickname(), player
                            -> copyPlayer(player, player.stack(),
                                    player.streetBet(),
                                    player.potContribution(), player.active(),
                                    player.winner(), transfer.position(),
                                    player.lastAction(), player.handName(),
                                    player.holeCards()));
                }
            }
        } else if (event instanceof TableVisualEvent.PreparationStatus
                || event instanceof TableVisualEvent.DeckChanged
                || event instanceof TableVisualEvent.InitialStackFill
                || event instanceof TableVisualEvent.Cinematic
                || event instanceof TableVisualEvent.AudioCue
                || event instanceof TableVisualEvent.SpecialCardSound
                || event instanceof TableVisualEvent.LateJoinRequest
                || event instanceof TableVisualEvent.Shuffle) {
            // Transient presentation-only events still consume their sequence.
        } else {
            // Never let a newly added core event degrade into an invisible GDX
            // no-op. A missing projection is a contract violation and must fail
            // loudly until its real, renderer-neutral semantics are wired.
            throw new IllegalArgumentException(
                    "Unsupported GDX table event " + event.getClass().getName());
        }
    }

    private void applyHotJoinState(TableSnapshot next) {
        snapshot = Objects.requireNonNull(next, "next");
        // HOTJOIN_STATE is the one-time bootstrap. From this point onward the
        // ordinary ordered TableVisualEvent stream is the sole presentation
        // authority, exactly as it is for players and normal spectators.
        callCostText = "";
        callCostAggressorNickname = "";
        preActionControlsActive = false;
        if (snapshot.currentTurnNickname().isBlank()) stopTurn();
    }

    /**
     * Swing resets each still-actionable player's decision when a new betting
     * street opens. Fold and all-in are hand-long states and must remain
     * visible; every other action belongs only to the street just completed.
     */
    private void resetActionsForNewStreet() {
        actionKinds.entrySet().removeIf(entry
                -> !persistentAcrossStreets(entry.getValue()));
        actionLabels.keySet().removeIf(nickname
                -> !persistentAcrossStreets(actionKinds.get(nickname)));
        List<TableSnapshot.PlayerSnapshot> players = snapshot.players().stream()
                .map(player -> {
                    TableVisualEvent.PlayerAction.ActionKind kind =
                            actionKinds.get(player.nickname());
                    boolean persistent = foldedThisHand.contains(
                            player.nickname()) || persistentAcrossStreets(kind);
                    return persistent ? player : copyPlayer(player,
                            player.stack(), player.streetBet(),
                            player.potContribution(), player.active(),
                            player.winner(), player.position(), "",
                            player.handName(), player.holeCards());
                }).toList();
        snapshot = copySnapshot(snapshot, snapshot.pot(),
                snapshot.currentTurnNickname(), players,
                snapshot.communityCards());
        callCostText = "";
        callCostAggressorNickname = "";
    }

    private static boolean persistentAcrossStreets(
            TableVisualEvent.PlayerAction.ActionKind kind) {
        return kind == TableVisualEvent.PlayerAction.ActionKind.FOLD
                || kind == TableVisualEvent.PlayerAction.ActionKind.ALL_IN;
    }

    private void clearCompletedHandPresentation(String nickname) {
        revealedHoleCards.remove(nickname);
        foldedThisHand.remove(nickname);
        showdownHighlights.remove(nickname);
        partialHandPercentages.remove(nickname);
        resolvedHandResults.remove(nickname);
        lateShownHands.remove(nickname);
        resolvedHandNames.remove(nickname);
        resolvedHandWinners.remove(nickname);
        resolvedWonPotIndexes.remove(nickname);
        returnedSidePotPlayers.remove(nickname);
    }

    private void recordWonPotIndex(String nickname, int potIndex) {
        java.util.TreeSet<Integer> indexes = new java.util.TreeSet<>(
                resolvedWonPotIndexes(nickname));
        indexes.add(potIndex);
        resolvedWonPotIndexes.put(nickname, List.copyOf(indexes));
    }

    private void applyHandBoundary(TableVisualEvent.HandBoundary boundary) {
        TableSnapshot boundarySnapshot = boundary.snapshot();
        // END is published after the dealer has closed the accounting model
        // (whose canonical pot is already zero), but before the visible
        // showdown payout flies to the winners.  Swing kept the last painted
        // Run It Twice half on screen throughout that interval.  Replacing the
        // whole projection here produced the impossible "BOTE (CARA-B): 0"
        // captured in a real game.  The following Payout event remains the
        // single owner of clearing the board prefix and moving the pot.
        if (boundary.phase() == TableVisualEvent.HandBoundary.Phase.END
                && !runItTwicePotPrefix.isBlank()) {
            boundarySnapshot = copySnapshot(boundarySnapshot,
                    snapshot.pot(), boundarySnapshot.currentTurnNickname(),
                    boundarySnapshot.players(),
                    boundarySnapshot.communityCards());
        }
        snapshot = boundarySnapshot;
        if (boundary.phase() == TableVisualEvent.HandBoundary.Phase.PREPARE) {
            showdownHighlights.clear();
            actionKinds.clear();
            actionLabels.clear();
            rebuyDecisions.clear();
            partialHandPercentages.clear();
            resolvedHandResults.clear();
            lateShownHands.clear();
            resolvedHandNames.clear();
            resolvedHandWinners.clear();
            resolvedWonPotIndexes.clear();
            returnedSidePotPlayers.clear();
            revealedHoleCards.clear();
            foldedThisHand.clear();
            iwtsthCandidates.clear();
            rabbitCardSlots.clear();
            rabbitRequestable = false;
            rabbitNoticeNickname = "";
            rabbitNoticeUntilNanos = 0L;
            callCostText = "";
            callCostAggressorNickname = "";
            runItTwicePotPrefix = "";
            preActionControlsActive = false;
            newStreetActionResetPending = false;
        }
        stopTurn();
    }

    /**
     * Swing's {@code repaintLastAction()} removes SIDE-A's verdict and restores
     * each player's accepted action before SIDE-B is dealt. Core-backed GDX
     * players deliberately have no Swing repaint hook, so the ordered board
     * event owns the equivalent presentation reset. Revealed hole cards remain
     * authoritative for the whole RIT hand and must not be covered again.
     */
    private void resetRunItTwiceBoardPresentation() {
        showdownHighlights.clear();
        partialHandPercentages.clear();
        resolvedHandResults.clear();
        lateShownHands.clear();
        resolvedHandNames.clear();
        resolvedHandWinners.clear();
        resolvedWonPotIndexes.clear();
        returnedSidePotPlayers.clear();
        // Swing's repaintLastAction() calls enfocar() on both hole cards for
        // every player before SIDE-B.  The GDX projection must do the same to
        // both its accepted reveal copy and the underlying snapshot; merely
        // clearing the SIDE-A verdict leaves the CardSnapshot.disabled flag
        // set and therefore keeps winners/losers faded during the second run.
        revealedHoleCards.replaceAll((nickname, cards) -> cards.stream()
                .map(GdxTableViewState::enabledCard)
                .toList());
        List<TableSnapshot.PlayerSnapshot> players = snapshot.players().stream()
                .map(player -> {
                    List<TableSnapshot.CardSnapshot> cards
                            = revealedHoleCards.containsKey(player.nickname())
                            ? player.holeCards().stream()
                                    .map(GdxTableViewState::enabledCard)
                                    .toList()
                            : player.holeCards();
                    return copyPlayer(player, player.stack(),
                            player.streetBet(), player.potContribution(),
                            player.active(), false, player.position(),
                            player.lastAction(), "", cards);
                })
                .toList();
        snapshot = copySnapshot(snapshot, snapshot.pot(),
                snapshot.currentTurnNickname(), players,
                snapshot.communityCards());
    }

    private void applyCollection(TableVisualEvent.CollectBets collect) {
        for (TableVisualEvent.ChipTransfer transfer : collect.transfers()) {
            requireKnownPlayer(transfer.nickname());
        }
        for (TableVisualEvent.ChipTransfer transfer : collect.transfers()) {
            replacePlayer(transfer.nickname(), player -> copyPlayer(player,
                    transfer.stackAfter(), transfer.streetBetAfter(),
                    transfer.potContributionAfter(),
                    player.active(), player.winner(), player.position(),
                    player.lastAction(), player.handName(), player.holeCards()));
        }
        // Run It Twice is announced as soon as the final all-in action closes,
        // while the cosmetic collection of those action chips may still be
        // queued behind it. The collection carries the canonical FULL pot;
        // overwriting the board event's half here made CARA A jump back to the
        // full amount. Once a board is active, its RunItTwiceBoard event owns
        // the central amount until settlement begins.
        double displayedPot = runItTwicePotPrefix.isBlank()
                ? collect.potAfterLanding() : snapshot.pot();
        snapshot = copySnapshot(snapshot, displayedPot,
                snapshot.currentTurnNickname(), snapshot.players(),
                snapshot.communityCards());
    }

    private void applyTurnTimer(TableVisualEvent.TurnTimer timer) {
        if (timer.phase() == TableVisualEvent.TurnTimer.Phase.STOP) {
            stopTurn();
            return;
        }
        turnTotalMillis = timer.totalMillis();
        turnRemainingMillis = timer.remainingMillis();
        turnTimerUpdatedNanos = nanoTime.getAsLong();
        turnTimerPausedAtNanos = snapshot.paused()
                ? turnTimerUpdatedNanos : 0L;
        snapshot = copySnapshot(snapshot, snapshot.pot(), timer.nickname(),
                snapshot.players(), snapshot.communityCards());
        if (!snapshot.localNickname().equals(timer.nickname())) {
            actionControls = ActionControlState.disabled();
        }
    }

    private void applySharedProgress(TableVisualEvent.SharedProgress progress) {
        sharedProgressMode = progress.mode();
        sharedProgressTotalMillis = Math.max(0L, progress.seconds() * 1_000L);
        sharedProgressRemainingMillis = sharedProgressTotalMillis;
        sharedProgressUpdatedNanos = nanoTime.getAsLong();
        sharedProgressPausedAtNanos = snapshot.paused()
                ? sharedProgressUpdatedNanos : 0L;
    }

    private void updateSharedProgressPause(boolean wasPaused, boolean paused) {
        if (sharedProgressUpdatedNanos <= 0L || wasPaused == paused) return;
        long now = nanoTime.getAsLong();
        if (paused) {
            sharedProgressPausedAtNanos = now;
        } else if (sharedProgressPausedAtNanos > 0L) {
            sharedProgressUpdatedNanos += now - sharedProgressPausedAtNanos;
            sharedProgressPausedAtNanos = 0L;
        }
    }

    private void updateTurnTimerPause(boolean wasPaused, boolean paused) {
        if (turnTimerUpdatedNanos <= 0L || wasPaused == paused) {
            return;
        }
        long now = nanoTime.getAsLong();
        if (paused) {
            turnTimerPausedAtNanos = now;
        } else if (turnTimerPausedAtNanos > 0L) {
            turnTimerUpdatedNanos += now - turnTimerPausedAtNanos;
            turnTimerPausedAtNanos = 0L;
        }
    }

    private void stopTurn() {
        turnTotalMillis = 0L;
        turnRemainingMillis = 0L;
        turnTimerUpdatedNanos = 0L;
        turnTimerPausedAtNanos = 0L;
        actionControls = ActionControlState.disabled();
        if (!snapshot.currentTurnNickname().isBlank()) {
            snapshot = copySnapshot(snapshot, snapshot.pot(), "",
                    snapshot.players(), snapshot.communityCards());
        }
    }

    private void replacePlayer(String nickname,
            java.util.function.UnaryOperator<TableSnapshot.PlayerSnapshot> transform) {
        List<TableSnapshot.PlayerSnapshot> players = new ArrayList<>(snapshot.players());
        for (int index = 0; index < players.size(); index++) {
            if (players.get(index).nickname().equals(nickname)) {
                players.set(index, transform.apply(players.get(index)));
                snapshot = copySnapshot(snapshot, snapshot.pot(),
                        snapshot.currentTurnNickname(), players,
                        snapshot.communityCards());
                return;
            }
        }
        throw new IllegalArgumentException("Unknown GDX table player: " + nickname);
    }

    private void requireKnownPlayer(String nickname) {
        if (snapshot.players().stream()
                .noneMatch(player -> player.nickname().equals(nickname))) {
            throw new IllegalArgumentException(
                    "Unknown GDX table player: " + nickname);
        }
    }

    private static List<TableSnapshot.CardSnapshot> replaceCard(
            List<TableSnapshot.CardSnapshot> source, int slot,
            TableSnapshot.CardSnapshot card, int size) {
        List<TableSnapshot.CardSnapshot> cards = padded(source, size);
        cards.set(slot, card);
        return List.copyOf(cards);
    }

    private static TableSnapshot.PlayerSnapshot copyPlayerTelemetry(
            TableSnapshot.PlayerSnapshot source,
            TableVisualEvent.PlayerTelemetry telemetry) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(), source.stack(),
                source.streetBet(), source.potContribution(), source.active(),
                source.spectator(), source.exited(), source.timedOut(),
                telemetry.latency(), telemetry.previousLatency(),
                telemetry.reconnectionCount(), telemetry.measuredAtMillis(),
                source.winner(), source.underTheGun(), source.position(),
                source.lastAction(), source.handName(), source.holeCards(),
                source.buyIn(), source.rebuyCount(), source.warming());
    }

    private static TableSnapshot.PlayerSnapshot copyPlayerTimeout(
            TableSnapshot.PlayerSnapshot source, boolean timedOut) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(), source.stack(),
                source.streetBet(), source.potContribution(), source.active(),
                source.spectator(), source.exited(), timedOut,
                source.latency(), source.previousLatency(),
                source.reconnectionCount(), source.telemetryAt(),
                source.winner(), source.underTheGun(), source.position(),
                source.lastAction(), source.handName(), source.holeCards(),
                source.buyIn(), source.rebuyCount(), source.warming());
    }

    private static TableSnapshot.PlayerSnapshot copyPlayerDeparture(
            TableSnapshot.PlayerSnapshot source, String label) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(), source.stack(),
                source.streetBet(), source.potContribution(), false,
                source.spectator(), true, false,
                source.latency(), source.previousLatency(),
                source.reconnectionCount(), source.telemetryAt(),
                source.winner(), source.underTheGun(), source.position(),
                label, source.handName(), source.holeCards(), source.buyIn(),
                source.rebuyCount(), source.warming());
    }

    private static TableSnapshot.PlayerSnapshot copyPlayerUnderTheGun(
            TableSnapshot.PlayerSnapshot source, boolean underTheGun) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(),
                source.stack(), source.streetBet(), source.potContribution(),
                source.active(), source.spectator(), source.exited(),
                source.timedOut(), source.latency(), source.previousLatency(),
                source.reconnectionCount(), source.telemetryAt(),
                source.winner(), underTheGun, source.position(),
                source.lastAction(), source.handName(), source.holeCards(),
                source.buyIn(), source.rebuyCount(), source.warming());
    }

    private static List<TableSnapshot.CardSnapshot> padded(
            List<TableSnapshot.CardSnapshot> source, int size) {
        List<TableSnapshot.CardSnapshot> cards = new ArrayList<>(source);
        while (cards.size() < size) {
            cards.add(HIDDEN_CARD);
        }
        return cards;
    }

    private static TableSnapshot copySnapshot(TableSnapshot source, double pot,
            String turn, List<TableSnapshot.PlayerSnapshot> players,
            List<TableSnapshot.CardSnapshot> board) {
        return copySnapshot(source, source.street(), pot, turn, players, board);
    }

    private static TableSnapshot copySnapshot(TableSnapshot source,
            TableSnapshot.Street street, double pot, String turn,
            List<TableSnapshot.PlayerSnapshot> players,
            List<TableSnapshot.CardSnapshot> board) {
        return new TableSnapshot(source.revision(), source.localNickname(),
                street, pot, turn, source.paused(), players, board);
    }

    private static TableSnapshot.PlayerSnapshot copyPlayer(
            TableSnapshot.PlayerSnapshot source, double stack, double streetBet,
            double contribution, boolean active, boolean winner,
            TableSnapshot.Position position, String action, String hand,
            List<TableSnapshot.CardSnapshot> cards) {
        return new TableSnapshot.PlayerSnapshot(source.nickname(), stack,
                streetBet, contribution, active, source.spectator(),
                source.exited(), source.timedOut(), source.latency(),
                source.previousLatency(), source.reconnectionCount(),
                source.telemetryAt(), winner, source.underTheGun(), position,
                action, hand, cards, source.buyIn(), source.rebuyCount(),
                source.warming());
    }

    private static TableSnapshot.PlayerSnapshot copyPlayerRebuy(
            TableSnapshot.PlayerSnapshot source,
            TableVisualEvent.ChipTransfer transfer) {
        int amount = (int) Math.max(0L, Math.round(transfer.amount()));
        return new TableSnapshot.PlayerSnapshot(source.nickname(),
                transfer.stackAfter(), transfer.streetBetAfter(),
                transfer.potContributionAfter(), source.active(),
                source.spectator(), source.exited(), source.timedOut(),
                source.latency(), source.previousLatency(),
                source.reconnectionCount(), source.telemetryAt(),
                source.winner(), source.underTheGun(), source.position(),
                source.lastAction(), source.handName(), source.holeCards(),
                source.buyIn() + amount, source.rebuyCount() + 1,
                source.warming());
    }

    private TableSnapshot.PlayerSnapshot preserveWarmingUntilAdmission(
            TableSnapshot.PlayerSnapshot incoming) {
        if (incoming.warming() || !incoming.spectator()
                || incoming.exited()) {
            return incoming;
        }
        boolean wasWarming = snapshot.players().stream()
                .filter(player -> player.nickname().equals(
                        incoming.nickname()))
                .findFirst()
                .map(TableSnapshot.PlayerSnapshot::warming)
                .orElse(false);
        if (!wasWarming) return incoming;
        return new TableSnapshot.PlayerSnapshot(incoming.nickname(),
                incoming.stack(), incoming.streetBet(),
                incoming.potContribution(), incoming.active(),
                incoming.spectator(), incoming.exited(),
                incoming.timedOut(), incoming.latency(),
                incoming.previousLatency(), incoming.reconnectionCount(),
                incoming.telemetryAt(), incoming.winner(),
                incoming.underTheGun(), incoming.position(),
                incoming.lastAction(), incoming.handName(),
                incoming.holeCards(), incoming.buyIn(),
                incoming.rebuyCount(), true);
    }
}
