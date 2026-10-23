package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Texture.TextureFilter;
import org.junit.jupiter.api.Test;

final class CardRenderLabModelTest {

    @Test
    void defaultsToTheProposedNativeArtworkPlusSmaaCombination() {
        CardRenderLabModel model = new CardRenderLabModel();

        assertEquals("goliat", model.deck());
        assertEquals("Q_P", model.card());
        assertFalse(model.mode().smoothArtwork());
        assertTrue(model.mode().smaa());
        assertEquals(TextureFilter.MipMapLinearNearest,
                model.filter().minification());
        assertEquals(-0.5f, model.lodBias());
        assertEquals(15f, model.angleDegrees());
        assertEquals(140, model.cardWidth());
    }

    @Test
    void everySelectorWrapsInBothDirections() {
        CardRenderLabModel model = new CardRenderLabModel();

        model.change(CardRenderLabModel.Setting.DECK, -1);
        assertEquals("interstate60", model.deck());
        model.change(CardRenderLabModel.Setting.CARD, -3);
        assertEquals("trasera", model.card());
        model.change(CardRenderLabModel.Setting.MODE, -3);
        assertEquals("SUAVE 16X + SMAA", model.mode().label());
        model.change(CardRenderLabModel.Setting.FILTER, -2);
        assertEquals(TextureFilter.MipMapLinearLinear,
                model.filter().minification());
        model.change(CardRenderLabModel.Setting.LOD_BIAS, -3);
        assertEquals(0.5f, model.lodBias());
        model.change(CardRenderLabModel.Setting.ANGLE, -6);
        assertEquals(30f, model.angleDegrees());
        model.change(CardRenderLabModel.Setting.SIZE, -3);
        assertEquals(500, model.cardWidth());
    }

    @Test
    void resourcesFollowTheSelectedDeckAndCard() {
        CardRenderLabModel model = new CardRenderLabModel();
        model.change(CardRenderLabModel.Setting.DECK, 1);
        model.change(CardRenderLabModel.Setting.CARD, 1);

        assertEquals("images/decks/coronapoker/hq/J_P.jpg",
                model.cardResource());
        assertEquals("images/decks/coronapoker/hq/trasera.jpg",
                model.backResource());
        assertEquals(4, CardRenderLabModel.decks().size());
        assertEquals(54, CardRenderLabModel.cards().size());
    }

    @Test
    void largeCardSizesAndSettingTooltipsAreAvailable() {
        assertEquals(500, CardRenderLabModel.sizes().get(
                CardRenderLabModel.sizes().size() - 1));
        for (CardRenderLabModel.Setting setting
                : CardRenderLabModel.Setting.values()) {
            assertFalse(setting.tooltip().isBlank());
        }
    }

    @Test
    void lodExperimentUsesTheRealShaderUniformForAllArtworkSamples() {
        String shader = CoronaPokerGdxTable.CARD_FRAGMENT_SHADER;

        assertTrue(shader.contains("uniform float u_artworkLodBias"));
        assertEquals(17, occurrences(shader, "u_artworkLodBias);"));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }
}
