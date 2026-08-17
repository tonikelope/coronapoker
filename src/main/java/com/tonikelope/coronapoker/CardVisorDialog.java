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

import com.tonikelope.coronapoker.Helpers.HandScrollListener;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;

/**
 * Popup window that displays a single enlarged card image ("card visor").
 *
 * @author tonikelope
 */
public class CardVisorDialog extends javax.swing.JDialog {

    private final static String PALOS = "PCTD";
    private final static int CORNER = 100;

    // Currently open visors keyed by deck+card, so the same card from the same deck can't
    // be opened twice (bring the existing one to front instead); the same card from a
    // different deck is a different image, so the key includes the deck.
    private final static HashMap<String, CardVisorDialog> OPEN_VISORS = new HashMap<>();

    /**
     * Converts a rank+suit pair to the card index used by the visor (same
     * formula as the index-based constructor).
     *
     * @param valor rank ("A", "J", "Q", "K", or a numeric string)
     * @param palo suit letter, one of {@link #PALOS}
     * @return the visor card index
     */
    public static int cartaFrom(String valor, String palo) {
        int v;

        switch (valor) {
            case "A":
                v = 1;
                break;
            case "J":
                v = 11;
                break;
            case "Q":
                v = 12;
                break;
            case "K":
                v = 13;
                break;
            default:
                v = Integer.parseInt(valor);
        }

        return CardVisorDialog.PALOS.indexOf(palo) * 13 + v;
    }

    /**
     * Opens the visor for a card, or brings the existing one to front if a
     * visor is already open for that same card (no duplicates).
     *
     * @param parent owner frame, used to center the visor
     * @param carta visor card index (see {@link #cartaFrom})
     */
    public static void openOrFocus(java.awt.Frame parent, int carta) {

        if (GameFrame.visorSonidoOn()) {
            Audio.playWavResource("misc/card_visor.wav");
        }

        // Uses the currently active global deck (see showCard) as part of the key, so
        // the same card from a different deck isn't treated as a duplicate.
        final String key = GameFrame.BARAJA + "|" + carta;

        CardVisorDialog existing = OPEN_VISORS.get(key);

        if (existing != null && existing.isShowing()) {
            existing.toFront();
            return;
        }

        CardVisorDialog visor = new CardVisorDialog(parent, false, carta, false);

        OPEN_VISORS.put(key, visor);

        visor.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                OPEN_VISORS.remove(key, visor);
            }
        });

        visor.setLocationRelativeTo(parent);

        visor.setVisible(true);
    }

    public CardVisorDialog(java.awt.Frame parent, boolean modal, int carta, boolean buttons) {
        super(parent, modal);

        initComponents();

        this.setFocusable(modal);
        this.setFocusCycleRoot(modal);
        this.setAutoRequestFocus(modal);
        this.setFocusableWindowState(modal);

        Helpers.setTranslatedTitle(this, "ui.visor_de_cartas");

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        HandScrollListener scrollListener = new HandScrollListener(card, this);
        scroll_panel.getViewport().addMouseMotionListener(scrollListener);
        scroll_panel.getViewport().addMouseListener(scrollListener);

        // The visor is a non-focusable window (so it doesn't steal focus from the game),
        // so Windows never raises its Z-order on click; with several visors open, the
        // newest one covers the rest and they can't come forward on their own. Bring it
        // to front manually (without requesting focus) whenever the user interacts with
        // it: clicking the card body, and dragging it (see the componentMoved listener
        // below for native title-bar drags).
        java.awt.event.MouseAdapter bring_to_front = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                toFront();
            }
        };

        card.addMouseListener(bring_to_front);
        scroll_panel.getViewport().addMouseListener(bring_to_front);

        // Native title-bar dragging fires no Swing mouse events, only componentMoved.
        // toFront() is idempotent (a no-op once already in front), so calling it on
        // every move is harmless and doesn't interfere with the native drag.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                toFront();
            }
        });

        // Scale the card to fit the available area while keeping its aspect ratio.
        // scroll_panel's preferred size is DEFAULT_SIZE (see initComponents), so pack()
        // sizes the window to the icon plus decoration with no scrollbars up front; the
        // JScrollPane stays in case the card doesn't fit on a small screen.
        showCard(carta, Math.round(0.9f * parent.getWidth()), Math.round(0.85f * parent.getHeight()));

        pack();
    }

    private void showCard(int carta, int max_w, int max_h) {

        BufferedImage im;
        ImageIcon icon;
        String c;

        if (carta == 53) {

            // The card back is a GLOBAL setting (game's back/ or the mod folder); use its
            // hq/ version, same as face-up cards, so the visor shows it at max quality
            // (falls back to the normal version if the mod doesn't ship an hq one).
            im = Helpers.makeImageRoundedCorner(Card.traseraSourceIcon().getImage(), CORNER);

            int w = im.getWidth();
            int h = im.getHeight();
            double scale = Math.min(Math.min((double) max_w / w, (double) max_h / h), 1.0);

            if (scale < 1.0) {
                this.card.setIcon(new ImageIcon(im.getScaledInstance((int) Math.round(w * scale), (int) Math.round(h * scale), java.awt.Image.SCALE_SMOOTH)));
            } else {
                this.card.setIcon(new ImageIcon(im));
            }

        } else {

            switch (carta) {
                case 54:
                    c = "joker.jpg";
                    break;
                default:
                    c = Card.VALORES[((carta - 1) % 13)] + "_" + Card.PALOS[(int) ((float) (carta - 1) / 13)] + ".jpg";
                    break;
            }

            boolean baraja_mod = (boolean) ((Object[]) Card.BARAJAS.get(GameFrame.BARAJA))[1];

            if (baraja_mod && !Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + GameFrame.BARAJA + "/hq/" + c))) {
                Logger.getLogger(CardVisorDialog.class.getName()).log(Level.INFO, "No existe {0}", Helpers.getCurrentJarParentPath() + "/mod/decks/" + GameFrame.BARAJA + "/hq/" + c);
                this.setVisible(false);
            } else {
                icon = baraja_mod ? new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/decks/" + GameFrame.BARAJA + "/hq/" + c) : new ImageIcon(getClass().getResource("/images/decks/" + GameFrame.BARAJA + "/hq/" + c));
                im = Helpers.makeImageRoundedCorner(icon.getImage(), CORNER);

                int w = im.getWidth();
                int h = im.getHeight();

                // Only ever shrink (never upscale past natural size) to avoid degrading
                // quality; if the card already fits, show it at full size.
                double scale = Math.min(Math.min((double) max_w / w, (double) max_h / h), 1.0);

                if (scale < 1.0) {
                    this.card.setIcon(new ImageIcon(im.getScaledInstance((int) Math.round(w * scale), (int) Math.round(h * scale), java.awt.Image.SCALE_SMOOTH)));
                } else {
                    this.card.setIcon(new ImageIcon(im));
                }
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        scroll_panel = new javax.swing.JScrollPane();
        card = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Visor de cartas");
        setAutoRequestFocus(false);
        setFocusCycleRoot(false);
        setFocusable(false);
        setFocusableWindowState(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        scroll_panel.setBorder(null);
        scroll_panel.setFocusable(false);

        card.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        card.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card.setFocusable(false);
        scroll_panel.setViewportView(card);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // TODO add your handling code here:
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel card;
    private javax.swing.JScrollPane scroll_panel;
    // End of variables declaration//GEN-END:variables
}
