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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

/**
 * Unified settings dialog. Appearance, Audio, Shortcuts and Debug tabs are
 * always present; the Game tab (blinds + rules) is added only in-game (live,
 * GameSettingsPanel) or in the waiting room (pre-game config,
 * WaitingGameSettingsPanel), never both. Outside a game (launcher, no
 * GameFrame) the Game tab is omitted entirely.
 *
 * Appearance and Audio apply LIVE in-game (local preference) and only PERSIST
 * the preference outside of one (no table to preview against). Game (blinds +
 * rules) applies only on SAVE. For clients the Game tab is read-only (the host
 * owns the rules); Appearance and Audio remain editable (local).
 *
 * @author tonikelope
 */
public class SettingsDialog extends JDialog {

    private final AppearanceSettingsPanel appearance_panel;
    private final AudioSettingsPanel audio_panel;
    private final GameSettingsPanel game_panel;
    // Waiting-room "Game" tab (session config before it starts). Mutually exclusive with
    // game_panel: only one or the other depending on context (in-game vs waiting room), never both.
    private final WaitingGameSettingsPanel waiting_panel;
    // "Debug" tab: read-only java.util.logging console. Global (all contexts), no settings
    // to confirm/revert.
    private final DebugSettingsPanel debug_panel;
    // "Shortcuts" tab: rebinds global keyboard shortcuts. Edits the KeyboardShortcuts
    // registry transactionally, same as Appearance/Audio.
    private final ShortcutsSettingsPanel shortcuts_panel;
    // Tab pane and the index of the "Shortcuts" tab, so the dialog can be opened directly
    // on it (the "Customize" button in the shortcuts dialog).
    private final JTabbedPane tabs = new JTabbedPane();
    private int shortcuts_tab_index = -1;
    // Transactional dialog: true only if SAVE was pressed (then nothing is reverted).
    private boolean committed = false;

    // Currently open instance (only one modal at a time). Used by the auto-close on game
    // start (closeIfOpen) and by the waiting-room Game tab mirror refresh.
    private static volatile SettingsDialog INSTANCE;

    public static void open(java.awt.Frame parent) {
        Helpers.GUIRun(() -> {
            SettingsDialog dialog = new SettingsDialog(parent, true);
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    // Opens the dialog directly on the "Shortcuts" tab (used by the standalone shortcuts
    // dialog's "Customize" button).
    public static void openOnShortcuts(java.awt.Frame parent) {
        Helpers.GUIRun(() -> {
            SettingsDialog dialog = new SettingsDialog(parent, true);
            dialog.selectShortcutsTab();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    // Selects the "Shortcuts" tab, if present.
    public void selectShortcutsTab() {
        if (shortcuts_tab_index >= 0) {
            tabs.setSelectedIndex(shortcuts_tab_index);
        }
    }

    public SettingsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        // Three contexts depending on where the settings dialog is opened from:
        //  - IN-GAME (GameFrame exists): live Game tab (GameSettingsPanel).
        //  - WAITING ROOM (parent = WaitingRoomFrame, game not started yet): waiting-room
        //    Game tab (WaitingGameSettingsPanel, full pre-game config).
        //  - LAUNCHER (neither game nor waiting room): only Appearance and Audio (no Game tab).
        boolean in_game = GameFrame.getInstance() != null;
        boolean in_waiting = !in_game && (parent instanceof WaitingRoomFrame)
                && !((WaitingRoomFrame) parent).isPartida_empezada();
        boolean read_only_game = !in_game || !GameFrame.getInstance().isPartida_local();
        // In the waiting room: editable only for the HOST; client / non-server -> fully
        // read-only. When RECOVERING a session (paused to admit players), the host can
        // edit the "Game" settings (rules + think time) and bot difficulty, but the
        // economy (buy-in, rebuy, blinds, structure, ante, straddle) stays LOCKED to the
        // recovered session's values -> partial recover mode, not fully read-only.
        boolean read_only_wait = !in_waiting || !((WaitingRoomFrame) parent).isServer();
        boolean recover_wait = in_waiting && !read_only_wait && GameFrame.isRECOVER();

        setTitle(Translator.translate("settings.ajustes"));
        // DO_NOTHING: the close (X) button is handled by windowClosing (confirms before
        // discarding, same as the Cancel button).
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        appearance_panel = new AppearanceSettingsPanel();
        audio_panel = new AudioSettingsPanel();
        game_panel = in_game ? new GameSettingsPanel(read_only_game) : null;
        waiting_panel = in_waiting ? new WaitingGameSettingsPanel(read_only_wait, recover_wait) : null;
        debug_panel = new DebugSettingsPanel();
        shortcuts_panel = new ShortcutsSettingsPanel();
        // The Shortcuts tab edits the shortcuts registry transactionally: applies live,
        // SAVE persists and Cancel reverts (same as Appearance and Audio).
        KeyboardShortcuts.beginTransaction();

        // Each tab lives inside a JScrollPane (ScrollableTabPanel): tracks the viewport
        // width (no spurious horizontal bar) and fills the height when it fits, showing a
        // vertical bar only when content overflows. This lets the dialog shrink and scroll
        // at low resolutions instead of running off screen.
        // Appearance and Audio each have their own "Restore defaults" button in a fixed
        // footer (always visible, doesn't scroll away): each restores only its own tab,
        // with the same transactional semantics (applies live; SAVE keeps it, Cancel
        // reverts it).
        tabs.addTab(Translator.translate("settings.tab_apariencia"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")), tabWithRestore(appearance_panel, appearance_panel::restoreDefaults, Translator.translate("settings.tab_apariencia")));
        tabs.addTab(Translator.translate("settings.tab_audio"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/sound.png")), tabWithRestore(audio_panel, audio_panel::restoreDefaults, Translator.translate("settings.tab_audio")));
        // Keyboard shortcuts (global, all contexts): rebindable, with its own
        // restore-defaults footer.
        tabs.addTab(Translator.translate("settings.tab_atajos"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/keyboard.png")), tabWithRestore(shortcuts_panel, shortcuts_panel::restoreDefaults, Translator.translate("settings.tab_atajos")));
        shortcuts_tab_index = tabs.getTabCount() - 1;
        if (in_game) {
            tabs.addTab(Translator.translate("settings.tab_partida"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")), scrollableTab(game_panel));
        } else if (in_waiting) {
            tabs.addTab(Translator.translate("settings.tab_partida"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")), scrollableTab(waiting_panel));
        }
        // Debug console (java.util.logging). Global, added last. NOT wrapped in
        // scrollableTab: the console already has its own JScrollPane and must fill the tab.
        tabs.addTab(Translator.translate("settings.tab_debug"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/log.png")), debug_panel);

        // Switching tabs cancels any key-capture armed on the Shortcuts tab (otherwise it
        // would stay live outside it and swallow the next key press).
        tabs.addChangeListener(e -> shortcuts_panel.cancelCapture());

        // TRANSACTIONAL dialog: Appearance and Audio apply live as a preview, but SAVE is
        // what CONFIRMS them and also applies the pending display mode and the Game tab
        // (blinds + rules, only if you're the host: applyToGame no-ops for clients).
        // Cancel / close reverts EVERYTHING to the state at open (see windowClosed). SAVE
        // is always active: for a client it confirms their LOCAL appearance/audio settings.
        JButton save_button = new JButton(Translator.translate("ui.guardar"));
        save_button.setBackground(new java.awt.Color(0, 130, 0));
        save_button.setForeground(new java.awt.Color(255, 255, 255));
        save_button.addActionListener(e -> {
            committed = true;
            if (game_panel != null) {
                game_panel.applyToGame();
            }
            if (waiting_panel != null) {
                waiting_panel.applyToGame();
            }
            appearance_panel.applyPendingDisplayMode();
            appearance_panel.applyPendingDialogZoom();
            // Confirms (persists) the shortcut rebindings.
            KeyboardShortcuts.commit();
            dispose();
        });

        JButton cancel_button = new JButton(Translator.translate("ui.cancelar_2"));
        cancel_button.addActionListener(e -> cancelWithConfirm());

        JPanel right_buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right_buttons.add(save_button);
        right_buttons.add(cancel_button);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(right_buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM)));
        content.add(tabs, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelWithConfirm();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                // Scroll the Debug console to the bottom (most recent) when the dialog
                // opens. snapToBottom already defers the scroll (invokeLater), so it runs
                // after the viewport's layout.
                debug_panel.snapToBottom();
            }

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
                // If NOT saved (Cancel / close), revert the LIVE Appearance/Audio changes
                // to the state at open. Display mode and the Game tab only apply on SAVE
                // (not here). This also runs on the automatic close (dispose) when the
                // game starts: it discards without asking (windowClosing -and its
                // confirmation- doesn't fire on dispose()).
                if (!committed) {
                    appearance_panel.revert();
                    audio_panel.revert();
                    // Discards the shortcut rebindings (back to the state at open).
                    KeyboardShortcuts.revert();
                }
                // Closes the audio panel's key capture + persists the volume.
                audio_panel.cleanup();
                // Closes any pending key capture on the Shortcuts tab.
                shortcuts_panel.cleanup();
                // Releases the Debug console's subscription to DebugLog.
                debug_panel.cleanup();
                if (INSTANCE == SettingsDialog.this) {
                    INSTANCE = null;
                }
            }
        });

        // Fonts UNIFIED to the new-game dialog's size (16, keeping each control's
        // bold/plain style). The Appearance and Audio tabs used the default (smaller)
        // font and looked out of balance next to Game; this makes the whole dialog
        // consistent.
        Helpers.setUniformFont(content, Helpers.GUI_FONT, Math.round(16 * Helpers.DIALOG_ZOOM));

        Helpers.scaleIcons(content, Helpers.DIALOG_ZOOM);

        // setUniformFont doesn't reach TitledBorder titles.
        fixTitledBorderFonts(content, save_button.getFont());

        // Audio panel sizing fixups (row/panel maximums), after the unified font is applied.
        audio_panel.applyFontsAndSizing();

        // Restore the Debug console's monospace font (setUniformFont just overwrote it
        // with GUI_FONT). Done before pack().
        debug_panel.reapplyConsoleFont();

        // The Shortcuts tab's combo buttons use the "Dialog" font (the UI font, McLaren,
        // lacks the ↑↓←→ arrow glyphs); restored after setUniformFont.
        shortcuts_panel.applyKeyFont();

        // Action buttons slightly larger than the rest of the dialog.
        java.awt.Font buttons_font = Helpers.GUI_FONT.deriveFont(Font.BOLD, 18f * Helpers.DIALOG_ZOOM);
        save_button.setFont(buttons_font);
        cancel_button.setFont(buttons_font);

        pack();

        // Packed size stays at the content's minimum: Audio (two-column effects) is
        // already the widest tab and stretches the rest, so no extra widening is needed.
        // Cap to the screen's usable area (same low-resolution pattern as NewGameDialog:
        // getMaximumWindowBounds excludes the taskbar). The dialog stays as small as the
        // content allows; if it still overflows (very low resolution / high scaling),
        // it's clipped and each tab starts scrolling. SAVE/Cancel live in SOUTH, outside
        // the scroll, so they're always visible.
        capToScreen();

        // Only one modal at a time: register as the open instance (cleared by
        // windowClosed). Used by closeIfOpen (auto-close on start) and refreshWaitingMirror.
        INSTANCE = this;
    }

    // Clips the packed size to the screen's usable area (95%). Shrinks only.
    private void capToScreen() {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int max_w = Math.round(usable.width * 0.95f);
        int max_h = Math.round(usable.height * 0.95f);
        int w = getWidth();
        int h = getHeight();
        // If the HEIGHT needs clipping, a vertical scrollbar will appear; reserve its
        // width (~17px) so it doesn't also trigger a spurious horizontal bar by eating
        // into the width. If the screen can't fit that extra, the horizontal bar appears
        // and scrolls.
        if (h > max_h) {
            w += new javax.swing.JScrollBar(javax.swing.JScrollBar.VERTICAL).getPreferredSize().width + 2;
        }
        w = Math.min(w, max_w);
        h = Math.min(h, max_h);
        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);
        }
    }

    // Are there unconfirmed changes in any tab? (Appearance/Audio apply live; Game is
    // apply-on-save.) Used to ask for confirmation before discarding on cancel.
    private boolean isDirty() {
        return appearance_panel.isDirty() || audio_panel.isDirty() || KeyboardShortcuts.isDirty()
                || (game_panel != null && game_panel.isDirty())
                || (waiting_panel != null && waiting_panel.isDirty());
    }

    /**
     * Closes the currently open dialog, if any, WITHOUT asking about unsaved
     * changes. Used when a game starts on the client: once the session has
     * started, the waiting-room Game tab settings no longer apply. A direct
     * dispose() does not fire windowClosing (where the discard confirmation
     * lives), so this closes like Alt+F4 but without the "discard changes?"
     * prompt; unsaved changes are dropped. Idempotent.
     */
    public static void closeIfOpen() {
        Helpers.GUIRun(() -> {
            SettingsDialog d = INSTANCE;
            if (d != null && d.isDisplayable()) {
                d.dispose();
            }
        });
    }

    /**
     * Refreshes (live) the read-only waiting-room Game tab when a new host
     * config mirror arrives (GAMECONFIG). Must be called on the EDT.
     */
    public static void refreshWaitingMirror() {
        SettingsDialog d = INSTANCE;
        if (d != null && d.waiting_panel != null) {
            d.waiting_panel.refreshFromMirror();
        }
    }

    // Closes discarding changes; if there are unconfirmed changes, asks first. Used by
    // the Cancel button and the window's close (X).
    private void cancelWithConfirm() {
        if (!isDirty() || Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("settings.descartar_cambios")) == javax.swing.JOptionPane.YES_OPTION) {
            dispose();
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

    // Tab with its own fixed "Restore defaults" footer: content scrolls in the CENTER and
    // the button stays at the bottom, always visible. Pressing it restores only this tab
    // (applied live) and warns that SAVE is needed to keep it (the dialog is
    // transactional: Cancel reverts it). The button inherits the dialog's font/scale with
    // the rest of the content (setUniformFont / scaleIcons over 'content').
    private JPanel tabWithRestore(Component panel, Runnable restore, String section_name) {

        JButton restore_button = new JButton(Translator.translate("settings.restaurar_predeterminados_seccion", section_name));
        restore_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/undo.png")));
        restore_button.addActionListener(e -> {
            restore.run();
            Helpers.mostrarMensajeInformativo(this, Translator.translate("settings.predeterminados_restaurados_seccion", section_name));
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(restore_button);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(scrollableTab(panel), BorderLayout.CENTER);
        wrap.add(footer, BorderLayout.SOUTH);
        return wrap;
    }

    // Wraps a tab's content in a borderless JScrollPane with on-demand
    // vertical/horizontal bars and smooth mouse-wheel scrolling. The content (see
    // ScrollableTabPanel) FILLS the viewport while it fits and only scrolls when it doesn't.
    private static JScrollPane scrollableTab(Component panel) {
        JScrollPane sp = new JScrollPane(new ScrollableTabPanel(panel),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // Container that, inside a JScrollPane, FILLS the viewport while the content fits
    // (tracks its width/height: no spurious bars or dead background on wide screens) and
    // lets the axis that doesn't fit scroll when the dialog shrinks. Standard
    // "ScrollablePanel" pattern.
    private static final class ScrollableTabPanel extends JPanel implements Scrollable {

        ScrollableTabPanel(Component view) {
            super(new BorderLayout());
            add(view, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        // Tracks the viewport's width only if the content FITS; otherwise lets the
        // horizontal bar appear (when the user narrows the dialog a lot).
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getPreferredSize().width <= getParent().getWidth();
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getPreferredSize().height < getParent().getHeight();
        }
    }

}
