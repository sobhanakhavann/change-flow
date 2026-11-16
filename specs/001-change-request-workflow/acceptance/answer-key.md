# Frozen facilitator answer key

Frozen before execution: 2026-11-16, owner-approved revision 2. Keep separate from participant prompts until both independent submissions are recorded. The exact revision-1 key (7/7 for each role, scored V7 and internal-detail V6 wording) is preserved in history/revision-1/answer-key.md. The reason and impact of the approved scope correction are in scope-review.md.

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
| V6 | Correctly identify the returned denial/result and visible loss of decision access after revocation; an already-authorized operation may finish, while a subsequent operation reflects revoked access. Judge these observable outcomes only: no explanation of transactions, SQL, snapshot or post-rollback diagnostic implementation is required. |

Score a prompt pass only when all its required elements are correct. Requester must pass R1-R7 (7/7); Reviewer must pass V1-V6 (6/6). Both real participants must independently achieve 100%; no compensating average or V7 discussion can offset a wrong scored element. Until real execution, SC-005 remains Unassessed.

## V7 facilitator reference - supplemental, never scored

Safe 503 may mean an unknown whole committed/rolled-back outcome; an authorized GET inspects state without automatic fresh-tag replay. This reference guides the supplemental facilitator demonstration/discussion, not a seventh scored Reviewer answer. Do not assign points, require a participant answer for SC-005, or convert omission/incorrect discussion into an SC-005 failure.
