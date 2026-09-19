/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.List;
import java.util.Objects;

/** Semantic visual events emitted by the unchanged game flow. */
public sealed interface TableVisualEvent permits TableVisualEvent.PreparationStatus,
        TableVisualEvent.PauseStatus,
        TableVisualEvent.TelemetryStatus,
        TableVisualEvent.HandBoundary, TableVisualEvent.Shuffle,
        TableVisualEvent.PositionRotation,
        TableVisualEvent.CollectBets, TableVisualEvent.DealHoleCard,
        TableVisualEvent.DealCommunityCard,
        TableVisualEvent.RunItTwiceBoard,
        TableVisualEvent.SwapHoleCards, TableVisualEvent.FoldHoleCards,
        TableVisualEvent.RevealCommunityCards, TableVisualEvent.TurnTimer,
        TableVisualEvent.SharedProgress,
        TableVisualEvent.TableInfo, TableVisualEvent.CallCost,
        TableVisualEvent.ActionControls, TableVisualEvent.PreActionControls,
        TableVisualEvent.PlayerAction, TableVisualEvent.Cinematic,
        TableVisualEvent.AudioCue,
        TableVisualEvent.SpecialCardSound,
        TableVisualEvent.InitialStackFill,
        TableVisualEvent.RevealHoleCards, TableVisualEvent.PartialHand,
        TableVisualEvent.HandResult,
        TableVisualEvent.ShowdownHighlight, TableVisualEvent.Payout,
        TableVisualEvent.Rebuy,
        TableVisualEvent.ImmediateRebuyStatus,
        TableVisualEvent.DeckChanged, TableVisualEvent.SeatRoster,
        TableVisualEvent.LastHandStatus, TableVisualEvent.HandLimitStatus,
        TableVisualEvent.GameConfigurationStatus,
        TableVisualEvent.RunItTwiceLockStatus,
        TableVisualEvent.CommunicationRulesStatus,
        TableVisualEvent.GameClock,
        TableVisualEvent.CloseTable {

    long sequence();

    /**
     * Real hand-off milestones while the lobby gives ownership to the table.
     * These are semantic engine facts rather than a fabricated percentage.
     */
    record PreparationStatus(long sequence, Phase phase)
            implements TableVisualEvent {

        public PreparationStatus {
            Objects.requireNonNull(phase, "phase");
        }

        public enum Phase {
            STARTING_DEALER,
            DRAWING_SEATS,
            READY
        }
    }

    /**
     * Pause is an independent canonical fact.  It must not be transported in a
     * whole-table snapshot: the neutral aggregate may be sampled between
     * ordered card/action events and would then overwrite newer presentation
     * state with stale cards, pot or turn data.
     */
    record PauseStatus(long sequence, boolean paused) implements TableVisualEvent {
    }

    /** Connection telemetry is likewise a narrow state update, never a table resync. */
    record TelemetryStatus(long sequence, List<PlayerTelemetry> players)
            implements TableVisualEvent {

        public TelemetryStatus {
            players = List.copyOf(players);
            if (players.stream().map(PlayerTelemetry::nickname).distinct().count()
                    != players.size()) {
                throw new IllegalArgumentException(
                        "Telemetry players must have unique nicknames");
            }
        }
    }

    record PlayerTelemetry(String nickname, int latency,
            int previousLatency, int reconnectionCount, long measuredAtMillis) {

        public PlayerTelemetry {
            Objects.requireNonNull(nickname, "nickname");
            if (nickname.isBlank() || reconnectionCount < 0
                    || measuredAtMillis < 0L) {
                throw new IllegalArgumentException("Invalid player telemetry");
            }
        }
    }

    /**
     * Canonical state sampled by the dealer at a hand boundary.  A renderer
     * must consume this snapshot verbatim; it cannot infer resets, cards,
     * contributions, winners, street or pot from the phase.
     */
    record HandBoundary(long sequence, long handId, Phase phase,
            TableSnapshot snapshot) implements TableVisualEvent {

        public HandBoundary {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(snapshot, "snapshot");
            TableSnapshot.Street expected = phase == Phase.PREPARE
                    ? TableSnapshot.Street.PREFLOP
                    : TableSnapshot.Street.SHOWDOWN;
            if (snapshot.street() != expected
                    || !snapshot.currentTurnNickname().isBlank()) {
                throw new IllegalArgumentException(
                        "Hand-boundary snapshot does not match " + phase);
            }
        }

        public enum Phase {
            PREPARE,
            END
        }
    }

    record Shuffle(long sequence, String deck, Phase phase) implements TableVisualEvent {

        public Shuffle {
            Objects.requireNonNull(deck, "deck");
            Objects.requireNonNull(phase, "phase");
        }

        public enum Phase {
            START,
            FINISH
        }
    }

    /** Dealer/SB/BB move in one parallel batch before forced bets are posted. */
    record PositionRotation(long sequence, List<PositionTransfer> transfers,
            long durationMillis) implements TableVisualEvent {

        public PositionRotation {
            transfers = List.copyOf(transfers);
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException("Position rotation needs at least one transfer");
            }
            if (durationMillis <= 0L) {
                throw new IllegalArgumentException("Position rotation duration must be positive");
            }
        }
    }

    record PositionTransfer(String fromNickname, String toNickname,
            TableSnapshot.Position position, boolean fromCenter) {

        public PositionTransfer {
            fromNickname = fromNickname == null ? "" : fromNickname;
            Objects.requireNonNull(toNickname, "toNickname");
            if (toNickname.isBlank()) {
                throw new IllegalArgumentException("Position destination player is required");
            }
            Objects.requireNonNull(position, "position");
        }
    }

    record CollectBets(long sequence, List<ChipTransfer> transfers,
            double potBefore, double potAfterLanding) implements TableVisualEvent {

        public CollectBets {
            transfers = List.copyOf(transfers);
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException("Collect-bets event needs at least one transfer");
            }
            requireMoney(potBefore, "Pot before collection");
            requireMoney(potAfterLanding, "Pot after landing");
            if (potAfterLanding < potBefore) {
                throw new IllegalArgumentException("Pot cannot decrease while collecting bets");
            }
        }
    }

    /**
     * A visual chip flight plus the exact canonical player balance once it
     * lands. Renderers may interpolate the flight, but must never derive poker
     * accounting from {@code amount}.
     */
    record ChipTransfer(String nickname, double amount, double stackAfter,
            double streetBetAfter, double potContributionAfter) {

        public ChipTransfer {
            Objects.requireNonNull(nickname, "nickname");
            if (nickname.isBlank()) {
                throw new IllegalArgumentException(
                        "Chip transfer nickname must not be blank");
            }
            requireMoney(amount, "Chip transfer amount");
            requireMoney(stackAfter, "Stack after chip transfer");
            requireMoney(streetBetAfter, "Street bet after chip transfer");
            requireMoney(potContributionAfter,
                    "Pot contribution after chip transfer");
        }
    }

    record DealHoleCard(long sequence, String nickname, int slot,
            TableSnapshot.CardSnapshot card) implements TableVisualEvent {

        public DealHoleCard {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(card, "card");
            if (slot < 0 || slot > 1) {
                throw new IllegalArgumentException("Hole-card slot must be 0 or 1");
            }
        }
    }

    /** Initial 0-to-buy-in counter curtain before the first hand starts. */
    record InitialStackFill(long sequence, List<ChipTransfer> transfers,
            long durationMillis, String soundResource)
            implements TableVisualEvent {

        public InitialStackFill {
            transfers = List.copyOf(transfers);
            soundResource = soundResource == null ? "" : soundResource;
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Initial stack fill needs at least one player");
            }
            if (durationMillis <= 0L) {
                throw new IllegalArgumentException(
                        "Initial stack fill duration must be positive");
            }
        }
    }

    /** Places one community card face down during the initial deal. */
    record DealCommunityCard(long sequence, int slot) implements TableVisualEvent {

        public DealCommunityCard {
            if (slot < 0 || slot > 4) {
                throw new IllegalArgumentException("Community-card slot must be 0..4");
            }
        }
    }

    /**
     * Selects one run-it-twice board and, for side B, removes the run-out
     * community cards before their normal face-down deal events arrive.
     * The dealer remains the sole owner of the vote, cards and pot split.
     */
    record RunItTwiceBoard(long sequence, Side side, String potPrefix,
            double potAmount, List<Integer> redealSlots)
            implements TableVisualEvent {

        public RunItTwiceBoard {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(potPrefix, "potPrefix");
            redealSlots = List.copyOf(redealSlots);
            requireMoney(potAmount, "Run-it-twice pot amount");
            if (potPrefix.isBlank()) {
                throw new IllegalArgumentException(
                        "Run-it-twice pot prefix is required");
            }
            if (redealSlots.stream().anyMatch(slot -> slot == null
                    || slot < 0 || slot > 4)
                    || redealSlots.stream().distinct().count()
                    != redealSlots.size()) {
                throw new IllegalArgumentException(
                        "Run-it-twice redeal slots must be unique slots 0..4");
            }
            if (side == Side.A && !redealSlots.isEmpty()) {
                throw new IllegalArgumentException(
                        "Run-it-twice side A cannot redeal community cards");
            }
        }

        public enum Side {
            A,
            B
        }
    }

    /**
     * Orders the two local cards visually.
     *
     * @param blocking {@code true} only when play really depends on seeing the
     * cards first (the deferred straddle reveal); ordinary post-deal sorting is
     * cosmetic and must never hold the dealer thread.
     */
    record SwapHoleCards(long sequence, String nickname, boolean blocking)
            implements TableVisualEvent {

        public SwapHoleCards {
            Objects.requireNonNull(nickname, "nickname");
        }
    }

    record FoldHoleCards(long sequence, String nickname) implements TableVisualEvent {

        public FoldHoleCards {
            Objects.requireNonNull(nickname, "nickname");
        }
    }

    /** One semantic reveal. The flop is emitted as one event containing three cards. */
    record RevealCommunityCards(long sequence, TableSnapshot.Street street,
            int firstSlot, List<TableSnapshot.CardSnapshot> cards,
            long leadInMillis)
            implements TableVisualEvent {

        public RevealCommunityCards(long sequence, int firstSlot,
                List<TableSnapshot.CardSnapshot> cards) {
            this(sequence, revealStreet(firstSlot), firstSlot, cards, 0L);
        }

        public RevealCommunityCards(long sequence, int firstSlot,
                List<TableSnapshot.CardSnapshot> cards, long leadInMillis) {
            this(sequence, revealStreet(firstSlot), firstSlot, cards,
                    leadInMillis);
        }

        public RevealCommunityCards {
            Objects.requireNonNull(street, "street");
            cards = List.copyOf(cards);
            if (firstSlot < 0 || firstSlot > 4 || cards.isEmpty()
                    || firstSlot + cards.size() > 5) {
                throw new IllegalArgumentException("Community-card reveal must fit slots 0..4");
            }
            if (cards.size() != 1 && !(firstSlot == 0 && cards.size() == 3)) {
                throw new IllegalArgumentException("Only a grouped flop or one turn/river card is valid");
            }
            if (leadInMillis < 0L) {
                throw new IllegalArgumentException(
                        "Community-card reveal lead-in cannot be negative");
            }
            if (street != revealStreet(firstSlot)) {
                throw new IllegalArgumentException(
                        "Community-card street does not match its first slot");
            }
        }

        private static TableSnapshot.Street revealStreet(int firstSlot) {
            return switch (firstSlot) {
                case 0 -> TableSnapshot.Street.FLOP;
                case 3 -> TableSnapshot.Street.TURN;
                case 4 -> TableSnapshot.Street.RIVER;
                default -> throw new IllegalArgumentException(
                        "Unsupported community reveal slot: " + firstSlot);
            };
        }
    }

    record TurnTimer(long sequence, String nickname, long totalMillis,
            long remainingMillis, Phase phase) implements TableVisualEvent {

        public TurnTimer {
            nickname = nickname == null ? "" : nickname;
            Objects.requireNonNull(phase, "phase");
            if (totalMillis < 0 || remainingMillis < 0 || remainingMillis > totalMillis) {
                throw new IllegalArgumentException("Invalid turn timer duration");
            }
            if (phase == Phase.START && nickname.isBlank()) {
                throw new IllegalArgumentException("A started turn needs a player");
            }
        }

        public enum Phase {
            START,
            UPDATE,
            STOP
        }
    }

    /** Values shown by the central community-card HUD. */
    record TableInfo(long sequence, double smallBlind, double bigBlind,
            int handNumber) implements TableVisualEvent {

        public TableInfo {
            requireMoney(smallBlind, "Small blind");
            requireMoney(bigBlind, "Big blind");
            if (handNumber < 0) {
                throw new IllegalArgumentException(
                        "Hand number must be non-negative");
            }
        }
    }

    /**
     * Renderer-neutral state of Swing's shared community progress bar. This is
     * deliberately separate from {@link TurnTimer}: during showdown there is
     * no player turn, but the canonical dealer still owns a visible countdown.
     */
    record SharedProgress(long sequence, Mode mode, int seconds)
            implements TableVisualEvent {

        public SharedProgress {
            Objects.requireNonNull(mode, "mode");
            if (seconds < 0 || (mode == Mode.COUNTDOWN && seconds == 0)) {
                throw new IllegalArgumentException(
                        "Invalid shared progress duration");
            }
        }

        public enum Mode {
            COUNTDOWN,
            INDETERMINATE,
            RESET
        }
    }

    /** Amount the local player must match, anchored to the current aggressor on the river. */
    record CallCost(long sequence, String text,
            String aggressorNickname) implements TableVisualEvent {

        public CallCost {
            text = text == null ? "" : text;
            aggressorNickname = aggressorNickname == null ? "" : aggressorNickname;
        }
    }

    record ActionControls(long sequence,
            com.tonikelope.coronapoker.core.game.ActionControlState state)
            implements TableVisualEvent {

        public ActionControls {
            Objects.requireNonNull(state, "state");
        }
    }

    /** Availability of Swing's two out-of-turn AUTO actions for the local player. */
    record PreActionControls(long sequence, boolean active,
            boolean clearSelection) implements TableVisualEvent {
    }

    record PlayerAction(long sequence, String nickname, ActionKind kind,
            String label, double amount, double contributionDelta,
            double stackAfter, double streetBetAfter,
            double potContributionAfter) implements TableVisualEvent {

        public PlayerAction {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            requireMoney(amount, "Action amount");
            requireMoney(contributionDelta, "Action contribution delta");
            requireMoney(stackAfter, "Stack after action");
            requireMoney(streetBetAfter, "Street bet after action");
            requireMoney(potContributionAfter,
                    "Pot contribution after action");
        }

        public enum ActionKind {
            WAITING,
            FOLD,
            CHECK,
            CALL,
            BET,
            RAISE,
            RERAISE,
            ALL_IN,
            SMALL_BLIND,
            BIG_BLIND,
            STRADDLE
        }
    }

    record Cinematic(long sequence, Type type, Phase phase, String nickname,
            String assetName, long durationMillis) implements TableVisualEvent {

        public Cinematic {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(assetName, "assetName");
            if (nickname.isBlank() || assetName.isBlank()
                    || durationMillis < 0L) {
                throw new IllegalArgumentException(
                        "Cinematic actor, asset and duration must be valid");
            }
        }

        public enum Type {
            ALL_IN
        }

        public enum Phase {
            START,
            FINISH
        }
    }

    record RevealHoleCards(long sequence, String nickname,
            TableSnapshot.CardSnapshot left,
            TableSnapshot.CardSnapshot right,
            String handName) implements TableVisualEvent {

        public RevealHoleCards(long sequence, String nickname,
                TableSnapshot.CardSnapshot left,
                TableSnapshot.CardSnapshot right) {
            this(sequence, nickname, left, right, "");
        }

        public RevealHoleCards {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            handName = handName == null ? "" : handName;
        }
    }

    /**
     * Current hand and Monte Carlo win probability shown while an all-in board
     * is still incomplete. A negative percentage is the canonical pre-compute
     * placeholder used by Swing ({@code --%}); otherwise the value is in
     * {@code [0,100]}.
     */
    record PartialHand(long sequence, String nickname, String handName,
            boolean winner, float winPercentage) implements TableVisualEvent {

        public PartialHand {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(handName, "handName");
            if (nickname.isBlank() || handName.isBlank()
                    || !Float.isFinite(winPercentage)
                    || winPercentage < 0f && Float.compare(winPercentage, -1f) != 0
                    || winPercentage > 100f) {
                throw new IllegalArgumentException("Invalid partial hand");
            }
        }
    }

    /** Exact per-player showdown verdict emitted by the canonical dealer. */
    record HandResult(long sequence, String nickname, String handName,
            boolean winner, TableSnapshot.Street street)
            implements TableVisualEvent {

        public HandResult {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(handName, "handName");
            Objects.requireNonNull(street, "street");
            if (street != TableSnapshot.Street.SHOWDOWN) {
                throw new IllegalArgumentException(
                        "Hand result must carry the canonical showdown street");
            }
        }
    }

    record ShowdownHighlight(long sequence, String nickname, boolean enabled,
            List<Integer> holeCardSlots, List<Integer> communityCardSlots)
            implements TableVisualEvent {

        public ShowdownHighlight {
            Objects.requireNonNull(nickname, "nickname");
            holeCardSlots = checkedSlots(holeCardSlots, 2, "hole-card");
            communityCardSlots = checkedSlots(communityCardSlots, 5, "community-card");
        }
    }

    record Payout(long sequence, String nickname, double amount,
            int potIndex, double stackAfter, double potAfter) implements TableVisualEvent {

        public Payout {
            Objects.requireNonNull(nickname, "nickname");
            requireMoney(amount, "Payout amount");
            requireMoney(stackAfter, "Stack after payout");
            requireMoney(potAfter, "Pot after payout");
            if (potIndex < 0) {
                throw new IllegalArgumentException("Pot index cannot be negative");
            }
        }
    }

    /**
     * Audio requested by the canonical dealer and rendered by the active
     * frontend. Resource names retain the classic {@code sounds/} relative
     * contract, allowing a frontend to resolve an installed mod before the
     * bundled fallback without exposing filesystem paths to game logic.
     */
    record AudioCue(long sequence, Operation operation, String resource,
            boolean waitForCompletion, boolean forceClose,
            boolean bypassMuted, boolean forceSilent)
            implements TableVisualEvent {

        public AudioCue {
            Objects.requireNonNull(operation, "operation");
            resource = resource == null ? "" : resource.replace('\\', '/');
            if (operation.requiresResource() && resource.isBlank()) {
                throw new IllegalArgumentException(
                        "Audio operation requires a resource");
            }
            if (resource.startsWith("/") || resource.contains("../")) {
                throw new IllegalArgumentException("Unsafe audio resource");
            }
        }

        public enum Operation {
            PLAY,
            STOP,
            PLAY_LOOP,
            STOP_LOOP,
            START_DANGER_LOOP,
            STOP_DANGER_LOOP,
            MUTE_LOOPS,
            UNMUTE_LOOPS;

            public boolean requiresResource() {
                return this != STOP_DANGER_LOOP
                        && this != MUTE_LOOPS
                        && this != UNMUTE_LOOPS;
            }
        }
    }

    /**
     * Requests the optional easter-egg sound attached to one revealed card in
     * the active deck. The renderer resolves it against the external mod; the
     * game flow deliberately carries only the stable card code.
     */
    record SpecialCardSound(long sequence, String cardCode)
            implements TableVisualEvent {

        public SpecialCardSound {
            cardCode = cardCode == null ? "" : cardCode.trim();
            if (!cardCode.matches("(?:A|[2-9]|10|J|Q|K)_[PCTD]")) {
                throw new IllegalArgumentException(
                        "Invalid special-sound card code: " + cardCode);
            }
        }
    }

    /** Chips entering the table from the lower centre for accepted rebuys. */
    record Rebuy(long sequence, List<ChipTransfer> transfers,
            long durationMillis) implements TableVisualEvent {

        public Rebuy {
            transfers = List.copyOf(transfers);
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Rebuy event needs at least one transfer");
            }
            if (transfers.stream().anyMatch(transfer -> transfer.amount() <= 0d)) {
                throw new IllegalArgumentException(
                        "Rebuy transfers must be positive");
            }
            if (durationMillis <= 0L) {
                throw new IllegalArgumentException(
                        "Rebuy duration must be positive");
            }
        }
    }

    /** Canonical state of the next-hand rebuy toggle for one player. */
    record ImmediateRebuyStatus(long sequence, String nickname, int amount)
            implements TableVisualEvent {

        public ImmediateRebuyStatus {
            Objects.requireNonNull(nickname, "nickname");
            if (nickname.isBlank()) {
                throw new IllegalArgumentException(
                        "Immediate-rebuy nickname must not be blank");
            }
            if (amount < 0) {
                throw new IllegalArgumentException(
                        "Immediate-rebuy amount must not be negative");
            }
        }

        public boolean enabled() {
            return amount > 0;
        }
    }

    record DeckChanged(long sequence, String deck) implements TableVisualEvent {

        public DeckChanged {
            Objects.requireNonNull(deck, "deck");
        }
    }

    /** Exact player-controller state for a roster change; never a hand resync. */
    record SeatRoster(long sequence, List<TableSnapshot.PlayerSnapshot> players)
            implements TableVisualEvent {

        public SeatRoster {
            players = List.copyOf(players);
            if (players.stream().map(TableSnapshot.PlayerSnapshot::nickname)
                    .distinct().count() != players.size()) {
                throw new IllegalArgumentException(
                        "Roster players must have unique nicknames");
            }
        }
    }

    /** Authoritative host state shown by every attached table renderer. */
    record LastHandStatus(long sequence, boolean enabled)
            implements TableVisualEvent {
    }

    /** Maximum table hand count; {@code -1} means unlimited. */
    record HandLimitStatus(long sequence, int maximumHands)
            implements TableVisualEvent {

        public HandLimitStatus {
            if (maximumHands != -1 && maximumHands < 1) {
                throw new IllegalArgumentException(
                        "maximumHands must be -1 or positive");
            }
        }
    }

    /** Complete authoritative table configuration after a live host update. */
    record GameConfigurationStatus(long sequence,
            GameConfigCodecV1.Configuration configuration)
            implements TableVisualEvent {

        public GameConfigurationStatus {
            configuration = GameConfigCodecV1.requireValid(configuration);
        }
    }

    /** Whether the host may still alter Run It Twice for this hand. */
    record RunItTwiceLockStatus(long sequence, boolean locked)
            implements TableVisualEvent {
    }

    /** Global host-owned chat rules reflected by every table renderer. */
    record CommunicationRulesStatus(long sequence, boolean textToSpeech,
            boolean voiceMessages) implements TableVisualEvent {
    }

    /** Renderer-neutral elapsed table time, excluding canonical pauses. */
    record GameClock(long sequence, long playTimeSeconds)
            implements TableVisualEvent {

        public GameClock {
            if (playTimeSeconds < 0L) {
                throw new IllegalArgumentException(
                        "playTimeSeconds must be non-negative");
            }
        }
    }

    /** Terminal transition after the shared game session has finished. */
    record CloseTable(long sequence, TableSessionSummary summary,
            TableSnapshot.Street terminalStreet)
            implements TableVisualEvent {

        public CloseTable {
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(terminalStreet, "terminalStreet");
            if (terminalStreet != TableSnapshot.Street.FINISHED) {
                throw new IllegalArgumentException(
                        "Close-table street must be terminal");
            }
        }

        public CloseTable(long sequence) {
            this(sequence, TableSessionSummary.empty(),
                    TableSnapshot.Street.FINISHED);
        }
    }

    private static void requireMoney(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static List<Integer> checkedSlots(List<Integer> slots, int limit, String name) {
        List<Integer> copy = List.copyOf(slots);
        for (Integer slot : copy) {
            if (slot == null || slot < 0 || slot >= limit) {
                throw new IllegalArgumentException("Invalid " + name + " slot");
            }
        }
        return copy;
    }
}
