/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.Timer;

//Thanks -> https://stackoverflow.com/a/51201622
public abstract class ComponentResizeEndListener extends ComponentAdapter implements ActionListener {

    private final Timer timer;

    public ComponentResizeEndListener() {
        this(250);
    }

    public ComponentResizeEndListener(int delayMS) {
        timer = new Timer(delayMS, this);
        timer.setRepeats(false);
        timer.setCoalesce(false);
    }

    @Override
    public void componentResized(ComponentEvent e) {
        timer.restart();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        resizeTimedOut();
    }

    public abstract void resizeTimedOut();
}
