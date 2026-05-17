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

import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class DebugLog {

    private static final int MAX_CHARS = 512 * 1024;
    private static final SimpleFormatter FORMATTER = new SimpleFormatter();
    private static final StringBuilder BUFFER = new StringBuilder(8192);
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

    public static void install() {
        Logger root = java.util.logging.LogManager.getLogManager().getLogger("");
        root.addHandler(HANDLER);
    }

    public static String snapshot() {
        synchronized (BUFFER) {
            return BUFFER.toString();
        }
    }

    public static void subscribe(Consumer<String> l) {
        listener = l;
    }

    public static void unsubscribe(Consumer<String> l) {
        if (listener == l) {
            listener = null;
        }
    }

    public static void clear() {
        synchronized (BUFFER) {
            BUFFER.setLength(0);
        }
    }
}
