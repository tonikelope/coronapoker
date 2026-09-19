/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

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

    enum Gate {
        NONE, SOUND, MUSIC, EFFECTS, ANIMATIONS, CINEMATICS
    }

    record ToggleOption(String key, String label, boolean fallback,
            Gate gate, boolean inverted) {

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

    enum Section {
        APPEARANCE("APARIENCIA", "settings.tab_apariencia"),
        AUDIO("AUDIO", "settings.tab_audio"),
        GAME("JUEGO", "settings.tab_partida"),
        SHORTCUTS("ATAJOS", "settings.tab_atajos"),
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

    /** Swing's canonical order. GAME is added only while a table is open. */
    static final List<Section> MENU_SECTIONS = List.of(
            Section.APPEARANCE, Section.AUDIO, Section.SHORTCUTS,
            Section.DEBUG);
    static final List<Section> TABLE_SECTIONS = List.of(
            Section.APPEARANCE, Section.AUDIO, Section.SHORTCUTS,
            Section.GAME, Section.DEBUG);

    /**
     * Context-specific Game pages from Swing's two mutually exclusive panels.
     * The chrome is shared, but the waiting room exposes the complete pre-game
     * draft while the running table exposes only live controls plus immutable
     * summaries and session actions.
     */
    static final List<String> WAITING_ROOM_GAME_PAGES = List.of(
            "CIEGAS", "COMPRA", "RECOMPRA", "BOTS", "PARTIDA", "REGLAS");
    static final List<String> LIVE_TABLE_GAME_PAGES = List.of(
            "CONTROLES", "TIMBA", "CIEGAS", "COMPRA", "BOTS", "SESIÓN");

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
                yield List.copyOf(labels);
            }
            case AUDIO -> AUDIO_PAGES.stream().map(page -> page.title(text))
                    .toList();
            case GAME -> gamePages.stream()
                    .map(page -> translatedUpper(text,
                            gamePageTranslationKey(page), page))
                    .toList();
            case SHORTCUTS -> {
                int rows = Math.max(1, shortcutRowsPerPage);
                int pages = Math.max(1, (Math.max(0, shortcutEntries)
                        + rows - 1) / rows);
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                for (int page = 0; page < pages; page++) {
                    labels.add(translatedUpper(text,
                            "gdx.settings.page_number",
                            "PÁGINA " + (page + 1), page + 1));
                }
                yield List.copyOf(labels);
            }
            case DEBUG -> List.of(translatedUpper(text,
                    "gdx.settings.page.technical_log",
                    "REGISTRO TÉCNICO"));
        };
    }

    static String contentHeading(Section section, String subpage) {
        String page = subpage == null ? "" : subpage.strip();
        // The selected primary and secondary tabs already communicate the
        // hierarchy. Repeating "AUDIO · GENERAL" (and equivalents) wastes
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
    private static final TogglePage AUDIO_LOCAL_VOICE_PAGE = page(
            "VOZ LOCAL",
            option("audio_mic_enabled", "MICRÓFONO", true, Gate.SOUND),
            invertedOption("audio_block_voice_messages",
                    "NOTAS DE VOZ (LOCAL)", false, Gate.SOUND),
            option("audio_play_own_voice", "REPRODUCIR MIS NOTAS", true,
                    Gate.SOUND),
            invertedOption("audio_block_tts_local", "TTS (LOCAL)", false,
                    Gate.SOUND));

    private static final TogglePage AUDIO_DEVICE_PAGE = page("DISPOSITIVOS");
    private static final TogglePage APPEARANCE_ANIMATION_OPTIONS_PAGE = page(
            "RITMO Y ESTILO");

    private static final int[] VOICE_RETENTION_DAYS = {7, 15, 30, 90, 0};

    static final List<TogglePage> AUDIO_PAGES = List.of(
            page("GENERAL",
                    option("sonidos", "SONIDO", true, Gate.NONE),
                    option("musica", "MÚSICA", true, Gate.SOUND),
                    option("sonido_efectos", "EFECTOS DE SONIDO", true,
                            Gate.SOUND),
                    option("sonidos_chorra", "SONIDOS DE COÑA", false,
                            Gate.SOUND)),
            page("MÚSICA",
                    option("sonido_ascensor", "AMBIENTE", true, Gate.MUSIC),
                    option("musica_sala_espera", "SALA DE ESPERA", true,
                            Gate.MUSIC)),
            page("ACCIONES",
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
                    option("sonido_volumen", "CAMBIAR VOLUMEN", true,
                            Gate.EFFECTS),
                    option("sonido_arranque", "ARRANQUE DE LA APP", true,
                            Gate.EFFECTS)),
            page("AVISOS Y CHAT",
                    option("sonido_aviso", "ADVERTENCIA", true,
                            Gate.EFFECTS),
                    option("sonido_error", "ALERTA DE PELIGRO", true,
                            Gate.EFFECTS),
                    option("sonido_error_red", "ERROR DE RED", true,
                            Gate.EFFECTS),
                    option("voice_messages", "NOTAS DE VOZ", true,
                            Gate.SOUND),
                    option("tts_server", "VOZ (TTS)", true, Gate.SOUND)),
            AUDIO_LOCAL_VOICE_PAGE,
            AUDIO_DEVICE_PAGE);

    /** Appearance switches shared with Swing; choice controls are separate. */
    static final List<TogglePage> APPEARANCE_PAGES = List.of(
            page("CINEMÁTICAS",
                    option("cinematicas", "CINEMÁTICAS", true,
                            Gate.NONE),
                    option("cinematicas_allin", "ALL-IN", true,
                            Gate.CINEMATICS),
                    option("cinematicas_gameover", "GAME OVER", true,
                            Gate.CINEMATICS)),
            page("MESA Y CARTAS",
                    option("animacion_barajado", "BARAJADO", true,
                            Gate.ANIMATIONS),
                    option("animacion_reparto", "REPARTO", true,
                            Gate.ANIMATIONS),
                    option("animacion_destape", "DESTAPAR", true,
                            Gate.ANIMATIONS),
                    option("animacion_ciegas_dealer",
                            "FICHAS DE POSICIÓN", true, Gate.ANIMATIONS),
                    option("animacion_apuestas", "APUESTAS", true,
                            Gate.ANIMATIONS)),
            page("MOVIMIENTO",
                    option("animacion_contadores", "CONTADORES", true,
                            Gate.ANIMATIONS),
                    option("animacion_swap", "ORDENAR LA MANO", true,
                            Gate.ANIMATIONS),
                    option("animacion_contador_final", "RECUENTO FINAL", true,
                            Gate.ANIMATIONS)),
            page("INFORMACIÓN",
                    option("show_time", "MOSTRAR RELOJ", false, Gate.NONE),
                    option("gdx_show_fps", "MOSTRAR FPS", true, Gate.NONE),
                    option("mostrar_coste_igualar", "COSTE DE IGUALAR", true,
                            Gate.NONE),
                    option("resaltar_jugada_showdown",
                            "RESALTAR JUGADA EN SHOWDOWN", true, Gate.NONE),
                    option("resaltar_avatares", "RESALTAR AVATARES", false,
                            Gate.NONE)),
            page("CHAT",
                    option("chat_notifications_ingame",
                            "NOTIFICACIONES DURANTE LA PARTIDA", true,
                            Gate.NONE),
                    option("chat_images_ingame", "IMÁGENES DEL CHAT", true,
                            Gate.NONE)),
            page("CAPTURA Y VISTA",
                    option("screenshot_fin_timba",
                            "CAPTURA AL TERMINAR LA TIMBA", false, Gate.NONE)),
            APPEARANCE_ANIMATION_OPTIONS_PAGE);

    /**
     * Persisted values owned by the shared Swing/GDX settings transaction.
     * This is deliberately a superset of the currently displayed GDX rows:
     * Cancel must preserve hidden Swing-only values, and controls still under
     * migration are exposed only after their GDX consumer exists.
     */
    static final List<String> PREFERENCE_KEYS = List.of(
            "sonidos", "master_volume", "musica",
            "sonido_ascensor", "musica_sala_espera", "musica_about",
            "musica_stats", "sonido_efectos", "sonidos_chorra",
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
            "audio_mic_enabled", "audio_play_own_voice",
            "audio_block_voice_messages", "audio_block_tts_local",
            "audio_voice_note_retention_days",
            "baraja", "trasera", "color_tapete", "nivel_luz",
            "show_time", "gdx_show_fps", "mostrar_coste_igualar",
            "chat_images_ingame", "chat_notifications_ingame",
            "resaltar_jugada_showdown", "resaltar_avatares",
            "screenshot_fin_timba", "animacion_contador_final",
            "animaciones", "cinematicas", "cinematicas_accion",
            "cinematicas_allin", "cinematicas_gameover",
            "animacion_barajado", "animacion_reparto",
            "animacion_destape", "animacion_ciegas_dealer",
            "animacion_apuestas", "animacion_contadores",
            "animacion_cascada_overlay", "animacion_swap",
            "animacion_downgrade", "card_flip_duration",
            "card_flip_zoom", "reparto_velocidad", "anim_calidad",
            "swap_velocidad", "swap_arco", "downgrade_velocidad",
            "zoom_level", "vista_compacta", "auto_zoom", "dialog_zoom",
            "auto_fullscreen", "gdx_window_mode", "gdx_msaa_samples",
            "confirmar_todo",
            "auto_action_buttons", "auto_action_persist",
            "modo_auto_confirm", "auto_call_enabled", "auto_call_max");

    private GdxSettingsContract() {
    }

    private static TogglePage page(String title, ToggleOption... options) {
        return new TogglePage(title, List.of(options));
    }

    private static ToggleOption option(String key, String label,
            boolean fallback, Gate gate) {
        return new ToggleOption(key, label, fallback, gate, false);
    }

    private static ToggleOption invertedOption(String key, String label,
            boolean fallback, Gate gate) {
        return new ToggleOption(key, label, fallback, gate, true);
    }

    static boolean displayedValue(ToggleOption option, Properties properties,
            boolean masterSound) {
        boolean stored = "sonidos".equals(option.key())
                ? masterSound
                : Boolean.parseBoolean(properties.getProperty(option.key(),
                        Boolean.toString(option.fallback())));
        return option.inverted() ? !stored : stored;
    }

    static boolean hasVoiceRetention(TogglePage page) {
        return page == AUDIO_LOCAL_VOICE_PAGE;
    }

    static boolean hasAudioDevices(TogglePage page) {
        return page == AUDIO_DEVICE_PAGE;
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

    /** Shared localized MSAA summary for every GDX settings entry point. */
    static String msaaStatusLabel(int requested, int actual,
            GdxGameText text) {
        String requestedText = msaaValue(requested, text);
        if (actual == requested) {
            return requestedText + "  \u00b7  " + translatedUpper(text,
                    "gdx.settings.value.active", "ACTIVO");
        }
        return requestedText + "  \u00b7  " + translatedUpper(text,
                "gdx.settings.value.restart_current",
                "REINICIAR (ACTUAL " + msaaValue(actual, text) + ")",
                msaaValue(actual, text));
    }

    private static String msaaValue(int samples, GdxGameText text) {
        return samples == 0 ? translatedUpper(text,
                "gdx.settings.value.disabled", "DESACTIVADO")
                : samples + "X";
    }

    /** Host-authoritative communication rules, unlike local audio choices. */
    static boolean isGlobalCommunicationOption(ToggleOption option) {
        return option != null && ("tts_server".equals(option.key())
                || "voice_messages".equals(option.key()));
    }

    static boolean hasAppearanceAnimationOptions(TogglePage page) {
        return page == APPEARANCE_ANIMATION_OPTIONS_PAGE;
    }

    static String voiceRetentionLabel(Properties properties) {
        int days = voiceRetentionDays(properties);
        return days == 0 ? "PARA SIEMPRE" : days + " DÍAS";
    }

    static String voiceRetentionLabel(Properties properties,
            GdxGameText text) {
        int days = voiceRetentionDays(properties);
        return days == 0
                ? translatedUpper(text, "audio.retencion_siempre",
                        "PARA SIEMPRE")
                : translatedUpper(text, "audio.retencion_dias",
                        days + " DÍAS", days);
    }

    static void cycleVoiceRetention(Properties properties) {
        adjustVoiceRetention(properties, 1);
    }

    static void adjustVoiceRetention(Properties properties, int direction) {
        int current = voiceRetentionDays(properties);
        int index = 0;
        for (int i = 0; i < VOICE_RETENTION_DAYS.length; i++) {
            if (VOICE_RETENTION_DAYS[i] == current) {
                index = i;
                break;
            }
        }
        int next = VOICE_RETENTION_DAYS[Math.floorMod(index
                + Integer.signum(direction), VOICE_RETENTION_DAYS.length)];
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
        properties.setProperty("master_volume",
                Float.toString(DEFAULT_MASTER_VOLUME));
        for (TogglePage page : AUDIO_PAGES) {
            for (ToggleOption option : page.options()) {
                if (!includeGlobalRules
                        && ("tts_server".equals(option.key())
                        || "voice_messages".equals(option.key()))) {
                    continue;
                }
                properties.setProperty(option.key(),
                        Boolean.toString(option.fallback()));
            }
        }
        properties.setProperty("audio_mic_enabled",
                Boolean.toString(microphoneAvailable));
        properties.setProperty("audio_voice_note_retention_days", "90");
        properties.setProperty(GdxAudioDevices.OUTPUT_KEY, "");
        properties.setProperty(GdxAudioDevices.CAPTURE_KEY, "");
    }

    /** Restores only settings that have a real GDX appearance consumer. */
    static void restoreAppearanceDefaults(Properties properties,
            boolean includeStartupGraphics) {
        // The GDX migration deliberately keeps the approved visual reference's
        // Goliat deck as its product default. Every other shared appearance value
        // below mirrors Swing's factory reset.
        properties.setProperty("baraja", "goliat");
        properties.setProperty("trasera", "default");
        properties.setProperty("color_tapete", "verde");
        properties.setProperty("nivel_luz", "50");
        for (TogglePage page : APPEARANCE_PAGES) {
            for (ToggleOption option : page.options()) {
                properties.setProperty(option.key(),
                        Boolean.toString(option.fallback()));
            }
        }
        for (GdxAppearanceOptions.Choice choice
                : GdxAppearanceOptions.ANIMATION_CHOICES) {
            properties.setProperty(choice.key(), choice.fallback());
        }
        if (includeStartupGraphics) {
            properties.setProperty(GdxWindowMode.PREFERENCE_KEY,
                    GdxWindowMode.BORDERLESS.persistedValue());
            properties.setProperty("gdx_msaa_samples", "4");
        }
    }

    private static int voiceRetentionDays(Properties properties) {
        try {
            int value = Integer.parseInt(properties.getProperty(
                    "audio_voice_note_retention_days", "90"));
            for (int allowed : VOICE_RETENTION_DAYS) {
                if (value == allowed) return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return 90;
    }

    private static String pageTranslationKey(String title) {
        return switch (title) {
            case "GENERAL" -> "gdx.settings.page.general";
            case "MÚSICA" -> "gdx.settings.page.music";
            case "ACCIONES" -> "gdx.settings.page.actions";
            case "CARTAS" -> "gdx.settings.page.cards";
            case "PARTIDA" -> "gdx.settings.page.game";
            case "TURNO Y AVISOS" -> "gdx.settings.page.turn_alerts";
            case "SALA" -> "gdx.settings.page.room";
            case "PANTALLA" -> "gdx.settings.page.display";
            case "AVISOS Y CHAT" -> "gdx.settings.page.alerts_chat";
            case "CHAT" -> "gdx.settings.page.chat";
            case "VOZ LOCAL" -> "gdx.settings.page.local_voice";
            case "DISPOSITIVOS" -> "gdx.settings.page.devices";
            case "CINEMÁTICAS" -> "gdx.settings.page.cinematics";
            case "MESA Y CARTAS" -> "gdx.settings.page.table_cards";
            case "MOVIMIENTO" -> "gdx.settings.page.movement";
            case "INFORMACIÓN" -> "gdx.settings.page.information";
            case "CAPTURA Y VISTA" -> "gdx.settings.page.capture_view";
            case "RITMO Y ESTILO" -> "gdx.settings.page.pace_style";
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
            case "SESIÓN" -> "gdx.settings.game.session";
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
        boolean music = Boolean.parseBoolean(properties.getProperty(
                "musica", "true"));
        boolean effects = Boolean.parseBoolean(properties.getProperty(
                "sonido_efectos", "true"));
        boolean cinematics = Boolean.parseBoolean(properties.getProperty(
                "cinematicas", "true"));
        return switch (option.gate()) {
            case NONE -> true;
            case SOUND -> soundEnabled;
            case MUSIC -> soundEnabled && music;
            case EFFECTS -> soundEnabled && effects;
            // GDX intentionally has no global "disable every animation"
            // switch: its semantic animations are part of the renderer
            // contract.  Keep the gate for the individual rows, but never let
            // a hidden legacy Swing preference disable the whole GDX table.
            case ANIMATIONS -> true;
            case CINEMATICS -> cinematics;
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
