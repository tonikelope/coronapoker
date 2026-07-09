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
    // Listener sobre la ventana del juego: reancla el banner cuando el frame se mueve o cambia de
    // tamaño (modo ventana). Sin esto el diálogo se quedaba fijo en la pantalla al mover la ventana.
    // Se retira en windowClosed (los PauseDialog se crean/disponen en cada pausa; no debe acumularse).
    private java.awt.event.ComponentListener parent_follow_listener = null;
    // Fuente base del banner (family GUI_FONT + BOLD); el TAMAÑO lo fija applyBannerBounds según el
    // tamaño de la ventana del juego, para que el texto escale al reducir/agrandar la ventana.
    private java.awt.Font base_font = null;

    // El diálogo de pausa es un BANNER a todo el ancho de la ventana del juego. Su alto y el tamaño
    // del texto/icono escalan con la VENTANA (no con un tamaño fijo): al reducir la ventana el banner
    // se reduce proporcionalmente.
    private static final float BANNER_HEIGHT_FRACTION = 0.14f;  // alto del banner = 14% del alto de la ventana
    private static final float FONT_HEIGHT_FRACTION = 0.5f;     // alto del texto ~= 50% del alto del banner
    private static final float MAX_TEXT_WIDTH_FRACTION = 0.9f;  // el texto + icono no supera el 90% del ancho

    /**
     * Creates new form Pausa
     */
    public PauseDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        // El parpadeo oculta/muestra el texto (pausa_label). Por defecto GroupLayout HONRA la
        // visibilidad y colapsaría el panel a altura ~0 con el label invisible. Con honorsVisibility
        // = false el layout reserva su espacio pase lo que pase con la visibilidad, así el tamaño del
        // banner no baila con el parpadeo.
        ((javax.swing.GroupLayout) panel.getLayout()).setHonorsVisibility(pausa_label, false);

        // Family GUI_FONT (sin zoom: el TAMAÑO lo fija applyBannerBounds según la ventana).
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        // Fuente base: family ya aplicado + BOLD; applyBannerBounds deriva el tamaño en cada resize.
        base_font = pausa_label.getFont().deriveFont(java.awt.Font.BOLD);

        pack(); // realiza el diálogo (peer) para que setOpacity/applyBannerBounds operen sobre él

        // Franja semitransparente (90% de opacidad) para dejar entrever el tapete. La ventana es
        // undecorated, requisito de setOpacity; se protege por si la plataforma no lo soporta.
        // OJO: SIN setAlwaysOnTop. Con always-on-top el banner quedaba ENCIMA de los diálogos modales
        // (p. ej. "¿SALIR DE LA TIMBA?"), tapándolos y bloqueando su input (ni el diálogo ni el banner
        // respondían). Como JDialog con owner, el banner ya se muestra sobre el tapete; y un modal
        // puede aparecer sobre él y usarse con normalidad.
        try {
            setOpacity(0.9f);
        } catch (Exception | Error ex) {
        }

        applyBannerBounds();

        // El banner sigue a la ventana del juego: al mover el frame se recentra sobre él; al
        // redimensionarlo, recalcula ancho, alto y tamaño de fuente/icono. Se retira en windowClosed.
        java.awt.Window owner = getOwner();
        if (owner != null) {
            parent_follow_listener = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentMoved(java.awt.event.ComponentEvent e) {
                    repositionOverContent();
                }

                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    applyBannerBounds();
                }
            };
            owner.addComponentListener(parent_follow_listener);
        }

        // El Timer de Swing dispara en el EDT: solo parpadea el texto. Guard isShowing() para no
        // tocar el diálogo tras el dispose al reanudar la partida.
        timer = new Timer(1000, (ActionEvent ae) -> {
            if (!isShowing()) {
                return;
            }
            pausa_label.setVisible(!pausa_label.isVisible());
        });
    }

    // Tamaño/posición del banner: se dimensiona y coloca sobre el CONTENT PANE del frame (el área del
    // tapete), NO sobre la ventana con decoración: así no sobresale por los bordes ni se descentra por
    // la barra de título/menú. Ancho = ancho del content; alto = fracción de su alto; texto/icono con
    // tamaño proporcional a ese alto (recortado si "TIMBA PAUSADA" no cupiera de ancho). Centrado
    // verticalmente sobre el content. Sin pack() (lo manda la ventana, no el contenido).
    private void applyBannerBounds() {
        java.awt.Window owner = getOwner();
        if (!(owner instanceof javax.swing.RootPaneContainer) || base_font == null) {
            return;
        }
        java.awt.Container content = ((javax.swing.RootPaneContainer) owner).getContentPane();
        if (content == null || !content.isShowing() || content.getWidth() <= 0 || content.getHeight() <= 0) {
            return;
        }
        int cw = content.getWidth();
        int ch = content.getHeight();
        int banner_h = Math.max(1, Math.round(ch * BANNER_HEIGHT_FRACTION));

        // Fuente proporcional a la altura del banner, encogida si el texto + icono no cabe de ancho.
        float font_size = banner_h * FONT_HEIGHT_FRACTION;
        java.awt.FontMetrics fm = pausa_label.getFontMetrics(base_font.deriveFont(font_size));
        int text_w = fm.stringWidth(pausa_label.getText()) + Math.round(font_size) + pausa_label.getIconTextGap();
        int max_w = Math.round(cw * MAX_TEXT_WIDTH_FRACTION);
        if (text_w > max_w && text_w > 0) {
            font_size *= (float) max_w / text_w;
        }
        pausa_label.setFont(base_font.deriveFont(font_size));

        // Icono (cuadrado) al tamaño de la fuente.
        int icon_px = Math.max(1, Math.round(font_size));
        Helpers.setScaledIconLabel(pausa_label, getClass().getResource("/images/pause.png"), icon_px, icon_px);

        java.awt.Point origin = content.getLocationOnScreen();
        setSize(cw, banner_h);
        setLocation(origin.x, origin.y + (ch - banner_h) / 2);
    }

    // Reposiciona el banner sobre el content pane sin recalcular tamaño/fuente/icono, para seguir a la
    // ventana cuando solo se MUEVE (más barato que applyBannerBounds en cada píxel de arrastre).
    private void repositionOverContent() {
        java.awt.Window owner = getOwner();
        if (!(owner instanceof javax.swing.RootPaneContainer)) {
            return;
        }
        java.awt.Container content = ((javax.swing.RootPaneContainer) owner).getContentPane();
        if (content == null || !content.isShowing()) {
            return;
        }
        java.awt.Point origin = content.getLocationOnScreen();
        setLocation(origin.x, origin.y + (content.getHeight() - getHeight()) / 2);
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
