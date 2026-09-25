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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JLabel;

/**
 * Floating fast-access button bar overlaid on the table. Collapsed, only the
 * "menu" icon is visible; hovering it deploys the full bar, which auto-hides
 * (after a delay, with a fade) once the mouse leaves.
 *
 * @author tonikelope
 */
public final class TapeteFastButtons extends javax.swing.JPanel implements ZoomableInterface {

    public final static int H = 50;
    private final Object[][] botones;
    private volatile Dimension pref_size;
    private volatile float zoom_factor;

    // Collapsible fading bar: deploys ONLY on hover over the "menu" icon (bottom-left corner, the
    // only icon always visible while collapsed). Leaving the bar does not collapse it instantly;
    // it waits HIDE_DELAY_MS, then fades over FADE_MS. Re-entering during either the wait or the
    // fade cancels it and restores full opacity.
    private static final int HIDE_DELAY_MS = 1000;
    private static final int FADE_MS = 400;
    private static final int FADE_INTERVAL_MS = 16;
    private volatile float bar_opacity = 1f;
    private javax.swing.Timer hide_delay_timer;
    private javax.swing.Timer fade_timer;

    public Dimension getPref_size() {
        return pref_size;
    }

    public boolean areButtonsVisible() {
        return chat.isVisible();
    }

    public JLabel getMenu() {
        return menu;
    }

    /**
     * Builds the bar, binds its icons/tooltips and starts it collapsed.
     */
    public TapeteFastButtons() {

        initComponents();
        botones = new Object[][]{{chat, "chat.png", "chat.chat_rapido"}, {mic, "mic.png", "audio.boton_nota_voz"}, {image, "image.png", "chat.enviar_imagen"}, {compact, "compact.png", "view.vista_compacta"}, {zoom_in, "zoom_in.png", "view.aumentar_zoom"}, {zoom_reset, "zoom_reset.png", "view.reset_zoom"}, {zoom_out, "zoom_out.png", "view.reducir_zoom"}, {fullscreen, "fullscreen.png", "view.pantalla_completa"}, {log, "log.png", "log.registro"}, {rebuy, "rebuy.png", "rebuy.recomprar"}};

        for (Object[] b : botones) {
            Helpers.setScaledIconLabel(((JLabel) b[0]), getClass().getResource("/images/fast_panel/" + ((String) b[1])), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H));
            ((JLabel) b[0]).setToolTipText(Translator.translate((String) b[2]));
        }

        Helpers.setScaledIconLabel(menu, getClass().getResource("/images/fast_panel/menu.png"), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H));

        pref_size = getPreferredSize();
        // The panel spans the deployed bar's width even while collapsed; its own cursor stays
        // DEFAULT so the empty gap to the right of the icon does NOT show a hand. The "menu" icon
        // and the buttons each keep their own hand cursor.
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        hideButtons();
        setComListeners();
    }

    private void zoomIcons(float factor) {

        Helpers.GUIRunAndWait(() -> {
            // Nothing is rescaled while the bar is deployed: changing zoom from the bar's own
            // buttons must not rebuild it under the mouse (icons would shift mid-click). It stays
            // as-is until collapsed; showButtons() then rebuilds it at the new size next time it
            // deploys (it compares zoom_factor against the current level).
            if (chat.isVisible()) {
                return;
            }

            for (Object[] b : botones) {
                Helpers.setScaledIconLabel(((JLabel) b[0]), getClass().getResource("/images/fast_panel/" + ((String) b[1])), Math.round(factor * H), Math.round(factor * H));

            }

            zoom_factor = factor;
        });
    }

    private void setComListeners() {
        initHoverTimers();

        // The "menu" icon (corner, always visible while collapsed) is the ONLY trigger that
        // DEPLOYS the bar.
        menu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (canShow()) {
                    showBar();
                }
            }
        });

        // The buttons (visible only while deployed) keep the bar alive while the mouse is over
        // them; leaving schedules the delayed collapse (1s wait + fade).
        for (Object[] b : botones) {
            ((Component) b[0]).addMouseListener(new MouseAdapter() {

                @Override
                public void mouseEntered(MouseEvent e) {
                    cancelHide();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    scheduleHide();
                }
            });
        }
    }

    // Creates the collapse timers (once). The DELAY timer, once the second elapses, starts the
    // fade ONLY if the pointer is still outside the bar (re-checked via MouseInfo, in case it
    // came back during the wait without a mouseEntered event). The FADE timer lowers opacity
    // frame by frame and, on reaching 0, collapses the bar (menu icon) and restores opacity for
    // next time.
    private void initHoverTimers() {
        hide_delay_timer = new javax.swing.Timer(HIDE_DELAY_MS, e -> {
            hide_delay_timer.stop();
            if (pointerOutsideBar()) {
                startFade();
            }
        });
        hide_delay_timer.setRepeats(false);

        fade_timer = new javax.swing.Timer(FADE_INTERVAL_MS, e -> {
            bar_opacity -= FADE_INTERVAL_MS / (float) FADE_MS;
            if (bar_opacity <= 0f) {
                fade_timer.stop();
                // hideButtons() restores opacity to 1 together with the collapse (same EDT
                // block), so the "menu" icon reappears at full opacity with no flash.
                hideButtons();
            } else {
                repaint();
            }
        });
    }

    // Deploys the bar from the "menu" icon: cancels any collapse in progress, restores opacity
    // and shows the buttons.
    private void showBar() {
        cancelHide();
        showButtons();
    }

    // Cancels the collapse in progress (delay or fade) and restores full opacity.
    private void cancelHide() {
        if (hide_delay_timer != null) {
            hide_delay_timer.stop();
        }
        if (fade_timer != null) {
            fade_timer.stop();
        }
        if (bar_opacity != 1f) {
            bar_opacity = 1f;
            repaint();
        }
    }

    /**
     * Starts (or leaves running) the 1s delay before the bar begins fading out.
     * No-op if already collapsed or already fading. Public because the table
     * panel calls it when the mouse enters it (a reliable "left the bar"
     * signal) instead of collapsing instantly.
     */
    public void scheduleHide() {
        if (!chat.isVisible() || (fade_timer != null && fade_timer.isRunning())) {
            return;
        }
        // Starts ONLY if not already running: the table panel fires mouseEntered every time the
        // mouse crosses one of its subcomponents, and restart() would keep pushing the second
        // back on every move. This way it counts 1s from when the mouse actually left the bar;
        // re-entering the bar cancels it via cancelHide().
        if (hide_delay_timer != null && !hide_delay_timer.isRunning()) {
            hide_delay_timer.start();
        }
    }

    private void startFade() {
        // Discard any button tooltip that might still be hanging around before fading starts.
        dismissActiveTooltip();
        bar_opacity = 1f;
        if (fade_timer != null) {
            fade_timer.start();
        }
    }

    // Dismisses any visible button tooltip. Swing does NOT hide the tooltip balloon when its
    // owning component is made invisible (and doesn't always deliver its MOUSE_EXITED), so
    // collapsing the bar could leave the balloon hanging. Disabling the ToolTipManager hides the
    // current tooltip; re-enabling it leaves tooltips working next time.
    private void dismissActiveTooltip() {
        javax.swing.ToolTipManager ttm = javax.swing.ToolTipManager.sharedInstance();
        ttm.setEnabled(false);
        ttm.setEnabled(true);
    }

    // Whether the pointer is outside the bar (or the bar isn't showing). Uses MouseInfo (screen
    // coordinates) so it doesn't depend on the event's own coordinate system.
    private boolean pointerOutsideBar() {
        if (!isShowing()) {
            return true;
        }
        java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
        if (pi == null) {
            return true;
        }
        return !new Rectangle(getLocationOnScreen(), getSize()).contains(pi.getLocation());
    }

    // Whether the bar is allowed to deploy: the game is still running and no fast-chat dialog is
    // open on top of it.
    private boolean canShow() {
        GameFrame gf = GameFrame.getInstance();
        return !gf.getCrupier().isFin_de_la_transmision() && (gf.getFastchat_dialog() == null || !gf.getFastchat_dialog().isVisible());
    }

    // The panel spans the DEPLOYED bar's width even while collapsed, and lives on the table's
    // POPUP_LAYER (above the seats). Collapsed, only the "menu" icon is visible, but a
    // transparent JPanel would still capture the mouse over its WHOLE rectangle, blocking
    // whatever is underneath (e.g. a player's action label, which highlights on hover). So the
    // hit-test is narrowed: collapsed, only the "menu" icon belongs to the panel and the rest
    // lets the mouse through to the table's components; deployed (chat visible), normal behavior.
    @Override
    public boolean contains(int x, int y) {
        if (!chat.isVisible()) {
            return menu.isVisible() && menu.getBounds().contains(x, y);
        }
        return super.contains(x, y);
    }

    // Paints the bar at the current opacity (for the fade). At full opacity it delegates
    // directly; otherwise the whole tree (icons included) is composited via an AlphaComposite.
    @Override
    public void paint(java.awt.Graphics g) {
        if (bar_opacity >= 1f) {
            super.paint(g);
            return;
        }
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, bar_opacity)));
            super.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    public void hideButtons() {
        Helpers.GUIRun(() -> {
            if (hide_delay_timer != null) {
                hide_delay_timer.stop();
            }
            if (fade_timer != null) {
                fade_timer.stop();
            }
            bar_opacity = 1f;
            // Discard any hanging tooltip before hiding the buttons (Swing won't do it on its own).
            dismissActiveTooltip();
            for (Object[] b : botones) {
                ((Component) b[0]).setVisible(false);
            }
            if (isEnabled()) {
                menu.setVisible(true);
            }
        });
    }

    private void showButtons() {

        if (zoom_factor != (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)) {
            zoomIcons(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
        }
        Helpers.GUIRun(() -> {
            for (Object[] b : botones) {

                if (((Component) b[0]) == image) {
                    ((Component) b[0]).setVisible(GameFrame.CHAT_IMAGES_INGAME);
                } else if (((Component) b[0]) == mic) {
                    ((Component) b[0]).setVisible(GameFrame.VOICE_MESSAGES);
                } else {
                    ((Component) b[0]).setVisible(true);
                }
            }

            // The "menu" icon is hidden BEFORE measuring: hiding it after would include its width
            // in the preferred size (it's first in the FlowLayout, so the buttons shift left once
            // it disappears), leaving an invisible strip one button wide on the right of the bar.
            // Inside that strip neither the panel's mouseExited nor the table's mouseEntered
            // fires, so the mouse would need to travel a whole extra button width before the exit
            // was detected and the collapse started.
            menu.setVisible(false);

            // Preferred size is compared by VALUE (previously with !=, comparing references, so
            // it was ALWAYS different). Only resize/reposition when it actually changed.
            Dimension pref = getPreferredSize();
            if (!pref.equals(getPref_size())) {
                pref_size = pref;
                setSize(pref_size);
                setLocation(0, (int) (GameFrame.getInstance().getTapete().getHeight() - getSize().getHeight()));
            }

            // The buttons just flipped from invisible to visible: relayout (FlowLayout) and
            // repaint ALWAYS, even if the preferred size didn't change. Without this the LAST
            // button in the FlowLayout (fullscreen, rightmost, last add()) could end up
            // unplaced or half-placed until a later repaint: it would appear late, mis-painted,
            // or not at all. Most visible in windowed mode, where the bar's right edge is the
            // first to suffer.
            revalidate();
            repaint();
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

        menu = new javax.swing.JLabel();
        chat = new javax.swing.JLabel();
        mic = new javax.swing.JLabel();
        image = new javax.swing.JLabel();
        rebuy = new javax.swing.JLabel();
        log = new javax.swing.JLabel();
        compact = new javax.swing.JLabel();
        zoom_out = new javax.swing.JLabel();
        zoom_reset = new javax.swing.JLabel();
        zoom_in = new javax.swing.JLabel();
        fullscreen = new javax.swing.JLabel();

        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        setFocusable(false);
        setOpaque(false);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                formMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                formMouseExited(evt);
            }
        });
        setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/menu.png"))); // NOI18N
        menu.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        add(menu);

        chat.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/chat.png"))); // NOI18N
        chat.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat.setFocusable(false);
        chat.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                chatMouseClicked(evt);
            }
        });
        add(chat);

        mic.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/mic.png"))); // NOI18N
        mic.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        mic.setFocusable(false);
        mic.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                micMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                micMouseReleased(evt);
            }
        });
        add(mic);

        image.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/image.png"))); // NOI18N
        image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        image.setFocusable(false);
        image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                imageMouseClicked(evt);
            }
        });
        add(image);

        rebuy.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/rebuy.png"))); // NOI18N
        rebuy.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy.setFocusable(false);
        rebuy.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                rebuyMouseClicked(evt);
            }
        });
        add(rebuy);

        log.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/log.png"))); // NOI18N
        log.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        log.setFocusable(false);
        log.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                logMouseClicked(evt);
            }
        });
        add(log);

        compact.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/compact.png"))); // NOI18N
        compact.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        compact.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                compactMouseClicked(evt);
            }
        });
        add(compact);

        zoom_out.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_out.png"))); // NOI18N
        zoom_out.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_out.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                zoom_outMouseClicked(evt);
            }
        });
        add(zoom_out);

        zoom_reset.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_reset.png"))); // NOI18N
        zoom_reset.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_reset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                zoom_resetMouseClicked(evt);
            }
        });
        add(zoom_reset);

        zoom_in.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_in.png"))); // NOI18N
        zoom_in.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_in.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                zoom_inMouseClicked(evt);
            }
        });
        add(zoom_in);

        fullscreen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/fullscreen.png"))); // NOI18N
        fullscreen.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fullscreen.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                fullscreenMouseClicked(evt);
            }
        });
        add(fullscreen);
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
        // Entering the panel (not the "menu" icon) does NOT deploy the bar: it only keeps an
        // already-deployed bar alive, cancelling a pending collapse.
        cancelHide();
    }//GEN-LAST:event_formMouseEntered

    private void formMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseExited
        scheduleHide();
    }//GEN-LAST:event_formMouseExited

    private void chatMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_chatMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().showFastChatDialog();
        }
    }//GEN-LAST:event_chatMouseClicked

    private void micMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_micMousePressed
        // Hold left button to record a voice note (same as holding the voice key)
        if (javax.swing.SwingUtilities.isLeftMouseButton(evt) && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            VoiceMessageManager.buttonPressed();
        }
    }//GEN-LAST:event_micMousePressed

    private void micMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_micMouseReleased
        // Release sends (or cancels if the talk-now dialog had not appeared yet)
        if (javax.swing.SwingUtilities.isLeftMouseButton(evt)) {

            VoiceMessageManager.buttonReleased();
        }
    }//GEN-LAST:event_micMouseReleased

    private void imageMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_imageMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().showFastChatImage();
        }
    }//GEN-LAST:event_imageMouseClicked

    private void compactMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_compactMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getCompact_menu().doClick();
        }

    }//GEN-LAST:event_compactMouseClicked

    private void zoom_outMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_outMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_out().doClick();
        }

    }//GEN-LAST:event_zoom_outMouseClicked

    private void zoom_resetMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_resetMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_reset().doClick();
        }
    }//GEN-LAST:event_zoom_resetMouseClicked

    private void zoom_inMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_inMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_in().doClick();
        }

    }//GEN-LAST:event_zoom_inMouseClicked

    private void fullscreenMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fullscreenMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getFull_screen_menu().doClick();
        }
    }//GEN-LAST:event_fullscreenMouseClicked

    private void rebuyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_rebuyMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getRebuy_now_menu().doClick();
        }

    }//GEN-LAST:event_rebuyMouseClicked

    private void logMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logMouseClicked
        // Left click only, like the other icons on this bar: this one used to also open the log
        // on a right click, which on the table is the menu button's click.
        if (!Helpers.isRealClick(evt)) {
            return;
        }

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getRegistro_menu().doClick();
        }

    }//GEN-LAST:event_logMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel chat;
    private javax.swing.JLabel compact;
    private javax.swing.JLabel fullscreen;
    private javax.swing.JLabel image;
    private javax.swing.JLabel log;
    private javax.swing.JLabel menu;
    private javax.swing.JLabel mic;
    private javax.swing.JLabel rebuy;
    private javax.swing.JLabel zoom_in;
    private javax.swing.JLabel zoom_out;
    private javax.swing.JLabel zoom_reset;
    // End of variables declaration//GEN-END:variables

    @Override
    public void zoom(float factor, ConcurrentLinkedQueue<Long> notifier) {

        zoomIcons(factor);

        if (notifier != null) {

            notifier.add(Thread.currentThread().getId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }
}
