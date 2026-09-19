package com.tonikelope.coronapoker.core.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerIdentityTest {
    @TempDir Path temporary;

    @Test void persistsPerCanonicalNicknameAndSignsTheClassicJoinPayload() throws Exception {
        Path identities = temporary.resolve(".coronapoker");
        PlayerIdentity first = PlayerIdentity.loadOrCreate(identities, "  Juga\u0064or  ");
        PlayerIdentity reloaded = PlayerIdentity.loadOrCreate(identities, "Jugador");
        assertArrayEquals(first.publicKey(), reloaded.publicKey());

        byte[] session = new byte[16];
        session[0] = 42;
        byte[] signature = first.signJoin(session);
        assertTrue(PlayerIdentity.verifyJoin(session, "Jugador", first.publicKey(), signature));
        session[1] = 1;
        assertFalse(PlayerIdentity.verifyJoin(session, "Jugador", first.publicKey(), signature));
    }
}
