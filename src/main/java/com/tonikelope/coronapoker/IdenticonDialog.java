/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Identicon dialog for OOB visual fingerprint comparison.
 *
 * Two operational modes:
 *
 *   - SESSION (legacy, host vs client AES channel key): no "Verificar" button.
 *     Compares against the counterpart's identicon out-of-band to detect a network
 *     MITM between client and host. The fact that the dialog can be opened at all
 *     means the channel is established, so the user reads the visual + hex
 *     fingerprint and compares it via a side channel.
 *
 *   - IDENTITY (Ed25519 pubkey of a peer): exposes a "Verificar identidad" button
 *     that marks (nick, pubkey) as verified_oob in known_identities. A hint line
 *     reminds the user to compare the fingerprint through an external secure channel
 *     (WhatsApp, Telegram, voice...) before pressing it.
 *
 * Both modes use SHA-256 over the raw input bytes and render a 7x7 grid with
 * horizontal symmetry, using two foreground colors derived from disjoint hash
 * bytes (replaces the legacy MD5 + 5x5 + single colour).
 */
public class IdenticonDialog extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(IdenticonDialog.class.getName());

    public enum Mode {
        SESSION,
        IDENTITY
    }

    private static final int GRID = 7;

    private final Mode mode;
    private final String nick;
    private final byte[] pubkeyForVerify;
    private BufferedImage identiconImage;

    /**
     * Legacy constructor for session AES identicons (host vs client channel).
     * Mode: SESSION (no verify button).
     */
    public IdenticonDialog(java.awt.Frame parent, boolean modal, String nick, SecretKeySpec key) {
        this(parent, modal, nick, key != null ? key.getEncoded() : new byte[0], Mode.SESSION, null);
    }

    /**
     * General constructor.
     *
     * @param parent         parent frame
     * @param modal          modal flag
     * @param nick           displayed in the dialog title
     * @param rawInput       bytes to fingerprint (AES key bytes for SESSION, Ed25519
     *                       pubkey for IDENTITY)
     * @param mode           SESSION or IDENTITY
     * @param pubkeyForVerify in IDENTITY mode, the same 32-byte pubkey to pass to
     *                       TOFUResolver.markVerified when the user clicks the
     *                       verify button. Pass null for SESSION mode.
     */
    public IdenticonDialog(java.awt.Frame parent, boolean modal, String nick,
            byte[] rawInput, Mode mode, byte[] pubkeyForVerify) {
        super(parent, modal);
        this.mode = mode != null ? mode : Mode.SESSION;
        this.nick = nick;
        this.pubkeyForVerify = pubkeyForVerify != null ? pubkeyForVerify.clone() : null;

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawInput != null ? rawInput : new byte[0]);

            initComponents();
            setTitle(nick);

            int SIZE = Math.round(parent.getHeight() * 0.3f);
            while (SIZE % GRID != 0) {
                SIZE--;
            }
            if (SIZE < GRID) {
                SIZE = GRID * 32;
            }

            this.identiconImage = generateIdenticon(hash, SIZE, SIZE);
            ImageIcon icon = new ImageIcon(this.identiconImage);
            icon_label.setIcon(icon);
            icon_label.setHorizontalAlignment(SwingConstants.CENTER);
            icon_label.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
            icon_panel.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
            icon_panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 18, 14, 18));

            // ===== NORTH: big centered nick =====
            JLabel nickLabel = new JLabel(nick, SwingConstants.CENTER);
            nickLabel.setFont(nickLabel.getFont().deriveFont(java.awt.Font.BOLD, 22f));
            nickLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 10, 6, 10));
            nickLabel.setOpaque(true);
            nickLabel.setBackground(Color.WHITE);

            // ===== CENTER: identicon (icon_panel from form, with padding around) =====
            // icon_panel already has the icon_label set above plus an EmptyBorder so
            // the identicon does not touch the dialog edges.

            // ===== SOUTH: fingerprint + extra panels (verify for remote IDENTITY,
            //              explanatory hint for self IDENTITY) + copy-to-clipboard
            //              button (always available regardless of mode).
            String fp = formatFullFingerprint(hash);
            JLabel fingerprintLabel = new JLabel(fp, SwingConstants.CENTER);
            fingerprintLabel.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 14));
            fingerprintLabel.setOpaque(true);
            fingerprintLabel.setBackground(Color.WHITE);
            fingerprintLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

            JPanel southPanel = new JPanel(new BorderLayout());
            southPanel.setOpaque(true);
            southPanel.setBackground(Color.WHITE);
            southPanel.add(fingerprintLabel, BorderLayout.NORTH);

            if (this.mode == Mode.IDENTITY && this.pubkeyForVerify != null) {
                // Remote peer identity: show the "Verify identity" hint + button.
                southPanel.add(buildVerifyPanel(), BorderLayout.CENTER);
            } else if (this.mode == Mode.IDENTITY) {
                // Own identity (LocalPlayer in the mesa): no verify button (the user
                // does not verify themselves) but we surface a short explanatory line
                // so the user knows what this dialog is for and how to use the
                // copy-to-clipboard button below.
                southPanel.add(buildSelfHintPanel(), BorderLayout.CENTER);
            }

            // Copy-to-clipboard row sits at the very bottom for all modes and roles,
            // so screenshots of the identicon are not the only way to share it OOB.
            southPanel.add(buildCopyImagePanel(), BorderLayout.SOUTH);

            getContentPane().setLayout(new BorderLayout());
            getContentPane().setBackground(Color.WHITE);
            getContentPane().add(nickLabel, BorderLayout.NORTH);
            getContentPane().add(icon_panel, BorderLayout.CENTER);
            getContentPane().add(southPanel, BorderLayout.SOUTH);

            setTitle(nick + " - " + fp);
            pack();

        } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Explanatory hint panel shown only on a self-identity dialog (own pubkey in the
     * mesa, no peer to verify against). Tells the user that the rendered image and
     * fingerprint are theirs to share through an external secure channel so others
     * can verify them.
     */
    private JPanel buildSelfHintPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 14, 6, 14));

        String hintHtml = "<html><body style='width: 380px; text-align: justify; "
                + "font-family: sans-serif; font-size: 13pt;'>"
                + Translator.translate("ui.identicon.self_explicacion")
                + "</body></html>";
        JLabel hintLabel = new JLabel(hintHtml, SwingConstants.CENTER);
        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(hintLabel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Sharing row. Always present, mode-agnostic and role-agnostic: lets the user
     * push the identicon to the system clipboard (paste into any chat / mail app) or
     * save it as a PNG file (attach the file by hand from any chat / mail app). No
     * third-party services involved — both options stay strictly local, which keeps
     * the OOB verification model intact.
     */
    private JPanel buildCopyImagePanel() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        row.setOpaque(true);
        row.setBackground(Color.WHITE);
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JButton copyBtn = new JButton(Translator.translate("ui.identicon.copiar_imagen_button"));
        copyBtn.setFont(copyBtn.getFont().deriveFont(java.awt.Font.PLAIN, 13f));
        copyBtn.setMargin(new java.awt.Insets(6, 14, 6, 14));
        copyBtn.addActionListener(evt -> {
            if (copyImageToClipboard(identiconImage)) {
                copyBtn.setText("✓ " + Translator.translate("ui.identicon.copiado"));
                copyBtn.setEnabled(false);
                javax.swing.Timer t = new javax.swing.Timer(2000, e -> {
                    copyBtn.setText(Translator.translate("ui.identicon.copiar_imagen_button"));
                    copyBtn.setEnabled(true);
                });
                t.setRepeats(false);
                t.start();
            } else {
                Helpers.mostrarMensajeError(this,
                        Translator.translate("ui.identicon.copiar_error"));
            }
        });
        row.add(copyBtn);

        JButton saveBtn = new JButton(Translator.translate("ui.identicon.guardar_png_button"));
        saveBtn.setFont(saveBtn.getFont().deriveFont(java.awt.Font.PLAIN, 13f));
        saveBtn.setMargin(new java.awt.Insets(6, 14, 6, 14));
        saveBtn.addActionListener(evt -> savePngWithChooser());
        row.add(saveBtn);
        return row;
    }

    /**
     * Opens a JFileChooser pre-populated with a sensible default filename derived
     * from nick and short fingerprint ("identicon_<nick>_<short_fp>.png") and writes
     * the identicon to disk as PNG. If the user picks an existing file we confirm
     * before overwriting; any I/O failure is surfaced as an error popup.
     */
    private void savePngWithChooser() {
        if (identiconImage == null) {
            return;
        }
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle(Translator.translate("ui.identicon.guardar_png_dialog_title"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG", "png"));
        chooser.setSelectedFile(new java.io.File(defaultPngFilename()));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File target = chooser.getSelectedFile();
        if (target == null) {
            return;
        }
        if (!target.getName().toLowerCase().endsWith(".png")) {
            target = new java.io.File(target.getParentFile(), target.getName() + ".png");
        }
        if (target.exists()) {
            int ans = javax.swing.JOptionPane.showConfirmDialog(this,
                    Translator.translate("ui.identicon.guardar_png_overwrite", target.getName()),
                    Translator.translate("ui.identicon.guardar_png_dialog_title"),
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            if (ans != javax.swing.JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            javax.imageio.ImageIO.write(identiconImage, "PNG", target);
            LOGGER.log(Level.INFO, "Identicon PNG saved to {0}", target.getAbsolutePath());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not save identicon PNG", ex);
            Helpers.mostrarMensajeError(this,
                    Translator.translate("ui.identicon.guardar_png_error", ex.getMessage()));
        }
    }

    /**
     * Builds a filesystem-safe default filename: {@code identicon_<sanitized_nick>_<short_fp>.png}.
     * Replaces every non-{@code [A-Za-z0-9_-]} character with {@code _} so chats nicks
     * containing spaces, emojis or punctuation do not produce invalid filenames on any
     * platform.
     */
    private String defaultPngFilename() {
        String safeNick = nick == null ? "anon" : nick.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeNick.length() > 24) {
            safeNick = safeNick.substring(0, 24);
        }
        String tail = "";
        String currentTitle = getTitle();
        if (currentTitle != null) {
            int dash = currentTitle.indexOf(" - ");
            if (dash >= 0 && dash + 3 < currentTitle.length()) {
                String fp = currentTitle.substring(dash + 3);
                int firstSpace = fp.indexOf(' ');
                if (firstSpace > 0) {
                    tail = "_" + fp.substring(0, firstSpace).toLowerCase();
                }
            }
        }
        return "identicon_" + safeNick + tail + ".png";
    }

    /**
     * Pushes the given BufferedImage to the system clipboard wrapped in a Transferable
     * that exposes DataFlavor.imageFlavor (consumed by image-aware apps like Gimp,
     * Photoshop, Telegram desktop, browsers, etc.). Returns false if the clipboard is
     * unreachable.
     */
    private static boolean copyImageToClipboard(BufferedImage img) {
        if (img == null) {
            return false;
        }
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new ImageTransferable(img), null);
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not copy identicon image to clipboard", ex);
            return false;
        }
    }

    private static final class ImageTransferable implements Transferable {

        private final Image image;

        ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

    private JPanel buildVerifyPanel() {
        JPanel verifyPanel = new JPanel(new BorderLayout(0, 8));
        verifyPanel.setOpaque(true);
        verifyPanel.setBackground(Color.WHITE);
        verifyPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 14, 14, 14));

        boolean alreadyVerified = (pubkeyForVerify != null)
                && TOFUResolver.isVerified(nick, pubkeyForVerify);

        if (alreadyVerified) {
            JLabel verifiedLabel = new JLabel(
                    "✓ " + Translator.translate("ui.identicon.ya_verificada"),
                    SwingConstants.CENTER);
            verifiedLabel.setForeground(new Color(0, 128, 0));
            verifiedLabel.setFont(verifiedLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
            verifyPanel.add(verifiedLabel, BorderLayout.CENTER);
        } else {
            // Hint text: justified and centered, slightly larger so the user actually
            // reads what verifying means before pressing the button.
            String hintHtml = "<html><body style='width: 380px; text-align: justify; "
                    + "font-family: sans-serif; font-size: 13pt;'>"
                    + Translator.translate("ui.identicon.no_verificada")
                    + "</body></html>";
            JLabel hintLabel = new JLabel(hintHtml, SwingConstants.CENTER);
            hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
            verifyPanel.add(hintLabel, BorderLayout.CENTER);

            JButton verifyButton = new JButton(Translator.translate("ui.identicon.verificar_button"));
            verifyButton.setBackground(new Color(0, 130, 0));
            verifyButton.setForeground(Color.WHITE);
            verifyButton.setFont(verifyButton.getFont().deriveFont(java.awt.Font.BOLD, 15f));
            verifyButton.setMargin(new java.awt.Insets(8, 18, 8, 18));
            verifyButton.addActionListener(evt -> {
                if (pubkeyForVerify != null && TOFUResolver.markVerified(nick, pubkeyForVerify)) {
                    verifyButton.setVisible(false);
                    hintLabel.setVisible(false);
                    JLabel done = new JLabel(
                            "✓ " + Translator.translate("ui.identicon.ya_verificada"),
                            SwingConstants.CENTER);
                    done.setForeground(new Color(0, 128, 0));
                    done.setFont(done.getFont().deriveFont(java.awt.Font.BOLD, 16f));
                    verifyPanel.add(done, BorderLayout.CENTER);
                    verifyPanel.revalidate();
                    verifyPanel.repaint();
                }
            });
            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
            buttonRow.setOpaque(true);
            buttonRow.setBackground(Color.WHITE);
            buttonRow.add(verifyButton);
            verifyPanel.add(buttonRow, BorderLayout.SOUTH);
        }
        return verifyPanel;
    }

    /**
     * Formats the first 16 bytes of a SHA-256 digest as 8 groups of 4 lowercase hex
     * separated by spaces ("a3f9 1c4b 7e2d 9faa 8c12 4456 ef78 1234").
     */
    public static String formatFullFingerprint(byte[] hash) {
        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < 16; i++) {
            sb.append(String.format("%02x", hash[i] & 0xff));
            if (i % 2 == 1 && i < 15) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Renders a 7x7 identicon with horizontal symmetry and two foreground colors
     * derived from disjoint bytes of the hash. The input is the SHA-256 digest of
     * whatever raw bytes seeded this view; this method picks bytes from it for the
     * pattern (positions 8..14, four unique columns after symmetry × seven rows)
     * and the two colors (bytes 0..2 and 4..6).
     *
     * Static so callers like the mosaic dialog can render tiles without needing
     * a JDialog instance for layout reasons.
     */
    public static BufferedImage generateIdenticon(byte[] hash, int image_width, int image_height) {
        int width = GRID, height = GRID;
        BufferedImage identicon = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        WritableRaster raster = identicon.getRaster();

        int[] background = new int[]{255, 255, 255, 0};
        int[] foregroundA = new int[]{hash[0] & 0xff, hash[1] & 0xff, hash[2] & 0xff, 255};
        int[] foregroundB = new int[]{hash[4] & 0xff, hash[5] & 0xff, hash[6] & 0xff, 255};

        // Pattern bytes: enough for 4 unique columns (after horizontal symmetry) of
        // 7 rows. We pick bytes 8..14 from the digest so they don't overlap the
        // colour bytes.
        int patternBase = 8;

        for (int x = 0; x < width; x++) {
            // Horizontal symmetry: columns 0..3 are unique, 4..6 mirror columns 2..0.
            int i = x < 4 ? x : 6 - x;
            byte b = hash[patternBase + i];
            for (int y = 0; y < height; y++) {
                int bit = (b >> y) & 1;
                int[] pixelColor;
                if (bit == 1) {
                    // Alternate between the two foreground colours based on row parity.
                    pixelColor = ((y & 1) == 0) ? foregroundA : foregroundB;
                } else {
                    pixelColor = background;
                }
                raster.setPixel(x, y, pixelColor);
            }
        }

        BufferedImage finalImage = new BufferedImage(image_width, image_height, BufferedImage.TYPE_INT_ARGB);
        AffineTransform at = new AffineTransform();
        at.scale((double) image_width / width, (double) image_height / height);
        AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        finalImage = op.filter(identicon, finalImage);
        return finalImage;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        icon_panel = new javax.swing.JPanel();
        icon_label = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("AES-KEY");
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        icon_panel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout icon_panelLayout = new javax.swing.GroupLayout(icon_panel);
        icon_panel.setLayout(icon_panelLayout);
        icon_panelLayout.setHorizontalGroup(
            icon_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(icon_label, javax.swing.GroupLayout.DEFAULT_SIZE, 340, Short.MAX_VALUE)
        );
        icon_panelLayout.setVerticalGroup(
            icon_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(icon_label, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(icon_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(icon_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // TODO add your handling code here:
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel icon_label;
    private javax.swing.JPanel icon_panel;
    // End of variables declaration//GEN-END:variables
}
