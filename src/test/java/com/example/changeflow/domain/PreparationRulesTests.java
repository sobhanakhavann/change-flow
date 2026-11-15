package com.example.changeflow.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationRulesTests {
    @Test
    void editsPreserveOwnershipStateAndAdvanceVersionWhileSubmitRequiresCompleteContent() {
        var request = new ChangeRequest(UUID.randomUUID(), UUID.randomUUID(), "", "", "", ChangeRequest.Status.Draft, 0);
        var incomplete = ChangeRequestRules.edit(request, new ChangeRequestRules.Content(null, null, null));
        assertEquals(request.requesterId(), incomplete.requesterId());
        assertEquals(ChangeRequest.Status.Draft, incomplete.status());
        assertEquals(1, incomplete.version());
        assertThrows(IllegalArgumentException.class, () -> ChangeRequestRules.submit(incomplete));
        var edited = ChangeRequestRules.edit(incomplete, new ChangeRequestRules.Content("t", "d", "r"));
        assertEquals(ChangeRequest.Status.PendingReview, ChangeRequestRules.submit(edited).status());
        assertEquals(0, request.version());
    }
}
