package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ApplicationMetadataTest {

    @Test
    void modularProductUsesItsPackagedRuntimeVersion() {
        assertEquals("24.11", ApplicationMetadata.VERSION);
    }
}
