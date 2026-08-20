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

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * Base Swing panel for a poker table's felt: owns the felt background painting,
 * table-wide animation overlays (central label, card flights, chip flights,
 * call-cost overlays) and zoom/resize handling shared by every table layout.
 *
 * @author tonikelope
 */
public abstract class TablePanel extends javax.swing.JLayeredPane implements ZoomableInterface {

    protected volatile TexturePaint tp = null;

    // Single-image felt (suffix "*"): a large JPG stretched over the whole table,
    // kept unscaled and painted with drawImage(...,0,0,w,h) each frame (see
    // paintComponent). NOT a TexturePaint: a panel-sized tile renders a partial
    // repaint strip differently from a full repaint (visible seams where flying
    // chips/cards cross), whereas drawImage always maps the whole source to
    // (0,0)-(w,h) regardless of the clip.
    protected volatile BufferedImage secret_bg = null;

    protected volatile RemotePlayer[] remotePlayers;

    protected volatile Player[] players;

    protected volatile ZoomableInterface[] zoomables;

    protected final CyclicBarrier central_label_barrier = new CyclicBarrier(2);

    protected final GifLabel central_label = new GifLabel();

    // "SHUFFLING" fallback label shown centered where the shuffle GIF would play when
    // that GIF isn't available (deck has no shuffle.gif, or animations are off); same
    // style as the end-of-hand banner (white fill, black outline). Own layer above the
    // table, visible only during a GIF-less shuffle.
    protected final ShufflingTextLabel shuffling_label = new ShufflingTextLabel();

    // Optional overlay over the community cards showing the local player's call cost
    // in large text (semi-transparent black fill + white halo, readable over any
    // background). Updated live as bets rise.
    protected final CallCostOverlayLabel call_cost_label = new CallCostOverlayLabel();

    // Per-player call-cost overlays for the RIVER round: once no community card is
    // left face down, the call cost is shown over each in-pot RemotePlayer's face-down
    // hole cards instead (never over the local player, who sees their own cards) — it's
    // effectively the cost of "revealing" the rival hand at showdown. Live in DRAG_LAYER,
    // EDT-only. Keyed by RemotePlayer (stable per-seat object).
    private final java.util.Map<RemotePlayer, CallCostOverlayLabel> player_call_cost_labels = new java.util.HashMap<>();
    private final java.util.Set<RemotePlayer> player_call_overlay_listeners = new java.util.HashSet<>();

    // All pot-bound chip flights currently in progress, advanced by ONE shared EDT timer
    // (pot_chip_timer -> tickPotChipFlights) instead of one Timer per chip. A batch (antes,
    // simultaneous all-ins) used to spawn up to ~9 independent 2 ms timers; this collapses them
    // to one. EDT-only (registered from flyChipToPot's EDT setup, drained by the shared timer).
    private final java.util.List<PotChipFlight> pot_chip_flights = new java.util.ArrayList<>();
    private javax.swing.Timer pot_chip_timer;

    protected final TapeteFastButtons fastbuttons = new TapeteFastButtons();

    protected volatile Long central_label_thread = null;

    protected volatile boolean invalidate = false;

    protected final Object paint_lock = new Object();

    // Auto-fit is launched from resize, fullscreen and zoom paths. Coalesce overlapping requests
    // without holding a monitor across GUIRunAndWait: the zoom itself already serializes its
    // worker phases, while this flag only prevents duplicate auto-fit workers.
    private final AtomicBoolean auto_zoom_running = new AtomicBoolean(false);

    public RemotePlayer[] getRemotePlayers() {
        return remotePlayers;
    }

    public Player[] getPlayers() {
        return players;
    }

    abstract public CommunityCardsPanel getCommunityCards();

    abstract public LocalPlayer getLocalPlayer();

    /**
     * Loads the felt background (image or tiled texture) and wires up the
     * table's fixed overlays (central label, shuffling label, call-cost
     * overlay, fast buttons).
     */
    public TablePanel() {

        if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {

            // Single-image felt: see the secret_bg field doc above for why this
            // uses drawImage instead of TexturePaint.
            try {
                secret_bg = Helpers.toBufferedImage(Init.I1);

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            BufferedImage tile = null;
            // try-with-resources: ImageIO.read(InputStream) does NOT close the stream
            // (JDK contract), so the JAR resource handle used to leak until GC. Same
            // fix applied to the live felt-swap path in paintComponent below.
            try (java.io.InputStream is = getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg")) {

                tile = ImageIO.read(is);

            } catch (Exception ex) {

                try (java.io.InputStream isf = getClass().getResourceAsStream("/images/tapete_verde.jpg")) {
                    tile = ImageIO.read(isf);
                } catch (IOException ex1) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

            Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
            tp = new TexturePaint(tile, tr);
        }

        Helpers.GUIRunAndWait(() -> {
            initComponents();
            add(fastbuttons, JLayeredPane.POPUP_LAYER);
            fastbuttons.setSize(fastbuttons.getPref_size());
            central_label.setFocusable(false);
            central_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            central_label.setBarrier(central_label_barrier);
            add(central_label, JLayeredPane.POPUP_LAYER);

            // "SHUFFLING" fallback label: same layer as the central GIF, hidden except
            // during a GIF-less shuffle.
            shuffling_label.setFocusable(false);
            shuffling_label.setVisible(false);
            add(shuffling_label, JLayeredPane.POPUP_LAYER);

            // Call-cost overlay: above the community cards (PALETTE layer, below the
            // shuffle/flying layers). Paints its own centered halo text, so no
            // alignment/foreground needed here.
            call_cost_label.setFocusable(false);
            call_cost_label.setOpaque(false);
            call_cost_label.setVisible(false);
            add(call_cost_label, JLayeredPane.PALETTE_LAYER);

            // Double left-click on an empty felt area cycles to the next felt (same order
            // as the menu). Hooked on mouseReleased + isRealClick, this app's reliable
            // pattern (mouseClicked is lost if the mouse drifts slightly between press and
            // release). No collision with the right-click context menu.
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseReleased(java.awt.event.MouseEvent evt) {
                    if (evt.getClickCount() == 2 && Helpers.isRealClick(evt)) {
                        cycleNextTapete();
                    }
                }
            });
            addComponentListener(new ComponentResizeEndListener() {
                @Override
                public void resizeTimedOut() {
                    if (GameFrame.AUTO_ZOOM) {
                        Helpers.threadRun(() -> {
                            autoZoom(false);
                        });
                    }
                    if (GameFrame.COLOR_TAPETE.endsWith("*")) {
                        invalidate = true;

                        revalidate();
                        repaint();

                    }

                    fastbuttons.setLocation(0, (int) (getHeight() - fastbuttons.getSize().getHeight()));

                    // The call-cost overlay is absolutely positioned: after a
                    // resize/zoom it must be relaid over the community cards (or the
                    // rivals' hole cards, on the river round).
                    if (call_cost_label.isVisible()) {
                        layoutCallCostOverlay();
                        call_cost_label.repaint();
                    }
                    relayoutPlayerCallCostOverlaysIfVisible();

                    if (GameFrame.getInstance() != null && GameFrame.getInstance().isFull_screen()) {
                        GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                    }

                }
            });
        });
    }

    // Cycles to the next felt in menu order (green -> blue -> red -> black -> wood ->
    // green) by delegating to the matching menu item, which sets COLOR_TAPETE, persists
    // the preference, repaints the felt and syncs both menus. A secret variant (suffix
    // "*") has its suffix stripped so cycling still lands on the next normal color.
    private void cycleNextTapete() {
        GameFrame gf = GameFrame.getInstance();

        if (gf == null) {
            return;
        }

        String current = GameFrame.COLOR_TAPETE;

        if (current.endsWith("*")) {
            current = current.substring(0, current.length() - 1);
        }

        String[] order = {"verde", "azul", "rojo", "negro", "madera"};
        int idx = 0;

        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(current)) {
                idx = i;
                break;
            }
        }

        String next = order[(idx + 1) % order.length];
        javax.swing.AbstractButton item;

        switch (next) {
            case "azul":
                item = gf.getMenu_tapete_azul();
                break;
            case "rojo":
                item = gf.getMenu_tapete_rojo();
                break;
            case "negro":
                item = gf.getMenu_tapete_negro();
                break;
            case "madera":
                item = gf.getMenu_tapete_madera();
                break;
            default:
                item = gf.getMenu_tapete_verde();
                break;
        }

        if (item != null) {
            item.doClick();
        }
    }

    /**
     * Shows an animated GIF icon centered (or at its current location) in the
     * central label, optionally playing an audio clip in sync, and blocks the
     * caller until the animation and {@code delay_end} pause finish.
     */
    public void showCentralImage(ImageIcon icon, int frames, int delay_end, boolean center, String audio, int audio_frame_start, int audio_frame_end) {
        central_label_thread = Thread.currentThread().getId();

        try {
            central_label_barrier.reset();
        } catch (Exception ex) {
            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
        }

        Helpers.GUIRunAndWait(() -> {
            getCentral_label().setSize(icon.getIconWidth(), icon.getIconHeight());

            if (center) {
                int pos_x = Math.round((getWidth() - icon.getIconWidth()) / 2);
                int pos_y = Math.round((getHeight() - icon.getIconHeight()) / 2);
                getCentral_label().setLocation(pos_x, pos_y);
            }

            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                icon.getImage().flush();
                getCentral_label().setIcon(icon, frames);
                getCentral_label().addAudio(audio, audio_frame_start, audio_frame_end);
                getCentral_label().setVisible(true);

                getCentral_label().revalidate();
                getCentral_label().repaint();
            }
        });
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().getId() == central_label_thread) {
            try {
                central_label_barrier.await();
            } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                        "central label barrier", ex);
            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (delay_end > 0) {
                Helpers.parkThreadMillis(delay_end);
            }
            if (Thread.currentThread().getId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setVisible(false);
                });
            }
        }
    }

    /**
     * Plays a pre-decoded GIF on the central label using clock-based (catch-up)
     * frame indexing: the visible frame is chosen from elapsed nanoTime, so
     * total duration always matches the GIF's nominal length even if timer
     * ticks lag (Windows timer granularity). Same contract as
     * {@link #showCentralImage}: blocks the caller until playback ends,
     * respecting {@code fin_de_la_transmision} and central-label takeover.
     */
    public void showCentralFrames(PreRenderedGif anim, int display_w, int display_h, int delay_end, String audio) {

        showCentralFrames(anim, display_w, display_h, delay_end, audio, null, null);
    }

    /**
     * Same as {@link #showCentralFrames(PreRenderedGif, int, int, int, String)}
     * but with two hooks for gap-free handoffs. {@code on_show} runs in the
     * SAME EDT event that paints the first frame, so the caller can hide the
     * face-down card underneath in that single paint (doing it in a separate
     * EDT event let the empty gap flash through intermittently).
     * {@code before_hide} runs on the caller's thread after the last frame and
     * BEFORE the {@code delay_end} pause, only if this thread still owns the
     * label: the caller can reveal the static card there while the GIF still
     * shows its last frame, so the GIF-to-card handoff never paints an empty
     * gap either.
     */
    public void showCentralFrames(PreRenderedGif anim, int display_w, int display_h, int delay_end, String audio, Runnable on_show, Runnable before_hide) {

        central_label_thread = Thread.currentThread().getId();

        final CountDownLatch finished = new CountDownLatch(1);

        final javax.swing.Timer[] player_holder = new javax.swing.Timer[1];

        Helpers.GUIRunAndWait(() -> {

            getCentral_label().setSize(display_w, display_h);

            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

                getCentral_label().setIcon(null);
                getCentral_label().setFrameOverride(anim.getFrame(0));
                getCentral_label().setVisible(true);

                if (on_show != null) {
                    on_show.run();
                }

                if (audio != null) {
                    Audio.playWavResource(audio);
                }

                final long t0 = System.nanoTime();
                final int last_frame = anim.getFrameCount() - 1;
                final int[] painted = {0};

                final javax.swing.Timer player = new javax.swing.Timer(GameFrame.getTickMs(), null);

                player_holder[0] = player;

                player.addActionListener(e -> {

                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;

                    int idx = anim.frameAt(elapsed);

                    if (idx != painted[0]) {
                        painted[0] = idx;
                        getCentral_label().setFrameOverride(anim.getFrame(idx));
                    }

                    if (idx == last_frame || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        finished.countDown();
                    }
                });

                player.start();

            } else {
                finished.countDown();
            }
        });

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().getId() == central_label_thread) {

            try {
                finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                        "central label pre-rendered playback", ex);
            }

            // Runs before the intentional pause: whatever the caller does here (reveal
            // the static card) is covered by the last frame during delay_end and doesn't
            // extend the animation. Guarded so a hook failure can never leave the label
            // stuck visible forever.
            if (before_hide != null && Thread.currentThread().getId() == central_label_thread) {
                try {
                    before_hide.run();
                } catch (Exception ex) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            if (delay_end > 0) {
                Helpers.parkThreadMillis(delay_end);
            }

            if (Thread.currentThread().getId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setFrameOverride(null);
                    getCentral_label().setVisible(false);
                });
            }
        }

        // Belt and suspenders: stop THIS player no matter how the wait ended (latch
        // timeout, end of transmission, takeover). The Timer already stops itself on
        // the last frame; this only closes the exotic paths without touching a new
        // label owner's player.
        Helpers.GUIRun(() -> {
            if (player_holder[0] != null) {
                player_holder[0].stop();
            }
        });
    }

    /**
     * Plays the flip GIFs of one or more cards AT THE SAME TIME on ephemeral
     * overlays in POPUP_LAYER (one per card, centered over it), using the same
     * catch-up engine and gap-free handoffs as {@link #showCentralFrames}: each
     * face-down card is hidden in the SAME EDT event that shows its overlay's
     * first frame, and each card is revealed synchronously under its overlay's
     * last frame before the overlays are removed. Blocks the caller (NEVER call
     * from the EDT) until all animations finish plus {@code delay_end}; for
     * sequential reveals (one card fully landed before the next flips) the
     * caller chains single-card calls. Independent of {@code central_label} and
     * its takeover — overlays are created and torn down here only.
     */
    public void playCardFlipOverlays(Card[] cartas, PreRenderedGif[] anims, int[] dws, int[] dhs, int delay_end, String audio) {

        final GifLabel[] overlays = new GifLabel[cartas.length];

        final CountDownLatch finished = new CountDownLatch(1);

        final javax.swing.Timer[] player_holder = new javax.swing.Timer[1];

        Helpers.GUIRunAndWait(() -> {

            try {
                if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

                    for (int i = 0; i < cartas.length; i++) {

                        GifLabel overlay = new GifLabel();
                        overlay.setFocusable(false);
                        overlay.setSize(dws[i], dhs[i]);

                        int x = (int) ((int) ((cartas[i].getLocationOnScreen().getX() + Math.round(cartas[i].getWidth() / 2))
                                - Math.round(dws[i] / 2))
                                - getLocationOnScreen().getX());

                        int y = (int) ((int) ((cartas[i].getLocationOnScreen().getY() + Math.round(cartas[i].getHeight() / 2))
                                - Math.round(dhs[i] / 2))
                                - getLocationOnScreen().getY());

                        overlay.setLocation(x, y);
                        overlay.setFrameOverride(anims[i].getFrame(0));

                        add(overlay, JLayeredPane.POPUP_LAYER);

                        overlay.setVisible(true);

                        overlays[i] = overlay;

                        // Same EDT event that shows the first frame: the card-to-GIF
                        // handoff paints as a single unit.
                        cartas[i].setVisibleCard(false);
                    }

                    if (audio != null) {
                        // Clip pre-opened and reused (uncover.wav is preloaded at
                        // startup): the flip sound starts instantly in sync with the
                        // overlay's first frame, with no late per-flip line open. Off-EDT
                        // because playPreloadedWav may resolve a lazy preload.
                        Helpers.threadRun(() -> Audio.playPreloadedWav(audio));
                    }

                    final long t0 = System.nanoTime();
                    final int[] painted = new int[cartas.length];

                    final javax.swing.Timer player = new javax.swing.Timer(GameFrame.getTickMs(), null);

                    player_holder[0] = player;

                    player.addActionListener(e -> {

                        long elapsed = (System.nanoTime() - t0) / 1_000_000L;

                        boolean all_done = true;

                        for (int i = 0; i < anims.length; i++) {

                            int idx = anims[i].frameAt(elapsed);

                            if (idx != painted[i]) {
                                painted[i] = idx;
                                overlays[i].setFrameOverride(anims[i].getFrame(idx));
                            }

                            all_done = all_done && (idx == anims[i].getFrameCount() - 1);
                        }

                        if (all_done || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                            player.stop();
                            finished.countDown();
                        }
                    });

                    player.start();

                } else {
                    finished.countDown();
                }
            } catch (Exception ex) {
                // E.g. IllegalComponentStateException if a card just stopped being on
                // screen: clean up what was added and release the caller, who reveals
                // the cards outright via destaparSync below.
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                for (GifLabel overlay : overlays) {
                    if (overlay != null) {
                        remove(overlay);
                    }
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "card flip overlays playback", ex);
        }

        // Same as showCentralFrames: the synchronous reveal happens covered by the
        // overlay's last frame during delay_end, so the GIF-to-card handoff never
        // paints an empty gap.
        for (Card carta : cartas) {
            carta.destaparSync();
        }

        if (delay_end > 0) {
            Helpers.parkThreadMillis(delay_end);
        }

        Helpers.GUIRunAndWait(() -> {
            for (GifLabel overlay : overlays) {
                if (overlay != null) {
                    overlay.setVisible(false);
                    remove(overlay);
                }
            }
            revalidate();
            repaint();
        });

        // Belt and suspenders (same pattern as showCentralFrames): stop the timer no
        // matter how the wait ended.
        Helpers.GUIRun(() -> {
            if (player_holder[0] != null) {
                player_holder[0].stop();
            }
        });
    }

    /**
     * Deal animation: a face-down card travels from the hand's dealer seat
     * (anchor {@code origin}) to {@code target}, rotated to the
     * origin-to-target angle, along a smooth easeOut arc (fast start, gentle
     * landing). If {@code origin} is null it starts from the table center
     * (back-compat). On landing runs {@code onLand} (which seats the face-down
     * card) and, after a brief handoff dwell, removes the traveling card with
     * no gap: it shows the same back image (Card.getBackImage) and lands
     * straight and centered on the seat, so the handoff is pixel-identical.
     * <p>
     * Geometry-agnostic: reads target's (and origin's) real on-screen position,
     * so it works for all 9 tables under any zoom/HiDPI. Blocks the caller
     * (crupier thread, NEVER the EDT) until landing + dwell. If the animation
     * can't run (no back image, target off-screen, end of transmission) runs
     * {@code onLand} immediately and returns.
     */
    public void flyCardToSeat(final Card target, final Card origin, final int duration_ms, final String audio, final Runnable onLand) {

        // --- Animation tuning knobs ---
        // Offset (rad) added to the origin-to-target angle (0 = card aligns with the
        // travel direction; +PI/2 aligns the long axis with the path).
        final double ROT_OFFSET = 0.0;
        // Straightens to vertical at the end of the flight to match the seated card
        // (which sits straight) so the handoff has no rotation pop. Set to false to
        // land rotated.
        final boolean STRAIGHTEN_ON_LAND = true;
        // Constant speed: duration is derived from travel distance measured in CARD
        // HEIGHTS (zoom-invariant), so every card travels at the same visual speed
        // regardless of seat (looks like the crupier throws them all with equal force).
        // With false, duration_ms is used instead (fixed duration per card, legacy
        // behavior).
        final boolean CONSTANT_SPEED = false;
        final double MS_PER_CARDHEIGHT = 120.0; // ms per card-height of distance
        final int SPEED_MIN_MS = 120;
        final int SPEED_MAX_MS = 320;

        final ImageIcon back = Card.getBackImage();

        if (target == null || back == null || back.getIconWidth() <= 0
                || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            if (onLand != null) {
                Helpers.GUIRunAndWait(onLand);
            }
            return;
        }

        final int dw = back.getIconWidth();
        final int dh = back.getIconHeight();
        // Square that contains the card at ANY angle (its diagonal), so the traveling
        // component never needs resizing while it rotates.
        final int box = (int) Math.ceil(Math.hypot(dw, dh));

        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final FlyingCard[] travelerHolder = new FlyingCard[1];

        Helpers.GUIRunAndWait(() -> {
            try {
                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    if (onLand != null) {
                        onLand.run();
                    }
                    finished.countDown();
                    return;
                }

                // Centers in the table's local coordinates.
                final double toCx = target.getLocationOnScreen().getX() + target.getWidth() / 2.0 - getLocationOnScreen().getX();
                final double toCy = target.getLocationOnScreen().getY() + target.getHeight() / 2.0 - getLocationOnScreen().getY();
                // Origin: the dealer seat (anchor card); if not given or off-screen
                // (e.g. dealer left), falls back to the table center (legacy behavior).
                final double fromCx, fromCy;
                if (origin != null && origin.isShowing()) {
                    fromCx = origin.getLocationOnScreen().getX() + origin.getWidth() / 2.0 - getLocationOnScreen().getX();
                    fromCy = origin.getLocationOnScreen().getY() + origin.getHeight() / 2.0 - getLocationOnScreen().getY();
                } else {
                    fromCx = getWidth() / 2.0;
                    fromCy = getHeight() / 2.0;
                }

                final double theta = Math.atan2(toCy - fromCy, toCx - fromCx) + ROT_OFFSET;

                // Arc control point: path midpoint offset perpendicular, with a
                // clamped height.
                final double mx = (fromCx + toCx) / 2.0, my = (fromCy + toCy) / 2.0;
                final double vx = toCx - fromCx, vy = toCy - fromCy;
                final double len = Math.hypot(vx, vy);
                final double arc = Math.min(len * 0.16, dh);
                final double nx = (len > 1) ? -vy / len : 0.0;
                final double ny = (len > 1) ? vx / len : 0.0;
                final double ctrlX = mx + nx * arc;
                final double ctrlY = my + ny * arc;

                // Effective duration: with constant speed, proportional to distance in
                // card-heights (clamped); otherwise the passed-in value.
                final int eff_dur = CONSTANT_SPEED
                        ? (int) Math.round(Math.max(SPEED_MIN_MS,
                                Math.min(SPEED_MAX_MS, (len / dh) * MS_PER_CARDHEIGHT)))
                        : duration_ms;

                if (audio != null) {
                    Audio.playWavResource(audio, false);
                }

                final FlyingCard traveler = new FlyingCard(back.getImage(), dw, dh);
                traveler.setSize(box, box);
                traveler.setAngle(theta);
                traveler.setCenter(fromCx, fromCy);
                add(traveler, JLayeredPane.DRAG_LAYER);
                travelerHolder[0] = traveler;

                final long t0 = System.nanoTime();
                // Change-gate: at the 2 ms tick many steps round to the same pixel position (and,
                // near the end, the same straightened angle), so the traveler wouldn't paint any
                // differently. setCenter/setLocation is already a Swing no-op when unchanged; only
                // repaint() is costly (it recomposites the felt under a transparent sprite), so we
                // gate exactly that. Skipped only when neither position nor (quality-mode) angle
                // moved -> byte-identical frame.
                final double[] last_angle = {Double.NaN};

                final javax.swing.Timer player = new javax.swing.Timer(GameFrame.getTickMs(), null);
                holder[0] = player;

                player.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, eff_dur));

                    // Quadratic easeOut for position.
                    double s = 1.0 - (1.0 - u) * (1.0 - u);
                    double is = 1.0 - s;
                    double x = is * is * fromCx + 2 * is * s * ctrlX + s * s * toCx;
                    double y = is * is * fromCy + 2 * is * s * ctrlY + s * s * toCy;

                    // Straightening: holds theta for most of the flight and eases to 0
                    // at the end (smoothstep 0.55->1) to land straight like the seated
                    // card (no rotation pop on handoff).
                    double st = STRAIGHTEN_ON_LAND ? smoothstep(0.55, 1.0, u) : 0.0;
                    double na = theta * (1.0 - st);
                    traveler.setAngle(na);
                    int bx = traveler.getX();
                    int by = traveler.getY();
                    traveler.setCenter(x, y);
                    boolean moved = traveler.getX() != bx || traveler.getY() != by;
                    boolean rotated = GameFrame.ANIM_CALIDAD && Double.compare(na, last_angle[0]) != 0;
                    if (moved || rotated) {
                        last_angle[0] = na;
                        traveler.repaint();
                    }

                    if (u >= 1.0 || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        if (onLand != null) {
                            // Seats the card (async refresh); the traveling card stays
                            // on top showing the same back image -> no visible gap.
                            onLand.run();
                        }
                        finished.countDown();
                    }
                });

                player.start();

            } catch (Exception ex) {
                // E.g. IllegalComponentStateException if target just stopped being on
                // screen: clean up and seat the card outright.
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                if (travelerHolder[0] != null) {
                    remove(travelerHolder[0]);
                }
                if (onLand != null) {
                    onLand.run();
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "deal flying card", ex);
        }

        // Handoff dwell: the seated card is painted async underneath the traveling one
        // (same back image, same position), so removing it later is a clean handoff.
        // Not a defensive timeout — it's the deliberate gap-free overlap.
        Helpers.parkThreadMillis(40);

        final FlyingCard traveler = travelerHolder[0];
        Helpers.GUIRunAndWait(() -> {
            if (traveler != null) {
                java.awt.Rectangle b = traveler.getBounds();
                remove(traveler);
                repaint(b);
            }
        });

        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    /**
     * Animates swapping the local player's two hole cards when reordering the
     * hand: each card slides to the other's position (crossing, with opposing
     * arcs so the swap is visible without overlapping). On completion applies
     * the logical swap ({@code onSwapApply}) while the static cards are still
     * hidden under the overlays, then removes the overlays with no gap. BLOCKS
     * the caller until done, so it must be invoked from a background thread
     * (Helpers.threadRun), NEVER the EDT or the crupier thread (hand ordering
     * is purely visual, nothing needs to wait on it). Geometry-agnostic (reads
     * real on-screen positions; works for all 9 tables under zoom/HiDPI). If
     * the animation can't run (end of transmission, off-screen cards, no image)
     * runs {@code onSwapApply} immediately and returns.
     *
     * @param chip the local player's large position chip (or null); if visible,
     * it's cloned into a static overlay on a layer ABOVE the swap's flying
     * cards, in the same position, so cards cross UNDER the chip with no
     * flicker (the real chip stays put; this overlay just keeps it on top).
     */
    public void playHoleCardSwap(final Card left, final Card right, final int duration_ms, final boolean arc, final javax.swing.JLabel chip, final Runnable onSwapApply) {

        final CountDownLatch finished = new CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final FlyingCard[] ovLeft = new FlyingCard[1];
        final FlyingCard[] ovRight = new FlyingCard[1];
        final javax.swing.JLabel[] chipOv = new javax.swing.JLabel[1];
        final boolean[] applied = new boolean[1];

        Helpers.GUIRunAndWait(() -> {
            try {
                final java.awt.Image leftFace = left.getDisplayedImage();
                final java.awt.Image rightFace = right.getDisplayedImage();

                if (leftFace == null || rightFace == null || !left.isShowing() || !right.isShowing()
                        || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    onSwapApply.run();
                    applied[0] = true;
                    finished.countDown();
                    return;
                }

                final int lw = left.getWidth(), lh = left.getHeight();
                final int rw = right.getWidth(), rh = right.getHeight();

                // Centers in the table's local coordinates.
                final double lx = left.getLocationOnScreen().getX() + lw / 2.0 - getLocationOnScreen().getX();
                final double ly = left.getLocationOnScreen().getY() + lh / 2.0 - getLocationOnScreen().getY();
                final double rx = right.getLocationOnScreen().getX() + rw / 2.0 - getLocationOnScreen().getX();
                final double ry = right.getLocationOnScreen().getY() + rh / 2.0 - getLocationOnScreen().getY();

                // Crossing style. With the "hop" (arc=true), opposing vertical arcs: left
                // arches up and right arches down, so they cross without overlapping. In
                // straight mode (arc=false) arc_amt=0: both travel straight and left
                // (POPUP layer) passes in front of right (DRAG layer).
                final double arc_amt = arc ? Math.max(lh, rh) * 0.55 : 0.0;

                // In compact view the static card shows only its TOP HALF (the "mini
                // card" with the index), cropped at native resolution (not scaled). We
                // crop that same top half and give it to the traveling card at half size,
                // so the crossing looks identical to the static card (not squashed or
                // showing a middle band). In normal mode the image is full height:
                // topHalfIfShorter returns it unchanged.
                final java.awt.Image leftDraw = topHalfIfShorter(leftFace, lw, lh);
                final java.awt.Image rightDraw = topHalfIfShorter(rightFace, rw, rh);

                final FlyingCard fl = new FlyingCard(leftDraw, lw, lh);
                fl.setSize(lw, lh);
                fl.setCenter(lx, ly);
                final FlyingCard fr = new FlyingCard(rightDraw, rw, rh);
                fr.setSize(rw, rh);
                fr.setCenter(rx, ry);

                // Left (arcs up) goes in front; right goes behind.
                add(fr, JLayeredPane.DRAG_LAYER);
                add(fl, JLayeredPane.POPUP_LAYER);
                ovLeft[0] = fl;
                ovRight[0] = fr;

                // Large position chip ABOVE the flying cards (DRAG+1 layer): a static
                // overlay with its icon, in the same position, so cards cross underneath
                // without flicker. The real LocalPlayer chip stays put (hidden behind the
                // flying cards during the crossing, but this overlay keeps it visually on
                // top). Not painted if it isn't showing (disabled or no chip role).
                if (chip != null && chip.isShowing() && chip.getIcon() != null) {
                    javax.swing.Icon ci = chip.getIcon();
                    javax.swing.JLabel co = new javax.swing.JLabel(ci);
                    co.setSize(ci.getIconWidth(), ci.getIconHeight());
                    co.setLocation(
                            (int) Math.round(chip.getLocationOnScreen().getX() - getLocationOnScreen().getX()),
                            (int) Math.round(chip.getLocationOnScreen().getY() - getLocationOnScreen().getY()));
                    add(co, Integer.valueOf(JLayeredPane.DRAG_LAYER + 1));
                    chipOv[0] = co;
                }

                // Hides the static cards in the same EDT event that shows the overlays.
                left.setVisibleCard(false);
                right.setVisibleCard(false);

                final long t0 = System.nanoTime();
                final javax.swing.Timer player = new javax.swing.Timer(GameFrame.getTickMs(), null);
                holder[0] = player;

                player.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, duration_ms));
                    // Cubic easeInOut for a crossing with a smooth start and stop.
                    double s = (u < 0.5) ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
                    double bump = Math.sin(Math.PI * s); // 0 at the ends, 1 at the crossing

                    int flx = fl.getX();
                    int fly = fl.getY();
                    int frx = fr.getX();
                    int fry = fr.getY();

                    fl.setCenter(
                            lx + (rx - lx) * s,
                            ly + (ry - ly) * s - arc_amt * bump
                    );

                    fr.setCenter(
                            rx + (lx - rx) * s,
                            ry + (ly - ry) * s + arc_amt * bump
                    );

                    if (fl.getX() != flx || fl.getY() != fly) {
                        fl.repaint();
                    }

                    if (fr.getX() != frx || fr.getY() != fry) {
                        fr.repaint();
                    }

                    if (u >= 1.0 || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        // Logical swap while the static cards are still hidden: left now
                        // shows the high card and right the low one, matching EXACTLY
                        // where the overlays landed (no visual jump on handoff).
                        if (!applied[0]) {
                            onSwapApply.run();
                            applied[0] = true;
                        }
                        finished.countDown();
                    }
                });
                player.start();

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                if (ovLeft[0] != null) {
                    remove(ovLeft[0]);
                }
                if (ovRight[0] != null) {
                    remove(ovRight[0]);
                }
                if (chipOv[0] != null) {
                    remove(chipOv[0]);
                }
                if (!applied[0]) {
                    onSwapApply.run();
                    applied[0] = true;
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "hole card swap", ex);
        }

        // Handoff dwell: the static cards (already swapped) are painted async under the
        // overlays at their final position; removing the overlays is then a clean handoff.
        Helpers.parkThreadMillis(40);

        Helpers.GUIRunAndWait(() -> {
            left.setVisibleCard(true);
            right.setVisibleCard(true);
            if (ovLeft[0] != null) {
                remove(ovLeft[0]);
            }
            if (ovRight[0] != null) {
                remove(ovRight[0]);
            }
            if (chipOv[0] != null) {
                remove(chipOv[0]);
            }
            revalidate();
            repaint();
        });

        // Belt and suspenders: stop the timer no matter how the wait ended.
        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    // If the image is taller than the slot (compact view: slot at 1/2, image at full
    // height), returns a crop of its TOP HALF at native resolution (w x h), matching
    // what the compact static card shows. Otherwise (full height) returns it unchanged.
    // Keeps the swap's traveling card from squashing the whole card into the slot.
    private java.awt.Image topHalfIfShorter(java.awt.Image img, int w, int h) {
        if (img == null || w <= 0 || h <= 0) {
            return img;
        }
        int imgH = img.getHeight(null);
        if (imgH <= 0 || imgH <= h) {
            return img;
        }
        int imgW = img.getWidth(null);
        if (imgW <= 0) {
            imgW = w;
        }
        java.awt.image.BufferedImage cut = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = cut.createGraphics();
        // dest (0,0,w,h) <- src TOP strip (0,0,imgW,h): top half, unscaled vertically
        // (native res); horizontally imgW->w fits exactly (same width).
        g.drawImage(img, 0, 0, w, h, 0, 0, imgW, h, null);
        g.dispose();
        return cut;
    }

    /**
     * Clones the large position chip into a STATIC overlay on the table, on a
     * layer ABOVE the deal's flying cards (DRAG+1) and in the same position, so
     * a flying card passes/lands UNDER the chip without flicker. Does not touch
     * the deal animation ({@link #flyCardToSeat}) itself — it only paints the
     * chip on top; the real chip stays put (hidden by the traveling card on
     * landing, but this overlay keeps it visually on top).
     *
     * @param chip the chip to clone; must still be visible when called
     * @return the overlay to remove via {@link #removeTopOverlay} right before
     * the flip, or null if {@code chip} isn't showing
     */
    public javax.swing.JLabel addChipTopOverlay(final javax.swing.JLabel chip) {
        final javax.swing.JLabel[] out = new javax.swing.JLabel[1];
        Helpers.GUIRunAndWait(() -> {
            if (chip == null || !chip.isShowing() || chip.getIcon() == null) {
                return;
            }
            javax.swing.Icon ci = chip.getIcon();
            javax.swing.JLabel co = new javax.swing.JLabel(ci);
            co.setSize(ci.getIconWidth(), ci.getIconHeight());
            co.setLocation(
                    (int) Math.round(chip.getLocationOnScreen().getX() - getLocationOnScreen().getX()),
                    (int) Math.round(chip.getLocationOnScreen().getY() - getLocationOnScreen().getY()));
            add(co, Integer.valueOf(JLayeredPane.DRAG_LAYER + 1));
            out[0] = co;
        });
        return out[0];
    }

    /**
     * Removes an overlay previously added by {@link #addChipTopOverlay} (or
     * similar), a no-op if {@code overlay} is null.
     */
    public void removeTopOverlay(final javax.swing.JComponent overlay) {
        if (overlay == null) {
            return;
        }
        Helpers.GUIRunAndWait(() -> {
            java.awt.Rectangle b = overlay.getBounds();
            remove(overlay);
            repaint(b);
        });
    }

    /**
     * Shows/updates the call-cost overlay. Before the river it's centered over
     * the face-down community cards (geometry-agnostic: reads the real
     * on-screen position of the community cards panel, works for all 9 tables
     * under zoom/HiDPI); on the river it switches to per-player overlays over
     * the rivals' hole cards. Font scales with community-card height so it
     * reads clearly without covering anything.
     */
    public void updateCallCostOverlay(String text) {
        Helpers.GUIRun(() -> {
            if (hasFaceDownCommunityCards()) {
                // Preflop/flop/turn: community cards are still face down -> single
                // overlay over them.
                hidePlayerCallCostOverlays();
                call_cost_label.setText(text);
                if (layoutCallCostOverlay()) {
                    call_cost_label.setVisible(true);
                    call_cost_label.repaint();
                } else {
                    call_cost_label.setVisible(false);
                }
            } else {
                // River: no community cards left to reveal -> the cost is shown over
                // each in-pot RemotePlayer's face-down hole cards instead.
                call_cost_label.setVisible(false);
                updatePlayerCallCostOverlays(text);
            }
        });
    }

    /**
     * Hides the call-cost overlay (both the shared one and the per-player
     * ones).
     */
    public void hideCallCostOverlay() {
        Helpers.GUIRun(() -> {
            call_cost_label.setVisible(false);
            hidePlayerCallCostOverlays();
        });
    }

    // Is any community card still face down? Decides the call-cost overlay mode:
    // yes -> single overlay over the community cards; no (river round) -> per-player
    // overlays.
    private boolean hasFaceDownCommunityCards() {
        CommunityCardsPanel cc = getCommunityCards();
        if (cc == null) {
            return false;
        }
        Card[] comunes = cc.getCartasComunes();
        if (comunes == null) {
            return false;
        }
        for (Card c : comunes) {
            if (c != null && c.isTapada()) {
                return true;
            }
        }
        return false;
    }

    private volatile boolean call_overlay_listener_attached = false;

    // Relocates and rescales the overlay to cover the community cards (position =
    // cards_panel) with a font proportional to a community card's REAL height — so the
    // text follows the community cards: shrinks in compact view, grows when expanded,
    // and scales with zoom. Returns false if the community cards aren't on screen (the
    // overlay is then hidden). Must be called on the EDT.
    private boolean layoutCallCostOverlay() {
        try {
            javax.swing.JPanel cards = getCommunityCards().getCards_panel();
            Card[] comunes = getCommunityCards().getCartasComunes();
            if (cards == null || comunes == null || comunes.length == 0 || comunes[0] == null) {
                return false;
            }
            final Card ref = comunes[0];
            // Listens for geometry changes to rescale/reposition the overlay on its own.
            attachCallOverlayResizeListener(ref);
            if (!cards.isShowing() || !isShowing()) {
                return false;
            }

            // The overlay only covers the community cards that are STILL FACE DOWN: the
            // call cost is effectively the cost of "revealing" the remaining card(s)
            // (preflop -> all 5; after the flop -> turn + river; after the turn ->
            // river), so it never covers already-revealed cards. Since cards are dealt
            // in a row (flop1-flop2-flop3-turn-river), the face-down ones are always a
            // trailing suffix. If none are face down (river round), falls back to the
            // whole set so the cost is still shown somewhere.
            java.awt.Rectangle box = unionCardBounds(comunes, true);
            if (box == null) {
                box = unionCardBounds(comunes, false);
            }
            if (box == null || box.width <= 0 || box.height <= 0) {
                return false;
            }

            java.awt.Point cp = cards.getLocationOnScreen();
            java.awt.Point origin = getLocationOnScreen();
            call_cost_label.setBounds(cp.x - origin.x + box.x, cp.y - origin.y + box.y, box.width, box.height);

            // Font proportional to a card's REAL height, but SHRUNK if needed so the
            // number fits within the covered area's width: with a single card (river)
            // the text shrinks to avoid overflowing onto revealed cards.
            int card_h = ref.getHeight();
            float base = card_h > 0 ? card_h : box.height;
            float size = base * 0.9f;
            final String text = call_cost_label.getText();
            if (text != null && !text.isEmpty()) {
                java.awt.FontMetrics fm = call_cost_label.getFontMetrics(
                        call_cost_label.getFont().deriveFont(java.awt.Font.BOLD, size));
                int text_w = fm.stringWidth(text);
                float budget = box.width * 0.92f;
                if (text_w > budget && text_w > 0) {
                    size *= budget / text_w;
                }
            }
            size = Math.max(12f, size);
            call_cost_label.setFont(call_cost_label.getFont().deriveFont(java.awt.Font.BOLD, size));
            return true;
        } catch (Exception ex) {
            // E.g. IllegalComponentStateException if cards_panel just stopped being on
            // screen.
            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    // Bounding box (in cards_panel's coordinates, the cards' parent) of the community
    // cards: if only_tapadas, only the ones still face down; otherwise all of them.
    // Returns null if none qualify.
    private static java.awt.Rectangle unionCardBounds(Card[] comunes, boolean only_tapadas) {
        java.awt.Rectangle box = null;
        for (Card c : comunes) {
            if (c == null || (only_tapadas && !c.isTapada())) {
                continue;
            }
            box = (box == null) ? c.getBounds() : box.union(c.getBounds());
        }
        return box;
    }

    // Attaches (once) listeners that rescale/reposition the overlay when the geometry
    // changes, while it's visible. Listens to TWO things:
    //   - the community card: catches SIZE changes (zoom, compact view shrinking cards)
    //     to recompute the font.
    //   - the whole CommunityCardsPanel: catches POSITION/size changes of the group
    //     (e.g. MEDIUM compact: only the remotes shrink and the panel moves up without
    //     the card itself changing size or relative position -> only this catches that).
    private void attachCallOverlayResizeListener(final Card ref) {
        if (call_overlay_listener_attached) {
            return;
        }
        call_overlay_listener_attached = true;
        java.awt.event.ComponentAdapter relayout = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                relayoutCallCostOverlayIfVisible();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                relayoutCallCostOverlayIfVisible();
            }
        };
        ref.addComponentListener(relayout);
        getCommunityCards().addComponentListener(relayout);
    }

    private void relayoutCallCostOverlayIfVisible() {
        Helpers.GUIRun(() -> {
            if (call_cost_label.isVisible()) {
                layoutCallCostOverlay();
                call_cost_label.repaint();
            }
        });
    }

    // --- Per-RemotePlayer call-cost overlays (river round) -------------------------
    // Paints/updates a call-cost overlay over the hole cards of each RemotePlayer still
    // in the pot with face-down cards. Reuses one label per player. Must be called on
    // the EDT.
    private void updatePlayerCallCostOverlays(String text) {
        RemotePlayer[] rps = remotePlayers;
        if (rps == null) {
            hidePlayerCallCostOverlays();
            return;
        }
        // The river overlay goes ONLY over the LAST AGGRESSOR (whoever made the raise or
        // re-raise the local player must call), not over everyone who calls before my
        // turn. current_bet is kept as a robustness guard (the aggressor set the current
        // bet, so it matches theirs) plus for the in-pot card checks.
        double current_bet = GameFrame.getInstance().getCrupier().getApuesta_actual();
        Player last_aggressor = GameFrame.getInstance().getCrupier().getLast_aggressor();
        for (RemotePlayer rp : rps) {
            if (rp == null) {
                continue;
            }
            CallCostOverlayLabel lbl = player_call_cost_labels.get(rp);
            if (rp == last_aggressor && isPotPlayerMatchingCurrentBet(rp, current_bet)) {
                if (lbl == null) {
                    lbl = new CallCostOverlayLabel();
                    lbl.setFocusable(false);
                    lbl.setOpaque(false);
                    lbl.setVisible(false);
                    // DRAG_LAYER: above everything living at the seat (cards, position
                    // chip, chat/rebuy GIFs, pot strip).
                    add(lbl, JLayeredPane.DRAG_LAYER);
                    player_call_cost_labels.put(rp, lbl);
                }
                attachPlayerCallOverlayResizeListener(rp);
                lbl.setText(text);
                if (layoutPlayerCallCostOverlay(rp, lbl)) {
                    lbl.setVisible(true);
                    lbl.repaint();
                } else {
                    lbl.setVisible(false);
                }
            } else if (lbl != null) {
                lbl.setVisible(false);
            }
        }
    }

    // Guard for "still in the pot with hidden cards + already matched the bet": (a) the
    // RemotePlayer is still in the pot with both hole cards shown on the table (folding
    // hides them via setVisibleCard(false)) and face down — which excludes folded
    // players, revealed all-ins, and (by iterating remotePlayers) the local player — and
    // (b) their bet matches current_bet. The caller (updatePlayerCallCostOverlays) ALSO
    // requires the LAST AGGRESSOR, so the river overlay appears ONLY over whoever
    // raised/re-raised, not over every player who just calls.
    private static boolean isPotPlayerMatchingCurrentBet(RemotePlayer rp, double current_bet) {
        Card c1 = rp.getHoleCard1();
        Card c2 = rp.getHoleCard2();
        if (c1 == null || c2 == null
                || !c1.isVisible_card() || !c2.isVisible_card()
                || !c1.isTapada() || !c2.isTapada()
                || c1.isSecure_hidden() || c2.isSecure_hidden()) {
            return false;
        }
        // current_bet > 0 is guaranteed (the overlay is only requested when the local
        // player has something to call), but we check it anyway for robustness.
        return Helpers.doubleSecureCompare(current_bet, 0f) > 0
                && Helpers.doubleSecureCompare(current_bet, rp.getBet()) == 0;
    }

    // Relocates/rescales a RemotePlayer's overlay to cover, centered, both their hole
    // cards (reads real on-screen positions -> works for all 9 tables, zoom, HiDPI, and
    // the compact view that shrinks remote players). Font proportional to card height,
    // shrunk to fit the number within the two cards' width. Returns false if they aren't
    // on screen. Must be called on the EDT.
    private boolean layoutPlayerCallCostOverlay(RemotePlayer rp, CallCostOverlayLabel lbl) {
        try {
            Card c1 = rp.getHoleCard1();
            Card c2 = rp.getHoleCard2();
            if (c1 == null || c2 == null || !isShowing() || !c1.isShowing() || !c2.isShowing()) {
                return false;
            }
            java.awt.Point origin = getLocationOnScreen();
            java.awt.Point p1 = c1.getLocationOnScreen();
            java.awt.Rectangle box = new java.awt.Rectangle(p1.x, p1.y, c1.getWidth(), c1.getHeight());
            java.awt.Point p2 = c2.getLocationOnScreen();
            box = box.union(new java.awt.Rectangle(p2.x, p2.y, c2.getWidth(), c2.getHeight()));
            if (box.width <= 0 || box.height <= 0) {
                return false;
            }
            lbl.setBounds(box.x - origin.x, box.y - origin.y, box.width, box.height);

            // The font family comes from the community-cards label (which already went
            // through the table's font pass), so the per-player overlay uses EXACTLY the
            // same font instead of JLabel's default.
            java.awt.Font base_font = call_cost_label.getFont();
            int card_h = c1.getHeight();
            float base = card_h > 0 ? card_h : box.height;
            float size = base * 0.9f;
            final String text = lbl.getText();
            if (text != null && !text.isEmpty()) {
                java.awt.FontMetrics fm = lbl.getFontMetrics(
                        base_font.deriveFont(java.awt.Font.BOLD, size));
                int text_w = fm.stringWidth(text);
                float budget = box.width * 0.92f;
                if (text_w > budget && text_w > 0) {
                    size *= budget / text_w;
                }
            }
            size = Math.max(12f, size);
            lbl.setFont(base_font.deriveFont(java.awt.Font.BOLD, size));
            return true;
        } catch (Exception ex) {
            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private void hidePlayerCallCostOverlays() {
        for (CallCostOverlayLabel lbl : player_call_cost_labels.values()) {
            lbl.setVisible(false);
        }
    }

    // Attaches (once per RemotePlayer) listeners that rescale/reposition their overlays
    // when the geometry changes while visible. Like with the community cards, listens to
    // the whole seat (POSITION: compact view moves it up) and panel_cartas + hole cards
    // (SIZE: compact view shrinks remote players).
    private void attachPlayerCallOverlayResizeListener(final RemotePlayer rp) {
        if (!player_call_overlay_listeners.add(rp)) {
            return;
        }
        java.awt.event.ComponentAdapter relayout = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                relayoutPlayerCallCostOverlaysIfVisible();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                relayoutPlayerCallCostOverlaysIfVisible();
            }
        };
        rp.addComponentListener(relayout);
        if (rp.getPanel_cartas() != null) {
            rp.getPanel_cartas().addComponentListener(relayout);
        }
        Card c1 = rp.getHoleCard1();
        Card c2 = rp.getHoleCard2();
        if (c1 != null) {
            c1.addComponentListener(relayout);
        }
        if (c2 != null) {
            c2.addComponentListener(relayout);
        }
    }

    private void relayoutPlayerCallCostOverlaysIfVisible() {
        Helpers.GUIRun(() -> {
            for (java.util.Map.Entry<RemotePlayer, CallCostOverlayLabel> e : player_call_cost_labels.entrySet()) {
                CallCostOverlayLabel lbl = e.getValue();
                if (lbl.isVisible()) {
                    if (layoutPlayerCallCostOverlay(e.getKey(), lbl)) {
                        lbl.repaint();
                    } else {
                        lbl.setVisible(false);
                    }
                }
            }
        });
    }

    // Call-cost overlay label: paints centered text with a semi-transparent black fill
    // and a yellow outline (halo), readable over ANY background (light cards, dark card
    // backs, felt) without obscuring it. Extends JLabel to reuse setText/setFont/
    // setBounds for positioning; overrides painting to draw the outline (plain JLabel
    // doesn't support that).
    private static final class CallCostOverlayLabel extends javax.swing.JLabel {

        // Contrast/visibility tunables.
        private final java.awt.Color fill = new java.awt.Color(0, 0, 0, 204);
        private final java.awt.Color halo = new java.awt.Color(255, 255, 0, 204);
        private static final float STROKE_RATIO = 0.05f;

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            final String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                java.awt.Font font = getFont();
                java.awt.font.TextLayout tl = new java.awt.font.TextLayout(text, font, g2.getFontRenderContext());
                java.awt.geom.Rectangle2D b = tl.getBounds();
                double x = (getWidth() - b.getWidth()) / 2.0 - b.getX();
                double y = (getHeight() - b.getHeight()) / 2.0 - b.getY();
                java.awt.Shape outline = tl.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));

                float stroke = Math.max(2f, font.getSize2D() * STROKE_RATIO);
                g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(halo);
                g2.draw(outline);
                g2.setColor(fill);
                g2.fill(outline);
            } finally {
                g2.dispose();
            }
        }
    }

    // "SHUFFLING" label: centered text painted as an outline (black border) + fill,
    // same style as the end-of-hand banner (YOU WON). The font is RESCALED on every
    // paint to FILL the component's width (set to the community-cards panel's width),
    // capped by height. Visual substitute for the shuffle GIF when that GIF isn't played.
    private static final class ShufflingTextLabel extends javax.swing.JLabel {

        // Fill = the table's counter color (white on dark/wood felt, its own color on
        // green/blue/red); border is fixed black. setFill updates it when shown.
        private java.awt.Color fill = java.awt.Color.WHITE;
        private final java.awt.Color halo = new java.awt.Color(0, 0, 0, 235);
        private static final float STROKE_RATIO = 0.06f;

        ShufflingTextLabel() {
            super("", javax.swing.SwingConstants.CENTER);
            setOpaque(false);
        }

        void setFill(java.awt.Color c) {
            this.fill = (c != null) ? c : java.awt.Color.WHITE;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            final String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                java.awt.Font base = getFont();
                java.awt.font.FontRenderContext frc = g2.getFontRenderContext();

                // Rescales the font so the text FILLS the available width (96%), capped
                // by height: this way "SHUFFLING" spans the community-cards panel's
                // width regardless of resolution.
                java.awt.font.TextLayout probe = new java.awt.font.TextLayout(text, base, frc);
                double tw = probe.getAdvance();
                double th = probe.getAscent() + probe.getDescent();
                double avail_w = getWidth() * 0.96;
                double avail_h = getHeight() * 0.96;
                double scale = tw > 0 ? avail_w / tw : 1.0;
                if (th * scale > avail_h && th > 0) {
                    scale = Math.min(scale, avail_h / th);
                }
                java.awt.Font font = base.deriveFont((float) Math.max(8.0, base.getSize2D() * scale));

                java.awt.font.TextLayout tl = new java.awt.font.TextLayout(text, font, frc);
                java.awt.geom.Rectangle2D b = tl.getBounds();
                double x = (getWidth() - b.getWidth()) / 2.0 - b.getX();
                double y = (getHeight() - b.getHeight()) / 2.0 - b.getY();
                java.awt.Shape outline = tl.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));

                float stroke = Math.max(2f, font.getSize2D() * STROKE_RATIO);
                g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(halo);
                g2.draw(outline);
                g2.setColor(fill);
                g2.fill(outline);
            } finally {
                g2.dispose();
            }
        }
    }

    // Classic (Hermite) smoothstep: 0 for x<=a, 1 for x>=b, smooth in between.
    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }

    // Ephemeral traveling-card component: paints a (pre-rasterized, pre-scaled) back
    // image rotated by an arbitrary angle on a square canvas, centered, transparent
    // outside the card. No heavy Swing state. Also supports scale (1.0 = nominal size)
    // and opacity (1.0 = opaque), neutral by default, for the shrink-and-fade effect
    // when chips land in the pot.
    private static final class FlyingCard extends javax.swing.JComponent {

        private final java.awt.Image img;
        private final int dw;
        private final int dh;
        private volatile double angle = 0.0;
        private volatile double scale = 1.0;
        private volatile float alpha = 1.0f;

        FlyingCard(java.awt.Image img, int dw, int dh) {
            this.img = img;
            this.dw = dw;
            this.dh = dh;
            setOpaque(false);
        }

        void setAngle(double a) {
            this.angle = a;
        }

        void setScale(double s) {
            this.scale = s;
        }

        void setAlpha(float a) {
            this.alpha = a;
        }

        void setCenter(double cx, double cy) {
            setLocation((int) Math.round(cx - getWidth() / 2.0), (int) Math.round(cy - getHeight() / 2.0));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                if (alpha < 1.0f) {
                    g2.setComposite(java.awt.AlphaComposite.getInstance(
                            java.awt.AlphaComposite.SRC_OVER, Math.max(0.0f, Math.min(1.0f, alpha))));
                }
                int w = getWidth();
                int h = getHeight();
                // Performance mode: no rotation (direct blit, no per-frame bitmap
                // resampling). Quality mode (default) rotates exactly as before. angle == 0
                // (every chip flight) makes rotate() an identity transform, so skip it: same
                // pixels, one less transform concat per frame.
                if (GameFrame.ANIM_CALIDAD && angle != 0.0) {
                    g2.rotate(angle, w / 2.0, h / 2.0);
                }
                if (scale != 1.0) {
                    g2.translate(w / 2.0, h / 2.0);
                    g2.scale(scale, scale);
                    g2.translate(-w / 2.0, -h / 2.0);
                }
                g2.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * A position chip's flight (dealer/blind): its already-scaled sprite, the
     * origin seat (previous carrier; null = table center, e.g. the first hand),
     * and the destination seat (new carrier).
     */
    public static final class ChipFlight {

        private final Player from;
        private final Player to;
        private final ImageIcon sprite;

        public ChipFlight(Player from, Player to, ImageIcon sprite) {
            this.from = from;
            this.to = to;
            this.sprite = sprite;
        }
    }

    /**
     * Slides SEVERAL position chips at once (dealer + blinds) from their
     * previous seat to the new one, right before the central shuffle. Reuses
     * the deal flight's kinematics (quadratic easeOut + clamped perpendicular
     * bezier arc, nanoTime-driven ticks, fixed duration = same speed as cards),
     * but all chips travel in parallel (a single Timer) so pauses don't chain.
     * Geometry-agnostic: reads each seat's real on-screen position. Blocks the
     * caller (crupier thread, NEVER the EDT) until landing + dwell.
     *
     * @param onLand runs on the EDT once all chips reach their destination,
     * BEFORE the dwell, to put the static chips back under the traveling ones
     * (gap-free handoff); a no-op if the animation is disabled or there are no
     * flights
     */
    public void flyChipsToSeats(final java.util.List<ChipFlight> flights, final int duration_ms, final Runnable onLand) {

        if (flights == null || flights.isEmpty()
                || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            return;
        }

        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final java.util.List<FlyingCard> travelers = new java.util.ArrayList<>();

        Helpers.GUIRunAndWait(() -> {
            try {
                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    if (onLand != null) {
                        onLand.run();
                    }
                    finished.countDown();
                    return;
                }

                final double tableCx = getWidth() / 2.0;
                final double tableCy = getHeight() / 2.0;
                final double originX = getLocationOnScreen().getX();
                final double originY = getLocationOnScreen().getY();

                // Precomputed path per chip: {fromX, fromY, ctrlX, ctrlY, toX, toY}.
                final java.util.List<double[]> paths = new java.util.ArrayList<>();

                for (ChipFlight f : flights) {
                    if (f == null || f.to == null || f.sprite == null) {
                        continue;
                    }
                    final int w = f.sprite.getIconWidth();
                    final int h = f.sprite.getIconHeight();
                    if (w <= 0 || h <= 0) {
                        continue;
                    }

                    final java.awt.geom.Point2D toScr = f.to.getPositionChipScreenCenter(w, h);
                    if (toScr == null) {
                        continue;
                    }
                    final double toCx = toScr.getX() - originX;
                    final double toCy = toScr.getY() - originY;

                    final double fromCx, fromCy;
                    final java.awt.geom.Point2D fromScr = (f.from != null) ? f.from.getPositionChipScreenCenter(w, h) : null;
                    if (fromScr != null) {
                        fromCx = fromScr.getX() - originX;
                        fromCy = fromScr.getY() - originY;
                    } else {
                        fromCx = tableCx;
                        fromCy = tableCy;
                    }

                    // Arc control point: path midpoint offset perpendicular, clamped
                    // (identical to the card flight).
                    final double mx = (fromCx + toCx) / 2.0, my = (fromCy + toCy) / 2.0;
                    final double vx = toCx - fromCx, vy = toCy - fromCy;
                    final double len = Math.hypot(vx, vy);
                    final double arc = Math.min(len * 0.16, h);
                    final double nx = (len > 1) ? -vy / len : 0.0;
                    final double ny = (len > 1) ? vx / len : 0.0;
                    final double ctrlX = mx + nx * arc;
                    final double ctrlY = my + ny * arc;

                    final int box = (int) Math.ceil(Math.hypot(w, h));
                    final FlyingCard traveler = new FlyingCard(f.sprite.getImage(), w, h);
                    traveler.setSize(box, box);
                    traveler.setAngle(0.0);
                    traveler.setCenter(fromCx, fromCy);
                    add(traveler, JLayeredPane.DRAG_LAYER);
                    travelers.add(traveler);
                    paths.add(new double[]{fromCx, fromCy, ctrlX, ctrlY, toCx, toCy});
                }

                if (travelers.isEmpty()) {
                    if (onLand != null) {
                        onLand.run();
                    }
                    finished.countDown();
                    return;
                }

                final long t0 = System.nanoTime();
                final boolean[] landed = {false};

                final javax.swing.Timer player = new javax.swing.Timer(GameFrame.getTickMs(), null);
                holder[0] = player;

                player.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, duration_ms));

                    // Quadratic easeOut for position (same as the deal).
                    double s = 1.0 - (1.0 - u) * (1.0 - u);
                    double is = 1.0 - s;

                    for (int k = 0; k < travelers.size(); k++) {
                        double[] p = paths.get(k);
                        double x = is * is * p[0] + 2 * is * s * p[2] + s * s * p[4];
                        double y = is * is * p[1] + 2 * is * s * p[3] + s * s * p[5];
                        FlyingCard traveler = travelers.get(k);
                        // Change-gate: skip repaint for any chip that rounds to the same pixel
                        // this tick (setCenter is a no-op then). Chips never rotate, so position
                        // is the only visible state -> byte-identical frame when unmoved.
                        int bx = traveler.getX();
                        int by = traveler.getY();
                        traveler.setCenter(x, y);
                        if (traveler.getX() != bx || traveler.getY() != by) {
                            traveler.repaint();
                        }
                    }

                    if (u >= 1.0 || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        if (!landed[0]) {
                            landed[0] = true;
                            // Puts the static chips back under the traveling ones (same
                            // sprite, same position) -> gap-free handoff on removal.
                            if (onLand != null) {
                                onLand.run();
                            }
                        }
                        finished.countDown();
                    }
                });

                player.start();

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                for (FlyingCard traveler : travelers) {
                    remove(traveler);
                }
                if (onLand != null) {
                    onLand.run();
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "chip flight", ex);
        }

        // Handoff dwell: the static chip (restored in onLand) is painted async under
        // the traveling one, so removing it later is identical (no flicker).
        Helpers.parkThreadMillis(40);

        Helpers.GUIRunAndWait(() -> {
            for (FlyingCard traveler : travelers) {
                java.awt.Rectangle b = traveler.getBounds();
                remove(traveler);
                repaint(b);
            }
        });

        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    /**
     * Flies ONE chip (pot sprite) from the seat of the player who just put in
     * money to the pot_label icon (the pot). Same kinematics as the
     * deal/position flights (quadratic easeOut + clamped perpendicular bezier
     * arc, nanoTime-driven ticks, constant speed by sprite height so every chip
     * travels equally fast regardless of seat). Unlike the position-chip
     * handoff, there's no static chip to restore here: on landing the traveling
     * chip shrinks and fades out, pot_label flashes yellow (signaling it
     * absorbed the chip), and the traveler is removed. Geometry-agnostic: reads
     * the seat's and pot's real on-screen positions, works for all 9 tables
     * under zoom/HiDPI.
     * <p>
     * Does NOT block: starts on the EDT and returns control immediately
     * (cleanup, the flash, and {@code onLand} happen in the timer's final
     * stretch). Several chips can be in flight at once.
     *
     * @param onLand runs EXACTLY ONCE, the instant the chip touches the pot
     * (together with the yellow flash) so pot_label's value updates right on
     * landing. If the animation can't run (no sprite/visible origin, or end of
     * transmission) it runs immediately instead, so pot_label is never left
     * stale.
     */
    public void flyChipToPot(final Player player,
            final ImageIcon sprite,
            final int shrink_ms,
            final Runnable onLand) {

        flyMoneyChip(player, sprite, shrink_ms, false, onLand);
    }

    /**
     * Reverse counterpart of flyChipToPot: flies a money chip from the real pot
     * icon back to the same player anchor used as the origin of a normal
     * bet/call chip.
     */
    public void flyChipFromPot(final Player player,
            final ImageIcon sprite,
            final int shrink_ms,
            final Runnable onLand) {

        flyMoneyChip(player, sprite, shrink_ms, true, onLand);
    }

    /**
     * Shared money-chip flight used in both directions:
     *
     * player -> pot : normal bet/call pot -> player : showdown payout
     *
     * Both directions use the same sprite, speed calculation, Bezier path,
     * shared timer and shrink/fade absorption.
     */
    private void flyMoneyChip(final Player player,
            final ImageIcon sprite,
            final int shrink_ms,
            final boolean fromPot,
            final Runnable onLand) {

        // Same constant-speed parameters used by the existing bet/call flight.
        final double MS_PER_CHIPHEIGHT = 120.0;
        final int SPEED_MIN_MS = 120;
        final int SPEED_MAX_MS = 320;

        // Guarantees the landing callback runs exactly once.
        final Runnable[] land_holder = {onLand};

        if (sprite == null || player == null
                || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            runOnce(land_holder);
            return;
        }

        final int w = sprite.getIconWidth();
        final int h = sprite.getIconHeight();

        if (w <= 0 || h <= 0) {
            runOnce(land_holder);
            return;
        }

        Helpers.GUIRun(() -> {
            try {

                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    runOnce(land_holder);
                    return;
                }

                final double originX = getLocationOnScreen().getX();
                final double originY = getLocationOnScreen().getY();

                // Player anchor: EXACTLY the same anchor already used by bet/call.
                final java.awt.geom.Point2D seatScr
                        = player.getPositionChipScreenCenter(w, h);

                if (seatScr == null) {
                    runOnce(land_holder);
                    return;
                }

                final double seatCx = seatScr.getX() - originX;
                final double seatCy = seatScr.getY() - originY;

                // Pot anchor: the real chip icon inside pot_label.
                final java.awt.geom.Point2D potIcon
                        = getCommunityCards().getPotIconScreenCenter();

                final double potCx;
                final double potCy;

                if (potIcon != null) {

                    potCx = potIcon.getX() - originX;
                    potCy = potIcon.getY() - originY;

                } else if (!fromPot) {

                    // Preserve the old bet/call fallback.
                    potCx = getWidth() / 2.0;
                    potCy = getHeight() / 2.0;

                } else {

                    // A payout must really originate at pot_label; never fake it
                    // from the table center.
                    runOnce(land_holder);
                    return;
                }

                final double fromCx = fromPot ? potCx : seatCx;
                final double fromCy = fromPot ? potCy : seatCy;

                final double toCx = fromPot ? seatCx : potCx;
                final double toCy = fromPot ? seatCy : potCy;

                // Same curved trajectory as bet/call.
                final double mx = (fromCx + toCx) / 2.0;
                final double my = (fromCy + toCy) / 2.0;

                final double vx = toCx - fromCx;
                final double vy = toCy - fromCy;
                final double len = Math.hypot(vx, vy);

                final double arc = Math.min(len * 0.16, h);
                final double nx = (len > 1) ? -vy / len : 0.0;
                final double ny = (len > 1) ? vx / len : 0.0;

                // Reversing the direction also reverses the perpendicular vector.
                // Flipping its sign makes the payout retrace the same visual side
                // of the original bet/call arc instead of mirroring it.
                final double arcSide = fromPot ? -1.0 : 1.0;

                final double ctrlX = mx + nx * arc * arcSide;
                final double ctrlY = my + ny * arc * arcSide;

                // Same distance-based duration as bet/call.
                final int fly_dur = (int) Math.round(
                        Math.max(
                                SPEED_MIN_MS,
                                Math.min(
                                        SPEED_MAX_MS,
                                        (len / h) * MS_PER_CHIPHEIGHT
                                )
                        )
                );

                final int box = (int) Math.ceil(Math.hypot(w, h));

                final FlyingCard traveler
                        = new FlyingCard(sprite.getImage(), w, h);

                traveler.setSize(box, box);
                traveler.setAngle(0.0);
                traveler.setCenter(fromCx, fromCy);

                add(traveler, JLayeredPane.DRAG_LAYER);

                // Reuse the existing shared money-chip animator.
                pot_chip_flights.add(new PotChipFlight(
                        traveler,
                        fromCx,
                        fromCy,
                        ctrlX,
                        ctrlY,
                        toCx,
                        toCy,
                        fly_dur,
                        shrink_ms,
                        System.nanoTime(),
                        land_holder,
                        !fromPot
                ));

                ensurePotChipTimer();

            } catch (Exception ex) {

                Logger.getLogger(TablePanel.class.getName())
                        .log(Level.SEVERE, null, ex);

                runOnce(land_holder);
            }
        });
    }

    // Runs the holder's Runnable at most once (clears it after running). The call sites
    // are mutually exclusive (early exit on the caller's thread, or timer ticks on the
    // EDT), so there's no concurrent access.
    private static void runOnce(Runnable[] holder) {
        Runnable r = holder[0];
        if (r != null) {
            holder[0] = null;
            r.run();
        }
    }

    // Ensures the single shared pot-chip animator timer exists and is running. EDT-only.
    private void ensurePotChipTimer() {
        if (pot_chip_timer == null) {
            pot_chip_timer = new javax.swing.Timer(GameFrame.getTickMs(), e -> tickPotChipFlights());
        }
        if (!pot_chip_timer.isRunning()) {
            pot_chip_timer.start();
        }
    }

    // Advances EVERY in-flight pot chip one tick — the single shared replacement for the former
    // one-Timer-per-chip design. Iterates a SNAPSHOT of the list so a reentrant flyChipToPot fired
    // from an onLand/flash callback (Helpers.GUIRun runs inline when already on the EDT) can safely
    // append to pot_chip_flights mid-tick: the new chip is picked up on the NEXT tick, never during
    // this iteration. Each flight is advanced in its own try/catch so one bad chip can't kill the
    // shared timer (and thus every other chip). Stops the timer once nothing is left. EDT-only.
    private void tickPotChipFlights() {
        final boolean end = GameFrame.getInstance().getCrupier().isFin_de_la_transmision();

        for (PotChipFlight f : new java.util.ArrayList<>(pot_chip_flights)) {
            boolean done;
            try {
                done = advancePotChipFlight(f, end);
            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                done = true; // tear this flight down below; never let it wedge the shared timer
            }

            if (done) {
                // The finally GUARANTEES the flight is dropped from the list no matter what the
                // cleanup does — so even a throwing onLand can never leave a flight stuck being
                // reprocessed every tick (which would starve the others and spin the shared timer).
                try {
                    java.awt.Rectangle b = f.traveler.getBounds();
                    remove(f.traveler);
                    repaint(b);
                    // If phase 2 never ran (end-of-transmission or an exception before landing),
                    // onLand never fired; run it now so the pot value is never left stale. No-op if
                    // it already ran.
                    runOnce(f.land_holder);
                } catch (Exception ex) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    pot_chip_flights.remove(f);
                }
            }
        }

        if (pot_chip_flights.isEmpty() && pot_chip_timer != null) {
            pot_chip_timer.stop();
        }
    }

    // Advances one flight a single tick; returns true when it has finished (reached the pot and
    // fully shrunk) or must be torn down (end-of-transmission). Mirrors exactly what each chip's
    // own Timer listener used to do. EDT-only.
    private boolean advancePotChipFlight(PotChipFlight f, boolean end) {

        if (end) {
            return true;
        }

        final FlyingCard traveler = f.traveler;

        final long elapsed
                = (System.nanoTime() - f.t0) / 1_000_000L;

        if (elapsed < f.fly_dur) {

            // Phase 1: flight to the destination, identical to bet/call.
            double u = (double) elapsed / Math.max(1, f.fly_dur);

            double s = 1.0 - (1.0 - u) * (1.0 - u);
            double is = 1.0 - s;

            double x
                    = is * is * f.fromCx
                    + 2 * is * s * f.ctrlX
                    + s * s * f.toCx;

            double y
                    = is * is * f.fromCy
                    + 2 * is * s * f.ctrlY
                    + s * s * f.toCy;

            int bx = traveler.getX();
            int by = traveler.getY();

            traveler.setCenter(x, y);

            if (traveler.getX() != bx || traveler.getY() != by) {
                traveler.repaint();
            }

            return false;
        }

        // Phase 2 starts exactly when the chip touches its destination.
        if (!f.flashed) {

            f.flashed = true;

            if (f.flashPotOnLand) {

                // Normal bet/call: the pot absorbs the chip and flashes yellow.
                final Runnable land = f.land_holder[0];

                f.land_holder[0] = null;

                getCommunityCards().flashPotLabelYellow(land);

            } else {

                // Showdown payout: the player's stack absorbs the chip.
                // The callback starts the reverse pot/stack counter rolls.
                runOnce(f.land_holder);
            }
        }

        // Same shrink-and-fade absorption used by bet/call.
        long se = elapsed - f.fly_dur;

        double su = Math.min(
                1.0,
                (double) se / Math.max(1, f.shrink_ms)
        );

        double k = su * su * (3.0 - 2.0 * su);

        traveler.setCenter(f.toCx, f.toCy);
        traveler.setScale(1.0 - k);
        traveler.setAlpha((float) (1.0 - k));
        traveler.repaint();

        return su >= 1.0;
    }

    // One pot-bound chip flight's state, advanced by the shared pot-chip timer. Bundles the
    // kinematic path, its own timing (t0/fly_dur/shrink_ms) and the one-shot pot-flash + onLand
    // callback that used to live in each chip's per-Timer closure. EDT-only.
    private static final class PotChipFlight {

        final FlyingCard traveler;

        final double fromCx;
        final double fromCy;

        final double ctrlX;
        final double ctrlY;

        final double toCx;
        final double toCy;

        final int fly_dur;
        final int shrink_ms;

        final long t0;

        final Runnable[] land_holder;

        // true  = normal player -> pot flight
        // false = showdown pot -> player flight
        final boolean flashPotOnLand;

        boolean flashed = false;

        PotChipFlight(FlyingCard traveler,
                double fromCx,
                double fromCy,
                double ctrlX,
                double ctrlY,
                double toCx,
                double toCy,
                int fly_dur,
                int shrink_ms,
                long t0,
                Runnable[] land_holder,
                boolean flashPotOnLand) {

            this.traveler = traveler;

            this.fromCx = fromCx;
            this.fromCy = fromCy;

            this.ctrlX = ctrlX;
            this.ctrlY = ctrlY;

            this.toCx = toCx;
            this.toCy = toCy;

            this.fly_dur = fly_dur;
            this.shrink_ms = shrink_ms;

            this.t0 = t0;

            this.land_holder = land_holder;

            this.flashPotOnLand = flashPotOnLand;
        }
    }

    /**
     * Looping variant of {@link #showCentralFrames} for the shuffle GIF:
     * repeats the animation (centered, with audio re-triggered each cycle and
     * cut at {@code audio_stop_frame}, same contract as addAudio(1, stop))
     * until {@code keep_looping} turns false. The predicate is only checked on
     * reaching the last frame, so at least one full cycle always plays,
     * matching the legacy do-while over showCentralImage. Blocks the caller
     * until the loop ends, respecting {@code fin_de_la_transmision} and
     * central-label takeover.
     */
    public void showCentralFramesLoop(PreRenderedGif anim, int display_w, int display_h, String audio, int audio_stop_frame, java.util.function.BooleanSupplier keep_looping) {

        central_label_thread = Thread.currentThread().getId();

        final CountDownLatch finished = new CountDownLatch(1);

        final javax.swing.Timer[] player_holder = new javax.swing.Timer[1];

        Helpers.GUIRunAndWait(() -> {

            getCentral_label().setSize(display_w, display_h);
            getCentral_label().setLocation(Math.round((getWidth() - display_w) / 2), Math.round((getHeight() - display_h) / 2));

            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

                getCentral_label().setIcon(null);
                getCentral_label().setFrameOverride(anim.getFrame(0));
                getCentral_label().setVisible(true);

                // The shuffle audio is SYNCED to the GIF's cycle: starts with the first
                // frame and stops at the end of the cycle; if the GIF loops again, it
                // restarts from zero. Clip pre-opened and reused -> start/stop is
                // instant, with no per-cycle open that could lose the race and go silent.
                if (audio != null) {
                    Audio.playPreloadedWav(audio);
                }

                final long[] t0 = {System.nanoTime()};
                final long total_ms = anim.getTotalMs();
                final int[] painted = {0};
                final boolean[] audio_on = {audio != null};

                final javax.swing.Timer player = new javax.swing.Timer(GameFrame.getTickMs(), null);

                player_holder[0] = player;

                player.addActionListener(e -> {

                    if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        if (audio != null && audio_on[0]) {
                            audio_on[0] = false;
                            Audio.stopPreloadedWav(audio);
                        }
                        player.stop();
                        finished.countDown();
                        return;
                    }

                    long elapsed = (System.nanoTime() - t0[0]) / 1_000_000L;

                    int idx = anim.frameAt(elapsed);

                    // Early audio cutoff at audio_stop_frame (before the last frame):
                    // lets the device's output buffer drain before the cycle visually
                    // ends, so the sound doesn't linger a beat after the animation
                    // disappears.
                    if (audio_on[0] && idx + 1 >= audio_stop_frame) {
                        audio_on[0] = false;
                        Audio.stopPreloadedWav(audio);
                    }

                    if (idx != painted[0]) {
                        painted[0] = idx;
                        getCentral_label().setFrameOverride(anim.getFrame(idx));
                    }

                    // Cycle ends once the LAST frame has also consumed its own delay
                    // (not upon entering it), so the cycle always lasts the GIF's
                    // nominal total duration.
                    if (elapsed >= total_ms) {

                        if (keep_looping.getAsBoolean()) {
                            // Another GIF cycle: exact time rewind, and the audio starts
                            // again from zero (it will be cut again at this cycle's
                            // audio_stop_frame).
                            t0[0] = System.nanoTime();
                            painted[0] = 0;
                            getCentral_label().setFrameOverride(anim.getFrame(0));
                            if (audio != null) {
                                audio_on[0] = true;
                                Audio.playPreloadedWav(audio);
                            }
                        } else {
                            // End of shuffle: the audio was already cut at
                            // audio_stop_frame; this is a defensive close in case it
                            // wasn't reached.
                            if (audio != null && audio_on[0]) {
                                audio_on[0] = false;
                                Audio.stopPreloadedWav(audio);
                            }
                            player.stop();
                            finished.countDown();
                        }
                    }
                });

                player.start();

            } else {
                finished.countDown();
            }
        });

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().getId() == central_label_thread) {

            try {
                // The loop lasts as long as the predicate holds (the SRA cascade can run
                // long), so the normal wait is indefinite: the only normal exit is the
                // player's countDown. The per-round timeout is purely defensive: if the
                // predicate already fell (or there's an end of transmission) and the
                // player hasn't counted the latch after one FULL extra round, no EDT is
                // alive to count it (shutdown), and continuing to wait would block the
                // crupier thread.
                boolean stopping_observed = false;

                while (!finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS)) {

                    boolean stopping = !keep_looping.getAsBoolean() || GameFrame.getInstance().getCrupier().isFin_de_la_transmision();

                    if (stopping && stopping_observed) {
                        break;
                    }

                    stopping_observed = stopping;
                }
            } catch (InterruptedException ex) {
                Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                        "central label pre-rendered loop playback", ex);
            }

            if (Thread.currentThread().getId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setFrameOverride(null);
                    getCentral_label().setVisible(false);
                });
            }
        }

        // Same belt and suspenders as showCentralFrames for the exotic paths (defensive
        // timeout, takeover): stop THIS player without touching a new label owner's.
        Helpers.GUIRun(() -> {
            if (player_holder[0] != null) {
                player_holder[0].stop();
            }
        });
    }

    public GifLabel getCentral_label() {
        return central_label;
    }

    /**
     * Shows "SHUFFLING" centered where the shuffle GIF would play, sized to the
     * community-cards panel's width (the text rescales to fill it). Visual
     * substitute for when the shuffle GIF isn't played (deck has no
     * shuffle.gif, or animations are off). EDT-safe; stays visible until
     * {@link #hideShufflingText()}.
     */
    public void showShufflingText() {
        Helpers.GUIRun(() -> {
            // The width is set as a fraction of the TABLE (not the community-cards
            // panel): it's the only reference that's INVARIANT per hand. The community-
            // cards panel's width depends on layout/zoom state, which settles after the
            // first hand -> this used to produce a large label the first time and
            // smaller ones after. The table doesn't change with zoom, so the label is
            // ALWAYS the same size. 0.35 of the table's width is the chosen size.
            int w = Math.round(getWidth() * 0.35f);
            int h = Math.max(40, Math.round(w * 0.30f));
            // Fill = the table's counter color (adapts to the chosen felt); white if
            // not yet defined. Border is fixed black (the label itself paints it).
            java.awt.Color tapete_color = null;
            try {
                tapete_color = getCommunityCards().getColor_contadores();
            } catch (Exception ex) {
            }
            shuffling_label.setFill(tapete_color);
            shuffling_label.setText(Translator.translate("game.barajando"));
            // Base font; paintComponent itself rescales it to fill the width.
            shuffling_label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, Math.max(12, h)));
            shuffling_label.setSize(w, h);
            shuffling_label.setLocation(Math.round((getWidth() - w) / 2f), Math.round((getHeight() - h) / 2f));
            shuffling_label.setVisible(true);
            shuffling_label.repaint();
        });
    }

    /**
     * Hides the "SHUFFLING" fallback label.
     */
    public void hideShufflingText() {
        Helpers.GUIRun(() -> {
            shuffling_label.setVisible(false);
            shuffling_label.setText("");
        });
    }

    /**
     * Hides every table element (players, community cards, overlays) and
     * cancels any pending local-player auto-action dialog.
     */
    public void hideALL() {

        Helpers.GUIRun(() -> {
            for (Player p : players) {
                ((JPanel) p).setVisible(false);
            }

            getCommunityCards().setVisible(false);

            central_label.setVisible(false);

            // "SHUFFLING" label: lives in its own layer, outside the table's normal
            // flow; hiding the table (leave, game over, balance) would otherwise leave
            // it floating in the center.
            shuffling_label.setVisible(false);

            // The call-cost overlay lives in its own table layer: without this it would
            // stay floating in the center when the table is hidden (leave, game over,
            // balance). Same for the per-player river overlays.
            call_cost_label.setVisible(false);
            hidePlayerCallCostOverlays();

            // AUTO mode is now an overlay inside the table itself (POPUP layer). When
            // hiding the table -leaving the game, end of match- we close it as canceled
            // (the hand already ended, so it takes no action); cancel() removes it from
            // the table and restores the button bar.
            LocalPlayer local_player = GameFrame.getInstance().getLocalPlayer();

            if (local_player != null && local_player.getAuto_action_dialog() != null) {
                local_player.getAuto_action_dialog().cancel();
            }
        });

    }

    /**
     * Plays the felt "thud" sound (if enabled) and forces a full repaint.
     */
    public void refresh() {

        if (GameFrame.tapeteSonidoOn()) {
            Audio.playWavResource("misc/mat.wav");
        }

        this.invalidate = true;

        Helpers.GUIRun(() -> {

            revalidate();
            repaint();
        });
    }

    // The "lights out" veil is painted by the table itself, on top of everything on it.
    // paint(), not paintComponent(): it must come AFTER the children (cards, seats,
    // pots), not underneath. It used to be painted by a JLayer wrapping the whole table.
    @Override
    public void paint(Graphics g) {

        super.paint(g);

        if (GameFrame.getInstance() != null) {
            GameFrame.getInstance().getCapa_brillo().paintOverlay(g, getWidth(), getHeight());
        }
    }

    // With the veil on, any child component's repaint must originate HERE so the veil
    // gets repainted on top of whatever was just redrawn. With the lights on this isn't
    // needed and the table repaints component-by-component like any JLayeredPane — that
    // was the cost of the old JLayer-based veil, which needed this ALWAYS.
    @Override
    protected boolean isPaintingOrigin() {

        return GameFrame.getInstance() != null && GameFrame.getInstance().getCapa_brillo().getBrightness() > 0f;
    }

    @Override
    protected void paintComponent(Graphics g) {

        boolean ok = false;

        do {

            try {
                super.paintComponent(g);

                if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {

                    // Single-image felt: see the secret_bg field doc above for why
                    // drawImage avoids seams under partial repaints from flying chips
                    // and cards.
                    if (secret_bg == null) {
                        try {
                            secret_bg = Helpers.toBufferedImage(Init.I1);
                        } catch (Exception ex) {
                            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    if (secret_bg != null) {
                        Graphics2D g2d = (Graphics2D) g;
                        Object old_interp = g2d.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2d.drawImage(secret_bg, 0, 0, getWidth(), getHeight(), null);
                        if (old_interp != null) {
                            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, old_interp);
                        }
                    }

                    ok = true;

                } else if (invalidate || tp == null) {

                    Helpers.threadRun(() -> {
                        synchronized (paint_lock) {
                            // The single-image felt (suffix "*") is painted above with
                            // drawImage and never reaches here; this rebuild is only for
                            // TILED texture felts (a small JPG repeated).
                            BufferedImage tile = null;
                            // try-with-resources: ImageIO.read(InputStream) does NOT
                            // close the stream (JDK contract). Every felt change used to
                            // leak the JAR resource handle until GC.
                            try (java.io.InputStream is = getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg")) {

                                tile = ImageIO.read(is);

                            } catch (Exception ex) {

                                try (java.io.InputStream isf = getClass().getResourceAsStream("/images/tapete_verde.jpg")) {
                                    tile = ImageIO.read(isf);
                                } catch (IOException ex1) {
                                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex1);
                                }
                            }
                            // Snapshots the previous tile for a DEFERRED post-repaint
                            // flush. The EDT reads tp without synchronization (no
                            // paint_lock), so flushing it here while the EDT's
                            // paintComponent is still painting with that same tp would
                            // produce an inconsistent render. invokeLater guarantees the
                            // flush runs only after painting with the old tp is done.
                            final java.awt.Image oldImage = (tp != null) ? tp.getImage() : null;
                            Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
                            tp = new TexturePaint(tile, tr);
                            invalidate = false;
                            Helpers.GUIRun(() -> {

                                revalidate();
                                repaint();
                                if (oldImage != null) {
                                    javax.swing.SwingUtilities.invokeLater(oldImage::flush);
                                }
                            });
                        }
                    });

                    if (tp != null) {

                        Graphics2D g2d = (Graphics2D) g;

                        g2d.setPaint(tp);

                        g2d.fill(getBounds());
                    }

                    ok = true;

                } else if (tp != null) {

                    Graphics2D g2d = (Graphics2D) g;

                    g2d.setPaint(tp);

                    g2d.fill(getBounds());

                    ok = true;
                }

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }

        } while (!ok);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                formMouseEntered(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
        // Mouse entering the felt = it left the fast-buttons bar: schedules its collapse
        // with a delay + fade-out (doesn't collapse instantly). scheduleHide is a no-op
        // if the bar is already collapsed.
        fastbuttons.scheduleHide();
    }//GEN-LAST:event_formMouseEntered

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        for (ZoomableInterface zoomeable : zoomables) {
            Helpers.threadRun(() -> {
                zoomeable.zoom(factor, mynotifier);
            });
        }

        // The check must be INSIDE the synchronized block: outside it, the last
        // zoomable could add()+notifyAll right between the size() check and the wait,
        // losing the notification and sleeping the full 1000ms -> a random ~1s stall on
        // startup (initial zoom) while the zoomables rescale.
        synchronized (mynotifier) {
            while (mynotifier.size() < zoomables.length) {
                try {
                    mynotifier.wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
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

    public TapeteFastButtons getFastbuttons() {
        return fastbuttons;
    }

    /**
     * Zooms out (optionally after resetting to default zoom first) until every
     * player seat fits within the table bounds.
     *
     * @param reset if true, resets to the default zoom level before zooming out
     * again
     */
    public void autoZoom(boolean reset) {
        if (java.awt.EventQueue.isDispatchThread()) {
            Helpers.threadRun(() -> autoZoom(reset));
            return;
        }

        if (!auto_zoom_running.compareAndSet(false, true)) {
            return;
        }

        try {
            GameFrame frame = GameFrame.getInstance();
            if (frame == null || !hasOverflowingPlayer()) {
                return;
            }

            boolean changed = false;
            if (reset && GameFrame.ZOOM_LEVEL != GameFrame.DEFAULT_ZOOM_LEVEL) {
                changed = frame.applyZoomLevelSynchronouslyForAutoZoom(GameFrame.DEFAULT_ZOOM_LEVEL);
                if (changed) {
                    settleLayoutAfterAutoZoom();
                }
            }

            while (hasOverflowingPlayer()) {
                int next_level = GameFrame.ZOOM_LEVEL - 1;
                if (!frame.applyZoomLevelSynchronouslyForAutoZoom(next_level)) {
                    break;
                }
                changed = true;
                settleLayoutAfterAutoZoom();
            }

            // Auto-fit can perform several adjacent level changes. Persist once after the final
            // level instead of rewriting the whole preferences file for every step.
            if (changed) {
                Helpers.savePropertiesFileDeferred();
            }
        } finally {
            auto_zoom_running.set(false);
        }
    }

    /**
     * Reads all seat bounds on the EDT, after any queued zoom UI work has been
     * applied.
     */
    private boolean hasOverflowingPlayer() {
        final boolean[] overflowing = new boolean[]{false};
        Helpers.GUIRunAndWait(() -> {
            if (!isShowing() || getPlayers() == null) {
                return;
            }

            try {
                java.awt.Point table_location = getLocationOnScreen();
                double table_bottom = table_location.getY() + getHeight();
                double table_right = table_location.getX() + getWidth();

                for (Player jugador : getPlayers()) {
                    if (!(jugador instanceof JPanel) || !((JPanel) jugador).isShowing()) {
                        continue;
                    }

                    java.awt.Point player_location = ((JPanel) jugador).getLocationOnScreen();
                    double player_bottom = player_location.getY() + ((JPanel) jugador).getHeight();
                    double player_right = player_location.getX() + ((JPanel) jugador).getWidth();
                    if (player_bottom > table_bottom || player_right > table_right) {
                        overflowing[0] = true;
                        return;
                    }
                }
            } catch (java.awt.IllegalComponentStateException ex) {
                // The table is transitioning between native peers (e.g. fullscreen). A later
                // resize/fullscreen callback will retry once the hierarchy is showing again.
            }
        });
        return overflowing[0];
    }

    /**
     * Flushes queued zoom mutations and validates the hierarchy before the next
     * bounds read.
     */
    private void settleLayoutAfterAutoZoom() {
        Helpers.GUIRunAndWait(() -> {
            revalidate();
            java.awt.Container root = getRootPane();
            if (root != null) {
                root.validate();
            } else {
                validate();
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
