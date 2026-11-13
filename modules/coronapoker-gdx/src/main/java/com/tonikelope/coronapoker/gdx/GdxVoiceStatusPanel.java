/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Shared non-modal recording panel for the lobby and the table. */
final class GdxVoiceStatusPanel {

    static final float HEIGHT = 54f;
    private static final Color INACTIVE = new Color(GdxSettingsStyle.LINE_RGBA);
    private static final Color GOLD = new Color(GdxSettingsStyle.GOLD_RGBA);

    private GdxVoiceStatusPanel() {
    }

    static void draw(ShapeRenderer shapes, SpriteBatch batch, Viewport viewport,
            GlyphLayout glyph, GdxCrispButtonText.FontTiers tiers,
            GdxCrispButtonText.Result fit, String status, GdxVoiceCapture capture,
            float centerX, float y, float availableWidth, boolean compact) {
        boolean recording = capture.recording();
        boolean active = capture.busy();
        float remaining = capture.secondsLeft();
        float width = Math.min(720f, availableWidth);
        float height = compact ? 44f : HEIGHT;
        float inset = compact ? 7f : 10f;
        float x = centerX - width / 2f;

        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.008f, 0.035f, 0.055f, 0.95f);
        GdxAntialiasedRoundedRect.fill(shapes, x, y, width, height, 15f);
        if (active) shapes.setColor(0.898f, 0.224f, 0.208f, 1f);
        else shapes.setColor(INACTIVE);
        GdxAntialiasedRoundedRect.fill(shapes, x + 16f, y + inset, 7f,
                height - 2f * inset, 3f);
        if (recording) {
            float timerHeight = compact ? 28f : 34f;
            shapes.setColor(0.08f, 0.16f, 0.24f, 0.96f);
            GdxAntialiasedRoundedRect.fill(shapes, x + width - 110f,
                    y + (height - timerHeight) / 2f, 88f, timerHeight, 10f);
            float barY = y + (compact ? 4f : 6f);
            float barHeight = compact ? 3f : 7f;
            float barWidth = width - 216f;
            GdxAntialiasedRoundedRect.fill(shapes, x + 84f, barY, barWidth,
                    barHeight, 2f);
            if (remaining > 0f) {
                shapes.setColor(0.17f, 0.92f, 0.62f, 0.92f);
                GdxAntialiasedRoundedRect.fill(shapes, x + 84f, barY,
                        barWidth * remaining / GdxVoiceRecorder.MAX_SECONDS,
                        barHeight, 2f);
            }
        }
        shapes.end();

        batch.begin();
        batch.setColor(Color.WHITE);
        if (recording) {
            drawText(batch, viewport, glyph, tiers, fit,
                    (int) Math.ceil(remaining) + " s", x + width - 66f,
                    y + height / 2f + 8f, 80f, Color.WHITE, true);
        }
        drawText(batch, viewport, glyph, tiers, fit, status, x + 38f,
                y + height / 2f + 10f, width - (recording ? 174f : 76f),
                active ? Color.WHITE : GOLD, false);
        batch.end();
    }

    private static void drawText(SpriteBatch batch, Viewport viewport, GlyphLayout glyph,
            GdxCrispButtonText.FontTiers tiers, GdxCrispButtonText.Result fit,
            String text, float x, float y, float maxWidth, Color color, boolean centered) {
        GdxCrispButtonText.resolveWithinContentWidth(glyph, text, maxWidth, tiers, fit);
        BitmapFont font = fit.font();
        font.setColor(color);
        glyph.setText(font, fit.text());
        GdxPixelText.draw(font, batch, glyph,
                centered ? x - glyph.width / 2f : x, y, viewport);
        font.setColor(Color.WHITE);
    }
}
