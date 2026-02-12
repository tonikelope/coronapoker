/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import static com.tonikelope.coronapoker.Init.CHAT_IMAGE_CACHE;

/**
 * Manages image caching and ensures independent animation instances for GIFs.
 * * @author tonikelope
 */
public class ImageCacheManager {

    /**
     * Primary entry point. Returns a UNIQUE ImageIcon instance.
     * Loading from bytes prevents Swing from synchronizing GIF animations.
     * * @param url The remote URL of the image/gif.
     * @return ImageIcon or null if download/read fails.
     */
    public static ImageIcon getIcon(URL url) {
        if (url == null) {
            return null;
        }

        String fileName = generateFileName(url);
        String separator = CHAT_IMAGE_CACHE.endsWith(File.separator) ? "" : File.separator;
        File localFile = new File(CHAT_IMAGE_CACHE + separator + fileName);

        // 1. Download if not present in local storage
        if (!localFile.exists()) {
            if (!downloadImage(url, localFile)) {
                return null;
            }
        }

        // 2. Load from bytes to ensure the animation is independent (not shared)
        try {
            byte[] imageBytes = Files.readAllBytes(Paths.get(localFile.getAbsolutePath()));
            return new ImageIcon(imageBytes);
        } catch (IOException e) {
            Logger.getLogger(ImageCacheManager.class.getName()).log(Level.SEVERE, 
                "Error reading cached file: " + localFile.getAbsolutePath(), e);
            // Fallback to path-based loading if byte reading fails
            return new ImageIcon(localFile.getAbsolutePath());
        }
    }

    /**
     * Downloads the resource to the local file system.
     */
    private static boolean downloadImage(URL url, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedInputStream in = new BufferedInputStream(url.openStream()); 
             FileOutputStream out = new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Logger.getLogger(ImageCacheManager.class.getName()).log(Level.SEVERE, 
                "Critical: Failed to cache image from " + url, e);
            return false;
        }
    }

    /**
     * Generates a unique filename based on the URL's path and query.
     */
    private static String generateFileName(URL url) {
        String path = url.getPath();
        String query = url.getQuery();
        String identityString = (query != null) ? path + "?" + query : path;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(identityString.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            String extension = path.contains(".") ? path.substring(path.lastIndexOf(".")) : ".tmp";
            return sb.toString() + extension;
        } catch (NoSuchAlgorithmException e) {
            return "img_" + Math.abs(identityString.hashCode());
        }
    }
}