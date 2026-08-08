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

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * "Appearance settings" content as a JPanel (tab of the unified settings dialog).
 *
 * Has TWO modes depending on whether a game is running ({@code GameFrame.getInstance()}):
 *
 * - IN-GAME (gf != null): each control MIRRORS the current state and DELEGATES to the
 *   matching GameFrame menu item via {@code doClick()} (or its setter), which applies the
 *   effect LIVE on the table + persists it + reflects it in the felt popup. Control and
 *   item start in sync and both toggle one step per click, so they never drift apart.
 *
 * - OUT OF GAME (gf == null: launcher / waiting room): there is no table to preview
 *   against, so controls only PERSIST the preference (static flag + {@code
 *   Helpers.PROPERTIES} + {@code savePropertiesFile()}); it takes effect when the next
 *   game is created (GameFrame reads these preferences on construction). No live effect,
 *   EXCEPT the felt color: the launcher screen paints its background with it ({@code
 *   InitPanel}), so changing it refreshes the launcher on the fly as a preview (and
 *   reverts on cancel, same as in-game).
 *
 * The dialog is TRANSACTIONAL in both modes: changes revert to the opening state on
 * cancel (revert()); SAVE keeps them.
 *
 * NOTE: each animation toggle's preference lives both in its menu item's isSelected and
 * in its PROPERTIES key, kept in sync (the item persists the key on every change and
 * initializes from it). That's why this class always reads from PROPERTIES: it's
 * equivalent to reading the item and doesn't depend on GameFrame existing.
 *
 * @author tonikelope
 */
public class AppearanceSettingsPanel extends JPanel {

    // Running GameFrame, or null out of game (launcher / waiting room). In null mode
    // controls only persist the preference, with no live effect.
    private final GameFrame gf;

    // Suppresses combo actions while the panel is being built (setting the initial
    // selection must not trigger delegation).
    private volatile boolean building = true;

    // Display mode chosen in the combo. NOT applied live (the toggle disposes and
    // recreates the frame, which would corrupt this dialog); REMEMBERED here and
    // applied by the dialog on close (applyPendingDisplayMode).
    private volatile boolean pending_fullscreen;

    // The 5 individual animation checkboxes and their menu items (null out of game), so
    // the master toggle can DISABLE (not uncheck) them when turned off.
    private final java.util.List<JCheckBox> anim_sub_cb = new ArrayList<>();
    private final java.util.List<JMenuItem> anim_sub_menu = new ArrayList<>();

    // Animation master toggle (field, not local): restoreDefaults() needs it re-enabled
    // BEFORE resetting the children (with the master off, their menu items are disabled
    // and doClick would be a no-op, same as in revertLive).
    private JCheckBox anim_master;

    // "Restore defaults" actions, one per control: each Runnable sets its control to the
    // factory value via the widget API, which triggers the listener (applies live +
    // persists). Registered alongside each control; restoreDefaults() runs them in
    // creation order.
    private final java.util.List<Runnable> reset_actions = new ArrayList<>();

    // Snapshot of the appearance state on OPEN: the dialog is transactional, so changes
    // (applied live as a preview) REVERT to these values on cancel (revert()); SAVE
    // keeps them.
    private final int snap_zoom_level;
    private final int snap_vista_compacta;
    private final String snap_baraja;
    private final String snap_trasera;
    private final String snap_color_tapete;
    // PENDING dialog zoom: NOT previewed live (it would affect the discard-changes
    // dialog itself on cancel). Applied only on SAVE (applyPendingDialogZoom).
    private volatile float pending_dialog_zoom;
    private final boolean snap_auto_zoom;
    private final boolean snap_show_clock;
    private final boolean snap_coste_igualar;
    private final boolean snap_cinematicas;
    private final boolean snap_cinematicas_accion;
    private final boolean snap_cinematicas_allin;
    private final boolean snap_cinematicas_gameover;
    private final boolean snap_anim_barajado;
    private final boolean snap_anim_reparto;
    private final boolean snap_anim_destape;
    private final boolean snap_anim_ciegas_dealer;
    private final boolean snap_anim_apuestas;
    private final boolean snap_anim_contadores;
    private final boolean snap_anim_cascada_overlay;
    private final boolean snap_resaltar_jugada_showdown;
    private final boolean snap_resaltar_avatares;
    private final boolean snap_screenshot_fin_timba;
    private final boolean snap_animaciones;
    private final boolean snap_chat_images;
    private final boolean snap_fullscreen;
    private final int snap_card_flip_duration;
    private final int snap_card_flip_zoom;
    private final int snap_reparto_velocidad;
    private final boolean snap_anim_calidad;
    private final boolean snap_animacion_contador_final;
    private final boolean snap_anim_swap;
    private final int snap_swap_duration;
    private final boolean snap_swap_arc;
    private final boolean snap_anim_downgrade;
    private final int snap_downgrade_velocidad;
    private final int snap_nivel_luz;
    private final float snap_dialog_zoom;

    public AppearanceSettingsPanel() {

        super(new java.awt.BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM)));

        gf = GameFrame.getInstance();

        snap_zoom_level = GameFrame.ZOOM_LEVEL;
        snap_vista_compacta = GameFrame.VISTA_COMPACTA;
        snap_baraja = GameFrame.BARAJA;
        snap_trasera = GameFrame.TRASERA;
        snap_color_tapete = GameFrame.COLOR_TAPETE;
        snap_auto_zoom = GameFrame.AUTO_ZOOM;
        snap_show_clock = GameFrame.SHOW_CLOCK;
        snap_coste_igualar = GameFrame.MOSTRAR_COSTE_IGUALAR;
        // Snapshot of the animation PREFERENCES read from PROPERTIES (equivalent to the
        // item's isSelected, see class note): NOT the EFFECTIVE flag, which with the
        // master off is false for all and wouldn't let us detect a preference change on
        // revert.
        snap_cinematicas = prefBool("cinematicas");
        snap_cinematicas_accion = prefBool("cinematicas_accion", true);
        snap_cinematicas_allin = prefBool("cinematicas_allin", true);
        snap_cinematicas_gameover = prefBool("cinematicas_gameover", true);
        // Shuffle and reveal have no menu item: their preference is the GameFrame flag
        // (already migrated from the legacy "animacion_reparto" key if not yet saved),
        // not raw PROPERTIES, which might not have the key yet.
        snap_anim_barajado = GameFrame.ANIMACION_BARAJADO_PREF;
        snap_anim_reparto = prefBool("animacion_reparto");
        snap_anim_destape = GameFrame.ANIMACION_DESTAPE_PREF;
        snap_anim_ciegas_dealer = prefBool("animacion_ciegas_dealer");
        snap_anim_apuestas = prefBool("animacion_apuestas");
        snap_anim_contadores = prefBool("animacion_contadores");
        snap_anim_cascada_overlay = prefBool("animacion_cascada_overlay", false);
        snap_resaltar_jugada_showdown = prefBool("resaltar_jugada_showdown", true);
        snap_resaltar_avatares = prefBool("resaltar_avatares", false);
        snap_screenshot_fin_timba = prefBool("screenshot_fin_timba", false);
        snap_animaciones = GameFrame.ANIMACIONES;
        snap_chat_images = GameFrame.CHAT_IMAGES_INGAME;
        snap_fullscreen = (gf != null) ? gf.isFull_screen() : GameFrame.AUTO_FULLSCREEN;
        snap_card_flip_duration = GameFrame.CARD_FLIP_DURATION;
        snap_card_flip_zoom = GameFrame.CARD_FLIP_ZOOM;
        snap_reparto_velocidad = GameFrame.REPARTO_VELOCIDAD;
        snap_anim_calidad = GameFrame.ANIM_CALIDAD;
        snap_animacion_contador_final = prefBool("animacion_contador_final", true);
        snap_anim_swap = GameFrame.ANIMACION_SWAP_PREF;
        snap_swap_duration = GameFrame.SWAP_ANIM_DURATION;
        snap_swap_arc = GameFrame.SWAP_ANIM_ARC;
        snap_anim_downgrade = GameFrame.ANIMACION_DOWNGRADE_PREF;
        snap_downgrade_velocidad = GameFrame.DOWNGRADE_VELOCIDAD;
        snap_nivel_luz = GameFrame.NIVEL_LUZ;
        snap_dialog_zoom = Helpers.DIALOG_ZOOM;
        pending_dialog_zoom = snap_dialog_zoom;

        // ---------------- Screen and zoom ----------------
        JPanel pantalla = titledColumn("settings.apariencia_pantalla");

        // Display mode: windowed / fullscreen. Mirrors the current table state (or the
        // AUTO_FULLSCREEN preference out of game). NOT applied live (entering/leaving
        // fullscreen disposes and recreates the frame, which broke this open modal
        // dialog: "only worked once"). The choice is REMEMBERED and applied by the
        // dialog on CLOSE (changes the mode in-game; out of game only persists the
        // startup preference).
        pending_fullscreen = snap_fullscreen;
        JComboBox<String> display_combo = new JComboBox<>(new String[]{
            Translator.translate("settings.modo_ventana"),
            Translator.translate("settings.modo_pantalla_completa")
        });
        display_combo.setSelectedIndex(pending_fullscreen ? 1 : 0);
        Helpers.setTranslatedToolTip(display_combo, "tooltip.cfg.display_mode");
        display_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            pending_fullscreen = display_combo.getSelectedIndex() == 1;
        });
        // Default: fullscreen (AUTO_FULLSCREEN=true -> index 1). Applied on SAVE.
        reset_actions.add(() -> display_combo.setSelectedIndex(1));

        // Zoom: spinner in % (each step = 5% = one internal zoom level). In-game it
        // applies live to the chosen level; out of game it only persists the preference.
        int zoom_pct = Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * 100f);
        // The bounds ALWAYS include the current value (the engine has no upper zoom cap)
        // so SpinnerNumberModel doesn't throw if the saved zoom is out of range.
        JSpinner zoom_spinner = new JSpinner(new SpinnerNumberModel(zoom_pct, Math.min(5, zoom_pct), Math.max(300, zoom_pct), 5));
        zoom_spinner.addChangeListener(e -> {
            if (building) {
                return;
            }
            int pct = (Integer) zoom_spinner.getValue();
            int level = Math.round((pct - 100) / (GameFrame.ZOOM_STEP * 100f));
            if (gf != null) {
                gf.setZoomLevel(level);
            } else {
                GameFrame.ZOOM_LEVEL = level;
                persistDeferred("zoom_level", String.valueOf(level));
            }
        });
        // Table zoom + auto-fit together in a thin black box (auto-fit is a modifier of
        // the table zoom, so they read as one group).
        JPanel zoom_group = groupBox();
        addToGroup(zoom_group, labeledRow("/images/menu/zoom.png", "settings.zoom_pct", zoom_spinner));
        // Default: DEFAULT_ZOOM_LEVEL (same % as at construction). setValue triggers the
        // listener, which applies the level (in-game) or persists it (out of game).
        final int def_zoom_pct = Math.round((1f + GameFrame.DEFAULT_ZOOM_LEVEL * GameFrame.getZOOM_STEP()) * 100f);
        reset_actions.add(() -> zoom_spinner.setValue(def_zoom_pct));
        addToGroup(zoom_group, delegatingCheckbox("/images/menu/zoom_auto.png", "menu.auto_ajustar", GameFrame.AUTO_ZOOM,
                gf != null ? gf.getAuto_fit_zoom_menu() : null,
                () -> {
                    GameFrame.AUTO_ZOOM = !GameFrame.AUTO_ZOOM;
                    persist("auto_zoom", String.valueOf(GameFrame.AUTO_ZOOM));
                }, false));
        // Compact view: four-state dropdown (0=off, 1=compact, 2=compact+cards,
        // 3=compact+cards+local); applies live in-game / persist-only out of game.
        JComboBox<String> compact_combo = new JComboBox<>(new String[]{
            Translator.translate("settings.compacta_off"),
            Translator.translate("settings.compacta_on"),
            Translator.translate("settings.compacta_full"),
            Translator.translate("settings.compacta_local")
        });
        compact_combo.setSelectedIndex(Math.min(Math.max(GameFrame.VISTA_COMPACTA, 0), 3));
        Helpers.setTranslatedToolTip(compact_combo, "tooltip.cfg.compact_view");
        compact_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            int idx = compact_combo.getSelectedIndex();
            if (gf != null) {
                gf.setCompactView(idx);
            } else {
                GameFrame.VISTA_COMPACTA = idx;
                persist("vista_compacta", String.valueOf(idx));
            }
        });
        // Default: compact view off (index 0).
        reset_actions.add(() -> compact_combo.setSelectedIndex(0));

        // DIALOG zoom (font + window size), INDEPENDENT of the table zoom and the game
        // zoom: does NOT touch ZOOM_LEVEL. Persist-only in both contexts (no live dialog
        // to preview); takes effect on the next dialog opened, which reads
        // Helpers.DIALOG_ZOOM at construction. Range 50-200%, step 10, 100% = design
        // size. LAST option in the section.
        int dialog_zoom_pct = Math.round(Helpers.DIALOG_ZOOM * 100f);
        JSpinner dialog_zoom_spinner = new JSpinner(new SpinnerNumberModel(dialog_zoom_pct,
                Math.min(Math.round(Helpers.DIALOG_ZOOM_MIN * 100f), dialog_zoom_pct),
                Math.max(Math.round(Helpers.DIALOG_ZOOM_MAX * 100f), dialog_zoom_pct), 10));
        dialog_zoom_spinner.addChangeListener(e -> {
            if (building) {
                return;
            }
            // Transactional and WITHOUT a live preview (unlike the rest of appearance):
            // applying the zoom live would make the "discard changes?" dialog on cancel
            // itself appear with the new, unsaved zoom. Only recorded; applied on SAVE
            // (applyPendingDialogZoom).
            pending_dialog_zoom = ((Integer) dialog_zoom_spinner.getValue()) / 100f;
        });
        // The dialog zoom can ONLY be used from the LAUNCHER SCREEN: not in-game (gf !=
        // null) and not in the waiting room. Checks whether the room is VISIBLE (not just
        // whether it exists): going back from the room to the launcher can leave its
        // instance stale, so isShowing() is the reliable check. Changing it anywhere else
        // wouldn't refresh already-open dialogs or the room/table.
        WaitingRoomFrame wr = WaitingRoomFrame.getInstance();
        dialog_zoom_spinner.setEnabled(gf == null && (wr == null || !wr.isShowing()));
        Helpers.setTranslatedToolTip(dialog_zoom_spinner, "tooltip.cfg.dialog_zoom");
        // Default: 100%. ONLY if the spinner is enabled (launcher screen only): in-game
        // or in the room it's grayed out and must not overwrite the pending preference.
        reset_actions.add(() -> {
            if (dialog_zoom_spinner.isEnabled()) {
                dialog_zoom_spinner.setValue(100);
            }
        });

        // "Screen and zoom" stays at its NATURAL HEIGHT (no internal glue): it must not
        // open an empty strip inside its titled border. The right column's leftover space
        // is absorbed BETWEEN Table and Screen (see the right_inner assembly), not inside
        // this panel.

        // The three standalone controls (display mode, compact view, dialog zoom) share a
        // common label|control grid so their dropdowns start at the SAME x (previously
        // each landed at a different x depending on its label's width). The "Table zoom"
        // box (zoom_group) is inserted spanning both columns, keeping the original order.
        JLabel display_label = new JLabel(Translator.translate("settings.modo_pantalla") + ":");
        display_label.setIcon(icon("/images/menu/full_screen.png"));
        JLabel compact_label = new JLabel(Translator.translate("view.vista_compacta") + ":");
        compact_label.setIcon(icon("/images/menu/tiny.png"));
        JLabel dialog_zoom_label = new JLabel(Translator.translate("settings.dialog_zoom_pct") + ":");
        dialog_zoom_label.setIcon(icon("/images/menu/zoom.png"));
        JPanel pantalla_grid = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        pantalla_grid.setOpaque(false);
        pantalla_grid.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        int pantalla_vgap = Math.round(12 * Helpers.DIALOG_ZOOM);
        int pantalla_lgap = Math.round(6 * Helpers.DIALOG_ZOOM);
        java.awt.GridBagConstraints pgc = new java.awt.GridBagConstraints();
        pgc.anchor = java.awt.GridBagConstraints.WEST;
        pgc.gridx = 0;
        pgc.gridy = 0;
        pgc.insets = new java.awt.Insets(0, 0, pantalla_vgap, pantalla_lgap);
        pantalla_grid.add(display_label, pgc);
        pgc.gridx = 1;
        pgc.insets = new java.awt.Insets(0, 0, pantalla_vgap, 0);
        pantalla_grid.add(display_combo, pgc);
        // Table zoom box: spans both columns (its border reads as a separate block).
        pgc.gridx = 0;
        pgc.gridy = 1;
        pgc.gridwidth = 2;
        pgc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        pgc.insets = new java.awt.Insets(0, 0, pantalla_vgap, 0);
        pantalla_grid.add(zoom_group, pgc);
        pgc.gridwidth = 1;
        pgc.fill = java.awt.GridBagConstraints.NONE;
        pgc.gridx = 0;
        pgc.gridy = 2;
        pgc.insets = new java.awt.Insets(0, 0, pantalla_vgap, pantalla_lgap);
        pantalla_grid.add(compact_label, pgc);
        pgc.gridx = 1;
        pgc.insets = new java.awt.Insets(0, 0, pantalla_vgap, 0);
        pantalla_grid.add(compact_combo, pgc);
        pgc.gridx = 0;
        pgc.gridy = 3;
        pgc.insets = new java.awt.Insets(0, 0, 0, pantalla_lgap);
        pantalla_grid.add(dialog_zoom_label, pgc);
        pgc.gridx = 1;
        pgc.insets = new java.awt.Insets(0, 0, 0, 0);
        pantalla_grid.add(dialog_zoom_spinner, pgc);
        addLeft(pantalla, pantalla_grid);

        // ---------------- Table ----------------
        JPanel mesa = titledColumn("settings.apariencia_mesa");

        List<String> decks = new ArrayList<>(Card.BARAJAS.keySet());
        Collections.sort(decks);

        // Deck: combo with the available decks (including MOD decks). In-game it
        // delegates to the deck submenu's radio item (reloads images); out of game it
        // persists and rebuilds the static images (so the "default" back looks right).
        JComboBox<String> baraja_combo = new JComboBox<>(decks.toArray(new String[0]));
        Helpers.setTranslatedToolTip(baraja_combo, "tooltip.cfg.deck");
        baraja_combo.setSelectedItem(GameFrame.BARAJA);
        baraja_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            String sel = (String) baraja_combo.getSelectedItem();
            if (sel != null && !sel.equals(GameFrame.BARAJA)) {
                if (gf != null) {
                    for (Component c : gf.getMenu_barajas().getMenuComponents()) {
                        if (c instanceof JMenuItem && ((JMenuItem) c).getText().equals(sel)) {
                            ((JMenuItem) c).doClick();
                            break;
                        }
                    }
                } else {
                    GameFrame.BARAJA = sel;
                    persist("baraja", sel);
                    Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);
                    // Out of game there's no cambiarBaraja() to warm the cache: pre-decode
                    // the new deck's shuffle.gif here so the first hand doesn't pay for it.
                    Crupier.warmShuffleAnimCache();
                }
            }
        });
        // Deck + Card back go together in a bordered box (groupBox). Both are laid out in
        // a 2-column grid (label | dropdown) further below, so their dropdowns stay
        // ALIGNED in the same column.
        JPanel baraja_group = groupBox();
        // Default: factory deck.
        reset_actions.add(() -> baraja_combo.setSelectedItem(GameFrame.BARAJA_DEFAULT));

        // Card back: "default" (follows the current deck) + one option per deck (base
        // game or mod) to use its back with other faces. Aligned with "Deck" (same grid).
        // In-game it applies live (refreshes the back); out of game it persists and
        // rebuilds the static back.
        List<String> traseras = new ArrayList<>();
        traseras.add("default");
        traseras.addAll(decks);
        JComboBox<String> trasera_combo = new JComboBox<>(traseras.toArray(new String[0]));
        Helpers.setTranslatedToolTip(trasera_combo, "tooltip.cfg.deck_back");
        // The internal VALUE stays "default" (for persistence), but it's shown translated.
        trasera_combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if ("default".equals(value)) {
                    setText(Translator.translate("settings.trasera_default"));
                }
                return c;
            }
        });
        trasera_combo.setSelectedItem(Card.BARAJAS.containsKey(GameFrame.TRASERA) ? GameFrame.TRASERA : "default");
        trasera_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            String sel = (String) trasera_combo.getSelectedItem();
            if (sel != null && !sel.equals(GameFrame.TRASERA)) {
                if (gf != null) {
                    gf.setTrasera(sel);
                } else {
                    GameFrame.TRASERA = sel;
                    persist("trasera", sel);
                    Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);
                }
            }
        });
        // 2-column grid: label | dropdown. The label column measures the widest one
        // ("Card back:"), so both dropdowns start at the SAME x and "Card back" aligns
        // with "Deck" above it (previously it was indented and offset). The labels' right
        // inset (6px) mirrors labeledRow's hgap; the bottom one (4px) mirrors
        // addToGroup's row spacing.
        JLabel baraja_label = new JLabel(Translator.translate("settings.baraja") + ":");
        baraja_label.setIcon(icon("/images/menu/baraja.png"));
        JLabel trasera_label = new JLabel(Translator.translate("settings.trasera") + ":");
        trasera_label.setIcon(icon("/images/menu/baraja.png"));
        JPanel baraja_grid = new JPanel(new java.awt.GridBagLayout()) {
            // Max width = preferred: the box's BoxLayout won't stretch it (with weightx=0
            // it would center the rows); capped to its preferred size it hugs the left edge.
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        baraja_grid.setOpaque(false);
        java.awt.GridBagConstraints baraja_gbc = new java.awt.GridBagConstraints();
        baraja_gbc.anchor = java.awt.GridBagConstraints.WEST;
        baraja_gbc.gridx = 0;
        baraja_gbc.gridy = 0;
        baraja_gbc.insets = new java.awt.Insets(0, 0, Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM));
        baraja_grid.add(baraja_label, baraja_gbc);
        baraja_gbc.gridx = 1;
        // fill=HORIZONTAL: both dropdowns take the column's width (= the widest one), so
        // Deck and Card back end up the SAME width.
        baraja_gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        baraja_gbc.insets = new java.awt.Insets(0, 0, Math.round(4 * Helpers.DIALOG_ZOOM), 0);
        baraja_grid.add(baraja_combo, baraja_gbc);
        baraja_gbc.gridx = 0;
        baraja_gbc.gridy = 1;
        baraja_gbc.fill = java.awt.GridBagConstraints.NONE;
        baraja_gbc.insets = new java.awt.Insets(0, 0, Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM));
        baraja_grid.add(trasera_label, baraja_gbc);
        baraja_gbc.gridx = 1;
        baraja_gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        baraja_gbc.insets = new java.awt.Insets(0, 0, Math.round(4 * Helpers.DIALOG_ZOOM), 0);
        baraja_grid.add(trasera_combo, baraja_gbc);
        addToGroup(baraja_group, baraja_grid);
        // Default: "default" back (follows the deck). Reset AFTER the deck.
        reset_actions.add(() -> trasera_combo.setSelectedItem("default"));
        addLeft(mesa, baraja_group);

        // Felt: combo with the 5 colors; in-game it delegates to the matching radio item
        // (refreshes the table); out of game it persists the base color and refreshes the
        // launcher background (InitPanel) on the fly, the only preview of the felt
        // outside the table.
        JComboBox<String> tapete_combo = new JComboBox<>(new String[]{
            Translator.translate("menu.verde"),
            Translator.translate("menu.azul"),
            Translator.translate("menu.rojo"),
            Translator.translate("menu.negro"),
            Translator.translate("menu.sin_tapete")
        });
        tapete_combo.setSelectedIndex(currentTapeteIndex());
        Helpers.setTranslatedToolTip(tapete_combo, "tooltip.cfg.table_felt");
        tapete_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            int idx = tapete_combo.getSelectedIndex();
            if (gf != null) {
                switch (idx) {
                    case 0:
                        gf.getMenu_tapete_verde().doClick();
                        break;
                    case 1:
                        gf.getMenu_tapete_azul().doClick();
                        break;
                    case 2:
                        gf.getMenu_tapete_rojo().doClick();
                        break;
                    case 3:
                        gf.getMenu_tapete_negro().doClick();
                        break;
                    case 4:
                        gf.getMenu_tapete_madera().doClick();
                        break;
                    default:
                        break;
                }
            } else {
                String color = tapeteColorForIndex(idx);
                GameFrame.COLOR_TAPETE = color;
                persist("color_tapete", color);
                refreshLauncherTapete();
            }
        });
        // "Felt" aligns with Deck and Card back in the SAME grid (deck, back and felt are
        // all "table appearance"): previously it stood alone below, starting at a
        // different x depending on its label.
        JLabel tapete_label = new JLabel(Translator.translate("settings.tapete") + ":");
        tapete_label.setIcon(icon("/images/menu/tapetes.png"));
        baraja_gbc.gridx = 0;
        baraja_gbc.gridy = 2;
        baraja_gbc.fill = java.awt.GridBagConstraints.NONE;
        baraja_gbc.insets = new java.awt.Insets(0, 0, Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM));
        baraja_grid.add(tapete_label, baraja_gbc);
        baraja_gbc.gridx = 1;
        baraja_gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        baraja_gbc.insets = new java.awt.Insets(0, 0, Math.round(4 * Helpers.DIALOG_ZOOM), 0);
        baraja_grid.add(tapete_combo, baraja_gbc);
        // Default: green felt (index 0).
        reset_actions.add(() -> tapete_combo.setSelectedIndex(0));

        // Brightness left when the table lights are TURNED OFF (the felt switch, the
        // shortcut, and automatic dimming): the lower, the darker. In-game it previews
        // live if the lights are currently off; out of game it only persists. Closes the
        // table-appearance grid.
        JSpinner luz_spinner = new JSpinner(new SpinnerNumberModel(
                Math.max(GameFrame.NIVEL_LUZ_MIN, Math.min(GameFrame.NIVEL_LUZ, GameFrame.NIVEL_LUZ_MAX)),
                GameFrame.NIVEL_LUZ_MIN, GameFrame.NIVEL_LUZ_MAX, 5));
        Helpers.setTranslatedToolTip(luz_spinner, "tooltip.cfg.nivel_luz");
        luz_spinner.addChangeListener(e -> {
            if (building) {
                return;
            }
            GameFrame.NIVEL_LUZ = (Integer) luz_spinner.getValue();
            persistDeferred("nivel_luz", String.valueOf(GameFrame.NIVEL_LUZ));
            applyNivelLuz();
        });
        JLabel luz_label = new JLabel(Translator.translate("settings.nivel_luz") + ":");
        // The switch icon is landscape (256x120): it's fit inside the same 24px box the
        // other three rows' icons use, keeping its aspect ratio. Giving it its real width
        // (51) would make it bigger but misalign its label from "Deck", "Card back" and
        // "Felt", and push the three dropdowns right: in this grid the icon sits INSIDE
        // the label, so its width is part of the column's width.
        luz_label.setIcon(fitIcon("/images/lights_on.png", 24, 24));
        baraja_gbc.gridx = 0;
        baraja_gbc.gridy = 3;
        baraja_gbc.fill = java.awt.GridBagConstraints.NONE;
        baraja_gbc.insets = new java.awt.Insets(0, 0, 0, Math.round(6 * Helpers.DIALOG_ZOOM));
        baraja_grid.add(luz_label, baraja_gbc);
        baraja_gbc.gridx = 1;
        // No fill (unlike the dropdowns above): a two-digit spinner stretched to the
        // column's width would look disproportionate. It starts at the same x, which is
        // what aligns the grid -- same criterion as the "Screen and zoom" spinners.
        baraja_gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        baraja_grid.add(luz_spinner, baraja_gbc);
        // Default: 50%. setValue triggers the listener, which persists and previews the
        // same as a manual change.
        reset_actions.add(() -> luz_spinner.setValue(GameFrame.DEFAULT_NIVEL_LUZ));

        addLeft(mesa, delegatingCheckbox("/images/menu/clock.png", "action.mostrar_reloj", GameFrame.SHOW_CLOCK,
                gf != null ? gf.getTime_menu() : null,
                () -> {
                    GameFrame.SHOW_CLOCK = !GameFrame.SHOW_CLOCK;
                    persist("show_time", String.valueOf(GameFrame.SHOW_CLOCK));
                }, false));
        addLeft(mesa, delegatingCheckbox("/images/menu/eyes.png", "menu.coste_igualar", GameFrame.MOSTRAR_COSTE_IGUALAR,
                gf != null ? gf.getCoste_igualar_menu() : null,
                () -> {
                    GameFrame.MOSTRAR_COSTE_IGUALAR = !GameFrame.MOSTRAR_COSTE_IGUALAR;
                    persist("mostrar_coste_igualar", String.valueOf(GameFrame.MOSTRAR_COSTE_IGUALAR));
                }, true));
        addLeft(mesa, delegatingCheckbox("/images/menu/chat_image.png", "menu.imagenes_del_chat_en_el_juego", GameFrame.CHAT_IMAGES_INGAME,
                gf != null ? gf.getChat_image_menu() : null,
                () -> {
                    GameFrame.CHAT_IMAGES_INGAME = !GameFrame.CHAT_IMAGES_INGAME;
                    persist("chat_images_ingame", String.valueOf(GameFrame.CHAT_IMAGES_INGAME));
                }, true));
        // Showdown highlight: no menu item and no live effect (read on the fly when
        // hovering the hand-rank label). Persist-only, like the cascade overlay.
        addLeft(mesa, delegatingCheckbox("/images/menu/eyes.png", "settings.resaltar_jugada_showdown", GameFrame.RESALTAR_JUGADA_SHOWDOWN,
                null,
                () -> {
                    GameFrame.RESALTAR_JUGADA_SHOWDOWN = !GameFrame.RESALTAR_JUGADA_SHOWDOWN;
                    persist("resaltar_jugada_showdown", String.valueOf(GameFrame.RESALTAR_JUGADA_SHOWDOWN));
                }, true, "tooltip.cfg.resaltar_jugada_showdown"));
        // Automatic screenshot of the final screen when the game ends: persist-only, no
        // menu item and no live effect (BalanceScreen reads it at construction). Default
        // OFF (can pile up many screenshots). Same mechanism as the showdown highlight above.
        addLeft(mesa, delegatingCheckbox("/images/menu/camera.png", "settings.screenshot_fin_timba", GameFrame.SCREENSHOT_FIN_TIMBA,
                null,
                () -> {
                    GameFrame.SCREENSHOT_FIN_TIMBA = !GameFrame.SCREENSHOT_FIN_TIMBA;
                    persist("screenshot_fin_timba", String.valueOf(GameFrame.SCREENSHOT_FIN_TIMBA));
                }, false, "tooltip.cfg.screenshot_fin_timba"));
        // Avatar zoom-on-hover: persist-only, no menu item (AvatarZoomOverlay reads it on
        // the fly on every hover; unchecking it while a magnifier is showing removes it on
        // its watcher's next poll). Default OFF (the zoom covers part of the table).
        addLeft(mesa, delegatingCheckbox("/images/menu/eyes.png", "settings.resaltar_avatares", GameFrame.RESALTAR_AVATARES,
                null,
                () -> {
                    GameFrame.RESALTAR_AVATARES = !GameFrame.RESALTAR_AVATARES;
                    persist("resaltar_avatares", String.valueOf(GameFrame.RESALTAR_AVATARES));
                }, false, "tooltip.cfg.resaltar_avatares"));

        // ---------------- Animations ----------------
        JPanel anim = titledColumn("settings.apariencia_animaciones");

        // Master: turns ALL animations on/off at once. Unchecking it DISABLES (doesn't
        // uncheck) the 5 checkboxes below, which keep their value.
        anim_master = new JCheckBox(Translator.translate("menu.efectos_animacion_general").toUpperCase(), GameFrame.ANIMACIONES);
        anim_master.setFont(anim_master.getFont().deriveFont(java.awt.Font.BOLD));
        anim_master.addActionListener(e -> {
            boolean on = anim_master.isSelected();
            if (gf != null) {
                gf.setAnimacionesMaster(on);
            } else {
                // Out of game: the master is just a GATE. It persists ANIMACIONES and does
                // NOT touch the individual preferences (the *_PREF fields are the raw
                // preference; the gate is applied by the *On() helpers when reading them).
                GameFrame.ANIMACIONES = on;
                persist("animaciones", String.valueOf(on));
            }
            for (int i = 0; i < anim_sub_cb.size(); i++) {
                JMenuItem m = anim_sub_menu.get(i);
                anim_sub_cb.get(i).setEnabled(on && (m == null || m.isEnabled()));
            }
        });
        JPanel master_row = naturalRow();
        master_row.add(new JLabel(icon("/images/menu/fx.png")));
        master_row.add(anim_master);
        addLeft(anim, master_row);

        // --- Cinematics (master, with menu item) + its two subtypes: Action and ALL-IN ---
        // Thin box like the other groups with sub-settings. Each subtype is persist-only
        // (no menu item) and hangs off the "Cinematics" master: disabled if "Cinematics"
        // or the animation master is unchecked.
        JPanel cinematicas_group = groupBox();
        addToGroup(cinematicas_group, animCheckbox("/images/menu/video.png", "menu.cinematicas",
                gf != null ? gf.getMenu_cinematicas() : null, "cinematicas", v -> GameFrame.CINEMATICAS_PREF = v));
        final JCheckBox cinematicas_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // ACTION subtype: the fold/call/check/bet/raise GIFs shown by opponents.
        {
            final JCheckBox accion_cb = new JCheckBox(Translator.translate("menu.cinematicas_accion"),
                    prefBool("cinematicas_accion", true));
            accion_cb.addActionListener(e -> {
                boolean now = accion_cb.isSelected();
                persist("cinematicas_accion", String.valueOf(now));
                GameFrame.CINEMATICAS_ACCION_PREF = now;
            });
            Runnable updateAccionEnabled = () -> accion_cb.setEnabled(anim_master.isSelected() && cinematicas_cb.isSelected());
            anim_master.addActionListener(e -> updateAccionEnabled.run());
            cinematicas_cb.addActionListener(e -> updateAccionEnabled.run());
            updateAccionEnabled.run();
            // Default: ON (default true). Reset after the master and "Cinematics" (already
            // enabled in restoreDefaults), so the doClick takes effect.
            reset_actions.add(() -> {
                if (!accion_cb.isSelected()) {
                    accion_cb.doClick();
                }
            });
            JPanel accion_row = naturalRow();
            accion_row.add(Box.createHorizontalStrut(Math.round(18 * Helpers.DIALOG_ZOOM))); // sub-option of "Cinematics"
            accion_row.add(new JLabel(icon("/images/menu/chips.png")));
            accion_row.add(accion_cb);
            addToGroup(cinematicas_group, accion_row);
        }
        // ALL-IN subtype: the fullscreen sequence when someone goes all-in.
        {
            final JCheckBox allin_cb = new JCheckBox(Translator.translate("menu.cinematicas_allin"),
                    prefBool("cinematicas_allin", true));
            allin_cb.addActionListener(e -> {
                boolean now = allin_cb.isSelected();
                persist("cinematicas_allin", String.valueOf(now));
                GameFrame.CINEMATICAS_ALLIN_PREF = now;
            });
            Runnable updateAllinEnabled = () -> allin_cb.setEnabled(anim_master.isSelected() && cinematicas_cb.isSelected());
            anim_master.addActionListener(e -> updateAllinEnabled.run());
            cinematicas_cb.addActionListener(e -> updateAllinEnabled.run());
            updateAllinEnabled.run();
            reset_actions.add(() -> {
                if (!allin_cb.isSelected()) {
                    allin_cb.doClick();
                }
            });
            JPanel allin_row = naturalRow();
            allin_row.add(Box.createHorizontalStrut(Math.round(18 * Helpers.DIALOG_ZOOM))); // sub-option of "Cinematics"
            allin_row.add(new JLabel(icon("/images/menu/video.png")));
            allin_row.add(allin_cb);
            addToGroup(cinematicas_group, allin_row);
        }
        // GAME OVER subtype: the busted-player GIFs while the rebuy decision runs (the
        // game-over dialog's own GIF and the one covering busted opponents' cards). When
        // off, the rebuy cycle falls back to its static mode: a "GAME OVER" banner with a
        // countdown, the action label showing "REBUY? (N)", and a smooth time bar.
        {
            final JCheckBox gameover_cb = new JCheckBox(Translator.translate("menu.cinematicas_gameover"),
                    prefBool("cinematicas_gameover", true));
            gameover_cb.addActionListener(e -> {
                boolean now = gameover_cb.isSelected();
                persist("cinematicas_gameover", String.valueOf(now));
                GameFrame.CINEMATICAS_GAMEOVER_PREF = now;
            });
            Runnable updateGameOverEnabled = () -> gameover_cb.setEnabled(anim_master.isSelected() && cinematicas_cb.isSelected());
            anim_master.addActionListener(e -> updateGameOverEnabled.run());
            cinematicas_cb.addActionListener(e -> updateGameOverEnabled.run());
            updateGameOverEnabled.run();
            reset_actions.add(() -> {
                if (!gameover_cb.isSelected()) {
                    gameover_cb.doClick();
                }
            });
            JPanel gameover_row = naturalRow();
            gameover_row.add(Box.createHorizontalStrut(Math.round(18 * Helpers.DIALOG_ZOOM))); // sub-option of "Cinematics"
            gameover_row.add(new JLabel(scaledIcon("/images/action/skull.png", 24)));
            gameover_row.add(gameover_cb);
            addToGroup(cinematicas_group, gameover_row);
        }
        addLeft(anim, indent(cinematicas_group));
        // --- Shuffle (Settings only, no menu item) + its Cascade overlay sub-setting ---
        // Turning it on re-warms the shuffle.gif cache (startup warm-up may have skipped
        // it). Parent + nested sub-controls inside a thin grouping box.
        JPanel barajado_group = groupBox();
        addToGroup(barajado_group, animCheckbox("/images/menu/baraja.png", "menu.efectos_animacion_barajado",
                null, "animacion_barajado",
                v -> { GameFrame.ANIMACION_BARAJADO_PREF = v; if (v) { Crupier.warmShuffleAnimCache(); } },
                GameFrame.ANIMACION_BARAJADO_PREF));
        final JCheckBox barajado_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Cascade overlay: per-player shuffle overlay. Hangs (more indented) off
        // "Shuffle": disabled if "Shuffle" or the master is unchecked. Persist-only (no
        // menu item); built by hand (not via animCheckbox) to gate its enablement on
        // "Shuffle", not just on the master.
        {
            final JCheckBox cascada_cb = new JCheckBox(Translator.translate("menu.efectos_animacion_cascada_overlay"),
                    prefBool("animacion_cascada_overlay", false));
            cascada_cb.addActionListener(e -> {
                boolean now = cascada_cb.isSelected();
                persist("animacion_cascada_overlay", String.valueOf(now));
                GameFrame.ANIMACION_CASCADA_OVERLAY_PREF = now;
            });
            Runnable updateCascadaEnabled = () -> cascada_cb.setEnabled(anim_master.isSelected() && barajado_cb.isSelected());
            anim_master.addActionListener(e -> updateCascadaEnabled.run());
            barajado_cb.addActionListener(e -> updateCascadaEnabled.run());
            updateCascadaEnabled.run();
            // Default: cascade overlay OFF (default false). Reset after the master and
            // "Shuffle" (already enabled), so the doClick takes effect.
            reset_actions.add(() -> {
                if (cascada_cb.isSelected()) {
                    cascada_cb.doClick();
                }
            });
            JPanel cascada_row = naturalRow();
            cascada_row.add(Box.createHorizontalStrut(Math.round(18 * Helpers.DIALOG_ZOOM))); // sub-option of "Shuffle"
            cascada_row.add(new JLabel(icon("/images/menu/baraja.png")));
            cascada_row.add(cascada_cb);
            addToGroup(barajado_group, cascada_row);
        }
        addLeft(anim, indent(barajado_group));

        // --- Deal (used to be "Cards", keeps its menu item and the "animacion_reparto" key) ---
        JPanel reparto_group = groupBox();
        addToGroup(reparto_group, animCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_reparto",
                gf != null ? gf.getAnim_reparto_menu() : null, "animacion_reparto", v -> GameFrame.ANIMACION_REPARTO_PREF = v));
        final JCheckBox reparto_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Deal speed: 3 options (slow/normal/fast). "Normal" = the EXACT historical speed
        // (REPARTO_VELOCIDAD 100 -> factor 1.0). Hangs off "Deal": disabled if "Deal" or
        // the master is unchecked. Stores the base-pause % (GameFrame.REPARTO_VELOCIDAD).
        {
            final int[] speed_pct = {150, GameFrame.DEFAULT_REPARTO_VELOCIDAD, 60}; // slow, normal, fast
            final String[] speed_keys = {"settings.reparto_lento", "settings.reparto_normal", "settings.reparto_rapido"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel deal_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> deal_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selects the option whose saved % is closest (defaults to Normal).
            int sel = 1, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_pct.length; i++) {
                int d = Math.abs(speed_pct[i] - GameFrame.REPARTO_VELOCIDAD);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            deal_combo.setSelectedIndex(sel);
            deal_combo.setMaximumSize(deal_combo.getPreferredSize());
            deal_combo.addActionListener(e -> {
                int pct = speed_pct[deal_combo.getSelectedIndex()];
                GameFrame.REPARTO_VELOCIDAD = pct;
                persist("reparto_velocidad", String.valueOf(pct));
            });
            Helpers.setTranslatedToolTip(deal_combo, "tooltip.cfg.reparto_velocidad");

            Runnable updateDealEnabled = () -> {
                boolean on = anim_master.isSelected() && reparto_cb.isSelected();
                deal_combo.setEnabled(on);
                deal_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateDealEnabled.run());
            reparto_cb.addActionListener(e -> updateDealEnabled.run());
            updateDealEnabled.run();
            // Default: "Normal" speed (index 1 = DEFAULT_REPARTO_VELOCIDAD).
            reset_actions.add(() -> deal_combo.setSelectedIndex(1));

            JPanel deal_row = naturalRow();
            deal_row.add(Box.createHorizontalStrut(Math.round(18 * Helpers.DIALOG_ZOOM))); // sub-option of "Deal"
            deal_row.add(new JLabel(icon("/images/menu/clock.png")));
            deal_row.add(deal_text);
            deal_row.add(deal_combo);
            addToGroup(reparto_group, deal_row);
        }
        addLeft(anim, indent(reparto_group));

        // --- Reveal (used to be the flip part of the old "Cards", now its own setting,
        // Settings only) --- Its reveal speed and zoom-in effect hang off it.
        JPanel destapar_group = groupBox();
        addToGroup(destapar_group, animCheckbox("/images/menu/flip.png", "menu.efectos_animacion_destape",
                null, "animacion_destape", v -> GameFrame.ANIMACION_DESTAPE_PREF = v, GameFrame.ANIMACION_DESTAPE_PREF));
        final JCheckBox destapar_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Speed and zoom-in effect sit in a grid that ALIGNS their dropdowns in a column.
        JPanel destapar_sub = subGrid();
        // Reveal speed: 5 options (very slow ... very fast). "Normal" is the exact
        // default value. Hangs (more indented) off "Reveal": disabled if "Reveal" or the
        // master is unchecked. Stores the duration in ms (GameFrame.CARD_FLIP_DURATION).
        {
            final int[] speed_ms = {1100, 850, GameFrame.DEFAULT_CARD_FLIP_DURATION, 480, 350}; // very slow -> very fast
            final String[] speed_keys = {"settings.destape_muy_lenta", "settings.destape_lenta",
                "settings.destape_normal", "settings.destape_rapida", "settings.destape_muy_rapida"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel flip_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> speed_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selects the option whose saved ms is closest (defaults to Normal).
            int sel = 2, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_ms.length; i++) {
                int d = Math.abs(speed_ms[i] - GameFrame.CARD_FLIP_DURATION);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            speed_combo.setSelectedIndex(sel);
            speed_combo.addActionListener(e -> {
                int ms = speed_ms[speed_combo.getSelectedIndex()];
                GameFrame.CARD_FLIP_DURATION = ms;
                persist("card_flip_duration", String.valueOf(ms));
            });
            Helpers.setTranslatedToolTip(speed_combo, "tooltip.cfg.card_flip_duration");

            // Enabled only if the animation master AND the "Reveal" checkbox are both on.
            Runnable updateFlipEnabled = () -> {
                boolean on = anim_master.isSelected() && destapar_cb.isSelected();
                speed_combo.setEnabled(on);
                flip_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateFlipEnabled.run());
            destapar_cb.addActionListener(e -> updateFlipEnabled.run());
            updateFlipEnabled.run();
            // Default: "Normal" speed (index 2 = DEFAULT_CARD_FLIP_DURATION).
            reset_actions.add(() -> speed_combo.setSelectedIndex(2));

            addAlignedSubRow(destapar_sub, 0, "/images/menu/clock.png", flip_text, speed_combo);
        }
        // "Zoom-in" effect: 4 options (off ... strong). Hangs off "Reveal" just like the
        // speed. Stores the enlargement percentage (GameFrame.CARD_FLIP_ZOOM): 100 = off.
        {
            final int[] acercar_pct = {100, 115, 130, 145}; // off, mild, normal, strong
            final String[] zoom_keys = {"settings.acercar_desactivado", "settings.acercar_suave",
                "settings.acercar_normal", "settings.acercar_fuerte"};
            final String[] zoom_labels = new String[zoom_keys.length];
            for (int i = 0; i < zoom_keys.length; i++) {
                zoom_labels[i] = Translator.translate(zoom_keys[i]);
            }

            final JLabel zoom_text = new JLabel(Translator.translate("settings.efecto_acercar") + ":");
            final javax.swing.JComboBox<String> zoom_combo = new javax.swing.JComboBox<>(zoom_labels);

            // Selects the option whose saved percentage is closest (defaults to Off).
            int sel = 0, best = Integer.MAX_VALUE;
            for (int i = 0; i < acercar_pct.length; i++) {
                int d = Math.abs(acercar_pct[i] - GameFrame.CARD_FLIP_ZOOM);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            zoom_combo.setSelectedIndex(sel);
            zoom_combo.addActionListener(e -> {
                int pct = acercar_pct[zoom_combo.getSelectedIndex()];
                GameFrame.CARD_FLIP_ZOOM = pct;
                persist("card_flip_zoom", String.valueOf(pct));
            });
            Helpers.setTranslatedToolTip(zoom_combo, "tooltip.cfg.card_flip_zoom");

            // Enabled only if the animation master AND the "Reveal" checkbox are both on.
            Runnable updateZoomEnabled = () -> {
                boolean on = anim_master.isSelected() && destapar_cb.isSelected();
                zoom_combo.setEnabled(on);
                zoom_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateZoomEnabled.run());
            destapar_cb.addActionListener(e -> updateZoomEnabled.run());
            updateZoomEnabled.run();
            // Default: zoom-in effect OFF (index 0 = DEFAULT_CARD_FLIP_ZOOM 100).
            reset_actions.add(() -> zoom_combo.setSelectedIndex(0));

            addAlignedSubRow(destapar_sub, 1, "/images/menu/zoom_in.png", zoom_text, zoom_combo);
        }
        addToGroup(destapar_group, destapar_sub);
        addLeft(anim, indent(destapar_group));

        // --- Sort hand (animated crossing of your two hole cards when sorted, Settings
        // only) --- Its swap speed hangs off it.
        JPanel swap_group = groupBox();
        addToGroup(swap_group, animCheckbox("/images/menu/swap.png", "menu.efectos_animacion_swap",
                null, "animacion_swap", v -> GameFrame.ANIMACION_SWAP_PREF = v, GameFrame.ANIMACION_SWAP_PREF));
        final JCheckBox swap_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Speed and style sit in a grid that ALIGNS their dropdowns in a column.
        JPanel swap_sub = subGrid();
        // Swap speed: 3 options (slow/normal/fast). "Normal" = default value (320 ms).
        // Hangs off the setting: disabled if unchecked or the master is off. Stores the
        // duration in ms (GameFrame.SWAP_ANIM_DURATION).
        {
            final int[] speed_ms = {520, GameFrame.DEFAULT_SWAP_ANIM_DURATION, 200}; // slow, normal, fast
            final String[] speed_keys = {"settings.swap_lenta", "settings.swap_normal", "settings.swap_rapida"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel swap_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> swap_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selects the option whose saved ms is closest (defaults to Normal).
            int sel = 1, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_ms.length; i++) {
                int d = Math.abs(speed_ms[i] - GameFrame.SWAP_ANIM_DURATION);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            swap_combo.setSelectedIndex(sel);
            swap_combo.addActionListener(e -> {
                int ms = speed_ms[swap_combo.getSelectedIndex()];
                GameFrame.SWAP_ANIM_DURATION = ms;
                persist("swap_velocidad", String.valueOf(ms));
            });
            Helpers.setTranslatedToolTip(swap_combo, "tooltip.cfg.swap_velocidad");

            Runnable updateSwapEnabled = () -> {
                boolean on = anim_master.isSelected() && swap_cb.isSelected();
                swap_combo.setEnabled(on);
                swap_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateSwapEnabled.run());
            swap_cb.addActionListener(e -> updateSwapEnabled.run());
            updateSwapEnabled.run();
            // Default: "Normal" speed (index 1 = DEFAULT_SWAP_ANIM_DURATION).
            reset_actions.add(() -> swap_combo.setSelectedIndex(1));

            addAlignedSubRow(swap_sub, 0, "/images/menu/clock.png", swap_text, swap_combo);
        }
        // Swap style: 2 options ("Hop" arc / Horizontal). Hangs off "Sort hand" just like
        // the speed. Stores a boolean (GameFrame.SWAP_ANIM_ARC).
        {
            final String[] style_keys = {"settings.swap_arco", "settings.swap_horizontal"};
            final String[] style_labels = new String[style_keys.length];
            for (int i = 0; i < style_keys.length; i++) {
                style_labels[i] = Translator.translate(style_keys[i]);
            }

            final JLabel style_text = new JLabel(Translator.translate("settings.swap_estilo") + ":");
            final javax.swing.JComboBox<String> style_combo = new javax.swing.JComboBox<>(style_labels);
            style_combo.setSelectedIndex(GameFrame.SWAP_ANIM_ARC ? 0 : 1);
            style_combo.addActionListener(e -> {
                boolean arc = style_combo.getSelectedIndex() == 0;
                GameFrame.SWAP_ANIM_ARC = arc;
                persist("swap_arco", String.valueOf(arc));
            });
            Helpers.setTranslatedToolTip(style_combo, "tooltip.cfg.swap_estilo");

            Runnable updateStyleEnabled = () -> {
                boolean on = anim_master.isSelected() && swap_cb.isSelected();
                style_combo.setEnabled(on);
                style_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateStyleEnabled.run());
            swap_cb.addActionListener(e -> updateStyleEnabled.run());
            updateStyleEnabled.run();
            // Default: "Horizontal" style (index 1 = SWAP_ANIM_ARC false).
            reset_actions.add(() -> style_combo.setSelectedIndex(1));

            addAlignedSubRow(swap_sub, 1, "/images/menu/swap.png", style_text, style_combo);
        }
        addToGroup(swap_group, swap_sub);
        addLeft(anim, indent(swap_group));

        // --- Table reseat on player exit (DynamicTablePanel, Settings only) --- Its
        // slide animation speed hangs off it.
        JPanel downgrade_group = groupBox();
        addToGroup(downgrade_group, animCheckbox("/images/menu/reseat.png", "menu.efectos_animacion_downgrade",
                null, "animacion_downgrade", v -> GameFrame.ANIMACION_DOWNGRADE_PREF = v, GameFrame.ANIMACION_DOWNGRADE_PREF));
        final JCheckBox downgrade_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Reseat speed: 3 options (slow/normal/fast). "Normal" = default value (500 ms).
        // Hangs off the setting: disabled if unchecked or the master is off. Stores the
        // duration in ms (GameFrame.DOWNGRADE_VELOCIDAD).
        {
            final int[] speed_ms = {800, GameFrame.DEFAULT_DOWNGRADE_VELOCIDAD, 300}; // slow, normal, fast
            final String[] speed_keys = {"settings.downgrade_lento", "settings.downgrade_normal", "settings.downgrade_rapido"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel dg_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> dg_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selects the option whose saved ms is closest (defaults to Normal).
            int sel = 1, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_ms.length; i++) {
                int d = Math.abs(speed_ms[i] - GameFrame.DOWNGRADE_VELOCIDAD);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            dg_combo.setSelectedIndex(sel);
            dg_combo.setMaximumSize(dg_combo.getPreferredSize());
            dg_combo.addActionListener(e -> {
                int ms = speed_ms[dg_combo.getSelectedIndex()];
                GameFrame.DOWNGRADE_VELOCIDAD = ms;
                persist("downgrade_velocidad", String.valueOf(ms));
            });
            Helpers.setTranslatedToolTip(dg_combo, "tooltip.cfg.downgrade_velocidad");

            Runnable updateDgEnabled = () -> {
                boolean on = anim_master.isSelected() && downgrade_cb.isSelected();
                dg_combo.setEnabled(on);
                dg_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateDgEnabled.run());
            downgrade_cb.addActionListener(e -> updateDgEnabled.run());
            updateDgEnabled.run();
            // Default: "Normal" speed (index 1 = DEFAULT_DOWNGRADE_VELOCIDAD).
            reset_actions.add(() -> dg_combo.setSelectedIndex(1));

            JPanel dg_row = naturalRow();
            dg_row.add(Box.createHorizontalStrut(Math.round(18 * Helpers.DIALOG_ZOOM))); // sub-option of the setting
            dg_row.add(new JLabel(icon("/images/menu/clock.png")));
            dg_row.add(dg_text);
            dg_row.add(dg_combo);
            addToGroup(downgrade_group, dg_row);
        }
        addLeft(anim, indent(downgrade_group));

        addLeft(anim, indent(animCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_ciegas_dealer",
                gf != null ? gf.getAnim_ciegas_dealer_menu() : null, "animacion_ciegas_dealer", v -> GameFrame.ANIMACION_CIEGAS_DEALER_PREF = v), 28));
        addLeft(anim, indent(animCheckbox("/images/menu/chips.png", "menu.efectos_animacion_apuestas",
                gf != null ? gf.getAnim_apuestas_menu() : null, "animacion_apuestas", v -> GameFrame.ANIMACION_APUESTAS_PREF = v), 28));
        addLeft(anim, indent(animCheckbox("/images/menu/meter.png", "menu.efectos_animacion_contadores",
                gf != null ? gf.getAnim_contadores_menu() : null, "animacion_contadores", v -> GameFrame.ANIMACION_CONTADORES_PREF = v), 28));
        // GAME-END screen countdown, right below "Counters" with the same style
        // (animCheckbox: bold + indent 28 + gated by the master via anim_sub_cb). menu=null
        // because it's settings-only (no in-game menu item). Its SFX hangs off the
        // countdown itself (contadorFinalAnimOn), so turning it off silences that too.
        addLeft(anim, indent(animCheckbox("/images/menu/meter.png", "settings.animacion_contador_final",
                null, "animacion_contador_final", v -> GameFrame.ANIMACION_CONTADOR_FINAL_PREF = v), 28));

        // Row of Animations | (Table over Screen) at NATURAL HEIGHT in the NORTH, aligned
        // top-left. Animations (the tallest column, since it groups Shuffle/Deal/Reveal)
        // stands ALONE on the left, and the two shorter ones (Table and Screen) stack on
        // the right to balance heights and keep the dialog shorter. The right column's
        // leftover space is absorbed BETWEEN Table and Screen (middle glue, see
        // right_inner), without stretching or clipping the sub-panels, so Screen's bottom
        // border aligns with Animations'.
        anim.setAlignmentY(JComponent.TOP_ALIGNMENT);
        mesa.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        pantalla.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        // ---------------- Graphics profile (loose row at the bottom of Animations) ----------------
        // Quality/Performance selector as one more row at the bottom of the Animations
        // column, with NO box of its own and aligned LEFT with the "USE ANIMATIONS" master
        // (not indented). GATED by the master: with "USE ANIMATIONS" off no animation
        // runs, so the profile doesn't apply and its combo is DISABLED (same as the speed
        // sub-controls). Stores the anim_calidad boolean (true=Quality, false=Performance).
        // "Quality" (index 0, default) = EXACTLY the historical behavior; "Performance"
        // trims per-frame cost (flips without rotation + reveal without supersampling:
        // less crisp image, same smoothness).
        {
            final String[] q_labels = {Translator.translate("settings.calidad"),
                Translator.translate("settings.rendimiento")};

            final JLabel q_text = new JLabel(Translator.translate("settings.perfil_grafico") + ":");
            final javax.swing.JComboBox<String> q_combo = new javax.swing.JComboBox<>(q_labels);

            q_combo.setSelectedIndex(GameFrame.ANIM_CALIDAD ? 0 : 1);
            q_combo.setMaximumSize(q_combo.getPreferredSize());
            q_combo.addActionListener(e -> {
                boolean calidad = q_combo.getSelectedIndex() == 0;
                GameFrame.ANIM_CALIDAD = calidad;
                persist("anim_calidad", String.valueOf(calidad));
            });
            Helpers.setTranslatedToolTip(q_combo, "tooltip.cfg.anim_calidad");
            // Default: Quality (index 0).
            reset_actions.add(() -> q_combo.setSelectedIndex(0));

            // Gated by the master: combo + label disable if "USE ANIMATIONS" is off. Same
            // pattern as the speed sub-controls; restoreDefaults re-enables the master with
            // anim_master.doClick(), which fires this listener and re-enables the combo.
            Runnable updatePerfilEnabled = () -> {
                boolean on = anim_master.isSelected();
                q_combo.setEnabled(on);
                q_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updatePerfilEnabled.run());
            updatePerfilEnabled.run();

            JPanel q_row = naturalRow();
            q_row.add(new JLabel(icon("/images/menu/flip.png")));
            q_row.add(q_text);
            q_row.add(q_combo);
            addLeft(anim, q_row);
        }
        // Glue at the bottom of Animations (now including the graphics profile): if this
        // column ends up SHORTER, stretching it to match heights collects the gap
        // cleanly at the bottom.
        closeColumn(anim);

        JPanel right_inner = new JPanel();
        right_inner.setLayout(new BoxLayout(right_inner, BoxLayout.Y_AXIS));
        right_inner.setAlignmentY(JComponent.TOP_ALIGNMENT);
        // Table pinned at the TOP, Screen pinned at the BOTTOM: the glue sits BETWEEN them
        // (not at the column's foot). So when this column is shorter than Animations (the
        // tallest, grouping Shuffle/Deal/Reveal + Graphics), all the leftover space is
        // absorbed BETWEEN the two panels and "Screen and zoom"'s bottom border aligns
        // with "Animations"' (both columns stretch to the taller one's height). Neither
        // panel stretches: both stay at their natural height (no empty strip inside their
        // titled border); the glue absorbs all the leftover as spacing BETWEEN panels,
        // same as the minimum 10px strut.
        right_inner.add(mesa);
        right_inner.add(Box.createVerticalStrut(Math.round(10 * Helpers.DIALOG_ZOOM)));
        right_inner.add(Box.createVerticalGlue());
        right_inner.add(pantalla);

        // Both columns stretch vertically to the taller one's height (BoxLayout X with an
        // uncapped maximum) so their bottom borders line up.
        anim.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        right_inner.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(anim);
        row.add(Box.createHorizontalStrut(Math.round(12 * Helpers.DIALOG_ZOOM)));
        row.add(right_inner);

        add(row, java.awt.BorderLayout.NORTH);

        building = false;
    }

    /**
     * Applies the display mode chosen in the combo. Called by the dialog on SAVE (not
     * live: toggling disposes and recreates the frame, which would corrupt the open
     * dialog). Only acts if the user CHANGED the combo from the opening state; otherwise
     * it leaves AUTO_FULLSCREEN untouched, so saving an unrelated setting doesn't rewrite
     * the startup preference (e.g. after a transient ALT+F that doesn't change it).
     * In-game it changes the frame's mode; out of game it only persists the preference.
     */
    public void applyPendingDisplayMode() {
        if (pending_fullscreen == snap_fullscreen) {
            return;
        }
        if (gf != null) {
            gf.setDisplayModeFullScreen(pending_fullscreen);
        } else {
            GameFrame.AUTO_FULLSCREEN = pending_fullscreen;
            persist("auto_fullscreen", String.valueOf(pending_fullscreen));
        }
    }

    /**
     * Applies the pending dialog zoom. Not previewed live (it would affect the
     * discard-changes dialog itself on cancel) -- applied and persisted only on SAVE.
     * Takes effect on dialogs opened after this point (they read Helpers.DIALOG_ZOOM at
     * construction). Called by SettingsDialog on Save.
     */
    public void applyPendingDialogZoom() {
        if (pending_dialog_zoom == snap_dialog_zoom) {
            return;
        }
        Helpers.DIALOG_ZOOM = pending_dialog_zoom;
        Helpers.updateCoronaDialogsFont();
        persist("dialog_zoom", String.valueOf(Helpers.DIALOG_ZOOM));
    }

    /**
     * Whether appearance changed from the opening state (includes the pending display
     * mode, not yet applied). Used by the dialog to confirm before discarding on cancel.
     * Animation preferences are read from PROPERTIES (equivalent to the menu item, see
     * class note), so this doesn't depend on gf.
     *
     * @return true if any appearance setting differs from the snapshot taken on open
     */
    public boolean isDirty() {
        return GameFrame.ZOOM_LEVEL != snap_zoom_level
                || GameFrame.VISTA_COMPACTA != snap_vista_compacta
                || !snap_baraja.equals(GameFrame.BARAJA)
                || !snap_trasera.equals(GameFrame.TRASERA)
                || !snap_color_tapete.equals(GameFrame.COLOR_TAPETE)
                || GameFrame.AUTO_ZOOM != snap_auto_zoom
                || GameFrame.SHOW_CLOCK != snap_show_clock
                || GameFrame.MOSTRAR_COSTE_IGUALAR != snap_coste_igualar
                || prefBool("cinematicas") != snap_cinematicas
                || prefBool("cinematicas_accion", true) != snap_cinematicas_accion
                || prefBool("cinematicas_allin", true) != snap_cinematicas_allin
                || prefBool("cinematicas_gameover", true) != snap_cinematicas_gameover
                || GameFrame.ANIMACION_BARAJADO_PREF != snap_anim_barajado
                || prefBool("animacion_reparto") != snap_anim_reparto
                || GameFrame.ANIMACION_DESTAPE_PREF != snap_anim_destape
                || prefBool("animacion_ciegas_dealer") != snap_anim_ciegas_dealer
                || prefBool("animacion_apuestas") != snap_anim_apuestas
                || prefBool("animacion_contadores") != snap_anim_contadores
                || prefBool("animacion_cascada_overlay", false) != snap_anim_cascada_overlay
                || prefBool("resaltar_jugada_showdown", true) != snap_resaltar_jugada_showdown
                || prefBool("resaltar_avatares", false) != snap_resaltar_avatares
                || prefBool("screenshot_fin_timba", false) != snap_screenshot_fin_timba
                || prefBool("animacion_contador_final", true) != snap_animacion_contador_final
                || GameFrame.ANIMACIONES != snap_animaciones
                || GameFrame.CHAT_IMAGES_INGAME != snap_chat_images
                || pending_fullscreen != snap_fullscreen
                || GameFrame.CARD_FLIP_DURATION != snap_card_flip_duration
                || GameFrame.CARD_FLIP_ZOOM != snap_card_flip_zoom
                || GameFrame.REPARTO_VELOCIDAD != snap_reparto_velocidad
                || GameFrame.ANIM_CALIDAD != snap_anim_calidad
                || GameFrame.ANIMACION_SWAP_PREF != snap_anim_swap
                || GameFrame.SWAP_ANIM_DURATION != snap_swap_duration
                || GameFrame.SWAP_ANIM_ARC != snap_swap_arc
                || GameFrame.ANIMACION_DOWNGRADE_PREF != snap_anim_downgrade
                || GameFrame.DOWNGRADE_VELOCIDAD != snap_downgrade_velocidad
                || GameFrame.NIVEL_LUZ != snap_nivel_luz
                || pending_dialog_zoom != snap_dialog_zoom;
    }

    /**
     * Reverts (on CANCEL of the transactional dialog) the appearance settings to the
     * state captured on open. In-game each one is re-applied through its normal path
     * (live effect); out of game only the preferences are re-persisted.
     */
    public void revert() {
        if (gf != null) {
            revertLive();
        } else {
            revertStandalone();
        }
    }

    /**
     * Restores ALL appearance settings to their factory values, applying them LIVE like
     * any other edit (transactional dialog: SAVE keeps them, Cancel reverts to the
     * opening state). Follows the same path as a user click on each control, so in-game
     * the effect is live and out of game it's persist-only. Called by the dialog's
     * "Restore defaults" button.
     */
    public void restoreDefaults() {
        // 1) Re-enables the animation MASTER (default ON) BEFORE the children: with the
        //    master off their menu items are grayed out and a doClick would be a no-op
        //    (same as revertLive).
        if (!anim_master.isSelected()) {
            anim_master.doClick();
        }
        // 2) All individual animation toggles default to ON.
        for (JCheckBox cb : anim_sub_cb) {
            if (!cb.isSelected()) {
                cb.doClick();
            }
        }
        // 3) Per-control reset (table/screen combos/spinners/checkboxes + cascade +
        //    speeds), in creation order; with master and children already on, the
        //    sub-controls are enabled.
        for (Runnable action : reset_actions) {
            action.run();
        }
    }

    // Revert IN-GAME: re-applies each setting through its normal path (toggles via
    // doClick if they differ; zoom/compact via its setter; deck/felt by reselecting the
    // radio item). The pending display mode is NOT applied on cancel.
    private void revertLive() {
        if (GameFrame.ZOOM_LEVEL != snap_zoom_level) {
            gf.setZoomLevel(snap_zoom_level);
        }
        if (GameFrame.VISTA_COMPACTA != snap_vista_compacta) {
            gf.setCompactView(snap_vista_compacta);
        }
        if (!snap_baraja.equals(GameFrame.BARAJA)) {
            selectBaraja(gf, snap_baraja);
        }
        if (!snap_trasera.equals(GameFrame.TRASERA)) {
            gf.setTrasera(snap_trasera);
        }
        if (!snap_color_tapete.equals(GameFrame.COLOR_TAPETE)) {
            selectTapete(gf, snap_color_tapete);
        }
        if (GameFrame.AUTO_ZOOM != snap_auto_zoom) {
            if (gf.getAuto_fit_zoom_menu().isEnabled()) {
                gf.getAuto_fit_zoom_menu().doClick();
            } else {
                // The auto-fit menu is disabled while the async autoZoom runs (right after
                // turning it on); a doClick here would be a no-op and AUTO_ZOOM would leak
                // through on cancel. Revert the flag directly (this case only turns it off).
                GameFrame.AUTO_ZOOM = snap_auto_zoom;
                gf.getAuto_fit_zoom_menu().setSelected(snap_auto_zoom);
                Helpers.TapetePopupMenu.AUTO_ZOOM_MENU.setSelected(snap_auto_zoom);
                Helpers.PROPERTIES.setProperty("auto_zoom", String.valueOf(snap_auto_zoom));
                Helpers.savePropertiesFile();
            }
        }
        if (GameFrame.SHOW_CLOCK != snap_show_clock) {
            gf.getTime_menu().doClick();
        }
        if (GameFrame.MOSTRAR_COSTE_IGUALAR != snap_coste_igualar) {
            gf.getCoste_igualar_menu().doClick();
        }
        // Animations (transactional with a master): preferences live in each item's
        // isSelected and are ONLY reverted via doClick on an ENABLED item (they're
        // disabled with the master off). Hence: enable (master on) -> revert each
        // preference (comparing isSelected to its snapshot) -> restore the master to its
        // snapshot, which re-gates and re-disables if needed. (Leaving the master off
        // first would make the doClicks land on disabled items = no-op, and the
        // preference wouldn't revert.)
        if (GameFrame.ANIMACIONES != snap_animaciones
                || gf.getMenu_cinematicas().isSelected() != snap_cinematicas
                || gf.getAnim_reparto_menu().isSelected() != snap_anim_reparto
                || gf.getAnim_ciegas_dealer_menu().isSelected() != snap_anim_ciegas_dealer
                || gf.getAnim_apuestas_menu().isSelected() != snap_anim_apuestas
                || gf.getAnim_contadores_menu().isSelected() != snap_anim_contadores) {
            gf.setAnimacionesMaster(true);
            if (gf.getMenu_cinematicas().isSelected() != snap_cinematicas) {
                gf.getMenu_cinematicas().doClick();
            }
            if (gf.getAnim_reparto_menu().isSelected() != snap_anim_reparto) {
                gf.getAnim_reparto_menu().doClick();
            }
            if (gf.getAnim_ciegas_dealer_menu().isSelected() != snap_anim_ciegas_dealer) {
                gf.getAnim_ciegas_dealer_menu().doClick();
            }
            if (gf.getAnim_apuestas_menu().isSelected() != snap_anim_apuestas) {
                gf.getAnim_apuestas_menu().doClick();
            }
            if (gf.getAnim_contadores_menu().isSelected() != snap_anim_contadores) {
                gf.getAnim_contadores_menu().doClick();
            }
            gf.setAnimacionesMaster(snap_animaciones);
        }
        // The cascade overlay has no menu item and no live effect (it only appears during
        // the shuffle): reverted by setting the flag directly + persisting, like CHAT_IMAGES.
        if (GameFrame.ANIMACION_CASCADA_OVERLAY_PREF != snap_anim_cascada_overlay) {
            GameFrame.ANIMACION_CASCADA_OVERLAY_PREF = snap_anim_cascada_overlay;
            Helpers.PROPERTIES.setProperty("animacion_cascada_overlay", String.valueOf(snap_anim_cascada_overlay));
            Helpers.savePropertiesFile();
        }
        // Cinematics subtypes (action / all-in / game over): persist-only, no menu item;
        // reverted by setting the flag + re-persisting the snapshot, like the cascade overlay.
        if (GameFrame.CINEMATICAS_ACCION_PREF != snap_cinematicas_accion) {
            GameFrame.CINEMATICAS_ACCION_PREF = snap_cinematicas_accion;
            Helpers.PROPERTIES.setProperty("cinematicas_accion", String.valueOf(snap_cinematicas_accion));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.CINEMATICAS_ALLIN_PREF != snap_cinematicas_allin) {
            GameFrame.CINEMATICAS_ALLIN_PREF = snap_cinematicas_allin;
            Helpers.PROPERTIES.setProperty("cinematicas_allin", String.valueOf(snap_cinematicas_allin));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.CINEMATICAS_GAMEOVER_PREF != snap_cinematicas_gameover) {
            GameFrame.CINEMATICAS_GAMEOVER_PREF = snap_cinematicas_gameover;
            Helpers.PROPERTIES.setProperty("cinematicas_gameover", String.valueOf(snap_cinematicas_gameover));
            Helpers.savePropertiesFile();
        }
        // Showdown highlight: persist-only, no menu item and no live effect (read on the
        // fly). Reverted by setting the flag + re-persisting the snapshot, like the cascade overlay.
        if (GameFrame.RESALTAR_JUGADA_SHOWDOWN != snap_resaltar_jugada_showdown) {
            GameFrame.RESALTAR_JUGADA_SHOWDOWN = snap_resaltar_jugada_showdown;
            Helpers.PROPERTIES.setProperty("resaltar_jugada_showdown", String.valueOf(snap_resaltar_jugada_showdown));
            Helpers.savePropertiesFile();
        }
        // Avatar zoom: persist-only, no menu item. Same as the showdown highlight; if
        // reverting turns it off while a magnifier is showing, its watcher removes it on
        // the next poll (canShow checks this same flag).
        if (GameFrame.RESALTAR_AVATARES != snap_resaltar_avatares) {
            GameFrame.RESALTAR_AVATARES = snap_resaltar_avatares;
            Helpers.PROPERTIES.setProperty("resaltar_avatares", String.valueOf(snap_resaltar_avatares));
            Helpers.savePropertiesFile();
        }
        // Screenshot at game end: persist-only, no menu item and no live effect. Reverted
        // by setting the flag + re-persisting the snapshot, like the showdown highlight.
        if (GameFrame.SCREENSHOT_FIN_TIMBA != snap_screenshot_fin_timba) {
            GameFrame.SCREENSHOT_FIN_TIMBA = snap_screenshot_fin_timba;
            Helpers.PROPERTIES.setProperty("screenshot_fin_timba", String.valueOf(snap_screenshot_fin_timba));
            Helpers.savePropertiesFile();
        }
        // Game-end countdown: persist-only, no live effect (only applies when the final
        // screen opens). Reverted by setting the flag + re-persisting the snapshot.
        if (GameFrame.ANIMACION_CONTADOR_FINAL_PREF != snap_animacion_contador_final) {
            GameFrame.ANIMACION_CONTADOR_FINAL_PREF = snap_animacion_contador_final;
            Helpers.PROPERTIES.setProperty("animacion_contador_final", String.valueOf(snap_animacion_contador_final));
            Helpers.savePropertiesFile();
        }
        // Shuffle and reveal also have no menu item: reverted by setting the flag +
        // persisting, like the cascade overlay. Restoring shuffle to ON re-warms the
        // shuffle.gif cache in case the warm-up was skipped while it was off.
        if (GameFrame.ANIMACION_BARAJADO_PREF != snap_anim_barajado) {
            GameFrame.ANIMACION_BARAJADO_PREF = snap_anim_barajado;
            Helpers.PROPERTIES.setProperty("animacion_barajado", String.valueOf(snap_anim_barajado));
            Helpers.savePropertiesFile();
            if (snap_anim_barajado) {
                Crupier.warmShuffleAnimCache();
            }
        }
        if (GameFrame.ANIMACION_DESTAPE_PREF != snap_anim_destape) {
            GameFrame.ANIMACION_DESTAPE_PREF = snap_anim_destape;
            Helpers.PROPERTIES.setProperty("animacion_destape", String.valueOf(snap_anim_destape));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.CHAT_IMAGES_INGAME != snap_chat_images) {
            gf.getChat_image_menu().doClick();
        }
        // Reveal speed: no menu item (like the cascade overlay), reverted by setting the
        // flag + re-persisting the snapshot.
        if (GameFrame.CARD_FLIP_DURATION != snap_card_flip_duration) {
            GameFrame.CARD_FLIP_DURATION = snap_card_flip_duration;
            Helpers.PROPERTIES.setProperty("card_flip_duration", String.valueOf(snap_card_flip_duration));
            Helpers.savePropertiesFile();
        }
        // Zoom-in effect: same path as the speed (no menu item, no live effect).
        if (GameFrame.CARD_FLIP_ZOOM != snap_card_flip_zoom) {
            GameFrame.CARD_FLIP_ZOOM = snap_card_flip_zoom;
            Helpers.PROPERTIES.setProperty("card_flip_zoom", String.valueOf(snap_card_flip_zoom));
            Helpers.savePropertiesFile();
        }
        // Deal speed: same path (persist-only).
        if (GameFrame.REPARTO_VELOCIDAD != snap_reparto_velocidad) {
            GameFrame.REPARTO_VELOCIDAD = snap_reparto_velocidad;
            Helpers.PROPERTIES.setProperty("reparto_velocidad", String.valueOf(snap_reparto_velocidad));
            Helpers.savePropertiesFile();
        }
        // Quality profile: persist-only (read by the animation code when rendering each effect).
        if (GameFrame.ANIM_CALIDAD != snap_anim_calidad) {
            GameFrame.ANIM_CALIDAD = snap_anim_calidad;
            Helpers.PROPERTIES.setProperty("anim_calidad", String.valueOf(snap_anim_calidad));
            Helpers.savePropertiesFile();
        }
        // Sort hand (swap): checkbox + speed + style, all persist-only (no menu item).
        if (GameFrame.ANIMACION_SWAP_PREF != snap_anim_swap) {
            GameFrame.ANIMACION_SWAP_PREF = snap_anim_swap;
            Helpers.PROPERTIES.setProperty("animacion_swap", String.valueOf(snap_anim_swap));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.SWAP_ANIM_DURATION != snap_swap_duration) {
            GameFrame.SWAP_ANIM_DURATION = snap_swap_duration;
            Helpers.PROPERTIES.setProperty("swap_velocidad", String.valueOf(snap_swap_duration));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.SWAP_ANIM_ARC != snap_swap_arc) {
            GameFrame.SWAP_ANIM_ARC = snap_swap_arc;
            Helpers.PROPERTIES.setProperty("swap_arco", String.valueOf(snap_swap_arc));
            Helpers.savePropertiesFile();
        }
        // Table reseat (checkbox + speed): persist-only, no menu item.
        if (GameFrame.ANIMACION_DOWNGRADE_PREF != snap_anim_downgrade) {
            GameFrame.ANIMACION_DOWNGRADE_PREF = snap_anim_downgrade;
            Helpers.PROPERTIES.setProperty("animacion_downgrade", String.valueOf(snap_anim_downgrade));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.DOWNGRADE_VELOCIDAD != snap_downgrade_velocidad) {
            GameFrame.DOWNGRADE_VELOCIDAD = snap_downgrade_velocidad;
            Helpers.PROPERTIES.setProperty("downgrade_velocidad", String.valueOf(snap_downgrade_velocidad));
            Helpers.savePropertiesFile();
        }
        // Light level: reverted by setting the flag + re-persisting the snapshot, and the
        // preview must also be undone (if the lights are off, they'd still be painted at
        // the level left by the discarded edit).
        if (GameFrame.NIVEL_LUZ != snap_nivel_luz) {
            GameFrame.NIVEL_LUZ = snap_nivel_luz;
            Helpers.PROPERTIES.setProperty("nivel_luz", String.valueOf(snap_nivel_luz));
            Helpers.savePropertiesFile();
            applyNivelLuz();
        }
        // Dialog zoom: persist-only, no live effect (each dialog reads it on open).
        // Reverted by setting the flag + re-persisting the snapshot, like the other
        // persist-only settings.
        if (Helpers.DIALOG_ZOOM != snap_dialog_zoom) {
            Helpers.DIALOG_ZOOM = snap_dialog_zoom;
            Helpers.updateCoronaDialogsFont();
            Helpers.PROPERTIES.setProperty("dialog_zoom", String.valueOf(snap_dialog_zoom));
            Helpers.savePropertiesFile();
        }
    }

    // Revert OUT OF GAME: re-persists each preference to its snapshot (no live effect,
    // there's no table). Sets the static flags and flushes PROPERTIES once.
    private void revertStandalone() {
        // The felt is the only setting with a live preview out of game (launcher
        // background); if it changed during the session, the launcher must repaint on revert.
        boolean tapete_changed = !snap_color_tapete.equals(GameFrame.COLOR_TAPETE);

        // The card/chip/back image cache (Card.updateCachedImages) is DERIVED from
        // zoom + deck + back and isn't just another flag to revert below: the deck/back
        // listeners (and "Restore defaults", which resets the deck with zoom already at
        // its default) REBUILD it live on the fly. If any of those three changed during
        // the dialog session, the cache is stuck at the NEW scale/deck, and since starting
        // a game only calls zoom() (not updateCachedImages), the next game would inherit
        // cards/chips at the wrong size even though ZOOM_LEVEL/BARAJA are already
        // reverted. So, like revertLive in-game (via setZoomLevel/selectBaraja), the cache
        // must be rebuilt to the opening state here. Recorded BEFORE reverting the static
        // fields and redone at the end.
        boolean baraja_reverted = !snap_baraja.equals(GameFrame.BARAJA);
        boolean rebuild_card_cache = GameFrame.ZOOM_LEVEL != snap_zoom_level
                || baraja_reverted
                || !snap_trasera.equals(GameFrame.TRASERA);

        GameFrame.ZOOM_LEVEL = snap_zoom_level;
        GameFrame.VISTA_COMPACTA = snap_vista_compacta;
        GameFrame.BARAJA = snap_baraja;
        GameFrame.TRASERA = snap_trasera;
        GameFrame.COLOR_TAPETE = snap_color_tapete;
        GameFrame.AUTO_ZOOM = snap_auto_zoom;
        GameFrame.SHOW_CLOCK = snap_show_clock;
        GameFrame.MOSTRAR_COSTE_IGUALAR = snap_coste_igualar;
        GameFrame.CHAT_IMAGES_INGAME = snap_chat_images;
        GameFrame.ANIMACIONES = snap_animaciones;
        GameFrame.CINEMATICAS_PREF = snap_cinematicas;
        GameFrame.CINEMATICAS_ACCION_PREF = snap_cinematicas_accion;
        GameFrame.CINEMATICAS_ALLIN_PREF = snap_cinematicas_allin;
        GameFrame.CINEMATICAS_GAMEOVER_PREF = snap_cinematicas_gameover;
        GameFrame.ANIMACION_BARAJADO_PREF = snap_anim_barajado;
        GameFrame.ANIMACION_REPARTO_PREF = snap_anim_reparto;
        GameFrame.ANIMACION_DESTAPE_PREF = snap_anim_destape;
        GameFrame.ANIMACION_CIEGAS_DEALER_PREF = snap_anim_ciegas_dealer;
        GameFrame.ANIMACION_APUESTAS_PREF = snap_anim_apuestas;
        GameFrame.ANIMACION_CONTADORES_PREF = snap_anim_contadores;
        GameFrame.ANIMACION_CASCADA_OVERLAY_PREF = snap_anim_cascada_overlay;
        GameFrame.RESALTAR_JUGADA_SHOWDOWN = snap_resaltar_jugada_showdown;
        GameFrame.RESALTAR_AVATARES = snap_resaltar_avatares;
        GameFrame.SCREENSHOT_FIN_TIMBA = snap_screenshot_fin_timba;
        GameFrame.ANIMACION_CONTADOR_FINAL_PREF = snap_animacion_contador_final;
        GameFrame.CARD_FLIP_DURATION = snap_card_flip_duration;
        GameFrame.CARD_FLIP_ZOOM = snap_card_flip_zoom;
        GameFrame.REPARTO_VELOCIDAD = snap_reparto_velocidad;
        GameFrame.ANIM_CALIDAD = snap_anim_calidad;
        GameFrame.ANIMACION_SWAP_PREF = snap_anim_swap;
        GameFrame.SWAP_ANIM_DURATION = snap_swap_duration;
        GameFrame.SWAP_ANIM_ARC = snap_swap_arc;
        GameFrame.ANIMACION_DOWNGRADE_PREF = snap_anim_downgrade;
        GameFrame.DOWNGRADE_VELOCIDAD = snap_downgrade_velocidad;
        GameFrame.NIVEL_LUZ = snap_nivel_luz;
        Helpers.DIALOG_ZOOM = snap_dialog_zoom;
        Helpers.updateCoronaDialogsFont();

        Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(snap_zoom_level));
        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(snap_vista_compacta));
        Helpers.PROPERTIES.setProperty("baraja", snap_baraja);
        Helpers.PROPERTIES.setProperty("trasera", snap_trasera);
        Helpers.PROPERTIES.setProperty("color_tapete", snap_color_tapete);
        Helpers.PROPERTIES.setProperty("auto_zoom", String.valueOf(snap_auto_zoom));
        Helpers.PROPERTIES.setProperty("show_time", String.valueOf(snap_show_clock));
        Helpers.PROPERTIES.setProperty("mostrar_coste_igualar", String.valueOf(snap_coste_igualar));
        Helpers.PROPERTIES.setProperty("chat_images_ingame", String.valueOf(snap_chat_images));
        Helpers.PROPERTIES.setProperty("animaciones", String.valueOf(snap_animaciones));
        Helpers.PROPERTIES.setProperty("cinematicas", String.valueOf(snap_cinematicas));
        Helpers.PROPERTIES.setProperty("cinematicas_accion", String.valueOf(snap_cinematicas_accion));
        Helpers.PROPERTIES.setProperty("cinematicas_allin", String.valueOf(snap_cinematicas_allin));
        Helpers.PROPERTIES.setProperty("cinematicas_gameover", String.valueOf(snap_cinematicas_gameover));
        Helpers.PROPERTIES.setProperty("animacion_barajado", String.valueOf(snap_anim_barajado));
        Helpers.PROPERTIES.setProperty("animacion_reparto", String.valueOf(snap_anim_reparto));
        Helpers.PROPERTIES.setProperty("animacion_destape", String.valueOf(snap_anim_destape));
        Helpers.PROPERTIES.setProperty("animacion_ciegas_dealer", String.valueOf(snap_anim_ciegas_dealer));
        Helpers.PROPERTIES.setProperty("animacion_apuestas", String.valueOf(snap_anim_apuestas));
        Helpers.PROPERTIES.setProperty("animacion_contadores", String.valueOf(snap_anim_contadores));
        Helpers.PROPERTIES.setProperty("animacion_cascada_overlay", String.valueOf(snap_anim_cascada_overlay));
        Helpers.PROPERTIES.setProperty("resaltar_jugada_showdown", String.valueOf(snap_resaltar_jugada_showdown));
        Helpers.PROPERTIES.setProperty("resaltar_avatares", String.valueOf(snap_resaltar_avatares));
        Helpers.PROPERTIES.setProperty("screenshot_fin_timba", String.valueOf(snap_screenshot_fin_timba));
        Helpers.PROPERTIES.setProperty("animacion_contador_final", String.valueOf(snap_animacion_contador_final));
        Helpers.PROPERTIES.setProperty("card_flip_duration", String.valueOf(snap_card_flip_duration));
        Helpers.PROPERTIES.setProperty("card_flip_zoom", String.valueOf(snap_card_flip_zoom));
        Helpers.PROPERTIES.setProperty("reparto_velocidad", String.valueOf(snap_reparto_velocidad));
        Helpers.PROPERTIES.setProperty("anim_calidad", String.valueOf(snap_anim_calidad));
        Helpers.PROPERTIES.setProperty("animacion_swap", String.valueOf(snap_anim_swap));
        Helpers.PROPERTIES.setProperty("swap_velocidad", String.valueOf(snap_swap_duration));
        Helpers.PROPERTIES.setProperty("swap_arco", String.valueOf(snap_swap_arc));
        Helpers.PROPERTIES.setProperty("animacion_downgrade", String.valueOf(snap_anim_downgrade));
        Helpers.PROPERTIES.setProperty("downgrade_velocidad", String.valueOf(snap_downgrade_velocidad));
        Helpers.PROPERTIES.setProperty("nivel_luz", String.valueOf(snap_nivel_luz));
        Helpers.PROPERTIES.setProperty("dialog_zoom", String.valueOf(snap_dialog_zoom));
        Helpers.savePropertiesFile();

        // Rebuilds the derived cache to the opening state (deck/back already reverted
        // above, so updateCachedImages reads the correct GameFrame.BARAJA/TRASERA) so the
        // next game doesn't inherit cards/chips at the scale/deck left by a discarded edit.
        if (rebuild_card_cache) {
            Card.updateCachedImages(1f + snap_zoom_level * GameFrame.getZOOM_STEP(), true);
        }

        // If the deck reverts to one whose shuffle.gif wasn't warmed during the dialog
        // session, re-warm the cache (BARAJA is already reverted above) so the decode
        // doesn't drag into the first hand. Out of game there's no cambiarBaraja() to do it.
        if (baraja_reverted) {
            Crupier.warmShuffleAnimCache();
        }

        if (tapete_changed) {
            refreshLauncherTapete();
        }
    }

    // Repaints the launcher background (InitPanel) on the fly with the current
    // COLOR_TAPETE: this is the felt's live preview out of game. No-op if the launcher
    // doesn't exist yet (startup). InitPanel reloads the texture in the background and,
    // for base colors, is independent of the panel size, so it's safe even while the
    // launcher is hidden (e.g. opening the dialog from the waiting room). Also plays the
    // same sound effect (mat.wav) as TablePanel.refresh() in-game, so changing (or
    // reverting) the felt sounds the same in and out of the table. The sound lives INSIDE
    // the guard here, NOT in InitPanel.refresh(): that method also runs on the secret "*"
    // felt's resize, where it must stay silent.
    private static void refreshLauncherTapete() {
        if (Init.VENTANA_INICIO != null) {
            if (GameFrame.tapeteSonidoOn()) {
                Audio.playWavResource("misc/mat.wav");
            }
            Init.VENTANA_INICIO.getTapete().refresh();
        }
    }

    // Live brightness preview: the dimming overlay is always recalculated with the
    // just-saved level, but there's only something to repaint if the table is currently
    // dimmed (the setting is exactly that overlay's depth). With the lights on, nothing
    // shows until they're turned off.
    // Checks GameFrame.getInstance(), NOT the gf captured on dialog open: if the game
    // ended while Settings was open, that gf is still non-null but the table no longer
    // exists; this method also runs when discarding changes (revertLive).
    // Repainting is enough and avoids revalidating the whole frame on every spinner tick:
    // the light switch shows the same icon (still off) and the quick-chat colors depend
    // on whether there's an overlay, not how deep. The in-game warning does paint a
    // proportional overlay, so it's repainted too.
    private static void applyNivelLuz() {

        GameFrame live = GameFrame.getInstance();

        if (live == null || live.getTapete() == null) {
            return;
        }

        // Recalculates the overlay with the just-saved level WITHOUT touching who
        // requested it (neither the player's switch nor the game's temporary dimming).
        live.getCapa_brillo().refreshBrightness();

        if (live.getCapa_brillo().getBrightness() == 0f) {
            return;
        }

        live.getTapete().repaint();

        if (live.getNotify_dialog() != null) {
            live.getNotify_dialog().repaint();
        }
    }

    private static void selectBaraja(GameFrame gf, String baraja) {
        for (Component c : gf.getMenu_barajas().getMenuComponents()) {
            if (c instanceof JMenuItem && ((JMenuItem) c).getText().equals(baraja)) {
                ((JMenuItem) c).doClick();
                break;
            }
        }
    }

    private static void selectTapete(GameFrame gf, String color) {
        if (color.startsWith("azul")) {
            gf.getMenu_tapete_azul().doClick();
        } else if (color.startsWith("rojo")) {
            gf.getMenu_tapete_rojo().doClick();
        } else if (color.startsWith("negro")) {
            gf.getMenu_tapete_negro().doClick();
        } else if (color.startsWith("madera")) {
            gf.getMenu_tapete_madera().doClick();
        } else {
            gf.getMenu_tapete_verde().doClick();
        }
    }

    // Base felt color for the combo index (0=green..4=wood). Out of game this base value
    // is what gets persisted (without easter-egg suffixes, which are only resolved on the
    // live table).
    private static String tapeteColorForIndex(int idx) {
        switch (idx) {
            case 1:
                return "azul";
            case 2:
                return "rojo";
            case 3:
                return "negro";
            case 4:
                return "madera";
            default:
                return "verde";
        }
    }

    // Reads a boolean preference from PROPERTIES (all animation ones default to true).
    // Equivalent to reading the menu item's isSelected (see class note) and doesn't
    // depend on GameFrame existing.
    private static boolean prefBool(String key) {
        return prefBool(key, true);
    }

    private static boolean prefBool(String key, boolean def) {
        return Boolean.parseBoolean(Helpers.PROPERTIES.getProperty(key, String.valueOf(def)));
    }

    // Like persist, but for SPINNERS: holding the arrow down fires a change on repeat,
    // and writing the file on every one is bursty I/O on the EDT. The value is recorded
    // immediately (what revert and isDirty read); the file write is coalesced. Any other
    // immediate save in the dialog (SAVE, restore, cancel) flushes it anyway.
    private static void persistDeferred(String key, String value) {
        Helpers.PROPERTIES.setProperty(key, value);
        Helpers.savePropertiesFileDeferred();
    }

    // Persists a preference (key -> value) with no live effect. Used by controls in
    // out-of-game mode.
    private static void persist(String key, String value) {
        Helpers.PROPERTIES.setProperty(key, value);
        Helpers.savePropertiesFile();
    }

    private JPanel titledColumn(String titleKey) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(Translator.translate(titleKey)));
        return p;
    }

    // Adds a left-aligned row + a constant vertical gap (12px, same spacing as the "Misc"
    // rows in the Game tab). Rows are naturalRow() (max height = preferred), so they
    // don't stretch to fill the column; the leftover is absorbed by the glue closing
    // each column.
    private void addLeft(JPanel column, JComponent comp) {
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        column.add(comp);
        column.add(Box.createVerticalStrut(Math.round(12 * Helpers.DIALOG_ZOOM)));
    }

    // Closes a column with glue that pushes rows up and leaves the leftover space at the
    // bottom (like the Game tab's final addContainerGap), instead of spreading it between
    // rows. Only matters for the shorter column ("Screen and zoom"), stretched to match
    // on the right.
    private static void closeColumn(JPanel column) {
        column.add(Box.createVerticalGlue());
    }

    // Thin black rounded-corner grouping box for animation checkboxes with nested
    // sub-controls (Shuffle/Deal/Reveal): wraps the parent and its sub-controls so they
    // read as one group. Transparent (shows the Nimbus dialog background through, text
    // intact); only draws the outline. Natural height (doesn't stretch in the BoxLayout Y).
    private JPanel groupBox() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(0, 0, 0, 150));
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }

            // Caps the box to its content (doesn't span the whole column width): indented
            // under the master, it then reads as a sub-group rather than a full-width
            // strip. Live (getPreferredSize), not a value cached with the old font.
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM)));
        p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return p;
    }

    // Indents a component so it visually hangs off the column's master checkbox: shifts
    // it right by a fixed gap. Max height = preferred (doesn't stretch in the column's
    // BoxLayout Y); the trailing glue absorbs the leftover width on the right when the
    // component hugs its own content (the group boxes).
    private static JComponent indent(JComponent comp) {
        return indent(comp, 22);
    }

    // px = logical left gap (scaled with DIALOG_ZOOM). Group boxes use 22; STANDALONE
    // checkboxes use 28 (22 + the box's 6px left border) so their checkbox lines up with
    // the parent checkbox's INSIDE the boxes.
    private static JComponent indent(JComponent comp, int px) {
        JPanel wrap = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.X_AXIS));
        wrap.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrap.add(Box.createHorizontalStrut(Math.round(px * Helpers.DIALOG_ZOOM)));
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrap.add(comp);
        wrap.add(Box.createHorizontalGlue());
        return wrap;
    }

    // Adds a row (parent checkbox or a sub-control) to the group box, with a thin gap
    // between rows (tighter than addLeft's strut, so the group reads as compact).
    private void addToGroup(JPanel group, JComponent row) {
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if (group.getComponentCount() > 0) {
            group.add(Box.createVerticalStrut(Math.round(4 * Helpers.DIALOG_ZOOM)));
        }
        group.add(row);
    }

    // Row (FlowLayout) whose MAX height is its preferred height: in the column's
    // BoxLayout Y it won't stretch to fill the gap, so rows keep a constant spacing
    // (addLeft's strut) instead of spreading out.
    private static JPanel naturalRow() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
    }

    // Empty grid for sub-settings hanging off a parent checkbox (speed, effect, style...):
    // each row is added via addAlignedSubRow and ALL dropdowns end up aligned in a common
    // column, instead of landing at different x depending on their label's width. The
    // grid (GridBagLayout) measures widths LIVE, so it adapts to font/zoom changes.
    private JPanel subGrid() {
        // Max width = preferred (not just height): this keeps the box's BoxLayout from
        // stretching the grid; with weightx=0 on every cell, stretching it would center
        // the rows instead of hugging the left edge. Capped to its preferred size, it
        // stays left-aligned.
        JPanel grid = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        grid.setOpaque(false);
        grid.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return grid;
    }

    // Adds a row (icon label | dropdown) to a grid built with subGrid(). The sub-option
    // indent is the label's left inset; the label column measures the widest one, so
    // every row's dropdown starts at the SAME x. gridy = row index (0 is first); later
    // rows get a bit of extra space on top.
    private void addAlignedSubRow(JPanel grid, int gridy, String iconPath, JLabel label, JComponent control) {
        label.setIcon(icon(iconPath));
        int gap = Math.round(6 * Helpers.DIALOG_ZOOM);
        int top = gridy > 0 ? Math.round(4 * Helpers.DIALOG_ZOOM) : 0;
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.gridy = gridy;
        g.anchor = java.awt.GridBagConstraints.WEST;
        g.gridx = 0;
        g.insets = new java.awt.Insets(top, Math.round(18 * Helpers.DIALOG_ZOOM), 0, gap);
        grid.add(label, g);
        g.gridx = 1;
        // fill=HORIZONTAL: the dropdown takes the column's width (= the grid's widest),
        // so every dropdown in the group ends up the SAME width (cleaner).
        g.fill = java.awt.GridBagConstraints.HORIZONTAL;
        g.insets = new java.awt.Insets(top, 0, 0, 0);
        grid.add(control, g);
    }

    // Checkbox that MIRRORS an appearance toggle. In-game (menu != null) a click = a
    // click on the menu item (applies live + persists + reflects in the popup). Out of
    // game (menu == null) it runs the supplied persist-only action.
    // NOTE: the checkbox is NOT always in sync with the setting. Checking it while its
    // menu item is disabled (the zoom auto-fit disables its own while it's working) still
    // toggles it, but the delegated click is a no-op, so it ends up showing the opposite
    // of the real state. That's why the item's state is the SOURCE OF TRUTH, and why
    // "Restore defaults" realigns the checkbox on every path.
    private JComponent delegatingCheckbox(String iconPath, String i18nKey, boolean selected, JMenuItem menu, Runnable standalone, boolean defaultValue) {
        return delegatingCheckbox(iconPath, i18nKey, selected, menu, standalone, defaultValue, null);
    }

    private JComponent delegatingCheckbox(String iconPath, String i18nKey, boolean selected, JMenuItem menu, Runnable standalone, boolean defaultValue, String tooltipKey) {
        JCheckBox cb = new JCheckBox(Translator.translate(i18nKey), selected);
        cb.setEnabled(menu == null || menu.isEnabled());
        cb.addActionListener(e -> {
            if (menu != null) {
                menu.doClick();
            } else {
                standalone.run();
            }
        });
        // Restore defaults: whenever possible, a click follows the same path as the
        // user's (applies live + persists). All three branches (nothing to do, normal
        // click, and the fallback) leave the checkbox at the factory value, since it can
        // arrive here out of sync with the real setting (see delegatingCheckbox's note).
        reset_actions.add(() -> {
            // The source of truth is the menu item, which stays in lockstep with the flag;
            // the checkbox may have drifted out of sync (checking it while its item was
            // disabled applies nothing). Reading the checkbox here would let through
            // exactly the case the branch below exists to fix.
            boolean current = menu != null ? menu.isSelected() : cb.isSelected();

            if (current == defaultValue) {
                // Nothing to apply, but the checkbox is set right in case it had drifted.
                cb.setSelected(defaultValue);
                return;
            }

            if (cb.isEnabled() && (menu == null || menu.isEnabled())) {
                cb.doClick();
                // The click TOGGLES the checkbox, and if it had drifted from the real
                // state it now shows the opposite of the setting just applied (the user's
                // next click would undo it). No-op when it already matches, the normal case.
                cb.setSelected(defaultValue);
            } else {
                // A doClick on a DISABLED control does nothing, and the menu item can be
                // disabled at times: zoom auto-fit disables its own while its background
                // work runs. Without this fallback, that setting would escape "Restore
                // defaults" (or worse: the checkbox would change but the real setting
                // wouldn't). Same hazard revertLive already dodges when reverting
                // animations, and both of its mirrors need to stay in sync: the main
                // menu's item and the felt popup's. setSelected doesn't fire the listener,
                // so this doesn't re-enter.
                standalone.run();

                cb.setSelected(defaultValue);

                if (menu != null) {
                    menu.setSelected(defaultValue);

                    if (gf != null && menu == gf.getAuto_fit_zoom_menu() && Helpers.TapetePopupMenu.AUTO_ZOOM_MENU != null) {
                        Helpers.TapetePopupMenu.AUTO_ZOOM_MENU.setSelected(defaultValue);
                    }
                }
            }
        });
        // Icon on the left (same as the old menu item) for parity with the Game tab and
        // the menus this dialog replaces.
        JPanel row = naturalRow();
        JLabel iconLabel = new JLabel(icon(iconPath));
        row.add(iconLabel);
        row.add(cb);
        // Optional tooltip: set on the row and both its children so it appears over the
        // whole clickable area (icon + checkbox + text).
        if (tooltipKey != null) {
            Helpers.setTranslatedToolTip(row, tooltipKey);
            Helpers.setTranslatedToolTip(iconLabel, tooltipKey);
            Helpers.setTranslatedToolTip(cb, tooltipKey);
        }
        return row;
    }

    // Like delegatingCheckbox but for the animation toggles governed by the master: the
    // CHECKED state reflects the PREFERENCE (read from the menu item in-game, or from
    // PROPERTIES out of game), and the checkbox registers itself so the master can
    // enable/disable it. Starts disabled if the master is off. In-game (menu != null) it
    // delegates to the item; out of game it persists the preference and sets the
    // effective flag.
    private JComponent animCheckbox(String iconPath, String i18nKey, JMenuItem menu, String prefKey, Consumer<Boolean> effSetter) {
        return animCheckbox(iconPath, i18nKey, menu, prefKey, effSetter, true);
    }

    private JComponent animCheckbox(String iconPath, String i18nKey, JMenuItem menu, String prefKey, Consumer<Boolean> effSetter, boolean defaultPref) {
        boolean pref = (menu != null) ? menu.isSelected() : prefBool(prefKey, defaultPref);
        JCheckBox cb = new JCheckBox(Translator.translate(i18nKey), pref);
        // Animation group header (Shuffle, Deal, Reveal, Sort hand...): bold to
        // distinguish it from its sub-settings (speed, style, etc.), which use normal weight.
        cb.setFont(cb.getFont().deriveFont(java.awt.Font.BOLD));
        cb.setEnabled((menu == null || menu.isEnabled()) && GameFrame.ANIMACIONES);
        cb.addActionListener(e -> {
            if (menu != null) {
                menu.doClick();
            } else {
                boolean now = cb.isSelected();
                persist(prefKey, String.valueOf(now));
                effSetter.accept(now);
            }
        });
        anim_sub_cb.add(cb);
        anim_sub_menu.add(menu);
        // NO indent of its own: the parent checkbox hugs the box's edge (just the
        // FlowLayout gap), so its left margin matches the right one (symmetric box).
        // Standalone checkboxes get their indent from indent(); sub-controls, their own strut.
        JPanel row = naturalRow();
        row.add(new JLabel(icon(iconPath)));
        row.add(cb);
        return row;
    }

    private JPanel labeledRow(String iconPath, String labelKey, JComponent control) {
        JPanel row = naturalRow();
        JLabel label = new JLabel(Translator.translate(labelKey) + ":");
        label.setIcon(icon(iconPath));
        row.add(label);
        row.add(control);
        return row;
    }

    private static javax.swing.ImageIcon icon(String path) {
        return new javax.swing.ImageIcon(AppearanceSettingsPanel.class.getResource(path));
    }

    // Icon from outside /images/menu (those already come sized for these rows) scaled
    // down to the same height as them, so it doesn't throw off the checkbox row.
    private static javax.swing.ImageIcon scaledIcon(String path, int size) {
        return scaledIcon(path, size, size);
    }

    private static javax.swing.ImageIcon scaledIcon(String path, int width, int height) {
        try {
            return Helpers.scaleIcon(AppearanceSettingsPanel.class.getResource(path), width, height);
        } catch (java.net.MalformedURLException ex) {
            return null;
        }
    }

    // Fits an icon inside the given box WITHOUT distorting it, for artwork that isn't
    // square (the light switch is 256x120: squeezed into a 24px square it comes out
    // squashed to less than half its width).
    private static javax.swing.ImageIcon fitIcon(String path, int max_width, int max_height) {

        java.net.URL url = AppearanceSettingsPanel.class.getResource(path);

        if (url == null) {
            return null;
        }

        javax.swing.ImageIcon raw = new javax.swing.ImageIcon(url);

        if (raw.getIconWidth() <= 0 || raw.getIconHeight() <= 0) {
            return raw;
        }

        float scale = Math.min((float) max_width / raw.getIconWidth(), (float) max_height / raw.getIconHeight());

        return scaledIcon(path, Math.max(1, Math.round(raw.getIconWidth() * scale)), Math.max(1, Math.round(raw.getIconHeight() * scale)));
    }

    private int currentTapeteIndex() {
        String ct = GameFrame.COLOR_TAPETE;
        if (ct.startsWith("azul")) {
            return 1;
        }
        if (ct.startsWith("rojo")) {
            return 2;
        }
        if (ct.startsWith("negro")) {
            return 3;
        }
        if (ct.startsWith("madera")) {
            return 4;
        }
        return 0;
    }
}
