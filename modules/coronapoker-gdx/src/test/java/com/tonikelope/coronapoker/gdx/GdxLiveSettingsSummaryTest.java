package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLiveSettingsSummaryTest {

    private static final GdxGameText ES = new GdxGameText("es");
    private static final GdxGameText EN = new GdxGameText("en");

    @Test
    void exposesSwingEquivalentFixedSessionTimingAndPurchaseInformation() {
        GameConfigCodecV1.Configuration configuration = configuration(
                true, true, 3, 1, true);

        assertEquals(List.of(
                "TIEMPO DE PENSAR  \u00b7  40 S",
                "TIEMPO DE SHOWDOWN  \u00b7  10 S"),
                GdxLiveSettingsSummary.timingLabels(configuration, ES));
        assertEquals(List.of(
                "COMPRA INICIAL  \u00b7  10",
                "BUY-IN  \u00b7  FIJO",
                "RANGO DE COMPRA  \u00b7  10 \u2013 100 BB",
                "RECOMPRA  \u00b7  ACTIVADA",
                "L\u00cdMITE POR JUGADOR  \u00b7  3",
                "TOPE DE RECOMPRA  \u00b7  STACK M\u00c1S ALTO"),
                GdxLiveSettingsSummary.purchaseLabels(configuration, ES));
    }

    @Test
    void labelsDisabledUnlimitedAndVariableVariantsWithoutInventingValues() {
        GameConfigCodecV1.Configuration configuration = configuration(
                false, false, 0, 0, false);

        assertEquals("TIEMPO DE PENSAR  \u00b7  DESACTIVADO",
                GdxLiveSettingsSummary.timingLabels(configuration, ES).get(0));
        assertEquals("BUY-IN  \u00b7  VARIABLE",
                GdxLiveSettingsSummary.purchaseLabels(configuration, ES).get(1));
        assertEquals("RECOMPRA  \u00b7  DESACTIVADA",
                GdxLiveSettingsSummary.purchaseLabels(configuration, ES).get(3));
        assertEquals("L\u00cdMITE POR JUGADOR  \u00b7  SIN L\u00cdMITE",
                GdxLiveSettingsSummary.purchaseLabels(configuration, ES).get(4));
        assertEquals("TOPE DE RECOMPRA  \u00b7  BUY-IN",
                GdxLiveSettingsSummary.purchaseLabels(configuration, ES).get(5));
    }

    @Test
    void visibleLabelsNeverContainMojibakeMarkers() {
        List<String> labels = new ArrayList<>();
        labels.add(GdxLiveSettingsSummary.purchaseHeading(ES));
        labels.addAll(GdxLiveSettingsSummary.timingLabels(configuration(
                true, true, 3, 1, true), ES));
        labels.addAll(GdxLiveSettingsSummary.purchaseLabels(configuration(
                true, true, 3, 1, true), ES));
        labels.addAll(GdxLiveSettingsSummary.unavailablePurchaseLabels(ES));

        for (String label : labels) {
            assertFalse(label.contains("\u00c2") || label.contains("\u00c3")
                    || label.contains("\ufffd"), label);
        }
    }

    @Test
    void purchaseSectionIsExplicitlyReadOnlyLikeSwing() {
        assertEquals("FIJADO AL CREAR LA TIMBA \u00b7 SOLO LECTURA",
                GdxLiveSettingsSummary.purchaseHeading(ES));
    }

    @Test
    void rabbitHuntingUsesTheSameFourLevelsAsSwingAndNewGame() {
        assertEquals("DESACTIVADO",
                GdxLiveSettingsSummary.rabbitHuntingLabel(0, ES));
        assertEquals("GRATIS",
                GdxLiveSettingsSummary.rabbitHuntingLabel(1, ES));
        assertEquals("GRATIS \u00bb CIEGA PEQUE\u00d1A",
                GdxLiveSettingsSummary.rabbitHuntingLabel(2, ES));
        assertEquals("GRATIS \u00bb CIEGA PEQUE\u00d1A \u00bb CIEGA GRANDE",
                GdxLiveSettingsSummary.rabbitHuntingLabel(3, ES));
        assertEquals("NO DISPONIBLE",
                GdxLiveSettingsSummary.rabbitHuntingLabel(-1, ES));
    }

    @Test
    void liveSummariesFollowTheSelectedLanguage() {
        GameConfigCodecV1.Configuration configuration = configuration(
                true, true, 3, 1, true);

        assertEquals("THINK TIME  ·  40 S",
                GdxLiveSettingsSummary.timingLabels(configuration, EN).get(0));
        assertEquals("BUY-IN RANGE  ·  10 – 100 BB",
                GdxLiveSettingsSummary.purchaseLabels(configuration, EN).get(2));
        assertEquals("FREE » SMALL BLIND",
                GdxLiveSettingsSummary.rabbitHuntingLabel(2, EN));
        assertEquals("SET WHEN THE GAME WAS CREATED · READ ONLY",
                GdxLiveSettingsSummary.purchaseHeading(EN));
    }

    private static GameConfigCodecV1.Configuration configuration(
            boolean thinkEnabled, boolean rebuy, int rebuyLimit,
            int rebuyCapPolicy, boolean fixedBuyin) {
        return new GameConfigCodecV1.Configuration(
                10, 0.10d, 0.20d, 0, 1, false, "settings-test",
                rebuy, 100, 0d, rebuyLimit, true, fixedBuyin, 10, 100,
                rebuyCapPolicy, false, false, true, false, 0, 40,
                thinkEnabled, 10, false, List.of());
    }
}
