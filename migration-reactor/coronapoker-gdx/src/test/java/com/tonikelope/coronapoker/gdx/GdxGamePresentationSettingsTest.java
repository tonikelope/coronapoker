package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxGamePresentationSettingsTest {

    @Test
    void nativeGameplayAnimationsCannotBeDisabledByLegacySwingPreferences(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("animaciones", "false");
        preferences.properties().setProperty("animacion_ciegas_dealer", "false");
        preferences.properties().setProperty("animacion_apuestas", "false");
        preferences.properties().setProperty("animacion_contadores", "false");
        preferences.properties().setProperty("animacion_barajado", "false");
        preferences.properties().setProperty("animacion_reparto", "false");
        preferences.properties().setProperty("animacion_destape", "false");
        preferences.properties().setProperty("animacion_swap", "false");

        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);

        assertTrue(settings.blindDealerAnimation());
        assertTrue(settings.betAnimation());
        assertTrue(settings.counterAnimation());
        assertTrue(settings.shuffleAnimation());
        assertTrue(settings.dealAnimation());
        assertTrue(settings.flipAnimation());
        assertTrue(settings.swapAnimation());
    }
}
