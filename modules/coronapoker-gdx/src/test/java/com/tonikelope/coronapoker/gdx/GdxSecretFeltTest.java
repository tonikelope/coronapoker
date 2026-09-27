package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GdxSecretFeltTest {

    @Test
    void decodesTheOriginalSwingSecretFeltAsOneFullImage() throws Exception {
        byte[] decoded = GdxSecretFelt.decode();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(decoded));

        assertNotNull(image);
        assertTrue(image.getWidth() >= 1024);
        assertTrue(image.getHeight() >= 576);
    }
}
