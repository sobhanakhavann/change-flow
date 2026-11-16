# SC-005 scope review and owner-approved decision

Decision recorded 2026-11-16, owner-approved revision 2, before any human evaluation. Requester R1-R7 requires 7/7; Reviewer V1-V6 requires 6/6; all scored elements must be correct independently (100%), with no compensating average. V7 is supplemental/non-scored. V6 tests recognition of returned results and observable revocation effects, not internal transactions. No participant has been evaluated.

SC-005 requires two real role representatives to perform submission, revision, approval, denial and conflict scenarios; each must correctly identify **every scored** resulting state and denial/conflict reason from returned outcomes without internal diagnostics (100% per participant). Revision 2 explicitly fixes seven Requester and six Reviewer scored prompts; every required subcase/element must be correct. This preserves correctness and prevents an infrastructure demonstration from extending human role-competence scoring without authorization.

| Revision-2 prompt | Approved source / coverage |
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
| V6 | FR-009 and additional AC13/14: observable denial/revocation effects and already-authorized-operation contrast; internal mechanism explanation is not scored |
| V7 (supplemental, non-scored) | Plan/API failure policy and T025/T049: unknown commit acknowledgement / safe 503; facilitator demonstration outside SC-005 score |

**Owner decision:** V7 is a supplemental facilitator demonstration excluded from SC-005 scoring. V6 judges only identification of the returned result and observable permission-revocation effects; transaction implementation knowledge is not required. Requester must fully answer R1-R7 and Reviewer V1-V6; no averaging, substitution or supplemental points are permitted. No scoring-scope decision remains pending.

**Reason and effect:** separate an operational uncertainty demonstration from role-relevant workflow/denial/conflict interpretation, and align V6 with assessment from returned outcomes rather than internals. The Reviewer denominator changes from seven to six; Requester remains seven; both still require 100% of all scored elements. This does not change product behavior, automated integrity/security requirements, tooling or TLS/physical-media limits. T048 remains incomplete and SC-005 remains Unassessed until real independent execution.

**History:** revision 1 (2026-11-15; preparation reviewed 2026-11-16) required 7/7 for both roles, scored V7 and included a V6 post-rollback-diagnostic explanation. The owner later approved this explicit correction before any human evaluation, so no result was regraded. Exact pre-decision documents, including the unresolved review, key and blank sheets, are archived in [history/revision-1/](history/revision-1/). Current scenarios, key and response sheets are fixed at revision 2 before use.

R7/V6 timing contrasts are approved business policy, not newly invented requirements. Their controlled observations come from the same completed first-permission-SELECT/read pattern already tested by PermissionRevocationTests. V7 uses the same after-commit/lost-acknowledgement seam as TransactionCleanupTests. Only test-classpath harnesses contain these seams; ordinary server operation has no delay/fault/provisioning endpoint.
