package com.example.changeflow.repository;

import com.example.changeflow.support.SqliteTestSupport;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class SchemaIntegrationTests {
    @Test
    void migrationsWalPragmasAndIndependentConnectionsUseActualSqlite() throws Exception {
        var fixture = new SqliteTestSupport();
        assertEquals(3, fixture.jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals(4, fixture.jdbc.queryForObject("SELECT count(*) FROM pragma_table_list WHERE name IN ('app_user','user_role','change_request','return_reason') AND strict=1", Integer.class));
        assertTrue(fixture.jdbc.queryForObject("SELECT sqlite_version()", String.class).startsWith("3.53.4"));
        try (Connection a = fixture.dataSource.getConnection(); Connection b = fixture.dataSource.getConnection()) {
            assertNotSame(a, b);
            for (Connection connection : new Connection[]{a, b}) {
                assertEquals(1, SqliteTestSupport.pragma(connection, "foreign_keys"));
                assertEquals(0, SqliteTestSupport.pragma(connection, "read_uncommitted"));
                assertEquals(2, SqliteTestSupport.pragma(connection, "synchronous"));
                assertEquals(100, SqliteTestSupport.pragma(connection, "busy_timeout"));
            }
        }
        assertEquals("wal", fixture.jdbc.queryForObject("PRAGMA journal_mode", String.class));
        SqliteTestSupport.migrate(fixture.dataSource); // repeat startup validates without resetting data
        assertEquals(4, fixture.jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class));
    }

    @Test
    void constraintsAndUnicodeLengthsAreEnforcedWithoutTruncation() {
        var f = new SqliteTestSupport();
        UUID id = f.request(f.owner, "Draft", 0);
        f.jdbc.update("UPDATE change_request SET title=? WHERE id=?", "\uD83D\uDE00".repeat(200), id.toString());
        assertEquals(200, f.jdbc.queryForObject("SELECT length(title) FROM change_request WHERE id=?", Integer.class, id.toString()));
        for (String sql : new String[]{
                "UPDATE change_request SET version=-1", "UPDATE change_request SET version='bad'",
                "UPDATE change_request SET status='Unknown'", "UPDATE change_request SET title=NULL",
                "UPDATE app_user SET enabled=2", "INSERT INTO user_role(user_id,role) VALUES ('missing','Requester')",
                "INSERT INTO user_role(user_id,role) VALUES ('" + f.owner + "','Invalid')"}) {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(sql));
        }
        assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(
                "UPDATE change_request SET title=?", "x".repeat(201)));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(
                "UPDATE change_request SET change_description=?", "x".repeat(5001)));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(
                "UPDATE change_request SET change_reason=?", "x".repeat(2001)));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(
                "INSERT INTO app_user VALUES (?,?,?,1)", UUID.randomUUID().toString(), "owner", "unused"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(
                "INSERT INTO return_reason VALUES (?,0,?,?)", id.toString(), f.reviewer.toString(), "reason"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> f.jdbc.update(
                "INSERT INTO return_reason VALUES (?,1,?,?)", id.toString(), f.reviewer.toString(), "x".repeat(2001)));
        assertEquals(0, f.jdbc.queryForObject("SELECT count(*) FROM return_reason", Integer.class));
    }

    @Test
    void committedContentVersionsAndFeedbackSurviveReopen() {
        var f = new SqliteTestSupport();
        UUID id = f.request(f.owner, "ChangesRequested", 2);
        f.jdbc.update("INSERT INTO return_reason VALUES (?,2,?,?)", id.toString(), f.reviewer.toString(), "revise");
        var reopened = new DataSourceConfiguration().dataSource(f.file.toString(), 100);
        SqliteTestSupport.migrate(reopened);
        var jdbc = new JdbcTemplate(reopened);
        assertEquals(2L, jdbc.queryForObject("SELECT version FROM change_request WHERE id=?", Long.class, id.toString()));
        assertEquals("revise", jdbc.queryForObject("SELECT reason FROM return_reason WHERE request_id=?", String.class, id.toString()));
    }

    @Test
    void checksumMismatchAndUnknownSchemaFailClosed() {
        var f = new SqliteTestSupport();
        f.jdbc.update("UPDATE flyway_schema_history SET checksum=0 WHERE version='1'");
        assertThrows(Exception.class, () -> SqliteTestSupport.migrate(f.dataSource));
        var source = new DataSourceConfiguration().dataSource(SqliteTestSupport.newFile().toString(), 100);
        new JdbcTemplate(source).execute("CREATE TABLE unknown_data(value TEXT) STRICT");
        assertThrows(Exception.class, () -> SqliteTestSupport.migrate(source));
    }
}
