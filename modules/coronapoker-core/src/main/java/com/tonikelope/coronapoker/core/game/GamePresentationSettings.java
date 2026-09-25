/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/**
 * Live, frontend-owned presentation preferences consumed by the canonical
 * controller. Implementations may reflect mutable user settings; game rules
 * must remain in {@link GameConfigCodecV1.Configuration} instead.
 */
public interface GamePresentationSettings {

    String language();
    String defaultLanguage();
    String deck();
    /** Selects the next installed presentation deck and returns its identifier. */
    default String selectNextDeck() { return deck(); }
    /** Selects one concrete installed presentation deck and returns its identifier. */
    default String selectDeck(String deck) { return this.deck(); }
    boolean testMode();
    boolean sillySounds();
    boolean ambientMusic();
    boolean showCallCost();
    boolean autoActionButtons();
    boolean autoActionPersist();
    boolean autoRebuyOnBroke();
    boolean autoFullscreen();
    boolean cinematics();
    boolean allInCinematics();
    boolean gameOverCinematics();
    boolean blindDealerAnimation();
    boolean betAnimation();
    boolean counterAnimation();
    boolean shuffleAnimation();
    boolean dealAnimation();
    boolean flipAnimation();
    boolean swapAnimation();
    boolean callSound();
    boolean betSound();
    boolean blindSound();
    boolean shuffleSound();
    boolean dealSound();
    boolean flipSound();
    boolean cashSound();
    boolean iwtsthSound();
    boolean startSound();
    boolean warningSound();
    /** Hurry-up and timeout horn preference for the local turn clock. */
    default boolean turnWarningSound() { return warningSound(); }
    boolean errorSound();
    String initialStackFillSound();
    String cashRegisterSound();
    int compactView();
    int dealSpeed();
    int swapAnimationDuration();
    boolean swapAnimationArc();
    float zoomFactor();
    float dialogZoomFactor();

    static GamePresentationSettings defaults() {
        return DefaultSettings.INSTANCE;
    }

    final class DefaultSettings implements GamePresentationSettings {
        private static final DefaultSettings INSTANCE = new DefaultSettings();
        private DefaultSettings() { }
        @Override public String language() { return "en"; }
        @Override public String defaultLanguage() { return "en"; }
        @Override public String deck() { return "coronapoker"; }
        @Override public boolean testMode() { return false; }
        @Override public boolean sillySounds() { return false; }
        @Override public boolean ambientMusic() { return true; }
        @Override public boolean showCallCost() { return true; }
        @Override public boolean autoActionButtons() { return false; }
        @Override public boolean autoActionPersist() { return false; }
        @Override public boolean autoRebuyOnBroke() { return false; }
        @Override public boolean autoFullscreen() { return false; }
        @Override public boolean cinematics() { return true; }
        @Override public boolean allInCinematics() { return true; }
        @Override public boolean gameOverCinematics() { return true; }
        @Override public boolean blindDealerAnimation() { return true; }
        @Override public boolean betAnimation() { return true; }
        @Override public boolean counterAnimation() { return true; }
        @Override public boolean shuffleAnimation() { return true; }
        @Override public boolean dealAnimation() { return true; }
        @Override public boolean flipAnimation() { return true; }
        @Override public boolean swapAnimation() { return true; }
        @Override public boolean callSound() { return true; }
        @Override public boolean betSound() { return true; }
        @Override public boolean blindSound() { return true; }
        @Override public boolean shuffleSound() { return true; }
        @Override public boolean dealSound() { return true; }
        @Override public boolean flipSound() { return true; }
        @Override public boolean cashSound() { return true; }
        @Override public boolean iwtsthSound() { return true; }
        @Override public boolean startSound() { return true; }
        @Override public boolean warningSound() { return true; }
        @Override public boolean errorSound() { return true; }
        @Override public String initialStackFillSound() { return "misc/balance_count.wav"; }
        @Override public String cashRegisterSound() { return "misc/cash_register.wav"; }
        @Override public int compactView() { return 0; }
        @Override public int dealSpeed() { return 100; }
        @Override public int swapAnimationDuration() { return 320; }
        @Override public boolean swapAnimationArc() { return false; }
        @Override public float zoomFactor() { return 1f; }
        @Override public float dialogZoomFactor() { return 1f; }
    }
}
