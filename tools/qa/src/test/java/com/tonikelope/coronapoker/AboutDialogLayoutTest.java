package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AboutDialogLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    void modBrandingUsesOnlyTheImage() throws Exception {
        Path logoPath = tempDir.resolve("mod.png");
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB), "png", logoPath.toFile());
        JLabel mod = new JLabel("Chilean MOD 0.57");

        assertTrue(AboutDialog.configureModLogo(mod, logoPath));
        assertNull(mod.getText(), "the MOD name and version already belong in the window title");
        assertNotNull(mod.getIcon());
        assertTrue(mod.isVisible());
    }

    @Test
    void missingModImageLeavesNoLabelOrLayoutPlaceholder() {
        JLabel mod = new JLabel("Chilean MOD 0.57");

        assertFalse(AboutDialog.configureModLogo(mod, tempDir.resolve("missing.png")));
        assertNull(mod.getText());
        assertNull(mod.getIcon());
        assertFalse(mod.isVisible());
    }

    @Test
    void modProgressVisibilityDoesNotChangeBrandingHeight() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                JPanel panel = new JPanel();
                JLabel corona = sizedLabel(90, 110);
                JLabel mod = sizedLabel(250, AboutDialog.MAX_MOD_LOGO_HEIGHT);
                JProgressBar progress = new JProgressBar();
                progress.setPreferredSize(new Dimension(433, 19));
                progress.setVisible(false);
                mod.setVisible(true);
                addBrandingComponents(panel, corona, mod, progress);

                AboutDialog.configureBrandingLayout(panel, corona, mod, progress);
                int withoutProgress = panel.getPreferredSize().height;

                progress.setVisible(true);
                panel.revalidate();
                int withProgress = panel.getPreferredSize().height;

                assertEquals(withoutProgress, withProgress,
                        "showing the MOD update progress must not move the credits");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        rethrow(failure.get());
    }

    @Test
    void coronaLogoIsCenteredWhenNoModIsPresent() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                JPanel panel = new JPanel();
                JLabel corona = sizedLabel(90, 110);
                JLabel mod = sizedLabel(250, AboutDialog.MAX_MOD_LOGO_HEIGHT);
                JProgressBar progress = new JProgressBar();
                progress.setPreferredSize(new Dimension(433, 19));
                mod.setVisible(false);
                progress.setVisible(false);
                addBrandingComponents(panel, corona, mod, progress);

                AboutDialog.configureBrandingLayout(panel, corona, mod, progress);
                panel.setSize(panel.getPreferredSize());
                panel.doLayout();

                assertEquals(panel.getWidth() / 2, corona.getX() + corona.getWidth() / 2,
                        "the built-in logo must stay centered when no MOD is loaded");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        rethrow(failure.get());
    }

    @Test
    void viewportWidthIsSynchronizedImmediately() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                JPanel content = new JPanel();
                content.setPreferredSize(new Dimension(804, 600));
                JScrollPane scroll = new JScrollPane(content);
                scroll.setSize(500, 300);
                scroll.doLayout();

                Helpers.trackViewportWidth(scroll);

                assertEquals(scroll.getViewport().getExtentSize().width,
                        content.getPreferredSize().width,
                        "the first pack must not retain the form's fixed width");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        rethrow(failure.get());
    }

    private static JLabel sizedLabel(int width, int height) {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(width, height));
        return label;
    }

    private static void addBrandingComponents(JPanel panel, JLabel corona,
            JLabel mod, JProgressBar progress) {
        panel.add(corona);
        panel.add(mod);
        panel.add(progress);
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new AssertionError(failure);
    }
}
