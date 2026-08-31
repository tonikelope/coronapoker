/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdxdemo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import java.util.Arrays;
import java.io.IOException;

/**
 * Visual-only GPU prototype. It intentionally has no dependency on Crupier,
 * networking, crypto or SQLite yet; it proves the rendering direction without
 * risking the production client.
 */
public final class CoronaPokerGdxDemo extends ApplicationAdapter {

    private static final float BASE_WIDTH = 1920f;
    private static final float BASE_HEIGHT = 1080f;
    private static final float INTRO_SECONDS = 4.2f;
    private static final int STAR_COUNT = 150;
    private static final int SEAT_COUNT = 10;
    private static final int DEMO_HAND_COUNT = 2;
    private static final int FRAME_SAMPLE_COUNT = 720;
    private static final float CARD_FLIP_SECONDS = 0.620f;
    private static final float LOCAL_CARD_FAN_ANGLE = 8.5f;
    private static final float LOCAL_SWAP_DELAY = 0.14f;
    private static final float LOCAL_SWAP_SECONDS = 0.68f;
    private static final float HAND_SECONDS = 60.0f;
    private static final float NORMAL_DEMO_SECONDS = HAND_SECONDS * DEMO_HAND_COUNT;
    private static final float SHOWCASE_FULL_TABLE_PAUSE = 2.5f;
    private static final float SHOWCASE_EXIT_SECONDS = 0.85f;
    private static final float SHOWCASE_REFLOW_SECONDS = 0.90f;
    private static final float SHOWCASE_PAUSE_SECONDS = 1.50f;
    private static final float SHOWCASE_STEP_SECONDS = SHOWCASE_EXIT_SECONDS
            + SHOWCASE_REFLOW_SECONDS + SHOWCASE_PAUSE_SECONDS;
    private static final float SHUFFLE_END = 1.72f;
    private static final float POSITION_CHIP_START = SHUFFLE_END + 0.04f;
    private static final float POSITION_CHIP_STAGGER = 0.03f;
    private static final float POSITION_CHIP_SECONDS = 0.40f;
    private static final float RIVAL_HOLE_CARD_WIDTH = 125f;
    private static final float LOCAL_HOLE_CARD_WIDTH = 184f;
    private static final float COMMUNITY_CARD_MAX_WIDTH = 140f;
    // All positional pucks share one physical diameter. The GDX seat tucks its
    // private cards under the avatar, so Swing's nominal 80% icon ratio looks
    // oversized here; 54% preserves the perceived CoronaPoker proportion.
    private static final float POSITION_CHIP_SIZE = RIVAL_HOLE_CARD_WIDTH * 0.54f;
    private static final float AVATAR_SIZE = 72f;
    private static final float AVATAR_ACTIVE_RADIUS = 61f;
    private static final float AVATAR_OUTER_RADIUS = 52f;
    private static final float AVATAR_RIM_RADIUS = 45f;
    private static final float AVATAR_INNER_RADIUS = 40f;
    private static final int SHUFFLE_AUDIO_STOP_FRAME = 53;
    private static final float CHIP_FLIGHT_DELAY = 0.12f;
    private static final float CHIP_FLIGHT_SECONDS = 0.92f;
    private static final float BLIND_POST_START = 2.24f;
    private static final int BLIND_FLIGHT_COUNT = 3;
    private static final float DEAL_START = 2.92f;
    private static final float DEAL_CARD_GAP = 0.14f;
    private static final float DEAL_CARD_SECONDS = 0.21f;
    private static final float DEAL_END = DEAL_START
            + (SEAT_COUNT * 2 - 1) * DEAL_CARD_GAP + DEAL_CARD_SECONDS;
    private static final float BOARD_DEAL_START = DEAL_END + 0.30f;
    private static final float BOARD_CARD_GAP = 0.20f;
    private static final float BOARD_DEAL_END = BOARD_DEAL_START
            + 4f * BOARD_CARD_GAP + DEAL_CARD_SECONDS;
    private static final float SHOWDOWN_START = 52.2f;
    private static final float WINNER_START = 54.2f;
    private static final float PAYOUT_COMPLETE = WINNER_START + 1.90f;
    private static final float SHOWCASE_TABLE_TIME = WINNER_START + 0.25f;
    private static final float[] COMMUNITY_REVEAL = {23.0f, 23.2f, 23.4f, 36.0f, 44.0f};
    private static final float CARD_CORNER_RADIUS = 0.075f;
    private static final float CARD_EDGE_SOFTNESS = 0.006f;
    private static final float PLAYER_POD_WIDTH = 286f;
    private static final float PLAYER_POD_HEIGHT = 120f;
    private static final float POT_PANEL_HEIGHT = 82f;
    private static final float POT_BOARD_GAP = 24f;
    private static final float LOCAL_HUD_Y = 12f;
    private static final float LOCAL_HUD_HEIGHT = 126f;
    private static final float LOCAL_HUD_SAFE_TOP = LOCAL_HUD_Y + LOCAL_HUD_HEIGHT + 32f;
    private static final float RIVAL_REVEAL_HUD_GAP = 20f;
    private static final float RIVAL_REVEAL_TOP_MARGIN = 58f;
    private static final int ACTION_CHECK = 0;
    private static final int ACTION_BET = 1;
    private static final int ACTION_CALL = 2;
    private static final int ACTION_FOLD = 3;
    private static final int ACTION_ALLIN = 4;
    private static final String[] HUD_ACTIONS = {"NO IR", "IR +300", "APOSTAR", "ALL-IN"};
    private static final int[][] LOCAL_CARD_RANKS = {{11, 12}, {14, 13}};
    private static final int[] SHOWDOWN_SEATS = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final String[][] SHOWDOWN_RESULTS = {
        {
            "CARTA ALTA", "ESCALERA", "TRÍO",
            "CARTA ALTA", "CARTA ALTA", "CARTA ALTA",
            "TRÍO", "TRÍO", "TRÍO", "TRÍO"
        },
        {
            "CARTA ALTA", "PAREJA", "TRÍO",
            "TRÍO", "TRÍO", "TRÍO",
            "TRÍO", "PAREJA", "CARTA ALTA", "PAREJA"
        }
    };
    private static final float[][][] SEAT_LAYOUTS = createSeatLayouts();
    private static final float[][] SEAT_ANCHORS = SEAT_LAYOUTS[SEAT_COUNT];

    private static final ActionEvent[] ACTIONS = {
        // Preflop. SB and BB already have 50/100 posted. Every player who
        // reaches the flop has contributed exactly 300.
        new ActionEvent(7.8f, 3, ACTION_CALL, "VA", 100, 2, 0),
        new ActionEvent(9.0f, 4, ACTION_FOLD, "NO VA", 0, 0, 0),
        new ActionEvent(10.2f, 5, ACTION_CALL, "VA", 100, 2, 1),
        new ActionEvent(11.4f, 6, ACTION_BET, "SUBE (+200)", 300, 4, 2),
        new ActionEvent(12.7f, 7, ACTION_FOLD, "NO VA", 0, 0, 0),
        new ActionEvent(13.9f, 8, ACTION_CALL, "VA", 300, 4, 3),
        new ActionEvent(15.1f, 9, ACTION_FOLD, "NO VA", 0, 0, 0),
        // The local fold retains the swap demonstration before disabling its
        // cards; sorting and folding are independent behaviours.
        new ActionEvent(16.3f, 0, ACTION_FOLD, "NO VAS", 0, 0, 0),
        new ActionEvent(17.5f, 1, ACTION_CALL, "VA", 250, 3, 0),
        new ActionEvent(18.7f, 2, ACTION_CALL, "VA", 200, 3, 1),
        new ActionEvent(19.9f, 3, ACTION_CALL, "VA", 200, 3, 2),
        new ActionEvent(21.1f, 5, ACTION_CALL, "VA", 200, 3, 3),

        // Flop. The three players continuing to the turn finish on 900 each.
        new ActionEvent(24.4f, 1, ACTION_CHECK, "PASA", 0, 0, 0),
        new ActionEvent(25.5f, 2, ACTION_CHECK, "PASA", 0, 0, 0),
        new ActionEvent(26.6f, 3, ACTION_FOLD, "NO VA", 0, 0, 0),
        new ActionEvent(27.7f, 5, ACTION_CHECK, "PASA", 0, 0, 0),
        new ActionEvent(28.8f, 6, ACTION_BET, "APUESTA 600", 600, 4, 0),
        new ActionEvent(30.4f, 8, ACTION_CALL, "VA", 600, 4, 1),
        new ActionEvent(32.0f, 1, ACTION_FOLD, "NO VA", 0, 0, 0),
        new ActionEvent(33.0f, 2, ACTION_CALL, "VA", 600, 4, 2),
        new ActionEvent(34.6f, 5, ACTION_FOLD, "NO VA", 0, 0, 0),

        // Turn. Riverking, Pixel and Shark all close the street on 1,800.
        new ActionEvent(37.2f, 2, ACTION_CHECK, "PASA", 0, 0, 0),
        new ActionEvent(38.3f, 6, ACTION_BET, "APUESTA 900", 900, 5, 3),
        new ActionEvent(40.0f, 8, ACTION_CALL, "VA", 900, 5, 0),
        new ActionEvent(41.7f, 2, ACTION_CALL, "VA", 900, 5, 1),

        // River. Riverking has 1,450 left and shoves it. Pixel calls; Shark
        // folds. Both showdown contenders therefore invested exactly 3,250.
        new ActionEvent(45.0f, 2, ACTION_ALLIN, "ALL IN (+1.450)", 1450, 7, 2),
        // rounders.gif lasts 3.42 s; the call waits for its final frame.
        new ActionEvent(48.7f, 6, ACTION_CALL, "VA", 1450, 7, 3),
        new ActionEvent(51.0f, 8, ACTION_FOLD, "NO VA", 0, 0, 0)
    };

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
            + "    pixel.a *= mask;\n"
            + "    if (pixel.a <= 0.001) discard;\n"
            + "    gl_FragColor = pixel;\n"
            + "}\n";

    private static final Color BACKGROUND_TOP = new Color(0x07111fff);
    private static final Color BACKGROUND_BOTTOM = new Color(0x02050cff);
    private static final Color FELT_SHADE_TOP = new Color(0x07111f1f);
    private static final Color FELT_SHADE_BOTTOM = new Color(0x02050c52);
    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color ORANGE = new Color(0xff6b27ff);
    private static final Color PANEL = new Color(0x101a2ee6);
    private static final Color SEAT_RIM = new Color(0x647594ff);
    private static final Color SEAT_INNER = new Color(0x111a2aff);
    private static final Color STACK_GREEN = new Color(0x9fffd2ff);
    private static final Color POT_GOLD = new Color(0xffe07aff);
    private static final Color BUTTON_LINE = new Color(0x31445fff);
    private static final Color FOLD_RED = new Color(0xd9343fff);
    private static final Color FOLDED_AVATAR = new Color(0.35f, 0.35f, 0.38f, 0.72f);
    // Exact semantic palette from Swing LocalPlayer/RemotePlayer. These are
    // learned gameplay signals, not decorative colors for the new renderer.
    private static final Color LEGACY_FOLD = new Color(0x808080ff);
    private static final Color LEGACY_CHECK = new Color(0x008200ff);
    private static final Color LEGACY_CALL = new Color(0xffffffff);
    private static final Color LEGACY_BET = new Color(0xffff00ff);
    private static final Color LEGACY_RERAISE = new Color(0x7d05e1ff);
    private static final Color LEGACY_ALL_IN = new Color(0x000000ff);
    private static final Color LEGACY_SHOW = new Color(0x3399ffff);
    private static final Color LEGACY_WINNER = new Color(0x00ff00ff);
    private static final Color LEGACY_LOSER = new Color(0xff0000ff);

    private final int detectedRefreshRate;
    private final Star[] stars = new Star[STAR_COUNT];
    private final Seat[] seats = new Seat[SEAT_COUNT];
    private final float[] thinkDurations = new float[ACTIONS.length];
    private final RandomXS128 thinkRandom = new RandomXS128(0x5EEDC0DEL);
    private ChipFlight[] flights;
    private final float[] frameSamples = new float[FRAME_SAMPLE_COUNT];
    private final float[] frameScratch = new float[FRAME_SAMPLE_COUNT];
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private ShaderProgram roundedCardShader;

    private BitmapFont uiFont;
    private BitmapFont smallFont;
    private BitmapFont playerNameFont;
    private BitmapFont stackFont;
    private BitmapFont actionFont;
    private BitmapFont seatActionFont;

    private Texture logo;
    private Texture feltTexture;
    private Texture avatarDefault;
    private Texture avatarBot;
    private Texture dealerChip;
    private Texture smallBlindChip;
    private Texture bigBlindChip;
    private Texture cardBack;
    private Texture[] flyingChips;
    private Texture pot;
    private Texture[][] communityHands;
    private Texture[][][] holeCardHands;
    private GifTextureAnimation shuffleGif;
    private GifTextureAnimation allInGif;

    private Sound shuffleSound;
    private Sound dealSound;
    private Sound uncoverSound;
    private Sound checkSound;
    private Sound callSound;
    private Sound betSound;
    private Sound foldSound;
    private Sound allInSound;
    private Sound showdownSound;

    private float tableCenterX;
    private float tableCenterY;
    private float dealerSourceX;
    private float dealerSourceY;
    private float potCenterX;
    private float potCenterY;
    private int lastPotValue = -1;
    private String potText = "BOTE: 0";

    private float totalTime;
    private float sceneTime;
    private float statsClock;
    private float burstClock = -10f;
    private float previousSoundTime = -1f;
    private float shuffleAudioStopTime;
    private long shuffleSoundId = -1L;
    private int frameCursor;
    private int frameCount;
    private boolean intro = true;
    private String statsText = "Midiendo frame pacing...";

    public CoronaPokerGdxDemo(int detectedRefreshRate) {
        this.detectedRefreshRate = detectedRefreshRate;
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

        logo = texture("images/corona_poker_splash.png");
        feltTexture = texture("images/tapete_verde.jpg");
        feltTexture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        avatarDefault = texture("images/avatar_default.png");
        avatarBot = texture("images/avatar_bot.png");
        dealerChip = texture("images/dealer.png");
        smallBlindChip = texture("images/sb.png");
        bigBlindChip = texture("images/bb.png");
        cardBack = cardTexture("images/decks/goliat/hq/trasera.jpg");
        flyingChips = new Texture[]{
            createChipTexture(new Color(0xd72d3bff), new Color(0x7f101bff)),
            createChipTexture(new Color(0x247ee8ff), new Color(0x10458fff)),
            createChipTexture(new Color(0x20a96bff), new Color(0x0d6840ff)),
            createChipTexture(new Color(0xe2a72fff), new Color(0x936312ff))
        };
        pot = texture("images/pot.png");
        communityHands = new Texture[][]{
            hqCards("A_P.jpg", "K_D.jpg", "8_C.jpg", "4_T.jpg", "2_P.jpg"),
            hqCards("Q_C.jpg", "10_D.jpg", "7_P.jpg", "5_C.jpg", "3_T.jpg")
        };
        holeCardHands = new Texture[][][]{
            {
                hqCards("J_P.jpg", "Q_P.jpg"),
                hqCards("3_C.jpg", "7_D.jpg"),
                hqCards("A_D.jpg", "A_C.jpg"),
                hqCards("5_T.jpg", "6_T.jpg"),
                hqCards("9_P.jpg", "10_P.jpg"),
                hqCards("Q_D.jpg", "J_D.jpg"),
                hqCards("K_C.jpg", "K_T.jpg"),
                hqCards("2_C.jpg", "2_D.jpg"),
                hqCards("8_D.jpg", "8_T.jpg"),
                hqCards("4_C.jpg", "4_D.jpg")
            },
            {
                hqCards("A_T.jpg", "K_T.jpg"),
                hqCards("A_D.jpg", "A_P.jpg"),
                hqCards("Q_D.jpg", "Q_T.jpg"),
                hqCards("10_C.jpg", "10_T.jpg"),
                hqCards("7_C.jpg", "7_D.jpg"),
                hqCards("5_D.jpg", "5_T.jpg"),
                hqCards("3_C.jpg", "3_D.jpg"),
                hqCards("A_C.jpg", "Q_P.jpg"),
                hqCards("J_T.jpg", "9_T.jpg"),
                hqCards("K_C.jpg", "K_D.jpg")
            }
        };
        shuffleGif = gif("images/decks/goliat/gif/shuffle.gif", 960);
        shuffleAudioStopTime = shuffleGif.frameStartSeconds(SHUFFLE_AUDIO_STOP_FRAME);
        System.out.printf("Shuffle audio cutoff: frame %d at %.0f ms%n",
                SHUFFLE_AUDIO_STOP_FRAME, shuffleAudioStopTime * 1000f);
        allInGif = gif("cinematics/allin/rounders.gif", 563);
        shuffleSound = sound("sounds/misc/shuffle.wav");
        dealSound = sound("sounds/misc/deal.wav");
        uncoverSound = sound("sounds/misc/uncover.wav");
        checkSound = sound("sounds/misc/check.wav");
        callSound = sound("sounds/misc/call.wav");
        betSound = sound("sounds/misc/bet.wav");
        foldSound = sound("sounds/misc/fold.wav");
        allInSound = sound("sounds/misc/allin.wav");
        showdownSound = sound("sounds/misc/showyourcards.wav");

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        uiFont = font(generator, 31, 1.2f);
        smallFont = font(generator, 21, 0.8f);
        playerNameFont = font(generator, 44, 3.2f);
        playerNameFont.getData().setScale(0.5f);
        stackFont = font(generator, 24, 2.0f);
        actionFont = font(generator, 22, 1.0f);
        seatActionFont = font(generator, 32, 2.2f);
        generator.dispose();

        validateBettingScenario();
        initialiseStars();
        initialiseSeats();
        validateAdaptiveSeatLayouts();
        initialiseFlights();
        randomizeThinkDurations();
        Gdx.input.setCursorCatched(false);
    }

    private static Texture texture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return texture;
    }

    private static Texture cardTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path), true);
        texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        return texture;
    }

    private static Texture[] hqCards(String... names) {
        Texture[] cards = new Texture[names.length];
        for (int i = 0; i < names.length; i++) {
            cards[i] = cardTexture("images/decks/goliat/hq/" + names[i]);
        }
        return cards;
    }

    private static Sound sound(String path) {
        return Gdx.audio.newSound(Gdx.files.internal(path));
    }

    private static GifTextureAnimation gif(String path, int maxWidth) {
        try {
            return GifTextureAnimation.load(path, maxWidth);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load CoronaPoker GIF " + path, ex);
        }
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
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = size;
        parameter.color = Color.WHITE;
        parameter.borderColor = new Color(0x02050ccc);
        parameter.borderWidth = border;
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
        String[] names = {"TONIKELOPE", "NEBULA", "RIVERKING", "LUNA", "MAVERICK",
            "ORION", "PIXEL", "NOVA", "SHARK", "CORONA_BOT"};
        for (int i = 0; i < seats.length; i++) {
            seats[i] = new Seat(names[i], 2500 + i * 375, i);
        }
    }

    /**
     * Distributes occupied seats around the complete table instead of selecting
     * holes from the ten-player layout. Coordinates are normalized, so the same
     * geometry scales to every viewport. The local player always owns the lower
     * center; all rivals are equiangular around the remaining ellipse.
     */
    private static float[][][] createSeatLayouts() {
        float[][][] layouts = new float[SEAT_COUNT + 1][][];
        for (int playerCount = 2; playerCount <= SEAT_COUNT; playerCount++) {
            layouts[playerCount] = createSeatAnchors(playerCount);
        }
        return layouts;
    }

    private static float[][] createSeatAnchors(int playerCount) {
        if (playerCount < 2 || playerCount > SEAT_COUNT) {
            throw new IllegalArgumentException("Numero de jugadores fuera de rango: "
                    + playerCount);
        }
        if (playerCount == SEAT_COUNT) {
            // Preserve the proven ten-player composition. Only the four lateral
            // levels are vertically regularized; HUD/card structure is unchanged.
            return new float[][]{
                {0.500f, 0.185f},
                {0.125f, 0.280f}, {0.024f, 0.530f},
                {0.024f, 0.780f}, {0.250f, 0.890f},
                {0.500f, 0.930f}, {0.750f, 0.890f},
                {0.976f, 0.780f}, {0.976f, 0.530f},
                {0.875f, 0.280f}
            };
        }
        float[][] anchors = new float[playerCount][2];
        anchors[0][0] = 0.50f;
        anchors[0][1] = 0.185f;
        for (int seat = 1; seat < playerCount; seat++) {
            double angle = Math.toRadians(-90d - seat * (360d / playerCount));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            float x = (float) (0.5d + Math.copySign(
                    0.5d * Math.pow(Math.abs(cos), 0.55d), cos));
            float y = (float) (0.54d + Math.copySign(
                    0.48d * Math.pow(Math.abs(sin), 0.85d), sin));
            anchors[seat][0] = MathUtils.clamp(x, 0.024f, 0.976f);
            anchors[seat][1] = MathUtils.clamp(y, 0.145f, 0.93f);
            if (anchors[seat][1] > 0.84f) {
                // Keep upper hands out of the permanent logo and FPS corners.
                anchors[seat][0] = MathUtils.clamp(anchors[seat][0], 0.25f, 0.75f);
            } else if (anchors[seat][1] < 0.25f) {
                anchors[seat][0] = anchors[seat][0] < 0.5f ? 0.125f : 0.875f;
            }
        }
        return anchors;
    }

    private static void validateAdaptiveSeatLayouts() {
        for (int playerCount = 2; playerCount <= SEAT_COUNT; playerCount++) {
            float[][] anchors = SEAT_LAYOUTS[playerCount];
            float minimumDistance = Float.POSITIVE_INFINITY;
            for (int a = 0; a < anchors.length; a++) {
                for (int b = a + 1; b < anchors.length; b++) {
                    float dx = (anchors[a][0] - anchors[b][0]) * BASE_WIDTH;
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
        }
    }

    /**
     * Keeps the cinematic hand honest. The demo must never reach a new street
     * while two live players have different committed amounts, and every
     * CHECK/CALL must be legal against the current street target.
     */
    private static void validateBettingScenario() {
        float[] streetEnds = {
            COMMUNITY_REVEAL[0], COMMUNITY_REVEAL[3],
            COMMUNITY_REVEAL[4], SHOWDOWN_START
        };
        String[] streetNames = {"preflop", "flop", "turn", "river"};
        int[] cumulative = new int[SEAT_COUNT];
        int[] street = new int[SEAT_COUNT];
        boolean[] active = new boolean[SEAT_COUNT];
        Arrays.fill(active, true);
        cumulative[1] = street[1] = 50;
        cumulative[2] = street[2] = 100;
        int target = 100;
        int streetIndex = 0;
        float previousTime = -1f;

        for (ActionEvent action : ACTIONS) {
            if (action.time <= previousTime) {
                throw new IllegalStateException("Acciones fuera de orden en " + action.time);
            }
            while (streetIndex < streetEnds.length
                    && action.time >= streetEnds[streetIndex]) {
                assertStreetClosed(streetNames[streetIndex], cumulative, street, active);
                Arrays.fill(street, 0);
                target = 0;
                streetIndex++;
            }
            if (!active[action.seat]) {
                throw new IllegalStateException("Actua un jugador retirado: asiento "
                        + action.seat + " en " + action.time);
            }

            switch (action.kind) {
                case ACTION_CHECK -> {
                    if (street[action.seat] != target) {
                        throw new IllegalStateException("CHECK ilegal del asiento "
                                + action.seat + " en " + action.time);
                    }
                }
                case ACTION_FOLD -> active[action.seat] = false;
                case ACTION_CALL -> {
                    addContribution(action, cumulative, street);
                    if (street[action.seat] != target) {
                        throw new IllegalStateException("CALL no igualado del asiento "
                                + action.seat + " en " + action.time);
                    }
                }
                case ACTION_BET, ACTION_ALLIN -> {
                    int before = street[action.seat];
                    addContribution(action, cumulative, street);
                    if (street[action.seat] <= target || street[action.seat] <= before) {
                        throw new IllegalStateException("Apuesta no creciente del asiento "
                                + action.seat + " en " + action.time);
                    }
                    target = street[action.seat];
                }
                default -> throw new IllegalStateException("Accion desconocida: " + action.kind);
            }

            int initialStack = 2500 + action.seat * 375;
            if (cumulative[action.seat] > initialStack) {
                throw new IllegalStateException("El asiento " + action.seat
                        + " apuesta mas que su stack");
            }
            previousTime = action.time;
        }

        while (streetIndex < streetEnds.length) {
            assertStreetClosed(streetNames[streetIndex], cumulative, street, active);
            Arrays.fill(street, 0);
            streetIndex++;
        }

        int total = 0;
        for (int contribution : cumulative) {
            total += contribution;
        }
        if (total != 9200) {
            throw new IllegalStateException("Bote final incoherente: " + total + " != 9200");
        }
    }

    private static void addContribution(ActionEvent action,
            int[] cumulative, int[] street) {
        if (action.amount <= 0 || action.chipCount <= 0) {
            throw new IllegalStateException("Apuesta sin importe/fichas en " + action.time);
        }
        cumulative[action.seat] += action.amount;
        street[action.seat] += action.amount;
    }

    private static void assertStreetClosed(String name, int[] cumulative,
            int[] street, boolean[] active) {
        int expectedCumulative = -1;
        int expectedStreet = -1;
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            if (!active[seat]) {
                continue;
            }
            if (expectedCumulative < 0) {
                expectedCumulative = cumulative[seat];
                expectedStreet = street[seat];
            } else if (cumulative[seat] != expectedCumulative
                    || street[seat] != expectedStreet) {
                throw new IllegalStateException("Cierre " + name
                        + " desigual: asiento " + seat + " lleva "
                        + cumulative[seat] + " (calle " + street[seat]
                        + "), esperado " + expectedCumulative
                        + " (calle " + expectedStreet + ")");
            }
        }
    }

    private void initialiseFlights() {
        int count = BLIND_FLIGHT_COUNT;
        for (ActionEvent action : ACTIONS) {
            count += action.chipCount;
        }
        flights = new ChipFlight[count];
        int index = 0;
        // The table starts at zero. SB posts one 50 chip; BB posts two 50
        // chips. Their contribution becomes part of the pot only on impact.
        flights[index++] = new ChipFlight(1, BLIND_POST_START, 0.34f,
                37f, 2, ACTION_BET, 0, 50);
        flights[index++] = new ChipFlight(2, BLIND_POST_START + 0.06f, 0.36f,
                91f, 1, ACTION_BET, 0, 50);
        flights[index++] = new ChipFlight(2, BLIND_POST_START + 0.14f, 0.38f,
                143f, 1, ACTION_BET, 1, 50);
        for (ActionEvent action : ACTIONS) {
            for (int chipIndex = 0; chipIndex < action.chipCount; chipIndex++) {
                int contribution = action.amount / action.chipCount
                        + (chipIndex < action.amount % action.chipCount ? 1 : 0);
                flights[index] = new ChipFlight(action.seat,
                        action.time + CHIP_FLIGHT_DELAY + chipIndex * 0.065f,
                        CHIP_FLIGHT_SECONDS + chipIndex * 0.035f,
                        index * 37f,
                        (action.chipColor + chipIndex) % 4,
                        action.kind,
                        chipIndex,
                        contribution);
                index++;
            }
        }
    }

    private void randomizeThinkDurations() {
        float previousActionTime = BOARD_DEAL_END;
        for (int i = 0; i < ACTIONS.length; i++) {
            float streetReady = BOARD_DEAL_END;
            if (ACTIONS[i].time >= COMMUNITY_REVEAL[0]) {
                streetReady = COMMUNITY_REVEAL[2] + CARD_FLIP_SECONDS;
            }
            if (ACTIONS[i].time >= COMMUNITY_REVEAL[3]) {
                streetReady = COMMUNITY_REVEAL[3] + CARD_FLIP_SECONDS;
            }
            if (ACTIONS[i].time >= COMMUNITY_REVEAL[4]) {
                streetReady = COMMUNITY_REVEAL[4] + CARD_FLIP_SECONDS;
            }
            float available = ACTIONS[i].time
                    - Math.max(previousActionTime, streetReady) - 0.10f;
            float maximum = Math.min(1.35f, Math.max(0.18f, available));
            float minimum = Math.min(0.55f, maximum * 0.70f);
            thinkDurations[i] = minimum
                    + thinkRandom.nextFloat() * (maximum - minimum);
            previousActionTime = ACTIONS[i].time;
        }
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        totalTime += delta;
        sceneTime += delta;
        recordFrame(delta);
        handleInput();
        updateStars(delta);

        ScreenUtils.clear(BACKGROUND_BOTTOM, true);
        viewport.apply();
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        drawBackground();
        if (intro) {
            drawIntro();
            if (sceneTime >= INTRO_SECONDS) {
                intro = false;
                sceneTime = 0f;
                burstClock = 0f;
            }
        } else {
            drawTableScene();
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (intro) {
                intro = false;
                sceneTime = 0f;
            }
            burstClock = 0f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            sceneTime = 0f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            stopShuffleSound();
            intro = true;
            sceneTime = 0f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)
                || (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                && Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            if (Gdx.graphics.isFullscreen()) {
                Gdx.graphics.setWindowedMode(1600, 900);
            } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
        }
        if (!intro && Gdx.input.justTouched()) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            burstClock = 0f;
        }
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
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.rect(0, 0, width, height, FELT_SHADE_BOTTOM, FELT_SHADE_BOTTOM,
                    FELT_SHADE_TOP, FELT_SHADE_TOP);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (Star star : stars) {
                float pulse = 0.32f + 0.28f * MathUtils.sin(totalTime * 1.8f + star.phase);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b, pulse);
                shapes.circle(star.x, star.y, star.size, 10);
            }
            shapes.end();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void drawIntro() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float appear = MathUtils.clamp(sceneTime / 1.1f, 0f, 1f);
        float eased = Interpolation.swingOut.apply(appear);
        float alpha = sceneTime > INTRO_SECONDS - 0.7f
                ? MathUtils.clamp((INTRO_SECONDS - sceneTime) / 0.7f, 0f, 1f) : 1f;

        batch.begin();
        batch.setColor(1f, 1f, 1f, alpha);
        float logoWidth = Math.min(760f, width * 0.48f) * eased;
        float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
        batch.draw(logo, width / 2f - logoWidth / 2f, height / 2f - logoHeight / 2f + 85f,
                logoWidth, logoHeight);

        useRoundedCardShader();
        drawIntroSpinningCard(-1f, width, height, alpha);
        drawIntroSpinningCard(1f, width, height, alpha);
        batch.setShader(null);

        // Five chips spiral in after the cards and settle with a short physical
        // bounce. This gives the splash a second beat without making it long.
        for (int chip = 0; chip < 5; chip++) {
            float raw = MathUtils.clamp(
                    (sceneTime - 1.05f - chip * 0.085f) / 1.35f, 0f, 1f);
            if (raw <= 0f) {
                continue;
            }
            float travel = Interpolation.pow3Out.apply(raw);
            float targetX = width / 2f + (chip - 2f) * 48f;
            float targetY = height * 0.245f;
            float startAngle = chip * MathUtils.PI2 / 5f + 0.45f;
            float startX = width / 2f + MathUtils.cos(startAngle) * width * 0.29f;
            float startY = height * 0.49f + MathUtils.sin(startAngle) * height * 0.19f;
            float x = MathUtils.lerp(startX, targetX, travel);
            float y = MathUtils.lerp(startY, targetY, travel)
                    + MathUtils.sin(raw * MathUtils.PI) * 115f
                    + Math.abs(MathUtils.sin(raw * MathUtils.PI * 3f))
                    * (1f - raw) * 26f;
            float size = 42f * (0.58f + travel * 0.42f);
            float rotation = (1f - travel) * (chip % 2 == 0 ? 900f : -900f);
            Texture chipTexture = flyingChips[chip % flyingChips.length];
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(chipTexture, x - size / 2f, y - size / 2f,
                    size / 2f, size / 2f, size, size,
                    1f, 1f, rotation, 0, 0,
                    chipTexture.getWidth(), chipTexture.getHeight(), false, false);
        }

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawIntroSpinningCard(float side, float width, float height,
            float introAlpha) {
        float delay = side < 0f ? 0.22f : 0.34f;
        float raw = MathUtils.clamp((sceneTime - delay) / 2.15f, 0f, 1f);
        if (raw <= 0f) {
            return;
        }
        float arrival = Interpolation.pow3Out.apply(raw);
        float cardW = 164f;
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float targetX = width * (side < 0f ? 0.17f : 0.83f);
        float targetY = height * 0.48f;
        float startX = side < 0f ? -cardW : width + cardW;
        float startY = height * 0.15f;
        float x = MathUtils.lerp(startX, targetX, arrival);
        float y = MathUtils.lerp(startY, targetY, arrival)
                + MathUtils.sin(raw * MathUtils.PI) * height * 0.18f;
        // Quadratic angular decay produces several fast turns followed by a
        // readable, smooth stop. The height wobble sells the spinning-top feel.
        float remaining = 1f - raw;
        float rotation = side * (24f + remaining * remaining * 1620f);
        float scale = 0.46f + arrival * 0.54f;
        float wobble = 0.76f + 0.24f
                * Math.abs(MathUtils.cos(remaining * MathUtils.PI * 9f));
        batch.setColor(1f, 1f, 1f, introAlpha);
        batch.draw(cardBack, x - cardW / 2f, y - cardH / 2f,
                cardW / 2f, cardH / 2f, cardW, cardH,
                scale, scale * wobble, rotation,
                0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
    }

    private void drawTableScene() {
        updateHandSounds();
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
        drawHandOverlay();
        drawShowdownOverlay();
        drawLocalHud(width, height);
        drawFpsCounter(width, height);

        if (burstClock >= 0f) {
            burstClock += Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
            if (burstClock > 1.2f) {
                burstClock = -10f;
            }
        }
    }

    private void drawTableBranding(float height) {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(logo, 42f, height - 145f, 235f,
                235f * logo.getHeight() / logo.getWidth());
        batch.end();
    }

    private void updateSeatPositions(float width, float height) {
        // Same visual language as CoronaPoker's DynamicTablePanel: seats are
        // pinned to the edges of the felt, not arranged around a casino oval.
        for (int i = 0; i < seats.length; i++) {
            seats[i].x = showcaseSeatAnchor(i, 0) * width;
            seats[i].y = showcaseSeatAnchor(i, 1) * height;
            if (i == 0) {
                seats[i].stackX = seats[i].x + 67f;
                // The lowest chip pixel must remain above the complete HUD,
                // including its frame and shared turn bar.
                seats[i].stackY = Math.max(seats[i].y - 7f,
                        LOCAL_HUD_SAFE_TOP + 14f);
            } else {
                // Restore the original PlayerPod geometry: every rival HUD stays
                // under its avatar and only edge clamping makes it lateral.
                float podCenterX = MathUtils.clamp(seats[i].x,
                        PLAYER_POD_WIDTH / 2f + 8f,
                        width - PLAYER_POD_WIDTH / 2f - 8f);
                seats[i].podX = podCenterX - PLAYER_POD_WIDTH / 2f;
                seats[i].podY = MathUtils.clamp(
                        seats[i].y - PLAYER_POD_HEIGHT - 42f, 8f,
                        height - PLAYER_POD_HEIGHT - 8f);
                seats[i].stackX = seats[i].podX + 36f;
                seats[i].stackY = seats[i].podY + 68f;
            }
            float towardX = tableCenterX - seats[i].x;
            float towardY = tableCenterY - seats[i].y;
            float towardLength = Math.max(1f,
                    (float) Math.sqrt(towardX * towardX + towardY * towardY));
            seats[i].positionX = seats[i].x + towardX / towardLength * 80f;
            seats[i].positionY = seats[i].y + towardY / towardLength * 80f;
            if (i == 0) {
                // The large local hand occupies the inward axis; keep its dealer
                // badge beside the avatar instead of underneath the cards.
                seats[i].positionX = seats[i].x - 82f;
                seats[i].positionY = seats[i].y + 8f;
            }
        }
        Seat dealer = seats[0];
        // Cards originate under the dealer avatar itself. There is no artificial
        // shoe/deck widget on the felt.
        dealerSourceX = dealer.x;
        dealerSourceY = dealer.y;
    }

    private float showcaseSeatAnchor(int seat, int component) {
        if (!isLayoutShowcase()) {
            return SEAT_ANCHORS[seat][component];
        }
        float elapsed = showcaseElapsed() - SHOWCASE_FULL_TABLE_PAUSE;
        if (elapsed < 0f) {
            return SEAT_ANCHORS[seat][component];
        }
        int step = Math.min(SEAT_COUNT - 2,
                Math.max(0, (int) (elapsed / SHOWCASE_STEP_SECONDS)));
        if (step >= SEAT_COUNT - 2) {
            return seat < 2 ? SEAT_LAYOUTS[2][seat][component] : 0.5f;
        }
        int oldCount = SEAT_COUNT - step;
        if (seat >= oldCount) {
            return 0.5f;
        }
        float phase = elapsed - step * SHOWCASE_STEP_SECONDS;
        float oldAnchor = SEAT_LAYOUTS[oldCount][seat][component];
        if (seat == oldCount - 1) {
            float exit = Interpolation.pow2In.apply(MathUtils.clamp(
                    phase / SHOWCASE_EXIT_SECONDS, 0f, 1f));
            float center = component == 0 ? 0.5f : 0.54f;
            float push = component == 0 ? 0.28f : 0.20f;
            return oldAnchor + (oldAnchor - center) * push * exit;
        }
        float reflow = Interpolation.smoother.apply(MathUtils.clamp(
                (phase - SHOWCASE_EXIT_SECONDS) / SHOWCASE_REFLOW_SECONDS, 0f, 1f));
        float newAnchor = SEAT_LAYOUTS[oldCount - 1][seat][component];
        return MathUtils.lerp(oldAnchor, newAnchor, reflow);
    }

    private float seatPresenceAlpha(int seat) {
        if (!isLayoutShowcase()) {
            return 1f;
        }
        float elapsed = showcaseElapsed() - SHOWCASE_FULL_TABLE_PAUSE;
        if (elapsed < 0f) {
            return 1f;
        }
        int step = (int) (elapsed / SHOWCASE_STEP_SECONDS);
        if (step >= SEAT_COUNT - 2) {
            return seat < 2 ? 1f : 0f;
        }
        int oldCount = SEAT_COUNT - step;
        if (seat < oldCount - 1) {
            return 1f;
        }
        if (seat > oldCount - 1) {
            return 0f;
        }
        float phase = elapsed - step * SHOWCASE_STEP_SECONDS;
        return 1f - Interpolation.smooth.apply(MathUtils.clamp(
                phase / SHOWCASE_EXIT_SECONDS, 0f, 1f));
    }

    private int visiblePlayerCount() {
        if (!isLayoutShowcase()) {
            return SEAT_COUNT;
        }
        float elapsed = showcaseElapsed() - SHOWCASE_FULL_TABLE_PAUSE;
        if (elapsed < 0f) {
            return SEAT_COUNT;
        }
        int step = Math.min(SEAT_COUNT - 2,
                Math.max(0, (int) (elapsed / SHOWCASE_STEP_SECONDS)));
        if (step >= SEAT_COUNT - 2) {
            return 2;
        }
        int oldCount = SEAT_COUNT - step;
        float phase = elapsed - step * SHOWCASE_STEP_SECONDS;
        return phase < SHOWCASE_EXIT_SECONDS ? oldCount : oldCount - 1;
    }

    private int winnerSeat() {
        return visiblePlayerCount() > 2 ? 2 : 1;
    }

    private void drawSeats() {
        ActionEvent thinkingAction = thinkingAction(handTime());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        for (Seat seat : seats) {
            float presence = seatPresenceAlpha(seat.index);
            if (presence <= 0f) {
                continue;
            }
            boolean active = thinkingAction != null && seat.index == thinkingAction.seat;
            boolean folded = isFolded(seat.index, handTime())
                    && handTime() < SHOWDOWN_START;
            boolean settledShowdown = handTime() >= WINNER_START
                    && isShowdownContender(seat.index);
            seat.updateStack(handTime(), flights);
            if (seat.index != 0) {
                // One component owns name, chips and amount for every rival.
                Color rim = settledShowdown
                        ? (seat.index == winnerSeat() ? LEGACY_WINNER : LEGACY_LOSER)
                        : folded ? BUTTON_LINE : (active ? CYAN : SEAT_RIM);
                String actionLabel = lastActionLabelForSeat(seat.index, handTime());
                shapes.setColor(rim.r, rim.g, rim.b,
                        (settledShowdown ? 1f : folded ? 0.55f : 0.88f) * presence);
                roundedRect(seat.podX - 2f, seat.podY - 2f,
                        PLAYER_POD_WIDTH + 4f, PLAYER_POD_HEIGHT + 4f, 14f);
                shapes.setColor(0.015f, 0.028f, 0.05f,
                        (folded ? 0.72f : 0.92f) * presence);
                roundedRect(seat.podX, seat.podY,
                        PLAYER_POD_WIDTH, PLAYER_POD_HEIGHT, 12f);
                if (!actionLabel.isEmpty()) {
                    Color lastColor = lastActionColorForSeat(seat.index, handTime());
                    shapes.setColor(lastColor.r, lastColor.g, lastColor.b,
                            (settledShowdown ? 1f : folded ? 0.68f : 0.92f) * presence);
                    roundedRect(seat.podX + 7f, seat.podY + 7f,
                            PLAYER_POD_WIDTH - 14f, 44f, 8f);
                    shapes.setColor(1f, 1f, 1f,
                            (folded ? 0.24f : 0.48f) * presence);
                    shapes.rect(seat.podX + 15f, seat.podY + 47f,
                            PLAYER_POD_WIDTH - 30f, 2f);
                }
                shapes.setColor(rim.r, rim.g, rim.b,
                        (folded ? 0.24f : 0.42f) * presence);
                shapes.rect(seat.podX + 12f, seat.podY + 55f,
                        PLAYER_POD_WIDTH - 24f, 2f);
                shapes.rect(seat.podX + 12f, seat.podY + 89f,
                        PLAYER_POD_WIDTH - 24f, 2f);
                shapes.rect(seat.podX + 198f, seat.podY + 61f, 2f, 24f);
            }
            if (active) {
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                        (0.18f + 0.12f * MathUtils.sin(totalTime * 4f)) * presence);
                shapes.circle(seat.x, seat.y, AVATAR_ACTIVE_RADIUS, 48);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            shapes.setColor(PANEL.r, PANEL.g, PANEL.b, PANEL.a * presence);
            shapes.circle(seat.x, seat.y, AVATAR_OUTER_RADIUS, 48);
            Color avatarRim = settledShowdown
                    ? (seat.index == winnerSeat() ? LEGACY_WINNER : LEGACY_LOSER)
                    : folded ? BUTTON_LINE : (active ? CYAN : SEAT_RIM);
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
            Texture avatar = seat.index == 0 ? avatarDefault : avatarBot;
            boolean folded = isFolded(seat.index, handTime())
                    && handTime() < SHOWDOWN_START;
            Color avatarTint = folded ? FOLDED_AVATAR : Color.WHITE;
            batch.setColor(avatarTint.r, avatarTint.g, avatarTint.b,
                    avatarTint.a * presence);
            batch.draw(avatar, seat.x - AVATAR_SIZE / 2f,
                    seat.y - AVATAR_SIZE / 2f, AVATAR_SIZE, AVATAR_SIZE);
            batch.setColor(1f, 1f, 1f, presence);
            Texture position = seat.index == 0 ? dealerChip
                    : seat.index == 1 ? smallBlindChip
                    : seat.index == 2 ? bigBlindChip : null;
            if (position != null) {
                float positionProgress = positionChipProgress(seat.index, handTime());
                if (positionProgress > 0f) {
                    float positionEase = Interpolation.pow3Out.apply(positionProgress);
                    float positionX = positionChipX(seat, positionEase);
                    float positionY = positionChipY(seat, positionEase);
                    float positionSize = POSITION_CHIP_SIZE
                            * (0.62f + positionEase * 0.38f);
                    batch.draw(position, positionX - positionSize / 2f,
                            positionY - positionSize / 2f,
                            positionSize / 2f, positionSize / 2f,
                            positionSize, positionSize, 1f, 1f,
                            (1f - positionEase) * (seat.index - 1) * 320f,
                            0, 0, position.getWidth(), position.getHeight(), false, false);
                }
            }
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
                        seat.podX + 68f, seat.podY + 93f,
                        PLAYER_POD_WIDTH - 78f, 23f,
                        folded ? Color.GRAY : Color.WHITE, presence);
                String actionLabel = lastActionLabelForSeat(seat.index, handTime());
                if (!actionLabel.isEmpty()) {
                    drawFittedCenteredInBox(seatActionFont, actionLabel,
                            seat.podX + 16f, seat.podY + 13f,
                            PLAYER_POD_WIDTH - 32f, 32f,
                            lastActionTextColorForSeat(seat.index, handTime()), presence);
                }
                drawFittedCenteredInBox(stackFont, seat.stackText,
                        seat.podX + 68f, seat.podY + 62f, 124f, 23f,
                        folded ? Color.GRAY : STACK_GREEN, presence);
                drawFittedCenteredInBox(stackFont, seat.investedText,
                        seat.podX + 202f, seat.podY + 62f, 76f, 23f,
                        folded ? Color.GRAY : POT_GOLD, presence);
            }
        }
        batch.end();
    }

    private void drawHoleCards(boolean foregroundRevealFlights) {
        float time = handTime();
        if (time < DEAL_START) {
            return;
        }
        float localFold = time >= SHOWDOWN_START ? 0f : localFoldProgress(time);
        float localSwapRaw = localHandNeedsSwap() ? MathUtils.clamp(
                (time - localSwapStart()) / LOCAL_SWAP_SECONDS, 0f, 1f) : 0f;
        float localSwap = Interpolation.smoother.apply(localSwapRaw);
        float localSwapArc = MathUtils.sin(localSwap * MathUtils.PI);
        float cardW = RIVAL_HOLE_CARD_WIDTH;
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        batch.begin();
        for (Seat seat : seats) {
            float presence = seatPresenceAlpha(seat.index);
            if (presence <= 0f) {
                continue;
            }
            if (seat.index != 0 && isFolded(seat.index, time)
                    && time < SHOWDOWN_START) {
                continue;
            }
            float seatCardW = seat.index == 0 ? LOCAL_HOLE_CARD_WIDTH : cardW;
            float seatCardH = seatCardW * cardBack.getHeight() / cardBack.getWidth();
            float towardX = tableCenterX - seat.x;
            float towardY = tableCenterY - seat.y;
            float length = Math.max(1f, (float) Math.sqrt(towardX * towardX + towardY * towardY));
            towardX /= length;
            towardY /= length;
            float sideX = -towardY;
            float sideY = towardX;
            float hiddenDistance = seat.index == 0 ? 100f : 78f;
            float shownDistance = seat.index == 0 ? 110f : 185f;
            float hiddenCenterX = seat.x + towardX * hiddenDistance;
            float hiddenCenterY = seat.y + towardY * hiddenDistance;
            float hiddenFanX = sideX;
            float hiddenFanY = sideY;
            float hiddenSideDistance = seat.index == 0 ? 54f : 18f;
            float shownCenterX = seat.x + towardX * shownDistance;
            float shownCenterY = seat.y + towardY * shownDistance;
            float shownFanX = sideX;
            float shownFanY = sideY;
            float revealedSideDistance = seat.index == 0 ? 82f : 48f;
            if (seat.index != 0 && isShowdownContender(seat.index)) {
                // The final composition follows the actual pod alignment. Edge
                // pods keep both cards fanned beneath the HUD on the free side;
                // centered pods place one card at either side of the avatar.
                float podCenterX = seat.podX + PLAYER_POD_WIDTH / 2f;
                float avatarOffset = seat.x - podCenterX;
                boolean lateralAvatar = Math.abs(avatarOffset) > 32f;
                float freeSide = avatarOffset < -32f ? 1f
                        : avatarOffset > 32f ? -1f : 0f;
                // Face-down cards already form the same upright V used by every
                // seat. Centered avatars keep one card at each side; lateral
                // avatars tuck that V into the available side and open it on show.
                hiddenCenterX = seat.x + freeSide * (lateralAvatar ? 80f : 0f);
                hiddenCenterY = seat.y + (lateralAvatar ? 4f : 48f);
                hiddenFanX = 1f;
                hiddenFanY = 0f;
                hiddenSideDistance = lateralAvatar ? 34f : 62f;
                if (avatarOffset < -32f) {
                    shownCenterX = seat.x + 125f;
                } else if (avatarOffset > 32f) {
                    shownCenterX = seat.x - 125f;
                } else {
                    shownCenterX = seat.x;
                    revealedSideDistance = AVATAR_OUTER_RADIUS + 58f;
                }
                // The pod masks only the lower card area. Ranks and suits stay
                // above it, while the overlap makes ownership unmistakable.
                // The complete card rectangle, not just its center, must clear
                // the local action HUD. This is what the previous centre-only
                // spacing check failed to guarantee for NEBULA/CORONA_BOT.
                float revealVerticalOffset = lateralAvatar ? 4f : 48f;
                shownCenterY = MathUtils.clamp(seat.y + revealVerticalOffset,
                        LOCAL_HUD_SAFE_TOP + seatCardH / 2f + RIVAL_REVEAL_HUD_GAP,
                        viewport.getWorldHeight() - seatCardH / 2f
                                - RIVAL_REVEAL_TOP_MARGIN);
                shownFanX = 1f;
                shownFanY = 0f;
            }
            for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
                // The small blind (left of the dealer) receives the first card.
                // A complete clockwise round finishes before the second starts.
                int dealOrder = (seat.index + SEAT_COUNT - 1) % SEAT_COUNT;
                int dealTurn = cardIndex * SEAT_COUNT + dealOrder;
                float start = DEAL_START + dealTurn * DEAL_CARD_GAP;
                float progress = MathUtils.clamp((time - start) / DEAL_CARD_SECONDS, 0f, 1f);
                if (progress <= 0f) {
                    continue;
                }
                float eased = Interpolation.pow2Out.apply(progress);
                Texture face = holeCardHands[demoHandIndex()][seat.index][cardIndex];
                float revealStart = showdownRevealStart(seat.index, cardIndex);
                boolean revealing = face != null && time >= revealStart;
                float reveal = revealing ? MathUtils.clamp(
                        (time - revealStart) / CARD_FLIP_SECONDS, 0f, 1f) : 0f;
                boolean airborneReveal = seat.index != 0 && revealing
                        && reveal > 0f && reveal < 1f;
                if (airborneReveal != foregroundRevealFlights) {
                    continue;
                }
                float revealMotion = Interpolation.smooth.apply(reveal);
                float handCenterX = seat.index == 0 ? shownCenterX
                        : MathUtils.lerp(hiddenCenterX, shownCenterX, revealMotion);
                float handCenterY = seat.index == 0 ? shownCenterY
                        : MathUtils.lerp(hiddenCenterY, shownCenterY, revealMotion);
                if (seat.index != 0 && airborneReveal) {
                    // A short inward hop makes the reveal a flight, rather than
                    // a flat UI translation, before the card settles behind HUD.
                    float revealArc = MathUtils.sin(reveal * MathUtils.PI);
                    handCenterX += towardX * revealArc * 56f;
                    handCenterY += towardY * revealArc * 56f + revealArc * 24f;
                }
                float fanX = MathUtils.lerp(hiddenFanX, shownFanX, revealMotion);
                float fanY = MathUtils.lerp(hiddenFanY, shownFanY, revealMotion);
                float fanLength = Math.max(0.001f,
                        (float) Math.sqrt(fanX * fanX + fanY * fanY));
                fanX /= fanLength;
                fanY /= fanLength;
                // Rivals keep a tight pair tucked under their avatar. The pair
                // only travels out and opens when the player reveals it.
                float normalSideDistance = hiddenSideDistance;
                float sideDistance = MathUtils.lerp(
                        normalSideDistance, revealedSideDistance, revealMotion);
                float sideDirection = cardIndex == 0 ? -1f : 1f;
                if (seat.index == 0) {
                    // Deal order is literal: first card lands left, second
                    // right. Only a hand whose second rank is higher swaps.
                    sideDirection = cardIndex == 0 ? 1f : -1f;
                    sideDirection = MathUtils.lerp(
                            sideDirection, -sideDirection, localSwap);
                }
                float side = sideDirection * sideDistance;
                float targetX = handCenterX + fanX * side;
                float targetY = handCenterY + fanY * side;
                if (seat.index == 0 && localSwapRaw > 0f && localSwapRaw < 1f) {
                    // Two depth lanes make the crossover readable: the Q passes
                    // in front while the J travels behind it. Both cards keep
                    // their exact dimensions throughout the animation.
                    float lane = cardIndex == 0 ? -30f : 46f;
                    targetX += towardX * localSwapArc * lane;
                    targetY += towardY * localSwapArc * lane;
                }
                float sourceX = dealerSourceX;
                float sourceY = dealerSourceY;
                float controlX = (sourceX + targetX) * 0.5f + fanX * 128f;
                float controlY = (sourceY + targetY) * 0.5f + fanY * 128f + 62f;
                float x = bezier(sourceX, controlX, targetX, eased);
                float y = bezier(sourceY, controlY, targetY, eased);
                float launchRotation = (dealTurn & 1) == 0 ? -26f : 26f;
                float restingRotation = cardIndex == 0 ? 7f : -7f;
                if (seat.index == 0) {
                    // Local cards form a true V: upper corners open outwards
                    // while the lower inner corners stay close together.
                    restingRotation = cardIndex == 0
                            ? LOCAL_CARD_FAN_ANGLE : -LOCAL_CARD_FAN_ANGLE;
                    restingRotation = MathUtils.lerp(
                            restingRotation, -restingRotation, localSwap);
                } else {
                    // Both final layouts read as a compact V. The second card
                    // is drawn last, but the first card's upper index remains free.
                    float revealedRotation = cardIndex == 0 ? 7f : -7f;
                    restingRotation = MathUtils.lerp(
                            restingRotation, revealedRotation, revealMotion);
                }
                float rotation = MathUtils.lerp(launchRotation, restingRotation, eased);
                float scale = 0.82f + eased * 0.18f;
                // The perspective shader maps the visible card into the middle
                // 2/3 of its canvas. A 1.5x canvas exactly cancels that crop, so
                // the apparent size before and after reveal is pixel-identical.
                float shaderCanvas = revealing ? 1.5f : 1f;
                float renderW = seatCardW * shaderCanvas;
                float renderH = seatCardH * shaderCanvas;
                if (revealing) {
                    usePerspectiveCardShader(face, reveal * MathUtils.PI,
                            seatCardH / seatCardW);
                } else {
                    useRoundedCardShader();
                }
                if (seat.index == 0) {
                    float disabled = Interpolation.smooth.apply(localFold);
                    float tint = MathUtils.lerp(1f, 0.34f, disabled);
                    float alpha = eased * MathUtils.lerp(1f, 0.52f, disabled) * presence;
                    batch.setColor(tint, tint, tint, alpha);
                } else {
                    batch.setColor(1f, 1f, 1f, eased * presence);
                }
                batch.draw(cardBack, x - renderW / 2f, y - renderH / 2f,
                        renderW / 2f, renderH / 2f, renderW, renderH, scale, scale, rotation,
                        0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
            }
        }
        batch.setColor(Color.WHITE);
        batch.setShader(null);
        batch.end();
    }

    private void drawCardsAndPot(float cx, float cy, float tableWidth) {
        Texture[] communityCards = communityHands[demoHandIndex()];
        float cardW = Math.min(COMMUNITY_CARD_MAX_WIDTH, tableWidth / 10f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = cx - gap * 2f - cardW / 2f;
        float cardY = cy - cardH * 0.36f;
        float canvasW = cardW * 1.5f;
        float canvasH = cardH * 1.5f;

        batch.begin();
        for (int i = 0; i < communityCards.length; i++) {
            float dealStart = BOARD_DEAL_START + i * BOARD_CARD_GAP;
            float dealProgress = MathUtils.clamp(
                    (handTime() - dealStart) / DEAL_CARD_SECONDS, 0f, 1f);
            if (dealProgress <= 0f) {
                continue;
            }
            float dealEase = Interpolation.pow2Out.apply(dealProgress);
            float targetX = firstX + i * gap + cardW / 2f;
            float targetY = cardY + cardH / 2f;
            float controlX = (dealerSourceX + targetX) * 0.5f + (i - 2f) * 42f;
            float controlY = Math.max(dealerSourceY, targetY) + 150f;
            float x = bezier(dealerSourceX, controlX, targetX, dealEase);
            float y = bezier(dealerSourceY, controlY, targetY, dealEase);
            float rotation = MathUtils.lerp(-20f + i * 10f, 0f, dealEase);
            float reveal = MathUtils.clamp(
                    (handTime() - COMMUNITY_REVEAL[i]) / CARD_FLIP_SECONDS, 0f, 1f);
            boolean revealing = handTime() >= COMMUNITY_REVEAL[i];
            float renderW = revealing ? canvasW : cardW;
            float renderH = revealing ? canvasH : cardH;
            if (revealing) {
                usePerspectiveCardShader(communityCards[i], reveal * MathUtils.PI, cardH / cardW);
            } else {
                useRoundedCardShader();
            }
            batch.setColor(Color.WHITE);
            batch.draw(cardBack, x - renderW / 2f, y - renderH / 2f,
                    renderW / 2f, renderH / 2f, renderW, renderH,
                    1f, 1f, rotation, 0, 0,
                    cardBack.getWidth(), cardBack.getHeight(), false, false);
        }
        batch.setShader(null);

        float payoutFade = MathUtils.clamp(
                (handTime() - WINNER_START) / (PAYOUT_COMPLETE - WINNER_START), 0f, 1f);
        float pulse = 1f + MathUtils.sin(totalTime * 3.3f) * 0.035f;
        float basePotW = 76f;
        float basePotH = basePotW * pot.getHeight() / pot.getWidth();
        float potW = basePotW * pulse;
        float potH = basePotH * pulse;
        int currentPot = potAt(handTime());
        if (currentPot != lastPotValue) {
            lastPotValue = currentPot;
            potText = String.format("BOTE: %,d", currentPot);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        float alpha = 1f - payoutFade;
        float panelWidth = 390f;
        float panelHeight = POT_PANEL_HEIGHT;
        float panelX = potCenterX - panelWidth / 2f;
        float panelY = potCenterY - panelHeight / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
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
    }

    private void useRoundedCardShader() {
        configureCardShader(cardBack, 0f, false, cardBack.getHeight() / (float) cardBack.getWidth());
    }

    private void usePerspectiveCardShader(Texture front, float angle, float aspect) {
        configureCardShader(front, angle, true, aspect);
    }

    private void configureCardShader(Texture front, float angle, boolean perspective, float aspect) {
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
    }

    private float handTime() {
        return isLayoutShowcase() ? SHOWCASE_TABLE_TIME : sceneTime % HAND_SECONDS;
    }

    private int demoHandIndex() {
        return isLayoutShowcase() ? DEMO_HAND_COUNT - 1
                : ((int) (sceneTime / HAND_SECONDS)) % DEMO_HAND_COUNT;
    }

    private boolean isLayoutShowcase() {
        return sceneTime >= NORMAL_DEMO_SECONDS;
    }

    private float showcaseElapsed() {
        return Math.max(0f, sceneTime - NORMAL_DEMO_SECONDS);
    }

    private void updateHandSounds() {
        if (isLayoutShowcase()) {
            stopShuffleSound();
            previousSoundTime = SHOWCASE_TABLE_TIME;
            return;
        }
        float current = handTime();
        float previous = previousSoundTime;
        if (previous < 0f) {
            stopShuffleSound();
            previous = -0.001f;
        } else if (current < previous) {
            stopShuffleSound();
            randomizeThinkDurations();
            previous = -0.001f;
        }

        if (crossed(previous, current, 0f)) {
            shuffleSoundId = shuffleSound.play(0.62f, 1f, 0f);
        }
        if (crossed(previous, current, shuffleAudioStopTime)) {
            stopShuffleSound();
        }

        for (int turn = 0; turn < SEAT_COUNT * 2; turn++) {
            float cue = DEAL_START + turn * DEAL_CARD_GAP;
            if (crossed(previous, current, cue)) {
                play(dealSound, 0.30f, 0.96f + (turn % 3) * 0.025f);
            }
        }
        for (int card = 0; card < communityHands[demoHandIndex()].length; card++) {
            float cue = BOARD_DEAL_START + card * BOARD_CARD_GAP;
            if (crossed(previous, current, cue)) {
                play(dealSound, 0.34f, 0.94f + card * 0.018f);
            }
        }

        for (ActionEvent action : ACTIONS) {
            if (crossed(previous, current, action.time)) {
                switch (action.kind) {
                    case ACTION_CHECK -> play(checkSound, 0.58f, 1f);
                    case ACTION_FOLD -> play(foldSound, 0.58f, 1f);
                    case ACTION_ALLIN -> play(allInSound, 0.74f, 1f);
                    default -> {
                    }
                }
            }
        }
        for (ChipFlight flight : flights) {
            float landingTime = flight.startTime + flight.duration;
            if (crossed(previous, current, landingTime)) {
                Sound landingSound = flight.actionKind == ACTION_CALL ? callSound : betSound;
                float volume = flight.chipIndex == 0 ? 0.40f : 0.20f;
                float pitch = 0.92f + (flight.chipColor % 4) * 0.055f;
                play(landingSound, volume, pitch);
            }
        }

        for (float reveal : COMMUNITY_REVEAL) {
            if (crossed(previous, current, reveal)) {
                play(uncoverSound, 0.48f, 1f);
            }
        }
        for (int seatIndex : SHOWDOWN_SEATS) {
            for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
                if (crossed(previous, current,
                        showdownRevealStart(seatIndex, cardIndex))) {
                    play(uncoverSound, 0.54f, 1f + cardIndex * 0.035f);
                }
            }
        }
        for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
            if (crossed(previous, current, showdownRevealStart(0, cardIndex))) {
                play(uncoverSound, 0.45f, 1f + cardIndex * 0.035f);
            }
        }
        if (crossed(previous, current, SHOWDOWN_START)) {
            play(showdownSound, 0.64f, 1f);
        }
        previousSoundTime = current;
    }

    private static boolean crossed(float previous, float current, float cue) {
        return previous < cue && current >= cue;
    }

    private static float positionChipProgress(int seatIndex, float time) {
        float start = POSITION_CHIP_START + seatIndex * POSITION_CHIP_STAGGER;
        return MathUtils.clamp((time - start) / POSITION_CHIP_SECONDS, 0f, 1f);
    }

    private float positionChipX(Seat seat, float progress) {
        float sourceX = tableCenterX + (seat.index - 1) * 34f;
        float controlX = tableCenterX + (seat.index - 1) * 96f;
        return bezier(sourceX, controlX, seat.positionX, progress);
    }

    private float positionChipY(Seat seat, float progress) {
        float sourceY = tableCenterY + 48f;
        float controlY = tableCenterY + 172f;
        return bezier(sourceY, controlY, seat.positionY, progress);
    }

    private static void play(Sound sound, float volume, float pitch) {
        sound.play(volume, pitch, 0f);
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

    private int potAt(float time) {
        if (time >= PAYOUT_COMPLETE) {
            return 0;
        }
        int value = 0;
        for (ChipFlight flight : flights) {
            if (time >= flight.startTime + flight.duration) {
                value += flight.potContribution;
            }
        }
        return value;
    }

    private boolean isFolded(int seat, float time) {
        for (ActionEvent action : ACTIONS) {
            if (action.seat == seat && action.kind == ACTION_FOLD
                    && time >= action.time + 0.9f) {
                return true;
            }
        }
        return time >= SHOWDOWN_START && !isShowdownContender(seat);
    }

    private static boolean isShowdownContender(int seat) {
        // Showcase mode: every occupied seat reveals so both demo hands can be
        // evaluated as a complete ten-player layout. The real game will feed
        // this from its authoritative showdown participant set.
        return seat >= 0 && seat < SEAT_COUNT;
    }

    private static float showdownRevealStart(int seat, int cardIndex) {
        if (seat == 0) {
            int localDealOrder = SEAT_COUNT - 1;
            float localDealStart = DEAL_START
                    + (cardIndex * SEAT_COUNT + localDealOrder) * DEAL_CARD_GAP;
            return localDealStart + DEAL_CARD_SECONDS;
        }
        return SHOWDOWN_START + 0.12f + (seat - 1) * 0.12f
                + cardIndex * 0.11f;
    }

    private static float localSwapStart() {
        // Sorting starts only after both local cards have landed and finished
        // turning face-up. In the demo J/Q needs one swap: Q finishes left.
        return showdownRevealStart(0, 1) + CARD_FLIP_SECONDS + LOCAL_SWAP_DELAY;
    }

    private boolean localHandNeedsSwap() {
        int[] ranks = LOCAL_CARD_RANKS[demoHandIndex()];
        return ranks[0] < ranks[1];
    }

    private static float localFoldStart() {
        for (ActionEvent action : ACTIONS) {
            if (action.seat == 0 && action.kind == ACTION_FOLD) {
                return action.time;
            }
        }
        return Float.POSITIVE_INFINITY;
    }

    private static float localFoldProgress(float time) {
        float start = localFoldStart();
        return start == Float.POSITIVE_INFINITY
                ? 0f : MathUtils.clamp((time - start) / 0.30f, 0f, 1f);
    }

    private ActionEvent currentAllInAction(float time) {
        for (ActionEvent action : ACTIONS) {
            if (action.kind == ACTION_ALLIN
                    && time >= action.time
                    && time < action.time + allInGif.durationSeconds()) {
                return action;
            }
        }
        return null;
    }

    private static ActionEvent lastActionForSeat(int seat, float time) {
        ActionEvent latest = null;
        for (ActionEvent action : ACTIONS) {
            if (action.time > time) {
                break;
            }
            if (action.seat == seat) {
                latest = action;
            }
        }
        return latest;
    }

    private String lastActionLabelForSeat(int seat, float time) {
        if (time >= SHOWDOWN_START && isShowdownContender(seat)) {
            return SHOWDOWN_RESULTS[demoHandIndex()][seat];
        }
        ActionEvent latest = lastActionForSeat(seat, time);
        if (latest != null) {
            return latest.label;
        }
        if (time >= BLIND_POST_START) {
            if (seat == 1) {
                return "SB 50";
            }
            if (seat == 2) {
                return "BB 100";
            }
        }
        return "";
    }

    private Color lastActionColorForSeat(int seat, float time) {
        if (time >= SHOWDOWN_START && isShowdownContender(seat)) {
            if (time >= WINNER_START) {
                return seat == winnerSeat() ? LEGACY_WINNER : LEGACY_LOSER;
            }
            return LEGACY_SHOW;
        }
        ActionEvent latest = lastActionForSeat(seat, time);
        return latest == null ? LEGACY_BET : actionColor(latest);
    }

    private Color lastActionTextColorForSeat(int seat, float time) {
        if (time >= SHOWDOWN_START && isShowdownContender(seat)) {
            return time >= WINNER_START && seat == winnerSeat()
                    ? Color.BLACK : Color.WHITE;
        }
        ActionEvent latest = lastActionForSeat(seat, time);
        return latest == null ? Color.BLACK : actionTextColor(latest);
    }

    private static Color actionColor(ActionEvent action) {
        if (action.kind == ACTION_FOLD) {
            return LEGACY_FOLD;
        }
        if (action.kind == ACTION_CHECK) {
            return LEGACY_CHECK;
        }
        if (action.kind == ACTION_CALL) {
            return LEGACY_CALL;
        }
        if (action.kind == ACTION_ALLIN) {
            return LEGACY_ALL_IN;
        }
        return action.label.startsWith("RESUBE") ? LEGACY_RERAISE : LEGACY_BET;
    }

    private static Color actionTextColor(ActionEvent action) {
        return action.kind == ACTION_CALL
                || (action.kind == ACTION_BET && !action.label.startsWith("RESUBE"))
                ? Color.BLACK : Color.WHITE;
    }

    private ActionEvent thinkingAction(float time) {
        for (int i = 0; i < ACTIONS.length; i++) {
            ActionEvent action = ACTIONS[i];
            float start = action.time - thinkDurations[i];
            if (time >= start && time < action.time) {
                return action;
            }
        }
        return null;
    }

    private float sharedTurnRemaining(float time) {
        for (int i = 0; i < ACTIONS.length; i++) {
            ActionEvent action = ACTIONS[i];
            float start = action.time - thinkDurations[i];
            if (time >= start && time < action.time) {
                return MathUtils.clamp((action.time - time) / thinkDurations[i], 0f, 1f);
            }
        }
        return 0f;
    }

    private void drawHandOverlay() {
        float time = handTime();
        float worldWidth = viewport.getWorldWidth();
        float worldHeight = viewport.getWorldHeight();
        if (time < SHUFFLE_END) {
            // Native physical 1:1 size. Convert the GIF's source pixels to world
            // units so a 2560x1440 monitor does not enlarge 960x540 to 1280x720.
            float width = shuffleGif.width() * worldWidth
                    / Gdx.graphics.getBackBufferWidth();
            float height = shuffleGif.height() * worldHeight
                    / Gdx.graphics.getBackBufferHeight();
            float x = tableCenterX - width / 2f;
            float y = tableCenterY - height / 2f;
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(shuffleGif.frameAt(time, true), x, y, width, height);
            batch.end();
            return;
        }
        if (time < DEAL_START) {
            if (time < BLIND_POST_START) {
                float alpha = Interpolation.fade.apply(MathUtils.clamp(
                        (time - POSITION_CHIP_START) / 0.20f, 0f, 1f));
                batch.begin();
                drawCentered(smallFont, "POSICIONES DE MESA", tableCenterX,
                        tableCenterY - 38f, CYAN, alpha);
                batch.end();
            }
            return;
        }

        ActionEvent action = currentAllInAction(time);
        if (action != null) {
            drawAllInCinematic(action, time - action.time, worldWidth, worldHeight);
        }
    }

    private void drawAllInCinematic(ActionEvent action, float elapsed,
            float worldWidth, float worldHeight) {
        // Exact GifAnimationDialog geometry used by CoronaPoker: landscape clips
        // occupy 50% of the parent height, capped to 80% of its width.
        float height = worldHeight * 0.5f;
        float width = allInGif.width() * height / allInGif.height();
        float maxWidth = worldWidth * 0.8f;
        if (width > maxWidth) {
            height *= maxWidth / width;
            width = maxWidth;
        }
        float x = worldWidth / 2f - width / 2f;
        float y = worldHeight / 2f - height / 2f;
        float labelWidth = Math.min(width, 520f);
        float labelX = worldWidth / 2f - labelWidth / 2f;
        float labelY = y - 66f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.34f);
        shapes.rect(0f, 0f, worldWidth, worldHeight);
        shapes.setColor(ORANGE.r, ORANGE.g, ORANGE.b, 0.92f);
        roundedRect(labelX - 2f, labelY - 2f, labelWidth + 4f, 58f, 12f);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.96f);
        roundedRect(labelX, labelY, labelWidth, 54f, 10f);
        shapes.end();
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(allInGif.frameAt(elapsed, false), x, y, width, height);
        drawCentered(uiFont, seats[action.seat].name + "  //  " + action.label,
                worldWidth / 2f, labelY + 37f, ORANGE, 1f);
        batch.end();
    }

    private void drawShowdownOverlay() {
        if (isLayoutShowcase()) {
            return;
        }
        float time = handTime();
        if (time < SHOWDOWN_START) {
            return;
        }

        boolean winnerVisible = time >= WINNER_START;
        if (!winnerVisible) {
            return;
        }
        float winnerProgress = MathUtils.clamp((time - WINNER_START) / 1.2f, 0f, 1f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Seat winner = seats[2];
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int ring = 7; ring >= 1; ring--) {
            float radius = 48f + ring * 16f + MathUtils.sin(totalTime * 4f + ring) * 5f;
            shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                    winnerProgress * (0.018f + (8 - ring) * 0.006f));
            shapes.circle(winner.x, winner.y, radius, 64);
        }
        for (int particle = 0; particle < 72; particle++) {
            float phase = particle * 1.731f;
            float travel = (winnerProgress * 1.4f + particle * 0.019f) % 1f;
            float angle = phase + totalTime * (particle % 2 == 0 ? 0.35f : -0.28f);
            float radius = 55f + Interpolation.circleOut.apply(travel)
                    * (90f + particle % 8 * 18f);
            Color color = particle % 3 == 0 ? CYAN : POT_GOLD;
            shapes.setColor(color.r, color.g, color.b, (1f - travel) * 0.62f);
            shapes.circle(winner.x + MathUtils.cos(angle) * radius,
                    winner.y + MathUtils.sin(angle) * radius,
                    2f + particle % 4, 10);
        }
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.end();

        batch.begin();
        for (int chip = 0; chip < 18; chip++) {
            float raw = (time - WINNER_START - 0.18f - chip * 0.035f) / 1.05f;
            if (raw <= 0f || raw >= 1f) {
                continue;
            }
            float eased = Interpolation.pow2Out.apply(raw);
            // Payout chips land on the player's visible stack, never on the
            // avatar or the revealed ranks/suits.
            float targetX = winner.stackX + (chip % 5 - 2) * 9f;
            float targetY = winner.stackY + 8f + (chip % 4) * 6f;
            float x = bezier(potCenterX, tableCenterX - 210f, targetX, eased);
            float y = bezier(potCenterY, tableCenterY + 330f, targetY, eased);
            float size = 34f + MathUtils.sin(raw * MathUtils.PI) * 9f;
            Texture chipTexture = flyingChips[chip % flyingChips.length];
            batch.setColor(1f, 1f, 1f, MathUtils.clamp((1f - raw) / 0.12f, 0f, 1f));
            batch.draw(chipTexture, x - size / 2f, y - size / 2f,
                    size / 2f, size / 2f, size, size, 1f, 1f,
                    chip * 29f + raw * 240f, 0, 0,
                    chipTexture.getWidth(), chipTexture.getHeight(), false, false);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawChipTrails(float targetX, float targetY) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (ChipFlight flight : flights) {
            Seat from = seats[flight.seat];
            float impactAge = handTime() - flight.startTime - flight.duration;
            if (impactAge >= 0f && impactAge < 0.34f) {
                float impact = impactAge / 0.34f;
                shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                        (1f - impact) * 0.24f);
                shapes.circle(targetX, targetY, 24f + impact * 82f, 36);
            }
            float u = flightProgress(flight);
            if (u < 0f) {
                continue;
            }
            shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, (1f - u) * 0.22f);
            shapes.circle(from.stackX, from.stackY, 18f + u * 28f, 24);
            for (int j = 1; j <= 5; j++) {
                float t = Math.max(0f, u - j * 0.025f);
                float x = bezier(from.stackX,
                        stackControlX(from, targetX, flight), targetX, t);
                float y = bezier(from.stackY,
                        controlY(from.stackY, targetY, flight), targetY, t);
                shapes.setColor(ORANGE.r, ORANGE.g, ORANGE.b, (6 - j) * 0.028f);
                shapes.circle(x, y, 15f - j * 1.7f, 12);
            }
        }
        if (burstClock >= 0f) {
            float progress = MathUtils.clamp(burstClock / 1.1f, 0f, 1f);
            for (int i = 0; i < 48; i++) {
                float a = MathUtils.PI2 * i / 48f + i * 0.17f;
                float r = Interpolation.circleOut.apply(progress) * (95f + (i % 7) * 23f);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b, (1f - progress) * 0.55f);
                shapes.circle(targetX + MathUtils.cos(a) * r,
                        targetY + MathUtils.sin(a) * r, 3f + (i % 3), 10);
            }
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawFlyingChips(float targetX, float targetY) {
        batch.begin();
        for (ChipFlight flight : flights) {
            Seat from = seats[flight.seat];
            float u = flightProgress(flight);
            if (u < 0f) {
                continue;
            }
            // The rendered chip and its landing sound must share the exact same
            // progress clock. Easing this position made the chip look settled
            // before its scheduled collision sound was emitted.
            float travel = u;
            float x = bezier(from.stackX,
                    stackControlX(from, targetX, flight), targetX, travel);
            float y = bezier(from.stackY,
                    controlY(from.stackY, targetY, flight), targetY, travel);
            float landing = Interpolation.pow3In.apply(u);
            float size = (45f + MathUtils.sin(u * MathUtils.PI) * 9f) * (1f - landing * 0.28f);
            // Do not fade before impact: the chip remains tangible until the
            // collision frame, when it joins the pot and its sound is played.
            float alpha = 1f;
            Texture flyingChip = flyingChips[flight.chipColor];
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(flyingChip, x - size / 2f, y - size / 2f, size / 2f, size / 2f,
                    size, size, 1f, 1f, flight.rotation + u * 360f,
                    0, 0, flyingChip.getWidth(), flyingChip.getHeight(), false, false);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private float flightProgress(ChipFlight flight) {
        float raw = handTime() - flight.startTime;
        return raw <= flight.duration ? raw / flight.duration : -1f;
    }

    private float stackControlX(Seat from, float to, ChipFlight flight) {
        float outward = from.stackX < tableCenterX ? -1f : 1f;
        return (from.stackX + to) * 0.5f + outward * 175f
                + MathUtils.cos(flight.rotation) * 35f;
    }

    private static float controlY(float from, float to, ChipFlight flight) {
        // Midpoint-based arc keeps upper-seat stacks inside the viewport while
        // retaining a visible bow for every path into the pot.
        return (from + to) * 0.5f + 120f + MathUtils.sin(flight.rotation) * 45f;
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
        float state = selected ? 0.58f : hover ? 0.46f : enabled ? 0.34f : 0.22f;
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(x + 5f, y - 6f, width, height, 14f);
        shapes.setColor(color.r, color.g, color.b,
                enabled || hover ? 0.90f : 0.62f);
        roundedRect(x, y, width, height, 14f);
        shapes.setColor(0.018f, 0.032f, 0.055f, 0.97f);
        roundedRect(x + 3f, y + 4f, width - 6f, height - 8f, 11f);
        shapes.setColor(color.r, color.g, color.b, state);
        roundedRect(x + 6f, y + 8f, width - 12f, height - 17f, 9f);
        shapes.setColor(1f, 1f, 1f, hover || selected ? 0.22f : 0.09f);
        roundedRect(x + 9f, y + height - 17f, width - 18f, 8f, 4f);
        shapes.setColor(color.r, color.g, color.b, enabled ? 1f : 0.56f);
        shapes.rect(x + 13f, y + 5f, width - 26f, 4f);
    }

    private void drawHudActionContent(String text, float x, float y,
            float width, float height, Color color, float alpha) {
        float iconBayWidth = 50f;
        drawFittedCenteredInBox(actionFont, text,
                x + iconBayWidth + 4f, y + 16f,
                width - iconBayWidth - 14f, height - 32f,
                color, alpha);
    }

    private void drawHudActionBadge(float x, float y, float height,
            Color color, float alpha) {
        float centerX = x + 27f;
        float centerY = y + height / 2f;
        shapes.setColor(0f, 0f, 0f, 0.48f * alpha);
        shapes.circle(centerX + 2f, centerY - 2f, 22f, 32);
        shapes.setColor(color.r, color.g, color.b, 0.82f * alpha);
        shapes.circle(centerX, centerY, 20f, 32);
        shapes.setColor(0.018f, 0.032f, 0.055f, 0.96f * alpha);
        shapes.circle(centerX, centerY, 15f, 32);
    }

    private void drawHudActionIcon(int action, float centerX, float centerY,
            Color color, float alpha) {
        shapes.setColor(color.r, color.g, color.b, alpha);
        switch (action) {
            case ACTION_FOLD -> {
                shapes.rectLine(centerX - 11f, centerY - 11f,
                        centerX + 11f, centerY + 11f, 5f);
                shapes.rectLine(centerX - 11f, centerY + 11f,
                        centerX + 11f, centerY - 11f, 5f);
            }
            case ACTION_CHECK -> {
                shapes.rectLine(centerX - 13f, centerY,
                        centerX - 4f, centerY - 9f, 5f);
                shapes.rectLine(centerX - 4f, centerY - 9f,
                        centerX + 15f, centerY + 12f, 5f);
            }
            case ACTION_BET -> {
                for (int i = 0; i < 3; i++) {
                    roundedRect(centerX - 14f, centerY - 13f + i * 10f,
                            28f, 8f, 4f);
                }
            }
            case ACTION_ALLIN -> {
                roundedRect(centerX - 15f, centerY - 14f, 13f, 8f, 4f);
                roundedRect(centerX + 2f, centerY - 14f, 13f, 8f, 4f);
                shapes.rect(centerX - 2.5f, centerY - 3f, 5f, 16f);
                shapes.triangle(centerX - 10f, centerY + 9f,
                        centerX + 10f, centerY + 9f, centerX, centerY + 20f);
            }
            default -> {
            }
        }
    }

    private void drawSharedTurnBar(float x, float y, float width, float time) {
        float height = 10f;
        float remaining = sharedTurnRemaining(time);
        Color timerColor = remaining > 0.55f ? STACK_GREEN
                : remaining > 0.25f ? POT_GOLD : FOLD_RED;
        shapes.setColor(0f, 0f, 0f, 0.58f);
        roundedRect(x + 2f, y - 2f, width, height, 5f);
        shapes.setColor(0.018f, 0.032f, 0.055f, 0.96f);
        roundedRect(x, y, width, height, 5f);
        if (remaining > 0.002f) {
            float fillWidth = Math.max(height, width * remaining);
            shapes.setColor(timerColor.r, timerColor.g, timerColor.b, 0.96f);
            roundedRect(x, y, fillWidth, height, 5f);
        }
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
        ActionEvent thinking = thinkingAction(handTime());
        boolean localTurn = thinking != null && thinking.seat == 0;
        boolean settledShowdown = handTime() >= WINNER_START
                && isShowdownContender(0);
        String lastLocalActionLabel = lastActionLabelForSeat(0, handTime());
        Color lastLocalActionColor = lastActionColorForSeat(0, handTime());
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
        float contentAlpha = localTurn ? 1f : 0.74f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        float turnPulse = 0.5f + 0.5f * MathUtils.sin(totalTime * 5.2f);
        Color hudFrame = settledShowdown
                ? (winnerSeat() == 0 ? LEGACY_WINNER : LEGACY_LOSER)
                : localTurn ? POT_GOLD : BUTTON_LINE;
        shapes.setColor(hudFrame.r, hudFrame.g, hudFrame.b,
                settledShowdown ? 1f
                        : localTurn ? 0.48f + turnPulse * 0.30f : 0.34f);
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
        if (!lastLocalActionLabel.isEmpty()) {
            shapes.setColor(lastLocalActionColor.r, lastLocalActionColor.g,
                    lastLocalActionColor.b, settledShowdown ? 1f : 0.20f);
            roundedRect(hudX + 14f, hudY + 37f,
                    infoWidth - 28f, 24f, 6f);
        }
        Color hudLine = localTurn ? POT_GOLD : CYAN;
        shapes.setColor(hudLine.r, hudLine.g, hudLine.b, 0.82f);
        shapes.rect(hudX + 16f, hudY + hudHeight - 4f, infoWidth - 32f, 3f);
        shapes.setColor(BUTTON_LINE.r, BUTTON_LINE.g, BUTTON_LINE.b, 0.72f);
        shapes.rect(hudX + 16f, hudY + 34f, infoWidth - 32f, 2f);
        shapes.rect(hudX + 16f, hudY + 63f, infoWidth - 32f, 2f);
        shapes.rect(hudX + infoWidth / 2f, hudY + 13f, 2f, 18f);
        drawHudActionSurface(foldX, actionY, foldWidth, actionHeight,
                FOLD_RED, foldHover, false, localTurn);
        drawHudActionSurface(checkX, actionY, checkWidth, actionHeight,
                CYAN, checkHover, localTurn, localTurn);

        shapes.setColor(0f, 0f, 0f, 0.52f);
        roundedRect(spinnerX + 4f, actionY - 5f, spinnerWidth, actionHeight, 12f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b,
                spinnerHover ? 0.86f : 0.58f);
        roundedRect(spinnerX, actionY, spinnerWidth, actionHeight, 12f);
        shapes.setColor(0.025f, 0.045f, 0.075f, 0.97f);
        roundedRect(spinnerX + 3f, actionY + 5f, spinnerWidth - 6f,
                actionHeight - 9f, 9f);
        shapes.setColor(POT_GOLD.r, POT_GOLD.g, POT_GOLD.b, 0.30f);
        shapes.rect(spinnerX + 41f, actionY + 9f, 2f, actionHeight - 18f);
        shapes.rect(spinnerX + spinnerWidth - 43f, actionY + 9f, 2f, actionHeight - 18f);

        drawHudActionSurface(betX, actionY, betWidth, actionHeight,
                POT_GOLD, betHover, false, localTurn);
        drawHudActionSurface(allInX, actionY, allInWidth, actionHeight,
                ORANGE, allInHover, false, localTurn);
        drawHudActionBadge(foldX, actionY, actionHeight, FOLD_RED, contentAlpha);
        drawHudActionBadge(checkX, actionY, actionHeight, CYAN, contentAlpha);
        drawHudActionBadge(betX, actionY, actionHeight, POT_GOLD, contentAlpha);
        drawHudActionBadge(allInX, actionY, actionHeight, ORANGE, contentAlpha);
        float iconY = actionY + actionHeight / 2f;
        drawHudActionIcon(ACTION_FOLD, foldX + 27f, iconY,
                Color.WHITE, contentAlpha);
        drawHudActionIcon(ACTION_CHECK, checkX + 27f, iconY,
                Color.WHITE, contentAlpha);
        drawHudActionIcon(ACTION_BET, betX + 27f, iconY,
                Color.WHITE, contentAlpha);
        drawHudActionIcon(ACTION_ALLIN, allInX + 27f, iconY,
                Color.WHITE, contentAlpha);
        drawSharedTurnBar(hudX, hudY + hudHeight + 7f, hudWidth, handTime());
        shapes.end();

        batch.begin();
        Seat local = seats[0];
        drawFittedCenteredInBox(localTurn ? actionFont : smallFont,
                localTurn ? "TU TURNO" : "ESPERANDO TURNO",
                hudX + 12f, hudY + 96f, infoWidth - 24f, 24f,
                localTurn ? POT_GOLD : CYAN, 1f);
        drawFittedCenteredInBox(uiFont, "TONIKELOPE",
                hudX + 12f, hudY + 66f, infoWidth - 24f, 28f,
                Color.WHITE, 1f);
        if (!lastLocalActionLabel.isEmpty()) {
            drawFittedCenteredInBox(actionFont, lastLocalActionLabel,
                    hudX + 18f, hudY + 37f, infoWidth - 36f, 25f,
                    lastActionTextColorForSeat(0, handTime()), 1f);
        }
        drawFittedCenteredInBox(stackFont, local.stackText,
                hudX + 12f, hudY + 10f, infoWidth * 0.49f - 14f, 20f,
                STACK_GREEN, 1f);
        drawFittedCenteredInBox(stackFont, local.investedText,
                hudX + infoWidth * 0.52f, hudY + 10f,
                infoWidth * 0.43f, 20f, POT_GOLD, 1f);

        drawHudActionContent(HUD_ACTIONS[0], foldX, actionY,
                foldWidth, actionHeight, Color.WHITE, contentAlpha);
        drawHudActionContent(HUD_ACTIONS[1], checkX, actionY,
                checkWidth, actionHeight, CYAN, contentAlpha);
        drawHudActionContent(HUD_ACTIONS[2], betX, actionY,
                betWidth, actionHeight, POT_GOLD, contentAlpha);
        drawHudActionContent(HUD_ACTIONS[3], allInX, actionY,
                allInWidth, actionHeight, ORANGE, contentAlpha);
        batch.setColor(Color.WHITE);
        drawCentered(smallFont, "APUESTA", spinnerX + spinnerWidth / 2f,
                actionY + 68f, POT_GOLD, 0.92f);
        drawCentered(uiFont, "-   600   +", spinnerX + spinnerWidth / 2f,
                actionY + 40f, Color.WHITE, 1f);
        batch.end();
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
        glyph.setText(font, text);
        float fitX = glyph.width > 0f ? width / glyph.width : 1f;
        float fitY = glyph.height > 0f ? height / glyph.height : 1f;
        float fit = Math.min(1f, Math.min(fitX, fitY));
        if (fit < 1f) {
            data.setScale(originalScaleX * fit, originalScaleY * fit);
            glyph.setText(font, text);
        }
        font.setColor(color.r, color.g, color.b, alpha);
        font.draw(batch, glyph,
                x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f);
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

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        roundedCardShader.dispose();
        uiFont.dispose();
        smallFont.dispose();
        playerNameFont.dispose();
        stackFont.dispose();
        actionFont.dispose();
        seatActionFont.dispose();
        logo.dispose();
        feltTexture.dispose();
        avatarDefault.dispose();
        avatarBot.dispose();
        dealerChip.dispose();
        smallBlindChip.dispose();
        bigBlindChip.dispose();
        cardBack.dispose();
        for (Texture flyingChip : flyingChips) {
            flyingChip.dispose();
        }
        pot.dispose();
        for (Texture[] board : communityHands) {
            for (Texture texture : board) {
                texture.dispose();
            }
        }
        for (Texture[][] hand : holeCardHands) {
            for (Texture[] playerCards : hand) {
                for (Texture texture : playerCards) {
                    texture.dispose();
                }
            }
        }
        shuffleGif.dispose();
        allInGif.dispose();
        shuffleSound.dispose();
        dealSound.dispose();
        uncoverSound.dispose();
        checkSound.dispose();
        callSound.dispose();
        betSound.dispose();
        foldSound.dispose();
        allInSound.dispose();
        showdownSound.dispose();
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
        final int stack;
        String stackText;
        String investedText;
        int displayedStack;
        int displayedInvested;
        final int index;
        float x;
        float y;
        float stackX;
        float stackY;
        float podX;
        float podY;
        float positionX;
        float positionY;

        Seat(String name, int stack, int index) {
            this.name = name;
            this.stack = stack;
            this.displayedStack = stack;
            this.stackText = String.format("%,d", stack);
            this.displayedInvested = 0;
            this.investedText = "0";
            this.index = index;
        }

        void updateStack(float time, ChipFlight[] flights) {
            int current = stack;
            int invested = 0;
            int payout = 0;
            for (ChipFlight flight : flights) {
                payout += flight.potContribution;
                if (flight.seat == index
                        && time >= flight.startTime + flight.duration) {
                    current -= flight.potContribution;
                    invested += flight.potContribution;
                }
            }
            if (time >= PAYOUT_COMPLETE) {
                if (index == 2) {
                    current += payout;
                }
                // Once the pot reaches the winner, no player still has money
                // committed on the felt for the finished hand.
                invested = 0;
            }
            if (current != displayedStack) {
                displayedStack = current;
                stackText = String.format("%,d", current);
            }
            if (invested != displayedInvested) {
                displayedInvested = invested;
                investedText = String.format("%,d", invested);
            }
        }
    }

    private static final class ChipFlight {

        final int seat;
        final float startTime;
        final float duration;
        final float rotation;
        final int chipColor;
        final int actionKind;
        final int chipIndex;
        final int potContribution;

        ChipFlight(int seat, float startTime, float duration, float rotation,
                int chipColor, int actionKind, int chipIndex,
                int potContribution) {
            this.seat = seat;
            this.startTime = startTime;
            this.duration = duration;
            this.rotation = rotation;
            this.chipColor = chipColor;
            this.actionKind = actionKind;
            this.chipIndex = chipIndex;
            this.potContribution = potContribution;
        }
    }

    private static final class ActionEvent {

        final float time;
        final int seat;
        final int kind;
        final String label;
        final int amount;
        final int chipCount;
        final int chipColor;

        ActionEvent(float time, int seat, int kind, String label,
                int amount, int chipCount, int chipColor) {
            this.time = time;
            this.seat = seat;
            this.kind = kind;
            this.label = label;
            this.amount = amount;
            this.chipCount = chipCount;
            this.chipColor = chipColor;
        }
    }
}
