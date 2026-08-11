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
 * "Game settings" tab content as a JPanel (page of the unified dialog): game RULES on the
 * left (no subpanel) and BLINDS on the right (in a titled subpanel). Built READ-ONLY for
 * clients. All blind logic (money-sensitive) comes from the old EditBlindsDialog. Applied
 * via {@link #applyToGame()} (triggered by the unified dialog's SAVE button); appearance
 * and audio apply live, rules/blinds only on save.
 *
 * @author tonikelope
 */
public class GameSettingsPanel extends javax.swing.JPanel {

    private volatile boolean init = false;

    private final boolean read_only;

    // Signature of control values when OPENED; isDirty() diffs against the current one to
    // tell if the tab has unsaved changes (this tab only applies on SAVE).
    private String snap_signature;

    // Blind-structure selector: lets the user PICK (not create) an already-saved structure
    // or "Default" during the game; SAVE sets ACTIVE_BLIND_STRUCTURE and propagates it to
    // clients. pending_structure = the picked one (null = default).
    private String item_estructura_por_defecto;
    private String item_estructura_actual;
    private BlindStructure pending_structure;
    // Unsaved ACTIVE structure (synthetic "(current)" item); kept separately so it can be
    // restored if the user navigates Default -> (current) without losing it.
    private BlindStructure actual_structure;

    // Rules (left side, no subpanel)
    private javax.swing.JPanel rules_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JLabel manos_label;
    private javax.swing.JSpinner manos_spinner;
    // Think time: READ-ONLY in-game (not changeable once the game has started).
    private javax.swing.JCheckBox think_time_checkbox;
    private javax.swing.JLabel think_time_label;
    private javax.swing.JSpinner think_time_spinner;
    // Showdown time: READ-ONLY in-game (not changeable once started). No checkbox: the pause
    // cannot be disabled.
    private javax.swing.JLabel showdown_time_label;
    private javax.swing.JSpinner showdown_time_spinner;
    private javax.swing.JCheckBox iwtsth_checkbox;
    private javax.swing.JLabel iwtsth_label;
    private javax.swing.JCheckBox rit_checkbox;
    private javax.swing.JLabel rit_label;
    private javax.swing.JLabel rabbit_label;
    private javax.swing.JComboBox<String> rabbit_combo;

    // Blinds (right side, titled subpanel)
    private javax.swing.JPanel ciegas_panel;
    private javax.swing.JLabel estructura_label;
    private javax.swing.JComboBox<String> estructura_combobox;
    private javax.swing.JLabel ciegas_label;
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
    private javax.swing.JLabel ante_label;
    private javax.swing.JCheckBox straddle_checkbox;
    private javax.swing.JLabel straddle_label;

    // Bots (subpanel, below rules|blinds). Difficulty and rebuy are READ-ONLY in-game (not
    // changeable once started); "balance payout" IS editable (harmless: only affects the 2nd
    // table of the end-of-game log, doesn't touch the audit). For clients everything is
    // read-only.
    private javax.swing.JPanel bots_panel;
    private javax.swing.JLabel bots_avatar_label;
    private javax.swing.JLabel bots_label;
    private javax.swing.JComboBox<String> bots_combobox;
    private javax.swing.JCheckBox bot_rebuy_checkbox;
    private javax.swing.JCheckBox bot_balance_checkbox;

    // Buy-in + rebuy (subpanel, INFO-ONLY in-game: buy-in and rebuy economics are fixed once
    // the game starts; shown DISABLED, for information only).
    private javax.swing.JPanel compra_panel;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JCheckBox fixed_buyin_checkbox;
    private javax.swing.JLabel buyin_range_label;
    private javax.swing.JSpinner buyin_min_bb_spinner;
    private javax.swing.JLabel buyin_range_sep_label;
    private javax.swing.JSpinner buyin_max_bb_spinner;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JLabel recomprar_label;
    private javax.swing.JCheckBox rebuy_limit_checkbox;
    private javax.swing.JSpinner rebuy_limit_spinner;
    private javax.swing.JLabel rebuy_cap_label;
    private javax.swing.JComboBox<String> rebuy_cap_combo;

    // i18n tooltips (setTranslatedToolTip => re-translated on language change) for settings
    // controls whose function isn't obvious from their label. Called after initComponents().
    private void setupTooltips() {
        Helpers.setTranslatedToolTip(manos_checkbox, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(manos_label, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(manos_spinner, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(think_time_checkbox, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(think_time_label, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(think_time_spinner, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(showdown_time_label, "tooltip.cfg.showdown_time");
        Helpers.setTranslatedToolTip(showdown_time_spinner, "tooltip.cfg.showdown_time");
        Helpers.setTranslatedToolTip(iwtsth_checkbox, "tooltip.cfg.iwtsth");
        Helpers.setTranslatedToolTip(iwtsth_label, "tooltip.cfg.iwtsth");
        Helpers.setTranslatedToolTip(rit_checkbox, "tooltip.cfg.rit");
        Helpers.setTranslatedToolTip(rit_label, "tooltip.cfg.rit");
        Helpers.setTranslatedToolTip(rabbit_combo, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(rabbit_label, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(estructura_label, "tooltip.cfg.structure");
        Helpers.setTranslatedToolTip(estructura_combobox, "tooltip.cfg.structure");
        Helpers.setTranslatedToolTip(ciegas_label, "tooltip.cfg.blinds_level");
        Helpers.setTranslatedToolTip(ciegas_combobox, "tooltip.cfg.blinds_level");
        Helpers.setTranslatedToolTip(doblar_checkbox, "tooltip.cfg.double_blinds");
        Helpers.setTranslatedToolTip(blind_cap_checkbox, "tooltip.cfg.blind_cap");
        Helpers.setTranslatedToolTip(blind_cap_spinner, "tooltip.cfg.blind_cap");
        Helpers.setTranslatedToolTip(ante_checkbox, "tooltip.cfg.ante");
        Helpers.setTranslatedToolTip(straddle_checkbox, "tooltip.cfg.straddle");
        Helpers.setTranslatedToolTip(straddle_label, "tooltip.cfg.straddle");
        Helpers.setTranslatedToolTip(bots_label, "tooltip.cfg.bots");
        Helpers.setTranslatedToolTip(bots_combobox, "tooltip.cfg.bots");
        Helpers.setTranslatedToolTip(bot_rebuy_checkbox, "tooltip.cfg.bot_rebuy");
        Helpers.setTranslatedToolTip(bot_balance_checkbox, "tooltip.cfg.bot_balance");
        Helpers.setTranslatedToolTip(buyin_label, "tooltip.cfg.buyin");
        Helpers.setTranslatedToolTip(buyin_spinner, "tooltip.cfg.buyin");
        Helpers.setTranslatedToolTip(fixed_buyin_checkbox, "tooltip.cfg.buyin_fixed");
        Helpers.setTranslatedToolTip(buyin_range_label, "tooltip.cfg.buyin_range");
        Helpers.setTranslatedToolTip(buyin_min_bb_spinner, "tooltip.cfg.buyin_range");
        Helpers.setTranslatedToolTip(buyin_max_bb_spinner, "tooltip.cfg.buyin_range");
        Helpers.setTranslatedToolTip(rebuy_checkbox, "tooltip.rebuy_description");
        Helpers.setTranslatedToolTip(recomprar_label, "tooltip.rebuy_description");
        Helpers.setTranslatedToolTip(rebuy_limit_checkbox, "tooltip.cfg.rebuy_limit");
        Helpers.setTranslatedToolTip(rebuy_limit_spinner, "tooltip.cfg.rebuy_limit");
        Helpers.setTranslatedToolTip(rebuy_cap_label, "rebuy.tope_recompra_tooltip");
        Helpers.setTranslatedToolTip(rebuy_cap_combo, "rebuy.tope_recompra_tooltip");
    }

    /**
     * Builds the panel and seeds every control from the current game state.
     *
     * @param read_only {@code true} to build a client (read-only) view
     */
    public GameSettingsPanel(boolean read_only) {
        this.read_only = read_only;
        initComponents();

        setupTooltips();

        // ============================ BLINDS ============================
        // The levels combo always reflects the effective ladder (the active structure, or
        // the default ladder if none), not the designer's fixed list, so it includes every
        // level from defaultLevels().
        {
            double[][] levels = GameFrame.ACTIVE_BLIND_STRUCTURE != null
                    ? GameFrame.ACTIVE_BLIND_STRUCTURE : BlindStructure.defaultLevels();
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
            updateAnteStraddleLabels();
        });

        // Blind structure: reflects the active one and lets the user PICK another
        // already-saved one during the game (not create). Changing it repopulates the
        // levels combo.
        initStructureCombo();
        estructura_combobox.addActionListener((java.awt.event.ActionEvent e) -> {
            if (init) {
                applySelectedStructure();
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
        this.blind_cap_label.setEnabled(ciegas_double > 0 && GameFrame.BLIND_CAP > 0f);

        // ============================ GAME (rules) ============================
        int mano_actual = GameFrame.getInstance().getCrupier().getMano();
        int manos_min = Math.max(1, mano_actual + 1);
        boolean manos_on = GameFrame.MANOS != -1;
        manos_spinner.setModel(new SpinnerNumberModel(manos_on ? Math.max(GameFrame.MANOS, manos_min) : Math.max(100, manos_min), manos_min, null, 1));
        Helpers.makeNumericSpinnerEditable(manos_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) manos_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        manos_checkbox.setSelected(manos_on);
        manos_spinner.setEnabled(manos_on);

        // Think time: READ-ONLY in-game (not changeable once started). Shows the current
        // value and is DISABLED for both host and client (excluded from apply).
        think_time_checkbox.setSelected(GameFrame.THINK_TIME_ENABLED);
        think_time_spinner.setModel(new SpinnerNumberModel(Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, GameFrame.THINK_TIME)), GameFrame.THINK_TIME_MIN, GameFrame.THINK_TIME_MAX, 5));
        Helpers.makeNumericSpinnerEditable(think_time_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) think_time_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        think_time_checkbox.setEnabled(false);
        think_time_spinner.setEnabled(false);

        // Showdown time: READ-ONLY in-game (not changeable once started). Shows the current
        // value and is DISABLED (excluded from any apply). No checkbox: not disable-able.
        showdown_time_spinner.setModel(new SpinnerNumberModel(Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, GameFrame.SHOWDOWN_TIME)), GameFrame.SHOWDOWN_TIME_MIN, GameFrame.SHOWDOWN_TIME_MAX, 5));
        Helpers.makeNumericSpinnerEditable(showdown_time_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) showdown_time_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        showdown_time_spinner.setEnabled(false);

        iwtsth_checkbox.setSelected(GameFrame.IWTSTH_RULE);
        rit_checkbox.setSelected(GameFrame.RUN_IT_TWICE);

        rabbit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("menu.off"),
            Translator.translate("menu.free"),
            Translator.translate("menu.free_sb"),
            Translator.translate("menu.free_sb_bb")
        }));
        rabbit_combo.setSelectedIndex(Math.min(Math.max(GameFrame.RABBIT_HUNTING, 0), 3));

        ciegas_panel.setOpaque(false);

        // Rules (hand limit, IWTSTH, RIT, rabbit) go in a "Misc" subpanel.
        rules_panel.setOpaque(false);

        // Bots: difficulty, "rebuy bots" and "balance payout among humans" are EDITABLE in-game
        // (difficulty is read live on every decision -- perBotDifficulty is never fixed;
        // rebuy is read when a bot busts; balance payout only affects the final settlement).
        // For the CLIENT everything is read-only (bots belong to the host): disabled by the
        // read_only block below.
        bots_panel.setOpaque(false);
        bots_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("ui.bots_facil"), Translator.translate("ui.bots_media"), Translator.translate("ui.bots_dificil")}));
        bots_combobox.setSelectedIndex(Bot.DIFFICULTY == Bot.Difficulty.EASY ? 0 : (Bot.DIFFICULTY == Bot.Difficulty.HARD ? 2 : 1));
        bot_rebuy_checkbox.setSelected(GameFrame.BOT_REBUY);
        // "Rebuy bots" only applies if rebuy is active (REBUY, fixed in-game); otherwise moot.
        bot_rebuy_checkbox.setEnabled(GameFrame.REBUY);
        bot_balance_checkbox.setSelected(GameFrame.BOT_BALANCE_TO_HUMANS);

        // Buy-in + rebuy: INFO-ONLY (everything fixed once the game starts). Populated with the
        // current config and disabled entirely (host and client).
        compra_panel.setOpaque(false);
        buyin_spinner.setModel(new SpinnerNumberModel(Math.max(1, GameFrame.BUYIN), 1, null, 1));
        fixed_buyin_checkbox.setSelected(GameFrame.FIXED_BUYIN);
        buyin_min_bb_spinner.setModel(new SpinnerNumberModel(Math.max(1, GameFrame.BUYIN_MIN_BB), 1, null, 1));
        buyin_max_bb_spinner.setModel(new SpinnerNumberModel(Math.max(1, GameFrame.BUYIN_MAX_BB), 1, null, 1));
        rebuy_checkbox.setSelected(GameFrame.REBUY);
        rebuy_limit_checkbox.setSelected(GameFrame.REBUY_LIMIT > 0);
        if (GameFrame.REBUY_LIMIT > 0) {
            rebuy_limit_spinner.setModel(new SpinnerNumberModel(GameFrame.REBUY_LIMIT, 1, null, 1));
        }
        rebuy_cap_combo.setSelectedIndex(GameFrame.REBUY_CAP_POLICY == GameFrame.REBUY_CAP_HIGHEST_STACK ? 1 : 0);
        for (javax.swing.JComponent comp : new javax.swing.JComponent[]{buyin_label, buyin_spinner, fixed_buyin_checkbox, buyin_range_label, buyin_min_bb_spinner, buyin_range_sep_label, buyin_max_bb_spinner, rebuy_checkbox, recomprar_label, rebuy_limit_checkbox, rebuy_limit_spinner, rebuy_cap_label, rebuy_cap_combo}) {
            comp.setEnabled(false);
        }

        Helpers.translateComponents(this, false);

        // Current ante (= small blind) and straddle (= 2x big blind) amounts shown in
        // parentheses, same as the new-game dialog; refreshed when the blind level changes
        // (combo listener).
        updateAnteStraddleLabels();

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
            blind_cap_label.setEnabled(false);
            ante_checkbox.setEnabled(false);
            straddle_checkbox.setEnabled(false);
            manos_checkbox.setEnabled(false);
            manos_spinner.setEnabled(false);
            iwtsth_checkbox.setEnabled(false);
            rit_checkbox.setEnabled(false);
            rabbit_combo.setEnabled(false);
            estructura_combobox.setEnabled(false);
            // Client: bots belong to the host, so difficulty/rebuy/balance-payout are read-only.
            bots_combobox.setEnabled(false);
            bot_rebuy_checkbox.setEnabled(false);
            bot_balance_checkbox.setEnabled(false);
        } else if (GameFrame.RUN_IT_TWICE_LOCKED) {
            rit_checkbox.setEnabled(false);
        }

        snap_signature = controlsSignature();
    }

    // Compact signature of ALL editable controls; comparing two signatures tells if
    // anything changed. (Disabling controls doesn't change their values, so it's stable.)
    private String controlsSignature() {
        return manos_checkbox.isSelected() + "|" + manos_spinner.getValue() + "|"
                + iwtsth_checkbox.isSelected() + "|" + rit_checkbox.isSelected() + "|"
                + rabbit_combo.getSelectedIndex() + "|" + ciegas_combobox.getSelectedIndex() + "|"
                + doblar_checkbox.isSelected() + "|" + double_blinds_radio_minutos.isSelected() + "|"
                + double_blinds_radio_manos.isSelected() + "|" + doblar_ciegas_spinner_minutos.getValue() + "|"
                + doblar_ciegas_spinner_manos.getValue() + "|" + blind_cap_checkbox.isSelected() + "|"
                + blind_cap_spinner.getValue() + "|" + ante_checkbox.isSelected() + "|"
                + straddle_checkbox.isSelected() + "|"
                + String.valueOf(estructura_combobox.getSelectedItem()) + "|"
                + bot_balance_checkbox.isSelected() + "|"
                + bots_combobox.getSelectedIndex() + "|" + bot_rebuy_checkbox.isSelected();
    }

    /**
     * Whether the Game tab has unsaved changes. Used by the dialog to confirm before
     * discarding on cancel.
     *
     * @return {@code true} if any control differs from its value when the tab was opened
     */
    public boolean isDirty() {
        return !controlsSignature().equals(snap_signature);
    }

    /**
     * @return {@code true} if this panel was built read-only (client view)
     */
    public boolean isReadOnly() {
        return read_only;
    }

    // Wraps a text JLabel + a text-less ToggleSwitch into one compact transparent row (label on
    // the left, toggle on the right), to drop in where a self-text checkbox used to sit.
    private javax.swing.JPanel toggleRow(javax.swing.JLabel label, javax.swing.JComponent toggle) {
        // Capped to its natural height: otherwise the label's unbounded maximum height lets the
        // GroupLayout stretch this row vertically, opening a big gap between stacked rows (bots).
        javax.swing.JPanel row = new javax.swing.JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setLayout(new javax.swing.BoxLayout(row, javax.swing.BoxLayout.X_AXIS));
        label.setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
        toggle.setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
        row.add(label);
        row.add(javax.swing.Box.createHorizontalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));
        row.add(toggle);
        return row;
    }

    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM)));

        // Both subpanels sit side by side in a GridBagLayout row (rules | blinds, see 'grid'
        // below); we leave their MAXIMUM height unbounded (only the width is capped) so that
        // fill=BOTH can stretch the SHORTER of the two up to the taller one's height, keeping
        // their titled borders aligned at the bottom too. Any leftover space falls INSIDE the
        // shorter one's border (its content stays pinned at the top). maxWidth is inherited
        // unchanged (doesn't affect the row's horizontal split).
        rules_panel = new javax.swing.JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(super.getMaximumSize().width, Short.MAX_VALUE);
            }
        };
        manos_checkbox = new SettingsUI.ToggleSwitch(false);
        manos_label = new javax.swing.JLabel();
        manos_spinner = new javax.swing.JSpinner();
        think_time_checkbox = new SettingsUI.ToggleSwitch(false);
        think_time_label = new javax.swing.JLabel();
        think_time_spinner = new javax.swing.JSpinner();
        showdown_time_label = new javax.swing.JLabel();
        showdown_time_spinner = new javax.swing.JSpinner();
        iwtsth_checkbox = new SettingsUI.ToggleSwitch(false);
        iwtsth_label = new javax.swing.JLabel();
        rit_checkbox = new SettingsUI.ToggleSwitch(false);
        rit_label = new javax.swing.JLabel();
        rabbit_label = new javax.swing.JLabel();
        rabbit_combo = new javax.swing.JComboBox<>();

        // Same idea as rules_panel: unbounded maximum height so that, if it ends up the
        // shorter one in the row, it stretches to match the taller one and both titled
        // borders stay aligned at the bottom.
        ciegas_panel = new javax.swing.JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(super.getMaximumSize().width, Short.MAX_VALUE);
            }
        };
        estructura_label = new javax.swing.JLabel();
        estructura_combobox = new javax.swing.JComboBox<>();
        ciegas_label = new javax.swing.JLabel();
        ciegas_combobox = new javax.swing.JComboBox<>();
        doblar_checkbox = new SettingsUI.ToggleSwitch(false);
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        blind_cap_panel = new javax.swing.JPanel();
        blind_cap_checkbox = new SettingsUI.ToggleSwitch(false);
        blind_cap_spinner = new javax.swing.JSpinner();
        blind_cap_label = new javax.swing.JLabel();
        ante_checkbox = new SettingsUI.ToggleSwitch(false);
        straddle_checkbox = new SettingsUI.ToggleSwitch(false);
        straddle_label = new javax.swing.JLabel();

        bots_panel = new javax.swing.JPanel();
        bots_avatar_label = new javax.swing.JLabel();
        bots_label = new javax.swing.JLabel();
        bots_combobox = new javax.swing.JComboBox<>();
        bot_rebuy_checkbox = new SettingsUI.ToggleSwitch(false);
        bot_balance_checkbox = new SettingsUI.ToggleSwitch(false);

        compra_panel = new javax.swing.JPanel();
        buyin_label = new javax.swing.JLabel();
        buyin_spinner = new javax.swing.JSpinner();
        fixed_buyin_checkbox = new SettingsUI.ToggleSwitch(false);
        buyin_range_label = new javax.swing.JLabel();
        buyin_min_bb_spinner = new javax.swing.JSpinner();
        buyin_range_sep_label = new javax.swing.JLabel();
        buyin_max_bb_spinner = new javax.swing.JSpinner();
        rebuy_checkbox = new SettingsUI.ToggleSwitch(false);
        recomprar_label = new javax.swing.JLabel();
        rebuy_limit_checkbox = new SettingsUI.ToggleSwitch(false);
        rebuy_limit_spinner = new javax.swing.JSpinner();
        rebuy_cap_label = new javax.swing.JLabel();
        rebuy_cap_combo = new javax.swing.JComboBox<>();

        // ---------------- Rules (left side, no subpanel) ----------------
        manos_label.setFont(new java.awt.Font("Dialog", 1, 16));
        manos_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png")));
        manos_label.setText("Límite de manos:");
        manos_label.putClientProperty("i18n.key", "game.limite_de_manos");
        manos_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                manos_checkbox.doClick();
            }
        });

        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        think_time_label.setFont(new java.awt.Font("Dialog", 1, 16));
        think_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png")));
        think_time_label.setText("Tiempo de pensar:");
        think_time_label.putClientProperty("i18n.key", "newgame.tiempo_pensar");

        think_time_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        think_time_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        think_time_spinner.setModel(new javax.swing.SpinnerNumberModel(40, 10, 120, 5));
        think_time_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        showdown_time_label.setFont(new java.awt.Font("Dialog", 1, 16));
        showdown_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png")));
        showdown_time_label.setText("Tiempo de showdown:");
        showdown_time_label.putClientProperty("i18n.key", "newgame.tiempo_showdown");

        showdown_time_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        showdown_time_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 5, 30, 5));
        showdown_time_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        iwtsth_label.setFont(new java.awt.Font("Dialog", 1, 16));
        iwtsth_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/eyes.png")));
        iwtsth_label.setText("Regla IWTSTH");
        iwtsth_label.putClientProperty("i18n.key", "menu.regla_iwtsth");
        iwtsth_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        iwtsth_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                iwtsth_checkbox.doClick();
            }
        });

        iwtsth_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rit_label.setFont(new java.awt.Font("Dialog", 1, 16));
        rit_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")));
        rit_label.setText("ALL-IN Run-it-twice");
        rit_label.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rit_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rit_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                rit_checkbox.doClick();
            }
        });

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
                    // Hand limit / think time / showdown time share three columns
                    // (checkbox | label | spinner) so the three spinners line up on their left
                    // edge (same as in the waiting room). Showdown has no checkbox: it leaves
                    // the gap and aligns its label with the other two.
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(manos_checkbox)
                            .addComponent(think_time_checkbox))
                        .addGap(0, 0, 0)
                        .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(manos_label)
                            .addComponent(think_time_label)
                            .addComponent(showdown_time_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                        .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(think_time_checkbox)
                    .addComponent(think_time_label)
                    .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(showdown_time_label)
                    .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iwtsth_checkbox)
                    .addComponent(iwtsth_label))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rit_checkbox)
                    .addComponent(rit_label))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rabbit_label)
                    .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Blinds (right side, titled subpanel) ----------------
        estructura_label.setFont(new java.awt.Font("Dialog", 1, 14));
        estructura_label.setText("Estructura:");
        estructura_label.putClientProperty("i18n.key", "blinds.estructura");

        estructura_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        estructura_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16));
        ciegas_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png")));
        ciegas_label.setText("Ciegas iniciales:");
        ciegas_label.putClientProperty("i18n.key", "blinds.ciegas_iniciales");

        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,10 / 0,20" : "0.10 / 0.20", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,20 / 0,40" : "0.20 / 0.40", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,30 / 0,60" : "0.30 / 0.60", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,50 / 1" : "0.50 / 1", "1 / 2", "2 / 4", "3 / 6", "5 / 10", "10 / 20", "20 / 40", "30 / 60", "50 / 100", "100 / 200", "200 / 400", "300 / 600", "500 / 1000", "1000 / 2000", "2000 / 4000", "3000 / 6000", "5000 / 10000"}));
        ciegas_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.JLabel doblar_label = new javax.swing.JLabel("Aumentar ciegas");
        doblar_label.setFont(new java.awt.Font("Dialog", 1, 16));
        doblar_label.putClientProperty("i18n.key", "blinds.aumentar_ciegas");
        javax.swing.JPanel doblar_row = toggleRow(doblar_label, doblar_checkbox);
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

        blind_cap_panel.setBorder(new RoundedLineBorder(new java.awt.Color(210, 210, 210), 1, 12));
        blind_cap_panel.setOpaque(false);

        javax.swing.JLabel blind_cap_text = new javax.swing.JLabel("Tope ciega grande");
        blind_cap_text.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_text.putClientProperty("i18n.key", "blinds.tope_ciega_grande");
        javax.swing.JPanel blind_cap_row = toggleRow(blind_cap_text, blind_cap_checkbox);
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
                .addComponent(blind_cap_row)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(100 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(blind_cap_label))
                .addContainerGap())
        );
        blind_cap_panelLayout.setVerticalGroup(
            blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(blind_cap_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(blind_cap_row)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blind_cap_label)
                .addContainerGap())
        );

        ante_label = new javax.swing.JLabel("Ante");
        ante_label.setFont(new java.awt.Font("Dialog", 1, 16));
        javax.swing.JPanel ante_row = toggleRow(ante_label, ante_checkbox);
        ante_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        straddle_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        // Straddle control styled like the other rule icons (text-less checkbox + label
        // with icon and text). Icon is straddle.png downscaled to menu size (24px).
        straddle_label.setFont(new java.awt.Font("Dialog", 1, 16));
        straddle_label.setIcon(new javax.swing.ImageIcon(new javax.swing.ImageIcon(getClass().getResource("/images/straddle_small.png")).getImage().getScaledInstance(24, 24, java.awt.Image.SCALE_SMOOTH)));
        straddle_label.setText("Straddle");
        javax.swing.JPanel straddle_row = toggleRow(straddle_label, straddle_checkbox);
        straddle_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        straddle_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                straddle_checkbox.doClick();
            }
        });

        javax.swing.GroupLayout ciegas_panelLayout = new javax.swing.GroupLayout(ciegas_panel);
        ciegas_panel.setLayout(ciegas_panelLayout);
        ciegas_panelLayout.setHorizontalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(estructura_label)
                            .addComponent(ciegas_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(estructura_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(ciegas_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(doblar_row)
                        .addGap(0, 0, Short.MAX_VALUE))
                    // "Increase blinds" sub-options (hands/minutes + cap), indented
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addGap(Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM))
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(ciegas_panelLayout.createSequentialGroup()
                                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(double_blinds_radio_minutos)
                                    .addComponent(double_blinds_radio_manos))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(blind_cap_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(ante_row)
                        .addGap(Math.round(48 * Helpers.DIALOG_ZOOM), Math.round(48 * Helpers.DIALOG_ZOOM), Math.round(48 * Helpers.DIALOG_ZOOM))
                        .addComponent(straddle_row)))
                .addContainerGap())
        );
        ciegas_panelLayout.setVerticalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(estructura_label)
                    .addComponent(estructura_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ciegas_label)
                    .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(doblar_row)
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
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ante_row)
                    .addComponent(straddle_row))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Buy-in + rebuy (INFO-ONLY in-game) ----------------
        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png")));
        buyin_label.setText("Compra inicial:");
        buyin_label.putClientProperty("i18n.key", "blinds.compra_inicial");

        buyin_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, null, 1));

        javax.swing.JLabel fixed_buyin_label = new javax.swing.JLabel("Buy-in fijo");
        fixed_buyin_label.setFont(new java.awt.Font("Dialog", 1, 16));
        fixed_buyin_label.putClientProperty("i18n.key", "newgame.buyin_fijo");
        javax.swing.JPanel fixed_buyin_row = toggleRow(fixed_buyin_label, fixed_buyin_checkbox);

        buyin_range_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_range_label.setText("Rango compra (CG):");
        buyin_range_label.putClientProperty("i18n.key", "blinds.rango_compra");

        buyin_min_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_min_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, null, 1));

        buyin_range_sep_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_range_sep_label.setText("a");
        buyin_range_sep_label.putClientProperty("i18n.key", "blinds.rango_a");

        buyin_max_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_max_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));

        rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));

        recomprar_label.setFont(new java.awt.Font("Dialog", 1, 16));
        recomprar_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png")));
        recomprar_label.setText("Recomprar");
        recomprar_label.putClientProperty("i18n.key", "rebuy.recomprar_2");

        javax.swing.JLabel rebuy_limit_label = new javax.swing.JLabel("Límite recompra por jugador");
        rebuy_limit_label.setFont(new java.awt.Font("Dialog", 1, 14));
        rebuy_limit_label.putClientProperty("i18n.key", "rebuy.limite_por_jugador");
        javax.swing.JPanel rebuy_limit_row = toggleRow(rebuy_limit_label, rebuy_limit_checkbox);

        rebuy_limit_spinner.setFont(new java.awt.Font("Dialog", 0, 14));
        rebuy_limit_spinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, null, 1));

        rebuy_cap_label.setFont(new java.awt.Font("Dialog", 1, 14));
        rebuy_cap_label.setText("Tope recompra:");
        rebuy_cap_label.putClientProperty("i18n.key", "rebuy.tope_recompra");

        rebuy_cap_combo.setFont(new java.awt.Font("Dialog", 0, 14));
        rebuy_cap_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("rebuy.cap_policy_buyin"), Translator.translate("rebuy.cap_policy_highest")}));

        javax.swing.GroupLayout compra_panelLayout = new javax.swing.GroupLayout(compra_panel);
        compra_panel.setLayout(compra_panelLayout);
        compra_panelLayout.setHorizontalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(buyin_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM))
                        .addComponent(fixed_buyin_row))
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(buyin_range_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_range_sep_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                    // "Rebuy" = header; its sub-options (limit + cap) go indented
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(recomprar_label))
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addGap(Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM))
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addComponent(rebuy_limit_row)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addComponent(rebuy_cap_label)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        compra_panelLayout.setVerticalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(buyin_label)
                    .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fixed_buyin_row))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_range_label)
                    .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buyin_range_sep_label)
                    .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rebuy_checkbox)
                    .addComponent(recomprar_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_limit_row)
                    .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_cap_label)
                    .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Bots (subpanel, below rules|blinds) ----------------
        Helpers.setScaledIconLabel(bots_avatar_label, getClass().getResource("/images/avatar_bot.png"), 48, 48);

        bots_label.setFont(new java.awt.Font("Dialog", 1, 16));
        bots_label.setText("Dificultad de los bots:");
        bots_label.putClientProperty("i18n.key", "ui.bots_dificultad");

        bots_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        bots_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.JLabel bot_rebuy_label = new javax.swing.JLabel("Recomprar bots");
        bot_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 16));
        bot_rebuy_label.putClientProperty("i18n.key", "rebuy.permitir_bots");
        javax.swing.JPanel bot_rebuy_row = toggleRow(bot_rebuy_label, bot_rebuy_checkbox);
        bot_rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.JLabel bot_balance_label = new javax.swing.JLabel("Repartir saldo de bots entre humanos");
        bot_balance_label.setFont(new java.awt.Font("Dialog", 1, 16));
        bot_balance_label.putClientProperty("i18n.key", "balance.repartir_saldo_bots");
        javax.swing.JPanel bot_balance_row = toggleRow(bot_balance_label, bot_balance_checkbox);
        bot_balance_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout bots_panelLayout = new javax.swing.GroupLayout(bots_panel);
        bots_panel.setLayout(bots_panelLayout);
        bots_panelLayout.setHorizontalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(bots_panelLayout.createSequentialGroup()
                        .addComponent(bots_avatar_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bots_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(bot_rebuy_row)
                    .addComponent(bot_balance_row))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        bots_panelLayout.setVerticalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(bots_avatar_label)
                    .addComponent(bots_label)
                    .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bot_rebuy_row)
                .addGap(Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM))
                .addComponent(bot_balance_row)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // 2x2 grid with the SAME order and layout as the "Game" tab of the WAITING ROOM
        // (WaitingGameSettingsPanel): Buy-in | Blinds / Misc(rules) | Bots. GridBagLayout ties
        // each column's width ACROSS the two rows (left column identical in Buy-in and Misc;
        // right column identical in Blinds and Bots), and fill=BOTH stretches each subpanel to
        // match its row neighbor's height, keeping the titled borders aligned. weighty 0 keeps
        // each row at its natural height and lets the dialog's leftover vertical space fall
        // cleanly BELOW (at CENTER).
        javax.swing.JPanel grid = new javax.swing.JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.fill = java.awt.GridBagConstraints.BOTH;
        gc.weightx = 0.5;
        gc.weighty = 0.0;

        // Each section wrapped in a rounded SettingsUI.card instead of its old TitledBorder.
        javax.swing.JPanel compra_card = SettingsUI.card("newgame.grupo_compra");
        compra_card.add(compra_panel);
        javax.swing.JPanel ciegas_card = SettingsUI.card("newgame.grupo_ciegas");
        ciegas_card.add(ciegas_panel);
        javax.swing.JPanel rules_card = SettingsUI.card("settings.varios");
        rules_card.add(rules_panel);
        javax.swing.JPanel bots_card = SettingsUI.card("newgame.grupo_bots");
        bots_card.add(bots_panel);

        gc.gridx = 0;
        gc.gridy = 0;
        gc.insets = new java.awt.Insets(0, 0, 8, 6);
        grid.add(compra_card, gc);
        gc.gridx = 1;
        gc.insets = new java.awt.Insets(0, 6, 8, 0);
        grid.add(ciegas_card, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.insets = new java.awt.Insets(0, 0, 0, 6);
        grid.add(rules_card, gc);
        gc.gridx = 1;
        gc.insets = new java.awt.Insets(0, 6, 0, 0);
        grid.add(bots_card, gc);

        add(grid, java.awt.BorderLayout.NORTH);

        // i18n for icon labels (translated in translateComponents).
        manos_label.putClientProperty("i18n.key", "game.limite_de_manos");
        iwtsth_label.putClientProperty("i18n.key", "menu.regla_iwtsth");
        rit_label.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");
    }

    /**
     * Applies pending Game-tab changes (rules + blinds) to the live game. Triggered by the
     * unified dialog's SAVE button; does nothing when {@link #isReadOnly()}.
     */
    public void applyToGame() {

        if (read_only) {
            return;
        }

        // ---- Chosen blind structure (null = default ladder). Set BEFORE the blinds (the
        // levels combo already reflects this structure) and propagated to clients inside
        // UPDATEBLINDS; the dealer reads it directly for the automatic raise. ----
        double[][] new_structure = selectedStructureLevels();
        boolean structure_changed = !java.util.Arrays.deepEquals(new_structure, GameFrame.ACTIVE_BLIND_STRUCTURE);
        GameFrame.ACTIVE_BLIND_STRUCTURE = new_structure;
        final String structure_str = (new_structure != null) ? BlindStructure.levelsToString(new_structure) : "";

        // ---- Blinds (identical to EditBlindsDialog) ----
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
        final boolean blind_cap_changed = Helpers.doubleSecureCompare(GameFrame.BLIND_CAP, blind_cap) != 0;
        GameFrame.BLIND_CAP = blind_cap;

        boolean ante_nuevo = this.ante_checkbox.isSelected();
        boolean straddle_nuevo = this.straddle_checkbox.isSelected();
        final boolean ante_straddle_changed = GameFrame.ANTE != ante_nuevo || GameFrame.STRADDLE != straddle_nuevo;

        // Ante/straddle are still applied and broadcast immediately (further below, in
        // UPDATEBLINDS); if they change, the deferred notice is also flagged so the yellow
        // indicator and popup show up on the next hand, same as with blinds.
        if (ante_straddle_changed) {
            GameFrame.getInstance().getCrupier().marcarCambioAnteStraddle();
        }

        GameFrame.ANTE = ante_nuevo;
        GameFrame.STRADDLE = straddle_nuevo;

        String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

        GameFrame.getInstance().getCrupier().actualizarCiegasManualmente(Double.valueOf(valores_ciegas[0].trim()), Double.valueOf(valores_ciegas[1].trim()), ciegas_double, ciegas_double_type);

        // ---- Hand limit: same semantics as CommunityCardsPanel.click_max_hands ----
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
            GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("UPDATEBLINDS#" + String.valueOf(ciegas_double_f) + "#" + String.valueOf(ciegas_double_type_f) + "#" + sb + "#" + bb + "#" + String.valueOf(blind_cap_f) + "#" + String.valueOf(GameFrame.ANTE) + "#" + String.valueOf(GameFrame.STRADDLE) + "#" + structure_str, null);
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

        // Bots (editable in-game). Difficulty: server-local (bots belong to the host), read live;
        // not broadcast, persisted on recover. Rebuy and balance-payout: setters that broadcast + persist.
        Bot.Difficulty new_diff = bots_combobox.getSelectedIndex() == 0 ? Bot.Difficulty.EASY
                : (bots_combobox.getSelectedIndex() == 2 ? Bot.Difficulty.HARD : Bot.Difficulty.MEDIUM);
        boolean diff_changed = new_diff != Bot.DIFFICULTY;
        if (diff_changed) {
            Bot.DIFFICULTY = new_diff;
        }
        if (bot_rebuy_checkbox.isSelected() != GameFrame.BOT_REBUY) {
            GameFrame.setBotRebuy(bot_rebuy_checkbox.isSelected());
        }
        if (bot_balance_checkbox.isSelected() != GameFrame.BOT_BALANCE_TO_HUMANS) {
            GameFrame.setBotBalanceToHumans(bot_balance_checkbox.isSelected());
        }
        // Recover fossil: everything serializeRecoverSettings includes must be persisted so it
        // survives a stop+recover cycle. Four rules (blind cap, ante, straddle, hand limit)
        // used to be editable in-game but reverted to their old value on recover, while their
        // seven siblings in this same method already persisted correctly.
        //
        // Runs OFF the EDT and in a SINGLE write: persisting right here hits the database from
        // the SAVE button, which is exactly the path where the EDT blocks while the game is
        // being stopped. Same pattern as the rebuy/balance-payout persistence above.
        final boolean recover_settings_changed = diff_changed || structure_changed
                || blind_cap_changed || ante_straddle_changed || manos_changed;

        if (recover_settings_changed) {
            Helpers.threadRun(()
                    -> GameFrame.persistRecoverSettings(GameFrame.getInstance().getCrupier().getSqlite_game_id()));
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
            this.blind_cap_label.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
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
            this.blind_cap_label.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
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

    // Informational ante/straddle text with its CURRENT amount in parentheses (ante = small
    // blind, straddle = 2x big blind), read from the selected blind level. Same approach as
    // NewGameDialog.updateAnteStraddleLabels().
    private void updateAnteStraddleLabels() {
        Object sel = ciegas_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        String[] v = ((String) sel).replace(",", ".").split("/");
        if (v.length < 2) {
            return;
        }
        try {
            double sb = Double.valueOf(v[0].trim());
            double bb = Double.valueOf(v[1].trim());
            ante_label.setText("Ante (" + Helpers.money2String(sb) + ")");
            straddle_label.setText("Straddle (" + Helpers.money2String(Helpers.doubleClean(2 * bb)) + ")");
        } catch (NumberFormatException ignored) {
        }
    }

    // Initializes the structure selector from the ACTIVE one, without touching the levels
    // combo (already populated in the constructor). If the active one doesn't match any
    // saved structure, represents it with a synthetic "(current)" item so it isn't lost.
    private void initStructureCombo() {
        pending_structure = null;
        item_estructura_actual = null;
        String selectName = null;
        double[][] active = GameFrame.ACTIVE_BLIND_STRUCTURE;
        if (active != null) {
            for (java.util.Map.Entry<String, BlindStructure> en : BlindStructure.loadAll().entrySet()) {
                if (java.util.Arrays.deepEquals(en.getValue().getLevels(), active)) {
                    pending_structure = en.getValue();
                    selectName = en.getKey();
                    break;
                }
            }
            if (pending_structure == null) {
                try {
                    pending_structure = new BlindStructure(Translator.translate("blinds.estructura_actual"), active);
                    item_estructura_actual = pending_structure.getName();
                    actual_structure = pending_structure;
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        populateStructureCombo(selectName);
    }

    // (Re)fills the combo: "Default" + (unsaved active structure, if any) + saved
    // structures. Does NOT include "Manage...": this is pick-only.
    private void populateStructureCombo(String selectName) {
        boolean prev_init = init;
        init = false;
        try {
            item_estructura_por_defecto = Translator.translate("blinds.estructura_por_defecto");
            estructura_combobox.removeAllItems();
            estructura_combobox.addItem(item_estructura_por_defecto);
            // Unless a saved one already has that exact name: the combo would show the entry
            // twice and the saved one would become unselectable.
            if (item_estructura_actual != null && !BlindStructure.loadAll().containsKey(item_estructura_actual)) {
                estructura_combobox.addItem(item_estructura_actual);
            }
            for (String name : BlindStructure.loadAll().keySet()) {
                estructura_combobox.addItem(name);
            }
            if (selectName != null) {
                estructura_combobox.setSelectedItem(selectName);
            } else if (item_estructura_actual != null) {
                estructura_combobox.setSelectedItem(item_estructura_actual);
            } else {
                estructura_combobox.setSelectedItem(item_estructura_por_defecto);
            }
        } finally {
            init = prev_init;
        }
    }

    // Applies the chosen structure to the LEVELS combo, keeping the current STEP by
    // POSITION (not by value): if the game is at level N of the old ladder (combo index N
    // = number of blind raises since level one), the combo jumps to level N of the new
    // structure, capped to the last one if it's shorter. This keeps the same number of
    // jumps rather than the exact value; the user can adjust the level by hand before
    // saving. Sets pending_structure.
    private void applySelectedStructure() {
        Object sel = estructura_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        // Current step in the ladder the combo shows NOW (before repopulating).
        int prev_index = Math.max(0, ciegas_combobox.getSelectedIndex());
        double[][] levels;
        if (sel.equals(item_estructura_por_defecto)) {
            pending_structure = null;
            levels = BlindStructure.defaultLevels();
        } else {
            // Saved structures take priority over the synthetic entry (see the waiting-room
            // twin): if one exists with that name, it's the one the combo shows, so it's
            // resolved by name first and only falls back to the in-use ladder when none exists.
            BlindStructure bs = BlindStructure.loadAll().get((String) sel);

            if (bs == null && item_estructura_actual != null && sel.equals(item_estructura_actual)) {
                bs = actual_structure;
            }

            pending_structure = bs;
            levels = bs != null ? bs.getLevels() : BlindStructure.defaultLevels();
        }
        String[] items = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
        }
        int target = Math.min(prev_index, levels.length - 1);
        boolean prev_init = init;
        init = false;
        try {
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
            ciegas_combobox.setSelectedIndex(target);
        } finally {
            init = prev_init;
        }
        // Recompute the big-blind cap for the new ladder + labels.
        modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());
        updateAnteStraddleLabels();
    }

    // Levels of the CHOSEN structure (null = default ladder), applied to
    // GameFrame.ACTIVE_BLIND_STRUCTURE on save.
    private double[][] selectedStructureLevels() {
        return pending_structure != null ? pending_structure.getLevels() : null;
    }

}
