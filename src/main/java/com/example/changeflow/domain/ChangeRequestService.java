package com.example.changeflow.domain;

import com.example.changeflow.repository.ChangeRequestRepository;
import com.example.changeflow.repository.IdentityRepository;
import com.example.changeflow.repository.ReturnReasonRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.example.changeflow.domain.RequestFailure.Kind.*;
import static com.example.changeflow.domain.ActorPermissions.Role.*;

/** Orchestrates outside callbacks; never retries a business action or inherits an ambient transaction. */
@Service
public class ChangeRequestService {
    public enum Action { EDIT, SUBMIT, APPROVE, REQUEST_CHANGES }
    private final IdentityRepository identities;
    private final ChangeRequestRepository requests;
    private final ReturnReasonRepository reasons;
    private final TransactionTemplate transaction;
    private final RequestAccessPolicy access = new RequestAccessPolicy();

    public ChangeRequestService(IdentityRepository identities, ChangeRequestRepository requests,
            ReturnReasonRepository reasons, PlatformTransactionManager manager) {
        this.identities = identities; this.requests = requests; this.reasons = reasons;
        transaction = new TransactionTemplate(manager);
    }

    public RequestView get(UUID actor, UUID id) {
        noAmbient();
        return transaction.execute(status -> visible(current(actor), id));
    }

    public record Page(List<RequestView> items, UUID nextAfter) { }

    public Page list(UUID actor, Supplier<Integer> limit, Supplier<UUID> after) {
        noAmbient();
        return transaction.execute(status -> {
            ActorPermissions permissions = current(actor);
            int size = limit.get();
            if (size < 1 || size > 100) { throw new IllegalArgumentException("Invalid page size"); }
            var rows = requests.visible(permissions, size + 1, after.get());
            boolean more = rows.size() > size;
            var items = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
            return new Page(items, more ? items.getLast().request().id() : null);
        });
    }

    public RequestView create(UUID actor, Supplier<ChangeRequestRules.Content> payload) {
        noAmbient();
        return transaction.execute(status -> {
            var permissions = current(actor);
            if (!permissions.enabled() || !permissions.roles().contains(Requester)) { throw new RequestFailure(PERMISSION_DENIED); }
            var content = payload.get();
            var request = new ChangeRequest(UUID.randomUUID(), actor, content.title(), content.changeDescription(),
                    content.reasonForChange(), ChangeRequest.Status.Draft, 0);
            requests.insert(request);
            return new RequestView(request, null);
        });
    }

    public RequestView mutate(UUID actor, UUID id, Action action, Supplier<ChangeRequestRules.Content> content,
            Supplier<String> reason, Supplier<ExpectedVersion> precondition) {
        noAmbient();
        var expected = new AtomicReference<ExpectedVersion>();
        var completion = new AtomicReference<Integer>();
        try {
            return transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int result) { completion.set(result); }
                });
                ActorPermissions permissions = current(actor); // FIRST SELECT, including diagnostic path below
                RequestView view = visible(permissions, id);
                ChangeRequest before = view.request();
                requireActionPermission(permissions, before, action);
                ChangeRequestRules.Content edit = content.get();
                String feedback = action == Action.REQUEST_CHANGES ? reason.get() : null;
                if (edit != null) { ContentRules.validateForSave(edit.title(), edit.changeDescription(), edit.reasonForChange()); }
                if (feedback != null || action == Action.REQUEST_CHANGES) { ContentRules.validateReturnReason(feedback); }
                expected.set(precondition.get());
                if (!expected.get().id().equals(id) || expected.get().version() != before.version()) { throw new RequestFailure(VERSION_CONFLICT); }
                ChangeRequest after = switch (action) {
                    case EDIT -> ChangeRequestRules.edit(before, edit);
                    case SUBMIT -> ChangeRequestRules.submit(before);
                    case APPROVE -> ChangeRequestRules.approve(before);
                    case REQUEST_CHANGES -> ChangeRequestRules.requestChanges(before, feedback);
                };
                requests.update(before, after);
                ReturnReason latest = view.latestReturnReason();
                if (action == Action.REQUEST_CHANGES) {
                    latest = new ReturnReason(id, after.version(), actor, feedback);
                    reasons.append(latest);
                }
                return new RequestView(after, latest);
            });
        } catch (ChangeRequestRepository.SnapshotFailure failure) {
            return diagnose(actor, id, expected.get(), completion.get());
        } catch (RequestFailure failure) {
            if (failure.kind() == VERSION_CONFLICT) { return diagnose(actor, id, expected.get(), completion.get()); }
            throw failure;
        } catch (DataAccessException | TransactionException failure) {
            throw new RequestFailure(SERVICE_UNAVAILABLE);
        }
    }

    private RequestView diagnose(UUID actor, UUID id, ExpectedVersion expected, Integer completion) {
        noAmbient();
        if (completion == null || completion != TransactionSynchronization.STATUS_ROLLED_BACK || expected == null) {
            throw new RequestFailure(SERVICE_UNAVAILABLE);
        }
        // Only new SELECTs, after execute has completed rollback and unbound the failed connection.
        return transaction.execute(status -> {
            var now = visible(current(actor), id).request(); // current permission, not the failed callback's grant
            if (!expected.id().equals(id) || expected.version() != now.version()) { throw new RequestFailure(VERSION_CONFLICT); }
            throw new RequestFailure(SERVICE_UNAVAILABLE);
        });
    }

    private ActorPermissions current(UUID actor) {
        return identities.loadCurrentPermissions(actor).orElse(new ActorPermissions(actor, false, java.util.Set.of()));
    }

    private RequestView visible(ActorPermissions actor, UUID id) {
        var view = requests.find(id).orElseThrow(() -> new RequestFailure(REQUEST_NOT_FOUND));
        if (!access.canRead(actor, view.request())) { throw new RequestFailure(REQUEST_NOT_FOUND); }
        return view;
    }

    private void requireActionPermission(ActorPermissions actor, ChangeRequest request, Action action) {
        // Role/ownership errors precede state errors; state validity remains a pure domain rule.
        boolean allowed = switch (action) {
            case EDIT, SUBMIT -> access.canPrepare(actor, request);
            case APPROVE, REQUEST_CHANGES -> access.canReview(actor, request);
        };
        if (!allowed) { throw new RequestFailure(PERMISSION_DENIED); }
    }

    private static void noAmbient() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Unexpected ambient transaction"); }
    }
}
