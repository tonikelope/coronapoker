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
import static com.tonikelope.coronapoker.GifLabel.GIF_BARRIER_TIMEOUT;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

/**
 *
 * @author tonikelope
 */
public class RemotePlayer extends JPanel implements ZoomableInterface, Player {

    public static String[][] getActionsLabels() {
        return new String[][]{
            new String[]{Translator.translate("action.label.fold2")},
            new String[]{Translator.translate("action.label.check2"), Translator.translate("action.label.call2")},
            new String[]{Translator.translate("action.label.bet2"), Translator.translate("action.label.raise2")},
            new String[]{Translator.translate("action.label.allin")}
        };
    }

    public static volatile String[][] ACTIONS_LABELS = getActionsLabels();
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.YELLOW, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 200;
    public static final int MIN_ACTION_HEIGHT = 45;

    private volatile String nickname;
    private volatile double stack = 0;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile double bet = 0;
    private volatile int decision = Player.NODEC;
    private volatile boolean utg = false;
    private volatile boolean spectator = false;
    private volatile double pagar = 0;
    // Baseline of 'pagar' at the start of the current run-it-twice FACE (0 on
    // FACE-A, FACE-A's total when entering FACE-B). Money won on this face is
    // 'pagar - pagar_face_base', derived from the single source of truth
    // (pagar), so it can't drift out of sync. Unused outside RIT.
    private volatile double pagar_face_base = 0;
    private volatile double bote = 0;
    private volatile Double last_bote = null;
    private volatile boolean exit = false;
    private volatile Timer auto_action = null;
    private volatile boolean timeout = false;
    private volatile boolean winner = false;
    private volatile boolean loser = false;
    // Showdown hover highlight (RESALTAR_JUGADA_SHOWDOWN): this player's hand cards (no kickers)
    // to highlight on mouseover of their label; null if they didn't show. The three snapshot_
    // fields hold what to restore on mouse-exit: each table card's pre-hover focus state (the
    // winner's highlight is left as-is) and the label's background/foreground colors. Touched
    // only on the EDT (inside Helpers.GUIRun).
    private volatile java.util.List<Card> showdown_hand_cards = null;
    private java.util.Map<Card, Boolean> showdown_focus_snapshot = null;
    private Color showdown_action_bg_snapshot = null;
    private Color showdown_action_fg_snapshot = null;
    private volatile double call_required;
    private volatile boolean turno = false;
    private volatile Bot bot = null;
    private volatile int response_counter;
    private volatile boolean spectator_bb = false;
    private volatile Color border_color = null;
    private volatile boolean player_stack_click = false;
    private volatile String player_action_icon = null;
    private volatile Timer icon_zoom_timer = null;
    private volatile Timer iwtsth_blink_timer = null;
    private volatile Timer rebuy_countdown_timer = null;
    // Bet/call cinematic: the action thread waits ONLY for the chip to launch (GIF frame 32),
    // not for the whole GIF to finish. addAudio counts it down when it throws the chip;
    // awaitChipLaunch waits on it with a timeout.
    private volatile CountDownLatch chip_launch_latch = null;
    private volatile String rebuy_countdown_saved_text = null;
    // Shuffle-cascade GIF overlay (small, MUTE, looping) + white highlight border on this player
    // while it processes its SRA cascade step. Synced across ALL peers: the host broadcasts
    // SHUFFLE_TURN and GameFrame's controller (onShuffleTurn) shows/hides it on the player whose
    // turn it is. No audio, no barrier: purely visual. The controller serializes turns (one
    // overlay at a time, minimum duration), so no 'generation' counter is needed here. The
    // ImageIcon is decoded once per instance (cache-busted) and reused (setIcon rewinds it);
    // reloaded if the deck changes.
    private final GifLabel shuffle_cascade_gif_label = new GifLabel();
    private volatile ImageIcon shuffle_cascade_icon = null;
    private volatile int shuffle_cascade_frames = 0;
    private volatile String shuffle_cascade_icon_url = null;
    // Border color saved before turning it white (cascade turn), to restore afterwards.
    private volatile Color shuffle_border_saved = null;
    private volatile boolean shuffle_border_active = false;
    // Game-over GIF over the busted player's cards while they decide on a rebuy (only with the
    // GAME OVER cinematic on). Dedicated label (layer 1001, below chat_notify_label): a chat meme
    // paints over it and, once hidden, the game-over GIF is still underneath, without fighting
    // for ownership of the notify label.
    private final GifLabel rebuy_gif_label = new GifLabel();
    // Rebuy-visual generation: invalidates the swap to the zero GIF if the rebuy resolved while
    // the countdown GIF was still running. Written only on the EDT.
    private volatile int rebuy_generation = 0;
    // Rebuy-visual active flag (EDT-confined): makes setRebuying(true)/setRebuying(false)
    // idempotent in both modes.
    private boolean rebuying_visual = false;
    // Count of busted players currently showing the game-over GIF (EDT-confined, shared across
    // all RemotePlayer instances): with several simultaneous, only ONE game_over.wav plays
    // (the first one hooks it) and it stops when the last one resolves.
    private static int REBUY_GIF_ACTIVOS = 0;
    private volatile boolean notify_blocked = false;
    private volatile URL chat_notify_image_url = null;
    private volatile Long chat_notify_thread = null;
    private final Object zoom_lock = new Object();
    private final GifLabel chat_notify_label = new GifLabel();
    private final JLabel chip_label = new JLabel();
    private final JLabel sec_pot_win_label = new JLabel();
    private final ConcurrentLinkedQueue<Integer> botes_secundarios = new ConcurrentLinkedQueue<>();
    private volatile boolean raise;
    private volatile boolean reraise;
    private volatile boolean muestra = false;
    private volatile int conta_win = 0;

    private volatile Font orig_action_font = null;
    private volatile float border_size = Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
    private volatile float arc = Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
    // Cached BasicStroke for paintBorder; rebuilt only when border_size changes (zoom).
    private float cached_stroke_size = -1f;
    private BasicStroke cached_stroke = null;

    @Override
    public void stopActionTimer() {
        Helpers.GUIRun(() -> {
            if (auto_action != null && auto_action.isRunning()) {
                auto_action.stop();
            }
            // Do NOT stop icon_zoom_timer here — between hands / at recover start this left the
            // next hand without setAvatar, leaving the avatar invisible. Reverted change b173ccf9.
        });
    }

    public void updateLatency(String latency, boolean error) {
        Helpers.GUIRun(() -> {
            latency_label.setBackground(error ? Color.RED : Color.BLUE);
            latency_label.setText(latency);
        });
    }

    public JLabel getLatency_label() {
        return latency_label;
    }

    // Telemetry: the LatencyDot widget is placed in the .form (NetBeans visual editor) and
    // wired by calling setLatencyDot in the constructor after initComponents(). If null,
    // applyTelemetry is a silent no-op (telemetry never affects game flow).
    private volatile LatencyDot latency_dot = null;

    public LatencyDot getLatencyDot() {
        return latency_dot;
    }

    public void setLatencyDot(LatencyDot dot) {
        this.latency_dot = dot;
    }

    /**
     * Updates the latency dot with the latest snapshot from the host's TELEMETRY broadcast.
     * Uses the min of lat1/lat2 if both are valid, whichever is valid if only one is, or -1
     * (red dot) if both are -1. No-op if latency_dot hasn't been wired via setLatencyDot yet.
     *
     * @param lat1 latency sample 1 in ms, or -1 if unavailable
     * @param lat2 latency sample 2 in ms, or -1 if unavailable
     * @param reconnectionCount reconnection count to display alongside the latency
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

    // The seat has ROUNDED corners: if it were really opaque, Swing wouldn't repaint the
    // background (felt) behind it and the corners outside the arc would show garbage. So the
    // seat is NEVER truly opaque: setOpaque is intercepted and only records the fill INTENT,
    // which paintComponent then paints itself (rounded); Swing still repaints the felt behind it
    // and the corners stay clean. Only affects the busted/red highlight state, which is static,
    // so there's no performance cost.
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
            // super.paintComponent is NOT called: Swing has already repainted the felt behind
            // (the seat is non-opaque) and the rounded fill goes on top; calling super would
            // paint a rectangular background underneath it.
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

    public void refreshNotifyChatLabel() {

        Helpers.GUIRun(() -> {
            if (getChat_notify_label().isVisible()) {
                Helpers.threadRun(() -> {
                    if (chat_notify_image_url == null) {
                        setNotifyTTSChatLabel();
                    }
                });
            }
        });

    }

    // Repositions/resizes the rebuy game-over GIF if visible. Called by vistaCompacta and by
    // icon_zoom_timer after a resize (compact view, zoom), same as refreshNotifyChatLabel does
    // for chat GIFs: this GIF lasts the whole rebuy decision and without this it would keep the
    // geometry from the moment it was shown. GifLabel stretches the Image to the bounds at paint
    // time (GPU), so it's enough to recompute bounds with the SAME calculation as the show —
    // no icon reload, no touching the animation in progress.
    public void refreshRebuyGifLabel() {

        Helpers.GUIRun(() -> {
            if (!rebuy_gif_label.isVisible() || !(rebuy_gif_label.getIcon() instanceof ImageIcon)) {
                return;
            }

            ImageIcon icon = (ImageIcon) rebuy_gif_label.getIcon();

            if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                return;
            }

            int max_width = panel_cartas.getWidth();
            int new_height = panel_cartas.getHeight();
            int new_width = (int) Math.round((icon.getIconWidth() * new_height) / icon.getIconHeight());

            if (new_width > max_width) {
                new_height = (int) Math.round((new_height * max_width) / new_width);
                new_width = max_width;
            }

            rebuy_gif_label.setSize(new_width, new_height);
            rebuy_gif_label.setPreferredSize(rebuy_gif_label.getSize());
            rebuy_gif_label.setLocation(Math.round((panel_cartas.getWidth() - new_width) / 2), Math.round((getHoleCard1().getHeight() - new_height) / 2));
            rebuy_gif_label.repaint();
        });

    }

    @Override
    public boolean isMuestra() {
        return muestra;
    }

    @Override
    public void setNotifyTTSChatLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        int sound_icon_size_h = getHoleCard1().getHeight();

        int sound_icon_size_w = Math.round((596 * sound_icon_size_h) / 460);

        ImageIcon image = new ImageIcon(new ImageIcon(getClass().getResource("/images/talk.png")).getImage().getScaledInstance(sound_icon_size_w, sound_icon_size_h, Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {

            int pos_x = Math.round((panel_cartas.getWidth() - sound_icon_size_w) / 2);

            int pos_y = 0;

            getChat_notify_label().setIcon(image);

            getChat_notify_label().setSize(sound_icon_size_w, sound_icon_size_h);

            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

            getChat_notify_label().setOpaque(false);

            getChat_notify_label().setLocation(pos_x, pos_y);

        });
    }

    public void setNotifyRabbitLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        int icon_size_h = getHoleCard1().getHeight();

        int icon_size_w = Math.round((484 * icon_size_h) / 556);

        ImageIcon image = new ImageIcon(new ImageIcon(getClass().getResource("/images/bugs_notify.png")).getImage().getScaledInstance(icon_size_w, icon_size_h, Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {

            int pos_x = Math.round((panel_cartas.getWidth() - icon_size_w) / 2);

            int pos_y = 0;

            getChat_notify_label().setIcon(image);

            getChat_notify_label().setSize(icon_size_w, icon_size_h);

            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

            getChat_notify_label().setOpaque(false);

            getChat_notify_label().setLocation(pos_x, pos_y);

        });
    }

    private boolean isActionGif(URL u) {

        String[] gif_actions = new String[]{"check", "fold1", "fold2", "fold3", "bet1", "bet2", "bet3", "bet4", "call1", "call2", "call3", "call4"};

        for (String gif : gif_actions) {
            if (getClass().getResource("/images/gif_actions/" + gif + ".gif").equals(u)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void setNotifyImageChatLabel(URL u) {
        setNotifyImageChatLabel(u, true);
    }

    // caller_awaits: if true (fold and pure check), the action thread waits on the barrier for
    // the whole GIF to finish (3 parties). If false (bet and call with money), it does NOT: the
    // chip still flies on its frame (addAudio) but the action only waits for it to LAUNCH
    // (chip_launch_latch); the GIF tears itself down (2 parties: setup + GIF-end) and its
    // remaining frames play out separately.
    private void setNotifyImageChatLabel(URL u, boolean caller_awaits) {

        // Any notify (this one, or one that SUPERSEDES a bet/call GIF in flight) releases the
        // pending latch: if the previous cinematic tears down before its frame 32, its action
        // thread doesn't keep waiting (it used to, up to 5s). This call's own latch is armed
        // LATER (below), so this only affects a previous action.
        signalChipLaunched();

        if (!this.isNotify_blocked()) {

            try {

                chat_notify_image_url = u;

                final boolean action_gif = isActionGif(u);

                // bet/call (caller_awaits=false): arms the latch its thread will wait on until
                // the chip launches on frame 32 (counted down by addAudio). Armed after releasing
                // the previous one, to avoid a leak window if it gets superseded before frame 32.
                if (!caller_awaits && action_gif) {
                    chip_launch_latch = new CountDownLatch(1);
                }

                final boolean isgif = (action_gif || ChatImageDialog.GIF_CACHE.containsKey(u.toString()) || Helpers.isImageGIF(u));

                final CyclicBarrier gif_barrier = new CyclicBarrier((action_gif && caller_awaits) ? 3 : 2);

                getChat_notify_label().setBarrier(gif_barrier);

                Helpers.threadRun(() -> {

                    synchronized (getChat_notify_label()) {

                        chat_notify_thread = Thread.currentThread().threadId(); // Claim ownership of the notify icon and wake up any thread that was manipulating it

                        getChat_notify_label().notifyAll();

                        try {

                            final ImageIcon orig = action_gif ? new ImageIcon(u) : ImageCacheManager.getIcon(new URL(u.toString() + "#" + String.valueOf(System.currentTimeMillis())));

                            while (orig.getIconHeight() == 0 || orig.getIconWidth() == 0) {

                                Helpers.pausar(GUI_RENDER_WAIT);
                            }

                            int max_width = Math.max(panel_cartas.getWidth(), orig.getIconWidth());

                            int max_height = Math.max(panel_cartas.getHeight(), panel_cartas.getHeight());

                            int new_height = max_height;

                            int new_width = (int) Math.round((orig.getIconWidth() * max_height) / orig.getIconHeight());

                            if (new_width > max_width) {

                                new_height = (int) Math.round((new_height * max_width) / new_width);

                                new_width = max_width;
                            }

                            ImageIcon image = new ImageIcon(orig.getImage().getScaledInstance(new_width, new_height, isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));

                            int pos_x = Math.round((panel_cartas.getWidth() - image.getIconWidth()) / 2);

                            int pos_y = Math.round((getHoleCard1().getHeight() - image.getIconHeight()) / 2);

                            int gif_frames_count = isgif ? Helpers.getGIFFramesCount(u) : 0;

                            Helpers.GUIRun(() -> {

                                if (isgif) {
                                    getChat_notify_label().setIcon(image, gif_frames_count);
                                } else {
                                    getChat_notify_label().setIcon(image);
                                }

                                getChat_notify_label().setRepeat(action_gif ? 1 : NOTIFY_INGAME_GIF_REPEAT);

                                if (action_gif) {

                                    /* These sounds aren't mandatory for every action; they're hooked onto the
                                       label itself via addAudio so playback starts/ends on an exact GIF frame.
                                       (The thread that plays this audio does NOT wait on the barrier.) */
                                    if (getDecision() == Player.BET) {
                                        // The chip flies on this frame (gesture + sound in sync, INTACT).
                                        // signalChipLaunched releases the action thread: it closes the
                                        // action and commits the pot while the chip is in flight, so on
                                        // landing pot+stack+bet roll together (true). The bet sound can be
                                        // toggled off, but the callback (throw the chip + release the action
                                        // thread) MUST stay pinned to frame 32 — hence audio null when off.
                                        getChat_notify_label().addAudio(GameFrame.apuestaSonidoOn() ? "misc/bet.wav" : null, 32, 60, () -> {
                                            GameFrame.getInstance().getCrupier().launchChipToPot(this);
                                            signalChipLaunched();
                                        });
                                    } else if (getDecision() == Player.CHECK && Helpers.doubleSecureCompare(0f, call_required) < 0) {
                                        // Call sound is toggleable, but the callback (chip to pot + release
                                        // the thread) MUST stay pinned to frame 32: audio null when off.
                                        getChat_notify_label().addAudio(GameFrame.igualarSonidoOn() ? "misc/call.wav" : null, 32, 60, () -> {
                                            GameFrame.getInstance().getCrupier().launchChipToPot(this);
                                            signalChipLaunched();
                                        });
                                    } else if (getDecision() == Player.CHECK) {
                                        getChat_notify_label().addAudio(GameFrame.pasarSonidoOn() ? "misc/check.wav" : null, 5, 14);
                                    }
                                }

                                getChat_notify_label().setSize(image.getIconWidth(), image.getIconHeight());
                                getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());
                                getChat_notify_label().setOpaque(false);
                                getChat_notify_label().setLocation(pos_x, pos_y);
                                getChat_notify_label().setVisible(true);
                            });

                        } catch (Exception ex) {
                            Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    if (isgif) {

                        try {
                            gif_barrier.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                        } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                            Thread.currentThread().interrupt();
                            // Expected during pool shutdown — chat-image GIF
                            // barrier cancelled cooperatively.
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.INFO,
                                    "GIF barrier cancelled (cooperative cancellation)");
                        } catch (java.util.concurrent.TimeoutException ex) {
                            // The notify was superseded (or its GIF torn down) before
                            // the rendezvous completed: non-fatal, the label is hidden
                            // by whoever owns it now. Not an interrupt.
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.INFO,
                                    "GIF barrier timed out (superseded notify — cooperative cancellation)");
                        } catch (Exception ex) {
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        synchronized (getChat_notify_label()) {
                            if (Thread.currentThread().threadId() == chat_notify_thread) {
                                try {
                                    getChat_notify_label().wait(TTS_NO_SOUND_TIMEOUT);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    // Expected during pool shutdown.
                                    Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.INFO,
                                            "Chat notify wait interrupted (cooperative cancellation)");
                                }
                            }
                        }
                    }
                    synchronized (getChat_notify_label()) {
                        if (Thread.currentThread().threadId() == chat_notify_thread) {
                            Helpers.GUIRun(() -> {
                                getChat_notify_label().setVisible(false);
                            });
                        }
                    }
                });

            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public void refreshSecPotLabel() {

        // In run-it-twice the strip is PER FACE: each face splits HALF the pot, so it shows the
        // money won ON IT (pagar - pagar_face_base) and the profit against half the pot. Outside
        // RIT (tag null), it's the full pagar and bote, as always.
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

                int pos_y = Math.round(GameFrame.VISTA_COMPACTA > 0 ? (getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) : ((getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) / 2));

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

    public boolean isNotify_blocked() {
        return notify_blocked;
    }

    public JLabel getChip_label() {
        return chip_label;
    }

    @Override
    public GifLabel getChat_notify_label() {
        return chat_notify_label;
    }

    public JLayeredPane getPanel_cartas() {
        return panel_cartas;
    }

    // The position chip sits at panel_cartas's top-left corner (0, 0) — same anchor
    // refreshPositionChipIcons uses. Returns its on-screen center, or null if the seat isn't
    // showing.
    @Override
    public java.awt.geom.Point2D getPositionChipScreenCenter(int chip_w, int chip_h) {
        if (panel_cartas == null || !panel_cartas.isShowing()) {
            return null;
        }
        java.awt.Point tl = new java.awt.Point(0, 0);
        javax.swing.SwingUtilities.convertPointToScreen(tl, panel_cartas);
        return new java.awt.geom.Point2D.Double(tl.getX() + chip_w / 2.0, tl.getY() + chip_h / 2.0);
    }

    @Override
    public boolean isTimeout() {
        return timeout;
    }

    private void setPlayerBorder(Color color) {

        if (!timeout) {
            border_color = color;
        }

        repaint();

    }

    @Override
    public int getResponseTime() {

        return GameFrame.THINK_TIME - response_counter;
    }

    public Bot getBot() {
        return bot;
    }

    @Override
    public boolean isTurno() {
        return turno;
    }

    @Override
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

    @Override
    public boolean isWinner() {
        return winner;
    }

    public boolean isLoser() {
        return loser;
    }

    public JLabel getAvatar() {
        return avatar;
    }

    @Override
    public int getBuyin() {
        return buyin;
    }

    @Override
    public boolean isExit() {
        return exit;
    }

    @Override
    public void setExit() {

        if (!this.exit) {
            this.exit = true;
            this.timeout = false;

            Helpers.GUIRun(() -> {
                if (auto_action != null) {
                    auto_action.stop();
                }

                setPlayerBorder(new Color(204, 204, 204, 75));

                // Preserve the hole-card state the peer had at the moment of
                // leaving: if they were still active in the hand, the cards are
                // face-down (tapadas, visible_card=true) and stay that way as a
                // visual cue that they had a hand; if they had already folded,
                // fold() set visible_card=false and they remain hidden. A
                // resetearCarta() call here would flatten both cases to an empty
                // slot. The next-hand board reset purges everything anyway.

                setActionBackground(new Color(255, 102, 0));
                player_action.setForeground(Color.WHITE);
                setActionTextFitted(Translator.translate("ui.se_pira"));
                setPlayerActionIcon("exit.png");
                player_action.setVisible(true);

                chip_label.setVisible(false);
                sec_pot_win_label.setVisible(false);
            });

        }

    }

    @Override
    public double getPagar() {
        return pagar;
    }

    @Override
    public double getBote() {
        return bote;
    }

    // Live roll of the stack label (EDT-confined). The renderer only writes the text; the color
    // is still set by setStack/setStackDisplay. Lazily created (player_stack already exists on
    // first use, always on the EDT).
    private RollingCounter stack_roller;

    private RollingCounter stackRoller() {
        if (stack_roller == null) {
            stack_roller = new RollingCounter((v) -> player_stack.setText(Helpers.money2String(v)),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return stack_roller;
    }

    @Override
    public synchronized void setStack(double stack) {
        this.stack = Helpers.doubleClean(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(() -> {
                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);

                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));

                    player_stack.setForeground(Color.WHITE);
                }

                // Rolls the number to the new stack (constant speed; off/recover jumps). If the
                // action is about to throw a chip (defer_counter_rolls), it does NOT roll here:
                // the label stays at its previous value and rollCountersToModel rolls it when
                // the chip lands, together with the pot and the bet.
                if (!defer_counter_rolls) {
                    stackRoller().roll(stack, GameFrame.isCounterRollEnabled());
                }
            });
        }
    }

    // Paints ONLY the stack label with 'value' (without touching the model or the pot): used by
    // the animated stack-fill counter (buy-in / rebuy) to roll the number frame by frame. NOT
    // synchronized on purpose: it runs on the EDT (invoked by the counter's Timer) and the
    // caller deferring the model may be holding the player's monitor — synchronizing here would
    // deadlock. Respects the "see buy-in" override (player_stack_click) just like setStack.
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
            // Set outright (the fill animation already goes frame by frame); keeps the roller's
            // displayed value in sync so the next live roll starts from the right place.
            stackRoller().set(value);
        });
    }

    // Live roll of the player's bet label (player_pot = 'bote', their accumulated
    // contribution this hand). The renderer shows "----" when it's 0. EDT-confined.
    private RollingCounter bet_roller;

    private RollingCounter betRoller() {
        if (bet_roller == null) {
            bet_roller = new RollingCounter(
                    (v) -> player_pot.setText(Helpers.doubleSecureCompare(0f, v) < 0 ? Helpers.money2String(v) : "----"),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return bet_roller;
    }

    // Live-roll deferral flag: set by the action handler BEFORE setBet when a chip is about to
    // fly, so the stack/bet labels don't outrun it. volatile: written by the action thread and
    // read by setStack/setBet (on the EDT) and rollCountersToModel (on landing).
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

    @Override
    public synchronized void setBet(double new_bet) {

        double old_bet = bet;

        bet = Helpers.doubleClean(new_bet);

        if (Helpers.doubleSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.doubleClean(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            // If the action is about to throw a chip (defer), it does NOT roll here: the bet
            // label stays put and rollCountersToModel rolls it on landing, together with the
            // stack and the pot.
            if (!defer_counter_rolls) {
                betRoller().roll(bote, GameFrame.isCounterRollEnabled());
            }
        });

    }

    public synchronized double postAnte(double ante) {

        if (Helpers.doubleSecureCompare(0f, stack) >= 0) {
            return 0f; // already all-in / no chips left: nothing to ante
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
            // If the ante chip is about to fly to the pot (defer), it does NOT roll here: it's
            // deferred and rollCountersToModel rolls it on landing, together with the stack
            // and the pot.
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

    @Override
    public void esTuTurno() {
        // Stack-fill gate: if this player is mid-way through a stack fill animation (buy-in or
        // rebuy), don't activate their turn (border + buttons) until it finishes. The rest of
        // the game isn't blocked by the animation; only this turn waits.
        GameFrame.getInstance().getCrupier().awaitStackFillIfPending(this.nickname);
        turno = true;

        GameFrame.getInstance().getCrupier().disableAllPlayersTimeout();

        // Once setExit() has painted the orange "SE PIRA" badge, the slot must
        // stay that way until the next hand purges the board. esTuTurno fires
        // when rondaApuestas iterates to this player's slot — if a race makes
        // the peer's EXIT arrive at our lambda before main thread reaches the
        // iteration, setExit runs first and esTuTurno would then repaint the
        // orange "thinking" border + the "pensando" label over it. Bail before
        // touching any GUI so SE PIRA wins. The do-while at the caller still
        // exits cleanly because readActionFromRemotePlayer detects isExit and
        // returns a synth FOLD that triggers finTurno (no UI work needed).
        if (this.exit) {
            return;
        }

        if (this.getDecision() == Player.NODEC) {

            call_required = GameFrame.getInstance().getCrupier().getApuesta_actual() - bet;

            Helpers.GUIRun(() -> {
                setPlayerBorder(Color.ORANGE);

                setActionBackground(new Color(204, 204, 204, 75));

                player_action.setForeground(Color.LIGHT_GRAY);

                setActionTextFitted(Translator.translate("ui.pensando"));

                setPlayerActionIcon("action/thinking.png");

                // Configurable think time: disabled => a static FULL bar (no countdown). The
                // actual auto-fold is done by the host via isExit(), not this bar.
                if (GameFrame.THINK_TIME_ENABLED) {
                    Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                } else {
                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                }

            });

            if (!GameFrame.TEST_MODE) {

                // Maximum think time
                Helpers.GUIRun(() -> {
                    response_counter = GameFrame.THINK_TIME;
                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    auto_action = new Timer(1000, new ActionListener() {
                        long t = GameFrame.getInstance().getCrupier().getTurno();

                        @Override
                        public void actionPerformed(ActionEvent ae) {

                            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isSomePlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && !WaitingRoomFrame.getInstance().isExit() && response_counter > 0 && t == GameFrame.getInstance().getCrupier().getTurno() && auto_action.isRunning() && getDecision() == Player.NODEC) {

                                // Disabled => does NOT decrement (counter frozen): the remote bar
                                // doesn't count down and the timeout auto-stop never fires; the
                                // host decides the remote player's turn on its own.
                                if (GameFrame.THINK_TIME_ENABLED) {
                                    response_counter--;
                                }

                                // setValue(response_counter) would be redundant: smoothCountdown
                                // already repaints the bar on a ms scale via its own internal
                                // Timer. Calling setValue here on a seconds scale caused flicker.

                                if (GameFrame.THINK_TIME_ENABLED && response_counter == GameFrame.getHurryupThreshold() && Helpers.doubleSecureCompare(0f, call_required) < 0) {
                                    if (GameFrame.avisoTiempoSonidoOn()) {
                                        Audio.playWavResource("misc/hurryup.wav");
                                    }
                                }

                                if (GameFrame.THINK_TIME_ENABLED && response_counter == 0) {
                                    Helpers.threadRun(() -> {
                                        Audio.playWavResourceAndWait("misc/timeout.wav", true, false, !GameFrame.avisoTiempoSonidoOn());
                                        GameFrame.getInstance().checkPause();
                                        Helpers.GUIRun(() -> {
                                            if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                                auto_action.stop();
                                            }
                                        });
                                    });
                                }

                            }

                            repaint();
                        }

                    });

                    auto_action.start();

                });
            }

        } else {

            finTurno();
        }

    }

    public void setDecisionFromRemotePlayer(int decision, double bet) {
        Helpers.threadRun(() -> {
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
            Helpers.GUIRun(() -> {
                if (auto_action != null) {
                    auto_action.stop();
                }
            });

            this.decision = decision;

            switch (this.decision) {
                case Player.CHECK:
                    check();
                    break;
                case Player.FOLD:
                    fold();
                    break;
                case Player.BET:
                    bet(bet);
                    break;
                case Player.ALLIN:
                    allin();
                    break;
                default:
                    break;
            }
        });

    }

    @Override
    public void markFoldedOnRecover() {
        // The private setDecision sets decision=FOLD and paints it gray (renderDecisionVisual),
        // with NO sound or cinematic (unlike fold()/setDecisionFromRemotePlayer). Respects the
        // exit guard: if the peer is still marked as gone, it leaves the orange "SE PIRA" badge
        // alone. Called by the recover skip so the seat of whoever left shows folded (gray) for
        // that hand.
        setDecision(Player.FOLD);
    }

    private void setDecision(int dec) {

        this.decision = dec;

        raise = false;

        reraise = false;

        // If the peer has already left, setExit() has painted the slot orange
        // with "SE PIRA" — the player_action label, the colours, the icon, the
        // border, the chip label. The synthetic FOLD that rondaApuestas issues
        // to advance the betting loop must NOT overwrite that visual state with
        // a regular fold/check/bet decoration. Keep the internal decision in
        // sync with the betting logic (already done above) but stop here so the
        // GUI stays as setExit left it.
        if (this.exit) {
            return;
        }

        renderDecisionVisual(dec);
    }

    // Visual rendering of a decision (label/border/icon + backgrounds), without mutating state.
    // Extracted from setDecision so the run-it-twice rewind can RE-PAINT the last action (restore
    // the black all-in, etc.) without the side effects (sound, bet, finTurno) the normal flow has.
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

                        raise = true;

                        reraise = (conta_raise_snapshot > 0);

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
                    // SINGLE read of bet+stack for the guard and the text: they're volatile and
                    // the all-in money moves in two steps (bet goes up, then stack goes down) on
                    // another thread. With separate reads the guard could see the inflated sum
                    // mid-setBet while the text saw the already-settled one, leaking a negative
                    // amount into the label ("ALL IN (+-0.90)").
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

        Helpers.GUIRun(() -> {
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

    // Run-it-twice rewind: re-applies the render of the last saved action (decision) and clears
    // SIDE-A's winner/loser green/red, leaving the hole cards revealed. Doesn't touch pots or
    // stacks (the pot persists across sides). If the peer left, keeps their exit visual.
    @Override
    public void repaintLastAction() {
        if (this.exit) {
            return;
        }
        this.winner = false;
        this.loser = false;
        // Run-it-twice: forgets SIDE-A's hover highlight before the rewind (idempotent if no
        // hover was active). DISCARDED without restoring the color: renderDecisionVisual (below)
        // re-paints the label to the decision (ALL IN); restoring SIDE-A's loser red here would
        // leave it hanging over FACE-B. Re-focusing the hole cards and SIDE-B's settle rebuild
        // the rest.
        Helpers.GUIRun(this::discardShowdownHandHighlight);
        this.showdown_hand_cards = null;
        // Limpia la franja de side pots de SIDE-A (se recalcula en SIDE-B).
        this.botes_secundarios.clear();
        // FACE-B's baseline = whatever accumulated on FACE-A: FACE-B's strip shows
        // 'pagar - base', i.e. ONLY what's won on FACE-B (pagar keeps accumulating both faces
        // for accounting).
        this.pagar_face_base = this.pagar;
        // Re-focuses the hole cards: SIDE-A's showdown dims the losers' cards; on SIDE-B they
        // must look bright again (re-evaluated).
        Helpers.GUIRun(() -> {
            holeCard1.enfocar();
            holeCard2.enfocar();
            sec_pot_win_label.setVisible(false);
            // Neutral border: in the normal flow finTurno restores it (which the rewind doesn't
            // call), and renderDecisionVisual only repaints the border for ALLIN/FOLD; without
            // this, SIDE-A's winner/loser green/red would survive on CHECK/BET (e.g. whoever
            // covers the all-in).
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }
        });
        renderDecisionVisual(this.decision);
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

    public void finTurno() {

        stopActionTimer();

        Audio.stopWavResource("misc/hurryup.wav");

        turno = false;

        synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
            GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
        }

        Helpers.GUIRun(() -> {
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }
        });

    }

    private void fold() {

        setDecision(Player.FOLD);

        if (GameFrame.foldSonidoOn()) {
            Audio.playWavResource("misc/fold.wav");
        }

        // Skip the GIF cinematic entirely when this is an autofold of a peer
        // that already left. The chat-notify label belongs to a player slot
        // that the UI has already torn down, the GIF never finishes painting,
        // its barrier never gets the "frames done" party, and the await below
        // blocks the game thread for GIF_BARRIER_TIMEOUT seconds before
        // throwing TimeoutException. Just play the sound and finish the turn.
        if (GameFrame.cinematicasAccionOn() && !this.isNotify_blocked() && !this.isExit()) {
            int r = 1 + new Random().nextInt(3);

            setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/fold" + String.valueOf(r) + ".gif"));

            // Barrier captured into a local, same as in check(). Reading the field twice (once
            // for the null-check, once for the await) let a chat notify sneak in between the two
            // reads and replace the barrier; this thread would then join as an EXTRA party in the
            // new one, which for a chat image only has two parties — tripping it early and
            // cutting that animation short. fold() fires with three different GIFs and much more
            // often than a pure check.
            java.util.concurrent.CyclicBarrier fold_barrier = getChat_notify_label().getGif_barrier();

            if (fold_barrier != null) {
                try {
                    fold_barrier.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                    Thread.currentThread().interrupt();
                    // Expected during pool shutdown — fold animation barrier
                    // cancelled cooperatively.
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                            "Fold animation barrier cancelled (cooperative cancellation)");
                } catch (java.util.concurrent.TimeoutException ex) {
                    // The notify was superseded (or its GIF torn down) before
                    // the rendezvous completed: non-fatal, the label is hidden
                    // by whoever owns it now. Not an interrupt.
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                            "Fold animation barrier timed out (superseded notify — cooperative cancellation)");
                } catch (Exception ex) {
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        // Only hide the hole cards on a real fold. When fold() runs as part of
        // the exit synth flow (peer left mid-turn → readActionFromRemotePlayer
        // returns a local FOLD → setDecisionFromRemotePlayer → fold()), the
        // contract is: cards stay face-down (tapadas) as the visual cue that
        // the peer had a hand when they left. Hiding them here would flatten
        // that to an empty slot, indistinguishable from the "peer folded
        // before leaving" case which fold() handled BEFORE setExit was called
        // (and therefore actually wants the cards hidden).
        if (!this.exit) {
            holeCard1.setVisibleCard(false);
            holeCard2.setVisibleCard(false);
        }

        finTurno();
    }

    // Waits for the cinematic's chip to LAUNCH (GIF frame 32, where addAudio throws it), not for
    // the whole GIF to end. This way the action closes as soon as the chip is in flight: the pot
    // gets committed and, on landing, the counters roll along with it (all three at once, clean
    // as if there were no cinematic), while the GIF plays out its remaining frames separately.
    // Timeout = GIF_BARRIER_TIMEOUT: if the cinematic dies before launching, the action still
    // proceeds (without the chip animation) instead of hanging.
    private void awaitChipLaunch() {
        CountDownLatch l = chip_launch_latch;
        if (l == null) {
            return;
        }
        try {
            l.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                    "Chip-launch wait interrupted (cooperative cancellation)");
        }
    }

    private void signalChipLaunched() {
        CountDownLatch l = chip_launch_latch;
        if (l != null) {
            l.countDown();
        }
    }

    private void check() {

        final boolean is_call = Helpers.doubleSecureCompare(0f, call_required) < 0;

        // CALL with money: a chip is about to fly; stack/bet are NOT rolled here — launchChipToPot
        // rolls them ON LANDING together with the pot (all three at once, as if there were no
        // cinematic). Pure check: no money, nothing to defer.
        setCounterRollDeferred(is_call && GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

        setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

        setDecision(Player.CHECK);

        // See fold() comment: skip cinematic for exited players to avoid the
        // 10-second blocking await on a barrier the GIF callback never closes.
        if (GameFrame.cinematicasAccionOn() && !this.isNotify_blocked() && !this.isExit()) {

            if (is_call) {
                // The chip flies on GIF frame 32 (in sync, INTACT). We wait ONLY for it to
                // launch, not for the GIF to finish: the remaining frames play out on their own
                // while the round continues.
                int r = 1 + new Random().nextInt(4);
                setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/call" + String.valueOf(r) + ".gif"), false);
                awaitChipLaunch();
            } else {
                // Pure check (no money): the usual BLOCKING cinematic (no counters to sync;
                // check.wav is tied to a GIF frame).
                setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/check.gif"));
                // Same pattern as fold(): the barrier is captured into a local and awaited WITH a
                // timeout. Reading the field twice (once for the null-check, once for the await)
                // could end up waiting on a different barrier than the one just installed, if
                // another notify replaced it in between; and without a timeout that wait never
                // ends. Hanging here freezes the WHOLE table, not just this seat: finTurno never
                // runs, `turno` never goes down, and the rondaApuestas loop waiting on this
                // player has no deadline.
                java.util.concurrent.CyclicBarrier check_barrier = getChat_notify_label().getGif_barrier();
                if (check_barrier != null) {
                    try {
                        check_barrier.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                    } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                        Thread.currentThread().interrupt();
                        // Expected during pool shutdown — animation barrier
                        // cancelled cooperatively.
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                                "Animation barrier cancelled (cooperative cancellation)");
                    } catch (java.util.concurrent.TimeoutException ex) {
                        // The notify was superseded (or its GIF torn down) before the rendezvous
                        // closed: not fatal, the label is hidden by whoever owns it now. Not
                        // an interruption.
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                                "Check animation barrier timed out (superseded notify — cooperative cancellation)");
                    } catch (Exception ex) {
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

        } else if (is_call) {
            if (GameFrame.igualarSonidoOn()) {
                Audio.playWavResource("misc/call.wav");
            }
            GameFrame.getInstance().getCrupier().launchChipToPot(this);
        } else {
            if (GameFrame.pasarSonidoOn()) {
                Audio.playWavResource("misc/check.wav");
            }
        }

        finTurno();

    }

    public double getEffectiveStack() {

        return Helpers.doubleClean(this.stack) + Helpers.doubleClean(this.bote) + Helpers.doubleClean(this.pagar);

    }

    private void bet(double new_bet) {

        // The chip flies on GIF frame 32 (addAudio), in sync with the gesture and the sound —
        // INTACT. stack/bet are NOT rolled here; launchChipToPot rolls them ON LANDING together
        // with the pot, all three at once (same as WITHOUT the cinematic).
        setCounterRollDeferred(GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

        setBet(new_bet);

        setDecision(Player.BET);

        // See fold() comment: skip cinematic for exited players to avoid the
        // 10-second blocking await on a barrier the GIF callback never closes.
        if (GameFrame.cinematicasAccionOn() && !this.isNotify_blocked() && !this.isExit()) {
            int r = 1 + new Random().nextInt(4);

            // We wait ONLY for the chip to launch (frame 32), not for the whole GIF to finish:
            // from there the round closes the action and commits the pot while the chip is
            // flying -> on landing, pot+stack+bet roll together, cleanly; the GIF's remaining
            // frames play out on their own.
            setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/bet" + String.valueOf(r) + ".gif"), false);
            awaitChipLaunch();

        } else {
            if (GameFrame.apuestaSonidoOn()) {
                Audio.playWavResource("misc/bet.wav");
            }
            GameFrame.getInstance().getCrupier().launchChipToPot(this);
        }

        if (GameFrame.SONIDOS_CHORRA && raise) {

            Audio.playWavResource("misc/raise.wav");

        }

        finTurno();

    }

    private void allin() {

        // A chip is about to fly (launchChipToPot right below, BEFORE setBet): stack/bet are NOT
        // rolled in setBet; rollCountersToModel (on landing) rolls them together with the pot.
        // setBet runs before the chip lands, so the model is already current when onLand reads
        // it. All three at once.
        setCounterRollDeferred(GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

        if (GameFrame.allinSonidoOn()) {
            Audio.playWavResource("misc/allin.wav");
        }
        GameFrame.getInstance().getCrupier().launchChipToPot(this);

        Init.PLAYING_CINEMATIC = true;

        Helpers.threadRun(() -> {
            if (!GameFrame.getInstance().getCrupier().remoteCinematicAllin()) {
                GameFrame.getInstance().getCrupier().soundAllin();
            }
        });

        // setBet BEFORE setDecision on purpose (same order as bet() and check()): the all-in
        // render that setDecision queues to the EDT reads bet+stack, so it reads them already
        // settled instead of racing the money movement mid-setBet.
        setBet(this.stack + this.bet);

        setDecision(Player.ALLIN);

        finTurno();

    }

    public int getDecision() {
        return decision;
    }

    public double getBet() {
        return bet;
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

            if (val && GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getParticipantes().get(this.nickname).isForce_reset_socket() && GameFrame.errorRedSonidoOn()) {
                Audio.playWavResource("misc/network_error_" + GameFrame.LANGUAGE.toLowerCase() + ".wav");
            }

        }

    }

    /**
     * Creates a new remote-player seat.
     */
    public RemotePlayer() {

        Helpers.GUIRunAndWait(() -> {
            initComponents();
            setOpaque(false);
            setBackground(null);
            installShowdownHandHighlight();
            // Avatar magnifier (with the seat's stack alongside): the provider returns the SAME
            // thing setAvatar looks up (file path, "*" for a bot, "" for no avatar) and is
            // evaluated when shown, not now — at this point the seat has no nick yet.
            AvatarZoomOverlay.install(avatar, player_stack, player_name, () -> nickname == null ? "" : GameFrame.getInstance().getNick2avatar().get(nickname));
            latency_label.setVisible(false);
            // Translated placeholder until the first PING arrives (the .form's text is
            // just the design-time default).
            latency_label.setText(Translator.translate("conn.latencia_format", "*", "*"));
            // If the .form contains a latency_dot_widget placed by hand in NetBeans, wire it
            // up here. Otherwise, no-op.
            try {
                java.lang.reflect.Field f = getClass().getDeclaredField("latency_dot_widget");
                f.setAccessible(true);
                Object widget = f.get(this);
                if (widget instanceof LatencyDot) {
                    setLatencyDot((LatencyDot) widget);
                    ((LatencyDot) widget).applyZoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                }
            } catch (NoSuchFieldException nsfe) {
                // OK: not added to the .form yet.
            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.WARNING, "Could not wire latency_dot_widget", ex);
            }
            player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP))));
            player_action.setPreferredSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP))));
            hands_win.setVisible(false);
            sec_pot_win_label.setVisible(false);
            sec_pot_win_label.setHorizontalAlignment(JLabel.CENTER);
            sec_pot_win_label.setOpaque(true);
            sec_pot_win_label.setFocusable(false);
            sec_pot_win_label.setFont(player_action.getFont().deriveFont(player_action.getFont().getStyle(), Math.round(player_action.getFont().getSize() * 0.7f)));
            panel_cartas.add(sec_pot_win_label, Integer.valueOf(1003));
            chat_notify_label.setVisible(false);
            chat_notify_label.setFocusable(false);
            chat_notify_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chat_notify_label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!Helpers.isReleaseInsideComponent(e)) {
                        return;
                    }
                    chat_notify_label.setVisible(false);
                    if (SwingUtilities.isRightMouseButton(e)) {
                        notify_blocked = true;
                    }
                    Helpers.threadRun(() -> {
                        synchronized (chat_notify_label) {

                            chat_notify_label.notifyAll();
                        }
                    });
                }
            });
            panel_cartas.add(chat_notify_label, Integer.valueOf(1002));
            rebuy_gif_label.setVisible(false);
            rebuy_gif_label.setFocusable(false);
            // Unlike chat_notify_label, this GIF does NOT hide on click: an empty listener that
            // also consumes the event (without it, the click would fall through to the card
            // viewer underneath). Only setRebuying(false) removes it.
            rebuy_gif_label.addMouseListener(new MouseAdapter() {
            });
            panel_cartas.add(rebuy_gif_label, Integer.valueOf(1001));
            shuffle_cascade_gif_label.setVisible(false);
            shuffle_cascade_gif_label.setFocusable(false);
            // Same as rebuy_gif_label: an empty listener that consumes the click so it doesn't
            // fall through to the card viewer underneath. The listener is permanent;
            // hideShuffleCascadeOverlay only hides the label (setVisible(false) + setIcon(null)).
            // Layer 1002 (above chip/rebuy at 1001): during the shuffle there's no active chat
            // notify to compete with for visibility.
            shuffle_cascade_gif_label.addMouseListener(new MouseAdapter() {
            });
            panel_cartas.add(shuffle_cascade_gif_label, Integer.valueOf(1002));
            chip_label.setVisible(false);
            chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chip_label.setOpaque(false);
            chip_label.setFocusable(false);
            chip_label.setSize(new Dimension(100, 100));
            panel_cartas.add(chip_label, Integer.valueOf(1001));
            border_color = ((LineBorder) getBorder()).getLineColor();
            danger.setVisible(false);
            player_pot.setText("----");
            disablePlayerAction();
            Helpers.setScaledIconLabel(utg_icon, getClass().getResource("/images/utg.png"), 41, 31);
            utg_icon.setVisible(false);
            icon_zoom_timer = new Timer(GameFrame.GUI_RENDER_WAIT, (ActionEvent ae) -> {
                icon_zoom_timer.stop();
                zoomIcons();
                holeCard1.updateImagePreloadCache();
                holeCard2.updateImagePreloadCache();
                refreshNotifyChatLabel();
                refreshRebuyGifLabel();
            });
            icon_zoom_timer.setRepeats(false);
            icon_zoom_timer.setCoalesce(false);
            iwtsth_blink_timer = new Timer(1500, (ActionEvent ae) -> {
                if (player_action.getBackground() == Color.RED) {
                    setActionBackground(Color.WHITE);
                    player_action.setForeground(Color.RED);
                } else {
                    setActionBackground(Color.RED);
                    player_action.setForeground(Color.WHITE);
                }

                setActionTextFitted(player_action.getText().equals(Translator.translate("ui.pierde_3")) ? Translator.translate("iwtsth.iwtsth") : Translator.translate("ui.pierde_3"));
            });
        });

    }

    public void playerActionClick() {
        Helpers.GUIRun(() -> {
            player_actionMouseClicked(null);
        });
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;

        Helpers.GUIRun(() -> {

            if (GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getParticipantes().get(nickname).isUnsecure_player()) {
                danger.setText(Translator.translate("radar.posible_trampos"));
                danger.setVisible(true);
            } else if (!GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getSala_espera().isUnsecure_server()) {
                danger.setText(Translator.translate("radar.posible_trampos"));
                danger.setVisible(true);
            }

            player_name.setText(nickname);

            // Server's nick highlighted on the client view.
            if (!GameFrame.getInstance().isPartida_local()
                    && GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                player_name.setForeground(Color.YELLOW);
            }

            // "$" marks a bot (no identity, nothing clickable) — same convention the
            // anticheat-log affordance below already uses, and null-safe (no lookup
            // in participantes, which may not hold this nick yet on the client).
            if (!nickname.contains("$")) {
                // Human peer: name opens the anticheat log; right-clicking the avatar
                // opens the identicon of this peer's Ed25519 public identity key.
                // Shown for both host and client views.
                Helpers.setTranslatedToolTip(player_name, "ui.click_anticheat_log");
                Helpers.setTranslatedToolTip(avatar, "ui.click_identity_identicon");
                avatar.setCursor(new Cursor(Cursor.HAND_CURSOR));
            } else {
                player_name.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                avatar.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        if (GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu()) {
            this.bot = new Bot(this);
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

        panel_cartas = new javax.swing.JLayeredPane();
        holeCard1 = new com.tonikelope.coronapoker.Card();
        holeCard2 = new com.tonikelope.coronapoker.Card();
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
        danger = new javax.swing.JLabel();
        player_action_panel = new RoundedPanel(20);
        player_action = new javax.swing.JLabel();
        latency_label = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204, 75), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

        panel_cartas.setDoubleBuffered(true);

        panel_cartas.setLayer(holeCard1, javax.swing.JLayeredPane.DEFAULT_LAYER);
        panel_cartas.setLayer(holeCard2, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

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
        player_pot.setDoubleBuffered(true);
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
        player_stack.setDoubleBuffered(true);
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
        player_name.setText("12345678901");
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
                .addComponent(hands_win))
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
                    .addComponent(avatar_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        indicadores_arribaLayout.setVerticalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        danger.setBackground(new java.awt.Color(255, 0, 0));
        danger.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        danger.setForeground(new java.awt.Color(255, 255, 255));
        danger.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger.setText("POSIBLE TRAMPOS@");
        danger.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        danger.setFocusable(false);
        danger.setOpaque(true);

        player_action.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_action.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                player_actionMouseClicked(evt);
            }
        });

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
                .addComponent(player_action)
                .addGap(0, 0, 0))
        );

        latency_label.setBackground(new java.awt.Color(0, 0, 255));
        latency_label.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        latency_label.setForeground(new java.awt.Color(255, 255, 255));
        latency_label.setText("Latencia: * ms | * ms");
        latency_label.setOpaque(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(danger, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(latency_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(indicadores_arriba, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_cartas, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(player_action_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(latency_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(danger)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panel_cartas)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_action_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!player_stack_click) {
            player_stack_click = true;

            // Shows the fixed buy-in (not the stack value): the roller is left invalidated so
            // that restoring jumps straight to the real stack instead of animating from here.
            stackRoller().invalidate();
            player_stack.setText(Helpers.money2String(this.buyin));
            setPlayerStackBackground(Color.GRAY);
            player_stack.setForeground(Color.WHITE);

            Helpers.threadRun(() -> {
                Helpers.pausar(1500);
                double s = getStack();
                Helpers.GUIRun(() -> {
                    if (GameFrame.hasRebought(nickname)) {
                        setPlayerStackBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        setPlayerStackBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    stackRoller().set(s);
                });
                player_stack_click = false;
            });
        }
    }//GEN-LAST:event_player_stackMouseClicked

    private void player_actionMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_actionMouseClicked

        // evt is null when invoked programmatically from playerActionClick(); in that case there's
        // no real click to validate. For a user click we require the left button released inside.
        if (evt != null && !Helpers.isRealClick(evt)) {
            return;
        }

        if (GameFrame.getInstance().isPartida_local() && this.timeout) {

            if (!GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("conn.este_usuario_tiene_problemas_de"), new ImageIcon(Init.class.getResource("/images/action/timeout.png"))) == 0) {
                GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nickname);
            }

        } else if (GameFrame.IWTSTH_RULE && isIwtsthCandidate() && GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized() && !GameFrame.getInstance().getCrupier().isIwtsthing() && !GameFrame.getInstance().getCrupier().isIwtsthing_request() && !GameFrame.getInstance().getCrupier().isIwtsth() && GameFrame.getInstance().getCrupier().isShow_time()) {

            GameFrame.getInstance().getCrupier().IWTSTH_REQUEST(GameFrame.getInstance().getLocalPlayer().getNickname());
        }
    }//GEN-LAST:event_player_actionMouseClicked

    // Re-entrancy guard for the identity identicon: its pubkey resolution is async (off-EDT under
    // SQL_LOCK), so the modal dialog does not block input the instant of the click — this prevents a
    // rapid double-click from stacking duplicate dialogs. Set/read on the EDT; cleared when the modal
    // dialog closes, or if resolution fails before it opens.
    private volatile boolean avatar_identity_opening = false;

    private void avatarMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatarMouseClicked
        // Plain left click: nothing else claims it on a seat avatar, and the
        // primary action of a control belongs to the primary button. The right
        // button is reserved for controls whose left click is already taken (the
        // avatar of the new game dialog, where it restores the default one).
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // Identity: clicking a remote human avatar opens the identicon of that
        // peer's Ed25519 public identity. The dialog includes a "Verificar identidad"
        // button that marks (nick, pubkey) as verified_oob if the user has compared
        // the fingerprint with the peer through an external secure channel.
        //
        // The pubkey is normally cached on the Participant during the JOIN handshake
        // (host's pubkey rides the intro packet; the rest piggyback on USERSLIST /
        // NEWUSER atomically with their nick + avatar). If for any reason the
        // Participant has no pubkey, we fall back to the TOFU-pinned pubkey from
        // known_identities so the click still works.
        Participant par = GameFrame.getInstance().getParticipantes().get(this.nickname);
        if (par == null || par.isCpu()) {
            return;
        }
        if (avatar_identity_opening) {
            return; // a resolution/dialog for this avatar is already in flight
        }
        avatar_identity_opening = true;
        // The pubkey fallback reads known_identities via TOFUResolver under SQL_LOCK, which must
        // never run on the EDT — resolve it off the EDT, then open the dialog back on the EDT.
        if (Helpers.threadRun(() -> {
            try {
                byte[] pubkey = par.getIdentity_pubkey();
                if (pubkey == null) {
                    pubkey = TOFUResolver.getPinnedPubkey(this.nickname);
                    if (pubkey != null) {
                        par.setIdentity_pubkey(pubkey);
                    }
                }
                final byte[] pubkeyResolved = pubkey;
                Helpers.GUIRun(() -> {
                    try {
                        if (pubkeyResolved == null) {
                            java.util.logging.Logger.getLogger(RemotePlayer.class.getName()).log(
                                    java.util.logging.Level.WARNING,
                                    "No identity pubkey recorded for {0}; cannot open identity identicon",
                                    this.nickname);
                            return;
                        }
                        IdenticonDialog identicon = new IdenticonDialog(
                                GameFrame.getInstance(), true, this.nickname,
                                pubkeyResolved, IdenticonDialog.Mode.IDENTITY, pubkeyResolved);
                        identicon.setLocationRelativeTo(GameFrame.getInstance());
                        identicon.setVisible(true); // modal: blocks until closed
                    } finally {
                        avatar_identity_opening = false;
                    }
                });
            } catch (Throwable t) {
                // Resolution failed before the dialog could be scheduled — clear the guard so the
                // avatar stays clickable.
                avatar_identity_opening = false;
            }
        }) == null) {
            // Pool shutting down (teardown): the resolution will never run — clear the guard.
            avatar_identity_opening = false;
        }
    }//GEN-LAST:event_avatarMouseClicked

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        if (GameFrame.getInstance().isPartida_local() && this.timeout) {

            if (!GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("conn.este_usuario_tiene_problemas_de"), new ImageIcon(Init.class.getResource("/images/action/timeout.png"))) == 0) {
                GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nickname);
            }

        }

    }//GEN-LAST:event_player_nameMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JLabel danger;
    private javax.swing.JLabel hands_win;
    private com.tonikelope.coronapoker.Card holeCard1;
    private com.tonikelope.coronapoker.Card holeCard2;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JLabel latency_dot_widget;
    private javax.swing.JLabel latency_label;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JLayeredPane panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JPanel player_action_panel;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JPanel player_pot_panel;
    private javax.swing.JLabel player_stack;
    private javax.swing.JPanel player_stack_panel;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    public boolean isIwtsthCandidate() {
        return isLoser() && isActivo() && getHoleCard1().isVisible_card() && getHoleCard1().isTapada();
    }

    public void zoomIcons() {

        Helpers.threadRun(() -> {
            synchronized (zoom_lock) {
                Helpers.GUIRunAndWait(() -> {
                    setAvatar();
                    utgIconZoom();
                    actionIconZoom();
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

                    player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                    player_action.setPreferredSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                    setPlayerBorder(border_color);

                    getAvatar().setVisible(false);

                    utg_icon.setVisible(false);

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
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
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

    public Timer getIwtsth_blink_timer() {
        return iwtsth_blink_timer;
    }

    public Timer getRebuy_countdown_timer() {
        return rebuy_countdown_timer;
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.RED);

            if (!holeCard1.isTapada() || !GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized()) {

                setActionBackground(Color.RED);
                player_action.setForeground(Color.WHITE);
                holeCard1.desenfocar();
                holeCard2.desenfocar();

            } else {
                setActionBackground(Color.WHITE);
                player_action.setForeground(Color.RED);
                player_action.setCursor(new Cursor(Cursor.HAND_CURSOR));
                holeCard1.setIwtsth_candidate(this);
                holeCard2.setIwtsth_candidate(this);
            }

            setActionTextFitted(msg);

            setPlayerActionIcon("action/angry.png");

        });

    }

    @Override
    public void setShowdownHand(java.util.List<Card> cartas) {
        this.showdown_hand_cards = cartas;
    }

    // Enter/exit on the hand label (installed from the constructor): on enter, highlights this
    // player's hand — winner or loser — (focuses their cards, dims the rest of the table) and
    // paints their label yellow/black; on exit, restores it. Applies to anyone whose hand is
    // visible: winner(s), a loser who showed at showdown, or a loser/folded player who showed
    // later (forced IWTSTH or the voluntary SHOW button) — in all of them, revealing sets
    // showdown_hand_cards. Coexists with the IWTSTH click listener already on player_action
    // (while an IWTSTH candidate hasn't shown, showdown_hand_cards is null and enter is a no-op).
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

    // on=true: only if the option is enabled, this player is NOT a spectator, has a visible hand
    // (showdown_hand_cards) and we're still in show_time. Focuses ONLY their hand's cards and
    // dims every other table card (saving each one's prior focus first), and paints the label
    // yellow/black. Works for ANY player with a shown hand: winner(s) and losers alike (the gate
    // no longer excludes winners; in a mixed run-it-twice it highlights whatever hand is in
    // showdown_hand_cards). A spectator deals no cards this hand, so anything left there can only
    // be a leftover from the last hand they played. Someone who left DOES pass the gate: they can
    // leave with a live hand (all-in run-out) and it gets resolved in this same showdown.
    // on=false: unconditional (defensive) restore.
    private void highlightShowdownHand(boolean on) {
        if (on) {
            final java.util.List<Card> cartas = showdown_hand_cards;
            Crupier crupier = GameFrame.getInstance() != null ? GameFrame.getInstance().getCrupier() : null;

            if (!GameFrame.RESALTAR_JUGADA_SHOWDOWN || isSpectator() || cartas == null || crupier == null || !crupier.isShow_time()) {
                return;
            }

            Helpers.GUIRun(() -> {
                // Idempotency: if a highlight was left hanging, undo it before re-snapshotting.
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

    // Returns the table cards to the focus they had before the hover (the winner's highlight
    // comes back as-is) and removes the tint. Does NOT touch the label's color. Idempotent
    // (no-op if there's no snapshot). Must be called on the EDT.
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

    // Full restore (focus + label color) for mouseExited and the between-hands reset: the label
    // goes back to the loser's red exactly as it was.
    private void restoreShowdownHandHighlight() {
        restoreShowdownHandFocus();

        if (showdown_action_bg_snapshot != null) {
            setActionBackground(showdown_action_bg_snapshot);
            player_action.setForeground(showdown_action_fg_snapshot);
            showdown_action_bg_snapshot = null;
            showdown_action_fg_snapshot = null;
        }
    }

    // Discards the hover WITHOUT restoring the label color: for the run-it-twice rewind, where
    // renderDecisionVisual re-paints the label to the decision (ALL IN) right after; restoring
    // SIDE-A's loser red here would leave it hanging over FACE-B.
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

    public void setPosition(int pos) {

        switch (pos) {
            case Player.DEALER:

                if (GameFrame.getInstance().getCrupier().getDealer_nick().equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                        setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                    } else {

                        // Going ALL IN (setBet first: see allin())
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

                    // Going ALL IN (setBet first: see allin())
                    setBet(stack);

                    setDecision(Player.ALLIN);
                }

                break;
            case Player.SMALL_BLIND:

                if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                } else {

                    // Going ALL IN (setBet first: see allin())
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

    // silent: the animated rebuy counter (Crupier.animateRebuyStacks) already fired the
    // cash-register sound for the whole batch -> it does NOT repeat here. On the non-animated
    // path (silent=false) it plays as always, once per rebuy.
    public synchronized void reComprar(int cantidad, boolean silent) {

        // Re-check at apply time (anti-stale / anti-cheat): never exceed the table ceiling even
        // if the requested amount was larger or the stack changed between the request and the
        // start of the hand. headroom 0 -> rebuy voided.
        int applied = Math.min(cantidad, GameFrame.rebuyHeadroom(this.stack));
        if (applied <= 0) {
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.WARNING,
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

        // If the fill animation is rolling the rebuy (silent), IT paints the text+CYAN frame by
        // frame (via setStackDisplay, which already picks CYAN via hasRebought); painting it here
        // too would flash the final value mid-roll.
        if (!player_stack_click && !silent) {
            Helpers.GUIRun(() -> {
                player_stack.setText(Helpers.money2String(stack));
                setPlayerStackBackground(Color.CYAN);
                player_stack.setForeground(Color.BLACK);
            });
        }
    }

    // LOCK-FREE accessors: stack/bote/pagar/exit are volatile, so these plain getters/setters
    // don't need synchronized. Anti-deadlock KEY: the EDT reads them (table display, rebuy menu,
    // setSpectator, etc.); if they were synchronized they'd grab the player's monitor, and with
    // the game thread holding it across a GUIRunAndWait (setBet/setStack post blindly while the
    // stack fill rolls on the EDT) -> permanent EDT<->worker deadlock. Lock-free makes that
    // IMPOSSIBLE anywhere. Only the money's COMPOUND mutators (setStack/setBet/postAnte/
    // postStraddle/reComprar) stay synchronized; the EDT never calls them (only the game thread
    // does).
    @Override
    public double getStack() {
        return stack;
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    @Override
    public void resetGUI() {
        Helpers.GUIRunAndWait(() -> {
            if (orig_action_font != null && orig_action_font.getSize() != player_action.getFont().getSize()) {
                player_action.setFont(orig_action_font);
                orig_action_font = null;
            }

            sec_pot_win_label.setVisible(false);

            setOpaque(false);

            setBackground(null);

            setPlayerBorder(new java.awt.Color(204, 204, 204, 75));

            if (iwtsth_blink_timer.isRunning()) {

                iwtsth_blink_timer.stop();
            }

            player_name.setIcon(null);

            utg_icon.setVisible(false);

            // New hand: syncs the bet roller to 0 (shows "----") so this hand's first bet rolls
            // from 0 instead of from the previous hand's contribution.
            betRoller().set(0);

            setPlayerPotBackground(new Color(204, 204, 204, 75));

            player_pot.setForeground(Color.WHITE);

            player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

            if (conta_win > 0) {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            } else {
                hands_win.setVisible(false);
            }

            disablePlayerAction();

            if (!player_stack_click) {
                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);

                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));

                    player_stack.setForeground(Color.WHITE);
                }
            }

        });
    }

    @Override
    public void nuevaMano() {

        // Guarantee the avatar is painted at the start of EVERY hand. In the normal flow the
        // initial zoom triggers setAvatar via icon_zoom_timer, but on RECOVER that chain doesn't
        // run (SHUTDOWN_THREAD_POOL between games kills the spawned initial-zoom thread) → the
        // first hand post-recover would be left without an avatar. Calling it here is idempotent
        // and cheap.
        setAvatar();

        this.decision = Player.NODEC;

        this.notify_blocked = false;

        this.botes_secundarios.clear();

        this.pagar_face_base = 0f;

        this.winner = false;

        this.loser = false;

        // Showdown highlight: undoes any highlight left hanging if the previous hand ended with
        // the mouse over the label, and forgets the highlightable hand.
        highlightShowdownHand(false);
        this.showdown_hand_cards = null;

        this.bote = 0f;

        this.last_bote = null;

        this.bet = 0f;

        // Safety net: clears any counter-roll deferral left hanging from a previous hand (e.g. an
        // action cinematic interrupted before launching its chip) BEFORE setting this hand's
        // blind deferral. Without this, a stuck flag would leave this player's setStack/setBet
        // not rolling until their next chip. Only affects the counter roll.
        setCounterRollDeferred(false);

        resetGUI();

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {

            int rebuy = GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname);

            GameFrame.getInstance().getCrupier().getRebuy_now().remove(nickname);

            // If the rebuy was animated by the fill (animateRebuyStacks already rolled the stack
            // to its final value and played the cash-register sound), reComprar doesn't repeat
            // the sound. Uses the CAPTURED decision (isRebuyFillAnimated): if "Counters" got
            // toggled off mid-count, it stays silent (no double cash-register sound).
            reComprar(rebuy, GameFrame.getInstance().getCrupier().isRebuyFillAnimated());

        }

        setStack(stack + pagar);

        pagar = 0f;

        // If about to post a blind (BB/SB) whose chip will fly to the pot, don't roll its
        // stack/bet at posting time (setPosition->setBet(blind), right below): it's deferred and,
        // when its chip LANDS (flyForcedBetsToPot.onLand -> rollCountersToModel), it rolls
        // together with the pot. The pending winnings (setStack(stack+pagar) above) already
        // rolled, NOT deferred. Same gate as the flight (here game_recovered==0 always: the
        // recover block runs afterwards).
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

                // Going ALL IN (setBet first: see allin())
                setBet(stack);
                setDecision(Player.ALLIN);

            }

        }

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
            // In 3-handed games the dealer is the UTG; if they straddle, use the combined
            // dealer+straddle chip (the DEALER branch wins over the straddle branch below,
            // so it's resolved here).
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

        // Suppressed during the chip rotation (until the traveling chip lands): the position
        // chip is NOT painted even if we're called (e.g. from a table re-layout).
        final boolean suppressed = GameFrame.getInstance().getCrupier() != null
                && GameFrame.getInstance().getCrupier().isBigChipSuppressed(this);
        Helpers.GUIRun(() -> {
            if (isActivo() && !(holeCard1.isIniciada() && !holeCard1.isTapada()) && chip_label_icon != null && !suppressed) {
                chip_label.setIcon(chip_label_icon);
                chip_label.setSize(chip_label.getIcon().getIconWidth(), chip_label.getIcon().getIconHeight());
                chip_label.setLocation(0, 0);
                chip_label.setVisible(true);

                chip_label.repaint();

            } else {

                chip_label.setVisible(false);
            }
        });

    }

    @Override
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

    @Override
    public void disableUTG() {

        if (this.utg) {
            this.utg = false;

            Helpers.GUIRun(() -> {
                utg_icon.setVisible(false);
            });
        }
    }

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(() -> {
            utg_icon.setVisible(true);
        });
    }

    @Override
    public boolean isSpectator() {
        return this.spectator;
    }

    @Override
    public String getLastActionString() {

        // The action text is painted on player_action via GUIRun (async, on the EDT) from
        // renderDecisionVisual; the game thread gets here as soon as finTurno sets turno=false
        // and notifies, and could READ the label BEFORE the EDT repainted it — while it still
        // said "THINKING" — leaking "THINKING (n)" into the log instead of "FOLD/CHECK/...".
        // With cinematics OFF, fold doesn't even wait on the GIF barrier (which used to give the
        // EDT plenty of time), so the game thread won the race systematically. Reading the label
        // ON the EDT respects the queue's FIFO order: THIS action's setActionTextFitted was
        // already queued before finTurno notified, so it applies before this read.
        final String[] label = new String[]{""};
        Helpers.GUIRunAndWait(() -> label[0] = player_action.getText());

        String action = nickname + " ";

        switch (this.getDecision()) {
            case Player.FOLD:
            case Player.CHECK:
            case Player.BET:
            case Player.ALLIN:
                action += label[0] + " (" + Helpers.money2String(this.bote) + ")";
                break;
            default:
                break;
        }

        return action;
    }

    @Override
    public void setBuyin(int buyin) {
        this.buyin = buyin;

    }

    @Override
    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            // The hand reset (nuevaMano) only runs for active players, so the highlightable hand
            // from the last hand they played would stay stuck to the seat while they're a
            // spectator. Discarded without restoring the label color: the spectator repaint
            // below leaves it as it should be.
            Helpers.GUIRun(this::discardShowdownHandHighlight);
            this.showdown_hand_cards = null;

            Helpers.GUIRunAndWait(() -> {
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

    public void disablePlayerAction() {

        Helpers.GUIRun(() -> {
            setActionTextFitted(" ");
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionBackground(new Color(204, 204, 204, 75));
            setPlayerActionIcon(null);
        });
    }

    // Voluntary straddle: while UTG decides (blind to everyone) whether to post it, the other
    // peers paint the thinking icon and "STRADDLE?" on their seat — same look as a normal turn's
    // "THINKING", but without starting the turn countdown.
    public void showStraddleThinking() {
        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.ORANGE);
            setActionBackground(new Color(204, 204, 204, 75));
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionTextFitted(Translator.translate("straddle.pensando"));
            setPlayerActionIcon("action/thinking.png");
        });
    }

    // Clears the straddle's "thinking" visual: returns the seat to its neutral state (no action
    // + neutral border), same as disablePlayerAction. If the straddle was posted, the red chip
    // is painted separately by refreshPositionChipIcons (from applyStraddlePost).
    public void clearStraddleThinking() {
        disablePlayerAction();
        Helpers.GUIRun(() -> setPlayerBorder(new Color(204, 204, 204, 75)));
    }

    @Override
    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(() -> {
            setPlayerBorder(new Color(204, 204, 204, 75));
            player_name.setIcon(null);
            player_stack.setEnabled(true);
            disablePlayerAction();

        });

    }

    private void actionIconZoom() {

        if (player_action_icon != null) {

            setPlayerActionIcon(player_action_icon);

        }
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

        // Robust fallback if player_pot isn't laid out yet: try preferredSize, then the current
        // avatar's iconHeight, finally a reasonable default. Avoids BufferedImage(0,0) -> exception.
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

        String avatar_path = GameFrame.getInstance().getNick2avatar().get(nickname);

        if (!"".equals(avatar_path) && !"*".equals(avatar_path)) {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(avatar_path).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));

        } else if ("*".equals(avatar_path)) {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));

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

    // Serializes concurrent animated reveals of the same player (e.g. a duplicate/echoed
    // SHOWCARDS processed by two workers): the classic reveal was idempotent via isTapada() on
    // the EDT; the animated one re-checks under this lock. Dedicated lock on purpose: do NOT
    // synchronize on this (the player's synchronized methods, like setPagar, must never wait
    // on an animation).
    private final Object destape_animado_lock = new Object();

    public Object getDestape_animado_lock() {
        return destape_animado_lock;
    }

    // Shows the hand on the action label with a NEUTRAL style (the label's resting translucent
    // gray): used by the showdown's reveal pass to show WHAT the player has without giving away
    // the verdict yet. showCards' blue stays reserved for the folded players' voluntary SHOW
    // button. Same font-shrink handling for long hand names as setWinner/setLoser (which will
    // repaint over it in the verdict pass).
    public void showJugadaNeutral(String jugada) {

        Helpers.GUIRun(() -> {
            setActionBackground(new Color(204, 204, 204, 75));
            player_action.setForeground(Color.WHITE);

            setActionTextFitted(jugada);
        });
    }

    // Side effects of the classic reveal that must happen when the animated flip STARTS
    // (Crupier.mostrarAnimacionDestaparCartasJugador): hide the position chip, and if the
    // IWTSTH blink was active, stop it with its loser re-coloring (the LOSES label was already
    // showing and blinking, so this gives nothing away early). Exact replica of
    // destaparCartas(boolean) minus the actual card flip, which the animation engine handles.
    public void prepararDestapeAnimado() {

        Helpers.GUIRunAndWait(() -> {

            chip_label.setVisible(false);

            if (iwtsth_blink_timer.isRunning()) {

                iwtsth_blink_timer.stop();

                if (isLoser()) {
                    setActionBackground(Color.RED);
                    player_action.setForeground(Color.WHITE);
                    player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
    }

    @Override
    public void destaparCartas(boolean sound) {

        Helpers.GUIRun(() -> {

            if (getHoleCard1().isIniciada() && getHoleCard1().isTapada()) {

                if (sound && GameFrame.destapeSonidoOn()) {
                    Helpers.threadRun(() -> Audio.playPreloadedWav("misc/uncover.wav"));
                }

                chip_label.setVisible(false);

                getHoleCard1().destapar(false);

                getHoleCard2().destapar(false);

                if (iwtsth_blink_timer.isRunning()) {

                    iwtsth_blink_timer.stop();

                    if (isLoser()) {
                        setActionBackground(Color.RED);
                        player_action.setForeground(Color.WHITE);
                        player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            }
        });
    }

    // synchronized: the swap permutes values between the two Card components in several steps
    // and can be called from different threads (crupier, SHOWCARDS worker, IWTSTH worker). Two
    // concurrent swaps could leave both cards with the same value; serialized it's idempotent
    // (the second one sees c1 >= c2 and does nothing). Locks on this on purpose: it's a
    // microsecond-scale swap, never animated or blocked inside here.
    @Override
    public synchronized void ordenarCartas() {
        if (getHoleCard1().getValorNumerico() != -1 && getHoleCard2().getValorNumerico() != -1 && getHoleCard1().getValorNumerico() < getHoleCard2().getValorNumerico()) {

            // Sort the cards for readability
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

    // Visual "REBUY? (N)" countdown on the action label while this remote human decides ON THEIR
    // OWN machine whether to rebuy (local GameOverDialog/RebuyDialog). Purely cosmetic and humans
    // only: toggled by recibirRebuys (on the host, bots never enter this wait, and on clients
    // their REBUY arrives instantly from the host). Keeps checkGameOver's skull (doesn't touch
    // the icon); a LOCAL 1s timer, approximate (not synced with the remote's actual dialog):
    // counts the same seconds as the game-over RebuyDialog and, once it runs out, freezes on
    // "REBUY?" alone (never shows zero) — by then the remote will normally have already clicked
    // and their REBUY will be arriving. On turning off, restores the previous text (the hand they
    // lost with); if the decision was to become a spectator, setSpectator has already set
    // this.spectator and the restore is skipped (its repaint wins). All of this runs on the EDT
    // (a Swing Timer).
    public void setRebuying(boolean rebuying) {
        setRebuying(rebuying, false);
    }

    // 'recompro' only applies when turning off: true if the busted player's decision was to
    // REBUY — the action label switches to "REBOUGHT!" as feedback of the outcome (with a
    // single busted player the wait ends instantly, and without this there'd be no time to see
    // what happened) and it stays that way until the next hand's repaint. With false
    // (spectator/exit/timeout), the previous text is restored, and setSpectator repaints over
    // it if applicable.
    public void setRebuying(boolean rebuying, boolean recompro) {
        Helpers.GUIRun(() -> {
            if (rebuying) {
                if (this.exit || this.spectator || rebuying_visual) {
                    return;
                }
                rebuying_visual = true;
                rebuy_countdown_saved_text = player_action.getText();
                // LOCAL snapshot of the GAME OVER cinematic setting when starting: decides the
                // mode for the whole wait (later toggles don't affect it).
                if (GameFrame.cinematicasGameOverOn()) {
                    // GIF mode: the label stays FIXED on "REBUY?" (no number) and the countdown
                    // is driven by the game-over GIF over the cards — plays once in full (by
                    // frames, no clock) with its own audio; once done, the zero GIF stays frozen
                    // until the rebuy resolves.
                    setActionTextFitted(Translator.translate("rebuy.recompra_3"));
                    // repaint() the whole slot after each setText (same idiom as
                    // setPlayerActionIcon): the slot and the action panel are opaque rounded
                    // rects that don't paint their own corners (RoundedPanel / this class's
                    // paintComponent), and a partial repaint leaves orphan pixels in the 4 corners.
                    repaint();
                    mostrarRebuyGameOverGif(++rebuy_generation);
                } else {
                    // No-cinematics mode: numeric countdown on the label.
                    final int[] count = {GameOverDialog.REBUY_DIALOG_COUNTDOWN};
                    setActionTextFitted(Translator.translate("rebuy.recompra_3") + " (" + count[0] + ")");
                    repaint();
                    rebuy_countdown_timer = new Timer(1000, (e) -> {
                        if (--count[0] > 0) {
                            setActionTextFitted(Translator.translate("rebuy.recompra_3") + " (" + count[0] + ")");
                        } else {
                            setActionTextFitted(Translator.translate("rebuy.recompra_3"));
                            ((Timer) e.getSource()).stop();
                        }
                        repaint();
                    });
                    rebuy_countdown_timer.start();
                }
            } else {
                if (!rebuying_visual) {
                    return;
                }
                rebuying_visual = false;
                rebuy_generation++;
                if (rebuy_countdown_timer != null) {
                    rebuy_countdown_timer.stop();
                    rebuy_countdown_timer = null;
                }
                if (rebuy_gif_label.isVisible()) {
                    rebuy_gif_label.setVisible(false);
                    // setIcon(null) resets the GifLabel's pending audio: without this, a REBUY
                    // arriving between the show and the GIF's FIRST frame would leave the wav
                    // orphaned (the stop below would run before frame 1 ever triggered it). It
                    // also releases the reference to the GIF's Image.
                    rebuy_gif_label.setIcon((javax.swing.Icon) null);
                    // With several busted players at once, only ONE game_over.wav plays:
                    // it stops when the last visual in the group is removed.
                    if (--REBUY_GIF_ACTIVOS <= 0) {
                        REBUY_GIF_ACTIVOS = 0;
                        Audio.stopWavResource("misc/game_over.wav");
                    }
                }
                if (rebuy_countdown_saved_text != null) {
                    if (!this.exit && !this.spectator) {
                        if (recompro) {
                            // Outcome feedback: they rebought — skull off,
                            // sunglasses on.
                            setActionTextFitted(Translator.translate("rebuy.recompra_4"));
                            setPlayerActionIcon("action/glasses.png");
                        } else {
                            setActionTextFitted(rebuy_countdown_saved_text);
                        }
                        repaint();
                    }
                    rebuy_countdown_saved_text = null;
                }
            }
        });
    }

    // Shows ONLY the rebuy outcome (REBOUGHT with sunglasses) without ever having launched the
    // countdown. Used when the LOCAL player was also busted: in that case recibirRebuys runs
    // AFTER their game-over modal and a remote countdown GIF would come out desynced, so it's
    // never launched — just reflecting the result is enough. The "didn't rebuy" case is painted
    // by setSpectator.
    public void showRebuyOutcome(boolean recompro) {
        Helpers.GUIRun(() -> {
            if (recompro && !this.exit && !this.spectator) {
                setActionTextFitted(Translator.translate("rebuy.recompra_4"));
                setPlayerActionIcon("action/glasses.png");
                repaint();
            }
        });
    }

    // Game-over GIF over the cards while this busted player decides on a rebuy (only launched by
    // setRebuying with the GAME OVER cinematic on). The countdown GIF plays once in full, driven
    // by its frames (no clock) and with its own audio (only the FIRST busted player in the group
    // hooks it); once it ends, the zero GIF stays fixed until setRebuying(false) removes it
    // (REBUY received, exit, or crupier timeout). Scaled/centered like the chat notifications.
    // URLs are cache-busted with a unique fragment: the Toolkit caches Image by URL, and two
    // simultaneous busted players would share the animation and step on each other's frame
    // counters. 'gen' invalidates the show/swap if the rebuy resolved in the meantime.
    private void mostrarRebuyGameOverGif(int gen) {
        Helpers.threadRun(() -> {
            try {
                URL countdown_url = getClass().getResource("/cinematics/misc/game_over.gif");
                ImageIcon gif = new ImageIcon(new URL(countdown_url.toString() + "#" + String.valueOf(System.nanoTime())));
                while (gif.getIconHeight() == 0 || gif.getIconWidth() == 0) {
                    Helpers.pausar(GUI_RENDER_WAIT);
                }

                int max_width = panel_cartas.getWidth();
                int new_height = panel_cartas.getHeight();
                int new_width = (int) Math.round((gif.getIconWidth() * new_height) / gif.getIconHeight());
                if (new_width > max_width) {
                    new_height = (int) Math.round((new_height * max_width) / new_width);
                    new_width = max_width;
                }

                final int width = new_width;
                final int height = new_height;
                final int frames = Helpers.getGIFFramesCount(countdown_url);
                final CyclicBarrier barrier = new CyclicBarrier(2);

                Helpers.GUIRun(() -> {
                    if (gen != rebuy_generation) {
                        return;
                    }
                    rebuy_gif_label.setBarrier(barrier);
                    rebuy_gif_label.setIcon(gif, frames);
                    rebuy_gif_label.setRepeat(1);
                    // Audio is hooked AFTER setIcon (setIcon resets it); end_frame -1 = the wav
                    // plays in full and setRebuying(false) cuts it off if the rebuy resolves
                    // first. Only for the first in the group: ONE audio even with several GIFs.
                    // No risk of it doubling with the local player's own game-over sound: if the
                    // LOCAL player also busted, their modal dialog runs first and these remote
                    // GIFs never even launch (recibirRebuys skips them via skip_countdown and
                    // just reflects the outcome).
                    if (REBUY_GIF_ACTIVOS == 0) {
                        rebuy_gif_label.addAudio(GameFrame.finPartidaSonidoOn() ? "misc/game_over.wav" : null, 1, -1);
                    }
                    REBUY_GIF_ACTIVOS++;
                    rebuy_gif_label.setSize(width, height);
                    rebuy_gif_label.setPreferredSize(rebuy_gif_label.getSize());
                    rebuy_gif_label.setOpaque(false);
                    rebuy_gif_label.setLocation(Math.round((panel_cartas.getWidth() - width) / 2), Math.round((getHoleCard1().getHeight() - height) / 2));
                    rebuy_gif_label.setVisible(true);
                });

                // GifLabel trips the barrier on completing the GIF's single pass; generous
                // defensive cap in case the resource never gets to animate (the gen-check below
                // aborts the swap if it's no longer relevant).
                try {
                    barrier.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                }

                URL zero_url = getClass().getResource("/cinematics/misc/game_over_zero.gif");
                ImageIcon zero = new ImageIcon(new URL(zero_url.toString() + "#" + String.valueOf(System.nanoTime())));
                while (zero.getIconHeight() == 0 || zero.getIconWidth() == 0) {
                    Helpers.pausar(GUI_RENDER_WAIT);
                }
                final int zero_frames = Helpers.getGIFFramesCount(zero_url);

                Helpers.GUIRun(() -> {
                    if (gen != rebuy_generation || !rebuy_gif_label.isVisible()) {
                        return;
                    }
                    // FIXED zero: one pass and GifLabel stops requesting frames (freezes on the
                    // last one); removed by setRebuying(false).
                    rebuy_gif_label.setBarrier(null);
                    rebuy_gif_label.setIcon(zero, zero_frames);
                    rebuy_gif_label.setRepeat(1);
                });

            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    /**
     * Shows the shuffle GIF (MUTE, looping) + white highlight border on this player. Invoked by
     * GameFrame's controller from its serializer thread (NOT the EDT), which guarantees one
     * overlay at a time and its minimum duration. Loads the GIF SYNCHRONOUSLY (hence must NOT be
     * called from the EDT) and then paints on the EDT.
     */
    @Override
    public void showShuffleCascadeOverlay() {
        final ImageIcon icon;
        try {
            icon = ensureShuffleCascadeIcon();
        } catch (Exception ex) {
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        if (icon == null) {
            return;
        }
        final int frames = shuffle_cascade_frames;
        if (frames <= 0) {
            return; // GIF with no Graphic Control Extension (deck mod): the imageUpdate loop wouldn't stop on hide
        }
        Helpers.GUIRun(() -> {
            int max_width = panel_cartas.getWidth();
            int new_height = panel_cartas.getHeight();
            if (icon.getIconHeight() <= 0 || new_height <= 0) {
                return;
            }
            // GifLabel stretches the Image to the bounds via GPU, so the label's size is enough.
            int new_width = (int) Math.round((icon.getIconWidth() * (double) new_height) / icon.getIconHeight());
            if (max_width > 0 && new_width > max_width) {
                new_height = (int) Math.round(((double) new_height * max_width) / new_width);
                new_width = max_width;
            }
            shuffle_cascade_gif_label.setBarrier(null);
            shuffle_cascade_gif_label.setIcon(icon, frames);
            shuffle_cascade_gif_label.setRepeat(Integer.MAX_VALUE); // loops until hideShuffleCascadeOverlay
            shuffle_cascade_gif_label.setSize(new_width, new_height);
            shuffle_cascade_gif_label.setPreferredSize(shuffle_cascade_gif_label.getSize());
            shuffle_cascade_gif_label.setOpaque(false);
            shuffle_cascade_gif_label.setLocation(Math.round((panel_cartas.getWidth() - new_width) / 2f), Math.round((getHoleCard1().getHeight() - new_height) / 2f));
            shuffle_cascade_gif_label.setVisible(true);
            // White turn-highlight border (saves the previous color to restore it on hide).
            if (!shuffle_border_active) {
                shuffle_border_saved = border_color;
                shuffle_border_active = true;
            }
            border_color = java.awt.Color.WHITE;
            repaint();
        });
    }

    /**
     * Hides the shuffle overlay and restores the previous border. Idempotent: safe even if no
     * overlay is visible. setIcon(null) resets the GifLabel's repeat count to 1 (stops the loop).
     */
    @Override
    public void hideShuffleCascadeOverlay() {
        Helpers.GUIRun(() -> {
            shuffle_cascade_gif_label.setVisible(false);
            shuffle_cascade_gif_label.setIcon((javax.swing.Icon) null);
            if (shuffle_border_active) {
                // Only restore if the border is still the white we set: if other code changed it
                // in the meantime (e.g. the betting-turn highlight), respect it.
                if (border_color == java.awt.Color.WHITE) {
                    border_color = shuffle_border_saved;
                    repaint();
                }
                shuffle_border_active = false;
            }
        });
    }

    /**
     * Decodes (once per instance, cache-busted) the CURRENT deck's shuffle.gif ImageIcon and
     * counts its frames; null if there's no shuffle GIF or it never got dimensioned. Reloaded if
     * the deck changes. Blocks the (background) thread until the Image reports a size, with a
     * hard 3s cap. Cache-busted with a unique fragment: the Toolkit caches Image by URL for the
     * JVM's whole lifetime, and sharing the shuffle central_label's would step on its frame
     * counters.
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
            Helpers.pausar(GUI_RENDER_WAIT);
        }
        if (icon.getIconHeight() == 0 || icon.getIconWidth() == 0) {
            return null;
        }
        shuffle_cascade_frames = Helpers.getGIFFramesCount(url);
        shuffle_cascade_icon = icon;
        shuffle_cascade_icon_url = url_key;
        return icon;
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

    /**
     * Sets {@code msg} on the action label, auto-shrinking the font (measured
     * with FontMetrics) so a long hand name fits the label width, and restoring
     * the original size when it fits again. Must run on the EDT.
     */
    private void setActionTextFitted(String msg) {
        // Any NORMAL action text (CALL/RAISE/thinking/leaving/reset...) invalidates the all-in
        // %-roll: the next % will jump instead of rolling from a value that no longer applies
        // (e.g. a previous all-in's). The roll itself and "(--%)" use setActionTextFittedRaw
        // so they do NOT self-invalidate.
        if (jugada_prob_roller != null) {
            jugada_prob_roller.invalidate();
        }
        setActionTextFittedRaw(msg);
    }

    private void setActionTextFittedRaw(String msg) {

        Font base_font = (orig_action_font != null) ? orig_action_font : player_action.getFont();

        Insets insets = player_action.getInsets();

        int available_width = (player_action.getWidth() > 0 ? player_action.getWidth() : player_action.getPreferredSize().width) - (insets != null ? insets.left + insets.right : 0);

        // JLabel lays the icon and the text out side by side. The old calculation
        // measured the text against the whole label, so adding the winner/loser
        // icon after fitting could still clip the end of a long hand name.
        javax.swing.Icon icon = player_action.getIcon();
        if (icon != null && msg != null && !msg.isEmpty()) {
            available_width -= icon.getIconWidth() + player_action.getIconTextGap();
        }

        Font fitted_font = Helpers.fitFontToWidth(player_action, msg, base_font, available_width, Math.max(9, Math.round(base_font.getSize() * 0.5f)));

        if (fitted_font.getSize() < base_font.getSize()) {
            orig_action_font = base_font;
            player_action.setFont(fitted_font);

        } else if (orig_action_font != null) {
            player_action.setFont(orig_action_font);
            orig_action_font = null;
        }

        player_action.setText(msg);
    }

    // Live roll of the all-in win-probability % on the action label (HAND + PROB). The number
    // rolls at constant speed while keeping the hand name as a prefix; the renderer rebuilds
    // "HAND (NN%)" via setActionTextFittedRaw (so it doesn't self-invalidate) and runs it
    // through the font auto-fit. EDT-only (lazily created).
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
                // option (isCounterRollEnabled; skipped on recover). Goes through the roller ->
                // rendered with setActionTextFittedRaw (doesn't self-invalidate).
                boolean animate = GameFrame.isCounterRollEnabled();
                RollingCounter roller = jugadaProbRoller();
                // First all-in reveal: the roller has no value yet (the previous action
                // invalidated it), so roll() would jump straight to the value on ONLY the first
                // street and animate the rest. Seed it at 0 so it rolls 0->% over the same fixed
                // duration, so EVERY street takes the same time.
                if (animate && !roller.isValid()) {
                    roller.set(0);
                }
                roller.roll(win_per, animate);
            } else {
                // Still no simulation yet: raw "(--%)" (without invalidating) so the roller's
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
