/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Root-logger {@link Handler} that buffers formatted log records in memory
 * (capped, oldest dropped first) and optionally forwards each one live to a
 * single subscriber. Backs the in-app debug console
 * ({@code DebugSettingsPanel}), which reads {@link #snapshot()} on open and
 * {@link #subscribe(Consumer)} for live updates.
 */
public final class DebugLog {

    private static final int MAX_CHARS = 512 * 1024;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Locale-independent two-line layout (matching SimpleFormatter's shape): a fixed ISO
    // timestamp plus the English level name (getName, not the localized name), so the in-app
    // debug console can colour each record by level reliably regardless of the system locale.
    private static final Formatter FORMATTER = new Formatter() {
        @Override
        public String format(LogRecord record) {
            String message = formatMessage(record);
            String throwable = "";
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw)) {
                    record.getThrown().printStackTrace(pw);
                }
                throwable = sw.toString();
            }
            String source;
            if (record.getSourceClassName() != null) {
                source = record.getSourceClassName();
                if (record.getSourceMethodName() != null) {
                    source += " " + record.getSourceMethodName();
                }
            } else {
                source = record.getLoggerName();
            }
            String ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), ZONE).format(TS_FORMAT);
            return ts + " " + source + "\n" + record.getLevel().getName() + ": " + message + "\n" + throwable;
        }
    };

    private static final StringBuilder BUFFER = new StringBuilder(8192);
    // Only one listener at a time: the debug console is the sole subscriber.
    private static volatile Consumer<String> listener = null;

    private static final Handler HANDLER = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record == null) {
                return;
            }
            String formatted;
            try {
                formatted = FORMATTER.format(record);
            } catch (Exception ex) {
                return;
            }
            synchronized (BUFFER) {
                BUFFER.append(formatted);
                // Cap memory use: keep only the most recent MAX_CHARS characters.
                if (BUFFER.length() > MAX_CHARS) {
                    BUFFER.delete(0, BUFFER.length() - MAX_CHARS);
                }
            }
            Consumer<String> l = listener;
            if (l != null) {
                try {
                    l.accept(formatted);
                } catch (Exception ignore) {
                }
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private DebugLog() {
    }

    /**
     * Formats a record exactly as the buffered console sees it. Package-visible
     * for tests.
     */
    static String format(LogRecord record) {
        return FORMATTER.format(record);
    }

    /**
     * Attaches the buffering handler to the root logger. Call once at startup.
     */
    public static void install() {
        Logger root = java.util.logging.LogManager.getLogManager().getLogger("");
        root.addHandler(HANDLER);
    }

    /**
     * @return the buffered log text accumulated so far.
     */
    public static String snapshot() {
        synchronized (BUFFER) {
            return BUFFER.toString();
        }
    }

    /**
     * Registers {@code l} to receive each formatted record as it's logged,
     * replacing any previous subscriber.
     */
    public static void subscribe(Consumer<String> l) {
        listener = l;
    }

    /**
     * Removes {@code l} if it is the current subscriber; no-op otherwise.
     */
    public static void unsubscribe(Consumer<String> l) {
        if (listener == l) {
            listener = null;
        }
    }

    /**
     * Clears the buffered log text.
     */
    public static void clear() {
        synchronized (BUFFER) {
            BUFFER.setLength(0);
        }
    }
}
