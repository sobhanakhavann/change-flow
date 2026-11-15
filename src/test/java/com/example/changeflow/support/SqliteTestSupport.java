package com.example.changeflow.support;

import com.example.changeflow.repository.DataSourceConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Real private per-scenario files. Retained under build/test-databases; never deletes existing data. */
public final class SqliteTestSupport {
    public record StoredState(java.util.List<java.util.Map<String, Object>> requests,
            java.util.List<java.util.Map<String, Object>> reasons) { }

    /** Detached complete business rows, deliberately excluding identity credentials. */
    public StoredState storedState() {
        return new StoredState(jdbc.queryForList("SELECT * FROM change_request ORDER BY id").stream().map(java.util.Map::copyOf).toList(),
                jdbc.queryForList("SELECT * FROM return_reason ORDER BY request_id,request_version").stream().map(java.util.Map::copyOf).toList());
    }
    public static final String PASSWORD = "test-password";
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);
    public final Path file;
    public final DataSource dataSource;
    public final JdbcTemplate jdbc;
    public final UUID owner = UUID.randomUUID();
    public final UUID reviewer = UUID.randomUUID();
    public final UUID dual = UUID.randomUUID();
    public final UUID other = UUID.randomUUID();

    public SqliteTestSupport() { this(100); }

    public SqliteTestSupport(int timeout) {
        file = newFile();
        dataSource = new DataSourceConfiguration().dataSource(file.toString(), timeout);
        jdbc = new JdbcTemplate(dataSource);
        migrate(dataSource);
        user(owner, "owner", "Requester");
        user(reviewer, "reviewer", "Reviewer");
        user(dual, "dual", "Requester", "Reviewer");
        user(other, "other", "Requester");
    }

    public static Path newFile() {
        try {
            Path parent = Path.of("build", "test-databases", UUID.randomUUID().toString()).toAbsolutePath();
            Files.createDirectories(parent);
            return parent.resolve("changeflow.sqlite");
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot prepare test storage", failure); }
    }

    public static Flyway migrate(DataSource source) {
        Flyway flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .cleanDisabled(true).baselineOnMigrate(false).load();
        new DataSourceConfiguration().sqliteMigrationStrategy(source).migrate(flyway);
        return flyway;
    }

    public void user(UUID id, String name, String... roles) {
        jdbc.update("INSERT INTO app_user(id,username,password_hash,enabled) VALUES (?,?,?,1)", id.toString(), name, HASH);
        for (String role : roles) { jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", id.toString(), role); }
    }

    public UUID request(UUID requester, String status, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO change_request(id,requester_id,title,change_description,change_reason,status,version) VALUES (?,?,?,?,?,?,?)",
                id.toString(), requester.toString(), "title", "description", "reason", status, version);
        return id;
    }

    /** Independent committed writer, even when the calling thread has a Spring-bound transaction. */
    public void externalUpdate(String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) { statement.setObject(i + 1, parameters[i]); }
            statement.executeUpdate();
        } catch (SQLException failure) { throw new IllegalStateException("Test external update failed", failure); }
    }

    public static int pragma(Connection connection, String key) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA " + key)) {
            result.next(); return result.getInt(1);
        }
    }
}
