package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GdxRabbitPresentationContractTest {

    @Test
    void coveredRabbitCardsIgnoreTheEndedHandDisabledFade() {
        assertEquals(1f, CoronaPokerGdxTable.rabbitRestingAlpha(true, 0.30f));
        assertEquals(0.30f,
                CoronaPokerGdxTable.rabbitRestingAlpha(false, 0.30f));
    }

    @Test
    void packagesTheOriginalSwingRabbitCardArtwork() throws Exception {
        BufferedImage covered = image("/images/bugs2.png");
        BufferedImage revealed = image("/images/bugs2_b.png");

        assertEquals(484, covered.getWidth());
        assertEquals(652, covered.getHeight());
        assertEquals(covered.getWidth(), revealed.getWidth());
        assertEquals(covered.getHeight(), revealed.getHeight());
        assertTrue(revealed.getColorModel().hasAlpha(),
                "the revealed Rabbit peel must overlay the card face");
    }

    @Test
    void rabbitPeelShaderHonorsTheSpriteBatchSamplerContract() {
        String shader = CoronaPokerGdxTable.RABBIT_PEEL_FRAGMENT_SHADER;

        assertTrue(shader.contains("uniform sampler2D u_texture;"));
        assertTrue(shader.contains("texture2D(u_texture, uv)"),
                "u_texture must remain active and cannot be optimized away");
    }

    private static BufferedImage image(String resource) throws Exception {
        try (var stream = GdxRabbitPresentationContractTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, resource);
            return image;
        }
    }
}
