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

import java.awt.Dimension;
import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;

/**
 * Swing component rendering a single playing card: face/back image, zoom,
 * rabbit-hunting overlay and the showdown highlight tint.
 *
 * @author tonikelope
 */
public class Card extends JLayeredPane implements ZoomableInterface, Comparable {

    public final static ConcurrentHashMap<String, Object[]> BARAJAS = new ConcurrentHashMap<>(Map.ofEntries(new HashMap.SimpleEntry<>("coronapoker", new Object[]{1.345f, false, null}), new HashMap.SimpleEntry<>("interstate60", new Object[]{1.345f, false, null}), new HashMap.SimpleEntry<>("goliat", new Object[]{1.345f, false, null}), new HashMap.SimpleEntry<>("goliat4", new Object[]{1.345f, false, null})));
    public final static int DEFAULT_HEIGHT = 200;
    public final static String[] PALOS = {"P", "C", "T", "D"};
    public final static String PALOS_STRING = "PCTD";
    public final static String[] VALORES = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    public final static int DEFAULT_CORNER = 20;
    public final static long DESTAPAR_SYNC_TIMEOUT = 5000;
    public static float DISABLED_CARD_OPACITY = 0.30f;
    private final static HashMap<String, String> UNICODE_TABLE = loadUnicodeTable();
    private static volatile int CARD_WIDTH = -1;
    private static volatile int CARD_HEIGHT = -1;
    private static volatile int CARD_CORNER = -1;
    private static volatile int RABBIT_OFF = 0;
    private static volatile int RABBIT_TAPADA = 1;
    private static volatile int RABBIT_DESTAPADA = 2;
    private static volatile ImageIcon IMAGEN_TRASERA = null;
    private static volatile ImageIcon IMAGEN_TRASERA_B = null;
    private static volatile ImageIcon IMAGEN_JOKER = null;
    private static volatile ImageIcon IMAGEN_RABBIT_HUNTING = null;
    private static volatile ImageIcon IMAGEN_RABBIT_HUNTING_B = null;
    private static volatile ImageIcon IMAGEN_RABBIT_BB;
    private static volatile ImageIcon IMAGEN_RABBIT_SB;
    private static volatile List<String> CARTAS_SONIDO = null;
    private static volatile float CURRENT_ZOOM = 0f;
    private volatile String valor = "";
    private volatile String palo = "";
    private volatile boolean iniciada = false;
    private volatile boolean tapada = true;
    private volatile boolean desenfocada = false;
    // Semi-transparent yellow overlay painted over the card during showdown, on hovering a
    // losing player's hand label, to highlight which cards make up that hand
    // (RESALTAR_JUGADA_SHOWDOWN). Doesn't touch the image or focus state; actual painting happens
    // in paint(). Cleared in resetearCarta().
    private volatile boolean tinte_showdown = false;
    private final static java.awt.Color TINTE_SHOWDOWN_COLOR = new java.awt.Color(255, 236, 0, 80);
    private volatile boolean visible_card = false;
    private volatile boolean compactable = true;
    private volatile boolean gui = true;
    private volatile ImageIcon image = null;
    private volatile ImageIcon image_b = null;
    private volatile RemotePlayer iwtsth_candidate = null;
    private final Object image_precache_lock = new Object();
    private volatile boolean secure_hidden = false;
    private volatile int rabbit = RABBIT_OFF;
    private volatile boolean mouse_hover = false;

    // Global static caches to share images in memory across ALL cards
    private static final ConcurrentHashMap<String, ImageIcon> GLOBAL_FRONT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ImageIcon> GLOBAL_DISABLED_CACHE = new ConcurrentHashMap<>();

    public boolean isRabbitTapada() {
        return (rabbit == RABBIT_TAPADA);
    }

    public void taparRabbit() {
        rabbit = RABBIT_TAPADA;
        refreshCard();
    }

    public void destaparRabbit() {
        if (isRabbitTapada()) {
            rabbit = RABBIT_DESTAPADA;
            tapada = false;
            refreshCard();
        }
    }

    public boolean isSecure_hidden() {
        return secure_hidden;
    }

    public void setSecure_hidden(boolean secure_hidden) {
        this.secure_hidden = secure_hidden;
    }

    public void setIwtsth_candidate(RemotePlayer iwtsth_candidate) {
        this.iwtsth_candidate = iwtsth_candidate;
    }

    public boolean isIniciadaConValor() {
        return this.isIniciada() && !"".equals(this.valor) && !"".equals(this.palo);
    }

    public boolean isVisible_card() {
        return visible_card;
    }

    /**
     * Image currently shown by card_image (front face-up, back face-down). Used by animation
     * overlays (e.g. the hole-card sort swap) so the flying card is pixel-identical to the static
     * one.
     *
     * @return the current image, or {@code null} if no icon is set yet; must be called on the EDT
     */
    public java.awt.Image getDisplayedImage() {
        javax.swing.Icon ic = card_image.getIcon();
        return (ic instanceof ImageIcon) ? ((ImageIcon) ic).getImage() : null;
    }

    public void setVisibleCard(boolean v_card) {
        this.visible_card = v_card;

        if (!this.secure_hidden) {
            Helpers.GUIRun(() -> {
                card_image.setVisible(visible_card);
            });
        }
    }

    public void setCompactable(boolean compactable) {
        this.compactable = compactable;
    }

    public boolean isCompactable() {
        return compactable;
    }

    public static int getCardWidth() {
        return CARD_WIDTH;
    }

    public static int getCardHeight() {
        return CARD_HEIGHT;
    }

    public static int getCardCorner() {
        return CARD_CORNER;
    }

    /**
     * Recomputes all zoom-dependent card/chip images and clears the front/disabled image caches
     * when {@code zoom} changes (or unconditionally if {@code force}); no-op otherwise.
     *
     * @param zoom  target zoom factor
     * @param force recompute even if {@code zoom} matches the currently cached value
     */
    public static synchronized void updateCachedImages(float zoom, boolean force) {

        if (force || CURRENT_ZOOM != zoom) {

            CURRENT_ZOOM = zoom;
            CARD_WIDTH = Math.round(((float) DEFAULT_HEIGHT / ((float) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[0])) * zoom);
            CARD_HEIGHT = Math.round(DEFAULT_HEIGHT * zoom);
            CARD_CORNER = Math.round(Card.DEFAULT_CORNER * zoom);

            // Clear global caches because card sizes changed
            GLOBAL_FRONT_CACHE.clear();
            GLOBAL_DISABLED_CACHE.clear();

            IMAGEN_TRASERA = createBackImageIcon(false);
            IMAGEN_TRASERA_B = createBackImageIcon(true);
            IMAGEN_JOKER = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/joker.jpg");
            IMAGEN_RABBIT_HUNTING = createRabbitCardImageIcon("/images/bugs2.png");
            IMAGEN_RABBIT_HUNTING_B = createRabbitCardImageIcon("/images/bugs2_b.png");
            IMAGEN_RABBIT_BB = createRabbitChipImageIcon(Player.BIG_BLIND);
            IMAGEN_RABBIT_SB = createRabbitChipImageIcon(Player.SMALL_BLIND);

            Helpers.IMAGEN_BB = createPositionChipImageIcon(Player.BIG_BLIND);
            Helpers.IMAGEN_SB = createPositionChipImageIcon(Player.SMALL_BLIND);
            Helpers.IMAGEN_DEALER = createPositionChipImageIcon(Player.DEALER);
            Helpers.IMAGEN_DEAD_DEALER = createPositionChipImageIcon(Player.DEAD_DEALER);
            Helpers.IMAGEN_STRADDLE = createPositionChipImageIcon(Player.STRADDLE);
            Helpers.IMAGEN_DEALER_STRADDLE = createPositionChipImageIcon(Player.DEALER_STRADDLE);
            Helpers.IMAGEN_POT_CHIP = createPotChipImageIcon();

            if (((Object[]) BARAJAS.get(GameFrame.BARAJA))[2] != null) {
                CARTAS_SONIDO = Arrays.asList(((String) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[2]).split(" *, *"));
            } else {
                CARTAS_SONIDO = null;
            }
        }
    }

    private static ImageIcon createPositionChipImageIcon(int position) {

        String image = "";

        switch (position) {

            case Player.DEALER:

                image = "/images/dealer.png";
                break;

            case Player.DEAD_DEALER:

                image = "/images/dead_dealer.png";
                break;

            case Player.BIG_BLIND:

                image = "/images/bb.png";
                break;

            case Player.SMALL_BLIND:

                image = "/images/sb.png";
                break;

            case Player.STRADDLE:

                image = "/images/straddle.png";
                break;

            case Player.DEALER_STRADDLE:

                image = "/images/dealer_straddle.png";
                break;
        }

        return new ImageIcon(new ImageIcon(Card.class.getResource(image)).getImage().getScaledInstance(Math.round(IMAGEN_TRASERA.getIconWidth() * 0.80f), Math.round(IMAGEN_TRASERA.getIconWidth() * 0.80f), Image.SCALE_SMOOTH));

    }

    // Pot chip (pot.png), scaled to the current card size, for the flying-chip animation
    // played when a player puts money in. Same sizing rule as the position chips.
    private static ImageIcon createPotChipImageIcon() {

        return new ImageIcon(new ImageIcon(Card.class.getResource("/images/pot.png")).getImage().getScaledInstance(Math.round(IMAGEN_TRASERA.getIconWidth() * 0.80f), Math.round(IMAGEN_TRASERA.getIconWidth() * 0.80f), Image.SCALE_SMOOTH));

    }

    private static ImageIcon createRabbitChipImageIcon(int position) {

        String image = "";

        switch (position) {

            case Player.BIG_BLIND:

                image = "/images/bb.png";
                break;

            case Player.SMALL_BLIND:

                image = "/images/sb.png";
                break;
        }

        return new ImageIcon(new ImageIcon(Card.class.getResource(image)).getImage().getScaledInstance(Math.round(IMAGEN_TRASERA.getIconWidth() * 0.40f), Math.round(IMAGEN_TRASERA.getIconWidth() * 0.40f), Image.SCALE_SMOOTH));

    }

    /**
     * Current deck's back image, scaled to the current card size (kept in sync with the zoom by
     * {@link #updateCachedImages}). Used by the deal animation so the flying card matches the
     * covered card that lands in the seat.
     *
     * @return the cached back-of-card icon
     */
    public static ImageIcon getBackImage() {
        return IMAGEN_TRASERA;
    }

    // Loads the selected back (GameFrame.TRASERA), scaled and corner-rounded to the current card
    // size, from the game assets or a mod (mod/decks/{baraja}/trasera.jpg). disabled desaturates it.
    private static ImageIcon createBackImageIcon(boolean disabled) {

        java.awt.image.BufferedImage rounded = Helpers.makeImageRoundedCorner(
                new ImageIcon(loadTraseraSource().getScaledInstance(CARD_WIDTH, CARD_HEIGHT, Image.SCALE_SMOOTH)).getImage(), CARD_CORNER);

        return disabled ? new ImageIcon(Helpers.desaturate(rounded, DISABLED_CARD_OPACITY)) : new ImageIcon(rounded);
    }

    // Native-resolution source image of the selected back, from the game assets or a mod
    // (mod/decks/{baraja}/trasera.jpg). Falls back to the default deck's back.
    private static Image loadTraseraSource() {
        String baraja = GameFrame.TRASERA;
        // "default" (or an unknown deck name): the back follows the current deck, so TRASERA
        // doesn't need resetting when the deck changes.
        if (baraja == null || !BARAJAS.containsKey(baraja)) {
            baraja = GameFrame.BARAJA;
        }
        boolean mod = false;
        try {
            mod = (boolean) ((Object[]) BARAJAS.get(baraja))[1];
        } catch (Exception ignore) {
        }
        if (mod) {
            String mod_path = Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/trasera.jpg";
            if (Files.exists(Paths.get(mod_path))) {
                return new ImageIcon(mod_path).getImage();
            }
        } else if (baraja != null) {
            java.net.URL res = Card.class.getResource("/images/decks/" + baraja + "/trasera.jpg");
            if (res != null) {
                return new ImageIcon(res).getImage();
            }
        }
        return new ImageIcon(Card.class.getResource("/images/decks/" + GameFrame.BARAJA_DEFAULT + "/trasera.jpg")).getImage();
    }

    // HQ-folder source image of the selected back, for the viewer (/images/decks/{baraja}/hq/
    // trasera.jpg or the mod equivalent). Falls back to the normal version if no HQ asset exists.
    private static Image loadTraseraSourceHQ() {
        String baraja = GameFrame.TRASERA;
        // "default" (or an unknown deck name): the back follows the current deck, so TRASERA
        // doesn't need resetting when the deck changes.
        if (baraja == null || !BARAJAS.containsKey(baraja)) {
            baraja = GameFrame.BARAJA;
        }
        boolean mod = false;
        try {
            mod = (boolean) ((Object[]) BARAJAS.get(baraja))[1];
        } catch (Exception ignore) {
        }
        if (mod) {
            String mod_path = Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/hq/trasera.jpg";
            if (Files.exists(Paths.get(mod_path))) {
                return new ImageIcon(mod_path).getImage();
            }
        } else if (baraja != null) {
            java.net.URL res = Card.class.getResource("/images/decks/" + baraja + "/hq/trasera.jpg");
            if (res != null) {
                return new ImageIcon(res).getImage();
            }
        }
        // No HQ version (e.g. a mod that doesn't ship one): fall back to the normal image.
        return loadTraseraSource();
    }

    /**
     * @return a high-resolution icon of the selected back, for the zoom viewer
     */
    public static ImageIcon traseraSourceIcon() {
        return new ImageIcon(loadTraseraSourceHQ());
    }

    /**
     * Deck backs offered in the settings dropdown: one per available deck (built-in + mods),
     * keyed by deck name. Same source as the deck combo box.
     *
     * @return the available deck names
     */
    public static java.util.List<String> listTraseras() {
        return new java.util.ArrayList<>(BARAJAS.keySet());
    }

    private static ImageIcon createCardImageIcon(String path) {

        Image img;

        boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[1];

        if (baraja_mod) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + path.replace("/images/decks/", "")))) {
                img = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/decks/" + path.replace("/images/decks/", "")).getImage();
            } else {
                img = new ImageIcon(Card.class.getResource(path.replace(GameFrame.BARAJA, "coronapoker"))).getImage();
                Logger.getLogger(Card.class.getName()).log(Level.WARNING, "No existe {0}", Helpers.getCurrentJarParentPath() + "/mod/decks/" + path.replace("/images/decks/", ""));
            }
        } else {
            img = new ImageIcon(Card.class.getResource(path)).getImage();

        }

        return new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(img.getScaledInstance(CARD_WIDTH, CARD_HEIGHT, Image.SCALE_SMOOTH)).getImage(), CARD_CORNER));

    }

    private static ImageIcon createRabbitCardImageIcon(String path) {

        Image img;

        img = new ImageIcon(Card.class.getResource(path)).getImage();

        return new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(img.getScaledInstance(CARD_WIDTH, CARD_HEIGHT, Image.SCALE_SMOOTH)).getImage(), CARD_CORNER));

    }

    private static ImageIcon createDisabledCardImageIcon(String path) {

        Image img;

        boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[1];

        if (baraja_mod) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + path.replace("/images/decks/", "")))) {
                img = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/decks/" + path.replace("/images/decks/", "")).getImage();
            } else {
                img = new ImageIcon(Card.class.getResource(path.replace(GameFrame.BARAJA, "coronapoker"))).getImage();
                Logger.getLogger(Card.class.getName()).log(Level.WARNING, "No existe {0}", Helpers.getCurrentJarParentPath() + "/mod/decks/" + path.replace("/images/decks/", ""));
            }
        } else {
            img = new ImageIcon(Card.class.getResource(path)).getImage();

        }

        return new ImageIcon(Helpers.desaturate(Helpers.makeImageRoundedCorner(new ImageIcon(img.getScaledInstance(CARD_WIDTH, CARD_HEIGHT, Image.SCALE_SMOOTH)).getImage(), CARD_CORNER), DISABLED_CARD_OPACITY));

    }

    // Compact view clips the card to its top half via the component's own clip (the icon is
    // shown at full height), which leaves a FLAT bottom edge. Returns the SAME full-height icon
    // with its bottom two corners pre-rounded at the cut line (y = CARD_HEIGHT/2, radius
    // CARD_CORNER), so the clipped card shows rounded corners matching the top ones. The bottom
    // half stays intact (it's outside the clip, never shown). The hole-card swap inherits this
    // for free via getDisplayedImage(). Returns a new image; never mutates the shared cached icon.
    private static ImageIcon roundCompactBottomCorners(ImageIcon full) {

        if (full == null) {
            return null;
        }

        int w = full.getIconWidth();
        int fh = full.getIconHeight();
        int cut = Math.round(CARD_HEIGHT / 2);

        if (w <= 0 || fh <= 0 || cut <= 0 || cut >= fh) {
            return full;
        }

        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, fh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();

        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Keep-mask (SrcIn pattern, like makeImageRoundedCorner and the flip's topHalf): top
            // half rounded on all 4 corners + intact bottom half. Their union leaves the bottom
            // corner notches just above the cut line (the bottom rectangle starts there and
            // doesn't cover them), so clipping at CARD_HEIGHT/2 shows all 4 corners alike. The
            // mask is painted FIRST, then the icon on top with SrcIn so anything outside the mask
            // becomes transparent. (DstIn + fill didn't work: fill only rasterizes INSIDE the
            // shape, leaving the outer corners untouched.)
            java.awt.geom.Area keep = new java.awt.geom.Area(
                    new java.awt.geom.RoundRectangle2D.Float(0, 0, w, cut, CARD_CORNER, CARD_CORNER));
            keep.add(new java.awt.geom.Area(new java.awt.Rectangle(0, cut, w, fh - cut)));

            g.setColor(java.awt.Color.WHITE);
            g.fill(keep);
            g.setComposite(java.awt.AlphaComposite.SrcIn);
            g.drawImage(full.getImage(), 0, 0, null);
        } finally {
            g.dispose();
        }

        return new ImageIcon(out);
    }

    /**
     * Creates new form PlayingCard.
     *
     * @param g {@code false} to skip GUI refreshes ({@link #refreshCard} becomes a no-op), for
     *          cards used only as data holders (e.g. offline hand generation / stats)
     */
    public Card(boolean g) {

        gui = g;

        Helpers.GUIRunAndWait(() -> {
            initComponents();
        });

    }

    public Card() {
        gui = true;

        Helpers.GUIRunAndWait(() -> {
            initComponents();
        });
    }

    private static HashMap<String, String> loadUnicodeTable() {

        HashMap<String, String> table = new HashMap<>();

        table.put("A♠", "🂡");
        table.put("K♠", "🂮");
        table.put("Q♠", "🂭");
        table.put("J♠", "🂫");
        table.put("10♠", "🂪");
        table.put("9♠", "🂩");
        table.put("8♠", "🂨");
        table.put("7♠", "🂧");
        table.put("6♠", "🂦");
        table.put("5♠", "🂥");
        table.put("4♠", "🂤");
        table.put("3♠", "🂣");
        table.put("2♠", "🂢");

        table.put("A♥", "🂱");
        table.put("K♥", "🂾");
        table.put("Q♥", "🂽");
        table.put("J♥", "🂻");
        table.put("10♥", "🂺");
        table.put("9♥", "🂹");
        table.put("8♥", "🂸");
        table.put("7♥", "🂷");
        table.put("6♥", "🂶");
        table.put("5♥", "🂵");
        table.put("4♥", "🂴");
        table.put("3♥", "🂳");
        table.put("2♥", "🂲");

        table.put("A♣", "🃑");
        table.put("K♣", "🃞");
        table.put("Q♣", "🃝");
        table.put("J♣", "🃛");
        table.put("10♣", "🃚");
        table.put("9♣", "🃙");
        table.put("8♣", "🃘");
        table.put("7♣", "🃗");
        table.put("6♣", "🃖");
        table.put("5♣", "🃕");
        table.put("4♣", "🃔");
        table.put("3♣", "🃓");
        table.put("2♣", "🃒");

        table.put("A♦", "🃁");
        table.put("K♦", "🃎");
        table.put("Q♦", "🃍");
        table.put("J♦", "🃋");
        table.put("10♦", "🃊");
        table.put("9♦", "🃉");
        table.put("8♦", "🃈");
        table.put("7♦", "🃇");
        table.put("6♦", "🃆");
        table.put("5♦", "🃅");
        table.put("4♦", "🃄");
        table.put("3♦", "🃃");
        table.put("2♦", "🃂");

        table.put("P", "♠");
        table.put("C", "♥");
        table.put("T", "♣");
        table.put("D", "♦");

        return table;

    }

    public void refreshCard() {

        refreshCard(true, null);
    }

    /**
     * Recomputes and applies this card's displayed icon off the EDT, then applies it on the EDT.
     * When {@code notifier} is non-null the update runs synchronously: this thread's id is pushed
     * to it once the icon is applied, so a caller can wait on it (see {@link #destaparSync}).
     *
     * @param pre_cache reuse the front/disabled image cache instead of forcing a reload
     * @param notifier  queue signalled on completion, or {@code null} to fire-and-forget
     */
    public void refreshCard(boolean pre_cache, final ConcurrentLinkedQueue<Long> notifier) {
        if (this.gui) {
            Helpers.threadRun(() -> {
                ImageIcon img;

                synchronized (image_precache_lock) {
                    if (!pre_cache) {
                        invalidateImagePrecache();
                    }

                    if (isIniciada()) {
                        if (isTapada()) {
                            if (rabbit == RABBIT_TAPADA) {
                                img = IMAGEN_RABBIT_HUNTING;
                            } else {
                                img = isDesenfocada() ? Card.IMAGEN_TRASERA_B : Card.IMAGEN_TRASERA;
                            }
                        } else {
                            // Read from Global Cache
                            String key = valor + "_" + palo;
                            if (!isDesenfocada() || mouse_hover) {
                                img = GLOBAL_FRONT_CACHE.computeIfAbsent(key, k
                                        -> createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + k + ".jpg")
                                );
                                image = img;
                            } else {
                                img = GLOBAL_DISABLED_CACHE.computeIfAbsent(key, k
                                        -> createDisabledCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + k + ".jpg")
                                );
                                image_b = img;
                            }
                        }
                    } else {
                        img = Card.IMAGEN_JOKER;
                    }
                }

                final ImageIcon finalImg = img;

                Runnable guiUpdate = () -> {
                    // Avoid redundant repaints and layout invalidations
                    boolean compact = (GameFrame.VISTA_COMPACTA > 0 && compactable);
                    Dimension targetSize = new Dimension(CARD_WIDTH, compact ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT);

                    if (!targetSize.equals(card_image.getPreferredSize())) {
                        card_image.setPreferredSize(targetSize);
                        rabbit_image.setPreferredSize(targetSize);
                        setPreferredSize(targetSize);
                    }

                    // Compact: same icon at full height but with the bottom corners rounded at
                    // the cut line, so the component's clip shows all 4 corners alike instead of
                    // a flat bottom edge.
                    card_image.setIcon(compact ? roundCompactBottomCorners(finalImg) : finalImg);
                    card_image.setVisible(isVisible_card());

                    if (rabbit == RABBIT_DESTAPADA) {
                        rabbit_image.setIcon(IMAGEN_RABBIT_HUNTING_B);
                        rabbit_image.setVisible(isVisible_card());
                    } else if (rabbit == RABBIT_TAPADA && GameFrame.RABBIT_HUNTING > 1) {
                        int conta_rabbit = GameFrame.getInstance().getLocalPlayer().getConta_rabbit();
                        if (GameFrame.RABBIT_HUNTING == 2 && conta_rabbit >= 1) {
                            rabbit_image.setIcon(IMAGEN_RABBIT_SB);
                            rabbit_image.setSize(rabbit_image.getIcon().getIconWidth(), rabbit_image.getIcon().getIconHeight());
                            rabbit_image.setLocation(0, 0);
                            rabbit_image.setPreferredSize(rabbit_image.getSize());
                            rabbit_image.setVisible(isVisible_card());
                        } else if (GameFrame.RABBIT_HUNTING == 3 && conta_rabbit >= 1) {
                            if (conta_rabbit == 1) {
                                rabbit_image.setIcon(IMAGEN_RABBIT_SB);
                            } else if (conta_rabbit > 1) {
                                rabbit_image.setIcon(IMAGEN_RABBIT_BB);
                            }
                            rabbit_image.setSize(rabbit_image.getIcon().getIconWidth(), rabbit_image.getIcon().getIconHeight());
                            rabbit_image.setLocation(0, 0);
                            rabbit_image.setPreferredSize(rabbit_image.getSize());
                            rabbit_image.setVisible(isVisible_card());
                        } else {
                            rabbit_image.setVisible(false);
                        }
                    } else {
                        rabbit_image.setVisible(false);
                    }

                    revalidate();
                    repaint();

                };

                if (notifier == null) {
                    Helpers.GUIRun(guiUpdate);
                } else {
                    Helpers.GUIRunAndWait(guiUpdate);
                    synchronized (notifier) {
                        notifier.add(Thread.currentThread().threadId());
                        notifier.notifyAll();
                    }
                }

                if (pre_cache) {
                    updateImagePreloadCache();
                }
            });
        }
    }

    public void invalidateImagePrecache() {

        synchronized (image_precache_lock) {
            this.image = null;
            this.image_b = null;
        }
    }

    public void updateImagePreloadCache() {
        Helpers.threadRun(() -> {
            synchronized (image_precache_lock) {
                try {
                    if (isIniciadaConValor()) {
                        String key = valor + "_" + palo;
                        if (image == null) {
                            image = GLOBAL_FRONT_CACHE.computeIfAbsent(key, k
                                    -> createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + k + ".jpg")
                            );
                        }
                        if (image_b == null) {
                            image_b = GLOBAL_DISABLED_CACHE.computeIfAbsent(key, k
                                    -> createDisabledCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + k + ".jpg")
                            );
                        }
                    }
                } catch (Exception ex) {
                    Logger.getLogger(Card.class.getName()).log(Level.SEVERE, null, ex);
                    Logger.getLogger(Card.class.getName()).log(Level.WARNING, "ERROR UPDATING CARD IMAGE PRECACHE");
                }
            }
        });
    }

    public void iniciarCarta() {

        iniciarCarta(true);
    }

    public void iniciarCarta(boolean visible) {

        synchronized (image_precache_lock) {
            this.iniciada = true;
            this.tapada = true;
            this.desenfocada = false;
            this.visible_card = visible;
            invalidateImagePrecache();
        }
        refreshCard();
    }

    public void resetearCarta() {
        resetearCarta(true);
    }

    public void resetearCarta(boolean visible) {

        synchronized (image_precache_lock) {
            this.iniciada = false;
            this.tapada = false;
            this.rabbit = RABBIT_OFF;
            this.desenfocada = false;
            this.tinte_showdown = false;
            this.visible_card = visible;
            this.valor = "";
            this.palo = "";
            this.iwtsth_candidate = null;
            invalidateImagePrecache();
        }

        refreshCard();
    }

    /**
     * @return the cards' {@link #toString()} forms joined with spaces, or {@code null} if the
     *         list is null/empty
     */
    public static String collection2String(List<Card> cartas) {

        if (cartas != null && !cartas.isEmpty()) {
            String cadena = "";

            cadena = cartas.stream().map((carta) -> carta + " ").reduce(cadena, String::concat);

            return cadena.substring(0, cadena.length() - 1);
        }

        return null;
    }

    /**
     * @return the cards' {@link #toShortString()} forms joined with "#", or {@code null} if the
     *         list is null/empty
     */
    public static String collection2ShortString(List<Card> cartas) {

        if (cartas != null && !cartas.isEmpty()) {
            String cadena = "";

            cadena = cartas.stream().map((carta) -> carta.toShortString() + "#").reduce(cadena, String::concat);

            return cadena.substring(0, cadena.length() - 1);
        }

        return null;
    }

    /** Sorts descending, ranking the ace low (1). */
    public static void sortAceLowCollection(List<Card> cartas) {
        if (cartas != null) {
            Collections.sort(cartas, new Card.AceLowSortingComparator());

            Collections.reverse(cartas);
        }
    }

    /** Sorts descending, ranking the ace high (14) — the natural order. */
    public static void sortCollection(List<Card> cartas) {

        if (cartas != null) {
            Collections.sort(cartas);

            Collections.reverse(cartas);
        }
    }

    @Override
    public String toString() {
        return "[" + this.valor + Card.UNICODE_TABLE.get(this.palo) + "]";
    }

    /** @return the compact "VALUE_SUIT" form used as a cache/lookup key */
    public String toShortString() {
        return this.valor + "_" + this.palo;
    }

    /**
     * @param id card index 0-51 (id/13 selects the suit, id%13 the rank)
     * @return the short-form string ("VALUE_SUIT"), or {@code null} if out of range
     */
    public static String shortStringFromIndex(int id) {
        if (id < 0 || id > 51) {
            return null;
        }
        return VALORES[id % 13] + "_" + PALOS[id / 13];
    }

    public void iniciarConValorPalo(String valor, String palo) {

        iniciarConValorPalo(valor, palo, true);
    }

    public void iniciarConValorPalo(String valor, String palo, boolean tapada) {
        synchronized (image_precache_lock) {
            String nuevoValor = valor.toUpperCase().trim();
            String nuevoPalo = palo.toUpperCase().trim();

            // Avoid flicker and IO by skipping if state is identical
            if (this.iniciada && this.valor.equals(nuevoValor) && this.palo.equals(nuevoPalo) && this.tapada == tapada) {
                return;
            }

            this.valor = nuevoValor;
            this.palo = nuevoPalo;
            invalidateImagePrecache();
            this.iniciada = true;
            this.tapada = tapada;
            this.desenfocada = false;
        }
        this.refreshCard();
    }

    public void actualizarValorPalo(String valor, String palo) {
        synchronized (image_precache_lock) {
            String nuevoValor = valor.toUpperCase().trim();
            String nuevoPalo = palo.toUpperCase().trim();

            // Avoid flicker by skipping if value is identical
            if (this.valor.equals(nuevoValor) && this.palo.equals(nuevoPalo)) {
                return;
            }

            this.valor = nuevoValor;
            this.palo = nuevoPalo;
            invalidateImagePrecache();
        }
        this.refreshCard();
    }

    public void actualizarValorPaloEnfoque(String valor, String palo, boolean desenfocada) {

        actualizarValorPaloEnfoque(valor, palo, desenfocada, true);
    }

    public void actualizarValorPaloEnfoque(String valor, String palo, boolean desenfocada, boolean refresh) {
        synchronized (image_precache_lock) {
            this.valor = valor.toUpperCase().trim();
            this.palo = palo.toUpperCase().trim();
            this.desenfocada = desenfocada;
            invalidateImagePrecache();
        }

        if (refresh) {
            this.refreshCard();
        }
    }

    public void actualizarConValorNumerico(int value) {
        if (value < 1 || value > 52) {
            // Guard: out-of-range value (typically a MISDEAL-aborted hand that stored
            // VISUAL@ -1,-1, later read back as (byte) -1 & 0xFF + 1 = 256). Without this check
            // PALOS[(value-1)/13] = PALOS[19] throws ArrayIndexOutOfBoundsException, escaping
            // Crupier.run's generic try-catch and killing the server. Skip the update instead.
            return;
        }
        actualizarValorPalo(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
    }

    public void iniciarConValorNumerico(int value) {
        if (value < 1 || value > 52) {
            // See note in actualizarConValorNumerico.
            return;
        }
        iniciarConValorPalo(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
    }

    /**
     * @return this card encoded as 1-52 (ace-low), the same encoding consumed by
     *         {@link #actualizarConValorNumerico(int)} / {@link #iniciarConValorNumerico(int)}
     */
    public int getCartaComoEntero() {
        return PALOS_STRING.indexOf(getPalo()) * 13 + getValorNumerico(true);
    }

    /** @return this card's rank (2-14, ace high), or -1 if unset */
    public int getValorNumerico() {
        return getValorNumerico(false);
    }

    /**
     * @param sort_low_ace {@code true} to rank the ace as 1 instead of 14
     * @return this card's rank as a number, or -1 if unset
     */
    public int getValorNumerico(boolean sort_low_ace) {

        int valor_num = -1;

        if (!this.valor.isEmpty() && Character.isDigit(this.valor.charAt(0))) {
            valor_num = Integer.valueOf(valor);
        } else {
            switch (valor) {
                case "A":
                    valor_num = sort_low_ace ? 1 : 14;
                    break;
                case "K":
                    valor_num = 13;
                    break;
                case "Q":
                    valor_num = 12;
                    break;
                case "J":
                    valor_num = 11;
                    break;
                default:
                    break;
            }
        }

        return valor_num;
    }

    public boolean isIniciada() {
        return iniciada;
    }

    public void destapar() {

        destapar(true);
    }

    public void destapar(boolean sound) {

        if (isIniciadaConValor() && this.tapada) {

            if (sound && GameFrame.destapeSonidoOn()) {
                Helpers.threadRun(() -> Audio.playPreloadedWav("misc/uncover.wav"));
            }

            this.tapada = false;

            this.visible_card = true;

            this.refreshCard();

        }

        this.iwtsth_candidate = null;
    }

    /**
     * Synchronous, silent reveal: doesn't return until the front face is applied on the EDT. Used
     * by the community-card flip animation to splice the static card in UNDER the GIF's last
     * frame before hiding it, so the handoff never shows a blank gap (the classic async reveal
     * leaves a variable-length window with nothing there). The deadline covers the one path where
     * the refresh worker dies without signalling (an exception loading the card image): rather
     * than freeze the hand, it falls back to the same visual result as the async reveal.
     *
     * <p>Must always be called off the EDT: the worker needs the EDT to apply the icon, so calling
     * this from the EDT would just burn the whole deadline waiting on itself.
     */
    public void destaparSync() {

        if (isIniciadaConValor() && this.tapada) {

            this.tapada = false;

            this.visible_card = true;

            final ConcurrentLinkedQueue<Long> notifier = new ConcurrentLinkedQueue<>();

            this.refreshCard(true, notifier);

            long deadline = System.currentTimeMillis() + DESTAPAR_SYNC_TIMEOUT;

            synchronized (notifier) {
                while (notifier.isEmpty() && System.currentTimeMillis() < deadline) {
                    try {
                        notifier.wait(1000);
                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(Logger.getLogger(Card.class.getName()),
                                "destapar sync notifier wait", ex);
                        break;
                    }
                }
            }
        }

        this.iwtsth_candidate = null;
    }

    public void tapar() {

        if (!this.tapada) {

            this.tapada = true;

            this.refreshCard();
        }
    }

    public void desenfocar() {

        if (!this.desenfocada && this.isIniciada()) {

            this.desenfocada = true;

            this.refreshCard();
        }

    }

    public void enfocar() {

        if (this.desenfocada && this.isIniciada()) {

            this.desenfocada = false;

            this.refreshCard();
        }

    }

    /**
     * Marks this card for the showdown yellow tint, painted over the card (see {@link #paint})
     * without touching the image or focus state, while the mouse hovers a losing player's hand
     * label. A full repaint of this Card is enough — no need to rebuild the icon.
     */
    public void marcarTinteShowdown() {
        if (!this.tinte_showdown) {
            this.tinte_showdown = true;
            Helpers.GUIRun(this::repaint);
        }
    }

    /** Clears the tint set by {@link #marcarTinteShowdown()}. */
    public void desmarcarTinteShowdown() {
        if (this.tinte_showdown) {
            this.tinte_showdown = false;
            Helpers.GUIRun(this::repaint);
        }
    }

    public boolean isTinteShowdown() {
        return tinte_showdown;
    }

    // The yellow tint is painted AFTER super.paint() (image + rabbit included), so it sits above
    // the card without altering it: a rounded fill using the same corner radius as the image
    // (getCardCorner()) at low alpha (TINTE_SHOWDOWN_COLOR). It persists until this Card gets a
    // repaint that isn't scoped to just the card_image child — enfocar()/desenfocar() already call
    // this.repaint() on the whole Card, so a stalled show_time doesn't leave it stuck.
    @Override
    public void paint(java.awt.Graphics g) {
        super.paint(g);

        if (tinte_showdown) {
            int w = getWidth();
            int h = getHeight();

            if (w > 0 && h > 0) {
                int arc = getCardCorner();

                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                try {
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(TINTE_SHOWDOWN_COLOR);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                } finally {
                    g2.dispose();
                }
            }
        }
    }

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        Helpers.threadRun(() -> {
            refreshCard(false, null);

            if (notifier != null) {

                notifier.add(Thread.currentThread().threadId());

                synchronized (notifier) {

                    notifier.notifyAll();

                }
            }
        });
    }

    public String getValor() {
        return valor;
    }

    public String getPalo() {
        return palo;
    }

    public boolean isTapada() {
        return tapada;
    }

    public boolean isDesenfocada() {
        return desenfocada;
    }

    /**
     * Plays the deck's configured sound effect if this card is one of its special/easter-egg
     * cards ({@code CARTAS_SONIDO}) and that setting is enabled.
     *
     * @return {@code true} if a sound was triggered
     */
    public boolean checkSpecialCardSound() {

        if (GameFrame.SONIDOS_CHORRA && CARTAS_SONIDO != null) {

            if (CARTAS_SONIDO.contains(this.toShortString())) {
                Audio.playWavResource("decks/" + GameFrame.BARAJA + "/" + this.toShortString() + ".wav");
                return true;
            }
        }

        return false;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        card_image = new javax.swing.JLabel();
        rabbit_image = new javax.swing.JLabel();

        setFocusable(false);
        setPreferredSize(new java.awt.Dimension(148, 200));

        card_image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card_image.setFocusable(false);
        card_image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                card_imageMouseClicked(evt);
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                card_imageMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                card_imageMouseExited(evt);
            }
        });

        rabbit_image.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        rabbit_image.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        rabbit_image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rabbit_image.setFocusable(false);

        setLayer(card_image, javax.swing.JLayeredPane.DEFAULT_LAYER);
        setLayer(rabbit_image, javax.swing.JLayeredPane.PALETTE_LAYER);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(card_image, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(rabbit_image, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(card_image, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(rabbit_image, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void card_imageMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_card_imageMouseClicked
        // TODO add your handling code here:

        // Moved to mouseReleased to avoid dropping clicks (left: zoom/flip card, rabbit hunting,
        // IWTSTH; right: change deck) — require the button to have been released INSIDE the
        // component, keeping the per-button branches below unchanged.
        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }

        if (rabbit == RABBIT_TAPADA && GameFrame.getInstance().getCrupier().isShow_time()) {
            Helpers.threadRun(() -> {
                GameFrame.getInstance().getLocalPlayer().incrementContaRabbit();
                GameFrame.getInstance().getCrupier().RABBIT_HANDLER(GameFrame.getInstance().getLocalPlayer().getNickname(), GameFrame.getInstance().getLocalPlayer().getConta_rabbit());
            });
        } else if (SwingUtilities.isLeftMouseButton(evt) && isTapada() && iwtsth_candidate != null) {

            iwtsth_candidate.playerActionClick();

        } else if (SwingUtilities.isLeftMouseButton(evt) && (!isDesenfocada() || !isTapada())) {
            int carta;

            if (isIniciada()) {

                if (!isTapada()) {

                    carta = CardVisorDialog.cartaFrom(this.valor, this.palo);

                } else {

                    carta = 53;

                }

            } else {

                carta = 54;

            }

            CardVisorDialog.openOrFocus(GameFrame.getInstance(), carta);

        } else if (SwingUtilities.isRightMouseButton(evt)) {

            JMenu menu_barajas = GameFrame.getInstance().getMenu_barajas();

            if (menu_barajas.getItemCount() > 1) {

                int m = 0;

                while (m < menu_barajas.getItemCount() && !menu_barajas.getItem(m).getText().equals(GameFrame.BARAJA)) {
                    m++;
                }

                m = (m + 1) % menu_barajas.getItemCount();

                menu_barajas.getItem(m).doClick();
            }
        }
    }//GEN-LAST:event_card_imageMouseClicked

    private void card_imageMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_card_imageMouseEntered
        // TODO add your handling code here:
        mouse_hover = true;
        refreshCard();
    }//GEN-LAST:event_card_imageMouseEntered

    private void card_imageMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_card_imageMouseExited
        // TODO add your handling code here:
        mouse_hover = false;
        refreshCard();
    }//GEN-LAST:event_card_imageMouseExited

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel card_image;
    private javax.swing.JLabel rabbit_image;
    // End of variables declaration//GEN-END:variables

    @Override
    public int compareTo(Object t) {

        int val1 = this.getValorNumerico();

        int val2 = ((Card) t).getValorNumerico();

        return Integer.compare(val1, val2);
    }

    static class AceLowSortingComparator implements Comparator<Card> {

        @Override
        public int compare(Card carta1, Card carta2) {

            int val1 = carta1.getValorNumerico(true);

            int val2 = carta2.getValorNumerico(true);

            return Integer.compare(val1, val2);
        }
    }
}
