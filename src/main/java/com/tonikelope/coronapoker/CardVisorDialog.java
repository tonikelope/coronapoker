/*
 * Copyright (C) 2020 tonikelope
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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JViewport;

/**
 *
 * @author tonikelope
 */
public class CardVisorDialog extends javax.swing.JDialog {

    private final static String PALOS = "PCTD";
    private final static int CORNER = 100;
    private final static int WIDTH = 1100;
    private final HashMap<String, Integer> valores = new HashMap<>();
    private int carta = 0;

    /**
     * Creates new form CardVisor
     */
    public CardVisorDialog(java.awt.Frame parent, boolean modal, String valor, String palo, boolean buttons) {
        super(parent, modal);

        initComponents();

        this.setFocusable(modal);
        this.setFocusCycleRoot(modal);
        this.setAutoRequestFocus(modal);
        this.setFocusableWindowState(modal);

        setTitle(Init.WINDOW_TITLE + " - Visor de cartas");

        setPreferredSize(new Dimension(Math.min(Math.round(0.6f * parent.getWidth()), WIDTH), Math.round(0.75f * parent.getHeight())));

        valores.put("A", 1);
        valores.put("J", 11);
        valores.put("Q", 12);
        valores.put("K", 13);

        carta = CardVisorDialog.PALOS.indexOf(palo) * 13 + (valores.containsKey(valor) ? valores.get(valor) : Integer.valueOf(valor));

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        HandScrollListener scrollListener = new HandScrollListener(card);
        scroll_panel.getViewport().addMouseMotionListener(scrollListener);
        scroll_panel.getViewport().addMouseListener(scrollListener);

        showCard(carta);

        pack();

    }

    public CardVisorDialog(java.awt.Frame parent, boolean modal, int carta, boolean buttons) {
        super(parent, modal);

        initComponents();

        this.setFocusable(modal);
        this.setFocusCycleRoot(modal);
        this.setAutoRequestFocus(modal);
        this.setFocusableWindowState(modal);

        setTitle(Init.WINDOW_TITLE + " - Visor de cartas");

        setPreferredSize(new Dimension(Math.min(Math.round(0.6f * parent.getWidth()), WIDTH), Math.round(0.75f * parent.getHeight())));

        valores.put("A", 1);
        valores.put("J", 11);
        valores.put("Q", 12);
        valores.put("K", 13);

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        HandScrollListener scrollListener = new HandScrollListener(card);
        scroll_panel.getViewport().addMouseMotionListener(scrollListener);
        scroll_panel.getViewport().addMouseListener(scrollListener);

        showCard(carta);

        pack();

    }

    //Thanks -> https://stackoverflow.com/a/10245657
    public class HandScrollListener extends MouseAdapter {

        private final Cursor defCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        private final Cursor hndCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        private final Point pp = new Point();
        private final JLabel image;

        public HandScrollListener(JLabel image) {
            this.image = image;
        }

        public void mouseDragged(final MouseEvent e) {
            JViewport vport = (JViewport) e.getSource();
            Point cp = e.getPoint();
            Point vp = vport.getViewPosition();
            vp.translate(pp.x - cp.x, pp.y - cp.y);
            image.scrollRectToVisible(new Rectangle(vp, vport.getSize()));
            pp.setLocation(cp);
        }

        public void mousePressed(MouseEvent e) {
            image.setCursor(hndCursor);
            pp.setLocation(e.getPoint());
        }

        public void mouseReleased(MouseEvent e) {
            image.setCursor(defCursor);
            image.repaint();
        }
    }

    private void showCard(int carta) {

        BufferedImage im;
        ImageIcon icon;
        String c;

        switch (carta) {
            case 54:
                c = "joker.jpg";
                break;
            case 53:
                c = "trasera.jpg";
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
            this.card.setIcon(new ImageIcon(im));

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
        scroll_panel.setDoubleBuffered(true);
        scroll_panel.setFocusable(false);

        card.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        card.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card.setDoubleBuffered(true);
        card.setFocusable(false);
        scroll_panel.setViewportView(card);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 773, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 572, Short.MAX_VALUE))
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
