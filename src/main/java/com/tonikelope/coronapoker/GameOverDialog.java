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
 *
 * @author tonikelope
 */
public class GameOverDialog extends JDialog {

    // Segundos del RebuyDialog para decidir la recompra (game-over, recompra
    // intra-partida y elección de buy-in al entrar al tablero comparten esta
    // misma cifra). Es también la cuenta atrás visual "¿RECOMPRA? (N)" que los
    // demás ven en el RemotePlayer del arruinado (RemotePlayer.setRebuying) y la
    // ventana que el host espera las respuestas (Crupier.recibirRebuys).
    public static final int REBUY_DIALOG_COUNTDOWN = 15;
    private volatile boolean continua = false;
    private volatile String last_mp3_loop = null;
    private volatile boolean direct_gameover = false;
    private volatile RebuyDialog buyin_dialog = null;
    private volatile boolean exit = false;

    // Sin cinemáticas el GIF de game over no debe reproducirse: en su lugar se pinta un
    // "GAME OVER" estático (rojo borde negro sobre fondo negro) con una CUENTA ATRÁS de
    // ALT_COUNTDOWN_SECONDS y SIN audio alguno (ni game_over.wav ni los efectos). Esa
    // cuenta atrás es además la ventana de decisión de este diálogo alternativo (al
    // llegar a 0 -> espectador). Los botones continuar/espectador son los mismos.
    // Congruente con el rótulo BARAJANDO del barajado sin gif.
    private static final int ALT_COUNTDOWN_SECONDS = 10;
    private final boolean cinematics_off = !GameFrame.cinematicasOn();
    private volatile int countdown_seconds = ALT_COUNTDOWN_SECONDS;
    private volatile javax.swing.Timer countdown_timer = null;

    public RebuyDialog getBuyin_dialog() {
        return buyin_dialog;
    }

    public boolean isContinua() {
        return continua;
    }

    /**
     * Creates new form Recomprar
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

    // Game over interactivo: con cinemáticas reproduce el GIF; sin cinemáticas pinta el
    // "GAME OVER" estático con la cuenta atrás actual (countdown_seconds).
    private void showGameOverActive() {
        if (cinematics_off) {
            gifPanel.setGifIcon(renderGameOverStatic(false, countdown_seconds), 782, 326);
        } else {
            gifPanel.setGifIcon(new ImageIcon(getClass().getResource("/cinematics/misc/game_over.gif")), 782, 326);
        }
    }

    // Estado final (sin recompra / se acabó la cuenta atrás): con cinemáticas el GIF
    // game_over_zero; sin cinemáticas el "GAME OVER" estático sin número. Para la cuenta.
    private void showGameOverFinal() {
        stopCountdown();
        if (cinematics_off) {
            gifPanel.setGifIcon(renderGameOverStatic(true, 0), 782, 326);
        } else {
            gifPanel.setGifIcon(new ImageIcon(getClass().getResource("/cinematics/misc/game_over_zero.gif")), 782, 326);
        }
    }

    // Arranca la cuenta atrás visual (1 Hz) del game over alternativo: cada tick baja el
    // número y repinta el "GAME OVER" estático. Es además la VENTANA de decisión (sin
    // game_over.wav): al llegar a 0, si no se ha elegido, queda en espectador
    // (onCountdownTimeout). Solo en game over interactivo sin cinemáticas.
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

    // Fin de la cuenta atrás del game over alternativo sin que se haya elegido: queda en
    // espectador y cierra. SIN audios (el usuario los quiere mudos en este diálogo); el
    // estado en espectador lo aplica Crupier al cerrarse el diálogo (isContinua()=false).
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

    // Pinta un lienzo 782x326 (mismo tamaño que el GIF) negro con "GAME OVER" centrado
    // arriba (rojo, borde negro) y, salvo en el estado final, el número de la cuenta
    // atrás debajo (blanco, borde negro). Sustituto NO animado del game_over.gif.
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

    // Texto centrado en (cx,cy) pintado como contorno (borde negro) + relleno, encogido
    // si hace falta para caber en max_width.
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

        // El game over (interactivo o directo) NO oculta la mesa: la caja del
        // GIF se pone encima de todo en el centro, pero jugadores y comunitarias
        // siguen vivos detrás (con las luces apagadas) y los arruinados remotos
        // muestran su GIF de game over a la vez. Antes aquí se hacía hideALL();
        // ya no — el local ve el estado de la mesa durante su game over.

        if (GameFrame.getInstance().getFastchat_dialog() != null) {
            GameFrame.getInstance().getFastchat_dialog().setVisible(false);
        }

        // El registro NO se oculta: si el usuario lo tiene abierto, se mantiene
        // visible durante el game over para poder leer el resultado de la mano
        // (el GIF va centrado encima; el game over es modal, asi que queda al
        // frente y el registro permanece detras/al lado, como lo haya colocado).

        if (GameFrame.getInstance().getJugadas_dialog() != null) {
            GameFrame.getInstance().getJugadas_dialog().setVisible(false);
        }

        if (GameFrame.getInstance().getShortcuts_dialog() != null) {
            GameFrame.getInstance().getShortcuts_dialog().setVisible(false);
        }

        continue_button.requestFocus();

        // Game over ALTERNATIVO (sin cinemáticas): SIN audios. No se silencia la música
        // de fondo ni se reproduce game_over.wav/efectos. La cuenta atrás de 10s es la
        // ventana de decisión (onCountdownTimeout -> espectador). El directo muestra el
        // "GAME OVER" estático un par de segundos y cierra.
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

        // Ya no se oculta la mesa al abrir el game over (ver formWindowOpened),
        // así que no hay visibilidad que restaurar al cerrarlo.

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

        // Game over alternativo (sin cinemáticas): mudo, sin el wav de recompra.
        if (!cinematics_off && GameFrame.finPartidaSonidoOn()) {
            Audio.playWavResource("misc/rebuy.wav");
        }

        dispose();

        // Jugador arruinado (stack 0): rango segun modo. En fijo [1, BUYIN]
        // default BUYIN (comportamiento de siempre); en variable, el rango de
        // buy-in configurado [getBuyinMin, getBuyinDefault]. El techo (cap) lo da
        // getBuyinCap segun la politica (BUYIN o stack mas alto), = headroom a 0.
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

        // Game over alternativo (sin cinemáticas): mudo, cierra directamente.
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
