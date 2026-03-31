/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                 
| |_ ___  _ __ (_) | _____| | ___  _ __  ___ 
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
 * Implements the v81 Hybrid Semantic Architecture (Layer 1 TCP + Layer 2 P2P
 * Mesh). WARNING: Native methods strictly expect exact byte array sizes.
 * Passing incorrectly sized arrays will result in JVM crashes (SIGSEGV).
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

    /* Thread-safe storage mapping peer identifiers to their ephemeral session keys */
    private final ConcurrentHashMap<String, byte[]> activeSessionKeys = new ConcurrentHashMap<>();

    /**
     * Bootstraps the native engine by comparing local binaries against remote
     * checksums, triggering a secure download if the binary is missing or
     * tampered with.
     */
    private static void verifyAndDownloadNativeLib(String targetDir) {
        String os = System.getProperty("os.name").toLowerCase();
        String libName;

        // 1. Resolve OS-specific binary extension
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

            // 2. Fetch official manifest from remote repository
            URL manifestUrl = new URL(REPO_RAW_URL + CHECKSUM_FILE);
            String manifestContent = downloadText(manifestUrl);

            String expectedLibHash = parseHashFromManifest(manifestContent, libName);
            String expectedKeyHash = parseHashFromManifest(manifestContent, MANIFEST_KEY);

            if (expectedLibHash == null || expectedKeyHash == null) {
                throw new Exception("Target hashes not found in the remote manifest.");
            }

            boolean needsLibUpdate = false;
            boolean needsKeyUpdate = false;

            // 3. Verify Library Integrity
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

            // 4. Verify Consensus Key Integrity
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

            // 5. Download and Patch if necessary
            if (needsLibUpdate) {
                LOGGER.log(Level.INFO, "[PANOPTES] Downloading official binary from repository...");
                downloadBinary(new URL(REPO_RAW_URL + libName), localLib);

                // macOS requires explicit code signing and extended attribute clearing to allow execution
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
                Helpers.cleanAllOldTempCrupierFiles();
                LOGGER.log(Level.INFO, "[PANOPTES] UPDATE COMPLETED");
            } else {
                LOGGER.log(Level.INFO, "[PANOPTES] Integrity verified. Native engine is up to date.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[PANOPTES] CRITICAL ERROR: Unable to verify native engine integrity.", e);
        }
    }

    /**
     * Triggers the environment preparation sequence, loads the native C engine,
     * and securely imports the cryptographic manifest.
     */
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
            LOGGER.log(Level.SEVERE, "[PANOPTES] Initialization failed. Aborting execution.", e);
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
    // NATIVE JNI METHODS (V81 HYBRID)
    // =========================================================================
    // --- SESSION & VAULT DOMAIN ---
    /**
     * Generates an ephemeral session keypair and locks it in the Vault.
     *
     * @return An 80-byte array: [0-31: Public Key] + [32-79: Encrypted Private
     * Key Blob].
     */
    public native byte[] sessionInitialize();

    /**
     * Loads a previously generated encrypted session into the Vault.
     *
     * @param sessionBlob Exactly 48 bytes (Encrypted Private Key Blob).
     * @return true if successfully loaded and verified; false if MAC validation
     * fails.
     */
    public native boolean sessionLoad(byte[] sessionBlob);

    /**
     * Executes a secure "Lobotomy" on the Vault, wiping session keys and
     * generating an exit proof.
     *
     * @return A 96-byte Cryptographic Testament to be distributed to peers upon
     * legitimate exit.
     */
    public native byte[] sessionGenerateExitTestament();

    /**
     * Exports the local entropy seed state from the Vault for persistent
     * recovery.
     *
     * @return Exactly 48 bytes representing the encrypted local entropy blob.
     */
    public native byte[] stateExportLocalEntropy();

    /**
     * Imports and restores the local entropy seed state into the Vault.
     *
     * @param entropyBlob Exactly 48 bytes.
     * @return true if successfully loaded and verified; false if MAC validation
     * fails.
     */
    public native boolean stateImportLocalEntropy(byte[] entropyBlob);

    // --- NETWORK ATTESTATION DOMAIN (LAYER 1: SERVER ↔ CLIENT) ---
    /**
     * Generates a network attestation challenge for a remote peer.
     *
     * @param sessionKey Exactly 32 bytes.
     * @param ipType 4 for IPv4, 6 for IPv6.
     * @param ip Exactly 16 bytes (padded with zeros if IPv4).
     * @param port Target network port.
     * @return A 75-byte encrypted challenge payload.
     */
    public native byte[] attestationGenerateChallenge(byte[] sessionKey, byte ipType, byte[] ip, short port);

    /**
     * Solves an incoming attestation challenge, executing Ring-0 system
     * telemetry and verifying JAR hashes.
     *
     * @param encryptedChallenge Exactly 75 bytes.
     * @return A 73-byte encrypted response containing the system audit MAC.
     */
    public native byte[] attestationSolveChallenge(byte[] encryptedChallenge);

    /**
     * Verifies the solved attestation response from a remote peer.
     *
     * @param sessionKey Exactly 32 bytes (must match the key used to generate
     * the challenge).
     * @param encryptedResponse Exactly 73 bytes.
     * @return 0 (FAILED), 1 (CLEAN), or 2 (VM_DETECTED).
     */
    public native int attestationVerifyResponse(byte[] sessionKey, byte[] encryptedResponse);

    // --- NETWORK ATTESTATION DOMAIN (LAYER 2: P2P MESH) ---
    /**
     * Generates a Zero-Trust P2P challenge for a specific opponent using a Key
     * Encapsulation Mechanism (KEM).
     *
     * @param targetPubKey Exactly 32 bytes (Opponent's Public Key).
     * @return An 80-byte encrypted payload containing the KEM Nonce.
     */
    public native byte[] p2pGenerateChallenge(byte[] targetPubKey);

    /**
     * Solves an incoming P2P challenge by mathematically injecting the local
     * machine's telemetry (Debuggers, Hashes, Thread starvation) directly into
     * the cryptographic Sponge.
     *
     * @param encryptedChallenge Exactly 48 bytes (The encrypted challenge
     * block).
     * @param senderPubKey Exactly 32 bytes (The challenger's Public Key).
     * @return A 17-byte encrypted response (16 byte MAC + 1 byte VM Flag).
     */
    public native byte[] p2pSolveChallenge(byte[] encryptedChallenge, byte[] senderPubKey);

    /**
     * Verifies a solved P2P response from an opponent. If valid, the opponent
     * is mathematically guaranteed to be running identical code without active
     * cheats.
     *
     * @param targetPubKey Exactly 32 bytes (Opponent's Public Key).
     * @param originalNonce Exactly 32 bytes (The unencrypted nonce generated by
     * p2pGenerateChallenge).
     * @param encryptedResponse Exactly 17 bytes (The opponent's response).
     * @return true if clean; false if cheating, tampered, or desynchronized.
     */
    public native boolean p2pVerifyResponse(byte[] targetPubKey, byte[] originalNonce, byte[] encryptedResponse);

    /**
     * Solves an incoming P2P challenge ON BEHALF OF A BOT. The server acts as a
     * proxy, verifying its own memory state but using the bot's keys.
     *
     * @param encryptedChallenge Exactly 48 bytes (The encrypted challenge
     * block).
     * @param senderPubKey Exactly 32 bytes (The challenger's Public Key).
     * @param botPrivKey Exactly 32 bytes (The Bot's Private Key).
     * @return A 17-byte encrypted response.
     */
    public native byte[] p2pSolveBotChallenge(byte[] encryptedChallenge, byte[] senderPubKey, byte[] botPrivKey);

    // --- UTILITIES & CRYPTO HELPERS ---
    /**
     * Loads the official panoptes_key.bin manifest into the Vault for
     * cross-platform binary verification.
     *
     * @param manifest Exactly 48 bytes containing expected DLL/SO/DYLIB hashes.
     */
    public native void utilsLoadManifest(byte[] manifest);

    /**
     * Derives an X25519 Public Key from a raw Private Key.
     *
     * @param privateKey Exactly 32 bytes.
     * @return Exactly 32 bytes (Public Key).
     */
    public native byte[] utilsGetPublicKey(byte[] privateKey);

    /**
     * Performs a deterministic Fisher-Yates shuffle on a standard 52-card deck.
     *
     * @param seed Exactly 32 bytes (Master Seed).
     * @return Exactly 52 bytes representing the shuffled deck (values 0-51).
     */
    public native byte[] utilsShuffleDeck(byte[] seed);

    /**
     * PHASE 1: Executes a local forensic audit of a completed hand.
     * Reconstructs the deck and verifies local seed contributions.
     *
     * @param dealPacket The original Megapacket byte array.
     * @param masterKey Exactly 32 bytes (The reconstructed Master Shuffle Key).
     * @param myPos The executing player's physical seat index.
     * @return A 49-byte array: [0-47: AEAD Receipt] + [48: Boolean 1=OK,
     * 0=FAILED].
     */
    public native byte[] utilsVerifyHandHistory(byte[] dealPacket, byte[] masterKey, int myPos);

    /**
     * PHASE 2: Executes global table consensus verification. Absorbs all P2P
     * receipts AND Exit Testaments to guarantee no player has desynchronized.
     *
     * @param dealPacket The original Megapacket byte array (needed to extract
     * static keys for testaments).
     * @param allReceipts Array containing 48-byte receipts or 96-byte
     * testaments from all peers.
     * @return true if consensus is mathematically sound; false if
     * spoofing/desync detected.
     */
    public native boolean utilsVerifyHandConsensus(byte[] dealPacket, byte[][] allReceipts);

    /**
     * Verifies a Poly1305 Chaos MAC signature.
     *
     * @param data The raw data to verify.
     * @param mac Exactly 16 bytes.
     * @return true if the signature is valid; false otherwise.
     */
    public native boolean utilsVerifyChaosMAC(byte[] data, byte[] mac);

    /**
     * Decrypts a Key Encapsulation Mechanism (KEM) envelope directed to a bot.
     *
     * @param priv Exactly 32 bytes (Bot's private key).
     * @param epub Exactly 32 bytes (Ephemeral public key from the packet).
     * @param enc Exactly 114 bytes (Encrypted payload).
     * @return Exactly 98 bytes (Decrypted clear payload), or null on failure.
     */
    public native byte[] utilsDecryptBotEnvelope(byte[] priv, byte[] epub, byte[] enc);

    // --- CONSENSUS & GAME STATE DOMAIN ---
    /**
     * Initializes a new hand, generating a random 16-byte HAND_ID.
     *
     * @return Exactly 16 bytes (HAND_ID).
     */
    public native byte[] stateInitializeHand();

    /**
     * Generates the immutable genesis Megapacket for a new hand (Server/Host
     * only).
     *
     * @param playerSeedsFlat Flattened array of all player seeds (numPlayers *
     * 32 bytes).
     * @param numPlayers Total number of players (2-22).
     * @param playerPubKeysFlat Flattened array of all player public keys
     * (numPlayers * 32 bytes).
     * @return The variable-length Megapacket byte array.
     */
    public native byte[] stateGenerateMegapacket(byte[] playerSeedsFlat, int numPlayers, byte[] playerPubKeysFlat);

    /**
     * Ingests the Megapacket into the client Vault, verifying structure and
     * extracting local pocket cards.
     *
     * @param megapacket The full Megapacket byte array.
     * @param myPos The client's physical seat index.
     * @return true on successful ingestion; false on tampering or OOB errors.
     */
    public native boolean stateIngestMegapacket(byte[] megapacket, int myPos);

    /**
     * Retrieves the decrypted pocket cards for the local player.
     *
     * @return Exactly 2 bytes representing card IDs (0-51).
     */
    public native byte[] stateGetLocalPocketCards();

    /**
     * Retrieves the local fragment of the Master Shuffle Key. WARNING: Calling
     * this triggers the "Scorched Earth" defense, wiping street tokens.
     *
     * @return Exactly 32 bytes.
     */
    public native byte[] stateGetShuffleKeyShare();

    /**
     * Retrieves the cryptographic token required to unlock the Flop.
     *
     * @return Exactly 16 bytes.
     */
    public native byte[] stateGetFlopToken();

    /**
     * Retrieves the cryptographic token required to unlock the Turn.
     *
     * @return Exactly 16 bytes.
     */
    public native byte[] stateGetTurnToken();

    /**
     * Retrieves the cryptographic token required to unlock the River.
     *
     * @return Exactly 16 bytes.
     */
    public native byte[] stateGetRiverToken();

    /**
     * Evolves the community cards by applying the aggregated consensus key.
     *
     * @param nextStreet The target street index (1=Flop, 2=Turn, 3=River).
     * @param consensusKey Exactly 16 bytes (aggregated XOR of all player
     * tokens).
     * @return 3 bytes for Flop, 1 byte for Turn/River.
     */
    public native byte[] stateEvolveStreet(int nextStreet, byte[] consensusKey);

    // --- ACTION CHAIN DOMAIN ---
    /**
     * Commits and signs a local player action into the cryptographic Sponge.
     *
     * @param type Action type identifier (e.g., Fold, Call, Raise).
     * @param amount The betting amount involved.
     * @return Exactly 52 bytes containing the signed action packet.
     */
    public native byte[] chainCommitLocalAction(int type, float amount);

    /**
     * Commits and signs an action on behalf of a server-side bot.
     *
     * @param type Action type identifier.
     * @param amount The betting amount involved.
     * @param botPrivKey Exactly 32 bytes (Bot's private key).
     * @return Exactly 52 bytes containing the signed action packet.
     */
    public native byte[] chainCommitBotAction(int type, float amount, byte[] botPrivKey);

    /**
     * Verifies and absorbs a remote action packet into the local Sponge state.
     *
     * @param actionPacket Exactly 52 bytes.
     * @return true if signature and sequence are valid; false on
     * desynchronization or tampering.
     */
    public native boolean chainVerifyRemoteAction(byte[] actionPacket);

    /**
     * Closes the state machine and returns the final AEAD receipt for P2P
     * comparison.
     *
     * @return Exactly 48 bytes (Final State AEAD Receipt).
     */
    public native byte[] chainCloseStateAndGetReceipt();

    /**
     * Closes the state machine and returns the final AEAD receipt on behalf of
     * a bot.
     *
     * @param botPrivKey Exactly 32 bytes (Bot's private key).
     * @return Exactly 48 bytes (Final State AEAD Receipt).
     */
    public native byte[] chainCloseBotStateAndGetReceipt(byte[] botPrivKey);

    /**
     * Generates or retrieves the 32-byte local entropy seed for the current
     * hand. Protected by anti-grinding idempotency locks.
     *
     * @param external_entropy Hardware/User provided entropy (optional, can be
     * null).
     * @return Exactly 32 bytes.
     */
    public native byte[] stateGenerateLocalSeed(byte[] external_entropy);

    // --- TELEMETRY & RADAR DOMAIN ---
    /**
     * Captures a deep system telemetry report (loaded modules, hooks, anomalous
     * memory).
     *
     * @param targetPubKey Exactly 32 bytes (Requesting peer's public key for
     * KEM encryption).
     * @return A variable-length encrypted KEM envelope containing the radar
     * data.
     */
    public native byte[] telemetryGetSystemRadar(byte[] targetPubKey);

    /**
     * Decrypts an incoming system radar telemetry packet.
     *
     * @param encryptedRadarPacket The variable-length encrypted packet.
     * @return The plaintext UTF-8 string bytes of the radar report, or null if
     * tampered.
     */
    public native byte[] telemetryDecryptRadarData(byte[] encryptedRadarPacket);

    /**
     * Captures a visual context representation of the host's screen.
     *
     * @param mode 1 = Target Application Window (100% Quality), 2 = Desktop
     * Screen (Grayscale 70% Quality).
     * @return A variable-length byte array representing a valid JPEG image, or
     * null on failure/cooldown.
     */
    public native byte[] telemetryCaptureScreenContext(int mode);

    /**
     * Diagnostics: Retrieves the path to the JAR file currently locked by the C
     * engine.
     *
     * @return String representation of the absolute file path.
     */
    public native String telemetryGetDiagnosticJarPathC();

    // =========================================================================
    // JAVA COMPATIBILITY WRAPPERS
    // =========================================================================
    /**
     * High-level wrapper that generates a LAYER 1 attestation challenge for a
     * remote peer. It automatically creates a SecureRandom session key and
     * caches it using the owner's ID.
     *
     * @param ownerID The unique identifier for the target peer (e.g.,
     * "PARTICIPANT_1234").
     * @param ipString The raw string representation of the target IP address.
     * @param port The target network port.
     * @return A 75-byte encrypted challenge payload ready for network
     * transmission.
     * @throws Exception If IP parsing or underlying JNI cryptographic
     * generation fails.
     */
    public byte[] generateChallenge(String ownerID, String ipString, int port) throws Exception {
        byte[] sessionKey = new byte[32];
        new SecureRandom().nextBytes(sessionKey);

        // Map the ephemeral session key to the connection owner
        activeSessionKeys.put(ownerID, sessionKey);

        InetAddress addr = InetAddress.getByName(ipString);
        byte[] rawIp = addr.getAddress();

        // Zero-padding up to 16 bytes for strict native C array constraints
        byte[] paddedIp = new byte[16];
        System.arraycopy(rawIp, 0, paddedIp, 0, rawIp.length);

        return attestationGenerateChallenge(sessionKey, (byte) (rawIp.length == 4 ? 4 : 6), paddedIp, (short) port);
    }

    /**
     * High-level wrapper that delegates an incoming LAYER 1 challenge to the
     * native C engine. Generates a cryptographic response authenticating the
     * JVM's clean state.
     *
     * @param encryptedChallenge The raw 75-byte challenge payload.
     * @return A 73-byte encrypted response payload, or null if the input is
     * malformed.
     */
    public byte[] signChallenge(byte[] encryptedChallenge) {
        if (encryptedChallenge == null || encryptedChallenge.length != 75) {
            return null;
        }
        return attestationSolveChallenge(encryptedChallenge);
    }

    /**
     * High-level wrapper that verifies an incoming LAYER 1 attestation response
     * from a peer. Automatically retrieves and destroys the stored session key
     * ensuring Perfect Forward Secrecy.
     *
     * @param ownerID The unique identifier used previously in
     * generateChallenge().
     * @param encryptedResponse The raw 73-byte response payload from the peer.
     * @return STATUS_CLEAN (1), STATUS_VM_DETECTED (2), or STATUS_FAILED (0).
     */
    public int verifyResponse(String ownerID, byte[] encryptedResponse) {
        // Remove guarantees the key is wiped from memory immediately after use
        byte[] sessionKey = activeSessionKeys.remove(ownerID);

        if (sessionKey == null || encryptedResponse == null || encryptedResponse.length != 73) {
            return STATUS_FAILED; // Fail-safe fallback
        }

        int status = attestationVerifyResponse(sessionKey, encryptedResponse);

        // Deep memory wipe of the session key bytes
        Arrays.fill(sessionKey, (byte) 0);

        return status;
    }

    /**
     * Convenience wrapper that consolidates 2D arrays into flattened memory
     * arrays required by the native C Megapacket generator.
     *
     * @param playerSeeds A 2D array containing the 32-byte seeds of all
     * players.
     * @param playerPubKeys A 2D array containing the 32-byte public keys of all
     * players.
     * @return The complete encrypted Megapacket, or null if parameter
     * constraints fail.
     */
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

    /**
     * Parses an encrypted system radar envelope generated by a remote peer.
     *
     * @param encryptedRadarPacket The raw byte array containing the encrypted
     * intel.
     * @return The plaintext UTF-8 representation of the system radar, or null
     * on decryption failure.
     */
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

    /**
     * Convenience wrapper for capturing the visual application context.
     *
     * @param mode 1 = Target Application Window (100% Quality), 2 = Desktop
     * Screen (Grayscale 70% Quality).
     * @return JPEG byte array or null.
     */
    public byte[] takeTacticalScreenshot(int mode) {
        return telemetryCaptureScreenContext(mode);
    }

    // =========================================================================
    // HASHING UTILS
    // =========================================================================
    /**
     * Standard SHA-1 fallback to verify remote repository files.
     */
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

    /**
     * Simple utility to download and read plain text files from an URL.
     */
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

    /**
     * Utility to download binary artifacts and commit them safely to the local
     * disk.
     */
    private static void downloadBinary(URL url, File destination) throws IOException {
        if (destination.getParentFile() != null) {
            destination.getParentFile().mkdirs();
        }
        try (InputStream in = url.openStream()) {
            java.nio.file.Files.copy(in, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Extrapolates a target file hash from the official repository manifest
     * structure.
     */
    private static String parseHashFromManifest(String manifest, String fileName) {
        for (String line : manifest.split("\n")) {
            if (line.contains(fileName)) {
                return line.split("\\s+")[0].trim();
            }
        }
        return null;
    }
}
