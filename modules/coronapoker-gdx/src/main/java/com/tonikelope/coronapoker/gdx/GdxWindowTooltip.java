/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Hover help painted above the chrome and Chromium texture of a floating window. */
final class GdxWindowTooltip {
    private static final Color BORDER = new Color(0x31445fff);
    private final GdxTooltipDelay delay = new GdxTooltipDelay();
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();

    void draw(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font,
            Viewport viewport, float x, float y, String text) {
        if (text == null || text.isBlank()) {
            delay.clear();
            return;
        }
        if (!delay.ready(text)) return;
        glyph.setText(font, text, Color.WHITE, 400f, Align.left, true);
        Rectangle box = GdxSettingsTooltips.bounds(pointer.set(x, y),
                glyph.width + 28f, Math.max(36f, glyph.height + 20f),
                viewport.getWorldWidth(), viewport.getWorldHeight(), null);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        GdxUiDialogStyle.drawPanel(shapes, box.x, box.y, box.width, box.height,
                GdxUiDialogStyle.TOOLTIP_FILL, BORDER, 1f);
        shapes.end();
        batch.begin();
        GdxPixelText.draw(font, batch, glyph, box.x + 14f,
                box.y + (box.height + glyph.height) / 2f, viewport);
        batch.end();
    }

    void clear() {
        delay.clear();
    }
}
