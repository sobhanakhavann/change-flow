# Frozen human evaluation scenarios

Frozen: 2026-11-15, revision 1. Do not change prompts or answer key during an evaluation. Use a private evaluation database and two real participants: one Requester representative and one Reviewer representative. Neither participant sees the answer key before submitting independent answers. The facilitator records the actual HTTP responses; participants name the resulting state and explain each rejection/conflict. Never use production credentials or data.

## Requester prompts

R1. Save a proposal with missing content. Identify its state and whether submission can succeed.

R2. Complete all three content fields and submit. Identify the resulting state and whether content can now be edited.

R3. A reviewer returns the proposal with feedback. Identify the state, who can see the latest feedback, and who may revise it. Save incomplete content, then complete it and resubmit.

R4. Try to read another requester's private Draft. Explain the outcome and whether it reveals existence.

R5. Two edits use the same original ETag; the first commits. Explain the second response and the content that survives.

R6. Read your Approved proposal and try to edit it. Identify the state and rejection reason.

R7. Your Requester permission is revoked before the operation permission check. Explain why your next operation cannot proceed; contrast with an operation already authorized before revocation.

## Reviewer prompts

V1. Identify which states appear in your review queue and which remain private.

V2. Approve another owner's PendingReview proposal. Identify its new state and explain a repeated command using the original ETag.

V3. Return another PendingReview proposal first without a reason, then with a nonblank reason. Identify each result and whether you can read it immediately after return.

V4. The owner revises and resubmits. Identify its state. Try the old decision ETag from the earlier review cycle and explain the outcome.

V5. With both roles, try Approve and RequestChanges on your own PendingReview proposal. Explain both results.

V6. Your Reviewer permission is revoked before the operation check. Explain the next decision's failure and the boundary for an already authorized operation.

V7. A storage failure makes commit acknowledgement uncertain. Explain why success is not established and how to inspect the outcome without automatically replaying the command.

## Execution protocol

The facilitator prepares original tags, roles and seeded states before each independent scenario. Capture participant identity/role, date, service build, response/state observations, verbatim answers and rubric score. For each prompt all requested state and reason elements must be correct. Success requires both participants to score 100%; no partial-credit aggregate can compensate for a wrong answer. If unavailable, leave results Unassessed. Automated tests do not supply participant answers.
