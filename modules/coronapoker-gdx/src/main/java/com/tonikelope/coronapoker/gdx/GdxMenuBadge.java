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
        this(asset, false);
    }

    GdxMenuBadge(String asset, boolean trimWhiteExterior) {
        Pixmap loaded = new Pixmap(Gdx.files.internal(asset));
        if (!trimWhiteExterior || loaded.getPixel(0, 0) != 0xffffffff) {
            source = loaded;
            return;
        }
        try {
            source = trimWhiteExterior(loaded);
        } finally {
            loaded.dispose();
        }
    }

    private static Pixmap trimWhiteExterior(Pixmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] left = new int[height];
        int[] right = new int[height];
        int minX = width;
        int maxX = 0;
        int minY = height;
        int maxY = 0;
        // This solid badge has a closed rim. Retain the entire span of each
        // row so the white pixels inside the artwork stay opaque.
        for (int y = 0; y < height; y++) {
            int start = 0;
            while (start < width && image.getPixel(start, y) == 0xffffffff) start++;
            if (start == width) continue;
            int end = width;
            while (end > start && image.getPixel(end - 1, y) == 0xffffffff) end--;
            left[y] = start;
            right[y] = end;
            minX = Math.min(minX, start);
            maxX = Math.max(maxX, end);
            minY = Math.min(minY, y);
            maxY = y + 1;
        }
        Pixmap cutout = new Pixmap(Math.max(1, maxX - minX),
                Math.max(1, maxY - minY), Pixmap.Format.RGBA8888);
        cutout.setBlending(Pixmap.Blending.None);
        cutout.setColor(0);
        cutout.fill();
        for (int y = minY; y < maxY; y++) {
            int span = right[y] - left[y];
            if (span > 0) cutout.drawPixmap(image, left[y] - minX,
                    y - minY, left[y], y, span, 1);
        }
        return cutout;
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
