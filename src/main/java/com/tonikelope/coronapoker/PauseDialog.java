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

import java.awt.event.ActionEvent;
import javax.swing.JDialog;
import javax.swing.Timer;

/**
 *
 * @author tonikelope
 */
public class PauseDialog extends JDialog {

    private volatile Timer timer = null;
    private volatile Float last_zoom = null;
    // Listener sobre la ventana del juego: reancla el banner cuando el frame se mueve o cambia de
    // tamaño (modo ventana). Sin esto el diálogo se quedaba fijo en la pantalla al mover la ventana.
    // Se retira en windowClosed (los PauseDialog se crean/disponen en cada pausa; no debe acumularse).
    private java.awt.event.ComponentListener parent_follow_listener = null;

    // El diálogo de pausa se muestra como un BANNER a todo el ancho del frame del juego: fuente
    // notablemente más grande que el diseño original (52pt) para que "TIMBA PAUSADA" llene la franja.
    private static final float PAUSE_LABEL_FONT_SCALE = 1.8f;
    // Alto de la franja como múltiplo del alto natural del contenido (texto + márgenes).
    private static final float BANNER_HEIGHT_FACTOR = 1.4f;

    /**
     * Creates new form Pausa
     */
    public PauseDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        // Fuente del banner MÁS GRANDE. Se agranda ANTES de preserveOriginalFontSizes para que el
        // sistema de zoom del juego la capture como tamaño base y la escale de forma coherente.
        pausa_label.setFont(pausa_label.getFont().deriveFont(pausa_label.getFont().getSize2D() * PAUSE_LABEL_FONT_SCALE));

        last_zoom = (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        Helpers.preserveOriginalFontSizes(this);

        Helpers.updateFonts(this, Helpers.GUI_FONT, last_zoom);

        Helpers.translateComponents(this, false);

        pack();

        Helpers.setScaledIconLabel(pausa_label, getClass().getResource("/images/pause.png"), pausa_label.getHeight(), pausa_label.getHeight());

        pack();

        // Encima de todo mientras dura la pausa y con formato de banner (ancho completo).
        setAlwaysOnTop(true);

        // Franja semitransparente (90% de opacidad) para dejar entrever el tapete. La ventana es
        // undecorated, requisito de setOpacity; se protege por si la plataforma no lo soporta.
        try {
            setOpacity(0.9f);
        } catch (Exception | Error ex) {
        }

        applyBannerBounds();

        // El banner sigue a la ventana del juego (modo ventana): al mover el frame se recentra sobre
        // él; al redimensionarlo, recalcula ancho y alto. El listener se retira en windowClosed.
        java.awt.Window owner = getOwner();
        if (owner != null) {
            parent_follow_listener = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentMoved(java.awt.event.ComponentEvent e) {
                    setLocationRelativeTo(getParent());
                }

                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    applyBannerBounds();
                }
            };
            owner.addComponentListener(parent_follow_listener);
        }

        timer = new Timer(1000, (ActionEvent ae) -> {
            // El Timer de Swing dispara en el EDT: se tocan los componentes DIRECTAMENTE, sin salir
            // a otro hilo. (El codigo anterior hacia zoomFonts en un hilo aparte -> manipulaba Swing
            // fuera del EDT, un antipatron; y su GUIRun diferido podia ejecutarse tras el dispose del
            // dialogo al reanudar.) Guard: si el dialogo ya no se muestra (reanudada la partida), no
            // parpadear ni recomponer sobre una ventana muerta.
            if (!isShowing()) {
                return;
            }
            pausa_label.setVisible(!pausa_label.isVisible());
            if (pausa_label.isVisible()) {
                float zoom_now = 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP;
                // Comparacion segura (last_zoom es Float; el != con float autounboxeaba y habria dado
                // NPE si fuese null). Solo recomponemos cuando el zoom del juego cambia en vivo.
                if (last_zoom == null || last_zoom.floatValue() != zoom_now) {
                    last_zoom = zoom_now;
                    pausa_label.setIcon(null);
                    Helpers.zoomFonts(pausa_label, zoom_now, null);
                    pack(); // recalcula el alto del label con la nueva fuente antes de escalar el icono
                    Helpers.setScaledIconLabel(pausa_label, getClass().getResource("/images/pause.png"), pausa_label.getHeight(), pausa_label.getHeight());
                    // Re-aplica el formato de banner: el pack() habria vuelto al tamaño del contenido.
                    applyBannerBounds();
                }
            }
        });
    }

    // Tamaño/posición del diálogo de pausa como BANNER: ancho = TODO el ancho del frame del juego
    // (parent) y alto = BANNER_HEIGHT_FACTOR × el que necesita su contenido; centrado sobre el
    // parent. El texto (pausa_label, alineado CENTER y estirado por el layout) queda centrado H y V
    // dentro de la franja. Hace su propio pack() primero, así es idempotente (siempre el mismo
    // múltiplo del contenido, no del tamaño ya agrandado) y sirve tanto al crear como al recomponer
    // por cambio de zoom.
    private void applyBannerBounds() {
        java.awt.Container parent = getParent();
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            return;
        }
        pack();
        setSize(parent.getWidth(), Math.round(getHeight() * BANNER_HEIGHT_FACTOR));
        setLocationRelativeTo(parent);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panel = new javax.swing.JPanel();
        pausa_label = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        setUndecorated(true);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        panel.setBackground(new java.awt.Color(255, 255, 255));

        pausa_label.setBackground(new java.awt.Color(255, 255, 255));
        pausa_label.setFont(new java.awt.Font("Dialog", 1, 52)); // NOI18N
        pausa_label.setForeground(new java.awt.Color(255, 0, 0));
        pausa_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pausa_label.setText("TIMBA PAUSADA");
        pausa_label.putClientProperty("i18n.key", "game.timba_pausada");

        javax.swing.GroupLayout panelLayout = new javax.swing.GroupLayout(panel);
        panel.setLayout(panelLayout);
        panelLayout.setHorizontalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pausa_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        panelLayout.setVerticalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pausa_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        GameFrame.getInstance().getExit_menu().doClick();
    }//GEN-LAST:event_formWindowClosing

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        // TODO add your handling code here:
        this.timer.stop();

        // Retira el seguidor de la ventana del juego (añadido en el constructor) para no dejarlo
        // colgado en el frame persistente tras disponer este diálogo.
        java.awt.Window owner = getOwner();
        if (parent_follow_listener != null && owner != null) {
            owner.removeComponentListener(parent_follow_listener);
        }
    }//GEN-LAST:event_formWindowClosed

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:

        this.timer.start();
    }//GEN-LAST:event_formWindowOpened

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        // TODO add your handling code here:

        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
    }//GEN-LAST:event_formMouseClicked

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
    private javax.swing.JPanel panel;
    private javax.swing.JLabel pausa_label;
    // End of variables declaration//GEN-END:variables
}
