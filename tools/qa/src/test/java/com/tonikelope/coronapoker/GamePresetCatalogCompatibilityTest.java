package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.GamePresetCatalog;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GamePresetCatalogCompatibilityTest {

    @Test
    void swingAndNeutralFrontendsUseTheSameStoredCatalogue() {
        Properties properties = new Properties();
        GamePreset swingPreset = new GamePreset("Cash",
                new GamePreset.Settings().serialize());

        GamePreset.writeTo(properties, List.of(swingPreset));

        GamePresetCatalog.Entry neutral = GamePresetCatalog.readFrom(properties)
                .get("Cash");
        assertEquals(swingPreset.getSettings(), neutral.settings());
        assertEquals(swingPreset.getSettings(),
                GamePreset.readFrom(properties).get("Cash").getSettings());
    }
}
