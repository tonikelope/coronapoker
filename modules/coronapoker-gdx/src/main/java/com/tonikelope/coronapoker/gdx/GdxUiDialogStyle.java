package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

/** Shared glass material for every renderer-owned GDX dialog. */
final class GdxUiDialogStyle {

    // Match the 80% glass used by the main menu and statistics surfaces.
    // Dialogs get their hierarchy from the dimmed backdrop and outline, not
    // from turning the panel into an almost opaque second visual language.
    static final int PANEL_RGBA = 0x071321cc;
    static final int INSET_RGBA = 0x08182870;
    static final float BACKDROP_ALPHA = 0.68f;
    static final String CLOSE_GLYPH = "\u00d7";
    private static final Color PANEL = new Color(PANEL_RGBA);
    private static final Color INSET = new Color(INSET_RGBA);
    private static final Color LINE = new Color(0x31445fff);

    private GdxUiDialogStyle() {
    }

    /**
     * Preserves authored line breaks and gives a labelled explanation a
     * stable semantic break. This avoids the renderer placing the first word
     * of a conclusion at the end of the preceding line merely because one
     * translated glyph happened to be narrower on a particular display.
     */
    static String formatNoticeMessage(String message) {
        if (message == null) return "";
        String value = message.replace("\r\n", "\n")
                .replace('\r', '\n').strip();
        if (value.indexOf('\n') >= 0) return value;
        int colon = value.indexOf(':');
        if (colon > 0 && colon + 1 < value.length()) {
            String detail = value.substring(colon + 1).stripLeading();
            if (!detail.isEmpty()) {
                return value.substring(0, colon + 1).stripTrailing()
                        + "\n" + detail;
            }
        }
        return value;
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
                new Rectangle(panelX + 50f, panelY + 132f,
                        panelWidth - 100f, 78f),
                new Rectangle(panelX + 75f, panelY + 55f, 300f, 75f),
                new Rectangle(panelX + panelWidth - 375f,
                        panelY + 55f, 300f, 75f));
    }

    record ConfirmationLayout(Rectangle panel, Rectangle title,
            Rectangle message, Rectangle negativeButton,
            Rectangle positiveButton) {
    }

    /**
     * Keeps image-gallery header actions large enough for translated labels
     * and leaves a real gap before the close control.  The same geometry is
     * consumed by painting and hit testing, so the visible button can never
     * drift away from its pointer target.
     */
    static GalleryHeaderLayout galleryHeaderLayout(float x, float y,
            float width, float height, boolean includeSearch) {
        float top = y + height - (includeSearch ? 80f : 158f);
        float controlHeight = 52f;
        float gap = 12f;
        Rectangle close = new Rectangle(x + width - 80f, top,
                52f, controlHeight);
        Rectangle clear = new Rectangle(close.x - gap - 142f, top,
                142f, controlHeight);
        Rectangle autoReceive = new Rectangle(clear.x - gap - 220f, top,
                220f, controlHeight);
        Rectangle search = includeSearch
                ? new Rectangle(autoReceive.x - gap - 220f, top,
                        220f, controlHeight)
                : new Rectangle(autoReceive.x, top, 0f, controlHeight);
        float actionsLeft = includeSearch ? search.x : x + width - 34f;
        Rectangle title = new Rectangle(x + 34f,
                includeSearch ? top + 7f : y + height - 73f,
                Math.max(0f, actionsLeft - x - 54f), 38f);
        Rectangle help = new Rectangle(x + 35f,
                includeSearch ? top - 29f : y + height - 108f,
                Math.max(0f, actionsLeft - x - 55f), 25f);
        return new GalleryHeaderLayout(title, help, search, autoReceive,
                clear, close);
    }

    record GalleryHeaderLayout(Rectangle title, Rectangle help,
            Rectangle searchButton, Rectangle autoReceiveButton,
            Rectangle clearButton, Rectangle closeButton) {
    }

    /**
     * Paints the one canonical modal shade.
     *
     * <p>The shade deliberately has no transition alpha. A modal owns the
     * frame from the instant it becomes visible; only its foreground chrome
     * may animate. Keeping this here also prevents screens from inventing
     * subtly different darkness values.</p>
     */
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
