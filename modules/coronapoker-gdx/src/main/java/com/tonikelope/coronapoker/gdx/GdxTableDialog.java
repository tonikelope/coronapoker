/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** One renderer-owned modal request; poker state never lives here. */
final class GdxTableDialog {

    private static final BigDecimal AUTO_CALL_STEP = new BigDecimal("0.05");

    enum Kind {
        ERROR,
        INFO,
        CONFIRM,
        TIMED_WARNING,
        GAME_OVER,
        AUTO_ACTION,
        REBUY,
        AUTO_CALL,
        HAND_LIMIT
    }

    private final Kind kind;
    private final String title;
    private volatile String message;
    private final GameDialogSink.Icon icon;
    private final int preferredWidth;
    private final int seconds;
    private final boolean timeoutAccepts;
    private final String negativeLabel;
    private final String positiveLabel;
    private final int minimumAmount;
    private final int maximumAmount;
    private final int amountStep;
    private int amount;
    private BigDecimal autoCallCommittedAmount;
    private String autoCallAmountText;
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();
    private float openedAt = Float.NaN;
    private float timerPausedAt = Float.NaN;
    private float autoDismissSeconds;
    private boolean optionEnabled;
    private boolean noLimit;
    private boolean externallyControlled;
    private boolean recovery;
    private boolean deferCloseAfterDecision;
    private boolean externalCloseReleased;
    private String waitingMessage = "";

    GdxTableDialog(Kind kind, String message, GameDialogSink.Icon icon,
            int preferredWidth, int seconds) {
        this(kind, defaultTitle(kind), message, icon, preferredWidth, seconds,
                kind == Kind.TIMED_WARNING, "CANCELAR",
                kind == Kind.CONFIRM ? "ACEPTAR" : "CERRAR",
                0, 0, 0);
    }

    GdxTableDialog(Kind kind, String title, String message,
            GameDialogSink.Icon icon, int preferredWidth, int seconds,
            boolean timeoutAccepts, String negativeLabel,
            String positiveLabel) {
        this(kind, title, message, icon, preferredWidth, seconds,
                timeoutAccepts, negativeLabel, positiveLabel, 0, 0, 0);
    }

    GdxTableDialog(String title, String message, int preferredWidth,
            int seconds, boolean timeoutAccepts, String negativeLabel,
            int minimumAmount, int maximumAmount, int defaultAmount) {
        this(title, message, preferredWidth, seconds, timeoutAccepts,
                negativeLabel, "ACEPTAR", minimumAmount, maximumAmount,
                defaultAmount);
    }

    GdxTableDialog(String title, String message, int preferredWidth,
            int seconds, boolean timeoutAccepts, String negativeLabel,
            String positiveLabel, int minimumAmount, int maximumAmount,
            int defaultAmount) {
        this(Kind.REBUY, title, message, GameDialogSink.Icon.NONE,
                preferredWidth, seconds, timeoutAccepts, negativeLabel,
                positiveLabel, minimumAmount, maximumAmount, defaultAmount);
    }

    static GdxTableDialog autoCall(boolean enabled, double maximum) {
        return autoCall(enabled, maximum, GameText.keys());
    }

    static GdxTableDialog autoCall(boolean enabled, double maximum,
            GameText text) {
        boolean unlimited = maximum <= 0d;
        BigDecimal initial = BigDecimal.valueOf(unlimited ? 0.05d : maximum)
                .max(AUTO_CALL_STEP).setScale(2, RoundingMode.HALF_UP);
        int cents = centsForLegacyAmount(initial);
        GdxTableDialog dialog = new GdxTableDialog(Kind.AUTO_CALL,
                tr(text, "gdx.auto_call.title", "AUTO IGUALAR"),
                tr(text, "auto_call.nota",
                        "IMPORTE MÁXIMO QUE SE IGUALARÁ AUTOMÁTICAMENTE"),
                GameDialogSink.Icon.NONE, 820, 0, false,
                tr(text, "ui.cancelar", "CANCELAR"),
                tr(text, "ui.aceptar", "ACEPTAR"), 5,
                Integer.MAX_VALUE, cents);
        dialog.optionEnabled = enabled;
        dialog.noLimit = unlimited;
        dialog.autoCallCommittedAmount = initial;
        dialog.autoCallAmountText = decimalText(initial);
        return dialog;
    }

    static GdxTableDialog handLimit(int currentHand, int maximumHands) {
        return handLimit(currentHand, maximumHands, GameText.keys());
    }

    static GdxTableDialog handLimit(int currentHand, int maximumHands,
            GameText text) {
        int minimum = Math.max(1, currentHand + 1);
        int selected = maximumHands > currentHand
                ? maximumHands : minimum;
        GdxTableDialog dialog = new GdxTableDialog(Kind.HAND_LIMIT,
                tr(text, "game.limite_de_manos_2", "LÍMITE DE MANOS"),
                tr(text, "gdx.hand_limit.detail",
                        "LA TIMBA TERMINA AL ALCANZAR ESTE NÚMERO DE MANOS"),
                GameDialogSink.Icon.NONE, 820, 0, false,
                tr(text, "ui.cancelar", "CANCELAR"),
                tr(text, "ui.guardar", "GUARDAR"), minimum,
                1_000_000, selected);
        dialog.noLimit = maximumHands == -1;
        return dialog;
    }

    static GdxTableDialog autoAction(String action) {
        return autoAction(action, GameText.keys());
    }

    static GdxTableDialog autoAction(String action, GameText text) {
        return new GdxTableDialog(Kind.AUTO_ACTION,
                tr(text, "modo_auto.titulo", "MODO AUTO"), action,
                GameDialogSink.Icon.NONE, 720, 5, true,
                tr(text, "ui.cancelar", "CANCELAR"), "");
    }

    static GdxTableDialog gameOverChoice(int seconds) {
        return gameOverChoice(seconds, GameText.keys());
    }

    static GdxTableDialog gameOverChoice(int seconds, GameText text) {
        return new GdxTableDialog(Kind.GAME_OVER, "GAME OVER", "",
                GameDialogSink.Icon.STOP, 900, seconds, false,
                tr(text, "player.espectador", "ESPECTADOR"),
                tr(text, "ui.continuar", "CONTINUAR"), 0, 0, 0);
    }

    static GdxTableDialog gameOverFinal(float dwellSeconds) {
        GdxTableDialog dialog = new GdxTableDialog(Kind.GAME_OVER,
                "GAME OVER", "", GameDialogSink.Icon.STOP, 900, 0,
                false, "", "", 0, 0, 0);
        dialog.autoDismissSeconds = Math.max(0f, dwellSeconds);
        dialog.externallyControlled = dwellSeconds <= 0f;
        return dialog;
    }

    /**
     * Static informational overlay that releases its dealer decision after the
     * exact visual dwell used by Swing.  It deliberately has no visible
     * countdown: Swing's direct game-over card simply remains for 2.5 seconds.
     */
    static GdxTableDialog autoDismissInfo(String title, String message,
            GameDialogSink.Icon icon, int preferredWidth,
            float autoDismissSeconds) {
        GdxTableDialog dialog = new GdxTableDialog(Kind.INFO, title, message,
                icon, preferredWidth, 0, false, "", "");
        dialog.autoDismissSeconds = Math.max(0f, autoDismissSeconds);
        return dialog;
    }

    /**
     * Renderer-owned equivalent of Swing's non-dismissible RecoverDialog.
     * Only the dealer's {@link GameDecisionSink.CloseHandle} may close it.
     */
    static GdxTableDialog recovery() {
        return recovery(GameText.keys());
    }

    static GdxTableDialog recovery(GameText text) {
        GdxTableDialog dialog = new GdxTableDialog(Kind.INFO,
                tr(text, "game.recuperando_timba_2", "RECUPERANDO TIMBA"),
                tr(text, "gdx.recovery.detail",
                        "RECONSTRUYENDO LA MANO EN CURSO…"),
                GameDialogSink.Icon.NONE, 820, 0, false, "", "");
        dialog.externallyControlled = true;
        dialog.recovery = true;
        return dialog;
    }

    private GdxTableDialog(Kind kind, String title, String message,
            GameDialogSink.Icon icon, int preferredWidth, int seconds,
            boolean timeoutAccepts, String negativeLabel,
            String positiveLabel, int minimumAmount, int maximumAmount,
            int defaultAmount) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = Objects.requireNonNull(title, "title");
        this.message = plainText(Objects.requireNonNull(message, "message"));
        this.icon = Objects.requireNonNull(icon, "icon");
        this.preferredWidth = Math.max(0, preferredWidth);
        this.seconds = Math.max(0, seconds);
        this.timeoutAccepts = timeoutAccepts;
        this.negativeLabel = Objects.requireNonNull(negativeLabel,
                "negativeLabel");
        this.positiveLabel = Objects.requireNonNull(positiveLabel,
                "positiveLabel");
        this.minimumAmount = minimumAmount;
        this.maximumAmount = Math.max(minimumAmount, maximumAmount);
        amountStep = kind == Kind.AUTO_CALL ? 5
                : kind == Kind.HAND_LIMIT ? 1
                : Math.max(1, this.maximumAmount / 100);
        amount = Math.max(this.minimumAmount,
                Math.min(this.maximumAmount, defaultAmount));
    }

    Kind kind() { return kind; }
    String title() { return title; }
    String message() {
        return waitingForExternalClose() ? waitingMessage : message;
    }
    GameDialogSink.Icon icon() { return icon; }
    int preferredWidth() { return preferredWidth; }
    int seconds() { return seconds; }
    String negativeLabel() { return negativeLabel; }
    String positiveLabel() { return positiveLabel; }
    boolean showsNegative() { return !negativeLabel.isBlank(); }
    boolean showsPositive() { return !positiveLabel.isBlank(); }
    boolean hasAmount() { return kind == Kind.REBUY || kind == Kind.AUTO_CALL
            || kind == Kind.HAND_LIMIT; }
    boolean isAutoCall() { return kind == Kind.AUTO_CALL; }
    boolean isAutoAction() { return kind == Kind.AUTO_ACTION; }
    boolean isGameOver() { return kind == Kind.GAME_OVER; }
    boolean isRecovery() { return recovery; }
    boolean isRebuy() { return kind == Kind.REBUY; }
    boolean allowsDismissal() { return !isRebuy() || showsNegative(); }
    boolean isHandLimit() { return kind == Kind.HAND_LIMIT; }
    boolean isExternallyControlled() { return externallyControlled; }
    boolean waitingForExternalClose() {
        return deferCloseAfterDecision && result.isDone()
                && !externalCloseReleased;
    }
    boolean readyToClose() {
        return result.isDone() && (!deferCloseAfterDecision
                || externalCloseReleased);
    }
    boolean optionEnabled() { return optionEnabled; }
    boolean noLimit() { return noLimit; }
    int amount() { return amount; }
    String amountText() {
        return isAutoCall() ? autoCallAmountText : Integer.toString(amount);
    }
    int minimumAmount() { return minimumAmount; }
    int maximumAmount() { return maximumAmount; }
    CompletionStage<Boolean> result() { return result; }

    void message(String value) {
        message = plainText(Objects.requireNonNull(value, "value"));
    }

    void opened(float now) {
        if (Float.isNaN(openedAt)) openedAt = now;
    }

    boolean expired(float now) {
        float lifetime = timedLifetimeSeconds();
        return lifetime > 0f && !Float.isNaN(openedAt)
                && effectiveNow(now) - openedAt >= lifetime;
    }

    float remainingFraction(float now) {
        if (seconds <= 0 || Float.isNaN(openedAt)) return 0f;
        return Math.max(0f, Math.min(1f,
                1f - (effectiveNow(now) - openedAt) / seconds));
    }

    float elapsedSeconds(float now) {
        return Float.isNaN(openedAt) ? 0f
                : Math.max(0f, effectiveNow(now) - openedAt);
    }

    int remainingSeconds(float now) {
        if (seconds <= 0 || Float.isNaN(openedAt)) return 0;
        return Math.max(0, (int) Math.ceil(seconds
                - (effectiveNow(now) - openedAt)));
    }

    void setTimerPaused(float now, boolean paused) {
        if (timedLifetimeSeconds() <= 0f || Float.isNaN(openedAt)) return;
        if (paused) {
            if (Float.isNaN(timerPausedAt)) timerPausedAt = now;
        } else if (!Float.isNaN(timerPausedAt)) {
            openedAt += Math.max(0f, now - timerPausedAt);
            timerPausedAt = Float.NaN;
        }
    }

    private float effectiveNow(float now) {
        return Float.isNaN(timerPausedAt) ? now : timerPausedAt;
    }

    private float timedLifetimeSeconds() {
        return seconds > 0 ? seconds : autoDismissSeconds;
    }

    void changeAmount(int direction) {
        if (waitingForExternalClose() || !hasAmount() || direction == 0
                || (isAutoCall() && (!optionEnabled || noLimit))
                || (isHandLimit() && noLimit)) return;
        if (isAutoCall()) {
            BigDecimal next = parsedAutoCallText()
                    .orElse(autoCallCommittedAmount)
                    .add(AUTO_CALL_STEP.multiply(BigDecimal.valueOf(direction)))
                    .max(AUTO_CALL_STEP).setScale(2, RoundingMode.HALF_UP);
            autoCallCommittedAmount = next;
            autoCallAmountText = decimalText(next);
            amount = centsForLegacyAmount(next);
        } else {
            long next = (long) amount + (long) amountStep * direction;
            amount = (int) Math.max(minimumAmount,
                    Math.min(maximumAmount, next));
        }
    }

    /**
     * Swing leaves the spinner editor live while the dialog is open.  GDX
     * keeps the same contract: comma and dot are accepted, incomplete input
     * may remain visible while editing, and acceptance falls back to the last
     * valid spinner value before applying the 0.05 minimum and two decimals.
     */
    boolean acceptsAutoCallAmountText(String value) {
        if (!isAutoCall() || value == null || value.length() > 32) return false;
        return value.matches("[+-]?(?:[0-9]{0,30})(?:[.,][0-9]{0,30})?");
    }

    void autoCallAmountText(String value) {
        if (!acceptsAutoCallAmountText(value)) return;
        autoCallAmountText = value;
        parsedAutoCallText().ifPresent(valid -> {
            autoCallCommittedAmount = valid;
            amount = centsForLegacyAmount(valid);
        });
    }

    boolean autoCallAmountEditable() {
        return isAutoCall() && optionEnabled && !noLimit;
    }

    double autoCallAmount() {
        if (!isAutoCall()) return amount;
        if (noLimit) return 0d;
        return parsedAutoCallText().orElse(autoCallCommittedAmount)
                .max(AUTO_CALL_STEP).setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    void toggleOptionEnabled() {
        if (isAutoCall()) optionEnabled = !optionEnabled;
    }

    void toggleNoLimit() {
        if ((isAutoCall() && optionEnabled) || isHandLimit()) {
            noLimit = !noLimit;
        }
    }

    void timeout() { result.complete(timeoutAccepts); }
    void accept() { result.complete(true); }
    void dismiss() { result.complete(false); }
    boolean complete() { return result.isDone(); }

    void deferCloseAfterDecision(String message) {
        deferCloseAfterDecision = true;
        waitingMessage = plainText(Objects.requireNonNull(message, "message"));
    }

    void releaseExternalClose() {
        externalCloseReleased = true;
        if (!result.isDone()) result.complete(false);
    }

    private java.util.Optional<BigDecimal> parsedAutoCallText() {
        if (!isAutoCall() || autoCallAmountText == null) {
            return java.util.Optional.empty();
        }
        try {
            String normalized = autoCallAmountText.trim().replace(',', '.');
            if (normalized.isEmpty() || normalized.equals("+")
                    || normalized.equals("-") || normalized.equals(".")
                    || normalized.equals("+.") || normalized.equals("-.")) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }

    private static int centsForLegacyAmount(BigDecimal value) {
        BigDecimal cents = value.max(AUTO_CALL_STEP)
                .multiply(BigDecimal.valueOf(100L))
                .setScale(0, RoundingMode.HALF_UP);
        return cents.min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
    }

    private static String decimalText(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    static String plainText(String value) {
        return value.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .strip();
    }

    private static String defaultTitle(Kind kind) {
        return switch (kind) {
            case ERROR -> "ERROR";
            case INFO -> "INFORMACIÓN";
            case CONFIRM -> "CONFIRMACIÓN";
            case TIMED_WARNING -> "AVISO";
            case GAME_OVER -> "GAME OVER";
            case AUTO_ACTION -> "MODO AUTO";
            case REBUY -> "RECOMPRAR";
            case AUTO_CALL -> "AUTO IGUALAR";
            case HAND_LIMIT -> "L\u00cdMITE DE MANOS";
        };
    }

    private static String tr(GameText text, String key, String fallback) {
        Objects.requireNonNull(text, "text");
        String translated = text.translate(key);
        return key.equals(translated) ? fallback : translated;
    }
}
