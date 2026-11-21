/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Rasterizes a menu badge directly onto the current framebuffer pixel grid. */
final class GdxMenuBadge implements Disposable {
    private final Pixmap source;
    private Texture texture;

    GdxMenuBadge(String asset) {
        source = new Pixmap(Gdx.files.internal(asset));
    }

    float widthForHeight(float height) {
        return height * source.getWidth() / source.getHeight();
    }

    void drawAt(SpriteBatch batch, Viewport viewport, float x, float y, float height) {
        float left = GdxPixelText.snapX(x, viewport);
        float bottom = GdxPixelText.snapY(y, viewport);
        float width = GdxPixelText.snapX(x + widthForHeight(height), viewport) - left;
        float snappedHeight = GdxPixelText.snapY(y + height, viewport) - bottom;
        if (width <= 0f || snappedHeight <= 0f) return;

        float densityX = GdxPixelText.physicalPixelScale(
                Gdx.graphics.getWidth(), Gdx.graphics.getBackBufferWidth());
        float densityY = GdxPixelText.physicalPixelScale(
                Gdx.graphics.getHeight(), Gdx.graphics.getBackBufferHeight());
        int pixelsWide = Math.max(1, Math.round(width * viewport.getScreenWidth()
                / viewport.getWorldWidth() * densityX));
        int pixelsHigh = Math.max(1, Math.round(snappedHeight * viewport.getScreenHeight()
                / viewport.getWorldHeight() * densityY));
        if (texture == null || texture.getWidth() != pixelsWide || texture.getHeight() != pixelsHigh) {
            // STB filters the RGB using alpha coverage, including over clear pixels.
            Pixmap resized = GdxTextureQuality.resizePixmap(source, pixelsWide, pixelsHigh);
            try {
                Texture next = new Texture(resized);
                next.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                if (texture != null) texture.dispose();
                texture = next;
            } finally {
                resized.dispose();
            }
        }
        batch.draw(texture, left, bottom, width, snappedHeight);
    }

    @Override
    public void dispose() {
        if (texture != null) texture.dispose();
        source.dispose();
    }
}
