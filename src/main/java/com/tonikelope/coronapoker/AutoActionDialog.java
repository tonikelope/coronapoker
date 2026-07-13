/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Veto del MODO AUTO: antes de ejecutar una acción automática (el pre-pulsado de
 * los botones AUTO) se muestra una cuenta atrás con un botón rojo de cancelar,
 * NO modal — el jugador puede seguir usando el tablero / menú (clic, clic derecho)
 * mientras corre. La resolución se entrega por callback en el EDT: al expirar la
 * barra se ejecuta la acción; al cancelar (o si el turno se resuelve por otra vía
 * o se cae la partida) NO se ejecuta. keep_waiting permite abortar si el jugador
 * actúa a mano mientras corría la cuenta.
 *
 * @author tonikelope
 */
public class AutoActionDialog extends JDialog {

    private volatile boolean resolved = false;

    private final JProgressBar barra = new JProgressBar();

    private final Consumer<Boolean> on_resolve;

    // Resolución de un solo disparo. cancelled=true -> NO ejecutar (cancelar /
    // abortar); cancelled=false -> timeout -> ejecutar. Cierra el diálogo e
    // invoca el callback en el EDT.
    private synchronized void resolve(boolean cancelled) {
        if (resolved) {
            return;
        }
        resolved = true;
        Helpers.GUIRun(() -> {
            Helpers.resetBarra(barra, 0);
            dispose();
            if (on_resolve != null) {
                on_resolve.accept(cancelled);
            }
        });
    }

    // Cierra el veto desde fuera (p.ej. al ocultarse la mesa por salir de la
    // timba o por fin de partida): lo resuelve como CANCELADO —no ejecuta la
    // acción automática— y cierra la ventana. Idempotente (resolve es de un
    // solo disparo), así que es inofensivo aunque el diálogo ya se hubiese
    // resuelto por su cuenta atrás.
    public void cancel() {
        resolve(true);
    }

    public AutoActionDialog(Frame parent, Component center_over, Component width_ref, int seconds, String action_text, BooleanSupplier keep_waiting, Consumer<Boolean> on_resolve) {

        super(parent, false);

        this.on_resolve = on_resolve;

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(7, 20, 7, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("MODO AUTO");
        title.putClientProperty("i18n.key", "modo_auto.titulo");
        title.setFont(new Font("Dialog", Font.BOLD, 30));
        title.setForeground(new Color(255, 102, 0));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFocusable(false);
        panel.add(title, gbc);

        JLabel action = null;

        if (action_text != null && !action_text.isEmpty()) {
            gbc.gridy++;
            action = new JLabel(action_text);
            action.setFont(new Font("Dialog", Font.BOLD, 22));
            action.setForeground(Color.BLACK);
            action.setHorizontalAlignment(SwingConstants.CENTER);
            action.setFocusable(false);
            panel.add(action, gbc);
        }

        gbc.gridy++;
        barra.setPreferredSize(new Dimension(275, 26));
        panel.add(barra, gbc);

        gbc.gridy++;
        JButton cancel = new JButton(Translator.translate("ui.cancelar_2"));
        cancel.putClientProperty("i18n.key", "ui.cancelar_2");
        cancel.setBackground(new Color(200, 0, 0));
        cancel.setForeground(Color.WHITE);
        cancel.setFont(new Font("Dialog", Font.BOLD, 16));
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.setFocusable(false);
        cancel.addActionListener((java.awt.event.ActionEvent e) -> resolve(true));
        panel.add(cancel, gbc);

        setContentPane(panel);

        // No-modal: que no robe el foco del teclado al tablero. El botón Cancelar
        // sigue respondiendo al ratón aunque la ventana no sea focusable.
        setFocusableWindowState(false);

        // Siempre por encima: el veto de MODO AUTO es un diálogo ACCIONABLE (cuenta atrás +
        // botón Cancelar) y debe quedar visible/clicable aunque el REGISTRO (u otra ventana
        // hermana no-modal del mismo GameFrame) esté abierto. Sin esto el z-order dependía del
        // ORDEN de apertura (gana la última mostrada): si el registro se abría después tapaba el
        // modo auto, y al revés el modo auto tapaba el registro. alwaysOnTop lo fija por encima
        // de forma consistente, sin depender del orden.
        setAlwaysOnTop(true);

        Helpers.applyDialogZoom(this);
        Helpers.translateComponents(this, false);

        // A la altura del jugador local: misma columna izquierda y anchura que su
        // botonera de acción (width_ref), centrado verticalmente respecto a su
        // asiento (center_over). Los rótulos se reajustan al ancho de la botonera
        // ANTES de empaquetar para que la altura salga correcta y no quede holgura
        // vertical. Si la botonera no se está mostrando, cae al centro del owner
        // con el tamaño natural.
        boolean anchored = center_over != null && center_over.isShowing() && width_ref != null && width_ref.isShowing();

        if (anchored) {
            // Ancho útil = botonera − borde (10 px a cada lado) − insets (20 px a
            // cada lado). fitFontToWidth solo encoge la fuente si el texto no cabe.
            int avail = width_ref.getWidth() - 2 * 10 - 2 * 20;
            title.setFont(Helpers.fitFontToWidth(title, title.getText(), title.getFont(), avail, 14));
            if (action != null) {
                action.setFont(Helpers.fitFontToWidth(action, action.getText(), action.getFont(), avail, 12));
            }
        }

        pack();

        if (anchored) {
            setSize(width_ref.getWidth(), getHeight());
            Point player_anchor = center_over.getLocationOnScreen();
            Point ref_anchor = width_ref.getLocationOnScreen();
            setLocation(ref_anchor.x, player_anchor.y + (center_over.getHeight() - getHeight()) / 2);
        } else {
            setLocationRelativeTo(parent);
        }

        // Cuenta atrás en background. Resuelve por callback: timeout -> ejecutar;
        // fin de partida o keep_waiting falso (el jugador actuó a mano) -> abortar.
        Helpers.threadRun(() -> {

            Helpers.GUIRun(() -> Helpers.smoothCountdown(barra, seconds));

            int t = seconds;

            while (t > 0 && !resolved) {

                Helpers.pausar(1000);

                if (resolved) {
                    return;
                }

                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()
                        || (keep_waiting != null && !keep_waiting.getAsBoolean())) {
                    resolve(true);
                    return;
                }

                if (!GameFrame.getInstance().isTimba_pausada()) {
                    --t;
                }
            }

            if (!resolved) {
                resolve(false);
            }
        });
    }
}
