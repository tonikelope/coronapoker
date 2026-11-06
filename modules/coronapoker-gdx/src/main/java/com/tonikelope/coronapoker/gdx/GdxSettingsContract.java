/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Shared contract for the GDX settings surface.
 *
 * <p>The menu and the live table must not maintain independent lists of
 * preference keys.  This class is deliberately UI-free so both renderers can
 * use the same transactional snapshot while the common visual surface is
 * extracted.</p>
 */
final class GdxSettingsContract {

    static final float DEFAULT_MASTER_VOLUME = 0.8f;
    static final boolean DEFAULT_VSYNC = true;
    static final int DEFAULT_FRAME_RATE_LIMIT = 120;
    /** Persisted under its legacy SMAA key to preserve existing preferences. */
    static final String CARD_RENDERING_KEY = "gdx_card_smaa_quality";
    static final String HALO_EFFECT_KEY = "efecto_halos";
    static final String MODAL_BLUR_KEY = "efecto_desenfoque";
    static final String GLASS_EFFECT_KEY = "efecto_relieve";
    static final String POKER_VIDEOS_KEY = "mostrar_videos_poker";
    static final String AUTO_REBUY_KEY = "auto_rebuy";
    static final int DEFAULT_CARD_RENDERING_MODE =
            GdxCardSmaaRenderer.RENDERING_SMOOTH;
    static final int APPEARANCE_PRIMARY_ROW_COUNT = 4;
    static final int APPEARANCE_GRAPHICS_ROW_COUNT = 8;
    /** AUTO/action rows rendered before the editable shortcut bindings. */
    static final int CONTROL_ROWS = 6;
    static final int BLIND_STRUCTURE_ROW = 0;
    static final int BLIND_INITIAL_ROW = 1;
    static final int BLIND_MANAGE_ROW = 2;
    static final int BLIND_INCREASE_ROW = 3;
    static final int BLIND_UNIT_ROW = 4;
    static final int BLIND_INTERVAL_ROW = 5;
    static final int BLIND_CAP_TOGGLE_ROW = 6;
    static final int BLIND_CAP_ROW = 7;
    static final int BLIND_ANTE_ROW = 8;
    static final int BLIND_STRADDLE_ROW = 9;
    static final int BUYIN_MINIMUM_ROW = 0;
    static final int BUYIN_MAXIMUM_ROW = 1;
    static final int BUYIN_FIXED_ROW = 2;
    static final int BUYIN_INITIAL_ROW = 3;
    static final int BUYIN_REBUY_ROW = 4;
    static final int BUYIN_PLAYER_LIMIT_ROW = 5;
    static final int BUYIN_MAXIMUM_REBUYS_ROW = 6;
    static final int BUYIN_REBUY_CAP_ROW = 7;
    private static final List<String> CONTROL_SETTING_KEYS = List.of(
            "auto_action_buttons", "auto_call_enabled",
            "auto_action_persist", "modo_auto_confirm", "confirmar_todo",
            AUTO_REBUY_KEY);
    static final String CHAT_GAME_NOTIFICATIONS_KEY =
            "chat_game_notifications";
    static final String AUDIO_MIC_AUTO_GAIN_KEY = "audio_mic_auto_gain";
    static final String ANTI_DOS_WARNINGS_KEY = "anti_dos_warnings";
    private static final String LEGACY_GDX_CHAT_NOTIFICATIONS_KEY =
            "chat_notifications_ingame";

    enum Gate {
        NONE, MUSIC, EFFECTS, FUNNY_SOUNDS, ANIMATIONS, SHUFFLE_ANIMATION
    }

    enum PerformanceImpact {
        NONE, LOW, MEDIUM, HIGH;

        String label(GdxGameText text) {
            return translatedUpper(text,
                    "gdx.settings.performance_impact."
                            + name().toLowerCase(Locale.ROOT),
                    name());
        }
    }

    record ToggleOption(String key, String label, boolean fallback,
            Gate gate, boolean inverted, String parentKey) {

        ToggleOption(String key, String label, boolean fallback,
                Gate gate, boolean inverted) {
            this(key, label, fallback, gate, inverted,
                    defaultParentKey(gate));
        }

        String label(GdxGameText text) {
            return translatedUpper(text, "gdx.settings.option." + key,
                    label);
        }
    }

    record TogglePage(String title, List<ToggleOption> options) {

        String title(GdxGameText text) {
            return translatedUpper(text, pageTranslationKey(title), title);
        }
    }

    /** A contiguous parent/descendant block painted as one nested group. */
    record RowGroup(int parentRow, int lastRow, int depth) {

        RowGroup {
            if (parentRow < 0 || lastRow <= parentRow || depth < 0) {
                throw new IllegalArgumentException("Invalid settings group");
            }
        }
    }

    /**
     * One visual row in Appearance > Animations.  Switches and their related
     * selectors deliberately share this ordered model so the menu and the
     * live-table settings cannot drift into two different layouts.
     */
    record AppearanceAnimationRow(ToggleOption toggle,
            GdxAppearanceOptions.Choice choice) {

        AppearanceAnimationRow {
            if ((toggle == null) == (choice == null)) {
                throw new IllegalArgumentException(
                        "Exactly one animation row control is required");
            }
        }

        String key() {
            return toggle != null ? toggle.key() : choice.key();
        }

        boolean child() {
            return toggle != null
                    ? GdxSettingsContract.isChildOption(
                            APPEARANCE_ANIMATIONS_PAGE, toggle)
                    : GdxAppearanceOptions.isChildChoice(choice);
        }
    }

    record AudioPreview(String resource, float volume, float limitSeconds) {
    }

    static AudioPreview audioPreview(ToggleOption option, String language) {
        if (option == null) return null;
        String concreteJoke = GamePresentationSettings.jokeSoundResource(
                option.key());
        if (concreteJoke != null) {
            return new AudioPreview(concreteJoke, 1f, 10f);
        }
        String resource = switch (option.key()) {
            case "sonido_ascensor" -> "misc/background_music.mp3";
            case "musica_sala_espera" -> "misc/waiting_room.mp3";
            case "musica_about" -> "misc/about_music.mp3";
            case "musica_stats" -> "misc/stats_music.mp3";
            case "musica_gestor_bots" -> "misc/bots_manager.mp3";
            case "musica_recuperacion" -> "misc/recovering.mp3";
            case "sonido_apostar" -> "misc/bet.wav";
            case "sonido_igualar" -> "misc/call.wav";
            case "sonido_pasar" -> "misc/check.wav";
            case "sonido_allin" -> "misc/allin.wav";
            case "sonido_fold" -> "misc/fold.wav";
            case "sonido_barajado" -> "misc/shuffle.wav";
            case "sonido_reparto" -> "misc/deal.wav";
            case "sonido_destape", "sonido_destape_mis_cartas" ->
                "misc/uncover.wav";
            case "sonido_entra" -> "misc/laser.wav";
            case "sonido_entrar_sala" -> "misc/new_user.wav";
            case "sonido_conexion" -> "misc/yahoo.wav";
            case "sonido_sale" -> "misc/toilet.wav";
            case "sonido_aviso" -> "misc/warning.wav";
            case "sonido_error" -> "misc/danger_alert.wav";
            case "sonido_error_red" -> "misc/network_error_"
                    + ("en".equalsIgnoreCase(language) ? "en" : "es")
                    + ".wav";
            case "sonido_inicio" -> "misc/startplay.wav";
            case "sonido_ciegas" -> "misc/double_blinds.wav";
            case "sonido_caja" -> "misc/cash_register.wav";
            case "sonido_ultima_mano" -> "misc/last_hand_on.wav";
            case "sonido_conteo", "sonido_carga_stacks" ->
                "misc/balance_count.wav";
            case "sonido_iwtsth" -> "misc/showyourcards.wav";
            case "sonido_interruptor" -> "misc/button_on.wav";
            case "sonido_pausa" -> "misc/pause.wav";
            case "sonido_fin_partida" -> "misc/game_over.wav";
            case "sonido_tu_turno" -> "misc/yourturn.wav";
            case "sonido_aviso_tiempo" -> "misc/hurryup.wav";
            case "sonido_zoom" -> "misc/zoom_in.wav";
            case "sonido_screenshot" -> "misc/screenshot.wav";
            case "sonido_tapete" -> "misc/mat.wav";
            case "sonido_visor" -> "misc/card_visor.wav";
            case "sonido_volumen" -> "misc/volume_change.wav";
            case "sonido_arranque" -> "misc/init.wav";
            case "sonido_chorra_raise" -> GdxSoundFeedback.FUNNY_RAISE;
            case "sonido_chorra_norebuy" -> GdxSoundFeedback.FUNNY_NO_REBUY;
            case "sonido_chorra_indivisible" -> "misc/indivisible.wav";
            case "sonido_chorra_lastcard" -> "misc/lastcard.wav";
            case "sonido_chorra_badbeat" -> "misc/badbeat.wav";
            case "sonido_chorra_lucky" -> "misc/youarelucky.wav";
            default -> null;
        };
        if (resource == null) return null;
        float volume = switch (option.key()) {
            case "sonido_ascensor" -> 0.4f;
            case "musica_sala_espera", "musica_about" -> 0.9f;
            case "musica_stats" -> 0.3f;
            case "musica_gestor_bots" -> 0.65f;
            case "musica_recuperacion" -> 0.4f;
            default -> 1f;
        };
        float limit = "sonido_carga_stacks".equals(option.key())
                ? 1f : 10f;
        return new AudioPreview(resource, volume, limit);
    }

    enum Section {
        APPEARANCE("APARIENCIA", "settings.tab_apariencia"),
        AUDIO("AUDIO", "settings.tab_audio"),
        GAME("JUEGO", "settings.tab_partida"),
        SHORTCUTS("CONTROLES", "settings.tab_atajos"),
        DEBUG("DEBUG", "settings.tab_debug");

        private final String label;
        private final String translationKey;

        Section(String label, String translationKey) {
            this.label = label;
            this.translationKey = translationKey;
        }

        String label() {
            return label;
        }

        String label(GdxGameText text) {
            return text.translate(translationKey).toUpperCase(
                    java.util.Locale.forLanguageTag(text.language()));
        }
    }

    /** Shared order; GAME edits future-table defaults from the main menu. */
    static final List<Section> MENU_SECTIONS = List.of(
            Section.APPEARANCE, Section.AUDIO, Section.SHORTCUTS,
            Section.GAME, Section.DEBUG);
    static final List<Section> TABLE_SECTIONS = List.of(
            Section.APPEARANCE, Section.AUDIO, Section.SHORTCUTS,
            Section.GAME, Section.DEBUG);

    /**
     * Context-specific Game pages from Swing's two mutually exclusive panels.
     * The chrome is shared, but the waiting room exposes the complete pre-game
     * draft while the running table exposes only actual game settings. Runtime
     * session operations belong exclusively to the table's fast-access bar.
     */
    static final List<String> WAITING_ROOM_GAME_PAGES = List.of(
            "CIEGAS", "COMPRA", "BOTS", "REGLAS");
    /**
     * The live table exposes the same semantic pages, order and hierarchy as
     * the waiting room. Controls which cannot safely change mid-hand are
     * disabled in place instead of moving into a different two-column UI.
     */
    static final List<String> LIVE_TABLE_GAME_PAGES =
            List.copyOf(WAITING_ROOM_GAME_PAGES);

    static List<String> subpageLabels(Section section,
            List<String> gamePages, int shortcutEntries,
            int shortcutRowsPerPage) {
        return subpageLabels(section, gamePages, shortcutEntries,
                shortcutRowsPerPage, null);
    }

    static List<String> subpageLabels(Section section,
            List<String> gamePages, int shortcutEntries,
            int shortcutRowsPerPage, GdxGameText text) {
        return switch (section) {
            case APPEARANCE -> {
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                labels.add(translatedUpper(text, "gdx.settings.page.table",
                        "MESA"));
                APPEARANCE_PAGES.stream().map(page -> page.title(text))
                        .forEach(labels::add);
                labels.add(translatedUpper(text, "gdx.settings.page.graphics",
                        "GRÁFICOS"));
                yield List.copyOf(labels);
            }
            case AUDIO -> AUDIO_PAGES.stream().map(page -> page.title(text))
                    .toList();
            case GAME -> gamePages.stream()
                    .map(page -> translatedUpper(text,
                            gamePageTranslationKey(page), page))
                    .toList();
            // Controls is already the primary tab. A second, identically
            // named tab added no navigation value and made the AUTO controls
            // look as if they belonged to the Game section.
            case SHORTCUTS -> List.of();
            case DEBUG -> List.of(translatedUpper(text,
                    "gdx.settings.page.technical_log",
                    "REGISTRO TÉCNICO"));
        };
    }

    static String contentHeading(Section section, String subpage) {
        String page = subpage == null ? "" : subpage.strip();
        // The selected primary and secondary tabs already communicate the
        // hierarchy. Repeating "AUDIO / GENERAL" (and equivalents) wastes
        // vertical space and makes the content look like a third navigation
        // level. Keep only the useful leaf heading.
        return page.isEmpty() ? section.label() : page;
    }

    /** Sections whose Swing counterpart owns a transactional reset action. */
    static boolean hasRestoreDefaults(Section section) {
        return section == Section.APPEARANCE
                || section == Section.AUDIO
                || section == Section.SHORTCUTS;
    }

    /** Audio controls present in Swing and applicable to the GDX frontend. */
    private static final TogglePage AUDIO_VOICE_CHAT_PAGE = page(
            "NOTAS Y CHAT DE VOZ",
            option("voice_messages", "NOTAS DE VOZ", true,
                    Gate.NONE),
            invertedOption("audio_block_voice_messages",
                    "NOTAS DE VOZ (LOCAL)", false, Gate.NONE,
                    "voice_messages"),
            option("audio_play_own_voice", "REPRODUCIR MIS NOTAS", true,
                    Gate.NONE, "voice_messages"),
            option("tts_server", "VOZ (TTS)", true, Gate.NONE),
            invertedOption("audio_block_tts_local", "TTS (LOCAL)", false,
                    Gate.NONE, "tts_server"));
    private static final TogglePage APPEARANCE_TABLE_PAGE = page(
            "MESA",
            option("show_time", "MOSTRAR RELOJ DURANTE LA PARTIDA",
                    false, Gate.NONE),
            option("mostrar_coste_igualar", "COSTE DE IGUALAR", true,
                    Gate.NONE),
            option("resaltar_jugada_showdown",
                    "RESALTAR JUGADA EN SHOWDOWN", true, Gate.NONE),
            option("resaltar_avatares", "RESALTAR AVATARES", false,
                    Gate.NONE),
            option("screenshot_fin_timba",
                    "CAPTURA AL TERMINAR LA TIMBA", false, Gate.NONE),
            option(ANTI_DOS_WARNINGS_KEY,
                    "MOSTRAR AVISOS ANTIDOS", true, Gate.NONE),
            option(CHAT_GAME_NOTIFICATIONS_KEY,
                    "NOTIFICACIONES DE CHAT DURANTE LA PARTIDA", true,
                    Gate.NONE),
            option("chat_images_ingame", "IMÁGENES DEL CHAT", true,
                    Gate.NONE));
    private static final TogglePage APPEARANCE_ANIMATIONS_PAGE =
            withoutUnavailablePokerVideo(page(
            "ANIMACIONES Y EFECTOS",
            option("animaciones", "USAR ANIMACIONES Y EFECTOS", true,
                    Gate.NONE),
            option(POKER_VIDEOS_KEY,
                    "MOSTRAR SHORTS DE PÓKER EN PRINCIPAL Y SALA DE ESPERA",
                    true, Gate.ANIMATIONS),
            option(HALO_EFFECT_KEY, "HALOS Y RESPLANDORES", true,
                    Gate.ANIMATIONS),
            option(MODAL_BLUR_KEY, "DESENFOQUE DE DIÁLOGOS", true,
                    Gate.ANIMATIONS),
            option(GLASS_EFFECT_KEY, "BRILLO Y RELIEVE", true,
                    Gate.ANIMATIONS),
            option("cinematicas_allin", "CINEMÁTICA ALL-IN", true,
                    Gate.ANIMATIONS),
            option("cinematicas_gameover", "CINEMÁTICA GAME OVER", true,
                    Gate.ANIMATIONS),
            option("animacion_barajado", "BARAJADO", true,
                    Gate.ANIMATIONS),
            option("animacion_barajado_precarga",
                    "PRECARGAR BARAJADO EN RAM", true,
                    Gate.SHUFFLE_ANIMATION),
            option("animacion_reparto", "REPARTO", true,
                    Gate.ANIMATIONS),
            option("animacion_destape", "DESTAPAR", true,
                    Gate.ANIMATIONS),
            option("animacion_ciegas_dealer", "FICHAS DE POSICIÓN", true,
                    Gate.ANIMATIONS),
            option("animacion_contadores", "CONTADORES", true,
                    Gate.ANIMATIONS),
            option("animacion_entrada_asientos", "MOVIMIENTO DE ASIENTOS", true,
                    Gate.ANIMATIONS),
            option("animacion_swap", "ORDENAR LA MANO", true,
                    Gate.ANIMATIONS),
            option("animacion_contador_final", "RECUENTO FINAL", true,
                    Gate.ANIMATIONS),
            option("animacion_fuego_allin", "FUEGO DE ALL-IN", true,
                    Gate.ANIMATIONS),
            option("animacion_efecto_ganador", "EFECTO DE GANADOR", true,
                    Gate.ANIMATIONS),
            option("animacion_apuestas", "FICHAS VOLADORAS", true,
                    Gate.ANIMATIONS)));

    private static final List<Integer> VOICE_RETENTION_DAYS = List.of(
            7, 15, 30, 90, 0);

    private static final List<TogglePage> AUDIO_SOURCE_PAGES = List.of(
            page("GENERAL",
                    option("sonidos", "SONIDO", true, Gate.NONE),
                    option("audio_mic_enabled", "MICRÓFONO", true,
                            Gate.NONE),
                    option(AUDIO_MIC_AUTO_GAIN_KEY,
                            "GANANCIA AUTOMÁTICA DEL MICRÓFONO", true,
                            Gate.NONE, "audio_mic_enabled")),
            page("MÚSICA",
                    option("musica", "MÚSICA", true, Gate.NONE),
                    option("sonido_ascensor", "AMBIENTE", true, Gate.MUSIC),
                    option("musica_sala_espera", "SALA DE ESPERA", true,
                            Gate.MUSIC),
                    option("musica_stats", "ESTADÍSTICAS", true,
                            Gate.MUSIC),
                    option("musica_gestor_bots", "GESTOR DE BOTS", true,
                            Gate.MUSIC),
                    option("musica_recuperacion", "RECUPERACIÓN DE TIMBAS",
                            true, Gate.MUSIC),
                    option("musica_about", "ACERCA DE", true, Gate.MUSIC)),
            page("ACCIONES",
                    option("sonido_efectos", "USAR EFECTOS DE SONIDO", true,
                            Gate.NONE),
                    option("sonido_cinematicas",
                            "REPRODUCIR AUDIO DE CINEMÁTICAS", true,
                            Gate.EFFECTS),
                    option("sonido_apostar", "APOSTAR", true, Gate.EFFECTS),
                    option("sonido_igualar", "IGUALAR", true, Gate.EFFECTS),
                    option("sonido_pasar", "PASAR", true, Gate.EFFECTS),
                    option("sonido_allin", "ALL-IN", true, Gate.EFFECTS),
                    option("sonido_fold", "RETIRARSE", true, Gate.EFFECTS)),
            page("CARTAS",
                    option("sonido_barajado", "BARAJAR", true, Gate.EFFECTS),
                    option("sonido_reparto", "REPARTIR", true, Gate.EFFECTS),
                    option("sonido_destape", "DESTAPAR", true, Gate.EFFECTS),
                    option("sonido_destape_mis_cartas",
                            "DESTAPAR MIS CARTAS", false, Gate.EFFECTS),
                    option("sonido_ciegas", "CIEGAS", true, Gate.EFFECTS)),
            page("PARTIDA",
                    option("sonido_conteo", "CONTEO", true, Gate.EFFECTS),
                    option("sonido_carga_stacks", "CARGA INICIAL DE STACKS",
                            true, Gate.EFFECTS),
                    option("sonido_caja", "RECOMPRA", true, Gate.EFFECTS),
                    option("sonido_ultima_mano", "MARCAR ÚLTIMA MANO", true,
                            Gate.EFFECTS),
                    option("sonido_pausa", "PAUSA", true, Gate.EFFECTS)),
            page("TURNO Y AVISOS",
                    option("sonido_tu_turno", "TU TURNO", true,
                            Gate.EFFECTS),
                    option("sonido_aviso_tiempo", "AVISO DE TIEMPO", true,
                            Gate.EFFECTS),
                    option("sonido_fin_partida", "FIN DE PARTIDA", true,
                            Gate.EFFECTS),
                    option("sonido_inicio", "INICIO DE PARTIDA", true,
                            Gate.EFFECTS),
                    option("sonido_iwtsth", "IWTSTH", true, Gate.EFFECTS)),
            page("SALA",
                    option("sonido_entra", "CREAR PARTIDA / NUEVO JUGADOR", true,
                            Gate.EFFECTS),
                    option("sonido_entrar_sala", "JUGADOR QUIERE ENTRAR", true,
                            Gate.EFFECTS),
                    option("sonido_conexion", "CONEXIÓN AL SERVIDOR", true,
                            Gate.EFFECTS),
                    option("sonido_sale", "JUGADOR SALE / EXPULSAR", true,
                            Gate.EFFECTS),
                    option("sonido_interruptor", "INTERRUPTOR", true,
                            Gate.EFFECTS)),
            page("PANTALLA",
                    option("sonido_screenshot", "CAPTURA DE PANTALLA", true,
                            Gate.EFFECTS),
                    option("sonido_tapete", "CAMBIAR TAPETE", true,
                            Gate.EFFECTS),
                    option("sonido_visor", "VISOR DE CARTAS", true,
                            Gate.EFFECTS),
                    option("sonido_zoom", "ZOOM DE AVATAR", true,
                            Gate.EFFECTS),
                    option("sonido_volumen", "CAMBIAR VOLUMEN", true,
                            Gate.EFFECTS)),
            page("SISTEMA",
                    option("sonido_arranque", "ARRANQUE DE LA APP", true,
                            Gate.EFFECTS),
                    option("sonido_aviso", "ADVERTENCIA", true,
                            Gate.EFFECTS),
                    option("sonido_error", "ALERTA DE PELIGRO", true,
                            Gate.EFFECTS),
                    option("sonido_error_red", "ERROR DE RED", true,
                            Gate.EFFECTS)),
            AUDIO_VOICE_CHAT_PAGE);

    private static final TogglePage AUDIO_FUNNY_SOUNDS_PAGE = page(
            "SONIDOS DE COÑA",
            option("sonidos_chorra", "USAR SONIDOS DE COÑA", false,
                    Gate.EFFECTS),
            option("sonido_chorra_allin", "ALL-IN", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_fold", "RETIRARSE", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_showdown", "SHOWDOWN", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_winner", "GANADOR", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_loser", "PERDEDOR", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_raise", "SUBIDA", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_norebuy", "SIN RECOMPRA", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_indivisible", "RESTO INDIVISIBLE", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_lastcard", "GANAR EN LA ÚLTIMA CARTA", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_badbeat", "BAD BEAT", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_lucky", "MANO AFORTUNADA", true,
                    Gate.FUNNY_SOUNDS),
            option("sonido_chorra_special_card", "CARTAS ESPECIALES", true,
                    Gate.FUNNY_SOUNDS));

    /**
     * Audio keeps only meaningful navigation groups.  Detailed Swing sound
     * families remain available as rows inside the scrollable EFFECTS page
     * instead of becoming a wall of tiny invented subcategories.
     */
    private static final TogglePage AUDIO_EFFECTS_PAGE = mergePages(
            "EFECTOS", AUDIO_SOURCE_PAGES.subList(2, 9));
    static final List<TogglePage> AUDIO_PAGES = List.of(
            AUDIO_SOURCE_PAGES.get(0),
            AUDIO_SOURCE_PAGES.get(1),
            AUDIO_EFFECTS_PAGE,
            AUDIO_FUNNY_SOUNDS_PAGE,
            AUDIO_SOURCE_PAGES.get(9));

    static TogglePage audioPage(int pageIndex,
            GdxGamePresentationSettings settings, String language) {
        int index = Math.max(0, Math.min(pageIndex, AUDIO_PAGES.size() - 1));
        TogglePage page = AUDIO_PAGES.get(index);
        return page == AUDIO_FUNNY_SOUNDS_PAGE
                ? GdxGameAudioResources.jokeSoundsPage(settings, language,
                        page)
                : page;
    }

    /** Appearance switches shared with Swing; choice controls are separate. */
    static final List<TogglePage> APPEARANCE_PAGES = List.of(
            APPEARANCE_ANIMATIONS_PAGE);

    static int appearanceGraphicsPageIndex() {
        return APPEARANCE_PAGES.size() + 1;
    }

    static boolean isAppearanceGraphicsPage(int pageIndex) {
        return pageIndex == appearanceGraphicsPageIndex();
    }

    static boolean isAppearanceTogglePage(int pageIndex) {
        return pageIndex > 0 && !isAppearanceGraphicsPage(pageIndex);
    }

    static TogglePage appearanceTogglePage(int pageIndex) {
        int index = Math.max(0, Math.min(pageIndex - 1,
                APPEARANCE_PAGES.size() - 1));
        return APPEARANCE_PAGES.get(index);
    }

    /**
     * Product order: each animation's speed, direction or style follows the
     * switch that owns it instead of being collected in a detached block.
     */
    private static final List<AppearanceAnimationRow>
            APPEARANCE_ANIMATION_ROWS = withoutUnavailablePokerVideoRows(
                    List.of(
                    animationToggle("animaciones"),
                    animationToggle(POKER_VIDEOS_KEY),
                    animationToggle(HALO_EFFECT_KEY),
                    animationToggle(MODAL_BLUR_KEY),
                    animationToggle(GLASS_EFFECT_KEY),
                    animationToggle("cinematicas_allin"),
                    animationToggle("cinematicas_gameover"),
                    animationToggle("animacion_barajado"),
                    animationToggle("animacion_barajado_precarga"),
                    animationToggle("animacion_reparto"),
                    animationChoice("reparto_velocidad"),
                    animationToggle("animacion_destape"),
                    animationChoice("card_flip_duration"),
                    animationChoice("card_flip_direction"),
                    animationChoice("card_flip_zoom"),
                    animationToggle("animacion_ciegas_dealer"),
                    animationToggle("animacion_contadores"),
                    animationToggle("animacion_entrada_asientos"),
                    animationToggle("animacion_swap"),
                    animationChoice("swap_velocidad"),
                    animationChoice("swap_arco"),
                    animationToggle("animacion_contador_final"),
                    animationToggle("animacion_fuego_allin"),
                    animationToggle("animacion_efecto_ganador"),
                    animationToggle("animacion_apuestas")));

    /**
     * Persisted values owned by the shared Swing/GDX settings transaction.
     * This is deliberately a superset of the currently displayed GDX rows:
     * Cancel must preserve hidden Swing-only values, and controls still under
     * migration are exposed only after their GDX consumer exists.
     */
    static final List<String> PREFERENCE_KEYS = List.of(
            "sonidos", "master_volume", "musica",
            "sonido_ascensor", "musica_sala_espera", "musica_about",
            "musica_stats", "musica_gestor_bots", "musica_recuperacion",
            "sonido_efectos",
            "sonido_cinematicas",
            "sonidos_chorra",
            "sonido_chorra_allin", "sonido_chorra_fold",
            "sonido_chorra_showdown", "sonido_chorra_winner",
            "sonido_chorra_loser", "sonido_chorra_raise",
            "sonido_chorra_norebuy", "sonido_chorra_indivisible",
            "sonido_chorra_lastcard", "sonido_chorra_badbeat",
            "sonido_chorra_lucky", "sonido_chorra_special_card",
            "sonido_barajado", "sonido_reparto", "sonido_destape",
            "sonido_destape_mis_cartas", "sonido_apostar", "sonido_fold",
            "sonido_conteo", "sonido_carga_stacks", "sonido_entra",
            "sonido_sale", "sonido_interruptor", "sonido_caja",
            "sonido_igualar", "sonido_pasar", "sonido_allin",
            "sonido_ciegas", "sonido_ultima_mano", "sonido_pausa",
            "sonido_entrar_sala", "sonido_tu_turno",
            "sonido_aviso_tiempo", "sonido_fin_partida", "sonido_inicio",
            "sonido_conexion", "sonido_iwtsth", "sonido_screenshot",
            "sonido_zoom", "sonido_vista_compacta", "sonido_tapete",
            "sonido_visor", "sonido_volumen",
            "sonido_arranque", "sonido_aviso", "sonido_error",
            "sonido_error_red", "tts_server", "voice_messages",
            "audio_output_device", "gdx_audio_output_device",
            "audio_capture_device",
            "audio_mic_enabled", AUDIO_MIC_AUTO_GAIN_KEY,
            "audio_play_own_voice",
            "audio_block_voice_messages", "audio_block_tts_local",
            "audio_voice_note_retention_days",
            "baraja", "trasera", "color_tapete", "nivel_luz",
            "show_time", "gdx_show_fps", "gdx_show_cpu_memory",
            "mostrar_coste_igualar",
            "chat_images_ingame", CHAT_GAME_NOTIFICATIONS_KEY,
            "resaltar_jugada_showdown", "resaltar_avatares",
            "screenshot_fin_timba", ANTI_DOS_WARNINGS_KEY,
            "animacion_contador_final",
            "animacion_fuego_allin", "animacion_efecto_ganador",
            HALO_EFFECT_KEY, MODAL_BLUR_KEY, GLASS_EFFECT_KEY,
            "animaciones", POKER_VIDEOS_KEY, "cinematicas",
            "cinematicas_accion",
            "cinematicas_allin", "cinematicas_gameover",
            "animacion_barajado", "animacion_barajado_precarga",
            "animacion_reparto",
            "animacion_destape", "animacion_ciegas_dealer",
            "animacion_apuestas",
            "animacion_contadores",
            "animacion_entrada_asientos", "animacion_cascada_overlay",
            "animacion_swap",
            "animacion_downgrade", "card_flip_duration",
            "card_flip_direction", "card_flip_zoom", "reparto_velocidad",
            "anim_calidad",
            "swap_velocidad", "swap_arco", "downgrade_velocidad",
            "zoom_level", "vista_compacta", "auto_zoom", "dialog_zoom",
            "auto_fullscreen", "gdx_window_mode", "gdx_vsync",
            "gdx_fps_limit",
            "gdx_msaa_samples", CARD_RENDERING_KEY,
            "confirmar_todo",
            "auto_action_buttons", "auto_action_persist",
            "modo_auto_confirm", "auto_call_enabled", "auto_call_max",
            AUTO_REBUY_KEY);

    private GdxSettingsContract() {
    }

    private static TogglePage withoutUnavailablePokerVideo(TogglePage page) {
        if (PokerVideoPlatformSupport.isAvailable()) return page;
        return new TogglePage(page.title(), page.options().stream()
                .filter(option -> !POKER_VIDEOS_KEY.equals(option.key()))
                .toList());
    }

    private static List<AppearanceAnimationRow>
            withoutUnavailablePokerVideoRows(
                    List<AppearanceAnimationRow> rows) {
        if (PokerVideoPlatformSupport.isAvailable()) return rows;
        return rows.stream()
                .filter(row -> row.toggle() == null
                        || !POKER_VIDEOS_KEY.equals(row.toggle().key()))
                .toList();
    }

    static String controlSettingKey(int row) {
        return row >= 0 && row < CONTROL_SETTING_KEYS.size()
                ? CONTROL_SETTING_KEYS.get(row) : "shortcut";
    }

    /**
     * Conservative user-facing estimate of the render cost controlled by one
     * setting. It describes the cost while the feature is active, not a GPU
     * benchmark, and deliberately omits options whose effect is negligible.
     */
    static PerformanceImpact performanceImpact(String key,
            Properties properties) {
        if (key == null || key.isBlank()) return PerformanceImpact.NONE;
        Properties values = properties == null ? new Properties() : properties;
        return switch (key) {
            case "animaciones", POKER_VIDEOS_KEY,
                    "animacion_barajado", "gdx_vsync",
                    "animacion_fuego_allin", MODAL_BLUR_KEY -> PerformanceImpact.HIGH;
            case "cinematicas", "cinematicas_allin",
                    "animacion_reparto", "animacion_destape",
                    "animacion_apuestas", "animacion_contador_final",
                    "animacion_efecto_ganador", HALO_EFFECT_KEY,
                    GLASS_EFFECT_KEY -> PerformanceImpact.MEDIUM;
            case "cinematicas_gameover", "animacion_ciegas_dealer",
                    "animacion_contadores", "animacion_entrada_asientos",
                    "animacion_swap" ->
                PerformanceImpact.LOW;
            case "card_flip_zoom" -> integer(values, key, 100) <= 100
                    ? PerformanceImpact.LOW : PerformanceImpact.MEDIUM;
            case "gdx_msaa_samples", CARD_RENDERING_KEY ->
                PerformanceImpact.HIGH;
            case "gdx_fps_limit" -> {
                int limit = integer(values, key, DEFAULT_FRAME_RATE_LIMIT);
                yield limit == 0 || limit >= 240 ? PerformanceImpact.HIGH
                        : limit >= 120 ? PerformanceImpact.MEDIUM
                                : PerformanceImpact.LOW;
            }
            default -> PerformanceImpact.NONE;
        };
    }

    private static int integer(Properties properties, String key,
            int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key,
                    Integer.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static TogglePage page(String title, ToggleOption... options) {
        return new TogglePage(title, List.of(options));
    }

    private static TogglePage mergePages(String title,
            List<TogglePage> pages) {
        return new TogglePage(title, pages.stream()
                .flatMap(page -> page.options().stream()).toList());
    }

    private static ToggleOption option(String key, String label,
            boolean fallback, Gate gate) {
        return new ToggleOption(key, label, fallback, gate, false);
    }

    private static ToggleOption option(String key, String label,
            boolean fallback, Gate gate, String parentKey) {
        return new ToggleOption(key, label, fallback, gate, false, parentKey);
    }

    private static ToggleOption invertedOption(String key, String label,
            boolean fallback, Gate gate) {
        return new ToggleOption(key, label, fallback, gate, true);
    }

    private static ToggleOption invertedOption(String key, String label,
            boolean fallback, Gate gate, String parentKey) {
        return new ToggleOption(key, label, fallback, gate, true, parentKey);
    }

    private static String defaultParentKey(Gate gate) {
        return switch (gate) {
            case MUSIC -> "musica";
            case EFFECTS -> "sonido_efectos";
            case FUNNY_SOUNDS -> "sonidos_chorra";
            case ANIMATIONS -> "animaciones";
            case SHUFFLE_ANIMATION -> "animacion_barajado";
            case NONE -> null;
        };
    }

    /** Every control governed by a parent present on this page is a child. */
    static boolean isChildOption(TogglePage page, ToggleOption option) {
        return option != null && option.parentKey() != null
                && page.options().stream().anyMatch(parent ->
                        option.parentKey().equals(parent.key()));
    }

    /**
     * Computes every real parent/descendant block from the canonical option
     * tree. The renderer only supplies the physical row used by each option.
     */
    static List<RowGroup> toggleGroups(TogglePage page,
            IntUnaryOperator optionRow) {
        Map<String, Integer> optionByKey = new HashMap<>();
        for (int index = 0; index < page.options().size(); index++) {
            optionByKey.put(page.options().get(index).key(), index);
        }
        ArrayList<RowGroup> groups = new ArrayList<>();
        for (int parentIndex = 0; parentIndex < page.options().size();
                parentIndex++) {
            String parentKey = page.options().get(parentIndex).key();
            int lastRow = -1;
            for (int childIndex = 0; childIndex < page.options().size();
                    childIndex++) {
                if (isDescendant(page.options().get(childIndex), parentKey,
                        optionByKey, page.options())) {
                    lastRow = Math.max(lastRow, optionRow.applyAsInt(
                            childIndex));
                }
            }
            int parentRow = optionRow.applyAsInt(parentIndex);
            if (lastRow > parentRow) {
                groups.add(new RowGroup(parentRow, lastRow,
                        optionDepth(page.options().get(parentIndex),
                                optionByKey, page.options())));
            }
        }
        groups.sort((left, right) -> {
            int byDepth = Integer.compare(left.depth(), right.depth());
            return byDepth != 0 ? byDepth
                    : Integer.compare(left.parentRow(), right.parentRow());
        });
        return List.copyOf(groups);
    }

    static Set<Integer> groupGapsBefore(List<RowGroup> groups,
            int totalRows) {
        LinkedHashSet<Integer> gaps = new LinkedHashSet<>();
        for (RowGroup group : groups) {
            if (group.parentRow() > 0) gaps.add(group.parentRow());
            if (group.lastRow() + 1 < totalRows) {
                gaps.add(group.lastRow() + 1);
            }
        }
        return Set.copyOf(gaps);
    }

    /**
     * Removes frames already supplied by the subpage container and then
     * recalculates nesting from the remaining visible ranges. This prevents
     * a whole-page parent from producing a redundant second border while its
     * meaningful child categories keep their own frames.
     */
    static List<RowGroup> visualGroups(List<RowGroup> groups,
            int totalRows) {
        if (groups == null || groups.isEmpty() || totalRows <= 0) {
            return List.of();
        }
        List<RowGroup> useful = groups.stream()
                .filter(group -> !(group.parentRow() == 0
                        && group.lastRow() == totalRows - 1))
                .toList();
        ArrayList<RowGroup> result = new ArrayList<>(useful.size());
        for (RowGroup group : useful) {
            int depth = 0;
            for (RowGroup candidate : useful) {
                if (candidate == group) continue;
                boolean strictlyContains = candidate.parentRow()
                        <= group.parentRow()
                        && candidate.lastRow() >= group.lastRow()
                        && (candidate.parentRow() < group.parentRow()
                                || candidate.lastRow() > group.lastRow());
                if (strictlyContains) depth++;
            }
            result.add(new RowGroup(group.parentRow(), group.lastRow(),
                    depth));
        }
        result.sort((left, right) -> {
            int byDepth = Integer.compare(left.depth(), right.depth());
            return byDepth != 0 ? byDepth
                    : Integer.compare(left.parentRow(), right.parentRow());
        });
        return List.copyOf(result);
    }

    private static boolean isDescendant(ToggleOption candidate,
            String ancestorKey, Map<String, Integer> optionByKey,
            List<ToggleOption> options) {
        String parent = candidate.parentKey();
        Set<String> visited = new LinkedHashSet<>();
        while (parent != null && visited.add(parent)) {
            if (ancestorKey.equals(parent)) return true;
            Integer parentIndex = optionByKey.get(parent);
            parent = parentIndex == null ? null
                    : options.get(parentIndex).parentKey();
        }
        return false;
    }

    private static int optionDepth(ToggleOption option,
            Map<String, Integer> optionByKey, List<ToggleOption> options) {
        int depth = 0;
        String parent = option.parentKey();
        Set<String> visited = new LinkedHashSet<>();
        while (parent != null && visited.add(parent)) {
            Integer parentIndex = optionByKey.get(parent);
            if (parentIndex == null) break;
            depth++;
            parent = options.get(parentIndex).parentKey();
        }
        return depth;
    }

    static boolean displayedValue(ToggleOption option, Properties properties,
            boolean masterSound) {
        boolean stored = "sonidos".equals(option.key())
                ? masterSound
                : Boolean.parseBoolean(properties.getProperty(option.key(),
                        Boolean.toString(option.fallback())));
        return option.inverted() ? !stored : stored;
    }

    static boolean microphoneEnabled(Properties properties) {
        return Boolean.parseBoolean(properties.getProperty(
                "audio_mic_enabled", "true"));
    }

    static boolean displayedFallback(ToggleOption option) {
        return option.inverted() ? !option.fallback() : option.fallback();
    }

    static boolean hasVoiceRetention(TogglePage page) {
        return page == AUDIO_VOICE_CHAT_PAGE;
    }

    static boolean hasAudioDevices(TogglePage page) {
        return page == AUDIO_SOURCE_PAGES.get(0);
    }

    /** No audio page inserts anonymous rows before its declared controls. */
    static int audioLeadingRows(TogglePage page) {
        return 0;
    }

    static int audioOptionRow(TogglePage page, int optionIndex) {
        if (hasMasterVolume(page)) {
            return switch (optionIndex) {
                case 0 -> 0;
                case 1 -> 3;
                case 2 -> 5;
                default -> throw new IllegalArgumentException(
                        "Unknown general audio option: " + optionIndex);
            };
        }
        if (hasVoiceRetention(page)) {
            return optionIndex < 3 ? optionIndex : optionIndex + 2;
        }
        return optionIndex;
    }

    /** Every painted audio row participates in the same scroll model. */
    static int audioRowCount(TogglePage page) {
        if (hasMasterVolume(page)) return 6;
        if (hasVoiceRetention(page)) return 7;
        return page.options().size();
    }

    /** General audio keeps the sound master immediately above its volume. */
    static boolean hasMasterVolume(TogglePage page) {
        return page == AUDIO_SOURCE_PAGES.get(0);
    }

    static int masterVolumeRow(TogglePage page) {
        if (!hasMasterVolume(page)) {
            throw new IllegalArgumentException(
                    "The audio page does not contain master volume");
        }
        return 1;
    }

    static int voiceRetentionRow(TogglePage page) {
        if (!hasVoiceRetention(page)) {
            throw new IllegalArgumentException(
                    "The audio page does not contain voice retention");
        }
        return 3;
    }

    static int voiceActionsRow(TogglePage page) {
        return 4;
    }

    static int audioOutputDeviceRow(TogglePage page) {
        if (!hasAudioDevices(page)) throw new IllegalArgumentException(
                "The audio page does not contain device selectors");
        return 2;
    }

    static int audioCaptureDeviceRow(TogglePage page) {
        if (!hasAudioDevices(page)) throw new IllegalArgumentException(
                "The audio page does not contain device selectors");
        return 4;
    }

    static List<RowGroup> audioGroups(TogglePage page) {
        if (hasMasterVolume(page)) {
            return toggleGroups(page,
                    optionIndex -> audioOptionRow(page, optionIndex));
        }
        if (hasVoiceRetention(page)) {
            return List.of(new RowGroup(0, 4, 0),
                    new RowGroup(5, 6, 0));
        }
        return toggleGroups(page,
                optionIndex -> audioOptionRow(page, optionIndex));
    }

    /** One validated volume contract for the menu, lobby and live table. */
    static float masterVolume(Properties properties) {
        try {
            float value = Float.parseFloat(properties.getProperty(
                    "master_volume", Float.toString(DEFAULT_MASTER_VOLUME)));
            return Float.isFinite(value) && value >= 0f && value <= 1f
                    ? value : DEFAULT_MASTER_VOLUME;
        } catch (NumberFormatException invalid) {
            return DEFAULT_MASTER_VOLUME;
        }
    }

    /** Clean selected MSAA value shared by every GDX settings entry point. */
    static String msaaStatusLabel(int requested, int actual,
            GdxGameText text) {
        return antialiasingValue("MSAA", requested, text);
    }

    static String frameRateLimitLabel(int limit, GdxGameText text) {
        return limit == 0
                ? translatedUpper(text, "gdx.settings.value.monitor_refresh",
                        "FRECUENCIA DEL MONITOR")
                : limit + " FPS";
    }

    /**
     * Separate restart notice for the wide label lane of the MSAA row.
     * Keeping it out of the narrow value lane prevents illegible fitting.
     */
    static String msaaRestartStatus(int requested, int actual,
            GdxGameText text) {
        if (actual == requested) return "";
        return translatedUpper(text,
                "gdx.settings.value.restart_current",
                "REINICIAR (ACTUAL "
                + antialiasingValue("MSAA", actual, text) + ")",
                antialiasingValue("MSAA", actual, text));
    }

    /** Whether this edit selected an MSAA back buffer that needs a restart. */
    static boolean requiresMsaaRestart(int openedRequested, int requested,
            int actual) {
        return openedRequested != requested && requested != actual;
    }

    static String cardRenderingStatusLabel(int rendering, GdxGameText text) {
        return rendering == GdxCardSmaaRenderer.RENDERING_SMOOTH
                ? translatedUpper(text, "gdx.settings.value.smooth", "SUAVE")
                : translatedUpper(text, "gdx.settings.value.native", "NATIVO");
    }

    private static String antialiasingValue(String technique, int samples,
            GdxGameText text) {
        String value = samples == 0 ? translatedUpper(text,
                "gdx.settings.value.disabled", "DESACTIVADO")
                : samples + "X";
        return technique + " " + value;
    }

    /** Host-authoritative communication rules, unlike local audio choices. */
    static boolean isGlobalCommunicationOption(ToggleOption option) {
        return option != null && ("tts_server".equals(option.key())
                || "voice_messages".equals(option.key()));
    }

    static boolean hasAppearanceAnimationOptions(TogglePage page) {
        return page == APPEARANCE_ANIMATIONS_PAGE;
    }

    static List<AppearanceAnimationRow> appearanceRows(TogglePage page) {
        if (hasAppearanceAnimationOptions(page)) {
            return APPEARANCE_ANIMATION_ROWS;
        }
        return page.options().stream()
                .map(option -> new AppearanceAnimationRow(option, null))
                .toList();
    }

    static int appearanceRowCount(TogglePage page) {
        return appearanceRows(page).size();
    }

    static List<RowGroup> appearanceGroups(TogglePage page) {
        List<AppearanceAnimationRow> rows = appearanceRows(page);
        ArrayList<ToggleOption> visualOptions = new ArrayList<>(rows.size());
        for (AppearanceAnimationRow row : rows) {
            if (row.toggle() != null) {
                visualOptions.add(row.toggle());
            } else {
                GdxAppearanceOptions.Choice choice = row.choice();
                visualOptions.add(new ToggleOption(choice.key(),
                        choice.label(), false, Gate.NONE, false,
                        GdxAppearanceOptions.parentKey(choice)));
            }
        }
        return toggleGroups(new TogglePage(page.title(),
                List.copyOf(visualOptions)), index -> index);
    }

    /**
     * Window mode owns the automatic-fullscreen preference.  Keeping this
     * relationship in the shared contract makes the menu, waiting room and
     * live table use the same frame, insets, scrolling and hit geometry.
     */
    static List<RowGroup> appearanceGraphicsGroups() {
        return List.of(new RowGroup(0, 1, 0));
    }

    static List<RowGroup> controlGroups() {
        return List.of(new RowGroup(0, 3, 0));
    }

    /** Parent/child blocks shared by menu, lobby and live game settings. */
    static List<RowGroup> gameGroups(int gamePage) {
        return switch (gamePage) {
            case 0 -> List.of(
                    new RowGroup(BLIND_STRUCTURE_ROW, BLIND_MANAGE_ROW, 0),
                    new RowGroup(BLIND_INCREASE_ROW, BLIND_CAP_ROW, 0));
            case 1 -> List.of(
                    new RowGroup(BUYIN_FIXED_ROW, BUYIN_INITIAL_ROW, 0),
                    new RowGroup(BUYIN_REBUY_ROW, BUYIN_REBUY_CAP_ROW, 0));
            case 2 -> List.of(new RowGroup(2, 4, 0));
            case 3 -> List.of(
                    new RowGroup(0, 1, 0),
                    new RowGroup(2, 3, 0));
            default -> List.of();
        };
    }

    private static AppearanceAnimationRow animationToggle(String key) {
        return new AppearanceAnimationRow(
                APPEARANCE_ANIMATIONS_PAGE.options().stream()
                        .filter(option -> option.key().equals(key))
                        .findFirst().orElseThrow(
                                () -> new IllegalStateException(
                                        "Unknown animation switch: " + key)),
                null);
    }

    private static AppearanceAnimationRow animationChoice(String key) {
        return new AppearanceAnimationRow(null,
                GdxAppearanceOptions.ANIMATION_CHOICES.stream()
                        .filter(choice -> choice.key().equals(key))
                        .findFirst().orElseThrow(
                                () -> new IllegalStateException(
                                        "Unknown animation choice: " + key)));
    }

    static TogglePage appearanceTablePage() {
        return APPEARANCE_TABLE_PAGE;
    }

    static int appearanceTableRowCount() {
        return APPEARANCE_PRIMARY_ROW_COUNT
                + APPEARANCE_TABLE_PAGE.options().size();
    }

    static String markDefault(String value, boolean factoryDefault) {
        return factoryDefault ? value + " *" : value;
    }

    static boolean isDefaultFrameRateLimit(Properties properties) {
        Properties values = properties == null ? new Properties() : properties;
        return integer(values, "gdx_fps_limit", DEFAULT_FRAME_RATE_LIMIT)
                == DEFAULT_FRAME_RATE_LIMIT;
    }

    static String vsyncStatusLabel(boolean enabled, GdxGameText text) {
        return booleanStatusLabel(enabled, DEFAULT_VSYNC, text);
    }

    static String booleanStatusLabel(boolean enabled,
            boolean factoryDefault, GdxGameText text) {
        String key = enabled
                ? "gdx.settings.value.enabled"
                : "gdx.settings.value.disabled";
        return markDefault(translatedUpper(text, key,
                enabled ? "ACTIVADO" : "DESACTIVADO"),
                enabled == factoryDefault);
    }

    /** Complete Auto Call summary shared by menu and live-table settings. */
    static String autoCallStatusLabel(boolean enabled, double maximum,
            String formattedMaximum, GdxGameText text) {
        if (!enabled) {
            return markDefault(translatedUpper(text,
                    "gdx.settings.value.disabled", "DESACTIVADO"), true);
        }
        String enabledText = translatedUpper(text,
                "gdx.settings.value.enabled", "ACTIVADO");
        String detail = maximum <= 0d
                ? translatedUpper(text,
                        "gdx.settings.game.value.no_limit", "SIN LÍMITE")
                : translatedUpper(text, "gdx.settings.value.up_to", "HASTA")
                        + " " + formattedMaximum;
        return enabledText + " (" + detail + ")";
    }

    /** Decimal presentation shared by menu, lobby and live blind settings. */
    static String blindAmountLabel(double value) {
        if (value < 1d) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    static String blindLevelLabel(double smallBlind, double bigBlind) {
        return blindAmountLabel(smallBlind) + " / "
                + blindAmountLabel(bigBlind);
    }

    static String voiceRetentionLabel(Properties properties) {
        int days = voiceRetentionDays(properties);
        return days == 0 ? "PARA SIEMPRE" : days + " DÍAS";
    }

    static String voiceRetentionLabel(Properties properties,
            GdxGameText text) {
        return voiceRetentionOptionLabel(voiceRetentionDays(properties), text);
    }

    static String voiceRetentionOptionLabel(int days, GdxGameText text) {
        return days == 0
                ? translatedUpper(text, "audio.retencion_siempre",
                        "PARA SIEMPRE")
                : translatedUpper(text, "audio.retencion_dias",
                        days + " DÍAS", days);
    }

    static List<Integer> voiceRetentionOptions() {
        return VOICE_RETENTION_DAYS;
    }

    static int voiceRetentionIndex(Properties properties) {
        return VOICE_RETENTION_DAYS.indexOf(voiceRetentionDays(properties));
    }

    static void selectVoiceRetention(Properties properties, int option) {
        if (option < 0 || option >= VOICE_RETENTION_DAYS.size()) return;
        properties.setProperty("audio_voice_note_retention_days",
                Integer.toString(VOICE_RETENTION_DAYS.get(option)));
    }

    static void cycleVoiceRetention(Properties properties) {
        adjustVoiceRetention(properties, 1);
    }

    static void adjustVoiceRetention(Properties properties, int direction) {
        int index = voiceRetentionIndex(properties);
        int next = VOICE_RETENTION_DAYS.get(Math.floorMod(index
                + Integer.signum(direction), VOICE_RETENTION_DAYS.size()));
        properties.setProperty("audio_voice_note_retention_days",
                Integer.toString(next));
    }

    /**
     * Restores the audio controls owned by the GDX surface to the same factory
     * values as {@code AudioSettingsPanel}.  Global communication rules remain
     * authoritative on a client, so callers explicitly decide whether those
     * two values may be reset.
     */
    static void restoreAudioDefaults(Properties properties,
            boolean includeGlobalRules, boolean microphoneAvailable) {
        for (int page = 0; page < AUDIO_PAGES.size(); page++) {
            restoreAudioPageDefaults(properties, page, includeGlobalRules,
                    microphoneAvailable);
        }
    }

    /** Restores only the currently visible Audio subsection. */
    static void restoreAudioPageDefaults(Properties properties, int pageIndex,
            boolean includeGlobalRules, boolean microphoneAvailable) {
        int index = Math.max(0, Math.min(pageIndex, AUDIO_PAGES.size() - 1));
        TogglePage page = AUDIO_PAGES.get(index);
        if (page == AUDIO_FUNNY_SOUNDS_PAGE) {
            properties.stringPropertyNames().stream()
                    .filter(key -> key.startsWith(
                            GamePresentationSettings
                                    .JOKE_SOUND_PREFERENCE_PREFIX))
                    .toList().forEach(properties::remove);
        }
        if (index == 0) {
            properties.setProperty("master_volume",
                    Float.toString(DEFAULT_MASTER_VOLUME));
        }
        for (ToggleOption option : page.options()) {
            if (isGlobalCommunicationRule(option)
                    && !includeGlobalRules) continue;
            boolean fallback = "audio_mic_enabled".equals(option.key())
                    ? microphoneAvailable : option.fallback();
            properties.setProperty(option.key(), Boolean.toString(fallback));
        }
        if (hasVoiceRetention(page)) {
            properties.setProperty("audio_voice_note_retention_days", "90");
        }
        if (hasAudioDevices(page)) {
            properties.setProperty(GdxAudioDevices.OUTPUT_KEY, "");
            properties.setProperty(GdxAudioDevices.CAPTURE_KEY, "");
        }
    }

    static boolean audioPageAtDefaults(Properties properties, int pageIndex,
            boolean includeGlobalRules, boolean microphoneAvailable,
            boolean masterSound) {
        int index = Math.max(0, Math.min(pageIndex, AUDIO_PAGES.size() - 1));
        TogglePage page = AUDIO_PAGES.get(index);
        if (index == 0 && (Float.compare(masterVolume(properties),
                DEFAULT_MASTER_VOLUME) != 0 || !masterSound)) {
            return false;
        }
        for (ToggleOption option : page.options()) {
            if (isGlobalCommunicationRule(option)
                    && !includeGlobalRules) continue;
            boolean expected = "audio_mic_enabled".equals(option.key())
                    ? microphoneAvailable : option.fallback();
            boolean actual = "sonidos".equals(option.key())
                    ? masterSound : Boolean.parseBoolean(
                            properties.getProperty(option.key(),
                                    Boolean.toString(option.fallback())));
            if (actual != expected) return false;
        }
        if (hasVoiceRetention(page)
                && voiceRetentionDays(properties) != 90) return false;
        if (page == AUDIO_FUNNY_SOUNDS_PAGE
                && properties.stringPropertyNames().stream()
                        .filter(key -> key.startsWith(
                                GamePresentationSettings
                                        .JOKE_SOUND_PREFERENCE_PREFIX))
                        .anyMatch(key -> !Boolean.parseBoolean(
                                properties.getProperty(key, "true")))) {
            return false;
        }
        return !hasAudioDevices(page)
                || (properties.getProperty(GdxAudioDevices.OUTPUT_KEY, "")
                        .isBlank()
                && properties.getProperty(GdxAudioDevices.CAPTURE_KEY, "")
                        .isBlank());
    }

    /** Restores only settings that have a real GDX appearance consumer. */
    static void restoreAppearanceDefaults(Properties properties,
            boolean includeStartupGraphics) {
        for (int page = 0; page < APPEARANCE_PAGES.size() + 2; page++) {
            restoreAppearancePageDefaults(properties, page,
                    includeStartupGraphics);
        }
    }

    /** Restores only the currently visible Appearance subsection. */
    static void restoreAppearancePageDefaults(Properties properties,
            int pageIndex, boolean includeStartupGraphics) {
        int maximum = APPEARANCE_PAGES.size() + 1;
        int index = Math.max(0, Math.min(pageIndex, maximum));
        if (index == 0) {
            // GDX's approved visual reference uses the Goliat deck.
            properties.setProperty("baraja", "goliat");
            properties.setProperty("trasera", "default");
            properties.setProperty("color_tapete", "verde");
            properties.setProperty("nivel_luz", Integer.toString(
                    GdxAppearanceOptions.DEFAULT_LIGHT_LEVEL));
            for (ToggleOption option : APPEARANCE_TABLE_PAGE.options()) {
                properties.setProperty(option.key(),
                        Boolean.toString(option.fallback()));
            }
            return;
        }
        if (isAppearanceGraphicsPage(index)) {
            properties.setProperty("gdx_vsync",
                    Boolean.toString(DEFAULT_VSYNC));
            properties.setProperty("gdx_fps_limit",
                    Integer.toString(DEFAULT_FRAME_RATE_LIMIT));
            properties.setProperty("gdx_show_fps", "false");
            properties.setProperty(CARD_RENDERING_KEY,
                    Integer.toString(DEFAULT_CARD_RENDERING_MODE));
            if (includeStartupGraphics) {
                properties.setProperty(GdxWindowMode.PREFERENCE_KEY,
                        GdxWindowMode.BORDERLESS.persistedValue());
                properties.setProperty("gdx_msaa_samples", "4");
            }
            return;
        }
        TogglePage page = appearanceTogglePage(index);
        for (ToggleOption option : page.options()) {
            properties.setProperty(option.key(),
                    Boolean.toString(option.fallback()));
        }
        if (page == APPEARANCE_ANIMATIONS_PAGE) {
            for (GdxAppearanceOptions.Choice choice
                    : GdxAppearanceOptions.ANIMATION_CHOICES) {
                properties.setProperty(choice.key(), choice.fallback());
            }
        }
    }

    static boolean appearancePageAtDefaults(Properties properties,
            int pageIndex, boolean includeStartupGraphics) {
        int maximum = APPEARANCE_PAGES.size() + 1;
        int index = Math.max(0, Math.min(pageIndex, maximum));
        if (index == 0) {
            boolean visualDefaults = stringAtDefault(properties, "baraja",
                    "goliat")
                    && stringAtDefault(properties, "trasera", "default")
                    && stringAtDefault(properties, "color_tapete", "verde")
                    && stringAtDefault(properties, "nivel_luz",
                            Integer.toString(
                                    GdxAppearanceOptions.DEFAULT_LIGHT_LEVEL));
            if (!visualDefaults) return false;
            for (ToggleOption option : APPEARANCE_TABLE_PAGE.options()) {
                if (!booleanAtDefault(properties, option.key(),
                        option.fallback())) return false;
            }
            return true;
        }
        if (isAppearanceGraphicsPage(index)) {
            boolean runtime = booleanAtDefault(properties, "gdx_vsync",
                    DEFAULT_VSYNC)
                    && stringAtDefault(properties, "gdx_fps_limit",
                            Integer.toString(DEFAULT_FRAME_RATE_LIMIT))
                    && booleanAtDefault(properties, "gdx_show_fps", false)
                    && stringAtDefault(properties, CARD_RENDERING_KEY,
                            Integer.toString(DEFAULT_CARD_RENDERING_MODE));
            return runtime && (!includeStartupGraphics
                    || stringAtDefault(properties,
                            GdxWindowMode.PREFERENCE_KEY,
                            GdxWindowMode.BORDERLESS.persistedValue())
                    && stringAtDefault(properties, "gdx_msaa_samples", "4"));
        }
        TogglePage page = appearanceTogglePage(index);
        for (ToggleOption option : page.options()) {
            if (!booleanAtDefault(properties, option.key(),
                    option.fallback())) return false;
        }
        if (page == APPEARANCE_ANIMATIONS_PAGE) {
            for (GdxAppearanceOptions.Choice choice
                    : GdxAppearanceOptions.ANIMATION_CHOICES) {
                if (!stringAtDefault(properties, choice.key(),
                        choice.fallback())) return false;
            }
        }
        return true;
    }

    private static boolean isGlobalCommunicationRule(ToggleOption option) {
        return "tts_server".equals(option.key())
                || "voice_messages".equals(option.key());
    }

    private static boolean booleanAtDefault(Properties properties, String key,
            boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key,
                Boolean.toString(fallback))) == fallback;
    }

    private static boolean stringAtDefault(Properties properties, String key,
            String fallback) {
        return fallback.equalsIgnoreCase(properties.getProperty(key,
                fallback).strip());
    }

    static int voiceRetentionDays(Properties properties) {
        try {
            int value = Integer.parseInt(properties.getProperty(
                    "audio_voice_note_retention_days", "90"));
            if (VOICE_RETENTION_DAYS.contains(value)) return value;
        } catch (NumberFormatException ignored) {
        }
        return 90;
    }

    private static String pageTranslationKey(String title) {
        return switch (title) {
            case "GENERAL" -> "gdx.settings.page.general";
            case "MÚSICA" -> "gdx.settings.page.music";
            case "EFECTOS" -> "gdx.settings.page.effects";
            case "SONIDOS DE COÑA" -> "gdx.settings.page.joke_sounds";
            case "ACCIONES" -> "gdx.settings.page.actions";
            case "CARTAS" -> "gdx.settings.page.cards";
            case "PARTIDA" -> "gdx.settings.page.game";
            case "TURNO Y AVISOS" -> "gdx.settings.page.turn_alerts";
            case "SALA" -> "gdx.settings.page.room";
            case "PANTALLA" -> "gdx.settings.page.display";
            case "AVISOS Y CHAT" -> "gdx.settings.page.alerts_chat";
            case "SISTEMA" -> "gdx.settings.page.system";
            case "NOTAS Y CHAT DE VOZ" ->
                "gdx.settings.page.voice_notes_chat";
            case "CHAT" -> "gdx.settings.page.chat";
            case "DISPOSITIVOS" -> "gdx.settings.page.devices";
            case "CINEMÁTICAS" -> "gdx.settings.page.cinematics";
            case "ANIMACIONES" -> "gdx.settings.page.animations";
            case "ANIMACIONES Y EFECTOS" ->
                "gdx.settings.page.animations_and_effects";
            case "MOVIMIENTO" -> "gdx.settings.page.movement";
            case "INFORMACIÓN" -> "gdx.settings.page.information";
            default -> "gdx.settings.page." + title;
        };
    }

    private static String gamePageTranslationKey(String title) {
        return switch (title) {
            case "CIEGAS" -> "gdx.settings.game.blinds";
            case "COMPRA" -> "gdx.settings.game.buyin";
            case "RECOMPRA" -> "gdx.settings.game.rebuy";
            case "BOTS" -> "gdx.settings.game.bots";
            case "PARTIDA" -> "gdx.settings.game.game";
            case "REGLAS" -> "gdx.settings.game.rules";
            case "CONTROLES" -> "gdx.settings.game.controls";
            case "TIMBA" -> "gdx.settings.game.table";
            default -> "gdx.settings.game." + title;
        };
    }

    private static String translatedUpper(GdxGameText text, String key,
            String fallback, Object... arguments) {
        if (text == null) return fallback;
        String translated = text.translate(key, arguments);
        if (translated.equals(key)) return fallback;
        return translated.toUpperCase(Locale.forLanguageTag(text.language()));
    }

    static boolean enabled(ToggleOption option, Properties properties,
            boolean soundEnabled) {
        return enabled(option, properties, soundEnabled, true);
    }

    static boolean enabled(ToggleOption option, Properties properties,
            boolean soundEnabled, boolean mayEditGlobalCommunication) {
        if (isGlobalCommunicationOption(option)
                && !mayEditGlobalCommunication) return false;
        if (option.parentKey() != null && !Boolean.parseBoolean(
                properties.getProperty(option.parentKey(), "true"))) {
            return false;
        }
        boolean music = Boolean.parseBoolean(properties.getProperty(
                "musica", "true"));
        boolean effects = Boolean.parseBoolean(properties.getProperty(
                "sonido_efectos", "true"));
        boolean funnySounds = effects && Boolean.parseBoolean(
                properties.getProperty("sonidos_chorra", "false"));
        boolean animations = Boolean.parseBoolean(
                properties.getProperty("animaciones", "true"));
        boolean shuffleAnimation = animations && Boolean.parseBoolean(
                properties.getProperty("animacion_barajado", "true"));
        String concreteJoke = GamePresentationSettings.jokeSoundResource(
                option.key());
        if (concreteJoke != null) {
            String categoryPreference = GdxSoundFeedback.funnyPreferenceKey(
                    concreteJoke);
            if (categoryPreference != null && !Boolean.parseBoolean(
                    properties.getProperty(categoryPreference, "true"))) {
                return false;
            }
        }
        return switch (option.gate()) {
            case NONE -> true;
            case MUSIC -> music;
            case EFFECTS -> effects;
            case FUNNY_SOUNDS -> funnySounds;
            case ANIMATIONS -> animations;
            case SHUFFLE_ANIMATION -> shuffleAnimation;
        };
    }

    static Map<String, String> snapshot(Properties properties) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String key : PREFERENCE_KEYS) {
            snapshot.put(key, properties.getProperty(key));
        }
        for (String key : properties.stringPropertyNames()) {
            if (isTransactionalDynamicKey(key)) {
                snapshot.put(key, properties.getProperty(key));
            }
        }
        return snapshot;
    }

    /**
     * Migrates the temporary GDX-only key to CoronaPoker's canonical Swing
     * preference. Both frontends must observe the same value.
     */
    static boolean migrateLegacyChatNotificationPreference(
            Properties properties) {
        if (properties == null
                || !properties.containsKey(LEGACY_GDX_CHAT_NOTIFICATIONS_KEY)) {
            return false;
        }
        if (!properties.containsKey(CHAT_GAME_NOTIFICATIONS_KEY)) {
            properties.setProperty(CHAT_GAME_NOTIFICATIONS_KEY,
                    properties.getProperty(LEGACY_GDX_CHAT_NOTIFICATIONS_KEY));
        }
        properties.remove(LEGACY_GDX_CHAT_NOTIFICATIONS_KEY);
        return true;
    }

    static boolean chatNotificationsEnabled(Properties properties,
            boolean fallback) {
        if (properties == null) return fallback;
        String value = properties.getProperty(CHAT_GAME_NOTIFICATIONS_KEY,
                properties.getProperty(LEGACY_GDX_CHAT_NOTIFICATIONS_KEY,
                        Boolean.toString(fallback)));
        return Boolean.parseBoolean(value);
    }

    static void restore(Properties properties, Map<String, String> snapshot) {
        for (String key : PREFERENCE_KEYS) {
            restoreValue(properties, snapshot, key);
        }
        properties.stringPropertyNames().stream()
                .filter(GdxSettingsContract::isTransactionalDynamicKey)
                .filter(key -> !snapshot.containsKey(key))
                .toList()
                .forEach(properties::remove);
        snapshot.keySet().stream()
                .filter(GdxSettingsContract::isTransactionalDynamicKey)
                .forEach(key -> restoreValue(properties, snapshot, key));
    }

    private static boolean isTransactionalDynamicKey(String key) {
        return key.startsWith("shortcut.")
                || key.startsWith("blind_structure.")
                || key.startsWith(
                        GamePresentationSettings.JOKE_SOUND_PREFERENCE_PREFIX)
                || key.equals("blind_structures.count");
    }

    private static void restoreValue(Properties properties,
            Map<String, String> snapshot, String key) {
        String value = snapshot.get(key);
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }
}
