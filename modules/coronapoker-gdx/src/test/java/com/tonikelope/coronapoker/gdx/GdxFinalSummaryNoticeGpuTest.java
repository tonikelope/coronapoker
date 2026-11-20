/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

@Tag("certification")
final class GdxFinalSummaryNoticeGpuTest {

    @Test
    void serverExitNoticeRendersOverBlurredSummaryAndOwnsThePointer() {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(960, 540);
        config.disableAudio(true);
        var failure = new AtomicReference<Throwable>();
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void render() {
                if (Gdx.graphics.getDeltaTime() <= 0f) return;
                try {
                    long window = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
                    assertEquals(GLFW.GLFW_FALSE,
                            GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE));
                    verifyNotice();
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verifyNotice() throws Exception {
        var table = GdxFinalSummaryNoticeTest.table();
        try {
            table.create();
            table.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            var barrier = GdxFinalSummaryNoticeTest.close(
                    table, TableSessionSummary.CloseReason.COMPLETED, true);
            var field = CoronaPokerGdxTable.class.getDeclaredField("viewport");
            field.setAccessible(true);
            var viewport = (Viewport) field.get(table);
            var menu = CoronaPokerGdxTable.finalSummaryHeaderLayout(
                    viewport.getWorldWidth(), viewport.getWorldHeight()).navButton(0);
            var point = viewport.project(new Vector3(
                    menu.x + menu.width / 2f, menu.y + menu.height / 2f, 0f));
            int x = Math.round(point.x);
            int y = Gdx.graphics.getHeight() - Math.round(point.y);

            // Use the same frame delta for each render, with the dismissible
            // notice still present and no input sent to the window.
            for (int frame = 0; frame < 25; frame++) table.render();
            var noBlur = new Properties();
            noBlur.setProperty(GdxSettingsContract.MODAL_BLUR_KEY, "false");
            GdxVisualEffects.synchronize(noBlur);
            table.render();
            byte[] sharpButton = readMenuButton(x, y);
            byte[] sharpDialog = readDialogCenter();

            GdxVisualEffects.synchronize(null);
            table.render();
            assertFalse(Arrays.equals(sharpButton, readMenuButton(x, y)),
                    "The summary's menu button should be part of the blurred backdrop");
            assertTrue(Arrays.equals(sharpDialog, readDialogCenter()),
                    "The center of the foreground notice should remain sharp");
            assertTrue(table.inputProcessor().touchDown(x, y, 0, Input.Buttons.LEFT));
            assertTrue(table.inputProcessor().touchUp(x, y, 0, Input.Buttons.LEFT));
            assertFalse(barrier.isDone());

            assertTrue(table.resolveActiveDialogChoice(true));
            table.advanceDialogState();
            table.render();
            assertFalse(Arrays.equals(sharpDialog, readDialogCenter()),
                    "Acknowledging the foreground notice should reveal the summary");
            table.inputProcessor().touchDown(x, y, 0, Input.Buttons.LEFT);
            table.inputProcessor().touchUp(x, y, 0, Input.Buttons.LEFT);
            assertTrue(barrier.isDone());
        } finally {
            GdxVisualEffects.synchronize(null);
            table.dispose();
        }
    }

    private static byte[] readMenuButton(int x, int y) {
        return ScreenUtils.getFrameBufferPixels(x - 20,
                Gdx.graphics.getHeight() - y - 10, 40, 20, false);
    }

    private static byte[] readDialogCenter() {
        return ScreenUtils.getFrameBufferPixels(Gdx.graphics.getWidth() / 2 - 20,
                Gdx.graphics.getHeight() / 2 - 10, 40, 20, false);
    }
}
