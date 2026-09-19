package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxFrontendAvatarSelectionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsARealImageWithinTheWireLimit() throws Exception {
        Path avatar = temporaryDirectory.resolve("avatar.png");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB),
                "png", avatar.toFile());

        assertTrue(GdxFrontendScreen.isSupportedAvatar(avatar));
    }

    @Test
    void rejectsMissingMalformedAndOversizedFiles() throws Exception {
        Path malformed = temporaryDirectory.resolve("malformed.png");
        Files.writeString(malformed, "not an image");
        Path oversized = temporaryDirectory.resolve("oversized.png");
        Files.write(oversized,
                new byte[(int) NewGameConnectionDraft.MAX_AVATAR_BYTES + 1]);

        assertFalse(GdxFrontendScreen.isSupportedAvatar(
                temporaryDirectory.resolve("missing.png")));
        assertFalse(GdxFrontendScreen.isSupportedAvatar(malformed));
        assertFalse(GdxFrontendScreen.isSupportedAvatar(oversized));
    }
}
