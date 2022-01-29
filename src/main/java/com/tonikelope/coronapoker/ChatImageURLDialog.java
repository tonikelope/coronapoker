/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.MediaTracker;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class ChatImageURLDialog extends javax.swing.JDialog {

    private static final ArrayDeque<String> HISTORIAL = cargarHistorial();
    private static final HashMap<String, JLabel> LABEL_CACHE = new HashMap<>();
    private volatile static ChatImageURLDialog THIS;

    /**
     * Creates new form ChatImageURLDialog
     */
    public ChatImageURLDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();

        Helpers.setTranslatedTitle(this, "Enviar URL de imagen");

        Helpers.JTextFieldRegularPopupMenu.addTo(image_url);

        barra.setIndeterminate(true);

        send_button.setEnabled(false);

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);

        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        pack();

        THIS = this;

        cargarHistorialPanel();
    }

    private void cargarHistorialPanel() {

        Helpers.threadRun(new Runnable() {
            public void run() {

                int max_width = 0;
                int max_height = 0;

                ArrayList<String> rem = new ArrayList<>();

                for (String h : HISTORIAL) {

                    if (!LABEL_CACHE.containsKey(h)) {
                        ImageIcon image;

                        try {
                            image = new ImageIcon(new URL(h));

                            if (image.getImageLoadStatus() != MediaTracker.ERRORED) {

                                if (image.getIconWidth() > max_width) {
                                    max_width = image.getIconWidth();
                                }

                                if (image.getIconHeight() > max_height) {
                                    max_height = image.getIconHeight();
                                }

                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        JLabel label = new JLabel();
                                        LABEL_CACHE.put(h, label);
                                        label.setAlignmentX(0.5f);
                                        label.setBorder(new EmptyBorder(10, 0, 10, 0));
                                        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                                        label.setIcon(image);
                                        label.addMouseListener(new MouseAdapter() {
                                            @Override
                                            public void mouseClicked(MouseEvent e) {

                                                if (SwingUtilities.isLeftMouseButton(e)) {
                                                    image_url.setText(h);
                                                    send_buttonActionPerformed(null);

                                                } else if (SwingUtilities.isRightMouseButton(e)) {

                                                    label.setBorder(new LineBorder(Color.RED, 5));

                                                    if (Helpers.mostrarMensajeInformativoSINO(label.getParent().getParent(), "¿ELIMINAR ESTA IMAGEN DEL HISTORIAL?") == 0) {
                                                        HISTORIAL.remove(h);
                                                        THIS.historial_panel.remove(label);
                                                        LABEL_CACHE.remove(label);
                                                        THIS.revalidate();
                                                        THIS.repaint();

                                                        Helpers.threadRun(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                guardarHistorial();
                                                            }
                                                        });
                                                    }

                                                    label.setBorder(new EmptyBorder(10, 0, 10, 0));
                                                }

                                            }
                                        });
                                        historial_panel.add(label);

                                    }
                                });

                            } else {
                                rem.add(h);

                            }

                        } catch (MalformedURLException ex) {
                            rem.add(h);
                            Logger.getLogger(ChatImageURLDialog.class.getName()).log(Level.SEVERE, null, ex);

                        }

                    } else {
                        historial_panel.add(LABEL_CACHE.get(h));

                        if (LABEL_CACHE.get(h).getIcon().getIconWidth() > max_width) {
                            max_width = LABEL_CACHE.get(h).getIcon().getIconWidth();
                        }

                        if (LABEL_CACHE.get(h).getIcon().getIconHeight() > max_height) {
                            max_height = LABEL_CACHE.get(h).getIcon().getIconHeight();
                        }
                    }

                }

                HISTORIAL.removeAll(rem);

                for (String url : rem) {
                    LABEL_CACHE.remove(url);
                }

                guardarHistorial();

                int w = max_width;
                int h = max_height;

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        setPreferredSize(new Dimension(w + 35, h + 35));
                        setSize(new Dimension(w + 35, h + 35));
                        barra.setVisible(false);
                        loading.setVisible(false);
                        send_button.setEnabled(true);
                        pack();

                        Helpers.containerSetLocationRelativeTo(getParent(), THIS);
                    }
                });

            }
        });

    }

    public synchronized static void updateHistorialEnviados(String url) {

        if (HISTORIAL.isEmpty() || !HISTORIAL.peekFirst().equals(url)) {

            if (HISTORIAL.contains(url)) {
                HISTORIAL.remove(url);
            }

            HISTORIAL.push(url);
        }
    }

    public synchronized static void updateHistorialRecibidos(String url) {

        if (!HISTORIAL.contains(url)) {

            HISTORIAL.addLast(url);
        }
    }

    public synchronized static void guardarHistorial() {

        String[] historial = HISTORIAL.toArray(new String[0]);

        for (int i = 0; i < historial.length; i++) {

            try {
                historial[i] = Base64.encodeBase64String(historial[i].getBytes("UTF-8"));

            } catch (Exception ex) {
                Logger.getLogger(ChatImageURLDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        Helpers.PROPERTIES.setProperty("chat_img_hist", String.join("@", historial));

        Helpers.savePropertiesFile();

    }

    private static ArrayDeque<String> cargarHistorial() {

        ArrayDeque<String> historial = new ArrayDeque<>();

        String hist_b64 = Helpers.PROPERTIES.getProperty("chat_img_hist", "");

        if (!hist_b64.isBlank()) {

            String[] hist = hist_b64.split("@");

            for (String h : hist) {
                try {
                    historial.addLast(new String(Base64.decodeBase64(h), "UTF-8"));
                } catch (Exception ex) {
                    Logger.getLogger(ChatImageURLDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return historial;

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        image_url = new javax.swing.JTextField();
        send_button = new javax.swing.JButton();
        scroll_panel = new javax.swing.JScrollPane();
        historial_panel = new javax.swing.JPanel();
        barra = new javax.swing.JProgressBar();
        loading = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Enviar URL de imagen");
        setModal(true);
        setPreferredSize(new java.awt.Dimension(744, 300));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        image_url.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        image_url.setDoubleBuffered(true);

        send_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        send_button.setText("Enviar");
        send_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        send_button.setDoubleBuffered(true);
        send_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                send_buttonActionPerformed(evt);
            }
        });

        scroll_panel.setBorder(null);
        scroll_panel.setDoubleBuffered(true);

        historial_panel.setLayout(new javax.swing.BoxLayout(historial_panel, javax.swing.BoxLayout.Y_AXIS));
        scroll_panel.setViewportView(historial_panel);

        barra.setDoubleBuffered(true);

        loading.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        loading.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        loading.setText("CARGANDO IMÁGENES...");
        loading.setDoubleBuffered(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scroll_panel)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(barra, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(image_url)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(send_button))
                    .addComponent(loading, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(send_button, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(image_url))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(loading)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 22, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void send_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_send_buttonActionPerformed
        // TODO add your handling code here:

        send_button.setEnabled(false);

        barra.setVisible(true);

        String url = image_url.getText().trim();

        if (url.startsWith("http")) {

            Helpers.threadRun(new Runnable() {

                public void run() {

                    if (!LABEL_CACHE.containsKey(url)) {

                        try {
                            ImageIcon image = new ImageIcon(new URL(url));

                            if (image.getImageLoadStatus() != MediaTracker.ERRORED) {

                                Helpers.GUIRun(new Runnable() {

                                    public void run() {

                                        WaitingRoomFrame.getInstance().chatHTMLAppend(WaitingRoomFrame.getInstance().getLocal_nick() + ":(" + Helpers.getLocalTimeString() + ") " + url.replaceAll("^http", "img") + "\n");

                                        THIS.setVisible(false);

                                        WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                                    }
                                });

                                WaitingRoomFrame.getInstance().enviarMensajeChat(WaitingRoomFrame.getInstance().getLocal_nick(), url.replaceAll("^http", "img"));

                            } else {
                                Helpers.mostrarMensajeError(THIS, "ERROR: LA IMAGEN NO ES VÁLIDA");

                                Helpers.GUIRun(new Runnable() {

                                    public void run() {
                                        barra.setVisible(false);
                                        send_button.setEnabled(true);
                                    }
                                });
                            }
                        } catch (MalformedURLException ex) {
                            Logger.getLogger(ChatImageURLDialog.class.getName()).log(Level.SEVERE, null, ex);
                            Helpers.mostrarMensajeError(THIS, "ERROR: LA IMAGEN NO ES VÁLIDA");

                            Helpers.GUIRun(new Runnable() {

                                public void run() {
                                    barra.setVisible(false);
                                    send_button.setEnabled(true);
                                }
                            });
                        }

                    } else {
                        Helpers.GUIRun(new Runnable() {

                            public void run() {

                                WaitingRoomFrame.getInstance().chatHTMLAppend(WaitingRoomFrame.getInstance().getLocal_nick() + ":(" + Helpers.getLocalTimeString() + ") " + url.replaceAll("^http", "img") + "\n");

                                THIS.setVisible(false);

                                WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                            }
                        });

                        WaitingRoomFrame.getInstance().enviarMensajeChat(WaitingRoomFrame.getInstance().getLocal_nick(), url.replaceAll("^http", "img"));
                    }

                }
            });
        } else {
            barra.setVisible(false);
            send_button.setEnabled(true);
        }

    }//GEN-LAST:event_send_buttonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        WaitingRoomFrame.getInstance().getChat_box().requestFocus();
    }//GEN-LAST:event_formWindowClosing

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar barra;
    private javax.swing.JPanel historial_panel;
    private javax.swing.JTextField image_url;
    private javax.swing.JLabel loading;
    private javax.swing.JScrollPane scroll_panel;
    private javax.swing.JButton send_button;
    // End of variables declaration//GEN-END:variables
}
