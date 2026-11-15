package com.example.changeflow.repository;

import com.example.changeflow.domain.*;
import com.example.changeflow.support.WorkflowTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewIntegrationTests {
    @Test
    void abortedCommitAndLostAcknowledgementHaveWholeOutcomesAndNeverDiagnoseOrReplay() {
        for (boolean committed : new boolean[]{false, true}) {
            var f = new WorkflowTestSupport();
            var id = f.db.request(f.db.owner, "PendingReview", 1);
            var identities = spy(f.identities);
            var requests = spy(f.requests);
            var manager = new SqliteTransactionManager(f.db.dataSource) {
                @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                    if (committed) { super.doCommit(status); }
                    throw new org.springframework.transaction.TransactionSystemException("injected unknown acknowledgement");
                }
            };
            // Use production cleanup, with no test-only rollback policy override.
            var service = new ChangeRequestService(identities, requests, f.reasons, manager);
            var failure = assertThrows(RequestFailure.class, () -> service.mutate(f.db.reviewer, id,
                    ChangeRequestService.Action.REQUEST_CHANGES, () -> null, () -> "revise", () -> WorkflowTestSupport.version(id, 1)));
            assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, failure.kind());
            assertEquals(committed ? 2 : 1, f.requests.find(id).orElseThrow().request().version());
            assertEquals(committed ? 1 : 0, f.db.jdbc.queryForObject("SELECT count(*) FROM return_reason", Integer.class));
            verify(identities, times(1)).loadCurrentPermissions(any());
            verify(requests, times(1)).update(any(), any());
        }
    }

    @Test
    void injectedFullAndIoErrorsRollbackTheRealFileTransaction() {
        for (var code : new org.sqlite.SQLiteErrorCode[]{org.sqlite.SQLiteErrorCode.SQLITE_FULL, org.sqlite.SQLiteErrorCode.SQLITE_IOERR}) {
            var f = new WorkflowTestSupport();
            var id = f.db.request(f.db.owner, "PendingReview", 0);
            var reasons = spy(f.reasons);
            doThrow(new DataAccessResourceFailureException("injected storage failure", new org.sqlite.SQLiteException("test-only", code)))
                    .when(reasons).append(any());
            assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, assertThrows(RequestFailure.class,
                    () -> f.service(f.identities, f.requests, reasons).mutate(f.db.reviewer, id,
                            ChangeRequestService.Action.REQUEST_CHANGES, () -> null, () -> "revise", () -> WorkflowTestSupport.version(id, 0))).kind());
            assertEquals(0, f.requests.find(id).orElseThrow().request().version());
            assertEquals(0, f.db.jdbc.queryForObject("SELECT count(*) FROM return_reason", Integer.class));
        }
    }

    @Test
    void realReadOnlyConnectionRejectsWritesWithoutChangingFile() throws Exception {
        var f = new WorkflowTestSupport();
        var id = f.db.request(f.db.owner, "PendingReview", 0);
        var config = new org.sqlite.SQLiteConfig();
        config.setReadOnly(true);
        var source = new org.sqlite.SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + f.db.file);
        var service = new ChangeRequestService(new IdentityRepository(source), new ChangeRequestRepository(source),
                new ReturnReasonRepository(source), new org.springframework.jdbc.support.JdbcTransactionManager(source));
        assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, assertThrows(RequestFailure.class,
                () -> service.mutate(f.db.reviewer, id, ChangeRequestService.Action.APPROVE, () -> null, () -> null,
                        () -> WorkflowTestSupport.version(id, 0))).kind());
        assertEquals(0, f.requests.find(id).orElseThrow().request().version());
    }
    @Test
    void failureAfterCasRollsBackRequestAndFeedbackTogether() {
        var f = new WorkflowTestSupport();
        var reasons = spy(f.reasons);
        doThrow(new DataAccessResourceFailureException("injected insert failure")).when(reasons).append(any());
        var id = f.db.request(f.db.owner, "PendingReview", 1);
        var failure = assertThrows(RequestFailure.class, () -> f.service(f.identities, f.requests, reasons)
                .mutate(f.db.reviewer, id, ChangeRequestService.Action.REQUEST_CHANGES, () -> null, () -> "revise",
                        () -> WorkflowTestSupport.version(id, 1)));
        assertEquals(RequestFailure.Kind.SERVICE_UNAVAILABLE, failure.kind());
        assertEquals(1, f.requests.find(id).orElseThrow().request().version());
        assertEquals(ChangeRequest.Status.PendingReview, f.requests.find(id).orElseThrow().request().status());
        assertEquals(0, f.db.jdbc.queryForObject("SELECT count(*) FROM return_reason", Integer.class));
    }
}
