/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;
import javax.imageio.ImageIO;

/** Desktop clipboard boundary kept outside the renderer and Swing UI. */
final class GdxImageClipboard {

    private static final Object PRELOAD_LOCK = new Object();
    private static volatile PreparedImage preparedImage;
    private static volatile Clipboard systemClipboard;

    private GdxImageClipboard() {
    }

    static boolean copy(Image image) {
        if (image == null) return false;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                clipboard().setContents(new ImageTransferable(image), null);
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

    /**
     * Starts decoding the selected PNG and initializes AWT while the player is
     * looking at it. Copying then only publishes the already decoded pixels,
     * instead of decoding the same multi-megapixel PNG a second time on click.
     */
    static void prepare(Path file) {
        if (file == null) return;
        try {
            prepared(file);
        } catch (IOException ignored) {
            // The foreground copy reports the real error if the file vanished.
        }
    }

    static boolean copy(Path file) throws IOException {
        if (file == null) return false;
        CompletableFuture<BufferedImage> image = prepared(file).image();
        try {
            return copy(image.get());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while preparing screenshot",
                    interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException ioFailure) throw ioFailure;
            throw new IOException("Unable to decode screenshot", cause);
        }
    }

    static void discard(Path file) {
        if (file == null) return;
        Path normalized = file.toAbsolutePath().normalize();
        synchronized (PRELOAD_LOCK) {
            if (preparedImage != null
                    && preparedImage.source().path().equals(normalized)) {
                preparedImage = null;
            }
        }
    }

    private static PreparedImage prepared(Path file) throws IOException {
        ImageSource source = new ImageSource(file.toAbsolutePath().normalize(),
                Files.size(file), Files.getLastModifiedTime(file).toMillis());
        synchronized (PRELOAD_LOCK) {
            if (preparedImage != null
                    && preparedImage.source().equals(source)) {
                return preparedImage;
            }
            CompletableFuture<BufferedImage> decoded = new CompletableFuture<>();
            PreparedImage replacement = new PreparedImage(source, decoded);
            preparedImage = replacement;
            Thread worker = new Thread(() -> decode(source, decoded),
                    "coronapoker-gdx-clipboard-preload");
            worker.setDaemon(true);
            worker.start();
            return replacement;
        }
    }

    private static void decode(ImageSource source,
            CompletableFuture<BufferedImage> target) {
        try {
            try {
                clipboard();
            } catch (RuntimeException ignored) {
                // Image decoding remains useful; copy() will report clipboard
                // availability when the user actually requests it.
            }
            BufferedImage image = ImageIO.read(source.path().toFile());
            if (image == null) {
                throw new IOException("Unsupported screenshot image");
            }
            target.complete(image);
        } catch (Throwable failure) {
            target.completeExceptionally(failure);
        }
    }

    private static Clipboard clipboard() {
        Clipboard current = systemClipboard;
        if (current != null) return current;
        synchronized (PRELOAD_LOCK) {
            if (systemClipboard == null) {
                systemClipboard = Toolkit.getDefaultToolkit()
                        .getSystemClipboard();
            }
            return systemClipboard;
        }
    }

    private record ImageSource(Path path, long size, long modifiedMillis) {
    }

    private record PreparedImage(ImageSource source,
            CompletableFuture<BufferedImage> image) {
    }

    private static final class ImageTransferable implements Transferable {

        private final Image image;

        private ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
