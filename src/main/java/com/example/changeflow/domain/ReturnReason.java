package com.example.changeflow.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable feedback recorded at the resulting request version. */
public record ReturnReason(UUID requestId, long requestVersion, UUID reviewerId, String reason) {
    public ReturnReason {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(reviewerId, "reviewerId");
        if (requestVersion <= 0) {
            throw new IllegalArgumentException("requestVersion must be positive");
        }
        ContentRules.validateReturnReason(reason);
    }
}
