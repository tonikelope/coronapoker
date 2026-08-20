package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class RecoveryWireWiringTest {
    @Test
    public void recoverDataUsesTypedV1CodecAndNoJavaSerialization() throws IOException {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String receiveState = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/RecoveryReceiveState.java"));
        assertTrue(source.contains("new RecoveryReceiveState(GameFrame.UGI)"));
        assertTrue(receiveState.contains("RecoverySnapshotV1.decode(wire, expectedSession)"));
        assertTrue(source.contains("snapshot.value().encode()"));
        assertFalse(source.contains("ObjectInputStream"));
        assertFalse(source.contains("ObjectOutputStream"));
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
