package com.example.changeflow.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable stored proposal snapshot. Workflow and access decisions belong to later rules. */
public record ChangeRequest(UUID id, UUID requesterId, String title, String changeDescription,
        String reasonForChange, Status status, long version) {
    public enum Status { Draft, PendingReview, ChangesRequested, Approved }

    public ChangeRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(status, "status");
        if (version < 0) {
            throw new IllegalArgumentException("version must be nonnegative");
        }
        title = title == null ? "" : title;
        changeDescription = changeDescription == null ? "" : changeDescription;
        reasonForChange = reasonForChange == null ? "" : reasonForChange;
        ContentRules.validateForSave(title, changeDescription, reasonForChange);
    }

    /** Calculates a subsequent version without changing this snapshot or its owner. */
    public long nextVersion() {
        if (version == Long.MAX_VALUE) {
            throw new IllegalStateException("Request version exhausted");
        }
        return version + 1;
    }
}
