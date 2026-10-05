package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GdxAboutEasterEggTest {

    @Test
    void readingSurfaceIsOpaqueAndDoesNotRenderOverTheMenu() {
        assertNotEquals(GdxUiDialogStyle.PANEL_RGBA,
                GdxFrontendScreen.ABOUT_PANEL_RGBA);
        assertEquals(0xff, GdxFrontendScreen.ABOUT_PANEL_RGBA & 0xff,
                "the reading surface must be opaque");
        assertFalse(GdxFrontendScreen.renderMainMenuContent(true, false));
        assertFalse(GdxFrontendScreen.renderMainMenuContent(false, true));
        assertTrue(GdxFrontendScreen.renderMainMenuContent(false, false));
    }

    @Test
    void aboutCopyKeepsClearRhythmOnTheSingleDialogBackground() {
        float mourningIconBottom = GdxFrontendScreen.ABOUT_MEMORIAL_CENTER_Y
                - GdxFrontendScreen.ABOUT_MOURNING_ICON_SIZE / 2f;

        assertTrue(mourningIconBottom
                > GdxFrontendScreen.ABOUT_MUSIC_FIRST_LINE_Y + 20f,
                "the memorial must remain visually separate from music copy");
        float lastMusicLine = GdxFrontendScreen.ABOUT_MUSIC_FIRST_LINE_Y
                - 3f * GdxFrontendScreen.ABOUT_MUSIC_LINE_GAP;
        assertTrue(lastMusicLine
                - GdxFrontendScreen.ABOUT_COPYRIGHT_Y >= 24f,
                "the copyright note must not overlap the last music credit");
        assertTrue(GdxFrontendScreen.ABOUT_COPYRIGHT_Y - 226f >= 80f,
                "copyright and handmade credits need breathing room");
    }

    @Test
    void aboutCopyIsDrawnDirectlyOnTheDialogWithoutInteriorBoxes()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private void drawAboutDialog()");
        int end = source.indexOf("private void drawHandGeneratorDialog()",
                start);
        assertTrue(start >= 0 && end > start);
        assertFalse(source.substring(start, end).contains("drawInset("),
                "about content must sit directly on the dialog background");
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
