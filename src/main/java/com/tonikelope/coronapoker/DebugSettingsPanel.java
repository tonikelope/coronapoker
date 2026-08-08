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
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * "Debug" tab of the Settings dialog: a read-only console showing the same
 * {@code java.util.logging} dump (via {@link DebugLog}) that used to live in the Log dialog.
 *
 * Subscribes to {@link DebugLog} on construction and unsubscribes in {@link #cleanup()}
 * (called by SettingsDialog on close, so the static DebugLog listener doesn't keep this
 * discarded panel alive). Reuses the console look ({@code LOG_BG}/{@code LOG_FONT}) and the
 * sticky autoscroll ({@code BottomFollower}) from {@link GameLogDialog}.
 *
 * @author tonikelope
 */
public class DebugSettingsPanel extends JPanel {

    private final JTextArea debug_textarea;
    private final GameLogDialog.BottomFollower follow;
    private final Consumer<String> listener;

    public DebugSettingsPanel() {
        super(new BorderLayout());

        debug_textarea = new JTextArea();
        debug_textarea.setEditable(false);
        debug_textarea.setBackground(GameLogDialog.LOG_BG);
        debug_textarea.setForeground(new Color(220, 220, 220));
        debug_textarea.setFont(GameLogDialog.LOG_FONT.deriveFont(GameLogDialog.LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM));
        debug_textarea.setLineWrap(true);
        debug_textarea.setWrapStyleWord(false);
        Helpers.JTextFieldRegularPopupMenu.addTo(debug_textarea);

        JScrollPane debug_scroll = new JScrollPane(debug_textarea);
        debug_scroll.setBorder(BorderFactory.createEmptyBorder());
        debug_scroll.getVerticalScrollBar().setUnitIncrement(16);
        // Bounded preferred size: the Settings dialog packs to its content, and a JTextArea
        // with many lines would report a huge preferred size that would blow up the dialog
        // height. With a modest fixed preferred size the content scrolls internally instead,
        // and the other tabs drive the final dialog size.
        debug_scroll.setPreferredSize(new Dimension(Math.round(620 * Helpers.DIALOG_ZOOM), Math.round(380 * Helpers.DIALOG_ZOOM)));
        add(debug_scroll, BorderLayout.CENTER);

        follow = new GameLogDialog.BottomFollower(debug_scroll, debug_textarea);

        debug_textarea.setText(DebugLog.snapshot());
        debug_textarea.setCaretPosition(debug_textarea.getDocument().getLength());

        listener = (String record) -> Helpers.GUIRun(() -> {
            try {
                debug_textarea.append(record);
                follow.followIfNeeded();
            } catch (Throwable t) {
                // The text area may be mid-teardown while the dialog closes — ignore.
            }
        });
        DebugLog.subscribe(listener);
    }

    /**
     * Restores the monospace console font after SettingsDialog's {@code setUniformFont} pass,
     * which would otherwise overwrite it with {@code GUI_FONT}. Call this after that pass.
     */
    public void reapplyConsoleFont() {
        debug_textarea.setFont(GameLogDialog.LOG_FONT.deriveFont(GameLogDialog.LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM));
    }

    /**
     * Jumps to the bottom of the console. Called when the dialog opens, to show the most
     * recent log lines.
     */
    public void snapToBottom() {
        follow.snapToBottom();
    }

    /**
     * Unsubscribes from {@link DebugLog} when the dialog closes. Idempotent.
     */
    public void cleanup() {
        DebugLog.unsubscribe(listener);
    }
}
