/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;

/**
 * Headless card controller used by the canonical engine when no Swing card
 * component owns the slot. Renderers observe the same {@link CardState}.
 */
public final class CoreCardController implements GameCardController {

    private enum RabbitState { OFF, FACE_DOWN, FACE_UP }

    private final CardState state;
    private volatile RabbitState rabbit = RabbitState.OFF;

    public CoreCardController() {
        this(new CardState());
    }

    public CoreCardController(CardState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public CardState getState() {
        return state;
    }

    @Override
    public boolean isIniciadaConValor() {
        return state.initialized() && state.code() != null;
    }

    @Override
    public boolean isVisible_card() {
        return state.visible();
    }

    @Override
    public void setVisibleCard(boolean visible) {
        state.setVisible(visible);
    }

    @Override
    public void iniciarCarta() {
        iniciarCarta(true);
    }

    @Override
    public void iniciarCarta(boolean visible) {
        state.initializeUnknown(visible);
    }

    @Override
    public void resetearCarta() {
        resetearCarta(true);
    }

    @Override
    public void resetearCarta(boolean visible) {
        state.reset(visible, true);
        rabbit = RabbitState.OFF;
    }

    @Override
    public void actualizarConValorNumerico(int value) {
        if (value >= 1 && value <= 52) {
            state.updateCode(CardCode.fromOneBased(value));
        }
    }

    @Override
    public void iniciarConValorNumerico(int value) {
        if (value >= 1 && value <= 52) {
            state.initialize(CardCode.fromOneBased(value), false);
        }
    }

    @Override
    public int getCartaComoEntero() {
        CardCode code = state.code();
        return code == null ? -1 : code.oneBased();
    }

    @Override
    public int getCardIndex() {
        CardCode code = state.code();
        return code == null ? -1 : code.index();
    }

    @Override
    public int getValorNumerico() {
        return getValorNumerico(false);
    }

    @Override
    public int getValorNumerico(boolean aceLow) {
        CardCode code = state.code();
        if (code == null) return -1;
        return aceLow ? code.rank().aceLowValue() : code.rank().aceHighValue();
    }

    @Override
    public String getValor() {
        CardCode code = state.code();
        return code == null ? "" : code.rank().wire();
    }

    @Override
    public String getPalo() {
        CardCode code = state.code();
        return code == null ? "" : code.suit().wire();
    }

    @Override
    public boolean isTapada() {
        return !state.faceUp();
    }

    @Override
    public boolean isDesenfocada() {
        return state.disabled();
    }

    @Override
    public void destapar() {
        destapar(true);
    }

    @Override
    public void destapar(boolean sound) {
        if (isIniciadaConValor() && isTapada()) {
            state.setFaceUp(true);
            state.setVisible(true);
        }
    }

    @Override
    public void tapar() {
        state.setFaceUp(false);
    }

    @Override
    public void desenfocar() {
        if (state.initialized()) state.setDisabled(true);
    }

    @Override
    public void enfocar() {
        if (state.initialized()) state.setDisabled(false);
    }

    @Override
    public boolean isRabbitTapada() {
        return rabbit == RabbitState.FACE_DOWN;
    }

    @Override
    public void taparRabbit() {
        rabbit = RabbitState.FACE_DOWN;
    }

    @Override
    public void destaparRabbit() {
        if (rabbit == RabbitState.FACE_DOWN) {
            rabbit = RabbitState.FACE_UP;
            state.setFaceUp(true);
        }
    }

    @Override
    public String toShortString() {
        CardCode code = state.code();
        return code == null ? "_" : code.shortCode();
    }

    @Override
    public String toString() {
        return GameCards.display(this);
    }
}
