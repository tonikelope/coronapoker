/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.tonikelope.coronapoker.core.BlindStructureRules;
import com.tonikelope.coronapoker.core.BlindStructureCatalog;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableCommandSink;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.AutoActionResolver;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import com.tonikelope.coronapoker.core.audio.VoiceWavContract;
import com.tonikelope.coronapoker.DebugLog;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Production GDX table presentation. Poker rules, player state and causal
 * ordering come exclusively from the renderer-neutral table snapshot/event
 * contract; this class owns GPU presentation and user-command forwarding only.
 */
final class CoronaPokerGdxTable extends ApplicationAdapter {

    private static final Logger LOGGER = Logger.getLogger(
            CoronaPokerGdxTable.class.getName());

    private static final float BASE_WIDTH = 1920f;
    private static final float BASE_HEIGHT = 1080f;
    private static final float INTRO_SECONDS = 4.8f;
    static final float FINAL_AMOUNT_ROLL_SECONDS = 1.5f;
    static final float FINAL_AMOUNT_BLINK_STEP_SECONDS = 0.13f;
    static final int FINAL_AMOUNT_BLINK_STEPS = 6;
    static final float FINAL_SUMMARY_REVEAL_SECONDS = 0.60f;
    private static final float INTRO_LOGO_DOCK_START = 3.42f;
    private static final float INTRO_LOGO_DOCK_SECONDS = 1.28f;
    private static final float INTRO_LIGHT_SWITCH_TIME = 2.0f;
    private static final float INTRO_LIGHT_FADE_SECONDS = 0.34f;
    private static final float INTRO_CARD_APPEAR_SECONDS = 0.28f;
    private static final float INTRO_DARKNESS_ALPHA = 0.78f;
    private static final int INTRO_CARD_COUNT = 52;
    private static final float INTRO_CARD_CLEAR_START = 2.02f;
    private static final String[] INTRO_CARD_RANKS = {
        "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"
    };
    private static final String[] INTRO_CARD_SUITS = {"C", "D", "P", "T"};
    private static final String[] HAND_TRANSLATION_KEYS = {
        "hand.high_card", "hand.one_pair", "hand.two_pair",
        "hand.three_of_a_kind", "hand.straight", "hand.flush",
        "hand.full_house", "hand.four_of_a_kind",
        "hand.straight_flush", "hand.royal_flush"
    };
    private static final Map<String, String> LEGACY_HAND_NAME_KEYS
            = legacyHandNameKeys();
    private static final int STAR_COUNT = 150;
    private static final int SEAT_COUNT = 10;
    private static final int FRAME_SAMPLE_COUNT = 720;
    private static final float DEFAULT_CARD_FLIP_SECONDS = 0.620f;
    private static final float CARD_FLIP_END_DELAY_SECONDS = 0.100f;
    // Match the original GDX demo: the flop flips as a readable cascade, with
    // each card starting 200 ms after the previous one while their turns
    // overlap. Turn and river remain single-card reveals.
    private static final float COMMUNITY_FLIP_STAGGER_SECONDS = 0.200f;
    private static final float FOLD_DISABLE_SECONDS = 0.30f;
    static final float PARTIAL_HAND_ROLL_SECONDS = 0.150f;
    // Disabled cards remain fully coloured. Only their opacity changes, so a
    // folded/losing hand is still readable instead of turning into a muddy
    // near-invisible silhouette.
    static final float DISABLED_CARD_ALPHA = 0.28f;
    private static final float LOCAL_CARD_FAN_ANGLE = 8.5f;
    private static final float LOCAL_SWAP_DELAY = 0.14f;
    private static final float LOCAL_SWAP_SECONDS = 0.68f;
    private static final float RIVAL_HOLE_CARD_WIDTH = 125f;
    private static final float LOCAL_HOLE_CARD_WIDTH = 184f;
    private static final float COMMUNITY_CARD_MAX_WIDTH = 140f;
    // All positional pucks share one physical diameter. The GDX seat tucks its
    // private cards under the avatar, so Swing's nominal 80% icon ratio looks
    // oversized here; 54% preserves the perceived CoronaPoker proportion.
    private static final float POSITION_CHIP_SIZE = RIVAL_HOLE_CARD_WIDTH * 0.54f;
    private static final float POSITION_CHIP_HUD_GAP = 7f;
    private static final float AVATAR_SIZE = 72f;
    private static final float AVATAR_ACTIVE_RADIUS = 61f;
    private static final float AVATAR_OUTER_RADIUS = 52f;
    private static final float AVATAR_RIM_RADIUS = 45f;
    private static final float AVATAR_INNER_RADIUS = 40f;
    private static final float ALL_IN_FIRE_WIDTH = 154f;
    private static final float ALL_IN_FIRE_HEIGHT = 184f;
    private static final float AVATAR_ZOOM_HOVER_SECONDS = 0.250f;
    private static final float AVATAR_ZOOM_FACTOR = 2f;
    private static final float AVATAR_ZOOM_MAX_HEIGHT_RATIO = 0.45f;
    private static final int SHUFFLE_AUDIO_STOP_FRAME = 53;
    private static final float SHUFFLE_AUDIO_FALLBACK_SECONDS = 2.720f;
    private static final float SHUFFLE_TEXT_CYCLE_SECONDS = 0.300f;
    private static final float CHIP_FLIGHT_DELAY = 0.12f;
    private static final float CHIP_FLIGHT_SECONDS = 0.92f;
    private static final int CLASSIC_DEAL_BASE_MILLIS = 250;
    private static final float CARD_CORNER_RADIUS = 0.075f;
    private static final float CARD_EDGE_SOFTNESS = 0.006f;
    private static final float PLAYER_POD_WIDTH = 286f;
    private static final float PLAYER_POD_HEIGHT = 120f;
    private static final float POT_PANEL_HEIGHT = 82f;
    private static final float POT_BOARD_GAP = 24f;
    private static final float COMMUNITY_TIMER_HEIGHT = 14f;
    private static final float COMMUNITY_TIMER_Y_OFFSET = 18f;
    private static final float COMMUNITY_HUD_Y_OFFSET = 48f;
    private static final float COMMUNITY_HUD_HEIGHT = 30f;
    private static final float LOCAL_HUD_Y = 12f;
    private static final float LOCAL_HUD_HEIGHT = 126f;
    private static final float LOCAL_HUD_SAFE_TOP = LOCAL_HUD_Y + LOCAL_HUD_HEIGHT + 32f;
    private static final float FAST_BAR_X = 18f;
    private static final float FAST_BAR_Y = 18f;
    private static final float FAST_BUTTON_SIZE = 52f;
    private static final float FAST_BUTTON_GAP = 7f;
    private static final float FAST_BAR_PADDING = 7f;
    private static final float FAST_BAR_HIDE_DELAY = 1f;
    private static final float FAST_BAR_FADE_SECONDS = 0.40f;
    /** Same client-side recovery notice used by Swing's GameFrame.HALT_PAUSE. */
    private static final float RECOVERY_STOP_NOTICE_SECONDS = 5f;
    private static final int QUICK_CHAT_HISTORY_LIMIT = 200;
    private static final float QUICK_CHAT_WIDTH_RATIO = 0.30f;
    private static final float QUICK_CHAT_SCREEN_MARGIN = 18f;
    private static final float IMAGE_SEND_COOLDOWN_SECONDS = 2f;
    private static final float VOICE_RECORD_MAX_SECONDS = 15f;
    private static final long FELT_DOUBLE_CLICK_NANOS = 500_000_000L;
    private static final float FELT_CLICK_DRIFT = 14f;
    private static final float FELT_DOUBLE_CLICK_DRIFT = 32f;
    private static final String[] FAST_BUTTON_TEXT_KEYS = {
        "settings.ajustes", "chat.chat_rapido", "audio.nota_de_voz",
        "chat.enviar_imagen", "rebuy.recomprar_2",
        "log.registro_de_la_timba", "view.pantalla_completa",
        "menu.detener_timba", "game.salir_de_la_timba_2"
    };
    private static final float LOCAL_HOLE_CENTER_DISTANCE = 102f;
    private static final float RIVAL_REVEAL_HUD_GAP = 20f;
    private static final float RIVAL_REVEAL_TOP_MARGIN = 8f;
    private static final float RIVAL_CARD_FAN_ANGLE = 7f;
    private static final float RIVAL_HAND_VERTICAL_OFFSET = -22f;
    private static final float RIVAL_HAND_CENTER_X_INSET = 183f;
    private static final float RIVAL_HAND_SIDE_DISTANCE = 34f;
    private static final float RIVAL_LEFT_CARD_SHIFT = 16f;
    private static final int UI_NONE = 0;
    private static final int UI_SETTINGS = 2;
    private static final int UI_GAME_LOG = 3;
    private static final int UI_CHAT = 4;
    private static final int UI_CARD_VIEWER = 5;
    private static final int UI_SCREENSHOTS = 6;
    private static final int UI_HAND_GENERATOR = 7;
    private static final int EMOJI_COUNT = 1826;
    private static final int EMOJI_COLUMNS = 8;
    private static final int EMOJI_ROWS = 5;
    private static final int EMOJI_PAGE_SIZE = EMOJI_COLUMNS * EMOJI_ROWS;
    private static final Pattern EMOJI_TOKEN = Pattern.compile("#([0-9]{1,4})#");
    private static final String[] SETTINGS_SESSION_ACTIONS = {
        "Pantalla completa", "Visor de capturas", "Registro de la timba",
        "Reglas de Robert", "Generador de jugadas", "Marcar última mano",
        "Forzar reconexión de jugadores", "Detener timba",
        "Salir de la timba"
    };
    private static final String[] SETTINGS_SESSION_ACTION_KEYS = {
        "fullscreen", "screenshots", "game_log", "robert_rules",
        "hand_generator", "last_hand", "force_reconnect", "stop_game",
        "leave_game"
    };
    private static final float SETTINGS_DEBUG_LINE_HEIGHT = 25f;
    private static final float SETTINGS_DEBUG_VIEWPORT_HEIGHT = 394f;
    private static final float GAME_LOG_LINE_HEIGHT = 31f;
    /**
     * The settings backdrop is deliberately rendered below native resolution.
     * It is going to be blurred and darkened anyway, so processing every 4K
     * pixel at the monitor refresh rate only burns fill-rate without adding
     * visible detail.
     */
    private static final int SETTINGS_BACKDROP_DOWNSAMPLE = 2;
    private static final float SETTINGS_TAB_GAP = 62f;
    private static final int SHORTCUT_ROWS_PER_PAGE = 5;
    private static final Properties EMPTY_SETTINGS_PROPERTIES =
            new Properties();
    private static final float[][] TEN_PLAYER_SEAT_OUTLINE = {
        {0.500f, 0.185f},
        {0.125f, 0.280f}, {0.024f, 0.530f},
        {0.024f, 0.780f}, {0.250f, 0.890f},
        {0.500f, 0.930f}, {0.750f, 0.890f},
        {0.976f, 0.780f}, {0.976f, 0.530f},
        {0.875f, 0.280f}
    };
    private static final float[][][] SEAT_LAYOUTS = createSeatLayouts();

    private static final String CARD_VERTEX_SHADER = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_color.a = v_color.a * (255.0 / 254.0);\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";

    private static final String CARD_FRAGMENT_SHADER = "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform sampler2D u_frontTexture;\n"
            + "uniform float u_cornerRadius;\n"
            + "uniform float u_edgeSoftness;\n"
            + "uniform float u_perspective;\n"
            + "uniform float u_flipAngle;\n"
            + "uniform float u_cardAspect;\n"
            + "uniform vec4 u_overlay;\n"
            + "void main() {\n"
            + "    vec2 sourceUv = v_texCoords;\n"
            + "    if (u_perspective > 0.5) {\n"
            + "        float x = (v_texCoords.x - 0.5) * 1.5;\n"
            + "        float y = (v_texCoords.y - 0.5) * 1.5 * u_cardAspect;\n"
            + "        float halfWidth = 0.5;\n"
            + "        float depth = 2.0;\n"
            + "        float projectedX = halfWidth * cos(u_flipAngle);\n"
            + "        float projectedZ = halfWidth * sin(u_flipAngle);\n"
            + "        float denominator = projectedX * depth - x * projectedZ;\n"
            + "        if (abs(denominator) < 0.00001) discard;\n"
            + "        float horizontal = x * depth / denominator;\n"
            + "        if (abs(horizontal) > 1.0) discard;\n"
            + "        float perspectiveScale = depth / (depth + horizontal * projectedZ);\n"
            + "        sourceUv.y = y / (perspectiveScale * u_cardAspect) + 0.5;\n"
            + "        if (sourceUv.y < 0.0 || sourceUv.y > 1.0) discard;\n"
            + "        float facing = u_flipAngle > 1.5707963 ? -horizontal : horizontal;\n"
            + "        sourceUv.x = facing * 0.5 + 0.5;\n"
            + "    }\n"
            + "    vec2 centered = abs(sourceUv - vec2(0.5));\n"
            + "    vec2 corner = centered - (vec2(0.5) - vec2(u_cornerRadius));\n"
            + "    float distanceToCorner = length(max(corner, vec2(0.0))) - u_cornerRadius;\n"
            + "    float mask = 1.0 - smoothstep(-u_edgeSoftness, u_edgeSoftness, distanceToCorner);\n"
            + "    vec4 backPixel = texture2D(u_texture, sourceUv);\n"
            + "    vec4 frontPixel = texture2D(u_frontTexture, sourceUv);\n"
            + "    vec4 pixel = (u_flipAngle > 1.5707963 ? frontPixel : backPixel) * v_color;\n"
            + "    pixel.rgb = mix(pixel.rgb, u_overlay.rgb, u_overlay.a);\n"
            + "    pixel.a *= mask;\n"
            + "    if (pixel.a <= 0.001) discard;\n"
            + "    gl_FragColor = pixel;\n"
            + "}\n";

    private static final String AVATAR_FRAGMENT_SHADER = "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "void main() {\n"
            + "    vec2 radial = (v_texCoords - vec2(0.5)) * 2.0;\n"
            + "    float mask = 1.0 - smoothstep(0.94, 1.0, length(radial));\n"
            + "    vec4 pixel = texture2D(u_texture, v_texCoords) * v_color;\n"
            + "    pixel.a *= mask;\n"
            + "    if (pixel.a <= 0.001) discard;\n"
            + "    gl_FragColor = pixel;\n"
            + "}\n";

    /** Buoyant multi-plume fire used only for the persistent ALL-IN state. */
    private static final String ALL_IN_FIRE_FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            uniform float u_time;
            uniform float u_seed;
            uniform float u_alpha;
            uniform float u_bloom;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                        mix(hash(i + vec2(0.0, 1.0)),
                                hash(i + vec2(1.0, 1.0)), f.x), f.y);
            }

            float fbm(vec2 p) {
                float value = 0.0;
                float amplitude = 0.5;
                for (int octave = 0; octave < 4; octave++) {
                    value += noise(p) * amplitude;
                    p = p * 2.03 + vec2(17.7, 9.2);
                    amplitude *= 0.5;
                }
                return value;
            }

            float plume(float x, float y, float centre, float width,
                    float height, float phase) {
                float relativeY = clamp(y / height, 0.0, 1.35);
                float slowBend = sin(relativeY * 4.7 - u_time * 1.28 + phase)
                        * (0.022 + relativeY * 0.112);
                float turbulentBend = (fbm(vec2(relativeY * 3.1
                        - u_time * 0.76, phase + u_seed)) - 0.5)
                        * (0.042 + relativeY * 0.27);
                float spine = centre + slowBend + turbulentBend;

                // Real flames do not converge towards a geometric apex. The
                // neck keeps a small, breathing radius and the noisy cap
                // erodes it into changing rounded forks instead of a triangle.
                float breathing = 0.82 + 0.25 * noise(vec2(relativeY * 4.4
                        - u_time * 1.72, phase * 2.7));
                float neck = 0.17 + 0.83
                        * pow(max(0.0, 1.0 - relativeY), 0.64);
                float scallop = 0.88 + 0.14 * sin(relativeY * 15.0
                        - u_time * 2.05 + phase * 1.9);
                float radius = width * neck * breathing * scallop;
                float distanceFromCore = abs(x - spine);
                float edgeNoise = fbm(vec2(x * 8.2 + phase,
                        y * 9.7 - u_time * 2.55));
                float edge = 1.0 - smoothstep(radius * (0.52
                        + edgeNoise * 0.13), radius + 0.035,
                        distanceFromCore);

                float capNoise = fbm(vec2(x * 5.8 + phase,
                        -u_time * 1.86 + phase));
                float raggedCap = height + (capNoise - 0.5) * 0.23
                        - 0.075 * pow(distanceFromCore
                                / max(width, 0.01), 2.0);
                float cap = 1.0 - smoothstep(raggedCap - 0.105,
                        raggedCap + 0.035, y);

                float upper = smoothstep(0.54, 0.96, relativeY);
                float forkSide = sin((x - spine) * 15.0 + phase
                        + edgeNoise * 4.0);
                float fork = smoothstep(0.18, 0.82,
                        noise(vec2(x * 9.3 + phase,
                                y * 10.6 - u_time * 3.05))
                                + forkSide * 0.12);
                float breakup = mix(1.0, fork, upper * 0.78);
                return edge * cap * breakup;
            }

            void main() {
                vec2 uv = vec2(v_texCoords.x, 1.0 - v_texCoords.y);
                float y = clamp(uv.y, 0.0, 1.0);
                float x = (uv.x - 0.5) * 2.0;
                float seedA = sin(u_seed * 1.71);
                float seedB = sin(u_seed * 2.93 + 1.4);
                float seedC = sin(u_seed * 4.17 + 3.1);

                float leftHeight = 0.58 + 0.20 * (0.5 + 0.5
                        * sin(u_time * 1.21 + u_seed * 2.1));
                float centreHeight = 0.70 + 0.24 * (0.5 + 0.5
                        * sin(u_time * 0.93 + u_seed * 3.7));
                float rightHeight = 0.52 + 0.27 * (0.5 + 0.5
                        * sin(u_time * 1.47 + u_seed * 1.3));
                float left = plume(x, y, -0.43 + seedA * 0.07, 0.50,
                        leftHeight, 1.2 + u_seed);
                float centre = plume(x, y, seedB * 0.08, 0.56,
                        centreHeight, 5.4 + u_seed * 0.7);
                float right = plume(x, y, 0.42 + seedC * 0.06, 0.46,
                        rightHeight, 9.1 + u_seed * 1.3);
                float sideTongue = plume(x, y, -0.13 + seedC * 0.18,
                        0.24, 0.88 + seedA * 0.07,
                        13.7 + u_seed * 0.5);

                float bedNoise = fbm(vec2(x * 3.1 + u_seed,
                        y * 5.0 - u_time * 2.15));
                float bed = (1.0 - smoothstep(0.68 + bedNoise * 0.17,
                        1.03, abs(x)))
                        * (1.0 - smoothstep(0.08, 0.32, y));
                float flame = max(max(left, centre), max(right, sideTongue));
                flame = max(flame, bed * (0.78 + bedNoise * 0.22));
                flame *= smoothstep(0.0, 0.045, y);

                float upwardNoise = fbm(vec2(x * 5.3 + u_seed * 0.8,
                        y * 7.6 - u_time * 3.25));
                float holes = smoothstep(0.73, 0.91, upwardNoise)
                        * smoothstep(0.18, 0.82, y);
                flame *= 1.0 - holes * (0.52 - u_bloom * 0.30);

                float localFuel = max(max(left, centre), max(right,
                        sideTongue));
                float heat = clamp(localFuel * (1.16 - y * 0.70)
                        + bed * 0.48, 0.0, 1.0);
                vec3 colour = mix(vec3(0.34, 0.004, 0.001),
                        vec3(0.98, 0.075, 0.002),
                        smoothstep(0.04, 0.34, flame));
                colour = mix(colour, vec3(1.0, 0.48, 0.008),
                        smoothstep(0.24, 0.68, heat));
                colour = mix(colour, vec3(1.0, 0.88, 0.31),
                        smoothstep(0.62, 0.91, heat));
                colour = mix(colour, vec3(1.0, 0.97, 0.76),
                        smoothstep(0.88, 1.0, heat) * (1.0 - y));

                float smokeNoise = fbm(vec2(x * 2.7 + u_seed * 1.9,
                        y * 3.7 - u_time * 0.62));
                float smoke = smoothstep(0.57, 0.83, smokeNoise)
                        * smoothstep(0.57, 0.82, y)
                        * (1.0 - smoothstep(0.94, 1.0, y))
                        * (1.0 - smoothstep(0.08, 0.38, flame));
                vec3 smokeColour = mix(vec3(0.075, 0.057, 0.048),
                        vec3(0.19, 0.13, 0.09), smokeNoise);

                float alpha = max(flame * (0.82 + upwardNoise * 0.18),
                        smoke * 0.14 * (1.0 - u_bloom));
                if (u_bloom > 0.5) {
                    colour = vec3(1.0, 0.17, 0.006);
                    alpha = smoothstep(0.03, 0.55, flame) * 0.19;
                } else {
                    colour = mix(smokeColour, colour,
                            smoothstep(0.0, 0.14, flame));
                }
                alpha *= texture2D(u_texture, v_texCoords).a
                        * v_color.a * u_alpha;
                if (alpha <= 0.006) discard;
                gl_FragColor = vec4(colour, alpha);
            }
            """;

    private static final String BACKDROP_VERTEX_SHADER =
            "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";

    private static final String BACKDROP_BLUR_FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_texelSize;\n"
            + "uniform vec2 u_direction;\n"
            + "void main() {\n"
            + "    vec2 step = u_texelSize * u_direction;\n"
            + "    vec4 c = texture2D(u_texture, v_texCoords) * 0.227027;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 1.384615) * 0.316216;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 1.384615) * 0.316216;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 3.230769) * 0.070270;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 3.230769) * 0.070270;\n"
            + "    gl_FragColor = c * v_color;\n"
            + "}\n";

    private static final Color BACKGROUND_BOTTOM = new Color(0x02050cff);
    private static final Color FELT_SHADE_TOP = new Color(0x07111f1f);
    private static final Color FELT_SHADE_BOTTOM = new Color(0x02050c52);
    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color ORANGE = new Color(0xff6b27ff);
    private static final Color PANEL = new Color(0x101a2ee6);
    private static final Color SEAT_RIM = new Color(0x647594ff);
    private static final Color SEAT_INNER = new Color(0x111a2aff);
    private static final Color STACK_GREEN = new Color(0x9fffd2ff);
    private static final Color SWING_STACK_GREEN = new Color(0x339900ff);
    private static final Color POT_GOLD = new Color(0xffe07aff);
    private static final Color ACTIVE_TURN_GOLD = new Color(0xffad20ff);
    private static final Color BUTTON_LINE = new Color(0x31445fff);
    private static final Color FOLD_RED = new Color(0xd9343fff);
    private static final Color PAUSE_RED = new Color(0xd71920ff);
    private static final Color CONTEXT_EXIT = new Color(0xff7d86ff);
    private static final Color FOLDED_AVATAR = new Color(0.35f, 0.35f, 0.38f, 0.72f);
    private static final float FOLDED_ACTION_SURFACE_ALPHA = 0.34f;
    private static final float FOLDED_ACTION_TEXT_ALPHA = 0.56f;
    private static final float ACTIVE_ACTION_SURFACE_ALPHA = 0.92f;
    // Exact semantic palette from Swing LocalPlayer/RemotePlayer. These are
    // learned gameplay signals, not decorative colors for the new renderer.
    private static final Color LEGACY_FOLD = new Color(0x808080ff);
    private static final Color SWING_FOLD_BUTTON = new Color(0x404040ff);
    private static final Color SWING_FOLD_DANGER_BUTTON = new Color(0xff0000ff);
    private static final Color LEGACY_CHECK = new Color(0x008200ff);
    private static final Color LEGACY_CALL = new Color(0xffffffff);
    private static final Color LEGACY_BET = new Color(0xffff00ff);
    private static final Color LEGACY_RERAISE = new Color(0x7d05e1ff);
    private static final Color LEGACY_ALL_IN = new Color(0x000000ff);
    private static final Color ARMED_ACTION_GREEN = new Color(0x20df72ff);
    private static final Color ARMED_ACTION_TEXT = new Color(0x071b12ff);
    static final Color LEGACY_THINKING = new Color(0xcccccc4b);
    private static final Color LEGACY_SHOW = new Color(0x3399ffff);
    private static final Color LEGACY_WINNER = new Color(0x00ff00ff);
    private static final Color LEGACY_LOSER = new Color(0xff0000ff);
    // Montecarlo winner state must remain unmistakable even over a darkened
    // all-in seat. The former olive green lost contrast at 100%.
    private static final Color PARTIAL_HAND_WINNER = new Color(0x72df00ff);
    private static final Color PARTIAL_HAND_LOSER = new Color(230f / 255f,
            70f / 255f, 0f, 1f);
    // Keep Swing's red/green semantics, but avoid pure primaries blooming in
    // the large final-screen typography against bright custom felts.
    private static final Color FINAL_WINNER = new Color(0x10d744ff);
    private static final Color FINAL_LOSER = new Color(0xe63238ff);
    static final Color LEGACY_TIMEOUT = new Color(0xff00ffff);
    private static final Color LATENCY_GREEN = new Color(0x4caf50ff);
    private static final Color LATENCY_YELLOW = new Color(0xffc107ff);
    private static final Color LATENCY_ORANGE = new Color(0xff9800ff);
    private static final Color LATENCY_RED = new Color(0xf44336ff);
    private static final Color LATENCY_STALE = new Color(0x9e9e9eff);
    private static final long LATENCY_STALE_MILLIS = 15_000L;

    private int detectedRefreshRate;
    private final GdxTableViewState liveState;
    private final TableCommandSink commands;
    private final Runnable onReady;
    private final Runnable onIntroLightsOn;
    private final GdxGameLogSink gameLog;
    private final PreferencesService preferences;
    private final GdxShortcutBindings shortcutBindings;
    private final GdxAudioControl audioControl;
    private final GdxTableChatSession tableChat;
    private final GdxTextToSpeechPlayback textToSpeech;
    private final GdxGamePresentationSettings presentationSettings;
    private final GdxGameText gameText;
    private final boolean tableRebuyAllowed;
    private final boolean tableHost;
    private final boolean startupIntroOnly;
    private final Star[] stars = new Star[STAR_COUNT];
    private final Seat[] seats = new Seat[SEAT_COUNT];
    private final float[] frameSamples = new float[FRAME_SAMPLE_COUNT];
    private final float[] frameScratch = new float[FRAME_SAMPLE_COUNT];
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();
    private final ArrayDeque<GdxTableDialog> dialogQueue = new ArrayDeque<>();
    private final Map<String, SeatChatNotice> seatChatNotices = new HashMap<>();
    private final ArrayDeque<SilentChatNotice> silentChatNotices =
            new ArrayDeque<>();
    private final Map<String, Texture> tableAvatarTextures = new HashMap<>();
    private final Set<String> blockedSeatMediaNotices = new HashSet<>();
    private final Map<Integer, Texture> emojiTextures = new HashMap<>();
    private final GdxChatGalleryMedia tableGalleryMedia =
            new GdxChatGalleryMedia();
    private GdxTableDialog activeDialog;
    private GdxTableDialog terminationConfirmation;
    private GdxTableDialog settingsDiscardConfirmation;
    private Runnable settingsDiscardAfterClose;
    private GdxTableDialog forceReconnectConfirmation;
    private TableSessionSummary finalSummary;
    private CompletableFuture<Void> finalSummaryBarrier;
    private float finalSummaryOpenedAt;
    private int finalSummaryPage;
    private boolean finalSummaryScreenshotTaken;
    private boolean finalContinueRequested;
    private boolean finalStatsRequested;
    private boolean finalApplicationExitRequested;
    private CompletableFuture<Void> recoveryStopBarrier;
    private float recoveryStopUntil;
    private boolean finalExitPending;
    private boolean terminationRequested;
    private boolean recoverableTerminationRequested;
    private TableVisualEvent.PreparationStatus.Phase preparationPhase
            = TableVisualEvent.PreparationStatus.Phase.STARTING_DEALER;

    private int uiLayer = UI_NONE;
    private final GdxHandGeneratorModel handGenerator
            = new GdxHandGeneratorModel();
    private float uiOpenedAt;
    private float musicVolume = 0.40f;
    private float effectsVolume = 1.0f;
    private boolean autoButtons;
    private boolean autoActionPersist;
    private boolean autoModeConfirm;
    private boolean autoCallEnabled;
    private double autoCallMax;
    private boolean confirmActions;
    private boolean autoRebuy;
    private boolean fastBarExpanded;
    private float fastBarOutsideSeconds;
    private float fastBarAlpha = 1f;
    private int armedHudTarget;
    private final Set<String> allInActionSoundsPlayed = new HashSet<>();
    private final Map<String, LiveHandProbability> liveHandProbabilities =
            new HashMap<>();
    private int queuedPreAction;
    /*
     * A local turn is announced in three causal steps: PreActionControls(false),
     * TurnTimer(START), then ActionControls(enabled).  Rendering may happen
     * between those events.  Never resolve an armed AUTO choice against the
     * disabled controls left by the previous turn during that small window.
     */
    private boolean autoActionControlsReady;
    private float autoActionEligibleAt = Float.POSITIVE_INFINITY;
    private float gameLogScroll;
    private int gameLogLineCount;
    private final Map<String, List<GdxGameLogFormatter.Run>> gameLogRunCache
            = new HashMap<>();
    private boolean gameLogScrollDragging;
    private int gameLogSelectionAnchor = -1;
    private int gameLogSelectionCaret = -1;
    private boolean gameLogSelectionDragging;
    private boolean gameLogEditMenuOpen;
    private float gameLogEditMenuX;
    private float gameLogEditMenuY;
    private final GdxSettingsSession settingsSession =
            new GdxSettingsSession();
    private int settingsGamePage;
    private int settingsAppearancePage;
    private int settingsAudioPage;
    private float settingsDebugScroll;
    private int settingsDebugLineCount;
    private boolean settingsDebugScrollDragging;
    private final GdxVoiceNoteLibrary voiceNoteLibrary =
            new GdxVoiceNoteLibrary();
    private boolean voiceNotesOpen;
    private boolean voiceNotesLoading;
    private List<GdxVoiceNoteLibrary.Entry> voiceNotes = List.of();
    private int voiceNotesPage;
    private GdxVoiceNoteLibrary.Entry voiceNotePlaying;
    private GdxVoiceNoteLibrary.Entry voiceNoteDeleteConfirmation;
    private boolean voiceNotesPurgeConfirmation;
    private int shortcutPage;
    private String shortcutCaptureId;
    private String shortcutStatus = "";
    private boolean shortcutCaptureConsumed;
    private int emojiPage;
    private boolean emojiPickerOpen;
    private boolean chatImageMode;
    private boolean chatSending;
    private List<String> tableImageHistory = List.of();
    private float tableImageSendAllowedAt;
    private String chatDraft = "";
    private final GdxTextEditState chatEdit = new GdxTextEditState();
    private final GdxTextEditState dialogAmountEdit = new GdxTextEditState();
    private final GdxKeyRepeat textDeleteRepeat = new GdxKeyRepeat();
    private boolean chatEditMenuOpen;
    private boolean chatPointerSelectionDragging;
    private float chatEditMenuX;
    private float chatEditMenuY;
    private String chatError = "";
    private String avatarHoverNickname;
    private float avatarHoverStartedAt;
    private String avatarZoomNickname;
    private final Rectangle avatarZoomBounds = new Rectangle();
    private final ArrayList<String> quickChatHistory = new ArrayList<>();
    private int quickChatHistoryIndex;
    private String quickChatPendingDraft = "";
    private boolean quickChatAutoClose = true;
    private float quickChatScroll;
    private float quickChatScrollMaximum;
    private boolean quickChatScrollDragging;
    private int quickChatMessageCount;
    private volatile GdxVoiceRecorder voiceRecorder;
    private boolean voiceOpening;
    private boolean voiceLive;
    private boolean voiceStopping;
    private boolean micPointerHeld;
    private float voiceLiveAt;
    private float voiceStatusAt;
    private String voiceStatus = "";
    private int seatChatBlockHand = Integer.MIN_VALUE;
    private boolean settingsAutoRebuySnapshot;
    private boolean settingsTextToSpeechSnapshot;
    private boolean settingsVoiceMessagesSnapshot;
    private boolean settingsTextToSpeechDraft = true;
    private boolean settingsVoiceMessagesDraft = true;
    private GdxWindowMode settingsOpenedWindowMode;
    private GameConfigCodecV1.Configuration liveSettingsOpened;
    private GameConfigCodecV1.Configuration liveSettingsDraft;
    private NewGameTableDraft.BotDifficulty tableBotDifficulty;
    private NewGameTableDraft.BotDifficulty settingsBotDifficultySnapshot;
    private NewGameTableDraft.BotDifficulty liveBotDifficultyDraft;

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private ShaderProgram roundedCardShader;
    private ShaderProgram avatarShader;
    private ShaderProgram allInFireShader;
    private ShaderProgram backdropBlurShader;
    private FrameBuffer settingsBackdrop;
    private FrameBuffer settingsBlurScratch;
    private FrameBuffer disabledHoleCardsLayer;

    private BitmapFont uiFont;
    private BitmapFont smallFont;
    private BitmapFont playerNameFont;
    private BitmapFont stackFont;
    private BitmapFont actionFont;
    private BitmapFont seatActionFont;
    private BitmapFont callCostFont;
    private BitmapFont pauseFont;
    private BitmapFont finalHeroFont;
    private BitmapFont finalAmountFont;
    private BitmapFont finalTitleFont;
    private BitmapFont finalDetailFont;
    private BitmapFont finalButtonFont;
    private BitmapFont finalCardFont;
    private BitmapFont finalCardBoldFont;
    private BitmapFont gameLogFont;

    private Texture logo;
    private Texture feltTexture;
    private Texture avatarDefault;
    private Texture avatarBot;
    private Texture allInFireCanvas;
    private Texture dealerChip;
    private Texture smallBlindChip;
    private Texture bigBlindChip;
    private Texture logMoneyIcon;
    private Texture logStraddleIcon;
    private Texture logDealerStraddleIcon;
    private Texture soundIcon;
    private Texture muteIcon;
    private Texture blockedSoundIcon;
    private Texture lightsOnIcon;
    private Texture lightsOffIcon;
    private Texture pauseIcon;
    private Texture foldThumbIcon;
    private Texture callThumbIcon;
    private Texture timeoutIcon;
    private Texture fastMenuIcon;
    private Texture[] fastButtonIcons;
    private Texture talkIcon;
    private Texture finalMenuIcon;
    private Texture finalLogIcon;
    private Texture finalStatsIcon;
    private Texture finalContinueIcon;
    private Texture defaultCardBack;
    private Texture[] introCardFaces;
    private Texture[] flyingChips;
    private Texture pot;
    private StreamingGifTextureAnimation gameOverAnimation;
    private GdxTableDialog gameOverAnimationDialog;
    private boolean gameOverAnimationFailureReported;
    private StreamingGifTextureAnimation recoveryAnimation;
    private GdxTableDialog recoveryAnimationDialog;
    private boolean recoveryAnimationFailureReported;
    private final Map<String, Sound> liveCinematicSounds = new HashMap<>();
    private final Map<String, Sound> liveAudioCueSounds = new HashMap<>();
    private final Set<String> failedPreferenceSoundCues = new HashSet<>();
    private final Map<String, Music> liveAudioCueLoops = new HashMap<>();
    private final List<LiveAudioPlayback> liveAudioCueWaits = new ArrayList<>();
    private Sound liveDangerAlertSound;
    private long liveDangerAlertSoundId = -1L;
    private boolean liveAudioLoopsMuted;
    private boolean chatTextToSpeechDucking;
    private boolean gameOverAudioActive;
    private boolean gameOverPreviousLoopsMuted;

    private Sound shuffleSound;
    private Sound dealSound;
    private Sound uncoverSound;
    private Sound checkSound;
    private Sound callSound;
    private Sound betSound;
    private Sound foldSound;
    private Sound allInSound;
    private Sound buttonOnSound;
    private Sound buttonOffSound;
    private Sound balanceCountSound;
    private Sound cardViewerSound;
    private Sound screenshotSound;
    private Sound feltChangeSound;
    private Music backgroundMusic;

    private Texture cardViewerTexture;
    private boolean cardViewerFaceUp;
    private List<Path> screenshotFiles = List.of();
    private int screenshotIndex;
    private Texture screenshotTexture;
    private boolean screenshotRequested;
    private String screenshotError = "";
    private String screenshotToast = "";
    private float screenshotToastUntil;
    private boolean screenshotOperationPending;

    private float tableCenterX;
    private float tableCenterY;
    private float dealerSourceX;
    private float dealerSourceY;
    private float potCenterX;
    private float potCenterY;
    private double lastPotValue = Double.NaN;
    private String lastPotPrefix = "";
    private String potText = "BOTE: 0";

    private float totalTime;
    private float volumeOverlayUntil;
    private float sceneTime;
    private float statsClock;
    private float monitorRefreshPollClock;
    private String activeMonitorName = "";
    private float shuffleSoundDurationSeconds;
    private long shuffleSoundId = -1L;
    private int frameCursor;
    private int frameCount;
    private boolean intro = true;
    private boolean readySignalled;
    private boolean introVisibleSignalled;
    private boolean introLightsSignalled;
    private String statsText = "Midiendo frame pacing...";
    private LivePositionRotation livePositionRotation;
    private final List<LiveActionChip> liveActionChips = new ArrayList<>();
    private PendingCollectBets pendingCollectBets;
    private LiveChipBatch liveChipBatch;
    private final Map<String, Double> livePotContributions = new HashMap<>();
    private final Map<String, Long> liveWinnerStarts = new HashMap<>();
    private LivePayout livePayout;
    private LiveRebuy liveRebuy;
    private LiveInitialStackFill liveInitialStackFill;
    private LiveCinematic liveCinematic;
    private LiveShuffle liveShuffle;
    private final List<LiveCardFlight> liveCardFlights = new ArrayList<>();
    private int liveHoleDealCount;
    private LiveHoleSwap liveHoleSwap;
    private LiveCommunityReveal liveCommunityReveal;
    private LiveHoleReveal liveHoleReveal;
    private LiveHoleFold liveHoleFold;
    private String liveShowdownHoverNickname;
    private final Map<String, Texture> liveCardFaces = new HashMap<>();
    private final Map<String, Texture> liveCardBacks = new HashMap<>();
    private String liveDeck = "goliat";
    private double liveBetAmount;
    private float communityPauseX = Float.NaN;
    private float communityPauseY = Float.NaN;
    private float communityPauseWidth;
    private float communityPauseHeight;
    private float communitySoundX = Float.NaN;
    private float communitySoundY = Float.NaN;
    private float communitySoundWidth;
    private float communitySoundHeight;
    private float communityLightsX = Float.NaN;
    private float communityLightsY = Float.NaN;
    private float communityLightsWidth;
    private float communityLightsHeight;
    private float communityHandX = Float.NaN;
    private float communityHandY = Float.NaN;
    private float communityHandWidth;
    private float communityHandHeight;
    private boolean userLightsOff;
    private boolean leftButtonReleasedThisFrame;
    private final GdxPointerCapture primaryPointer = new GdxPointerCapture();
    private boolean feltClickCandidate;
    private float feltClickDownX;
    private float feltClickDownY;
    private boolean finalSummaryPointerCaptured;
    private int finalSummaryPointerTarget = -1;
    private float finalSummaryPointerDownX;
    private float finalSummaryPointerDownY;
    private long previousFeltClickNanos;
    private float previousFeltClickX;
    private float previousFeltClickY;
    private boolean disposed;
    private final InputProcessor tableInput = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (isClientTransportReconnecting()) return true;
            if (activeDialog != null && activeDialog.isAutoCall()
                    && handleAutoCallAmountEditingKey(keycode)) {
                if (isTextDeletionKey(keycode)) {
                    textDeleteRepeat.press(keycode);
                }
                return true;
            }
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                // A real dialog owns the keyboard completely.  The render-loop
                // handler still reads ESC/ENTER from Gdx.input, but the event
                // must not continue to a control that happens to sit behind it.
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) return true;
            if (uiLayer == UI_SETTINGS && settingsSection()
                    == GdxSettingsContract.Section.SHORTCUTS
                    && shortcutCaptureId != null) {
                boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
                boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                GdxShortcutBindings.Assignment assignment
                        = shortcutBindings.assign(shortcutCaptureId, keycode,
                                alt, control, shift);
                shortcutCaptureConsumed = true;
                if (assignment == GdxShortcutBindings.Assignment.ASSIGNED) {
                    shortcutCaptureId = null;
                    shortcutStatus = "updated";
                } else if (assignment
                        == GdxShortcutBindings.Assignment.CONFLICT) {
                    shortcutStatus = "conflict";
                } else {
                    shortcutStatus = "unsupported";
                }
                return true;
            }
            if (uiLayer == UI_CHAT) chatEditMenuOpen = false;
            if (uiLayer == UI_CHAT && activeDialog == null
                    && handleChatEditingKey(keycode)) {
                if (isTextDeletionKey(keycode)) {
                    textDeleteRepeat.press(keycode);
                }
                return true;
            }
            if (uiLayer == UI_CHAT && !chatImageMode) {
                if (keycode == Input.Keys.UP) {
                    recallQuickChat(-1);
                    return true;
                }
                if (keycode == Input.Keys.DOWN) {
                    recallQuickChat(1);
                    return true;
                }
            }
            if (uiLayer == UI_GAME_LOG) {
                boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                if (control && keycode == Input.Keys.A) {
                    List<String> lines = gameLogLines();
                    gameLogSelectionAnchor = lines.isEmpty() ? -1 : 0;
                    gameLogSelectionCaret = lines.size() - 1;
                    gameLogEditMenuOpen = false;
                    return true;
                }
                if (control && keycode == Input.Keys.C) {
                    copySelectedGameLogLines();
                    gameLogEditMenuOpen = false;
                    return true;
                }
                Rectangle content = gameLogContentBounds();
                float page = Math.max(GAME_LOG_LINE_HEIGHT,
                        content.height - GAME_LOG_LINE_HEIGHT);
                float maximum = gameLogMaximumScroll();
                switch (keycode) {
                    case Input.Keys.HOME -> gameLogScroll = maximum;
                    case Input.Keys.END -> gameLogScroll = 0;
                    case Input.Keys.PAGE_UP -> gameLogScroll = Math.min(maximum,
                            gameLogScroll + page);
                    case Input.Keys.PAGE_DOWN -> gameLogScroll = Math.max(0f,
                            gameLogScroll - page);
                    case Input.Keys.UP -> gameLogScroll = Math.min(maximum,
                            gameLogScroll + GAME_LOG_LINE_HEIGHT);
                    case Input.Keys.DOWN -> gameLogScroll = Math.max(0f,
                            gameLogScroll - GAME_LOG_LINE_HEIGHT);
                    default -> {
                        // Let the remaining table shortcuts continue below.
                    }
                }
                if (keycode == Input.Keys.HOME || keycode == Input.Keys.END
                        || keycode == Input.Keys.PAGE_UP
                        || keycode == Input.Keys.PAGE_DOWN
                        || keycode == Input.Keys.UP
                        || keycode == Input.Keys.DOWN) return true;
            }
            if (shortcutBindings.keyCodeMatches(
                    GdxShortcutBindings.VOICE_RECORD, keycode)
                    && canUseTableVoice()) {
                beginVoiceRecording();
                return true;
            }
            if (keycode == Input.Keys.BACKSPACE
                    && (voiceOpening || voiceLive || voiceStopping)) {
                cancelVoiceRecording();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyUp(int keycode) {
            textDeleteRepeat.release(keycode);
            if (shortcutBindings.keyCodeMatches(
                    GdxShortcutBindings.VOICE_RECORD, keycode)
                    && voiceRecorder != null) {
                finishVoiceRecording(false);
                return true;
            }
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) return true;
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (isClientTransportReconnecting()) return true;
            if (activeDialog != null && activeDialog.isAutoCall()) {
                handleAutoCallAmountTyped(character);
                return true;
            }
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) return true;
            boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (isQuickChatToggleCharacter(character, control)
                    && activeDialog == null && liveState != null) {
                if (uiLayer == UI_CHAT && !chatImageMode) {
                    closeTableChat();
                } else if (uiLayer == UI_NONE && canUseTableChat()) {
                    openQuickChat();
                }
                // Always consume the unmodified quick-chat key while a live
                // table exists. It must never be inserted in the composer or
                // leak through to a control below the popup.
                return true;
            }
            if (uiLayer != UI_CHAT || activeDialog != null) return false;
            if (character == '\b') return true;
            if (character >= 32 && character != 127) {
                chatEdit.focus("tableChat", chatDraft);
                chatDraft = chatEdit.replaceSelection(chatDraft,
                        Character.toString(character), 360);
                return true;
            }
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) return true;
            if (uiLayer == UI_SETTINGS && amountY != 0f) {
                if (settingsSection() == GdxSettingsContract.Section.DEBUG) {
                    pointer.set(Gdx.input.getX(), Gdx.input.getY());
                    viewport.unproject(pointer);
                    if (!settingsDebugViewportContains(pointer.x,
                            pointer.y)) return true;
                    float maximum = settingsDebugMaximumPixelScroll(
                            settingsDebugLines().size());
                    // This view is anchored at the newest line when the offset
                    // is zero, like the game log. Wheel-down moves towards it.
                    settingsDebugScroll = quickChatPixelScrollAfterWheel(
                            settingsDebugScroll, maximum, amountY);
                    return true;
                }
            }
            if (uiLayer == UI_CHAT && !chatImageMode && amountY != 0f) {
                pointer.set(Gdx.input.getX(), Gdx.input.getY());
                viewport.unproject(pointer);
                if (quickChatHistoryBounds().contains(pointer)) {
                    quickChatScroll = quickChatPixelScrollAfterWheel(
                            quickChatScroll, quickChatScrollMaximum, amountY);
                    return true;
                }
            }
            if (uiLayer != UI_GAME_LOG || amountY == 0f) return false;
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (!gameLogContentBounds().contains(pointer)) return true;
            gameLogScroll = quickChatPixelScrollAfterWheel(gameLogScroll,
                    gameLogMaximumScroll(), amountY);
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointerIndex,
                int button) {
            if (isClientTransportReconnecting()) return true;
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                // Dialog buttons are resolved once per frame by
                // handleDialogInput().  Consume the raw press here as well so
                // an InputMultiplexer can never deliver it underneath.
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) {
                if (button == Input.Buttons.LEFT) {
                    pointer.set(screenX, screenY);
                    viewport.unproject(pointer);
                    finalSummaryPointerTarget = finalSummaryPointerTargetAt(
                            viewport.getWorldWidth(),
                            viewport.getWorldHeight(), pointer.x, pointer.y,
                            finalSummaryPage, finalSummary.balances().size());
                    finalSummaryPointerCaptured
                            = finalSummaryPointerTarget >= 0;
                    finalSummaryPointerDownX = pointer.x;
                    finalSummaryPointerDownY = pointer.y;
                }
                return true;
            }
            if (uiLayer == UI_SETTINGS && button == Input.Buttons.LEFT) {
                pointer.set(screenX, screenY);
                viewport.unproject(pointer);
                if (settingsDebugTrackContains(pointer.x, pointer.y)) {
                    settingsDebugScrollDragging = true;
                    updateSettingsDebugScrollFromTrack(pointer.y);
                    return true;
                }
            }
            if (uiLayer == UI_CHAT && activeDialog == null
                    && button == Input.Buttons.LEFT && !chatEditMenuOpen) {
                pointer.set(screenX, screenY);
                viewport.unproject(pointer);
                if (!chatImageMode
                        && quickChatScrollTrackContains(pointer.x, pointer.y)) {
                    quickChatScrollDragging = true;
                    updateQuickChatScrollFromTrack(pointer.y);
                    return true;
                }
                Rectangle input = tableChatInputBounds();
                if (input.contains(pointer)) {
                    chatEdit.focus("tableChat", chatDraft);
                    placeTableChatCaret(pointer.x,
                            Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
                    chatPointerSelectionDragging = true;
                    return true;
                }
            }
            if (uiLayer != UI_GAME_LOG || button != Input.Buttons.LEFT
                    || gameLogEditMenuOpen) {
                if (button == Input.Buttons.LEFT && uiLayer == UI_NONE
                        && activeDialog == null && liveState != null) {
                    pointer.set(screenX, screenY);
                    viewport.unproject(pointer);
                    feltClickCandidate = emptyFeltGestureContains(
                            pointer.x, pointer.y);
                    feltClickDownX = pointer.x;
                    feltClickDownY = pointer.y;
                }
                return false;
            }
            pointer.set(screenX, screenY);
            viewport.unproject(pointer);
            if (gameLogTrackContains(pointer.x, pointer.y)) {
                gameLogScrollDragging = true;
                updateGameLogScrollFromTrack(pointer.y);
                return true;
            }
            int line = gameLogLineAt(pointer.x, pointer.y);
            if (line >= 0) {
                gameLogSelectionAnchor = line;
                gameLogSelectionCaret = line;
                gameLogSelectionDragging = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointerIndex) {
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) {
                if (finalSummaryPointerCaptured) {
                    pointer.set(screenX, screenY);
                    viewport.unproject(pointer);
                    if (Vector2.dst(finalSummaryPointerDownX,
                            finalSummaryPointerDownY, pointer.x, pointer.y)
                            > FELT_CLICK_DRIFT) {
                        finalSummaryPointerCaptured = false;
                        finalSummaryPointerTarget = -1;
                    }
                }
                return true;
            }
            pointer.set(screenX, screenY);
            viewport.unproject(pointer);
            if (feltClickCandidate
                    && Vector2.dst(feltClickDownX, feltClickDownY,
                            pointer.x, pointer.y) > FELT_CLICK_DRIFT) {
                feltClickCandidate = false;
            }
            if (chatPointerSelectionDragging) {
                placeTableChatCaret(pointer.x, true);
                return true;
            }
            if (quickChatScrollDragging) {
                updateQuickChatScrollFromTrack(pointer.y);
                return true;
            }
            if (gameLogScrollDragging) {
                updateGameLogScrollFromTrack(pointer.y);
                return true;
            }
            if (settingsDebugScrollDragging) {
                updateSettingsDebugScrollFromTrack(pointer.y);
                return true;
            }
            if (gameLogSelectionDragging) {
                int line = gameLogLineAt(pointer.x, pointer.y);
                if (line >= 0) gameLogSelectionCaret = line;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointerIndex,
                int button) {
            if (activeDialog != null && !activeDialog.isAutoAction()) {
                chatPointerSelectionDragging = false;
                quickChatScrollDragging = false;
                gameLogScrollDragging = false;
                settingsDebugScrollDragging = false;
                gameLogSelectionDragging = false;
                feltClickCandidate = false;
                return true;
            }
            if (finalSummary != null && uiLayer != UI_GAME_LOG) {
                feltClickCandidate = false;
                boolean activate = button == Input.Buttons.LEFT
                        && finalSummaryPointerCaptured;
                int pressedTarget = finalSummaryPointerTarget;
                finalSummaryPointerCaptured = false;
                finalSummaryPointerTarget = -1;
                if (activate) {
                    pointer.set(screenX, screenY);
                    viewport.unproject(pointer);
                    int releasedTarget = finalSummaryPointerTargetAt(
                            viewport.getWorldWidth(),
                            viewport.getWorldHeight(), pointer.x, pointer.y,
                            finalSummaryPage, finalSummary.balances().size());
                    if (pressedTarget == releasedTarget) {
                        handleFinalSummaryTarget(releasedTarget);
                    }
                }
                return true;
            }
            if (chatPointerSelectionDragging) {
                chatPointerSelectionDragging = false;
                return true;
            }
            if (quickChatScrollDragging) {
                quickChatScrollDragging = false;
                return true;
            }
            if (button == Input.Buttons.LEFT && feltClickCandidate) {
                feltClickCandidate = false;
                pointer.set(screenX, screenY);
                viewport.unproject(pointer);
                registerEmptyFeltClick(pointer.x, pointer.y,
                        System.nanoTime());
            }
            if (!gameLogScrollDragging && !settingsDebugScrollDragging
                    && !gameLogSelectionDragging) return false;
            gameLogScrollDragging = false;
            settingsDebugScrollDragging = false;
            gameLogSelectionDragging = false;
            return true;
        }
    };

    private boolean handleChatEditingKey(int keycode) {
        chatEdit.focus("tableChat", chatDraft);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (control) {
            switch (keycode) {
                case Input.Keys.A -> chatEdit.selectAll(chatDraft);
                case Input.Keys.C -> {
                    if (chatEdit.hasSelection(chatDraft)) {
                        Gdx.app.getClipboard().setContents(
                                chatEdit.selectedText(chatDraft));
                    }
                }
                case Input.Keys.X -> {
                    if (chatEdit.hasSelection(chatDraft)) {
                        Gdx.app.getClipboard().setContents(
                                chatEdit.selectedText(chatDraft));
                        chatDraft = chatEdit.replaceSelection(chatDraft, "", 360);
                    }
                }
                case Input.Keys.V -> {
                    String pasted = Gdx.app.getClipboard().getContents();
                    if (pasted != null && !pasted.isEmpty()) {
                        chatDraft = chatEdit.replaceSelection(chatDraft,
                                singleLine(pasted), 360);
                    }
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        switch (keycode) {
            case Input.Keys.LEFT -> chatEdit.left(chatDraft, shift);
            case Input.Keys.RIGHT -> chatEdit.right(chatDraft, shift);
            case Input.Keys.HOME -> chatEdit.home(chatDraft, shift);
            case Input.Keys.END -> chatEdit.end(chatDraft, shift);
            case Input.Keys.BACKSPACE ->
                chatDraft = chatEdit.backspace(chatDraft);
            case Input.Keys.FORWARD_DEL ->
                chatDraft = chatEdit.delete(chatDraft);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean isTextDeletionKey(int keycode) {
        return keycode == Input.Keys.BACKSPACE
                || keycode == Input.Keys.FORWARD_DEL;
    }

    private void updateTextDeleteRepeat(float delta) {
        int keycode = textDeleteRepeat.keycode();
        boolean editable = (activeDialog != null
                && activeDialog.isAutoCall()
                && activeDialog.autoCallAmountEditable())
                || (uiLayer == UI_CHAT && activeDialog == null);
        boolean pressed = editable && keycode >= 0
                && Gdx.input.isKeyPressed(keycode);
        int repeats = textDeleteRepeat.update(delta, pressed);
        for (int repeat = 0; repeat < repeats; repeat++) {
            if (activeDialog != null && activeDialog.isAutoCall()) {
                handleAutoCallAmountEditingKey(keycode);
            } else if (uiLayer == UI_CHAT && activeDialog == null) {
                handleChatEditingKey(keycode);
            }
        }
    }

    private boolean handleAutoCallAmountEditingKey(int keycode) {
        GdxTableDialog dialog = activeDialog;
        if (dialog == null || !dialog.autoCallAmountEditable()) return false;
        String value = dialog.amountText();
        dialogAmountEdit.focus("autoCallAmount", value);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (control) {
            switch (keycode) {
                case Input.Keys.A -> dialogAmountEdit.selectAll(value);
                case Input.Keys.C -> {
                    if (dialogAmountEdit.hasSelection(value)) {
                        Gdx.app.getClipboard().setContents(
                                dialogAmountEdit.selectedText(value));
                    }
                }
                case Input.Keys.X -> {
                    if (dialogAmountEdit.hasSelection(value)) {
                        Gdx.app.getClipboard().setContents(
                                dialogAmountEdit.selectedText(value));
                        replaceAutoCallAmountSelection(dialog, "");
                    }
                }
                case Input.Keys.V -> {
                    String pasted = Gdx.app.getClipboard().getContents();
                    if (pasted != null && !pasted.isEmpty()) {
                        replaceAutoCallAmountSelection(dialog,
                                singleLine(pasted).trim());
                    }
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        switch (keycode) {
            case Input.Keys.LEFT -> dialogAmountEdit.left(value, shift);
            case Input.Keys.RIGHT -> dialogAmountEdit.right(value, shift);
            case Input.Keys.HOME -> dialogAmountEdit.home(value, shift);
            case Input.Keys.END -> dialogAmountEdit.end(value, shift);
            case Input.Keys.BACKSPACE -> {
                String updated = dialogAmountEdit.backspace(value);
                dialog.autoCallAmountText(updated);
            }
            case Input.Keys.FORWARD_DEL -> {
                String updated = dialogAmountEdit.delete(value);
                dialog.autoCallAmountText(updated);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean handleAutoCallAmountTyped(char character) {
        GdxTableDialog dialog = activeDialog;
        if (dialog == null || !dialog.isAutoCall()) return false;
        if (!dialog.autoCallAmountEditable()) return true;
        String value = dialog.amountText();
        dialogAmountEdit.focus("autoCallAmount", value);
        if (character == '\b') return true;
        if ((character >= '0' && character <= '9') || character == '.'
                || character == ',' || character == '+' || character == '-') {
            replaceAutoCallAmountSelection(dialog,
                    Character.toString(character));
        }
        // The modal owns all typed input, including characters rejected by
        // Swing's numeric spinner formatter.
        return true;
    }

    private void replaceAutoCallAmountSelection(GdxTableDialog dialog,
            String replacement) {
        String value = dialog.amountText();
        int start = dialogAmountEdit.selectionStart(value);
        int end = dialogAmountEdit.selectionEnd(value);
        int remaining = Math.max(0, 32 - (value.length() - (end - start)));
        String accepted = replacement.length() <= remaining ? replacement
                : replacement.substring(0, remaining);
        String candidate = value.substring(0, start) + accepted
                + value.substring(end);
        if (!dialog.acceptsAutoCallAmountText(candidate)) return;
        dialogAmountEdit.replaceSelection(value, accepted, 32);
        dialog.autoCallAmountText(candidate);
    }

    private void focusAutoCallAmount(GdxTableDialog dialog,
            boolean selectAll) {
        dialogAmountEdit.blur();
        if (dialog == null || !dialog.autoCallAmountEditable()) return;
        dialogAmountEdit.focus("autoCallAmount", dialog.amountText());
        if (selectAll) dialogAmountEdit.selectAll(dialog.amountText());
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private String uppercase(String value) {
        return value == null ? "" : value.toUpperCase(
                Locale.forLanguageTag(gameText.language()));
    }

    private static String uppercase(String value, GdxGameText text) {
        return value == null ? "" : value.toUpperCase(
                Locale.forLanguageTag(text.language()));
    }

    private String settingsGameText(String suffix, Object... arguments) {
        return uppercase(gameText.translate("gdx.settings.game." + suffix,
                arguments));
    }

    CoronaPokerGdxTable(int detectedRefreshRate, Runnable onReady) {
        this(detectedRefreshRate, null, command -> { }, onReady,
                () -> { }, new GdxGameLogSink(), null, null, null, true);
    }

    CoronaPokerGdxTable(int detectedRefreshRate, Runnable onReady,
            PreferencesService preferences) {
        this(detectedRefreshRate, null, command -> { }, onReady,
                () -> { }, new GdxGameLogSink(), preferences, null, null, true);
    }

    CoronaPokerGdxTable(int detectedRefreshRate, Runnable onReady,
            Runnable onIntroLightsOn, PreferencesService preferences) {
        this(detectedRefreshRate, onReady, onIntroLightsOn, preferences, null);
    }

    CoronaPokerGdxTable(int detectedRefreshRate, Runnable onReady,
            Runnable onIntroLightsOn, PreferencesService preferences,
            GdxGamePresentationSettings presentationSettings) {
        this(detectedRefreshRate, null, command -> { }, onReady,
                onIntroLightsOn, new GdxGameLogSink(), preferences,
                null, presentationSettings, true);
    }

    CoronaPokerGdxTable(int detectedRefreshRate, GdxTableViewState liveState,
            TableCommandSink commands, Runnable onReady,
            GdxGameLogSink gameLog, PreferencesService preferences) {
        this(detectedRefreshRate, liveState, commands, onReady, () -> { },
                gameLog, preferences, null, null, false);
    }

    CoronaPokerGdxTable(int detectedRefreshRate, GdxTableViewState liveState,
            TableCommandSink commands, Runnable onReady,
            GdxGameLogSink gameLog, PreferencesService preferences,
            LobbySession lobby) {
        this(detectedRefreshRate, liveState, commands, onReady, () -> { },
                gameLog, preferences, lobby, null, false);
    }

    CoronaPokerGdxTable(int detectedRefreshRate, GdxTableViewState liveState,
            TableCommandSink commands, Runnable onReady,
            GdxGameLogSink gameLog, PreferencesService preferences,
            LobbySession lobby,
            GdxGamePresentationSettings presentationSettings) {
        this(detectedRefreshRate, liveState, commands, onReady, () -> { },
                gameLog, preferences, lobby, presentationSettings, false);
    }

    private CoronaPokerGdxTable(int detectedRefreshRate,
            GdxTableViewState liveState, TableCommandSink commands,
            Runnable onReady, Runnable onIntroLightsOn, GdxGameLogSink gameLog,
            PreferencesService preferences, LobbySession lobby,
            GdxGamePresentationSettings presentationSettings,
            boolean startupIntroOnly) {
        if (startupIntroOnly) {
            if (liveState != null) {
                throw new IllegalArgumentException(
                        "The startup intro cannot own live table state");
            }
        } else {
            Objects.requireNonNull(liveState,
                    "A product GDX table requires authoritative live state");
        }
        this.detectedRefreshRate = detectedRefreshRate;
        this.liveState = liveState;
        this.commands = Objects.requireNonNull(commands, "commands");
        this.onReady = Objects.requireNonNull(onReady, "onReady");
        this.onIntroLightsOn = Objects.requireNonNull(
                onIntroLightsOn, "onIntroLightsOn");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
        this.preferences = preferences;
        Properties audioProperties = preferences == null
                ? new Properties() : preferences.properties();
        shortcutBindings = new GdxShortcutBindings(audioProperties);
        audioControl = new GdxAudioControl(audioProperties, preferences);
        tableChat = lobby == null ? null : new GdxTableChatSession(lobby);
        textToSpeech = lobby == null ? null : new GdxTextToSpeechPlayback(
                () -> audioControl.enabled() ? effectsVolume : 0d,
                this::setChatTextToSpeechDucking);
        this.presentationSettings = presentationSettings;
        gameText = new GdxGameText(presentationSettings == null
                ? "es" : presentationSettings.language());
        if (presentationSettings != null) {
            liveDeck = presentationSettings.deck();
        }
        tableRebuyAllowed = lobby != null
                && lobby.snapshot().tableSettings() != null
                && lobby.snapshot().tableSettings().rebuy();
        tableHost = lobby != null && lobby.snapshot().host();
        tableBotDifficulty = lobby == null
                || lobby.snapshot().tableSettings() == null
                        ? NewGameTableDraft.BotDifficulty.MEDIUM
                        : lobby.snapshot().tableSettings().botDifficulty();
        this.startupIntroOnly = startupIntroOnly;
        // The only product scene without an authoritative table snapshot is
        // the startup intro; every table frame is state/event driven.
        intro = startupIntroOnly;
        if (preferences != null) {
            Properties persisted = preferences.properties();
            effectsVolume = GdxSettingsContract.masterVolume(persisted);
            musicVolume = 0.40f * effectsVolume;
            autoButtons = booleanPreference(persisted,
                    "auto_action_buttons", false) && !isTestMode();
            autoActionPersist = booleanPreference(persisted,
                    "auto_action_persist", true);
            autoModeConfirm = booleanPreference(persisted,
                    "modo_auto_confirm", true);
            autoCallEnabled = booleanPreference(persisted,
                    "auto_call_enabled", false);
            autoCallMax = doublePreference(persisted,
                    "auto_call_max", 0d);
            confirmActions = booleanPreference(persisted,
                    "confirmar_todo", false) && !isTestMode();
        }
        autoRebuy = presentationSettings != null
                && presentationSettings.autoRebuyOnBroke();
    }

    InputProcessor inputProcessor() {
        return tableInput;
    }

    static boolean booleanPreference(Properties properties, String key,
            boolean fallback) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(key, "key");
        return Boolean.parseBoolean(properties.getProperty(key,
                Boolean.toString(fallback)));
    }

    static double doublePreference(Properties properties, String key,
            double fallback) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(key, "key");
        try {
            double parsed = Double.parseDouble(properties.getProperty(key,
                    Double.toString(fallback)));
            return Double.isFinite(parsed) && parsed >= 0d ? parsed : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private boolean tablePreference(String key, boolean fallback) {
        return preferences == null ? fallback : booleanPreference(
                preferences.properties(), key, fallback);
    }

    private boolean globalTextToSpeechEnabled() {
        return liveState == null
                ? tablePreference("tts_server", true)
                : liveState.textToSpeechEnabled();
    }

    private boolean globalVoiceMessagesEnabled() {
        return liveState == null
                ? tablePreference("voice_messages", true)
                : liveState.voiceMessagesEnabled();
    }

    private boolean settingsOptionEnabled(
            GdxSettingsContract.ToggleOption option) {
        return GdxSettingsContract.enabled(option, tableSettingsProperties(),
                audioControl.enabled(), tableHost);
    }

    private boolean settingsOptionDisplayedValue(
            GdxSettingsContract.ToggleOption option) {
        return switch (option.key()) {
            case "tts_server" -> settingsTextToSpeechDraft;
            case "voice_messages" -> settingsVoiceMessagesDraft;
            default -> GdxSettingsContract.displayedValue(option,
                    tableSettingsProperties(), audioControl.enabled());
        };
    }

    private void toggleSettingsAudioOption(
            GdxSettingsContract.ToggleOption option) {
        switch (option.key()) {
            case "sonidos" -> toggleMasterSound();
            case "tts_server" -> settingsTextToSpeechDraft
                    = !settingsTextToSpeechDraft;
            case "voice_messages" -> settingsVoiceMessagesDraft
                    = !settingsVoiceMessagesDraft;
            default -> toggleTablePreference(option.key(), option.fallback());
        }
        if (GdxSettingsContract.isGlobalCommunicationOption(option)) {
            playSwitchSound(settingsOptionDisplayedValue(option));
        }
    }

    private float cardFlipSeconds() {
        Properties properties = tableSettingsProperties();
        try {
            int millis = MathUtils.clamp(Integer.parseInt(properties
                    .getProperty("card_flip_duration", "620")), 150, 1500);
            return millis / 1000f;
        } catch (NumberFormatException invalid) {
            return DEFAULT_CARD_FLIP_SECONDS;
        }
    }

    static float communityRevealStartSeconds(int offset,
            float leadInSeconds) {
        return Math.max(0f, leadInSeconds)
                + Math.max(0, offset) * COMMUNITY_FLIP_STAGGER_SECONDS;
    }

    static float communityRevealAnimationSeconds(int cardCount,
            float leadInSeconds, float flipSeconds) {
        int count = Math.max(0, cardCount);
        if (count == 0) {
            return Math.max(0f, leadInSeconds);
        }
        return communityRevealStartSeconds(count - 1, leadInSeconds)
                + Math.max(0f, flipSeconds) + CARD_FLIP_END_DELAY_SECONDS;
    }

    private float cardFlipScale(float progress) {
        int percent;
        try {
            percent = MathUtils.clamp(Integer.parseInt(
                    tableSettingsProperties().getProperty(
                            "card_flip_zoom", "100")), 100, 145);
        } catch (NumberFormatException invalid) {
            percent = 100;
        }
        return 1f + (percent / 100f - 1f)
                * MathUtils.sin(MathUtils.clamp(progress, 0f, 1f)
                        * MathUtils.PI);
    }

    private String tablePreferenceText(String key, String fallback) {
        return preferences == null ? fallback : preferences.properties()
                .getProperty(key, fallback);
    }

    private boolean finalCounterAnimationEnabled() {
        return tablePreference("animacion_contador_final", true);
    }

    private static boolean isTestMode() {
        return Boolean.getBoolean("coronapoker.testMode");
    }

    private void persistBooleanPreference(String key, boolean value) {
        if (preferences == null) {
            return;
        }
        preferences.properties().setProperty(key, Boolean.toString(value));
        if (uiLayer != UI_SETTINGS) preferences.saveDeferred();
    }

    private void toggleAutoButtons() {
        autoButtons = !autoButtons;
        if (!autoButtons) {
            queuedPreAction = queuedPreActionAfterAutoButtonsToggle(
                    queuedPreAction, false);
        }
        persistBooleanPreference("auto_action_buttons", autoButtons);
        playSwitchSound(autoButtons);
    }

    static int queuedPreActionAfterAutoButtonsToggle(int selection,
            boolean enabled) {
        return enabled ? selection : 0;
    }

    private void toggleQueuedPreAction(int selection) {
        int next = queuedPreAction == selection ? 0 : selection;
        if (next == queuedPreAction) return;
        queuedPreAction = next;
        playSwitchSound(next != 0);
    }

    private void toggleTablePreference(String key, boolean fallback) {
        boolean next = !tablePreference(key, fallback);
        persistBooleanPreference(key, next);
        if ("musica".equals(key) || "sonido_ascensor".equals(key)) {
            if (musicEnabled()) {
                backgroundMusic.play();
            } else {
                backgroundMusic.pause();
            }
        }
        if ("sonido_efectos".equals(key)) {
            refreshDealerLoopVolumes();
            if (!next) {
                stopShuffleSound();
                shuffleSound.stop();
                dealSound.stop();
                uncoverSound.stop();
                checkSound.stop();
                callSound.stop();
                betSound.stop();
                foldSound.stop();
                allInSound.stop();
                for (Sound cue : liveAudioCueSounds.values()) cue.stop();
            }
        }
        boolean displayed = key.equals("audio_block_voice_messages")
                || key.equals("audio_block_tts_local") ? !next : next;
        playSwitchSound(displayed);
    }

    private void beginTableSettings() {
        settingsSession.begin(GdxSettingsSession.Context.LIVE_TABLE,
                preferences == null ? null : preferences.properties());
        settingsOpenedWindowMode = GdxDisplayModeController.activeMode();
        settingsGamePage = 0;
        settingsAppearancePage = 0;
        settingsAudioPage = 0;
        settingsDebugScroll = 0;
        shortcutPage = 0;
        shortcutCaptureId = null;
        shortcutStatus = "";
        shortcutBindings.beginEdit();
        GdxAudioDevices.refreshCaptureDevicesAsync();
        settingsAutoRebuySnapshot = autoRebuy;
        settingsTextToSpeechDraft = globalTextToSpeechEnabled();
        settingsVoiceMessagesDraft = globalVoiceMessagesEnabled();
        settingsTextToSpeechSnapshot = settingsTextToSpeechDraft;
        settingsVoiceMessagesSnapshot = settingsVoiceMessagesDraft;
        liveSettingsOpened = liveState == null
                ? null : liveState.gameConfiguration();
        liveSettingsDraft = liveSettingsOpened;
        settingsBotDifficultySnapshot = tableBotDifficulty;
        liveBotDifficultyDraft = tableBotDifficulty;
    }

    private void closeTableSettings(boolean save) {
        shortcutCaptureId = null;
        shortcutStatus = "";
        if (save) {
            shortcutBindings.commitEdit();
        } else {
            shortcutBindings.cancelEdit();
        }
        if (!save && preferences != null) {
            Properties properties = preferences.properties();
            String previewOutput = properties.getProperty(
                    GdxAudioDevices.OUTPUT_KEY, "");
            settingsSession.restore(properties);
            if (settingsOpenedWindowMode != null
                    && GdxDisplayModeController.activeMode()
                    != settingsOpenedWindowMode) {
                GdxDisplayModeController.apply(settingsOpenedWindowMode);
            }
            String restoredOutput = properties.getProperty(
                    GdxAudioDevices.OUTPUT_KEY, "");
            // Switching an OpenAL output device interrupts every playing
            // stream. Restore it only when the user actually previewed a
            // different device while this transaction was open.
            if (GdxAudioDevices.outputSelectionChanged(previewOutput,
                    restoredOutput)) {
                GdxAudioDevices.applyConfiguredOutput(properties);
            }
            audioControl.setEnabled(tablePreference("sonidos", true), false);
            confirmActions = tablePreference("confirmar_todo", false)
                    && !isTestMode();
            autoButtons = tablePreference("auto_action_buttons", false)
                    && !isTestMode();
            autoActionPersist = tablePreference("auto_action_persist", true);
            autoModeConfirm = tablePreference("modo_auto_confirm", true);
            autoCallEnabled = tablePreference("auto_call_enabled", false);
            autoCallMax = doublePreference(properties, "auto_call_max", 0d);
            effectsVolume = GdxSettingsContract.masterVolume(properties);
            musicVolume = 0.40f * effectsVolume;
            if (presentationSettings != null) {
                liveDeck = availableDeck(presentationSettings.deck());
                replaceFeltTexture(presentationSettings.felt());
            }
            autoRebuy = settingsAutoRebuySnapshot;
            if (presentationSettings != null) {
                presentationSettings.setAutoRebuyOnBroke(autoRebuy);
            }
            if (!autoButtons) queuedPreAction = 0;
            if (musicEnabled()) {
                if (!backgroundMusic.isPlaying()) backgroundMusic.play();
            } else {
                backgroundMusic.pause();
            }
            refreshDealerLoopVolumes();
            if (textToSpeech != null) textToSpeech.refreshVolume();
        }
        if (save && tableHost && liveSettingsOpened != null
                && liveSettingsDraft != null && liveState != null
                && liveState.gameConfiguration() != null) {
            GameConfigCodecV1.Configuration current =
                    liveState.gameConfiguration();
            GameConfigCodecV1.Configuration merged =
                    GdxLiveSettingsMerge.merge(liveSettingsOpened,
                            liveSettingsDraft, current);
            if (!merged.equals(current)) {
                submit(new TableCommand.ApplyGameConfiguration(merged));
            }
            if (liveBotDifficultyDraft != null
                    && liveBotDifficultyDraft != tableBotDifficulty) {
                tableBotDifficulty = liveBotDifficultyDraft;
                submit(new TableCommand.SetBotDifficulty(tableBotDifficulty));
            }
        }
        if (save && tableHost) {
            persistBooleanPreference("tts_server",
                    settingsTextToSpeechDraft);
            persistBooleanPreference("voice_messages",
                    settingsVoiceMessagesDraft);
            if (liveState == null
                    || liveState.textToSpeechEnabled()
                    != settingsTextToSpeechDraft
                    || liveState.voiceMessagesEnabled()
                    != settingsVoiceMessagesDraft) {
                submit(new TableCommand.SetCommunicationRules(
                        settingsTextToSpeechDraft,
                        settingsVoiceMessagesDraft));
            }
        }
        if (save && preferences != null) preferences.saveDeferred();
        liveSettingsOpened = null;
        liveSettingsDraft = null;
        liveBotDifficultyDraft = null;
        settingsOpenedWindowMode = null;
        settingsSession.close();
        uiLayer = UI_NONE;
    }

    private boolean settingsHavePendingChanges() {
        if (shortcutBindings.hasPendingEdits()
                || autoRebuy != settingsAutoRebuySnapshot
                || settingsTextToSpeechDraft != settingsTextToSpeechSnapshot
                || settingsVoiceMessagesDraft != settingsVoiceMessagesSnapshot
                || !Objects.equals(liveSettingsDraft, liveSettingsOpened)
                || liveBotDifficultyDraft != settingsBotDifficultySnapshot) {
            return true;
        }
        return preferences != null && settingsSession.propertiesChanged(
                preferences.properties());
    }

    private void requestCancelTableSettings(Runnable afterClose) {
        if (!settingsHavePendingChanges()) {
            closeTableSettings(false);
            if (afterClose != null) afterClose.run();
            return;
        }
        if (settingsDiscardConfirmation != null
                && !settingsDiscardConfirmation.complete()) {
            // A stronger intent can arrive while the same transactional prompt
            // is already visible (notably ESC followed by Alt+F4). Preserve the
            // latest requested continuation instead of losing the window-close.
            if (afterClose != null) settingsDiscardAfterClose = afterClose;
            return;
        }
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                gameText.translate("gdx.settings.unsaved.title"),
                gameText.translate("gdx.settings.unsaved.message"),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.STOP,
                780, 0, false,
                gameText.translate("gdx.settings.unsaved.continue"),
                gameText.translate("gdx.settings.unsaved.discard"));
        settingsDiscardConfirmation = confirmation;
        settingsDiscardAfterClose = afterClose;
        confirmation.result().thenAccept(discard -> {
            if (settingsDiscardConfirmation == confirmation) {
                settingsDiscardConfirmation = null;
            }
            Runnable continuation = settingsDiscardAfterClose;
            settingsDiscardAfterClose = null;
            if (!discard || uiLayer != UI_SETTINGS) return;
            closeTableSettings(false);
            if (continuation != null) continuation.run();
        });
        showDialog(confirmation);
    }

    private void restoreTableAudioDefaults() {
        Properties properties = tableSettingsProperties();
        GdxSettingsContract.restoreAudioDefaults(properties, tableHost,
                GdxAudioDevices.hasCaptureDevices());
        if (tableHost) {
            settingsTextToSpeechDraft = true;
            settingsVoiceMessagesDraft = true;
        }
        GdxAudioDevices.applyConfiguredOutput(properties);
        audioControl.setEnabled(true, false);
        effectsVolume = 0.8f;
        musicVolume = 0.40f * effectsVolume;
        if (musicEnabled()) backgroundMusic.play();
        else backgroundMusic.pause();
        refreshDealerLoopVolumes();
        if (textToSpeech != null) textToSpeech.refreshVolume();
        playSwitchSound(true);
    }

    private void restoreTableAppearanceDefaults() {
        Properties properties = tableSettingsProperties();
        GdxSettingsContract.restoreAppearanceDefaults(properties, true);
        GdxDisplayModeController.apply(GdxWindowMode.configured(properties));
        if (presentationSettings != null) {
            liveDeck = availableDeck(presentationSettings.deck());
            replaceFeltTexture(presentationSettings.felt());
        }
        playSwitchSound(true);
    }

    private boolean settingsSectionHasRestoreDefaults() {
        return GdxSettingsContract.hasRestoreDefaults(settingsSection());
    }

    private void restoreCurrentSettingsSectionDefaults() {
        if (settingsSection() == GdxSettingsContract.Section.APPEARANCE) {
            restoreTableAppearanceDefaults();
        } else if (settingsSection() == GdxSettingsContract.Section.AUDIO) {
            restoreTableAudioDefaults();
        } else if (settingsSection()
                == GdxSettingsContract.Section.SHORTCUTS) {
            shortcutBindings.resetAllEdits();
            shortcutCaptureId = null;
            shortcutStatus = "restored";
        }
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(BASE_WIDTH, BASE_HEIGHT, 2560f, 1440f, camera);
        shapes = new ShapeRenderer();
        batch = new SpriteBatch(2000);
        roundedCardShader = new ShaderProgram(CARD_VERTEX_SHADER, CARD_FRAGMENT_SHADER);
        if (!roundedCardShader.isCompiled()) {
            throw new IllegalStateException("Rounded-card shader: " + roundedCardShader.getLog());
        }
        if (!startupIntroOnly) {
            avatarShader = new ShaderProgram(CARD_VERTEX_SHADER,
                    AVATAR_FRAGMENT_SHADER);
            if (!avatarShader.isCompiled()) {
                throw new IllegalStateException("Avatar shader: "
                        + avatarShader.getLog());
            }
            allInFireShader = new ShaderProgram(CARD_VERTEX_SHADER,
                    ALL_IN_FIRE_FRAGMENT_SHADER);
            if (!allInFireShader.isCompiled()) {
                System.err.println("ALL-IN fire shader disabled: "
                        + allInFireShader.getLog());
                allInFireShader.dispose();
                allInFireShader = null;
            }
            allInFireCanvas = createSolidTexture(Color.WHITE);
            backdropBlurShader = new ShaderProgram(BACKDROP_VERTEX_SHADER,
                    BACKDROP_BLUR_FRAGMENT_SHADER);
            if (!backdropBlurShader.isCompiled()) {
                throw new IllegalStateException("Backdrop blur shader: "
                        + backdropBlurShader.getLog());
            }
        }

        // The intro enlarges the official logo substantially. Use its exact
        // 2x source there so the dock animation never magnifies the 525 px
        // menu asset and exposes jagged/pixelated edges.
        logo = texture(startupIntroOnly
                ? "images/coronapoker_logo_big.png"
                : "images/corona_poker_splash.png");
        feltTexture = feltTexture("images/tapete_" + (presentationSettings == null
                ? "verde" : presentationSettings.felt()) + ".jpg");
        feltTexture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        if (startupIntroOnly) {
            defaultCardBack = cardTexture(
                    "images/decks/goliat/hq/trasera.jpg");
            introCardFaces = new Texture[INTRO_CARD_COUNT];
            for (int card = 0; card < INTRO_CARD_COUNT; card++) {
                introCardFaces[card] = liveCardFace(introCardCode(card));
            }
            initialiseStars();
            Gdx.input.setCursorCatched(false);
            return;
        }
        avatarDefault = texture("images/avatar_default.png");
        avatarBot = texture("images/avatar_bot.png");
        dealerChip = texture("images/dealer.png");
        smallBlindChip = texture("images/sb.png");
        bigBlindChip = texture("images/bb.png");
        logMoneyIcon = texture("images/chips.png");
        logStraddleIcon = texture("images/straddle.png");
        logDealerStraddleIcon = texture("images/dealer_straddle.png");
        soundIcon = texture("images/sound.png");
        muteIcon = texture("images/mute.png");
        blockedSoundIcon = texture("images/sound_b.png");
        lightsOnIcon = texture("images/lights_on.png");
        lightsOffIcon = texture("images/lights_off.png");
        pauseIcon = texture("images/pause.png");
        foldThumbIcon = texture("images/action/down.png");
        callThumbIcon = texture("images/action/up.png");
        timeoutIcon = texture("images/menu/timeout.png");
        talkIcon = texture("images/talk.png");
        finalMenuIcon = silhouetteTexture("images/exit2.png", Color.WHITE);
        finalLogIcon = texture("images/menu/log2.png");
        finalStatsIcon = texture("images/stats.png");
        finalContinueIcon = texture("images/continue.png");
        fastMenuIcon = texture("images/fast_panel/menu.png");
        fastButtonIcons = new Texture[]{
            texture("images/menu/gear.png"),
            texture("images/fast_panel/chat.png"),
            texture("images/fast_panel/mic.png"),
            texture("images/fast_panel/image.png"),
            texture("images/fast_panel/rebuy.png"),
            texture("images/fast_panel/log.png"),
            texture("images/fast_panel/fullscreen.png"),
            texture("images/stop.png"),
            silhouetteTexture("images/exit2.png", Color.WHITE)
        };
        defaultCardBack = cardTexture("images/decks/goliat/hq/trasera.jpg");
        flyingChips = new Texture[]{
            createChipTexture(new Color(0xd72d3bff), new Color(0x7f101bff)),
            createChipTexture(new Color(0x247ee8ff), new Color(0x10458fff)),
            createChipTexture(new Color(0x20a96bff), new Color(0x0d6840ff)),
            createChipTexture(new Color(0xe2a72fff), new Color(0x936312ff))
        };
        pot = texture("images/pot.png");
        FileHandle shuffleAudio = gameAudioResource("misc/shuffle.wav");
        shuffleSoundDurationSeconds = wavDurationSeconds(shuffleAudio,
                SHUFFLE_AUDIO_FALLBACK_SECONDS);
        shuffleSound = Gdx.audio.newSound(shuffleAudio);
        dealSound = gameSound("misc/deal.wav");
        uncoverSound = gameSound("misc/uncover.wav");
        checkSound = gameSound("misc/check.wav");
        callSound = gameSound("misc/call.wav");
        betSound = gameSound("misc/bet.wav");
        foldSound = gameSound("misc/fold.wav");
        allInSound = gameSound("misc/allin.wav");
        buttonOnSound = gameSound("misc/button_on.wav");
        buttonOffSound = gameSound("misc/button_off.wav");
        balanceCountSound = gameSound("misc/balance_count.wav");
        cardViewerSound = gameSound("misc/card_visor.wav");
        screenshotSound = gameSound("misc/screenshot.wav");
        feltChangeSound = gameSound("misc/mat.wav");

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        uiFont = font(generator, 31, 1.2f);
        smallFont = font(generator, 21, 0.8f);
        playerNameFont = font(generator, 44, 3.2f);
        playerNameFont.getData().setScale(0.5f);
        stackFont = font(generator, 24, 0f);
        // Action surfaces already provide their own contrast. A heavy glyph
        // outline makes black labels such as SUBE/VA look double-bold, so the
        // canonical Swing palette is kept with a clean, consistent face.
        actionFont = font(generator, 22, 0f);
        seatActionFont = font(generator, 32, 0f);
        callCostFont = font(generator, 160, 6f,
                new Color(0f, 0f, 0f, 0.80f),
                new Color(1f, 1f, 0f, 0.80f));
        pauseFont = font(generator, 76, 0f,
                PAUSE_RED, new Color(0x640000cc));
        // BalanceScreen uses a bold Dialog face. Generate the GDX equivalents
        // natively at display size: enlarging uiFont's 31 px atlas made the
        // final title visibly pixelated at 1080p and above.
        FreeTypeFontGenerator balanceGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Montserrat-Bold.ttf"));
        finalTitleFont = font(balanceGenerator, 66, 0f);
        finalHeroFont = font(balanceGenerator, 173, 5.5f,
                Color.WHITE, new Color(0x000000ef));
        finalAmountFont = font(balanceGenerator, 162, 5.5f,
                Color.WHITE, new Color(0x000000ef));
        finalCardBoldFont = font(balanceGenerator, 24, 0f);
        balanceGenerator.dispose();
        FreeTypeFontGenerator balanceDetailGenerator
                = new FreeTypeFontGenerator(
                        Gdx.files.internal("fonts/Inter-Medium.ttf"));
        finalDetailFont = font(balanceDetailGenerator, 38, 0f);
        finalCardFont = font(balanceDetailGenerator, 23, 0f);
        balanceDetailGenerator.dispose();
        // Navigation is part of the shared game chrome, not of the balance
        // report typography.  Keep it identical to the McLaren action buttons
        // used by the main menu and the rest of the GDX interface.
        finalButtonFont = font(generator, 26, 0.2f);
        generator.dispose();
        FreeTypeFontGenerator logGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Inter-Medium.ttf"));
        gameLogFont = font(logGenerator, 21, 0f);
        logGenerator.dispose();

        initialiseSeats();
        backgroundMusic = gameMusic("misc/background_music.mp3");
        backgroundMusic.setLooping(true);
        // Same ambient-music attenuation used by CoronaPoker's Audio subsystem.
        backgroundMusic.setVolume(musicVolume);
        if (!startupIntroOnly && musicEnabled()) {
            backgroundMusic.play();
        }
        Gdx.input.setCursorCatched(false);
    }

    private static Texture texture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        // TexturePaint in Swing preserves the tile's original grain. Linear
        // magnification smears the near-black high-frequency fabric until it
        // looks like a flat fill on HiDPI displays.
        texture.setFilter(TextureFilter.Linear, TextureFilter.Nearest);
        return texture;
    }

    private static Texture feltTexture(String path) {
        // Swing displays these exact bundled pixels. Keep GDX colour-neutral:
        // no gamma curves, darkening overlays or per-felt substitutions.
        // Nearest magnification preserves the black felt's fine grain instead
        // of averaging its near-black texels into an apparently flat fill.
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(feltMinificationFilter(),
                TextureFilter.Nearest);
        return texture;
    }

    static TextureFilter feltMinificationFilter() {
        // The bundled 450 px tiles are drawn almost pixel-for-pixel. Linear
        // minification averages the black tile's one-pixel fibres whenever the
        // viewport is slightly narrower than the logical canvas, making the
        // original fabric look like a flat black fill.
        return TextureFilter.Nearest;
    }

    private static Texture silhouetteTexture(String path, Color tint) {
        Pixmap source = new Pixmap(Gdx.files.internal(path));
        Pixmap tinted = new Pixmap(source.getWidth(), source.getHeight(),
                Pixmap.Format.RGBA8888);
        tinted.setColor(0f, 0f, 0f, 0f);
        tinted.fill();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = source.getPixel(x, y) & 0xff;
                if (alpha == 0) continue;
                tinted.setColor(tint.r, tint.g, tint.b,
                        tint.a * alpha / 255f);
                tinted.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(tinted, true);
        texture.setFilter(TextureFilter.MipMapLinearLinear,
                TextureFilter.Linear);
        source.dispose();
        tinted.dispose();
        return texture;
    }

    private static Texture cardTexture(String path) {
        return cardTexture(Gdx.files.internal(path));
    }

    static boolean cardMipMapsEnabled() {
        return true;
    }

    static TextureFilter cardMinificationFilter() {
        return TextureFilter.MipMapLinearNearest;
    }

    private static Texture cardTexture(FileHandle file) {
        // Cards are rotated and usually minified. Sampling only the HQ base
        // level keeps ranks sharp but aliases thin diagonal artwork; full
        // trilinear filtering blends adjacent levels and softens ranks/suits.
        // Select one mip level and filter within it: antialiased interior art
        // without the additional cross-level blur.
        Texture texture = new Texture(file, cardMipMapsEnabled());
        texture.setFilter(cardMinificationFilter(),
                TextureFilter.Linear);
        return texture;
    }

    private Sound gameSound(String resource) {
        FileHandle file = gameAudioResource(resource);
        if (file == null) {
            throw new IllegalStateException(
                    "Missing CoronaPoker sound " + resource);
        }
        return Gdx.audio.newSound(file);
    }

    private Music gameMusic(String resource) {
        FileHandle file = gameAudioResource(resource);
        if (file == null) {
            throw new IllegalStateException(
                    "Missing CoronaPoker music " + resource);
        }
        return Gdx.audio.newMusic(file);
    }

    private FileHandle gameAudioResource(String resource) {
        String normalized = resource == null ? ""
                : resource.replace('\\', '/');
        if (normalized.startsWith("sounds/")) {
            normalized = normalized.substring("sounds/".length());
        }
        if (normalized.isBlank() || normalized.startsWith("/")
                || normalized.contains("../")) return null;
        if (presentationSettings != null) {
            Path modSound = presentationSettings.modAsset(
                    "sounds/" + normalized).orElse(null);
            if (modSound != null) return Gdx.files.absolute(modSound.toString());
            Path modCinematic = presentationSettings.modAsset(
                    "cinematics/" + normalized).orElse(null);
            if (modCinematic != null) {
                return Gdx.files.absolute(modCinematic.toString());
            }
        }
        FileHandle bundledSound = Gdx.files.internal("sounds/" + normalized);
        if (bundledSound.exists()) return bundledSound;
        FileHandle bundledCinematic = Gdx.files.internal(
                "cinematics/" + normalized);
        return bundledCinematic.exists() ? bundledCinematic : null;
    }

    private static Texture createSolidTexture(Color color) {
        Pixmap pixels = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixels.setColor(color);
        pixels.fill();
        Texture texture = new Texture(pixels);
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pixels.dispose();
        return texture;
    }

    private static Texture createChipTexture(Color base, Color dark) {
        final int size = 256;
        final int center = size / 2;
        Pixmap pixels = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixels.setColor(0f, 0f, 0f, 0f);
        pixels.fill();

        pixels.setColor(0f, 0f, 0f, 0.32f);
        pixels.fillCircle(center + 5, center - 7, 118);
        pixels.setColor(dark);
        pixels.fillCircle(center, center, 116);
        pixels.setColor(base);
        pixels.fillCircle(center, center, 108);

        pixels.setColor(Color.WHITE);
        for (int i = 0; i < 8; i++) {
            float middle = MathUtils.PI2 * i / 8f;
            float half = 0.115f;
            int ox1 = center + Math.round(MathUtils.cos(middle - half) * 108f);
            int oy1 = center + Math.round(MathUtils.sin(middle - half) * 108f);
            int ox2 = center + Math.round(MathUtils.cos(middle + half) * 108f);
            int oy2 = center + Math.round(MathUtils.sin(middle + half) * 108f);
            int ix1 = center + Math.round(MathUtils.cos(middle - half) * 83f);
            int iy1 = center + Math.round(MathUtils.sin(middle - half) * 83f);
            int ix2 = center + Math.round(MathUtils.cos(middle + half) * 83f);
            int iy2 = center + Math.round(MathUtils.sin(middle + half) * 83f);
            pixels.fillTriangle(ox1, oy1, ox2, oy2, ix1, iy1);
            pixels.fillTriangle(ox2, oy2, ix2, iy2, ix1, iy1);
        }

        pixels.setColor(dark);
        pixels.fillCircle(center, center, 80);
        pixels.setColor(base);
        pixels.fillCircle(center, center, 70);
        pixels.setColor(1f, 1f, 1f, 0.72f);
        for (int radius = 56; radius <= 59; radius++) {
            pixels.drawCircle(center, center, radius);
        }

        Texture texture = new Texture(pixels, true);
        texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        pixels.dispose();
        return texture;
    }

    private static BitmapFont font(FreeTypeFontGenerator generator, int size, float border) {
        return font(generator, size, border, Color.WHITE,
                new Color(0x02050ccc));
    }

    private static BitmapFont font(FreeTypeFontGenerator generator, int size,
            float border, Color color, Color borderColor) {
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = size;
        parameter.color = color;
        parameter.borderColor = borderColor;
        parameter.borderWidth = border;
        // Text is already generated at its intended UI size. Font mipmaps made
        // thin strokes choose a softer lower-resolution level under Windows'
        // fractional DPI scaling (notably 125%). Keep the demo/frontend's
        // direct linear sampling and ask FreeType to align stems to the pixel
        // grid. Card textures deliberately retain their separate HQ+mipmap
        // pipeline because they undergo large animated scale changes.
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        parameter.kerning = true;
        parameter.genMipMaps = false;
        parameter.minFilter = TextureFilter.Linear;
        parameter.magFilter = TextureFilter.Linear;
        return generator.generateFont(parameter);
    }

    private void initialiseStars() {
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star(
                    MathUtils.random(0f, BASE_WIDTH),
                    MathUtils.random(0f, BASE_HEIGHT),
                    MathUtils.random(8f, 34f),
                    MathUtils.random(0.8f, 3.2f),
                    MathUtils.random(0f, MathUtils.PI2));
        }
    }

    private void initialiseSeats() {
        Objects.requireNonNull(liveState,
                "Seat initialization requires authoritative live state");
        syncSeatsFromLiveState();
    }

    private void syncSeatsFromLiveState() {
        List<TableSnapshot.PlayerSnapshot> ordered = new ArrayList<>(
                visibleSeatPlayers(liveState.snapshot()));
        String localNickname = liveState.snapshot().localNickname();
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).nickname().equals(localNickname)) {
                TableSnapshot.PlayerSnapshot local = ordered.remove(index);
                ordered.add(0, local);
                break;
            }
        }
        for (int index = 0; index < seats.length; index++) {
            if (index < ordered.size()) {
                TableSnapshot.PlayerSnapshot player = ordered.get(index);
                seats[index] = new Seat(player.nickname(), index);
                seats[index].displayedStackAmount = player.stack();
                seats[index].stackText = formatAmount(player.stack());
                seats[index].displayedInvestedAmount = player.streetBet();
                seats[index].investedText = formatAmount(player.streetBet());
            } else {
                seats[index] = new Seat("", index);
            }
        }
    }

    /**
     * Swing's downgraded TablePanel omits players marked as exited while the
     * canonical model keeps them for settlement and audit.  GDX mirrors that
     * display-only rule and retains an exited local seat only until its own
     * ordered CloseTable arrives, avoiding a transient remote player in the
     * local HUD position during shutdown.
     */
    static List<TableSnapshot.PlayerSnapshot> visibleSeatPlayers(
            TableSnapshot snapshot) {
        return snapshot.players().stream()
                .filter(player -> !player.exited()
                        || player.nickname().equals(snapshot.localNickname()))
                .toList();
    }

    private TableSnapshot.PlayerSnapshot livePlayer(Seat seat) {
        if (liveState == null || seat == null || seat.name.isBlank()) {
            return null;
        }
        for (TableSnapshot.PlayerSnapshot player
                : liveState.snapshot().players()) {
            if (player.nickname().equals(seat.name)) {
                return player;
            }
        }
        return null;
    }

    private static String formatAmount(double amount) {
        return java.math.BigDecimal.valueOf(amount)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    static String callLabel(ActionControlState controls, GdxGameText text) {
        return switch (controls.callAction()) {
            case CHECK -> uppercase(text.translate("action.pasar"), text);
            case CALL -> uppercase(text.translate("ui.ir"), text) + " (+"
                    + formatAmount(controls.callAmount()) + ")";
            // Swing leaves a readable caption while the control is disabled.
            // An empty caption produced the apparently broken white button
            // during short state transitions and between automatic actions.
            case DISABLED -> controls.callAmount() > 0d
                    ? uppercase(text.translate("ui.ir"), text) + " (+"
                            + formatAmount(controls.callAmount()) + ")"
                    : uppercase(text.translate("action.pasar"), text);
        };
    }

    static String raiseLabel(ActionControlState controls, GdxGameText text) {
        return switch (controls.raiseAction()) {
            case BET -> uppercase(text.translate("action.apostar"), text);
            case RAISE -> uppercase(text.translate("action.subir"), text);
            case RERAISE -> uppercase(text.translate("action.resubir"), text);
            case DISABLED -> "";
        };
    }

    void acceptEvent(TableVisualEvent event, CompletableFuture<Void> barrier) {
        if (liveState == null) {
            throw new IllegalStateException(
                    "The startup intro cannot consume table events");
        }
        Objects.requireNonNull(barrier, "barrier");
        if (event instanceof TableVisualEvent.CloseTable close) {
            terminationRequested = false;
            recoverableTerminationRequested = false;
            terminationConfirmation = null;
            liveState.apply(event);
            if (recoveryStopSkipsFinalSummary(close.summary().reason())) {
                // Swing never opens BalanceScreen for force_recover. The host
                // immediately rebuilds its recovery lobby; remote clients keep
                // the stopped table visible under a five-second notice, then
                // reconnect automatically.
                while (activeDialog != null) {
                    activeDialog.dismiss();
                    activeDialog = dialogQueue.pollFirst();
                }
                dialogQueue.clear();
                uiLayer = UI_NONE;
                finalContinueRequested = true;
                if (tableHost) {
                    barrier.complete(null);
                } else {
                    recoveryStopBarrier = barrier;
                    recoveryStopUntil = totalTime
                            + RECOVERY_STOP_NOTICE_SECONDS;
                }
                return;
            }
            if (!close.summary().hasBalances()) {
                barrier.complete(null);
                return;
            }
            if (finalSummary != null) {
                throw new IllegalStateException(
                        "A GDX final table summary is already active");
            }
            while (activeDialog != null) {
                activeDialog.dismiss();
                activeDialog = dialogQueue.pollFirst();
            }
            dialogQueue.clear();
            uiLayer = UI_NONE;
            finalSummary = close.summary();
            finalSummaryBarrier = barrier;
            finalSummaryOpenedAt = totalTime;
            finalSummaryPage = 0;
            finalSummaryScreenshotTaken = false;
            finalExitPending = false;
            TableSessionSummary.PlayerBalance localBalance
                    = finalSummary.localBalance();
            boolean hasAnimatedResult = localBalance != null
                    && Double.compare(localBalance.netResult(), 0d) != 0
                    && finalSummary.reason()
                            != TableSessionSummary.CloseReason.RECOVERABLE_STOP
                    && finalSummary.reason()
                            != TableSessionSummary.CloseReason.FAILURE;
            if (hasAnimatedResult && finalCounterAnimationEnabled()
                    && tablePreference("sonido_conteo", true)) {
                play(balanceCountSound, 0.74f, 1f);
            }
        } else if (event instanceof TableVisualEvent.LateJoinRequest request) {
            liveState.apply(event);
            screenshotToast = "[" + request.nickname() + "] "
                    + gameText.translate("game.quiere_entrar_en_la_timba");
            screenshotToastUntil = totalTime + 4f;
            playPreferenceSound("misc/new_user.wav",
                    "sonido_entrar_sala", 0.82f);
            barrier.complete(null);
        } else if (event instanceof TableVisualEvent.AudioCue cue) {
            liveState.apply(event);
            acceptAudioCue(cue, barrier);
        } else if (event instanceof TableVisualEvent.SpecialCardSound special) {
            liveState.apply(event);
            playSpecialCardSound(special.cardCode());
            barrier.complete(null);
        } else if (event instanceof TableVisualEvent.PreparationStatus preparation) {
            liveState.apply(event);
            preparationPhase = preparation.phase();
            barrier.complete(null);
        } else if (event instanceof TableVisualEvent.GameClock) {
            liveState.apply(event);
            barrier.complete(null);
        } else if (event instanceof TableVisualEvent.PauseStatus
                || event instanceof TableVisualEvent.TelemetryStatus
                || event instanceof TableVisualEvent.SeatRoster) {
            boolean wasPaused = liveState.snapshot().paused();
            liveState.apply(event);
            if (GdxSoundFeedback.pauseStarted(wasPaused,
                    liveState.snapshot().paused())) {
                playPreferenceSound(GdxSoundFeedback.PAUSE,
                        "sonido_pausa", 0.82f);
            }
            syncSeatsFromLiveState();
            barrier.complete(null);
        } else if (event instanceof TableVisualEvent.PositionRotation rotation) {
            if (livePositionRotation != null) {
                throw new IllegalStateException("A GDX position rotation is already active");
            }
            // Presentation-only events still own an ordered sequence number.
            // Consume it on acceptance so auxiliary clock/telemetry events
            // cannot overtake the animation and make its completion stale.
            liveState.apply(event);
            livePositionRotation = new LivePositionRotation(rotation,
                    System.nanoTime(), barrier);
        } else if (event instanceof TableVisualEvent.CollectBets collect) {
            if (liveChipBatch != null || pendingCollectBets != null) {
                throw new IllegalStateException("A GDX chip batch is already active");
            }
            TableSnapshot presentationSnapshot = liveState.snapshot();
            if (liveActionChips.isEmpty()) {
                liveChipBatch = new LiveChipBatch(collect, System.nanoTime(),
                        barrier, presentationSnapshot, livePotContributions);
            } else {
                // Swing lets action chips fly without holding the next turn,
                // but never duplicates them when the street is collected.
                pendingCollectBets = new PendingCollectBets(collect, barrier,
                        presentationSnapshot);
            }
            // The flight owns its captured pre-collection presentation. Move
            // canonical balances forward now; only the visual barrier waits.
            liveState.apply(event);
            syncSeatsFromLiveState();
        } else if (event instanceof TableVisualEvent.PlayerAction action
                && action.contributionDelta() > 0d) {
            if (!liveBetAnimationEnabled()) {
                liveState.apply(event);
                syncSeatsFromLiveState();
                playPlayerActionSoundForAcceptedEvent(action, 0);
                barrier.complete(null);
                return;
            }
            liveActionChips.add(new LiveActionChip(action,
                    System.nanoTime(), liveCounterAnimationEnabled()));
            // The canonical sequence fixes both the action label and the exact
            // post-action balances. Counter interpolation remains cosmetic.
            liveState.apply(event);
            syncSeatsFromLiveState();
            if (action.kind()
                    == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
                // The gunshot belongs to the accepted ALL-IN action. The chip
                // impact remains tied to physical contact with the pot below.
                playAllInActionSoundOnce(action.nickname());
            }
            // Exactly like Swing's launchChipToPot, the flight is cosmetic and
            // must not hold the dealer or the following player's turn.
            barrier.complete(null);
        } else if (event instanceof TableVisualEvent.Payout payout) {
            if (livePayout != null) {
                throw new IllegalStateException("A GDX payout is already active");
            }
            if (!liveBetAnimationEnabled()
                    || presentationSettings != null
                    && presentationSettings.testMode()) {
                liveState.apply(event);
                liveWinnerStarts.putIfAbsent(payout.nickname(),
                        System.nanoTime());
                syncSeatsFromLiveState();
                barrier.complete(null);
                return;
            }
            liveState.apply(event);
            livePayout = new LivePayout(payout, System.nanoTime(), barrier);
            syncSeatsFromLiveState();
        } else if (event instanceof TableVisualEvent.Rebuy rebuy) {
            if (liveRebuy != null) {
                throw new IllegalStateException("A GDX rebuy is already active");
            }
            String cashSoundResource = liveCashRegisterSoundResource();
            playResourceSound(cashSoundResource, 0.72f, 1f);
            if (!liveCounterAnimationEnabled()) {
                liveState.apply(event);
                syncSeatsFromLiveState();
                barrier.complete(null);
                return;
            }
            liveState.apply(event);
            liveRebuy = new LiveRebuy(rebuy, System.nanoTime(), barrier,
                    cashSoundResource);
            syncSeatsFromLiveState();
        } else if (event instanceof TableVisualEvent.InitialStackFill fill) {
            if (liveInitialStackFill != null) {
                throw new IllegalStateException(
                        "A GDX initial stack fill is already active");
            }
            liveState.apply(event);
            syncSeatsFromLiveState();
            playResourceSound(fill.soundResource(), 0.72f, 1f);
            liveInitialStackFill = new LiveInitialStackFill(fill,
                    System.nanoTime(), barrier);
        } else if (event instanceof TableVisualEvent.Cinematic cinematic
                && cinematic.type() == TableVisualEvent.Cinematic.Type.ALL_IN
                && cinematic.phase() == TableVisualEvent.Cinematic.Phase.START) {
            if (liveCinematic != null) {
                throw new IllegalStateException("A GDX cinematic is already active");
            }
            String asset = cinematic.assetName();
            StreamingGifTextureAnimation animation = allInAnimation(asset);
            // START belongs to the ordered state stream and must be consumed
            // as soon as the animation begins. Only its completion barrier is
            // delayed until the final GIF frame. Applying START at the end let
            // ordinary action/timer events overtake it and produced sequences
            // such as 190 after 195, releasing the dealer through the fallback
            // instead of the real cinematic barrier.
            liveState.apply(event);
            liveCinematic = new LiveCinematic(cinematic, animation,
                    System.nanoTime(), barrier);
            // The cinematic owns only its optional companion soundtrack. The
            // canonical ALL-IN gunshot belongs to PlayerAction, at the exact
            // instant the action chip leaves the seat (Swing's contract).
            // Starting it here could make it precede the chip flight when the
            // asynchronous cinematic START wins the event race.
            Sound companion = liveAllInSoundEnabled()
                    ? allInCompanionSound(asset) : null;
            if (companion != null) {
                play(companion, 0.82f, 1f);
            }
        } else if (event instanceof TableVisualEvent.Shuffle shuffle) {
            acceptLiveShuffle(shuffle, barrier);
        } else if (event instanceof TableVisualEvent.DealHoleCard deal) {
            int dealOrder = liveHoleDealCount++;
            int activePlayers = activeDealtPlayerCount();
            boolean animated = liveDealAnimationEnabled();
            float flightSeconds = animated
                    ? liveDealFlightSeconds(activePlayers,
                            liveDealSpeedPercent()) : 0f;
            float barrierDelay = animated ? flightSeconds
                    : liveDealCadenceSeconds(activePlayers,
                            liveDealSpeedPercent());
            long startedAtNanos = System.nanoTime();
            LiveCardFlight flight = new LiveCardFlight(event, startedAtNanos,
                    barrier, barrierDelay, dealOrder,
                    flightSeconds,
                    animated && liveFlipAnimationEnabled()
                            ? cardFlipSeconds() : 0f,
                    animated);
            // Commit the ordered projection when the renderer accepts the
            // event.  The flight remains the sole visible representation until
            // it finishes, so this does not make the card pop into place.
            // Deferring the projection until landing allowed an asynchronous
            // telemetry/clock event with a higher sequence to overtake it; the
            // later landing was then rejected as stale and the card vanished.
            liveState.apply(event);
            flight.stateApplied = true;
            syncSeatsFromLiveState();
            liveCardFlights.add(flight);
            if (animated && liveDealSoundEnabled()) {
                play(dealSound, 0.30f,
                        0.96f + (dealOrder % 3) * 0.025f);
            }
        } else if (event instanceof TableVisualEvent.DealCommunityCard deal) {
            int activePlayers = activeDealtPlayerCount();
            boolean animated = liveDealAnimationEnabled();
            float flightSeconds = animated
                    ? liveDealFlightSeconds(activePlayers,
                            liveDealSpeedPercent()) : 0f;
            float barrierDelay = animated ? flightSeconds
                    : liveDealCadenceSeconds(activePlayers,
                            liveDealSpeedPercent());
            LiveCardFlight flight = new LiveCardFlight(event,
                    System.nanoTime(),
                    barrier, barrierDelay, deal.slot(),
                    flightSeconds, 0f, animated);
            // As with hole cards, state order and visual timing are separate:
            // the back is drawn by this flight while the canonical slot is
            // already protected from unrelated higher-sequence events.
            liveState.apply(event);
            flight.stateApplied = true;
            liveCardFlights.add(flight);
            if (animated && liveDealSoundEnabled()) {
                play(dealSound, 0.34f,
                        0.94f + deal.slot() * 0.018f);
            }
        } else if (event instanceof TableVisualEvent.RevealCommunityCards reveal) {
            if (liveCommunityReveal != null) {
                throw new IllegalStateException(
                        "A GDX community-card reveal is already active");
            }
            LiveCommunityReveal animation = new LiveCommunityReveal(reveal,
                    System.nanoTime(), barrier, cardFlipSeconds(),
                    liveFlipAnimationEnabled());
            liveState.apply(event);
            liveCommunityReveal = animation;
        } else if (event instanceof TableVisualEvent.RevealHoleCards reveal) {
            if (liveHoleReveal != null) {
                throw new IllegalStateException(
                        "A GDX hole-card reveal is already active");
            }
            TableSnapshot.PlayerSnapshot player = liveState.snapshot().players()
                    .stream()
                    .filter(candidate -> candidate.nickname().equals(reveal.nickname()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot reveal cards for missing player "
                            + reveal.nickname()));
            if (!shouldAnimateHoleReveal(
                    liveState.snapshot().localNickname(), player, reveal)) {
                // The local player's cards are normally face-up from the deal.
                // Showdown still carries the authoritative hand/result update,
                // but replaying a flip here would hide and reveal the same two
                // cards for no game-state reason.
                try {
                    liveState.apply(reveal);
                    syncSeatsFromLiveState();
                    barrier.complete(null);
                } catch (Throwable error) {
                    barrier.completeExceptionally(error);
                }
            } else if (!liveFlipAnimationEnabled()) {
                // Swing's non-animated showdown reveals both cards together
                // with one uncover sound.  The blind-straddle reveal is the
                // exception: its classic flat fallback is intentionally silent.
                try {
                    liveState.apply(reveal);
                    syncSeatsFromLiveState();
                    if (!reveal.handName().isBlank()
                            && liveFlipSoundEnabled()) {
                        play(uncoverSound, 0.54f, 1f);
                    }
                    barrier.complete(null);
                } catch (Throwable error) {
                    barrier.completeExceptionally(error);
                }
            } else {
                LiveHoleReveal animation = new LiveHoleReveal(reveal,
                        System.nanoTime(),
                        barrier, player.holeCards(), cardFlipSeconds());
                liveState.apply(event);
                syncSeatsFromLiveState();
                liveHoleReveal = animation;
            }
        } else if (event instanceof TableVisualEvent.FoldHoleCards fold) {
            if (liveHoleFold != null) {
                throw new IllegalStateException(
                        "A GDX hole-card fold is already active");
            }
            // Swing disables both cards as soon as FOLD is accepted.  Consume
            // the ordered state immediately and let the barrier cover only the
            // short visual transition; otherwise the cards remain fully lit
            // for the entire animation and auxiliary snapshots can revive them.
            liveState.apply(event);
            syncSeatsFromLiveState();
            liveHoleFold = new LiveHoleFold(fold, System.nanoTime(), barrier);
            if (liveFoldSoundEnabled()) {
                play(foldSound, 0.58f, 1f);
            }
        } else if (event instanceof TableVisualEvent.SwapHoleCards swap) {
            if (liveHoleSwap != null) {
                throw new IllegalStateException("A GDX hole-card swap is already active");
            }
            // A non-blocking local sort can be published as soon as the
            // second local deal barrier releases, while the card is still
            // finishing its flip. Install every preceding deal state in
            // sequence order before consuming the swap. The active flights
            // continue drawing those cards, so this does not make them pop
            // into place; it only prevents the newer swap sequence from
            // invalidating the still-pending DealHoleCard state.
            applyPendingCardFlightStatesBefore(swap.sequence());
            if (!liveSwapAnimationEnabled()) {
                try {
                    liveState.apply(event);
                    syncSeatsFromLiveState();
                    barrier.complete(null);
                } catch (Throwable error) {
                    barrier.completeExceptionally(error);
                }
                return;
            }
            TableSnapshot.PlayerSnapshot player = liveState.snapshot().players()
                    .stream()
                    .filter(candidate -> candidate.nickname().equals(swap.nickname()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot animate cards for missing player "
                            + swap.nickname()));
            boolean waitForCompletion = swap.blocking();
            float visualDelay = waitForCompletion ? 0f
                    : Math.max(LOCAL_SWAP_DELAY,
                            localHoleVisualRemaining(swap.nickname()) + 0.02f);
            liveHoleSwap = new LiveHoleSwap(swap, System.nanoTime(),
                    player.holeCards(), barrier, waitForCompletion,
                    visualDelay, liveSwapDurationSeconds(),
                    liveSwapAnimationArc());
            liveState.apply(event);
            syncSeatsFromLiveState();
            if (!waitForCompletion) {
                barrier.complete(null);
            }
        } else {
            if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase() != TableVisualEvent.HandBoundary.Phase.END) {
                livePotContributions.clear();
                liveWinnerStarts.clear();
                liveHoleDealCount = 0;
                liveShowdownHoverNickname = null;
                allInActionSoundsPlayed.clear();
                liveHandProbabilities.clear();
            }
            if (event instanceof TableVisualEvent.RunItTwiceBoard board
                    && board.side()
                    == TableVisualEvent.RunItTwiceBoard.Side.B) {
                liveWinnerStarts.clear();
                liveShowdownHoverNickname = null;
                liveHandProbabilities.clear();
            }
            boolean wasLastHand = liveState.lastHand();
            liveState.apply(event);
            if (event instanceof TableVisualEvent.LastHandStatus) {
                String cue = GdxSoundFeedback.lastHandTransition(wasLastHand,
                        liveState.lastHand());
                if (!cue.isBlank()) {
                    playPreferenceSound(cue, "sonido_ultima_mano", 0.82f);
                }
            }
            if (event instanceof TableVisualEvent.HandResult result
                    && result.winner()) {
                liveWinnerStarts.putIfAbsent(result.nickname(), System.nanoTime());
            }
            if (event instanceof TableVisualEvent.PartialHand partial) {
                updateLiveHandProbability(partial);
            } else if (event instanceof TableVisualEvent.HandResult result) {
                liveHandProbabilities.remove(result.nickname());
            }
            if (event instanceof TableVisualEvent.PlayerAction action
                    && action.kind() == TableVisualEvent.PlayerAction.ActionKind.CHECK) {
                if (liveCheckSoundEnabled()) {
                    play(checkSound, 0.58f, 1f);
                }
            }
            if (event instanceof TableVisualEvent.ActionControls controls) {
                armedHudTarget = 0;
                liveBetAmount = controls.state().raiseAmount();
                autoActionControlsReady = true;
                autoActionEligibleAt = totalTime + 0.08f;
            }
            if (event instanceof TableVisualEvent.TurnTimer timer
                    && timer.phase() == TableVisualEvent.TurnTimer.Phase.STOP) {
                liveBetAmount = liveState.actionControls().raiseAmount();
                // Swing's LocalPlayer.finTurno() always stops the hurry-up dog.
                // GDX receives the same causal end-of-turn marker, so bind the
                // presentation-only stop here and never let it leak into the
                // following player or showdown.
                stopAudioCue("misc/hurryup.wav");
            }
            if (event instanceof TableVisualEvent.TurnTimer timer
                    && GdxSoundFeedback.localTurnStarted(timer,
                            liveState.snapshot().localNickname())) {
                playPreferenceSound(GdxSoundFeedback.YOUR_TURN,
                        "sonido_tu_turno", 0.82f);
            }
            if (event instanceof TableVisualEvent.PreActionControls controls) {
                queuedPreAction = queuedPreActionAfterControlsEvent(
                        queuedPreAction, controls.active(),
                        controls.clearSelection());
                autoActionControlsReady = autoActionControlsReadyAfterPreAction(
                        autoActionControlsReady, controls.active());
            }
            if (event instanceof TableVisualEvent.DeckChanged changed) {
                String selectedDeck = availableDeck(changed.deck());
                if (!selectedDeck.equals(liveDeck)) {
                    liveDeck = selectedDeck;
                    // Swing's cambiarBaraja() gives immediate audible feedback
                    // with uncover.wav after committing the selected deck.
                    if (tablePreference("sonido_efectos", true)
                            && tablePreference("sonido_destape", true)) {
                        play(uncoverSound, 0.92f, 1f);
                    }
                }
            }
            if (activeDialog != null && activeDialog.isAutoAction()
                    && (isClientTransportReconnecting()
                    || shouldDismissAutoActionDialog(event,
                            liveState.snapshot()))) {
                // The queued decision became stale (manual action, timeout,
                // disconnect or street advance). Do not leave its veto floating
                // over another player's turn and never fire it later.
                activeDialog.dismiss();
            }
            syncSeatsFromLiveState();
            barrier.complete(null);
        }
    }

    static boolean holeCardsAlreadyRevealed(
            TableSnapshot.PlayerSnapshot player,
            TableVisualEvent.RevealHoleCards reveal) {
        List<TableSnapshot.CardSnapshot> cards = player.holeCards();
        return cards.size() >= 2
                && cards.get(0).visible() && cards.get(0).faceUp()
                && cards.get(1).visible() && cards.get(1).faceUp()
                && cards.get(0).code().equals(reveal.left().code())
                && cards.get(1).code().equals(reveal.right().code());
    }

    static boolean shouldAnimateHoleReveal(String localNickname,
            TableSnapshot.PlayerSnapshot player,
            TableVisualEvent.RevealHoleCards reveal) {
        // The local hand is already face-up from its initial deal. Its sorted
        // visual order may differ from the authoritative reveal order, so an
        // exact code comparison alone would incorrectly flip it again at
        // showdown/all-in. An empty hand name is the special blind-straddle
        // reveal and must retain its real uncover animation.
        boolean localShowdownAlreadyVisible
                = reveal.nickname().equals(localNickname)
                && !reveal.handName().isBlank();
        return !localShowdownAlreadyVisible
                && !holeCardsAlreadyRevealed(player, reveal);
    }

    /**
     * Redistributes every player count over the proven ten-player perimeter.
     * The local player remains at the lower centre and lower counts expand over
     * the same outline instead of shrinking towards the table centre.
     */
    private static float[][][] createSeatLayouts() {
        float[][][] layouts = new float[SEAT_COUNT + 1][][];
        for (int playerCount = 2; playerCount <= SEAT_COUNT; playerCount++) {
            layouts[playerCount] = createSeatAnchors(playerCount);
        }
        return layouts;
    }

    static float[][] createSeatAnchors(int playerCount) {
        if (playerCount < 2 || playerCount > SEAT_COUNT) {
            throw new IllegalArgumentException("Numero de jugadores fuera de rango: "
                    + playerCount);
        }
        float[][] anchors = new float[playerCount][2];
        for (int seat = 0; seat < playerCount; seat++) {
            float outlinePosition = seat * (SEAT_COUNT / (float) playerCount);
            int before = (int) Math.floor(outlinePosition);
            int after = (before + 1) % SEAT_COUNT;
            float progress = outlinePosition - before;
            anchors[seat][0] = MathUtils.lerp(
                    TEN_PLAYER_SEAT_OUTLINE[before][0],
                    TEN_PLAYER_SEAT_OUTLINE[after][0], progress);
            anchors[seat][1] = MathUtils.lerp(
                    TEN_PLAYER_SEAT_OUTLINE[before][1],
                    TEN_PLAYER_SEAT_OUTLINE[after][1], progress);
        }
        return anchors;
    }

    /**
     * Build-time geometry guard for the fixed adaptive layouts.
     *
     * This deliberately validates the actual PlayerPod rectangles.  The avatar
     * sits above the left side of its pod, so wrapping both in one large axis
     * aligned rectangle creates an empty upper-right corner and reports false
     * collisions for valid diagonal neighbours (notably seats 2/3 with eight
     * players).  Keep this guard in the focused test suite; a development
     * heuristic must never abort opening a production table.
     */
    static void validateAdaptiveSeatLayouts() {
        for (int playerCount = 2; playerCount <= SEAT_COUNT; playerCount++) {
            float[][] anchors = SEAT_LAYOUTS[playerCount];
            float minimumDistance = Float.POSITIVE_INFINITY;
            for (int a = 0; a < anchors.length; a++) {
                for (int b = a + 1; b < anchors.length; b++) {
                    float ax = a == 0 ? anchors[a][0] * BASE_WIDTH
                            : centeredRivalX(anchors[a][0], BASE_WIDTH);
                    float bx = b == 0 ? anchors[b][0] * BASE_WIDTH
                            : centeredRivalX(anchors[b][0], BASE_WIDTH);
                    float dx = ax - bx;
                    float dy = (anchors[a][1] - anchors[b][1]) * BASE_HEIGHT;
                    minimumDistance = Math.min(minimumDistance,
                            (float) Math.sqrt(dx * dx + dy * dy));
                }
            }
            // The ten-player arrangement is the densest supported case. If its
            // seat envelopes fit, every lower player count has still more room.
            if (minimumDistance < 230f) {
                throw new IllegalStateException("Asientos demasiado juntos para "
                        + playerCount + " jugadores: " + minimumDistance);
            }
            // Validate the visible PlayerPod rectangles. Avatar/card clearance
            // is covered independently by the centre-distance and rival-card
            // envelope invariants above/below.
            for (int a = 1; a < anchors.length; a++) {
                float ax = centeredRivalX(anchors[a][0], BASE_WIDTH);
                float ay = anchors[a][1] * BASE_HEIGHT;
                float apodY = MathUtils.clamp(
                        ay - PLAYER_POD_HEIGHT - 42f, 8f,
                        BASE_HEIGHT - PLAYER_POD_HEIGHT - 8f);
                float aLeft = ax - PLAYER_POD_WIDTH / 2f - 4f;
                float aRight = ax + PLAYER_POD_WIDTH / 2f + 4f;
                float aBottom = apodY - 4f;
                float aTop = apodY + PLAYER_POD_HEIGHT + 4f;
                if (aLeft < 0f || aRight > BASE_WIDTH) {
                    throw new IllegalStateException(
                            "PlayerPod fuera de pantalla para "
                            + playerCount + " jugadores, asiento " + a);
                }
                for (int b = a + 1; b < anchors.length; b++) {
                    float bx = centeredRivalX(anchors[b][0], BASE_WIDTH);
                    float by = anchors[b][1] * BASE_HEIGHT;
                    float bpodY = MathUtils.clamp(
                            by - PLAYER_POD_HEIGHT - 42f, 8f,
                            BASE_HEIGHT - PLAYER_POD_HEIGHT - 8f);
                    float bLeft = bx - PLAYER_POD_WIDTH / 2f - 4f;
                    float bRight = bx + PLAYER_POD_WIDTH / 2f + 4f;
                    float bBottom = bpodY - 4f;
                    float bTop = bpodY + PLAYER_POD_HEIGHT + 4f;
                    boolean horizontalOverlap = aLeft < bRight + 8f
                            && aRight + 8f > bLeft;
                    boolean verticalOverlap = aBottom < bTop + 8f
                            && aTop + 8f > bBottom;
                    if (horizontalOverlap && verticalOverlap) {
                        throw new IllegalStateException(
                                "PlayerPods solapados para " + playerCount
                                + " jugadores: " + a + " y " + b);
                    }
                }
            }
        }
    }

    private static float centeredRivalX(float normalizedX, float width) {
        return MathUtils.clamp(normalizedX * width,
                PLAYER_POD_WIDTH / 2f + 8f,
                width - PLAYER_POD_WIDTH / 2f - 8f);
    }

    static float adjustedRivalSeatY(float anchorY, float viewportHeight,
            float cardAspect) {
        Rectangle envelope = rivalHandEnvelope(cardAspect);
        float rotatedHalfHeight = envelope.height / 2f;
        float minimumSeatY = LOCAL_HUD_SAFE_TOP + rotatedHalfHeight
                + RIVAL_REVEAL_HUD_GAP - RIVAL_HAND_VERTICAL_OFFSET;
        float maximumSeatY = viewportHeight - rotatedHalfHeight
                - RIVAL_REVEAL_TOP_MARGIN - RIVAL_HAND_VERTICAL_OFFSET;
        return MathUtils.clamp(anchorY, minimumSeatY, maximumSeatY);
    }

    /**
     * Keeps non-standard mod backs inside the same rival-seat envelope as the
     * official cards.  A fixed width works for CoronaPoker's 1.346 ratio but a
     * taller mod makes the rotated right card cross the pod/screen edge.  Only
     * such outliers are reduced; every bundled deck retains the canonical
     * 125-world-unit width.
     */
    static float rivalHoleCardWidth(float cardAspect) {
        if (!Float.isFinite(cardAspect) || cardAspect <= 0f) {
            throw new IllegalArgumentException("Invalid card aspect: "
                    + cardAspect);
        }
        float angle = RIVAL_CARD_FAN_ANGLE * MathUtils.degreesToRadians;
        float rightCenter = RIVAL_HAND_CENTER_X_INSET
                + RIVAL_HAND_SIDE_DISTANCE;
        float maximumRotatedHalfWidth = PLAYER_POD_WIDTH + 4f - rightCenter;
        float widthForEnvelope = 2f * maximumRotatedHalfWidth
                / (Math.abs(MathUtils.cos(angle))
                + Math.abs(MathUtils.sin(angle)) * cardAspect);
        return Math.min(RIVAL_HOLE_CARD_WIDTH, widthForEnvelope);
    }

    /** Axis-aligned bounds of both fanned rival cards, relative to the pod. */
    static Rectangle rivalHandEnvelope(float cardAspect) {
        float cardWidth = rivalHoleCardWidth(cardAspect);
        float cardHeight = cardWidth * cardAspect;
        float angle = RIVAL_CARD_FAN_ANGLE * MathUtils.degreesToRadians;
        float rotatedHalfWidth = Math.abs(MathUtils.cos(angle))
                * cardWidth / 2f
                + Math.abs(MathUtils.sin(angle)) * cardHeight / 2f;
        float rotatedHalfHeight = Math.abs(MathUtils.cos(angle))
                * cardHeight / 2f
                + Math.abs(MathUtils.sin(angle)) * cardWidth / 2f;
        float firstCenter = RIVAL_HAND_CENTER_X_INSET
                - RIVAL_HAND_SIDE_DISTANCE + RIVAL_LEFT_CARD_SHIFT;
        float secondCenter = RIVAL_HAND_CENTER_X_INSET
                + RIVAL_HAND_SIDE_DISTANCE;
        float left = Math.min(firstCenter, secondCenter) - rotatedHalfWidth;
        float right = Math.max(firstCenter, secondCenter) + rotatedHalfWidth;
        return new Rectangle(left,
                RIVAL_HAND_VERTICAL_OFFSET - rotatedHalfHeight,
                right - left, rotatedHalfHeight * 2f);
    }

    static boolean rivalCardGeometryFits(float cardAspect) {
        Rectangle envelope = rivalHandEnvelope(cardAspect);
        // Edge pods themselves retain 8 px to the viewport, so the hand may
        // extend at most 4 px beyond the HUD while preserving a 4 px screen gap.
        return envelope.x >= 4f
                && envelope.x + envelope.width <= PLAYER_POD_WIDTH + 4f;
    }

    /**
     * Protects the narrow central lane at the reference 16:9 geometry. The
     * private cards keep their canonical size; the community timer and HUD are
     * packed into the real space between those cards and the board.
     */
    static boolean localCenterLaneHasClearance(float aspect) {
        if (!Float.isFinite(aspect) || aspect <= 0f) return false;
        float boardCardHeight = COMMUNITY_CARD_MAX_WIDTH * aspect;
        float boardBottom = BASE_HEIGHT * 0.52f
                - boardCardHeight * 0.36f;
        float communityHudBottom = boardBottom - COMMUNITY_HUD_Y_OFFSET;
        float communityHudTop = communityHudBottom + COMMUNITY_HUD_HEIGHT;
        float timerBottom = boardBottom - COMMUNITY_TIMER_Y_OFFSET;

        float localCardHeight = LOCAL_HOLE_CARD_WIDTH * aspect;
        float angle = LOCAL_CARD_FAN_ANGLE * MathUtils.degreesToRadians;
        float rotatedHalfHeight = Math.abs(MathUtils.cos(angle))
                * localCardHeight / 2f
                + Math.abs(MathUtils.sin(angle))
                * LOCAL_HOLE_CARD_WIDTH / 2f;
        float localCenter = BASE_HEIGHT * 0.185f
                + LOCAL_HOLE_CENTER_DISTANCE;
        float localCardBottom = localCenter - rotatedHalfHeight;
        float localCardTop = localCenter + rotatedHalfHeight;
        float localHudVisualTop = LOCAL_HUD_Y + LOCAL_HUD_HEIGHT + 17f;

        return communityHudBottom - localCardTop >= 7f
                && timerBottom - communityHudTop >= 0f
                && boardBottom - (timerBottom + COMMUNITY_TIMER_HEIGHT) >= 4f
                && localCardBottom - localHudVisualTop >= 4f;
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        if (startupIntroOnly && !introVisibleSignalled) {
            // Window/context creation must never consume the two-second dark
            // beat. Start its clock on the first visible frame.
            introVisibleSignalled = true;
            delta = 0f;
        }
        totalTime += delta;
        sceneTime += delta;
        updateTextDeleteRepeat(delta);
        updateWindowMonitorRefresh(delta);
        if (startupIntroOnly) {
            renderStartupIntroFrame(delta);
            return;
        }

        Objects.requireNonNull(liveState,
                "A product table frame requires authoritative live state");
        updateLivePositionRotation();
        updateLiveActionChip();
        updateLiveChipBatch();
        updateLivePayout();
        updateLiveRebuy();
        updateLiveInitialStackFill();
        updateLiveCinematic();
        updateLiveShuffle();
        updateLiveCardFlight();
        updateLiveHoleSwap();
        updateLiveCommunityReveal();
        updateLiveHoleReveal();
        updateLiveHoleFold();
        updateTableChat();
        updateVoiceRecording();
        updateRecoveryStopTransition();
        recordFrame(delta);
        if (activeDialog != null) {
            activeDialog.setTimerPaused(totalTime,
                    liveState.snapshot().paused());
        }
        updateDialog();
        updatePointerButtonTransitions();
        updateFastAccessBar(delta);
        handleInput();
        boolean blurSettingsBackdrop = uiLayer == UI_SETTINGS;
        if (blurSettingsBackdrop) {
            ensureSettingsBackdrop();
            settingsBackdrop.begin();
        }
        ScreenUtils.clear(BACKGROUND_BOTTOM, true);
        viewport.apply();
        if (blurSettingsBackdrop) {
            // Viewport.apply() targets the real backbuffer. While the FBO is
            // bound its projection remains correct, but the GL viewport must
            // match the deliberately smaller blur surface.
            Gdx.gl.glViewport(0, 0, settingsBackdrop.getWidth(),
                    settingsBackdrop.getHeight());
        }
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        drawBackground();
        signalReady();
        drawTableScene();
        drawPreparationOverlay();
        if (finalSummary == null) {
            drawPauseOverlay();
        } else {
            drawFinalSummary();
        }
        if (blurSettingsBackdrop) {
            settingsBackdrop.end();
            drawBlurredSettingsBackdrop();
        }
        drawUiLayer();
        drawTerminationOverlay();
        drawActiveDialog();
        drawNetworkReconnectOverlay();
        drawRecoveryStopOverlay();
        drawScreenshotToast();
        drawVolumeOverlay();
        if (screenshotRequested) {
            screenshotRequested = false;
            captureScreenshot();
        }
    }

    private void ensureSettingsBackdrop() {
        int width = settingsBackdropDimension(
                Gdx.graphics.getBackBufferWidth());
        int height = settingsBackdropDimension(
                Gdx.graphics.getBackBufferHeight());
        if (settingsBackdrop != null && settingsBlurScratch != null
                && settingsBackdrop.getWidth() == width
                && settingsBackdrop.getHeight() == height
                && settingsBlurScratch.getWidth() == width
                && settingsBlurScratch.getHeight() == height) {
            return;
        }
        if (settingsBackdrop != null) settingsBackdrop.dispose();
        if (settingsBlurScratch != null) settingsBlurScratch.dispose();
        settingsBackdrop = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        settingsBlurScratch = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        settingsBackdrop.getColorBufferTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
        settingsBlurScratch.getColorBufferTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
    }

    static int settingsBackdropDimension(int backBufferDimension) {
        return Math.max(1, backBufferDimension
                / SETTINGS_BACKDROP_DOWNSAMPLE);
    }

    private void drawBlurredSettingsBackdrop() {
        settingsBlurScratch.begin();
        ScreenUtils.clear(BACKGROUND_BOTTOM, true);
        Gdx.gl.glViewport(0, 0, settingsBlurScratch.getWidth(),
                settingsBlurScratch.getHeight());
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        drawSettingsBlurPass(settingsBackdrop.getColorBufferTexture(),
                1f, 0f);
        settingsBlurScratch.end();

        ScreenUtils.clear(BACKGROUND_BOTTOM, true);
        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        drawSettingsBlurPass(settingsBlurScratch.getColorBufferTexture(),
                0f, 1f);
    }

    private void drawSettingsBlurPass(Texture texture, float directionX,
            float directionY) {
        batch.setShader(backdropBlurShader);
        batch.begin();
        backdropBlurShader.setUniformf("u_texelSize",
                1f / settingsBackdrop.getWidth(),
                1f / settingsBackdrop.getHeight());
        backdropBlurShader.setUniformf("u_direction", directionX,
                directionY);
        batch.setColor(Color.WHITE);
        batch.draw(texture, 0f, 0f,
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                0, 0, texture.getWidth(), texture.getHeight(), false, true);
        batch.end();
        batch.setShader(null);
    }

    private void renderStartupIntroFrame(float delta) {
        updateStars(delta);
        recordFrame(delta);
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            finishIntro();
        }

        ScreenUtils.clear(BACKGROUND_BOTTOM, true);
        viewport.apply();
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        drawBackground();
        if (intro) {
            signalIntroLightsOnIfDue();
            drawIntro();
            drawIntroBlackout();
            if (sceneTime >= INTRO_SECONDS) {
                finishIntro();
            }
        }
    }

    private void handleInput() {
        boolean screenshotAlt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        boolean screenshotControl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean screenshotShift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (!shortcutCaptureConsumed && GdxShortcutBindings.SCREENSHOT.equals(
                shortcutBindings.justPressedAction(Gdx.input, screenshotAlt,
                        screenshotControl, screenshotShift))) {
            screenshotRequested = true;
            if (tablePreference("sonido_screenshot", true)) {
                play(screenshotSound, 0.92f, 1f);
            }
            return;
        }
        if (preparationPhase != TableVisualEvent.PreparationStatus.Phase.READY) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F11)
                    || ((Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))
                    && Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
                toggleFullscreen();
            }
            return;
        }
        // A visible confirmation is always the topmost modal surface. In
        // particular, an OS close request from the final summary opens this
        // dialog; routing final-summary input first made its buttons visible
        // but permanently unreachable.
        if (activeDialog != null) {
            if (activeDialog.isAutoAction()) {
                if (handleAutoActionDialogInput()) return;
            } else {
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                    primaryPointer.capturePressedGesture();
                }
                handleDialogInput();
                if (Gdx.input.isKeyJustPressed(Input.Keys.F11)
                        || (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                        && Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
                    toggleFullscreen();
                }
                return;
            }
        }
        // Terminal and reconnecting surfaces own the complete input frame.
        // Keeping these gates in the main router is essential: consuming raw
        // InputProcessor events alone does not hide libGDX's polled button
        // state from the live table controls below.
        if (finalSummary != null) {
            handleFinalSummaryInput();
            return;
        }
        if (isClientTransportReconnecting()) {
            boolean reconnectAlt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
            boolean reconnectControl = Gdx.input.isKeyPressed(
                    Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean reconnectShift = Gdx.input.isKeyPressed(
                    Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            String reconnectAction = shortcutBindings.justPressedAction(
                    Gdx.input, reconnectAlt, reconnectControl,
                    reconnectShift);
            if (GdxShortcutBindings.FORCE_EXIT.equals(reconnectAction)) {
                Gdx.app.exit();
            }
            return;
        }
        if (shortcutCaptureConsumed) {
            shortcutCaptureConsumed = false;
            return;
        }
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        String shortcutAction = shortcutBindings.justPressedAction(Gdx.input,
                alt, control, shift);
        if (terminationRequested) {
            if (GdxShortcutBindings.FORCE_EXIT.equals(shortcutAction)) {
                Gdx.app.exit();
            }
            return;
        }
        if (uiLayer == UI_NONE && liveState != null) {
            if (GdxShortcutBindings.QUIT.equals(shortcutAction)) {
                requestExit();
                return;
            }
            if (GdxShortcutBindings.FORCE_EXIT.equals(shortcutAction)) {
                Gdx.app.exit();
                return;
            }
            if (GdxShortcutBindings.HALT.equals(shortcutAction)) {
                requestStopGame();
                return;
            }
            if (GdxShortcutBindings.LOG.equals(shortcutAction)) {
                gameLogScroll = 0;
                openUiLayer(UI_GAME_LOG);
                return;
            }
            if (GdxShortcutBindings.MUTE.equals(shortcutAction)) {
                toggleMasterSound();
                return;
            }
            if (GdxShortcutBindings.VOLUME_UP.equals(shortcutAction)) {
                adjustMasterVolume(0.01f);
                return;
            }
            if (GdxShortcutBindings.VOLUME_DOWN.equals(shortcutAction)) {
                adjustMasterVolume(-0.01f);
                return;
            }
            if (GdxShortcutBindings.BUYIN.equals(shortcutAction)) {
                if (canToggleImmediateRebuy()) {
                    submit(new TableCommand.ToggleImmediateRebuy());
                }
                return;
            }
            if (GdxShortcutBindings.FASTCHAT_IMAGE.equals(shortcutAction)) {
                if (canUseTableImages()) {
                    openTableImageGallery();
                }
                return;
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (uiLayer == UI_CHAT) {
                closeTableChat();
            } else if (uiLayer == UI_CARD_VIEWER) {
                closeCardViewer();
            } else if (uiLayer == UI_SCREENSHOTS) {
                closeScreenshotViewer();
            } else if (uiLayer == UI_HAND_GENERATOR) {
                uiLayer = UI_NONE;
            } else if (uiLayer == UI_SETTINGS) {
                if (voiceNotesOpen) {
                    if (voiceNoteDeleteConfirmation != null
                            || voiceNotesPurgeConfirmation) {
                        voiceNoteDeleteConfirmation = null;
                        voiceNotesPurgeConfirmation = false;
                    } else {
                        closeTableVoiceNotes();
                    }
                } else {
                    requestCancelTableSettings(null);
                }
            } else if (uiLayer == UI_GAME_LOG) {
                uiLayer = UI_NONE;
            } else if (liveState == null) {
                Gdx.app.exit();
            }
        }
        if (GdxShortcutBindings.FULLSCREEN.equals(shortcutAction)
                || Gdx.input.isKeyJustPressed(Input.Keys.F11)
                || (alt && Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            toggleFullscreen();
        }
        if (consumeAvatarZoomPointer()) {
            return;
        }
        if (uiLayer == UI_GAME_LOG
                && Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (gameLogContentBounds().contains(pointer)) {
                openGameLogEditMenu(pointer.x, pointer.y);
            } else {
                gameLogEditMenuOpen = false;
            }
            return;
        }
        if (uiLayer == UI_GAME_LOG && gameLogEditMenuOpen
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            handleGameLogEditMenuClick(pointer.x, pointer.y);
            return;
        }
        if (uiLayer == UI_CHAT
                && Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            Rectangle input = tableChatInputBounds();
            if (input.contains(pointer)) {
                chatEdit.focus("tableChat", chatDraft);
                openTableChatEditMenu(pointer.x, pointer.y);
            } else {
                chatEditMenuOpen = false;
            }
            return;
        }
        if (!intro && Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)
                && uiLayer == UI_NONE) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (uiLayer == UI_NONE && liveState != null
                    && handleSeatChatNoticeClick(pointer.x, pointer.y, true)) {
                return;
            }
            if (uiLayer == UI_NONE && liveState != null
                    && liveCardContains(pointer.x, pointer.y)) {
                // Direct card gesture retained after removing the generic
                // right-click popup: only a card owns this interaction.
                submit(new TableCommand.ChangeDeck());
                return;
            }
            return;
        }
        if (uiLayer != UI_NONE) {
            if (uiLayer == UI_SCREENSHOTS) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
                    showRelativeScreenshot(-1);
                    return;
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
                    showRelativeScreenshot(1);
                    return;
                }
            }
            if (uiLayer == UI_HAND_GENERATOR) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)
                        || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
                    handGenerator.previous();
                    return;
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
                        || Gdx.input.isKeyJustPressed(Input.Keys.UP)
                        || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                        || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER)) {
                    handGenerator.next();
                    return;
                }
            }
            if (uiLayer == UI_CHAT
                    && (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                    || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER))) {
                sendTableChat();
                return;
            }
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                primaryPointer.capturePressedGesture();
                pointer.set(Gdx.input.getX(), Gdx.input.getY());
                viewport.unproject(pointer);
                if (uiLayer == UI_CHAT && chatEditMenuOpen) {
                    handleTableChatEditMenuClick(pointer.x, pointer.y);
                    return;
                }
                handleUiClick(pointer.x, pointer.y);
            }
            return;
        }
        handleLiveTableInput(shortcutAction);
    }

    private boolean liveCardContains(float x, float y) {
        return liveCardAt(x, y) != null;
    }

    private String iwtsthCandidateAt(float x, float y) {
        if (liveState == null || liveState.snapshot().paused()
                || uiLayer != UI_NONE || activeDialog != null) {
            return null;
        }
        for (Seat seat : seats) {
            if (seat.index == 0 || !liveState.isIwtsthCandidate(seat.name)) {
                continue;
            }
            if (contains(x, y, seat.podX + 7f, seat.podY + 7f,
                    PLAYER_POD_WIDTH - 14f, 44f)) {
                return seat.name;
            }
            TableSnapshot.PlayerSnapshot player = livePlayer(seat);
            int cards = player == null ? 0
                    : Math.min(2, player.holeCards().size());
            for (int slot = cards - 1; slot >= 0; slot--) {
                if (player.holeCards().get(slot).visible()
                        && placementContains(liveHolePlacement(seat, slot), x, y)) {
                    return seat.name;
                }
            }
        }
        return null;
    }

    private ViewedCard liveCardAt(float x, float y) {
        for (TableSnapshot.PlayerSnapshot player
                : liveState.snapshot().players()) {
            Seat seat = seatByNickname(player.nickname());
            if (seat == null) continue;
            int limit = Math.min(2, player.holeCards().size());
            // Slot 1 is painted above slot 0 in the fan, so hit-test it first.
            for (int slot = limit - 1; slot >= 0; slot--) {
                TableSnapshot.CardSnapshot card = player.holeCards().get(slot);
                if (!card.visible()) continue;
                if (placementContains(liveHolePlacement(seat, slot), x, y)) {
                    return viewedCard(card);
                }
            }
        }
        float width = viewport.getWorldWidth();
        float tableW = Math.min(1510f, width * 0.78f);
        Texture cardBack = activeCardBack();
        float cardW = Math.min(COMMUNITY_CARD_MAX_WIDTH, tableW / 10f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = tableCenterX - gap * 2f - cardW / 2f;
        float cardY = tableCenterY - cardH * 0.36f;
        List<TableSnapshot.CardSnapshot> board
                = liveState.snapshot().communityCards();
        for (int slot = 0; slot < Math.min(5, board.size()); slot++) {
            TableSnapshot.CardSnapshot card = board.get(slot);
            if (!card.visible()) continue;
            LiveCardPlacement placement = new LiveCardPlacement(
                    firstX + slot * gap + cardW / 2f,
                    cardY + cardH / 2f, cardW, cardH, 0f);
            if (placementContains(placement, x, y)) return viewedCard(card);
        }
        return null;
    }

    private boolean rabbitCardContains(float x, float y) {
        if (liveState == null || !liveState.rabbitRequestable()
                || liveState.snapshot().paused()) {
            return false;
        }
        float tableW = Math.min(1510f, viewport.getWorldWidth() * 0.78f);
        Texture cardBack = activeCardBack();
        float cardW = Math.min(COMMUNITY_CARD_MAX_WIDTH, tableW / 10f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = tableCenterX - gap * 2f - cardW / 2f;
        float cardY = tableCenterY - cardH * 0.36f;
        List<TableSnapshot.CardSnapshot> board
                = liveState.snapshot().communityCards();
        for (int slot = 0; slot < Math.min(5, board.size()); slot++) {
            TableSnapshot.CardSnapshot card = board.get(slot);
            if (!card.visible() || card.faceUp()) continue;
            LiveCardPlacement placement = new LiveCardPlacement(
                    firstX + slot * gap + cardW / 2f,
                    cardY + cardH / 2f, cardW, cardH, 0f);
            if (placementContains(placement, x, y)) return true;
        }
        return false;
    }

    private ViewedCard viewedCard(TableSnapshot.CardSnapshot card) {
        boolean faceUp = card.faceUp() && !card.code().isBlank();
        return new ViewedCard(faceUp ? liveCardFace(card.code())
                : activeCardBack(), faceUp);
    }

    private void openCardViewer(ViewedCard card) {
        cardViewerTexture = card.texture();
        cardViewerFaceUp = card.faceUp();
        openUiLayer(UI_CARD_VIEWER);
        if (tablePreference("sonido_visor", true)) {
            play(cardViewerSound, 0.92f, 1f);
        }
    }

    private void closeCardViewer() {
        uiLayer = UI_NONE;
        cardViewerTexture = null;
        cardViewerFaceUp = false;
    }

    private boolean emptyFeltGestureContains(float x, float y) {
        if (intro || startupIntroOnly || liveState == null
                || liveState.snapshot().paused() || uiLayer != UI_NONE
                || activeDialog != null || liveCardContains(x, y)) {
            return false;
        }
        for (Seat seat : seats) {
            if (seatPresenceAlpha(seat.index) <= 0f) continue;
            if (contains(x, y, seat.podX - 16f, seat.podY - 16f,
                    PLAYER_POD_WIDTH + 32f, PLAYER_POD_HEIGHT + 104f)) {
                return false;
            }
        }
        float width = viewport.getWorldWidth();
        float hudWidth = Math.min(1110f, width - 620f);
        float hudX = width / 2f - hudWidth / 2f;
        if (contains(x, y, hudX - 12f, LOCAL_HUD_Y - 12f,
                hudWidth + 24f, LOCAL_HUD_HEIGHT + 42f)) {
            return false;
        }
        if (contains(x, y, tableCenterX - 470f, tableCenterY - 300f,
                940f, 600f)) {
            return false;
        }
        float fastWidth = fastBarExpanded
                ? fastExpandedWidth() : FAST_BUTTON_SIZE + 2f * FAST_BAR_PADDING;
        return !contains(x, y, FAST_BAR_X, FAST_BAR_Y,
                fastWidth, FAST_BUTTON_SIZE + 2f * FAST_BAR_PADDING);
    }

    private void registerEmptyFeltClick(float x, float y, long nowNanos) {
        boolean doubleClick = previousFeltClickNanos > 0L
                && nowNanos - previousFeltClickNanos <= FELT_DOUBLE_CLICK_NANOS
                && Vector2.dst(previousFeltClickX, previousFeltClickY, x, y)
                        <= FELT_DOUBLE_CLICK_DRIFT;
        previousFeltClickNanos = nowNanos;
        previousFeltClickX = x;
        previousFeltClickY = y;
        if (!doubleClick || presentationSettings == null) return;
        previousFeltClickNanos = 0L;
        replaceFeltTexture(presentationSettings.selectNextFelt(), true);
    }

    private void replaceFeltTexture(String felt) {
        replaceFeltTexture(felt, false);
    }

    private void replaceFeltTexture(String felt, boolean userInitiated) {
        Texture replacement = feltTexture("images/tapete_"
                + GdxGamePresentationSettings.normalizedFelt(felt) + ".jpg");
        replacement.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        Texture previous = feltTexture;
        feltTexture = replacement;
        if (previous != null) previous.dispose();
        if (userInitiated && tablePreference("sonido_efectos", true)
                && tablePreference("sonido_tapete", true)) {
            // Same cue and preference gate as Swing's TablePanel.refresh().
            play(feltChangeSound, 0.92f, 1f);
        }
    }

    private static boolean placementContains(LiveCardPlacement placement,
            float x, float y) {
        // Transform the pointer into card-local coordinates, so the clickable
        // area follows the rendered fan rotation instead of using a loose box.
        float radians = -placement.rotation * MathUtils.degreesToRadians;
        float dx = x - placement.x;
        float dy = y - placement.y;
        float localX = dx * MathUtils.cos(radians)
                - dy * MathUtils.sin(radians);
        float localY = dx * MathUtils.sin(radians)
                + dy * MathUtils.cos(radians);
        return Math.abs(localX) <= placement.width / 2f
                && Math.abs(localY) <= placement.height / 2f;
    }

    private void finishIntro() {
        signalIntroLightsOn();
        intro = false;
        sceneTime = 0f;
        signalReady();
    }

    private void signalIntroLightsOnIfDue() {
        if (sceneTime >= INTRO_LIGHT_SWITCH_TIME) {
            signalIntroLightsOn();
        }
    }

    private void signalIntroLightsOn() {
        if (introLightsSignalled) return;
        introLightsSignalled = true;
        onIntroLightsOn.run();
    }

    private void signalReady() {
        if (!readySignalled) {
            readySignalled = true;
            onReady.run();
        }
    }

    void showDialog(GdxTableDialog request) {
        Objects.requireNonNull(request, "request");
        if (activeDialog == null) {
            activeDialog = request;
            request.opened(totalTime);
            focusAutoCallAmount(request, true);
        } else {
            dialogQueue.addLast(request);
        }
    }

    GdxTableDialog requestExit() {
        if (terminationRequested) return null;
        if (liveState == null) {
            Gdx.app.exit();
            return null;
        }
        if (terminationConfirmation != null
                && !terminationConfirmation.complete()) {
            return terminationConfirmation;
        }
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                uppercase(gameText.translate("gdx.dialog.confirmation")),
                uppercase(gameText.translate(
                        "exit.salir_de_la_timba_pregunta")),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.EXIT,
                760, 0, false,
                uppercase(gameText.translate("ui.cancelar")),
                uppercase(gameText.translate("ui.aceptar")));
        terminationConfirmation = confirmation;
        confirmation.result().thenAccept(accepted -> {
            if (terminationConfirmation == confirmation) {
                terminationConfirmation = null;
            }
            if (accepted) submitTermination(new TableCommand.ExitGame(), false);
        });
        showDialog(confirmation);
        return confirmation;
    }

    /**
     * Routes an OS window-close gesture through pending settings before the
     * normal table-exit confirmation. This keeps Alt+F4 and the quick action
     * semantically identical without silently discarding an open transaction.
     */
    void requestWindowClose() {
        if (finalSummary != null) {
            requestFinalSummaryWindowClose();
        } else if (uiLayer == UI_SETTINGS) {
            requestCancelTableSettings(this::requestExit);
        } else {
            requestExit();
        }
    }

    private void requestFinalSummaryWindowClose() {
        if (finalExitPending) return;
        if (terminationConfirmation != null
                && !terminationConfirmation.complete()) {
            return;
        }
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                uppercase(gameText.translate("gdx.dialog.confirmation")),
                uppercase(gameText.translate(
                        "exit.salir_de_la_timba_pregunta")),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.EXIT,
                760, 0, false,
                uppercase(gameText.translate("ui.cancelar")),
                uppercase(gameText.translate("ui.aceptar")));
        terminationConfirmation = confirmation;
        confirmation.result().thenAccept(accepted -> {
            if (terminationConfirmation == confirmation) {
                terminationConfirmation = null;
            }
            if (!accepted || finalSummary == null || finalExitPending) return;
            // The dealer has already published CloseTable and is blocked only
            // by the visible final-summary barrier. Sending ExitGame here asks
            // an already-finished session for a second terminal transition and
            // can never complete. Release the summary first; the shell exits
            // only after closeTable has disposed the table resources.
            finalApplicationExitRequested = true;
            finalExitPending = true;
            CompletableFuture<Void> barrier = finalSummaryBarrier;
            if (barrier != null && !barrier.isDone()) barrier.complete(null);
        });
        showDialog(confirmation);
    }

    private void requestStopGame() {
        if (terminationRequested || liveState == null || !tableHost) {
            return;
        }
        if (terminationConfirmation != null
                && !terminationConfirmation.complete()) {
            return;
        }
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                uppercase(gameText.translate("exit.detener_la_timba")),
                uppercase(gameText.translate("gdx.stop_for_recovery")),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.EXIT,
                820, 0, false,
                uppercase(gameText.translate("ui.cancelar")),
                uppercase(gameText.translate("ui.aceptar")));
        terminationConfirmation = confirmation;
        confirmation.result().thenAccept(accepted -> {
            if (terminationConfirmation == confirmation) {
                terminationConfirmation = null;
            }
            if (accepted) submitTermination(new TableCommand.StopGame(), true);
        });
        showDialog(confirmation);
    }

    private void requestLastHandChange() {
        if (liveState == null || !tableHost) return;
        if (liveState.lastHand()) {
            submit(new TableCommand.SetLastHand(false));
            return;
        }
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                uppercase(gameText.translate("game.ultima_mano_2")),
                uppercase(gameText.translate("gdx.last_hand_detail")),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.NONE,
                820, 0, false,
                uppercase(gameText.translate("ui.cancelar")),
                uppercase(gameText.translate("game.ultima_mano_3")));
        confirmation.result().thenAccept(accepted -> {
            if (accepted) submit(new TableCommand.SetLastHand(true));
        });
        showDialog(confirmation);
    }

    GdxTableDialog requestForceReconnect() {
        if (liveState == null || !tableHost || !hasRemoteHumanPeers()) {
            return null;
        }
        if (forceReconnectConfirmation != null
                && !forceReconnectConfirmation.complete()) {
            return forceReconnectConfirmation;
        }
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                uppercase(gameText.translate(
                        "conn.forzar_reconexion_de_todos_los")),
                uppercase(gameText.translate("gdx.force_reconnect_detail")),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.MAINTENANCE,
                900, 0, false,
                uppercase(gameText.translate("ui.cancelar")),
                uppercase(gameText.translate("conn.reconectar")));
        forceReconnectConfirmation = confirmation;
        confirmation.result().thenAccept(accepted -> {
            if (forceReconnectConfirmation == confirmation) {
                forceReconnectConfirmation = null;
            }
            if (accepted) submit(new TableCommand.ForceReconnectPlayers());
        });
        showDialog(confirmation);
        return confirmation;
    }

    private void openDraftHandLimitDialog() {
        if (!tableHost || liveSettingsDraft == null) return;
        GdxTableDialog dialog = GdxTableDialog.handLimit(
                liveState == null ? 0 : liveState.handNumber(),
                liveSettingsDraft.hands(), gameText);
        dialog.result().thenAccept(accepted -> {
            if (accepted && liveSettingsDraft != null) {
                liveSettingsDraft = liveSettingsDraft.withHands(
                        dialog.noLimit() ? -1 : dialog.amount());
            }
        });
        showDialog(dialog);
    }

    private void updateDialog() {
        if (activeDialog != null && !activeDialog.complete()
                && activeDialog.expired(totalTime)) {
            activeDialog.timeout();
        }
        if (activeDialog != null && activeDialog.readyToClose()) {
            GdxTableDialog completed = activeDialog;
            activeDialog = dialogQueue.pollFirst();
            if (completed == gameOverAnimationDialog) {
                releaseGameOverAnimation();
            }
            if (completed == recoveryAnimationDialog) {
                releaseRecoveryAnimation();
            }
            dialogAmountEdit.blur();
            if (activeDialog != null) {
                activeDialog.opened(totalTime);
                focusAutoCallAmount(activeDialog, true);
            }
        }
    }

    /**
     * Advances the same dialog state machine normally ticked by the render
     * loop.  Package-private scenario harnesses use this to emulate a real GDX
     * frame without starting a second OpenGL window; no dialog is completed or
     * bypassed here.
     */
    void advanceDialogState() {
        updateDialog();
    }

    boolean hasActiveDialog() {
        return activeDialog != null;
    }

    private void handleDialogInput() {
        GdxTableDialog dialog = activeDialog;
        if (dialog == null) return;
        if (dialog.isExternallyControlled()
                || dialog.waitingForExternalClose()) {
            // Swing's RecoverDialog is DO_NOTHING_ON_CLOSE. The dealer alone
            // releases this modal after replay; input remains captured here.
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            resolveActiveDialogChoice(false);
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER)) {
            if (!dialog.showsPositive()) return;
            resolveActiveDialogChoice(true);
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) return;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        float panelW = dialogWidth(dialog);
        float panelH = dialogHeight(dialog);
        float panelX = dialogX(dialog, panelW);
        float panelY = dialogY(dialog, panelH);
        float negativeX = panelX + (dialog.isAutoAction() ? 30f : 42f);
        float negativeW = dialog.isAutoAction()
                ? panelW - 60f : 230f;
        float buttonY = panelY + (dialog.isAutoAction() ? 22f : 34f);
        float buttonH = dialog.isAutoAction() ? 52f : 64f;
        if (dialog.showsNegative()
                && contains(pointer.x, pointer.y, negativeX,
                        buttonY, negativeW, buttonH)) {
            resolveActiveDialogChoice(false);
        } else if (dialog.isAutoCall()
                && contains(pointer.x, pointer.y, panelX + 56f,
                        panelY + 318f, panelW - 112f, 64f)) {
            dialog.toggleOptionEnabled();
            focusAutoCallAmount(dialog, dialog.autoCallAmountEditable());
        } else if (dialog.isAutoCall()
                && contains(pointer.x, pointer.y, panelX + 56f,
                        panelY + 244f, panelW - 112f, 64f)) {
            dialog.toggleNoLimit();
            focusAutoCallAmount(dialog, dialog.autoCallAmountEditable());
        } else if (dialog.isHandLimit()
                && contains(pointer.x, pointer.y, panelX + 56f,
                        panelY + 244f, panelW - 112f, 64f)) {
            dialog.toggleNoLimit();
        } else if (dialog.isAutoCall()
                && contains(pointer.x, pointer.y,
                        panelX + panelW / 2f - 110f,
                        panelY + 155f, 220f, 64f)) {
            focusAutoCallAmount(dialog, true);
        } else if (dialog.hasAmount()
                && contains(pointer.x, pointer.y, panelX + panelW / 2f - 190f,
                        panelY + 155f, 72f, 64f)) {
            dialog.changeAmount(-1);
            focusAutoCallAmount(dialog, false);
        } else if (dialog.hasAmount()
                && contains(pointer.x, pointer.y, panelX + panelW / 2f + 118f,
                        panelY + 155f, 72f, 64f)) {
            dialog.changeAmount(1);
            focusAutoCallAmount(dialog, false);
        } else if (dialog.showsPositive() && contains(pointer.x, pointer.y,
                panelX + panelW - 272f, panelY + 34f, 230f, 64f)) {
            resolveActiveDialogChoice(true);
        }
    }

    /**
     * Resolves the dialog currently owned by the table. Pointer, keyboard and
     * automated scenario drivers deliberately share this single path so a
     * scenario cannot bypass the product wiring between the GDX table and the
     * dealer decision future.
     */
    boolean resolveActiveDialogChoice(boolean accepted) {
        GdxTableDialog dialog = activeDialog;
        if (dialog == null || dialog.isExternallyControlled()
                || dialog.waitingForExternalClose()) return false;
        if (accepted) {
            if (!dialog.showsPositive()) return false;
            dialog.accept();
        } else {
            if (!dialog.allowsDismissal()) return false;
            dialog.dismiss();
        }
        return true;
    }

    /**
     * Swing's AUTO MODE veto is intentionally non-modal: ESC cancels, SPACE
     * accepts immediately, and pointer/keyboard input outside the overlay keeps
     * reaching table utilities and menus. The poker action row itself remains
     * disabled until the veto resolves, exactly like the classic frontend.
     */
    private boolean handleAutoActionDialogInput() {
        if (activeDialog == null || !activeDialog.isAutoAction()) return false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            activeDialog.dismiss();
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            activeDialog.accept();
            return true;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) return false;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        float panelW = dialogWidth(activeDialog);
        float panelH = dialogHeight(activeDialog);
        float panelX = dialogX(activeDialog, panelW);
        float panelY = dialogY(activeDialog, panelH);
        if (!contains(pointer.x, pointer.y, panelX, panelY, panelW, panelH)) {
            return false;
        }
        primaryPointer.capturePressedGesture();
        handleDialogInput();
        return true;
    }

    private float dialogWidth(GdxTableDialog dialog) {
        if (dialog.isAutoAction()) {
            float hudWidth = Math.min(1110f,
                    viewport.getWorldWidth() - 620f);
            float actionWidth = hudWidth - 230f - 12f;
            return Math.min(620f, actionWidth);
        }
        return MathUtils.clamp(dialog.preferredWidth() > 0
                ? dialog.preferredWidth() : 860f, 620f, 1200f);
    }

    private static float dialogHeight(GdxTableDialog dialog) {
        return dialog.isAutoAction() ? 230f
                : dialog.isGameOver()
                        ? (dialog.showsPositive() ? 570f : 390f)
                : dialog.isAutoCall() || dialog.isHandLimit() ? 540f
                : dialog.isRebuy() ? 390f
                : dialog.hasAmount() ? 470f : 390f;
    }

    private float dialogX(GdxTableDialog dialog, float panelW) {
        if (dialog.isAutoAction()) {
            float hudWidth = Math.min(1110f,
                    viewport.getWorldWidth() - 620f);
            float hudX = viewport.getWorldWidth() / 2f - hudWidth / 2f;
            float actionX = hudX + 230f + 12f;
            float actionW = hudWidth - 230f - 12f;
            return actionX + (actionW - panelW) / 2f;
        }
        return (viewport.getWorldWidth() - panelW) / 2f;
    }

    private float dialogY(GdxTableDialog dialog, float panelH) {
        if (dialog.isAutoAction()) {
            // Same causal/visual ownership as Swing's showOn(): over the local
            // action row, not floating in the middle of the board.
            return LOCAL_HUD_Y + 6f;
        }
        return (viewport.getWorldHeight() - panelH) / 2f;
    }

    private void handleLiveTableInput(String shortcutAction) {
        ActionControlState controls = liveState.actionControls();
        boolean autoActionVeto = activeDialog != null
                && activeDialog.isAutoAction();
        boolean localTurn = hasActiveLocalTurn();
        maybeExecuteQueuedPreAction(localTurn, controls);
        TableShortcut shortcut = tableShortcut(shortcutAction);
        if (shortcut == TableShortcut.PAUSE) {
            togglePauseAction();
        } else if (shortcut == TableShortcut.LIGHTS
                && !liveState.snapshot().paused()) {
            toggleUserLights();
        } else if (!autoActionVeto && localTurn) {
            switch (shortcut) {
                case FOLD -> {
                    activateFoldAction();
                }
                case CHECK_OR_SHOW -> {
                    if (controls.showCards()) {
                        submit(new TableCommand.ShowCards());
                    } else {
                        activateCheckOrCallAction();
                    }
                }
                case BET_DOWN -> adjustLiveBet(-1);
                case BET_UP -> adjustLiveBet(1);
                case BET -> {
                    activateBetAction();
                }
                case ALL_IN -> {
                    activateAllInAction();
                }
                default -> {
                }
            }
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                && fastBarExpanded) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            int fastIndex = fastButtonAt(pointer.x, pointer.y);
            if (visibleFastAccessActionAt(fastIndex) == FastAccessAction.VOICE
                    && fastButtonEnabled(fastIndex)) {
                micPointerHeld = true;
                beginVoiceRecording();
                return;
            }
        }
        if (!leftButtonReleasedThisFrame) {
            return;
        }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        // The pause shade owns the whole table, just like Swing's glass pane.
        // Route it before cards, seat notices and HUD controls so the release
        // cannot click through the visible modal overlay.  The full overlay is
        // deliberately clickable; the white banner is only its visual focus.
        if (pauseOverlayConsumesRelease(liveState.snapshot().paused(),
                leftButtonReleasedThisFrame)) {
            togglePauseAction();
            return;
        }
        if (handleSeatChatNoticeClick(pointer.x, pointer.y, false)) {
            return;
        }
        String iwtsthCandidate = iwtsthCandidateAt(pointer.x, pointer.y);
        if (iwtsthCandidate != null) {
            // Close the local hit target synchronously, as Swing does once the
            // request starts. The dealer still validates every rule and owns
            // the network request; this only prevents accidental double-clicks.
            liveState.dismissIwtsthCandidates();
            submit(new TableCommand.RequestIwtsth(iwtsthCandidate));
            return;
        }
        if (rabbitCardContains(pointer.x, pointer.y)) {
            liveState.dismissRabbitRequest();
            submit(new TableCommand.RequestRabbit());
            return;
        }
        ViewedCard viewedCard = liveCardAt(pointer.x, pointer.y);
        if (viewedCard != null) {
            openCardViewer(viewedCard);
            return;
        }
        if (handleFastAccessClick(pointer.x, pointer.y)) {
            return;
        }
        if (contains(pointer.x, pointer.y, communityPauseX,
                communityPauseY, communityPauseWidth,
                communityPauseHeight)) {
            togglePauseAction();
            return;
        }
        if (!liveState.snapshot().paused()
                && contains(pointer.x, pointer.y, communityLightsX,
                        communityLightsY, communityLightsWidth,
                        communityLightsHeight)) {
            toggleUserLights();
            return;
        }
        if (contains(pointer.x, pointer.y, communitySoundX,
                communitySoundY, communitySoundWidth,
                communitySoundHeight)) {
            toggleMasterSound();
            return;
        }
        if (tableHost && contains(pointer.x, pointer.y, communityHandX,
                communityHandY, communityHandWidth,
                communityHandHeight)) {
            requestLastHandChange();
            return;
        }
        if (autoActionVeto) {
            return;
        }
        switch (hudTarget(pointer.x, pointer.y, viewport.getWorldWidth())) {
            case 1 -> {
                if (localTurn) {
                    activateFoldAction();
                } else if (!localTurn && autoPreActionsVisible()) {
                    toggleQueuedPreAction(1);
                }
            }
            case 2 -> {
                if (localTurn) {
                    activateCheckOrCallAction();
                } else if (!localTurn && autoPreActionsVisible()) {
                    toggleQueuedPreAction(2);
                }
            }
            case 3 -> {
                if (localTurn) adjustLiveBet(-1);
            }
            case 4 -> {
                if (localTurn) adjustLiveBet(1);
            }
            case 5 -> {
                if (localTurn) activateBetAction();
            }
            case 6 -> {
                if (localTurn && controls.showCards()) {
                    submit(new TableCommand.ShowCards());
                } else {
                    activateAllInAction();
                }
            }
            default -> { }
        }
    }

    static TableShortcut tableShortcut(String action) {
        if (action == null) return TableShortcut.NONE;
        return switch (action) {
            case GdxShortcutBindings.PAUSE -> TableShortcut.PAUSE;
            case GdxShortcutBindings.LIGHTS -> TableShortcut.LIGHTS;
            case GdxShortcutBindings.FOLD -> TableShortcut.FOLD;
            case GdxShortcutBindings.CHECK -> TableShortcut.CHECK_OR_SHOW;
            case GdxShortcutBindings.BET_DOWN -> TableShortcut.BET_DOWN;
            case GdxShortcutBindings.BET_UP -> TableShortcut.BET_UP;
            case GdxShortcutBindings.BET -> TableShortcut.BET;
            case GdxShortcutBindings.ALL_IN -> TableShortcut.ALL_IN;
            default -> TableShortcut.NONE;
        };
    }

    enum TableShortcut {
        NONE,
        PAUSE,
        LIGHTS,
        FOLD,
        CHECK_OR_SHOW,
        BET_DOWN,
        BET_UP,
        BET,
        ALL_IN
    }

    enum FastAccessAction {
        SETTINGS,
        CHAT,
        VOICE,
        IMAGE,
        REBUY,
        GAME_LOG,
        FULLSCREEN,
        STOP,
        EXIT,
        NONE
    }

    private void maybeExecuteQueuedPreAction(boolean localTurn,
            ActionControlState controls) {
        if (!localTurn || queuedPreAction == 0 || !autoActionControlsReady
                || totalTime < autoActionEligibleAt || activeDialog != null) {
            return;
        }
        int selection = queuedPreAction;
        AutoActionResolver.QueuedAction queued = switch (selection) {
            case 1 -> AutoActionResolver.QueuedAction.FOLD_OR_CHECK;
            case 2 -> AutoActionResolver.QueuedAction.CHECK_OR_CALL;
            default -> AutoActionResolver.QueuedAction.NONE;
        };
        AutoActionResolver.Target resolved = AutoActionResolver.resolve(queued,
                controls, liveState.snapshot().street()
                        == TableSnapshot.Street.PREFLOP,
                liveState.bigBlind(), autoCallEnabled, autoCallMax);
        int target = switch (resolved) {
            case FOLD -> 1;
            case CHECK_OR_CALL -> 2;
            case ALL_IN -> 6;
            case NONE -> 0;
        };
        TableCommand command = switch (resolved) {
            case FOLD -> new TableCommand.Fold();
            case CHECK_OR_CALL -> new TableCommand.CheckOrCall();
            case ALL_IN -> new TableCommand.AllIn();
            case NONE -> null;
        };
        queuedPreAction = queuedPreActionAfterTargetResolution(selection, target);
        if (command == null) {
            // Swing's esTuTurno() calls desPrePulsarAutoTodo() when the queued
            // choice has no legal target (for example AUTO Call above its
            // configured cap).  It must not leak into a later street/hand.
            return;
        }
        autoActionEligibleAt = Float.POSITIVE_INFINITY;
        autoActionControlsReady = false;
        if (!autoModeConfirm) {
            // Swing keeps an executed AUTO choice armed for the rest of the
            // hand. Re-arm it BEFORE submitting because command delivery may
            // synchronously finish the street or hand. The canonical
            // PreActionControls(clearSelection) event must run afterwards and
            // have the final word on whether the choice survives the boundary.
            queuedPreAction = retainedPreAction(selection, target, true);
            submit(command);
            return;
        }
        String action = target == 1
                ? uppercase(gameText.translate("action.no_ir"))
                : controls.callAction() == ActionControlState.CallAction.CHECK
                        ? uppercase(gameText.translate("action.pasar"))
                        : uppercase(gameText.translate("ui.ir"));
        GdxTableDialog confirmation = GdxTableDialog.autoAction(action,
                gameText);
        confirmation.result().thenAccept(accepted -> {
            if (accepted && hasActiveLocalTurn()) {
                // See the direct path above: a synchronous hand boundary must
                // be able to clear this selection when persistence is off.
                queuedPreAction = retainedPreAction(selection, target, true);
                submit(command);
            }
        });
        showDialog(confirmation);
    }

    static int retainedPreAction(int selection, int target,
            boolean accepted) {
        return accepted && target != 0 ? selection : 0;
    }

    static int queuedPreActionAfterTargetResolution(int selection,
            int target) {
        return 0;
    }

    static int queuedPreActionAfterControlsEvent(int selection,
            boolean active, boolean clearSelection) {
        return !active && clearSelection ? 0 : selection;
    }

    /**
     * Returns the card slot that must be painted last for a screen-space fan.
     * The card physically on the right always owns the upper layer, including
     * the second half of a swap animation after both cards have crossed.
     */
    static int upperHoleCardSlot(float firstCenterX, float secondCenterX) {
        return firstCenterX > secondCenterX ? 0 : 1;
    }

    /** Returns the slot for back-to-front layer zero or one. */
    static int holeCardSlotForLayer(float firstCenterX, float secondCenterX,
            int layer) {
        int upper = upperHoleCardSlot(firstCenterX, secondCenterX);
        return layer <= 0 ? 1 - upper : upper;
    }

    static boolean autoActionControlsReadyAfterPreAction(boolean ready,
            boolean active) {
        return active && ready;
    }

    static boolean pauseOverlayConsumesRelease(boolean paused,
            boolean released) {
        return paused && released;
    }

    private int activeDealtPlayerCount() {
        long count = liveState.snapshot().players().stream()
                .filter(player -> player.active() && !player.spectator()
                && !player.exited())
                .count();
        if (count <= 0L) {
            throw new IllegalStateException(
                    "A canonical deal event has no active seated players");
        }
        return Math.toIntExact(count);
    }

    static float liveDealCadenceSeconds(int activePlayers,
            int speedPercent) {
        int players = Math.max(1, activePlayers);
        int pauseBase = Math.max(100, Math.round(
                CLASSIC_DEAL_BASE_MILLIS * (2f / players)));
        float speed = speedPercent / 100f;
        return Math.max(60, Math.round(pauseBase * speed)) / 1000f;
    }

    static float liveDealFlightSeconds(int activePlayers,
            int speedPercent) {
        int players = Math.max(1, activePlayers);
        int pauseBase = Math.max(100, Math.round(
                CLASSIC_DEAL_BASE_MILLIS * (2f / players)));
        int flightBase = Math.max(150, pauseBase);
        float speed = speedPercent / 100f;
        return Math.max(80, Math.round(flightBase * speed)) / 1000f;
    }

    static boolean shouldDismissAutoActionDialog(TableVisualEvent event,
            TableSnapshot snapshot) {
        if (!isActionableTurn(snapshot, snapshot.localNickname())) {
            return true;
        }
        return event instanceof TableVisualEvent.PlayerAction action
                && action.nickname().equals(snapshot.localNickname());
    }

    static int preActionAfterOpeningAutoCallSettings(int selection,
            boolean localTurn) {
        return localTurn ? selection : 0;
    }

    private boolean autoPreActionsVisible() {
        return autoButtons && liveState != null
                && liveState.preActionControlsActive();
    }

    private void updateFastAccessBar(float delta) {
        if (intro || startupIntroOnly || liveState == null || uiLayer != UI_NONE
                || activeDialog != null) {
            return;
        }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        float width = fastBarExpanded ? fastExpandedWidth() : FAST_BUTTON_SIZE;
        boolean inside = contains(pointer.x, pointer.y, FAST_BAR_X, FAST_BAR_Y,
                width, FAST_BUTTON_SIZE + 2f * FAST_BAR_PADDING);
        boolean overMenu = contains(pointer.x, pointer.y,
                FAST_BAR_X + FAST_BAR_PADDING, FAST_BAR_Y + FAST_BAR_PADDING,
                FAST_BUTTON_SIZE, FAST_BUTTON_SIZE);
        if (!fastBarExpanded && overMenu) {
            fastBarExpanded = true;
            fastBarOutsideSeconds = 0f;
            fastBarAlpha = 1f;
            return;
        }
        if (!fastBarExpanded) {
            fastBarAlpha = 1f;
            return;
        }
        if (inside) {
            fastBarOutsideSeconds = 0f;
            fastBarAlpha = 1f;
            return;
        }
        fastBarOutsideSeconds += delta;
        if (fastBarOutsideSeconds > FAST_BAR_HIDE_DELAY) {
            fastBarAlpha = MathUtils.clamp(1f
                    - (fastBarOutsideSeconds - FAST_BAR_HIDE_DELAY)
                    / FAST_BAR_FADE_SECONDS, 0f, 1f);
            if (fastBarAlpha <= 0f) {
                fastBarExpanded = false;
                fastBarOutsideSeconds = 0f;
                fastBarAlpha = 1f;
            }
        }
    }

    private float fastExpandedWidth() {
        int count = fastButtonCount();
        return 2f * FAST_BAR_PADDING
                + count * FAST_BUTTON_SIZE
                + (count - 1) * FAST_BUTTON_GAP;
    }

    private int fastButtonCount() {
        return tableHost ? 9 : 8;
    }

    private boolean fastButtonEnabled(int index) {
        return switch (visibleFastAccessActionAt(index)) {
            case SETTINGS, GAME_LOG, FULLSCREEN, EXIT -> true;
            case STOP -> tableHost;
            case CHAT -> canUseTableChat();
            case VOICE -> canUseTableVoice();
            case IMAGE -> canUseTableImages();
            case REBUY -> canToggleImmediateRebuy();
            case NONE -> false;
        };
    }

    static FastAccessAction fastAccessActionAt(int index) {
        return fastAccessActionAt(index, false);
    }

    static FastAccessAction fastAccessActionAt(int index, boolean host) {
        if (index < 0) return FastAccessAction.NONE;
        if (index <= 6) return FastAccessAction.values()[index];
        if (host && index == 7) return FastAccessAction.STOP;
        if (index == (host ? 8 : 7)) return FastAccessAction.EXIT;
        return FastAccessAction.NONE;
    }

    private FastAccessAction visibleFastAccessActionAt(int index) {
        return fastAccessActionAt(index, tableHost);
    }

    private int fastButtonResourceIndex(int index) {
        // A client skips the host-only stop resource, leaving EXIT last.
        return !tableHost && index == 7 ? 8 : index;
    }

    private boolean canToggleImmediateRebuy() {
        return immediateRebuyControlEnabled(tableRebuyAllowed);
    }

    private boolean canUseTableChat() {
        return tableChatControlEnabled(tableChat != null, liveState != null);
    }

    private boolean canUseTableImages() {
        return tableImageControlEnabled(tableChat != null,
                tablePreference("chat_images_ingame", true));
    }

    static boolean immediateRebuyControlEnabled(boolean rebuyAllowed) {
        return rebuyAllowed;
    }

    static boolean tableChatControlEnabled(boolean chatAvailable,
            boolean tableLive) {
        return chatAvailable && tableLive;
    }

    static boolean tableImageControlEnabled(boolean chatAvailable,
            boolean imagesEnabled) {
        return chatAvailable && imagesEnabled;
    }

    private int fastButtonAt(float x, float y) {
        if (!fastBarExpanded || !contains(x, y, FAST_BAR_X, FAST_BAR_Y,
                fastExpandedWidth(), FAST_BUTTON_SIZE + 2f * FAST_BAR_PADDING)) {
            return -1;
        }
        float firstX = FAST_BAR_X + FAST_BAR_PADDING;
        for (int index = 0; index < fastButtonCount(); index++) {
            float buttonX = firstX + index * (FAST_BUTTON_SIZE + FAST_BUTTON_GAP);
            if (contains(x, y, buttonX, FAST_BAR_Y + FAST_BAR_PADDING,
                    FAST_BUTTON_SIZE, FAST_BUTTON_SIZE)) {
                return index;
            }
        }
        return -1;
    }

    private boolean handleFastAccessClick(float x, float y) {
        if (micPointerHeld) {
            micPointerHeld = false;
            finishVoiceRecording(false);
            fastBarExpanded = false;
            return true;
        }
        if (!fastBarExpanded) {
            if (contains(x, y, FAST_BAR_X + FAST_BAR_PADDING,
                    FAST_BAR_Y + FAST_BAR_PADDING,
                    FAST_BUTTON_SIZE, FAST_BUTTON_SIZE)) {
                fastBarExpanded = true;
                fastBarOutsideSeconds = 0f;
                return true;
            }
            return false;
        }
        int button = fastButtonAt(x, y);
        if (button < 0) {
            return false;
        }
        if (!fastButtonEnabled(button)) {
            return true;
        }
        fastBarExpanded = false;
        switch (visibleFastAccessActionAt(button)) {
            case SETTINGS -> openSettingsSection(
                    GdxSettingsContract.Section.GAME);
            case CHAT -> openQuickChat();
            case IMAGE -> openTableImageGallery();
            case REBUY -> submit(new TableCommand.ToggleImmediateRebuy());
            case GAME_LOG -> {
                gameLogScroll = 0;
                openUiLayer(UI_GAME_LOG);
            }
            case FULLSCREEN -> toggleFullscreen();
            case STOP -> requestStopGame();
            case EXIT -> requestExit();
            case VOICE, NONE -> {
                // Voice is press-and-hold and is handled before button release.
            }
        }
        return true;
    }

    private void openQuickChat() {
        if (!canUseTableChat()) return;
        chatError = "";
        chatImageMode = false;
        emojiPickerOpen = false;
        quickChatHistoryIndex = quickChatHistory.size();
        quickChatPendingDraft = chatDraft;
        quickChatScroll = 0f;
        quickChatScrollMaximum = 0f;
        quickChatMessageCount = visibleTableChatMessages().size();
        chatEdit.focus("tableChat", chatDraft);
        chatEdit.end(chatDraft, false);
        openUiLayer(UI_CHAT);
    }

    private void openTableImageGallery() {
        if (!canUseTableImages() || !canUseTableChat()) return;
        chatError = "";
        chatImageMode = true;
        emojiPickerOpen = false;
        tableImageHistory = preferences == null ? List.of()
                : GdxChatImageHistory.read(preferences.properties());
        tableGalleryMedia.refresh(tableImageHistory, 8, "table-history");
        chatEdit.focus("tableChat", chatDraft);
        chatEdit.end(chatDraft, false);
        openUiLayer(UI_CHAT);
    }

    private void closeTableChat() {
        uiLayer = UI_NONE;
        chatImageMode = false;
        emojiPickerOpen = false;
        chatEditMenuOpen = false;
        quickChatPendingDraft = chatDraft;
    }

    static boolean isQuickChatToggleCharacter(char character,
            boolean controlDown) {
        return character == 'º' && !controlDown;
    }

    private boolean canUseTableVoice() {
        if (tableChat == null || intro || startupIntroOnly || liveState == null) {
            return false;
        }
        Properties persisted = preferences == null
                ? new Properties() : preferences.properties();
        return globalVoiceMessagesEnabled()
                && booleanPreference(persisted, "audio_mic_enabled", true)
                && !booleanPreference(persisted,
                        "audio_block_voice_messages", false);
    }

    private void beginVoiceRecording() {
        if (!canUseTableVoice() || voiceRecorder != null || voiceStopping) return;
        GdxVoiceRecorder recorder = new GdxVoiceRecorder(
                tableSettingsProperties());
        voiceRecorder = recorder;
        voiceOpening = true;
        voiceLive = false;
        voiceStatus = uppercase(gameText.translate("gdx.lobby.voice_opening"));
        voiceStatusAt = totalTime;
        stopShuffleSound();
        backgroundMusic.pause();
        shuffleSound.stop();
        dealSound.stop();
        uncoverSound.stop();
        checkSound.stop();
        callSound.stop();
        betSound.stop();
        foldSound.stop();
        allInSound.stop();
        CompletableFuture.supplyAsync(() -> recorder.start(
                () -> Gdx.app.postRunnable(() -> {
                    if (disposed || voiceRecorder != recorder) return;
                    voiceOpening = false;
                    voiceLive = true;
                    voiceLiveAt = totalTime;
                    voiceStatus = uppercase(gameText.translate(
                            "gdx.table.chat.voice_recording"));
                    voiceStatusAt = totalTime;
                }),
                () -> Gdx.app.postRunnable(() -> {
                    if (!disposed && voiceRecorder == recorder) {
                        finishVoiceRecording(false);
                    }
                }))).whenComplete((outcome, failure) ->
                        Gdx.app.postRunnable(() -> {
                            if (disposed || voiceRecorder != recorder) return;
                            if (failure != null
                                    || outcome != GdxVoiceRecorder.Outcome.RECORDING) {
                                voiceOpening = false;
                                voiceLive = false;
                                voiceRecorder = null;
                                voiceStatus = uppercase(gameText.translate(
                                        "gdx.lobby.voice_unavailable"));
                                voiceStatusAt = totalTime;
                                resumeBackgroundAfterVoice();
                            }
                        }));
    }

    private void finishVoiceRecording(boolean discard) {
        GdxVoiceRecorder recorder = voiceRecorder;
        if (recorder == null || voiceStopping) return;
        voiceStopping = true;
        voiceOpening = false;
        voiceLive = false;
        voiceStatus = uppercase(gameText.translate(discard
                ? "gdx.lobby.voice_cancelled"
                : "gdx.lobby.voice_processing"));
        voiceStatusAt = totalTime;
        if (discard) recorder.abort();
        CompletableFuture.supplyAsync(() -> discard
                ? null : recorder.stopAndEncode()).whenComplete((wav, failure) ->
                        Gdx.app.postRunnable(() -> {
                            if (voiceRecorder == recorder) voiceRecorder = null;
                            voiceStopping = false;
                            if (disposed) return;
                            resumeBackgroundAfterVoice();
                            if (discard) return;
                            if (failure != null || wav == null) {
                                voiceStatus = voiceFailureLabel(
                                        recorder.outcome(), gameText);
                                voiceStatusAt = totalTime;
                                return;
                            }
                            voiceStatus = uppercase(gameText.translate(
                                    "gdx.lobby.voice_sending"));
                            voiceStatusAt = totalTime;
                            tableChat.sendVoice(wav).whenComplete((ignored, sendFailure) ->
                                    Gdx.app.postRunnable(() -> {
                                        voiceStatus = uppercase(gameText.translate(
                                                sendFailure == null
                                                        ? "gdx.lobby.voice_sent"
                                                        : "gdx.table.chat.send_failed"));
                                        voiceStatusAt = totalTime;
                                    }));
                        }));
    }

    private void cancelVoiceRecording() {
        micPointerHeld = false;
        finishVoiceRecording(true);
    }

    private void resumeBackgroundAfterVoice() {
        if (!disposed && musicEnabled()) {
            backgroundMusic.play();
        }
    }

    private static String voiceFailureLabel(GdxVoiceRecorder.Outcome outcome,
            GdxGameText gameText) {
        String key = switch (outcome) {
            case SILENT -> "gdx.table.chat.voice_silent";
            case NO_LINE -> "gdx.lobby.voice_unavailable";
            case LOST -> "gdx.table.chat.voice_lost";
            default -> "gdx.lobby.voice_discarded";
        };
        return gameText.translate(key).toUpperCase(
                Locale.forLanguageTag(gameText.language()));
    }

    private void updatePointerButtonTransitions() {
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        leftButtonReleasedThisFrame = primaryPointer.update(down);
    }

    private void toggleUserLights() {
        userLightsOff = !userLightsOff;
        playSwitchSound(!userLightsOff);
    }

    private void adjustLiveBet(int direction) {
        ActionControlState controls = liveState.actionControls();
        if (controls.raiseAction() == ActionControlState.RaiseAction.DISABLED) return;
        armedHudTarget = 0;
        liveBetAmount = MathUtils.clamp(
                liveBetAmount + direction * controls.raiseStep(),
                controls.raiseMinimum(), controls.raiseMaximum());
    }

    /** Canonical fold activation shared by pointer, shortcut and scenarios. */
    boolean activateFoldAction() {
        if (!hasActiveLocalTurn()) return false;
        if (!liveState.actionControls().foldEnabled()) return false;
        submitConfirmedAction(1, new TableCommand.Fold());
        return true;
    }

    /** Canonical check/call activation shared by pointer, shortcut and scenarios. */
    boolean activateCheckOrCallAction() {
        if (!hasActiveLocalTurn()) return false;
        if (liveState.actionControls().callAction()
                == ActionControlState.CallAction.DISABLED) return false;
        submitConfirmedAction(2, new TableCommand.CheckOrCall());
        return true;
    }

    /** Canonical bet/raise activation shared by pointer, shortcut and scenarios. */
    boolean activateBetAction() {
        if (!hasActiveLocalTurn()) return false;
        ActionControlState controls = liveState.actionControls();
        if (controls.raiseAction()
                == ActionControlState.RaiseAction.DISABLED) return false;
        double amount = liveBetAmount;
        if (amount < controls.raiseMinimum()
                || amount > controls.raiseMaximum()) {
            amount = controls.raiseAmount();
        }
        submitConfirmedAction(5, new TableCommand.Bet(amount));
        return true;
    }

    private boolean hasActiveLocalTurn() {
        if (liveState == null) return false;
        String localNickname = liveState.snapshot().localNickname();
        return isActionableTurn(liveState.snapshot(), localNickname,
                terminationRequested || isClientTransportReconnecting()
                || isLiveReconnectingPlayer(localNickname));
    }

    /**
     * A turn owner is actionable only while its authoritative player snapshot
     * still represents a seated participant in the hand.  Network/event order
     * can briefly leave the previous nickname in currentTurnNickname after a
     * fold, timeout, exit or spectator transition; GDX must neither illuminate
     * that stale owner nor accept commands for it.
     */
    static boolean isActionableTurn(TableSnapshot snapshot, String nickname) {
        if (snapshot == null || nickname == null || nickname.isBlank()
                || snapshot.paused()
                || !nickname.equals(snapshot.currentTurnNickname())) {
            return false;
        }
        return snapshot.players().stream()
                .filter(player -> nickname.equals(player.nickname()))
                .anyMatch(player -> player.active()
                && !player.spectator()
                && !player.exited()
                && !player.timedOut());
    }

    static boolean isActionableTurn(TableSnapshot snapshot, String nickname,
            boolean frontendUnavailable) {
        return !frontendUnavailable && isActionableTurn(snapshot, nickname);
    }

    /** Canonical ALL-IN activation shared by pointer, shortcut and tests. */
    boolean activateAllInAction() {
        if (!hasActiveLocalTurn()) return false;
        ActionControlState controls = liveState.actionControls();
        if (controls.showCards() || !controls.allInEnabled()) return false;
        submitConfirmedAction(6, new TableCommand.AllIn());
        return true;
    }

    /** Canonical pause activation shared by overlay, button, shortcut and tests. */
    boolean togglePauseAction() {
        if (liveState == null) return false;
        submit(new TableCommand.TogglePause());
        return true;
    }

    /** Swing's CONFIRMAR ACCIONES contract: first press arms, second executes. */
    private void submitConfirmedAction(int target, TableCommand command) {
        if (activeDialog != null && activeDialog.isAutoAction()) {
            activeDialog.dismiss();
        }
        if (!requiresActionConfirmation(target, confirmActions, isTestMode())) {
            armedHudTarget = 0;
            submit(command);
            return;
        }
        if (armedHudTarget == target) {
            armedHudTarget = 0;
            submit(command);
        } else {
            armedHudTarget = target;
        }
    }

    private void playPlayerActionSoundForAcceptedEvent(
            TableVisualEvent.PlayerAction action, int color) {
        if (action.kind() == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
            playAllInActionSoundOnce(action.nickname());
            return;
        }
        playPlayerActionSound(action.kind(), color);
    }

    private void playAllInActionSoundOnce(String nickname) {
        if (!liveAllInSoundEnabled() || nickname == null
                || nickname.isBlank() || !allInActionSoundsPlayed.add(nickname)) {
            return;
        }
        play(allInSound, 0.58f, 1f);
    }

    /**
     * Swing protects ALL-IN unconditionally with two presses.  The general
     * "confirm every action" preference extends that protection to the other
     * poker buttons; it never weakens the ALL-IN safeguard.
     */
    static boolean requiresActionConfirmation(int target,
            boolean confirmEveryAction, boolean testMode) {
        return !testMode && (target == 6 || confirmEveryAction);
    }

    static int hudTarget(float x, float y, float worldWidth) {
        float hudWidth = Math.min(1110f, worldWidth - 620f);
        float hudX = worldWidth / 2f - hudWidth / 2f;
        float actionY = LOCAL_HUD_Y + 24f;
        float actionHeight = 80f;
        float gap = 12f;
        float foldX = hudX + 230f + gap;
        float checkX = foldX + 150f + gap;
        float spinnerX = checkX + 200f + gap;
        float betX = spinnerX + 145f + gap;
        float allInX = betX + 175f + gap;
        if (contains(x, y, foldX, actionY, 150f, actionHeight)) return 1;
        if (contains(x, y, checkX, actionY, 200f, actionHeight)) return 2;
        if (contains(x, y, spinnerX, actionY, 43f, actionHeight)) return 3;
        if (contains(x, y, spinnerX + 102f, actionY, 43f, actionHeight)) return 4;
        if (contains(x, y, betX, actionY, 175f, actionHeight)) return 5;
        if (contains(x, y, allInX, actionY, 150f, actionHeight)) return 6;
        return 0;
    }

    private void submit(TableCommand command) {
        commands.submit(command);
    }

    void submitExternal(TableCommand command) {
        submit(Objects.requireNonNull(command, "command"));
    }

    /**
     * Keeps the GDX scene in an explicit terminal transition until the dealer
     * publishes {@link TableVisualEvent.CloseTable}. The command remains the
     * sole authority: this state neither closes the window nor advances the
     * game and therefore cannot overtake network testament or settlement.
     */
    private void submitTermination(TableCommand command, boolean recoverable) {
        if (terminationRequested) return;
        terminationRequested = true;
        recoverableTerminationRequested = recoverable;
        terminationConfirmation = null;
        armedHudTarget = 0;
        uiLayer = UI_NONE;
        // The confirmation remains active until the next render tick. Remove
        // it and every queued modal immediately so a stale dialog cannot cover
        // the terminal acknowledgement or accept a duplicate command while
        // the authoritative dealer shutdown is in progress.
        if (activeDialog != null && !activeDialog.complete()) {
            activeDialog.dismiss();
        }
        activeDialog = null;
        while (!dialogQueue.isEmpty()) {
            GdxTableDialog queued = dialogQueue.removeFirst();
            if (!queued.complete()) queued.dismiss();
        }
        dialogAmountEdit.blur();
        try {
            submit(command);
        } catch (RuntimeException failure) {
            terminationRequested = false;
            recoverableTerminationRequested = false;
            String detail = failure.getMessage();
            showDialog(new GdxTableDialog(GdxTableDialog.Kind.ERROR,
                    uppercase(gameText.translate(
                            "gdx.termination_failed"))
                    + (detail == null || detail.isBlank()
                            ? "" : "\n" + detail),
                    com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.EXIT,
                    820, 0));
        }
    }

    private void toggleFullscreen() {
        GdxDisplayModeController.toggle(GdxWindowMode.configured(
                tableSettingsProperties()));
    }

    private void adjustTableWindowMode(int direction) {
        Properties properties = tableSettingsProperties();
        String previous = properties.getProperty(GdxWindowMode.PREFERENCE_KEY);
        GdxWindowMode next = GdxWindowMode.adjust(properties, direction);
        if (!GdxDisplayModeController.apply(next)) {
            if (previous == null) {
                properties.remove(GdxWindowMode.PREFERENCE_KEY);
            } else {
                properties.setProperty(GdxWindowMode.PREFERENCE_KEY, previous);
            }
            return;
        }
    }

    private String tableWindowModeSettingLabel() {
        return GdxWindowMode.configured(tableSettingsProperties())
                .label(gameText) + "  ·  "
                + uppercase(gameText.translate("gdx.settings.value.active"));
    }

    private String tableMsaaSettingLabel() {
        if (presentationSettings == null) {
            return uppercase(gameText.translate(
                    "gdx.settings.value.unavailable"));
        }
        return GdxSettingsContract.msaaStatusLabel(
                presentationSettings.requestedMsaaSamples(),
                presentationSettings.actualMsaaSamples(), gameText);
    }

    private void updateStars(float delta) {
        float width = viewport == null ? BASE_WIDTH : viewport.getWorldWidth();
        float height = viewport == null ? BASE_HEIGHT : viewport.getWorldHeight();
        for (Star star : stars) {
            star.y += star.speed * delta;
            star.x += MathUtils.sin(totalTime * 0.35f + star.phase) * delta * 3f;
            if (star.y > height + 8f) {
                star.y = -8f;
                star.x = MathUtils.random(0f, width);
            }
        }
    }

    private void drawBackground() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(feltTexture, 0f, 0f, width, height,
                0f, 0f, width / feltTexture.getWidth(), height / feltTexture.getHeight());
        batch.setColor(Color.WHITE);
        batch.end();

        if (intro) {
            float backdropAlpha = (1f - introLogoDockProgress(sceneTime))
                    * (1f - introLightProgress(sceneTime));
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.rect(0, 0, width, height,
                    scaledAlpha(FELT_SHADE_BOTTOM, backdropAlpha),
                    scaledAlpha(FELT_SHADE_BOTTOM, backdropAlpha),
                    scaledAlpha(FELT_SHADE_TOP, backdropAlpha),
                    scaledAlpha(FELT_SHADE_TOP, backdropAlpha));
            shapes.end();

            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (Star star : stars) {
                float pulse = 0.32f + 0.28f * MathUtils.sin(totalTime * 1.8f + star.phase);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                        pulse * backdropAlpha);
                shapes.circle(star.x, star.y, star.size, 10);
            }
            shapes.end();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void drawIntro() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float appear = Interpolation.smoother.apply(
                MathUtils.clamp(sceneTime / 0.82f, 0f, 1f));
        float dock = introLogoDockProgress(sceneTime);
        float cardsAppear = introCardAppearProgress(sceneTime);
        float pulseTime = Math.max(0f, sceneTime - 2.28f);
        float pulse = pulseTime <= 0f ? 0f
                : (float) Math.pow(Math.max(0f,
                        MathUtils.sin(pulseTime * MathUtils.PI2 * 1.55f)), 3d)
                * MathUtils.clamp(1f - pulseTime / 2.25f, 0f, 1f);
        float centerLogoWidth = Math.min(760f, width * 0.48f);
        float centerLogoHeight = centerLogoWidth * logo.getHeight() / logo.getWidth();
        float centerLogoX = width / 2f;
        float centerLogoY = height / 2f + 85f;

        batch.begin();
        float logoScale = (0.90f + appear * 0.10f) * (1f + pulse * 0.075f);
        float startWidth = centerLogoWidth * logoScale;
        float startHeight = centerLogoHeight * logoScale;
        float dockWidth = GdxFrontendScreen.MENU_LOGO_WIDTH;
        float dockHeight = dockWidth * logo.getHeight() / logo.getWidth();
        float logoWidth = MathUtils.lerp(startWidth, dockWidth, dock);
        float logoHeight = MathUtils.lerp(startHeight, dockHeight, dock);
        float logoX = MathUtils.lerp(centerLogoX,
                GdxFrontendScreen.MENU_LOGO_X + dockWidth / 2f, dock);
        float logoY = MathUtils.lerp(centerLogoY,
                BASE_HEIGHT - GdxFrontendScreen.MENU_LOGO_TOP
                        - dockHeight / 2f, dock);
        batch.setColor(1f, 1f, 1f, appear);
        batch.draw(logo, logoX - logoWidth / 2f,
                logoY - logoHeight / 2f, logoWidth, logoHeight);

        for (int card = 0; card < INTRO_CARD_COUNT; card++) {
            drawIntroCard(card, width, height, centerLogoX, centerLogoY,
                    centerLogoWidth, centerLogoHeight,
                    cardsAppear * (1f - dock));
        }
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    static float introLogoDockProgress(float timeSeconds) {
        return Interpolation.smoother.apply(MathUtils.clamp(
                (timeSeconds - INTRO_LOGO_DOCK_START)
                        / INTRO_LOGO_DOCK_SECONDS, 0f, 1f));
    }

    static float introLightProgress(float timeSeconds) {
        return Interpolation.smoother.apply(MathUtils.clamp(
                (timeSeconds - INTRO_LIGHT_SWITCH_TIME)
                        / INTRO_LIGHT_FADE_SECONDS, 0f, 1f));
    }

    static float introCardAppearProgress(float timeSeconds) {
        return Interpolation.smoother.apply(MathUtils.clamp(
                timeSeconds / INTRO_CARD_APPEAR_SECONDS, 0f, 1f));
    }

    static boolean introLightSwitchReached(float timeSeconds) {
        return timeSeconds >= INTRO_LIGHT_SWITCH_TIME;
    }

    private void drawIntroBlackout() {
        float darkness = INTRO_DARKNESS_ALPHA
                * (1f - introLightProgress(sceneTime));
        if (darkness <= 0f) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, darkness);
        shapes.rect(0f, 0f, viewport.getWorldWidth(), viewport.getWorldHeight());
        shapes.end();
    }

    private static Color scaledAlpha(Color color, float alphaScale) {
        return new Color(color.r, color.g, color.b, color.a * alphaScale);
    }

    private void drawIntroCard(int card, float width, float height,
            float logoX, float logoY, float logoWidth, float logoHeight,
            float transitionAlpha) {
        Texture cardBack = defaultCardBack;
        Texture cardFace = introCardFaces[card];
        float cardW = MathUtils.clamp(width * 0.082f, 118f, 192f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float targetX = logoX + (introHash(card, 2f) - 0.5f)
                * logoWidth * 0.94f;
        float targetY = logoY + (introHash(card, 3f) - 0.5f)
                * Math.max(logoHeight * 0.90f, cardH * 1.32f);
        float x = targetX;
        float y = targetY;
        float targetRotation = (introHash(card, 6f) - 0.5f) * 72f;
        float rotation = targetRotation;
        float scale = 0.94f + introHash(card, 5f) * 0.12f;
        float alpha = transitionAlpha;

        float clearDelay = INTRO_CARD_CLEAR_START
                + introHash(card, 9f) * 0.34f;
        float clearRaw = MathUtils.clamp((sceneTime - clearDelay) / 0.78f, 0f, 1f);
        if (clearRaw > 0f) {
            float clear = Interpolation.pow2In.apply(clearRaw);
            float exitAngle = MathUtils.PI2 * introHash(card, 4f);
            float awayX = MathUtils.cos(exitAngle);
            float awayY = MathUtils.sin(exitAngle);
            float tangentX = -awayY;
            float tangentY = awayX;
            float sweep = Math.max(width, height) * (0.78f + introHash(card, 8f) * 0.28f);
            float arc = MathUtils.sin(clearRaw * MathUtils.PI)
                    * (introHash(card, 1f) - 0.5f) * 240f;
            x += awayX * sweep * clear + tangentX * arc;
            y += awayY * sweep * clear + tangentY * arc;
            rotation += (card % 2 == 0 ? 1f : -1f) * clear * 520f;
            scale *= 1f - clear * 0.12f;
            alpha *= 1f - Interpolation.pow2In.apply(
                    MathUtils.clamp((clearRaw - 0.72f) / 0.28f, 0f, 1f));
        }

        float canvasW = cardW * 1.5f;
        float canvasH = cardH * 1.5f;
        // Startup has deliberately no table session.  Its cards are a purely
        // visual composition and therefore use the explicit bundled back;
        // never route them through the live-table deck selection contract.
        useIntroPerspectiveCardShader(cardFace, MathUtils.PI, cardH / cardW);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(cardBack, x - canvasW / 2f, y - canvasH / 2f,
                canvasW / 2f, canvasH / 2f, canvasW, canvasH,
                scale, scale, rotation,
                0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
    }

    static String introCardCode(int card) {
        return INTRO_CARD_RANKS[card % INTRO_CARD_RANKS.length] + "_"
                + INTRO_CARD_SUITS[card / INTRO_CARD_RANKS.length];
    }

    private static float introHash(int index, float salt) {
        float value = MathUtils.sin((index + 1f) * 12.9898f
                + salt * 78.233f) * 43758.547f;
        return value - (float) Math.floor(value);
    }

    private void drawTableScene() {
        Objects.requireNonNull(liveState,
                "A table scene requires authoritative live state");
        Texture cardBack = activeCardBack();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float tableCx = width / 2f;
        float tableCy = height * 0.52f;
        float tableW = Math.min(1510f, width * 0.78f);
        tableCenterX = tableCx;
        tableCenterY = tableCy;
        float boardCardW = Math.min(COMMUNITY_CARD_MAX_WIDTH, tableW / 10f);
        float boardCardH = boardCardW * cardBack.getHeight() / cardBack.getWidth();
        // The pot owns the central axis, clearly above the upper edge of the
        // community row. It is never painted on top of a card.
        potCenterX = tableCx;
        float boardTopY = tableCy + boardCardH * 0.64f;
        potCenterY = boardTopY + POT_BOARD_GAP + POT_PANEL_HEIGHT / 2f;

        updateSeatPositions(width, height);
        updateLiveShowdownHover(width);
        drawTableBranding(height);
        drawChipTrails(potCenterX, potCenterY);
        // Hidden cards and completed showdown hands live behind their PlayerPod.
        // A revealing card temporarily moves to the foreground while airborne,
        // then lands at the exact same coordinates behind its owner's HUD.
        drawHoleCards(false);
        drawSeats();
        drawCardsAndPot(tableCx, tableCy, tableW);
        drawHoleCards(true);
        // The physical chip flies above the table contents; only its light
        // trail stays below. This preserves a believable foreground collision.
        drawFlyingChips(potCenterX, potCenterY);
        drawCallCostOverlay(tableCx, tableCy, tableW);
        drawHandOverlay();
        drawShowdownOverlay();
        drawLocalHud(width, height);
        // Swing puts chat_notify_label in the player's topmost layered-pane
        // band.  Draw notices only after every remote and local HUD; otherwise
        // the local HUD repaints over talk.png and makes an own voice note look
        // as if it never produced its speaking indicator.
        drawSeatChatNotices();
        drawSilentChatNotice();
        drawFastAccessBar();
        drawVoiceRecordingOverlay(width, height);
        drawAvatarZoomOverlay(width, height);
        if (tablePreference("gdx_show_fps", true)) {
            drawFpsCounter(width, height);
        }

    }

    private void drawFastAccessBar() {
        float alpha = fastBarExpanded ? fastBarAlpha : 1f;
        float panelW = fastBarExpanded ? fastExpandedWidth()
                : FAST_BUTTON_SIZE + 2f * FAST_BAR_PADDING;
        float panelH = FAST_BUTTON_SIZE + 2f * FAST_BAR_PADDING;
        int hovered = fastButtonAt(pointer.x, pointer.y);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.42f * alpha);
        roundedRect(FAST_BAR_X + 4f, FAST_BAR_Y - 4f,
                panelW, panelH, 13f);
        shapes.setColor(SEAT_RIM.r, SEAT_RIM.g, SEAT_RIM.b,
                0.76f * alpha);
        roundedRect(FAST_BAR_X - 1.5f, FAST_BAR_Y - 1.5f,
                panelW + 3f, panelH + 3f, 13f);
        shapes.setColor(0.006f, 0.028f, 0.040f, 0.88f * alpha);
        roundedRect(FAST_BAR_X, FAST_BAR_Y, panelW, panelH, 12f);
        int count = fastBarExpanded ? fastButtonCount() : 1;
        for (int index = 0; index < count; index++) {
            float buttonX = FAST_BAR_X + FAST_BAR_PADDING
                    + index * (FAST_BUTTON_SIZE + FAST_BUTTON_GAP);
            boolean enabled = !fastBarExpanded || fastButtonEnabled(index);
            boolean hover = fastBarExpanded && hovered == index;
            Color accent = hover && enabled
                    ? visibleFastAccessActionAt(index) == FastAccessAction.EXIT
                            ? FOLD_RED : CYAN
                    : BUTTON_LINE;
            shapes.setColor(accent.r, accent.g, accent.b,
                    (hover ? 0.54f : 0.24f) * alpha);
            roundedRect(buttonX, FAST_BAR_Y + FAST_BAR_PADDING,
                    FAST_BUTTON_SIZE, FAST_BUTTON_SIZE, 9f);
            shapes.setColor(1f, 1f, 1f,
                    (hover && enabled ? 0.17f : 0.07f) * alpha);
            roundedRect(buttonX + 4f,
                    FAST_BAR_Y + FAST_BAR_PADDING + FAST_BUTTON_SIZE - 11f,
                    FAST_BUTTON_SIZE - 8f, 7f, 3f);
        }
        if (fastBarExpanded && hovered >= 0) {
            String hoveredLabel = fastButtonLabel(hovered);
            float tipW = Math.max(180f,
                    Math.min(260f, hoveredLabel.length() * 13f));
            float tipX = MathUtils.clamp(pointer.x - tipW / 2f, 8f,
                    viewport.getWorldWidth() - tipW - 8f);
            shapes.setColor(0.005f, 0.020f, 0.032f, 0.96f * alpha);
            roundedRect(tipX, FAST_BAR_Y + panelH + 8f,
                    tipW, 36f, 7f);
            shapes.setColor(fastButtonEnabled(hovered)
                    ? CYAN : Color.GRAY);
            shapes.rect(tipX + 8f, FAST_BAR_Y + panelH + 41f,
                    tipW - 16f, 2f);
        }
        shapes.end();

        batch.begin();
        if (fastBarExpanded) {
            for (int index = 0; index < fastButtonCount(); index++) {
                float buttonX = FAST_BAR_X + FAST_BAR_PADDING
                        + index * (FAST_BUTTON_SIZE + FAST_BUTTON_GAP);
                float tint = fastButtonEnabled(index) ? 1f : 0.36f;
                batch.setColor(tint, tint, tint, alpha);
                batch.draw(fastButtonIcons[fastButtonResourceIndex(index)],
                        buttonX + 5f,
                        FAST_BAR_Y + FAST_BAR_PADDING + 5f,
                        FAST_BUTTON_SIZE - 10f, FAST_BUTTON_SIZE - 10f);
            }
            if (hovered >= 0) {
                String hoveredLabel = fastButtonLabel(hovered);
                float tipW = Math.max(180f,
                        Math.min(260f, hoveredLabel.length() * 13f));
                float tipX = MathUtils.clamp(pointer.x - tipW / 2f, 8f,
                        viewport.getWorldWidth() - tipW - 8f);
                drawFittedCenteredInBox(smallFont,
                        fastButtonEnabled(hovered)
                                ? hoveredLabel
                                : hoveredLabel + " · " + uppercase(
                                        gameText.translate(
                                                "gdx.quick.unavailable")),
                        tipX + 8f, FAST_BAR_Y + panelH + 10f,
                        tipW - 16f, 30f,
                        fastButtonEnabled(hovered) ? Color.WHITE : Color.GRAY,
                        alpha);
            }
        } else {
            batch.setColor(Color.WHITE);
            batch.draw(fastMenuIcon,
                    FAST_BAR_X + FAST_BAR_PADDING + 5f,
                    FAST_BAR_Y + FAST_BAR_PADDING + 5f,
                    FAST_BUTTON_SIZE - 10f, FAST_BUTTON_SIZE - 10f);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void updateVoiceRecording() {
        if (voiceLive && totalTime - voiceLiveAt >= VOICE_RECORD_MAX_SECONDS) {
            micPointerHeld = false;
            finishVoiceRecording(false);
        }
        if (!voiceOpening && !voiceLive && !voiceStopping
                && !voiceStatus.isEmpty()
                && totalTime - voiceStatusAt > 2.8f) {
            voiceStatus = "";
        }
    }

    private String fastButtonLabel(int index) {
        return uppercase(gameText.translate(
                FAST_BUTTON_TEXT_KEYS[fastButtonResourceIndex(index)]));
    }

    private void drawVoiceRecordingOverlay(float width, float height) {
        if (voiceStatus.isEmpty()) return;
        boolean active = voiceOpening || voiceLive || voiceStopping;
        float panelW = 620f;
        float panelH = 82f;
        float x = (width - panelW) / 2f;
        float y = Math.max(LOCAL_HUD_SAFE_TOP + 12f, height * 0.18f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.45f);
        roundedRect(x + 5f, y - 5f, panelW, panelH, 16f);
        shapes.setColor(0.008f, 0.035f, 0.055f, 0.95f);
        roundedRect(x, y, panelW, panelH, 15f);
        shapes.setColor(active ? new Color(0xe53935ff) : BUTTON_LINE);
        roundedRect(x + 18f, y + 20f, 42f, 42f, 21f);
        if (voiceLive) {
            float elapsed = MathUtils.clamp(totalTime - voiceLiveAt, 0f,
                    VOICE_RECORD_MAX_SECONDS);
            shapes.setColor(0.17f, 0.92f, 0.62f, 0.92f);
            roundedRect(x + 82f, y + 12f,
                    (panelW - 106f) * (1f - elapsed / VOICE_RECORD_MAX_SECONDS),
                    7f, 3f);
        }
        shapes.end();
        batch.begin();
        drawFittedCenteredInBox(uiFont, voiceStatus, x + 78f, y + 25f,
                panelW - 102f, 38f, active ? Color.WHITE : POT_GOLD, 1f);
        if (voiceLive) {
            int remaining = Math.max(0, (int) Math.ceil(
                    VOICE_RECORD_MAX_SECONDS - (totalTime - voiceLiveAt)));
            drawFittedCenteredInBox(smallFont, remaining + " s", x + 10f,
                    y + 24f, 58f, 30f, Color.WHITE, 1f);
        }
        batch.end();
    }

    private void updateLiveShowdownHover(float worldWidth) {
        liveShowdownHoverNickname = null;
        if (liveState == null || uiLayer != UI_NONE
                || !tablePreference("resaltar_jugada_showdown", true)
                || !liveState.hasShowdownHighlights()) {
            return;
        }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        for (Seat seat : seats) {
            TableVisualEvent.ShowdownHighlight highlight
                    = liveState.showdownHighlight(seat.name);
            if (highlight == null || !highlight.enabled()) {
                continue;
            }
            TableSnapshot.PlayerSnapshot player = livePlayer(seat);
            if (player == null || player.spectator()
                    || player.handName().isBlank()
                    && liveState.resolvedHandName(player.nickname()).isBlank()) {
                continue;
            }
            boolean hovering;
            if (seat.index == 0) {
                float hudWidth = Math.min(1110f, worldWidth - 620f);
                float hudX = worldWidth / 2f - hudWidth / 2f;
                hovering = contains(pointer.x, pointer.y,
                        hudX + 14f, LOCAL_HUD_Y + 37f,
                        202f, 24f);
            } else {
                hovering = contains(pointer.x, pointer.y,
                        seat.podX + 7f, seat.podY + 7f,
                        PLAYER_POD_WIDTH - 14f, 44f);
            }
            if (hovering) {
                liveShowdownHoverNickname = seat.name;
                return;
            }
        }
    }

    private void drawTableBranding(float height) {
        batch.begin();
        batch.setColor(Color.WHITE);
        float logoWidth = 235f;
        float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
        batch.draw(logo, 42f, height - 32f - logoHeight,
                logoWidth, logoHeight);
        batch.end();
    }

    private void updateSeatPositions(float width, float height) {
        // Same visual language as CoronaPoker's DynamicTablePanel: seats are
        // distributed around the felt, not arranged around a casino oval. Every
        // rival is one fixed unit: avatar left, private cards right, HUD below.
        for (int i = 0; i < seats.length; i++) {
            float anchorX = seatAnchor(i, 0) * width;
            seats[i].y = seatAnchor(i, 1) * height;
            if (i == 0) {
                seats[i].x = anchorX;
                seats[i].stackX = seats[i].x + 67f;
                // The lowest chip pixel must remain above the complete HUD,
                // including its frame and shared turn bar.
                seats[i].stackY = Math.max(seats[i].y - 7f,
                        LOCAL_HUD_SAFE_TOP + 14f);
                // Clear the chip stack drawn immediately to the avatar's
                // right. The role puck belongs outside that stack, not over it.
                seats[i].positionX = seats[i].x + AVATAR_OUTER_RADIUS
                        + POSITION_CHIP_SIZE / 2f + 24f;
                seats[i].positionY = seats[i].y + 8f;
            } else {
                /*
                 * Keep the complete rival unit together. Adaptive layouts for
                 * 7/8/9 players deliberately place two seats low; clamping only
                 * their cards to the local-HUD safe lane detached the hand from
                 * its avatar/pod and made one card appear to float over another
                 * seat. Raise the whole unit by the same constraint instead.
                 */
                float cardAspect = activeCardBack().getHeight()
                        / (float) activeCardBack().getWidth();
                seats[i].y = adjustedRivalSeatY(seats[i].y, height,
                        cardAspect);
                float podCenterX = centeredRivalX(anchorX / width, width);
                seats[i].podX = podCenterX - PLAYER_POD_WIDTH / 2f;
                // All rival seats use the same composition: avatar on the left,
                // private cards on its right and the information panel below.
                seats[i].x = seats[i].podX + AVATAR_OUTER_RADIUS;
                seats[i].podY = MathUtils.clamp(
                        seats[i].y - PLAYER_POD_HEIGHT - 42f, 8f,
                        height - PLAYER_POD_HEIGHT - 8f);
                seats[i].stackX = seats[i].podX + 36f;
                seats[i].stackY = seats[i].podY + 68f;
                // Every positional puck lands at the same semantic anchor: to
                // the avatar's right and above the PlayerPod's upper-right
                // corner. Its complete disc clears the text box.
                seats[i].positionX = MathUtils.clamp(
                        seats[i].podX + PLAYER_POD_WIDTH - 26f,
                        POSITION_CHIP_SIZE / 2f + 4f,
                        width - POSITION_CHIP_SIZE / 2f - 4f);
                seats[i].positionY = seats[i].podY + PLAYER_POD_HEIGHT
                        + POSITION_CHIP_SIZE / 2f + POSITION_CHIP_HUD_GAP;
            }
        }
        int dealerIndex = dealerSeat();
        Seat dealer = dealerIndex < 0 ? null : seats[dealerIndex];
        // Cards originate under the dealer avatar itself. There is no artificial
        // shoe/deck widget on the felt. Before the authoritative position event
        // arrives, keep the source neutral instead of inventing a seat.
        dealerSourceX = dealer == null ? width / 2f : dealer.x;
        dealerSourceY = dealer == null ? height / 2f : dealer.y;
    }

    private float seatAnchor(int seat, int component) {
        int visibleCount = livePlayerCount();
        int layoutCount = Math.max(2, visibleCount);
        return seat < visibleCount
                ? SEAT_LAYOUTS[layoutCount][seat][component] : 0.5f;
    }

    private float seatPresenceAlpha(int seat) {
        return seat < livePlayerCount() ? 1f : 0f;
    }

    private void drawSeats() {
        Objects.requireNonNull(liveState,
                "Seat rendering requires authoritative live state");
        drawActiveSeatGlows();
        drawAllInSeatFlames();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        // This entire ShapeRenderer batch is normal-alpha only.  Mixing the
        // active-seat additive glow into the same buffered batch allowed a
        // later flush to wash out geometry queued for earlier seats.
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Seat seat : seats) {
            float presence = seatPresenceAlpha(seat.index);
            if (presence <= 0f) {
                continue;
            }
            boolean active = isSeatActive(seat);
            Boolean settledWinner = liveState.resolvedHandWinner(seat.name);
            boolean settledShowdown = hasSettledPresentation(
                    liveState.hasHandResult(seat.name), settledWinner);
            TableSnapshot.PlayerSnapshot livePlayer = livePlayer(seat);
            // END snapshots may already mark every player inactive.  A player
            // with an ordered HandResult is nevertheless in the settled
            // showdown presentation and must keep the full winner/loser
            // treatment instead of being mistaken for a folded seat.
            boolean folded = shouldDimSeat(livePlayer == null
                    || livePlayer.active(), settledShowdown,
                    liveState.foldedThisHand(seat.name));
            boolean timedOut = livePlayer != null && livePlayer.timedOut();
            updateLiveSeatAmounts(seat);
            if (seat.index != 0) {
                // One component owns name, chips and amount for every rival.
                Color rim = settledShowdown
                        ? settledShowdownColor(Boolean.TRUE.equals(settledWinner))
                        : timeoutAwareRim(livePlayer, folded ? BUTTON_LINE
                                : (active ? ACTIVE_TURN_GOLD : SEAT_RIM));
                String actionLabel = lastActionLabelForSeat(seat.index);
                boolean showActionSurface = timedOut || !actionLabel.isEmpty();
                Color actionColor = lastActionColorForSeat(seat.index);
                shapes.setColor(rim.r, rim.g, rim.b,
                        (settledShowdown || active ? 1f
                                : folded ? 0.55f : 0.88f) * presence);
                float rimSize = active ? 6f : 2f;
                roundedRect(seat.podX - rimSize, seat.podY - rimSize,
                        PLAYER_POD_WIDTH + rimSize * 2f,
                        PLAYER_POD_HEIGHT + rimSize * 2f,
                        active ? 16f : 14f);
                shapes.setColor(0.015f, 0.028f, 0.05f,
                        (folded ? 0.72f : 0.92f) * presence);
                roundedRect(seat.podX, seat.podY,
                        PLAYER_POD_WIDTH, PLAYER_POD_HEIGHT, 12f);
                shapes.setColor(SWING_STACK_GREEN.r, SWING_STACK_GREEN.g,
                        SWING_STACK_GREEN.b, (folded ? 0.30f : 0.92f) * presence);
                roundedRect(seat.podX + 63f, seat.podY + 59f,
                        132f, 29f, 7f);
                Color potBackground = actionLabel.isEmpty()
                        ? BUTTON_LINE : actionColor;
                shapes.setColor(potBackground.r, potBackground.g,
                        potBackground.b, composedAlpha(potBackground,
                                folded ? 0.30f : 0.92f, presence));
                roundedRect(seat.podX + 201f, seat.podY + 59f,
                        78f, 29f, 7f);
                if (showActionSurface) {
                    shapes.setColor(actionColor.r, actionColor.g, actionColor.b,
                            composedAlpha(actionColor,
                                    seatActionSurfaceAlpha(folded,
                                            settledShowdown),
                                    presence));
                    roundedRect(seat.podX + 7f, seat.podY + 7f,
                            PLAYER_POD_WIDTH - 14f, 44f, 8f);
                    shapes.setColor(1f, 1f, 1f,
                            (folded ? 0.24f : 0.48f) * presence);
                    shapes.rect(seat.podX + 15f, seat.podY + 47f,
                            PLAYER_POD_WIDTH - 30f, 2f);
                }
                shapes.setColor(rim.r, rim.g, rim.b,
                        (active ? 0.88f : folded ? 0.24f : 0.42f) * presence);
                if (active) {
                    shapes.rect(seat.podX + 14f,
                            seat.podY + PLAYER_POD_HEIGHT - 5f,
                            PLAYER_POD_WIDTH - 28f, 5f);
                }
                shapes.rect(seat.podX + 12f, seat.podY + 55f,
                        PLAYER_POD_WIDTH - 24f, 2f);
                shapes.rect(seat.podX + 12f, seat.podY + 89f,
                        PLAYER_POD_WIDTH - 24f, 2f);
                shapes.rect(seat.podX + 198f, seat.podY + 61f, 2f, 24f);
            }
            shapes.setColor(PANEL.r, PANEL.g, PANEL.b, PANEL.a * presence);
            shapes.circle(seat.x, seat.y, AVATAR_OUTER_RADIUS, 48);
            Color avatarRim = settledShowdown
                    ? settledShowdownColor(Boolean.TRUE.equals(settledWinner))
                    : timeoutAwareRim(livePlayer, folded ? BUTTON_LINE
                            : (active ? ACTIVE_TURN_GOLD : SEAT_RIM));
            shapes.setColor(avatarRim.r, avatarRim.g, avatarRim.b,
                    avatarRim.a * presence);
            shapes.circle(seat.x, seat.y, AVATAR_RIM_RADIUS, 48);
            shapes.setColor(SEAT_INNER.r, SEAT_INNER.g, SEAT_INNER.b,
                    SEAT_INNER.a * presence);
            shapes.circle(seat.x, seat.y, AVATAR_INNER_RADIUS, 48);
        }
        shapes.end();

        batch.begin();
        for (Seat seat : seats) {
            float presence = seatPresenceAlpha(seat.index);
            if (presence <= 0f) {
                continue;
            }
            Texture avatar = tableAvatar(seat.name);
            TableSnapshot.PlayerSnapshot player = livePlayer(seat);
            boolean folded = shouldDimSeat(player == null || player.active(),
                    hasSettledPresentation(liveState.hasHandResult(seat.name),
                            liveState.resolvedHandWinner(seat.name)),
                    liveState.foldedThisHand(seat.name));
            Color avatarTint = folded ? FOLDED_AVATAR : Color.WHITE;
            batch.setColor(avatarTint.r, avatarTint.g, avatarTint.b,
                    avatarTint.a * presence);
            batch.flush();
            batch.setShader(avatarShader);
            batch.draw(avatar, seat.x - AVATAR_SIZE / 2f,
                    seat.y - AVATAR_SIZE / 2f, AVATAR_SIZE, AVATAR_SIZE);
            batch.flush();
            batch.setShader(null);
            batch.setColor(1f, 1f, 1f, presence);
            batch.setColor(avatarTint.r, avatarTint.g, avatarTint.b,
                    avatarTint.a * presence);
            for (int column = 0; column < 2; column++) {
                int chipCount = column == 0 ? 6 : 4;
                Texture stackChip = flyingChips[(seat.index + column) % flyingChips.length];
                float columnX = seat.stackX - 19f + column * 20f;
                for (int chip = 0; chip < chipCount; chip++) {
                    batch.draw(stackChip, columnX - 13f,
                            seat.stackY - 14f + chip * 4f, 26f, 26f);
                }
            }
            batch.setColor(1f, 1f, 1f, presence);
            if (seat.index != 0) {
                drawFittedCenteredInBox(playerNameFont, seat.name,
                        seat.podX + 12f, seat.podY + 93f,
                        PLAYER_POD_WIDTH - 24f, 23f,
                        folded ? Color.GRAY : Color.WHITE, presence);
                String actionLabel = lastActionLabelForSeat(seat.index);
                TableSnapshot.PlayerSnapshot livePlayer = livePlayer(seat);
                boolean timedOut = livePlayer != null && livePlayer.timedOut();
                if (timedOut) {
                    batch.setColor(1f, 1f, 1f, presence);
                    batch.draw(timeoutIcon, seat.podX + 17f,
                            seat.podY + 15f, 28f, 28f);
                }
                if (!actionLabel.isEmpty()) {
                    drawFittedCenteredInBox(seatActionFont, actionLabel,
                            seat.podX + (timedOut ? 48f : 16f),
                            seat.podY + 13f,
                            PLAYER_POD_WIDTH - (timedOut ? 64f : 32f), 32f,
                            folded ? Color.GRAY
                                    : lastActionTextColorForSeat(seat.index),
                            presence * seatActionTextAlpha(folded));
                }
                drawFittedCenteredInBox(stackFont, seat.stackText,
                        seat.podX + 68f, seat.podY + 62f, 124f, 23f,
                        folded ? Color.GRAY : Color.WHITE, presence);
                drawFittedCenteredInBox(stackFont, seat.investedText,
                        seat.podX + 202f, seat.podY + 62f, 76f, 23f,
                        folded ? Color.GRAY
                                : lastActionTextColorForSeat(seat.index), presence);
            }
        }
        batch.end();
        drawLatencyDots();
        drawPositionChips();
    }

    private boolean isSeatActive(Seat seat) {
        return !isLiveReconnectingPlayer(seat.name)
                && isActionableTurn(liveState.snapshot(), seat.name);
    }

    /**
     * Additive light is isolated in its own submitted batch.  ShapeRenderer is
     * buffered: changing glBlendFunc between shapes without ending the batch
     * can retroactively affect earlier vertices when the buffer flushes.
     */
    private void drawActiveSeatGlows() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Seat seat : seats) {
            float presence = seatPresenceAlpha(seat.index);
            if (presence <= 0f || !isSeatActive(seat)) {
                continue;
            }
            float activePulse = 0.5f + 0.5f
                    * MathUtils.sin(totalTime * 4.8f);
            if (seat.index != 0) {
                shapes.setColor(ACTIVE_TURN_GOLD.r, ACTIVE_TURN_GOLD.g,
                        ACTIVE_TURN_GOLD.b,
                        (0.34f + activePulse * 0.24f) * presence);
                roundedRect(seat.podX - 12f, seat.podY - 12f,
                        PLAYER_POD_WIDTH + 24f,
                        PLAYER_POD_HEIGHT + 24f, 22f);
            }
            shapes.setColor(ACTIVE_TURN_GOLD.r, ACTIVE_TURN_GOLD.g,
                    ACTIVE_TURN_GOLD.b,
                    (0.30f + activePulse * 0.22f) * presence);
            shapes.circle(seat.x, seat.y, AVATAR_ACTIVE_RADIUS + 10f, 48);
            shapes.setColor(ACTIVE_TURN_GOLD.r, ACTIVE_TURN_GOLD.g,
                    ACTIVE_TURN_GOLD.b,
                    (0.70f + activePulse * 0.25f) * presence);
            shapes.circle(seat.x, seat.y, AVATAR_ACTIVE_RADIUS, 48);
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Persistent GDX-only presentation of the authoritative ALL-IN state.
     * The state comes from PlayerAction and is cleared by the next PREPARE
     * boundary; this effect neither completes nor delays a dealer barrier.
     */
    private void drawAllInSeatFlames() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Three independently seeded sheets create a low combustion bed plus
        // asymmetrical licking tongues. A soft additive pass supplies heat
        // bloom before the opaque flame pass; there are deliberately no
        // radial rays or circular halo sprites.
        if (allInFireShader != null) {
            batch.setShader(allInFireShader);
            for (int pass = 0; pass < 2; pass++) {
                boolean bloom = pass == 0;
                batch.setBlendFunction(GL20.GL_SRC_ALPHA,
                        bloom ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
                batch.begin();
                for (Seat seat : seats) {
                    float presence = seatPresenceAlpha(seat.index);
                    if (presence <= 0f
                            || !seatHasAllInFire(liveState, seat.name)) {
                        continue;
                    }
                    for (int layer = 0; layer < 3; layer++) {
                        Rectangle fire = allInFireLayerBounds(seat.x, seat.y,
                                layer);
                        float expansion = bloom ? 12f : 0f;
                        batch.flush();
                        allInFireShader.setUniformf("u_time",
                                totalTime * (1f + layer * 0.065f));
                        allInFireShader.setUniformf("u_seed",
                                seat.index * 1.713f + layer * 4.129f);
                        allInFireShader.setUniformf("u_alpha",
                                (layer == 0 ? 0.96f : 0.58f) * presence);
                        allInFireShader.setUniformf("u_bloom",
                                bloom ? 1f : 0f);
                        batch.setColor(1f, 1f, 1f, 1f);
                        batch.draw(allInFireCanvas,
                                fire.x - expansion / 2f,
                                fire.y - expansion / 2f,
                                fire.width + expansion,
                                fire.height + expansion);
                    }
                }
                batch.end();
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA,
                    GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setShader(null);
        }

        // Embers rise with buoyancy and lateral turbulence. Their staggered
        // size/colour and intermittent ignition avoid a uniform particle ring
        // and make the fire read as a hot, living combustion bed.
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Seat seat : seats) {
            float presence = seatPresenceAlpha(seat.index);
            if (presence <= 0f
                    || !seatHasAllInFire(liveState, seat.name)) {
                continue;
            }
            for (int ember = 0; ember < 25; ember++) {
                float speed = 0.27f + (ember % 7) * 0.033f;
                float life = (totalTime * speed + ember * 0.173f
                        + seat.index * 0.113f) % 1f;
                float buoyantLife = (float) Math.pow(life, 0.78f);
                float origin = (((ember * 37) % 101) / 100f - 0.5f) * 70f;
                float curl = MathUtils.sin(ember * 2.37f
                        + buoyantLife * 7.4f + totalTime * 0.41f)
                        * (4f + buoyantLife * 19f);
                curl += MathUtils.sin(ember * 0.91f
                        - buoyantLife * 3.1f + totalTime * 0.23f)
                        * buoyantLife * 8f;
                float emberX = seat.x + origin * (1f - life * 0.18f) + curl;
                float emberY = seat.y + 8f + buoyantLife * 162f;
                float hot = 1f - life;
                float ignition = MathUtils.clamp(life * 11f, 0f, 1f);
                float extinction = hot * hot * (3f - 2f * hot);
                shapes.setColor(1f, 0.24f + hot * 0.60f,
                        0.012f + hot * 0.11f,
                        ignition * extinction * 0.72f * presence);
                float radius = 0.55f
                        + hot * (0.85f + (ember % 4) * 0.22f);
                // A single faint wake reads as a rising spark without turning
                // every ember into the same dotted line.
                shapes.circle(emberX - curl * 0.025f,
                        emberY - 3.4f, radius * 0.48f, 7);
                shapes.circle(emberX, emberY, radius, 8);
            }
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    static boolean seatHasAllInFire(GdxTableViewState state,
            String nickname) {
        // Reveals, HandResult and RIT participation never imply ALL-IN. The
        // effect is owned solely by that player's accepted PlayerAction.
        return state != null && state.actionKind(nickname)
                == TableVisualEvent.PlayerAction.ActionKind.ALL_IN;
    }

    static Rectangle allInFireLayerBounds(float centerX, float centerY,
            int layer) {
        return switch (layer) {
            case 0 -> new Rectangle(centerX - ALL_IN_FIRE_WIDTH / 2f,
                    centerY - 57f, ALL_IN_FIRE_WIDTH, 158f);
            case 1 -> new Rectangle(centerX - 94f, centerY - 51f,
                    120f, ALL_IN_FIRE_HEIGHT + 10f);
            case 2 -> new Rectangle(centerX - 22f, centerY - 49f,
                    116f, ALL_IN_FIRE_HEIGHT - 4f);
            default -> throw new IllegalArgumentException(
                    "Invalid ALL-IN fire layer: " + layer);
        };
    }

    static float avatarZoomSize(float avatarSize, float worldHeight) {
        if (!Float.isFinite(avatarSize) || !Float.isFinite(worldHeight)
                || avatarSize <= 0f || worldHeight <= 0f) {
            return 0f;
        }
        return Math.max(avatarSize, Math.min(avatarSize * AVATAR_ZOOM_FACTOR,
                worldHeight * AVATAR_ZOOM_MAX_HEIGHT_RATIO));
    }

    static boolean avatarZoomDelayReached(float elapsedSeconds) {
        return Float.isFinite(elapsedSeconds)
                && elapsedSeconds >= AVATAR_ZOOM_HOVER_SECONDS;
    }

    /**
     * Native counterpart of Swing's {@code AvatarZoomOverlay}. It is purely a
     * local presentation aid: no command, event or dealer barrier is involved.
     */
    private void drawAvatarZoomOverlay(float worldWidth, float worldHeight) {
        updateAvatarZoomHover();
        if (avatarZoomNickname == null) return;
        Seat seat = seatByNickname(avatarZoomNickname);
        if (seat == null || seatPresenceAlpha(seat.index) <= 0f) {
            clearAvatarZoom();
            return;
        }

        float imageSize = avatarZoomSize(AVATAR_SIZE, worldHeight);
        if (imageSize <= AVATAR_SIZE) {
            clearAvatarZoom();
            return;
        }
        float pad = Math.max(6f, imageSize / 24f);
        float nameHeight = 30f;
        float stackWidth = Math.max(112f, imageSize * 0.82f);
        float boxWidth = imageSize + stackWidth + pad * 4f;
        float boxHeight = imageSize + nameHeight + pad * 3f;
        float x = MathUtils.clamp(seat.x - imageSize / 2f - pad,
                0f, Math.max(0f, worldWidth - boxWidth));
        float y = MathUtils.clamp(seat.y - imageSize / 2f - nameHeight - pad,
                0f, Math.max(0f, worldHeight - boxHeight));
        avatarZoomBounds.set(x, y, boxWidth, boxHeight);

        boolean folded = isFolded(seat.index);
        float imageX = x + pad;
        float imageY = y + nameHeight + pad * 2f;
        float stackX = imageX + imageSize + pad * 2f;
        float stackY = imageY + imageSize / 2f - 22f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.90f);
        roundedRect(x, y, boxWidth, boxHeight, 16f);
        shapes.setColor(SEAT_RIM.r, SEAT_RIM.g, SEAT_RIM.b, 0.98f);
        roundedRect(x + 3f, y + 3f, boxWidth - 6f, boxHeight - 6f, 14f);
        shapes.setColor(0.012f, 0.028f, 0.050f, 0.98f);
        roundedRect(x + 6f, y + 6f, boxWidth - 12f, boxHeight - 12f, 12f);
        Color pill = folded ? BUTTON_LINE : SWING_STACK_GREEN;
        shapes.setColor(pill.r, pill.g, pill.b, 0.96f);
        roundedRect(stackX, stackY, stackWidth - pad * 2f, 44f, 12f);
        shapes.end();

        Texture avatar = tableAvatar(seat.name);
        Color tint = folded ? FOLDED_AVATAR : Color.WHITE;
        batch.begin();
        batch.setColor(tint);
        batch.flush();
        batch.setShader(avatarShader);
        batch.draw(avatar, imageX, imageY, imageSize, imageSize);
        batch.flush();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        drawFittedCenteredInBox(playerNameFont, seat.name,
                imageX, y + pad, imageSize, nameHeight,
                folded ? Color.GRAY : Color.WHITE, 1f);
        drawFittedCenteredInBox(uiFont, seat.stackText,
                stackX + pad, stackY + 5f, stackWidth - pad * 4f, 34f,
                folded ? Color.LIGHT_GRAY : Color.WHITE, 1f);
        batch.end();
    }

    private void updateAvatarZoomHover() {
        if (liveState == null || finalSummary != null || uiLayer != UI_NONE
                || activeDialog != null
                || !tablePreference("resaltar_avatares", false)) {
            clearAvatarZoom();
            return;
        }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);

        if (avatarZoomNickname != null) {
            Seat zoomed = seatByNickname(avatarZoomNickname);
            if (avatarZoomBounds.contains(pointer)
                    || zoomed != null && pointer.dst(zoomed.x, zoomed.y)
                    <= AVATAR_INNER_RADIUS) {
                return;
            }
            clearAvatarZoom();
        }

        Seat hovered = null;
        for (Seat seat : seats) {
            if (seatPresenceAlpha(seat.index) > 0f
                    && pointer.dst(seat.x, seat.y) <= AVATAR_INNER_RADIUS) {
                hovered = seat;
                break;
            }
        }
        if (hovered == null) {
            avatarHoverNickname = null;
            return;
        }
        if (!hovered.name.equals(avatarHoverNickname)) {
            avatarHoverNickname = hovered.name;
            avatarHoverStartedAt = totalTime;
            return;
        }
        if (avatarZoomDelayReached(totalTime - avatarHoverStartedAt)) {
            avatarZoomNickname = hovered.name;
            playPreferenceSound("misc/zoom_in.wav", "sonido_zoom", 0.72f);
        }
    }

    private void clearAvatarZoom() {
        avatarHoverNickname = null;
        avatarZoomNickname = null;
        avatarZoomBounds.set(0f, 0f, 0f, 0f);
    }

    private boolean consumeAvatarZoomPointer() {
        if (avatarZoomNickname == null
                || !Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                && !Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            return false;
        }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        return avatarZoomBounds.contains(pointer);
    }

    private void updateTableChat() {
        if (tableChat == null || liveState == null) return;
        if (seatChatBlockHand != liveState.handNumber()) {
            seatChatBlockHand = liveState.handNumber();
            blockedSeatMediaNotices.clear();
        }
        boolean notifications = preferences == null ||
                GdxSettingsContract.chatNotificationsEnabled(
                        preferences.properties(), true);
        for (LobbyChatMessage message : tableChat.drainIncoming()) {
            boolean ownMessage = message.nickname().equals(
                    tableChat.snapshot().localNickname());
            if (preferences != null
                    && message.type() == LobbyChatMessage.Type.IMAGE
                    && !ownMessage
                    && GdxChatImageHistory.autoReceive(
                            preferences.properties())) {
                tableImageHistory = GdxChatImageHistory.remember(
                        preferences.properties(), message.content(), false);
                tableGalleryMedia.refresh(tableImageHistory, 8,
                        "table-history");
                if (preferences != null) preferences.saveDeferred();
            }
            boolean senderBlocked = blockedSeatMediaNotices.contains(
                    message.nickname());
            boolean voiceNotice = shouldShowVoiceSeatNotice(
                    audioControl.enabled(),
                    tablePreference("audio_block_voice_messages", false),
                    ownMessage,
                    tablePreference("audio_play_own_voice", true));
            boolean spokenText = shouldSpeakTableChat(message.type(),
                    notifications, audioControl.enabled(),
                    globalTextToSpeechEnabled(),
                    tablePreference("audio_block_tts_local", false),
                    senderBlocked);
            boolean eligibleNotice = shouldShowSeatNotice(message.type(),
                    notifications,
                    tablePreference("chat_images_ingame", true), voiceNotice,
                    senderBlocked, ownMessage);
            if (shouldShowSilentTextNotice(message.type(), eligibleNotice,
                    spokenText)
                    && seatByNickname(message.nickname()) != null) {
                silentChatNotices.addLast(new SilentChatNotice(
                        message.nickname(),
                        GdxTextToSpeechPlayback.cleanChatMessage(
                                message.content()), senderBlocked,
                        seatChatNoticeDuration(message.type(),
                                message.content())));
                continue;
            }
            if (!shouldDisplaySeatNotice(message.type(), eligibleNotice,
                    spokenText)
                    || seatByNickname(message.nickname()) == null) {
                continue;
            }
            SeatChatNotice notice = new SeatChatNotice(message.type(),
                    message.content(), totalTime,
                    totalTime + seatChatNoticeDuration(message.type(),
                            message.content()));
            if (spokenText) {
                textToSpeech.enqueue(message.content(),
                        presentationSettings == null
                                ? tablePreferenceText("lenguaje", "es")
                                : presentationSettings.language(),
                        () -> {
                            if (disposed || seatByNickname(
                                    message.nickname()) == null) return;
                            SeatChatNotice previous = seatChatNotices.remove(
                                    message.nickname());
                            if (previous != null) previous.dispose();
                            // Swing only makes talk.png visible once playback
                            // really starts. The normal completion replaces
                            // this bounded backend-failure guard with Swing's
                            // 500 ms tail below.
                            notice.startedAt = totalTime;
                            notice.expiresAt = totalTime
                                    + seatChatPlaybackWatchdog(
                                            message.type(), message.content());
                            seatChatNotices.put(message.nickname(), notice);
                        })
                        .thenAccept(played -> {
                            if (!played || Gdx.app == null) return;
                            Gdx.app.postRunnable(() -> {
                                if (!disposed
                                        && seatChatNotices.get(
                                                message.nickname()) == notice) {
                                    notice.expiresAt = totalTime + 0.5f;
                                }
                            });
                        });
                continue;
            }
            if (message.type() == LobbyChatMessage.Type.VOICE) {
                playTableVoice(message, () -> {
                    if (Gdx.app == null) return;
                    Gdx.app.postRunnable(() -> {
                        if (disposed || seatByNickname(
                                message.nickname()) == null) return;
                        SeatChatNotice previous = seatChatNotices.remove(
                                message.nickname());
                        if (previous != null) previous.dispose();
                        notice.startedAt = totalTime;
                        // Bounded only as a backend-failure guard. Normal
                        // completion uses Swing's exact 500 ms tail below.
                        notice.expiresAt = totalTime
                                + seatChatPlaybackWatchdog(
                                        message.type(), message.content());
                        seatChatNotices.put(message.nickname(), notice);
                    });
                }).whenComplete((ignored, failure) -> {
                    if (Gdx.app == null) return;
                    Gdx.app.postRunnable(() -> {
                        if (!disposed
                                && seatChatNotices.get(message.nickname())
                                        == notice) {
                            notice.expiresAt = totalTime + 0.5f;
                        }
                    });
                });
                continue;
            }
            SeatChatNotice previous = seatChatNotices.remove(message.nickname());
            if (previous != null) previous.dispose();
            seatChatNotices.put(message.nickname(), notice);
            if (message.type() == LobbyChatMessage.Type.IMAGE) {
                loadSeatChatImage(message.nickname(), notice);
            }
        }
        var iterator = seatChatNotices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SeatChatNotice> entry = iterator.next();
            if (entry.getValue().expiresAt <= totalTime
                    || seatByNickname(entry.getKey()) == null) {
                entry.getValue().dispose();
                iterator.remove();
            }
        }
        updateSilentChatNotice();
    }

    private void updateSilentChatNotice() {
        SilentChatNotice active = silentChatNotices.peekFirst();
        if (active != null && active.startedAt >= 0f
                && active.expiresAt <= totalTime) {
            silentChatNotices.removeFirst();
            active = silentChatNotices.peekFirst();
        }
        if (active != null && active.startedAt < 0f) {
            active.startedAt = totalTime;
            active.expiresAt = totalTime + active.duration;
        }
    }

    private CompletableFuture<Void> playTableVoice(LobbyChatMessage message,
            Runnable playbackStarted) {
        if (!audioControl.enabled()
                || tablePreference("audio_block_voice_messages", false)
                || (message.nickname().equals(tableChat.snapshot().localNickname())
                && !tablePreference("audio_play_own_voice", true))) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            byte[] wav = Base64.getDecoder().decode(message.content());
            if (VoiceWavContract.isValid(wav)) {
                return GdxVoicePlayback.play(wav, effectsVolume,
                        playbackStarted);
            }
        } catch (IllegalArgumentException malformed) {
            // The typed chat event remains visible, but malformed audio is inert.
        }
        return CompletableFuture.completedFuture(null);
    }

    private void loadSeatChatImage(String nickname, SeatChatNotice notice) {
        CompletableFuture.supplyAsync(() -> {
            byte[] data = GdxChatImageLoader.download(notice.content);
            try {
                StreamingGifTextureAnimation gif =
                        GdxChatImageLoader.isGif(data)
                                ? StreamingGifTextureAnimation.loadLooping(
                                        data, "chat:" + nickname, 360)
                                : null;
                return new PreparedSeatChatImage(data, gif);
            } catch (IOException invalidGif) {
                throw new java.util.concurrent.CompletionException(invalidGif);
            }
        }).whenComplete((prepared, failure) -> Gdx.app.postRunnable(() -> {
                    if (disposed || seatChatNotices.get(nickname) != notice) {
                        if (prepared != null && prepared.gif() != null) {
                            prepared.gif().dispose();
                        }
                        return;
                    }
                    notice.loading = false;
                    if (failure != null || prepared == null
                            || prepared.data().length == 0) {
                        notice.failed = true;
                        return;
                    }
                    try {
                        if (prepared.gif() != null) {
                            notice.gif = prepared.gif();
                            // Download/metadata work happens asynchronously.
                            // Start the visible loop only once it is attached
                            // so frame zero and both authored passes are seen.
                            notice.startedAt = totalTime;
                            notice.expiresAt = totalTime + Math.max(4f,
                                    Math.min(14f, notice.gif.durationSeconds() * 2f));
                        } else {
                            byte[] data = prepared.data();
                            Pixmap pixmap = new Pixmap(data, 0, data.length);
                            try {
                                notice.image = new Texture(pixmap, true);
                                notice.image.setFilter(TextureFilter.MipMapLinearLinear,
                                        TextureFilter.Linear);
                            } finally {
                                pixmap.dispose();
                            }
                            notice.expiresAt = totalTime + 6.5f;
                        }
                    } catch (RuntimeException invalidImage) {
                        notice.failed = true;
                    }
                }));
    }

    /** Mirrors Swing's talk.png notification over the cards of the sender. */
    private void drawSeatChatNotices() {
        if (seatChatNotices.isEmpty()) return;
        batch.begin();
        for (Map.Entry<String, SeatChatNotice> entry : seatChatNotices.entrySet()) {
            Seat seat = seatByNickname(entry.getKey());
            if (seat == null) continue;
            SeatChatNotice notice = entry.getValue();
            float remaining = MathUtils.clamp((notice.expiresAt - totalTime) / 0.35f,
                    0f, 1f);
            float appeared = MathUtils.clamp((totalTime - notice.startedAt) / 0.16f,
                    0f, 1f);
            float alpha = Interpolation.fade.apply(Math.min(appeared, remaining));
            Texture icon = seatChatNoticeTexture(notice);
            Rectangle bounds = seatChatNoticeBounds(seat, icon);
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(icon, bounds.x, bounds.y, bounds.width, bounds.height);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** Swing uses a mute/blocked dialog instead of talk.png when TTS cannot play. */
    private void drawSilentChatNotice() {
        SilentChatNotice notice = silentChatNotices.peekFirst();
        if (notice == null || notice.startedAt < 0f) return;
        float appeared = MathUtils.clamp(
                (totalTime - notice.startedAt) / 0.16f, 0f, 1f);
        float remaining = MathUtils.clamp(
                (notice.expiresAt - totalTime) / 0.22f, 0f, 1f);
        float alpha = Interpolation.fade.apply(Math.min(appeared, remaining));
        float width = Math.min(820f, viewport.getWorldWidth() - 80f);
        float height = 92f;
        float x = (viewport.getWorldWidth() - width) / 2f;
        float y = viewport.getWorldHeight() - 190f;
        Color accent = notice.senderBlocked ? POT_GOLD : FOLD_RED;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.62f * alpha);
        roundedRect(x + 7f, y - 7f, width, height, 13f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.94f * alpha);
        roundedRect(x - 2f, y - 2f, width + 4f, height + 4f, 13f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.98f * alpha);
        roundedRect(x, y, width, height, 11f);
        shapes.end();
        batch.begin();
        Texture icon = notice.senderBlocked ? blockedSoundIcon : muteIcon;
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(icon, x + 18f, y + 18f, 56f, 56f);
        drawFittedCenteredInBox(smallFont,
                notice.nickname + ": " + notice.content,
                x + 92f, y + 16f, width - 112f, height - 32f,
                notice.senderBlocked ? POT_GOLD : Color.WHITE, alpha);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private Texture seatChatNoticeTexture(SeatChatNotice notice) {
        if (notice.gif != null) {
            Texture frame = notice.gif.frameAt(totalTime - notice.startedAt);
            if (frame != null) return frame;
        }
        if (notice.image != null) return notice.image;
        return notice.type == LobbyChatMessage.Type.IMAGE
                ? fastButtonIcons[3] : talkIcon;
    }

    private Rectangle seatChatNoticeBounds(Seat seat, Texture icon) {
        Rectangle target;
        if (seat.index == 0) {
            float width = viewport.getWorldWidth();
            float hudWidth = Math.min(1110f, width - 620f);
            float hudX = width / 2f - hudWidth / 2f;
            target = new Rectangle(hudX, LOCAL_HUD_Y,
                    230f, LOCAL_HUD_HEIGHT);
        } else {
            target = new Rectangle(seat.podX, seat.podY,
                    PLAYER_POD_WIDTH, PLAYER_POD_HEIGHT);
        }
        // Media belongs to the player's HUD, never to the physical cards.
        // Covering the hole-card envelope made an intact hand look absent.
        return fitSeatChatNoticeBounds(target, icon.getWidth(),
                icon.getHeight());
    }

    static Rectangle fitSeatChatNoticeBounds(Rectangle target,
            float sourceWidth, float sourceHeight) {
        float availableW = Math.max(1f, target.width - 12f);
        float availableH = Math.max(1f, target.height - 12f);
        float scale = Math.min(availableW / Math.max(1f, sourceWidth),
                availableH / Math.max(1f, sourceHeight));
        float width = Math.max(1f, sourceWidth * scale);
        float height = Math.max(1f, sourceHeight * scale);
        return new Rectangle(target.x + (target.width - width) / 2f,
                target.y + (target.height - height) / 2f, width, height);
    }

    private boolean handleSeatChatNoticeClick(float x, float y,
            boolean blockRemoteMedia) {
        for (Map.Entry<String, SeatChatNotice> entry
                : List.copyOf(seatChatNotices.entrySet())) {
            Seat seat = seatByNickname(entry.getKey());
            if (seat == null) continue;
            SeatChatNotice notice = entry.getValue();
            if (!seatChatNoticeBounds(seat,
                    seatChatNoticeTexture(notice)).contains(x, y)) {
                continue;
            }
            if (blockRemoteMedia && seat.index != 0) {
                blockedSeatMediaNotices.add(entry.getKey());
            }
            if (seatChatNotices.remove(entry.getKey(), notice)) {
                notice.dispose();
            }
            return true;
        }
        return false;
    }

    private void drawLatencyDots() {
        if (liveState == null) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        for (Seat seat : seats) {
            TableSnapshot.PlayerSnapshot player = livePlayer(seat);
            if (player == null || seatPresenceAlpha(seat.index) <= 0f) {
                continue;
            }
            float x = seat.x - 42f;
            float y = seat.y + 41f;
            shapes.setColor(0f, 0f, 0f, 0.62f);
            shapes.circle(x, y, 10f, 28);
            shapes.setColor(latencyColor(player));
            shapes.circle(x, y, 8f, 28);
            if (player.reconnectionCount() > 0) {
                shapes.setColor(Color.WHITE);
                shapes.circle(x + 8f, y - 8f, 7f, 24);
            }
        }
        shapes.end();

        batch.begin();
        for (Seat seat : seats) {
            TableSnapshot.PlayerSnapshot player = livePlayer(seat);
            if (player == null || player.reconnectionCount() <= 0
                    || seatPresenceAlpha(seat.index) <= 0f) {
                continue;
            }
            String badge = player.reconnectionCount() > 9
                    ? "9+" : Integer.toString(player.reconnectionCount());
            drawFittedCentered(smallFont, badge, seat.x - 34f,
                    seat.y + 37f, 12f, Color.BLACK, 1f);
        }
        batch.end();
    }

    private static Color latencyColor(TableSnapshot.PlayerSnapshot player) {
        if (player.telemetryAt() <= 0L
                || System.currentTimeMillis() - player.telemetryAt()
                > LATENCY_STALE_MILLIS) {
            return LATENCY_STALE;
        }
        int first = player.latency();
        int second = player.previousLatency();
        int best = first < 0 ? second : second < 0 ? first
                : Math.min(first, second);
        if (best < 0) return LATENCY_RED;
        if (best <= 100) return LATENCY_GREEN;
        if (best <= 250) return LATENCY_YELLOW;
        if (best <= 400) return LATENCY_ORANGE;
        return LATENCY_RED;
    }

    private void drawLiveHoleCards(boolean foregroundFlights) {
        Texture cardBack = activeCardBack();
        boolean compositeDisabledCards = !foregroundFlights
                && uiLayer != UI_SETTINGS;
        if (compositeDisabledCards) {
            drawDisabledHoleCardsLayer(cardBack);
        }
        batch.begin();
        if (!foregroundFlights) {
            for (TableSnapshot.PlayerSnapshot player : liveState.snapshot().players()) {
                Seat seat = seatByNickname(player.nickname());
                if (seat == null) {
                    continue;
                }
                if (liveHoleSwap != null
                        && liveHoleSwap.cards.size() >= 2
                        && liveHoleSwap.event.nickname().equals(player.nickname())) {
                    drawLiveHoleSwap(seat, cardBack);
                    continue;
                }
                if (liveHoleFold != null
                        && liveHoleFold.event.nickname().equals(player.nickname())) {
                    drawLiveHoleFold(player, seat, cardBack);
                    continue;
                }
                if (liveHoleReveal != null
                        && liveHoleReveal.event.nickname().equals(player.nickname())) {
                    drawLiveHoleRevealResting(seat, cardBack);
                    continue;
                }
                List<TableSnapshot.CardSnapshot> holeCards
                        = liveState.presentedHoleCards(player.nickname());
                for (int slot = 0; slot < holeCards.size() && slot < 2; slot++) {
                    if (hasActiveHoleFlight(player.nickname(), slot)) {
                        continue;
                    }
                    TableSnapshot.CardSnapshot card = holeCards.get(slot);
                    // A folded face-down hand disappears, as in Swing. A
                    // face-up card disabled by the showdown must remain on the
                    // table and be drawn dimmed; hiding it loses the canonical
                    // winning-five-card focus.
                    if (!card.visible()
                            || card.disabled() && !card.faceUp()) {
                        continue;
                    }
                    if (liveRestingCardAlpha(card, player.nickname(), slot,
                            false) < 1f && compositeDisabledCards) {
                        // Disabled hands are composited as one translucent
                        // layer. Drawing each card with alpha independently
                        // makes the rear card visible through the front card.
                        continue;
                    }
                    LiveCardPlacement placement = liveHolePlacement(seat, slot);
                    drawLiveRestingCard(card, placement, cardBack,
                            player.nickname(), slot, false);
                }
            }
        } else {
            for (LiveCardFlight flight : liveCardFlights) {
                if (!(flight.event instanceof TableVisualEvent.DealHoleCard deal)
                        || flight.visualFinished()) {
                    continue;
                }
                Seat seat = seatByNickname(deal.nickname());
                if (seat != null) {
                    LiveCardPlacement target = liveHolePlacement(seat, deal.slot());
                    float progress = Interpolation.pow2Out.apply(flight.flightProgress());
                    float fanX = 1f;
                    float fanY = 0f;
                    if (seat.index == 0) {
                        float towardX = tableCenterX - seat.x;
                        float towardY = tableCenterY - seat.y;
                        float length = Math.max(1f, (float) Math.sqrt(
                                towardX * towardX + towardY * towardY));
                        fanX = -towardY / length;
                        fanY = towardX / length;
                    }
                    // Canonical visual-reference curve: the tangent follows
                    // each hand's fan and the constant lift is only 62 world units.
                    float controlX = (dealerSourceX + target.x) * 0.5f
                            + fanX * 128f;
                    float controlY = (dealerSourceY + target.y) * 0.5f
                            + fanY * 128f + 62f;
                    float x = bezier(dealerSourceX, controlX, target.x, progress);
                    float y = bezier(dealerSourceY, controlY, target.y, progress);
                    float launchRotation = (flight.dealOrder & 1) == 0
                            ? -26f : 26f;
                    float rotation = MathUtils.lerp(launchRotation,
                            target.rotation, progress);
                    float reveal = flight.revealProgress();
                    float scale = (0.82f + progress * 0.18f)
                            * cardFlipScale(reveal);
                    float shaderCanvas = reveal > 0f ? 1.5f : 1f;
                    float renderWidth = target.width * shaderCanvas;
                    float renderHeight = target.height * shaderCanvas;
                    if (reveal > 0f) {
                        usePerspectiveCardShader(liveCardFace(deal.card().code()),
                                reveal * MathUtils.PI,
                                target.height / target.width);
                    } else {
                        useRoundedCardShader();
                    }
                    batch.setColor(Color.WHITE);
                    batch.draw(cardBack, x - renderWidth / 2f,
                            y - renderHeight / 2f, renderWidth / 2f,
                            renderHeight / 2f, renderWidth, renderHeight,
                            scale, scale, rotation, 0, 0,
                            cardBack.getWidth(), cardBack.getHeight(), false, false);
                }
            }
            if (liveHoleReveal != null) {
                Seat seat = seatByNickname(liveHoleReveal.event.nickname());
                if (seat != null) {
                    drawLiveHoleRevealFlights(seat, cardBack);
                }
            }
        }
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawLiveHoleSwap(Seat seat, Texture cardBack) {
        if (liveHoleSwap.cards.size() < 2) {
            return;
        }
        float raw = liveHoleSwap.progress();
        float motion = Interpolation.smoother.apply(raw);
        float arc = liveHoleSwap.arc
                ? MathUtils.sin(motion * MathUtils.PI) : 0f;
        float towardX = tableCenterX - seat.x;
        float towardY = tableCenterY - seat.y;
        float length = Math.max(1f, (float) Math.sqrt(
                towardX * towardX + towardY * towardY));
        towardX /= length;
        towardY /= length;

        LiveCardPlacement firstPlacement = null;
        LiveCardPlacement secondPlacement = null;
        for (int slot = 0; slot < 2; slot++) {
            LiveCardPlacement from = liveHolePlacement(seat, slot);
            LiveCardPlacement to = liveHolePlacement(seat, 1 - slot);
            float lane = slot == 0 ? -30f : 46f;
            LiveCardPlacement placement = new LiveCardPlacement(
                    MathUtils.lerp(from.x, to.x, motion) + towardX * arc * lane,
                    MathUtils.lerp(from.y, to.y, motion) + towardY * arc * lane,
                    from.width, from.height,
                    MathUtils.lerp(from.rotation, to.rotation, motion));
            if (slot == 0) {
                firstPlacement = placement;
            } else {
                secondPlacement = placement;
            }
        }
        for (int layer = 0; layer < 2; layer++) {
            int slot = holeCardSlotForLayer(firstPlacement.x,
                    secondPlacement.x, layer);
            drawLiveHoleSwapCard(slot,
                    slot == 0 ? firstPlacement : secondPlacement, cardBack);
        }
    }

    private void drawLiveHoleSwapCard(int slot, LiveCardPlacement placement,
            Texture cardBack) {
        if (!hasActiveHoleFlight(liveHoleSwap.event.nickname(), slot)) {
            drawLiveRestingCard(liveHoleSwap.cards.get(slot), placement,
                    cardBack);
        }
    }

    private boolean hasActiveHoleFlight(String nickname, int slot) {
        for (LiveCardFlight flight : liveCardFlights) {
            if (!flight.visualFinished()
                    && flight.event instanceof TableVisualEvent.DealHoleCard deal
                    && deal.slot() == slot && deal.nickname().equals(nickname)) {
                return true;
            }
        }
        return false;
    }

    private void drawLiveHoleRevealResting(Seat seat, Texture cardBack) {
        for (int slot = 0; slot < 2; slot++) {
            float progress = liveHoleReveal.progress(slot);
            if (progress > 0f && progress < 1f) {
                continue;
            }
            TableSnapshot.CardSnapshot card = progress >= 1f
                    ? liveHoleReveal.revealedCard(slot)
                    : liveHoleReveal.originalCard(slot);
            drawLiveRestingCard(card, liveHolePlacement(seat, slot), cardBack);
        }
    }

    private void drawLiveHoleRevealFlights(Seat seat, Texture cardBack) {
        float towardX = tableCenterX - seat.x;
        float towardY = tableCenterY - seat.y;
        float length = Math.max(1f, (float) Math.sqrt(
                towardX * towardX + towardY * towardY));
        towardX /= length;
        towardY /= length;
        LiveCardPlacement first = liveHolePlacement(seat, 0);
        LiveCardPlacement second = liveHolePlacement(seat, 1);
        for (int layer = 0; layer < 2; layer++) {
            int slot = holeCardSlotForLayer(first.x, second.x, layer);
            float progress = liveHoleReveal.progress(slot);
            LiveCardPlacement target = slot == 0 ? first : second;
            if (progress <= 0f || progress >= 1f) {
                // Resting cards were painted before the foreground reveal
                // pass. Repaint the physically right card when it is not the
                // one flipping, otherwise a flipping left card can cover its
                // rank for the duration of the animation.
                if (layer == 1) {
                    TableSnapshot.CardSnapshot card = progress >= 1f
                            ? liveHoleReveal.revealedCard(slot)
                            : liveHoleReveal.originalCard(slot);
                    drawLiveRestingCard(card, target, cardBack);
                }
                continue;
            }
            float arc = seat.index == 0 ? 0f
                    : MathUtils.sin(progress * MathUtils.PI);
            float x = target.x + towardX * arc * 56f;
            float y = target.y + towardY * arc * 56f + arc * 24f;
            float canvasWidth = target.width * 1.5f;
            float canvasHeight = target.height * 1.5f;
            float revealScale = cardFlipScale(progress);
            Texture face = liveCardFace(liveHoleReveal.revealedCard(slot).code());
            usePerspectiveCardShader(face, progress * MathUtils.PI,
                    target.height / target.width);
            batch.setColor(Color.WHITE);
            batch.draw(cardBack, x - canvasWidth / 2f,
                    y - canvasHeight / 2f, canvasWidth / 2f,
                    canvasHeight / 2f, canvasWidth, canvasHeight,
                    revealScale, revealScale, target.rotation, 0, 0,
                    cardBack.getWidth(), cardBack.getHeight(), false, false);
        }
    }

    private LiveCardPlacement liveHolePlacement(Seat seat, int slot) {
        Texture cardBack = activeCardBack();
        float aspect = cardBack.getHeight() / (float) cardBack.getWidth();
        float width = seat.index == 0 ? LOCAL_HOLE_CARD_WIDTH
                : rivalHoleCardWidth(aspect);
        float height = width * aspect;
        if (seat.index != 0) {
            float centerX = seat.podX + RIVAL_HAND_CENTER_X_INSET;
            // updateSeatPositions clamps the complete seat unit with the
            // active deck's exact aspect ratio. Do not clamp the cards again:
            // doing so detaches them from low rival seats.
            float centerY = seat.y + RIVAL_HAND_VERTICAL_OFFSET;
            float x = centerX + (slot == 0 ? -1f : 1f) * RIVAL_HAND_SIDE_DISTANCE;
            if (slot == 0) {
                x += RIVAL_LEFT_CARD_SHIFT;
            }
            return new LiveCardPlacement(x, centerY, width, height,
                    slot == 0 ? RIVAL_CARD_FAN_ANGLE : -RIVAL_CARD_FAN_ANGLE);
        }
        float towardX = tableCenterX - seat.x;
        float towardY = tableCenterY - seat.y;
        float length = Math.max(1f, (float) Math.sqrt(
                towardX * towardX + towardY * towardY));
        towardX /= length;
        towardY /= length;
        float sideX = -towardY;
        float sideY = towardX;
        float centerX = seat.x + towardX * LOCAL_HOLE_CENTER_DISTANCE;
        float centerY = seat.y + towardY * LOCAL_HOLE_CENTER_DISTANCE;
        float side = (slot == 0 ? 1f : -1f) * 82f;
        return new LiveCardPlacement(centerX + sideX * side,
                centerY + sideY * side, width, height,
                slot == 0 ? LOCAL_CARD_FAN_ANGLE : -LOCAL_CARD_FAN_ANGLE);
    }

    private void drawLiveRestingCard(TableSnapshot.CardSnapshot card,
            LiveCardPlacement placement, Texture cardBack) {
        float alpha = card.disabled() ? DISABLED_CARD_ALPHA : 1f;
        drawLiveRestingCard(card, placement, cardBack, 1f, alpha, false);
    }

    private void drawLiveRestingCard(TableSnapshot.CardSnapshot card,
            LiveCardPlacement placement, Texture cardBack, String nickname,
            int slot, boolean communityCard) {
        float alpha = liveRestingCardAlpha(card, nickname, slot,
                communityCard);
        TableVisualEvent.ShowdownHighlight highlight = liveShowdownHoverNickname == null
                ? null : liveState.showdownHighlight(liveShowdownHoverNickname);
        drawLiveRestingCard(card, placement, cardBack,
                1f, alpha, highlight != null && alpha >= 1f);
    }

    private float liveRestingCardAlpha(TableSnapshot.CardSnapshot card,
            String nickname, int slot, boolean communityCard) {
        if (!card.faceUp()) {
            return card.disabled() ? DISABLED_CARD_ALPHA : 1f;
        }
        TableVisualEvent.ShowdownHighlight highlight = liveShowdownHoverNickname == null
                ? null : liveState.showdownHighlight(liveShowdownHoverNickname);
        Boolean selected;
        if (highlight != null) {
            selected = communityCard
                    ? highlight.communityCardSlots().contains(slot)
                    : highlight.nickname().equals(nickname)
                    && highlight.holeCardSlots().contains(slot);
        } else {
            selected = liveState.showdownCardSelected(nickname, slot,
                    communityCard);
        }
        if (selected != null) {
            return selected ? 1f : DISABLED_CARD_ALPHA;
        }
        return card.disabled() ? DISABLED_CARD_ALPHA : 1f;
    }

    private void drawDisabledHoleCardsLayer(Texture cardBack) {
        boolean hasDisabledCards = false;
        for (TableSnapshot.PlayerSnapshot player : liveState.snapshot().players()) {
            Seat seat = seatByNickname(player.nickname());
            if (seat == null || hasActiveHolePresentation(player.nickname())) {
                continue;
            }
            List<TableSnapshot.CardSnapshot> holeCards
                    = liveState.presentedHoleCards(player.nickname());
            for (int slot = 0; slot < holeCards.size() && slot < 2;
                    slot++) {
                TableSnapshot.CardSnapshot card = holeCards.get(slot);
                if (card.visible() && card.faceUp()
                        && liveRestingCardAlpha(card, player.nickname(), slot,
                                false) < 1f) {
                    hasDisabledCards = true;
                    break;
                }
            }
            if (hasDisabledCards) break;
        }
        if (!hasDisabledCards) return;

        ensureDisabledHoleCardsLayer();
        disabledHoleCardsLayer.begin();
        Gdx.gl.glViewport(0, 0, disabledHoleCardsLayer.getWidth(),
                disabledHoleCardsLayer.getHeight());
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (TableSnapshot.PlayerSnapshot player : liveState.snapshot().players()) {
            Seat seat = seatByNickname(player.nickname());
            if (seat == null || hasActiveHolePresentation(player.nickname())) {
                continue;
            }
            List<TableSnapshot.CardSnapshot> holeCards
                    = liveState.presentedHoleCards(player.nickname());
            for (int slot = 0; slot < holeCards.size() && slot < 2;
                    slot++) {
                TableSnapshot.CardSnapshot card = holeCards.get(slot);
                if (!card.visible() || !card.faceUp()
                        || liveRestingCardAlpha(card, player.nickname(), slot,
                                false) >= 1f) {
                    continue;
                }
                drawLiveRestingCard(card, liveHolePlacement(seat, slot),
                        cardBack, 1f, 1f, false);
            }
        }
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        batch.end();
        disabledHoleCardsLayer.end();

        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        Texture layer = disabledHoleCardsLayer.getColorBufferTexture();
        batch.begin();
        batch.setColor(1f, 1f, 1f, DISABLED_CARD_ALPHA);
        batch.draw(layer, 0f, 0f, viewport.getWorldWidth(),
                viewport.getWorldHeight(), 0, 0, layer.getWidth(),
                layer.getHeight(), false, true);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private boolean hasActiveHolePresentation(String nickname) {
        return liveHoleSwap != null
                && liveHoleSwap.event.nickname().equals(nickname)
                || liveHoleFold != null
                && liveHoleFold.event.nickname().equals(nickname)
                || liveHoleReveal != null
                && liveHoleReveal.event.nickname().equals(nickname);
    }

    private void ensureDisabledHoleCardsLayer() {
        int width = Math.max(1, Gdx.graphics.getBackBufferWidth());
        int height = Math.max(1, Gdx.graphics.getBackBufferHeight());
        if (disabledHoleCardsLayer != null
                && disabledHoleCardsLayer.getWidth() == width
                && disabledHoleCardsLayer.getHeight() == height) {
            return;
        }
        if (disabledHoleCardsLayer != null) disabledHoleCardsLayer.dispose();
        disabledHoleCardsLayer = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        disabledHoleCardsLayer.getColorBufferTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
    }

    private void drawLiveHoleFold(TableSnapshot.PlayerSnapshot player,
            Seat seat, Texture cardBack) {
        float disabled = Interpolation.smooth.apply(liveHoleFold.progress());
        float alpha = MathUtils.lerp(1f, DISABLED_CARD_ALPHA, disabled);
        for (int slot = 0; slot < player.holeCards().size() && slot < 2; slot++) {
            drawLiveRestingCard(player.holeCards().get(slot),
                    liveHolePlacement(seat, slot), cardBack, 1f, alpha, false);
        }
    }

    private void drawLiveRestingCard(TableSnapshot.CardSnapshot card,
            LiveCardPlacement placement, Texture cardBack,
            float tint, float alpha, boolean showdownTint) {
        if (!card.visible()) {
            return;
        }
        batch.setColor(tint, tint, tint, alpha);
        if (card.faceUp() && !card.code().isBlank()) {
            Texture face = liveCardFace(card.code());
            usePerspectiveCardShader(face, MathUtils.PI,
                    placement.height / placement.width);
            setCardShowdownOverlay(showdownTint);
            float canvasWidth = placement.width * 1.5f;
            float canvasHeight = placement.height * 1.5f;
            batch.draw(cardBack, placement.x - canvasWidth / 2f,
                    placement.y - canvasHeight / 2f, canvasWidth / 2f,
                    canvasHeight / 2f, canvasWidth, canvasHeight, 1f, 1f,
                    placement.rotation, 0, 0, cardBack.getWidth(),
                    cardBack.getHeight(), false, false);
        } else {
            useRoundedCardShader();
            batch.draw(cardBack, placement.x - placement.width / 2f,
                    placement.y - placement.height / 2f, placement.width / 2f,
                    placement.height / 2f, placement.width, placement.height,
                    1f, 1f, placement.rotation, 0, 0, cardBack.getWidth(),
                    cardBack.getHeight(), false, false);
        }
    }

    private void drawHoleCards(boolean foregroundRevealFlights) {
        Objects.requireNonNull(liveState,
                "Hole-card rendering requires authoritative live state");
        drawLiveHoleCards(foregroundRevealFlights);
    }

    private void drawLiveCommunityCards(float cx, float cardY, float cardW,
            float cardH, float gap, float firstX, Texture cardBack) {
        // Swing hides the five board cards for the whole shuffle loop, including
        // its text-only fallback.  Keeping the old board visible under the GDX
        // overlay made a recovered/new hand look as if it were reusing cards.
        if (liveShuffle != null) {
            return;
        }
        List<TableSnapshot.CardSnapshot> board = liveState.snapshot().communityCards();
        int revealEnd = liveCommunityReveal == null ? 0
                : liveCommunityReveal.event.firstSlot()
                + liveCommunityReveal.event.cards().size();
        int flightEnd = 0;
        for (LiveCardFlight flight : liveCardFlights) {
            if (!flight.visualFinished()
                    && flight.event instanceof TableVisualEvent.DealCommunityCard deal) {
                flightEnd = Math.max(flightEnd, deal.slot() + 1);
            }
        }
        int presentedBoardEnd = 0;
        for (int slot = 0; slot < board.size() && slot < 5; slot++) {
            if (board.get(slot).visible()) {
                presentedBoardEnd = slot + 1;
            }
        }
        int visibleSlots = Math.min(5,
                Math.max(Math.max(presentedBoardEnd, revealEnd), flightEnd));
        for (int slot = 0; slot < visibleSlots; slot++) {
            float x = firstX + slot * gap + cardW / 2f;
            float y = cardY + cardH / 2f;
            LiveCardPlacement placement = new LiveCardPlacement(
                    x, y, cardW, cardH, 0f);
            if (liveCommunityReveal != null
                    && liveCommunityReveal.containsSlot(slot)) {
                drawLiveCommunityRevealCard(slot, placement, cardBack);
            } else if (slot < board.size() && board.get(slot).visible()
                    && !hasActiveCommunityFlight(slot)) {
                drawLiveRestingCard(board.get(slot), placement, cardBack,
                        "", slot, true);
            }
        }
        for (LiveCardFlight flight : liveCardFlights) {
            if (!(flight.event instanceof TableVisualEvent.DealCommunityCard deal)
                    || flight.visualFinished()) {
                continue;
            }
            int slot = deal.slot();
            float progress = Interpolation.pow2Out.apply(flight.flightProgress());
            float targetX = firstX + slot * gap + cardW / 2f;
            float targetY = cardY + cardH / 2f;
            float controlX = (dealerSourceX + targetX) * 0.5f + (slot - 2f) * 42f;
            float controlY = Math.max(dealerSourceY, targetY) + 150f;
            float x = bezier(dealerSourceX, controlX, targetX, progress);
            float y = bezier(dealerSourceY, controlY, targetY, progress);
            float rotation = MathUtils.lerp(-20f + slot * 10f, 0f, progress);
            useRoundedCardShader();
            batch.setColor(Color.WHITE);
            batch.draw(cardBack, x - cardW / 2f, y - cardH / 2f,
                    cardW / 2f, cardH / 2f, cardW, cardH, 1f, 1f, rotation,
                    0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
        }
    }

    private boolean hasActiveCommunityFlight(int slot) {
        for (LiveCardFlight flight : liveCardFlights) {
            if (!flight.visualFinished()
                    && flight.event instanceof TableVisualEvent.DealCommunityCard deal
                    && deal.slot() == slot) {
                return true;
            }
        }
        return false;
    }

    private void drawLiveCommunityRevealCard(int slot,
            LiveCardPlacement placement, Texture cardBack) {
        int offset = slot - liveCommunityReveal.event.firstSlot();
        float progress = liveCommunityReveal.progress(offset);
        if (progress <= 0f) {
            drawLiveRestingCard(new TableSnapshot.CardSnapshot("", false, false),
                    placement, cardBack);
            return;
        }
        TableSnapshot.CardSnapshot card = liveCommunityReveal.event.cards().get(offset);
        Texture face = liveCardFace(card.code());
        float canvasWidth = placement.width * 1.5f;
        float canvasHeight = placement.height * 1.5f;
        float revealScale = cardFlipScale(progress);
        usePerspectiveCardShader(face, progress * MathUtils.PI,
                placement.height / placement.width);
        batch.setColor(Color.WHITE);
        batch.draw(cardBack, placement.x - canvasWidth / 2f,
                placement.y - canvasHeight / 2f, canvasWidth / 2f,
                canvasHeight / 2f, canvasWidth, canvasHeight,
                revealScale, revealScale, placement.rotation, 0, 0,
                cardBack.getWidth(), cardBack.getHeight(), false, false);
    }

    private void drawCardsAndPot(float cx, float cy, float tableWidth) {
        Objects.requireNonNull(liveState,
                "Board rendering requires authoritative live state");
        Texture cardBack = activeCardBack();
        float cardW = Math.min(COMMUNITY_CARD_MAX_WIDTH, tableWidth / 10f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = cx - gap * 2f - cardW / 2f;
        float cardY = cy - cardH * 0.36f;
        batch.begin();
        drawLiveCommunityCards(cx, cardY, cardW, cardH, gap, firstX, cardBack);
        batch.setShader(null);

        float pulse = 1f + MathUtils.sin(totalTime * 3.3f) * 0.035f;
        float basePotW = 76f;
        float basePotH = basePotW * pot.getHeight() / pot.getWidth();
        float potW = basePotW * pulse;
        float potH = basePotH * pulse;
        double currentPot = Math.max(0d, livePot());
        String currentPotPrefix = liveState.runItTwicePotPrefix().isBlank()
                ? uppercase(gameText.translate("game.bote"))
                : liveState.runItTwicePotPrefix();
        if (Double.compare(currentPot, lastPotValue) != 0
                || !currentPotPrefix.equals(lastPotPrefix)) {
            lastPotValue = currentPot;
            lastPotPrefix = currentPotPrefix;
            potText = currentPotPrefix + " " + formatAmount(currentPot);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        float alpha = 1f;
        float panelWidth = 390f;
        float panelHeight = POT_PANEL_HEIGHT;
        float panelX = potCenterX - panelWidth / 2f;
        float panelY = potCenterY - panelHeight / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float boardWidth = cardW + 4f * gap;
        if (liveState.turnTimerVisible()) {
            drawSharedTurnBar(firstX, cardY - COMMUNITY_TIMER_Y_OFFSET,
                    boardWidth, 0f);
        } else if (liveState.sharedProgressVisible()) {
            drawSharedProgressBar(firstX,
                    cardY - COMMUNITY_TIMER_Y_OFFSET, boardWidth);
        }
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.78f * alpha);
        roundedRect(panelX - 2f, panelY - 2f,
                panelWidth + 4f, panelHeight + 4f, 13f);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.94f * alpha);
        roundedRect(panelX, panelY, panelWidth, panelHeight, 11f);
        shapes.end();

        batch.begin();
        batch.setColor(1f, 1f, 1f, alpha);
        float iconCenterX = panelX + 52f;
        float iconCenterY = panelY + panelHeight / 2f;
        batch.draw(pot, iconCenterX - potW / 2f, iconCenterY - potH / 2f,
                potW, potH);
        float textAreaX = panelX + 98f;
        float textAreaWidth = panelWidth - 112f;
        drawFittedCentered(uiFont, potText,
                textAreaX + textAreaWidth / 2f, panelY + 51f,
                textAreaWidth, POT_GOLD, alpha);
        batch.setColor(Color.WHITE);
        batch.end();

        drawCommunityHud(firstX, cardY - COMMUNITY_HUD_Y_OFFSET,
                boardWidth);
    }

    /** Mirrors Swing's call-cost label: unrevealed board suffix, then river aggressor. */
    private void drawCallCostOverlay(float cx, float cy, float tableWidth) {
        if (liveState == null || liveState.callCostText().isBlank()
                || !tablePreference("mostrar_coste_igualar", true)) {
            return;
        }
        Texture cardBack = activeCardBack();
        float cardW = Math.min(COMMUNITY_CARD_MAX_WIDTH, tableWidth / 10f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = cx - gap * 2f - cardW / 2f;
        float cardY = cy - cardH * 0.36f;
        List<TableSnapshot.CardSnapshot> board = liveState.snapshot().communityCards();
        int firstHidden = -1;
        for (int slot = 0; slot < Math.min(5, board.size()); slot++) {
            TableSnapshot.CardSnapshot card = board.get(slot);
            if (card.visible() && !card.faceUp()) {
                firstHidden = slot;
                break;
            }
        }
        if (firstHidden >= 0) {
            float x = firstX + firstHidden * gap;
            float overlayWidth = cardW + (4 - firstHidden) * gap;
            drawCallCostText(x, cardY, overlayWidth, cardH);
            return;
        }

        Seat aggressor = seatByNickname(liveState.callCostAggressorNickname());
        TableSnapshot.PlayerSnapshot player = aggressor == null
                ? null : livePlayer(aggressor);
        if (aggressor == null || aggressor.index == 0 || player == null
                || !player.active() || player.holeCards().size() < 2
                || player.holeCards().stream().limit(2)
                        .anyMatch(card -> !card.visible() || card.faceUp())) {
            return;
        }
        LiveCardPlacement left = liveHolePlacement(aggressor, 0);
        LiveCardPlacement right = liveHolePlacement(aggressor, 1);
        float minX = Math.min(left.x - left.width / 2f,
                right.x - right.width / 2f);
        float maxX = Math.max(left.x + left.width / 2f,
                right.x + right.width / 2f);
        float minY = Math.min(left.y - left.height / 2f,
                right.y - right.height / 2f);
        float maxY = Math.max(left.y + left.height / 2f,
                right.y + right.height / 2f);
        drawCallCostText(minX, minY, maxX - minX, maxY - minY);
    }

    private void drawCallCostText(float x, float y, float width, float height) {
        batch.begin();
        BitmapFont.BitmapFontData data = callCostFont.getData();
        float oldX = data.scaleX;
        float oldY = data.scaleY;
        float desiredScale = Math.max(0.15f,
                height * 1.35f / callCostFont.getLineHeight());
        data.setScale(oldX * desiredScale, oldY * desiredScale);
        glyph.setText(callCostFont, liveState.callCostText());
        float widthBudget = width * 0.92f;
        if (glyph.width > widthBudget && glyph.width > 0f) {
            float fit = widthBudget / glyph.width;
            data.setScale(data.scaleX * fit, data.scaleY * fit);
            glyph.setText(callCostFont, liveState.callCostText());
        }
        callCostFont.setColor(1f, 1f, 1f, 1f);
        callCostFont.draw(batch, glyph, x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f);
        callCostFont.setColor(Color.WHITE);
        data.setScale(oldX, oldY);
        batch.end();
    }

    /**
     * Keeps the lobby-to-table hand-off visible until the dealer has completed
     * the real seat draw and announced that play is ready. No fake percentage
     * is shown: the moving segment only communicates that work is ongoing.
     */
    private void drawPreparationOverlay() {
        if (intro || preparationPhase
                == TableVisualEvent.PreparationStatus.Phase.READY) {
            return;
        }
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(820f, width - 80f);
        float panelH = 244f;
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float trackX = panelX + 82f;
        float trackY = panelY + 46f;
        float trackW = panelW - 164f;
        float trackH = 16f;
        float segmentW = Math.min(172f, trackW * 0.30f);
        float travel = Math.max(0f, trackW - segmentW - 8f);
        float phase = (totalTime * 0.70f) % 2f;
        float eased = phase <= 1f ? phase : 2f - phase;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.76f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(panelX + 9f, panelY - 10f, panelW, panelH, 18f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.90f);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f,
                panelH + 4f, 18f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.98f);
        roundedRect(panelX, panelY, panelW, panelH, 16f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.92f);
        shapes.rect(panelX + 26f, panelY + panelH - 10f,
                panelW - 52f, 3f);
        shapes.setColor(new Color(0x020813ff));
        roundedRect(trackX, trackY, trackW, trackH, 8f);
        shapes.setColor(BUTTON_LINE);
        roundedRect(trackX + 2f, trackY + 2f, trackW - 4f,
                trackH - 4f, 6f);
        shapes.setColor(CYAN);
        roundedRect(trackX + 4f + travel * eased, trackY + 4f,
                segmentW, trackH - 8f, 4f);
        shapes.end();

        batch.begin();
        drawFittedCenteredInBox(actionFont,
                uppercase(gameText.translate("gdx.lobby.preparing_table")),
                panelX + 44f, panelY + 142f, panelW - 88f, 58f,
                POT_GOLD, 1f);
        drawFittedCenteredInBox(uiFont,
                preparationStatusText(preparationPhase, gameText),
                panelX + 44f, panelY + 91f, panelW - 88f, 42f,
                Color.WHITE, 1f);
        batch.end();
    }

    static String preparationStatusText(
            TableVisualEvent.PreparationStatus.Phase phase,
            GdxGameText gameText) {
        return switch (Objects.requireNonNull(phase, "phase")) {
            case STARTING_DEALER -> gameText.translate(
                    "gdx.table.preparation.starting_dealer");
            case DRAWING_SEATS -> gameText.translate("ui.sorteando_sitios");
            case READY -> gameText.translate("gdx.table.preparation.ready");
        };
    }

    /** Swing-compatible user/forced lights overlay and full-width pause banner. */
    private void drawPauseOverlay() {
        if (liveState == null || intro) {
            return;
        }
        boolean paused = liveState.snapshot().paused();
        if (!paused && !userLightsOff) return;
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        int lightLevel = 50;
        if (preferences != null) {
            try {
                lightLevel = Integer.parseInt(preferences.properties()
                        .getProperty("nivel_luz", "50"));
            } catch (NumberFormatException ignored) {
                lightLevel = 50;
            }
        }
        lightLevel = MathUtils.clamp(lightLevel, 10, 90);
        float bannerHeight = pauseBannerHeight();
        float bannerY = pauseBannerY();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, (100 - lightLevel) / 100f);
        shapes.rect(0f, 0f, width, height);
        if (paused) {
            shapes.setColor(1f, 1f, 1f, 0.95f);
            shapes.rect(0f, bannerY, width, bannerHeight);
        }
        shapes.end();

        if (!paused) return;

        if (((long) totalTime & 1L) != 0L) {
            return;
        }
        String text = uppercase(gameText.translate("game.timba_pausada"));
        BitmapFont.BitmapFontData data = pauseFont.getData();
        float oldX = data.scaleX;
        float oldY = data.scaleY;
        float scale = Math.max(0.20f,
                bannerHeight * 0.68f / pauseFont.getLineHeight());
        data.setScale(oldX * scale, oldY * scale);
        glyph.setText(pauseFont, text);
        float iconSize = bannerHeight * 0.60f;
        float iconGap = Math.max(12f, iconSize * 0.20f);
        float groupWidth = iconSize + iconGap + glyph.width;
        float maxWidth = width * 0.90f;
        if (groupWidth > maxWidth) {
            float fit = maxWidth / groupWidth;
            data.setScale(data.scaleX * fit, data.scaleY * fit);
            glyph.setText(pauseFont, text);
            iconSize *= fit;
            iconGap *= fit;
            groupWidth = iconSize + iconGap + glyph.width;
        }
        float groupX = (width - groupWidth) / 2f;
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(pauseIcon, groupX,
                bannerY + (bannerHeight - iconSize) / 2f,
                iconSize, iconSize);
        pauseFont.setColor(PAUSE_RED);
        pauseFont.draw(batch, glyph, groupX + iconSize + iconGap,
                bannerY + (bannerHeight + glyph.height) / 2f);
        pauseFont.setColor(Color.WHITE);
        batch.end();
        data.setScale(oldX, oldY);
    }

    private float pauseBannerHeight() {
        return viewport.getWorldHeight() * 0.14f;
    }

    private float pauseBannerY() {
        return (viewport.getWorldHeight() - pauseBannerHeight()) / 2f;
    }

    /** Visible acknowledgement of the real dealer/network shutdown path. */
    private void drawTerminationOverlay() {
        if (!terminationRequested || finalSummary != null || intro) return;
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(760f, width - 80f);
        float panelH = 150f;
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        Color accent = recoverableTerminationRequested ? POT_GOLD : ORANGE;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        roundedRect(panelX + 9f, panelY - 10f, panelW, panelH, 18f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.92f);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f,
                panelH + 4f, 18f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.98f);
        roundedRect(panelX, panelY, panelW, panelH, 16f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.85f);
        shapes.rect(panelX + 24f, panelY + panelH - 9f,
                panelW - 48f, 3f);
        shapes.end();

        batch.begin();
        drawFittedCenteredInBox(actionFont,
                recoverableTerminationRequested
                        ? uppercase(gameText.translate(
                                "gdx.stopping_and_saving"))
                        : uppercase(gameText.translate(
                                "gdx.leaving_game")),
                panelX + 34f, panelY + 46f, panelW - 68f, 66f,
                Color.WHITE, 1f);
        batch.end();
    }

    private void updateRecoveryStopTransition() {
        if (recoveryStopBarrier == null || totalTime < recoveryStopUntil) {
            return;
        }
        CompletableFuture<Void> barrier = recoveryStopBarrier;
        recoveryStopBarrier = null;
        barrier.complete(null);
    }

    /** Swing's five-second SERVEREXITRECOVER notice with its timeout bar. */
    private void drawRecoveryStopOverlay() {
        if (recoveryStopBarrier == null || intro) return;
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(820f, width - 80f);
        float panelH = 190f;
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float remaining = MathUtils.clamp(
                (recoveryStopUntil - totalTime)
                        / RECOVERY_STOP_NOTICE_SECONDS,
                0f, 1f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.76f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(panelX + 9f, panelY - 10f, panelW, panelH, 18f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.94f);
        roundedRect(panelX - 2f, panelY - 2f,
                panelW + 4f, panelH + 4f, 18f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.99f);
        roundedRect(panelX, panelY, panelW, panelH, 16f);
        shapes.setColor(0.10f, 0.15f, 0.22f, 1f);
        roundedRect(panelX + 34f, panelY + 24f,
                panelW - 68f, 12f, 6f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 1f);
        roundedRect(panelX + 34f, panelY + 24f,
                (panelW - 68f) * remaining, 12f, 6f);
        shapes.end();

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(fastButtonIcons[7], panelX + 36f,
                panelY + 75f, 58f, 58f);
        drawFittedCenteredInBox(actionFont,
                uppercase(gameText.translate(
                        "conn.el_servidor_ha_detenido_la")),
                panelX + 112f, panelY + 65f,
                panelW - 146f, 86f, Color.WHITE, 1f);
        batch.end();
    }

    /**
     * Client reconnection is driven entirely by NetworkLobbyGateway.  This
     * overlay is only an input shield and status projection: it has no timer,
     * retry loop or completion authority of its own.
     */
    private void drawNetworkReconnectOverlay() {
        if (!isClientTransportReconnecting() || terminationRequested
                || finalSummary != null || intro) {
            return;
        }
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(860f, width - 80f);
        float panelH = 190f;
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float pulse = 0.72f + 0.20f * MathUtils.sin(totalTime * 4f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.76f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(panelX + 9f, panelY - 10f, panelW, panelH, 18f);
        shapes.setColor(LATENCY_ORANGE.r, LATENCY_ORANGE.g,
                LATENCY_ORANGE.b, pulse);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f,
                panelH + 4f, 18f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.99f);
        roundedRect(panelX, panelY, panelW, panelH, 16f);
        shapes.end();

        batch.begin();
        drawFittedCenteredInBox(actionFont,
                gameText.translate("conn.reconectando_con_el_servidor"),
                panelX + 36f, panelY + 91f, panelW - 72f, 62f,
                Color.WHITE, 1f);
        drawFittedCenteredInBox(uiFont,
                gameText.translate("table.reconnect_wait"),
                panelX + 58f, panelY + 35f, panelW - 116f, 44f,
                new Color(0xc8d4e2ff), 1f);
        batch.end();
    }

    /** Compact equivalent of Swing's CommunityCardsPanel, fed by the dealer. */
    private void drawCommunityHud(float x, float y, float width) {
        // The canonical local cards occupy the lower centre. This compact bar
        // fits in the measured safe gap below the turn timer without touching
        // either those cards or the community row at any supported viewport.
        float height = COMMUNITY_HUD_HEIGHT;
        float padding = 6f;
        float soundWidth = 46f;
        float lightsWidth = 54f;
        float pauseWidth = Math.min(166f, width * 0.25f);
        float handWidth = Math.min(150f, width * 0.22f);
        float blindsWidth = width - pauseWidth - handWidth - soundWidth
                - lightsWidth - padding * 4f;
        communityPauseX = x + width - pauseWidth;
        communityPauseY = y;
        communityPauseWidth = pauseWidth;
        communityPauseHeight = height;
        communityLightsX = communityPauseX - padding - lightsWidth;
        communityLightsY = y;
        communityLightsWidth = lightsWidth;
        communityLightsHeight = height;
        communitySoundX = communityLightsX - padding - soundWidth;
        communitySoundY = y;
        communitySoundWidth = soundWidth;
        communitySoundHeight = height;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        boolean pauseHover = contains(pointer.x, pointer.y,
                communityPauseX, communityPauseY,
                communityPauseWidth, communityPauseHeight);
        boolean soundHover = contains(pointer.x, pointer.y,
                communitySoundX, communitySoundY,
                communitySoundWidth, communitySoundHeight);
        boolean paused = liveState.snapshot().paused();
        boolean lightsHover = !paused && contains(pointer.x, pointer.y,
                communityLightsX, communityLightsY,
                communityLightsWidth, communityLightsHeight);
        Color pauseColor = paused ? ORANGE : SEAT_RIM;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.52f);
        roundedRect(x + 3f, y - 4f, width, height, 10f);
        shapes.setColor(SEAT_RIM.r, SEAT_RIM.g, SEAT_RIM.b, 0.72f);
        roundedRect(x - 2f, y - 2f, width + 4f, height + 4f, 11f);
        // Smoked glass: the felt remains visible through the HUD.
        shapes.setColor(0.012f, 0.040f, 0.052f, 0.78f);
        roundedRect(x, y, width, height, 9f);
        shapes.setColor(1f, 1f, 1f, 0.10f);
        roundedRect(x + 5f, y + height - 10f, width - 10f, 6f, 3f);

        float handX = x + blindsWidth + padding;
        communityHandX = handX;
        communityHandY = y;
        communityHandWidth = handWidth;
        communityHandHeight = height;
        boolean handHover = tableHost && contains(pointer.x, pointer.y,
                communityHandX, communityHandY,
                communityHandWidth, communityHandHeight);
        if (liveState.lastHand() || handHover) {
            Color handAccent = liveState.lastHand() ? POT_GOLD : CYAN;
            shapes.setColor(handAccent.r, handAccent.g, handAccent.b,
                    liveState.lastHand() ? 0.34f : 0.16f);
            roundedRect(handX + 3f, y + 3f,
                    handWidth - 7f, height - 6f, 7f);
        }
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b, 0.76f);
        shapes.rect(handX - padding / 2f, y + 6f, 2f, height - 12f);
        shapes.rect(communitySoundX - padding / 2f,
                y + 6f, 2f, height - 12f);
        shapes.rect(communityLightsX - padding / 2f,
                y + 6f, 2f, height - 12f);
        shapes.rect(communityPauseX - padding / 2f,
                y + 6f, 2f, height - 12f);
        drawHudActionSurface(communitySoundX, communitySoundY,
                communitySoundWidth, communitySoundHeight,
                SEAT_RIM, soundHover, false, true);
        drawHudActionSurface(communityLightsX, communityLightsY,
                communityLightsWidth, communityLightsHeight,
                userLightsOff ? ORANGE : SEAT_RIM, lightsHover,
                userLightsOff, !paused);
        drawHudActionSurface(communityPauseX, communityPauseY,
                communityPauseWidth, communityPauseHeight,
                pauseColor, pauseHover, paused, true);

        float iconX = communityPauseX + 26f;
        float iconY = communityPauseY + height / 2f;
        shapes.setColor(paused ? Color.WHITE : pauseColor);
        if (paused) {
            shapes.triangle(iconX - 6f, iconY - 8f,
                    iconX - 6f, iconY + 8f, iconX + 8f, iconY);
        } else {
            shapes.rect(iconX - 8f, iconY - 8f, 5f, 16f);
            shapes.rect(iconX + 3f, iconY - 8f, 5f, 16f);
        }
        shapes.end();

        String blindsLabel = uppercase(gameText.translate(
                "blinds.ciegas_titulo"));
        String blinds = liveState.smallBlind() > 0d
                && liveState.bigBlind() > 0d
                ? blindsLabel + "  " + formatAmount(liveState.smallBlind())
                + " / " + formatAmount(liveState.bigBlind())
                : blindsLabel + "  —";
        if (tablePreference("show_time", false)) {
            blinds += "   " + formatPlayTime(liveState.playTimeSeconds());
        }
        String hand;
        if (liveState.lastHand()) {
            hand = uppercase(gameText.translate("game.ultima_mano"));
        } else if (liveState.handNumber() > 0
                && liveState.maximumHands() > 0) {
            hand = uppercase(gameText.translate("game.mano_2")) + "  "
                    + liveState.handNumber()
                    + " / " + liveState.maximumHands();
        } else {
            hand = liveState.handNumber() > 0
                    ? uppercase(gameText.translate("game.mano_2")) + "  "
                            + liveState.handNumber()
                    : uppercase(gameText.translate("game.mano_2")) + "  —";
        }
        batch.begin();
        drawFittedCenteredInBox(actionFont, blinds,
                x + 10f, y + 5f, blindsWidth - 20f, height - 10f,
                POT_GOLD, 1f);
        drawFittedCenteredInBox(actionFont, hand,
                handX + 4f, y + 5f, handWidth - 12f, height - 10f,
                liveState.lastHand() ? POT_GOLD : Color.WHITE, 1f);
        Texture masterSoundIcon = audioControl.enabled() ? soundIcon : muteIcon;
        batch.setColor(Color.WHITE);
        float soundIconSize = height - 4f;
        batch.draw(masterSoundIcon,
                communitySoundX + (communitySoundWidth - soundIconSize) / 2f,
                communitySoundY + 2f, soundIconSize, soundIconSize);
        Texture lightsIcon = userLightsOff ? lightsOffIcon : lightsOnIcon;
        float lightsAspect = lightsIcon.getWidth()
                / (float) lightsIcon.getHeight();
        float lightsIconHeight = Math.min(height - 12f, 24f);
        float lightsIconWidth = Math.min(lightsWidth - 10f,
                lightsIconHeight * lightsAspect);
        batch.setColor(paused ? 0.45f : 1f, paused ? 0.50f : 1f,
                paused ? 0.56f : 1f, 1f);
        batch.draw(lightsIcon,
                communityLightsX + (communityLightsWidth - lightsIconWidth) / 2f,
                communityLightsY + (height - lightsIconHeight) / 2f,
                lightsIconWidth, lightsIconHeight);
        batch.setColor(Color.WHITE);
        drawFittedCenteredInBox(actionFont,
                uppercase(gameText.translate(paused
                        ? "gdx.table.resume" : "gdx.table.pause")),
                communityPauseX + 46f, communityPauseY + 5f,
                communityPauseWidth - 54f, communityPauseHeight - 10f,
                paused ? Color.WHITE : Color.BLACK, 1f);
        batch.end();
    }

    private void useRoundedCardShader() {
        Texture cardBack = activeCardBack();
        configureCardShader(cardBack, 0f, false, cardBack.getHeight() / (float) cardBack.getWidth());
    }

    private void usePerspectiveCardShader(Texture front, float angle, float aspect) {
        configureCardShader(front, angle, true, aspect);
    }

    private void useIntroPerspectiveCardShader(Texture front, float angle,
            float aspect) {
        configureCardShader(front, defaultCardBack, angle, true, aspect);
    }

    private void configureCardShader(Texture front, float angle, boolean perspective, float aspect) {
        configureCardShader(front, activeCardBack(), angle, perspective, aspect);
    }

    private void configureCardShader(Texture front, Texture cardBack,
            float angle, boolean perspective, float aspect) {
        Objects.requireNonNull(front, "Card shader requires a front texture");
        Objects.requireNonNull(cardBack, "Card shader requires a back texture");
        batch.flush();
        batch.setShader(roundedCardShader);
        batch.flush();
        front.bind(1);
        cardBack.bind(0);
        roundedCardShader.setUniformi("u_texture", 0);
        roundedCardShader.setUniformi("u_frontTexture", 1);
        roundedCardShader.setUniformf("u_cornerRadius", CARD_CORNER_RADIUS);
        roundedCardShader.setUniformf("u_edgeSoftness", CARD_EDGE_SOFTNESS);
        roundedCardShader.setUniformf("u_perspective", perspective ? 1f : 0f);
        roundedCardShader.setUniformf("u_flipAngle", angle);
        roundedCardShader.setUniformf("u_cardAspect", aspect);
        roundedCardShader.setUniformf("u_overlay", 1f, 0.925f, 0f, 0f);
    }

    private void setCardShowdownOverlay(boolean highlighted) {
        roundedCardShader.setUniformf("u_overlay", 1f, 0.925f, 0f,
                highlighted ? 0.31f : 0f);
    }

    private Texture activeCardBack() {
        Objects.requireNonNull(liveState,
                "Card-back selection requires authoritative live state");
        String deck = presentationSettings == null
                ? availableDeck(liveDeck)
                : availableDeck(presentationSettings.cardBackDeck());
        if ("goliat".equals(deck)) {
            return defaultCardBack;
        }
        return liveCardBacks.computeIfAbsent(deck,
                value -> deckCardTexture(value, "trasera.jpg"));
    }

    private int dealerSeat() {
        Objects.requireNonNull(liveState,
                "Dealer lookup requires authoritative live state");
        int seat = liveSeatWithPosition(TableSnapshot.Position.DEALER,
                TableSnapshot.Position.DEAD_DEALER,
                TableSnapshot.Position.DEALER_STRADDLE);
        if (seat >= 0) {
            return seat;
        }
        // Heads-up stores the shared dealer/small-blind role as SMALL_BLIND.
        // The dealer button is intentionally suppressed, but dealing must still
        // originate from the real dealer rather than an invented seat.
        if (livePlayerCount() == 2) {
            seat = liveSeatWithPosition(TableSnapshot.Position.SMALL_BLIND);
            if (seat >= 0) {
                return seat;
            }
        }
        return -1;
    }

    private String availableDeck(String deck) {
        if (deck == null) return "goliat";
        if (presentationSettings != null) {
            for (String candidate : presentationSettings.availableDecks()) {
                if (candidate.equalsIgnoreCase(deck)) return candidate;
            }
        }
        return GdxGamePresentationSettings.OFFICIAL_DECKS.stream()
                .filter(candidate -> candidate.equalsIgnoreCase(deck))
                .findFirst().orElse("goliat");
    }

    private int liveSeatWithPosition(TableSnapshot.Position... positions) {
        List<TableSnapshot.PlayerSnapshot> players = liveState.snapshot().players();
        for (int seat = 0; seat < seats.length; seat++) {
            TableSnapshot.PlayerSnapshot player = livePlayer(seats[seat]);
            if (player == null) {
                continue;
            }
            for (TableSnapshot.Position position : positions) {
                if (player.position() == position) {
                    return seat;
                }
            }
        }
        return -1;
    }

    private int livePlayerCount() {
        return visibleSeatCount(liveState.snapshot());
    }

    static int visibleSeatCount(TableSnapshot snapshot) {
        return Math.min(SEAT_COUNT, visibleSeatPlayers(snapshot).size());
    }

    private void drawPositionChips() {
        Objects.requireNonNull(liveState,
                "Position-chip rendering requires authoritative live state");
        drawLivePositionChips();
    }

    private void updateLivePositionRotation() {
        LivePositionRotation active = livePositionRotation;
        if (active == null || active.progress() < 1f) {
            return;
        }
        try {
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            livePositionRotation = null;
        }
    }

    private void updateLiveActionChip() {
        boolean completedAny = false;
        for (int index = liveActionChips.size() - 1; index >= 0; index--) {
            LiveActionChip active = liveActionChips.get(index);
            float elapsed = active.elapsedSeconds();
            // The visible GDX chip has a long, staggered flight. Anchor the
            // payment cue to first contact with the pot; playing it on launch
            // makes the sound visibly lead the impact by almost a second.
            if (!active.soundPlayed
                    && actionChipSoundDue(
                            active.chips.get(0).progress(elapsed))) {
                active.soundPlayed = true;
                if (active.event.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
                    if (liveBetSoundEnabled()) {
                        play(betSound, 0.48f,
                                0.98f + active.chips.get(0).color * 0.02f);
                    }
                } else {
                    playPlayerActionSoundForAcceptedEvent(active.event,
                            active.chips.get(0).color);
                }
            }
            if (!active.complete(elapsed)) continue;
            livePotContributions.merge(active.event.nickname(),
                    active.event.contributionDelta(), Double::sum);
            liveActionChips.remove(index);
            completedAny = true;
        }
        if (completedAny) {
            syncSeatsFromLiveState();
        }
        if (liveActionChips.isEmpty() && pendingCollectBets != null) {
            PendingCollectBets pending = pendingCollectBets;
            pendingCollectBets = null;
            liveChipBatch = new LiveChipBatch(pending.event,
                    System.nanoTime(), pending.barrier,
                    pending.presentationSnapshot,
                    livePotContributions);
        }
    }

    private void drawLivePositionChips() {
        LivePositionRotation active = livePositionRotation;
        batch.begin();
        if (active == null) {
            for (TableSnapshot.PlayerSnapshot player : liveState.snapshot().players()) {
                Texture texture = positionTexture(player.position());
                Seat seat = seatByNickname(player.nickname());
                if (texture != null && seat != null) {
                    drawPositionTexture(texture, seat.positionX, seat.positionY,
                            POSITION_CHIP_SIZE, 0f);
                }
            }
            batch.end();
            return;
        }

        float raw = active.progress();
        float progress = Interpolation.smoother.apply(raw);
        for (int role = 0; role < active.event.transfers().size(); role++) {
            TableVisualEvent.PositionTransfer transfer =
                    active.event.transfers().get(role);
            Texture texture = positionTexture(transfer.position());
            Seat target = seatByNickname(transfer.toNickname());
            if (texture == null || target == null) {
                continue;
            }
            Seat previous = seatByNickname(transfer.fromNickname());
            float sourceX = transfer.fromCenter() || previous == null
                    ? tableCenterX + (role - 1) * 34f : previous.positionX;
            float sourceY = transfer.fromCenter() || previous == null
                    ? tableCenterY + 48f : previous.positionY;
            float middleX = (sourceX + target.positionX) * 0.5f;
            float middleY = (sourceY + target.positionY) * 0.5f;
            float inwardX = tableCenterX - middleX;
            float inwardY = tableCenterY - middleY;
            float inwardLength = Math.max(1f,
                    (float) Math.sqrt(inwardX * inwardX + inwardY * inwardY));
            float controlX = middleX + inwardX / inwardLength * 92f;
            float controlY = middleY + inwardY / inwardLength * 92f + 34f;
            float x = bezier(sourceX, controlX, target.positionX, progress);
            float y = bezier(sourceY, controlY, target.positionY, progress);
            float size = POSITION_CHIP_SIZE
                    * (transfer.fromCenter() ? 0.62f + progress * 0.38f : 1f);
            drawPositionTexture(texture, x, y, size,
                    (1f - progress) * (role - 1) * 320f);
        }
        batch.end();
    }

    private Texture positionTexture(TableSnapshot.Position position) {
        return switch (positionChipKind(position)) {
            case DEALER -> dealerChip;
            case SMALL_BLIND -> smallBlindChip;
            case BIG_BLIND -> bigBlindChip;
            case STRADDLE -> logStraddleIcon;
            case DEALER_STRADDLE -> logDealerStraddleIcon;
            case NONE -> null;
        };
    }

    static PositionChipKind positionChipKind(TableSnapshot.Position position) {
        return switch (position) {
            case DEALER, DEAD_DEALER -> PositionChipKind.DEALER;
            case SMALL_BLIND -> PositionChipKind.SMALL_BLIND;
            case BIG_BLIND -> PositionChipKind.BIG_BLIND;
            case STRADDLE -> PositionChipKind.STRADDLE;
            case DEALER_STRADDLE -> PositionChipKind.DEALER_STRADDLE;
            default -> PositionChipKind.NONE;
        };
    }

    enum PositionChipKind {
        NONE, DEALER, SMALL_BLIND, BIG_BLIND, STRADDLE, DEALER_STRADDLE
    }

    private Seat seatByNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        for (Seat seat : seats) {
            if (nickname.equals(seat.name)) {
                return seat;
            }
        }
        return null;
    }

    private void drawPositionTexture(Texture texture, float x, float y,
            float size, float rotation) {
        batch.setColor(Color.WHITE);
        batch.draw(texture, x - size / 2f, y - size / 2f,
                size / 2f, size / 2f, size, size, 1f, 1f, rotation,
                0, 0, texture.getWidth(), texture.getHeight(), false, false);
    }

    private void play(Sound sound, float volume, float pitch) {
        if (voiceOpening || voiceLive || voiceStopping
                || chatTextToSpeechDucking
                || !audioControl.enabled()
                || !tablePreference("sonido_efectos", true)) {
            return;
        }
        sound.play(volume * effectsVolume, pitch, 0f);
    }

    private void playSwitchSound(boolean enabled) {
        if (!tablePreference("sonido_interruptor", true)) return;
        play(enabled ? buttonOnSound : buttonOffSound, 0.60f, 1f);
    }

    private void playPreferenceSound(String resource, String preferenceKey,
            float volume) {
        if (!tablePreference(preferenceKey, true)
                || failedPreferenceSoundCues.contains(resource)) return;
        FileHandle file = gameAudioResource(resource);
        if (file == null) return;
        try {
            Sound sound = liveAudioCueSounds.computeIfAbsent(resource,
                    ignored -> Gdx.audio.newSound(file));
            play(sound, volume, 1f);
        } catch (RuntimeException failure) {
            failedPreferenceSoundCues.add(resource);
            LOGGER.log(Level.WARNING,
                    "GDX optional sound could not be loaded: " + resource,
                    failure);
        }
    }

    CompletionStage<Void> playGameOverAudio(
            GdxGameDecisionSink.GameOverAudioCue cue) {
        Objects.requireNonNull(cue, "cue");
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (disposed) {
            result.complete(null);
            return result;
        }
        switch (cue) {
            case OPEN -> openGameOverAudio(result);
            case CONTINUE -> continueGameOverAudio(result);
            case SPECTATOR -> finishGameOverAudio(result);
        }
        return result;
    }

    private void openGameOverAudio(CompletableFuture<Void> result) {
        if (!gameOverCinematicsEnabled()) {
            result.complete(null);
            return;
        }
        if (gameOverAudioActive) {
            stopAudioCue("misc/game_over.wav");
            restoreGameOverLoopDucking();
        }
        gameOverAudioActive = true;
        gameOverPreviousLoopsMuted = liveAudioLoopsMuted;
        liveAudioLoopsMuted = true;
        refreshDealerLoopVolumes();
        playAudioCue("misc/game_over.wav", false, true,
                !gameOverSoundEnabled(), result);
    }

    private void continueGameOverAudio(CompletableFuture<Void> result) {
        boolean wasActive = gameOverAudioActive;
        stopAudioCue("misc/game_over.wav");
        restoreGameOverLoopDucking();
        if (!wasActive) {
            result.complete(null);
            return;
        }
        // Swing starts rebuy.wav without making the mandatory amount dialog
        // wait for it. Preserve that exact non-blocking ordering.
        playAudioCue("misc/rebuy.wav", false, true,
                !gameOverSoundEnabled(), result);
    }

    private void finishGameOverAudio(CompletableFuture<Void> result) {
        boolean wasActive = gameOverAudioActive;
        stopAudioCue("misc/game_over.wav");
        if (!wasActive) {
            result.complete(null);
            return;
        }
        CompletableFuture<Void> audio = new CompletableFuture<>();
        audio.whenComplete((ignored, failure) -> {
            restoreGameOverLoopDucking();
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        });
        // This cue is part of Swing's dealer timeline.  A disabled preference
        // therefore means volume zero, not skipping the WAV and releasing the
        // game-over decision early.
        playAudioCue("misc/nocontinue.wav", true, true,
                !gameOverSoundEnabled(), audio);
    }

    private boolean gameOverCinematicsEnabled() {
        return presentationSettings == null
                ? tablePreference("cinematicas", true)
                        && tablePreference("cinematicas_gameover", true)
                : presentationSettings.gameOverCinematics();
    }

    private boolean gameOverSoundEnabled() {
        return audioControl.enabled()
                && tablePreference("sonido_efectos", true)
                && tablePreference("sonido_fin_partida", true);
    }

    private void restoreGameOverLoopDucking() {
        if (!gameOverAudioActive) return;
        gameOverAudioActive = false;
        liveAudioLoopsMuted = gameOverPreviousLoopsMuted;
        refreshDealerLoopVolumes();
    }

    private void playSpecialCardSound(String cardCode) {
        if (presentationSettings == null || !presentationSettings.sillySounds()) {
            return;
        }
        String resource = "decks/" + availableDeck(liveDeck) + "/"
                + cardCode + ".wav";
        FileHandle file = gameAudioResource(resource);
        if (file == null) return;
        Sound special = liveAudioCueSounds.computeIfAbsent(resource,
                ignored -> Gdx.audio.newSound(file));
        play(special, 0.86f, 1f);
    }

    private void acceptAudioCue(TableVisualEvent.AudioCue cue,
            CompletableFuture<Void> barrier) {
        try {
            switch (cue.operation()) {
                case PLAY -> playAudioCue(cue, barrier);
                case STOP -> {
                    stopAudioCue(cue.resource());
                    barrier.complete(null);
                }
                case PLAY_LOOP -> {
                    playAudioLoop(cue.resource());
                    barrier.complete(null);
                }
                case STOP_LOOP -> {
                    stopAudioLoop(cue.resource());
                    barrier.complete(null);
                }
                case START_DANGER_LOOP -> {
                    startDangerAudioLoop(cue.resource());
                    barrier.complete(null);
                }
                case STOP_DANGER_LOOP -> {
                    stopDangerAudioLoop();
                    barrier.complete(null);
                }
                case MUTE_LOOPS -> {
                    liveAudioLoopsMuted = true;
                    refreshDealerLoopVolumes();
                    barrier.complete(null);
                }
                case UNMUTE_LOOPS -> {
                    liveAudioLoopsMuted = false;
                    refreshDealerLoopVolumes();
                    barrier.complete(null);
                }
            }
        } catch (Throwable failure) {
            barrier.completeExceptionally(failure);
        }
    }

    private void playAudioCue(TableVisualEvent.AudioCue cue,
            CompletableFuture<Void> barrier) {
        playAudioCue(cue.resource(), cue.waitForCompletion(),
                cue.forceClose(), cue.forceSilent(), barrier);
    }

    private void playAudioCue(String resource, boolean waitForCompletion,
            boolean forceClose, boolean forceSilent,
            CompletableFuture<Void> barrier) {
        boolean audible = !forceSilent && audioControl.enabled()
                && tablePreference("sonido_efectos", true)
                && !voiceOpening && !voiceLive && !voiceStopping
                && !chatTextToSpeechDucking;
        FileHandle file = gameAudioResource(resource);
        if (forceClose) stopAudioCue(resource);
        if (file == null) {
            barrier.complete(null);
            return;
        }
        if (!waitForCompletion) {
            if (!audible) {
                barrier.complete(null);
                return;
            }
            Sound sound = liveAudioCueSounds.computeIfAbsent(resource,
                    ignored -> Gdx.audio.newSound(file));
            sound.play(effectsVolume);
            barrier.complete(null);
            return;
        }
        // A wait-for-completion cue is part of the dealer's causal timeline.
        // Muting controls audibility only: skipping the playback would make
        // GDX release the dealer early (notably the timeout siren) and let the
        // next action overtake the same flow that Swing waits for.
        Music playback = Gdx.audio.newMusic(file);
        LiveAudioPlayback active = new LiveAudioPlayback(resource,
                playback, barrier);
        liveAudioCueWaits.add(active);
        playback.setVolume(audible ? effectsVolume : 0f);
        playback.setOnCompletionListener(completed ->
                finishAudioCue(active, null));
        try {
            playback.play();
        } catch (Throwable failure) {
            finishAudioCue(active, failure);
        }
    }

    private void finishAudioCue(LiveAudioPlayback active, Throwable failure) {
        if (!liveAudioCueWaits.remove(active)) return;
        try {
            active.music.stop();
            active.music.dispose();
        } finally {
            if (failure == null) active.barrier.complete(null);
            else active.barrier.completeExceptionally(failure);
        }
    }

    private void stopAudioCue(String resource) {
        Sound sound = liveAudioCueSounds.get(resource);
        if (sound != null) sound.stop();
        for (LiveAudioPlayback active : List.copyOf(liveAudioCueWaits)) {
            if (active.resource.equals(resource)) finishAudioCue(active, null);
        }
    }

    private void playAudioLoop(String resource) {
        if ("misc/background_music.mp3".equals(resource)) {
            if (!backgroundMusic.isPlaying()) backgroundMusic.play();
            refreshDealerLoopVolumes();
            return;
        }
        FileHandle file = gameAudioResource(resource);
        if (file == null) return;
        Music loop = liveAudioCueLoops.computeIfAbsent(resource, ignored -> {
            Music created = Gdx.audio.newMusic(file);
            created.setLooping(true);
            return created;
        });
        if (!loop.isPlaying()) loop.play();
        refreshDealerLoopVolumes();
    }

    private void stopAudioLoop(String resource) {
        if ("misc/background_music.mp3".equals(resource)) {
            backgroundMusic.stop();
            return;
        }
        Music loop = liveAudioCueLoops.get(resource);
        if (loop != null) loop.stop();
    }

    private void refreshDealerLoopVolumes() {
        float volume = audioControl.enabled() && !liveAudioLoopsMuted
                && tablePreference("sonido_ascensor", true)
                ? musicVolume * (chatTextToSpeechDucking ? 0.30f : 1f) : 0f;
        backgroundMusic.setVolume(volume);
        for (Music loop : liveAudioCueLoops.values()) loop.setVolume(volume);
        for (LiveAudioPlayback active : liveAudioCueWaits) {
            active.music.setVolume(audioControl.enabled()
                    && tablePreference("sonido_efectos", true)
                    && !chatTextToSpeechDucking
                    ? effectsVolume : 0f);
        }
        if (liveDangerAlertSound != null && liveDangerAlertSoundId >= 0L) {
            liveDangerAlertSound.setVolume(liveDangerAlertSoundId,
                    audioControl.enabled()
                    && tablePreference("sonido_efectos", true)
                    && !chatTextToSpeechDucking ? effectsVolume : 0f);
        }
    }

    private void setChatTextToSpeechDucking(boolean active) {
        chatTextToSpeechDucking = active;
        if (backgroundMusic != null) refreshDealerLoopVolumes();
    }

    private void startDangerAudioLoop(String resource) {
        stopDangerAudioLoop();
        FileHandle file = gameAudioResource(resource);
        if (file == null || !audioControl.enabled()
                || !tablePreference("sonido_efectos", true)) return;
        liveDangerAlertSound = Gdx.audio.newSound(file);
        liveDangerAlertSoundId = liveDangerAlertSound.loop(
                chatTextToSpeechDucking ? 0f : effectsVolume);
    }

    private void stopDangerAudioLoop() {
        if (liveDangerAlertSound == null) return;
        if (liveDangerAlertSoundId >= 0L) {
            liveDangerAlertSound.stop(liveDangerAlertSoundId);
        }
        liveDangerAlertSound.dispose();
        liveDangerAlertSound = null;
        liveDangerAlertSoundId = -1L;
    }

    private void startShuffleSound() {
        if (audioControl.enabled() && !chatTextToSpeechDucking
                && liveShuffleSoundEnabled()) {
            shuffleSoundId = shuffleSound.play(0.62f * effectsVolume, 1f, 0f);
        }
    }

    private boolean liveShuffleSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_efectos", true)
                    && tablePreference("sonido_barajado", true)
                : presentationSettings.shuffleSound();
    }

    private boolean liveShuffleAnimationEnabled() {
        return presentationSettings == null
                || presentationSettings.shuffleAnimation();
    }

    private boolean liveDealSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_reparto", true)
                : presentationSettings.dealSound();
    }

    private boolean liveDealAnimationEnabled() {
        return presentationSettings == null
                || presentationSettings.dealAnimation();
    }

    private boolean liveFlipAnimationEnabled() {
        return presentationSettings == null
                || presentationSettings.flipAnimation();
    }

    private boolean liveSwapAnimationEnabled() {
        return presentationSettings == null
                || presentationSettings.swapAnimation();
    }

    private boolean liveBetAnimationEnabled() {
        return presentationSettings == null
                || presentationSettings.betAnimation();
    }

    private boolean liveCounterAnimationEnabled() {
        return presentationSettings == null
                || presentationSettings.counterAnimation();
    }

    private float liveSwapDurationSeconds() {
        int durationMillis = presentationSettings == null
                ? Math.round(LOCAL_SWAP_SECONDS * 1_000f)
                : presentationSettings.swapAnimationDuration();
        return Math.max(1, durationMillis) / 1_000f;
    }

    private boolean liveSwapAnimationArc() {
        return presentationSettings != null
                && presentationSettings.swapAnimationArc();
    }

    private int liveDealSpeedPercent() {
        return presentationSettings == null
                ? 100 : presentationSettings.dealSpeed();
    }

    private boolean liveFlipSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_destape", true)
                : presentationSettings.flipSound();
    }

    private boolean liveOwnHoleFlipSoundEnabled() {
        return presentationSettings == null
                ? liveFlipSoundEnabled()
                    && tablePreference("sonido_destape_mis_cartas", false)
                : presentationSettings.ownHoleFlipSound();
    }

    private boolean liveCheckSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_pasar", true)
                : presentationSettings.checkSound();
    }

    private boolean liveFoldSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_fold", true)
                : presentationSettings.foldSound();
    }

    private boolean liveCallSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_igualar", true)
                : presentationSettings.callSound();
    }

    private boolean liveBetSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_apostar", true)
                : presentationSettings.betSound();
    }

    private boolean liveAllInSoundEnabled() {
        return presentationSettings == null
                ? tablePreference("sonido_allin", true)
                : presentationSettings.allInSound();
    }

    private String liveCashRegisterSoundResource() {
        if (presentationSettings != null) {
            return presentationSettings.cashRegisterSound();
        }
        return tablePreference("sonido_caja", true)
                ? "misc/cash_register.wav" : null;
    }

    private void playResourceSound(String resource, float volume,
            float pitch) {
        if (resource == null || resource.isBlank()) return;
        FileHandle file = gameAudioResource(resource);
        if (file == null) return;
        Sound sound = liveAudioCueSounds.computeIfAbsent(resource,
                ignored -> Gdx.audio.newSound(file));
        play(sound, volume, pitch);
    }

    private void playPlayerActionSound(
            TableVisualEvent.PlayerAction.ActionKind kind, int color) {
        switch (kind) {
            case CALL -> {
                if (liveCallSoundEnabled()) {
                    play(callSound, 0.44f, 0.98f + color * 0.02f);
                }
            }
            case BET, RAISE, RERAISE -> {
                if (liveBetSoundEnabled()) {
                    play(betSound, 0.48f, 0.98f + color * 0.02f);
                }
            }
            case ALL_IN -> {
                if (liveAllInSoundEnabled()) {
                    play(allInSound, 0.58f, 1f);
                }
            }
            default -> { }
        }
    }

    private void toggleMasterSound() {
        boolean wasEnabled = audioControl.enabled();
        if (wasEnabled) playSwitchSound(false);
        boolean enabled = audioControl.toggle(uiLayer != UI_SETTINGS);
        if (!enabled) GdxVoicePlayback.stop();
        if (enabled) playSwitchSound(true);
        if (musicEnabled()) {
            backgroundMusic.play();
        } else {
            stopShuffleSound();
            shuffleSound.stop();
            dealSound.stop();
            uncoverSound.stop();
            checkSound.stop();
            callSound.stop();
            betSound.stop();
            foldSound.stop();
            allInSound.stop();
            for (Sound cue : liveAudioCueSounds.values()) cue.stop();
            stopDangerAudioLoop();
            backgroundMusic.pause();
        }
        refreshDealerLoopVolumes();
        if (textToSpeech != null) textToSpeech.refreshVolume();
        GdxVoicePlayback.refreshVolume(effectsVolume);
    }

    private void adjustMasterVolume(float delta) {
        effectsVolume = MathUtils.clamp(
                Math.round((effectsVolume + delta) * 100f) / 100f,
                0f, 1f);
        musicVolume = 0.40f * effectsVolume;
        if (preferences != null) {
            preferences.properties().setProperty("master_volume",
                    Float.toString(effectsVolume));
            if (uiLayer != UI_SETTINGS) preferences.saveDeferred();
        }
        refreshDealerLoopVolumes();
        if (textToSpeech != null) textToSpeech.refreshVolume();
        GdxVoicePlayback.refreshVolume(effectsVolume);
        volumeOverlayUntil = totalTime + 1f;
        playPreferenceSound(GdxSoundFeedback.VOLUME_CHANGE,
                "sonido_volumen", 0.72f);
    }

    private boolean musicEnabled() {
        return audioControl.enabled()
                && tablePreference("musica", true)
                && tablePreference("sonido_ascensor", true);
    }

    private void stopShuffleSound() {
        if (shuffleSoundId >= 0L) {
            shuffleSound.stop(shuffleSoundId);
            shuffleSoundId = -1L;
        }
        // Match CoronaPoker's defensive close: guarantee that no duplicated or
        // internally recycled OpenAL instance can retain the WAV tail.
        shuffleSound.stop();
    }

    private void acceptLiveShuffle(TableVisualEvent.Shuffle event,
            CompletableFuture<Void> barrier) {
        if (event.phase() == TableVisualEvent.Shuffle.Phase.START) {
            if (liveShuffle != null) {
                throw new IllegalStateException("A GDX shuffle is already active");
            }
            liveDeck = availableDeck(event.deck());
            liveState.apply(event);
            liveShuffle = new LiveShuffle(event.deck(), System.nanoTime(),
                    liveShuffleAnimationEnabled(),
                    liveShuffleSoundEnabled());
            startLiveShuffleSound(liveShuffle);
            barrier.complete(null);
            return;
        }
        LiveShuffle active = liveShuffle;
        if (active == null || active.finishBarrier != null) {
            throw new IllegalStateException("GDX shuffle FINISH without one active START");
        }
        active.finishEvent = event;
        active.finishBarrier = barrier;
        // FINISH is presentation-only. Consume it immediately while retaining
        // the real GIF/audio boundary below.
        liveState.apply(event);
        float elapsed = active.elapsedSeconds();
        float duration = active.cycleSeconds;
        active.stopAtSeconds = Math.max(duration,
                ((float) Math.floor(elapsed / duration) + 1f) * duration);
    }

    private void updateLiveShuffle() {
        LiveShuffle active = liveShuffle;
        if (active == null) {
            return;
        }
        float elapsed = active.elapsedSeconds();
        float duration = active.cycleSeconds;
        int cycle = Math.max(0, (int) (elapsed / duration));
        float cycleElapsed = elapsed - cycle * duration;
        if (cycle != active.soundCycle
                && (active.finishBarrier == null || elapsed < active.stopAtSeconds)) {
            stopShuffleSound();
            active.soundCycle = cycle;
            active.soundStopped = false;
            startLiveShuffleSound(active);
        }
        if (!active.soundStopped
                && active.soundEnabled
                && cycleElapsed >= active.soundStopSeconds) {
            stopShuffleSound();
            active.soundStopped = true;
        }
        if (active.finishBarrier == null || elapsed < active.stopAtSeconds) {
            return;
        }
        stopShuffleSound();
        try {
            active.finishBarrier.complete(null);
        } catch (Throwable error) {
            active.finishBarrier.completeExceptionally(error);
        } finally {
            if (active.animation != null) active.animation.dispose();
            liveShuffle = null;
        }
    }

    private void startLiveShuffleSound(LiveShuffle active) {
        if (active.soundEnabled) {
            startShuffleSound();
        }
        active.soundStopped = false;
    }

    private static float wavDurationSeconds(FileHandle file, float fallback) {
        if (file == null || !file.exists()) {
            return fallback;
        }
        try (BufferedInputStream buffered = new BufferedInputStream(file.read());
                AudioInputStream audio = AudioSystem.getAudioInputStream(buffered)) {
            float frameRate = audio.getFormat().getFrameRate();
            long frames = audio.getFrameLength();
            if (frameRate > 0f && frames > 0L) {
                return frames / frameRate;
            }
        } catch (Exception ignored) {
            // The packaged canonical WAV is supported. A malformed/modded file
            // still receives the same conservative duration used by Swing's
            // blocking audio fallback instead of breaking the table.
        }
        return fallback;
    }

    private StreamingGifTextureAnimation liveShuffleAnimation(String deck) {
        String selected = availableDeck(deck);
        if ("goliat".equals(selected)) {
            return loopingGif("images/decks/goliat/gif/shuffle.gif", 960);
        }
        java.util.Optional<Path> external = externalDeckAsset(selected,
                "gif/shuffle.gif");
        if (external.isPresent()) {
            return loopingGif(external.get(), 960);
        }
        if (GdxGamePresentationSettings.OFFICIAL_DECKS.contains(selected)) {
            return loopingGif("images/decks/" + selected
                    + "/gif/shuffle.gif", 960);
        }
        // Swing also permits a mod deck without its own shuffle animation.
        // In that case retain the canonical Goliat shuffle rather than trying
        // to resolve a non-existent classpath resource.
        return loopingGif("images/decks/goliat/gif/shuffle.gif", 960);
    }

    private static StreamingGifTextureAnimation loopingGif(String path,
            int maxWidth) {
        try {
            return StreamingGifTextureAnimation.loadLooping(path, maxWidth);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not load CoronaPoker GIF " + path, ex);
        }
    }

    private static StreamingGifTextureAnimation loopingGif(Path path,
            int maxWidth) {
        try {
            return StreamingGifTextureAnimation.loadLooping(path, maxWidth);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not load external CoronaPoker GIF " + path, ex);
        }
    }

    private StreamingGifTextureAnimation allInAnimation(String filename) {
        if (presentationSettings != null) {
            java.util.Optional<Path> external = presentationSettings.modAsset(
                    "cinematics/allin/" + filename);
            if (external.isPresent()) {
                return streamingGif(external.get(), 563);
            }
        }
        String bundledPath = "cinematics/allin/" + filename;
        return Gdx.files.internal(bundledPath).exists()
                ? streamingGif(bundledPath, 563)
                : streamingGif("cinematics/allin/rounders.gif", 563);
    }

    private static StreamingGifTextureAnimation streamingGif(String path,
            int maxWidth) {
        try {
            return StreamingGifTextureAnimation.load(path, maxWidth);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not load CoronaPoker streaming GIF " + path, ex);
        }
    }

    private static StreamingGifTextureAnimation streamingGif(Path path,
            int maxWidth) {
        try {
            return StreamingGifTextureAnimation.load(path, maxWidth);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not load external CoronaPoker streaming GIF "
                    + path, ex);
        }
    }

    private Sound allInCompanionSound(String gifFilename) {
        if (presentationSettings == null) return null;
        String wav = gifFilename.replaceFirst("(?i)\\.gif$", ".wav");
        java.util.Optional<Path> external = presentationSettings.modAsset(
                "cinematics/allin/" + wav);
        return external.map(path -> liveCinematicSounds.computeIfAbsent(
                path.toString(), ignored -> Gdx.audio.newSound(
                        Gdx.files.absolute(path.toString())))).orElse(null);
    }

    private void updateLiveCardFlight() {
        // State consumption is deliberately forward/sequence ordered. Several
        // overlapping cards can cross their apply threshold in the same frame
        // (especially after a frame hitch); walking the list backwards used to
        // apply the newest sequence first and reject the preceding cards as
        // stale, leaving an apparently random back on the table.
        for (int index = 0; index < liveCardFlights.size(); index++) {
            LiveCardFlight active = liveCardFlights.get(index);
            if (!active.revealSoundPlayed && active.revealProgress() > 0f) {
                active.revealSoundPlayed = true;
                boolean ownHoleCard = active.event
                        instanceof TableVisualEvent.DealHoleCard deal
                        && deal.nickname().equals(
                                liveState.snapshot().localNickname());
                if (ownHoleCard ? liveOwnHoleFlipSoundEnabled()
                        : liveFlipSoundEnabled()) {
                    play(uncoverSound, 0.45f, 1f);
                }
            }
        }
        // Barrier release/removal can run backwards safely after all due state
        // transitions have been applied in canonical order.
        for (int index = liveCardFlights.size() - 1; index >= 0; index--) {
            LiveCardFlight active = liveCardFlights.get(index);
            if (!active.barrierReleased
                    && active.elapsedSeconds() >= active.barrierDelaySeconds) {
                active.barrierReleased = true;
                active.barrier.complete(null);
            }
            if (active.stateApplied && active.barrierReleased
                    && active.visualFinished()) {
                liveCardFlights.remove(index);
            }
        }
    }

    private void applyPendingCardFlightStatesBefore(long sequence) {
        List<LiveCardFlight> pending = new ArrayList<>();
        for (LiveCardFlight flight : liveCardFlights) {
            if (!flight.stateApplied && flight.event.sequence() < sequence) {
                pending.add(flight);
            }
        }
        pending.sort((left, right) -> Long.compare(
                left.event.sequence(), right.event.sequence()));
        for (LiveCardFlight flight : pending) {
            liveState.apply(flight.event);
            flight.stateApplied = true;
        }
        if (!pending.isEmpty()) {
            syncSeatsFromLiveState();
        }
    }

    private float localHoleVisualRemaining(String nickname) {
        float remaining = 0f;
        for (LiveCardFlight flight : liveCardFlights) {
            if (flight.event instanceof TableVisualEvent.DealHoleCard deal
                    && deal.nickname().equals(nickname)) {
                remaining = Math.max(remaining,
                        flight.visualDurationSeconds()
                        - flight.elapsedSeconds());
            }
        }
        return Math.max(0f, remaining);
    }

    private void updateLiveHoleSwap() {
        LiveHoleSwap active = liveHoleSwap;
        if (active == null || !active.finished()) {
            return;
        }
        if (active.waitForCompletion) {
            active.barrier.complete(null);
        }
        liveHoleSwap = null;
    }

    private void updateLiveCommunityReveal() {
        LiveCommunityReveal active = liveCommunityReveal;
        if (active == null) {
            return;
        }
        for (int offset = 0; offset < active.event.cards().size(); offset++) {
            if (!active.soundPlayed[offset] && active.progress(offset) > 0f) {
                active.soundPlayed[offset] = true;
                if (liveFlipSoundEnabled()) {
                    play(uncoverSound, 0.48f, 1f);
                }
            }
        }
        if (!active.finished()) {
            return;
        }
        active.barrier.complete(null);
        liveCommunityReveal = null;
    }

    private void updateLiveHoleReveal() {
        LiveHoleReveal active = liveHoleReveal;
        if (active == null) {
            return;
        }
        if (!active.soundPlayed && active.progress(0) > 0f) {
            active.soundPlayed = true;
            if (liveFlipSoundEnabled()) {
                play(uncoverSound, 0.54f, 1f);
            }
        }
        if (!active.finished()) {
            return;
        }
        active.barrier.complete(null);
        liveHoleReveal = null;
    }

    private void updateLiveHoleFold() {
        LiveHoleFold active = liveHoleFold;
        if (active == null || active.progress() < 1f) {
            return;
        }
        try {
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            liveHoleFold = null;
        }
    }

    private Texture liveCardFace(String code) {
        if (code == null || code.isBlank()) {
            return activeCardBack();
        }
        String deck = availableDeck(liveDeck);
        String key = deck + "/hq/" + code;
        return liveCardFaces.computeIfAbsent(key,
                ignored -> deckCardTexture(deck, code + ".jpg"));
    }

    private Texture deckCardTexture(String deck, String filename) {
        java.util.Optional<Path> hq = externalDeckAsset(deck,
                "hq/" + filename);
        if (hq.isPresent()) {
            return cardTexture(Gdx.files.absolute(hq.get().toString()));
        }
        java.util.Optional<Path> standard = externalDeckAsset(deck, filename);
        if (standard.isPresent()) {
            return cardTexture(Gdx.files.absolute(standard.get().toString()));
        }
        String bundled = GdxGamePresentationSettings.OFFICIAL_DECKS.contains(deck)
                ? deck : "goliat";
        return cardTexture("images/decks/" + bundled + "/hq/" + filename);
    }

    private java.util.Optional<Path> externalDeckAsset(String deck,
            String relative) {
        return presentationSettings == null || !presentationSettings.modDeck(deck)
                ? java.util.Optional.empty()
                : presentationSettings.modAsset("decks/" + deck + "/" + relative);
    }

    private boolean isFolded(int seat) {
        TableSnapshot.PlayerSnapshot player = livePlayer(seats[seat]);
        return player != null && !player.active();
    }

    static boolean hasSettledPresentation(boolean hasHandResult,
            Boolean resolvedWinner) {
        return hasHandResult || resolvedWinner != null;
    }

    static boolean shouldDimSeat(boolean playerActive,
            boolean hasSettledPresentation, boolean foldedThisHand) {
        return foldedThisHand || !playerActive && !hasSettledPresentation;
    }

    static float seatActionSurfaceAlpha(boolean folded,
            boolean settledShowdown) {
        return folded ? FOLDED_ACTION_SURFACE_ALPHA
                : settledShowdown ? 1f : ACTIVE_ACTION_SURFACE_ALPHA;
    }

    static float seatActionTextAlpha(boolean folded) {
        return folded ? FOLDED_ACTION_TEXT_ALPHA : 1f;
    }

    private String lastActionLabelForSeat(int seat) {
        TableSnapshot.PlayerSnapshot player = livePlayer(seats[seat]);
        if (player == null) return "";
        if (isLiveReconnectingPlayer(player.nickname())) {
            return gameText.translate("table.player_reconnecting");
        }
        if (isLiveThinkingSeat(seats[seat])) {
            return uppercase(gameText.translate("ui.pensando")) + "...";
        }
        if (liveState.isIwtsthCandidate(player.nickname())) {
            return iwtsthBlinkOn()
                    ? gameText.translate("iwtsth.iwtsth")
                    : gameText.translate("ui.pierde_3");
        }
        if (liveState.rabbitNoticeActive(player.nickname())) {
            return "RABBIT";
        }
        if (liveState.hasHandResult(player.nickname())) {
            String resolvedName = liveState.resolvedHandName(player.nickname());
            // A blank HandResult is the canonical IWTSTH/muck case: Swing
            // displays the generic loser verdict instead of leaking the hand.
            return resolvedName.isBlank()
                    ? gameText.translate("ui.pierde_3")
                    : localizedHandName(resolvedName, gameText);
        }
        // A hand won because everybody else folded has no HandResult by
        // design. Swing still replaces the previous action (often ALL IN)
        // with GANA/GANAS and paints the whole player frame as settled. The
        // canonical Payout is the only winner fact needed for that case.
        if (Boolean.TRUE.equals(
                liveState.resolvedHandWinner(player.nickname()))) {
            return gameText.translate(seat == 0 ? "ui.ganas_3" : "ui.gana_3");
        }
        if (liveHandLabelVisible(player)) {
            String handName = localizedHandName(player.handName(), gameText);
            Float percentage = liveState.partialHandPercentage(player.nickname());
            if (percentage != null) {
                if (percentage < 0f) {
                    return handName + " (--%)";
                }
                LiveHandProbability probability = liveHandProbabilities.get(
                        player.nickname());
                float shown = probability == null ? percentage
                        : probability.valueAt(System.nanoTime());
                return handName + " (" + formatAmount(shown) + "%)";
            }
            return handName;
        }
        String canonicalLabel = liveState.actionLabel(player.nickname());
        String fallback = !canonicalLabel.isBlank()
                ? canonicalLabel : player.lastAction();
        return localizedActionLabel(liveState.actionKind(player.nickname()),
                fallback, gameText);
    }

    private boolean liveHandLabelVisible(
            TableSnapshot.PlayerSnapshot player) {
        LiveHoleReveal reveal = liveHoleReveal;
        return !player.handName().isBlank()
                && showdownHandLabelVisible(player.nickname(),
                        reveal == null ? null : reveal.event.nickname(),
                        reveal == null || reveal.finished());
    }

    /**
     * The reveal event is installed immediately to preserve canonical sequence
     * ordering, but its evaluated hand must not visually overtake the cards.
     * Other players remain unaffected while a cascade reveals one seat.
     */
    static boolean showdownHandLabelVisible(String playerNickname,
            String revealingNickname, boolean revealFinished) {
        return revealingNickname == null
                || !revealingNickname.equals(playerNickname)
                || revealFinished;
    }

    /**
     * Action events carry their semantic kind as well as Swing's already
     * translated caption.  Render from the kind so an in-place language
     * change also updates actions that happened earlier in the street.  A
     * recovered initial snapshot has no event-kind map yet, so recognise the
     * finite ES/EN legacy captions before falling back to its original text.
     */
    static String localizedActionLabel(
            TableVisualEvent.PlayerAction.ActionKind kind,
            String fallback, GdxGameText text) {
        String label = fallback == null ? "" : fallback;
        TableVisualEvent.PlayerAction.ActionKind resolved = kind == null
                ? actionKindFromLegacyLabel(label) : kind;
        if (resolved == null) return label;
        return switch (resolved) {
            case FOLD -> text.translate("action.label.fold2");
            case CHECK -> text.translate("action.label.check2");
            case CALL -> text.translate("action.label.call2");
            case BET -> text.translate("action.label.bet2");
            case RAISE -> text.translate("action.label.raise2");
            case RERAISE -> "RE" + text.translate("action.label.raise2");
            case ALL_IN -> text.translate("action.label.allin");
            case WAITING, SMALL_BLIND, BIG_BLIND, STRADDLE -> label;
        };
    }

    /**
     * Hand evaluation is canonical, but its snapshot caption was translated
     * when the event was created. Resolve the finite ES/EN poker vocabulary at
     * render time so a live language change also updates Monte Carlo and
     * showdown labels without changing the shared game contract.
     */
    static String localizedHandName(String handName, GdxGameText text) {
        String value = handName == null ? "" : handName;
        String key = LEGACY_HAND_NAME_KEYS.get(normalizedCaption(value));
        return key == null ? value : text.translate(key);
    }

    private static Map<String, String> legacyHandNameKeys() {
        Map<String, String> names = new HashMap<>();
        for (String language : List.of("es", "en")) {
            GdxGameText catalog = new GdxGameText(language);
            for (String key : HAND_TRANSLATION_KEYS) {
                names.put(normalizedCaption(catalog.translate(key)), key);
            }
        }
        return Map.copyOf(names);
    }

    private static String normalizedCaption(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static TableVisualEvent.PlayerAction.ActionKind
            actionKindFromLegacyLabel(String label) {
        String normalized = normalizedCaption(label).replace('-', ' ')
                .replaceAll("\\s+", " ");
        return switch (normalized) {
            case "NO VA", "FOLD" ->
                TableVisualEvent.PlayerAction.ActionKind.FOLD;
            case "PASA", "CHECK" ->
                TableVisualEvent.PlayerAction.ActionKind.CHECK;
            case "VA", "CALL" ->
                TableVisualEvent.PlayerAction.ActionKind.CALL;
            case "APUESTA", "BET" ->
                TableVisualEvent.PlayerAction.ActionKind.BET;
            case "SUBE", "RAISE" ->
                TableVisualEvent.PlayerAction.ActionKind.RAISE;
            case "RESUBE", "RERAISE" ->
                TableVisualEvent.PlayerAction.ActionKind.RERAISE;
            case "ALL IN" ->
                TableVisualEvent.PlayerAction.ActionKind.ALL_IN;
            default -> null;
        };
    }

    private Color lastActionColorForSeat(int seat) {
        TableSnapshot.PlayerSnapshot player = livePlayer(seats[seat]);
        if (player == null) return SEAT_RIM;
        if (isLiveReconnectingPlayer(player.nickname())) {
            return LATENCY_ORANGE;
        }
        if (isLiveThinkingSeat(seats[seat])) return LEGACY_THINKING;
        if (liveState.isIwtsthCandidate(player.nickname())) {
            return iwtsthBlinkOn() ? Color.WHITE : LEGACY_LOSER;
        }
        if (player.nickname().equals(liveShowdownHoverNickname)) {
            return POT_GOLD;
        }
        Boolean resolvedWinner = liveState.resolvedHandWinner(player.nickname());
        if (resolvedWinner != null) {
            return resolvedWinner ? LEGACY_WINNER : LEGACY_LOSER;
        }
        if (liveHandLabelVisible(player)) {
            if (liveState.partialHandPercentage(player.nickname()) != null) {
                return player.winner() ? PARTIAL_HAND_WINNER : PARTIAL_HAND_LOSER;
            }
            if (!liveState.hasHandResult(player.nickname())) {
                return LEGACY_SHOW;
            }
            return player.winner() ? LEGACY_WINNER : LEGACY_LOSER;
        }
        return liveActionColor(liveState.actionKind(player.nickname()));
    }

    private Color lastActionTextColorForSeat(int seat) {
        TableSnapshot.PlayerSnapshot player = livePlayer(seats[seat]);
        if (player != null && isLiveReconnectingPlayer(player.nickname())) {
            return Color.WHITE;
        }
        if (isLiveThinkingSeat(seats[seat])) {
            return Color.LIGHT_GRAY;
        }
        if (player != null && liveState.isIwtsthCandidate(player.nickname())) {
            return iwtsthBlinkOn() ? LEGACY_LOSER : Color.WHITE;
        }
        if (player != null
                && player.nickname().equals(liveShowdownHoverNickname)) {
            return Color.BLACK;
        }
        if (player == null) {
            return Color.WHITE;
        }
        Boolean resolvedWinner = liveState.resolvedHandWinner(player.nickname());
        if (resolvedWinner != null) {
            return resolvedWinner ? Color.BLACK : Color.WHITE;
        }
        if (liveHandLabelVisible(player)) {
            if (liveState.partialHandPercentage(player.nickname()) != null) {
                return player.winner() ? Color.BLACK : Color.WHITE;
            }
            return liveState.hasHandResult(player.nickname()) && player.winner()
                    ? Color.BLACK : Color.WHITE;
        }
        return liveActionTextColor(liveState.actionKind(player.nickname()));
    }

    private boolean iwtsthBlinkOn() {
        return ((long) (totalTime / 1.5f) & 1L) != 0L;
    }

    private void updateLiveHandProbability(TableVisualEvent.PartialHand partial) {
        if (partial.winPercentage() < 0f) {
            // Swing paints --% while retaining the last numeric value so the
            // next street rolls from it rather than restarting at zero.
            return;
        }
        long now = System.nanoTime();
        LiveHandProbability previous = liveHandProbabilities.get(
                partial.nickname());
        float from = previous == null ? 0f : previous.valueAt(now);
        boolean animate = liveCounterAnimationEnabled();
        liveHandProbabilities.put(partial.nickname(), new LiveHandProbability(
                animate ? from : partial.winPercentage(),
                partial.winPercentage(), now, animate));
    }

    static float partialHandProbabilityValue(float from, float target,
            long elapsedNanos, boolean animate) {
        if (!animate) return target;
        float progress = MathUtils.clamp(elapsedNanos
                / (PARTIAL_HAND_ROLL_SECONDS * 1_000_000_000f), 0f, 1f);
        return from + (target - from) * progress;
    }

    private boolean isLiveThinkingSeat(Seat seat) {
        if (liveState == null || seat == null || seat.name.isBlank()) {
            return false;
        }
        TableSnapshot snapshot = liveState.snapshot();
        return !seat.name.equals(snapshot.localNickname())
                && !isLiveReconnectingPlayer(seat.name)
                && isActionableTurn(snapshot, seat.name);
    }

    /**
     * Transient socket replacement belongs to the network projection, not to
     * poker state.  The live lobby remains attached to the table so GDX can
     * show it without inventing a dealer action or mutating TableSnapshot.
     */
    boolean isLiveReconnectingPlayer(String nickname) {
        if (tableChat == null || nickname == null || nickname.isBlank()) {
            return false;
        }
        return tableChat.snapshot().participants().stream()
                .anyMatch(participant -> participant.nickname().equals(nickname)
                        && !participant.local() && !participant.bot()
                        && !participant.connected());
    }

    private Texture tableAvatar(String nickname) {
        if (tableChat != null && nickname != null) {
            for (com.tonikelope.coronapoker.core.LobbyParticipant participant
                    : tableChat.snapshot().participants()) {
                if (!participant.nickname().equals(nickname)) continue;
                if (participant.avatar() == null) {
                    return participant.bot() ? avatarBot : avatarDefault;
                }
                String path = participant.avatar().toString();
                Texture cached = tableAvatarTextures.get(path);
                if (cached != null) return cached;
                try {
                    Texture loaded = new Texture(Gdx.files.absolute(path), true);
                    loaded.setFilter(TextureFilter.MipMapLinearLinear,
                            TextureFilter.Linear);
                    tableAvatarTextures.put(path, loaded);
                    return loaded;
                } catch (RuntimeException invalidAvatar) {
                    return participant.bot() ? avatarBot : avatarDefault;
                }
            }
        }
        return nickname != null && nickname.startsWith("CoronaBot$")
                ? avatarBot : avatarDefault;
    }

    boolean isClientTransportReconnecting() {
        return tableChat != null && !tableHost
                && tableChat.snapshot().phase()
                        == com.tonikelope.coronapoker.core.LobbySnapshot.Phase.RECONNECTING;
    }

    static Color liveActionColor(
            TableVisualEvent.PlayerAction.ActionKind kind) {
        if (kind == null) {
            return SEAT_RIM;
        }
        return switch (kind) {
            case FOLD -> LEGACY_FOLD;
            case CHECK -> LEGACY_CHECK;
            case CALL -> LEGACY_CALL;
            case ALL_IN -> LEGACY_ALL_IN;
            case BET, RAISE -> LEGACY_BET;
            case RERAISE -> LEGACY_RERAISE;
            case SMALL_BLIND, BIG_BLIND, STRADDLE -> POT_GOLD;
            case WAITING -> SEAT_RIM;
        };
    }

    static Color settledShowdownColor(boolean winner) {
        return winner ? LEGACY_WINNER : LEGACY_LOSER;
    }

    static Color timeoutAwareRim(TableSnapshot.PlayerSnapshot livePlayer,
            Color normalRim) {
        return livePlayer != null && livePlayer.timedOut()
                ? LEGACY_TIMEOUT : normalRim;
    }

    static Color liveActionTextColor(
            TableVisualEvent.PlayerAction.ActionKind kind) {
        return kind == TableVisualEvent.PlayerAction.ActionKind.CALL
                || kind == TableVisualEvent.PlayerAction.ActionKind.BET
                || kind == TableVisualEvent.PlayerAction.ActionKind.RAISE
                ? Color.BLACK : Color.WHITE;
    }

    static float composedAlpha(Color color, float visualAlpha,
            float presence) {
        return color.a * visualAlpha * presence;
    }

    private float sharedTurnRemaining() {
        long total = liveState.turnTotalMillis();
        return total <= 0L ? 0f
                : MathUtils.clamp(liveState.turnRemainingMillis()
                        / (float) total, 0f, 1f);
    }

    private void drawHandOverlay() {
        Objects.requireNonNull(liveState,
                "Hand overlays require authoritative live state");
        drawLiveShuffleOverlay();
        drawLiveCinematicOverlay();
    }

    private void drawLiveShuffleOverlay() {
        LiveShuffle active = liveShuffle;
        if (active == null) {
            return;
        }
        float worldWidth = viewport.getWorldWidth();
        float worldHeight = viewport.getWorldHeight();
        if (!active.animationEnabled) {
            batch.begin();
            drawCentered(actionFont, gameText.translate("game.barajando") + "…",
                    tableCenterX, tableCenterY + 16f, POT_GOLD, 1f);
            batch.end();
            return;
        }
        float width = active.animation.width() * worldWidth
                / Gdx.graphics.getBackBufferWidth();
        float height = active.animation.height() * worldHeight
                / Gdx.graphics.getBackBufferHeight();
        float x = tableCenterX - width / 2f;
        float y = tableCenterY - height / 2f;
        Texture frame = active.animation.frameAt(active.elapsedSeconds());
        if (frame != null) {
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(frame, x, y, width, height);
            batch.end();
        }
    }

    private void drawLiveCinematicOverlay() {
        LiveCinematic active = liveCinematic;
        if (active == null) {
            return;
        }
        Texture frame = active.animation.frameAt(active.elapsedSeconds());
        float worldWidth = viewport.getWorldWidth();
        float worldHeight = viewport.getWorldHeight();
        float height = worldHeight * 0.5f;
        int sourceWidth = active.animation.width();
        int sourceHeight = active.animation.height();
        float width = sourceWidth > 0 && sourceHeight > 0
                ? sourceWidth * height / sourceHeight : worldWidth * 0.5f;
        float maxWidth = worldWidth * 0.8f;
        if (width > maxWidth) {
            height *= maxWidth / width;
            width = maxWidth;
        }
        float x = worldWidth / 2f - width / 2f;
        float y = worldHeight / 2f - height / 2f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.34f);
        shapes.rect(0f, 0f, worldWidth, worldHeight);
        shapes.end();
        batch.begin();
        batch.setColor(Color.WHITE);
        if (frame != null) {
            batch.draw(frame, x, y, width, height);
        }
        batch.end();
    }

    private void drawShowdownOverlay() {
        Objects.requireNonNull(liveState,
                "Showdown rendering requires authoritative live state");
        drawLiveWinnerGlow();
    }

    private void drawLiveWinnerGlow() {
        List<TableSnapshot.PlayerSnapshot> players = liveState.snapshot().players();
        boolean hasWinner = false;
        for (TableSnapshot.PlayerSnapshot player : players) {
            if (Boolean.TRUE.equals(
                    liveState.resolvedHandWinner(player.nickname()))) {
                hasWinner = true;
                break;
            }
        }
        if (!hasWinner) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (TableSnapshot.PlayerSnapshot player : players) {
            if (!Boolean.TRUE.equals(
                    liveState.resolvedHandWinner(player.nickname()))) continue;
            Seat winner = seatByNickname(player.nickname());
            if (winner == null) continue;
            long startedAt = liveWinnerStarts.getOrDefault(player.nickname(), 0L);
            float winnerProgress = startedAt == 0L ? 1f : MathUtils.clamp(
                    (System.nanoTime() - startedAt) / 1_200_000_000f, 0f, 1f);
            for (int ring = 7; ring >= 1; ring--) {
                float radius = 48f + ring * 16f
                        + MathUtils.sin(totalTime * 4f + ring) * 5f;
                shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                        winnerProgress * (0.018f + (8 - ring) * 0.006f));
                shapes.circle(winner.x, winner.y, radius, 64);
            }
            for (int particle = 0; particle < 72; particle++) {
                float phase = particle * 1.731f;
                float travel = (winnerProgress * 1.4f
                        + particle * 0.019f) % 1f;
                float angle = phase + totalTime
                        * (particle % 2 == 0 ? 0.35f : -0.28f);
                float radius = 55f + Interpolation.circleOut.apply(travel)
                        * (90f + particle % 8 * 18f);
                Color color = particle % 3 == 0 ? CYAN : POT_GOLD;
                shapes.setColor(color.r, color.g, color.b,
                        (1f - travel) * 0.62f);
                shapes.circle(winner.x + MathUtils.cos(angle) * radius,
                        winner.y + MathUtils.sin(angle) * radius,
                        2f + particle % 4, 10);
            }
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void updateLiveChipBatch() {
        LiveChipBatch active = liveChipBatch;
        if (active == null) {
            return;
        }
        float elapsed = active.elapsedSeconds();
        for (LiveChip chip : active.chips) {
            if (!chip.soundPlayed && chip.progress(elapsed) >= 1f) {
                chip.soundPlayed = true;
                if (liveBetSoundEnabled()) {
                    play(betSound,
                            chip.chipIndex == 0 ? 0.40f : 0.20f,
                            0.92f + chip.color * 0.055f);
                }
            }
        }
        if (!active.complete(elapsed)) {
            return;
        }
        try {
            livePotContributions.clear();
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            liveChipBatch = null;
        }
    }

    private void updateLivePayout() {
        LivePayout active = livePayout;
        if (active == null) {
            return;
        }
        float elapsed = active.elapsedSeconds();
        for (LivePayoutChip chip : active.chips) {
            if (!chip.soundPlayed && chip.progress(elapsed) >= 1f) {
                chip.soundPlayed = true;
                // A payout is a stream of chips. Sound a representative subset
                // exactly on contact instead of playing one cue when the event is
                // accepted, before any chip has reached the winner.
                if (chip.index % 3 == 0 && liveCallSoundEnabled()) {
                    play(callSound, 0.24f, 0.96f + chip.color * 0.04f);
                }
            }
        }
        if (!active.complete(elapsed)) {
            return;
        }
        try {
            liveWinnerStarts.putIfAbsent(active.event.nickname(),
                    System.nanoTime());
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            livePayout = null;
        }
    }

    private void updateLiveRebuy() {
        LiveRebuy active = liveRebuy;
        if (active == null) {
            return;
        }
        float elapsed = active.elapsedSeconds();
        for (LiveRebuyChip chip : active.chips) {
            if (!chip.soundPlayed && chip.progress(elapsed) >= 1f) {
                chip.soundPlayed = true;
                if (chip.index % 3 == 0 && liveBetSoundEnabled()) {
                    play(betSound, 0.24f, 0.96f + chip.color * 0.04f);
                }
            }
        }
        if (!active.complete(elapsed)) {
            return;
        }
        try {
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            stopAudioCue(active.cashSoundResource);
            liveRebuy = null;
        }
    }

    private void updateLiveInitialStackFill() {
        LiveInitialStackFill active = liveInitialStackFill;
        if (active == null || !active.complete()) {
            return;
        }
        try {
            syncSeatsFromLiveState();
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            stopAudioCue(active.event.soundResource());
            liveInitialStackFill = null;
        }
    }

    private void updateLiveCinematic() {
        LiveCinematic active = liveCinematic;
        if (active == null
                || active.elapsedSeconds()
                < active.event.durationMillis() / 1_000f) {
            return;
        }
        try {
            // The ordered START event was consumed when the GIF began. This
            // future is solely the causal completion signal for the dealer.
            active.barrier.complete(null);
        } catch (Throwable error) {
            active.barrier.completeExceptionally(error);
        } finally {
            active.animation.dispose();
            liveCinematic = null;
        }
    }

    private void updateLiveSeatAmounts(Seat seat) {
        TableSnapshot.PlayerSnapshot player = livePlayer(seat);
        if (player == null) {
            return;
        }
        double actionRemaining = 0d;
        for (LiveActionChip action : liveActionChips) {
            if (action.deferPlayerCounters
                    && action.event.nickname().equals(seat.name)) {
                actionRemaining += action.event.contributionDelta()
                        - action.landedContribution(action.elapsedSeconds());
            }
        }
        double payoutTotal = livePayout == null
                || !livePayout.event.nickname().equals(seat.name) ? 0d
                : livePayout.event.amount();
        double paid = livePayout == null
                || !livePayout.event.nickname().equals(seat.name) ? 0d
                : livePayout.landedContribution(livePayout.elapsedSeconds());
        double rebuyTotal = liveRebuy == null ? 0d
                : liveRebuy.totalContribution(seat.name);
        double rebought = liveRebuy == null ? 0d
                : liveRebuy.landedContribution(seat.name,
                        liveRebuy.elapsedSeconds());
        double initialFillRemaining = liveInitialStackFill == null ? 0d
                : liveInitialStackFill.remaining(seat.name);
        double stack = displayedIncomingStack(player.stack()
                + actionRemaining - initialFillRemaining,
                payoutTotal + rebuyTotal, paid + rebought);
        // Swing's per-player pot box displays Player.getBote(): the amount
        // accumulated by this player over the whole hand. Moving the street's
        // chips into the central pot must never erase that counter. Only hold
        // back the still-airborne part of the current action so the number and
        // chip animation land together.
        double invested = displayedPotContribution(
                player.potContribution(), actionRemaining);
        if (Double.compare(stack, seat.displayedStackAmount) != 0) {
            seat.displayedStackAmount = stack;
            seat.stackText = formatAmount(stack);
        }
        if (Double.compare(invested, seat.displayedInvestedAmount) != 0) {
            seat.displayedInvestedAmount = invested;
            seat.investedText = formatAmount(invested);
        }
    }

    static double displayedPotContribution(double canonicalContribution,
            double airborneContribution) {
        return Math.max(0d, canonicalContribution
                - Math.max(0d, airborneContribution));
    }

    static boolean actionChipSoundDue(float firstChipProgress) {
        return firstChipProgress >= 1f;
    }

    static double displayedIncomingStack(double canonicalStackAfter,
            double totalIncoming, double landedIncoming) {
        double incoming = Math.max(0d, totalIncoming);
        double landed = Math.min(incoming, Math.max(0d, landedIncoming));
        return Math.max(0d, canonicalStackAfter - incoming + landed);
    }

    static double displayedPayoutPot(double canonicalPotAfter,
            double payoutAmount, double landedAmount) {
        double payout = Math.max(0d, payoutAmount);
        double landed = Math.min(payout, Math.max(0d, landedAmount));
        return Math.max(0d, canonicalPotAfter + payout - landed);
    }

    private double livePot() {
        if (livePayout != null) {
            return displayedPayoutPot(livePayout.event.potAfter(),
                    livePayout.event.amount(), livePayout.landedContribution(
                            livePayout.elapsedSeconds()));
        }
        double pending = 0d;
        for (double contribution : livePotContributions.values()) {
            pending += contribution;
        }
        double active = 0d;
        for (LiveActionChip action : liveActionChips) {
            active += action.landedContribution(action.elapsedSeconds());
        }
        return liveChipBatch == null ? liveState.snapshot().pot() + pending + active
                : liveChipBatch.event.potBefore() + liveChipBatch.alreadyInPot
                + liveChipBatch.landedContribution(null,
                        liveChipBatch.elapsedSeconds());
    }

    private void drawLiveChipTrails(float targetX, float targetY) {
        LiveChipBatch active = liveChipBatch;
        if (active == null && liveActionChips.isEmpty()) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (LiveActionChip action : liveActionChips) {
            Seat from = seatByNickname(action.event.nickname());
            if (from != null) {
                float elapsed = action.elapsedSeconds();
                for (LiveChip chip : action.chips) {
                    drawLiveTrail(from.stackX, from.stackY,
                            targetX, targetY,
                            chip.rotation, chip.progress(elapsed),
                            elapsed - chip.startSeconds - chip.durationSeconds);
                }
            }
        }
        if (active != null) {
            float elapsed = active.elapsedSeconds();
            for (LiveChip chip : active.chips) {
                Seat from = seatByNickname(chip.nickname);
                if (from == null) {
                    continue;
                }
                drawLiveTrail(from.stackX, from.stackY,
                        targetX, targetY, chip.rotation,
                        chip.progress(elapsed),
                        elapsed - chip.startSeconds - chip.durationSeconds);
            }
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawLiveTrail(float fromX, float fromY, float toX, float toY,
            float rotation, float progress, float impactAge) {
        if (impactAge >= 0f && impactAge < 0.34f) {
            float impact = impactAge / 0.34f;
            shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                    (1f - impact) * 0.24f);
            shapes.circle(toX, toY, 24f + impact * 82f, 36);
        }
        if (progress < 0f || progress >= 1f) {
            return;
        }
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                (1f - progress) * 0.22f);
        shapes.circle(fromX, fromY, 18f + progress * 28f, 24);
        for (int tail = 1; tail <= 5; tail++) {
            float t = Math.max(0f, progress - tail * 0.025f);
            float x = bezier(fromX,
                    liveControlX(fromX, toX, rotation), toX, t);
            float y = bezier(fromY,
                    liveControlY(fromY, toY, rotation), toY, t);
            shapes.setColor(ORANGE.r, ORANGE.g, ORANGE.b,
                    (6 - tail) * 0.028f);
            shapes.circle(x, y, 15f - tail * 1.7f, 12);
        }
    }

    private void drawLiveFlyingChips(float targetX, float targetY) {
        LiveChipBatch active = liveChipBatch;
        LivePayout payout = livePayout;
        LiveRebuy rebuy = liveRebuy;
        if (active == null && liveActionChips.isEmpty()
                && payout == null && rebuy == null) {
            return;
        }
        batch.begin();
        for (LiveActionChip action : liveActionChips) {
            Seat from = seatByNickname(action.event.nickname());
            if (from != null) {
                float elapsed = action.elapsedSeconds();
                for (LiveChip chip : action.chips) {
                    drawLiveFlyingChip(from.stackX, from.stackY,
                            targetX, targetY, chip.rotation,
                            chip.color, chip.progress(elapsed));
                }
            }
        }
        if (active != null) {
            float elapsed = active.elapsedSeconds();
            for (LiveChip chip : active.chips) {
                Seat from = seatByNickname(chip.nickname);
                if (from != null) {
                    drawLiveFlyingChip(from.stackX, from.stackY,
                            targetX, targetY, chip.rotation, chip.color,
                            chip.progress(elapsed));
                }
            }
        }
        if (payout != null) {
            Seat winner = seatByNickname(payout.event.nickname());
            if (winner != null) {
                float elapsed = payout.elapsedSeconds();
                for (LivePayoutChip chip : payout.chips) {
                    drawLivePayoutChip(winner, chip, elapsed);
                }
            }
        }
        if (rebuy != null) {
            float elapsed = rebuy.elapsedSeconds();
            for (LiveRebuyChip chip : rebuy.chips) {
                Seat receiver = seatByNickname(chip.nickname);
                if (receiver != null) {
                    drawLiveRebuyChip(receiver, chip, elapsed);
                }
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawLiveFlyingChip(float fromX, float fromY, float toX,
            float toY, float rotation, int color, float progress) {
        if (progress < 0f || progress >= 1f) {
            return;
        }
        float x = bezier(fromX, liveControlX(fromX, toX, rotation),
                toX, progress);
        float y = bezier(fromY, liveControlY(fromY, toY, rotation),
                toY, progress);
        float landing = Interpolation.pow3In.apply(progress);
        float size = (45f + MathUtils.sin(progress * MathUtils.PI) * 9f)
                * (1f - landing * 0.28f);
        Texture texture = flyingChips[color];
        batch.setColor(Color.WHITE);
        batch.draw(texture, x - size / 2f, y - size / 2f,
                size / 2f, size / 2f, size, size, 1f, 1f,
                rotation + progress * 360f,
                0, 0, texture.getWidth(), texture.getHeight(), false, false);
    }

    private void drawLivePayoutChip(Seat winner, LivePayoutChip chip,
            float elapsed) {
        float raw = (elapsed - chip.startSeconds) / chip.durationSeconds;
        if (raw <= 0f || raw >= 1f) return;
        float eased = Interpolation.pow2Out.apply(raw);
        float targetX = payoutTargetX(winner, chip.index);
        float targetY = payoutTargetY(winner, chip.index);
        float x = bezier(potCenterX, tableCenterX - 210f, targetX, eased);
        float y = bezier(potCenterY, tableCenterY + 330f, targetY, eased);
        float size = 34f + MathUtils.sin(raw * MathUtils.PI) * 9f;
        Texture texture = flyingChips[chip.color];
        // Keep the chip tangible until the collision frame. Fading it during
        // the final approach makes an impact-timed sound appear late.
        batch.setColor(Color.WHITE);
        batch.draw(texture, x - size / 2f, y - size / 2f,
                size / 2f, size / 2f, size, size, 1f, 1f,
                chip.index * 29f + raw * 240f,
                0, 0, texture.getWidth(), texture.getHeight(), false, false);
    }

    private void drawLiveRebuyChip(Seat receiver, LiveRebuyChip chip,
            float elapsed) {
        float raw = chip.progress(elapsed);
        if (raw <= 0f || raw >= 1f) {
            return;
        }
        float eased = Interpolation.pow2Out.apply(raw);
        float originX = tableCenterX;
        float originY = 24f;
        float targetX = payoutTargetX(receiver, chip.index);
        float targetY = payoutTargetY(receiver, chip.index);
        float controlX = (originX + targetX) * 0.5f
                + MathUtils.sinDeg(chip.rotation) * 95f;
        float controlY = Math.max(originY, targetY) + 190f;
        float x = bezier(originX, controlX, targetX, eased);
        float y = bezier(originY, controlY, targetY, eased);
        float size = 38f + MathUtils.sin(raw * MathUtils.PI) * 11f;
        Texture texture = flyingChips[chip.color];
        // The impact cue is emitted at progress == 1; do not visually erase
        // the chip before it reaches the receiving stack.
        batch.setColor(Color.WHITE);
        batch.draw(texture, x - size / 2f, y - size / 2f,
                size / 2f, size / 2f, size, size, 1f, 1f,
                chip.rotation + raw * 300f,
                0, 0, texture.getWidth(), texture.getHeight(), false, false);
    }

    private float liveControlX(float from, float to, float rotation) {
        float outward = from < tableCenterX ? -1f : 1f;
        return (from + to) * 0.5f + outward * 175f
                + MathUtils.cos(rotation) * 35f;
    }

    private static float payoutTargetX(Seat winner, int chip) {
        return winner.stackX + (chip % 5 - 2) * 9f;
    }

    private static float payoutTargetY(Seat winner, int chip) {
        return winner.stackY + 8f + (chip % 4) * 6f;
    }

    private static float liveControlY(float from, float to, float rotation) {
        return (from + to) * 0.5f + 120f + MathUtils.sin(rotation) * 45f;
    }

    private void drawChipTrails(float targetX, float targetY) {
        Objects.requireNonNull(liveState,
                "Chip trails require authoritative live state");
        drawLiveChipTrails(targetX, targetY);
    }

    private void drawFlyingChips(float targetX, float targetY) {
        Objects.requireNonNull(liveState,
                "Flying chips require authoritative live state");
        drawLiveFlyingChips(targetX, targetY);
    }

    private static float bezier(float from, float control, float to, float t) {
        float inverse = 1f - t;
        return inverse * inverse * from + 2f * inverse * t * control + t * t * to;
    }

    private void roundedRect(float x, float y, float width, float height, float radius) {
        float safeRadius = Math.min(radius, Math.min(width, height) * 0.5f);
        if (safeRadius <= 0f) {
            shapes.rect(x, y, width, height);
            return;
        }
        // These pieces only share boundaries. The previous implementation
        // overlapped two rectangles and four circles; with alpha blending those
        // overlaps appeared as bright balls in every rounded corner.
        shapes.rect(x + safeRadius, y, width - safeRadius * 2f, height);
        shapes.rect(x, y + safeRadius, safeRadius, height - safeRadius * 2f);
        shapes.rect(x + width - safeRadius, y + safeRadius,
                safeRadius, height - safeRadius * 2f);
        shapes.arc(x + safeRadius, y + safeRadius, safeRadius, 180f, 90f, 18);
        shapes.arc(x + width - safeRadius, y + safeRadius,
                safeRadius, 270f, 90f, 18);
        shapes.arc(x + width - safeRadius, y + height - safeRadius,
                safeRadius, 0f, 90f, 18);
        shapes.arc(x + safeRadius, y + height - safeRadius,
                safeRadius, 90f, 90f, 18);
    }

    private void drawHudActionSurface(float x, float y, float width, float height,
            Color color, boolean hover, boolean selected, boolean enabled) {
        // Keep Swing's learned action colours genuinely recognisable. The former
        // almost-opaque navy layer sat on top of every base colour, turning white
        // into grey and desaturating green/red/purple. We retain the approved
        // glass highlight and depth, but the material itself now derives
        // directly from the canonical action colour.
        // Active controls use the exact source colour. In particular, Swing's
        // white call/bet surface must remain #FFFFFF instead of becoming an
        // off-white after compositing over the dark table material.
        float colorMix = enabled ? 1f : 0.34f;
        float surfaceR = 0.018f + (color.r - 0.018f) * colorMix;
        float surfaceG = 0.032f + (color.g - 0.032f) * colorMix;
        float surfaceB = 0.055f + (color.b - 0.055f) * colorMix;
        if (selected && enabled) {
            float pulse = 0.58f + 0.42f
                    * MathUtils.sin(totalTime * 7.4f) * MathUtils.sin(totalTime * 7.4f);
            shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                    0.32f + pulse * 0.40f);
            roundedRect(x - 7f, y - 7f, width + 14f, height + 14f, 18f);
            shapes.setColor(1f, 1f, 1f, 0.52f + pulse * 0.38f);
            roundedRect(x - 3f, y - 3f, width + 6f, height + 6f, 16f);
        }
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(x + 5f, y - 6f, width, height, 14f);
        Color rimColor = hudActionRimColor(color, selected);
        shapes.setColor(rimColor.r, rimColor.g, rimColor.b,
                enabled || hover ? 1f : 0.48f);
        roundedRect(x, y, width, height, 14f);
        shapes.setColor(surfaceR, surfaceG, surfaceB,
                enabled || hover ? 1f : 0.78f);
        roundedRect(x + 3f, y + 4f, width - 6f, height - 8f, 11f);
        shapes.setColor(1f, 1f, 1f,
                hover || selected ? 0.28f : enabled ? 0.18f : 0.07f);
        roundedRect(x + 9f, y + height - 19f, width - 18f, 10f, 5f);
        shapes.setColor(color.r, color.g, color.b, enabled ? 1f : 0.48f);
        shapes.rect(x + 13f, y + 5f, width - 26f, 4f);
    }

    static Color hudActionRimColor(Color semanticColor, boolean selected) {
        return selected ? POT_GOLD : semanticColor;
    }

    static Color hudArmedSurfaceColor(Color semanticColor, boolean armed) {
        return armed ? ARMED_ACTION_GREEN : semanticColor;
    }

    private void drawHudActionContent(String text, float x, float y,
            float width, float height, Color color, float alpha) {
        drawFittedCenteredInBox(actionFont, text,
                x + 12f, y + 16f,
                width - 24f, height - 32f,
                color, alpha);
    }

    private void drawHudActionContent(Texture icon, String text,
            float x, float y, float width, float height,
            Color color, float alpha) {
        float iconSize = Math.min(38f, height * 0.43f);
        float gap = 7f;
        float contentX = x + 12f;
        float contentY = y + 16f;
        float contentWidth = width - 24f;
        float contentHeight = height - 32f;
        // GlyphLayout snapshots the font colour into its runs.  Set it before
        // both layouts; changing BitmapFont afterwards leaves (for example)
        // the CALL caption white over Swing's white call surface.
        actionFont.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(actionFont, text);
        float maximumTextWidth = Math.max(1f,
                contentWidth - iconSize - gap);
        float scale = Math.min(1f, maximumTextWidth
                / Math.max(1f, glyph.width));
        float textWidth = glyph.width * scale;
        float groupWidth = iconSize + gap + textWidth;
        float groupX = contentX + (contentWidth - groupWidth) / 2f;
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(icon, groupX,
                y + (height - iconSize) / 2f,
                iconSize, iconSize);
        BitmapFont.BitmapFontData data = actionFont.getData();
        float previousScaleX = data.scaleX;
        float previousScaleY = data.scaleY;
        data.setScale(previousScaleX * scale, previousScaleY * scale);
        glyph.setText(actionFont, text);
        actionFont.draw(batch, glyph, groupX + iconSize + gap,
                contentY + (contentHeight + glyph.height) / 2f);
        data.setScale(previousScaleX, previousScaleY);
        actionFont.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
    }

    private void drawSharedTurnBar(float x, float y, float width, float time) {
        float height = COMMUNITY_TIMER_HEIGHT;
        float remaining = sharedTurnRemaining();
        Color timerColor = remaining > 0.55f ? SWING_STACK_GREEN
                : remaining > 0.25f ? POT_GOLD : FOLD_RED;
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(x + 2f, y - 2f, width, height, 5f);
        shapes.setColor(0.018f, 0.032f, 0.055f, 0.96f);
        roundedRect(x, y, width, height, 5f);
        if (remaining > 0.002f) {
            float fillWidth = width * remaining;
            shapes.setColor(timerColor.r, timerColor.g, timerColor.b, 0.96f);
            roundedRect(x, y, fillWidth, height, 5f);
        }
    }

    private void drawSharedProgressBar(float x, float y, float width) {
        float height = COMMUNITY_TIMER_HEIGHT;
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(x + 2f, y - 2f, width, height, 5f);
        shapes.setColor(0.018f, 0.032f, 0.055f, 0.96f);
        roundedRect(x, y, width, height, 5f);
        if (liveState.sharedProgressIndeterminate()) {
            float segmentWidth = width * 0.24f;
            float travel = width + segmentWidth;
            float segmentX = x - segmentWidth
                    + (totalTime * 0.48f % 1f) * travel;
            float clippedX = Math.max(x, segmentX);
            float clippedRight = Math.min(x + width,
                    segmentX + segmentWidth);
            if (clippedRight > clippedX) {
                shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.96f);
                roundedRect(clippedX, y, clippedRight - clippedX,
                        height, 5f);
            }
            return;
        }
        float remaining = liveState.sharedProgressFraction();
        if (remaining > 0.002f) {
            Color timerColor = remaining > 0.55f ? SWING_STACK_GREEN
                    : remaining > 0.25f ? POT_GOLD : FOLD_RED;
            shapes.setColor(timerColor.r, timerColor.g, timerColor.b, 0.96f);
            roundedRect(x, y, width * remaining, height, 5f);
        }
    }

    float backgroundMusicPosition() {
        return backgroundMusic == null ? 0f : backgroundMusic.getPosition();
    }

    private void drawLocalHud(float width, float height) {
        // Functional parity with CoronaPoker's current LocalPlayer controls, laid
        // out horizontally: NO IR, PASAR/IR, numeric bet spinner, APOSTAR, ALL IN.
        float hudWidth = Math.min(1110f, width - 620f);
        float hudX = width / 2f - hudWidth / 2f;
        float hudY = LOCAL_HUD_Y;
        float hudHeight = LOCAL_HUD_HEIGHT;
        float infoWidth = 230f;
        float gap = 12f;
        float foldWidth = 150f;
        float checkWidth = 200f;
        float spinnerWidth = 145f;
        float betWidth = 175f;
        float allInWidth = 150f;
        float actionY = hudY + 24f;
        float actionHeight = 80f;
        float foldX = hudX + infoWidth + gap;
        float checkX = foldX + foldWidth + gap;
        float spinnerX = checkX + checkWidth + gap;
        float betX = spinnerX + spinnerWidth + gap;
        float allInX = betX + betWidth + gap;
        boolean localTurn = hasActiveLocalTurn();
        ActionControlState controls = liveState.actionControls();
        boolean autoActionVeto = activeDialog != null
                && activeDialog.isAutoAction();
        boolean preActions = !localTurn && autoPreActionsVisible();
        boolean foldEnabled = !autoActionVeto
                && ((localTurn && controls.foldEnabled()) || preActions);
        boolean checkEnabled = !autoActionVeto
                && ((localTurn && controls.callAction()
                        != ActionControlState.CallAction.DISABLED) || preActions);
        boolean betEnabled = !autoActionVeto && localTurn
                && controls.raiseAction()
                        != ActionControlState.RaiseAction.DISABLED;
        boolean allInEnabled = !autoActionVeto && localTurn
                && (controls.allInEnabled() || controls.showCards());
        Boolean settledLocalWinner = liveState.resolvedHandWinner(seats[0].name);
        boolean settledShowdown = hasSettledPresentation(
                liveState.hasHandResult(seats[0].name), settledLocalWinner);
        TableSnapshot.PlayerSnapshot liveLocalPlayer = livePlayer(seats[0]);
        boolean localFolded = shouldDimSeat(liveLocalPlayer == null
                || liveLocalPlayer.active(), settledShowdown,
                liveState.foldedThisHand(seats[0].name));
        boolean localTimedOut = liveLocalPlayer != null
                && liveLocalPlayer.timedOut();
        String lastLocalActionLabel = lastActionLabelForSeat(0);
        Color lastLocalActionColor = lastActionColorForSeat(0);
        Color foldButtonColor = preActions ? SWING_FOLD_BUTTON
                : controls.callAction() == ActionControlState.CallAction.CHECK
                ? SWING_FOLD_DANGER_BUTTON : SWING_FOLD_BUTTON;
        Color checkButtonColor = preActions ? LEGACY_CHECK
                : hudCallSurfaceColor(controls.callAction());
        Color checkTextColor = preActions ? Color.WHITE
                : hudCallTextColor(controls.callAction());
        Color betButtonColor = hudRaiseSurfaceColor(controls.raiseAction());
        Color betTextColor = hudRaiseTextColor(controls.raiseAction());
        Color allInButtonColor = LEGACY_ALL_IN;
        boolean foldSelected = preActions && queuedPreAction == 1;
        boolean checkSelected = preActions && queuedPreAction == 2;
        // A protected first press must be unmistakable without replacing the
        // action caption with a generic "CONFIRMAR". Keep every normal poker
        // colour unchanged; only the armed action becomes vivid green until
        // the second press executes it or a new control state disarms it.
        Color foldVisualColor = hudArmedSurfaceColor(
                foldSelected ? LEGACY_BET : foldButtonColor,
                armedHudTarget == 1);
        Color checkVisualColor = hudArmedSurfaceColor(
                checkSelected ? LEGACY_BET : checkButtonColor,
                armedHudTarget == 2);
        Color betVisualColor = hudArmedSurfaceColor(betButtonColor,
                armedHudTarget == 5);
        Color allInVisualColor = hudArmedSurfaceColor(allInButtonColor,
                armedHudTarget == 6);
        Color foldVisualText = armedHudTarget == 1 ? ARMED_ACTION_TEXT
                : foldSelected ? Color.BLACK : Color.WHITE;
        Color checkVisualText = armedHudTarget == 2 ? ARMED_ACTION_TEXT
                : checkSelected ? Color.BLACK : checkTextColor;
        Color betVisualText = armedHudTarget == 5 ? ARMED_ACTION_TEXT
                : betTextColor;
        Color allInVisualText = armedHudTarget == 6 ? ARMED_ACTION_TEXT
                : Color.WHITE;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);

        boolean foldHover = pointer.x >= foldX && pointer.x <= foldX + foldWidth
                && pointer.y >= actionY && pointer.y <= actionY + actionHeight;
        boolean checkHover = pointer.x >= checkX && pointer.x <= checkX + checkWidth
                && pointer.y >= actionY && pointer.y <= actionY + actionHeight;
        boolean spinnerHover = pointer.x >= spinnerX && pointer.x <= spinnerX + spinnerWidth
                && pointer.y >= actionY && pointer.y <= actionY + actionHeight;
        boolean betHover = pointer.x >= betX && pointer.x <= betX + betWidth
                && pointer.y >= actionY && pointer.y <= actionY + actionHeight;
        boolean allInHover = pointer.x >= allInX && pointer.x <= allInX + allInWidth
                && pointer.y >= actionY && pointer.y <= actionY + actionHeight;
        float foldContentAlpha = foldEnabled ? 1f : 0.34f;
        float checkContentAlpha = checkEnabled ? 1f : 0.34f;
        float betContentAlpha = betEnabled ? 1f : 0.34f;
        float allInContentAlpha = allInEnabled ? 1f : 0.34f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        float turnPulse = 0.5f + 0.5f * MathUtils.sin(totalTime * 5.2f);
        Color hudFrame = settledShowdown
                ? settledShowdownColor(Boolean.TRUE.equals(settledLocalWinner))
                : timeoutAwareRim(liveLocalPlayer,
                        localTurn ? ACTIVE_TURN_GOLD
                                : localFolded ? BUTTON_LINE : SEAT_RIM);
        shapes.setColor(hudFrame.r, hudFrame.g, hudFrame.b,
                settledShowdown ? 1f
                        : localTurn ? 0.72f + turnPulse * 0.26f
                                : localHudIdleFrameAlpha(localFolded));
        roundedRect(hudX - 8f, hudY - 8f,
                hudWidth + 16f, hudHeight + 25f, 19f);
        // A unified HUD does not need an opaque black slab. Keep a subtle
        // smoked-glass tint so the felt remains visible behind the controls.
        shapes.setColor(0.025f, 0.085f, 0.095f, 0.56f);
        roundedRect(hudX - 3f, hudY - 3f,
                hudWidth + 6f, hudHeight + 15f, 16f);
        shapes.setColor(0.01f, 0.04f, 0.055f, 0.38f);
        roundedRect(hudX + 5f, hudY - 4f, infoWidth, hudHeight, 15f);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.84f);
        roundedRect(hudX, hudY, infoWidth, hudHeight, 15f);
        shapes.setColor(0.03f, 0.06f, 0.11f, 0.98f);
        roundedRect(hudX + 10f, hudY + 10f, infoWidth - 20f, hudHeight - 20f, 11f);
        shapes.setColor(SWING_STACK_GREEN.r, SWING_STACK_GREEN.g,
                SWING_STACK_GREEN.b, 0.94f);
        roundedRect(hudX + 12f, hudY + 8f,
                infoWidth * 0.49f - 12f, 25f, 6f);
        Color localPotBackground = lastLocalActionLabel.isEmpty()
                ? BUTTON_LINE : lastLocalActionColor;
        shapes.setColor(localPotBackground.r, localPotBackground.g,
                localPotBackground.b, localPotBackground.a * 0.94f);
        roundedRect(hudX + infoWidth * 0.51f, hudY + 8f,
                infoWidth * 0.45f, 25f, 6f);
        if (localTimedOut || !lastLocalActionLabel.isEmpty()) {
            shapes.setColor(lastLocalActionColor.r, lastLocalActionColor.g,
                    lastLocalActionColor.b, lastLocalActionColor.a
                    * seatActionSurfaceAlpha(localFolded,
                            settledShowdown));
            roundedRect(hudX + 14f, hudY + 37f,
                    infoWidth - 28f, 24f, 6f);
        }
        Color hudLine = localTurn ? POT_GOLD : SEAT_RIM;
        shapes.setColor(hudLine.r, hudLine.g, hudLine.b, 0.82f);
        shapes.rect(hudX + 16f, hudY + hudHeight - 4f, infoWidth - 32f, 3f);
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b, 0.72f);
        shapes.rect(hudX + 16f, hudY + 34f, infoWidth - 32f, 2f);
        shapes.rect(hudX + 16f, hudY + 63f, infoWidth - 32f, 2f);
        shapes.rect(hudX + infoWidth / 2f, hudY + 13f, 2f, 18f);
        drawHudActionSurface(foldX, actionY, foldWidth, actionHeight,
                foldVisualColor, foldHover && foldEnabled,
                foldSelected || armedHudTarget == 1, foldEnabled);
        drawHudActionSurface(checkX, actionY, checkWidth, actionHeight,
                checkVisualColor, checkHover && checkEnabled,
                checkSelected || armedHudTarget == 2, checkEnabled);

        drawGroupedBetControl(spinnerX, betX, actionY,
                spinnerWidth, betWidth, actionHeight,
                betVisualColor, spinnerHover, betHover,
                armedHudTarget == 5, betEnabled);
        drawHudActionSurface(allInX, actionY, allInWidth, actionHeight,
                allInVisualColor, allInHover && allInEnabled,
                armedHudTarget == 6, allInEnabled);
        shapes.end();

        batch.begin();
        Seat local = seats[0];
        String turnStatus = localHudTurnStatus(localTurn, settledShowdown,
                gameText);
        if (!turnStatus.isEmpty()) {
            drawFittedCenteredInBox(localTurn ? actionFont : smallFont,
                    turnStatus, hudX + 12f, hudY + 96f,
                    infoWidth - 24f, 24f,
                    localTurn ? POT_GOLD : SEAT_RIM, 1f);
        }
        drawFittedCenteredInBox(uiFont, liveState.snapshot().localNickname(),
                hudX + 12f, hudY + 66f, infoWidth - 24f, 28f,
                Color.WHITE, 1f);
        if (localTimedOut) {
            batch.setColor(Color.WHITE);
            batch.draw(timeoutIcon, hudX + 16f, hudY + 39f, 20f, 20f);
        }
        if (!lastLocalActionLabel.isEmpty()) {
            drawFittedCenteredInBox(actionFont, lastLocalActionLabel,
                    hudX + (localTimedOut ? 40f : 18f), hudY + 37f,
                    infoWidth - (localTimedOut ? 58f : 36f), 25f,
                    localFolded ? Color.GRAY : lastActionTextColorForSeat(0),
                    seatActionTextAlpha(localFolded));
        }
        drawFittedCenteredInBox(stackFont, local.stackText,
                hudX + 12f, hudY + 10f, infoWidth * 0.49f - 14f, 20f,
                Color.WHITE, 1f);
        drawFittedCenteredInBox(stackFont, local.investedText,
                hudX + infoWidth * 0.52f, hudY + 10f,
                infoWidth * 0.43f, 20f,
                lastLocalActionLabel.isEmpty() ? Color.WHITE
                        : lastActionTextColorForSeat(0), 1f);

        drawHudActionContent(foldThumbIcon,
                preActions ? uppercase(gameText.translate("action.auto_fold"))
                        : uppercase(gameText.translate("action.no_ir")),
                foldX, actionY, foldWidth, actionHeight,
                foldVisualText, foldContentAlpha);
        drawHudActionContent(callThumbIcon,
                preActions ? uppercase(gameText.translate("action.auto_call"))
                        : callLabel(controls, gameText),
                checkX, actionY, checkWidth, actionHeight,
                checkVisualText, checkContentAlpha);
        drawHudActionContent(raiseLabel(controls, gameText), betX, actionY,
                betWidth, actionHeight, betVisualText, betContentAlpha);
        drawHudActionContent(controls.showCards()
                        ? uppercase(gameText.translate("action.mostrar"))
                        : uppercase(gameText.translate("action.all_in")),
                allInX, actionY, allInWidth, actionHeight,
                allInVisualText, allInContentAlpha);
        batch.setColor(Color.WHITE);
        drawFittedCenteredInBox(actionFont,
                "-   " + formatAmount(liveBetAmount) + "   +",
                spinnerX + 8f, actionY + 13f,
                spinnerWidth - 16f, actionHeight - 26f,
                Color.WHITE, betContentAlpha);
        batch.end();
    }

    private void drawGroupedBetControl(float spinnerX, float actionX,
            float y, float spinnerWidth, float actionWidth, float height,
            Color actionColor, boolean spinnerHover, boolean actionHover,
            boolean selected, boolean enabled) {
        float groupX = spinnerX - 6f;
        float groupWidth = actionX + actionWidth - spinnerX + 12f;
        if (selected && enabled) {
            float pulse = 0.58f + 0.42f
                    * MathUtils.sin(totalTime * 7.4f)
                    * MathUtils.sin(totalTime * 7.4f);
            shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                    0.32f + pulse * 0.40f);
            roundedRect(groupX - 7f, y - 7f,
                    groupWidth + 14f, height + 14f, 18f);
            shapes.setColor(1f, 1f, 1f, 0.52f + pulse * 0.38f);
            roundedRect(groupX - 3f, y - 3f,
                    groupWidth + 6f, height + 6f, 16f);
        }
        shapes.setColor(0f, 0f, 0f, 0.62f);
        roundedRect(groupX + 5f, y - 6f, groupWidth, height, 14f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                enabled ? spinnerHover || actionHover ? 0.98f : 0.72f : 0.24f);
        roundedRect(groupX, y, groupWidth, height, 14f);
        shapes.setColor(0.018f, 0.032f, 0.055f, enabled ? 0.98f : 0.78f);
        roundedRect(groupX + 3f, y + 4f,
                groupWidth - 6f, height - 8f, 11f);

        shapes.setColor(1f, 1f, 1f, spinnerHover && enabled ? 0.10f : 0.035f);
        roundedRect(spinnerX, y + 4f, spinnerWidth, height - 8f, 10f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                enabled ? 0.38f : 0.12f);
        shapes.rect(actionX - 6f, y + 10f, 2f, height - 20f);

        float mix = enabled ? 1f : 0.34f;
        float r = 0.018f + (actionColor.r - 0.018f) * mix;
        float g = 0.032f + (actionColor.g - 0.032f) * mix;
        float b = 0.055f + (actionColor.b - 0.055f) * mix;
        shapes.setColor(r, g, b, enabled ? 1f : 0.76f);
        roundedRect(actionX, y + 4f, actionWidth, height - 8f, 11f);
        shapes.setColor(1f, 1f, 1f,
                actionHover || selected ? 0.30f : enabled ? 0.17f : 0.06f);
        roundedRect(actionX + 9f, y + height - 19f,
                actionWidth - 18f, 9f, 4.5f);
        shapes.setColor(actionColor.r, actionColor.g, actionColor.b,
                enabled ? 1f : 0.42f);
        shapes.rect(actionX + 13f, y + 7f, actionWidth - 26f, 3f);
    }

    static Color hudCallSurfaceColor(ActionControlState.CallAction action) {
        return switch (action) {
            case CHECK -> LEGACY_CHECK;
            case CALL -> LEGACY_CALL;
            case DISABLED -> SWING_FOLD_BUTTON;
        };
    }

    static Color hudCallTextColor(ActionControlState.CallAction action) {
        return action == ActionControlState.CallAction.CALL
                ? Color.BLACK : Color.WHITE;
    }

    static String localHudTurnStatus(boolean localTurn,
            boolean settledShowdown, GdxGameText text) {
        if (settledShowdown) {
            return "";
        }
        return uppercase(text.translate(localTurn
                ? "gdx.table.hud.your_turn"
                : "gdx.table.hud.waiting_turn"), text);
    }

    static float localHudIdleFrameAlpha(boolean folded) {
        return folded ? 0.55f : 0.88f;
    }

    static Color hudRaiseSurfaceColor(ActionControlState.RaiseAction action) {
        return switch (action) {
            case RERAISE -> LEGACY_RERAISE;
            case BET, RAISE -> LEGACY_BET;
            case DISABLED -> SWING_FOLD_BUTTON;
        };
    }

    static Color hudRaiseTextColor(ActionControlState.RaiseAction action) {
        return action == ActionControlState.RaiseAction.BET
                || action == ActionControlState.RaiseAction.RAISE
                ? Color.BLACK : Color.WHITE;
    }

    static int anchoredScrollAfterWheel(int current, int maximum,
            float amountY) {
        int delta = amountY == 0f ? 0
                : Math.max(1, Math.round(Math.abs(amountY)));
        return MathUtils.clamp(current
                + (amountY < 0f ? delta : -delta), 0, maximum);
    }

    static int anchoredScrollFromTrack(float pointerY, float trackY,
            float trackHeight, float thumbHeight, int maximum) {
        if (maximum <= 0) return 0;
        float travel = Math.max(1f, trackHeight - thumbHeight);
        float ratio = MathUtils.clamp(
                (pointerY - trackY - thumbHeight / 2f) / travel,
                0f, 1f);
        return Math.round(ratio * maximum);
    }

    static float quickChatMaximumPixelScroll(int messageCount,
            float viewportHeight) {
        return Math.max(0f, messageCount * 30f
                - Math.max(0f, viewportHeight));
    }

    static float quickChatPixelScrollAfterWheel(float current, float maximum,
            float amountY) {
        return MathUtils.clamp(current - amountY * 36f, 0f,
                Math.max(0f, maximum));
    }

    static float quickChatPixelScrollFromTrack(float pointerY, float trackY,
            float trackHeight, float thumbHeight, float maximum) {
        if (maximum <= 0f) return 0f;
        float travel = Math.max(1f, trackHeight - thumbHeight);
        float ratio = MathUtils.clamp(
                (pointerY - trackY - thumbHeight / 2f) / travel,
                0f, 1f);
        return ratio * maximum;
    }

    static float anchoredPixelRowY(int rowCount, int rowIndex,
            float rowHeight, float viewportBottom, float viewportHeight,
            float scroll, float maximum) {
        float contentBottom = maximum > 0f
                ? viewportBottom - scroll
                : viewportBottom + viewportHeight - rowCount * rowHeight;
        return contentBottom + (rowCount - 1 - rowIndex) * rowHeight;
    }

    private void openUiLayer(int layer) {
        uiLayer = layer;
        if (layer != UI_CHAT) chatEditMenuOpen = false;
        if (layer != UI_GAME_LOG) gameLogEditMenuOpen = false;
        uiOpenedAt = totalTime;
    }

    static List<String> settingsGamePageLabels() {
        return GdxSettingsContract.LIVE_TABLE_GAME_PAGES;
    }

    static List<String> settingsSessionActionLabels() {
        return List.copyOf(Arrays.asList(SETTINGS_SESSION_ACTIONS.clone()));
    }

    private String settingsSessionActionLabel(int index) {
        return settingsGameText("session.action."
                + SETTINGS_SESSION_ACTION_KEYS[index]);
    }

    private boolean hasRemoteHumanPeers() {
        return tableChat != null && tableChat.snapshot().participants().stream()
                .anyMatch(participant -> !participant.local()
                        && !participant.bot() && participant.connected());
    }

    private void openSettingsSection(GdxSettingsContract.Section section) {
        beginTableSettings();
        int requested = settingsSession.sections().indexOf(section);
        settingsSession.selectTab(requested < 0 ? 0 : requested);
        openUiLayer(UI_SETTINGS);
    }

    private void handleUiClick(float x, float y) {
        switch (uiLayer) {
            case UI_SETTINGS -> handleSettingsClick(x, y);
            case UI_GAME_LOG -> handleGameLogClick(x, y);
            case UI_CHAT -> handleChatClick(x, y);
            case UI_CARD_VIEWER -> closeCardViewer();
            case UI_SCREENSHOTS -> handleScreenshotViewerClick(x, y);
            case UI_HAND_GENERATOR -> handleHandGeneratorClick(x, y);
            default -> {
            }
        }
        if (uiLayer == UI_CHAT && chatEditMenuOpen) {
            drawTableChatEditMenu();
        }
        if (uiLayer == UI_GAME_LOG && gameLogEditMenuOpen) {
            drawGameLogEditMenu();
        }
    }

    private Rectangle handGeneratorPanelBounds() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(980f, width - 80f);
        float panelH = Math.min(590f, height - 70f);
        return new Rectangle((width - panelW) / 2f,
                (height - panelH) / 2f, panelW, panelH);
    }

    private Rectangle handGeneratorPreviousBounds() {
        Rectangle panel = handGeneratorPanelBounds();
        return new Rectangle(panel.x + 34f, panel.y + 32f,
                190f, 58f);
    }

    private Rectangle handGeneratorNextBounds() {
        Rectangle panel = handGeneratorPanelBounds();
        return new Rectangle(panel.x + panel.width - 224f,
                panel.y + 32f, 190f, 58f);
    }

    private Rectangle handGeneratorProbabilityBounds() {
        Rectangle panel = handGeneratorPanelBounds();
        return new Rectangle(panel.x + panel.width / 2f - 150f,
                panel.y + panel.height - 151f, 300f, 42f);
    }

    private Rectangle handGeneratorCloseBounds() {
        Rectangle panel = handGeneratorPanelBounds();
        return new Rectangle(panel.x + panel.width - 72f,
                panel.y + panel.height - 72f, 42f, 42f);
    }

    private void handleHandGeneratorClick(float x, float y) {
        if (handGeneratorCloseBounds().contains(x, y)) {
            uiLayer = UI_NONE;
        } else if (handGeneratorPreviousBounds().contains(x, y)) {
            handGenerator.previous();
        } else if (handGeneratorNextBounds().contains(x, y)) {
            handGenerator.next();
        } else if (handGeneratorProbabilityBounds().contains(x, y)) {
            Gdx.net.openURI(GdxHandGeneratorModel.POKER_ODDS_URL);
        }
    }

    private void handleSettingsClick(float x, float y) {
        if (voiceNotesOpen) {
            handleTableVoiceNotesClick(x, y);
            return;
        }
        if (recoveryStopBarrier != null) {
            // Swing's timed stop notice is modal. The retired table cannot
            // accept actions while the client waits to reconnect.
            return;
        }
        List<GdxSettingsContract.Section> sections =
                settingsSession.sections();
        List<String> subpages = settingsSubpageLabels();
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                sections.size(), subpages.size());
        Rectangle panel = frame.panel();
        Rectangle content = frame.content();
        float panelW = panel.width;
        for (int i = 0; i < sections.size(); i++) {
            if (frame.mainTab(i).contains(x, y)) {
                settingsSession.selectTab(i);
                settingsGamePage = 0;
                settingsAppearancePage = 0;
                settingsAudioPage = 0;
                shortcutCaptureId = null;
                shortcutStatus = "";
                return;
            }
        }
        for (int i = 0; i < subpages.size(); i++) {
            if (frame.subTab(i).contains(x, y)) {
                selectSettingsSubpage(i);
                return;
            }
        }
        float contentX = content.x;
        float rowW = content.width;
        float firstRowY = frame.firstRowY();
        int contentPage = settingsContentPage();
        if (contentPage == 0) {
            GdxSettingsContract.TogglePage page = settingsAudioPage();
            if (settingsAudioPage == 0) {
                if (contains(x, y, contentX + rowW - 158f,
                        firstRowY + 10f, 62f, 46f)) {
                    adjustMasterVolume(-0.05f);
                    return;
                }
                if (contains(x, y, contentX + rowW - 84f,
                        firstRowY + 10f, 62f, 46f)) {
                    adjustMasterVolume(0.05f);
                    return;
                }
            }
            float audioFirstY = firstRowY
                    - (settingsAudioPage == 0 ? 70f : 0f);
            for (int row = 0; row < page.options().size(); row++) {
                GdxSettingsContract.ToggleOption option =
                        page.options().get(row);
                if (contains(x, y, contentX, audioFirstY - row * 70f,
                        rowW, 68f)
                        && settingsOptionEnabled(option)) {
                    if ("sonidos".equals(option.key())) {
                        toggleMasterSound();
                    } else {
                        toggleSettingsAudioOption(option);
                    }
                    return;
                }
            }
            if (GdxSettingsContract.hasVoiceRetention(page)
                    && contains(x, y, contentX,
                            audioFirstY - page.options().size() * 70f,
                            rowW, 68f)) {
                int direction = settingsStepperDirection(x, contentX, rowW);
                if (direction == 0) return;
                GdxSettingsContract.adjustVoiceRetention(
                        tableSettingsProperties(), direction);
                return;
            }
            if (GdxSettingsContract.hasVoiceRetention(page)) {
                float actionY = audioFirstY
                        - page.options().size() * 70f - 70f;
                float half = (rowW - 12f) / 2f;
                if (contains(x, y, contentX, actionY, half, 58f)) {
                    openTableVoiceNotes(false);
                    return;
                }
                if (contains(x, y, contentX + half + 12f, actionY,
                        half, 58f)) {
                    openTableVoiceNotes(true);
                    return;
                }
            }
            if (GdxSettingsContract.hasAudioDevices(page)) {
                if (contains(x, y, contentX, audioFirstY, rowW, 68f)) {
                    int direction = settingsStepperDirection(x, contentX,
                            rowW);
                    if (direction != 0) {
                        GdxAudioDevices.adjustOutput(tableSettingsProperties(),
                                direction);
                    }
                    return;
                }
                if (contains(x, y, contentX, audioFirstY - 70f,
                        rowW, 68f)) {
                    int direction = settingsStepperDirection(x, contentX,
                            rowW);
                    if (direction == 0) return;
                    GdxAudioDevices.adjustCapture(tableSettingsProperties(),
                            direction);
                    return;
                }
            }
        } else if (contentPage == 1) {
            if (settingsAppearancePage == 0) {
                if (contains(x, y, contentX, firstRowY, rowW, 68f)) {
                    adjustTableDeck(settingsStepperDirection(x, contentX,
                            rowW));
                    return;
                }
                if (presentationSettings != null && contains(x, y,
                        contentX, firstRowY - 70f, rowW, 68f)) {
                    adjustTableCardBack(settingsStepperDirection(x,
                            contentX, rowW));
                    return;
                }
                if (presentationSettings != null && contains(x, y,
                        contentX, firstRowY - 140f, rowW, 68f)) {
                    adjustTableFelt(settingsStepperDirection(x, contentX,
                            rowW));
                    return;
                }
                if (contains(x, y, contentX, firstRowY - 210f,
                        rowW, 68f)) {
                    float localX = x - contentX;
                    int direction;
                    if (localX >= rowW - 210f
                            && localX <= rowW - 148f) {
                        direction = -1;
                    } else if (localX >= rowW - 74f
                            && localX <= rowW - 12f) {
                        direction = 1;
                    } else {
                        return;
                    }
                    GdxAppearanceOptions.adjustLightLevel(
                            tableSettingsProperties(),
                            direction);
                    return;
                }
                if (contains(x, y, contentX, firstRowY - 280f,
                        rowW, 68f)) {
                    int direction = settingsStepperDirection(x, contentX,
                            rowW);
                    if (direction != 0) adjustTableWindowMode(direction);
                    return;
                }
                if (presentationSettings != null && contains(x, y,
                        contentX, firstRowY - 350f, rowW, 68f)) {
                    int direction = settingsStepperDirection(x, contentX,
                            rowW);
                    if (direction < 0) {
                        presentationSettings.selectPreviousMsaaSamples(false);
                    } else if (direction > 0) {
                        presentationSettings.selectNextMsaaSamples(false);
                    }
                    return;
                }
            } else {
                GdxSettingsContract.TogglePage page =
                        settingsAppearanceTogglePage();
                if (GdxSettingsContract.hasAppearanceAnimationOptions(page)) {
                    for (int row = 0;
                            row < GdxAppearanceOptions.ANIMATION_CHOICES.size();
                            row++) {
                        if (contains(x, y, contentX,
                                firstRowY - row * 70f, rowW, 68f)) {
                            GdxAppearanceOptions.Choice option =
                                    GdxAppearanceOptions.ANIMATION_CHOICES
                                            .get(row);
                            if (!GdxAppearanceOptions.enabled(option,
                                    tableSettingsProperties())) {
                                return;
                            }
                            int direction = settingsStepperDirection(x,
                                    contentX, rowW);
                            if (direction == 0) return;
                            GdxAppearanceOptions.adjust(option,
                                    tableSettingsProperties(), direction);
                            return;
                        }
                    }
                    return;
                }
                for (int row = 0; row < page.options().size(); row++) {
                    GdxSettingsContract.ToggleOption option =
                            page.options().get(row);
                    if (contains(x, y, contentX,
                            firstRowY - row * 70f, rowW, 68f)
                            && GdxSettingsContract.enabled(option,
                                    tableSettingsProperties(),
                                    audioControl.enabled())) {
                        toggleTablePreference(option.key(),
                                option.fallback());
                        return;
                    }
                }
            }
        } else if (contentPage == 2) {
            if (contains(x, y, contentX, firstRowY, rowW, 68f)) {
                toggleAutoButtons();
                return;
            }
            if (autoButtons && contains(x, y, contentX,
                    firstRowY - 70f, rowW, 68f)) {
                showAutoCallSettings();
                return;
            }
            if (autoButtons && contains(x, y, contentX,
                    firstRowY - 140f, rowW, 68f)) {
                autoActionPersist = !autoActionPersist;
                persistBooleanPreference("auto_action_persist", autoActionPersist);
                return;
            }
            if (autoButtons && contains(x, y, contentX,
                    firstRowY - 210f, rowW, 68f)) {
                autoModeConfirm = !autoModeConfirm;
                persistBooleanPreference("modo_auto_confirm", autoModeConfirm);
                return;
            }
            if (contains(x, y, contentX,
                    firstRowY - 280f, rowW, 68f)) {
                confirmActions = !confirmActions;
                persistBooleanPreference("confirmar_todo", confirmActions);
                return;
            }
            if (tableRebuyAllowed && contains(x, y, contentX,
                    firstRowY - 350f, rowW, 68f)) {
                autoRebuy = !autoRebuy;
                if (presentationSettings != null) {
                    presentationSettings.setAutoRebuyOnBroke(autoRebuy);
                }
                return;
            }
        } else if (contentPage == 3
                && GdxLiveSettingsPolicy.canEditGameRules(tableHost,
                        liveSettingsDraft)) {
            if (contains(x, y, contentX, firstRowY, rowW, 68f)) {
                liveSettingsDraft = liveSettingsDraft.withIwtsth(
                        !liveSettingsDraft.iwtsth());
                return;
            }
            if (GdxLiveSettingsPolicy.canEditRunItTwice(tableHost,
                    liveSettingsDraft,
                    liveState != null && liveState.runItTwiceLocked())
                    && contains(x, y, contentX, firstRowY - 70f,
                            rowW, 68f)) {
                liveSettingsDraft = liveSettingsDraft.withRunItTwice(
                        !liveSettingsDraft.runItTwice());
                return;
            }
            if (contains(x, y, contentX, firstRowY - 140f, rowW, 68f)) {
                adjustDraftRabbitHunting(settingsStepperDirection(x,
                        contentX, rowW));
                return;
            }
            if (contains(x, y, contentX, firstRowY - 210f, rowW, 68f)) {
                openDraftHandLimitDialog();
                return;
            }
        } else if (contentPage == 4
                && GdxLiveSettingsPolicy.canEditGameRules(tableHost,
                        liveSettingsDraft)) {
            float compactGap = 52f;
            float minusX = contentX + rowW - 164f;
            float plusX = contentX + rowW - 78f;
            if (contains(x, y, minusX, firstRowY, 72f, 46f)) {
                selectDraftBlindStructure(-1);
                return;
            }
            if (contains(x, y, plusX, firstRowY, 72f, 46f)) {
                selectDraftBlindStructure(1);
                return;
            }
            if (contains(x, y, minusX, firstRowY - compactGap, 72f, 46f)) {
                changeDraftBlindLevel(-1);
                return;
            }
            if (contains(x, y, plusX, firstRowY - compactGap, 72f, 46f)) {
                changeDraftBlindLevel(1);
                return;
            }
            if (contains(x, y, contentX, firstRowY - 2f * compactGap,
                    rowW, 46f)) {
                toggleDraftBlindIncrease();
                return;
            }
            if (liveSettingsDraft.blindsDouble() > 0
                    && contains(x, y, minusX,
                            firstRowY - 3f * compactGap, 72f, 46f)) {
                changeDraftBlindInterval(-1);
                return;
            }
            if (liveSettingsDraft.blindsDouble() > 0
                    && contains(x, y, plusX,
                            firstRowY - 3f * compactGap, 72f, 46f)) {
                changeDraftBlindInterval(1);
                return;
            }
            if (liveSettingsDraft.blindsDouble() > 0
                    && contains(x, y, contentX,
                            firstRowY - 4f * compactGap, rowW, 46f)) {
                toggleDraftBlindIntervalType();
                return;
            }
            if (liveSettingsDraft.blindsDouble() > 0
                    && contains(x, y, contentX,
                            firstRowY - 5f * compactGap, rowW, 46f)) {
                toggleDraftBlindCap();
                return;
            }
            if (liveSettingsDraft.blindsDouble() > 0
                    && liveSettingsDraft.blindCap() > 0d
                    && contains(x, y, minusX,
                            firstRowY - 6f * compactGap, 72f, 46f)) {
                changeDraftBlindCap(-1);
                return;
            }
            if (liveSettingsDraft.blindsDouble() > 0
                    && liveSettingsDraft.blindCap() > 0d
                    && contains(x, y, plusX,
                            firstRowY - 6f * compactGap, 72f, 46f)) {
                changeDraftBlindCap(1);
                return;
            }
            float flagsY = firstRowY - 7f * compactGap;
            float flagGap = 12f;
            float flagW = (rowW - flagGap) / 2f;
            if (contains(x, y, contentX, flagsY, flagW, 46f)) {
                liveSettingsDraft = liveSettingsDraft.withAnte(
                        !liveSettingsDraft.ante());
                return;
            }
            if (contains(x, y, contentX + flagW + flagGap,
                    flagsY, flagW, 46f)) {
                liveSettingsDraft = liveSettingsDraft.withStraddle(
                        !liveSettingsDraft.straddle());
                return;
            }
        } else if (contentPage == 6
                && GdxLiveSettingsPolicy.canEditGameRules(tableHost,
                        liveSettingsDraft)) {
            if (contains(x, y, contentX, firstRowY, rowW, 68f)) {
                adjustDraftBotDifficulty(settingsStepperDirection(x,
                        contentX, rowW));
                return;
            }
            if (GdxLiveSettingsPolicy.canEditBotRebuy(tableHost,
                    liveSettingsDraft)
                    && contains(x, y, contentX, firstRowY - 88f,
                            rowW, 68f)) {
                liveSettingsDraft = liveSettingsDraft.withBotRebuy(
                        !liveSettingsDraft.botRebuy());
                return;
            }
            if (contains(x, y, contentX, firstRowY - 176f, rowW, 68f)) {
                liveSettingsDraft = liveSettingsDraft.withBotBalanceToHumans(
                        !liveSettingsDraft.botBalanceToHumans());
                return;
            }
        } else if (contentPage == 7) {
            float compactGap = 52f;
            for (int row = 0; row < SETTINGS_SESSION_ACTIONS.length; row++) {
                if (!contains(x, y, contentX,
                        firstRowY - row * compactGap, rowW, 46f)
                        || !settingsSessionActionEnabled(row)) {
                    continue;
                }
                switch (row) {
                    case 0 -> toggleFullscreen();
                    case 1 -> {
                        requestCancelTableSettings(this::openScreenshotViewer);
                    }
                    case 2 -> {
                        requestCancelTableSettings(() -> {
                            gameLogScroll = 0;
                            openUiLayer(UI_GAME_LOG);
                        });
                    }
                    case 3 -> {
                        requestCancelTableSettings(() -> Gdx.net.openURI(
                                GdxHandGeneratorModel.ROBERT_RULES_URL));
                    }
                    case 4 -> {
                        requestCancelTableSettings(
                                () -> openUiLayer(UI_HAND_GENERATOR));
                    }
                    case 5 -> {
                        requestCancelTableSettings(this::requestLastHandChange);
                    }
                    case 6 -> {
                        requestCancelTableSettings(this::requestForceReconnect);
                    }
                    case 7 -> {
                        requestCancelTableSettings(this::requestStopGame);
                    }
                    case 8 -> {
                        requestCancelTableSettings(this::requestExit);
                    }
                    default -> {
                    }
                }
                return;
            }
        } else if (contentPage == 8) {
            List<GdxShortcutBindings.ShortcutEntry> entries
                    = shortcutBindings.editableEntries(gameText);
            int first = shortcutPage * SHORTCUT_ROWS_PER_PAGE;
            int visible = Math.min(SHORTCUT_ROWS_PER_PAGE,
                    Math.max(0, entries.size() - first));
            for (int row = 0; row < visible; row++) {
                float rowY = firstRowY - row * 70f;
                if (contains(x, y, contentX, rowY, rowW, 62f)) {
                    shortcutCaptureId = entries.get(first + row).id();
                    shortcutStatus = "prompt";
                    return;
                }
            }
        }
        if (frame.cancelButton().contains(x, y)) {
            requestCancelTableSettings(null);
            return;
        }
        if (settingsSectionHasRestoreDefaults()
                && frame.restoreButton().contains(x, y)) {
            restoreCurrentSettingsSectionDefaults();
            return;
        }
        if (frame.saveButton().contains(x, y)) {
            closeTableSettings(true);
            return;
        }
        // Clicking outside a modal dialog deliberately does nothing.
    }

    private static int settingsStepperDirection(float pointerX, float rowX,
            float rowWidth) {
        float localX = pointerX - rowX;
        if (localX >= rowWidth - 426f && localX <= rowWidth - 364f) {
            return -1;
        }
        if (localX >= rowWidth - 74f && localX <= rowWidth - 12f) {
            return 1;
        }
        return 0;
    }

    private Rectangle settingsPanelBounds() {
        return GdxSettingsLayout.panelBounds(viewport.getWorldWidth(),
                viewport.getWorldHeight());
    }

    private List<String> settingsSubpageLabels() {
        return settingsSession.subpages(
                shortcutBindings.editableEntries(gameText).size(),
                SHORTCUT_ROWS_PER_PAGE, gameText);
    }

    private int settingsSubpageIndex() {
        return switch (settingsSection()) {
            case APPEARANCE -> settingsAppearancePage;
            case AUDIO -> settingsAudioPage;
            case GAME -> settingsGamePage;
            case SHORTCUTS -> shortcutPage;
            case DEBUG -> 0;
        };
    }

    private void selectSettingsSubpage(int index) {
        switch (settingsSection()) {
            case APPEARANCE -> settingsAppearancePage = index;
            case AUDIO -> settingsAudioPage = index;
            case GAME -> settingsGamePage = index;
            case SHORTCUTS -> {
                shortcutPage = index;
                shortcutCaptureId = null;
                shortcutStatus = "";
            }
            case DEBUG -> {
            }
        }
    }

    private void adjustTableDeck(int direction) {
        if (presentationSettings == null || direction == 0) return;
        List<String> decks = presentationSettings.availableDecks();
        if (decks.isEmpty()) return;
        int current = decks.indexOf(liveDeck);
        int target = current < 0 ? 0 : Math.floorMod(current
                + Integer.signum(direction), decks.size());
        String selected = presentationSettings.selectDeck(decks.get(target),
                false);
        if (!selected.equals(liveDeck)) {
            liveDeck = selected;
            if (tablePreference("sonido_efectos", true)
                    && tablePreference("sonido_destape", true)) {
                play(uncoverSound, 0.92f, 1f);
            }
        }
    }

    private void adjustTableCardBack(int direction) {
        if (presentationSettings == null || direction == 0) return;
        String previous = presentationSettings.cardBack();
        String selected = direction < 0
                ? presentationSettings.selectPreviousCardBack(false)
                : presentationSettings.selectNextCardBack(false);
        if (!Objects.equals(previous, selected)
                && tablePreference("sonido_efectos", true)
                && tablePreference("sonido_destape", true)) {
            play(uncoverSound, 0.92f, 1f);
        }
    }

    private void adjustTableFelt(int direction) {
        if (presentationSettings == null || direction == 0) return;
        String selected = direction < 0
                ? presentationSettings.selectPreviousFelt(false)
                : presentationSettings.selectNextFelt(false);
        replaceFeltTexture(selected, true);
    }

    private GdxSettingsContract.Section settingsSection() {
        return settingsSession.section();
    }

    /** Maps the unified tabs to the existing, already wired content pages. */
    private int settingsContentPage() {
        return switch (settingsSection()) {
            case AUDIO -> 0;
            case APPEARANCE -> 1;
            case GAME -> 2 + MathUtils.clamp(settingsGamePage, 0,
                    GdxSettingsContract.LIVE_TABLE_GAME_PAGES.size() - 1);
            case SHORTCUTS -> 8;
            case DEBUG -> 9;
        };
    }

    private boolean settingsSessionActionEnabled(int row) {
        return switch (row) {
            case 5, 7 -> tableHost;
            case 6 -> tableHost && hasRemoteHumanPeers();
            default -> true;
        };
    }

    private Properties tableSettingsProperties() {
        return preferences == null
                ? EMPTY_SETTINGS_PROPERTIES : preferences.properties();
    }

    private GdxSettingsContract.TogglePage settingsAudioPage() {
        settingsAudioPage = MathUtils.clamp(settingsAudioPage, 0,
                GdxSettingsContract.AUDIO_PAGES.size() - 1);
        return GdxSettingsContract.AUDIO_PAGES.get(settingsAudioPage);
    }

    private GdxSettingsContract.TogglePage settingsAppearanceTogglePage() {
        settingsAppearancePage = MathUtils.clamp(settingsAppearancePage, 1,
                GdxSettingsContract.APPEARANCE_PAGES.size());
        return GdxSettingsContract.APPEARANCE_PAGES.get(
                settingsAppearancePage - 1);
    }

    private void showAutoCallSettings() {
        // Swing's openAutoCallMaxDialog() disarms an out-of-turn AUTO choice
        // before the threshold can be edited.  During the local turn its
        // desPrePulsarAutoTodo() guard deliberately leaves the state alone.
        boolean localTurn = hasActiveLocalTurn();
        queuedPreAction = preActionAfterOpeningAutoCallSettings(
                queuedPreAction, localTurn);
        GdxTableDialog dialog = GdxTableDialog.autoCall(
                autoCallEnabled, autoCallMax, gameText);
        dialog.result().thenAccept(saved -> {
            if (!saved) return;
            autoCallEnabled = dialog.optionEnabled();
            autoCallMax = dialog.noLimit()
                    ? 0d : dialog.autoCallAmount();
            persistBooleanPreference("auto_call_enabled", autoCallEnabled);
            if (preferences != null) {
                preferences.properties().setProperty("auto_call_max",
                        Double.toString(autoCallMax));
                if (uiLayer != UI_SETTINGS) preferences.saveDeferred();
            }
        });
        showDialog(dialog);
    }

    private void handleGameLogClick(float x, float y) {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1160f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        if (contains(x, y, panelX + panelW - 224f,
                panelY + 24f, 194f, 58f)) {
            if (finalSummary != null) {
                uiLayer = UI_NONE;
            } else {
                uiLayer = UI_NONE;
            }
            return;
        }
        if (gameLogTrackContains(x, y)) {
            updateGameLogScrollFromTrack(y);
        }
    }

    private float gameLogMaximumScroll() {
        return gameLogMaximumPixelScroll(gameLogLines().size(),
                gameLogContentBounds().height);
    }

    private boolean gameLogTrackContains(float x, float y) {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1160f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float logX = panelX + 34f;
        float logY = panelY + 105f;
        float logW = panelW - 68f;
        float logH = panelH - 230f;
        return contains(x, y, logX + logW - 30f, logY + 8f,
                28f, logH - 16f);
    }

    private void updateGameLogScrollFromTrack(float y) {
        float height = viewport.getWorldHeight();
        float panelH = Math.min(720f, height - 70f);
        float panelY = (height - panelH) / 2f;
        float logY = panelY + 105f;
        float logH = panelH - 230f;
        float trackY = logY + 14f;
        float trackHeight = logH - 28f;
        float maximum = gameLogMaximumScroll();
        if (maximum == 0f) {
            gameLogScroll = 0;
            return;
        }
        int lineCount = gameLogLines().size();
        float contentHeight = lineCount * GAME_LOG_LINE_HEIGHT;
        float thumbHeight = Math.max(44f, trackHeight
                * gameLogContentBounds().height
                / Math.max(1f, contentHeight));
        gameLogScroll = quickChatPixelScrollFromTrack(y, trackY,
                trackHeight, thumbHeight, maximum);
    }

    private boolean settingsDebugTrackContains(float x, float y) {
        if (settingsSection() != GdxSettingsContract.Section.DEBUG) {
            return false;
        }
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                settingsSession.sections().size(),
                settingsSubpageLabels().size());
        float trackX = frame.content().x + frame.content().width - 15f;
        return contains(x, y, trackX - 7f, frame.firstRowY() - 337f,
                24f, 394f);
    }

    private boolean settingsDebugViewportContains(float x, float y) {
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                settingsSession.sections().size(),
                settingsSubpageLabels().size());
        return contains(x, y, frame.content().x,
                frame.firstRowY() - 337f, frame.content().width,
                SETTINGS_DEBUG_VIEWPORT_HEIGHT);
    }

    private void updateSettingsDebugScrollFromTrack(float y) {
        List<String> lines = settingsDebugLines();
        float maximum = settingsDebugMaximumPixelScroll(lines.size());
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                settingsSession.sections().size(),
                settingsSubpageLabels().size());
        float thumbHeight = settingsDebugThumbHeight(lines.size());
        settingsDebugScroll = quickChatPixelScrollFromTrack(y,
                frame.firstRowY() - 337f, SETTINGS_DEBUG_VIEWPORT_HEIGHT,
                thumbHeight, maximum);
    }

    private void handleChatClick(float x, float y) {
        if (!chatImageMode) {
            handleQuickChatClick(x, y);
            return;
        }
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1140f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float inputY = panelY + 34f;
        float sendX = panelX + panelW - 214f;
        float emojiX = sendX - 150f;
        float historyY = panelY + 118f;
        float historyH = panelH - 220f;
        if (contains(x, y, panelX + panelW - 216f,
                panelY + panelH - 62f, 136f, 34f)) {
            if (preferences != null) {
                GdxChatImageHistory.clear(preferences.properties());
            }
            tableImageHistory = List.of();
            tableGalleryMedia.clear();
            if (preferences != null) preferences.saveDeferred();
            chatError = "";
            return;
        }
        if (contains(x, y, panelX + 34f, historyY,
                panelW - 68f, historyH)) {
            int visible = Math.min(8, tableImageHistory.size());
            for (int index = 0; index < visible; index++) {
                if (tableGalleryCellBounds(index, panelX + 34f, historyY,
                        panelW - 68f, historyH).contains(x, y)) {
                    chatDraft = tableImageHistory.get(index);
                    sendTableChat();
                    return;
                }
            }
            return;
        }
        if (contains(x, y, panelX + 34f, inputY, panelW - 416f, 60f)) {
            return;
        }
        if (contains(x, y, emojiX, inputY, 132f, 60f)) {
            if (chatImageMode) {
                chatImageMode = false;
                emojiPickerOpen = false;
                chatDraft = "";
            } else {
                emojiPickerOpen = !emojiPickerOpen;
            }
            chatError = "";
            return;
        }
        if (contains(x, y, sendX, inputY, 180f, 60f)) {
            sendTableChat();
            return;
        }
        if (contains(x, y, panelX + panelW - 62f,
                panelY + panelH - 62f, 34f, 34f)) {
            uiLayer = UI_NONE;
            emojiPickerOpen = false;
            return;
        }
        if (!emojiPickerOpen) return;

        float pickerX = panelX + panelW - 510f;
        float pickerY = panelY + 124f;
        float cell = 52f;
        if (contains(x, y, pickerX + 18f, pickerY + 14f, 84f, 38f)) {
            emojiPage = Math.max(0, emojiPage - 1);
            return;
        }
        int pageCount = (EMOJI_COUNT + EMOJI_PAGE_SIZE - 1) / EMOJI_PAGE_SIZE;
        if (contains(x, y, pickerX + 398f, pickerY + 14f, 84f, 38f)) {
            emojiPage = Math.min(pageCount - 1, emojiPage + 1);
            return;
        }
        float gridY = pickerY + 70f;
        for (int row = 0; row < EMOJI_ROWS; row++) {
            for (int column = 0; column < EMOJI_COLUMNS; column++) {
                int offset = row * EMOJI_COLUMNS + column;
                int id = emojiPage * EMOJI_PAGE_SIZE + offset + 1;
                if (id > EMOJI_COUNT) continue;
                float cellX = pickerX + 18f + column * (cell + 6f);
                float cellY = gridY + (EMOJI_ROWS - 1 - row) * (cell + 5f);
                if (contains(x, y, cellX, cellY, cell, cell)) {
                    appendEmoji(id);
                    return;
                }
            }
        }
    }

    static Rectangle tableGalleryCellBounds(int index, float x, float y,
            float width, float height) {
        float padding = 14f;
        float gap = 12f;
        float cellWidth = (width - 2f * padding - 3f * gap) / 4f;
        float cellHeight = (height - 2f * padding - gap) / 2f;
        int column = index % 4;
        int row = index / 4;
        return new Rectangle(x + padding + column * (cellWidth + gap),
                y + height - padding - cellHeight
                        - row * (cellHeight + gap),
                cellWidth, cellHeight);
    }

    private float quickChatWidth() {
        // FastChatDialog uses exactly 30% of the active table width.  Do not
        // cap ultrawide layouts: that silently changed the Swing geometry.
        return Math.min(viewport.getWorldWidth() - 2f * QUICK_CHAT_SCREEN_MARGIN,
                viewport.getWorldWidth() * QUICK_CHAT_WIDTH_RATIO);
    }

    private float quickChatHeight() {
        // Swing anchors the undecorated popup to the lower-left corner and
        // gives it the complete LocalPlayer component height.  GDX has no
        // LocalPlayer widget, so derive the equivalent live visual envelope:
        // local HUD plus both rotated hole cards.  This follows deck aspect
        // ratio and table scaling instead of using a guessed fixed height.
        float visualTop = LOCAL_HUD_Y + LOCAL_HUD_HEIGHT + 17f;
        Seat local = seats[0];
        if (local != null && activeCardBack() != null) {
            for (int slot = 0; slot < 2; slot++) {
                LiveCardPlacement card = liveHolePlacement(local, slot);
                float radians = card.rotation * MathUtils.degreesToRadians;
                float rotatedHalfHeight = Math.abs(MathUtils.cos(radians))
                        * card.height / 2f
                        + Math.abs(MathUtils.sin(radians))
                        * card.width / 2f;
                visualTop = Math.max(visualTop, card.y + rotatedHalfHeight);
            }
        }
        return MathUtils.clamp(visualTop - QUICK_CHAT_SCREEN_MARGIN,
                170f, viewport.getWorldHeight()
                        - 2f * QUICK_CHAT_SCREEN_MARGIN);
    }

    private Rectangle tableChatInputBounds() {
        if (!chatImageMode) {
            return new Rectangle(32f, 32f, quickChatWidth() - 28f, 42f);
        }
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1140f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        return new Rectangle(panelX + 34f, panelY + 34f,
                panelW - 416f, 60f);
    }

    private Rectangle quickChatHistoryBounds() {
        float panelX = 18f;
        float panelY = 18f;
        return new Rectangle(panelX + 14f, panelY + 100f,
                quickChatWidth() - 28f, quickChatHeight() - 114f);
    }

    private boolean quickChatScrollTrackContains(float x, float y) {
        if (quickChatScrollMaximum <= 0f) return false;
        Rectangle history = quickChatHistoryBounds();
        return contains(x, y, history.x + history.width - 26f,
                history.y + 8f, 24f, history.height - 16f);
    }

    private float quickChatScrollThumbHeight(float trackHeight) {
        float viewportHeight = Math.max(0f,
                quickChatHistoryBounds().height - 16f);
        float contentHeight = viewportHeight + quickChatScrollMaximum;
        return quickChatScrollMaximum <= 0f ? trackHeight
                : Math.max(34f, trackHeight * viewportHeight
                        / Math.max(1f, contentHeight));
    }

    private void updateQuickChatScrollFromTrack(float y) {
        Rectangle history = quickChatHistoryBounds();
        float trackY = history.y + 8f;
        float trackHeight = history.height - 16f;
        quickChatScroll = quickChatPixelScrollFromTrack(y, trackY,
                trackHeight, quickChatScrollThumbHeight(trackHeight),
                quickChatScrollMaximum);
    }

    private void openTableChatEditMenu(float x, float y) {
        float width = 270f;
        float height = 208f;
        chatEditMenuX = MathUtils.clamp(x, 10f,
                viewport.getWorldWidth() - width - 10f);
        chatEditMenuY = MathUtils.clamp(y - height, 10f,
                viewport.getWorldHeight() - height - 10f);
        chatEditMenuOpen = true;
    }

    private void handleTableChatEditMenuClick(float x, float y) {
        final float width = 270f;
        final float rowHeight = 48f;
        if (!contains(x, y, chatEditMenuX, chatEditMenuY,
                width, 208f)) {
            chatEditMenuOpen = false;
            return;
        }
        int row = MathUtils.clamp((int) ((y - chatEditMenuY - 8f)
                / rowHeight), 0, 3);
        chatEdit.focus("tableChat", chatDraft);
        switch (row) {
            case 0 -> chatEdit.selectAll(chatDraft);
            case 1 -> {
                String pasted = Gdx.app.getClipboard().getContents();
                if (pasted != null && !pasted.isEmpty()) {
                    chatDraft = chatEdit.replaceSelection(chatDraft,
                            singleLine(pasted), 360);
                }
            }
            case 2 -> {
                if (chatEdit.hasSelection(chatDraft)) {
                    Gdx.app.getClipboard().setContents(
                            chatEdit.selectedText(chatDraft));
                }
            }
            case 3 -> {
                if (chatEdit.hasSelection(chatDraft)) {
                    Gdx.app.getClipboard().setContents(
                            chatEdit.selectedText(chatDraft));
                    chatDraft = chatEdit.delete(chatDraft);
                }
            }
            default -> {
            }
        }
        chatEditMenuOpen = false;
    }

    private void drawTableChatEditMenu() {
        float alpha = uiFade();
        float width = 270f;
        float height = 208f;
        float rowHeight = 48f;
        chatEdit.focus("tableChat", chatDraft);
        boolean selected = chatEdit.hasSelection(chatDraft);
        String clipboard = Gdx.app.getClipboard().getContents();
        boolean[] enabled = { !chatDraft.isEmpty(),
            clipboard != null && !clipboard.isEmpty(), selected, selected };
        String[] labels = {
            uppercase(gameText.translate("ui.seleccionar_todo")),
            uppercase(gameText.translate("ui.pegar")),
            uppercase(gameText.translate("ui.copiar")),
            uppercase(gameText.translate("ui.cortar"))
        };
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.58f * alpha);
        roundedRect(chatEditMenuX + 6f, chatEditMenuY - 6f,
                width, height, 10f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.76f * alpha);
        roundedRect(chatEditMenuX - 1f, chatEditMenuY - 1f,
                width + 2f, height + 2f, 10f);
        shapes.setColor(0.006f, 0.018f, 0.031f, 0.99f * alpha);
        roundedRect(chatEditMenuX, chatEditMenuY, width, height, 9f);
        for (int row = 0; row < labels.length; row++) {
            float rowY = chatEditMenuY + 8f + row * rowHeight;
            boolean hover = enabled[row] && contains(pointer.x, pointer.y,
                    chatEditMenuX + 8f, rowY, width - 16f, rowHeight);
            shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                    (hover ? 0.22f : 0.045f) * alpha);
            roundedRect(chatEditMenuX + 8f, rowY,
                    width - 16f, rowHeight - 2f, 6f);
        }
        shapes.end();
        batch.begin();
        for (int row = 0; row < labels.length; row++) {
            float rowY = chatEditMenuY + 8f + row * rowHeight;
            drawLeftInBox(smallFont, labels[row], chatEditMenuX + 24f,
                    rowY + 4f, width - 48f, rowHeight - 8f,
                    enabled[row] ? Color.WHITE : Color.GRAY, alpha);
        }
        batch.end();
    }

    private void handleQuickChatClick(float x, float y) {
        float panelX = 18f;
        float panelY = 18f;
        float panelW = quickChatWidth();
        float panelH = quickChatHeight();
        float toggleY = panelY + 58f;
        if (contains(x, y, panelX + 14f, toggleY,
                panelW - 28f, 34f)) {
            quickChatAutoClose = !quickChatAutoClose;
            return;
        }
        // Like Swing's undecorated fast-chat window, clicks outside do not
        // trigger table controls. ESC (or the fast-chat button) closes it.
        if (!contains(x, y, panelX, panelY, panelW, panelH)) {
            return;
        }
    }

    private void recallQuickChat(int direction) {
        if (quickChatHistory.isEmpty()) return;
        if (quickChatHistoryIndex == quickChatHistory.size()) {
            quickChatPendingDraft = chatDraft;
        }
        quickChatHistoryIndex = MathUtils.clamp(
                quickChatHistoryIndex + Integer.signum(direction),
                0, quickChatHistory.size());
        chatDraft = quickChatHistoryIndex == quickChatHistory.size()
                ? quickChatPendingDraft
                : quickChatHistory.get(quickChatHistoryIndex);
        chatEdit.focus("tableChat", chatDraft);
        chatEdit.end(chatDraft, false);
        chatError = "";
    }

    private void rememberQuickChat(String message) {
        quickChatHistory.add(message);
        while (quickChatHistory.size() > QUICK_CHAT_HISTORY_LIMIT) {
            quickChatHistory.remove(0);
        }
        quickChatHistoryIndex = quickChatHistory.size();
        quickChatPendingDraft = "";
    }

    private void appendEmoji(int id) {
        if (id < 1 || id > EMOJI_COUNT) return;
        String token = " #" + id + "# ";
        chatEdit.focus("tableChat", chatDraft);
        chatDraft = chatEdit.replaceSelection(chatDraft, token, 360);
        chatError = "";
    }

    private void sendTableChat() {
        if (tableChat == null || chatSending) return;
        String message = chatDraft.trim();
        if (message.isEmpty()) return;
        if (chatImageMode && totalTime < tableImageSendAllowedAt) {
            chatError = uppercase(gameText.translate(
                    "gdx.lobby.image_cooldown"));
            return;
        }
        chatSending = true;
        chatError = "";
        java.util.concurrent.CompletionStage<Void> delivery;
        boolean quickChat = !chatImageMode;
        String submittedImage = chatImageMode ? message : null;
        try {
            if (chatImageMode) {
                URI uri = URI.create(message);
                String scheme = uri.getScheme();
                if (scheme == null || (!("http".equalsIgnoreCase(scheme))
                        && !("https".equalsIgnoreCase(scheme)))) {
                    throw new IllegalArgumentException(uppercase(
                            gameText.translate("gdx.lobby.invalid_image_url")));
                }
                delivery = tableChat.sendImage(message);
            } else {
                delivery = tableChat.sendText(message);
                rememberQuickChat(message);
            }
        } catch (RuntimeException invalid) {
            chatSending = false;
            chatError = invalid.getMessage() == null
                    ? uppercase(gameText.translate(
                            "gdx.table.chat.invalid_message"))
                    : invalid.getMessage();
            return;
        }
        delivery.whenComplete((ignored, failure) ->
                Gdx.app.postRunnable(() -> {
                    chatSending = false;
                    if (failure == null) {
                        chatDraft = "";
                        if (submittedImage != null) {
                            tableImageSendAllowedAt = totalTime
                                    + IMAGE_SEND_COOLDOWN_SECONDS;
                            tableImageHistory = preferences == null
                                    ? List.of(submittedImage)
                                    : GdxChatImageHistory.remember(
                                            preferences.properties(),
                                            submittedImage, true);
                            tableGalleryMedia.refresh(tableImageHistory, 8,
                                    "table-history");
                            if (preferences != null) preferences.saveDeferred();
                            closeTableChat();
                        }
                        if (quickChat && quickChatAutoClose) {
                            uiLayer = UI_NONE;
                        }
                    } else {
                        Throwable root = failure instanceof CompletionException
                                && failure.getCause() != null
                                ? failure.getCause() : failure;
                        chatError = root.getMessage() == null
                                ? uppercase(gameText.translate(
                                        "gdx.table.chat.send_failed"))
                                : root.getMessage();
                    }
                }));
    }

    private List<String> gameLogLines() {
        GdxGameLogSink.Snapshot snapshot = gameLog.snapshot();
        ArrayList<String> source = new ArrayList<>(snapshot.lines());
        ArrayList<String> result = new ArrayList<>();
        for (String line : source) {
            GdxGameLogFormatter.wrapLine(result, line == null ? "" : line, 92);
        }
        return result;
    }

    private Rectangle gameLogContentBounds() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1160f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float logX = panelX + 34f;
        float logY = panelY + 105f;
        float logW = panelW - 68f;
        float logH = panelH - 230f;
        return new Rectangle(logX + 14f, logY + 14f,
                logW - 44f, logH - 28f);
    }

    private int gameLogLineAt(float x, float y) {
        Rectangle content = gameLogContentBounds();
        if (!content.contains(x, y)) return -1;
        List<String> lines = gameLogLines();
        if (lines.isEmpty()) return -1;
        float maximum = gameLogMaximumPixelScroll(lines.size(),
                content.height);
        return anchoredPixelRowAt(lines.size(), GAME_LOG_LINE_HEIGHT,
                content.y, content.height, gameLogScroll, maximum, y);
    }

    static float gameLogMaximumPixelScroll(int lineCount,
            float viewportHeight) {
        return Math.max(0f, lineCount * GAME_LOG_LINE_HEIGHT
                - Math.max(0f, viewportHeight));
    }

    static int anchoredPixelRowAt(int rowCount, float rowHeight,
            float viewportBottom, float viewportHeight, float scroll,
            float maximum, float pointerY) {
        if (rowCount <= 0 || rowHeight <= 0f
                || pointerY < viewportBottom
                || pointerY >= viewportBottom + viewportHeight) return -1;
        float contentBottom = maximum > 0f
                ? viewportBottom - MathUtils.clamp(scroll, 0f, maximum)
                : viewportBottom + viewportHeight - rowCount * rowHeight;
        int fromBottom = (int) Math.floor(
                (pointerY - contentBottom) / rowHeight);
        if (fromBottom < 0 || fromBottom >= rowCount) return -1;
        return rowCount - 1 - fromBottom;
    }

    private void copySelectedGameLogLines() {
        String selected = selectedGameLogText(gameLogLines(),
                gameLogSelectionAnchor, gameLogSelectionCaret);
        if (!selected.isEmpty()) {
            Gdx.app.getClipboard().setContents(selected);
        }
    }

    static String selectedGameLogText(List<String> lines, int anchor,
            int caret) {
        if (lines == null || lines.isEmpty() || anchor < 0 || caret < 0) {
            return "";
        }
        int first = MathUtils.clamp(Math.min(anchor, caret), 0,
                lines.size() - 1);
        int last = MathUtils.clamp(Math.max(anchor, caret), 0,
                lines.size() - 1);
        return lines.subList(first, last + 1).stream()
                .map(GdxGameLogFormatter::visibleText)
                .collect(java.util.stream.Collectors.joining(
                        System.lineSeparator()));
    }

    private void openGameLogEditMenu(float x, float y) {
        final float menuWidth = 270f;
        final float menuHeight = 112f;
        gameLogEditMenuX = MathUtils.clamp(x, 10f,
                viewport.getWorldWidth() - menuWidth - 10f);
        gameLogEditMenuY = MathUtils.clamp(y - menuHeight, 10f,
                viewport.getWorldHeight() - menuHeight - 10f);
        gameLogEditMenuOpen = true;
    }

    private void handleGameLogEditMenuClick(float x, float y) {
        final float menuWidth = 270f;
        final float menuHeight = 112f;
        final float rowHeight = 48f;
        if (!contains(x, y, gameLogEditMenuX, gameLogEditMenuY,
                menuWidth, menuHeight)) {
            gameLogEditMenuOpen = false;
            return;
        }
        int row = MathUtils.clamp((int) ((y - gameLogEditMenuY - 8f)
                / rowHeight), 0, 1);
        if (row == 0) {
            List<String> lines = gameLogLines();
            gameLogSelectionAnchor = lines.isEmpty() ? -1 : 0;
            gameLogSelectionCaret = lines.size() - 1;
        } else {
            copySelectedGameLogLines();
        }
        gameLogEditMenuOpen = false;
    }

    private void drawGameLogEditMenu() {
        float alpha = uiFade();
        final float width = 270f;
        final float height = 112f;
        final float rowHeight = 48f;
        String[] labels = {
            uppercase(gameText.translate("ui.seleccionar_todo")),
            uppercase(gameText.translate("ui.copiar"))
        };
        boolean[] enabled = { !gameLogLines().isEmpty(),
            gameLogSelectionAnchor >= 0 && gameLogSelectionCaret >= 0 };
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.58f * alpha);
        roundedRect(gameLogEditMenuX + 6f, gameLogEditMenuY - 6f,
                width, height, 10f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.76f * alpha);
        roundedRect(gameLogEditMenuX - 1f, gameLogEditMenuY - 1f,
                width + 2f, height + 2f, 10f);
        shapes.setColor(0.006f, 0.018f, 0.031f, 0.99f * alpha);
        roundedRect(gameLogEditMenuX, gameLogEditMenuY, width, height, 9f);
        for (int row = 0; row < labels.length; row++) {
            float rowY = gameLogEditMenuY + 8f + row * rowHeight;
            boolean hover = enabled[row] && contains(pointer.x, pointer.y,
                    gameLogEditMenuX + 8f, rowY, width - 16f, rowHeight);
            shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                    (hover ? 0.22f : 0.045f) * alpha);
            roundedRect(gameLogEditMenuX + 8f, rowY,
                    width - 16f, rowHeight - 2f, 6f);
        }
        shapes.end();
        batch.begin();
        for (int row = 0; row < labels.length; row++) {
            float rowY = gameLogEditMenuY + 8f + row * rowHeight;
            drawLeftInBox(smallFont, labels[row], gameLogEditMenuX + 24f,
                    rowY + 4f, width - 48f, rowHeight - 8f,
                    enabled[row] ? Color.WHITE : Color.GRAY, alpha);
        }
        batch.end();
    }

    private List<GdxGameLogFormatter.Run> cachedGameLogRuns(String value) {
        if (gameLogRunCache.size() >= 4096) gameLogRunCache.clear();
        return gameLogRunCache.computeIfAbsent(value,
                GdxGameLogFormatter::runs);
    }

    private static boolean contains(float px, float py, float x, float y,
            float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private void drawUiLayer() {
        if (uiLayer == UI_NONE) {
            return;
        }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        switch (uiLayer) {
            case UI_SETTINGS -> drawSettingsDialog();
            case UI_GAME_LOG -> drawGameLogDialog();
            case UI_CHAT -> drawChatDialog();
            case UI_CARD_VIEWER -> drawCardViewer();
            case UI_SCREENSHOTS -> drawScreenshotViewer();
            case UI_HAND_GENERATOR -> drawHandGeneratorDialog();
            default -> {
            }
        }
    }

    private void drawHandGeneratorDialog() {
        float alpha = uiFade();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        Rectangle panel = handGeneratorPanelBounds();
        Rectangle previous = handGeneratorPreviousBounds();
        Rectangle next = handGeneratorNextBounds();
        Rectangle probability = handGeneratorProbabilityBounds();
        Rectangle close = handGeneratorCloseBounds();
        GdxHandGeneratorModel.Example example = handGenerator.current();
        boolean previousEnabled = handGenerator.canPrevious();
        boolean nextEnabled = handGenerator.canNext();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.72f * alpha);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.56f * alpha);
        roundedRect(panel.x + 10f, panel.y - 11f,
                panel.width, panel.height, 20f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.86f * alpha);
        roundedRect(panel.x - 2f, panel.y - 2f,
                panel.width + 4f, panel.height + 4f, 20f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.985f * alpha);
        roundedRect(panel.x, panel.y, panel.width, panel.height, 18f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.82f * alpha);
        shapes.rect(panel.x + 30f, panel.y + panel.height - 9f,
                panel.width - 60f, 3f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.07f * alpha);
        roundedRect(panel.x + 34f, panel.y + 112f,
                panel.width - 68f, panel.height - 292f, 13f);
        drawDialogButton(previous.x, previous.y, previous.width,
                previous.height, CYAN, previousEnabled
                && previous.contains(pointer), previousEnabled ? alpha : 0.28f * alpha);
        drawDialogButton(next.x, next.y, next.width, next.height,
                CYAN, nextEnabled && next.contains(pointer),
                nextEnabled ? alpha : 0.28f * alpha);
        if (probability.contains(pointer)) {
            shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.14f * alpha);
            roundedRect(probability.x, probability.y,
                    probability.width, probability.height, 8f);
        }
        shapes.end();

        float cardGap = 14f;
        float cardHeight = Math.min(252f, panel.height - 310f);
        float cardWidth = cardHeight * 0.714f;
        int cardCount = example.cards().size();
        float cardsWidth = cardWidth * cardCount
                + cardGap * Math.max(0, cardCount - 1);
        float cardsX = panel.x + (panel.width - cardsWidth) / 2f;
        float cardsY = panel.y + 125f;
        batch.begin();
        batch.setColor(1f, 1f, 1f, alpha);
        for (int index = 0; index < example.cards().size(); index++) {
            Texture face = liveCardFace(example.cards().get(index));
            useRoundedCardShader();
            batch.draw(face, cardsX + index * (cardWidth + cardGap),
                    cardsY, cardWidth, cardHeight);
            batch.flush();
            batch.setShader(null);
        }
        String handName = gameText.translate(example.translationKey());
        drawFittedCenteredInBox(uiFont,
                uppercase(gameText.translate("gdx.hand_generator.title")),
                panel.x + 90f, panel.y + panel.height - 75f,
                panel.width - 180f, 48f, POT_GOLD, alpha);
        drawFittedCenteredInBox(actionFont, handName,
                panel.x + 100f, panel.y + panel.height - 126f,
                panel.width - 200f, 46f, Color.WHITE, alpha);
        drawFittedCenteredInBox(smallFont,
                uppercase(gameText.translate(
                        "gdx.hand_generator.probability",
                        example.probability())),
                probability.x, probability.y, probability.width,
                probability.height, POT_GOLD, alpha);
        drawFittedCenteredInBox(actionFont,
                "‹  " + uppercase(gameText.translate(
                        "gdx.hand_generator.previous")),
                previous.x, previous.y, previous.width, previous.height,
                Color.WHITE, previousEnabled ? alpha : 0.28f * alpha);
        drawFittedCenteredInBox(actionFont,
                uppercase(gameText.translate("gdx.hand_generator.next"))
                        + "  ›",
                next.x, next.y, next.width, next.height,
                Color.WHITE, nextEnabled ? alpha : 0.28f * alpha);
        drawFittedCenteredInBox(actionFont, "×", close.x, close.y,
                close.width, close.height, Color.WHITE, alpha);
        drawFittedCenteredInBox(smallFont,
                (handGenerator.index() + 1) + " / " + handGenerator.size(),
                panel.x + panel.width / 2f - 80f, panel.y + 42f,
                160f, 38f, new Color(0xb8c5d6ff), alpha);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawCardViewer() {
        Texture texture = cardViewerTexture;
        if (texture == null) {
            closeCardViewer();
            return;
        }
        float alpha = uiFade();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        Rectangle card = cardViewerBounds(texture.getWidth(),
                texture.getHeight(), width, height);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.80f * alpha);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.46f * alpha);
        roundedRect(card.x - 18f, card.y - 20f,
                card.width + 36f, card.height + 38f, 22f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.90f * alpha);
        roundedRect(card.x - 5f, card.y - 5f,
                card.width + 10f, card.height + 10f, 18f);
        shapes.end();

        batch.begin();
        batch.setColor(1f, 1f, 1f, alpha);
        if (cardViewerFaceUp) {
            usePerspectiveCardShader(texture, MathUtils.PI,
                    card.height / card.width);
            float canvasW = card.width * 1.5f;
            float canvasH = card.height * 1.5f;
            batch.draw(activeCardBack(),
                    card.x + card.width / 2f - canvasW / 2f,
                    card.y + card.height / 2f - canvasH / 2f,
                    canvasW, canvasH);
        } else {
            useRoundedCardShader();
            batch.draw(texture, card.x, card.y, card.width, card.height);
        }
        batch.flush();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        drawFittedCenteredInBox(smallFont, uppercase(gameText.translate(
                "gdx.card_viewer.close_hint")),
                width / 2f - 220f, Math.max(18f, card.y - 62f),
                440f, 40f, new Color(0xe8edf4ff), alpha);
        batch.end();
    }

    static Rectangle cardViewerBounds(int textureWidth, int textureHeight,
            float worldWidth, float worldHeight) {
        if (textureWidth <= 0 || textureHeight <= 0
                || worldWidth <= 0f || worldHeight <= 0f) {
            return new Rectangle();
        }
        float scale = Math.min(1f, Math.min(
                (worldWidth * 0.72f) / textureWidth,
                (worldHeight * 0.78f) / textureHeight));
        float width = textureWidth * scale;
        float height = textureHeight * scale;
        return new Rectangle((worldWidth - width) / 2f,
                (worldHeight - height) / 2f, width, height);
    }

    private void captureScreenshot() {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        if (width <= 0 || height <= 0) return;
        // GPU readback must happen on the render thread. PNG encoding and disk
        // I/O do not, so keep them away from frame pacing.
        byte[] rgba = ScreenUtils.getFrameBufferPixels(
                0, 0, width, height, true);
        Path file = screenshotDirectory().resolve(
                screenshotFilename(System.currentTimeMillis()));
        String savedText = uppercase(gameText.translate(
                "gdx.screenshot.saved"));
        String failedText = uppercase(gameText.translate(
                "gdx.screenshot.save_failed"));
        Thread writer = new Thread(() -> {
            String result;
            try {
                Files.createDirectories(file.getParent());
                Pixmap pixmap = new Pixmap(width, height,
                        Pixmap.Format.RGBA8888);
                try {
                    BufferUtils.copy(rgba, 0, pixmap.getPixels(), rgba.length);
                    PixmapIO.writePNG(new FileHandle(file.toFile()), pixmap);
                } finally {
                    pixmap.dispose();
                }
                result = savedText;
            } catch (RuntimeException | IOException failure) {
                result = failedText;
            }
            String notice = result;
            if (Gdx.app != null) {
                Gdx.app.postRunnable(() -> {
                    screenshotToast = notice;
                    screenshotToastUntil = totalTime + 2f;
                });
            }
        }, "coronapoker-gdx-screenshot");
        writer.setDaemon(true);
        writer.start();
    }

    static Path screenshotDirectory() {
        return Path.of(System.getProperty("user.home"),
                ".coronapoker", "Screenshots");
    }

    static String screenshotFilename(long timestamp) {
        return "coronapoker_screenshot_" + timestamp + ".png";
    }

    static boolean isScreenshotFile(Path file) {
        if (file == null || file.getFileName() == null) return false;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("coronapoker_screenshot_")
                && name.endsWith(".png");
    }

    private void openScreenshotViewer() {
        refreshScreenshotFiles(0);
        openUiLayer(UI_SCREENSHOTS);
    }

    private void refreshScreenshotFiles(int preferredIndex) {
        try {
            if (Files.isDirectory(screenshotDirectory())) {
                try (var paths = Files.list(screenshotDirectory())) {
                    screenshotFiles = paths.filter(Files::isRegularFile)
                            .filter(CoronaPokerGdxTable::isScreenshotFile)
                            .sorted((left, right) -> right.getFileName()
                                    .toString().compareToIgnoreCase(
                                            left.getFileName().toString()))
                            .toList();
                }
            } else {
                screenshotFiles = List.of();
            }
            screenshotError = "";
        } catch (IOException failure) {
            screenshotFiles = List.of();
            screenshotError = uppercase(gameText.translate(
                    "gdx.screenshot.folder_failed"));
        }
        screenshotIndex = screenshotFiles.isEmpty() ? 0
                : MathUtils.clamp(preferredIndex, 0,
                        screenshotFiles.size() - 1);
        loadScreenshotTexture();
    }

    private void closeScreenshotViewer() {
        uiLayer = UI_NONE;
        disposeScreenshotTexture();
        screenshotFiles = List.of();
        screenshotError = "";
        screenshotOperationPending = false;
    }

    private void disposeScreenshotTexture() {
        if (screenshotTexture != null) {
            screenshotTexture.dispose();
            screenshotTexture = null;
        }
    }

    private void loadScreenshotTexture() {
        disposeScreenshotTexture();
        if (screenshotFiles.isEmpty()) return;
        screenshotIndex = MathUtils.clamp(screenshotIndex,
                0, screenshotFiles.size() - 1);
        try {
            screenshotTexture = new Texture(Gdx.files.absolute(
                    screenshotFiles.get(screenshotIndex).toString()), true);
            screenshotTexture.setFilter(TextureFilter.MipMapLinearLinear,
                    TextureFilter.Linear);
            screenshotError = "";
        } catch (RuntimeException failure) {
            screenshotError = uppercase(gameText.translate(
                    "gdx.screenshot.open_failed"));
        }
    }

    private void showRelativeScreenshot(int delta) {
        int target = screenshotIndex + delta;
        if (target < 0 || target >= screenshotFiles.size()) return;
        screenshotIndex = target;
        loadScreenshotTexture();
    }

    private void handleScreenshotViewerClick(float x, float y) {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        if (contains(x, y, width - 76f, height - 76f, 48f, 48f)) {
            closeScreenshotViewer();
            return;
        }
        if (screenshotIndex > 0
                && contains(x, y, 24f, height / 2f - 70f, 64f, 140f)) {
            showRelativeScreenshot(-1);
        } else if (screenshotIndex + 1 < screenshotFiles.size()
                && contains(x, y, width - 88f,
                        height / 2f - 70f, 64f, 140f)) {
            showRelativeScreenshot(1);
        } else if (screenshotTexture != null && !screenshotOperationPending
                && screenshotCopyBounds(width).contains(x, y)) {
            copyCurrentScreenshot();
        } else if (screenshotTexture != null && !screenshotOperationPending
                && screenshotDeleteBounds(width).contains(x, y)) {
            confirmDeleteCurrentScreenshot();
        }
    }

    static Rectangle screenshotCopyBounds(float worldWidth) {
        return new Rectangle(worldWidth / 2f - 258f, 24f, 240f, 58f);
    }

    static Rectangle screenshotDeleteBounds(float worldWidth) {
        return new Rectangle(worldWidth / 2f + 18f, 24f, 240f, 58f);
    }

    static boolean isManagedScreenshot(Path directory, Path file) {
        if (directory == null || file == null || !isScreenshotFile(file)) {
            return false;
        }
        Path root = directory.toAbsolutePath().normalize();
        Path candidate = file.toAbsolutePath().normalize();
        return root.equals(candidate.getParent());
    }

    private void copyCurrentScreenshot() {
        if (screenshotFiles.isEmpty()) return;
        Path selected = screenshotFiles.get(screenshotIndex);
        screenshotOperationPending = true;
        runScreenshotOperation("coronapoker-gdx-copy-screenshot", () -> {
            BufferedImage image = ImageIO.read(selected.toFile());
            if (image == null || !GdxImageClipboard.copy(image)) {
                throw new IOException("Clipboard rejected screenshot");
            }
        }, "ui.imagen_copiada", "ui.copiar_imagen_error", null);
    }

    private void confirmDeleteCurrentScreenshot() {
        if (screenshotFiles.isEmpty()) return;
        Path selected = screenshotFiles.get(screenshotIndex);
        int selectedIndex = screenshotIndex;
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM,
                uppercase(gameText.translate("ui.borrar_captura")),
                uppercase(gameText.translate("ui.borrar_captura_confirm")),
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.NONE,
                720, 0, false,
                uppercase(gameText.translate("ui.cancelar")),
                uppercase(gameText.translate("ui.aceptar")));
        confirmation.result().thenAccept(accepted -> {
            if (!accepted || screenshotOperationPending) return;
            screenshotOperationPending = true;
            runScreenshotOperation("coronapoker-gdx-delete-screenshot", () -> {
                if (!isManagedScreenshot(screenshotDirectory(), selected)) {
                    throw new IOException("Screenshot outside managed folder");
                }
                Files.delete(selected);
            }, "", "ui.borrar_captura_error", () -> {
                if (uiLayer == UI_SCREENSHOTS) {
                    refreshScreenshotFiles(selectedIndex);
                }
            });
        });
        showDialog(confirmation);
    }

    private void runScreenshotOperation(String threadName,
            ScreenshotOperation operation, String successKey,
            String failureKey, Runnable afterSuccess) {
        Thread worker = new Thread(() -> {
            boolean success = false;
            try {
                operation.run();
                success = true;
            } catch (Exception failure) {
                System.err.println("GDX screenshot operation failed: "
                        + failure.getMessage());
                failure.printStackTrace(System.err);
            }
            boolean completed = success;
            if (Gdx.app != null) {
                Gdx.app.postRunnable(() -> {
                    screenshotOperationPending = false;
                    if (completed && afterSuccess != null) afterSuccess.run();
                    String key = completed ? successKey : failureKey;
                    if (key != null && !key.isBlank()) {
                        screenshotToast = uppercase(gameText.translate(key));
                        screenshotToastUntil = totalTime + 1.5f;
                    }
                });
            }
        }, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    @FunctionalInterface
    private interface ScreenshotOperation {
        void run() throws Exception;
    }

    private void drawScreenshotViewer() {
        float alpha = uiFade();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0.008f, 0.012f, 0.018f, 0.985f * alpha);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.84f * alpha);
        shapes.rect(24f, height - 88f, width - 48f, 3f);
        if (screenshotIndex > 0) {
            drawDialogButton(24f, height / 2f - 70f, 64f, 140f,
                    CYAN, contains(pointer.x, pointer.y, 24f,
                            height / 2f - 70f, 64f, 140f), alpha);
        }
        if (screenshotIndex + 1 < screenshotFiles.size()) {
            drawDialogButton(width - 88f, height / 2f - 70f,
                    64f, 140f, CYAN, contains(pointer.x, pointer.y,
                            width - 88f, height / 2f - 70f,
                            64f, 140f), alpha);
        }
        shapes.setColor(FOLD_RED.r, FOLD_RED.g, FOLD_RED.b,
                (contains(pointer.x, pointer.y, width - 76f,
                        height - 76f, 48f, 48f) ? 0.92f : 0.52f) * alpha);
        roundedRect(width - 76f, height - 76f, 48f, 48f, 9f);
        if (screenshotTexture != null) {
            Rectangle copy = screenshotCopyBounds(width);
            Rectangle delete = screenshotDeleteBounds(width);
            drawDialogButton(copy.x, copy.y, copy.width, copy.height,
                    CYAN, copy.contains(pointer), alpha);
            drawDialogButton(delete.x, delete.y, delete.width, delete.height,
                    FOLD_RED, delete.contains(pointer), alpha);
        }
        shapes.end();

        batch.begin();
        batch.setColor(1f, 1f, 1f, alpha);
        if (screenshotTexture != null) {
            Rectangle bounds = fitInside(screenshotTexture.getWidth(),
                    screenshotTexture.getHeight(), 106f, 96f,
                    width - 212f, height - 210f, true);
            batch.draw(screenshotTexture, bounds.x, bounds.y,
                    bounds.width, bounds.height);
        }
        String viewerTitle = uppercase(gameText.translate(
                "menu.visor_capturas"));
        String title = screenshotFiles.isEmpty()
                ? viewerTitle
                : viewerTitle + "  ·  " + (screenshotIndex + 1)
                        + " / " + screenshotFiles.size();
        drawFittedCenteredInBox(uiFont, title, 150f, height - 80f,
                width - 300f, 62f, POT_GOLD, alpha);
        String message = !screenshotError.isBlank() ? screenshotError
                : screenshotFiles.isEmpty()
                        ? uppercase(gameText.translate("ui.no_capturas")) : "";
        if (!message.isBlank()) {
            drawFittedCenteredInBox(uiFont, message,
                    width / 2f - 360f, height / 2f - 45f,
                    720f, 90f, new Color(0xe8edf4ff), alpha);
        }
        if (screenshotIndex > 0) {
            drawFittedCenteredInBox(uiFont, "‹", 24f,
                    height / 2f - 70f, 64f, 140f, Color.WHITE, alpha);
        }
        if (screenshotIndex + 1 < screenshotFiles.size()) {
            drawFittedCenteredInBox(uiFont, "›", width - 88f,
                    height / 2f - 70f, 64f, 140f, Color.WHITE, alpha);
        }
        drawFittedCenteredInBox(actionFont, "×", width - 76f,
                height - 76f, 48f, 48f, Color.WHITE, alpha);
        if (screenshotTexture != null) {
            Rectangle copy = screenshotCopyBounds(width);
            Rectangle delete = screenshotDeleteBounds(width);
            float enabledAlpha = screenshotOperationPending
                    ? alpha * 0.45f : alpha;
            drawFittedCenteredInBox(actionFont,
                    uppercase(gameText.translate(
                            "ui.copiar_imagen_portapapeles")),
                    copy.x + 12f, copy.y + 5f, copy.width - 24f,
                    copy.height - 10f, POT_GOLD, enabledAlpha);
            drawFittedCenteredInBox(actionFont,
                    uppercase(gameText.translate("ui.borrar_captura")),
                    delete.x + 12f, delete.y + 5f, delete.width - 24f,
                    delete.height - 10f, Color.WHITE, enabledAlpha);
        }
        batch.end();
    }

    static Rectangle fitInside(int sourceWidth, int sourceHeight,
            float areaX, float areaY, float areaWidth, float areaHeight,
            boolean neverUpscale) {
        if (sourceWidth <= 0 || sourceHeight <= 0
                || areaWidth <= 0f || areaHeight <= 0f) {
            return new Rectangle(areaX, areaY, 0f, 0f);
        }
        float scale = Math.min(areaWidth / sourceWidth,
                areaHeight / sourceHeight);
        if (neverUpscale) scale = Math.min(1f, scale);
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        return new Rectangle(areaX + (areaWidth - width) / 2f,
                areaY + (areaHeight - height) / 2f, width, height);
    }

    private void drawScreenshotToast() {
        if (screenshotToast.isBlank() || totalTime >= screenshotToastUntil) {
            return;
        }
        float remaining = MathUtils.clamp(
                (screenshotToastUntil - totalTime) / 0.22f, 0f, 1f);
        float alpha = Interpolation.fade.apply(remaining);
        float x = 26f;
        float y = viewport.getWorldHeight() - 132f;
        float width = 430f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.62f * alpha);
        roundedRect(x + 6f, y - 7f, width, 64f, 11f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.88f * alpha);
        roundedRect(x - 2f, y - 2f, width + 4f, 64f, 11f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.98f * alpha);
        roundedRect(x, y, width, 60f, 9f);
        shapes.end();
        batch.begin();
        drawFittedCenteredInBox(actionFont, screenshotToast,
                x + 18f, y + 6f, width - 36f, 48f,
                Color.WHITE, alpha);
        batch.end();
    }

    private void drawVolumeOverlay() {
        if (totalTime >= volumeOverlayUntil) return;
        float width = 520f;
        float height = 100f;
        float x = (viewport.getWorldWidth() - width) / 2f;
        float y = (viewport.getWorldHeight() - height) / 2f;
        float volume = effectsVolume;
        Color accent = volume > 0f ? CYAN : FOLD_RED;
        float barX = x + 104f;
        float barY = y + 37f;
        float barW = width - 132f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(x + 8f, y - 9f, width, height, 15f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.92f);
        roundedRect(x - 2f, y - 2f, width + 4f, height + 4f, 15f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.98f);
        roundedRect(x, y, width, height, 13f);
        shapes.setColor(0.15f, 0.20f, 0.28f, 1f);
        roundedRect(barX, barY, barW, 26f, 7f);
        if (volume > 0f) {
            shapes.setColor(accent);
            roundedRect(barX, barY, barW * volume, 26f, 7f);
        }
        shapes.end();
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(volume > 0f ? soundIcon : muteIcon,
                x + 22f, y + 21f, 58f, 58f);
        drawFittedCenteredInBox(uiFont, Math.round(volume * 100f) + "%",
                barX, barY, barW, 26f, Color.WHITE, 1f);
        batch.end();
    }

    private void drawActiveDialog() {
        GdxTableDialog dialog = activeDialog;
        if (dialog == null) return;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = dialogWidth(dialog);
        float panelH = dialogHeight(dialog);
        float panelX = dialogX(dialog, panelW);
        float panelY = dialogY(dialog, panelH);
        if (dialog.isGameOver()) {
            drawGameOverDialog(dialog, panelX, panelY, panelW, panelH,
                    width, height);
            return;
        }
        if (dialog.isRecovery()) {
            drawRecoveryDialog(dialog, width, height);
            return;
        }
        float acceptX = panelX + panelW - 272f;
        float negativeX = panelX + (dialog.isAutoAction() ? 30f : 42f);
        float negativeW = dialog.isAutoAction() ? panelW - 60f : 230f;
        float buttonY = panelY + (dialog.isAutoAction() ? 22f : 34f);
        float buttonH = dialog.isAutoAction() ? 52f : 64f;
        Color accent = switch (dialog.kind()) {
            case ERROR -> FOLD_RED;
            case INFO -> CYAN;
            case CONFIRM, TIMED_WARNING, AUTO_ACTION, REBUY, AUTO_CALL,
                    HAND_LIMIT, GAME_OVER -> POT_GOLD;
        };

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        if (!dialog.isAutoAction()) {
            shapes.setColor(0f, 0f, 0f, 0.68f);
            shapes.rect(0f, 0f, width, height);
        }
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(panelX + 10f, panelY - 11f, panelW, panelH, 20f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.90f);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f, panelH + 4f, 20f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.97f);
        roundedRect(panelX, panelY, panelW, panelH, 18f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.72f);
        shapes.rect(panelX + 24f, panelY + panelH - 9f, panelW - 48f, 3f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.045f);
        float contentY = panelY + (dialog.isAutoAction() ? 88f : 125f);
        float contentH = dialog.isAutoAction() ? 70f : panelH - 222f;
        roundedRect(panelX + 28f, contentY,
                panelW - 56f, contentH, 12f);
        if (dialog.showsNegative() && !dialog.waitingForExternalClose()) {
            drawDialogButton(negativeX, buttonY, negativeW, buttonH,
                    BUTTON_LINE, contains(pointer.x, pointer.y,
                            negativeX, buttonY, negativeW, buttonH), 1f);
        }
        if (dialog.showsPositive() && !dialog.waitingForExternalClose()) {
            drawDialogButton(acceptX, panelY + 34f, 230f, 64f, accent,
                    contains(pointer.x, pointer.y, acceptX,
                            panelY + 34f, 230f, 64f), 1f);
        }
        if (dialog.hasAmount()) {
            float minusX = panelX + panelW / 2f - 190f;
            float plusX = panelX + panelW / 2f + 118f;
            float amountAlpha = dialog.waitingForExternalClose() ? 0.34f
                    : dialog.isHandLimit() && dialog.noLimit()
                    || dialog.isAutoCall()
                    && (!dialog.optionEnabled() || dialog.noLimit())
                    ? 0.34f : 1f;
            drawDialogButton(minusX, panelY + 155f, 72f, 64f,
                    CYAN, contains(pointer.x, pointer.y, minusX,
                            panelY + 155f, 72f, 64f),
                    amountAlpha);
            drawDialogButton(plusX, panelY + 155f, 72f, 64f,
                    CYAN, contains(pointer.x, pointer.y, plusX,
                            panelY + 155f, 72f, 64f),
                    amountAlpha);
        }
        if (dialog.isAutoCall()) {
            drawSettingsToggleShape(panelX + 56f, panelY + 318f,
                    panelW - 112f, dialog.optionEnabled(), 1f);
            drawSettingsToggleShape(panelX + 56f, panelY + 244f,
                    panelW - 112f, dialog.noLimit(),
                    dialog.optionEnabled() ? 1f : 0.36f);
        } else if (dialog.isHandLimit()) {
            drawSettingsToggleShape(panelX + 56f, panelY + 244f,
                    panelW - 112f, dialog.noLimit(), 1f);
        }
        if (dialog.waitingForExternalClose()) {
            float progressW = panelW - 84f;
            float progressY = panelY + 116f;
            shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g,
                    BUTTON_LINE.b, 0.80f);
            roundedRect(panelX + 42f, progressY,
                    progressW, 8f, 4f);
            float segmentW = Math.max(96f, progressW * 0.24f);
            float travel = progressW - segmentW;
            float phase = (totalTime * 0.65f) % 2f;
            float normalized = phase <= 1f ? phase : 2f - phase;
            shapes.setColor(accent.r, accent.g, accent.b, 0.94f);
            roundedRect(panelX + 42f + travel * normalized, progressY,
                    segmentW, 8f, 4f);
        } else if (dialog.seconds() > 0) {
            float progressW = panelW - 84f;
            float progressH = dialog.isAutoAction() ? 14f : 8f;
            float progressY = panelY + (dialog.isAutoAction() ? 78f : 116f);
            shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g,
                    BUTTON_LINE.b, 0.80f);
            roundedRect(panelX + 42f, progressY,
                    progressW, progressH, progressH / 2f);
            float remaining = progressW * dialog.remainingFraction(totalTime);
            if (remaining > 0f) {
                shapes.setColor(accent.r, accent.g, accent.b, 0.94f);
                roundedRect(panelX + 42f, progressY,
                        remaining, progressH, progressH / 2f);
            }
        }
        shapes.end();

        batch.begin();
        drawLeftInBox(uiFont, dialog.title(), panelX + 42f,
                panelY + panelH - (dialog.isAutoAction() ? 58f : 80f),
                panelW - 84f, 46f,
                accent, 1f);
        BitmapFont.BitmapFontData dialogFontData = uiFont.getData();
        float originalScaleX = dialogFontData.scaleX;
        float originalScaleY = dialogFontData.scaleY;
        float messageW = panelW - 112f;
        float messageH = dialog.isAutoAction() ? 54f
                : dialog.hasAmount() ? 86f : panelH - 240f;
        glyph.setText(uiFont, dialog.message(), Color.WHITE,
                messageW, Align.center, true);
        if (glyph.height > messageH) {
            float fit = messageH / glyph.height;
            dialogFontData.setScale(originalScaleX * fit, originalScaleY * fit);
            glyph.setText(uiFont, dialog.message(), Color.WHITE,
                    messageW, Align.center, true);
        }
        uiFont.setColor(Color.WHITE);
        uiFont.draw(batch, glyph, panelX + 56f,
                dialog.isAutoCall() || dialog.isHandLimit() ? panelY + 414f
                        : dialog.hasAmount() ? panelY + 286f
                        : dialog.isAutoAction()
                                ? panelY + 96f + glyph.height / 2f
                                : panelY + 125f
                                        + (panelH - 222f + glyph.height) / 2f);
        uiFont.setColor(Color.WHITE);
        dialogFontData.setScale(originalScaleX, originalScaleY);
        if (dialog.hasAmount()) {
            drawFittedCenteredInBox(seatActionFont, "-",
                    panelX + panelW / 2f - 190f, panelY + 155f,
                    72f, 64f, Color.WHITE, 1f);
            String amountText = dialog.isHandLimit() && dialog.noLimit()
                    ? uppercase(gameText.translate("auto_call.sin_limite"))
                    : dialog.isAutoCall() ? dialog.amountText()
                    : Integer.toString(dialog.amount());
            if (dialog.isAutoCall() && dialog.autoCallAmountEditable()
                    && dialogAmountEdit.focused("autoCallAmount")
                    && ((int) (totalTime * 2f) & 1) == 0) {
                amountText += "|";
            }
            drawFittedCenteredInBox(uiFont, amountText,
                    panelX + panelW / 2f - 110f, panelY + 155f,
                    220f, 64f, POT_GOLD,
                    dialog.waitingForExternalClose() ? 0.52f : 1f);
            drawFittedCenteredInBox(seatActionFont, "+",
                    panelX + panelW / 2f + 118f, panelY + 155f,
                    72f, 64f, Color.WHITE, 1f);
            if (!dialog.isRebuy()) {
                drawFittedCenteredInBox(smallFont,
                        dialog.isHandLimit()
                                ? uppercase(gameText.translate(
                                        "gdx.dialog.minimum")) + " "
                                        + dialog.minimumAmount()
                                : uppercase(gameText.translate(
                                        "gdx.dialog.minimum")) + " "
                                        + formatAmount(dialog.minimumAmount() / 100d),
                        panelX + panelW / 2f - 190f, panelY + 135f,
                        380f, 24f, Color.LIGHT_GRAY, 1f);
            }
        }
        if (dialog.isAutoCall()) {
            drawSettingsRowText(panelX + 56f, panelY + 318f,
                    panelW - 112f, dialog.optionEnabled()
                            ? uppercase(gameText.translate("auto_call.activado"))
                            : uppercase(gameText.translate(
                                    "auto_call.desactivado")), 1f);
            drawSettingsRowText(panelX + 56f, panelY + 244f,
                    panelW - 112f, uppercase(gameText.translate(
                            "auto_call.sin_limite")),
                    dialog.optionEnabled() ? 1f : 0.36f);
        } else if (dialog.isHandLimit()) {
            drawSettingsRowText(panelX + 56f, panelY + 244f,
                    panelW - 112f, uppercase(gameText.translate(
                            "auto_call.sin_limite")), 1f);
        }
        if (dialog.showsNegative() && !dialog.waitingForExternalClose()) {
            drawFittedCenteredInBox(actionFont, dialog.negativeLabel(),
                    negativeX, buttonY, negativeW, buttonH,
                    Color.WHITE, 1f);
        }
        if (dialog.showsPositive() && !dialog.waitingForExternalClose()) {
            drawFittedCenteredInBox(actionFont,
                    dialog.positiveLabel(),
                    acceptX, panelY + 34f, 230f, 64f,
                    Color.WHITE, 1f);
        }
        batch.end();
    }

    private void drawGameOverDialog(GdxTableDialog dialog, float panelX,
            float panelY, float panelW, float panelH, float worldW,
            float worldH) {
        boolean interactive = dialog.showsPositive();
        float contentX = panelX + 28f;
        float contentY = panelY + (interactive ? 126f : 28f);
        float contentW = panelW - 56f;
        float contentH = panelH - (interactive ? 154f : 56f);
        float sourceAspect = 782f / 326f;
        float imageW = Math.min(contentW, contentH * sourceAspect);
        float imageH = imageW / sourceAspect;
        float imageX = panelX + (panelW - imageW) / 2f;
        float imageY = contentY + (contentH - imageH) / 2f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0f, 0f, worldW, worldH);
        shapes.setColor(0f, 0f, 0f, 0.60f);
        roundedRect(panelX + 10f, panelY - 11f, panelW, panelH, 20f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.94f);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f,
                panelH + 4f, 20f);
        shapes.setColor(0.004f, 0.010f, 0.018f, 0.985f);
        roundedRect(panelX, panelY, panelW, panelH, 18f);
        shapes.setColor(0f, 0f, 0f, 1f);
        roundedRect(contentX, contentY, contentW, contentH, 12f);
        if (interactive) {
            boolean negativeHover = contains(pointer.x, pointer.y,
                    panelX + 42f, panelY + 34f, 230f, 64f);
            boolean positiveHover = contains(pointer.x, pointer.y,
                    panelX + panelW - 272f, panelY + 34f, 230f, 64f);
            drawDialogButton(panelX + 42f, panelY + 34f, 230f, 64f,
                    BUTTON_LINE, negativeHover, 1f);
            drawDialogButton(panelX + panelW - 272f, panelY + 34f,
                    230f, 64f, POT_GOLD, positiveHover, 1f);
        }
        shapes.end();

        StreamingGifTextureAnimation animation = gameOverAnimation(dialog,
                !interactive);
        Texture gameOverFrame = animation == null ? null
                : animation.frameAt(dialog.elapsedSeconds(totalTime));
        if (animation != null && animation.failed()
                && !gameOverAnimationFailureReported) {
            gameOverAnimationFailureReported = true;
            Gdx.app.error("CoronaPoker GDX",
                    "GAME OVER GIF decode failed; using live fallback",
                    animation.failure());
        }
        batch.begin();
        if (gameOverFrame != null) {
            batch.setColor(Color.WHITE);
            batch.draw(gameOverFrame, imageX, imageY, imageW, imageH);
        } else {
            drawFittedCenteredInBox(finalHeroFont, "GAME OVER",
                    contentX + 24f,
                    imageY + (interactive ? imageH * 0.36f : 0f),
                    contentW - 48f,
                    interactive ? imageH * 0.48f : imageH,
                    new Color(0xdc1e1eff), 1f);
            if (interactive) {
                drawFittedCenteredInBox(finalAmountFont,
                        Integer.toString(dialog.remainingSeconds(totalTime)),
                        contentX + contentW * 0.32f, imageY + 8f,
                        contentW * 0.36f, imageH * 0.42f,
                        Color.WHITE, 1f);
            }
        }
        if (interactive) {
            drawFittedCenteredInBox(actionFont, dialog.negativeLabel(),
                    panelX + 42f, panelY + 34f, 230f, 64f,
                    Color.WHITE, 1f);
            drawFittedCenteredInBox(actionFont, dialog.positiveLabel(),
                    panelX + panelW - 272f, panelY + 34f, 230f, 64f,
                    Color.WHITE, 1f);
        }
        batch.end();
    }

    private StreamingGifTextureAnimation gameOverAnimation(
            GdxTableDialog dialog, boolean zero) {
        if (!gameOverCinematicsEnabled()) return null;
        if (gameOverAnimationDialog != dialog) {
            if (gameOverAnimation != null) gameOverAnimation.dispose();
            gameOverAnimation = null;
            gameOverAnimationDialog = dialog;
            gameOverAnimationFailureReported = false;
            try {
                gameOverAnimation = StreamingGifTextureAnimation.load(
                        zero ? "cinematics/misc/game_over_zero.gif"
                                : "cinematics/misc/game_over.gif",
                        782);
            } catch (IOException | RuntimeException missing) {
                // The static, live countdown fallback remains visible. A
                // missing/broken cosmetic asset must never strand the dealer.
                gameOverAnimationFailureReported = true;
                Gdx.app.error("CoronaPoker GDX",
                        "GAME OVER GIF unavailable; using live fallback",
                        missing);
            }
        }
        return gameOverAnimation;
    }

    private void releaseGameOverAnimation() {
        if (gameOverAnimation != null) gameOverAnimation.dispose();
        gameOverAnimation = null;
        gameOverAnimationDialog = null;
        gameOverAnimationFailureReported = false;
    }

    private float uiFade() {
        return Interpolation.fade.apply(MathUtils.clamp(
                (totalTime - uiOpenedAt) / 0.16f, 0f, 1f));
    }

    private void drawSettingsDialog() {
        float alpha = uiFade();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        List<GdxSettingsContract.Section> sections =
                settingsSession.sections();
        List<String> subpages = settingsSubpageLabels();
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                width, height, sections.size(), subpages.size());
        Rectangle panel = frame.panel();
        Rectangle content = frame.content();
        float panelW = panel.width;
        float panelH = panel.height;
        float panelX = panel.x;
        float panelY = panel.y;
        int activeSubpage = settingsSubpageIndex();
        float contentX = content.x;
        float contentW = content.width;
        float firstRowY = frame.firstRowY();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.28f * alpha);
        shapes.rect(0f, 0f, width, height);
        GdxSettingsChrome.draw(shapes, frame, sections.size(),
                subpages.size(), settingsSession.tabIndex(), activeSubpage,
                pointer, settingsSectionHasRestoreDefaults(), alpha);

        drawSettingsContentShapes(contentX, firstRowY, contentW, alpha);
        Rectangle cancel = frame.cancelButton();
        Rectangle save = frame.saveButton();
        shapes.end();

        batch.begin();
        drawLeftInBox(uiFont,
                gameText.translate("settings.ajustes").toUpperCase(
                        Locale.forLanguageTag(gameText.language())),
                panelX + 34f,
                panelY + panelH - 70f, panelW - 68f, 42f,
                Color.WHITE, alpha);
        drawLeftInBox(smallFont,
                gameText.translate("gdx.settings.subtitle").toUpperCase(
                        Locale.forLanguageTag(gameText.language())),
                panelX + 35f, panelY + panelH - 112f,
                panelW - 70f, 24f, CYAN, alpha);
        for (int i = 0; i < sections.size(); i++) {
            Rectangle tab = frame.mainTab(i);
            drawFittedCenteredInBox(actionFont,
                    sections.get(i).label(gameText),
                    tab.x, tab.y, tab.width, tab.height,
                    Color.WHITE,
                    (i == settingsSession.tabIndex() ? 1f : 0.72f) * alpha);
        }
        for (int i = 0; i < subpages.size(); i++) {
            Rectangle tab = frame.subTab(i);
            drawFittedCenteredInBox(smallFont, subpages.get(i),
                    tab.x, tab.y, tab.width, tab.height,
                    i == activeSubpage ? POT_GOLD : Color.WHITE,
                    (i == activeSubpage ? 1f : 0.70f) * alpha);
        }
        drawSettingsContentText(contentX, firstRowY, contentW, alpha);
        drawFittedCenteredInBox(actionFont,
                gameText.translate("ui.cancelar"),
                cancel.x, cancel.y, cancel.width, cancel.height,
                Color.WHITE, alpha);
        if (settingsSectionHasRestoreDefaults()) {
            Rectangle restore = frame.restoreButton();
            drawFittedCenteredInBox(smallFont,
                    gameText.translate("gdx.settings.restore_defaults")
                            .toUpperCase(Locale.forLanguageTag(
                                    gameText.language())),
                    restore.x, restore.y, restore.width, restore.height,
                    Color.WHITE, alpha);
        }
        drawFittedCenteredInBox(actionFont,
                gameText.translate("ui.guardar"),
                save.x, save.y, save.width, save.height,
                Color.WHITE, alpha);
        batch.end();
        if (settingsContentPage() == 9) {
            drawSettingsDebugTextLayer(contentX, firstRowY, contentW, alpha);
        }
        if (voiceNotesOpen) drawTableVoiceNotesDialog();
    }

    /**
     * Exact GDX counterpart of Swing's undecorated RecoverDialog: while the
     * core replays the interrupted hand, the table lights are lowered and the
     * localized recovery GIF is the only foreground element.  The core owns
     * the matching recovering.mp3/background-music swap.
     */
    private void drawRecoveryDialog(GdxTableDialog dialog, float worldW,
            float worldH) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0f, 0f, worldW, worldH);
        shapes.end();

        StreamingGifTextureAnimation animation = recoveryAnimation(dialog);
        Texture frame = animation == null ? null
                : animation.frameAt(dialog.elapsedSeconds(totalTime));
        if (animation != null && animation.failed()
                && !recoveryAnimationFailureReported) {
            recoveryAnimationFailureReported = true;
            Gdx.app.error("CoronaPoker GDX",
                    "Recovery GIF decode failed", animation.failure());
        }
        if (frame == null || animation.width() <= 0
                || animation.height() <= 0) return;

        float imageW = animation.width();
        float imageH = animation.height();
        float fit = Math.min(1f, Math.min(
                (worldW - 48f) / imageW, (worldH - 48f) / imageH));
        imageW *= fit;
        imageH *= fit;
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(frame, (worldW - imageW) / 2f,
                (worldH - imageH) / 2f, imageW, imageH);
        batch.end();
    }

    private StreamingGifTextureAnimation recoveryAnimation(
            GdxTableDialog dialog) {
        if (recoveryAnimationDialog != dialog) {
            releaseRecoveryAnimation();
            recoveryAnimationDialog = dialog;
            String language = gameText.language().toLowerCase(Locale.ROOT);
            String localized = "cinematics/misc/recover_" + language + ".gif";
            String path = Gdx.files.internal(localized).exists()
                    ? localized : "cinematics/misc/recover.gif";
            try {
                recoveryAnimation = StreamingGifTextureAnimation.loadLooping(
                        path, 1600);
            } catch (IOException | RuntimeException missing) {
                recoveryAnimationFailureReported = true;
                Gdx.app.error("CoronaPoker GDX",
                        "Recovery GIF unavailable", missing);
            }
        }
        return recoveryAnimation;
    }

    private void releaseRecoveryAnimation() {
        if (recoveryAnimation != null) recoveryAnimation.dispose();
        recoveryAnimation = null;
        recoveryAnimationDialog = null;
        recoveryAnimationFailureReported = false;
    }

    private void openTableVoiceNotes(boolean purgeConfirmation) {
        voiceNotesOpen = true;
        voiceNotesPurgeConfirmation = purgeConfirmation;
        voiceNoteDeleteConfirmation = null;
        voiceNotesPage = 0;
        reloadTableVoiceNotes();
    }

    private void reloadTableVoiceNotes() {
        voiceNotesLoading = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return voiceNoteLibrary.list();
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((entries, failure) -> Gdx.app.postRunnable(() -> {
            if (!voiceNotesOpen) return;
            voiceNotesLoading = false;
            if (failure == null) {
                voiceNotes = entries;
            } else {
                voiceNotes = List.of();
                voiceStatus = gameText.translate("audio.borrar_nota_error");
                voiceStatusAt = totalTime;
            }
        }));
    }

    private void closeTableVoiceNotes() {
        GdxVoicePlayback.stop();
        voiceNotePlaying = null;
        voiceNoteDeleteConfirmation = null;
        voiceNotesPurgeConfirmation = false;
        voiceNotesOpen = false;
    }

    private Rectangle tableVoiceNotesPanel() {
        float width = Math.min(1300f, viewport.getWorldWidth() - 80f);
        float height = Math.min(820f, viewport.getWorldHeight() - 70f);
        return new Rectangle((viewport.getWorldWidth() - width) / 2f,
                (viewport.getWorldHeight() - height) / 2f, width, height);
    }

    private void handleTableVoiceNotesClick(float px, float py) {
        Rectangle panel = tableVoiceNotesPanel();
        if (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation) {
            if (contains(px, py, panel.x + 250f, panel.y + 300f,
                    360f, 74f)) {
                voiceNoteDeleteConfirmation = null;
                voiceNotesPurgeConfirmation = false;
            } else if (contains(px, py,
                    panel.x + panel.width - 610f, panel.y + 300f,
                    360f, 74f) && !voiceNotesLoading) {
                confirmTableVoiceNoteDeletion();
            }
            return;
        }
        if (contains(px, py, panel.x + panel.width - 260f,
                panel.y + 36f, 210f, 68f)) {
            closeTableVoiceNotes();
            return;
        }
        int pageCount = Math.max(1, (voiceNotes.size() + 4) / 5);
        if (contains(px, py, panel.x + 48f, panel.y + 42f, 80f, 58f)) {
            voiceNotesPage = Math.max(0, voiceNotesPage - 1);
            return;
        }
        if (contains(px, py, panel.x + 232f, panel.y + 42f, 80f, 58f)) {
            voiceNotesPage = Math.min(pageCount - 1, voiceNotesPage + 1);
            return;
        }
        int start = MathUtils.clamp(voiceNotesPage, 0, pageCount - 1) * 5;
        int end = Math.min(voiceNotes.size(), start + 5);
        float rowY = panel.y + panel.height - 178f;
        for (int index = start; index < end; index++) {
            GdxVoiceNoteLibrary.Entry entry = voiceNotes.get(index);
            if (contains(px, py, panel.x + panel.width - 420f,
                    rowY - 42f, 160f, 64f)) {
                toggleTableVoiceNotePreview(entry);
                return;
            }
            if (contains(px, py, panel.x + panel.width - 240f,
                    rowY - 42f, 160f, 64f)) {
                voiceNoteDeleteConfirmation = entry;
                return;
            }
            rowY -= 102f;
        }
    }

    private void drawTableVoiceNotesDialog() {
        Rectangle panel = tableVoiceNotesPanel();
        float x = panel.x;
        float y = panel.y;
        float w = panel.width;
        float h = panel.height;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0.008f, 0.016f, 0.030f, 0.94f);
        shapes.rect(0f, 0f, viewport.getWorldWidth(),
                viewport.getWorldHeight());
        shapes.setColor(BUTTON_LINE);
        roundedRect(x - 2f, y - 2f, w + 4f, h + 4f, 20f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 1f);
        roundedRect(x, y, w, h, 18f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.72f);
        shapes.rect(x + 28f, y + h - 18f, w - 56f, 3f);

        if (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation) {
            drawDialogButton(x + 250f, y + 300f, 360f, 74f,
                    BUTTON_LINE, contains(pointer.x, pointer.y,
                            x + 250f, y + 300f, 360f, 74f), 1f);
            drawDialogButton(x + w - 610f, y + 300f, 360f, 74f,
                    FOLD_RED, contains(pointer.x, pointer.y,
                            x + w - 610f, y + 300f, 360f, 74f), 1f);
        } else {
            int pageCount = Math.max(1, (voiceNotes.size() + 4) / 5);
            voiceNotesPage = MathUtils.clamp(voiceNotesPage, 0,
                    pageCount - 1);
            int start = voiceNotesPage * 5;
            int end = Math.min(voiceNotes.size(), start + 5);
            float rowY = y + h - 178f;
            for (int index = start; index < end; index++) {
                GdxVoiceNoteLibrary.Entry entry = voiceNotes.get(index);
                shapes.setColor(BUTTON_LINE);
                roundedRect(x + 46f, rowY - 52f, w - 92f, 86f, 10f);
                shapes.setColor(0.025f, 0.060f, 0.105f, 0.96f);
                roundedRect(x + 49f, rowY - 49f, w - 98f, 80f, 8f);
                drawDialogButton(x + w - 420f, rowY - 42f, 160f, 64f,
                        BUTTON_LINE, contains(pointer.x, pointer.y,
                                x + w - 420f, rowY - 42f, 160f, 64f), 1f);
                drawDialogButton(x + w - 240f, rowY - 42f, 160f, 64f,
                        FOLD_RED, contains(pointer.x, pointer.y,
                                x + w - 240f, rowY - 42f, 160f, 64f), 1f);
                rowY -= 102f;
            }
            if (pageCount > 1) {
                drawDialogButton(x + 48f, y + 42f, 80f, 58f, BUTTON_LINE,
                        contains(pointer.x, pointer.y,
                                x + 48f, y + 42f, 80f, 58f), 1f);
                drawDialogButton(x + 232f, y + 42f, 80f, 58f, BUTTON_LINE,
                        contains(pointer.x, pointer.y,
                                x + 232f, y + 42f, 80f, 58f), 1f);
            }
            drawDialogButton(x + w - 260f, y + 36f, 210f, 68f,
                    POT_GOLD, contains(pointer.x, pointer.y,
                            x + w - 260f, y + 36f, 210f, 68f), 1f);
        }
        shapes.end();

        batch.begin();
        drawFittedCenteredInBox(uiFont,
                uppercase(gameText.translate("audio.ver_notas")),
                x + 45f, y + h - 100f, w - 90f, 54f, POT_GOLD, 1f);
        if (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation) {
            String message = voiceNotesPurgeConfirmation
                    ? gameText.translate("audio.purgar_notas_confirm")
                    : gameText.translate("audio.borrar_nota_confirm",
                            voiceNoteDeleteConfirmation.nickname());
            drawFittedCenteredInBox(uiFont, message, x + 80f, y + 450f,
                    w - 160f, 70f, Color.WHITE, 1f);
            drawFittedCenteredInBox(actionFont,
                    uppercase(gameText.translate("ui.cancelar")),
                    x + 250f, y + 300f, 360f, 74f, Color.WHITE, 1f);
            drawFittedCenteredInBox(actionFont,
                    uppercase(gameText.translate("audio.borrar_nota")),
                    x + w - 610f, y + 300f, 360f, 74f,
                    Color.WHITE, 1f);
        } else if (voiceNotesLoading) {
            drawFittedCenteredInBox(uiFont,
                    uppercase(gameText.translate("audio.ver_notas")) + "…",
                    x + 80f, y + 420f, w - 160f, 70f,
                    Color.LIGHT_GRAY, 1f);
        } else if (voiceNotes.isEmpty()) {
            drawFittedCenteredInBox(uiFont,
                    gameText.translate("audio.no_notas_voz"),
                    x + 80f, y + 420f, w - 160f, 70f,
                    Color.LIGHT_GRAY, 1f);
        } else {
            int pageCount = Math.max(1, (voiceNotes.size() + 4) / 5);
            int start = voiceNotesPage * 5;
            int end = Math.min(voiceNotes.size(), start + 5);
            float rowY = y + h - 178f;
            for (int index = start; index < end; index++) {
                GdxVoiceNoteLibrary.Entry entry = voiceNotes.get(index);
                String date = java.time.format.DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy HH:mm").withZone(
                        java.time.ZoneId.systemDefault()).format(
                        java.time.Instant.ofEpochMilli(
                                entry.timestampMillis()));
                drawLeftInBox(uiFont, entry.nickname(), x + 70f,
                        rowY - 18f, 390f, 48f, Color.WHITE, 1f);
                drawLeftInBox(smallFont, date + "  ·  "
                        + tableVoiceDuration(entry.durationMillis()),
                        x + 500f, rowY - 18f, 320f, 48f,
                        Color.LIGHT_GRAY, 1f);
                drawFittedCenteredInBox(actionFont,
                        uppercase(gameText.translate(entry.equals(
                                voiceNotePlaying) ? "audio.preview_parar"
                                : "audio.preview_escuchar")),
                        x + w - 420f, rowY - 42f, 160f, 64f,
                        Color.WHITE, 1f);
                drawFittedCenteredInBox(actionFont,
                        uppercase(gameText.translate("audio.borrar_nota")),
                        x + w - 240f, rowY - 42f, 160f, 64f,
                        Color.WHITE, 1f);
                rowY -= 102f;
            }
            if (pageCount > 1) {
                drawFittedCenteredInBox(actionFont, "‹", x + 48f,
                        y + 42f, 80f, 58f, Color.WHITE, 1f);
                drawFittedCenteredInBox(smallFont,
                        (voiceNotesPage + 1) + " / " + pageCount,
                        x + 138f, y + 42f, 84f, 58f,
                        Color.LIGHT_GRAY, 1f);
                drawFittedCenteredInBox(actionFont, "›", x + 232f,
                        y + 42f, 80f, 58f, Color.WHITE, 1f);
            }
        }
        if (voiceNoteDeleteConfirmation == null
                && !voiceNotesPurgeConfirmation) {
            drawFittedCenteredInBox(actionFont,
                    uppercase(gameText.translate("ui.cerrar")),
                    x + w - 260f, y + 36f, 210f, 68f,
                    Color.WHITE, 1f);
        }
        batch.end();
    }

    private static String tableVoiceDuration(long millis) {
        long seconds = Math.max(0L, Math.round(millis / 1000d));
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60,
                seconds % 60);
    }

    private void toggleTableVoiceNotePreview(
            GdxVoiceNoteLibrary.Entry entry) {
        if (entry.equals(voiceNotePlaying)) {
            GdxVoicePlayback.stop();
            voiceNotePlaying = null;
            return;
        }
        GdxVoicePlayback.stop();
        voiceNotePlaying = entry;
        CompletableFuture.supplyAsync(() -> {
            try {
                return voiceNoteLibrary.read(entry);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).thenCompose(wav -> GdxVoicePlayback.play(wav, effectsVolume,
                null)).whenComplete((ignored, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (entry.equals(voiceNotePlaying)) {
                        voiceNotePlaying = null;
                    }
                    if (failure != null && voiceNotesOpen) {
                        voiceStatus = gameText.translate(
                                "gdx.lobby.voice_playback_failed");
                        voiceStatusAt = totalTime;
                    }
                }));
    }

    private void confirmTableVoiceNoteDeletion() {
        GdxVoiceNoteLibrary.Entry entry = voiceNoteDeleteConfirmation;
        boolean purge = voiceNotesPurgeConfirmation;
        voiceNoteDeleteConfirmation = null;
        voiceNotesPurgeConfirmation = false;
        voiceNotesLoading = true;
        GdxVoicePlayback.stop();
        voiceNotePlaying = null;
        CompletableFuture.supplyAsync(() -> {
            try {
                return purge ? voiceNoteLibrary.purge()
                        : voiceNoteLibrary.delete(entry) ? 1 : 0;
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((deleted, failure) -> Gdx.app.postRunnable(() -> {
            if (!voiceNotesOpen) return;
            if (failure != null) {
                voiceNotesLoading = false;
                voiceStatus = gameText.translate("audio.borrar_nota_error");
                voiceStatusAt = totalTime;
            } else {
                if (purge) {
                    voiceStatus = gameText.translate(
                            "audio.purgar_notas_resultado", deleted);
                    voiceStatusAt = totalTime;
                }
                reloadTableVoiceNotes();
            }
        }));
    }

    private void drawSettingsContentShapes(float x, float firstY,
            float width, float alpha) {
        int contentPage = settingsContentPage();
        if (contentPage == 0) {
            GdxSettingsContract.TogglePage page = settingsAudioPage();
            if (settingsAudioPage == 0) {
                drawSettingsVolumeShape(x, firstY, width, alpha);
            }
            float audioFirstY = firstY
                    - (settingsAudioPage == 0 ? 70f : 0f);
            for (int row = 0; row < page.options().size(); row++) {
                GdxSettingsContract.ToggleOption option =
                        page.options().get(row);
                boolean enabled = settingsOptionEnabled(option);
                boolean value = settingsOptionDisplayedValue(option);
                drawSettingsToggleShape(x, audioFirstY - row * 70f, width,
                        value, enabled ? alpha : alpha * 0.36f);
            }
            if (GdxSettingsContract.hasVoiceRetention(page)) {
                drawSettingsStepperShape(x,
                        audioFirstY - page.options().size() * 70f,
                        width, alpha);
                float actionY = audioFirstY
                        - page.options().size() * 70f - 70f;
                float half = (width - 12f) / 2f;
                drawDialogButton(x, actionY, half, 58f, BUTTON_LINE,
                        contains(pointer.x, pointer.y, x, actionY,
                                half, 58f), alpha);
                drawDialogButton(x + half + 12f, actionY, half, 58f,
                        new Color(0xa83a42ff), contains(pointer.x, pointer.y,
                                x + half + 12f, actionY, half, 58f), alpha);
            }
            if (GdxSettingsContract.hasAudioDevices(page)) {
                drawSettingsStepperShape(x, audioFirstY, width, alpha);
                drawSettingsStepperShape(x, audioFirstY - 70f, width, alpha);
            }
        } else if (contentPage == 1) {
            if (settingsAppearancePage == 0) {
                drawSettingsStepperShape(x, firstY, width, alpha);
                drawSettingsStepperShape(x, firstY - 70f, width,
                        presentationSettings == null
                                ? alpha * 0.36f : alpha);
                drawSettingsStepperShape(x, firstY - 140f, width,
                        presentationSettings == null
                                ? alpha * 0.36f : alpha);
                drawSettingsStepperShape(x, firstY - 210f, width, alpha);
                drawSettingsStepperShape(x, firstY - 280f, width, alpha);
                drawSettingsStepperShape(x, firstY - 350f, width,
                        presentationSettings == null
                                ? alpha * 0.36f : alpha);
            } else {
                GdxSettingsContract.TogglePage page =
                        settingsAppearanceTogglePage();
                if (GdxSettingsContract.hasAppearanceAnimationOptions(page)) {
                    for (int row = 0;
                            row < GdxAppearanceOptions.ANIMATION_CHOICES.size();
                            row++) {
                        GdxAppearanceOptions.Choice option =
                                GdxAppearanceOptions.ANIMATION_CHOICES.get(row);
                        drawSettingsStepperShape(x, firstY - row * 70f,
                                width, GdxAppearanceOptions.enabled(option,
                                        tableSettingsProperties())
                                                ? alpha : alpha * 0.36f);
                    }
                    return;
                }
                for (int row = 0; row < page.options().size(); row++) {
                    GdxSettingsContract.ToggleOption option =
                            page.options().get(row);
                    boolean enabled = GdxSettingsContract.enabled(option,
                            tableSettingsProperties(), audioControl.enabled());
                    drawSettingsToggleShape(x, firstY - row * 70f, width,
                            tablePreference(option.key(), option.fallback()),
                            enabled ? alpha : alpha * 0.36f);
                }
            }
        } else if (contentPage == 2) {
            drawSettingsToggleShape(x, firstY, width,
                    autoButtons, alpha);
            drawSettingsToggleShape(x, firstY - 70f, width,
                    autoCallEnabled, autoButtons ? alpha : alpha * 0.36f);
            drawSettingsToggleShape(x, firstY - 140f, width,
                    autoActionPersist, autoButtons ? alpha : alpha * 0.36f);
            drawSettingsToggleShape(x, firstY - 210f, width,
                    autoModeConfirm, autoButtons ? alpha : alpha * 0.36f);
            drawSettingsToggleShape(x, firstY - 280f, width,
                    confirmActions, alpha);
            drawSettingsToggleShape(x, firstY - 350f, width,
                    autoRebuy, tableRebuyAllowed ? alpha : alpha * 0.36f);
        } else if (contentPage == 3) {
            float enabledAlpha = GdxLiveSettingsPolicy.canEditGameRules(
                    tableHost, liveSettingsDraft)
                    ? alpha : alpha * 0.36f;
            float runItTwiceAlpha = GdxLiveSettingsPolicy.canEditRunItTwice(
                    tableHost, liveSettingsDraft,
                    liveState != null && liveState.runItTwiceLocked())
                            ? alpha : alpha * 0.36f;
            drawSettingsToggleShape(x, firstY, width,
                    liveSettingsDraft != null && liveSettingsDraft.iwtsth(),
                    enabledAlpha);
            drawSettingsToggleShape(x, firstY - 70f, width,
                    liveSettingsDraft != null
                    && liveSettingsDraft.runItTwice(), runItTwiceAlpha);
            drawSettingsStepperShape(x, firstY - 140f, width, enabledAlpha);
            drawSettingsChoiceShape(x, firstY - 210f, width, enabledAlpha);
            drawCompactSettingsRowShape(x, firstY - 280f, width, alpha);
            drawCompactSettingsRowShape(x, firstY - 332f, width, alpha);
        } else if (contentPage == 4) {
            float enabledAlpha = GdxLiveSettingsPolicy.canEditGameRules(
                    tableHost, liveSettingsDraft)
                    ? alpha : alpha * 0.36f;
            float compactGap = 52f;
            drawCompactSettingsStepperShape(x, firstY, width, enabledAlpha);
            drawCompactSettingsStepperShape(x, firstY - compactGap, width,
                    enabledAlpha);
            boolean increasing = liveSettingsDraft != null
                    && liveSettingsDraft.blindsDouble() > 0;
            drawCompactSettingsToggleShape(x, firstY - 2f * compactGap,
                    width, increasing, enabledAlpha);
            drawCompactSettingsStepperShape(x, firstY - 3f * compactGap,
                    width, increasing ? enabledAlpha : enabledAlpha * 0.36f);
            drawCompactSettingsChoiceShape(x, firstY - 4f * compactGap,
                    width, increasing ? enabledAlpha : enabledAlpha * 0.36f);
            boolean capped = increasing && liveSettingsDraft != null
                    && liveSettingsDraft.blindCap() > 0d;
            drawCompactSettingsToggleShape(x, firstY - 5f * compactGap,
                    width, capped, increasing ? enabledAlpha
                            : enabledAlpha * 0.36f);
            drawCompactSettingsStepperShape(x, firstY - 6f * compactGap,
                    width, capped ? enabledAlpha : enabledAlpha * 0.36f);
            float flagsY = firstY - 7f * compactGap;
            float flagGap = 12f;
            float flagW = (width - flagGap) / 2f;
            drawCompactSettingsToggleShape(x, flagsY, flagW,
                    liveSettingsDraft != null && liveSettingsDraft.ante(),
                    enabledAlpha);
            drawCompactSettingsToggleShape(x + flagW + flagGap, flagsY,
                    flagW, liveSettingsDraft != null
                            && liveSettingsDraft.straddle(), enabledAlpha);
        } else if (contentPage == 5) {
            float compactGap = 58f;
            for (int row = 0; row < 6; row++) {
                drawCompactSettingsRowShape(x,
                        firstY - row * compactGap, width, alpha * 0.62f);
            }
        } else if (contentPage == 6) {
            float enabledAlpha = GdxLiveSettingsPolicy.canEditGameRules(
                    tableHost, liveSettingsDraft)
                    ? alpha : alpha * 0.36f;
            float botRebuyAlpha = GdxLiveSettingsPolicy.canEditBotRebuy(
                    tableHost, liveSettingsDraft)
                            ? alpha : alpha * 0.36f;
            drawSettingsStepperShape(x, firstY, width, enabledAlpha);
            drawSettingsToggleShape(x, firstY - 88f, width,
                    liveSettingsDraft != null && liveSettingsDraft.botRebuy(),
                    botRebuyAlpha);
            drawSettingsToggleShape(x, firstY - 176f, width,
                    liveSettingsDraft != null
                    && liveSettingsDraft.botBalanceToHumans(), enabledAlpha);
        } else if (contentPage == 7) {
            float compactGap = 52f;
            for (int row = 0; row < SETTINGS_SESSION_ACTIONS.length; row++) {
                float enabledAlpha = settingsSessionActionEnabled(row)
                        ? alpha : alpha * 0.36f;
                if (row == 0 || row == 5) {
                    boolean on = row == 0
                            ? GdxDisplayModeController.isFullscreenLike()
                            : liveState != null && liveState.lastHand();
                    drawCompactSettingsToggleShape(x,
                            firstY - row * compactGap, width, on,
                            enabledAlpha);
                } else {
                    drawCompactSettingsRowShape(x,
                            firstY - row * compactGap, width, enabledAlpha);
                }
            }
        } else if (contentPage == 8) {
            List<GdxShortcutBindings.ShortcutEntry> entries
                    = shortcutBindings.editableEntries(gameText);
            int first = shortcutPage * SHORTCUT_ROWS_PER_PAGE;
            int visible = Math.min(SHORTCUT_ROWS_PER_PAGE,
                    Math.max(0, entries.size() - first));
            for (int row = 0; row < visible; row++) {
                GdxShortcutBindings.ShortcutEntry entry
                        = entries.get(first + row);
                drawSettingsShortcutShape(x, firstY - row * 70f, width,
                        entry.id().equals(shortcutCaptureId), alpha);
            }
        } else {
            drawSettingsDebugShape(x, firstY, width, alpha);
        }
    }

    private void drawSettingsVolumeShape(float x, float y, float width,
            float alpha) {
        GdxSettingsLayout.VolumeRow row = GdxSettingsLayout.volumeRow(
                x, y, width);
        Rectangle bounds = row.bounds();
        Rectangle slider = row.slider();
        Rectangle minus = row.minusButton();
        Rectangle plus = row.plusButton();
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b,
                0.92f * alpha);
        roundedRect(bounds.x, bounds.y, bounds.width, bounds.height, 11f);
        shapes.setColor(0.025f, 0.060f, 0.105f, 0.94f * alpha);
        roundedRect(bounds.x + 3f, bounds.y + 3f,
                bounds.width - 6f, bounds.height - 6f, 9f);
        shapes.setColor(0.15f, 0.20f, 0.28f, alpha);
        roundedRect(slider.x, slider.y, slider.width, slider.height, 6f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, alpha);
        roundedRect(slider.x, slider.y, slider.width * effectsVolume,
                slider.height, 6f);
        drawDialogButton(minus.x, minus.y, minus.width, minus.height,
                BUTTON_LINE, minus.contains(pointer), alpha);
        drawDialogButton(plus.x, plus.y, plus.width, plus.height,
                BUTTON_LINE, plus.contains(pointer), alpha);
    }

    private void drawSettingsDebugShape(float x, float firstY,
            float width, float alpha) {
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b,
                0.92f * alpha);
        roundedRect(x, firstY - 350f, width, 420f, 11f);
        shapes.setColor(0.008f, 0.012f, 0.020f, 0.98f * alpha);
        roundedRect(x + 3f, firstY - 347f, width - 6f, 414f, 9f);
        float trackX = x + width - 15f;
        shapes.setColor(0.15f, 0.21f, 0.30f, alpha);
        roundedRect(trackX - 4f, firstY - 337f, 14f,
                SETTINGS_DEBUG_VIEWPORT_HEIGHT, 7f);
        List<String> lines = settingsDebugLines();
        float maximum = settingsDebugMaximumPixelScroll(lines.size());
        settingsDebugScroll = preservePixelScrollOnAppend(
                settingsDebugScroll, settingsDebugLineCount, lines.size(),
                SETTINGS_DEBUG_LINE_HEIGHT);
        settingsDebugLineCount = lines.size();
        settingsDebugScroll = MathUtils.clamp(settingsDebugScroll, 0f,
                maximum);
        float thumbH = settingsDebugThumbHeight(lines.size());
        float travel = SETTINGS_DEBUG_VIEWPORT_HEIGHT - thumbH;
        float ratio = maximum == 0f ? 0f : settingsDebugScroll / maximum;
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.78f * alpha);
        roundedRect(trackX - 4f, firstY - 337f + travel * ratio,
                14f, thumbH, 7f);
    }

    private void drawSettingsToggleShape(float x, float y, float width,
            boolean on, float alpha) {
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b,
                0.92f * alpha);
        roundedRect(x, y, width, 68f, 11f);
        shapes.setColor(0.025f, 0.060f, 0.105f, 0.94f * alpha);
        roundedRect(x + 3f, y + 3f, width - 6f, 62f, 9f);
        shapes.setColor(0.20f, 0.32f, 0.45f, 0.24f * alpha);
        roundedRect(x + 10f, y + 43f, width - 20f, 14f, 6f);
        float trackX = x + width - 82f;
        Color track = on ? new Color(0x20c765ff) : new Color(0x253248ff);
        shapes.setColor(track.r, track.g, track.b, alpha);
        roundedRect(trackX, y + 17f, 64f, 36f, 18f);
        Color knob = on ? new Color(0xb8ffc5ff) : new Color(0x8290a4ff);
        shapes.setColor(knob.r, knob.g, knob.b, alpha);
        shapes.circle(trackX + (on ? 46f : 18f), y + 35f, 13f, 28);
    }

    private void drawCompactSettingsRowShape(float x, float y, float width,
            float alpha) {
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b,
                0.90f * alpha);
        roundedRect(x, y, width, 46f, 9f);
        shapes.setColor(0.025f, 0.060f, 0.105f, 0.96f * alpha);
        roundedRect(x + 3f, y + 3f, width - 6f, 40f, 7f);
        shapes.setColor(0.20f, 0.32f, 0.45f, 0.22f * alpha);
        roundedRect(x + 10f, y + 30f, width - 20f, 8f, 4f);
    }

    private void drawCompactSettingsToggleShape(float x, float y, float width,
            boolean on, float alpha) {
        drawCompactSettingsRowShape(x, y, width, alpha);
        float trackX = x + width - 74f;
        Color track = on ? new Color(0x20c765ff) : new Color(0x253248ff);
        shapes.setColor(track.r, track.g, track.b, alpha);
        roundedRect(trackX, y + 9f, 56f, 28f, 14f);
        Color knob = on ? new Color(0xb8ffc5ff) : new Color(0x8290a4ff);
        shapes.setColor(knob.r, knob.g, knob.b, alpha);
        shapes.circle(trackX + (on ? 41f : 15f), y + 23f, 10f, 24);
    }

    private void drawCompactSettingsStepperShape(float x, float y,
            float width, float alpha) {
        drawCompactSettingsRowShape(x, y, width, alpha);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.24f * alpha);
        roundedRect(x + width - 164f, y + 3f, 72f, 40f, 7f);
        roundedRect(x + width - 78f, y + 3f, 72f, 40f, 7f);
    }

    private void drawCompactSettingsChoiceShape(float x, float y,
            float width, float alpha) {
        drawCompactSettingsRowShape(x, y, width, alpha);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, alpha);
        shapes.rectLine(x + width - 45f, y + 14f,
                x + width - 33f, y + 23f, 3f);
        shapes.rectLine(x + width - 33f, y + 23f,
                x + width - 45f, y + 32f, 3f);
    }

    private void drawSettingsChoiceShape(float x, float y, float width,
            float alpha) {
        drawSettingsToggleShape(x, y, width, false, alpha);
        // Cover the toggle track; choice rows use a chevron instead.
        shapes.setColor(0.025f, 0.060f, 0.105f, 0.98f * alpha);
        roundedRect(x + width - 96f, y + 7f, 84f, 54f, 8f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, alpha);
        shapes.rectLine(x + width - 45f, y + 24f,
                x + width - 33f, y + 35f, 3f);
        shapes.rectLine(x + width - 33f, y + 35f,
                x + width - 45f, y + 46f, 3f);
    }

    private void drawSettingsStepperShape(float x, float y, float width,
            float alpha) {
        drawSettingsToggleShape(x, y, width, false, alpha);
        shapes.setColor(0.025f, 0.060f, 0.105f, 0.98f * alpha);
        roundedRect(x + width - 432f, y + 7f, 420f, 54f, 8f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.32f * alpha);
        roundedRect(x + width - 426f, y + 10f, 62f, 48f, 7f);
        roundedRect(x + width - 74f, y + 10f, 62f, 48f, 7f);
    }

    private void drawSettingsShortcutShape(float x, float y, float width,
            boolean capturing, float alpha) {
        Color border = capturing ? POT_GOLD : BUTTON_LINE;
        shapes.setColor(border.r, border.g, border.b,
                0.92f * alpha);
        roundedRect(x, y, width, 62f, 11f);
        shapes.setColor(0.025f, 0.060f, 0.105f, 0.94f * alpha);
        roundedRect(x + 3f, y + 3f, width - 6f, 56f, 9f);
        Color keySurface = capturing ? POT_GOLD : CYAN;
        shapes.setColor(keySurface.r, keySurface.g, keySurface.b,
                (capturing ? 0.40f : 0.28f) * alpha);
        roundedRect(x + 12f, y + 8f, 216f, 46f, 8f);
    }

    private void drawSettingsContentText(float x, float firstY,
            float width, float alpha) {
        int contentPage = settingsContentPage();
        List<String> subpages = settingsSubpageLabels();
        String title = GdxSettingsContract.contentHeading(settingsSection(),
                subpages.get(MathUtils.clamp(settingsSubpageIndex(), 0,
                        subpages.size() - 1)));
        drawLeftInBox(smallFont, title, x, settingsContentTitleY(firstY),
                width, 28f, POT_GOLD, alpha);
        if (contentPage == 0) {
            GdxSettingsContract.TogglePage page = settingsAudioPage();
            if (settingsAudioPage == 0) {
                GdxSettingsLayout.VolumeRow row =
                        GdxSettingsLayout.volumeRow(x, firstY, width);
                Rectangle label = row.label();
                Rectangle percentage = row.percentage();
                Rectangle minus = row.minusButton();
                Rectangle plus = row.plusButton();
                drawLeftInBox(smallFont, uppercase(gameText.translate(
                        "gdx.settings.row.master_volume")),
                        label.x, label.y, label.width, label.height,
                        Color.WHITE, alpha);
                drawFittedCenteredInBox(smallFont,
                        Math.round(effectsVolume * 100f) + "%",
                        percentage.x, percentage.y, percentage.width,
                        percentage.height, POT_GOLD, alpha);
                drawFittedCenteredInBox(actionFont, "−",
                        minus.x, minus.y, minus.width, minus.height,
                        Color.WHITE, alpha);
                drawFittedCenteredInBox(actionFont, "+",
                        plus.x, plus.y, plus.width, plus.height,
                        Color.WHITE, alpha);
            }
            float audioFirstY = firstY
                    - (settingsAudioPage == 0 ? 70f : 0f);
            for (int row = 0; row < page.options().size(); row++) {
                GdxSettingsContract.ToggleOption option =
                        page.options().get(row);
                boolean enabled = settingsOptionEnabled(option);
                drawSettingsRowText(x, audioFirstY - row * 70f, width,
                        option.label(gameText),
                        enabled ? alpha : alpha * 0.36f);
            }
            if (GdxSettingsContract.hasVoiceRetention(page)) {
                drawSettingsStepperText(x,
                        audioFirstY - page.options().size() * 70f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.keep_voice_notes")),
                        GdxSettingsContract.voiceRetentionLabel(
                                tableSettingsProperties(), gameText), alpha);
                float actionY = audioFirstY
                        - page.options().size() * 70f - 70f;
                float half = (width - 12f) / 2f;
                drawFittedCenteredInBox(smallFont,
                        uppercase(gameText.translate("audio.ver_notas")),
                        x, actionY, half, 58f, Color.WHITE, alpha);
                drawFittedCenteredInBox(smallFont,
                        uppercase(gameText.translate("audio.purgar_notas")),
                        x + half + 12f, actionY, half, 58f,
                        Color.WHITE, alpha);
            }
            if (GdxSettingsContract.hasAudioDevices(page)) {
                drawSettingsStepperText(x, audioFirstY, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.game_output")),
                        GdxAudioDevices.outputLabel(
                                tableSettingsProperties(), gameText), alpha);
                drawSettingsStepperText(x, audioFirstY - 70f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.microphone")),
                        GdxAudioDevices.captureLabel(
                                tableSettingsProperties(), gameText), alpha);
            }
        } else if (contentPage == 1) {
            if (settingsAppearancePage == 0) {
                drawSettingsStepperText(x, firstY, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.deck")),
                        GdxAppearanceOptions.deckLabel(liveDeck, gameText),
                        alpha);
                drawSettingsStepperText(x, firstY - 70f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.card_back")),
                        GdxAppearanceOptions.cardBackLabel(
                                presentationSettings == null ? "default"
                                        : presentationSettings.cardBack(),
                                gameText),
                        presentationSettings == null
                                ? alpha * 0.36f : alpha);
                drawSettingsStepperText(x, firstY - 140f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.felt")),
                        GdxAppearanceOptions.feltLabel(
                                presentationSettings == null ? "verde"
                                        : presentationSettings.felt(),
                                gameText),
                        presentationSettings == null
                                ? alpha * 0.36f : alpha);
                drawSettingsStepperText(x, firstY - 210f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.light_off")),
                        GdxAppearanceOptions.lightLevelLabel(
                                tableSettingsProperties()), alpha);
                drawSettingsStepperText(x, firstY - 280f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.window_mode")),
                        tableWindowModeSettingLabel(), alpha);
                drawSettingsStepperText(x, firstY - 350f, width,
                        uppercase(gameText.translate(
                                "gdx.settings.row.antialiasing")),
                        tableMsaaSettingLabel(),
                        presentationSettings == null
                                ? alpha * 0.36f : alpha);
            } else {
                GdxSettingsContract.TogglePage page =
                        settingsAppearanceTogglePage();
                if (GdxSettingsContract.hasAppearanceAnimationOptions(page)) {
                    for (int row = 0;
                            row < GdxAppearanceOptions.ANIMATION_CHOICES.size();
                            row++) {
                        GdxAppearanceOptions.Choice option =
                                GdxAppearanceOptions.ANIMATION_CHOICES.get(row);
                        boolean enabled = GdxAppearanceOptions.enabled(option,
                                tableSettingsProperties());
                        drawSettingsStepperText(x, firstY - row * 70f, width,
                                option.label(gameText),
                                GdxAppearanceOptions.selectedLabel(option,
                                        tableSettingsProperties(), gameText),
                                enabled ? alpha : alpha * 0.36f);
                    }
                    return;
                }
                for (int row = 0; row < page.options().size(); row++) {
                    GdxSettingsContract.ToggleOption option =
                            page.options().get(row);
                    boolean enabled = GdxSettingsContract.enabled(option,
                            tableSettingsProperties(), audioControl.enabled());
                    drawSettingsRowText(x, firstY - row * 70f, width,
                            option.label(gameText),
                            enabled ? alpha : alpha * 0.36f);
                }
            }
        } else if (contentPage == 2) {
            drawSettingsRowText(x, firstY, width,
                    settingsGameText("row.auto_buttons"), alpha);
            drawSettingsRowText(x, firstY - 70f, width,
                    settingsGameText("row.auto_call") + "  ·  "
                            + (autoCallMax == 0d
                                    ? settingsGameText("value.no_limit")
                                    : settingsGameText("value.maximum") + " "
                                    + formatAmount(autoCallMax)),
                    autoButtons ? alpha : alpha * 0.36f);
            drawSettingsRowText(x, firstY - 140f, width,
                    settingsGameText("row.persist_auto"),
                    autoButtons ? alpha : alpha * 0.36f);
            drawSettingsRowText(x, firstY - 210f, width,
                    settingsGameText("row.confirm_auto"),
                    autoButtons ? alpha : alpha * 0.36f);
            drawSettingsRowText(x, firstY - 280f, width,
                    settingsGameText("row.confirm_actions"), alpha);
            drawSettingsRowText(x, firstY - 350f, width,
                    settingsGameText("row.auto_rebuy"),
                    tableRebuyAllowed ? alpha : alpha * 0.36f);
        } else if (contentPage == 3) {
            float enabledAlpha = GdxLiveSettingsPolicy.canEditGameRules(
                    tableHost, liveSettingsDraft)
                    ? alpha : alpha * 0.36f;
            float runItTwiceAlpha = GdxLiveSettingsPolicy.canEditRunItTwice(
                    tableHost, liveSettingsDraft,
                    liveState != null && liveState.runItTwiceLocked())
                            ? alpha : alpha * 0.36f;
            drawSettingsRowText(x, firstY, width,
                    settingsGameText("row.iwtsth"), enabledAlpha);
            drawSettingsRowText(x, firstY - 70f, width,
                    settingsGameText("row.run_it_twice"), runItTwiceAlpha);
            drawSettingsStepperText(x, firstY - 140f, width,
                    settingsGameText("row.rabbit_hunting"), rabbitRuleLabel(),
                    enabledAlpha);
            drawSettingsRowText(x, firstY - 210f, width,
                    settingsGameText("row.hand_limit") + "  ·  "
                            + handLimitSettingLabel(),
                    enabledAlpha);
            if (liveSettingsDraft == null) {
                drawCompactSettingsRowText(x, firstY - 280f, width,
                        settingsGameText("row.think_time") + "  ·  "
                                + settingsGameText("value.unavailable"), alpha);
                drawCompactSettingsRowText(x, firstY - 332f, width,
                        settingsGameText("summary.label.showdown_time")
                                + "  ·  "
                                + settingsGameText("value.unavailable"), alpha);
            } else {
                List<String> timingLabels =
                        GdxLiveSettingsSummary.timingLabels(
                                liveSettingsDraft, gameText);
                drawCompactSettingsRowText(x, firstY - 280f, width,
                        timingLabels.get(0), alpha);
                drawCompactSettingsRowText(x, firstY - 332f, width,
                        timingLabels.get(1), alpha);
            }
        } else if (contentPage == 4) {
            float enabledAlpha = tableHost && liveSettingsDraft != null
                    ? alpha : alpha * 0.36f;
            float compactGap = 52f;
            drawCompactSettingsStepperText(x, firstY, width,
                    settingsGameText("row.structure"),
                    draftBlindStructureLabel(), enabledAlpha);
            drawCompactSettingsStepperText(x, firstY - compactGap, width,
                    settingsGameText("row.level"),
                    liveSettingsDraft == null ? "—"
                            : formatAmount(liveSettingsDraft.smallBlind())
                            + " / " + formatAmount(
                                    liveSettingsDraft.bigBlind()),
                    enabledAlpha);
            boolean increasing = liveSettingsDraft != null
                    && liveSettingsDraft.blindsDouble() > 0;
            drawCompactSettingsRowText(x, firstY - 2f * compactGap, width,
                    settingsGameText("row.increase_blinds"), enabledAlpha);
            drawCompactSettingsStepperText(x, firstY - 3f * compactGap,
                    width, settingsGameText("row.interval"),
                    liveSettingsDraft == null ? "—"
                            : Integer.toString(Math.max(1,
                                    liveSettingsDraft.blindsDouble())),
                    increasing ? enabledAlpha : enabledAlpha * 0.36f);
            drawCompactSettingsRowText(x, firstY - 4f * compactGap, width,
                    settingsGameText("row.unit") + "  ·  "
                            + (liveSettingsDraft != null
                            && liveSettingsDraft.blindsDoubleType() == 2
                                    ? settingsGameText("value.hands")
                                    : settingsGameText("value.minutes")),
                    increasing ? enabledAlpha : enabledAlpha * 0.36f);
            boolean capped = increasing && liveSettingsDraft != null
                    && liveSettingsDraft.blindCap() > 0d;
            drawCompactSettingsRowText(x, firstY - 5f * compactGap, width,
                    settingsGameText("row.blind_cap"),
                    increasing ? enabledAlpha : enabledAlpha * 0.36f);
            drawCompactSettingsStepperText(x, firstY - 6f * compactGap,
                    width, settingsGameText("row.cap"),
                    liveSettingsDraft == null
                            || liveSettingsDraft.blindCap() <= 0d ? "—"
                                    : formatAmount(
                                            liveSettingsDraft.blindCap()),
                    capped ? enabledAlpha : enabledAlpha * 0.36f);
            float flagsY = firstY - 7f * compactGap;
            float flagGap = 12f;
            float flagW = (width - flagGap) / 2f;
            drawCompactSettingsRowText(x, flagsY, flagW,
                    settingsGameText("row.ante"), enabledAlpha);
            drawCompactSettingsRowText(x + flagW + flagGap, flagsY,
                    flagW, settingsGameText("row.straddle"), enabledAlpha);
        } else if (contentPage == 5) {
            float compactGap = 58f;
            drawFittedCenteredInBox(smallFont,
                    GdxLiveSettingsSummary.purchaseHeading(gameText),
                    x, firstY + 48f, width, 28f,
                    Color.LIGHT_GRAY, alpha * 0.78f);
            List<String> purchaseLabels = liveSettingsDraft == null
                    ? GdxLiveSettingsSummary.unavailablePurchaseLabels(
                            gameText)
                    : GdxLiveSettingsSummary.purchaseLabels(
                            liveSettingsDraft, gameText);
            for (int row = 0; row < purchaseLabels.size(); row++) {
                drawCompactSettingsRowText(x,
                        firstY - row * compactGap, width,
                        purchaseLabels.get(row), alpha * 0.72f);
            }
        } else if (contentPage == 6) {
            float enabledAlpha = GdxLiveSettingsPolicy.canEditGameRules(
                    tableHost, liveSettingsDraft)
                    ? alpha : alpha * 0.36f;
            float botRebuyAlpha = GdxLiveSettingsPolicy.canEditBotRebuy(
                    tableHost, liveSettingsDraft)
                            ? alpha : alpha * 0.36f;
            drawSettingsStepperText(x, firstY, width,
                    settingsGameText("row.bot_difficulty"),
                    botDifficultyText(), enabledAlpha);
            drawSettingsRowText(x, firstY - 88f, width,
                    settingsGameText("row.bot_rebuy"), botRebuyAlpha);
            drawSettingsRowText(x, firstY - 176f, width,
                    settingsGameText("row.bot_balance"), enabledAlpha);
        } else if (contentPage == 7) {
            float compactGap = 52f;
            for (int row = 0; row < SETTINGS_SESSION_ACTIONS.length; row++) {
                boolean enabled = settingsSessionActionEnabled(row);
                Color color = row >= 7 ? CONTEXT_EXIT
                        : row == 5 && liveState != null && liveState.lastHand()
                                ? STACK_GREEN : Color.WHITE;
                drawLeftInBox(smallFont, settingsSessionActionLabel(row),
                        x + 18f, firstY - row * compactGap + 7f,
                        width - 118f, 32f, color,
                        (enabled ? 0.94f : 0.36f) * alpha);
            }
        } else if (contentPage == 8) {
            List<GdxShortcutBindings.ShortcutEntry> entries
                    = shortcutBindings.editableEntries(gameText);
            int first = shortcutPage * SHORTCUT_ROWS_PER_PAGE;
            int visible = Math.min(SHORTCUT_ROWS_PER_PAGE,
                    Math.max(0, entries.size() - first));
            for (int row = 0; row < visible; row++) {
                GdxShortcutBindings.ShortcutEntry entry
                        = entries.get(first + row);
                boolean capturing = entry.id().equals(shortcutCaptureId);
                drawSettingsShortcutText(x, firstY - row * 70f, width,
                        capturing ? uppercase(gameText.translate(
                                "gdx.settings.shortcut.press_key"))
                                : entry.display(),
                        uppercase(entry.description()), capturing, alpha);
            }
            if (!shortcutStatus.isBlank()) {
                boolean warning = shortcutStatus.equals("conflict")
                        || shortcutStatus.equals("unsupported");
                drawFittedCenteredInBox(smallFont,
                        uppercase(gameText.translate(
                                "gdx.settings.shortcut.status."
                                        + shortcutStatus)),
                        x, firstY + 64f, width, 20f,
                        warning ? FOLD_RED : CYAN, alpha);
            }
        }
    }

    private void drawSettingsDebugTextLayer(float x, float firstY,
            float width, float alpha) {
        List<String> lines = settingsDebugLines();
        float viewportBottom = firstY - 337f;
        float maximum = settingsDebugMaximumPixelScroll(lines.size());
        settingsDebugScroll = MathUtils.clamp(settingsDebugScroll, 0f,
                maximum);
        int screenX = Math.round(viewport.getScreenX()
                + (x + 12f) * viewport.getScreenWidth()
                / viewport.getWorldWidth());
        int screenY = Math.round(viewport.getScreenY()
                + viewportBottom * viewport.getScreenHeight()
                / viewport.getWorldHeight());
        int screenWidth = Math.max(1, Math.round((width - 46f)
                * viewport.getScreenWidth() / viewport.getWorldWidth()));
        int screenHeight = Math.max(1, Math.round(
                SETTINGS_DEBUG_VIEWPORT_HEIGHT * viewport.getScreenHeight()
                / viewport.getWorldHeight()));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(screenX, screenY, screenWidth, screenHeight);
        batch.begin();
        for (int i = 0; i < lines.size(); i++) {
            float lineY = anchoredPixelRowY(lines.size(), i,
                    SETTINGS_DEBUG_LINE_HEIGHT, viewportBottom,
                    SETTINGS_DEBUG_VIEWPORT_HEIGHT, settingsDebugScroll,
                    maximum);
            if (lineY + SETTINGS_DEBUG_LINE_HEIGHT < viewportBottom
                    || lineY > viewportBottom
                    + SETTINGS_DEBUG_VIEWPORT_HEIGHT) continue;
            String line = lines.get(i);
            drawLeftInBox(gameLogFont,
                    ellipsizeSettingsDebugLine(line, width),
                    x + 14f, lineY, width - 42f, 23f,
                    settingsDebugLineColor(line), alpha);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    static float settingsDebugMaximumPixelScroll(int lineCount) {
        return Math.max(0f, lineCount * SETTINGS_DEBUG_LINE_HEIGHT
                - SETTINGS_DEBUG_VIEWPORT_HEIGHT);
    }

    static float preservePixelScrollOnAppend(float currentScroll,
            int previousLineCount, int currentLineCount, float lineHeight) {
        if (currentScroll <= 0f || currentLineCount <= previousLineCount) {
            return Math.max(0f, currentScroll);
        }
        return currentScroll
                + (currentLineCount - previousLineCount) * lineHeight;
    }

    private static float settingsDebugThumbHeight(int lineCount) {
        float contentHeight = lineCount * SETTINGS_DEBUG_LINE_HEIGHT;
        return contentHeight <= SETTINGS_DEBUG_VIEWPORT_HEIGHT
                ? SETTINGS_DEBUG_VIEWPORT_HEIGHT
                : Math.max(34f, SETTINGS_DEBUG_VIEWPORT_HEIGHT
                        * SETTINGS_DEBUG_VIEWPORT_HEIGHT / contentHeight);
    }

    private String ellipsizeSettingsDebugLine(String value, float width) {
        return ellipsizeToWidth(gameLogFont,
                value == null ? "" : value.replace('\t', ' '),
                Math.max(0f, width - 42f));
    }

    private static List<String> settingsDebugLines() {
        return DebugLog.snapshot().lines().toList();
    }

    private static Color settingsDebugLineColor(String line) {
        if (line.startsWith("SEVERE:")) return FOLD_RED;
        if (line.startsWith("WARNING:")) return POT_GOLD;
        if (line.startsWith("CONFIG:")) return CYAN;
        if (line.startsWith("FINE:") || line.startsWith("FINER:")
                || line.startsWith("FINEST:")) return SEAT_RIM;
        if (line.startsWith("INFO:")) return STACK_GREEN;
        return Color.LIGHT_GRAY;
    }

    private void drawSettingsRowText(float x, float y, float width,
            String label, float alpha) {
        drawLeftInBox(actionFont, label, x + 20f, y + 5f,
                width - 126f, 58f, Color.WHITE, alpha);
    }

    private void drawSettingsStepperText(float x, float y, float width,
            String label, String value, float alpha) {
        drawLeftInBox(actionFont, label, x + 20f, y + 5f,
                width - 466f, 58f, Color.WHITE, alpha);
        drawFittedCenteredInBox(actionFont, "-", x + width - 426f,
                y + 5f, 62f, 54f, Color.WHITE, alpha);
        drawFittedCenteredInBox(smallFont, value, x + width - 362f,
                y + 5f, 286f, 54f, POT_GOLD, alpha);
        drawFittedCenteredInBox(actionFont, "+", x + width - 74f,
                y + 5f, 62f, 54f, Color.WHITE, alpha);
    }

    static float settingsContentTitleY(float firstRowY) {
        // The first content row ends at firstRowY + 64. Keep the heading
        // immediately above it; GdxSettingsLayout reserves the clearance to
        // either one or two rows of subsection tabs.
        return firstRowY + 66f;
    }

    private void drawCompactSettingsRowText(float x, float y, float width,
            String label, float alpha) {
        drawLeftInBox(smallFont, label, x + 18f, y + 3f,
                width - 112f, 40f, Color.WHITE, alpha);
    }

    private void drawCompactSettingsStepperText(float x, float y,
            float width, String label, String value, float alpha) {
        drawLeftInBox(smallFont, label, x + 18f, y + 3f,
                width - 340f, 40f, Color.WHITE, alpha);
        drawFittedCenteredInBox(actionFont, "−",
                x + width - 164f, y + 3f, 72f, 40f,
                Color.WHITE, alpha);
        drawFittedCenteredInBox(smallFont, value,
                x + width - 330f, y + 3f, 158f, 40f,
                POT_GOLD, alpha);
        drawFittedCenteredInBox(actionFont, "+",
                x + width - 78f, y + 3f, 72f, 40f,
                Color.WHITE, alpha);
    }

    private String rabbitRuleLabel() {
        if (liveSettingsDraft == null) {
            return GdxLiveSettingsSummary.rabbitHuntingLabel(-1, gameText);
        }
        return GdxLiveSettingsSummary.rabbitHuntingLabel(
                liveSettingsDraft.rabbitHunting(), gameText);
    }

    private String botDifficultyText() {
        if (liveBotDifficultyDraft == null) {
            return settingsGameText("value.unavailable");
        }
        return switch (liveBotDifficultyDraft) {
            case EASY -> settingsGameText("value.easy");
            case MEDIUM -> settingsGameText("value.medium");
            case HARD -> settingsGameText("value.hard");
        };
    }

    private String handLimitSettingLabel() {
        if (liveSettingsDraft == null) {
            return settingsGameText("value.unavailable");
        }
        return liveSettingsDraft.hands() == -1
                ? settingsGameText("value.no_limit")
                : Integer.toString(liveSettingsDraft.hands());
    }

    private List<GameConfigCodecV1.BlindLevel> draftBlindLevels() {
        if (liveSettingsDraft == null) return List.of();
        if (!liveSettingsDraft.blindStructure().isEmpty()) {
            return liveSettingsDraft.blindStructure();
        }
        return Arrays.stream(BlindStructureRules.defaultLevels())
                .map(level -> new GameConfigCodecV1.BlindLevel(
                        level[0], level[1]))
                .toList();
    }

    private List<DraftBlindStructure> availableDraftBlindStructures() {
        ArrayList<DraftBlindStructure> structures = new ArrayList<>();
        structures.add(new DraftBlindStructure("PREDETERMINADA", List.of()));
        for (BlindStructureCatalog.Entry entry : BlindStructureCatalog.read(
                tableSettingsProperties())) {
            structures.add(new DraftBlindStructure(entry.name(),
                    entry.levels().stream()
                            .map(level -> new GameConfigCodecV1.BlindLevel(
                                    level.smallBlind(), level.bigBlind()))
                            .toList()));
        }
        return List.copyOf(structures);
    }

    private static boolean sameBlindStructure(
            List<GameConfigCodecV1.BlindLevel> left,
            List<GameConfigCodecV1.BlindLevel> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (Math.round(left.get(index).smallBlind() * 100d)
                    != Math.round(right.get(index).smallBlind() * 100d)
                    || Math.round(left.get(index).bigBlind() * 100d)
                    != Math.round(right.get(index).bigBlind() * 100d)) {
                return false;
            }
        }
        return true;
    }

    private int draftBlindStructureIndex(
            List<DraftBlindStructure> structures) {
        if (liveSettingsDraft == null) return -1;
        List<GameConfigCodecV1.BlindLevel> active =
                liveSettingsDraft.blindStructure();
        for (int index = 0; index < structures.size(); index++) {
            if (sameBlindStructure(active, structures.get(index).levels())) {
                return index;
            }
        }
        return -1;
    }

    private void selectDraftBlindStructure(int direction) {
        if (liveSettingsDraft == null || direction == 0) return;
        List<DraftBlindStructure> structures = availableDraftBlindStructures();
        if (structures.isEmpty()) return;
        int oldLevel = Math.max(0, draftBlindLevelIndex());
        int current = draftBlindStructureIndex(structures);
        int selectedIndex = current < 0 ? 0
                : Math.floorMod(current + Integer.signum(direction),
                        structures.size());
        DraftBlindStructure selected = structures.get(selectedIndex);
        List<GameConfigCodecV1.BlindLevel> effective = selected.levels().isEmpty()
                ? Arrays.stream(BlindStructureRules.defaultLevels())
                        .map(level -> new GameConfigCodecV1.BlindLevel(
                                level[0], level[1]))
                        .toList()
                : selected.levels();
        int levelIndex = Math.min(oldLevel, effective.size() - 1);
        GameConfigCodecV1.BlindLevel level = effective.get(levelIndex);
        double cap = liveSettingsDraft.blindCap();
        if (cap > 0d && cap < level.bigBlind()) cap = level.bigBlind();
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                level.smallBlind(), level.bigBlind(),
                liveSettingsDraft.blindsDouble(),
                liveSettingsDraft.blindsDoubleType(), cap,
                selected.levels());
        play(buttonOnSound, 0.55f, 1f);
    }

    private void adjustDraftRabbitHunting(int direction) {
        if (liveSettingsDraft == null || direction == 0) return;
        int choices = NewGameTableDraft.RabbitHunting.values().length;
        liveSettingsDraft = liveSettingsDraft.withRabbitHunting(
                Math.floorMod(liveSettingsDraft.rabbitHunting()
                        + Integer.signum(direction), choices));
    }

    private void adjustDraftBotDifficulty(int direction) {
        if (liveBotDifficultyDraft == null || direction == 0) return;
        NewGameTableDraft.BotDifficulty[] values =
                NewGameTableDraft.BotDifficulty.values();
        liveBotDifficultyDraft = values[Math.floorMod(
                liveBotDifficultyDraft.ordinal() + Integer.signum(direction),
                values.length)];
    }

    private int draftBlindLevelIndex() {
        if (liveSettingsDraft == null) return -1;
        List<GameConfigCodecV1.BlindLevel> levels = draftBlindLevels();
        long current = Math.round(liveSettingsDraft.smallBlind() * 100d);
        for (int i = 0; i < levels.size(); i++) {
            if (Math.round(levels.get(i).smallBlind() * 100d) == current) {
                return i;
            }
        }
        return -1;
    }

    private void changeDraftBlindLevel(int direction) {
        List<GameConfigCodecV1.BlindLevel> levels = draftBlindLevels();
        int current = draftBlindLevelIndex();
        if (liveSettingsDraft == null || levels.isEmpty() || current < 0) return;
        int target = MathUtils.clamp(current + Integer.signum(direction),
                0, levels.size() - 1);
        GameConfigCodecV1.BlindLevel selected = levels.get(target);
        double cap = liveSettingsDraft.blindCap();
        if (cap > 0d && cap < selected.bigBlind()) cap = selected.bigBlind();
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                selected.smallBlind(), selected.bigBlind(),
                liveSettingsDraft.blindsDouble(),
                liveSettingsDraft.blindsDoubleType(), cap,
                liveSettingsDraft.blindStructure());
    }

    private void toggleDraftBlindIncrease() {
        if (liveSettingsDraft == null) return;
        int interval = liveSettingsDraft.blindsDouble() > 0
                ? 0 : 60;
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                liveSettingsDraft.smallBlind(), liveSettingsDraft.bigBlind(),
                interval, liveSettingsDraft.blindsDoubleType(),
                interval == 0 ? 0d : liveSettingsDraft.blindCap(),
                liveSettingsDraft.blindStructure());
    }

    private void changeDraftBlindInterval(int direction) {
        if (liveSettingsDraft == null
                || liveSettingsDraft.blindsDouble() <= 0) return;
        int next = MathUtils.clamp(liveSettingsDraft.blindsDouble()
                + Integer.signum(direction), 1, 1_000_000);
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                liveSettingsDraft.smallBlind(), liveSettingsDraft.bigBlind(),
                next, liveSettingsDraft.blindsDoubleType(),
                liveSettingsDraft.blindCap(),
                liveSettingsDraft.blindStructure());
    }

    private void toggleDraftBlindIntervalType() {
        if (liveSettingsDraft == null
                || liveSettingsDraft.blindsDouble() <= 0) return;
        int type = liveSettingsDraft.blindsDoubleType() == 1 ? 2 : 1;
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                liveSettingsDraft.smallBlind(), liveSettingsDraft.bigBlind(),
                liveSettingsDraft.blindsDouble(), type,
                liveSettingsDraft.blindCap(),
                liveSettingsDraft.blindStructure());
    }

    private void toggleDraftBlindCap() {
        if (liveSettingsDraft == null
                || liveSettingsDraft.blindsDouble() <= 0) return;
        double cap = 0d;
        if (liveSettingsDraft.blindCap() <= 0d) {
            List<GameConfigCodecV1.BlindLevel> levels = draftBlindLevels();
            int current = draftBlindLevelIndex();
            if (current < 0 || levels.isEmpty()) return;
            cap = levels.get(Math.min(current + 1,
                    levels.size() - 1)).bigBlind();
        }
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                liveSettingsDraft.smallBlind(), liveSettingsDraft.bigBlind(),
                liveSettingsDraft.blindsDouble(),
                liveSettingsDraft.blindsDoubleType(), cap,
                liveSettingsDraft.blindStructure());
    }

    private void changeDraftBlindCap(int direction) {
        if (liveSettingsDraft == null || liveSettingsDraft.blindCap() <= 0d) return;
        List<GameConfigCodecV1.BlindLevel> levels = draftBlindLevels();
        if (levels.isEmpty()) return;
        long cap = Math.round(liveSettingsDraft.blindCap() * 100d);
        int current = 0;
        for (int i = 0; i < levels.size(); i++) {
            current = i;
            if (Math.round(levels.get(i).bigBlind() * 100d) >= cap) break;
        }
        int minimum = Math.max(0, draftBlindLevelIndex());
        int target = MathUtils.clamp(current + Integer.signum(direction),
                minimum, levels.size() - 1);
        liveSettingsDraft = liveSettingsDraft.withBlindSettings(
                liveSettingsDraft.smallBlind(), liveSettingsDraft.bigBlind(),
                liveSettingsDraft.blindsDouble(),
                liveSettingsDraft.blindsDoubleType(),
                levels.get(target).bigBlind(),
                liveSettingsDraft.blindStructure());
    }

    private String draftBlindStructureLabel() {
        if (liveSettingsDraft == null) {
            return settingsGameText("value.unavailable");
        }
        List<DraftBlindStructure> structures = availableDraftBlindStructures();
        int selected = draftBlindStructureIndex(structures);
        if (selected >= 0) return structures.get(selected).label();
        return settingsGameText("value.active_levels",
                liveSettingsDraft.blindStructure().size());
    }

    private record DraftBlindStructure(String label,
            List<GameConfigCodecV1.BlindLevel> levels) {
        DraftBlindStructure {
            label = label == null || label.isBlank() ? "SIN NOMBRE" : label;
            levels = List.copyOf(levels);
        }
    }

    private void drawSettingsShortcutText(float x, float y, float width,
            String key, String description, boolean capturing, float alpha) {
        drawFittedCenteredInBox(capturing ? smallFont : actionFont, key,
                x + 12f, y + 8f, 216f, 46f,
                capturing ? Color.WHITE : CYAN, alpha);
        drawLeftInBox(smallFont, description, x + 248f, y + 5f,
                width - 264f, 52f, Color.WHITE, alpha);
    }

    private void drawGameLogDialog() {
        float alpha = uiFade();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1160f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float logX = panelX + 34f;
        float logY = panelY + 105f;
        float logW = panelW - 68f;
        float logH = panelH - 230f;
        List<String> logLines = gameLogLines();
        if (logLines.isEmpty()) logLines = List.of("—");
        Rectangle content = gameLogContentBounds();
        float maximumScroll = gameLogMaximumPixelScroll(logLines.size(),
                content.height);
        if (logLines.size() > gameLogLineCount && gameLogScroll > 0f) {
            gameLogScroll += (logLines.size() - gameLogLineCount)
                    * GAME_LOG_LINE_HEIGHT;
        }
        gameLogLineCount = logLines.size();
        gameLogScroll = MathUtils.clamp(gameLogScroll, 0f, maximumScroll);
        float trackHeight = logH - 28f;
        float thumbHeight = maximumScroll == 0 ? trackHeight
                : Math.max(44f, trackHeight * content.height
                        / (logLines.size() * GAME_LOG_LINE_HEIGHT));
        float thumbY = logY + 14f + (trackHeight - thumbHeight)
                * (maximumScroll == 0 ? 0f
                        : gameLogScroll / maximumScroll);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.20f * alpha);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.58f * alpha);
        roundedRect(panelX + 9f, panelY - 10f, panelW, panelH, 20f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.82f * alpha);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f, panelH + 4f, 20f);
        shapes.setColor(0.015f, 0.030f, 0.052f, 0.985f * alpha);
        roundedRect(panelX, panelY, panelW, panelH, 18f);
        shapes.setColor(0.004f, 0.012f, 0.020f, 0.96f * alpha);
        roundedRect(logX, logY, logW, logH, 10f);
        int selectedFirst = Math.min(gameLogSelectionAnchor,
                gameLogSelectionCaret);
        int selectedLast = Math.max(gameLogSelectionAnchor,
                gameLogSelectionCaret);
        for (int line = 0; line < logLines.size(); line++) {
            if (selectedFirst >= 0 && line >= selectedFirst
                    && line <= selectedLast) {
                float rowY = anchoredPixelRowY(logLines.size(), line,
                        GAME_LOG_LINE_HEIGHT, content.y, content.height,
                        gameLogScroll, maximumScroll);
                float clippedY = Math.max(rowY, content.y);
                float clippedTop = Math.min(rowY + GAME_LOG_LINE_HEIGHT,
                        content.y + content.height);
                if (clippedTop <= clippedY) continue;
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.22f * alpha);
                shapes.rect(content.x, clippedY, content.width,
                        clippedTop - clippedY);
            }
        }
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b, 0.85f * alpha);
        roundedRect(logX + logW - 24f, logY + 14f, 14f,
                logH - 28f, 7f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.88f * alpha);
        roundedRect(logX + logW - 24f, thumbY, 14f, thumbHeight, 7f);
        drawDialogButton(panelX + panelW - 224f, panelY + 24f,
                194f, 58f, POT_GOLD,
                contains(pointer.x, pointer.y, panelX + panelW - 224f,
                        panelY + 24f, 194f, 58f), alpha);
        shapes.end();

        batch.begin();
        drawLeftInBox(uiFont, uppercase(gameText.translate(
                "log.registro_de_la_timba")),
                panelX + 34f, panelY + panelH - 72f,
                panelW - 68f, 42f, Color.WHITE, alpha);
        drawFittedCenteredInBox(actionFont, uppercase(gameText.translate(
                "ui.cerrar")),
                panelX + panelW - 224f, panelY + 24f,
                194f, 58f, Color.WHITE, alpha);
        batch.end();

        int screenX = Math.round(viewport.getScreenX()
                + content.x * viewport.getScreenWidth()
                / viewport.getWorldWidth());
        int screenY = Math.round(viewport.getScreenY()
                + content.y * viewport.getScreenHeight()
                / viewport.getWorldHeight());
        int screenWidth = Math.max(1, Math.round(content.width
                * viewport.getScreenWidth() / viewport.getWorldWidth()));
        int screenHeight = Math.max(1, Math.round(content.height
                * viewport.getScreenHeight() / viewport.getWorldHeight()));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(screenX, screenY, screenWidth, screenHeight);
        batch.begin();
        for (int line = 0; line < logLines.size(); line++) {
            float rowY = anchoredPixelRowY(logLines.size(), line,
                    GAME_LOG_LINE_HEIGHT, content.y, content.height,
                    gameLogScroll, maximumScroll);
            if (rowY + GAME_LOG_LINE_HEIGHT < content.y
                    || rowY > content.y + content.height) continue;
            String value = logLines.get(line);
            GdxGameLogFormatter.Marker marker = GdxGameLogFormatter.marker(value);
            float runX = logX + (marker == GdxGameLogFormatter.Marker.NONE
                    ? 24f : 58f);
            float runY = rowY + 23f;
            drawGameLogMarker(marker, logX + 22f, runY - 20f, alpha);
            float contentRight = logX + logW - 34f;
            for (GdxGameLogFormatter.Run run : cachedGameLogRuns(value)) {
                float remainingWidth = contentRight - runX;
                if (remainingWidth <= 0f) break;
                Color color = run.color();
                gameLogFont.setColor(color.r, color.g, color.b, alpha);
                String fitted = ellipsizeToWidth(gameLogFont, run.text(),
                        remainingWidth);
                if (fitted.isEmpty()) break;
                gameLogFont.draw(batch, fitted, runX, runY);
                glyph.setText(gameLogFont, fitted);
                runX += glyph.width;
                if (!fitted.equals(run.text())) break;
            }
        }
        gameLogFont.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawGameLogMarker(GdxGameLogFormatter.Marker marker,
            float x, float y,
            float alpha) {
        Texture icon = switch (marker) {
            case DEALER -> dealerChip;
            case SMALL_BLIND -> smallBlindChip;
            case BIG_BLIND -> bigBlindChip;
            case MONEY -> logMoneyIcon;
            case STRADDLE -> logStraddleIcon;
            case DEALER_STRADDLE -> logDealerStraddleIcon;
            default -> null;
        };
        if (icon == null) return;
        float maximum = 24f;
        float scale = Math.min(maximum / icon.getWidth(),
                maximum / icon.getHeight());
        float iconW = Math.max(1f, icon.getWidth() * scale);
        float iconH = Math.max(1f, icon.getHeight() * scale);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(icon, x + (maximum - iconW) / 2f,
                y + (maximum - iconH) / 2f, iconW, iconH);
        batch.setColor(Color.WHITE);
    }

    private void drawChatDialog() {
        if (!chatImageMode) {
            drawQuickChatDialog();
            return;
        }
        float alpha = uiFade();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelW = Math.min(1140f, width - 80f);
        float panelH = Math.min(720f, height - 70f);
        float panelX = (width - panelW) / 2f;
        float panelY = (height - panelH) / 2f;
        float inputY = panelY + 34f;
        float sendX = panelX + panelW - 214f;
        float emojiX = sendX - 150f;
        float historyX = panelX + 34f;
        float historyY = panelY + 118f;
        float historyW = panelW - 68f;
        float historyH = panelH - 220f;
        if (emojiPickerOpen && !chatImageMode) ensureEmojiPageTextures();

        int visibleImages = Math.min(8, tableImageHistory.size());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.28f * alpha);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(0f, 0f, 0f, 0.58f * alpha);
        roundedRect(panelX + 9f, panelY - 10f, panelW, panelH, 20f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.82f * alpha);
        roundedRect(panelX - 2f, panelY - 2f, panelW + 4f, panelH + 4f, 20f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.985f * alpha);
        roundedRect(panelX, panelY, panelW, panelH, 18f);
        shapes.setColor(0.002f, 0.012f, 0.019f, 0.92f * alpha);
        roundedRect(historyX, historyY, historyW, historyH, 12f);
        for (int index = 0; index < visibleImages; index++) {
            Rectangle cell = tableGalleryCellBounds(index, historyX,
                    historyY, historyW, historyH);
            boolean over = cell.contains(pointer);
            shapes.setColor(over ? CYAN : BUTTON_LINE);
            roundedRect(cell.x, cell.y, cell.width, cell.height, 9f);
            shapes.setColor(0.010f, 0.024f, 0.041f, 0.98f * alpha);
            roundedRect(cell.x + 2f, cell.y + 2f,
                    cell.width - 4f, cell.height - 4f, 8f);
        }
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b,
                0.92f * alpha);
        roundedRect(panelX + 34f, inputY, panelW - 416f, 60f, 10f);
        shapes.setColor(0.018f, 0.035f, 0.060f, 0.98f * alpha);
        roundedRect(panelX + 37f, inputY + 3f,
                panelW - 422f, 54f, 8f);
        TableInputWindow visibleDraft = tableInputWindow(smallFont, chatDraft,
                panelW - 456f);
        drawTableInputSelection(panelX + 54f, inputY + 14f, 32f,
                visibleDraft, alpha);
        drawTableInputCaret(panelX + 54f + visibleDraft.caretOffset(),
                inputY + 14f, 32f, alpha);
        drawDialogButton(emojiX, inputY, 132f, 60f, CYAN,
                contains(pointer.x, pointer.y, emojiX, inputY, 132f, 60f), alpha);
        drawDialogButton(sendX, inputY, 180f, 60f,
                chatSending ? BUTTON_LINE : STACK_GREEN,
                !chatSending && contains(pointer.x, pointer.y,
                        sendX, inputY, 180f, 60f), alpha);
        drawDialogButton(panelX + panelW - 216f,
                panelY + panelH - 62f, 136f, 34f, BUTTON_LINE,
                contains(pointer.x, pointer.y, panelX + panelW - 216f,
                        panelY + panelH - 62f, 136f, 34f), alpha);
        shapes.setColor(FOLD_RED.r, FOLD_RED.g, FOLD_RED.b,
                contains(pointer.x, pointer.y, panelX + panelW - 62f,
                        panelY + panelH - 62f, 34f, 34f)
                        ? 0.90f * alpha : 0.52f * alpha);
        roundedRect(panelX + panelW - 62f, panelY + panelH - 62f,
                34f, 34f, 7f);
        if (emojiPickerOpen && !chatImageMode) {
            drawEmojiPickerShapes(panelX, panelY, panelW, alpha);
        }
        shapes.end();

        batch.begin();
        drawLeftInBox(uiFont, uppercase(gameText.translate(
                "gdx.lobby.image_gallery")), panelX + 34f,
                panelY + panelH - 72f, panelW - 320f, 42f,
                Color.WHITE, alpha);
        drawLeftInBox(smallFont, uppercase(gameText.translate(
                "gdx.lobby.image_gallery_help")),
                panelX + 35f, panelY + panelH - 103f,
                panelW - 280f, 24f, CYAN, alpha);
        drawFittedCenteredInBox(actionFont, "×",
                panelX + panelW - 62f, panelY + panelH - 62f,
                34f, 34f, Color.WHITE, alpha);
        drawFittedCenteredInBox(smallFont, uppercase(gameText.translate(
                "gdx.lobby.clear")),
                panelX + panelW - 216f, panelY + panelH - 62f,
                136f, 34f, Color.WHITE, alpha);
        if (tableImageHistory.isEmpty()) {
            drawFittedCenteredInBox(smallFont, uppercase(gameText.translate(
                    "gdx.lobby.image_gallery_empty")),
                    historyX + 20f, historyY + historyH / 2f - 18f,
                    historyW - 40f, 36f, Color.GRAY, alpha);
        } else {
            for (int index = 0; index < visibleImages; index++) {
                String url = tableImageHistory.get(index);
                Rectangle cell = tableGalleryCellBounds(index, historyX,
                        historyY, historyW, historyH);
                GdxChatGalleryMedia.Entry media = tableGalleryMedia.get(url);
                Texture thumbnail = media == null ? null
                        : media.frameAt(totalTime);
                if (thumbnail != null) {
                    float availableW = cell.width - 14f;
                    float availableH = cell.height - 14f;
                    float scale = Math.min(availableW / thumbnail.getWidth(),
                            availableH / thumbnail.getHeight());
                    float imageW = Math.max(1f,
                            thumbnail.getWidth() * scale);
                    float imageH = Math.max(1f,
                            thumbnail.getHeight() * scale);
                    batch.setColor(1f, 1f, 1f, alpha);
                    batch.draw(thumbnail,
                            cell.x + (cell.width - imageW) / 2f,
                            cell.y + (cell.height - imageH) / 2f,
                            imageW, imageH);
                } else {
                    drawFittedCenteredInBox(smallFont,
                            media != null && media.failed()
                                    ? uppercase(gameText.translate(
                                            "gdx.lobby.media_unavailable"))
                                    : uppercase(gameText.translate(
                                            "gdx.lobby.media_loading")),
                            cell.x + 10f, cell.y + cell.height / 2f - 12f,
                            cell.width - 20f, 24f,
                            media != null && media.failed()
                                    ? FOLD_RED : Color.GRAY, alpha);
                }
            }
        }
        String draft = chatDraft.isEmpty()
                ? gameText.translate(chatImageMode
                        ? "gdx.lobby.image_url_placeholder"
                        : "gdx.lobby.message_placeholder")
                : visibleDraft.text();
        drawLeftInBox(smallFont, draft, panelX + 54f, inputY + 10f,
                panelW - 456f, 40f,
                chatDraft.isEmpty() ? Color.GRAY : Color.WHITE, alpha);
        drawFittedCenteredInBox(actionFont,
                uppercase(gameText.translate(chatImageMode
                        ? "gdx.table.chat.text" : "gdx.lobby.emoji")),
                emojiX, inputY,
                132f, 60f, POT_GOLD, alpha);
        drawFittedCenteredInBox(actionFont,
                uppercase(gameText.translate(chatSending
                        ? "gdx.table.chat.sending"
                        : "gdx.table.chat.send_url")), sendX, inputY,
                180f, 60f, Color.WHITE, alpha);
        if (!chatError.isBlank()) {
            drawLeftInBox(smallFont, chatError, panelX + 38f,
                    panelY + 8f, panelW - 76f, 22f, FOLD_RED, alpha);
        }
        if (emojiPickerOpen && !chatImageMode) {
            drawEmojiPickerTextures(panelX, panelY, panelW, alpha);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawQuickChatDialog() {
        float alpha = uiFade();
        float panelX = 18f;
        float panelY = 18f;
        float panelW = quickChatWidth();
        float panelH = quickChatHeight();
        float historyX = panelX + 14f;
        float historyY = panelY + 100f;
        float historyW = panelW - 28f;
        float historyH = panelH - 114f;
        float inputY = panelY + 14f;
        List<LobbyChatMessage> messages = visibleTableChatMessages();
        float historyViewportH = Math.max(0f, historyH - 16f);
        quickChatScrollMaximum = quickChatMaximumPixelScroll(messages.size(),
                historyViewportH);
        if (messages.size() > quickChatMessageCount && quickChatScroll > 0f) {
            quickChatScroll += (messages.size() - quickChatMessageCount) * 30f;
        }
        quickChatMessageCount = messages.size();
        quickChatScroll = MathUtils.clamp(quickChatScroll, 0f,
                quickChatScrollMaximum);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.42f * alpha);
        roundedRect(panelX + 5f, panelY - 5f, panelW, panelH, 10f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.72f * alpha);
        roundedRect(panelX - 1.5f, panelY - 1.5f,
                panelW + 3f, panelH + 3f, 10f);
        // Swing uses an 80% opaque undecorated window. Keep that causal
        // relationship: the table remains visible, but text stays readable.
        shapes.setColor(0.008f, 0.018f, 0.028f, 0.82f * alpha);
        roundedRect(panelX, panelY, panelW, panelH, 9f);
        shapes.setColor(0f, 0f, 0f, 0.36f * alpha);
        roundedRect(historyX, historyY, historyW, historyH, 6f);
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b,
                0.92f * alpha);
        roundedRect(panelX + 14f, inputY, panelW - 28f, 42f, 6f);
        shapes.setColor(0.012f, 0.025f, 0.038f, 0.95f * alpha);
        roundedRect(panelX + 17f, inputY + 3f,
                panelW - 34f, 36f, 4f);
        TableInputWindow visibleDraft = tableInputWindow(smallFont, chatDraft,
                panelW - 54f);
        drawTableInputSelection(panelX + 27f, inputY + 8f, 27f,
                visibleDraft, alpha);
        drawTableInputCaret(panelX + 27f + visibleDraft.caretOffset(),
                inputY + 8f, 27f, alpha);
        float switchX = panelX + panelW - 58f;
        float switchY = panelY + 65f;
        shapes.setColor(quickChatAutoClose ? STACK_GREEN : BUTTON_LINE);
        roundedRect(switchX, switchY, 38f, 20f, 10f);
        shapes.setColor(Color.WHITE);
        shapes.circle(quickChatAutoClose ? switchX + 28f : switchX + 10f,
                switchY + 10f, 7f, 24);
        if (quickChatScrollMaximum > 0f) {
            float trackY = historyY + 8f;
            float trackH = historyH - 16f;
            float thumbH = quickChatScrollThumbHeight(trackH);
            float thumbTravel = Math.max(1f, trackH - thumbH);
            float thumbY = trackY + thumbTravel * quickChatScroll
                    / quickChatScrollMaximum;
            shapes.setColor(0.08f, 0.14f, 0.24f, 0.96f * alpha);
            roundedRect(historyX + historyW - 20f, trackY,
                    14f, trackH, 7f);
            shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.96f * alpha);
            roundedRect(historyX + historyW - 20f, thumbY,
                    14f, thumbH, 7f);
        }
        shapes.end();

        int screenX = Math.round(viewport.getScreenX()
                + (historyX + 8f) * viewport.getScreenWidth()
                / viewport.getWorldWidth());
        int screenY = Math.round(viewport.getScreenY()
                + (historyY + 8f) * viewport.getScreenHeight()
                / viewport.getWorldHeight());
        int screenWidth = Math.max(1, Math.round((historyW - 36f)
                * viewport.getScreenWidth() / viewport.getWorldWidth()));
        int screenHeight = Math.max(1, Math.round(historyViewportH
                * viewport.getScreenHeight() / viewport.getWorldHeight()));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(screenX, screenY, screenWidth, screenHeight);
        batch.begin();
        if (messages.isEmpty()) {
            drawFittedCenteredInBox(smallFont, uppercase(gameText.translate(
                    "gdx.table.chat.no_messages")),
                    historyX + 12f, historyY + historyH / 2f - 14f,
                    historyW - 24f, 28f, Color.GRAY, alpha);
        } else {
            for (int line = 0; line < messages.size(); line++) {
                float rowY = anchoredPixelRowY(messages.size(), line, 30f,
                        historyY + 8f, historyViewportH, quickChatScroll,
                        quickChatScrollMaximum);
                if (rowY + 30f < historyY + 8f
                        || rowY > historyY + historyH - 8f) continue;
                LobbyChatMessage message = messages.get(line);
                String text = quickChatHistoryText(message, gameText);
                drawLeftInBox(smallFont, text, historyX + 12f,
                        rowY, historyW - 50f, 28f, Color.WHITE, alpha);
            }
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        batch.begin();
        drawLeftInBox(smallFont, uppercase(gameText.translate(
                "gdx.table.chat.close_on_send")),
                panelX + 18f, panelY + 58f,
                panelW - 92f, 34f, Color.LIGHT_GRAY, alpha);
        String draft = chatDraft.isEmpty()
                ? gameText.translate("gdx.table.chat.quick_placeholder")
                : visibleDraft.text();
        drawLeftInBox(smallFont, draft, panelX + 27f, inputY + 3f,
                panelW - 54f, 36f,
                chatDraft.isEmpty() ? Color.GRAY : Color.WHITE, alpha);
        if (!chatError.isBlank()) {
            drawLeftInBox(smallFont, chatError, panelX + 18f,
                    panelY + panelH - 30f, panelW - 36f, 24f,
                    FOLD_RED, alpha);
        }
        batch.end();
    }

    static String quickChatHistoryText(LobbyChatMessage message,
            GdxGameText gameText) {
        String content = switch (message.type()) {
            case IMAGE -> gameText.translate("gdx.table.chat.image_label");
            case VOICE -> gameText.translate("gdx.table.chat.voice_label");
            default -> message.content();
        };
        return message.nickname() + ": " + content;
    }

    static float seatChatNoticeDuration(LobbyChatMessage.Type type,
            String content) {
        if (type == LobbyChatMessage.Type.IMAGE) return 9f;
        if (type == LobbyChatMessage.Type.VOICE) return 60f;
        String spoken = GdxTextToSpeechPlayback.cleanChatMessage(content);
        int length = spoken.codePointCount(0, spoken.length());
        return Math.max(3f, (float) Math.ceil(length / 25d));
    }

    /**
     * Last-resort visual timeout when an OpenAL completion callback is lost.
     * It is not the normal icon duration: successful TTS/voice playback still
     * removes the indicator 500 ms after the real audio completion.  Voice
     * notes are contractually capped at 15 seconds; text gets a conservative
     * allowance derived from the same length estimate used by Swing.
     */
    static float seatChatPlaybackWatchdog(LobbyChatMessage.Type type,
            String content) {
        if (type == LobbyChatMessage.Type.VOICE) {
            return VoiceWavContract.MAX_SECONDS + 1f;
        }
        if (type == LobbyChatMessage.Type.TEXT) {
            return MathUtils.clamp(seatChatNoticeDuration(type, content) * 2.5f,
                    4f, 18f);
        }
        return seatChatNoticeDuration(type, content);
    }

    /** Mirrors the Swing TTS watchdog rule for voice-note seat notices. */
    static boolean shouldShowVoiceSeatNotice(boolean soundEnabled,
            boolean voiceMessagesBlocked, boolean ownMessage,
            boolean playOwnVoiceMessages) {
        return soundEnabled && !voiceMessagesBlocked
                && (!ownMessage || playOwnVoiceMessages);
    }

    static boolean shouldShowSeatNotice(LobbyChatMessage.Type type,
            boolean notificationsEnabled, boolean chatImagesInGame,
            boolean voiceNoticeEnabled, boolean senderMediaBlocked,
            boolean ownMessage) {
        if (type == null) return false;
        return switch (type) {
            case TEXT -> notificationsEnabled;
            // ChatImageDialog enqueues the sender's own image directly while
            // playing, outside CHAT_GAME_NOTIFICATIONS. It is the local send
            // confirmation and therefore must survive that remote-notice gate.
            case IMAGE -> (notificationsEnabled || ownMessage)
                    && chatImagesInGame && !senderMediaBlocked;
            case VOICE -> notificationsEnabled && voiceNoticeEnabled
                    && !senderMediaBlocked;
            case PLAYER_JOINED, PLAYER_LEFT -> false;
        };
    }

    static boolean shouldDisplaySeatNotice(LobbyChatMessage.Type type,
            boolean eligibleNotice, boolean spokenText) {
        // Swing prepares talk.png for every text notification but only makes
        // it visible from Audio.TTS once playback really starts. A muted,
        // disabled or blocked TTS message uses Swing's separate mute/blocked
        // notice without impersonating active speech on its seat. Voice notes
        // have their own playback gate in shouldShowVoiceSeatNotice.
        return eligibleNotice
                && (type != LobbyChatMessage.Type.TEXT || spokenText);
    }

    static boolean shouldShowSilentTextNotice(LobbyChatMessage.Type type,
            boolean eligibleNotice, boolean spokenText) {
        return type == LobbyChatMessage.Type.TEXT && eligibleNotice
                && !spokenText;
    }

    /** Same gates evaluated by Swing's in-game TTS watchdog. */
    static boolean shouldSpeakTableChat(LobbyChatMessage.Type type,
            boolean notificationsEnabled, boolean soundEnabled,
            boolean textToSpeechEnabled, boolean textToSpeechBlocked,
            boolean senderBlocked) {
        return type == LobbyChatMessage.Type.TEXT && notificationsEnabled
                && soundEnabled && textToSpeechEnabled
                && !textToSpeechBlocked && !senderBlocked;
    }

    private void drawTableInputCaret(float x, float y, float caretHeight,
            float alpha) {
        if (((int) (totalTime / 0.50f) & 1) != 0) return;
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, alpha);
        shapes.rect(x + 1f, y, 2f, caretHeight);
    }

    private void drawTableInputSelection(float x, float y, float height,
            TableInputWindow window, float alpha) {
        if (window.selectionWidth() <= 0f) return;
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.34f * alpha);
        shapes.rect(x + window.selectionOffset(), y,
                window.selectionWidth(), height);
    }

    private TableInputWindow tableInputWindow(BitmapFont font, String value,
            float maxWidth) {
        chatEdit.focus("tableChat", value);
        int caret = chatEdit.caret(value);
        int start = 0;
        while (start < caret
                && tableTextWidth(font, value.substring(start, caret)) > maxWidth) {
            start = value.offsetByCodePoints(start, 1);
        }
        int end = caret;
        while (end < value.length()) {
            int next = value.offsetByCodePoints(end, 1);
            if (tableTextWidth(font, value.substring(start, next)) > maxWidth) break;
            end = next;
        }
        int selectionStart = Math.max(start,
                Math.min(end, chatEdit.selectionStart(value)));
        int selectionEnd = Math.max(start,
                Math.min(end, chatEdit.selectionEnd(value)));
        float selectionOffset = tableTextWidth(font,
                value.substring(start, selectionStart));
        float selectionWidth = tableTextWidth(font,
                value.substring(selectionStart, selectionEnd));
        return new TableInputWindow(value.substring(start, end), start, end,
                tableTextWidth(font, value.substring(start, caret)),
                selectionOffset, selectionWidth);
    }

    private void placeTableChatCaret(float pointerX, boolean extend) {
        Rectangle input = tableChatInputBounds();
        float padding = chatImageMode ? 20f : 13f;
        float maxWidth = input.width - padding * 2f;
        TableInputWindow window = tableInputWindow(smallFont, chatDraft,
                maxWidth);
        int start = window.sourceStart();
        int target = GdxTextEditState.nearestBoundary(chatDraft, start,
                window.sourceEnd(), pointerX - input.x - padding,
                index -> chatDraft.offsetByCodePoints(index, 1),
                index -> tableTextWidth(smallFont,
                        chatDraft.substring(start, index)));
        chatEdit.setCaret(chatDraft, target, extend);
    }

    private float tableTextWidth(BitmapFont font, String value) {
        glyph.setText(font, value);
        return glyph.width;
    }

    private List<LobbyChatMessage> visibleTableChatMessages() {
        if (tableChat == null) return List.of();
        return tableChat.history().stream().filter(message ->
                message.type() == LobbyChatMessage.Type.TEXT
                || message.type() == LobbyChatMessage.Type.IMAGE
                || message.type() == LobbyChatMessage.Type.VOICE).toList();
    }

    private void preloadVisibleChatEmojiTextures(
            List<LobbyChatMessage> messages, int first) {
        for (int index = first; index < messages.size(); index++) {
            LobbyChatMessage message = messages.get(index);
            if (message.type() != LobbyChatMessage.Type.TEXT) continue;
            Matcher matcher = EMOJI_TOKEN.matcher(message.content());
            while (matcher.find()) {
                try {
                    int id = Integer.parseInt(matcher.group(1));
                    if (id >= 1 && id <= EMOJI_COUNT) emojiTexture(id);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void drawInlineChatMessage(LobbyChatMessage message, float x,
            float baseline, float maxWidth, float alpha) {
        String prefix = message.nickname() + ": ";
        float cursor = drawInlineText(prefix, x, baseline, maxWidth,
                POT_GOLD, alpha);
        float limit = x + maxWidth;
        if (message.type() == LobbyChatMessage.Type.IMAGE) {
            drawInlineText(gameText.translate("gdx.table.chat.image_gif_label"),
                    cursor, baseline,
                    limit - cursor, CYAN, alpha);
            return;
        }
        if (message.type() == LobbyChatMessage.Type.VOICE) {
            drawInlineText(gameText.translate("gdx.table.chat.voice_label"),
                    cursor, baseline,
                    limit - cursor, CYAN, alpha);
            return;
        }
        Matcher matcher = EMOJI_TOKEN.matcher(message.content());
        int start = 0;
        while (matcher.find() && cursor < limit - 10f) {
            cursor = drawInlineText(message.content().substring(start, matcher.start()),
                    cursor, baseline, limit - cursor,
                    Color.LIGHT_GRAY, alpha);
            int id;
            try {
                id = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                id = 0;
            }
            Texture emoji = id >= 1 && id <= EMOJI_COUNT
                    ? emojiTexture(id) : null;
            if (emoji != null && cursor + 27f <= limit) {
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(emoji, cursor + 2f, baseline - 22f, 25f, 25f);
                cursor += 30f;
            }
            start = matcher.end();
        }
        if (start < message.content().length() && cursor < limit) {
            drawInlineText(message.content().substring(start), cursor,
                    baseline, limit - cursor, Color.LIGHT_GRAY, alpha);
        }
    }

    private float drawInlineText(String text, float x, float baseline,
            float available, Color color, float alpha) {
        if (text.isEmpty() || available <= 3f) return x;
        String fitted = fitChatText(text, available);
        smallFont.setColor(color.r, color.g, color.b, alpha);
        smallFont.draw(batch, fitted, x, baseline);
        glyph.setText(smallFont, fitted);
        smallFont.setColor(Color.WHITE);
        return x + glyph.width;
    }

    private String fitChatText(String text, float available) {
        glyph.setText(smallFont, text);
        if (glyph.width <= available) return text;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            glyph.setText(smallFont, text.substring(0, middle) + "…");
            if (glyph.width <= available) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, low) + "…";
    }

    private void drawEmojiPickerShapes(float panelX, float panelY,
            float panelW, float alpha) {
        float pickerX = panelX + panelW - 510f;
        float pickerY = panelY + 124f;
        float pickerW = 492f;
        float pickerH = 380f;
        shapes.setColor(0f, 0f, 0f, 0.64f * alpha);
        roundedRect(pickerX + 7f, pickerY - 7f, pickerW, pickerH, 14f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.78f * alpha);
        roundedRect(pickerX - 2f, pickerY - 2f, pickerW + 4f, pickerH + 4f, 14f);
        shapes.setColor(0.010f, 0.025f, 0.043f, 0.99f * alpha);
        roundedRect(pickerX, pickerY, pickerW, pickerH, 12f);
        float cell = 52f;
        float gridY = pickerY + 70f;
        for (int row = 0; row < EMOJI_ROWS; row++) {
            for (int column = 0; column < EMOJI_COLUMNS; column++) {
                int id = emojiPage * EMOJI_PAGE_SIZE
                        + row * EMOJI_COLUMNS + column + 1;
                if (id > EMOJI_COUNT) continue;
                float cellX = pickerX + 18f + column * (cell + 6f);
                float cellY = gridY + (EMOJI_ROWS - 1 - row) * (cell + 5f);
                boolean hover = contains(pointer.x, pointer.y,
                        cellX, cellY, cell, cell);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                        (hover ? 0.30f : 0.08f) * alpha);
                roundedRect(cellX, cellY, cell, cell, 7f);
            }
        }
        drawDialogButton(pickerX + 18f, pickerY + 14f, 84f, 38f,
                CYAN, contains(pointer.x, pointer.y,
                        pickerX + 18f, pickerY + 14f, 84f, 38f), alpha);
        drawDialogButton(pickerX + 398f, pickerY + 14f, 84f, 38f,
                CYAN, contains(pointer.x, pointer.y,
                        pickerX + 398f, pickerY + 14f, 84f, 38f), alpha);
    }

    private void drawEmojiPickerTextures(float panelX, float panelY,
            float panelW, float alpha) {
        float pickerX = panelX + panelW - 510f;
        float pickerY = panelY + 124f;
        float cell = 52f;
        float gridY = pickerY + 70f;
        for (int row = 0; row < EMOJI_ROWS; row++) {
            for (int column = 0; column < EMOJI_COLUMNS; column++) {
                int id = emojiPage * EMOJI_PAGE_SIZE
                        + row * EMOJI_COLUMNS + column + 1;
                if (id > EMOJI_COUNT) continue;
                Texture emoji = emojiTextures.get(id);
                if (emoji == null) continue;
                float cellX = pickerX + 18f + column * (cell + 6f);
                float cellY = gridY + (EMOJI_ROWS - 1 - row) * (cell + 5f);
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(emoji, cellX + 7f, cellY + 7f, 38f, 38f);
            }
        }
        int pages = (EMOJI_COUNT + EMOJI_PAGE_SIZE - 1) / EMOJI_PAGE_SIZE;
        drawFittedCenteredInBox(smallFont, "‹", pickerX + 18f,
                pickerY + 14f, 84f, 38f, Color.WHITE, alpha);
        drawFittedCenteredInBox(smallFont,
                (emojiPage + 1) + " / " + pages,
                pickerX + 120f, pickerY + 14f, 260f, 38f,
                POT_GOLD, alpha);
        drawFittedCenteredInBox(smallFont, "›", pickerX + 398f,
                pickerY + 14f, 84f, 38f, Color.WHITE, alpha);
    }

    private void ensureEmojiPageTextures() {
        int first = emojiPage * EMOJI_PAGE_SIZE + 1;
        int last = Math.min(EMOJI_COUNT, first + EMOJI_PAGE_SIZE - 1);
        for (int id = first; id <= last; id++) emojiTexture(id);
    }

    private Texture emojiTexture(int id) {
        if (id < 1 || id > EMOJI_COUNT) return null;
        Texture cached = emojiTextures.get(id);
        if (cached != null) return cached;
        try {
            Texture loaded = texture("images/emoji_chat/" + id + ".png");
            emojiTextures.put(id, loaded);
            return loaded;
        } catch (RuntimeException missing) {
            return null;
        }
    }

    private void drawDialogButton(float x, float y, float width, float height,
            Color accent, boolean hover, float alpha) {
        shapes.setColor(0f, 0f, 0f, 0.46f * alpha);
        roundedRect(x + 4f, y - 4f, width, height, 11f);
        shapes.setColor(accent.r, accent.g, accent.b,
                (hover ? 0.92f : 0.62f) * alpha);
        roundedRect(x, y, width, height, 11f);
        shapes.setColor(0.018f, 0.035f, 0.060f, 0.98f * alpha);
        roundedRect(x + 3f, y + 4f, width - 6f, height - 8f, 8f);
        shapes.setColor(accent.r, accent.g, accent.b,
                (hover ? 0.34f : 0.18f) * alpha);
        roundedRect(x + 7f, y + 8f, width - 14f, height - 16f, 7f);
    }

    private void handleFinalSummaryInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)
                || (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                && Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            toggleFullscreen();
        }
        if (uiLayer == UI_GAME_LOG) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                uiLayer = UI_NONE;
                return;
            }
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                pointer.set(Gdx.input.getX(), Gdx.input.getY());
                viewport.unproject(pointer);
                handleGameLogClick(pointer.x, pointer.y);
            }
            return;
        }
        if (finalExitPending) return;
        // Pointer presses are dispatched synchronously by tableInput so the
        // final surface consumes them before any control underneath.
    }

    private void handleFinalSummaryTarget(int target) {
        if (finalExitPending || finalSummary == null
                || uiLayer == UI_GAME_LOG) return;
        if (handleFinalSummaryNavigationAction(target)) return;
        float width = viewport.getWorldWidth();
        int visible = finalSummaryVisibleCards(width);
        int maximum = Math.max(0, finalSummary.balances().size() - visible);
        if (target == 5 && finalSummaryPage > 0) {
            finalSummaryPage = Math.max(0, finalSummaryPage - visible);
        } else if (target == 6 && finalSummaryPage < maximum) {
            finalSummaryPage = Math.min(maximum,
                    finalSummaryPage + visible);
        }
    }

    boolean handleFinalSummaryNavigationAction(int action) {
        if (finalExitPending || finalSummary == null
                || uiLayer == UI_GAME_LOG) return false;
        if (action == 0) {
            finalExitPending = true;
            CompletableFuture<Void> barrier = finalSummaryBarrier;
            if (barrier != null && !barrier.isDone()) barrier.complete(null);
            return true;
        }
        if (action == 1) {
            gameLogScroll = 0;
            openUiLayer(UI_GAME_LOG);
            return true;
        }
        if (action == 2) {
            finalStatsRequested = true;
            finalExitPending = true;
            CompletableFuture<Void> barrier = finalSummaryBarrier;
            if (barrier != null && !barrier.isDone()) barrier.complete(null);
            return true;
        }
        if (action == 3) {
            finalContinueRequested = true;
            finalExitPending = true;
            CompletableFuture<Void> barrier = finalSummaryBarrier;
            if (barrier != null && !barrier.isDone()) barrier.complete(null);
            return true;
        }
        if (action == 4) {
            toggleMasterSound();
            return true;
        }
        return false;
    }

    static int finalSummaryActionAt(float width, float height,
            float x, float y) {
        float navGap = 18f;
        float navWidth = Math.min(350f,
                (width - 160f - navGap * 3f) / 4f);
        float navStart = (width - (navWidth * 4f + navGap * 3f)) / 2f;
        float navY = height - 82f;
        for (int index : new int[]{0, 1, 2, 3}) {
            if (contains(x, y, navStart + index * (navWidth + navGap),
                    navY, navWidth, 54f)) return index;
        }
        return contains(x, y, width - 74f, height - 78f, 46f, 46f)
                ? 4 : -1;
    }

    static int finalSummaryPointerTargetAt(float width, float height,
            float x, float y, int page, int balanceCount) {
        int action = finalSummaryActionAt(width, height, x, y);
        if (action >= 0) return action;
        int visible = finalSummaryVisibleCards(width);
        int maximum = Math.max(0, balanceCount - visible);
        if (page > 0 && contains(x, y, 12f, 48f, 58f, 226f)) return 5;
        if (page < maximum
                && contains(x, y, width - 70f, 48f, 58f, 226f)) return 6;
        return -1;
    }

    private void drawFinalSummary() {
        TableSessionSummary summary = finalSummary;
        if (summary == null) return;
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float centerX = width / 2f;
        float elapsed = Math.max(0f, totalTime - finalSummaryOpenedAt);
        float reveal = Interpolation.fade.apply(
                MathUtils.clamp(elapsed / 0.34f, 0f, 1f));
        float cardsReveal = Interpolation.swingOut.apply(
                MathUtils.clamp((elapsed - 0.12f) / 0.46f, 0f, 1f));
        TableSessionSummary.PlayerBalance local = summary.localBalance();
        double net = local == null ? 0d : local.netResult();
        Color resultColor = finalSummaryResultColor(net);

        int capacity = finalSummaryVisibleCards(width);
        int maximum = Math.max(0, summary.balances().size() - capacity);
        finalSummaryPage = MathUtils.clamp(finalSummaryPage, 0, maximum);
        int shown = Math.min(capacity,
                summary.balances().size() - finalSummaryPage);
        float gap = 12f;
        float cardsAreaW = width - 154f;
        float cardW = Math.min(218f,
                (cardsAreaW - gap * Math.max(0, capacity - 1)) / capacity);
        float rowW = shown * cardW + Math.max(0, shown - 1) * gap;
        float firstX = centerX - rowW / 2f;
        float cardY = 28f - (1f - cardsReveal) * 22f;
        float cardH = 258f;

        // BalanceScreen is transparent over the selected table surface. Repaint
        // that same felt here to remove the inert board without introducing a
        // different background or a modal-looking opaque sheet.
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(feltTexture, 0f, 0f, width, height,
                0f, 0f, width / feltTexture.getWidth(),
                height / feltTexture.getHeight());
        batch.end();

        float navGap = 18f;
        float navWidth = Math.min(350f,
                (width - 160f - navGap * 3f) / 4f);
        float navStart = (width - (navWidth * 4f + navGap * 3f)) / 2f;
        float navY = height - 82f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        // Swing's BalanceScreen is transparent over the selected table felt.
        // Do not apply a second green/black veil here: it made every custom
        // tapete visibly darker when the final summary opened.
        for (int index = 0; index < 4; index++) {
            float x = navStart + index * (navWidth + navGap);
            boolean enabled = index != 2;
            drawFinalNavSurface(x, navY, navWidth, 54f, enabled,
                    enabled && contains(pointer.x, pointer.y,
                            x, navY, navWidth, 54f), reveal);
        }
        shapes.setColor(0.004f, 0.018f, 0.028f, 0.78f * reveal);
        roundedRect(width - 74f, height - 78f, 46f, 46f, 11f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.42f * reveal);
        roundedRect(width - 72f, height - 76f, 42f, 42f, 9f);
        shapes.setColor(0.004f, 0.018f, 0.028f, 0.92f * reveal);
        roundedRect(width - 70f, height - 74f, 38f, 38f, 8f);

        for (int offset = 0; offset < shown; offset++) {
            int index = finalSummaryPage + offset;
            if (index >= summary.balances().size()) break;
            TableSessionSummary.PlayerBalance balance
                    = summary.balances().get(index);
            float x = firstX + offset * (cardW + gap);
            boolean localCard = balance.nickname().equals(
                    summary.localNickname());
            Color accent = localCard ? CYAN : Color.WHITE;
            shapes.setColor(0f, 0f, 0f, 0.38f * cardsReveal);
            roundedRect(x + 5f, cardY - 6f, cardW, cardH, 15f);
            shapes.setColor(accent.r, accent.g, accent.b,
                    (localCard ? 0.92f : 0.70f) * cardsReveal);
            roundedRect(x - 2f, cardY - 2f, cardW + 4f,
                    cardH + 4f, 15f);
            shapes.setColor(0.985f, 0.985f, 0.975f, cardsReveal);
            roundedRect(x, cardY, cardW, cardH, 13f);
            Color balanceColor = balance.netResult() > 0d ? FINAL_WINNER
                    : balance.netResult() < 0d ? FINAL_LOSER
                            : new Color(0x707070ff);
            shapes.setColor(balanceColor.r, balanceColor.g,
                    balanceColor.b, 0.10f * cardsReveal);
            roundedRect(x + 10f, cardY + 74f,
                    cardW - 20f, 42f, 7f);
        }
        if (finalSummaryPage > 0) {
            shapes.setColor(0f, 0f, 0f, 0.58f * cardsReveal);
            shapes.circle(41f, 158f, 29f, 40);
        }
        if (finalSummaryPage < maximum) {
            shapes.setColor(0f, 0f, 0f, 0.58f * cardsReveal);
            shapes.circle(width - 41f, 158f, 29f, 40);
        }
        shapes.end();

        batch.begin();
        Texture[] navIcons = {finalMenuIcon, finalLogIcon,
            finalStatsIcon, finalContinueIcon};
        String[] navLabels = {
            uppercase(gameText.translate("ui.menu_principal")),
            uppercase(gameText.translate("log.registro_de_la_timba")),
            uppercase(gameText.translate("stats.estadisticas")),
            uppercase(gameText.translate(tableHost
                    ? "game.continuar_esta_timba"
                    : "gdx.final.reconnect_server"))
        };
        for (int index = 0; index < 4; index++) {
            float x = navStart + index * (navWidth + navGap);
            boolean enabled = finalSummaryNavEnabled(index);
            boolean hover = enabled && contains(pointer.x, pointer.y,
                    x, navY, navWidth, 54f);
            float alpha = enabled ? reveal : 0.38f * reveal;
            Color navColor = GdxUiButtonStyle.labelColor(
                    GdxUiButtonStyle.Tone.NEUTRAL, enabled);
            batch.setColor(navColor.r, navColor.g, navColor.b, alpha);
            batch.draw(navIcons[index], x + 17f, navY + 12f, 30f, 30f);
            drawFittedCenteredInBox(finalButtonFont, navLabels[index],
                    x + 52f, navY + 6f, navWidth - 64f, 42f,
                    navColor, alpha);
        }
        Texture speaker = audioControl.enabled() ? soundIcon : muteIcon;
        batch.setColor(1f, 1f, 1f, reveal);
        batch.draw(speaker, width - 66f, height - 70f, 30f, 30f);

        drawFittedCentered(finalTitleFont,
                finalSummaryTitle(summary.reason(), gameText),
                centerX, height - 168f, width - 150f, Color.WHITE, reveal);
        String details = finalSummaryDate(summary.endedAtMillis())
                + "   (" + finalSummaryDuration(summary.durationSeconds())
                + ")   [" + summary.handCount() + " "
                + gameText.translate(summary.handCount() == 1
                        ? "gdx.final.hand" : "gdx.final.hands") + "]";
        drawFittedCentered(finalDetailFont, details, centerX,
                height - 222f, width - 180f, Color.WHITE, 0.94f * reveal);
        boolean moneyCounterVisible = local != null
                && summary.reason()
                        != TableSessionSummary.CloseReason.RECOVERABLE_STOP
                && summary.reason()
                        != TableSessionSummary.CloseReason.FAILURE
                && finalMoneyCounterVisible(net);
        drawFittedCentered(finalHeroFont,
                finalSummaryHero(summary.reason(), net, gameText), centerX,
                finalSummaryHeroY(height, moneyCounterVisible),
                width - 100f, resultColor, reveal);
        if (moneyCounterVisible) {
            boolean animated = finalCounterAnimationEnabled();
            if (!animated || finalAmountVisible(elapsed)) {
                double value = animated
                        ? finalAmountValue(elapsed, local.totalBuyin(),
                                local.finalStack())
                        : Math.abs(net);
                drawFittedCentered(finalAmountFont, formatAmount(value),
                        centerX, height - 475f, width - 130f,
                        resultColor, reveal);
            }
        }

        for (int offset = 0; offset < shown; offset++) {
            int index = finalSummaryPage + offset;
            if (index >= summary.balances().size()) break;
            TableSessionSummary.PlayerBalance balance
                    = summary.balances().get(index);
            float x = firstX + offset * (cardW + gap);
            boolean localCard = balance.nickname().equals(
                    summary.localNickname());
            if (localCard) {
                float logoW = Math.min(106f, cardW - 38f);
                float logoH = logoW * logo.getHeight() / logo.getWidth();
                batch.setColor(1f, 1f, 1f, cardsReveal);
                batch.draw(logo, x + (cardW - logoW) / 2f,
                        cardY + 157f + (90f - logoH) / 2f,
                        logoW, logoH);
            } else {
                // Keep the same identity projection used by the live seats.
                // The old fallback replaced every remote human's custom
                // avatar with the generic silhouette on the final screen.
                Texture avatar = tableAvatar(balance.nickname());
                batch.setColor(1f, 1f, 1f, cardsReveal);
                batch.setShader(avatarShader);
                batch.draw(avatar, x + cardW / 2f - 45f,
                        cardY + 157f, 90f, 90f);
                batch.setShader(null);
            }
            drawFittedCenteredInBox(finalCardBoldFont, balance.nickname(),
                    x + 10f, cardY + 127f, cardW - 20f, 28f,
                    new Color(0x11151bff), cardsReveal);
            Color cardResult = balance.netResult() > 0d ? FINAL_WINNER
                    : balance.netResult() < 0d ? FINAL_LOSER
                            : new Color(0x707070ff);
            String result = balance.netResult() > 0d
                    ? uppercase(gameText.translate("ui.gana_4")) + " "
                            + formatAmount(balance.netResult())
                    : balance.netResult() < 0d
                            ? uppercase(gameText.translate("ui.pierde_2"))
                                    + " "
                                    + formatAmount(-balance.netResult())
                            : uppercase(gameText.translate(
                                    "ui.ni_gana_ni_pierde"));
            drawFittedCenteredInBox(finalCardBoldFont, result,
                    x + 10f, cardY + 82f, cardW - 20f, 32f,
                    cardResult, cardsReveal);
            drawFittedCenteredInBox(finalCardFont,
                    uppercase(gameText.translate("balance.fichas")) + " "
                            + formatAmount(balance.finalStack()),
                    x + 10f, cardY + 46f, cardW - 20f, 26f,
                    new Color(0x2c3138ff), 0.92f * cardsReveal);
            drawFittedCenteredInBox(finalCardFont,
                    uppercase(gameText.translate("stats.buyin")) + " "
                            + formatAmount(balance.totalBuyin()),
                    x + 10f, cardY + 16f, cardW - 20f, 25f,
                    new Color(0x4e555eff), 0.90f * cardsReveal);
        }
        if (finalSummaryPage > 0) {
            drawCentered(uiFont, "<", 41f, 169f,
                    Color.WHITE, cardsReveal);
        }
        if (finalSummaryPage < maximum) {
            drawCentered(uiFont, ">", width - 41f, 169f,
                    Color.WHITE, cardsReveal);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        if (finalExitPending) drawFinalExitPending(width, height);

        if (!finalSummaryScreenshotTaken
                && tablePreference("screenshot_fin_timba", false)
                && finalSummaryScreenshotReady(elapsed, local, summary.reason(),
                        finalCounterAnimationEnabled())) {
            // Swing captures only after the rolling amount and its three
            // visible/blank cycles have ended.  Readback remains on the render
            // thread at the end of this frame; PNG encoding stays asynchronous.
            finalSummaryScreenshotTaken = true;
            screenshotRequested = true;
            if (tablePreference("sonido_screenshot", true)) {
                play(screenshotSound, 0.92f, 1f);
            }
        }
    }

    private void drawFinalExitPending(float width, float height) {
        float boxW = 520f;
        float boxH = 112f;
        float boxX = (width - boxW) / 2f;
        float boxY = (height - boxH) / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(0f, 0f, width, height, 0f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.92f);
        roundedRect(boxX - 2f, boxY - 2f, boxW + 4f, boxH + 4f, 15f);
        shapes.setColor(0.006f, 0.024f, 0.040f, 0.98f);
        roundedRect(boxX, boxY, boxW, boxH, 13f);
        for (int index = 0; index < 8; index++) {
            float phase = totalTime * 4.2f - index * MathUtils.PI2 / 8f;
            float alpha = 0.22f + 0.78f * ((index + 1f) / 8f);
            shapes.setColor(CYAN.r, CYAN.g, CYAN.b, alpha);
            shapes.circle(boxX + 56f + MathUtils.cos(phase) * 19f,
                    boxY + boxH / 2f + MathUtils.sin(phase) * 19f,
                    4.5f, 16);
        }
        shapes.end();
        batch.begin();
        drawFittedCenteredInBox(finalButtonFont,
                uppercase(gameText.translate("gdx.final.closing_table")),
                boxX + 98f, boxY + 24f, boxW - 122f, boxH - 48f,
                Color.WHITE, 1f);
        batch.end();
    }

    /** True only when the final-screen recovery action was selected. */
    boolean finalContinueRequested() {
        return finalContinueRequested;
    }

    boolean finalStatsRequested() {
        return finalStatsRequested;
    }

    boolean finalApplicationExitRequested() {
        return finalApplicationExitRequested;
    }

    static boolean recoveryStopSkipsFinalSummary(
            TableSessionSummary.CloseReason reason) {
        return reason == TableSessionSummary.CloseReason.RECOVERABLE_STOP;
    }

    private void drawFinalNavSurface(float x, float y, float width,
            float height, boolean enabled, boolean hover, float alpha) {
        GdxUiButtonStyle.draw(shapes, x, y, width, height,
                GdxUiButtonStyle.Tone.NEUTRAL, enabled, hover ? 1f : 0f,
                false, alpha);
    }

    private static int finalSummaryVisibleCards(float width) {
        return Math.max(1, Math.min(9, (int) ((width - 154f) / 178f)));
    }

    /** Exact numeric route used by Swing's BalanceScreen. */
    static double[] finalAmountAnimationRange(double buyin, double stack) {
        double cleanedBuyin = MoneyMath.clean(buyin);
        double cleanedStack = MoneyMath.clean(stack);
        double result = Math.abs(MoneyMath.clean(cleanedStack - cleanedBuyin));
        double distanceFromZero = result;
        double distanceFromStack = Math.abs(cleanedStack - result);
        double from = Double.compare(distanceFromStack,
                distanceFromZero) >= 0 ? cleanedStack : 0d;
        return new double[]{from, result};
    }

    /** 1500 ms cubic ease-out, identical to BalanceScreen. */
    static double finalAmountValue(float elapsedSeconds,
            double buyin, double stack) {
        double[] range = finalAmountAnimationRange(buyin, stack);
        double progress = Math.min(1d, Math.max(0d,
                elapsedSeconds / FINAL_AMOUNT_ROLL_SECONDS));
        double eased = 1d - Math.pow(1d - progress, 3d);
        return MoneyMath.clean(range[0] + (range[1] - range[0]) * eased);
    }

    /** Swing reveals, then performs three visible/blank cycles at 130 ms. */
    static boolean finalAmountVisible(float elapsedSeconds) {
        if (elapsedSeconds < FINAL_AMOUNT_ROLL_SECONDS) return true;
        int step = (int) ((elapsedSeconds - FINAL_AMOUNT_ROLL_SECONDS)
                / FINAL_AMOUNT_BLINK_STEP_SECONDS);
        return step >= FINAL_AMOUNT_BLINK_STEPS || step % 2 == 0;
    }

    static boolean finalSummaryScreenshotReady(float elapsedSeconds,
            TableSessionSummary.PlayerBalance local,
            TableSessionSummary.CloseReason reason,
            boolean counterAnimationEnabled) {
        if (local == null) return false;
        if (reason == TableSessionSummary.CloseReason.RECOVERABLE_STOP
                || reason == TableSessionSummary.CloseReason.FAILURE) {
            return false;
        }
        if (elapsedSeconds < FINAL_SUMMARY_REVEAL_SECONDS) {
            return false;
        }
        if (!counterAnimationEnabled
                || Double.compare(local.netResult(), 0d) == 0) {
            return true;
        }
        return elapsedSeconds >= FINAL_AMOUNT_ROLL_SECONDS
                + FINAL_AMOUNT_BLINK_STEP_SECONDS * FINAL_AMOUNT_BLINK_STEPS;
    }

    static Color finalSummaryResultColor(double net) {
        return net > 0d ? FINAL_WINNER
                : net < 0d ? FINAL_LOSER : new Color(0x8b9098ff);
    }

    static float finalSummaryHeroY(float height,
            boolean moneyCounterVisible) {
        // With no amount below it (break-even or non-economic closure), the
        // hero is the only element in the large gap between the header and
        // player cards. Centre that single line in the useful vertical area;
        // retaining the two-line origin would leave it visibly top-heavy.
        return height - (moneyCounterVisible ? 325f : 490f);
    }

    /** A neutral result has a headline, never a redundant giant zero. */
    static boolean finalMoneyCounterVisible(double net) {
        return Double.compare(MoneyMath.clean(net), 0d) != 0;
    }

    static boolean finalSummaryNavEnabled(int index) {
        return index >= 0 && index < 4;
    }

    private static String finalSummaryTitle(
            TableSessionSummary.CloseReason reason, GdxGameText gameText) {
        String suffix = switch (reason) {
            case COMPLETED -> "completed";
            case EXITED -> "exited";
            case RECOVERABLE_STOP -> "recoverable_stop";
            case FAILURE -> "failure";
        };
        return gameText.translate("gdx.final.title." + suffix).toUpperCase(
                Locale.forLanguageTag(gameText.language()));
    }

    static String finalSummaryHero(
            TableSessionSummary.CloseReason reason, double net,
            GdxGameText gameText) {
        String key;
        if (reason == TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
            key = "gdx.final.hero.recoverable_stop";
        } else if (reason == TableSessionSummary.CloseReason.FAILURE) {
            key = "gdx.final.hero.failure";
        } else {
            key = net > 0d ? "gdx.final.hero.win" : net < 0d
                    ? "gdx.final.hero.loss" : "gdx.final.hero.even";
        }
        return gameText.translate(key).toUpperCase(
                Locale.forLanguageTag(gameText.language()));
    }

    private static String finalSummaryDate(long endedAtMillis) {
        return java.time.Instant.ofEpochMilli(endedAtMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    private static String finalSummaryDuration(long seconds) {
        return formatPlayTime(seconds);
    }

    static String formatPlayTime(long seconds) {
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainder = seconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, remainder);
    }

    private void drawFpsCounter(float width, float height) {
        float panelWidth = 126f;
        float panelHeight = 42f;
        float x = width - panelWidth - 20f;
        float y = height - panelHeight - 18f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(0f, 0f, 0f, 0.48f);
        roundedRect(x + 3f, y - 3f, panelWidth, panelHeight, 11f);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.88f);
        roundedRect(x, y, panelWidth, panelHeight, 11f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.86f);
        shapes.rect(x + 12f, y + 4f, panelWidth - 24f, 3f);
        shapes.end();

        batch.begin();
        drawCentered(smallFont, Gdx.graphics.getFramesPerSecond() + " FPS",
                x + panelWidth / 2f, y + 29f, Color.WHITE, 1f);
        batch.end();
    }

    private void drawCentered(BitmapFont font, String text, float centerX, float baselineY,
            Color color, float alpha) {
        font.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(font, text);
        font.draw(batch, text, centerX - glyph.width / 2f, baselineY);
        font.setColor(Color.WHITE);
    }

    /** Returns one single-line label which can never escape its horizontal box. */
    private String ellipsizeToWidth(BitmapFont font, String text,
            float maxWidth) {
        String value = text == null ? "" : text;
        if (maxWidth <= 0f || value.isEmpty()) return "";
        glyph.setText(font, value);
        if (glyph.width <= maxWidth) return value;
        String suffix = "…";
        glyph.setText(font, suffix);
        if (glyph.width > maxWidth) return "";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing()
                    + suffix;
            glyph.setText(font, candidate);
            if (glyph.width <= maxWidth) return candidate;
        }
        return suffix;
    }

    private void drawFittedCentered(BitmapFont font, String text, float centerX,
            float baselineY, float maxWidth, Color color, float alpha) {
        BitmapFont.BitmapFontData data = font.getData();
        float originalScaleX = data.scaleX;
        float originalScaleY = data.scaleY;
        glyph.setText(font, text);
        if (glyph.width > maxWidth) {
            float fit = maxWidth / glyph.width;
            data.setScale(originalScaleX * fit, originalScaleY * fit);
        }
        drawCentered(font, text, centerX, baselineY, color, alpha);
        data.setScale(originalScaleX, originalScaleY);
    }

    private void drawFittedCenteredInBox(BitmapFont font, String text,
            float x, float y, float width, float height,
            Color color, float alpha) {
        BitmapFont.BitmapFontData data = font.getData();
        float originalScaleX = data.scaleX;
        float originalScaleY = data.scaleY;
        font.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(font, text);
        float fitX = glyph.width > 0f ? width / glyph.width : 1f;
        float fitY = glyph.height > 0f ? height / glyph.height : 1f;
        float fit = Math.min(1f, Math.min(fitX, fitY));
        if (fit < 1f) {
            data.setScale(originalScaleX * fit, originalScaleY * fit);
            glyph.setText(font, text);
        }
        font.draw(batch, glyph,
                x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f);
        font.setColor(Color.WHITE);
        data.setScale(originalScaleX, originalScaleY);
    }

    private void drawLeftInBox(BitmapFont font, String text,
            float x, float y, float width, float height,
            Color color, float alpha) {
        BitmapFont.BitmapFontData data = font.getData();
        float originalScaleX = data.scaleX;
        float originalScaleY = data.scaleY;
        font.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(font, text);
        float fitX = glyph.width > 0f ? width / glyph.width : 1f;
        float fitY = glyph.height > 0f ? height / glyph.height : 1f;
        float fit = Math.min(1f, Math.min(fitX, fitY));
        if (fit < 1f) {
            data.setScale(originalScaleX * fit, originalScaleY * fit);
            glyph.setText(font, text);
        }
        font.draw(batch, glyph, x, y + (height + glyph.height) / 2f);
        font.setColor(Color.WHITE);
        data.setScale(originalScaleX, originalScaleY);
    }

    private void recordFrame(float delta) {
        frameSamples[frameCursor] = delta * 1000f;
        frameCursor = (frameCursor + 1) % frameSamples.length;
        frameCount = Math.min(frameCount + 1, frameSamples.length);
        statsClock += delta;
        if (statsClock < 0.5f || frameCount < 30) {
            return;
        }
        statsClock = 0f;
        System.arraycopy(frameSamples, 0, frameScratch, 0, frameCount);
        Arrays.sort(frameScratch, 0, frameCount);
        float p99 = frameScratch[Math.min(frameCount - 1, (int) (frameCount * 0.99f))];
        float worst = frameScratch[frameCount - 1];
        statsText = Gdx.graphics.getFramesPerSecond() + " FPS  |  p99 "
                + String.format("%.2f", p99) + " ms  |  max "
                + String.format("%.2f", worst) + " ms  |  monitor "
                + detectedRefreshRate + " Hz";
    }

    /** Keeps the windowed safety cap aligned with the monitor containing the window. */
    private void updateWindowMonitorRefresh(float delta) {
        monitorRefreshPollClock += delta;
        if (monitorRefreshPollClock < 0.35f) {
            return;
        }
        monitorRefreshPollClock = 0f;
        Monitor monitor = Gdx.graphics.getMonitor();
        if (monitor == null) {
            return;
        }
        DisplayMode mode = Gdx.graphics.getDisplayMode(monitor);
        if (mode == null || mode.refreshRate <= 0) {
            return;
        }
        boolean changed = !monitor.name.equals(activeMonitorName)
                || detectedRefreshRate != mode.refreshRate;
        if (!changed) {
            return;
        }
        activeMonitorName = monitor.name;
        detectedRefreshRate = mode.refreshRate;
        if (!Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setForegroundFPS(detectedRefreshRate);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        disposed = true;
        CompletableFuture<Void> closingSummaryBarrier = finalSummaryBarrier;
        finalSummaryBarrier = null;
        if (closingSummaryBarrier != null
                && !closingSummaryBarrier.isDone()) {
            closingSummaryBarrier.complete(null);
        }
        CompletableFuture<Void> closingRecoveryBarrier = recoveryStopBarrier;
        recoveryStopBarrier = null;
        if (closingRecoveryBarrier != null
                && !closingRecoveryBarrier.isDone()) {
            closingRecoveryBarrier.complete(null);
        }
        GdxVoiceRecorder activeRecorder = voiceRecorder;
        voiceRecorder = null;
        if (activeRecorder != null) activeRecorder.abort();
        if (textToSpeech != null) textToSpeech.close();
        GdxVoicePlayback.stop();
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
        while (!dialogQueue.isEmpty()) dialogQueue.removeFirst().dismiss();
        if (startupIntroOnly) {
            batch.dispose();
            shapes.dispose();
            roundedCardShader.dispose();
            logo.dispose();
            feltTexture.dispose();
            defaultCardBack.dispose();
            for (Texture texture : liveCardFaces.values()) texture.dispose();
            liveCardFaces.clear();
            return;
        }
        stopAudioCue("misc/game_over.wav");
        restoreGameOverLoopDucking();
        for (LiveAudioPlayback playback : List.copyOf(liveAudioCueWaits)) {
            finishAudioCue(playback, null);
        }
        stopDangerAudioLoop();
        for (Sound cue : liveAudioCueSounds.values()) cue.dispose();
        liveAudioCueSounds.clear();
        failedPreferenceSoundCues.clear();
        for (Music loop : liveAudioCueLoops.values()) {
            loop.stop();
            loop.dispose();
        }
        liveAudioCueLoops.clear();
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.dispose();
        }
        batch.dispose();
        shapes.dispose();
        roundedCardShader.dispose();
        if (avatarShader != null) avatarShader.dispose();
        if (allInFireShader != null) allInFireShader.dispose();
        if (backdropBlurShader != null) backdropBlurShader.dispose();
        if (settingsBackdrop != null) settingsBackdrop.dispose();
        if (settingsBlurScratch != null) settingsBlurScratch.dispose();
        if (disabledHoleCardsLayer != null) disabledHoleCardsLayer.dispose();
        uiFont.dispose();
        smallFont.dispose();
        playerNameFont.dispose();
        stackFont.dispose();
        actionFont.dispose();
        seatActionFont.dispose();
        callCostFont.dispose();
        pauseFont.dispose();
        finalHeroFont.dispose();
        finalAmountFont.dispose();
        finalTitleFont.dispose();
        finalDetailFont.dispose();
        finalButtonFont.dispose();
        finalCardFont.dispose();
        finalCardBoldFont.dispose();
        gameLogFont.dispose();
        logo.dispose();
        feltTexture.dispose();
        avatarDefault.dispose();
        avatarBot.dispose();
        allInFireCanvas.dispose();
        dealerChip.dispose();
        smallBlindChip.dispose();
        bigBlindChip.dispose();
        logMoneyIcon.dispose();
        logStraddleIcon.dispose();
        logDealerStraddleIcon.dispose();
        defaultCardBack.dispose();
        for (Texture flyingChip : flyingChips) {
            flyingChip.dispose();
        }
        pot.dispose();
        // The live-card cache is the single owner, preventing duplicate GPU
        // uploads across board, private-card and viewer projections.
        for (Texture texture : liveCardFaces.values()) {
            texture.dispose();
        }
        liveCardFaces.clear();
        for (Texture texture : liveCardBacks.values()) {
            texture.dispose();
        }
        liveCardBacks.clear();
        if (liveShuffle != null) {
            if (liveShuffle.animation != null) {
                liveShuffle.animation.dispose();
            }
            liveShuffle = null;
        }
        if (liveCinematic != null) {
            liveCinematic.animation.dispose();
            liveCinematic = null;
        }
        releaseGameOverAnimation();
        releaseRecoveryAnimation();
        for (Sound cinematicSound : liveCinematicSounds.values()) {
            cinematicSound.dispose();
        }
        liveCinematicSounds.clear();
        shuffleSound.dispose();
        dealSound.dispose();
        uncoverSound.dispose();
        checkSound.dispose();
        callSound.dispose();
        betSound.dispose();
        foldSound.dispose();
        allInSound.dispose();
        buttonOnSound.dispose();
        buttonOffSound.dispose();
        balanceCountSound.dispose();
        cardViewerSound.dispose();
        screenshotSound.dispose();
        feltChangeSound.dispose();
        disposeScreenshotTexture();
        soundIcon.dispose();
        muteIcon.dispose();
        blockedSoundIcon.dispose();
        lightsOnIcon.dispose();
        lightsOffIcon.dispose();
        pauseIcon.dispose();
        foldThumbIcon.dispose();
        callThumbIcon.dispose();
        talkIcon.dispose();
        finalMenuIcon.dispose();
        finalLogIcon.dispose();
        finalStatsIcon.dispose();
        finalContinueIcon.dispose();
        fastMenuIcon.dispose();
        for (Texture fastButtonIcon : fastButtonIcons) {
            fastButtonIcon.dispose();
        }
        for (Texture emojiTexture : emojiTextures.values()) {
            emojiTexture.dispose();
        }
        emojiTextures.clear();
        tableGalleryMedia.dispose();
        for (Texture avatarTexture : tableAvatarTextures.values()) {
            avatarTexture.dispose();
        }
        tableAvatarTextures.clear();
        for (SeatChatNotice notice : seatChatNotices.values()) notice.dispose();
        seatChatNotices.clear();
        silentChatNotices.clear();
        blockedSeatMediaNotices.clear();
        if (tableChat != null) tableChat.close();
    }

    private static final class SilentChatNotice {

        final String nickname;
        final String content;
        final boolean senderBlocked;
        final float duration;
        float startedAt = -1f;
        float expiresAt = Float.POSITIVE_INFINITY;

        SilentChatNotice(String nickname, String content,
                boolean senderBlocked, float duration) {
            this.nickname = nickname;
            this.content = content;
            this.senderBlocked = senderBlocked;
            this.duration = duration;
        }
    }

    private static final class LiveAudioPlayback {

        final String resource;
        final Music music;
        final CompletableFuture<Void> barrier;

        LiveAudioPlayback(String resource, Music music,
                CompletableFuture<Void> barrier) {
            this.resource = resource;
            this.music = music;
            this.barrier = barrier;
        }
    }

    private record LiveCardPlacement(float x, float y, float width,
            float height, float rotation) {
    }

    private record ViewedCard(Texture texture, boolean faceUp) {
    }

    private record TableInputWindow(String text, int sourceStart,
            int sourceEnd, float caretOffset,
            float selectionOffset, float selectionWidth) {
    }

    private static final class SeatChatNotice {

        final LobbyChatMessage.Type type;
        final String content;
        float startedAt;
        float expiresAt;
        boolean loading = true;
        boolean failed;
        Texture image;
        StreamingGifTextureAnimation gif;

        SeatChatNotice(LobbyChatMessage.Type type, String content,
                float startedAt, float expiresAt) {
            this.type = type;
            this.content = content;
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
            loading = type == LobbyChatMessage.Type.IMAGE;
        }

        void dispose() {
            if (image != null) {
                image.dispose();
                image = null;
            }
            if (gif != null) {
                gif.dispose();
                gif = null;
            }
        }
    }

    private record PreparedSeatChatImage(byte[] data,
            StreamingGifTextureAnimation gif) {
    }

    private static final class LiveCardFlight {

        final TableVisualEvent event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;
        final float barrierDelaySeconds;
        final int dealOrder;
        final float flightSeconds;
        final float flipSeconds;
        final boolean animated;
        boolean stateApplied;
        boolean barrierReleased;
        boolean revealSoundPlayed;

        LiveCardFlight(TableVisualEvent event, long startedAtNanos,
                CompletableFuture<Void> barrier, float barrierDelaySeconds,
                int dealOrder, float flightSeconds, float flipSeconds,
                boolean animated) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
            this.barrierDelaySeconds = barrierDelaySeconds;
            this.dealOrder = dealOrder;
            this.flightSeconds = flightSeconds;
            this.flipSeconds = flipSeconds;
            this.animated = animated;
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        float flightProgress() {
            return !animated || flightSeconds <= 0f ? 1f
                    : Math.min(1f, elapsedSeconds() / flightSeconds);
        }

        float revealProgress() {
            if (!(event instanceof TableVisualEvent.DealHoleCard deal)
                    || !deal.card().faceUp() || deal.card().code().isBlank()) {
                return 0f;
            }
            if (flipSeconds <= 0f) {
                return elapsedSeconds() >= flightSeconds ? 1f : 0f;
            }
            return MathUtils.clamp(
                    (elapsedSeconds() - flightSeconds) / flipSeconds,
                    0f, 1f);
        }

        boolean visualFinished() {
            return elapsedSeconds() >= visualDurationSeconds();
        }

        float visualDurationSeconds() {
            return event instanceof TableVisualEvent.DealHoleCard deal
                    && deal.card().faceUp() && !deal.card().code().isBlank()
                    ? flightSeconds + flipSeconds : flightSeconds;
        }
    }

    private static final class LiveHoleSwap {

        final TableVisualEvent.SwapHoleCards event;
        final long startedAtNanos;
        final List<TableSnapshot.CardSnapshot> cards;
        final CompletableFuture<Void> barrier;
        final boolean waitForCompletion;
        final float delaySeconds;
        final float durationSeconds;
        final boolean arc;

        LiveHoleSwap(TableVisualEvent.SwapHoleCards event, long startedAtNanos,
                List<TableSnapshot.CardSnapshot> cards,
                CompletableFuture<Void> barrier, boolean waitForCompletion,
                float delaySeconds, float durationSeconds, boolean arc) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.cards = List.copyOf(cards);
            this.barrier = barrier;
            this.waitForCompletion = waitForCompletion;
            this.delaySeconds = delaySeconds;
            this.durationSeconds = durationSeconds;
            this.arc = arc;
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        float progress() {
            return MathUtils.clamp(
                    (elapsedSeconds() - delaySeconds)
                    / durationSeconds,
                    0f, 1f);
        }

        boolean finished() {
            return elapsedSeconds() >= delaySeconds + durationSeconds;
        }
    }

    private static final class LiveCommunityReveal {

        final TableVisualEvent.RevealCommunityCards event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;
        final boolean[] soundPlayed;
        final float flipSeconds;
        final boolean animated;
        final float leadInSeconds;

        LiveCommunityReveal(TableVisualEvent.RevealCommunityCards event,
                long startedAtNanos, CompletableFuture<Void> barrier,
                float flipSeconds, boolean animated) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
            this.soundPlayed = new boolean[event.cards().size()];
            this.flipSeconds = flipSeconds;
            this.animated = animated;
            this.leadInSeconds = event.leadInMillis() / 1000f;
        }

        boolean containsSlot(int slot) {
            return slot >= event.firstSlot()
                    && slot < event.firstSlot() + event.cards().size();
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        float progress(int offset) {
            if (!animated) {
                return elapsedSeconds() >= leadInSeconds * (offset + 1)
                        ? 1f : 0f;
            }
            return MathUtils.clamp(
                    (elapsedSeconds()
                            - communityRevealStartSeconds(offset,
                                    leadInSeconds))
                    / flipSeconds, 0f, 1f);
        }

        boolean finished() {
            if (!animated) {
                return elapsedSeconds() >= leadInSeconds * event.cards().size();
            }
            return elapsedSeconds() >= communityRevealAnimationSeconds(
                    event.cards().size(), leadInSeconds, flipSeconds);
        }
    }

    private static final class LiveHoleReveal {

        private static final TableSnapshot.CardSnapshot HIDDEN_CARD
                = new TableSnapshot.CardSnapshot("", false, false);

        final TableVisualEvent.RevealHoleCards event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;
        final List<TableSnapshot.CardSnapshot> originalCards;
        boolean soundPlayed;
        final float flipSeconds;

        LiveHoleReveal(TableVisualEvent.RevealHoleCards event,
                long startedAtNanos, CompletableFuture<Void> barrier,
                List<TableSnapshot.CardSnapshot> originalCards,
                float flipSeconds) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
            this.originalCards = List.copyOf(originalCards);
            this.flipSeconds = flipSeconds;
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        float progress(int slot) {
            return MathUtils.clamp(
                    elapsedSeconds() / flipSeconds, 0f, 1f);
        }

        TableSnapshot.CardSnapshot originalCard(int slot) {
            return slot < originalCards.size() ? originalCards.get(slot) : HIDDEN_CARD;
        }

        TableSnapshot.CardSnapshot revealedCard(int slot) {
            return slot == 0 ? event.left() : event.right();
        }

        boolean finished() {
            return elapsedSeconds() >= flipSeconds;
        }
    }

    private static final class LiveHoleFold {

        final TableVisualEvent.FoldHoleCards event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;

        LiveHoleFold(TableVisualEvent.FoldHoleCards event,
                long startedAtNanos, CompletableFuture<Void> barrier) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
        }

        float progress() {
            float elapsed = Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
            return Math.min(1f, elapsed / FOLD_DISABLE_SECONDS);
        }
    }

    private static final class LiveHandProbability {

        private final float from;
        private final float target;
        private final long startedAtNanos;
        private final boolean animate;

        LiveHandProbability(float from, float target, long startedAtNanos,
                boolean animate) {
            this.from = from;
            this.target = target;
            this.startedAtNanos = startedAtNanos;
            this.animate = animate;
        }

        float valueAt(long nowNanos) {
            return partialHandProbabilityValue(from, target,
                    Math.max(0L, nowNanos - startedAtNanos), animate);
        }
    }

    private static final class LivePositionRotation {

        final TableVisualEvent.PositionRotation event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;

        LivePositionRotation(TableVisualEvent.PositionRotation event,
                long startedAtNanos, CompletableFuture<Void> barrier) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
        }

        float progress() {
            long elapsedNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
            double durationNanos = event.durationMillis() * 1_000_000d;
            return (float) Math.min(1d, elapsedNanos / durationNanos);
        }
    }

    private final class LiveShuffle {

        final StreamingGifTextureAnimation animation;
        final long startedAtNanos;
        final boolean animationEnabled;
        final boolean soundEnabled;
        final float cycleSeconds;
        final float soundStopSeconds;
        int soundCycle;
        boolean soundStopped;
        float stopAtSeconds = Float.POSITIVE_INFINITY;
        TableVisualEvent.Shuffle finishEvent;
        CompletableFuture<Void> finishBarrier;

        LiveShuffle(String deck, long startedAtNanos,
                boolean animationEnabled, boolean soundEnabled) {
            this.startedAtNanos = startedAtNanos;
            this.animationEnabled = animationEnabled;
            this.soundEnabled = soundEnabled;
            animation = animationEnabled ? liveShuffleAnimation(deck) : null;
            cycleSeconds = animationEnabled
                    ? animation.durationSeconds()
                    : soundEnabled ? shuffleSoundDurationSeconds
                            : SHUFFLE_TEXT_CYCLE_SECONDS;
            soundStopSeconds = animationEnabled
                    ? animation.frameStartSeconds(SHUFFLE_AUDIO_STOP_FRAME)
                            : cycleSeconds;
            if (animationEnabled) {
                System.out.printf(
                        "Shuffle stream: %dx%d | %.0f ms | audio cutoff %.0f ms%n",
                        animation.width(), animation.height(),
                        cycleSeconds * 1000f, soundStopSeconds * 1000f);
            }
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }
    }

    private static final class LiveActionChip {

        final TableVisualEvent.PlayerAction event;
        final long startedAtNanos;
        final List<LiveChip> chips;
        final boolean deferPlayerCounters;
        boolean soundPlayed;

        LiveActionChip(TableVisualEvent.PlayerAction event,
                long startedAtNanos, boolean deferPlayerCounters) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.deferPlayerCounters = deferPlayerCounters;
            int chipCount = switch (event.kind()) {
                case ALL_IN -> 7;
                case BET, RAISE, RERAISE -> 4;
                case CALL -> 3;
                default -> 1;
            };
            int baseColor = switch (event.kind()) {
                case ALL_IN -> 3;
                case BET, RAISE, RERAISE -> 2;
                case CALL -> 1;
                default -> 0;
            };
            ArrayList<LiveChip> created = new ArrayList<>(chipCount);
            double base = event.contributionDelta() / chipCount;
            for (int chip = 0; chip < chipCount; chip++) {
                double contribution = chip == chipCount - 1
                        ? event.contributionDelta() - base * (chipCount - 1)
                        : base;
                created.add(new LiveChip(event.nickname(),
                        CHIP_FLIGHT_DELAY + chip * 0.065f,
                        CHIP_FLIGHT_SECONDS + chip * 0.035f,
                        Math.floorMod(event.nickname().hashCode()
                                + chip * 53, 360),
                        (baseColor + chip) % 4, chip, contribution));
            }
            chips = List.copyOf(created);
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        boolean complete(float elapsed) {
            return chips.stream().allMatch(chip -> chip.progress(elapsed) >= 1f);
        }

        double landedContribution(float elapsed) {
            double total = 0d;
            for (LiveChip chip : chips) {
                if (chip.progress(elapsed) >= 1f) total += chip.contribution;
            }
            return total;
        }

    }

    private static final class PendingCollectBets {

        final TableVisualEvent.CollectBets event;
        final CompletableFuture<Void> barrier;
        final TableSnapshot presentationSnapshot;

        PendingCollectBets(TableVisualEvent.CollectBets event,
                CompletableFuture<Void> barrier,
                TableSnapshot presentationSnapshot) {
            this.event = event;
            this.barrier = barrier;
            this.presentationSnapshot = presentationSnapshot;
        }
    }

    private static final class LivePayout {

        final TableVisualEvent.Payout event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;
        final List<LivePayoutChip> chips;

        LivePayout(TableVisualEvent.Payout event, long startedAtNanos,
                CompletableFuture<Void> barrier) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
            List<LivePayoutChip> created = new ArrayList<>(18);
            double base = event.amount() / 18d;
            for (int index = 0; index < 18; index++) {
                double amount = index == 17
                        ? event.amount() - base * 17d : base;
                created.add(new LivePayoutChip(index,
                        0.18f + index * 0.035f, 1.05f,
                        index % 4, amount));
            }
            chips = List.copyOf(created);
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        double landedContribution(float elapsed) {
            double total = 0d;
            for (LivePayoutChip chip : chips) {
                if (chip.progress(elapsed) >= 1f) {
                    total += chip.amount;
                }
            }
            return total;
        }

        boolean complete(float elapsed) {
            return chips.stream().allMatch(chip -> chip.progress(elapsed) >= 1f);
        }
    }

    private static final class LivePayoutChip {

        final int index;
        final float startSeconds;
        final float durationSeconds;
        final int color;
        final double amount;
        boolean soundPlayed;

        LivePayoutChip(int index, float startSeconds, float durationSeconds,
                int color, double amount) {
            this.index = index;
            this.startSeconds = startSeconds;
            this.durationSeconds = durationSeconds;
            this.color = color;
            this.amount = amount;
        }

        float progress(float elapsed) {
            return MathUtils.clamp((elapsed - startSeconds) / durationSeconds,
                    0f, 1f);
        }
    }

    private static final class LiveRebuy {

        final TableVisualEvent.Rebuy event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;
        final List<LiveRebuyChip> chips;
        final String cashSoundResource;

        LiveRebuy(TableVisualEvent.Rebuy event, long startedAtNanos,
                CompletableFuture<Void> barrier, String cashSoundResource) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
            this.cashSoundResource = cashSoundResource == null
                    ? "" : cashSoundResource;
            ArrayList<LiveRebuyChip> created = new ArrayList<>();
            float totalSeconds = event.durationMillis() / 1_000f;
            int transferIndex = 0;
            for (TableVisualEvent.ChipTransfer transfer : event.transfers()) {
                int chipCount = 14;
                double base = transfer.amount() / chipCount;
                for (int index = 0; index < chipCount; index++) {
                    float start = 0.04f + index * 0.025f
                            + transferIndex * 0.015f;
                    float duration = Math.max(0.28f, totalSeconds - start);
                    double amount = index == chipCount - 1
                            ? transfer.amount() - base * (chipCount - 1)
                            : base;
                    int seed = transfer.nickname().hashCode()
                            + transferIndex * 97 + index * 31;
                    created.add(new LiveRebuyChip(transfer.nickname(), index,
                            start, duration, Math.floorMod(seed, 4), amount,
                            Math.floorMod(seed, 360)));
                }
                transferIndex++;
            }
            chips = List.copyOf(created);
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }

        double landedContribution(String nickname, float elapsed) {
            double total = 0d;
            for (LiveRebuyChip chip : chips) {
                if (chip.nickname.equals(nickname)
                        && chip.progress(elapsed) >= 1f) {
                    total += chip.amount;
                }
            }
            return total;
        }

        double totalContribution(String nickname) {
            double total = 0d;
            for (LiveRebuyChip chip : chips) {
                if (chip.nickname.equals(nickname)) total += chip.amount;
            }
            return total;
        }

        boolean complete(float elapsed) {
            return chips.stream().allMatch(chip -> chip.progress(elapsed) >= 1f);
        }
    }

    private static final class LiveInitialStackFill {

        final TableVisualEvent.InitialStackFill event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;

        LiveInitialStackFill(TableVisualEvent.InitialStackFill event,
                long startedAtNanos, CompletableFuture<Void> barrier) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
        }

        float progress() {
            float elapsedMillis = Math.max(0L,
                    System.nanoTime() - startedAtNanos) / 1_000_000f;
            return MathUtils.clamp(elapsedMillis / event.durationMillis(),
                    0f, 1f);
        }

        double remaining(String nickname) {
            double remaining = 1d - progress();
            return event.transfers().stream()
                    .filter(transfer -> transfer.nickname().equals(nickname))
                    .mapToDouble(TableVisualEvent.ChipTransfer::amount)
                    .sum() * remaining;
        }

        boolean complete() {
            return progress() >= 1f;
        }
    }

    private static final class LiveRebuyChip {

        final String nickname;
        final int index;
        final float startSeconds;
        final float durationSeconds;
        final int color;
        final double amount;
        final float rotation;
        boolean soundPlayed;

        LiveRebuyChip(String nickname, int index, float startSeconds,
                float durationSeconds, int color, double amount, float rotation) {
            this.nickname = nickname;
            this.index = index;
            this.startSeconds = startSeconds;
            this.durationSeconds = durationSeconds;
            this.color = color;
            this.amount = amount;
            this.rotation = rotation;
        }

        float progress(float elapsed) {
            return MathUtils.clamp((elapsed - startSeconds) / durationSeconds,
                    0f, 1f);
        }
    }

    private static final class LiveCinematic {

        final TableVisualEvent.Cinematic event;
        final StreamingGifTextureAnimation animation;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;

        LiveCinematic(TableVisualEvent.Cinematic event,
                StreamingGifTextureAnimation animation, long startedAtNanos,
                CompletableFuture<Void> barrier) {
            this.event = event;
            this.animation = animation;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos)
                    / 1_000_000_000f;
        }
    }

    private static final class LiveChipBatch {

        final TableVisualEvent.CollectBets event;
        final long startedAtNanos;
        final CompletableFuture<Void> barrier;
        final List<LiveChip> chips;
        final double alreadyInPot;

        LiveChipBatch(TableVisualEvent.CollectBets event, long startedAtNanos,
                CompletableFuture<Void> barrier, TableSnapshot snapshot,
                Map<String, Double> delivered) {
            this.event = event;
            this.startedAtNanos = startedAtNanos;
            this.barrier = barrier;
            alreadyInPot = delivered.values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            List<LiveChip> created = new ArrayList<>();
            TableVisualEvent.ChipTransfer smallBlind = null;
            TableVisualEvent.ChipTransfer bigBlind = null;
            if (event.transfers().size() == 2) {
                for (TableVisualEvent.ChipTransfer transfer : event.transfers()) {
                    TableSnapshot.Position position = canonicalPosition(
                            snapshot, transfer.nickname());
                    if (position == TableSnapshot.Position.SMALL_BLIND) {
                        smallBlind = transfer;
                    } else if (position == TableSnapshot.Position.BIG_BLIND) {
                        bigBlind = transfer;
                    }
                }
            }
            if (smallBlind != null && bigBlind != null) {
                double smallAmount = Math.max(0d, smallBlind.amount()
                        - delivered.getOrDefault(smallBlind.nickname(), 0d));
                double bigAmount = Math.max(0d, bigBlind.amount()
                        - delivered.getOrDefault(bigBlind.nickname(), 0d));
                if (smallAmount > 0.000_001d) {
                    created.add(new LiveChip(smallBlind.nickname(), 0f, 0.34f,
                            37f, 2, 0, smallAmount));
                }
                if (bigAmount > 0.000_001d) {
                    double first = bigAmount / 2d;
                    created.add(new LiveChip(bigBlind.nickname(), 0.06f, 0.36f,
                            91f, 1, 0, first));
                    created.add(new LiveChip(bigBlind.nickname(), 0.14f, 0.38f,
                            143f, 1, 1, bigAmount - first));
                }
                chips = List.copyOf(created);
                return;
            }
            for (int transferIndex = 0;
                    transferIndex < event.transfers().size(); transferIndex++) {
                TableVisualEvent.ChipTransfer transfer =
                        event.transfers().get(transferIndex);
                double visualAmount = Math.max(0d, transfer.amount()
                        - delivered.getOrDefault(transfer.nickname(), 0d));
                if (visualAmount <= 0.000_001d) continue;
                TableSnapshot.Position position = canonicalPosition(
                        snapshot, transfer.nickname());
                int chipCount = switch (position) {
                    case BIG_BLIND -> 2;
                    case STRADDLE, DEALER_STRADDLE -> 3;
                    default -> 1;
                };
                double base = visualAmount / chipCount;
                for (int chipIndex = 0; chipIndex < chipCount; chipIndex++) {
                    double contribution = chipIndex == chipCount - 1
                            ? visualAmount - base * (chipCount - 1) : base;
                    float start = transferIndex * 0.025f + chipIndex * 0.06f;
                    float duration = 0.34f + chipIndex * 0.02f;
                    created.add(new LiveChip(transfer.nickname(), start, duration,
                            (transferIndex * 97f + chipIndex * 53f) % 360f,
                            (transferIndex + chipIndex) % 4, chipIndex,
                            contribution));
                }
            }
            chips = List.copyOf(created);
        }

        private static TableSnapshot.Position canonicalPosition(
                TableSnapshot snapshot, String nickname) {
            return snapshot.players().stream()
                    .filter(player -> player.nickname().equals(nickname))
                    .map(TableSnapshot.PlayerSnapshot::position)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Chip transfer references missing canonical player "
                            + nickname));
        }

        float elapsedSeconds() {
            return Math.max(0L, System.nanoTime() - startedAtNanos) / 1_000_000_000f;
        }

        boolean complete(float elapsed) {
            return chips.stream().allMatch(chip -> chip.progress(elapsed) >= 1f);
        }

        double landedContribution(String nickname, float elapsed) {
            double total = 0d;
            for (LiveChip chip : chips) {
                if ((nickname == null || nickname.equals(chip.nickname))
                        && chip.progress(elapsed) >= 1f) {
                    total += chip.contribution;
                }
            }
            return total;
        }
    }

    private static final class LiveChip {

        final String nickname;
        final float startSeconds;
        final float durationSeconds;
        final float rotation;
        final int color;
        final int chipIndex;
        final double contribution;
        boolean soundPlayed;

        LiveChip(String nickname, float startSeconds, float durationSeconds,
                float rotation, int color, int chipIndex, double contribution) {
            this.nickname = nickname;
            this.startSeconds = startSeconds;
            this.durationSeconds = durationSeconds;
            this.rotation = rotation;
            this.color = color;
            this.chipIndex = chipIndex;
            this.contribution = contribution;
        }

        float progress(float elapsed) {
            float raw = elapsed - startSeconds;
            if (raw < 0f) {
                return -1f;
            }
            return Math.min(1f, raw / durationSeconds);
        }
    }

    private static final class Star {

        float x;
        float y;
        final float speed;
        final float size;
        final float phase;

        Star(float x, float y, float speed, float size, float phase) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.size = size;
            this.phase = phase;
        }
    }

    private static final class Seat {

        final String name;
        String stackText;
        String investedText;
        double displayedStackAmount;
        double displayedInvestedAmount;
        final int index;
        float x;
        float y;
        float stackX;
        float stackY;
        float podX;
        float podY;
        float positionX;
        float positionY;

        Seat(String name, int index) {
            this.name = name;
            this.displayedStackAmount = 0d;
            this.stackText = "0";
            this.displayedInvestedAmount = 0d;
            this.investedText = "0";
            this.index = index;
        }
    }
}
