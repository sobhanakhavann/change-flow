package com.example.changeflow.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class DomainModelsTests {
    private final UUID id = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(value = ChangeRequest.Status.class, names = {"Draft", "ChangesRequested"})
    void incompleteSavedContentIsRepresentable(ChangeRequest.Status status) {
        var request = new ChangeRequest(id, owner, null, null, null, status, 0);
        assertEquals("", request.title());
        assertEquals("", request.changeDescription());
        assertEquals("", request.reasonForChange());
    }

    @Test
    void preservesExactContentAndOwnershipWithoutMutationWhenAdvancingVersion() {
        var request = new ChangeRequest(id, owner, " e\u0301 ", "d", "r", ChangeRequest.Status.Draft, 7);
        assertEquals(8, request.nextVersion());
        assertEquals(7, request.version());
        assertEquals(owner, request.requesterId());
        assertEquals(" e\u0301 ", request.title());
    }

    @Test
    void versionCannotBeNegativeOrOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeRequest(id, owner, "", "", "", ChangeRequest.Status.Draft, -1));
        var max = new ChangeRequest(id, owner, "", "", "", ChangeRequest.Status.Draft, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, max::nextVersion);
        var beforeMax = new ChangeRequest(id, owner, "", "", "", ChangeRequest.Status.Draft, Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, beforeMax.nextVersion());
    }

    @Test
    void rejectsMissingRequiredIdentityAndStatus() {
        assertThrows(NullPointerException.class,
                () -> new ChangeRequest(null, owner, "", "", "", ChangeRequest.Status.Draft, 0));
        assertThrows(NullPointerException.class,
                () -> new ChangeRequest(id, null, "", "", "", ChangeRequest.Status.Draft, 0));
        assertThrows(NullPointerException.class,
                () -> new ChangeRequest(id, owner, "", "", "", null, 0));
    }

    @Test
    void everySnapshotValidatesContentLengths() {
        for (var status : ChangeRequest.Status.values()) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ChangeRequest(id, owner, "x".repeat(201), "", "", status, 0));
        }
    }

    @Test
    void permissionRolesAreAnImmutableSnapshotAndCanContainBothRoles() {
        var supplied = new HashSet<>(Set.of(ActorPermissions.Role.Requester, ActorPermissions.Role.Reviewer));
        var actor = new ActorPermissions(owner, true, supplied);
        supplied.clear();
        assertEquals(2, actor.roles().size());
        assertThrows(UnsupportedOperationException.class, () -> actor.roles().clear());
        assertTrue(new ActorPermissions(owner, false, Set.of()).roles().isEmpty());
        assertThrows(NullPointerException.class, () -> new ActorPermissions(null, true, Set.of()));
        assertThrows(NullPointerException.class, () -> new ActorPermissions(owner, true, null));
    }

    @Test
    void returnReasonRequiresPositiveVersionIdentitiesAndValidNonblankContent() {
        var reason = new ReturnReason(id, 1, owner, " reason ");
        assertEquals(" reason ", reason.reason());
        assertEquals(Long.MAX_VALUE, new ReturnReason(id, Long.MAX_VALUE, owner, "r").requestVersion());
        for (long version : new long[]{0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new ReturnReason(id, version, owner, "r"));
        }
        assertThrows(NullPointerException.class, () -> new ReturnReason(null, 1, owner, "r"));
        assertThrows(NullPointerException.class, () -> new ReturnReason(id, 1, null, "r"));
        assertThrows(IllegalArgumentException.class, () -> new ReturnReason(id, 1, owner, "\u00a0"));
        assertThrows(IllegalArgumentException.class, () -> new ReturnReason(id, 1, owner, "x".repeat(2001)));
    }
}
