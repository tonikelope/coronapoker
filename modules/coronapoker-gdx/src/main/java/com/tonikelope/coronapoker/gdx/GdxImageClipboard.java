/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/** Desktop clipboard boundary kept outside the renderer and Swing UI. */
final class GdxImageClipboard {

    private GdxImageClipboard() {
    }

    static boolean copy(Image image) {
        if (image == null) return false;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new ImageTransferable(image), null);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
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
