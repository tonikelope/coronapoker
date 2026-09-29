package com.tonikelope.coronapoker;

import java.util.ArrayList;
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
        LinkedHashMap<P, List<Integer>> wonPotIndexes = new LinkedHashMap<>();
        if (!sideHands.isEmpty()) {
            for (P winner : mainWinners.keySet()) {
                addWonPot(wonPotIndexes, winner, 1);
            }
        }
        for (int i = 0; i < sideHands.size(); i++) {
            allHands.putAll(sideHands.get(i));
            allWinners.putAll(sideWinners.get(i));
            for (P winner : sideWinners.get(i).keySet()) {
                addWonPot(wonPotIndexes, winner, i + 2);
            }
        }
        allHands.keySet().removeAll(allWinners.keySet());
        return new Plan<>(allWinners, allHands, wonPotIndexes);
    }

    private static <P> void addWonPot(Map<P, List<Integer>> wonPotIndexes,
            P winner, int potIndex) {
        wonPotIndexes.computeIfAbsent(winner,
                ignored -> new ArrayList<>()).add(potIndex);
    }

    public static final class Plan<P, H> {

        private final Map<P, H> winners;
        private final Map<P, H> losers;
        private final Map<P, List<Integer>> wonPotIndexes;

        private Plan(Map<P, H> winners, Map<P, H> losers,
                Map<P, List<Integer>> wonPotIndexes) {
            this.winners = Collections.unmodifiableMap(new LinkedHashMap<>(winners));
            this.losers = Collections.unmodifiableMap(new LinkedHashMap<>(losers));
            LinkedHashMap<P, List<Integer>> indexes = new LinkedHashMap<>();
            wonPotIndexes.forEach((winner, pots)
                    -> indexes.put(winner, List.copyOf(pots)));
            this.wonPotIndexes = Collections.unmodifiableMap(indexes);
        }

        public Map<P, H> winners() {
            return winners;
        }

        public Map<P, H> losers() {
            return losers;
        }

        /**
         * One-based indexes of every contested pot won by each player. The map
         * is empty for an ordinary single-pot hand, where numbering would add
         * noise to the result HUD.
         */
        public Map<P, List<Integer>> wonPotIndexes() {
            return wonPotIndexes;
        }
    }
}
