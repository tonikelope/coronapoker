package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class RabbitCrossHandReplayRejectedTest {
    @Test
    public void authorizationFromAnotherHandCannotAdvanceLedger() {
        RabbitFeeLedger oldHand = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(1), 3, 10, 20);
        RabbitFeeLedger newHand = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(2), 3, 10, 20);
        RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(
                RabbitClientChosenCounterRejectedTest.hand(1), "alice",
                RabbitClientChosenCounterRejectedTest.nonce(1),
                RabbitClientChosenCounterRejectedTest.signature(1));
        assertEquals(RabbitFeeLedger.Acceptance.REJECTED,
                newHand.accept(oldHand.authorize(request).value()));
        RabbitFeeLedger.Request firstNew = new RabbitFeeLedger.Request(
                RabbitClientChosenCounterRejectedTest.hand(2), "alice",
                RabbitClientChosenCounterRejectedTest.nonce(2),
                RabbitClientChosenCounterRejectedTest.signature(2));
        assertEquals(1, newHand.authorize(firstNew).value().count());
    }
}
