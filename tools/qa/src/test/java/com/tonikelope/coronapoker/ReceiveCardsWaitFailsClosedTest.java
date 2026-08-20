package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class ReceiveCardsWaitFailsClosedTest {

    @Test
    public void missingCardsExpireAndNeverAuthorizeDeal() {
        assertFalse(Crupier.receiveCardsWaitExpired(Crupier.RECEIVE_CARDS_HARD_TIMEOUT_MS - 1L));
        assertTrue(Crupier.receiveCardsWaitExpired(Crupier.RECEIVE_CARDS_HARD_TIMEOUT_MS));
        assertFalse(Crupier.receivedCardsAllowDeal(null));
        assertTrue(Crupier.receivedCardsAllowDeal(new ArrayList<String>()));
    }
}
