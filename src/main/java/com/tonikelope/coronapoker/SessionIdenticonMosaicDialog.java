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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Host-only dialog that renders, in a grid, the session AES identicons of every
 * participant's channel with the host. Each tile shows the participant's nick, the
 * 7x7 visual identicon (SHA-256 of the AES key bytes) and the full 128-bit
 * fingerprint formatted as 8 groups of 4 hex chars.
 *
 * The host uses this for OOB cross-checking: send a screenshot of the mosaic to
 * the group (or compare individually with each peer) to detect ISP/Wi-Fi/router
 * MITM on any of the host-to-client channels. A mismatch on a single tile is the
 * smoking-gun signal of a MITM on that specific peer's connection.
 *
 * Clients do not get a mosaic — they have a single channel with the host and so
 * they open the regular {@link IdenticonDialog} in SESSION mode.
 */
public class SessionIdenticonMosaicDialog extends JDialog {

    private static final int TILE_PX = 128;
    private static final int MAX_COLS = 3;

    public SessionIdenticonMosaicDialog(java.awt.Frame parent, boolean modal,
            List<MosaicEntry> entries) {
        super(parent, modal);
        setTitle(Translator.translate("ui.identicon.mosaico_title"));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        int n = entries == null ? 0 : entries.size();
        int cols = Math.min(MAX_COLS, Math.max(1, n));
        int rows = (n + cols - 1) / cols;
        if (rows < 1) {
            rows = 1;
        }

        JPanel grid = new JPanel(new GridLayout(rows, cols, 8, 8));
        grid.setBackground(Color.WHITE);
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (n == 0) {
            JLabel emptyLabel = new JLabel(
                    Translator.translate("ui.identicon.mosaico_vacio"),
                    SwingConstants.CENTER);
            grid.add(emptyLabel);
        } else {
            for (MosaicEntry e : entries) {
                grid.add(buildTile(e));
            }
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setPreferredSize(new Dimension(
                Math.min(900, 32 + cols * (TILE_PX + 32)),
                Math.min(700, 64 + rows * (TILE_PX + 56))));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);

        pack();
    }

    private JPanel buildTile(MosaicEntry e) {
        JPanel tile = new JPanel(new BorderLayout(0, 4));
        tile.setBackground(Color.WHITE);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));

        JLabel nickLabel = new JLabel(e.nick, SwingConstants.CENTER);
        nickLabel.setFont(nickLabel.getFont().deriveFont(java.awt.Font.BOLD));
        tile.add(nickLabel, BorderLayout.NORTH);

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    e.aesKey != null ? e.aesKey.getEncoded() : new byte[0]);
            ImageIcon icon = new ImageIcon(IdenticonDialog.generateIdenticon(hash, TILE_PX, TILE_PX));
            JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER);
            tile.add(iconLabel, BorderLayout.CENTER);

            JLabel fp = new JLabel(IdenticonDialog.formatFullFingerprint(hash),
                    SwingConstants.CENTER);
            fp.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
            tile.add(fp, BorderLayout.SOUTH);
        } catch (Exception ex) {
            tile.add(new JLabel("?", SwingConstants.CENTER), BorderLayout.CENTER);
        }

        return tile;
    }

    /**
     * Returns the dialog for the current waiting-room state. If the caller is the
     * host, the dialog renders one tile per connected participant. If the caller is
     * a client, the result is null and the caller should open a regular
     * IdenticonDialog with the client's own session key instead.
     */
    public static SessionIdenticonMosaicDialog buildForHost(java.awt.Frame parent,
            WaitingRoomFrame waitingRoom) {
        if (waitingRoom == null || !waitingRoom.isServer()) {
            return null;
        }
        List<MosaicEntry> entries = new ArrayList<>();
        synchronized (waitingRoom.getParticipantes()) {
            for (Participant p : waitingRoom.getParticipantes().values()) {
                if (p == null || p.isCpu() || p.getAes_key() == null) {
                    continue;
                }
                entries.add(new MosaicEntry(p.getNick(), p.getAes_key()));
            }
        }
        return new SessionIdenticonMosaicDialog(parent, true, entries);
    }

    public static final class MosaicEntry {

        final String nick;
        final SecretKeySpec aesKey;

        public MosaicEntry(String nick, SecretKeySpec aesKey) {
            this.nick = nick;
            this.aesKey = aesKey;
        }
    }
}
