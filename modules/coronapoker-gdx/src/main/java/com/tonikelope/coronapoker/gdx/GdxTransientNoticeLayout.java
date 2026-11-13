package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.math.Rectangle;

/** Shared content-sized geometry for non-modal transient notifications. */
final class GdxTransientNoticeLayout {

    private GdxTransientNoticeLayout() {
    }

    static float contentWidth(float measuredTextWidth, float leadingWidth,
            float horizontalPadding, float minimumWidth, float maximumWidth,
            float availableWidth) {
        float available = finitePositiveOrOne(availableWidth);
        float maximum = Math.min(finitePositiveOrOne(maximumWidth), available);
        float minimum = Math.min(Math.max(1f,
                finiteNonNegative(minimumWidth)), maximum);
        float desired = finiteNonNegative(measuredTextWidth)
                + finiteNonNegative(leadingWidth)
                + finiteNonNegative(horizontalPadding);
        return MathUtils.clamp(desired, minimum, maximum);
    }

    static Rectangle topCentered(float worldWidth, float worldHeight,
            float desiredWidth, float height, float topInset,
            float horizontalMargin) {
        float safeWorldWidth = finitePositiveOrOne(worldWidth);
        float safeWorldHeight = finitePositiveOrOne(worldHeight);
        float margin = Math.min(finiteNonNegative(horizontalMargin),
                Math.max(0f, (safeWorldWidth - 1f) / 2f));
        float width = MathUtils.clamp(finitePositiveOrOne(desiredWidth), 1f,
                Math.max(1f, safeWorldWidth - margin * 2f));
        float safeHeight = MathUtils.clamp(finitePositiveOrOne(height), 1f,
                safeWorldHeight);
        float x = (safeWorldWidth - width) / 2f;
        float y = Math.max(0f, safeWorldHeight - finiteNonNegative(topInset)
                - safeHeight);
        return new Rectangle(x, y, width, safeHeight);
    }

    static final float DURATION_SECONDS = 2.8f;
    static final float FADE_SECONDS = 0.25f;

    static void drawCentered(ShapeRenderer shapes, SpriteBatch batch, Viewport viewport,
            BitmapFont font, GlyphLayout glyph, GdxCrispButtonText.FontTiers tiers,
            GdxCrispButtonText.Result fit, String message, Color accent, float alpha) {
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        glyph.setText(font, message);
        float worldWidth = viewport.getWorldWidth();
        float width = contentWidth(glyph.width, 0f, 52f, 260f, 820f, worldWidth - 80f);
        float height = 76f;
        float x = (worldWidth - width) / 2f;
        float y = (viewport.getWorldHeight() - height) / 2f;
        Color fill = GdxUiDialogStyle.NOTICE_FILL;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(fill.r, fill.g, fill.b, alpha);
        GdxAntialiasedRoundedRect.fill(shapes, x, y, width, height, 14f);
        shapes.setColor(accent.r, accent.g, accent.b, alpha);
        GdxAntialiasedRoundedRect.outline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f, 13f, 2f);
        shapes.end();

        GdxCrispButtonText.resolveWithinBox(glyph, message, width - 52f, height - 16f,
                tiers, fit);
        BitmapFont selected = fit.font();
        selected.setColor(1f, 1f, 1f, alpha);
        glyph.setText(selected, fit.text());
        batch.begin();
        batch.setColor(Color.WHITE);
        GdxPixelText.draw(selected, batch, glyph, x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f, viewport);
        batch.end();
        selected.setColor(Color.WHITE);
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private static float finitePositiveOrOne(float value) {
        return Float.isFinite(value) && value > 0f ? value : 1f;
    }
}
