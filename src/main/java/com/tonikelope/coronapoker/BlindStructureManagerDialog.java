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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Editor for the user's custom blind structures. Opened (modal) from the
 * new-game dialog. The built-in default ladder is NOT listed here: it lives
 * implicitly in the new-game combo and is never editable.
 *
 * Hand-coded (no .form), following the same idiom as AudioSettingsDialog: a
 * modal dialog assembled with plain layout managers, translated and font-scaled
 * via Helpers. Editing happens on an in-memory working copy keyed by name; the
 * level table commits each cell live into that copy (reverting unparseable
 * input) and the whole set is validated and persisted only on Save.
 *
 * @author tonikelope
 */
public class BlindStructureManagerDialog extends javax.swing.JDialog {

    // Working copy: structure name -> {sb, bb} ladder. Order is preserved.
    private final LinkedHashMap<String, double[][]> working = new LinkedHashMap<>();

    private final JList<String> structure_list;
    private final javax.swing.DefaultListModel<String> structure_model;
    private final JTable levels_table;
    private final DefaultTableModel levels_model;
    private final JButton duplicate_button;
    private final JButton rename_button;
    private final JButton delete_button;
    private final JButton add_level_button;
    private final JButton remove_level_button;
    private final TitledBorder levels_border;

    // Guards the table model listener while we repopulate it programmatically.
    private volatile boolean loading_table = false;
    private volatile boolean dirty = false;

    public BlindStructureManagerDialog(Window owner) {
        super(owner, Dialog.ModalityType.APPLICATION_MODAL);

        setTitle(Translator.translate("blinds.gestionar_estructuras"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        for (Map.Entry<String, BlindStructure> e : BlindStructure.loadAll().entrySet()) {
            working.put(e.getKey(), e.getValue().getLevels());
        }

        // --- Structures column (list + CRUD buttons) ---
        structure_model = new javax.swing.DefaultListModel<>();
        for (String name : working.keySet()) {
            structure_model.addElement(name);
        }
        structure_list = new JList<>(structure_model);
        structure_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        structure_list.setVisibleRowCount(10);
        structure_list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadLevelsTable(selectedStructure());
                refreshButtonsEnabled();
            }
        });

        JButton new_button = new JButton(Translator.translate("blinds.nueva"));
        new_button.addActionListener(e -> onNew());
        duplicate_button = new JButton(Translator.translate("blinds.duplicar"));
        duplicate_button.addActionListener(e -> onDuplicate());
        rename_button = new JButton(Translator.translate("blinds.renombrar"));
        rename_button.addActionListener(e -> onRename());
        delete_button = new JButton(Translator.translate("blinds.borrar"));
        delete_button.addActionListener(e -> onDelete());

        JPanel structure_buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        structure_buttons.add(new_button);
        structure_buttons.add(duplicate_button);
        structure_buttons.add(rename_button);
        structure_buttons.add(delete_button);

        JPanel structures_panel = new JPanel(new BorderLayout(0, 5));
        structures_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("blinds.estructuras")));
        JScrollPane list_scroll = new JScrollPane(structure_list);
        list_scroll.setPreferredSize(new Dimension(220, 240));
        // Lista + botones agrupados arriba para que ambas columnas alineen su
        // lista/tabla por arriba a la misma altura, aunque sus filas de botones
        // tengan distinta altura (CRUD de 4 vs 2 de niveles).
        JPanel structures_inner = new JPanel(new BorderLayout(0, 5));
        structures_inner.add(list_scroll, BorderLayout.CENTER);
        structures_inner.add(structure_buttons, BorderLayout.SOUTH);
        structures_panel.add(structures_inner, BorderLayout.NORTH);

        // --- Levels column (table + add/remove) ---
        levels_model = new DefaultTableModel(new Object[]{
            Translator.translate("blinds.ciega_pequena_col"),
            Translator.translate("blinds.ciega_grande_col")}, 0);
        levels_table = new JTable(levels_model);
        levels_table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        levels_table.getTableHeader().setReorderingAllowed(false);
        levels_model.addTableModelListener(this::onLevelCellChanged);

        add_level_button = new JButton(Translator.translate("blinds.anadir_nivel"));
        add_level_button.addActionListener(e -> onAddLevel());
        remove_level_button = new JButton(Translator.translate("blinds.quitar_nivel"));
        remove_level_button.addActionListener(e -> onRemoveLevel());

        JPanel level_buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        level_buttons.add(add_level_button);
        level_buttons.add(remove_level_button);

        JPanel levels_panel = new JPanel(new BorderLayout(0, 5));
        levels_border = BorderFactory.createTitledBorder(Translator.translate("blinds.niveles"));
        levels_panel.setBorder(levels_border);
        JScrollPane table_scroll = new JScrollPane(levels_table);
        table_scroll.setPreferredSize(new Dimension(240, 240));
        JPanel levels_inner = new JPanel(new BorderLayout(0, 5));
        levels_inner.add(table_scroll, BorderLayout.CENTER);
        levels_inner.add(level_buttons, BorderLayout.SOUTH);
        levels_panel.add(levels_inner, BorderLayout.NORTH);

        // --- Bottom buttons ---
        JButton save_button = new JButton(Translator.translate("ui.guardar"));
        save_button.addActionListener(e -> onSave());
        JButton close_button = new JButton(Translator.translate("ui.cerrar"));
        close_button.addActionListener(e -> onClose());

        JPanel button_panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        button_panel.add(save_button);
        button_panel.add(close_button);

        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.add(structures_panel, BorderLayout.WEST);
        center.add(levels_panel, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.add(center, BorderLayout.CENTER);
        content.add(button_panel, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(save_button);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(BlindStructureManagerDialog.this);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                if (isModal()) {
                    try {
                        Init.CURRENT_MODAL_DIALOG.removeLast();
                    } catch (Exception ex) {
                    }
                }
            }
        });

        if (!structure_model.isEmpty()) {
            structure_list.setSelectedIndex(0);
        } else {
            refreshButtonsEnabled();
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1.1f);
        ((TitledBorder) structures_panel.getBorder()).setTitleFont(structure_list.getFont());
        levels_border.setTitleFont(structure_list.getFont());
        levels_table.getTableHeader().setFont(structure_list.getFont());
        levels_table.setRowHeight(structure_list.getFontMetrics(structure_list.getFont()).getHeight() + 6);

        pack();
        setLocationRelativeTo(owner);
    }

    // ----- Selection / table population ---------------------------------------

    private String selectedStructure() {
        return structure_list.getSelectedValue();
    }

    private void loadLevelsTable(String name) {
        loading_table = true;
        try {
            levels_model.setRowCount(0);
            if (name != null && working.containsKey(name)) {
                for (double[] lvl : working.get(name)) {
                    levels_model.addRow(new Object[]{Helpers.money2String(lvl[0]), Helpers.money2String(lvl[1])});
                }
                levels_border.setTitle(Translator.translate("blinds.niveles") + " — " + name);
            } else {
                levels_border.setTitle(Translator.translate("blinds.niveles"));
            }
        } finally {
            loading_table = false;
        }
        repaint();
    }

    private void refreshButtonsEnabled() {
        boolean sel = selectedStructure() != null;
        duplicate_button.setEnabled(sel);
        rename_button.setEnabled(sel);
        delete_button.setEnabled(sel);
        add_level_button.setEnabled(sel);
        remove_level_button.setEnabled(sel);
    }

    // ----- Live cell commit ---------------------------------------------------

    private void onLevelCellChanged(javax.swing.event.TableModelEvent e) {
        if (loading_table || e.getType() != javax.swing.event.TableModelEvent.UPDATE) {
            return;
        }
        String name = selectedStructure();
        if (name == null) {
            return;
        }
        int row = e.getFirstRow();
        int col = e.getColumn();
        if (row < 0 || col < 0 || row >= working.get(name).length) {
            return;
        }
        Double parsed = parseBlind((String) levels_model.getValueAt(row, col));
        if (parsed == null) {
            // Revert the bad edit to the last good value and warn.
            loading_table = true;
            levels_model.setValueAt(Helpers.money2String(working.get(name)[row][col]), row, col);
            loading_table = false;
            java.awt.Toolkit.getDefaultToolkit().beep();
            return;
        }
        working.get(name)[row][col] = parsed;
        dirty = true;
    }

    // Accepts the locale decimal separator (the app shows blinds with a comma in
    // Spanish). Returns null if the text is not a positive number.
    private static Double parseBlind(String text) {
        if (text == null) {
            return null;
        }
        try {
            double v = Double.parseDouble(text.trim().replace(",", "."));
            if (Double.isNaN(v) || v <= 0) {
                return null;
            }
            return v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ----- Structure CRUD -----------------------------------------------------

    private void onNew() {
        String name = promptName(null);
        if (name == null) {
            return;
        }
        working.put(name, BlindStructure.defaultLevels());
        structure_model.addElement(name);
        structure_list.setSelectedValue(name, true);
        dirty = true;
    }

    private void onDuplicate() {
        String src = selectedStructure();
        if (src == null) {
            return;
        }
        String name = uniqueCopyName(src);
        // Deep-copy the source ladder.
        double[][] srcLevels = working.get(src);
        double[][] copy = new double[srcLevels.length][];
        for (int i = 0; i < srcLevels.length; i++) {
            copy[i] = new double[]{srcLevels[i][0], srcLevels[i][1]};
        }
        working.put(name, copy);
        structure_model.addElement(name);
        structure_list.setSelectedValue(name, true);
        dirty = true;
    }

    private void onRename() {
        String old = selectedStructure();
        if (old == null) {
            return;
        }
        String name = promptName(old);
        if (name == null || name.equals(old)) {
            return;
        }
        // Rebuild the map preserving order, swapping only the renamed key.
        LinkedHashMap<String, double[][]> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, double[][]> en : working.entrySet()) {
            if (en.getKey().equals(old)) {
                rebuilt.put(name, en.getValue());
            } else {
                rebuilt.put(en.getKey(), en.getValue());
            }
        }
        working.clear();
        working.putAll(rebuilt);
        int idx = structure_model.indexOf(old);
        structure_model.set(idx, name);
        structure_list.setSelectedValue(name, true);
        dirty = true;
    }

    private void onDelete() {
        String name = selectedStructure();
        if (name == null) {
            return;
        }
        if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("blinds.confirmar_borrar", name)) != 0) {
            return;
        }
        working.remove(name);
        structure_model.removeElement(name);
        dirty = true;
        if (!structure_model.isEmpty()) {
            structure_list.setSelectedIndex(0);
        } else {
            loadLevelsTable(null);
            refreshButtonsEnabled();
        }
    }

    // ----- Level add / remove -------------------------------------------------

    private void onAddLevel() {
        String name = selectedStructure();
        if (name == null) {
            return;
        }
        double[][] cur = working.get(name);
        double sb, bb;
        if (cur.length > 0) {
            // Sensible next step: double the current top small blind, snapped to
            // the one-decimal money resolution; big blind defaults to twice the
            // small blind (the user may then edit it freely).
            sb = Helpers.doubleClean(cur[cur.length - 1][0] * 2);
            bb = Helpers.doubleClean(sb * 2);
        } else {
            sb = 25;
            bb = 50;
        }
        double[][] grown = new double[cur.length + 1][];
        System.arraycopy(cur, 0, grown, 0, cur.length);
        grown[cur.length] = new double[]{sb, bb};
        working.put(name, grown);
        loading_table = true;
        levels_model.addRow(new Object[]{Helpers.money2String(sb), Helpers.money2String(bb)});
        loading_table = false;
        int last = levels_model.getRowCount() - 1;
        levels_table.setRowSelectionInterval(last, last);
        levels_table.scrollRectToVisible(levels_table.getCellRect(last, 0, true));
        dirty = true;
    }

    private void onRemoveLevel() {
        String name = selectedStructure();
        if (name == null) {
            return;
        }
        int[] rows = levels_table.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        double[][] cur = working.get(name);
        ArrayList<double[]> kept = new ArrayList<>();
        for (int i = 0; i < cur.length; i++) {
            boolean drop = false;
            for (int r : rows) {
                if (r == i) {
                    drop = true;
                    break;
                }
            }
            if (!drop) {
                kept.add(cur[i]);
            }
        }
        working.put(name, kept.toArray(new double[0][]));
        loadLevelsTable(name);
        dirty = true;
    }

    // ----- Save / close -------------------------------------------------------

    private void onSave() {
        // Validate every structure; stop at the first offender, select it and
        // explain why, so the user lands exactly on what needs fixing.
        for (Map.Entry<String, double[][]> en : working.entrySet()) {
            String err = BlindStructure.validateName(en.getKey());
            if (err == null) {
                err = BlindStructure.validateLevels(en.getValue());
            }
            if (err != null) {
                structure_list.setSelectedValue(en.getKey(), true);
                Helpers.mostrarMensajeError(this,
                        Translator.translate("blinds.estructura_invalida", en.getKey(), Translator.translate(err)));
                return;
            }
        }
        ArrayList<BlindStructure> out = new ArrayList<>();
        for (Map.Entry<String, double[][]> en : working.entrySet()) {
            out.add(new BlindStructure(en.getKey(), en.getValue()));
        }
        BlindStructure.saveAll(out);
        dirty = false;
        dispose();
    }

    private void onClose() {
        if (dirty && Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("blinds.descartar_cambios")) != 0) {
            return;
        }
        dispose();
    }

    // ----- Name prompting / uniqueness ---------------------------------------

    // Prompts for a structure name, validating format and uniqueness (the
    // current name, when renaming, is allowed). Returns null on cancel/invalid.
    private String promptName(String current) {
        JLabel prompt = new JLabel(Translator.translate("blinds.nombre_estructura"));
        JTextField field = new JTextField(current != null ? current : "", 18);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(prompt, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        // Construimos el JOptionPane a mano para poder agrandar la fuente con el
        // mecanismo estándar de la app: updateFonts escala desde el tamaño POR
        // DEFECTO de cada componente (~12pt) x zoom, NO desde el tamaño base de
        // GUI_FONT (que es ~1pt al venir de createFont). Así la etiqueta, la caja y
        // los botones Aceptar/Cancelar quedan legibles y en la fuente de la app.
        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        javax.swing.JDialog d = pane.createDialog(this, getTitle());
        Helpers.updateFonts(pane, Helpers.GUI_FONT, 1.15f);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
        d.dispose();
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())) {
            return null;
        }
        String name = field.getText().trim();
        String err = BlindStructure.validateName(name);
        if (err != null) {
            Helpers.mostrarMensajeError(this, err);
            return null;
        }
        if ((current == null || !name.equals(current)) && working.containsKey(name)) {
            Helpers.mostrarMensajeError(this, "blinds.err_name_dup");
            return null;
        }
        return name;
    }

    private String uniqueCopyName(String base) {
        String suffix = " " + Translator.translate("blinds.copia_sufijo");
        String candidate = base + suffix;
        int n = 2;
        while (working.containsKey(candidate)) {
            candidate = base + suffix + " " + n;
            n++;
        }
        // Guard against the (rare) over-long name once the suffix is appended.
        if (candidate.length() > BlindStructure.MAX_NAME_LENGTH) {
            candidate = candidate.substring(0, BlindStructure.MAX_NAME_LENGTH);
        }
        return candidate;
    }
}
