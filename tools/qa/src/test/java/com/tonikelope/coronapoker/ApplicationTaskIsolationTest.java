package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class ApplicationTaskIsolationTest {

    @Test
    public void startupTasksCannotHoldThePerTableExecutorOpen() throws Exception {
        String init = Files.readString(sourceRoot().resolve("Init.java"));

        assertTrue(init.contains("Helpers.applicationTask(Helpers::purgeOldVoiceNotes"),
                "voice-note startup cleanup belongs to the application lifecycle");
        assertTrue(init.contains("Helpers.applicationTask(() -> {\n            Audio.warmAudioDevice()"),
                "audio startup warmup belongs to the application lifecycle");

        String update = methodBody(init, "UPDATE");
        assertTrue(update.contains("Helpers.applicationTask(() ->"),
                "network update checks must not prevent a table executor handoff");
    }

    @Test
    public void applicationTasksAreDaemonThreads() throws Exception {
        Thread task = Helpers.applicationTask(() -> { }, "qa-app-task");
        task.join(1_000);
        assertTrue(task.isDaemon(), "application-lifetime helpers must never pin JVM shutdown");
    }

    private static String methodBody(String source, String methodName) {
        int name = source.indexOf("private static void " + methodName + "(");
        assertTrue(name >= 0, "method not found: " + methodName);
        int open = source.indexOf('{', name);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1).replace("\r\n", "\n");
            }
        }
        throw new AssertionError("unterminated method: " + methodName);
    }

    private static Path sourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository source root not found");
    }
}
