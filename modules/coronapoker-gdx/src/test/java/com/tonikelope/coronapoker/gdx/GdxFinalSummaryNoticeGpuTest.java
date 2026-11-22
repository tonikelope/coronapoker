/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

@Tag("certification")
final class GdxFinalSummaryNoticeGpuTest {

    @Test
    void finalSummaryShowsAvatarsAndKeepsTheServerNoticeModal(@TempDir Path directory) {
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
                    verifyAvatars(directory);
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

    private static void verifyAvatars(Path directory) throws Exception {
        int localColor = 0xfa21ceff;
        int remoteColor = 0x13e4ccff;
        int botColor = 0xf9a317ff;
        Path localImage = portrait(directory, "local", localColor);
        Path remoteImage = portrait(directory, "departed", remoteColor);
        Path botImage = portrait(directory, "bot", botColor);
        var local = participant("local", localImage, true, false);
        var remote = participant("departed", remoteImage, false, false);
        var customBot = participant("custom$", botImage, false, true);
        var unknownHuman = participant("no-picture", directory.resolve("missing.png"), false, false);
        var standardBot = participant("CoronaBot$1", null, false, true);
        try (var lobby = new LobbySession(snapshot(List.of(local, remote, customBot,
                unknownHuman, standardBot)), command -> CompletableFuture.completedFuture(null))) {
            var table = GdxFinalSummaryNoticeTest.table(lobby);
            try {
                table.create();
                table.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                var avatar = CoronaPokerGdxTable.class.getDeclaredMethod("tableAvatar", String.class);
                avatar.setAccessible(true);
                avatar.invoke(table, "departed");
                assertSame(field(table, "avatarDefault"), avatar.invoke(table, "no-picture"));
                assertSame(field(table, "avatarBot"), avatar.invoke(table, "CoronaBot$1"));
                lobby.publish(snapshot(List.of(local, customBot, unknownHuman, standardBot)));
                Files.delete(remoteImage);
                var balances = List.of("local", "departed", "custom$", "no-picture", "CoronaBot$1").stream()
                        .map(nick -> new TableSessionSummary.PlayerBalance(nick, 20d, 20d, 0))
                        .toList();
                var barrier = new CompletableFuture<Void>();
                var summary = new TableSessionSummary("local", 1, 15L, 1_000L,
                        TableSessionSummary.CloseReason.COMPLETED, balances);
                table.acceptEvent(new TableVisualEvent.CloseTable(2L, summary,
                        TableSnapshot.Street.FINISHED), barrier);
                var openedAt = CoronaPokerGdxTable.class.getDeclaredField("finalSummaryOpenedAt");
                openedAt.setAccessible(true);
                openedAt.setFloat(table, -10f);
                table.render();
                byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0,
                        Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), false);
                assertPortraitVisible(pixels, localColor, "local player's portrait");
                assertPortraitVisible(pixels, remoteColor, "departed player's cached portrait");
                assertPortraitVisible(pixels, botColor, "custom bot's portrait");
                assertFalse(barrier.isDone());
            } finally {
                table.dispose();
            }
        }
    }

    private static LobbySnapshot snapshot(List<LobbyParticipant> participants) {
        return new LobbySnapshot("local", "local", "localhost:2345", true,
                LobbySnapshot.Phase.IN_GAME, "", participants, List.of(), null, false);
    }

    private static LobbyParticipant participant(String name, Path avatar, boolean local, boolean bot) {
        return new LobbyParticipant(name, avatar, local, local, bot, true,
                false, true, 0, 0);
    }

    private static Path portrait(Path directory, String name, int rgba) {
        Path path = directory.resolve(name + ".png");
        var image = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        try {
            image.setColor(rgba);
            image.fill();
            PixmapIO.writePNG(Gdx.files.absolute(path.toString()), image);
        } finally {
            image.dispose();
        }
        return path;
    }

    private static void assertPortraitVisible(byte[] pixels, int rgba, String label) {
        int matching = 0;
        for (int i = 0; i < pixels.length; i += 4) {
            if (Math.abs(Byte.toUnsignedInt(pixels[i]) - (rgba >>> 24)) < 4
                    && Math.abs(Byte.toUnsignedInt(pixels[i + 1]) - (rgba >>> 16 & 0xff)) < 4
                    && Math.abs(Byte.toUnsignedInt(pixels[i + 2]) - (rgba >>> 8 & 0xff)) < 4) {
                matching++;
            }
        }
        assertTrue(matching >= 40, label + " should render in the settled cards");
    }

    private static Object field(CoronaPokerGdxTable table, String name) throws Exception {
        var field = CoronaPokerGdxTable.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(table);
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
