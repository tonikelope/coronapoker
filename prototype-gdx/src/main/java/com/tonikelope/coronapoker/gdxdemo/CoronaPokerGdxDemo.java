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
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import java.util.Arrays;

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
    private static final int FLIGHT_COUNT = 18;
    private static final int FRAME_SAMPLE_COUNT = 720;

    private static final Color BACKGROUND_TOP = new Color(0x07111fff);
    private static final Color BACKGROUND_BOTTOM = new Color(0x02050cff);
    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color CYAN_SOFT = new Color(0x36d9ff55);
    private static final Color ORANGE = new Color(0xff6b27ff);
    private static final Color FELT = new Color(0x075c3fff);
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

    private final int detectedRefreshRate;
    private final Star[] stars = new Star[STAR_COUNT];
    private final Seat[] seats = new Seat[SEAT_COUNT];
    private final ChipFlight[] flights = new ChipFlight[FLIGHT_COUNT];
    private final float[] frameSamples = new float[FRAME_SAMPLE_COUNT];
    private final float[] frameScratch = new float[FRAME_SAMPLE_COUNT];
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;

    private BitmapFont titleFont;
    private BitmapFont uiFont;
    private BitmapFont smallFont;

    private Texture logo;
    private Texture cardBack;
    private Texture chip;
    private Texture pot;
    private Texture[] communityCards;

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

        logo = texture("images/corona_poker_splash.png");
        cardBack = texture("images/decks/coronapoker/trasera.jpg");
        chip = texture("images/chips.png");
        pot = texture("images/pot.png");
        communityCards = new Texture[]{
            texture("images/decks/coronapoker/A_P.jpg"),
            texture("images/decks/coronapoker/K_D.jpg"),
            texture("images/decks/coronapoker/Q_C.jpg"),
            texture("images/decks/coronapoker/J_T.jpg"),
            texture("images/decks/coronapoker/10_P.jpg")
        };

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
        for (int i = 0; i < flights.length; i++) {
            flights[i] = new ChipFlight(i % seats.length, i * 0.19f,
                    1.0f + (i % 5) * 0.08f, i * 37f);
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
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.rect(0, 0, width, height, BACKGROUND_BOTTOM, BACKGROUND_BOTTOM,
                BACKGROUND_TOP, BACKGROUND_TOP);

        Gdx.gl.glEnable(GL20.GL_BLEND);
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

        float cardW = 150f;
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float cardEase = Interpolation.pow3Out.apply(MathUtils.clamp((sceneTime - 0.45f) / 1.1f, 0f, 1f));
        batch.draw(cardBack, width * 0.17f - cardW / 2f, height * 0.48f - cardH / 2f,
                cardW / 2f, cardH / 2f, cardW, cardH, cardEase, cardEase,
                -28f + 360f * (1f - cardEase), 0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);
        batch.draw(cardBack, width * 0.83f - cardW / 2f, height * 0.48f - cardH / 2f,
                cardW / 2f, cardH / 2f, cardW, cardH, cardEase, cardEase,
                28f - 360f * (1f - cardEase), 0, 0, cardBack.getWidth(), cardBack.getHeight(), false, false);

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

        updateSeatPositions(tableCx, tableCy, tableW, tableH);
        drawHeader(width, height);
        drawTableGlow(tableCx, tableCy, tableW, tableH);
        drawTable(tableCx, tableCy, tableW, tableH);
        drawChipTrails(tableCx, tableCy);
        drawSeats();
        drawCardsAndPot(tableCx, tableCy, tableW);
        drawFlyingChips(tableCx, tableCy);
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
        drawCentered(smallFont, "OPENGL + LWJGL3  |  ANIMACION TEMPORAL  |  V-SYNC",
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
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        for (Seat seat : seats) {
            boolean active = seat.index == ((int) (sceneTime / 2.1f) % seats.length);
            if (active) {
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                shapes.setColor(CYAN.r, CYAN.g, CYAN.b,
                        0.18f + 0.12f * MathUtils.sin(totalTime * 4f));
                shapes.circle(seat.x, seat.y, 74f, 48);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            shapes.setColor(PANEL);
            shapes.circle(seat.x, seat.y, 61f, 48);
            shapes.setColor(active ? CYAN : SEAT_RIM);
            shapes.circle(seat.x, seat.y, 54f, 48);
            shapes.setColor(SEAT_INNER);
            shapes.circle(seat.x, seat.y, 48f, 48);
        }
        shapes.end();

        batch.begin();
        for (Seat seat : seats) {
            drawCentered(smallFont, Integer.toString(seat.index + 1), seat.x, seat.y + 8f,
                    Color.WHITE, 1f);
            drawCentered(smallFont, seat.name, seat.x, seat.y - 72f,
                    Color.WHITE, 1f);
            drawCentered(smallFont, seat.stackText, seat.x, seat.y - 97f,
                    STACK_GREEN, 1f);
        }
        batch.end();
    }

    private void drawCardsAndPot(float cx, float cy, float tableWidth) {
        float cardW = Math.min(126f, tableWidth / 12f);
        float cardH = cardW * cardBack.getHeight() / cardBack.getWidth();
        float gap = cardW + 18f;
        float firstX = cx - gap * 2f - cardW / 2f;
        float cardY = cy - cardH * 0.36f;

        batch.begin();
        for (int i = 0; i < communityCards.length; i++) {
            float local = MathUtils.clamp((sceneTime - i * 0.18f) / 0.72f, 0f, 1f);
            float flip = MathUtils.sin(local * MathUtils.PI - MathUtils.PI / 2f);
            float visibleWidth = Math.max(2f, cardW * Math.abs(flip));
            Texture shown = flip >= 0f ? communityCards[i] : cardBack;
            float x = firstX + i * gap + (cardW - visibleWidth) / 2f;
            batch.setColor(0f, 0f, 0f, 0.35f);
            batch.draw(shown, x + 7f, cardY - 8f, visibleWidth, cardH);
            batch.setColor(Color.WHITE);
            batch.draw(shown, x, cardY, visibleWidth, cardH);
        }

        float pulse = 1f + MathUtils.sin(totalTime * 3.3f) * 0.035f;
        float potW = 108f * pulse;
        float potH = potW * pot.getHeight() / pot.getWidth();
        batch.draw(pot, cx - potW / 2f, cy + cardH * 0.73f, potW, potH);
        drawCentered(uiFont, "BOTE  12.450", cx, cy + cardH * 0.66f,
                POT_GOLD, 1f);
        batch.end();
    }

    private void drawChipTrails(float targetX, float targetY) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (ChipFlight flight : flights) {
            Seat from = seats[flight.seat];
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
            float size = 54f + MathUtils.sin(u * MathUtils.PI) * 16f;
            batch.draw(chip, x - size / 2f, y - size / 2f, size / 2f, size / 2f,
                    size, size, 1f, 1f, flight.rotation + u * 540f,
                    0, 0, chip.getWidth(), chip.getHeight(), false, false);
        }
        batch.end();
    }

    private float flightProgress(ChipFlight flight) {
        float cycle = 3.7f;
        float raw = (sceneTime - flight.offset) % cycle;
        if (raw < 0f) {
            raw += cycle;
        }
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
        smallFont.draw(batch, "ESC salir   F11 pantalla completa   R repetir intro   ESPACIO/clic explosión",
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
        titleFont.dispose();
        uiFont.dispose();
        smallFont.dispose();
        logo.dispose();
        cardBack.dispose();
        chip.dispose();
        pot.dispose();
        for (Texture texture : communityCards) {
            texture.dispose();
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
        final int stack;
        final String stackText;
        final int index;
        float x;
        float y;

        Seat(String name, int stack, int index) {
            this.name = name;
            this.stack = stack;
            this.stackText = String.format("%,d", stack);
            this.index = index;
        }
    }

    private static final class ChipFlight {

        final int seat;
        final float offset;
        final float duration;
        final float rotation;

        ChipFlight(int seat, float offset, float duration, float rotation) {
            this.seat = seat;
            this.offset = offset;
            this.duration = duration;
            this.rotation = rotation;
        }
    }
}
