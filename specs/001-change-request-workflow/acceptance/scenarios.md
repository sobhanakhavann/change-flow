# Frozen human evaluation scenarios

Execution companion: [participant-guide.md](participant-guide.md) supplies actual contract HTTP commands; [response-sheet.md](response-sheet.md) is blank. Owner-approved revision 2 is recorded in [scope-review.md](scope-review.md). No automatic test or preparation is a human result.

Frozen before execution: 2026-11-16, revision 2, following the explicit owner decision. Revision 1 (2026-11-15) is preserved in history/revision-1/; no evaluation occurred under it. Do not change prompts/key during an evaluation. Use a private evaluation database and two real participants: one Requester representative and one Reviewer representative. Neither participant sees the answer key before submitting independent answers. The facilitator records actual HTTP responses; participants name resulting states and explain scored rejection/conflict outcomes. Never use production credentials or data.

## Requester prompts

R1. Save a proposal with missing content. Identify its state and whether submission can succeed.

R2. Complete all three content fields and submit. Identify the resulting state and whether content can now be edited.

R3. A reviewer returns the proposal with feedback. Identify the state, who can see the latest feedback, and who may revise it. Save incomplete content, then complete it and resubmit.

R4. Try to read another requester's private Draft. Explain the outcome and whether it reveals existence.

R5. Two edits use the same original ETag; the first commits. Explain the second response and the content that survives.

R6. Read your Approved proposal and try to edit it. Identify the state and rejection reason.

R7. Your Requester permission is revoked before the operation permission check. Explain why your next operation cannot proceed; contrast with an operation already authorized before revocation.

## Reviewer scored prompts: V1-V6

V1. Identify which states appear in your review queue and which remain private.

V2. Approve another owner's PendingReview proposal. Identify its new state and explain a repeated command using the original ETag.

V3. Return another PendingReview proposal first without a reason, then with a nonblank reason. Identify each result and whether you can read it immediately after return.

V4. The owner revises and resubmits. Identify its state. Try the old decision ETag from the earlier review cycle and explain the outcome.

V5. With both roles, try Approve and RequestChanges on your own PendingReview proposal. Explain both results.

V6. Your Reviewer permission is revoked before the operation check. Identify the returned result and observable effect on your next decision. Contrast the observed result for an already-authorized operation and a later operation. No internal transaction, SQL or diagnostic-mechanism explanation is required.

## Supplemental facilitator demonstration - not scored

V7. A storage failure makes commit acknowledgement uncertain. The facilitator demonstrates the actual response and later authorized inspection without automatically replaying the command. Discussion is supplemental: participation, correctness or omission cannot change or compensate for either SC-005 score.

## Execution protocol

The facilitator prepares original tags, roles and seeded states before each independent scenario. Capture participant identity/role, date, service build, material revision, response/state observations, verbatim answers and rubric score. Each scored prompt passes only when all its required state/reason elements are correct. Requester must pass R1-R7 (7/7), Reviewer must pass V1-V6 (6/6), independently 100%; no aggregate compensates for a wrong answer. V7 is recorded separately if demonstrated, without a score. If real execution is unavailable, leave results Unassessed. Automated observations never supply participant answers.
