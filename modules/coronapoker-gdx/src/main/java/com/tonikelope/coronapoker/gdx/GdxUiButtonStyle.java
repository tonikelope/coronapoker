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
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        shapes.setColor(0f, 0f, 0f, 0.40f * alpha);
        roundedRect(shapes, x + 5f, y - 6f, width, height, 14f);
        if (enabled && hover > 0.01f) {
            Color glow = accent(tone, true);
            shapes.setColor(glow.r, glow.g, glow.b,
                    (tone == Tone.FEATURED ? 0.18f : 0.14f) * hover * alpha);
            roundedRect(shapes, x - 4f * hover, y - 4f * hover,
                    width + 8f * hover, height + 8f * hover, 18f);
        }

        Color border = enabled ? accent(tone, hover > 0.5f) : LINE;
        Color fill = fill(tone, enabled, pressed);
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 14f);
        shapes.setColor(border.r, border.g, border.b,
                (enabled ? 1f : 0.55f) * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f, width - 2f,
                height - 2f, 13f, 2f);

        float inset = 14f;
        float sheenBottom = y + height * 0.54f;
        float sheenTop = y + height - 9f;
        shapes.rect(x + inset, sheenBottom, width - inset * 2f,
                sheenTop - sheenBottom,
                new Color(1f, 1f, 1f, 0.018f * alpha),
                new Color(1f, 1f, 1f, 0.018f * alpha),
                new Color(1f, 1f, 1f, 0.115f * alpha),
                new Color(1f, 1f, 1f, 0.115f * alpha));
        shapes.rect(x + inset, y + 8f, width - inset * 2f,
                height * 0.18f,
                new Color(0f, 0f, 0f, 0.11f * alpha),
                new Color(0f, 0f, 0f, 0.11f * alpha),
                new Color(0f, 0f, 0f, 0.01f * alpha),
                new Color(0f, 0f, 0f, 0.01f * alpha));
        shapes.setColor(1f, 1f, 1f, 0.055f * alpha);
        shapes.rect(x + 16f, y + height - 7f, width - 32f, 1.5f);

        if (enabled && tone != Tone.FEATURED) {
            Color inner = accent(tone, false);
            shapes.setColor(inner.r, inner.g, inner.b,
                    (0.10f + hover * 0.16f) * alpha);
            roundedRectOutline(shapes, x + 5f, y + 5f,
                    width - 10f, height - 10f, 10f, 1f);
            shapes.setColor(inner.r, inner.g, inner.b,
                    (0.18f + hover * 0.30f) * alpha);
            shapes.rect(x + 20f, y + height - 7f,
                    (width - 40f) * (0.68f + hover * 0.32f), 2f);
        }
        if (tone == Tone.FEATURED) {
            shapes.setColor(GOLD.r, GOLD.g, GOLD.b, 0.80f * alpha);
            shapes.rect(x + 18f, y + height - 9f, width - 36f, 2f);
            shapes.setColor(CYAN_DARK.r, CYAN_DARK.g, CYAN_DARK.b,
                    0.80f * alpha);
            shapes.rect(x + 18f, y + 6f, width - 36f, 3f);
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
