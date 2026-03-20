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
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core JNI interface mapping for the Panoptes Zero-Trust Cryptographic Engine.
 * Implements the V61 semantic architecture.
 */
public class Panoptes {

    private static Panoptes instance = null;
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Panoptes.class.getName());

    private static final String REPO_RAW_URL = "https://github.com/tonikelope/coronapoker/raw/refs/heads/master/panoptes/";
    private static final String CHECKSUM_FILE = "checksum.sha1";
    private static final String MANIFEST_KEY = "panoptes_key.bin";

    public static final int STATUS_FAILED = 0;
    public static final int STATUS_CLEAN = 1;
    public static final int STATUS_VM_DETECTED = 2;

    private final ConcurrentHashMap<String, byte[]> activeSessionKeys = new ConcurrentHashMap<>();

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
            LOGGER.log(Level.INFO, "[PANOPTES] Checking engine integrity...");
            URL manifestUrl = new URL(REPO_RAW_URL + CHECKSUM_FILE);
            String manifestContent = downloadText(manifestUrl);

            String expectedLibHash = parseHashFromManifest(manifestContent, libName);
            String expectedKeyHash = parseHashFromManifest(manifestContent, MANIFEST_KEY);

            if (expectedLibHash == null || expectedKeyHash == null) {
                throw new Exception("Target hashes not found in the remote manifest.");
            }

            boolean needsLibUpdate = false;
            boolean needsKeyUpdate = false;

            if (!localLib.exists()) {
                LOGGER.log(Level.INFO, "[PANOPTES] Local library not found. Forcing download...");
                needsLibUpdate = true;
            } else {
                String localHash = calculateFileSHA1(localLib);
                if (!expectedLibHash.equalsIgnoreCase(localHash)) {
                    LOGGER.log(Level.SEVERE, "[PANOPTES-SHIELD] Library hash mismatch! Local: {0} | Remote: {1}", new Object[]{localHash, expectedLibHash});
                    needsLibUpdate = true;
                }
            }

            if (!localKey.exists()) {
                LOGGER.log(Level.INFO, "[PANOPTES] Consensus key not found. Forcing download...");
                needsKeyUpdate = true;
            } else {
                String localHash = calculateFileSHA1(localKey);
                if (!expectedKeyHash.equalsIgnoreCase(localHash)) {
                    LOGGER.log(Level.SEVERE, "[PANOPTES-SHIELD] Key hash mismatch! Local: {0} | Remote: {1}", new Object[]{localHash, expectedKeyHash});
                    needsKeyUpdate = true;
                }
            }

            if (needsLibUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] Downloading official binary from repository...");
                downloadBinary(new URL(REPO_RAW_URL + libName), localLib);

                if (os.contains("mac")) {
                    try {
                        LOGGER.log(Level.INFO, "[PANOPTES] Applying macOS security patches...");
                        new ProcessBuilder("xattr", "-cr", localLib.getAbsolutePath()).start().waitFor();
                        new ProcessBuilder("codesign", "--force", "-s", "-", localLib.getAbsolutePath()).start().waitFor();
                        LOGGER.log(Level.INFO, "[PANOPTES] macOS patches applied successfully.");
                    } catch (Exception macEx) {
                        LOGGER.log(Level.WARNING, "[PANOPTES] Failed to apply macOS patch: {0}", macEx.getMessage());
                    }
                }
            }

            if (needsKeyUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] Downloading official consensus key...");
                downloadBinary(new URL(REPO_RAW_URL + MANIFEST_KEY), localKey);
            }

            if (needsLibUpdate || needsKeyUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] UPDATE COMPLETED");
            } else {
                LOGGER.log(Level.INFO, "[PANOPTES] Integrity verified. Native engine is up to date.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[PANOPTES] CRITICAL ERROR: Unable to verify native engine integrity.", e);
        }
    }

    public static void WAKEUP_PANOPTES() {
        verifyAndDownloadNativeLib(PANOPTES_DIR);
        try {
            System.loadLibrary("panoptes");
            File keyFile = new File(PANOPTES_DIR + File.separator + "panoptes_key.bin");
            if (keyFile.exists()) {
                byte[] manifestBytes = java.nio.file.Files.readAllBytes(keyFile.toPath());
                getInstance().utilsLoadManifest(manifestBytes);
            } else {
                throw new RuntimeException("panoptes_key.bin not found after download sequence.");
            }
        } catch (Throwable e) {
            System.exit(1);
        }
    }

    private Panoptes() {
    }

    public static synchronized Panoptes getInstance() {
        if (instance == null) {
            instance = new Panoptes();
        }
        return instance;
    }

    // =========================================================================
    // NATIVE JNI METHODS (V61)
    // =========================================================================
    // --- IDENTITY & VAULT DOMAIN ---
    public native byte[] identityCreate();

    public native boolean identityLoad(byte[] identityBlob);

    public native byte[] sessionInitialize();

    public native boolean sessionLoad(byte[] sessionBlob);

    public native byte[] sessionGenerateExitTestament();

    // --- NETWORK ATTESTATION DOMAIN ---
    public native byte[] attestationGenerateChallenge(byte[] sessionKey, byte ipType, byte[] ip, short port);

    public native byte[] attestationSolveChallenge(byte[] encryptedChallenge);

    public native int attestationVerifyResponse(byte[] sessionKey, byte[] encryptedResponse);

    // --- UTILITIES & CRYPTO HELPERS ---
    public native void utilsLoadManifest(byte[] manifest);

    public native byte[] utilsGetPublicKey(byte[] privateKey);

    public native byte[] utilsExpandSeedPRNG(byte[] seed, int length);

    public native byte[] utilsShuffleDeck(byte[] seed);

    public native boolean utilsVerifyHandHistory(byte[] dealPacket, byte[] masterKey, int myPos, byte[] myCards, byte[] comCards, byte[][] receipts);

    public native boolean utilsVerifyChaosMAC(byte[] data, byte[] mac);

    public native byte[] utilsDecryptBotEnvelope(byte[] priv, byte[] epub, byte[] enc);

    // --- CONSENSUS & GAME STATE DOMAIN ---
    public native byte[] stateInitializeHand();

    public native byte[] stateGenerateMegapacket(byte[] playerSeedsFlat, int numPlayers, byte[] playerPubKeysFlat);

    public native boolean stateIngestMegapacket(byte[] megapacket, int myPos);

    public native byte[] stateGetLocalPocketCards();

    public native byte[] stateGetShuffleKeyShare();

    public native byte[] stateGetFlopToken();

    public native byte[] stateGetTurnToken();

    public native byte[] stateGetRiverToken();

    public native byte[] stateEvolveStreet(int nextStreet, byte[] consensusKey);

    // --- ACTION CHAIN DOMAIN ---
    public native byte[] chainCommitLocalAction(int type, float amount);

    public native byte[] chainCommitBotAction(int type, float amount, byte[] botPrivKey);

    public native boolean chainVerifyRemoteAction(byte[] actionPacket);

    public native byte[] chainCloseStateAndGetReceipt();

    // --- TELEMETRY & RADAR DOMAIN ---
    public native byte[] telemetryGetSystemRadar(byte[] targetPubKey);

    public native byte[] telemetryDecryptRadarData(byte[] encryptedRadarPacket);

    public native byte[] telemetryCaptureScreenContext(int mode);

    public native String telemetryGetDiagnosticJarPathC();

    // =========================================================================
    // JAVA COMPATIBILITY WRAPPERS
    // =========================================================================
    public byte[] generateChallenge(String ownerID, String ipString, int port) throws Exception {
        byte[] sessionKey = new byte[32];
        new SecureRandom().nextBytes(sessionKey);
        activeSessionKeys.put(ownerID, sessionKey);

        InetAddress addr = InetAddress.getByName(ipString);
        byte[] rawIp = addr.getAddress();
        byte[] paddedIp = new byte[16];
        System.arraycopy(rawIp, 0, paddedIp, 0, rawIp.length);

        return attestationGenerateChallenge(sessionKey, (byte) (rawIp.length == 4 ? 4 : 6), paddedIp, (short) port);
    }

    public byte[] signChallenge(byte[] encryptedChallenge) {
        if (encryptedChallenge == null || encryptedChallenge.length != 75) {
            return null;
        }
        return attestationSolveChallenge(encryptedChallenge);
    }

    public int verifyResponse(String ownerID, byte[] encryptedResponse) {
        byte[] sessionKey = activeSessionKeys.remove(ownerID);
        if (sessionKey == null || encryptedResponse == null || encryptedResponse.length != 73) {
            return STATUS_FAILED;
        }
        int status = attestationVerifyResponse(sessionKey, encryptedResponse);
        Arrays.fill(sessionKey, (byte) 0);
        return status;
    }

    public byte[] easyFlatDeal(byte[][] playerSeeds, byte[][] playerPubKeys) {
        if (playerSeeds == null || playerPubKeys == null || playerSeeds.length != playerPubKeys.length) {
            return null;
        }
        int numPlayers = playerSeeds.length;
        byte[] flatSeeds = new byte[numPlayers * 32];
        byte[] flatPubs = new byte[numPlayers * 32];

        for (int i = 0; i < numPlayers; i++) {
            System.arraycopy(playerSeeds[i], 0, flatSeeds, i * 32, 32);
            System.arraycopy(playerPubKeys[i], 0, flatPubs, i * 32, 32);
        }
        return stateGenerateMegapacket(flatSeeds, numPlayers, flatPubs);
    }

    public byte[] generateRadarReport(byte[] serverPubKey) {
        if (serverPubKey == null || serverPubKey.length != 32) {
            return null;
        }
        return telemetryGetSystemRadar(serverPubKey);
    }

    public String parseRadarReport(byte[] encryptedRadarPacket) {
        if (encryptedRadarPacket == null || encryptedRadarPacket.length <= 48) {
            return null;
        }
        byte[] rawBytes = telemetryDecryptRadarData(encryptedRadarPacket);
        if (rawBytes == null) {
            return null;
        }
        return new String(rawBytes, Charset.forName("UTF-8"));
    }

    public byte[] takeTacticalScreenshot(int mode) {
        return telemetryCaptureScreenContext(mode);
    }

    // =========================================================================
    // HASHING UTILS
    // =========================================================================
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
