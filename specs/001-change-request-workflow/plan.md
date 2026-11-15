# Implementation Plan: ChangeFlow Initial Workflow

**Branch**: 001-change-request-workflow (setup-plan reported feature context; no Git checkout independently verified)
**Date**: 2026-11-15
**Spec**: [spec.md](spec.md)

## Summary

Implement the approved HTTP workflow as one Spring Boot application using the existing Java 21 toolchain and Gradle Wrapper. Updated 2026-11-15 after Tasks generation for the owner-approved switch to persistent file-based SQLite. Use Spring MVC controllers, a transactional domain service with plain business rules, and JDBC repositories backed by SQLite. Authenticate every request with HTTP Basic over TLS; load current permissions from the database for each operation. Optimistic compare-and-set writes and mandatory version preconditions protect content and review decisions.

This phase produces design documents only. The specification and application code remain unchanged.

## Technical Context

**Language/Version**: Java 21; existing Spring Boot 4.1.2 and dependency-management plugin 1.1.7; existing Gradle Wrapper 9.8.2.
**Primary Dependencies**: Boot-managed MVC, Security, JDBC, Flyway starter/core and Xerial SQLite JDBC driver; existing test starter plus MVC/Security test support. No JPA, Lombok, mapping framework, token issuer, or additional application module.
**Storage**: SQLite 3.53.4 bundled by Boot-managed sqlite-jdbc 3.53.4.0; persistent local UTF8 file; Flyway core 12.4.0; WAL, SQL CAS, driver DEFERRED/default SERIALIZABLE snapshot transactions. No READ_COMMITTED override.
**Testing**: Plain JUnit 6 domain tests; Spring HTTP/security tests; real SQLite on-disk integration and controlled concurrency tests using independent physical connections and JUnit temporary directories; no Testcontainers/Docker.
**Target Platform**: JVM server, TLS termination at a trusted reverse proxy or application TLS; local persistent file and writable sidecar directory. Windows development supported.
**Project Type**: Single HTTP service.
**Performance Goals**: None specified; no new latency, throughput, capacity, or availability promises.
**Constraints**: Approved status/access rules, retained return reasons, concealed private resources, current authorization and atomic mutations. No UI or execution of approved changes.
**Scale/Scope**: One application and one local SQLite database file; one writer across the whole file. No distributed cache or message broker.
**Environment evidence**: Local Java is 25.0.4, not the required 21. Wrapper --version failed before Gradle startup because C:\.gradle wrapper lock parent could not be created. Build, test and dependency resolution have not succeeded or been attempted beyond that startup failure. See research and quickstart for evidence and prerequisites.

## Constitution Check

| Principle | Before research | After design |
| --- | --- | --- |
| Simplicity | Pass: one service, no speculative infrastructure | Pass: JDBC avoids ORM and separate mapping abstraction |
| Specification first | Pass: approved spec read | Pass: product rules preserved; contracts resolve deferred representation details |
| Three-layer responsibilities | Pass: controller/domain/repository selected | Pass: domain service coordinates; repository owns SQL, not policy |
| Security | Pass: identity/current access required | Pass: uncached current roles, server ownership, TLS, privacy-first errors |
| Data integrity | Pass: atomicity and stale rejection required | Pass: transactional CAS plus feedback insert and database constraints |
| Testing/evidence | Pass: independent rules and real storage tests planned | Pass: both controlled race orders and unchanged-data assertions specified; not executed |
| Reliable delivery | Pass: explicit errors required | Pass: no sensitive diagnostics; run guide distinguishes future validation from evidence |

No constitutional exceptions or new product decisions required. Implementation merge still requires a successful build, relevant tests, review, and reported acceptance evidence. A documentation gate pass is not implementation acceptance.

## Project Structure

### Documentation (this feature)

- plan.md: architecture, gates, validation strategy
- research.md: decisions, alternatives, compatibility sources
- data-model.md: entities, invariants, transactions
- contracts/http-api.md: normative HTTP contract
- quickstart.md: prerequisites and future run/validation steps
- spec.md and checklists/requirements.md: existing, unchanged
- tasks.md: already generated; revised with existing T001–T048 IDs preserved for SQLite

### Source Code (repository root)

Retain existing com.example.changeflow package and one Gradle project. Proposed packages, not created:

- controller: request/response records, ChangeRequestController, central error mapping; Security configuration/filter integration
- domain: ChangeRequest, rules, ActorPermissions, ChangeRequestService, access policy
- repository: JDBC request, return-reason and identity repositories; row mapping; DataSourceConfiguration for file path/PRAGMAs
- src/main/resources/db/migration: future Flyway schema migrations
- src/test/java/com/example/changeflow: domain, controller/security, repository, concurrency tests

Dependency direction: Controller → Domain service → Repository → JDBC/database. Repository may return domain records; domain entities/rules never import MVC, Security, SQL or transaction APIs. Service is the coordination seam; orchestration stays outside TransactionTemplate callbacks and has no enclosing transactional annotation; do not introduce a fourth application layer or generic repository hierarchy.

## Operation Coordination and Transactions

Security filter authenticates credentials per HTTP request; no session or authentication cache. It does not decide request ownership or trust roles captured in a token. The domain service opens a transaction and loads enabled status and roles in one database statement on the primary connection. This statement is the operation authorization-check boundary. This is the first database SELECT, establishing the fresh snapshot; roles committed revoked before it are absent. Do not run any resource SELECT first or reuse an earlier transaction. Authorization already granted can finish; do not recheck roles midway through a continuing business transaction or reuse its snapshot in a later operation. A separate post-rollback diagnostic transaction must reread current roles/access. Credential validity remains a separate request-entry check.

Read: authenticate, load current permission snapshot, load resource, apply union read policy, then map output. Query list results with that same policy. No transaction snapshot carried across requests.

Mutation: authenticate; in one DEFERRED SQLite transaction (default SERIALIZABLE snapshot semantics) load current actor permissions and resource; conceal missing/inaccessible resource before state/version errors; enforce action permission (including self-review ban); validate input and version; apply pure domain rule; conditional update WHERE id and version match; insert return reason if required; commit once before success response. Creation binds requester to verified actor, never input identity.

The service orchestrator runs outside all transaction callbacks and has no enclosing @Transactional boundary. Reject accidental ambient transactions rather than joining them. Execute each mutation exactly once through TransactionTemplate with a Spring JDBC transaction manager; authentication has already finished. A callback failure must escape execute so rollback completes and JDBC resources are unbound before the orchestrator classifies it. Do not merely catch inside the callback, mark rollback-only and diagnose there.

Only after confirmed rollback/afterCompletion(ROLLED_BACK) and callback exit, start a separate fresh DEFERRED diagnostic transaction through another TransactionTemplate execution. It is read-only by behavior (SELECT only), without JDBC readOnly/enforceReadOnly flags. Its first SELECT rereads enabled status and current roles; then load resource and enforce current read access. Do not carry the failed operation's permission grant into diagnosis. Missing or currently inaccessible (including disabled/no-longer-entitled caller) returns the same generic 404 without state/version details. If accessible, compare current resource version against the immutable ORIGINAL caller If-Match: mismatch gives 412, unchanged version after snapshot failure gives sanitized 503. Diagnostic roles/access reads share the new snapshot. No domain action, CAS, reason insert, If-Match refresh or business replay occurs in diagnosis.

A normal already-authorized operation may still finish under its original permission snapshot; this does not grant a separate diagnostic read access after rollback. If rollback cannot be confirmed or commit acknowledgement is uncertain, do not enter stale-conflict diagnosis or assert rollback: close/discard as possible, return safe 503 and leave outcome uncertain. Successful commit returns only after execute completes.

No automatic retry of failed writes. A successful save always increments the request version, even if content is identical. All submitted commands include the version captured when the command was prepared; do not silently refresh it and replay. Status transitions increment versions, so a command from an earlier review cycle cannot match again.

Startup task ordering: T003 authors configuration/Git exclusions only; it does not execute a migrator or certify a database. T004–T006 define migrations. T013 supplies fixtures; T014 then performs first runtime WAL/PRAGMA verification, single migration execution and schema validation. Foundation wave A is T007/T008; wave B T009/T010 starts only after T007 model contracts are stable. T011 follows T010.

## Validation Strategy

- Domain: all four states × actions × owner/non-owner × both/single/no roles; blank/incomplete input; immutability; Unicode length boundaries; ownership and status cannot be assigned externally. No HTTP/database dependency.
- HTTP/security: real filter chain, Basic failures, union visibility, no sessions, custom-header CSRF protection, self-review prohibition, current permissions, distinct errors and identical missing/private responses. Check concealment ahead of stale version/state/input errors. Unknown properties cannot inject identity/status.
- Persistence: actual SQLite temp file, Flyway from new file and repeated startup/checksum validation, STRICT types, per-connection FK enforcement, scalar counting via length(text), constraints, immutable ownership, CAS, all retained feedback, latest reason projection. Force failure between CAS and feedback insert: both roll back. Verify close/reopen persistence, busy timeout, stale snapshot with unchanged resource version, read-only/unwritable paths and failure classification. Check successful commit before response and known pre-commit rejection leaves every table unchanged; lost commit acknowledgement is an uncertain whole-operation outcome.
- Controlled races: independent SQLite connections/DEFERRED transactions on the same real file and CountDownLatch or CyclicBarrier with bounded waits (no sleeps), pause both after loading the same version and before CAS. Release and commit chosen winner; then release loser. Repeat with winner order reversed for edit/edit, Approve/RequestChanges, edit/Submit in Draft, edit/Submit in ChangesRequested: eight ordering cases. Each asserts both operation results, final status/content/version, return-reason rows, no silent overwrite and no partial changes. Choose role/visibility fixtures deliberately: dual-role owner edit/Submit loser remains readable and gets 412; Reviewer-only loser after RequestChanges gets concealed 404, not a conflict revealing hidden state.
- Revocation: controlled commit before permission SELECT causes rejection; commit after first permission SELECT allows an authorized read to finish; a mutation may fail snapshot upgrade independently (fresh diagnosis first enforces current read access: revoked/inaccessible gives concealed 404; only currently readable unchanged-version failure gives 503), while the next operation rejects revoked permission. Test read and mutation. Avoid testing this with stale in-memory authorities.
- Stale commands: repeat each completed command, including after another return/resubmit cycle, with original If-Match. No extra mutation or feedback row.
- Diagnostic boundary regression (T033/T034): record callback failure, actual rollback completion, resource unbinding, and diagnostic transaction start in that order. Commit a changed resource/revocation between snapshots; prove diagnosis sees it, hides newly private data, executes zero writes and does not reenter the business callback. Test unconfirmed rollback/uncertain commit skips diagnosis safely.
- SC-005: actual two-person role evaluation using frozen scenarios/key; each participant must score 100% for role-relevant outcomes. Record responses. Unassessed until executed; no automated test substitutes for it.


Split lock tests into two distinct paths. Snapshot/DEFERRED-upgrade tests deliberately establish a read snapshot and allow a competing commit before upgrade; immediate BUSY/BUSY_SNAPSHOT is acceptable and no minimum elapsed wait is asserted. For busy-timeout validation use a separate low-level JDBC connection that has NOT established a read snapshot and explicitly attempts BEGIN IMMEDIATE while another independent connection holds the writer slot on the same WAL file. This is a storage test only, not a change to production DEFERRED transaction mode. Set a short positive busy_timeout (for example 100 ms), verify its value, and use barriers/latches plus statement-entry/completion events to keep the holder locked until the blocked attempt returns. Measure the native call with a monotonic clock; require actual bounded waiting consistent with that setting (document tolerance and a generous test deadline), not just a fast BUSY result. Immediate failure does not pass timeout validation. Release holder in finally after the contender completes; no arbitrary Thread.sleep, business retries or tag refresh. Assert BUSY and safe 503 translation/no mutations separately from the eight snapshot order cases.
## SQLite File Lifecycle and Failure Policy

This replaces the former server-database deployment assumption. Keep JDBC and three layers. A repository/DataSourceConfiguration owns Xerial SQLiteDataSource with independent short-lived connections; use Spring JDBC transaction binding, no extra pool/queue/application lock. CHANGEFLOW_DB_PATH is an absolute local persistent file path; reject :memory:, shared-cache and remote-file deployments. File existence alone is not a schema-validity check. External credentials remain Basic user credentials, not a nonexistent SQLite database login.

Before serving traffic: create/validate the configured parent with restrictive permissions, establish and verify WAL, then run a single Flyway migrator. Never run concurrent startup migrations, Flyway clean/baseline-on-error, or migrate an unknown existing schema silently. Configure every connection with foreign_keys=ON, read_uncommitted=OFF, synchronous=FULL, busy_timeout=5000 ms (external CHANGEFLOW_SQLITE_BUSY_TIMEOUT_MS) and transaction_mode=DEFERRED. Do not set readOnly/enforceReadOnly or unsupported READ_COMMITTED. Validate these settings with the actual driver.

Readers see snapshots and only one file-wide writer exists. Busy-handler waiting is bounded, can return early on snapshot upgrade, and is not a business retry. SQLITE_BUSY_SNAPSHOT rolls back and gets read-only diagnosis as described above; ordinary BUSY/LOCKED timeout is 503, not a fabricated version conflict. FULL/IOERR/READONLY/CANTOPEN fail safely as 503 with no paths or SQL. Preserve uncertain-commit handling. Never auto-restart on a new snapshot, replay a business action, or refresh If-Match. Revocation after authorization can invalidate a mutation snapshot independently of permission; permitted-to-finish is not guaranteed-to-commit.

Persistent main file and -wal/-shm/-journal sidecars stay outside Git. T003 plans .gitignore exclusions before test/production files can be created; this documentation revision does not edit .gitignore. Prefer a directory outside the repository; never delete sidecars to fix locks. Local filesystem and OS file permissions replace server-database credentials. Back up using a consistent SQLite backup procedure or verified offline checkpoint/close; do not copy only the main file during live WAL writes. No data import/delete is authorized here.

Validation gate: native loading, exact Flyway+driver combination, PRAGMA settings, migrations, persistence across restart, eight controlled order cases and storage failure scenarios must actually pass during implementation. Existing environment evidence is historical, not a new successful runtime check. Product rules, HTTP error schema/statuses and human SC-005 requirements remain unchanged.
## Complexity Tracking

Implementation safety follow-up (T049/T050, 2026-11-15): DataSourceConfiguration supplies SqliteTransactionManager for the approved unpooled, independently owned physical connections. Spring transaction completion remains in place; cleanup unbinds/closes rather than resets autoCommit, preventing Xerial's implicit COMMIT on unresolved cleanup. Pool logical-close/reset is outside this design and must not be substituted without reviewed eviction semantics. Controllers retain raw byte[] until the service invokes deferred payload parsing after visibility/action checks; strict UTF-8 decoding reports malformed sequences before JSON/domain validation. These are corrective implementation details of existing atomicity/Unicode/privacy decisions, not new product rules. See research.md and convergence-report.md for alternatives and actual regression evidence.

No constitutional violations. Flyway is justified by reproducible schema creation. No Testcontainers or external database is required; disk-file integration tests use the same JDBC engine as production. No performance goal or extra product capability introduced.





