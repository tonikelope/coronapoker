/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Render-thread ownership of the microphone and its asynchronous callbacks. */
final class GdxVoiceCapture {

    private enum Phase { IDLE, OPENING, RECORDING, PROCESSING, CANCELLING }

    record Result(byte[] wav, GdxVoiceRecorder.Outcome outcome) {
        String statusKey() {
            return switch (outcome) {
                case ABORTED -> "gdx.lobby.voice_cancelled";
                case NO_LINE -> "gdx.lobby.voice_unavailable";
                case SILENT -> "gdx.table.chat.voice_silent";
                case LOST -> "gdx.table.chat.voice_lost";
                default -> "gdx.lobby.voice_discarded";
            };
        }
    }

    private final Consumer<Runnable> post;
    private GdxVoiceRecorder recorder;
    private Consumer<Result> completion;
    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;

    GdxVoiceCapture() {
        this(task -> Gdx.app.postRunnable(task));
    }

    GdxVoiceCapture(Consumer<Runnable> post) {
        this.post = post;
    }

    void start(GdxVoiceRecorder next, Consumer<Result> onComplete) {
        recorder = next;
        completion = onComplete;
        phase = Phase.OPENING;
        phaseStartedAt = System.nanoTime();
        CompletableFuture.supplyAsync(() -> next.start(
                () -> post.accept(() -> {
                    if (recorder != next || phase != Phase.OPENING) return;
                    phase = Phase.RECORDING;
                    phaseStartedAt = System.nanoTime();
                }),
                () -> post.accept(() -> {
                    if (recorder == next && held()) finish(false);
                }))).whenComplete((outcome, error) -> post.accept(() -> {
                    if (recorder == next && phase == Phase.OPENING
                            && (error != null || outcome != GdxVoiceRecorder.Outcome.RECORDING)) {
                        next.requestAbort();
                        complete(new Result(null, GdxVoiceRecorder.Outcome.NO_LINE));
                    }
                }));
    }

    void finish(boolean discard) {
        if (!held() && !(discard && phase == Phase.PROCESSING)) return;
        GdxVoiceRecorder active = recorder;
        Phase finishing = discard ? Phase.CANCELLING : Phase.PROCESSING;
        phase = finishing;
        CompletableFuture.supplyAsync(() -> {
            if (!discard) return active.stopAndEncode();
            active.abort();
            return null;
        }).orTimeout(3, TimeUnit.SECONDS).whenComplete((wav, error) ->
                post.accept(() -> {
                    if (recorder != active || phase != finishing) return;
                    if (error instanceof TimeoutException) active.requestAbort();
                    var outcome = discard ? GdxVoiceRecorder.Outcome.ABORTED
                            : error == null ? active.outcome() : GdxVoiceRecorder.Outcome.LOST;
                    complete(new Result(wav, outcome));
                }));
    }

    private void complete(Result result) {
        Consumer<Result> callback = completion;
        recorder = null;
        completion = null;
        phase = Phase.IDLE;
        callback.accept(result);
    }

    void reset() {
        GdxVoiceRecorder active = recorder;
        recorder = null;
        completion = null;
        phase = Phase.IDLE;
        if (active != null) active.requestAbort();
    }

    boolean busy() {
        return phase != Phase.IDLE;
    }

    boolean held() {
        return phase == Phase.OPENING || phase == Phase.RECORDING;
    }

    boolean recording() {
        return phase == Phase.RECORDING;
    }

    void update() {
        if (phase == Phase.OPENING
                && System.nanoTime() - phaseStartedAt >= TimeUnit.SECONDS.toNanos(5)) {
            recorder.requestAbort();
            complete(new Result(null, GdxVoiceRecorder.Outcome.NO_LINE));
        } else if (recording() && secondsLeft() <= 0f) {
            finish(false);
        }
    }

    float secondsLeft() {
        if (!recording()) return 0f;
        return Math.max(0f, GdxVoiceRecorder.MAX_SECONDS
                - (System.nanoTime() - phaseStartedAt) / 1_000_000_000f);
    }

    String progressKey(String recordingKey) {
        return switch (phase) {
            case OPENING -> "gdx.lobby.voice_opening";
            case RECORDING -> recordingKey;
            case PROCESSING -> "gdx.lobby.voice_processing";
            case CANCELLING -> "gdx.lobby.voice_cancelled";
            case IDLE -> null;
        };
    }
}
