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
