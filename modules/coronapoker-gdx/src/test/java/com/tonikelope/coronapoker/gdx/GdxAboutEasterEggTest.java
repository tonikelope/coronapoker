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
        assertFalse(GdxFrontendScreen.renderMainMenuContent(true, true));
        assertFalse(GdxFrontendScreen.renderMainMenuContent(false, false));
        assertTrue(GdxFrontendScreen.renderMainMenuContent(true, false));
    }

    @Test
    void bundledGameMusicIsCreditedInBothLanguages() {
        for (String language : new String[]{"es", "en"}) {
            GdxGameText text = new GdxGameText(language);
            assertTrue(text.translate("about.musica_juego").contains("David Luong"));
            assertTrue(text.translate("about.software_license").contains("GPLv3"));
        }
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
    void aboutImagesFitTheSameCenteredAreaAsTheCardViewer() {
        var nativeImage = CoronaPokerGdxTable.cardViewerBounds(1024, 673, 1920, 1080);
        assertEquals(1024f, nativeImage.width);
        assertEquals(673f, nativeImage.height);
        assertEquals(448f, nativeImage.x);
        assertEquals(203.5f, nativeImage.y);
        var fittedImage = CoronaPokerGdxTable.cardViewerBounds(1024, 673, 800, 600);
        assertEquals(576f, fittedImage.width, 0.0001f);
        assertEquals(400f, fittedImage.x + fittedImage.width / 2f, 0.0001f);
        assertEquals(300f, fittedImage.y + fittedImage.height / 2f, 0.0001f);
    }

    @Test
    void aboutUsesThePackagedPdfAndTheProjectLink() throws Exception {
        assertEquals("https://github.com/tonikelope/coronapoker",
                GdxFrontendScreen.ABOUT_PROJECT_URI.toString());
        assertEquals("%PDF-", new String(resource(GdxRobertRulesAssets.ORIGINAL),
                0, 5, java.nio.charset.StandardCharsets.US_ASCII));
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
