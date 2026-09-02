package com.tonikelope.coronapoker.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Renderer-neutral staged state shared by the create, join and recover flows.
 * No persistent value changes until a successful session handoff commits it.
 */
public final class NewGameConnectionDraft {

    public static final int DEFAULT_PORT = 7234;
    public static final int MAX_NICK_LENGTH = 15;
    public static final int MAX_PASSWORD_LENGTH = 30;
    public static final int MAX_PORT_LENGTH = 5;
    public static final long MAX_AVATAR_BYTES = 256L * 1024L;

    public enum Mode {
        CREATE, JOIN, RECOVER
    }

    private final Mode mode;
    private final List<String> serverHistory;
    private String nickname;
    private String password = "";
    private String server;
    private String port;
    private Path avatar;
    private boolean upnp;
    private boolean recoverRequested;
    private boolean recoverLoading;
    private Integer recoveredGameId;
    private boolean submitting;
    private boolean committed;
    private Submission activeSubmission;

    private NewGameConnectionDraft(Mode mode, List<String> serverHistory) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.serverHistory = new ArrayList<>(serverHistory);
    }

    public static NewGameConnectionDraft from(Properties properties, Mode mode) {
        Objects.requireNonNull(properties, "properties");
        NewGameConnectionDraft draft = new NewGameConnectionDraft(mode,
                parseHistory(properties.getProperty("server_history", "")));
        draft.setNickname(properties.getProperty("nick", ""));
        draft.setAvatarPath(properties.getProperty("avatar", ""));
        boolean host = mode != Mode.JOIN;
        draft.setServer(properties.getProperty(host ? "local_ip" : "server_ip", "localhost"));
        draft.setPort(properties.getProperty(host ? "local_port" : "server_port",
                Integer.toString(DEFAULT_PORT)));
        draft.upnp = host && Boolean.parseBoolean(properties.getProperty("upnp", "false"));
        draft.recoverRequested = mode == Mode.RECOVER;
        return draft;
    }

    public Mode mode() {
        return mode;
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String value) {
        nickname = truncate(value, MAX_NICK_LENGTH);
    }

    public String password() {
        return password;
    }

    public void setPassword(String value) {
        password = truncate(value, MAX_PASSWORD_LENGTH);
    }

    public String server() {
        return server;
    }

    public void setServer(String value) {
        server = Objects.requireNonNullElse(value, "");
    }

    public String port() {
        return port;
    }

    /** Applies the same numeric-only, five-character replacement contract as Swing. */
    public void setPort(String value) {
        String candidate = Objects.requireNonNullElse(value, "");
        if (candidate.length() <= MAX_PORT_LENGTH && candidate.matches("[0-9]*")) {
            port = candidate;
        }
    }

    public Path avatar() {
        return avatar;
    }

    public boolean setAvatar(Path value) {
        Path normalized = value == null ? null : value.toAbsolutePath().normalize();
        if (normalized != null && (!Files.isRegularFile(normalized)
                || !Files.isReadable(normalized) || fileSize(normalized) > MAX_AVATAR_BYTES)) {
            return false;
        }
        avatar = normalized;
        return true;
    }

    public void resetAvatar() {
        avatar = null;
    }

    public boolean upnp() {
        return upnp;
    }

    public void setUpnp(boolean value) {
        upnp = mode != Mode.JOIN && value;
    }

    public boolean recoverRequested() {
        return recoverRequested;
    }

    public void setRecoverRequested(boolean value) {
        if (mode != Mode.JOIN) {
            recoverRequested = value;
            if (!value) {
                recoverLoading = false;
                recoveredGameId = null;
            }
        }
    }

    public boolean beginRecoverLoad() {
        if (!recoverRequested || recoverLoading) {
            return false;
        }
        recoverLoading = true;
        recoveredGameId = null;
        return true;
    }

    public void completeRecoverLoad(int gameId) {
        if (!recoverLoading) {
            throw new IllegalStateException("No recover load is active");
        }
        recoveredGameId = gameId;
        recoverLoading = false;
    }

    public void failRecoverLoad() {
        recoverLoading = false;
        recoveredGameId = null;
    }

    public boolean recoverLoading() {
        return recoverLoading;
    }

    public List<String> serverHistory() {
        return Collections.unmodifiableList(serverHistory);
    }

    public boolean canSubmit() {
        return !submitting && !committed && !recoverLoading
                && !submittedNickname().isBlank()
                && !server.trim().isEmpty()
                && !port.isEmpty()
                && (!recoverRequested || recoveredGameId != null);
    }

    /** Locks submission and returns the sanitized immutable handoff payload. */
    public Submission beginSubmission() {
        if (!canSubmit()) {
            throw new IllegalStateException("New-game connection fields are incomplete or busy");
        }
        submitting = true;
        activeSubmission = new Submission(mode, submittedNickname(), password,
                server.trim(), port, avatar, upnp, recoverRequested, recoveredGameId);
        return activeSubmission;
    }

    public void submissionFailed() {
        submitting = false;
        activeSubmission = null;
    }

    /** Called only after identity/network session creation has succeeded. */
    public void commitSuccessful(PreferencesService preferences, Submission submission) {
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(submission, "submission");
        if (!submitting || !submission.equals(activeSubmission)) {
            throw new IllegalStateException("Submission is not active for this draft");
        }
        Properties properties = preferences.properties();
        properties.setProperty("nick", submission.nickname());
        boolean host = submission.mode() != Mode.JOIN;
        properties.setProperty(host ? "local_ip" : "server_ip", submission.server());
        properties.setProperty(host ? "local_port" : "server_port", submission.port());
        properties.setProperty("avatar", submission.avatar() == null
                ? "" : submission.avatar().toString());
        if (host) {
            properties.setProperty("upnp", Boolean.toString(submission.upnp()));
        } else {
            rememberServer(submission.server() + ":" + submission.port());
            properties.setProperty("server_history", String.join("@", serverHistory));
        }
        preferences.saveDeferred();
        submitting = false;
        committed = true;
        activeSubmission = null;
    }

    private void setAvatarPath(String value) {
        try {
            if (value == null || value.isBlank() || !setAvatar(Path.of(value))) {
                avatar = null;
            }
        } catch (RuntimeException ex) {
            avatar = null;
        }
    }

    private String submittedNickname() {
        return nickname.trim().replace("$", "");
    }

    private void rememberServer(String endpoint) {
        serverHistory.remove(endpoint);
        serverHistory.add(endpoint);
    }

    private static List<String> parseHistory(String encoded) {
        List<String> result = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String endpoint : encoded.split("@")) {
            if (!endpoint.isBlank() && !result.contains(endpoint)) {
                result.add(endpoint);
            }
        }
        return result;
    }

    private static String truncate(String value, int limit) {
        String safe = Objects.requireNonNullElse(value, "");
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return Long.MAX_VALUE;
        }
    }

    public record Submission(Mode mode, String nickname, String password,
            String server, String port, Path avatar, boolean upnp,
            boolean recover, Integer recoveredGameId) {
    }
}
