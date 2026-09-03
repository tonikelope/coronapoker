/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GamePresentationSettings;

/** Live adapter for the classic mutable Swing preferences. */
final class SwingGamePresentationSettings implements GamePresentationSettings {
    @Override public String language() { return GameFrame.LANGUAGE; }
    @Override public String defaultLanguage() { return GameFrame.DEFAULT_LANGUAGE; }
    @Override public String deck() { return GameFrame.BARAJA; }
    @Override public boolean testMode() { return GameFrame.TEST_MODE; }
    @Override public boolean sillySounds() { return GameFrame.SONIDOS_CHORRA; }
    @Override public boolean ambientMusic() { return GameFrame.MUSICA_AMBIENTAL; }
    @Override public boolean showCallCost() { return GameFrame.MOSTRAR_COSTE_IGUALAR; }
    @Override public boolean autoActionButtons() { return GameFrame.AUTO_ACTION_BUTTONS; }
    @Override public boolean autoActionPersist() { return GameFrame.AUTO_ACTION_PERSIST; }
    @Override public boolean autoRebuyOnBroke() { return GameFrame.AUTO_REBUY_ON_BROKE; }
    @Override public boolean autoFullscreen() { return GameFrame.AUTO_FULLSCREEN; }
    @Override public boolean cinematics() { return GameFrame.cinematicasOn(); }
    @Override public boolean allInCinematics() { return GameFrame.cinematicasAllinOn(); }
    @Override public boolean gameOverCinematics() { return GameFrame.cinematicasGameOverOn(); }
    @Override public boolean blindDealerAnimation() { return GameFrame.ciegasDealerAnimOn(); }
    @Override public boolean betAnimation() { return GameFrame.apuestasAnimOn(); }
    @Override public boolean counterAnimation() { return GameFrame.contadoresAnimOn(); }
    @Override public boolean shuffleAnimation() { return GameFrame.barajadoAnimOn(); }
    @Override public boolean dealAnimation() { return GameFrame.repartoAnimOn(); }
    @Override public boolean flipAnimation() { return GameFrame.destapeAnimOn(); }
    @Override public boolean swapAnimation() { return GameFrame.swapAnimOn(); }
    @Override public boolean callSound() { return GameFrame.igualarSonidoOn(); }
    @Override public boolean betSound() { return GameFrame.apuestaSonidoOn(); }
    @Override public boolean blindSound() { return GameFrame.ciegasSonidoOn(); }
    @Override public boolean shuffleSound() { return GameFrame.barajadoSonidoOn(); }
    @Override public boolean dealSound() { return GameFrame.repartoSonidoOn(); }
    @Override public boolean flipSound() { return GameFrame.destapeSonidoOn(); }
    @Override public boolean cashSound() { return GameFrame.cajaSonidoOn(); }
    @Override public boolean iwtsthSound() { return GameFrame.iwtsthSonidoOn(); }
    @Override public boolean startSound() { return GameFrame.inicioSonidoOn(); }
    @Override public boolean warningSound() { return GameFrame.avisoSonidoOn(); }
    @Override public boolean errorSound() { return GameFrame.errorSonidoOn(); }
    @Override public String initialStackFillSound() { return GameFrame.initialStackFillSound(); }
    @Override public String cashRegisterSound() { return GameFrame.cashRegisterSound(); }
    @Override public int compactView() { return GameFrame.VISTA_COMPACTA; }
    @Override public int dealSpeed() { return GameFrame.REPARTO_VELOCIDAD; }
    @Override public int swapAnimationDuration() { return GameFrame.SWAP_ANIM_DURATION; }
    @Override public boolean swapAnimationArc() { return GameFrame.SWAP_ANIM_ARC; }
    @Override public float zoomFactor() { return 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP; }
}
