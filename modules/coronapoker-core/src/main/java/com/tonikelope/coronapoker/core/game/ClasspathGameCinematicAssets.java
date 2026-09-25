/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Read-only cinematic catalog backed by bundled classpath resources. */
public final class ClasspathGameCinematicAssets implements GameCinematicAssets {

    private final String resourceDirectory;
    private final ConcurrentHashMap<String, Long> durations =
            new ConcurrentHashMap<>();

    public ClasspathGameCinematicAssets(String resourceDirectory) {
        Objects.requireNonNull(resourceDirectory, "resourceDirectory");
        this.resourceDirectory = resourceDirectory.endsWith("/")
                ? resourceDirectory : resourceDirectory + "/";
    }

    @Override
    public long durationMillis(String filename) throws Exception {
        Long cached = durations.get(filename);
        if (cached != null) {
            return cached;
        }
        long measured = measureGif(resourceName(filename));
        durations.put(filename, measured);
        return measured;
    }

    @Override
    public boolean hasCinematic(String filename) {
        return resource(filename) != null;
    }

    @Override
    public boolean hasCompanionAudio(String filename) {
        String wav = filename.replaceFirst("(?i)\\.gif$", ".wav");
        return resource(wav) != null;
    }

    private String resourceName(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("invalid cinematic filename");
        }
        return resourceDirectory + filename;
    }

    private java.net.URL resource(String filename) {
        try {
            return getClass().getClassLoader().getResource(resourceName(filename));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private long measureGif(String resourceName) throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (input == null) {
                return 0L;
            }
            try (ImageInputStream images = ImageIO.createImageInputStream(input)) {
                Iterator<ImageReader> readers =
                        ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) {
                    return 0L;
                }
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
    }

    private static long frameDelayMillis(IIOMetadata metadata) {
        Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
        for (Node child = root.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            if (!"GraphicControlExtension".equals(child.getNodeName())) {
                continue;
            }
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
