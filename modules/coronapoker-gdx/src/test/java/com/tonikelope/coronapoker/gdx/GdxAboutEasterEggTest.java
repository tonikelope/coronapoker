package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
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

    private static byte[] resource(String path) throws Exception {
        try (var stream = GdxAboutEasterEggTest.class
                .getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }
}
