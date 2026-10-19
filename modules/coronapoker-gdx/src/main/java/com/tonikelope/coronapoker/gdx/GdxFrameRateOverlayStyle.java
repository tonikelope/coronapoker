package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;

/** One visual and typographic contract for the global FPS/VSync overlay. */
final class GdxFrameRateOverlayStyle {

    static final float WIDTH = 206f;
    static final float HEIGHT = 38f;
    static final float RIGHT_MARGIN = 12f;
    static final float TOP_MARGIN = 10f;
    static final int FONT_SIZE = 18;
    private static final float HORIZONTAL_PADDING = 8f;
    private static final float CORNER_RADIUS = 11f;
    private static final int CORNER_SEGMENTS = 10;
    private static final Color PANEL = new Color(GdxSettingsStyle.PANEL_RGBA);

    static float x(float surfaceWidth) {
        return surfaceWidth - WIDTH - RIGHT_MARGIN;
    }

    static float y(float surfaceHeight) {
        return surfaceHeight - HEIGHT - TOP_MARGIN;
    }

    static BitmapFont font(GdxAdaptiveFontQuality.Profile profile) {
        FreeTypeFontGenerator generator = GdxMonospaceFonts.generator(false);
        try {
            return GdxAdaptiveFontQuality.generate(generator, FONT_SIZE, 0f,
                    Color.WHITE, new Color(0x02050ccc), null, profile);
        } finally {
            generator.dispose();
        }
    }

    static void draw(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font,
            GlyphLayout glyph, String label, float surfaceWidth,
            float surfaceHeight, Viewport viewport) {
        float x = x(surfaceWidth);
        float y = y(surfaceHeight);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL.r, PANEL.g, PANEL.b, 0.88f);
        roundedRect(shapes, x, y, WIDTH, HEIGHT, CORNER_RADIUS);
        shapes.end();

        batch.begin();
        drawFittedCenteredInBox(batch, font, glyph, label,
                x + HORIZONTAL_PADDING, y, WIDTH - 2f * HORIZONTAL_PADDING,
                HEIGHT, viewport);
        batch.end();
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float safeRadius = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(x + safeRadius, y, width - 2f * safeRadius, height);
        shapes.rect(x, y + safeRadius, safeRadius,
                height - 2f * safeRadius);
        shapes.rect(x + width - safeRadius, y + safeRadius, safeRadius,
                height - 2f * safeRadius);
        quarterCircle(shapes, x + safeRadius, y + safeRadius, safeRadius,
                180f);
        quarterCircle(shapes, x + width - safeRadius, y + safeRadius,
                safeRadius, 270f);
        quarterCircle(shapes, x + width - safeRadius,
                y + height - safeRadius, safeRadius, 0f);
        quarterCircle(shapes, x + safeRadius, y + height - safeRadius,
                safeRadius, 90f);
    }

    private static void quarterCircle(ShapeRenderer shapes, float centerX,
            float centerY, float radius, float startDegrees) {
        float previousRadians = (float) Math.toRadians(startDegrees);
        float previousX = centerX + (float) Math.cos(previousRadians) * radius;
        float previousY = centerY + (float) Math.sin(previousRadians) * radius;
        for (int index = 1; index <= CORNER_SEGMENTS; index++) {
            float radians = (float) Math.toRadians(startDegrees
                    + 90f * index / CORNER_SEGMENTS);
            float nextX = centerX + (float) Math.cos(radians) * radius;
            float nextY = centerY + (float) Math.sin(radians) * radius;
            shapes.triangle(centerX, centerY, previousX, previousY,
                    nextX, nextY);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private static void drawFittedCenteredInBox(SpriteBatch batch,
            BitmapFont font, GlyphLayout glyph, String text, float x, float y,
            float width, float height, Viewport viewport) {
        font.setColor(Color.WHITE);
        glyph.setText(font, text);
        GdxPixelText.draw(font, batch, glyph,
                x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f, viewport);
        font.setColor(Color.WHITE);
    }

    private GdxFrameRateOverlayStyle() {
    }
}
