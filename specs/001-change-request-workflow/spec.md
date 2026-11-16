# Feature Specification: ChangeFlow Initial Workflow

**Feature Branch**: No branch created
**Created**: 2026-11-15
**Status**: Implemented; see quickstart.md for actual automated validation evidence.
**Input**: Initial HTTP API for change request management with Requester and Reviewer roles.

## Clarifications

### Session 2026-11-15

The project owner supplied the following approved answers in two clarification rounds; no new interactive questions were asked.

- Q: What can a Requester read? → A: Only their own requests, in every state.
- Q: What can a Reviewer read, including dual-role users? → A: PendingReview and Approved requests; dual-role users receive the union of both roles' read access.
- Q: May PendingReview content be edited? → A: No.
- Q: Who may edit ChangesRequested? → A: Only its owner with current Requester permission.
- Q: May Draft requests be deleted in version one? → A: No deletion capability is offered.
- Q: Is a return reason required and who can see it? → A: RequestChanges requires a nonblank reason visible to the owner and authorized Reviewers, subject to request read access.
- Q: May a requester review their own request? → A: Neither Approve nor RequestChanges is permitted, even with both roles.
- Q: What happens to completed or stale commands? → A: Repeated completed commands are rejected without mutation; an old command after a return/resubmit cycle cannot become a fresh decision.
- Q: May incomplete content be saved? → A: Yes in Draft and ChangesRequested; Submit requires nonblank title, change description, and reason for change.
- Q: When does revocation take effect? → A: Revocation effective before the operation's authorization check causes rejection. An operation already checked and authorized may finish; each subsequent operation checks again.

Approved follow-up decisions (2026-11-15):

- Q: How is SC-003 assessed? → A: Controlled tests cover both orders of every defined competing-operation pair and check operation outcomes, final state, no silent overwrite, and no partial changes. The technical mechanism is selected in Plan.
- Q: What length limits apply? → A: Every save enforces title 200, change description 5000, reason for change 2000, and return reason 2000 characters maximum. Incomplete Draft/ChangesRequested saves remain allowed; submission and return nonblank rules remain mandatory.
- Q: What happens to earlier return reasons? → A: Preserve all previous reasons; the owner can see the latest, and Reviewers access reasons only under current request access. Full history display is not required in version one.
- Q: How are missing and inaccessible requests reported? → A: They have the same response, taking precedence over disclosure of state or conflict for that request.
- Q: How are other errors reported? → A: Input, identity, permission, and conflict outcomes remain distinguishable subject to request concealment. Exact response formats and HTTP codes are deferred to the API contract.
## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prepare and submit (Priority: P1)

As a Requester, I want to create and edit my draft and submit it for review.

**Why this priority**: Every change starts with a proposal.
**Independent Test**: Create, edit, and submit a draft without performing review.

**Acceptance Scenarios**:

1. **Given** a currently authorized Requester, **When** they create a request, **Then** it is Draft and owned by their verified identity.
2. **Given** an owned Draft, **When** its Requester edits title, description, or reason, **Then** content changes while ownership and status remain unchanged.
3. **Given** a valid owned Draft, **When** its authorized Requester submits it, **Then** it becomes PendingReview with accepted content preserved.
4. **Given** another user's Draft or an absent/invalid identity, **When** editing or submission is attempted, **Then** it is denied with no data changes.
5. **Given** missing or blank submission content, **When** Submit is attempted, **Then** validation fails with no data changes.

### User Story 2 - Review a proposal (Priority: P1)

As an authorized Reviewer, I want to approve pending requests or return them for revision.

**Why this priority**: This supplies the business decision with separation of duties.
**Independent Test**: Review prepared PendingReview requests with authorized, unauthorized, and dual-role identities.

**Acceptance Scenarios**:

1. **Given** PendingReview and an authorized Reviewer other than its requester, **When** Approve succeeds, **Then** status becomes Approved and content is preserved.
2. **Given** the same conditions, **When** RequestChanges succeeds, **Then** status becomes ChangesRequested; a nonblank return reason is saved atomically with the transition.
3. **Given** a requester who also has Reviewer permission, **When** they Approve or RequestChanges their own request, **Then** the review command is denied with no changes.
4. **Given** a caller without current review access, including revocation effective before its authorization check, **When** either review command is attempted, **Then** it is denied with no changes.
5. **Given** a state other than PendingReview, **When** review is attempted, **Then** no review transition occurs.
6. **Given** Approved, **When** any caller attempts editing or a status change, **Then** it is denied without changes.

### User Story 3 - Revise and resubmit (Priority: P2)

As a Requester, I want to correct a returned proposal and resubmit it.

**Why this priority**: This completes the required feedback loop.
**Independent Test**: Start with ChangesRequested, edit as its owner, and resubmit.

**Acceptance Scenarios**:

1. **Given** an owned ChangesRequested request, **When** its currently authorized Requester edits content, **Then** content is saved and status remains ChangesRequested.
2. **Given** valid revised content, **When** its currently authorized owner submits it, **Then** status becomes PendingReview with latest accepted content preserved.
3. **Given** another user's returned request, **When** editing or resubmission is attempted, **Then** it is denied with no changes.

### User Story 4 - Preserve integrity under contention (Priority: P1)

As either role, I want competing operations to preserve accepted content and valid decisions.

**Why this priority**: Silent data loss undermines the workflow.
**Independent Test**: Use controlled tests of both orders for edit/edit, Approve/RequestChanges, and edit/Submit (separately in Draft and ChangesRequested). For every order, inspect each operation outcome, final state/content, absence of silent overwrites, and absence of partial changes. The technical test mechanism is deferred to Plan.

**Acceptance Scenarios**:

1. **Given** two edits based on the same earlier content, **When** one succeeds first, **Then** the other cannot silently overwrite that newer change and reports conflict if incompatible.
2. **Given** concurrent Approve and RequestChanges on PendingReview, **When** both finish, **Then** at most one transition succeeds and the other cannot replace the decision.
3. **Given** editing racing with submission of Draft or ChangesRequested, **When** both finish, **Then** submitted content is a consistent accepted version and no stale edit silently replaces it.
4. **Given** failed authorization, validation, or conflict checks, **When** an operation returns, **Then** no partial content or status changes remain.


Additional acceptance scenarios for FR-014-017 and the clarified command rules:

1. **Given** a Requester-only user, **When** they read requests in each of the four states, **Then** only their own requests are visible; reading another user's request is denied without returning its content.
2. **Given** a Reviewer-only user, **When** they read requests, **Then** PendingReview and Approved are visible, while Draft and ChangesRequested are not.
3. **Given** a dual-role user, **When** they read requests, **Then** their own requests in all states and all PendingReview/Approved requests are visible; other users' Draft/ChangesRequested remain inaccessible.
4. **Given** absent/invalid identity or no current applicable role, **When** a read is attempted, **Then** no request content is disclosed.
5. **Given** PendingReview, **When** its owner or any Reviewer edits content, **Then** editing is denied without changes.
6. **Given** Draft, **When** deletion is attempted, **Then** no deletion occurs; version one offers no such capability.
7. **Given** Draft or ChangesRequested, **When** its owner with current Requester permission saves incomplete content, **Then** the save succeeds without a status change; Submit rejects that content until all required fields are nonblank.
8. **Given** ChangesRequested and its owner without current Requester permission, **When** they edit or resubmit it, **Then** the operation is denied without changes, even if they retain Reviewer permission.
9. **Given** PendingReview and an authorized non-owner Reviewer, **When** RequestChanges has no reason, an empty reason, or a whitespace-only reason, **Then** neither status nor reason changes.
10. **Given** a successful RequestChanges with a nonblank reason, **When** the authorized owner reads the returned request, **Then** the reason is visible. A Reviewer-only user cannot read ChangesRequested; after resubmission an authorized Reviewer can read the recorded reason.
11. **Given** a completed Submit, Approve, or RequestChanges, **When** the same command is repeated, **Then** it is rejected without changing any request data, including recorded feedback.
12. **Given** an old completed command followed by return and resubmission, **When** that old command is replayed, **Then** it is rejected rather than treated as a new transition or decision.
13. **Given** permission revocation effective before an operation's authorization check, **When** that operation is checked, **Then** it is denied without mutation or disclosure.
14. **Given** an operation already checked and authorized, **When** its permission is revoked before completion, **Then** it may finish if state and integrity checks pass; the next operation must check again and reject reliance on the revoked permission.

15. **Given** otherwise permitted saves in Draft and ChangesRequested, **When** title, description, and reason are each at their respective 200/5000/2000-character limits, **Then** length validation passes; at one character over any limit the entire save fails with no changes. Repeat this boundary check for the 2000-character return reason on RequestChanges.
16. **Given** an incomplete Draft or ChangesRequested request within length limits, **When** saved by its currently authorized owner, **Then** saving succeeds; Submit still fails until all three content fields are nonblank.
17. **Given** two or more return/resubmit cycles with different reasons, **When** later edits or decisions complete, **Then** all prior reasons remain retained and the owner can read the latest. Authorized Reviewers can read the latest recorded reason in PendingReview/Approved; losing read access prevents reading it. A full-history display is not required.
18. **Given** an authenticated caller and either a missing reference or an existing request outside their read access, **When** the same read or mutation is attempted, **Then** both return the same response with no mutation and no state/conflict disclosure, even when the private request is stale or in an invalid state.
19. **Given** a request the caller may read, **When** input validation, action permission, state, or conflict checks reject an operation, **Then** the applicable outcomes are distinguishable and data is unchanged. Invalid identity has its distinguishable outcome without disclosing request information.
20. **Given** both controlled orders of every competing pair defined in Story 4, **When** the operations execute, **Then** each outcome and final state/content satisfy the workflow, with zero silent overwrites and zero partial changes; any inaccessible request is concealed under FR-012 rather than exposing conflict details.
### Edge Cases

- Missing and inaccessible requests return the same response without mutation. Neither state nor conflict information may reveal a private request.
- Client-supplied requester identity or status cannot substitute ownership or bypass commands.
- Submit outside Draft/ChangesRequested and review outside PendingReview cannot add transitions.
- PendingReview editing, Approved mutation, self-review, and Draft deletion are forbidden.
- Missing or whitespace-only submission fields or return reason cause rejection without mutation.
- Lost success responses do not permit retries to mutate again; old commands remain stale across workflow cycles.
- Revocation timing follows the operation authorization check, not request arrival. Successful authorization does not bypass state or concurrency checks.
## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide change-request management through an HTTP API. No UI is required.
- **FR-002**: Requester and Reviewer roles MUST coexist on one identity without bypassing ownership or resource access.
- **FR-003**: Each request MUST have a stable reference, title, change description, reason for change, immutable requester identity, and exactly one of Draft, PendingReview, ChangesRequested, Approved.
- **FR-004**: A currently authorized Requester MUST be able to create their own Draft and edit their own Draft or ChangesRequested request, preserving ownership and status.
- **FR-005**: Submit MUST require ownership, current Requester permission, and nonblank title, description, and reason. It MUST move Draft or ChangesRequested to PendingReview.
- **FR-006**: Only a currently authorized Reviewer with access to the request MUST Approve or RequestChanges on PendingReview, producing Approved or ChangesRequested respectively.
- **FR-007**: Self-approval and self-RequestChanges MUST be denied even with both roles.
- **FR-008**: Approved requests MUST be immutable; reopening is outside scope.
- **FR-009**: Every mutation MUST verify identity, current permission, resource access, and current request state on the server. Revocation effective before an operation authorization check MUST cause rejection. An operation already checked and authorized MAY finish after revocation, provided state and integrity checks still pass. Every subsequent operation MUST check permissions again.
- **FR-010**: Only the transitions below MUST be allowed. Arbitrary status assignment MUST NOT bypass them.

| Current state | Action | Result | Actor |
| --- | --- | --- | --- |
| Draft | Submit | PendingReview | Owning Requester |
| ChangesRequested | Submit | PendingReview | Owning Requester |
| PendingReview | Approve | Approved | Authorized Reviewer other than requester |
| PendingReview | RequestChanges | ChangesRequested | Authorized Reviewer other than requester |

- **FR-011**: Concurrent or stale operations MUST NOT silently overwrite newer accepted content, decide on incompatible content, or create invalid states. Incompatible operations MUST report conflict only when the caller may access the request; FR-012 concealment takes precedence. Related changes MUST succeed or fail together.
- **FR-012**: Rejected operations MUST leave data unchanged. Missing requests and requests outside the caller's read access MUST return the same response and MUST NOT disclose state or conflict information. This concealment rule takes precedence over request-specific state/conflict errors. Other invalid-input, identity, permission, invalid-state, and conflict outcomes MUST be distinguishable without exposing private data or secrets. Exact response formats and HTTP status codes are deferred to the API contract.
- **FR-013**: Repeated completed commands MUST be rejected without mutation. A stale command from an earlier review/submission cycle MUST be rejected, even if the request returns to an otherwise eligible state; it MUST NOT become a fresh decision.
- **FR-014**: Read operations MUST verify identity and current read permission. Requester access is limited to owned requests in every state. Reviewer access covers all PendingReview and Approved requests. Dual-role access is the union. Neither role alone grants access outside these rules.


- **FR-015**: Editing PendingReview MUST be denied for every actor. Draft deletion MUST NOT be offered in version one.
- **FR-016**: RequestChanges MUST require a nonblank return reason, distinct from the reason for proposing the change. Missing, empty, or whitespace-only reasons MUST cause rejection without mutation. The reason and status transition MUST be accepted together. The reason MUST be readable by the owner and authorized Reviewers wherever they have request read access; it MUST NOT independently grant access to ChangesRequested.
- **FR-017**: Incomplete content MUST be saveable in Draft and ChangesRequested by the owner with current Requester permission. Submit MUST reject missing, empty, or whitespace-only required content without mutation.

- **FR-018**: Every save MUST reject content exceeding these maxima: title 200 characters, change description 5000, reason for change 2000, and return reason 2000. A value at its limit MUST pass length validation; a value one character over MUST fail without partial changes. Incomplete content in Draft/ChangesRequested remains allowed within limits. Submit still requires three nonblank content fields; RequestChanges still requires a nonblank return reason.
- **FR-019**: Every accepted return reason MUST be preserved across edits, resubmissions, and later decisions. The owner with current read access MUST be able to read the latest reason. Reviewer access to reasons MUST follow current request-read policy. Full history display is not required in version one; retention MUST NOT be omitted because history is not displayed.
**Acceptance coverage**: FR-001 bounds all stories; FR-002-005 map to Stories 1 and 3; FR-006-010 to Story 2 and the transition table; FR-011-013 to Story 4 and edge cases. FR-014-019 map to the additional access, validation, state, and revocation scenarios above.

### Key Entities *(include if feature involves data)*

- **User identity**: Verified actor with current Requester and/or Reviewer permissions.
- **Change request**: Proposal with reference, title, description, reason, immutable requester, and status.
- **Review decision**: Approval or request for revision; a nonblank return reason is required for RequestChanges and readable only with applicable request access. All return reasons are retained; the latest is visible to the owner. Full history display is not required in version one.
- **Current authorization**: Effective role and access to the particular request at operation time; mechanism deferred.

## Success Criteria *(mandatory)*

### Measurable Outcomes

These are acceptance targets, not executed tests.

- **SC-001**: 100% of valid-flow acceptance cases complete submission, revision/resubmission, and approval with accepted content preserved.
- **SC-002**: 100% of negative cases deny self-approval, unauthorized mutation, invalid transitions, and Approved edits with zero data changes.
- **SC-003**: Controlled tests MUST cover both orders of every competing-operation pair defined in Story 4: edit/edit, Approve/RequestChanges, and edit/Submit separately in Draft and ChangesRequested. Every case MUST verify each operation outcome, final state and content, zero silent overwrites, and zero partial changes. Accessible incompatible operations report conflict; FR-012 concealment takes precedence for inaccessible requests. The technical mechanism is chosen in Plan.
- **SC-004**: Every case where revocation is effective before the operation authorization check grants zero successful protected operations using that permission.

## Assumptions

- Scope is proposal management and decisions through HTTP. UI, execution of approved changes, AI, RAG, MCP, and multi-agent execution are excluded.
- Verified identity and current authorization information are dependencies. No database, authentication method, concurrency mechanism, endpoint design, or implementation architecture is selected.
- Constitutional engineering constraints remain applicable to later phases.
- Reviewer access is state-based across PendingReview and Approved, not assignment-based; Requester access remains ownership-based.
- Approved validation policy: incomplete Draft and ChangesRequested content may be saved; submission requires all three content fields to be nonblank. Every save enforces the maximum lengths in FR-018; incomplete content does not waive those limits.
- Ownership remains stable when roles change and does not replace current permissions.
- No performance/capacity commitment was supplied; none is invented.
- Plan, Tasks, implementation, role administration, and a general audit feature are outside this invocation. Return-reason retention is required; full history display is outside version one.

## Open Product Decisions

All previously recorded product questions and the SC-003 proposal are resolved by the approved Clarifications. No blocking product ambiguity remains for planning.

- Database, authentication, concurrency/test mechanisms are intentionally deferred to Plan. Exact HTTP codes and response formats belong in the API contract.
- The API contract must make character-counting semantics explicit for supplementary Unicode characters and combining sequences; this lower-impact representation detail does not change the approved numeric limits.
- No new performance or capacity targets have been introduced.
- Specification quality review does not establish implementation correctness or human acceptance. This section records the historical specification review; current execution evidence is in quickstart.md.
- Planning is the next eligible phase; no Plan, Tasks, or implementation is created by this clarification.

## Assignment scope revision — 2026-11-16

The owner removed the additional two-participant evaluation criterion (formerly SC-005). It is not a mandatory Session 1 assignment deliverable. This is a scope withdrawal, not a passed evaluation. Product rules and automated integrity/security requirements remain unchanged.
