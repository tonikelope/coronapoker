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
 *
 * * @author tonikelope
 */
public class ImageCacheManager {

    /**
     * Primary entry point. Returns a UNIQUE ImageIcon instance. Loading from
     * bytes prevents Swing from synchronizing GIF animations.
     *
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

    // Topes de la descarga de una imagen de chat. La URL la elige quien manda el
    // mensaje, asi que sin ellos un servidor que no responde deja el hilo esperando
    // para siempre y uno que sirve sin parar llena el disco.
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final long MAX_IMAGE_BYTES = 16L * 1024 * 1024;

    // Tope del directorio de imagenes cacheadas. Se purga por antiguedad al arrancar.
    private static final long MAX_CACHE_BYTES = 256L * 1024 * 1024;

    /**
     * Downloads the resource to the local file system.
     *
     * <p>Se baja a un temporal y solo al terminar se pone en su sitio: escribir
     * directamente sobre el destino dejaba, si la descarga se cortaba, un fichero a
     * medias que YA EXISTE, y a partir de ahi la imagen rota se daba por buena para
     * siempre porque la cache solo mira si el fichero esta.
     */
    private static boolean downloadImage(URL url, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tmp = new File(destination.getAbsolutePath() + ".part");

        try {
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            long total = 0;

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream()); FileOutputStream out = new FileOutputStream(tmp)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    total += bytesRead;
                    if (total > MAX_IMAGE_BYTES) {
                        throw new IOException("chat image exceeds " + MAX_IMAGE_BYTES + " bytes");
                    }
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            java.nio.file.Files.move(tmp.toPath(), destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Logger.getLogger(ImageCacheManager.class.getName()).log(Level.SEVERE,
                    "Critical: Failed to cache image from " + url, e);
            tmp.delete();
            return false;
        }
    }

    /**
     * Purga la cache de imagenes de chat si se ha pasado del tope, borrando de lo mas
     * viejo a lo mas nuevo. El directorio no se limpiaba NUNCA: cada imagen que alguien
     * pegara en el chat se quedaba ahi para siempre.
     *
     * <p>Se llama una vez al arrancar, antes de que nadie use la cache.
     */
    public static void purgeCache() {
        try {
            File dir = new File(CHAT_IMAGE_CACHE);
            File[] files = dir.listFiles(File::isFile);

            if (files == null || files.length == 0) {
                return;
            }

            long total = 0;
            for (File f : files) {
                total += f.length();
            }

            if (total <= MAX_CACHE_BYTES) {
                return;
            }

            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));

            int borrados = 0;
            for (File f : files) {
                if (total <= MAX_CACHE_BYTES) {
                    break;
                }
                long size = f.length();
                if (f.delete()) {
                    total -= size;
                    borrados++;
                }
            }

            Logger.getLogger(ImageCacheManager.class.getName()).log(Level.INFO,
                    "Chat image cache purged: {0} files removed, {1} bytes left",
                    new Object[]{borrados, total});
        } catch (Exception ex) {
            Logger.getLogger(ImageCacheManager.class.getName()).log(Level.WARNING,
                    "Could not purge the chat image cache", ex);
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
