package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class CooperativeTeardownPolicyTest {

    private static final Pattern CANCELLATION_ACTION = Pattern.compile(
            "Thread\\.currentThread\\(\\)\\.interrupt\\(\\)|"
            + "\\breturn\\b|\\bbreak\\b|\\bthrow\\b|"
            + "logCooperativeCancellation|interrupted\\s*=\\s*true|"
            + "setFin_de_la_transmision\\s*\\(");

    @Test
    public void interruptedWaitsCannotSilentlyKeepAnOldTableWorkerAlive() throws Exception {
        Path sourceRoot = sourceRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                        if (!lines.get(i).contains("catch (InterruptedException")) {
                            continue;
                        }
                        int end = Math.min(lines.size(), i + 12);
                        String catchWindow = String.join("\n", lines.subList(i, end))
                                .replaceAll("(?s)/\\*.*?\\*/", "")
                                .replaceAll("(?m)//.*$", "");
                        if (!CANCELLATION_ACTION.matcher(catchWindow).find()) {
                            violations.add(sourceRoot.relativize(path) + ":" + (i + 1));
                        }
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "InterruptedException must terminate, propagate, or explicitly preserve cancellation: "
                + violations);
    }

    @Test
    public void criticalCrupierResponseLoopsReturnInsteadOfRetryingWithInterruptSet() throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        for (String method : List.of("requestRemoteCascade", "requestRemoteRotation",
                "requestRemoteUnlockChain", "solicitarYRecibirCartasVisuales")) {
            String body = methodBody(source, method);
            assertTrue(Pattern.compile(
                    "catch \\(InterruptedException ex\\) \\{[^}]*interrupt\\(\\);[^}]*return(?: null)?;",
                    Pattern.DOTALL).matcher(body).find(),
                    method + " must return immediately when teardown interrupts its response wait");
        }
    }

    private static String methodBody(String source, String methodName) {
        int name = source.indexOf(methodName + "(");
        if (name < 0) {
            throw new IllegalArgumentException("method not found: " + methodName);
        }
        int open = source.indexOf('{', name);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new IllegalArgumentException("unterminated method: " + methodName);
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
        throw new IllegalStateException("CoronaPoker source root not found");
    }
}
