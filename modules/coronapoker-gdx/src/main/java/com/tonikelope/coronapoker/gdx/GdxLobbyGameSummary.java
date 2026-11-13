/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.AnteMode;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.util.ArrayList;
import java.util.List;

final class GdxLobbyGameSummary {

    static final int MAX_ROWS = 11;
    private static final String GAME_ROW = "gdx.settings.game.row.";

    private GdxLobbyGameSummary() {
    }

    static List<Row> rows(NewGameTableDraft.Settings settings,
            GdxGameText text) {
        List<Row> rows = new ArrayList<>(MAX_ROWS);
        rows.add(new Row(text.translate("gdx.settings.game.summary.label.buyin"),
                settings.fixedBuyin()
                        ? GdxChipAmountText.buyinLabel(settings.buyin(),
                                text.language())
                        : settings.minBuyinBb() + " - " + settings.maxBuyinBb()
                                + " BB", settings.fixedBuyin()));
        String rebuy = settings.rebuy() && settings.rebuyLimit()
                ? text.translate("gdx.lobby.rebuy_limit",
                        settings.rebuyLimitCount())
                : toggle(settings.rebuy(), text);
        rows.add(new Row(text.translate("gdx.settings.game.summary.label.rebuy"),
                rebuy));

        NewGameTableDraft.BlindLevel blind = settings.selectedBlindLevel();
        rows.add(new Row(text.translate(GAME_ROW + "initial_blinds"),
                blindLabel(blind), true));
        String interval = !settings.increaseBlinds() ? toggle(false, text)
                : text.translate(settings.blindIncreaseType()
                        == NewGameTableDraft.BlindIncreaseType.MINUTES
                                ? "gdx.lobby.blind_increase_minutes"
                                : "gdx.lobby.blind_increase_hands",
                        settings.blindInterval());
        rows.add(new Row(text.translate("gdx.lobby.blind_increase"), interval));
        if (settings.increaseBlinds() && settings.blindCap()) {
            int capIndex = Math.min(settings.blindLevels().size() - 1,
                    settings.blindLevelIndex() + settings.blindCapRaises());
            rows.add(new Row(text.translate(GAME_ROW + "blind_cap"),
                    blindLabel(settings.blindLevels().get(capIndex)), true));
        }

        String rabbitMode = switch (settings.rabbitHunting()) {
            case OFF -> text.translate("menu.off");
            case FREE -> text.translate("menu.free");
            case FREE_SMALL_BLIND -> text.translate("menu.free") + " » SB";
            case FREE_SMALL_AND_BIG_BLIND ->
                text.translate("menu.free") + " » SB » BB";
        };
        rows.add(new Row(text.translate(GAME_ROW + "rabbit_hunting"),
                rabbitMode));
        rows.add(new Row(text.translate(GAME_ROW + "run_it_twice"),
                toggle(settings.runItTwice(), text)));
        rows.add(new Row(text.translate(GAME_ROW + "iwtsth"),
                toggle(settings.iwtsth(), text)));

        AnteMode mode = settings.anteMode();
        rows.add(new Row(text.translate(GAME_ROW + "ante"),
                settings.ante()
                        ? GdxChipAmountText.format(mode.amount(blind.bigBlind()), 2)
                                + " / " + text.translate(mode.labelKey())
                        : toggle(false, text), settings.ante()));
        rows.add(new Row(text.translate(GAME_ROW + "straddle"),
                settings.straddle() ? text.translate("gdx.lobby.straddle")
                        : toggle(false, text)));
        rows.add(new Row(text.translate("gdx.settings.game.value.hands"),
                settings.handLimit() ? Integer.toString(settings.handLimitCount())
                        : text.translate("gdx.lobby.no_hand_limit")));
        return rows;
    }

    private static String blindLabel(NewGameTableDraft.BlindLevel level) {
        return GdxChipAmountText.format(level.smallBlind(), 2) + " / "
                + GdxChipAmountText.format(level.bigBlind(), 2);
    }

    private static String toggle(boolean enabled, GdxGameText text) {
        return text.translate(enabled ? "ui.option_pane.yes"
                : "ui.option_pane.no");
    }

    record Row(String label, String value, boolean monetary) {
        Row(String label, String value) {
            this(label, value, false);
        }

        String displayValue(GdxChipAmountText.ZeroMode mode) {
            return monetary ? mode.display(value) : value;
        }
    }
}
