/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.crypto.CryptoRandom;
import java.util.Random;

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

    /** A dedicated bot stream for replayable scenarios; null uses the CSPRNG. */
    default Random botDecisionRandom(String nickname) {
        return null;
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
