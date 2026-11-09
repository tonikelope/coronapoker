/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.lwjgl.glfw.GLFW;

/** Keeps file and folder pickers consistent across the GDX frontend. */
final class GdxFileChooser {

    static CompletableFuture<Path> chooseDirectory(Path initial, String title, String approve) {
        return choose(initial, title, approve, JFileChooser.DIRECTORIES_ONLY, null);
    }

    static CompletableFuture<Path> chooseFile(Path initial, String title, String approve,
            String description, String... extensions) {
        return choose(initial, title, approve, JFileChooser.FILES_ONLY,
                new FileNameExtensionFilter(description, extensions));
    }

    /** Called on the GDX thread so GLFW attributes stay on their owning thread. */
    private static CompletableFuture<Path> choose(Path initial, String title, String approve,
            int selectionMode, FileNameExtensionFilter filter) {
        var application = Gdx.app;
        long window = Gdx.graphics instanceof Lwjgl3Graphics graphics
                ? graphics.getWindow().getWindowHandle() : 0;
        boolean preserveFullscreen = window != 0
                && GLFW.glfwGetWindowMonitor(window) != 0
                && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_AUTO_ICONIFY) == GLFW.GLFW_TRUE;
        if (preserveFullscreen) {
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        }

        CompletableFuture<Path> result = new CompletableFuture<>();
        EventQueue.invokeLater(() -> {
            try {
                result.complete(showChooser(initial, title, approve, selectionMode, filter));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        if (!preserveFullscreen) return result;
        return result.whenCompleteAsync((path, failure) ->
                GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_TRUE),
                application::postRunnable);
    }

    private static Path showChooser(Path initial, String title, String approve,
            int selectionMode, FileNameExtensionFilter filter) {
        JFileChooser chooser = new JFileChooser(initial.toFile()) {
            @Override
            protected JDialog createDialog(Component parent) {
                JDialog dialog = super.createDialog(parent);
                dialog.setAlwaysOnTop(true);
                sizeForScreen(dialog);
                return dialog;
            }
        };
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(selectionMode);
        chooser.setAcceptAllFileFilterUsed(false);
        if (filter != null) chooser.setFileFilter(filter);
        if (chooser.showDialog(null, approve) != JFileChooser.APPROVE_OPTION
                || chooser.getSelectedFile() == null) return null;
        return chooser.getSelectedFile().toPath();
    }

    private static void sizeForScreen(JDialog dialog) {
        var pointer = MouseInfo.getPointerInfo();
        GraphicsConfiguration screen = pointer == null
                ? dialog.getGraphicsConfiguration() : pointer.getDevice().getDefaultConfiguration();
        Rectangle area = screen.getBounds();
        Insets insets = dialog.getToolkit().getScreenInsets(screen);
        int availableWidth = area.width - insets.left - insets.right;
        int availableHeight = area.height - insets.top - insets.bottom;
        int width = Math.min(availableWidth,
                Math.max(dialog.getWidth(), Math.round(availableWidth * 0.55f)));
        int height = Math.min(availableHeight,
                Math.max(dialog.getHeight(), Math.round(availableHeight * 0.62f)));
        dialog.setBounds(area.x + insets.left + (availableWidth - width) / 2,
                area.y + insets.top + (availableHeight - height) / 2, width, height);
    }

    private GdxFileChooser() {
    }
}
