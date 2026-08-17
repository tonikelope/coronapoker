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
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Voluntary straddle: right after the deal, the UTG player decides blind (hole
 * cards face down) whether to post the straddle. Non-modal dialog overlaid on
 * their two hidden hole cards, spanning both, with the game's orange border, a
 * green POST button and a red NO button, plus a countdown. If the bar expires
 * (or the table closes) it resolves as NO. The result is delivered via callback
 * on the EDT: 1 = post, 0 = don't post. Single-shot resolution.
 *
 * Modeled on {@link AutoActionDialog} (same border/countdown/non-modal look),
 * but with two buttons anchored over two components (the two hole cards).
 *
 * @author tonikelope
 */
public class VoluntaryStraddleDialog extends JPanel {

    public static final int NO_STRADDLE = 0;
    public static final int POST_STRADDLE = 1;

    private volatile boolean resolved = false;

    private final JProgressBar barra = new JProgressBar();

    private final IntConsumer on_resolve;

    // Anchors: the local UTG's two hidden hole cards, kept to position the overlay over
    // the table in showOn (table coordinates, not screen coordinates).
    private final Component card1;
    private final Component card2;

    // Single-shot resolution. Closes the dialog and invokes the callback on the EDT
    // with the result (1 = post straddle, 0 = don't).
    private synchronized void resolve(int result) {
        if (resolved) {
            return;
        }
        resolved = true;
        Helpers.GUIRun(() -> {
            Helpers.resetBarra(barra, 0);
            removeFromParent();
            if (on_resolve != null) {
                on_resolve.accept(result);
            }
        });
    }

    /**
     * Closes the dialog externally (e.g. on receiving the host's canonical
     * result before the player answers, or when the table goes down): resolves
     * as NO. Idempotent — safe to call even if already resolved by the
     * countdown or the button.
     */
    public void cancel() {
        resolve(NO_STRADDLE);
    }

    /**
     * Accepts externally (SPACE keyboard shortcut): posts the straddle.
     * Idempotent, safe to call even if already resolved.
     */
    public void accept() {
        resolve(POST_STRADDLE);
    }

    /**
     * Builds the overlay; call {@link #showOn} to attach it to the table.
     *
     * @param card1 first hidden hole card, used to anchor/size the overlay
     * @param card2 second hidden hole card, used to anchor/size the overlay
     * @param seconds countdown length in seconds
     * @param amount_text optional straddle amount label, or {@code null}/empty
     * to omit it
     * @param on_resolve callback invoked on the EDT with {@link #POST_STRADDLE}
     * or {@link #NO_STRADDLE}
     */
    public VoluntaryStraddleDialog(Component card1, Component card2, int seconds, String amount_text, IntConsumer on_resolve) {

        super();

        this.on_resolve = on_resolve;
        this.card1 = card1;
        this.card2 = card2;

        // The overlay IS the panel itself (white, orange border): mounted directly onto the table.
        setOpaque(true);
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(6, 14, 6, 14);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel(Translator.translate("straddle.dialog_titulo"));
        title.putClientProperty("i18n.key", "straddle.dialog_titulo");
        title.setFont(new Font("Dialog", Font.BOLD, 28));
        title.setForeground(new Color(255, 102, 0));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFocusable(false);
        add(title, gbc);

        JLabel amount = null;

        if (amount_text != null && !amount_text.isEmpty()) {
            gbc.gridy++;
            amount = new JLabel(amount_text);
            amount.setFont(new Font("Dialog", Font.BOLD, 22));
            amount.setForeground(Color.BLACK);
            amount.setHorizontalAlignment(SwingConstants.CENTER);
            amount.setFocusable(false);
            add(amount, gbc);
        }

        gbc.gridy++;
        barra.setPreferredSize(new Dimension(Math.round(220 * Helpers.DIALOG_ZOOM), Math.round(22 * Helpers.DIALOG_ZOOM)));
        add(barra, gbc);

        // Buttons side by side: POST (green) on the left, NO (red) on the right.
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;

        JButton post = new JButton(Translator.translate("straddle.dialog_poner"));
        post.putClientProperty("i18n.key", "straddle.dialog_poner");
        post.setBackground(new Color(0, 130, 0));
        post.setForeground(Color.WHITE);
        post.setFont(new Font("Dialog", Font.BOLD, 16));
        post.setCursor(new Cursor(Cursor.HAND_CURSOR));
        post.setFocusable(false);
        post.addActionListener((java.awt.event.ActionEvent e) -> resolve(POST_STRADDLE));
        gbc.gridx = 0;
        add(post, gbc);

        JButton no = new JButton(Translator.translate("straddle.dialog_no"));
        no.putClientProperty("i18n.key", "straddle.dialog_no");
        no.setBackground(new Color(200, 0, 0));
        no.setForeground(Color.WHITE);
        no.setFont(new Font("Dialog", Font.BOLD, 16));
        no.setCursor(new Cursor(Cursor.HAND_CURSOR));
        no.setFocusable(false);
        no.addActionListener((java.awt.event.ActionEvent e) -> resolve(NO_STRADDLE));
        gbc.gridx = 1;
        add(no, gbc);

        // As a lightweight component it doesn't steal keyboard focus from the table; the
        // buttons still respond to mouse clicks. Scaled by DIALOG_ZOOM like a dialog.
        Helpers.applyDialogZoom(this);
        Helpers.translateComponents(this, false);

        // Labels are resized to fit the two hole cards' span BEFORE the layout computes height
        // (so the height comes out right); getLocationOnScreen works here since span is
        // translation-invariant — the final POSITION is computed later in showOn, in table
        // coordinates. The straddle chip icon sits left of the title, scaled to the title's
        // font height; its width is reserved during the font-fit so the icon+text still fits
        // the card span without widening the dialog.
        java.awt.Image straddle_chip = new javax.swing.ImageIcon(getClass().getResource("/images/straddle.png")).getImage();
        title.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));

        if (card1 != null && card1.isShowing() && card2 != null && card2.isShowing()) {
            Point a1 = card1.getLocationOnScreen();
            Point a2 = card2.getLocationOnScreen();
            int left = Math.min(a1.x, a2.x);
            int right = Math.max(a1.x + card1.getWidth(), a2.x + card2.getWidth());
            int span = right - left;

            // Usable width = span − border (10px each side) − insets (14px each side).
            int avail = span - 2 * 10 - 2 * 14;
            if (avail > 20) {
                // Icon reservation estimated from the pre-fit font; the final icon is tied to
                // the already-fitted font, which is never larger, so this stays conservative.
                int reserved = title.getFont().getSize() + title.getIconTextGap();
                title.setFont(Helpers.fitFontToWidth(title, title.getText(), title.getFont(), Math.max(20, avail - reserved), 12));
                if (amount != null) {
                    amount.setFont(Helpers.fitFontToWidth(amount, amount.getText(), amount.getFont(), avail, 11));
                }
            }
        }

        // Square chip sized to the title font's height (already final), placed to its left.
        int chip_px = Math.max(1, title.getFont().getSize());
        title.setIcon(new javax.swing.ImageIcon(straddle_chip.getScaledInstance(chip_px, chip_px, java.awt.Image.SCALE_SMOOTH)));

        // Background countdown thread. Resolves via callback: timeout or end of the game ->
        // NO straddle. The host (or the canonical result) can close it earlier via cancel()/resolve.
        Helpers.threadRun(() -> {

            Helpers.GUIRun(() -> Helpers.smoothCountdown(barra, seconds));

            int t = seconds;

            while (t > 0 && !resolved) {

                Helpers.pausar(1000);

                if (resolved) {
                    return;
                }

                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    resolve(NO_STRADDLE);
                    return;
                }

                if (!GameFrame.getInstance().isTimba_pausada()) {
                    --t;
                }
            }

            if (!resolved) {
                resolve(NO_STRADDLE);
            }
        });
    }

    /**
     * Mounts the overlay onto the table (a {@link JLayeredPane}), anchored to
     * span the UTG's two hidden hole cards and vertically centered on their
     * height, in table coordinates. Replaces the old JDialog's
     * screen-coordinate setLocation, which some Linux window managers ignored
     * (the window would land at 0,0).
     *
     * @param tapete table panel to attach the overlay to; no-op if {@code null}
     */
    public void showOn(TablePanel tapete) {
        if (tapete == null) {
            return;
        }

        if (card1 != null && card1.isShowing() && card2 != null && card2.isShowing()) {
            Point a1 = SwingUtilities.convertPoint(card1, 0, 0, tapete);
            Point a2 = SwingUtilities.convertPoint(card2, 0, 0, tapete);
            int left = Math.min(a1.x, a2.x);
            int right = Math.max(a1.x + card1.getWidth(), a2.x + card2.getWidth());
            int span = right - left;
            int top = Math.min(a1.y, a2.y);
            int cards_h = Math.max(card1.getHeight(), card2.getHeight());
            int h = getPreferredSize().height;
            int w = Math.max(span, getPreferredSize().width);
            setBounds(left, top + (cards_h - h) / 2, w, h);
        } else {
            Dimension pref = getPreferredSize();
            setBounds((tapete.getWidth() - pref.width) / 2, (tapete.getHeight() - pref.height) / 2, pref.width, pref.height);
        }

        // Layer above the flying cards/chips (DRAG_LAYER): the straddle prompt appears right
        // after the deal and must always stay visible over the table.
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
