/*
 * Copyright (C) 2026 tonikelope
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EC-Identity v1: persistence layer for Trust-On-First-Use identity pinning.
 *
 * Reads and updates the SQLite known_identities table created by Helpers.initSQLITE.
 * Resolution semantics:
 *
 *   Outcome  Behavior                                                        Shield
 *   -------  --------------------------------------------------------------  -------
 *   NEW      Unknown nick. INSERT row, sessions_count=1, verified_oob=0.     Grey
 *   MATCH    Known nick, pubkey byte-identical. UPDATE last_seen +           Grey or
 *            sessions_count++; verified_oob untouched.                       green
 *   CHANGED  Known nick, pubkey differs. UPDATE pubkey + last_seen +         Grey
 *            sessions_count++ + verified_oob reset to 0. Silent — the
 *            user only discovers this by inspecting the identicon.
 *
 * The resolution is intentionally non-interactive (no blocking modal) to keep the
 * UX friction-free. Spec §3 "TOFU resolution".
 */
public final class TOFUResolver {

    private static final Logger LOGGER = Logger.getLogger(TOFUResolver.class.getName());

    public enum Outcome {
        NEW,
        MATCH,
        CHANGED
    }

    public static final class Resolution {

        private final Outcome outcome;
        private final boolean verifiedOob;
        private final int sessionsCount;

        Resolution(Outcome outcome, boolean verifiedOob, int sessionsCount) {
            this.outcome = outcome;
            this.verifiedOob = verifiedOob;
            this.sessionsCount = sessionsCount;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public boolean isVerifiedOob() {
            return verifiedOob;
        }

        public int getSessionsCount() {
            return sessionsCount;
        }
    }

    private TOFUResolver() {
    }

    /**
     * Resolves a (nick, pubkey) pair against the local pinning store, updating it
     * according to TOFU rules. The pubkey is the 32 raw bytes of an Ed25519 public
     * key. Returns the outcome category and the post-update state.
     */
    public static Resolution resolve(String nick, byte[] pubkey) {
        if (nick == null || nick.isEmpty()) {
            throw new IllegalArgumentException("nick must not be null or empty");
        }
        if (pubkey == null || pubkey.length != 32) {
            throw new IllegalArgumentException("pubkey must be 32 raw bytes");
        }
        long now = System.currentTimeMillis() / 1000L;

        synchronized (Helpers.class) {
            // Declared outside try so the catch (Exception) below can propagate the
            // CORRECT outcome (CHANGED / MATCH / NEW) even if the SQL UPDATE/INSERT
            // throws AFTER the SELECT already established what we know. The previous
            // catch returned Outcome.NEW unconditionally, which silently downgraded
            // a CHANGED identity (a MITM rotating pubkeys under a known nick) to
            // "first time we see this nick" when SQL hiccupped mid-operation.
            byte[] existingPubkey = null;
            boolean existingVerified = false;
            int existingSessionsCount = 0;
            boolean found = false;
            try {
                Connection conn = Helpers.getSQLITE();

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT pubkey, verified_oob, sessions_count FROM known_identities WHERE nick = ?")) {
                    ps.setString(1, nick);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingPubkey = rs.getBytes(1);
                            existingVerified = rs.getInt(2) != 0;
                            existingSessionsCount = rs.getInt(3);
                            found = true;
                        }
                    }
                }

                if (!found) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO known_identities (nick, pubkey, first_seen, last_seen, sessions_count, verified_oob) "
                            + "VALUES (?, ?, ?, ?, 1, 0)")) {
                        ps.setString(1, nick);
                        ps.setBytes(2, pubkey);
                        ps.setLong(3, now);
                        ps.setLong(4, now);
                        ps.executeUpdate();
                    }
                    LOGGER.log(Level.INFO, "TOFU: NEW identity for {0}", nick);
                    return new Resolution(Outcome.NEW, false, 1);
                }

                boolean matches = java.security.MessageDigest.isEqual(existingPubkey, pubkey);

                if (matches) {
                    int newCount = existingSessionsCount + 1;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE known_identities SET last_seen = ?, sessions_count = ? WHERE nick = ?")) {
                        ps.setLong(1, now);
                        ps.setInt(2, newCount);
                        ps.setString(3, nick);
                        ps.executeUpdate();
                    }
                    return new Resolution(Outcome.MATCH, existingVerified, newCount);
                } else {
                    int newCount = existingSessionsCount + 1;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE known_identities SET pubkey = ?, last_seen = ?, sessions_count = ?, verified_oob = 0 WHERE nick = ?")) {
                        ps.setBytes(1, pubkey);
                        ps.setLong(2, now);
                        ps.setInt(3, newCount);
                        ps.setString(4, nick);
                        ps.executeUpdate();
                    }
                    LOGGER.log(Level.WARNING, "TOFU: pubkey CHANGED for {0} (verified_oob reset to 0)", nick);
                    return new Resolution(Outcome.CHANGED, false, newCount);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "TOFU: resolve failed for nick " + nick, ex);
                // Defensa: si el SELECT inicial alcanzó a leer un pubkey existente
                // y NO matchea con el presentado, propagamos CHANGED — no NEW —
                // aunque el UPDATE/INSERT posterior haya fallado. Devolver NEW aquí
                // ocultaría un MITM exitoso al usuario (su única señal visual de
                // CHANGED es el identicon, que no se actualiza si el outcome es NEW).
                if (found && existingPubkey != null
                        && !java.security.MessageDigest.isEqual(existingPubkey, pubkey)) {
                    return new Resolution(Outcome.CHANGED, false, existingSessionsCount + 1);
                }
                // SELECT vio el mismo pubkey y el UPDATE de last_seen falló: behave
                // as MATCH para no resetear la confianza por un fallo I/O transitorio.
                if (found) {
                    return new Resolution(Outcome.MATCH, existingVerified, existingSessionsCount + 1);
                }
                // No alcanzamos a ver al peer (SELECT o INSERT falló): NEW es la única
                // respuesta honesta — no tenemos info para decir otra cosa.
                return new Resolution(Outcome.NEW, false, 0);
            }
        }
    }

    /**
     * Marks a (nick, pubkey) pair as verified out-of-band. Only succeeds if the
     * stored pubkey for the nick byte-matches the supplied one (defensive guard so
     * verification cannot be forged from stale UI state).
     */
    public static boolean markVerified(String nick, byte[] pubkey) {
        if (nick == null || pubkey == null || pubkey.length != 32) {
            return false;
        }
        synchronized (Helpers.class) {
            try {
                Connection conn = Helpers.getSQLITE();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT pubkey FROM known_identities WHERE nick = ?")) {
                    ps.setString(1, nick);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return false;
                        }
                        byte[] stored = rs.getBytes(1);
                        if (!java.security.MessageDigest.isEqual(stored, pubkey)) {
                            return false;
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE known_identities SET verified_oob = 1 WHERE nick = ?")) {
                    ps.setString(1, nick);
                    int rows = ps.executeUpdate();
                    LOGGER.log(Level.INFO, "TOFU: marked {0} as verified_oob", nick);
                    return rows == 1;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "TOFU: markVerified failed for nick " + nick, ex);
                return false;
            }
        }
    }

    /**
     * Reads the pinned pubkey for a nick. Returns null if not present.
     */
    public static byte[] getPinnedPubkey(String nick) {
        if (nick == null || nick.isEmpty()) {
            return null;
        }
        synchronized (Helpers.class) {
            try {
                Connection conn = Helpers.getSQLITE();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT pubkey FROM known_identities WHERE nick = ?")) {
                    ps.setString(1, nick);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getBytes(1);
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "TOFU: getPinnedPubkey failed for nick " + nick, ex);
            }
        }
        return null;
    }

    /**
     * Returns true if the pinned pubkey for this nick matches and has been verified
     * out-of-band by the user. Used to decide whether to render the shield in green.
     */
    public static boolean isVerified(String nick, byte[] pubkey) {
        if (nick == null || pubkey == null || pubkey.length != 32) {
            return false;
        }
        synchronized (Helpers.class) {
            try {
                Connection conn = Helpers.getSQLITE();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT pubkey, verified_oob FROM known_identities WHERE nick = ?")) {
                    ps.setString(1, nick);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return false;
                        }
                        byte[] stored = rs.getBytes(1);
                        if (!java.security.MessageDigest.isEqual(stored, pubkey)) {
                            return false;
                        }
                        return rs.getInt(2) != 0;
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "TOFU: isVerified failed for nick " + nick, ex);
            }
        }
        return false;
    }
}
