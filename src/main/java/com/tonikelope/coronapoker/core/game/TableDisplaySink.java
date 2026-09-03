/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Immediate, semantic table-display operations retained by the classic flow. */
public interface TableDisplaySink {

    interface OverlayHandle {

        void remove();

        static OverlayHandle noop() {
            return () -> {
            };
        }
    }

    interface PreparedCardFlip {

        boolean matches(String cardCode, boolean topHalf, float zoomFactor);

        int frameCount();

        long totalMillis();
    }

    enum FlipSound {
        STANDARD,
        LOCAL
    }

    record PayoutTransfer(String nickname, double stackBefore,
            double amount) {
    }

    record StackTransfer(String nickname, double from, double to) {
    }

    enum PotStyle {
        DEFAULT,
        WIN,
        LOSS,
        SIDE_POT
    }

    void showPot(double amount, Double mainPotProfit);

    void showPotText(String text);

    void hideStreetBets();

    void showStreetBets(double amount);

    void showBlinds(double smallBlind, double bigBlind);

    void showHandNumber(int handNumber);

    void setPotCentered(boolean centered);

    void setPotStyle(PotStyle style);

    void showCallCost(String text);

    void hideCallCost();

    void resetForNewHand();

    void setHandAndStreetBetVisible(boolean visible);

    void showDecryptingStreet(String text);

    void finishDecryptingStreet();

    void prepareRunItTwiceSideB();

    void requestHandLimitAction();

    void repaintCommunity();

    void refresh();

    void setLightsSuppressed(boolean suppressed);

    void downgradeAndRefreshSeats();

    void showShuffleTurn(String nickname);

    void hideShuffleTurn();

    void resetPlayer(String nickname);

    void refreshPlayerPosition(String nickname);

    void refreshPositionChip(String nickname);

    void showPlayerCards(String nickname, String handName);

    void showWinner(String nickname, String message);

    void showLoser(String nickname, String message);

    void preparePlayerReveal(String nickname);

    void revealPlayerCards(String nickname, boolean sound);

    void showNeutralHand(String nickname, String handName);

    void preparePositionRotation(
            List<TableVisualEvent.PositionTransfer> transfers);

    CompletionStage<Void> animatePositionRotation(
            List<TableVisualEvent.PositionTransfer> transfers,
            long durationMillis, Runnable onLand);

    void preparePotTarget();

    void launchChipToPot(String nickname, int shrinkMillis, Runnable onLand);

    CompletionStage<Void> potFlashCompletion();

    CompletionStage<Void> animateShowdownPayout(
            List<PayoutTransfer> payouts, int shrinkMillis,
            int postAnimationPauseMillis);

    void animateStackFill(List<StackTransfer> transfers,
            long durationMillis, Runnable onComplete);

    void playShuffleLoop(boolean animationEnabled, boolean soundEnabled,
            BooleanSupplier keepRunning, Runnable onComplete);

    OverlayHandle addPositionChipOverlay(String nickname);

    void dealHoleCard(String nickname, int slot, int durationMillis,
            boolean soundEnabled, Runnable onLand);

    void dealCommunityCard(int slot, int durationMillis,
            boolean soundEnabled, Runnable onLand);

    void swapHoleCards(String nickname, int durationMillis, boolean arc,
            Runnable onSwapApply);

    PreparedCardFlip prepareCardFlip(String cardCode, boolean topHalf,
            float zoomFactor);

    void playHoleCardFlips(String nickname, int[] slots,
            List<PreparedCardFlip> flips, int delayEndMillis,
            FlipSound sound);

    void playCommunityCardFlip(int slot, PreparedCardFlip flip,
            int delayEndMillis, FlipSound sound);

    static TableDisplaySink noop() {
        return new TableDisplaySink() {
            @Override
            public void showPot(double amount, Double mainPotProfit) {
            }

            @Override
            public void showPotText(String text) {
            }

            @Override
            public void hideStreetBets() {
            }

            @Override
            public void showStreetBets(double amount) {
            }

            @Override
            public void showBlinds(double smallBlind, double bigBlind) {
            }

            @Override
            public void showHandNumber(int handNumber) {
            }

            @Override
            public void setPotCentered(boolean centered) {
            }

            @Override
            public void setPotStyle(PotStyle style) {
            }

            @Override
            public void showCallCost(String text) {
            }

            @Override
            public void hideCallCost() {
            }

            @Override
            public void resetForNewHand() {
            }

            @Override
            public void setHandAndStreetBetVisible(boolean visible) {
            }

            @Override
            public void showDecryptingStreet(String text) {
            }

            @Override
            public void finishDecryptingStreet() {
            }

            @Override
            public void prepareRunItTwiceSideB() {
            }

            @Override
            public void requestHandLimitAction() {
            }

            @Override
            public void repaintCommunity() {
            }

            @Override
            public void refresh() {
            }

            @Override
            public void setLightsSuppressed(boolean suppressed) {
            }

            @Override
            public void downgradeAndRefreshSeats() {
            }

            @Override
            public void showShuffleTurn(String nickname) {
            }

            @Override
            public void hideShuffleTurn() {
            }

            @Override
            public void resetPlayer(String nickname) {
            }

            @Override
            public void refreshPlayerPosition(String nickname) {
            }

            @Override
            public void refreshPositionChip(String nickname) {
            }

            @Override
            public void showPlayerCards(String nickname, String handName) {
            }

            @Override
            public void showWinner(String nickname, String message) {
            }

            @Override
            public void showLoser(String nickname, String message) {
            }

            @Override
            public void preparePlayerReveal(String nickname) {
            }

            @Override
            public void revealPlayerCards(String nickname, boolean sound) {
            }

            @Override
            public void showNeutralHand(String nickname, String handName) {
            }

            @Override
            public void preparePositionRotation(
                    List<TableVisualEvent.PositionTransfer> transfers) {
            }

            @Override
            public CompletionStage<Void> animatePositionRotation(
                    List<TableVisualEvent.PositionTransfer> transfers,
                    long durationMillis, Runnable onLand) {
                onLand.run();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void preparePotTarget() {
            }

            @Override
            public void launchChipToPot(String nickname, int shrinkMillis,
                    Runnable onLand) {
                onLand.run();
            }

            @Override
            public CompletionStage<Void> potFlashCompletion() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> animateShowdownPayout(
                    List<PayoutTransfer> payouts, int shrinkMillis,
                    int postAnimationPauseMillis) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void animateStackFill(List<StackTransfer> transfers,
                    long durationMillis, Runnable onComplete) {
                onComplete.run();
            }

            @Override
            public void playShuffleLoop(boolean animationEnabled,
                    boolean soundEnabled, BooleanSupplier keepRunning,
                    Runnable onComplete) {
                onComplete.run();
            }

            @Override
            public OverlayHandle addPositionChipOverlay(String nickname) {
                return OverlayHandle.noop();
            }

            @Override
            public void dealHoleCard(String nickname, int slot,
                    int durationMillis, boolean soundEnabled, Runnable onLand) {
                onLand.run();
            }

            @Override
            public void dealCommunityCard(int slot, int durationMillis,
                    boolean soundEnabled, Runnable onLand) {
                onLand.run();
            }

            @Override
            public void swapHoleCards(String nickname, int durationMillis,
                    boolean arc, Runnable onSwapApply) {
                onSwapApply.run();
            }

            @Override
            public PreparedCardFlip prepareCardFlip(String cardCode,
                    boolean topHalf, float zoomFactor) {
                return null;
            }

            @Override
            public void playHoleCardFlips(String nickname, int[] slots,
                    List<PreparedCardFlip> flips, int delayEndMillis,
                    FlipSound sound) {
            }

            @Override
            public void playCommunityCardFlip(int slot,
                    PreparedCardFlip flip, int delayEndMillis,
                    FlipSound sound) {
            }
        };
    }
}
