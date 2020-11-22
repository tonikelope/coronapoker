/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 *
 * @author tonikelope
 */
public class BalanceDialog extends javax.swing.JDialog {

    /**
     * Creates new form BalanceDialog
     */
    public BalanceDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        BalanceDialog tthis = this;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                initComponents();

                jScrollPane1.getVerticalScrollBar().setUnitIncrement(20);

                int width = 0;

                ArrayList<Object[]> ranking = new ArrayList<>();

                synchronized (Game.getInstance().getCrupier().getLock_contabilidad()) {

                    for (Map.Entry<String, Float[]> entry : Game.getInstance().getCrupier().getAuditor().entrySet()) {

                        JLabel label = new JLabel();

                        Float[] pasta = entry.getValue();

                        String ganancia_msg = "";

                        float ganancia = Helpers.clean1DFloat(Helpers.clean1DFloat(pasta[0]) - Helpers.clean1DFloat(pasta[1]));

                        if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                            ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                            label.setForeground(Color.RED);
                        } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                            ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                            label.setForeground(new Color(0, 150, 0));
                        } else {
                            ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                        }

                        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));

                        label.setText(entry.getKey() + " " + ganancia_msg);

                        label.setFont(label.getFont().deriveFont(Font.BOLD, Math.round(label.getFont().getSize() * 1.5f)));

                        if (entry.getKey().equals(Game.getInstance().getLocalPlayer().getNickname())) {
                            label.setBackground(Color.WHITE);
                            label.setOpaque(true);
                        }

                        Participant p = Game.getInstance().getParticipantes().get(entry.getKey());

                        if (p != null) {
                            if (p.getAvatar() != null) {
                                label.setIcon(new ImageIcon(new ImageIcon(p.getAvatar().getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                            } else if (Game.getInstance().isPartida_local() && p.isCpu()) {
                                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                            } else {
                                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                            }

                        } else {

                            if (Game.getInstance().getSala_espera().getAvatar() != null) {
                                label.setIcon(new ImageIcon(new ImageIcon(Game.getInstance().getSala_espera().getAvatar().getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                            } else {
                                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                            }

                        }

                        ranking.add(new Object[]{ganancia, label});

                        if (label.getWidth() > width) {
                            width = label.getWidth();
                        }
                    }

                    Collections.sort(ranking, new RankingComparator());

                    Collections.reverse(ranking);

                    for (Object[] o : ranking) {

                        jugadores.add((JLabel) o[1]);
                    }

                    pack();

                }

                setPreferredSize(new Dimension(jugadores.getWidth(), Math.round(0.7f * getParent().getHeight())));

                Helpers.updateFonts(tthis, Helpers.GUI_FONT, null);

                Helpers.translateComponents(tthis, false);

                pack();

            }
        });
    }

    static class RankingComparator implements Comparator<Object[]> {

        @Override
        public int compare(Object[] t, Object[] t1) {

            return Float.compare((float) t[0], (float) t1[0]);
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

        title = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jugadores = new javax.swing.JPanel();
        ok_button = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setUndecorated(true);

        title.setBackground(new java.awt.Color(102, 102, 102));
        title.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        title.setForeground(new java.awt.Color(255, 255, 255));
        title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        title.setText("LA TIMBA HA TERMINADO");
        title.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        title.setDoubleBuffered(true);
        title.setFocusable(false);
        title.setOpaque(true);

        jScrollPane1.setBorder(null);

        jugadores.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        jugadores.setFocusable(false);
        jugadores.setLayout(new java.awt.GridLayout(0, 1));
        jScrollPane1.setViewportView(jugadores);

        ok_button.setBackground(new java.awt.Color(0, 150, 0));
        ok_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        ok_button.setForeground(new java.awt.Color(255, 255, 255));
        ok_button.setText("OK");
        ok_button.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        ok_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ok_button.setDoubleBuffered(true);
        ok_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ok_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(title, javax.swing.GroupLayout.DEFAULT_SIZE, 411, Short.MAX_VALUE)
            .addComponent(jScrollPane1)
            .addComponent(ok_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(title)
                .addGap(0, 0, 0)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 249, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(ok_button)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void ok_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ok_buttonActionPerformed
        // TODO add your handling code here:
        dispose();
    }//GEN-LAST:event_ok_buttonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel jugadores;
    private javax.swing.JButton ok_button;
    private javax.swing.JLabel title;
    // End of variables declaration//GEN-END:variables
}
