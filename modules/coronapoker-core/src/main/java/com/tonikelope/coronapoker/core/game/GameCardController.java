/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Mutable card slot controlled by the canonical game, never by a renderer. */
public interface GameCardController {

    CardState getState();

    boolean isIniciadaConValor();

    boolean isVisible_card();

    void setVisibleCard(boolean visible);

    void iniciarCarta();

    void iniciarCarta(boolean visible);

    void resetearCarta();

    void resetearCarta(boolean visible);

    void actualizarConValorNumerico(int value);

    void iniciarConValorNumerico(int value);

    int getCartaComoEntero();

    int getCardIndex();

    int getValorNumerico();

    int getValorNumerico(boolean aceLow);

    String getValor();

    String getPalo();

    boolean isTapada();

    boolean isDesenfocada();

    /** Whether compact presentation may crop this card to its top half. */
    default boolean isCompactable() {
        return true;
    }

    void destapar();

    void destapar(boolean sound);

    void tapar();

    void desenfocar();

    void enfocar();

    boolean isRabbitTapada();

    void taparRabbit();

    void destaparRabbit();

    String toShortString();
}
