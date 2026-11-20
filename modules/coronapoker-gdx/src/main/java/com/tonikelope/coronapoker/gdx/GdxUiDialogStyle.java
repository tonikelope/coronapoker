package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun;
import com.badlogic.gdx.graphics.g2d.GlyphLayout.WrapState;
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

    static float singleLineWidth(GlyphLayout layout, BitmapFont font,
            String message) {
        String text = formatNoticeMessage(message);
        if (text.indexOf('\n') >= 0) return 0f;
        layout.setText(font, text);
        return layout.width;
    }

    static void layoutTooltip(GlyphLayout layout, BitmapFont font,
            String text, Color color, float width, float height) {
        layoutMessage(layout, font, formatNoticeMessage(text), color, width,
                height, Align.left);
    }

    static void layoutTooltip(GlyphLayout layout, BitmapFont font, String text,
            Color color, float preferredWidth, float availableWidth, float height) {
        float width = Math.min(preferredWidth, availableWidth);
        float maximumWidth = Math.min(availableWidth, width * 1.16f);
        layoutMessage(layout, font, formatNoticeMessage(text), color, width,
                maximumWidth, height, Align.left, true);
    }

    static BitmapFont fitMessage(GlyphLayout layout, String message,
            Color color, float width, float height, int shortTextAlignment,
            GdxCrispButtonText.FontTiers tiers, GdxCrispButtonText.Result fit) {
        return fitMessage(layout, message, color, width, height, shortTextAlignment,
                tiers, fit, true);
    }

    static BitmapFont fitMessage(GlyphLayout layout, String message,
            Color color, float width, float height, int shortTextAlignment,
            GdxCrispButtonText.FontTiers tiers, GdxCrispButtonText.Result fit,
            boolean justifyWrapped) {
        String text = formatNoticeMessage(message);
        GdxCrispButtonText.resolveWrapped(layout, text, color, width, height,
                shortTextAlignment, tiers, fit);
        BitmapFont font = fit.font();
        layoutMessage(layout, font, text, color, width, height, shortTextAlignment,
                justifyWrapped);
        return font;
    }

    static void layoutMessage(GlyphLayout layout, BitmapFont font,
            String message, Color color, float width, float height,
            int shortTextAlignment) {
        layoutMessage(layout, font, message, color, width, height, shortTextAlignment, true);
    }

    static void layoutMessage(GlyphLayout layout, BitmapFont font,
            String message, Color color, float width, float height,
            int shortTextAlignment, boolean justifyWrapped) {
        layoutMessage(layout, font, message, color, width, width, height,
                shortTextAlignment, justifyWrapped);
    }

    private static void layoutMessage(GlyphLayout layout, BitmapFont font,
            String message, Color color, float width, float maximumWidth,
            float height, int shortTextAlignment, boolean justifyWrapped) {
        String text = message;
        layout.setText(font, text, color, width, shortTextAlignment, true);
        if (layout.height > height || layout.width > width) {
            text = fittingExcerpt(layout, font, text, color, width, height);
            layout.setText(font, text, color, width, shortTextAlignment, true);
        }
        if (!justifyWrapped || layout.runs.size < 2) return;
        float maxExtraSpace = font.getSpaceXadvance() * 1.5f;
        GdxParagraphReflow.Plan reflow = null;
        for (GlyphRun line : layout.runs) {
            if (GdxParagraphReflow.needsReflow(line, width, maxExtraSpace)) {
                reflow = GdxParagraphReflow.plan(font, text, width, maximumWidth, maxExtraSpace);
                break;
            }
        }
        if (reflow != null) {
            width = reflow.width();
            layout.setText(font, reflow.text(), color, width, Align.left, true);
        }
        for (int index = 0; index < layout.runs.size; index++) {
            GlyphRun line = layout.runs.get(index);
            line.x = 0f;
            if (reflow != null && reflow.raggedRuns().get(index)) continue;
            if (line.wrapState == WrapState.wrapped
                    || (reflow != null && reflow.softBreakRuns().get(index))) {
                justifyLine(line, width, maxExtraSpace);
                layout.width = Math.max(layout.width, line.width);
            }
        }
    }

    private static String fittingExcerpt(GlyphLayout layout, BitmapFont font,
            String message, Color color, float width, float height) {
        String ellipsis = "…";
        layout.setText(font, ellipsis, color, width, Align.left, true);
        if (layout.height > height || layout.width > width) return "";
        String excerpt = ellipsis;
        int lower = 1;
        int upper = message.codePointCount(0, message.length()) - 1;
        while (lower <= upper) {
            int count = lower + (upper - lower) / 2;
            int end = message.offsetByCodePoints(0, count);
            String candidate = message.substring(0, end).stripTrailing() + ellipsis;
            layout.setText(font, candidate, color, width, Align.left, true);
            if (layout.height <= height && layout.width <= width) {
                excerpt = candidate;
                lower = count + 1;
            } else {
                upper = count - 1;
            }
        }
        return excerpt;
    }

    private static void justifyLine(GlyphRun line, float width,
            float maxExtraSpace) {
        int gaps = GdxParagraphReflow.wordGaps(line);
        if (gaps == 0) return;

        float extraPerSpace = (width - line.width) / gaps;
        // Limit the extra space so the gaps between words stay readable.
        if (extraPerSpace <= 0f || extraPerSpace > maxExtraSpace) return;
        for (int i = 0; i < line.glyphs.size - 1; i++) {
            if (line.glyphs.get(i).id == ' ') {
                line.xAdvances.incr(i + 1, extraPerSpace);
            }
        }
        line.width = width;
    }

    static ConfirmationLayout confirmationLayout(float worldWidth,
            float worldHeight) {
        return confirmationLayout(worldWidth, worldHeight, 0f);
    }

    static ConfirmationLayout confirmationLayout(float worldWidth,
            float worldHeight, float singleLineWidth) {
        float messageInset = 50f;
        float panelWidth = messagePanelWidth(800f, 1000f, messageInset,
                singleLineWidth);
        float panelHeight = 330f;
        float panelX = (worldWidth - panelWidth) / 2f;
        float panelY = (worldHeight - panelHeight) / 2f;
        float centerX = worldWidth / 2f;
        Rectangle panel = new Rectangle(panelX, panelY,
                panelWidth, panelHeight);
        return new ConfirmationLayout(panel,
                new Rectangle(panelX + 40f, panelY + 232f,
                        panelWidth - 80f, 48f),
                new Rectangle(panelX + messageInset, panelY + 146f,
                        panelWidth - 2f * messageInset, 64f),
                new Rectangle(centerX - 325f, panelY + 43f, 300f, 75f),
                new Rectangle(centerX + 25f, panelY + 43f, 300f, 75f));
    }

    record ConfirmationLayout(Rectangle panel, Rectangle title,
            Rectangle message, Rectangle negativeButton,
            Rectangle positiveButton) {
    }

    static NoticeLayout noticeLayout(float worldWidth, float worldHeight) {
        return noticeLayout(worldWidth, worldHeight, 0f);
    }

    static NoticeLayout noticeLayout(float worldWidth, float worldHeight,
            float singleLineWidth) {
        float messageInset = 65f;
        float panelWidth = messagePanelWidth(900f, 1080f, messageInset,
                singleLineWidth);
        float panelHeight = 390f;
        float panelX = (worldWidth - panelWidth) / 2f;
        float panelY = (worldHeight - panelHeight) / 2f;
        Rectangle panel = new Rectangle(panelX, panelY,
                panelWidth, panelHeight);
        return new NoticeLayout(panel,
                new Rectangle(panelX + 50f, panelY + 292f,
                        panelWidth - 100f, 48f),
                new Rectangle(panelX + messageInset, panelY + 128f,
                        panelWidth - 2f * messageInset, 142f),
                new Rectangle(panelX + panelWidth / 2f - 190f,
                        panelY + 40f, 380f, 72f));
    }

    record NoticeLayout(Rectangle panel, Rectangle title,
            Rectangle message, Rectangle button) {
    }

    static float messagePanelWidth(float minimum, float maximum,
            float messageInset, float textWidth) {
        // Leave a little room for rounding at the wrap boundary.
        float needed = (float) Math.ceil(textWidth) + 2f * messageInset + 2f;
        return Math.max(minimum, Math.min(maximum, needed));
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
