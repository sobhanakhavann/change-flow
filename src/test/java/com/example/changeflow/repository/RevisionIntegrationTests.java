package com.example.changeflow.repository;

import com.example.changeflow.domain.*;
import com.example.changeflow.support.WorkflowTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.changeflow.domain.ChangeRequestService.Action.*;

class RevisionIntegrationTests {
    @Test void multipleCyclesRetainFeedbackAndRevocationDoesNotEraseHistory() {
        var f = new WorkflowTestSupport();
        var service = f.service();
        var id = f.db.request(f.db.owner, "PendingReview", 1);
        for (int cycle = 0; cycle < 2; cycle++) {
            long v = 1 + cycle * 4;
            String reason = "revision " + cycle;
            service.mutate(f.db.reviewer, id, REQUEST_CHANGES, () -> null, () -> reason, () -> WorkflowTestSupport.version(id, v));
            assertEquals(reason, service.get(f.db.owner, id).latestReturnReason().reason());
            service.mutate(f.db.owner, id, EDIT, () -> new ChangeRequestRules.Content("", "", ""), () -> null, () -> WorkflowTestSupport.version(id, v + 1));
            assertThrows(IllegalArgumentException.class, () -> service.mutate(f.db.owner, id, SUBMIT, () -> null, () -> null, () -> WorkflowTestSupport.version(id, v + 2)));
            service.mutate(f.db.owner, id, EDIT, WorkflowTestSupport::complete, () -> null, () -> WorkflowTestSupport.version(id, v + 2));
            service.mutate(f.db.owner, id, SUBMIT, () -> null, () -> null, () -> WorkflowTestSupport.version(id, v + 3));
        }
        service.mutate(f.db.reviewer, id, APPROVE, () -> null, () -> null, () -> WorkflowTestSupport.version(id, 9));
        assertEquals(2, f.db.jdbc.queryForObject("SELECT count(*) FROM return_reason WHERE request_id=?", Integer.class, id.toString()));
        assertEquals("revision 1", service.get(f.db.reviewer, id).latestReturnReason().reason());
        f.db.externalUpdate("UPDATE app_user SET enabled=0 WHERE id=?", f.db.owner.toString());
        assertEquals(RequestFailure.Kind.REQUEST_NOT_FOUND, assertThrows(RequestFailure.class, () -> service.get(f.db.owner, id)).kind());
        assertEquals(2, f.db.jdbc.queryForObject("SELECT count(*) FROM return_reason", Integer.class));
    }
}
