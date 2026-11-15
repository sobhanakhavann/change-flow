package com.example.changeflow.domain;

import static com.example.changeflow.domain.ActorPermissions.Role.*;
import static com.example.changeflow.domain.ChangeRequest.Status.*;

/** Pure policy evaluated against the caller's current permission and request snapshots. */
public final class RequestAccessPolicy {
    public boolean canRead(ActorPermissions actor, ChangeRequest request) {
        return active(actor) && ((actor.roles().contains(Requester) && owns(actor, request))
                || (actor.roles().contains(Reviewer)
                    && (request.status() == PendingReview || request.status() == Approved)));
    }

    public boolean canEdit(ActorPermissions actor, ChangeRequest request) {
        return canPrepare(actor, request)
                && (request.status() == Draft || request.status() == ChangesRequested);
    }

    /** Identity/role part only: transport precedence checks state after payload/version. */
    public boolean canPrepare(ActorPermissions actor, ChangeRequest request) {
        return active(actor) && actor.roles().contains(Requester) && owns(actor, request);
    }

    public boolean canReview(ActorPermissions actor, ChangeRequest request) {
        return active(actor) && actor.roles().contains(Reviewer) && !owns(actor, request);
    }

    public boolean canSubmit(ActorPermissions actor, ChangeRequest request) {
        return canEdit(actor, request);
    }

    public boolean canApprove(ActorPermissions actor, ChangeRequest request) {
        return canReview(actor, request)
                && request.status() == PendingReview;
    }

    public boolean canRequestChanges(ActorPermissions actor, ChangeRequest request) {
        return canApprove(actor, request);
    }

    private static boolean active(ActorPermissions actor) {
        return actor != null && actor.enabled();
    }

    private static boolean owns(ActorPermissions actor, ChangeRequest request) {
        return actor.userId().equals(request.requesterId());
    }
}
