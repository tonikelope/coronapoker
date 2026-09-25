package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.core.game.GameIdentityTrust;

/** Mutable TOFU store used by frontends, transport and the canonical dealer. */
public interface IdentityTrustStore extends GameIdentityTrust {

    enum Observation { NEW, MATCH, CHANGED }

    Observation observe(String nickname, byte[] publicKey);

    boolean markVerified(String nickname, byte[] publicKey);

    static IdentityTrustStore unavailable() {
        return new IdentityTrustStore() {
            @Override public Observation observe(String nickname, byte[] publicKey) {
                return Observation.NEW;
            }
            @Override public boolean markVerified(String nickname, byte[] publicKey) {
                return false;
            }
            @Override public boolean isVerified(String nickname, byte[] publicKey) {
                return false;
            }
        };
    }
}
