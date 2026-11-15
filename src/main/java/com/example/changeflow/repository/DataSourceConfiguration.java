package com.example.changeflow.repository;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Connection and startup wiring only; no business operations or schema definitions. */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfiguration {

    @Bean
    public SqliteTransactionManager transactionManager(DataSource dataSource) {
        return new SqliteTransactionManager(dataSource);
    }

    @Bean
    public DataSource dataSource(@Value("${changeflow.database.path}") String configuredPath,
            @Value("${changeflow.database.busy-timeout-ms}") int busyTimeout) {
        Path path = localFilePath(configuredPath);
        if (busyTimeout <= 0) {
            throw new IllegalArgumentException("SQLite busy timeout must be positive");
        }
        // Operators provision a private local directory; never change existing directory ACLs.
        Path parent = path.getParent();
        if (!Files.isDirectory(parent) || !Files.isWritable(parent)
                || (Files.exists(path) && (!Files.isRegularFile(path)
                    || !Files.isReadable(path) || !Files.isWritable(path)))) {
            throw new IllegalArgumentException("SQLite storage must be a writable local file in a provisioned directory");
        }

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setReadUncommitted(false);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(busyTimeout);
        config.setTransactionMode(SQLiteConfig.TransactionMode.DEFERRED);
        SQLiteDataSource delegate = new SQLiteDataSource(config);
        // Use a literal filename, not a user-controlled SQLite URI/query string.
        delegate.setUrl("jdbc:sqlite:" + path);

        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = delegate.getConnection();
                try {
                    requirePragma(connection, "foreign_keys", 1);
                    requirePragma(connection, "read_uncommitted", 0);
                    requirePragma(connection, "synchronous", 2);
                    requirePragma(connection, "busy_timeout", busyTimeout);
                    return connection;
                } catch (SQLException failure) {
                    try {
                        connection.close();
                    } catch (SQLException closingFailure) {
                        failure.addSuppressed(closingFailure);
                    }
                    throw failure;
                }
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("SQLite datasource credentials are not supported");
            }
        };
    }

    @Bean
    public FlywayMigrationStrategy sqliteMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            // No connection is opened when this strategy is declared. On later startup,
            // the single migrator establishes WAL before executing any migration.
            try (Connection connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("PRAGMA journal_mode=WAL")) {
                if (!result.next() || !"wal".equalsIgnoreCase(result.getString(1))) {
                    throw new IllegalStateException("SQLite WAL initialization failed");
                }
            } catch (SQLException failure) {
                // Do not include filesystem paths or SQL in the public startup message.
                throw new IllegalStateException("SQLite startup initialization failed");
            }
            flyway.migrate();
        };
    }

    private static Path localFilePath(String value) {
        if (value == null || value.isBlank() || value.contains(":memory:")
                || value.startsWith("\\\\") || value.startsWith("//")
                || value.startsWith("file:") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException("An absolute local SQLite file path is required");
        }
        try {
            Path path = Path.of(value).normalize();
            if (!path.isAbsolute() || path.getParent() == null) {
                throw new IllegalArgumentException("An absolute local SQLite file path is required");
            }
            return path;
        } catch (InvalidPathException failure) {
            throw new IllegalArgumentException("Invalid SQLite file path");
        }
    }

    private static void requirePragma(Connection connection, String name, int expected) throws SQLException {
        try (var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            if (!result.next() || result.getInt(1) != expected) {
                throw new SQLException("Required SQLite connection setting was not applied");
            }
        }
    }
}
