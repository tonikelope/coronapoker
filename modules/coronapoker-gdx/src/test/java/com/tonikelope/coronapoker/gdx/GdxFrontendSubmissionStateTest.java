package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxFrontendSubmissionStateTest {

    @Test
    void createAndJoinRemainDisabledUntilRequiredFieldsAreValid() {
        for (NewGameConnectionDraft.Mode mode : new NewGameConnectionDraft.Mode[]{
            NewGameConnectionDraft.Mode.CREATE,
            NewGameConnectionDraft.Mode.JOIN}) {
            NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                    new Properties(), mode);
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));

            draft.setNickname("Alice");
            assertTrue(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(true, draft));

            draft.setPort("");
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
            draft.setPort("99999");
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
        }
    }

    @Test
    void recoveryWaitsForTheRealRecoveredGame() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                new Properties(), NewGameConnectionDraft.Mode.RECOVER);
        draft.setNickname("Alice");

        assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
        assertTrue(draft.beginRecoverLoad());
        assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
        draft.completeRecoverLoad(23);
        assertTrue(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
    }
}
