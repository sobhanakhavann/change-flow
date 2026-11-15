# ChangeFlow HTTP API Contract v1

Normative design for later implementation. Base path /api/v1; UTF-8 JSON; authentication via HTTP Basic over TLS. No cookies or sessions. Unsafe requests require X-ChangeFlow-Client: api; reject absent/wrong value with 403 CLIENT_HEADER_REQUIRED. No cross-origin access enabled. Never transmit production credentials over plain HTTP.

## Resources and operations

| Method/path | Input | Success | Permission |
| --- | --- | --- | --- |
| POST /api/v1/requests | Content object | 201, request representation; Location and ETag | Current Requester; server binds owner; Draft/version 0 |
| GET /api/v1/requests/{id} | UUID | 200 representation and ETag | Union read policy |
| GET /api/v1/requests | Optional limit and after | 200 page | Only currently visible rows |
| PUT /api/v1/requests/{id} | Replacement content, If-Match | 200 representation and new ETag | Owner/current Requester, Draft or ChangesRequested |
| POST /api/v1/requests/{id}/submit | Empty JSON object, If-Match | 204, empty body | Owner/current Requester; Draft or ChangesRequested; complete content |
| POST /api/v1/requests/{id}/approve | Empty JSON object, If-Match | 204, empty body | Current Reviewer, non-owner, PendingReview |
| POST /api/v1/requests/{id}/request-changes | Return object, If-Match | 204, empty body | Current Reviewer, non-owner, PendingReview |

No DELETE, ownership update, arbitrary status update, login, account/role-management or history endpoints. Successful commands return no resource representation: RequestChanges can remove the Reviewer's read access immediately. Clients may GET again only under the new state's read policy. Creation/edit representation includes latest reason if present and readable.

Content object: title (max 200), changeDescription (max 5000), reasonForChange (max 2000), each string or null/omitted (treated as empty). PUT replaces all three content fields; it is not a merge. Unknown properties (including id, requesterId, status, version, roles) rejected as INVALID_INPUT. Arrays/scalars/wrong field types rejected.

Return object: exactly reason, a required nonblank string max 2000. Empty command object allows no properties. Request bodies are parsed after authentication; for UUID resource routes perform read-access concealment before reporting payload field validation errors, version errors or state errors. Transport-invalid HTTP is handled generically without request-specific metadata.

Representation fields:

- id: UUID
- requesterId: immutable UUID
- title, changeDescription, reasonForChange: strings
- status: Draft | PendingReview | ChangesRequested | Approved
- version: decimal string of nonnegative BIGINT (avoids JSON-number precision loss)
- latestReturnReason: null, or { reason: string, reviewerId: UUID, requestVersion: decimal string }

Do not return password data, roles, all feedback history or internal diagnostics. Detail GET and list items use the same fields.

List: limit integer 1–100, default 50 (page size, not a capacity promise). after optional UUID cursor, order by id ascending; apply visibility filter before limit. Response { items: [representation], nextAfter: UUID or null }. No global totals. Paging is a live view, not a historical snapshot; clients tolerate changes between pages.

## Read/access policy

Current Requester sees owned requests in every state. Current Reviewer sees all PendingReview and Approved requests. Both roles grant the union. No roles/disabled user grants no resource access. Reading does not imply action permission: a Reviewer cannot edit, and an owner can never review their own proposal.

At each operation, query current roles/enabled status as the first SELECT in a new SQLite operation transaction. Revocation committed before this check causes rejection; permission already checked may finish, subject to state/version integrity. Every subsequent operation checks again.

## Version preconditions and replay

GET/create/edit ETag is a strong opaque quoted tag containing resource identity and monotonic version, e.g. "request-550e8400-e29b-41d4-a716-446655440000-v7". Every existing-resource mutation requires exactly one such If-Match. No weak tags, lists, or wildcard: malformed/unsupported tag 400 INVALID_PRECONDITION; absent tag 428 PRECONDITION_REQUIRED; nonmatching tag 412 VERSION_CONFLICT if the resource is readable.

Successful saves/transitions always increment version; versions never reset. The client must retain the tag captured when preparing a command and send that original tag on retry. Repeating a completed command is rejected, never returns prior success. An old command cannot apply after a return/resubmit cycle because its version no longer matches. A newly fetched tag used for a new command is a new intent, not a replay of the old command; clients must not silently refresh/retry.

RequestChanges has no success ETag/body. Reviewers may lose access; do not infer permission from a previous successful request. After CAS failure, recheck current resource visibility before conflict disclosure: 404 supersedes 412 for a request now hidden.

## Unicode and validation

Count Unicode scalar values in the exact supplied string, with no normalization or trimming. Reject ill-formed Unicode (unpaired UTF16 surrogates) and U+0000 as 400 INVALID_INPUT. Count supplementary characters once; combining marks separately: emoji U+1F600 counts 1, e plus U+0301 counts 2, precomposed U+00E9 counts 1. ZWJ and variation selectors each count as their own scalar; a displayed emoji sequence can count more than one.

Use Java 21 codePointCount after surrogate validation, not String.length or default @Size. SQLite length(text) must agree for validated text (U+0000 and malformed surrogates rejected). Apply limits on every create/edit/save and return decision. No truncation. Values at limit pass length validation; limit+1 fails atomically.

Nonblank means at least one scalar for which neither Character.isWhitespace nor Character.isSpaceChar (Java 21) is true. Empty/null/missing input fails nonblank validation where required. Draft/ChangesRequested content may be incomplete within maximums; Submit requires all three fields nonblank. RequestChanges requires nonblank reason. Failure changes neither request nor reason rows.

## Error contract and precedence

application/problem+json following RFC 9457:
{ type: "urn:changeflow:error:CODE", title: stable generic summary, status: integer, code: "CODE", detail: safe explanation, fieldErrors: optional [{ field, code }] }.
No stack traces, SQL, supplied secrets, private status, current private version, or echoed request body. Log correlation identifiers and generic failure codes, never Basic headers/passwords/content. Cache-Control: no-store on authenticated responses.

| HTTP | code | Condition |
| --- | --- | --- |
| 400 | INVALID_INPUT | Malformed payload, Unicode, overlength, blank required field, invalid UUID/query |
| 400 | INVALID_PRECONDITION | Unsupported If-Match form |
| 401 | UNAUTHENTICATED | Missing/invalid credentials, disabled at authentication; WWW-Authenticate: Basic realm="ChangeFlow" |
| 403 | CLIENT_HEADER_REQUIRED | Unsafe request missing required custom header |
| 403 | PERMISSION_DENIED | Readable resource but action disallowed, self-review, or no Requester on create |
| 404 | REQUEST_NOT_FOUND | Missing OR outside current read access |
| 409 | INVALID_STATE | Readable, permitted action incompatible with state |
| 412 | VERSION_CONFLICT | Readable resource with stale precondition |
| 428 | PRECONDITION_REQUIRED | Readable resource missing mutation precondition |
| 503 | SERVICE_UNAVAILABLE | Database failure; no success implied, secrets suppressed |
| 500 | INTERNAL_ERROR | Unexpected failure; no partial committed mutation |

Order: authenticate; unsafe-header guard; operation permission snapshot and resource load; missing/private concealment; action permission; body/field validation; If-Match syntax/presence/match; state/rules; atomic write. Malformed UUID or HTTP cannot query a resource and yields generic input error. For valid resource references missing/private always has identical 404 body, headers and code; no ETag, state or conflict details. A readable Approved request can produce 409 INVALID_STATE for attempted edit; a hidden request never reveals that it is Approved.

Infrastructure failure with an uncertain commit result must not be described as a rejected mutation known to have rolled back. Return 503 with safe detail indicating outcome uncertainty; the client may perform an authorized GET to inspect state but must not automatically refresh version and replay. The whole transaction is committed or rolled back, never partially committed.

General identity/input/permission/conflict outcomes remain distinguishable except where this explicitly approved concealment rule takes precedence. No constant-time/timing-indistinguishability guarantee is introduced.

Storage clarification (SQLite, 2026-11-15): bounded lock exhaustion uses the existing 503 SERVICE_UNAVAILABLE, not a new business error. After rolling back SQLITE_BUSY_SNAPSHOT, fresh read-only diagnosis yields concealed 404 if hidden/missing, 412 only for an accessible changed request version, otherwise generic 503. Diagnosis never retries the command or refreshes If-Match; unresolvable storage access returns metadata-free 503. Routes, payloads, success outcomes and version semantics are unchanged.

## Contract acceptance

Verify all paths, body shapes, response codes, ETags and field boundaries through actual HTTP tests; union read filters must apply to list and detail. Compare identical missing/private 404 responses, including hidden stale/invalid-state requests. No product acceptance claimed before execution.



