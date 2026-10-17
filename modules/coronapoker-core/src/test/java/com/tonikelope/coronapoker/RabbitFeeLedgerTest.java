/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RabbitFeeLedgerTest {

    @Test
    void tableLifetimeBaselineMakesOnlyTheFirstRequestFree() {
        RabbitFeeLedger firstHand = ledger(1, 2, Map.of());
        RabbitFeeLedger.Authorization first = firstHand.authorize(
                request(1, "Ana", 1)).value();

        assertEquals(1, first.count());
        assertEquals(0L, first.feeCents());
        assertEquals(RabbitFeeLedger.Acceptance.ACCEPTED,
                firstHand.accept(first));

        RabbitFeeLedger secondHand = ledger(2, 2, Map.of("Ana", 1));
        RabbitFeeLedger.Authorization second = secondHand.authorize(
                request(2, "Ana", 2)).value();

        assertEquals(2, second.count());
        assertEquals(10L, second.feeCents());
        assertEquals(RabbitFeeLedger.Acceptance.ACCEPTED,
                secondHand.accept(second));
    }

    @Test
    void smallThenBigBlindModeKeepsSwingCounterSemanticsAcrossHands() {
        RabbitFeeLedger secondHand = ledger(2, 3, Map.of("Ana", 1));
        RabbitFeeLedger.Authorization second = secondHand.authorize(
                request(2, "Ana", 2)).value();
        RabbitFeeLedger thirdHand = ledger(3, 3, Map.of("Ana", 2));
        RabbitFeeLedger.Authorization third = thirdHand.authorize(
                request(3, "Ana", 3)).value();

        assertEquals(10L, second.feeCents());
        assertEquals(20L, third.feeCents());
        assertEquals(3, third.count());
    }

    @Test
    void eachPlayerKeepsAnIndependentCounter() {
        RabbitFeeLedger ledger = ledger(4, 2, Map.of("Ana", 3));

        RabbitFeeLedger.Authorization ana = ledger.authorize(
                request(4, "Ana", 4)).value();
        RabbitFeeLedger.Authorization borja = ledger.authorize(
                request(4, "Borja", 5)).value();

        assertEquals(4, ana.count());
        assertEquals(10L, ana.feeCents());
        assertEquals(1, borja.count());
        assertEquals(0L, borja.feeCents());
    }

    private static RabbitFeeLedger ledger(int handSeed, int mode,
            Map<String, Integer> baseline) {
        byte[] hand = new byte[RabbitFeeLedger.HAND_BYTES];
        hand[0] = (byte) handSeed;
        return new RabbitFeeLedger(hand, mode, 10L, 20L, baseline);
    }

    private static RabbitFeeLedger.Request request(int handSeed,
            String nickname, int nonceSeed) {
        byte[] hand = new byte[RabbitFeeLedger.HAND_BYTES];
        hand[0] = (byte) handSeed;
        byte[] nonce = new byte[RabbitFeeLedger.NONCE_BYTES];
        nonce[0] = (byte) nonceSeed;
        return new RabbitFeeLedger.Request(hand, nickname, nonce,
                new byte[HandStateChain.SIG_BYTES]);
    }
}
