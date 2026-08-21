package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Runs complete production host/client Crupiers over real loopback sockets. */
@Tag("real-game-e2e")
final class RealGameLoopbackE2EIT {

    @Test
    @Timeout(value = 90, unit = TimeUnit.MINUTES)
    void completesConfiguredLocalGameWithRealCrupiersAndSockets(@TempDir Path root) throws Exception {
        int clients = intProperty("qa.e2e.clients", 1, 1, 7);
        int bots = intProperty("qa.e2e.bots", 2, 0, 7);
        int hands = intProperty("qa.e2e.hands", 1, 1, 100);
        long seed = Long.getLong("qa.e2e.seed", 23059L);
        String scenario = System.getProperty("qa.e2e.scenario", "normal");
        assertTrue(scenario.equals("normal") || scenario.equals("abrupt-exit")
                || scenario.equals("controlled-exit") || scenario.equals("allin-rit"),
                "unsupported qa.e2e.scenario: " + scenario);
        assertTrue(clients + bots + 1 <= 8, "host + clients + bots must fit the table");
        assertTrue(!scenario.equals("allin-rit") || bots == 0,
                "allin-rit requires zero bots so every survivor uses the forced action policy");
        assertTrue(!scenario.equals("allin-rit") || hands == 1,
                "allin-rit requires one hand because a full-stack all-in may eliminate a seat");

        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }

        List<NodeProcess> nodes = new ArrayList<>();
        try {
            NodeProcess host = startNode(root.resolve("host"), "host", "server", port,
                    clients, bots, hands, seed);
            nodes.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)), host.diagnostic());

            for (int i = 1; i <= clients; i++) {
                NodeProcess client = startNode(root.resolve("client-" + i), "client", "client" + i,
                        port, clients, bots, hands, seed + i);
                nodes.add(client);
            }

            if (scenario.equals("abrupt-exit")) {
                runAbruptExitScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("controlled-exit")) {
                runControlledExitScenario(nodes, host, clients + bots + 1);
                return;
            }
            if (scenario.equals("allin-rit")) {
                runAllInRitScenario(nodes, host, clients + bots + 1);
                return;
            }

            for (NodeProcess node : nodes) {
                Duration completionTimeout = Duration.ofSeconds(Math.max(240L, hands * 60L));
                assertTrue(node.await("CP_E2E_HANDS_COMPLETE", completionTimeout), node.diagnostic());
                assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
                assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
                assertFalse(node.contains("QA dialog suppressed [Error"), node.diagnostic());
            }

            List<String> hostConsensus = host.linesContaining(" verified: ");
            List<String> hostBalances = host.canonicalBalanceSnapshots();
            assertEquals(hands, hostConsensus.size(), host.diagnostic());
            assertEquals(hands, hostBalances.size(), host.diagnostic());
            for (NodeProcess node : nodes.subList(1, nodes.size())) {
                assertEquals(hostConsensus, node.linesContaining(" verified: "),
                        "consensus divergence\n" + node.diagnostic());
                assertEquals(hostBalances, node.canonicalBalanceSnapshots(),
                        "balance divergence\n" + node.diagnostic());
            }
        } finally {
            // Clients first, host last: avoids manufacturing a reconnect error
            // by killing the server while clients still own live channels.
            for (int i = nodes.size() - 1; i >= 0; i--) {
                nodes.get(i).close();
            }
        }
    }

    private static void runAbruptExitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        NodeProcess victim = nodes.get(1);
        assertTrue(host.await("HAND 1: betting round Preflop", Duration.ofSeconds(90)),
                host.diagnostic());
        victim.killForcibly();

        assertTrue(host.await("MISDEAL triggered:", Duration.ofMinutes(3)), host.diagnostic());
        assertTrue(host.await("RECOVERY: abortAndRecover engaged", Duration.ofSeconds(30)),
                host.diagnostic());
        assertTrue(host.await("CP_E2E_LEDGER", Duration.ofSeconds(30)), host.diagnostic());
        assertTrue(host.contains("potCents=0"), host.diagnostic());
        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        assertTrue(host.isAlive(), "host died after peer MISDEAL\n" + host.diagnostic());
        assertFalse(host.contains("TABLE_FAILURE_V1"), host.diagnostic());
        assertFalse(host.contains("CP_E2E_FAIL"), host.diagnostic());
    }

    private static void runControlledExitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        NodeProcess departingClient = nodes.get(1);
        assertTrue(host.await("HAND 1: betting round Preflop", Duration.ofSeconds(90)),
                host.diagnostic());
        assertTrue(departingClient.await("HAND 1: betting round Preflop", Duration.ofSeconds(30)),
                departingClient.diagnostic());

        departingClient.send("CONTROLLED_EXIT");
        assertTrue(departingClient.await("CP_E2E_CONTROLLED_EXIT_SENT", Duration.ofSeconds(30)),
                departingClient.diagnostic());
        assertTrue(host.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(3)), host.diagnostic());
        assertTrue(host.await("CP_E2E_LEDGER", Duration.ofSeconds(30)), host.diagnostic());

        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());
        assertTrue(host.contains("verified: 1 receipts unanimous"), host.diagnostic());
        assertTrue(host.isAlive(), "host died after controlled EXIT\n" + host.diagnostic());
        assertFalse(host.contains("MISDEAL triggered:"), host.diagnostic());
        assertFalse(host.contains("TABLE_FAILURE_V1"), host.diagnostic());
        assertFalse(host.contains("CP_E2E_FAIL"), host.diagnostic());
    }

    private static void runAllInRitScenario(List<NodeProcess> nodes, NodeProcess host,
            int seats) throws Exception {
        for (NodeProcess node : nodes) {
            assertTrue(node.await("CP_E2E_HANDS_COMPLETE", Duration.ofMinutes(4)),
                    node.diagnostic());
            assertTrue(node.contains("CP_E2E_RIT_VOTE decision=run-it-twice"),
                    node.diagnostic());
            assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
            assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
            assertFalse(node.contains("QA dialog suppressed [Error"), node.diagnostic());
        }
        assertTrue(host.contains("RUN-IT-TWICE vote result: true"), host.diagnostic());
        assertEquals(3, host.countContaining("Initiating SRA SIDE-B street unlock:"),
                host.diagnostic());
        assertTrue(host.contains("balanceRows=" + seats), host.diagnostic());
        assertTrue(host.contains("stackCents=" + (seats * 1000L)), host.diagnostic());

        List<String> hostConsensus = host.linesContaining(" verified: ");
        List<String> hostBalances = host.canonicalBalanceSnapshots();
        for (NodeProcess node : nodes.subList(1, nodes.size())) {
            assertEquals(hostConsensus, node.linesContaining(" verified: "),
                    "RIT consensus divergence\n" + node.diagnostic());
            assertEquals(hostBalances, node.canonicalBalanceSnapshots(),
                    "RIT balance divergence\n" + node.diagnostic());
        }
    }

    private static NodeProcess startNode(Path home, String role, String nick, int port,
            int clients, int bots, int hands, long seed) throws IOException {
        Files.createDirectories(home);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                java,
                "-Djava.awt.headless=false",
                "-Dcoronapoker.qa.suppressDialogs=true",
                "-Dcoronapoker.qa.windowMode="
                        + System.getProperty("qa.e2e.windowMode", "hidden"),
                "-Dcoronapoker.qa.screen="
                        + Integer.getInteger("qa.e2e.screen", 2),
                "-Dcoronapoker.qa.animations="
                        + Boolean.getBoolean("qa.e2e.animations"),
                "-Dcoronapoker.qa.scenario="
                        + System.getProperty("qa.e2e.scenario", "normal"),
                "-Dcoronapoker.testMode="
                        + System.getProperty("qa.e2e.testMode", "true"),
                "-Duser.home=" + home.toAbsolutePath(),
                "-cp", classpath,
                RealGameNodeMain.class.getName(),
                role, nick, Integer.toString(port), Integer.toString(clients),
                Integer.toString(bots), Integer.toString(hands), Long.toString(seed));
        builder.redirectErrorStream(true);
        return new NodeProcess(role + ":" + nick, builder.start());
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        int value = Integer.getInteger(name, fallback);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + "," + max + "]");
        }
        return value;
    }

    private static final class NodeProcess implements AutoCloseable {

        private final String name;
        private final Process process;
        private final List<String> output = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch readerDone = new CountDownLatch(1);

        private NodeProcess(String name, Process process) {
            this.name = name;
            this.process = process;
            Thread reader = new Thread(() -> {
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        output.add(line);
                        // Keep the real peer trace visible while the watchdog is
                        // running. Otherwise a hang hides the decisive last line
                        // until the full JUnit timeout expires.
                        System.out.println("[" + name + "] " + line);
                    }
                } catch (IOException ex) {
                    output.add("reader failure: " + ex);
                } finally {
                    readerDone.countDown();
                }
            }, "e2e-output-" + name);
            reader.setDaemon(true);
            reader.start();
        }

        private boolean await(String marker, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (contains(marker)) {
                    return true;
                }
                if (!process.isAlive()) {
                    readerDone.await(2, TimeUnit.SECONDS);
                    return contains(marker);
                }
                Thread.sleep(25L);
            }
            return contains(marker);
        }

        private boolean contains(String text) {
            synchronized (output) {
                return output.stream().anyMatch(line -> line.contains(text));
            }
        }

        private List<String> linesContaining(String token) {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains(token))
                        .map(line -> line.substring(line.indexOf("Hand ")))
                        .toList();
            }
        }

        private long countContaining(String token) {
            synchronized (output) {
                return output.stream().filter(line -> line.contains(token)).count();
            }
        }

        private List<String> canonicalBalanceSnapshots() {
            synchronized (output) {
                return output.stream()
                        .filter(line -> line.contains("Balance after hand "))
                        .map(line -> line.substring(line.indexOf("Balance after hand ")))
                        .map(line -> {
                            int arrow = line.indexOf(" -> ");
                            String prefix = line.substring(0, arrow + 4);
                            String[] balances = line.substring(arrow + 4).split("@");
                            java.util.Arrays.sort(balances);
                            return prefix + String.join("@", balances);
                        })
                        .toList();
            }
        }

        private String diagnostic() {
            synchronized (output) {
                int start = Math.max(0, output.size() - 120);
                return "Node " + name + " (alive=" + process.isAlive() + ") output:\n"
                        + String.join("\n", output.subList(start, output.size()));
            }
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private void send(String command) throws IOException {
            process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }

        private void killForcibly() throws InterruptedException {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            readerDone.await(2, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
