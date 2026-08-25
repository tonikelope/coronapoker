package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

final class ToggleSwitchTooltipTest {

    @Test
    void tooltipAssignedThroughSwitchAppearsOnlyOnItsNonInteractiveLabel() {
        SettingsUI.ToggleSwitch toggle = new SettingsUI.ToggleSwitch(false);
        JLabel label = new JLabel("Option");

        toggle.pairLabel(label);
        toggle.setToolTipText("Explanation");

        assertEquals("Explanation", label.getToolTipText());
        assertNull(toggle.getToolTipText());
        label.dispatchEvent(new java.awt.event.MouseEvent(label,
                java.awt.event.MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false));
        assertFalse(toggle.isSelected());
    }

    @Test
    void tooltipAssignedDirectlyToLabelNeverAppearsOnSwitch() {
        SettingsUI.ToggleSwitch toggle = new SettingsUI.ToggleSwitch(false);
        JLabel label = new JLabel("Option");

        toggle.pairLabel(label);
        label.setToolTipText("First language");
        assertNull(toggle.getToolTipText());

        label.setToolTipText("Second language");
        assertEquals("Second language", label.getToolTipText());
        assertNull(toggle.getToolTipText());
    }

}
