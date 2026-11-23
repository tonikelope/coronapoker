/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.IdentityTrustStore;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.StatsRepository;
import com.tonikelope.coronapoker.core.UpdateService;
import com.tonikelope.coronapoker.core.UpdaterService;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

@Tag("certification")
final class GdxFrontendSettingsGpuTest {
    private static final int WIDTH = 960, HEIGHT = 540;

    @Test
    void menuAndLobbySettingsChangeOnPressAndTheImageViewerKeepsModalOwnership(
            @TempDir Path dir) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setWindowedMode(WIDTH, HEIGHT);
        var failure = new AtomicReference<Throwable>();
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void render() {
                if (Gdx.graphics.getDeltaTime() <= 0f) return;
                try {
                    long window = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
                    assertEquals(GLFW.GLFW_FALSE,
                            GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE));
                    verifyStepperSignCenters();
                    verify(dir);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verifyStepperSignCenters() {
        int screenWidth = Gdx.graphics.getBackBufferWidth();
        int screenHeight = Gdx.graphics.getBackBufferHeight();
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
        var shapes = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
        try {
            shapes.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4()
                    .setToOrtho2D(0, 0, screenWidth, screenHeight));
            shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < 2; i++) {
                GdxUiButtonStyle.drawStepperSign(shapes, 100 + 80 * i, 100,
                        60, 44, i == 1, com.badlogic.gdx.graphics.Color.WHITE, 1f);
            }
            shapes.end();
            for (int i = 0; i < 2; i++) {
                byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(
                        100 + 80 * i, 100, 60, 44, false);
                int minX = 60, maxX = -1, minY = 44, maxY = -1;
                for (int y = 0; y < 44; y++) {
                    for (int x = 0; x < 60; x++) {
                        if (Byte.toUnsignedInt(pixels[4 * (60 * y + x)]) < 128) continue;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
                assertTrue(maxX > minX && maxY > minY);
                assertEquals(59, minX + maxX, "The sign is centered horizontally");
                assertEquals(43, minY + maxY, "The sign is centered vertically");
                assertEquals(i == 1, maxX - minX == maxY - minY);
            }
        } finally {
            shapes.dispose();
        }
    }

    private static void verify(Path dir) throws Exception {
        try (var prefs = new PreferencesService(dir.resolve("settings.properties"));
                var database = new DatabaseService(dir.resolve("test.sqlite").toString());
                var updates = new UpdateService("27.81", 1, () -> "27.81");
                var updater = new UpdaterService(() -> dir.resolve("unused.jar"), command -> {})) {
            prefs.start();
            database.start();
            updates.start();
            updater.start();
            prefs.properties().setProperty("mostrar_videos_poker", "false");
            var presentation = new GdxGamePresentationSettings(prefs, ModMediaCatalog.discover(dir));
            var screen = new GdxFrontendScreen(prefs,
                    request -> { throw new AssertionError("Unexpected lobby creation"); },
                    new RecoverableGameRepository(database), IdentityTrustStore.unavailable(),
                    new SecureRandom(), session -> {}, () -> {},
                    presentation, new GdxGameText("es"), language -> {},
                    updates, updater, new StatsRepository(database));
            try {
                screen.holdStartupAudio();
                screen.create();
                screen.resize(WIDTH, HEIGHT);
                invoke(screen, "cancelUpdateCheck", new Class<?>[0]);
                screen.completeStartupReveal();
                verifyAudioDeviceAlignment(screen);
                verifyMenuDialogSlide(screen);
                verifyMenuModalBackdrop(screen);
                verifySettings(screen, presentation, GdxSettingsSession.Context.MENU);
                verifyImageViewer(screen);
                try (var lobby = lobby()) {
                    screen.openLobby(lobby);
                    verifySettings(screen, presentation, GdxSettingsSession.Context.WAITING_ROOM);
                }
                verifyLobbyNoticeHandoff(screen);
            } finally {
                screen.dispose();
            }
            verifyLiveTable(prefs, presentation, null);
            try (var host = lobby()) {
                prefs.properties().setProperty(GdxSettingsContract.TABLE_VIEW_KEY, "2d");
                prefs.properties().setProperty(GdxSettingsContract.MODAL_BLUR_KEY, "false");
                GdxVisualEffects.synchronize(prefs.properties());
                verifyLiveTable(prefs, presentation, host);
            }
        }
    }

    private static void verifyAudioDeviceAlignment(GdxFrontendScreen screen)
            throws Exception {
        var shapes = (com.badlogic.gdx.graphics.glutils.ShapeRenderer) field(screen, "shapes");
        var fontType = com.badlogic.gdx.graphics.g2d.BitmapFont.class;
        var dropdownType = field(screen, "dropdown").getClass();
        Object dropdown = List.of(dropdownType.getEnumConstants()).stream()
                .filter(item -> item.toString().equals("AUDIO_OUTPUT")).findFirst().orElseThrow();
        var texts = (List<?>) field(screen, "texts");
        var row = GdxSettingsLayout.audioDeviceChoiceRow(120f, 320f, 1_400f, 72f);
        var longDevice = "USB Headset Audio Device With Long Manufacturer and Model Names ".repeat(3);
        for (String name : List.of("PREDETERMINADO DEL SISTEMA *", longDevice)) {
            int before = texts.size();
            shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
            try {
                invoke(screen, "settingsAudioDeviceDropdownChoice",
                        new Class<?>[]{float.class, float.class, float.class, float.class,
                            String.class, String.class, dropdownType, boolean.class},
                        row.bounds().x, row.bounds().y, row.bounds().width, row.bounds().height,
                        "DISPOSITIVO DE SALIDA", name, dropdown, true);
            } finally {
                shapes.end();
            }
            Object value = texts.get(before + 1);
            Object font = field(value, "font");
            String display = (String) field(value, "text");
            float width = (float) invoke(screen, "textWidth",
                    new Class<?>[]{fontType, String.class}, font, display);
            assertEquals(row.value().x + row.value().width,
                    (float) field(value, "x") + width, 0.1f);
            Object fitted = invoke(screen, "fittedTextItem",
                    new Class<?>[]{fontType, String.class, float.class, float.class,
                        com.badlogic.gdx.graphics.Color.class, boolean.class, float.class},
                    field(screen, "uiFont"), name, 0f, 0f, com.badlogic.gdx.graphics.Color.WHITE,
                    false, row.value().width - 12f);
            assertSame(field(fitted, "font"), font);
            assertEquals(field(fitted, "text"), display);
        }
    }

    private static void verifyMenuDialogSlide(GdxFrontendScreen screen)
            throws Exception {
        var slide = (GdxDialogSlide) field(screen, "menuDialogSlide");
        var choice = GdxAppearanceOptions.DIALOG_SLIDE_DIRECTION;
        var properties = (Properties) field(screen, "initialProperties");
        var saved = new Properties();
        saved.putAll(properties);
        int[][] directions = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}, {0, 0}};
        try {
            for (int index = 0; index < directions.length; index++) {
                GdxAppearanceOptions.select(choice, properties, index);
                invoke(screen, "openNewGame", new Class<?>[]{NewGameConnectionDraft.Mode.class},
                        NewGameConnectionDraft.Mode.CREATE);
                screen.render();
                int x = directions[index][0], y = directions[index][1];
                assertSlideDirection(slide, x, y);
                slide.advance(1f);
                screen.render();
                assertSlideDirection(slide, 0, 0);
                invoke(screen, "cancelOrReturnToMenu", new Class<?>[0]);
                if (x != 0 || y != 0) {
                    assertTrue(slide.closing());
                    slide.advance(0.08f);
                    screen.render();
                    assertSlideDirection(slide, x, y);
                    assertEquals("NEW_GAME", field(screen, "surface").toString());
                    slide.advance(1f);
                } else {
                    assertFalse(slide.closing());
                }
                screen.render();
                assertEquals("MENU", field(screen, "surface").toString());
            }
            GdxAppearanceOptions.select(choice, properties, 2);
            screen.requestApplicationExit();
            screen.render();
            assertSlideDirection(slide, 0, 0);
            invoke(screen, "cancelApplicationExit", new Class<?>[0]);
            assertFalse(slide.closing());
            assertFalse((boolean) field(screen, "applicationExitConfirmation"));
        } finally {
            restoreKey(properties, saved, choice.key());
        }
    }

    private static void assertSlideDirection(GdxDialogSlide slide, int x, int y) {
        assertEquals(x, (int) Math.signum(slide.offsetX(WIDTH)));
        assertEquals(y, (int) Math.signum(slide.offsetY(HEIGHT)));
    }

    private static void verifyMenuModalBackdrop(GdxFrontendScreen screen)
            throws Exception {
        var properties = (Properties) field(screen, "initialProperties");
        var saved = new Properties();
        saved.putAll(properties);
        properties.setProperty("animaciones", "false");
        try {
            verifyMenuModalBackdropWithoutMotion(screen);
        } finally {
            restoreKey(properties, saved, "animaciones");
        }
    }

    private static void verifyMenuModalBackdropWithoutMotion(GdxFrontendScreen screen)
            throws Exception {
        screen.render();
        Object quotes = field(screen, "menuQuotes");
        assertFalse(((String) field(quotes, "current")).isBlank());
        float quoteSeconds = (float) field(quotes, "remaining");
        byte[] menuButton = ScreenUtils.getFrameBufferPixels(
                310, 375, 20, 20, false);

        invoke(screen, "openAboutDialog", new Class<?>[0]);
        screen.render();
        byte[] aboutBackdrop = capturedFrontendBackdrop(screen);
        invoke(screen, "closeAboutDialog", new Class<?>[0]);

        invoke(screen, "openNewGame", new Class<?>[]{NewGameConnectionDraft.Mode.class},
                NewGameConnectionDraft.Mode.CREATE);
        screen.render();
        assertEquals("NEW_GAME", field(screen, "surface").toString());
        assertEquals(quoteSeconds, (float) field(quotes, "remaining"));
        byte[] newGameBackdrop = capturedFrontendBackdrop(screen);
        assertArrayEquals(aboutBackdrop, newGameBackdrop,
                "Both dialogs must capture the same background without menu buttons or the quote");
        assertFalse(java.util.Arrays.equals(menuButton,
                ScreenUtils.getFrameBufferPixels(310, 375, 20, 20, false)));

        invoke(screen, "cancelOrReturnToMenu", new Class<?>[0]);
        screen.render();
        assertEquals("MENU", field(screen, "surface").toString());
        assertTrue((float) field(quotes, "remaining") < quoteSeconds);
        assertArrayEquals(menuButton,
                ScreenUtils.getFrameBufferPixels(310, 375, 20, 20, false));
    }

    private static byte[] capturedFrontendBackdrop(GdxFrontendScreen screen)
            throws Exception {
        var buffer = (FrameBuffer) field(field(screen, "modalBackdropBlur"), "backdrop");
        assertNotNull(buffer, "The modal should capture its background for blur");
        buffer.begin();
        try {
            return ScreenUtils.getFrameBufferPixels(
                    0, 0, buffer.getWidth(), buffer.getHeight(), false);
        } finally {
            buffer.end();
        }
    }

    private static void verifyLobbyNoticeHandoff(GdxFrontendScreen screen)
            throws Exception {
        try (var lobby = lobby(LobbySnapshot.Phase.ERROR, "Server connection lost")) {
            screen.openLobby(lobby);
            assertEquals("Server connection lost", field(screen, "frontendNoticeMessage"));
            var table = GdxFinalSummaryNoticeTest.table();
            screen.transferLobbyNoticeTo(table);
            assertEquals("", field(screen, "frontendNoticeMessage"));
            GdxFinalSummaryNoticeTest.close(table,
                    com.tonikelope.coronapoker.table.TableSessionSummary.CloseReason.FAILURE, false);
            assertEquals("Server connection lost",
                    GdxFinalSummaryNoticeTest.activeDialog(table).message());
            assertFalse(table.handleFinalSummaryNavigationAction(0));
            assertTrue(table.resolveActiveDialogChoice(true));
            table.advanceDialogState();
            assertTrue(table.handleFinalSummaryNavigationAction(0));
            screen.returnFromTable(lobby);
            assertEquals("", field(screen, "frontendNoticeMessage"));
        }
    }

    private static void verifySettings(GdxFrontendScreen screen,
            GdxGamePresentationSettings presentation, GdxSettingsSession.Context context)
            throws Exception {
        invoke(screen, "openSettings", new Class<?>[]{GdxSettingsContract.Section.class},
                GdxSettingsContract.Section.APPEARANCE);
        Runnable renderSettings = settledSettingsRender(screen, screen::render);
        verifyDiscardFromEveryPage(screen, context, (Properties) field(screen, "initialProperties"),
                renderSettings, point -> click(screen, point));
        var session = (GdxSettingsSession) field(screen, "settingsSession");
        assertEquals(context, session.context());
        for (var section : session.sections()) {
            session.selectTab(session.sections().indexOf(section));
            List<?> pages = (List<?>) invoke(screen, "settingsSubpageLabels", new Class<?>[0]);
            for (int page = 0; page < Math.max(1, pages.size()); page++) {
                invoke(screen, "selectSettingsSubpage", new Class<?>[]{int.class}, page);
                screen.render();
                assertPressHits(screen, context + "/" + section + "/" + page);
            }
        }
        verifyPercentageTracks(screen, screen, screen::render, (Properties) field(screen, "initialProperties"));
        session.selectTab(session.sections().indexOf(GdxSettingsContract.Section.APPEARANCE));
        verifyZoomTracks(screen, screen, screen::render, (Properties) field(screen, "initialProperties"));
        verifyAngleTrack(screen, screen, screen::render, (Properties) field(screen, "initialProperties"));
        invoke(screen, "selectSettingsSubpage", new Class<?>[]{int.class}, GdxSettingsContract.appearanceGraphicsPageIndex());
        verifyFrameRateControl(screen, screen, screen::render, (Properties) field(screen, "initialProperties"));
        var modes = presentation.cardRenderingModes();
        assertEquals(List.of(GdxCardSmaaRenderer.RENDERING_PERFORMANCE, GdxCardSmaaRenderer.RENDERING_BALANCED,
                GdxCardSmaaRenderer.RENDERING_SMOOTH_AF), modes);
        for (int mode : modes) {
            var point = pressOption(screen, "CARD_RENDERING", modes.indexOf(mode));
            assertEquals(mode, presentation.requestedCardRenderingMode());
            screen.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        }
        invoke(screen, "selectSettingsSubpage", new Class<?>[]{int.class}, 0);
        for (String felt : List.of("azul", "verde", "azul", "verde", "azul")) {
            chooseFelt(screen, presentation, felt);
        }
        assertTrue(presentation.secretFelt());
        assertEquals(true, field(screen, "secretFeltTexture"));
        chooseFelt(screen, presentation, "azul");
        assertFalse(presentation.secretFelt());
        assertEquals(false, field(screen, "secretFeltTexture"));
        verifyDeckPreview(screen, presentation);
        invoke(screen, "closeSettings", new Class<?>[]{boolean.class}, false);
        renderSettings.run();
    }

    private static Runnable settledSettingsRender(Object owner, Runnable render) throws Exception {
        String fieldName = owner instanceof GdxFrontendScreen ? "menuDialogSlide" : "settingsSlide";
        var slide = (GdxDialogSlide) field(owner, fieldName);
        return () -> {
            render.run();
            if (slide.moving()) {
                slide.advance(1f);
                render.run();
            }
        };
    }

    @FunctionalInterface
    private interface SettingsClick {
        void at(Vector2 world) throws Exception;
    }

    private static void verifyDiscardFromEveryPage(Object owner, GdxSettingsSession.Context context,
            Properties props, Runnable render, SettingsClick click) throws Exception {
        var session = (GdxSettingsSession) field(owner, "settingsSession");
        var viewport = (Viewport) field(owner, "viewport");
        var prompt = GdxUiDialogStyle.confirmationLayout(viewport.getWorldWidth(), viewport.getWorldHeight());
        for (var section : session.sections()) {
            render.run();
            var initial = GdxSettingsLayout.frame(viewport.getWorldWidth(), viewport.getWorldHeight(),
                    session.sections().size(), 0);
            click.at(initial.mainTab(session.sections().indexOf(section)).getCenter(new Vector2()));
            assertEquals(section, session.section());
            var pages = (List<?>) invoke(owner, "settingsSubpageLabels", new Class<?>[0]);
            var frame = GdxSettingsLayout.frame(viewport.getWorldWidth(), viewport.getWorldHeight(),
                    session.sections().size(), pages.size());
            for (int page = 0; page < Math.max(1, pages.size()); page++) {
                render.run();
                if (pages.size() > 1) {
                    click.at(frame.subTab(page).getCenter(new Vector2()));
                    assertEquals(page, session.subpageIndex());
                }
                render.run();
                if (section != GdxSettingsContract.Section.DEBUG) verifyScrollableSettingsRows(owner, render);
                var baseline = GdxSettingsContract.snapshot(props);
                // A pending edit may also originate on another tab when the current rule is locked.
                NewGameTableDraft draft = owner instanceof GdxFrontendScreen
                        && section == GdxSettingsContract.Section.GAME
                        ? (NewGameTableDraft) field(owner, "settingsTable") : null;
                var openingDraft = draft == null ? null : draft.snapshot();
                boolean onlyGameDraftChanged = draft != null;
                if (draft != null) {
                    switch (page) {
                        case 0 -> draft.setIncreaseBlinds(!draft.increaseBlinds());
                        case 1 -> draft.setRebuy(!draft.rebuy());
                        case 2 -> draft.setCustomBots(!draft.customBots());
                        case 3 -> draft.setIwtsth(!draft.iwtsth());
                        default -> throw new AssertionError(page);
                    }
                } else if (owner instanceof CoronaPokerGdxTable table
                        && section == GdxSettingsContract.Section.GAME && page == 3 && (boolean) field(table, "tableHost")) {
                    invoke(table, "toggleDraftHandLimit", new Class<?>[0]);
                    onlyGameDraftChanged = true;
                } else if (section == GdxSettingsContract.Section.DEBUG) {
                    click.at(GdxSettingsLayout.debugPerformanceRow(frame.content()).getCenter(new Vector2()));
                } else {
                    editForDiscard(props, section, page);
                }
                render.run();
                var discard = prompt.positiveButton().getCenter(new Vector2());
                String label = context + "/" + section + "/" + page;
                if (section == GdxSettingsContract.Section.APPEARANCE && page == 1) {
                    var scroll = owner.getClass().getDeclaredField("settingsAppearanceScroll");
                    scroll.setAccessible(true);
                    var hit = (Rectangle) field(field(owner, "shuffleAngleSlider"), "hit");
                    var button = prompt.positiveButton();
                    for (int offset = 0; offset < 1000 && !hit.overlaps(button); offset += 20) {
                        scroll.setFloat(owner, offset);
                        render.run();
                    }
                    assertTrue(hit.overlaps(button), label);
                    discard.set((Math.max(hit.x, button.x) + Math.min(hit.x + hit.width, button.x + button.width)) / 2f,
                            (Math.max(hit.y, button.y) + Math.min(hit.y + hit.height, button.y + button.height)) / 2f);
                    click.at(discard);
                    assertNotEquals(baseline.get(GdxShuffleAngle.KEY), props.getProperty(GdxShuffleAngle.KEY), label);
                }
                var edited = GdxSettingsContract.snapshot(props);
                if (onlyGameDraftChanged) assertEquals(baseline, edited, label);
                else assertNotEquals(baseline, edited, label);
                assertEquals(true, invoke(owner, "settingsHavePendingChanges", new Class<?>[0]), label);
                render.run();
                click.at(frame.cancelButton().getCenter(new Vector2()));
                render.run();
                assertDiscardPrompt(owner, true, label);
                if (owner instanceof GdxFrontendScreen screen) {
                    var p = screenPoint(screen, discard.x, discard.y);
                    screen.touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.MIDDLE);
                    screen.touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.MIDDLE);
                    assertDiscardPrompt(owner, true, label);
                }
                click.at(frame.restoreButton().getCenter(new Vector2()));
                render.run();
                assertDiscardPrompt(owner, true, label);
                assertEquals(edited, GdxSettingsContract.snapshot(props), label);
                click.at(prompt.negativeButton().getCenter(new Vector2()));
                assertTrue(session.open(), label);
                assertDiscardPrompt(owner, false, label);
                if (owner instanceof GdxFrontendScreen screen) {
                    click(screen, prompt.positiveButton().getCenter(new Vector2()));
                    assertTrue(session.open(), label);
                }
                assertEquals(edited, GdxSettingsContract.snapshot(props), label);
                render.run();
                click.at(frame.cancelButton().getCenter(new Vector2()));
                render.run();
                click.at(discard);
                render.run();
                assertFalse(session.open(), "Discard did not close " + label);
                assertEquals(baseline, GdxSettingsContract.snapshot(props), label);
                if (owner instanceof GdxFrontendScreen screen) {
                    assertEquals(context == GdxSettingsSession.Context.MENU ? "MENU" : "LOBBY",
                            field(screen, "surface").toString(), label);
                    invoke(screen, "openSettings", new Class<?>[]{GdxSettingsContract.Section.class}, section);
                    if (openingDraft != null) {
                        assertEquals(openingDraft, ((NewGameTableDraft) field(screen, "settingsTable")).snapshot(), label);
                    }
                } else {
                    assertEquals(CoronaPokerGdxTable.UI_NONE, field(owner, "uiLayer"), label);
                    invoke(owner, "openSettings", new Class<?>[0]);
                }
                assertFalse(session.propertiesChanged(props), label);
                assertEquals(false, invoke(owner, "settingsHavePendingChanges", new Class<?>[0]), label);
                if (GdxSettingsContract.hasRestoreDefaults(section)) {
                    editForDiscard(props, section, page);
                    render.run();
                    assertEquals(false, invoke(owner, "currentSettingsSubsectionAtDefaults", new Class<?>[0]), label);
                    click.at(frame.restoreButton().getCenter(new Vector2()));
                    assertEquals(true, invoke(owner, "currentSettingsSubsectionAtDefaults", new Class<?>[0]), label);
                    // Restore Defaults itself is also a staged edit.
                    boolean pending = (boolean) invoke(owner, "settingsHavePendingChanges", new Class<?>[0]);
                    render.run();
                    click.at(frame.cancelButton().getCenter(new Vector2()));
                    if (pending) {
                        render.run();
                        assertDiscardPrompt(owner, true, label);
                        click.at(prompt.positiveButton().getCenter(new Vector2()));
                    }
                    render.run();
                    assertFalse(session.open(), label);
                    assertEquals(baseline, GdxSettingsContract.snapshot(props), label);
                    invoke(owner, "openSettings", new Class<?>[0]);
                }
            }
        }
        verifySettingsKeyboardAndSave(owner, context, props, render, click);
    }

    private static void verifyScrollableSettingsRows(Object owner, Runnable render) throws Exception {
        var input = owner instanceof CoronaPokerGdxTable table ? table.inputProcessor() : (GdxFrontendScreen) owner;
        GdxSettingsLayout.PixelRows rows = currentRows(owner);
        assertNotNull(rows);
        var visited = new java.util.BitSet(rows.totalRows());
        for (;;) {
            visited.set(rows.firstIndex(), rows.lastExclusive());
            if (rows.offset() >= rows.maximum()) break;
            float previous = rows.offset();
            input.scrolled(0f, Math.max(1f, rows.viewportHeight() / (2f * GdxSettingsLayout.PIXEL_SCROLL_WHEEL_STEP)));
            render.run();
            rows = currentRows(owner);
            assertTrue(rows.offset() > previous, "Wheel did not advance");
        }
        assertEquals(rows.totalRows(), visited.cardinality(), "Every settings row must be reachable");
        input.scrolled(0f, -1f - rows.maximum() / GdxSettingsLayout.PIXEL_SCROLL_WHEEL_STEP);
        render.run();
        assertEquals(0f, currentRows(owner).offset());
    }

    private static GdxSettingsLayout.PixelRows currentRows(Object owner) throws Exception {
        if (owner instanceof CoronaPokerGdxTable) {
            Object scrollbar = invoke(owner, "settingsRowScroll", new Class<?>[0]);
            return (GdxSettingsLayout.PixelRows) invoke(scrollbar, "rows", new Class<?>[0]);
        }
        return (GdxSettingsLayout.PixelRows) invoke(owner, "currentSettingsPixelRows", new Class<?>[0]);
    }

    private static void verifySettingsKeyboardAndSave(Object owner, GdxSettingsSession.Context context,
            Properties props, Runnable render, SettingsClick click) throws Exception {
        var session = (GdxSettingsSession) field(owner, "settingsSession");
        var viewport = (Viewport) field(owner, "viewport");
        var duration = GdxAppearanceOptions.SHUFFLE_DURATION;
        var beforeKeys = GdxSettingsContract.snapshot(props);
        GdxAppearanceOptions.cycle(duration, props);
        render.run();
        settingsKey(owner, Input.Keys.ESCAPE);
        render.run();
        assertDiscardPrompt(owner, true, context.toString());
        settingsKey(owner, Input.Keys.ESCAPE);
        assertDiscardPrompt(owner, false, context.toString());
        assertTrue(session.open());
        assertTrue(session.propertiesChanged(props));
        render.run();
        settingsKey(owner, Input.Keys.ESCAPE);
        render.run();
        settingsKey(owner, Input.Keys.ENTER);
        render.run();
        assertFalse(session.open());
        assertEquals(beforeKeys, GdxSettingsContract.snapshot(props));
        invoke(owner, "openSettings", new Class<?>[0]);
        render.run();
        var frame = GdxSettingsLayout.frame(viewport.getWorldWidth(), viewport.getWorldHeight(),
                session.sections().size(), ((List<?>) invoke(owner, "settingsSubpageLabels", new Class<?>[0])).size());
        GdxAppearanceOptions.cycle(duration, props);
        String selected = props.getProperty(duration.key());
        render.run();
        click.at(frame.saveButton().getCenter(new Vector2()));
        render.run();
        assertFalse(session.open());
        assertDiscardPrompt(owner, false, context.toString());
        invoke(owner, "openSettings", new Class<?>[0]);
        assertEquals(selected, props.getProperty(duration.key()));
        assertFalse(session.propertiesChanged(props));
        render.run();
        click.at(frame.cancelButton().getCenter(new Vector2()));
        render.run();
        assertFalse(session.open());
        assertDiscardPrompt(owner, false, context.toString());
        if (owner instanceof GdxFrontendScreen screen) {
            invoke(screen, "openSettings", new Class<?>[]{GdxSettingsContract.Section.class},
                    GdxSettingsContract.Section.APPEARANCE);
        } else {
            invoke(owner, "openSettings", new Class<?>[0]);
        }
    }

    private static void editForDiscard(Properties props, GdxSettingsContract.Section section, int page) {
        if (section == GdxSettingsContract.Section.APPEARANCE && page == 1) {
            String current = props.getProperty("duracion_barajado", "medium");
            props.setProperty("duracion_barajado", "short".equals(current) ? "full" : "short");
            return;
        }
        switch (section) {
            case APPEARANCE -> {
                if (page == 0) props.setProperty("show_time", "true");
                else props.setProperty("auto_fullscreen", "false");
            }
            case AUDIO -> {
                if (page == 0) props.setProperty("master_volume", "0.25");
                else if (page == 4) props.setProperty("audio_block_voice_messages", "true");
                else {
                    var option = GdxSettingsContract.AUDIO_PAGES.get(page).options().get(0);
                    props.setProperty(option.key(), Boolean.toString(!option.fallback()));
                }
            }
            case SHORTCUTS -> props.setProperty("confirmar_todo", "true");
            case GAME -> props.setProperty("show_time", "true");
            case DEBUG -> throw new AssertionError(section);
        }
    }

    private static void assertDiscardPrompt(Object owner, boolean expected, String label) throws Exception {
        Object prompt = field(owner, "settingsDiscardConfirmation");
        assertEquals(expected, owner instanceof GdxFrontendScreen ? Boolean.TRUE.equals(prompt) : prompt != null, label);
    }

    private static void settingsKey(Object owner, int key) throws Exception {
        if (owner instanceof GdxFrontendScreen screen) {
            assertTrue(screen.keyDown(key));
            screen.keyUp(key);
        } else {
            tableGesture((CoronaPokerGdxTable) owner, new Vector2(), key);
        }
    }

    private static void tableGesture(CoronaPokerGdxTable table, Vector2 world, int key) throws Exception {
        var viewport = (Viewport) field(table, "viewport");
        Vector2 point = viewport.project(new Vector2(world));
        int x = Math.round(point.x), y = Gdx.graphics.getHeight() - 1 - Math.round(point.y);
        Input hardware = Gdx.input;
        boolean[] down = {true};
        Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> x;
                    case "getY" -> y;
                    case "isButtonPressed", "isButtonJustPressed" ->
                            key == Input.Keys.UNKNOWN && down[0] && (int) args[0] == Input.Buttons.LEFT;
                    case "isKeyPressed", "isKeyJustPressed" -> key != Input.Keys.UNKNOWN && down[0] && (int) args[0] == key;
                    default -> method.invoke(hardware, args);
                });
        try {
            var input = table.inputProcessor();
            if (key == Input.Keys.UNKNOWN) input.touchDown(x, y, 0, Input.Buttons.LEFT);
            else input.keyDown(key);
            table.render();
            down[0] = false;
            if (key == Input.Keys.UNKNOWN) input.touchUp(x, y, 0, Input.Buttons.LEFT);
            else input.keyUp(key);
            table.render();
        } finally {
            Gdx.input = hardware;
        }
    }

    private static void click(GdxFrontendScreen screen, Vector2 world) throws Exception {
        Vector2 point = screenPoint(screen, world.x, world.y);
        int x = Math.round(point.x), y = Math.round(point.y);
        screen.touchDown(x, y, 0, Input.Buttons.LEFT);
        screen.touchUp(x, y, 0, Input.Buttons.LEFT);
    }

    private static void assertPressHits(GdxFrontendScreen screen, String label)
            throws Exception {
        List<?> hits = (List<?>) field(screen, "hits");
        assertFalse(hits.isEmpty(), label);
        for (Object hit : hits) {
            assertNotEquals("RELEASE", field(hit, "activation").toString(), label);
        }
    }

    private static void chooseFelt(GdxFrontendScreen screen,
            GdxGamePresentationSettings presentation, String felt) throws Exception {
        var point = pressOption(screen, "FELT", GdxGamePresentationSettings.FELTS.indexOf(felt));
        assertEquals(felt, presentation.felt());
        assertEquals("NONE", field(screen, "dropdown").toString());
        Object texture = field(screen, "feltTexture");
        screen.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        assertSame(texture, field(screen, "feltTexture"));
    }

    private static void verifyFrameRateControl(Object owner, com.badlogic.gdx.InputProcessor input,
            Runnable render, Properties savedSettings) throws Exception {
        var session = (GdxSettingsSession) field(owner, "settingsSession");
        var settings = session.frameRateDraft();
        var presentation = (GdxGamePresentationSettings) field(owner, "presentationSettings");
        int openingLimit = presentation.requestedFrameRateLimit();
        var control = (GdxFrameRateControl) field(owner, "fpsControl");
        var slider = field(control, "slider");
        var viewport = (Viewport) field(owner, "viewport");
        var box = (Rectangle) field(control, "boxHit");
        var track = (Rectangle) field(slider, "track");
        var scroll = owner.getClass().getDeclaredField("settingsAppearanceScroll");
        scroll.setAccessible(true);
        var saved = (Properties) settings.clone();
        try {
            GdxFrameRateLimit.selectMode(settings, 1);
            for (int offset = 0; offset <= 500; offset += 36) {
                scroll.setFloat(owner, offset);
                render.run();
                if (box.height == 40f) break;
            }
            assertEquals(40f, box.height);
            assertEquals(12f, box.x - track.x - track.width, .01f,
                    "Only the track and the exact-value field occupy the control");
            assertEquals(box.x + box.width / 2f, control.textCenterX());
            var boxPoint = viewport.project(box.getCenter(new Vector2()));
            int boxX = Math.round(boxPoint.x), boxY = HEIGHT - 1 - Math.round(boxPoint.y);
            assertTrue(input.touchDown(boxX, boxY, 0, Input.Buttons.LEFT));
            input.touchUp(boxX, boxY, 0, Input.Buttons.LEFT);
            for (char digit : "173".toCharArray()) assertTrue(input.keyTyped(digit));
            assertEquals(173, GdxFrameRateLimit.CUSTOM.value(settings));
            assertEquals("173", control.text(settings));
            assertEquals(openingLimit, presentation.requestedFrameRateLimit());

            var left = viewport.project(new Vector2(track.x - 4f, track.y + 4f));
            var right = viewport.project(new Vector2(track.x + track.width + 4f, track.y + 4f));
            int leftX = Math.round(left.x), rightX = Math.round(right.x);
            int y = HEIGHT - 1 - Math.round(left.y);
            assertTrue(input.touchDown(leftX, y, 0, Input.Buttons.LEFT));
            assertEquals("30", control.text(settings));
            assertTrue(input.touchDragged(rightX, y, 0));
            assertEquals("400", control.text(settings));
            assertTrue(input.touchUp(rightX, y, 0, Input.Buttons.LEFT));

            GdxFrameRateLimit.selectMode(settings, 0);
            render.run();
            assertFalse(control.editable(settings));
            assertNull(field(slider, "visible"));
            assertEquals("400", control.text(settings));
            GdxFrameRateLimit.selectMode(settings, 1);
            render.run();
            assertTrue(control.editable(settings));
            assertEquals("400", control.text(settings));
            assertEquals(openingLimit, presentation.requestedFrameRateLimit());
            assertTrue(session.propertiesChanged(savedSettings));
        } finally {
            restoreKey(settings, saved, GdxFrameRateLimit.KEY);
            restoreKey(settings, saved, GdxFrameRateLimit.MODE_KEY);
            scroll.setFloat(owner, 0f);
        }
    }

    private static void verifyZoomTracks(Object owner, com.badlogic.gdx.InputProcessor input, Runnable render,
            java.util.Properties properties) throws Exception {
        var slider = field(owner, "cardZoomSlider");
        var viewport = (Viewport) field(owner, "viewport");
        var scroll = owner.getClass().getDeclaredField("settingsAppearanceScroll");
        scroll.setAccessible(true);
        var saved = (java.util.Properties) properties.clone();
        try {
            for (var zoom : GdxCardZoom.values()) {
                boolean perspective = zoom == GdxCardZoom.SHUFFLE_3D;
                properties.setProperty(GdxSettingsContract.TABLE_VIEW_KEY, perspective ? "3d" : "2d");
                invoke(owner, "selectSettingsSubpage", new Class<?>[]{int.class}, 1);
                var other = GdxCardZoom.shuffle(!perspective);
                int otherValue = other.percent(properties);
                for (int offset = 0; offset < 1100; offset += 48) {
                    scroll.setFloat(owner, offset);
                    render.run();
                    if (field(slider, "visible") == zoom) {
                        var track = (Rectangle) field(slider, "track");
                        var hit = (Rectangle) field(slider, "hit");
                        if (hit.contains(track.x, track.y + track.height / 2f)) break;
                    }
                }
                assertSame(zoom, field(slider, "visible"));
                var track = (Rectangle) field(slider, "track");
                var start = viewport.project(new Vector2(track.x, track.y + track.height / 2));
                var end = viewport.project(new Vector2(track.x + track.width, track.y + track.height / 2));
                int y = HEIGHT - 1 - Math.round(start.y);
                assertTrue(input.touchDown(Math.round(start.x), y, 0, Input.Buttons.LEFT));
                assertEquals(GdxCardZoom.MIN, zoom.percent(properties));
                int middle = Math.round(start.x + (end.x - start.x) * .38f);
                assertTrue(input.touchDragged(middle, y, 0));
                assertEquals(145, zoom.percent(properties),
                        "The full-width track preserves five-point zoom steps");
                assertTrue(input.touchDragged(Math.round(end.x), y, 0));
                assertTrue(input.touchUp(Math.round(end.x), y, 0, Input.Buttons.LEFT));
                assertEquals(zoom.max, zoom.percent(properties));
                assertEquals(otherValue, other.percent(properties));
            }
        } finally {
            for (var zoom : GdxCardZoom.values()) restoreKey(properties, saved, zoom.key);
            restoreKey(properties, saved, GdxSettingsContract.TABLE_VIEW_KEY);
            scroll.setFloat(owner, 0f);
        }
    }

    private static void verifyPercentageTracks(Object owner, com.badlogic.gdx.InputProcessor input,
            Runnable render, Properties properties) throws Exception {
        var session = (GdxSettingsSession) field(owner, "settingsSession");
        var slider = field(owner, "percentageSlider");
        var volume = (GdxMasterVolumeSetting) field(owner, "masterVolumeSetting");
        var audio = (GdxAudioControl) field(owner, "audioControl");
        boolean soundEnabled = audio.enabled();
        var saved = (Properties) properties.clone();
        Object overlay = field(owner, "volumeOverlayUntil");
        try {
            session.selectTab(session.sections().indexOf(GdxSettingsContract.Section.AUDIO));
            invoke(owner, "selectSettingsSubpage", new Class<?>[]{int.class}, 0);
            audio.restoreSessionEnabled(false);
            render.run();
            assertNull(field(slider, "visible"));
            audio.restoreSessionEnabled(true);
            render.run();
            assertSame(volume, field(slider, "visible"));
            dragPercentageTrack(owner, input, slider, () -> volume.value(properties), 0, 37, 100);
            assertEquals(1, volume.step());
            assertEquals(overlay, field(owner, "volumeOverlayUntil"),
                    "Settings volume changes must not open the global volume overlay");
            if (owner instanceof CoronaPokerGdxTable) {
                assertEquals(1f, field(owner, "effectsVolume"));
            }

            session.selectTab(session.sections().indexOf(GdxSettingsContract.Section.APPEARANCE));
            invoke(owner, "selectSettingsSubpage", new Class<?>[]{int.class}, 0);
            render.run();
            var light = GdxAppearanceOptions.LIGHT_OFF;
            assertSame(light, field(slider, "visible"));
            dragPercentageTrack(owner, input, slider, () -> light.value(properties), 10, 40, 90);
        } finally {
            audio.restoreSessionEnabled(soundEnabled);
            invoke(owner, owner instanceof CoronaPokerGdxTable
                    ? "applyMasterVolume" : "applyFrontendMasterVolume", new Class<?>[]{float.class},
                    GdxSettingsContract.masterVolume(saved));
            restoreKey(properties, saved, "master_volume");
            restoreKey(properties, saved, "nivel_luz");
        }
    }

    private static void dragPercentageTrack(Object owner, com.badlogic.gdx.InputProcessor input,
            Object slider, java.util.function.IntSupplier value, int min, int middle, int max) throws Exception {
        var view = (Viewport) field(owner, "viewport");
        var track = (Rectangle) field(slider, "track");
        float center = track.y + track.height / 2f;
        Vector2 start = view.project(new Vector2(track.x, center));
        Vector2 end = view.project(new Vector2(track.x + track.width, center));
        int y = HEIGHT - 1 - Math.round(start.y);
        assertTrue(input.touchDown(Math.round(start.x), y, 0, Input.Buttons.LEFT));
        assertEquals(min, value.getAsInt());
        assertTrue(input.touchDragged(Math.round(start.x + (end.x - start.x) * .37f), y, 0));
        assertEquals(middle, value.getAsInt());
        assertTrue(input.touchDragged(Math.round(end.x), y, 0));
        assertTrue(input.touchUp(Math.round(end.x), y, 0, Input.Buttons.LEFT));
        assertEquals(max, value.getAsInt());
    }

    private static void restoreKey(java.util.Properties properties, java.util.Properties saved, String key) {
        if (saved.containsKey(key)) properties.setProperty(key, saved.getProperty(key));
        else properties.remove(key);
    }

    private static void verifyAngleTrack(Object owner, com.badlogic.gdx.InputProcessor input, Runnable render,
            java.util.Properties properties) throws Exception {
        invoke(owner, "selectSettingsSubpage", new Class<?>[]{int.class}, 1);
        var slider = field(owner, "shuffleAngleSlider");
        var track = (Rectangle) field(slider, "track");
        var hit = (Rectangle) field(slider, "hit");
        var scroll = owner.getClass().getDeclaredField("settingsAppearanceScroll");
        scroll.setAccessible(true);
        for (int offset = 0; offset < 1000; offset += 64) {
            scroll.setFloat(owner, offset);
            render.run();
            if (hit.height > 32f) break;
        }
        assertTrue(hit.height > 32f, "The angle track must be visible and enabled");
        var viewport = (Viewport) field(owner, "viewport");
        Vector2 start = viewport.project(new Vector2(track.x, hit.y + hit.height * .5f));
        Vector2 end = viewport.project(new Vector2(track.x + track.width + 10f, hit.y + hit.height * .5f));
        int touchY = HEIGHT - 1 - Math.round(start.y);
        assertTrue(input.touchDown((int) start.x, touchY, 0, Input.Buttons.LEFT));
        assertEquals(-180, GdxShuffleAngle.value(properties));
        assertTrue(input.touchCancelled((int) start.x, touchY, 0, Input.Buttons.LEFT));
        assertTrue(input.touchDown((int) start.x, touchY, 0, Input.Buttons.LEFT));
        assertTrue(input.touchDragged(Math.round(end.x), touchY, 0));
        assertTrue(input.touchUp(Math.round(end.x), touchY, 0, Input.Buttons.LEFT));
        assertEquals(180, GdxShuffleAngle.value(properties));
        assertTrue(((GdxSettingsSession) field(owner, "settingsSession")).propertiesChanged(properties));
        GdxSettingsContract.restoreAppearancePageDefaults(properties, 1, true);
        assertEquals(GdxShuffleAngle.DEFAULT, GdxShuffleAngle.value(properties));
    }

    private static void verifyDeckPreview(GdxFrontendScreen screen,
            GdxGamePresentationSettings presentation) throws Exception {
        var preview = (GdxImagePreview) field(screen, "imagePreview");
        int deck = presentation.availableDecks().indexOf("interstate60");
        for (int selection = 1; selection <= 5; selection++) {
            var point = pressOption(screen, "DECK", deck);
            assertEquals("interstate60", presentation.deck());
            assertEquals(selection == 5, preview.isOpen());
            screen.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        }
        assertEnlargedFrame(preview);
        screen.render();
        assertEquals(1, ((List<?>) field(screen, "hits")).size());
        assertTrue(screen.keyDown(Input.Keys.ESCAPE));
        assertFalse(preview.isOpen());
        assertEquals("SETTINGS", field(screen, "surface").toString());
    }

    private static void verifyMixedDeckProportions(CoronaPokerGdxTable table,
            GdxGamePresentationSettings settings) throws Exception {
        String faceDeck = settings.deck(), cardBack = settings.cardBack();
        try {
            settings.selectDeck("goliat", false);
            settings.selectCardBack("coronapoker", false);
            invoke(table, "synchronizeSelectedDeck", new Class<?>[0]);
            table.render();
            Texture back = (Texture) invoke(table, "activeCardBack", new Class<?>[0]);
            float faceRatio = 1242f / 923f;
            assertNotEquals(faceRatio, back.getHeight() / (float) back.getWidth(), .000001f);
            var seats = (Object[]) field(table, "seats");
            for (int index : new int[]{0, 1}) {
                Object card = invoke(table, "liveHolePlacement", new Class<?>[]{seats[index].getClass(), int.class},
                        seats[index], 0);
                assertEquals(faceRatio, (float) field(card, "height") / (float) field(card, "width"), .000001f);
            }
            assertEquals(faceRatio, (float) invoke(table, "deckCardHeight", new Class<?>[]{float.class}, 1f), .000001f);
        } finally {
            settings.selectDeck(faceDeck, false);
            settings.selectCardBack(cardBack, false);
            invoke(table, "synchronizeSelectedDeck", new Class<?>[0]);
        }
    }

    private static void verifyLiveTable(PreferencesService preferences,
            GdxGamePresentationSettings presentation, LobbySession lobby) throws Exception {
        var player = new TableSnapshot.PlayerSnapshot("human", 20d, 0d, 20d,
                true, false, false, false, -2, -2, 0, 0L, false,
                TableSnapshot.Position.DEALER, "", "", List.of());
        var snapshot = new TableSnapshot(1L, "human", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(player), List.of());
        var state = new GdxTableViewState(snapshot);
        state.apply(new TableVisualEvent.GameConfigurationStatus(1L,
                GameConfigCodecV1.fromSettings(new NewGameTableDraft().snapshot(), false, "settings-test")));
        var table = new CoronaPokerGdxTable(60, state,
                command -> { throw new AssertionError("Discard submitted " + command); },
                () -> {}, new GdxGameLogSink(), preferences, lobby, presentation);
        try {
            table.create();
            assertEquals(presentation.shuffleAnimation(), field(table, "riffle") != null);
            table.resize(WIDTH, HEIGHT);
            var ready = new CompletableFuture<Void>();
            table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                    TableVisualEvent.PreparationStatus.Phase.READY), ready);
            ready.join();
            verifyMixedDeckProportions(table, presentation);
            invoke(table, "openSettings", new Class<?>[0]);
            var slide = (GdxDialogSlide) field(table, "settingsSlide");
            table.render();
            assertSlideDirection(slide, 0, 1);
            slide.advance(1f);
            table.render();
            invoke(table, "requestCancelTableSettings", new Class<?>[]{Runnable.class}, (Object) null);
            assertTrue(slide.closing());
            slide.advance(0.10f);
            table.render();
            assertSlideDirection(slide, 0, 1);
            assertEquals(CoronaPokerGdxTable.UI_SETTINGS, field(table, "uiLayer"));
            slide.advance(1f);
            assertEquals(CoronaPokerGdxTable.UI_NONE, field(table, "uiLayer"));
            invoke(table, "openSettings", new Class<?>[0]);
            Runnable renderSettings = settledSettingsRender(table, table::render);
            verifyDiscardFromEveryPage(table, GdxSettingsSession.Context.LIVE_TABLE, preferences.properties(),
                    renderSettings, point -> tableGesture(table, point, Input.Keys.UNKNOWN));
            renderSettings.run();
            // Exercise the remaining appearance previews once, on their original perspective table.
            if (lobby != null) return;
            var session = (GdxSettingsSession) field(table, "settingsSession");
            verifyPercentageTracks(table, table.inputProcessor(), table::render, preferences.properties());
            session.selectTab(session.sections().indexOf(GdxSettingsContract.Section.APPEARANCE));
            verifyZoomTracks(table, table.inputProcessor(), table::render, preferences.properties());
            verifyAngleTrack(table, table.inputProcessor(), table::render, preferences.properties());
            verifyShuffleAngleLock(table, preferences.properties());
            verifyDealerDeparture(table, preferences.properties());
            invoke(table, "selectSettingsSubpage", new Class<?>[]{int.class},
                    GdxSettingsContract.appearanceGraphicsPageIndex());
            verifyFrameRateControl(table, table.inputProcessor(), table::render, preferences.properties());
            invoke(table, "selectSettingsSubpage", new Class<?>[]{int.class}, 0);
            for (String felt : List.of("azul", "verde", "azul", "verde", "azul")) {
                invoke(table, "selectTableFeltOption", new Class<?>[]{int.class},
                        GdxGamePresentationSettings.FELTS.indexOf(felt));
            }
            assertTrue(presentation.secretFelt());
            assertEquals(true, field(table, "secretFeltTexture"));
            invoke(table, "selectTableFeltOption", new Class<?>[]{int.class},
                    GdxGamePresentationSettings.FELTS.indexOf("azul"));
            assertEquals(false, field(table, "secretFeltTexture"));

            var preview = (GdxImagePreview) field(table, "imagePreview");
            int deck = presentation.availableDecks().indexOf("interstate60");
            for (int selection = 1; selection <= 5; selection++) {
                invoke(table, "selectTableDeckOption", new Class<?>[]{int.class}, deck);
                assertEquals(selection == 5, preview.isOpen());
            }
            assertEnlargedFrame(preview);
            table.render();
            assertTrue((boolean) invoke(table, "hasNestedTableModal", new Class<?>[0]));
            assertEquals(CoronaPokerGdxTable.UI_SETTINGS, field(table, "uiLayer"));
        } finally {
            table.dispose();
        }
    }

    private static void verifyShuffleAngleLock(CoronaPokerGdxTable table, java.util.Properties properties) throws Exception {
        properties.setProperty(GdxSettingsContract.SHUFFLE_TO_DEALER_KEY, "false");
        var slider = field(table, "shuffleAngleSlider");
        var track = (Rectangle) field(slider, "track");
        var hit = (Rectangle) field(slider, "hit");
        var viewport = (Viewport) field(table, "viewport");
        var input = table.inputProcessor();
        Vector2 start = viewport.project(new Vector2(track.x + track.width * .7f, hit.y + hit.height * .5f));
        Vector2 end = viewport.project(new Vector2(track.x, hit.y + hit.height * .5f));
        int touchY = HEIGHT - 1 - Math.round(start.y);
        assertTrue(input.touchDown(Math.round(start.x), touchY, 0, Input.Buttons.LEFT));
        int initialAngle = GdxShuffleAngle.value(properties);
        assertTrue(initialAngle > 60 && initialAngle < 80);
        properties.setProperty(GdxCardZoom.SHUFFLE_2D.key, "115");
        properties.setProperty(GdxCardZoom.SHUFFLE_3D.key, "175");
        var view = GdxAppearanceOptions.SHUFFLE_VIEW;
        properties.setProperty(view.key(), "3d");
        var duration = GdxAppearanceOptions.SHUFFLE_DURATION;
        properties.setProperty(duration.key(), "medium");
        var started = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.Shuffle(3, "goliat", TableVisualEvent.Shuffle.Phase.START), started);
        assertTrue(started.isDone());
        var shuffle = field(table, "liveShuffle");
        assertEquals(initialAngle, field(shuffle, "angle"));
        assertEquals(true, field(shuffle, "tiltedOnFlatTable"));
        assertEquals(GdxShuffleGeometry.Motion.FULL_DECK, field(shuffle, "motion"));
        invoke(table, "selectTableAppearanceChoice",
                new Class<?>[]{GdxAppearanceOptions.Choice.class, int.class}, duration, 2);
        assertEquals("medium", properties.getProperty(duration.key()));
        invoke(table, "selectTableAppearanceChoice",
                new Class<?>[]{GdxAppearanceOptions.Choice.class, int.class}, view, 0);
        assertEquals("3d", properties.getProperty(view.key()));
        assertEquals(115, field(shuffle, "zoom2D"));
        assertEquals(175, field(shuffle, "zoom3D"));
        properties.setProperty(GdxCardZoom.SHUFFLE_3D.key, "250");
        assertEquals(175, invoke(table, "shuffleZoom", new Class<?>[]{boolean.class}, true));
        input.touchDragged(Math.round(end.x), touchY, 0);
        input.touchUp(Math.round(end.x), touchY, 0, Input.Buttons.LEFT);
        table.render();
        assertNull(field(field(table, "cardZoomSlider"), "visible"));
        assertEquals(0f, hit.width);
        assertEquals(initialAngle, GdxShuffleAngle.value(properties));
        invoke(table, "restoreTableAppearanceDefaults", new Class<?>[0]);
        assertEquals("medium", properties.getProperty(duration.key()));
        assertEquals(HEIGHT, Gdx.graphics.getHeight(), "Resetting animations must preserve the window size");
        assertEquals("3d", properties.getProperty(view.key()));
        assertEquals(initialAngle, GdxShuffleAngle.value(properties));
        // Cancelling uncommitted settings may restore the next hand's preference.
        properties.setProperty(GdxShuffleAngle.KEY, "15");
        assertEquals(initialAngle, invoke(table, "shuffleAngle", new Class<?>[0]));
        properties.setProperty(GdxShuffleAngle.KEY, Integer.toString(initialAngle));
        // Advance the animation clock across the first pass, still awaiting FINISH.
        float cycle = (float) field(shuffle, "cycleSeconds");
        var epoch = shuffle.getClass().getDeclaredField("startedAtNanos");
        epoch.setAccessible(true);
        epoch.setLong(shuffle, System.nanoTime() - (long) ((cycle + .25) * 1_000_000_000L));
        table.render();
        table.render();
        assertTrue((boolean) field(shuffle, "firstEndPresented"));
        assertSame(shuffle, field(table, "liveShuffle"));
        assertEquals(0f, hit.width);
        input.touchDown(Math.round(end.x), touchY, 0, Input.Buttons.LEFT);
        input.touchUp(Math.round(end.x), touchY, 0, Input.Buttons.LEFT);
        assertEquals(initialAngle, GdxShuffleAngle.value(properties));
        var finished = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.Shuffle(4, "goliat", TableVisualEvent.Shuffle.Phase.FINISH), finished);
        table.render();
        assertTrue(finished.isDone());
        assertNull(field(table, "liveShuffle"));
        assertTrue(hit.width > 0f);
        assertTrue(input.touchDown((int) end.x, touchY, 0, Input.Buttons.LEFT));
        input.touchUp((int) end.x, touchY, 0, Input.Buttons.LEFT);
        assertEquals(-180, GdxShuffleAngle.value(properties));
        GdxSettingsContract.restoreAppearancePageDefaults(properties, 1, true);
    }

    private static void verifyDealerDeparture(CoronaPokerGdxTable table, java.util.Properties properties) throws Exception {
        properties.remove(GdxSettingsContract.SHUFFLE_TO_DEALER_KEY);
        var started = new CompletableFuture<Void>();
        var finished = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.Shuffle(5, "goliat", TableVisualEvent.Shuffle.Phase.START), started);
        table.render();
        Object shuffle = field(table, "liveShuffle");
        assertTrue(started.isDone());
        assertEquals(true, field(shuffle, "flyToDealer"));
        table.acceptEvent(new TableVisualEvent.Shuffle(6, "goliat", TableVisualEvent.Shuffle.Phase.FINISH), finished);
        setLong(shuffle, "startedAtNanos", System.nanoTime() - (long) (((float) field(shuffle, "cycleSeconds") + 1f) * 1e9));
        table.render();
        assertEquals(true, field(shuffle, "firstEndPresented"));
        table.render();
        assertEquals(true, field(shuffle, "departing"));
        assertFalse(finished.isDone());
        // Even a late frame must present the opaque deck at the seat before the fade starts.
        setLong(shuffle, "departureStart", System.nanoTime() - 2_000_000_000L);
        table.render();
        assertEquals(true, field(shuffle, "landingPresented"));
        Object local = ((Object[]) field(table, "seats"))[0];
        assertEquals(field(local, "x"), field(table, "dealerSeatCenterX"));
        assertEquals(field(local, "y"), field(table, "dealerSeatCenterY"));
        assertFalse(finished.isDone());
        setLong(shuffle, "landingNanos", System.nanoTime() - 200_000_000L);
        table.render();
        assertEquals(true, field(shuffle, "departurePresented"));
        assertFalse(finished.isDone());
        table.render();
        assertTrue(finished.isDone());
        assertNull(field(table, "liveShuffle"));
    }

    private static void setLong(Object owner, String name, long value) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(owner, value);
    }

    private static void assertEnlargedFrame(GdxImagePreview preview) throws InterruptedException {
        Texture frame = null;
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (frame == null && System.nanoTime() < deadline) {
            frame = preview.frame();
            if (frame == null) Thread.sleep(5);
        }
        assertNotNull(frame, "The enlarged animation must reach the renderer");
        assertEquals(632, frame.getWidth());
        assertEquals(502, frame.getHeight());
    }

    private static Vector2 pressOption(GdxFrontendScreen screen, String dropdown, int row)
            throws Exception {
        assertTrue(row >= 0 && row < GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS);
        Class<?> dropdownType = field(screen, "dropdown").getClass();
        Object target = List.of(dropdownType.getEnumConstants()).stream()
                .filter(value -> value.toString().equals(dropdown)).findFirst().orElseThrow();
        invoke(screen, "openDropdown", new Class<?>[]{
                dropdownType, float.class, float.class, float.class, float.class},
                target, 950f, 600f, 400f, 72f);
        screen.render();
        Rectangle menu = (Rectangle) field(screen, "dropdownClip");
        var point = screenPoint(screen, menu.x + 40f, menu.y + menu.height
                - (row + .5f) * GdxSettingsLayout.POPUP_ROW_HEIGHT);
        assertTrue(screen.touchDown((int) point.x, (int) point.y, 0, Input.Buttons.LEFT));
        return point;
    }

    private static void verifyImageViewer(GdxFrontendScreen screen) throws Exception {
        invoke(screen, "openAboutDialog", new Class<?>[0]);
        for (boolean alternate : new boolean[]{false, true}) {
            for (int count = 0; count < 5; count++) {
                invoke(screen, "activateAboutEasterEgg",
                        new Class<?>[]{boolean.class}, alternate);
            }
            Texture visible = (Texture) field(screen, "aboutEasterEggTexture");
            assertNotNull(visible);
            screen.render();
            assertTrue((boolean) invoke(screen, "hasNestedFrontendModal", new Class<?>[0]));
            assertEquals(1, ((List<?>) field(screen, "hits")).size());
            assertTrue(screen.keyDown(Input.Keys.ESCAPE));
            assertNull(field(screen, "aboutEasterEggTexture"));
            assertEquals(true, field(screen, "aboutOpen"));
        }
        invoke(screen, "closeAboutDialog", new Class<?>[0]);
        settledSettingsRender(screen, screen::render).run();
    }

    private static Vector2 screenPoint(GdxFrontendScreen screen, float x, float y)
            throws Exception {
        var viewport = (Viewport) field(screen, "viewport");
        Vector2 point = viewport.project(new Vector2(x, y));
        return point.set(point.x, Gdx.graphics.getHeight() - 1f - point.y);
    }

    private static LobbySession lobby() {
        return lobby(LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "");
    }

    private static LobbySession lobby(LobbySnapshot.Phase phase, String detail) {
        return new LobbySession(new LobbySnapshot("human", "human", "127.0.0.1:2345",
                true, phase, detail,
                List.of(new LobbyParticipant("human", null, true, true,
                        false, true, false, true, 0, 0)),
                List.of(), null, false), command -> CompletableFuture.completedFuture(null));
    }

    private static Object field(Object receiver, String name) throws Exception {
        var field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static Object invoke(Object receiver, String name, Class<?>[] parameters,
            Object... arguments) throws Exception {
        var method = receiver.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(receiver, arguments);
    }
}
