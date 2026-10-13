package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void textualLabelsAlwaysKeepAVisibleHorizontalSafeArea() {
        assertEquals(14f, GdxUiButtonStyle.labelHorizontalInset(80f));
        assertEquals(52f, GdxUiButtonStyle.labelContentWidth(80f));
        assertEquals(26f, GdxUiButtonStyle.labelHorizontalInset(220f));
        assertEquals(168f, GdxUiButtonStyle.labelContentWidth(220f));
        assertTrue(GdxUiButtonStyle.labelContentWidth(220f) < 220f);
    }

    @Test
    void nativeFontFallbackCanRecoverSpaceWithoutTouchingTheButtonEdge() {
        assertEquals(12f,
                GdxUiButtonStyle.minimumLabelHorizontalInset(80f));
        assertEquals(56f, GdxUiButtonStyle.nativeLabelContentWidth(80f));
        assertEquals(14f,
                GdxUiButtonStyle.minimumLabelHorizontalInset(220f));
        assertEquals(192f,
                GdxUiButtonStyle.nativeLabelContentWidth(220f));
    }

    @Test
    void roundedSurfacesDoNotReintroduceUnclippedRectangularNoise()
            throws Exception {
        String base = "src/main/java/com/tonikelope/coronapoker/gdx/";
        String buttons = Files.readString(Path.of(base
                + "GdxUiButtonStyle.java"), StandardCharsets.UTF_8);
        String settings = Files.readString(Path.of(base
                + "GdxSettingsStyle.java"), StandardCharsets.UTF_8);
        String frontend = Files.readString(Path.of(base
                + "GdxFrontendScreen.java"), StandardCharsets.UTF_8);
        String bevel = Files.readString(Path.of(base
                + "GdxGlassBevel.java"), StandardCharsets.UTF_8);

        assertFalse(buttons.contains("GRADIENT_"));
        assertFalse(buttons.contains("gradient(shapes"));
        assertFalse(settings.contains("GRADIENT_"));
        assertFalse(frontend.contains("BOX_SHEEN"));
        assertFalse(frontend.contains("BOX_SHADE"));
        assertFalse(buttons.contains("GdxRoundedGradient"));
        assertFalse(settings.contains("GdxRoundedGradient"));
        assertFalse(frontend.contains("GdxRoundedGradient"));
        assertTrue(buttons.contains("GdxGlassBevel.draw"));
        assertTrue(settings.contains("GdxGlassBevel.draw"));
        assertFalse(frontend.contains("GdxGlassBevel.draw"),
                "large dialog surfaces must stay uniformly filled");
        assertFalse(buttons.contains("y - (pressed ? 1f : 2f)"));
        assertFalse(settings.contains("y - 1f, width, height"));
        assertFalse(buttons.contains(
                "roundedRectOutline(shapes, x + 5f, y + 5f"));
        assertFalse(bevel.contains("bottomThickness"));
        assertFalse(bevel.contains("bottomAlpha"));
        assertFalse(bevel.contains("rectLine("));
        assertFalse(bevel.contains("shapes.arc("));
        assertTrue(bevel.contains("roundedInset"));
        assertTrue(bevel.contains("smoothstep"));
    }
}
