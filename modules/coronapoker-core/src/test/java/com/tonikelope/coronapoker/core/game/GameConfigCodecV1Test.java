package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConfigCodecV1Test {

    @Test
    void settingsRoundTripKeepsCanonicalBytes() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setIncreaseBlinds(true);
        draft.setBlindCap(true);
        draft.setRunItTwice(true);
        GameConfigCodecV1.Configuration original = GameConfigCodecV1.fromSettings(
                draft.snapshot(), false, "session-123");

        byte[] encoded = GameConfigCodecV1.encode(original);
        GameConfigCodecV1.Result decoded = GameConfigCodecV1.decode(encoded);

        assertTrue(decoded.isOk(), decoded.error());
        assertEquals(original, decoded.value());
        assertArrayEquals(encoded, GameConfigCodecV1.encode(decoded.value()));
    }

    @Test
    void strictPacketRejectsTrailingDataAndInvalidSettings() {
        GameConfigCodecV1.Configuration valid = GameConfigCodecV1.fromSettings(
                new NewGameTableDraft().snapshot(), false, "session-123");
        byte[] encoded = GameConfigCodecV1.encode(valid);

        assertFalse(GameConfigCodecV1.decode(Arrays.copyOf(encoded,
                encoded.length + 1)).isOk());
        assertThrows(IllegalArgumentException.class, () ->
                GameConfigCodecV1.fromSettings(new NewGameTableDraft().snapshot(),
                        false, " "));
    }
}
