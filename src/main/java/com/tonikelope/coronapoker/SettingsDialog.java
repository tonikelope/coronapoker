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
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

/**
 * Diálogo unificado de ajustes (en partida), con 3 pestañas: Apariencia, Audio y
 * Partida. Apariencia y Audio se aplican EN VIVO (preferencias locales); Partida
 * (ciegas + reglas) solo al pulsar GUARDAR. Para clientes la pestaña Partida es de
 * solo-lectura (las reglas las manda el host) y GUARDAR queda deshabilitado;
 * Apariencia y Audio siguen siendo editables (son locales).
 *
 * @author tonikelope
 */
public class SettingsDialog extends JDialog {

    private final AppearanceSettingsPanel appearance_panel;
    private final AudioSettingsPanel audio_panel;
    private final GameSettingsPanel game_panel;
    // Diálogo transaccional: true solo si se pulsó GUARDAR (entonces NO se revierte).
    private boolean committed = false;

    public static void open(java.awt.Frame parent) {
        Helpers.GUIRun(() -> {
            SettingsDialog dialog = new SettingsDialog(parent, true);
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    public SettingsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        boolean read_only = GameFrame.getInstance() == null || !GameFrame.getInstance().isPartida_local();

        setTitle(Translator.translate("settings.ajustes"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        appearance_panel = new AppearanceSettingsPanel();
        audio_panel = new AudioSettingsPanel();
        game_panel = new GameSettingsPanel(read_only);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Translator.translate("settings.tab_apariencia"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")), appearance_panel);
        tabs.addTab(Translator.translate("settings.tab_audio"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/sound.png")), audio_panel);
        tabs.addTab(Translator.translate("settings.tab_partida"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")), game_panel);

        // Diálogo TRANSACCIONAL: Apariencia y Audio se aplican en vivo como
        // previsualización, pero GUARDAR es lo que los CONFIRMA y además aplica el modo
        // de pantalla pendiente y la pestaña Partida (ciegas + reglas, solo si eres host:
        // applyToGame no-opea para clientes). Cancelar / cerrar revierte TODO al estado
        // de apertura (ver windowClosed). GUARDAR está siempre activo: para un cliente
        // confirma sus ajustes LOCALES de apariencia y audio.
        JButton save_button = new JButton(Translator.translate("ui.guardar"));
        save_button.setBackground(new java.awt.Color(0, 130, 0));
        save_button.setForeground(new java.awt.Color(255, 255, 255));
        save_button.addActionListener(e -> {
            committed = true;
            game_panel.applyToGame();
            appearance_panel.applyPendingDisplayMode();
            dispose();
        });

        JButton cancel_button = new JButton(Translator.translate("ui.cancelar_2"));
        cancel_button.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(save_button);
        buttons.add(cancel_button);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(tabs, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(SettingsDialog.this);
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
                // Si NO se guardó (Cancelar / cerrar), revierte los cambios EN VIVO de
                // Apariencia y Audio al estado de apertura. El modo de pantalla y la
                // pestaña Partida solo se aplican al GUARDAR (no aquí).
                if (!committed) {
                    appearance_panel.revert();
                    audio_panel.revert();
                }
                // Cierra la captura de tecla del panel de audio + persiste el volumen.
                audio_panel.cleanup();
            }
        });

        // Fuentes UNIFICADAS al tamaño del diálogo de nueva timba (16, conservando el
        // estilo bold/plain de cada control). Las pestañas Apariencia y Audio usaban
        // la fuente por defecto (más pequeña) y quedaban descompensadas respecto a
        // Partida; con esto todo el diálogo va al mismo tamaño.
        setUniformFont(content, Helpers.GUI_FONT, 16);

        // setUniformFont no alcanza los títulos de los TitledBorder.
        fixTitledBorderFonts(content, save_button.getFont());

        // Arreglos de tamaño del panel de audio (máximos de fila/panel), ya con la
        // fuente unificada aplicada.
        audio_panel.applyFontsAndSizing();

        // Botones de acción un pelín más grandes que el resto del diálogo.
        java.awt.Font buttons_font = Helpers.GUI_FONT.deriveFont(Font.BOLD, 18f);
        save_button.setFont(buttons_font);
        cancel_button.setFont(buttons_font);

        pack();
    }

    // Aplica GUI_FONT a TODOS los componentes al MISMO tamaño (conservando el estilo
    // bold/plain de cada uno), para que las 3 pestañas tengan fuentes homogéneas.
    private static void setUniformFont(Container c, Font base, int size) {
        for (Component child : c.getComponents()) {
            Font f = child.getFont();
            int style = (f != null) ? f.getStyle() : Font.PLAIN;
            child.setFont(base.deriveFont(style, (float) size));
            if (child instanceof Container) {
                setUniformFont((Container) child, base, size);
            }
        }
    }

    private static void fixTitledBorderFonts(Container c, Font font) {
        if (c instanceof javax.swing.JComponent) {
            javax.swing.border.Border b = ((javax.swing.JComponent) c).getBorder();
            if (b instanceof TitledBorder) {
                ((TitledBorder) b).setTitleFont(font);
            }
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                fixTitledBorderFonts((Container) child, font);
            }
        }
    }

}
