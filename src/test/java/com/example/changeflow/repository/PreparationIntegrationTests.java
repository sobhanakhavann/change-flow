package com.example.changeflow.repository;

import com.example.changeflow.domain.*;
import com.example.changeflow.support.WorkflowTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationIntegrationTests {
    @Test
    void jdbcPermissionsAndProposalWritesUseTheRealTransactionConnection() {
        var f = new WorkflowTestSupport();
        var service = f.service();
        var view = service.create(f.db.owner, WorkflowTestSupport::complete);
        var id = view.request().id();
        assertEquals(f.db.owner, service.get(f.db.owner, id).request().requesterId());
        service.mutate(f.db.owner, id, ChangeRequestService.Action.EDIT,
                () -> new ChangeRequestRules.Content("updated", "d", "r"), () -> null, () -> WorkflowTestSupport.version(id, 0));
        assertEquals(1L, f.requests.find(id).orElseThrow().request().version());
        assertThrows(RequestFailure.class, () -> service.get(f.db.other, id));
        assertEquals("updated", f.requests.find(id).orElseThrow().request().title());
    }
}
