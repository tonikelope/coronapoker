/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import java.util.Locale;

/** Reports the OpenGL renderer that is actually drawing the GDX frontend. */
final class GdxGraphicsInfo {

    private GdxGraphicsInfo() {
    }

    static String renderer() {
        if (Gdx.gl == null) return "";
        String renderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
        return renderer == null ? "" : renderer.strip();
    }

    static boolean softwareRenderer(String renderer) {
        String normalized = renderer == null
                ? "" : renderer.toLowerCase(Locale.ROOT);
        return normalized.contains("llvmpipe")
                || normalized.contains("softpipe")
                || normalized.contains("swiftshader")
                || normalized.contains("software rasterizer")
                || normalized.contains("microsoft basic render driver")
                || normalized.contains("gdi generic");
    }

    static String displayValue(GdxGameText text) {
        return displayValue(renderer(), text);
    }

    static String displayValue(String renderer, GdxGameText text) {
        String value = renderer == null ? "" : renderer.strip();
        if (value.isEmpty()) {
            return text.translate("gdx.settings.value.unavailable");
        }
        return text.translate(softwareRenderer(value)
                ? "gdx.settings.value.gpu_software"
                : "gdx.settings.value.gpu_active", value);
    }
}
