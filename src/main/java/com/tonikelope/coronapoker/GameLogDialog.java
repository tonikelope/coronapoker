package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTextArea;

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
/**
 *
 * @author tonikelope
 */
public class GameLogDialog extends javax.swing.JDialog {

    private volatile String text = "";
    public final static String TITLE = "REGISTRO DE LA TIMBA";
    private volatile boolean auto_scroll = true;
    private volatile boolean utf8_cards = false;

    public boolean isAuto_scroll() {
        return auto_scroll;
    }

    public boolean isUtf8_cards() {
        return utf8_cards;
    }

    /**
     * Creates new form Registro
     */
    public GameLogDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        GameLogDialog tthis = this;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();

                Helpers.setTranslatedTitle(tthis, TITLE);

                utf8_cards_menu.setSelected(false);

                Helpers.JTextFieldRegularPopupMenu.addTo(textarea);

                Helpers.updateFonts(jMenuBar1, Helpers.GUI_FONT, null);

                Helpers.translateComponents(tthis, false);

                print("[CoronaPoker " + AboutDialog.VERSION + Translator.translate(" - REGISTRO DE LA TIMBA]"));
            }
        });
    }

    public GameLogDialog(java.awt.Frame parent, GameLogDialog dialog) {
        super(parent, dialog.isModal());

        initComponents();

        this.utf8_cards = dialog.isUtf8_cards();

        this.utf8_cards_menu.setSelected(this.utf8_cards);

        this.auto_scroll = dialog.isAuto_scroll();

        this.auto_scroll_menu.setSelected(this.auto_scroll);

        this.text = dialog.getText();

        this.textarea.setText(this.text);

        if (auto_scroll) {
            this.textarea.setCaretPosition(this.textarea.getText().length());
        }

        Helpers.JTextFieldRegularPopupMenu.addTo(this.textarea);

        Helpers.updateFonts(this.jMenuBar1, Helpers.GUI_FONT, null);

        setTitle(Init.WINDOW_TITLE + " - REGISTRO DE LA TIMBA");
    }

    public JTextArea getTextArea() {
        return textarea;
    }

    public String getText() {
        return text;
    }

    public synchronized void actualizarCartasPerdedores(HashMap<Player, Hand> perdedores) {

        if (perdedores != null && !perdedores.isEmpty()) {
            for (Map.Entry<Player, Hand> entry : perdedores.entrySet()) {

                Player perdedor = entry.getKey();

                Hand jugada = entry.getValue();

                if (!"".equals(perdedor.getPlayingCard1().getValor()) && ((perdedor != Game.getInstance().getLocalPlayer() && !perdedor.getPlayingCard1().isTapada()) || (perdedor == Game.getInstance().getLocalPlayer() && Game.getInstance().getLocalPlayer().isMuestra()))) {

                    ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                    cartas_repartidas_jugador.add(perdedor.getPlayingCard1());

                    cartas_repartidas_jugador.add(perdedor.getPlayingCard2());

                    String lascartas = this.utf8_cards ? this.translateNormalCards2UTF8(Card.collection2String(cartas_repartidas_jugador)) : Card.collection2String(cartas_repartidas_jugador);

                    String lajugada = this.utf8_cards ? this.translateNormalCards2UTF8(jugada.toString()) : jugada.toString();

                    this.text = this.text.replaceAll(perdedor.getNickname() + " +[(]---[)] +(\\w+ .+)", perdedor.getNickname() + " (" + lascartas + ") $1 -> " + lajugada);

                } else {

                    this.text = this.text.replaceAll(perdedor.getNickname() + " +[(]---[)]", perdedor.getNickname() + " (***)");

                }
            }

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    getTextArea().setText(text);

                    if (auto_scroll) {
                        getTextArea().setCaretPosition(getTextArea().getText().length());
                    }
                }
            });
        }
    }

    public synchronized void print(String msg) {

        String message = this.utf8_cards ? this.translateNormalCards2UTF8(Translator.translate(msg)) : Translator.translate(msg);

        text += message + "\n\n";

        Helpers.GUIRun(new Runnable() {
            public void run() {

                getTextArea().append(message + "\n\n");

                if (auto_scroll) {
                    getTextArea().setCaretPosition(getTextArea().getText().length());
                }
            }
        });

    }

    private synchronized void disableUTF8Cards() {

        this.text = translateUTF8Cards2Normal(this.text);

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                getTextArea().setText(text);

                if (auto_scroll) {
                    getTextArea().setCaretPosition(getTextArea().getText().length());
                }
            }
        });

    }

    private synchronized void enableUTF8Cards() {

        this.text = translateNormalCards2UTF8(this.text);

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                getTextArea().setText(text);

                if (auto_scroll) {
                    getTextArea().setCaretPosition(getTextArea().getText().length());
                }
            }
        });

    }

    private String translateUTF8Cards2Normal(String msg) {

        for (Map.Entry<String, String> entry : Card.getUNICODE_TABLE().entrySet()) {

            if (entry.getKey().length() > 1) {
                msg = msg.replace(entry.getValue(), "[" + entry.getKey() + "]");
            }
        }

        return msg;

    }

    private String translateNormalCards2UTF8(String msg) {

        for (Map.Entry<String, String> entry : Card.getUNICODE_TABLE().entrySet()) {

            if (entry.getKey().length() > 1) {

                msg = msg.replace("[" + entry.getKey() + "]", entry.getValue());
            }

        }

        return msg;

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        textarea = new javax.swing.JTextArea();
        jMenuBar1 = new javax.swing.JMenuBar();
        opciones_menu = new javax.swing.JMenu();
        auto_scroll_menu = new javax.swing.JCheckBoxMenuItem();
        utf8_cards_menu = new javax.swing.JCheckBoxMenuItem();

        setTitle("REGISTRO");

        jScrollPane1.setDoubleBuffered(true);

        textarea.setEditable(false);
        textarea.setBackground(new java.awt.Color(0, 0, 153));
        textarea.setColumns(20);
        textarea.setFont(new java.awt.Font("Dialog", 0, 20)); // NOI18N
        textarea.setForeground(new java.awt.Color(255, 255, 255));
        textarea.setLineWrap(true);
        textarea.setRows(5);
        textarea.setText("\n");
        textarea.setDoubleBuffered(true);
        jScrollPane1.setViewportView(textarea);

        jMenuBar1.setDoubleBuffered(true);

        opciones_menu.setText("Preferencias");
        opciones_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        auto_scroll_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_scroll_menu.setSelected(true);
        auto_scroll_menu.setText("Auto scroll");
        auto_scroll_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_scroll_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(auto_scroll_menu);

        utf8_cards_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        utf8_cards_menu.setSelected(true);
        utf8_cards_menu.setText("Cartas UTF-8");
        utf8_cards_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                utf8_cards_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(utf8_cards_menu);

        jMenuBar1.add(opciones_menu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 962, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 493, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void auto_scroll_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_scroll_menuActionPerformed
        // TODO add your handling code here:
        this.auto_scroll = this.auto_scroll_menu.isSelected();
    }//GEN-LAST:event_auto_scroll_menuActionPerformed

    private void utf8_cards_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_utf8_cards_menuActionPerformed
        // TODO add your handling code here:
        this.utf8_cards = this.utf8_cards_menu.isSelected();

        if (this.utf8_cards) {
            this.enableUTF8Cards();
        } else {
            this.disableUTF8Cards();
        }
    }//GEN-LAST:event_utf8_cards_menuActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBoxMenuItem auto_scroll_menu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JMenu opciones_menu;
    private javax.swing.JTextArea textarea;
    private javax.swing.JCheckBoxMenuItem utf8_cards_menu;
    // End of variables declaration//GEN-END:variables
}
