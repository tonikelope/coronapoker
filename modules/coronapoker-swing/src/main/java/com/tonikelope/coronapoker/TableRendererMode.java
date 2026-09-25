/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.Locale;
import java.util.Objects;

/**
 * Selects only the table presentation implementation. Game rules, networking,
 * dealer sequencing and persistence remain shared by both renderers.
 *
 * The saved preference is used for normal starts. A command-line override is
 * process-local and deliberately never rewrites the user's preference.
 */
public enum TableRendererMode {

    SWING("Swing Legacy"),
    GDX("GDX (beta)");

    public static final String PROPERTY_KEY = "table_renderer";
    public static final TableRendererMode DEFAULT = GDX;

    private static volatile TableRendererMode commandLineOverride;

    private final String displayName;

    TableRendererMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Returns the persisted choice, falling back safely for old preferences. */
    public static TableRendererMode preferred() {
        return parse(Helpers.PROPERTIES.getProperty(PROPERTY_KEY), DEFAULT);
    }

    /** Returns the renderer fixed for this process (CLI override wins). */
    public static TableRendererMode effective() {
        TableRendererMode override = commandLineOverride;
        return override != null ? override : preferred();
    }

    public static boolean hasCommandLineOverride() {
        return commandLineOverride != null;
    }

    public static void persistPreferred(TableRendererMode mode) {
        Objects.requireNonNull(mode, "mode");
        Helpers.PROPERTIES.setProperty(PROPERTY_KEY, mode.name().toLowerCase(Locale.ROOT));
        Helpers.savePropertiesFile();
    }

    /**
     * Reads the optional renderer override. Accepted forms are {@code --gdx},
     * {@code --swing}, {@code --renderer=gdx} and
     * {@code --table-renderer=gdx} (and their Swing counterparts).
     *
     * Repeated identical flags are harmless; contradictory flags fail startup
     * instead of selecting whichever happened to be last.
     */
    public static void configureCommandLine(String[] args) {
        TableRendererMode selected = null;
        if (args != null) {
            for (String raw : args) {
                TableRendererMode candidate = commandLineValue(raw);
                if (candidate == null) {
                    continue;
                }
                if (selected != null && selected != candidate) {
                    throw new IllegalArgumentException("Conflicting table renderer flags");
                }
                selected = candidate;
            }
        }
        commandLineOverride = selected;
    }

    private static TableRendererMode commandLineValue(String raw) {
        if (raw == null) {
            return null;
        }
        String arg = raw.trim().toLowerCase(Locale.ROOT);
        if ("--gdx".equals(arg)) {
            return GDX;
        }
        if ("--swing".equals(arg)) {
            return SWING;
        }
        String prefix;
        if (arg.startsWith("--table-renderer=")) {
            prefix = "--table-renderer=";
        } else if (arg.startsWith("--renderer=")) {
            prefix = "--renderer=";
        } else {
            return null;
        }
        String value = arg.substring(prefix.length());
        TableRendererMode parsed = parse(value, null);
        if (parsed == null) {
            throw new IllegalArgumentException("Unknown table renderer: " + value);
        }
        return parsed;
    }

    private static TableRendererMode parse(String value, TableRendererMode fallback) {
        if (value == null) {
            return fallback;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "gdx":
                return GDX;
            case "swing":
            case "swing_legacy":
            case "swing legacy":
                return SWING;
            default:
                return fallback;
        }
    }
}
