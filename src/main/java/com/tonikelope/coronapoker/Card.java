/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
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
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

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
    private static volatile List<String> CARTAS_SONIDO = null;
    private static volatile float CURRENT_ZOOM = 0f;

    private volatile String valor = "";
    private volatile String palo = "";
    private volatile boolean iniciada = false;
    private volatile boolean tapada = true;
    private volatile boolean desenfocada = false;
    private volatile boolean compactable = true;
    private volatile JLabel ciega_image = null;
    private volatile int ciega = -1;
    private volatile int ciega_pos = 1;
    private volatile boolean ciega_visible = true;

    public boolean isCiega_visible() {
        return ciega_visible;
    }

    public void setCiega_visible(boolean ciega_visible) {
        this.ciega_visible = ciega_visible;

        if (!this.ciega_visible) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    ciega_image.setVisible(false);
                }
            });
        } else if (ciega > 0) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    ciega_image.setVisible(true);
                }
            });
        }
    }

    public int getCiega_pos() {
        return ciega_pos;
    }

    public int getCiega() {
        return ciega;
    }

    public void setCiega(int ciega, int pos) {
        this.ciega = ciega;
        this.ciega_pos = pos;

        if (this.ciega < 0) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    ciega_image.setVisible(false);
                }
            });
        }
    }

    public void setCompactable(boolean compactable) {
        this.compactable = compactable;
    }

    public static void updateCachedImages(float zoom, boolean force) {

        if (force || CURRENT_ZOOM != zoom) {

            CURRENT_ZOOM = zoom;
            CARD_WIDTH = Math.round(((float) DEFAULT_HEIGHT / ((float) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[0])) * zoom);
            CARD_HEIGHT = Math.round(DEFAULT_HEIGHT * zoom);
            CARD_CORNER = Math.round(Card.DEFAULT_CORNER * zoom);
            IMAGEN_TRASERA = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/trasera.jpg");
            IMAGEN_TRASERA_B = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/trasera_b.jpg");
            IMAGEN_JOKER = createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/joker.jpg");

            if (((Object[]) BARAJAS.get(GameFrame.BARAJA))[2] != null) {
                CARTAS_SONIDO = Arrays.asList(((String) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[2]).split(" *, *"));
            }
        }
    }

    public static HashMap<String, String> getUNICODE_TABLE() {
        return UNICODE_TABLE;
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
    public Card() {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
                ciega_image = new JLabel("");
                ciega_image.setDoubleBuffered(true);
                ciega_image.setCursor(new Cursor(Cursor.HAND_CURSOR));
                ciega_image.setOpaque(false);
                ciega_image.setVisible(false);
                add(ciega_image, JLayeredPane.POPUP_LAYER);
                ciega_image.setSize(new Dimension(100, 100));
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

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));
                card_image.setPreferredSize(new Dimension(CARD_WIDTH, (GameFrame.VISTA_COMPACTA && compactable) ? Math.round(CARD_HEIGHT / 2) : CARD_HEIGHT));

                if (isIniciada()) {

                    if (isTapada()) {

                        card_image.setIcon(isDesenfocada() ? Card.IMAGEN_TRASERA_B : Card.IMAGEN_TRASERA);

                    } else {

                        card_image.setIcon(createCardImageIcon("/images/decks/" + GameFrame.BARAJA + "/" + valor + "_" + palo + (isDesenfocada() ? "_b.jpg" : ".jpg")));

                        if (ciega_pos == 1) {
                            setCiega(-1, 1);
                        }

                    }
                } else {
                    card_image.setIcon(Card.IMAGEN_JOKER);

                }

                card_image.revalidate();

                card_image.repaint();
            }
        });

        Helpers.GUIRun(new Runnable() {
            public void run() {
                refreshCiega();

                ciega_image.revalidate();

                ciega_image.repaint();
            }
        });
    }

    public void iniciarCarta() {

        this.iniciada = true;
        this.tapada = true;
        this.desenfocada = false;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.setIcon(Card.IMAGEN_TRASERA);
                refreshCiega();
            }
        });
    }

    public void resetearCarta() {
        this.iniciada = false;
        this.tapada = false;
        this.desenfocada = false;
        this.valor = "";
        this.palo = "";
        setCiega(-1, 1);

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.setIcon(Card.IMAGEN_JOKER);
                refreshCiega();
            }
        });
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

        this.valor = valor.toUpperCase().trim();
        this.palo = palo.toUpperCase().trim();
        this.iniciada = true;
        this.tapada = true;
        this.desenfocada = false;
        this.refreshCard();
    }

    public void preIniciarConValorPalo(String valor, String palo) {

        this.valor = valor.toUpperCase().trim();
        this.palo = palo.toUpperCase().trim();
        this.iniciada = false;
        this.tapada = true;
        this.desenfocada = false;
        this.refreshCard();
    }

    public void actualizarValorPalo(String valor, String palo) {

        this.valor = valor.toUpperCase().trim();
        this.palo = palo.toUpperCase().trim();
        this.refreshCard();
    }

    public void actualizarValorPaloEnfoque(String valor, String palo, boolean desenfocada) {

        this.valor = valor.toUpperCase().trim();
        this.palo = palo.toUpperCase().trim();
        this.desenfocada = desenfocada;
        this.refreshCard();
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

    private void refreshCiega() {

        String image = null;

        if (this.ciega > 0) {

            switch (this.ciega) {

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

            ciega_image.setSize(new Dimension(Math.round(card_image.getIcon().getIconWidth() * 0.75f), Math.round(card_image.getIcon().getIconWidth() * 0.75f)));
            ciega_image.setIcon(new ImageIcon(new ImageIcon(Card.class.getResource(image)).getImage().getScaledInstance(Math.round(card_image.getIcon().getIconWidth() * 0.75f), Math.round(card_image.getIcon().getIconWidth() * 0.75f), Image.SCALE_SMOOTH)));

            if (ciega_pos == 1) {
                ciega_image.setLocation(new Point(card_image.getX(), card_image.getY()));
            } else {
                ciega_image.setLocation(new Point(card_image.getX(), card_image.getHeight() - ciega_image.getHeight()));
            }

            if (this.ciega_visible) {
                ciega_image.setVisible(true);
            }
        } else {
            ciega_image.setVisible(false);
        }
    }

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<String> notifier) {

        this.refreshCard();

        if (notifier != null) {

            notifier.add(Thread.currentThread().getName());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
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

            visor.setLocationRelativeTo(visor.getParent());

            visor.setVisible(true);
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
