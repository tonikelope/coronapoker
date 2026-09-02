package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class GameStateTest {
    @Test void cardCodesKeepTheExistingProtocolOrder() {
        assertEquals("A_P", CardCode.fromIndex(0).shortCode());
        assertEquals("K_D", CardCode.fromIndex(51).shortCode());
        assertEquals(51, CardCode.parseShortCode("K_D").index());
        assertThrows(IllegalArgumentException.class, () -> CardCode.fromIndex(52));
    }

    @Test void cardStateHasNoVisualDependencyAndSnapshotsItsFlags() {
        CardState state = new CardState();
        state.initialize(CardCode.of("A", "P"), false);
        state.setFaceUp(true);
        state.setVisible(true);
        state.setShowdownHighlighted(true);
        CardState.Snapshot snapshot = state.snapshot();
        assertTrue(snapshot.initialized());
        assertTrue(snapshot.faceUp());
        assertTrue(snapshot.visible());
        assertFalse(snapshot.disabled());
        assertEquals("A_P", snapshot.code().shortCode());
    }

    @Test void tableSnapshotIsDetachedFromLaterRosterMutations() {
        TableState table = new TableState("Local");
        table.putPlayer(new LocalPlayerState("Local"));
        TableState.Snapshot before = table.snapshot();
        table.putPlayer(new RemotePlayerState("Remote"));
        assertEquals(1, before.players().size());
        assertEquals(2, table.snapshot().players().size());
    }

    @Test void frontendCardModelsCanBackTheNeutralPlayerSnapshot() {
        LocalPlayerState player = new LocalPlayerState("Local");
        CardState first = new CardState();
        CardState second = new CardState();
        player.bindHoleCards(first, second);
        first.initialize(CardCode.parseShortCode("A_P"), false);
        second.initialize(CardCode.parseShortCode("K_D"), true);

        assertEquals("A_P", player.snapshot().holeCards().get(0).code().shortCode());
        assertEquals("K_D", player.snapshot().holeCards().get(1).code().shortCode());
    }
}
