package com.example.changeflow.repository;

import com.example.changeflow.domain.*;
import com.example.changeflow.support.SqliteTestSupport;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Faults wrap real unpooled connections; the transaction manager is the actual Boot bean. */
@SpringBootTest
class TransactionCleanupTests {
    private static final java.nio.file.Path FILE = SqliteTestSupport.newFile();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { registry.add("changeflow.database.path", FILE::toString); }
    @MockitoSpyBean DataSource source;
    @Autowired PlatformTransactionManager manager;
    @Autowired ChangeRequestService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ReturnReasonRepository reasons;
    @MockitoSpyBean IdentityRepository identities;

    @Test void failedRollbackBeforeSqlMustNotCommitTheCasWithoutFeedback() throws Exception { check("rollback-before"); }
    @Test void failedRollbackAcknowledgementDiscardsTheConnection() throws Exception { check("rollback-after"); }
    @Test void failedCommitBeforeSqlMustNotBeRetriedByAutoCommitRestore() throws Exception { check("commit-before"); }
    @Test void lostCommitAcknowledgementKeepsAWholeUnknownOutcome() throws Exception { check("commit-after"); }

    private void check(String fault) throws Exception {
        var owner = UUID.randomUUID(); var reviewer = UUID.randomUUID(); var id = UUID.randomUUID();
        for (var user : List.of(owner, reviewer)) {
            jdbc.update("INSERT INTO app_user VALUES (?,?,?,1)", user.toString(), user.toString(), "unused-test-hash");
            jdbc.update("INSERT INTO user_role VALUES (?,?)", user.toString(), user.equals(owner) ? "Requester" : "Reviewer");
        }
        jdbc.update("INSERT INTO change_request VALUES (?,?,?,?,?,?,?)", id.toString(), owner.toString(), "t", "d", "r", "PendingReview", 0);
        var originalRow = jdbc.queryForList("SELECT * FROM change_request WHERE id=?", id.toString());
        if (fault.startsWith("rollback")) {
            doThrow(new org.springframework.dao.DataAccessResourceFailureException("injected feedback failure")).when(reasons).append(any());
        }
        var armed = new AtomicBoolean(true);
        var events = new ArrayList<String>();
        var physical = new ArrayList<Connection>();
        doAnswer(call -> {
            Connection real = (Connection) call.callRealMethod();
            if (!armed.getAndSet(false)) { return real; }
            physical.add(real);
            ((org.sqlite.SQLiteConnection) real).addCommitListener(new org.sqlite.SQLiteCommitListener() {
                @Override public void onCommit() { events.add("native-commit"); }
                @Override public void onRollback() { events.add("native-rollback"); }
            });
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("setAutoCommit") && Boolean.TRUE.equals(args[0])) { events.add("restore-autocommit"); }
                if (name.equals("commit") || name.equals("rollback") || name.equals("close")) { events.add(name); }
                if (fault.equals(name + "-before")) { throw new SQLException("injected before SQL"); }
                try {
                    Object result = method.invoke(real, args);
                    if (fault.equals(name + "-after")) { throw new SQLException("injected lost acknowledgement"); }
                    return result;
                } catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        }).when(source).getConnection();
        clearInvocations(identities);
        var failure = assertThrows(RequestFailure.class, () -> service.mutate(reviewer, id,
                ChangeRequestService.Action.REQUEST_CHANGES, () -> null, () -> "feedback", () -> new ExpectedVersion(id, 0)));
        assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, failure.kind());
        assertTrue(physical.getFirst().isClosed(), "unpooled physical connection must be discarded");
        assertInstanceOf(org.sqlite.SQLiteConnection.class, physical.getFirst(), "production source exposes a physical SQLite connection, not a pool lease");
        long expectedVersion = fault.equals("commit-after") ? 1 : 0;
        assertEquals(expectedVersion, jdbc.queryForObject("SELECT version FROM change_request WHERE id=?", Long.class, id.toString()), events.toString());
        assertEquals(fault.equals("commit-after") ? 1 : 0,
                jdbc.queryForObject("SELECT count(*) FROM return_reason WHERE request_id=?", Integer.class, id.toString()));
        if (!fault.equals("commit-after")) {
            assertEquals(originalRow, jdbc.queryForList("SELECT * FROM change_request WHERE id=?", id.toString()));
        } else {
            assertEquals("ChangesRequested", jdbc.queryForObject("SELECT status FROM change_request WHERE id=?", String.class, id.toString()));
            assertEquals("feedback", jdbc.queryForObject("SELECT reason FROM return_reason WHERE request_id=?", String.class, id.toString()));
        }
        assertEquals(fault.equals("commit-after") ? 1 : 0, Collections.frequency(events, "native-commit"), events.toString());
        assertFalse(events.contains("restore-autocommit"), "cleanup must close, not implicitly COMMIT: " + events);
        assertEquals(fault.startsWith("commit") ? 1 : 0, Collections.frequency(events, "commit"));
        verify(identities, times(1)).loadCurrentPermissions(any());
        try (var next = source.getConnection()) { assertNotSame(physical.getFirst(), next); }
    }
}
