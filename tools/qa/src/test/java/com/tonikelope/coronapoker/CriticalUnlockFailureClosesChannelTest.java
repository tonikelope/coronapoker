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
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"))
                .replace("\r\n", "\n");
        int start = source.indexOf("private void processClientUnlockChainRequest(");
        int end = source.indexOf("private void processClientDualLockBundle(", start);
        assertTrue(start >= 0 && end > start, "REQ_SRA_UNLOCK_CHAIN handler not found");
        String handler = source.substring(start, end);
        assertTrue(handler.contains("catch (Exception failure)"));
        assertTrue(handler.contains(
                "failClientCriticalHostCommand(\"REQ_SRA_UNLOCK_CHAIN\", failure)"),
                "unexpected unlock failure must close the host channel");
        assertTrue(handler.contains("if (isFin_de_la_transmision() || this.termination_pending)"),
                "only an already-closing table may suppress the fail-closed path");
    }

    private static Path locateRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("modules/coronapoker-core/src/main/java"))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
