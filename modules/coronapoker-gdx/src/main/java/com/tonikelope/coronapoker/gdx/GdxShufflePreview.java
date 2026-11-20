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

    private GdxShuffleGeometry.Motion motion = GdxShuffleGeometry.Motion.MID_BRIDGE;
    private static final Color BACKGROUND = Color.valueOf("101917");
    private static final Color TABLE = Color.valueOf("184137");
    private static final Color PANEL = Color.valueOf("23342f");
    private static final Color INK = Color.valueOf("ededdf");
    private static final Color MUTED = Color.valueOf("a9bbb1");
    private static final Color ACCENT = Color.valueOf("e9c776");
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final float[] SPEEDS = {1f, .5f, .25f};
    private static final String[] SPEED_LABELS = {"Velocidad 1x", "Velocidad 1/2", "Velocidad 1/4"};
    private static final float[] SCALES = {1f, .65f, .35f};

    private final FitViewport viewport = new FitViewport(1280, 720);
    private final Rectangle referencePanel = new Rectangle(40, 223, 590, 332);
    private final Rectangle nativePanel = new Rectangle(650, 223, 590, 332);
    private final Rectangle timeline = new Rectangle(48, 143, 1184, 24);
    private final Rectangle play = new Rectangle(48, 63, 146, 47);
    private final Rectangle restart = new Rectangle(207, 63, 122, 47);
    private final Rectangle speed = new Rectangle(342, 63, 140, 47);
    private static final GdxShuffleGeometry.Motion[] MOTIONS = GdxShuffleGeometry.Motion.values();
    private final Rectangle[] motionControls = {
        new Rectangle(48, 12, 208, 38), new Rectangle(268, 12, 208, 38),
        new Rectangle(488, 12, 256, 38), new Rectangle(756, 12, 208, 38),
        new Rectangle(976, 12, 256, 38)
    };
    private final Rectangle deckControl = new Rectangle(40, 598, 282, 42);
    private final Rectangle backControl = new Rectangle(334, 598, 282, 42);
    private final Rectangle viewControl = new Rectangle(650, 598, 200, 42);
    private final Vector2 pointer = new Vector2();
    private final Path captureDirectory;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private final java.util.Map<Integer, BitmapFont> fonts = new java.util.HashMap<>();
    private com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator fontGenerator;
    private GdxRiffleShuffle riffle;
    private Texture back;
    private GdxShuffleArtwork faces;
    private final Path customDeck, customBack;
    private int deckIndex, backIndex;
    private GdxRiffleShuffle.View view = GdxRiffleShuffle.View.PERSPECTIVE;
    private int frontCard = 10, scaleIndex;
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
                case "--overhead" -> view = GdxRiffleShuffle.View.OVERHEAD;
                case "--front" -> view = GdxRiffleShuffle.View.FRONT;
                case "--full-deck" -> motion = GdxShuffleGeometry.Motion.FULL_DECK;
                case "--bridge" -> motion = GdxShuffleGeometry.Motion.BRIDGE;
                case "--open-bridge" -> motion = GdxShuffleGeometry.Motion.OPEN_BRIDGE;
                case "--classic" -> motion = GdxShuffleGeometry.Motion.ORIGINAL;
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
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0,
                GdxGamePresentationSettings.supportedMsaa(Integer.getInteger("coronapoker.lab.msaa", 4)));
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.disableAudio(true);
        new Lwjgl3Application(viewer, config);
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        fontGenerator = new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        riffle = new GdxRiffleShuffle();
        loadDeck();
        loadBack();
        playing = captureDirectory == null;
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                switch (key) {
                    case Input.Keys.SPACE -> playing = !playing;
                    case Input.Keys.R -> elapsed = 0f;
                    case Input.Keys.V -> view = view.next();
                    case Input.Keys.F -> view = GdxRiffleShuffle.View.FRONT;
                    case Input.Keys.M -> selectMotion(MOTIONS[(motion.ordinal() + 1) % MOTIONS.length]);
                    case Input.Keys.NUM_1 -> selectMotion(GdxShuffleGeometry.Motion.ORIGINAL);
                    case Input.Keys.NUM_2 -> selectMotion(GdxShuffleGeometry.Motion.FULL_DECK);
                    case Input.Keys.NUM_3 -> selectMotion(GdxShuffleGeometry.Motion.BRIDGE);
                    case Input.Keys.NUM_4 -> selectMotion(GdxShuffleGeometry.Motion.OPEN_BRIDGE);
                    case Input.Keys.NUM_5 -> selectMotion(GdxShuffleGeometry.Motion.MID_BRIDGE);
                    case Input.Keys.LEFT -> navigate(-1);
                    case Input.Keys.RIGHT -> navigate(1);
                    default -> { return false; }
                }
                return true;
            }

            @Override public boolean touchDown(int x, int y, int index, int button) {
                if (button != Input.Buttons.LEFT) return false;
                toWorld(x, y);
                if (!frontal()) {
                    for (int i = 0; i < motionControls.length; i++) {
                        if (motionControls[i].contains(pointer)) {
                            selectMotion(MOTIONS[i]);
                            return true;
                        }
                    }
                }
                if (!frontal() && timeline.contains(pointer)) {
                    scrubbing = true;
                    seek();
                } else if (deckControl.contains(pointer)) {
                    deckIndex = next(deckIndex, customDeck);
                    loadDeck();
                } else if (backControl.contains(pointer)) {
                    backIndex = next(backIndex, customBack);
                    loadBack();
                } else if (viewControl.contains(pointer)) {
                    view = view.next();
                } else if (play.contains(pointer)) {
                    if (frontal()) navigate(-1);
                    else playing = !playing;
                } else if (restart.contains(pointer)) {
                    if (frontal()) navigate(1);
                    else elapsed = 0f;
                } else if (speed.contains(pointer)) {
                    if (frontal()) scaleIndex = (scaleIndex + 1) % SCALES.length;
                    else speedIndex = (speedIndex + 1) % SPEEDS.length;
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
        GdxShuffleArtwork replacement = new GdxShuffleArtwork(hq);
        if (faces != null) faces.dispose();
        faces = replacement;
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

    private boolean frontal() {
        return view == GdxRiffleShuffle.View.FRONT;
    }

    private void selectMotion(GdxShuffleGeometry.Motion selected) {
        if (frontal() || selected == motion) return;
        elapsed = motion.hasBridge() && selected.hasBridge() ? Math.min(elapsed, selected.duration()) : 0f;
        motion = selected;
    }

    private void navigate(int direction) {
        if (frontal()) frontCard = Math.floorMod(frontCard + direction, GdxShuffleGeometry.CARDS);
        else step(direction * GdxShuffleGeometry.FRAME_SECONDS);
    }

    private void seek() {
        playing = false;
        elapsed = MathUtils.clamp((pointer.x - timeline.x) / timeline.width, 0f, 1f) * motion.duration();
    }

    private void step(float seconds) {
        playing = false;
        elapsed = MathUtils.clamp(elapsed + seconds, 0f, motion.duration());
    }

    @Override
    public void resize(int width, int height) {
        fonts.values().forEach(BitmapFont::dispose);
        fonts.clear();
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        if (captureDirectory != null) {
            elapsed = captureIndex * GdxShuffleGeometry.FRAME_SECONDS;
        } else if (playing && !frontal()) {
            elapsed = (elapsed + Gdx.graphics.getDeltaTime() * SPEEDS[speedIndex]) % motion.duration();
        }
        GdxPixelText.beginFrame();
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
        if (!frontal()) {
            for (int i = 0; i < motionControls.length; i++) {
                fill(motionControls[i], motion == MOTIONS[i] ? ACCENT : PANEL);
            }
            shapes.setColor(PANEL);
            shapes.rect(timeline.x, timeline.y + 10f, timeline.width, 4f);
            float progressWidth = timeline.width * elapsed / motion.duration();
            shapes.setColor(ACCENT);
            shapes.rect(timeline.x, timeline.y + 10f, progressWidth, 4f);
            shapes.circle(timeline.x + progressWidth, timeline.y + 12f, 7f, 24);
        }
        shapes.end();

        int nativeWidth = Math.round(nativePanel.width * viewport.getScreenWidth() / 1280f);
        int nativeHeight = Math.round(nativePanel.height * viewport.getScreenHeight() / 720f);
        int nativeX = viewport.getScreenX() + Math.round(nativePanel.x * viewport.getScreenWidth() / 1280f);
        int nativeY = viewport.getScreenY() + Math.round(nativePanel.y * viewport.getScreenHeight() / 720f);
        Texture face = faces.face(0);
        riffle.prepare(elapsed, view, motion, (float) face.getWidth() / face.getHeight());
        int left = viewport.getScreenX() + Math.round(referencePanel.x * viewport.getScreenWidth() / 1280f);
        riffle.draw(left, nativeY, nativeWidth, nativeHeight, back, faces, view, true, frontCard, SCALES[scaleIndex]);
        riffle.draw(nativeX, nativeY, nativeWidth, nativeHeight, back, faces, view, false, frontCard, SCALES[scaleIndex]);
        viewport.apply();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        text("Visor de cartas", 40, 677, 1.9f, INK);
        if (!frontal()) text(String.format(SPANISH, "26 + 26 cartas    /    %.2f s", motion.duration()), 1024, 667, 1.1f, MUTED);
        text("CoronaPoker · Suave 16X", 40, 581, 1.25f, INK);
        text("Equilibrado · Anisotrópico 16X", 650, 581, 1.25f, INK);
        button("Baraja · " + (deckIndex < 0 ? customDeck.getFileName() : GdxShuffleArtwork.DECKS[deckIndex]), deckControl, INK);
        button("Dorso · " + (backIndex < 0 ? customBack.getFileName() : GdxShuffleArtwork.DECKS[backIndex]), backControl, INK);
        button("Vista · " + view.label, viewControl, INK);
        if (frontal()) {
            text(GdxShuffleArtwork.label(frontCard), 48, 189, 1.13f, MUTED);
            button("Anterior", play, BACKGROUND);
            button("Siguiente", restart, INK);
            button("Tamaño " + Math.round(SCALES[scaleIndex] * 100f) + "%", speed, INK);
            text("Flechas para cambiar de carta · V para cambiar de vista", 530, 92, 1.05f, MUTED);
        } else {
            text(String.format(SPANISH, "%.3f s", elapsed), 1148, 189, 1.13f, MUTED);
            button(playing ? "Pausar" : "Reproducir", play, BACKGROUND);
            button("Reiniciar", restart, INK);
            button(SPEED_LABELS[speedIndex], speed, INK);
            for (int i = 0; i < motionControls.length; i++) {
                button(MOTIONS[i].label, motionControls[i], motion == MOTIONS[i] ? BACKGROUND : INK);
            }
            text("Espacio · pausa     Flechas · fotograma     3/4/5 · puentes     V · vista", 530, 92, 1.05f, MUTED);
        }
        batch.end();
        if (captureDirectory != null) capture();
    }

    private void fill(Rectangle bounds, Color color) {
        shapes.setColor(color);
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private BitmapFont font(float scale) {
        return fonts.computeIfAbsent(Math.round(15 * scale), size -> GdxAdaptiveFontQuality.generate(
                fontGenerator, size, 0f, Color.WHITE, Color.CLEAR, "", GdxAdaptiveFontQuality.current(1280, 720)));
    }

    private void text(String value, float x, float y, float scale, Color color) {
        BitmapFont font = font(scale);
        font.setColor(color);
        GdxPixelText.draw(font, batch, value, x, y, viewport);
    }

    private void button(String value, Rectangle bounds, Color color) {
        BitmapFont font = font(1.12f);
        font.setColor(color);
        GdxPixelText.draw(font, batch, value, bounds.x, bounds.y + bounds.height * .5f + 6.5f, bounds.width, Align.center, false, viewport);
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
        if (++captureIndex == motion.leadFrames + motion.releaseFrames || frontal()) Gdx.app.exit();
    }

    @Override
    public void dispose() {
        riffle.dispose();
        back.dispose();
        faces.dispose();
        fonts.values().forEach(BitmapFont::dispose);
        fontGenerator.dispose();
        shapes.dispose();
        batch.dispose();
    }
}
