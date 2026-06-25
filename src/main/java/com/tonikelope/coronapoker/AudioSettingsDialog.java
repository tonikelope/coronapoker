/*
 * Copyright (C) 2026 tonikelope
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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.WindowConstants;

/**
 * Diálogo independiente de ajustes de audio: acceso rápido desde el icono del
 * altavoz (incluido el lobby y la sala de espera, donde no hay partida y por tanto
 * no aplica el diálogo unificado de ajustes). Es un envoltorio fino de
 * {@link AudioSettingsPanel} (que contiene toda la UI y la lógica, reutilizada
 * también por la pestaña "Audio" del diálogo unificado). Los cambios se aplican EN
 * VIVO como previsualización, pero el diálogo es TRANSACCIONAL: Aceptar los confirma
 * y Cancelar / cerrar los revierte al estado de apertura (panel.revert()).
 *
 * @author tonikelope
 */
public class AudioSettingsDialog extends javax.swing.JDialog {

    private final AudioSettingsPanel panel;
    // Transaccional: true solo si se pulsó Aceptar (entonces NO se revierte).
    private boolean committed = false;

    // Right-click menu shared by every speaker icon
    public static void showSpeakerPopup(java.awt.Component invoker, java.awt.Frame parent, int x, int y) {

        JPopupMenu popup = new JPopupMenu();

        JMenuItem settings_item = new JMenuItem(Translator.translate("audio.ajustes"));

        settings_item.setIcon(new javax.swing.ImageIcon(AudioSettingsDialog.class.getResource("/images/menu/gear.png")));

        settings_item.addActionListener(e -> open(parent));

        popup.add(settings_item);

        Helpers.updateFonts(popup, Helpers.GUI_FONT, null);

        popup.show(invoker, x, y);
    }

    public static void open(java.awt.Frame parent) {

        Helpers.GUIRun(() -> {
            AudioSettingsDialog dialog = new AudioSettingsDialog(parent, true);
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    // Keeps the slider in sync when the volume changes via the global shortcut
    // while a panel is open. Delegates to the shared panel (single source).
    public static void refreshVolume() {
        AudioSettingsPanel.refreshVolume();
    }

    public AudioSettingsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        setTitle(Translator.translate("audio.ajustes"));

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        panel = new AudioSettingsPanel();

        JButton ok_button = new JButton(Translator.translate("ui.aceptar"));
        ok_button.addActionListener(e -> {
            committed = true;
            dispose();
        });

        JButton cancel_button = new JButton(Translator.translate("ui.cancelar_2"));
        cancel_button.addActionListener(e -> dispose());

        JPanel button_panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        button_panel.add(ok_button);
        button_panel.add(cancel_button);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        content.add(panel, BorderLayout.CENTER);
        content.add(button_panel, BorderLayout.SOUTH);

        setContentPane(content);

        getRootPane().setDefaultButton(ok_button);

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowActivated(WindowEvent e) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(AudioSettingsDialog.this);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                if (isModal()) {
                    try {
                        Init.CURRENT_MODAL_DIALOG.removeLast();
                    } catch (Exception ex) {
                    }
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                // Transaccional: si NO se aceptó (Cancelar / cerrar), revierte los cambios
                // EN VIVO de audio al estado de apertura.
                if (!committed) {
                    panel.revert();
                }
                // Cierra captura de tecla + persiste volumen + suelta la instancia viva.
                panel.cleanup();
            }
        });

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1.2f);

        panel.applyFontsAndSizing();

        pack();
    }

}
