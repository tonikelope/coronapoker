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

import com.tonikelope.coronapoker.Helpers.JTextFieldRegularPopupMenu;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;

/**
 * Dialog for creating a new local game (host + bots) or joining a remote one: collects
 * nick/avatar/password, blind structure and buy-in rules, rebuy/rabbit/IWTSTH/run-it-twice
 * settings, and optional game presets. Also drives the "recover previous game" flow, which
 * loads and locks the recovered game's economy while leaving its "Game" settings editable.
 *
 * @author tonikelope
 */
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public class NewGameDialog extends JDialog {

    public final static int DEFAULT_PORT = 7234;
    public final static int DEFAULT_AVATAR_WIDTH = 50;
    public final static int AVATAR_MAX_FILESIZE = 256; //KB
    public final static int MAX_NICK_LENGTH = 15;
    public final static int MAX_PASS_LENGTH = 30;
    public final static int MAX_PORT_LENGTH = 5;
    public static volatile int BUYIN_SPINNER_STEP;

    private final HashMap<String, HashMap<String, Object>> game = new HashMap<>();
    // Description ("server @ date") of the last recoverable game loaded (shown in
    // game_label), or null if none is loaded.
    private String last_game_key = null;
    private volatile boolean dialog_ok = false;
    private volatile boolean partida_local;
    private volatile File avatar = null;
    private volatile boolean init = false;
    private final static ConcurrentLinkedQueue<String> SERVER_HISTORY_QUEUE = loadServerHistory();
    private volatile int conta_history = SERVER_HISTORY_QUEUE.isEmpty() ? 0 : SERVER_HISTORY_QUEUE.size() - 1;
    private volatile boolean force_recover = false;

    // Guards the async loadLastGame(): its DB read now runs off the EDT (under SQL_LOCK), so a
    // second recover click while a load is in flight is ignored. Touched on the EDT.
    private volatile boolean recover_loading = false;

    public void setForce_recover(boolean force_recover) {
        this.force_recover = force_recover;
    }

    public JCheckBox getRecover_checkbox() {
        return recover_checkbox;
    }

    private int getCurrentBotLevel() {

        if (Bot.DIFFICULTY == Bot.Difficulty.EASY) {
            return 0;
        }

        if (Bot.DIFFICULTY == Bot.Difficulty.MEDIUM) {
            return 1;
        }

        if (Bot.DIFFICULTY == Bot.Difficulty.HARD) {
            return 2;
        }

        return 1;
    }

    public boolean isDialog_ok() {
        return dialog_ok;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            // setLocationRelativeTo can center this dialog partially off-screen or under
            // the taskbar; clamp it back into the usable screen area right before showing it.
            Helpers.clampWindowToUsableBounds(this);
        }
        super.setVisible(visible);
    }

    private static ConcurrentLinkedQueue<String> loadServerHistory() {

        ConcurrentLinkedQueue<String> history = new ConcurrentLinkedQueue<>();

        if (!GameFrame.SERVER_HISTORY.isBlank()) {

            history.addAll(Arrays.asList(GameFrame.SERVER_HISTORY.split("@")));
        }

        return history;
    }

    private String getServerHistoryString() {

        if (!SERVER_HISTORY_QUEUE.isEmpty()) {

            String ret = "";

            for (String s : SERVER_HISTORY_QUEUE) {

                ret += s + "@";
            }

            return ret.substring(0, ret.length() - 1);
        } else {
            return "";
        }
    }

    public void setPass(String password) {
        pass_text.setEnabled(true);
        pass_text.setText(password);

    }

    /**
     * Builds the new-game / join-game dialog.
     *
     * @param parent owner frame
     * @param modal whether the dialog blocks input to the owner
     * @param loc {@code true} for "create game" (host, local), {@code false} for "join game"
     */
    public NewGameDialog(java.awt.Frame parent, boolean modal, boolean loc) {
        super(parent, modal);

        initComponents();

        // Thin grouping boxes (blind escalation/cap and rebuy) get ROUNDED corners instead
        // of square. Applied here (after initComponents) to avoid depending on the generated
        // .form. Same gray and thickness as the original LineBorder, only the arc changes.
        aumento_panel.setBorder(new RoundedLineBorder(new java.awt.Color(210, 210, 210), 1, 12));
        aumento_panel.setOpaque(false);
        recompra_panel.setBorder(new RoundedLineBorder(new java.awt.Color(210, 210, 210), 1, 12));
        recompra_panel.setOpaque(false);

        setupTooltips();

        blind_cap_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> updateBlindCapLabel());

        Helpers.attachPasswordStrengthHint(pass_text);
        Helpers.attachPasswordRevealButton(pass_text);

        // A new game always starts on "Default" (ignores any structure left active from
        // a previous game). Picking a custom one makes applySelectedStructure repopulate
        // the level combo.
        pending_structure = null;
        populateStructureCombo(null);

        // The LEVELS combo starts with the full default ladder (defaultLevels), not the
        // designer's fixed list, so it includes every level. populateStructureCombo only
        // fills the structure selector.
        {
            double[][] def_levels = BlindStructure.defaultLevels();
            String[] def_items = new String[def_levels.length];
            for (int k = 0; k < def_levels.length; k++) {
                def_items[k] = BlindStructure.formatLevel(def_levels[k][0], def_levels[k][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(def_items));
        }

        // The Rabbit Hunting combo is populated (translated) here: nothing else sets its
        // model for a new game, and populatePresetsCombo suppresses the presets combo's
        // action (never calls applySettingsToControls), so without this the dropdown
        // would come up EMPTY.
        this.rabbit_combo.setModel(new DefaultComboBoxModel<>(new String[]{
            Translator.translate("menu.off"),
            Translator.translate("menu.free"),
            Translator.translate("menu.free_sb"),
            Translator.translate("menu.free_sb_bb")
        }));
        this.rabbit_combo.setSelectedIndex(Math.min(Math.max(GameFrame.RABBIT_HUNTING, 0), 3));

        // Think time: starts from the last session value (like rabbit); defaults to
        // enabled at GameFrame.DEFAULT_THINK_TIME (40 s). The spinner already clamps to
        // the 10-120 range.
        this.think_time_checkbox.setSelected(GameFrame.THINK_TIME_ENABLED);
        this.think_time_spinner.setValue(Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, GameFrame.THINK_TIME)));
        this.think_time_spinner.setEnabled(GameFrame.THINK_TIME_ENABLED);

        // Showdown time: starts from the last session value; defaults to
        // GameFrame.DEFAULT_SHOWDOWN_TIME (10 s). The spinner already clamps to the 5-30 range.
        this.showdown_time_spinner.setValue(Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, GameFrame.SHOWDOWN_TIME)));

        titulo_ventana.setText(loc ? Translator.translate("game.crear_timba") : Translator.translate("game.unirme_a_timba"));

        recover_checkbox_label.setText(Translator.translate("game.continuar_timba_anterior"));

        partida_local = loc;

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        if (partida_local) {
            DefaultComboBoxModel<String> bots_combobox_model = new DefaultComboBoxModel<>();

            bots_combobox_model.addElement(Translator.translate("ui.bots_facil"));

            bots_combobox_model.addElement(Translator.translate("ui.bots_media"));

            bots_combobox_model.addElement(Translator.translate("ui.bots_dificil"));

            bots_combobox.setModel(bots_combobox_model);

            bots_combobox.setSelectedIndex(this.getCurrentBotLevel());

            bots_label.setText(Translator.translate("ui.bots_dificultad"));

            Helpers.setScaledIconLabel(bots_avatar_label, getClass().getResource("/images/avatar_bot.png"), Math.round(56 * Helpers.DIALOG_ZOOM), Math.round(56 * Helpers.DIALOG_ZOOM));

        } else {
            bots_panel.setVisible(false);
        }

        // Presets: only when creating a game as host (not when joining). Hidden in other
        // modes so a full config load isn't offered where it doesn't apply.
        presets_panel.setVisible(partida_local);
        if (partida_local) {
            populatePresetsCombo(null);
        }

        password.setEnabled(false);

        manos_spinner.setEnabled(false);

        double_blinds_radio_manos.setSelected(true);

        double_blinds_radio_minutos.setSelected(false);

        doblar_ciegas_spinner_minutos.setEnabled(false);

        if (partida_local) {
            upnp_checkbox.setSelected(Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("upnp", "false")));
        } else {
            upnp_checkbox.setVisible(false);
            recover_panel.setVisible(false);
        }

        // Seed the local working store for the buy-in range + cap from GameFrame on open.
        // From here on the UI operates on these fields; GameFrame isn't touched again until CREATE.
        working_min_bb = GameFrame.BUYIN_MIN_BB;
        working_max_bb = GameFrame.BUYIN_MAX_BB;
        working_rebuy_cap_policy = GameFrame.REBUY_CAP_POLICY;

        initBuyinRangeAndCapUI();

        class VamosButtonListener implements DocumentListener {

            public void changedUpdate(DocumentEvent e) {
                vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
                password.setEnabled(pass_text.getPassword().length > 0);
            }

            public void insertUpdate(DocumentEvent e) {
                vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
                password.setEnabled(pass_text.getPassword().length > 0);
            }

            public void removeUpdate(DocumentEvent e) {
                vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
                password.setEnabled(pass_text.getPassword().length > 0);
            }
        }

        JTextFieldRegularPopupMenu.addTo(server_ip_textfield);
        server_ip_textfield.getDocument().addDocumentListener(new VamosButtonListener());

        JTextFieldRegularPopupMenu.addTo(server_port_textfield);
        server_port_textfield.getDocument().addDocumentListener(new VamosButtonListener());
        ((AbstractDocument) server_port_textfield.getDocument()).setDocumentFilter(new Helpers.numericFilter(server_port_textfield, MAX_PORT_LENGTH));

        JTextFieldRegularPopupMenu.addTo(nick);
        nick.getDocument().addDocumentListener(new VamosButtonListener());
        ((AbstractDocument) nick.getDocument()).setDocumentFilter(new Helpers.maxLenghtFilter(nick, MAX_NICK_LENGTH));

        JTextFieldRegularPopupMenu.addTo(pass_text);
        pass_text.getDocument().addDocumentListener(new VamosButtonListener());
        ((AbstractDocument) pass_text.getDocument()).setDocumentFilter(new Helpers.maxLenghtFilter(pass_text, MAX_PASS_LENGTH));

        String elnick = Helpers.PROPERTIES.getProperty("nick", "");

        nick.setText(elnick.substring(0, Math.min(MAX_NICK_LENGTH, elnick.length())));

        String avatar_path = Helpers.PROPERTIES.getProperty("avatar", "");

        if (!avatar_path.isEmpty()) {

            avatar = new File(avatar_path);

            if (avatar.exists() && avatar.canRead() && avatar.length() <= AVATAR_MAX_FILESIZE * 1024) {
                avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
                Helpers.setScaledIconLabel(avatar_label, avatar.getAbsolutePath(), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

            } else {
                avatar = null;
                avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
                Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

            }
        } else {
            avatar = null;
            avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
            Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

        }

        if (partida_local) {
            server_ip_textfield.setText(Helpers.PROPERTIES.getProperty("local_ip", "localhost"));
            server_ip_textfield.setEnabled(false);
            server_port_textfield.setText(Helpers.PROPERTIES.getProperty("local_port", String.valueOf(DEFAULT_PORT)));

            rebuy_checkbox.setSelected(true);
            doblar_checkbox.setSelected(false);
            bot_rebuy_checkbox.setSelected(true);
            bot_balance_checkbox.setSelected(false);
            fixed_buyin_checkbox.setSelected(true);
            buyin_spinner.setEnabled(true);
            double_blinds_radio_minutos.setEnabled(false);
            double_blinds_radio_manos.setEnabled(false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            blind_cap_checkbox.setSelected(false);
            blind_cap_checkbox.setEnabled(false);
            setBlindCapControlsEnabled(false);
            rebuy_limit_checkbox.setSelected(false);
            rebuy_limit_spinner.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
            Helpers.makeNumericSpinnerEditable(rebuy_limit_spinner, false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            double ciega_grande = Double.valueOf(valores[1].trim());

            buyin_spinner.setModel(new SpinnerNumberModel(BuyinRules.defaultBuyin(ciega_grande, working_min_bb, working_max_bb), BuyinRules.min(ciega_grande, working_min_bb), Math.max(BuyinRules.min(ciega_grande, working_min_bb), BuyinRules.max(ciega_grande, working_max_bb)), (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

            Helpers.makeNumericSpinnerEditable(buyin_spinner, false);

            modelBlindCapSpinner(5);

            Helpers.makeNumericSpinnerEditable(manos_spinner, false);

            Helpers.setTranslatedTitle(this, "ui.crear_timba");

            Helpers.updateFonts(this, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);

            Helpers.translateComponents(this, false);

        } else {
            server_port_textfield.setText(Helpers.PROPERTIES.getProperty("server_port", String.valueOf(DEFAULT_PORT)));
            server_ip_textfield.setText(Helpers.PROPERTIES.getProperty("server_ip", "localhost"));
            Helpers.setTranslatedTitle(this, "ui.unirme_a_timba");
            Helpers.updateFonts(this, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);
            Helpers.translateComponents(this, false);
            config_partida_panel.setVisible(false);
        }

        applyGroupTitledBorders();

        updateAnteStraddleLabels();

        Helpers.scaleIcons(this, Helpers.DIALOG_ZOOM);

        // Opens with a SINGLE pack(). It used to pack once to read the preferred size,
        // clamp, and pack again — two full window relayouts in the common case (this
        // dialog is tall and almost always needs clamping). The preferred size can already
        // be read directly (getPreferredSize reflects the state after
        // updateFonts/scaleIcons, and the peer exists since initComponents' pack()), so we
        // clamp FIRST and pack once. The prior revalidate()/repaint() calls are gone too:
        // pack() already revalidates and the window isn't visible yet (Init calls
        // setVisible afterwards), so they painted nothing.
        //
        // Clamps to the USABLE AREA (getMaximumWindowBounds excludes the taskbar), not the
        // full screen size: at low resolution the window fits entirely above the taskbar
        // and scroll_panel's vertical scrollbar covers the rest. The VAMOS/CANCEL buttons
        // stay fixed at the bottom (outside the scroll), always visible.
        Rectangle usable_bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        Dimension pref_size = getPreferredSize();

        int w = Math.min(pref_size.width, Math.round(usable_bounds.width * 0.95f));

        int h = Math.min(pref_size.height, Math.round(usable_bounds.height * 0.95f));

        boolean clamped = (w != pref_size.width || h != pref_size.height);

        if (clamped) {
            setPreferredSize(new Dimension(w, h));
        }

        pack();

        if (clamped) {
            Helpers.windowAutoFitToRemoveHScrollBar(this, scroll_panel.getHorizontalScrollBar(), usable_bounds.width);
        }

        init = true;

    }

    /**
     * Titled borders for the four configuration groups (blinds, buy-in, game and
     * bots), so each block reads on its own. A TitledBorder is not a component, so
     * Helpers.updateFonts cannot reach it: the title font is set here from an
     * already-scaled label font (same approach as SettingsDialog). Must be
     * called after Helpers.updateFonts and before pack() so the border insets are
     * accounted for in the dialog's preferred size.
     */
    private javax.swing.JLabel ante_label;

    // Wraps a text label + a text-less ToggleSwitch into one compact transparent row (label on the
    // left, toggle on the right), to drop in where a self-text checkbox used to sit. Height is
    // capped to natural so the GroupLayout can't stretch the row and open a gap.
    private javax.swing.JPanel toggleRow(javax.swing.JComponent label, javax.swing.JComponent toggle) {
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
        if (toggle instanceof SettingsUI.ToggleSwitch && label instanceof javax.swing.JLabel) {
            ((SettingsUI.ToggleSwitch) toggle).pairLabel((javax.swing.JLabel) label);
        }
        return row;
    }

    // The 4 game-setting sections are wrapped in SettingsUI.card() at assembly; here we just make
    // the panels transparent so the card's white shows through (was: TitledBorders).
    private void applyGroupTitledBorders() {
        ciegas_panel.setOpaque(false);
        compra_panel.setOpaque(false);
        partida_panel.setOpaque(false);
        bots_panel.setOpaque(false);
    }

    private void loadLastGame() {

        String sql = "SELECT id,start,server,recover_settings,rebuy FROM game WHERE (ugi IS NOT NULL AND local == 1) ORDER BY start DESC LIMIT 1";

        if (this.force_recover) {
            // Crash recovery ("server halted the game so a player can rejoin"): continueLastGame()
            // does recover_checkbox.doClick() → here, then setVisible() auto-starts via
            // vamosActionPerformed(null), which reads last_game_key — so this MUST resolve
            // SYNCHRONOUSLY on the EDT. SQL_LOCK must NEVER be acquired on the EDT (a worker holding
            // it across a GUIRunAndWait — e.g. a StatsDialog stat render — would deadlock), so this
            // read is deliberately UNLOCKED. It runs during recovery; the concurrent connection users
            // still possible are an interrupted-but-still-finishing StatsSync import (writing DISJOINT
            // rows — received games are local=0, this reads local=1) or a lingering StatsDialog read on
            // its stats-db thread. Both are made safe by SQLite's serialized/full-mutex driver mode (the
            // connection is opened WITHOUT SQLITE_OPEN_NOMUTEX) plus the 5s busy_timeout — a pre-existing,
            // narrow, benign race (worst case a transient stall, never corruption). See
            // sqlite-single-connection-lock.
            boolean found = false;
            int id = -1;
            long start = 0L;
            String server = null;
            String settings = null;
            boolean rebuy = false;
            try (Statement statement = Helpers.getSQLITE().createStatement()) {
                statement.setQueryTimeout(30);
                try (ResultSet rs = statement.executeQuery(sql)) {
                    if (rs.next()) {
                        id = rs.getInt("id");
                        start = rs.getLong("start");
                        server = rs.getString("server");
                        settings = rs.getString("recover_settings");
                        rebuy = rs.getBoolean("rebuy");
                        found = true;
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (found) {
                applyLoadedLastGame(id, start, server, settings, rebuy);
                applyRecoverSelectedUi();
            } else {
                showNoRecoverableGamesUi();
            }
            return;
        }

        if (recover_loading) {
            return;
        }
        recover_loading = true;
        // Feedback while the recovered game loads off the EDT: grey out "Vamos" so a click during the
        // (brief) load is a visible no-op rather than a silent one; restored when the load lands.
        vamos.setEnabled(false);

        // Normal user-click path: the read touches the shared connection under SQL_LOCK (a
        // StatsDialog query can run concurrently), which must never be requested from the EDT — run
        // it on a worker, then apply the recovered settings + "recover selected" UI back on the EDT.
        if (Helpers.threadRun(() -> {
            boolean found = false;
            int id = -1;
            long start = 0L;
            String server = null;
            String settings = null;
            boolean rebuy = false;
            try {
                synchronized (GameFrame.SQL_LOCK) {
                    try (Statement statement = Helpers.getSQLITE().createStatement()) {
                        statement.setQueryTimeout(30);
                        try (ResultSet rs = statement.executeQuery(sql)) {
                            if (rs.next()) {
                                id = rs.getInt("id");
                                start = rs.getLong("start");
                                server = rs.getString("server");
                                settings = rs.getString("recover_settings");
                                rebuy = rs.getBoolean("rebuy");
                                found = true;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                // A read failure is treated as "no recoverable game" (same outcome as the old code,
                // where a failed read left last_game_key null → the "no games" branch).
                Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

            final boolean fFound = found;
            final int fId = id;
            final long fStart = start;
            final String fServer = server;
            final String fSettings = settings;
            final boolean fRebuy = rebuy;
            Helpers.GUIRun(() -> {
                try {
                    // The user may have unticked "recover" while the read was in flight — don't apply.
                    if (!this.recover_checkbox.isSelected()) {
                        return;
                    }
                    if (fFound) {
                        applyLoadedLastGame(fId, fStart, fServer, fSettings, fRebuy);
                        applyRecoverSelectedUi();
                    } else {
                        showNoRecoverableGamesUi();
                    }
                } finally {
                    recover_loading = false;
                    restoreVamosEnabled();
                }
            });
        }) == null) {
            // Pool shutting down (game teardown): the submitted load will never run — undo the guard
            // and feedback so "recover" stays usable.
            recover_loading = false;
            restoreVamosEnabled();
        }
    }

    /** EDT. Sets "Vamos" enabled iff nick + server IP + port are all filled (the normal condition). */
    private void restoreVamosEnabled() {
        vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
    }

    /** EDT. Populates the dialog fields from the recovered game's row (read off the EDT). */
    private void applyLoadedLastGame(int id, long start, String server, String settings, boolean rebuy) {
        Timestamp ts = new Timestamp(start);
        DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Date date = new Date(ts.getTime());

        HashMap<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("recover_settings", settings);
        GameFrame.applyRecoverSettings(settings);
        if (partida_local) {
            bots_combobox.setSelectedIndex(getCurrentBotLevel());
        }

        // "Game" settings are EDITABLE on recover: populate them from the recovered
        // game's values (for IWTSTH/RIT/rabbit the *_RECOVER override wins if set;
        // hand limit and think time are left by applyRecoverSettings directly in the
        // static field). The user can still tweak them before rejoining.
        boolean rec_iwtsth = GameFrame.IWTSTH_RULE_RECOVER != null ? GameFrame.IWTSTH_RULE_RECOVER : GameFrame.IWTSTH_RULE;
        int rec_rabbit = GameFrame.RABBIT_HUNTING_RECOVER != null ? GameFrame.RABBIT_HUNTING_RECOVER : GameFrame.RABBIT_HUNTING;
        boolean rec_rit = GameFrame.RUN_IT_TWICE_RECOVER != null ? GameFrame.RUN_IT_TWICE_RECOVER : GameFrame.RUN_IT_TWICE;
        this.iwtsth_checkbox.setSelected(rec_iwtsth);
        this.rit_checkbox.setSelected(rec_rit);
        this.rabbit_combo.setSelectedIndex(Math.min(Math.max(rec_rabbit, 0), 3));
        boolean rec_manos_on = GameFrame.MANOS != -1;
        this.manos_checkbox.setSelected(rec_manos_on);
        this.manos_spinner.setModel(new SpinnerNumberModel(rec_manos_on ? GameFrame.MANOS : 100, 1, null, 1));
        Helpers.makeNumericSpinnerEditable(this.manos_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) this.manos_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        this.think_time_checkbox.setSelected(GameFrame.THINK_TIME_ENABLED);
        this.think_time_spinner.setValue(Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, GameFrame.THINK_TIME)));
        this.showdown_time_spinner.setValue(Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, GameFrame.SHOWDOWN_TIME)));

        this.blind_cap_checkbox.setSelected(GameFrame.BLIND_CAP > 0f);
        modelBlindCapSpinner(blindCapDoublingsFromCap());
        this.rebuy_limit_checkbox.setSelected(GameFrame.REBUY_LIMIT > 0);
        if (GameFrame.REBUY_LIMIT > 0) {
            this.rebuy_limit_spinner.setValue(GameFrame.REBUY_LIMIT);
        }
        this.bot_rebuy_checkbox.setSelected(GameFrame.BOT_REBUY);
        this.bot_balance_checkbox.setSelected(GameFrame.BOT_BALANCE_TO_HUMANS);
        // Rebuy is EDITABLE on recover: "allow rebuy" comes from the game.rebuy
        // column; the rebuy cap from the recovered config (REBUY_CAP_POLICY).
        this.rebuy_checkbox.setSelected(rebuy);
        this.rebuy_cap_combo.setSelectedIndex(GameFrame.REBUY_CAP_POLICY == GameFrame.REBUY_CAP_HIGHEST_STACK ? 1 : 0);
        game.put(server + " @ " + timeZoneFormat.format(date), map);
        this.last_game_key = server + " @ " + timeZoneFormat.format(date);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        scroll_panel = new javax.swing.JScrollPane();
        main_panel = new javax.swing.JPanel();
        vamos = new javax.swing.JButton();
        vamos.putClientProperty("i18n.key", "ui.vamos");
        url_panel = new javax.swing.JPanel();
        server_port_puntos = new javax.swing.JLabel();
        server_port_textfield = new javax.swing.JTextField();
        upnp_checkbox = new SettingsUI.ToggleSwitch(false);
        server_ip_textfield = new javax.swing.JTextField();
        server_label = new javax.swing.JLabel();
        presets_panel = new javax.swing.JPanel();
        preset_label = new javax.swing.JLabel();
        preset_label.putClientProperty("i18n.key", "newgame.preset_label");
        presets_combobox = new javax.swing.JComboBox<>();
        preset_save_button = new javax.swing.JButton();
        preset_save_button.putClientProperty("i18n.key", "newgame.preset_guardar");
        preset_delete_button = new javax.swing.JButton();
        preset_delete_button.putClientProperty("i18n.key", "newgame.preset_borrar");
        config_partida_panel = new javax.swing.JPanel();
        ciegas_panel = new javax.swing.JPanel();
        estructura_label = new javax.swing.JLabel();
        estructura_combobox = new javax.swing.JComboBox<>();
        ciegas_label = new javax.swing.JLabel();
        ciegas_combobox = new javax.swing.JComboBox<>();
        aumento_panel = new javax.swing.JPanel();
        doblar_checkbox = new SettingsUI.ToggleSwitch(false);
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        blind_cap_checkbox = new SettingsUI.ToggleSwitch(false);
        blind_cap_spinner = new javax.swing.JSpinner();
        blind_cap_label = new javax.swing.JLabel();
        ante_checkbox = new SettingsUI.ToggleSwitch(false);
        straddle_checkbox = new SettingsUI.ToggleSwitch(false);
        straddle_icon = new javax.swing.JLabel();
        compra_panel = new javax.swing.JPanel();
        buyin_label = new javax.swing.JLabel();
        buyin_spinner = new javax.swing.JSpinner();
        fixed_buyin_checkbox = new SettingsUI.ToggleSwitch(false);
        buyin_range_label = new javax.swing.JLabel();
        buyin_min_bb_spinner = new javax.swing.JSpinner();
        buyin_range_sep_label = new javax.swing.JLabel();
        buyin_max_bb_spinner = new javax.swing.JSpinner();
        recompra_panel = new javax.swing.JPanel();
        rebuy_checkbox = new SettingsUI.ToggleSwitch(false);
        recomprar_label = new javax.swing.JLabel();
        rebuy_limit_checkbox = new SettingsUI.ToggleSwitch(false);
        rebuy_limit_spinner = new javax.swing.JSpinner();
        rebuy_cap_label = new javax.swing.JLabel();
        rebuy_cap_combo = new javax.swing.JComboBox<>();
        partida_panel = new javax.swing.JPanel();
        manos_checkbox = new SettingsUI.ToggleSwitch(false);
        limite_manos_label = new javax.swing.JLabel();
        manos_spinner = new javax.swing.JSpinner();
        think_time_checkbox = new SettingsUI.ToggleSwitch(false);
        think_time_label = new javax.swing.JLabel();
        think_time_spinner = new javax.swing.JSpinner();
        showdown_time_label = new javax.swing.JLabel();
        showdown_time_spinner = new javax.swing.JSpinner();
        iwtsth_icon = new javax.swing.JLabel();
        iwtsth_checkbox = new SettingsUI.ToggleSwitch(false);
        rit_icon = new javax.swing.JLabel();
        rit_checkbox = new SettingsUI.ToggleSwitch(false);
        rabbit_icon = new javax.swing.JLabel();
        rabbit_label = new javax.swing.JLabel();
        rabbit_combo = new javax.swing.JComboBox<>();
        bots_panel = new javax.swing.JPanel();
        bots_avatar_label = new javax.swing.JLabel();
        bots_combobox = new javax.swing.JComboBox<>();
        bots_label = new javax.swing.JLabel();
        bot_rebuy_checkbox = new SettingsUI.ToggleSwitch(false);
        bot_balance_checkbox = new SettingsUI.ToggleSwitch(false);
        nick_pass_panel = new javax.swing.JPanel();
        nick = new javax.swing.JTextField();
        nick_label = new javax.swing.JLabel();
        password = new javax.swing.JLabel();
        pass_text = new javax.swing.JPasswordField();
        avatar_label = new javax.swing.JLabel();
        recover_panel = new javax.swing.JPanel();
        recover_checkbox = new SettingsUI.ToggleSwitch(false);
        recover_checkbox_label = new javax.swing.JLabel();
        game_label = new javax.swing.JLabel();
        cancel_button = new javax.swing.JButton();
        cancel_button.putClientProperty("i18n.key", "ui.cancelar");
        titulo_ventana = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("CoronaPoker - Nueva timba");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        setUndecorated(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));

        scroll_panel.setBorder(null);
        scroll_panel.setDoubleBuffered(true);

        vamos.setBackground(new java.awt.Color(0, 130, 0));
        vamos.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        vamos.setForeground(new java.awt.Color(255, 255, 255));
        vamos.setText("¡VAMOS!");
        vamos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        vamos.setDoubleBuffered(true);
        vamos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vamosActionPerformed(evt);
            }
        });

        server_port_puntos.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        server_port_puntos.setText(":");
        server_port_puntos.setDoubleBuffered(true);

        server_port_textfield.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        server_port_textfield.setDoubleBuffered(true);
        server_port_textfield.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                server_port_textfieldActionPerformed(evt);
            }
        });

        javax.swing.JLabel upnp_label = new javax.swing.JLabel("UPnP");
        upnp_label.setFont(new java.awt.Font("Dialog", 1, 16));
        javax.swing.JPanel upnp_row = toggleRow(upnp_label, upnp_checkbox);
        upnp_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        upnp_checkbox.setDoubleBuffered(true);

        server_ip_textfield.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        server_ip_textfield.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        server_ip_textfield.setText("localhost");
        server_ip_textfield.setDoubleBuffered(true);
        server_ip_textfield.setPreferredSize(new java.awt.Dimension(Math.round(500 * Helpers.DIALOG_ZOOM), Math.round(31 * Helpers.DIALOG_ZOOM)));
        server_ip_textfield.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                server_ip_textfieldActionPerformed(evt);
            }
        });
        server_ip_textfield.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                server_ip_textfieldKeyReleased(evt);
            }
        });

        server_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        server_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/780.png"))); // NOI18N
        server_label.setText("Servidor:");
        server_label.setDoubleBuffered(true);

        javax.swing.GroupLayout url_panelLayout = new javax.swing.GroupLayout(url_panel);
        url_panel.setLayout(url_panelLayout);
        url_panelLayout.setHorizontalGroup(
            url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(url_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(server_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(server_ip_textfield, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(server_port_puntos)
                .addGap(0, 0, 0)
                .addComponent(server_port_textfield, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upnp_row))
        );
        url_panelLayout.setVerticalGroup(
            url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(server_ip_textfield, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(server_label))
            .addGroup(url_panelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(upnp_row))
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, url_panelLayout.createSequentialGroup()
                .addComponent(server_port_puntos)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(server_port_textfield)
        );

        server_label.putClientProperty("i18n.key", "ui.servidor");

        presets_panel.setOpaque(false);

        preset_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        preset_label.setText("Perfil de ajustes:");
        preset_label.setDoubleBuffered(true);

        presets_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        presets_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        presets_combobox.setDoubleBuffered(true);
        presets_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                presets_comboboxActionPerformed(evt);
            }
        });

        preset_save_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        preset_save_button.setText("Guardar…");
        preset_save_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        preset_save_button.setDoubleBuffered(true);
        preset_save_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preset_save_buttonActionPerformed(evt);
            }
        });

        preset_delete_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        preset_delete_button.setText("Borrar");
        preset_delete_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        preset_delete_button.setDoubleBuffered(true);
        preset_delete_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preset_delete_buttonActionPerformed(evt);
            }
        });

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
                    .addComponent(presets_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(preset_save_button)
                    .addComponent(preset_delete_button))
                .addContainerGap())
        );

        config_partida_panel.setOpaque(false);

        estructura_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        estructura_label.setText("Estructura:");
        estructura_label.setDoubleBuffered(true);

        estructura_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        estructura_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        estructura_combobox.setDoubleBuffered(true);
        estructura_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                estructura_comboboxActionPerformed(evt);
            }
        });

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        ciegas_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png"))); // NOI18N
        ciegas_label.setText("Ciegas iniciales:");
        ciegas_label.setDoubleBuffered(true);

        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,10 / 0,20":"0.10 / 0.20", GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,20 / 0,40":"0.20 / 0.40", GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,30 / 0,60":"0.30 / 0.60", GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,50 / 1":"0.50 / 1", "1 / 2", "2 / 4", "3 / 6", "5 / 10", "10 / 20", "20 / 40", "30 / 60", "50 / 100", "100 / 200", "200 / 400", "300 / 600", "500 / 1000", "1000 / 2000", "2000 / 4000", "3000 / 6000", "5000 / 10000" }));
        ciegas_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ciegas_combobox.setDoubleBuffered(true);
        ciegas_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ciegas_comboboxActionPerformed(evt);
            }
        });

        aumento_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        javax.swing.JLabel doblar_label = new javax.swing.JLabel("Aumentar ciegas");
        doblar_label.setFont(new java.awt.Font("Dialog", 1, 16));
        javax.swing.JPanel doblar_row = toggleRow(doblar_label, doblar_checkbox);
        doblar_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_checkbox.setDoubleBuffered(true);
        doblar_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doblar_checkboxActionPerformed(evt);
            }
        });

        double_blinds_radio_manos.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        double_blinds_radio_manos.setText("Manos:");
        double_blinds_radio_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_manos.setDoubleBuffered(true);
        double_blinds_radio_manos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_manosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_manos.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        doblar_ciegas_spinner_manos.setModel(new javax.swing.SpinnerNumberModel(30, 1, null, 1));
        doblar_ciegas_spinner_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_ciegas_spinner_manos.setDoubleBuffered(true);

        double_blinds_radio_minutos.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        double_blinds_radio_minutos.setText("Minutos:");
        double_blinds_radio_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_minutos.setDoubleBuffered(true);
        double_blinds_radio_minutos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_minutosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_minutos.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        doblar_ciegas_spinner_minutos.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        doblar_ciegas_spinner_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_ciegas_spinner_minutos.setDoubleBuffered(true);

        javax.swing.JLabel blind_cap_text = new javax.swing.JLabel("Tope ciega grande");
        blind_cap_text.setFont(new java.awt.Font("Dialog", 1, 14));
        javax.swing.JPanel blind_cap_row = toggleRow(blind_cap_text, blind_cap_checkbox);
        blind_cap_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_checkbox.setDoubleBuffered(true);
        blind_cap_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blind_cap_checkboxActionPerformed(evt);
            }
        });

        blind_cap_spinner.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        blind_cap_spinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, null, 1));
        blind_cap_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_spinner.setDoubleBuffered(true);

        blind_cap_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        blind_cap_label.setText("0 / 0");
        blind_cap_label.setDoubleBuffered(true);

        javax.swing.GroupLayout aumento_panelLayout = new javax.swing.GroupLayout(aumento_panel);
        aumento_panel.setLayout(aumento_panelLayout);
        aumento_panelLayout.setHorizontalGroup(
            aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aumento_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    // "Aumentar ciegas" = cabecera (sin sangrar); sus sub-opciones van sangradas
                    .addComponent(doblar_row)
                    .addGroup(aumento_panelLayout.createSequentialGroup()
                        .addGap(Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM))
                        .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(double_blinds_radio_manos)
                            .addComponent(double_blinds_radio_minutos)
                            .addComponent(blind_cap_row))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(160 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(160 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(blind_cap_label)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
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

        doblar_label.putClientProperty("i18n.key", "stats.aumentar_ciegas");
        double_blinds_radio_manos.putClientProperty("i18n.key", "stats.manos");
        double_blinds_radio_minutos.putClientProperty("i18n.key", "ui.minutos");
        blind_cap_text.putClientProperty("i18n.key", "blinds.tope_ciega_grande");

        ante_label = new javax.swing.JLabel("Ante");
        ante_label.setFont(new java.awt.Font("Dialog", 1, 16));
        javax.swing.JPanel ante_row = toggleRow(ante_label, ante_checkbox);
        ante_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ante_checkbox.setDoubleBuffered(true);

        straddle_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        straddle_checkbox.setDoubleBuffered(true);

        straddle_icon.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        straddle_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/straddle_24.png"))); // NOI18N
        straddle_icon.setText("Straddle");
        javax.swing.JPanel straddle_row = toggleRow(straddle_icon, straddle_checkbox);
        straddle_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        straddle_icon.setDoubleBuffered(true);
        straddle_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                straddle_iconMouseClicked(evt);
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
                    .addComponent(estructura_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ciegas_label)
                    .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(aumento_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ante_row)
                    .addComponent(straddle_row))
                .addContainerGap())
        );

        estructura_label.putClientProperty("i18n.key", "blinds.estructura");
        ciegas_label.putClientProperty("i18n.key", "blinds.ciegas_iniciales");

        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png"))); // NOI18N
        buyin_label.setText("Compra inicial:");
        buyin_label.setDoubleBuffered(true);

        buyin_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 5, null, 1));
        buyin_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_spinner.setDoubleBuffered(true);
        buyin_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_spinnerStateChanged(evt);
            }
        });

        javax.swing.JLabel fixed_buyin_label = new javax.swing.JLabel("Buy-in fijo");
        fixed_buyin_label.setFont(new java.awt.Font("Dialog", 1, 14));
        javax.swing.JPanel fixed_buyin_row = toggleRow(fixed_buyin_label, fixed_buyin_checkbox);
        fixed_buyin_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fixed_buyin_checkbox.setDoubleBuffered(true);
        fixed_buyin_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixed_buyin_checkboxActionPerformed(evt);
            }
        });

        buyin_range_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_range_label.setText("Rango compra (CG):");
        buyin_range_label.setDoubleBuffered(true);

        buyin_min_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_min_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 10, 500, 5));
        buyin_min_bb_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_min_bb_spinner.setDoubleBuffered(true);
        buyin_min_bb_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_min_bb_spinnerStateChanged(evt);
            }
        });

        buyin_range_sep_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_range_sep_label.setText("a");
        buyin_range_sep_label.setDoubleBuffered(true);

        buyin_max_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_max_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 10, 500, 5));
        buyin_max_bb_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_max_bb_spinner.setDoubleBuffered(true);
        buyin_max_bb_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_max_bb_spinnerStateChanged(evt);
            }
        });

        recompra_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_checkbox.setDoubleBuffered(true);
        rebuy_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_checkboxActionPerformed(evt);
            }
        });

        recomprar_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recomprar_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png"))); // NOI18N
        recomprar_label.setText("Recomprar");
        recomprar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recomprar_label.setDoubleBuffered(true);
        recomprar_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                recomprar_labelMouseClicked(evt);
            }
        });

        javax.swing.JLabel rebuy_limit_label = new javax.swing.JLabel("Límite recompra por jugador");
        rebuy_limit_label.setFont(new java.awt.Font("Dialog", 1, 14));
        javax.swing.JPanel rebuy_limit_row = toggleRow(rebuy_limit_label, rebuy_limit_checkbox);
        rebuy_limit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_limit_checkbox.setDoubleBuffered(true);
        rebuy_limit_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_limit_checkboxActionPerformed(evt);
            }
        });

        rebuy_limit_spinner.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_limit_spinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, null, 1));
        rebuy_limit_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_limit_spinner.setDoubleBuffered(true);

        rebuy_cap_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        rebuy_cap_label.setText("Tope recompra:");
        rebuy_cap_label.setDoubleBuffered(true);

        rebuy_cap_combo.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_cap_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_cap_combo.setDoubleBuffered(true);

        javax.swing.GroupLayout recompra_panelLayout = new javax.swing.GroupLayout(recompra_panel);
        recompra_panel.setLayout(recompra_panelLayout);
        recompra_panelLayout.setHorizontalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addGap(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM))
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    // "Recomprar" = cabecera del bloque (sin sangrar)
                    .addGroup(recompra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(recomprar_label))
                    // Sub-opciones de "Recomprar" (limite + tope) sangradas a la derecha
                    .addGroup(recompra_panelLayout.createSequentialGroup()
                        .addGap(Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM))
                        .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(recompra_panelLayout.createSequentialGroup()
                                .addComponent(rebuy_limit_row)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
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

        recomprar_label.putClientProperty("i18n.key", "rebuy.recomprar_2");
        rebuy_limit_label.putClientProperty("i18n.key", "rebuy.limite_por_jugador");
        rebuy_cap_label.putClientProperty("i18n.key", "rebuy.tope_recompra");

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
                            .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE))
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(buyin_range_sep_label)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM))
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

        buyin_label.putClientProperty("i18n.key", "blinds.compra_inicial");
        fixed_buyin_label.putClientProperty("i18n.key", "newgame.buyin_fijo");
        buyin_range_label.putClientProperty("i18n.key", "blinds.rango_compra");
        buyin_range_sep_label.putClientProperty("i18n.key", "blinds.rango_a");

        manos_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.setDoubleBuffered(true);
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        limite_manos_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        limite_manos_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        limite_manos_label.setText("Límite de manos:");
        limite_manos_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        limite_manos_label.setDoubleBuffered(true);
        limite_manos_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                limite_manos_labelMouseClicked(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        // Think time: checkbox (enable/disable) + label with clock icon + seconds spinner
        // (10-120, default 40). Same pattern as the "Hand limit" row.
        think_time_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        think_time_checkbox.setDoubleBuffered(true);
        think_time_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                think_time_checkboxActionPerformed(evt);
            }
        });

        think_time_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        think_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png"))); // NOI18N
        think_time_label.setText("Tiempo de pensar:");
        think_time_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        think_time_label.setDoubleBuffered(true);
        think_time_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                think_time_labelMouseClicked(evt);
            }
        });

        think_time_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        think_time_spinner.setModel(new javax.swing.SpinnerNumberModel(40, 10, 120, 5));
        think_time_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        // Showdown pause time: label with clock icon + seconds spinner (5-30, default 10).
        // No checkbox: the pause is always on, only its duration is adjustable.
        showdown_time_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        showdown_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png"))); // NOI18N
        showdown_time_label.setText("Tiempo de showdown:");
        showdown_time_label.setDoubleBuffered(true);

        showdown_time_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        showdown_time_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 5, 30, 5));
        showdown_time_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        // Each rule: checkbox (no text) + label with icon and text (checkbox first,
        // then the icon), like the "Hand limit" row.
        iwtsth_icon.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        iwtsth_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/eyes.png"))); // NOI18N
        iwtsth_icon.setText("Regla IWTSTH");
        iwtsth_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        iwtsth_icon.setDoubleBuffered(true);
        iwtsth_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                iwtsth_iconMouseClicked(evt);
            }
        });

        iwtsth_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        iwtsth_checkbox.setDoubleBuffered(true);

        rit_icon.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        rit_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png"))); // NOI18N
        rit_icon.setText("ALL-IN Run-it-twice");
        rit_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rit_icon.setDoubleBuffered(true);
        rit_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                rit_iconMouseClicked(evt);
            }
        });

        rit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rit_checkbox.setDoubleBuffered(true);

        rabbit_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rabbit.png"))); // NOI18N

        rabbit_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        rabbit_label.setText("Rabbit Hunting:");
        rabbit_label.setDoubleBuffered(true);

        rabbit_combo.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        rabbit_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout partida_panelLayout = new javax.swing.GroupLayout(partida_panel);
        partida_panel.setLayout(partida_panelLayout);
        partida_panelLayout.setHorizontalGroup(
            partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(partida_panelLayout.createSequentialGroup()
                .addGap(Math.round(11 * Helpers.DIALOG_ZOOM), Math.round(11 * Helpers.DIALOG_ZOOM), Math.round(11 * Helpers.DIALOG_ZOOM))
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    // Limite de manos / Tiempo de pensar / Tiempo de showdown comparten tres
                    // columnas (casilla | etiqueta | spinner) para que los TRES spinners queden
                    // alineados por su borde izquierdo. Showdown no tiene casilla: deja el hueco
                    // en la columna de casilla y su etiqueta se alinea con las otras dos.
                    .addGroup(partida_panelLayout.createSequentialGroup()
                        .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(manos_checkbox)
                            .addComponent(think_time_checkbox))
                        .addGap(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM))
                        .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(limite_manos_label)
                            .addComponent(think_time_label)
                            .addComponent(showdown_time_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(partida_panelLayout.createSequentialGroup()
                        .addComponent(iwtsth_checkbox)
                        .addGap(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM))
                        .addComponent(iwtsth_icon))
                    .addGroup(partida_panelLayout.createSequentialGroup()
                        .addComponent(rit_checkbox)
                        .addGap(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM))
                        .addComponent(rit_icon))
                    .addGroup(partida_panelLayout.createSequentialGroup()
                        .addComponent(rabbit_icon)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rabbit_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        partida_panelLayout.setVerticalGroup(
            partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(partida_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(manos_checkbox)
                    .addComponent(limite_manos_label)
                    .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(think_time_checkbox)
                    .addComponent(think_time_label)
                    .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(showdown_time_label)
                    .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iwtsth_icon)
                    .addComponent(iwtsth_checkbox))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rit_icon)
                    .addComponent(rit_checkbox))
                .addGap(Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM))
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rabbit_icon)
                    .addComponent(rabbit_label)
                    .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        limite_manos_label.putClientProperty("i18n.key", "game.limite_de_manos");
        think_time_label.putClientProperty("i18n.key", "newgame.tiempo_pensar");
        showdown_time_label.putClientProperty("i18n.key", "newgame.tiempo_showdown");
        iwtsth_icon.putClientProperty("i18n.key", "menu.regla_iwtsth");
        rit_icon.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");

        bots_combobox.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        bots_combobox.setSelectedItem(1);
        bots_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bots_comboboxActionPerformed(evt);
            }
        });

        bots_label.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        bots_label.setText("Dificultad bots:");

        javax.swing.JLabel bot_rebuy_label = new javax.swing.JLabel("Recomprar bots");
        bot_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 16));
        javax.swing.JPanel bot_rebuy_row = toggleRow(bot_rebuy_label, bot_rebuy_checkbox);
        bot_rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        bot_rebuy_checkbox.setDoubleBuffered(true);

        javax.swing.JLabel bot_balance_label = new javax.swing.JLabel("Repartir saldo de bots entre humanos");
        bot_balance_label.setFont(new java.awt.Font("Dialog", 1, 16));
        javax.swing.JPanel bot_balance_row = toggleRow(bot_balance_label, bot_balance_checkbox);
        bot_balance_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        bot_balance_checkbox.setDoubleBuffered(true);

        javax.swing.GroupLayout bots_panelLayout = new javax.swing.GroupLayout(bots_panel);
        bots_panel.setLayout(bots_panelLayout);
        bots_panelLayout.setHorizontalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(bots_panelLayout.createSequentialGroup()
                        .addComponent(bots_avatar_label)
                        .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM))
                        .addComponent(bots_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(bot_rebuy_row)
                    .addComponent(bot_balance_row))
                .addContainerGap(Math.round(72 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE))
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
                .addContainerGap())
        );

        bot_rebuy_label.putClientProperty("i18n.key", "rebuy.permitir_bots");
        bot_balance_label.putClientProperty("i18n.key", "balance.repartir_saldo_bots");

        // Each game-setting section wrapped in a rounded SettingsUI.card instead of its TitledBorder.
        javax.swing.JPanel compra_card = SettingsUI.card("newgame.grupo_compra");
        compra_card.add(compra_panel);
        javax.swing.JPanel ciegas_card = SettingsUI.card("newgame.grupo_ciegas");
        ciegas_card.add(ciegas_panel);
        javax.swing.JPanel partida_card = SettingsUI.card("newgame.grupo_partida");
        partida_card.add(partida_panel);
        javax.swing.JPanel bots_card = SettingsUI.card("newgame.grupo_bots");
        bots_card.add(bots_panel);

        javax.swing.GroupLayout config_partida_panelLayout = new javax.swing.GroupLayout(config_partida_panel);
        config_partida_panel.setLayout(config_partida_panelLayout);
        config_partida_panelLayout.setHorizontalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                // Left column (Buy-in on top, Game below). Fillable: absorbs the leftover width
                // instead of shrinking to its content and leaving the gap in a central spring +
                // another at the end (variable whitespace).
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(compra_card, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(partida_card, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                // Fixed gap between columns = platform RELATED gap (same PREFERRED width as the old
                // central spring, so the dialog isn't widened); the fillable columns absorb the
                // leftover instead of leaving whitespace on the right.
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                // Right column (Blinds on top, Bots below), also fillable
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bots_card, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ciegas_card, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        config_partida_panelLayout.setVerticalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(compra_card, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ciegas_card, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(partida_card, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bots_card, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        nick.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        nick.setDoubleBuffered(true);
        nick.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nickActionPerformed(evt);
            }
        });

        nick_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        nick_label.setText("Nick:");
        nick_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        nick_label.setDoubleBuffered(true);
        nick_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                nick_labelMouseClicked(evt);
            }
        });

        password.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        password.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/lock.png"))); // NOI18N
        password.setText("Password:");
        password.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        password.setDoubleBuffered(true);

        pass_text.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        pass_text.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pass_textActionPerformed(evt);
            }
        });

        avatar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar_label.setDoubleBuffered(true);
        avatar_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                avatar_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout nick_pass_panelLayout = new javax.swing.GroupLayout(nick_pass_panel);
        nick_pass_panel.setLayout(nick_pass_panelLayout);
        nick_pass_panelLayout.setHorizontalGroup(
            nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_pass_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(password)
                    .addComponent(nick_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nick)
                    .addComponent(pass_text))
                .addGap(0, 0, 0))
        );
        nick_pass_panelLayout.setVerticalGroup(
            nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_pass_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(avatar_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(nick_pass_panelLayout.createSequentialGroup()
                        .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(nick_label)
                            .addComponent(nick, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(password)
                            .addComponent(pass_text, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
        );

        password.putClientProperty("i18n.key", "ui.password");

        recover_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recover_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recover_checkbox.setDoubleBuffered(true);
        recover_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recover_checkboxActionPerformed(evt);
            }
        });

        recover_checkbox_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recover_checkbox_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/undo.png"))); // NOI18N
        recover_checkbox_label.setText("CONTINUAR TIMBA ANTERIOR:");
        recover_checkbox_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recover_checkbox_label.setDoubleBuffered(true);
        recover_checkbox_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                recover_checkbox_labelMouseClicked(evt);
            }
        });

        game_label.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N

        javax.swing.GroupLayout recover_panelLayout = new javax.swing.GroupLayout(recover_panel);
        recover_panel.setLayout(recover_panelLayout);
        recover_panelLayout.setHorizontalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(recover_checkbox)
                .addGap(0, 0, 0)
                .addComponent(recover_checkbox_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(game_label)
                .addGap(Math.round(85 * Helpers.DIALOG_ZOOM), Math.round(85 * Helpers.DIALOG_ZOOM), Math.round(85 * Helpers.DIALOG_ZOOM)))
        );
        recover_panelLayout.setVerticalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addGroup(recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(recover_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(recover_checkbox_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(game_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        cancel_button.setBackground(new java.awt.Color(204, 0, 0));
        cancel_button.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        cancel_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_button.setText("CANCELAR");
        cancel_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        cancel_button.setDoubleBuffered(true);
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout main_panelLayout = new javax.swing.GroupLayout(main_panel);
        main_panel.setLayout(main_panelLayout);
        main_panelLayout.setHorizontalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(url_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(presets_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(config_partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(recover_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        main_panelLayout.setVerticalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(main_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(url_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(recover_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(presets_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(config_partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        scroll_panel.setViewportView(main_panel);

        titulo_ventana.setBackground(new java.awt.Color(102, 153, 255));
        titulo_ventana.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        titulo_ventana.setForeground(new java.awt.Color(255, 255, 255));
        titulo_ventana.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titulo_ventana.setText("CREAR TIMBA");
        titulo_ventana.setDoubleBuffered(true);
        titulo_ventana.setOpaque(true);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_panel)
                .addContainerGap())
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(nick_pass_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(vamos, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(400 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cancel_button, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(400 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(titulo_ventana, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(titulo_ventana)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(nick_pass_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(vamos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cancel_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void vamosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vamosActionPerformed
        // Re-entry guard: in the force_recover flow, formWindowActivated calls this method
        // directly. If a modal gave focus back to the dialog, a second activation would
        // queue up and, without this guard, re-enter and create a SECOND WaitingRoomFrame.
        // dialog_ok only becomes true after a successful commit, so returning here doesn't
        // affect the first call or retries after a validation error (those leave
        // dialog_ok=false).
        if (dialog_ok) {
            return;
        }

        // loadLastGame() is async in normal (non-recovery) mode: if the user ticked "recover" and
        // clicked "Vamos" before the recovered game finished loading, last_game_key is still null and
        // the recover block below (game.get(last_game_key)) would NPE. Bail WITHOUT disabling "Vamos"
        // so the click is a harmless no-op; the load lands in a moment and the user can click again.
        // (In force_recover this can't trigger — there loadLastGame is synchronous, so last_game_key
        // is already resolved by the time this auto-start runs.)
        if (this.recover_checkbox.isSelected() && this.last_game_key == null) {
            return;
        }

        vamos.setEnabled(false);

        // The UPDATE branch (editing options from inside the room) was removed: that
        // editing now lives in the settings wheel (SettingsDialog -> Game tab). Only
        // CREATE/join remains here.
        if (!this.nick.getText().trim().isEmpty() && !this.server_ip_textfield.getText().trim().isEmpty() && !this.server_port_textfield.getText().trim().isEmpty()) {

            vamos.setEnabled(false);

            if (GameFrame.entraSonidoOn()) {
                Audio.playWavResource("misc/laser.wav");
            }

            String elnick = this.nick.getText().trim().replaceAll("\\$", "");

            Helpers.PROPERTIES.setProperty("nick", elnick);

            // Identity: load or generate the Ed25519 keypair bound to the nick the user
            // is about to enter the waiting room. Per-nick files in CORONA_DIR let different
            // test instances on the same machine use distinct identities, and switching back
            // to a known nick reloads the existing keypair. Abort the join if storage fails —
            // networked games cannot proceed without a stable identity.
            IdentityManager im = IdentityManager.initializeForNick(elnick);
            if (!im.isReady()) {
                vamos.setEnabled(true);
                Helpers.mostrarMensajeError(getContentPane(),
                        Translator.translate("ui.identity.load_error", im.getLoadError()));
                return;
            }

            if (this.partida_local) {
                Helpers.PROPERTIES.setProperty("local_ip", this.server_ip_textfield.getText().trim());
            } else {

                Helpers.PROPERTIES.setProperty("server_ip", this.server_ip_textfield.getText().trim());

                if (SERVER_HISTORY_QUEUE.contains(this.server_ip_textfield.getText().trim() + ":" + this.server_port_textfield.getText().trim())) {

                    SERVER_HISTORY_QUEUE.remove(this.server_ip_textfield.getText().trim() + ":" + this.server_port_textfield.getText().trim());
                }

                SERVER_HISTORY_QUEUE.add(this.server_ip_textfield.getText().trim() + ":" + this.server_port_textfield.getText().trim());

                Helpers.PROPERTIES.setProperty("server_history", getServerHistoryString());
            }

            Helpers.PROPERTIES.setProperty(this.partida_local ? "local_port" : "server_port", this.server_port_textfield.getText().trim());

            if (this.avatar != null) {
                Helpers.PROPERTIES.setProperty("avatar", this.avatar.getAbsolutePath());
            } else {
                Helpers.PROPERTIES.setProperty("avatar", "");
            }

            Helpers.savePropertiesFile();

            GameFrame.setRECOVER(this.recover_checkbox.isSelected());

            if (GameFrame.RECOVER) {
                GameFrame.RECOVER_ID = (int) game.get(this.last_game_key).get("id");
            }

            if (this.manos_checkbox.isSelected()) {

                GameFrame.MANOS = (int) this.manos_spinner.getValue();
            } else {
                GameFrame.MANOS = -1;
            }

            GameFrame.THINK_TIME = (int) this.think_time_spinner.getValue();
            GameFrame.THINK_TIME_ENABLED = this.think_time_checkbox.isSelected();

            GameFrame.SHOWDOWN_TIME = (int) this.showdown_time_spinner.getValue();

            GameFrame.REBUY = this.rebuy_checkbox.isSelected();

            GameFrame.BOT_REBUY = this.bot_rebuy_checkbox.isSelected();

            GameFrame.BOT_BALANCE_TO_HUMANS = this.bot_balance_checkbox.isSelected();

            GameFrame.REBUY_LIMIT = this.rebuy_limit_checkbox.isSelected() ? (int) this.rebuy_limit_spinner.getValue() : 0;

            // Rebuy cap (policy): also editable on recover, along with the rest of rebuy
            // (allow / limit / bot rebuy).
            GameFrame.REBUY_CAP_POLICY = this.rebuy_cap_combo.getSelectedIndex() == 1 ? GameFrame.REBUY_CAP_HIGHEST_STACK : GameFrame.REBUY_CAP_BUYIN;

            GameFrame.BLIND_CAP = this.blind_cap_checkbox.isSelected() ? blindCapSelectedBB() : 0f;

            GameFrame.BUYIN = (int) this.buyin_spinner.getValue();

            // "Game" settings (IWTSTH, run-it-twice, rabbit): always applied, even on recover
            // (they're editable before rejoining). Applying them CLEARS the *_RECOVER
            // overrides so that re-saving recover_settings uses the EDITED value, not the
            // original recovered one. (Hand limit and think time are already read above
            // with no guard.)
            GameFrame.IWTSTH_RULE = this.iwtsth_checkbox.isSelected();
            GameFrame.RUN_IT_TWICE = this.rit_checkbox.isSelected();
            GameFrame.RABBIT_HUNTING = this.rabbit_combo.getSelectedIndex();
            GameFrame.IWTSTH_RULE_RECOVER = null;
            GameFrame.RUN_IT_TWICE_RECOVER = null;
            GameFrame.RABBIT_HUNTING_RECOVER = null;

            // Game economy (ante/straddle, mode, and buy-in range): NOT touched on RECOVER
            // (applyRecoverSettings already restored them when the previous game loaded;
            // their controls are disabled and hold stale values). Without this guard, the
            // disabled controls would overwrite the recovered config with defaults and
            // re-persist it corrupted.
            if (!GameFrame.RECOVER) {
                GameFrame.ANTE = this.ante_checkbox.isSelected();

                GameFrame.STRADDLE = this.straddle_checkbox.isSelected();

                GameFrame.FIXED_BUYIN = this.fixed_buyin_checkbox.isSelected();

                GameFrame.BUYIN_MIN_BB = ((Number) this.buyin_min_bb_spinner.getValue()).intValue();

                GameFrame.BUYIN_MAX_BB = ((Number) this.buyin_max_bb_spinner.getValue()).intValue();
            }

            String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            GameFrame.CIEGA_GRANDE = Double.parseDouble(valores_ciegas[1].trim());

            GameFrame.CIEGA_PEQUEÑA = Double.parseDouble(valores_ciegas[0].trim());

            // Active custom structure (null = default ladder). Not touched on RECOVER:
            // applyRecoverSettings already restored it when the previous game loaded. On a
            // new game it reflects the combo's structure and travels to clients in the
            // INIT (C5).
            if (!GameFrame.RECOVER) {
                GameFrame.ACTIVE_BLIND_STRUCTURE = pending_structure != null ? pending_structure.getLevels() : null;
            }

            if (this.doblar_checkbox.isSelected()) {

                if (this.double_blinds_radio_minutos.isSelected()) {
                    GameFrame.CIEGAS_DOUBLE = (int) this.doblar_ciegas_spinner_minutos.getValue();
                    GameFrame.CIEGAS_DOUBLE_TYPE = 1;
                } else {
                    GameFrame.CIEGAS_DOUBLE = (int) this.doblar_ciegas_spinner_manos.getValue();
                    GameFrame.CIEGAS_DOUBLE_TYPE = 2;
                }
            } else {
                GameFrame.CIEGAS_DOUBLE_TYPE = 1;
                GameFrame.CIEGAS_DOUBLE = 0;
            }

            // Issue#9: on recover, the spinners' BUYIN/BLINDS/BLIND-DOUBLE hold the form's
            // defaults (never loaded from the game being resumed — their controls are only
            // disabled visually). Load the real values from the game row before
            // WaitingRoomFrame + GameFrame so a late joiner who sits at the table captures
            // the correct BUYIN in their slot (RemotePlayer field initializer + the matching
            // setStack/setBuyin loop in GameFrame's constructor).
            if (GameFrame.RECOVER) {
                // Both calls take SQL_LOCK on the EDT and BOTH stay synchronous here on purpose.
                // applyRecoveredGameStats must load BUYIN/CIEGAS from the game row BEFORE the
                // WaitingRoomFrame (and later GameFrame) below are built, since their Player slots
                // capture BUYIN at construction; this is also the force_recover path
                // (recover_checkbox.doClick() -> vamos -> setVisible auto-start), which must run
                // synchronously (offloading it caused a prior deadlock). Taking SQL_LOCK on the EDT
                // is safe here: this is pre-game (no Crupier/finTransmision running), so no worker
                // holds SQL_LOCK across a blocking-EDT call and no EDT<->lock cycle can form. See
                // the GameFrame.SQL_LOCK invariant (half 2 -- no lock across a blocking-EDT call --
                // is the load-bearing one, since half 1 is unachievable at this exact spot).
                GameFrame.applyRecoveredGameStats(GameFrame.RECOVER_ID);
                // "Allow rebuy" is editable on recover: game.rebuy is persisted with the edited
                // value so the resume (the Crupier re-reads game.rebuy) doesn't revert it.
                GameFrame.persistRecoverRebuy(GameFrame.RECOVER_ID, GameFrame.REBUY);
            }

            commitBotDifficultyFromCombo();

            this.dialog_ok = true;

            // Identity: warn the host if the game password is weak.
            // Non-blocking informational popup — the user dismisses with OK and proceeds.
            if (this.partida_local && pass_text.getPassword().length > 0) {
                String pwd = new String(pass_text.getPassword());
                int entropyBits = Helpers.estimatePasswordEntropyBits(pwd);
                if (entropyBits < 60) {
                    Helpers.mostrarMensajeInformativo(
                            getContentPane(),
                            Translator.translate("ui.password_debil_aviso", entropyBits));
                }
            }

            WaitingRoomFrame espera = new WaitingRoomFrame(partida_local, elnick, server_ip_textfield.getText().trim() + ":" + server_port_textfield.getText().trim(), avatar, pass_text.getPassword().length == 0 ? null : new String(pass_text.getPassword()), upnp_checkbox.isSelected());

            WaitingRoomFrame.setInstance(espera);

            espera.setLocationRelativeTo(this);

            setVisible(false);

            espera.setVisible(true);

        } else {
            Helpers.mostrarMensajeError(getContentPane(), Translator.translate("ui.error.faltan_campos"));
        }

    }//GEN-LAST:event_vamosActionPerformed

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doblar_checkboxActionPerformed
        this.doblar_ciegas_spinner_minutos.setEnabled(this.doblar_checkbox.isSelected() && this.double_blinds_radio_minutos.isSelected());
        this.doblar_ciegas_spinner_manos.setEnabled(this.doblar_checkbox.isSelected() && this.double_blinds_radio_manos.isSelected());
        this.double_blinds_radio_manos.setEnabled(this.doblar_checkbox.isSelected());
        this.double_blinds_radio_minutos.setEnabled(this.doblar_checkbox.isSelected());
        this.blind_cap_checkbox.setEnabled(this.doblar_checkbox.isSelected());
        setBlindCapControlsEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
    }//GEN-LAST:event_doblar_checkboxActionPerformed

    private void rebuy_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_checkboxActionPerformed
        this.rebuy_limit_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_limit_spinner.setEnabled(this.rebuy_checkbox.isSelected() && this.rebuy_limit_checkbox.isSelected());
        this.bot_rebuy_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_cap_label.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_cap_combo.setEnabled(this.rebuy_checkbox.isSelected());
    }//GEN-LAST:event_rebuy_checkboxActionPerformed

    private void fixed_buyin_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixed_buyin_checkboxActionPerformed
        // Variable (unchecked): the initial buy-in is asked from each player when they sit
        // at the table, so this spinner doesn't apply -> disabled.
        this.buyin_spinner.setEnabled(this.fixed_buyin_checkbox.isSelected());
    }//GEN-LAST:event_fixed_buyin_checkboxActionPerformed

    private void nick_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_nick_labelMouseClicked
        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }

        if (SwingUtilities.isRightMouseButton(evt)) {
            this.avatar = null;

            avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
            Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());
        } else {
            JFileChooser fileChooser = new JFileChooser();

            FileFilter imageFilter = new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes());

            fileChooser.setFileFilter(imageFilter);

            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

            int result = fileChooser.showOpenDialog(this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();

                if (selectedFile.length() > NewGameDialog.AVATAR_MAX_FILESIZE * 1024) {
                    Helpers.mostrarMensajeError(getContentPane(), Translator.translate("ui.max_avatar_size") + " " + NewGameDialog.AVATAR_MAX_FILESIZE + " KB");
                } else {
                    this.avatar = selectedFile;

                    avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
                    Helpers.setScaledIconLabel(avatar_label, avatar.getAbsolutePath(), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

                }

            }

        }
    }//GEN-LAST:event_nick_labelMouseClicked

    private void recover_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recover_checkboxActionPerformed
        if (this.recover_checkbox.isSelected()) {

            if (this.last_game_key != null) {
                applyRecoverSelectedUi();
            } else {
                loadLastGame();
            }

        } else {
            this.game_label.setText("");

            this.fixed_buyin_checkbox.setEnabled(true);

            // The spinner follows the mode: disabled when buy-in is variable.
            this.buyin_spinner.setEnabled(this.fixed_buyin_checkbox.isSelected());

            // The buy-in range becomes editable again. The rebuy-cap policy is re-enabled
            // further down only if rebuy is active.
            this.buyin_min_bb_spinner.setEnabled(true);
            this.buyin_max_bb_spinner.setEnabled(true);
            this.buyin_range_label.setEnabled(true);
            this.buyin_range_sep_label.setEnabled(true);

            this.buyin_label.setEnabled(true);

            this.ciegas_label.setEnabled(true);

            this.ciegas_combobox.setEnabled(true);

            // Back to a new game: re-enables the structure selector, drops the synthetic
            // "(recovered)" item, and restores "Default" + default blinds.
            this.estructura_combobox.setEnabled(true);
            item_recuperada = null;
            pending_structure = null;
            populateStructureCombo(null);
            applySelectedStructure();

            this.doblar_checkbox.setEnabled(true);

            this.rebuy_checkbox.setEnabled(true);

            this.recover_checkbox_label.setOpaque(false);
            this.recover_checkbox_label.setBackground(null);

            if (this.doblar_checkbox.isSelected()) {

                this.double_blinds_radio_minutos.setEnabled(true);

                this.double_blinds_radio_manos.setEnabled(true);

                this.doblar_ciegas_spinner_manos.setEnabled(this.double_blinds_radio_manos.isSelected());

                this.doblar_ciegas_spinner_minutos.setEnabled(this.double_blinds_radio_minutos.isSelected());

                this.blind_cap_checkbox.setEnabled(true);

                setBlindCapControlsEnabled(this.blind_cap_checkbox.isSelected());

            } else {
                this.double_blinds_radio_minutos.setEnabled(false);

                this.double_blinds_radio_manos.setEnabled(false);

                this.doblar_ciegas_spinner_manos.setEnabled(false);

                this.doblar_ciegas_spinner_minutos.setEnabled(false);

                this.blind_cap_checkbox.setEnabled(false);

                setBlindCapControlsEnabled(false);
            }

            if (this.rebuy_checkbox.isSelected()) {
                this.rebuy_limit_checkbox.setEnabled(true);
                this.rebuy_limit_spinner.setEnabled(this.rebuy_limit_checkbox.isSelected());
                this.bot_rebuy_checkbox.setEnabled(true);
                this.rebuy_cap_label.setEnabled(true);
                this.rebuy_cap_combo.setEnabled(true);
            } else {
                this.rebuy_limit_checkbox.setEnabled(false);
                this.rebuy_limit_spinner.setEnabled(false);
                this.bot_rebuy_checkbox.setEnabled(false);
                this.rebuy_cap_label.setEnabled(false);
                this.rebuy_cap_combo.setEnabled(false);
            }

            // Restore ante/straddle, bots, hand limit, and presets to their new-game state.
            this.ante_checkbox.setEnabled(true);
            this.straddle_checkbox.setEnabled(true);
            this.bots_combobox.setEnabled(true);
            this.bots_label.setEnabled(true);
            this.manos_checkbox.setEnabled(true);
            this.manos_spinner.setEnabled(this.manos_checkbox.isSelected());
            this.iwtsth_checkbox.setEnabled(true);
            this.rit_checkbox.setEnabled(true);
            this.rabbit_combo.setEnabled(true);
            this.presets_combobox.setEnabled(true);
            this.preset_save_button.setEnabled(true);
            this.preset_delete_button.setEnabled(this.presets_combobox.getSelectedIndex() > 0);
            this.preset_label.setEnabled(true);

            this.nick.setEnabled(true);

            packPreservingCenter();
        }

    }//GEN-LAST:event_recover_checkboxActionPerformed

    /**
     * EDT. Applies the "recover selected" UI state: locks the fixed game economy (buy-in, blinds,
     * structure, ante/straddle, presets), keeps the editable game settings enabled, and shows the
     * recovered game key. Assumes {@code last_game_key} is already set (by applyLoadedLastGame).
     */
    private void applyRecoverSelectedUi() {
        this.game_label.setText(this.last_game_key);

        this.buyin_spinner.setEnabled(false);

        this.buyin_label.setEnabled(false);

        this.ciegas_label.setEnabled(false);

        this.ciegas_combobox.setEnabled(false);

        this.estructura_combobox.setEnabled(false);

        syncStructureComboForRecover();

        this.doblar_ciegas_spinner_minutos.setEnabled(false);

        this.double_blinds_radio_minutos.setEnabled(false);

        this.doblar_ciegas_spinner_manos.setEnabled(false);

        this.double_blinds_radio_manos.setEnabled(false);

        this.doblar_checkbox.setEnabled(false);

        this.blind_cap_checkbox.setEnabled(false);

        setBlindCapControlsEnabled(false);

        this.fixed_buyin_checkbox.setEnabled(false);

        this.buyin_min_bb_spinner.setEnabled(false);

        this.buyin_max_bb_spinner.setEnabled(false);

        this.buyin_range_label.setEnabled(false);

        this.buyin_range_sep_label.setEnabled(false);

        // Ante/straddle and presets: locked on recover (game economy is fixed;
        // ante/straddle are dead money tied to the blinds).
        this.ante_checkbox.setEnabled(false);
        this.straddle_checkbox.setEnabled(false);
        this.presets_combobox.setEnabled(false);
        this.preset_save_button.setEnabled(false);
        this.preset_delete_button.setEnabled(false);
        this.preset_label.setEnabled(false);
        // "Game" settings (hand limit, IWTSTH, run-it-twice, rabbit, think time) + bot
        // difficulty: EDITABLE before rejoining. loadLastGame already populated them
        // with the recovered game's values; this just guarantees they're enabled.
        this.bots_combobox.setEnabled(true);
        this.bots_label.setEnabled(true);
        this.manos_checkbox.setEnabled(true);
        this.manos_spinner.setEnabled(this.manos_checkbox.isSelected());
        this.iwtsth_checkbox.setEnabled(true);
        this.rit_checkbox.setEnabled(true);
        this.rabbit_combo.setEnabled(true);
        this.think_time_checkbox.setEnabled(true);
        this.think_time_spinner.setEnabled(this.think_time_checkbox.isSelected());
        this.showdown_time_spinner.setEnabled(true);
        // Rebuy (allow / limit / bot rebuy / cap): EDITABLE on recover. Enabled state
        // follows "allow rebuy" (same as rebuy_checkboxActionPerformed).
        this.rebuy_checkbox.setEnabled(true);
        this.rebuy_limit_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_limit_spinner.setEnabled(this.rebuy_checkbox.isSelected() && this.rebuy_limit_checkbox.isSelected());
        this.bot_rebuy_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_cap_label.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_cap_combo.setEnabled(this.rebuy_checkbox.isSelected());

        this.recover_checkbox_label.setOpaque(true);

        this.recover_checkbox_label.setBackground(Color.YELLOW);

        String[] parts = this.last_game_key.split(" @ ");

        this.nick.setText(parts[0]);

        this.nick.setEnabled(false);

        packPreservingCenter();

        if (!this.force_recover) {

            Helpers.mostrarMensajeInformativo(this, Translator.translate("player.en_el_bmodo_recuperacionb_se"), "justify", (int) Math.round(getWidth() * 0.8f), new ImageIcon(getClass().getResource("/images/action/robot.png")));
        }
    }

    /** EDT. Shown when there is no recoverable game: unticks and disables the recover checkbox. */
    private void showNoRecoverableGamesUi() {
        this.recover_checkbox_label.setOpaque(false);
        this.recover_checkbox_label.setBackground(null);
        this.recover_checkbox.setSelected(false);
        this.game_label.setText("");
        this.recover_checkbox.setEnabled(false);
        Helpers.mostrarMensajeError(this, Translator.translate("game.no_hay_timbas_que_se"));

        packPreservingCenter();
    }

    private void pass_textActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pass_textActionPerformed
        vamos.doClick();
    }//GEN-LAST:event_pass_textActionPerformed

    private void server_port_textfieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_server_port_textfieldActionPerformed
        vamos.doClick();
    }//GEN-LAST:event_server_port_textfieldActionPerformed

    private void double_blinds_radio_minutosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_double_blinds_radio_minutosActionPerformed
        if (this.double_blinds_radio_minutos.isSelected()) {
            this.doblar_ciegas_spinner_minutos.setEnabled(true);
            this.double_blinds_radio_manos.setSelected(false);
            this.doblar_ciegas_spinner_manos.setEnabled(false);
        } else {
            this.double_blinds_radio_minutos.setSelected(true);
        }

    }//GEN-LAST:event_double_blinds_radio_minutosActionPerformed

    private void double_blinds_radio_manosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_double_blinds_radio_manosActionPerformed
        if (this.double_blinds_radio_manos.isSelected()) {
            this.doblar_ciegas_spinner_manos.setEnabled(true);
            this.double_blinds_radio_minutos.setSelected(false);
            this.doblar_ciegas_spinner_minutos.setEnabled(false);
        } else {
            this.double_blinds_radio_manos.setSelected(true);
        }
    }//GEN-LAST:event_double_blinds_radio_manosActionPerformed

    private void server_ip_textfieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_server_ip_textfieldKeyReleased
        if (!SERVER_HISTORY_QUEUE.isEmpty()) {

            if (evt.getKeyCode() == KeyEvent.VK_UP && conta_history <= SERVER_HISTORY_QUEUE.size() - 2) {

                conta_history++;

                String[] history = SERVER_HISTORY_QUEUE.toArray(new String[0]);

                String[] parts = history[conta_history].split(":");

                server_ip_textfield.setText(parts[0]);

                try {
                    ((AbstractDocument) server_port_textfield.getDocument()).remove(0, ((AbstractDocument) server_port_textfield.getDocument()).getLength());
                    ((AbstractDocument) server_port_textfield.getDocument()).insertString(0, parts[1], null);
                } catch (BadLocationException ex) {
                    Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else if (evt.getKeyCode() == KeyEvent.VK_DOWN && conta_history >= 1) {

                conta_history--;

                String[] history = SERVER_HISTORY_QUEUE.toArray(new String[0]);

                String[] parts = history[conta_history].split(":");

                server_ip_textfield.setText(parts[0]);

                try {
                    ((AbstractDocument) server_port_textfield.getDocument()).remove(0, ((AbstractDocument) server_port_textfield.getDocument()).getLength());
                    ((AbstractDocument) server_port_textfield.getDocument()).insertString(0, parts[1], null);
                } catch (BadLocationException ex) {
                    Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
    }//GEN-LAST:event_server_ip_textfieldKeyReleased

    private void server_ip_textfieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_server_ip_textfieldActionPerformed
        vamos.doClick();
    }//GEN-LAST:event_server_ip_textfieldActionPerformed

    private void ciegas_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ciegas_comboboxActionPerformed
        // Live info label, OUTSIDE the init gate: this way it also refreshes when the combo
        // changes from loading a preset or a structure (which suppress init but still fire
        // this handler when rebuilding/selecting the level).
        updateAnteStraddleLabels();

        if (init) {

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            double ciega_pequena = Double.valueOf(valores[0].trim());

            double ciega_grande = Double.valueOf(valores[1].trim());

            // Spinner step is derived from the small blind's magnitude, not the combo
            // index: for the default 1-2-3-5 ladder it matches the old
            // pow(10, floor(index/4)) exactly, but it also works with custom structures
            // that have arbitrary levels.
            int buyin_lo_cg = BuyinRules.min(ciega_grande, working_min_bb);
            int buyin_hi_cg = Math.max(buyin_lo_cg, BuyinRules.max(ciega_grande, working_max_bb));
            buyin_spinner.setModel(new SpinnerNumberModel(BuyinRules.defaultBuyin(ciega_grande, working_min_bb, working_max_bb), buyin_lo_cg, buyin_hi_cg, (BUYIN_SPINNER_STEP = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(ciega_pequena)) + 1)))));

            Helpers.makeNumericSpinnerEditable(buyin_spinner, false);

            modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());

            packPreservingCenter();

        }
    }//GEN-LAST:event_ciegas_comboboxActionPerformed

    // Derived INFO label: shows in parentheses the CURRENT ante amount (= small blind) and
    // straddle amount (= 2x big blind), read from the selected blind level, refreshed live
    // on change. The amounts themselves are FIXED by code (small blind / double big
    // blind), not configurable — this is just the display text.
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
            straddle_icon.setText("Straddle (" + Helpers.money2String(Helpers.doubleClean(2 * bb)) + ")");
        } catch (NumberFormatException ignored) {
        }
    }

    // Blind structure chosen for this game (null = default 1-2-3-5 ladder). Drives the
    // blinds combo's levels and, on creating the game, GameFrame.ACTIVE_BLIND_STRUCTURE.
    private BlindStructure pending_structure = null;

    // Special markers in the structure combo; every other item is a custom structure's name.
    private String item_por_defecto;
    private String item_gestionar;
    // Synthetic label for a recovered structure that's no longer saved (only appears in
    // read-only recover mode). null if not applicable.
    private String item_recuperada;

    // (Re)fills the structure combo: "Default" + custom ones + "Manage…". Reselects by
    // name if given and it still exists. Doesn't trigger the selection logic (init is
    // lowered while repopulating).
    private void populateStructureCombo(String selectName) {
        boolean prev_init = init;
        init = false;
        try {
            item_por_defecto = Translator.translate("blinds.estructura_por_defecto");
            item_gestionar = Translator.translate("blinds.gestionar");
            estructura_combobox.removeAllItems();
            estructura_combobox.addItem(item_por_defecto);
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

    private void estructura_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_estructura_comboboxActionPerformed
        if (!init) {
            return;
        }
        Object sel = estructura_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        if (sel.equals(item_gestionar)) {
            // Open the editor; on close, reload keeping the active structure (fall back
            // to "Default" if it was deleted/renamed).
            String previous = pending_structure != null ? pending_structure.getName() : item_por_defecto;
            BlindStructureManagerDialog mgr = new BlindStructureManagerDialog(this);
            mgr.setVisible(true);
            if (!item_por_defecto.equals(previous) && !BlindStructure.loadAll().containsKey(previous)) {
                previous = item_por_defecto;
            }
            populateStructureCombo(previous);
            applySelectedStructure();
            return;
        }
        applySelectedStructure();
    }//GEN-LAST:event_estructura_comboboxActionPerformed

    // Applies the selected structure to the levels combo (ciegas_combobox) and to
    // pending_structure. "Default" => null + 1-2-3-5 ladder; custom => its levels. Keeps
    // the local "sb / bb" format (same as the original combo).
    private void applySelectedStructure() {
        Object sel = estructura_combobox.getSelectedItem();
        if (item_recuperada != null && item_recuperada.equals(sel)) {
            // Synthetic item for a recovered structure (read-only): does nothing.
            return;
        }
        double[][] levels;
        if (sel == null || sel.equals(item_por_defecto) || sel.equals(item_gestionar)) {
            pending_structure = null;
            levels = BlindStructure.defaultLevels();
        } else {
            BlindStructure bs = BlindStructure.loadAll().get((String) sel);
            pending_structure = bs;
            levels = bs != null ? bs.getLevels() : BlindStructure.defaultLevels();
        }
        String[] items = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
        }
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        ciegas_combobox.setSelectedIndex(0);
        // Recompute buy-in + cap for the new ladder (setModel doesn't reliably fire the listener).
        ciegas_comboboxActionPerformed(null);
    }

    // Initializes the structure selector from the active structure parameter (null =
    // default ladder), reflecting it in the levels combo. Call this BEFORE the logic that
    // looks up and selects the current blind level in the combo.
    private void initBlindStructureUIFrom(double[][] active) {
        pending_structure = null;
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
                // R1: the active structure is no longer saved (deleted or edited since the
                // game was configured). Keep it as an anonymous "in use" structure so SAVE
                // settings doesn't silently revert it to the default ladder. (The combo
                // shows "Default"; picking another entry replaces it as usual.)
                try {
                    pending_structure = new BlindStructure(Translator.translate("blinds.estructura_actual"), active);
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        // ALWAYS populate the combo from the effective ladder (the structure in use, or
        // the default one without it) so it includes every level from defaultLevels(), not
        // the designer's fixed list.
        double[][] levels = pending_structure != null ? pending_structure.getLevels() : BlindStructure.defaultLevels();
        String[] items = new String[levels.length];
        for (int k = 0; k < levels.length; k++) {
            items[k] = BlindStructure.formatLevel(levels[k][0], levels[k][1]);
        }
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        populateStructureCombo(selectName);
    }

    // After loading a game to recover (loadLastGame already restored
    // GameFrame.ACTIVE_BLIND_STRUCTURE), reflects the recovered structure in the combo
    // even if it's NOT among the user's saved ones: shows its name if it matches a saved
    // one, otherwise a read-only synthetic "(recovered)" item. The levels combo shows the
    // recovered blinds. The engine recovers using ACTIVE regardless of what happens here —
    // this is only the display label.
    private void syncStructureComboForRecover() {
        item_recuperada = null;
        double[][] active = GameFrame.ACTIVE_BLIND_STRUCTURE;
        if (active == null) {
            pending_structure = null;
            populateStructureCombo(null);
            return;
        }
        String matchName = null;
        for (java.util.Map.Entry<String, BlindStructure> e : BlindStructure.loadAll().entrySet()) {
            if (java.util.Arrays.deepEquals(e.getValue().getLevels(), active)) {
                matchName = e.getKey();
                pending_structure = e.getValue();
                break;
            }
        }
        if (matchName != null) {
            populateStructureCombo(matchName);
        } else {
            pending_structure = null;
            boolean prev_init = init;
            init = false;
            try {
                populateStructureCombo(null);
                item_recuperada = Translator.translate("blinds.estructura_recuperada");
                estructura_combobox.insertItemAt(item_recuperada, 1);
                estructura_combobox.setSelectedItem(item_recuperada);
            } finally {
                init = prev_init;
            }
        }
        String[] items = new String[active.length];
        for (int k = 0; k < active.length; k++) {
            items[k] = BlindStructure.formatLevel(active[k][0], active[k][1]);
        }
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
    }

    // The blind cap is chosen as a "number of raises" (how many times the blinds may raise
    // at most, from the chosen initial level). The spinner holds that integer, and
    // blind_cap_label shows the resulting level live. Internally GameFrame.BLIND_CAP is
    // still the big blind of that level (double), so the blind-freezing / recover /
    // network logic doesn't change.

    // Big blind (second number) of a levels-combo item.
    private double parseBlindLevelBB(String item) {
        return Double.parseDouble(item.replace(",", ".").split("/")[1].trim());
    }

    // Combo index of the level after n raises from the initial blinds, capped at the last
    // available level.
    private int blindCapTargetIndex(int n) {
        int last = ciegas_combobox.getModel().getSize() - 1;
        return Math.min(Math.max(0, ciegas_combobox.getSelectedIndex()) + n, last);
    }

    // Big blind of the cap level for the current number of raises (for saving).
    private double blindCapSelectedBB() {
        return parseBlindLevelBB(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private void updateBlindCapLabel() {
        blind_cap_label.setText(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    // Enables/disables the blind-cap spinner and its label ("n / m") together, so the
    // label dims along with the spinner when the cap or the parent "Increase blinds"
    // checkbox is off (like the other panels).
    private void setBlindCapControlsEnabled(boolean enabled) {
        blind_cap_spinner.setEnabled(enabled);
        blind_cap_label.setEnabled(enabled);
    }

    // Rebuilds the number of raises from the saved GameFrame.BLIND_CAP (looks up the level
    // whose big blind matches); defaults to 5 if there's no saved cap.
    private int blindCapDoublingsFromCap() {
        return blindCapDoublingsFromCap(GameFrame.BLIND_CAP);
    }

    // Same, but taking the cap (cap level's big blind) as a parameter, to rebuild the
    // number of raises when loading a preset without going through GameFrame.
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

    // Models the spinner as number of raises (1..levels above the initial one) and
    // refreshes the label.
    private void modelBlindCapSpinner(int n) {
        int levels_above = Math.max(1, ciegas_combobox.getModel().getSize() - 1 - Math.max(0, ciegas_combobox.getSelectedIndex()));
        n = Math.min(Math.max(1, n), levels_above);
        this.blind_cap_spinner.setModel(new SpinnerNumberModel(n, 1, levels_above, 1));
        Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
        updateBlindCapLabel();
    }

    private void buyin_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_spinnerStateChanged
        // No sound: the spinner's clicking sounded like a slot machine.
    }//GEN-LAST:event_buyin_spinnerStateChanged

    // Lower/upper bounds of the buy-in range, in big blinds (BB). The money engine still
    // works in chips: these spinners only set the multipliers that BuyinRules converts to
    // chips. They're cross-clamped to keep lower < upper within [FLOOR_MIN_BB, CEIL_MAX_BB].
    private static final int BUYIN_RANGE_STEP = 5;
    private boolean adjusting_buyin_range = false;

    // LOCAL working store for the buy-in range (min/max BB) and the rebuy-cap policy.
    // These controls used to scratch directly on the static
    // GameFrame.BUYIN_MIN_BB/MAX_BB/REBUY_CAP_POLICY, which BROKE the transactional model:
    // touching the spinners mutated global state live, so canceling the dialog (without
    // creating the game) left the changes saved. Now the live logic operates on these
    // fields and GameFrame is only written on CREATE.
    private int working_min_bb;
    private int working_max_bb;
    private int working_rebuy_cap_policy;

    // Initializes the range spinners and the rebuy-cap policy combo from the local working
    // store (working_*), validating the bounds.
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

        // Policy combo: index 0 = BUYIN, index 1 = the highest player's stack (indices
        // match the GameFrame.REBUY_CAP_* constants).
        rebuy_cap_combo.removeAllItems();
        rebuy_cap_combo.addItem(Translator.translate("rebuy.cap_policy_buyin"));
        rebuy_cap_combo.addItem(Translator.translate("rebuy.cap_policy_highest"));
        rebuy_cap_combo.setSelectedIndex(working_rebuy_cap_policy == GameFrame.REBUY_CAP_HIGHEST_STACK ? 1 : 0);
        Helpers.setTranslatedToolTip(rebuy_cap_combo, "rebuy.tope_recompra_tooltip");
    }

    // Rebuilds the buy-in spinner's model with the current [min,max] BB bounds for the
    // selected big blind, clamping the value to stay within them.
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
        BUYIN_SPINNER_STEP = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(cp)) + 1));
        buyin_spinner.setModel(new SpinnerNumberModel(val, lo, hi, BUYIN_SPINNER_STEP));
        Helpers.makeNumericSpinnerEditable(buyin_spinner, false);
    }

    private void buyin_min_bb_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_min_bb_spinnerStateChanged
        onBuyinRangeChanged(true);
    }//GEN-LAST:event_buyin_min_bb_spinnerStateChanged

    private void buyin_max_bb_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_max_bb_spinnerStateChanged
        onBuyinRangeChanged(false);
    }//GEN-LAST:event_buyin_max_bb_spinnerStateChanged

    // Applies a change on either range spinner: keeps lower < upper (pushing the other end
    // if needed, within the caps), propagates to the local working store, and rebuilds the
    // buy-in spinner.
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
        packPreservingCenter();
    }

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }

        if (force_recover) {
            vamosActionPerformed(null);
        }

    }//GEN-LAST:event_formWindowActivated

    private void manos_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manos_checkboxActionPerformed
        this.manos_spinner.setEnabled(this.manos_checkbox.isSelected());
    }//GEN-LAST:event_manos_checkboxActionPerformed

    private void think_time_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_think_time_checkboxActionPerformed
        this.think_time_spinner.setEnabled(this.think_time_checkbox.isSelected());
    }//GEN-LAST:event_think_time_checkboxActionPerformed

    private void blind_cap_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_blind_cap_checkboxActionPerformed
        setBlindCapControlsEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
    }//GEN-LAST:event_blind_cap_checkboxActionPerformed

    private void rebuy_limit_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_limit_checkboxActionPerformed
        this.rebuy_limit_spinner.setEnabled(this.rebuy_checkbox.isSelected() && this.rebuy_limit_checkbox.isSelected());
    }//GEN-LAST:event_rebuy_limit_checkboxActionPerformed

    private void recover_checkbox_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_recover_checkbox_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        recover_checkbox.doClick();
    }//GEN-LAST:event_recover_checkbox_labelMouseClicked

    private void recomprar_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_recomprar_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        rebuy_checkbox.doClick();
    }//GEN-LAST:event_recomprar_labelMouseClicked

    private void limite_manos_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_limite_manos_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        manos_checkbox.doClick();
    }//GEN-LAST:event_limite_manos_labelMouseClicked

    private void think_time_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_think_time_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        think_time_checkbox.doClick();
    }//GEN-LAST:event_think_time_labelMouseClicked

    private void straddle_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_straddle_iconMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        straddle_checkbox.doClick();
    }//GEN-LAST:event_straddle_iconMouseClicked

    private void iwtsth_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_iwtsth_iconMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        iwtsth_checkbox.doClick();
    }//GEN-LAST:event_iwtsth_iconMouseClicked

    private void rit_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_rit_iconMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        rit_checkbox.doClick();
    }//GEN-LAST:event_rit_iconMouseClicked

    private void nickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nickActionPerformed
        vamos.doClick();
    }//GEN-LAST:event_nickActionPerformed

    private void avatar_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatar_labelMouseClicked
        this.nick_labelMouseClicked(evt);
    }//GEN-LAST:event_avatar_labelMouseClicked

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        dispose();
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void bots_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bots_comboboxActionPerformed
        // Selection is committed only when the user accepts the dialog (see vamosActionPerformed).
    }//GEN-LAST:event_bots_comboboxActionPerformed

    // ===== New-game presets (only when creating a game as host) ================
    // A preset saves the ENTIRE game configuration (blinds, chosen structure, buy-in,
    // rebuy, escalation, cap, hand limit, ante, straddle, bots), like the user's own blind
    // structures. The dialog maps its controls to/from GamePreset.Settings and never
    // touches GameFrame: the buy-in range and rebuy-cap policy go through the dialog's own
    // local working store instead (working_min_bb/working_max_bb, see
    // initBuyinRangeAndCapUI). Loading a preset and then canceling leaves no trace.

    private static final int MAX_PRESET_NAME_LENGTH = 40;
    // Suppresses loading while repopulating the combo, or whenever the internal guard requires it.
    private boolean suppress_preset_combo = false;

    // (Re)fills the presets combo: "(choose preset)" marker + saved names. Doesn't trigger
    // loading (lowers the guard while repopulating). Reselects by name if given.
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

    private void presets_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_presets_comboboxActionPerformed
        if (suppress_preset_combo) {
            return;
        }
        int idx = presets_combobox.getSelectedIndex();
        preset_delete_button.setEnabled(idx > 0);
        if (idx <= 0) {
            // "Default": restores a new game's factory configuration (same as picking
            // "Default" in the structure combo).
            applySettingsToControls(new GamePreset.Settings());
            return;
        }
        GamePreset preset = GamePreset.loadAll().get((String) presets_combobox.getSelectedItem());
        if (preset != null) {
            applySettingsToControls(GamePreset.Settings.parse(preset.getSettings()));
        }
    }//GEN-LAST:event_presets_comboboxActionPerformed

    private void preset_save_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preset_save_buttonActionPerformed
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
    }//GEN-LAST:event_preset_save_buttonActionPerformed

    private void preset_delete_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preset_delete_buttonActionPerformed
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
    }//GEN-LAST:event_preset_delete_buttonActionPerformed

    // Prompts for a preset name (dialog styled with the app's font, like the structure
    // editor). Returns null on cancel or if left empty.
    private String promptPresetName() {
        javax.swing.JLabel prompt = new javax.swing.JLabel(Translator.translate("newgame.preset_nombre"));
        javax.swing.JTextField field = new javax.swing.JTextField("", 18);
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        panel.add(prompt, java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);
        javax.swing.JOptionPane pane = new javax.swing.JOptionPane(panel, javax.swing.JOptionPane.PLAIN_MESSAGE, javax.swing.JOptionPane.OK_CANCEL_OPTION);
        javax.swing.JDialog d = pane.createDialog(this, getTitle());
        Helpers.updateFonts(pane, Helpers.GUI_FONT, 1.15f);
        d.pack();
        d.setLocationRelativeTo(this);
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

    // Reads the controls' CURRENT configuration into a Settings (without touching
    // GameFrame). Same mapping as vamosActionPerformed's commit.
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
        s.difficulty = partida_local ? botDifficultyFromComboIndex(bots_combobox.getSelectedIndex()) : Bot.DIFFICULTY;
        return s;
    }

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

    // Selects the given "sb / bb" blind level in the levels combo, if it exists.
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

    // Dumps a Settings into the dialog's controls (never touches GameFrame — the buy-in
    // range and rebuy-cap policy go through the dialog's local working store instead, see
    // initBuyinRangeAndCapUI). Order: toggles/enables first, then structure -> level ->
    // buy-in/cap last, since those depend on the chosen level.
    private void applySettingsToControls(GamePreset.Settings s) {
        boolean prev_init = init;
        init = false;
        try {
            // Increase blinds + minutes/hands.
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

            // Hand limit.
            manos_checkbox.setSelected(s.handLimit > 0);
            manos_spinner.setEnabled(s.handLimit > 0);
            manos_spinner.setModel(new SpinnerNumberModel(s.handLimit > 0 ? s.handLimit : 100, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(manos_spinner, false);
            ((javax.swing.JSpinner.DefaultEditor) manos_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);

            // Think time.
            think_time_checkbox.setSelected(s.thinkTimeEnabled);
            think_time_spinner.setValue(Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, s.thinkTime)));
            think_time_spinner.setEnabled(s.thinkTimeEnabled);
            Helpers.makeNumericSpinnerEditable(think_time_spinner, false);

            // Showdown time (no checkbox: always active).
            showdown_time_spinner.setValue(Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, s.showdownTime)));
            Helpers.makeNumericSpinnerEditable(showdown_time_spinner, false);

            // Rebuy + ante + straddle.
            rebuy_checkbox.setSelected(s.rebuy);
            ante_checkbox.setSelected(s.ante);
            straddle_checkbox.setSelected(s.straddle);
            iwtsth_checkbox.setSelected(s.iwtsth);
            rit_checkbox.setSelected(s.runItTwice);
            // The rabbit combo is populated (translated) here, since nothing else sets
            // its model for a new game, otherwise the dropdown would come up empty.
            // Index 0..3 = off/free/free+sb/free+sb+bb.
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

            // Buy-in mode.
            fixed_buyin_checkbox.setSelected(s.fixedBuyin);
            buyin_spinner.setEnabled(s.fixedBuyin);

            // Buy-in range + rebuy-cap policy: LOCAL working store (not GameFrame), so the
            // transactional model doesn't break; GameFrame is only written when the game
            // is CREATED.
            working_min_bb = s.minBb;
            working_max_bb = s.maxBb;
            working_rebuy_cap_policy = s.rebuyCapPolicy;
            initBuyinRangeAndCapUI();

            // Rebuy limit.
            rebuy_limit_checkbox.setSelected(s.rebuyLimit > 0);
            rebuy_limit_checkbox.setEnabled(s.rebuy);
            rebuy_limit_spinner.setEnabled(s.rebuy && s.rebuyLimit > 0);
            rebuy_limit_spinner.setModel(new SpinnerNumberModel(s.rebuyLimit > 0 ? s.rebuyLimit : 3, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(rebuy_limit_spinner, false);
            rebuy_cap_label.setEnabled(s.rebuy);
            rebuy_cap_combo.setEnabled(s.rebuy);

            // Blind cap (checkbox + enable; the spinner's model is set further down).
            blind_cap_checkbox.setSelected(s.blindCap > 0);
            blind_cap_checkbox.setEnabled(s.doubleEvery > 0);

            // Structure -> combo levels -> current level.
            initBlindStructureUIFrom(s.structure);
            double[][] levels = s.structure != null ? s.structure : BlindStructure.defaultLevels();
            String[] items = new String[levels.length];
            for (int i = 0; i < levels.length; i++) {
                items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
            selectCurrentBlindLevel(s.smallBlind, s.bigBlind);

            // Buy-in for the chosen level (clamps the preset's value to the range).
            rebuildBuyinSpinnerModel();
            SpinnerNumberModel bm = (SpinnerNumberModel) buyin_spinner.getModel();
            int blo = ((Number) bm.getMinimum()).intValue();
            int bhi = ((Number) bm.getMaximum()).intValue();
            buyin_spinner.setValue(Math.max(blo, Math.min(s.buyin, bhi)));

            // Blind cap: number of raises rebuilt from the preset's cap.
            modelBlindCapSpinner(blindCapDoublingsFromCap(s.blindCap));
            setBlindCapControlsEnabled(s.doubleEvery > 0 && s.blindCap > 0);

            // Bot difficulty.
            if (partida_local) {
                bots_combobox.setSelectedIndex(botComboIndexFromDifficulty(s.difficulty));
            }
        } finally {
            init = prev_init;
        }
        packPreservingCenter();
    }

    private void packPreservingCenter() {

        int center_x = getX() + getWidth() / 2;

        int center_y = getY() + getHeight() / 2;

        pack();

        Rectangle screen = getGraphicsConfiguration().getBounds();

        int x = Math.max(screen.x, Math.min(center_x - getWidth() / 2, screen.x + screen.width - getWidth()));

        int y = Math.max(screen.y, Math.min(center_y - getHeight() / 2, screen.y + screen.height - getHeight()));

        setLocation(x, y);
    }

    private void commitBotDifficultyFromCombo() {
        if (!partida_local) {
            return;
        }
        switch (bots_combobox.getSelectedIndex()) {
            case 0:
                Bot.DIFFICULTY = Bot.Difficulty.EASY;
                break;
            case 1:
                Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
                break;
            case 2:
                Bot.DIFFICULTY = Bot.Difficulty.HARD;
                break;
            default:
                Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
                break;
        }
    }

    // i18n tooltips (setTranslatedToolTip => re-translated on language change) for config
    // controls whose purpose isn't obvious from their label. Called after
    // initComponents() and OVERRIDES any hardcoded setToolTipText set inside initComponents.
    private void setupTooltips() {
        Helpers.setTranslatedToolTip(server_label, "tooltip.cfg.server_ip");
        Helpers.setTranslatedToolTip(server_ip_textfield, "tooltip.cfg.server_ip");
        Helpers.setTranslatedToolTip(server_port_puntos, "tooltip.cfg.server_port");
        Helpers.setTranslatedToolTip(server_port_textfield, "tooltip.cfg.server_port");
        Helpers.setTranslatedToolTip(upnp_checkbox, "tooltip.cfg.upnp");
        Helpers.setTranslatedToolTip(preset_label, "tooltip.cfg.preset");
        Helpers.setTranslatedToolTip(presets_combobox, "tooltip.cfg.preset");
        Helpers.setTranslatedToolTip(preset_save_button, "tooltip.cfg.preset_save");
        Helpers.setTranslatedToolTip(preset_delete_button, "tooltip.cfg.preset_delete");
        Helpers.setTranslatedToolTip(manos_checkbox, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(limite_manos_label, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(manos_spinner, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(think_time_checkbox, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(think_time_label, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(think_time_spinner, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(showdown_time_label, "tooltip.cfg.showdown_time");
        Helpers.setTranslatedToolTip(showdown_time_spinner, "tooltip.cfg.showdown_time");
        Helpers.setTranslatedToolTip(iwtsth_checkbox, "tooltip.cfg.iwtsth");
        Helpers.setTranslatedToolTip(iwtsth_icon, "tooltip.cfg.iwtsth");
        Helpers.setTranslatedToolTip(rit_checkbox, "tooltip.cfg.rit");
        Helpers.setTranslatedToolTip(rit_icon, "tooltip.cfg.rit");
        Helpers.setTranslatedToolTip(rabbit_combo, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(rabbit_label, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(rabbit_icon, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(estructura_label, "tooltip.cfg.structure");
        Helpers.setTranslatedToolTip(estructura_combobox, "tooltip.cfg.structure");
        Helpers.setTranslatedToolTip(ciegas_label, "tooltip.cfg.blinds_level");
        Helpers.setTranslatedToolTip(ciegas_combobox, "tooltip.cfg.blinds_level");
        Helpers.setTranslatedToolTip(doblar_checkbox, "tooltip.cfg.double_blinds");
        Helpers.setTranslatedToolTip(blind_cap_checkbox, "tooltip.cfg.blind_cap");
        Helpers.setTranslatedToolTip(blind_cap_spinner, "tooltip.cfg.blind_cap");
        Helpers.setTranslatedToolTip(ante_checkbox, "tooltip.cfg.ante");
        Helpers.setTranslatedToolTip(straddle_checkbox, "tooltip.cfg.straddle");
        Helpers.setTranslatedToolTip(straddle_icon, "tooltip.cfg.straddle");
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
        Helpers.setTranslatedToolTip(bot_rebuy_checkbox, "tooltip.cfg.bot_rebuy");
        Helpers.setTranslatedToolTip(bot_balance_checkbox, "tooltip.cfg.bot_balance");
        Helpers.setTranslatedToolTip(bots_label, "tooltip.cfg.bots");
        Helpers.setTranslatedToolTip(bots_combobox, "tooltip.cfg.bots");
        Helpers.setTranslatedToolTip(rebuy_cap_label, "rebuy.tope_recompra_tooltip");
        // The avatar and the "Nick:" label share the same gesture (avatar_labelMouseClicked
        // delegates to nick_labelMouseClicked): a normal click opens the image picker, a
        // right click restores the default avatar.
        Helpers.setTranslatedToolTip(avatar_label, "tooltip.change_avatar");
        Helpers.setTranslatedToolTip(nick_label, "tooltip.change_avatar");
        Helpers.setTranslatedToolTip(recover_checkbox, "tooltip.cfg.recover");
        Helpers.setTranslatedToolTip(recover_checkbox_label, "tooltip.cfg.recover");
        // rebuy_cap_combo already has its own tooltip ("rebuy.tope_recompra_tooltip") set in initComponents.
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox ante_checkbox;
    private javax.swing.JPanel aumento_panel;
    private javax.swing.JLabel avatar_label;
    private javax.swing.JCheckBox blind_cap_checkbox;
    private javax.swing.JLabel blind_cap_label;
    private javax.swing.JSpinner blind_cap_spinner;
    private javax.swing.JCheckBox bot_rebuy_checkbox;
    private javax.swing.JCheckBox bot_balance_checkbox;
    private javax.swing.JLabel bots_avatar_label;
    private javax.swing.JComboBox<String> bots_combobox;
    private javax.swing.JLabel bots_label;
    private javax.swing.JPanel bots_panel;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_max_bb_spinner;
    private javax.swing.JSpinner buyin_min_bb_spinner;
    private javax.swing.JLabel buyin_range_label;
    private javax.swing.JLabel buyin_range_sep_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JButton cancel_button;
    private javax.swing.JComboBox<String> ciegas_combobox;
    private javax.swing.JLabel ciegas_label;
    private javax.swing.JPanel ciegas_panel;
    private javax.swing.JPanel compra_panel;
    private javax.swing.JPanel config_partida_panel;
    private javax.swing.JCheckBox doblar_checkbox;
    private javax.swing.JSpinner doblar_ciegas_spinner_manos;
    private javax.swing.JSpinner doblar_ciegas_spinner_minutos;
    private javax.swing.JRadioButton double_blinds_radio_manos;
    private javax.swing.JRadioButton double_blinds_radio_minutos;
    private javax.swing.JComboBox<String> estructura_combobox;
    private javax.swing.JLabel estructura_label;
    private javax.swing.JCheckBox fixed_buyin_checkbox;
    private javax.swing.JLabel game_label;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel limite_manos_label;
    private javax.swing.JPanel main_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JSpinner manos_spinner;
    // Configurable/disableable think time in the "Game" subpanel (new-game creation).
    private javax.swing.JCheckBox think_time_checkbox;
    private javax.swing.JLabel think_time_label;
    private javax.swing.JSpinner think_time_spinner;
    // Showdown pause time in the "Game" subpanel (new-game creation). No checkbox: the
    // pause cannot be disabled.
    private javax.swing.JLabel showdown_time_label;
    private javax.swing.JSpinner showdown_time_spinner;
    // Game rules in the "Game" subpanel (new-game creation): IWTSTH, Run It Twice
    // and Rabbit Hunting. Same as the live "Game settings" dialog.
    private javax.swing.JLabel iwtsth_icon;
    private javax.swing.JCheckBox iwtsth_checkbox;
    private javax.swing.JLabel rit_icon;
    private javax.swing.JCheckBox rit_checkbox;
    private javax.swing.JLabel rabbit_icon;
    private javax.swing.JLabel rabbit_label;
    private javax.swing.JComboBox<String> rabbit_combo;
    private javax.swing.JTextField nick;
    private javax.swing.JLabel nick_label;
    private javax.swing.JPanel nick_pass_panel;
    private javax.swing.JPanel partida_panel;
    private javax.swing.JPasswordField pass_text;
    private javax.swing.JLabel password;
    private javax.swing.JButton preset_delete_button;
    private javax.swing.JLabel preset_label;
    private javax.swing.JButton preset_save_button;
    private javax.swing.JComboBox<String> presets_combobox;
    private javax.swing.JPanel presets_panel;
    private javax.swing.JComboBox<String> rebuy_cap_combo;
    private javax.swing.JLabel rebuy_cap_label;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JCheckBox rebuy_limit_checkbox;
    private javax.swing.JSpinner rebuy_limit_spinner;
    private javax.swing.JPanel recompra_panel;
    private javax.swing.JLabel recomprar_label;
    private javax.swing.JCheckBox recover_checkbox;
    private javax.swing.JLabel recover_checkbox_label;
    private javax.swing.JPanel recover_panel;
    private javax.swing.JScrollPane scroll_panel;
    private javax.swing.JTextField server_ip_textfield;
    private javax.swing.JLabel server_label;
    private javax.swing.JLabel server_port_puntos;
    private javax.swing.JTextField server_port_textfield;
    private javax.swing.JCheckBox straddle_checkbox;
    private javax.swing.JLabel straddle_icon;
    private javax.swing.JLabel titulo_ventana;
    private javax.swing.JCheckBox upnp_checkbox;
    private javax.swing.JPanel url_panel;
    private javax.swing.JButton vamos;
    // End of variables declaration//GEN-END:variables
}
