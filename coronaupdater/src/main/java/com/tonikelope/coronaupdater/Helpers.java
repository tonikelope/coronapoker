/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronaupdater;

import java.awt.Desktop;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 *
 * @author tonikelope
 */
public class Helpers {

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

    public static void centrarJFrame(JFrame window, int screen) {

        GUIRun(new Runnable() {
            @Override
            public void run() {

                GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice[] allDevices = env.getScreenDevices();
                int topLeftX, topLeftY, screenX, screenY, windowPosX, windowPosY;

                if (screen < allDevices.length && screen > -1) {
                    topLeftX = allDevices[screen].getDefaultConfiguration().getBounds().x;
                    topLeftY = allDevices[screen].getDefaultConfiguration().getBounds().y;

                    screenX = allDevices[screen].getDefaultConfiguration().getBounds().width;
                    screenY = allDevices[screen].getDefaultConfiguration().getBounds().height;
                } else {
                    topLeftX = allDevices[0].getDefaultConfiguration().getBounds().x;
                    topLeftY = allDevices[0].getDefaultConfiguration().getBounds().y;

                    screenX = allDevices[0].getDefaultConfiguration().getBounds().width;
                    screenY = allDevices[0].getDefaultConfiguration().getBounds().height;
                }

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

        boolean ok;

        do {
            ok = true;

            try {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(r);
                } else {
                    r.run();
                }
            } catch (Exception ex) {
                ok = false;
                Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                pausar(250);
            }

        } while (!ok);
    }

    public static void GUIRunAndWait(Runnable r) {

        boolean ok;

        do {
            ok = true;
            try {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeAndWait(r);
                } else {
                    r.run();
                }
            } catch (Exception ex) {
                ok = false;
                Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                pausar(250);
            }
        } while (!ok);
    }

    public static void threadRun(Runnable r) {

        Thread hilo = new Thread(r);

        hilo.start();

    }

}
