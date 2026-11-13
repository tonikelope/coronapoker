package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tonikelope.coronapoker.core.AnteMode;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class GdxLobbyGameSummaryTest {

    @Test
    void summaryShowsTheRulesReceivedFromTheHost() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindStructure("Test", List.of(
                new NewGameTableDraft.BlindLevel(1, 2),
                new NewGameTableDraft.BlindLevel(2, 4),
                new NewGameTableDraft.BlindLevel(4, 8)), 0);
        draft.setIncreaseBlinds(true);
        draft.setBlindIncreaseType(NewGameTableDraft.BlindIncreaseType.HANDS);
        draft.setBlindInterval(12);
        draft.setBlindCap(true);
        draft.setBlindCapRaises(1);
        draft.setAnte(true);
        draft.setAnteMode(AnteMode.BIG_BLIND);
        draft.setStraddle(true);
        draft.setFixedBuyin(false);
        draft.setRebuy(true);
        draft.setRebuyLimit(true);
        draft.setRebuyLimitCount(2);
        draft.setHandLimit(true);
        draft.setHandLimitCount(50);
        draft.setRabbitHunting(
                NewGameTableDraft.RabbitHunting.FREE_SMALL_AND_BIG_BLIND);
        draft.setRunItTwice(true);
        draft.setIwtsth(true);
        var received = NewGameTableDraft.Settings.parseWire(
                draft.snapshot().serializeForWire());

        List<String> labels = GdxLobbyGameSummary.rows(received,
                new GdxGameText("es")).stream()
                .map(GdxLobbyGameSummary.Row::label).toList();
        assertEquals(List.of("Buy-in", "Recompra", "Ciegas iniciales",
                "Subida de ciegas", "Tope de ciegas", "Rabbit hunting",
                "Run it twice", "IWTSTH", "Ante", "Straddle", "Manos"),
                labels);
        Map<String, String> summary = values(received, "es");
        assertEquals(GdxLobbyGameSummary.MAX_ROWS, summary.size());
        assertEquals("1.00 / 2.00", summary.get("Ciegas iniciales"));
        assertEquals("Cada 12 manos", summary.get("Subida de ciegas"));
        assertEquals("2.00 / 4.00", summary.get("Tope de ciegas"));
        assertEquals("2.00 / Ciega grande (BBA)", summary.get("Ante"));
        assertEquals("Opcional (2 BB)", summary.get("Straddle"));
        assertEquals(received.minBuyinBb() + " - " + received.maxBuyinBb()
                + " BB", summary.get("Buy-in"));
        assertEquals("Hasta 2 por jugador", summary.get("Recompra"));
        assertEquals("50", summary.get("Manos"));
        assertEquals("Gratis » SB » BB", summary.get("Rabbit hunting"));
        assertEquals("Sí", summary.get("Run it twice"));
        assertEquals("Sí", summary.get("IWTSTH"));
    }

    @Test
    void summaryKeepsBothDecimalsForFractionalBlindsAndAnte() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setAnte(true);
        draft.setAnteMode(AnteMode.BIG_BLIND);

        Map<String, String> summary = values(draft.snapshot(), "es");
        assertEquals("0.10 / 0.20", summary.get("Ciegas iniciales"));
        assertEquals("0.20 / Ciega grande (BBA)", summary.get("Ante"));
    }

    @Test
    void summaryDistinguishesFixedBlindsAndBothIncreaseUnitsInEnglish() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setRebuy(false);
        var settings = draft.snapshot();
        Map<String, String> summary = values(settings, "en");
        assertEquals("No", summary.get("Blind increases"));
        assertFalse(summary.containsKey("Blind cap"));
        assertEquals("No", summary.get("Ante"));
        assertEquals("No", summary.get("Straddle"));
        assertEquals(GdxChipAmountText.buyinLabel(settings.buyin(), "en"),
                summary.get("Buy-in"));
        assertEquals("No", summary.get("Rebuy"));
        assertEquals("No limit", summary.get("Hands"));
        assertEquals("Off", summary.get("Rabbit hunting"));

        draft.setIncreaseBlinds(true);
        draft.setBlindInterval(15);
        assertEquals("Every 15 min",
                values(draft.snapshot(), "en").get("Blind increases"));
        draft.setBlindIncreaseType(NewGameTableDraft.BlindIncreaseType.HANDS);
        draft.setBlindInterval(1);
        assertEquals("Every hand",
                values(draft.snapshot(), "en").get("Blind increases"));
    }

    private static Map<String, String> values(
            NewGameTableDraft.Settings settings, String language) {
        return GdxLobbyGameSummary.rows(settings, new GdxGameText(language))
                .stream().collect(Collectors.toMap(
                        GdxLobbyGameSummary.Row::label,
                        GdxLobbyGameSummary.Row::value));
    }
}
