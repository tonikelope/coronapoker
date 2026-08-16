package com.tonikelope.coronapoker;

import java.awt.Rectangle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class FrameGeometryTest {

    @Test
    void defaultRestoredBoundsUseCenteredEightyPercent() {
        assertEquals(new Rectangle(192, 108, 1536, 864),
                Helpers.defaultFrameBounds(new Rectangle(0, 0, 1920, 1080)));
    }

    @Test
    void defaultRestoredBoundsRespectSecondaryMonitorOrigin() {
        assertEquals(new Rectangle(-1843, 144, 1638, 1152),
                Helpers.defaultFrameBounds(new Rectangle(-2048, 0, 2048, 1440)));
    }

    @Test
    void defaultRestoredBoundsRoundOddDimensionsConsistently() {
        assertEquals(new Rectangle(136, 77, 1093, 614),
                Helpers.defaultFrameBounds(new Rectangle(0, 0, 1366, 768)));
    }
}
