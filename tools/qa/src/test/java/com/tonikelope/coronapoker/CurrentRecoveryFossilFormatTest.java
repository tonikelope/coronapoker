package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CurrentRecoveryFossilFormatTest {

    @Test
    void onlyTheSingleCurrentDecisionShapeIsAccepted() {
        assertTrue(Crupier.isCurrentRecoveryFossil(
                "ORDER@ring#RIT@false,false,-1#STRADDLE@false"));
        assertTrue(Crupier.isCurrentRecoveryFossil(
                "ORDER@ring#RIT@true,true,2#STRADDLE@true"));

        assertFalse(Crupier.isCurrentRecoveryFossil("ORDER@ring"));
        assertFalse(Crupier.isCurrentRecoveryFossil(
                "ORDER@ring#RIT@false,false,-1"));
        assertFalse(Crupier.isCurrentRecoveryFossil(
                "ORDER@ring#STRADDLE@false"));
        assertFalse(Crupier.isCurrentRecoveryFossil(
                "ORDER@ring#RIT@no,false,-1#STRADDLE@false"));
        assertFalse(Crupier.isCurrentRecoveryFossil(
                "ORDER@ring#RIT@false,false,-1#STRADDLE@maybe"));
        assertFalse(Crupier.isCurrentRecoveryFossil(
                "RIT@false,false,-1#RIT@false,false,-1#STRADDLE@false"));
    }

    @Test
    void bothActiveRecoveryRolesEnforceTheCurrentFossilBeforeParsing() throws Exception {
        Path root = projectRoot();
        String source = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        assertEquals(2, occurrences(source,
                "if (!isCurrentRecoveryFossil(fosil))"));
    }

    @Test
    void publicRecoveryContractRejectsOldOrIncompleteFossils() throws Exception {
        String security = Files.readString(projectRoot().resolve("docs/SECURITY.md"));
        assertTrue(security.contains(
                "Recovery never migrates or default-fills an older or incomplete fossil."));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Path projectRoot() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("repository root not found");
        }
        return root;
    }
}
