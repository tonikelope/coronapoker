/*
 * Copyright (C) 2016 Jellen Vermeir
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vermeir.poker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.vermeir.poker.handranking.Card;

/**
 * This class represents a deck of cards
 *
 * @author Jellen Vermeir
 */
public class Deck {

    // List of Cards in the deck. Fast random access required (<> Set)
    private final List<Card> cards;
    // Random generator
    private final Random random;

    /**
     * Default constructor. Create a Deck that consists of a List of 52 unique
     * Card objects.
     */
    public Deck() {
        this.cards = new ArrayList<>();
        for (int i = 0; i < Card.ALL_SUITS.length; i++) {
            for (int j = 0; j < Card.ALL_RANKS.length; j++) {
                this.cards.add(new Card(Card.ALL_SUITS[i], Card.ALL_RANKS[j]));
            }
        }
        this.random = new Random();
    }

    /**
     * This constructor creates a new Deck from a Set of Cards, given as input.
     *
     * @param c A Set of Card objects.
     */
    public Deck(Set<Card> c) {
        this.cards = new ArrayList<>(new HashSet<>(c));
        this.random = new Random();
    }

    /**
     * This function draws and removes a random card from the deck.
     *
     * @return The random card that was drawn
     */
    public Card drawRandomCard() {
        return drawRandomCard(true);
    }

    /**
     * This function draws and optionally removes a random card from the deck.
     * Removal depends on the input flag setting.
     *
     * @param remove True if the card should be removed, false otherwise.
     * @return The random Card that was drawn from the deck.
     */
    public Card drawRandomCard(boolean remove) {
        int randomIndex = this.random.nextInt(this.cards.size());
        Card selectedCard = this.cards.get(randomIndex);

        if (remove) {
            this.cards.remove(randomIndex);
        }

        return selectedCard;
    }

    /**
     * Thie function returns the List of remaining cards in the deck.
     *
     * @return A list of remaining cards in the deck.
     */
    public List<Card> getCards() {
        return this.cards;
    }

    /**
     * This function removes a Card from the deck.
     *
     * @param c The card that must be removed from the deck.
     */
    public void removeCard(Card c) {
        this.cards.remove(c);
    }

    /**
     * This function return a selected card from the deck. The relevant card is
     * selected via the input index.
     *
     * @param i The index of the card that should be returned.
     * @return The requested card, located at index i.
     */
    public Card getCard(int i) {
        return cards.get(i);
    }
}
