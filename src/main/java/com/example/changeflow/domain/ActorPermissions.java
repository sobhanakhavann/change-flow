package com.example.changeflow.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A defensive snapshot of effective permissions; contains no resource-access policy. */
public record ActorPermissions(UUID userId, boolean enabled, Set<Role> roles) {
    public enum Role { Requester, Reviewer }

    public ActorPermissions {
        Objects.requireNonNull(userId, "userId");
        roles = Set.copyOf(roles);
    }
}
