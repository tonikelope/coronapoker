package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SecondaryPotLabelConcurrencyTest {

    @Test
    void concurrentQueueMutationCannotOverflowAStaleSizeSnapshot() throws Exception {
        ConcurrentLinkedQueue<Integer> pots = new ConcurrentLinkedQueue<>();
        pots.add(1);
        pots.add(2);

        AtomicReference<Throwable> mutationFailure = new AtomicReference<>();
        Thread mutator = new Thread(() -> {
            try {
                for (int i = 0; i < 100_000; i++) {
                    pots.add(i);
                    if ((i & 1) == 0) {
                        pots.poll();
                    }
                    if (i % 97 == 0) {
                        pots.clear();
                    }
                }
            } catch (Throwable failure) {
                mutationFailure.set(failure);
            }
        }, "secondary-pot-mutator");
        mutator.start();
        for (int i = 0; i < 100_000; i++) {
            Player.formatSecondaryPotIndexes(pots);
        }
        mutator.join();
        assertEquals(null, mutationFailure.get());

        assertEquals("#1+#2", Player.formatSecondaryPotIndexes(java.util.List.of(1, 2)));
    }

    @Test
    void localAndRemotePlayersUseTheSizeIndependentFormatter() throws Exception {
        Path source = locateSourceDir();
        for (String name : new String[]{"LocalPlayer.java", "RemotePlayer.java"}) {
            String text = Files.readString(source.resolve(name));
            assertTrue(text.contains("Player.formatSecondaryPotIndexes(botes_secundarios)"), name);
            assertFalse(text.contains("new String[botes_secundarios.size()]"), name);
        }
    }

    private static Path locateSourceDir() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate CoronaPoker production sources");
    }
}
