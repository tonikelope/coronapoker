package com.tonikelope.coronapoker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable authoritative reducer for per-seat betting and raise entitlement. */
public final class BettingRoundState {
    public enum Action {
        CHECK_CALL, FOLD, RAISE, ALL_IN
    }

    public enum Error {
        UNKNOWN_SEAT, INACTIVE_SEAT, INVALID_AMOUNT, ILLEGAL_RAISE
    }

    public static final class LegalActions {
        private final boolean canCheck;
        private final boolean canCall;
        private final boolean canRaise;
        private final boolean canFold;

        private LegalActions(boolean canCheck, boolean canCall, boolean canRaise, boolean canFold) {
            this.canCheck = canCheck;
            this.canCall = canCall;
            this.canRaise = canRaise;
            this.canFold = canFold;
        }

        public boolean canCheck() {
            return canCheck;
        }

        public boolean canCall() {
            return canCall;
        }

        public boolean canRaise() {
            return canRaise;
        }

        public boolean canFold() {
            return canFold;
        }
    }

    public static final class Transition {
        private final BettingRoundState state;
        private final Error error;
        private final boolean fullRaise;

        private Transition(BettingRoundState state, Error error, boolean fullRaise) {
            this.state = state;
            this.error = error;
            this.fullRaise = fullRaise;
        }

        public boolean isAccepted() {
            return state != null;
        }

        public BettingRoundState state() {
            return state;
        }

        public Error error() {
            return error;
        }

        public boolean isFullRaise() {
            return fullRaise;
        }
    }

    private static final class SeatState {
        private final long committedCents;
        private final Long actedFacingCents;
        private final boolean folded;
        private final boolean allIn;
        private final boolean raiseEntitled;

        private SeatState(long committedCents, Long actedFacingCents,
                boolean folded, boolean allIn, boolean raiseEntitled) {
            this.committedCents = committedCents;
            this.actedFacingCents = actedFacingCents;
            this.folded = folded;
            this.allIn = allIn;
            this.raiseEntitled = raiseEntitled;
        }

        private SeatState with(long committed, Long actedFacing,
                boolean isFolded, boolean isAllIn, boolean entitled) {
            return new SeatState(committed, actedFacing, isFolded, isAllIn, entitled);
        }
    }

    private final long toCallCents;
    private final long lastFullRaiseSizeCents;
    private final long fullRaiseGeneration;
    private final Map<String, SeatState> seats;

    private BettingRoundState(long toCallCents, long lastFullRaiseSizeCents,
            long fullRaiseGeneration, Map<String, SeatState> seats) {
        this.toCallCents = toCallCents;
        this.lastFullRaiseSizeCents = lastFullRaiseSizeCents;
        this.fullRaiseGeneration = fullRaiseGeneration;
        this.seats = Collections.unmodifiableMap(new LinkedHashMap<>(seats));
    }

    public static BettingRoundState start(Map<String, Long> committedBySeat,
            long toCallCents, long lastFullRaiseSizeCents) {
        if (committedBySeat == null || committedBySeat.isEmpty()
                || toCallCents < 0 || lastFullRaiseSizeCents <= 0) {
            throw new IllegalArgumentException("invalid betting round genesis");
        }
        LinkedHashMap<String, SeatState> seats = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : committedBySeat.entrySet()) {
            String seat = entry.getKey();
            Long committed = entry.getValue();
            if (seat == null || seat.isEmpty() || committed == null
                    || committed < 0 || committed > toCallCents || seats.containsKey(seat)) {
                throw new IllegalArgumentException("invalid betting seat");
            }
            seats.put(seat, new SeatState(committed, null, false, false, true));
        }
        return new BettingRoundState(toCallCents, lastFullRaiseSizeCents, 0L, seats);
    }

    public LegalActions legalActions(String seat) {
        SeatState current = seats.get(seat);
        if (current == null || current.folded || current.allIn) {
            return new LegalActions(false, false, false, false);
        }
        return new LegalActions(current.committedCents == toCallCents,
                current.committedCents < toCallCents,
                current.raiseEntitled, true);
    }

    public Transition apply(String seat, Action action, long committedTotalCents) {
        SeatState actor = seats.get(seat);
        if (actor == null) {
            return rejected(Error.UNKNOWN_SEAT);
        }
        if (actor.folded || actor.allIn) {
            return rejected(Error.INACTIVE_SEAT);
        }
        if (action == null || committedTotalCents < actor.committedCents) {
            return rejected(Error.INVALID_AMOUNT);
        }

        LinkedHashMap<String, SeatState> next = new LinkedHashMap<>(seats);
        switch (action) {
            case FOLD:
                if (committedTotalCents != actor.committedCents) {
                    return rejected(Error.INVALID_AMOUNT);
                }
                next.put(seat, actor.with(actor.committedCents, toCallCents, true, false, false));
                return accepted(new BettingRoundState(toCallCents, lastFullRaiseSizeCents,
                        fullRaiseGeneration, next), false);
            case CHECK_CALL:
                if (committedTotalCents != toCallCents) {
                    return rejected(Error.INVALID_AMOUNT);
                }
                next.put(seat, actor.with(committedTotalCents, toCallCents, false, false, false));
                return accepted(new BettingRoundState(toCallCents, lastFullRaiseSizeCents,
                        fullRaiseGeneration, next), false);
            case RAISE:
                if (!actor.raiseEntitled || committedTotalCents <= toCallCents
                        || committedTotalCents - toCallCents < lastFullRaiseSizeCents) {
                    return rejected(Error.ILLEGAL_RAISE);
                }
                return fullRaise(seat, actor, committedTotalCents, false, next);
            case ALL_IN:
                if (committedTotalCents <= actor.committedCents) {
                    return rejected(Error.INVALID_AMOUNT);
                }
                if (committedTotalCents <= toCallCents) {
                    next.put(seat, actor.with(committedTotalCents, toCallCents, false, true, false));
                    return accepted(new BettingRoundState(toCallCents, lastFullRaiseSizeCents,
                            fullRaiseGeneration, next), false);
                }
                if (!actor.raiseEntitled) {
                    return rejected(Error.ILLEGAL_RAISE);
                }
                if (committedTotalCents - toCallCents >= lastFullRaiseSizeCents) {
                    return fullRaise(seat, actor, committedTotalCents, true, next);
                }
                next.put(seat, actor.with(committedTotalCents, committedTotalCents, false, true, false));
                for (Map.Entry<String, SeatState> entry : next.entrySet()) {
                    if (entry.getKey().equals(seat)) {
                        continue;
                    }
                    SeatState other = entry.getValue();
                    if (!other.folded && !other.allIn) {
                        boolean cumulativeReopen = other.actedFacingCents != null
                                && committedTotalCents - other.actedFacingCents >= lastFullRaiseSizeCents;
                        entry.setValue(other.with(other.committedCents, other.actedFacingCents,
                                false, false, other.raiseEntitled || cumulativeReopen));
                    }
                }
                return accepted(new BettingRoundState(committedTotalCents, lastFullRaiseSizeCents,
                        fullRaiseGeneration, next), false);
            default:
                return rejected(Error.INVALID_AMOUNT);
        }
    }

    private Transition fullRaise(String seat, SeatState actor, long target,
            boolean allIn, LinkedHashMap<String, SeatState> next) {
        long increment = target - toCallCents;
        for (Map.Entry<String, SeatState> entry : next.entrySet()) {
            SeatState current = entry.getValue();
            boolean isActor = entry.getKey().equals(seat);
            if (isActor) {
                entry.setValue(actor.with(target, target, false, allIn, false));
            } else if (!current.folded && !current.allIn) {
                entry.setValue(current.with(current.committedCents, current.actedFacingCents,
                        false, false, true));
            }
        }
        return accepted(new BettingRoundState(target, increment,
                fullRaiseGeneration + 1L, next), true);
    }

    public long toCallCents() {
        return toCallCents;
    }

    public long lastFullRaiseSizeCents() {
        return lastFullRaiseSizeCents;
    }

    public long fullRaiseGeneration() {
        return fullRaiseGeneration;
    }

    private static Transition accepted(BettingRoundState state, boolean fullRaise) {
        return new Transition(state, null, fullRaise);
    }

    private static Transition rejected(Error error) {
        return new Transition(null, error, false);
    }
}
