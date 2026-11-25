/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final Properties draft = new Properties();
    private Context context = Context.MENU;
    private int tabIndex;
    private final Map<Context, Integer> lastTabByContext =
            new EnumMap<>(Context.class);
    private final Map<Context, EnumMap<GdxSettingsContract.Section, Integer>>
            lastSubpageByContext = new EnumMap<>(Context.class);
    private Map<String, String> preferenceSnapshot = Map.of();
    private boolean openedPerspective;
    private GdxDialogSlide.Direction openedDialogSlideDirection;
    private boolean open;

    void begin(Context nextContext, Properties properties) {
        context = nextContext == null ? Context.MENU : nextContext;
        tabIndex = lastTabByContext.getOrDefault(context, 0);
        preferenceSnapshot = new LinkedHashMap<>();
        draft.clear();
        if (properties != null) {
            synchronized (properties) {
                properties.stringPropertyNames().forEach(key ->
                        draft.setProperty(key, properties.getProperty(key)));
                preferenceSnapshot = GdxSettingsContract.snapshot(draft);
            }
        }
        openedPerspective = properties != null && GdxAppearanceOptions.perspectiveTable(properties);
        openedDialogSlideDirection = properties == null ? GdxDialogSlide.Direction.UP
                : GdxAppearanceOptions.dialogSlideDirection(properties);
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

    Properties draftProperties() {
        return draft;
    }

    void initializeSound(boolean enabled) {
        String value = Boolean.toString(enabled);
        draft.setProperty("sonidos", value);
        preferenceSnapshot.put("sonidos", value);
    }

    GdxDialogSlide.Direction dialogSlideDirection(Properties properties) {
        return open ? openedDialogSlideDirection
                : GdxAppearanceOptions.dialogSlideDirection(properties);
    }

    boolean changed(String key) {
        return !Objects.equals(draft.getProperty(key), preferenceSnapshot.get(key));
    }

    boolean propertiesChanged() {
        return !changes().isEmpty();
    }

    private Map<String, String> changes() {
        Map<String, String> edited = GdxSettingsContract.snapshot(draft);
        preferenceSnapshot.keySet().forEach(key -> {
            if (!edited.containsKey(key)) edited.put(key, null);
        });
        if (GdxAppearanceOptions.perspectiveTable(draft) == openedPerspective) {
            edited.put(GdxSettingsContract.TABLE_VIEW_KEY,
                    preferenceSnapshot.get(GdxSettingsContract.TABLE_VIEW_KEY));
        }
        edited.entrySet().removeIf(entry -> Objects.equals(entry.getValue(),
                preferenceSnapshot.get(entry.getKey())));
        return edited;
    }

    void commitProperties(Properties properties) {
        if (!open) return;
        Map<String, String> edited = changes();
        synchronized (properties) {
            edited.forEach((key, value) -> {
                if (value == null) properties.remove(key);
                else properties.setProperty(key, value);
            });
        }
    }

    void applyNativeChanges() {
        if (changed(GdxWindowMode.PREFERENCE_KEY)) {
            GdxWindowMode mode = GdxWindowMode.configured(draft);
            if (mode != GdxDisplayModeController.activeMode() && !GdxDisplayModeController.apply(mode)) {
                resetValue(GdxWindowMode.PREFERENCE_KEY);
            }
        }
        if (changed(GdxAudioDevices.OUTPUT_KEY) && !GdxAudioDevices.selectOutput(
                draft, draft.getProperty(GdxAudioDevices.OUTPUT_KEY, ""))) {
            resetValue(GdxAudioDevices.OUTPUT_KEY);
        }
    }

    private void resetValue(String key) {
        String original = preferenceSnapshot.get(key);
        if (original == null) draft.remove(key);
        else draft.setProperty(key, original);
    }

    void close() {
        preferenceSnapshot = new LinkedHashMap<>();
        draft.clear();
        open = false;
    }

    boolean open() {
        return open;
    }
}
