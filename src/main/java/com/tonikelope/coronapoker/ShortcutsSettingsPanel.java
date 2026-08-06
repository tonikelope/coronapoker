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

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Pestaña "Atajos" del diálogo de Ajustes: lista las acciones reasignables (de
 * {@link KeyboardShortcuts}) agrupadas por sección, cada sección en su propia caja con borde negro
 * fino, y las cajas repartidas en DOS columnas (equilibradas por número de filas) para no ser un
 * único bloque vertical larguísimo. Cada acción lleva un botón con su combinación actual; al
 * pulsarlo, el botón entra en modo captura ("Pulsa la combinación...") y la siguiente combinación de
 * teclas pasa a ser el nuevo atajo, salvo que ya esté en uso por otra acción (se ignora). Para
 * CANCELAR una captura basta con hacer clic fuera; no se usa ninguna tecla, para que cualquier
 * tecla —incluida ESC— pueda asignarse.
 *
 * Los cambios se aplican EN VIVO sobre el registro (transacción abierta por el diálogo) y solo
 * persisten al GUARDAR; Cancelar los revierte. La captura pone {@link KeyboardShortcuts#setCapturing}
 * para que los dispatchers globales se aparten y la tecla no dispare el atajo que tuviera.
 *
 * Los botones muestran la combinación con la fuente "Dialog" (ver {@link #applyKeyFont()}): la fuente
 * de la interfaz (McLaren) no trae los glifos de las flechas (↑ ↓ ← →) y saldrían en blanco.
 *
 * @author tonikelope
 */
public class ShortcutsSettingsPanel extends JPanel {

    // Separación MÍNIMA entre cajas de una columna (crece elásticamente para alinear los fondos).
    private static final int BOX_GAP = 10;

    // Botón de captura por id de acción, para refrescar su texto tras un cambio o un restaurar.
    private final Map<String, JButton> buttons = new HashMap<>();

    // Botón "deshacer" por acción (deja ESE atajo en su valor de fábrica). Se habilita solo si la
    // acción está personalizada.
    private final Map<String, JButton> reset_buttons = new HashMap<>();

    // Bordes de las cajas de sección, para poner sus títulos en negrita TRAS la unificación de
    // fuentes del diálogo (fixTitledBorderFonts los pisaría a plano antes).
    private final List<javax.swing.border.TitledBorder> section_borders = new ArrayList<>();

    // Captura en curso (solo una a la vez). Todo se toca en el EDT.
    private KeyEventDispatcher capture_dispatcher = null;
    private AWTEventListener mouse_cancel_listener = null;
    private String capturing_id = null;

    public ShortcutsSettingsPanel() {
        super(new GridBagLayout());
        buildUI();
    }

    private void buildUI() {

        GridBagConstraints gbc = new GridBagConstraints();

        JLabel hint = new JLabel("<html>" + Translator.translate("shortcuts.pista_editar") + "</html>");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 12, 12, 12);
        add(hint, gbc);

        // Agrupa las acciones por sección conservando el orden del catálogo.
        LinkedHashMap<String, List<KeyboardShortcuts.Def>> by_section = new LinkedHashMap<>();
        for (KeyboardShortcuts.Def d : KeyboardShortcuts.defs()) {
            by_section.computeIfAbsent(d.section_key, k -> new ArrayList<>()).add(d);
        }

        // Reparte las cajas de sección en dos columnas, metiendo cada sección en la columna que
        // MENOS filas lleve acumuladas, para que las dos columnas queden parejas de alto.
        List<JPanel> left = new ArrayList<>();
        List<JPanel> right = new ArrayList<>();
        int left_rows = 0;
        int right_rows = 0;

        for (Map.Entry<String, List<KeyboardShortcuts.Def>> e : by_section.entrySet()) {
            // Peso ~ alto de la caja: filas + un extra por el título y los bordes, para que las dos
            // columnas queden parejas de alto (no solo de número de filas) y sus fondos casen abajo.
            int weight = e.getValue().size() + 2;
            if (left_rows <= right_rows) {
                left.add(sectionBox(e.getKey(), e.getValue()));
                left_rows += weight;
            } else {
                right.add(sectionBox(e.getKey(), e.getValue()));
                right_rows += weight;
            }
        }

        // fill BOTH + weighty: las dos columnas se estiran a la misma altura (la de la más larga),
        // para que la columna corta reparta su hueco sobrante entre sus cajas y ambas casen abajo.
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.insets = new Insets(0, 10, 8, 5);
        add(column(left), gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 5, 8, 10);
        add(column(right), gbc);
    }

    // Caja de una sección: borde negro fino con el nombre de la sección como título, y dentro las
    // filas (etiqueta + botón alineado + relleno elástico).
    private JPanel sectionBox(String section_key, List<KeyboardShortcuts.Def> defs) {

        JPanel box = new JPanel(new GridBagLayout());
        // Borde por defecto (esquinas redondeadas, igual que las cajas de las otras pestañas). El
        // título se pone en negrita en applyKeyFont (tras la unificación de fuentes del diálogo).
        javax.swing.border.TitledBorder border = BorderFactory.createTitledBorder(Translator.translate(section_key));
        section_borders.add(border);
        box.setBorder(border);

        GridBagConstraints gbc = new GridBagConstraints();
        int row = 0;

        for (KeyboardShortcuts.Def d : defs) {

            JLabel action = new JLabel(Translator.translate(d.label_key));
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(3, 10, 3, 14);
            box.add(action, gbc);

            final String id = d.id;
            JButton button = new JButton(keyText(id));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            // Cajetines más anchos (margen horizontal generoso) para que la combinación respire.
            button.setMargin(new Insets(3, 24, 3, 24));
            button.addActionListener(e -> startCapture(id));
            buttons.put(id, button);

            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(3, 0, 3, 6);
            box.add(button, gbc);

            // Botón pequeño para devolver ESTE atajo a su valor de fábrica. Se habilita solo si la
            // acción está personalizada (ver refreshButton).
            JButton reset = new JButton(new ImageIcon(getClass().getResource("/images/menu/undo.png")));
            reset.setCursor(new Cursor(Cursor.HAND_CURSOR));
            reset.setToolTipText(Translator.translate("shortcuts.restaurar_este"));
            reset.setMargin(new Insets(2, 4, 2, 4));
            reset.setFocusable(false);
            reset.addActionListener(e -> {
                KeyboardShortcuts.reset(id);
                refreshButton(id);
            });
            reset_buttons.put(id, reset);

            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(3, 0, 3, 8);
            box.add(reset, gbc);

            gbc.gridx = 3;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, 0, 0);
            box.add(Box.createHorizontalGlue(), gbc);

            row++;
        }

        return box;
    }

    // Apila las cajas de una columna con separadores ELÁSTICOS entre ellas: al menos BOX_GAP px, y
    // crecen para repartir el hueco sobrante. Como las dos columnas se estiran a la misma altura (la
    // de la más larga, ver buildUI con fill BOTH), la columna corta separa sus cajas hasta que su
    // última caja queda alineada por abajo con la de la otra columna. No hay muelle tras la última
    // caja: así esa caja se pega al fondo.
    private static JPanel column(List<JPanel> boxes) {

        JPanel col = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;

        int row = 0;
        for (int i = 0; i < boxes.size(); i++) {

            if (i > 0) {
                gbc.gridy = row++;
                gbc.weighty = 1;
                gbc.fill = GridBagConstraints.VERTICAL;
                col.add(new Box.Filler(new Dimension(0, BOX_GAP), new Dimension(0, BOX_GAP),
                        new Dimension(0, Short.MAX_VALUE)), gbc);
            }

            gbc.gridy = row++;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTH;
            col.add(boxes.get(i), gbc);
        }

        return col;
    }

    // Texto de la combinación actual de una acción ("ALT + P", "CTRL + ALT + ESC").
    private static String keyText(String id) {
        return String.join(" + ", KeyboardShortcuts.keyCapStrings(KeyboardShortcuts.get(id)));
    }

    private void refreshButton(String id) {

        boolean customized = KeyboardShortcuts.isCustomized(id);

        JButton b = buttons.get(id);
        if (b != null) {
            b.setText(keyText(id));
            // Fuente "Dialog" siempre (la de la interfaz no trae los glifos de flecha); en NEGRITA
            // si el usuario ha personalizado la combinación (difiere de la de fábrica).
            b.setFont(new Font("Dialog", customized ? Font.BOLD : Font.PLAIN, b.getFont().getSize()));
        }

        // El botón "deshacer" SOLO aparece si el atajo está personalizado.
        JButton reset = reset_buttons.get(id);
        if (reset != null) {
            reset.setVisible(customized);
        }

        // Al aparecer/desaparecer el botón deshacer o cambiar el ancho del texto, recolocar.
        revalidate();
        repaint();
    }

    /**
     * Repone las fuentes de la pestaña TRAS la unificación del diálogo: los botones de combinación en
     * "Dialog" (la de la interfaz no trae los glifos de flecha ↑↓←→) con negrita si están
     * personalizados, y los títulos de las cajas de sección en negrita.
     */
    public void applyKeyFont() {

        for (String id : buttons.keySet()) {
            refreshButton(id);
        }

        for (javax.swing.border.TitledBorder border : section_borders) {
            java.awt.Font f = border.getTitleFont();
            if (f != null) {
                border.setTitleFont(f.deriveFont(Font.BOLD));
            }
        }

        repaint();
    }

    // Arranca la captura de una acción: aparta los dispatchers globales y espera la próxima
    // combinación. Si ya había una captura en curso (otro botón), la cancela antes.
    private void startCapture(final String id) {

        // Si ya había una captura en curso en OTRO botón, la cancela y le devuelve su texto (si no,
        // ese botón se quedaría con "Pulsa la combinación..." pegado).
        if (capture_dispatcher != null) {
            String prev = capturing_id;
            stopCapture();
            if (prev != null) {
                refreshButton(prev);
            }
        }

        capturing_id = id;

        JButton button = buttons.get(id);
        if (button != null) {
            button.setText(Translator.translate("shortcuts.pulsa_combinacion"));
        }

        KeyboardShortcuts.setCapturing(true);

        capture_dispatcher = (KeyEvent e) -> {

            if (e.getID() != KeyEvent.KEY_PRESSED) {
                // Nos comemos también el release/typed de las teclas de la captura para que no se
                // filtren a nadie mientras dura.
                return true;
            }

            KeyStroke ks = KeyboardShortcuts.fromKeyEvent(e);

            if (ks == null) {
                // Modificador suelto: seguir esperando la tecla de verdad.
                return true;
            }

            if (KeyboardShortcuts.isAssignable(ks, id)) {
                KeyboardShortcuts.set(id, ks);
                cancelToBinding();
            } else {
                // Ya la usa otra acción: se ignora, con un aviso breve en el propio botón.
                flashAlreadyAssigned(id);
            }

            return true;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(capture_dispatcher);

        // Cancelar = clic fuera (o en cualquier sitio). No se usa ninguna tecla para cancelar, para
        // que cualquiera —incluida ESC— pueda asignarse. El clic que inició la captura ya pasó
        // (actionPerformed salta al soltar), así que el listener solo verá el SIGUIENTE clic.
        mouse_cancel_listener = (AWTEvent ev) -> {
            if (ev.getID() == MouseEvent.MOUSE_PRESSED) {
                cancelToBinding();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(mouse_cancel_listener, AWTEvent.MOUSE_EVENT_MASK);
    }

    // Cierra la captura y deja el botón mostrando la combinación ACTUAL de la acción (la nueva si se
    // asignó, o la de antes si se canceló).
    private void cancelToBinding() {
        String id = capturing_id;
        stopCapture();
        if (id != null) {
            refreshButton(id);
        }
    }

    // Aviso breve "Ya asignado" en el botón y vuelta a su combinación.
    private void flashAlreadyAssigned(final String id) {
        stopCapture();
        JButton b = buttons.get(id);
        if (b != null) {
            b.setText(Translator.translate("shortcuts.ya_asignado"));
            javax.swing.Timer t = new javax.swing.Timer(900, e -> refreshButton(id));
            t.setRepeats(false);
            t.start();
        }
    }

    // Quita el dispatcher de captura y el listener de ratón, y reactiva los atajos globales.
    // Idempotente.
    private void stopCapture() {
        if (capture_dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(capture_dispatcher);
            capture_dispatcher = null;
        }
        if (mouse_cancel_listener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouse_cancel_listener);
            mouse_cancel_listener = null;
        }
        KeyboardShortcuts.setCapturing(false);
        capturing_id = null;
    }

    /**
     * Cancela una captura en curso (si la hay) devolviendo al botón su combinación actual. Lo llama
     * el diálogo al cambiar de pestaña.
     */
    public void cancelCapture() {
        if (capture_dispatcher != null) {
            cancelToBinding();
        }
    }

    /**
     * Restaura TODOS los atajos a sus valores de fábrica (en vivo; persiste al GUARDAR) y refresca
     * los botones. Lo invoca el pie "Restaurar predeterminados" de la pestaña.
     */
    public void restoreDefaults() {
        stopCapture();
        KeyboardShortcuts.resetAll();
        for (String id : buttons.keySet()) {
            refreshButton(id);
        }
    }

    /**
     * Cierra cualquier captura pendiente (al cerrarse el diálogo). No revierte los cambios: de eso se
     * encarga la transacción del registro (commit al GUARDAR, revert al Cancelar).
     */
    public void cleanup() {
        stopCapture();
    }
}
