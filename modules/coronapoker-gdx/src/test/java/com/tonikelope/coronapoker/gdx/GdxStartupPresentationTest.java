package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxStartupPresentationTest {

    @Test
    void copyrightSignatureUsesTheRuntimeYearAfterTheStartYear() {
        assertEquals("© 2020–2026 Tonikelope Solutions S. L.",
                CoronaPokerGdxTable.startupPresentationTitle(2026));
        assertEquals("© 2020–2020 Tonikelope Solutions S. L.",
                CoronaPokerGdxTable.startupPresentationTitle(2019));
    }
}
