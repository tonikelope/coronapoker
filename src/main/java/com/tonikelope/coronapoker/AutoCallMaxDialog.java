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
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Diálogo modal de AUTO CALL: un checkbox "Activado" (on/off) más un spinner para
 * fijar el límite de fichas que el pre-pulsado de check/call igualará
 * automáticamente (0 = sin límite). El checkbox habilita/deshabilita el spinner.
 *
 * @author tonikelope
 */
public class AutoCallMaxDialog extends JDialog {

    private volatile boolean accepted = false;

    private final JSpinner spinner = new JSpinner();

    private final JCheckBox enabled_check = new JCheckBox();

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isAutoCallEnabled() {
        return enabled_check.isSelected();
    }

    public float getValue() {
        return ((BigDecimal) spinner.getValue()).floatValue();
    }

    public AutoCallMaxDialog(Frame parent, boolean enabled, float current) {

        super(parent, true);

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Pasos de 0,05 (la granularidad de ajuste de ciegas), redondeando a 2
        // decimales (el motor trabaja en céntimos). Es un umbral de auto-igualar,
        // no dinero en mesa. Sin tope por arriba (máximo = null).
        BigDecimal step = new BigDecimal("0.05");
        BigDecimal bd_min = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal bd_current = new BigDecimal(Math.max(current, 0f)).setScale(2, RoundingMode.HALF_UP);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(8, 24, 8, 24);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel(Translator.translate("menu.auto_call"));
        title.putClientProperty("i18n.key", "menu.auto_call");
        title.setFont(new Font("Dialog", Font.BOLD, 30));
        title.setForeground(new Color(255, 102, 0));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFocusable(false);
        panel.add(title, gbc);

        gbc.gridy++;
        enabled_check.setSelected(enabled);
        enabled_check.setFont(new Font("Dialog", Font.BOLD, 24));
        enabled_check.setBackground(Color.WHITE);
        enabled_check.setHorizontalAlignment(SwingConstants.CENTER);
        enabled_check.setFocusable(false);
        enabled_check.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // La etiqueta refleja el estado: ACTIVADO (verde) marcado, DESACTIVADO
        // (rojo) sin marcar. El checkbox además habilita/deshabilita el spinner.
        Runnable refreshLabel = () -> {
            boolean on = enabled_check.isSelected();
            String key = on ? "auto_call.activado" : "auto_call.desactivado";
            enabled_check.setText(Translator.translate(key));
            enabled_check.putClientProperty("i18n.key", key);
            enabled_check.setForeground(on ? new Color(0, 130, 0) : new Color(200, 0, 0));
        };
        enabled_check.addActionListener((java.awt.event.ActionEvent e) -> {
            refreshLabel.run();
            spinner.setEnabled(enabled_check.isSelected());
        });
        refreshLabel.run();
        panel.add(enabled_check, gbc);

        gbc.gridy++;
        JLabel hint = new JLabel(Translator.translate("auto_call.cero_sin_limite"));
        hint.putClientProperty("i18n.key", "auto_call.cero_sin_limite");
        hint.setFont(new Font("Dialog", Font.PLAIN, 18));
        hint.setForeground(Color.DARK_GRAY);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setFocusable(false);
        panel.add(hint, gbc);

        gbc.gridy++;
        spinner.setModel(new SpinnerNumberModel(bd_current, bd_min, null, step) {
            @Override
            public Object getNextValue() {
                // Sin tope por arriba: siempre se puede subir.
                return ((BigDecimal) super.getValue()).add((BigDecimal) super.getStepSize());
            }

            @Override
            public Object getPreviousValue() {
                BigDecimal v = ((BigDecimal) super.getValue()).subtract((BigDecimal) super.getStepSize());
                return ((BigDecimal) super.getMinimum()).compareTo(v) <= 0 ? v : null;
            }
        });
        spinner.setFont(new Font("Dialog", Font.BOLD, 24));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setEditable(false);
        spinner.setCursor(new Cursor(Cursor.HAND_CURSOR));
        spinner.setEnabled(enabled);
        panel.add(spinner, gbc);

        gbc.gridy++;
        JLabel note = new JLabel(Translator.translate("auto_call.nota"));
        note.putClientProperty("i18n.key", "auto_call.nota");
        note.setFont(new Font("Dialog", Font.ITALIC, 16));
        note.setForeground(Color.GRAY);
        note.setHorizontalAlignment(SwingConstants.CENTER);
        note.setFocusable(false);
        panel.add(note, gbc);

        gbc.gridy++;
        JButton ok = new JButton(Translator.translate("ui.aceptar"));
        ok.putClientProperty("i18n.key", "ui.aceptar");
        ok.setBackground(new Color(0, 130, 0));
        ok.setForeground(Color.WHITE);
        ok.setFont(new Font("Dialog", Font.BOLD, 18));
        ok.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ok.setFocusable(false);
        ok.addActionListener((java.awt.event.ActionEvent e) -> {
            accepted = true;
            dispose();
        });
        panel.add(ok, gbc);

        gbc.gridy++;
        JButton cancel = new JButton(Translator.translate("ui.cancelar_2"));
        cancel.putClientProperty("i18n.key", "ui.cancelar_2");
        cancel.setBackground(new Color(200, 0, 0));
        cancel.setForeground(Color.WHITE);
        cancel.setFont(new Font("Dialog", Font.BOLD, 18));
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.setFocusable(false);
        cancel.addActionListener((java.awt.event.ActionEvent e) -> {
            accepted = false;
            dispose();
        });
        panel.add(cancel, gbc);

        setContentPane(panel);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowActivated(java.awt.event.WindowEvent evt) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(AutoCallMaxDialog.this);
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
        setLocationRelativeTo(parent);
    }
}
