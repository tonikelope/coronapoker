package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Guards the fail-closed handoff of asynchronous GDX termination commands. */
final class GdxTerminationExecutorContainmentTest {

    @Test
    void exitAndRecoverableStopCannotLoseAnExecutorFailure() throws Exception {
        Path root = locateRoot();
        String factory = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/CoreGameTableFactory.java"));
        String dealer = Files.readString(root.resolve(
                "modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java"));

        int exitStart = factory.indexOf(
                "command instanceof TableCommand.ExitGame");
        int stopStart = factory.indexOf(
                "command instanceof TableCommand.StopGame", exitStart);
        int nextStart = factory.indexOf(
                "command instanceof TableCommand.TogglePause", stopStart);

        assertTrue(exitStart >= 0 && exitStart < stopStart
                && stopStart < nextStart);
        assertContained(factory.substring(exitStart, stopStart));
        assertContained(factory.substring(stopStart, nextStart));
        assertTrue(dealer.contains(
                "void containExternalTableFailure(RuntimeException cause)"));
        assertTrue(dealer.contains(
                "containTableFailure(java.util.Objects.requireNonNull(cause, \"cause\"))"));
    }

    private static void assertContained(String commandBranch) {
        assertTrue(commandBranch.contains("catch (RuntimeException failure)"));
        assertTrue(commandBranch.contains(
                "dealer.containExternalTableFailure(failure)"));
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
