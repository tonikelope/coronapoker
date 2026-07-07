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
 * Diálogo modal de AUTO CALL: checkbox "Activado" (on/off), checkbox "Sin límite"
 * y un spinner EDITABLE con el importe MÁXIMO que el pre-pulsado de check/call
 * igualará automáticamente al hacer call. "Sin límite" mapea a AUTO_CALL_MAX = 0
 * (el motor lo interpreta como igualar cualquier importe) y desactiva el spinner;
 * "Activado" gobierna ambos.
 *
 * @author tonikelope
 */
public class AutoCallMaxDialog extends JDialog {

    private volatile boolean accepted = false;

    private final JSpinner spinner = new JSpinner();

    private final JCheckBox enabled_check = new JCheckBox();

    private final JCheckBox no_limit_check = new JCheckBox();

    // Paso/granularidad del umbral (0,05). En modo con límite el valor NUNCA baja
    // de un paso: el 0 se reserva para "sin límite" en el motor (AUTO_CALL_MAX ==
    // 0 = igualar cualquier importe).
    private static final BigDecimal STEP = new BigDecimal("0.05");

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isAutoCallEnabled() {
        return enabled_check.isSelected();
    }

    // Coerce cualquier Number del modelo/edición a BigDecimal con 2 decimales, sin
    // castear a ciegas: al teclear, el editor puede dejar un Double/Long en el
    // modelo y un cast directo a BigDecimal reventaría en flechas/aceptar.
    private static BigDecimal asBD(Object o) {
        if (o instanceof BigDecimal) {
            return ((BigDecimal) o).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(((Number) o).doubleValue())).setScale(2, RoundingMode.HALF_UP);
    }

    // "Sin límite" marcado => 0 (el motor lo interpreta como igualar cualquier
    // importe). Con límite se lee el TEXTO del editor (editable) normalizando la
    // coma a punto, sin depender del formatter de locale (grouping desactivado);
    // nunca por debajo de un paso, para no colisionar con el 0 de "sin límite".
    public double getValue() {
        if (no_limit_check.isSelected()) {
            return 0d;
        }

        BigDecimal v;

        try {
            String txt = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText().trim().replace(',', '.');
            v = txt.isEmpty() ? STEP : new BigDecimal(txt).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            v = asBD(spinner.getValue());
        }

        if (v.compareTo(STEP) < 0) {
            v = STEP;
        }

        return v.doubleValue();
    }

    public AutoCallMaxDialog(Frame parent, boolean enabled, double current) {

        super(parent, true);

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Pasos de 0,05 (la granularidad de ajuste de ciegas), redondeando a 2
        // decimales (el motor trabaja en céntimos). Es un umbral de auto-igualar,
        // no dinero en mesa. Mínimo un paso (el 0 se reserva para "sin límite") y
        // sin tope por arriba (máximo = null).
        double cur = Math.max(current, 0d);
        boolean no_limit = cur <= 0d;
        BigDecimal bd_current = new BigDecimal(String.valueOf(no_limit ? STEP.doubleValue() : Math.max(cur, STEP.doubleValue()))).setScale(2, RoundingMode.HALF_UP);

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

        // El spinner solo es usable con auto-call ON y sin "sin límite"; "sin
        // límite" solo tiene sentido con auto-call ON.
        Runnable refreshEnablement = () -> {
            boolean on = enabled_check.isSelected();
            no_limit_check.setEnabled(on);
            spinner.setEnabled(on && !no_limit_check.isSelected());
        };

        gbc.gridy++;
        enabled_check.setSelected(enabled);
        enabled_check.setFont(new Font("Dialog", Font.BOLD, 24));
        enabled_check.setBackground(Color.WHITE);
        enabled_check.setHorizontalAlignment(SwingConstants.CENTER);
        enabled_check.setFocusable(false);
        enabled_check.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // La etiqueta refleja el estado: ACTIVADO (verde) marcado, DESACTIVADO
        // (rojo) sin marcar. El checkbox además gobierna sin-límite y el spinner.
        Runnable refreshLabel = () -> {
            boolean on = enabled_check.isSelected();
            String key = on ? "auto_call.activado" : "auto_call.desactivado";
            enabled_check.setText(Translator.translate(key));
            enabled_check.putClientProperty("i18n.key", key);
            enabled_check.setForeground(on ? new Color(0, 130, 0) : new Color(200, 0, 0));
        };
        enabled_check.addActionListener((java.awt.event.ActionEvent e) -> {
            refreshLabel.run();
            refreshEnablement.run();
        });
        refreshLabel.run();
        panel.add(enabled_check, gbc);

        // Rótulo que aclara qué es el spinner: el importe MÁXIMO que se igualará
        // automáticamente al hacer call (con all-in, el propio stack).
        gbc.gridy++;
        JLabel note = new JLabel(Translator.translate("auto_call.nota"));
        note.putClientProperty("i18n.key", "auto_call.nota");
        note.setFont(new Font("Dialog", Font.PLAIN, 18));
        note.setForeground(Color.DARK_GRAY);
        note.setHorizontalAlignment(SwingConstants.CENTER);
        note.setFocusable(false);
        panel.add(note, gbc);

        // "Sin límite" a la IZQUIERDA del spinner (misma fila). Marcada => iguala
        // cualquier importe (mapea a AUTO_CALL_MAX = 0) y desactiva el spinner.
        no_limit_check.setSelected(no_limit);
        no_limit_check.setText(Translator.translate("auto_call.sin_limite"));
        no_limit_check.putClientProperty("i18n.key", "auto_call.sin_limite");
        no_limit_check.setFont(new Font("Dialog", Font.BOLD, 20));
        no_limit_check.setBackground(Color.WHITE);
        no_limit_check.setForeground(Color.DARK_GRAY);
        no_limit_check.setFocusable(false);
        no_limit_check.setCursor(new Cursor(Cursor.HAND_CURSOR));
        no_limit_check.addActionListener((java.awt.event.ActionEvent e) -> refreshEnablement.run());

        spinner.setModel(new SpinnerNumberModel(bd_current, STEP, null, STEP) {
            @Override
            public Object getNextValue() {
                // Sin tope por arriba: siempre se puede subir.
                return asBD(super.getValue()).add((BigDecimal) super.getStepSize());
            }

            @Override
            public Object getPreviousValue() {
                BigDecimal v = asBD(super.getValue()).subtract((BigDecimal) super.getStepSize());
                return ((BigDecimal) super.getMinimum()).compareTo(v) <= 0 ? v : null;
            }
        });
        spinner.setFont(new Font("Dialog", Font.BOLD, 24));
        // Editable por teclado (decimales). getValue() lee el TEXTO crudo del
        // editor (coma->punto), así que hay que blindar ese texto:
        //  - grouping OFF: sin separador de millar que confunda al parsear.
        //  - PERSIST + Enter desligado: el formatter NO recommitea al perder foco
        //    ni con Enter (evita que malinterprete coma/punto según el locale y
        //    corrompa lo tecleado).
        //  - re-render inicial con el formato ya sin grouping (el editor por
        //    defecto lo pintó con millares).
        if (spinner.getEditor() instanceof JSpinner.NumberEditor) {
            JSpinner.NumberEditor ne = (JSpinner.NumberEditor) spinner.getEditor();
            ne.getFormat().setGroupingUsed(false);
            javax.swing.JFormattedTextField ftf = ne.getTextField();
            ftf.setFocusLostBehavior(javax.swing.JFormattedTextField.PERSIST);
            ftf.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "none");
            ftf.setText(ne.getFormat().format(bd_current));
        }
        Helpers.makeNumericSpinnerEditable(spinner, true);
        spinner.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Fila: "Sin límite" a la IZQUIERDA + spinner (editable) a la derecha.
        JPanel spinner_row = new JPanel(new java.awt.BorderLayout(12, 0));
        spinner_row.setOpaque(false);
        spinner_row.add(no_limit_check, java.awt.BorderLayout.WEST);
        spinner_row.add(spinner, java.awt.BorderLayout.CENTER);
        gbc.gridy++;
        panel.add(spinner_row, gbc);

        refreshEnablement.run();

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
