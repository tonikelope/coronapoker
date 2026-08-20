package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalUnlockFailureClosesChannelTest {

    @Test
    public void everyUnlockHandlerAbortClosesTheHostChannel() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/WaitingRoomFrame.java"))
                .replace("\r\n", "\n");
        int start = source.indexOf("case \"REQ_SRA_UNLOCK_CHAIN\":");
        int end = source.indexOf("case \"H_CHECK\":", start);
        assertTrue(start >= 0 && end > start, "REQ_SRA_UNLOCK_CHAIN handler not found");
        String handler = source.substring(start, end);
        String[] lines = handler.split("\n");
        int aborts = 0;
        for (int i = 0; i < lines.length; i++) {
            if ("return;".equals(lines[i].trim())) {
                aborts++;
                int previous = i - 1;
                while (previous >= 0 && lines[previous].trim().isEmpty()) previous--;
                assertEquals("closeCriticalHostChannel();", lines[previous].trim(),
                        "critical unlock return at handler line " + (i + 1) + " is a silent abort");
            }
        }
        assertTrue(aborts >= 15, "test did not cover the complete critical handler");
        assertTrue(handler.matches("(?s).*catch \\(Exception e\\) \\{.*closeCriticalHostChannel\\(\\);.*"),
                "unexpected unlock exception must close the host channel");
    }

    private static Path locateRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("src/main/java"))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
