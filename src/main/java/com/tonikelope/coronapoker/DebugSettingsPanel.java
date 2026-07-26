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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Pestaña "Debug" del diálogo de Ajustes: consola de solo lectura con el mismo volcado de
 * logs (java.util.logging, vía {@link DebugLog}) que antes vivía en el diálogo de Registro.
 *
 * Se suscribe a {@link DebugLog} al construirse y libera la suscripción en {@link #cleanup()}
 * (lo llama SettingsDialog al cerrarse, para no retener este panel desechado a través del
 * listener estático de DebugLog). Reutiliza el aspecto de consola ({@code LOG_BG}/{@code LOG_FONT})
 * y el autoscroll pegajoso ({@code BottomFollower}) del registro (GameLogDialog).
 *
 * @author tonikelope
 */
public class DebugSettingsPanel extends JPanel {

    private final JTextArea debug_textarea;
    private final GameLogDialog.BottomFollower follow;
    private final Consumer<String> listener;

    public DebugSettingsPanel() {
        super(new BorderLayout());

        debug_textarea = new JTextArea();
        debug_textarea.setEditable(false);
        debug_textarea.setBackground(GameLogDialog.LOG_BG);
        debug_textarea.setForeground(new Color(220, 220, 220));
        debug_textarea.setFont(GameLogDialog.LOG_FONT.deriveFont(GameLogDialog.LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM));
        debug_textarea.setLineWrap(true);
        debug_textarea.setWrapStyleWord(false);
        Helpers.JTextFieldRegularPopupMenu.addTo(debug_textarea);

        JScrollPane debug_scroll = new JScrollPane(debug_textarea);
        debug_scroll.setBorder(BorderFactory.createEmptyBorder());
        debug_scroll.getVerticalScrollBar().setUnitIncrement(16);
        // Tamaño de referencia acotado: el diálogo de Ajustes se empaqueta al contenido, y
        // un JTextArea con muchas líneas reportaría un preferido enorme que dispararía el
        // alto del diálogo. Con un preferido fijo modesto el contenido scrollea dentro y el
        // resto de pestañas manda en el tamaño final.
        debug_scroll.setPreferredSize(new Dimension(Math.round(620 * Helpers.DIALOG_ZOOM), Math.round(380 * Helpers.DIALOG_ZOOM)));
        add(debug_scroll, BorderLayout.CENTER);

        follow = new GameLogDialog.BottomFollower(debug_scroll, debug_textarea);

        debug_textarea.setText(DebugLog.snapshot());
        debug_textarea.setCaretPosition(debug_textarea.getDocument().getLength());

        listener = (String record) -> Helpers.GUIRun(() -> {
            try {
                debug_textarea.append(record);
                follow.followIfNeeded();
            } catch (Throwable t) {
                // El textarea puede estar en transición al cerrarse el diálogo — ignorar.
            }
        });
        DebugLog.subscribe(listener);
    }

    // Repone la fuente monoespaciada de consola: SettingsDialog aplica setUniformFont a todo
    // el diálogo (que la pisaría con la GUI_FONT). Se llama DESPUÉS de esa pasada.
    public void reapplyConsoleFont() {
        debug_textarea.setFont(GameLogDialog.LOG_FONT.deriveFont(GameLogDialog.LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM));
    }

    // Salta al fondo (al abrir el diálogo: se quiere ver lo más reciente).
    public void snapToBottom() {
        follow.snapToBottom();
    }

    // Libera la suscripción a DebugLog al cerrar el diálogo. Idempotente.
    public void cleanup() {
        DebugLog.unsubscribe(listener);
    }
}
