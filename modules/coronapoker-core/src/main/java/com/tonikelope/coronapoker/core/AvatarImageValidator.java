package com.tonikelope.coronapoker.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Validates untrusted avatar metadata before a renderer decodes image pixels.
 */
public final class AvatarImageValidator {

    public static final int MAX_DECODED_BYTES = 256 * 1024;
    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_FRAME_PIXELS = 4_194_304L;
    public static final int MAX_FRAMES = 32;
    public static final long MAX_TOTAL_FRAME_PIXELS = 8_388_608L;

    private static final Set<String> ALLOWED_FORMATS = Set.of("PNG", "JPEG", "GIF");

    private AvatarImageValidator() {
    }

    public static void validate(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("Avatar file is unavailable");
        }
        long size = Files.size(path);
        if (size <= 0L || size > MAX_DECODED_BYTES) {
            throw new IOException("Avatar exceeds size limit");
        }
        validate(Files.readAllBytes(path));
    }

    public static void validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_DECODED_BYTES) {
            throw new IOException("Avatar exceeds size limit");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Unable to inspect avatar image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported avatar image");

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) {
                    throw new IOException("Unsupported avatar format: " + format);
                }
                reader.setInput(input, false, true);
                int frames = reader.getNumImages(true);
                if (frames < 1 || frames > MAX_FRAMES) {
                    throw new IOException("Avatar frame count exceeds limit");
                }
                int[] gifCanvas = "GIF".equals(format)
                        ? validateGifCanvas(reader.getStreamMetadata()) : null;
                long canvasPixels = gifCanvas == null ? 0L
                        : Math.multiplyExact((long) gifCanvas[0], (long) gifCanvas[1]);
                long totalPixels = 0L;
                for (int index = 0; index < frames; index++) {
                    int width = reader.getWidth(index);
                    int height = reader.getHeight(index);
                    validateExtent(width, height, "frame");
                    long retainedPixels = gifCanvas == null
                            ? Math.multiplyExact((long) width, (long) height) : canvasPixels;
                    totalPixels = Math.addExact(totalPixels, retainedPixels);
                    if (totalPixels > MAX_TOTAL_FRAME_PIXELS) {
                        throw new IOException("Avatar total pixels exceed limit");
                    }
                    if (gifCanvas != null) {
                        validateGifFrame(reader.getImageMetadata(index), width, height,
                                gifCanvas[0], gifCanvas[1]);
                    }
                }
            } catch (ArithmeticException failure) {
                throw new IOException("Avatar dimensions overflow", failure);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void validateExtent(int width, int height, String label) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_FRAME_PIXELS) {
            throw new IOException("Avatar " + label + " dimensions exceed limit");
        }
    }

    private static int[] validateGifCanvas(IIOMetadata metadata) throws IOException {
        Node descriptor = findNode(metadata, "javax_imageio_gif_stream_1.0",
                "LogicalScreenDescriptor");
        if (descriptor == null) throw new IOException("GIF logical canvas metadata is missing");
        int width = intAttribute(descriptor, "logicalScreenWidth");
        int height = intAttribute(descriptor, "logicalScreenHeight");
        validateExtent(width, height, "GIF canvas");
        return new int[]{width, height};
    }

    private static void validateGifFrame(IIOMetadata metadata, int width, int height,
            int canvasWidth, int canvasHeight) throws IOException {
        Node descriptor = findNode(metadata, "javax_imageio_gif_image_1.0",
                "ImageDescriptor");
        if (descriptor == null) throw new IOException("GIF frame metadata is missing");
        long left = intAttribute(descriptor, "imageLeftPosition");
        long top = intAttribute(descriptor, "imageTopPosition");
        if (left < 0 || top < 0 || left + width > canvasWidth || top + height > canvasHeight) {
            throw new IOException("GIF frame lies outside bounded canvas");
        }
    }

    private static Node findNode(IIOMetadata metadata, String format, String name)
            throws IOException {
        if (metadata == null) return null;
        try {
            return findNode(metadata.getAsTree(format), name);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Malformed avatar metadata", failure);
        }
    }

    private static Node findNode(Node node, String name) {
        if (node == null) return null;
        if (name.equals(node.getNodeName())) return node;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static int intAttribute(Node node, String name) throws IOException {
        NamedNodeMap attributes = node.getAttributes();
        Node attribute = attributes == null ? null : attributes.getNamedItem(name);
        if (attribute == null) throw new IOException("Missing avatar metadata attribute " + name);
        try {
            return Integer.parseInt(attribute.getNodeValue());
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid avatar metadata attribute " + name, failure);
        }
    }
}
