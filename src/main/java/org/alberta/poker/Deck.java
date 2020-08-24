/** *************************************************************************
 * Copyright (c) 2000:
 * University of Alberta,
 * Deptartment of Computing Science
 * Computer Poker Research Group
 *
 * See "Liscence.txt"
 ************************************************************************** */
package org.alberta.poker;

import java.util.Random;

/**
 * A Deck of 52 Cards which can be dealt and shuffled
 *
 * Some functions could be made much faster with some extra memory. I invite
 * anyone to do so ;-)
 *
 * @author Aaron Davidson
 * @version 1.0.4
 */
public class Deck {

    public static final int NUM_CARDS = 52;
    private Card[] gCards = new Card[NUM_CARDS];
    private char position; // top of deck
    private Random r = new Random();
    private Random r2 = new Random();

    /**
     * Constructor.
     */
    public Deck() {
        position = 0;
        for (int i = 0; i < NUM_CARDS; i++) {
            gCards[i] = new Card(i);
        }
    }

    /**
     * Constructor w/ shuffle seed.
     *
     * @param seed the seed to use in randomly shuffling the deck.
     */
    public Deck(long seed) {
        this();
        if (seed == 0) {
            seed = System.currentTimeMillis();
        }
        r.setSeed(seed);
        r2.setSeed(seed / 2 + NUM_CARDS);
    }

    /**
     * Constructor w/ shuffle seed.
     *
     * @param seed the seed to use in randomly shuffling the deck.
     */
    public Deck(long seed, long seed2) {
        this();
        if (seed == 0) {
            seed = System.currentTimeMillis();
        }
        r.setSeed(seed);
        r2.setSeed(seed2);
    }

    /**
     * Places all cards back into the deck. Note: Does not sort the deck.
     */
    public synchronized void reset() {
        position = 0;
    }

    /**
     * Shuffles the cards in the deck.
     */
    public synchronized void shuffle() {
        Card tempCard;
        int i, j;
        for (i = 0; i < NUM_CARDS; i++) {
            j = i + randInt(NUM_CARDS - i);
            tempCard = gCards[j];
            gCards[j] = gCards[i];
            gCards[i] = tempCard;
        }
        position = 0;
    }

    /**
     * Obtain the next card in the deck. If no cards remain, a null card is
     * returned
     *
     * @return the card dealt
     */
    public synchronized Card deal() {
        return (position < NUM_CARDS ? gCards[position++] : null);
    }

    /**
     * Obtain the next card in the deck. If no cards remain, a null card is
     * returned
     *
     * @return the card dealt
     */
    public synchronized Card dealCard() {
        return extractRandomCard();
    }

    /**
     * Find position of Card in Deck.
     */
    public synchronized int findCard(Card c) {
        int i = position;
        int n = c.getIndex();
        while (i < NUM_CARDS && n != gCards[i].getIndex()) {
            i++;
        }
        return (i < NUM_CARDS ? i : -1);
    }

    private synchronized int findDiscard(Card c) {
        int i = 0;
        int n = c.getIndex();
        while (i < position && n != gCards[i].getIndex()) {
            i++;
        }
        return (n == gCards[i].getIndex() ? i : -1);
    }

    /**
     * Remove all cards in the given hand from the Deck.
     */
    public synchronized void extractHand(Hand h) {
        for (int i = 1; i <= h.size(); i++) {
            this.extractCard(h.getCard(i));
        }
    }

    /**
     * Remove a card from within the deck.
     *
     * @param c the card to remove.
     */
    public synchronized void extractCard(Card c) {
        int i = findCard(c);
        if (i != -1) {
            Card t = gCards[i];
            gCards[i] = gCards[position];
            gCards[position] = t;
            position++;
        } else {
            System.err.println("*** ERROR: could not find card " + c);
            Thread.currentThread().dumpStack();
        }
    }

    /**
     * Remove and return a randomly selected card from within the deck.
     */
    public synchronized Card extractRandomCard() {
        int pos = position + randInt2(NUM_CARDS - position);
        Card c = gCards[pos];
        gCards[pos] = gCards[position];
        gCards[position] = c;
        position++;
        return c;
    }

    /**
     * Return a randomly selected card from within the deck without removing it.
     */
    public synchronized Card pickRandomCard() {
        return gCards[position + randInt(NUM_CARDS - position)];
    }

    /**
     * Place a card back into the deck.
     *
     * @param c the card to insert.
     */
    public synchronized void replaceCard(Card c) {
        int i = findDiscard(c);
        if (i != -1) {
            position--;
            Card t = gCards[i];
            gCards[i] = gCards[position];
            gCards[position] = t;
        }
    }

    /**
     * Obtain the position of the top card. (the number of cards dealt from the
     * deck)
     *
     * @return the top card index
     */
    public synchronized int getTopCardIndex() {
        return position;
    }

    /**
     * Obtain the number of cards left in the deck
     */
    public synchronized int cardsLeft() {
        return NUM_CARDS - position;
    }

    /**
     * Obtain the card at a specific index in the deck. Does not matter if card
     * has been dealt or not. If i < topCardIndex it has been dealt. @param i
     * the index into the
     *
     * deck (0..51) @return the card at position i
     */
    public synchronized Card getCard(int i) {
        return gCards[i];
    }

    public String toString() {
        StringBuffer s = new StringBuffer();
        s.append("* ");
        for (int i = 0; i < position; i++) {
            s.append(gCards[i].toString() + " ");
        }
        s.append("\n* ");
        for (int i = position; i < NUM_CARDS; i++) {
            s.append(gCards[i].toString() + " ");
        }
        return s.toString();
    }

    private int randInt(int range) {
        return (int) (r.nextDouble() * range);
    }

    private int randInt2(int range) {
        return (int) (r2.nextDouble() * range);
    }

}
