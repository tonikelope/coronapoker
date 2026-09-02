package com.tonikelope.coronapoker.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import org.sqlite.SQLiteConfig;

/** Process owner of CoronaPoker's shared SQLite connection. */
public final class DatabaseService implements ApplicationService {

    private String databaseLocation;
    private Connection connection;
    private boolean started;
    private boolean closed;

    public DatabaseService(String databaseLocation) {
        this.databaseLocation = requireLocation(databaseLocation);
    }

    @Override
    public synchronized void start() throws ClassNotFoundException {
        if (started) {
            return;
        }
        Class.forName("org.sqlite.JDBC");
        started = true;
    }

    public synchronized String databaseLocation() {
        return databaseLocation;
    }

    /** Selects a different database before a connection has been opened. */
    public synchronized void useDatabase(String location) {
        if (connection != null) {
            throw new IllegalStateException("Cannot change database while its connection is open");
        }
        if (closed) {
            throw new IllegalStateException("Database service is closed");
        }
        databaseLocation = requireLocation(location);
    }

    /** Opens lazily and reopens after a per-session release. */
    public synchronized Connection connection() throws SQLException {
        if (!started) {
            throw new IllegalStateException("Database service has not started");
        }
        if (closed) {
            throw new IllegalStateException("Database service is closed");
        }
        if (connection == null || connection.isClosed()) {
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setJournalMode(SQLiteConfig.JournalMode.DELETE);
            config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
            config.setCacheSize(-50_000);
            config.setBusyTimeout(5000);
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseLocation, config.toProperties());
        }
        return connection;
    }

    /** Releases a game/session connection while keeping the process service reusable. */
    public synchronized void releaseConnection() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (closed) {
            return;
        }
        try {
            releaseConnection();
        } finally {
            closed = true;
        }
    }

    private static String requireLocation(String location) {
        String value = Objects.requireNonNull(location, "databaseLocation").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("databaseLocation is blank");
        }
        return value;
    }
}
