/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionNoticeOrderingTest {
    @Test
    void aDelayedTimeoutAfterDepartureIsHarmless() {
        var peers = new HashSet<>(Set.of("bob"));
        var applied = new ArrayList<String>();
        var notice = new GameChannel.Inbound("host", "TIMEOUT#Ym9i");
        CoreGameTableFactory.receiveTimeoutNotice(notice, "host", peers, applied::add);
        assertEquals(java.util.List.of("bob"), applied);
        peers.remove("bob");
        assertDoesNotThrow(() -> CoreGameTableFactory.receiveTimeoutNotice(notice, "host", peers, applied::add),
                "a delayed UI notice cannot close the game after DELUSER removed its player");
        assertEquals(java.util.List.of("bob"), applied);
    }

    @Test
    void concurrentRetirementBeforeApplyingTheNoticeIsHarmless() throws Exception {
        Crupier dealer = new Crupier();
        var field = Crupier.class.getDeclaredField("nick2player");
        field.setAccessible(true);
        field.set(dealer, new java.util.concurrent.ConcurrentHashMap<>());
        assertDoesNotThrow(() -> CoreGameTableFactory.receiveTimeoutNotice(
                new GameChannel.Inbound("host", "TIMEOUT#Ym9i"), "host", Set.of("bob"),
                nickname -> dealer.applyPlayerTimeout(nickname, true)));
    }

    @Test
    void staleNoticesStillRequireTheAuthenticatedHost() {
        assertThrows(IllegalArgumentException.class, () -> CoreGameTableFactory.receiveTimeoutNotice(
                new GameChannel.Inbound("intruder", "TIMEOUT#Ym9i"), "host", Set.of(), ignored -> fail()));
    }

    @Test
    void malformedConnectionNoticesRemainInvalid() {
        for (String body : new String[]{"TIMEOUT", "TIMEOUT#", "TIMEOUT#%%%", "TIMEOUT#/w==",
                "TIMEOUT#Ym9i#extra", "ACTION#Ym9i"}) {
            assertThrows(IllegalArgumentException.class, () -> CoreGameTableFactory.receiveTimeoutNotice(
                    new GameChannel.Inbound("host", body), "host", Set.of(), ignored -> fail()), body);
        }
    }
}
