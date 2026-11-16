# SC-005 scope review and unresolved scoring decision

Reviewed 2026-11-16. This review changes neither the approved specification nor the frozen revision-1 prompts/key or 7/7 rubric. No participant has been evaluated.

SC-005 requires two real role representatives to perform submission, revision, approval, denial and conflict scenarios; each must correctly identify **every** role-relevant resulting state and denial/conflict reason from returned outcomes, without internal diagnostics (100% per participant). It does not specify seven questions. Seven prompts per role are an implementation of the worksheet, not permission to omit subcases: every required element in a prompt must be correct.

| Frozen prompt | Approved source / coverage |
| --- | --- |
| R1 | FR-004/005/017: incomplete Draft, failed submission and unchanged state |
| R2 | FR-005/015: submission and PendingReview edit restriction |
| R3 | FR-004/005/016/017/019: feedback, revision, incomplete save and resubmission |
| R4 | FR-012/014: ownership/read concealment |
| R5 | FR-011/013: stale content conflict, preserved accepted version |
| R6 | FR-008/015: Approved read/immutability |
| R7 | FR-009 and additional AC13/14: effective revocation versus already authorized operation |
| V1 | FR-002/014: role union and state-based visibility |
| V2 | FR-006/008/013: approval, resulting state and repeated original intent |
| V3 | FR-006/012/016: missing/valid return reason, returned state and lost read access |
| V4 | FR-005/011/013/019: revised submission and old-cycle decision rejection |
| V5 | FR-002/007: both-role ownership never permits self-review |
| V6 | FR-009 and additional AC13/14: revocation and authorization timing |
| V7 | Plan/API failure policy and T025/T049: unknown commit acknowledgement / safe 503; not explicitly one of SC-005's workflow state/denial/conflict scenarios |

**Decision needed before formal scoring:** confirm whether V7's infrastructure-failure recovery is required within SC-005's current seven-prompt Reviewer score, or is a separate operational exercise. The frozen worksheet currently requires it; this preparation has NOT removed it, marked it optional, reduced the score to 6/6, substituted a different question or declared it owner-approved. Also confirm that V6 is scored on observable policy outcomes; its key's post-rollback diagnostic implementation detail must not become a requirement for participants to read internals, contrary to SC-005. No independent new product behavior is needed to execute the scenarios.

Until this decision is recorded by the owner, the current seven-prompt material remains preserved and rehearsable, but a final SC-005 pass must not be asserted. The controlled V7 demonstration has been prepared so lack of tooling does not force a silent criterion change.

R7/V6 timing contrasts are approved business policy, not newly invented requirements. Their controlled observations come from the same completed first-permission-SELECT/read pattern already tested by PermissionRevocationTests. V7 uses the same after-commit/lost-acknowledgement seam as TransactionCleanupTests. Only test-classpath harnesses contain these seams; ordinary server operation has no delay/fault/provisioning endpoint.
