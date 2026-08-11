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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * In-game log console: a translucent, borderless HUD showing {@link #LOG_TEXT} (the
 * hand-by-hand plain-text log) with syntax highlighting — cards, amounts, balance
 * tables and result lines — rendered into a styled {@link JTextPane} that replaces the
 * generated plain {@code JTextArea}. One instance is reused for the session; hiding it
 * just calls {@code setVisible(false)}, it's never disposed.
 *
 * @author tonikelope
 */
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public final class GameLogDialog extends JDialog {

    public final static String TITLE = "log.registro_de_la_timba";
    private static volatile String LOG_TEXT = "[CoronaPoker " + AboutDialog.VERSION + " " + Translator.translate("log.registro_de_la_timba_2") + "\n\n";
    private volatile boolean auto_scroll = true;
    private volatile boolean fin_transmision = false;
    // Tracks whether the default size/position (set by the caller, e.g. GameFrame's
    // 1280x720 centered default) has already been applied once. The dialog is only
    // hidden on close, never disposed, so later reopens keep whatever the user resized
    // or moved it to instead of resetting every time.
    private volatile boolean default_bounds_applied = false;
    private final Object log_lock = new Object();
    private BottomFollower main_follow;

    // Rich rendering: the generated `textarea` (JTextArea) cannot show mixed
    // styles, so the scrollpane's viewport is swapped at runtime to this styled
    // JTextPane. LOG_TEXT stays the plain-text source of truth (so the loser-card
    // regex surgery and the plain-text log-file export are untouched); the styling
    // is DERIVED from the text on every render — language
    // agnostic (card suit glyphs ♠♥♦♣, parenthesised amounts) plus a few
    // hard-coded structural markers (hand header, FLOP/TURN/RIVER, placeholders).
    private JTextPane log_pane;
    private JCheckBoxMenuItem transparent_menu;

    // Custom title bar's maximize/restore control (the window is undecorated, see
    // setupTitleBar). Its icon is repainted from the "maximized" state, which is derived
    // from whether the current bounds cover the monitor's work area. normal_bounds
    // remembers the pre-maximize size/position so it can be restored.
    private javax.swing.JLabel max_btn;
    private java.awt.Rectangle normal_bounds;

    // Console look (PowerShell-ish): near-black background + a monospaced font,
    // shared with the Debug console in Settings (DebugSettingsPanel) — package-visible.
    static final Color LOG_BG = new Color(12, 12, 12);
    static final Font LOG_FONT = new Font("Consolas", Font.PLAIN, 20);

    private static SimpleAttributeSet logStyle(Color c, boolean bold, boolean italic) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        return s;
    }

    // Background-color variant (a highlighted band): used for the most severe errors (light text on red).
    private static SimpleAttributeSet logStyle(Color fg, Color bg, boolean bold, boolean italic) {
        SimpleAttributeSet s = logStyle(fg, bold, italic);
        StyleConstants.setBackground(s, bg);
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
    // Most severe errors (a canceled hand / security & integrity violations): WHITE text
    // on a RED background band, the log's strongest highlight.
    private static final SimpleAttributeSet ST_CRITICAL = logStyle(new Color(255, 255, 255), new Color(170, 20, 20), true, false);

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
    //   "(ST)" straddle  "(DS)" dealer+straddle (3-handed: the dealer acts as UTG)
    //   "($$)" the account-auditor totals line (money icon)
    // Data rows are monospace-padded columns: nick / stack / buyin.
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
    // The live HUD document is capped to the last MAX_LIVE_LOG_CHARS so it can't grow without
    // bound over a long game (the plain-text LOG_TEXT source of truth stays complete, so the saved
    // registro — rendered separately via buildLogPane — keeps every hand).
    private static final int MAX_LIVE_LOG_CHARS = 500_000;

    private void renderAll(String fullText) {
        StyledDocument doc = log_pane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ex) {
        }
        appendStyled(doc, tailFromLineBoundary(fullText, MAX_LIVE_LOG_CHARS));
    }

    // Returns the last ~max chars of text, snapped forward to the next line boundary so a chunk is
    // never cut mid-line (which would split a balance row / card token). Unchanged when within cap.
    private static String tailFromLineBoundary(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        int cut = text.length() - max;
        int nl = text.indexOf('\n', cut);
        return (nl >= 0) ? text.substring(nl + 1) : text.substring(cut);
    }

    // Trims the head of the LIVE HUD document to keep it within the cap (frees the retained styled
    // card components in the removed range). EDT-only (Swing document mutation).
    private void trimLiveLogDocument() {
        StyledDocument doc = log_pane.getStyledDocument();
        int len = doc.getLength();
        if (len <= MAX_LIVE_LOG_CHARS) {
            return;
        }
        try {
            int cut = len - MAX_LIVE_LOG_CHARS;
            String window = doc.getText(cut, Math.min(4000, len - cut));
            int nl = window.indexOf('\n');
            if (nl >= 0) {
                cut += nl + 1;
            }
            doc.remove(0, cut);
        } catch (BadLocationException ex) {
        }
    }

    // Builds a read-only styled pane that renders a plain-text game log with the SAME
    // console look as the in-game log HUD: Consolas monospace, dark background, syntax
    // highlighting and box-drawing balance tables with role icons / card chips. Used by
    // the Stats dialog's "REGISTRO DE LA TIMBA" view so a saved .log reads exactly as it
    // did live — the previous HTML rendering used a proportional font and collapsed the
    // monospace column padding, so the tables came out misaligned. Renders whatever text
    // it's given (independent of the live LOG_TEXT).
    public static JTextPane buildLogPane(String text) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(LOG_BG);
        pane.setForeground(new Color(230, 230, 230));
        pane.setFont(LOG_FONT.deriveFont(LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM));
        appendStyled(pane.getStyledDocument(), text);
        return pane;
    }

    // Appends a chunk (one or more lines) with per-line + per-token styling into the
    // given document. Static (target document passed in) so it can render either the live
    // HUD's pane or a detached pane (see buildLogPane).
    private static void appendStyled(StyledDocument doc, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int i = 0, n = text.length();
        while (i < n) {
            int nl = text.indexOf('\n', i);
            String line = (nl < 0) ? text.substring(i) : text.substring(i, nl + 1);
            i = (nl < 0) ? n : nl + 1;
            appendStyledLine(doc, CARD_PAREN.matcher(line).replaceAll("$1"));
        }
    }

    private static void appendStyledLine(StyledDocument doc, String line) {
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

    private static void appendNormalLine(StyledDocument doc, String line) {
        appendNormalLine(doc, line, null);
    }

    // forcedBase, when non-null, overrides the per-line base style instead of deriving it
    // from lineBaseStyle(line); card/amount/placeholder overlays still apply on top. No
    // caller currently passes a non-null value (the base color always comes from
    // lineBaseStyle); kept as a hook for a future forced-style caller.
    private static void appendNormalLine(StyledDocument doc, String line, SimpleAttributeSet forcedBase) {
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
        if (base != ST_HEADER && base != ST_BOARD && base != ST_CRITICAL) {
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
    //   "(##)" -> grid borders / header / separators, all dimmed.
    //   "(MV)" -> MULTIVERSO row: dimmed grid + dimmed content + cards rendered as
    //             chips to the right of the frame.
    //   other  -> balance/totals/auditor-warning row: dimmed grid + normal-color
    //             content (amounts in amber).
    private static void appendBalanceRow(StyledDocument doc, String line) throws BadLocationException {
        String token = line.substring(0, 4);
        String rest = line.substring(4);
        SimpleAttributeSet ca = new SimpleAttributeSet();
        StyleConstants.setComponent(ca, makeRoleMarker(token));
        doc.insertString(doc.getLength(), " ", ca);
        if (token.equals("(##)")) {
            doc.insertString(doc.getLength(), rest, ST_DIM);
            return;
        }
        appendGridLine(doc, rest, token.equals("(MV)") ? ST_DIM : balanceContentStyle(rest));
    }

    // Colors the final results table's row (NICK / RESULT) by its last cell: ST_WIN
    // (green) on a win, ST_LOSS (red) on a loss, ST_DEFAULT (white) otherwise. Only the
    // RESULT cell (last, between the final two '│') is inspected, so a nick containing
    // one of these words can't mis-tint the row (Spanish "NI GANA NI PIERDE" contains
    // both "gana" and "pierde"); matches both the active language and English, like
    // categoryRules, to work in any language. Other marker tables (per-hand
    // NICK/STACK/BUYIN, auditor totals) don't carry these phrases in their last cell, so
    // they stay ST_DEFAULT as before.
    private static SimpleAttributeSet balanceContentStyle(String rest) {
        int last = rest.lastIndexOf('│');
        if (last <= 0) {
            return ST_DEFAULT;
        }
        int prev = rest.lastIndexOf('│', last - 1);
        if (prev < 0) {
            return ST_DEFAULT;
        }
        String cell = rest.substring(prev + 1, last).strip();
        if (cell.isEmpty()) {
            return ST_DEFAULT;
        }
        if (cell.equals(Translator.translate("ui.ni_gana_ni_pierde"))
                || cell.equals(Translator.translate("ui.ni_gana_ni_pierde", true))) {
            return ST_DEFAULT;
        }
        if (cell.startsWith(Translator.translate("ui.gana_4"))
                || cell.startsWith(Translator.translate("ui.gana_4", true))) {
            return ST_WIN;
        }
        if (cell.startsWith(Translator.translate("ui.pierde_2"))
                || cell.startsWith(Translator.translate("ui.pierde_2", true))) {
            return ST_LOSS;
        }
        return ST_DEFAULT;
    }

    // Renders a table line where the box-drawing characters (the grid: ─│┌┐└┘├┤┬┴┼,
    // Unicode range U+2500..U+257F) are dimmed and the rest uses `contentStyle`.
    // Parenthesized amounts are painted amber (money / leftover pot) and [A♠] card
    // tokens are inserted as chips, so a bordered table still shows card chips and
    // highlights the amount.
    private static void appendGridLine(StyledDocument doc, String line, SimpleAttributeSet contentStyle) {
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

    // Role icons are fit into a ROLE_ICON_PX square WITHOUT distortion: the dealer and
    // blind icons are square source images (350x350), but the account-auditor chips icon
    // is landscape (287x211) — forcing it into a square would squeeze it to three quarters
    // of its width.
    private static javax.swing.ImageIcon scaledRoleIcon(String resource) {
        javax.swing.ImageIcon raw = new javax.swing.ImageIcon(GameLogDialog.class.getResource(resource));

        int width = ROLE_ICON_PX;
        int height = ROLE_ICON_PX;

        if (raw.getIconWidth() > 0 && raw.getIconHeight() > 0) {
            float scale = Math.min((float) ROLE_ICON_PX / raw.getIconWidth(), (float) ROLE_ICON_PX / raw.getIconHeight());
            width = Math.max(1, Math.round(raw.getIconWidth() * scale));
            height = Math.max(1, Math.round(raw.getIconHeight() * scale));
        }

        return new javax.swing.ImageIcon(raw.getImage().getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH));
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
                + "<span style=\"font-size:" + Math.round(28 * Helpers.DIALOG_ZOOM) + "pt\">" + suit + "</span></span></html>") {
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
        card.setFont(new java.awt.Font("Consolas", java.awt.Font.BOLD, Math.round(22 * Helpers.DIALOG_ZOOM)));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(Math.round(1 * Helpers.DIALOG_ZOOM), Math.round(7 * Helpers.DIALOG_ZOOM), Math.round(1 * Helpers.DIALOG_ZOOM), Math.round(7 * Helpers.DIALOG_ZOOM)));
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
        // Titles framed with box-drawing characters (instead of a row of asterisks):
        // a SINGLE frame (┌─┐/│/└) -> header style (cyan); a DOUBLE frame (╔═╗/║/╚) ->
        // alert style (red, e.g. server shutdown). Bordered tables (balance/MULTIVERSO)
        // never reach here — they carry a marker token and go through appendBalanceRow —
        // so this leading-box-character check can't collide with them.
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
    // the app uses, so detection works in ANY language without touching every print()
    // call site. Priority = list order (first match wins). Rules are registered in the
    // ACTIVE language (plus forced English); if the user switches language mid-session
    // (e.g. plays one session in English, another in Spanish) they must be REBUILT, or
    // the new language's phrases wouldn't match and the line would render uncolored
    // (white). Hence the cache is keyed on the language it was built with and rebuilt as
    // soon as GameFrame.LANGUAGE changes.
    private static java.util.List<Object[]> categoryRules() {
        java.util.List<Object[]> rules = CATEGORY_RULES;
        if (rules == null || !java.util.Objects.equals(CATEGORY_RULES_LANG, GameFrame.LANGUAGE)) {
            rules = new java.util.ArrayList<>();
            // Most severe errors -> red-background band with white text (ST_CRITICAL): a
            // canceled hand (misdeal, the worst hand-level error; matched by its "HAND
            // CANCELED" header) and security/integrity violations — zero_trust (including
            // the security misdeal reasons, the same ones that trigger the siren and
            // abortAndExit), the hand's cryptographic verification and an invalid action
            // signature. Other aborts (peer/protocol, straddle, RIT) are routine and are
            // NOT highlighted.
            for (String k : new String[]{"game.mano_anulada",
                "zero_trust.security_alert", "zero_trust.suspicious_alert", "zero_trust.peer_alert", "zero_trust.lockdown_activated",
                "zero_trust.cascade_refused", "zero_trust.card_resolve_failed", "zero_trust.pocket_unlock_refused", "zero_trust.community_unlock_refused",
                "game.mano_verificacion_divergente", "game.mano_verificacion_jugador_ausente", "game.mano_verificacion_firma_invalida",
                "game.mano_verificacion_host_sin_prueba", "game.firma_accion_invalida"}) {
                addCategoryRule(rules, k, ST_CRITICAL);
            }
            // Consensus + the two shuffle lines (green "verified", yellow "not verified yet").
            // These carry the {0} ordinal, but addCategoryRule already matches on the fixed prefix.
            addCategoryRule(rules, "game.mano_verificada_consenso", ST_WIN);
            addCategoryRule(rules, "game.barajado_verificado", ST_WIN);
            addCategoryRule(rules, "game.barajado_pendiente", ST_BLIND);
            // Recover: actions replayed by the host that happened while the player was away
            // (after their last recorded action). A mild caution notice -> RED TEXT (ST_ALERT),
            // the level reserved for soft warnings; white-on-red (ST_CRITICAL) is reserved for
            // severe errors. Nobody is accused and the hand continues.
            addCategoryRule(rules, "game.recover_accion_ausencia", ST_ALERT);
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
        // Registers the marker in the ACTIVE language and also in forced ENGLISH (some log
        // lines can be printed in a language other than the one the rules were built with).
        // The marker is the phrase up to the first "{": keys with {0} (e.g. the hand ordinal)
        // then match on their fixed PREFIX, since the real line carries the formatted value,
        // not the placeholder (without this, any category message with {0} rendered uncolored).
        addCategoryPhrase(rules, Translator.translate(key), key, style);
        addCategoryPhrase(rules, Translator.translate(key, true), key, style);
    }

    private static void addCategoryPhrase(java.util.List<Object[]> rules, String phrase, String key, SimpleAttributeSet style) {
        if (phrase == null || phrase.equals(key)) {
            return; // key not found (translate() returns the key itself)
        }
        // Only the FIRST line is used as the marker: multi-line keys (e.g. "HAND
        // CANCELED\n\nREASON:") are printed split on \n, so the marker must be that first
        // line to match.
        int nl = phrase.indexOf('\n');
        if (nl >= 0) {
            phrase = phrase.substring(0, nl);
        }
        int brace = phrase.indexOf('{');
        if (brace >= 0) {
            phrase = phrase.substring(0, brace);
        }
        phrase = phrase.trim();
        // The marker is detected with line.contains(phrase) against the ALREADY RENDERED
        // text. Some pot-related keys start with punctuation that the render pipeline
        // TRANSFORMS, so it doesn't survive a log re-render:
        //   ") WINS POT ... ("       -> the ")" is the closing paren of the WINNER's hole
        //                              cards, which CARD_PAREN strips when painting the
        //                              cards as chips (so the winner would never render
        //                              green).
        //   "(---) LOSES POT ... (" -> the "(---)" is the LOSER's placeholder, which
        //                              actualizarCartasPerdedores replaces with the
        //                              revealed showdown cards (so the loser rendered red
        //                              the first time but WHITE on re-render).
        // That unstable prefix is dropped, keeping the stable core ("WINS POT ... (",
        // "LOSES POT ... (") present identically on the first print and on re-render. The
        // trailing "(" is kept: it's the amount's opening paren (which DOES survive) and
        // gives the marker specificity.
        phrase = phrase.replaceFirst("^\\((?:---|\\*\\*\\*)\\)\\s*", "").replaceFirst("^\\)\\s*", "").trim();
        if (phrase.length() >= 3) {
            for (Object[] r : rules) {
                if (phrase.equals(r[0])) {
                    return; // already registered (same phrase in both languages)
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
    // lost native frame: menu-bar drag, four corner resize grips and a Close item.
    private void setupLogPane() {
        // JTextComponent's default unit increment is visibleRect.height / 10, so each
        // wheel notch moves ~3 x (1/10 of the viewport): several lines, more the bigger
        // the window. Pin it to one line's height instead so the wheel scrolls the
        // system's configured line count (3 on Windows) regardless of panel size, like a
        // normal text editor.
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
        log_pane.setFont(LOG_FONT.deriveFont(LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM));
        jScrollPane1.setViewportView(log_pane);

        dispose();
        setUndecorated(true);
        // No border at all — the console sits flush. Resize is via corner grips (below).
        getRootPane().setBorder(null);
        getRootPane().setOpaque(true);
        getRootPane().setBackground(LOG_BG);

        // Transparency = 95% opacity (works because the window is undecorated). ON by default.
        transparent_menu = new JCheckBoxMenuItem();
        transparent_menu.setFont(new Font("Dialog", Font.PLAIN, 14));
        transparent_menu.setText("Transparente");
        transparent_menu.putClientProperty("i18n.key", "ui.registro_transparente");
        transparent_menu.setSelected(true);
        transparent_menu.addActionListener(evt -> applyLogOpacity(transparent_menu.isSelected()));
        opciones_menu.add(transparent_menu);

        // Move the HUD by dragging the menu (and its filler strip) or the title bar
        // (see setupTitleBar). Based on screen coordinates so it works no matter which
        // subcomponent fires the event. The close (X) button now lives in the title bar.
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

    // Adapter for moving the (borderless) window by dragging a bar-like zone. Based on
    // screen coordinates so it works no matter which subcomponent fires the event. Each
    // call creates its own state (only one drag happens at a time, so sharing it across
    // components of the same bar would also work).
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
                if (off[0] == null) {
                    return;
                }
                java.awt.Point sp = e.getLocationOnScreen();
                // Mimic Windows: dragging a maximized window restores it to its normal
                // size under the cursor (keeping the grab point's proportional position
                // on the bar), then it moves like a normal window from there on. The
                // offset is rescaled to the new size so the mouse stays pinned to the bar.
                if (normal_bounds != null && isMaximizedToScreen()) {
                    double frac_x = getWidth() > 0 ? (double) off[0].x / getWidth() : 0.5;
                    int new_w = normal_bounds.width;
                    int new_h = normal_bounds.height;
                    int new_off_x = (int) Math.round(frac_x * new_w);
                    int new_off_y = Math.min(off[0].y, Math.max(0, new_h - 1));
                    off[0] = new java.awt.Point(new_off_x, new_off_y);
                    setBounds(sp.x - new_off_x, sp.y - new_off_y, new_w, new_h);
                    return;
                }
                setLocation(sp.x - off[0].x, sp.y - off[0].y);
            }
        };
    }

    // Custom title bar. The window is undecorated (so it can be semi-transparent), so
    // there's no native bar: we build our own with the name and close button. It goes
    // ABOVE the menu — since the native JMenuBar owns the root pane's top slot, it's
    // pulled out of there (setJMenuBar(null)) and stacked below the title bar in the
    // content pane's NORTH (wrapLogInBorderLayout already set that up as a BorderLayout
    // with the log in CENTER).
    private void setupTitleBar() {
        setJMenuBar(null);

        javax.swing.JLabel title = new javax.swing.JLabel("CoronaPoker - " + Translator.translate("log.registro"));
        title.setForeground(new Color(70, 70, 70));
        title.setFont(new Font("Dialog", Font.BOLD, Math.round(14 * Helpers.DIALOG_ZOOM)));
        title.setBorder(javax.swing.BorderFactory.createEmptyBorder(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM), Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(12 * Helpers.DIALOG_ZOOM)));

        final Color CTRL_FG = new Color(70, 70, 70);       // light bar -> dark controls
        final Color CLOSE_HOVER = new Color(215, 40, 40);  // close = red
        final Color MAX_HOVER = new Color(20, 20, 20);     // maximize/restore = darken
        final Color bar_bg = jMenuBar1.getBackground();

        // MAXIMIZE / RESTORE button. The window is undecorated (so it can be
        // semi-transparent), so there's no native button: the icon is painted with
        // Java2D instead of relying on a font glyph (not every font has one). One hollow
        // square = maximize; two overlapping squares (Windows-style) = restore. The
        // state is derived from the real geometry in refreshMaxRestoreState, so resizing
        // from a corner or dragging also keeps the icon correct.
        final int ICON = Math.max(Math.round(15 * Helpers.DIALOG_ZOOM), 13);
        final int STROKE = Math.max(Math.round(2 * Helpers.DIALOG_ZOOM), 2);
        max_btn = new javax.swing.JLabel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new java.awt.BasicStroke(STROKE));
                g2.setColor(getForeground());
                int x = (getWidth() - ICON) / 2;
                int y = (getHeight() - ICON) / 2;
                if (Boolean.TRUE.equals(getClientProperty("maximized"))) {
                    int off = Math.max(Math.round(ICON * 0.32f), 3);
                    int sq = ICON - off;
                    g2.drawRect(x + off, y, sq, sq);            // back square (top-right)
                    g2.setColor(bar_bg);
                    g2.fillRect(x, y + off, sq + 1, sq + 1);    // clears the overlap with the bar color
                    g2.setColor(getForeground());
                    g2.drawRect(x, y + off, sq, sq);            // front square (bottom-left)
                } else {
                    g2.drawRect(x, y, ICON, ICON);              // a single square = maximize
                }
                g2.dispose();
            }
        };
        max_btn.setOpaque(false);
        max_btn.setForeground(CTRL_FG);
        max_btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        final int max_hpad = Math.max(Math.round(11 * Helpers.DIALOG_ZOOM), 9);
        final int max_vpad = Math.max(Math.round(6 * Helpers.DIALOG_ZOOM), 5);
        max_btn.setPreferredSize(new java.awt.Dimension(ICON + max_hpad * 2, ICON + max_vpad * 2));
        max_btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                toggleMaximize();
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                max_btn.setForeground(MAX_HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                max_btn.setForeground(CTRL_FG);
            }
        });

        final javax.swing.JLabel close_btn = new javax.swing.JLabel("X");
        // The X is a CLICKABLE control: it scales with zoom but has a FLOOR (font and padding) so
        // that at low zoom (50%) it doesn't shrink to nothing and stays easy to click. At 100% the
        // max() calls just return the original design values (22 / 12 / 14) unchanged.
        close_btn.setFont(new Font("Dialog", Font.BOLD, Math.max(Math.round(22 * Helpers.DIALOG_ZOOM), 18)));
        close_btn.setForeground(CTRL_FG); // light bar -> dark X
        close_btn.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, Math.max(Math.round(12 * Helpers.DIALOG_ZOOM), 10), 0, Math.max(Math.round(14 * Helpers.DIALOG_ZOOM), 12)));
        close_btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        close_btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                setVisible(false);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                close_btn.setForeground(CLOSE_HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                close_btn.setForeground(CTRL_FG);
            }
        });

        // The two controls, flush right, in order [maximize/restore][X]. FlowLayout
        // (hgap/vgap 0) vertically centers them even though they have different heights;
        // the X's side padding already gives breathing room from the right edge.
        javax.swing.JPanel controls = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);
        controls.add(max_btn);
        controls.add(close_btn);

        javax.swing.JPanel title_bar = new javax.swing.JPanel(new BorderLayout());
        title_bar.setBackground(bar_bg);
        title_bar.add(title, BorderLayout.WEST);
        title_bar.add(controls, BorderLayout.EAST);

        java.awt.event.MouseAdapter drag = windowDragAdapter();
        title_bar.addMouseListener(drag);
        title_bar.addMouseMotionListener(drag);
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);

        javax.swing.JPanel north = new javax.swing.JPanel(new BorderLayout());
        north.add(title_bar, BorderLayout.NORTH);
        north.add(jMenuBar1, BorderLayout.CENTER);

        getContentPane().add(north, BorderLayout.NORTH);

        // Keep the maximize/restore icon in sync with the real geometry: anything that
        // changes the bounds (the button itself, the corner grips, dragging the bar —
        // which only MOVES the window) refreshes the state. The work area can differ
        // between monitors, so it's derived on every event instead of trusting a flag.
        java.awt.event.ComponentAdapter geometry_watch = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                refreshMaxRestoreState();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                refreshMaxRestoreState();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                refreshMaxRestoreState();
            }
        };
        addComponentListener(geometry_watch);

        refreshMaxRestoreState();
    }

    // Work area of the monitor the window is on (monitor screen minus taskbar). Null
    // if the window doesn't have a GraphicsConfiguration yet.
    private java.awt.Rectangle currentScreenWorkArea() {
        java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            return null;
        }
        java.awt.Rectangle sb = gc.getBounds();
        java.awt.Insets ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new java.awt.Rectangle(sb.x + ins.left, sb.y + ins.top,
                sb.width - ins.left - ins.right, sb.height - ins.top - ins.bottom);
    }

    // True if the window exactly covers the monitor's work area (our definition of
    // "maximized", since being undecorated there's no native state to query).
    private boolean isMaximizedToScreen() {
        java.awt.Rectangle work = currentScreenWorkArea();
        return work != null && work.equals(getBounds());
    }

    // Maximizes (fills the current monitor's work area) or restores the previous
    // bounds. The window is undecorated, so maximize/restore is done by hand
    // (setExtendedState only applies to decorated Frames). setBounds triggers
    // componentResized -> refreshMaxRestoreState, which repositions the grips and
    // updates the icon.
    private void toggleMaximize() {
        if (isMaximizedToScreen()) {
            if (normal_bounds != null) {
                setBounds(normal_bounds);
            }
        } else {
            java.awt.Rectangle work = currentScreenWorkArea();
            if (work == null) {
                return;
            }
            normal_bounds = getBounds();
            setBounds(work);
        }
    }

    // Derives the maximized state from the real geometry and updates the icon, so it
    // stays correct after a corner resize or a drag too, not just after clicking the
    // button.
    private void refreshMaxRestoreState() {
        if (max_btn == null) {
            return;
        }
        boolean maxed = false;
        try {
            maxed = isMaximizedToScreen();
        } catch (Exception ex) {
        }
        max_btn.putClientProperty("maximized", maxed);
        max_btn.repaint();
    }

    private void applyLogOpacity(boolean transparent) {
        try {
            setOpacity(transparent ? 0.95f : 1.0f);
        } catch (Exception | Error ex) {
            // Window translucency not supported on this platform — ignore.
        }
    }

    // Pixel margin for "close enough to the bottom": if the viewport is within
    // AT_BOTTOM_TOLERANCE_PX of the bottom, treat it as "at bottom" for smart
    // autoscroll (a user 1-2 lines from the end hasn't "scrolled up to read", they're
    // waiting for the next message).
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
     * Creates the game log dialog (initially hidden; the caller shows/positions it).
     *
     * @param parent owning frame
     * @param modal whether the dialog blocks input to its owner
     */
    public GameLogDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        setupLogPane();

        main_follow = new BottomFollower(jScrollPane1, log_pane);

        Helpers.setTranslatedTitle(this, TITLE);

        Helpers.JTextFieldRegularPopupMenu.addTo(log_pane);

        Helpers.updateFonts(jMenuBar1, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);
        Helpers.scaleIcons(jMenuBar1, Helpers.DIALOG_ZOOM);

        Helpers.translateComponents(this, false);

        renderAll(GameLogDialog.LOG_TEXT);

        wrapLogInBorderLayout();

        setupTitleBar();

        // Every time the dialog becomes visible (first open, or reopened as a brand new
        // instance after GameFrame disposed the old one on a parent-window change) jump
        // to the end and resume following — opening the log means the user wants to see
        // the latest lines. snapToBottom() already defers the scroll (invokeLater) to
        // happen AFTER the viewport's layout.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent evt) {
                if (main_follow != null) {
                    main_follow.snapToBottom();
                }
            }
        });

        pack();

        // Compact default size (resizable from any corner). Transparency ON (95%).
        setSize(Math.round(720 * Helpers.DIALOG_ZOOM), Math.round(430 * Helpers.DIALOG_ZOOM));
        applyLogOpacity(transparent_menu.isSelected());

    }

    // The window is undecorated with its own title bar: setupTitleBar stacks title + menu
    // in the content pane's NORTH, which requires a BorderLayout with the log in CENTER.
    // Debug output no longer lives here (moved to the Debug tab in Settings), so the
    // content pane goes back to showing just the log.
    private void wrapLogInBorderLayout() {
        getContentPane().remove(jScrollPane1);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(jScrollPane1, BorderLayout.CENTER);
    }

    public JTextComponent getTextArea() {
        return log_pane;
    }

    public String getText() {
        return LOG_TEXT;
    }

    /**
     * Reveals showdown cards for players who mucked (or were auto-shown) by rewriting
     * their "(---)" hole-card placeholder(s) already printed in {@link #LOG_TEXT}, then
     * re-renders the log. A no-op if nothing in the text actually changes.
     *
     * @param perdedores losers (or shown hands) for the just-finished hand, keyed by player
     */
    public void actualizarCartasPerdedores(ConcurrentHashMap<Player, Hand> perdedores) {

        synchronized (log_lock) {

            if (perdedores != null && !perdedores.isEmpty()) {

                // Text BEFORE the substitutions: re-rendering the whole log gets more
                // expensive the longer it is, and this ran at the end of EVERY hand even
                // when there was nothing to replace (nobody showed cards, or they were
                // already revealed). If the text doesn't change, re-rendering would be a
                // no-op anyway.
                final String log_antes = GameLogDialog.LOG_TEXT;

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

                if (log_antes.equals(GameLogDialog.LOG_TEXT)) {
                    return;
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

    /**
     * Translates and appends a log line, unless the dialog's transmission has ended.
     *
     * @param msg a translator key or literal line to append
     */
    public void print(String msg) {

        if (!this.fin_transmision) {

            // logRun (not threadRun): a single FIFO consumer thread, so log lines are
            // applied in the order print() was called and never get reordered relative
            // to each other (the general multi-threaded pool would allow that).
            Helpers.logRun(() -> {
                synchronized (log_lock) {
                    String message = Translator.translate(msg);
                    GameLogDialog.LOG_TEXT += message + "\n\n";
                    Helpers.GUIRun(() -> {
                        appendStyled(log_pane.getStyledDocument(), message + "\n\n");
                        trimLiveLogDocument();

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
        // Re-enabling autoscroll catches up by jumping to the most recent line.
        if (this.auto_scroll) {
            if (main_follow != null) {
                main_follow.snapToBottom();
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

    // "Smart" autoscroll: follows the bottom while the user is parked there and stops
    // as soon as they scroll up to read — no fragile before/after geometry sampling.
    //
    // Why: the old code sampled isAtBottom() right before each append and scrolled
    // SYNCHRONOUSLY. A styled append can embed cards or role icons as components whose
    // height isn't known until the panel re-lays out (a revalidate published as a LATER
    // EDT event), so the synchronous scroll clamped to the still-small panel and fell
    // SHORT of the real bottom. The next message then sampled "not at bottom" and kept
    // the stale caret, drifting further from the bottom each message until the user
    // dragged back down manually.
    //
    // This instead models user intent as sticky state: `follow` starts true and only
    // changes on a real gesture (wheel, mouse on the scrollbar, or keyboard) — scrolling
    // up stops following, returning to the bottom resumes it. Programmatic scrolls never
    // touch it, so a transient layout delay can't turn following off. The jump to bottom
    // runs via invokeLater so it happens AFTER the append's revalidate, landing on the
    // real bottom.
    // Package-visible: reused by the Debug console in Settings (DebugSettingsPanel).
    static final class BottomFollower {

        private final JScrollPane scroll;
        private final JTextComponent view;
        private volatile boolean follow = true;

        BottomFollower(JScrollPane scroll, JTextComponent view) {
            this.scroll = scroll;
            this.view = view;

            // NEVER_UPDATE: a non-editable log shouldn't auto-scroll just because the
            // document changed (the default caret policy drags the view to the caret on
            // every append, pulling down anyone who's reading). All scrolling here is
            // driven explicitly instead.
            if (view.getCaret() instanceof javax.swing.text.DefaultCaret) {
                ((javax.swing.text.DefaultCaret) view.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
            }

            // Re-evaluate "is the user parked at the bottom?" after any mouse gesture on
            // the scrollbar (thumb drag, track click, arrows), the wheel, or the keyboard
            // (below). Programmatic scrolls never go through mouse/wheel/keyboard, so
            // they never flip the flag: a layout delay can't turn following off.
            java.awt.event.MouseAdapter reeval = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    follow = false; // grabbed the scrollbar: don't fight the user's drag
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    SwingUtilities.invokeLater(() -> follow = isAtBottom(scroll));
                }
            };
            scroll.getVerticalScrollBar().addMouseListener(reeval);

            // NOTE: the listener goes on the JScrollPane, NOT the view. A MouseWheelEvent
            // only "bubbles up" to the JScrollPane (which does the scrolling) if the
            // component under the cursor has NO MouseWheelListener; putting it on the
            // view would swallow the event and the wheel would stop scrolling. On the
            // JScrollPane it coexists fine with the default scroll listener.
            scroll.addMouseWheelListener((java.awt.event.MouseWheelEvent e) -> {
                if (e.getWheelRotation() < 0) {
                    follow = false; // scrolling up = the user wants to read; stop following now
                }
                SwingUtilities.invokeLater(() -> follow = isAtBottom(scroll));
            });

            // Keyboard navigation on the focused panel (arrows, Page Up/Down, Home/End):
            // the KeyListener runs BEFORE the caret action moves/scrolls, so the
            // "upward" keys stop following on the spot, and invokeLater re-evaluates the
            // position after the scroll (so going back down to the bottom resumes it).
            view.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case java.awt.event.KeyEvent.VK_UP:
                        case java.awt.event.KeyEvent.VK_KP_UP:
                        case java.awt.event.KeyEvent.VK_PAGE_UP:
                        case java.awt.event.KeyEvent.VK_HOME:
                            follow = false; // navigating upward = reading; stop following now
                            break;
                        default:
                            break;
                    }
                    SwingUtilities.invokeLater(() -> follow = isAtBottom(scroll));
                }
            });
        }

        // Forces following and jumps to the bottom (when (re)showing the log, or when
        // re-enabling the autoscroll preference: it must catch up).
        void snapToBottom() {
            follow = true;
            // Deferred double pass: (re)opening the log makes the JTextPane re-lay out
            // all its accumulated content plus the embedded components (cards / role
            // icons), whose real height only arrives in a LATER EDT revalidate. A single
            // jump clamps to the still-small panel and leaves the view at the TOP (hence
            // the intermittent scroll-appears-at-top-on-open bug). The 1st pass pushes
            // after the initial layout; the 2nd reaffirms the bottom once getMaximum() is
            // up to date.
            SwingUtilities.invokeLater(() -> {
                forceBottom();
                SwingUtilities.invokeLater(this::forceBottom);
            });
        }

        // Jumps to the bottom only while still following the user.
        void followIfNeeded() {
            if (follow) {
                scrollToBottomLater();
            }
        }

        // Deferred so the jump happens AFTER the newly appended content is laid out (a
        // styled append can embed cards whose height isn't known until the revalidate,
        // published as a later EDT event); a synchronous scroll would clamp to the
        // still-small panel and fall short.
        private void scrollToBottomLater() {
            SwingUtilities.invokeLater(() -> {
                try {
                    view.setCaretPosition(view.getDocument().getLength());
                } catch (Throwable t) {
                    // The view may be between dispose/re-show — ignore.
                }
            });
        }

        // Pushes the view to the bottom two ways: setCaretPosition (drags the viewport)
        // and setValue(getMaximum()) on the vertical scrollbar (reaches the bottom even
        // when the caret would fall short due to geometry not yet updated after a
        // relayout). Used by the (re)open jump.
        private void forceBottom() {
            try {
                view.setCaretPosition(view.getDocument().getLength());
            } catch (Throwable t) {
                // The view may be between dispose/re-show — ignore.
            }
            try {
                javax.swing.JScrollBar vbar = scroll.getVerticalScrollBar();
                if (vbar != null) {
                    vbar.setValue(vbar.getMaximum());
                }
            } catch (Throwable t) {
                // Same: scrollbar mid-transition — ignore.
            }
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
