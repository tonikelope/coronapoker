package com.tonikelope.coronapoker;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class ExitedRingParticipantStillRequiredForConsensusTest {

    @Test
    public void immutableHandRingDeterminesHumanReceiptSet() {
        String[] handRing = {"host", "alice", "bot-1"};
        LinkedHashSet<String> expected = new LinkedHashSet<>(Arrays.asList("host", "alice"));

        assertEquals(expected,
                Crupier.expectedConsensusSignersForRing(handRing, Collections.singleton("bot-1")));
    }
}
