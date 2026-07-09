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
 *
 * @author tonikelope
 */
public class CardVisorDialog extends javax.swing.JDialog {

    private final static String PALOS = "PCTD";
    private final static int CORNER = 100;

    // Visores actualmente abiertos indexados por baraja+carta, para no abrir dos veces
    // la misma carta DE LA MISMA BARAJA a la vez: si ya hay uno visible se trae al frente
    // en vez de duplicar. La misma carta de barajas distintas SÍ se puede abrir a la vez
    // (son imágenes diferentes), por eso la clave incluye la baraja.
    private final static HashMap<String, CardVisorDialog> OPEN_VISORS = new HashMap<>();

    /**
     * Traduce valor+palo al índice de carta que usa el visor (mismo cálculo que
     * el constructor por índice).
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
     * Abre el visor para una carta o, si ya hay uno abierto para esa misma carta,
     * lo trae al frente sin crear un duplicado.
     */
    public static void openOrFocus(java.awt.Frame parent, int carta) {

        Audio.playWavResource("misc/card_visor.wav");

        // La baraja usada al mostrar la carta es la global vigente en este instante
        // (ver showCard). Forma parte de la clave para que la misma carta de barajas
        // distintas se pueda tener abierta a la vez sin considerarse un duplicado.
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

        // Escalamos la carta para que quepa entera dentro del área disponible
        // (respetando su proporción). El scroll_panel usa DEFAULT_SIZE como
        // preferido (ver initComponents), así que pack() dimensiona la ventana
        // justo al icono más la decoración: de salida no sale ni scroll vertical
        // ni horizontal. El JScrollPane sigue ahí por si en pantallas pequeñas la
        // carta no cupiera.
        showCard(carta, Math.round(0.9f * parent.getWidth()), Math.round(0.85f * parent.getHeight()));

        pack();

    }

    private void showCard(int carta, int max_w, int max_h) {

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

            int w = im.getWidth();
            int h = im.getHeight();

            // Solo reducimos (nunca ampliamos por encima del tamaño natural) para no
            // degradar la calidad; si la carta ya cabe se muestra a tamaño real.
            double scale = Math.min(Math.min((double) max_w / w, (double) max_h / h), 1.0);

            if (scale < 1.0) {
                this.card.setIcon(new ImageIcon(im.getScaledInstance((int) Math.round(w * scale), (int) Math.round(h * scale), java.awt.Image.SCALE_SMOOTH)));
            } else {
                this.card.setIcon(new ImageIcon(im));
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
