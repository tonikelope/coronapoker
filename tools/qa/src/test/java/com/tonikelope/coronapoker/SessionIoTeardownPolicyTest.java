package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class SessionIoTeardownPolicyTest {

    @Test
    public void sessionIoTimeoutFitsInsideTheOldPoolTerminationWindow() {
        assertTrue(Helpers.COOPERATIVE_SESSION_IO_TIMEOUT_MS
                < Helpers.THREAD_POOL_SHUTDOWN_TIMEOUT * 1_000,
                "session I/O must release before the executor handoff deadline");
    }

    @Test
    public void gifMetadataReadsUseTheBoundedSessionIoOpener() throws IOException {
        String source = Files.readString(sourceRoot().resolve("Helpers.java"));

        for (String method : new String[]{"getGIFLength", "getGIFFramesCount", "isImageGIF"}) {
            String body = methodBody(source, method);
            assertTrue(body.contains("openCooperativeUrlStream(url)"),
                    method + " must not leave an old executor blocked on an unbounded URL read");
            assertTrue(!body.contains("url.openStream()"),
                    method + " must not bypass the cooperative session I/O timeout");
        }
    }

    @Test
    public void upnpDiscoveryAndUnmapCannotBlockTableTeardownIndefinitely() throws IOException {
        Path javaRoot = javaSourceRoot();
        String gateway = Files.readString(javaRoot.resolve("org/dosse/upnp/Gateway.java"));
        assertTrue(occurrences(gateway, "setConnectTimeout(") >= 2,
                "UPnP gateway discovery and commands both need bounded connect timeouts");
        assertTrue(occurrences(gateway, "setReadTimeout(") >= 2,
                "UPnP gateway discovery and commands both need bounded read timeouts");

        String upnp = Files.readString(javaRoot.resolve("org/dosse/upnp/UPnP.java"));
        String waitInit = methodBody(upnp, "waitInit");
        assertTrue(waitInit.contains("Thread.currentThread().isInterrupted()")
                && waitInit.contains("return;"),
                "UPnP discovery wait must stop cooperatively during teardown");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    private static String methodBody(String source, String methodName) {
        int start = source.indexOf(" " + methodName + "(");
        assertTrue(start >= 0, "method not found: " + methodName);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new AssertionError("unterminated method: " + methodName);
    }

    private static Path sourceRoot() {
        return javaSourceRoot().resolve("com/tonikelope/coronapoker");
    }

    private static Path javaSourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository source root not found");
    }
}
