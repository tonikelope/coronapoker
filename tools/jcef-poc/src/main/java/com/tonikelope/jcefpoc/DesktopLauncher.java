package com.tonikelope.jcefpoc;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import java.util.Arrays;
import java.util.Locale;

/** Process entry point for the standalone JCEF/libGDX proof of concept. */
public final class DesktopLauncher {
    private DesktopLauncher() {}

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--help")) {
            printUsage();
            return;
        }

        PocConfig config;
        try {
            config = PocConfig.load(args);
        } catch (Exception failure) {
            System.err.println("Configuración no válida: " + failure.getMessage());
            printUsage();
            return;
        }

        warnAboutMacLauncher();
        System.out.println("CoronaPoker JCEF OSR PoC");
        System.out.println("Vídeo: " + config.videoId());
        System.out.println("Datos JCEF: " + config.dataDirectory());
        System.out.println("Inicializando Chromium; la primera extracción puede tardar unos segundos…");

        ChromiumFrameBuffer frameBuffer = new ChromiumFrameBuffer();
        JcefRuntime runtime = JcefRuntime.initialize(config, frameBuffer);

        Lwjgl3ApplicationConfiguration window = new Lwjgl3ApplicationConfiguration();
        window.setTitle("CoronaPoker // JCEF OSR PoC");
        window.setWindowedMode(1280, 720);
        window.setWindowSizeLimits(960, 540, -1, -1);
        window.setResizable(true);
        window.useVsync(true);
        window.setForegroundFPS(60);
        window.setIdleFPS(20);
        window.setHdpiMode(HdpiMode.Pixels);
        window.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
        new Lwjgl3Application(new JcefPocApp(runtime), window);
    }

    private static void warnAboutMacLauncher() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") && !Boolean.getBoolean("jcef.poc.mac.firstThread")) {
            System.err.println("macOS: inicia con scripts/run-macos.sh; LWJGL3 requiere "
                    + "-XstartOnFirstThread.");
        }
    }

    private static void printUsage() {
        System.out.println("""
                Uso:
                  java -jar coronapoker-jcef-poc-...jar [URL_O_ID]
                  java -jar ... --video URL_O_ID [--config archivo.properties]

                Opciones:
                  --video <url|id>          Vídeo de YouTube (predeterminado M7lc1UVf-VE)
                  --config <ruta>           Archivo .properties opcional
                  --page-url <https-url>    Página HTTPS externa en vez del servidor local
                  --jcef-dir <ruta>         Directorio de extracción/caché de JCEF
                  --browser-width <px>      Anchura inicial (200-4096)
                  --browser-height <px>     Altura inicial (200-4096)
                  --help                    Muestra esta ayuda
                """);
    }
}
