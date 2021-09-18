/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseEvent;
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
import javax.swing.JMenuItem;

/**
 *
 * @author tonikelope
 */
public class Card extends javax.swing.JLayeredPane implements ZoomableInterface, Comparable {

    public final static ConcurrentHashMap<String, Object[]> BARAJAS = new ConcurrentHashMap<>(Map.ofEntries(new HashMap.SimpleEntry<String, Object[]>("coronapoker", new Object[]{1.345f, false, null}), new HashMap.SimpleEntry<String, Object[]>("interstate60", new Object[]{1.345f, false, null}), new HashMap.SimpleEntry<String, Object[]>("goliat", new Object[]{1.345f, false, null})));
    public final static int DEFAULT_HEIGHT = 200;
    public final static String[] PALOS = {"P", "C", "T", "D"};
    public final static String[] VALORES = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    public final static int DEFAULT_CORNER = 20;
    private final static HashMap<String, String> UNICODE_TABLE = loadUnicodeTable();
    private static volatile int CARD_WIDTH = -1;
    private static volatile int CARD_HEIGHT = -1;
    private static volatile int CARD_CORNER = -1;
    private static volatile ImageIcon IMAGEN_TRASERA = null;
    private static volatile ImageIcon IMAGEN_TRASERA_B = null;
    private static volatile ImageIcon IMAGEN_JOKER = null;
    private static volatile ImageIcon IMAGEN_BB = null;
    private static volatile ImageIcon IMAGEN_SB = null;
    private static volatile ImageIcon IMAGEN_DEALER = null;
    private static volatile List<String> CARTAS_SONIDO = null;
    private static volatile float CURRENT_ZOOM = 0f;

    private volatile String valor = "";
    private volatile String palo = "";
    private volatile boolean iniciada = false;
    private volatile boolean tapada = true;
    private volatile boolean desenfocada = false;
    private volatile boolean visible_card = false;
    private volatile boolean compactable = true;
    private volatile JLabel pos_chip_label = null;
    private volatile int pos_chip = -1;
    private volatile int pos_chip_location = 1;
    private volatile boolean pos_chip_visible = true;
    private volatile boolean gui = true;
    private volatile ImageIcon image = null;
    private volatile ImageIcon image_b = null;
    private final Object image_precache_lock = new Object();

    public boolean isVisible_card() {
        return visible_card;
    }

    public void setVisibleCard(boolean visible_card) {
        this.visible_card = visible_card;
        refreshCard();
    }

    public boolean isPosChip_visible() {
        return pos_chip_visible;
    }

    public void setPosChip_visible(boolean chip_visible) {
        this.pos_chip_visible = chip_visible;

        if (!this.pos_chip_visible) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    pos_chip_label.setVisible(false);
                }
            });
        } else if (pos_chip > 0) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    pos_chip_label.setVisible(true);
                }
            });
        }
    }

    public int getPosChipLocation() {
        return pos_chip_location;
    }

    public int getPosChip() {
        return pos_chip;
    }

    public void resetPosChip() {
        setPosChip(-1, 1);
    }

    public void setPosChip(int pos, int location) {
        this.pos_chip = pos;
        this.pos_chip_location = location;

        if (this.pos_chip < 0) {
            this.pos_chip_visible = true;
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    pos_chip_label.setVisible(false);
                }
            });
        }
    }

    public void setCompactable(boolean compactable) {
        this.compactable = compactable;
    }

    public static synchronized void updateCachedImages(float zoom, boolean force) {

        if (force || CURRENT_ZOOM != zoom) {

            CURRENT_ZOOM = zoom;
            CARD_WIDTH = Math.round(((float) DEFAULT_HEIGHT / ((float) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[0])) * zoom);
            CARD_HEIGHT = Math.round(DEFAULT_HEIGHT * zoom);
            CARD_CORNER = Math.round(Card.DEFAULT_CORNER * zoom);
            IMAGEN_TRASERA = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/trasera.jpg");
            IMAGEN_TRASERA_B = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/trasera_b.jpg");
            IMAGEN_JOKER = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/joker.jpg");
            IMAGEN_BB = createPositionChipImageIcon(Player.BIG_BLIND);
            IMAGEN_SB = createPositionChipImageIcon(Player.SMALL_BLIND);
            IMAGEN_DEALER = createPositionChipImageIcon(Player.DEALER);

            if (((Object[]) BARAJAS.get(GameFrame.BARAJA))[2] != null) {
                CARTAS_SONIDO = Arrays.asList(((String) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[2]).split(" *, *"));
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

            case Player.BIG_BLIND:

                image = "/images/bb.png";
                break;

            case Player.SMALL_BLIND:

                image = "/images/sb.png";
                break;
        }

        return new ImageIcon(new ImageIcon(Card.class.getResource(image)).getImage().getScaledInstance(Math.round(IMAGEN_TRASERA.getIconWidth() * 0.75f), Math.round(IMAGEN_TRASERA.getIconWidth() * 0.75f), Image.SCALE_SMOOTH));

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

    /**
     * Creates new form PlayingCard
     */
    public Card(boolean gui) {

        this.gui = gui;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();

                if (gui) {
                    pos_chip_label = new JLabel("");
                    pos_chip_label.setDoubleBuffered(true);
                    pos_chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    pos_chip_label.setOpaque(false);
                    pos_chip_label.setVisible(false);
                    add(pos_chip_label, JLayeredPane.POPUP_LAYER);
                    pos_chip_label.setSize(new Dimension(100, 100));
                }
            }
        });

    }

    public Card() {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
                pos_chip_label = new JLabel("");
                pos_chip_label.setDoubleBuffered(true);
                pos_chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                pos_chip_label.setOpaque(false);
                pos_chip_label.setVisible(false);
                add(pos_chip_label, JLayeredPane.POPUP_LAYER);
                pos_chip_label.setSize(new Dimension(100, 100));
            }
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

        refreshCard(true);
    }

    public void refreshCard(boolean pre_cache) {
        if (this.gui) {

            Helpers.threadRun(new Runnable() {
                public void run() {

                    ImageIcon img;

                    synchronized (image_precache_lock) {

                        if (isIniciada()) {

                            if (isTapada()) {

                                img = isDesenfocada() ? Card.IMAGEN_TRASERA_B : Card.IMAGEN_TRASERA;

                            } else {

                                if (!pre_cache) {

                                    image = null;
                                    image_b = null;

                                }

                                if (!isDesenfocada()) {

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
                                        img = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + "_b.jpg");
                                        image_b = img;

                                    }
                                }

                            }
                        } else {
                            img = Card.IMAGEN_JOKER;
                        }

                    }

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            card_image.setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                            card_image.setIcon(img);
                            card_image.setVisible(isVisible_card());
                            setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                            updatePositionChip((GameFrame.VISTA_COMPACTA && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT);
                            revalidate();
                            repaint();
                        }
                    });

                    if (pre_cache) {

                        updateImagePreloadCache();
                    }
                }
            });
        }
    }

    public void updateImagePreloadCache() {

        Helpers.threadRun(new Runnable() {
            public void run() {

                synchronized (image_precache_lock) {
                    try {

                        if (isIniciada()) {

                            if (image == null) {
                                image = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + ".jpg");
                            }

                            if (image_b == null) {
                                image_b = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + "_b.jpg");
                            }

                        }

                    } catch (Exception e) {
                        Logger.getLogger(Card.class.getName()).log(Level.WARNING, "ERROR UPDATING CARD IMAGE PRECACHE");
                    }

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
            this.image = null;
            this.image_b = null;
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
            this.desenfocada = false;
            this.visible_card = visible;
            this.valor = "";
            this.palo = "";
            this.image = null;
            this.image_b = null;
        }

        resetPosChip();
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

        Collections.sort(cartas, new Card.AceLowSortingComparator());

        Collections.reverse(cartas);
    }

    public static void sortCollection(List<Card> cartas) {

        Collections.sort(cartas);

        Collections.reverse(cartas);
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
            this.image = null;
            this.image_b = null;
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
            this.image = null;
            this.image_b = null;
        }
        this.refreshCard();
    }

    public void actualizarValorPalo(String valor, String palo) {
        synchronized (image_precache_lock) {
            this.valor = valor.toUpperCase().trim();
            this.palo = palo.toUpperCase().trim();
            this.image = null;
            this.image_b = null;
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
            this.image = null;
            this.image_b = null;
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

        if (!"".equals(this.valor) && this.tapada) {

            if (sound) {
                Helpers.playWavResource("misc/uncover.wav", false);
            }

            this.tapada = false;

            this.visible_card = true;

            this.refreshCard();

        }
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

    private void updatePositionChip(int card_height) {

        ImageIcon image = null;

        if (pos_chip > 0) {

            switch (pos_chip) {

                case Player.DEALER:

                    image = IMAGEN_DEALER;
                    break;

                case Player.BIG_BLIND:

                    image = IMAGEN_BB;
                    break;

                case Player.SMALL_BLIND:

                    image = IMAGEN_SB;
                    break;
            }

            pos_chip_label.setSize(new Dimension(image.getIconWidth(), image.getIconHeight()));
            pos_chip_label.setIcon(image);

            if (pos_chip_location == 1) {
                pos_chip_label.setLocation(new Point(card_image.getX(), card_image.getY()));
            } else {
                pos_chip_label.setLocation(new Point(card_image.getX(), card_height - pos_chip_label.getHeight()));
            }

            pos_chip_label.setVisible(pos_chip_visible && (pos_chip_location == 1 || GameFrame.LOCAL_POSITION_CHIP));

            pos_chip_label.setCursor(isVisible_card() ? new Cursor(Cursor.HAND_CURSOR) : new Cursor(Cursor.DEFAULT_CURSOR));

        } else {
            pos_chip_label.setVisible(false);
        }

    }

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        Helpers.threadRun(new Runnable() {
            public void run() {

                refreshCard(false);

                if (notifier != null) {

                    notifier.add(Thread.currentThread().getId());

                    synchronized (notifier) {

                        notifier.notifyAll();

                    }
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
                Helpers.playWavResource("decks/" + GameFrame.BARAJA + "/" + this.toShortString() + ".wav");
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

        setPreferredSize(new java.awt.Dimension(148, 200));

        card_image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card_image.setDoubleBuffered(true);
        card_image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                card_imageMouseClicked(evt);
            }
        });

        setLayer(card_image, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(card_image, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(card_image, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void card_imageMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_card_imageMouseClicked
        // TODO add your handling code here:

        if (evt.getButton() == MouseEvent.BUTTON1 && (!isDesenfocada() || !isTapada())) {
            CardVisorDialog visor;

            if (isIniciada()) {

                if (!isTapada()) {

                    visor = new CardVisorDialog(GameFrame.getInstance().getFrame(), false, this.valor, this.palo, false);

                } else {

                    visor = new CardVisorDialog(GameFrame.getInstance().getFrame(), false, 53, false);

                }

            } else {

                visor = new CardVisorDialog(GameFrame.getInstance().getFrame(), false, 54, false);

            }

            Helpers.playWavResource("misc/card_visor.wav");

            visor.setLocationRelativeTo(visor.getParent());

            visor.setVisible(true);

        } else if (evt.getButton() == MouseEvent.BUTTON3) {

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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel card_image;
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
