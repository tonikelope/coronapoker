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

import static com.tonikelope.coronapoker.Init.PANOPTES_DIR;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Panoptes {

    private static Panoptes instance;
    private final ConcurrentHashMap<String, byte[]> activeSessionKeys = new ConcurrentHashMap<>();
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Panoptes.class.getName());

    private static final String REPO_RAW_URL = "https://github.com/tonikelope/coronapoker/raw/refs/heads/master/panoptes/";
    private static final String CHECKSUM_FILE = "checksum.sha1";
    private static final String MANIFEST_KEY = "panoptes_key.bin";

    public static final int STATUS_FAILED = 0;
    public static final int STATUS_CLEAN = 1;
    public static final int STATUS_VM_DETECTED = 2;

    private static void verifyAndDownloadNativeLib(String targetDir) {
        String os = System.getProperty("os.name").toLowerCase();
        String libName;

        if (os.contains("win")) {
            libName = "panoptes.dll";
        } else if (os.contains("mac")) {
            libName = "libpanoptes.dylib";
        } else {
            libName = "libpanoptes.so";
        }
        
        File localLib = new File(targetDir + File.separator + libName);
        File localKey = new File(targetDir + File.separator + MANIFEST_KEY);

        try {
            LOGGER.log(Level.INFO, "[PANOPTES] Checking integrity for: {0} and {1}", new Object[]{libName, MANIFEST_KEY});
            URL manifestUrl = new URL(REPO_RAW_URL + CHECKSUM_FILE);
            String manifestContent = downloadText(manifestUrl);
            
            String expectedLibHash = parseHashFromManifest(manifestContent, libName);
            String expectedKeyHash = parseHashFromManifest(manifestContent, MANIFEST_KEY);

            if (expectedLibHash == null || expectedKeyHash == null) {
                throw new Exception("Required files not found in remote checksum manifest.");
            }

            boolean needsLibUpdate = false;
            boolean needsKeyUpdate = false;

            if (!localLib.exists()) {
                LOGGER.log(Level.WARNING, "[PANOPTES] Local library missing.");
                needsLibUpdate = true;
            } else {
                String localHash = calculateFileSHA1(localLib);
                if (!expectedLibHash.equalsIgnoreCase(localHash)) {
                    LOGGER.log(Level.SEVERE, "[PANOPTES-SHIELD] Library Hash mismatch! Local: {0} | Expected: {1}", new Object[]{localHash, expectedLibHash});
                    needsLibUpdate = true;
                }
            }

            if (!localKey.exists()) {
                LOGGER.log(Level.WARNING, "[PANOPTES] Consensus Key missing.");
                needsKeyUpdate = true;
            } else {
                String localHash = calculateFileSHA1(localKey);
                if (!expectedKeyHash.equalsIgnoreCase(localHash)) {
                    LOGGER.log(Level.SEVERE, "[PANOPTES-SHIELD] Key Hash mismatch! Local: {0} | Expected: {1}", new Object[]{localHash, expectedKeyHash});
                    needsKeyUpdate = true;
                }
            }

            if (needsLibUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] Fetching official binary from GitHub...");
                downloadBinary(new URL(REPO_RAW_URL + libName), localLib);

                if (os.contains("mac")) {
                    try {
                        LOGGER.log(Level.INFO, "[PANOPTES] Applying macOS security patches...");
                        new ProcessBuilder("xattr", "-cr", localLib.getAbsolutePath()).start().waitFor();
                        new ProcessBuilder("codesign", "--force", "-s", "-", localLib.getAbsolutePath()).start().waitFor();
                        LOGGER.log(Level.INFO, "[PANOPTES] macOS patches applied successfully.");
                    } catch (Exception macEx) {
                        LOGGER.log(Level.WARNING, "[PANOPTES] Warning: macOS patch failed: {0}", macEx.getMessage());
                    }
                }
            }
            
            if (needsKeyUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] Fetching Consensus Key (panoptes_key.bin) from GitHub...");
                downloadBinary(new URL(REPO_RAW_URL + MANIFEST_KEY), localKey);
            }
            
            if (needsLibUpdate || needsKeyUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] Local files updated and synchronized with GitHub.");
            } else {
                LOGGER.log(Level.INFO, "[PANOPTES] Integrity verified (SHA1 OK). Everything is up to date.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[PANOPTES] CRITICAL ERROR: Could not verify native engine.", e);
        }
    }

    public static void WAKEUP_PANOPTES() {
        verifyAndDownloadNativeLib(PANOPTES_DIR);

        try {
            System.loadLibrary("panoptes");
            
            // [NUEVO ZERO-TRUST] Inyectamos los 48 bytes en la memoria blindada de C
            File keyFile = new File(PANOPTES_DIR + File.separator + "panoptes_key.bin");
            if (keyFile.exists()) {
                byte[] manifestBytes = java.nio.file.Files.readAllBytes(keyFile.toPath());
                getInstance().loadManifest(manifestBytes);
                LOGGER.log(Level.INFO, "[PANOPTES] Vault loaded with Consensus Manifest.");
            } else {
                throw new RuntimeException("panoptes_key.bin not found after download sequence.");
            }
            
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "===================================================");
            LOGGER.log(Level.SEVERE, "FATAL ERROR: Failed to load 'panoptes' library or key.");
            LOGGER.log(Level.SEVERE, "Please verify your -Djava.library.path configuration.");
            LOGGER.log(Level.SEVERE, "Details: {0}", e.getMessage());
            LOGGER.log(Level.SEVERE, "===================================================");
            Helpers.mostrarMensajeError(null, "ERROR FATAL: NO SE PUEDE CARGAR EL MOTOR PANOPTES O SU LLAVE DE CONSENSO");
            System.exit(1);
        }
        LOGGER.log(Level.INFO, "[PANOPTES] Core engine loaded. Ready for SECURE POKER.");
    }

    private Panoptes() {
    }

    public static synchronized Panoptes getInstance() {
        if (instance == null) {
            instance = new Panoptes();
        }
        return instance;
    }

    // [NUEVO] Inyector del Manifiesto de 48 bytes
    public native void loadManifest(byte[] manifest);

    private native byte[] C(byte[] sessionKey, byte ipType, byte[] ip, short port);
    private native byte[] S(byte[] encryptedChallenge);
    private native int V(byte[] sessionKey, byte[] encryptedResponse);
    
    public native byte[] initHand();
    public native byte[] getPublicKey(byte[] privateKey);
    public native byte[] getSharedSecret(byte[] myPrivateKey, byte[] theirPublicKey);
    public native byte[] expandSeedPRNG(byte[] seed, int length);
    public native byte[] shuffleDeck(byte[] seed);
    public native byte[] deal(byte[] playerSeedsFlat, int numPlayers, byte[] playerPubKeysFlat);
    public native byte[] decryptMyHand(byte[] myPrivateKey, byte[] ephemeralPubKey, byte[] encryptedCards);
    public native byte[] decryptMasterKey(byte[] myPrivateKey, byte[] ephemeralPubKey, byte[] encryptedMasterKey);
    public native boolean verifyHandHistory(byte[] dealPacket, byte[] masterKey, int myPos, byte[] myCards, byte[] comCards);

    // Calles con Cerrojo de Consenso
    public native byte[] getFlop(byte[] tokens);
    public native byte[] getTurn(byte[] tokens);
    public native byte[] getRiver(byte[] tokens);
    public native boolean verifyChaosMAC(byte[] data, byte[] mac);
    
    private native byte[] getRadarData(byte[] targetPubKey);
    private native byte[] decryptRadarData(byte[] myPrivateKey, byte[] encryptedRadarPacket);
    public native byte[] getTacticalScreenshot(int mode);

    // Hibernación y Resurrección
    public native boolean resume(byte[] dump);

    public byte[] generateChallenge(String ownerID, String ipString, int port) throws Exception {
        byte[] sessionKey = new byte[32];
        new SecureRandom().nextBytes(sessionKey);
        activeSessionKeys.put(ownerID, sessionKey);
        InetAddress addr = InetAddress.getByName(ipString);
        byte[] rawIp = addr.getAddress();
        byte[] paddedIp = new byte[16];
        System.arraycopy(rawIp, 0, paddedIp, 0, rawIp.length);
        return C(sessionKey, (byte) (rawIp.length == 4 ? 4 : 6), paddedIp, (short) port);
    }

    public byte[] signChallenge(byte[] encryptedChallenge) {
        if (encryptedChallenge == null || encryptedChallenge.length != 75) {
            return null;
        }
        return S(encryptedChallenge);
    }

    public int verifyResponse(String ownerID, byte[] encryptedResponse) {
        byte[] sessionKey = activeSessionKeys.remove(ownerID);
        if (sessionKey == null || encryptedResponse == null || encryptedResponse.length != 73) {
            return STATUS_FAILED;
        }
        int status = V(sessionKey, encryptedResponse);
        Arrays.fill(sessionKey, (byte) 0);
        return status;
    }

    public byte[] easyFlatDeal(byte[][] playerSeeds, byte[][] playerPubKeys) {
        if (playerSeeds == null || playerPubKeys == null || playerSeeds.length != playerPubKeys.length) {
            return null;
        }
        byte[] flatSeeds = flattenByteArray(playerSeeds);
        byte[] flatPubs = flattenByteArray(playerPubKeys);
        return deal(flatSeeds, playerSeeds.length, flatPubs);
    }

    private byte[] flattenByteArray(byte[][] arrays) {
        if (arrays == null || arrays.length == 0) {
            return null;
        }
        int totalLength = 0;
        for (byte[] arr : arrays) {
            if (arr != null) {
                totalLength += arr.length;
            }
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] arr : arrays) {
            if (arr != null) {
                System.arraycopy(arr, 0, result, offset, arr.length);
                offset += arr.length;
            }
        }
        return result;
    }

    public byte[] generateRadarReport(byte[] serverPubKey) {
        if (serverPubKey == null || serverPubKey.length != 32) {
            return null;
        }
        return getRadarData(serverPubKey);
    }

    public String parseRadarReport(byte[] serverPrivKey, byte[] encryptedRadarPacket) {
        if (serverPrivKey == null || encryptedRadarPacket == null || encryptedRadarPacket.length <= 48) {
            return null;
        }
        byte[] rawBytes = decryptRadarData(serverPrivKey, encryptedRadarPacket);
        if (rawBytes == null) {
            return null;
        }
        return new String(rawBytes, java.nio.charset.Charset.forName("windows-1252"));
    }

    public byte[] takeTacticalScreenshot(int mode) {
        return getTacticalScreenshot(mode);
    }

    private static String calculateFileSHA1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] mdbytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : mdbytes) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    private static String downloadText(URL url) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private static void downloadBinary(URL url, File destination) throws IOException {
        if (destination.getParentFile() != null) {
            destination.getParentFile().mkdirs();
        }
        try (InputStream in = url.openStream()) {
            java.nio.file.Files.copy(in, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String parseHashFromManifest(String manifest, String fileName) {
        for (String line : manifest.split("\n")) {
            if (line.contains(fileName)) {
                return line.split("\\s+")[0].trim();
            }
        }
        return null;
    }
}