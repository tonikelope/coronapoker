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
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;

/**
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

    public RemotePlayer getIwtsth_candidate() {
        return iwtsth_candidate;
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

    public static synchronized void updateCachedImages(float zoom, boolean force) {

        if (force || CURRENT_ZOOM != zoom) {

            CURRENT_ZOOM = zoom;
            CARD_WIDTH = Math.round(((float) DEFAULT_HEIGHT / ((float) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[0])) * zoom);
            CARD_HEIGHT = Math.round(DEFAULT_HEIGHT * zoom);
            CARD_CORNER = Math.round(Card.DEFAULT_CORNER * zoom);
            IMAGEN_TRASERA = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/trasera.jpg");
            IMAGEN_TRASERA_B = createDisabledCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/trasera.jpg");
            IMAGEN_JOKER = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/joker.jpg");
            IMAGEN_RABBIT_HUNTING = createRabbitCardImageIcon("/images/bugs2.png");
            IMAGEN_RABBIT_HUNTING_B = createRabbitCardImageIcon("/images/bugs2_b.png");
            IMAGEN_RABBIT_BB = createRabbitChipImageIcon(Player.BIG_BLIND);
            IMAGEN_RABBIT_SB = createRabbitChipImageIcon(Player.SMALL_BLIND);

            Helpers.IMAGEN_BB = createPositionChipImageIcon(Player.BIG_BLIND);
            Helpers.IMAGEN_SB = createPositionChipImageIcon(Player.SMALL_BLIND);
            Helpers.IMAGEN_DEALER = createPositionChipImageIcon(Player.DEALER);
            Helpers.IMAGEN_DEAD_DEALER = createPositionChipImageIcon(Player.DEAD_DEALER);

            if (((Object[]) BARAJAS.get(GameFrame.BARAJA))[2] != null) {
                CARTAS_SONIDO = Arrays.asList(((String) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[2]).split(" *, *"));
            } else {
                CARTAS_SONIDO = null;
            }
        }
    }

    public static HashMap<String, String> getUNICODE_TABLE() {
        return UNICODE_TABLE;
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
        }

        return new ImageIcon(new ImageIcon(Card.class.getResource(image)).getImage().getScaledInstance(Math.round(IMAGEN_TRASERA.getIconWidth() * 0.80f), Math.round(IMAGEN_TRASERA.getIconWidth() * 0.80f), Image.SCALE_SMOOTH));

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

    public JLabel getCard_image() {
        return card_image;
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

    /**
     * Creates new form PlayingCard
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

                            if (!isDesenfocada() || mouse_hover) {

                                if (image != null) {
                                    img = image;
                                } else {
                                    img = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + ".jpg");
                                    image = img;

                                }

                            } else {

                                if (image_b != null) {
                                    img = image_b;
                                } else {
                                    img = createDisabledCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + ".jpg");
                                    image_b = img;

                                }
                            }

                        }
                    } else {
                        img = Card.IMAGEN_JOKER;
                    }

                }
                if (notifier == null) {
                    Helpers.GUIRun(() -> {

                        card_image.setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA > 0 && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                        card_image.setIcon(img);
                        card_image.setVisible(isVisible_card());

                        if (rabbit == RABBIT_DESTAPADA) {
                            rabbit_image.setPreferredSize(card_image.getPreferredSize());
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

                        setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA > 0 && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                        invalidate();
                        revalidate();
                        repaint();
                    });

                } else {
                    Helpers.GUIRunAndWait(() -> {

                        card_image.setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA > 0 && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                        card_image.setIcon(img);
                        card_image.setVisible(isVisible_card());

                        if (rabbit == RABBIT_DESTAPADA) {
                            rabbit_image.setPreferredSize(card_image.getPreferredSize());
                            rabbit_image.setIcon(IMAGEN_RABBIT_HUNTING_B);
                            rabbit_image.setVisible(isVisible_card());
                        } else {
                            rabbit_image.setVisible(false);
                        }

                        setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA > 0 && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                        invalidate();
                        revalidate();
                        repaint();
                    });

                    synchronized (notifier) {
                        notifier.add(Thread.currentThread().getId());
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

                        if (image == null) {
                            image = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + ".jpg");
                        }

                        if (image_b == null) {
                            image_b = createDisabledCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + ".jpg");
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
            this.visible_card = visible;
            this.valor = "";
            this.palo = "";
            this.iwtsth_candidate = null;
            invalidateImagePrecache();
        }

        refreshCard();
    }

    public static String collection2String(List<Card> cartas) {

        if (cartas != null && !cartas.isEmpty()) {
            String cadena = "";

            cadena = cartas.stream().map((carta) -> carta + " ").reduce(cadena, String::concat);

            return cadena.substring(0, cadena.length() - 1);
        }

        return null;
    }

    public static String collection2ShortString(List<Card> cartas) {

        if (cartas != null && !cartas.isEmpty()) {
            String cadena = "";

            cadena = cartas.stream().map((carta) -> carta.toShortString() + "#").reduce(cadena, String::concat);

            return cadena.substring(0, cadena.length() - 1);
        }

        return null;
    }

    public static String shortString2UNICODEString(String cards) {

        if (cards != null) {
            String cadena = "";

            for (String card : cards.split("#")) {

                String[] parts = card.split("_");

                cadena += parts[0] + Card.UNICODE_TABLE.get(parts[1]) + " ";
            }

            return cadena.substring(0, cadena.length() - 1);
        }

        return null;
    }

    public static void sortAceLowCollection(List<Card> cartas) {
        if (cartas != null) {
            Collections.sort(cartas, new Card.AceLowSortingComparator());

            Collections.reverse(cartas);
        }
    }

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

    public String toShortString() {
        return this.valor + "_" + this.palo;
    }

    public void iniciarConValorPalo(String valor, String palo) {

        iniciarConValorPalo(valor, palo, true);
    }

    public void iniciarConValorPalo(String valor, String palo, boolean tapada) {

        synchronized (image_precache_lock) {
            this.valor = valor.toUpperCase().trim();
            this.palo = palo.toUpperCase().trim();
            invalidateImagePrecache();
            this.iniciada = true;
            this.tapada = tapada;
            this.desenfocada = false;
        }
        this.refreshCard();
    }

    public void preIniciarConValorPalo(String valor, String palo) {
        synchronized (image_precache_lock) {
            this.valor = valor.toUpperCase().trim();
            this.palo = palo.toUpperCase().trim();
            this.iniciada = false;
            this.tapada = true;
            this.desenfocada = false;
            invalidateImagePrecache();
        }
        this.refreshCard();
    }

    public void actualizarValorPalo(String valor, String palo) {
        synchronized (image_precache_lock) {
            this.valor = valor.toUpperCase().trim();
            this.palo = palo.toUpperCase().trim();
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
        actualizarValorPalo(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
    }

    public void iniciarConValorNumerico(int value) {
        iniciarConValorPalo(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
    }

    public void preIniciarConValorNumerico(int value) {
        preIniciarConValorPalo(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
    }

    public int getCartaComoEntero() {
        return PALOS_STRING.indexOf(getPalo()) * 13 + getValorNumerico(true);
    }

    public int getValorNumerico() {
        return getValorNumerico(false);
    }

    public int getValorNumerico(boolean sort_low_ace) {

        int valor_num = -1;

        if (Helpers.isNumeric(this.valor)) {
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

            if (sound) {
                Audio.playWavResource("misc/uncover.wav", false);
            }

            this.tapada = false;

            this.visible_card = true;

            this.refreshCard();

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

            this.desenfocada = true;

            this.refreshCard();
        }

    }

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        Helpers.threadRun(() -> {
            refreshCard(false, null);

            if (notifier != null) {

                notifier.add(Thread.currentThread().getId());

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

        setDoubleBuffered(true);
        setFocusable(false);
        setPreferredSize(new java.awt.Dimension(148, 200));

        card_image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card_image.setDoubleBuffered(true);
        card_image.setFocusable(false);
        card_image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
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
        rabbit_image.setDoubleBuffered(true);
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

        if (rabbit == RABBIT_TAPADA && GameFrame.getInstance().getCrupier().isShow_time()) {
            Helpers.threadRun(() -> {
                GameFrame.getInstance().getLocalPlayer().incrementContaRabbit();
                GameFrame.getInstance().getCrupier().RABBIT_HANDLER(GameFrame.getInstance().getLocalPlayer().getNickname(), GameFrame.getInstance().getLocalPlayer().getConta_rabbit());
            });
        } else if (SwingUtilities.isLeftMouseButton(evt) && isTapada() && iwtsth_candidate != null) {

            iwtsth_candidate.playerActionClick();

        } else if (SwingUtilities.isLeftMouseButton(evt) && (!isDesenfocada() || !isTapada())) {
            CardVisorDialog visor;

            if (isIniciada()) {

                if (!isTapada()) {

                    visor = new CardVisorDialog(GameFrame.getInstance(), false, this.valor, this.palo, false);

                } else {

                    visor = new CardVisorDialog(GameFrame.getInstance(), false, 53, false);

                }

            } else {

                visor = new CardVisorDialog(GameFrame.getInstance(), false, 54, false);

            }

            Audio.playWavResource("misc/card_visor.wav");

            visor.setLocationRelativeTo(GameFrame.getInstance());

            visor.setVisible(true);

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
