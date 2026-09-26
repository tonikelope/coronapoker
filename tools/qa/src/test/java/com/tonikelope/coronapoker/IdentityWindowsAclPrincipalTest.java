package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IdentityWindowsAclPrincipalTest {

    @Test
    void aclUsesTheCreatedFilesOwnerInsteadOfTheUserNameProperty() throws Exception {
        String text = Files.readString(sourceRoot().resolve("PlayerIdentity.java"));
        int method = text.indexOf("private static void restrictToOwner(Path path)");
        int end = text.lastIndexOf('}');
        assertTrue(method >= 0 && end > method);

        String body = text.substring(method, end);
        assertTrue(body.contains("Files.getOwner(path)"));
        assertFalse(body.contains("System.getProperty(\"user.name\")"));
    }

    private static Path sourceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/core/identity");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate CoronaPoker production sources");
    }
}
