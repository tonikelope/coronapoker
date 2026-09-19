/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;

/** Runtime F11 transition shared by the native menu and the live table. */
final class GdxDisplayModeController {

    private static final int WINDOWED_WIDTH = 1600;
    private static final int WINDOWED_HEIGHT = 900;
    private static final String ACTIVE_MODE_PROPERTY =
            "coronapoker.gdx.activeWindowMode";
    private static final String FULLSCREEN_MODE_PROPERTY =
            "coronapoker.gdx.preferredFullscreenMode";

    static boolean isFullscreenLike() {
        return Gdx.graphics.isFullscreen()
                || activeMode() == GdxWindowMode.BORDERLESS;
    }

    static void toggle(GdxWindowMode configuredMode) {
        if (!(Gdx.graphics instanceof Lwjgl3Graphics graphics)) return;
        GdxWindowMode preferred = fullscreenPreference(configuredMode,
                preferredFullscreenMode());
        switch (toggleTarget(activeMode(), preferred)) {
            case WINDOWED -> leaveFullscreen(graphics);
            case BORDERLESS -> enterBorderless(graphics);
            case EXCLUSIVE -> enterExclusive(graphics);
        }
    }

    static boolean apply(GdxWindowMode mode) {
        if (!(Gdx.graphics instanceof Lwjgl3Graphics graphics)
                || mode == null) return false;
        rememberFullscreenMode(mode);
        switch (mode) {
            case WINDOWED -> leaveFullscreen(graphics);
            case BORDERLESS -> enterBorderless(graphics);
            case EXCLUSIVE -> enterExclusive(graphics);
        }
        return true;
    }

    private static void leaveFullscreen(Lwjgl3Graphics graphics) {
        Monitor monitor = graphics.getMonitor();
        DisplayMode desktop = graphics.getDisplayMode(monitor);
        // Borderless is a windowed GLFW surface. setWindowedMode() alone
        // preserves its undecorated attribute, producing the small frameless
        // window reported by users. Restore normal decorations explicitly.
        graphics.setUndecorated(false);
        graphics.setResizable(true);
        graphics.setWindowedMode(WINDOWED_WIDTH, WINDOWED_HEIGHT);
        graphics.getWindow().setPosition(
                monitor.virtualX + Math.max(0,
                        (desktop.width - WINDOWED_WIDTH) / 2),
                monitor.virtualY + Math.max(0,
                        (desktop.height - WINDOWED_HEIGHT) / 2));
        setActiveMode(GdxWindowMode.WINDOWED);
    }

    private static void enterExclusive(Lwjgl3Graphics graphics) {
        graphics.setUndecorated(false);
        graphics.setResizable(false);
        graphics.setFullscreenMode(graphics.getDisplayMode(
                graphics.getMonitor()));
        setActiveMode(GdxWindowMode.EXCLUSIVE);
    }

    private static void enterBorderless(Lwjgl3Graphics graphics) {
        Monitor monitor = graphics.getMonitor();
        DisplayMode desktop = graphics.getDisplayMode(monitor);
        graphics.setUndecorated(true);
        graphics.setResizable(false);
        graphics.setWindowedMode(desktop.width, desktop.height);
        graphics.getWindow().setPosition(monitor.virtualX, monitor.virtualY);
        setActiveMode(GdxWindowMode.BORDERLESS);
    }

    static GdxWindowMode activeMode() {
        String value = System.getProperty(ACTIVE_MODE_PROPERTY, "");
        for (GdxWindowMode mode : GdxWindowMode.values()) {
            if (mode.persistedValue().equalsIgnoreCase(value)) return mode;
        }
        return Gdx.graphics != null && Gdx.graphics.isFullscreen()
                ? GdxWindowMode.EXCLUSIVE : GdxWindowMode.WINDOWED;
    }

    private static void setActiveMode(GdxWindowMode mode) {
        System.setProperty(ACTIVE_MODE_PROPERTY,
                mode.persistedValue());
    }

    static void rememberFullscreenMode(GdxWindowMode mode) {
        if (mode == GdxWindowMode.BORDERLESS
                || mode == GdxWindowMode.EXCLUSIVE) {
            System.setProperty(FULLSCREEN_MODE_PROPERTY,
                    mode.persistedValue());
        }
    }

    static GdxWindowMode preferredFullscreenMode() {
        String value = System.getProperty(FULLSCREEN_MODE_PROPERTY, "");
        return GdxWindowMode.EXCLUSIVE.persistedValue()
                .equalsIgnoreCase(value)
                        ? GdxWindowMode.EXCLUSIVE
                        : GdxWindowMode.BORDERLESS;
    }

    static GdxWindowMode toggleTarget(GdxWindowMode active,
            GdxWindowMode preferredFullscreen) {
        if (active == GdxWindowMode.BORDERLESS
                || active == GdxWindowMode.EXCLUSIVE) {
            return GdxWindowMode.WINDOWED;
        }
        return preferredFullscreen == GdxWindowMode.EXCLUSIVE
                ? GdxWindowMode.EXCLUSIVE : GdxWindowMode.BORDERLESS;
    }

    static GdxWindowMode fullscreenPreference(GdxWindowMode configured,
            GdxWindowMode remembered) {
        if (configured == GdxWindowMode.BORDERLESS
                || configured == GdxWindowMode.EXCLUSIVE) {
            return configured;
        }
        return remembered == GdxWindowMode.EXCLUSIVE
                ? GdxWindowMode.EXCLUSIVE : GdxWindowMode.BORDERLESS;
    }

    private GdxDisplayModeController() {
    }
}
