package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tonikelope.coronapoker.table.TablePresentation;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

final class TablePresentationContractTest {

    @Test
    void presentationAssignsStrictlyMonotonicSequencesAndPreservesBarrier() {
        RecordingRenderer renderer = new RecordingRenderer();
        TablePresentation presentation = new TablePresentation(renderer);

        CompletionStage<Void> returned = presentation.publish(
                sequence -> new TableVisualEvent.Shuffle(sequence, "Goliat",
                        TableVisualEvent.Shuffle.Phase.START));
        presentation.publish(sequence -> new TableVisualEvent.PlayerAction(
                sequence, "CoronaBot$1", "PASA", 0d));

        assertSame(renderer.firstBarrier, returned);
        assertEquals(List.of(1L, 2L), renderer.sequences);
        assertEquals(2L, presentation.lastSequence());
    }

    @Test
    void factoryCannotForgeAnOutOfOrderSequence() {
        TablePresentation presentation = new TablePresentation(new RecordingRenderer());
        assertThrows(IllegalArgumentException.class,
                () -> presentation.publish(sequence -> new TableVisualEvent.CloseTable(sequence + 1)));
    }

    private static final class RecordingRenderer implements TableRenderer {

        private final List<Long> sequences = new ArrayList<>();
        private final CompletableFuture<Void> firstBarrier = new CompletableFuture<>();

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            sequences.add(event.sequence());
            return sequences.size() == 1
                    ? firstBarrier : CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }
}
