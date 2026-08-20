package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class SettlementReceiptTransportFailureTest {

    @Test
    public void ownReceiptAndEveryRelayMustSucceedBeforeSqlClose() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));

        assertTrue(source.contains("private boolean emitOwnReceipt(byte[] localReceipt)"));
        assertTrue(source.contains("if (localReceipt == null || !emitOwnReceipt(localReceipt))"));
        assertTrue(source.contains("HANDVERIFY receipt relay failed; refusing SQL close"));
        assertTrue(source.contains("return false; // receipt relay is a settlement barrier"));
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
