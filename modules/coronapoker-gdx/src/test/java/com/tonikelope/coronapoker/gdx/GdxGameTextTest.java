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
        assertEquals("Confirm all actions",
                text.translate("gdx.settings.game.row.confirm_actions"));
        assertEquals("Force players to reconnect",
                text.translate("gdx.settings.game.session.action.force_reconnect"));
        assertEquals("Unsaved changes",
                text.translate("gdx.settings.unsaved.title"));
        assertEquals("Waiting room", text.translate("gdx.lobby.title"));
        assertEquals("Room closed", text.translate("gdx.lobby.closed"));
        assertEquals("UPnP active",
                GdxFrontendScreen.lobbyNetworkStatusText(
                        "UPNP_OK", text));
        assertEquals("UPnP unavailable",
                GdxFrontendScreen.lobbyNetworkStatusText(
                        "UPNP_ERROR", text));
        assertEquals("", GdxFrontendScreen.lobbyNetworkStatusText(
                "", text));
        assertEquals("Paste an image or GIF URL",
                text.translate("gdx.lobby.image_url_placeholder"));
        assertEquals("Page 2 / 58",
                text.translate("gdx.lobby.page", 2, 58));
        assertEquals("Recording - press stop to send",
                text.translate("gdx.lobby.voice_recording"));
        assertEquals("The voice message could not be played",
                text.translate("gdx.lobby.voice_playback_failed"));
        assertEquals("Preparing the table",
                text.translate("gdx.lobby.preparing_table"));
        assertEquals("Preparing the waiting room…",
                text.translate("gdx.newgame.preparing_waiting_room"));
        assertEquals("Connecting to the waiting room…",
                text.translate("gdx.newgame.connecting_waiting_room"));
        assertEquals("Select avatar", text.translate("gdx.avatar.select"));
        assertEquals("No previous servers",
                text.translate("gdx.newgame.no_previous_servers"));
        assertEquals("The table could not be opened",
                text.translate("gdx.table.open_failed"));
        assertEquals("Fast chat", text.translate("chat.chat_rapido"));
        assertEquals("Unavailable", text.translate("gdx.quick.unavailable"));
        assertEquals("CLICK OR PRESS ESC TO CLOSE",
                text.translate("gdx.card_viewer.close_hint"));
        assertEquals("THE SCREENSHOT FOLDER COULD NOT BE READ",
                text.translate("gdx.screenshot.folder_failed"));
        assertEquals("CLOSE AFTER SENDING",
                text.translate("gdx.table.chat.close_on_send"));
        assertEquals("RESUME", text.translate("gdx.table.resume"));
        assertEquals("Probability  1 in 46",
                text.translate("gdx.hand_generator.probability", 46));
        assertEquals("Previous",
                text.translate("gdx.hand_generator.previous"));
        assertEquals("Where did this come from?",
                text.translate("about.titulo"));
        assertEquals("Handmade in Spain and with love by tonikelope (c) 2020",
                text.translate("about.hecho_a_mano"));

        assertEquals("es", text.setLanguage("not-a-language"));
        assertEquals("CREAR TIMBA", text.translate("game.crear_timba"));
        assertEquals("Aumentar ciegas",
                text.translate("gdx.settings.game.row.increase_blinds"));
        assertEquals("Rango mínimo (CG)",
                text.translate("gdx.settings.game.row.minimum_range_bb"));
        assertEquals("Máximo de recompras",
                text.translate("gdx.settings.game.row.maximum_rebuys"));
        assertEquals("Confirmar todas las acciones",
                text.translate("gdx.settings.game.row.confirm_actions"));
        assertEquals("Forzar reconexión de jugadores",
                text.translate("gdx.settings.game.session.action.force_reconnect"));
        assertEquals("Cambios sin guardar",
                text.translate("gdx.settings.unsaved.title"));
        assertEquals("No se pueden guardar más de 10 perfiles",
                text.translate("gdx.newgame.profile_limit", 10));
        assertEquals("Preparando la sala de espera…",
                text.translate("gdx.newgame.preparing_waiting_room"));
        assertEquals("Conectando con la sala de espera…",
                text.translate("gdx.newgame.connecting_waiting_room"));
        assertEquals("2 de 5",
                text.translate("gdx.newgame.blind_editor.level_count", 2, 5));
        assertEquals("MÍNIMO", text.translate("gdx.dialog.minimum"));
        assertEquals("SIN MENSAJES",
                text.translate("gdx.table.chat.no_messages"));
        assertEquals("REANUDAR", text.translate("gdx.table.resume"));
        assertEquals("Probabilidad  1 entre 46",
                text.translate("gdx.hand_generator.probability", 46));
        assertEquals("¿De dónde ha salido esto?",
                text.translate("about.titulo"));
        assertEquals("Hecho a mano en España y con amor por tonikelope (c) 2020",
                text.translate("about.hecho_a_mano"));
    }
}
