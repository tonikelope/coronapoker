/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import java.util.Properties;

/** Shared explanatory text for every settings surface. */
final class GdxSettingsTooltips {

    record Content(String text,
            GdxSettingsContract.PerformanceImpact performanceImpact) {
    }

    private GdxSettingsTooltips() {
    }

    static Content content(String settingKey, Properties properties,
            GdxGameText text) {
        return content(settingKey, properties, text, false,
                GdxGameAudioResources.JokeBankMode.OFFICIAL);
    }

    static Content content(String settingKey, Properties properties,
            GdxGameText text, boolean modOverride) {
        return content(settingKey, properties, text, modOverride,
                modOverride
                        && GdxGameAudioResources.jokeCategory(settingKey)
                                != null
                        ? GdxGameAudioResources.JokeBankMode.MOD_ONLY
                        : GdxGameAudioResources.JokeBankMode.OFFICIAL);
    }

    static Content content(String settingKey, Properties properties,
            GdxGameText text, boolean modOverride,
            GdxGameAudioResources.JokeBankMode jokeBankMode) {
        String jokeResource = GamePresentationSettings.jokeSoundResource(
                settingKey);
        String description;
        boolean jokeBankSetting = false;
        if (jokeResource == null) {
            description = text.translate(descriptionKey(settingKey));
            String jokeBank = GdxGameAudioResources.jokeBankFolder(
                    settingKey, text.language());
            if (jokeBank != null) {
                jokeBankSetting = true;
                description += " " + text.translate(
                        switch (jokeBankMode) {
                            case MOD_ONLY ->
                                "gdx.settings.tooltip.joke_bank_mod_only";
                            case FUSED ->
                                "gdx.settings.tooltip.joke_bank_fused";
                            case OFFICIAL ->
                                "gdx.settings.tooltip.joke_bank_official";
                        },
                        jokeBank);
            }
        } else {
            String resource = "sounds/" + jokeResource.replace('\\', '/');
            String folder = GdxGameAudioResources.jokeBankFolderForResource(
                    jokeResource);
            description = text.translate(modOverride
                    ? "gdx.settings.tooltip.joke_sound_item_mod"
                    : "gdx.settings.tooltip.joke_sound_item_official",
                    resource, folder);
        }
        if (jokeResource == null) {
            java.util.List<String> resources =
                    GdxGameAudioResources.settingResources(settingKey,
                            text.language());
            if (!resources.isEmpty()) {
                String paths = resources.stream()
                        .map(resource -> "sounds/"
                                + resource.replace('\\', '/'))
                        .collect(java.util.stream.Collectors.joining(", "));
                description += " " + text.translate(
                        "gdx.settings.tooltip.mod_replacement_path", paths);
            }
        }
        GdxSettingsContract.PerformanceImpact impact =
                GdxSettingsContract.performanceImpact(settingKey,
                        properties);
        String complete = impact == GdxSettingsContract.PerformanceImpact.NONE
                ? description
                : text.translate("gdx.settings.tooltip.with_impact",
                        description, impact.label(text));
        String displayed = modOverride && jokeResource == null
                && !jokeBankSetting
                ? complete + " " + text.translate(
                        "gdx.settings.tooltip.mod_audio_override")
                : complete;
        return new Content(displayed, impact);
    }

    static String descriptionKey(String settingKey) {
        if (settingKey == null || settingKey.isBlank()) {
            return "gdx.settings.tooltip.generic";
        }
        if (GamePresentationSettings.jokeSoundResource(settingKey) != null) {
            return "gdx.settings.tooltip.joke_sound_item_official";
        }
        return switch (settingKey) {
            case "master_volume" -> "tooltip.cfg.volume";
            case "sonidos" -> "tooltip.cfg.sound_master";
            case "musica" -> "tooltip.cfg.music_master";
            case "sonido_efectos" -> "tooltip.cfg.fx_master";
            case "sonido_cinematicas" ->
                "gdx.settings.tooltip.cinematic_audio";
            case "sonidos_chorra" -> "tooltip.cfg.silly_sounds";
            case "sonido_chorra_allin" ->
                "gdx.settings.tooltip.joke_allin";
            case "sonido_chorra_fold" ->
                "gdx.settings.tooltip.joke_fold";
            case "sonido_chorra_showdown" ->
                "gdx.settings.tooltip.joke_showdown";
            case "sonido_chorra_winner" ->
                "gdx.settings.tooltip.joke_winner";
            case "sonido_chorra_loser" ->
                "gdx.settings.tooltip.joke_loser";
            case "sonido_chorra_raise" ->
                "gdx.settings.tooltip.joke_raise";
            case "sonido_chorra_norebuy" ->
                "gdx.settings.tooltip.joke_norebuy";
            case "sonido_chorra_indivisible" ->
                "gdx.settings.tooltip.joke_indivisible";
            case "sonido_chorra_lastcard" ->
                "gdx.settings.tooltip.joke_lastcard";
            case "sonido_chorra_badbeat" ->
                "gdx.settings.tooltip.joke_badbeat";
            case "sonido_chorra_lucky" ->
                "gdx.settings.tooltip.joke_lucky";
            case "sonido_chorra_special_card" ->
                "gdx.settings.tooltip.joke_special_card";
            case "sonido_inicio" -> "tooltip.cfg.game_start_sound";
            case "musica_recuperacion" ->
                "tooltip.cfg.recovery_music";
            case "sonido_ascensor", "musica_sala_espera", "musica_about",
                    "musica_stats", "musica_gestor_bots" ->
                "tooltip.cfg.music_surface";
            case "voice_messages" -> "tooltip.cfg.voice_notes_rule";
            case "tts_server" -> "tooltip.cfg.tts";
            case "audio_block_voice_messages" -> "tooltip.cfg.notes_local";
            case "audio_play_own_voice" -> "tooltip.cfg.play_own_notes";
            case "audio_voice_note_retention_days" ->
                "tooltip.cfg.notes_retention";
            case "voice_notes_view" -> "tooltip.cfg.view_notes";
            case "voice_notes_purge" -> "tooltip.cfg.purge_notes";
            case "baraja" -> "tooltip.cfg.deck";
            case "trasera" -> "tooltip.cfg.deck_back";
            case "color_tapete" -> "tooltip.cfg.table_felt";
            case "nivel_luz" -> "tooltip.cfg.nivel_luz";
            case "show_time" -> "gdx.settings.tooltip.show_time";
            case "mostrar_coste_igualar" ->
                "gdx.settings.tooltip.show_call_cost";
            case "resaltar_jugada_showdown" ->
                "tooltip.cfg.resaltar_jugada_showdown";
            case "resaltar_avatares" ->
                "tooltip.cfg.resaltar_avatares";
            case "screenshot_fin_timba" ->
                "tooltip.cfg.screenshot_fin_timba";
            case GdxSettingsContract.ANTI_DOS_WARNINGS_KEY ->
                "gdx.settings.tooltip.anti_dos_warnings";
            case GdxSettingsContract.CHAT_GAME_NOTIFICATIONS_KEY ->
                "gdx.settings.tooltip.chat_notifications";
            case "chat_images_ingame" ->
                "gdx.settings.tooltip.chat_images";
            case "animaciones" ->
                "gdx.settings.tooltip.animations_master";
            case GdxSettingsContract.POKER_VIDEOS_KEY ->
                "gdx.settings.tooltip.poker_videos";
            case GdxSettingsContract.POKER_VIDEO_AUTO_OPEN_KEY ->
                "gdx.settings.tooltip.poker_video_auto_open";
            case GdxSettingsContract.POKER_VIDEO_WINDOW_ANIMATION_KEY ->
                "gdx.settings.tooltip.poker_video_window_animation";
            case GdxSettingsContract.HALO_EFFECT_KEY ->
                "gdx.settings.tooltip.halo_effects";
            case GdxSettingsContract.MODAL_BLUR_KEY ->
                "gdx.settings.tooltip.modal_blur";
            case GdxSettingsContract.GLASS_EFFECT_KEY ->
                "gdx.settings.tooltip.glass_effects";
            case "cinematicas", "cinematicas_accion" ->
                "gdx.settings.tooltip.cinematics_master";
            case "cinematicas_allin" ->
                "gdx.settings.tooltip.cinematic_allin";
            case "cinematicas_gameover" ->
                "gdx.settings.tooltip.cinematic_gameover";
            case "animacion_barajado" ->
                "gdx.settings.tooltip.animation_shuffle";
            case "card_flip_direction" ->
                "tooltip.cfg.card_flip_direction";
            case "animacion_barajado_precarga" ->
                "gdx.settings.tooltip.shuffle_preload";
            case "animacion_reparto" ->
                "gdx.settings.tooltip.animation_deal";
            case "reparto_velocidad" ->
                "tooltip.cfg.reparto_velocidad";
            case GdxSettingsContract.COMMUNITY_DEAL_STYLE_KEY ->
                "tooltip.cfg.community_deal_style";
            case "animacion_destape" ->
                "gdx.settings.tooltip.animation_reveal";
            case "card_flip_duration" ->
                "tooltip.cfg.card_flip_duration";
            case "card_flip_zoom" -> "tooltip.cfg.card_flip_zoom";
            case "animacion_ciegas_dealer" ->
                "gdx.settings.tooltip.animation_position_chips";
            case "animacion_contadores" ->
                "gdx.settings.tooltip.animation_counters";
            case "animacion_entrada_asientos" ->
                "gdx.settings.tooltip.animation_seats";
            case "animacion_swap" ->
                "gdx.settings.tooltip.animation_sort_hand";
            case "swap_velocidad" -> "tooltip.cfg.swap_velocidad";
            case "swap_arco" -> "tooltip.cfg.swap_estilo";
            case GdxSettingsContract.FINAL_SUMMARY_CARDS_ANIMATION_KEY ->
                "gdx.settings.tooltip.animation_final_summary_cards";
            case "animacion_contador_final" ->
                "gdx.settings.tooltip.animation_final_count";
            case "animacion_fuego_allin" ->
                "gdx.settings.tooltip.animation_allin_fire";
            case "animacion_efecto_ganador" ->
                "gdx.settings.tooltip.animation_winner";
            case "animacion_apuestas" ->
                "gdx.settings.tooltip.animation_chips";
            case "animacion_downgrade" ->
                "gdx.settings.tooltip.animation_reseat";
            case "downgrade_velocidad" ->
                "tooltip.cfg.downgrade_velocidad";
            case "animacion_cascada_overlay" ->
                "gdx.settings.tooltip.animation_shuffle_cascade";
            case "anim_calidad" -> "tooltip.cfg.anim_calidad";
            case "gdx_window_mode" -> "tooltip.cfg.display_mode";
            case "auto_fullscreen" ->
                "gdx.settings.tooltip.auto_fullscreen";
            case "gdx_vsync" -> "gdx.settings.tooltip.vsync";
            case "gdx_fps_limit" -> "gdx.settings.tooltip.fps_limit";
            case "gdx_msaa_samples" -> "gdx.settings.tooltip.msaa";
            case GdxSettingsContract.CARD_RENDERING_KEY ->
                "gdx.settings.tooltip.card_rendering";
            case "gdx_show_fps" -> "gdx.settings.tooltip.show_fps";
            case "gdx_show_cpu_memory" ->
                "gdx.settings.tooltip.show_cpu_memory";
            case "gpu_renderer" -> "gdx.settings.tooltip.gpu_renderer";
            case "gdx_audio_output_device" ->
                "gdx.settings.tooltip.audio_output";
            case "audio_capture_device" ->
                "gdx.settings.tooltip.audio_capture";
            case "audio_mic_enabled" ->
                "gdx.settings.tooltip.microphone";
            case GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY ->
                "gdx.settings.tooltip.mic_auto_gain";
            case "audio_block_tts_local" ->
                "gdx.settings.tooltip.local_tts";
            case "auto_action_buttons" ->
                "gdx.settings.tooltip.auto_buttons";
            case "auto_call_enabled" ->
                "gdx.settings.tooltip.auto_call";
            case "auto_action_persist" ->
                "gdx.settings.tooltip.auto_persist";
            case "modo_auto_confirm" ->
                "gdx.settings.tooltip.auto_confirm";
            case "confirmar_todo" ->
                "gdx.settings.tooltip.confirm_actions";
            case "auto_rebuy" -> "gdx.settings.tooltip.auto_rebuy";
            case "shortcut" -> "gdx.settings.tooltip.shortcut";
            case "structure" -> "tooltip.cfg.structure";
            case "blind_structures" -> "gdx.settings.tooltip.blind_structures";
            case "initial_blinds" -> "tooltip.cfg.blinds_level";
            case "blind_unit" -> "gdx.settings.tooltip.blind_unit";
            case "blind_interval" -> "gdx.settings.tooltip.blind_interval";
            case "ante" -> "tooltip.cfg.ante";
            case "straddle" -> "tooltip.cfg.straddle";
            case GdxSettingsContract.STRADDLE_ON_TIMEOUT_KEY ->
                "tooltip.cfg.straddle_on_timeout";
            case "increase_blinds" -> "tooltip.cfg.double_blinds";
            case "blind_cap", "blind_cap_level" ->
                "tooltip.cfg.blind_cap";
            case "fixed_buyin" -> "tooltip.cfg.buyin_fixed";
            case "initial_buyin" -> "tooltip.cfg.buyin";
            case "minimum_buyin", "maximum_buyin" ->
                "tooltip.cfg.buyin_range";
            case "rebuy" -> "gdx.settings.tooltip.rebuy";
            case "maximum_rebuys", "player_rebuy_limit" ->
                "tooltip.cfg.rebuy_limit";
            case "rebuy_cap" -> "gdx.settings.tooltip.rebuy_cap";
            case "bot_difficulty" -> "tooltip.cfg.bots";
            case "bot_rebuy" -> "tooltip.cfg.bot_rebuy";
            case "bot_balance" -> "tooltip.cfg.bot_balance";
            case "custom_bots" -> "tooltip.cfg.custom_bots";
            case "custom_bots_only" -> "tooltip.cfg.custom_bots.only";
            case "custom_bots_manage" -> "tooltip.cfg.custom_bots.manage";
            case "hand_limit", "hand_count" -> "tooltip.cfg.hand_limit";
            case "think_time", "think_seconds" -> "tooltip.cfg.think_time";
            case "showdown_time" -> "tooltip.cfg.showdown_time";
            case "allow_hot_join" -> "tooltip.cfg.allow_hot_join";
            case "iwtsth" -> "tooltip.cfg.iwtsth";
            case "run_it_twice" -> "tooltip.cfg.rit";
            case "rabbit_hunting" -> "tooltip.cfg.rabbit";
            default -> categorizedDescriptionKey(settingKey);
        };
    }

    private static String categorizedDescriptionKey(String settingKey) {
        if (settingKey.startsWith("sonido_")
                || settingKey.startsWith("sonidos_")
                || settingKey.startsWith("musica_")) {
            return "gdx.settings.tooltip.audio_event";
        }
        if (settingKey.startsWith("animacion_")
                || settingKey.startsWith("card_flip_")
                || settingKey.startsWith("swap_")) {
            return "gdx.settings.tooltip.animation";
        }
        if (settingKey.startsWith("cinematicas")) {
            return "gdx.settings.tooltip.cinematic";
        }
        if (settingKey.startsWith("chat_")
                || settingKey.equals("show_time")
                || settingKey.startsWith("mostrar_")
                || settingKey.startsWith("resaltar_")
                || settingKey.startsWith("screenshot_")
                || settingKey.equals(GdxSettingsContract.ANTI_DOS_WARNINGS_KEY)) {
            return "gdx.settings.tooltip.information";
        }
        return "gdx.settings.tooltip.generic";
    }
}
