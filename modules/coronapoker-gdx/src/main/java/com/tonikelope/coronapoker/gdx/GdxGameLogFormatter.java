/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure presentation formatting for the renderer-native game log.
 *
 * <p>The dealer remains the sole source of log content.  This class only
 * interprets the role markers and colour hierarchy already emitted for the
 * classic log, keeping parsing rules out of the table renderer.</p>
 */
final class GdxGameLogFormatter {

    private static final Color DEFAULT = new Color(0xffffffff);
    private static final Color HEADER = new Color(0x78e1ebff);
    private static final Color BOARD = new Color(0x96c8ffff);
    private static final Color AMOUNT = new Color(0xffc85aff);
    private static final Color DIM = new Color(0xaaaaaaff);
    private static final Color RANK = new Color(0xcdcdcdff);
    private static final Color WIN = new Color(0x78e678ff);
    private static final Color LOSS = new Color(0xeb7878ff);
    private static final Color NICK = new Color(0x8edcffff);
    private static final Color FOLD = new Color(0x99a9beff);
    private static final Color PASSIVE = new Color(0x91cdc9ff);
    private static final Color AGGRESSIVE = new Color(0xffbd80ff);
    private static final Color ALL_IN = new Color(0xf4a7d5ff);
    private static final Color VERIFIED = new Color(0x8ce5aeff);
    private static final Color SECURITY = new Color(0xaad8eeff);
    private static final String ZERO_TRUST = "[ZERO-TRUST]";
    private static final Pattern HAND_TITLE = Pattern.compile(
            "(?iu)^\\s*(?:MANO|HAND)\\s*\\(\\d+\\)\\s*$");
    private static final Pattern CHECKMARK = Pattern.compile("^\\s*[✓✔]\\ufe0f?\\s*");
    private static final Pattern VERIFICATION = Pattern.compile(
            "(?iu)^(?:MANO|HAND)\\s+\\d+(?:\\s+VERIFICADA CRIPTOGRÁFICAMENTE"
                    + "|\\s+CRYPTOGRAPHICALLY VERIFIED|:\\s*(?:BARAJADO VERIFICADO|SHUFFLE VERIFIED))"
                    + "|^(?:\\[ZERO-TRUST\\]\\s+)?(?:BARAJADO VERIFICADO|SHUFFLE VERIFIED"
                    + "|CLAVE DE SESI[ÓO]N RESTAURADA|SESSION KEY SUCCESSFULLY RESTORED)");
    private static final Pattern PENDING_VERIFICATION = Pattern.compile(
            "(?iu)^(?:⚠\\ufe0f?\\s*)?(?:(?:MANO|HAND)\\s+\\d+:\\s*"
                    + "(?:BARAJADO SIN VERIFICAR|SHUFFLE NOT VERIFIED)"
                    + "|(?:AVISO: EL HOST REPRODUJO ACCIONES|NOTICE: THE HOST REPLAYED ACTIONS))");
    private static final Pattern SECURITY_FAILURE = Pattern.compile(
            "(?iu)^(?:CR[IÍ]TICO: HA FALLADO EL CIERRE SEGURO|CRITICAL: SECURE HAND CLOSURE FAILED)"
                    + "|^(?:⚠\\ufe0f?\\s*)?(?:DIVERGENCIA EN (?:LA )?VERIFICACI[ÓO]N CRIPTOGR[ÁA]FICA"
                    + "|FIRMA CRIPTOGR[ÁA]FICA INV[ÁA]LIDA EN ACCI[ÓO]N)");
    private static final Pattern ACTION = Pattern.compile(
            "(?iu)^(?:\\s+)?(?:(\\[AUTO\\])\\s*)?(ALL[ -]IN|NO VAS?|FOLD|PASA(?:S)?|CHECK"
                    + "|VAS?|CALL|APUESTA(?:S)?|BET|SUBE(?:S)?|RAISE|MUESTRA|SHOW)(?=\\s|\\(|$)");
    private static final Pattern RESULT = Pattern.compile(
            "(?iu)\\b(?:GANA(?:DOR)?|WINS(?: MAIN| SIDE)? POT|PIERDE|LOSES(?: MAIN| SIDE)? POT)\\b");
    private static final Pattern MONEY_CELL = Pattern.compile(
            "\\s*[+-]?\\d[\\d.,]*[KkMm]?\\s*");
    private static final Color CARD_RED = new Color(0xc80000ff);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "\\(\\s*[+-]?\\d[\\d.,\\s]*[KkMm]?\\)");
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\[[^\\[\\]]*[\\u2660\\u2665\\u2666\\u2663]\\]");
    private static final Pattern CARD_PAREN_PATTERN = Pattern.compile(
            "\\(\\s*((?:\\[[^\\[\\]]*[\\u2660\\u2665\\u2666\\u2663]\\]\\s*)+)\\)");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\((?:---|\\*\\*\\*)\\)");
    private static final Pattern GRID_PATTERN = Pattern.compile(
            "[\\u2500-\\u257f]+");
    private static final Pattern EMPHASIS_PATTERN = Pattern.compile(
            "(?iu)\\b(?:FLOP|TURN|RIVER|GANADOR|GANA|PIERDE|WINS POT|"
                    + "LOSES POT|ERROR|FALLO)\\b");
    private static final Pattern GENERIC_INTEGRITY = Pattern.compile(
            "(?iu)^(?:MANO ANULADA|HAND CANCELED|MOTIVO:|REASON:)");
    private static final Pattern CRITICAL_SECURITY_PATTERN = Pattern.compile(
            "(?iu)^\\s*\\u26a0(?:\\ufe0f)?\\s*\\[(?:SECURITY ALERT|"
                    + "ALERTA DE SEGURIDAD|ANOMALOUS (?:PLAYER|SERVER) "
                    + "BEHAVIOUR|COMPORTAMIENTO ANÓMALO (?:DE UN JUGADOR|"
                    + "DEL SERVIDOR)|ZERO-TRUST ALERT)\\]"
                    + "|^\\s*\\u274c\\s*\\[ZERO-TRUST\\]"
                    + "|^\\s*\\u26a0(?:\\ufe0f)?\\s*\\[ZERO-TRUST\\]\\s*"
                    + "(?:ACTION REJECTED|ACCIÓN RECHAZADA)");
    private static final Pattern CRITICAL_INTEGRITY_PATTERN = Pattern.compile(
            "(?iu)^\\s*(?:MANO ANULADA|HAND CANCELED|MOTIVO:|REASON:|"
                    + "\\p{So}?\\s*\\[LOCKDOWN\\]|"
                    + "\\u26a0\\s*(?:DIVERGENCIA EN VERIFICACIÓN "
                    + "CRIPTOGRÁFICA|CRYPTOGRAPHIC VERIFICATION DIVERGENCE|"
                    + "FIRMA CRIPTOGRÁFICA INVÁLIDA EN ACCIÓN|"
                    + "INVALID CRYPTOGRAPHIC SIGNATURE ON ACTION)|"
                    + "\\u26a0\\s*(?:Aviso: .*?(?:NO ENVIÓ FIRMA DE CIERRE|"
                    + "FIRMA CRIPTOGRÁFICA INVÁLIDA DURANTE ESTA MANO|"
                    + "NO LES LLEGÓ LA PRUEBA DE BARAJADO HONESTO)|"
                    + "Notice: .*?(?:DID NOT SEND CLOSING SIGNATURE|"
                    + "INVALID CRYPTOGRAPHIC SIGNATURE DURING THIS HAND|"
                    + "DID NOT RECEIVE THE HONEST-SHUFFLE PROOF)))");

    private GdxGameLogFormatter() {
    }

    static void wrapLine(List<String> target, String line,
            int maximumCharacters) {
        // Swing keeps each framed balance/result row as one document line.
        // Splitting one destroys the box grid and shifts its numeric columns.
        if (marker(line) != Marker.NONE) {
            target.add(line);
            return;
        }
        if (line.length() <= maximumCharacters) {
            target.add(line);
            return;
        }
        String remaining = line;
        boolean continuation = false;
        while (remaining.length() > maximumCharacters) {
            int split = remaining.lastIndexOf(' ', maximumCharacters);
            if (split <= 0) split = maximumCharacters;
            split = moveSplitOutsideCardToken(remaining, split);
            target.add((continuation ? "  " : "")
                    + remaining.substring(0, split).stripTrailing());
            remaining = remaining.substring(split).stripLeading();
            continuation = true;
        }
        target.add((continuation ? "  " : "") + remaining);
    }

    private static int moveSplitOutsideCardToken(String value, int split) {
        int open = value.lastIndexOf('[', split);
        int close = value.lastIndexOf(']', split);
        if (open <= close) return split;
        int tokenEnd = value.indexOf(']', split);
        if (tokenEnd >= 0 && tokenEnd + 1 < value.length()) {
            return tokenEnd + 1;
        }
        return Math.max(1, open);
    }

    static List<Run> runs(String value) {
        return runs(value, List.of());
    }

    static List<Run> runs(String value, List<String> nicknames) {
        String visible = displayText(value);
        // Role/grid markers already reserve a dedicated gutter in the GDX
        // renderer. Swing's payload adds one separator space after the marker,
        // but LibGDX measures that space only when it shares a run with visible
        // glyphs; a colour split can turn it into a zero-width whitespace run.
        // Remove it uniformly so border, header, body and footer start at the
        // exact same pixel column.
        if (visible.isEmpty()) return List.of();
        Color base = lineColor(value);
        Color[] colors = new Color[visible.length()];
        Arrays.fill(colors, base);
        boolean[] bold = new boolean[visible.length()];
        Arrays.fill(bold, false);
        if (marker(value) != Marker.NONE) {
            overlay(colors, visible, GRID_PATTERN, DIM);
        }
        if (base != HEADER && base != BOARD && base != LOSS) {
            int arrow = visible.indexOf(" -> ");
            if (arrow >= 0) {
                Arrays.fill(colors, arrow, colors.length, RANK);
            }
        }
        overlay(colors, visible, PLACEHOLDER_PATTERN, DIM);
        overlay(colors, visible, AMOUNT_PATTERN, AMOUNT);
        overlayBold(bold, visible, AMOUNT_PATTERN);
        overlayBold(bold, visible, EMPHASIS_PATTERN);
        boolean table = isFramedTableRow(value);
        if (securityTone(value) != SecurityTone.NONE) {
            Arrays.fill(colors, base);
            if (visible.startsWith(ZERO_TRUST)) {
                Arrays.fill(bold, 0, ZERO_TRUST.length(), true);
            }
        } else if (table) {
            colorTableCells(visible, marker(value), colors);
            colorTableNickname(visible, nicknames, colors);
        } else {
            int nicknameEnd = colorNicknames(visible, nicknames, colors);
            if (nicknameEnd > 0) {
                Matcher action = ACTION.matcher(visible.substring(nicknameEnd));
                if (action.find()) {
                    int first = nicknameEnd + action.start(2);
                    int end = nicknameEnd + action.end(2);
                    Arrays.fill(colors, first, end, actionColor(action.group(2)));
                }
            }
            Matcher result = RESULT.matcher(visible);
            result.region(Math.max(0, nicknameEnd), visible.length());
            while (result.find()) {
                String token = result.group().toUpperCase(Locale.ROOT);
                Arrays.fill(colors, result.start(), result.end(),
                        token.startsWith("PIERDE") || token.startsWith("LOSES") ? LOSS : WIN);
            }
        }
        // A framed table must use one font with one glyph advance from the
        // first corner to the last. Mixing the regular and bold bitmap fonts
        // makes otherwise monospaced columns use different pixel widths, so
        // the horizontal borders no longer meet the vertical separators.
        // Keep the colour hierarchy and role icons, but render every glyph of
        // a marked box-drawing row with the same regular monospace font.
        if (table) {
            Arrays.fill(bold, false);
        }
        ArrayList<Run> runs = new ArrayList<>();
        Matcher cards = CARD_PATTERN.matcher(visible);
        int position = 0;
        while (cards.find()) {
            appendTextRuns(runs, visible, colors, bold, position,
                    cards.start());
            String token = cards.group();
            boolean red = token.indexOf('\u2665') >= 0
                    || token.indexOf('\u2666') >= 0;
            runs.add(new Run(token, red ? CARD_RED : Color.BLACK, true,
                    true));
            position = cards.end();
        }
        appendTextRuns(runs, visible, colors, bold, position,
                visible.length());
        return List.copyOf(runs);
    }

    private static Color actionColor(String action) {
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "FOLD", "NO VA", "NO VAS" -> FOLD;
            case "ALL IN", "ALL-IN" -> ALL_IN;
            case "BET", "RAISE", "SUBE", "SUBES", "APUESTA", "APUESTAS" -> AGGRESSIVE;
            default -> PASSIVE;
        };
    }

    private static int colorNicknames(String visible, List<String> nicknames, Color[] colors) {
        int leading = visible.length() - visible.stripLeading().length();
        int firstEnd = 0;
        for (String nickname : nicknames) {
            if (nickname.isBlank()) continue;
            int start = visible.indexOf(nickname);
            while (start >= 0) {
                int end = start + nickname.length();
                if ((start == 0 || !nameCharacter(visible.charAt(start - 1)))
                        && (end == visible.length() || !nameCharacter(visible.charAt(end)))) {
                    Arrays.fill(colors, start, end, NICK);
                    if (start == leading) firstEnd = Math.max(firstEnd, end);
                }
                start = visible.indexOf(nickname, end);
            }
        }
        return firstEnd;
    }

    private static boolean nameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$'
                || value == '-' || value == '.';
    }

    private static void colorTableNickname(String visible, List<String> nicknames, Color[] colors) {
        int left = visible.indexOf('│');
        int right = visible.indexOf('│', left + 1);
        if (left < 0 || right < 0) return;
        String cell = visible.substring(left + 1, right).strip();
        if (!cell.isEmpty() && nicknames.contains(cell)) {
            int start = visible.indexOf(cell, left + 1);
            Arrays.fill(colors, start, start + cell.length(), NICK);
        }
    }

    private static void colorTableCells(String visible, Marker marker, Color[] colors) {
        int left = visible.indexOf('│');
        if (left < 0) return;
        for (int right = visible.indexOf('│', left + 1); right >= 0;
                right = visible.indexOf('│', left + 1)) {
            String cell = visible.substring(left + 1, right);
            if (marker == Marker.GRID && !cell.isBlank()) {
                Arrays.fill(colors, left + 1, right, HEADER);
            } else if (MONEY_CELL.matcher(cell).matches()) {
                Arrays.fill(colors, left + 1, right, AMOUNT);
            }
            left = right;
        }
    }

    static boolean needsSectionGap(String previous, String next) {
        if (isFramedTableRow(previous)) return securityTone(next) != SecurityTone.NONE;
        return isFramedTableRow(next) && (isHandTitle(previous)
                || securityTone(previous) != SecurityTone.NONE);
    }

    static boolean isHandTitle(String value) {
        return value != null && HAND_TITLE.matcher(value).matches();
    }

    static String tagSecurityLine(String value) {
        if (value.contains(ZERO_TRUST) || securityTone(value) == SecurityTone.NONE
                || GENERIC_INTEGRITY.matcher(value.stripLeading()).find()) return value;
        return ZERO_TRUST + " " + value.stripLeading();
    }

    static SecurityTone securityTone(String value) {
        if (value == null) return SecurityTone.NONE;
        String payload = visibleText(value).stripLeading();
        boolean tagged = payload.contains(ZERO_TRUST);
        if (payload.startsWith(ZERO_TRUST)) {
            payload = payload.substring(ZERO_TRUST.length()).stripLeading();
        }
        String withoutCheck = CHECKMARK.matcher(payload).replaceFirst("");
        if (withoutCheck.length() != payload.length() && VERIFICATION.matcher(withoutCheck).find()) {
            return SecurityTone.VERIFIED;
        }
        if (isCriticalIntegrityLine(payload) || SECURITY_FAILURE.matcher(payload).find()) {
            return SecurityTone.FAILURE;
        }
        if (PENDING_VERIFICATION.matcher(payload).find()) return SecurityTone.PENDING;
        return tagged ? SecurityTone.NOTICE : SecurityTone.NONE;
    }

    enum SecurityTone { NONE, VERIFIED, PENDING, FAILURE, NOTICE }

    static boolean isFramedTableRow(String value) {
        return marker(value) != Marker.NONE
                && GRID_PATTERN.matcher(visibleText(value)).find();
    }

    /**
     * Identifies the translated zero-trust prefixes emitted by the dealer.
     * The game log deliberately carries plain text rather than presentation
     * metadata, so the native renderer recognises the complete, anchored
     * Spanish/English markers and never guesses from an ordinary occurrence
     * of words such as "error" or "security" in a nickname or chat line.
     */
    static boolean isCriticalIntegrityLine(String value) {
        if (value == null) return false;
        String visible = visibleText(value).stripLeading();
        if (visible.startsWith(ZERO_TRUST)) {
            visible = visible.substring(ZERO_TRUST.length()).stripLeading();
        }
        return CRITICAL_SECURITY_PATTERN.matcher(visible).find()
                || CRITICAL_INTEGRITY_PATTERN.matcher(visible).find()
                || SECURITY_FAILURE.matcher(visible).find();
    }

    /** Exact visible payload used by both text and vector-grid layout. */
    static String displayText(String value) {
        String visible = CARD_PAREN_PATTERN.matcher(visibleText(value))
                .replaceAll("$1");
        SecurityTone tone = securityTone(value);
        if (tone != SecurityTone.NONE) {
            visible = tagSecurityLine(visible).stripLeading();
            if (tone == SecurityTone.VERIFIED) {
                visible = CHECKMARK.matcher(visible).replaceFirst("");
                if (visible.startsWith(ZERO_TRUST)) {
                    visible = ZERO_TRUST + " " + CHECKMARK.matcher(
                            visible.substring(ZERO_TRUST.length())).replaceFirst("").stripLeading();
                }
            }
        }
        if (isFramedTableRow(value) && visible.startsWith(" ")) {
            visible = visible.substring(1);
        }
        return visible;
    }

    static boolean isBoxDrawingCharacter(char value) {
        return value >= '\u2500' && value <= '\u257f';
    }

    private static void appendTextRuns(List<Run> runs, String visible,
            Color[] colors, boolean[] bold, int from, int to) {
        int start = from;
        while (start < to) {
            int end = start + 1;
            while (end < to && colors[end] == colors[start]
                    && bold[end] == bold[start]) end++;
            runs.add(new Run(visible.substring(start, end), colors[start],
                    false, bold[start]));
            start = end;
        }
    }

    static String cardValue(String token) {
        String inner = token.substring(1, token.length() - 1);
        return inner.substring(0, inner.length() - 1);
    }

    static String cardSuit(String token) {
        return token.substring(token.length() - 2, token.length() - 1);
    }

    static Marker marker(String value) {
        if (value == null || value.length() < 4) return Marker.NONE;
        return switch (value.substring(0, 4)) {
            case "(D )" -> Marker.DEALER;
            case "(SB)" -> Marker.SMALL_BLIND;
            case "(BB)" -> Marker.BIG_BLIND;
            case "(  )" -> Marker.BLANK;
            case "(##)" -> Marker.GRID;
            case "($$)", "(A )" -> Marker.MONEY;
            case "(ST)" -> Marker.STRADDLE;
            case "(DS)" -> Marker.DEALER_STRADDLE;
            case "(MV)" -> Marker.MULTIVERSE;
            default -> Marker.NONE;
        };
    }

    static String visibleText(String value) {
        if (value == null) return "";
        return marker(value) == Marker.NONE ? value : value.substring(4);
    }

    private static Color lineColor(String value) {
        if (isHandTitle(value)) return HEADER;
        switch (securityTone(value)) {
            case VERIFIED -> { return VERIFIED; }
            case PENDING -> { return AMOUNT; }
            case FAILURE -> { return DEFAULT; }
            case NOTICE -> { return SECURITY; }
            case NONE -> { }
        }
        Marker marker = marker(value);
        String visible = visibleText(value);
        if (marker == Marker.GRID || marker == Marker.MULTIVERSE) return DIM;
        Color balanceResult = balanceResultColor(marker, visible);
        if (balanceResult != null) return balanceResult;
        String upper = visible.toUpperCase(Locale.ROOT);
        String stripped = upper.stripLeading();
        if (upper.contains("NI GANA NI PIERDE")
                || upper.contains("BREAK EVEN")) return DEFAULT;
        if (stripped.startsWith("ERROR:") || stripped.startsWith("FALLO:")) return LOSS;
        if (visible.contains("***************")
                || stripped.startsWith("┌") || stripped.startsWith("│")
                || stripped.startsWith("└")) return HEADER;
        if (stripped.startsWith("FLOP -> ")
                || stripped.startsWith("TURN -> ")
                || stripped.startsWith("RIVER -> ")) return BOARD;
        if (stripped.startsWith("PAUSE (") || upper.contains("LOKI:")) {
            return DIM;
        }
        return DEFAULT;
    }

    /** Mirrors Swing's final-result table without matching words in nicknames. */
    private static Color balanceResultColor(Marker marker, String visible) {
        if (marker != Marker.BLANK) return null;
        int last = visible.lastIndexOf('│');
        if (last <= 0) return null;
        int previous = visible.lastIndexOf('│', last - 1);
        if (previous < 0) return null;
        String result = visible.substring(previous + 1, last)
                .strip().toUpperCase(Locale.ROOT);
        if (result.equals("NI GANA NI PIERDE")
                || result.equals("BREAK EVEN")) return DEFAULT;
        if (result.equals("GANA") || result.startsWith("GANA ")
                || result.equals("WINS") || result.startsWith("WINS ")) {
            return WIN;
        }
        if (result.equals("PIERDE") || result.startsWith("PIERDE ")
                || result.equals("LOSES") || result.startsWith("LOSES ")) {
            return LOSS;
        }
        return null;
    }

    private static void overlay(Color[] colors, String value,
            Pattern pattern, Color color) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            Arrays.fill(colors, matcher.start(), matcher.end(), color);
        }
    }

    private static void overlayBold(boolean[] bold, String value,
            Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            Arrays.fill(bold, matcher.start(), matcher.end(), true);
        }
    }

    record Run(String text, Color color, boolean card, boolean bold) {

        Run(String text, Color color) {
            this(text, color, false, false);
        }
    }

    enum Marker {
        NONE, DEALER, SMALL_BLIND, BIG_BLIND, BLANK, GRID, MONEY,
        STRADDLE, DEALER_STRADDLE, MULTIVERSE
    }
}
