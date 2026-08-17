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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

/**
 * Magnifier for a seat's avatar: hovering over it for HOVER_DELAY_MS shows the
 * same avatar ENLARGED in an overlay centered on the original, removed as soon
 * as the pointer leaves.
 *
 * The overlay lives in the table's DRAG layer (above the whole board) and is
 * transparent to mouse events (contains always returns false), so covering the
 * avatar steals neither its hover nor the identicon's click.
 *
 * The enlargement is NOT scaled up from the seat icon (~64 px): it is rebuilt
 * from the original avatar file, kept intact, downscaling in steps with bicubic
 * interpolation. Default and bot avatars are 200x200, so at the usual sizes the
 * magnifier does not invent detail.
 *
 * @author tonikelope
 */
public final class AvatarZoomOverlay extends javax.swing.JComponent {

    // How long the pointer must stay inside the avatar before the magnifier shows:
    // sweeping the pointer across the table shouldn't light up every avatar along
    // the way, only the one actually being looked at. The first time for a given
    // avatar also costs whatever it takes to decode its file (off the EDT); after
    // that it comes from cache, within the same timer tick.
    public static final int HOVER_DELAY_MS = 250;

    // Size of the enlargement: N times the seat avatar's height, capped at a
    // fraction of the table's height so small windows (or a high zoom level)
    // don't have it swallow the table.
    private static final float ZOOM_FACTOR = 2f;
    private static final float MAX_TABLE_FRACTION = 0.45f;

    // Corner radius of the seat avatar (setAvatar), relative to its height: the
    // magnifier keeps it proportional so it reads as "the same" avatar.
    private static final int SEAT_CORNER_RADIUS = 20;
    private static final int SEAT_CORNER_REFERENCE = 64;

    // Dark frame around the image: separates the magnifier from the table and
    // the cards when the avatar's tones are similar.
    private static final float FRAME_ALPHA = 0.9f;

    // Poll interval of the watchdog that removes the magnifier. It polls the
    // pointer position instead of listening for events because the overlay never
    // receives them (it's transparent to the mouse) and the avatar's mouseExited
    // is useless: it fires the moment the pointer crosses onto the enlargement,
    // and never fires at all if the seat gets hidden under the pointer (hand
    // change, compact view, game end).
    private static final int POLL_MS = 100;

    // Already-generated enlargements. Soft-referenced: they survive the whole
    // game (rebuilding on every hover would stutter), but the GC can reclaim
    // them under memory pressure, and they regenerate on demand.
    private static final ConcurrentHashMap<String, java.lang.ref.SoftReference<BufferedImage>> CACHE = new ConcurrentHashMap<>();

    private static volatile AvatarZoomOverlay current = null;
    private static volatile JLabel current_avatar = null;
    private static javax.swing.Timer watchdog = null;

    private final BufferedImage image;
    private final int pad;

    // Player's stack (next to the photo) and nick (below it). The stack is painted
    // with the seat's font scaled by the same factor as the image; its text, text
    // color and highlight pill (green/cyan/yellow/gray) are read LIVE from the seat
    // on every paint (not copied once on open): the stack and its state can change
    // while the magnifier is up (a bet, a rebuy). The factor is kept to round the
    // pill the same way the seat does.
    private final JLabel stack;
    private final java.awt.Font stack_font;
    private final float factor;
    private String painted_stack = null;

    private final JLabel name;
    private final java.awt.Font name_font;
    private final Color name_color;
    private String painted_name = null;

    private AvatarZoomOverlay(BufferedImage image, int pad, JLabel stack, JLabel name, float factor) {
        this.image = image;
        this.pad = pad;
        this.stack = stack;
        this.factor = factor;
        this.stack_font = stack != null && stack.getFont() != null
                ? stack.getFont().deriveFont(stack.getFont().getSize2D() * factor) : null;
        this.painted_stack = stackText();
        this.name = name;
        this.name_color = name != null ? name.getForeground() : null;
        // The nick is NOT enlarged: it already reads fine at the seat's font size,
        // and scaling it by the photo's factor could nearly double the magnifier's
        // width for text that isn't the point of the zoom.
        this.name_font = name != null ? name.getFont() : null;
        this.painted_name = nameText();
        setOpaque(false);
        setFocusable(false);
        setSize(preferredBox());

        // The enlargement is SOLID to the mouse: it consumes clicks landing on it
        // instead of passing them through to whatever it covers (the seat's nick,
        // the table...). Since it's the same avatar just bigger, the click is
        // forwarded to the original: opens the identicon for a human, does nothing
        // for a bot, exactly like clicking the small one. The overlay eating the
        // avatar's events doesn't affect removal: that's decided by the watchdog,
        // which polls the pointer position and doesn't depend on entered/exited.
        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                redispatchToAvatar(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                redispatchToAvatar(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                redispatchToAvatar(e);
            }
        });
    }

    // Forwards the event to the avatar the magnifier is enlarging, aimed at its
    // center: clicking anywhere on the large image is equivalent to clicking the
    // avatar, so "released inside the component" guards are satisfied.
    private void redispatchToAvatar(MouseEvent e) {

        JLabel target = current_avatar;

        if (target == null || !target.isShowing() || target.getWidth() <= 0) {
            return;
        }

        target.dispatchEvent(new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(),
                target.getWidth() / 2, target.getHeight() / 2, e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }

    // The stack text as currently shown by the seat, or null if that seat has no
    // stack to show.
    private String stackText() {

        if (stack == null || stack_font == null) {
            return null;
        }

        String text = stack.getText();

        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    // Color of the stack's highlight pill (green normal, cyan rebuy, yellow
    // pending, gray eliminated) as the seat shows it RIGHT NOW. It's painted by
    // the RoundedPanel wrapping the stack label: its background is the color and
    // the label is non-opaque, so it's read from the panel (the label's parent),
    // not the label itself. null if that panel isn't painting a fill.
    private Color stackPillColor() {

        if (stack == null) {
            return null;
        }

        java.awt.Container parent = stack.getParent();

        if (parent instanceof RoundedPanel && ((RoundedPanel) parent).isRoundedFill()) {
            return parent.getBackground();
        }

        return null;
    }

    // The nick as shown by the seat, or null if there is none.
    private String nameText() {

        if (name == null || name_font == null) {
            return null;
        }

        String text = name.getText();

        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    // The PHOTO rules: it is ALWAYS flush against the magnifier's left edge, with
    // the stack to its right and the nick below. Neither the nick nor the stack
    // ever move it, so the image lands exactly over the seat's avatar regardless
    // of how long the texts are.
    private int imageOffsetX() {
        return pad;
    }

    private int nameWidth() {
        return painted_name != null ? getFontMetrics(name_font).stringWidth(painted_name) : 0;
    }

    // The nick is centered under the photo while it fits; if longer, it starts
    // aligned with the photo and overflows to the right.
    private int nameOffsetX() {
        return pad + Math.max(0, (image.getWidth() - nameWidth()) / 2);
    }

    // The stack starts at 2*pad from the photo: one pad of black gap, plus one
    // pad for the pill's left padding, so the pill sits off the photo with the
    // same margin that frames the photo itself.
    private int stackOffsetX() {
        return pad + image.getWidth() + 2 * pad;
    }

    // Bounding box of the magnifier: the photo with the stack beside it and the
    // nick below.
    private java.awt.Dimension preferredBox() {

        int w = image.getWidth() + 2 * pad;
        int h = image.getHeight() + 2 * pad;

        if (painted_stack != null) {
            java.awt.FontMetrics fm = getFontMetrics(stack_font);
            // +2*pad: the pill's right padding plus the black gap after it.
            w = Math.max(w, stackOffsetX() + fm.stringWidth(painted_stack) + 2 * pad);
            h = Math.max(h, fm.getHeight() + 2 * pad);
        }

        if (painted_name != null) {
            java.awt.FontMetrics fm = getFontMetrics(name_font);
            w = Math.max(w, nameOffsetX() + nameWidth() + pad);
            h = Math.max(h, pad + image.getHeight() + fm.getHeight() + pad);
        }

        return new java.awt.Dimension(w, h);
    }

    /**
     * Resizes the magnifier if the stack or nick changed while it was up (a
     * bet, a pot being awarded). If the box grows enough to push it off the
     * table, relocates it back inside.
     */
    private void refreshIfChanged() {

        String new_stack = stackText();
        String new_name = nameText();

        if (java.util.Objects.equals(new_stack, painted_stack) && java.util.Objects.equals(new_name, painted_name)) {
            return;
        }

        painted_stack = new_stack;
        painted_name = new_name;

        java.awt.Dimension box = preferredBox();

        if (!box.equals(getSize())) {
            setSize(box);
            java.awt.Container parent = getParent();
            if (parent != null) {
                setLocation(Math.max(0, Math.min(getX(), parent.getWidth() - getWidth())),
                        Math.max(0, Math.min(getY(), parent.getHeight() - getHeight())));
            }
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(new Color(0f, 0f, 0f, FRAME_ALPHA));
            int arc = cornerRadius(image.getWidth()) + pad;
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));

            g2.drawImage(image, imageOffsetX(), pad, null);

            if (painted_name != null) {
                g2.setFont(name_font);
                g2.setColor(name_color != null ? name_color : Color.WHITE);
                g2.drawString(painted_name, nameOffsetX(), pad + image.getHeight() + g2.getFontMetrics().getAscent());
            }

            if (painted_stack != null) {
                g2.setFont(stack_font);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                // Vertically centered on the PHOTO, not the component: the nick
                // below must not drag the number down.
                int text_top = pad + (image.getHeight() - fm.getHeight()) / 2;
                int baseline = text_top + fm.getAscent();
                int text_x = stackOffsetX();
                int text_w = fm.stringWidth(painted_stack);

                // Same highlight pill as the seat, behind the number. The gap
                // before it (stackOffsetX = photo + 2*pad) leaves room for the
                // pill's left edge without touching the photo, and preferredBox
                // already reserves the right margin, so the pill fits entirely
                // inside the box.
                Color pill = stackPillColor();

                if (pill != null) {
                    int hpad = pad;
                    int vpad = Math.max(2, pad / 2);
                    int pill_arc = Math.min(fm.getHeight() + 2 * vpad,
                            Math.round(RoundedPanel.DEFAULT_ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * factor));
                    g2.setColor(pill);
                    g2.fill(new RoundRectangle2D.Float(text_x - hpad, text_top - vpad,
                            text_w + 2 * hpad, fm.getHeight() + 2 * vpad, pill_arc, pill_arc));
                }

                // Text color read LIVE from the seat: white on green, black on
                // cyan/yellow. Reading it here (not caching it) keeps it legible if
                // the pill changes color while the magnifier is up.
                Color fg = stack.getForeground();
                g2.setColor(fg != null ? fg : Color.WHITE);
                g2.drawString(painted_stack, text_x, baseline);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Attaches the magnifier to a seat's avatar, with that seat's stack shown
     * enlarged next to the photo. The supplier returns the SAME string
     * setAvatar uses to paint the seat (file path, "*" for a bot, or "" for the
     * default avatar) and is queried at show time, not here: at install time
     * the seat doesn't have a nick yet.
     *
     * @param avatar the seat's avatar label to watch for hover
     * @param stack the seat's stack label, enlarged alongside the photo
     * @param name the seat's nick label, shown below the photo
     * @param source supplies the avatar source string at show time
     */
    public static void install(final JLabel avatar, final JLabel stack, final JLabel name, final Supplier<String> source) {

        final javax.swing.Timer[] delay = new javax.swing.Timer[1];

        delay[0] = new javax.swing.Timer(HOVER_DELAY_MS, e -> {
            delay[0].stop();
            show(avatar, stack, name, source);
        });

        delay[0].setRepeats(false);

        avatar.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!canShow()) {
                    return;
                }
                delay[0].restart();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Only cancels the pending appearance. If the magnifier is ALREADY
                // up, leaving the avatar doesn't remove it: the enlargement is
                // bigger than the avatar and the pointer is still inside it.
                // Removal is decided by the watchdog, which watches the whole area
                // (avatar + enlargement).
                delay[0].stop();
            }
        });
    }

    // Whether the magnifier is allowed to show: enabled in settings and the game
    // is still alive. Checked on every hover AND every watchdog poll, so turning
    // it off while one is up removes it right away.
    private static boolean canShow() {
        GameFrame gf = GameFrame.getInstance();
        return GameFrame.RESALTAR_AVATARES && gf != null && gf.getCrupier() != null && !gf.getCrupier().isFin_de_la_transmision();
    }

    /**
     * Builds the enlargement OFF the EDT (decoding the original file and
     * rescaling it costs real time the first time for each avatar) and shows it
     * afterward, if the pointer is still on the same avatar by then.
     */
    private static void show(final JLabel avatar, final JLabel stack, final JLabel name, final Supplier<String> source) {

        if (!canShow() || !avatar.isShowing() || !pointerOver(avatar)) {
            return;
        }

        final JLayeredPane tapete = GameFrame.getInstance().getTapete();

        if (tapete == null || tapete.getHeight() <= 0) {
            return;
        }

        final int size = zoomSize(avatar, tapete);

        // If the table's cap shrinks the "enlargement" down to the avatar's own
        // size (very short window, very high zoom), there's nothing to enlarge.
        if (size <= Math.min(avatar.getWidth(), avatar.getHeight())) {
            return;
        }

        final String src;

        try {
            src = source.get();
        } catch (Exception ex) {
            // The seat has nothing yet to resolve its avatar from (waiting room
            // not wired up, nick not assigned): no magnifier, and no noise.
            return;
        }

        // Already generated: painted right within this mouse event, with no
        // bouncing through a thread and back to the EDT. This is the common case
        // except the first time for each avatar.
        final BufferedImage cached = cachedImage(src, size);

        if (cached != null) {
            display(avatar, stack, name, tapete, cached, size);
            return;
        }

        Helpers.threadRun(() -> {

            final BufferedImage img = zoomedImage(src, size);

            if (img == null) {
                return;
            }

            Helpers.GUIRun(() -> display(avatar, stack, name, tapete, img, size));
        });
    }

    // Positions the enlargement centered over its avatar. EDT only.
    private static void display(final JLabel avatar, final JLabel stack, final JLabel name, final JLayeredPane tapete, final BufferedImage img, final int size) {

        if (!canShow() || !avatar.isShowing() || !pointerOver(avatar)) {
            return;
        }

        hideZoom();

        int seat = Math.min(avatar.getWidth(), avatar.getHeight());

        AvatarZoomOverlay overlay = new AvatarZoomOverlay(img, Math.max(4, size / 24), stack, name,
                seat > 0 ? size / (float) seat : ZOOM_FACTOR);

        // Same cursor as the avatar it enlarges: if clicking the small one does
        // something (identicon), the large image signals it the same way; for a
        // bot, a normal cursor on both.
        overlay.setCursor(avatar.getCursor());

        // The magnifier covers the avatar, so it inherits its tooltip: hovering
        // over the large image still explains what the click does. The avatar's
        // own tooltip is left untouched (it can't surface anyway while the
        // magnifier is up).
        overlay.setToolTipText(avatar.getToolTipText());

        Point p = SwingUtilities.convertPoint(avatar, 0, 0, tapete);

        // The PHOTO is centered on the original avatar (the stack extends to its
        // right and the nick hangs below), and the whole thing is clamped to the
        // table so corner seats still show it in full.
        int x = p.x + avatar.getWidth() / 2 - (overlay.imageOffsetX() + img.getWidth() / 2);
        int y = p.y + avatar.getHeight() / 2 - (overlay.pad + img.getHeight() / 2);

        x = Math.max(0, Math.min(x, tapete.getWidth() - overlay.getWidth()));
        y = Math.max(0, Math.min(y, tapete.getHeight() - overlay.getHeight()));

        overlay.setLocation(x, y);

        tapete.add(overlay, JLayeredPane.DRAG_LAYER);

        current = overlay;
        current_avatar = avatar;

        startWatchdog();

        // Just its rectangle: showing the magnifier shouldn't cost a repaint of
        // the whole table.
        tapete.repaint(x, y, overlay.getWidth(), overlay.getHeight());
    }

    /**
     * Removes the magnifier. Idempotent and safe from any thread.
     */
    public static void hideZoom() {

        Helpers.GUIRun(() -> {

            if (watchdog != null) {
                watchdog.stop();
            }

            AvatarZoomOverlay overlay = current;

            if (overlay != null) {
                java.awt.Container parent = overlay.getParent();
                if (parent != null) {
                    Rectangle bounds = overlay.getBounds();
                    parent.remove(overlay);
                    parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
                }
                current = null;
            }

            current_avatar = null;
        });
    }

    // While the magnifier is up, checks that the pointer is still within its area
    // (the avatar PLUS the enlargement itself). This is the only way to remove
    // it: the overlay covers the avatar so entered/exited aren't usable there,
    // and the avatar's exited fires exactly when crossing onto the enlargement,
    // which is precisely when it must NOT be removed. Also refreshes the stack if
    // it changed while the magnifier was up.
    private static void startWatchdog() {

        if (watchdog == null) {
            watchdog = new javax.swing.Timer(POLL_MS, e -> {
                JLabel avatar = current_avatar;
                if (avatar == null || !avatar.isShowing() || !canShow() || !pointerInHoverArea(avatar)) {
                    hideZoom();
                    return;
                }
                AvatarZoomOverlay overlay = current;
                if (overlay != null) {
                    overlay.refreshIfChanged();
                }
            });
        }

        watchdog.start();
    }

    // Whether the pointer is over the avatar. Uses MouseInfo (screen coordinates)
    // to avoid depending on an event actually arriving.
    private static boolean pointerOver(java.awt.Component c) {

        if (c == null || !c.isShowing()) {
            return false;
        }

        try {
            java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();

            if (pi == null) {
                return false;
            }

            return new Rectangle(c.getLocationOnScreen(), c.getSize()).contains(pi.getLocation());

        } catch (java.awt.IllegalComponentStateException ex) {
            // The component stopped being on-screen between the isShowing check
            // and this query: counts as the pointer being outside.
            return false;
        }
    }

    // Area that keeps the magnifier alive: the avatar or the enlargement coming
    // off it.
    private static boolean pointerInHoverArea(JLabel avatar) {
        return pointerOver(avatar) || pointerOver(current);
    }

    // Side length of the enlargement: ZOOM_FACTOR times the seat avatar, capped
    // at MAX_TABLE_FRACTION of the table's height.
    private static int zoomSize(JLabel avatar, JLayeredPane tapete) {

        int seat = Math.min(avatar.getWidth(), avatar.getHeight());

        if (seat <= 0) {
            return 0;
        }

        int size = Math.round(seat * ZOOM_FACTOR);

        return Math.min(size, Math.round(tapete.getHeight() * MAX_TABLE_FRACTION));
    }

    private static int cornerRadius(int size) {
        return Math.max(1, Math.round(SEAT_CORNER_RADIUS * size / (float) SEAT_CORNER_REFERENCE));
    }

    private static String cacheKey(String src, int size) {
        return (src != null ? src : "") + "@" + size;
    }

    // The already-generated enlargement, or null if it still needs to be built.
    // Cheap: used to decide whether to paint immediately or leave the EDT.
    private static BufferedImage cachedImage(String src, int size) {
        java.lang.ref.SoftReference<BufferedImage> ref = CACHE.get(cacheKey(src, size));
        return ref != null ? ref.get() : null;
    }

    // Enlargement cached by (source, size): the same avatar is only decoded and
    // rescaled once per size, and size only changes with the zoom level.
    private static BufferedImage zoomedImage(String src, int size) {

        String key = cacheKey(src, size);

        BufferedImage cached = cachedImage(src, size);

        if (cached != null) {
            return cached;
        }

        BufferedImage original = readOriginal(src);

        if (original == null) {
            return null;
        }

        BufferedImage zoomed = Helpers.makeImageRoundedCorner(scale(original, size), cornerRadius(size));

        CACHE.put(key, new java.lang.ref.SoftReference<>(zoomed));

        return zoomed;
    }

    // The avatar's original file exactly as its owner uploaded it (up to 256 KB),
    // or the bundled resource when the seat is a bot ("*") or has no avatar ("").
    private static BufferedImage readOriginal(String src) {

        try {
            if (src == null || src.isEmpty()) {
                return readResource("/images/avatar_default.png");
            }

            if ("*".equals(src)) {
                return readResource("/images/avatar_bot.png");
            }

            File f = new File(src);

            if (!f.exists() || !f.canRead()) {
                return readResource("/images/avatar_default.png");
            }

            return ImageIO.read(f);

        } catch (Exception ex) {
            Logger.getLogger(AvatarZoomOverlay.class.getName()).log(Level.WARNING, "Could not read avatar image for zoom overlay", ex);
            return null;
        }
    }

    private static BufferedImage readResource(String path) throws java.io.IOException {
        URL url = AvatarZoomOverlay.class.getResource(path);
        return url != null ? ImageIO.read(url) : null;
    }

    /**
     * Quality downscaling: bicubic, halving step by step while the image is
     * more than twice the target size (shrinking a large photo in one jump
     * loses detail and leaves hard edges).
     */
    private static BufferedImage scale(BufferedImage src, int size) {

        BufferedImage current_img = src;

        int w = src.getWidth();
        int h = src.getHeight();

        while (w > 2 * size && h > 2 * size) {
            w = Math.max(size, w / 2);
            h = Math.max(size, h / 2);
            current_img = drawScaled(current_img, w, h);
        }

        return drawScaled(current_img, size, size);
    }

    private static BufferedImage drawScaled(Image src, int w, int h) {

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = dst.createGraphics();

        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(src, 0, 0, w, h, null);
        } finally {
            g2.dispose();
        }

        return dst;
    }
}
