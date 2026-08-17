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

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * "Shortcuts" tab of the Settings dialog: lists the reassignable actions (from
 * {@link KeyboardShortcuts}) grouped by section, each section in its own
 * rounded {@link SettingsUI} card, split across TWO columns (balanced by row
 * count) so it isn't one long vertical block. Each action has a button showing
 * its current combination; clicking it enters capture mode ("Press the
 * combination...") and the next key combination becomes the new shortcut,
 * unless another action already uses it (then it's ignored). To CANCEL a
 * capture, just click outside; no key is used for cancel, so any key —
 * including ESC — can be assigned.
 *
 * Changes apply LIVE to the registry (transaction opened by the dialog) and
 * only persist on SAVE; Cancel reverts them. Capture sets
 * {@link KeyboardShortcuts#setCapturing} so the global dispatchers step aside
 * and the key doesn't trigger whatever shortcut it used to.
 *
 * Buttons render the combination with the "Dialog" font (see
 * {@link #applyKeyFont()}): the UI font (McLaren) lacks the arrow glyphs (↑ ↓ ←
 * →), which would otherwise render blank.
 *
 * @author tonikelope
 */
public class ShortcutsSettingsPanel extends JPanel {

    // Minimum gap between boxes in a column (grows elastically to align the bottoms).
    private static final int BOX_GAP = 10;

    // Capture button per action id, to refresh its text after a change or a reset.
    private final Map<String, JButton> buttons = new HashMap<>();

    // "Undo" button per action (resets THAT shortcut to its factory value). Enabled only when the
    // action is customized.
    private final Map<String, JButton> reset_buttons = new HashMap<>();

    // Label with each action's name, bolded when its shortcut is customized.
    private final Map<String, JLabel> action_labels = new HashMap<>();

    // Capture currently in progress (only one at a time). Everything is touched on the EDT.
    private KeyEventDispatcher capture_dispatcher = null;
    private AWTEventListener mouse_cancel_listener = null;
    private String capturing_id = null;

    public ShortcutsSettingsPanel() {
        super(new GridBagLayout());
        buildUI();
    }

    private void buildUI() {

        GridBagConstraints gbc = new GridBagConstraints();

        JLabel hint = new JLabel("<html>" + Translator.translate("shortcuts.pista_editar") + "</html>");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 12, 12, 12);
        add(hint, gbc);

        // Groups actions by section, preserving the catalog's order.
        LinkedHashMap<String, List<KeyboardShortcuts.Def>> by_section = new LinkedHashMap<>();
        for (KeyboardShortcuts.Def d : KeyboardShortcuts.defs()) {
            by_section.computeIfAbsent(d.section_key, k -> new ArrayList<>()).add(d);
        }

        // Distributes section boxes into two columns, adding each section to whichever column has
        // FEWER accumulated rows so far, to keep both columns similar in height.
        List<JPanel> left = new ArrayList<>();
        List<JPanel> right = new ArrayList<>();
        int left_rows = 0;
        int right_rows = 0;

        for (Map.Entry<String, List<KeyboardShortcuts.Def>> e : by_section.entrySet()) {
            // Weight ~ box height: rows plus an extra for the title and borders, so the two columns
            // end up similar in height (not just row count) and their bottoms line up.
            int weight = e.getValue().size() + 2;
            if (left_rows <= right_rows) {
                left.add(sectionBox(e.getKey(), e.getValue()));
                left_rows += weight;
            } else {
                right.add(sectionBox(e.getKey(), e.getValue()));
                right_rows += weight;
            }
        }

        // fill BOTH + weighty: both columns stretch to the same height (that of the taller one), so
        // the shorter column distributes its leftover space between its boxes and both bottoms align.
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.insets = new Insets(0, 10, 8, 5);
        add(column(left), gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 5, 8, 10);
        add(column(right), gbc);
    }

    // Card for one section: rounded titled card (SettingsUI.card, same look as the other tabs)
    // wrapping the section's rows (label + aligned button + reset + elastic filler). The row grid
    // is transparent so the white card shows through.
    private JPanel sectionBox(String section_key, List<KeyboardShortcuts.Def> defs) {

        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        int row = 0;

        for (KeyboardShortcuts.Def d : defs) {

            JLabel action = new JLabel(Translator.translate(d.label_key));
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(3, 10, 3, 14);
            box.add(action, gbc);
            action_labels.put(d.id, action);

            final String id = d.id;
            JButton button = new JButton(keyText(id));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            // Wider buttons (generous horizontal margin) so the combination has room to breathe.
            button.setMargin(new Insets(3, 24, 3, 24));
            button.addActionListener(e -> startCapture(id));
            buttons.put(id, button);

            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(3, 0, 3, 6);
            box.add(button, gbc);

            // Small button to reset THIS shortcut to its factory value. Enabled only when the
            // action is customized (see refreshButton).
            JButton reset = new JButton(new ImageIcon(getClass().getResource("/images/menu/undo.png")));
            reset.setCursor(new Cursor(Cursor.HAND_CURSOR));
            reset.setToolTipText(Translator.translate("shortcuts.restaurar_este"));
            reset.setMargin(new Insets(2, 4, 2, 4));
            reset.setFocusable(false);
            reset.addActionListener(e -> {
                KeyboardShortcuts.reset(id);
                refreshButton(id);
            });
            reset_buttons.put(id, reset);

            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(3, 0, 3, 8);
            box.add(reset, gbc);

            gbc.gridx = 3;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, 0, 0);
            box.add(Box.createHorizontalGlue(), gbc);

            row++;
        }

        box.setAlignmentX(LEFT_ALIGNMENT);
        JPanel card = SettingsUI.card(section_key);
        card.add(box);
        return card;
    }

    // Stacks a column's boxes with ELASTIC separators between them: at least BOX_GAP px, growing to
    // absorb any leftover space. Since both columns stretch to the same height (the taller one —
    // see buildUI's fill BOTH), the shorter column spreads its boxes apart until its last box lines
    // up at the bottom with the other column's. There's no filler after the last box, so it stays
    // pinned to the bottom.
    private static JPanel column(List<JPanel> boxes) {

        JPanel col = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;

        int row = 0;
        for (int i = 0; i < boxes.size(); i++) {

            if (i > 0) {
                gbc.gridy = row++;
                gbc.weighty = 1;
                gbc.fill = GridBagConstraints.VERTICAL;
                col.add(new Box.Filler(new Dimension(0, BOX_GAP), new Dimension(0, BOX_GAP),
                        new Dimension(0, Short.MAX_VALUE)), gbc);
            }

            gbc.gridy = row++;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTH;
            col.add(boxes.get(i), gbc);
        }

        return col;
    }

    // Current key combination text for an action ("ALT + P", "CTRL + ALT + ESC").
    private static String keyText(String id) {
        return String.join(" + ", KeyboardShortcuts.keyCapStrings(KeyboardShortcuts.get(id)));
    }

    private void refreshButton(String id) {

        boolean customized = KeyboardShortcuts.isCustomized(id);

        JButton b = buttons.get(id);
        if (b != null) {
            b.setText(keyText(id));
            // Always "Dialog" font (the UI font lacks arrow glyphs); BOLD if the user has
            // customized the combination (differs from the factory default).
            b.setFont(new Font("Dialog", customized ? Font.BOLD : Font.PLAIN, b.getFont().getSize()));
        }

        // The name label is also bolded when the shortcut is customized.
        JLabel label = action_labels.get(id);
        if (label != null) {
            label.setFont(label.getFont().deriveFont(customized ? Font.BOLD : Font.PLAIN));
        }

        // The "undo" button is visible ONLY when the shortcut is customized.
        JButton reset = reset_buttons.get(id);
        if (reset != null) {
            reset.setVisible(customized);
        }

        // Relayout when the undo button appears/disappears or the text width changes.
        revalidate();
        repaint();
    }

    /**
     * Restores this tab's fonts AFTER the dialog's font unification:
     * combination buttons in "Dialog" (the UI font lacks the arrow glyphs
     * ↑↓←→), bold if customized. The section titles are painted by
     * {@link SettingsUI#card} itself, so they're immune to the unification.
     */
    public void applyKeyFont() {

        for (String id : buttons.keySet()) {
            refreshButton(id);
        }

        repaint();
    }

    // Starts capturing an action: steps the global dispatchers aside and waits for the next
    // combination. If another capture was already in progress (another button), cancels it first.
    private void startCapture(final String id) {

        // If another button had a capture in progress, cancel it and restore its text (otherwise
        // that button would be stuck showing "Press the combination...").
        if (capture_dispatcher != null) {
            String prev = capturing_id;
            stopCapture();
            if (prev != null) {
                refreshButton(prev);
            }
        }

        capturing_id = id;

        JButton button = buttons.get(id);
        if (button != null) {
            button.setText(Translator.translate("shortcuts.pulsa_combinacion"));
        }

        KeyboardShortcuts.setCapturing(true);

        capture_dispatcher = (KeyEvent e) -> {

            if (e.getID() != KeyEvent.KEY_PRESSED) {
                // Also swallow the release/typed events of the capture's keys so they don't leak
                // to anyone else while it lasts.
                return true;
            }

            // Base-key-only actions (voice note): modifiers are ignored.
            KeyStroke ks = KeyboardShortcuts.fromKeyEvent(e, KeyboardShortcuts.isKeycodeOnly(id));

            if (ks == null) {
                // Lone modifier: keep waiting for the actual key.
                return true;
            }

            if (KeyboardShortcuts.isAssignable(ks, id)) {
                KeyboardShortcuts.set(id, ks);
                cancelToBinding();
            } else {
                // Already used by another action: ignored, with a brief notice on the button itself.
                flashAlreadyAssigned(id);
            }

            return true;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(capture_dispatcher);

        // Cancel = click outside (or anywhere). No key is used for cancel, so any key — including
        // ESC — can be assigned. The click that started the capture has already happened
        // (actionPerformed fires on release), so this listener only sees the NEXT click.
        mouse_cancel_listener = (AWTEvent ev) -> {
            if (ev.getID() == MouseEvent.MOUSE_PRESSED) {
                cancelToBinding();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(mouse_cancel_listener, AWTEvent.MOUSE_EVENT_MASK);
    }

    // Ends the capture and leaves the button showing the action's CURRENT combination (the new one
    // if it was assigned, or the previous one if cancelled).
    private void cancelToBinding() {
        String id = capturing_id;
        stopCapture();
        if (id != null) {
            refreshButton(id);
        }
    }

    // Brief "Already assigned" notice on the button, then reverts to its combination.
    private void flashAlreadyAssigned(final String id) {
        stopCapture();
        JButton b = buttons.get(id);
        if (b != null) {
            b.setText(Translator.translate("shortcuts.ya_asignado"));
            javax.swing.Timer t = new javax.swing.Timer(900, e -> refreshButton(id));
            t.setRepeats(false);
            t.start();
        }
    }

    // Removes the capture dispatcher and mouse listener, and re-enables the global shortcuts.
    // Idempotent.
    private void stopCapture() {
        if (capture_dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(capture_dispatcher);
            capture_dispatcher = null;
        }
        if (mouse_cancel_listener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouse_cancel_listener);
            mouse_cancel_listener = null;
        }
        KeyboardShortcuts.setCapturing(false);
        capturing_id = null;
    }

    /**
     * Cancels an in-progress capture (if any), restoring the button's current
     * combination. Called by the dialog when switching tabs.
     */
    public void cancelCapture() {
        if (capture_dispatcher != null) {
            cancelToBinding();
        }
    }

    /**
     * Restores ALL shortcuts to their factory values (live; persists on SAVE)
     * and refreshes the buttons. Invoked by the tab's "Restore defaults"
     * footer.
     */
    public void restoreDefaults() {
        stopCapture();
        KeyboardShortcuts.resetAll();
        for (String id : buttons.keySet()) {
            refreshButton(id);
        }
    }

    /**
     * Closes any pending capture (when the dialog closes). Does not revert
     * changes: that's handled by the registry transaction (commit on SAVE,
     * revert on Cancel).
     */
    public void cleanup() {
        stopCapture();
    }
}
