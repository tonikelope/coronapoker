/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Standalone side-by-side viewer. It does not open a game or a player profile. */
public final class GdxShufflePreview extends ApplicationAdapter {

    private static final float DURATION = GdxShuffleGeometry.CYCLE_SECONDS;
    private static final Color BACKGROUND = Color.valueOf("101917");
    private static final Color TABLE = Color.valueOf("184137");
    private static final Color PANEL = Color.valueOf("23342f");
    private static final Color INK = Color.valueOf("ededdf");
    private static final Color MUTED = Color.valueOf("a9bbb1");
    private static final Color ACCENT = Color.valueOf("e9c776");
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final float[] SPEEDS = {1f, .5f, .25f};
    private static final String[] SPEED_LABELS = {"Velocidad 1x", "Velocidad 1/2", "Velocidad 1/4"};

    private final FitViewport viewport = new FitViewport(1280, 720);
    private final Rectangle referencePanel = new Rectangle(40, 223, 590, 332);
    private final Rectangle nativePanel = new Rectangle(650, 223, 590, 332);
    private final Rectangle timeline = new Rectangle(48, 143, 1184, 24);
    private final Rectangle play = new Rectangle(48, 63, 146, 47);
    private final Rectangle restart = new Rectangle(207, 63, 122, 47);
    private final Rectangle speed = new Rectangle(342, 63, 140, 47);
    private final Rectangle deckControl = new Rectangle(40, 598, 282, 42);
    private final Rectangle backControl = new Rectangle(334, 598, 282, 42);
    private final Rectangle viewControl = new Rectangle(650, 598, 200, 42);
    private final Vector2 pointer = new Vector2();
    private final Path captureDirectory;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private StreamingGifTextureAnimation reference;
    private GdxRiffleShuffle riffle;
    private Texture back, faces;
    private final Path customDeck, customBack;
    private int deckIndex, backIndex;
    private boolean fromAbove;
    private float elapsed;
    private int speedIndex;
    private int captureIndex;
    private boolean playing = true;
    private boolean scrubbing;

    private GdxShufflePreview(String[] args) {
        Path captures = null, deckPath = null, backPath = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--capture" -> captures = Path.of(args[++i]);
                case "--overhead" -> fromAbove = true;
                case "--deck" -> {
                    String deck = args[++i];
                    deckIndex = Arrays.asList(GdxShuffleArtwork.DECKS).indexOf(deck);
                    if (deckIndex < 0) deckPath = Path.of(deck).toAbsolutePath();
                }
                case "--back" -> {
                    String deck = args[++i];
                    backIndex = Arrays.asList(GdxShuffleArtwork.DECKS).indexOf(deck);
                    if (backIndex < 0) backPath = Path.of(deck).toAbsolutePath();
                }
                default -> throw new IllegalArgumentException(args[i]);
            }
        }
        captureDirectory = captures;
        customDeck = deckPath;
        customBack = backPath;
    }

    public static void main(String[] args) {
        var viewer = new GdxShufflePreview(args);
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker · Visor de barajado");
        config.setWindowedMode(1280, 720);
        config.setWindowSizeLimits(840, 480, -1, -1);
        config.setInitialVisible(viewer.captureDirectory == null);
        config.setResizable(true);
        config.setHdpiMode(HdpiMode.Pixels);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 4);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.disableAudio(true);
        new Lwjgl3Application(viewer, config);
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        riffle = new GdxRiffleShuffle();
        loadDeck();
        loadBack();
        playing = captureDirectory == null;
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                switch (key) {
                    case Input.Keys.SPACE -> playing = !playing;
                    case Input.Keys.R -> elapsed = 0f;
                    case Input.Keys.V -> fromAbove = !fromAbove;
                    case Input.Keys.LEFT -> step(-.02f);
                    case Input.Keys.RIGHT -> step(.02f);
                    default -> { return false; }
                }
                return true;
            }

            @Override public boolean touchDown(int x, int y, int index, int button) {
                if (button != Input.Buttons.LEFT) return false;
                toWorld(x, y);
                if (timeline.contains(pointer)) {
                    scrubbing = true;
                    seek();
                } else if (deckControl.contains(pointer)) {
                    deckIndex = next(deckIndex, customDeck);
                    loadDeck();
                } else if (backControl.contains(pointer)) {
                    backIndex = next(backIndex, customBack);
                    loadBack();
                } else if (viewControl.contains(pointer)) {
                    fromAbove = !fromAbove;
                } else if (play.contains(pointer)) {
                    playing = !playing;
                } else if (restart.contains(pointer)) {
                    elapsed = 0f;
                } else if (speed.contains(pointer)) {
                    speedIndex = (speedIndex + 1) % SPEEDS.length;
                } else {
                    return false;
                }
                return true;
            }

            @Override public boolean touchDragged(int x, int y, int index) {
                if (!scrubbing) return false;
                toWorld(x, y);
                seek();
                return true;
            }

            @Override public boolean touchUp(int x, int y, int index, int button) {
                boolean owned = scrubbing;
                scrubbing = false;
                return owned;
            }
        });
    }

    private static int next(int index, Path custom) {
        return index + 1 == GdxShuffleArtwork.DECKS.length ? (custom == null ? 0 : -1) : index + 1;
    }

    private void loadDeck() {
        FileHandle dir = deckIndex < 0 ? Gdx.files.absolute(customDeck.toString())
                : GdxShuffleArtwork.directory(GdxShuffleArtwork.DECKS[deckIndex]);
        FileHandle hq = deckIndex >= 0 || dir.child("hq/2_P.jpg").exists() ? dir.child("hq") : dir;
        Texture replacement = GdxShuffleArtwork.faces(hq);
        if (faces != null) faces.dispose();
        faces = replacement;
        if (reference != null) reference.dispose();
        reference = null;
        FileHandle gif = dir.child("gif/shuffle.gif");
        if (gif.exists()) {
            try {
                var prepared = StreamingGifTextureAnimation.prepareLoopingFully(gif.readBytes(), gif.path(), 590, () -> false);
                reference = StreamingGifTextureAnimation.loadLooping(prepared);
            } catch (IOException failure) {
                throw new IllegalStateException("El GIF de referencia no se pudo abrir", failure);
            }
        }
    }

    private void loadBack() {
        FileHandle file = backIndex < 0 ? Gdx.files.absolute(customBack.toString())
                : GdxShuffleArtwork.directory(GdxShuffleArtwork.DECKS[backIndex]).child("hq/trasera.jpg");
        Texture replacement = GdxShuffleArtwork.back(file);
        if (back != null) back.dispose();
        back = replacement;
    }

    private void toWorld(int x, int y) {
        viewport.unproject(pointer.set(x, y));
    }

    private void seek() {
        playing = false;
        elapsed = MathUtils.clamp((pointer.x - timeline.x) / timeline.width, 0f, 1f) * DURATION;
    }

    private void step(float seconds) {
        playing = false;
        elapsed = MathUtils.clamp(elapsed + seconds, 0f, DURATION);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        if (captureDirectory != null) {
            elapsed = captureIndex * GdxShuffleGeometry.FRAME_SECONDS;
        } else if (playing) {
            elapsed = (elapsed + Gdx.graphics.getDeltaTime() * SPEEDS[speedIndex]) % DURATION;
        }
        ScreenUtils.clear(BACKGROUND, true);
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        fill(referencePanel, TABLE);
        fill(nativePanel, TABLE);
        fill(play, ACCENT);
        fill(restart, PANEL);
        fill(speed, PANEL);
        fill(deckControl, PANEL);
        fill(backControl, PANEL);
        fill(viewControl, PANEL);
        shapes.setColor(PANEL);
        shapes.rect(timeline.x, timeline.y + 10f, timeline.width, 4f);
        float progressWidth = timeline.width * elapsed / DURATION;
        shapes.setColor(ACCENT);
        shapes.rect(timeline.x, timeline.y + 10f, progressWidth, 4f);
        shapes.circle(timeline.x + progressWidth, timeline.y + 12f, 7f, 24);
        shapes.end();

        int nativeWidth = Math.round(nativePanel.width * viewport.getScreenWidth() / 1280f);
        int nativeHeight = Math.round(nativePanel.height * viewport.getScreenHeight() / 720f);
        int nativeX = viewport.getScreenX() + Math.round(nativePanel.x * viewport.getScreenWidth() / 1280f);
        int nativeY = viewport.getScreenY() + Math.round(nativePanel.y * viewport.getScreenHeight() / 720f);
        riffle.draw(elapsed, nativeX, nativeY, nativeWidth, nativeHeight, back, faces, fromAbove);
        viewport.apply();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        Texture original = reference == null ? null : reference.frameAt(Math.min(elapsed + .00001f, DURATION - .001f));
        if (original != null) {
            batch.draw(original, referencePanel.x, referencePanel.y,
                    referencePanel.width, referencePanel.height);
        }
        text("Barajado", 40, 677, 1.9f, INK);
        text("26 + 26 cartas    /    1,72 s", 1024, 667, 1.1f, MUTED);
        text(reference == null ? "Sin GIF de referencia" : "GIF original de la baraja", 40, 581, 1.25f, INK);
        text("Barajado GDX", 650, 581, 1.25f, INK);
        button("Baraja · " + (deckIndex < 0 ? customDeck.getFileName() : GdxShuffleArtwork.DECKS[deckIndex]), deckControl, INK);
        button("Dorso · " + (backIndex < 0 ? customBack.getFileName() : GdxShuffleArtwork.DECKS[backIndex]), backControl, INK);
        button(fromAbove ? "Vista cenital 2D" : "Vista en perspectiva 3D", viewControl, INK);
        text(String.format(SPANISH, "%.3f s", elapsed), 1148, 189, 1.13f, MUTED);
        button(playing ? "Pausar" : "Reproducir", play, BACKGROUND);
        button("Reiniciar", restart, INK);
        button(SPEED_LABELS[speedIndex], speed, INK);
        text("Espacio para pausar · Flechas para avanzar o retroceder un fotograma", 530, 92, 1.05f, MUTED);
        batch.end();
        if (captureDirectory != null) capture();
    }

    private void fill(Rectangle bounds, Color color) {
        shapes.setColor(color);
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private void text(String value, float x, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(batch, value, x, y);
    }

    private void button(String value, Rectangle bounds, Color color) {
        font.getData().setScale(1.12f);
        font.setColor(color);
        font.draw(batch, value, bounds.x, bounds.y + 30f, bounds.width, Align.center, false);
    }

    private void capture() {
        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight());
        PixmapIO.PNG png = new PixmapIO.PNG();
        try {
            png.setFlipY(true);
            png.write(Gdx.files.absolute(captureDirectory.resolve("shuffle-" + captureIndex + ".png").toString()), pixels);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        } finally {
            png.dispose();
            pixels.dispose();
        }
        if (++captureIndex == GdxShuffleGeometry.FRAME_COUNT) Gdx.app.exit();
    }

    @Override
    public void dispose() {
        if (reference != null) reference.dispose();
        riffle.dispose();
        back.dispose();
        faces.dispose();
        font.dispose();
        shapes.dispose();
        batch.dispose();
    }
}
