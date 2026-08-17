/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.KeyStroke;

/**
 * Central registry of the game's customizable keyboard shortcuts. Each action
 * has a STABLE id (matching its AbstractAction name in the {@link GameFrame}
 * and {@link Init} dispatchers) and a default key combination; the user can
 * reassign it from the "Shortcuts" tab in Settings, and the override persists
 * in {@code coronapoker.properties} under key {@code shortcut.<id>}.
 *
 * The two global dispatchers resolve by id via {@link #idFor(KeyStroke)} and
 * run the fixed body registered for that id, so a reassignment takes effect
 * LIVE with nothing to rebuild. The reverse map (KeyStroke -&gt; id) is
 * published atomically (a volatile reference swapped to a new map) so reads
 * from the keyboard thread always see a consistent snapshot.
 *
 * Only keyCode-based shortcuts (with or without modifiers) are modeled. Left
 * out, being of a different nature or fragile across keyboard layouts: the
 * mouse-wheel zoom, the quick fastchat key {@code 'º'} (a dead-key "typed"
 * event), and the internal keys of the chat/image dialogs.
 *
 * @author tonikelope
 */
public final class KeyboardShortcuts {

    private static final Logger LOG = Logger.getLogger(KeyboardShortcuts.class.getName());

    private static final String PROPERTY_PREFIX = "shortcut.";

    // Only these modifiers count toward a shortcut (button masks etc. are discarded).
    private static final int MOD_MASK = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    // STABLE ids (== AbstractAction name in the dispatchers). Do not rename: they are the
    // persistence key and the dispatcher lookup key.
    public static final String PAUSE = "PAUSE";
    public static final String FULLSCREEN = "FULL-SCREEN";
    public static final String COMPACT = "COMPACT-CARDS";
    public static final String LIGHTS = "LIGHTS";
    public static final String HALT = "HALT";
    public static final String REFRESH = "REFRESH";
    public static final String LATENCY = "LATENCY_STATS";
    public static final String CLOCK = "RELOJ";
    public static final String LOG_REGISTRO = "REGISTRO";
    public static final String CHAT = "CHAT";
    public static final String BUYIN = "BUYIN";
    public static final String SCREENSHOT = "SCREENSHOT";
    public static final String QUIT = "QUIT";
    public static final String FORCE_EXIT = "FORCE_EXIT";

    public static final String CHECK = "CHECK-BUTTON";
    public static final String FOLD = "FOLD-BUTTON";
    public static final String BET_UP = "BET-UP";
    public static final String BET_DOWN = "BET-DOWN";
    public static final String BET = "BET-BUTTON";
    public static final String ALLIN = "ALLIN-BUTTON";

    public static final String ZOOM_IN = "ZOOM-IN";
    public static final String ZOOM_OUT = "ZOOM-OUT";
    public static final String ZOOM_RESET = "ZOOM-RESET";

    public static final String MUTE = "SOUND-SWITCH";
    public static final String VOLUME_UP = "VOLUME-UP";
    public static final String VOLUME_DOWN = "VOLUME-DOWN";
    // Voice note is push-to-record (hold key down): NOT dispatched via actionMap, handled by
    // VoiceMessageManager, which reads the key from here. Only its combination is modeled, so it
    // can be reassigned. Keycode-only (no modifiers): holding CTRL+F9 makes no sense for recording.
    public static final String VOICE_RECORD = "VOICE-RECORD";

    public static final String FASTCHAT_IMAGE = "FASTCHAT-IMAGE";

    /**
     * Definition of a reassignable action: id, i18n keys for section/label
     * (reused from
     * {@link ShortcutsDialog}: {@code shortcuts.sec_*} / {@code shortcuts.act_*}),
     * default combination, and FIXED aliases: extra keys that are always active
     * and NOT reassignable (e.g. the horizontal bet arrows), registered in the
     * reverse map both to fire and to reserve the key so nothing else can claim
     * it.
     */
    public static final class Def {

        public final String id;
        public final String section_key;
        public final String label_key;
        public final KeyStroke def;
        public final KeyStroke[] aliases;
        // true = keycode only, no modifiers (voice note push-to-record): capture ignores
        // ALT/CTRL/SHIFT.
        public boolean keycode_only = false;

        Def(String id, String section_key, String label_key, KeyStroke def, KeyStroke... aliases) {
            this.id = id;
            this.section_key = section_key;
            this.label_key = label_key;
            this.def = def;
            this.aliases = aliases;
        }

        Def keycodeOnly() {
            this.keycode_only = true;
            return this;
        }
    }

    private static KeyStroke ks(int key_code, int modifiers) {
        return KeyStroke.getKeyStroke(key_code, modifiers);
    }

    // ORDERED catalog (grouped by section, mirroring the shortcuts help). Order drives the tab UI.
    private static final List<Def> DEFS = new ArrayList<>();

    static {
        DEFS.add(new Def(PAUSE, "shortcuts.sec_game", "shortcuts.act_pause", ks(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(FULLSCREEN, "shortcuts.sec_game", "shortcuts.act_fullscreen", ks(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(COMPACT, "shortcuts.sec_game", "shortcuts.act_compact", ks(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(LIGHTS, "shortcuts.sec_game", "shortcuts.act_lights", ks(KeyEvent.VK_L, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(HALT, "shortcuts.sec_game", "shortcuts.act_halt", ks(KeyEvent.VK_H, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(REFRESH, "shortcuts.sec_game", "shortcuts.act_refresh", ks(KeyEvent.VK_F5, 0)));
        DEFS.add(new Def(LATENCY, "shortcuts.sec_game", "shortcuts.act_latency", ks(KeyEvent.VK_F7, 0)));
        DEFS.add(new Def(CLOCK, "shortcuts.sec_game", "shortcuts.act_clock", ks(KeyEvent.VK_W, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(LOG_REGISTRO, "shortcuts.sec_game", "shortcuts.act_log", ks(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(CHAT, "shortcuts.sec_game", "shortcuts.act_chat", ks(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(BUYIN, "shortcuts.sec_game", "shortcuts.act_buyin", ks(KeyEvent.VK_S, 0)));
        DEFS.add(new Def(SCREENSHOT, "shortcuts.sec_game", "shortcuts.act_screenshot", ks(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)));
        DEFS.add(new Def(QUIT, "shortcuts.sec_game", "shortcuts.act_quit", ks(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK)));
        DEFS.add(new Def(FORCE_EXIT, "shortcuts.sec_game", "shortcuts.act_force", ks(KeyEvent.VK_ESCAPE, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK)));

        DEFS.add(new Def(CHECK, "shortcuts.sec_bet", "shortcuts.act_check", ks(KeyEvent.VK_SPACE, 0)));
        DEFS.add(new Def(FOLD, "shortcuts.sec_bet", "shortcuts.act_fold", ks(KeyEvent.VK_ESCAPE, 0)));
        // Raise/lower bet: primary UP/DOWN is editable; RIGHT/LEFT stay as FIXED aliases (kept, not
        // reassignable), matching how all four arrows already behave.
        DEFS.add(new Def(BET_UP, "shortcuts.sec_bet", "shortcuts.act_bet_up", ks(KeyEvent.VK_UP, 0), ks(KeyEvent.VK_RIGHT, 0)));
        DEFS.add(new Def(BET_DOWN, "shortcuts.sec_bet", "shortcuts.act_bet_down", ks(KeyEvent.VK_DOWN, 0), ks(KeyEvent.VK_LEFT, 0)));
        DEFS.add(new Def(BET, "shortcuts.sec_bet", "shortcuts.act_confirm", ks(KeyEvent.VK_ENTER, 0)));
        DEFS.add(new Def(ALLIN, "shortcuts.sec_bet", "shortcuts.act_allin", ks(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)));

        DEFS.add(new Def(ZOOM_IN, "shortcuts.sec_view", "shortcuts.act_zoomin", ks(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK)));
        DEFS.add(new Def(ZOOM_OUT, "shortcuts.sec_view", "shortcuts.act_zoomout", ks(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK)));
        DEFS.add(new Def(ZOOM_RESET, "shortcuts.sec_view", "shortcuts.act_zoomreset", ks(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK)));

        DEFS.add(new Def(MUTE, "shortcuts.sec_audio", "shortcuts.act_mute", ks(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK)));
        DEFS.add(new Def(VOLUME_UP, "shortcuts.sec_audio", "shortcuts.act_volup", ks(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK)));
        DEFS.add(new Def(VOLUME_DOWN, "shortcuts.sec_audio", "shortcuts.act_voldown", ks(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK)));
        DEFS.add(new Def(VOICE_RECORD, "shortcuts.sec_audio", "shortcuts.act_voice", ks(KeyEvent.VK_F9, 0)).keycodeOnly());

        DEFS.add(new Def(FASTCHAT_IMAGE, "shortcuts.sec_img", "shortcuts.act_images", ks(KeyEvent.VK_1, 0)));
    }

    private static final Map<String, Def> BY_ID = new HashMap<>();

    static {
        for (Def d : DEFS) {
            BY_ID.put(d.id, d);
        }
    }

    // id -> current combination (default unless overridden). Immutable snapshot published atomically.
    private static volatile Map<String, KeyStroke> current = new HashMap<>();
    // combination -> id (includes fixed aliases). Immutable snapshot published atomically: read by
    // the dispatchers from the keyboard thread.
    private static volatile Map<KeyStroke, String> reverse = new HashMap<>();

    static {
        load();
    }

    private KeyboardShortcuts() {
    }

    // Initial load: for each action, use the properties override if valid, else the default.
    private static synchronized void load() {

        Map<String, KeyStroke> cur = new HashMap<>();

        for (Def d : DEFS) {
            KeyStroke override = deserialize(Helpers.PROPERTIES.getProperty(PROPERTY_PREFIX + d.id));
            cur.put(d.id, override != null ? override : d.def);
        }

        // Migrate the old voice-note key property ("voice_message_key", a bare keyCode that
        // VoiceMessageManager used to manage) into the registry, if the user had customized it and
        // no new-style override exists yet.
        if (Helpers.PROPERTIES.getProperty(PROPERTY_PREFIX + VOICE_RECORD) == null) {
            String legacy = Helpers.PROPERTIES.getProperty("voice_message_key");
            if (legacy != null) {
                try {
                    int code = Integer.parseInt(legacy.trim());
                    if (code != KeyEvent.VK_UNDEFINED) {
                        cur.put(VOICE_RECORD, ks(code, 0));
                    }
                } catch (NumberFormatException ignore) {
                }
            }
        }

        current = cur;
        reverse = buildReverse(cur);
    }

    // Rebuilds the reverse map from the current combinations plus the fixed aliases. If two actions
    // collide (shouldn't happen given assignment validation), the first one in DEFS order wins and
    // a warning is logged.
    private static Map<KeyStroke, String> buildReverse(Map<String, KeyStroke> cur) {

        Map<KeyStroke, String> rev = new HashMap<>();

        for (Def d : DEFS) {

            KeyStroke primary = cur.get(d.id);

            if (primary != null) {
                putReverse(rev, primary, d.id);
            }

            for (KeyStroke alias : d.aliases) {
                putReverse(rev, alias, d.id);
            }
        }

        return rev;
    }

    private static void putReverse(Map<KeyStroke, String> rev, KeyStroke k, String id) {
        String prev = rev.putIfAbsent(k, id);
        if (prev != null && !prev.equals(id)) {
            LOG.log(Level.WARNING, "Shortcut collision on {0}: kept {1}, ignored {2}", new Object[]{k, prev, id});
        }
    }

    /**
     * @param id action id
     * @return the current combination (default unless overridden), or
     * {@code null} if the id is unknown
     */
    public static KeyStroke get(String id) {
        return current.get(id);
    }

    /**
     * @param id action id
     * @return true if the action is keycode-only, ignoring modifiers (voice
     * note push-to-record)
     */
    public static boolean isKeycodeOnly(String id) {
        Def d = BY_ID.get(id);
        return d != null && d.keycode_only;
    }

    /**
     * @param id action id
     * @return true if the action's combination differs from the factory default
     */
    public static boolean isCustomized(String id) {
        Def d = BY_ID.get(id);
        KeyStroke k = current.get(id);
        return d != null && k != null && !k.equals(d.def);
    }

    /**
     * keyCode of the action's current combination, for dispatchers that compare
     * by keyCode (e.g. the FOLD/CHECK guards, the volume beep).
     *
     * @param id action id
     * @return the keyCode, or {@link KeyEvent#VK_UNDEFINED} if not applicable
     */
    public static int keyCode(String id) {
        KeyStroke k = current.get(id);
        return k != null ? k.getKeyCode() : KeyEvent.VK_UNDEFINED;
    }

    /**
     * Resolves which action owns a combination (including fixed aliases); used
     * by the dispatchers and by assignment conflict detection.
     *
     * @param ks combination to look up
     * @return the owning action id, or {@code null} if none
     */
    public static String idFor(KeyStroke ks) {
        return ks != null ? reverse.get(ks) : null;
    }

    /**
     * @param ks combination to check
     * @param target_id action that would receive it
     * @return true if free or already owned by {@code target_id}; false if
     * another action (or a fixed alias) already uses it
     */
    public static boolean isAssignable(KeyStroke ks, String target_id) {
        if (ks == null) {
            return false;
        }
        String owner = reverse.get(ks);
        return owner == null || owner.equals(target_id);
    }

    /**
     * Reassigns an action to a new combination LIVE (dispatchers see it
     * instantly). Not persisted — editing is transactional and only written to
     * disk by {@link #commit()}. Does not validate conflicts; callers must
     * check {@link #isAssignable} first.
     *
     * @param id action id
     * @param ks new combination
     */
    public static synchronized void set(String id, KeyStroke ks) {

        if (!BY_ID.containsKey(id) || ks == null) {
            return;
        }

        Map<String, KeyStroke> cur = new HashMap<>(current);
        cur.put(id, ks);
        current = cur;
        reverse = buildReverse(cur);
    }

    /**
     * Restores one action to its factory combination LIVE (not persisted; see
     * {@link #commit()}).
     *
     * @param id action id
     */
    public static synchronized void reset(String id) {

        Def d = BY_ID.get(id);

        if (d == null) {
            return;
        }

        Map<String, KeyStroke> cur = new HashMap<>(current);
        cur.put(id, d.def);
        current = cur;
        reverse = buildReverse(cur);
    }

    /**
     * Restores ALL actions to their factory combinations LIVE (not persisted
     * until {@link #commit()}).
     */
    public static synchronized void resetAll() {

        Map<String, KeyStroke> cur = new HashMap<>();

        for (Def d : DEFS) {
            cur.put(d.id, d.def);
        }

        current = cur;
        reverse = buildReverse(cur);
    }

    // While the Shortcuts tab is capturing a key press, the global dispatchers step aside (bail out
    // immediately) so the pressed combination reaches the capture field instead of firing whatever
    // shortcut it's assigned to.
    private static volatile boolean capturing = false;

    public static boolean isCapturing() {
        return capturing;
    }

    public static void setCapturing(boolean c) {
        capturing = c;
    }

    // --- Transactional editing (matches the Settings dialog: applies live, SAVE persists, Cancel
    // reverts). ---
    private static volatile Map<String, KeyStroke> snapshot = null;

    /**
     * Opens a transactional edit by snapshotting the current state. Changes
     * apply live but aren't persisted until
     * {@link #commit()}; {@link #revert()} restores this snapshot.
     */
    public static synchronized void beginTransaction() {
        snapshot = new HashMap<>(current);
    }

    /**
     * @return true if there are unconfirmed changes since the opening snapshot
     */
    public static boolean isDirty() {
        Map<String, KeyStroke> snap = snapshot;
        return snap != null && !snap.equals(current);
    }

    /**
     * Confirms the edit: persists the current state (one override per action
     * that differs from its default, clearing the key for those at factory
     * settings) and closes the transaction.
     */
    public static synchronized void commit() {

        for (Def d : DEFS) {
            KeyStroke k = current.get(d.id);
            if (k != null && !k.equals(d.def)) {
                Helpers.PROPERTIES.setProperty(PROPERTY_PREFIX + d.id, serialize(k));
            } else {
                Helpers.PROPERTIES.remove(PROPERTY_PREFIX + d.id);
            }
        }

        // Purge the legacy voice-key property now that it's migrated into the registry: otherwise
        // resetting VOICE_RECORD to factory from the Shortcuts tab would revert to the old key on
        // restart (load() would reapply it, seeing shortcut.VOICE-RECORD absent).
        Helpers.PROPERTIES.remove("voice_message_key");

        Helpers.savePropertiesFile();
        snapshot = null;
    }

    /**
     * Discards the edit: restores the opening state (live) without touching the
     * properties file.
     */
    public static synchronized void revert() {
        Map<String, KeyStroke> snap = snapshot;
        if (snap != null) {
            current = snap;
            reverse = buildReverse(snap);
            snapshot = null;
        }
    }

    /**
     * @return the ordered catalog of reassignable actions, for building the
     * Shortcuts tab
     */
    public static List<Def> defs() {
        return DEFS;
    }

    // --- Serialization format: "<keyCode>,<DOWN modifiers>" (layout-independent) ---
    private static String serialize(KeyStroke ks) {
        int mods = ks.getModifiers() & MOD_MASK;
        return ks.getKeyCode() + "," + mods;
    }

    private static KeyStroke deserialize(String s) {

        if (s == null || s.trim().isEmpty()) {
            return null;
        }

        try {
            String[] parts = s.trim().split(",");
            int key_code = Integer.parseInt(parts[0].trim());
            int mods = parts.length > 1 ? Integer.parseInt(parts[1].trim()) & MOD_MASK : 0;
            if (key_code == KeyEvent.VK_UNDEFINED) {
                return null;
            }
            return ks(key_code, mods);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Ignoring malformed shortcut override: " + s, ex);
            return null;
        }
    }

    /**
     * Builds the combination for a key-PRESSED event, keeping only the relevant
     * modifiers.
     *
     * @param e key event
     * @return the combination, or {@code null} if the key is a bare modifier
     * (ALT/CTRL/SHIFT/META/ALT_GR), which alone is not a shortcut
     */
    public static KeyStroke fromKeyEvent(KeyEvent e) {
        return fromKeyEvent(e, false);
    }

    /**
     * Same as {@link #fromKeyEvent(KeyEvent)} but drops modifiers when
     * {@code keycode_only} (for keycode-only actions, such as the voice note).
     *
     * @param e key event
     * @param keycode_only true to ignore modifiers
     * @return the combination, or {@code null} for a bare modifier key
     */
    public static KeyStroke fromKeyEvent(KeyEvent e, boolean keycode_only) {

        int key_code = e.getKeyCode();

        if (key_code == KeyEvent.VK_UNDEFINED || isModifierKey(key_code)) {
            return null;
        }

        return ks(key_code, keycode_only ? 0 : (e.getModifiersEx() & MOD_MASK));
    }

    private static boolean isModifierKey(int key_code) {
        return key_code == KeyEvent.VK_SHIFT || key_code == KeyEvent.VK_CONTROL
                || key_code == KeyEvent.VK_ALT || key_code == KeyEvent.VK_META
                || key_code == KeyEvent.VK_ALT_GRAPH;
    }

    /**
     * Human-readable parts of a combination for display (e.g. "CTRL + ALT +
     * ESC", "ALT + P"), in UPPERCASE and in CTRL, ALT, SHIFT, META, key order.
     *
     * @param ks combination, or {@code null}
     * @return the display parts (a single {@code "?"} element if {@code ks} is
     * null)
     */
    public static String[] keyCapStrings(KeyStroke ks) {

        if (ks == null) {
            return new String[]{"?"};
        }

        List<String> parts = new ArrayList<>();
        int mods = ks.getModifiers();

        if ((mods & (InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK)) != 0) {
            parts.add("CTRL");
        }
        if ((mods & (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK)) != 0) {
            parts.add("ALT");
        }
        if ((mods & (InputEvent.SHIFT_DOWN_MASK | InputEvent.SHIFT_MASK)) != 0) {
            parts.add("SHIFT");
        }
        if ((mods & (InputEvent.META_DOWN_MASK | InputEvent.META_MASK)) != 0) {
            parts.add("META");
        }
        if ((mods & (InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.ALT_GRAPH_MASK)) != 0) {
            parts.add("ALT GR");
        }

        parts.add(keyName(ks.getKeyCode()));

        return parts.toArray(new String[0]);
    }

    // Short name for a key's "physical key" label in the panel/help. Arrows and common keys get
    // abbreviated; everything else falls back to KeyEvent.getKeyText.
    private static String keyName(int key_code) {
        switch (key_code) {
            case KeyEvent.VK_UP:
                return "↑";
            case KeyEvent.VK_DOWN:
                return "↓";
            case KeyEvent.VK_LEFT:
                return "←";
            case KeyEvent.VK_RIGHT:
                return "→";
            case KeyEvent.VK_ENTER:
                return "ENTER";
            case KeyEvent.VK_SPACE:
                return "SPACE";
            case KeyEvent.VK_ESCAPE:
                return "ESC";
            case KeyEvent.VK_BACK_SPACE:
                return "BACK";
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_ADD:
                return "+";
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
                return "-";
            default:
                return KeyEvent.getKeyText(key_code).toUpperCase();
        }
    }
}
