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

import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.JDialog;

/**
 * Game-over dialog shown to a busted player: rebuy or go spectator. With
 * cinematics enabled it plays the game_over GIF and audio; with cinematics off
 * (per-setting or the global cinematics/animations master switch) it falls back
 * to a static "GAME OVER" render with its own silent countdown, since the GIF
 * itself carries the countdown when animated.
 *
 * @author tonikelope
 */
public class GameOverDialog extends JDialog {

    // Countdown (seconds) RebuyDialog gives to decide on a rebuy; shared by the game-over rebuy,
    // the mid-game rebuy and the initial buy-in choice when joining the table. Also the visual
    // "REBUY? (N)" countdown other players see on the busted player's RemotePlayer
    // (RemotePlayer.setRebuying), and how long the host waits for replies (Crupier.recibirRebuys).
    public static final int REBUY_DIALOG_COUNTDOWN = 15;
    private volatile boolean continua = false;
    private volatile String last_mp3_loop = null;
    private volatile boolean direct_gameover = false;
    private volatile RebuyDialog buyin_dialog = null;
    private volatile boolean exit = false;

    // Without the game-over cinematic (its own setting, or the cinematics/animations master
    // switch off) the game_over GIF must not play: a static "GAME OVER" (red, black outline, on
    // black) is drawn instead, with its own ALT_COUNTDOWN_SECONDS countdown and no audio at all
    // (neither game_over.wav nor the sfx). That countdown also doubles as this alternative
    // dialog's decision window (reaches 0 -> spectator). Same continue/spectator buttons either
    // way. Mirrors the "SHUFFLING" static label used when shuffling has no GIF either.
    private static final int ALT_COUNTDOWN_SECONDS = 10;
    private final boolean cinematics_off = !GameFrame.cinematicasGameOverOn();
    private volatile int countdown_seconds = ALT_COUNTDOWN_SECONDS;
    private volatile javax.swing.Timer countdown_timer = null;

    public RebuyDialog getBuyin_dialog() {
        return buyin_dialog;
    }

    public boolean isContinua() {
        return continua;
    }

    /**
     * Creates an interactive game-over dialog (rebuy or spectator choice).
     *
     * @param parent owning game window
     * @param modal whether the dialog blocks input to its owner
     */
    public GameOverDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        if (GameFrame.getInstance().getRebuy_dialog() != null) {
            GameFrame.getInstance().getRebuy_dialog().dispose();
        }

        continue_button.requestFocus();

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        showGameOverActive();

        pack();
    }

    /**
     * Creates a game-over dialog.
     *
     * @param parent owning game window
     * @param modal whether the dialog blocks input to its owner
     * @param direct if true, shows the final (no-rebuy) state right away with
     * both buttons disabled, instead of the interactive countdown
     */
    public GameOverDialog(java.awt.Frame parent, boolean modal, boolean direct) {
        super(parent, modal);

        initComponents();

        if (GameFrame.getInstance().getRebuy_dialog() != null) {
            GameFrame.getInstance().getRebuy_dialog().dispose();
        }

        direct_gameover = direct;

        continue_button.requestFocus();

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        if (direct_gameover) {
            spectator_button.setEnabled(false);
            continue_button.setEnabled(false);
            showGameOverFinal();
        } else {
            showGameOverActive();
        }

        pack();
    }

    // Interactive game-over: with cinematics, plays the GIF; without, draws the static
    // "GAME OVER" with the current countdown (countdown_seconds).
    private void showGameOverActive() {
        if (cinematics_off) {
            gifPanel.setGifIcon(renderGameOverStatic(false, countdown_seconds), 782, 326);
        } else {
            gifPanel.setGifIcon(new ImageIcon(getClass().getResource("/cinematics/misc/game_over.gif")), 782, 326);
        }
    }

    // Final state (no rebuy / countdown expired): with cinematics, the game_over_zero GIF;
    // without, the static "GAME OVER" with no number. Also stops the countdown.
    private void showGameOverFinal() {
        stopCountdown();
        if (cinematics_off) {
            gifPanel.setGifIcon(renderGameOverStatic(true, 0), 782, 326);
        } else {
            gifPanel.setGifIcon(new ImageIcon(getClass().getResource("/cinematics/misc/game_over_zero.gif")), 782, 326);
        }
    }

    // Starts the visual 1 Hz countdown for the alternative (no-cinematics) game-over: each tick
    // decrements the number and redraws the static "GAME OVER". This is also the decision
    // WINDOW (no game_over.wav involved): if nothing was chosen by 0, it falls back to spectator
    // (onCountdownTimeout). Only used for the interactive game-over without cinematics.
    private void startCountdown() {
        if (!cinematics_off || direct_gameover) {
            return;
        }
        stopCountdown();
        countdown_timer = new javax.swing.Timer(1000, (e) -> {
            countdown_seconds = Math.max(0, countdown_seconds - 1);
            showGameOverActive();
            if (countdown_seconds <= 0) {
                ((javax.swing.Timer) e.getSource()).stop();
                onCountdownTimeout();
            }
        });
        countdown_timer.setInitialDelay(1000);
        countdown_timer.start();
    }

    private void stopCountdown() {
        if (countdown_timer != null) {
            countdown_timer.stop();
            countdown_timer = null;
        }
    }

    // Alternative game-over countdown expired with no choice made: falls back to spectator and
    // closes. No audio here (this fallback dialog is meant to stay silent); Crupier applies the
    // spectator state once the dialog closes (isContinua() == false).
    private void onCountdownTimeout() {
        if (continua || exit) {
            return;
        }
        exit = true;
        spectator_button.setEnabled(false);
        continue_button.setEnabled(false);
        showGameOverFinal();
        dispose();
    }

    // Draws a 782x326 canvas (same size as the GIF), black background, "GAME OVER" centered
    // near the top (red, black outline), and, unless it's the final state, the countdown number
    // below it (white, black outline). Non-animated stand-in for game_over.gif.
    private javax.swing.ImageIcon renderGameOverStatic(boolean zero, int seconds) {
        final int w = 782;
        final int h = 326;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        try {
            g2.setColor(java.awt.Color.BLACK);
            g2.fillRect(0, 0, w, h);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean show_number = !zero && seconds > 0;
            double game_over_cy = show_number ? h * 0.34 : h * 0.5;

            drawOutlinedCentered(g2, "GAME OVER", new java.awt.Font("Dialog", java.awt.Font.BOLD, 120),
                    new java.awt.Color(220, 30, 30), w / 2.0, game_over_cy, w * 0.92);

            if (show_number) {
                drawOutlinedCentered(g2, String.valueOf(seconds), new java.awt.Font("Dialog", java.awt.Font.BOLD, 130),
                        java.awt.Color.WHITE, w / 2.0, h * 0.74, w * 0.92);
            }
        } finally {
            g2.dispose();
        }
        return new javax.swing.ImageIcon(img);
    }

    // Draws text centered at (cx,cy) as an outline (black stroke) + fill, shrunk to fit
    // max_width if needed.
    private static void drawOutlinedCentered(java.awt.Graphics2D g2, String text, java.awt.Font font, java.awt.Color fill, double cx, double cy, double max_width) {
        java.awt.font.FontRenderContext frc = g2.getFontRenderContext();
        java.awt.font.TextLayout tl = new java.awt.font.TextLayout(text, font, frc);
        double tw = tl.getAdvance();
        if (tw > max_width && tw > 0) {
            font = font.deriveFont((float) (font.getSize2D() * max_width / tw));
            tl = new java.awt.font.TextLayout(text, font, frc);
        }
        java.awt.geom.Rectangle2D b = tl.getBounds();
        double x = cx - b.getWidth() / 2.0 - b.getX();
        double y = cy - b.getHeight() / 2.0 - b.getY();
        java.awt.Shape outline = tl.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));
        g2.setStroke(new java.awt.BasicStroke(Math.max(2f, font.getSize2D() * 0.06f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g2.setColor(java.awt.Color.BLACK);
        g2.draw(outline);
        g2.setColor(fill);
        g2.fill(outline);
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
        gifPanel = new com.tonikelope.coronapoker.GifPanel(false);
        continue_button = new javax.swing.JButton();
        spectator_button = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setModal(true);
        setUndecorated(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        jPanel1.setBackground(new java.awt.Color(0, 0, 0));

        gifPanel.setPreferredSize(new java.awt.Dimension(782, 326));

        continue_button.setFont(new java.awt.Font("Dialog", 1, 60)); // NOI18N
        continue_button.setIcon(new ImageIcon(getClass().getResource("/images/gameover/continue_"+com.tonikelope.coronapoker.GameFrame.LANGUAGE+".png")));
        continue_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        continue_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                continue_buttonActionPerformed(evt);
            }
        });

        spectator_button.setIcon(new ImageIcon(getClass().getResource("/images/gameover/espectador_"+com.tonikelope.coronapoker.GameFrame.LANGUAGE+".png"))
        );
        spectator_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        spectator_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                spectator_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(gifPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 532, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(continue_button)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(10, 10, 10))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(spectator_button)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(gifPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(continue_button)
                .addGap(18, 18, 18)
                .addComponent(spectator_button)
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

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        // TODO add your handling code here:
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            continue_button.doClick();
        }
    }//GEN-LAST:event_formKeyPressed

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:

        // Game-over (interactive or direct) no longer hides the table: the GIF box overlays
        // everything centered, but players and community cards stay visible behind it (dimmed),
        // and busted remote players show their own game-over GIF at the same time. This used to
        // call hideALL() — no longer, so the local player can still see the table during theirs.
        if (GameFrame.getInstance().getFastchat_dialog() != null) {
            GameFrame.getInstance().getFastchat_dialog().setVisible(false);
        }

        // The game log (GameLogDialog) is intentionally left alone here: if the user has it
        // open, it stays visible during game-over so they can still read the hand result (the
        // GIF is centered on top; game-over is modal so it's frontmost, and the log stays
        // behind/beside it wherever the user placed it).
        if (GameFrame.getInstance().getJugadas_dialog() != null) {
            GameFrame.getInstance().getJugadas_dialog().setVisible(false);
        }

        if (GameFrame.getInstance().getShortcuts_dialog() != null) {
            GameFrame.getInstance().getShortcuts_dialog().setVisible(false);
        }

        continue_button.requestFocus();

        // Alternative game-over (no cinematics): no audio at all — background music isn't
        // muted and game_over.wav/sfx don't play. The 10s countdown is the decision window
        // (onCountdownTimeout -> spectator). Direct game-over shows the static "GAME OVER"
        // for a couple seconds and closes.
        if (cinematics_off) {
            if (!direct_gameover && !continua) {
                startCountdown();
            } else if (!continua) {
                Helpers.threadRun(() -> {
                    Helpers.parkThreadMillis(2500);
                    Helpers.GUIRun(this::dispose);
                });
            }
            return;
        }

        Helpers.threadRun(() -> {
            last_mp3_loop = Audio.getCurrentLoopMp3Playing();
            if (GameFrame.SONIDOS && last_mp3_loop != null && !Audio.MP3_LOOP_MUTED.contains(last_mp3_loop)) {
                Audio.muteLoopMp3(last_mp3_loop);
            } else {
                last_mp3_loop = null;
            }
            if (!direct_gameover && !continua) {
                Audio.playWavResourceAndWait("misc/game_over.wav", true, false, !GameFrame.finPartidaSonidoOn());
                if (!continua && !exit) {
                    Helpers.GUIRun(() -> {
                        spectator_button.setEnabled(false);
                        continue_button.setEnabled(false);
                        showGameOverFinal();
                    });
                    Audio.playWavResourceAndWait("misc/nocontinue.wav", true, false, !GameFrame.finPartidaSonidoOn());
                    if (GameFrame.SONIDOS && GameFrame.SONIDOS_CHORRA) {
                        Audio.playWavResourceAndWait("misc/norebuy.wav");
                    }
                    Helpers.GUIRun(this::dispose);
                }
            } else if (!continua) {
                if (GameFrame.SONIDOS && GameFrame.SONIDOS_CHORRA) {
                    Audio.playWavResourceAndWait("misc/norebuy.wav");
                }
                Helpers.GUIRun(this::dispose);
            }
        });
    }//GEN-LAST:event_formWindowOpened

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        // TODO add your handling code here:

        // The table is no longer hidden when game-over opens (see formWindowOpened), so
        // there's no visibility state to restore here on close.
        stopCountdown();

        if (last_mp3_loop != null) {
            Audio.unmuteLoopMp3(last_mp3_loop);
        }
    }//GEN-LAST:event_formWindowClosed

    private void continue_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_continue_buttonActionPerformed
        // TODO add your handling code here:
        this.continua = true;

        this.exit = true;

        Audio.stopWavResource("misc/game_over.wav");

        // Alternative game-over (no cinematics): silent, skips the rebuy wav.
        if (!cinematics_off && GameFrame.finPartidaSonidoOn()) {
            Audio.playWavResource("misc/rebuy.wav");
        }

        dispose();

        // Busted player (stack 0): range depends on the buy-in mode. Fixed buy-in uses
        // [1, BUYIN] defaulting to BUYIN (the historical behavior); variable buy-in uses the
        // configured range [getBuyinMin, getBuyinDefault]. getBuyinCap gives the ceiling per
        // policy (BUYIN or the highest stack), i.e. headroom down to 0.
        int rebuy_min = GameFrame.FIXED_BUYIN ? 1 : GameFrame.getBuyinMin();
        int rebuy_max = GameFrame.getBuyinCap();
        int rebuy_def = GameFrame.FIXED_BUYIN ? GameFrame.BUYIN : GameFrame.getBuyinDefault();
        buyin_dialog = new RebuyDialog(GameFrame.getInstance(), true, false, REBUY_DIALOG_COUNTDOWN, rebuy_min, rebuy_max, rebuy_def);

        buyin_dialog.setLocationRelativeTo(buyin_dialog.getParent());

        buyin_dialog.setVisible(true);
    }//GEN-LAST:event_continue_buttonActionPerformed

    private void spectator_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spectator_buttonActionPerformed
        // TODO add your handling code here:

        this.exit = true;

        spectator_button.setEnabled(false);
        continue_button.setEnabled(false);
        showGameOverFinal();

        // Alternative game-over (no cinematics): silent, closes right away.
        if (cinematics_off) {
            dispose();
            return;
        }

        Helpers.threadRun(() -> {
            Audio.stopWavResource("misc/game_over.wav");
            Audio.playWavResourceAndWait("misc/nocontinue.wav", true, false, !GameFrame.finPartidaSonidoOn());
            if (GameFrame.SONIDOS && GameFrame.SONIDOS_CHORRA) {
                Audio.playWavResourceAndWait("misc/norebuy.wav");
            }
            Helpers.GUIRun(this::dispose);
        });

    }//GEN-LAST:event_spectator_buttonActionPerformed

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
    private javax.swing.JButton continue_button;
    private com.tonikelope.coronapoker.GifPanel gifPanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton spectator_button;
    // End of variables declaration//GEN-END:variables
}
