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

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

/**
 *
 * @author tonikelope
 */
public class Reconnect2ServerDialog extends JDialog {

    private volatile boolean reconectar = false;

    public void reset() {

        Helpers.GUIRun(() -> {
            getIp_port().setEnabled(true);
            getYes().setText(Translator.translate("RECONECTAR"));
            getYes().setEnabled(true);
            getStatus().setEnabled(true);
            getStatus2().setEnabled(true);
            getBarra().setVisible(false);
            pack();
        });
    }

    public boolean isReconectar() {
        return reconectar;
    }

    public void setReconectar(boolean reconectar) {
        this.reconectar = reconectar;
    }

    public JTextField getIp_port() {
        return ip_port;
    }

    public JButton getYes() {
        return yes;
    }

    public JProgressBar getBarra() {
        return barra;
    }

    public JLabel getStatus() {
        return status;
    }

    public JLabel getStatus2() {
        return status2;
    }

    /**
     * Creates new form Reconnect2ServerDialog
     */
    public Reconnect2ServerDialog(java.awt.Frame parent, boolean modal, String ip_p) {
        super(parent, modal);

        initComponents();
        ip_port.setText(ip_p);
        barra.setVisible(false);
        Helpers.barraIndeterminada(barra);
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        pack();

        Helpers.setScaledIconButton(yes, getClass().getResource("/images/action/plug.png"), yes.getHeight(), yes.getHeight());
        Helpers.setScaledIconButton(exit_button, getClass().getResource("/images/exit.png"), exit_button.getHeight(), exit_button.getHeight());

        pack();

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        status = new javax.swing.JLabel();
        ip_port = new javax.swing.JTextField();
        yes = new javax.swing.JButton();
        barra = new javax.swing.JProgressBar();
        status2 = new javax.swing.JLabel();
        exit_button = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 102, 0), 10));

        status.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        status.setText("SE PERDIÓ LA CONEXIÓN CON EL SERVIDOR");
        status.setDoubleBuffered(true);

        ip_port.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        ip_port.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        ip_port.setText("tonikelope.noestasinvitado.com:23456");
        ip_port.setDoubleBuffered(true);

        yes.setBackground(new java.awt.Color(0, 153, 51));
        yes.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        yes.setForeground(new java.awt.Color(255, 255, 255));
        yes.setText("RECONECTAR");
        yes.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        yes.setDoubleBuffered(true);
        yes.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                yesActionPerformed(evt);
            }
        });

        barra.setDoubleBuffered(true);

        status2.setFont(new java.awt.Font("Dialog", 2, 16)); // NOI18N
        status2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        status2.setText("(Comprueba si la dirección o el puerto han cambiado antes de reconectar)");
        status2.setDoubleBuffered(true);

        exit_button.setBackground(new java.awt.Color(255, 0, 0));
        exit_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        exit_button.setForeground(new java.awt.Color(255, 255, 255));
        exit_button.setText("SALIR DEL JUEGO");
        exit_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        exit_button.setDoubleBuffered(true);
        exit_button.setFocusable(false);
        exit_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exit_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ip_port)
                    .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(yes, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(status2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exit_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ip_port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(status)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(status2)
                .addGap(18, 18, 18)
                .addComponent(yes)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(exit_button)
                .addGap(18, 18, 18)
                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void yesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_yesActionPerformed
        // TODO add your handling code here:
        this.ip_port.setEnabled(false);
        this.status.setEnabled(false);
        this.status2.setEnabled(false);
        this.yes.setEnabled(false);
        this.yes.setText(Translator.translate("Reconectando..."));
        this.barra.setVisible(true);
        this.reconectar = true;
        pack();

        Helpers.threadRun(() -> {
            synchronized (WaitingRoomFrame.getInstance().getLock_reconnect()) {
                WaitingRoomFrame.getInstance().getLock_reconnect().notifyAll();
            }
        });
    }//GEN-LAST:event_yesActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:
        Audio.playWavResource("misc/warning.wav");
    }//GEN-LAST:event_formComponentShown

    private void exit_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exit_buttonActionPerformed
        // TODO add your handling code here:

        System.exit(1);
    }//GEN-LAST:event_exit_buttonActionPerformed

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
    private javax.swing.JProgressBar barra;
    private javax.swing.JButton exit_button;
    private javax.swing.JTextField ip_port;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel status;
    private javax.swing.JLabel status2;
    private javax.swing.JButton yes;
    // End of variables declaration//GEN-END:variables
}
