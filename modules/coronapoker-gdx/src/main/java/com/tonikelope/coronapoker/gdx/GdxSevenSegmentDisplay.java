/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Shared scalable seven-segment display used by the lobby and game table. */
final class GdxSevenSegmentDisplay {

    private static final long COLON_BLINK_HALF_PERIOD_MILLIS = 750L;

    private GdxSevenSegmentDisplay() {
    }

    static float width(String value, float digitWidth, float gap,
            float colonWidth) {
        float result = 0f;
        for (int index = 0; index < value.length(); index++) {
            result += value.charAt(index) == ':' ? colonWidth : digitWidth;
            if (index + 1 < value.length()) result += gap;
        }
        return result;
    }

    static void draw(ShapeRenderer shapes, String value, float x, float y,
            float digitWidth, float digitHeight, float gap, float colonWidth,
            Color active, Color inactive, Color glow,
            boolean colonsVisible) {
        float cursor = x;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == ':') {
                drawColon(shapes, cursor, y, colonWidth, digitHeight,
                        active, glow, colonsVisible);
                cursor += colonWidth;
            } else {
                drawDigit(shapes, character, cursor, y,
                        digitWidth, digitHeight, active, inactive, glow);
                cursor += digitWidth;
            }
            if (index + 1 < value.length()) cursor += gap;
        }
    }

    static boolean colonsVisible(long epochMillis) {
        return (epochMillis / COLON_BLINK_HALF_PERIOD_MILLIS & 1L) == 0L;
    }

    static int mask(char value) {
        return switch (value) {
            case '0' -> 0x3f;
            case '1' -> 0x06;
            case '2' -> 0x5b;
            case '3' -> 0x4f;
            case '4' -> 0x66;
            case '5' -> 0x6d;
            case '6' -> 0x7d;
            case '7' -> 0x07;
            case '8' -> 0x7f;
            case '9' -> 0x6f;
            default -> 0;
        };
    }

    private static void drawColon(ShapeRenderer shapes, float x, float y,
            float width, float height, Color active, Color glow,
            boolean visible) {
        if (!visible) return;
        float centerX = x + width / 2f;
        float glowRadius = Math.max(1.5f, width * 0.45f);
        float radius = Math.max(1f, width * 0.25f);
        shapes.setColor(glow);
        shapes.circle(centerX, y + height * 0.68f, glowRadius, 20);
        shapes.circle(centerX, y + height * 0.32f, glowRadius, 20);
        shapes.setColor(active);
        shapes.circle(centerX, y + height * 0.68f, radius, 20);
        shapes.circle(centerX, y + height * 0.32f, radius, 20);
    }

    private static void drawDigit(ShapeRenderer shapes, char value,
            float x, float y, float width, float height,
            Color active, Color inactive, Color glow) {
        float thickness = Math.max(1.5f, height * 0.09f);
        float half = height / 2f;
        int mask = mask(value);
        drawSegment(shapes, mask, 0x01,
                x + thickness, y + height - thickness,
                width - thickness * 2f, thickness, active, inactive, glow);
        drawSegment(shapes, mask, 0x02,
                x + width - thickness, y + half + thickness / 2f,
                thickness, half - thickness * 1.5f,
                active, inactive, glow);
        drawSegment(shapes, mask, 0x04,
                x + width - thickness, y + thickness,
                thickness, half - thickness * 1.5f,
                active, inactive, glow);
        drawSegment(shapes, mask, 0x08,
                x + thickness, y,
                width - thickness * 2f, thickness, active, inactive, glow);
        drawSegment(shapes, mask, 0x10,
                x, y + thickness,
                thickness, half - thickness * 1.5f,
                active, inactive, glow);
        drawSegment(shapes, mask, 0x20,
                x, y + half + thickness / 2f,
                thickness, half - thickness * 1.5f,
                active, inactive, glow);
        drawSegment(shapes, mask, 0x40,
                x + thickness, y + half - thickness / 2f,
                width - thickness * 2f, thickness, active, inactive, glow);
    }

    private static void drawSegment(ShapeRenderer shapes, int mask,
            int segment, float x, float y, float width, float height,
            Color active, Color inactive, Color glow) {
        boolean enabled = (mask & segment) != 0;
        float radius = Math.min(width, height) / 2f;
        if (enabled) {
            shapes.setColor(glow);
            roundedRect(shapes, x - 2f, y - 2f,
                    width + 4f, height + 4f, radius + 2f);
        }
        shapes.setColor(enabled ? active : inactive);
        roundedRect(shapes, x, y, width, height, radius);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float r = Math.max(0f, Math.min(radius,
                Math.min(width, height) / 2f));
        shapes.rect(x + r, y, width - r * 2f, height);
        shapes.rect(x, y + r, width, height - r * 2f);
        shapes.circle(x + r, y + r, r, 12);
        shapes.circle(x + width - r, y + r, r, 12);
        shapes.circle(x + r, y + height - r, r, 12);
        shapes.circle(x + width - r, y + height - r, r, 12);
    }
}
