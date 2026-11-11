/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Lazily loaded emoji pages shared by the lobby and table on the render thread. */
final class GdxEmojiAtlas {

    private static final String ASSETS = "images/emoji_atlas/";
    private static final String PICKER_FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform sampler2D u_texture;
            uniform vec2 u_atlasSize;
            uniform float u_texelFootprint;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                vec2 texel = v_texCoords * u_atlasSize - vec2(0.5);
                vec2 blend = clamp((fract(texel) - vec2(0.5))
                        / u_texelFootprint + vec2(0.5), 0.0, 1.0);
                vec2 uv = (floor(texel) + blend + vec2(0.5)) / u_atlasSize;
                gl_FragColor = texture2D(u_texture, uv) * v_color;
            }
            """;
    private static final Layout LAYOUT = loadLayout();
    static final int COUNT = LAYOUT.count();
    private static GdxEmojiAtlas shared;

    private final Texture[] pages = new Texture[
            (COUNT + LAYOUT.pageSize() - 1) / LAYOUT.pageSize()];
    private final TextureRegion[] regions = new TextureRegion[COUNT];
    private ShaderProgram pickerShader;
    private int owners;

    private GdxEmojiAtlas() {
    }

    static GdxEmojiAtlas retain() {
        if (shared == null) shared = new GdxEmojiAtlas();
        shared.owners++;
        return shared;
    }

    void release() {
        if (--owners != 0) return;
        if (pickerShader != null) pickerShader.dispose();
        for (Texture page : pages) {
            if (page != null) page.dispose();
        }
        shared = null;
    }

    TextureRegion emoji(int id) {
        if (id < 1 || id > COUNT) return null;
        int index = id - 1;
        TextureRegion region = regions[index];
        if (region != null) return region;
        int page = index / LAYOUT.pageSize();
        int slot = index % LAYOUT.pageSize();
        int x = slot % LAYOUT.columns() * LAYOUT.stride() + LAYOUT.gutter();
        int y = slot / LAYOUT.columns() * LAYOUT.stride() + LAYOUT.gutter();
        region = new TextureRegion(pageTexture(page), x, y,
                LAYOUT.size(), LAYOUT.size());
        regions[index] = region;
        return region;
    }

    void preload(int first, int last) {
        int firstPage = (first - 1) / LAYOUT.pageSize();
        int lastPage = (last - 1) / LAYOUT.pageSize();
        for (int page = firstPage; page <= lastPage; page++) pageTexture(page);
    }

    private Texture pageTexture(int page) {
        if (pages[page] == null) {
            Texture texture = new Texture(Gdx.files.internal(
                    ASSETS + page + ".png"));
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            pages[page] = texture;
        }
        return pages[page];
    }

    /** Limits interpolation across a texel boundary to one output pixel. */
    void beginPickerSampling(SpriteBatch batch, String vertexShader,
            float imageSize, Viewport viewport) {
        if (pickerShader == null) {
            pickerShader = new ShaderProgram(vertexShader, PICKER_FRAGMENT_SHADER);
            if (!pickerShader.isCompiled()) {
                throw new IllegalStateException(
                        "Emoji picker shader: " + pickerShader.getLog());
            }
        }
        batch.setShader(pickerShader);
        pickerShader.setUniformf("u_atlasSize",
                LAYOUT.columns() * LAYOUT.stride(),
                LAYOUT.rows() * LAYOUT.stride());
        float pixels = Math.max(1f, Math.round(imageSize * physicalScale(viewport)));
        pickerShader.setUniformf("u_texelFootprint",
                Math.min(1f, LAYOUT.size() / pixels));
    }

    /** Centers the original artwork on physical pixels and avoids magnifying it. */
    static void drawNative(SpriteBatch batch, TextureRegion region,
            float x, float y, float width, float height, Viewport viewport) {
        float scale = physicalScale(viewport);
        float fit = Math.min(1f, Math.min(
                region.getRegionWidth() / (width * scale),
                region.getRegionHeight() / (height * scale)));
        float drawWidth = width * fit;
        float drawHeight = height * fit;
        drawAligned(batch, region, x + (width - drawWidth) / 2f,
                y + (height - drawHeight) / 2f, drawWidth, drawHeight, viewport);
    }

    static void drawAligned(SpriteBatch batch, TextureRegion region,
            float x, float y, float width, float height, Viewport viewport) {
        float alignedX = GdxPixelText.snapX(x, viewport);
        float alignedY = GdxPixelText.snapY(y, viewport);
        batch.draw(region, alignedX, alignedY,
                GdxPixelText.snapX(x + width, viewport) - alignedX,
                GdxPixelText.snapY(y + height, viewport) - alignedY);
    }

    private static float physicalScale(Viewport viewport) {
        return Math.min(
                Gdx.graphics.getBackBufferWidth() / viewport.getWorldWidth(),
                Gdx.graphics.getBackBufferHeight() / viewport.getWorldHeight());
    }

    private static Layout loadLayout() {
        Properties properties = new Properties();
        try (var input = GdxEmojiAtlas.class.getResourceAsStream(
                "/" + ASSETS + "layout.properties")) {
            if (input == null) throw new IOException("Missing chat emoji atlas");
            properties.load(input);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return new Layout(Integer.parseInt(properties.getProperty("count")),
                Integer.parseInt(properties.getProperty("columns")),
                Integer.parseInt(properties.getProperty("rows")),
                Integer.parseInt(properties.getProperty("size")),
                Integer.parseInt(properties.getProperty("gutter")));
    }

    private record Layout(int count, int columns, int rows, int size, int gutter) {
        int pageSize() {
            return columns * rows;
        }

        int stride() {
            return size + 2 * gutter;
        }
    }
}
