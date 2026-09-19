/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * GDX consumer of the canonical Swing shortcut persistence contract.
 *
 * <p>Swing stores each override as {@code shortcut.<stable-id>} with value
 * {@code <AWT keyCode>,<DOWN modifiers>}.  The renderer must translate that
 * representation; treating the persisted AWT number as a libGDX key code is
 * incorrect because the two numeric spaces are unrelated.</p>
 */
final class GdxShortcutBindings {

    static final String PAUSE = "PAUSE";
    static final String FULLSCREEN = "FULL-SCREEN";
    static final String LIGHTS = "LIGHTS";
    static final String HALT = "HALT";
    static final String LOG = "REGISTRO";
    static final String BUYIN = "BUYIN";
    static final String QUIT = "QUIT";
    static final String FORCE_EXIT = "FORCE_EXIT";
    static final String CHECK = "CHECK-BUTTON";
    static final String FOLD = "FOLD-BUTTON";
    static final String BET_UP = "BET-UP";
    static final String BET_DOWN = "BET-DOWN";
    static final String BET = "BET-BUTTON";
    static final String ALL_IN = "ALLIN-BUTTON";
    static final String MUTE = "SOUND-SWITCH";
    static final String VOLUME_UP = "VOLUME-UP";
    static final String VOLUME_DOWN = "VOLUME-DOWN";
    static final String VOICE_RECORD = "VOICE-RECORD";
    static final String FASTCHAT_IMAGE = "FASTCHAT-IMAGE";
    static final String SCREENSHOT = "SCREENSHOT";

    private static final String PREFIX = "shortcut.";
    private static final int RELEVANT_MODIFIERS = InputEvent.SHIFT_DOWN_MASK
            | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private final Properties properties;
    private final LinkedHashMap<String, Definition> definitions
            = new LinkedHashMap<>();
    private volatile Map<String, Binding> current = Map.of();
    private Map<String, Binding> editSnapshot;

    GdxShortcutBindings(Properties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        define(PAUSE, KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK);
        define(FULLSCREEN, KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK);
        define(LIGHTS, KeyEvent.VK_L, InputEvent.ALT_DOWN_MASK);
        define(HALT, KeyEvent.VK_H, InputEvent.ALT_DOWN_MASK);
        define(LOG, KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK);
        define(BUYIN, KeyEvent.VK_S, 0);
        define(QUIT, KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK);
        define(FORCE_EXIT, KeyEvent.VK_ESCAPE,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        define(CHECK, KeyEvent.VK_SPACE, 0);
        define(FOLD, KeyEvent.VK_ESCAPE, 0);
        define(BET_UP, KeyEvent.VK_UP, 0, KeyEvent.VK_RIGHT);
        define(BET_DOWN, KeyEvent.VK_DOWN, 0, KeyEvent.VK_LEFT);
        define(BET, KeyEvent.VK_ENTER, 0);
        define(ALL_IN, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
        define(MUTE, KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK);
        define(VOLUME_UP, KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK);
        define(VOLUME_DOWN, KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);
        defineKeycodeOnly(VOICE_RECORD, KeyEvent.VK_F9);
        define(FASTCHAT_IMAGE, KeyEvent.VK_1, 0);
        define(SCREENSHOT, KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK);
        reload();
    }

    synchronized void reload() {
        LinkedHashMap<String, Binding> loaded = new LinkedHashMap<>();
        for (Definition definition : definitions.values()) {
            Binding override = deserialize(properties.getProperty(
                    PREFIX + definition.id));
            loaded.put(definition.id,
                    override == null ? definition.fallback : override);
        }
        current = Map.copyOf(loaded);
    }

    String actionFor(int gdxKeyCode, boolean alt, boolean control,
            boolean shift) {
        for (Definition definition : definitions.values()) {
            Binding binding = current.get(definition.id);
            if (matches(binding, definition.keycodeOnly, gdxKeyCode,
                    alt, control, shift)) {
                return definition.id;
            }
            for (Binding alias : definition.aliases) {
                if (matches(alias, false, gdxKeyCode, alt, control, shift)) {
                    return definition.id;
                }
            }
        }
        return null;
    }

    String justPressedAction(Input input, boolean alt, boolean control,
            boolean shift) {
        Objects.requireNonNull(input, "input");
        for (Definition definition : definitions.values()) {
            Binding binding = current.get(definition.id);
            if (binding != null && input.isKeyJustPressed(binding.gdxKeyCode)
                    && matches(binding, definition.keycodeOnly,
                            binding.gdxKeyCode, alt, control, shift)) {
                return definition.id;
            }
            for (Binding alias : definition.aliases) {
                if (input.isKeyJustPressed(alias.gdxKeyCode)
                        && matches(alias, false, alias.gdxKeyCode,
                                alt, control, shift)) {
                    return definition.id;
                }
            }
        }
        return null;
    }

    boolean keyCodeMatches(String id, int gdxKeyCode) {
        Binding binding = current.get(id);
        return binding != null && binding.gdxKeyCode == gdxKeyCode;
    }

    int keyCode(String id) {
        Binding binding = current.get(id);
        return binding == null ? Input.Keys.UNKNOWN : binding.gdxKeyCode;
    }

    String displayFor(String id) {
        return display(current.get(id));
    }

    synchronized void beginEdit() {
        editSnapshot = new LinkedHashMap<>(current);
    }

    synchronized boolean hasPendingEdits() {
        return editSnapshot != null && !editSnapshot.equals(current);
    }

    synchronized void cancelEdit() {
        if (editSnapshot != null) {
            current = Map.copyOf(editSnapshot);
            editSnapshot = null;
        }
    }

    synchronized void commitEdit() {
        for (Definition definition : definitions.values()) {
            Binding binding = current.get(definition.id);
            String key = PREFIX + definition.id;
            if (binding == null || binding.equals(definition.fallback)) {
                properties.remove(key);
            } else {
                properties.setProperty(key, binding.awtKeyCode + ","
                        + binding.modifiers);
            }
        }
        editSnapshot = null;
    }

    synchronized void resetAllEdits() {
        LinkedHashMap<String, Binding> reset = new LinkedHashMap<>();
        for (Definition definition : definitions.values()) {
            reset.put(definition.id, definition.fallback);
        }
        current = Map.copyOf(reset);
    }

    synchronized Assignment assign(String id, int gdxKeyCode, boolean alt,
            boolean control, boolean shift) {
        Definition target = definitions.get(id);
        if (target == null || isModifierKey(gdxKeyCode)) {
            return Assignment.UNSUPPORTED;
        }
        int awtKeyCode = gdxToAwt(gdxKeyCode);
        if (awtKeyCode == KeyEvent.VK_UNDEFINED) {
            return Assignment.UNSUPPORTED;
        }
        int modifiers = target.keycodeOnly ? 0
                : (shift ? InputEvent.SHIFT_DOWN_MASK : 0)
                | (control ? InputEvent.CTRL_DOWN_MASK : 0)
                | (alt ? InputEvent.ALT_DOWN_MASK : 0);
        Binding candidate = new Binding(awtKeyCode, gdxKeyCode, modifiers);
        for (Definition definition : definitions.values()) {
            if (!definition.id.equals(id)
                    && conflicts(candidate, target.keycodeOnly,
                            current.get(definition.id),
                            definition.keycodeOnly)) {
                return Assignment.CONFLICT;
            }
            for (Binding alias : definition.aliases) {
                if (conflicts(candidate, target.keycodeOnly, alias, false)) {
                    return Assignment.CONFLICT;
                }
            }
        }
        LinkedHashMap<String, Binding> edited = new LinkedHashMap<>(current);
        edited.put(id, candidate);
        current = Map.copyOf(edited);
        return Assignment.ASSIGNED;
    }

    List<ShortcutEntry> editableEntries() {
        return editableEntries(null);
    }

    List<ShortcutEntry> editableEntries(GdxGameText text) {
        ArrayList<ShortcutEntry> entries = new ArrayList<>();
        for (Definition definition : definitions.values()) {
            entries.add(new ShortcutEntry(definition.id,
                    text == null ? shortcutDescription(definition.id)
                            : shortcutDescription(definition.id, text),
                    display(current.get(definition.id))));
        }
        return List.copyOf(entries);
    }

    private static String shortcutDescription(String id, GdxGameText text) {
        String suffix = switch (id) {
            case PAUSE -> "pause";
            case FULLSCREEN -> "fullscreen";
            case LIGHTS -> "lights";
            case HALT -> "halt";
            case LOG -> "log";
            case BUYIN -> "buyin";
            case QUIT -> "quit";
            case FORCE_EXIT -> "force_exit";
            case CHECK -> "check";
            case FOLD -> "fold";
            case BET_UP -> "bet_up";
            case BET_DOWN -> "bet_down";
            case BET -> "bet";
            case ALL_IN -> "all_in";
            case MUTE -> "mute";
            case VOLUME_UP -> "volume_up";
            case VOLUME_DOWN -> "volume_down";
            case VOICE_RECORD -> "voice_record";
            case FASTCHAT_IMAGE -> "fastchat_image";
            case SCREENSHOT -> "screenshot";
            default -> null;
        };
        return suffix == null ? id
                : text.translate("gdx.settings.shortcut.action." + suffix);
    }

    private static boolean conflicts(Binding left, boolean leftKeycodeOnly,
            Binding right, boolean rightKeycodeOnly) {
        if (left == null || right == null
                || left.gdxKeyCode != right.gdxKeyCode) return false;
        return leftKeycodeOnly || rightKeycodeOnly
                || left.modifiers == right.modifiers;
    }

    private static boolean isModifierKey(int keycode) {
        return keycode == Input.Keys.ALT_LEFT || keycode == Input.Keys.ALT_RIGHT
                || keycode == Input.Keys.CONTROL_LEFT
                || keycode == Input.Keys.CONTROL_RIGHT
                || keycode == Input.Keys.SHIFT_LEFT
                || keycode == Input.Keys.SHIFT_RIGHT
                || keycode == Input.Keys.SYM;
    }

    private static String shortcutDescription(String id) {
        return switch (id) {
            case PAUSE -> "PAUSAR LA TIMBA";
            case FULLSCREEN -> "PANTALLA COMPLETA";
            case LIGHTS -> "ENCENDER / APAGAR LUCES";
            case HALT -> "DETENER LA TIMBA";
            case LOG -> "ABRIR EL REGISTRO";
            case BUYIN -> "VER TU BUY-IN";
            case QUIT -> "SALIR DE LA TIMBA";
            case FORCE_EXIT -> "FORZAR EL CIERRE";
            case CHECK -> "PASAR / MOSTRAR";
            case FOLD -> "RETIRARSE (NO IR)";
            case BET_UP -> "SUBIR LA APUESTA";
            case BET_DOWN -> "BAJAR LA APUESTA";
            case BET -> "CONFIRMAR LA APUESTA";
            case ALL_IN -> "ALL-IN";
            case MUTE -> "SILENCIAR / ACTIVAR SONIDO";
            case VOLUME_UP -> "SUBIR EL VOLUMEN";
            case VOLUME_DOWN -> "BAJAR EL VOLUMEN";
            case VOICE_RECORD -> "GRABAR NOTA DE VOZ";
            case FASTCHAT_IMAGE -> "ABRIR IMÁGENES / GIFS";
            case SCREENSHOT -> "CAPTURAR LA PANTALLA";
            default -> id;
        };
    }

    private static String display(Binding binding) {
        if (binding == null) return "?";
        ArrayList<String> parts = new ArrayList<>();
        if ((binding.modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            parts.add("CTRL");
        }
        if ((binding.modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            parts.add("ALT");
        }
        if ((binding.modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            parts.add("SHIFT");
        }
        parts.add(keyName(binding.awtKeyCode));
        return String.join(" + ", parts);
    }

    private static String keyName(int awtKeyCode) {
        return switch (awtKeyCode) {
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_ESCAPE -> "ESC";
            case KeyEvent.VK_SPACE -> "SPACE";
            case KeyEvent.VK_BACK_SPACE -> "BACK";
            default -> KeyEvent.getKeyText(awtKeyCode).toUpperCase();
        };
    }

    private void define(String id, int awtKeyCode, int modifiers,
            int... awtAliasKeyCodes) {
        Binding fallback = translated(awtKeyCode, modifiers);
        ArrayList<Binding> aliases = new ArrayList<>();
        for (int alias : awtAliasKeyCodes) {
            aliases.add(translated(alias, 0));
        }
        definitions.put(id, new Definition(id, fallback,
                List.copyOf(aliases), false));
    }

    private void defineKeycodeOnly(String id, int awtKeyCode) {
        definitions.put(id, new Definition(id,
                translated(awtKeyCode, 0), List.of(), true));
    }

    private static boolean matches(Binding binding, boolean keycodeOnly,
            int keycode, boolean alt, boolean control, boolean shift) {
        if (binding == null || binding.gdxKeyCode != keycode) return false;
        if (keycodeOnly) return true;
        int actual = (shift ? InputEvent.SHIFT_DOWN_MASK : 0)
                | (control ? InputEvent.CTRL_DOWN_MASK : 0)
                | (alt ? InputEvent.ALT_DOWN_MASK : 0);
        return binding.modifiers == actual;
    }

    private static Binding deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) return null;
        try {
            String[] parts = serialized.trim().split(",", -1);
            int awtKeyCode = Integer.parseInt(parts[0].trim());
            int modifiers = parts.length > 1
                    ? Integer.parseInt(parts[1].trim()) & RELEVANT_MODIFIERS
                    : 0;
            // libGDX's desktop input path has no portable Meta/AltGraph state
            // exposed to this renderer. Keep the canonical default rather than
            // silently firing a customized high-impact action without them.
            if ((modifiers & (InputEvent.META_DOWN_MASK
                    | InputEvent.ALT_GRAPH_DOWN_MASK)) != 0) return null;
            return translatedOrNull(awtKeyCode, modifiers);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Binding translated(int awtKeyCode, int modifiers) {
        Binding translated = translatedOrNull(awtKeyCode, modifiers);
        if (translated == null) {
            throw new IllegalArgumentException("Unsupported default AWT key: "
                    + awtKeyCode);
        }
        return translated;
    }

    private static Binding translatedOrNull(int awtKeyCode, int modifiers) {
        int gdxKeyCode = awtToGdx(awtKeyCode);
        return gdxKeyCode == Input.Keys.UNKNOWN ? null
                : new Binding(awtKeyCode, gdxKeyCode,
                        modifiers & RELEVANT_MODIFIERS);
    }

    static int awtToGdx(int awtKeyCode) {
        if (awtKeyCode >= KeyEvent.VK_A && awtKeyCode <= KeyEvent.VK_Z) {
            return Input.Keys.A + awtKeyCode - KeyEvent.VK_A;
        }
        if (awtKeyCode >= KeyEvent.VK_0 && awtKeyCode <= KeyEvent.VK_9) {
            return Input.Keys.NUM_0 + awtKeyCode - KeyEvent.VK_0;
        }
        if (awtKeyCode >= KeyEvent.VK_F1 && awtKeyCode <= KeyEvent.VK_F12) {
            return Input.Keys.F1 + awtKeyCode - KeyEvent.VK_F1;
        }
        return switch (awtKeyCode) {
            case KeyEvent.VK_ESCAPE -> Input.Keys.ESCAPE;
            case KeyEvent.VK_ENTER -> Input.Keys.ENTER;
            case KeyEvent.VK_SPACE -> Input.Keys.SPACE;
            case KeyEvent.VK_TAB -> Input.Keys.TAB;
            case KeyEvent.VK_BACK_SPACE -> Input.Keys.BACKSPACE;
            case KeyEvent.VK_DELETE -> Input.Keys.FORWARD_DEL;
            case KeyEvent.VK_INSERT -> Input.Keys.INSERT;
            case KeyEvent.VK_HOME -> Input.Keys.HOME;
            case KeyEvent.VK_END -> Input.Keys.END;
            case KeyEvent.VK_PAGE_UP -> Input.Keys.PAGE_UP;
            case KeyEvent.VK_PAGE_DOWN -> Input.Keys.PAGE_DOWN;
            case KeyEvent.VK_LEFT -> Input.Keys.LEFT;
            case KeyEvent.VK_RIGHT -> Input.Keys.RIGHT;
            case KeyEvent.VK_UP -> Input.Keys.UP;
            case KeyEvent.VK_DOWN -> Input.Keys.DOWN;
            case KeyEvent.VK_PLUS -> Input.Keys.PLUS;
            case KeyEvent.VK_MINUS -> Input.Keys.MINUS;
            case KeyEvent.VK_COMMA -> Input.Keys.COMMA;
            case KeyEvent.VK_PERIOD -> Input.Keys.PERIOD;
            default -> Input.Keys.UNKNOWN;
        };
    }

    static int gdxToAwt(int gdxKeyCode) {
        if (gdxKeyCode >= Input.Keys.A && gdxKeyCode <= Input.Keys.Z) {
            return KeyEvent.VK_A + gdxKeyCode - Input.Keys.A;
        }
        if (gdxKeyCode >= Input.Keys.NUM_0
                && gdxKeyCode <= Input.Keys.NUM_9) {
            return KeyEvent.VK_0 + gdxKeyCode - Input.Keys.NUM_0;
        }
        if (gdxKeyCode >= Input.Keys.F1 && gdxKeyCode <= Input.Keys.F12) {
            return KeyEvent.VK_F1 + gdxKeyCode - Input.Keys.F1;
        }
        return switch (gdxKeyCode) {
            case Input.Keys.ESCAPE -> KeyEvent.VK_ESCAPE;
            case Input.Keys.ENTER -> KeyEvent.VK_ENTER;
            case Input.Keys.SPACE -> KeyEvent.VK_SPACE;
            case Input.Keys.TAB -> KeyEvent.VK_TAB;
            case Input.Keys.BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case Input.Keys.FORWARD_DEL -> KeyEvent.VK_DELETE;
            case Input.Keys.INSERT -> KeyEvent.VK_INSERT;
            case Input.Keys.HOME -> KeyEvent.VK_HOME;
            case Input.Keys.END -> KeyEvent.VK_END;
            case Input.Keys.PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case Input.Keys.PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
            case Input.Keys.LEFT -> KeyEvent.VK_LEFT;
            case Input.Keys.RIGHT -> KeyEvent.VK_RIGHT;
            case Input.Keys.UP -> KeyEvent.VK_UP;
            case Input.Keys.DOWN -> KeyEvent.VK_DOWN;
            case Input.Keys.PLUS -> KeyEvent.VK_PLUS;
            case Input.Keys.MINUS -> KeyEvent.VK_MINUS;
            case Input.Keys.COMMA -> KeyEvent.VK_COMMA;
            case Input.Keys.PERIOD -> KeyEvent.VK_PERIOD;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }

    enum Assignment {
        ASSIGNED, CONFLICT, UNSUPPORTED
    }

    record ShortcutEntry(String id, String description, String display) {
    }

    private record Binding(int awtKeyCode, int gdxKeyCode, int modifiers) {
    }

    private record Definition(String id, Binding fallback,
            List<Binding> aliases, boolean keycodeOnly) {
    }
}
