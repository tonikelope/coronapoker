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

import static java.awt.Toolkit.getDefaultToolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tonikelope
 */
public class ClipboardSpy implements Runnable, ClipboardOwner, ClipboardChangeObservable {

    private static final int SLEEP = 250;

    private static final Logger LOG = Logger.getLogger(ClipboardSpy.class.getName());

    private final Clipboard _sysClip;

    private boolean _notified;

    private final ConcurrentLinkedQueue<ClipboardChangeObserver> _observers;

    private Transferable _contents;

    private final Object _secure_notify_lock;

    private volatile boolean _enabled;

    public ClipboardSpy() {
        _sysClip = getDefaultToolkit().getSystemClipboard();
        _notified = false;
        _enabled = false;
        _contents = null;
        _secure_notify_lock = new Object();
        _observers = new ConcurrentLinkedQueue<>();
    }

    @Override
    public Transferable getContents() {
        return _contents;
    }

    private void _setEnabled(boolean enabled) {

        _enabled = enabled;

        if (_enabled) {

            _contents = getClipboardContents();

            notifyChangeToMyObservers();

            gainOwnership(_contents);

            LOG.log(Level.INFO, "{0} Monitoring clipboard ON...", Thread.currentThread().getName());

        } else {
            LOG.log(Level.INFO, "{0} Monitoring clipboard OFF...", Thread.currentThread().getName());
        }
    }

    public void secureNotify() {
        synchronized (_secure_notify_lock) {

            _notified = true;

            _secure_notify_lock.notifyAll();
        }
    }

    @Override
    public void run() {

        while (!_notified) {
            synchronized (_secure_notify_lock) {
                try {
                    _secure_notify_lock.wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ClipboardSpy.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    @Override
    public void lostOwnership(Clipboard c, Transferable t) {

        if (_enabled) {

            _contents = getClipboardContents();

            notifyChangeToMyObservers();

            gainOwnership(_contents);
        }
    }

    private Transferable getClipboardContents() {

        boolean error;

        Transferable c = null;

        do {
            error = false;

            try {

                c = _sysClip.getContents(this);

            } catch (Exception ex) {

                error = true;

                Helpers.pausar(SLEEP);
            }

        } while (error);

        return c;
    }

    private void gainOwnership(Transferable t) {

        boolean error;

        do {
            error = false;

            try {

                _sysClip.setContents(t, this);

            } catch (Exception ex) {

                error = true;

                Helpers.pausar(SLEEP);
            }

        } while (error);

    }

    @Override
    public void attachObserver(ClipboardChangeObserver observer) {

        if (!_observers.contains(observer)) {

            _observers.add(observer);
        }

        if (!_observers.isEmpty() && !_enabled) {

            _setEnabled(true);
        }
    }

    @Override
    public void detachObserver(ClipboardChangeObserver observer) {

        if (_observers.contains(observer)) {

            _observers.remove(observer);

            if (_observers.isEmpty() && _enabled) {

                _setEnabled(false);
            }
        }
    }

    @Override
    public void notifyChangeToMyObservers() {

        _observers.forEach((o) -> {
            o.notifyClipboardChange();
        });
    }

}
