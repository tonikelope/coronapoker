package com.tonikelope.coronapoker.swing;

import com.tonikelope.coronapoker.core.CoronaPokerApplication;
import com.tonikelope.coronapoker.core.PreferencesService;
import java.util.Objects;

/** Transitional adapter from the legacy Swing static graph to typed services. */
public final class SwingServiceBridge {

    private static volatile PreferencesService preferences;

    private SwingServiceBridge() {
    }

    public static synchronized void bind(CoronaPokerApplication application) {
        PreferencesService service = Objects.requireNonNull(application, "application")
                .service(PreferencesService.class);
        if (preferences != null && preferences != service) {
            throw new IllegalStateException("Swing services already bound to another application");
        }
        preferences = service;
    }

    public static PreferencesService preferencesOrNull() {
        return preferences;
    }
}
