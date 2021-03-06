/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JTextField;

/**
 *
 * @author tonikelope
 */
public class FastChatDialog extends javax.swing.JDialog {

    private volatile boolean focusing = false;

    /**
     * Creates new form FastChatDialog
     */
    public FastChatDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        pack();

    }

    public JTextField getChat_box() {
        return chat_box;
    }

    public void showDialog(java.awt.Frame p) {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                chat_box.setPreferredSize(new Dimension((int) Math.round(p.getWidth() * 0.3f), chat_box.getHeight()));

                setPreferredSize(new Dimension((int) Math.round(p.getWidth() * 0.3f), chat_box.getHeight()));

                pack();

                setLocation(p.getX(), p.getY() + p.getHeight() - getHeight());

                setVisible(true);
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        chat_box = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setUndecorated(true);

        chat_box.setFont(new java.awt.Font("Dialog", 0, 24)); // NOI18N
        chat_box.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        chat_box.setDoubleBuffered(true);
        chat_box.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                chat_boxFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                chat_boxFocusLost(evt);
            }
        });
        chat_box.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_boxActionPerformed(evt);
            }
        });
        chat_box.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                chat_boxKeyPressed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chat_box, javax.swing.GroupLayout.DEFAULT_SIZE, 800, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chat_box)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void chat_boxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_boxActionPerformed
        // TODO add your handling code here:

        String mensaje = chat_box.getText().trim();

        if (GameFrame.getInstance().getSala_espera().isChat_enabled() && mensaje.length() > 0) {

            GameFrame.getInstance().getSala_espera().getChat().append("[" + GameFrame.getInstance().getLocalPlayer().getNickname() + "]: " + mensaje + "\n");

            GameFrame.getInstance().getSala_espera().enviarMensajeChat(GameFrame.getInstance().getLocalPlayer().getNickname(), mensaje);

            chat_box.setText("");

            setVisible(false);

            if (WaitingRoomFrame.isCHAT_GAME_NOTIFICATIONS()) {

                Helpers.TTS_CHAT_QUEUE.add(new Object[]{GameFrame.getInstance().getLocalPlayer().getNickname(), mensaje});

                synchronized (Helpers.TTS_CHAT_QUEUE) {
                    Helpers.TTS_CHAT_QUEUE.notifyAll();
                }
            }

            GameFrame.getInstance().getSala_espera().setChat_enabled(false);

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.pausar(1000);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            GameFrame.getInstance().getSala_espera().setChat_enabled(true);

                        }
                    });

                }
            });
        }
    }//GEN-LAST:event_chat_boxActionPerformed

    private void chat_boxKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_chat_boxKeyPressed
        // TODO add your handling code here:

        if (evt.getKeyChar() == 'º') {
            setVisible(false);
        } else {

            if (chat_box.getText().length() <= Helpers.MAX_TTS_LENGTH) {

                if (chat_box.getBackground() != Color.WHITE) {
                    chat_box.setBackground(Color.WHITE);
                }

            } else {
                if (chat_box.getBackground() != Color.YELLOW) {
                    chat_box.setBackground(Color.YELLOW);
                }
            }
        }
    }//GEN-LAST:event_chat_boxKeyPressed

    private void chat_boxFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chat_boxFocusLost
        // TODO add your handling code here:
        if (this.isVisible() && !focusing) {

            this.focusing = true;

            Helpers.threadRun(new Runnable() {
                public void run() {

                    while (focusing) {

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                if (GameFrame.getInstance().getFastchat_dialog().isVisible() && !GameFrame.getInstance().getFastchat_dialog().getChat_box().isFocusOwner()) {
                                    GameFrame.getInstance().getFastchat_dialog().getChat_box().requestFocus();
                                } else {
                                    focusing = false;
                                }
                            }
                        });

                        if (focusing) {

                            Helpers.pausar(250);

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    if (!GameFrame.getInstance().getFastchat_dialog().isVisible() || GameFrame.getInstance().getFastchat_dialog().getChat_box().isFocusOwner()) {
                                        focusing = false;
                                    }
                                }
                            });

                        }
                    }
                }
            });
        }
    }//GEN-LAST:event_chat_boxFocusLost

    private void chat_boxFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chat_boxFocusGained
        // TODO add your handling code here:
        if (!this.isVisible()) {
            GameFrame.getInstance().getFastchat_dialog().getChat_box().grabFocus();
        }
    }//GEN-LAST:event_chat_boxFocusGained

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField chat_box;
    // End of variables declaration//GEN-END:variables
}
