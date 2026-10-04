package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdxVoiceNoteLibraryTest {

    @TempDir
    Path temporary;

    @Test
    void listsOnlyValidDirectNotesNewestFirst() throws Exception {
        GdxVoiceNoteLibrary library = new GdxVoiceNoteLibrary(temporary);
        byte[] shortNote = note(200);
        byte[] longNote = note(400);
        Files.write(temporary.resolve("100_Ana Maria_abcdefgh.wav"),
                shortNote);
        Files.write(temporary.resolve("200_Bob_12345678.WAV"), longNote);
        Files.write(temporary.resolve("300_invalid_12345678.wav"),
                new byte[]{1, 2, 3});
        Files.writeString(temporary.resolve("400_note.txt"), "ignored");

        List<GdxVoiceNoteLibrary.Entry> entries = library.list();

        assertEquals(2, entries.size());
        assertEquals("Bob", entries.get(0).nickname());
        assertEquals(200L, entries.get(0).timestampMillis());
        assertEquals(400L, entries.get(0).durationMillis());
        assertEquals("Ana Maria", entries.get(1).nickname());
        assertArrayEquals(shortNote, library.read(entries.get(1)));
    }

    @Test
    void malformedNameFallsBackToFileMetadata() throws Exception {
        Path note = temporary.resolve("legacy.wav");
        Files.write(note, note(300));
        Files.setLastModifiedTime(note, FileTime.fromMillis(9876L));

        GdxVoiceNoteLibrary.Entry entry = new GdxVoiceNoteLibrary(temporary)
                .list().get(0);

        assertEquals("legacy", entry.nickname());
        assertEquals(9876L, entry.timestampMillis());
        assertEquals(300L, entry.durationMillis());
    }

    @Test
    void deleteRejectsEntriesOutsideTheLibrary() throws Exception {
        GdxVoiceNoteLibrary library = new GdxVoiceNoteLibrary(temporary);
        Path outside = Files.createTempFile("outside-voice-note", ".wav");
        try {
            Files.write(outside, note(200));
            GdxVoiceNoteLibrary.Entry forged = new GdxVoiceNoteLibrary.Entry(
                    outside, 0L, "forged", 200L);
            assertThrows(java.io.IOException.class,
                    () -> library.delete(forged));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void purgeDeletesOnlyDirectRegularWavFiles() throws Exception {
        Path valid = temporary.resolve("100_A_12345678.wav");
        Path invalidWav = temporary.resolve("invalid.wav");
        Path text = temporary.resolve("keep.txt");
        Path nested = Files.createDirectory(temporary.resolve("nested"))
                .resolve("keep.wav");
        Files.write(valid, note(200));
        Files.write(invalidWav, new byte[]{1});
        Files.writeString(text, "keep");
        Files.write(nested, note(200));

        assertEquals(2, new GdxVoiceNoteLibrary(temporary).purge());
        assertFalse(Files.exists(valid));
        assertFalse(Files.exists(invalidWav));
        assertEquals("keep", Files.readString(text));
        assertEquals(note(200).length, Files.size(nested));
    }

    @Test
    void storesValidatedNotesWithSafeMetadata() throws Exception {
        GdxVoiceNoteLibrary library = new GdxVoiceNoteLibrary(temporary);
        byte[] wav = note(250);

        GdxVoiceNoteLibrary.Entry stored = library.store("Ana / Bob", wav);

        assertEquals("Ana___Bob", stored.nickname());
        assertEquals(250L, stored.durationMillis());
        assertEquals(temporary.toAbsolutePath().normalize(),
                stored.path().getParent());
        assertArrayEquals(wav, library.read(stored));
        assertEquals(1, library.list().size());
        assertThrows(java.io.IOException.class,
                () -> library.store("bad", new byte[]{1, 2, 3}));
    }

    @Test
    void retentionPurgeKeepsRecentNotesAndForeverMode() throws Exception {
        GdxVoiceNoteLibrary library = new GdxVoiceNoteLibrary(temporary);
        long now = 100L * 24L * 60L * 60L * 1000L;
        Path expired = temporary.resolve("old.wav");
        Path recent = temporary.resolve("recent.wav");
        Files.write(expired, note(200));
        Files.write(recent, note(200));
        Files.setLastModifiedTime(expired,
                FileTime.fromMillis(now - 8L * 24L * 60L * 60L * 1000L));
        Files.setLastModifiedTime(recent,
                FileTime.fromMillis(now - 6L * 24L * 60L * 60L * 1000L));

        assertEquals(1, library.purgeExpired(7, now));
        assertFalse(Files.exists(expired));
        assertEquals(0, library.purgeExpired(0, now));
        assertEquals(note(200).length, Files.size(recent));
    }

    private static byte[] note(int millis) throws Exception {
        int samples = Math.round(GdxVoiceRecorder.SAMPLE_RATE * millis
                / 1000f);
        return GdxVoiceRecorder.encodePcm(new byte[samples * 2]);
    }
}
