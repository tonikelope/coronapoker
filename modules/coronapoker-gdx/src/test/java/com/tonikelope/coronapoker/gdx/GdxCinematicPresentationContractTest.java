package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxCinematicPresentationContractTest {

    @Test
    void cinematicShaderAntialiasesOnlyTheRoundedSilhouette() {
        String shader = CoronaPokerGdxTable.CINEMATIC_FRAGMENT_SHADER;

        assertTrue(shader.contains("GL_OES_standard_derivatives"));
        assertEquals(4, occurrences(shader,
                "coverage += cinematicCoverageAt("),
                "four rotated subpixel samples resolve each rounded corner");
        assertTrue(shader.contains(
                "vec4 pixel = texture2D(u_texture, v_texCoords) * v_color;"),
                "cinematic artwork must remain a single sharp texture sample");
        assertTrue(shader.contains("pixel.a *= coverage;"));
    }

    @Test
    void cinematicRadiusScalesWithoutBecomingSquareOrExcessive() {
        assertEquals(0f, CoronaPokerGdxTable.cinematicCornerRadius(0f, 200f));
        assertEquals(7f, CoronaPokerGdxTable.cinematicCornerRadius(120f, 80f));
        assertEquals(18f,
                CoronaPokerGdxTable.cinematicCornerRadius(800f, 400f),
                0.001f);
        assertEquals(24f,
                CoronaPokerGdxTable.cinematicCornerRadius(1600f, 900f));
    }

    @Test
    void everyBuiltInTableCinematicUsesTheRoundedGpuPath()
            throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8);

        assertRounded(source, "private void drawLiveShuffleOverlay()",
                "private void drawTextOnlyShuffleOverlay(");
        assertRounded(source, "private void drawLiveCinematicOverlay()",
                "static float cinematicCornerRadius(");
        assertRounded(source, "private void drawGameOverDialog(",
                "private StreamingGifTextureAnimation gameOverAnimation(");
        assertRounded(source, "private void drawRemoteRebuyOverlays()",
                "private Rectangle remoteRebuyOverlayBounds(");
        assertRounded(source, "private void drawRecoveryDialog(",
                "private StreamingGifTextureAnimation recoveryAnimation(");
    }

    private static void assertRounded(String source, String startMarker,
            String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, startMarker);
        String method = source.substring(start, end);
        assertTrue(method.contains("drawRoundedCinematicFrame("), startMarker);
        assertFalse(method.contains("batch.draw(frame,"),
                "the raw rectangular GIF path must not bypass the mask");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
