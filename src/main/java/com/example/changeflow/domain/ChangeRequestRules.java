package com.example.changeflow.domain;

import static com.example.changeflow.domain.ChangeRequest.Status.*;
import static com.example.changeflow.domain.RequestFailure.Kind.INVALID_STATE;

/** Pure changes, with ownership immutable. Access decisions are a separate policy. */
public final class ChangeRequestRules {
    public record Content(String title, String changeDescription, String reasonForChange) { }
    private ChangeRequestRules() { }

    public static ChangeRequest edit(ChangeRequest current, Content content) {
        if (current.status() != Draft && current.status() != ChangesRequested) { throw new RequestFailure(INVALID_STATE); }
        return new ChangeRequest(current.id(), current.requesterId(), content.title(), content.changeDescription(),
                content.reasonForChange(), current.status(), current.nextVersion());
    }

    public static ChangeRequest submit(ChangeRequest current) {
        if (current.status() != Draft && current.status() != ChangesRequested) { throw new RequestFailure(INVALID_STATE); }
        ContentRules.validateForSubmission(current.title(), current.changeDescription(), current.reasonForChange());
        return state(current, PendingReview);
    }

    public static ChangeRequest approve(ChangeRequest current) {
        if (current.status() != PendingReview) { throw new RequestFailure(INVALID_STATE); }
        return state(current, Approved);
    }

    public static ChangeRequest requestChanges(ChangeRequest current, String reason) {
        if (current.status() != PendingReview) { throw new RequestFailure(INVALID_STATE); }
        ContentRules.validateReturnReason(reason);
        return state(current, ChangesRequested);
    }

    private static ChangeRequest state(ChangeRequest current, ChangeRequest.Status status) {
        return new ChangeRequest(current.id(), current.requesterId(), current.title(), current.changeDescription(),
                current.reasonForChange(), status, current.nextVersion());
    }
}
