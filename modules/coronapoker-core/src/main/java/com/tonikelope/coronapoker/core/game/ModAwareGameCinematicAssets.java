/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** External-mod-first cinematic catalog with the same fusion semantics as Swing. */
public final class ModAwareGameCinematicAssets implements GameCinematicAssets {

    private final ModMediaCatalog mod;
    private final GameCinematicAssets bundled;
    private final List<String> filenames;
    private final ConcurrentHashMap<String, Long> durations =
            new ConcurrentHashMap<>();

    public ModAwareGameCinematicAssets(ModMediaCatalog mod,
            GameCinematicAssets bundled, List<String> bundledFilenames) {
        this.mod = Objects.requireNonNull(mod, "mod");
        this.bundled = Objects.requireNonNull(bundled, "bundled");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Path path : mod.files("cinematics/allin", ".gif")) {
            names.add(path.getFileName().toString());
        }
        if (mod.fuseCinematics()) {
            names.addAll(Objects.requireNonNull(bundledFilenames,
                    "bundledFilenames"));
        }
        filenames = List.copyOf(names);
    }

    @Override
    public long durationMillis(String filename) throws Exception {
        Path external = external(filename);
        if (external != null) {
            Long cached = durations.get(filename);
            if (cached != null) return cached;
            long measured = measureGif(external);
            durations.put(filename, measured);
            return measured;
        }
        return mod.fuseCinematics() ? bundled.durationMillis(filename) : 0L;
    }

    @Override
    public boolean hasCinematic(String filename) {
        return external(filename) != null
                || (mod.fuseCinematics() && bundled.hasCinematic(filename));
    }

    @Override
    public boolean hasCompanionAudio(String filename) {
        String wav = validFilename(filename)
                ? filename.replaceFirst("(?i)\\.gif$", ".wav") : "";
        return !wav.isEmpty()
                && (mod.resolve("cinematics/allin/" + wav).isPresent()
                || (mod.fuseCinematics()
                && bundled.hasCompanionAudio(filename)));
    }

    @Override
    public List<String> filenames() {
        return filenames;
    }

    private Path external(String filename) {
        return validFilename(filename)
                ? mod.resolve("cinematics/allin/" + filename).orElse(null)
                : null;
    }

    private static boolean validFilename(String filename) {
        return filename != null && !filename.isBlank()
                && !filename.contains("/") && !filename.contains("\\");
    }

    private static long measureGif(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path);
                ImageInputStream images = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers =
                    ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return 0L;
            ImageReader reader = readers.next();
            try {
                reader.setInput(images, false, false);
                long duration = 0L;
                for (int index = 0; index < reader.getNumImages(true); index++) {
                    duration += frameDelayMillis(reader.getImageMetadata(index));
                }
                return duration;
            } finally {
                reader.dispose();
            }
        }
    }

    private static long frameDelayMillis(IIOMetadata metadata) {
        Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
        for (Node child = root.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            if (!"GraphicControlExtension".equals(child.getNodeName())) continue;
            NamedNodeMap attributes = child.getAttributes();
            Node delay = attributes == null ? null
                    : attributes.getNamedItem("delayTime");
            try {
                int centiseconds = delay == null ? 10
                        : Integer.parseInt(delay.getNodeValue());
                return centiseconds <= 1 ? 100L : centiseconds * 10L;
            } catch (NumberFormatException ignored) {
                return 100L;
            }
        }
        return 100L;
    }
}
