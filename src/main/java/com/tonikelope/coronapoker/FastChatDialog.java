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
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import static javax.swing.BorderFactory.createCompoundBorder;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;

/**
 *
 * @author tonikelope
 */
public final class FastChatDialog extends JDialog {

    private volatile static ArrayList<String[]> HISTORIAL = new ArrayList<>();
    private volatile static int HISTORIAL_INDEX = 0;
    private volatile boolean focusing = false;
    private volatile String current_message = null;
    private volatile boolean auto_close;

    public boolean isAuto_close() {
        return auto_close;
    }

    /**
     * Creates new form FastChatDialog
     */
    public FastChatDialog(java.awt.Frame parent, boolean modal, JTextField text, boolean auto_close_window) {
        super(parent, modal);

        initComponents();

        Helpers.translateComponents(this, false);

        this.auto_close = auto_close_window;

        setOpacity(0.8f);

        if (text != null) {
            chat_box.setText(text.getText());
            chat_box.setCaretPosition(text.getCaretPosition());
        }

        auto_close_checkbox.setSelected(auto_close_window);

        history_chat.setBorder(createCompoundBorder(history_chat.getBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        pack();

        //Helpers.setScaledIconLabel(icono, getClass().getResource("/images/chat.png"), chat_box.getHeight(), chat_box.getHeight());
        chat_panel.setSize((int) Math.round(GameFrame.getInstance().getWidth() * 0.3f), GameFrame.getInstance().getLocalPlayer().getHeight());

        chat_panel.setPreferredSize(chat_panel.getSize());

        setSize(chat_panel.getSize());

        setPreferredSize(getSize());

        refreshChatHistory();

        refreshColors();

        pack();

    }

    public void refreshChatHistory() {
        Helpers.GUIRun(() -> {
            history_chat.setText(GameFrame.getInstance().getSala_espera().getChat_text().toString().trim());
            chat_panel.repaint();
        });
    }

    public void refreshColors() {

        Helpers.GUIRun(() -> {
            if (chat_box.getText().length() <= Audio.MAX_TTS_LENGTH) {

                if (GameFrame.getInstance().getCapa_brillo().getBrightness() > 0f) {
                    chat_panel.setBackground(Color.DARK_GRAY);
                    chat_box.setBackground(Color.DARK_GRAY);
                    chat_box.setForeground(Color.WHITE);
                    history_chat.setBackground(Color.DARK_GRAY);
                    history_chat.setForeground(Color.WHITE);
                } else {
                    chat_panel.setBackground(Color.BLACK);
                    chat_box.setBackground(Color.BLACK);
                    chat_box.setForeground(Color.WHITE);
                    history_chat.setBackground(Color.BLACK);
                    history_chat.setForeground(Color.WHITE);
                }

            } else {

                chat_box.setForeground(Color.RED);
            }

            chat_panel.repaint();
        });
    }

    public JTextField getChat_box() {
        return chat_box;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        chat_panel = new javax.swing.JPanel();
        chat_box = new javax.swing.JTextField();
        history_scroll_panel = new javax.swing.JScrollPane();
        history_chat = new javax.swing.JTextArea();
        auto_close_checkbox = new javax.swing.JCheckBox();

        setUndecorated(true);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });

        chat_panel.setBackground(new java.awt.Color(255, 255, 255));

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
            public void keyReleased(java.awt.event.KeyEvent evt) {
                chat_boxKeyReleased(evt);
            }
        });

        history_scroll_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        history_scroll_panel.setFocusable(false);

        history_chat.setEditable(false);
        history_chat.setColumns(20);
        history_chat.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        history_chat.setLineWrap(true);
        history_chat.setRows(5);
        history_chat.setText("\n");
        history_chat.setBorder(null);
        history_chat.setDoubleBuffered(true);
        history_chat.setFocusable(false);
        history_scroll_panel.setViewportView(history_chat);

        auto_close_checkbox.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_close_checkbox.setForeground(new java.awt.Color(255, 255, 255));
        auto_close_checkbox.setText("Cerrar esta ventana después de enviar un mensaje.");
        auto_close_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        auto_close_checkbox.setDoubleBuffered(true);
        auto_close_checkbox.setFocusable(false);
        auto_close_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_close_checkboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout chat_panelLayout = new javax.swing.GroupLayout(chat_panel);
        chat_panel.setLayout(chat_panelLayout);
        chat_panelLayout.setHorizontalGroup(
            chat_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chat_box)
            .addComponent(auto_close_checkbox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 595, Short.MAX_VALUE)
            .addComponent(history_scroll_panel)
        );
        chat_panelLayout.setVerticalGroup(
            chat_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(chat_panelLayout.createSequentialGroup()
                .addComponent(history_scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 221, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(auto_close_checkbox)
                .addGap(0, 0, 0)
                .addComponent(chat_box, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(chat_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chat_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void chat_boxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_boxActionPerformed
        // TODO add your handling code here:

        String mensaje = chat_box.getText().trim();

        if (GameFrame.getInstance().getSala_espera().isChat_enabled() && mensaje.length() > 0) {

            if (HISTORIAL_INDEX < HISTORIAL.size()) {

                if (HISTORIAL.get(HISTORIAL_INDEX)[1] != null) {
                    HISTORIAL.set(HISTORIAL_INDEX, new String[]{HISTORIAL.get(HISTORIAL_INDEX)[1], null});
                }
            }

            HISTORIAL.add(new String[]{mensaje, null});

            HISTORIAL_INDEX = HISTORIAL.size();

            GameFrame.getInstance().getSala_espera().chatHTMLAppend(GameFrame.getInstance().getLocalPlayer().getNickname() + ":(" + Helpers.getLocalTimeString() + ") " + mensaje + "\n");

            GameFrame.getInstance().getSala_espera().enviarMensajeChat(GameFrame.getInstance().getLocalPlayer().getNickname(), mensaje);

            chat_box.setText("");

            refreshChatHistory();

            if (WaitingRoomFrame.CHAT_GAME_NOTIFICATIONS) {

                String tts_msg = GameFrame.getInstance().getSala_espera().cleanTTSChatMessage(mensaje);

                GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{GameFrame.getInstance().getLocalPlayer().getNickname(), tts_msg});

                synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {
                    GameFrame.NOTIFY_CHAT_QUEUE.notifyAll();
                }
            }

            GameFrame.getInstance().getSala_espera().setChat_enabled(false);

            Helpers.threadRun(() -> {
                Helpers.pausar(1000);
                Helpers.GUIRun(() -> {
                    GameFrame.getInstance().getSala_espera().setChat_enabled(true);
                });
            });

            if (auto_close) {
                setVisible(false);
                GameFrame.getInstance().getTapete().getFastbuttons().setEnabled(true);
                GameFrame.getInstance().getTapete().getFastbuttons().getMenu().setVisible(true);
            }
        }
    }//GEN-LAST:event_chat_boxActionPerformed

    private void chat_boxKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_chat_boxKeyPressed
        // TODO add your handling code here:

        if (evt.getKeyChar() == 'º') {
            if (!evt.isControlDown()) {
                setVisible(false);
                GameFrame.getInstance().getTapete().getFastbuttons().setEnabled(true);
                GameFrame.getInstance().getTapete().getFastbuttons().getMenu().setVisible(true);

            } else {
                try {
                    chat_box.getDocument().insertString(chat_box.getCaretPosition(), "º", null);
                } catch (BadLocationException ex) {
                    Logger.getLogger(FastChatDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else if (evt.getKeyCode() == KeyEvent.VK_UP) {
            HISTORIAL_INDEX--;

            if (HISTORIAL_INDEX < 0) {
                HISTORIAL_INDEX = 0;
            } else {
                if (current_message == null) {
                    current_message = chat_box.getText();
                }

                chat_box.setText(HISTORIAL.get(HISTORIAL_INDEX)[0]);
            }

        } else if (evt.getKeyCode() == KeyEvent.VK_DOWN) {

            HISTORIAL_INDEX++;

            if (HISTORIAL_INDEX >= HISTORIAL.size()) {
                HISTORIAL_INDEX = HISTORIAL.size();
                if (current_message != null) {
                    chat_box.setText(current_message);
                    current_message = null;
                }

            } else {

                chat_box.setText(HISTORIAL.get(HISTORIAL_INDEX)[0]);

            }

        } else {

            if (chat_box.getText().length() <= Audio.MAX_TTS_LENGTH) {

                if (chat_box.getBackground() != Color.WHITE) {
                    refreshColors();
                }

            } else if (chat_box.getBackground() != Color.RED) {
                refreshColors();
            }
        }
    }//GEN-LAST:event_chat_boxKeyPressed

    private void chat_boxFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chat_boxFocusLost
        // TODO add your handling code here:
        if (this.isVisible() && !focusing) {

            this.focusing = true;

            Helpers.threadRun(() -> {
                while (focusing) {
                    Helpers.GUIRun(() -> {
                        if (isVisible() && !getChat_box().isFocusOwner()) {
                            getChat_box().requestFocus();
                        } else {
                            focusing = false;
                        }
                    });
                    if (focusing) {
                        Helpers.pausar(125);
                        Helpers.GUIRun(() -> {
                            if (!isVisible() || getChat_box().isFocusOwner()) {
                                focusing = false;
                            }
                        });
                    }
                }
            });
        }
    }//GEN-LAST:event_chat_boxFocusLost

    private void chat_boxFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chat_boxFocusGained
        // TODO add your handling code here:
        if (!this.isVisible()) {
            getChat_box().grabFocus();
        } else {
            refreshColors();
        }
    }//GEN-LAST:event_chat_boxFocusGained

    private void chat_boxKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_chat_boxKeyReleased
        // TODO add your handling code here:
        if (HISTORIAL_INDEX < HISTORIAL.size() && evt.getKeyCode() != KeyEvent.VK_ENTER && (evt.getKeyChar() != 'º' || evt.isControlDown()) && evt.getKeyCode() != KeyEvent.VK_UP && evt.getKeyCode() != KeyEvent.VK_DOWN && current_message != null) {

            if (HISTORIAL.get(HISTORIAL_INDEX)[1] == null) {
                HISTORIAL.set(HISTORIAL_INDEX, new String[]{chat_box.getText(), HISTORIAL.get(HISTORIAL_INDEX)[0]});
            } else {
                HISTORIAL.set(HISTORIAL_INDEX, new String[]{chat_box.getText(), HISTORIAL.get(HISTORIAL_INDEX)[1]});
            }
        }
    }//GEN-LAST:event_chat_boxKeyReleased

    private void auto_close_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_close_checkboxActionPerformed
        // TODO add your handling code here:
        auto_close = auto_close_checkbox.isSelected();
    }//GEN-LAST:event_auto_close_checkboxActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:
        GameFrame.getInstance().getTapete().getFastbuttons().setEnabled(false);
        GameFrame.getInstance().getTapete().getFastbuttons().getMenu().setVisible(false);
    }//GEN-LAST:event_formComponentShown

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox auto_close_checkbox;
    private javax.swing.JTextField chat_box;
    private javax.swing.JPanel chat_panel;
    private javax.swing.JTextArea history_chat;
    private javax.swing.JScrollPane history_scroll_panel;
    // End of variables declaration//GEN-END:variables
}
