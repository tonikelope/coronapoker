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
 * Modal AUTO CALL dialog: an "Enabled" checkbox (on/off), a "No limit" checkbox, and
 * an editable spinner for the MAXIMUM amount that a pre-pressed check/call will match
 * automatically. "No limit" maps to AUTO_CALL_MAX = 0 (the engine reads that as
 * matching any amount) and disables the spinner; "Enabled" gates both.
 *
 * @author tonikelope
 */
public class AutoCallMaxDialog extends JDialog {

    private volatile boolean accepted = false;

    private final JSpinner spinner = new JSpinner();

    private final JCheckBox enabled_check = new JCheckBox();

    private final JCheckBox no_limit_check = new JCheckBox();

    // Threshold step/granularity (0.05). In limited mode the value never drops below
    // one step: 0 is reserved for "no limit" in the engine (AUTO_CALL_MAX == 0 means
    // match any amount).
    private static final BigDecimal STEP = new BigDecimal("0.05");

    /**
     * @return whether the user accepted the dialog (OK vs. Cancel/close).
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * @return whether the "Enabled" checkbox is checked.
     */
    public boolean isAutoCallEnabled() {
        return enabled_check.isSelected();
    }

    // Coerces any Number coming from the model/editor to a 2-decimal BigDecimal
    // instead of blind-casting: while typing, the editor may leave a Double/Long in
    // the model, and a direct cast to BigDecimal would blow up on arrows/accept.
    private static BigDecimal asBD(Object o) {
        if (o instanceof BigDecimal) {
            return ((BigDecimal) o).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(((Number) o).doubleValue())).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Reads the current threshold: 0 when "No limit" is checked; otherwise the
     * editor's raw TEXT (comma normalized to dot, grouping disabled, locale
     * formatter not trusted), clamped to at least one step so it can never collide
     * with the 0 that means "no limit".
     *
     * @return the AUTO_CALL_MAX threshold, with 2-decimal precision.
     */
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

    /**
     * @param parent  owner frame
     * @param enabled initial state of the "Enabled" checkbox
     * @param current initial threshold; values &lt;= 0 start as "No limit"
     */
    public AutoCallMaxDialog(Frame parent, boolean enabled, double current) {

        super(parent, true);

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // 0.05 steps (the blinds adjustment granularity), rounded to 2 decimals (the
        // engine works in cents). This is an auto-match threshold, not table money.
        // Minimum one step (0 is reserved for "no limit") and no upper cap (max = null).
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

        // The spinner is only usable with auto-call ON and "no limit" unchecked;
        // "no limit" only makes sense with auto-call ON.
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
        // The label reflects the state: ENABLED (green) when checked, DISABLED (red)
        // when not. The checkbox also gates "no limit" and the spinner.
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

        // Note clarifying what the spinner is: the MAXIMUM amount that will be
        // matched automatically on call (with all-in, the stack itself).
        gbc.gridy++;
        JLabel note = new JLabel(Translator.translate("auto_call.nota"));
        note.putClientProperty("i18n.key", "auto_call.nota");
        note.setFont(new Font("Dialog", Font.PLAIN, 18));
        note.setForeground(Color.DARK_GRAY);
        note.setHorizontalAlignment(SwingConstants.CENTER);
        note.setFocusable(false);
        panel.add(note, gbc);

        // "No limit" to the LEFT of the spinner (same row). Checked => matches any
        // amount (maps to AUTO_CALL_MAX = 0) and disables the spinner.
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
                // No upper cap: always allowed to go up.
                return asBD(super.getValue()).add((BigDecimal) super.getStepSize());
            }

            @Override
            public Object getPreviousValue() {
                BigDecimal v = asBD(super.getValue()).subtract((BigDecimal) super.getStepSize());
                return ((BigDecimal) super.getMinimum()).compareTo(v) <= 0 ? v : null;
            }
        });
        spinner.setFont(new Font("Dialog", Font.BOLD, 24));
        // Editable by keyboard (decimals). getValue() reads the editor's raw TEXT
        // (comma->dot), so that text must be safeguarded:
        //  - grouping OFF: no thousands separator to confuse the parser.
        //  - PERSIST + Enter unbound: the formatter does NOT recommit on focus loss
        //    or Enter (avoids misreading comma/dot per locale and corrupting input).
        //  - initial re-render with the grouping-less format (the default editor
        //    painted it with thousands separators).
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

        // Row: "No limit" on the LEFT + editable spinner on the right.
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

        Helpers.applyDialogZoom(this);
        Helpers.translateComponents(this, false);

        pack();
        setLocationRelativeTo(parent);
    }
}
