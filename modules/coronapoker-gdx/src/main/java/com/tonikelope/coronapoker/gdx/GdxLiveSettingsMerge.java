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
        merged = mergeBlinds(opened, edited, merged);
        if (edited.ante() != opened.ante()) {
            merged = merged.withAnte(edited.ante());
        }
        if (edited.anteMode() != opened.anteMode()) {
            merged = merged.withAnteMode(edited.anteMode());
        }
        if (edited.straddle() != opened.straddle()) {
            merged = merged.withStraddle(edited.straddle());
        }
        return merged;
    }

    private static GameConfigCodecV1.Configuration mergeBlinds(
            GameConfigCodecV1.Configuration opened,
            GameConfigCodecV1.Configuration edited,
            GameConfigCodecV1.Configuration current) {
        boolean structureChanged = !opened.blindStructure().equals(edited.blindStructure());
        // Selecting a different ladder also selects a level on that ladder.
        boolean levelChanged = structureChanged
                || Double.compare(opened.smallBlind(), edited.smallBlind()) != 0
                || Double.compare(opened.bigBlind(), edited.bigBlind()) != 0;
        boolean intervalChanged = opened.blindsDouble() != edited.blindsDouble();
        boolean unitChanged = opened.blindsDoubleType() != edited.blindsDoubleType();
        boolean capChanged = Double.compare(opened.blindCap(), edited.blindCap()) != 0;
        if (!levelChanged && !intervalChanged && !unitChanged && !capChanged) return current;

        double smallBlind = levelChanged ? edited.smallBlind() : current.smallBlind();
        double bigBlind = levelChanged ? edited.bigBlind() : current.bigBlind();
        int interval = intervalChanged ? edited.blindsDouble() : current.blindsDouble();
        int unit = unitChanged ? edited.blindsDoubleType() : current.blindsDoubleType();
        double cap = capChanged ? edited.blindCap() : current.blindCap();
        if (cap > 0d) cap = Math.max(cap, bigBlind);
        return current.withBlindSettings(smallBlind, bigBlind, interval, unit, cap,
                structureChanged ? edited.blindStructure() : current.blindStructure());
    }
}
