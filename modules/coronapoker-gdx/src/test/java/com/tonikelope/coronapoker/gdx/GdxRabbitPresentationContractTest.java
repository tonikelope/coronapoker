package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void rabbitCostChipMatchesTheOriginalSwingTableLifetimeRule() {
        assertEquals(CoronaPokerGdxTable.RabbitCostChipKind.NONE,
                CoronaPokerGdxTable.rabbitCostChipKind(1, 8));
        assertEquals(CoronaPokerGdxTable.RabbitCostChipKind.NONE,
                CoronaPokerGdxTable.rabbitCostChipKind(2, 0));
        assertEquals(CoronaPokerGdxTable.RabbitCostChipKind.SMALL_BLIND,
                CoronaPokerGdxTable.rabbitCostChipKind(2, 1));
        assertEquals(CoronaPokerGdxTable.RabbitCostChipKind.SMALL_BLIND,
                CoronaPokerGdxTable.rabbitCostChipKind(3, 1));
        assertEquals(CoronaPokerGdxTable.RabbitCostChipKind.BIG_BLIND,
                CoronaPokerGdxTable.rabbitCostChipKind(3, 2));
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
        assertTrue(shader.contains(
                "sampleCardArtwork(u_texture, uv)"),
                "u_texture must remain active and cannot be optimized away");
        assertTrue(shader.contains("* u_canvasScale + vec2(0.5)"),
                "Rabbit cards need the same transparent AA canvas as every other card");
        assertTrue(shader.contains("uniform vec4 u_highlight;"));
        assertTrue(shader.contains(
                "mix(pixel.rgb, u_highlight.rgb, u_highlight.a)"),
                "Rabbit cards must honor the existing yellow hand highlight");
    }

    @Test
    void rabbitHandHighlightTintsTheCoveredAndPeeledLayers()
            throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8);
        int start = source.indexOf("private void drawLiveRabbitCard(");
        int end = source.indexOf("private void drawRabbitCostChip(", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains(
                "drawRabbitCoveredCard(placement, alpha, showdownTint);"),
                "the yellow highlight must cover the still-covered card area");
        assertTrue(method.contains(
                "drawRabbitPeelLayer(liveCardFace(card.code()), placement, 1f,"),
                "the revealed Rabbit layer must remain highlighted too");
    }

    @Test
    void regularCardShaderKeepsArtworkScaleWhileExpandingOnlyItsAaCanvas() {
        String shader = CoronaPokerGdxTable.CARD_FRAGMENT_SHADER;

        assertTrue(shader.contains("uniform float u_canvasScale;"));
        assertTrue(shader.contains("* u_canvasScale + vec2(0.5)"));
        assertTrue(shader.contains("roundedCardCoverage(sourceUv)"));
        assertTrue(shader.contains("if (mask <= 0.001) discard;"),
                "transparent guard pixels should not perform texture reads");
        assertTrue(shader.contains(
                "sampleCardArtwork(u_texture, sourceUv)"));
        assertTrue(shader.contains(
                "sampleCardArtwork(u_frontTexture, sourceUv)"));
        assertTrue(shader.contains("texture2D(artwork, uv, -0.5)"),
                "all cards must share the half-mip detail policy");
        assertFalse(shader.contains("cardArtworkContrast"),
                "SMAA must replace the old colour-gradient heuristic");
        assertTrue(CoronaPokerGdxTable.RABBIT_PEEL_FRAGMENT_SHADER.contains(
                "sampleCardArtwork(u_overlayTexture, uv)"));
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
