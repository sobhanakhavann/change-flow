# Data Model and Transaction Design

Updated 2026-11-15 for owner-approved file-based SQLite; [spec.md](spec.md) product rules unchanged. Descriptions only: no executable migrations created.

## Storage and connection invariants

One persistent UTF8 SQLite file at externally configured absolute CHANGEFLOW_DB_PATH; no :memory:, shared cache, attached business database or remote filesystem. Schema uses STRICT tables. Per physical connection: foreign_keys=ON, read_uncommitted=OFF, synchronous=FULL, busy_timeout=5000 ms by default (configurable). Startup establishes/verifies WAL before one Flyway migrator. All access uses configured Xerial SQLiteDataSource, separate connections, Spring JDBC transaction binding and DEFERRED/default SERIALIZABLE snapshot semantics. No READ_COMMITTED override or JDBC readOnly transaction enforcement.

The file and sidecars must remain in durable local storage. See research.md/quickstart.md for Git exclusions, permissions and failure recovery; never clear sidecars to resolve locks.

Implementation cleanup clarification (T049, 2026-11-15): the selected connections are unpooled and physically owned per operation. SqliteTransactionManager unbinds/closes them on completion without setAutoCommit(true), which could implicitly commit pending CAS after failed rollback. Unknown cleanup outcomes never authorize diagnosis or business replay. This changes no schema, aggregate invariant, access policy or version behavior; actual fault-injection evidence is recorded in convergence-report.md.

## Identity

app_user:
- id: TEXT NOT NULL PRIMARY KEY, canonical lowercase UUID, never reused.
- username: TEXT NOT NULL UNIQUE, BINARY case-sensitive comparison.
- password_hash: TEXT NOT NULL, encoded BCrypt hash.
- enabled: INTEGER NOT NULL CHECK IN (0,1).

user_role:
- user_id: TEXT NOT NULL REFERENCES app_user(id), deletion restricted.
- role: TEXT NOT NULL CHECK IN ('Requester','Reviewer').
- PRIMARY KEY (user_id, role).

No removal of referenced users and no public account/role API. Authentication lookup is uncached and finishes before service operation. First SELECT in each operation transaction loads enabled/current roles together and establishes the fresh snapshot; no resource read precedes it. Operator commits define effective revocation. No role means no protected action/read grant.

## Change request aggregate

change_request (STRICT):

| Field | Type / invariant |
| --- | --- |
| id | TEXT NOT NULL PRIMARY KEY; server-generated canonical lowercase UUID, never reused |
| requester_id | TEXT NOT NULL REFERENCES app_user(id); immutable after creation; deletion restricted |
| title | TEXT NOT NULL DEFAULT ''; CHECK length(title) <= 200 |
| change_description | TEXT NOT NULL DEFAULT ''; CHECK length(change_description) <= 5000 |
| change_reason | TEXT NOT NULL DEFAULT ''; CHECK length(change_reason) <= 2000 |
| status | TEXT NOT NULL CHECK IN ('Draft','PendingReview','ChangesRequested','Approved') |
| version | INTEGER NOT NULL CHECK version >= 0; starts at 0; increments for every accepted edit or transition |

INTEGER storage is signed 64-bit. Reject overflow before 9223372036854775807 could be incremented; never wrap, reset or reuse versions. No timestamps/submission counter required. Index requester_id and status; BINARY canonical UUID text ordering supports the unchanged keyset cursor contract. Java binds UUID strings, not PostgreSQL UUID objects, and long versions, not floating point.

Blank/incomplete Draft/ChangesRequested content is allowed; Submit requires all three fields nonblank. Missing/null create/replacement fields become empty strings, wrong types fail. No truncation, trimming or normalization. Reject NUL and malformed surrogates before SQL; SQLite length(text) then agrees with Java Unicode scalar counts (supplementary counts one, combining marks separately). Domain validation handles exact Java whitespace policy; SQL CHECK is additional integrity, not complete business authorization.

## Return reasons

return_reason (STRICT):

| Field | Type / invariant |
| --- | --- |
| request_id | TEXT NOT NULL REFERENCES change_request(id); deletion restricted |
| request_version | INTEGER NOT NULL CHECK request_version > 0; resulting request version for this RequestChanges |
| reviewer_id | TEXT NOT NULL REFERENCES app_user(id); non-owner enforced by domain |
| reason | TEXT NOT NULL; nonblank under contract; CHECK length(reason) <= 2000 |

PRIMARY KEY (request_id, request_version). All components explicitly NOT NULL; do not rely on SQLite's legacy nullable-primary-key behavior. Append only; no cascading deletion, reason edits, retention purge or history endpoint. Query greatest request_version for latest reason. Every old reason survives edits/resubmission/approval. Current read policy controls owner/latest and Reviewer visibility; feedback never grants independent access.

## Allowed transitions

Draft --Submit--> PendingReview; ChangesRequested --Submit--> PendingReview; PendingReview --Approve--> Approved; PendingReview --RequestChanges--> ChangesRequested.

Only owner/current Requester edits Draft/ChangesRequested. No PendingReview edit, Approved mutation, self-Approve/self-RequestChanges, reopening or deletion. Pure domain rules enforce these invariants; repositories expose bounded SQL, not arbitrary ownership/status setters.

## Atomic write and locking protocol

1. Spring-managed DEFERRED transaction starts without a writer reservation. First SELECT reads enabled/current roles, establishing the operation snapshot. Then load resource; conceal missing/private before action/payload/version/state details.
2. Check original If-Match; apply pure rules. UPDATE matches id+version and increments version for every accepted save. Exactly one row must update. RequestChanges additionally inserts reason at resulting version in the same transaction.
3. SQLite allows only one writer for the entire file. Known failure rolls back request and reason together; commit before HTTP success. Never nest BEGIN/COMMIT inside Spring transactions or migration scripts.
4. A rival commit after the read snapshot may cause SQLITE_BUSY_SNAPSHOT on upgrade rather than zero CAS rows. Roll back and close old snapshot; read-only fresh diagnosis with freshly read enabled/current roles and current read access: hidden/missing → 404, readable changed version → 412, unchanged version while currently readable → generic 503. Diagnosis does not reexecute the command.
5. Other BUSY/LOCKED/storage failures roll back/close and use sanitized existing 503; timeout is not a stale-version proof. busy_timeout is bounded waiting, may return early and does not cure stale snapshots. Failed diagnostic access yields generic 503 with no resource metadata.
6. Lost commit acknowledgement means unknown whole-operation outcome. Never claim definite rollback in that case; never restart business transaction, retry COMMIT automatically, or refresh a command version. Atomicity still forbids partial request/reason commits.

Default isolation is snapshot/serialized writes, not per-statement READ COMMITTED. A revocation committed before first SELECT is seen; revocation afterward does not retroactively fail permission, but a write upgrade can independently fail because the file snapshot changed. A read already authorized may finish its snapshot; next operation checks again.

The service orchestrator runs outside all transaction callbacks and has no enclosing @Transactional boundary. Reject accidental ambient transactions rather than joining them. Execute each mutation exactly once through TransactionTemplate with a Spring JDBC transaction manager; authentication has already finished. A callback failure must escape execute so rollback completes and JDBC resources are unbound before the orchestrator classifies it. Do not merely catch inside the callback, mark rollback-only and diagnose there.

Only after confirmed rollback/afterCompletion(ROLLED_BACK) and callback exit, start a separate fresh DEFERRED diagnostic transaction through another TransactionTemplate execution. It is read-only by behavior (SELECT only), without JDBC readOnly/enforceReadOnly flags. Its first SELECT rereads enabled status and current roles; then load resource and enforce current read access. Do not carry the failed operation's permission grant into diagnosis. Missing or currently inaccessible (including disabled/no-longer-entitled caller) returns the same generic 404 without state/version details. If accessible, compare current resource version against the immutable ORIGINAL caller If-Match: mismatch gives 412, unchanged version after snapshot failure gives sanitized 503. Diagnostic roles/access reads share the new snapshot. No domain action, CAS, reason insert, If-Match refresh or business replay occurs in diagnosis.

A normal already-authorized operation may still finish under its original permission snapshot; this does not grant a separate diagnostic read access after rollback. If rollback cannot be confirmed or commit acknowledgement is uncertain, do not enter stale-conflict diagnosis or assert rollback: close/discard as possible, return safe 503 and leave outcome uncertain. Successful commit returns only after execute completes.
## Persistence and race validation

Real driver and shared on-disk temp file, separate connections/threads/transactions. Migrate once before concurrent test work. Check new-file and repeated Flyway startup, checksum mismatch fail-closed, STRICT/FK/checks on every connection, length limits, canonical UUID mapping, restart persistence and preserved reasons.

Eight controlled cases: both winner orders for edit/edit, Approve/RequestChanges, edit/Submit Draft, edit/Submit ChangesRequested. Both read same snapshot/version; release winner, wait for commit, then allow loser upgrade. Assert readable loser 412 versus newly hidden Reviewer loser 404 after rollback/diagnosis; no partial changes, lost content or duplicate reasons. No single connection, shared-cache :memory:, sleeps or enclosing rollback-only test wrapper.

Additional tests: separate no-read-snapshot BEGIN IMMEDIATE writer-slot timeout test as specified in plan.md (independent of production DEFERRED upgrade tests); stale snapshot caused by unrelated row/role change produces 503 only if request version is unchanged AND the caller still has current read access; lost current access instead requires concealed 404, as in Plan and T034; failure after CAS before reason insert; explicit commit abort versus lost acknowledgement; read-only/unwritable storage; close/reopen file across app contexts with all content/versions/reasons retained. Assert bounded completion with generous test deadline, not exact timing/performance claims. Never use test cleanup paths that could reach the configured production file.



