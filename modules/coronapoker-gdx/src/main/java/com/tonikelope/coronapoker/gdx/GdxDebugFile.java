/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Persists the GDX process console beside the legacy CoronaPoker logs. */
final class GdxDebugFile {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static PrintStream fileStream;

    static Path install() {
        Path directory = debugDirectory(System.getProperty("user.home"));
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("coronapoker_debug_gdx_"
                    + FILE_TIME.format(LocalDateTime.now()) + ".log");
            fileStream = new PrintStream(new FileOutputStream(
                    file.toFile(), true), true, StandardCharsets.UTF_8);
            PrintStream consoleOut = System.out;
            PrintStream consoleErr = System.err;
            System.setOut(new PrintStream(new MirroredOutputStream(
                    consoleOut, fileStream), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new MirroredOutputStream(
                    consoleErr, fileStream), true, StandardCharsets.UTF_8));
            return file;
        } catch (Exception failure) {
            System.err.println("Unable to create GDX debug log in "
                    + directory + ": " + failure.getMessage());
            return null;
        }
    }

    static Path debugDirectory(String userHome) {
        return Path.of(userHome, ".coronapoker", "Debug")
                .toAbsolutePath().normalize();
    }

    private static final class MirroredOutputStream extends OutputStream {

        private final OutputStream console;
        private final OutputStream file;

        MirroredOutputStream(OutputStream console, OutputStream file) {
            this.console = console;
            this.file = file;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            console.write(value);
            file.write(value);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length)
                throws IOException {
            console.write(data, offset, length);
            file.write(data, offset, length);
        }

        @Override
        public synchronized void flush() throws IOException {
            console.flush();
            file.flush();
        }
    }

    private GdxDebugFile() {
    }
}
