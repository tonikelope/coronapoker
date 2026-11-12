/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntSupplier;
import org.cef.browser.LibGdxCefBrowser;

/** Delivers text through libGDX's character events and editing keys through Chromium. */
final class GdxBrowserKeyboard {

    private final GdxTextKeyRepeat repeat = new GdxTextKeyRepeat();

    boolean key(LibGdxCefBrowser browser, int event, int keycode, char character, int modifiers) {
        if (event == KeyEvent.KEY_TYPED) {
            if (!Character.isISOControl(character)) {
                browser.dispatchKey(event, KeyEvent.VK_UNDEFINED, character, modifiers);
            }
            return true;
        }
        if (event == KeyEvent.KEY_RELEASED) repeat.release(keycode);
        if (event == KeyEvent.KEY_PRESSED) {
            if (GdxTextKeyRepeat.isEditingKey(keycode)) repeat.press(keycode, browser);
            else if (!modifierKey(keycode)) repeat.clear();
        }
        return dispatch(browser, event, keycode, modifiers, false);
    }

    void update(float delta, LibGdxCefBrowser browser, IntSupplier modifiers) {
        repeat.update(delta, browser,
                keycode -> dispatch(browser, KeyEvent.KEY_PRESSED, keycode, modifiers.getAsInt(), true));
    }

    void clear() {
        repeat.clear();
    }

    private static boolean modifierKey(int key) {
        return switch (key) {
            case Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT, Input.Keys.CONTROL_LEFT,
                    Input.Keys.CONTROL_RIGHT, Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT,
                    Input.Keys.SYM -> true;
            default -> false;
        };
    }

    private static boolean dispatch(LibGdxCefBrowser browser, int event, int keycode,
            int modifiers, boolean repeated) {
        String parameters = parameters(event, keycode, modifiers, repeated);
        if (parameters == null) return false;
        browser.getDevToolsClient().executeDevToolsMethod("Input.dispatchKeyEvent", parameters);
        return true;
    }

    static String parameters(int event, int keycode, int modifiers, boolean repeated) {
        BrowserKey key = browserKey(keycode, modifiers);
        if (key == null) return null;
        // Synthesized AWT events lack a Windows scan code. Keep the DOM and
        // virtual codes explicit, including for repeat presses.
        int flags = 0;
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) flags |= 1;
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) flags |= 2;
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) flags |= 4;
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) flags |= 8;
        boolean down = event == KeyEvent.KEY_PRESSED;
        boolean enter = down && "Enter".equals(key.key());
        String type = !down ? "keyUp" : enter ? "keyDown" : "rawKeyDown";
        String text = enter ? ",\"text\":\"\\r\",\"unmodifiedText\":\"\\r\"" : "";
        String command = down ? editCommand(keycode, modifiers) : null;
        String commands = command == null ? "" : ",\"commands\":[\"" + command + "\"]";
        return "{\"type\":\"" + type + "\",\"key\":\"" + key.key()
                + "\",\"code\":\"" + key.code() + "\",\"windowsVirtualKeyCode\":"
                + key.virtualKey() + ",\"modifiers\":" + flags
                + (repeated ? ",\"autoRepeat\":true" : "") + text + commands + "}";
    }

    private static String editCommand(int key, int modifiers) {
        boolean shortcut = (modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
        if (key == Input.Keys.A && shortcut) return "SelectAll";
        return switch (key) {
            case Input.Keys.BACKSPACE -> shortcut ? "DeleteWordBackward" : "DeleteBackward";
            case Input.Keys.FORWARD_DEL -> shortcut ? "DeleteWordForward" : "DeleteForward";
            default -> null;
        };
    }

    private record BrowserKey(String key, String code, int virtualKey) {
        BrowserKey(String name, int virtualKey) {
            this(name, name, virtualKey);
        }
    }

    private static BrowserKey browserKey(int key, int modifiers) {
        if ((modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0
                && key >= Input.Keys.A && key <= Input.Keys.Z) {
            char letter = (char) ('A' + key - Input.Keys.A);
            return new BrowserKey(Character.toString(Character.toLowerCase(letter)), "Key" + letter, letter);
        }
        return switch (key) {
            case Input.Keys.TAB -> new BrowserKey("Tab", 9);
            case Input.Keys.ENTER -> new BrowserKey("Enter", 13);
            case Input.Keys.NUMPAD_ENTER -> new BrowserKey("Enter", "NumpadEnter", 13);
            case Input.Keys.BACKSPACE -> new BrowserKey("Backspace", 8);
            case Input.Keys.FORWARD_DEL -> new BrowserKey("Delete", 46);
            case Input.Keys.LEFT -> new BrowserKey("ArrowLeft", 37);
            case Input.Keys.RIGHT -> new BrowserKey("ArrowRight", 39);
            case Input.Keys.UP -> new BrowserKey("ArrowUp", 38);
            case Input.Keys.DOWN -> new BrowserKey("ArrowDown", 40);
            case Input.Keys.HOME -> new BrowserKey("Home", 36);
            case Input.Keys.END -> new BrowserKey("End", 35);
            case Input.Keys.PAGE_UP -> new BrowserKey("PageUp", 33);
            case Input.Keys.PAGE_DOWN -> new BrowserKey("PageDown", 34);
            default -> null;
        };
    }
}
