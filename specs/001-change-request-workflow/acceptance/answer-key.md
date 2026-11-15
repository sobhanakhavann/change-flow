# Frozen facilitator answer key

Frozen: 2026-11-15, revision 1. Keep separate from participant prompts until both independent submissions are recorded.

| Prompt | Required correct elements |
| --- | --- |
| R1 | Draft accepts incomplete saved content; Submit rejects blank title, description or reason; state unchanged. |
| R2 | PendingReview; editing prohibited. |
| R3 | ChangesRequested; owner sees latest reason; only owner with current Requester may edit; incomplete saves allowed, complete nonblank content needed to resubmit to PendingReview. |
| R4 | Generic 404 identical to missing; existence/state/version concealed. |
| R5 | Readable stale version returns 412; first committed edit survives, no silent overwrite. |
| R6 | Approved is readable by current Requester owner but immutable; edit returns 409 with current tag. |
| R7 | Current permissions checked each operation; revocation effective before check rejects, already authorized operation may finish subject to state/version integrity. |
| V1 | Reviewer reads PendingReview and Approved; Draft/ChangesRequested private unless current Requester owns them. |
| V2 | Approved; repeated original command returns 412 for readable resource, no further mutation. |
| V3 | Missing/blank reason 400 and unchanged PendingReview; valid reason produces ChangesRequested; Reviewer without owner Requester access subsequently sees generic 404. |
| V4 | PendingReview after resubmit; old-cycle tag returns 412, no new decision or feedback insert. |
| V5 | Both forbidden with 403; dual roles never permit self-review. |
| V6 | Fresh permission check denies next decision; already checked authorization may finish, never reused for subsequent operation or fresh post-rollback diagnosis. |
| V7 | Safe 503 may mean unknown whole committed/rolled-back outcome; authorized GET inspects state, no automatic fresh-tag replay. |

Score each prompt pass only when all elements are correct. Requester must pass 7/7; Reviewer must pass 7/7. SC-005 passes only when both real participants achieve 100%.
