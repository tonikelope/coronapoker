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
    private static final int SEAT_COUNT = 9;
    private static final int FRAME_SAMPLE_COUNT = 720;
    private static final float CARD_FLIP_SECONDS = 0.620f;
    private static final float HAND_SECONDS = 33.5f;
    private static final float DEAL_START = 2.25f;
    private static final float DEAL_CARD_GAP = 0.26f;
    private static final float DEAL_CARD_SECONDS = 0.24f;
    private static final float DEAL_END = DEAL_START
            + (SEAT_COUNT * 2 - 1) * DEAL_CARD_GAP + DEAL_CARD_SECONDS;
    private static final float ACTION_CINEMATIC_SECONDS = 1.25f;
    private static final float[] COMMUNITY_REVEAL = {14.2f, 14.4f, 14.6f, 20.5f, 26.8f};
    private static final float CARD_CORNER_RADIUS = 0.075f;
    private static final float CARD_EDGE_SOFTNESS = 0.006f;
    private static final int ACTION_CHECK = 0;
    private static final int ACTION_BET = 1;
    private static final int ACTION_CALL = 2;
    private static final int ACTION_FOLD = 3;

    private static final ActionEvent[] ACTIONS = {
        new ActionEvent(7.5f, 1, ACTION_CHECK, "CHECK", 0, 0, 0),
        new ActionEvent(8.8f, 2, ACTION_BET, "SUBE 300", 300, 3, 1),
        new ActionEvent(10.1f, 4, ACTION_FOLD, "FOLD", 0, 0, 0),
        new ActionEvent(11.4f, 5, ACTION_CALL, "CALL 300", 300, 3, 2),
        new ActionEvent(12.7f, 7, ACTION_CALL, "CALL 300", 300, 3, 0),
        new ActionEvent(15.9f, 2, ACTION_BET, "APUESTA 600", 600, 4, 3),
        new ActionEvent(17.3f, 5, ACTION_CALL, "CALL 600", 600, 4, 1),
        new ActionEvent(21.8f, 2, ACTION_CHECK, "CHECK", 0, 0, 0),
        new ActionEvent(23.1f, 5, ACTION_BET, "APUESTA 900", 900, 4, 0),
        new ActionEvent(24.4f, 7, ACTION_CALL, "CALL 900", 900, 4, 2),
        new ActionEvent(28.4f, 2, ACTION_BET, "APUESTA 1.200", 1200, 5, 3),
        new ActionEvent(29.9f, 7, ACTION_FOLD, "FOLD", 0, 0, 0)
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
    private static final Color FELT_SHADE_TOP = new Color(0x07111f6b);
    private static final Color FELT_SHADE_BOTTOM = new Color(0x02050cbd);
    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color CYAN_SOFT = new Color(0x36d9ff55);
    private static final Color ORANGE = new Color(0xff6b27ff);
    private static final Color FELT = new Color(0x075c3f66);
    private static final Color FELT_EDGE = new Color(0x16a574ff);
    private static final Color PANEL = new Color(0x101a2ee6);
    private static final Color TABLE_SHADOW = new Color(0x02060dff);
    private static final Color FELT_INNER = new Color(0x063a2d66);
    private static final Color FELT_LINE = new Color(0x51f5c055);
    private static final Color SEAT_RIM = new Color(0x647594ff);
    private static final Color SEAT_INNER = new Color(0x111a2aff);
    private static final Color STACK_GREEN = new Color(0x9fffd2ff);
    private static final Color POT_GOLD = new Color(0xffe07aff);
    private static final Color BUTTON_HOVER = new Color(0x1d789dff);
    private static final Color BUTTON_LINE = new Color(0x31445fff);
    private static final Color FOLDED_AVATAR = new Color(0.35f, 0.35f, 0.38f, 0.72f);

    private final int detectedRefreshRate;
    private final Star[] stars = new Star[STAR_COUNT];
    private final Seat[] seats = new Seat[SEAT_COUNT];
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

    private BitmapFont titleFont;
    private BitmapFont uiFont;
    private BitmapFont smallFont;

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
    private Texture[] communityCards;
    private GifTextureAnimation shuffleGif;

    private float tableCenterX;
    private float tableCenterY;
    private int lastPotValue = -1;
    private String potText = "BOTE 150";

    private float totalTime;
    private float sceneTime;
    private float statsClock;
    private float burstClock = -10f;
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
        cardBack = cardTexture("images/decks/goliat/trasera.jpg");
        flyingChips = new Texture[]{
            createChipTexture(new Color(0xd72d3bff), new Color(0x7f101bff)),
            createChipTexture(new Color(0x247ee8ff), new Color(0x10458fff)),
            createChipTexture(new Color(0x20a96bff), new Color(0x0d6840ff)),
            createChipTexture(new Color(0xe2a72fff), new Color(0x936312ff))
        };
        pot = texture("images/pot.png");
        communityCards = new Texture[]{
            cardTexture("images/decks/goliat/A_P.jpg"),
            cardTexture("images/decks/goliat/K_D.jpg"),
            cardTexture("images/decks/goliat/Q_C.jpg"),
            cardTexture("images/decks/goliat/J_T.jpg"),
            cardTexture("images/decks/goliat/10_P.jpg")
        };
        shuffleGif = gif("images/decks/goliat/gif/shuffle.gif", 960);

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        titleFont = font(generator, 66, 2.0f);
        uiFont = font(generator, 31, 1.2f);
        smallFont = font(generator, 21, 0.8f);
        generator.dispose();

        initialiseStars();
        initialiseSeats();
        initialiseFlights();
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
            "PIXEL", "NOVA", "SHARK", "CORONA_BOT"};
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
                        action.time + 0.12f + chipIndex * 0.065f,
                        0.92f + chipIndex * 0.035f,
                        index * 37f,
                        (action.chipColor + chipIndex) % 4);
                index++;
            }
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
        batch.setColor(0.72f, 0.82f, 0.74f, 1f);
        batch.draw(feltTexture, 0f, 0f, width, height,
                0f, 0f, width / feltTexture.getWidth(), height / feltTexture.getHeight());
        batch.setColor(Color.WHITE);
        batch.end();

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

    private void drawIntro() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float appear = MathUtils.clamp(sceneTime / 1.1f, 0f, 1f);
        float eased = Interpolation.swingOut.apply(appear);
        float alpha = sceneTime > INTRO_SECONDS - 0.7f
                ? MathUtils.clamp((INTRO_SECONDS - sceneTime) / 0.7f, 0f, 1f) : 1f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int i = 8; i >= 1; i--) {
            float radius = 80f + i * 48f + MathUtils.sin(totalTime * 2f) * 8f;
            shapes.setColor(ORANGE.r, ORANGE.g, ORANGE.b, alpha * 0.012f * (9 - i));
            shapes.circle(width / 2f, height / 2f + 45f, radius, 96);
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

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

        drawCentered(titleFont, "GPU FRONTEND PROTOTYPE", width / 2f, height * 0.22f, CYAN, alpha);
        drawCentered(smallFont, "INTRO EN TIEMPO REAL  //  ESPACIO PARA SALTAR",
                width / 2f, height * 0.16f, Color.LIGHT_GRAY, alpha);
        batch.setColor(Color.WHITE);
        batch.end();

        float progress = MathUtils.clamp(sceneTime / INTRO_SECONDS, 0f, 1f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(CYAN_SOFT);
        shapes.rect(width * 0.32f, height * 0.115f, width * 0.36f, 3f);
        shapes.setColor(CYAN);
        shapes.rect(width * 0.32f, height * 0.115f, width * 0.36f * progress, 3f);
        shapes.end();
    }

    private void drawTableScene() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float tableCx = width / 2f;
        float tableCy = height * 0.52f;
        float tableW = Math.min(1510f, width * 0.78f);
        float tableH = Math.min(660f, height * 0.61f);
        tableCenterX = tableCx;
        tableCenterY = tableCy;

        updateSeatPositions(tableCx, tableCy, tableW, tableH);
        drawHeader(width, height);
        drawTableGlow(tableCx, tableCy, tableW, tableH);
        drawTable(tableCx, tableCy, tableW, tableH);
        drawChipTrails(tableCx, tableCy);
        drawHoleCards();
        drawSeats();
        drawCardsAndPot(tableCx, tableCy, tableW);
        drawFlyingChips(tableCx, tableCy);
        drawHandOverlay();
        drawFooter(width, height);

        if (burstClock >= 0f) {
            burstClock += Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
            if (burstClock > 1.2f) {
                burstClock = -10f;
            }
        }
    }

    private void drawHeader(float width, float height) {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(logo, 42f, height - 125f, 235f,
                235f * logo.getHeight() / logo.getWidth());
        drawCentered(uiFont, "MESA GPU // 9 JUGADORES", width / 2f, height - 58f,
                Color.WHITE, 1f);
        drawCentered(smallFont, stageText(handTime()) + "  |  GOLIAT + CINEMATICA GPU  |  V-SYNC",
                width / 2f, height - 94f, CYAN, 1f);
        smallFont.setColor(CYAN);
        smallFont.draw(batch, statsText, width - 475f, height - 46f);
        smallFont.setColor(Color.WHITE);
        batch.end();
    }

    private void drawTableGlow(float cx, float cy, float width, float height) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int i = 8; i >= 1; i--) {
            shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.012f * (9 - i));
            shapes.ellipse(cx - width / 2f - i * 7f, cy - height / 2f - i * 5f,
                    width + i * 14f, height + i * 10f, 128);
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawTable(float cx, float cy, float width, float height) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(TABLE_SHADOW);
        shapes.ellipse(cx - width / 2f - 30f, cy - height / 2f - 30f,
                width + 60f, height + 60f, 128);
        shapes.setColor(FELT_EDGE);
        shapes.ellipse(cx - width / 2f - 14f, cy - height / 2f - 14f,
                width + 28f, height + 28f, 128);
        shapes.setColor(FELT);
        shapes.ellipse(cx - width / 2f, cy - height / 2f, width, height, 128);
        shapes.setColor(FELT_INNER);
        shapes.ellipse(cx - width * 0.39f, cy - height * 0.34f,
                width * 0.78f, height * 0.68f, 128);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        shapes.setColor(FELT_LINE);
        shapes.ellipse(cx - width * 0.42f, cy - height * 0.37f,
                width * 0.84f, height * 0.74f, 128);
        shapes.end();
    }

    private void updateSeatPositions(float cx, float cy, float tableW, float tableH) {
        for (int i = 0; i < seats.length; i++) {
            float angle = MathUtils.PI / 2f + MathUtils.PI2 * i / seats.length;
            seats[i].x = cx + MathUtils.cos(angle) * tableW * 0.53f;
            seats[i].y = cy + MathUtils.sin(angle) * tableH * 0.58f;
        }
    }

    private void drawSeats() {
        ActionEvent currentAction = currentAction(handTime());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        for (Seat seat : seats) {
            boolean active = currentAction != null && seat.index == currentAction.seat;
            boolean folded = isFolded(seat.index, handTime());
            if (active) {
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                        0.18f + 0.12f * MathUtils.sin(totalTime * 4f));
                shapes.circle(seat.x, seat.y, 74f, 48);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            shapes.setColor(PANEL);
            shapes.circle(seat.x, seat.y, 61f, 48);
            shapes.setColor(folded ? BUTTON_LINE : (active ? CYAN : SEAT_RIM));
            shapes.circle(seat.x, seat.y, 54f, 48);
            shapes.setColor(SEAT_INNER);
            shapes.circle(seat.x, seat.y, 48f, 48);
        }
        shapes.end();

        batch.begin();
        for (Seat seat : seats) {
            Texture avatar = seat.index == 0 ? avatarDefault : avatarBot;
            boolean folded = isFolded(seat.index, handTime());
            batch.setColor(folded ? FOLDED_AVATAR : Color.WHITE);
            batch.draw(avatar, seat.x - 43f, seat.y - 43f, 86f, 86f);
            batch.setColor(Color.WHITE);
            Texture position = seat.index == 0 ? dealerChip
                    : seat.index == 1 ? smallBlindChip
                    : seat.index == 2 ? bigBlindChip : null;
            if (position != null) {
                batch.draw(position, seat.x - 69f, seat.y + 30f, 38f, 38f);
            }
            drawCentered(smallFont, Integer.toString(seat.index + 1), seat.x + 39f, seat.y + 38f,
                    CYAN, 1f);
            drawCentered(smallFont, seat.name, seat.x, seat.y - 72f,
                    folded ? Color.GRAY : Color.WHITE, 1f);
            seat.updateStack(handTime());
            drawCentered(smallFont, seat.stackText, seat.x, seat.y - 97f,
                    folded ? Color.GRAY : STACK_GREEN, 1f);
        }
        batch.end();
    }

    private void drawHoleCards() {
        float time = handTime();
        if (time < DEAL_START) {
            return;
        }
        float cardW = 46f;
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        batch.begin();
        useRoundedCardShader();
        if (time < DEAL_END) {
            for (int deckCard = 2; deckCard >= 0; deckCard--) {
                batch.setColor(1f, 1f, 1f, 0.96f);
                batch.draw(cardBack, tableCenterX - cardW / 2f + deckCard * 2f,
                        tableCenterY + 40f - cardH / 2f + deckCard * 2f,
                        cardW, cardH);
            }
        }
        for (Seat seat : seats) {
            if (isFolded(seat.index, time)) {
                continue;
            }
            float towardX = tableCenterX - seat.x;
            float towardY = tableCenterY - seat.y;
            float length = Math.max(1f, (float) Math.sqrt(towardX * towardX + towardY * towardY));
            towardX /= length;
            towardY /= length;
            float sideX = -towardY;
            float sideY = towardX;
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
                float side = cardIndex == 0 ? -15f : 15f;
                float targetX = seat.x + towardX * 91f + sideX * side;
                float targetY = seat.y + towardY * 91f + sideY * side;
                float sourceX = tableCenterX;
                float sourceY = tableCenterY + 40f;
                float controlX = (sourceX + targetX) * 0.5f + sideX * 86f;
                float controlY = (sourceY + targetY) * 0.5f + sideY * 86f + 42f;
                float x = bezier(sourceX, controlX, targetX, eased);
                float y = bezier(sourceY, controlY, targetY, eased);
                float launchRotation = (dealTurn & 1) == 0 ? -26f : 26f;
                float rotation = MathUtils.lerp(launchRotation,
                        cardIndex == 0 ? -7f : 7f, eased);
                float scale = 0.72f + eased * 0.28f;
                batch.setColor(0f, 0f, 0f, 0.35f * eased);
                batch.draw(cardBack, x - cardW / 2f + 4f, y - cardH / 2f - 5f,
                        cardW / 2f, cardH / 2f, cardW, cardH, scale, scale, rotation,
                        0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
                batch.setColor(1f, 1f, 1f, eased);
                batch.draw(cardBack, x - cardW / 2f, y - cardH / 2f,
                        cardW / 2f, cardH / 2f, cardW, cardH, scale, scale, rotation,
                        0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
            }
        }
        batch.setColor(Color.WHITE);
        batch.setShader(null);
        batch.end();
    }

    private void drawCardsAndPot(float cx, float cy, float tableWidth) {
        float cardW = Math.min(126f, tableWidth / 12f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = cx - gap * 2f - cardW / 2f;
        float cardY = cy - cardH * 0.36f;
        float canvasW = cardW * 1.5f;
        float canvasH = cardH * 1.5f;

        batch.begin();
        for (int i = 0; i < communityCards.length; i++) {
            float local = MathUtils.clamp(
                    (handTime() - COMMUNITY_REVEAL[i]) / CARD_FLIP_SECONDS, 0f, 1f);
            float angle = local * MathUtils.PI;
            float cardX = firstX + i * gap;
            float canvasX = cardX - (canvasW - cardW) / 2f;
            float canvasY = cardY - (canvasH - cardH) / 2f;
            usePerspectiveCardShader(communityCards[i], angle, cardH / cardW);
            batch.setColor(0f, 0f, 0f, 0.35f);
            batch.draw(cardBack, canvasX + 7f, canvasY - 8f, canvasW, canvasH);
            batch.setColor(Color.WHITE);
            batch.draw(cardBack, canvasX, canvasY, canvasW, canvasH);
        }
        batch.setShader(null);

        float pulse = 1f + MathUtils.sin(totalTime * 3.3f) * 0.035f;
        float potW = 108f * pulse;
        float potH = potW * pot.getHeight() / pot.getWidth();
        batch.draw(pot, cx - potW / 2f, cy + cardH * 0.73f, potW, potH);
        int currentPot = potAt(handTime());
        if (currentPot != lastPotValue) {
            lastPotValue = currentPot;
            potText = String.format("BOTE %,d", currentPot);
        }
        drawCentered(uiFont, potText, cx, cy + cardH * 0.66f,
                POT_GOLD, 1f);
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

    private String stageText(float time) {
        if (time < DEAL_START) {
            return "BARAJANDO";
        }
        if (time < DEAL_END) {
            return "REPARTIENDO";
        }
        if (time < COMMUNITY_REVEAL[0]) {
            return "PREFLOP";
        }
        if (time < COMMUNITY_REVEAL[3]) {
            return "FLOP";
        }
        if (time < COMMUNITY_REVEAL[4]) {
            return "TURN";
        }
        if (time < 31.2f) {
            return "RIVER";
        }
        return "NUEVA MANO...";
    }

    private int potAt(float time) {
        int value = 150;
        for (ActionEvent action : ACTIONS) {
            if (action.amount > 0 && time >= action.time + 0.85f) {
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
        return false;
    }

    private ActionEvent currentAction(float time) {
        for (int i = ACTIONS.length - 1; i >= 0; i--) {
            ActionEvent action = ACTIONS[i];
            if (time >= action.time) {
                if (time < action.time + ACTION_CINEMATIC_SECONDS) {
                    return action;
                }
                return null;
            }
        }
        return null;
    }

    private void drawHandOverlay() {
        float time = handTime();
        float worldWidth = viewport.getWorldWidth();
        float worldHeight = viewport.getWorldHeight();
        if (time < DEAL_START) {
            float width = Math.min(700f, worldWidth * 0.42f);
            float height = width * shuffleGif.height() / shuffleGif.width();
            float x = tableCenterX - width / 2f;
            float y = tableCenterY - height / 2f;
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(shuffleGif.frameAt(time, true), x, y, width, height);
            drawCentered(uiFont, "BARAJANDO GOLIAT", tableCenterX, y - 12f, Color.WHITE, 1f);
            batch.end();
            return;
        }

        ActionEvent action = currentAction(time);
        if (action == null) {
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
        float centerX = seat.x + towardX * 172f;
        float centerY = seat.y + towardY * 150f;
        float x = MathUtils.clamp(centerX - width / 2f, 18f, worldWidth - width - 18f);
        float y = MathUtils.clamp(centerY - height / 2f, 112f, worldHeight - height - 125f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int ring = 0; ring < 3; ring++) {
            float ringProgress = (progress * 1.7f - ring * 0.18f);
            if (ringProgress >= 0f && ringProgress <= 1f) {
                float radius = 58f + Interpolation.circleOut.apply(ringProgress) * 92f;
                shapes.setColor(actionColor.r, actionColor.g, actionColor.b,
                        (1f - ringProgress) * 0.20f * alpha);
                shapes.circle(seat.x, seat.y, radius, 64);
            }
        }
        for (int particle = 0; particle < 18; particle++) {
            float angle = MathUtils.PI2 * particle / 18f + action.seat * 0.71f;
            float radius = 52f + Interpolation.pow2Out.apply(progress) * (55f + particle % 4 * 13f);
            shapes.setColor(actionColor.r, actionColor.g, actionColor.b,
                    alpha * (0.12f + (particle % 3) * 0.04f));
            shapes.circle(seat.x + MathUtils.cos(angle) * radius,
                    seat.y + MathUtils.sin(angle) * radius, 2.5f + particle % 3, 10);
        }
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
            for (int j = 1; j <= 5; j++) {
                float t = Math.max(0f, u - j * 0.025f);
                float x = bezier(from.x, controlX(from.x, targetX, flight), targetX, t);
                float y = bezier(from.y, controlY(from.y, targetY, flight), targetY, t);
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
            float eased = Interpolation.pow2Out.apply(u);
            float x = bezier(from.x, controlX(from.x, targetX, flight), targetX, eased);
            float y = bezier(from.y, controlY(from.y, targetY, flight), targetY, eased);
            float landing = Interpolation.pow3In.apply(u);
            float size = (45f + MathUtils.sin(u * MathUtils.PI) * 9f) * (1f - landing * 0.28f);
            float alpha = MathUtils.clamp((1f - u) / 0.12f, 0f, 1f);
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

    private static float controlX(float from, float to, ChipFlight flight) {
        return (from + to) * 0.5f + MathUtils.cos(flight.rotation) * 100f;
    }

    private static float controlY(float from, float to, ChipFlight flight) {
        return Math.max(from, to) + 150f + MathUtils.sin(flight.rotation) * 55f;
    }

    private static float bezier(float from, float control, float to, float t) {
        float inverse = 1f - t;
        return inverse * inverse * from + 2f * inverse * t * control + t * t * to;
    }

    private void drawFooter(float width, float height) {
        float buttonY = 32f;
        float buttonW = 260f;
        float buttonH = 62f;
        String[] labels = {"CREAR PARTIDA", "UNIRSE", "AJUSTES"};
        float startX = width / 2f - (labels.length * buttonW + (labels.length - 1) * 24f) / 2f;

        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < labels.length; i++) {
            float x = startX + i * (buttonW + 24f);
            boolean hover = pointer.x >= x && pointer.x <= x + buttonW
                    && pointer.y >= buttonY && pointer.y <= buttonY + buttonH;
            shapes.setColor(hover ? BUTTON_HOVER : PANEL);
            shapes.rect(x, buttonY, buttonW, buttonH);
            shapes.setColor(hover ? CYAN : BUTTON_LINE);
            shapes.rect(x, buttonY, buttonW, 3f);
        }
        shapes.end();

        batch.begin();
        for (int i = 0; i < labels.length; i++) {
            float x = startX + i * (buttonW + 24f);
            drawCentered(smallFont, labels[i], x + buttonW / 2f, buttonY + 39f,
                    Color.WHITE, 1f);
        }
        smallFont.setColor(Color.LIGHT_GRAY);
        smallFont.draw(batch, "ESC salir   F11 pantalla completa   R reiniciar mano   I repetir intro   ESPACIO/clic efecto",
                34f, height - 152f);
        smallFont.setColor(Color.WHITE);
        batch.end();
    }

    private void drawCentered(BitmapFont font, String text, float centerX, float baselineY,
            Color color, float alpha) {
        font.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(font, text);
        font.draw(batch, text, centerX - glyph.width / 2f, baselineY);
        font.setColor(Color.WHITE);
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
        titleFont.dispose();
        uiFont.dispose();
        smallFont.dispose();
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
        for (Texture texture : communityCards) {
            texture.dispose();
        }
        shuffleGif.dispose();
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
                        && time >= action.time + 0.85f) {
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

        ChipFlight(int seat, float startTime, float duration, float rotation, int chipColor) {
            this.seat = seat;
            this.startTime = startTime;
            this.duration = duration;
            this.rotation = rotation;
            this.chipColor = chipColor;
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
