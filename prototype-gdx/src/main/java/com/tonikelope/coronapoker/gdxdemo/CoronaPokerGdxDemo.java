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
    private static final int FRAME_SAMPLE_COUNT = 720;
    private static final float CARD_FLIP_SECONDS = 0.620f;
    private static final float LOCAL_CARD_FAN_ANGLE = 8.5f;
    private static final float LOCAL_SWAP_DELAY = 0.14f;
    private static final float LOCAL_SWAP_SECONDS = 0.68f;
    private static final float HAND_SECONDS = 48.8f;
    private static final float SHUFFLE_END = 1.72f;
    private static final float POSITION_CHIP_START = SHUFFLE_END + 0.04f;
    private static final float POSITION_CHIP_STAGGER = 0.03f;
    private static final float POSITION_CHIP_SECONDS = 0.40f;
    private static final float POSITION_CHIP_SIZE = 64f;
    private static final float AVATAR_SIZE = 72f;
    private static final float AVATAR_ACTIVE_RADIUS = 61f;
    private static final float AVATAR_OUTER_RADIUS = 52f;
    private static final float AVATAR_RIM_RADIUS = 45f;
    private static final float AVATAR_INNER_RADIUS = 40f;
    private static final int SHUFFLE_AUDIO_STOP_FRAME = 53;
    private static final float CHIP_FLIGHT_DELAY = 0.12f;
    private static final float CHIP_FLIGHT_SECONDS = 0.92f;
    private static final float DEAL_START = 2.25f;
    private static final float DEAL_CARD_GAP = 0.17f;
    private static final float DEAL_CARD_SECONDS = 0.21f;
    private static final float DEAL_END = DEAL_START
            + (SEAT_COUNT * 2 - 1) * DEAL_CARD_GAP + DEAL_CARD_SECONDS;
    private static final float BOARD_DEAL_START = DEAL_END + 0.30f;
    private static final float BOARD_CARD_GAP = 0.20f;
    private static final float BOARD_DEAL_END = BOARD_DEAL_START
            + 4f * BOARD_CARD_GAP + DEAL_CARD_SECONDS;
    private static final float ACTION_CINEMATIC_SECONDS = 1.25f;
    private static final float SHOWDOWN_START = 41.2f;
    private static final float WINNER_START = 43.2f;
    private static final float[] COMMUNITY_REVEAL = {22.0f, 22.2f, 22.4f, 27.2f, 33.5f};
    private static final float CARD_CORNER_RADIUS = 0.075f;
    private static final float CARD_EDGE_SOFTNESS = 0.006f;
    private static final float PLAYER_POD_WIDTH = 172f;
    private static final float PLAYER_POD_HEIGHT = 62f;
    private static final int ACTION_CHECK = 0;
    private static final int ACTION_BET = 1;
    private static final int ACTION_CALL = 2;
    private static final int ACTION_FOLD = 3;
    private static final int ACTION_ALLIN = 4;
    private static final String[] HUD_ACTIONS = {"NO IR", "IR +300", "APOSTAR", "ALL-IN"};
    private static final int[] LOCAL_CARD_RANKS = {11, 12};
    private static final int[] SHOWDOWN_SEATS = {6, 2};
    private static final float[][] SEAT_ANCHORS = {
        {0.50f, 0.185f}, {0.135f, 0.145f}, {0.024f, 0.40f},
        {0.024f, 0.73f}, {0.24f, 0.90f}, {0.50f, 0.93f},
        {0.76f, 0.90f}, {0.976f, 0.73f}, {0.976f, 0.40f},
        {0.865f, 0.145f}
    };

    private static final ActionEvent[] ACTIONS = {
        new ActionEvent(7.8f, 1, ACTION_CHECK, "CHECK", 0, 0, 0),
        new ActionEvent(9.2f, 2, ACTION_BET, "SUBE 300", 300, 3, 1),
        new ActionEvent(10.6f, 3, ACTION_CALL, "CALL 300", 300, 3, 3),
        new ActionEvent(12.0f, 4, ACTION_FOLD, "FOLD", 0, 0, 0),
        new ActionEvent(13.4f, 5, ACTION_CALL, "CALL 300", 300, 3, 0),
        new ActionEvent(14.8f, 6, ACTION_CALL, "CALL 300", 300, 3, 2),
        new ActionEvent(16.2f, 7, ACTION_FOLD, "FOLD", 0, 0, 0),
        new ActionEvent(17.6f, 8, ACTION_CALL, "CALL 300", 300, 3, 0),
        new ActionEvent(19.0f, 9, ACTION_FOLD, "FOLD", 0, 0, 0),
        new ActionEvent(20.4f, 0, ACTION_CALL, "CALL 300", 300, 3, 1),
        new ActionEvent(24.0f, 2, ACTION_BET, "APUESTA 600", 600, 4, 3),
        new ActionEvent(25.4f, 6, ACTION_CALL, "CALL 600", 600, 4, 1),
        new ActionEvent(29.0f, 2, ACTION_CHECK, "CHECK", 0, 0, 0),
        new ActionEvent(30.4f, 6, ACTION_BET, "APUESTA 900", 900, 4, 0),
        new ActionEvent(31.8f, 8, ACTION_CALL, "CALL 900", 900, 4, 2),
        new ActionEvent(35.2f, 2, ACTION_ALLIN, "ALL IN 1.200", 1200, 8, 3),
        // rounders.gif lasts 3.42 s. Keep the next action outside that window
        // so the ALL-IN cinematic is always shown once, from first to last frame.
        new ActionEvent(39.1f, 8, ACTION_FOLD, "FOLD", 0, 0, 0)
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

    private Texture logo;
    private Texture feltTexture;
    private Texture avatarDefault;
    private Texture avatarBot;
    private Texture dealerChip;
    private Texture smallBlindChip;
    private Texture bigBlindChip;
    private Texture straddleChip;
    private Texture cardBack;
    private Texture[] flyingChips;
    private Texture pot;
    private Texture[] communityCards;
    private final Texture[][] showdownCards = new Texture[SEAT_COUNT][];
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
    private String potText = "150";
    private String potBreakdownText = "CIEGAS 150";

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
        straddleChip = texture("images/dealer_straddle.png");
        cardBack = cardTexture("images/decks/goliat/hq/trasera.jpg");
        flyingChips = new Texture[]{
            createChipTexture(new Color(0xd72d3bff), new Color(0x7f101bff)),
            createChipTexture(new Color(0x247ee8ff), new Color(0x10458fff)),
            createChipTexture(new Color(0x20a96bff), new Color(0x0d6840ff)),
            createChipTexture(new Color(0xe2a72fff), new Color(0x936312ff))
        };
        pot = texture("images/pot.png");
        communityCards = new Texture[]{
            cardTexture("images/decks/goliat/hq/A_P.jpg"),
            cardTexture("images/decks/goliat/hq/K_D.jpg"),
            cardTexture("images/decks/goliat/hq/8_C.jpg"),
            cardTexture("images/decks/goliat/hq/4_T.jpg"),
            cardTexture("images/decks/goliat/hq/2_P.jpg")
        };
        showdownCards[2] = new Texture[]{
            cardTexture("images/decks/goliat/hq/A_D.jpg"),
            cardTexture("images/decks/goliat/hq/A_C.jpg")
        };
        showdownCards[6] = new Texture[]{
            cardTexture("images/decks/goliat/hq/K_C.jpg"),
            cardTexture("images/decks/goliat/hq/K_T.jpg")
        };
        showdownCards[0] = new Texture[]{
            cardTexture("images/decks/goliat/hq/J_P.jpg"),
            cardTexture("images/decks/goliat/hq/Q_P.jpg")
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
        generator.dispose();

        initialiseStars();
        initialiseSeats();
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

    private void initialiseFlights() {
        int count = 0;
        for (ActionEvent action : ACTIONS) {
            count += action.chipCount;
        }
        flights = new ChipFlight[count];
        int index = 0;
        for (ActionEvent action : ACTIONS) {
            for (int chipIndex = 0; chipIndex < action.chipCount; chipIndex++) {
                flights[index] = new ChipFlight(action.seat,
                        action.time + CHIP_FLIGHT_DELAY + chipIndex * 0.065f,
                        CHIP_FLIGHT_SECONDS + chipIndex * 0.035f,
                        index * 37f,
                        (action.chipColor + chipIndex) % 4,
                        action.kind,
                        chipIndex);
                index++;
            }
        }
    }

    private void randomizeThinkDurations() {
        float previousActionTime = BOARD_DEAL_END;
        for (int i = 0; i < ACTIONS.length; i++) {
            float available = ACTIONS[i].time - previousActionTime - 0.10f;
            float maximum = Math.min(1.35f, Math.max(0.42f, available));
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
        float cardW = 150f;
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float cardEase = Interpolation.pow3Out.apply(MathUtils.clamp((sceneTime - 0.45f) / 1.1f, 0f, 1f));
        batch.draw(cardBack, width * 0.17f - cardW / 2f, height * 0.48f - cardH / 2f,
                cardW / 2f, cardH / 2f, cardW, cardH, cardEase, cardEase,
                -28f + 360f * (1f - cardEase), 0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
        batch.draw(cardBack, width * 0.83f - cardW / 2f, height * 0.48f - cardH / 2f,
                cardW / 2f, cardH / 2f, cardW, cardH, cardEase, cardEase,
                28f - 360f * (1f - cardEase), 0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
        batch.setShader(null);

        batch.setColor(Color.WHITE);
        batch.end();
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
        float boardCardW = Math.min(148f, tableW / 10f);
        float boardCardH = boardCardW * cardBack.getHeight() / cardBack.getWidth();
        // The pot owns the central axis, clearly above the upper edge of the
        // community row. It is never painted on top of a card.
        potCenterX = tableCx;
        potCenterY = tableCy + boardCardH * 0.64f + 145f;

        updateSeatPositions(width, height);
        drawTableBranding(height);
        drawChipTrails(potCenterX, potCenterY);
        drawHoleCards();
        drawSeats();
        drawCardsAndPot(tableCx, tableCy, tableW);
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
            seats[i].x = SEAT_ANCHORS[i][0] * width;
            seats[i].y = SEAT_ANCHORS[i][1] * height;
            if (i == 0) {
                seats[i].stackX = seats[i].x + 67f;
                seats[i].stackY = seats[i].y - 39f;
                seats[i].stackTextX = seats[i].stackX;
                seats[i].stackTextY = seats[i].stackY;
            } else {
                // Every rival uses the same PlayerPod. Edge clamping mirrors
                // the whole component without changing its internal layout.
                float podCenterX = MathUtils.clamp(seats[i].x,
                        PLAYER_POD_WIDTH / 2f + 8f,
                        width - PLAYER_POD_WIDTH / 2f - 8f);
                seats[i].podX = podCenterX - PLAYER_POD_WIDTH / 2f;
                seats[i].podY = MathUtils.clamp(seats[i].y - 104f, 8f,
                        height - PLAYER_POD_HEIGHT - 8f);
                seats[i].stackX = seats[i].podX + 35f;
                seats[i].stackY = seats[i].podY + 17f;
                seats[i].stackTextX = seats[i].podX + 116f;
                seats[i].stackTextY = seats[i].podY + 21f;
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

    private void drawSeats() {
        ActionEvent thinkingAction = thinkingAction(handTime());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        for (Seat seat : seats) {
            boolean active = thinkingAction != null && seat.index == thinkingAction.seat;
            boolean folded = isFolded(seat.index, handTime());
            seat.updateStack(handTime());
            if (seat.index != 0) {
                // One component owns name, chips and amount for every rival.
                Color rim = folded ? BUTTON_LINE : (active ? CYAN : SEAT_RIM);
                shapes.setColor(rim.r, rim.g, rim.b, folded ? 0.55f : 0.88f);
                roundedRect(seat.podX - 2f, seat.podY - 2f,
                        PLAYER_POD_WIDTH + 4f, PLAYER_POD_HEIGHT + 4f, 14f);
                shapes.setColor(0.015f, 0.028f, 0.05f, folded ? 0.72f : 0.92f);
                roundedRect(seat.podX, seat.podY,
                        PLAYER_POD_WIDTH, PLAYER_POD_HEIGHT, 12f);
                shapes.setColor(rim.r, rim.g, rim.b, folded ? 0.24f : 0.42f);
                shapes.rect(seat.podX + 12f, seat.podY + 31f,
                        PLAYER_POD_WIDTH - 24f, 2f);
            }
            if (active) {
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                        0.18f + 0.12f * MathUtils.sin(totalTime * 4f));
                shapes.circle(seat.x, seat.y, AVATAR_ACTIVE_RADIUS, 48);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            shapes.setColor(PANEL);
            shapes.circle(seat.x, seat.y, AVATAR_OUTER_RADIUS, 48);
            shapes.setColor(folded ? BUTTON_LINE : (active ? CYAN : SEAT_RIM));
            shapes.circle(seat.x, seat.y, AVATAR_RIM_RADIUS, 48);
            shapes.setColor(SEAT_INNER);
            shapes.circle(seat.x, seat.y, AVATAR_INNER_RADIUS, 48);
        }
        shapes.end();

        batch.begin();
        for (Seat seat : seats) {
            Texture avatar = seat.index == 0 ? avatarDefault : avatarBot;
            boolean folded = isFolded(seat.index, handTime());
            batch.setColor(folded ? FOLDED_AVATAR : Color.WHITE);
            batch.draw(avatar, seat.x - AVATAR_SIZE / 2f,
                    seat.y - AVATAR_SIZE / 2f, AVATAR_SIZE, AVATAR_SIZE);
            batch.setColor(Color.WHITE);
            Texture position = seat.index == 0 ? dealerChip
                    : seat.index == 1 ? smallBlindChip
                    : seat.index == 2 ? bigBlindChip
                    : seat.index == 3 ? straddleChip : null;
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
            batch.setColor(folded ? FOLDED_AVATAR : Color.WHITE);
            for (int column = 0; column < 2; column++) {
                int chipCount = column == 0 ? 6 : 4;
                Texture stackChip = flyingChips[(seat.index + column) % flyingChips.length];
                float columnX = seat.stackX - 19f + column * 20f;
                for (int chip = 0; chip < chipCount; chip++) {
                    batch.draw(stackChip, columnX - 13f,
                            seat.stackY - 14f + chip * 4f, 26f, 26f);
                }
            }
            batch.setColor(Color.WHITE);
            if (seat.index != 0) {
                drawCentered(stackFont, seat.stackText, seat.stackTextX, seat.stackTextY,
                        folded ? Color.GRAY : STACK_GREEN, 1f);
            }
            if (seat.index != 0) {
                drawFittedCentered(playerNameFont, seat.name,
                        seat.podX + PLAYER_POD_WIDTH / 2f, seat.podY + 52f,
                        PLAYER_POD_WIDTH - 24f,
                        folded ? Color.GRAY : Color.WHITE, 1f);
            }
        }
        batch.end();
    }

    private void drawHoleCards() {
        float time = handTime();
        if (time < DEAL_START) {
            return;
        }
        float localSwapRaw = localHandNeedsSwap() ? MathUtils.clamp(
                (time - localSwapStart()) / LOCAL_SWAP_SECONDS, 0f, 1f) : 0f;
        float localSwap = Interpolation.smoother.apply(localSwapRaw);
        float localSwapArc = MathUtils.sin(localSwap * MathUtils.PI);
        float cardW = 125f;
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        batch.begin();
        for (Seat seat : seats) {
            if (seat.index != 0 && isFolded(seat.index, time)) {
                continue;
            }
            float seatCardW = seat.index == 0 ? 200f : cardW;
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
            float shownCenterX = seat.x + towardX * shownDistance;
            float shownCenterY = seat.y + towardY * shownDistance;
            float shownFanX = sideX;
            float shownFanY = sideY;
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
                Texture face = showdownCards[seat.index] == null
                        ? null : showdownCards[seat.index][cardIndex];
                float revealStart = showdownRevealStart(seat.index, cardIndex);
                boolean revealing = face != null && time >= revealStart;
                float reveal = revealing ? MathUtils.clamp(
                        (time - revealStart) / CARD_FLIP_SECONDS, 0f, 1f) : 0f;
                float revealMotion = Interpolation.smooth.apply(reveal);
                float handCenterX = seat.index == 0 ? shownCenterX
                        : MathUtils.lerp(hiddenCenterX, shownCenterX, revealMotion);
                float handCenterY = seat.index == 0 ? shownCenterY
                        : MathUtils.lerp(hiddenCenterY, shownCenterY, revealMotion);
                float fanX = MathUtils.lerp(hiddenFanX, shownFanX, revealMotion);
                float fanY = MathUtils.lerp(hiddenFanY, shownFanY, revealMotion);
                float fanLength = Math.max(0.001f,
                        (float) Math.sqrt(fanX * fanX + fanY * fanY));
                fanX /= fanLength;
                fanY /= fanLength;
                // Rivals keep a tight pair tucked under their avatar. The pair
                // only travels out and opens when the player reveals it.
                float normalSideDistance = seat.index == 0 ? 54f : 18f;
                float revealedSideDistance = seat.index == 0 ? 90f : 72f;
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
                float restingRotation = cardIndex == 0 ? -7f : 7f;
                if (seat.index == 0) {
                    // Local cards form a true V: upper corners open outwards
                    // while the lower inner corners stay close together.
                    restingRotation = cardIndex == 0
                            ? LOCAL_CARD_FAN_ANGLE : -LOCAL_CARD_FAN_ANGLE;
                    restingRotation = MathUtils.lerp(
                            restingRotation, -restingRotation, localSwap);
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
                batch.setColor(1f, 1f, 1f, eased);
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
        float cardW = Math.min(148f, tableWidth / 10f);
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

        float payoutFade = MathUtils.clamp((handTime() - SHOWDOWN_START) / 0.38f, 0f, 1f);
        float pulse = 1f + MathUtils.sin(totalTime * 3.3f) * 0.035f;
        float potW = 108f * pulse;
        float potH = potW * pot.getHeight() / pot.getWidth();
        int currentPot = potAt(handTime());
        if (currentPot != lastPotValue) {
            lastPotValue = currentPot;
            potText = String.format("%,d", currentPot);
            potBreakdownText = currentPot == 150
                    ? "CIEGAS 150"
                    : String.format("CIEGAS 150  +  APUESTAS %,d", currentPot - 150);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        float alpha = 1f - payoutFade;
        float panelWidth = 342f;
        float panelHeight = 72f;
        float panelX = potCenterX - panelWidth / 2f;
        float panelY = potCenterY - potH / 2f - panelHeight - 14f;
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
        batch.draw(pot, potCenterX - potW / 2f, potCenterY - potH / 2f, potW, potH);
        drawCentered(actionFont, "BOTE TOTAL", potCenterX, panelY + 59f,
                POT_GOLD, alpha);
        drawCentered(uiFont, potText, potCenterX, panelY + 38f,
                Color.WHITE, alpha);
        drawFittedCentered(smallFont, potBreakdownText, potCenterX,
                panelY + 16f, panelWidth - 22f, STACK_GREEN, alpha);
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
        return sceneTime % HAND_SECONDS;
    }

    private void updateHandSounds() {
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
        for (int card = 0; card < communityCards.length; card++) {
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
        int value = 150;
        for (ActionEvent action : ACTIONS) {
            if (action.amount > 0 && time >= action.time
                    + CHIP_FLIGHT_DELAY + CHIP_FLIGHT_SECONDS) {
                value += action.amount;
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
        return seat == 2 || seat == 6;
    }

    private static float showdownRevealStart(int seat, int cardIndex) {
        if (seat == 0) {
            int localDealOrder = SEAT_COUNT - 1;
            float localDealStart = DEAL_START
                    + (cardIndex * SEAT_COUNT + localDealOrder) * DEAL_CARD_GAP;
            return localDealStart + DEAL_CARD_SECONDS;
        }
        if (seat == 6) {
            return SHOWDOWN_START + 0.15f + cardIndex * 0.12f;
        }
        if (seat == 2) {
            return SHOWDOWN_START + 0.95f + cardIndex * 0.12f;
        }
        return Float.POSITIVE_INFINITY;
    }

    private static float localSwapStart() {
        // Sorting starts only after both local cards have landed and finished
        // turning face-up. In the demo J/Q needs one swap: Q finishes left.
        return showdownRevealStart(0, 1) + CARD_FLIP_SECONDS + LOCAL_SWAP_DELAY;
    }

    private static boolean localHandNeedsSwap() {
        return LOCAL_CARD_RANKS[0] < LOCAL_CARD_RANKS[1];
    }

    private ActionEvent currentAction(float time) {
        for (int i = ACTIONS.length - 1; i >= 0; i--) {
            ActionEvent action = ACTIONS[i];
            if (time >= action.time) {
                float duration = action.kind == ACTION_ALLIN
                        ? allInGif.durationSeconds() : ACTION_CINEMATIC_SECONDS;
                if (time < action.time + duration) {
                    return action;
                }
                return null;
            }
        }
        return null;
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
            drawCentered(uiFont, "BARAJANDO GOLIAT", tableCenterX, y - 12f, Color.WHITE, 1f);
            batch.end();
            return;
        }
        if (time < DEAL_START) {
            float alpha = Interpolation.fade.apply(MathUtils.clamp(
                    (time - POSITION_CHIP_START) / 0.20f, 0f, 1f));
            batch.begin();
            drawCentered(smallFont, "POSICIONES DE MESA", tableCenterX,
                    tableCenterY - 38f, CYAN, alpha);
            batch.end();
            return;
        }

        ActionEvent action = currentAction(time);
        if (action == null) {
            return;
        }
        if (action.kind == ACTION_ALLIN) {
            drawAllInCinematic(action, time - action.time, worldWidth, worldHeight);
            return;
        }
        Seat seat = seats[action.seat];
        float elapsed = time - action.time;
        float progress = MathUtils.clamp(elapsed / ACTION_CINEMATIC_SECONDS, 0f, 1f);
        float appear = Interpolation.pow3Out.apply(MathUtils.clamp(progress / 0.22f, 0f, 1f));
        float disappear = 1f - Interpolation.pow2In.apply(
                MathUtils.clamp((progress - 0.72f) / 0.28f, 0f, 1f));
        float alpha = appear * disappear;
        Color actionColor = action.kind == ACTION_FOLD ? ORANGE
                : action.kind == ACTION_CALL ? STACK_GREEN
                : action.kind == ACTION_BET ? POT_GOLD : CYAN;
        float towardX = tableCenterX - seat.x;
        float towardY = tableCenterY - seat.y;
        float length = Math.max(1f, (float) Math.sqrt(towardX * towardX + towardY * towardY));
        towardX /= length;
        towardY /= length;
        float width = 286f;
        float height = 68f;
        // Action callout belongs to the table lane, not the seat HUD lane.
        float centerX = seat.x + towardX * 260f;
        float centerY = seat.y + towardY * 235f;
        float x = MathUtils.clamp(centerX - width / 2f, 18f, worldWidth - width - 18f);
        float y = MathUtils.clamp(centerY - height / 2f, 112f, worldHeight - height - 125f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, PANEL.a * alpha);
        shapes.rect(x, y, width, height);
        shapes.setColor(actionColor.r, actionColor.g, actionColor.b, alpha);
        shapes.rect(x, y, width * Math.min(1f, progress / 0.82f), 4f);
        shapes.end();

        batch.begin();
        drawCentered(smallFont, seats[action.seat].name + "  //  " + action.label,
                x + width / 2f, y + 43f, actionColor, alpha);
        batch.end();
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
        float time = handTime();
        if (time < SHOWDOWN_START) {
            return;
        }

        float revealAlpha = Interpolation.fade.apply(MathUtils.clamp(
                (time - SHOWDOWN_START) / 0.45f, 0f, 1f));
        boolean winnerVisible = time >= WINNER_START;
        float winnerProgress = MathUtils.clamp((time - WINNER_START) / 1.2f, 0f, 1f);
        float bannerWidth = winnerVisible ? 690f : 430f;
        float bannerHeight = winnerVisible ? 104f : 72f;
        float bannerX = tableCenterX - bannerWidth / 2f;
        float bannerY = tableCenterY + 205f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.92f * revealAlpha);
        shapes.rect(bannerX, bannerY, bannerWidth, bannerHeight);
        shapes.setColor(winnerVisible ? POT_GOLD : CYAN);
        shapes.rect(bannerX, bannerY, bannerWidth, 4f);

        for (int seatIndex : SHOWDOWN_SEATS) {
            Seat seat = seats[seatIndex];
            float towardX = tableCenterX - seat.x;
            float towardY = tableCenterY - seat.y;
            float length = Math.max(1f, (float) Math.sqrt(towardX * towardX + towardY * towardY));
            towardX /= length;
            towardY /= length;
            float labelX = seat.x + towardX * 225f;
            float labelY = seat.y + towardY * 210f;
            float cardReveal = MathUtils.clamp((time - showdownRevealStart(seatIndex, 1))
                    / CARD_FLIP_SECONDS, 0f, 1f);
            shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.84f * cardReveal);
            shapes.rect(labelX - 132f, labelY - 28f, 264f, 52f);
            Color line = seatIndex == 2 && winnerVisible ? POT_GOLD : CYAN;
            shapes.setColor(line.r, line.g, line.b, cardReveal);
            shapes.rect(labelX - 132f, labelY - 28f, 264f, 3f);
        }

        if (winnerVisible) {
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
                float radius = 55f + Interpolation.circleOut.apply(travel) * (90f + particle % 8 * 18f);
                Color color = particle % 3 == 0 ? CYAN : POT_GOLD;
                shapes.setColor(color.r, color.g, color.b, (1f - travel) * 0.62f);
                shapes.circle(winner.x + MathUtils.cos(angle) * radius,
                        winner.y + MathUtils.sin(angle) * radius, 2f + particle % 4, 10);
            }
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        shapes.end();

        batch.begin();
        if (winnerVisible) {
            drawCentered(uiFont, "RIVERKING GANA", tableCenterX, bannerY + 70f,
                    POT_GOLD, winnerProgress);
            drawCentered(smallFont, "TRIO DE ASES  //  BOTE 5.550", tableCenterX,
                    bannerY + 35f, Color.WHITE, winnerProgress);
        } else {
            drawCentered(uiFont, "SHOWDOWN", tableCenterX, bannerY + 47f,
                    Color.WHITE, revealAlpha);
        }

        for (int seatIndex : SHOWDOWN_SEATS) {
            Seat seat = seats[seatIndex];
            float towardX = tableCenterX - seat.x;
            float towardY = tableCenterY - seat.y;
            float length = Math.max(1f, (float) Math.sqrt(towardX * towardX + towardY * towardY));
            towardX /= length;
            towardY /= length;
            float labelX = seat.x + towardX * 225f;
            float labelY = seat.y + towardY * 210f;
            float cardReveal = MathUtils.clamp((time - showdownRevealStart(seatIndex, 1))
                    / CARD_FLIP_SECONDS, 0f, 1f);
            String result = seatIndex == 2 ? "TRIO DE ASES" : "TRIO DE REYES";
            Color color = seatIndex == 2 && winnerVisible ? POT_GOLD : Color.WHITE;
            drawCentered(smallFont, result, labelX, labelY + 5f, color, cardReveal);
        }

        if (winnerVisible) {
            Seat winner = seats[2];
            for (int chip = 0; chip < 18; chip++) {
                float raw = (time - WINNER_START - 0.18f - chip * 0.035f) / 1.05f;
                if (raw <= 0f || raw >= 1f) {
                    continue;
                }
                float eased = Interpolation.pow2Out.apply(raw);
                float targetX = winner.x + (chip % 5 - 2) * 12f;
                float targetY = winner.y + 18f + (chip % 4) * 8f;
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
        shapes.rect(x + safeRadius, y, width - safeRadius * 2f, height);
        shapes.rect(x, y + safeRadius, width, height - safeRadius * 2f);
        shapes.circle(x + safeRadius, y + safeRadius, safeRadius, 18);
        shapes.circle(x + width - safeRadius, y + safeRadius, safeRadius, 18);
        shapes.circle(x + safeRadius, y + height - safeRadius, safeRadius, 18);
        shapes.circle(x + width - safeRadius, y + height - safeRadius, safeRadius, 18);
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
        float textCenterX = x + iconBayWidth + (width - iconBayWidth) / 2f;
        drawFittedCentered(actionFont, text, textCenterX,
                y + 50f, width - iconBayWidth - 14f, color, alpha);
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
        float hudY = 12f;
        float hudHeight = 126f;
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
        Color hudFrame = localTurn ? POT_GOLD : BUTTON_LINE;
        shapes.setColor(hudFrame.r, hudFrame.g, hudFrame.b,
                localTurn ? 0.48f + turnPulse * 0.30f : 0.34f);
        roundedRect(hudX - 8f, hudY - 8f,
                hudWidth + 16f, hudHeight + 25f, 19f);
        shapes.setColor(0.008f, 0.018f, 0.034f, 0.90f);
        roundedRect(hudX - 3f, hudY - 3f,
                hudWidth + 6f, hudHeight + 15f, 16f);
        shapes.setColor(0f, 0f, 0f, 0.46f);
        roundedRect(hudX + 5f, hudY - 4f, infoWidth, hudHeight, 15f);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.96f);
        roundedRect(hudX, hudY, infoWidth, hudHeight, 15f);
        shapes.setColor(0.03f, 0.06f, 0.11f, 0.98f);
        roundedRect(hudX + 10f, hudY + 10f, infoWidth - 20f, hudHeight - 20f, 11f);
        Color hudLine = localTurn ? POT_GOLD : CYAN;
        shapes.setColor(hudLine.r, hudLine.g, hudLine.b, 0.82f);
        shapes.rect(hudX + 16f, hudY + hudHeight - 4f, infoWidth - 32f, 3f);
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
        drawCentered(localTurn ? actionFont : smallFont,
                localTurn ? "TU TURNO" : "ESPERANDO TURNO",
                hudX + infoWidth / 2f, hudY + 112f,
                localTurn ? POT_GOLD : CYAN, 1f);
        drawCentered(uiFont, "TONIKELOPE", hudX + infoWidth / 2f,
                hudY + 79f, Color.WHITE, 1f);
        drawCentered(smallFont, "STACK  " + local.stackText, hudX + infoWidth / 2f,
                hudY + 48f, STACK_GREEN, 1f);

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
        logo.dispose();
        feltTexture.dispose();
        avatarDefault.dispose();
        avatarBot.dispose();
        dealerChip.dispose();
        smallBlindChip.dispose();
        bigBlindChip.dispose();
        straddleChip.dispose();
        cardBack.dispose();
        for (Texture flyingChip : flyingChips) {
            flyingChip.dispose();
        }
        pot.dispose();
        for (Texture texture : communityCards) {
            texture.dispose();
        }
        for (Texture[] hand : showdownCards) {
            if (hand != null) {
                for (Texture texture : hand) {
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
        int displayedStack;
        final int index;
        float x;
        float y;
        float stackX;
        float stackY;
        float stackTextX;
        float stackTextY;
        float podX;
        float podY;
        float positionX;
        float positionY;

        Seat(String name, int stack, int index) {
            this.name = name;
            this.stack = stack;
            this.displayedStack = stack;
            this.stackText = String.format("%,d", stack);
            this.index = index;
        }

        void updateStack(float time) {
            int current = stack;
            for (ActionEvent action : ACTIONS) {
                if (action.seat == index && action.amount > 0
                        && time >= action.time + CHIP_FLIGHT_DELAY + CHIP_FLIGHT_SECONDS) {
                    current -= action.amount;
                }
            }
            if (current != displayedStack) {
                displayedStack = current;
                stackText = String.format("%,d", current);
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

        ChipFlight(int seat, float startTime, float duration, float rotation,
                int chipColor, int actionKind, int chipIndex) {
            this.seat = seat;
            this.startTime = startTime;
            this.duration = duration;
            this.rotation = rotation;
            this.chipColor = chipColor;
            this.actionKind = actionKind;
            this.chipIndex = chipIndex;
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
