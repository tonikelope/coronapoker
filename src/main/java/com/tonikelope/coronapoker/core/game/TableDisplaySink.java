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

/** Immediate, semantic table-display operations retained by the classic flow. */
public interface TableDisplaySink {

    record PayoutTransfer(String nickname, double stackBefore,
            double amount) {
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
        };
    }
}
