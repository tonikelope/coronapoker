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

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the screen awake while the game is fullscreen.
 *
 * <ul>
 * <li><b>Windows</b>: {@code SetThreadExecutionState} (Kernel32, via JNA).
 * Tells the OS not to turn off the display or suspend — it does NOT simulate
 * input (no mouse/keyboard). The state is per-thread, so this is always invoked
 * from the same timer thread; the OS releases it on its own when the app
 * exits.</li>
 * <li><b>Linux and macOS</b>: there is no native inhibition API that's clean
 * and uniform across both (on Linux it would differ between X11/Wayland, and
 * D-Bus would pull in another dependency), so a no-op key (F15) is sent via
 * Robot instead: unlike a mouse jiggle it doesn't move the cursor or trigger
 * hover events, and is harmless. This is the only place where input is
 * simulated, deliberately, for lack of a clean native alternative, and it works
 * the same under X11 and Wayland.</li>
 * </ul>
 *
 * Any failure on the Windows native path (JNA missing, etc.) is caught and
 * degrades to the key fallback without breaking — this class never propagates.
 *
 * @author tonikelope
 */
public final class ScreenWakeLock {

    private static final Logger LOGGER = Logger.getLogger(ScreenWakeLock.class.getName());

    private static volatile boolean native_unavailable = false;

    private ScreenWakeLock() {
    }

    /**
     * Refreshes the wake-lock based on the fullscreen state. Meant to be called
     * periodically (the anti-screensaver timer). Idempotent and silent on
     * failure.
     *
     * @param fullscreen true if the game is fullscreen (the only state in which
     * the screen is kept awake)
     * @param fallback_robot reusable Robot for the key fallback (Linux/macOS);
     * may be null, in which case the fallback simply does nothing
     */
    public static void refresh(boolean fullscreen, Robot fallback_robot) {

        // Windows: native API, no input simulation.
        if (Helpers.OSValidator.isWindows() && !native_unavailable) {
            try {
                // ES_CONTINUOUS makes the state persist for this thread; adding the display/system
                // flags while fullscreen keeps the screen awake, omitting them (ES_CONTINUOUS
                // alone) releases it.
                int flags = WinBase.ES_CONTINUOUS
                        | (fullscreen ? (WinBase.ES_DISPLAY_REQUIRED | WinBase.ES_SYSTEM_REQUIRED) : 0);
                Kernel32.INSTANCE.SetThreadExecutionState(flags);
                return;
            } catch (Throwable t) {
                native_unavailable = true;
                LOGGER.log(Level.WARNING, "Native screen wake-lock unavailable — falling back to no-op key", t);
            }
        }

        // Linux / macOS (or Windows if the native path failed): no-op key. Doesn't move the
        // cursor, so it doesn't trigger hover events; fullscreen only.
        if (fullscreen && fallback_robot != null) {
            try {
                fallback_robot.keyPress(KeyEvent.VK_F15);
                fallback_robot.keyRelease(KeyEvent.VK_F15);
            } catch (Exception ex) {
                // VK_F15 may be unmapped on some platforms: ignore it (that tick simply doesn't
                // refresh the idle timer).
                LOGGER.log(Level.FINE, "No-op key fallback failed", ex);
            }
        }
    }
}
