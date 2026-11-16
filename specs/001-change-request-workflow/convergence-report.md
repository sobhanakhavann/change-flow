# ChangeFlow convergence assessment

Human-rubric follow-up (2026-11-16): the owner approved revision 2, Requester R1-R7 (7/7) and Reviewer V1-V6 (6/6), every scored element correct independently, no averaging. V7 is supplemental/non-scored and V6 requires observable outcomes rather than internal implementation explanations. Earlier 7/7-for-both statements in the historical assessment below are superseded for future evaluation; exact revision-1 materials remain archived in acceptance/history/revision-1/. This documentation correction does not change automated evidence or record a human result: T048/SC-005 remains Unassessed.

**Current implementation follow-up (2026-11-15): F1-F4 have been remediated by T049-T052. The final Java 21 build passed with 184 tests in 25 suites, zero failures/errors/skips. T048/SC-005 remains Unassessed. The original assessment below is historical and retained; the implementation evidence is appended at the end.**

Date: 2026-11-15. Outcome: **tasks_appended**. Scope: feature `001-change-request-workflow` against the present code, approved specification/plan, referenced model/HTTP contract, all T001-T048 obligations and the constitution. Checkbox states were treated as claims, not proof. No Git comparison/history, application-code changes, commit, push, database migration or data deletion was performed during this assessment.

Four corrective tasks, T049-T052, were appended under Phase 8. Every original byte of tasks.md was verified preserved as a prefix. Existing checked tasks were not rewritten or unchecked. The previous statement that only T048 remains is qualified by the findings below. This report is saved separately at the user's explicit request; the specification, plan, contract, implementation, README, quickstart and acceptance records remain unchanged.

## Method and evidence

Reviewed 19 functional requirements, 5 success criteria, 38 numbered acceptance scenarios (5 US1, 6 US2, 3 US3, 4 US4 and 20 additional), 7 edge cases, all 48 existing tasks, 12 design decision groups and 7 constitutional principles. Decision groups: stack/dependencies; three layers; Basic/BCrypt/session policy; transport/header/CORS; current authorization; schema/migrations; persistent file/connection PRAGMAs; CAS/original ETags; transaction/failure diagnosis; feedback retention; Unicode/HTTP representation; controlled testing/delivery/human assessment.

The prerequisite script initially could not run under the shell's script policy. It subsequently succeeded through a child PowerShell process with `-NoProfile -ExecutionPolicy Bypass`; no global execution policy was changed. It identified this feature and research, model, contract, quickstart and tasks documents. `.specify/extensions.yml` was absent at the pre-check.

Inspected existing JUnit XML under `build/test-results/test`: **24 suites, 172 tests, zero failures/errors/skips**. The XML includes all eight named winner orders, the separate writer-slot wait, Boot context, real loopback HTTP, schema and persistence-restart tests. Boot and plain JAR artifacts exist under `build/libs`. Quickstart records the prior successful Java 21 offline Wrapper build (42 seconds), dependency inspection and native/WAL evidence. These artifacts substantiate the reported prior run, but do not imply untested branches passed. No fresh Gradle build or full test run was performed by Converge, and no human acceptance is inferred.

Resolved dependency source JARs in the available Gradle cache were inspected directly: Spring JDBC/transactions/web **7.0.10**, SQLite JDBC **3.53.4.0**, Jackson databind **3.1.7**. These sources, together with application code, support F1/F2; neither finding is represented as an executed end-to-end fault reproduction. Read-only JShell probe attempts were unsuccessful because of preferences/class-loading restrictions and supplied no passing behavioral evidence. Jackson 3.1.7 explicitly enables `FAIL_ON_TRAILING_TOKENS` by default; therefore accepting trailing JSON was investigated and **not** reported as a gap.

## Findings

| ID | Gap type | Severity | Source | Precise evidence | Task |
| --- | --- | --- | --- | --- | --- |
| F1 | contradicts | CRITICAL | Constitution 5/7; FR-011/012; plan transaction/failure policy; T020/T025/T033 | `src/main/java/com/example/changeflow/repository/DataSourceConfiguration.java:48-65` returns the raw connection; `domain/ChangeRequestService.java:30-33,75-109` relies on the injected manager; no production manager/cleanup customization exists. `repository/ReviewIntegrationTests.java:18-25` changes rollback-on-commit-failure only in its test manager. `concurrency/RequestConcurrencyTests.java:28-33` throws before any CAS write and only AFTER actual rollback. | T049 |
| F2 | contradicts | HIGH | HTTP contract UTF-8/Unicode; FR-012/017/018; plan HTTP/security; T008/T016/T021/T043 | `controller/ChangeRequestController.java:26-28,46-50,72-75` accepts `@RequestBody String`; `controller/RequestPayloads.java:24-30` parses that already-decoded String; `domain/ContentRules.java:28-47` cannot recover malformed original bytes. | T050 |
| F3 | partial | HIGH | FR-011/012/013/019; SC-002/003; US4; plan race/diagnostic regression; T031-T033/T036 | `concurrency/RequestConcurrencyTests.java:60-79,109-117` checks completion, different connection, state/version/title and feedback count, but not the full content/history or explicit close/unbind sequence. `support/ControlledOperations.java:7-20` only coordinates latches. `controller/StaleCommandTests.java:11-30` asserts response codes without complete stored-state/feedback comparisons; original Submit replay after a later cycle is absent. | T051 |
| F4 | partial | HIGH | FR-009/012/014; SC-004; additional AC8/13/14; plan revocation; T010/T034/T036/T043 | `concurrency/PermissionRevocationTests.java:11-17` tests only GET after account disabling; `:25-31` disables before the failing write, not after rollback; `:39-51` covers the already-authorized read. Real current-role revocation before each mutation and between rollback and diagnostic first SELECT is not demonstrated. | T052 |

Counts: missing 0, partial 2, contradicts 2, unrequested 0; CRITICAL 1, HIGH 3, MEDIUM 0, LOW 0. T048 is known unfinished external work already correctly tracked, not a duplicate new finding/task.

### F1: unresolved-transaction cleanup can commit pending work

Application orchestration correctly catches outside `TransactionTemplate.execute`, checks completion before diagnosis and never explicitly replays a command. That does not prevent unsafe lower-level connection cleanup.

In the resolved Spring source, `AbstractPlatformTransactionManager.java:142,812-828` defaults rollback-on-commit-failure to false and can leave a TransactionException outcome unknown. `JdbcTransactionManager.java:118-135,178-183` uses exception translation with fallback to TransactionSystemException. `DataSourceTransactionManager.java:376-402` then restores auto-commit before releasing the connection. Xerial `org/sqlite/SQLiteConnection.java:347-359` implements switching auto-commit on by issuing `commit;`.

Consequently, an unresolved original transaction can reach another native COMMIT through cleanup. More seriously, if a callback has performed the status CAS, feedback insertion fails, and rollback fails BEFORE actually rolling back, restoring auto-commit can commit only the pending status update. That violates the request-plus-feedback atomicity obligation; safe 503 text alone cannot make that partial outcome acceptable. This is a source-derived failure path, not a claim that a physical disk failure was reproduced.

The current rollback test cannot expose this path: it performs a successful real rollback before throwing its acknowledgement error, and its update spy never writes. The commit test's `setRollbackOnCommitFailure(true)` is not the production configuration and cannot establish production cleanup safety. Merely enabling that flag is also insufficient to prove safety when rollback itself fails.

T049 is complete only after the production configuration avoids committing pending work during unresolved cleanup, discards unsafe physical connections, preserves uncertain whole outcomes, and real-file regression tests cover the specified before/after commit/rollback failure positions without second COMMIT, business retry or diagnosis after uncertainty. Known aborts must leave both tables unchanged; lost acknowledgement may leave a whole committed result. This is the highest-priority delivery blocker.

### F2: strict UTF-8 is lost before domain validation

The resolved Spring `StringHttpMessageConverter.java:93-98` decodes raw request bytes with `new String(bytes, charset)`, which replaces malformed sequences rather than reporting them. The application has no strict byte-decoding boundary before that conversion. JSON parsing and scalar validation then receive valid replacement characters; the original malformed bytes cannot be recognized. Valid UTF-16 surrogate/NUL checks in ContentRules remain correct but do not cover this transport path.

For example, an invalid UTF-8 sequence C3 28 inside a JSON string can reach parsing as U+FFFD followed by `(`. The code must not solve this by globally rejecting U+FFFD: that scalar can be legitimate supplied content. T050 must establish raw-byte Boot HTTP regressions, exact preservation for valid Unicode and no mutations on invalid input, while keeping payload validation after current resource concealment. No numeric limits, blank semantics or product access rule needs to change.

### F3: passing race/replay tests cover less than their completion claim

All eight controlled independent-connection orders exist and passed. Winner release waits for the service Future, which means commit returned before loser release. Snapshot classification, a fresh diagnostic connection and prior rollback completion are meaningfully checked. The separate BEGIN IMMEDIATE test verifies a 100 ms busy timeout and a bounded 75-5000 ms native wait; it is not an immediate-upgrade failure masquerading as timeout evidence.

Remaining assertions are specific: all proposal fields/owner and exact feedback rows rather than only title/count; pre-existing feedback in ChangesRequested race fixtures; callback/rollback/unbind/close/diagnostic event ordering; unchanged stored snapshots after every replay, including Reviewer-only 404 and old Submit after a later cycle. Current code inspection supports CAS/replay correctness; this finding is incomplete required regression evidence, not an observed silent overwrite. T051 completion criteria are in tasks.md.

### F4: real-role revocation evidence is incomplete

IdentityRepository correctly reads current enabled/roles together without cached Security authorities, and services call it before resource reads. Existing tests demonstrate account disabling, an authorized read completing and fresh post-failure access rejection. They do not demonstrate the requested per-action revocation matrix with credentials still valid, removal of Requester while retaining Reviewer, or the exact after-rollback/before-diagnostic-SELECT window. An account disabled before Basic authentication would test a different boundary.

T052 must use committed role changes and controlled independent connections, test each protected mutation and subsequent list/detail access, compare unchanged request/reason data, and exercise the diagnostic window without sleeps or command replay. These are existing policy/Plan obligations, not new product behavior.

## Traceability and satisfied areas

| Intent / existing tasks | Observed implementation and evidence | Assessment |
| --- | --- | --- |
| T001-T003; stack/file setup | build.gradle, Wrapper properties, application.properties, DataSourceConfiguration and recorded toolchain/dependency evidence; Java 21/Boot/JDBC/Flyway/SQLite remain selected. | Present; cleanup issue isolated by F1. |
| T004-T008; FR-003/017/018 | V1-V3 STRICT schema; immutable records; version overflow rejection; ContentRules; ContentRulesTests and DomainModelsTests. Scalar lengths, supplementary/combining characters, blank union, NUL/unpaired surrogates and every-save bounds are covered. | Domain/schema work present; raw bytes remain F2. |
| T009-T012; FR-002/007/009/012/014 | RequestAccessPolicy, IdentityRepository, SecurityConfiguration, VersionPrecondition and central advice; 66-case policy suite, JDBC identity integration and security/version suites. | Current ownership, role union and self-review ban implemented; additional evidence F4. |
| T013-T014 | SqliteTestSupport creates unique real files with separate connections; SchemaIntegrationTests checks migration/WAL/PRAGMAs/constraints/checksum/unknown schema; PersistenceRestartTests reopens two complete contexts. | Real SQLite evidence exists, not H2/memory simulation. |
| T015-T022; US1; FR-004/005/010/015 | Preparation rules; request repository prepared insert/read/keyset/CAS; service; seven approved controller routes; preparation/API/storage tests. | Create/edit/submit implemented; incomplete saves and PendingReview edit denial present. |
| T023-T030; US2; FR-006/008/016 | Pending-only pure review rules, non-owner policy, same-transaction CAS plus ReturnReasonRepository.append; ReviewApiTests and ReviewIntegrationTests. | Core review implemented; aborted/uncertain failure safety needs F1. |
| T031-T036; US4; FR-011/013 | ControlledOperations, concurrency/revocation and stale-command suites; source orchestration outside callbacks with no retry. | Eight orders/timeout passed; F1/F3/F4 prevent a complete integrity-evidence claim. |
| T037-T042; US3; FR-019 | Shared returned edit/submit, greatest-version feedback projection, two-cycle retained reasons and current-access filtering. | No reason purge/history endpoint; current reviewer policy applies to latest reason. |
| T043-T044; error/layer review | Access/error matrix, deferred parsing, safe advice, pure models/rules/policy and service-to-repository dependencies. | Ordinary UUID missing/private 404 precedes action/body/tag/state; no owner/status injection or forbidden endpoint found. F2/F3/F4 qualify exhaustive coverage. |
| T045-T046; guide/build | README/Quickstart match paths, timeout, provisioning, ETag, backups, no sidecar cleanup, TLS boundary and uncertain outcomes. XML/JAR and prior build records substantiate execution. | Not an independently certified complete acceptance pass; no fresh build claimed here. |
| T047-T048; SC-005 | Frozen role prompts, separate key, results template with no answers; T048 unchecked. | Preparation exists; evaluation correctly Unassessed. No replacement task added. |

The fixed policy reads Requester-owned requests in all four states and Reviewer PendingReview/Approved; dual roles receive the union. Owner-only preparation requires current Requester, decisions require non-owner Reviewer, Approved is immutable, and server-generated ownership cannot be supplied in content. Version tags include UUID plus nonnegative long version; saves advance versions and original tags prevent old-cycle decisions. Current code and existing positive/negative tests support these areas. No extra UI, execution engine, history/delete/admin API or speculative dependency was found. The safe 405 mapping for unsupported HTTP methods is conventional transport handling, not a new business endpoint, and is not classified as unrequested work.

## Success criteria and delivery readiness

| Criterion | Present evidence | Current conclusion |
| --- | --- | --- |
| SC-001 | Real create/submit/return/edit/resubmit/approve HTTP plus multicycle JDBC tests. | Valid journeys covered; broader acceptance remains subject to corrections and review. |
| SC-002 | Policy/state/action/HTTP negative tests and selected unchanged-row assertions. | Partial evidence: F1/F2 and complete unchanged-state comparisons must be resolved before claiming 100%. |
| SC-003 | All eight controlled orders passed; timeout path separate. | Partial required evidence, F3; failure safety also depends on F1. |
| SC-004 | Fresh permission query implementation, disabling/read/failure-diagnosis cases and pure policy matrix. | Partial real-mutation/role-revocation evidence, F4. |
| SC-005 | Frozen scenarios/key, blank results and unchecked T048. | **Unassessed**, unchanged until two real independent participants each score 100%. |

This feature is **not ready for an unqualified delivery/acceptance claim**. Execute T049 first, then T050-T052, run the complete Java 21 build/tests, refresh actual evidence and run Converge again. Existing task markers are preserved for traceability; they do not override these findings. No new product decision is required by the identified remedies.

Production TLS/certificate validation and trusted-proxy/backend transport have not been tested: the real HTTP suite is loopback plaintext, and mocked secure flags are only branch tests. Remote deployment with Basic credentials therefore requires environment-specific TLS verification before exposure. This is an explicit operational gate, not a fabricated successful TLS test or a reason to mark T048 complete.

Physical disk exhaustion/damage, process-kill recovery, production ACL enforcement and backup/restore rehearsal remain unexecuted. Existing tests use actual read-only SQLite and injected FULL/IOERR/acknowledgement failures around real transactions; they cannot establish physical media durability. Approved requirements allow controlled fault injection, so no new physical-disk experiment task or capacity objective is invented. State these limits in handoff and validate the actual operational environment before production reliance.

Finally perform T048 with two real role representatives against the corrected build and record actual independent answers/scores. Automated success and documentation review do not replace this step. No participant answers, human pass or production readiness were simulated.

## Implementation follow-up: T049-T052 - 2026-11-15

### Working-tree review before edits

The initial Git diff showed existing changes to .gitignore, README, build.gradle, application.properties and ChangeFlowApplicationTests. The tracked application-context test had nine added lines connecting it to a unique real SQLite file through DynamicPropertySource. The workflow/concurrency/repository/support tests cited in this report were untracked, so Git had no earlier baseline for those files; their entire current content was read before editing. No claim was made that an untracked file was newly changed by someone else. Existing methods/fixtures were preserved and extended, rather than replaced wholesale. No dependency, migration script, operational database, existing application configuration or unrelated work was reset.

### Regression-first evidence

| Finding | Before correction | Correction and final evidence |
| --- | --- | --- |
| F1 | `test --tests '*TransactionCleanupTests'`: 4 tests, 4 failures with the actual Boot transaction-manager bean. Failure BEFORE real rollback committed version 1 instead of 0 after the post-CAS feedback failure; failure BEFORE real commit also left a changed row. Acknowledgement failures exposed autoCommit restoration. These were real-file behavioral regressions, not compilation failures. | Production SqliteTransactionManager unbinds and physically closes each independently owned unpooled connection without restoring autoCommit. Four before/after commit/rollback cases now pass, including complete unchanged-row/feedback checks, native SQLite commit/rollback hooks, physical closure, fresh subsequent connection, one permission lookup and no automatic replay/diagnosis. |
| F2 | `test --tests '*HttpWorkflowTests'`: 4 tests, 2 failures. Actual HTTP accepted malformed UTF-8 on create/edit instead of returning 400. Valid explicit U+FFFD and concealed-resource cases already passed. | Controllers retain byte[] until service visibility/action checks; RequestPayloads uses a UTF-8 decoder with REPORT before JSON parsing. Raw malformed/truncated/overlong/surrogate/out-of-range sequences, escaped lone surrogates/NUL, all-or-nothing storage, concealed 404, supplementary/combining values, explicit U+FFFD and the 200/201-scalar title boundary pass through real Boot HTTP. |
| F3 | Expanded assertions initially passed the existing production behavior: this finding was missing coverage, not an observed CAS defect. A temporary test-double mutation then changed every proposed description to `injected-regression`; all 8 controlled winner-order tests failed at the new full-content assertion. | Temporary mutation removed. Tests now compare full winning/final business snapshots, exact owner/content/status/version and complete retained/new feedback, seed old feedback for returned races, record rollback/completion/unbind/close/fresh-diagnostic order, and compare full stored snapshots after every immediate/old-cycle replay including concealed 404. No production rule change was necessary for F3. |
| F4 | New real-role tests initially passed. A temporary test double reused the old two-role grant after committed revocation; the new test failed because create incorrectly succeeded. The first draft of this negative control returned a wrong Optional type and failed with ClassCastException; that harness error was corrected and is not counted as a behavioral regression. | Old-grant injection removed. Real role revocation is checked for create, Draft/returned edit and Submit, Approve and RequestChanges with credentials still valid, current union list/detail, owner retaining Reviewer, authorized-read completion, and revocation committed after confirmed rollback/closure but before fresh diagnostic SELECT. Both controlled JDBC and HTTP cases verify concealed 404 and unchanged full business rows. No production policy change was needed. |

Negative controls for F3/F4 deliberately exercised the new assertions; they are not represented as defects reproduced in production code or as behavioral fixes. All temporary fault mutations were removed before the final build. User-requested failures before actual behavioral fixes were observed for F1 and F2; F3/F4 required additional evidence, not weakening or rewriting the approved policy.

### Transaction cleanup and pool boundary

DataSourceConfiguration now supplies the production SqliteTransactionManager bean; workflow and API fixtures call the same factory. ReviewIntegrationTests no longer use a test-only rollbackOnCommitFailure flag as a substitute for production cleanup. Successful and failed transactions keep Spring's completion/synchronization lifecycle; cleanup unbinds the holder and closes the owned physical connection instead of issuing setAutoCommit(true), which Xerial would translate to COMMIT. The before-rollback fault case proves pending CAS work does not survive without feedback. Native hooks verify zero commits in known-abort cases and one whole commit in the lost-acknowledgement case. Service orchestration remains outside callbacks, has no retries and diagnoses only after confirmed completion/cleanup.

The approved datasource is unpooled. Tests assert the connection being closed is an actual SQLiteConnection, not a pool lease, and a later checkout is a different physical connection. Although JDBC starters can include a pool library transitively, no pool is configured or instantiated for this datasource. Logical pool close/reset could itself restore autoCommit or return an unresolved connection to circulation; this manager intentionally does not claim compatibility with that behavior. Introducing a pool would require a separate reviewed eviction/reset design. No pool, queue, lock service or runtime dependency was added.

### Commands and final result

All Gradle executions used the existing Wrapper and session-only JAVA_HOME/GRADLE_USER_HOME/GRADLE_RO_DEP_CACHE settings recorded in quickstart.md. Actual commands included:

```powershell
.\gradlew.bat --offline --no-daemon test --tests '*TransactionCleanupTests'
.\gradlew.bat --offline --no-daemon test --tests '*TransactionCleanupTests' --tests '*ReviewIntegrationTests' --tests '*RequestConcurrencyTests'
.\gradlew.bat --offline --no-daemon test --tests '*HttpWorkflowTests'
.\gradlew.bat --offline --no-daemon test --tests '*RequestConcurrencyTests' --tests '*StaleCommandTests'
.\gradlew.bat --offline --no-daemon test --tests '*PermissionRevocationTests' --tests '*AccessAndErrorContractTests'
.\gradlew.bat --offline --no-daemon build
```

The first F1/F2 commands failed as documented, then passed after correction. Filtered mutation-control runs failed intentionally and are documented above. The **final complete build succeeded in 45 seconds: 184 tests, 25 suites, zero failures, errors or skips**. The XML files under build/test-results/test and assembled Boot/plain JARs correspond to the corrected code with no negative-control mutations. Git whitespace and SQLite main/sidecar ignore checks passed. The known Mockito JVM class-sharing notice is not a test failure. No global settings, data deletion, clean/reset, commit or push was performed.

### Current disposition and acceptance limits

F1-F4 are resolved at the implemented/tested scope; T049-T052 are complete. SC-001/SC-002 have workflow/negative evidence, SC-003 includes every required controlled order plus complete business-state/history comparisons, and SC-004 has the required current-role/action and diagnostic-window evidence. These are automated acceptance results, not a human sign-off or a proof of physical-media durability.

T048 and SC-005 remain **Unassessed**. The frozen prompts/key and results have not been replaced, and no participant answers were fabricated. Production TLS/certificate/proxy verification, physical disk exhaustion/damage, production ACL enforcement and restore rehearsal remain unexecuted. Real read-only SQLite and controlled injected storage/acknowledgement faults do not substitute for those environmental checks. The remaining feature acceptance step is the real two-participant evaluation; remote production exposure additionally needs actual TLS and operational validation. A subsequent independent Converge/review can assess the remedied code without treating this implementation follow-up as an independent audit.
