package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class PotCardsEnvelopeTest {

    @Test
    void parsesOnlyAnExactCanonicalAtomicEnvelope() {
        byte[] key = new byte[32];
        key[0] = 1;
        byte[] sig = new byte[64];
        sig[0] = 2;
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        String key64 = Base64.getEncoder().encodeToString(key);
        String sig64 = Base64.getEncoder().encodeToString(sig);
        String[] wire = {"GAME", "7", "POTCARDS", nick, "A_C", "K_D", key64, sig64};

        PotCardsEnvelope parsed = PotCardsEnvelope.parse(wire, Set.of("alice"));

        assertEquals(1, parsed.entries().size());
        assertEquals("alice", parsed.entries().get(0).nick());
        assertEquals(13, parsed.entries().get(0).firstCard());
        assertEquals(51, parsed.entries().get(0).secondCard());
        assertArrayEquals(key, parsed.entries().get(0).pocketKey());
        assertArrayEquals(sig, parsed.entries().get(0).signature());

        assertThrows(IllegalArgumentException.class,
                () -> PotCardsEnvelope.parse(new String[]{"GAME", "7", "POTCARDS", nick,
                    "A_C", "K_D", key64, sig64, "TRAILING"}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class,
                () -> PotCardsEnvelope.parse(new String[]{"GAME", "7", "POTCARDS", nick,
                    "A_C", "A_C", key64, sig64}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class,
                () -> PotCardsEnvelope.parse(wire, Set.of("bob")));
        assertThrows(IllegalArgumentException.class,
                () -> PotCardsEnvelope.parse(wire, Set.of("alice", "bob")),
                "a malicious host must not omit an active contender");
    }

    @Test
    void rejectsDuplicatePlayersAndCardsAcrossEntries() {
        byte[] key = new byte[32];
        key[0] = 1;
        byte[] sig = new byte[64];
        String key64 = Base64.getEncoder().encodeToString(key);
        String sig64 = Base64.getEncoder().encodeToString(sig);
        String alice = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        String bob = Base64.getEncoder().encodeToString("bob".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> PotCardsEnvelope.parse(new String[]{"GAME", "8", "POTCARDS",
                    alice, "A_C", "K_D", key64, sig64,
                    alice, "Q_T", "J_D", key64, sig64}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class,
                () -> PotCardsEnvelope.parse(new String[]{"GAME", "9", "POTCARDS",
                    alice, "A_C", "K_D", key64, sig64,
                    bob, "A_C", "J_D", key64, sig64}, Set.of("alice", "bob")));
    }
}
