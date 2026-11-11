/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Packs the numbered chat artwork in wire order, with a sampling gutter. */
class PackChatEmoji {

    private static final int SIZE = 32;
    private static final int GUTTER = 1;
    private static final int COLUMNS = 12;
    private static final int ROWS = 12;
    private static final int STRIDE = SIZE + 2 * GUTTER;
    private static final int PAGE_SIZE = COLUMNS * ROWS;

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]);
        Path output = Files.createDirectories(Path.of(args[1]));
        int count;
        try (var files = Files.list(source)) {
            count = Math.toIntExact(files.filter(path -> path.getFileName()
                    .toString().matches("[1-9][0-9]*\\.png")).count());
        }
        if (count == 0) throw new IOException("Missing chat emoji artwork");
        for (int first = 1; first <= count; first += PAGE_SIZE) {
            int page = (first - 1) / PAGE_SIZE;
            BufferedImage atlas = new BufferedImage(COLUMNS * STRIDE,
                    ROWS * STRIDE, BufferedImage.TYPE_INT_ARGB);
            int slots = Math.min(PAGE_SIZE, count - first + 1);
            for (int slot = 0; slot < slots; slot++) {
                Path file = source.resolve((first + slot) + ".png");
                BufferedImage emoji = ImageIO.read(file.toFile());
                if (emoji == null || emoji.getWidth() != SIZE
                        || emoji.getHeight() != SIZE) {
                    throw new IOException("Invalid emoji source: " + file);
                }
                pack(emoji, atlas, slot % COLUMNS * STRIDE,
                        slot / COLUMNS * STRIDE);
            }
            if (!ImageIO.write(atlas, "PNG", output.resolve(page + ".png").toFile())) {
                throw new IOException("Missing PNG encoder");
            }
        }
        Files.writeString(output.resolve("layout.properties"),
                "count=" + count + "\ncolumns=" + COLUMNS + "\nrows=" + ROWS
                + "\nsize=" + SIZE + "\ngutter=" + GUTTER + "\n");
    }

    private static void pack(BufferedImage emoji, BufferedImage atlas, int x, int y) {
        var raster = emoji.getRaster();
        boolean grayscale = emoji.getColorModel().getColorSpace().getType()
                == ColorSpace.TYPE_GRAY;
        boolean hasAlpha = emoji.getColorModel().hasAlpha();
        for (int row = 0; row < STRIDE; row++) {
            int sourceY = Math.max(0, Math.min(SIZE - 1, row - GUTTER));
            for (int col = 0; col < STRIDE; col++) {
                int sourceX = Math.max(0, Math.min(SIZE - 1, col - GUTTER));
                int pixel;
                if (grayscale) {
                    // Preserve the stored samples, as libGDX's PNG decoder did.
                    int gray = raster.getSample(sourceX, sourceY, 0);
                    int alpha = hasAlpha ? raster.getSample(sourceX, sourceY, 1)
                            : 255;
                    pixel = (alpha << 24) | gray * 0x010101;
                } else {
                    pixel = emoji.getRGB(sourceX, sourceY);
                }
                atlas.setRGB(x + col, y + row, pixel);
            }
        }
    }
}
