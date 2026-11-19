/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

final class GdxImagePreview implements Disposable {
    private static final Logger LOGGER = Logger.getLogger(GdxImagePreview.class.getName());
    private final Matrix4 pixelProjection = new Matrix4();
    private final Vector2 position = new Vector2();
    private StreamingGifTextureAnimation animation;
    private long startedAt;

    void playDeckAnimation() {
        dispose();
        var splash = Gdx.files.internal("images/splash.gif");
        var encrypted = Gdx.files.internal("images/e");
        animation = StreamingGifTextureAnimation.loadDeferred(() -> {
            Cipher cipher;
            try (var keySource = splash.read()) {
                cipher = GdxAboutEasterEgg.decoder(keySource);
            } catch (GeneralSecurityException failure) {
                throw new IOException("Unable to decode the deck preview", failure);
            }
            return StreamingGifTextureAnimation.progressiveInput(
                    new CipherInputStream(encrypted.read(), cipher));
        }, "deck preview", Integer.MAX_VALUE);
    }

    boolean isOpen() {
        return animation != null;
    }

    Texture frame() {
        if (animation == null) return null;
        long now = System.nanoTime();
        float elapsed = startedAt == 0L ? 0f : (now - startedAt) / 1_000_000_000f;
        Texture texture = animation.frameAt(elapsed);
        if (animation.playbackComplete(elapsed)) {
            if (animation.failed()) {
                LOGGER.log(Level.WARNING, "Unable to open the deck preview",
                        animation.failure());
            }
            dispose();
            return null;
        }
        if (texture != null && startedAt == 0L) startedAt = now;
        return texture;
    }

    /** Draws to the already open batch and returns the viewer bottom in world units. */
    float draw(Texture texture, SpriteBatch batch, Viewport viewport) {
        int pixelWidth = Gdx.graphics.getBackBufferWidth();
        int pixelHeight = Gdx.graphics.getBackBufferHeight();
        var image = CoronaPokerGdxTable.cardViewerBounds(
                texture.getWidth(), texture.getHeight(), pixelWidth, pixelHeight);
        if (image.width <= 0f || image.height <= 0f) return 0f;
        // The modal composition restores the fitted viewport. Use the full
        // backbuffer when drawing in physical pixels, then restore it for text.
        Gdx.gl.glViewport(0, 0, pixelWidth, pixelHeight);
        batch.setProjectionMatrix(pixelProjection.setToOrtho2D(
                0f, 0f, pixelWidth, pixelHeight));
        batch.setColor(Color.WHITE);
        batch.draw(texture, image.x, image.y, image.width, image.height);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        viewport.apply();
        return viewport.unproject(position.set(0f,
                Gdx.graphics.getHeight() * (1f - image.y / pixelHeight))).y;
    }

    @Override
    public void dispose() {
        if (animation != null) {
            animation.dispose();
            animation = null;
        }
        startedAt = 0L;
    }
}
