package com.tonikelope.coronapoker.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AvatarImageValidatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsBoundedPngJpegGifAndPath() throws Exception {
        assertDoesNotThrow(() -> AvatarImageValidator.validate(image("png", 8, 6)));
        assertDoesNotThrow(() -> AvatarImageValidator.validate(image("jpg", 5, 7)));
        assertDoesNotThrow(() -> AvatarImageValidator.validate(image("gif", 4, 3)));
        Path avatar = temporaryDirectory.resolve("avatar.png");
        Files.write(avatar, image("png", 3, 2));
        assertDoesNotThrow(() -> AvatarImageValidator.validate(avatar));
    }

    @Test
    void rejectsMissingEmptyOversizedAndMalformedInput() throws Exception {
        assertThrows(IOException.class, () -> AvatarImageValidator.validate((byte[]) null));
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(new byte[0]));
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(
                new byte[AvatarImageValidator.MAX_DECODED_BYTES + 1]));
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(
                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(
                temporaryDirectory.resolve("missing.png")));
    }

    @Test
    void rejectsHugePngDimensionsBeforeRasterization() throws Exception {
        byte[] png = image("png", 1, 1);
        putInt(png, 16, 65_536);
        putInt(png, 20, 65_536);
        rewriteIhdrCrc(png);
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(png));
    }

    @Test
    void rejectsHugeGifCanvasAndExcessiveRetainedPixels() throws Exception {
        byte[] huge = image("gif", 1, 1);
        setGifLogicalCanvas(huge, 32_767, 32_767);
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(huge));

        byte[] repeated = image("gif", 1, 1);
        setGifLogicalCanvas(repeated, 2048, 2048);
        byte[] threeFrames = repeatGifFrame(repeated, 3);
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(threeFrames));
    }

    @Test
    void rejectsTooManyGifFrames() throws Exception {
        byte[] gif = repeatGifFrame(image("gif", 1, 1),
                AvatarImageValidator.MAX_FRAMES + 1);
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(gif));
    }

    @Test
    void rejectsGifFrameOutsideLogicalCanvas() throws Exception {
        byte[] gif = image("gif", 1, 1);
        setGifLogicalCanvas(gif, 10, 10);
        int descriptor = indexOf(gif, (byte) 0x2c);
        gif[descriptor + 1] = 10;
        gif[descriptor + 2] = 0;
        assertThrows(IOException.class, () -> AvatarImageValidator.validate(gif));
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void rewriteIhdrCrc(byte[] png) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(png, 12, 17);
        putInt(png, 29, (int) crc.getValue());
    }

    private static byte[] repeatGifFrame(byte[] gif, int count) throws IOException {
        int descriptor = indexOf(gif, (byte) 0x2c);
        int trailer = gif.length - 1;
        assertEquals(0x3b, gif[trailer] & 0xff);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(gif, 0, descriptor);
        for (int index = 0; index < count; index++) {
            out.write(gif, descriptor, trailer - descriptor);
        }
        out.write(0x3b);
        return out.toByteArray();
    }

    private static void setGifLogicalCanvas(byte[] gif, int width, int height) {
        gif[6] = (byte) width;
        gif[7] = (byte) (width >>> 8);
        gif[8] = (byte) height;
        gif[9] = (byte) (height >>> 8);
    }

    private static int indexOf(byte[] bytes, byte wanted) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == wanted) return index;
        }
        throw new AssertionError("byte not found");
    }
}
