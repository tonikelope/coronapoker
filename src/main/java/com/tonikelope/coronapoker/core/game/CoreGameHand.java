/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical five-card hand evaluation over renderer-independent cards. */
public final class CoreGameHand implements GameHandResult {

    private static final int HAND_SIZE = 5;
    private static final String[] HAND_KEYS = {
        "hand.high_card", "hand.one_pair", "hand.two_pair",
        "hand.three_of_a_kind", "hand.straight", "hand.flush",
        "hand.full_house", "hand.four_of_a_kind",
        "hand.straight_flush", "hand.royal_flush"
    };

    private final List<GameCardController> usable;
    private final List<GameCardController> winners;
    private final List<GameCardController> hand;
    private final int value;
    private final String name;
    private volatile double strength;

    public CoreGameHand(List<? extends GameCardController> cards, GameText text) {
        Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(text, "text");
        if (cards.isEmpty()) {
            usable = List.of();
            winners = List.of();
            hand = List.of();
            value = -1;
            name = null;
            return;
        }
        usable = List.copyOf(cards);
        Evaluation evaluation = evaluate(new ArrayList<>(usable));
        value = evaluation.value();
        name = text.translate(HAND_KEYS[value - 1]);
        winners = List.copyOf(evaluation.winners());
        ArrayList<GameCardController> complete = new ArrayList<>(winners);
        complete.addAll(evaluation.kickers());
        hand = List.copyOf(complete);
    }

    @Override public double getFuerza() { return strength; }
    @Override public void setFuerza(double value) { strength = value; }
    @Override public int getValue() { return value; }
    @Override public String getName() { return name; }
    @Override public List<GameCardController> getWinners() { return winners; }
    @Override public List<GameCardController> getMano() { return hand; }

    @Override
    public String toString() {
        return name + " " + GameCards.displayCollection(winners);
    }

    private static Evaluation evaluate(ArrayList<GameCardController> cards) {
        ArrayList<GameCardController> made = royalFlush(cards);
        if (made != null) return result(ESCALERA_COLOR_REAL, made, null);
        made = straightFlush(cards);
        if (made != null) return result(ESCALERA_COLOR, made, null);
        made = repeated(cards, 4);
        if (made != null) return result(POKER, made, kickers(cards, made));
        made = fullHouse(cards);
        if (made != null) return result(FULL, made, null);
        made = flush(cards);
        if (made != null) return result(COLOR, made, null);
        made = straight(cards);
        if (made != null) return result(ESCALERA, made, null);
        made = repeated(cards, 3);
        if (made != null) return result(TRIO, made, kickers(cards, made));
        made = twoPair(cards);
        if (made != null) return result(DOBLE_PAREJA, made, kickers(cards, made));
        made = repeated(cards, 2);
        if (made != null) return result(PAREJA, made, kickers(cards, made));
        return result(CARTA_ALTA, highest(cards, HAND_SIZE), null);
    }

    private static Evaluation result(int value,
            List<GameCardController> winners,
            List<GameCardController> kickers) {
        return new Evaluation(value, winners,
                kickers == null ? List.of() : kickers);
    }

    private static ArrayList<GameCardController> kickers(
            List<GameCardController> cards, List<GameCardController> made) {
        ArrayList<GameCardController> remaining = new ArrayList<>(cards);
        remaining.removeAll(made);
        return remaining.isEmpty() ? null
                : highest(remaining, HAND_SIZE - made.size());
    }

    private static ArrayList<GameCardController> highest(
            List<GameCardController> cards, int size) {
        if (cards == null || cards.isEmpty() || size == 0) return null;
        ArrayList<GameCardController> result = new ArrayList<>(cards);
        sort(result, false);
        if (result.size() > size) result.subList(size, result.size()).clear();
        return result;
    }

    private static ArrayList<GameCardController> repeated(
            List<GameCardController> cards, int size) {
        if (cards == null || cards.size() < size || size < 2) return null;
        ArrayList<GameCardController> sorted = new ArrayList<>(cards);
        sort(sorted, false);
        ArrayList<GameCardController> run = new ArrayList<>();
        GameCardController pivot = sorted.get(0);
        run.add(pivot);
        for (int i = 1; i < sorted.size() && run.size() < size; i++) {
            GameCardController card = sorted.get(i);
            if (pivot.getValorNumerico() == card.getValorNumerico()) {
                run.add(card);
            } else {
                pivot = card;
                run.clear();
                run.add(card);
            }
        }
        return run.size() == size ? run : null;
    }

    private static ArrayList<GameCardController> consecutive(
            List<GameCardController> cards, boolean aceLow) {
        if (cards == null || cards.size() < HAND_SIZE) return null;
        ArrayList<GameCardController> sorted = new ArrayList<>(cards);
        sort(sorted, aceLow);
        ArrayList<GameCardController> run = new ArrayList<>();
        GameCardController pivot = sorted.get(0);
        run.add(pivot);
        int last = pivot.getValorNumerico(aceLow);
        int i = 1;
        while (run.size() < HAND_SIZE && i < sorted.size()) {
            while (i < sorted.size()
                    && last == sorted.get(i).getValorNumerico(aceLow)) i++;
            if (i < sorted.size()) {
                GameCardController card = sorted.get(i);
                if (pivot.getValorNumerico(aceLow)
                        - card.getValorNumerico(aceLow) == run.size()) {
                    run.add(card);
                    last = card.getValorNumerico(aceLow);
                } else {
                    pivot = card;
                    run.clear();
                    run.add(card);
                    last = card.getValorNumerico(aceLow);
                }
                i++;
            }
        }
        return run.size() == HAND_SIZE ? run : null;
    }

    private static ArrayList<GameCardController> sameSuit(
            List<GameCardController> cards) {
        if (cards == null || cards.size() < HAND_SIZE) return null;
        Map<String, ArrayList<GameCardController>> suits = new HashMap<>();
        suits.put("P", new ArrayList<>());
        suits.put("D", new ArrayList<>());
        suits.put("T", new ArrayList<>());
        suits.put("C", new ArrayList<>());
        ArrayList<GameCardController> largest = new ArrayList<>();
        for (GameCardController card : cards) {
            ArrayList<GameCardController> suit = suits.get(card.getPalo());
            if (suit == null) continue;
            suit.add(card);
            if (suit.size() > largest.size()) largest = suit;
        }
        sort(largest, false);
        return largest.size() >= HAND_SIZE ? largest : null;
    }

    private static ArrayList<GameCardController> fullHouse(
            List<GameCardController> cards) {
        ArrayList<GameCardController> remaining = new ArrayList<>(cards);
        ArrayList<GameCardController> trips = repeated(remaining, 3);
        if (trips == null) return null;
        remaining.removeAll(trips);
        ArrayList<GameCardController> pair = repeated(remaining, 2);
        if (pair == null) return null;
        trips.addAll(pair);
        return trips;
    }

    private static ArrayList<GameCardController> twoPair(
            List<GameCardController> cards) {
        ArrayList<GameCardController> remaining = new ArrayList<>(cards);
        if (repeated(remaining, 4) != null) return null;
        ArrayList<GameCardController> first = repeated(remaining, 2);
        if (first == null) return null;
        remaining.removeAll(first);
        ArrayList<GameCardController> second = repeated(remaining, 2);
        if (second == null) return null;
        first.addAll(second);
        return first;
    }

    private static ArrayList<GameCardController> straight(
            List<GameCardController> cards) {
        ArrayList<GameCardController> high = consecutive(cards, false);
        return high != null ? high : consecutive(cards, true);
    }

    private static ArrayList<GameCardController> straightFlush(
            List<GameCardController> cards) {
        return consecutive(sameSuit(cards), true);
    }

    private static ArrayList<GameCardController> royalFlush(
            List<GameCardController> cards) {
        ArrayList<GameCardController> result = consecutive(sameSuit(cards), false);
        return result != null && "A".equals(result.get(0).getValor())
                ? result : null;
    }

    private static ArrayList<GameCardController> flush(
            List<GameCardController> cards) {
        ArrayList<GameCardController> result = sameSuit(cards);
        if (result == null) return null;
        if (result.size() > HAND_SIZE) result.subList(HAND_SIZE, result.size()).clear();
        return result;
    }

    private static void sort(List<GameCardController> cards, boolean aceLow) {
        cards.sort(Comparator.comparingInt(
                (GameCardController card) -> card.getValorNumerico(aceLow))
                .reversed());
    }

    private record Evaluation(int value, List<GameCardController> winners,
            List<GameCardController> kickers) { }
}
