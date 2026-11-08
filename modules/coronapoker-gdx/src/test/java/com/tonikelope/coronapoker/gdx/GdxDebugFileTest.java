package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.DebugLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxDebugFileTest {

    @Test
    void usesTheSamePerUserDebugDirectoryAsCoronaPoker() {
        assertEquals(Path.of("C:/Users/tester/.coronapoker/Debug")
                .toAbsolutePath().normalize(),
                GdxDebugFile.debugDirectory("C:/Users/tester"));
    }

    @Test
    void debugFileAndPanelShareOrderedJulAndConsoleRecords(@TempDir Path temporary) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path processLog = temporary.resolve("console.txt");
        Process process = new ProcessBuilder(java, "-Duser.home=" + temporary,
                "-cp", classpath, LogProbe.class.getName(), temporary.toString())
                .redirectErrorStream(true).redirectOutput(processLog.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Logging probe timed out");
            assertEquals(0, process.exitValue(), () -> Files.exists(processLog)
                    ? processLog.toString() : "No console output");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        String panel = Files.readString(temporary.resolve("panel.txt"));
        String file = Files.readString(temporary.resolve("file.txt"));
        assertEquals(panel, file);
        assertTrue(panel.contains("stdout\nINFO: arranque de vídeo\n"));
        assertTrue(panel.contains("stderr\nWARNING: aviso de consola\n"));
        assertTrue(panel.contains("SEVERE: fallo de prueba\njava.lang.IllegalStateException: diagnóstico\n"));
        assertEquals(1, panel.lines().filter(line -> line.contains("fallo de prueba")).count());
        Path debugDirectory = GdxDebugFile.debugDirectory(temporary.toString());
        try (var files = Files.list(debugDirectory)) {
            String completed = Files.readString(files.findFirst().orElseThrow());
            assertTrue(completed.startsWith(panel));
            assertTrue(completed.endsWith("stderr\nWARNING: cierre sin salto de línea\n"));
        }
    }

    public static final class LogProbe {
        public static void main(String[] args) throws Exception {
            Locale.setDefault(Locale.forLanguageTag("es-ES"));
            Path file = GdxDebugFile.install();
            DebugLog.install();
            System.out.print("arranque ");
            System.out.println("de vídeo");
            System.err.println("aviso de consola");
            Logger.getLogger(LogProbe.class.getName()).log(Level.SEVERE,
                    "fallo de prueba", new IllegalStateException("diagnóstico"));
            Path temporary = Path.of(args[0]);
            Files.writeString(temporary.resolve("file.txt"), Files.readString(file));
            Files.writeString(temporary.resolve("panel.txt"), DebugLog.snapshot());
            System.err.print("cierre sin salto de línea");
        }
    }
}
