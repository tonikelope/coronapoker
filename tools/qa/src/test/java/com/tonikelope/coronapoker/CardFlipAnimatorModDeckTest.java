package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class CardFlipAnimatorModDeckTest {

    @Test
    void rendersModDeckFaceFromDirectoryBesideJar() throws Exception {
        String deckName = "qa-mod-card-flip";
        Path deckDir = Paths.get(Helpers.getCurrentJarParentPath())
                .resolve("mod").resolve("decks").resolve(deckName);
        Files.createDirectories(deckDir);

        BufferedImage source = new BufferedImage(37, 53, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.MAGENTA);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        } finally {
            graphics.dispose();
        }
        ImageIO.write(source, "jpg", deckDir.resolve("A_C.jpg").toFile());
        ImageIO.write(source, "jpg", deckDir.resolve("trasera.jpg").toFile());

        String previousDeck = GameFrame.BARAJA;
        String previousBack = GameFrame.TRASERA;
        Card.BARAJAS.put(deckName, new Object[]{1.43f, true, null});
        try {
            GameFrame.BARAJA = deckName;
            GameFrame.TRASERA = "default";
            CardFlipAnimator.clearCache();

            PreRenderedGif animation = CardFlipAnimator.generate(
                    deckName, "A_C", 37, 53, 4, 150, 20, 1f, false);

            assertNotNull(animation, "the flip renderer must load mod faces from disk");
            assertEquals(20, animation.getFrameCount());
        } finally {
            GameFrame.BARAJA = previousDeck;
            GameFrame.TRASERA = previousBack;
            CardFlipAnimator.clearCache();
            Card.BARAJAS.remove(deckName);
            Files.deleteIfExists(deckDir.resolve("A_C.jpg"));
            Files.deleteIfExists(deckDir.resolve("trasera.jpg"));
            Files.deleteIfExists(deckDir);
        }
    }
}
