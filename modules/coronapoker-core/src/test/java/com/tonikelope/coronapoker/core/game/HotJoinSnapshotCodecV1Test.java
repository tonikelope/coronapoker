/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotJoinSnapshotCodecV1Test {

    @Test
    void roundTripPreservesPublicStateAndUsesReceiverAsLocalPlayer() {
        TableSnapshot source = new TableSnapshot(42L, "Host",
                TableSnapshot.Street.TURN, 17.5d, "Alice", true,
                List.of(player("Host", "AS", true),
                        player("Alice", "", false)),
                List.of(new TableSnapshot.CardSnapshot("KH", true, false),
                        new TableSnapshot.CardSnapshot("", false, false)));

        TableSnapshot decoded = HotJoinSnapshotCodecV1.decode(
                HotJoinSnapshotCodecV1.encode(source), "Late");

        assertEquals(42L, decoded.revision());
        assertEquals("Late", decoded.localNickname());
        assertEquals(TableSnapshot.Street.TURN, decoded.street());
        assertEquals(17.5d, decoded.pot());
        assertEquals("Alice", decoded.currentTurnNickname());
        assertEquals(source.players(), decoded.players());
        assertEquals(source.communityCards(), decoded.communityCards());
    }

    @Test
    void rejectsMalformedAndTrailingPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> HotJoinSnapshotCodecV1.decode("not-base64", "Late"));
        String valid = HotJoinSnapshotCodecV1.encode(new TableSnapshot(1L,
                "Host", TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(player("Host", "", false)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> HotJoinSnapshotCodecV1.decode(valid + "AA", "Late"));
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            String card, boolean faceUp) {
        List<TableSnapshot.CardSnapshot> cards = card.isEmpty()
                ? List.of()
                : List.of(new TableSnapshot.CardSnapshot(card, faceUp, false));
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 1d, 2d,
                true, false, false, false, 10, 11, 0, 12L,
                false, false, TableSnapshot.Position.NONE, "VA", "",
                cards, 10, 0);
    }
}
