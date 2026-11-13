package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Shared rendering for global volume feedback. */
final class GdxVolumeOverlayStyle {

    static final float WIDTH = 520f;
    static final float HEIGHT = 100f;
    static final float BAR_X_OFFSET = 104f;
    static final float BAR_Y_OFFSET = 37f;
    static final float BAR_RIGHT_INSET = 132f;
    static final float BAR_HEIGHT = 26f;
    static final int FONT_SIZE = 31;
    static final float FONT_BORDER = 1.2f;

    static final float DURATION_SECONDS = 1f;
    private static final Color ACCENT = new Color(GdxSettingsStyle.CYAN_RGBA);
    private static final Color MUTED_ACCENT = new Color(0xf44336ff);
    private static final Color AUDIO_UNAVAILABLE = new Color(0xef3340ff);
    private static final Color TRACK = new Color(0x263347ff);

    private GdxVolumeOverlayStyle() {
    }

    static int shortcutDirection(String action) {
        if (GdxShortcutBindings.VOLUME_UP.equals(action)) return 1;
        if (GdxShortcutBindings.VOLUME_DOWN.equals(action)) return -1;
        return 0;
    }

    static void draw(ShapeRenderer shapes, SpriteBatch batch, Viewport viewport,
            BitmapFont font, GlyphLayout glyph, Texture soundIcon,
            Texture muteIcon, float volume, boolean outputAvailable) {
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float x = (viewport.getWorldWidth() - WIDTH) / 2f;
        float y = (viewport.getWorldHeight() - HEIGHT) / 2f;
        float barX = x + BAR_X_OFFSET;
        float barY = y + BAR_Y_OFFSET;
        float barWidth = WIDTH - BAR_RIGHT_INSET;
        boolean audible = outputAvailable && volume > 0f;
        Color accent = audible ? ACCENT : MUTED_ACCENT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(accent);
        GdxAntialiasedRoundedRect.fill(shapes, x - 2f, y - 2f,
                WIDTH + 4f, HEIGHT + 4f, 15f);
        shapes.setColor(GdxUiDialogStyle.NOTICE_FILL);
        GdxAntialiasedRoundedRect.fill(shapes, x, y, WIDTH, HEIGHT, 13f);
        shapes.setColor(TRACK);
        GdxAntialiasedRoundedRect.fill(shapes, barX, barY, barWidth, BAR_HEIGHT, 7f);
        if (volume > 0f) {
            shapes.setColor(accent);
            GdxAntialiasedRoundedRect.fill(shapes, barX, barY,
                    barWidth * volume, BAR_HEIGHT, 7f);
        }
        shapes.end();

        batch.begin();
        batch.setColor(outputAvailable ? Color.WHITE : AUDIO_UNAVAILABLE);
        batch.draw(audible ? soundIcon : muteIcon, x + 22f, y + 21f, 58f, 58f);
        batch.setColor(Color.WHITE);
        glyph.setText(font, Math.round(volume * 100f) + "%", Color.WHITE,
                0f, Align.left, false);
        GdxPixelText.draw(font, batch, glyph, barX + (barWidth - glyph.width) / 2f,
                barY + (BAR_HEIGHT + glyph.height) / 2f, viewport);
        batch.end();
    }
}
