# Tasks: ChangeFlow Initial Workflow

**Input**: Design documents from specs/001-change-request-workflow/
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [HTTP contract](contracts/http-api.md), [quickstart.md](quickstart.md), and .specify/memory/constitution.md.
**Created**: 2026-11-15
**Revised**: 2026-11-15 â€” owner-approved SQLite file storage; all T001â€“T048 IDs and existing unchecked states preserved.
**Tests**: Required by SC-001 through SC-004, acceptance scenarios, and the constitution.
**Organization**: Setup â†’ foundation â†’ US1 (P1) â†’ US2 (P1) â†’ US4 (P1) â†’ US3 (P2) â†’ cross-cutting validation. No implementation performed by generating this file.

## Format: [ID] [P?] [Story] Description

## Current execution status - 2026-11-15

T001-T046 and T049-T052 are complete according to the recorded evidence. T047-T048 are withdrawn, not passed.

| Tasks | Delivered evidence |
| --- | --- |
| T013-T014 | Real per-scenario file SQLite fixture, independent physical connections, native engine 3.53.4, Flyway V1-V3 execution/validation, WAL/PRAGMAs, STRICT/FK/unique/length/status constraints, unknown/checksum fail-closed and persistence across closed application contexts. Files retained; no cleanup deletes. |
| T015-T022 | Pure preparation rules and existing Unicode boundary suites; actual JDBC insert/CAS/filtered keyset reads; outer transaction orchestration; deferred payload validation and POST/detail/list/PUT/Submit; real filter-chain/API and preparation tests. |
| T023-T030 | Review rules/routes and atomic append-only feedback; rejection and latest-feedback checks; real read-only write failure; injected FULL/IOERR, post-CAS insert failure, pre-commit abort and lost acknowledgement around real SQLite transactions. Whole outcomes, safe errors and zero replay checked. |
| T031-T036 | Bounded latch support; original-tag replay tests; all eight independent-connection winner orders; actual BUSY_SNAPSHOT versus separate bounded writer-slot wait; confirmed rollback/fresh connection diagnosis; current-access 404/412/503; revocation and uncertain rollback/commit coverage. |
| T037-T042 | Returned-owner policy and incomplete edit rules, HTTP visibility, multiple feedback cycles, greatest-version projection, current revocation and full revision/resubmission/approval journey. Shared primitives reused. |
| T043-T044 | Real-JDBC role/state/action and privacy/error matrix, no-store and safe 500/503, header/CORS controls plus existing Basic/transport tests. Layer review consolidates role/owner policy and confirms seven approved routes, pure rules and no extra dependency/subsystem. |
| T045-T046 | README/Quickstart, full build, dependency checks, Git exclusions, and real HTTP validation. T047 is withdrawn. |

Final Java 21 `gradlew.bat --offline --no-daemon build`: **BUILD SUCCESSFUL in 42s**, 172 tests / 24 suites, zero failures, errors or skips. Runtime dependency inspections resolved Xerial 3.53.4.0 and Flyway core 12.4.0. Whitespace/Git exclusion checks passed. Full test evidence includes actual loopback Boot HTTP and repeated application startup against the same file, not production TLS or human acceptance. Physical disk exhaustion/ACL/restore/deployment tests were not represented as executed; storage fault injection is labeled explicitly. Initial fixture/test-configuration/unsupported-method defects were corrected and passing reruns recorded.


Every checkbox is executable work, not a claim of completion. [P] identifies separate-file work that may run together once its stated phase prerequisites are complete; it does not waive preceding dependencies. Story labels retain the specification's original numbering.

## Path Conventions

Paths are repository-relative and refer to the existing single Gradle project. All production Java stays in src/main/java/com/example/changeflow/{controller,domain,repository}; tests in src/test/java/com/example/changeflow/. New files are proposed outputs, not files created during task generation. Do not add a fourth application layer, JPA, UI, AI features, role-management API, or a general audit/history API.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the approved toolchain and minimal dependency baseline; preserve existing Boot/Wrapper versions.

- [X] T001 Verify a usable Java 21 toolchain and writable Gradle cache using gradlew.bat and gradle/wrapper/gradle-wrapper.properties; record actual commands, versions, failures and remediation in specs/001-change-request-workflow/quickstart.md without replacing the existing Gradle 9.8.2 Wrapper or Boot 4.1.2 to match local Java 25.
- [X] T002 Add only the approved BOM-managed MVC, Security, JDBC, Flyway starter/core, org.xerial:sqlite-jdbc 3.53.4.0 and MVC/Security test starters in build.gradle; omit the former PostgreSQL driver/module and Testcontainers dependencies, retain Java 21/Boot 4.1.2/Wrapper 9.8.2 and existing launcher, resolve runtime/test graphs and record versions/native-load evidence in specs/001-change-request-workflow/quickstart.md.
- [X] T003 Author SQLite file configuration only in .gitignore, src/main/resources/application.properties and src/main/java/com/example/changeflow/repository/DataSourceConfiguration.java: CHANGEFLOW_DB_PATH/busy timeout, independent Xerial connections, DEFERRED/FK/synchronous/read-uncommitted settings, WAL-before-single-migrator startup wiring, local path/permissions and fail-closed rules from plan.md. Add exclusions before runtime file creation; do not execute migrator or claim WAL/schema verification here. Actual execution waits for T014 after T004â€“T006 and T013; document this in specs/001-change-request-workflow/quickstart.md.

**Checkpoint**: Toolchain/dependency resolution is actually verified or explicitly blocked with evidence. Do not represent the previous wrapper startup failure as a successful build.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared storage, authorization snapshots, pure rules and HTTP infrastructure. Complete this phase before story implementation. These constraints are quoted from data-model.md; application checks supplement database checks.

- [X] T004 Create SQLite STRICT src/main/resources/db/migration/V1__identity.sql with app_user id "TEXT NOT NULL PRIMARY KEY, canonical lowercase UUID, never reused", username "TEXT NOT NULL UNIQUE, BINARY case-sensitive comparison", password_hash "TEXT NOT NULL, encoded BCrypt hash", enabled "INTEGER NOT NULL CHECK IN (0,1)"; user_role user_id "TEXT NOT NULL REFERENCES app_user(id), deletion restricted", role "TEXT NOT NULL CHECK IN ('Requester','Reviewer')" and "PRIMARY KEY (user_id, role)"; no nested BEGIN/COMMIT/schema-qualified SQL, no user deletion or public provisioning/revocation.
- [X] T005 Create SQLite STRICT src/main/resources/db/migration/V2__change_requests.sql with id "TEXT NOT NULL PRIMARY KEY; server-generated canonical lowercase UUID, never reused", requester_id "TEXT NOT NULL REFERENCES app_user(id); immutable after creation; deletion restricted", title "TEXT NOT NULL DEFAULT ''; CHECK length(title) <= 200", change_description "TEXT NOT NULL DEFAULT ''; CHECK length(change_description) <= 5000", change_reason "TEXT NOT NULL DEFAULT ''; CHECK length(change_reason) <= 2000", status "TEXT NOT NULL CHECK IN ('Draft','PendingReview','ChangesRequested','Approved')", version "INTEGER NOT NULL CHECK version >= 0; starts at 0; increments for every accepted edit or transition"; index requester_id/status and use canonical UUID BINARY ordering; no PostgreSQL types/casts or deletion.
- [X] T006 Create SQLite STRICT src/main/resources/db/migration/V3__return_reasons.sql with request_id "TEXT NOT NULL REFERENCES change_request(id); deletion restricted", request_version "INTEGER NOT NULL CHECK request_version > 0; resulting request version for this RequestChanges", reviewer_id "TEXT NOT NULL REFERENCES app_user(id); non-owner enforced by domain", reason "TEXT NOT NULL; nonblank under contract; CHECK length(reason) <= 2000" and "PRIMARY KEY (request_id, request_version)"; explicit NOT NULL on all keys, no cascade/purge/nested transaction and no invalid cross-table CHECK.
- [X] T007 [P] Define plain records/enums in src/main/java/com/example/changeflow/domain/ChangeRequest.java, ActorPermissions.java and ReturnReason.java with the T004â€“T006 field invariants; preserve immutable owner and nonnegative version, reject signed-64-bit version overflow, and keep MVC/Security/JDBC/transaction imports out of these types.
- [X] T008 [P] Implement exact scalar/blank rules in src/main/java/com/example/changeflow/domain/ContentRules.java: reject U+0000/unpaired surrogates, count code points without normalization/trimming, enforce 200/5000/2000/2000 limits on every save, allow incomplete Draft/ChangesRequested and require nonblank submission/return fields using Java 21 isWhitespace OR isSpaceChar.
- [X] T009 [P] After T007 model contracts are stable (foundation wave B), implement read-union/action policy in src/main/java/com/example/changeflow/domain/RequestAccessPolicy.java: Requester owns-only all states, Reviewer PendingReview/Approved, dual-role union, current owner Requester edits/submits, current non-owner Reviewer decisions; no self-review/Approved mutation. May run alongside T010, not alongside unfinished T007.
- [X] T010 [P] After T007 ActorPermissions/model contracts are stable (wave B), implement uncached JDBC credentials and first-SELECT enabled/current-role snapshot in src/main/java/com/example/changeflow/repository/IdentityRepository.java; authentication transaction ends before service operation, no old snapshot or cached Security authorities. Diagnostic transactions also fetch fresh roles before resource access; may run alongside T009, not unfinished T007.
- [X] T011 Implement stateless Basic/BCrypt and custom X-ChangeFlow-Client: api unsafe-method guard in src/main/java/com/example/changeflow/controller/SecurityConfiguration.java and ApiClientHeaderFilter.java using T010; disable sessions, cookie authentication, permissive CORS and trusted forwarded identity; use explicit custom-header CSRF protection, not a statelessness-only justification for disabling tokens.
- [X] T012 [P] Implement central safe problem mapping and strong resource/version tag parsing in src/main/java/com/example/changeflow/controller/ApiExceptionHandler.java and VersionPrecondition.java: RFC 9457, identical missing/private 404, 400 input/precondition, 401 Basic challenge, 403 permission/header, 409 state, 412 version, 428 absent precondition, 503 infrastructure/uncertain outcome, 500 unexpected; no private metadata or sensitive logs and no-store responses.
- [X] T013 [P] Create actual SQLite on-disk temporary-file support in src/test/java/com/example/changeflow/support/SqliteTestSupport.java with independent physical connections to the same per-scenario file, single Flyway migration and committed identity/request fixtures; no Docker/Testcontainers/H2/:memory:/shared cache/enclosing rollback-only transaction; verify configured PRAGMAs on each connection and make src/test/java/com/example/changeflow/ChangeFlowApplicationTests.java use this fixture; scope cleanup strictly to closed test temporary directories.
- [X] T014 After T003 configuration, all T004â€“T006 migration scripts and T013 fixtures are ready, first execute configured startup: verify actual journal_mode=WAL and every required connection PRAGMA, run the single migrator and validate schema. Verify exact Flyway 12.4.0/core + Xerial 3.53.4.0 native loading, SELECT sqlite_version(), new-file/repeated startup/checksum fail-closed, STRICT/FK/unique/length/status constraints and scalar-count length(text) in src/test/java/com/example/changeflow/repository/SchemaIntegrationTests.java using T013; close/reopen same file across contexts to prove persistence and reject unknown existing schema without clean/baseline/delete.

**Checkpoint**: Schema, security integration, policy and shared validation available. Wave A: T007 and T008 may run together. Wave B: T009 and T010 may run together only after T007 models are stable (complete wave A first). T012/T013 are separate-file work after migration contracts; T011 requires T010; T014 requires T003â€“T006 and T013 and performs first runtime validation. Generic error mapping never overrides resource concealment.

## Phase 3: User Story 1 - Prepare and submit (Priority: P1, MVP)

**Goal**: Owner creates, reads, edits and submits a proposal. Shared edit/Submit primitives support both Draft and ChangesRequested from the start, as FR-004/005 require; US3 later proves the full feedback journey.

**Independent Test**: Create incomplete Draft as Requester, edit within limits, reject incomplete Submit, complete and submit to PendingReview; owner preserved and unauthorized operations do not mutate. Review decisions are unnecessary.

### Tests for User Story 1

Write tests first and observe meaningful failure before implementation; execute them again at the checkpoint.

- [X] T015 [P] [US1] Add plain-domain Draft creation/edit/Submit tests in src/test/java/com/example/changeflow/domain/PreparationRulesTests.java covering immutable owner/status, incomplete saves, required nonblank submission fields, every Unicode limit/limit+1, supplementary/combining characters, whitespace, U+0000 and unpaired surrogates.
- [X] T016 [P] [US1] Add HTTP contract/security tests in src/test/java/com/example/changeflow/controller/PreparationApiTests.java for POST/GET/list/PUT/Submit, Basic and unsafe header failures, null/omitted-as-empty replacement semantics, unknown-property/type rejection, UUID/cursor/page validation, Location/ETag/decimal-string versions and read-filtered pagination.
- [X] T017 [P] [US1] Add real-storage preparation tests in src/test/java/com/example/changeflow/repository/PreparationIntegrationTests.java for owner-bound inserts, bounded reads, versioned edits/submission, all-state read union, 404 concealment before payload/state/version errors and unchanged rows after denial.

### Implementation for User Story 1

- [X] T018 [US1] Implement plain preparation rules in src/main/java/com/example/changeflow/domain/ChangeRequestRules.java using T007â€“T009; creation produces Draft/version 0, edit preserves owner/state in Draft or ChangesRequested, Submit validates all three fields and moves either eligible state to PendingReview, and no other transitions are added.
- [X] T019 [US1] Implement insert/load, visible keyset list and id+version CAS in src/main/java/com/example/changeflow/repository/ChangeRequestRepository.java using SQLite prepared SQL, canonical lowercase TEXT UUIDs/BINARY ordering and INTEGER long versions; project latest reason by greatest request_version, filter visibility before limit (default 50, 1â€“100, after UUID), never change owner, increment every save including identical content, reject 64-bit overflow and expose zero rows/extended lock codes for service handling without replay.
- [X] T020 [US1] Implement src/main/java/com/example/changeflow/domain/ChangeRequestService.java with nontransactional orchestration outside TransactionTemplate callbacks, rejecting ambient transactions. Execute mutation once in DEFERRED callback with fresh first-SELECT roles, concealment/action/input/original If-Match/state checks and atomic CAS. Catch only outside execute after confirmed rollback/completion and resource unbinding; run a separate SELECT-only fresh transaction (no JDBC readOnly flag), reread current enabled/roles/access and compare original caller tag: private/missing 404, readable mismatch 412, unchanged snapshot failure 503. Never reuse old snapshot/permission grant or replay/refresh. Unconfirmed rollback/uncertain commit gets safe 503 without diagnostic claims; success only after commit completes. Follow plan.md boundary protocol.
- [X] T021 [US1] Add content/representation/page records and POST/GET/list/PUT/Submit routes in src/main/java/com/example/changeflow/controller/ChangeRequestController.java and RequestPayloads.java; defer resource payload validation until after service read-access concealment, bind actor from verified principal not body, apply exact contracts/http-api.md field/output/header semantics, and return 204 empty for Submit.
- [X] T022 [US1] Execute T015â€“T017 and the US1 quickstart flow, fix failures within those files, and record actual command/test/storage outcomes in specs/001-change-request-workflow/quickstart.md; assert no Delete/PendingReview edit/Approved edit capability was introduced.

**Checkpoint**: US1 is a useful proposal-submission MVP with baseline security and CAS; it is not acceptance of the full reviewed workflow or SC-003.

## Phase 4: User Story 2 - Review a proposal (Priority: P1)

**Goal**: A non-owner current Reviewer approves or returns pending proposals with required feedback.

**Independent Test**: Seed PendingReview requests; approve/return with an authorized non-owner, reject self-review, wrong role/state and blank/overlength reason, inspect committed status and feedback.

### Tests for User Story 2

- [X] T023 [P] [US2] Add plain review-state/role/self-review tests in src/test/java/com/example/changeflow/domain/ReviewRulesTests.java, including both roles, all states, required nonblank/max-2000 return reason and Approved immutability.
- [X] T024 [P] [US2] Add Approve/RequestChanges contract tests in src/test/java/com/example/changeflow/controller/ReviewApiTests.java for If-Match variants, exact error precedence, 204 with no representation/ETag, rejection of completed-command retries, no private status/conflict disclosure and Reviewer losing read access after a return.
- [X] T025 [P] [US2] Add actual SQLite file atomic-review/storage failure tests in src/test/java/com/example/changeflow/repository/ReviewIntegrationTests.java: CAS+reason commit, forced failure after CAS/before insert and explicitly aborted commit roll back all rows, feedback inserted once/latest projection correct, lost commit acknowledgement unknown whole outcome, read-only/unwritable and injected FULL/IOERR fail safely without private paths/partial changes; never damage/fill production storage.

### Implementation for User Story 2

- [X] T026 [US2] Extend src/main/java/com/example/changeflow/domain/ChangeRequestRules.java with PendingReview-only Approve/RequestChanges, non-owner Reviewer requirement, required reason and immutable Approved behavior; retain preparation rules and do not permit requester self-return.
- [X] T027 [US2] Implement append-only reason insert/latest projection in src/main/java/com/example/changeflow/repository/ReturnReasonRepository.java, preserving all records by (request_id, request_version), no purge/cascade/history endpoint and no edits to prior reasons.
- [X] T028 [US2] Extend src/main/java/com/example/changeflow/domain/ChangeRequestService.java with review CAS+reason insert in the same TransactionTemplate callback and existing outer orchestration from T020; known failures escape for actual rollback before fresh-current-authorization SELECT-only diagnosis, no old snapshot/grant reuse, no success before commit, no replay or new read-access grant.
- [X] T029 [US2] Add Approve/RequestChanges routes and exact reason/empty-command bodies in src/main/java/com/example/changeflow/controller/ChangeRequestController.java and RequestPayloads.java; apply server-side actor/current-role policy, 204 empty success and existing safe errors without duplicating business rules.
- [X] T030 [US2] Execute T023â€“T025 against the actual shared-file SQLite driver, inspect unchanged request/reason rows after rejections and restart retention, run seeded review flow and record actual evidence/failures in specs/001-change-request-workflow/quickstart.md.

**Checkpoint**: US2 works from seeded requests without depending on a revision journey. US1 behavior remains green.

## Phase 5: User Story 4 - Preserve integrity under contention (Priority: P1)

**Goal**: Verify and complete contention/revocation/replay safeguards before the P2 journey.

**Independent Test**: Seed appropriate states, use real connections and bounded barriers, select each winner deliberately, inspect both outcomes and committed rows. Shared Draft/ChangesRequested edit/Submit operations already exist from US1; no US3 dependency.

### Tests for User Story 4

- [X] T031 [P] [US4] Build test-only bounded latch/barrier and transaction lifecycle recording in src/test/java/com/example/changeflow/support/ControlledOperations.java: callback failure, afterCompletion rollback, connection unbinding and fresh diagnosis start; independent SQLite snapshot/version pauses and separate no-read-snapshot BEGIN IMMEDIATE statement-entry/completion events. No arbitrary sleeps, same-connection simulation, production hooks or mutable global delays.
- [X] T032 [P] [US4] Add stale/replay tests in src/test/java/com/example/changeflow/controller/StaleCommandTests.java for every completed Submit/Approve/RequestChanges and replay after return/resubmit using original tags; assert rejection with no version/content/reason changes, and prevent implicit tag refresh/retry.
- [X] T033 [US4] Implement src/test/java/com/example/changeflow/concurrency/RequestConcurrencyTests.java using T031: eight controlled snapshot winner orders from plan.md, then assert actual rollback/completion and unbinding BEFORE diagnostic transaction, committed intervening changes visible in fresh snapshot, current read access checked, no second business callback/writes and no partial changes. Separately test no-read-snapshot BEGIN IMMEDIATE writer-slot waiting with verified short positive busy_timeout, monotonic elapsed/tolerance and bounded deadline while holder remains locked until contender returns; immediate BUSY does not pass timeout evidence. Snapshot-upgrade failure has no minimum wait requirement. Verify 412/404/503 classification and zero replay; include unconfirmed rollback/uncertain commit skipping diagnosis.
- [X] T034 [US4] Implement src/test/java/com/example/changeflow/concurrency/PermissionRevocationTests.java using T031: revoke before first operation SELECT rejects; an authorized read may finish after revoke, but failed mutations must rollback before fresh diagnostic authorization. Commit revocation/newly private resource between failed snapshot and diagnostic first SELECT; verify new snapshot sees current roles/access and returns generic 404 without status/version, not an old-grant 412. Next operation checks again; unchanged resource snapshot failure yields 503 only while currently readable. Assert no business callback rerun.

### Implementation and integration for User Story 4

- [X] T035 [US4] Correct lock-code classification, stale-write/transaction-boundary/first-role snapshot and fresh post-rollback diagnosis defects exposed by T032â€“T034 in src/main/java/com/example/changeflow/domain/ChangeRequestService.java and src/main/java/com/example/changeflow/repository/ChangeRequestRepository.java; preserve DEFERRED single-writer semantics, bounded waiting and no command retry; keep test seams package-private/test-only with no extra production queue/lock infrastructure.
- [X] T036 [US4] Execute T031â€“T034 with independent connections on the same real SQLite file, verify all eight ordered cases plus timeout/snapshot/rollback/revocation outcomes, record SELECT sqlite_version()/PRAGMAs and SC-003/004 evidence/failures in specs/001-change-request-workflow/quickstart.md; no random-repeat substitute or human acceptance claim.

**Checkpoint**: SC-003/SC-004 have actual controlled-test evidence or explicit unresolved failures. US1/US2 tests still pass; the full returned-request journey is next.

## Phase 6: User Story 3 - Revise and resubmit (Priority: P2)

**Goal**: Finish and prove the owner's feedback-driven revision journey while retaining every return reason.

**Independent Test**: Seed ChangesRequested with feedback, save incomplete content as current Requester owner, complete and resubmit, then verify latest reason and retained history over multiple cycles.

### Tests for User Story 3

- [X] T037 [P] [US3] Add returned-edit domain tests in src/test/java/com/example/changeflow/domain/RevisionRulesTests.java: current Requester owner only, Reviewer-only owner denied, incomplete saves within limits, same status on edit, nonblank Submit and no ownership/reason-history mutation.
- [X] T038 [P] [US3] Add revision contract tests in src/test/java/com/example/changeflow/controller/RevisionApiTests.java for owner latest-reason visibility, non-owner ChangesRequested concealment, dual-role union, resubmitted Reviewer visibility, malformed/overlength replacement rejection and stale-cycle commands.
- [X] T039 [P] [US3] Add real-storage multi-cycle workflow tests in src/test/java/com/example/changeflow/repository/RevisionIntegrationTests.java retaining all different return reasons through edit/resubmit/approval, projecting the greatest request_version, denying revoked access and proving failures leave all previous reasons intact.

### Implementation and integration for User Story 3

- [X] T040 [US3] Complete any revision-specific gaps in src/main/java/com/example/changeflow/domain/ChangeRequestService.java and ChangeRequestRules.java so existing shared edit/Submit paths enforce current owner Requester access, allow incomplete saves and preserve stored feedback; reuse existing routes/repository primitives rather than create a parallel revision subsystem.
- [X] T041 [US3] Complete latest-reason projection/read-policy integration in src/main/java/com/example/changeflow/repository/ChangeRequestRepository.java and src/main/java/com/example/changeflow/controller/ChangeRequestController.java; never disclose full history or grant Reviewer access to ChangesRequested merely because feedback exists.
- [X] T042 [US3] Execute T037â€“T039 and the full create/return/revise/resubmit/approve journey from specs/001-change-request-workflow/quickstart.md; record actual outcomes and verify prior US1/US2/US4 tests remain passing.

**Checkpoint**: Record actual automated validation and any unfinished assignment work.

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verify the assignment deliverables, documentation, and actual automated results.

- [X] T043 Add complete filter-chain/error-precedence regression coverage in src/test/java/com/example/changeflow/controller/AccessAndErrorContractTests.java for every role/state on list/detail/mutations, identical missing/private body+headers even with bad payload/stale version, custom-header CSRF/CORS restrictions, Basic failures, safe 500/503, no-store and no secrets/status/version leakage.
- [X] T044 Verify Controller â†’ domain service â†’ repository responsibilities and plain-rule independence across src/main/java/com/example/changeflow/controller/, src/main/java/com/example/changeflow/domain/ and src/main/java/com/example/changeflow/repository/; remove accidental duplicate policy/framework coupling and ensure no unsupported UI/history/delete/admin endpoints or speculative dependencies.
- [X] T045 Update README.md and specs/001-change-request-workflow/quickstart.md with actual CHANGEFLOW_DB_PATH/busy timeout, local persistent file permissions, Git main/sidecar exclusions, safe backup/restart/lock handling, no sidecar deletion or data reset, TLS/trusted-proxy boundary, BCrypt operator provisioning/revocation, original-ETag examples and uncertain-commit recovery; no datasource server credentials or Docker requirement.
- [X] T046 Run Java 21 Wrapper dependency inspection/full test/build, single-migrator validation and HTTP quickstart on actual SQLite file; inspect .gitignore via git check-ignore for main/-wal/-shm/-journal files without deleting tracked data, verify close/reopen persistence, record exact driver/engine/Flyway/native load/PRAGMAs and test/race/failure evidence in specs/001-change-request-workflow/quickstart.md; do not infer acceptance from build success.
T047 — Withdrawn: additional human-evaluation preparation is outside the Session 1 submission scope.
T048 — Withdrawn, not passed: the additional two-participant evaluation is no longer required.

**Checkpoint**: Record actual automated validation and any unfinished assignment work.

## Dependencies & Execution Order

### Phase Dependencies

Setup T001â€“T003 â†’ foundation T004â€“T014 â†’ US1 T015â€“T022 â†’ US2 T023â€“T030 â†’ US4 T031â€“T036 â†’ US3 T037â€“T042 â†’ final T043–T046 (T047–T048 withdrawn).

Within foundation: T003 configuration/Git authoring only â†’ T004 â†’ T005 â†’ T006. Foundation wave A: T007/T008; wave B: T009/T010 only after wave A and stable T007 models. T012/T013 can be prepared separately after migration contracts; T011 needs T010. T014 requires T003â€“T006/T013 and is the first execution of configured WAL/migrator/schema validation. All foundation work completes before story work.

### User Story Dependencies

| Story | Build prerequisites | Independent validation |
| --- | --- | --- |
| US1 P1 | Foundation | Create/edit/submit; no review needed |
| US2 P1 | US1 shared rules/repository/service/controller | Seed PendingReview; approve/return without US3 |
| US4 P1 | US1 and US2 operations; shared returned edit/Submit primitives in US1 | Seed all required states and feedback, then controlled races/revocation/replay |
| US3 P2 | US1/US2 shared operations; US4 integrity gate | Seed ChangesRequested; revise/resubmit; verify multicycle retention |

Logical implementation dependency US2 â†’ US3 reflects the feedback journey, not a requirement to drive every test through earlier HTTP stories. Fixtures allow isolated validation. Shared production files mean stories must not edit those files concurrently. US4 precedes US3 because of priority; returned-state edit/Submit correctness is established in the shared US1 primitives, not deferred until US3.

### Within Each User Story

Author tests first; observe the intended failure (not just a missing dependency error); implement rules â†’ repository â†’ service â†’ routes; run independent tests and storage assertions. For test-only US4/US3 slices, inspect existing shared primitives and change only demonstrated gaps. Preserve earlier story behavior at every checkpoint.

### Parallel Opportunities

Foundation parallel batches are conditional: wave A T007/T008, then wave B T009/T010 after model stabilization; never launch T007/T009/T010 as one independent batch. T012/T013 may be prepared independently after their migration/configuration prerequisites.

Only marked work is eligible after prerequisite completion. Do not run tasks against shared production files in parallel. Test-authoring pairs may proceed independently with the already defined contract; test execution still waits for their implementation prerequisites.

## Parallel Example: User Story 1

T015 (domain preparation tests), T016 (HTTP preparation tests), and T017 (storage preparation tests) use separate files and can be authored together after foundation. T018â€“T022 remain sequential.

## Parallel Example: User Story 2

T023 (plain review tests), T024 (review HTTP tests), and T025 (transactional review tests) can be authored together after US1. T026â€“T030 modify shared workflow files and remain sequential.

## Parallel Example: User Story 4

T031 (test-only controlled-operation support) and T032 (stale HTTP tests) use different files and can be authored together after US2. T033/T034 require T031 and are sequential in this list; T035/T036 wait for the cases they fix/execute.

## Parallel Example: User Story 3

T037 (revision rule tests), T038 (revision API tests), and T039 (multicycle storage tests) can be authored together after US4. T040â€“T042 share production workflow components and remain sequential.

## Implementation Strategy

### MVP First (User Story 1 Only)

Complete setup/foundation and US1, then demonstrate authenticated owner creation/editing/submission and safe failure handling. This is an internal proposal-submission MVP, not the full ChangeFlow feature. Baseline CAS/security cannot be skipped for an MVP.

### Incremental Delivery

Add US2 review, then P1 US4 controlled integrity evidence, then P2 US3 full revision/retention journey. Finish cross-cutting contract tests and operational documentation, run actual build/validation, record the actual automated validation and any remaining assignment gaps. Do not automatically deploy or claim acceptance.

### Parallel Team Strategy

Parallelize only separate-file foundation/test-authoring opportunities above. Serialize ChangeRequestRules.java, ChangeRequestService.java, ChangeRequestRepository.java and ChangeRequestController.java edits. This document identifies optional parallel work; it does not launch agents or implementation.

## Coverage and Validation Notes

- FR-001â€“005: T015â€“T022 and T037â€“T042.
- FR-006â€“010: T009â€“T011, T023â€“T030 and T034.
- FR-011/013 and SC-003/004: T019â€“T020, T025, T031â€“T036.
- FR-012/014: T009â€“T012, T016â€“T017, T020â€“T021, T024, T033â€“T034 and T043.
- FR-015: T018, T022â€“T023 and T043.
- FR-016â€“019: T005â€“T008, T015, T023â€“T030 and T037â€“T042.
- Exact field constraints are quoted in T004â€“T006; validation/counting/default policies are carried into T007/T008 and story tests.
- Generated task counts: setup 3, foundation 11, US1 8, US2 8, US4 6, US3 6, final 6; total 48.
- All tasks start unchecked. No application code, test suite, build, migration, HTTP flow or human evaluation was executed by task generation.
- No .specify/extensions.yml exists; before/after task hooks are absent.
- SQLite revision preserves 48 task IDs/order and all unchecked states. T002 removes planned server/container dependencies; T003 must protect file paths/Git before execution; T013/T014 establish actual-file fixtures; T020/T025/T031â€“T036 cover snapshot/lock/storage differences. Earlier PostgreSQL tasks are superseded. No runtime source/dependency/migration/Git-ignore edits or data deletion occurred during this revision.





## Setup execution checkpoint â€” 2026-11-15

T001â€“T003 completed with actual evidence in quickstart.md. I3 summary in data-model.md now gives 503 only while current read access remains; lost access expects concealed 404. Java 21/Wrapper, dependency files, native loader and compilation/resource processing verified. Sandbox JAR access required the identical offline compile outside the sandbox; no global settings changed. No startup, migrations, WAL/schema verification, tests or T004+ implementation ran; all T004â€“T048 remain unchecked. Historical generation notes above are not execution claims.


## Migration authoring checkpoint â€” 2026-11-15

T004, T005 and T006 completed in that order by creating and reviewing V1__identity.sql, V2__change_requests.sql and V3__return_reasons.sql. Static review checked four STRICT tables, required TEXT/INTEGER columns and NOT NULL, primary/unique keys, CHECK limits and enums, four deletion-restricted foreign keys referencing earlier tables, version default zero and request indexes. No PostgreSQL types/casts, nested transaction statements, cascading deletion or seed data were introduced. Canonical UUID generation, immutable ownership, Unicode nonblank checks, self-review prohibition and append-only business operations remain application responsibilities per data-model.md; no extra triggers or product rules were added.

Validation was file/text inspection and Git whitespace checking only. No SQLite connection, SQL execution, Flyway migration, startup, WAL/schema runtime verification or tests ran. Runtime acceptance remains T014 after its prerequisites. No Java or T007+ work was started in this checkpoint.
## Domain foundation checkpoint — 2026-11-15

T007/T008 completed: immutable ChangeRequest/Status, ActorPermissions/Role (defensive role-set copy), ReturnReason, and plain ContentRules. Required identities and version bounds validated; nextVersion rejects overflow without mutating the original owner or snapshot. All stored content validates scalar/length constraints; null proposal fields become empty strings, incomplete saves remain allowed, and submission/return reason require nonblank fields. No status-transition/access policy or T009+ implementation added.

Dedicated ContentRulesTests and DomainModelsTests were added without modifying PreparationRulesTests, ReviewRulesTests, RevisionRulesTests or later-task test ownership. Java 21 offline compileJava and filtered test execution passed (28 tests, zero failures/errors/skips). Domain source/tests contain no Spring/MVC/Security/JDBC/transaction dependencies. No startup, application-context test, JDBC connection, migration or schema/WAL validation executed. Full context suite intentionally not run because its datasource/security fixtures belong to later tasks.
## Access/identity foundation checkpoint - 2026-11-15

T009 completed as pure RequestAccessPolicy with enabled/current-role, union read access, owner preparation in Draft/ChangesRequested and non-owner Reviewer decisions only in PendingReview. The exhaustive state/ownership/enabled/role matrix has 64 cases plus two absent-actor/fresh-snapshot cases; no HTTP/database policy dependency was introduced.

T010 completed at implementation and unit-collaboration scope: uncached prepared JDBC credential lookup uses a standalone autocommit connection and refuses active operation transactions; permission lookup requires an actual transaction and its bound non-autocommit connection, reads enabled and roles in one SELECT without password data, and releases resources without closing the operation connection. AuthenticationIdentity is internal credential material with redacted toString, not a resource response. No Security authority lookup/cache or logging of secrets was added.

The first-SELECT requirement is an explicit repository calling contract, not something a repository can prove about SQL already issued elsewhere. Future T020 orchestration must call it first in each operation/diagnostic transaction; T013/T014/T034 and later integration tests must establish actual connection lifecycle, fresh diagnostic authorization and committed SQLite revocation behavior. No service or Security adapter was implemented in this checkpoint.

Actual Java 21 offline compileJava and filtered unit tests passed: RequestAccessPolicyTests 66, IdentityRepositoryTests 10, prior domain tests 28; total 104, zero failures/errors/skips. IdentityRepositoryTests mock JDBC and Spring thread-bound transaction state: they are not evidence of real SQLite SQL, snapshot isolation, schema, rollback or JDBC integration correctness. No database connections to a real engine, migrations, startup, application-context/full suite or T011+ work ran. Git diff --check passed.
## HTTP security/error foundation checkpoint - 2026-11-15

T011 implemented: uncached IdentityRepository-backed Basic provider validates only {bcrypt} BCrypt hashes, rejects missing/invalid/disabled identities with explicit challenge, and creates a UUID-only principal with erased credentials and no role claims. Explicit single-provider manager prevents duplicate fallback authentication lookup. Stateless/null SecurityContext persistence and request cache, disabled form/remember-me/logout, mandatory non-simple unsafe-method header and cross-origin denial replace token CSRF protection; statelessness alone is not the justification. Filters are registered once in the security chain, not as servlet filter beans. Forwarded/client identity headers are not trusted; server.forward-headers-strategy=none. Non-loopback plaintext is rejected before reading credentials; loopback HTTP is the documented local exception. Real TLS/server/proxy configuration remains an operational verification, not established by mock requests.

T012 implemented: central safe problem schema for filter and MVC failures, explicit Basic challenge and no-store, generic identical missing/private 404 outcome, no exception-message/body/secret echoes, fixed storage/uncertain-outcome details, and strict strong If-Match parsing with 400/428/412 distinctions and signed-64-bit bounds. VersionPrecondition is an explicit helper only for already-visible resources; no early parser filter/argument resolver or business rule/controller was introduced. T020/T021 and later story tests must enforce real resource-access-before-validation ordering. No business service or request endpoints were created here.

Actual Java 21 offline compileJava and filtered tests passed: SecurityConfigurationTests 9, VersionAndErrorTests 16, previous domain/repository unit tests 104; total 129 with zero failures/errors/skips. Two initial failing tests exposed duplicate provider fallback and a test-probe empty-header conversion; corrected and final rerun passed. Git diff --check passed. Security tests use a limited MVC/security test context and mocked IdentityRepository; no Boot server, real JDBC/SQLite, Flyway, WAL/schema, TLS handshake/certificate, trusted proxy deployment or full application acceptance was exercised. All T013+ remain unchecked; no migrations/startup/story implementation ran.

## Phase 8: Convergence


Execute T049 first, then T050, T051 and T052 sequentially: transaction, shared controller and shared test-support files may overlap, so no [P] markers are assigned. Preserve the approved product rules and contract. Each fix must include its regression evidence; after T052 run the complete Java 21 build/tests and update README/quickstart/current execution evidence without claiming human or production TLS acceptance.

- [X] T049 CRITICAL Resolve F1: make production transaction/connection cleanup discard an unresolved SQLite transaction without restoring auto-commit in a way that commits pending work; prevent a second COMMIT after commit failure. Inspect and minimally adjust src/main/java/com/example/changeflow/repository/DataSourceConfiguration.java and the actual transaction-manager wiring used by ChangeRequestService.java. Replace the test-only rollbackOnCommitFailure override as a substitute for production evidence in src/test/java/com/example/changeflow/repository/ReviewIntegrationTests.java and extend src/test/java/com/example/changeflow/concurrency/RequestConcurrencyTests.java. Completion: with the production bean/configuration and real on-disk SQLite, inject failure after CAS before feedback plus rollback failure BEFORE the real ROLLBACK, commit failure BEFORE the real COMMIT, lost acknowledgement AFTER successful commit and rollback failure after completion; record physical connection/SQL lifecycle. Pending partial work must never commit during cleanup, a known pre-commit abort must leave both request and reasons unchanged, lost acknowledgement must retain an unknown whole outcome, unresolved connections must close/discard safely, and no diagnostic read, business replay, refreshed tag or repeated COMMIT may occur after uncertainty. Re-run atomicity/race tests and correct affected delivery claims per FR-011/FR-012, plan: Operation Coordination and Transactions / SQLite File Lifecycle and Failure Policy, T020/T025/T033 and Constitution principles 5/7 (contradicts).

- [X] T050 Resolve F2: enforce the contract's exact UTF-8 JSON input at the byte-to-domain boundary in src/main/java/com/example/changeflow/controller/ChangeRequestController.java and RequestPayloads.java; do not let StringHttpMessageConverter silently replace malformed byte sequences before ContentRules sees them. First add raw-byte HTTP regressions using the production converter/Boot configuration in src/test/java/com/example/changeflow/HttpWorkflowTests.java and controller contract tests. Completion: malformed/truncated/overlong UTF-8 and UTF-8 encodings of surrogates are rejected as safe 400 INVALID_INPUT without request/reason/version changes for readable authorized targets; invalid payloads on missing/private UUID resources preserve identical concealed 404; valid supplementary characters, combining sequences and a genuinely supplied U+FFFD are preserved exactly and counted correctly. Retain escaped-unpaired-surrogate/U+0000 rejection, every-save limits, incomplete-save semantics and current error precedence. Do not reject valid U+FFFD as a workaround or change numeric limits per FR-012/FR-017/FR-018, plan: HTTP/security and Unicode validation, contracts/http-api.md: Unicode and validation, T008/T016/T021/T043 and Constitution principle 4 (contradicts).

- [X] T051 Resolve F3: complete integrity regression evidence in src/test/java/com/example/changeflow/support/ControlledOperations.java, concurrency/RequestConcurrencyTests.java and controller/StaleCommandTests.java. Completion: retain all eight independent-connection controlled winner orders and the separate real bounded writer-slot wait; for every order assert both outcomes, immutable owner, all three content fields, state/version and complete pre-existing/new feedback rows, including review-return winners. Record callback failure -> actual rollback completion -> old-resource unbinding/connection closure -> fresh diagnostic first permission SELECT, with no writes or command reentry during diagnosis. Seed prior feedback in returned-state races so its preservation is observable. Repeat completed Submit/Approve/RequestChanges immediately and after relevant return/resubmit cycles using original tags; compare full stored request and ordered feedback snapshots before/after each rejection, including 404 after Reviewer loses visibility. This is missing assertion coverage, not evidence that the current CAS/replay policy is broken. Record actual results and qualify prior full-coverage claims per FR-011/FR-012/FR-013/FR-019, SC-002/SC-003, US4/AC1-4, plan: controlled races and diagnostic boundary regression, T031/T032/T033/T036 (partial).

## Convergence remediation execution checkpoint - 2026-11-15

T049-T052 completed after review of the pre-existing working tree and test contents. The historical convergence findings and earlier evidence above remain retained; the latest implementation/disposition is in convergence-report.md. The final Java 21 offline Wrapper build succeeded in 45s with 184 tests in 25 suites, zero failures/errors/skips, and assembled Boot/plain JARs.

- T049: four real-file production-bean regressions failed before correction, including pending CAS being committed after rollback failed before SQL. SqliteTransactionManager now unbinds/closes owned physical connections without autoCommit restoration. Native commit hooks, before/after commit/rollback faults, complete unchanged rows, whole lost-acknowledgement outcome, no repeated COMMIT, no diagnostic lookup/replay and fresh subsequent physical connections pass. No pool is configured; this disposal policy is not asserted safe for logical pooled close/reset.
- T051: all eight races now verify complete content/owner/status/version/history, seeded earlier feedback, winning/final full snapshots and rollback/completion/unbind/physical-close/fresh-diagnostic order; immediate/old-cycle retries compare unchanged complete rows. A temporary test-double content mutation caused all eight cases to fail; it was removed. Existing production CAS/replay behavior needed no change.
- T052: real role revocation (credentials remain valid) is tested before every sensitive operation, including Draft/returned variants, remaining-role union reads and after rollback/closure before diagnostic first SELECT. JDBC coordination and HTTP error equality both pass. A temporary old-grant test double caused the new denial test to fail semantically and was removed. Existing production permission policy needed no change; one initial negative-control Optional-type harness error is recorded separately from behavioral evidence.


## Current scope — 2026-11-16

The owner removed additional participant evaluation and its tooling. T047-T048 are withdrawn rather than completed. Existing product and regression-test obligations remain. Previous build counts are historical; rerun the full build after removal of the four tooling-only tests. See scope-change.md.


### Current validation — 2026-11-16

Scope-cleanup build on 2026-11-16: `gradlew.bat --offline --no-daemon build` succeeded with Java 21; **184 tests in 25 suites, zero failures/errors/skips**. The four removed tests belonged only to the withdrawn evaluation tooling. Product regression tests remain.
