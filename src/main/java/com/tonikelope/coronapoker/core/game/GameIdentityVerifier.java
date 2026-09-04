/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.identity.GameIdentityProtocol;

/** Stateless verification helpers needed by compatibility entry points. */
public final class GameIdentityVerifier {

    private GameIdentityVerifier() { }

    public static boolean verifyAction(byte[] publicKey, byte[] record, byte[] signature) {
        return GameIdentityProtocol.verifyAction(publicKey, record, signature);
    }
}
