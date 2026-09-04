package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CorePlayerControllerTest {

    @Test
    void everyContributionRegistersThePlayerInTheCurrentPot() {
        CorePlayerController player = CorePlayerController.local("player");
        player.setStack(10d);
        AtomicInteger registrations = new AtomicInteger();
        player.bindPotRegistration(registrations::incrementAndGet);

        player.setBet(0.20d);
        player.postAnte(0.05d);

        assertEquals(2, registrations.get());
        assertEquals(0.25d, player.getBote());
        assertEquals(9.75d, player.getStack());
    }
}
