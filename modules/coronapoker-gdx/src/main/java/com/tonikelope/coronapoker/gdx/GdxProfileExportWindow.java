/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.glfw.GLFW;

/** Shows export progress after the main window has closed the profile. */
final class GdxProfileExportWindow extends ApplicationAdapter {

    private static final int WIDTH = 440;
    private static final int HEIGHT = 280;
    private static final float CORNER_RADIUS = 15f;
    private static final float LOGO_WIDTH = 146f;
    private static final Rectangle CANCEL_BUTTON = new Rectangle(144f, 18f, 152f, 40f);
    private static final Color BACKGROUND = new Color(0x071321ff);
    private static final Color TRACK = new Color(0x263952ff);
    private static final Color FILL = new Color(0x52cee0ff);
    private static final Color STATUS = new Color(0xc5d4e6ff);

    private final AtomicInteger percent = new AtomicInteger(-1);
    private final GdxProfileBackup.ExportControl export =
            new GdxProfileBackup.ExportControl(percent::set);
    private final GdxGameText text;
    private final CompletableFuture<GdxProfileBackupTransfer.Result> result;
    private final ScreenViewport viewport = new ScreenViewport();
    private final GlyphLayout glyph = new GlyphLayout();
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private Texture logo;
    private BitmapFont titleFont;
    private BitmapFont statusFont;
    private boolean transparentBackground;
    private float cornerFeather;
    private float elapsed;
    private boolean cancelPressed;

    private GdxProfileExportWindow(Path profile, GdxProfileBackupTransfer.Request backup) {
        text = new GdxGameText(backup.language());
        result = CompletableFuture.supplyAsync(() -> backup.run(profile, export),
                task -> new Thread(task, "coronapoker-profile-export").start());
    }

    static GdxProfileBackupTransfer.Result run(Path profile,
            GdxProfileBackupTransfer.Request backup) {
        var window = new GdxProfileExportWindow(profile, backup);
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker - " + window.text.translate("gdx.backup.export"));
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setDecorated(false);
        config.setResizable(false);
        config.disableAudio(true);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
        config.setTransparentFramebuffer(true);
        config.setForegroundFPS(30);
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public boolean closeRequested() {
                return false;
            }
        });
        try {
            if (!window.result.isDone()) new Lwjgl3Application(window, config);
        } catch (RuntimeException | LinkageError failure) {
            Logger.getLogger(GdxProfileExportWindow.class.getName()).log(Level.WARNING,
                    "Could not show ZIP export progress", failure);
        }
        return window.result.join();
    }

    @Override
    public void create() {
        var graphics = (Lwjgl3Graphics) Gdx.graphics;
        var window = graphics.getWindow();
        transparentBackground = graphics.getBufferFormat().a > 0
                && GLFW.glfwGetWindowAttrib(window.getWindowHandle(),
                        GLFW.GLFW_TRANSPARENT_FRAMEBUFFER) == GLFW.GLFW_TRUE;
        if (!GdxDisplayModeController.isWayland()) {
            Pixmap icon = new Pixmap(Gdx.files.internal("images/corona_poker_16.png"));
            try {
                window.setIcon(icon);
            } finally {
                icon.dispose();
            }
        }
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        // Keep the panel opaque where partially covered glyphs and artwork are drawn.
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        logo = new Texture(Gdx.files.internal("images/corona_poker_splash.png"), true);
        logo.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);

        var profile = GdxAdaptiveFontQuality.current(WIDTH, HEIGHT);
        cornerFeather = GdxAntialiasedRoundedRect.TARGET_PHYSICAL_FEATHER_PIXELS
                / profile.physicalPixelsPerWorldUnit();
        var generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        try {
            titleFont = GdxAdaptiveFontQuality.generate(generator, 20, 0f,
                    Color.WHITE, Color.CLEAR, null, profile);
            statusFont = GdxAdaptiveFontQuality.generate(generator, 17, 0f,
                    Color.WHITE, Color.CLEAR, null, profile);
        } finally {
            generator.dispose();
        }
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                cancelPressed = export.cancellable() && overCancel(x, y);
                return cancelPressed;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                if (button != Input.Buttons.LEFT || !cancelPressed) return false;
                cancelPressed = false;
                if (overCancel(x, y)) export.cancel();
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                return keycode == Input.Keys.ESCAPE && export.cancel();
            }
        });
    }

    private static boolean overCancel(int x, int y) {
        return CANCEL_BUTTON.contains(x, HEIGHT - y);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }

    @Override
    public void render() {
        if (result.isDone()) {
            Gdx.app.exit();
            return;
        }
        GdxPixelText.beginFrame();
        int progress = percent.get();
        boolean cancelEnabled = export.cancellable();
        boolean hover = cancelEnabled && overCancel(Gdx.input.getX(), Gdx.input.getY());
        ScreenUtils.clear(transparentBackground ? Color.CLEAR : BACKGROUND);
        float trackX = 32f;
        float trackY = 119f;
        float trackW = WIDTH - 2f * trackX;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (transparentBackground) {
            shapes.setColor(BACKGROUND);
            GdxAntialiasedRoundedRect.fill(shapes, 0f, 0f, WIDTH, HEIGHT,
                    CORNER_RADIUS, cornerFeather);
        }
        shapes.setColor(TRACK);
        shapes.rect(trackX, trackY, trackW, 12f);
        shapes.setColor(FILL);
        if (progress < 0) {
            elapsed += Gdx.graphics.getDeltaTime();
            float segment = trackW * 0.24f;
            float offset = (trackW - segment) * (0.5f - 0.5f * MathUtils.cos(elapsed * 4f));
            shapes.rect(trackX + offset, trackY, segment, 12f);
        } else {
            shapes.rect(trackX, trackY, trackW * progress / 100f, 12f);
        }
        var tone = GdxUiButtonStyle.Tone.NEUTRAL;
        GdxUiButtonStyle.draw(shapes, CANCEL_BUTTON.x, CANCEL_BUTTON.y,
                CANCEL_BUTTON.width, CANCEL_BUTTON.height, tone,
                cancelEnabled, hover ? 1f : 0f, hover && cancelPressed, 1f);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        String status = export.cancelled() ? text.translate("gdx.backup.export_cancelling")
                : switch (progress) {
                    case -1 -> text.translate("gdx.backup.export_preparing");
                    case 100 -> text.translate("gdx.backup.export_finalizing");
                    default -> progress + " %";
                };
        batch.begin();
        float logoHeight = LOGO_WIDTH * logo.getHeight() / logo.getWidth();
        batch.draw(logo, (WIDTH - LOGO_WIDTH) / 2f, HEIGHT - 18f - logoHeight,
                LOGO_WIDTH, logoHeight);
        drawCentered(titleFont, text.translate("gdx.backup.export_creating"),
                WIDTH / 2f, 162f);
        statusFont.setColor(STATUS);
        drawCentered(statusFont, status, WIDTH / 2f, 102f);
        statusFont.setColor(GdxUiButtonStyle.labelColor(tone, cancelEnabled));
        drawCentered(statusFont, text.translate("ui.cancelar"),
                CANCEL_BUTTON.x + CANCEL_BUTTON.width / 2f,
                CANCEL_BUTTON.y + (CANCEL_BUTTON.height + statusFont.getCapHeight()) / 2f);
        batch.end();
    }

    private void drawCentered(BitmapFont font, String label, float centerX, float y) {
        glyph.setText(font, label);
        GdxPixelText.draw(font, batch, glyph, centerX - glyph.width / 2f, y, viewport);
    }

    @Override
    public void dispose() {
        if (statusFont != null) statusFont.dispose();
        if (titleFont != null) titleFont.dispose();
        if (logo != null) logo.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }
}
