package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoBackwardCompatibilityPathsTest {

    @Test
    void oldWireAndPersistenceAdaptersAreAbsent() {
        assertFalse(hasMethod(Crupier.class, "canonicalLegacyRemoteRebuyAmount"));
        assertFalse(hasMethod(GameFrame.class, "migrateSplitAnimationPrefs"));
        assertFalse(hasMethod(SettlementRecord.class, "encode", byte[].class,
                java.util.List.class, long.class));
        assertFalse(hasMethod(SettlementRecord.class, "amountsBalance",
                java.util.List.class, long.class));
        assertFalse(hasMethod(Crupier.class, "recoveredActionBindsToRecord"));
    }

    @Test
    void missingCurrentSecurityContextFailsClosed() {
        assertFalse(Crupier.recordStartsAtHash(new byte[CanonicalActionRecord.RECORD_BYTES], null));
        assertFalse(Crupier.shouldApplyAsyncSequence(0L, 99L));
    }

    @Test
    void obsoleteAliasesAreNotInterpretedAndSeedShapesAreRejected() {
        assertEquals(Bot.Difficulty.MEDIUM,
                GamePreset.Settings.parse("DIFF=EXPERT").difficulty);
        assertThrows(RuntimeException.class,
                () -> DeterministicShuffle.shufflePermutation(52, new byte[32]));
    }

    @Test
    void publicSecurityDocsDoNotClaimADeletedSettlementCompatibilityFormat() throws IOException {
        Path root = projectRoot();
        for (String document : new String[]{"docs/SECURITY.md", "docs/ec-identity-spec.md"}) {
            String text = Files.readString(root.resolve(document));
            assertFalse(text.contains("legacy three-argument encoder"), document);
        }
    }

    @Test
    void publicReadmeStatesTheSingleVersionAdmissionRule() throws IOException {
        String readme = Files.readString(projectRoot().resolve("README.md"));
        assertEquals(true, readme.contains(
                "Every participant in a game must run the exact same CoronaPoker version"));
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... parameters) {
        if (parameters.length > 0) {
            try {
                type.getDeclaredMethod(name, parameters);
                return true;
            } catch (NoSuchMethodException expected) {
                return false;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("docs"))
                    && Files.isDirectory(current.resolve("src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker project root not found");
    }
}
