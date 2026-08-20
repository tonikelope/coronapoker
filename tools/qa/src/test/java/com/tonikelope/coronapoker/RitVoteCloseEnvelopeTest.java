package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RitVoteCloseEnvelopeTest {

    @Test
    void acceptsOnlyTheExactCurrentResultWire() {
        assertTrue(RitVoteCloseEnvelope.parse(parts("GAME#7#RIT_VOTE_CLOSE#0")).isOk());
        assertTrue(RitVoteCloseEnvelope.parse(parts("GAME#7#RIT_VOTE_CLOSE#1")).isOk());
        assertFalse(RitVoteCloseEnvelope.parse(parts("GAME#7#RIT_VOTE_CLOSE#2")).isOk());
        assertFalse(RitVoteCloseEnvelope.parse(parts("GAME#7#RIT_VOTE_CLOSE#1#extra")).isOk());
        assertFalse(RitVoteCloseEnvelope.parse(parts("GAME#7#RIT_VOTE_CLOSE")).isOk());
        assertFalse(RitVoteCloseEnvelope.parse(null).isOk());
    }

    private static String[] parts(String wire) {
        return wire.split("#", -1);
    }
}
