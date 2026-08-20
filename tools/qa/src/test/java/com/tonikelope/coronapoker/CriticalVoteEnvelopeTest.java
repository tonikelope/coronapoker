package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class CriticalVoteEnvelopeTest {

    private static final String ALICE = Base64.getEncoder().encodeToString(
            "alice".getBytes(StandardCharsets.UTF_8));

    @Test
    void parsesOnlyCurrentRitResponseWire() {
        CriticalVoteEnvelope parsed = CriticalVoteEnvelope.parseRitResponse(
                new String[]{"GAME", "7", "RIT_VOTE_RESP", ALICE, "1"});

        assertEquals("alice", parsed.nick());
        assertEquals(1, parsed.decision());
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseRitResponse(
                new String[]{"GAME", "7", "RIT_VOTE_RESP", ALICE, "1", "legacy"}));
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseRitResponse(
                new String[]{"GAME", "x", "RIT_VOTE_RESP", ALICE, "1"}));
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseRitResponse(
                new String[]{"GAME", "7", "RIT_VOTE_RESP", ALICE, "2"}));
    }

    @Test
    void parsesOnlySignedCurrentStraddleWires() {
        byte[] sig = new byte[64];
        String sigB64 = Base64.getEncoder().encodeToString(sig);
        CriticalVoteEnvelope response = CriticalVoteEnvelope.parseStraddleResponse(
                new String[]{"GAME", "8", "STRADDLE_RESP", ALICE, "0", sigB64});
        CriticalVoteEnvelope decision = CriticalVoteEnvelope.parseStraddleDecision(
                new String[]{"GAME", "9", "STRADDLE_DECISION", ALICE, "1", sigB64});

        assertArrayEquals(sig, response.signature());
        assertEquals(1, decision.decision());
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseStraddleResponse(
                new String[]{"GAME", "8", "STRADDLE_RESP", ALICE, "0"}));
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseStraddleDecision(
                new String[]{"GAME", "9", "STRADDLE_DECISION", ALICE, "1", ""}));
    }

    @Test
    void rejectsNonCanonicalOrInvalidNickEncoding() {
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseRitResponse(
                new String[]{"GAME", "7", "RIT_VOTE_RESP", "YWxpY2U", "1"}));
        String malformedUtf8 = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xC3, 0x28});
        assertThrows(IllegalArgumentException.class, () -> CriticalVoteEnvelope.parseRitResponse(
                new String[]{"GAME", "7", "RIT_VOTE_RESP", malformedUtf8, "1"}));
    }
}
