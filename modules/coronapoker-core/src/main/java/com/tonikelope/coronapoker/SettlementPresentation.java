package com.tonikelope.coronapoker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the immutable winner/loser view consumed by showdown presentation. */
public final class SettlementPresentation {

    private SettlementPresentation() {
    }

    public static <P, H> Plan<P, H> plan(Map<P, H> mainHands, Map<P, H> mainWinners,
            List<? extends Map<P, H>> sideHands, List<? extends Map<P, H>> sideWinners) {
        Objects.requireNonNull(mainHands);
        Objects.requireNonNull(mainWinners);
        Objects.requireNonNull(sideHands);
        Objects.requireNonNull(sideWinners);
        if (sideHands.size() != sideWinners.size()) {
            throw new IllegalArgumentException("side-pot hands and winners must describe the same pots");
        }

        LinkedHashMap<P, H> allHands = new LinkedHashMap<>(mainHands);
        LinkedHashMap<P, H> allWinners = new LinkedHashMap<>(mainWinners);
        for (int i = 0; i < sideHands.size(); i++) {
            allHands.putAll(sideHands.get(i));
            allWinners.putAll(sideWinners.get(i));
        }
        allHands.keySet().removeAll(allWinners.keySet());
        return new Plan<>(allWinners, allHands);
    }

    public static final class Plan<P, H> {

        private final Map<P, H> winners;
        private final Map<P, H> losers;

        private Plan(Map<P, H> winners, Map<P, H> losers) {
            this.winners = Collections.unmodifiableMap(new LinkedHashMap<>(winners));
            this.losers = Collections.unmodifiableMap(new LinkedHashMap<>(losers));
        }

        public Map<P, H> winners() {
            return winners;
        }

        public Map<P, H> losers() {
            return losers;
        }
    }
}
