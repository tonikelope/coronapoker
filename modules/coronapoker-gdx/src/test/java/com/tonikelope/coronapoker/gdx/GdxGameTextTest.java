package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxGameTextTest {

    @Test
    void languageCanChangeImmediatelyWithoutReplacingCoreConsumers() {
        GdxGameText text = new GdxGameText("es");
        assertEquals("CREAR TIMBA", text.translate("game.crear_timba"));

        assertEquals("en", text.setLanguage("en"));
        assertEquals("CREATE GAME", text.translate("game.crear_timba"));
        assertEquals("English", text.translate("gdx.language_name"));
        assertEquals("Increase blinds",
                text.translate("gdx.settings.game.row.increase_blinds"));
        assertEquals("Minimum range (BB)",
                text.translate("gdx.settings.game.row.minimum_range_bb"));
        assertEquals("Maximum range (BB)",
                text.translate("gdx.settings.game.row.maximum_range_bb"));
        assertEquals("Maximum rebuys",
                text.translate("gdx.settings.game.row.maximum_rebuys"));
        assertEquals("Free » small blind", text.translate("menu.free_sb"));
        assertEquals("3 raises · 1 / 2",
                text.translate("gdx.settings.game.value.raises", 3, "1 / 2"));

        assertEquals("es", text.setLanguage("not-a-language"));
        assertEquals("CREAR TIMBA", text.translate("game.crear_timba"));
        assertEquals("Aumentar ciegas",
                text.translate("gdx.settings.game.row.increase_blinds"));
        assertEquals("Rango mínimo (CG)",
                text.translate("gdx.settings.game.row.minimum_range_bb"));
        assertEquals("Máximo de recompras",
                text.translate("gdx.settings.game.row.maximum_rebuys"));
    }
}
