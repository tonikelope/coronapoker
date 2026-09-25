package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GdxAboutEasterEggTest {

    @Test
    void readingSurfaceUsesTheLighterBlueSlateBackground() {
        assertEquals(0x365f78fc, GdxFrontendScreen.ABOUT_PANEL_RGBA);
    }

    @Test
    void memorialAndCopyrightStayInsideTheirOwnVisualBands() {
        float musicPanelTop = GdxFrontendScreen.ABOUT_MUSIC_PANEL_Y
                + GdxFrontendScreen.ABOUT_MUSIC_PANEL_HEIGHT;
        float mourningIconBottom = GdxFrontendScreen.ABOUT_MEMORIAL_CENTER_Y
                - GdxFrontendScreen.ABOUT_MOURNING_ICON_SIZE / 2f;

        assertTrue(mourningIconBottom > musicPanelTop + 20f,
                "the memorial ribbon must not invade the music panel");
        assertTrue(GdxFrontendScreen.ABOUT_COPYRIGHT_Y
                > GdxFrontendScreen.ABOUT_MUSIC_PANEL_Y,
                "the copyright note must be inside the music panel");
        assertTrue(GdxFrontendScreen.ABOUT_COPYRIGHT_Y
                < musicPanelTop,
                "the copyright note must not escape above the music panel");
        float lastMusicLine = GdxFrontendScreen.ABOUT_MUSIC_FIRST_LINE_Y
                - 3f * GdxFrontendScreen.ABOUT_MUSIC_LINE_GAP;
        assertTrue(lastMusicLine
                - GdxFrontendScreen.ABOUT_COPYRIGHT_Y >= 24f,
                "the copyright note must not overlap the last music credit");
    }

    @Test
    void decodesBothOriginalAboutImagesWithoutSwing() throws Exception {
        byte[] splash = resource("/images/splash.gif");
        for (String name : new String[]{"c", "g"}) {
            byte[] decoded = GdxAboutEasterEgg.decode(
                    new ByteArrayInputStream(splash),
                    new ByteArrayInputStream(resource("/images/" + name)));
            assertTrue(decoded.length > 0);
            assertNotNull(ImageIO.read(new ByteArrayInputStream(decoded)));
        }
    }

    @Test
    void packagesTheOriginalSwingAboutArtwork() throws Exception {
        assertImage("/images/luto.png", 100, 100);
        assertImage("/images/open-book.png", 32, 32);
        assertImage("/images/cruz.png", 23, 15);
    }

    @Test
    void preservesNativePixelsAndOnlyShrinksForSmallBackBuffers() {
        assertEquals(1f, GdxFrontendScreen.nativeImageScale(
                1024, 673, 1920, 1080, 48));
        assertEquals(1f, GdxFrontendScreen.nativeImageScale(
                1280, 640, 1920, 1080, 48));
        assertEquals(704f / 1024f, GdxFrontendScreen.nativeImageScale(
                1024, 673, 800, 600, 48), 0.0001f);
    }

    @Test
    void preservesTheTwoInteractiveLinksFromSwingAbout() {
        assertEquals("https://github.com/tonikelope/coronapoker",
                GdxFrontendScreen.ABOUT_PROJECT_URI.toString());
        assertEquals("https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf",
                GdxFrontendScreen.ABOUT_RULES_URI.toString());
    }

    private static void assertImage(String path, int width, int height)
            throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                resource(path)));
        assertNotNull(image, path);
        assertTrue(image.getWidth() == width, path + " width");
        assertTrue(image.getHeight() == height, path + " height");
    }

    private static byte[] resource(String path) throws Exception {
        try (var stream = GdxAboutEasterEggTest.class
                .getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }
}
