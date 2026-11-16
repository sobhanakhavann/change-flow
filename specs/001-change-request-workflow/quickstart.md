# Quickstart and Validation Guide

## Current local startup configuration — 2026-11-16

The owner simplified local startup: no environment file or variables are required. The default database is `${user.dir}/changeflow.sqlite`, inside the application working directory, and logs use the console. Existing parent-directory validation still applies to an explicitly overridden path. Spring process environment variables may override the defaults; `.env` is not automatically imported. Earlier instructions requiring external storage or copying `.env` are superseded for local startup. Authentication, migrations and business rules are unchanged.

Updated 2026-11-15: workflow implementation and real file-backed SQLite validation are now available. The current checkpoint below supersedes all earlier task-limited historical observations.

## Current convergence-remediation checkpoint - 2026-11-15

T049-T052 completed. Final `gradlew.bat --offline --no-daemon build` succeeded in 45 seconds with **184 tests / 25 suites, zero failures/errors/skips**. This supersedes the earlier 172-test checkpoint, which is retained below as historical execution evidence. See [convergence follow-up](convergence-report.md#implementation-follow-up-t049-t052---2026-11-15) for the initial working-tree review, actual failing regressions, temporary negative controls, corrections and final results.

The configured SQLite datasource remains unpooled with independent physical connections. Its transaction manager closes/discards each owned connection after completion and never restores autoCommit during cleanup: Xerial can translate that reset into an unintended COMMIT after failed rollback. Before/after commit/rollback faults are tested through the production bean with native commit hooks. No pool lease is returned/reset; future pooling requires a reviewed eviction/reset policy and cannot silently reuse this cleanup assumption. No dependency or migration changed.

HTTP bodies now remain raw bytes until current resource visibility/action checks have passed. A strict UTF-8 decoder rejects malformed bytes instead of replacing them; valid explicit U+FFFD, supplementary scalars and combining sequences remain unchanged. Hidden/missing UUID resources retain identical 404 even with malformed payloads. Complete race/replay snapshots include all content, immutable owner, status/version and ordered feedback. Role-revocation tests preserve valid credentials and explicitly bracket rollback/physical close and the next diagnostic permission SELECT.

The original F1 cleanup and F2 HTTP regressions failed before correction. F3/F4 were missing coverage: expanded tests passed existing production behavior, and intentional temporary test-double mutations demonstrated failing content/old-permission assertions before being removed. They are not described as previously observed production-policy failures. Role grants are moved to isolated test-only sink identities to demonstrate revocation without deleting data. Test files remain in ignored unique build/test-databases directories; no clean or data deletion ran.


## Current implementation and operational guide

Java 21 (Temurin 21.0.13), Spring Boot 4.1.2, Wrapper 9.8.2, Xerial JDBC 3.53.4.0 (native SQLite 3.53.4) and Flyway core 12.4.0 are used. Actual schema tests load the native driver and execute migrations; this is execution evidence beyond the earlier documentation-only compatibility review. No PostgreSQL runtime driver, Docker, H2 or Testcontainers is required.

### Session-local toolchain and actual commands

The following process-only settings were used in this workspace; global settings were not changed:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle/session-home'
$env:GRADLE_RO_DEP_CACHE='C:/Users/Sobhan Akhavan/.gradle/caches'
.\gradlew.bat --offline --no-daemon test --tests '*SchemaIntegrationTests' --tests '*ChangeFlowApplicationTests'
.\gradlew.bat --offline --no-daemon test --tests '*Preparation*Tests' --tests '*Review*Tests'
.\gradlew.bat --offline --no-daemon test --tests '*RequestConcurrencyTests' --tests '*PermissionRevocationTests' --tests '*StaleCommandTests'
.\gradlew.bat --offline --no-daemon build
```

These commands have executed successfully during implementation. The final full-suite count and dependency inspection are recorded in the final evidence checkpoint below. Sandbox JAR access required running Gradle with approved build access; no global Gradle/PATH configuration was changed. Test files are retained under ignored `build/test-databases/<unique-id>/changeflow.sqlite`; no existing data was deleted and no `clean` command was run.

### Storage and startup

Provision a private writable local directory first, then set `CHANGEFLOW_DB_PATH` to an absolute local filename and optionally `CHANGEFLOW_SQLITE_BUSY_TIMEOUT_MS` (positive milliseconds, default 5000). The file persists across restart. UNC/network storage, URI/query filenames, `:memory:` and shared cache are unsupported. The application validates the existing directory/file and does not alter ACLs. Operators must enforce private OS permissions; Java writability checks do not establish an ACL audit.

Run `.\gradlew.bat bootRun` or the built Boot JAR with these variables. This operator startup command is guidance; automated startup evidence comes from real Boot context and loopback HTTP tests, not a separately launched production deployment. One startup migrator establishes WAL before V1-V3; existing migration checksums and unknown schemas fail closed. Do not baseline/clean/reset an existing database. Do not launch concurrent migration instances.

Each independent physical connection enforces `foreign_keys=1`, `read_uncommitted=0`, `synchronous=2` (FULL), positive `busy_timeout` and DEFERRED transactions. Tests use timeout 100 ms and verify PRAGMAs on multiple connections; production defaults to 5000 ms. WAL mode and engine version are queried against actual files. STRICT tables, keys, FK/unique/status/version/length constraints and supplementary Unicode scalar counts are exercised. Reopening the same file with a new datasource and repeated migration preserves committed content, versions and return reasons.

SQLite has one writer. Do not confuse `BUSY_SNAPSHOT` with a version conflict or evidence of timeout waiting. Failed snapshot upgrades roll back fully before a new SELECT-only diagnostic transaction reads fresh identity/roles first. Missing/private is 404; readable changed version is 412; readable unchanged version is 503. Unconfirmed rollback/commit skips diagnosis and returns safe 503. No business command is replayed. A separate BEGIN IMMEDIATE test with no old read snapshot holds the writer slot until the contender actually exhausts its configured wait; arbitrary sleeps are not used.

### Identity, transport and operator provisioning

No default users or public role-management API exists. An authorized operator uses a private SQLite connection with foreign keys enabled and a single explicit transaction to insert `app_user(id, username, password_hash, enabled)` and `user_role(user_id, role)` records. Generate canonical lowercase UUIDs and a salted BCrypt value using the approved Spring delegating password encoder; store its `{bcrypt}` prefix. Bind values rather than interpolate user input. Roles are exactly `Requester` and `Reviewer`; use distinct identities for reviewing another owner's request. Never put passwords, Basic headers or actual credential hashes in version control, command history, application logs or support reports. The test-only fixture password is not an operational account.

To revoke all access, the operator can bind the user ID in `UPDATE app_user SET enabled=0 WHERE id=?` and commit. Current roles/enabled are queried as the first SELECT in every operation transaction. Authentication uses a separate completed lookup without cached authorities. Revocation effective before the check rejects; already authorized operations may finish, with new checks on later operations and fresh diagnosis. Preserve user rows referenced by requests/reasons; do not delete accounts or feedback to revoke access.

Use Basic only over HTTPS outside loopback, and always send `X-ChangeFlow-Client: api` for modifications. No sessions/cookie login are created. CORS is restricted; custom headers provide the approved non-simple-request CSRF guard. `server.forward-headers-strategy=none`; untrusted client/forwarded identity or TLS assertions are ignored. Configure real servlet certificates or a secured TLS upstream hop and restrict backend exposure. Mock secure attributes and real loopback HTTP do not prove production certificate, handshake, proxy trust or remote network security.

### HTTP journey

With privately provisioned Requester and separate Reviewer credentials, use the [HTTP contract](contracts/http-api.md):

1. POST `/api/v1/requests` with the three content fields; save returned Location and original strong ETag. Incomplete content is valid in Draft.
2. PUT that Location with replacement content and the exact original `If-Match`; keep the new ETag. Omitted/null fields become empty strings. Limits count Unicode code points with no trim/normalization; invalid surrogates and U+0000 are rejected on every save.
3. POST `/submit` with `{}` and that tag; expect 204. GET obtains the next representation/tag. PendingReview cannot be edited.
4. The Reviewer POSTs `/request-changes` with `{"reason":"Please revise"}` and its original tag. Expect 204, then Reviewer-only detail becomes concealed 404. Owner GET sees latest reason.
5. Owner PUTs revised content, then submits using the corresponding new tag. Reviewer GET sees PendingReview again and approves with that tag, receiving 204.
6. Replaying the old completed tag yields 412 when still readable, or generic 404 if private. Approved cannot be edited; owner cannot review either way even with both roles. All earlier feedback remains stored; only latest is projected.

The complete journey is executed by `HttpWorkflowTests` through an actual random-port Boot server, real Basic authentication, JDBC and an on-disk SQLite file. MockMvc integration suites additionally exercise field/policy/error matrices through the real filter chain and repositories. No manual operator deployment or real TLS acceptance is inferred.

### Failure and backup operations

On lock timeout or storage failure, return generic 503 without SQL, paths or credentials. Do not automatically retry a command against a refreshed state. If acknowledgement is uncertain, an authorized GET can inspect current state; the entire transaction is committed or rolled back, never a partial request/feedback update. Resolve disk capacity/permissions/lock ownership operationally. Do not remove database sidecars, reset files or run Flyway clean to recover. Use SQLite's online backup API/tool or stop the service, close all connections and make a consistent backup; a live main-file-only copy can omit committed WAL content. Protect backup permissions and validate restore separately before relying on it.

Git exclusions cover database main files and `-wal`, `-shm`, `-journal` sidecars, build output and local environment settings. Tests retain their files, close connections/contexts, and never touch configured operational storage.

### Automated validation evidence

Actual independent-connection tests cover both winner orders for edit/edit, Approve/RequestChanges, edit/Submit from Draft and edit/Submit from ChangesRequested. They assert outcome, final version/state/content, retained reasons, one execution per command, rollback completion and a fresh connection/snapshot before diagnosis. Tests distinguish timeout waiting, snapshot failure, current-access 404, readable mismatch 412 and unchanged-readable 503. Revocation before checks, after a successful grant and before fresh diagnosis is exercised.

Real read-only SQLite write rejection and fail-closed invalid/unprovisioned path checks run safely. FULL/IOERR, aborted commit, lost commit acknowledgement and rollback-acknowledgement failures are injected at repository/transaction boundaries around real SQLite transactions; these are not physical disk exhaustion, damaged storage, process-kill or filesystem ACL experiments. Atomic whole outcomes and no replay/diagnosis after uncertainty are checked.


## Original workflow build evidence before convergence remediation - 2026-11-15

Full Java 21 offline build executed successfully with **172 tests in 24 suites, zero failures, errors or skips**. The final rerun following policy consolidation is recorded in tasks.md. Boot JAR and regular JAR were assembled. Commands used the session variables above:

```powershell
.\gradlew.bat --offline --no-daemon build
.\gradlew.bat --offline --no-daemon dependencyInsight --dependency sqlite-jdbc --configuration runtimeClasspath
.\gradlew.bat --offline --no-daemon dependencyInsight --dependency flyway-core --configuration runtimeClasspath
```

Dependency inspection resolved Xerial 3.53.4.0 and Flyway core 12.4.0. The engine query in schema tests returned SQLite 3.53.4. Native library loading, WAL and per-connection PRAGMAs were established by execution, not inferred from version selection. `PersistenceRestartTests` closes and reopens two real application contexts against the same file and verifies content/status/version/feedback retention. `HttpWorkflowTests` runs the full quickstart journey through real loopback HTTP.

`git check-ignore` matched `.sqlite` and `.db` main/-wal/-shm/-journal candidates; no tracked database/sidecar files were found. Whitespace checks passed. Initial test runs revealed and corrected independent-writer fixture binding, test configuration component-scan collisions and unsupported-method error handling (generic safe 405 rather than accidental 500). Final evidence concerns corrected runs; initial failures are not hidden. There was no commit, push, destructive cleanup or global setting change.

Layer review confirmed seven approved routes only, no UI/delete/history/admin API, and no new speculative dependency. Pure models, content/transition rules and access policy do not import Spring, JDBC, Security or HTTP. The domain service owns transaction coordination outside callbacks and calls repositories; controllers handle transport and deferred parsing. Shared role/ownership checks now live in the pure access policy, preserving the approved action/input/version/state precedence.


## Historical task-limited checkpoints

The following notes describe earlier stages only. Statements that JDBC/startup/T013+ had not run are historical and superseded by the current evidence above.

## T011-T012 security/HTTP validation - 2026-11-15

Implemented security/filter configuration, safe errors and explicit version precondition helper, plus isolated tests. Reused only process-local Java 21/Gradle cache settings recorded below.

~~~powershell
.\gradlew.bat --offline --no-daemon compileJava test --tests 'com.example.changeflow.controller.SecurityConfigurationTests' --tests 'com.example.changeflow.controller.VersionAndErrorTests' --tests 'com.example.changeflow.domain.*' --tests 'com.example.changeflow.repository.IdentityRepositoryTests'
~~~

Final result: BUILD SUCCESSFUL (16s), 129 tests total (9 security, 16 version/error, 104 previous units), no failures/errors/skips. Initial run: two failures (duplicate authentication-provider fallback lookup; empty-header conversion in a test probe). Both corrected, followed by successful reruns; final tests additionally cover unknown-route 404 and strict HTTP optional whitespace. The known Mockito JVM class-sharing notice remains harmless. Execution outside the sandbox was approved for its known JAR access restriction; no global settings changed. Git diff --check passed.

Security tests instantiate a small MVC/security context with mocked IdentityRepository, not the Boot application or DataSourceConfiguration. They verify BCrypt matching, per-request lookup, UUID-only/credential-free principal, explicit 401 challenge, no session/cookie authentication, no-store, header CSRF guard, cross-origin/preflight denial, safe storage failure and rejection of spoofed identity/forwarded TLS headers.

Version/error tests use probe routes only (not ChangeFlow story endpoints). They verify exact tag syntax, absent/empty/duplicate/weak/wildcard/overflow/stale/wrong-resource cases, signed-long limits, safe problem schema and a simulated common missing/private outcome before tag parsing. They do not prove real service privacy ordering. T020/T021 and story integration tests must establish it.

Operational limits:
- Password provisioning must use the selected delegating encoder's {bcrypt} format; no plaintext credentials or default account is added.
- Direct servlet TLS is required for non-loopback clients; filter rejects remote plaintext before authentication. Loopback plaintext is for local development. HTTP clients must use HTTPS before transmitting Basic credentials; a server rejection cannot retroactively encrypt a transmitted header.
- server.forward-headers-strategy=none prevents arbitrary Forwarded/X-Forwarded-* from being used as transport/identity evidence. Configure actual certificates/connector or a secured upstream TLS hop and restrict direct backend access before deployment. No proxy trust rule/certificate is invented or tested here.
- A simulated secure request verifies only branching on servlet transport attributes. It does not establish certificate validation, handshake security, network/proxy correctness or deployment readiness.
- Real IdentityRepository/SQLite correctness, current service-role lookup order, migrations, startup, WAL/schema and full-program behavior remain unvalidated. No T013+ work or real application/database startup occurred.

T011/T012 are complete at the authored-code/isolated-test scope. Deferred integration/operational evidence is not claimed as passing.
## T009-T010 validation - 2026-11-15

Scope: pure RequestAccessPolicy, uncached JDBC IdentityRepository and their isolated tests only. Both tasks are implemented; actual database/integration acceptance remains pending. Reused the recorded process-local Java 21 and Gradle cache variables; no global settings changed.

~~~powershell
.\gradlew.bat --offline --no-daemon compileJava test --tests 'com.example.changeflow.domain.*' --tests 'com.example.changeflow.repository.IdentityRepositoryTests'
~~~

Final result: BUILD SUCCESSFUL (14s); compilation and tests executed. RequestAccessPolicyTests: 66 tests; IdentityRepositoryTests: 10; existing domain tests: 28. Total 104, no failures/errors/skips. An initial successful run had a Mockito varargs null compiler warning; explicit String null removed it before the final successful run. The remaining JVM class-sharing notice is from Mockito instrumentation, not an application/test failure. Execution outside the sandbox was approved for the known JAR-access limitation.

The policy matrix covers every status, owner/non-owner, enabled/disabled and all four role combinations. Repository unit tests prove parameter binding, fresh query calls, credential redaction, result/connection closure, operation-transaction guard and transaction-bound connection use using mocks only. They execute no real SQL or engine.

T010's implementation requires its enabled/role SELECT to be first inside each fresh service/diagnostic transaction and prevents authentication from joining that transaction. Enforcing call order across service/resource queries and demonstrating actual SQLite visibility/revocation still need T020/T013/T014/T034. No Security authentication adapter (T011), Spring context, startup, migration, real SQLite JDBC validation, WAL/schema check or full suite was run. No mock test result should be presented as SQLite acceptance.
## T007–T008 unit validation — 2026-11-15

Only plain domain models/content validation and dedicated unit tests were added. Using the same process-scoped Java 21 and Gradle cache settings recorded below:

~~~powershell
.\gradlew.bat --offline --no-daemon compileJava test --tests 'com.example.changeflow.domain.ContentRulesTests' --tests 'com.example.changeflow.domain.DomainModelsTests'
~~~

Result: BUILD SUCCESSFUL; compileJava/compileTestJava and the filtered tests executed. ContentRulesTests: 20 tests; DomainModelsTests: 8 tests; total 28, no failures, errors or skips. The command ran outside the sandbox under approval for its previously established JAR-access restriction; no global environment change.

The filter excludes ChangeFlowApplicationTests. No Spring application context, datasource connection, Flyway migration or WAL/schema validation was executed. A full test/build acceptance claim would require unfinished foundation fixtures (T013/T014); those tests were not run. T009 and subsequent work remain untouched. Existing planning/setup paragraphs below are historical scope-specific evidence, not claims that later runtime checks passed.
## Current setup execution evidence — 2026-11-15

Scope: I3 wording correction plus T001–T003 only. The historical section below describes earlier planning, not the current execution status.

| Task | Actual evidence | Status |
| --- | --- | --- |
| T001 | Temurin Java/Javac 21.0.13 available; Wrapper 9.8.2 runs on that JDK; Gradle javaToolchains identifies it as a JDK | Complete |
| T002 | Approved dependencies declared; runtime/test graphs resolved and actual 57/93 artifact files resolved offline; Xerial nativeLoaded=true, driver=3.53.4.0 without opening a database | Complete |
| T003 | Path/timeout/connection and migration startup wiring authored; Java compilation/resource processing pass; all 13 Git-ignore examples pass | Complete as configuration only |

Actual command sequence and results:

- Direct Temurin bin/java.exe -version and bin/javac.exe -version: 21.0.13. Default system Java 25 was not changed.
- Initial GRADLE_USER_HOME at the existing user cache failed with AccessDenied on Wrapper's lock file. Copied the existing 9.8.2 distribution into .gradle/session-home; Wrapper --version succeeded. Nothing was deleted or upgraded.
- Initial runtime dependency request without the populated cache could not resolve Boot plugin 4.1.2. A PowerShell cache copy hit Windows long-path/lock errors; robocopy /E /XF *.lock /R:0 /W:0 copied cache content without deleting source or destination files. Resolution ultimately succeeded using GRADLE_RO_DEP_CACHE pointing to the EXISTING caches parent (not modules-2) as read-only input.
- gradlew.bat --offline --no-daemon javaToolchains dependencies --configuration runtimeClasspath: BUILD SUCCESSFUL; Java 21 JDK discovered. Boot 4.1.2, Spring 7.0.10, Security 7.1.2, Flyway 12.4.0, sqlite-jdbc 3.53.4.0 resolved.
- gradlew.bat --offline --no-daemon dependencies --configuration testRuntimeClasspath: BUILD SUCCESSFUL; both approved test starters and JUnit 6.0.3 resolved.
- A temporary ignored init script .gradle/setup-validation.gradle resolved configuration.files (rather than relying only on a dependency report): runtimeClasspath 57 artifact files, testRuntimeClasspath 93; BUILD SUCCESSFUL. Its first BOM-encoded version failed Groovy parsing; rewriting without BOM resolved that temporary probe issue.
- A temporary ignored NativeProbe invoked SQLiteJDBCLoader.initialize()/getVersion(): nativeLoaded=true, driver=3.53.4.0. It never requested a JDBC connection. The direct javac probe emitted AccessDenied during JAR cleanup in the sandbox; this is not reported as a successful compilation check.
- gradlew.bat --offline --no-daemon compileJava processResources first failed inside the sandbox with AccessDenied to a dependency JAR. The identical offline command was automatically approved outside the sandbox and then BUILD SUCCESSFUL (both tasks executed). No application context, startup strategy, Flyway migration, SQL or tests were executed.
- Git using its explicit installed path plus command-scoped safe.directory: check-ignore accepted var/changeflow/probe and all .db/.sqlite/.sqlite3 main/-wal/-shm/-journal examples; diff --check passed. No global safe.directory or Java/Gradle setting was modified.
- requirements.md gate: 16 checked, 0 unchecked; gate file untouched.

Each Gradle invocation used process-scoped settings only:

~~~powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle/session-home'
$env:GRADLE_RO_DEP_CACHE = 'C:/Users/Sobhan Akhavan/.gradle/caches'
~~~

The paths describe this machine/session, not committed environment defaults. Temporary probes and copied caches remain under ignored .gradle. No network download success is claimed.

Configuration behavior: CHANGEFLOW_DB_PATH is required and must identify an absolute local filename in an existing writable directory. Operators provision that directory privately, outside the checkout, with service/operator-only OS ACLs; existing directory permissions are never changed by setup. The bean validates access and rejects obvious UNC/URI/in-memory paths; deployments must not put it on mapped network storage. The configured startup strategy establishes WAL before the single migrator, but has not been invoked. Default busy timeout is 5000 ms and positive values are required. Application users/password hashes and authentication implementation remain later tasks.

## Historical design-stage evidence

- Read existing build.gradle: Java 21 toolchain, Boot 4.1.2, dependency-management 1.1.7; existing Wrapper is Gradle 9.8.2.
- Historical planning observation: java -version reported 25.0.4; gradlew.bat --version failed before Gradle startup creating C:\.gradle wrapper lock parent.
- This revision reviewed official Boot BOM, pinned Xerial/Flyway sources and SQLite locking documentation; see research.md. Documented compatibility is not an executed integration pass.
- No dependency resolution, native driver load, SQLite file creation, Flyway migration, application startup, test/build, or human evaluation ran in this revision.

## Prerequisites and future setup

Select an actual JDK 21 and writable Gradle cache; do not change the pinned Wrapper/Boot versions to match the machine Java. SQLite is embedded in org.xerial:sqlite-jdbc 3.53.4.0; no database server, Docker, Testcontainers or separately installed SQLite CLI is required for application/tests. Verify actual native library loading on the target OS; writable native-extraction temp directory may be necessary.

T003 only authors configuration and Git protection. T014 first executes/validates WAL, PRAGMAs, migrator and schema after T004–T006/T013; no setup-only task certifies startup. Implementation T002 adds Xerial JDBC plus Flyway starter/core and removes planned PostgreSQL/Testcontainers dependencies. T003 implements file configuration, PRAGMAs and Git exclusions before any runtime file creation. The current setup section above records the now-authored configuration/dependencies; runtime migration validation remains pending T014.

Set CHANGEFLOW_DB_PATH to a dedicated absolute persistent path on a local disk outside the checkout (for example a ChangeFlow directory under LOCALAPPDATA). Do not use :memory:, build/, classpath resources, shared-cache URIs, NFS/SMB or a network volume. On Windows, preserve proper absolute drive/path handling when creating jdbc:sqlite: URLs, including spaces. Configure optional CHANGEFLOW_SQLITE_BUSY_TIMEOUT_MS (default 5000 milliseconds). This is a lock-wait bound, not an HTTP latency or capacity promise.

Startup implementation must validate/create the parent as appropriate, restrict OS permissions, establish/verify WAL and run only one Flyway migrator before traffic. Every connection must verify foreign_keys=ON, synchronous=FULL, read_uncommitted=OFF and the chosen busy timeout; DEFERRED is the transaction mode. Fail closed on missing path, inaccessible storage, schema mismatch or migration failure; no in-memory fallback. Existing files must never be deleted, cleaned or automatically rebaselined.

## Git and data safety (implementation work still required)

Add /var/changeflow/ plus *.db, *.sqlite, *.sqlite3 and each format's -wal, -shm, -journal patterns to .gitignore before creating files. Arbitrary file names must be outside the checkout or explicitly excluded. Verify the configured path and sidecar paths with git check-ignore when available; ignore rules do not untrack existing files. Report tracked data rather than deleting/untracking automatically. T003 now supplies these ignore rules; this setup execution creates no runtime database file.

Never delete a live -wal/-shm/-journal file to clear locking. Do not copy the main file alone while live WAL writes exist. Use SQLite's online backup facility or stop/close all access with a verified checkpoint and then back up consistently. Restore procedures must preserve schema/content/versions/feedback; protect backups like the database. No backup/restore command that deletes existing data is authorized by this document.

## Future commands (not executed successfully here)

From repository root after implementation, set JAVA_HOME to an installed JDK 21 and put its bin first in PATH. PowerShell:

~~~powershell
$env:GRADLE_USER_HOME = Join-Path $env:LOCALAPPDATA 'ChangeFlow/gradle-cache'
java -version
.\gradlew.bat --version
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat dependencies --configuration testRuntimeClasspath
.\gradlew.bat test
.\gradlew.bat build
~~~

Record resolved sqlite-jdbc 3.53.4.0 and flyway-core 12.4.0 and confirm there is no PostgreSQL database module/Testcontainers. Record JDBC metadata, SELECT sqlite_version(), verified PRAGMAs, migration/checksum results and actual failures. Do not assume external tooling's SQLite version is the bundled engine's version.

After T003 configuration, T004–T006 migration scripts and successful T014 startup/WAL/schema verification exist:

~~~powershell
$env:CHANGEFLOW_DB_PATH = Join-Path $env:LOCALAPPDATA 'ChangeFlow/data/changeflow.sqlite'
$env:CHANGEFLOW_SQLITE_BUSY_TIMEOUT_MS = '5000'
.\gradlew.bat bootRun
~~~

These settings are now wired as configuration; successful startup behavior is still unverified until T014. No SPRING_DATASOURCE_USERNAME/PASSWORD is required for SQLite; Basic application identities remain database-backed and operator-provisioned with BCrypt hashes outside the public API. Provide owner, reviewer, dual-role, unrelated Requester and revocable fixtures through safe operator tooling; never commit passwords or introduce role-management endpoints. Use TLS outside localhost and required X-ChangeFlow-Client: api on unsafe requests. curl.exe --user username can prompt for a password instead of placing it in history.

## Future validation scenarios

Use [HTTP contract](contracts/http-api.md), [model](data-model.md) and [plan](plan.md) for normative behavior.

1. New temp file: Flyway creates schema once; second startup validates without destructive changes; checksum/unknown-schema errors fail startup. Check STRICT types, foreign keys on every connection and Unicode length consistency. Close all connections/restart an application context on the same file and confirm committed requests, versions, users and every return reason survive.
2. Owner creates incomplete Draft, edits within limits, fails incomplete Submit, completes and submits with current ETag. Reviewer returns with nonblank reason; blank/overlength reason fails unchanged. Reviewer-only GET after return is concealed 404; owner sees latest reason.
3. Owner revises/resubmits; multiple return cycles preserve all reason rows; Reviewer reads latest when access returns, then approves. Approved remains immutable. No history display/delete/admin API is introduced.
4. Full role/state/read/action matrix, including no self-review, current Requester for returned edits and union read policy. Missing/private references return identical 404 ahead of state/version/payload details.
5. Replay every completed command with original If-Match, including after another review cycle: reject with no duplicate transition/reason. Never refresh the tag automatically.
6. Field max/max+1 for ASCII, supplementary Unicode and combining sequences; NUL/unpaired surrogate rejection; incomplete saved content allowed within lengths.
7. Eight controlled race orders on one real file with separate physical connections: both snapshot version reads first, chosen winner commits before loser upgrade. Assert readable loser 412 versus hidden Reviewer 404 after rollback and fresh read-only diagnosis; inspect both results, final status/content/version, all reason rows, zero silent overwrite and zero partial writes.
8. Separate timeout validation from DEFERRED/snapshot upgrade failures: use the no-read-snapshot BEGIN IMMEDIATE storage-only scenario described in plan.md. A holder connection retains the writer slot until the contender finishes. Verify short positive busy_timeout, coordinate via barriers/statement events, measure actual bounded wait with monotonic clock and documented tolerance/deadline, and reject immediate failure as timeout evidence. No arbitrary sleeps or command replay. Snapshot errors have no minimum-wait assertion; unrelated-row snapshot invalidation with unchanged request version yields 503 only if fresh current access still permits the diagnostic read; otherwise concealed 404.
9. Revoke before first operation SELECT (reject) and after granted snapshot (old read may finish; mutation can independently fail snapshot upgrade). A failed operation diagnostic must independently reread current roles/access and conceal newly private resources; the next operation must observe revocation. Test no role cache, no earlier resource SELECT and no long-lived reused snapshot.
10. Force failure between CAS and reason insert and explicit aborted commit to prove rollback. Separately simulate lost commit acknowledgement as uncertain whole outcome; test safe read-only inspection, never automatic command replay. Exercise read-only/unwritable path, I/O/disk-full classification with controlled fixture fault injection rather than filling or modifying production disks.

Temp database fixtures are unique per test case, same file for each race's connections. No :memory:/shared-cache substitute or rollback-only enclosing test transaction. Close fixture handles before cleanup, and verify cleanup stays within that test's temporary directory; never delete the configured persistent production file.

The service orchestrator runs outside all transaction callbacks and has no enclosing @Transactional boundary. Reject accidental ambient transactions rather than joining them. Execute each mutation exactly once through TransactionTemplate with a Spring JDBC transaction manager; authentication has already finished. A callback failure must escape execute so rollback completes and JDBC resources are unbound before the orchestrator classifies it. Do not merely catch inside the callback, mark rollback-only and diagnose there.

Only after confirmed rollback/afterCompletion(ROLLED_BACK) and callback exit, start a separate fresh DEFERRED diagnostic transaction through another TransactionTemplate execution. It is read-only by behavior (SELECT only), without JDBC readOnly/enforceReadOnly flags. Its first SELECT rereads enabled status and current roles; then load resource and enforce current read access. Do not carry the failed operation's permission grant into diagnosis. Missing or currently inaccessible (including disabled/no-longer-entitled caller) returns the same generic 404 without state/version details. If accessible, compare current resource version against the immutable ORIGINAL caller If-Match: mismatch gives 412, unchanged version after snapshot failure gives sanitized 503. Diagnostic roles/access reads share the new snapshot. No domain action, CAS, reason insert, If-Match refresh or business replay occurs in diagnosis.

A normal already-authorized operation may still finish under its original permission snapshot; this does not grant a separate diagnostic read access after rollback. If rollback cannot be confirmed or commit acknowledgement is uncertain, do not enter stale-conflict diagnosis or assert rollback: close/discard as possible, return safe 503 and leave outcome uncertain. Successful commit returns only after execute completes.
Split lock tests into two distinct paths. Snapshot/DEFERRED-upgrade tests deliberately establish a read snapshot and allow a competing commit before upgrade; immediate BUSY/BUSY_SNAPSHOT is acceptable and no minimum elapsed wait is asserted. For busy-timeout validation use a separate low-level JDBC connection that has NOT established a read snapshot and explicitly attempts BEGIN IMMEDIATE while another independent connection holds the writer slot on the same WAL file. This is a storage test only, not a change to production DEFERRED transaction mode. Set a short positive busy_timeout (for example 100 ms), verify its value, and use barriers/latches plus statement-entry/completion events to keep the holder locked until the blocked attempt returns. Measure the native call with a monotonic clock; require actual bounded waiting consistent with that setting (document tolerance and a generous test deadline), not just a fast BUSY result. Immediate failure does not pass timeout validation. Release holder in finally after the contender completes; no arbitrary Thread.sleep, business retries or tag refresh. Assert BUSY and safe 503 translation/no mutations separately from the eight snapshot order cases.
## Reporting

Report runtime/migration/test/build evidence separately from design review and human acceptance. Unknown commit outcome is not rollback proof; no success before commit. Lock waits do not authorize business-command replay. No new performance/capacity target is introduced. Database and native-driver validation remain outstanding until implemented and executed.




## Scope cleanup — 2026-11-16

The additional human-evaluation package and its four tooling-only tests have been removed. Earlier counts in this file describe prior runs, not a fresh build of the simplified source. See scope-change.md for the current validation result. Normal application provisioning and HTTPS requirements above remain applicable.


### Current scope-cleanup validation — 2026-11-16

Scope-cleanup build on 2026-11-16: `gradlew.bat --offline --no-daemon build` succeeded with Java 21; **184 tests in 25 suites, zero failures/errors/skips**. The four removed tests belonged only to the withdrawn evaluation tooling. Product regression tests remain.
