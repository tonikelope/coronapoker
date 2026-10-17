package com.tonikelope.coronapoker.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NewGameConnectionDraftTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mirrorsSwingInputLimitsAndSanitizesHumanNicknameAtSubmission() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(new Properties(),
                NewGameConnectionDraft.Mode.CREATE);
        draft.setNickname("ab$cdefghijklmnopqrstuvwxyz");
        draft.setPassword("123456789012345678901234567890EXTRA");
        draft.setPort("");
        draft.setPort("7");
        draft.setPort("72");
        draft.setPort("72x");
        draft.setPort("62349");
        draft.setPort("623499");

        NewGameConnectionDraft.Submission submission = draft.beginSubmission();

        assertEquals("abcdefghijklmn", submission.nickname());
        assertEquals(30, submission.password().length());
        assertEquals("62349", submission.port());
    }

    @Test
    void recoverCannotSubmitUntilItsAsynchronousLoadCompletes() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(new Properties(),
                NewGameConnectionDraft.Mode.RECOVER);
        draft.setNickname("Alice");

        assertTrue(draft.beginRecoverLoad());
        assertFalse(draft.beginRecoverLoad());
        assertFalse(draft.canSubmit());
        draft.completeRecoverLoad(17);

        assertTrue(draft.canSubmit());
        assertEquals(17, draft.beginSubmission().recoveredGameId());
    }

    @Test
    void submitRejectsPortsOutsideTheTcpRangeBeforeOpeningTheNetwork() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                new Properties(), NewGameConnectionDraft.Mode.JOIN);
        draft.setNickname("Alice");

        draft.setPort("0");
        assertFalse(draft.canSubmit());
        assertFalse(draft.portValid());
        draft.setPort("65535");
        assertTrue(draft.canSubmit());
        assertTrue(draft.portValid());
        draft.setPort("65536");
        assertFalse(draft.canSubmit());
        draft.setPort("99999");
        assertFalse(draft.canSubmit());
    }

    @Test
    void portEditorCanBeClearedAndRetypedButRejectsNonNumericOrOversizedText() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                new Properties(), NewGameConnectionDraft.Mode.CREATE);

        assertEquals("7234", draft.port());
        draft.setPort("");
        assertEquals("", draft.port());
        draft.setPort("2");
        draft.setPort("23");
        draft.setPort("234");
        draft.setPort("2345");
        assertEquals("2345", draft.port());
        assertTrue(draft.portValid());

        draft.setPort("23x45");
        draft.setPort("123456");
        assertEquals("2345", draft.port());
    }

    @Test
    void malformedPersistedPortFallsBackToTheSafeDefault() {
        Properties properties = new Properties();
        properties.setProperty("local_port", "not-a-port");

        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                properties, NewGameConnectionDraft.Mode.CREATE);

        assertEquals("7234", draft.port());
        assertTrue(draft.portValid());
    }

    @Test
    void failedSubmissionUnlocksRetryButDoubleSubmissionIsRejected() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(new Properties(),
                NewGameConnectionDraft.Mode.CREATE);
        draft.setNickname("Alice");

        draft.beginSubmission();
        assertThrows(IllegalStateException.class, draft::beginSubmission);
        draft.submissionFailed();
        assertTrue(draft.canSubmit());
    }

    @Test
    void cancelIsSideEffectFreeAndSuccessfulJoinPersistsHistoryOnce() throws Exception {
        Path file = temporaryDirectory.resolve("coronapoker.properties");
        Files.writeString(file, "nick=Old\nserver_history=one:1@two:2\n");
        PreferencesService preferences = new PreferencesService(file);
        preferences.start();
        NewGameConnectionDraft cancelled = NewGameConnectionDraft.from(
                preferences.properties(), NewGameConnectionDraft.Mode.JOIN);
        cancelled.setNickname("Cancelled");
        cancelled.setServer("never");
        cancelled.setPort("9");

        preferences.close();
        Properties unchanged = load(file);
        assertEquals("Old", unchanged.getProperty("nick"));
        assertEquals("one:1@two:2", unchanged.getProperty("server_history"));

        preferences = new PreferencesService(file);
        preferences.start();
        NewGameConnectionDraft committed = NewGameConnectionDraft.from(
                preferences.properties(), NewGameConnectionDraft.Mode.JOIN);
        committed.setNickname("Alice");
        committed.setServer("one");
        committed.setPort("1");
        NewGameConnectionDraft.Submission submission = committed.beginSubmission();
        committed.commitSuccessful(preferences, submission);
        assertFalse(committed.canSubmit());
        PreferencesService committedPreferences = preferences;
        assertThrows(IllegalStateException.class,
                () -> committed.commitSuccessful(committedPreferences, submission));
        preferences.close();

        Properties saved = load(file);
        assertEquals("Alice", saved.getProperty("nick"));
        assertEquals("two:2@one:1", saved.getProperty("server_history"));
    }

    private static Properties load(Path file) throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }
}
