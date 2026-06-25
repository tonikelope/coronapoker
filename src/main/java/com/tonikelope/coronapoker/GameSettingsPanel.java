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

import javax.swing.SpinnerNumberModel;

/**
 * Contenido de "Ajustes de partida" como JPanel (pestaña del diálogo unificado):
 * las REGLAS de juego a la izquierda (sin subpanel) y las CIEGAS a la derecha (en
 * un subpanel titulado). Para clientes se construye en SOLO-LECTURA. Toda la lógica
 * de ciegas (dinero-sensible) es la del antiguo EditBlindsDialog. Se aplica con
 * {@link #applyToGame()} (lo dispara el botón GUARDAR del diálogo unificado);
 * apariencia y audio se aplican en vivo, las reglas/ciegas solo al guardar.
 *
 * @author tonikelope
 */
public class GameSettingsPanel extends javax.swing.JPanel {

    private volatile boolean init = false;

    private final boolean read_only;

    // Reglas (izquierda, sin subpanel)
    private javax.swing.JPanel rules_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JLabel manos_label;
    private javax.swing.JSpinner manos_spinner;
    private javax.swing.JCheckBox iwtsth_checkbox;
    private javax.swing.JLabel iwtsth_label;
    private javax.swing.JCheckBox rit_checkbox;
    private javax.swing.JLabel rit_label;
    private javax.swing.JLabel rabbit_label;
    private javax.swing.JComboBox<String> rabbit_combo;

    // Ciegas (derecha, subpanel titulado)
    private javax.swing.JPanel ciegas_panel;
    private javax.swing.JComboBox<String> ciegas_combobox;
    private javax.swing.JCheckBox doblar_checkbox;
    private javax.swing.JRadioButton double_blinds_radio_minutos;
    private javax.swing.JRadioButton double_blinds_radio_manos;
    private javax.swing.JSpinner doblar_ciegas_spinner_minutos;
    private javax.swing.JSpinner doblar_ciegas_spinner_manos;
    private javax.swing.JPanel blind_cap_panel;
    private javax.swing.JCheckBox blind_cap_checkbox;
    private javax.swing.JSpinner blind_cap_spinner;
    private javax.swing.JLabel blind_cap_label;
    private javax.swing.JCheckBox ante_checkbox;
    private javax.swing.JCheckBox straddle_checkbox;

    public GameSettingsPanel(boolean read_only) {
        this.read_only = read_only;
        initComponents();

        // ============================ CIEGAS ============================
        if (GameFrame.ACTIVE_BLIND_STRUCTURE != null) {
            double[][] levels = GameFrame.ACTIVE_BLIND_STRUCTURE;
            String[] items = new String[levels.length];
            for (int k = 0; k < levels.length; k++) {
                items[k] = BlindStructure.formatLevel(levels[k][0], levels[k][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        }

        Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
        Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);

        blind_cap_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> updateBlindCapLabel());
        ciegas_combobox.addActionListener((java.awt.event.ActionEvent e) -> {
            if (init) {
                modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());
            }
        });

        double peque, grande;
        int ciegas_double, ciegas_double_type;

        if (GameFrame.getInstance().getCrupier().getCiegas_update() != null) {
            peque = (double) GameFrame.getInstance().getCrupier().getCiegas_update()[0];
            grande = (double) GameFrame.getInstance().getCrupier().getCiegas_update()[1];
            ciegas_double = (int) GameFrame.getInstance().getCrupier().getCiegas_update()[2];
            ciegas_double_type = (int) GameFrame.getInstance().getCrupier().getCiegas_update()[3];
        } else {
            peque = GameFrame.getInstance().getCrupier().getCiega_pequeña();
            grande = GameFrame.getInstance().getCrupier().getCiega_grande();
            ciegas_double = GameFrame.CIEGAS_DOUBLE;
            ciegas_double_type = GameFrame.CIEGAS_DOUBLE_TYPE;
        }

        this.ante_checkbox.setSelected(GameFrame.ANTE);
        this.straddle_checkbox.setSelected(GameFrame.STRADDLE);

        this.doblar_checkbox.setSelected(ciegas_double > 0);
        double_blinds_radio_minutos.setEnabled(ciegas_double > 0);
        double_blinds_radio_manos.setEnabled(ciegas_double > 0);

        if (ciegas_double_type <= 1) {
            doblar_ciegas_spinner_minutos.setEnabled(ciegas_double > 0);
            doblar_ciegas_spinner_minutos.setModel(new SpinnerNumberModel(ciegas_double > 0 ? ciegas_double : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);
            double_blinds_radio_minutos.setSelected(true);
            double_blinds_radio_manos.setSelected(false);
        } else {
            doblar_ciegas_spinner_manos.setEnabled(ciegas_double > 0);
            doblar_ciegas_spinner_manos.setModel(new SpinnerNumberModel(ciegas_double > 0 ? ciegas_double : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            double_blinds_radio_minutos.setSelected(false);
            double_blinds_radio_manos.setSelected(true);
        }

        String ciegas = BlindStructure.formatLevel(peque, grande);
        int i = 0, t = this.ciegas_combobox.getModel().getSize();
        while (i < t) {
            if (this.ciegas_combobox.getItemAt(i).equals(ciegas)) {
                break;
            }
            i++;
        }
        if (i < t) {
            this.ciegas_combobox.setSelectedIndex(i);
        }

        this.blind_cap_checkbox.setSelected(GameFrame.BLIND_CAP > 0f);
        this.blind_cap_checkbox.setEnabled(ciegas_double > 0);
        modelBlindCapSpinner(blindCapDoublingsFromCap());
        this.blind_cap_spinner.setEnabled(ciegas_double > 0 && GameFrame.BLIND_CAP > 0f);

        // ============================ PARTIDA (reglas) ============================
        int mano_actual = GameFrame.getInstance().getCrupier().getMano();
        int manos_min = Math.max(1, mano_actual + 1);
        boolean manos_on = GameFrame.MANOS != -1;
        manos_spinner.setModel(new SpinnerNumberModel(manos_on ? Math.max(GameFrame.MANOS, manos_min) : Math.max(100, manos_min), manos_min, null, 1));
        Helpers.makeNumericSpinnerEditable(manos_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) manos_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        manos_checkbox.setSelected(manos_on);
        manos_spinner.setEnabled(manos_on);

        iwtsth_checkbox.setSelected(GameFrame.IWTSTH_RULE);
        rit_checkbox.setSelected(GameFrame.RUN_IT_TWICE);

        rabbit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("menu.off"),
            Translator.translate("menu.free"),
            Translator.translate("menu.free_sb"),
            Translator.translate("menu.free_sb_bb")
        }));
        rabbit_combo.setSelectedIndex(Math.min(Math.max(GameFrame.RABBIT_HUNTING, 0), 3));

        java.awt.Font title_font = ante_checkbox.getFont();
        ciegas_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_ciegas")));
        ((javax.swing.border.TitledBorder) ciegas_panel.getBorder()).setTitleFont(title_font);

        Helpers.translateComponents(this, false);

        init = true;

        if (read_only) {
            ciegas_combobox.setEnabled(false);
            doblar_checkbox.setEnabled(false);
            double_blinds_radio_minutos.setEnabled(false);
            double_blinds_radio_manos.setEnabled(false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            blind_cap_checkbox.setEnabled(false);
            blind_cap_spinner.setEnabled(false);
            ante_checkbox.setEnabled(false);
            straddle_checkbox.setEnabled(false);
            manos_checkbox.setEnabled(false);
            manos_spinner.setEnabled(false);
            iwtsth_checkbox.setEnabled(false);
            rit_checkbox.setEnabled(false);
            rabbit_combo.setEnabled(false);
        } else if (GameFrame.RUN_IT_TWICE_LOCKED) {
            rit_checkbox.setEnabled(false);
        }
    }

    public boolean isReadOnly() {
        return read_only;
    }

    private void initComponents() {

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        rules_panel = new javax.swing.JPanel();
        manos_checkbox = new javax.swing.JCheckBox();
        manos_label = new javax.swing.JLabel();
        manos_spinner = new javax.swing.JSpinner();
        iwtsth_checkbox = new javax.swing.JCheckBox();
        iwtsth_label = new javax.swing.JLabel();
        rit_checkbox = new javax.swing.JCheckBox();
        rit_label = new javax.swing.JLabel();
        rabbit_label = new javax.swing.JLabel();
        rabbit_combo = new javax.swing.JComboBox<>();

        ciegas_panel = new javax.swing.JPanel();
        ciegas_combobox = new javax.swing.JComboBox<>();
        doblar_checkbox = new javax.swing.JCheckBox();
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        blind_cap_panel = new javax.swing.JPanel();
        blind_cap_checkbox = new javax.swing.JCheckBox();
        blind_cap_spinner = new javax.swing.JSpinner();
        blind_cap_label = new javax.swing.JLabel();
        ante_checkbox = new javax.swing.JCheckBox();
        straddle_checkbox = new javax.swing.JCheckBox();

        // ---------------- Reglas (izquierda, sin subpanel) ----------------
        manos_label.setFont(new java.awt.Font("Dialog", 1, 16));
        manos_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png")));
        manos_label.setText("Límite de manos:");
        manos_label.putClientProperty("i18n.key", "game.limite_de_manos");

        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        iwtsth_label.setFont(new java.awt.Font("Dialog", 1, 16));
        iwtsth_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/eyes.png")));
        iwtsth_label.setText("Regla IWTSTH");
        iwtsth_label.putClientProperty("i18n.key", "menu.regla_iwtsth");

        iwtsth_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rit_label.setFont(new java.awt.Font("Dialog", 1, 16));
        rit_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")));
        rit_label.setText("ALL-IN Run-it-twice");
        rit_label.putClientProperty("i18n.key", "menu.regla_run_it_twice");

        rit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rabbit_label.setFont(new java.awt.Font("Dialog", 1, 16));
        rabbit_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rabbit.png")));
        rabbit_label.setText("Rabbit Hunting");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");

        rabbit_combo.setFont(new java.awt.Font("Dialog", 0, 16));
        rabbit_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout rules_panelLayout = new javax.swing.GroupLayout(rules_panel);
        rules_panel.setLayout(rules_panelLayout);
        rules_panelLayout.setHorizontalGroup(
            rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rules_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(manos_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(manos_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(iwtsth_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(iwtsth_label))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(rit_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(rit_label))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(rabbit_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        rules_panelLayout.setVerticalGroup(
            rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rules_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(manos_checkbox)
                    .addComponent(manos_label)
                    .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iwtsth_checkbox)
                    .addComponent(iwtsth_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rit_checkbox)
                    .addComponent(rit_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rabbit_label)
                    .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Ciegas (derecha, subpanel titulado) ----------------
        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,10 / 0,20" : "0.10 / 0.20", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,20 / 0,40" : "0.20 / 0.40", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,30 / 0,60" : "0.30 / 0.60", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,50 / 1" : "0.50 / 1", "1 / 2", "2 / 4", "3 / 6", "5 / 10", "10 / 20", "20 / 40", "30 / 60", "50 / 100", "100 / 200", "200 / 400", "300 / 600", "500 / 1000", "1000 / 2000", "2000 / 4000", "3000 / 6000", "5000 / 10000"}));
        ciegas_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        doblar_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        doblar_checkbox.setText("Aumentar ciegas");
        doblar_checkbox.putClientProperty("i18n.key", "blinds.aumentar_ciegas");
        doblar_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doblar_checkboxActionPerformed(evt);
            }
        });

        double_blinds_radio_manos.setFont(new java.awt.Font("Dialog", 1, 14));
        double_blinds_radio_manos.setText("Manos:");
        double_blinds_radio_manos.putClientProperty("i18n.key", "game.manos");
        double_blinds_radio_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_manos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_manosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_manos.setFont(new java.awt.Font("Dialog", 0, 16));
        doblar_ciegas_spinner_manos.setModel(new javax.swing.SpinnerNumberModel(30, 1, null, 1));
        doblar_ciegas_spinner_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        double_blinds_radio_minutos.setFont(new java.awt.Font("Dialog", 1, 14));
        double_blinds_radio_minutos.setText("Minutos:");
        double_blinds_radio_minutos.putClientProperty("i18n.key", "ui.minutos");
        double_blinds_radio_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_minutos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_minutosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_minutos.setFont(new java.awt.Font("Dialog", 0, 16));
        doblar_ciegas_spinner_minutos.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        doblar_ciegas_spinner_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        blind_cap_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        blind_cap_checkbox.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_checkbox.setText("Tope ciega grande");
        blind_cap_checkbox.putClientProperty("i18n.key", "blinds.tope_ciega_grande");
        blind_cap_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blind_cap_checkboxActionPerformed(evt);
            }
        });

        blind_cap_spinner.setFont(new java.awt.Font("Dialog", 0, 14));
        blind_cap_spinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, null, 1));
        blind_cap_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        blind_cap_label.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_label.setText("0 / 0");

        javax.swing.GroupLayout blind_cap_panelLayout = new javax.swing.GroupLayout(blind_cap_panel);
        blind_cap_panel.setLayout(blind_cap_panelLayout);
        blind_cap_panelLayout.setHorizontalGroup(
            blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(blind_cap_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addGroup(blind_cap_panelLayout.createSequentialGroup()
                        .addComponent(blind_cap_checkbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(blind_cap_label))
                .addContainerGap())
        );
        blind_cap_panelLayout.setVerticalGroup(
            blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(blind_cap_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(blind_cap_checkbox)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blind_cap_label)
                .addContainerGap())
        );

        ante_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        ante_checkbox.setText("Ante");
        ante_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        straddle_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        straddle_checkbox.setText("Straddle");
        straddle_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout ciegas_panelLayout = new javax.swing.GroupLayout(ciegas_panel);
        ciegas_panel.setLayout(ciegas_panelLayout);
        ciegas_panelLayout.setHorizontalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ciegas_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(doblar_checkbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(double_blinds_radio_minutos)
                            .addComponent(double_blinds_radio_manos))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(blind_cap_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(ante_checkbox)
                        .addGap(18, 18, 18)
                        .addComponent(straddle_checkbox)))
                .addContainerGap())
        );
        ciegas_panelLayout.setVerticalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(doblar_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_manos)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_minutos)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(blind_cap_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ante_checkbox)
                    .addComponent(straddle_checkbox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        add(rules_panel);
        add(javax.swing.Box.createHorizontalStrut(12));
        add(ciegas_panel);

        // i18n de las etiquetas con icono (se traducen en translateComponents).
        manos_label.putClientProperty("i18n.key", "game.limite_de_manos");
        iwtsth_label.putClientProperty("i18n.key", "menu.regla_iwtsth");
        rit_label.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");
    }

    // ===================== Aplicar (lo dispara GUARDAR del diálogo unificado) =====================
    public void applyToGame() {

        if (read_only) {
            return;
        }

        // ---- Ciegas (idéntico a EditBlindsDialog) ----
        int ciegas_double, ciegas_double_type;

        if (this.doblar_checkbox.isSelected()) {
            if (this.double_blinds_radio_minutos.isSelected()) {
                ciegas_double = (int) this.doblar_ciegas_spinner_minutos.getValue();
                ciegas_double_type = 1;
            } else {
                ciegas_double = (int) this.doblar_ciegas_spinner_manos.getValue();
                ciegas_double_type = 2;
            }
        } else {
            ciegas_double = 0;
            ciegas_double_type = 1;
        }

        double blind_cap = (this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected()) ? blindCapSelectedBB() : 0;
        GameFrame.BLIND_CAP = blind_cap;

        GameFrame.ANTE = this.ante_checkbox.isSelected();
        GameFrame.STRADDLE = this.straddle_checkbox.isSelected();

        String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

        GameFrame.getInstance().getCrupier().actualizarCiegasManualmente(Double.valueOf(valores_ciegas[0].trim()), Double.valueOf(valores_ciegas[1].trim()), ciegas_double, ciegas_double_type);

        // ---- Límite de manos: misma semántica que CommunityCardsPanel.click_max_hands ----
        int old_manos = GameFrame.MANOS;
        int desired_manos;
        if (!manos_checkbox.isSelected()) {
            desired_manos = -1;
        } else {
            int v = (int) manos_spinner.getValue();
            desired_manos = (GameFrame.getInstance().getCrupier().getMano() < v) ? v : old_manos;
        }
        final boolean manos_changed = desired_manos != old_manos;
        if (manos_changed) {
            GameFrame.MANOS = desired_manos;
        }

        boolean iwtsth = iwtsth_checkbox.isSelected();
        boolean rit = rit_checkbox.isSelected();
        int rabbit = rabbit_combo.getSelectedIndex();

        final int ciegas_double_f = ciegas_double, ciegas_double_type_f = ciegas_double_type;
        final double blind_cap_f = blind_cap;
        final String sb = valores_ciegas[0].trim(), bb = valores_ciegas[1].trim();

        Helpers.threadRun(() -> {
            GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("UPDATEBLINDS#" + String.valueOf(ciegas_double_f) + "#" + String.valueOf(ciegas_double_type_f) + "#" + sb + "#" + bb + "#" + String.valueOf(blind_cap_f) + "#" + String.valueOf(GameFrame.ANTE) + "#" + String.valueOf(GameFrame.STRADDLE), null);
            if (manos_changed) {
                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("MAXHANDS#" + String.valueOf(GameFrame.MANOS), null);
            }
            GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
        });

        if (iwtsth != GameFrame.IWTSTH_RULE) {
            GameFrame.setIwtsthRule(iwtsth);
        }
        if (rit != GameFrame.RUN_IT_TWICE) {
            GameFrame.setRunItTwiceRule(rit);
        }
        if (rabbit != GameFrame.RABBIT_HUNTING) {
            GameFrame.setRabbitHunting(rabbit);
        }
    }

    private void manos_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            manos_spinner.setEnabled(manos_checkbox.isSelected());
        }
    }

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            this.doblar_ciegas_spinner_minutos.setEnabled(this.double_blinds_radio_minutos.isSelected() && this.doblar_checkbox.isSelected());
            this.doblar_ciegas_spinner_manos.setEnabled(this.double_blinds_radio_manos.isSelected() && this.doblar_checkbox.isSelected());
            this.double_blinds_radio_manos.setEnabled(this.doblar_checkbox.isSelected());
            this.double_blinds_radio_minutos.setEnabled(this.doblar_checkbox.isSelected());
            this.blind_cap_checkbox.setEnabled(this.doblar_checkbox.isSelected());
            this.blind_cap_spinner.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
        }
    }

    private void double_blinds_radio_minutosActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            if (this.double_blinds_radio_minutos.isSelected()) {
                this.doblar_ciegas_spinner_minutos.setEnabled(true);
                this.double_blinds_radio_manos.setSelected(false);
                this.doblar_ciegas_spinner_manos.setEnabled(false);
            } else {
                this.double_blinds_radio_minutos.setSelected(true);
            }
        }
    }

    private void double_blinds_radio_manosActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            if (this.double_blinds_radio_manos.isSelected()) {
                this.doblar_ciegas_spinner_manos.setEnabled(true);
                this.double_blinds_radio_minutos.setSelected(false);
                this.doblar_ciegas_spinner_minutos.setEnabled(false);
            } else {
                this.double_blinds_radio_manos.setSelected(true);
            }
        }
    }

    private void blind_cap_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            this.blind_cap_spinner.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
        }
    }

    private double parseBlindLevelBB(String item) {
        return Double.parseDouble(item.replace(",", ".").split("/")[1].trim());
    }

    private int blindCapTargetIndex(int n) {
        int last = ciegas_combobox.getModel().getSize() - 1;
        return Math.min(Math.max(0, ciegas_combobox.getSelectedIndex()) + n, last);
    }

    private double blindCapSelectedBB() {
        return parseBlindLevelBB(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private void updateBlindCapLabel() {
        blind_cap_label.setText(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private int blindCapDoublingsFromCap() {
        int initial = Math.max(0, ciegas_combobox.getSelectedIndex());
        if (GameFrame.BLIND_CAP > 0f) {
            for (int k = initial + 1; k < ciegas_combobox.getModel().getSize(); k++) {
                if (Helpers.doubleSecureCompare(parseBlindLevelBB(ciegas_combobox.getItemAt(k)), GameFrame.BLIND_CAP) == 0) {
                    return k - initial;
                }
            }
        }
        return 5;
    }

    private void modelBlindCapSpinner(int n) {
        int levels_above = Math.max(1, ciegas_combobox.getModel().getSize() - 1 - Math.max(0, ciegas_combobox.getSelectedIndex()));
        n = Math.min(Math.max(1, n), levels_above);
        this.blind_cap_spinner.setModel(new SpinnerNumberModel(n, 1, levels_above, 1));
        Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
        updateBlindCapLabel();
    }

}
