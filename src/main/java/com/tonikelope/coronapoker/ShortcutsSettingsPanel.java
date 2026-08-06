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
import java.util.HashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Pestaña "Atajos" del diálogo de Ajustes: lista las acciones reasignables (de
 * {@link KeyboardShortcuts}) agrupadas por sección, y por cada una un botón que muestra su
 * combinación actual. Al pulsarlo, el botón entra en modo captura ("Pulsa la combinación...") y la
 * siguiente combinación de teclas pasa a ser el nuevo atajo, salvo que esa combinación ya esté en
 * uso por otra acción (se ignora, como pidió el diseño). Para CANCELAR una captura basta con hacer
 * clic fuera (o en otro sitio); no se usa ninguna tecla, para que cualquier tecla —incluida ESC—
 * pueda asignarse.
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

    // Botón de captura por id de acción, para refrescar su texto tras un cambio o un restaurar.
    private final Map<String, JButton> buttons = new HashMap<>();

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
        int row = 0;

        JLabel hint = new JLabel("<html>" + Translator.translate("shortcuts.pista_editar") + "</html>");
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 3;
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
                gbc.gridwidth = 3;
                gbc.weightx = 1;
                gbc.anchor = GridBagConstraints.WEST;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = new Insets(first_section ? 4 : 18, 12, 4, 12);
                add(section, gbc);
                first_section = false;
            }

            // Fila en 3 columnas: etiqueta (izquierda) + botón (columna alineada, pegada a la
            // etiqueta) + relleno elástico que se traga el ancho sobrante. Así las dos columnas van
            // JUNTAS a la izquierda en vez de separarse de lado a lado (cuesta seguir la fila).
            JLabel action = new JLabel(Translator.translate(d.label_key));
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(3, 26, 3, 14);
            add(action, gbc);

            final String id = d.id;
            JButton button = new JButton(keyText(id));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> startCapture(id));
            buttons.put(id, button);

            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(3, 0, 3, 0);
            add(button, gbc);

            gbc.gridx = 2;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, 0, 0);
            add(Box.createHorizontalGlue(), gbc);

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

    /**
     * Pone los botones de combinación en fuente "Dialog" (conservando el tamaño ya unificado por el
     * diálogo). La fuente de la interfaz no trae los glifos de las flechas y dejaría en blanco los
     * atajos de subir/bajar apuesta. Lo llama el diálogo TRAS unificar fuentes.
     */
    public void applyKeyFont() {
        for (JButton b : buttons.values()) {
            b.setFont(new Font("Dialog", Font.PLAIN, b.getFont().getSize()));
        }
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
