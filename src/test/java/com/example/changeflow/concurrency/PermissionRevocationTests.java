package com.example.changeflow.concurrency;

import com.example.changeflow.domain.*;
import com.example.changeflow.support.WorkflowTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.changeflow.repository.*;
import com.example.changeflow.domain.*;
import com.example.changeflow.support.ControlledOperations;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PermissionRevocationTests {
    private java.util.UUID roleSink(WorkflowTestSupport f) {
        var sink = java.util.UUID.randomUUID(); f.db.user(sink, sink.toString()); return sink;
    }

    @Test void currentRoleRevocationBeforeFirstSelectRejectsEverySensitiveOperation() {
        for (String operation : new String[]{"create", "edit-draft", "edit-returned", "submit-draft", "submit-returned", "approve", "return"}) {
            var f = new WorkflowTestSupport();
            var sink = roleSink(f);
            boolean review = operation.equals("approve") || operation.equals("return");
            var actor = review || operation.endsWith("returned") ? f.db.dual : f.db.owner;
            var id = f.db.request(review ? f.db.owner : actor, review ? "PendingReview" : operation.endsWith("returned") ? "ChangesRequested" : "Draft", 0);
            var before = f.db.storedState();
            var identities = spy(f.identities);
            var first = new java.util.concurrent.atomic.AtomicBoolean(true);
            doAnswer(call -> {
                if (first.getAndSet(false)) {
                    f.db.externalUpdate("UPDATE user_role SET user_id=? WHERE user_id=? AND role=?", sink.toString(), actor.toString(), review ? "Reviewer" : "Requester");
                }
                return call.callRealMethod();
            }).when(identities).loadCurrentPermissions(any());
            assertTrue(f.identities.findForAuthentication(actor.equals(f.db.owner) ? "owner" : "dual").orElseThrow().enabled());
            var service = f.service(identities, f.requests, f.reasons);
            var failure = assertThrows(RequestFailure.class, () -> {
                if (operation.equals("create")) { service.create(actor, WorkflowTestSupport::complete); }
                else {
                    var action = review ? operation.equals("approve") ? ChangeRequestService.Action.APPROVE : ChangeRequestService.Action.REQUEST_CHANGES
                            : operation.startsWith("edit") ? ChangeRequestService.Action.EDIT : ChangeRequestService.Action.SUBMIT;
                    service.mutate(actor, id, action, WorkflowTestSupport::complete, () -> "feedback", () -> WorkflowTestSupport.version(id, 0));
                }
            }, operation);
            assertEquals(operation.equals("create") ? RequestFailure.Kind.PERMISSION_DENIED : RequestFailure.Kind.REQUEST_NOT_FOUND, failure.kind(), operation);
            assertEquals(before, f.db.storedState(), operation);
            assertEquals(1, f.db.jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?", Integer.class, actor.toString()));
            assertThrows(RequestFailure.class, () -> service.get(actor, id));
            assertTrue(service.list(actor, () -> 50, () -> null).items().isEmpty());
        }
    }

    @Test void authorizedReadMayFinishAfterRoleRevocationButNextReadUsesRemainingRole() {
        var f = new WorkflowTestSupport(); var sink = roleSink(f);
        var id = f.db.request(f.db.dual, "Draft", 0);
        var identities = spy(f.identities);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (first.getAndSet(false)) {
                f.db.externalUpdate("UPDATE user_role SET user_id=? WHERE user_id=? AND role='Requester'", sink.toString(), f.db.dual.toString());
            }
            return result;
        }).when(identities).loadCurrentPermissions(any());
        var service = f.service(identities, f.requests, f.reasons);
        assertEquals(id, service.get(f.db.dual, id).request().id());
        assertEquals(RequestFailure.Kind.REQUEST_NOT_FOUND, assertThrows(RequestFailure.class, () -> service.get(f.db.dual, id)).kind());
        assertTrue(f.identities.findForAuthentication("dual").orElseThrow().enabled());
    }

    @Test void roleRevokedAfterActualRollbackBeforeDiagnosticFirstSelectIsConcealed() throws Exception {
        var f = new WorkflowTestSupport(); var sink = roleSink(f);
        var id = f.db.request(f.db.dual, "Draft", 0);
        var before = f.db.storedState();
        var ready = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var firstCleanup = new java.util.concurrent.atomic.AtomicBoolean(true);
        var oldConnection = new java.util.concurrent.atomic.AtomicReference<java.sql.Connection>();
        var rolledBack = new java.util.concurrent.atomic.AtomicBoolean();
        var requests = spy(f.requests); var identities = spy(f.identities);
        doAnswer(call -> {
            f.db.externalUpdate("UPDATE app_user SET username=? WHERE id=?", "unrelated-" + sink, sink.toString());
            return call.callRealMethod(); // actual SQLITE_BUSY_SNAPSHOT, not a synthetic conflict
        }).when(requests).update(any(), any());
        var manager = new SqliteTransactionManager(f.db.dataSource) {
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
                super.doRollback(status); rolledBack.set(true);
            }
            @Override protected void doCleanupAfterCompletion(Object transaction) {
                var old = DataSourceUtils.getConnection(f.db.dataSource);
                DataSourceUtils.releaseConnection(old, f.db.dataSource);
                super.doCleanupAfterCompletion(transaction);
                if (firstCleanup.getAndSet(false)) {
                    oldConnection.set(old);
                    assertFalse(TransactionSynchronizationManager.hasResource(f.db.dataSource));
                    try { assertTrue(old.isClosed()); } catch (java.sql.SQLException failure) { throw new AssertionError(failure); }
                    assertTrue(rolledBack.get()); ready.countDown(); ControlledOperations.await(release);
                }
            }
        };
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            if (calls.incrementAndGet() == 2) {
                assertTrue(rolledBack.get()); assertTrue(oldConnection.get().isClosed());
                var fresh = DataSourceUtils.getConnection(f.db.dataSource);
                assertNotSame(oldConnection.get(), fresh); DataSourceUtils.releaseConnection(fresh, f.db.dataSource);
            }
            return call.callRealMethod();
        }).when(identities).loadCurrentPermissions(any());
        var service = new ChangeRequestService(identities, requests, f.reasons, manager);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> assertThrows(RequestFailure.class, () -> service.mutate(f.db.dual, id,
                    ChangeRequestService.Action.EDIT, WorkflowTestSupport::complete, () -> null, () -> WorkflowTestSupport.version(id, 0))));
            ControlledOperations.await(ready);
            f.db.externalUpdate("UPDATE user_role SET user_id=? WHERE user_id=? AND role='Requester'", sink.toString(), f.db.dual.toString());
            release.countDown();
            var hidden = result.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(RequestFailure.Kind.REQUEST_NOT_FOUND, hidden.kind());
            assertEquals(RequestFailure.Kind.REQUEST_NOT_FOUND, assertThrows(RequestFailure.class, () -> service.get(f.db.dual, java.util.UUID.randomUUID())).kind());
            assertEquals(before, f.db.storedState());
            verify(requests, times(1)).update(any(), any());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test
    void revocationBeforeFirstPermissionSelectRejectsWithoutChanges() {
        var f = new WorkflowTestSupport();
        var id = f.db.request(f.db.owner, "Draft", 0);
        f.db.externalUpdate("UPDATE app_user SET enabled=0 WHERE id=?", f.db.owner.toString());
        var failure = assertThrows(RequestFailure.class, () -> f.service().get(f.db.owner, id));
        assertEquals(RequestFailure.Kind.REQUEST_NOT_FOUND, failure.kind());
        assertEquals(0, f.requests.find(id).orElseThrow().request().version());
    }

    @Test
    void failedSnapshotDiagnosisUsesCurrentRevokedAccessNotEarlierGrant() {
        var f = new WorkflowTestSupport();
        var id = f.db.request(f.db.owner, "Draft", 0);
        var requests = spy(f.requests);
        doAnswer(call -> {
            f.db.externalUpdate("UPDATE app_user SET enabled=0 WHERE id=?", f.db.owner.toString());
            return call.callRealMethod();
        }).when(requests).update(any(), any());
        var service = f.service(f.identities, requests, f.reasons);
        var failure = assertThrows(RequestFailure.class, () -> service.mutate(f.db.owner, id, ChangeRequestService.Action.EDIT,
                WorkflowTestSupport::complete, () -> null, () -> WorkflowTestSupport.version(id, 0)));
        assertEquals(RequestFailure.Kind.REQUEST_NOT_FOUND, failure.kind());
        assertEquals(0, f.requests.find(id).orElseThrow().request().version());
        verify(requests).update(any(), any());
        assertThrows(RequestFailure.class, () -> service.get(f.db.owner, id));
    }

    @Test
    void alreadyAuthorizedReadCanFinishButNextReadRechecks() {
        var f = new WorkflowTestSupport();
        var id = f.db.request(f.db.owner, "Draft", 0);
        var identity = spy(f.identities);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (first.getAndSet(false)) { f.db.externalUpdate("UPDATE app_user SET enabled=0 WHERE id=?", f.db.owner.toString()); }
            return result;
        }).when(identity).loadCurrentPermissions(any());
        var service = f.service(identity, f.requests, f.reasons);
        assertEquals(id, service.get(f.db.owner, id).request().id());
        assertThrows(RequestFailure.class, () -> service.get(f.db.owner, id));
    }
}
