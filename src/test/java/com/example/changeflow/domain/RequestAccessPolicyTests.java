package com.example.changeflow.domain;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static com.example.changeflow.domain.ActorPermissions.Role.*;
import static com.example.changeflow.domain.ChangeRequest.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestAccessPolicyTests {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private final RequestAccessPolicy policy = new RequestAccessPolicy();

    static Stream<Arguments> accessMatrix() {
        return Stream.of(ChangeRequest.Status.values()).flatMap(status ->
                Stream.of(true, false).flatMap(owner -> Stream.of(true, false).flatMap(enabled ->
                        Stream.of(Set.<ActorPermissions.Role>of(), Set.of(Requester), Set.of(Reviewer),
                                Set.of(Requester, Reviewer))
                                .map(roles -> Arguments.of(status, owner, enabled, roles)))));
    }

    @ParameterizedTest
    @MethodSource("accessMatrix")
    void appliesTheApprovedRoleOwnershipAndStateMatrix(ChangeRequest.Status status,
            boolean owner, boolean enabled, Set<ActorPermissions.Role> roles) {
        var actor = new ActorPermissions(owner ? OWNER : OTHER, enabled, roles);
        var request = new ChangeRequest(UUID.randomUUID(), OWNER, "t", "d", "r", status, 3);
        boolean read = enabled && ((roles.contains(Requester) && owner)
                || (roles.contains(Reviewer) && (status == PendingReview || status == Approved)));
        boolean prepare = enabled && owner && roles.contains(Requester)
                && (status == Draft || status == ChangesRequested);
        boolean review = enabled && !owner && roles.contains(Reviewer) && status == PendingReview;
        assertAll(
                () -> assertEquals(read, policy.canRead(actor, request)),
                () -> assertEquals(prepare, policy.canEdit(actor, request)),
                () -> assertEquals(prepare, policy.canSubmit(actor, request)),
                () -> assertEquals(review, policy.canApprove(actor, request)),
                () -> assertEquals(review, policy.canRequestChanges(actor, request)));
    }

    @Test
    void absentActorGrantsNothing() {
        var request = new ChangeRequest(UUID.randomUUID(), OWNER, "", "", "", Draft, 0);
        assertFalse(policy.canRead(null, request));
        assertFalse(policy.canEdit(null, request));
        assertFalse(policy.canSubmit(null, request));
        assertFalse(policy.canApprove(null, request));
        assertFalse(policy.canRequestChanges(null, request));
    }

    @Test
    void aNewPermissionSnapshotOverridesPreviouslyGrantedRolesWithoutPolicyCaching() {
        var request = new ChangeRequest(UUID.randomUUID(), OWNER, "t", "d", "r", PendingReview, 1);
        assertTrue(policy.canApprove(new ActorPermissions(OTHER, true, Set.of(Reviewer)), request));
        assertFalse(policy.canApprove(new ActorPermissions(OTHER, true, Set.of()), request));
        assertFalse(policy.canRead(new ActorPermissions(OTHER, false, Set.of(Reviewer)), request));
    }
}
