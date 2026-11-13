package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Shared glass material for every renderer-owned GDX dialog. */
final class GdxUiDialogStyle {

    // Match the 80% glass used by the main menu and statistics surfaces.
    // Dialogs get their hierarchy from the dimmed backdrop and outline, not
    // from turning the panel into an almost opaque second visual language.
    static final int PANEL_RGBA = 0x071321cc;
    static final int INSET_RGBA = 0x08182870;
    static final float BACKDROP_ALPHA = 0.68f;
    static final String CLOSE_GLYPH = "\u00d7";

    static void drawWindowClose(ShapeRenderer shapes,
            float x, float y, float width, float height, boolean enabled,
            float hover, boolean pressed, float alpha) {
        GdxUiButtonStyle.Tone tone = GdxUiButtonStyle.Tone.DANGER;
        GdxUiButtonStyle.draw(shapes, x, y, width, height, tone, enabled,
                hover, pressed, alpha);
        GdxUiIconRenderer.draw(shapes, GdxUiIconRenderer.Icon.CLOSE,
                x + width / 2f, y + height / 2f,
                Math.min(width, height) * 0.46f,
                GdxUiButtonStyle.labelColor(tone, enabled), alpha);
    }

    static void drawWindowTitle(SpriteBatch batch, BitmapFont font, GlyphLayout glyph,
            String title, Rectangle bounds, Viewport viewport, float alpha) {
        Color color = alpha == 1f ? Color.WHITE : new Color(1f, 1f, 1f, alpha);
        glyph.setText(font, title, 0, title.length(), color, bounds.width, Align.left, false, "…");
        GdxPixelText.draw(font, batch, glyph, bounds.x,
                bounds.y + (bounds.height + glyph.height) / 2f, viewport);
    }
    static final Color NOTICE_FILL = new Color(PANEL_RGBA | 0xff);
    static final Color TOOLTIP_FILL = new Color(0x071221ff);
    private static final Color PANEL = new Color(PANEL_RGBA);
    private static final Color INSET = new Color(INSET_RGBA);
    private static final Color LINE = new Color(0x31445fff);
    static final Color PREPARATION_TITLE = new Color(GdxSettingsStyle.GOLD_RGBA);
    static final Color PREPARATION_STATUS = new Color(GdxSettingsStyle.MUTED_RGBA);
    private static final Color PREPARATION_ACCENT = new Color(0x39d6ffff);
    private static final Color PREPARATION_TRACK = new Color(0x020813ff);

    private GdxUiDialogStyle() {
    }

    /** Preserves only line breaks deliberately authored by the translation. */
    static String formatNoticeMessage(String message) {
        if (message == null) return "";
        return message.replace("\r\n", "\n")
                .replace('\r', '\n').strip();
    }

    static ConfirmationLayout confirmationLayout(float worldWidth,
            float worldHeight) {
        float panelWidth = 800f;
        float panelHeight = 330f;
        float panelX = (worldWidth - panelWidth) / 2f;
        float panelY = (worldHeight - panelHeight) / 2f;
        Rectangle panel = new Rectangle(panelX, panelY,
                panelWidth, panelHeight);
        return new ConfirmationLayout(panel,
                new Rectangle(panelX + 40f, panelY + 232f,
                        panelWidth - 80f, 48f),
                new Rectangle(panelX + 50f, panelY + 146f,
                        panelWidth - 100f, 64f),
                new Rectangle(panelX + 75f, panelY + 43f, 300f, 75f),
                new Rectangle(panelX + panelWidth - 375f,
                        panelY + 43f, 300f, 75f));
    }

    record ConfirmationLayout(Rectangle panel, Rectangle title,
            Rectangle message, Rectangle negativeButton,
            Rectangle positiveButton) {
    }

    static NoticeLayout noticeLayout(float worldWidth, float worldHeight) {
        float panelWidth = 900f;
        float panelHeight = 390f;
        float panelX = (worldWidth - panelWidth) / 2f;
        float panelY = (worldHeight - panelHeight) / 2f;
        Rectangle panel = new Rectangle(panelX, panelY,
                panelWidth, panelHeight);
        return new NoticeLayout(panel,
                new Rectangle(panelX + 50f, panelY + 292f,
                        panelWidth - 100f, 48f),
                new Rectangle(panelX + 65f, panelY + 128f,
                        panelWidth - 130f, 142f),
                new Rectangle(panelX + panelWidth / 2f - 190f,
                        panelY + 40f, 380f, 72f));
    }

    record NoticeLayout(Rectangle panel, Rectangle title,
            Rectangle message, Rectangle button) {
    }

    static PreparationLayout preparationLayout(float worldWidth,
            float worldHeight) {
        float panelWidth = Math.min(800f, worldWidth - 80f);
        float panelHeight = 270f;
        float centerX = worldWidth / 2f;
        float bottom = (worldHeight - panelHeight) / 2f;
        float trackWidth = panelWidth * 0.75f;
        return new PreparationLayout(
                new Rectangle(centerX - panelWidth / 2f, bottom,
                        panelWidth, panelHeight),
                centerX, bottom + 174f, bottom + 122f, panelWidth - 88f,
                new Rectangle(centerX - trackWidth / 2f, bottom + 52f,
                        trackWidth, 18f));
    }

    record PreparationLayout(Rectangle panel, float centerX, float titleY,
            float statusY, float labelWidth, Rectangle track) {
    }

    static void drawPreparationDialog(ShapeRenderer shapes,
            PreparationLayout layout, float elapsed) {
        Rectangle panel = layout.panel();
        drawPanel(shapes, panel.x, panel.y, panel.width, panel.height,
                PREPARATION_ACCENT, 1f);
        Rectangle track = layout.track();
        shapes.setColor(PREPARATION_TRACK);
        roundedRect(shapes, track.x, track.y, track.width, track.height, 9f);
        shapes.setColor(LINE);
        roundedRect(shapes, track.x + 2f, track.y + 2f, track.width - 4f,
                track.height - 4f, 7f);
        float segment = Math.min(170f, track.width * 0.30f);
        float phase = (elapsed * 0.52f) % 2f;
        float travel = (track.width - segment - 8f) * (1f - Math.abs(phase - 1f));
        shapes.setColor(PREPARATION_ACCENT);
        roundedRect(shapes, track.x + 4f + travel, track.y + 4f,
                segment, track.height - 8f, 5f);
    }

    static void drawBackdrop(ShapeRenderer shapes, float width,
            float height) {
        shapes.setColor(0.008f, 0.018f, 0.038f, BACKDROP_ALPHA);
        shapes.rect(0f, 0f, width, height);
    }

    static void drawPanel(ShapeRenderer shapes, float x, float y,
            float width, float height, Color accent, float alpha) {
        drawPanel(shapes, x, y, width, height, PANEL, accent, alpha);
    }

    static void drawPanel(ShapeRenderer shapes, float x, float y,
            float width, float height, Color fill, Color accent,
            float alpha) {
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 18f);
        drawPanelOutline(shapes, x, y, width, height, accent, alpha);
    }

    static void drawNoticePanel(ShapeRenderer shapes, float x, float y,
            float width, float height, Color accent) {
        drawPanel(shapes, x, y, width, height, NOTICE_FILL, accent, 1f);
    }

    static void drawPanelOutline(ShapeRenderer shapes, float x, float y,
            float width, float height, Color accent, float alpha) {
        shapes.setColor(accent.r, accent.g, accent.b, 0.92f * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f, 17f, 2f);
    }

    static void drawInset(ShapeRenderer shapes, float x, float y,
            float width, float height, float alpha) {
        drawInset(shapes, x, y, width, height, INSET, LINE, alpha);
    }

    static void drawInset(ShapeRenderer shapes, float x, float y,
            float width, float height, Color fill, Color border,
            float alpha) {
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 14f);
        shapes.setColor(border.r, border.g, border.b,
                border.a * 0.82f * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f, 13f, 1.5f);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        GdxAntialiasedRoundedRect.fill(shapes, x, y, width, height, radius);
    }

    private static void roundedRectOutline(ShapeRenderer shapes, float x,
            float y, float width, float height, float radius,
            float thickness) {
        GdxAntialiasedRoundedRect.outline(shapes, x, y, width, height,
                radius, thickness);
    }
}
