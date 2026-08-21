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
import java.awt.RenderingHints;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
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
 * Swing panel for the local human player's seat: action buttons, bet spinner,
 * stack/pot labels, hole cards and showdown/overlay effects.
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
    // Font shrink factor for the action buttons in compact view (2x2 grid): with
    // less width per button, the normal font size no longer fits.
    private static final float COMPACT_ACTION_FONT_FACTOR = 0.62f;

    private final ConcurrentHashMap<JButton, Color[]> action_button_colors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JButton, Boolean> action_button_armed = new ConcurrentHashMap<>();
    private final Object pre_pulsar_lock = new Object();
    private final Object zoom_lock = new Object();
    private final Object rabbit_lock = new Object();

    private volatile String nickname;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile double stack = 0;
    private volatile double bet = 0;
    private volatile boolean utg = false;
    private volatile int decision = Player.NODEC;
    private volatile boolean spectator = false;
    private volatile double pagar = 0;
    // Baseline of 'pagar' at the start of the current run-it-twice side (0 on
    // SIDE-A, SIDE-A's total when entering SIDE-B). Money won on the side is
    // 'pagar - pagar_face_base', derived from the single source of truth
    // (pagar), so it can never drift out of sync. Unused outside RIT.
    private volatile double pagar_face_base = 0;
    private volatile double bote = 0;
    private volatile Double last_bote = null;
    private volatile boolean exit = false;
    private volatile boolean turno = false;
    private volatile Timer auto_action = null;
    private volatile AutoActionDialog auto_action_dialog = null;
    private volatile boolean timeout = false;
    private volatile boolean boton_mostrar = false;
    private volatile boolean winner = false;
    private volatile boolean loser = false;
    // Showdown (RESALTAR_JUGADA_SHOWDOWN): cards of THIS player's hand (no kickers) to highlight
    // on hover over their action label. The three snapshot_ fields hold the state to restore on
    // mouse-exit: each table card's focus before the hover (the winner's highlight comes back
    // as-is) and the label's background/foreground colors. Touched only on the EDT.
    private volatile java.util.List<Card> showdown_hand_cards = null;
    private java.util.Map<Card, Boolean> showdown_focus_snapshot = null;
    private Color showdown_action_bg_snapshot = null;
    private Color showdown_action_fg_snapshot = null;
    private volatile Double apuesta_recuperada = null;
    private volatile boolean click_recuperacion = false;
    private volatile double call_required;
    private volatile double min_raise;
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
    // Compact local view (VISTA_COMPACTA == 3): the action button bar switches from a
    // vertical stack (5 rows) to a 2x2 grid + spinner, and the buttons lose their icon
    // and shrink their font, so the text fits and the LocalPlayer's height goes down.
    private volatile boolean botonera_compacta = false;
    // Compact button-bar sub-layout: false = turn (2x2 grid), true = AUTO state
    // (AUTO-call on top, AUTO-fold below, both full-width and WITH icon).
    // Kept in sync by esTuTurno / activarPreBotones / desActivarPreBotones.
    private volatile boolean botonera_compacta_auto = false;
    // Reference width of the button bar (the normal .form layout's width). In compact
    // mode it is fixed to this value in BOTH sub-layouts (2x2 and auto) so the
    // LocalPlayer doesn't jump width when toggling turn/auto or entering/leaving compact.
    private int botonera_ref_width = -1;
    // Base font size (from the .form, pre-zoom) of the action buttons, captured in the
    // constructor; scaled by COMPACT_ACTION_FONT_FACTOR in compact mode.
    private int action_font_base = 22;
    private volatile String player_action_icon = null;
    private volatile Timer icon_zoom_timer = null;
    private volatile URL chat_notify_image_url = null;
    private volatile Long chat_notify_thread = null;
    private final GifLabel chat_notify_label = new GifLabel();
    // Shuffle GIF overlay (MUTED, looping) + white border while this LOCAL player processes
    // its step of the SRA cascade. Mirrors RemotePlayer's; invoked by GameFrame's controller
    // (onShuffleTurn) when the turn belongs to the local nick. Synchronized across all peers.
    private final GifLabel shuffle_cascade_gif_label = new GifLabel();
    private volatile ImageIcon shuffle_cascade_icon = null;
    private volatile int shuffle_cascade_frames = 0;
    private volatile String shuffle_cascade_icon_url = null;
    private volatile Color shuffle_border_saved = null;
    private volatile boolean shuffle_border_active = false;
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
            // Do NOT stop icon_zoom_timer here: stopActionTimer is called between hands,
            // and stopping the zoom timer left the next hand without setAvatar (timer
            // already stopped -> zoomIcons never fires -> invisible avatar). The GC leak
            // that motivated the stop is preferable to a visible bug.
        });
    }

    // Telemetry: the LatencyDot widget is placed by hand in the .form (NetBeans
    // visual editor) and wired up by calling setLatencyDot.
    private volatile LatencyDot latency_dot = null;

    public LatencyDot getLatencyDot() {
        return latency_dot;
    }

    public void setLatencyDot(LatencyDot dot) {
        this.latency_dot = dot;
    }

    /**
     * Telemetry: updates the LatencyDot widget. No-op if it hasn't been wired
     * up yet via setLatencyDot.
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

    // The seat has ROUNDED corners: if it were truly opaque, Swing would not repaint the
    // felt background behind it, and the corners outside the arc would show garbage. So
    // the seat is NEVER really opaque: setOpaque is intercepted and the fill INTENT is
    // remembered instead, painted by us (rounded) in paintComponent; Swing repaints the
    // felt behind it and the corners stay clean. Only affects the static highlighted
    // (eliminated, red) state, so there's no rendering-cost concern.
    private volatile boolean rounded_fill = false;

    @Override
    public void setOpaque(boolean isOpaque) {
        this.rounded_fill = isOpaque;
        super.setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (rounded_fill) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            } finally {
                g2d.dispose();
            }
            // super.paintComponent is NOT called: Swing has already repainted the felt
            // behind it (the seat is non-opaque) and the rounded fill goes on top; calling
            // super would paint a rectangular background underneath.
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

    public void setRabbitJugada(String jugada, java.util.List<Card> rabbitHandCards) {
        this.showdown_hand_cards = showdownHandAfterRabbit(
                this.muestra, this.showdown_hand_cards, rabbitHandCards);

        Helpers.GUIRun(() -> {
            setPlayerActionIcon("action/rabbit_action.png");
            setActionBackground(Color.BLUE);
            getPlayer_action().setForeground(Color.WHITE);
            setActionTextFitted(jugada);

        });
    }

    static java.util.List<Card> showdownHandAfterRabbit(boolean cardsAlreadyShown,
            java.util.List<Card> currentHand, java.util.List<Card> rabbitHand) {
        return cardsAlreadyShown && rabbitHand != null ? rabbitHand : currentHand;
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

                    chat_notify_thread = Thread.currentThread().getId();

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
                        if (Thread.currentThread().getId() == chat_notify_thread) {
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

                    if (Thread.currentThread().getId() == chat_notify_thread) {
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

    // The local position chip sits bottom-left of the first hole card (same
    // anchor as refreshPositionChipIcons): (0, holeCard1.height - chip.height)
    // inside panel_cartas. Returns its on-screen center, or null if the seat
    // isn't showing.
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

        return GameFrame.THINK_TIME - response_counter;
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

    public void setApuesta_recuperada(Double apuesta_recuperada) {
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

    // While the card carrying the BIG position chip is flipping/crossing (hc1 reveal and
    // swap), the Crupier forces it HIDDEN via this flag. The nick click — which cycles
    // its rendering (normal/70%/hidden) — is also BLOCKED while it's active, so it can't
    // restore the chip mid-animation. Once deactivated, the Crupier restores it via
    // refreshPositionChipIcons (which honors the flag). Doesn't change when/how chips
    // are placed otherwise.
    private volatile boolean chip_forced_hidden = false;

    public void setChipForcedHidden(boolean hidden) {
        this.chip_forced_hidden = hidden;
        if (hidden) {
            Helpers.GUIRun(() -> chip_label.setVisible(false));
        }
    }

    public boolean isChipForcedHidden() {
        return chip_forced_hidden;
    }

    public void refreshPositionChipIcons() {

        // Defensive: a call can arrive before the player is seated (nickname still
        // null); with no chip to paint, there's nothing to do.
        if (this.nickname == null) {
            return;
        }

        ImageIcon chip_label_icon;

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_BB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_SB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            // Heads-up the dealer is also UTG; if they straddle, use the combined
            // dealer+straddle chip (the DEALER branch wins over the straddle branch
            // below, so it's resolved here).
            boolean dealer_straddle = GameFrame.getInstance().getCrupier().isStraddle_posted()
                    && this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())
                    && !GameFrame.getInstance().getCrupier().isDead_dealer();
            String dealer_img = dealer_straddle ? "/images/dealer_straddle.png"
                    : (GameFrame.getInstance().getCrupier().isDead_dealer() ? "/images/dead_dealer.png" : "/images/dealer.png");
            Helpers.setScaledIconLabel(player_name, getClass().getResource(dealer_img), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = dealer_straddle ? Helpers.IMAGEN_DEALER_STRADDLE
                    : (GameFrame.getInstance().getCrupier().isDead_dealer() ? Helpers.IMAGEN_DEAD_DEALER : Helpers.IMAGEN_DEALER);
        } else if (GameFrame.getInstance().getCrupier().isStraddle_posted()
                && this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/straddle.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_STRADDLE;
        } else {
            chip_label_icon = null;
        }

        final int chip_state = GameFrame.LOCAL_POSITION_CHIP;
        // Suppressed while the traveling chip is in flight (until it lands): don't paint
        // the big chip even if called (e.g. from a table re-layout).
        final boolean suppressed = GameFrame.getInstance().getCrupier() != null
                && GameFrame.getInstance().getCrupier().isBigChipSuppressed(this);
        Helpers.GUIRun(() -> {
            // In compact + local view (level 3) the card sits at half height and the big
            // chip on top would look disproportionate, so it's hidden (the small icon next
            // to the nick is kept). Leaving level 3 triggers a refresh that repaints it
            // according to the active position.
            if (isActivo() && chip_label_icon != null && chip_state != GameFrame.LOCAL_POS_CHIP_HIDDEN && !suppressed && GameFrame.VISTA_COMPACTA != 3) {
                // Intermediate state: the chip is shown at 70% opacity over the cards.
                ImageIcon shown = (chip_state == GameFrame.LOCAL_POS_CHIP_DIM)
                        ? Helpers.translucentIcon(chip_label_icon, 0.7f) : chip_label_icon;
                chip_label.setIcon(shown);
                chip_label.setSize(shown.getIconWidth(), shown.getIconHeight());
                chip_label.setLocation(0, getHoleCard1().getHeight() - chip_label.getHeight());
                chip_label.setVisible(true);

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

        });

    }

    @Override
    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            // The hand reset (nuevaMano) only runs for active players, so the highlightable
            // hand from the last one they played would stay stuck on the seat while they're
            // a spectator. It's discarded without restoring the label color: the spectator
            // repaint below leaves it as it should be.
            Helpers.GUIRun(this::discardShowdownHandHighlight);
            this.showdown_hand_cards = null;

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

                player_stack.setText(Helpers.money2String(stack));

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
                    setPlayerActionIcon(Helpers.doubleSecureCompare(0f, getEffectiveStack()) == 0 ? "action/ghost.png" : "action/calentando.png");
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

            if (val && GameFrame.errorRedSonidoOn()) {

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

    public double getPagar() {
        return pagar;
    }

    public double getBote() {
        return bote;
    }

    public boolean isExit() {
        return exit;
    }

    public void setExit() {

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
                // On leaving, the call-cost overlay no longer applies: hide it (it wouldn't
                // refresh itself just because the local player leaves the betting loop).
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
            // cursor) is shown for host and client alike — but only while there IS
            // an identity to show: if the keypair could not be loaded the handler
            // bails out, and a hand cursor over a dead click would be a lie.
            if (IdentityManager.getInstance().isReady()) {
                Helpers.setTranslatedToolTip(avatar, "ui.click_identity_identicon");
                avatar.setCursor(new Cursor(Cursor.HAND_CURSOR));
            } else {
                avatar.setToolTipText(null);
                avatar.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });
    }

    public double getStack() {
        return stack;
    }

    // Live rolling animation of the stack label (EDT-confined). The renderer only writes
    // the text; the color is still set by setStack/setStackDisplay. Lazily created.
    private RollingCounter stack_roller;

    private RollingCounter stackRoller() {
        if (stack_roller == null) {
            stack_roller = new RollingCounter((v) -> player_stack.setText(Helpers.money2String(v)),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return stack_roller;
    }

    public synchronized void setStack(double stack) {
        this.stack = Helpers.doubleClean(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(() -> {
                if (getNickname() != null && GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                    setPlayerStackBackground(Color.YELLOW);
                    player_stack.setForeground(Color.BLACK);
                    // Pending "X + rebuy": composite (non-numeric) text -> invalidate the
                    // roller so the next roll jumps instead of animating from here.
                    player_stack.setText(Helpers.money2String(stack) + " + " + Helpers.money2String((int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname())));
                    stackRoller().invalidate();

                } else {

                    if (GameFrame.hasRebought(nickname)) {
                        setPlayerStackBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        setPlayerStackBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    // Rolls the number to the new stack (constant speed; off/recover jumps).
                    // If the action is about to fly a chip (defer_counter_rolls), it does NOT
                    // roll here: the label stays at its previous value and rollCountersToModel
                    // rolls it when the chip lands, together with the pot and the bet.
                    if (!defer_counter_rolls) {
                        stackRoller().roll(stack, GameFrame.isCounterRollEnabled());
                    }
                }
            });
        }
    }

    // Paints ONLY the stack label with 'value' (without touching the model): used by the
    // animated stack-fill counter (opening/rebuy) to roll the number frame by frame. NOT
    // synchronized on purpose (runs on the EDT from the counter's Timer, and the caller
    // that defers the model may hold the player's monitor). Doesn't use setStack's yellow
    // "+ rebuy" branch: during the count we want the number rolling, not the pending
    // amount. Honors player_stack_click.
    @Override
    public void setStackDisplay(double value) {
        if (player_stack_click) {
            return;
        }
        Helpers.GUIRun(() -> {
            if (GameFrame.hasRebought(nickname)) {
                setPlayerStackBackground(Color.CYAN);
                player_stack.setForeground(Color.BLACK);
            } else {
                setPlayerStackBackground(new Color(51, 153, 0));
                player_stack.setForeground(Color.WHITE);
            }
            // Snaps instantly (the fill animation already animates frame by frame); keeps
            // the roller's displayed value in sync so the next live roll starts correctly.
            stackRoller().set(value);
        });
    }

    public int getBuyin() {
        return buyin;
    }

    public double getBet() {
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
            action_font_base = player_check_button.getFont().getSize();
            botonera_ref_width = botonera.getPreferredSize().width;
            installShowdownHandHighlight();
            // Avatar magnifier (with the seat's stack shown alongside): same source that
            // setAvatar queries (the avatar chosen in the waiting room, or "" for the
            // default one).
            AvatarZoomOverlay.install(avatar, player_stack, player_name, () -> {
                java.io.File propio = GameFrame.getInstance().getSala_espera().getAvatar();
                return propio != null ? propio.getAbsolutePath() : "";
            });
            // Optional wiring to the .form's latency_dot_widget (if present).
            try {
                java.lang.reflect.Field f = getClass().getDeclaredField("latency_dot_widget");
                f.setAccessible(true);
                Object widget = f.get(this);
                if (widget instanceof LatencyDot) {
                    setLatencyDot((LatencyDot) widget);
                    ((LatencyDot) widget).applyZoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                }
            } catch (NoSuchFieldException nsfe) {
                // OK: not yet added to the .form.
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
                public void mouseReleased(MouseEvent e) {
                    if (!Helpers.isRealClick(e)) {
                        return;
                    }
                    chat_notify_label.setVisible(false);
                    Helpers.threadRun(() -> {
                        synchronized (chat_notify_label) {

                            chat_notify_label.notifyAll();
                        }
                    });
                }
            });
            panel_cartas.add(chat_notify_label, Integer.valueOf(1001));
            shuffle_cascade_gif_label.setVisible(false);
            shuffle_cascade_gif_label.setFocusable(false);
            // Empty listener that consumes the click (like in RemotePlayer): keeps it from
            // passing through the label to the card below. Layer 1002 (above chip 1000 /
            // chat_notify 1001).
            shuffle_cascade_gif_label.addMouseListener(new MouseAdapter() {
            });
            panel_cartas.add(shuffle_cascade_gif_label, Integer.valueOf(1002));
            chip_label.setVisible(false);
            chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chip_label.setOpaque(false);
            chip_label.setFocusable(false);
            chip_label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!Helpers.isRealClick(e)) {
                        return;
                    }
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
            // The bet spinner's editor is OPAQUE by default under Nimbus, and its white
            // fill "shows through" the spinner's band. Made non-opaque right at
            // construction (not only in setSpinnerColors, which runs on the first turn)
            // so the disabled spinner looks the same from startup as after the first hand.
            bet_spinner.getEditor().setOpaque(false);
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

    /**
     * Shows the shuffle GIF (MUTED, looping) + white border over this LOCAL
     * player. Invoked by GameFrame's controller from its serializer thread (NOT
     * the EDT) when the cascade turn belongs to the local nick. Loads the GIF
     * SYNCHRONOUSLY (do not call from the EDT) and paints on the EDT.
     */
    @Override
    public void showShuffleCascadeOverlay() {
        final ImageIcon icon;
        try {
            icon = ensureShuffleCascadeIcon();
        } catch (Exception ex) {
            Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        if (icon == null) {
            return;
        }
        final int frames = shuffle_cascade_frames;
        if (frames <= 0) {
            return; // GIF without a Graphic Control Extension (deck mod): the imageUpdate loop wouldn't stop on hide
        }
        Helpers.GUIRun(() -> {
            int max_width = panel_cartas.getWidth();
            int new_height = panel_cartas.getHeight();
            if (icon.getIconHeight() <= 0 || new_height <= 0) {
                return;
            }
            int new_width = (int) Math.round((icon.getIconWidth() * (double) new_height) / icon.getIconHeight());
            if (max_width > 0 && new_width > max_width) {
                new_height = (int) Math.round(((double) new_height * max_width) / new_width);
                new_width = max_width;
            }
            shuffle_cascade_gif_label.setBarrier(null);
            shuffle_cascade_gif_label.setIcon(icon, frames);
            shuffle_cascade_gif_label.setRepeat(Integer.MAX_VALUE);
            shuffle_cascade_gif_label.setSize(new_width, new_height);
            shuffle_cascade_gif_label.setPreferredSize(shuffle_cascade_gif_label.getSize());
            shuffle_cascade_gif_label.setOpaque(false);
            shuffle_cascade_gif_label.setLocation(Math.round((panel_cartas.getWidth() - new_width) / 2f), Math.round((getHoleCard1().getHeight() - new_height) / 2f));
            shuffle_cascade_gif_label.setVisible(true);
            if (!shuffle_border_active) {
                shuffle_border_saved = border_color;
                shuffle_border_active = true;
            }
            border_color = java.awt.Color.WHITE;
            repaint();
        });
    }

    /**
     * Hides the shuffle overlay and restores the previous border. Idempotent.
     */
    @Override
    public void hideShuffleCascadeOverlay() {
        Helpers.GUIRun(() -> {
            shuffle_cascade_gif_label.setVisible(false);
            shuffle_cascade_gif_label.setIcon((javax.swing.Icon) null);
            if (shuffle_border_active) {
                // Only restore if the border is still the white we set: if other code changed
                // it meanwhile (e.g. the betting-turn highlight), honor that instead.
                if (border_color == java.awt.Color.WHITE) {
                    border_color = shuffle_border_saved;
                    repaint();
                }
                shuffle_border_active = false;
            }
        });
    }

    /**
     * Decodes (once per instance, cache-busted) the current deck's shuffle.gif
     * ImageIcon and counts its frames; null if there's no GIF. Mirrors
     * RemotePlayer.ensureShuffleCascadeIcon.
     */
    private ImageIcon ensureShuffleCascadeIcon() throws Exception {
        URL url = Crupier.shuffleGifUrl();
        if (url == null) {
            return null;
        }
        String url_key = url.toString();
        ImageIcon cached = shuffle_cascade_icon;
        if (cached != null && url_key.equals(shuffle_cascade_icon_url)) {
            return cached;
        }
        ImageIcon icon = new ImageIcon(new URL(url.toString() + "#cascade" + System.nanoTime()));
        long t0 = System.nanoTime();
        while ((icon.getIconHeight() == 0 || icon.getIconWidth() == 0)
                && System.nanoTime() - t0 < 3_000_000_000L) {
            Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
        }
        if (icon.getIconHeight() == 0 || icon.getIconWidth() == 0) {
            return null;
        }
        shuffle_cascade_frames = Helpers.getGIFFramesCount(url);
        shuffle_cascade_icon = icon;
        shuffle_cascade_icon_url = url_key;
        return icon;
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

    // Live rolling animation of the player's bet label (player_pot = 'bote', their
    // accumulated contribution for the hand). The renderer shows "----" when it's 0.
    // EDT-confined.
    private RollingCounter bet_roller;

    private RollingCounter betRoller() {
        if (bet_roller == null) {
            bet_roller = new RollingCounter(
                    (v) -> player_pot.setText(Helpers.doubleSecureCompare(0f, v) < 0 ? Helpers.money2String(v) : "----"),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return bet_roller;
    }

    // Live-roll deferral flag: set by the action handler BEFORE the chip flies, so the
    // stack/bet don't get ahead of it. volatile: written by the action thread and read by
    // setStack/setBet (on the EDT) and rollCountersToModel.
    private volatile boolean defer_counter_rolls = false;

    @Override
    public void setCounterRollDeferred(boolean deferred) {
        this.defer_counter_rolls = deferred;
    }

    @Override
    public void rollCountersToModel() {
        Helpers.GUIRun(() -> {
            this.defer_counter_rolls = false;
            stackRoller().roll(this.stack, GameFrame.isCounterRollEnabled());
            betRoller().roll(this.bote, GameFrame.isCounterRollEnabled());
        });
    }

    public synchronized void setBet(double new_bet) {

        double old_bet = bet;

        bet = Helpers.doubleClean(new_bet);

        if (Helpers.doubleSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.doubleClean(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            // If the action is about to fly a chip (defer), it does NOT roll here: the bet
            // stays put and rollCountersToModel rolls it when it lands, along with the
            // stack and pot.
            if (!defer_counter_rolls) {
                betRoller().roll(bote, GameFrame.isCounterRollEnabled());
            }
        });

    }

    public synchronized double postAnte(double ante) {

        if (Helpers.doubleSecureCompare(0f, stack) >= 0) {
            return 0f; // already all-in / no chips: nothing to ante
        }

        double real;

        if (Helpers.doubleSecureCompare(ante, stack) < 0) {
            real = Helpers.doubleClean(ante);
        } else {
            // Doesn't cover the full ante: all-in for the ante.
            real = Helpers.doubleClean(stack);
            setDecision(Player.ALLIN);
        }

        this.bote += real;
        setStack(stack - real);

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            // If the ante chip is about to fly to the pot (defer), it does NOT roll here:
            // it's deferred and rollCountersToModel rolls it when it lands, along with the
            // stack and pot.
            if (!defer_counter_rolls) {
                betRoller().roll(bote, GameFrame.isCounterRollEnabled());
            }
        });

        return real;
    }

    public synchronized double postStraddle(double amount) {

        double want = Helpers.doubleClean(amount);

        if (Helpers.doubleSecureCompare(want, stack) < 0) {
            setBet(want);
            return want;
        }

        // Doesn't cover the full straddle: all-in for the straddle.
        double all = Helpers.doubleClean(stack);
        setBet(all);
        setDecision(Player.ALLIN);
        return all;
    }

    public JLabel getPlayer_stack() {
        return player_stack;
    }

    // silent: the animated rebuy counter (Crupier.animateRebuyStacks) already fired the
    // cash register for the whole batch -> NOT repeated here. On the non-animated path
    // (silent=false) it sounds as always, once per rebuy.
    public synchronized void reComprar(int cantidad, boolean silent) {

        // Re-check at apply time (anti-stale / anti-cheat): never exceed the table ceiling
        // even if the requested amount was larger or the stack changed between the request
        // and the start of the hand. headroom 0 -> rebuy voided.
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
        if (!silent && GameFrame.cajaSonidoOn()) {
            Audio.playWavResource("misc/cash_register.wav");
        }

        // If the fill animation animates the rebuy (silent), IT paints the text+CYAN frame by
        // frame (setStackDisplay, which already picks CYAN via hasRebought); painting it here
        // too would flash the final value mid-roll.
        if (!player_stack_click && !silent) {
            Helpers.GUIRun(() -> {
                player_stack.setText(Helpers.money2String(stack));
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

        // Stack-fill gate: if this player is mid-fill on their stack (opening or rebuy), do
        // NOT activate their turn (border + buttons) until it finishes. The rest of the
        // game isn't held up by the animation; only this turn waits.
        GameFrame.getInstance().getCrupier().awaitStackFillIfPending(this.nickname);

        turno = true;

        // On your turn the compact button bar returns to the 2x2 grid (the 4 action
        // buttons + spinner). No effect outside level 3.
        updateCompactLayout(false);

        GameFrame.getInstance().getCrupier().disableAllPlayersTimeout();

        if (this.getDecision() == Player.NODEC) {
            if (GameFrame.tuTurnoSonidoOn()) {
                Audio.playWavResource("misc/yourturn.wav");
            }

            call_required = Helpers.doubleClean(GameFrame.getInstance().getCrupier().getApuesta_actual() - bet);

            min_raise = BetRules.minRaiseIncrement(GameFrame.getInstance().getCrupier().getUltimo_raise(), GameFrame.getInstance().getCrupier().getCiega_grande());

            Helpers.GUIRun(() -> {
                desarmarBotonesAccion();

                setPlayerBorder(Color.ORANGE);

                player_allin_button.setText(Translator.translate("game.all_in"));
                player_allin_button.putClientProperty("i18n.key", "game.all_in");
                player_allin_button.setEnabled(true);

                setActionButtonIcon(player_allin_button, "/images/action/glasses.png");

                player_fold_button.setText(Translator.translate("player.no_ir"));
                player_fold_button.putClientProperty("i18n.key", "player.no_ir");
                player_fold_button.setEnabled(true);
                player_fold_button.setBackground(Color.DARK_GRAY);
                player_fold_button.setForeground(Color.WHITE);

                setActionButtonIcon(player_fold_button, "/images/action/down.png");

                setActionBackground(new Color(204, 204, 204, 75));

                player_action.setForeground(Color.WHITE);

                //Check whether we can cover the current bet
                if (Helpers.doubleSecureCompare(call_required, stack) < 0) {

                    player_check_button.setEnabled(true);

                    setActionButtonIcon(player_check_button, "/images/action/up.png");

                    if (Helpers.doubleSecureCompare(0f, call_required) == 0) {
                        player_check_button.setText(Translator.translate("game.pasar"));
                        player_check_button.putClientProperty("i18n.key", "game.pasar");
                        player_check_button.setBackground(new Color(0, 130, 0));
                        player_check_button.setForeground(Color.WHITE);

                        player_fold_button.setBackground(Color.RED);
                        player_fold_button.setForeground(Color.WHITE);
                    } else {
                        player_check_button.setText(Translator.translate("ui.ir_2") + " (+" + Helpers.money2String(call_required) + ")");
                        player_check_button.putClientProperty("i18n.key", null); // Cleared to avoid the dynamic-text glitch
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

                if (GameFrame.getInstance().getCrupier().canPlayerRaise(nickname) && GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) > 1 && ((Helpers.doubleSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0 && Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0)
                        || (Helpers.doubleSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0 && Helpers.doubleSecureCompare(call_required + min_raise, stack) < 0))) {

                    // Spinner step and range aligned to the Crupier's CURRENT sb (not the
                    // static GameFrame.CIEGA_PEQUEÑA, which would be the initial sb and
                    // goes stale after doblarCiegas or a recovery with doubled blinds).
                    // Without this the human could pick increments that are multiples of
                    // the old sb, which summed with the call produced totals fractional
                    // with respect to the new sb — the same "fractional chip bets" symptom
                    // fixed in Bot.java, but via the local player's path.
                    //
                    // Committed RAISE TOTAL = spinner_val + bet + call_required
                    //                       = spinner_val + apuesta_actual.
                    // For that total to be a multiple of the current sb when
                    // apuesta_actual comes in fractional (typical case: a prior all-in
                    // with a misaligned residual stack), spinner_min is adjusted to
                    // (aligned_min_total - apuesta_actual) and spinner_max to
                    // (aligned_max_total - apuesta_actual). With step = sb, every
                    // intermediate value spinner_min + k*sb keeps the total aligned.
                    double current_sb = GameFrame.getInstance().getCrupier().getCiega_pequeña();
                    if (current_sb <= 0) {
                        current_sb = GameFrame.CIEGA_PEQUEÑA;
                    }
                    BigDecimal sb_step = new BigDecimal(current_sb).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal apuesta_actual_bd = new BigDecimal(GameFrame.getInstance().getCrupier().getApuesta_actual()).setScale(2, RoundingMode.HALF_UP);

                    //Update the spinner and the bet button
                    BigDecimal spinner_min;
                    // aligned_max_total = floor((bet + stack) / sb) * sb, the largest
                    // committed total that's a multiple of sb and fits what the player
                    // has available. spinner_max = aligned_max_total - apuesta_actual.
                    BigDecimal bet_plus_stack = new BigDecimal(bet + stack).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal aligned_max_total = bet_plus_stack.divide(sb_step, 0, RoundingMode.FLOOR).multiply(sb_step);
                    BigDecimal spinner_max = aligned_max_total.subtract(apuesta_actual_bd);

                    setActionButtonIcon(player_bet_button, "/images/action/bet.png");

                    if (Helpers.doubleSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                        // Opening: the legal minimum is the big blind (NL rule,
                        // BetRules.minOpen). With bb=2*sb (the normal case) it coincides
                        // with a multiple of sb; a custom structure with a bb that's not a
                        // multiple of sb can leave the minimum misaligned from the step
                        // (doesn't affect the money: the all-in button covers the exact
                        // remainder).
                        spinner_min = new BigDecimal(BetRules.minOpen(GameFrame.getInstance().getCrupier().getCiega_grande())).setScale(2, RoundingMode.HALF_UP);
                        player_bet_button.setEnabled(true);
                        player_bet_button.setText(Translator.translate("action.apostar_2"));
                        player_bet_button.putClientProperty("i18n.key", "action.apostar_2");
                        player_bet_button.setBackground(Color.WHITE);
                        player_bet_button.setForeground(Color.BLACK);

                    } else {
                        // Raise: aligned_min_total = ceil((apuesta_actual + min_raise) /
                        // sb) * sb. spinner_min = aligned_min_total - apuesta_actual. It may
                        // not be a plain multiple of sb (if apuesta_actual comes in
                        // fractional), but spinner_min + k*sb added to apuesta_actual DOES
                        // produce an aligned total by construction.
                        BigDecimal min_raise_bd = new BigDecimal(min_raise).setScale(2, RoundingMode.HALF_UP);
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

                if ((GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) == 1
                        || !GameFrame.getInstance().getCrupier().canPlayerRaise(nickname))
                        && Helpers.doubleSecureCompare(call_required, stack) < 0) {
                    player_allin_button.setText(" ");
                    player_allin_button.putClientProperty("i18n.key", null);
                    player_allin_button.setEnabled(false);
                    player_allin_button.setIcon(null);
                }

                // Configurable think time: if disabled, static FULL bar (no countdown) => unlimited
                // time, nothing auto-folds the local player.
                if (GameFrame.THINK_TIME_ENABLED) {
                    Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                } else {
                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                }

                Helpers.setTranslatedText(player_action, "action.hablas_tu");

                // NOTE: the Helpers.translateComponents(botonera, false) call was removed here
                // because it was clobbering the dynamic button labels.
                Helpers.translateComponents(player_action, false);

                // Refits the font to the translated text (preserves the i18n key: just
                // re-sets the same text and, if it fits, restores the original size).
                setActionTextFitted(player_action.getText());

                setPlayerActionIcon("action/thinking.png");

                Helpers.setSpinnerColors(bet_spinner, player_bet_button.getBackground(), player_bet_button.getForeground());

                if (!GameFrame.TEST_MODE) {

                    //Maximum time to think
                    response_counter = GameFrame.THINK_TIME;

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    auto_action = new Timer(1000, new ActionListener() {
                        long t = GameFrame.getInstance().getCrupier().getTurno();

                        @Override
                        public void actionPerformed(ActionEvent ae) {

                            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isSomePlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && response_counter > 0 && auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                // Disabled => does NOT decrement (counter frozen): never reaches
                                // 0/10, so neither hurry-up nor auto-fold trigger, but the loop
                                // stays alive for the getJugadoresActivos()<2 safety check
                                // throughout the whole turn.
                                if (GameFrame.THINK_TIME_ENABLED) {
                                    response_counter--;
                                }

                                // setValue(response_counter) is redundant: smoothCountdown already
                                // has its own internal Timer updating the bar every 50ms.
                                if (GameFrame.THINK_TIME_ENABLED && response_counter == GameFrame.getHurryupThreshold()) {
                                    if (GameFrame.avisoTiempoSonidoOn()) {
                                        Audio.playWavResource("misc/hurryup.wav");
                                    }
                                    if ((hurryup_timer == null || !hurryup_timer.isRunning()) && Helpers.doubleSecureCompare(0f, call_required) < 0) {
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

                                if ((GameFrame.THINK_TIME_ENABLED && response_counter == 0) || GameFrame.getInstance().getCrupier().getJugadoresActivos() < 2) {
                                    Helpers.threadRun(() -> {
                                        if (GameFrame.THINK_TIME_ENABLED && response_counter == 0) {
                                            Audio.playWavResourceAndWait("misc/timeout.wav", true, false, !GameFrame.avisoTiempoSonidoOn()); //While the horn plays we'd still be in time to choose (the wait stays intact even if muted)
                                        }

                                        GameFrame.getInstance().checkPause();

                                        Helpers.GUIRun(() -> {
                                            if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno() && getDecision() == Player.NODEC) {

                                                if (Helpers.doubleSecureCompare(0f, call_required) == 0) {

                                                    //Auto-check
                                                    action_button_armed.put(player_check_button, true);
                                                    player_check_button.doClick();

                                                } else {

                                                    //Auto-fold
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

                        // Decide which button would auto-fire (target) and the label
                        // for the AUTO MODE dialog. Check/Fold: if checking is free we
                        // check (keeping it armed); if there's a cost we fold. Check/Call:
                        // checks for free or calls per the check pre-press rules.
                        JButton target = null;
                        String action_key = null;

                        if (pre_pulsado == Player.FOLD) {

                            if (player_check_button.isEnabled() && Helpers.doubleSecureCompare(0f, call_required) == 0) {
                                target = player_check_button;
                                action_key = "modo_auto.pasar";
                            } else if (player_fold_button.isEnabled()) {
                                target = player_fold_button;
                                action_key = "modo_auto.tirar";
                            }

                        } else if (pre_pulsado == Player.CHECK && (Helpers.doubleSecureCompare(0f, call_required) == 0 || (GameFrame.getInstance().getCrupier().getStreet() == Crupier.PREFLOP && Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), GameFrame.getInstance().getCrupier().getCiega_grande()) == 0) || (GameFrame.AUTO_CALL_ENABLED && (Helpers.doubleSecureCompare(0f, GameFrame.AUTO_CALL_MAX) == 0 || Helpers.doubleSecureCompare(Math.min(call_required, stack), GameFrame.AUTO_CALL_MAX) <= 0)))) {

                            if (player_check_button.isEnabled()) {
                                target = player_check_button;
                                action_key = (Helpers.doubleSecureCompare(0f, call_required) == 0) ? "modo_auto.pasar" : "modo_auto.igualar";
                            } else if (player_allin_button.isEnabled()) {
                                // Calling requires all-in (cost to call >= stack, check is
                                // disabled): the only way to call is to go all-in. The cap was
                                // already evaluated against what's actually committed —
                                // min(cost, stack), which here equals the stack — so
                                // stack <= AUTO_CALL_MAX and never more than the cap is risked.
                                // Same amount shown by the "call cost" overlay.
                                target = player_allin_button;
                                action_key = "modo_auto.igualar";
                            }
                        }

                        if (target == null) {

                            desPrePulsarAutoTodo();

                        } else if (GameFrame.MODO_AUTO_CONFIRM) {

                            // Non-modal 5s veto window: the rest of the board/menu stay usable,
                            // but the LocalPlayer's action button bar is DEACTIVATED while it
                            // runs (the dialog is the decision point). Its state is saved to
                            // restore it on resolution. Resolution happens via callback (EDT): it
                            // runs on expiry; on cancel (or if the turn resolves another way) it's
                            // ALWAYS disarmed (manual re-arming) and manual control is regained.
                            // doClick re-checks NODEC.
                            final JButton fire_target = target;

                            final boolean check_en = player_check_button.isEnabled();
                            final boolean fold_en = player_fold_button.isEnabled();
                            final boolean bet_en = player_bet_button.isEnabled();
                            final boolean allin_en = player_allin_button.isEnabled();
                            final boolean spinner_en = bet_spinner.isEnabled();

                            // Previous appearance (text + icon) of the button bar. During the
                            // veto the buttons are DEACTIVATED with the same "empty gray" look
                            // (no text or icon) as any other disabled board state, instead of
                            // staying dimmed while keeping their label. Restored on resolution
                            // (on cancel, the player regains manual control with the correct
                            // labels).
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
                                    LocalPlayer.this, botonera, GameFrame.AUTO_CONFIRM_SECONDS,
                                    Translator.translate(action_key),
                                    () -> getDecision() == Player.NODEC,
                                    (cancelled) -> {
                                        auto_action_dialog = null;

                                        // Restore the previous appearance (text + icon) before
                                        // re-enabling: doClick needs the button enabled, and on
                                        // cancel the player regains manual control with its labels.
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
                                            // Arming check or all-in skips CONFIRM_ACTIONS's
                                            // double-click (fold already skips it via
                                            // pre_pulsado==FOLD in its handler).
                                            if (fire_target == player_check_button || fire_target == player_allin_button) {
                                                action_button_armed.put(fire_target, true);
                                            }
                                            fire_target.doClick();
                                        } else if (cancelled) {
                                            pre_pulsado = Player.NODEC;
                                        }
                                    });
                            auto_action_dialog = dlg;
                            dlg.showOn(GameFrame.getInstance().getTapete());

                        } else {

                            // No veto dialog: fire directly. Arming check or all-in skips
                            // CONFIRM_ACTIONS's double-click.
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

            // After folding, the AUTO pre-buttons are also reactivated so they can be
            // armed out of turn (for the next hands); a folded player is skipped in the
            // betting loop, so the pre-press never fires this hand. ALLIN stays excluded.
            // Requires the "AUTO buttons" toggle to be on.
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
                    // LABEL CLEANUP: prevents the button from resurrecting a stale text
                    ((JButton) c).putClientProperty("i18n.key", null);
                }
            }

            desarmarBotonesAccion();

            // UNIFORM disabled state: neutral (Nimbus default) background on all buttons,
            // AFTER disarming, so the dimmed button bar looks identical regardless of the
            // previous color (the .form's at startup, or esTuTurno's after the first
            // hand). On re-enabling, esTuTurno/activarPreBotones repaint the right color.
            for (Component c : botonera.getComponents()) {
                if (c instanceof JButton) {
                    c.setBackground(null);
                }
            }

            // The all-in button keeps its characteristic BLACK background dimmed too (it's
            // part of its identity; unlike check/fold/bet, esTuTurno doesn't repaint it),
            // instead of turning gray like the rest.
            player_allin_button.setBackground(Color.BLACK);
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
            if (old_dec != Player.BET || Helpers.doubleSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                player_pot.setForeground(Color.WHITE);
            }

            disablePlayerAction();
        });

    }

    public void activarPreBotones() {

        // FOLD no longer blocks: a folded player can see/arm the pre-buttons out of
        // turn (for the next hands). ALLIN, spectator, exit and showdown still block.
        if (!turno && decision != Player.ALLIN && !spectator && !exit && !GameFrame.getInstance().getCrupier().isShow_time()) {

            Helpers.GUIRunAndWait(() -> {

                // AUTO state: in compact mode the button bar switches to 2 full-width
                // rows (AUTO-call on top, AUTO-fold below). Done BEFORE setting the icons
                // so botonera_compacta_auto is already true and setActionButtonIcon DOES
                // paint the thumb-up/thumb-down icons.
                updateCompactLayout(true);

                player_check_button.setBackground(null);
                player_check_button.setForeground(null);
                Helpers.setTranslatedText(player_check_button, "action.auto_call");
                player_check_button.setEnabled(true);
                setActionButtonIcon(player_check_button, "/images/action/up.png");

                player_fold_button.setBackground(null);
                player_fold_button.setForeground(null);
                Helpers.setTranslatedText(player_fold_button, "action.auto_fold");
                player_fold_button.setEnabled(true);
                setActionButtonIcon(player_fold_button, "/images/action/down.png");

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

                // On leaving the AUTO state, the compact button bar returns to the
                // default 2x2 grid (blank buttons). No effect outside level 3.
                updateCompactLayout(false);
            });
        }
    }

    public void refreshPos() {
        if (this.isActivo()) {
            this.bote = 0f;

            if (Helpers.doubleSecureCompare(0f, this.bet) < 0) {
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
            player_action.putClientProperty("i18n.key", null); // Ensures the translator key is cleared
            setActionTextFitted(" ");
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionBackground(new Color(204, 204, 204, 75));
            setPlayerActionIcon(null);
        });
    }

    public void resetGUI() {
        Helpers.GUIRunAndWait(() -> {
            // Restores the action label's font if a long hand name shrank it in the
            // previous hand (mirrors RemotePlayer.resetGUI).
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

            // New hand: syncs the bet roller to 0 (shows "----") so the first bet of the
            // hand rolls from 0, not from the previous hand's contribution.
            betRoller().set(0);

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

        // Guarantee the avatar is painted at the start of every hand (parity with
        // RemotePlayer.nuevaMano — fix for the first-hand-post-RECOVER bug).
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

        // Showdown highlight: undoes any highlight left stuck if the previous hand ended
        // with the mouse over the label, and forgets the highlightable hand.
        highlightShowdownHand(false);
        this.showdown_hand_cards = null;

        this.bote = 0f;

        this.last_bote = null;

        this.bet = 0f;

        // Safety net: clears any counter-roll deferral left over from a previous hand
        // BEFORE setting this hand's blind's deferral. Only affects the counter roll.
        setCounterRollDeferred(false);

        resetGUI();

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {

            int rebuy = (Integer) GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname);

            GameFrame.getInstance().getCrupier().getRebuy_now().remove(nickname);

            // If the rebuy was animated by the fill effect (animateRebuyStacks already
            // rolled the stack to the final value and rang the register), reComprar doesn't
            // repeat the sound. Uses the CAPTURED decision (isRebuyFillAnimated): if
            // "Counters" got turned off mid-count, it stays silent (no double cash-register).
            reComprar(rebuy, GameFrame.getInstance().getCrupier().isRebuyFillAnimated());

        }

        setStack(stack + pagar);

        pagar = 0f;

        // If about to post a blind (BB/SB) whose chip will fly to the pot, its stack/bet does
        // NOT roll in the posting (setPosition->setBet(blind), right below): it's deferred, and
        // when its chip LANDS (flyForcedBetsToPot.onLand -> rollCountersToModel) it rolls along
        // with the pot. The pending winnings (setStack(stack+pagar) above) already rolled, NOT
        // deferred. Same gate as the flight (here game_recovered==0 always: the recover block
        // runs afterward).
        if (GameFrame.getInstance().getCrupier().shouldDeferCountersToChip()
                && (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())
                || this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick()))) {
            setCounterRollDeferred(true);
        }

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

            if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack + bet) < 0) {
                setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

            } else {

                //Going ALL IN (setBet first: see note in player_allin_buttonActionPerformed)
                setBet(stack);
                setDecision(Player.ALLIN);
            }

        }
    }

    public double getEffectiveStack() {

        return Helpers.doubleClean(this.stack) + Helpers.doubleClean(this.bote) + Helpers.doubleClean(this.pagar);

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
                setActionButtonIcon(player_check_button, "/images/action/up.png");
            }
            if (player_bet_button.isEnabled()) {
                setActionButtonIcon(player_bet_button, "/images/action/bet.png");
            }

            if (player_allin_button.isEnabled() && !boton_mostrar) {
                setActionButtonIcon(player_allin_button, "/images/action/glasses.png");
            }

            if (player_fold_button.isEnabled()) {

                setActionButtonIcon(player_fold_button, "/images/action/down.png");
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

        if (Helpers.doubleSecureCompare(0f, zoom_factor) < 0) {

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
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        if (notifier != null) {

            notifier.add(Thread.currentThread().getId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }

    public boolean isBotoneraCompacta() {
        return botonera_compacta;
    }

    // Reorganizes the action button bar according to the local player's compact mode:
    //   compact == false -> vertical layout (rebuilds the same GroupLayout as the .form).
    //   compact == true  -> 2x2 grid (FOLD | CHECK // BET | ALL IN) + full-width spinner,
    //                       so the button bar stops being the LocalPlayer's height ceiling.
    // Changes the CONTAINER and the LayoutManager, adjusts font/icons (see
    // applyActionButtonsStyle) and keeps the button instances (listeners, text, colors
    // and enabled state intact).
    public void setBotoneraCompact(boolean compact) {

        Helpers.GUIRunAndWait(() -> {

            // The initial state (startup at levels 0/1/2, and freshly created panels)
            // keeps the .form's PRISTINE vertical layout untouched: it only reorganizes
            // when actually crossing to/from level 3.
            if (botonera_compacta == compact) {
                return;
            }

            botonera_compacta = compact;

            botonera.removeAll();

            if (compact) {
                // Sub-layout per the current game state (turn = 2x2, auto = 2 full-width
                // rows). Kept up to date by esTuTurno / activarPreBotones /
                // desActivarPreBotones.
                buildCompactLayout(botonera_compacta_auto);
            } else {
                buildBotoneraNormalLayout();
            }

            applyActionButtonsStyle();

            botonera.revalidate();
            botonera.repaint();
            revalidate();
            repaint();

            // Restores the icons where applicable: always in normal mode; in compact mode
            // ONLY the AUTO buttons (2-row grid). The rest stay without an icon (decided
            // by setActionButtonIcon based on botonera_compacta_auto).
            if (botonera_compacta) {
                // Compact: the AUTO icon size is derived from the base font and the zoom
                // (not getHeight), so it can be restored right away.
                buttonIconZoom();
            } else {
                // Normal: the icon is sized to 0.6*getHeight() of the button. The
                // preceding revalidate has NOT applied the vertical layout yet, and the
                // buttons still have the (taller) height of the 2x2 grid's cells, which
                // would render oversized icons. They're restored after letting the EDT
                // settle the layout, the same way the zoom does with GUI_RENDER_WAIT.
                Helpers.threadRun(() -> {
                    Helpers.pausar(GUI_RENDER_WAIT);
                    buttonIconZoom();
                });
            }
        });
    }

    // Builds (removeAll + GridBag) the compact button bar's sub-layout:
    //   autoLayout == false -> turn: 2x2 grid (FOLD | CHECK // BET | ALL IN) + spinner.
    //   autoLayout == true  -> auto: full-width AUTO-call on top and AUTO-fold below
    //                          (the other buttons/spinner aren't shown in that state).
    private void buildCompactLayout(boolean autoLayout) {

        float zoom = 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP;
        int gap = Math.max(1, Math.round(4 * zoom));

        botonera.removeAll();
        botonera.setLayout(new java.awt.GridBagLayout());

        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new java.awt.Insets(gap, gap, gap, gap);

        if (autoLayout) {

            c.gridwidth = 2;

            c.gridx = 0;
            c.gridy = 0;
            botonera.add(player_check_button, c);

            c.gridx = 0;
            c.gridy = 1;
            botonera.add(player_fold_button, c);

        } else {

            c.gridx = 0;
            c.gridy = 0;
            botonera.add(player_fold_button, c);

            c.gridx = 1;
            c.gridy = 0;
            botonera.add(player_check_button, c);

            c.gridx = 0;
            c.gridy = 1;
            botonera.add(player_bet_button, c);

            c.gridx = 1;
            c.gridy = 1;
            botonera.add(player_allin_button, c);

            c.gridx = 0;
            c.gridy = 2;
            c.gridwidth = 2;
            c.weighty = 0;
            botonera.add(bet_spinner, c);
        }

        // FIXED width = the normal layout's, in both sub-layouts, so the LocalPlayer
        // doesn't jump width when toggling turn/auto (the 2x2 with dynamic text is wider
        // than the auto one). Height is left natural (2 vs 3 rows).
        botonera.setPreferredSize(null);
        botonera.setMinimumSize(null);
        int naturalH = botonera.getPreferredSize().height;
        if (botonera_ref_width > 0) {
            botonera.setPreferredSize(new java.awt.Dimension(botonera_ref_width, naturalH));
            botonera.setMinimumSize(new java.awt.Dimension(botonera_ref_width, 0));
        }
    }

    // Switches the compact button bar's sub-layout according to the game state
    // (auto vs turn). ALWAYS saves the intent (so entering compact mode paints the
    // right sub-layout); only relayouts if already in compact mode.
    private void updateCompactLayout(boolean autoLayout) {

        Helpers.GUIRunAndWait(() -> {

            botonera_compacta_auto = autoLayout;

            if (!botonera_compacta) {
                return;
            }

            // The auto grid has 2 components; the turn one has 5. If already on the
            // requested sub-layout, don't redo anything.
            if (botonera.getComponentCount() == (autoLayout ? 2 : 5)) {
                return;
            }

            buildCompactLayout(autoLayout);

            botonera.revalidate();
            botonera.repaint();

            buttonIconZoom();
        });
    }

    // Rebuilds the .form's original vertical GroupLayout from scratch (same groups as
    // initComponents). A fresh instance is created on every transition instead of
    // reusing the saved one, which doesn't re-associate well after removeAll.
    // GroupLayout.addComponent re-adds the components to the container.
    private void buildBotoneraNormalLayout() {

        // Releases the size fixed in compact mode so the .form's GroupLayout takes over.
        botonera.setPreferredSize(null);
        botonera.setMinimumSize(null);

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

        // Refreshes the reference width with the current normal layout's (picks up zoom
        // changes), which is what gets fixed in compact mode.
        botonera_ref_width = botonera.getPreferredSize().width;
    }

    // Sets the font of the 4 action buttons per the mode (shrunk in compact mode),
    // also reprogramming their base size in ORIGINAL_FONT_SIZE so zoomFonts keeps
    // deriving the correct size after a zoom. Also strips their icon in compact mode.
    private void applyActionButtonsStyle() {

        float zoom = 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP;

        int base = botonera_compacta
                ? Math.max(8, Math.round(action_font_base * COMPACT_ACTION_FONT_FACTOR))
                : action_font_base;

        for (JButton boton : new JButton[]{player_fold_button, player_check_button, player_bet_button, player_allin_button}) {

            Helpers.ORIGINAL_FONT_SIZE.put(boton, base);

            java.awt.Font actual = boton.getFont();

            if (actual != null) {
                boton.setFont(actual.deriveFont(actual.getStyle(), Math.round(base * zoom)));
            }

            if (botonera_compacta) {
                boton.setIcon(null);
            }
        }
    }

    // Applies the icon to an action button. In normal mode it ALWAYS has an icon.
    // In compact mode only the AUTO buttons carry one (2-row grid, where the
    // thumb-up/thumb-down icon distinguishes call from fold); in the turn's 2x2 it's
    // omitted so the text fits.
    private void setActionButtonIcon(JButton boton, String resource) {

        if (botonera_compacta && !botonera_compacta_auto) {
            boton.setIcon(null);
            return;
        }

        // Icon size: in compact-auto it's based on the BASE font (action_font_base, not
        // the shrunk one) scaled by the zoom: stable during the hand (only changes with
        // the zoom), does NOT use getHeight() (the AUTO buttons are tall rows whose
        // height resettles when the table reflows, which made the icon "dance"). Normal:
        // 0.6*height.
        int size = botonera_compacta
                ? Math.round(2f * action_font_base * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP))
                : Math.round(0.6f * boton.getHeight());

        Helpers.setScaledIconButton(boton, getClass().getResource(resource), size, size);
    }

    public void setPosition(int pos) {

        switch (pos) {
            case Player.DEALER:

                if (GameFrame.getInstance().getCrupier().getDealer_nick().equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                        setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                    } else {

                        //Going ALL IN (setBet first: see note in player_allin_buttonActionPerformed)
                        setBet(stack);

                        setDecision(Player.ALLIN);
                    }
                } else {
                    setBet(0f);
                }

                break;
            case Player.BIG_BLIND:

                if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

                } else {

                    //Going ALL IN (setBet first: see note in player_allin_buttonActionPerformed)
                    setBet(stack);

                    setDecision(Player.ALLIN);
                }

                break;
            case Player.SMALL_BLIND:

                if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                } else {

                    //Going ALL IN (setBet first: see note in player_allin_buttonActionPerformed)
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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

                    if (GameFrame.interruptorSonidoOn()) {
                        Audio.playWavResource("misc/button_off.wav");
                    }

                    desPrePulsarBotonAuto(player_fold_button);

                } else {
                    if (GameFrame.interruptorSonidoOn()) {
                        Audio.playWavResource("misc/button_on.wav");
                    }

                    desPrePulsarAutoTodo();

                    prePulsarBotonAuto(player_fold_button, Player.FOLD);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_fold_button.isEnabled()) {

            if (pre_pulsado == Player.FOLD || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_fold_button) || click_recuperacion) {

                if (GameFrame.TEST_MODE || click_recuperacion || Helpers.doubleSecureCompare(0f, call_required) < 0 || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("ui.perder_mano_confirmacion"), new ImageIcon(getClass().getResource("/images/action/down.png"))) == 0) {

                    if (GameFrame.foldSonidoOn()) {
                        Audio.playWavResource("misc/fold.wav");
                    }

                    holeCard1.desenfocar();
                    holeCard2.desenfocar();

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

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

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

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

                                // Enables hover highlighting of the hand the local player just
                                // voluntarily showed (folded or a covered loser pressing SHOW): no
                                // kickers, same as a winner. The highlight gate is by !winner, so it
                                // works even if the local player isn't a showdown loser (e.g. folded
                                // earlier).
                                setShowdownHand(jugada.getWinners());

                                // Swing mutations must run on the EDT. GUIRun (async) and NOT
                                // GUIRunAndWait: we're inside lock_mostrar, and blocking the worker
                                // waiting for the EDT could deadlock. setActionBackground/Icon
                                // already self-protect; here we cover setForeground/clientProperty/
                                // setActionTextFitted, which mutated the label directly.
                                Helpers.GUIRun(() -> {
                                    player_action.setForeground(Color.WHITE);
                                    setActionBackground(new Color(51, 153, 255));

                                    // LABEL CLEANUP: avoids the "YOUR TURN" text glitch
                                    player_action.putClientProperty("i18n.key", null);
                                    setActionTextFitted(Translator.translate("ui.muestras") + jugada.getName() + Translator.translate("ui.suffix_close"));
                                });

                                if (GameFrame.SONIDOS_CHORRA && decision == Player.FOLD) {

                                    Audio.playWavResource("misc/showyourcards.wav");

                                }
                                if (!GameFrame.getInstance().getCrupier().getPerdedores().containsKey(GameFrame.getInstance().getLocalPlayer())) {
                                    GameFrame.getInstance().getRegistro().print(nickname + " " + Translator.translate("ui.muestra_2") + hole_cards_string + Translator.translate("ui.suffix_close") + " -> " + jugada);
                                }
                                Helpers.GUIRun(() -> Helpers.translateComponents(botonera, false));
                            }
                        }
                    });

                } else if (getDecision() == Player.NODEC) {

                    if (GameFrame.TEST_MODE || this.action_button_armed.get(player_allin_button) || click_recuperacion) {

                        GameFrame.getInstance().getCrupier().setCurrent_local_cinematic_b64(null);

                        // A chip is about to fly (launchChipToPot below, before the threadRun
                        // that calls setBet): stack/bet do NOT roll in setBet;
                        // rollCountersToModel rolls them on landing, together with the pot.
                        setCounterRollDeferred(GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

                        if (GameFrame.allinSonidoOn()) {
                            Audio.playWavResource("misc/allin.wav");
                        }
                        GameFrame.getInstance().getCrupier().launchChipToPot(this);

                        desactivarControles();

                        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        if (hurryup_timer != null) {
                            hurryup_timer.stop();
                        }

                        Init.PLAYING_CINEMATIC = true;

                        Helpers.threadRun(() -> {
                            // Sequenced on ONE thread (used to be two in parallel) to close the
                            // "*" race: localCinematicAllin sets current_local_cinematic_b64 and
                            // LAUNCHES the animation on its own threads (non-blocking), and only
                            // afterward does finTurno release the crupier — so the ACTION build
                            // can no longer read a null b64 and broadcast "*" when finTurno won
                            // the race. The action still fires as soon as the button is pressed
                            // (GIF selection takes milliseconds).
                            try {
                                if (!GameFrame.getInstance().getCrupier().localCinematicAllin()) {
                                    GameFrame.getInstance().getCrupier().soundAllin();
                                }
                            } catch (Exception ex) {
                                // The cinematic is cosmetic: no matter what, the turn has to
                                // close and the flag has to turn off (the bot's turn wait
                                // depends on it).
                                Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                                Init.PLAYING_CINEMATIC = false;
                                synchronized (Init.LOCK_CINEMATICS) {
                                    Init.LOCK_CINEMATICS.notifyAll();
                                }
                            }

                            // setBet BEFORE setDecision on purpose: the all-in render that
                            // setDecision queues to the EDT reads bet+stack, so this way it
                            // reads them already settled instead of racing the money movement
                            // mid-setBet.
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

                    if (GameFrame.interruptorSonidoOn()) {
                        Audio.playWavResource("misc/button_off.wav");
                    }

                    desPrePulsarBotonAuto(player_check_button);

                } else {

                    if (GameFrame.interruptorSonidoOn()) {
                        Audio.playWavResource("misc/button_on.wav");
                    }

                    desPrePulsarAutoTodo();

                    prePulsarBotonAuto(player_check_button, Player.CHECK);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_check_button.isEnabled()) {

            if (pre_pulsado == Player.CHECK || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_check_button) || click_recuperacion) {

                if (Helpers.doubleSecureCompare(this.stack - (GameFrame.getInstance().getCrupier().getApuesta_actual() - this.bet), 0f) == 0) {
                    player_allin_buttonActionPerformed(null);
                } else {

                    // If a chip is about to fly (CALL with money), stack/bet do NOT roll in
                    // setBet (runs in the threadRun below): launchChipToPot rolls them on
                    // landing, together with the pot.
                    setCounterRollDeferred(Helpers.doubleSecureCompare(0f, call_required) < 0
                            && GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

                    if (Helpers.doubleSecureCompare(0f, call_required) < 0) {
                        if (GameFrame.igualarSonidoOn()) {
                            Audio.playWavResource("misc/call.wav");
                        }
                        GameFrame.getInstance().getCrupier().launchChipToPot(this);
                    } else {
                        if (GameFrame.pasarSonidoOn()) {
                            Audio.playWavResource("misc/check.wav");
                        }
                    }

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

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

            if (Helpers.doubleSecureCompare(stack, (((BigDecimal) bet_spinner.getValue()).doubleValue()) + call_required) == 0) {

                player_allin_buttonActionPerformed(null);

            } else {

                if (!GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_bet_button) || click_recuperacion) {

                    double bet_spinner_val = Helpers.doubleClean(((BigDecimal) bet_spinner.getValue()).doubleValue());

                    // A chip is about to fly: stack/bet do NOT roll in setBet (threadRun
                    // below); launchChipToPot rolls them on landing, together with the pot.
                    setCounterRollDeferred(GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());
                    if (GameFrame.apuestaSonidoOn()) {
                        Audio.playWavResource("misc/bet.wav");
                    }
                    GameFrame.getInstance().getCrupier().launchChipToPot(this);

                    desactivarControles();

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

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

                        if (GameFrame.SONIDOS_CHORRA && !GameFrame.getInstance().getCrupier().isSincronizando_mano() && GameFrame.getInstance().getCrupier().getConta_raise() > 0 && Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.doubleSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {

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

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        // With the big chip forced hidden (hc1 reveal / swap), ignore the click that would
        // cycle it: it would restore it mid-animation.
        if (chip_forced_hidden) {
            return;
        }

        if (nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())
                || nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())
                || nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())
                || (GameFrame.getInstance().getCrupier().isStraddle_posted()
                && nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick()))) {

            // Cycles the position chip's 3 states: normal -> 70% -> hidden -> normal.
            GameFrame.LOCAL_POSITION_CHIP = (GameFrame.LOCAL_POSITION_CHIP + 1) % 3;

            this.refreshPositionChipIcons();

            Helpers.PROPERTIES.setProperty("local_pos_chip", String.valueOf(GameFrame.LOCAL_POSITION_CHIP));

            Helpers.savePropertiesFile();

            if (GameFrame.interruptorSonidoOn()) {
                Audio.playWavResource(GameFrame.LOCAL_POSITION_CHIP == GameFrame.LOCAL_POS_CHIP_HIDDEN ? "misc/button_off.wav" : "misc/button_on.wav");
            }
        }
    }//GEN-LAST:event_player_nameMouseClicked

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:

        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }

        if (SwingUtilities.isLeftMouseButton(evt)) {
            if (!player_stack_click) {
                player_stack_click = true;

                // Shows the fixed buy-in (not the stack value): invalidates the roller so
                // restoring jumps to the real stack without animating from here.
                stackRoller().invalidate();
                player_stack.setText(Helpers.money2String(this.buyin));
                setPlayerStackBackground(Color.GRAY);
                player_stack.setForeground(Color.WHITE);

                Helpers.threadRun(() -> {
                    Helpers.pausar(1500);
                    double s = getStack();
                    Helpers.GUIRun(() -> {
                        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                            setPlayerStackBackground(Color.YELLOW);
                            player_stack.setForeground(Color.BLACK);
                            player_stack.setText(Helpers.money2String(stack) + " + " + Helpers.money2String((int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname())));
                            stackRoller().invalidate();

                        } else {

                            if (GameFrame.hasRebought(nickname)) {
                                setPlayerStackBackground(Color.CYAN);

                                player_stack.setForeground(Color.BLACK);
                            } else {

                                setPlayerStackBackground(new Color(51, 153, 0));

                                player_stack.setForeground(Color.WHITE);
                            }

                            stackRoller().set(s);
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
        // Plain left click, same as the remote seats: the right button is kept for
        // controls whose left click already does something else.
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // Identity: clicking own avatar opens the identicon of THIS installation's
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

        // In run-it-twice the strip is PER SIDE: each side awards HALF the pot, so it
        // shows the money won ON IT (pagar - pagar_face_base) and the profit against
        // half the pot. Outside RIT (tag null) -> full pagar and pot, as always.
        final boolean is_rit = GameFrame.getInstance().getCrupier().getRitPotBoardTag() != null;

        final double fullbote = last_bote != null ? last_bote : bote;

        final double mibote = is_rit ? Crupier.splitPotForRunItTwice(fullbote)[0] : fullbote;

        final double dinero = is_rit ? Helpers.doubleClean(pagar - pagar_face_base) : pagar;

        if (Helpers.doubleSecureCompare(0f, dinero) < 0 && GameFrame.getInstance().getCrupier().getBote().getSide_pot_count() > 0) {

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

                sec_pot_win_label.setText(String.join("+", botes) + " = " + Helpers.money2String(dinero) + " (" + Helpers.money2String(dinero - mibote) + ")");

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
    public void setShowdownHand(java.util.List<Card> cartas) {
        this.showdown_hand_cards = cartas;
    }

    // Enter/exit over the hand label (installed in the constructor): entering highlights
    // this player's hand — winner or loser — (focuses their cards, dims the rest of the
    // table) and paints their label yellow/black; exiting restores it.
    private void installShowdownHandHighlight() {
        player_action.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                highlightShowdownHand(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                highlightShowdownHand(false);
            }
        });
    }

    // on=true: only if the option is enabled, this player is NOT a spectator, has a
    // visible hand (showdown_hand_cards) and we're still in show_time. Focuses ONLY the
    // cards of their hand and unfocuses every other table card (saving each one's focus
    // first), and paints the label yellow/black. Works for ANY player with a shown hand:
    // winner(s), losers, and a folded player showing voluntarily (the gate no longer
    // excludes winners). A spectator doesn't get dealt cards this hand, so whatever they
    // had saved can only be leftover from the last one they played. A player who left
    // DOES pass the gate: they can leave with the hand still live (all-in run-out) and
    // their hand gets resolved in this same showdown.
    // on=false: unconditional (defensive) restoration.
    private void highlightShowdownHand(boolean on) {
        if (on) {
            final java.util.List<Card> cartas = showdown_hand_cards;
            Crupier crupier = GameFrame.getInstance() != null ? GameFrame.getInstance().getCrupier() : null;

            if (!GameFrame.RESALTAR_JUGADA_SHOWDOWN || isSpectator() || cartas == null || crupier == null || !crupier.isShow_time()) {
                return;
            }

            Helpers.GUIRun(() -> {
                // Idempotence: if a highlight was left stuck, undo it before re-snapshotting.
                restoreShowdownHandHighlight();

                java.util.List<Card> mesa = GameFrame.getInstance().getShowdownVisibleCards();
                java.util.Map<Card, Boolean> snapshot = new java.util.HashMap<>();

                for (Card c : mesa) {
                    snapshot.put(c, c.isDesenfocada());
                }

                showdown_focus_snapshot = snapshot;

                for (Card c : mesa) {
                    if (cartas.contains(c)) {
                        c.enfocar();
                        c.marcarTinteShowdown();
                    } else {
                        c.desenfocar();
                    }
                }

                showdown_action_bg_snapshot = player_action_panel.getBackground();
                showdown_action_fg_snapshot = player_action.getForeground();
                setActionBackground(Color.YELLOW);
                player_action.setForeground(Color.BLACK);
            });
        } else {
            Helpers.GUIRun(this::restoreShowdownHandHighlight);
        }
    }

    // Returns the table cards to the focus they had before the hover (the winner's
    // highlight comes back as-is) and removes the tint. Does NOT touch the label's color.
    // Idempotent (no-op if there's no snapshot). Must be called on the EDT.
    private void restoreShowdownHandFocus() {
        java.util.Map<Card, Boolean> snapshot = showdown_focus_snapshot;

        if (snapshot != null) {
            for (java.util.Map.Entry<Card, Boolean> e : snapshot.entrySet()) {
                if (e.getValue()) {
                    e.getKey().desenfocar();
                } else {
                    e.getKey().enfocar();
                }

                e.getKey().desmarcarTinteShowdown();
            }

            showdown_focus_snapshot = null;
        }
    }

    // Full restoration (focus + label color) for mouseExited and the between-hands
    // reset: the label goes back to the loser's red exactly as it was.
    private void restoreShowdownHandHighlight() {
        restoreShowdownHandFocus();

        if (showdown_action_bg_snapshot != null) {
            setActionBackground(showdown_action_bg_snapshot);
            player_action.setForeground(showdown_action_fg_snapshot);
            showdown_action_bg_snapshot = null;
            showdown_action_fg_snapshot = null;
        }
    }

    // Discards the hover WITHOUT restoring the label color: for the run-it-twice rewind,
    // where renderDecisionVisual repaints the label to the decision (ALL IN) right after;
    // restoring SIDE-A's loser red here would leave it stuck over SIDE-B.
    private void discardShowdownHandHighlight() {
        restoreShowdownHandFocus();
        showdown_action_bg_snapshot = null;
        showdown_action_fg_snapshot = null;
    }

    @Override
    public void pagar(double pasta, Integer sec_pot) {

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

    @Override
    public void markFoldedOnRecover() {
        // setDecision already sets decision=FOLD and paints it gray (renderDecisionVisual),
        // with no sound or cinematic (those live in the button handler, not here). Exactly
        // what the skip needs.
        setDecision(Player.FOLD);
    }

    // Visual render of a decision (no side effects), extracted from setDecision so the
    // last action can be RE-PAINTED during the run-it-twice rewind.
    private void renderDecisionVisual(int dec) {
        switch (dec) {
            case Player.CHECK:

                Helpers.GUIRun(() -> {
                    if (Helpers.doubleSecureCompare(0f, call_required) < 0) {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][1]);
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);
                    }

                    setPlayerActionIcon("action/up.png");
                });

                break;
            case Player.BET:
                Helpers.GUIRun(() -> {
                    final double apuesta_actual_snapshot = GameFrame.getInstance().getCrupier().getApuesta_actual();
                    final int conta_raise_snapshot = GameFrame.getInstance().getCrupier().getConta_raise();
                    // SINGLE read of the volatile bet: the guard and the text must use
                    // exactly the same value (see the note in ALLIN).
                    final double bet_snapshot = bet;
                    if (Helpers.doubleSecureCompare(apuesta_actual_snapshot, bet_snapshot) < 0 && Helpers.doubleSecureCompare(0f, apuesta_actual_snapshot) < 0) {
                        setActionTextFitted((conta_raise_snapshot > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.money2String(bet_snapshot - apuesta_actual_snapshot) + ")");

                        if (conta_raise_snapshot > 0) {
                            reraise = true;
                        }
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.money2String(bet_snapshot));
                    }
                    setPlayerActionIcon("action/bet.png");
                });
                break;
            case Player.ALLIN:
                Helpers.GUIRun(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0]);

                    final double apuesta_actual_snapshot = GameFrame.getInstance().getCrupier().getApuesta_actual();
                    // SINGLE read of bet+stack for the guard and the text: they're volatile
                    // and the all-in money moves in two steps (bet goes up, then stack goes
                    // down) on another thread. With separate reads, the guard could see the
                    // inflated sum mid-setBet while the text saw the already-settled one,
                    // sneaking a negative amount into the label ("ALL IN (+-0.90)").
                    final double total_allin = bet + stack;
                    if (Helpers.doubleSecureCompare(apuesta_actual_snapshot, total_allin) < 0) {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0] + " (+" + Helpers.money2String(total_allin - apuesta_actual_snapshot) + ")");
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

                if (dec == Player.CHECK && Helpers.doubleSecureCompare(0f, call_required) == 0) {
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

    // Run-it-twice rewind: re-applies the render of the last saved action and clears
    // SIDE-A's winner/loser green/red, leaving the hole cards revealed. Doesn't touch
    // pots or stacks (the pot persists across sides).
    @Override
    public void repaintLastAction() {
        this.winner = false;
        this.loser = false;
        // Run-it-twice: forgets SIDE-A's hover highlight before the rewind (idempotent if
        // no hover was active). It's DISCARDED without restoring the color:
        // renderDecisionVisual (below) repaints the label to the decision (ALL IN);
        // restoring SIDE-A's loser red here would leave it stuck over SIDE-B. Re-focusing
        // the hole cards and SIDE-B's settle rebuild the rest.
        Helpers.GUIRun(this::discardShowdownHandHighlight);
        this.showdown_hand_cards = null;
        // Clears SIDE-A's side-pot strip (recalculated on SIDE-B).
        this.botes_secundarios.clear();
        // SIDE-B's baseline = what accumulated in SIDE-A: SIDE-B's strip shows
        // 'pagar - base', i.e. ONLY what's won on SIDE-B (pagar keeps accumulating both
        // sides for accounting).
        this.pagar_face_base = this.pagar;
        // Re-focuses the hole cards: SIDE-A's showdown dims the losers'; on SIDE-B they
        // must look bright again (re-evaluated).
        Helpers.GUIRun(() -> {
            holeCard1.enfocar();
            holeCard2.enfocar();
            sec_pot_win_label.setVisible(false);
            // Neutral border: in the normal flow finTurno restores it (which the rewind
            // doesn't call) and renderDecisionVisual only repaints the border on
            // ALLIN/FOLD; without this, SIDE-A's winner/loser green/red would survive
            // into CHECK/BET (e.g. whoever covers the all-in).
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }
        });
        renderDecisionVisual(this.decision);
    }

    // The table log speaks in 3rd person like it does for every other player ("server
    // CALLS", "server RAISES (+0.30)"...), even though the local player's action label on
    // the felt stays in 2nd person (that one is NOT touched). We rewrite ONLY the verb of
    // the already-rendered label to its 3rd-person form — the same keys RemotePlayer
    // uses — keeping the amounts ("(+0.30)", " 0.50", "(+...)") and the "RE" prefix. The
    // replacement is per-decision, so the verbs don't collide with each other or with the
    // (numeric) amounts. In English both persons coincide, so this ends up a no-op.
    private String thirdPersonActionLabel() {
        String text = player_action.getText();

        switch (this.getDecision()) {
            case Player.FOLD:
                return text.replace(Translator.translate("action.label.fold"), Translator.translate("action.label.fold2"));
            case Player.CHECK:
                return text.replace(Translator.translate("action.label.check"), Translator.translate("action.label.check2"))
                        .replace(Translator.translate("action.label.call"), Translator.translate("action.label.call2"));
            case Player.BET:
                return text.replace(Translator.translate("action.label.raise"), Translator.translate("action.label.raise2"))
                        .replace(Translator.translate("action.label.bet"), Translator.translate("action.label.bet2"));
            default:
                return text; // ALL IN: identico en ambas personas.
        }
    }

    @Override
    public String getLastActionString() {

        String action = nickname + " ";

        switch (this.getDecision()) {
            case Player.FOLD:
                action += thirdPersonActionLabel() + " (" + Helpers.money2String(this.bote) + ")";
                break;
            case Player.CHECK:
                action += thirdPersonActionLabel() + " (" + Helpers.money2String(this.bote) + ")";
                break;
            case Player.BET:
                action += thirdPersonActionLabel() + " (" + Helpers.money2String(this.bote) + ")";
                break;
            case Player.ALLIN:
                action += thirdPersonActionLabel() + " (" + Helpers.money2String(this.bote) + ")";
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
            player_action.putClientProperty("i18n.key", null); // Clears stale i18n key
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
        // Any NORMAL action text (CALL/RAISE/thinking/leaves/reset...) invalidates the
        // all-in %'s rolling animation: the next % will jump instead of rolling from a
        // value that no longer applies (e.g. a previous all-in's). The roll itself and the
        // "(--%)" use setActionTextFittedRaw so they do NOT self-invalidate.
        if (jugada_prob_roller != null) {
            jugada_prob_roller.invalidate();
        }
        setActionTextFittedRaw(msg);
    }

    private void setActionTextFittedRaw(String msg) {

        java.awt.Font base_font = (orig_action_font != null) ? orig_action_font : player_action.getFont();

        java.awt.Insets insets = player_action.getInsets();

        int available_width = (player_action.getWidth() > 0 ? player_action.getWidth() : player_action.getPreferredSize().width) - (insets != null ? insets.left + insets.right : 0);

        // JLabel lays the icon and the text out side by side. The old calculation
        // measured the text against the whole label, so adding the winner/loser
        // icon after fitting could still clip the end of a long hand name.
        javax.swing.Icon icon = player_action.getIcon();
        if (icon != null && msg != null && !msg.isEmpty()) {
            available_width -= icon.getIconWidth() + player_action.getIconTextGap();
        }

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

    // Hand shown on a NEUTRAL label (resting gray, not showCards's blue) during the
    // showdown's sequential reveal — mirrors RemotePlayer.showJugadaNeutral, but for the
    // local player's own hand (already face-up). Shrinks the font for long hand names,
    // same as the remote seats.
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

        return (spectator && Helpers.doubleSecureCompare(0f, stack) < 0);
    }

    @Override
    public boolean isActivo() {
        return (!exit && !spectator);
    }

    @Override
    public void setPagar(double pagar) {
        this.pagar = pagar;
    }

    @Override
    public void destaparCartas(boolean sound) {

        if (getHoleCard1().isIniciada() && getHoleCard1().isTapada()) {

            if (sound && GameFrame.destapeSonidoOn()) {
                Helpers.threadRun(() -> Audio.playPreloadedWav("misc/uncover.wav"));
            }

            getHoleCard1().destapar(false);

            getHoleCard2().destapar(false);
        }
    }

    @Override
    public void ordenarCartas() {
        if (getHoleCard1().getValorNumerico() != -1 && getHoleCard2().getValorNumerico() != -1 && getHoleCard1().getValorNumerico() < getHoleCard2().getValorNumerico()) {

            //Sort the cards for convenience
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
        if (isActivo() && Helpers.doubleSecureCompare(0f, getEffectiveStack()) == 0) {

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

                // setWinner/setLoser fit the text before installing their icon. Refit
                // after the icon changes so its width is included in the measurement.
                setActionTextFittedRaw(player_action.getText());

                repaint();
            });
        }
    }

    public void hidePlayerActionIcon() {

        Helpers.GUIRun(() -> {
            player_action.setIcon(null);
            setActionTextFittedRaw(player_action.getText());
        });

    }

    // Live rolling animation of the all-in win-probability % on the action label (HAND +
    // PROB). The number rolls at constant speed keeping the hand name as a prefix; the
    // renderer rebuilds "HAND (NN%)" via setActionTextFittedRaw (so it doesn't
    // self-invalidate) and runs it through the font auto-fit. EDT-only (lazily created).
    private RollingCounter jugada_prob_roller;
    private String jugada_prob_prefix = "";

    private RollingCounter jugadaProbRoller() {
        if (jugada_prob_roller == null) {
            jugada_prob_roller = new RollingCounter(
                    (v) -> setActionTextFittedRaw(jugada_prob_prefix + " (" + Helpers.floatClean((float) v) + "%)"),
                    GameFrame.PROB_ROLL_MS);
        }
        return jugada_prob_roller;
    }

    @Override
    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per) {
        Helpers.GUIRun(() -> {
            setActionBackground(ganador ? new Color(120, 200, 0) : new Color(230, 70, 0));
            player_action.setForeground(ganador ? Color.BLACK : Color.WHITE);
            setPlayerActionIcon(null);

            jugada_prob_prefix = jugada.getName();

            if (win_per >= 0) {
                // Rolls only the %, keeping the hand name. Gated by the Appearance "Counters"
                // option (isCounterRollEnabled; skipped on recover). Via the roller -> render
                // with setActionTextFittedRaw (doesn't self-invalidate).
                boolean animate = GameFrame.isCounterRollEnabled();
                RollingCounter roller = jugadaProbRoller();
                // All-in's first reveal: the roller has no value (the previous action
                // invalidated it), so roll() would jump straight to it on ONLY the first
                // street and animate the rest. Seeded with 0 so it rolls 0->% over the same
                // fixed duration, so EVERY street takes the same time.
                if (animate && !roller.isValid()) {
                    roller.set(0);
                }
                roller.roll(win_per, animate);
            } else {
                // Still no simulation: raw "(--%)" (without invalidating) so the roller's
                // value survives and the next street's % rolls from the current one.
                setActionTextFittedRaw(jugada_prob_prefix + " (--%)");
            }
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
