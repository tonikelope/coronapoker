package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class StraddleCanonicalResultDeliveryTest {

    @Test
    void straddleResultIsConfirmedOrTheHandStopsWithoutAnAssumedValue() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));

        assertTrue(source.contains("private boolean broadcastStraddleResult(int v)"));
        assertTrue(source.contains("broadcastGAMECommandFromServer(\"STRADDLE_RESULT#\" + v, null, true)"));
        assertTrue(source.contains("rejectCriticalStraddleResult(cmd"));
        assertTrue(source.contains("STRADDLE_RESULT timeout; closing host channel"));
        assertFalse(source.contains("return VoluntaryStraddleDialog.NO_STRADDLE;\n    }\n\n    // Straddler client"));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("CoronaPoker root not found from " + start);
    }
}
