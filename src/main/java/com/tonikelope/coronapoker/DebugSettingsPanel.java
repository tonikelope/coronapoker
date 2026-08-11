/*
 * Copyright (C) 2026 tonikelope
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
import java.awt.Dimension;
import java.awt.Font;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * "Debug" tab of the Settings dialog: a read-only, syntax-highlighted console showing the
 * {@code java.util.logging} dump buffered by {@link DebugLog}.
 *
 * Each record is coloured by log level (INFO green, WARNING amber, SEVERE red band, FINE dim),
 * with the timestamp/package dimmed, the source class in cyan and numeric values highlighted, so
 * the important lines stand out at a glance. Relies on {@link DebugLog}'s locale-independent
 * format (English level names, ISO timestamps) to classify each line reliably.
 *
 * Subscribes to {@link DebugLog} on construction and unsubscribes in {@link #cleanup()} (called by
 * SettingsDialog on close, so the static DebugLog listener doesn't keep this discarded panel
 * alive). Reuses the console look ({@code LOG_BG}/{@code LOG_FONT}) and the sticky autoscroll
 * ({@code BottomFollower}) from {@link GameLogDialog}.
 *
 * @author tonikelope
 */
public class DebugSettingsPanel extends JPanel {

    private static SimpleAttributeSet style(Color fg, boolean bold, boolean italic) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, fg);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        return s;
    }

    // Background-band variant (light text on a coloured band): used for the SEVERE level tag.
    private static SimpleAttributeSet style(Color fg, Color bg, boolean bold, boolean italic) {
        SimpleAttributeSet s = style(fg, bold, italic);
        StyleConstants.setBackground(s, bg);
        return s;
    }

    // Header (the "<timestamp> <class> <method>" line): timestamp + package dimmed, class in
    // cyan so the source reads at a glance, method in a brighter grey. No italics anywhere: on a
    // monospaced console the slant pulls glyphs off the character grid, which reads as misaligned.
    private static final SimpleAttributeSet ST_TS = style(new Color(0x6A, 0x6A, 0x6A), false, false);
    private static final SimpleAttributeSet ST_CLASS = style(new Color(0x78, 0xE1, 0xEB), true, false);
    private static final SimpleAttributeSet ST_METHOD = style(new Color(0xB9, 0xB9, 0xB9), false, false);
    // Message bodies, one per level.
    private static final SimpleAttributeSet ST_MSG = style(new Color(0xE6, 0xE6, 0xE6), false, false);
    private static final SimpleAttributeSet ST_INFO = style(new Color(0x79, 0xE0, 0xA0), true, false);
    private static final SimpleAttributeSet ST_WARN = style(new Color(0xFF, 0xC8, 0x5A), true, false);
    private static final SimpleAttributeSet ST_WARN_MSG = style(new Color(0xF4, 0xE2, 0xBD), false, false);
    private static final SimpleAttributeSet ST_SEV = style(new Color(0xFF, 0xFF, 0xFF), new Color(0xAA, 0x14, 0x14), true, false);
    private static final SimpleAttributeSet ST_SEV_MSG = style(new Color(0xFF, 0x6A, 0x6A), true, false);
    private static final SimpleAttributeSet ST_CONFIG = style(new Color(0x78, 0xE1, 0xEB), true, false);
    private static final SimpleAttributeSet ST_FINE = style(new Color(0x8F, 0x8F, 0x8F), true, false);
    private static final SimpleAttributeSet ST_FINE_MSG = style(new Color(0x8F, 0x8F, 0x8F), false, false);
    // Session banner ("=== NEW CORONAPOKER SESSION STARTED ===") and stack traces.
    private static final SimpleAttributeSet ST_SEP = style(new Color(0x5A, 0xA9, 0xD6), true, false);
    private static final SimpleAttributeSet ST_NUM = style(new Color(0xFF, 0xCF, 0x8A), false, false);
    private static final SimpleAttributeSet ST_EXC = style(new Color(0xFF, 0x6A, 0x6A), true, false);
    private static final SimpleAttributeSet ST_STACK = style(new Color(0x7A, 0x7A, 0x7A), false, false);

    // Timestamp width of DebugLog's fixed "yyyy-MM-dd HH:mm:ss" pattern.
    private static final int TS_WIDTH = 19;
    private static final Pattern HEADER = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .+");
    private static final Pattern LEVEL = Pattern.compile("^(SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST): ?");
    private static final Pattern STACK_AT = Pattern.compile("^\\s+(at |\\.\\.\\. )");
    private static final Pattern EXC_LINE = Pattern.compile("^(Caused by: |Suppressed: )?[\\w.$]+(Exception|Error|Throwable)([:\\s].*)?$");
    // A clean numeric token (with thousands/decimal separators), not glued to a letter, $, path
    // slash or dash on either side, so hex fragments, class$n, versions and paths aren't touched.
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w$/\\\\-])\\d+(?:[.,]\\d+)*(?![\\w$/\\\\-])");

    // The live pane is capped to the same window as the DebugLog buffer, so a long Settings
    // session can't grow the styled document without bound.
    private static final int MAX_DOC_CHARS = 512 * 1024;

    private final JTextPane debug_pane;
    private final GameLogDialog.BottomFollower follow;
    private final Consumer<String> listener;

    // Message style carried across a record's continuation lines (multi-line messages / stack
    // traces): set by the level line, reset by each header. EDT-only.
    private SimpleAttributeSet cur_msg_style = ST_MSG;

    public DebugSettingsPanel() {
        super(new BorderLayout());

        debug_pane = new JTextPane();
        debug_pane.setEditable(false);
        debug_pane.setBackground(GameLogDialog.LOG_BG);
        debug_pane.setForeground(new Color(0xE6, 0xE6, 0xE6));
        debug_pane.setFont(consoleFont());
        Helpers.JTextFieldRegularPopupMenu.addTo(debug_pane);

        JScrollPane debug_scroll = new JScrollPane(debug_pane);
        debug_scroll.setBorder(BorderFactory.createEmptyBorder());
        debug_scroll.getVerticalScrollBar().setUnitIncrement(16);
        // Bounded preferred size: the Settings dialog packs to its content, and a text pane with
        // many lines would report a huge preferred size that would blow up the dialog height. With
        // a modest fixed preferred size the content scrolls internally instead, and the other tabs
        // drive the final dialog size.
        debug_scroll.setPreferredSize(new Dimension(Math.round(620 * Helpers.DIALOG_ZOOM), Math.round(380 * Helpers.DIALOG_ZOOM)));
        add(debug_scroll, BorderLayout.CENTER);

        follow = new GameLogDialog.BottomFollower(debug_scroll, debug_pane);

        appendStyled(DebugLog.snapshot());
        debug_pane.setCaretPosition(debug_pane.getDocument().getLength());

        listener = (String record) -> Helpers.GUIRun(() -> {
            try {
                appendStyled(record);
                trimDocument();
                follow.followIfNeeded();
            } catch (Throwable t) {
                // The pane may be mid-teardown while the dialog closes — ignore.
            }
        });
        DebugLog.subscribe(listener);
    }

    private static Font consoleFont() {
        return GameLogDialog.LOG_FONT.deriveFont(GameLogDialog.LOG_FONT.getSize2D() * Helpers.DIALOG_ZOOM);
    }

    // Appends a chunk (a whole snapshot or a single live record) line by line, styling each line
    // by its role. A styling glitch on one line never breaks the console: it falls back to plain
    // text for that line.
    private void appendStyled(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        StyledDocument doc = debug_pane.getStyledDocument();
        int i = 0, n = text.length();
        while (i < n) {
            int nl = text.indexOf('\n', i);
            String line = (nl < 0) ? text.substring(i) : text.substring(i, nl + 1);
            i = (nl < 0) ? n : nl + 1;
            try {
                appendStyledLine(doc, line);
            } catch (BadLocationException ex) {
                insertSafe(doc, line, ST_MSG);
            }
        }
    }

    private void appendStyledLine(StyledDocument doc, String line) throws BadLocationException {
        if (line.isEmpty()) {
            return;
        }
        String body = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;

        if (body.trim().startsWith("===")) {
            insert(doc, line, ST_SEP);
            return;
        }
        if (HEADER.matcher(body).matches()) {
            appendHeader(doc, line, body);
            cur_msg_style = ST_MSG;
            return;
        }
        Matcher lm = LEVEL.matcher(body);
        if (lm.find()) {
            appendLevelLine(doc, line, lm.group(1));
            return;
        }
        if (STACK_AT.matcher(body).find()) {
            insert(doc, line, ST_STACK);
            return;
        }
        if (EXC_LINE.matcher(body).matches()) {
            insert(doc, line, ST_EXC);
            return;
        }
        appendMessage(doc, line, cur_msg_style);
    }

    // "<19-char timestamp> <fully.qualified.Class> [method]" — timestamp + package dim, class in
    // cyan, method in dim italic. The exact original text is preserved so copy/paste is intact.
    private void appendHeader(StyledDocument doc, String line, String body) throws BadLocationException {
        String nl = line.endsWith("\n") ? "\n" : "";
        String ts = body.substring(0, TS_WIDTH);
        String rest = body.substring(TS_WIDTH + 1); // drop the single space after the timestamp
        int lastSpace = rest.lastIndexOf(' ');
        String cls = lastSpace >= 0 ? rest.substring(0, lastSpace) : rest;
        String method = lastSpace >= 0 ? rest.substring(lastSpace + 1) : null;
        int lastDot = cls.lastIndexOf('.');
        String pkg = lastDot >= 0 ? cls.substring(0, lastDot + 1) : "";
        String simple = lastDot >= 0 ? cls.substring(lastDot + 1) : cls;

        insert(doc, ts + " ", ST_TS);
        if (!pkg.isEmpty()) {
            insert(doc, pkg, ST_TS);
        }
        insert(doc, simple, ST_CLASS);
        if (method != null) {
            insert(doc, " ", ST_TS);
            insert(doc, method, ST_METHOD);
        }
        insert(doc, nl, ST_MSG);
    }

    // "<LEVEL>: <message>" — the level tag in its level colour, the message in the matching body
    // style (with numeric highlights), and remembered as the style for the record's follow-on lines.
    private void appendLevelLine(StyledDocument doc, String line, String level) throws BadLocationException {
        SimpleAttributeSet tag;
        SimpleAttributeSet msg;
        switch (level) {
            case "SEVERE":
                tag = ST_SEV;
                msg = ST_SEV_MSG;
                break;
            case "WARNING":
                tag = ST_WARN;
                msg = ST_WARN_MSG;
                break;
            case "CONFIG":
                tag = ST_CONFIG;
                msg = ST_MSG;
                break;
            case "FINE":
            case "FINER":
            case "FINEST":
                tag = ST_FINE;
                msg = ST_FINE_MSG;
                break;
            default: // INFO
                tag = ST_INFO;
                msg = ST_MSG;
                break;
        }
        cur_msg_style = msg;
        int tagEnd = level.length() + 1; // "<LEVEL>:"
        insert(doc, line.substring(0, tagEnd), tag);
        appendMessage(doc, line.substring(tagEnd), msg);
    }

    // Inserts a message run in the given base style, overlaying numeric tokens in amber (skipped
    // for the dim FINE and the red SEVERE bodies, which read better kept uniform).
    private void appendMessage(StyledDocument doc, String text, SimpleAttributeSet base) throws BadLocationException {
        if (text.isEmpty()) {
            return;
        }
        boolean numbers = base == ST_MSG || base == ST_WARN_MSG;
        if (!numbers) {
            insert(doc, text, base);
            return;
        }
        int len = text.length();
        SimpleAttributeSet[] cs = new SimpleAttributeSet[len];
        Arrays.fill(cs, base);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            // Skip numbers sitting inside a hex group (a fingerprint / hash / id like
            // "9073 9a5d 756e ..."): highlighting only the all-digit groups looks arbitrary.
            if (adjacentHexToken(text, m.start(), m.end())) {
                continue;
            }
            for (int k = m.start(); k < m.end(); k++) {
                cs[k] = ST_NUM;
            }
        }
        int run = 0;
        while (run < len) {
            int end = run + 1;
            SimpleAttributeSet a = cs[run];
            while (end < len && cs[end] == a) {
                end++;
            }
            doc.insertString(doc.getLength(), text.substring(run, end), a);
            run = end;
        }
    }

    // True when the space-delimited token just before or after [start, end) is a hex group (>= 4
    // chars, all hex, at least one a-f letter) — i.e. the number is part of a fingerprint / hash.
    private static boolean adjacentHexToken(String text, int start, int end) {
        int i = start - 1;
        while (i >= 0 && text.charAt(i) == ' ') {
            i--;
        }
        int prevEnd = i + 1;
        while (i >= 0 && text.charAt(i) != ' ') {
            i--;
        }
        if (prevEnd > i + 1 && isHexToken(text.substring(i + 1, prevEnd))) {
            return true;
        }
        int j = end;
        while (j < text.length() && text.charAt(j) == ' ') {
            j++;
        }
        int nextStart = j;
        while (j < text.length() && text.charAt(j) != ' ') {
            j++;
        }
        return j > nextStart && isHexToken(text.substring(nextStart, j));
    }

    private static boolean isHexToken(String t) {
        if (t.length() < 4) {
            return false;
        }
        boolean hasLetter = false;
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            boolean digit = c >= '0' && c <= '9';
            boolean letter = (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!digit && !letter) {
                return false;
            }
            hasLetter |= letter;
        }
        return hasLetter;
    }

    private static void insert(StyledDocument doc, String s, SimpleAttributeSet st) throws BadLocationException {
        if (!s.isEmpty()) {
            doc.insertString(doc.getLength(), s, st);
        }
    }

    private static void insertSafe(StyledDocument doc, String s, SimpleAttributeSet st) {
        try {
            insert(doc, s, st);
        } catch (BadLocationException ignored) {
        }
    }

    // Trims the head of the styled document to keep it within the cap, snapping to a line boundary
    // so a chunk is never cut mid-line. EDT-only (Swing document mutation).
    private void trimDocument() {
        StyledDocument doc = debug_pane.getStyledDocument();
        int len = doc.getLength();
        if (len <= MAX_DOC_CHARS) {
            return;
        }
        try {
            int cut = len - MAX_DOC_CHARS;
            String window = doc.getText(cut, Math.min(4000, len - cut));
            int nl = window.indexOf('\n');
            if (nl >= 0) {
                cut += nl + 1;
            }
            doc.remove(0, cut);
        } catch (BadLocationException ex) {
            // Nothing safe to do here; leave the document as-is.
        }
    }

    /**
     * Restores the monospace console font after SettingsDialog's {@code setUniformFont} pass, which
     * would otherwise overwrite it with {@code GUI_FONT}. Call this after that pass.
     */
    public void reapplyConsoleFont() {
        debug_pane.setFont(consoleFont());
    }

    /**
     * Jumps to the bottom of the console. Called when the dialog opens, to show the most recent log
     * lines.
     */
    public void snapToBottom() {
        follow.snapToBottom();
    }

    /**
     * Unsubscribes from {@link DebugLog} when the dialog closes. Idempotent.
     */
    public void cleanup() {
        DebugLog.unsubscribe(listener);
    }
}
