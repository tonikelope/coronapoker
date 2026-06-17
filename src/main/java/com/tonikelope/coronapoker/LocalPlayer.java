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

import static com.tonikelope.coronapoker.GameFrame.GUI_RENDER_WAIT;
import static com.tonikelope.coronapoker.GameFrame.NOTIFY_INGAME_GIF_REPEAT;
import static com.tonikelope.coronapoker.GameFrame.TTS_NO_SOUND_TIMEOUT;
import static com.tonikelope.coronapoker.RemotePlayer.RERAISE_BACK_COLOR;
import static com.tonikelope.coronapoker.RemotePlayer.RERAISE_FORE_COLOR;
import static com.tonikelope.coronapoker.GifLabel.GIF_BARRIER_TIMEOUT;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

/**
 *
 * @author tonikelope
 */
public class LocalPlayer extends JPanel implements ZoomableInterface, Player {

    public static String[][] getActionsLabels() {
        return new String[][]{
            new String[]{Translator.translate("action.label.fold")},
            new String[]{Translator.translate("action.label.check"), Translator.translate("action.label.call")},
            new String[]{Translator.translate("action.label.bet"), Translator.translate("action.label.raise")},
            new String[]{Translator.translate("action.label.allin")}
        };
    }

    public static String[][] ACTIONS_LABELS = getActionsLabels();
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.YELLOW, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 550;
    public static final int MIN_ACTION_HEIGHT = 45;

    private final ConcurrentHashMap<JButton, Color[]> action_button_colors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JButton, Boolean> action_button_armed = new ConcurrentHashMap<>();
    private final Object pre_pulsar_lock = new Object();
    private final Object zoom_lock = new Object();
    private final Object rabbit_lock = new Object();

    private volatile String nickname;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile float stack = 0f;
    private volatile float bet = 0f;
    private volatile boolean utg = false;
    private volatile int decision = Player.NODEC;
    private volatile boolean spectator = false;
    private volatile float pagar = 0f;
    // Línea base de 'pagar' al empezar la CARA actual del run-it-twice (0 en
    // CARA-A, el total de CARA-A al entrar en CARA-B). El dinero ganado en la
    // cara es 'pagar - pagar_face_base', derivado de la única contabilidad real
    // (pagar), así que no puede desincronizarse. Fuera de RIT no se usa.
    private volatile float pagar_face_base = 0f;
    private volatile float bote = 0f;
    private volatile Float last_bote = null;
    private volatile boolean exit = false;
    private volatile boolean turno = false;
    private volatile Timer auto_action = null;
    private volatile AutoActionDialog auto_action_dialog = null;
    private volatile boolean timeout = false;
    private volatile boolean boton_mostrar = false;
    private volatile boolean winner = false;
    private volatile boolean loser = false;
    private volatile Float apuesta_recuperada = null;
    private volatile boolean click_recuperacion = false;
    private volatile float call_required;
    private volatile float min_raise;
    private volatile int pre_pulsado = Player.NODEC;
    private volatile boolean muestra = false;
    private volatile int parguela_counter = GameFrame.PEPILLO_COUNTER_MAX;
    private volatile int pause_counter = GameFrame.PAUSE_COUNTER_MAX;
    private volatile boolean auto_pause = false;
    private volatile boolean auto_pause_warning = false;
    private volatile Timer hurryup_timer = null;
    private volatile int response_counter = 0;
    private volatile boolean spectator_bb = false;
    private volatile Color border_color = null;
    private volatile boolean player_stack_click = false;
    private volatile String player_action_icon = null;
    private volatile Timer icon_zoom_timer = null;
    private volatile URL chat_notify_image_url = null;
    private volatile Long chat_notify_thread = null;
    private final GifLabel chat_notify_label = new GifLabel();
    private final JLabel chip_label = new JLabel();
    private final JLabel sec_pot_win_label = new JLabel();
    private final ConcurrentLinkedQueue<Integer> botes_secundarios = new ConcurrentLinkedQueue<>();
    private volatile boolean reraise;
    private volatile int conta_win = 0;
    private volatile int conta_rabbit = 0;

    private volatile float border_size = Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
    private volatile float arc = Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
    // Cached BasicStroke for paintBorder; rebuilt only when border_size changes (zoom).
    private float cached_stroke_size = -1f;
    private BasicStroke cached_stroke = null;

    public void stopActionTimer() {
        Helpers.GUIRun(() -> {
            if (auto_action != null && auto_action.isRunning()) {
                auto_action.stop();
            }
            if (hurryup_timer != null) {
                hurryup_timer.stop();
            }
            // NO matar icon_zoom_timer aquí: stopActionTimer se llama entre
            // manos y matar el timer del zoom dejaba la siguiente mano sin
            // setAvatar (timer ya parado → zoomIcons no dispara → avatar
            // invisible). El leak GC que justificaba el stop es preferible
            // a un bug visible.
        });
    }

    // Telemetría: el widget LatencyDot lo coloca el autor en el
    // .form (NetBeans visual editor) y lo enlaza llamando setLatencyDot.
    private volatile LatencyDot latency_dot = null;

    public LatencyDot getLatencyDot() {
        return latency_dot;
    }

    public void setLatencyDot(LatencyDot dot) {
        this.latency_dot = dot;
    }

    /**
     * Telemetría: actualiza la bolita LatencyDot. No-op si aún no
     * se ha enlazado vía setLatencyDot.
     */
    public void applyTelemetry(int lat1, int lat2, int reconnectionCount) {
        LatencyDot dot = this.latency_dot;
        if (dot == null) {
            return;
        }
        int best;
        if (lat1 < 0 && lat2 < 0) {
            best = -1;
        } else if (lat1 < 0) {
            best = lat2;
        } else if (lat2 < 0) {
            best = lat1;
        } else {
            best = Math.min(lat1, lat2);
        }
        dot.setLatency(best, reconnectionCount);
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (isOpaque()) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            } finally {
                g2d.dispose();
            }
            // Bypass super.paintComponent on the opaque branch: the rounded fill above
            // replaces the default rectangular background.
        } else {
            super.paintComponent(g);
        }
    }

    private BasicStroke borderStroke() {
        if (cached_stroke == null || cached_stroke_size != border_size) {
            cached_stroke = new BasicStroke(border_size);
            cached_stroke_size = border_size;
        }
        return cached_stroke;
    }

    @Override
    protected void paintBorder(Graphics g) {

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(border_color);
            g2d.setStroke(borderStroke());
            g2d.draw(new RoundRectangle2D.Double(
                    border_size / 2.0,
                    border_size / 2.0,
                    getWidth() - border_size,
                    getHeight() - border_size,
                    arc,
                    arc
            ));
        } finally {
            g2d.dispose();
        }
    }

    public void setConta_rabbit(int conta_rabbit) {
        synchronized (rabbit_lock) {
            this.conta_rabbit = conta_rabbit;
        }
    }

    public int getConta_rabbit() {
        synchronized (rabbit_lock) {
            return conta_rabbit;
        }
    }

    public void incrementContaRabbit() {
        synchronized (rabbit_lock) {
            conta_rabbit++;
        }
    }

    public void setActionBackground(Color color) {

        Helpers.GUIRun(() -> {
            player_action_panel.setBackground(color);
        });

    }

    public void setPlayerPotBackground(Color color) {

        Helpers.GUIRun(() -> {
            player_pot_panel.setBackground(color);
        });

    }

    public void setPlayerStackBackground(Color color) {
        Helpers.GUIRun(() -> {
            player_stack_panel.setBackground(color);

        });
    }

    public void setRabbitJugada(String jugada) {
        Helpers.GUIRun(() -> {
            setPlayerActionIcon("action/rabbit_action.png");
            setActionBackground(Color.BLUE);
            getPlayer_action().setForeground(Color.WHITE);
            setActionTextFitted(jugada);

        });
    }

    public void refreshNotifyChatLabel() {
        Helpers.GUIRun(() -> {
            if (getChat_notify_label().isVisible()) {
                Helpers.threadRun(() -> {
                    if (chat_notify_image_url != null) {
                        setNotifyImageChatLabel(chat_notify_image_url);
                    } else {
                        setNotifyTTSChatLabel();
                    }
                });
            }
        });

    }

    @Override
    public void setNotifyTTSChatLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        int sound_icon_size_h = Math.round(getHoleCard1().getHeight() / 2);

        int sound_icon_size_w = Math.round((596 * sound_icon_size_h) / 460);

        ImageIcon image = new ImageIcon(new ImageIcon(getClass().getResource("/images/talk.png")).getImage().getScaledInstance(sound_icon_size_w, sound_icon_size_h, Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {

            int pos_x = panel_cartas.getWidth() - sound_icon_size_w;

            int pos_y = Math.round(getHoleCard1().getHeight() / 2);

            getChat_notify_label().setIcon(image);

            getChat_notify_label().setSize(sound_icon_size_w, sound_icon_size_h);

            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

            getChat_notify_label().setOpaque(false);

            getChat_notify_label().setLocation(pos_x, pos_y);

        });
    }

    @Override
    public void setNotifyImageChatLabel(URL u) {

        try {

            chat_notify_image_url = u;

            final boolean isgif = (ChatImageDialog.GIF_CACHE.containsKey(u.toString()) || Helpers.isImageGIF(u));

            final CyclicBarrier gif_barrier = new CyclicBarrier(2);

            getChat_notify_label().setBarrier(gif_barrier);

            Helpers.threadRun(() -> {

                synchronized (getChat_notify_label()) {

                    chat_notify_thread = Thread.currentThread().threadId();

                    getChat_notify_label().notifyAll();

                    try {

                        final ImageIcon orig = ImageCacheManager.getIcon(new URL(u.toString() + "#" + String.valueOf(System.currentTimeMillis())));

                        while (orig.getIconHeight() == 0 || orig.getIconWidth() == 0) {

                            Helpers.pausar(GUI_RENDER_WAIT);
                        }

                        int max_width = panel_cartas.getWidth();

                        int max_height = Math.round(getHoleCard1().getHeight() / 2);

                        int new_height = max_height;

                        int new_width = (int) Math.round((orig.getIconWidth() * max_height) / orig.getIconHeight());

                        if (new_width > max_width) {

                            new_height = (int) Math.round((new_height * max_width) / new_width);

                            new_width = max_width;
                        }

                        final ImageIcon image = new ImageIcon(orig.getImage().getScaledInstance(new_width, new_height, isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));

                        int pos_x = panel_cartas.getWidth() - image.getIconWidth();

                        int pos_y = Math.round(getHoleCard1().getHeight() / 2);

                        int gif_frames_count = isgif ? Helpers.getGIFFramesCount(u) : 0;

                        Helpers.GUIRun(() -> {
                            if (isgif) {
                                getChat_notify_label().setIcon(image, gif_frames_count);
                            } else {
                                getChat_notify_label().setIcon(image);
                            }
                            getChat_notify_label().setRepeat(NOTIFY_INGAME_GIF_REPEAT);
                            getChat_notify_label().setSize(image.getIconWidth(), image.getIconHeight());
                            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());
                            getChat_notify_label().setOpaque(false);
                            getChat_notify_label().setLocation(pos_x, pos_y);
                            getChat_notify_label().setVisible(true);

                        });

                    } catch (Exception ex) {
                        Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                if (isgif) {

                    try {
                        gif_barrier.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                    } catch (InterruptedException | java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException ex) {
                        Helpers.logCooperativeCancellation(Logger.getLogger(GifAnimationDialog.class.getName()),
                                "local chat GIF barrier", ex);
                    } catch (Exception ex) {
                        Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    synchronized (getChat_notify_label()) {
                        if (Thread.currentThread().threadId() == chat_notify_thread) {
                            try {
                                getChat_notify_label().wait(TTS_NO_SOUND_TIMEOUT);
                            } catch (InterruptedException ex) {
                                Helpers.logCooperativeCancellation(Logger.getLogger(GifAnimationDialog.class.getName()),
                                        "local chat notify wait", ex);
                            } catch (Exception ex) {
                                Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }

                synchronized (getChat_notify_label()) {

                    if (Thread.currentThread().threadId() == chat_notify_thread) {
                        Helpers.GUIRunAndWait(() -> {
                            getChat_notify_label().setVisible(false);
                        });
                    }
                }
            });

        } catch (Exception ex) {
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public JLabel getChip_label() {
        return chip_label;
    }

    public JLayeredPane getPanel_cartas() {
        return panel_cartas;
    }

    // La ficha local reposa abajo-izquierda de la primera hole card (mismo
    // anclaje que refreshPositionChipIcons): (0, holeCard1.height - chip.height)
    // dentro de panel_cartas. Devuelve su centro en pantalla, o null si el
    // asiento no está visible.
    @Override
    public java.awt.geom.Point2D getPositionChipScreenCenter(int chip_w, int chip_h) {
        if (panel_cartas == null || !panel_cartas.isShowing()) {
            return null;
        }
        java.awt.Point tl = new java.awt.Point(0, getHoleCard1().getHeight() - chip_h);
        javax.swing.SwingUtilities.convertPointToScreen(tl, panel_cartas);
        return new java.awt.geom.Point2D.Double(tl.getX() + chip_w / 2.0, tl.getY() + chip_h / 2.0);
    }

    public boolean isBotonMostrarActivado() {
        return getPlayer_allin_button().isEnabled() && isBoton_mostrar();
    }

    public boolean isTimeout() {
        return timeout;
    }

    public JSpinner getBet_spinner() {
        return bet_spinner;
    }

    public int getResponseTime() {

        return Crupier.TIEMPO_PENSAR - response_counter;
    }

    public Timer getAuto_action() {
        return auto_action;
    }

    public AutoActionDialog getAuto_action_dialog() {
        return auto_action_dialog;
    }

    public Timer getHurryup_timer() {
        return hurryup_timer;
    }

    public boolean isAuto_pause_warning() {
        return auto_pause_warning;
    }

    public void setAuto_pause_warning(boolean auto_pause_warning) {
        this.auto_pause_warning = auto_pause_warning;
    }

    public boolean isAuto_pause() {
        return auto_pause;
    }

    public void setAuto_pause(boolean auto_pause) {
        this.auto_pause = auto_pause;
    }

    public int getPause_counter() {
        return pause_counter;
    }

    public void setPause_counter(int pause_counter) {
        this.pause_counter = pause_counter;
    }

    @Override
    public boolean isTurno() {
        return turno;
    }

    public int getParguela_counter() {
        return parguela_counter;
    }

    public void updateParguela_counter() {
        this.parguela_counter--;
    }

    public void setClick_recuperacion(boolean click_recuperacion) {
        this.click_recuperacion = click_recuperacion;
    }

    public void setApuesta_recuperada(Float apuesta_recuperada) {
        this.apuesta_recuperada = apuesta_recuperada;
    }

    public JButton getPlayer_allin_button() {
        return player_allin_button;
    }

    public JButton getPlayer_check_button() {
        return player_check_button;
    }

    public JButton getPlayer_fold_button() {
        return player_fold_button;
    }

    public void setMuestra(boolean muestra) {
        this.muestra = muestra;
    }

    public boolean isMuestra() {
        return muestra;
    }

    public boolean isWinner() {
        return winner;
    }

    public boolean isLoser() {
        return loser;
    }

    public void refreshPositionChipIcons() {

        ImageIcon chip_label_icon;

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_BB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_SB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource(GameFrame.getInstance().getCrupier().isDead_dealer() ? "/images/dead_dealer.png" : "/images/dealer.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = GameFrame.getInstance().getCrupier().isDead_dealer() ? Helpers.IMAGEN_DEAD_DEALER : Helpers.IMAGEN_DEALER;
        } else {
            chip_label_icon = null;
        }

        Helpers.GUIRun(() -> {
            if (isActivo() && chip_label_icon != null) {
                chip_label.setIcon(chip_label_icon);
                chip_label.setSize(chip_label.getIcon().getIconWidth(), chip_label.getIcon().getIconHeight());
                chip_label.setLocation(0, getHoleCard1().getHeight() - chip_label.getHeight());
                chip_label.setVisible(GameFrame.LOCAL_POSITION_CHIP);

                chip_label.repaint();

            } else {
                chip_label.setVisible(false);
            }
        });

    }

    public void activar_boton_mostrar(boolean parguela) {

        boton_mostrar = true;

        desactivarControles();

        Helpers.GUIRun(() -> {
            if (parguela) {
                player_allin_button.setText(Translator.translate("action.mostrar") + " (" + parguela_counter + ")");
            } else {
                player_allin_button.setText(Translator.translate("action.mostrar"));

            }
            player_allin_button.setIcon(null);
            player_allin_button.setForeground(Color.WHITE);
            player_allin_button.setBackground(new Color(51, 153, 255));
            player_allin_button.setEnabled(true);

            if (GameFrame.TEST_MODE) {
                player_allin_button.doClick();
            }
        });

    }

    @Override
    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            Helpers.GUIRun(() -> {
                desactivarControles();
                setOpaque(false);
                setBackground(null);
                setPlayerBorder(new Color(204, 204, 204, 75));

                player_pot.setText("----");
                player_pot.setForeground(Color.white);
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                utg_icon.setVisible(false);
                holeCard1.resetearCarta();
                holeCard2.resetearCarta();
                player_name.setOpaque(false);
                player_name.setBackground(null);
                player_name.setIcon(null);
                chip_label.setVisible(false);
                sec_pot_win_label.setVisible(false);

                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);
                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));
                    player_stack.setForeground(Color.WHITE);
                }

                player_stack.setText(Helpers.float2String(stack));

                if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                    player_name.setForeground(Color.YELLOW);
                } else {
                    player_name.setForeground(Color.WHITE);
                }

                setAuto_pause(false);
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setBackground(null);
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setForeground(null);

                if (!GameFrame.getInstance().isPartida_local()) {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setVisible(false);
                }

                disablePlayerAction();
            });

            Helpers.runWhenLaidOut(player_name, () -> {
                if (isSpectator()) {
                    setActionTextFitted(msg != null ? msg : Translator.translate("player.espectador"));
                    setPlayerActionIcon(Helpers.float1DSecureCompare(0f, getEffectiveStack()) == 0 ? "action/ghost.png" : "action/calentando.png");
                }
            });

        }
    }

    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(() -> {
            setPlayerBorder(new Color(204, 204, 204, 75));
            player_name.setIcon(null);
            player_stack.setEnabled(true);
            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setVisible(true);
            disablePlayerAction();

        });

    }

    public void desactivar_boton_mostrar() {

        if (boton_mostrar) {
            boton_mostrar = false;

            Helpers.GUIRun(() -> {
                player_allin_button.setText(" ");
                player_allin_button.setEnabled(false);
                player_allin_button.setBackground(Color.BLACK);
                player_allin_button.setForeground(Color.WHITE);
            });
        }
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    public void setTimeout(boolean val) {

        if (this.timeout != val) {

            this.timeout = val;

            Helpers.GUIRun(() -> {
                if (val) {

                    setPlayerBorder(Color.MAGENTA);
                    setPlayerActionIcon("action/timeout.png");
                } else {
                    setPlayerBorder(border_color != null ? border_color : new java.awt.Color(204, 204, 204, 75));
                    setPlayerActionIcon(player_action_icon);
                }
            });

            if (val) {

                Audio.playWavResource("misc/network_error_" + GameFrame.LANGUAGE.toLowerCase() + ".wav");
            }
        }

    }

    private void setPlayerBorder(Color color) {

        if (!timeout) {
            border_color = color;
        }

        repaint();

    }

    public JLabel getAvatar() {
        return avatar;
    }

    public synchronized float getPagar() {
        return pagar;
    }

    public synchronized float getBote() {
        return bote;
    }

    public synchronized boolean isExit() {
        return exit;
    }

    public synchronized void setExit() {

        if (!this.exit) {
            this.exit = true;
            this.timeout = false;

            desactivarControles();

            Helpers.GUIRun(() -> {
                setPlayerBorder(new Color(204, 204, 204, 75));

                // Hole cards are deliberately NOT reset here (same criterion as
                // RemotePlayer.setExit): if the hand is still live with the pot
                // already committed (all-in run-out, run-it-twice side boards),
                // the Card model must reach calcularJugadas intact or the
                // showdown mucks a legitimate hand and gives the pot away. If
                // betting action is still pending, the engine's fold path does
                // its own visual reset; the next-hand board reset purges
                // everything anyway.
                setActionBackground(new Color(255, 102, 0));
                player_action.setForeground(Color.WHITE);
                setActionTextFitted(Translator.translate("game.abandonas_la_timba"));
                setPlayerActionIcon("exit.png");
                player_action.setVisible(true);
                chip_label.setVisible(false);
                sec_pot_win_label.setVisible(false);
                // Al abandonar, el overlay de coste de igualar ya no aplica: ocultarlo
                // (no se refrescaría solo porque el local sale del bucle de apuestas).
                GameFrame.getInstance().getTapete().hideCallCostOverlay();
            });
        }
    }

    @Override
    public boolean isSpectator() {
        return this.spectator;
    }

    public int getDecision() {
        return decision;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public void setNickname(String nickname) {
        this.nickname = nickname;

        Helpers.GUIRun(() -> {
            player_name.setText(nickname);

            if (GameFrame.getInstance().isPartida_local()) {
                player_name.setForeground(Color.YELLOW);
            }

            // Own identity identicon (Ed25519 public key): right-click the avatar.
            // The handler works in both roles, so the affordance (tooltip + hand
            // cursor) is shown for host and client alike.
            Helpers.setTranslatedToolTip(avatar, "ui.click_identity_identicon");
            avatar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        });
    }

    public synchronized float getStack() {
        return stack;
    }

    public synchronized void setStack(float stack) {
        this.stack = Helpers.floatClean(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(() -> {
                if (getNickname() != null && GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                    setPlayerStackBackground(Color.YELLOW);
                    player_stack.setForeground(Color.BLACK);
                    player_stack.setText(Helpers.float2String(stack) + " + " + Helpers.float2String(Float.valueOf((int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname()))));

                } else {

                    if (GameFrame.hasRebought(nickname)) {
                        setPlayerStackBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        setPlayerStackBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    player_stack.setText(Helpers.float2String(stack));
                }
            });
        }
    }

    public int getBuyin() {
        return buyin;
    }

    public float getBet() {
        return bet;
    }

    public void player_stack_click() {

        MouseEvent me = new MouseEvent(player_stack, // which
                MouseEvent.MOUSE_CLICKED, // what
                System.currentTimeMillis(), // when
                MouseEvent.BUTTON1_MASK,
                0, 0, // where: at (0, 0}
                1, // only 1 click 
                false); // not a popup trigger

        player_stack.dispatchEvent(me);

    }

    public GifLabel getChat_notify_label() {
        return chat_notify_label;
    }

    /**
     * Creates new form JugadorLocalView
     */
    public LocalPlayer() {

        Helpers.GUIRunAndWait(() -> {
            initComponents();
            setOpaque(false);
            setBackground(null);
            // Wire opcional al latency_dot_widget del .form (si existe).
            try {
                java.lang.reflect.Field f = getClass().getDeclaredField("latency_dot_widget");
                f.setAccessible(true);
                Object widget = f.get(this);
                if (widget instanceof LatencyDot) {
                    setLatencyDot((LatencyDot) widget);
                    ((LatencyDot) widget).applyZoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                }
            } catch (NoSuchFieldException nsfe) {
                // OK: aún no se ha añadido en el .form.
            } catch (Exception ex) {
                Logger.getLogger(LocalPlayer.class.getName()).log(Level.WARNING, "Could not wire latency_dot_widget", ex);
            }
            hands_win.setVisible(false);
            sec_pot_win_label.setVisible(false);
            sec_pot_win_label.setHorizontalAlignment(JLabel.CENTER);
            sec_pot_win_label.setOpaque(true);
            sec_pot_win_label.setFocusable(false);
            sec_pot_win_label.setFont(player_action.getFont().deriveFont(player_action.getFont().getStyle(), Math.round(player_action.getFont().getSize() * 0.7f)));
            panel_cartas.add(sec_pot_win_label, Integer.valueOf(1002));
            chat_notify_label.setVisible(false);
            chat_notify_label.setFocusable(false);
            chat_notify_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chat_notify_label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    chat_notify_label.setVisible(false);
                    Helpers.threadRun(() -> {
                        synchronized (chat_notify_label) {

                            chat_notify_label.notifyAll();
                        }
                    });
                }
            });
            panel_cartas.add(chat_notify_label, Integer.valueOf(1001));
            chip_label.setVisible(false);
            chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chip_label.setOpaque(false);
            chip_label.setFocusable(false);
            chip_label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {

                    player_nameMouseClicked(e);
                }
            });
            panel_cartas.add(chip_label, Integer.valueOf(1000));
            border_color = ((LineBorder) getBorder()).getLineColor();
            action_button_armed.put(player_check_button, false);
            action_button_armed.put(player_bet_button, false);
            action_button_armed.put(player_allin_button, false);
            action_button_armed.put(player_fold_button, false);
            disablePlayerAction();
            desactivarControles();
            Helpers.setScaledIconLabel(utg_icon, getClass().getResource("/images/utg.png"), 41, 31);
            utg_icon.setVisible(false);
            player_pot.setText("----");
            player_name.setCursor(new Cursor(Cursor.HAND_CURSOR));
            icon_zoom_timer = new Timer(GameFrame.GUI_RENDER_WAIT, (ActionEvent ae) -> {
                icon_zoom_timer.stop();
                zoomIcons();
                holeCard1.updateImagePreloadCache();
                holeCard2.updateImagePreloadCache();
                refreshNotifyChatLabel();
            });
            icon_zoom_timer.setRepeats(false);
            icon_zoom_timer.setCoalesce(false);

        });

    }

    public JButton getPlayer_allin() {
        return player_allin_button;
    }

    public JButton getPlayer_bet_button() {
        return player_bet_button;
    }

    public JButton getPlayer_check() {
        return player_check_button;
    }

    public JButton getPlayer_fold() {
        return player_fold_button;
    }

    public Card getHoleCard1() {
        return holeCard1;
    }

    public Card getHoleCard2() {
        return holeCard2;
    }

    public ArrayList<Card> getHoleCards() {
        ArrayList<Card> cartas = new ArrayList<>();

        cartas.add(getHoleCard1());

        cartas.add(getHoleCard2());
        return cartas;
    }

    public synchronized void setBet(float new_bet) {

        float old_bet = bet;

        bet = Helpers.floatClean(new_bet);

        if (Helpers.float1DSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.floatClean(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            if (Helpers.float1DSecureCompare(0f, bote) < 0) {
                player_pot.setText(Helpers.float2String(bote));

            } else {

                player_pot.setText("----");
            }
        });

    }

    public JLabel getPlayer_stack() {
        return player_stack;
    }

    public synchronized void reComprar(int cantidad) {

        // Re-chequeo al aplicar (anti-stale / anti-trampa): nunca superar el techo
        // de mesa aunque la cantidad solicitada fuera mayor o el stack cambiara
        // entre la solicitud y el inicio de la mano. headroom 0 -> recompra anulada.
        int applied = Math.min(cantidad, GameFrame.rebuyHeadroom(this.stack));
        if (applied <= 0) {
            Logger.getLogger(LocalPlayer.class.getName()).log(Level.WARNING,
                    "Rebuy of {0} for {1} voided at apply time (already at table ceiling {2})",
                    new Object[]{cantidad, this.nickname, GameFrame.getBuyinCap()});
            return;
        }

        this.stack += applied;
        this.buyin += applied;
        GameFrame.getInstance().getRegistro().print(this.nickname + " " + Translator.translate("rebuy.recompra_2") + String.valueOf(applied) + ")");
        Audio.playWavResource("misc/cash_register.wav");

        if (!player_stack_click) {
            Helpers.GUIRun(() -> {
                player_stack.setText(Helpers.float2String(stack));
                setPlayerStackBackground(Color.CYAN);
                player_stack.setForeground(Color.BLACK);
            });
        }
    }

    private void guardarColoresBotonesAccion() {
        action_button_colors.clear();

        action_button_colors.put(player_check_button, new Color[]{player_check_button.getBackground(), player_check_button.getForeground()});

        action_button_colors.put(player_bet_button, new Color[]{player_bet_button.getBackground(), player_bet_button.getForeground()});

        action_button_colors.put(player_allin_button, new Color[]{player_allin_button.getBackground(), player_allin_button.getForeground()});

        action_button_colors.put(player_fold_button, new Color[]{player_fold_button.getBackground(), player_fold_button.getForeground()});

    }

    public void esTuTurno() {

        turno = true;

        GameFrame.getInstance().getCrupier().disableAllPlayersTimeout();

        if (this.getDecision() == Player.NODEC) {
            Audio.playWavResource("misc/yourturn.wav");

            call_required = Helpers.floatClean(GameFrame.getInstance().getCrupier().getApuesta_actual() - bet);

            min_raise = Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getUltimo_raise()) < 0 ? GameFrame.getInstance().getCrupier().getUltimo_raise() : Helpers.floatClean(GameFrame.getInstance().getCrupier().getCiega_grande());

            Helpers.GUIRun(() -> {
                desarmarBotonesAccion();

                setPlayerBorder(Color.ORANGE);

                player_allin_button.setText(Translator.translate("game.all_in"));
                player_allin_button.putClientProperty("i18n.key", "game.all_in");
                player_allin_button.setEnabled(true);

                Helpers.setScaledIconButton(player_allin_button, getClass().getResource("/images/action/glasses.png"), Math.round(0.6f * player_allin_button.getHeight()), Math.round(0.6f * player_allin_button.getHeight()));

                player_fold_button.setText(Translator.translate("player.no_ir"));
                player_fold_button.putClientProperty("i18n.key", "player.no_ir");
                player_fold_button.setEnabled(true);
                player_fold_button.setBackground(Color.DARK_GRAY);
                player_fold_button.setForeground(Color.WHITE);

                Helpers.setScaledIconButton(player_fold_button, getClass().getResource("/images/action/down.png"), Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()));

                setActionBackground(new Color(204, 204, 204, 75));

                player_action.setForeground(Color.WHITE);

                //Comprobamos si podemos ver la apuesta actual
                if (Helpers.float1DSecureCompare(call_required, stack) < 0) {

                    player_check_button.setEnabled(true);

                    Helpers.setScaledIconButton(player_check_button, getClass().getResource("/images/action/up.png"), Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()));

                    if (Helpers.float1DSecureCompare(0f, call_required) == 0) {
                        player_check_button.setText(Translator.translate("game.pasar"));
                        player_check_button.putClientProperty("i18n.key", "game.pasar");
                        player_check_button.setBackground(new Color(0, 130, 0));
                        player_check_button.setForeground(Color.WHITE);

                        player_fold_button.setBackground(Color.RED);
                        player_fold_button.setForeground(Color.WHITE);
                    } else {
                        player_check_button.setText(Translator.translate("ui.ir_2") + " (+" + Helpers.float2String(call_required) + ")");
                        player_check_button.putClientProperty("i18n.key", null); // Limpiamos para evitar el glitch de texto dinámico
                        player_check_button.setBackground(null);
                        player_check_button.setForeground(null);
                        player_fold_button.setBackground(Color.DARK_GRAY);
                        player_fold_button.setForeground(Color.WHITE);
                    }

                } else {

                    if (pre_pulsado == Player.CHECK) {
                        desPrePulsarBotonAuto(player_check_button);
                    }

                    player_check_button.setIcon(null);
                    player_check_button.setText(" ");
                    player_check_button.setEnabled(false);
                    player_check_button.putClientProperty("i18n.key", null);
                }

                if ((GameFrame.getInstance().getCrupier().getLast_aggressor() == null || !nickname.equals(GameFrame.getInstance().getCrupier().getLast_aggressor().getNickname())) && GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) > 1 && ((Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0)
                        || (Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0 && Helpers.float1DSecureCompare(call_required + min_raise, stack) < 0))) {

                    // Step y rango del spinner alineados a la sb ACTUAL del
                    // Crupier (no GameFrame.CIEGA_PEQUEÑA estática, que sería
                    // la sb inicial y queda obsoleta tras doblarCiegas o tras
                    // un recovery con blinds doblados). Sin esto el humano
                    // podía seleccionar incrementos múltiplos de la sb vieja
                    // que sumados al call generaban totales fraccionarios
                    // respecto a la sb nueva — el mismo síntoma "fractional
                    // chip bets" del fix de Bot.java pero por la ruta del
                    // jugador local.
                    //
                    // El RAISE TOTAL committed = spinner_val + bet + call_required
                    //                          = spinner_val + apuesta_actual.
                    // Para que ese total sea múltiplo de la sb actual cuando
                    // apuesta_actual viene fraccionario (caso típico: all-in
                    // previo con stack residual no alineado), spinner_min se
                    // ajusta a (aligned_min_total - apuesta_actual) y spinner_max
                    // a (aligned_max_total - apuesta_actual). Con step = sb
                    // todos los valores intermedios spinner_min + k*sb mantienen
                    // total alineado.
                    float current_sb = GameFrame.getInstance().getCrupier().getCiega_pequeña();
                    if (current_sb <= 0f) {
                        current_sb = GameFrame.CIEGA_PEQUEÑA;
                    }
                    BigDecimal sb_step = new BigDecimal(current_sb).setScale(1, RoundingMode.HALF_UP);
                    BigDecimal apuesta_actual_bd = new BigDecimal(GameFrame.getInstance().getCrupier().getApuesta_actual()).setScale(1, RoundingMode.HALF_UP);

                    //Actualizamos el spinner y el botón de apuestas
                    BigDecimal spinner_min;
                    // aligned_max_total = floor((bet + stack) / sb) * sb,
                    // que es el mayor total committed múltiplo de sb que cabe
                    // en lo que el jugador tiene disponible. spinner_max =
                    // aligned_max_total - apuesta_actual.
                    BigDecimal bet_plus_stack = new BigDecimal(bet + stack).setScale(1, RoundingMode.HALF_UP);
                    BigDecimal aligned_max_total = bet_plus_stack.divide(sb_step, 0, RoundingMode.FLOOR).multiply(sb_step);
                    BigDecimal spinner_max = aligned_max_total.subtract(apuesta_actual_bd);

                    Helpers.setScaledIconButton(player_bet_button, getClass().getResource("/images/action/bet.png"), Math.round(0.6f * player_bet_button.getHeight()), Math.round(0.6f * player_bet_button.getHeight()));

                    if (Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                        // Apertura: bb es 2*sb por construcción de CIEGAS, así
                        // que el mínimo legal coincide con un múltiplo de sb.
                        spinner_min = new BigDecimal(GameFrame.getInstance().getCrupier().getCiega_grande()).setScale(1, RoundingMode.HALF_UP);
                        player_bet_button.setEnabled(true);
                        player_bet_button.setText(Translator.translate("action.apostar_2"));
                        player_bet_button.putClientProperty("i18n.key", "action.apostar_2");
                        player_bet_button.setBackground(Color.WHITE);
                        player_bet_button.setForeground(Color.BLACK);

                    } else {
                        // Raise: aligned_min_total = ceil((apuesta_actual +
                        // min_raise) / sb) * sb. spinner_min = aligned_min_total
                        // - apuesta_actual. Puede no ser múltiplo de sb a secas
                        // (si apuesta_actual viene fraccionario), pero spinner_min
                        // + k*sb sumado a apuesta_actual SÍ produce total
                        // alineado por construcción.
                        BigDecimal min_raise_bd = new BigDecimal(min_raise).setScale(1, RoundingMode.HALF_UP);
                        BigDecimal aligned_min_total = apuesta_actual_bd.add(min_raise_bd).divide(sb_step, 0, RoundingMode.CEILING).multiply(sb_step);
                        spinner_min = aligned_min_total.subtract(apuesta_actual_bd);
                        player_bet_button.setEnabled(true);
                        String actionKey = GameFrame.getInstance().getCrupier().getConta_raise() > 0 ? "action.resubir" : "action.subir";
                        player_bet_button.setText(Translator.translate(actionKey));
                        player_bet_button.putClientProperty("i18n.key", actionKey);

                        if (GameFrame.getInstance().getCrupier().getConta_raise() > 0) {
                            player_bet_button.setBackground(RERAISE_BACK_COLOR);
                            player_bet_button.setForeground(RERAISE_FORE_COLOR);
                        } else {
                            player_bet_button.setBackground(Color.WHITE);
                            player_bet_button.setForeground(Color.BLACK);
                        }
                    }

                    if (spinner_min.compareTo(spinner_max) < 0) {

                        SpinnerNumberModel nummodel = new SpinnerNumberModel(spinner_min, spinner_min, spinner_max, sb_step) {
                            public Object getNextValue() {
                                BigDecimal current = (BigDecimal) super.getValue();

                                current = current.add((BigDecimal) super.getStepSize());

                                if (current.compareTo((BigDecimal) super.getMaximum()) <= 0) {
                                    return current;
                                } else {
                                    return null;
                                }

                            }

                            public Object getPreviousValue() {
                                BigDecimal current = (BigDecimal) super.getValue();

                                current = current.subtract((BigDecimal) super.getStepSize());

                                if (((BigDecimal) super.getMinimum()).compareTo(current) <= 0) {
                                    return current;
                                } else {
                                    return null;
                                }

                            }

                        };
                        bet_spinner.setModel(nummodel);
                        bet_spinner.setEnabled(true);

                        ((JSpinner.DefaultEditor) bet_spinner.getEditor()).getTextField().setEditable(false);
                        ((JSpinner.DefaultEditor) bet_spinner.getEditor()).getTextField().setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));

                    } else {
                        player_bet_button.setEnabled(false);
                        player_bet_button.setText(" ");
                        player_bet_button.putClientProperty("i18n.key", null);
                        bet_spinner.setValue(new BigDecimal(0));
                        bet_spinner.setEnabled(false);
                    }
                } else {
                    player_bet_button.setEnabled(false);
                    player_bet_button.setText(" ");
                    player_bet_button.putClientProperty("i18n.key", null);
                    player_bet_button.setIcon(null);
                }

                guardarColoresBotonesAccion();

                if ((GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) == 1 || ((GameFrame.getInstance().getCrupier().getLast_aggressor() != null && nickname.equals(GameFrame.getInstance().getCrupier().getLast_aggressor().getNickname())))) && Helpers.float1DSecureCompare(call_required, stack) < 0) {
                    player_allin_button.setText(" ");
                    player_allin_button.putClientProperty("i18n.key", null);
                    player_allin_button.setEnabled(false);
                    player_allin_button.setIcon(null);
                }

                Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                Helpers.setTranslatedText(player_action, "action.hablas_tu");

                // NOTA: Se ha borrado la línea Helpers.translateComponents(botonera, false) que machacaba los botones dinámicos.
                Helpers.translateComponents(player_action, false);

                // Reajusta la fuente al texto traducido (preserva la clave i18n: solo
                // re-setea el mismo texto y, si cabe, restaura el tamaño original).
                setActionTextFitted(player_action.getText());

                setPlayerActionIcon("action/thinking.png");

                Helpers.setSpinnerColors(bet_spinner, player_bet_button.getBackground(), player_bet_button.getForeground());

                if (GameFrame.TEST_MODE) {

                    Helpers.threadRun(() -> {

                        Helpers.pausar(GameFrame.TEST_MODE_PAUSE);

                        ArrayList<JButton> botones = new ArrayList<>(Arrays.asList(new JButton[]{player_check_button, player_bet_button, player_allin_button, player_fold_button}));

                        Iterator<JButton> iterator = botones.iterator();

                        Helpers.GUIRun(() -> {
                            while (iterator.hasNext()) {
                                JButton boton = iterator.next();

                                if (!boton.isEnabled()) {
                                    iterator.remove();
                                }
                            }

                            int eleccion = Helpers.CSPRNG_GENERATOR.nextInt(botones.size());

                            botones.get(eleccion).doClick();
                        });
                    });

                } else {

                    //Tiempo máximo para pensar
                    response_counter = Crupier.TIEMPO_PENSAR;

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    auto_action = new Timer(1000, new ActionListener() {
                        long t = GameFrame.getInstance().getCrupier().getTurno();

                        @Override
                        public void actionPerformed(ActionEvent ae) {

                            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isSomePlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && response_counter > 0 && auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                response_counter--;

                                // setValue(response_counter) redundante: smoothCountdown ya tiene
                                // su Timer interno actualizando la barra cada 50ms.

                                if (response_counter == 10) {
                                    Audio.playWavResource("misc/hurryup.wav");
                                    if ((hurryup_timer == null || !hurryup_timer.isRunning()) && Helpers.float1DSecureCompare(0f, call_required) < 0) {
                                        if (hurryup_timer != null) {
                                            hurryup_timer.stop();
                                        }
                                        Color orig_color = border_color;
                                        hurryup_timer = new Timer(1000, (ActionEvent ae1) -> {
                                            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().isTimba_pausada() && hurryup_timer.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {
                                                if (border_color != Color.GRAY) {
                                                    setPlayerBorder(Color.GRAY);
                                                    setActionBackground(Color.GRAY);
                                                    player_action.setForeground(Color.WHITE);
                                                } else {
                                                    setPlayerBorder(orig_color);
                                                    setActionBackground(new Color(204, 204, 204, 75));
                                                    player_action.setForeground(Color.WHITE);
                                                }

                                            }
                                        });
                                        hurryup_timer.start();
                                    }
                                }

                                if (response_counter == 0 || GameFrame.getInstance().getCrupier().getJugadoresActivos() < 2) {
                                    Helpers.threadRun(() -> {
                                        if (response_counter == 0) {
                                            Audio.playWavResourceAndWait("misc/timeout.wav"); //Mientras dura la bocina aún estaríamos a tiempo de elegir
                                        }

                                        GameFrame.getInstance().checkPause();

                                        Helpers.GUIRun(() -> {
                                            if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno() && getDecision() == Player.NODEC) {

                                                if (Helpers.float1DSecureCompare(0f, call_required) == 0) {

                                                    //Pasamos automáticamente
                                                    action_button_armed.put(player_check_button, true);
                                                    player_check_button.doClick();

                                                } else {

                                                    //Nos tiramos automáticamente
                                                    action_button_armed.put(player_fold_button, true);
                                                    player_fold_button.doClick();

                                                }

                                            }
                                        });
                                    });
                                }

                                repaint();

                            }
                        }
                    });

                    auto_action.start();

                    if (!auto_pause && GameFrame.AUTO_ACTION_BUTTONS && pre_pulsado != Player.NODEC) {

                        // Decide qué botón se auto-pulsaría (target) y la etiqueta
                        // para el diálogo MODO AUTO. Check/Fold: si pasar es gratis
                        // pasamos (manteniendo el armado); si hay que pagar nos
                        // tiramos. Check/Call: pasa gratis o iguala según las reglas
                        // del pre-pulsado de check.
                        JButton target = null;
                        String action_key = null;

                        if (pre_pulsado == Player.FOLD) {

                            if (player_check_button.isEnabled() && Helpers.float1DSecureCompare(0f, call_required) == 0) {
                                target = player_check_button;
                                action_key = "modo_auto.pasar";
                            } else if (player_fold_button.isEnabled()) {
                                target = player_fold_button;
                                action_key = "modo_auto.tirar";
                            }

                        } else if (pre_pulsado == Player.CHECK && (Helpers.float1DSecureCompare(0f, call_required) == 0 || (GameFrame.getInstance().getCrupier().getStreet() == Crupier.PREFLOP && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), GameFrame.getInstance().getCrupier().getCiega_grande()) == 0) || (GameFrame.AUTO_CALL_ENABLED && (Helpers.float1DSecureCompare(0f, GameFrame.AUTO_CALL_MAX) == 0 || Helpers.float1DSecureCompare(Math.min(call_required, stack), GameFrame.AUTO_CALL_MAX) <= 0)))) {

                            if (player_check_button.isEnabled()) {
                                target = player_check_button;
                                action_key = (Helpers.float1DSecureCompare(0f, call_required) == 0) ? "modo_auto.pasar" : "modo_auto.igualar";
                            } else if (player_allin_button.isEnabled()) {
                                // Igualar exige all-in (coste a igualar >= stack, el
                                // check está deshabilitado): la única forma de igualar
                                // es irse all-in. El tope ya se evaluó contra lo que
                                // realmente se compromete —min(coste, stack), que en
                                // este caso es el stack—, así que stack <= AUTO_CALL_MAX
                                // y nunca se arriesga más que el tope. Es el mismo
                                // importe que enseña el overlay de "coste de igualar".
                                target = player_allin_button;
                                action_key = "modo_auto.igualar";
                            }
                        }

                        if (target == null) {

                            desPrePulsarAutoTodo();

                        } else if (GameFrame.MODO_AUTO_CONFIRM) {

                            // Veto de 5s NO modal: el resto del tablero/menú siguen
                            // usables, pero la botonera de acción del LocalPlayer se
                            // DESACTIVA mientras corre (el diálogo es el punto de
                            // decisión). Guardamos su estado para restaurarlo al
                            // resolver. La resolución va por callback (EDT): al expirar
                            // se ejecuta; al cancelar (o si el turno se resuelve por
                            // otra vía) se desarma SIEMPRE (re-armado manual) y se
                            // recupera el control manual. doClick re-chequea NODEC.
                            final JButton fire_target = target;

                            final boolean check_en = player_check_button.isEnabled();
                            final boolean fold_en = player_fold_button.isEnabled();
                            final boolean bet_en = player_bet_button.isEnabled();
                            final boolean allin_en = player_allin_button.isEnabled();
                            final boolean spinner_en = bet_spinner.isEnabled();

                            // Apariencia previa (texto + icono) de la botonera. Durante
                            // el veto los botones se DESACTIVAN con el mismo aspecto
                            // "gris vacío" (sin texto ni icono) que cualquier otro estado
                            // deshabilitado del tablero, en lugar de quedar atenuados
                            // conservando su etiqueta. Se restaura al resolver (al
                            // cancelar, el jugador recupera el control manual con las
                            // etiquetas correctas).
                            final String check_text = player_check_button.getText();
                            final String fold_text = player_fold_button.getText();
                            final String bet_text = player_bet_button.getText();
                            final String allin_text = player_allin_button.getText();
                            final Icon check_icon = player_check_button.getIcon();
                            final Icon fold_icon = player_fold_button.getIcon();
                            final Icon bet_icon = player_bet_button.getIcon();
                            final Icon allin_icon = player_allin_button.getIcon();
                            final Object spinner_value = bet_spinner.getValue();

                            player_check_button.setText(" ");
                            player_check_button.setIcon(null);
                            player_check_button.setEnabled(false);
                            player_fold_button.setText(" ");
                            player_fold_button.setIcon(null);
                            player_fold_button.setEnabled(false);
                            player_bet_button.setText(" ");
                            player_bet_button.setIcon(null);
                            player_bet_button.setEnabled(false);
                            player_allin_button.setText(" ");
                            player_allin_button.setIcon(null);
                            player_allin_button.setEnabled(false);
                            bet_spinner.setValue(new BigDecimal(0));
                            bet_spinner.setEnabled(false);

                            AutoActionDialog dlg = new AutoActionDialog(
                                    GameFrame.getInstance(), LocalPlayer.this, GameFrame.AUTO_CONFIRM_SECONDS,
                                    Translator.translate(action_key),
                                    () -> getDecision() == Player.NODEC,
                                    (cancelled) -> {
                                        auto_action_dialog = null;

                                        // Restaurar la apariencia previa (texto + icono)
                                        // antes de re-habilitar: el doClick necesita el
                                        // botón enabled y, al cancelar, el jugador recupera
                                        // el control manual con sus etiquetas.
                                        player_check_button.setText(check_text);
                                        player_check_button.setIcon(check_icon);
                                        player_fold_button.setText(fold_text);
                                        player_fold_button.setIcon(fold_icon);
                                        player_bet_button.setText(bet_text);
                                        player_bet_button.setIcon(bet_icon);
                                        player_allin_button.setText(allin_text);
                                        player_allin_button.setIcon(allin_icon);
                                        bet_spinner.setValue(spinner_value);

                                        player_check_button.setEnabled(check_en);
                                        player_fold_button.setEnabled(fold_en);
                                        player_bet_button.setEnabled(bet_en);
                                        player_allin_button.setEnabled(allin_en);
                                        bet_spinner.setEnabled(spinner_en);

                                        if (!cancelled && getDecision() == Player.NODEC) {
                                            // Armar check o all-in salta el doble clic de
                                            // CONFIRM_ACTIONS (el fold ya lo salta por
                                            // pre_pulsado==FOLD en su handler).
                                            if (fire_target == player_check_button || fire_target == player_allin_button) {
                                                action_button_armed.put(fire_target, true);
                                            }
                                            fire_target.doClick();
                                        } else if (cancelled) {
                                            pre_pulsado = Player.NODEC;
                                        }
                                    });
                            auto_action_dialog = dlg;
                            dlg.setVisible(true);

                        } else {

                            // Sin diálogo de veto: ejecutar directamente. Armar check o
                            // all-in salta el doble clic de CONFIRM_ACTIONS.
                            if (target == player_check_button || target == player_allin_button) {
                                action_button_armed.put(target, true);
                            }
                            target.doClick();
                        }
                    }

                    if (auto_pause) {
                        GameFrame.getInstance().getLocalPlayer().setAuto_pause(false);
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
                    }

                }

            });

        } else {

            finTurno();
        }

    }

    public void finTurno() {

        stopActionTimer();

        Audio.stopWavResource("misc/hurryup.wav");

        action_button_colors.clear();

        Helpers.GUIRun(() -> {
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }

            turno = false;

            synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
            }

            // Tras tirarse (FOLD) también se reactivan los pre-botones AUTO para
            // poder armarlos fuera de turno (de cara a las manos siguientes); un
            // jugador en FOLD está saltado en el bucle de apuestas, así que el
            // pre-pulsado nunca dispara esta mano. ALLIN sí queda fuera. Requiere
            // el toggle "Botones AUTO" activo.
            if (GameFrame.AUTO_ACTION_BUTTONS && getDecision() != Player.ALLIN) {
                activarPreBotones();
            }

        });
    }

    public void desactivarControles() {

        Helpers.GUIRunAndWait(() -> {
            bet_spinner.setValue(new BigDecimal(0));

            bet_spinner.setEnabled(false);

            for (Component c : botonera.getComponents()) {

                if (c instanceof JButton) {
                    ((JButton) c).setText(" ");
                    ((JButton) c).setIcon(null);
                    c.setEnabled(false);
                    // LIMPIEZA DE ETIQUETA: Evita que el botón resucite textos antiguos
                    ((JButton) c).putClientProperty("i18n.key", null);
                }
            }

            desarmarBotonesAccion();
        });

    }

    public void desPrePulsarAutoTodo() {

        if (pre_pulsado != Player.NODEC) {

            desPrePulsarBotonAuto(player_check_button);
            desPrePulsarBotonAuto(player_fold_button);
        }
    }

    public void desPrePulsarBotonAuto(JButton boton) {

        // Abort the automatic reset of the pre-action if it is already our turn
        if (turno) {
            return;
        }

        pre_pulsado = Player.NODEC;

        Helpers.GUIRunAndWait(() -> {

            // Double check inside the GUI thread to prevent race conditions
            if (turno) {
                return;
            }

            Color[] colores = action_button_colors.get(boton);
            if (colores != null) {
                boton.setBackground(colores[0]);
                boton.setForeground(colores[1]);
            } else {
                boton.setBackground(null);
                boton.setForeground(null);
            }
        });

    }

    public void prePulsarBotonAuto(JButton boton, int dec) {

        // Abort the automatic pre-action UI update if it is already our turn
        if (turno) {
            return;
        }

        Helpers.GUIRunAndWait(() -> {

            // Double check inside the GUI thread: commit pre_pulsado AND the
            // highlight together under the same !turno gate. If the turn opened
            // between the outer check and here, neither is applied — so a press
            // landing exactly on the turn boundary is not auto-fired by
            // esTuTurno, and pre_pulsado can never disagree with the highlight.
            if (turno) {
                return;
            }

            pre_pulsado = dec;

            boton.setBackground(Color.YELLOW);
            boton.setForeground(Color.BLACK);
        });

    }

    public void desarmarBotonesAccion() {
        Helpers.GUIRunAndWait(() -> {
            for (Map.Entry<JButton, Color[]> entry : action_button_colors.entrySet()) {

                JButton b = entry.getKey();

                if (action_button_armed.get(b)) {

                    Color[] colores = entry.getValue();

                    action_button_armed.put(b, false);

                    b.setBackground(colores[0]);
                    b.setForeground(colores[1]);

                }

            }
        });
    }

    public void armarBoton(JButton boton) {

        Helpers.GUIRunAndWait(() -> {
            for (Map.Entry<JButton, Color[]> entry : action_button_colors.entrySet()) {

                JButton b = entry.getKey();

                Color[] colores = entry.getValue();

                if (b == boton) {
                    action_button_armed.put(b, true);

                    b.setBackground(Color.BLUE);
                    b.setForeground(Color.WHITE);

                } else {
                    action_button_armed.put(b, false);

                    b.setBackground(colores[0]);
                    b.setForeground(colores[1]);

                }
            }
        });

    }

    public void resetBetDecision() {

        int old_dec = this.decision;

        this.decision = Player.NODEC;

        Helpers.GUIRun(() -> {
            if (old_dec != Player.BET || Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                player_pot.setForeground(Color.WHITE);
            }

            disablePlayerAction();
        });

    }

    public void activarPreBotones() {

        // FOLD ya no bloquea: un jugador tirado puede ver/armar los pre-botones
        // fuera de su turno (para las manos siguientes). ALLIN, espectador, exit y
        // showdown sí siguen bloqueando.
        if (!turno && decision != Player.ALLIN && !spectator && !exit && !GameFrame.getInstance().getCrupier().isShow_time()) {

            Helpers.GUIRunAndWait(() -> {
                player_check_button.setBackground(null);
                player_check_button.setForeground(null);
                Helpers.setTranslatedText(player_check_button, "action.auto_call");
                player_check_button.setEnabled(true);
                Helpers.setScaledIconButton(player_check_button, getClass().getResource("/images/action/up.png"), Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()));

                player_fold_button.setBackground(null);
                player_fold_button.setForeground(null);
                Helpers.setTranslatedText(player_fold_button, "action.auto_fold");
                player_fold_button.setEnabled(true);
                Helpers.setScaledIconButton(player_fold_button, getClass().getResource("/images/action/down.png"), Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()));

                if (pre_pulsado != Player.NODEC) {

                    if (pre_pulsado == Player.CHECK) {
                        prePulsarBotonAuto(player_check_button, Player.CHECK);
                    } else if (pre_pulsado == Player.FOLD) {
                        prePulsarBotonAuto(player_fold_button, Player.FOLD);
                    }
                }
            });

        }

    }

    public void desActivarPreBotones() {
        desActivarPreBotones(true);
    }

    // reset_pre_press=false keeps the queued pre_pulsado alive while still
    // hiding the [AUTO] buttons: used at end of hand when "persist between
    // hands" is on, so the pre-press survives into the next hand and is
    // re-armed by the first activarPreBotones of the new hand.
    public void desActivarPreBotones(boolean reset_pre_press) {

        if (!turno) {

            Helpers.GUIRunAndWait(() -> {
                if (reset_pre_press) {
                    desPrePulsarAutoTodo();
                }

                player_check_button.setText(" ");
                player_check_button.setIcon(null);
                player_check_button.setEnabled(false);
                player_check_button.putClientProperty("i18n.key", null);

                player_fold_button.setText(" ");
                player_fold_button.setIcon(null);
                player_fold_button.setEnabled(false);
                player_fold_button.putClientProperty("i18n.key", null);
            });
        }
    }

    public void refreshPos() {
        if (this.isActivo()) {
            this.bote = 0f;

            if (Helpers.float1DSecureCompare(0f, this.bet) < 0) {
                setStack(this.stack + this.bet);
            }

            this.bet = 0f;

            if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
                this.setPosition(BIG_BLIND);
            } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                this.setPosition(SMALL_BLIND);
            } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
                this.setPosition(DEALER);
            } else {
                this.setPosition(-1);
            }

            if (this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
                this.setUTG();
            } else {
                this.disableUTG();
            }
        }
    }

    public void disablePlayerAction() {

        Helpers.GUIRun(() -> {
            player_action.putClientProperty("i18n.key", null); // Asegura que se vacía la clave del traductor
            setActionTextFitted(" ");
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionBackground(new Color(204, 204, 204, 75));
            setPlayerActionIcon(null);
        });
    }

    public void resetGUI() {
        Helpers.GUIRunAndWait(() -> {
            // Restaura la fuente del action label si una jugada larga la había
            // encogido en la mano anterior (espejo de RemotePlayer.resetGUI).
            if (orig_action_font != null && orig_action_font.getSize() != player_action.getFont().getSize()) {
                player_action.setFont(orig_action_font);
                orig_action_font = null;
            }

            sec_pot_win_label.setVisible(false);

            setOpaque(false);

            setBackground(null);

            setPlayerBorder(new java.awt.Color(204, 204, 204, 75));

            player_name.setIcon(null);

            desactivar_boton_mostrar();

            desactivarControles();

            utg_icon.setVisible(false);

            player_pot.setText("----");

            setPlayerPotBackground(new Color(204, 204, 204, 75));

            player_pot.setForeground(Color.WHITE);

            if (conta_win > 0) {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            } else {
                hands_win.setVisible(false);
            }

            if (!player_stack_click) {
                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);

                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));

                    player_stack.setForeground(Color.WHITE);
                }
            }

            disablePlayerAction();

        });
    }

    @Override
    public void nuevaMano() {

        // Garantizar avatar pintado al inicio de cada mano (paridad con
        // RemotePlayer.nuevaMano — fix bug primera mano post-RECOVER).
        setAvatar();

        // "Persist AUTO between hands" keeps the queued pre-press across the
        // hand boundary; otherwise (default) it is cleared at the start of
        // every hand as before.
        if (!(GameFrame.AUTO_ACTION_BUTTONS && GameFrame.AUTO_ACTION_PERSIST)) {
            desPrePulsarAutoTodo();
        }

        this.decision = Player.NODEC;

        this.botes_secundarios.clear();

        this.pagar_face_base = 0f;

        this.muestra = false;

        this.winner = false;

        this.loser = false;

        this.bote = 0f;

        this.last_bote = null;

        this.bet = 0f;

        resetGUI();

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {

            int rebuy = (Integer) GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname);

            GameFrame.getInstance().getCrupier().getRebuy_now().remove(nickname);

            reComprar(rebuy);

        }

        setStack(stack + pagar);

        pagar = 0f;

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            this.setPosition(BIG_BLIND);
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            this.setPosition(SMALL_BLIND);
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            this.setPosition(DEALER);
        } else {
            this.setPosition(-1);
        }

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
            this.setUTG();
        } else {
            this.disableUTG();
        }

        if (this.spectator_bb) {
            this.spectator_bb = false;

            if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack + bet) < 0) {
                setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

            } else {

                //Vamos ALLIN (setBet antes: ver nota en player_allin_buttonActionPerformed)
                setBet(stack);
                setDecision(Player.ALLIN);
            }

        }
    }

    public synchronized float getEffectiveStack() {

        return Helpers.floatClean(this.stack) + Helpers.floatClean(this.bote) + Helpers.floatClean(this.pagar);

    }

    public boolean isBoton_mostrar() {
        return boton_mostrar;
    }

    @Override
    public void disableUTG() {

        if (this.utg) {
            this.utg = false;

            Helpers.GUIRun(() -> {
                utg_icon.setVisible(false);
            });
        }
    }

    private void actionIconZoom() {

        if (player_action_icon != null) {

            setPlayerActionIcon(player_action_icon);

        }

    }

    private void buttonIconZoom() {

        Helpers.GUIRun(() -> {
            if (player_check_button.isEnabled()) {
                Helpers.setScaledIconButton(player_check_button, getClass().getResource("/images/action/up.png"), Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()));
            }
            if (player_bet_button.isEnabled()) {
                Helpers.setScaledIconButton(player_bet_button, getClass().getResource("/images/action/bet.png"), Math.round(0.6f * player_bet_button.getHeight()), Math.round(0.6f * player_bet_button.getHeight()));
            }

            if (player_allin_button.isEnabled() && !boton_mostrar) {
                Helpers.setScaledIconButton(player_allin_button, getClass().getResource("/images/action/glasses.png"), Math.round(0.6f * player_allin_button.getHeight()), Math.round(0.6f * player_allin_button.getHeight()));
            }

            if (player_fold_button.isEnabled()) {

                Helpers.setScaledIconButton(player_fold_button, getClass().getResource("/images/action/down.png"), Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()));
            }
        });
    }

    private void nickChipIconZoom() {
        Helpers.GUIRun(() -> {
            if (isActivo()) {

                if (nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
                    Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                } else if (nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                } else if (nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
                    Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/dealer.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                } else {
                    player_name.setIcon(null);
                }
            } else {
                player_name.setIcon(null);
            }

            player_name.revalidate();
            player_name.repaint();
        });
    }

    private void utgIconZoom() {

        ImageIcon icon = new ImageIcon(IMAGEN_UTG.getImage().getScaledInstance((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight(), Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {
            utg_icon.setIcon(icon);

            utg_icon.setPreferredSize(new Dimension((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight()));

            utg_icon.setVisible(utg);
        });
    }

    private void zoomIcons() {

        Helpers.threadRun(() -> {
            synchronized (zoom_lock) {
                Helpers.GUIRunAndWait(() -> {
                    setAvatar();
                    utgIconZoom();
                    actionIconZoom();
                    buttonIconZoom();
                    nickChipIconZoom();
                    refreshPositionChipIcons();
                    refreshSecPotLabel();
                });
            }
        });
    }

    @Override
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        border_size = Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
        arc = Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        if (Helpers.float1DSecureCompare(0f, zoom_factor) < 0) {

            holeCard1.zoom(zoom_factor, mynotifier);
            holeCard2.zoom(zoom_factor, mynotifier);

            synchronized (zoom_lock) {

                Helpers.GUIRunAndWait(() -> {
                    if (icon_zoom_timer.isRunning()) {
                        icon_zoom_timer.stop();
                    }

                    hidePlayerActionIcon();

                    player_action.setMinimumSize(new Dimension(Math.round(LocalPlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(LocalPlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                    setPlayerBorder(border_color);

                    getAvatar().setVisible(false);

                    utg_icon.setVisible(false);

                    player_check_button.setIcon(null);

                    player_bet_button.setIcon(null);

                    player_allin_button.setIcon(null);

                    player_fold_button.setIcon(null);

                    player_name.setIcon(null);

                    chip_label.setVisible(false);

                    LatencyDot dot = latency_dot;
                    if (dot != null) {
                        dot.applyZoom(zoom_factor);
                    }

                });

                Helpers.zoomFonts(this, zoom_factor, null);

                Helpers.GUIRun(() -> {
                    if (icon_zoom_timer.isRunning()) {
                        icon_zoom_timer.restart();
                    } else {
                        icon_zoom_timer.start();
                    }
                });

            }

            synchronized (mynotifier) {
                while (mynotifier.size() < 2) {
                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        if (notifier != null) {

            notifier.add(Thread.currentThread().threadId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }

    public void setPosition(int pos) {

        switch (pos) {
            case Player.DEALER:

                if (GameFrame.getInstance().getCrupier().getDealer_nick().equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                        setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                    } else {

                        //Vamos ALLIN (setBet antes: ver nota en player_allin_buttonActionPerformed)
                        setBet(stack);

                        setDecision(Player.ALLIN);
                    }
                } else {
                    setBet(0f);
                }

                break;
            case Player.BIG_BLIND:

                if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

                } else {

                    //Vamos ALLIN (setBet antes: ver nota en player_allin_buttonActionPerformed)
                    setBet(stack);

                    setDecision(Player.ALLIN);
                }

                break;
            case Player.SMALL_BLIND:

                if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                } else {

                    //Vamos ALLIN (setBet antes: ver nota en player_allin_buttonActionPerformed)
                    setBet(stack);

                    setDecision(Player.ALLIN);
                }

                break;
            default:

                setBet(0f);

                break;
        }

        refreshPositionChipIcons();

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        indicadores_arriba = new javax.swing.JPanel();
        avatar_panel = new javax.swing.JPanel();
        avatar = new javax.swing.JLabel();
        player_pot_panel = new RoundedPanel(20);
        player_pot = new javax.swing.JLabel();
        player_stack_panel = new RoundedPanel(20);
        player_stack = new javax.swing.JLabel();
        nick_panel = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_icon = new javax.swing.JLabel();
        hands_win = new javax.swing.JLabel();
        latency_dot_widget = new com.tonikelope.coronapoker.LatencyDot();
        botonera = new javax.swing.JPanel();
        player_allin_button = new com.tonikelope.coronapoker.TranslucentDisabledButton();
        player_fold_button = new com.tonikelope.coronapoker.TranslucentDisabledButton();
        player_check_button = new com.tonikelope.coronapoker.TranslucentDisabledButton();
        player_bet_button = new com.tonikelope.coronapoker.TranslucentDisabledButton();
        bet_spinner = new com.tonikelope.coronapoker.TranslucentDisabledSpinner();
        panel_cartas = new javax.swing.JLayeredPane();
        holeCard1 = new com.tonikelope.coronapoker.Card();
        holeCard2 = new com.tonikelope.coronapoker.Card();
        player_action_panel = new RoundedPanel(20);
        player_action = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204, 75), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

        indicadores_arriba.setFocusable(false);
        indicadores_arriba.setOpaque(false);

        avatar_panel.setFocusable(false);
        avatar_panel.setOpaque(false);

        avatar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_null.png"))); // NOI18N
        avatar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar.setDoubleBuffered(true);
        avatar.setFocusable(false);
        avatar.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                avatarMouseClicked(evt);
            }
        });

        player_pot.setBackground(new Color(204,204,204,75));
        player_pot.setFont(new java.awt.Font("Dialog", 1, 32)); // NOI18N
        player_pot.setForeground(new java.awt.Color(255, 255, 255));
        player_pot.setText("----");
        player_pot.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_pot.setFocusable(false);

        javax.swing.GroupLayout player_pot_panelLayout = new javax.swing.GroupLayout(player_pot_panel);
        player_pot_panel.setLayout(player_pot_panelLayout);
        player_pot_panelLayout.setHorizontalGroup(
            player_pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_pot_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_pot)
                .addGap(0, 0, 0))
        );
        player_pot_panelLayout.setVerticalGroup(
            player_pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_pot_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_pot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        player_stack.setBackground(new java.awt.Color(51, 153, 0));
        player_stack.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_stack.setForeground(new java.awt.Color(255, 255, 255));
        player_stack.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_stack.setText("1000");
        player_stack.setToolTipText("CLICK PARA VER SU BUYIN");
        player_stack.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_stack.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_stack.setFocusable(false);
        player_stack.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_stackMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout player_stack_panelLayout = new javax.swing.GroupLayout(player_stack_panel);
        player_stack_panel.setLayout(player_stack_panelLayout);
        player_stack_panelLayout.setHorizontalGroup(
            player_stack_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_stack_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_stack)
                .addGap(0, 0, 0))
        );
        player_stack_panelLayout.setVerticalGroup(
            player_stack_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_stack_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_stack)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout avatar_panelLayout = new javax.swing.GroupLayout(avatar_panel);
        avatar_panel.setLayout(avatar_panelLayout);
        avatar_panelLayout.setHorizontalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(avatar)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(player_stack_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_pot_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        avatar_panelLayout.setVerticalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(avatar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_pot_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(player_stack_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        nick_panel.setFocusable(false);
        nick_panel.setOpaque(false);

        player_name.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_name.setForeground(new java.awt.Color(255, 255, 255));
        player_name.setText("123456789012345");
        player_name.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_name.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_name.setDoubleBuffered(true);
        player_name.setFocusable(false);
        player_name.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_nameMouseClicked(evt);
            }
        });

        utg_icon.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        utg_icon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        utg_icon.setDoubleBuffered(true);
        utg_icon.setFocusable(false);

        hands_win.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        hands_win.setForeground(new java.awt.Color(255, 255, 255));
        hands_win.setText("(0)");
        hands_win.setToolTipText("MANOS GANADAS");
        hands_win.setDoubleBuffered(true);

        javax.swing.GroupLayout nick_panelLayout = new javax.swing.GroupLayout(nick_panel);
        nick_panel.setLayout(nick_panelLayout);
        nick_panelLayout.setHorizontalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_name)
                .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(nick_panelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(utg_icon)
                        .addGap(5, 5, 5))
                    .addGroup(nick_panelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(latency_dot_widget)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addComponent(hands_win)
                .addGap(0, 0, 0))
        );
        nick_panelLayout.setVerticalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(player_name)
                .addComponent(utg_icon)
                .addComponent(hands_win))
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(latency_dot_widget)
                .addContainerGap())
        );

        javax.swing.GroupLayout indicadores_arribaLayout = new javax.swing.GroupLayout(indicadores_arriba);
        indicadores_arriba.setLayout(indicadores_arribaLayout);
        indicadores_arribaLayout.setHorizontalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(avatar_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        indicadores_arribaLayout.setVerticalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        botonera.setFocusable(false);
        botonera.setOpaque(false);

        player_allin_button.setBackground(new java.awt.Color(0, 0, 0));
        player_allin_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_allin_button.setForeground(new java.awt.Color(255, 255, 255));
        player_allin_button.setText("ALL IN");
        player_allin_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_allin_button.setDoubleBuffered(true);
        player_allin_button.setFocusable(false);
        player_allin_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_allin_buttonActionPerformed(evt);
            }
        });

        player_fold_button.setBackground(new java.awt.Color(255, 0, 0));
        player_fold_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_fold_button.setForeground(new java.awt.Color(255, 255, 255));
        player_fold_button.setText("NO IR");
        player_fold_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_fold_button.setDoubleBuffered(true);
        player_fold_button.setFocusable(false);
        player_fold_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_fold_buttonActionPerformed(evt);
            }
        });

        player_check_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_check_button.setText("PASAR");
        player_check_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_check_button.setDoubleBuffered(true);
        player_check_button.setFocusable(false);
        player_check_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_check_buttonActionPerformed(evt);
            }
        });

        player_bet_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_bet_button.setText("APOSTAR");
        player_bet_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_bet_button.setDoubleBuffered(true);
        player_bet_button.setFocusable(false);
        player_bet_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_bet_buttonActionPerformed(evt);
            }
        });

        bet_spinner.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        bet_spinner.setModel(new javax.swing.SpinnerNumberModel());
        bet_spinner.setBorder(null);
        bet_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        bet_spinner.setDoubleBuffered(true);

        javax.swing.GroupLayout botoneraLayout = new javax.swing.GroupLayout(botonera);
        botonera.setLayout(botoneraLayout);
        botoneraLayout.setHorizontalGroup(
            botoneraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(player_bet_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 257, Short.MAX_VALUE)
            .addComponent(player_allin_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_check_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_fold_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(bet_spinner, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        botoneraLayout.setVerticalGroup(
            botoneraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(botoneraLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_check_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bet_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_bet_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_allin_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_fold_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        panel_cartas.setDoubleBuffered(true);

        panel_cartas.setLayer(holeCard1, javax.swing.JLayeredPane.DEFAULT_LAYER);
        panel_cartas.setLayer(holeCard2, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 12, Short.MAX_VALUE)
                .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 13, Short.MAX_VALUE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        player_action.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.setMinimumSize(new Dimension(Math.round(LocalPlayer.MIN_ACTION_WIDTH*(1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP)), Math.round(LocalPlayer.MIN_ACTION_HEIGHT * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));

        javax.swing.GroupLayout player_action_panelLayout = new javax.swing.GroupLayout(player_action_panel);
        player_action_panel.setLayout(player_action_panelLayout);
        player_action_panelLayout.setHorizontalGroup(
            player_action_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_action_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        player_action_panelLayout.setVerticalGroup(
            player_action_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_action_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(indicadores_arriba, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(panel_cartas))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(botonera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(player_action_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(panel_cartas))
                    .addComponent(botonera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_action_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void player_fold_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_fold_buttonActionPerformed
        // TODO add your handling code here:

        if (!turno) {

            synchronized (pre_pulsar_lock) {

                if (pre_pulsado == Player.FOLD) {

                    Audio.playWavResource("misc/button_off.wav");

                    desPrePulsarBotonAuto(player_fold_button);

                } else {
                    Audio.playWavResource("misc/button_on.wav");

                    desPrePulsarAutoTodo();

                    prePulsarBotonAuto(player_fold_button, Player.FOLD);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_fold_button.isEnabled()) {

            if (pre_pulsado == Player.FOLD || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_fold_button) || click_recuperacion) {

                if (GameFrame.TEST_MODE || Helpers.float1DSecureCompare(0f, call_required) < 0 || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("ui.perder_mano_confirmacion"), new ImageIcon(getClass().getResource("/images/action/down.png"))) == 0) {

                    Audio.playWavResource("misc/fold.wav");

                    holeCard1.desenfocar();
                    holeCard2.desenfocar();

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        GameFrame.getInstance().getCrupier().soundFold();

                        setDecision(Player.FOLD);

                        finTurno();
                    });

                }

            } else {

                this.armarBoton(player_fold_button);
            }

        }

    }//GEN-LAST:event_player_fold_buttonActionPerformed

    private void player_allin_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_allin_buttonActionPerformed
        // TODO add your handling code here:

        if (!GameFrame.getInstance().isTimba_pausada() || boton_mostrar) {

            if (player_allin_button.isEnabled()) {

                if (boton_mostrar && GameFrame.getInstance().getCrupier().isShow_time()) {

                    this.muestra = true;

                    if (decision == Player.FOLD) {
                        updateParguela_counter();
                    }

                    desactivar_boton_mostrar();

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        synchronized (GameFrame.getInstance().getCrupier().getLock_mostrar()) {
                            if (GameFrame.getInstance().getCrupier().isShow_time()) {
                                Helpers.threadRun(() -> {
                                    GameFrame.getInstance().getCrupier().showAndBroadcastPlayerCards(nickname);
                                });
                                ArrayList<Card> cartas_jugada = new ArrayList<>(getHoleCards());
                                String hole_cards_string = Card.collection2String(getHoleCards());
                                for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                                    if (!carta_comun.isTapada()) {
                                        cartas_jugada.add(carta_comun);
                                    }
                                }
                                Hand jugada = new Hand(cartas_jugada);

                                // Las mutaciones de Swing deben ir en el EDT. GUIRun
                                // (asíncrono) y NO GUIRunAndWait: estamos dentro de
                                // lock_mostrar y bloquear el worker esperando al EDT
                                // podría interbloquear. setActionBackground/Icon ya se
                                // autoprotegen; aquí cubrimos setForeground/clientProperty/
                                // setActionTextFitted, que mutaban el label en crudo.
                                Helpers.GUIRun(() -> {
                                    player_action.setForeground(Color.WHITE);
                                    setActionBackground(new Color(51, 153, 255));

                                    // LIMPIEZA DE ETIQUETA: Evita el glitch de "HABLAS TÚ"
                                    player_action.putClientProperty("i18n.key", null);
                                    setActionTextFitted(Translator.translate("ui.muestras") + jugada.getName() + Translator.translate("ui.suffix_close"));
                                });

                                if (GameFrame.SONIDOS_CHORRA && decision == Player.FOLD) {

                                    Audio.playWavResource("misc/showyourcards.wav");

                                }
                                if (!GameFrame.getInstance().getCrupier().getPerdedores().containsKey(GameFrame.getInstance().getLocalPlayer())) {
                                    GameFrame.getInstance().getRegistro().print(nickname + " " + Translator.translate("ui.muestra_2") + " " + hole_cards_string + Translator.translate("ui.suffix_close") + " -> " + jugada);
                                }
                                Helpers.GUIRun(() -> Helpers.translateComponents(botonera, false));
                            }
                        }
                    });

                } else if (getDecision() == Player.NODEC) {

                    if (GameFrame.TEST_MODE || this.action_button_armed.get(player_allin_button) || click_recuperacion) {

                        GameFrame.getInstance().getCrupier().setCurrent_local_cinematic_b64(null);

                        Audio.playWavResource("misc/allin.wav");
                        GameFrame.getInstance().getCrupier().launchChipToPot(this);

                        desactivarControles();

                        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        if (hurryup_timer != null) {
                            hurryup_timer.stop();
                        }

                        Init.PLAYING_CINEMATIC = true;

                        Helpers.threadRun(() -> {
                            // Secuenciado en UN hilo (antes eran dos en paralelo) para
                            // cerrar la race del "*": localCinematicAllin fija
                            // current_local_cinematic_b64 y LANZA la animación en sus
                            // propios hilos (no bloquea), y solo después finTurno
                            // libera al crupier — así el build del ACTION ya no puede
                            // leer el b64 a null y difundir "*" cuando finTurno ganaba
                            // la carrera. La acción sigue saliendo al pulsar el botón
                            // (la selección del GIF son milisegundos).
                            try {
                                if (!GameFrame.getInstance().getCrupier().localCinematicAllin()) {
                                    GameFrame.getInstance().getCrupier().soundAllin();
                                }
                            } catch (Exception ex) {
                                // La cinemática es cosmética: pase lo que pase, el
                                // turno tiene que cerrarse y el flag apagarse (la
                                // espera del turno del bot depende de él).
                                Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                                Init.PLAYING_CINEMATIC = false;
                                synchronized (Init.LOCK_CINEMATICS) {
                                    Init.LOCK_CINEMATICS.notifyAll();
                                }
                            }

                            // setBet ANTES de setDecision a propósito: el
                            // render del all-in que setDecision encola al EDT
                            // lee bet+stack, y así los lee ya asentados en vez
                            // de competir con el movimiento del dinero a mitad
                            // de setBet.
                            setBet(stack + bet);

                            setDecision(Player.ALLIN);

                            finTurno();
                        });
                    } else {

                        this.armarBoton(player_allin_button);
                    }

                }

            }
        }

    }//GEN-LAST:event_player_allin_buttonActionPerformed

    private void player_check_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_check_buttonActionPerformed
        // TODO add your handling code here:
        if (!turno) {

            synchronized (pre_pulsar_lock) {

                if (pre_pulsado == Player.CHECK) {

                    Audio.playWavResource("misc/button_off.wav");

                    desPrePulsarBotonAuto(player_check_button);

                } else {

                    Audio.playWavResource("misc/button_on.wav");

                    desPrePulsarAutoTodo();

                    prePulsarBotonAuto(player_check_button, Player.CHECK);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_check_button.isEnabled()) {

            if (pre_pulsado == Player.CHECK || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_check_button) || click_recuperacion) {

                if (Helpers.float1DSecureCompare(this.stack - (GameFrame.getInstance().getCrupier().getApuesta_actual() - this.bet), 0f) == 0) {
                    player_allin_buttonActionPerformed(null);
                } else {

                    if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                        Audio.playWavResource("misc/call.wav");
                        GameFrame.getInstance().getCrupier().launchChipToPot(this);
                    } else {
                        Audio.playWavResource("misc/check.wav");
                    }

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

                        setDecision(Player.CHECK);

                        finTurno();
                    });
                }
            } else {

                this.armarBoton(player_check_button);
            }
        }

    }//GEN-LAST:event_player_check_buttonActionPerformed

    private void player_bet_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_bet_buttonActionPerformed
        // TODO add your handling code here:

        if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_bet_button.isEnabled()) {

            if (Helpers.float1DSecureCompare(stack, (((BigDecimal) bet_spinner.getValue()).floatValue()) + call_required) == 0) {

                player_allin_buttonActionPerformed(null);

            } else {

                if (!GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_bet_button) || click_recuperacion) {

                    float bet_spinner_val = Helpers.floatClean(((BigDecimal) bet_spinner.getValue()).floatValue());

                    Audio.playWavResource("misc/bet.wav");
                    GameFrame.getInstance().getCrupier().launchChipToPot(this);

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        if (apuesta_recuperada == null) {

                            setBet(bet_spinner_val + bet + call_required);
                        } else {

                            setBet(apuesta_recuperada);

                            apuesta_recuperada = null;
                        }

                        setDecision(Player.BET);

                        if (GameFrame.SONIDOS_CHORRA && !GameFrame.getInstance().getCrupier().isSincronizando_mano() && GameFrame.getInstance().getCrupier().getConta_raise() > 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {

                            Audio.playWavResource("misc/raise.wav");

                        }

                        finTurno();
                    });
                } else {
                    this.armarBoton(player_bet_button);
                }
            }

        }

    }//GEN-LAST:event_player_bet_buttonActionPerformed

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick()) || nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick()) || nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {

            GameFrame.LOCAL_POSITION_CHIP = !GameFrame.LOCAL_POSITION_CHIP;

            this.refreshPositionChipIcons();

            Helpers.PROPERTIES.setProperty("local_pos_chip", String.valueOf(GameFrame.LOCAL_POSITION_CHIP));

            Helpers.savePropertiesFile();

            Audio.playWavResource(chip_label.isVisible() ? "misc/button_on.wav" : "misc/button_off.wav");
        }
    }//GEN-LAST:event_player_nameMouseClicked

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:

        if (SwingUtilities.isLeftMouseButton(evt)) {
            if (!player_stack_click) {
                player_stack_click = true;

                player_stack.setText(Helpers.float2String((float) this.buyin));
                setPlayerStackBackground(Color.GRAY);
                player_stack.setForeground(Color.WHITE);

                Helpers.threadRun(() -> {
                    Helpers.pausar(1500);
                    float s = getStack();
                    Helpers.GUIRun(() -> {
                        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                            setPlayerStackBackground(Color.YELLOW);
                            player_stack.setForeground(Color.BLACK);
                            player_stack.setText(Helpers.float2String(stack) + " + " + Helpers.float2String(Float.valueOf((int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname()))));

                        } else {

                            if (GameFrame.hasRebought(nickname)) {
                                setPlayerStackBackground(Color.CYAN);

                                player_stack.setForeground(Color.BLACK);
                            } else {

                                setPlayerStackBackground(new Color(51, 153, 0));

                                player_stack.setForeground(Color.WHITE);
                            }

                            player_stack.setText(Helpers.float2String(s));
                        }
                    });
                    player_stack_click = false;
                });
            }
        } else {
            GameFrame.getInstance().getRebuy_now_menu().doClick();
        }
    }//GEN-LAST:event_player_stackMouseClicked

    private void avatarMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatarMouseClicked
        if (!javax.swing.SwingUtilities.isRightMouseButton(evt)) {
            return;
        }
        // Identity: right-clicking own avatar opens the identicon of THIS installation's
        // Ed25519 public identity. The dialog shows the visual icon and the 128-bit
        // fingerprint in 8 groups of 4, ready to be shared with a peer through an
        // out-of-band channel (WhatsApp, Telegram, voice).
        //
        // No "Verify identity" button: the user is verifying themselves, which has no
        // meaning here. Just a showcase to share the fingerprint with peers.
        //
        // Works for both roles (host and client). Unlike the legacy AES-session
        // identicon which only made sense for clients, the identity identicon is
        // symmetric — every node has exactly one Ed25519 keypair regardless of role.
        IdentityManager im = IdentityManager.getInstance();
        if (!im.isReady()) {
            return;
        }
        IdenticonDialog identicon = new IdenticonDialog(
                GameFrame.getInstance(), true, player_name.getText(),
                im.getPublicKey(), IdenticonDialog.Mode.IDENTITY, null);
        identicon.setLocationRelativeTo(GameFrame.getInstance());
        identicon.setVisible(true);
    }//GEN-LAST:event_avatarMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private com.tonikelope.coronapoker.TranslucentDisabledSpinner bet_spinner;
    private javax.swing.JPanel botonera;
    private javax.swing.JLabel hands_win;
    private com.tonikelope.coronapoker.Card holeCard1;
    private com.tonikelope.coronapoker.Card holeCard2;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JLabel latency_dot_widget;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JLayeredPane panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JPanel player_action_panel;
    private com.tonikelope.coronapoker.TranslucentDisabledButton player_allin_button;
    private com.tonikelope.coronapoker.TranslucentDisabledButton player_bet_button;
    private com.tonikelope.coronapoker.TranslucentDisabledButton player_check_button;
    private com.tonikelope.coronapoker.TranslucentDisabledButton player_fold_button;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JPanel player_pot_panel;
    private javax.swing.JLabel player_stack;
    private javax.swing.JPanel player_stack_panel;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    @Override
    public void setWinner(String msg) {
        this.winner = true;
        this.conta_win++;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.GREEN);

            setActionBackground(Color.GREEN);
            player_action.setForeground(Color.BLACK);
            setActionTextFitted(msg);
            setPlayerActionIcon("action/happy.png");

            if (conta_win > 0) {

                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            }

        });

    }

    public void refreshSecPotLabel() {

        // En run-it-twice la franja es POR CARA: cada cara reparte la MITAD del
        // bote, así que muestra el dinero ganado en ELLA (pagar - pagar_face_base)
        // y el beneficio contra la mitad del bote. Fuera de RIT (tag null) →
        // pagar y bote enteros, como siempre.
        final boolean is_rit = GameFrame.getInstance().getCrupier().getRitPotBoardTag() != null;

        final float fullbote = last_bote != null ? last_bote : bote;

        final float mibote = is_rit ? Crupier.splitPotForRunItTwice(fullbote)[0] : fullbote;

        final float dinero = is_rit ? Helpers.floatClean(pagar - pagar_face_base) : pagar;

        if (Helpers.float1DSecureCompare(0f, dinero) < 0 && GameFrame.getInstance().getCrupier().getBote().getSide_pot_count() > 0) {

            Helpers.GUIRun(() -> {
                sec_pot_win_label.setBackground(Color.BLACK);

                sec_pot_win_label.setForeground(Color.WHITE);

                sec_pot_win_label.setSize(player_action.getSize());

                sec_pot_win_label.setPreferredSize(sec_pot_win_label.getSize());

                int pos_x = Math.round((panel_cartas.getWidth() - sec_pot_win_label.getWidth()) / 2);

                int pos_y = Math.round((getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) / 2);

                sec_pot_win_label.setLocation(pos_x, pos_y);

                String[] botes = new String[botes_secundarios.size()];

                int i = 0;

                for (Integer b : botes_secundarios) {
                    botes[i++] = "#" + String.valueOf(b);
                }

                sec_pot_win_label.setText(String.join("+", botes) + " = " + Helpers.float2String(dinero) + " (" + Helpers.float2String(dinero - mibote) + ")");

                sec_pot_win_label.setVisible(true);
            });

        }
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.RED);

            setActionBackground(Color.RED);
            player_action.setForeground(Color.WHITE);

            holeCard1.desenfocar();
            holeCard2.desenfocar();

            setActionTextFitted(msg);
            setPlayerActionIcon("action/angry.png");

        });

    }

    @Override
    public void pagar(float pasta, Integer sec_pot) {

        this.pagar += pasta;

        if (sec_pot != null) {
            botes_secundarios.add(sec_pot);

            refreshSecPotLabel();
        }

    }

    @Override
    public void marcarBotePot(int sec_pot) {
        if (!botes_secundarios.contains(sec_pot)) {
            botes_secundarios.add(sec_pot);
        }
        refreshSecPotLabel();
    }

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(() -> {
            utg_icon.setVisible(true);
        });
    }

    public void setDecision(int dec) {

        this.decision = dec;

        reraise = false;

        renderDecisionVisual(dec);
    }

    // Render visual de una decisión (sin efectos), extraído de setDecision para
    // poder RE-PINTAR la última acción en el rewind de run-it-twice.
    private void renderDecisionVisual(int dec) {
        switch (dec) {
            case Player.CHECK:

                Helpers.GUIRun(() -> {
                    if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][1]);
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);
                    }

                    setPlayerActionIcon("action/up.png");
                });

                break;
            case Player.BET:
                Helpers.GUIRun(() -> {
                    final float apuesta_actual_snapshot = GameFrame.getInstance().getCrupier().getApuesta_actual();
                    final int conta_raise_snapshot = GameFrame.getInstance().getCrupier().getConta_raise();
                    // Lectura ÚNICA del volátil bet: guard y texto deben usar
                    // exactamente el mismo valor (ver nota en ALLIN).
                    final float bet_snapshot = bet;
                    if (Helpers.float1DSecureCompare(apuesta_actual_snapshot, bet_snapshot) < 0 && Helpers.float1DSecureCompare(0f, apuesta_actual_snapshot) < 0) {
                        setActionTextFitted((conta_raise_snapshot > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.float2String(bet_snapshot - apuesta_actual_snapshot) + ")");

                        if (conta_raise_snapshot > 0) {
                            reraise = true;
                        }
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.float2String(bet_snapshot));
                    }
                    setPlayerActionIcon("action/bet.png");
                });
                break;
            case Player.ALLIN:
                Helpers.GUIRun(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0]);

                    final float apuesta_actual_snapshot = GameFrame.getInstance().getCrupier().getApuesta_actual();
                    // Lectura ÚNICA de bet+stack para guard y texto: son
                    // volátiles y el dinero del all-in se mueve en dos pasos
                    // (bet sube, luego stack baja) en otro hilo. Con lecturas
                    // separadas el guard podía ver la suma inflada a mitad de
                    // setBet y el texto la ya asentada, colando un importe
                    // negativo en la etiqueta ("ALL IN (+-0.90)").
                    final float total_allin = bet + stack;
                    if (Helpers.float1DSecureCompare(apuesta_actual_snapshot, total_allin) < 0) {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0] + " (+" + Helpers.float2String(total_allin - apuesta_actual_snapshot) + ")");
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);
                    }
                    setPlayerActionIcon("action/glasses.png");
                });
                break;
            default:
                Helpers.GUIRun(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0]);

                    setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);

                    setPlayerActionIcon("action/down.png");
                });
                break;
        }

        Helpers.GUIRunAndWait(() -> {
            if (!reraise) {

                if (dec == Player.CHECK && Helpers.float1DSecureCompare(0f, call_required) == 0) {
                    setActionBackground(new Color(0, 130, 0));
                    player_action.setForeground(Color.WHITE);
                } else {

                    setActionBackground(ACTIONS_COLORS[dec - 1][0]);
                    player_action.setForeground(ACTIONS_COLORS[dec - 1][1]);
                }

                setPlayerPotBackground(ACTIONS_COLORS[dec - 1][0]);
                player_pot.setForeground(ACTIONS_COLORS[dec - 1][1]);
            } else {
                setActionBackground(RERAISE_BACK_COLOR);
                player_action.setForeground(RERAISE_FORE_COLOR);

                setPlayerPotBackground(RERAISE_BACK_COLOR);
                player_pot.setForeground(RERAISE_FORE_COLOR);
            }

        });
    }

    // Run-it-twice rewind: re-aplica el render de la última acción guardada y
    // limpia el verde/rojo de ganador/perdedor de SIDE-A, dejando las hole cards
    // reveladas. No toca pots ni stacks (el bote persiste entre sides).
    @Override
    public void repaintLastAction() {
        this.winner = false;
        this.loser = false;
        // Limpia la franja de side pots de SIDE-A (se recalcula en SIDE-B).
        this.botes_secundarios.clear();
        // Línea base de CARA-B = lo acumulado en CARA-A: la franja de CARA-B
        // muestra 'pagar - base', es decir SOLO lo que se gane en CARA-B (pagar
        // sigue acumulando ambas caras para la contabilidad).
        this.pagar_face_base = this.pagar;
        // Re-enfoca las hole cards: el showdown de SIDE-A atenúa las de los
        // perdedores; en SIDE-B deben volver a verse brillantes (se reevalúan).
        Helpers.GUIRun(() -> {
            holeCard1.enfocar();
            holeCard2.enfocar();
            sec_pot_win_label.setVisible(false);
            // Borde neutro: en el flujo normal lo restaura finTurno (que el
            // rewind no llama) y renderDecisionVisual solo repinta borde en
            // ALLIN/FOLD; sin esto el verde/rojo de ganador/perdedor de SIDE-A
            // sobreviviría en CHECK/BET (p.ej. quien cubre el all-in).
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }
        });
        renderDecisionVisual(this.decision);
    }

    @Override
    public String getLastActionString() {

        String action = nickname + " ";

        switch (this.getDecision()) {
            case Player.FOLD:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                break;
            case Player.CHECK:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                break;
            case Player.BET:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                break;
            case Player.ALLIN:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                ;
                break;
            default:
                break;
        }

        return action;
    }

    public void setBuyin(int buyin) {
        this.buyin = buyin;

    }

    @Override
    public void showCards(String jugada) {
        this.muestra = true;
        Helpers.GUIRun(() -> {
            if (GameFrame.getInstance().getCrupier().getRabbit_players().containsKey(nickname)) {
                setActionBackground(Color.BLUE);
                setPlayerActionIcon("action/rabbit_action.png");
            } else {
                setActionBackground(new Color(51, 153, 255));
            }
            player_action.putClientProperty("i18n.key", null); // Limpiamos fantasma
            player_action.setForeground(Color.WHITE);
            setActionTextFitted(Translator.translate("ui.muestra_prefix") + jugada + Translator.translate("ui.suffix_close"));
        });
    }

    private volatile java.awt.Font orig_action_font = null;

    /**
     * Sets {@code msg} on the action label, auto-shrinking the font (measured
     * with FontMetrics) so a long hand name fits the label width, and restoring
     * the original size when it fits again. Must run on the EDT.
     */
    private void setActionTextFitted(String msg) {

        java.awt.Font base_font = (orig_action_font != null) ? orig_action_font : player_action.getFont();

        java.awt.Insets insets = player_action.getInsets();

        int available_width = (player_action.getWidth() > 0 ? player_action.getWidth() : player_action.getPreferredSize().width) - (insets != null ? insets.left + insets.right : 0);

        java.awt.Font fitted_font = Helpers.fitFontToWidth(player_action, msg, base_font, available_width, Math.max(9, Math.round(base_font.getSize() * 0.5f)));

        if (fitted_font.getSize() < base_font.getSize()) {
            orig_action_font = base_font;
            player_action.setFont(fitted_font);

        } else if (orig_action_font != null) {
            player_action.setFont(orig_action_font);
            orig_action_font = null;
        }

        player_action.setText(msg);
    }

    // Jugada en etiqueta NEUTRA (gris en reposo, no el azul de showCards) durante el
    // destape secuencial del showdown — espejo de RemotePlayer.showJugadaNeutral pero
    // para la propia mano del local (que ya está boca arriba). Encoge la fuente con
    // nombres de jugada largos, igual que los remotos.
    public void showJugadaNeutral(String jugada) {
        Helpers.GUIRun(() -> {
            setActionBackground(new Color(204, 204, 204, 75));
            player_action.setForeground(Color.WHITE);

            setActionTextFitted(jugada);
        });
    }

    @Override
    public void resetBote() {
        this.bet = 0f;
        this.last_bote = this.bote;
        this.bote = 0f;
    }

    @Override
    public void setAvatar() {

        int h = player_pot.getHeight();
        if (h <= 0) {
            java.awt.Dimension prefDim = player_pot.getPreferredSize();
            if (prefDim != null && prefDim.height > 0) {
                h = prefDim.height;
            }
        }
        if (h <= 0 && avatar.getIcon() != null) {
            int iconH = avatar.getIcon().getIconHeight();
            if (iconH > 0) {
                h = iconH;
            }
        }
        if (h <= 0) {
            h = 64;
        }

        ImageIcon avatar;

        if (GameFrame.getInstance().getSala_espera().getAvatar() != null) {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(GameFrame.getInstance().getSala_espera().getAvatar().getAbsolutePath()).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));
        } else {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));
        }

        final int finalH = h;
        Helpers.GUIRun(() -> {
            getAvatar().setPreferredSize(new Dimension(finalH, finalH));

            getAvatar().setIcon(avatar);

            getAvatar().setVisible(true);
        });

    }

    @Override
    public boolean isCalentando() {

        return (spectator && Helpers.float1DSecureCompare(0f, stack) < 0);
    }

    @Override
    public boolean isActivo() {
        return (!exit && !spectator);
    }

    @Override
    public synchronized void setPagar(float pagar) {
        this.pagar = pagar;
    }

    @Override
    public void destaparCartas(boolean sound) {

        if (getHoleCard1().isIniciada() && getHoleCard1().isTapada()) {

            if (sound) {
                Audio.playWavResource("misc/uncover.wav", false);
            }

            getHoleCard1().destapar(false);

            getHoleCard2().destapar(false);
        }
    }

    @Override
    public void ordenarCartas() {
        if (getHoleCard1().getValorNumerico() != -1 && getHoleCard2().getValorNumerico() != -1 && getHoleCard1().getValorNumerico() < getHoleCard2().getValorNumerico()) {

            //Ordenamos las cartas para mayor comodidad
            String valor1 = this.holeCard1.getValor();
            String palo1 = this.holeCard1.getPalo();
            boolean desenfocada1 = this.holeCard1.isDesenfocada();

            this.holeCard1.actualizarValorPaloEnfoque(this.holeCard2.getValor(), this.holeCard2.getPalo(), this.holeCard2.isDesenfocada());
            this.holeCard2.actualizarValorPaloEnfoque(valor1, palo1, desenfocada1);
        }
    }

    @Override
    public void setSpectatorBB(boolean bb) {
        this.spectator_bb = bb;
    }

    @Override
    public void checkGameOver() {
        if (isActivo() && Helpers.float1DSecureCompare(0f, getEffectiveStack()) == 0) {

            Helpers.GUIRun(() -> {
                setPlayerActionIcon("action/skull.png");
                setOpaque(true);
                setBackground(Color.RED);

            });

        }
    }

    @Override
    public void setPlayerActionIcon(String icon) {

        if (!isTimeout() || "action/timeout.png".equals(icon) || icon == null) {
            if (!"action/timeout.png".equals(icon)) {
                player_action_icon = icon;
            }

            Helpers.GUIRun(() -> {
                player_action.setIcon(icon != null ? new ImageIcon(new ImageIcon(getClass().getResource("/images/" + icon)).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)) : null);

                repaint();
            });
        }
    }

    public void hidePlayerActionIcon() {

        Helpers.GUIRun(() -> {
            player_action.setIcon(null);
        });

    }

    @Override
    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per) {
        Helpers.GUIRun(() -> {
            setActionBackground(ganador ? new Color(120, 200, 0) : new Color(230, 70, 0));
            player_action.setForeground(ganador ? Color.BLACK : Color.WHITE);
            setActionTextFitted(jugada.getName() + (win_per >= 0 ? " (" + win_per + "%)" : " (--%)"));
            setPlayerActionIcon(null);
        });
    }

    @Override
    public void setContaWin(int conta) {
        this.conta_win = conta;

        if (this.conta_win > 0) {
            Helpers.GUIRun(() -> {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            });
        }
    }

    @Override
    public int getContaWin() {
        return this.conta_win;
    }
}
