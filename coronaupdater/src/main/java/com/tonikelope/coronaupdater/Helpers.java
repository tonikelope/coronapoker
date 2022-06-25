/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronaupdater;

import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 *
 * @author tonikelope
 */
public class Helpers {

    public volatile static Font GUI_FONT = null;

    public static void deleteDirectory(String path) {
        try ( var dirStream = Files.walk(Paths.get(path))) {
            dirStream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
        } catch (IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void openBrowserURLAndWait(final String url) {

        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (URISyntaxException | IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex.getMessage());
        }

    }

    public static void mostrarMensajeError(JFrame frame, String msg) {

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(frame, msg, "ERROR", JOptionPane.ERROR_MESSAGE);

        } else {

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(frame, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
                }
            });
        }

    }

    // 0=yes, 1=no, 2=cancel
    public static int mostrarMensajeErrorSINO(JFrame frame, String msg) {

        if (SwingUtilities.isEventDispatchThread()) {

            return JOptionPane.showConfirmDialog(frame, msg, "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);

        } else {

            final int[] res = new int[1];

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    res[0] = JOptionPane.showConfirmDialog(frame, msg, "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                }
            });

            return res[0];

        }
    }

    public static void mostrarMensajeInformativo(JFrame frame, String msg) {

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(frame, msg);

        } else {
            GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(frame, msg);
                }
            });
        }
    }

    public static void centrarJFrame(JFrame window) {

        GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();

                int topLeftX, topLeftY, screenX, screenY, windowPosX, windowPosY;

                topLeftX = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().x;
                topLeftY = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().y;

                screenX = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().width;
                screenY = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().height;

                windowPosX = ((screenX - window.getWidth()) / 2) + topLeftX;
                windowPosY = ((screenY - window.getHeight()) / 2) + topLeftY;

                window.setLocation(windowPosX, windowPosY);
            }
        });
    }

    public static void pausar(long pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException ex) {
            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void GUIRun(Runnable r) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(r);
        } else {
            r.run();
        }

    }

    public static void GUIRunAndWait(Runnable r) {

        try {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(r);
            } else {
                r.run();
            }
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void threadRun(Runnable r) {

        Thread hilo = new Thread(r);

        hilo.start();

    }

    public static Font createAndRegisterFont(InputStream stream) {

        Font font = null;

        try {

            font = Font.createFont(Font.TRUETYPE_FONT, stream);

            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

            ge.registerFont(font);

        } catch (FontFormatException | IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex.getMessage());
        }

        return font;
    }

    public static void updateFonts(final Component component, final Font font, final Float zoom_factor) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        updateFonts(child, font, zoom_factor);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        updateFonts(child, font, zoom_factor);
                    }
                }
            }

            Font old_font = component.getFont();

            Font new_font = font.deriveFont(old_font.getStyle(), zoom_factor != null ? Math.round(old_font.getSize() * zoom_factor) : old_font.getSize());

            boolean error;

            do {
                try {

                    if (component instanceof JTable) {
                        ((JTable) component).getTableHeader().setFont(new_font);
                    }

                    component.setFont(new_font);
                    error = false;
                } catch (Exception ex) {
                    error = true;
                }
            } while (error);

        }
    }

}
