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

    public AutoActionDialog(Frame parent, Component center_over, int seconds, String action_text, BooleanSupplier keep_waiting, Consumer<Boolean> on_resolve) {

        super(parent, false);

        this.on_resolve = on_resolve;

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 9));

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

        if (action_text != null && !action_text.isEmpty()) {
            gbc.gridy++;
            JLabel action = new JLabel(action_text);
            action.setFont(new Font("Dialog", Font.BOLD, 19));
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

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        pack();
        // Anclado a la esquina inferior derecha del panel del jugador local (su
        // asiento), con un pequeño margen interior para quedar DENTRO sin pisar su
        // borde; no centrado encima. Si por lo que sea no está mostrándose, cae al
        // centro del owner.
        if (center_over != null && center_over.isShowing()) {
            Point anchor = center_over.getLocationOnScreen();
            int margin = 16;
            int x = anchor.x + center_over.getWidth() - getWidth() - margin;
            int y = anchor.y + center_over.getHeight() - getHeight() - margin;
            setLocation(x, y);
        } else {
            setLocationRelativeTo(parent);
        }

        // Translúcido al 85%: deja entrever ligeramente la mesa detrás mientras
        // corre la cuenta atrás.
        setOpacity(0.85f);

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
