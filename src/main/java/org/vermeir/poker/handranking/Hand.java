/*
 * Copyright (C) 2016 Jellen vermeir
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
package org.vermeir.poker.handranking;

import java.util.Objects;
import java.util.Set;

/**
 * This class represent the abstract base class for a poker hand
 *
 * @author Jellen Vermeir
 */
public abstract class Hand implements Comparable<Hand> {

    protected Set<Card> cards;
    protected int handValue;

    /**
     * Return the hand as a set of cards
     *
     * @return The hand
     */
    public Set<Card> getCards() {
        return cards;
    }

    /**
     * Return the handValue
     *
     * @return the handValue
     */
    public int getHandValue() {
        return this.handValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }

        Hand otherHand = (Hand) obj;
        return otherHand.getCards().equals(this.cards);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Objects.hashCode(this.cards);
        return hash;
    }

    @Override
    public int compareTo(Hand o) {
        if (this.handValue > o.getHandValue()) {
            return 1;
        }
        if (this.handValue < o.getHandValue()) {
            return -1;
        }

        return 0;
    }
}
