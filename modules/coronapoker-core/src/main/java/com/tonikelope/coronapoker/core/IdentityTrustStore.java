package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.core.game.GameIdentityTrust;
import java.util.List;

/** Mutable TOFU store used by frontends, transport and the canonical dealer. */
public interface IdentityTrustStore extends GameIdentityTrust {

    enum Observation { NEW, MATCH, CHANGED }

    /**
     * A known nickname has presented a different Ed25519 key. The trust store
     * has already replaced the pin and, when applicable, cleared its explicit
     * verification flag when this notice is emitted. Frontends may use
     * {@code previouslyVerified} to keep visual warnings limited to identities
     * the user had explicitly verified while still logging every rotation.
     */
    record IdentityChange(String nickname, boolean previouslyVerified) {
        public IdentityChange {
            if (nickname == null || nickname.isBlank()) {
                throw new IllegalArgumentException("nickname is required");
            }
            nickname = nickname.trim();
        }
    }

    Observation observe(String nickname, byte[] publicKey);

    boolean markVerified(String nickname, byte[] publicKey);

    /** Drains identity-key changes that have not yet reached the frontend. */
    default List<IdentityChange> drainIdentityChanges() {
        return List.of();
    }

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
