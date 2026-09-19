/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.Objects;

/**
 * Applies only the live-table fields edited by the GDX settings dialog to the
 * latest authoritative configuration. This prevents an unrelated settings
 * save from restoring stale blinds or rules while the table keeps running.
 */
final class GdxLiveSettingsMerge {

    private GdxLiveSettingsMerge() {
    }

    static GameConfigCodecV1.Configuration merge(
            GameConfigCodecV1.Configuration opened,
            GameConfigCodecV1.Configuration edited,
            GameConfigCodecV1.Configuration current) {
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(edited, "edited");
        Objects.requireNonNull(current, "current");

        GameConfigCodecV1.Configuration merged = current;
        if (edited.hands() != opened.hands()) {
            merged = merged.withHands(edited.hands());
        }
        if (edited.iwtsth() != opened.iwtsth()) {
            merged = merged.withIwtsth(edited.iwtsth());
        }
        if (edited.runItTwice() != opened.runItTwice()) {
            merged = merged.withRunItTwice(edited.runItTwice());
        }
        if (edited.rabbitHunting() != opened.rabbitHunting()) {
            merged = merged.withRabbitHunting(edited.rabbitHunting());
        }
        if (edited.botRebuy() != opened.botRebuy()) {
            merged = merged.withBotRebuy(edited.botRebuy());
        }
        if (edited.botBalanceToHumans() != opened.botBalanceToHumans()) {
            merged = merged.withBotBalanceToHumans(
                    edited.botBalanceToHumans());
        }
        if (blindSettingsChanged(opened, edited)) {
            merged = merged.withBlindSettings(edited.smallBlind(),
                    edited.bigBlind(), edited.blindsDouble(),
                    edited.blindsDoubleType(), edited.blindCap(),
                    edited.blindStructure());
        }
        if (edited.ante() != opened.ante()) {
            merged = merged.withAnte(edited.ante());
        }
        if (edited.straddle() != opened.straddle()) {
            merged = merged.withStraddle(edited.straddle());
        }
        return merged;
    }

    private static boolean blindSettingsChanged(
            GameConfigCodecV1.Configuration opened,
            GameConfigCodecV1.Configuration edited) {
        return Double.compare(opened.smallBlind(), edited.smallBlind()) != 0
                || Double.compare(opened.bigBlind(), edited.bigBlind()) != 0
                || opened.blindsDouble() != edited.blindsDouble()
                || opened.blindsDoubleType() != edited.blindsDoubleType()
                || Double.compare(opened.blindCap(), edited.blindCap()) != 0
                || !opened.blindStructure().equals(edited.blindStructure());
    }
}
