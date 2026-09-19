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
        assertEquals("Previous game loaded · game 7",
                text.translate("gdx.newgame.recover_loaded",
                        text.translate("gdx.newgame.recovered_game", 7)));
        assertEquals("2 of 5",
                text.translate("gdx.newgame.blind_editor.level_count", 2, 5));
        assertEquals("Create and adjust the blind schedules available for this game",
                text.translate("gdx.newgame.blind_editor.help"));
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
        assertEquals("No se pueden guardar más de 10 perfiles",
                text.translate("gdx.newgame.profile_limit", 10));
        assertEquals("2 de 5",
                text.translate("gdx.newgame.blind_editor.level_count", 2, 5));
    }
}
