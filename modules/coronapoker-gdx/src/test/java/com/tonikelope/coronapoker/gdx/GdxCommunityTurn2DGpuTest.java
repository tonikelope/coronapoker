/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual GPU checks for the flexible 2D sheet and its handoff to resting artwork. */
@Tag("certification")
final class GdxCommunityTurn2DGpuTest {
    private static final int WIDTH = 960, HEIGHT = 540;
    private static final float CARD_WIDTH = 180f, CARD_HEIGHT = 252f;
    private static final float CANVAS = CoronaPokerGdxTable.cardShaderCanvasScale();

    @Test void bothDirectionsReturnToTheExactRestingArtworkAndFootprint() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker flexible 2D community card certification");
        config.setInitialVisible(false);
        var monitors = Lwjgl3ApplicationConfiguration.getMonitors();
        var monitor = monitors[Math.min(1, monitors.length - 1)];
        config.setWindowPosition(monitor.virtualX + 8, monitor.virtualY + 8);
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void create() {
                try { certifyAndCapture(); }
                catch (Throwable problem) { failure.set(problem); }
                finally { Gdx.app.exit(); }
            }
        }, config);
        assertNull(failure.get(), () -> "Flexible 2D GPU check failed: " + failure.get());
    }

    private static void certifyAndCapture() throws Exception {
        var shader = new ShaderProgram(CoronaPokerGdxTable.CARD_VERTEX_SHADER,
                CoronaPokerGdxTable.CARD_FRAGMENT_SHADER);
        assertTrue(shader.isCompiled(), shader::getLog);
        var batch = new SpriteBatch(2000);
        var face = texture("A_P.jpg");
        var back = texture("trasera.jpg");
        var turn = new GdxCommunityTurn2D();
        var font = new BitmapFont();
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
        try {
            for (int direction : new int[]{-1, 1}) {
                for (float progress : new float[]{0f, 1f}) {
                    byte[] actual = render(batch, shader, face, back, turn, progress,
                            direction, false, font);
                    byte[] expected = renderResting(batch, shader, face, back, progress);
                    int changed = changedPixels(actual, expected, 12);
                    assertTrue(changed < 70, "Wrong footprint or mirrored artwork at "
                            + progress + ", direction " + direction + ": " + changed + " pixels");
                }
            }
            byte[] almost = render(batch, shader, face, back, turn, .9999f, 1, false, font);
            byte[] settled = render(batch, shader, face, back, turn, 1f, 1, false, font);
            assertTrue(changedPixels(almost, settled, 12) < 70, "Visible pop at final handoff");
            byte[] bent = render(batch, shader, face, back, turn, .38f, 1, false, font);
            byte[] resting = renderResting(batch, shader, face, back, 0f);
            assertTrue(changedPixels(bent, resting, 12) > 5000, "The flexible renderer is a no-op");
            String output = System.getProperty("coronapoker.turn2d.frames");
            if (output != null) {
                Path directory = Files.createDirectories(Path.of(output));
                for (int frame = 0; frame <= 60; frame++) {
                    byte[] pixels = render(batch, shader, face, back, turn, frame / 60f,
                            1, true, font);
                    savePng(pixels, directory.resolve(String.format("frame-%03d.png", frame)));
                }
                System.out.println("Flexible 2D GPU preview: " + directory);
            }
            System.out.println("Flexible 2D GPU: both directions, back/front footprint, "
                    + "unmirrored artwork and final handoff passed");
        } finally {
            batch.setShader(null);
            batch.dispose();
            shader.dispose();
            face.dispose();
            back.dispose();
            font.dispose();
        }
    }

    private static Texture texture(String filename) {
        var texture = new Texture(Gdx.files.internal("images/decks/goliat/hq/" + filename), true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static byte[] render(SpriteBatch batch, ShaderProgram shader, Texture face,
            Texture back, GdxCommunityTurn2D turn, float progress, int direction,
            boolean pair, BitmapFont font) {
        ScreenUtils.clear(.055f, .20f, .16f, 1f);
        batch.setShader(shader);
        batch.begin();
        drawTurn(batch, shader, face, back, turn, pair ? 260f : WIDTH / 2f,
                progress, direction);
        if (pair) drawTurn(batch, shader, face, back, turn, 700f, progress, -direction);
        batch.end();
        if (pair) {
            batch.setShader(null);
            batch.begin();
            font.setColor(new Color(.82f, .90f, .86f, 1f));
            font.getData().setScale(1.5f);
            font.draw(batch, "CARTAS DE PLASTICO - DESTAPE 2D", 235f, 490f);
            font.getData().setScale(1f);
            font.draw(batch, "DOS SENTIDOS DE DESTAPE", 375f, 58f);
            batch.end();
        }
        Gdx.gl.glFinish();
        return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
    }

    private static void drawTurn(SpriteBatch batch, ShaderProgram shader, Texture face,
            Texture back, GdxCommunityTurn2D turn, float x, float progress, int direction) {
        turn.prepare(x, 260f, CARD_WIDTH, CARD_HEIGHT, 0f, 1f, progress, direction,
                GdxPlasticCard.STANDARD, CANVAS);
        configure(batch, shader, face, back, 0f);
        turn.drawShadow(batch, back);
        turn.drawBack(batch, back);
        configure(batch, shader, face, back, MathUtils.PI);
        turn.drawFace(batch, back);
    }

    private static byte[] renderResting(SpriteBatch batch, ShaderProgram shader,
            Texture face, Texture back, float progress) {
        ScreenUtils.clear(.055f, .20f, .16f, 1f);
        batch.setShader(shader);
        batch.begin();
        configure(batch, shader, face, back, progress * MathUtils.PI);
        batch.setColor(Color.WHITE);
        batch.draw(back, WIDTH / 2f - CARD_WIDTH * CANVAS / 2f,
                260f - CARD_HEIGHT * CANVAS / 2f, CARD_WIDTH * CANVAS, CARD_HEIGHT * CANVAS);
        batch.end();
        Gdx.gl.glFinish();
        return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
    }

    private static void configure(SpriteBatch batch, ShaderProgram shader,
            Texture face, Texture back, float angle) {
        batch.flush();
        face.bind(1);
        back.bind(0);
        shader.setUniformi("u_texture", 0);
        shader.setUniformi("u_frontTexture", 1);
        shader.setUniformf("u_cornerRadius", CoronaPokerGdxTable.cardCornerRadius());
        shader.setUniformf("u_edgeSoftness", CoronaPokerGdxTable.cardEdgeSoftness());
        shader.setUniformf("u_perspective", 0f);
        shader.setUniformf("u_flipAngle", angle);
        shader.setUniformf("u_cardAspect", CARD_HEIGHT / CARD_WIDTH);
        shader.setUniformf("u_canvasScale", CANVAS);
        shader.setUniformf("u_artworkSmooth", 0f);
        shader.setUniformf("u_extraArtworkSampling", 1f);
        shader.setUniformf("u_projectedArtwork", 0f);
        shader.setUniformf("u_artworkLodBias", 0f);
        shader.setUniformf("u_overlay", 1f, 1f, 1f, 0f);
    }

    private static int changedPixels(byte[] a, byte[] b, int tolerance) {
        int changed = 0;
        for (int pixel = 0; pixel < a.length; pixel += 4) {
            for (int channel = 0; channel < 3; channel++) {
                if (Math.abs((a[pixel + channel] & 255) - (b[pixel + channel] & 255)) > tolerance) {
                    changed++;
                    break;
                }
            }
        }
        return changed;
    }

    private static void savePng(byte[] pixels, Path file) throws Exception {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int offset = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
                image.setRGB(x, y, ((pixels[offset] & 255) << 16)
                        | ((pixels[offset + 1] & 255) << 8) | (pixels[offset + 2] & 255));
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
