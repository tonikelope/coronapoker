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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 *
 * @author tonikelope
 */
public final class GameLogDialog extends JDialog {

    public final static String TITLE = "log.registro_de_la_timba";
    private static volatile String LOG_TEXT = "[CoronaPoker " + AboutDialog.VERSION + " " + Translator.translate("log.registro_de_la_timba_2") + "\n\n";
    private volatile boolean auto_scroll = true;
    private volatile boolean fin_transmision = false;
    // El tamaño/posición por defecto (1280x720 centrado) se aplica solo la PRIMERA
    // vez que se abre este diálogo; cerrarlo solo lo oculta (no se destruye), así
    // que reaperturas posteriores conservan lo que el usuario haya redimensionado
    // o movido.
    private volatile boolean default_bounds_applied = false;
    private final Object log_lock = new Object();
    private JTextArea debug_textarea;
    private JScrollPane debug_scroll;
    private Consumer<String> debug_log_listener;
    private BottomFollower main_follow;
    private BottomFollower debug_follow;

    // Rich rendering: the generated `textarea` (JTextArea) cannot show mixed
    // styles, so the scrollpane's viewport is swapped at runtime to this styled
    // JTextPane. LOG_TEXT stays the plain-text source of truth (so the loser-card
    // regex surgery and the plain-text log-file export are untouched); the styling
    // is DERIVED from the text on every render — language
    // agnostic (card suit glyphs ♠♥♦♣, parenthesised amounts) plus a few
    // hard-coded structural markers (hand header, FLOP/TURN/RIVER, placeholders).
    private JTextPane log_pane;
    private JCheckBoxMenuItem transparent_menu;

    // Console look (PowerShell-ish): near-black background + a monospaced font,
    // identical for the main log and the debug tab.
    private static final Color LOG_BG = new Color(12, 12, 12);
    private static final Font LOG_FONT = new Font("Consolas", Font.PLAIN, 20);

    private static SimpleAttributeSet logStyle(Color c, boolean bold, boolean italic) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        return s;
    }

    private static final SimpleAttributeSet ST_DEFAULT = logStyle(new Color(255, 255, 255), false, false);
    private static final SimpleAttributeSet ST_HEADER = logStyle(new Color(120, 225, 235), true, false);
    private static final SimpleAttributeSet ST_BOARD = logStyle(new Color(150, 200, 255), true, false);
    private static final SimpleAttributeSet ST_AMOUNT = logStyle(new Color(255, 200, 90), true, false);
    private static final SimpleAttributeSet ST_DIM = logStyle(new Color(170, 170, 170), false, true);
    private static final SimpleAttributeSet ST_RANK = logStyle(new Color(205, 205, 205), false, true);
    // Cards rendered like real cards: white background, value+suit red (♥♦) or
    // black (♠♣), with a bigger suit glyph; bracket chars hidden (white-on-white)
    // so a token like [A♠] reads as a clean white "A♠" card face.
    private static final Color CARD_RED = new Color(200, 0, 0);
    // Category line colours (detected by translated phrase — language agnostic).
    private static final SimpleAttributeSet ST_WIN = logStyle(new Color(120, 230, 120), true, false);
    private static final SimpleAttributeSet ST_LOSS = logStyle(new Color(235, 120, 120), false, false);
    private static final SimpleAttributeSet ST_ALERT = logStyle(new Color(255, 80, 80), true, false);
    private static final SimpleAttributeSet ST_BLIND = logStyle(new Color(235, 205, 80), true, false);
    private static final SimpleAttributeSet ST_RIT = logStyle(new Color(200, 150, 235), true, false);

    // [A♠], [10♥] — a bracketed card token (value + suit).
    private static final Pattern CARD_TOKEN = Pattern.compile("\\[[^\\[\\]]*[♠♥♦♣]\\]");
    // A parenthesised numeric amount: (120), (1,5K), (-50). Card groups never
    // match (they have no leading digit), so this won't steal card coloring.
    private static final Pattern AMOUNT = Pattern.compile("\\(\\s*-?\\d[\\d.,\\s]*[KkMm]?\\)");
    // Deferred / mucked hole-card placeholders.
    private static final Pattern PLACEHOLDER = Pattern.compile("\\((?:---|\\*\\*\\*)\\)");
    // A parenthesised group of ONLY card tokens, e.g. ([K♥] [9♦]) — the parens are
    // dropped on display (the white card chips read fine on their own); LOG_TEXT
    // (export) keeps them.
    private static final Pattern CARD_PAREN = Pattern.compile("\\(\\s*((?:\\[[^\\[\\]]*[♠♥♦♣]\\]\\s*)+)\\)");

    // Balance table (see Crupier.auditorCuentas): each line starts with a 4-char
    // marker token the renderer swaps for a small icon while keeping the columns
    // aligned (fixed-width marker). Tokens:
    //   "(##)" column-header row (NICK/STACK/BUYIN labels, blank marker)
    //   "(D )" dealer  "(SB)" small blind  "(BB)" big blind  "(  )" no role
    //   "(ST)" straddle  "(DS)" dealer+straddle (3-manos: el dealer es el UTG)
    //   "($$)" the AUDITOR DE CUENTAS totals line (money icon)
    // Data rows are monospace-padded columns: nick / stack / buyin.
    // The two trailing right-justified numeric columns (stack, buyin), anchored at
    // the end so a nick containing digits can't be mistaken for them.
    private static final Pattern BALANCE_NUMS = Pattern.compile("\\s{2,}(\\S+)\\s{2,}(\\S+)\\s*$");
    private static final int ROLE_ICON_PX = 17;
    private static final int ROLE_MARKER_W = 26;
    private static javax.swing.ImageIcon ROLE_DEALER, ROLE_SB, ROLE_BB, ROLE_MONEY, ROLE_STRADDLE, ROLE_DEALER_STRADDLE;

    private static boolean isBalanceRow(String line) {
        if (line.length() < 4) {
            return false;
        }
        String t = line.substring(0, 4);
        return t.equals("(D )") || t.equals("(SB)") || t.equals("(BB)") || t.equals("(  )")
                || t.equals("(##)") || t.equals("($$)") || t.equals("(ST)") || t.equals("(A )") || t.equals("(DS)")
                || t.equals("(MV)");
    }

    // Clears the pane and re-renders the whole text (used by setText paths:
    // initial load and loser-card reveal).
    private void renderAll(String fullText) {
        StyledDocument doc = log_pane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ex) {
        }
        appendStyled(fullText);
    }

    // Appends a chunk (one or more lines) with per-line + per-token styling.
    private void appendStyled(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        StyledDocument doc = log_pane.getStyledDocument();
        int i = 0, n = text.length();
        while (i < n) {
            int nl = text.indexOf('\n', i);
            String line = (nl < 0) ? text.substring(i) : text.substring(i, nl + 1);
            i = (nl < 0) ? n : nl + 1;
            appendStyledLine(doc, CARD_PAREN.matcher(line).replaceAll("$1"));
        }
    }

    private void appendStyledLine(StyledDocument doc, String line) {
        int len = line.length();
        if (len == 0) {
            return;
        }
        if (isBalanceRow(line)) {
            try {
                appendBalanceRow(doc, line);
            } catch (Throwable ex) {
                try {
                    doc.insertString(doc.getLength(), line, ST_DEFAULT);
                } catch (BadLocationException ignored) {
                }
            }
            return;
        }
        appendNormalLine(doc, line);
    }

    private void appendNormalLine(StyledDocument doc, String line) {
        appendNormalLine(doc, line, null);
    }

    // forcedBase != null fuerza el color base de la linea (la tabla MULTIVERSO la
    // pinta atenuada) saltandose lineBaseStyle; los overlays de cartas / importes /
    // placeholder siguen aplicandose igual.
    private void appendNormalLine(StyledDocument doc, String line, SimpleAttributeSet forcedBase) {
        int len = line.length();
        if (len == 0) {
            return;
        }
        SimpleAttributeSet base = forcedBase != null ? forcedBase : lineBaseStyle(line);
        SimpleAttributeSet[] cs = new SimpleAttributeSet[len];
        for (int k = 0; k < len; k++) {
            cs[k] = base;
        }
        // hand-ranking ("... -> Pareja"): dim italic (not on header/board lines).
        if (base != ST_HEADER && base != ST_BOARD) {
            int arrow = line.indexOf(" -> ");
            if (arrow >= 0) {
                for (int k = arrow; k < len; k++) {
                    cs[k] = ST_RANK;
                }
            }
        }
        overlay(cs, line, PLACEHOLDER, ST_DIM);
        overlay(cs, line, AMOUNT, ST_AMOUNT);
        try {
            // Normal-mode [A♠] cards are inserted as uniform white card COMPONENTS
            // (so the bigger suit can't deform a per-character background); the rest
            // goes in as styled runs.
            Matcher m = CARD_TOKEN.matcher(line);
            int pos = 0;
            while (m.find()) {
                insertStyledRun(doc, line, cs, pos, m.start());
                SimpleAttributeSet ca = new SimpleAttributeSet();
                StyleConstants.setComponent(ca, makeCard(line.substring(m.start(), m.end())));
                doc.insertString(doc.getLength(), " ", ca);
                pos = m.end();
            }
            insertStyledRun(doc, line, cs, pos, len);
        } catch (Throwable ex) {
            // A styling glitch must never break the log or freeze a re-render.
            // Fall back to plain text.
            try {
                doc.insertString(doc.getLength(), line, ST_DEFAULT);
            } catch (BadLocationException ignored) {
            }
        }
    }

    private static void insertStyledRun(StyledDocument doc, String line, SimpleAttributeSet[] cs, int from, int to) throws BadLocationException {
        int run = from;
        while (run < to) {
            int end = run + 1;
            SimpleAttributeSet a = cs[run];
            while (end < to && cs[end] == a) {
                end++;
            }
            doc.insertString(doc.getLength(), line.substring(run, end), a);
            run = end;
        }
    }

    private static void overlay(SimpleAttributeSet[] cs, String line, Pattern p, SimpleAttributeSet st) {
        Matcher m = p.matcher(line);
        while (m.find()) {
            for (int k = m.start(); k < m.end(); k++) {
                cs[k] = st;
            }
        }
    }

    // Renders a balance/MULTIVERSO table line: swaps the leading 4-char marker
    // token for a small icon (role / money / blank) in the LEFT GUTTER (outside the
    // box, fixed width so the grid stays aligned whatever the role), then renders
    // the rest:
    //   "(##)" -> bordes / cabecera / separadores de la rejilla, todo atenuado.
    //   "(MV)" -> fila MULTIVERSO: rejilla atenuada + contenido atenuado + cartas
    //             como fichas a la derecha del marco.
    //   resto  -> fila de cuentas / totales / aviso del auditor: rejilla atenuada +
    //             contenido en color normal (importes en ambar).
    private void appendBalanceRow(StyledDocument doc, String line) throws BadLocationException {
        String token = line.substring(0, 4);
        String rest = line.substring(4);
        SimpleAttributeSet ca = new SimpleAttributeSet();
        StyleConstants.setComponent(ca, makeRoleMarker(token));
        doc.insertString(doc.getLength(), " ", ca);
        if (token.equals("(##)")) {
            doc.insertString(doc.getLength(), rest, ST_DIM);
            return;
        }
        appendGridLine(doc, rest, token.equals("(MV)") ? ST_DIM : ST_DEFAULT);
    }

    // Renders a table line where the box-drawing characters (the grid: ─│┌┐└┘├┤┬┴┼,
    // rango Unicode U+2500..U+257F) van atenuados y el resto en `contentStyle`. Los
    // importes entre parentesis se pintan en ambar (dinero / bote sobrante) y los
    // tokens de carta [A♠] se insertan como fichas — asi una tabla con bordes
    // conserva las fichas y resalta el importe.
    private void appendGridLine(StyledDocument doc, String line, SimpleAttributeSet contentStyle) {
        int len = line.length();
        if (len == 0) {
            return;
        }
        SimpleAttributeSet[] cs = new SimpleAttributeSet[len];
        for (int k = 0; k < len; k++) {
            char c = line.charAt(k);
            cs[k] = (c >= '─' && c <= '╿') ? ST_DIM : contentStyle;
        }
        overlay(cs, line, AMOUNT, ST_AMOUNT);
        try {
            Matcher m = CARD_TOKEN.matcher(line);
            int pos = 0;
            while (m.find()) {
                insertStyledRun(doc, line, cs, pos, m.start());
                SimpleAttributeSet cc = new SimpleAttributeSet();
                StyleConstants.setComponent(cc, makeCard(line.substring(m.start(), m.end())));
                doc.insertString(doc.getLength(), " ", cc);
                pos = m.end();
            }
            insertStyledRun(doc, line, cs, pos, len);
        } catch (Throwable ex) {
            try {
                doc.insertString(doc.getLength(), line, contentStyle);
            } catch (BadLocationException ignored) {
            }
        }
    }

    // Small fixed-width role marker for a balance row: paints the dealer/SB/BB icon
    // (or nothing for "(  )"), so the nick column stays aligned across rows whatever
    // the role.
    private static javax.swing.JComponent makeRoleMarker(String token) {
        javax.swing.JLabel l = new javax.swing.JLabel();
        l.setOpaque(false);
        l.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        javax.swing.ImageIcon ic = roleIcon(token);
        if (ic != null) {
            l.setIcon(ic);
        }
        java.awt.Dimension d = new java.awt.Dimension(ROLE_MARKER_W, ROLE_ICON_PX + 4);
        l.setPreferredSize(d);
        l.setMinimumSize(d);
        l.setMaximumSize(d);
        l.setAlignmentY(0.82f);
        return l;
    }

    private static javax.swing.ImageIcon roleIcon(String token) {
        try {
            switch (token) {
                case "(D )":
                    if (ROLE_DEALER == null) {
                        ROLE_DEALER = scaledRoleIcon("/images/dealer.png");
                    }
                    return ROLE_DEALER;
                case "(SB)":
                    if (ROLE_SB == null) {
                        ROLE_SB = scaledRoleIcon("/images/sb.png");
                    }
                    return ROLE_SB;
                case "(BB)":
                    if (ROLE_BB == null) {
                        ROLE_BB = scaledRoleIcon("/images/bb.png");
                    }
                    return ROLE_BB;
                case "($$)":
                case "(A )":
                    if (ROLE_MONEY == null) {
                        ROLE_MONEY = scaledRoleIcon("/images/chips.png");
                    }
                    return ROLE_MONEY;
                case "(ST)":
                    if (ROLE_STRADDLE == null) {
                        ROLE_STRADDLE = scaledRoleIcon("/images/straddle.png");
                    }
                    return ROLE_STRADDLE;
                case "(DS)":
                    if (ROLE_DEALER_STRADDLE == null) {
                        ROLE_DEALER_STRADDLE = scaledRoleIcon("/images/dealer_straddle.png");
                    }
                    return ROLE_DEALER_STRADDLE;
                default:
                    return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static javax.swing.ImageIcon scaledRoleIcon(String resource) {
        java.awt.Image img = new javax.swing.ImageIcon(GameLogDialog.class.getResource(resource)).getImage();
        return new javax.swing.ImageIcon(img.getScaledInstance(ROLE_ICON_PX, ROLE_ICON_PX, java.awt.Image.SCALE_SMOOTH));
    }

    // Builds a uniform white, rounded-corner card chip for a [A♠] token: value +
    // a bigger suit glyph, red (♥♦) or black (♠♣). One component = one background,
    // so the bigger suit never deforms it.
    private static javax.swing.JComponent makeCard(String token) {
        String inner = token.substring(1, token.length() - 1);
        boolean red = inner.indexOf('♥') >= 0 || inner.indexOf('♦') >= 0;
        String suit = inner.substring(inner.length() - 1);
        String value = inner.substring(0, inner.length() - 1);
        final java.awt.Color fg = red ? CARD_RED : java.awt.Color.BLACK;
        javax.swing.JLabel card = new javax.swing.JLabel(
                "<html><span style=\"font-family:Consolas\">" + value
                + "<span style=\"font-size:28pt\">" + suit + "</span></span></html>") {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(java.awt.Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setForeground(fg);
        card.setFont(new java.awt.Font("Consolas", java.awt.Font.BOLD, 22));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 7, 1, 7));
        card.setAlignmentY(0.82f);
        // The HTML label reports a huge maximum width, so the text view stretches
        // it to fill the line. Pin max = preferred so each card stays card-sized.
        java.awt.Dimension pref = card.getPreferredSize();
        card.setMinimumSize(pref);
        card.setMaximumSize(pref);
        return card;
    }

    private static SimpleAttributeSet lineBaseStyle(String line) {
        if (line.contains("***************")) {
            return ST_HEADER;
        }
        String t = line.stripLeading();
        // Titulos enmarcados con caracteres de caja (en vez de tiras de asteriscos):
        // marco SIMPLE (┌─┐/│/└) -> estilo de cabecera (cian); marco DOBLE (╔═╗/║/╚)
        // -> estilo de alerta (rojo, p. ej. parada del server). Las tablas con bordes
        // (cuentas/MULTIVERSO) NO llegan aqui (van por appendBalanceRow al llevar
        // token), asi que esta deteccion por caracter de caja inicial no colisiona.
        if (!t.isEmpty()) {
            char c0 = t.charAt(0);
            if (c0 == '╔' || c0 == '║' || c0 == '╚') {
                return ST_ALERT;
            }
            if (c0 == '┌' || c0 == '│' || c0 == '└') {
                return ST_HEADER;
            }
        }
        if (t.startsWith("FLOP -> ") || t.startsWith("TURN -> ") || t.startsWith("RIVER -> ")) {
            return ST_BOARD;
        }
        for (Object[] rule : categoryRules()) {
            if (line.contains((String) rule[0])) {
                return (SimpleAttributeSet) rule[1];
            }
        }
        if (t.startsWith("PAUSE (") || line.contains("LOKI:")) {
            return ST_DIM;
        }
        return ST_DEFAULT;
    }

    private static volatile java.util.List<Object[]> CATEGORY_RULES;
    private static volatile String CATEGORY_RULES_LANG;

    // Maps a translated marker phrase -> line style, built from the SAME Translator
    // the app uses, so detection works in ANY language without touching the 65 print
    // callsites. Priority = list order (first match wins). Las reglas se registran
    // en el idioma ACTIVO (+ inglés forzado); si el usuario cambia de idioma en la
    // misma sesión (p. ej. juega una timba en inglés y otra en español) hay que
    // RECONSTRUIRLAS, porque si no las frases del nuevo idioma no casarían y la línea
    // saldría sin color (blanca). Por eso se cachea junto al idioma con el que se
    // construyó y se rehace en cuanto GameFrame.LANGUAGE cambia.
    private static java.util.List<Object[]> categoryRules() {
        java.util.List<Object[]> rules = CATEGORY_RULES;
        if (rules == null || !java.util.Objects.equals(CATEGORY_RULES_LANG, GameFrame.LANGUAGE)) {
            rules = new java.util.ArrayList<>();
            for (String k : new String[]{"zero_trust.security_alert", "zero_trust.suspicious_alert", "zero_trust.peer_alert", "zero_trust.lockdown_activated",
                "game.mano_verificacion_divergente", "game.mano_verificacion_jugador_ausente", "game.mano_verificacion_firma_invalida",
                "game.mano_verificacion_host_sin_prueba", "game.firma_accion_invalida"}) {
                addCategoryRule(rules, k, ST_ALERT);
            }
            // Consenso + las dos líneas del barajado (verde "verificado", amarillo "sin verificar
            // todavía"). Llevan el ordinal {0}, pero addCategoryRule ya casa por el prefijo fijo.
            addCategoryRule(rules, "game.mano_verificada_consenso", ST_WIN);
            addCategoryRule(rules, "game.barajado_verificado", ST_WIN);
            addCategoryRule(rules, "game.barajado_pendiente", ST_BLIND);
            for (String k : new String[]{"game.gana_bote_2", "game.gana_bote_principal", "game.gana_bote_secundario", "game.gana_bote"}) {
                addCategoryRule(rules, k, ST_WIN);
            }
            for (String k : new String[]{"game.pierde_bote_principal", "game.pierde_bote_secundario", "game.pierde_bote"}) {
                addCategoryRule(rules, k, ST_LOSS);
            }
            addCategoryRule(rules, "blinds.se_doblan_las_ciegas", ST_BLIND);
            for (String k : new String[]{"runittwice.log_accepted", "runittwice.log_rejected", "runittwice.log_fin_a", "runittwice.log_fin_b"}) {
                addCategoryRule(rules, k, ST_RIT);
            }
            CATEGORY_RULES = rules;
            CATEGORY_RULES_LANG = GameFrame.LANGUAGE;
        }
        return rules;
    }

    private static void addCategoryRule(java.util.List<Object[]> rules, String key, SimpleAttributeSet style) {
        // Registra el marcador en el idioma ACTIVO y tambien en INGLES forzado (alguna linea del
        // registro puede salir en un idioma distinto al de construccion de las reglas). El marcador
        // es la frase hasta el primer "{": asi las claves con {0} (p. ej. el ordinal de mano) casan
        // por su PREFIJO fijo, ya que la linea real lleva el valor formateado y no el placeholder
        // (sin esto, cualquier mensaje de categoria con {0} quedaba SIN color).
        addCategoryPhrase(rules, Translator.translate(key), key, style);
        addCategoryPhrase(rules, Translator.translate(key, true), key, style);
    }

    private static void addCategoryPhrase(java.util.List<Object[]> rules, String phrase, String key, SimpleAttributeSet style) {
        if (phrase == null || phrase.equals(key)) {
            return; // clave no encontrada (translate devuelve la propia clave)
        }
        int brace = phrase.indexOf('{');
        if (brace >= 0) {
            phrase = phrase.substring(0, brace);
        }
        phrase = phrase.trim();
        if (phrase.length() >= 3) {
            for (Object[] r : rules) {
                if (phrase.equals(r[0])) {
                    return; // ya registrada (misma frase en ambos idiomas)
                }
            }
            rules.add(new Object[]{phrase, style});
        }
    }

    // Swaps the scrollpane viewport to a styled JTextPane AND turns the dialog into
    // a borderless, movable, resizable HUD. Window opacity < 1 needs an undecorated
    // window in Swing, so instead of flipping decoration at runtime (flaky) the log
    // is undecorated from the start and "Transparente" simply toggles opacity.
    // initComponents() already pack()'d the dialog (making it displayable), so we
    // dispose() once before setUndecorated(true); the constructor's final pack()
    // re-realizes it and it is never shown in between. Custom chrome replaces the
    // lost native frame: menu-bar drag, a bottom-right resize grip and a Close item.
    private void setupLogPane() {
        // El incremento de unidad de JTextComponent es visibleRect.height / 10,
        // así que cada muesca de rueda mueve ~3 × (1/10 del viewport): varias
        // líneas, y más cuanto mayor sea la ventana. Lo fijamos a la altura de
        // una línea para que la rueda avance las líneas que indique el sistema
        // (3 en Windows) sea cual sea el tamaño del panel, como un editor normal.
        log_pane = new JTextPane() {
            @Override
            public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
                if (orientation == javax.swing.SwingConstants.VERTICAL) {
                    return Math.max(1, getFontMetrics(getFont()).getHeight());
                }
                return super.getScrollableUnitIncrement(visibleRect, orientation, direction);
            }
        };
        log_pane.setEditable(false);
        log_pane.setBackground(LOG_BG);
        log_pane.setForeground(new Color(230, 230, 230));
        log_pane.setFont(LOG_FONT);
        jScrollPane1.setViewportView(log_pane);

        dispose();
        setUndecorated(true);
        // No border at all — the console sits flush. Resize is via corner grips (below).
        getRootPane().setBorder(null);
        getRootPane().setOpaque(true);
        getRootPane().setBackground(LOG_BG);

        // Transparency = 90% opacity (works because the window is undecorated). ON by default.
        transparent_menu = new JCheckBoxMenuItem();
        transparent_menu.setFont(new Font("Dialog", Font.PLAIN, 14));
        transparent_menu.setText("Transparente");
        transparent_menu.putClientProperty("i18n.key", "ui.registro_transparente");
        transparent_menu.setSelected(true);
        transparent_menu.addActionListener(evt -> applyLogOpacity(transparent_menu.isSelected()));
        opciones_menu.add(transparent_menu);

        // Mover el HUD arrastrando el menú (y su tira de relleno) o la barra de
        // título (ver setupTitleBar). Basado en coordenadas de pantalla para que
        // funcione sea cual sea el subcomponente que dispare el evento. El botón de
        // cerrar (X) vive ahora en la barra de título.
        java.awt.event.MouseAdapter dragAdapter = windowDragAdapter();
        jMenuBar1.addMouseListener(dragAdapter);
        jMenuBar1.addMouseMotionListener(dragAdapter);
        java.awt.Component filler = javax.swing.Box.createHorizontalGlue();
        filler.addMouseListener(dragAdapter);
        filler.addMouseMotionListener(dragAdapter);
        jMenuBar1.add(filler);

        // Resize from the 4 corners (invisible grips on the layered pane — no border
        // needed, so the console sits flush). Each anchors the opposite corner.
        final int GS = 14;
        final int[] cursors = {
            java.awt.Cursor.NW_RESIZE_CURSOR, java.awt.Cursor.NE_RESIZE_CURSOR,
            java.awt.Cursor.SW_RESIZE_CURSOR, java.awt.Cursor.SE_RESIZE_CURSOR
        };
        final javax.swing.JLabel[] gr = new javax.swing.JLabel[4];
        for (int ci = 0; ci < 4; ci++) {
            final int corner = ci;
            javax.swing.JLabel g = new javax.swing.JLabel();
            g.setSize(GS, GS);
            g.setCursor(java.awt.Cursor.getPredefinedCursor(cursors[ci]));
            getLayeredPane().add(g, javax.swing.JLayeredPane.PALETTE_LAYER);
            gr[ci] = g;
            final int[] st = new int[6]; // mouseScreenX, mouseScreenY, x, y, w, h at press
            g.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    java.awt.Point p = e.getLocationOnScreen();
                    st[0] = p.x;
                    st[1] = p.y;
                    st[2] = getX();
                    st[3] = getY();
                    st[4] = getWidth();
                    st[5] = getHeight();
                }
            });
            g.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    java.awt.Point p = e.getLocationOnScreen();
                    int dx = p.x - st[0], dy = p.y - st[1];
                    int x = st[2], y = st[3], w = st[4], h = st[5];
                    int nw, nh, nx, ny;
                    switch (corner) {
                        case 0: nw = Math.max(360, w - dx); nh = Math.max(240, h - dy); nx = x + w - nw; ny = y + h - nh; break; // NW
                        case 1: nw = Math.max(360, w + dx); nh = Math.max(240, h - dy); nx = x; ny = y + h - nh; break; // NE
                        case 2: nw = Math.max(360, w - dx); nh = Math.max(240, h + dy); nx = x + w - nw; ny = y; break; // SW
                        default: nw = Math.max(360, w + dx); nh = Math.max(240, h + dy); nx = x; ny = y; break; // SE
                    }
                    setBounds(nx, ny, nw, nh);
                }
            });
        }
        final Runnable placeGrips = () -> {
            int w = getLayeredPane().getWidth(), h = getLayeredPane().getHeight();
            gr[0].setLocation(0, 0);
            gr[1].setLocation(w - GS, 0);
            gr[2].setLocation(0, h - GS);
            gr[3].setLocation(w - GS, h - GS);
        };
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                placeGrips.run();
            }
        });
        placeGrips.run();
    }

    // Adaptador para mover la ventana (sin bordes) arrastrando una zona-barra.
    // Basado en coordenadas de pantalla para funcionar sea cual sea el
    // subcomponente que dispare el evento. Cada llamada crea su propio estado
    // (solo hay un arrastre a la vez, así que compartirlo entre componentes de la
    // misma barra también valdría).
    private java.awt.event.MouseAdapter windowDragAdapter() {
        final java.awt.Point[] off = {null};
        return new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                java.awt.Point sp = e.getLocationOnScreen();
                off[0] = new java.awt.Point(sp.x - getX(), sp.y - getY());
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                off[0] = null;
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (off[0] != null) {
                    java.awt.Point sp = e.getLocationOnScreen();
                    setLocation(sp.x - off[0].x, sp.y - off[0].y);
                }
            }
        };
    }

    // Barra de título personalizada. La ventana es sin bordes (para poder ser
    // semitransparente), así que no hay barra nativa: ponemos la nuestra con el
    // nombre y el botón de cerrar. Va ENCIMA del menú — como el JMenuBar nativo
    // ocupa el slot superior del root pane, lo sacamos de ahí (setJMenuBar(null)) y
    // lo apilamos bajo la barra de título en el NORTH del content pane (que
    // setupDebugTab ya dejó en BorderLayout con las pestañas en CENTER).
    private void setupTitleBar() {
        setJMenuBar(null);

        javax.swing.JLabel title = new javax.swing.JLabel("CoronaPoker - " + Translator.translate("log.registro"));
        title.setForeground(new Color(70, 70, 70));
        title.setFont(new Font("Dialog", Font.BOLD, 14));
        title.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 12, 4, 12));

        final javax.swing.JLabel close_btn = new javax.swing.JLabel("X");
        close_btn.setFont(new Font("Dialog", Font.BOLD, 16));
        close_btn.setForeground(new Color(70, 70, 70)); // barra clara -> X oscura
        close_btn.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 12, 0, 14));
        close_btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        close_btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                setVisible(false);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                close_btn.setForeground(new Color(215, 40, 40));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                close_btn.setForeground(new Color(70, 70, 70));
            }
        });

        javax.swing.JPanel title_bar = new javax.swing.JPanel(new BorderLayout());
        title_bar.setBackground(jMenuBar1.getBackground());
        title_bar.add(title, BorderLayout.WEST);
        title_bar.add(close_btn, BorderLayout.EAST);

        java.awt.event.MouseAdapter drag = windowDragAdapter();
        title_bar.addMouseListener(drag);
        title_bar.addMouseMotionListener(drag);
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);

        javax.swing.JPanel north = new javax.swing.JPanel(new BorderLayout());
        north.add(title_bar, BorderLayout.NORTH);
        north.add(jMenuBar1, BorderLayout.CENTER);

        getContentPane().add(north, BorderLayout.NORTH);
    }

    private void applyLogOpacity(boolean transparent) {
        try {
            setOpacity(transparent ? 0.9f : 1.0f);
        } catch (Exception | Error ex) {
            // Window translucency not supported on this platform — ignore.
        }
    }

    // Margen en pixeles para "casi al fondo": si el viewport esta a menos de
    // AT_BOTTOM_TOLERANCE_PX del fondo, lo consideramos "al fondo" para smart
    // autoscroll (un usuario que esta a 1-2 lineas del final no ha "subido a
    // leer", esta esperando el proximo mensaje).
    private static final int AT_BOTTOM_TOLERANCE_PX = 30;

    private static boolean isAtBottom(JScrollPane sp) {
        if (sp == null) {
            return true;
        }
        JScrollBar vbar = sp.getVerticalScrollBar();
        return vbar.getValue() + vbar.getVisibleAmount() >= vbar.getMaximum() - AT_BOTTOM_TOLERANCE_PX;
    }

    public static void resetLOG() {
        LOG_TEXT = "[CoronaPoker " + AboutDialog.VERSION + " " + Translator.translate("log.registro_de_la_timba_2") + "\n\n";
    }

    public void setFin_transmision(boolean fin_transmision) {
        this.fin_transmision = fin_transmision;
    }

    public boolean isAuto_scroll() {
        return auto_scroll;
    }

    public boolean isDefaultBoundsApplied() {
        return default_bounds_applied;
    }

    public void setDefaultBoundsApplied(boolean default_bounds_applied) {
        this.default_bounds_applied = default_bounds_applied;
    }

    /**
     * Creates new form Registro
     */
    public GameLogDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        setupLogPane();

        main_follow = new BottomFollower(jScrollPane1, log_pane);

        Helpers.setTranslatedTitle(this, TITLE);

        Helpers.JTextFieldRegularPopupMenu.addTo(log_pane);

        Helpers.updateFonts(jMenuBar1, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        renderAll(GameLogDialog.LOG_TEXT);

        setupDebugTab();

        setupTitleBar();

        // Cada vez que el dialog se hace visible (apertura o reapertura tras
        // dispose) saltamos al final y reanudamos el seguimiento en ambas
        // pestañas — al abrir el registro el usuario quiere ver lo más reciente.
        // snapToBottom() ya difiere el scroll (invokeLater) para que ocurra
        // DESPUES del layout del viewport.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent evt) {
                if (main_follow != null) {
                    main_follow.snapToBottom();
                }
                if (debug_follow != null) {
                    debug_follow.snapToBottom();
                }
            }
        });

        pack();

        // Compact default size (resizable from any corner). Transparency ON (90%).
        setSize(720, 430);
        applyLogOpacity(transparent_menu.isSelected());

    }

    private void setupDebugTab() {

        debug_textarea = new JTextArea();
        debug_textarea.setEditable(false);
        debug_textarea.setBackground(LOG_BG);
        debug_textarea.setForeground(new Color(220, 220, 220));
        debug_textarea.setFont(LOG_FONT);
        debug_textarea.setLineWrap(true);
        debug_textarea.setWrapStyleWord(false);
        Helpers.JTextFieldRegularPopupMenu.addTo(debug_textarea);

        debug_scroll = new JScrollPane(debug_textarea);

        debug_follow = new BottomFollower(debug_scroll, debug_textarea);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Dialog", Font.BOLD, 16));
        tabs.addTab(Translator.translate("log.registro"), jScrollPane1);
        tabs.addTab(Translator.translate("log.debug"), debug_scroll);

        getContentPane().remove(jScrollPane1);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabs, BorderLayout.CENTER);

        debug_textarea.setText(DebugLog.snapshot());
        debug_textarea.setCaretPosition(debug_textarea.getDocument().getLength());

        debug_log_listener = (String record) -> Helpers.GUIRun(() -> {
            try {
                debug_textarea.append(record);
                if (auto_scroll && debug_follow != null) {
                    debug_follow.followIfNeeded();
                }
            } catch (Throwable t) {
                // Textarea may be in transition between dispose/re-show; skip.
            }
        });
        DebugLog.subscribe(debug_log_listener);
    }

    public JTextComponent getTextArea() {
        return log_pane;
    }

    public String getText() {
        return LOG_TEXT;
    }

    public void actualizarCartasPerdedores(ConcurrentHashMap<Player, Hand> perdedores) {

        synchronized (log_lock) {

            if (perdedores != null && !perdedores.isEmpty()) {
                for (Map.Entry<Player, Hand> entry : perdedores.entrySet()) {

                    Player perdedor = entry.getKey();

                    Hand jugada = entry.getValue();

                    if (!"".equals(perdedor.getHoleCard1().getValor()) && ((perdedor != GameFrame.getInstance().getLocalPlayer() && !perdedor.getHoleCard1().isTapada()) || (perdedor == GameFrame.getInstance().getLocalPlayer() && GameFrame.getInstance().getLocalPlayer().isMuestra()))) {

                        String hole_cards_string = Card.collection2String(perdedor.getHoleCards());

                        String jugada_string = jugada.toString();

                        GameLogDialog.LOG_TEXT = GameLogDialog.LOG_TEXT.replaceAll(perdedor.getNickname().replace("$", "\\$") + " +[(]---[)] +(\\w+ .+)", perdedor.getNickname().replace("$", "\\$") + " (" + hole_cards_string + ") $1 -> " + jugada_string);

                    } else {

                        GameLogDialog.LOG_TEXT = GameLogDialog.LOG_TEXT.replaceAll(perdedor.getNickname().replace("$", "\\$") + " +[(]---[)]", perdedor.getNickname().replace("$", "\\$") + " (***)");

                    }
                }

                Helpers.GUIRunAndWait(() -> {
                    renderAll(GameLogDialog.LOG_TEXT);

                    if (auto_scroll && main_follow != null) {
                        main_follow.followIfNeeded();
                    }
                });
            }
        }
    }

    public void print(String msg) {

        if (!this.fin_transmision) {

            // logRun (no threadRun): un unico hilo consumidor en orden FIFO, para que
            // las lineas del registro se apliquen en el orden en que se llamo a print()
            // y no se reordenen entre si (el pool general multi-hilo lo permitia).
            Helpers.logRun(() -> {
                synchronized (log_lock) {
                    String message = Translator.translate(msg);
                    GameLogDialog.LOG_TEXT += message + "\n\n";
                    Helpers.GUIRun(() -> {
                        appendStyled(message + "\n\n");

                        if (auto_scroll && main_follow != null) {
                            main_follow.followIfNeeded();
                        }
                    });
                }
            });
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

        jScrollPane1 = new javax.swing.JScrollPane();
        textarea = new javax.swing.JTextArea();
        jMenuBar1 = new javax.swing.JMenuBar();
        opciones_menu = new javax.swing.JMenu();
        auto_scroll_menu = new javax.swing.JCheckBoxMenuItem();

        setTitle("REGISTRO");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });


        textarea.setEditable(false);
        textarea.setBackground(new java.awt.Color(102, 102, 102));
        textarea.setColumns(20);
        textarea.setFont(new java.awt.Font("DejaVu Sans", 0, 20)); // NOI18N
        textarea.setForeground(new java.awt.Color(255, 255, 255));
        textarea.setLineWrap(true);
        textarea.setRows(5);
        textarea.setText("\n");
        jScrollPane1.setViewportView(textarea);


        opciones_menu.setMnemonic('p');
        opciones_menu.setText("Preferencias");
        opciones_menu.putClientProperty("i18n.key", "menu.preferencias");
        opciones_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        auto_scroll_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_scroll_menu.setSelected(true);
        auto_scroll_menu.setText("Auto scroll");
        auto_scroll_menu.putClientProperty("i18n.key", "ui.auto_scroll");
        auto_scroll_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_scroll_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(auto_scroll_menu);

        jMenuBar1.add(opciones_menu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 962, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 493, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void auto_scroll_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_scroll_menuActionPerformed
        this.auto_scroll = this.auto_scroll_menu.isSelected();
        // Al reactivar el autoscroll, ponerse al día saltando a lo más reciente.
        if (this.auto_scroll) {
            if (main_follow != null) {
                main_follow.snapToBottom();
            }
            if (debug_follow != null) {
                debug_follow.snapToBottom();
            }
        }
    }//GEN-LAST:event_auto_scroll_menuActionPerformed

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // TODO add your handling code here:
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    // Autoscroll "inteligente": sigue el fondo mientras el usuario está parado
    // ahí y deja de seguir en cuanto sube a leer — SIN el frágil muestreo
    // "antes/después" de geometría que usaba el registro.
    //
    // Por qué el rediseño: el código viejo muestreaba isAtBottom() justo antes de
    // cada append y scrolleaba SÍNCRONO. Un append con estilo puede embeber cartas
    // o iconos de rol como componentes cuya altura no se conoce hasta que el panel
    // se re-maqueta (un revalidate que se publica como evento POSTERIOR del EDT),
    // así que el scroll síncrono se clampaba al panel aún pequeño y se quedaba
    // CORTO del fondo real. El siguiente mensaje muestreaba entonces "no está al
    // fondo" y restauraba el caret viejo, con lo que el registro se alejaba más
    // del fondo en cada mensaje y el autoscroll parecía apagarse solo hasta que el
    // usuario arrastraba de nuevo al fondo.
    //
    // Esto modela la intención del usuario como estado pegajoso: `follow` empieza
    // en true y solo cambia con un gesto real (rueda, ratón sobre la barra o
    // teclado) — subir => deja de seguir, volver al fondo => reanuda. Los scrolls
    // programáticos nunca lo tocan, así que un retraso transitorio de layout no
    // puede desactivar el seguimiento. El salto al fondo va en invokeLater para
    // ejecutarse DESPUÉS del revalidate del append y llegar al fondo de verdad.
    private static final class BottomFollower {

        private final JScrollPane scroll;
        private final JTextComponent view;
        private volatile boolean follow = true;

        BottomFollower(JScrollPane scroll, JTextComponent view) {
            this.scroll = scroll;
            this.view = view;

            // NEVER_UPDATE: un log no editable no debe auto-scrollear solo porque
            // el documento cambió en el EDT (la política por defecto tira la vista
            // al caret en cada append, arrastrando hacia abajo a quien está
            // leyendo). Conducimos TODO el scroll explícitamente.
            if (view.getCaret() instanceof javax.swing.text.DefaultCaret) {
                ((javax.swing.text.DefaultCaret) view.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
            }

            // Reevaluar "¿está el usuario parado al fondo?" tras cualquier gesto de
            // ratón sobre la barra (arrastre del pulgar, clic en la pista, flechas),
            // sobre la rueda o con el teclado (más abajo). Los scrolls programáticos
            // no pasan por ratón/rueda/teclado, así que nunca cambian el flag: un
            // retraso de layout no puede apagar el seguimiento.
            java.awt.event.MouseAdapter reeval = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    follow = false; // agarró la barra: no pelear con su arrastre
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    SwingUtilities.invokeLater(() -> follow = isAtBottom(scroll));
                }
            };
            scroll.getVerticalScrollBar().addMouseListener(reeval);

            // OJO: el listener va en el JScrollPane, NO en la vista. Un
            // MouseWheelEvent solo "burbujea" hasta el JScrollPane (que hace el
            // scroll) si el componente bajo el cursor NO tiene MouseWheelListener;
            // ponérselo a la vista se lo comería y la rueda dejaría de scrollear.
            // En el JScrollPane convive con el listener de scroll por defecto.
            scroll.addMouseWheelListener((java.awt.event.MouseWheelEvent e) -> {
                if (e.getWheelRotation() < 0) {
                    follow = false; // subir = el usuario quiere leer; dejar de seguir ya
                }
                SwingUtilities.invokeLater(() -> follow = isAtBottom(scroll));
            });

            // Navegación con teclado sobre el panel enfocado (flechas, AvPág/RePág,
            // Inicio/Fin): el KeyListener corre ANTES de que la acción de caret
            // mueva/scrolee, así que las teclas "hacia arriba" paran el seguimiento
            // al vuelo y el invokeLater reevalúa la posición ya scrolleada (así
            // bajar de nuevo al fondo reanuda).
            view.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case java.awt.event.KeyEvent.VK_UP:
                        case java.awt.event.KeyEvent.VK_KP_UP:
                        case java.awt.event.KeyEvent.VK_PAGE_UP:
                        case java.awt.event.KeyEvent.VK_HOME:
                            follow = false; // navegar hacia arriba = leer; dejar de seguir ya
                            break;
                        default:
                            break;
                    }
                    SwingUtilities.invokeLater(() -> follow = isAtBottom(scroll));
                }
            });
        }

        // Fuerza el seguimiento y salta al fondo (al (re)mostrar el log o al
        // reactivar la preferencia de autoscroll: debe ponerse al día).
        void snapToBottom() {
            follow = true;
            scrollToBottomLater();
        }

        // Salta al fondo solo si seguimos al usuario.
        void followIfNeeded() {
            if (follow) {
                scrollToBottomLater();
            }
        }

        // Diferido para que el salto ocurra DESPUÉS de maquetar el contenido recién
        // añadido (un append con estilo puede embeber cartas cuya altura no se
        // conoce hasta el revalidate, publicado como evento posterior del EDT); un
        // scroll síncrono se clamparía al panel aún pequeño y se quedaría corto.
        private void scrollToBottomLater() {
            SwingUtilities.invokeLater(() -> {
                try {
                    view.setCaretPosition(view.getDocument().getLength());
                } catch (Throwable t) {
                    // La vista puede estar entre dispose/re-show — ignorar.
                }
            });
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBoxMenuItem auto_scroll_menu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JMenu opciones_menu;
    private javax.swing.JTextArea textarea;
    // End of variables declaration//GEN-END:variables
}
