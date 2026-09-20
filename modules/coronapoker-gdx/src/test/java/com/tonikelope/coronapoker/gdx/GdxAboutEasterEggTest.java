package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GdxAboutEasterEggTest {

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
