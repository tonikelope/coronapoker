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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import javax.swing.JDialog;

/**
 *
 * @author tonikelope
 */
public class QRChatDialog extends JDialog implements ClipboardChangeObserver {

    public static final int QR_SIZE = 300;
    private volatile boolean cboard_monitor = false;
    private volatile String link = null;
    private volatile boolean cancel = false;

    public boolean isCancel() {
        return cancel;
    }

    public String getLink() {
        return link;
    }

    /**
     * Creates new form Identicon
     */
    public QRChatDialog(java.awt.Frame parent, boolean modal, String link, boolean clipboard_monitor) {
        super(parent, modal);

        this.link = link;

        cboard_monitor = clipboard_monitor;

        initComponents();

        share_button.setVisible(cboard_monitor);

        updateQR(link);

        share_button.setEnabled(false);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        pack();

        if (cboard_monitor) {
            Helpers.CLIPBOARD_SPY.attachObserver(this);
        }
    }

    private void updateQR(String link) {

        if (link != null) {

            try {
                QRCodeWriter barcodeWriter = new QRCodeWriter();

                BitMatrix bitMatrix = barcodeWriter.encode(link, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);

                Helpers.GUIRunAndWait(() -> {
                    qr_status.setText(link);

                    qr_status.setToolTipText(Translator.translate("Click para copiar enlace"));

                    share_button.setEnabled(true);

                    ImageIcon icon = new ImageIcon(MatrixToImageWriter.toBufferedImage(bitMatrix));

                    icon_label.setIcon(icon);

                    pack();

                    if (isVisible()) {
                        setVisible(false);
                        setLocationRelativeTo(getParent());
                        setVisible(true);
                    }
                });
            } catch (WriterException ex) {
                Logger.getLogger(QRChatDialog.class.getName()).log(Level.SEVERE, null, ex);
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

        jPanel1 = new javax.swing.JPanel();
        icon_label = new javax.swing.JLabel();
        close_button = new javax.swing.JButton();
        qr_status = new javax.swing.JLabel();
        share_button = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("VIDEO CHAT");
        setFocusable(false);
        setUndecorated(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(26, 115, 232), 8));

        icon_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/duo_big.png"))); // NOI18N
        icon_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        icon_label.setDoubleBuffered(true);
        icon_label.setFocusable(false);
        icon_label.setPreferredSize(new Dimension(QR_SIZE, QR_SIZE));
        icon_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                icon_labelMouseClicked(evt);
            }
        });

        close_button.setBackground(new java.awt.Color(255, 0, 0));
        close_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        close_button.setForeground(new java.awt.Color(255, 255, 255));
        close_button.setText("CERRAR");
        close_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        close_button.setDoubleBuffered(true);
        close_button.setFocusable(false);
        close_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                close_buttonActionPerformed(evt);
            }
        });

        qr_status.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        qr_status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        qr_status.setText("Monitorizando portapapeles...");
        qr_status.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        qr_status.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                qr_statusMouseClicked(evt);
            }
        });

        share_button.setBackground(new java.awt.Color(26, 115, 232));
        share_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        share_button.setForeground(new java.awt.Color(255, 255, 255));
        share_button.setText("COMPARTIR");
        share_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        share_button.setDoubleBuffered(true);
        share_button.setFocusable(false);
        share_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                share_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(close_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(qr_status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(share_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(icon_label, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(qr_status)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(icon_label, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(share_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(close_button)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void close_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_close_buttonActionPerformed
        // TODO add your handling code here:

        if (cboard_monitor) {
            Helpers.CLIPBOARD_SPY.detachObserver(this);
        }

        this.cancel = true;

        dispose();
    }//GEN-LAST:event_close_buttonActionPerformed

    private void share_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_share_buttonActionPerformed
        // TODO add your handling code here:

        if (cboard_monitor) {
            Helpers.CLIPBOARD_SPY.detachObserver(this);
        }

        dispose();
    }//GEN-LAST:event_share_buttonActionPerformed

    private void icon_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_icon_labelMouseClicked
        // TODO add your handling code here:

        if (this.link != null) {

            if (cboard_monitor) {
                Helpers.CLIPBOARD_SPY.detachObserver(this);
            }

            Helpers.copyTextToClipboard(this.link);
            Helpers.mostrarMensajeInformativo(this, "¡ENLACE COPIADO EN EL PORTAPAPELES!");

            if (cboard_monitor) {
                Helpers.CLIPBOARD_SPY.attachObserver(this);
            }

        } else if (cboard_monitor) {
            Helpers.openBrowserURL("https://meet.google.com");
        }
    }//GEN-LAST:event_icon_labelMouseClicked

    private void qr_statusMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_qr_statusMouseClicked
        // TODO add your handling code here:

        if (this.link != null) {

            Helpers.copyTextToClipboard(this.link);

            Helpers.mostrarMensajeInformativo(this, "¡ENLACE COPIADO EN EL PORTAPAPELES!");

        } else if (cboard_monitor) {
            Helpers.openBrowserURL("https://meet.google.com");
        }
    }//GEN-LAST:event_qr_statusMouseClicked

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
    private javax.swing.JButton close_button;
    private javax.swing.JLabel icon_label;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel qr_status;
    private javax.swing.JButton share_button;
    // End of variables declaration//GEN-END:variables

    @Override
    public void notifyClipboardChange() {

        String contenido = Helpers.extractStringFromClipboardContents(Helpers.CLIPBOARD_SPY.getContents());

        if (contenido != null) {

            Pattern pattern = Pattern.compile("https://(?:duo\\.app\\.goo\\.gl|meet\\.google\\.com)/[^ \r\n]+", Pattern.DOTALL);

            Matcher matcher = pattern.matcher(contenido);

            if (matcher.find()) {

                String new_link = matcher.group(0);

                if (!new_link.equals(link)) {

                    link = new_link;

                    updateQR(link);
                }
            }

        }
    }
}
