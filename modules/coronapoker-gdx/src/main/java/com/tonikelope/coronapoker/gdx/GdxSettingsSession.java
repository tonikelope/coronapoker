/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.PreferencesService;
import java.util.EnumMap;
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
    private final Map<Context, Integer> lastTabByContext =
            new EnumMap<>(Context.class);
    private final Map<Context, EnumMap<GdxSettingsContract.Section, Integer>>
            lastSubpageByContext = new EnumMap<>(Context.class);
    private Map<String, String> preferenceSnapshot = Map.of();
    private boolean openedPerspective;
    private boolean open;

    void begin(Context nextContext, Properties properties) {
        context = nextContext == null ? Context.MENU : nextContext;
        tabIndex = lastTabByContext.getOrDefault(context, 0);
        preferenceSnapshot = properties == null
                ? Map.of() : GdxSettingsContract.snapshot(properties);
        openedPerspective = properties != null && GdxAppearanceOptions.perspectiveTable(properties);
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
            case MENU, WAITING_ROOM ->
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
        lastTabByContext.put(context, tabIndex);
    }

    int subpageIndex(GdxSettingsContract.Section requestedSection) {
        GdxSettingsContract.Section target = requestedSection == null
                ? section() : requestedSection;
        Map<GdxSettingsContract.Section, Integer> remembered =
                lastSubpageByContext.get(context);
        return remembered == null ? 0
                : Math.max(0, remembered.getOrDefault(target, 0));
    }

    int subpageIndex() {
        return subpageIndex(section());
    }

    void selectSubpage(int requested) {
        lastSubpageByContext.computeIfAbsent(context,
                ignored -> new EnumMap<>(GdxSettingsContract.Section.class))
                .put(section(), Math.max(0, requested));
    }

    boolean propertiesChanged(Properties properties) {
        if (properties == null) return false;
        var current = GdxSettingsContract.snapshot(properties);
        // Selecting the active view is a no-op even if the opening profile used its implicit default.
        if (GdxAppearanceOptions.perspectiveTable(properties) == openedPerspective) {
            current.put(GdxSettingsContract.TABLE_VIEW_KEY, preferenceSnapshot.get(GdxSettingsContract.TABLE_VIEW_KEY));
        }
        return !current.equals(preferenceSnapshot);
    }

    void restore(Properties properties, PreferencesService preferences) {
        if (properties == null) return;
        synchronized (properties) {
            if (GdxSettingsContract.snapshot(properties).equals(preferenceSnapshot)) return;
            GdxSettingsContract.restore(properties, preferenceSnapshot);
        }
        // An independent save (for example a lobby chat change) can have flushed a live preview.
        if (preferences != null) preferences.saveDeferred();
    }

    void close() {
        preferenceSnapshot = Map.of();
        open = false;
    }

    boolean open() {
        return open;
    }
}
