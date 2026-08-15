package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvatarIOTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsSmallImageAndStoresExactBytesInsideOwnedDirectory() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        byte[] png = image("png", 8, 6);

        File stored = store.decodeValidateStore(Base64.getEncoder().encodeToString(png));

        assertNotNull(stored);
        assertTrue(stored.toPath().normalize().startsWith(store.getSessionDirectory()));
        assertArrayEquals(png, Files.readAllBytes(stored.toPath()));
    }

    @Test
    void acceptsSmallJpegAndGifAndTreatsSentinelAsNoAvatar() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);

        assertNotNull(store.decodeValidateStore(Base64.getEncoder().encodeToString(image("jpg", 5, 7))));
        assertNotNull(store.decodeValidateStore(Base64.getEncoder().encodeToString(image("gif", 4, 3))));
        assertNull(store.decodeValidateStore("*"));
        assertNull(store.decodeValidateStore(""));
    }

    @Test
    void rejectsEncodedAndDecodedSizeBeforeCreatingAFile() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        long before = fileCount(store.getSessionDirectory());

        assertThrows(IOException.class,
                () -> store.decodeValidateStore("A".repeat(AvatarIO.MAX_ENCODED_CHARS + 1)));
        byte[] oversized = new byte[AvatarIO.MAX_DECODED_BYTES + 1];
        assertThrows(IOException.class,
                () -> store.decodeValidateStore(Base64.getEncoder().encodeToString(oversized)));
        assertEquals(before, fileCount(store.getSessionDirectory()));
    }

    @Test
    void rejectsMalformedBase64AndNonImagesWithoutLeavingFiles() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        long before = fileCount(store.getSessionDirectory());

        assertThrows(IOException.class, () -> store.decodeValidateStore("%%%not-base64%%%"));
        assertThrows(IOException.class, () -> store.decodeValidateStore(
                Base64.getEncoder().encodeToString("not an image".getBytes("UTF-8"))));
        assertEquals(before, fileCount(store.getSessionDirectory()));
    }

    @Test
    void rejectsHugePngDimensionsBeforeRasterization() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        byte[] png = image("png", 1, 1);
        // PNG IHDR width/height are big-endian at offsets 16 and 20. The ImageIO
        // reader exposes them from metadata before touching compressed pixels.
        putInt(png, 16, 65_536);
        putInt(png, 20, 65_536);
        rewriteIhdrCrc(png);

        assertThrows(IOException.class,
                () -> store.decodeValidateStore(Base64.getEncoder().encodeToString(png)));
        assertEquals(0, fileCount(store.getSessionDirectory()));
    }

    @Test
    void rejectsHugeGifLogicalCanvasEvenWhenFrameIsTiny() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        byte[] gif = image("gif", 1, 1);
        // GIF logical screen width/height are little-endian at offsets 6 and 8.
        gif[6] = (byte) 0xff;
        gif[7] = (byte) 0x7f;
        gif[8] = (byte) 0xff;
        gif[9] = (byte) 0x7f;

        assertThrows(IOException.class,
                () -> store.decodeValidateStore(Base64.getEncoder().encodeToString(gif)));
        assertEquals(0, fileCount(store.getSessionDirectory()));
    }

    @Test
    void rejectsTooManyGifFramesWithoutRasterizingThem() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        byte[] gif = repeatGifFrame(image("gif", 1, 1), AvatarIO.MAX_FRAMES + 1);

        assertThrows(IOException.class,
                () -> store.decodeValidateStore(Base64.getEncoder().encodeToString(gif)));
        assertEquals(0, fileCount(store.getSessionDirectory()));
    }

    @Test
    void rejectsExcessiveGifPixelsAcrossFrames() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        byte[] gif = image("gif", 1, 1);
        setGifLogicalCanvas(gif, 2048, 2048);
        final byte[] repeatedGif = repeatGifFrame(gif, 3);

        assertThrows(IOException.class,
                () -> store.decodeValidateStore(Base64.getEncoder().encodeToString(repeatedGif)));
        assertEquals(0, fileCount(store.getSessionDirectory()));
    }

    @Test
    void rejectsGifFrameOutsideItsLogicalCanvas() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        byte[] gif = image("gif", 1, 1);
        setGifLogicalCanvas(gif, 10, 10);
        int imageDescriptor = indexOf(gif, (byte) 0x2c);
        gif[imageDescriptor + 1] = 10; // left=10, width=1 => outside [0,10)
        gif[imageDescriptor + 2] = 0;

        assertThrows(IOException.class,
                () -> store.decodeValidateStore(Base64.getEncoder().encodeToString(gif)));
        assertEquals(0, fileCount(store.getSessionDirectory()));
    }

    @Test
    void deletesOnlyOwnedAvatarFiles() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        File owned = store.decodeValidateStore(Base64.getEncoder().encodeToString(image("png", 2, 2)));
        Path unrelated = Files.write(tempDir.resolve("local-avatar.png"), image("png", 2, 2));

        assertTrue(store.deleteOwned(owned));
        assertFalse(owned.exists());
        assertFalse(store.deleteOwned(unrelated.toFile()));
        assertTrue(Files.exists(unrelated));
    }

    @Test
    void repeatedAvatarAndThumbnailCleanupDoesNotGrowSessionDirectory() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        String encoded = Base64.getEncoder().encodeToString(image("png", 2, 2));

        for (int i = 0; i < 50; i++) {
            File avatar = store.decodeValidateStore(encoded);
            store.writeThumbnail(avatar, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            store.deleteAvatarArtifacts(avatar);
        }

        assertEquals(0, fileCount(store.getSessionDirectory()));
    }

    @Test
    void closeRemovesTheOwnedSessionDirectoryAndNeverTheParent() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        File avatar = store.decodeValidateStore(Base64.getEncoder().encodeToString(image("png", 2, 2)));
        store.writeThumbnail(avatar, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        Path session = store.getSessionDirectory();

        store.close();

        assertFalse(Files.exists(session));
        assertTrue(Files.exists(tempDir));
    }

    @Test
    void closeSerializesAgainstConcurrentWritersAndFailsClosedAfterwards() throws Exception {
        AvatarIO store = new AvatarIO(tempDir);
        Path session = store.getSessionDirectory();
        String encoded = Base64.getEncoder().encodeToString(image("png", 2, 2));
        BufferedImage thumbnail = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(9);
        List<Future<?>> writers = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                writers.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 25; i++) {
                        try {
                            File avatar = store.decodeValidateStore(encoded);
                            store.writeThumbnail(avatar, thumbnail);
                        } catch (IOException expectedAfterClose) {
                            break;
                        }
                    }
                    return null;
                }));
            }
            Future<?> closer = pool.submit(() -> {
                start.await();
                store.close();
                return null;
            });
            start.countDown();
            closer.get();
            for (Future<?> writer : writers) {
                writer.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertFalse(Files.exists(session));
        assertThrows(IOException.class, () -> store.decodeValidateStore(encoded));
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    private static long fileCount(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void rewriteIhdrCrc(byte[] png) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(png, 12, 17); // "IHDR" + its 13-byte payload
        putInt(png, 29, (int) crc.getValue());
    }

    private static byte[] repeatGifFrame(byte[] gif, int count) throws IOException {
        int imageDescriptor = indexOf(gif, (byte) 0x2c);
        int trailer = gif.length - 1;
        assertEquals(0x3b, gif[trailer] & 0xff);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(gif, 0, imageDescriptor);
        for (int i = 0; i < count; i++) {
            out.write(gif, imageDescriptor, trailer - imageDescriptor);
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
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == wanted) {
                return i;
            }
        }
        throw new AssertionError("byte not found");
    }
}
