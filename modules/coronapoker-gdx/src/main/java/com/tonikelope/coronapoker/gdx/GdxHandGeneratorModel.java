/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.core.game.CardCode.Rank;
import com.tonikelope.coronapoker.core.game.CardCode.Suit;
import com.tonikelope.coronapoker.core.game.CoreCardController;
import com.tonikelope.coronapoker.core.game.CoreGameHand;
import com.tonikelope.coronapoker.core.game.GameCardController;
import com.tonikelope.coronapoker.core.game.GameText;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Native-GDX presentation model for the classic hand generator.
 *
 * <p>The ordering and odds are deliberately the same as
 * {@code HandGeneratorDialog}. Each navigation generates a fresh valid example,
 * just as Swing did. The number of visible cards also matches Swing:
 * pair/trips/two-pair/quads show only the cards that define that rank.</p>
 */
final class GdxHandGeneratorModel {

    static final String ROBERT_RULES_URL
            = "https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf";
    static final String POKER_ODDS_URL
            = "https://brilliant.org/wiki/math-of-poker/";

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("hand.high_card", "4,74:1"),
            new Definition("hand.one_pair", "1,28:1"),
            new Definition("hand.two_pair", "3,26:1"),
            new Definition("hand.three_of_a_kind", "19,7:1"),
            new Definition("hand.straight", "20,65:1"),
            new Definition("hand.flush", "32,05:1"),
            new Definition("hand.full_house", "37,52:1"),
            new Definition("hand.four_of_a_kind", "594:1"),
            new Definition("hand.straight_flush", "3589,57:1"),
            new Definition("hand.royal_flush", "30939:1")
    );
    private static final List<List<Rank>> NON_ROYAL_STRAIGHTS = List.of(
            List.of(Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE),
            List.of(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX),
            List.of(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN),
            List.of(Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT),
            List.of(Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE),
            List.of(Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN),
            List.of(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK),
            List.of(Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN),
            List.of(Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING));
    private static final List<Rank> ROYAL = List.of(
            Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE);

    private final Random random;
    private int index;
    private Example current;

    GdxHandGeneratorModel() {
        this(DEFINITIONS.size() - 1, new SecureRandom());
    }

    GdxHandGeneratorModel(int initialIndex) {
        this(initialIndex, new SecureRandom());
    }

    GdxHandGeneratorModel(int initialIndex, Random random) {
        this.random = java.util.Objects.requireNonNull(random, "random");
        index = Math.max(0, Math.min(initialIndex, DEFINITIONS.size() - 1));
        regenerate();
    }

    Example current() {
        return current;
    }

    int index() {
        return index;
    }

    int size() {
        return DEFINITIONS.size();
    }

    boolean canPrevious() {
        return index > 0;
    }

    boolean canNext() {
        return index + 1 < DEFINITIONS.size();
    }

    void previous() {
        if (canPrevious()) {
            index--;
            regenerate();
        }
    }

    void next() {
        if (canNext()) {
            index++;
            regenerate();
        }
    }

    void regenerate() {
        Definition definition = DEFINITIONS.get(index);
        current = new Example(definition.translationKey(),
                definition.probability(), generatedCards(index));
    }

    private List<String> generatedCards(int category) {
        List<CardCode> cards = switch (category) {
            case 0 -> randomFiveCardHand(1);
            case 1 -> repeatedRank(2);
            case 2 -> twoPair();
            case 3 -> repeatedRank(3);
            case 4 -> straight(false);
            case 5 -> randomFlush();
            case 6 -> fullHouse();
            case 7 -> repeatedRank(4);
            case 8 -> straight(true);
            case 9 -> royalFlush();
            default -> throw new IllegalArgumentException(
                    "Unknown hand category: " + category);
        };
        return cards.stream().map(CardCode::shortCode).toList();
    }

    private List<CardCode> randomFiveCardHand(int expectedValue) {
        ArrayList<CardCode> deck = fullDeck();
        do {
            Collections.shuffle(deck, random);
            List<CardCode> candidate = List.copyOf(deck.subList(0, 5));
            if (evaluatedValue(candidate) == expectedValue) return candidate;
        } while (true);
    }

    private List<CardCode> repeatedRank(int count) {
        Rank rank = randomRank();
        ArrayList<Suit> suits = shuffledSuits();
        ArrayList<CardCode> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(new CardCode(rank, suits.get(index)));
        }
        return List.copyOf(cards);
    }

    private List<CardCode> twoPair() {
        ArrayList<Rank> ranks = shuffledRanks();
        ArrayList<CardCode> cards = new ArrayList<>();
        cards.addAll(cardsOfRank(ranks.get(0), 2));
        cards.addAll(cardsOfRank(ranks.get(1), 2));
        return List.copyOf(cards);
    }

    private List<CardCode> fullHouse() {
        ArrayList<Rank> ranks = shuffledRanks();
        ArrayList<CardCode> cards = new ArrayList<>();
        cards.addAll(cardsOfRank(ranks.get(0), 3));
        cards.addAll(cardsOfRank(ranks.get(1), 2));
        return List.copyOf(cards);
    }

    private List<CardCode> cardsOfRank(Rank rank, int count) {
        ArrayList<Suit> suits = shuffledSuits();
        ArrayList<CardCode> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(new CardCode(rank, suits.get(index)));
        }
        return cards;
    }

    private List<CardCode> straight(boolean sameSuit) {
        List<Rank> ranks = NON_ROYAL_STRAIGHTS.get(
                random.nextInt(NON_ROYAL_STRAIGHTS.size()));
        ArrayList<CardCode> cards = new ArrayList<>();
        Suit common = sameSuit ? randomSuit() : null;
        for (Rank rank : ranks) {
            cards.add(new CardCode(rank,
                    sameSuit ? common : randomSuit()));
        }
        if (!sameSuit && cards.stream().map(CardCode::suit).distinct()
                .count() == 1) {
            Suit replacement = Suit.values()[
                    (cards.get(0).suit().ordinal() + 1) % Suit.values().length];
            cards.set(cards.size() - 1,
                    new CardCode(cards.get(cards.size() - 1).rank(), replacement));
        }
        return List.copyOf(cards);
    }

    private List<CardCode> randomFlush() {
        Suit suit = randomSuit();
        ArrayList<Rank> ranks = shuffledRanks();
        do {
            List<CardCode> cards = ranks.subList(0, 5).stream()
                    .map(rank -> new CardCode(rank, suit)).toList();
            if (evaluatedValue(cards) == 6) return cards;
            Collections.shuffle(ranks, random);
        } while (true);
    }

    private List<CardCode> royalFlush() {
        Suit suit = randomSuit();
        return ROYAL.stream().map(rank -> new CardCode(rank, suit)).toList();
    }

    private int evaluatedValue(List<CardCode> codes) {
        ArrayList<GameCardController> cards = new ArrayList<>();
        for (CardCode code : codes) {
            CoreCardController card = new CoreCardController();
            card.iniciarConValorNumerico(code.oneBased());
            cards.add(card);
        }
        return new CoreGameHand(cards, GameText.keys()).getValue();
    }

    private ArrayList<CardCode> fullDeck() {
        ArrayList<CardCode> cards = new ArrayList<>(52);
        for (int index = 0; index < 52; index++) {
            cards.add(CardCode.fromIndex(index));
        }
        return cards;
    }

    private Rank randomRank() {
        return Rank.values()[random.nextInt(Rank.values().length)];
    }

    private Suit randomSuit() {
        return Suit.values()[random.nextInt(Suit.values().length)];
    }

    private ArrayList<Rank> shuffledRanks() {
        ArrayList<Rank> ranks = new ArrayList<>(List.of(Rank.values()));
        Collections.shuffle(ranks, random);
        return ranks;
    }

    private ArrayList<Suit> shuffledSuits() {
        ArrayList<Suit> suits = new ArrayList<>(List.of(Suit.values()));
        Collections.shuffle(suits, random);
        return suits;
    }

    private record Definition(String translationKey, String probability) { }

    record Example(String translationKey, String probability,
            List<String> cards) {

        Example {
            cards = List.copyOf(cards);
            if (cards.size() < 2 || cards.size() > 5
                    || cards.stream().distinct().count() != cards.size()) {
                throw new IllegalArgumentException(
                        "A hand example requires two to five unique cards");
            }
        }
    }
}
