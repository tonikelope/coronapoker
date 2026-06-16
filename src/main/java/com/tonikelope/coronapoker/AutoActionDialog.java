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
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Ventana de confirmación del MODO AUTO: antes de ejecutar una acción
 * automática (el pre-pulsado de los botones AUTO) se muestra una cuenta atrás
 * modal con un botón rojo de cancelar. Si expira la barra, la acción se ejecuta;
 * si se cancela, NO se ejecuta esta mano (el jugador recupera el control manual)
 * — la pre-pulsación se mantiene o no según "Persistir AUTO entre manos", de eso
 * se encarga quien invoca este diálogo.
 *
 * @author tonikelope
 */
public class AutoActionDialog extends JDialog {

    private volatile boolean cancelled = false;

    private final JProgressBar barra = new JProgressBar();

    public boolean isCancelled() {
        return cancelled;
    }

    public AutoActionDialog(Frame parent, Component center_over, int seconds, String action_text) {

        super(parent, true);

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(8, 24, 8, 24);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("MODO AUTO");
        title.putClientProperty("i18n.key", "modo_auto.titulo");
        title.setFont(new Font("Dialog", Font.BOLD, 36));
        title.setForeground(new Color(255, 102, 0));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFocusable(false);
        panel.add(title, gbc);

        if (action_text != null && !action_text.isEmpty()) {
            gbc.gridy++;
            JLabel action = new JLabel(action_text);
            action.setFont(new Font("Dialog", Font.BOLD, 22));
            action.setForeground(Color.BLACK);
            action.setHorizontalAlignment(SwingConstants.CENTER);
            action.setFocusable(false);
            panel.add(action, gbc);
        }

        gbc.gridy++;
        barra.setPreferredSize(new Dimension(320, 30));
        panel.add(barra, gbc);

        gbc.gridy++;
        JButton cancel = new JButton(Translator.translate("ui.cancelar_2"));
        cancel.putClientProperty("i18n.key", "ui.cancelar_2");
        cancel.setBackground(new Color(200, 0, 0));
        cancel.setForeground(Color.WHITE);
        cancel.setFont(new Font("Dialog", Font.BOLD, 18));
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.setFocusable(false);
        cancel.addActionListener((java.awt.event.ActionEvent e) -> {
            cancelled = true;
            dispose();
        });
        panel.add(cancel, gbc);

        setContentPane(panel);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowActivated(java.awt.event.WindowEvent evt) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(AutoActionDialog.this);
                }
            }

            @Override
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                if (isModal()) {
                    try {
                        Init.CURRENT_MODAL_DIALOG.removeLast();
                    } catch (Exception ex) {
                    }
                }
            }
        });

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        pack();
        // Centrado sobre el panel del jugador local (su asiento), no sobre todo el
        // frame; si por lo que sea no está mostrándose, cae al centro del owner.
        setLocationRelativeTo(center_over != null && center_over.isShowing() ? center_over : parent);

        // Cuenta atrás en background: decrementa solo si la timba no está pausada
        // ni se está cerrando; al expirar (o si la partida termina) cierra el
        // diálogo. El cierre por timeout deja cancelled=false -> la acción se
        // ejecuta; Cancelar lo pone a true. Si la partida se cae, lo tratamos
        // como cancelar (no auto-actuar sobre una mano que ya no sigue).
        Helpers.threadRun(() -> {

            Helpers.GUIRun(() -> Helpers.smoothCountdown(barra, seconds));

            int t = seconds;

            while (t > 0 && !cancelled) {

                Helpers.pausar(1000);

                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    cancelled = true;
                    break;
                }

                if (!GameFrame.getInstance().isTimba_pausada() && !cancelled) {
                    --t;
                }
            }

            Helpers.GUIRun(() -> {
                Helpers.resetBarra(barra, 0);
                dispose();
            });
        });
    }
}
