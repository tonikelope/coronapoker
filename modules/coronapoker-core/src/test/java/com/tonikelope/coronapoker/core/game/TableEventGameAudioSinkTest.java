package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

final class TableEventGameAudioSinkTest {

    @Test
    void publishesCanonicalAndFunnyModCuesButNotRendererOwnedImpacts() {
        TableEventBridge bridge = new TableEventBridge();
        RecordingRenderer renderer = new RecordingRenderer();
        bridge.attach(renderer, emptySnapshot()).toCompletableFuture().join();
        TableEventGameAudioSink audio = new TableEventGameAudioSink(bridge,
                Set.of("misc/deal.wav"));

        audio.playWavResource("misc/deal.wav");
        audio.playRandomWavResource(Map.of("joke/es/fold/",
                new String[]{"mod.wav"}));
        assertTrue(audio.playWavResourceAndWait("misc/warning.wav"));

        assertEquals(2, renderer.events.size());
        TableVisualEvent.AudioCue funny =
                (TableVisualEvent.AudioCue) renderer.events.get(0);
        assertEquals("joke/es/fold/mod.wav", funny.resource());
        TableVisualEvent.AudioCue warning =
                (TableVisualEvent.AudioCue) renderer.events.get(1);
        assertEquals("misc/warning.wav", warning.resource());
        assertTrue(warning.waitForCompletion());
    }

    @Test
    void preservesTheRecoveryMusicSwapAsOrderedLoopEvents() {
        TableEventBridge bridge = new TableEventBridge();
        RecordingRenderer renderer = new RecordingRenderer();
        bridge.attach(renderer, emptySnapshot()).toCompletableFuture().join();
        TableEventGameAudioSink audio = new TableEventGameAudioSink(bridge,
                Set.of());

        audio.stopLoopMp3("misc/background_music.mp3");
        audio.playLoopMp3Resource("misc/recovering.mp3");
        audio.stopLoopMp3("misc/recovering.mp3");
        audio.playLoopMp3Resource("misc/background_music.mp3");

        assertEquals(List.of(
                TableVisualEvent.AudioCue.Operation.STOP_LOOP,
                TableVisualEvent.AudioCue.Operation.PLAY_LOOP,
                TableVisualEvent.AudioCue.Operation.STOP_LOOP,
                TableVisualEvent.AudioCue.Operation.PLAY_LOOP),
                renderer.events.stream()
                        .map(TableVisualEvent.AudioCue.class::cast)
                        .map(TableVisualEvent.AudioCue::operation)
                        .toList());
        assertEquals(List.of("misc/background_music.mp3",
                "misc/recovering.mp3", "misc/recovering.mp3",
                "misc/background_music.mp3"),
                renderer.events.stream()
                        .map(TableVisualEvent.AudioCue.class::cast)
                        .map(TableVisualEvent.AudioCue::resource)
                        .toList());
    }

    private static TableSnapshot emptySnapshot() {
        return new TableSnapshot(0L, "local", TableSnapshot.Street.WAITING,
                0d, "", false, List.of(), List.of());
    }

    private static final class RecordingRenderer implements TableRenderer {

        final List<TableVisualEvent> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }
}
