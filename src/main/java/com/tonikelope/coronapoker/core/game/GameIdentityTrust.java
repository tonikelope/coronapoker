/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Read-only identity trust decision required by the canonical dealer. */
@FunctionalInterface
public interface GameIdentityTrust {

    boolean isVerified(String nickname, byte[] publicKey);

    static GameIdentityTrust unverified() {
        return (nickname, publicKey) -> false;
    }
}
