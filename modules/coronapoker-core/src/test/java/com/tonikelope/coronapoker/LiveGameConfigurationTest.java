package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LiveGameConfigurationTest {

    @Test
    void acceptsOnlyTheCompleteSetOfRulesEditableDuringATable() {
        GameConfigCodecV1.Configuration current = configuration("session", 10);
        GameConfigCodecV1.Configuration requested = current
                .withHands(40)
                .withIwtsth(!current.iwtsth())
                .withRunItTwice(!current.runItTwice())
                .withRabbitHunting(2)
                .withBotRebuy(!current.botRebuy())
                .withBotBalanceToHumans(!current.botBalanceToHumans())
                .withAnte(!current.ante())
                .withStraddle(!current.straddle());

        assertEquals(requested, Crupier.validateLiveConfiguration(
                current, requested, 12, false));
    }

    @Test
    void rejectsImmutableFieldsExpiredLimitsAndLockedRunItTwice() {
        GameConfigCodecV1.Configuration current = configuration("session", 10);
        GameConfigCodecV1.Configuration otherSession
                = configuration("different-session", 10);

        assertThrows(IllegalArgumentException.class,
                () -> Crupier.validateLiveConfiguration(
                        current, otherSession, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.validateLiveConfiguration(
                        current, current.withHands(5), 5, false));
        assertThrows(IllegalStateException.class,
                () -> Crupier.validateLiveConfiguration(current,
                        current.withRunItTwice(!current.runItTwice()), 1,
                        true));
    }

    private static GameConfigCodecV1.Configuration configuration(
            String session, int hands) {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setHandLimit(true);
        draft.setHandLimitCount(hands);
        return GameConfigCodecV1.fromSettings(
                draft.snapshot(), false, session);
    }
}
