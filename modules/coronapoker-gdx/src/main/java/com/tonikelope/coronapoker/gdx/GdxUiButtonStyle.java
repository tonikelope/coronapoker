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
    private static final Color POSITIVE_HOVER = new Color(0x8af59aff);
    private static final Color DANGER_HOVER = new Color(0xff8080ff);
    private static final Color FEATURED_HOVER = new Color(0xfff2b0ff);
    private static final Color DISABLED_FILL = new Color(0x0b1220b8);
    private static final Color POSITIVE_FILL = new Color(0x195335e8);
    private static final Color POSITIVE_PRESSED_FILL = new Color(0x123a25f2);
    private static final Color DANGER_FILL = new Color(0x65202ae8);
    private static final Color DANGER_PRESSED_FILL = new Color(0x47141df2);
    private static final Color FEATURED_FILL = new Color(0x123047e8);
    private static final Color FEATURED_PRESSED_FILL = new Color(0x091827f2);
    private static final Color NEUTRAL_FILL = new Color(0x0b1729c7);
    private static final Color NEUTRAL_PRESSED_FILL = new Color(0x07111fd9);
    private static final Color GRADIENT_BOTTOM_LEFT = new Color();
    private static final Color GRADIENT_BOTTOM_RIGHT = new Color();
    private static final Color GRADIENT_TOP_RIGHT = new Color();
    private static final Color GRADIENT_TOP_LEFT = new Color();
    private static final Color CUSTOM_SURFACE = new Color();
    private static final Color RESOLVED_ACCENT = new Color();
    private static final Color RESOLVED_PALETTE_ACCENT = new Color();

    private GdxUiButtonStyle() {
    }

    static void draw(ShapeRenderer shapes, float x, float y, float width,
            float height, Tone tone, boolean enabled, float hoverAmount,
            boolean pressed, float alpha) {
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        Color border = enabled
                ? resolveAccent(tone, hover, RESOLVED_ACCENT) : LINE;
        Color fill = fill(tone, enabled, pressed);
        drawMaterial(shapes, x, y, width, height, border, fill, enabled,
                hover, pressed, alpha,
                tone == Tone.FEATURED ? 0.52f : 0.42f, true);
    }

    /** Compact icon material used inside an already framed toolbar. */
    static void drawBorderless(ShapeRenderer shapes, float x, float y,
            float width, float height, Tone tone, boolean enabled,
            float hoverAmount, boolean pressed, float alpha) {
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        drawMaterial(shapes, x, y, width, height,
                enabled ? resolveAccent(tone, hover, RESOLVED_ACCENT) : LINE,
                fill(tone, enabled, pressed), enabled, hover, pressed, alpha,
                tone == Tone.FEATURED ? 0.52f : 0.42f, false);
    }

    /**
     * The same material used by dialogs, rendered with a caller-owned poker
     * palette. This keeps HUD action semantics without maintaining a second,
     * visually divergent button skin.
     */
    static void drawPalette(ShapeRenderer shapes, float x, float y,
            float width, float height, Color accent, float surfaceRed,
            float surfaceGreen, float surfaceBlue, float surfaceAlpha,
            boolean enabled, float hoverAmount, boolean pressed, float alpha) {
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        CUSTOM_SURFACE.set(surfaceRed, surfaceGreen, surfaceBlue,
                surfaceAlpha);
        Color border = enabled
                ? resolvePaletteAccent(accent, hover,
                        RESOLVED_PALETTE_ACCENT)
                : LINE;
        drawMaterial(shapes, x, y, width, height,
                border, CUSTOM_SURFACE, enabled, hover,
                pressed, alpha, 0.42f, true);
    }

    private static void drawMaterial(ShapeRenderer shapes, float x, float y,
            float width, float height, Color border, Color fill,
            boolean enabled, float hover, boolean pressed, float alpha,
            float glowStrength, boolean borderVisible) {
        // Hover is deliberately outline-only: it cannot produce patches or
        // alter the perceived shape of the button.
        if (borderVisible && enabled && hover > 0.01f) {
            shapes.setColor(border.r, border.g, border.b,
                    glowStrength * hover * alpha);
            roundedRectOutline(shapes, x - 2f, y - 2f,
                    width + 4f, height + 4f, 16f, 2f);
        }

        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 14f);
        if (borderVisible) {
            shapes.setColor(border.r, border.g, border.b,
                    (enabled ? 1f : 0.55f) * alpha);
            roundedRectOutline(shapes, x + 1f, y + 1f, width - 2f,
                    height - 2f, 13f, 2f);
        } else if (enabled && hover > 0.01f) {
            shapes.setColor(border.r, border.g, border.b,
                    0.16f * hover * alpha);
            roundedRect(shapes, x + 2f, y + 2f, width - 4f,
                    height - 4f, 12f);
            // Toolbar buttons remain borderless while idle, but hover must
            // expose the same semantic rim as every regular button.
            shapes.setColor(border.r, border.g, border.b,
                    0.88f * hover * alpha);
            roundedRectOutline(shapes, x + 1f, y + 1f,
                    width - 2f, height - 2f, 13f, 2f);
        }

        // A continuous glass bevel restores volume without the decorative
        // top bars that previously made FEATURED and regular buttons look as
        // if they came from different skins.
        float inset = Math.min(9f, Math.max(4f, height * 0.12f));
        float innerWidth = Math.max(0f, width - inset * 2f);
        float innerHeight = Math.max(0f, height - inset * 2f);
        if (innerWidth > 0f && innerHeight > 0f) {
            float split = innerHeight * 0.46f;
            gradient(shapes, x + inset, y + inset, innerWidth, split,
                    0f, 0f, 0f,
                    (pressed ? 0.18f : 0.13f) * alpha,
                    0f, 0f, 0f, 0.015f * alpha);
            gradient(shapes, x + inset, y + inset + split,
                    innerWidth, innerHeight - split,
                    1f, 1f, 1f, 0.012f * alpha,
                    1f, 1f, 1f,
                    ((pressed ? 0.055f : 0.10f) + 0.03f * hover) * alpha);
        }

        if (borderVisible) {
            shapes.setColor(1f, 1f, 1f, 0.07f * alpha);
            roundedRectOutline(shapes, x + 5f, y + 5f,
                    width - 10f, height - 10f, 9f, 1f);
        }
    }

    static Color labelColor(Tone tone, boolean enabled) {
        if (!enabled) return DISABLED;
        return tone == Tone.POSITIVE || tone == Tone.DANGER
                ? Color.WHITE : GOLD;
    }

    static Color resolveAccent(Tone tone, float hoverAmount, Color result) {
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        Color base = switch (tone) {
            case POSITIVE -> POSITIVE;
            case DANGER -> DANGER;
            case FEATURED -> GOLD;
            case NEUTRAL -> CYAN_DARK;
        };
        Color highlighted = switch (tone) {
            case POSITIVE -> POSITIVE_HOVER;
            case DANGER -> DANGER_HOVER;
            case FEATURED -> FEATURED_HOVER;
            case NEUTRAL -> CYAN;
        };
        return result.set(base).lerp(highlighted, hover);
    }

    static Color resolvePaletteAccent(Color accent, float hoverAmount,
            Color result) {
        float hover = Math.max(0f, Math.min(1f, hoverAmount));
        return result.set(accent).lerp(Color.WHITE, 0.24f * hover);
    }

    private static Color fill(Tone tone, boolean enabled, boolean pressed) {
        if (!enabled) return DISABLED_FILL;
        return switch (tone) {
            case POSITIVE -> pressed
                    ? POSITIVE_PRESSED_FILL : POSITIVE_FILL;
            case DANGER -> pressed ? DANGER_PRESSED_FILL : DANGER_FILL;
            case FEATURED -> pressed
                    ? FEATURED_PRESSED_FILL : FEATURED_FILL;
            case NEUTRAL -> pressed ? NEUTRAL_PRESSED_FILL : NEUTRAL_FILL;
        };
    }

    private static void gradient(ShapeRenderer shapes, float x, float y,
            float width, float height, float red, float green, float blue,
            float bottomAlpha, float topRed, float topGreen, float topBlue,
            float topAlpha) {
        GRADIENT_BOTTOM_LEFT.set(red, green, blue, bottomAlpha);
        GRADIENT_BOTTOM_RIGHT.set(red, green, blue, bottomAlpha);
        GRADIENT_TOP_RIGHT.set(topRed, topGreen, topBlue, topAlpha);
        GRADIENT_TOP_LEFT.set(topRed, topGreen, topBlue, topAlpha);
        shapes.rect(x, y, width, height,
                GRADIENT_BOTTOM_LEFT, GRADIENT_BOTTOM_RIGHT,
                GRADIENT_TOP_RIGHT, GRADIENT_TOP_LEFT);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, r, height - 2f * r);
        shapes.rect(x + width - r, y + r, r, height - 2f * r);
        shapes.arc(x + r, y + r, r, 180f, 90f, 18);
        shapes.arc(x + width - r, y + r, r, 270f, 90f, 18);
        shapes.arc(x + width - r, y + height - r, r, 0f, 90f, 18);
        shapes.arc(x + r, y + height - r, r, 90f, 90f, 18);
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
