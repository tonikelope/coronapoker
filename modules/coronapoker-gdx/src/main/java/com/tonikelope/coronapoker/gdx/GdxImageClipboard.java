/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.sun.jna.Platform;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Desktop clipboard boundary kept outside the renderer and Swing UI. */
final class GdxImageClipboard {

    static final Executor COPY_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "coronapoker-gdx-copy-screenshot");
        thread.setDaemon(true);
        return thread;
    });
    private static final long PNG_SIGNATURE = 0x89504e470d0a1a0aL;
    private static volatile Clipboard systemClipboard;

    private GdxImageClipboard() {
    }

    static boolean copy(Image image) {
        return image != null && publish(new ImageTransferable(image, null));
    }

    static boolean copy(Path file) throws IOException {
        if (file == null) return false;
        if (Platform.isWindows()) {
            byte[] encoded = Files.readAllBytes(file);
            if (encoded.length >= Long.BYTES && ByteBuffer.wrap(encoded).getLong() == PNG_SIGNATURE) {
                return publish(new ImageTransferable(decodePng(encoded), encoded));
            }
        }
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Unsupported screenshot image");
        return copy(image);
    }

    private static BufferedImage decodePng(byte[] encoded) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unsupported screenshot image");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, false);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean publish(Transferable contents) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                clipboard().setContents(contents, null);
                return true;
            } catch (IllegalStateException clipboardBusy) {
                if (attempt == 2) return false;
                LockSupport.parkNanos(12_000_000L);
            } catch (RuntimeException failure) {
                return false;
            }
        }
        return false;
    }

    private static Clipboard clipboard() {
        Clipboard current = systemClipboard;
        if (current != null) return current;
        synchronized (GdxImageClipboard.class) {
            if (systemClipboard == null) {
                systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            }
            return systemClipboard;
        }
    }

    private static final class WindowsPng {
        private static final DataFlavor FLAVOR = register();

        private static DataFlavor register() {
            DataFlavor png = new DataFlavor(
                    "application/x-coronapoker-png;class=\"[B\"", "Original PNG");
            var flavors = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
            flavors.addUnencodedNativeForFlavor(png, "PNG");
            return png;
        }
    }

    private record ImageTransferable(Image image, byte[] originalPng) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            if (originalPng == null) return new DataFlavor[] {DataFlavor.imageFlavor};
            // AWT assigns the first flavor to PNG and uses imageFlavor for the other formats.
            return new DataFlavor[] {WindowsPng.FLAVOR, DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor)
                    || (originalPng != null && WindowsPng.FLAVOR.equals(flavor));
        }

        @Override
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            if (originalPng != null && WindowsPng.FLAVOR.equals(flavor)) return originalPng.clone();
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
