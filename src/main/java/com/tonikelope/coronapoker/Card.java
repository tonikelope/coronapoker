/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Dimension;
import java.awt.Image;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;

/**
 *
 * @author tonikelope
 */
public class Card extends javax.swing.JPanel implements ZoomableInterface, Comparable {

    public final static ConcurrentHashMap<String, Object[]> BARAJAS = new ConcurrentHashMap<>(Map.ofEntries(new HashMap.SimpleEntry<String, Object[]>("coronapoker", new Object[]{1.345f, false, null}), new HashMap.SimpleEntry<String, Object[]>("interstate60", new Object[]{1.345f, false, null})));
    public final static int DEFAULT_HEIGHT = 200;
    public final static String[] PALOS = {"P", "C", "T", "D"};
    public final static String[] VALORES = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    public final static int DEFAULT_CORNER = 20;
    private final static HashMap<String, String> UNICODE_TABLE = loadUnicodeTable();
    private static int width = -1;
    private static int height = -1;
    private static int corner = -1;
    private static ImageIcon imagen_trasera = null;
    private static ImageIcon imagen_trasera_b = null;
    private static ImageIcon imagen_joker = null;
    private static List<String> CARTAS_SONIDO = null;

    private String valor = "";
    private String palo = "";
    private boolean cargada = false;
    private boolean tapada = true;
    private boolean desenfocada = false;
    private boolean compactable = true;

    public void setCompactable(boolean compactable) {
        this.compactable = compactable;
    }

    public static void actualizarImagenesPrecargadas(float zoom) {
        width = Math.round(((float) DEFAULT_HEIGHT / ((float) ((Object[]) BARAJAS.get(Game.BARAJA))[0])) * zoom);
        height = Math.round(DEFAULT_HEIGHT * zoom);
        corner = Math.round(Card.DEFAULT_CORNER * zoom);
        imagen_trasera = createCardImageIcon("/images/decks/" + Game.BARAJA + "/trasera.jpg");
        imagen_trasera_b = createCardImageIcon("/images/decks/" + Game.BARAJA + "/trasera_b.jpg");
        imagen_joker = createCardImageIcon("/images/decks/" + Game.BARAJA + "/joker.jpg");

        if (((Object[]) BARAJAS.get(Game.BARAJA))[2] != null) {
            CARTAS_SONIDO = Arrays.asList(((String) ((Object[]) BARAJAS.get(Game.BARAJA))[2]).split(" *, *"));
        }
    }

    public static HashMap<String, String> getUNICODE_TABLE() {
        return UNICODE_TABLE;
    }

    private static ImageIcon createCardImageIcon(String path) {

        Image img;

        boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(Game.BARAJA))[1];

        if (baraja_mod) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/decks/" + path.replace("/images/decks/", "")))) {
                img = new ImageIcon(Helpers.getCurrentJarPath() + "/mod/decks/" + path.replace("/images/decks/", "")).getImage();
            } else {
                img = new ImageIcon(Card.class.getResource(path.replace(Game.BARAJA, "coronapoker"))).getImage();
                Logger.getLogger(Card.class.getName()).log(Level.WARNING, "No existe {0}", Helpers.getCurrentJarPath() + "/mod/decks/" + path.replace("/images/decks/", ""));
            }
        } else {
            img = new ImageIcon(Card.class.getResource(path)).getImage();

        }

        return new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH)).getImage(), corner));

    }

    /**
     * Creates new form PlayingCard
     */
    public Card() {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
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

        Helpers.GUIRun(new Runnable() {
            public void run() {
                setPreferredSize(new Dimension((Game.VISTA_COMPACTA && compactable) ? Math.round(width / 2) : width, (Game.VISTA_COMPACTA && compactable) ? Math.round(height / 4) : height));
                card_image.setPreferredSize(new Dimension((Game.VISTA_COMPACTA && compactable) ? Math.round(width / 2) : width, (Game.VISTA_COMPACTA && compactable) ? Math.round(height / 4) : height));
            }
        });

        if (cargada) {

            if (tapada) {
                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        card_image.setIcon(isDesenfocada() ? Card.imagen_trasera_b : Card.imagen_trasera);
                    }
                });

            } else {

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        card_image.setIcon(createCardImageIcon("/images/decks/" + Game.BARAJA + "/" + valor + "_" + palo + (isDesenfocada() ? "_b.jpg" : ".jpg")));
                    }
                });
            }

        } else {

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    card_image.setIcon(Card.imagen_joker);
                }
            });
        }

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.revalidate();

            }
        });

    }

    public void cargarCarta() {

        this.cargada = true;
        this.tapada = true;
        this.desenfocada = false;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.setIcon(Card.imagen_trasera);

            }
        });
    }

    public void descargarCarta() {
        this.cargada = false;
        this.tapada = false;
        this.desenfocada = false;
        this.valor = "";
        this.palo = "";

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.setIcon(Card.imagen_joker);

            }
        });
    }

    public static String collection2String(List<Card> cartas) {

        if (cartas != null && !cartas.isEmpty()) {
            String cadena = " ";

            cadena = cartas.stream().map((carta) -> carta + " ").reduce(cadena, String::concat);

            return cadena;
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

    public void preCargarCarta(String valor, String palo) {

        this.valor = valor.toUpperCase().trim();
        this.palo = palo.toUpperCase().trim();
        this.cargada = false;
        this.tapada = true;
        this.desenfocada = false;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.setIcon(Card.imagen_joker);

            }
        });
    }

    public void cargarCarta(String valor, String palo) {

        this.valor = valor.toUpperCase().trim();
        this.palo = palo.toUpperCase().trim();
        this.cargada = true;
        this.tapada = true;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                card_image.setIcon(isDesenfocada() ? Card.imagen_trasera_b : Card.imagen_trasera);

            }
        });

    }

    public void cargarCarta(int value) {
        cargarCarta(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
    }

    public void preCargarCarta(int value) {
        preCargarCarta(VALORES[((value - 1) % 13)], PALOS[(int) ((float) (value - 1) / 13)]);
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

    public boolean isCargada() {
        return cargada;
    }

    public void destapar() {

        destapar(true);
    }

    public void destapar(boolean sound) {

        if (!"".equals(this.valor) && this.tapada) {

            if (sound) {
                Helpers.playWavResource("misc/uncover.wav");
            }

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    card_image.setIcon(createCardImageIcon("/images/decks/" + Game.BARAJA + "/" + valor + "_" + palo + (isDesenfocada() ? "_b.jpg" : ".jpg")));

                }
            });

            this.tapada = false;

        }
    }

    public void tapar() {

        if (!this.tapada) {

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    card_image.setIcon(isDesenfocada() ? Card.imagen_trasera_b : Card.imagen_trasera);

                }
            });

            this.tapada = true;
        }
    }

    public void desenfocar() {

        if (!this.desenfocada && this.isCargada()) {
            if (this.isTapada()) {

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        card_image.setIcon(Card.imagen_trasera_b);

                    }
                });

            } else if (!"".equals(this.valor)) {
                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        card_image.setIcon(createCardImageIcon("/images/decks/" + Game.BARAJA + "/" + valor + "_" + palo + "_b.jpg"));

                    }
                });
            }

            this.desenfocada = true;
        }

    }

    @Override
    public void zoom(float factor) {
        imageZoom();
    }

    private void imageZoom() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                setPreferredSize(new Dimension((Game.VISTA_COMPACTA && compactable) ? Math.round(width / 2) : width, (Game.VISTA_COMPACTA && compactable) ? Math.round(height / 4) : height));
                card_image.setPreferredSize(new Dimension((Game.VISTA_COMPACTA && compactable) ? Math.round(width / 2) : width, (Game.VISTA_COMPACTA && compactable) ? Math.round(height / 4) : height));

                if (isCargada()) {

                    if (isTapada()) {

                        if (isDesenfocada()) {
                            card_image.setIcon(Card.imagen_trasera_b);
                        } else {
                            card_image.setIcon(Card.imagen_trasera);
                        }

                    } else {

                        if (isDesenfocada()) {
                            card_image.setIcon(createCardImageIcon("/images/decks/" + Game.BARAJA + "/" + valor + "_" + palo + "_b.jpg"));
                        } else {
                            card_image.setIcon(createCardImageIcon("/images/decks/" + Game.BARAJA + "/" + valor + "_" + palo + ".jpg"));
                        }

                    }
                } else {
                    card_image.setIcon(Card.imagen_joker);
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

        if (Game.SONIDOS_CHORRA && CARTAS_SONIDO != null) {

            if (CARTAS_SONIDO.contains(this.toShortString())) {
                Helpers.playWavResource("decks/" + Game.BARAJA + "/" + this.toShortString() + ".wav");
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

        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(148, 200));

        card_image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card_image.setDoubleBuffered(true);
        card_image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                card_imageMouseClicked(evt);
            }
        });

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

            if (isCargada()) {

                if (!isTapada()) {

                    visor = new CardVisorDialog(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), false, this.valor, this.palo, false);

                } else {

                    visor = new CardVisorDialog(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), false, 53, false);

                }

            } else {

                visor = new CardVisorDialog(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), false, 54, false);

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
