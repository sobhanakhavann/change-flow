package com.example.changeflow.concurrency;

import com.example.changeflow.domain.*;
import com.example.changeflow.repository.*;
import com.example.changeflow.support.*;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestConcurrencyTests {
    @Test
    void unconfirmedRollbackSkipsDiagnosisAndNeverReplays() {
        var f = new WorkflowTestSupport();
        var id = f.db.request(f.db.owner, "Draft", 0);
        var identities = spy(f.identities);
        var requests = spy(f.requests);
        doThrow(new ChangeRequestRepository.SnapshotFailure()).when(requests).update(any(), any());
        var manager = new SqliteTransactionManager(f.db.dataSource) {
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
                super.doRollback(status);
                throw new org.springframework.transaction.TransactionSystemException("injected rollback acknowledgement failure");
            }
        };
        var service = new ChangeRequestService(identities, requests, f.reasons, manager);
        assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, assertThrows(RequestFailure.class, () -> service.mutate(f.db.owner, id,
                ChangeRequestService.Action.EDIT, WorkflowTestSupport::complete, () -> null, () -> WorkflowTestSupport.version(id, 0))).kind());
        verify(identities, times(1)).loadCurrentPermissions(any());
        verify(requests, times(1)).update(any(), any());
        assertEquals(0, f.requests.find(id).orElseThrow().request().version());
    }
    static Stream<Arguments> orders() {
        return Stream.of("edit-edit", "approve-return", "edit-submit-draft", "edit-submit-returned")
                .flatMap(pair -> Stream.of(Arguments.of(pair, 0), Arguments.of(pair, 1)));
    }

    @ParameterizedTest
    @MethodSource("orders")
    void realSnapshotsRollbackBeforeFreshPrivateSafeDiagnosis(String pair, int winner) throws Exception {
        var f = new WorkflowTestSupport();
        String state = pair.equals("approve-return") ? "PendingReview" : pair.endsWith("returned") ? "ChangesRequested" : "Draft";
        var id = f.db.request(f.db.dual, state, 1);
        if (state.equals("ChangesRequested")) {
            f.db.jdbc.update("INSERT INTO return_reason VALUES (?,1,?,?)", id.toString(), f.db.reviewer.toString(), "previous feedback");
        }
        var initial = f.db.storedState();
        var gates = new ControlledOperations();
        var requests = spy(f.requests);
        var identities = spy(f.identities);
        var reasons = spy(f.reasons);
        Map<Integer, Connection> connections = new ConcurrentHashMap<>();
        Map<Integer, Integer> completions = new ConcurrentHashMap<>();
        Map<Integer, AtomicInteger> reads = new ConcurrentHashMap<>();
        var snapshots = new AtomicInteger();
        doAnswer(call -> {
            int actor = gates.actor.get();
            Connection connection = DataSourceUtils.getConnection(f.db.dataSource);
            DataSourceUtils.releaseConnection(connection, f.db.dataSource);
            int read = reads.computeIfAbsent(actor, ignored -> new AtomicInteger()).getAndIncrement();
            if (read == 0) {
                gates.record("permission-select");
                connections.put(actor, connection);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        completions.put(actor, status);
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) { gates.record("after-completion-rollback"); }
                    }
                });
            } else {
                assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK, completions.get(actor));
                assertNotSame(connections.get(actor), connection, "diagnosis must use a fresh connection/snapshot");
                assertTrue(connections.get(actor).isClosed());
                gates.record("diagnostic-permission-select");
            }
            return call.callRealMethod();
        }).when(identities).loadCurrentPermissions(any());
        doAnswer(call -> {
            gates.pause();
            try { return call.callRealMethod(); }
            catch (ChangeRequestRepository.SnapshotFailure failure) {
                gates.record("callback-failure"); snapshots.incrementAndGet(); throw failure;
            }
        }).when(requests).update(any(), any());
        var manager = new SqliteTransactionManager(f.db.dataSource) {
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
                super.doRollback(status); gates.record("rollback");
            }
            @Override protected void doCleanupAfterCompletion(Object transaction) {
                Connection old = DataSourceUtils.getConnection(f.db.dataSource);
                DataSourceUtils.releaseConnection(old, f.db.dataSource);
                super.doCleanupAfterCompletion(transaction);
                assertFalse(TransactionSynchronizationManager.hasResource(f.db.dataSource));
                try { assertTrue(old.isClosed()); } catch (java.sql.SQLException failure) { throw new AssertionError(failure); }
                gates.record("unbound-and-closed");
            }
        };
        var service = new ChangeRequestService(identities, requests, reasons, manager);
        var executor = Executors.newFixedThreadPool(2);
        @SuppressWarnings("unchecked") Future<Object>[] results = new Future[2];
        try {
            for (int i = 0; i < 2; i++) {
                int index = i;
                results[i] = executor.submit(() -> {
                    gates.actor.set(index);
                    try {
                        var action = pair.equals("approve-return") ? (index == 0 ? ChangeRequestService.Action.APPROVE : ChangeRequestService.Action.REQUEST_CHANGES)
                                : index == 1 && !pair.equals("edit-edit") ? ChangeRequestService.Action.SUBMIT : ChangeRequestService.Action.EDIT;
                        return service.mutate(pair.equals("approve-return") ? f.db.reviewer : f.db.dual, id, action,
                                () -> new ChangeRequestRules.Content("edit-" + index, "d", "r"), () -> "revise",
                                () -> WorkflowTestSupport.version(id, 1));
                    } catch (RequestFailure failure) { return failure.kind(); }
                    finally { gates.actor.remove(); }
                });
            }
            ControlledOperations.await(gates.ready);
            assertNotSame(connections.get(0), connections.get(1));
            gates.release(winner);
            assertInstanceOf(RequestView.class, results[winner].get(10, TimeUnit.SECONDS));
            var winnerState = f.db.storedState();
            gates.release(1 - winner);
            Object loser = results[1 - winner].get(10, TimeUnit.SECONDS);
            assertEquals(pair.equals("approve-return") && winner == 1 ? RequestFailure.Kind.REQUEST_NOT_FOUND
                    : RequestFailure.Kind.VERSION_CONFLICT, loser);
            assertEquals(1, snapshots.get());
            assertEquals(winnerState, f.db.storedState(), "loser/diagnosis must change no business row");
            assertEquals(java.util.List.of("permission-select", "callback-failure", "rollback", "after-completion-rollback",
                    "unbound-and-closed", "diagnostic-permission-select"), gates.events(1 - winner).subList(0, 6));
            verify(requests, times(2)).update(any(), any()); // each business command executed exactly once
            var finalRequest = f.requests.find(id).orElseThrow().request();
            assertEquals(2, finalRequest.version());
            String finalState = pair.equals("approve-return") ? winner == 0 ? "Approved" : "ChangesRequested"
                    : !pair.equals("edit-edit") && winner == 1 ? "PendingReview" : state;
            assertEquals(finalState, finalRequest.status().name());
            assertEquals(f.db.dual, finalRequest.requesterId());
            assertEquals(pair.equals("approve-return") || (!pair.equals("edit-edit") && winner == 1)
                    ? "title" : "edit-" + winner, finalRequest.title());
            boolean edited = pair.equals("edit-edit") || (!pair.equals("approve-return") && winner == 0);
            assertEquals(edited ? "d" : "description", finalRequest.changeDescription());
            assertEquals(edited ? "r" : "reason", finalRequest.reasonForChange());
            boolean returned = pair.equals("approve-return") && winner == 1;
            verify(reasons, times(returned ? 1 : 0)).append(any());
            assertEquals((state.equals("ChangesRequested") ? 1 : 0) + (returned ? 1 : 0),
                    f.db.jdbc.queryForObject("SELECT count(*) FROM return_reason", Integer.class));
            if (!returned) { assertEquals(initial.reasons(), f.db.storedState().reasons()); }
            else {
                var feedback = f.requests.find(id).orElseThrow().latestReturnReason();
                assertEquals(new ReturnReason(id, 2, f.db.reviewer, "revise"), feedback);
            }
        } finally { gates.releaseAll(); executor.shutdownNow(); }
    }

    @Test
    void busyTimeoutActuallyWaitsForWriterSlotWithoutAnOldReadSnapshot() throws Exception {
        var f = new WorkflowTestSupport();
        try (Connection holder = f.db.dataSource.getConnection(); var hold = holder.createStatement()) {
            hold.execute("BEGIN IMMEDIATE");
            var entered = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var result = executor.submit(() -> {
                    try (Connection contender = f.db.dataSource.getConnection(); var statement = contender.createStatement()) {
                        assertEquals(100, SqliteTestSupport.pragma(contender, "busy_timeout"));
                        entered.countDown();
                        long start = System.nanoTime();
                        var failure = assertThrows(java.sql.SQLException.class, () -> statement.execute("BEGIN IMMEDIATE"));
                        assertEquals(5, failure.getErrorCode());
                        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    }
                });
                ControlledOperations.await(entered);
                long elapsed = result.get(10, TimeUnit.SECONDS);
                assertTrue(elapsed >= 75 && elapsed < 5000, "must wait near configured 100ms, not fail immediately: " + elapsed);
            } finally { hold.execute("ROLLBACK"); executor.shutdownNow(); }
        }
    }

    @Test
    void unrelatedCommitIsUnavailableNotFalseVersionConflictAndDoesNotReplay() throws Exception {
        var f = new WorkflowTestSupport();
        var id = f.db.request(f.db.owner, "Draft", 0);
        var requests = spy(f.requests);
        doAnswer(call -> {
            f.db.externalUpdate("UPDATE app_user SET enabled=0 WHERE id=?", f.db.other.toString());
            return call.callRealMethod();
        }).when(requests).update(any(), any());
        var failure = assertThrows(RequestFailure.class, () -> f.service(f.identities, requests, f.reasons).mutate(f.db.owner, id,
                ChangeRequestService.Action.EDIT, WorkflowTestSupport::complete, () -> null, () -> WorkflowTestSupport.version(id, 0)));
        assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, failure.kind());
        assertEquals(0, f.requests.find(id).orElseThrow().request().version());
        verify(requests).update(any(), any());
    }
}
