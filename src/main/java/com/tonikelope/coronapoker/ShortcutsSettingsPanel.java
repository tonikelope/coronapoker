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

import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Pestaña "Atajos" del diálogo de Ajustes: lista las acciones reasignables (de
 * {@link KeyboardShortcuts}) agrupadas por sección, y por cada una un botón que muestra su
 * combinación actual. Al pulsarlo, el botón entra en modo captura ("Pulsa la combinación...") y la
 * siguiente combinación de teclas pasa a ser el nuevo atajo, salvo que ESC (cancela) o que esa
 * combinación ya esté en uso por otra acción (se ignora, como pidió el diseño).
 *
 * Los cambios se aplican EN VIVO sobre el registro (transacción abierta por el diálogo) y solo
 * persisten al GUARDAR; Cancelar los revierte. La captura pone {@link KeyboardShortcuts#setCapturing}
 * para que los dispatchers globales se aparten y la tecla no dispare el atajo que tuviera.
 *
 * @author tonikelope
 */
public class ShortcutsSettingsPanel extends JPanel {

    // Botón de captura por id de acción, para refrescar su texto tras un cambio o un restaurar.
    private final Map<String, JButton> buttons = new HashMap<>();

    // Captura en curso (solo una a la vez). Todo se toca en el EDT.
    private KeyEventDispatcher capture_dispatcher = null;
    private String capturing_id = null;

    public ShortcutsSettingsPanel() {
        super(new GridBagLayout());
        buildUI();
    }

    private void buildUI() {

        GridBagConstraints gbc = new GridBagConstraints();
        int row = 0;

        JLabel hint = new JLabel("<html>" + Translator.translate("shortcuts.pista_editar") + "</html>");
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 12, 12, 12);
        add(hint, gbc);

        String current_section = null;
        boolean first_section = true;

        for (KeyboardShortcuts.Def d : KeyboardShortcuts.defs()) {

            if (!d.section_key.equals(current_section)) {
                current_section = d.section_key;

                JLabel section = new JLabel(Translator.translate(d.section_key));
                section.setFont(section.getFont().deriveFont(Font.BOLD));
                gbc.gridx = 0;
                gbc.gridy = row++;
                gbc.gridwidth = 2;
                gbc.weightx = 1;
                gbc.anchor = GridBagConstraints.WEST;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = new Insets(first_section ? 4 : 18, 12, 4, 12);
                add(section, gbc);
                first_section = false;
            }

            JLabel action = new JLabel(Translator.translate(d.label_key));
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 1;
            gbc.weightx = 1;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(3, 26, 3, 16);
            add(action, gbc);

            final String id = d.id;
            JButton button = new JButton(keyText(id));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> startCapture(id));
            buttons.put(id, button);

            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.EAST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(3, 0, 3, 12);
            add(button, gbc);

            row++;
        }
    }

    // Texto de la combinación actual de una acción ("ALT + P", "CTRL + ALT + ESC").
    private static String keyText(String id) {
        return String.join(" + ", KeyboardShortcuts.keyCapStrings(KeyboardShortcuts.get(id)));
    }

    private void refreshButton(String id) {
        JButton b = buttons.get(id);
        if (b != null) {
            b.setText(keyText(id));
        }
    }

    // Arranca la captura de una acción: aparta los dispatchers globales y espera la próxima
    // combinación. Si ya había una captura en curso (otro botón), la cancela antes.
    private void startCapture(final String id) {

        if (capture_dispatcher != null) {
            stopCapture();
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

            // ESC a secas cancela (deja el atajo como estaba).
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE && (e.getModifiersEx() & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) == 0) {
                cancelToBinding();
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

    // Quita el dispatcher de captura y reactiva los atajos globales. Idempotente.
    private void stopCapture() {
        if (capture_dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(capture_dispatcher);
            capture_dispatcher = null;
        }
        KeyboardShortcuts.setCapturing(false);
        capturing_id = null;
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
