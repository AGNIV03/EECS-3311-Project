package libraryagent.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Thin JDBC wrapper around one SQLite connection. */
public final class Database implements AutoCloseable {

    /** Maps the current row of a result set to an object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Connection connection;
    private int transactionDepth;

    private Database(String url) {
        try {
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not open database " + url, e);
        }
    }

    public static Database open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new PersistenceException("Could not create directory for " + file, e);
        }
        return new Database("jdbc:sqlite:" + file.toAbsolutePath());
    }

    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    /** Creates any missing tables from {@code db/schema.sql}. */
    public void createSchema() {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("db/schema.sql is missing from resources");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("Could not read schema", e);
        }
        try (Statement st = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not create schema", e);
        }
    }

    public int update(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Update failed: " + sql, e);
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw new PersistenceException("Query failed: " + sql, e);
        }
    }

    public long count(String sql, Object... params) {
        return query(sql, rs -> rs.getLong(1), params).get(0);
    }

    /** Runs the work atomically; nested calls join the outer transaction. */
    public void inTransaction(Runnable work) {
        try {
            if (transactionDepth == 0) {
                connection.setAutoCommit(false);
            }
            transactionDepth++;
            try {
                work.run();
            } catch (RuntimeException e) {
                transactionDepth--;
                if (transactionDepth == 0) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
                throw e;
            }
            transactionDepth--;
            if (transactionDepth == 0) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Transaction failed", e);
        }
    }

    /** Next identifier for a prefix, e.g. {@code nextId("L", 4)} returns "L0001", then "L0002". */
    public String nextId(String prefix, int width) {
        update("INSERT INTO id_counters(prefix, next_value) VALUES (?, 1) "
                + "ON CONFLICT(prefix) DO UPDATE SET next_value = next_value + 1", prefix);
        long value = count("SELECT next_value FROM id_counters WHERE prefix = ?", prefix);
        return prefix + String.format("%0" + width + "d", value);
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new PersistenceException("Could not close database", e);
        }
    }
}
