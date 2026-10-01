/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.crypto.CryptoRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Supplies the independent entropy domains that affect a game's observable
 * deal and automated decisions. Production uses the process CSPRNG. Keeping
 * these domains explicit lets an isolated scenario replay an exact hand
 * without weakening or replacing any shuffle/crypto algorithm.
 */
public interface GameEntropySource {

    void fillHandId(byte[] target);

    void fillLocalShuffleSeed(byte[] target);

    void fillPeerShuffleSeed(String nickname, byte[] target);

    void fillBotShuffleSeed(String nickname, byte[] target);

    /** Nonce for the verifiable commit-reveal seat draw. */
    default void fillSeatDrawNonce(byte[] target) {
        CryptoRandom.fill(target);
    }

    /** Local human contribution to the verifiable seat draw. */
    default void fillSeatDrawReveal(String nickname, byte[] target) {
        CryptoRandom.fill(target);
    }

    /** A dedicated bot stream for replayable scenarios; null uses the CSPRNG. */
    default Random botDecisionRandom(String nickname) {
        return null;
    }

    /** Random draw used by the host's automated voluntary-straddle choice. */
    default double botStraddleDecision(String nickname) {
        return CryptoRandom.nextDouble();
    }

    /** Randomises newcomers without coupling recovery to process-global state. */
    default <T> void shuffleSeatOrder(List<T> values) {
        CryptoRandom.shuffle(values);
    }

    /**
     * Randomises display-only Monte Carlo samples. Production deliberately
     * keeps the fast PRNG; replayable scenarios override this domain.
     */
    default <T> void shuffleOddsDeck(List<T> values) {
        Collections.shuffle(values, ThreadLocalRandom.current());
    }

    /** Chooses the observable all-in cinematic cycle. */
    default <T> void shuffleAllInCinematicBag(List<T> values) {
        CryptoRandom.shuffle(values);
    }

    /** Avoids repeating the last all-in cinematic across bag boundaries. */
    default int allInCinematicSwapIndex(int bound) {
        return CryptoRandom.nextInt(bound);
    }

    static GameEntropySource secure() {
        return new GameEntropySource() {
            @Override
            public void fillHandId(byte[] target) {
                CryptoRandom.fill(target);
            }

            @Override
            public void fillLocalShuffleSeed(byte[] target) {
                CryptoRandom.fill(target);
            }

            @Override
            public void fillPeerShuffleSeed(String nickname, byte[] target) {
                CryptoRandom.fill(target);
            }

            @Override
            public void fillBotShuffleSeed(String nickname, byte[] target) {
                CryptoRandom.fill(target);
            }
        };
    }
}
