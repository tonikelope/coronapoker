package com.tonikelope.coronapoker.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Owns the single in-flight create/join attempt shared by all frontends.
 * Draft preferences are committed only after the real session accepts the
 * handoff; failures and cancellation leave the form retryable.
 */
public final class NewGameSubmissionCoordinator {

    private final PreferencesService preferences;
    private final NewGameSessionGateway gateway;
    private Attempt active;

    public NewGameSubmissionCoordinator(PreferencesService preferences,
            NewGameSessionGateway gateway) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public synchronized boolean submitting() {
        return active != null;
    }

    public CompletableFuture<NewGameRequest> submit(
            NewGameConnectionDraft connection, NewGameTableDraft table) {
        Objects.requireNonNull(connection, "connection");
        final Attempt attempt;
        synchronized (this) {
            if (active != null) {
                throw new IllegalStateException("A new-game submission is already active");
            }
            NewGameRequest request = NewGameRequest.begin(connection, table);
            attempt = new Attempt(connection, request);
            active = attempt;
        }

        try {
            attempt.opening = Objects.requireNonNull(gateway.open(attempt.request),
                    "gateway result");
            attempt.opening.whenComplete((ignored, failure) -> finish(attempt, failure));
        } catch (Throwable failure) {
            finish(attempt, failure);
        }
        return attempt.result;
    }

    /** Requests cancellation of the current asynchronous opener, if any. */
    public synchronized boolean cancel() {
        return active != null && active.opening != null
                && active.opening.cancel(true);
    }

    private void finish(Attempt attempt, Throwable failure) {
        Throwable outcome = unwrap(failure);
        synchronized (this) {
            if (active != attempt) {
                return;
            }
            active = null;
            if (outcome == null) {
                try {
                    attempt.connection.commitSuccessful(preferences,
                            attempt.request.connection());
                } catch (Throwable commitFailure) {
                    attempt.connection.submissionFailed();
                    outcome = commitFailure;
                }
            } else {
                attempt.connection.submissionFailed();
            }
        }

        if (outcome == null) {
            attempt.result.complete(attempt.request);
        } else {
            attempt.result.completeExceptionally(outcome);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException completion
                && completion.getCause() != null) {
            return completion.getCause();
        }
        return failure;
    }

    private static final class Attempt {
        private final NewGameConnectionDraft connection;
        private final NewGameRequest request;
        private final CompletableFuture<NewGameRequest> result = new CompletableFuture<>();
        private CompletableFuture<Void> opening;

        private Attempt(NewGameConnectionDraft connection, NewGameRequest request) {
            this.connection = connection;
            this.request = request;
        }
    }
}
