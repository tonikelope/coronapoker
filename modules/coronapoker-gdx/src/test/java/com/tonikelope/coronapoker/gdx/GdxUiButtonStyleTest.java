package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class GdxUiButtonStyleTest {

    @Test
    void everySemanticToneChangesItsBorderOnHover() {
        for (GdxUiButtonStyle.Tone tone : GdxUiButtonStyle.Tone.values()) {
            Color idle = GdxUiButtonStyle.resolveAccent(tone, 0f,
                    new Color());
            Color hover = GdxUiButtonStyle.resolveAccent(tone, 1f,
                    new Color());
            assertNotEquals(idle.toIntBits(), hover.toIntBits(),
                    () -> tone + " must expose a visible hover border");
        }
    }

    @Test
    void customPokerPalettesKeepTheirHueButGainHoverContrast() {
        Color accent = new Color(0x4caf50ff);
        Color idle = GdxUiButtonStyle.resolvePaletteAccent(accent, 0f,
                new Color());
        Color hover = GdxUiButtonStyle.resolvePaletteAccent(accent, 1f,
                new Color());

        assertNotEquals(idle.toIntBits(), hover.toIntBits());
        assertTrue(hover.r >= idle.r && hover.g >= idle.g
                && hover.b >= idle.b,
                "the semantic palette must brighten instead of turning cyan");
    }
}
