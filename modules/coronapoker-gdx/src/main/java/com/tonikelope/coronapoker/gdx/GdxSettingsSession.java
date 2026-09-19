/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Shared transactional/navigation state for every GDX settings surface.
 *
 * <p>The frontend and the live table render different contexts of the same
 * settings dialog. Keeping the section selection and preference snapshot here
 * prevents the two entry points from silently acquiring different cancel or
 * tab semantics.</p>
 */
final class GdxSettingsSession {

    enum Context {
        MENU, WAITING_ROOM, LIVE_TABLE
    }

    private Context context = Context.MENU;
    private int tabIndex;
    private Map<String, String> preferenceSnapshot = Map.of();
    private boolean open;

    void begin(Context nextContext, Properties properties) {
        context = nextContext == null ? Context.MENU : nextContext;
        tabIndex = 0;
        preferenceSnapshot = properties == null
                ? Map.of() : GdxSettingsContract.snapshot(properties);
        open = true;
    }

    List<GdxSettingsContract.Section> sections() {
        return switch (context) {
            case MENU -> GdxSettingsContract.MENU_SECTIONS;
            case WAITING_ROOM, LIVE_TABLE ->
                GdxSettingsContract.TABLE_SECTIONS;
        };
    }

    List<String> gamePages() {
        return switch (context) {
            case MENU -> List.of();
            case WAITING_ROOM ->
                GdxSettingsContract.WAITING_ROOM_GAME_PAGES;
            case LIVE_TABLE -> GdxSettingsContract.LIVE_TABLE_GAME_PAGES;
        };
    }

    List<String> subpages(int shortcutEntries, int shortcutRowsPerPage,
            GdxGameText text) {
        return GdxSettingsContract.subpageLabels(section(), gamePages(),
                shortcutEntries, shortcutRowsPerPage, text);
    }

    List<String> subpages(int shortcutEntries, int shortcutRowsPerPage) {
        return subpages(shortcutEntries, shortcutRowsPerPage, null);
    }

    Context context() {
        return context;
    }

    GdxSettingsContract.Section section() {
        List<GdxSettingsContract.Section> available = sections();
        tabIndex = Math.max(0, Math.min(tabIndex, available.size() - 1));
        return available.get(tabIndex);
    }

    int tabIndex() {
        section();
        return tabIndex;
    }

    void selectTab(int requested) {
        tabIndex = Math.max(0, Math.min(requested, sections().size() - 1));
    }

    boolean propertiesChanged(Properties properties) {
        return properties != null
                && !GdxSettingsContract.snapshot(properties)
                        .equals(preferenceSnapshot);
    }

    void restore(Properties properties) {
        if (properties != null) {
            GdxSettingsContract.restore(properties, preferenceSnapshot);
        }
    }

    void close() {
        preferenceSnapshot = Map.of();
        open = false;
    }

    boolean open() {
        return open;
    }
}
