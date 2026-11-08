/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PokerVideoChromiumLogTest {
    @TempDir Path temp;

    @Test void forwardsOnlyNewRecordsAndRemovesSignedUrls() throws Exception {
        Path log = temp.resolve("chromium.log");
        Files.writeString(log, "[old:ERROR:gpu.cc:1] stale\n");
        List<LogRecord> records = new ArrayList<>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger logger = Logger.getLogger(PokerVideoChromiumLog.class.getName());
        logger.addHandler(capture);
        try {
            try (PokerVideoChromiumLog ignored = new PokerVideoChromiumLog(log)) {
                Files.writeString(log, "[new:ERROR:gpu.cc:1] failed https://example.org/video?sig=secret\r\n",
                        StandardOpenOption.APPEND);
            }
            assertEquals(1, records.size());
            assertEquals(Level.SEVERE, records.get(0).getLevel());
            assertEquals("[new:ERROR:gpu.cc:1] failed <URL>", records.get(0).getParameters()[0]);
        } finally {
            logger.removeHandler(capture);
        }
    }
}
