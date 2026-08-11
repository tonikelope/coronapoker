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

import javax.swing.DefaultComboBoxModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * "Game settings" content for the WAITING ROOM, as a tab of the unified settings dialog. Lets
 * the HOST configure the game before it starts: blinds + rules (like the live Game tab), plus
 * buy-in, rebuy, bots and presets, none of which can change mid-game.
 *
 * <p>Unlike {@link GameSettingsPanel} (the live Game tab, which operates on the Crupier and
 * broadcasts changes immediately), there is no Crupier or GameFrame instance here: the panel
 * works directly on the static {@code GameFrame.*} fields. {@link GamePreset.Settings} is the
 * single carrier: the HOST populates it via {@link GamePreset.Settings#fromGameFrame()} and,
 * on SAVE, writes it back with {@link GamePreset.Settings#applyToGameFrame(boolean)} and
 * broadcasts the full config to clients (mirror). For CLIENTS the panel is READ-ONLY, populated
 * from the received mirror ({@link WaitingRoomFrame#GAMECONFIG_MIRROR}).
 *
 * <p>The blinds/buy-in/structure/preset logic is the (Crupier-free) logic from NewGameDialog,
 * ported here; the blinds/misc layout mirrors {@link GameSettingsPanel}.
 *
 * @author tonikelope
 */
public class WaitingGameSettingsPanel extends javax.swing.JPanel {

    private volatile boolean init = false;

    private final boolean read_only;

    // Partial recover mode (host reattaching a stopped game to admit players): the economy
    // (buy-in/blinds/structure/ante/straddle) stays LOCKED to the recovered values, but the
    // "Game" settings (rules + think time), rebuy, and bot difficulty ARE editable. Only applies
    // when read_only is false (host).
    private final boolean recover;

    // Signature of the control values when the tab OPENS; isDirty() compares it against the
    // current one to tell whether there are unsaved changes (applied on SAVE).
    private String snap_signature;

    // Chosen blind structure (null = default ladder). Drives the blinds combo's levels and,
    // on SAVE, GameFrame.ACTIVE_BLIND_STRUCTURE.
    private BlindStructure pending_structure = null;
    private String item_por_defecto;
    private String item_gestionar;
    // Synthetic "(current)" entry for the active ladder when it doesn't match any saved one (came
    // from a preset, or from a structure that was edited/deleted afterwards). Without it the combo
    // would show "Default" while the game actually ran on a custom ladder — same mechanism as the
    // twin panel on the start screen.
    private String item_estructura_actual;
    private BlindStructure actual_structure;

    // Buy-in spinner step (derived from the small blind's magnitude).
    private int buyin_spinner_step = 1;
    private static final int BUYIN_RANGE_STEP = 5;
    private boolean adjusting_buyin_range = false;

    // LOCAL working store for the buy-in range (min/max BB) and the rebuy-cap policy. These used
    // to be scratch-written directly into the static GameFrame.BUYIN_MIN_BB/MAX_BB/REBUY_CAP_POLICY,
    // which broke the transactional model: touching the spinners mutated global state on the fly
    // and Cancel ("discard changes") never reverted it. Now the live logic operates on these
    // fields and GameFrame is only written on SAVE (captureSettingsFromControls).
    private int working_min_bb;
    private int working_max_bb;
    private int working_rebuy_cap_policy;

    private static final int MAX_PRESET_NAME_LENGTH = 40;
    private boolean suppress_preset_combo = false;

    // ---------------- Rules (left, "Misc" subpanel) ----------------
    private javax.swing.JPanel rules_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JLabel manos_label;
    private javax.swing.JSpinner manos_spinner;
    private javax.swing.JCheckBox think_time_checkbox;
    private javax.swing.JLabel think_time_label;
    private javax.swing.JSpinner think_time_spinner;
    // Showdown time: no checkbox (the pause can't be disabled).
    private javax.swing.JLabel showdown_time_label;
    private javax.swing.JSpinner showdown_time_spinner;
    private javax.swing.JCheckBox iwtsth_checkbox;
    private javax.swing.JLabel iwtsth_label;
    private javax.swing.JCheckBox rit_checkbox;
    private javax.swing.JLabel rit_label;
    private javax.swing.JLabel rabbit_label;
    private javax.swing.JComboBox<String> rabbit_combo;

    // ---------------- Blinds (right, titled subpanel) ----------------
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
    private javax.swing.JPanel aumento_panel;
    private javax.swing.JCheckBox blind_cap_checkbox;
    private javax.swing.JSpinner blind_cap_spinner;
    private javax.swing.JLabel blind_cap_label;
    private javax.swing.JCheckBox ante_checkbox;
    private javax.swing.JLabel ante_label;
    private javax.swing.JCheckBox straddle_checkbox;
    private javax.swing.JLabel straddle_label;

    // ---------------- Buy-in + rebuy ----------------
    private javax.swing.JPanel compra_panel;
    private javax.swing.JCheckBox fixed_buyin_checkbox;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JLabel buyin_range_label;
    private javax.swing.JSpinner buyin_min_bb_spinner;
    private javax.swing.JLabel buyin_range_sep_label;
    private javax.swing.JSpinner buyin_max_bb_spinner;
    private javax.swing.JPanel recompra_panel;
    private javax.swing.JLabel recomprar_label;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JCheckBox rebuy_limit_checkbox;
    private javax.swing.JSpinner rebuy_limit_spinner;
    private javax.swing.JCheckBox bot_rebuy_checkbox;
    private javax.swing.JCheckBox bot_balance_checkbox;
    private javax.swing.JLabel rebuy_cap_label;
    private javax.swing.JComboBox<String> rebuy_cap_combo;

    // ---------------- Bots ----------------
    private javax.swing.JPanel bots_panel;
    private javax.swing.JLabel bots_avatar_label;
    private javax.swing.JLabel bots_label;
    private javax.swing.JComboBox<String> bots_combobox;

    // ---------------- Presets ----------------
    private javax.swing.JPanel presets_panel;
    private javax.swing.JLabel preset_label;
    private javax.swing.JComboBox<String> presets_combobox;
    private javax.swing.JButton preset_save_button;
    private javax.swing.JButton preset_delete_button;

    // i18n tooltips (setTranslatedToolTip re-translates them on language change) for controls
    // whose purpose isn't obvious from their label alone. Called after initComponents().
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
        Helpers.setTranslatedToolTip(bot_rebuy_checkbox, "tooltip.cfg.bot_rebuy");
        Helpers.setTranslatedToolTip(bot_balance_checkbox, "tooltip.cfg.bot_balance");
        Helpers.setTranslatedToolTip(bots_label, "tooltip.cfg.bots");
        Helpers.setTranslatedToolTip(bots_combobox, "tooltip.cfg.bots");
        // rebuy_cap_combo's tooltip is re-applied in initBuyinRangeAndCapUI, whose combo rebuild
        // would otherwise leave it tooltip-less.
    }

    public WaitingGameSettingsPanel(boolean read_only, boolean recover) {
        this.read_only = read_only;
        this.recover = recover;
        initComponents();

        setupTooltips();

        // Single source of truth: the HOST reads the live config (statics); the client reads the
        // mirror received over the network (may be null if the wheel opens before the first
        // sync, falling back to defaults). applySettingsToControls populates ALL controls.
        applySettingsToControls(read_only
                ? GamePreset.Settings.parse(WaitingRoomFrame.GAMECONFIG_MIRROR)
                : GamePreset.Settings.fromGameFrame());

        ciegas_panel.setOpaque(false);
        rules_panel.setOpaque(false);
        compra_panel.setOpaque(false);
        bots_panel.setOpaque(false);

        Helpers.translateComponents(this, false);
        updateAnteStraddleLabels();

        // Presets: the combo is always populated (even though it stays hidden in read-only mode).
        populatePresetsCombo(null);

        init = true;

        if (read_only) {
            applyReadOnlyState();
        } else if (recover) {
            applyRecoverLockState();
        }

        snap_signature = controlsSignature();
    }

    public boolean isReadOnly() {
        return read_only;
    }

    /**
     * Whether there are unsaved changes. Used by the dialog to confirm before discarding on
     * Cancel. Never dirty in read-only mode (its controls never change).
     *
     * @return {@code true} if the controls differ from the snapshot taken on open
     */
    public boolean isDirty() {
        return !read_only && !controlsSignature().equals(snap_signature);
    }

    // Compact signature of ALL editable controls; comparing two signatures says whether
    // anything changed. (Disabling controls doesn't change their values, so it stays stable.)
    private String controlsSignature() {
        return manos_checkbox.isSelected() + "|" + manos_spinner.getValue() + "|"
                + think_time_checkbox.isSelected() + "|" + think_time_spinner.getValue() + "|"
                + showdown_time_spinner.getValue() + "|"
                + iwtsth_checkbox.isSelected() + "|" + rit_checkbox.isSelected() + "|"
                + rabbit_combo.getSelectedIndex() + "|" + ciegas_combobox.getSelectedIndex() + "|"
                + doblar_checkbox.isSelected() + "|" + double_blinds_radio_minutos.isSelected() + "|"
                + double_blinds_radio_manos.isSelected() + "|" + doblar_ciegas_spinner_minutos.getValue() + "|"
                + doblar_ciegas_spinner_manos.getValue() + "|" + blind_cap_checkbox.isSelected() + "|"
                + blind_cap_spinner.getValue() + "|" + ante_checkbox.isSelected() + "|"
                + straddle_checkbox.isSelected() + "|" + String.valueOf(estructura_combobox.getSelectedItem()) + "|"
                + fixed_buyin_checkbox.isSelected() + "|" + buyin_spinner.getValue() + "|"
                + buyin_min_bb_spinner.getValue() + "|" + buyin_max_bb_spinner.getValue() + "|"
                + rebuy_checkbox.isSelected() + "|" + rebuy_limit_checkbox.isSelected() + "|"
                + rebuy_limit_spinner.getValue() + "|" + bot_rebuy_checkbox.isSelected() + "|"
                + bot_balance_checkbox.isSelected() + "|"
                + rebuy_cap_combo.getSelectedIndex() + "|" + bots_combobox.getSelectedIndex();
    }

    /**
     * Refreshes a client's (read-only) panel with the mirror just received over the network.
     * Triggered by {@code WaitingRoomFrame} processing a GAMECONFIG, via {@code SettingsDialog}.
     */
    public void refreshFromMirror() {
        if (!read_only) {
            return;
        }
        applySettingsToControls(GamePreset.Settings.parse(WaitingRoomFrame.GAMECONFIG_MIRROR));
        updateAnteStraddleLabels();
        applyReadOnlyState();
    }

    // ===================== Apply (triggered by SAVE in the unified dialog) =====================
    /**
     * Only the HOST applies: writes the static {@code GameFrame.*} fields (like the old UPDATE
     * branch) and broadcasts the full config to clients (mirror) + refreshes the room's labels.
     */
    public void applyToGame() {
        if (read_only) {
            return;
        }

        if (recover) {
            // Recover: ONLY the EDITABLE settings (Game + bots) are applied. The economy stays
            // whatever the recovered game had (its controls are locked).
            applyRecoverEditableToGame();
        } else {
            GamePreset.Settings s = captureSettingsFromControls();
            s.applyToGameFrame(true);
        }

        WaitingRoomFrame room = WaitingRoomFrame.getInstance();
        if (room != null) {
            room.broadcastGameConfigAndLabels();
        }
    }

    // Recover: applies to GameFrame ONLY the editable settings ("Game" subpanel + bot
    // difficulty), without touching the economy (restored from the recovered game). Clears the
    // *_RECOVER overrides for iwtsth/rit/rabbit so a re-save uses the edited value, not the
    // original one.
    private void applyRecoverEditableToGame() {
        GameFrame.MANOS = manos_checkbox.isSelected() ? ((Number) manos_spinner.getValue()).intValue() : -1;
        GameFrame.THINK_TIME = Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, ((Number) think_time_spinner.getValue()).intValue()));
        GameFrame.THINK_TIME_ENABLED = think_time_checkbox.isSelected();
        GameFrame.SHOWDOWN_TIME = Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, ((Number) showdown_time_spinner.getValue()).intValue()));
        GameFrame.IWTSTH_RULE = iwtsth_checkbox.isSelected();
        GameFrame.RUN_IT_TWICE = rit_checkbox.isSelected();
        GameFrame.RABBIT_HUNTING = rabbit_combo.getSelectedIndex();
        GameFrame.IWTSTH_RULE_RECOVER = null;
        GameFrame.RUN_IT_TWICE_RECOVER = null;
        GameFrame.RABBIT_HUNTING_RECOVER = null;
        // Rebuy (editable on recover): allow / per-player limit / bot rebuy / cap.
        GameFrame.REBUY = rebuy_checkbox.isSelected();
        GameFrame.REBUY_LIMIT = rebuy_limit_checkbox.isSelected() ? ((Number) rebuy_limit_spinner.getValue()).intValue() : 0;
        GameFrame.BOT_REBUY = bot_rebuy_checkbox.isSelected();
        GameFrame.BOT_BALANCE_TO_HUMANS = bot_balance_checkbox.isSelected();
        GameFrame.REBUY_CAP_POLICY = rebuy_cap_combo.getSelectedIndex() == 1 ? GameFrame.REBUY_CAP_HIGHEST_STACK : GameFrame.REBUY_CAP_BUYIN;
        // "Allow rebuy" doesn't travel in recover_settings: it's persisted to game.rebuy so a
        // resume doesn't revert the edit (see GameFrame.persistRecoverRebuy). Synchronous on the
        // EDT: this SAVE runs in the WAITING ROOM (before the game resumes), so no Crupier or
        // finTransmision worker is holding SQL_LOCK across a blocking-EDT call and no EDT<->lock
        // cycle can form. (The in-game GameSettingsPanel sibling offloads instead, because once the
        // game is running there ARE such concurrent lock holders.)
        GameFrame.persistRecoverRebuy(GameFrame.RECOVER_ID, GameFrame.REBUY);
        Bot.DIFFICULTY = botDifficultyFromComboIndex(bots_combobox.getSelectedIndex());
    }

    // Disables all controls (client) and hides the presets panel. Re-applied after every mirror
    // refresh, since applySettingsToControls re-enables things.
    private void applyReadOnlyState() {
        boolean e = false;
        manos_checkbox.setEnabled(e);
        manos_spinner.setEnabled(e);
        think_time_checkbox.setEnabled(e);
        think_time_spinner.setEnabled(e);
        showdown_time_spinner.setEnabled(e);
        iwtsth_checkbox.setEnabled(e);
        rit_checkbox.setEnabled(e);
        rabbit_combo.setEnabled(e);
        estructura_combobox.setEnabled(e);
        ciegas_combobox.setEnabled(e);
        doblar_checkbox.setEnabled(e);
        double_blinds_radio_minutos.setEnabled(e);
        double_blinds_radio_manos.setEnabled(e);
        doblar_ciegas_spinner_minutos.setEnabled(e);
        doblar_ciegas_spinner_manos.setEnabled(e);
        blind_cap_checkbox.setEnabled(e);
        blind_cap_spinner.setEnabled(e);
        blind_cap_label.setEnabled(e);
        ante_checkbox.setEnabled(e);
        straddle_checkbox.setEnabled(e);
        fixed_buyin_checkbox.setEnabled(e);
        buyin_spinner.setEnabled(e);
        buyin_min_bb_spinner.setEnabled(e);
        buyin_max_bb_spinner.setEnabled(e);
        rebuy_checkbox.setEnabled(e);
        recomprar_label.setEnabled(e);
        rebuy_limit_checkbox.setEnabled(e);
        rebuy_limit_spinner.setEnabled(e);
        bot_rebuy_checkbox.setEnabled(e);
        bot_balance_checkbox.setEnabled(e);
        rebuy_cap_label.setEnabled(e);
        rebuy_cap_combo.setEnabled(e);
        bots_label.setEnabled(e);
        bots_combobox.setEnabled(e);
        presets_panel.setVisible(false);
    }

    // Partial recover (host): locks ONLY the economy (buy-in/range/blinds/structure/increase/
    // cap/ante/straddle), leaving editable the "Game" settings (hand limit, IWTSTH, run-it-twice,
    // rabbit, think time), rebuy, and bot difficulty, all already set correctly by
    // applySettingsToControls. Hides the presets panel (doesn't apply when recovering).
    private void applyRecoverLockState() {
        boolean e = false;
        estructura_combobox.setEnabled(e);
        ciegas_combobox.setEnabled(e);
        doblar_checkbox.setEnabled(e);
        double_blinds_radio_minutos.setEnabled(e);
        double_blinds_radio_manos.setEnabled(e);
        doblar_ciegas_spinner_minutos.setEnabled(e);
        doblar_ciegas_spinner_manos.setEnabled(e);
        blind_cap_checkbox.setEnabled(e);
        blind_cap_spinner.setEnabled(e);
        blind_cap_label.setEnabled(e);
        ante_checkbox.setEnabled(e);
        straddle_checkbox.setEnabled(e);
        fixed_buyin_checkbox.setEnabled(e);
        buyin_spinner.setEnabled(e);
        buyin_min_bb_spinner.setEnabled(e);
        buyin_max_bb_spinner.setEnabled(e);
        // Rebuy (allow / limit / bot rebuy / cap) stays EDITABLE on recover: it is NOT locked
        // here; its enabled state is set by applySettingsToControls from "allow rebuy" and kept
        // in sync by rebuy_checkboxActionPerformed.
        presets_panel.setVisible(false);
    }

    // ===================== UI construction =====================
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
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

        rules_panel = new javax.swing.JPanel();
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

        ciegas_panel = new javax.swing.JPanel();
        estructura_label = new javax.swing.JLabel();
        estructura_combobox = new javax.swing.JComboBox<>();
        ciegas_label = new javax.swing.JLabel();
        ciegas_combobox = new javax.swing.JComboBox<>();
        doblar_checkbox = new SettingsUI.ToggleSwitch(false);
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        aumento_panel = new javax.swing.JPanel();
        blind_cap_checkbox = new SettingsUI.ToggleSwitch(false);
        blind_cap_spinner = new javax.swing.JSpinner();
        blind_cap_label = new javax.swing.JLabel();
        ante_checkbox = new SettingsUI.ToggleSwitch(false);
        straddle_checkbox = new SettingsUI.ToggleSwitch(false);
        straddle_label = new javax.swing.JLabel();

        compra_panel = new javax.swing.JPanel();
        fixed_buyin_checkbox = new SettingsUI.ToggleSwitch(false);
        buyin_label = new javax.swing.JLabel();
        buyin_spinner = new javax.swing.JSpinner();
        buyin_range_label = new javax.swing.JLabel();
        buyin_min_bb_spinner = new javax.swing.JSpinner();
        buyin_range_sep_label = new javax.swing.JLabel();
        buyin_max_bb_spinner = new javax.swing.JSpinner();
        recompra_panel = new javax.swing.JPanel();
        recomprar_label = new javax.swing.JLabel();
        rebuy_checkbox = new SettingsUI.ToggleSwitch(false);
        rebuy_limit_checkbox = new SettingsUI.ToggleSwitch(false);
        rebuy_limit_spinner = new javax.swing.JSpinner();
        bot_rebuy_checkbox = new SettingsUI.ToggleSwitch(false);
        bot_balance_checkbox = new SettingsUI.ToggleSwitch(false);
        rebuy_cap_label = new javax.swing.JLabel();
        rebuy_cap_combo = new javax.swing.JComboBox<>();

        bots_panel = new javax.swing.JPanel();
        bots_avatar_label = new javax.swing.JLabel();
        bots_label = new javax.swing.JLabel();
        bots_combobox = new javax.swing.JComboBox<>();

        presets_panel = new javax.swing.JPanel();
        preset_label = new javax.swing.JLabel();
        presets_combobox = new javax.swing.JComboBox<>();
        preset_save_button = new javax.swing.JButton();
        preset_delete_button = new javax.swing.JButton();

        // ---------------- Rules ("Misc" subpanel) ----------------
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
        manos_checkbox.addActionListener(this::manos_checkboxActionPerformed);

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        think_time_label.setFont(new java.awt.Font("Dialog", 1, 16));
        think_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png")));
        think_time_label.setText("Tiempo de pensar:");
        think_time_label.putClientProperty("i18n.key", "newgame.tiempo_pensar");
        think_time_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        think_time_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                think_time_checkbox.doClick();
            }
        });

        think_time_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        think_time_checkbox.addActionListener(this::think_time_checkboxActionPerformed);

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
                    // hand-limit/think-time/showdown share columns (checkbox | label | spinner)
                    // to line up the three spinners (same as room and game tabs). Showdown, with
                    // no checkbox, leaves the gap and aligns its label with the other two.
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
                            .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                .addGap(12, 12, 12)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(think_time_checkbox)
                    .addComponent(think_time_label)
                    .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(showdown_time_label)
                    .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iwtsth_checkbox)
                    .addComponent(iwtsth_label))
                .addGap(12, 12, 12)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rit_checkbox)
                    .addComponent(rit_label))
                .addGap(12, 12, 12)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rabbit_label)
                    .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Blinds (titled subpanel) ----------------
        estructura_label.setFont(new java.awt.Font("Dialog", 1, 14));
        estructura_label.setText("Estructura:");
        estructura_label.putClientProperty("i18n.key", "blinds.estructura");

        estructura_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        estructura_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        estructura_combobox.addActionListener(this::estructura_comboboxActionPerformed);

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16));
        ciegas_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png")));
        ciegas_label.setText("Ciegas iniciales:");
        ciegas_label.putClientProperty("i18n.key", "blinds.ciegas_iniciales");

        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        ciegas_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ciegas_combobox.addActionListener(this::ciegas_comboboxActionPerformed);

        javax.swing.JLabel doblar_label = new javax.swing.JLabel("Aumentar ciegas");
        doblar_label.setFont(new java.awt.Font("Dialog", 1, 16));
        doblar_label.putClientProperty("i18n.key", "blinds.aumentar_ciegas");
        javax.swing.JPanel doblar_row = toggleRow(doblar_label, doblar_checkbox);
        doblar_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_checkbox.addActionListener(this::doblar_checkboxActionPerformed);

        double_blinds_radio_manos.setFont(new java.awt.Font("Dialog", 1, 14));
        double_blinds_radio_manos.setText("Manos:");
        double_blinds_radio_manos.putClientProperty("i18n.key", "game.manos");
        double_blinds_radio_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_manos.addActionListener(this::double_blinds_radio_manosActionPerformed);

        doblar_ciegas_spinner_manos.setFont(new java.awt.Font("Dialog", 0, 16));
        doblar_ciegas_spinner_manos.setModel(new javax.swing.SpinnerNumberModel(30, 1, null, 1));
        doblar_ciegas_spinner_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        double_blinds_radio_minutos.setFont(new java.awt.Font("Dialog", 1, 14));
        double_blinds_radio_minutos.setText("Minutos:");
        double_blinds_radio_minutos.putClientProperty("i18n.key", "ui.minutos");
        double_blinds_radio_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_minutos.addActionListener(this::double_blinds_radio_minutosActionPerformed);

        doblar_ciegas_spinner_minutos.setFont(new java.awt.Font("Dialog", 0, 16));
        doblar_ciegas_spinner_minutos.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        doblar_ciegas_spinner_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        // Increase box (line border) that WRAPS the whole "Increase blinds" group (checkbox +
        // hands/minutes radios + spinners + big-blind cap), same as the new-game dialog (it used
        // to be that only the cap was boxed, with "Increase blinds" + radios left loose).
        aumento_panel.setBorder(new RoundedLineBorder(new java.awt.Color(210, 210, 210), 1, 12));
        aumento_panel.setOpaque(false);

        javax.swing.JLabel blind_cap_text = new javax.swing.JLabel("Tope ciega grande");
        blind_cap_text.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_text.putClientProperty("i18n.key", "blinds.tope_ciega_grande");
        javax.swing.JPanel blind_cap_row = toggleRow(blind_cap_text, blind_cap_checkbox);
        blind_cap_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_checkbox.addActionListener(this::blind_cap_checkboxActionPerformed);

        blind_cap_spinner.setFont(new java.awt.Font("Dialog", 0, 14));
        blind_cap_spinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, null, 1));
        blind_cap_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> updateBlindCapLabel());

        blind_cap_label.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_label.setText("0 / 0");

        javax.swing.GroupLayout aumento_panelLayout = new javax.swing.GroupLayout(aumento_panel);
        aumento_panel.setLayout(aumento_panelLayout);
        aumento_panelLayout.setHorizontalGroup(
            aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aumento_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    // "Increase blinds" = header (not indented); its sub-options are indented
                    .addComponent(doblar_row)
                    .addGroup(aumento_panelLayout.createSequentialGroup()
                        .addGap(22, 22, 22)
                        .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(double_blinds_radio_manos)
                            .addComponent(double_blinds_radio_minutos)
                            .addComponent(blind_cap_row))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(blind_cap_label)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        aumento_panelLayout.setVerticalGroup(
            aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aumento_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(doblar_row)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_manos)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_minutos)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
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

        // Straddle chip like the other rule icons (textless checkbox + label with icon and
        // text). The icon is straddle.png scaled down to the menu size (24px).
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
                    .addComponent(aumento_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                    .addComponent(estructura_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ciegas_label)
                    .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(aumento_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ante_row)
                    .addComponent(straddle_row))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Buy-in + rebuy ----------------
        javax.swing.JLabel fixed_buyin_label = new javax.swing.JLabel("Buy-in fijo");
        fixed_buyin_label.setFont(new java.awt.Font("Dialog", 1, 16));
        fixed_buyin_label.putClientProperty("i18n.key", "newgame.buyin_fijo");
        javax.swing.JPanel fixed_buyin_row = toggleRow(fixed_buyin_label, fixed_buyin_checkbox);
        fixed_buyin_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fixed_buyin_checkbox.addActionListener(this::fixed_buyin_checkboxActionPerformed);

        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png")));
        buyin_label.setText("Compra inicial:");
        buyin_label.putClientProperty("i18n.key", "blinds.compra_inicial");

        buyin_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, null, 1));
        buyin_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        buyin_range_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_range_label.setText("Rango compra (CG):");
        buyin_range_label.putClientProperty("i18n.key", "blinds.rango_compra");

        buyin_min_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_min_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 10, 500, 5));
        buyin_min_bb_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_min_bb_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> onBuyinRangeChanged(true));

        buyin_range_sep_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_range_sep_label.setText("a");
        buyin_range_sep_label.putClientProperty("i18n.key", "blinds.rango_a");

        buyin_max_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_max_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 10, 500, 5));
        buyin_max_bb_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_max_bb_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> onBuyinRangeChanged(false));

        // rebuy (line-border subpanel)
        recompra_panel.setBorder(new RoundedLineBorder(new java.awt.Color(210, 210, 210), 1, 12));
        recompra_panel.setOpaque(false);

        // BARE checkbox (no text) + "Rebuy" label with icon next to it, same as the new-game
        // dialog: setIcon on the checkbox breaks the checkbox rendering, so the icon+text live in
        // a separate label that clicks the checkbox when pressed.
        rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_checkbox.addActionListener(this::rebuy_checkboxActionPerformed);

        recomprar_label.setFont(new java.awt.Font("Dialog", 1, 16));
        recomprar_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png")));
        recomprar_label.setText("Recomprar");
        recomprar_label.putClientProperty("i18n.key", "rebuy.recomprar_2");
        recomprar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recomprar_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                rebuy_checkbox.doClick();
            }
        });

        javax.swing.JLabel rebuy_limit_label = new javax.swing.JLabel("Límite recompra por jugador");
        rebuy_limit_label.setFont(new java.awt.Font("Dialog", 1, 14));
        rebuy_limit_label.putClientProperty("i18n.key", "rebuy.limite_por_jugador");
        javax.swing.JPanel rebuy_limit_row = toggleRow(rebuy_limit_label, rebuy_limit_checkbox);
        rebuy_limit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_limit_checkbox.addActionListener(this::rebuy_limit_checkboxActionPerformed);

        rebuy_limit_spinner.setFont(new java.awt.Font("Dialog", 0, 14));
        rebuy_limit_spinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, null, 1));
        rebuy_limit_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.JLabel bot_rebuy_label = new javax.swing.JLabel("Recomprar bots");
        bot_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 14));
        bot_rebuy_label.putClientProperty("i18n.key", "rebuy.permitir_bots");
        javax.swing.JPanel bot_rebuy_row = toggleRow(bot_rebuy_label, bot_rebuy_checkbox);
        bot_rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.JLabel bot_balance_label = new javax.swing.JLabel("Repartir saldo de bots entre humanos");
        bot_balance_label.setFont(new java.awt.Font("Dialog", 1, 14));
        bot_balance_label.putClientProperty("i18n.key", "balance.repartir_saldo_bots");
        javax.swing.JPanel bot_balance_row = toggleRow(bot_balance_label, bot_balance_checkbox);
        bot_balance_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rebuy_cap_label.setFont(new java.awt.Font("Dialog", 1, 14));
        rebuy_cap_label.setText("Tope recompra:");
        rebuy_cap_label.putClientProperty("i18n.key", "rebuy.tope_recompra");

        rebuy_cap_combo.setFont(new java.awt.Font("Dialog", 0, 14));
        rebuy_cap_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout recompra_panelLayout = new javax.swing.GroupLayout(recompra_panel);
        recompra_panel.setLayout(recompra_panelLayout);
        recompra_panelLayout.setHorizontalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    // "Rebuy" = block header (not indented)
                    .addGroup(recompra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(recomprar_label))
                    // "Rebuy" sub-options (limit + cap) indented to the right
                    .addGroup(recompra_panelLayout.createSequentialGroup()
                        .addGap(22, 22, 22)
                        .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(recompra_panelLayout.createSequentialGroup()
                                .addComponent(rebuy_limit_row)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(recompra_panelLayout.createSequentialGroup()
                                .addComponent(rebuy_cap_label)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap())
        );
        recompra_panelLayout.setVerticalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rebuy_checkbox)
                    .addComponent(recomprar_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_limit_row)
                    .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_cap_label)
                    .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout compra_panelLayout = new javax.swing.GroupLayout(compra_panel);
        compra_panel.setLayout(compra_panelLayout);
        compra_panelLayout.setHorizontalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(recompra_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(buyin_label)
                            .addComponent(buyin_range_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(buyin_spinner)
                            .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.DEFAULT_SIZE, 90, Short.MAX_VALUE))
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(buyin_range_sep_label)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addGap(18, 18, 18)
                                .addComponent(fixed_buyin_row)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        compra_panelLayout.setVerticalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(buyin_label)
                    .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fixed_buyin_row))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_range_label)
                    .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buyin_range_sep_label)
                    .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(recompra_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Bots ----------------
        // Bot portrait (as in new-game), scaled, to the left of the difficulty combo.
        Helpers.setScaledIconLabel(bots_avatar_label, getClass().getResource("/images/avatar_bot.png"), 48, 48);

        bots_label.setFont(new java.awt.Font("Dialog", 1, 16));
        bots_label.setText("Dificultad de los bots:");
        bots_label.putClientProperty("i18n.key", "ui.bots_dificultad");

        bots_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        bots_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        bots_combobox.setModel(new DefaultComboBoxModel<>(new String[]{
            Translator.translate("ui.bots_facil"),
            Translator.translate("ui.bots_media"),
            Translator.translate("ui.bots_dificil")
        }));

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

        // ---------------- Presets ----------------
        preset_label.setFont(new java.awt.Font("Dialog", 1, 16));
        preset_label.setText("Perfil de ajustes:");
        preset_label.putClientProperty("i18n.key", "newgame.preset_label");

        presets_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        presets_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        presets_combobox.addActionListener(this::presets_comboboxActionPerformed);

        preset_save_button.setFont(new java.awt.Font("Dialog", 1, 14));
        preset_save_button.setText("Guardar…");
        preset_save_button.putClientProperty("i18n.key", "newgame.preset_guardar");
        preset_save_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        preset_save_button.addActionListener(this::preset_save_buttonActionPerformed);

        preset_delete_button.setFont(new java.awt.Font("Dialog", 1, 14));
        preset_delete_button.setText("Borrar");
        preset_delete_button.putClientProperty("i18n.key", "newgame.preset_borrar");
        preset_delete_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        preset_delete_button.addActionListener(this::preset_delete_buttonActionPerformed);

        // The combo stretches to fill the width (grows to the edge and pushes Save/Delete to the
        // right), matching the new-game dialog's width; the old FlowLayout left it narrow with a
        // fixed prototype.
        javax.swing.GroupLayout presets_panelLayout = new javax.swing.GroupLayout(presets_panel);
        presets_panel.setLayout(presets_panelLayout);
        presets_panelLayout.setHorizontalGroup(
            presets_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(presets_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(preset_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(presets_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(preset_save_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(preset_delete_button)
                .addContainerGap())
        );
        presets_panelLayout.setVerticalGroup(
            presets_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(presets_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(presets_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(preset_label)
                    .addComponent(presets_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(preset_save_button)
                    .addComponent(preset_delete_button))
                .addContainerGap())
        );

        // ---------------- Assembly ----------------
        // 2x2 grid with the SAME order as the new-game dialog (CREATE GAME):
        //   Buy-in | Blinds
        //   Misc   | Bots
        // GridBagLayout ties each column's width ACROSS the two rows (left column identical for
        // Buy-in and Misc; right column identical for Blinds and Bots), so everything lines up
        // instead of each row computing its own cut (a separate BoxLayout per row used to measure
        // width independently and let "Misc" overhang "Buy-in"). fill BOTH: each subpanel FILLS
        // its cell (same height as its row neighbor), so the titled border reaches the bottom
        // with no empty gap under the shorter one (e.g. "Buy-in" under "Rebuy cap"). weighty 0
        // keeps the rows at their natural height, so leftover vertical space falls cleanly BELOW
        // the grid (goes to CENTER, not inside the panels).
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

        presets_panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        grid.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        javax.swing.JPanel north = new javax.swing.JPanel();
        north.setLayout(new javax.swing.BoxLayout(north, javax.swing.BoxLayout.Y_AXIS));
        north.add(presets_panel);
        north.add(javax.swing.Box.createVerticalStrut(8));
        north.add(grid);

        add(north, java.awt.BorderLayout.NORTH);
    }

    // ===================== Listeners (Crupier-free, ported from NewGameDialog) =====================
    private void manos_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        manos_spinner.setEnabled(manos_checkbox.isSelected());
    }

    private void think_time_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        think_time_spinner.setEnabled(think_time_checkbox.isSelected());
    }

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        doblar_ciegas_spinner_minutos.setEnabled(doblar_checkbox.isSelected() && double_blinds_radio_minutos.isSelected());
        doblar_ciegas_spinner_manos.setEnabled(doblar_checkbox.isSelected() && double_blinds_radio_manos.isSelected());
        double_blinds_radio_manos.setEnabled(doblar_checkbox.isSelected());
        double_blinds_radio_minutos.setEnabled(doblar_checkbox.isSelected());
        blind_cap_checkbox.setEnabled(doblar_checkbox.isSelected());
        setBlindCapControlsEnabled(doblar_checkbox.isSelected() && blind_cap_checkbox.isSelected());
    }

    private void double_blinds_radio_minutosActionPerformed(java.awt.event.ActionEvent evt) {
        if (double_blinds_radio_minutos.isSelected()) {
            doblar_ciegas_spinner_minutos.setEnabled(true);
            double_blinds_radio_manos.setSelected(false);
            doblar_ciegas_spinner_manos.setEnabled(false);
        } else {
            double_blinds_radio_minutos.setSelected(true);
        }
    }

    private void double_blinds_radio_manosActionPerformed(java.awt.event.ActionEvent evt) {
        if (double_blinds_radio_manos.isSelected()) {
            doblar_ciegas_spinner_manos.setEnabled(true);
            double_blinds_radio_minutos.setSelected(false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
        } else {
            double_blinds_radio_manos.setSelected(true);
        }
    }

    private void blind_cap_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        setBlindCapControlsEnabled(doblar_checkbox.isSelected() && blind_cap_checkbox.isSelected());
    }

    private void rebuy_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        rebuy_limit_checkbox.setEnabled(rebuy_checkbox.isSelected());
        rebuy_limit_spinner.setEnabled(rebuy_checkbox.isSelected() && rebuy_limit_checkbox.isSelected());
        bot_rebuy_checkbox.setEnabled(rebuy_checkbox.isSelected());
        rebuy_cap_label.setEnabled(rebuy_checkbox.isSelected());
        rebuy_cap_combo.setEnabled(rebuy_checkbox.isSelected());
    }

    private void rebuy_limit_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        rebuy_limit_spinner.setEnabled(rebuy_checkbox.isSelected() && rebuy_limit_checkbox.isSelected());
    }

    private void fixed_buyin_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        buyin_spinner.setEnabled(fixed_buyin_checkbox.isSelected());
    }

    private void ciegas_comboboxActionPerformed(java.awt.event.ActionEvent evt) {
        // Ante/straddle info label OUTSIDE the init gate (also when loading a preset/structure).
        // Buy-in/cap recalculation only happens inside init.
        updateAnteStraddleLabels();
        if (init) {
            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");
            double ciega_pequena = Double.valueOf(valores[0].trim());
            double ciega_grande = Double.valueOf(valores[1].trim());
            int buyin_lo_cg = BuyinRules.min(ciega_grande, working_min_bb);
            int buyin_hi_cg = Math.max(buyin_lo_cg, BuyinRules.max(ciega_grande, working_max_bb));
            buyin_spinner.setModel(new SpinnerNumberModel(BuyinRules.defaultBuyin(ciega_grande, working_min_bb, working_max_bb), buyin_lo_cg, buyin_hi_cg, (buyin_spinner_step = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(ciega_pequena)) + 1)))));
            Helpers.makeNumericSpinnerEditable(buyin_spinner, false);
            modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());
            revalidate();
            repaint();
        }
    }

    private void estructura_comboboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (!init) {
            return;
        }
        Object sel = estructura_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        if (sel.equals(item_gestionar)) {
            // Structure editor; on close, reload keeping the active one (fall back to
            // "Default" if it was deleted/renamed).
            String previous = pending_structure != null ? pending_structure.getName() : item_por_defecto;
            java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
            BlindStructureManagerDialog mgr = new BlindStructureManagerDialog(owner);
            mgr.setVisible(true);
            // The synthetic entry for the active ladder is NOT among the saved ones, but it's
            // still a valid selection: without this exception, opening and closing the manager
            // would lose it and leave the combo on "Default".
            if (!item_por_defecto.equals(previous)
                    && !previous.equals(item_estructura_actual)
                    && !BlindStructure.loadAll().containsKey(previous)) {
                previous = item_por_defecto;
            }
            populateStructureCombo(previous);
            applySelectedStructure();
            return;
        }
        applySelectedStructure();
    }

    // ===================== Blind structure =====================
    private void populateStructureCombo(String selectName) {
        boolean prev_init = init;
        init = false;
        try {
            item_por_defecto = Translator.translate("blinds.estructura_por_defecto");
            item_gestionar = Translator.translate("blinds.gestionar");
            estructura_combobox.removeAllItems();
            estructura_combobox.addItem(item_por_defecto);
            // Unless a saved structure already has that same name: the combo would show the
            // entry twice and the saved one would become unselectable.
            if (item_estructura_actual != null && !BlindStructure.loadAll().containsKey(item_estructura_actual)) {
                estructura_combobox.addItem(item_estructura_actual);
            }
            for (String name : BlindStructure.loadAll().keySet()) {
                estructura_combobox.addItem(name);
            }
            estructura_combobox.addItem(item_gestionar);
            estructura_combobox.setSelectedItem(selectName != null ? selectName : item_por_defecto);
            if (estructura_combobox.getSelectedItem() == null
                    || item_gestionar.equals(estructura_combobox.getSelectedItem())) {
                estructura_combobox.setSelectedItem(item_por_defecto);
            }
        } finally {
            init = prev_init;
        }
    }

    private void applySelectedStructure() {
        Object sel = estructura_combobox.getSelectedItem();
        double[][] levels;
        if (sel == null || sel.equals(item_por_defecto) || sel.equals(item_gestionar)) {
            pending_structure = null;
            levels = BlindStructure.defaultLevels();
        } else {
            // SAVED structures take priority over the synthetic entry: if one exists with that
            // name, it's the one the combo is showing (the synthetic entry isn't even added), so
            // resolve by name first and only fall back to the active ladder when none exists.
            // The other way around, a saved structure with the same name became unselectable.
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
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        ciegas_combobox.setSelectedIndex(0);
        // Recalculate buy-in + cap for the new ladder (setModel doesn't reliably fire the
        // listener).
        ciegas_comboboxActionPerformed(null);
    }

    // Reflects the active structure (parameter; null = default ladder) in the levels combo,
    // and selects/synthesizes its entry in the structures combo.
    private void initBlindStructureUIFrom(double[][] active) {
        pending_structure = null;
        item_estructura_actual = null;
        actual_structure = null;
        String selectName = null;
        if (active != null) {
            for (java.util.Map.Entry<String, BlindStructure> e : BlindStructure.loadAll().entrySet()) {
                if (java.util.Arrays.deepEquals(e.getValue().getLevels(), active)) {
                    pending_structure = e.getValue();
                    selectName = e.getKey();
                    break;
                }
            }
            if (pending_structure == null) {
                try {
                    pending_structure = new BlindStructure(Translator.translate("blinds.estructura_actual"), active);
                    // Remembered so it can be OFFERED in the combo: otherwise the dropdown would
                    // select "Default" and lie about the ladder actually being played.
                    item_estructura_actual = pending_structure.getName();
                    actual_structure = pending_structure;
                    selectName = item_estructura_actual;
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        // ALWAYS populate the combo from the effective ladder (the active structure or,
        // without one, the default), so it includes every level from defaultLevels() rather
        // than the designer's fixed list.
        double[][] levels = pending_structure != null ? pending_structure.getLevels() : BlindStructure.defaultLevels();
        String[] items = new String[levels.length];
        for (int k = 0; k < levels.length; k++) {
            items[k] = BlindStructure.formatLevel(levels[k][0], levels[k][1]);
        }
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        populateStructureCombo(selectName);
    }

    // ===================== Big-blind cap (number of increases) =====================
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

    private void setBlindCapControlsEnabled(boolean enabled) {
        blind_cap_spinner.setEnabled(enabled);
        blind_cap_label.setEnabled(enabled);
    }

    private int blindCapDoublingsFromCap(double cap) {
        int initial = Math.max(0, ciegas_combobox.getSelectedIndex());
        if (cap > 0f) {
            for (int k = initial + 1; k < ciegas_combobox.getModel().getSize(); k++) {
                if (Helpers.doubleSecureCompare(parseBlindLevelBB(ciegas_combobox.getItemAt(k)), cap) == 0) {
                    return k - initial;
                }
            }
        }
        return 5;
    }

    private void modelBlindCapSpinner(int n) {
        int levels_above = Math.max(1, ciegas_combobox.getModel().getSize() - 1 - Math.max(0, ciegas_combobox.getSelectedIndex()));
        n = Math.min(Math.max(1, n), levels_above);
        blind_cap_spinner.setModel(new SpinnerNumberModel(n, 1, levels_above, 1));
        Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
        updateBlindCapLabel();
    }

    // ===================== Buy-in (range + spinner) =====================
    private void initBuyinRangeAndCapUI() {
        int lo = Math.max(BuyinRules.FLOOR_MIN_BB, Math.min(working_min_bb, BuyinRules.CEIL_MAX_BB - BUYIN_RANGE_STEP));
        int hi = Math.max(lo + BUYIN_RANGE_STEP, Math.min(working_max_bb, BuyinRules.CEIL_MAX_BB));

        adjusting_buyin_range = true;
        try {
            buyin_min_bb_spinner.setModel(new SpinnerNumberModel(lo, BuyinRules.FLOOR_MIN_BB, BuyinRules.CEIL_MAX_BB, BUYIN_RANGE_STEP));
            buyin_max_bb_spinner.setModel(new SpinnerNumberModel(hi, BuyinRules.FLOOR_MIN_BB, BuyinRules.CEIL_MAX_BB, BUYIN_RANGE_STEP));
            Helpers.makeNumericSpinnerEditable(buyin_min_bb_spinner, false);
            Helpers.makeNumericSpinnerEditable(buyin_max_bb_spinner, false);
        } finally {
            adjusting_buyin_range = false;
        }

        working_min_bb = lo;
        working_max_bb = hi;

        rebuy_cap_combo.removeAllItems();
        rebuy_cap_combo.addItem(Translator.translate("rebuy.cap_policy_buyin"));
        rebuy_cap_combo.addItem(Translator.translate("rebuy.cap_policy_highest"));
        rebuy_cap_combo.setSelectedIndex(working_rebuy_cap_policy == GameFrame.REBUY_CAP_HIGHEST_STACK ? 1 : 0);
        Helpers.setTranslatedToolTip(rebuy_cap_combo, "rebuy.tope_recompra_tooltip");
    }

    private void rebuildBuyinSpinnerModel() {
        if (ciegas_combobox.getSelectedItem() == null) {
            return;
        }
        String[] v = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");
        double cp = Double.parseDouble(v[0].trim());
        double cg = Double.parseDouble(v[1].trim());
        int lo = BuyinRules.min(cg, working_min_bb);
        int hi = Math.max(lo, BuyinRules.max(cg, working_max_bb));
        int cur = ((Number) buyin_spinner.getValue()).intValue();
        int val = Math.max(lo, Math.min(cur, hi));
        buyin_spinner_step = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(cp)) + 1));
        buyin_spinner.setModel(new SpinnerNumberModel(val, lo, hi, buyin_spinner_step));
        Helpers.makeNumericSpinnerEditable(buyin_spinner, false);
    }

    private void onBuyinRangeChanged(boolean minChanged) {
        if (!init || adjusting_buyin_range) {
            return;
        }
        adjusting_buyin_range = true;
        try {
            int lo = ((Number) buyin_min_bb_spinner.getValue()).intValue();
            int hi = ((Number) buyin_max_bb_spinner.getValue()).intValue();
            if (lo >= hi) {
                if (minChanged) {
                    hi = Math.min(BuyinRules.CEIL_MAX_BB, lo + BUYIN_RANGE_STEP);
                    if (hi - BUYIN_RANGE_STEP < lo) {
                        lo = hi - BUYIN_RANGE_STEP;
                        buyin_min_bb_spinner.setValue(lo);
                    }
                    buyin_max_bb_spinner.setValue(hi);
                } else {
                    lo = Math.max(BuyinRules.FLOOR_MIN_BB, hi - BUYIN_RANGE_STEP);
                    if (lo + BUYIN_RANGE_STEP > hi) {
                        hi = lo + BUYIN_RANGE_STEP;
                        buyin_max_bb_spinner.setValue(hi);
                    }
                    buyin_min_bb_spinner.setValue(lo);
                }
            }
            working_min_bb = lo;
            working_max_bb = hi;
        } finally {
            adjusting_buyin_range = false;
        }
        rebuildBuyinSpinnerModel();
        revalidate();
        repaint();
    }

    // ===================== Ante / straddle (informational text) =====================
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

    // ===================== Bots =====================
    private Bot.Difficulty botDifficultyFromComboIndex(int idx) {
        switch (idx) {
            case 0:
                return Bot.Difficulty.EASY;
            case 2:
                return Bot.Difficulty.HARD;
            default:
                return Bot.Difficulty.MEDIUM;
        }
    }

    private int botComboIndexFromDifficulty(Bot.Difficulty d) {
        switch (d) {
            case EASY:
                return 0;
            case HARD:
                return 2;
            default:
                return 1;
        }
    }

    // ===================== Presets =====================
    private void populatePresetsCombo(String selectName) {
        suppress_preset_combo = true;
        try {
            presets_combobox.removeAllItems();
            presets_combobox.addItem(Translator.translate("newgame.preset_por_defecto"));
            for (String name : GamePreset.loadAll().keySet()) {
                presets_combobox.addItem(name);
            }
            if (selectName != null) {
                presets_combobox.setSelectedItem(selectName);
            }
            if (presets_combobox.getSelectedItem() == null) {
                presets_combobox.setSelectedIndex(0);
            }
            preset_delete_button.setEnabled(presets_combobox.getSelectedIndex() > 0);
        } finally {
            suppress_preset_combo = false;
        }
    }

    private void presets_comboboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (suppress_preset_combo) {
            return;
        }
        int idx = presets_combobox.getSelectedIndex();
        preset_delete_button.setEnabled(idx > 0);
        if (idx <= 0) {
            applySettingsToControls(new GamePreset.Settings());
            updateAnteStraddleLabels();
            return;
        }
        GamePreset preset = GamePreset.loadAll().get((String) presets_combobox.getSelectedItem());
        if (preset != null) {
            applySettingsToControls(GamePreset.Settings.parse(preset.getSettings()));
            updateAnteStraddleLabels();
        }
    }

    private void preset_save_buttonActionPerformed(java.awt.event.ActionEvent evt) {
        String name = promptPresetName();
        if (name == null) {
            return;
        }
        java.util.LinkedHashMap<String, GamePreset> all = GamePreset.loadAll();
        boolean exists = all.containsKey(name);
        if (exists) {
            if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("newgame.preset_sobrescribir", name)) != 0) {
                return;
            }
        } else if (all.size() >= GamePreset.MAX_PRESETS) {
            Helpers.mostrarMensajeError(this, Translator.translate("newgame.preset_limite", GamePreset.MAX_PRESETS));
            return;
        }
        all.put(name, new GamePreset(name, captureSettingsFromControls().serialize()));
        GamePreset.saveAll(all.values());
        populatePresetsCombo(name);
    }

    private void preset_delete_buttonActionPerformed(java.awt.event.ActionEvent evt) {
        int idx = presets_combobox.getSelectedIndex();
        if (idx <= 0) {
            return;
        }
        String name = (String) presets_combobox.getSelectedItem();
        if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("newgame.preset_confirmar_borrar", name)) != 0) {
            return;
        }
        java.util.LinkedHashMap<String, GamePreset> all = GamePreset.loadAll();
        all.remove(name);
        GamePreset.saveAll(all.values());
        populatePresetsCombo(null);
    }

    private String promptPresetName() {
        javax.swing.JLabel prompt = new javax.swing.JLabel(Translator.translate("newgame.preset_nombre"));
        javax.swing.JTextField field = new javax.swing.JTextField("", 18);
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        panel.add(prompt, java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);
        javax.swing.JOptionPane pane = new javax.swing.JOptionPane(panel, javax.swing.JOptionPane.PLAIN_MESSAGE, javax.swing.JOptionPane.OK_CANCEL_OPTION);
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        javax.swing.JDialog d = pane.createDialog(owner, Translator.translate("settings.ajustes"));
        Helpers.updateFonts(pane, Helpers.GUI_FONT, 1.15f);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        d.dispose();
        if (!Integer.valueOf(javax.swing.JOptionPane.OK_OPTION).equals(pane.getValue())) {
            return null;
        }
        String name = field.getText().trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > MAX_PRESET_NAME_LENGTH ? name.substring(0, MAX_PRESET_NAME_LENGTH) : name;
    }

    // ===================== Controls <-> Settings =====================
    private void selectCurrentBlindLevel(double sb, double bg) {
        String ciegas = BlindStructure.formatLevel(sb, bg);
        int t = ciegas_combobox.getModel().getSize();
        for (int i = 0; i < t; i++) {
            if (ciegas_combobox.getItemAt(i).equals(ciegas)) {
                ciegas_combobox.setSelectedIndex(i);
                return;
            }
        }
    }

    // Reads the controls into a Settings (without touching GameFrame). Same mapping as
    // NewGameDialog's old UPDATE branch / captureSettingsFromControls.
    private GamePreset.Settings captureSettingsFromControls() {
        GamePreset.Settings s = new GamePreset.Settings();
        String[] v = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");
        s.smallBlind = Double.parseDouble(v[0].trim());
        s.bigBlind = Double.parseDouble(v[1].trim());
        s.structure = pending_structure != null ? pending_structure.getLevels() : null;
        s.buyin = ((Number) buyin_spinner.getValue()).intValue();
        s.fixedBuyin = fixed_buyin_checkbox.isSelected();
        s.minBb = ((Number) buyin_min_bb_spinner.getValue()).intValue();
        s.maxBb = ((Number) buyin_max_bb_spinner.getValue()).intValue();
        s.rebuy = rebuy_checkbox.isSelected();
        s.rebuyLimit = rebuy_limit_checkbox.isSelected() ? ((Number) rebuy_limit_spinner.getValue()).intValue() : 0;
        s.botRebuy = bot_rebuy_checkbox.isSelected();
        s.botBalanceToHumans = bot_balance_checkbox.isSelected();
        s.rebuyCapPolicy = rebuy_cap_combo.getSelectedIndex() == 1 ? GameFrame.REBUY_CAP_HIGHEST_STACK : GameFrame.REBUY_CAP_BUYIN;
        if (doblar_checkbox.isSelected()) {
            if (double_blinds_radio_minutos.isSelected()) {
                s.doubleEvery = ((Number) doblar_ciegas_spinner_minutos.getValue()).intValue();
                s.doubleType = 1;
            } else {
                s.doubleEvery = ((Number) doblar_ciegas_spinner_manos.getValue()).intValue();
                s.doubleType = 2;
            }
        } else {
            s.doubleEvery = 0;
            s.doubleType = 1;
        }
        s.blindCap = blind_cap_checkbox.isSelected() ? blindCapSelectedBB() : 0;
        s.handLimit = manos_checkbox.isSelected() ? ((Number) manos_spinner.getValue()).intValue() : -1;
        s.thinkTime = ((Number) think_time_spinner.getValue()).intValue();
        s.thinkTimeEnabled = think_time_checkbox.isSelected();
        s.showdownTime = ((Number) showdown_time_spinner.getValue()).intValue();
        s.ante = ante_checkbox.isSelected();
        s.straddle = straddle_checkbox.isSelected();
        s.iwtsth = iwtsth_checkbox.isSelected();
        s.runItTwice = rit_checkbox.isSelected();
        s.rabbit = rabbit_combo.getSelectedIndex();
        s.difficulty = botDifficultyFromComboIndex(bots_combobox.getSelectedIndex());
        return s;
    }

    // Applies a Settings to the controls WITHOUT touching GameFrame (the buy-in range and cap
    // policy use the local working_* fields). Same order as NewGameDialog: toggles/enable, then
    // finally structure -> level -> buy-in/cap.
    private void applySettingsToControls(GamePreset.Settings s) {
        boolean prev_init = init;
        init = false;
        try {
            doblar_checkbox.setSelected(s.doubleEvery > 0);
            double_blinds_radio_minutos.setEnabled(s.doubleEvery > 0);
            double_blinds_radio_manos.setEnabled(s.doubleEvery > 0);
            if (s.doubleType <= 1) {
                doblar_ciegas_spinner_minutos.setEnabled(s.doubleEvery > 0);
                doblar_ciegas_spinner_minutos.setModel(new SpinnerNumberModel(s.doubleEvery > 0 ? s.doubleEvery : 60, 1, null, 1));
                doblar_ciegas_spinner_manos.setEnabled(false);
                double_blinds_radio_minutos.setSelected(true);
                double_blinds_radio_manos.setSelected(false);
            } else {
                doblar_ciegas_spinner_manos.setEnabled(s.doubleEvery > 0);
                doblar_ciegas_spinner_manos.setModel(new SpinnerNumberModel(s.doubleEvery > 0 ? s.doubleEvery : 60, 1, null, 1));
                doblar_ciegas_spinner_minutos.setEnabled(false);
                double_blinds_radio_minutos.setSelected(false);
                double_blinds_radio_manos.setSelected(true);
            }
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);

            manos_checkbox.setSelected(s.handLimit > 0);
            manos_spinner.setEnabled(s.handLimit > 0);
            manos_spinner.setModel(new SpinnerNumberModel(s.handLimit > 0 ? s.handLimit : 100, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(manos_spinner, false);
            ((javax.swing.JSpinner.DefaultEditor) manos_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);

            think_time_checkbox.setSelected(s.thinkTimeEnabled);
            think_time_spinner.setEnabled(s.thinkTimeEnabled);
            think_time_spinner.setValue(Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, s.thinkTime)));
            Helpers.makeNumericSpinnerEditable(think_time_spinner, false);

            // No checkbox (the pause is always on); the read-only state is set afterwards by
            // applyReadOnlyState (client) / applyRecoverLockState leaves it editable (host recover).
            showdown_time_spinner.setEnabled(true);
            showdown_time_spinner.setValue(Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, s.showdownTime)));
            Helpers.makeNumericSpinnerEditable(showdown_time_spinner, false);

            rebuy_checkbox.setSelected(s.rebuy);
            ante_checkbox.setSelected(s.ante);
            straddle_checkbox.setSelected(s.straddle);
            iwtsth_checkbox.setSelected(s.iwtsth);
            rit_checkbox.setSelected(s.runItTwice);
            rabbit_combo.setModel(new DefaultComboBoxModel<>(new String[]{
                Translator.translate("menu.off"),
                Translator.translate("menu.free"),
                Translator.translate("menu.free_sb"),
                Translator.translate("menu.free_sb_bb")
            }));
            rabbit_combo.setSelectedIndex(Math.min(Math.max(s.rabbit, 0), 3));
            bot_rebuy_checkbox.setSelected(s.botRebuy);
            bot_rebuy_checkbox.setEnabled(s.rebuy);
            bot_balance_checkbox.setSelected(s.botBalanceToHumans);

            fixed_buyin_checkbox.setSelected(s.fixedBuyin);
            buyin_spinner.setEnabled(s.fixedBuyin);

            // Buy-in range + cap policy: LOCAL working store (not GameFrame), to avoid breaking
            // the transactional model; GameFrame is only written on SAVE.
            working_min_bb = s.minBb;
            working_max_bb = s.maxBb;
            working_rebuy_cap_policy = s.rebuyCapPolicy;
            initBuyinRangeAndCapUI();

            rebuy_limit_checkbox.setSelected(s.rebuyLimit > 0);
            rebuy_limit_checkbox.setEnabled(s.rebuy);
            rebuy_limit_spinner.setEnabled(s.rebuy && s.rebuyLimit > 0);
            rebuy_limit_spinner.setModel(new SpinnerNumberModel(s.rebuyLimit > 0 ? s.rebuyLimit : 3, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(rebuy_limit_spinner, false);
            rebuy_cap_label.setEnabled(s.rebuy);
            rebuy_cap_combo.setEnabled(s.rebuy);

            blind_cap_checkbox.setSelected(s.blindCap > 0);
            blind_cap_checkbox.setEnabled(s.doubleEvery > 0);

            initBlindStructureUIFrom(s.structure);
            double[][] levels = s.structure != null ? s.structure : BlindStructure.defaultLevels();
            String[] items = new String[levels.length];
            for (int i = 0; i < levels.length; i++) {
                items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
            selectCurrentBlindLevel(s.smallBlind, s.bigBlind);

            rebuildBuyinSpinnerModel();
            SpinnerNumberModel bm = (SpinnerNumberModel) buyin_spinner.getModel();
            int blo = ((Number) bm.getMinimum()).intValue();
            int bhi = ((Number) bm.getMaximum()).intValue();
            buyin_spinner.setValue(Math.max(blo, Math.min(s.buyin, bhi)));

            modelBlindCapSpinner(blindCapDoublingsFromCap(s.blindCap));
            setBlindCapControlsEnabled(s.doubleEvery > 0 && s.blindCap > 0);

            bots_combobox.setSelectedIndex(botComboIndexFromDifficulty(s.difficulty));
        } finally {
            init = prev_init;
        }
    }
}
