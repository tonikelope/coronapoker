/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Frame;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;

/**
 *
 * @author tonikelope
 */
public class Huevos {

    private static SecretKeySpec key;
    private static IvParameterSpec iv;

    static {
        try {
            key = new SecretKeySpec(MessageDigest.getInstance("MD5").digest(Huevos.class.getResourceAsStream("/images/splash.gif").readAllBytes()), "AES");
            iv = new IvParameterSpec(Helpers.toByteArray("00000000000000000000000000000000"));
        } catch (Exception ex) {
            Logger.getLogger(Huevos.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void M1(JDialog parent, String image) {

        JDialog dialog = new JDialog((Frame) parent.getParent(), true);

        dialog.setResizable(false);

        JLabel l = new JLabel();

        dialog.add(l);

        try {
            Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

            cifrado.init(Cipher.DECRYPT_MODE, key, iv);

            byte[] dec = cifrado.doFinal(Huevos.class.getResourceAsStream("/images/" + image).readAllBytes());

            l.setIcon(new ImageIcon(dec));

            dialog.pack();

            dialog.setLocationRelativeTo(parent);

            dialog.setVisible(true);

        } catch (Exception ex) {
            Logger.getLogger(Huevos.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static byte[] M2(String file) {

        try {
            Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

            cifrado.init(Cipher.DECRYPT_MODE, key, iv);

            byte[] dec = cifrado.doFinal(Huevos.class.getResourceAsStream("/images/" + file).readAllBytes());

            return dec;

        } catch (Exception ex) {
            Logger.getLogger(Huevos.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

}
