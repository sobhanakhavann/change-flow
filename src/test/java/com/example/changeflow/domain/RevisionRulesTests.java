package com.example.changeflow.domain;

import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RevisionRulesTests {
    @Test void returnedEditsKeepOwnerAndStateButSubmissionRequiresCompleteContent() {
        var owner = UUID.randomUUID();
        var before = new ChangeRequest(UUID.randomUUID(), owner, "a", "b", "c", ChangeRequest.Status.ChangesRequested, 2);
        var after = ChangeRequestRules.edit(before, new ChangeRequestRules.Content("", "", ""));
        assertEquals(owner, after.requesterId());
        assertEquals(before.status(), after.status());
        assertEquals(3, after.version());
        assertThrows(IllegalArgumentException.class, () -> ChangeRequestRules.submit(after));
        var policy = new RequestAccessPolicy();
        assertTrue(policy.canEdit(new ActorPermissions(owner, true, Set.of(ActorPermissions.Role.Requester)), before));
        assertFalse(policy.canEdit(new ActorPermissions(owner, true, Set.of(ActorPermissions.Role.Reviewer)), before));
    }
}
