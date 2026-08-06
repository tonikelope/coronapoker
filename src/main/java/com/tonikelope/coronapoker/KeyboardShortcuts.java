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
 * Registro central de los atajos de teclado personalizables del juego. Cada acción tiene un id
 * ESTABLE (el mismo nombre que su AbstractAction en los dispatchers de {@link GameFrame} y
 * {@link Init}) y una combinación de teclas por defecto; el usuario puede reasignarla desde la
 * pestaña "Atajos" de Ajustes y el override persiste en {@code coronapoker.properties} bajo la
 * clave {@code shortcut.<id>}.
 *
 * Los dos dispatchers globales resuelven por id: piden {@link #idFor(KeyStroke)} y ejecutan el
 * cuerpo (fijo) que tengan registrado para ese id, así reasignar surte efecto EN VIVO sin
 * reconstruir nada. El mapa inverso (KeyStroke -> id) se publica de forma atómica (referencia
 * volatile a un mapa nuevo) para que las lecturas desde el hilo de teclado vean siempre una foto
 * coherente.
 *
 * Solo se modelan atajos basados en keyCode (con o sin modificadores). Quedan fuera, por ser de
 * otra naturaleza o frágiles entre distribuciones de teclado: la rueda de zoom (ratón), la tecla
 * de nota de voz (configurable aparte, en Audio), el chat rápido 'º' (dead-key "typed") y las
 * teclas internas de los diálogos de chat e imágenes.
 *
 * @author tonikelope
 */
public final class KeyboardShortcuts {

    private static final Logger LOG = Logger.getLogger(KeyboardShortcuts.class.getName());

    private static final String PROPERTY_PREFIX = "shortcut.";

    // Solo estos modificadores cuentan para un atajo (se descartan máscaras de botón u otras).
    private static final int MOD_MASK = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    // Ids ESTABLES (== nombre del AbstractAction en los dispatchers). No cambiar: son clave de
    // persistencia y de resolución en los dispatchers.
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

    public static final String FASTCHAT_IMAGE = "FASTCHAT-IMAGE";

    /**
     * Definición de una acción reasignable: id, claves i18n de sección/acción (reutilizan las de
     * {@link ShortcutsDialog}: {@code shortcuts.sec_*} / {@code shortcuts.act_*}), combinación por
     * defecto y alias FIJOS: teclas extra siempre activas y NO reasignables (p. ej. las flechas
     * horizontales de la apuesta), que se registran en el mapa inverso para dispararse y para
     * reservar la tecla (nadie más puede asignársela).
     */
    public static final class Def {

        public final String id;
        public final String section_key;
        public final String label_key;
        public final KeyStroke def;
        public final KeyStroke[] aliases;

        Def(String id, String section_key, String label_key, KeyStroke def, KeyStroke... aliases) {
            this.id = id;
            this.section_key = section_key;
            this.label_key = label_key;
            this.def = def;
            this.aliases = aliases;
        }
    }

    private static KeyStroke ks(int key_code, int modifiers) {
        return KeyStroke.getKeyStroke(key_code, modifiers);
    }

    // Catálogo ORDENADO (por secciones, como la ayuda de atajos). El orden manda en la pestaña.
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
        // Subir/bajar apuesta: primaria ARRIBA/ABAJO editable; DERECHA/IZQUIERDA quedan como alias
        // FIJOS (no se pierden y no se pueden reasignar), igual que hoy hacen las cuatro flechas.
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

        DEFS.add(new Def(FASTCHAT_IMAGE, "shortcuts.sec_img", "shortcuts.act_images", ks(KeyEvent.VK_1, 0)));
    }

    private static final Map<String, Def> BY_ID = new HashMap<>();

    static {
        for (Def d : DEFS) {
            BY_ID.put(d.id, d);
        }
    }

    // id -> combinación actual (default salvo override). Foto inmutable publicada de golpe.
    private static volatile Map<String, KeyStroke> current = new HashMap<>();
    // combinación -> id (incluye alias fijos). Foto inmutable publicada de golpe: la leen los
    // dispatchers desde el hilo de teclado.
    private static volatile Map<KeyStroke, String> reverse = new HashMap<>();

    static {
        load();
    }

    private KeyboardShortcuts() {
    }

    // Carga inicial: para cada acción, override de properties si es válido; si no, el default.
    private static synchronized void load() {

        Map<String, KeyStroke> cur = new HashMap<>();

        for (Def d : DEFS) {
            KeyStroke override = deserialize(Helpers.PROPERTIES.getProperty(PROPERTY_PREFIX + d.id));
            cur.put(d.id, override != null ? override : d.def);
        }

        current = cur;
        reverse = buildReverse(cur);
    }

    // Reconstruye el mapa inverso a partir de las combinaciones actuales + los alias fijos. Si dos
    // acciones colisionan (no debería pasar por validación en la asignación), gana la primera del
    // catálogo (orden de DEFS) y se avisa por log.
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
     * Combinación actual de una acción (default salvo override), o null si el id no existe.
     */
    public static KeyStroke get(String id) {
        return current.get(id);
    }

    /**
     * keyCode de la combinación actual de una acción, o {@link KeyEvent#VK_UNDEFINED} si no aplica.
     * Para los dispatchers que comparan por keyCode (guardas de ESC/ESPACIO, beep de volumen).
     */
    public static int keyCode(String id) {
        KeyStroke k = current.get(id);
        return k != null ? k.getKeyCode() : KeyEvent.VK_UNDEFINED;
    }

    /**
     * Id de la acción asignada a esa combinación (incluye alias fijos), o null si ninguna. Lo usan
     * los dispatchers para resolver y la asignación para detectar conflictos.
     */
    public static String idFor(KeyStroke ks) {
        return ks != null ? reverse.get(ks) : null;
    }

    /**
     * ¿Se puede asignar esa combinación a {@code target_id}? False si ya la usa OTRA acción (o un
     * alias fijo). True si está libre o es la que ya tiene la propia acción.
     */
    public static boolean isAssignable(KeyStroke ks, String target_id) {
        if (ks == null) {
            return false;
        }
        String owner = reverse.get(ks);
        return owner == null || owner.equals(target_id);
    }

    /**
     * Reasigna una acción a una combinación nueva EN VIVO (los dispatchers la ven al instante). No
     * persiste: la edición es transaccional y solo se escribe a disco en {@link #commit()}. No
     * valida conflictos: el llamador debe haber comprobado {@link #isAssignable} antes.
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
     * Devuelve una acción a su combinación de fábrica EN VIVO (no persiste; ver {@link #commit()}).
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
     * Restaura TODAS las acciones a sus combinaciones de fábrica EN VIVO (no persiste hasta commit).
     */
    public static synchronized void resetAll() {

        Map<String, KeyStroke> cur = new HashMap<>();

        for (Def d : DEFS) {
            cur.put(d.id, d.def);
        }

        current = cur;
        reverse = buildReverse(cur);
    }

    // Mientras la pestaña de Atajos está capturando una tecla, los dispatchers globales se apartan
    // (devuelven false a la primera) para que la combinación pulsada llegue al capturador y no
    // dispare el atajo que tuviera asignado.
    private static volatile boolean capturing = false;

    public static boolean isCapturing() {
        return capturing;
    }

    public static void setCapturing(boolean c) {
        capturing = c;
    }

    // --- Edición transaccional (coherente con el diálogo de Ajustes: aplica en vivo, GUARDAR
    // persiste, Cancelar revierte). ---
    private static volatile Map<String, KeyStroke> snapshot = null;

    /**
     * Abre una edición transaccional guardando una foto del estado actual. Los cambios se aplican en
     * vivo pero no se persisten hasta {@link #commit()}; {@link #revert()} restaura esta foto.
     */
    public static synchronized void beginTransaction() {
        snapshot = new HashMap<>(current);
    }

    /**
     * ¿Hay cambios sin confirmar respecto a la foto de apertura?
     */
    public static boolean isDirty() {
        Map<String, KeyStroke> snap = snapshot;
        return snap != null && !snap.equals(current);
    }

    /**
     * Confirma la edición: persiste el estado actual (override por acción que difiera de su default,
     * borrando la clave de las que estén de fábrica) y cierra la transacción.
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

        Helpers.savePropertiesFile();
        snapshot = null;
    }

    /**
     * Descarta la edición: restaura el estado de apertura (en vivo) sin tocar el fichero.
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
     * Catálogo ordenado de acciones reasignables (para construir la pestaña de Atajos).
     */
    public static List<Def> defs() {
        return DEFS;
    }

    // --- Serialización: "<keyCode>,<modificadores DOWN>" (independiente de distribución) ---
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
     * Construye la combinación de un evento de tecla PULSADA, quedándose solo con los modificadores
     * relevantes. Devuelve null si la tecla es un modificador suelto (ALT/CTRL/SHIFT/META/ALT_GR),
     * que por sí sola no es un atajo.
     */
    public static KeyStroke fromKeyEvent(KeyEvent e) {

        int key_code = e.getKeyCode();

        if (key_code == KeyEvent.VK_UNDEFINED || isModifierKey(key_code)) {
            return null;
        }

        return ks(key_code, e.getModifiersEx() & MOD_MASK);
    }

    private static boolean isModifierKey(int key_code) {
        return key_code == KeyEvent.VK_SHIFT || key_code == KeyEvent.VK_CONTROL
                || key_code == KeyEvent.VK_ALT || key_code == KeyEvent.VK_META
                || key_code == KeyEvent.VK_ALT_GRAPH;
    }

    /**
     * Texto legible de una combinación para mostrarla ("CTRL + ALT + ESC", "ALT + P"). Las partes
     * salen en MAYÚSCULAS y en el orden CTRL, ALT, SHIFT, META, tecla.
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

    // Nombre corto de una tecla para la "tecla física" del panel/ayuda. Las flechas y teclas
    // habituales se abrevian; el resto usa KeyEvent.getKeyText.
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
