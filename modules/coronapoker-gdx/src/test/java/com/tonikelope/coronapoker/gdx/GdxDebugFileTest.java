package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxDebugFileTest {

    @Test
    void usesTheSamePerUserDebugDirectoryAsCoronaPoker() {
        assertEquals(Path.of("C:/Users/tester/.coronapoker/Debug")
                .toAbsolutePath().normalize(),
                GdxDebugFile.debugDirectory("C:/Users/tester"));
    }
}
