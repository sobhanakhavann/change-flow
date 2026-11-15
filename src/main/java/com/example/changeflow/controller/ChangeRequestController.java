package com.example.changeflow.controller;

import com.example.changeflow.domain.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requests")
public class ChangeRequestController {
    private final ChangeRequestService service;
    private final RequestPayloads payloads;
    public ChangeRequestController(ChangeRequestService service, RequestPayloads payloads) {
        this.service = service; this.payloads = payloads;
    }

    public record Feedback(String reason, UUID reviewerId, String requestVersion) { }
    public record Representation(UUID id, UUID requesterId, String title, String changeDescription,
            String reasonForChange, ChangeRequest.Status status, String version, Feedback latestReturnReason) { }
    public record Page(List<Representation> items, UUID nextAfter) { }

    @PostMapping
    ResponseEntity<Representation> create(@AuthenticationPrincipal UUID actor, @RequestBody(required = false) byte[] body) {
        var view = service.create(actor, () -> payloads.content(body));
        return ResponseEntity.created(URI.create("/api/v1/requests/" + view.request().id()))
                .eTag(tag(view)).body(representation(view));
    }

    @GetMapping("/{id}")
    ResponseEntity<Representation> get(@AuthenticationPrincipal UUID actor, @PathVariable String id) {
        var view = service.get(actor, uuid(id));
        return ResponseEntity.ok().eTag(tag(view)).body(representation(view));
    }

    @GetMapping
    Page list(@AuthenticationPrincipal UUID actor, @RequestParam(required = false) String limit,
            @RequestParam(required = false) String after) {
        var page = service.list(actor, () -> limit == null ? 50 : Integer.parseInt(limit), () -> after == null ? null : uuid(after));
        return new Page(page.items().stream().map(ChangeRequestController::representation).toList(), page.nextAfter());
    }

    @PutMapping("/{id}")
    ResponseEntity<Representation> edit(@AuthenticationPrincipal UUID actor, @PathVariable String id,
            @RequestHeader HttpHeaders headers, @RequestBody(required = false) byte[] body) {
        var view = service.mutate(actor, uuid(id), ChangeRequestService.Action.EDIT, () -> payloads.content(body), () -> null,
                () -> VersionPrecondition.parseForVisibleResource(headers.get(HttpHeaders.IF_MATCH)));
        return ResponseEntity.ok().eTag(tag(view)).body(representation(view));
    }

    @PostMapping("/{id}/submit")
    ResponseEntity<Void> submit(@AuthenticationPrincipal UUID actor, @PathVariable String id,
            @RequestHeader HttpHeaders headers, @RequestBody(required = false) byte[] body) {
        return command(actor, id, headers, body, ChangeRequestService.Action.SUBMIT);
    }

    @PostMapping("/{id}/approve")
    ResponseEntity<Void> approve(@AuthenticationPrincipal UUID actor, @PathVariable String id,
            @RequestHeader HttpHeaders headers, @RequestBody(required = false) byte[] body) {
        return command(actor, id, headers, body, ChangeRequestService.Action.APPROVE);
    }

    @PostMapping("/{id}/request-changes")
    ResponseEntity<Void> requestChanges(@AuthenticationPrincipal UUID actor, @PathVariable String id,
            @RequestHeader HttpHeaders headers, @RequestBody(required = false) byte[] body) {
        return command(actor, id, headers, body, ChangeRequestService.Action.REQUEST_CHANGES);
    }

    private ResponseEntity<Void> command(UUID actor, String id, HttpHeaders headers, byte[] body, ChangeRequestService.Action action) {
        service.mutate(actor, uuid(id), action,
                () -> action == ChangeRequestService.Action.REQUEST_CHANGES ? null : payloads.emptyCommand(body),
                () -> payloads.reason(body), () -> VersionPrecondition.parseForVisibleResource(headers.get(HttpHeaders.IF_MATCH)));
        return ResponseEntity.noContent().build();
    }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) { throw new IllegalArgumentException("Invalid UUID"); }
        return id;
    }

    private static String tag(RequestView view) { return VersionPrecondition.etag(view.request().id(), view.request().version()); }

    private static Representation representation(RequestView view) {
        var request = view.request();
        var reason = view.latestReturnReason();
        return new Representation(request.id(), request.requesterId(), request.title(), request.changeDescription(),
                request.reasonForChange(), request.status(), Long.toString(request.version()), reason == null ? null
                    : new Feedback(reason.reason(), reason.reviewerId(), Long.toString(reason.requestVersion())));
    }
}
