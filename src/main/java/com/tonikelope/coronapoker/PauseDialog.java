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
 * Full-width banner dialog shown while the game is paused; it overlays the owning game
 * window's content pane and follows it as it moves or resizes.
 *
 * @author tonikelope
 */
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public class PauseDialog extends JDialog {

    private volatile Timer timer = null;
    // Listener on the game window: re-anchors the banner when the frame moves or resizes
    // (windowed mode). Without it the dialog stayed fixed on screen while the window moved.
    // Removed in windowClosed, since PauseDialog instances are created/disposed per pause and
    // must not accumulate.
    private java.awt.event.ComponentListener parent_follow_listener = null;
    // Base banner font (GUI_FONT family + BOLD); applyBannerBounds sets the SIZE from the game
    // window's size so the text scales as the window is resized.
    private java.awt.Font base_font = null;

    // The pause dialog is a BANNER spanning the full width of the game window. Its height and
    // the text/icon size scale with the WINDOW, not a fixed size, so shrinking the window
    // shrinks the banner proportionally.
    private static final float BANNER_HEIGHT_FRACTION = 0.14f;  // banner height = 14% of the window height
    private static final float FONT_HEIGHT_FRACTION = 0.5f;     // text height ~= 50% of the banner height
    private static final float MAX_TEXT_WIDTH_FRACTION = 0.9f;  // text + icon never exceed 90% of the width

    /**
     * Creates the pause banner dialog.
     *
     * @param parent owning game window
     * @param modal whether the dialog blocks input to its owner
     */
    public PauseDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        // Blinking hides/shows pausa_label. GroupLayout honors visibility by default and would
        // collapse the panel to ~0 height while the label is invisible; honorsVisibility = false
        // keeps its space reserved so the banner size doesn't jitter while blinking.
        ((javax.swing.GroupLayout) panel.getLayout()).setHonorsVisibility(pausa_label, false);

        // GUI_FONT family only, no zoom: applyBannerBounds sets the SIZE from the window.
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        // Base font: family already applied, plus BOLD; applyBannerBounds derives the size on
        // every resize.
        base_font = pausa_label.getFont().deriveFont(java.awt.Font.BOLD);

        pack(); // realizes the dialog's peer so setOpacity/applyBannerBounds can operate on it

        // Semi-transparent strip (95% opacity) so the felt still shows through. The window is
        // undecorated, a requirement for setOpacity; guarded in case the platform doesn't support it.
        // NOTE: no setAlwaysOnTop. With always-on-top the banner sat ON TOP of modal dialogs (e.g.
        // "Leave the game?"), covering them and blocking their input (neither dialog nor banner
        // responded). As a JDialog with an owner, the banner already shows above the felt, and a
        // modal can appear over it and be used normally.
        try {
            setOpacity(0.95f);
        } catch (Exception | Error ex) {
        }

        applyBannerBounds();

        // The banner follows the game window: moving the frame recenters it; resizing recalculates
        // width, height and font/icon size. Removed in windowClosed.
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

        // Swing Timer fires on the EDT; it only blinks the text. isShowing() guard avoids touching
        // the dialog after it's disposed when the game resumes.
        timer = new Timer(1000, (ActionEvent ae) -> {
            if (!isShowing()) {
                return;
            }
            pausa_label.setVisible(!pausa_label.isVisible());
        });
    }

    // Banner size/position: sized and placed over the frame's CONTENT PANE (the felt area), not
    // the decorated window, so it doesn't overhang the borders or get thrown off-center by the
    // title bar/menu. Width = content width; height = a fraction of content height; text/icon
    // scale with that height (shrunk if the text wouldn't fit the width). Centered vertically over
    // the content. No pack() here, the window drives the size, not the content.
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
        // Banner height (and with it the font and icon, both derived from banner_h) follows the
        // dialog zoom: at 100% it equals the design fraction unchanged. Width still tracks the window.
        int banner_h = Math.max(1, Math.round(ch * BANNER_HEIGHT_FRACTION * Helpers.DIALOG_ZOOM));

        // Font size proportional to the banner height, shrunk if the text + icon don't fit the width.
        float font_size = banner_h * FONT_HEIGHT_FRACTION;
        java.awt.FontMetrics fm = pausa_label.getFontMetrics(base_font.deriveFont(font_size));
        int text_w = fm.stringWidth(pausa_label.getText()) + Math.round(font_size) + pausa_label.getIconTextGap();
        int max_w = Math.round(cw * MAX_TEXT_WIDTH_FRACTION);
        if (text_w > max_w && text_w > 0) {
            font_size *= (float) max_w / text_w;
        }
        pausa_label.setFont(base_font.deriveFont(font_size));

        // Square icon sized to match the font.
        int icon_px = Math.max(1, Math.round(font_size));
        Helpers.setScaledIconLabel(pausa_label, getClass().getResource("/images/pause.png"), icon_px, icon_px);

        java.awt.Point origin = content.getLocationOnScreen();
        setSize(cw, banner_h);
        setLocation(origin.x, origin.y + (ch - banner_h) / 2);
    }

    // Repositions the banner over the content pane without recalculating size/font/icon, to follow
    // the window on MOVE only (cheaper than applyBannerBounds on every drag pixel).
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

        // Removes the game-window listener added in the constructor so it isn't left hanging on
        // the persistent frame after this dialog is disposed.
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
