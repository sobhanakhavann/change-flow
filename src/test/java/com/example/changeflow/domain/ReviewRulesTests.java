package com.example.changeflow.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewRulesTests {
    @Test
    void onlyPendingCanBeDecidedAndFeedbackIsRequired() {
        for (var state : ChangeRequest.Status.values()) {
            var request = new ChangeRequest(UUID.randomUUID(), UUID.randomUUID(), "t", "d", "r", state, 3);
            if (state == ChangeRequest.Status.PendingReview) {
                assertEquals(ChangeRequest.Status.Approved, ChangeRequestRules.approve(request).status());
                assertEquals(ChangeRequest.Status.ChangesRequested, ChangeRequestRules.requestChanges(request, "revise").status());
                assertThrows(IllegalArgumentException.class, () -> ChangeRequestRules.requestChanges(request, "\u00a0"));
            } else {
                assertThrows(RequestFailure.class, () -> ChangeRequestRules.approve(request));
                assertThrows(RequestFailure.class, () -> ChangeRequestRules.requestChanges(request, "revise"));
            }
        }
    }
}
