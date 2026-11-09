/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.DebugLog;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Routes Java console output and JUL records to the shared GDX debug log. */
final class GdxDebugFile {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static PrintStream disk;
    private static CapturedConsole stdout;
    private static CapturedConsole stderr;

    static Path install() {
        Path directory = debugDirectory(System.getProperty("user.home"));
        Path file = directory.resolve("coronapoker_debug_gdx_"
                + FILE_TIME.format(LocalDateTime.now()) + ".log");
        try {
            Files.createDirectories(directory);
            disk = new PrintStream(Files.newOutputStream(file,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), true, StandardCharsets.UTF_8);
            DebugLog.install(disk::print);
        } catch (Exception failure) {
            DebugLog.install();
            Logger.getLogger(GdxDebugFile.class.getName()).log(Level.WARNING,
                    "Unable to create GDX debug log in " + directory, failure);
            file = null;
        }
        var capturedOut = new CapturedConsole(System.out, "stdout", Level.INFO);
        var capturedErr = new CapturedConsole(System.err, "stderr", Level.WARNING);
        stdout = capturedOut;
        stderr = capturedErr;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            capturedOut.finishLine();
            capturedErr.finishLine();
        }, "CoronaPoker-debug-console"));
        return file;
    }

    static void close() {
        if (stdout == null || stderr == null) return;
        System.setOut(stdout.console);
        System.setErr(stderr.console);
        stdout.finishLine();
        stderr.finishLine();
        stdout = null;
        stderr = null;
        DebugLog.install(record -> {});
        if (disk != null) {
            disk.close();
            disk = null;
        }
    }

    static Path debugDirectory(String userHome) {
        return Path.of(userHome, ".coronapoker", "Debug")
                .toAbsolutePath().normalize();
    }

    private static final class CapturedConsole extends OutputStream {

        private final PrintStream console;
        private final String source;
        private final Level level;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        CapturedConsole(PrintStream console, String source, Level level) {
            this.console = console;
            this.source = source;
            this.level = level;
        }

        @Override
        public synchronized void write(int value) {
            console.write(value);
            if ((value & 0xff) == '\n') publishLine();
            else pending.write(value);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length) {
            console.write(data, offset, length);
            int start = offset;
            int end = offset + length;
            for (int index = offset; index < end; index++) {
                if (data[index] != '\n') continue;
                pending.write(data, start, index - start);
                publishLine();
                start = index + 1;
            }
            pending.write(data, start, end - start);
        }

        @Override
        public void flush() {
            console.flush();
        }

        private synchronized void finishLine() {
            if (pending.size() != 0) publishLine();
        }

        private void publishLine() {
            String text = pending.toString(StandardCharsets.UTF_8);
            pending.reset();
            if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
            DebugLog.appendConsoleLine(source, level, text);
        }
    }

    private GdxDebugFile() {
    }
}
