package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** One renderer-native button material shared by every GDX screen. */
final class GdxUiButtonStyle {

    enum Tone { NEUTRAL, FEATURED, POSITIVE, DANGER }

    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color CYAN_DARK = new Color(0x176b83ff);
    private static final Color GOLD = new Color(0xffe07aff);
    private static final Color LINE = new Color(0x31445fff);
    private static final Color POSITIVE = new Color(0x4caf50ff);
    private static final Color DANGER = new Color(0xf44336ff);
    private static final Color DISABLED = new Color(0x71809aff);

    private GdxUiButtonStyle() {
    }

    static void draw(ShapeRenderer shapes, float x, float y, float width,
            float height, Tone tone, boolean enabled, float hoverAmount,
            boolean pressed, float alpha) {
        draw(shapes, x, y, width, height, tone, enabled, hoverAmount,
                pressed, alpha, true);
    }

    static void draw(ShapeRenderer shapes, float x, float y, float width,
            float height, Tone tone, boolean enabled, float hoverAmount,
            boolean pressed, float alpha, boolean shadow) {
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        // The old displaced rounded shadow exposed its circular end caps
        // below dialog buttons.  On hover, the additional filled halo made
        // those caps look like detached bubbles.  Depth now comes from the
        // material itself; the hover indication is deliberately outline-only.
        if (enabled && hover > 0.01f) {
            Color glow = accent(tone, true);
            shapes.setColor(glow.r, glow.g, glow.b,
                    (tone == Tone.FEATURED ? 0.52f : 0.42f) * hover * alpha);
            roundedRectOutline(shapes, x - 2f, y - 2f,
                    width + 4f, height + 4f, 16f, 2f);
        }

        Color border = enabled ? accent(tone, hover > 0.5f) : LINE;
        Color fill = fill(tone, enabled, pressed);
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 14f);
        shapes.setColor(border.r, border.g, border.b,
                (enabled ? 1f : 0.55f) * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f, width - 2f,
                height - 2f, 13f, 2f);

        // Keep the material deliberately clean. Previous stacked gradients
        // formed bands and detached-looking patches on hover, especially on
        // short dialog buttons. Depth now comes from the fill and border.
        shapes.setColor(1f, 1f, 1f, 0.07f * alpha);
        shapes.rect(x + 16f, y + height - 7f, width - 32f, 1.5f);
        if (tone == Tone.FEATURED) {
            shapes.setColor(GOLD.r, GOLD.g, GOLD.b, 0.80f * alpha);
            shapes.rect(x + 18f, y + height - 9f, width - 36f, 2f);
        }
    }

    static Color labelColor(Tone tone, boolean enabled) {
        if (!enabled) return DISABLED;
        return tone == Tone.POSITIVE || tone == Tone.DANGER
                ? Color.WHITE : GOLD;
    }

    private static Color accent(Tone tone, boolean hover) {
        return switch (tone) {
            case POSITIVE -> hover ? new Color(0x8af59aff) : POSITIVE;
            case DANGER -> hover ? new Color(0xff8080ff) : DANGER;
            case FEATURED -> GOLD;
            case NEUTRAL -> hover ? CYAN : CYAN_DARK;
        };
    }

    private static Color fill(Tone tone, boolean enabled, boolean pressed) {
        if (!enabled) return new Color(0x0b1220b8);
        return switch (tone) {
            case POSITIVE -> pressed ? new Color(0x123a25f2)
                    : new Color(0x195335e8);
            case DANGER -> pressed ? new Color(0x47141df2)
                    : new Color(0x65202ae8);
            case FEATURED -> pressed ? new Color(0x091827f2)
                    : new Color(0x123047e8);
            case NEUTRAL -> pressed ? new Color(0x07111fd9)
                    : new Color(0x0b1729c7);
        };
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, width, height - 2f * r);
        shapes.circle(x + r, y + r, r, 24);
        shapes.circle(x + width - r, y + r, r, 24);
        shapes.circle(x + width - r, y + height - r, r, 24);
        shapes.circle(x + r, y + height - r, r, 24);
    }

    private static void roundedRectOutline(ShapeRenderer shapes, float x,
            float y, float width, float height, float radius,
            float thickness) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rectLine(x + r, y, x + width - r, y, thickness);
        shapes.rectLine(x + r, y + height, x + width - r,
                y + height, thickness);
        shapes.rectLine(x, y + r, x, y + height - r, thickness);
        shapes.rectLine(x + width, y + r, x + width,
                y + height - r, thickness);
        quarterArc(shapes, x + r, y + r, r, 180f, thickness);
        quarterArc(shapes, x + width - r, y + r, r, 270f, thickness);
        quarterArc(shapes, x + width - r, y + height - r, r, 0f, thickness);
        quarterArc(shapes, x + r, y + height - r, r, 90f, thickness);
    }

    private static void quarterArc(ShapeRenderer shapes, float cx, float cy,
            float radius, float startDegrees, float thickness) {
        float previous = (float) Math.toRadians(startDegrees);
        float previousX = cx + (float) Math.cos(previous) * radius;
        float previousY = cy + (float) Math.sin(previous) * radius;
        for (int step = 1; step <= 10; step++) {
            float radians = (float) Math.toRadians(startDegrees + step * 9f);
            float x = cx + (float) Math.cos(radians) * radius;
            float y = cy + (float) Math.sin(radians) * radius;
            shapes.rectLine(previousX, previousY, x, y, thickness);
            previousX = x;
            previousY = y;
        }
    }
}
