/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

/**
 * Mandatory product contract for every fatal Zero Trust route.
 *
 * <p>The financial disposition still belongs to the caller because a
 * pre-settlement abort may refund safely while a post-settlement divergence
 * must preserve the open hand.  What may never vary is the user-facing and
 * forensic contract: complete evidence is recorded, the alert is acknowledged
 * before terminal navigation, and the table closes as a security failure.</p>
 */
enum ZeroTrustIncidentContract {

    SECURITY_LOCKDOWN(true),
    PRE_SETTLEMENT_ABORT(false),
    SETTLEMENT_CONSENSUS(true),
    HAND_BOUNDARY(false);

    private final boolean preserveOpenHand;

    ZeroTrustIncidentContract(boolean preserveOpenHand) {
        this.preserveOpenHand = preserveOpenHand;
    }

    boolean preserveOpenHand() {
        return preserveOpenHand;
    }

    boolean requiresEvidence() {
        return true;
    }

    boolean requiresAcknowledgementBeforeClose() {
        return true;
    }

    boolean closesAsFailure() {
        return true;
    }
}
