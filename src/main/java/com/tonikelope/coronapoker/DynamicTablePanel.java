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

import java.awt.Container;
import java.awt.Dimension;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * Single dynamic table panel that positions the N players by geometry, replacing
 * the 9 fixed .form panels (TablePanel2..TablePanel10).
 *
 * The seat positions are not invented: they were extracted from the original
 * panels (by instantiating them and reading where their GroupLayout placed each
 * seat) and stored as FRACTIONS of the table's width/height (see {@link #ANCHORS}).
 * That reproduces their exact layout - including the equal side gaps, the bottom
 * corners at 8 players, and the off-center local seat at 10 - and, being
 * fractions, scales to any resolution/window size.
 *
 * All the shared logic (deal/chip animations, overlays, zoom, autoZoom, felt
 * painting) lives in the base class {@link TablePanel} and is geometry-agnostic
 * (it reads actual on-screen positions), so this panel only needs to create the
 * seats and place them; everything else works unmodified.
 *
 * @author tonikelope
 */
public class DynamicTablePanel extends TablePanel {

    // Gap (px) between a seat's outer edge and the table edge. Seats are PINNED to
    // their nearest edge with this margin (not anchored by center), so they use the
    // available space and stay pinned even when they shrink (compact view). Kept
    // small on purpose: the seat itself already has a rounded, padded border, which
    // is the minimum visible air even when the panel touches the table edge.
    private static final int EDGE_MARGIN = 5;

    // Anchors by player count (index = number of players, 2..10). Each row is a seat
    // in the ORDER of the players array: index 0 = local player, 1..N-1 = remotes
    // (remotePlayer1, remotePlayer2, ...). Values are {fx, fy}, the CENTER of the
    // seat as a fraction of the table's width/height. The last row (index N) is the
    // center of the COMMUNITY cards.
    //
    // Extracted from TablePanel2..TablePanel10 at 2560x1440 (16:9) with SeatLayoutExtractor.
    private static final double[][][] ANCHORS = new double[11][][];

    static {
        ANCHORS[2] = new double[][]{
            {0.5000, 0.8368}, // local
            {0.5000, 0.1590}, // r1
            {0.4242, 0.5000}, // community
        };
        ANCHORS[3] = new double[][]{
            {0.5000, 0.8368}, // local
            {0.0805, 0.1674}, // r1
            {0.9195, 0.1674}, // r2
            {0.5000, 0.5021}, // community
        };
        ANCHORS[4] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.5000}, // r1
            {0.5000, 0.1590}, // r2
            {0.9242, 0.5000}, // r3
            {0.3668, 0.5063}, // community
        };
        ANCHORS[5] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.5000}, // r1
            {0.3570, 0.1674}, // r2
            {0.6430, 0.1674}, // r3
            {0.9242, 0.5000}, // r4
            {0.3668, 0.5167}, // community
        };
        ANCHORS[6] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.7132}, // r1
            {0.0758, 0.2868}, // r2
            {0.5000, 0.1590}, // r3
            {0.9242, 0.2868}, // r4
            {0.9242, 0.7132}, // r5
            {0.3668, 0.5063}, // community
        };
        ANCHORS[7] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.7132}, // r1
            {0.0758, 0.2868}, // r2
            {0.3570, 0.1674}, // r3
            {0.6430, 0.1674}, // r4
            {0.9242, 0.2854}, // r5
            {0.9242, 0.7139}, // r6
            {0.3668, 0.5146}, // community
        };
        ANCHORS[8] = new double[][]{
            {0.4996, 0.8451}, // local
            {0.1879, 0.8410}, // r1
            {0.0805, 0.5000}, // r2
            {0.2172, 0.1590}, // r3
            {0.5008, 0.1590}, // r4
            {0.7832, 0.1590}, // r5
            {0.9195, 0.5000}, // r6
            {0.8113, 0.8410}, // r7
            {0.5012, 0.3410}, // community
        };
        ANCHORS[9] = new double[][]{
            {0.5000, 0.8368}, // local
            {0.0805, 0.8326}, // r1
            {0.0805, 0.5000}, // r2
            {0.0805, 0.1674}, // r3
            {0.3602, 0.1674}, // r4
            {0.6398, 0.1674}, // r5
            {0.9195, 0.1674}, // r6
            {0.9195, 0.5000}, // r7
            {0.9195, 0.8326}, // r8
            {0.5012, 0.5063}, // community
        };
        ANCHORS[10] = new double[][]{
            {0.3793, 0.8368}, // local
            {0.0805, 0.8326}, // r1
            {0.0805, 0.5000}, // r2
            {0.0805, 0.1674}, // r3
            {0.3602, 0.1674}, // r4
            {0.6398, 0.1674}, // r5
            {0.9195, 0.1674}, // r6
            {0.9195, 0.5000}, // r7
            {0.9195, 0.8326}, // r8
            {0.6781, 0.8326}, // r9
            {0.5012, 0.5063}, // community
        };
    }

    private volatile CommunityCardsPanel communityCards;
    private volatile LocalPlayer localPlayer;
    private volatile Player[] seats;

    // While the downgrade animation is running, doLayout() must not re-anchor the
    // seats (the tween moves them by hand with setBounds and they would fight).
    private volatile boolean layout_frozen = false;

    /**
     * Builds a table for {@code num_players} seats and lays them out by anchor.
     *
     * @param num_players seat count (2..10)
     */
    public DynamicTablePanel(int num_players) {

        // The base class (super()) has already set up its empty layout and added
        // the overlays to its layers (fastbuttons, central_label, shuffling_label,
        // call_cost_label). Here we create the seats and switch to manual placement.

        Helpers.GUIRunAndWait(() -> {

            // No layout manager: positioning is done by doLayout() via geometry.
            setLayout(null);

            CommunityCardsPanel community = new CommunityCardsPanel();
            LocalPlayer local = new LocalPlayer();

            RemotePlayer[] remotes = new RemotePlayer[num_players - 1];
            for (int i = 0; i < remotes.length; i++) {
                remotes[i] = new RemotePlayer();
            }

            Player[] all = new Player[num_players];
            all[0] = local;
            System.arraycopy(remotes, 0, all, 1, remotes.length);

            // Seats and community cards in the default layer (below the
            // overlays the base class adds in POPUP/PALETTE/DRAG).
            add(community, JLayeredPane.DEFAULT_LAYER);
            for (Player p : all) {
                add((JPanel) p, JLayeredPane.DEFAULT_LAYER);
            }

            // Set our own fields BEFORE revalidate(): doLayout() may fire during
            // revalidation and needs them (also null-guarded just in case).
            this.communityCards = community;
            this.localPlayer = local;
            this.seats = all;

            // Arrays consumed by the base class.
            players = all;
            remotePlayers = remotes;

            ZoomableInterface[] z = new ZoomableInterface[num_players + 2];
            for (int i = 0; i < num_players; i++) {
                z[i] = (ZoomableInterface) all[i];
            }
            z[num_players] = community;
            z[num_players + 1] = fastbuttons;
            zoomables = z;

            revalidate();
            repaint();
        });
    }

    @Override
    public CommunityCardsPanel getCommunityCards() {
        return communityCards;
    }

    @Override
    public LocalPlayer getLocalPlayer() {
        return localPlayer;
    }

    // Anchor-based placement: each seat (and the community cards) is centered on its
    // {fx, fy} position from the original N-player panel, scaled to the table's
    // CURRENT size. Called on every validation (table resize, revalidate after zoom).
    // Does NOT touch the upper-layer overlays (fastbuttons, central_label, etc.):
    // those are positioned by the base class.
    @Override
    public void doLayout() {

        // Frozen during the downgrade animation: the tween moves the seats with
        // setBounds and a doLayout would snap them back to their anchor (they'd fight).
        if (layout_frozen) {
            return;
        }

        final Player[] s = seats;
        final CommunityCardsPanel community = communityCards;

        final int W = getWidth();
        final int H = getHeight();

        if (s == null || community == null || W <= 0 || H <= 0) {
            return;
        }

        final int n = s.length;
        final double[][] anchors = (n < ANCHORS.length) ? ANCHORS[n] : null;
        if (anchors == null) {
            return;
        }

        // Seats (0 = local, 1..n-1 = remotes): each one is PINNED to its nearest edge
        // (per its anchor) with EDGE_MARGIN, using the anchor's perpendicular
        // coordinate to preserve the original's exact spacing. Anchoring by edge
        // instead of center keeps the seat pinned even as it shrinks (compact view).
        for (int i = 0; i < n; i++) {
            JPanel panel = (JPanel) s[i];
            java.awt.Rectangle r = seatBoundsFor(n, i, panel.getPreferredSize());
            if (r != null) {
                panel.setBounds(r);
            }
        }

        // Community cards: ALWAYS centered on the table by their CARD ROW, not by the
        // panel's bounds. CommunityCardsPanel is wider than the cards (it also carries
        // the pot and the controls); the cards sit CENTERED inside cards_panel (elastic
        // gaps on both sides), and cards_panel spans the panel's full width. We place
        // the panel so the CENTER of its cards_panel lands at (W/2, H/2).
        //
        // WHY THIS DOESN'T LOOP even though off_x = cards.width/2 depends on the panel's
        // own width: the offset is READ from a layout Swing already computed, and that
        // layout is a FIXED POINT. doLayout sets community to its OWN preferred size
        // (cd), and that preferred size doesn't depend on the size assigned to it (no
        // child reflows by width - all GroupLayout, single-line labels, no HTML/wrap) -
        // so its internal layout (and with it the card row's X) is stable across passes.
        // Converges in 2 passes; the 2nd only changes POSITION, which doesn't invalidate.
        // community.doLayout() is NEVER forced here: doing so re-laid-out its children
        // mid-pass, the preferred size oscillated, and hung the EDT on pause (infinite
        // loop -> full fullscreen freeze). That, not off_x depending on width, was the
        // actual mechanism behind the 22.58 hang.
        //
        // doLayout is IDEMPOTENT by design: it never touches anyone's preferred size,
        // and the setBounds below only fires when something actually changed; a
        // POSITION change doesn't invalidate (Component.reshape only invalidates on
        // resize), and SIZE only changes when the preferred size changes (a discrete
        // event: zoom, pause), which settles in one pass. So it can't feed back on
        // itself -> no loop is possible.
        //
        // HORIZONTAL: anchored to the card row (fixed point, converges in 2 passes).
        // This is the fix for the original off-center layout, and it does NOT move on
        // pause/last-hand (the banner only changes the panel's HEIGHT, not the card
        // row's X).
        //
        // VERTICAL: anchored to the panel's BOUNDS (cd.height/2), NOT the card row. In
        // normal play the cards are ALREADY at the panel's vertical center (measured
        // ~1px off), so they don't move. But anchoring by the card row made the whole
        // community panel reposition to re-center the cards whenever the "LAST HAND"
        // banner appeared/disappeared (it pushes the cards down) -> the entire
        // community flickered. Anchoring by bounds instead lets the panel only
        // GROW/SHRINK symmetrically in place (no jump), in ONE pass (off_y doesn't
        // depend on the cards already being placed, so there's no stale read or
        // double repositioning).
        Dimension cd = community.getPreferredSize();
        double off_x = cd.width / 2.0;
        double off_y = cd.height / 2.0;
        JPanel cards = community.getCards_panel();
        if (cards != null && cards.getWidth() > 0) {
            java.awt.Point p = javax.swing.SwingUtilities.convertPoint(
                    cards, cards.getWidth() / 2, cards.getHeight() / 2, community);
            off_x = p.x;
        }
        int comm_x = (int) Math.round(W / 2.0 - off_x);
        int comm_y = (int) Math.round(H / 2.0 - off_y);
        if (community.getX() != comm_x || community.getY() != comm_y
                || community.getWidth() != cd.width || community.getHeight() != cd.height) {
            community.setBounds(comm_x, comm_y, cd.width, cd.height);
        }
    }

    // Bounds (edge-pinned model) for seat 'index' at a table of 'total' players, for
    // a seat of size 'd', at the table's current size. Reused by doLayout() and by
    // the downgrade animation (which needs to know where each survivor will land at
    // the M-player table). Returns null if there are no anchors for that total/index.
    private java.awt.Rectangle seatBoundsFor(int total, int index, Dimension d) {
        final int W = getWidth();
        final int H = getHeight();
        final double[][] anchors = (total >= 0 && total < ANCHORS.length) ? ANCHORS[total] : null;
        if (anchors == null || index < 0 || index >= anchors.length) {
            return null;
        }
        double fx = anchors[index][0];
        double fy = anchors[index][1];

        double d_left = fx;
        double d_right = 1.0 - fx;
        double d_top = fy;
        double d_bottom = 1.0 - fy;
        double d_min = Math.min(Math.min(d_left, d_right), Math.min(d_top, d_bottom));

        double seat_cx;
        double seat_cy;
        if (d_min == d_left) {
            seat_cx = EDGE_MARGIN + d.width / 2.0;
            seat_cy = fy * H;
        } else if (d_min == d_right) {
            seat_cx = W - EDGE_MARGIN - d.width / 2.0;
            seat_cy = fy * H;
        } else if (d_min == d_top) {
            seat_cx = fx * W;
            seat_cy = EDGE_MARGIN + d.height / 2.0;
        } else {
            seat_cx = fx * W;
            seat_cy = H - EDGE_MARGIN - d.height / 2.0;
        }

        return new java.awt.Rectangle((int) Math.round(seat_cx - d.width / 2.0),
                (int) Math.round(seat_cy - d.height / 2.0), d.width, d.height);
    }

    /**
     * Animates the N-to-M player transition when someone leaves: departing seats
     * FADE OUT (a snapshot ghost, alpha 1-&gt;0) and survivors SLIDE from their current
     * position (N-player table) to their slot at the M-player table, preserving ring
     * order. Works for one or several departures at once. Blocks the caller (dealer
     * thread, NEVER the EDT) until done. Meant to be called RIGHT BEFORE the table
     * swap ({@code downgradeAndRefreshTapete}): once finished, survivors sit at the
     * M-player positions, which is where the new table will place its copies, so the
     * swap is imperceptible. Doesn't touch game logic (player arrays): purely visual.
     *
     * @param duration_ms animation duration in milliseconds
     */
    public void animateDowngrade(int duration_ms) {

        final Player[] all = players;
        if (all == null) {
            return;
        }

        // KNOWN TOCTOU (left in on purpose; cosmetic and SELF-CORRECTING): this read of
        // isExit() (T1) and the one TablePanelFactory.downgradePanel does when rebuilding
        // the table (T2, ~500ms later) are TWO separate reads. isExit() can flip to true
        // in between: it's set by RemotePlayer.setExit() (a volatile flag, NOT under the
        // dealer's monitor) via Participant.markExitAndNotify(), which runs on
        // watchdog/writer threads (timeout, closed socket, auto-kick). Since exit is
        // monotonic, leaving(T2) is a superset of leaving(T1). CONSEQUENCE BOUNDED TO
        // VISUALS: if a 2nd player drops during the animation, its seat slides as a
        // survivor, but the new table (T2) no longer includes it -> a ONE-FRAME jump at
        // swap time, after which the table is correct (downgradePanel is internally
        // consistent: size and copying use the SAME T2 read). If fewer than 2 players
        // remain there's no swap and the table doesn't re-anchor, but that's end of game
        // and the balance screen covers it within seconds. NEVER affects money/nick/seat
        // (this animation is purely visual) and self-corrects. Not hardened with a single
        // snapshot because that would touch the sensitive kick path in exchange for an
        // extreme and harmless edge case (2 drops within ~500ms). (Adversarial 8-lens
        // audit, Jul 2026.)
        final java.util.List<JPanel> survivors = new java.util.ArrayList<>();
        final java.util.List<JPanel> leaving = new java.util.ArrayList<>();
        for (Player p : all) {
            if ((p instanceof RemotePlayer) && ((RemotePlayer) p).isExit()) {
                leaving.add((JPanel) p);
            } else {
                survivors.add((JPanel) p);
            }
        }

        final int m = survivors.size();
        final int n = all.length;
        if (m < 2 || m >= n || m >= ANCHORS.length || ANCHORS[m] == null
                || getWidth() <= 0 || getHeight() <= 0 || !isShowing()) {
            return; // nothing to animate (or outside the supported table range)
        }

        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final java.util.List<FadeGhost> ghosts = new java.util.ArrayList<>();

        Helpers.GUIRunAndWait(() -> {
            try {
                // Freeze re-anchoring: from here on, positions are driven by the tween.
                layout_frozen = true;

                final java.awt.Rectangle[] from = new java.awt.Rectangle[m];
                final java.awt.Rectangle[] to = new java.awt.Rectangle[m];
                for (int j = 0; j < m; j++) {
                    JPanel sv = survivors.get(j);
                    from[j] = sv.getBounds();
                    java.awt.Rectangle t = seatBoundsFor(m, j, sv.getPreferredSize());
                    to[j] = (t != null) ? t : from[j];
                }

                // A ghost (snapshot) per departing seat to fade it out, hiding the
                // real seat underneath.
                for (JPanel lv : leaving) {
                    if (lv.getWidth() <= 0 || lv.getHeight() <= 0) {
                        continue;
                    }
                    java.awt.image.BufferedImage snap = new java.awt.image.BufferedImage(
                            lv.getWidth(), lv.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = snap.createGraphics();
                    lv.paint(g);
                    g.dispose();
                    FadeGhost ghost = new FadeGhost(snap);
                    ghost.setBounds(lv.getBounds());
                    add(ghost, JLayeredPane.DRAG_LAYER);
                    ghosts.add(ghost);
                    lv.setVisible(false);
                }

                final long t0 = System.nanoTime();
                final javax.swing.Timer timer = new javax.swing.Timer(15, null);
                holder[0] = timer;
                timer.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, duration_ms));
                    double s = u * u * (3.0 - 2.0 * u); // smoothstep (smooth ease-in/ease-out)

                    for (int j = 0; j < m; j++) {
                        java.awt.Rectangle a = from[j];
                        java.awt.Rectangle b = to[j];
                        int x = (int) Math.round(a.x + (b.x - a.x) * s);
                        int y = (int) Math.round(a.y + (b.y - a.y) * s);
                        survivors.get(j).setBounds(x, y, a.width, a.height);
                    }
                    for (FadeGhost ghost : ghosts) {
                        ghost.setAlpha((float) (1.0 - u));
                        ghost.repaint();
                    }

                    if (u >= 1.0) {
                        timer.stop();
                        finished.countDown();
                    }
                });
                timer.start();

            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(DynamicTablePanel.class.getName())
                        .log(java.util.logging.Level.SEVERE, null, ex);
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        // Cleanup: remove the ghosts. Survivors are left at the destination position
        // (M-player table). The layout is NOT unfrozen: this panel gets discarded in
        // the swap that follows, and unfreezing could trigger a doLayout that snaps
        // them back to the N-player table (flicker) right before the swap.
        Helpers.GUIRunAndWait(() -> {
            for (FadeGhost ghost : ghosts) {
                remove(ghost);
            }
        });

        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    // Short-lived component that paints a snapshot (image) at variable opacity, to
    // fade out a seat that's leaving the table.
    private static final class FadeGhost extends javax.swing.JComponent {

        private final java.awt.Image img;
        private volatile float alpha = 1.0f;

        FadeGhost(java.awt.Image img) {
            this.img = img;
            setOpaque(false);
        }

        void setAlpha(float a) {
            this.alpha = a;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
                g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            } finally {
                g2.dispose();
            }
        }
    }

    // After zooming, seats change preferred size: force a revalidation to
    // reposition them at the new size.
    @Override
    public void zoom(float factor, ConcurrentLinkedQueue<Long> notifier) {
        super.zoom(factor, notifier);
        Helpers.GUIRun(() -> {
            revalidate();
            repaint();
        });
    }

    // With no layout manager, the default preferred size would be (0,0). Since the
    // table goes in the content pane's CENTER (stretched by the frame), return the
    // parent's size when available, falling back to a design-time value.
    @Override
    public Dimension getPreferredSize() {
        Container parent = getParent();
        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            return new Dimension(parent.getWidth(), parent.getHeight());
        }
        return new Dimension(1200, 750);
    }
}
