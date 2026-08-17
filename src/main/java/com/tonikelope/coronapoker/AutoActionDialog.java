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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Veto overlay for AUTO MODE: before an auto-action fires (the pre-armed AUTO
 * buttons), shows a countdown with a red Cancel button. Non-modal — the player
 * can still use the board/menu (click, right-click) while it runs. Resolution
 * is delivered once via callback on the EDT: on timeout the action runs; on
 * cancel (or if the turn resolves another way, or the table goes down) it does
 * not. {@code keep_waiting} lets the caller abort the countdown if the player
 * acts manually.
 *
 * @author tonikelope
 */
public class AutoActionDialog extends JPanel {

    private volatile boolean resolved = false;

    private final JProgressBar barra = new JProgressBar();

    private final Consumer<Boolean> on_resolve;

    // Anchors used by showOn() to position the overlay in table coordinates: center_over (the
    // local seat, for vertical centering) and width_ref (its action button row, for the left
    // column and width).
    private final Component center_over;
    private final Component width_ref;

    // One-shot resolution. cancelled=true -> do not execute (cancel/abort); cancelled=false ->
    // timed out -> execute. Closes the overlay and invokes the callback on the EDT.
    private synchronized void resolve(boolean cancelled) {
        if (resolved) {
            return;
        }
        resolved = true;
        Helpers.GUIRun(() -> {
            Helpers.resetBarra(barra, 0);
            removeFromParent();
            if (on_resolve != null) {
                on_resolve.accept(cancelled);
            }
        });
    }

    /**
     * Closes the veto from outside (e.g. the table hiding because the player
     * left the game, or the hand ended): resolves as CANCELLED — the
     * auto-action does not run — and closes the overlay. Idempotent, so it's
     * harmless even if the countdown already resolved on its own.
     */
    public void cancel() {
        resolve(true);
    }

    /**
     * Accepts from outside (SPACE keyboard shortcut): runs the auto-action
     * immediately, as if the countdown had expired. Idempotent, harmless if
     * already resolved.
     */
    public void accept() {
        resolve(false);
    }

    /**
     * Builds the veto overlay; it is not shown until
     * {@link #showOn(TablePanel)} is called.
     *
     * @param center_over local seat used to vertically center the overlay in
     * showOn()
     * @param width_ref action button row used for the overlay's column and
     * width in showOn()
     * @param seconds countdown length in seconds
     * @param action_text optional action label shown under the title; may be
     * null/empty
     * @param keep_waiting polled once per second; returning false aborts the
     * countdown
     * @param on_resolve callback run on the EDT with the resolution (true =
     * cancelled, false = executed)
     */
    public AutoActionDialog(Component center_over, Component width_ref, int seconds, String action_text, BooleanSupplier keep_waiting, Consumer<Boolean> on_resolve) {

        super();

        this.on_resolve = on_resolve;
        this.center_over = center_over;
        this.width_ref = width_ref;

        // The overlay is the panel itself (white, orange border), mounted on top of the table.
        setOpaque(true);
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(7, 20, 7, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("MODO AUTO");
        title.putClientProperty("i18n.key", "modo_auto.titulo");
        title.setFont(new Font("Dialog", Font.BOLD, 30));
        title.setForeground(new Color(255, 102, 0));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFocusable(false);
        add(title, gbc);

        JLabel action = null;

        if (action_text != null && !action_text.isEmpty()) {
            gbc.gridy++;
            action = new JLabel(action_text);
            action.setFont(new Font("Dialog", Font.BOLD, 22));
            action.setForeground(Color.BLACK);
            action.setHorizontalAlignment(SwingConstants.CENTER);
            action.setFocusable(false);
            add(action, gbc);
        }

        gbc.gridy++;
        barra.setPreferredSize(new Dimension(Math.round(275 * Helpers.DIALOG_ZOOM), Math.round(26 * Helpers.DIALOG_ZOOM)));
        add(barra, gbc);

        gbc.gridy++;
        JButton cancel = new JButton(Translator.translate("ui.cancelar_2"));
        cancel.putClientProperty("i18n.key", "ui.cancelar_2");
        cancel.setBackground(new Color(200, 0, 0));
        cancel.setForeground(Color.WHITE);
        cancel.setFont(new Font("Dialog", Font.BOLD, 16));
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.setFocusable(false);
        cancel.addActionListener((java.awt.event.ActionEvent e) -> resolve(true));
        add(cancel, gbc);

        // As a lightweight component it doesn't steal keyboard focus from the table; the
        // Cancel button still responds to the mouse. Scaled via DIALOG_ZOOM like a real dialog.
        Helpers.applyDialogZoom(this);
        Helpers.translateComponents(this, false);

        // Fit the labels to the action row's width (width_ref) BEFORE the layout computes the
        // height, so the height comes out right with no vertical slack. The final position is
        // computed later, in showOn(), in table coordinates.
        // The AUTO MODE icon sits to the left of the title, scaled to the title's line height.
        // Its width is reserved during font fitting so icon + text still fit the action row
        // without widening the overlay.
        java.awt.Image auto_icon = new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png")).getImage();
        title.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));

        if (center_over != null && center_over.isShowing() && width_ref != null && width_ref.isShowing()) {
            // Usable width = action row width - border (10px each side) - insets (20px each
            // side). fitFontToWidth only shrinks the font if the text doesn't fit.
            int avail = width_ref.getWidth() - 2 * 10 - 2 * 20;
            // Icon reservation uses the pre-fit font size; the final icon is never larger, so
            // this is conservative.
            int reserved = title.getFont().getSize() + title.getIconTextGap();
            title.setFont(Helpers.fitFontToWidth(title, title.getText(), title.getFont(), Math.max(20, avail - reserved), 14));
            if (action != null) {
                action.setFont(Helpers.fitFontToWidth(action, action.getText(), action.getFont(), avail, 12));
            }
        }

        // Square icon sized to the title's final font height, placed to its left.
        int auto_px = Math.max(1, title.getFont().getSize());
        title.setIcon(new javax.swing.ImageIcon(auto_icon.getScaledInstance(auto_px, auto_px, java.awt.Image.SCALE_SMOOTH)));

        // Background countdown. Resolves via callback: timeout -> execute; table torn down or
        // keep_waiting false (player acted manually) -> abort.
        Helpers.threadRun(() -> {

            Helpers.GUIRun(() -> Helpers.smoothCountdown(barra, seconds));

            int t = seconds;

            while (t > 0 && !resolved) {

                Helpers.pausar(1000);

                if (resolved) {
                    return;
                }

                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()
                        || (keep_waiting != null && !keep_waiting.getAsBoolean())) {
                    resolve(true);
                    return;
                }

                if (!GameFrame.getInstance().isTimba_pausada()) {
                    --t;
                }
            }

            if (!resolved) {
                resolve(false);
            }
        });
    }

    /**
     * Mounts the veto as an overlay on the table (a {@link JLayeredPane}),
     * anchored to the local seat's height and to the action row's column/width,
     * in table coordinates. Replaces the old JDialog's screen-coordinate
     * setLocation, which some Linux window managers ignored (the window would
     * land at 0,0).
     *
     * @param tapete table panel to mount the overlay on; no-op if null
     */
    public void showOn(TablePanel tapete) {
        if (tapete == null) {
            return;
        }

        if (center_over != null && center_over.isShowing() && width_ref != null && width_ref.isShowing()) {
            int w = width_ref.getWidth();
            int h = getPreferredSize().height;
            Point ref = SwingUtilities.convertPoint(width_ref, 0, 0, tapete);
            Point cen = SwingUtilities.convertPoint(center_over, 0, 0, tapete);
            setBounds(ref.x, cen.y + (center_over.getHeight() - h) / 2, w, h);
        } else {
            Dimension pref = getPreferredSize();
            setBounds((tapete.getWidth() - pref.width) / 2, (tapete.getHeight() - pref.height) / 2, pref.width, pref.height);
        }

        // Layered above flying cards/chips (DRAG_LAYER): the veto is actionable and must
        // always stay visible over the table (replaces the old alwaysOnTop).
        tapete.add(this, Integer.valueOf(JLayeredPane.DRAG_LAYER + 100));
        tapete.revalidate();
        tapete.repaint();
    }

    // Removes the overlay from the table (replaces the old JDialog's dispose()). EDT-only.
    private void removeFromParent() {
        java.awt.Container p = getParent();
        if (p != null) {
            java.awt.Rectangle b = getBounds();
            p.remove(this);
            p.repaint(b.x, b.y, b.width, b.height);
        }
    }
}
