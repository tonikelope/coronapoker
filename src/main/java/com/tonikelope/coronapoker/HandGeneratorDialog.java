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

import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author tonikelope
 */
public class HandGeneratorDialog extends javax.swing.JDialog {

    public final static String TITLE = "Generador de jugadas";
    public final static String[] PALOS = {"P", "C", "T", "D"};
    public final static String[] VALORES = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    private Card[] cartas = null;
    private int valor_jugada = 9;

    /**
     * Creates new form HandGenerator
     */
    public HandGeneratorDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        HandGeneratorDialog tthis = this;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
                Helpers.setTranslatedTitle(tthis, TITLE);
                Helpers.updateFonts(tthis, Helpers.GUI_FONT, null);
                Helpers.translateComponents(tthis, false);
                cartas = new Card[]{card1, card2, card3, card4, card5};
                pack();
            }
        });
    }

    public Card[] getCartas() {
        return cartas;
    }

    public void pintarJugada() {

        Helpers.threadRun(new Runnable() {
            public void run() {

                for (Card carta : cartas) {
                    carta.descargarCarta();
                }

                switch (valor_jugada) {

                    case 0:
                        cartaAlta(valor_jugada);
                        break;
                    case 1:
                        pareja(valor_jugada);
                        break;
                    case 2:
                        doblePareja(valor_jugada);
                        break;
                    case 3:
                        trio(valor_jugada);
                        break;
                    case 4:
                        escalera(valor_jugada);
                        break;
                    case 5:
                        color(valor_jugada);
                        break;
                    case 6:
                        full(valor_jugada);
                        break;
                    case 7:
                        poker(valor_jugada);
                        break;
                    case 8:
                        escaleraColor(valor_jugada);
                        break;
                    case 9:
                        escaleraColorReal(valor_jugada);
                        break;
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {

                        inferior_button.setEnabled(valor_jugada > 0);
                        superior_button.setEnabled(valor_jugada < 9);

                    }
                });

            }
        });
    }

    private void cartaAlta(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(1.302.540 / 2.598.960)");

            }
        });

        int total = Card.VALORES.length * Card.PALOS.length;

        ArrayList<Card> posible = new ArrayList<>();
        ArrayList<Integer> valores = new ArrayList<>();

        do {

            valores.clear();

            posible.clear();

            while (posible.size() < Crupier.CARTAS_MAX) {

                int s = Helpers.PRNG_GENERATOR.nextInt(total) + 1;

                if (!valores.contains(s)) {
                    valores.add(s);
                    Card carta = new Card();
                    carta.cargarCarta(s);
                    posible.add(carta);
                }
            }

        } while (Hand.hayPareja(posible) != null
                || Hand.hayDoblePareja(posible) != null
                || Hand.hayTrio(posible) != null
                || Hand.hayEscalera(posible) != null
                || Hand.hayColor(posible) != null
                || Hand.hayFull(posible) != null
                || Hand.hayPoker(posible) != null
                || Hand.hayEscaleraColor(posible) != null
                || Hand.hayEscaleraColorReal(posible) != null);

        int i = 0;

        for (Card carta : posible) {

            cartas[i].cargarCarta(carta.getValor(), carta.getPalo());
            cartas[i].destapar(false);
            i++;
        }

    }

    private void pareja(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(1.098.240 / 2.598.960)");

            }
        });

        ArrayList<Card> pareja1 = new ArrayList<>();

        int valor_pareja1 = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        for (int i = 0; i < PALOS.length; i++) {
            Card carta = new Card();
            carta.cargarCarta(VALORES[valor_pareja1], PALOS[i]);
            pareja1.add(carta);
        }

        Collections.shuffle(pareja1);

        pareja1 = new ArrayList<Card>(pareja1.subList(0, Crupier.CARTAS_PAREJA));

        for (int i = 0; i < Crupier.CARTAS_PAREJA; i++) {

            cartas[i].cargarCarta(pareja1.get(i).getValor(), pareja1.get(i).getPalo());
            cartas[i].destapar(false);
        }

    }

    private void doblePareja(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(123.552 / 2.598.960)");

            }
        });

        ArrayList<Card> pareja1 = new ArrayList<>();

        int valor_pareja1 = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        for (int i = 0; i < PALOS.length; i++) {
            Card carta = new Card();
            carta.cargarCarta(VALORES[valor_pareja1], PALOS[i]);
            pareja1.add(carta);
        }

        Collections.shuffle(pareja1);

        pareja1 = new ArrayList<Card>(pareja1.subList(0, Crupier.CARTAS_PAREJA));

        for (int i = 0; i < Crupier.CARTAS_PAREJA; i++) {

            cartas[i].cargarCarta(pareja1.get(i).getValor(), pareja1.get(i).getPalo());
            cartas[i].destapar(false);
        }

        ArrayList<Card> pareja2 = new ArrayList<>();

        int valor_pareja2 = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        while (valor_pareja2 == valor_pareja1) {
            valor_pareja1 = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);
        }

        for (int i = 0; i < PALOS.length; i++) {
            Card carta = new Card();
            carta.cargarCarta(VALORES[valor_pareja2], PALOS[i]);
            pareja2.add(carta);
        }

        Collections.shuffle(pareja2);

        pareja2 = new ArrayList<Card>(pareja2.subList(0, Crupier.CARTAS_PAREJA));

        for (int i = 0; i < Crupier.CARTAS_PAREJA; i++) {

            cartas[2 + i].cargarCarta(pareja2.get(i).getValor(), pareja2.get(i).getPalo());
            cartas[2 + i].destapar(false);
        }

    }

    private void trio(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(54.912 / 2.598.960)");

            }
        });

        ArrayList<Card> trio = new ArrayList<>();

        int valor_trio = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        for (int i = 0; i < PALOS.length; i++) {
            Card carta = new Card();
            carta.cargarCarta(VALORES[valor_trio], PALOS[i]);
            trio.add(carta);
        }

        Collections.shuffle(trio);

        trio = new ArrayList<Card>(trio.subList(0, Crupier.CARTAS_TRIO));

        for (int i = 0; i < Crupier.CARTAS_TRIO; i++) {

            cartas[i].cargarCarta(trio.get(i).getValor(), trio.get(i).getPalo());
            cartas[i].destapar(false);
        }

    }

    private void escalera(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(9180 / 2.598.960)");

            }
        });

        ArrayList<Card> escalera;

        do {
            escalera = new ArrayList<>();

            int valor = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - Crupier.CARTAS_ESCALERA - 1) + 1;

            for (int i = 0; i < Crupier.CARTAS_ESCALERA; i++) {

                Card carta = new Card();

                carta.cargarCarta(VALORES[valor + i], PALOS[Helpers.PRNG_GENERATOR.nextInt(PALOS.length)]);

                escalera.add(carta);
            }

        } while (Hand.hayEscaleraColor(escalera) != null);

        int i = 0;

        for (Card carta : escalera) {

            cartas[i].cargarCarta(carta.getValor(), carta.getPalo());
            cartas[i].destapar(false);
            i++;

        }

    }

    private void color(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(5108 / 2.598.960)");

            }
        });

        ArrayList<Card> color = new ArrayList<>();

        ArrayList<Integer> valores = new ArrayList<>();

        do {

            color.clear();

            valores.clear();

            int palo = Helpers.PRNG_GENERATOR.nextInt(PALOS.length);

            while (color.size() < Crupier.CARTAS_COLOR) {

                int i = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

                if (!valores.contains(i)) {
                    Card carta = new Card();

                    carta.cargarCarta(VALORES[i], PALOS[palo]);

                    color.add(carta);

                    valores.add(i);
                }
            }

            Collections.sort(color);

        } while (Hand.hayEscaleraColor(color) != null || Hand.hayEscaleraColorReal(color) != null);

        int i = 0;

        for (Card carta : color) {

            cartas[i].cargarCarta(carta.getValor(), carta.getPalo());
            cartas[i].destapar(false);
            i++;
        }

    }

    private void full(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(3744 / 2.598.960)");

            }
        });

        ArrayList<Card> trio = new ArrayList<>();

        int valor_trio = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        for (int i = 0; i < PALOS.length; i++) {
            Card carta = new Card();
            carta.cargarCarta(VALORES[valor_trio], PALOS[i]);
            trio.add(carta);
        }

        Collections.shuffle(trio);

        trio = new ArrayList<Card>(trio.subList(0, Crupier.CARTAS_TRIO));

        for (int i = 0; i < Crupier.CARTAS_TRIO; i++) {

            cartas[i].cargarCarta(trio.get(i).getValor(), trio.get(i).getPalo());
            cartas[i].destapar(false);
        }

        ArrayList<Card> pareja = new ArrayList<>();

        int valor_pareja = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        while (valor_pareja == valor_trio) {
            valor_pareja = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);
        }

        for (int i = 0; i < PALOS.length; i++) {
            Card carta = new Card();
            carta.cargarCarta(VALORES[valor_pareja], PALOS[i]);
            pareja.add(carta);
        }

        Collections.shuffle(pareja);

        pareja = new ArrayList<Card>(pareja.subList(0, Crupier.CARTAS_PAREJA));

        for (int i = 0; i < Crupier.CARTAS_PAREJA; i++) {

            cartas[3 + i].cargarCarta(pareja.get(i).getValor(), pareja.get(i).getPalo());
            cartas[3 + i].destapar(false);
        }

    }

    private void poker(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(624 / 2.598.960)");

            }
        });

        int palo = 0;

        int valor = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - 1);

        for (int i = 0; i < Crupier.CARTAS_POKER; i++, palo++) {

            cartas[i].cargarCarta(VALORES[valor], PALOS[palo]);
            cartas[i].destapar(false);
        }

    }

    private void escaleraColor(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(36 / 2.598.960)");

            }
        });

        int palo = Helpers.PRNG_GENERATOR.nextInt(PALOS.length);

        int valor = Helpers.PRNG_GENERATOR.nextInt(VALORES.length - Crupier.CARTAS_ESCALERA - 1) + 1;

        for (int i = 0; i < Crupier.CARTAS_ESCALERA; i++, valor++) {

            cartas[i].cargarCarta(VALORES[valor], PALOS[palo]);
            cartas[i].destapar(false);
        }

    }

    private void escaleraColorReal(int v) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                nombre_jugada.setText(Hand.NOMBRES_JUGADAS[v]);

                probability.setText("(4 / 2.598.960)");

            }
        });

        int palo = Helpers.PRNG_GENERATOR.nextInt(PALOS.length);

        int valor = 9;

        for (int i = 0; i < Crupier.CARTAS_ESCALERA; i++) {

            cartas[i].cargarCarta(VALORES[valor + i], PALOS[palo]);
            cartas[i].destapar(false);
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

        superior_button = new javax.swing.JButton();
        inferior_button = new javax.swing.JButton();
        nombre_jugada = new javax.swing.JLabel();
        probability = new javax.swing.JLabel();
        cartas_panel = new javax.swing.JPanel();
        card4 = new com.tonikelope.coronapoker.Card();
        card2 = new com.tonikelope.coronapoker.Card();
        card5 = new com.tonikelope.coronapoker.Card();
        card1 = new com.tonikelope.coronapoker.Card();
        card3 = new com.tonikelope.coronapoker.Card();

        setResizable(false);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });

        superior_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        superior_button.setText("Jugada superior");
        superior_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        superior_button.setDoubleBuffered(true);
        superior_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                superior_buttonActionPerformed(evt);
            }
        });

        inferior_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        inferior_button.setText("Jugada inferior");
        inferior_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        inferior_button.setDoubleBuffered(true);
        inferior_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inferior_buttonActionPerformed(evt);
            }
        });

        nombre_jugada.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        nombre_jugada.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        nombre_jugada.setText("Escalera de color real");
        nombre_jugada.setDoubleBuffered(true);

        probability.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        probability.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        probability.setText("(4 de 2.598.960) ");
        probability.setDoubleBuffered(true);

        cartas_panel.setOpaque(false);

        javax.swing.GroupLayout cartas_panelLayout = new javax.swing.GroupLayout(cartas_panel);
        cartas_panel.setLayout(cartas_panelLayout);
        cartas_panelLayout.setHorizontalGroup(
            cartas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cartas_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(card1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(card2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(card3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(card4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(card5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );
        cartas_panelLayout.setVerticalGroup(
            cartas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cartas_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(cartas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(card5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(cartas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(card2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(card1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(card3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(card4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nombre_jugada, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(probability, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(cartas_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(superior_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(inferior_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(nombre_jugada)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(probability)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(superior_button)
                .addGap(18, 18, 18)
                .addComponent(cartas_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(inferior_button)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void inferior_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inferior_buttonActionPerformed
        // TODO add your handling code here:

        if (this.valor_jugada > 0) {
            this.valor_jugada--;

        }

        pintarJugada();

    }//GEN-LAST:event_inferior_buttonActionPerformed

    private void superior_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_superior_buttonActionPerformed
        // TODO add your handling code here:

        if (this.valor_jugada <= 10) {
            this.valor_jugada++;

        }

        pintarJugada();

    }//GEN-LAST:event_superior_buttonActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:
        valor_jugada = 9;
        pintarJugada();
    }//GEN-LAST:event_formComponentShown

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.tonikelope.coronapoker.Card card1;
    private com.tonikelope.coronapoker.Card card2;
    private com.tonikelope.coronapoker.Card card3;
    private com.tonikelope.coronapoker.Card card4;
    private com.tonikelope.coronapoker.Card card5;
    private javax.swing.JPanel cartas_panel;
    private javax.swing.JButton inferior_button;
    private javax.swing.JLabel nombre_jugada;
    private javax.swing.JLabel probability;
    private javax.swing.JButton superior_button;
    // End of variables declaration//GEN-END:variables
}
