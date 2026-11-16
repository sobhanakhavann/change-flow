# ChangeFlow


HTTP API for creating, submitting, returning, revising and approving change requests. Java 21, Spring Boot 4.1.2, Gradle Wrapper 9.8.2 and persistent file SQLite via JDBC/Flyway. No database server or Docker is needed.

## Status

The workflow and automated integration tests are implemented. Real SQLite migrations, WAL, independent-connection races, rollback, current authorization and loopback HTTP have been exercised. Automated results do not establish production deployment readiness.

Convergence fixes T049-T052 are implemented: safe physical-connection cleanup after transaction failures, strict deferred UTF-8 decoding, full-state race/replay assertions and current-role revocation coverage. Current Java 21 build: **184 tests in 25 suites, no failures/errors/skips**. See [scope-change.md](specs/001-change-request-workflow/scope-change.md). See [remediation evidence](specs/001-change-request-workflow/convergence-report.md). SQLite remains unpooled; cleanup does not restore autoCommit or return unresolved connections to a pool. Physical disk failure and production TLS remain unverified.

## Run locally

Use Java 21 and provision a private, writable directory on local disk. Set an absolute persistent filename; the application does not create parent directories, default accounts or an in-memory fallback.

```powershell
$env:CHANGEFLOW_DB_PATH='C:/private/changeflow/changeflow.sqlite'
$env:CHANGEFLOW_SQLITE_BUSY_TIMEOUT_MS='5000'
.\gradlew.bat bootRun
```

This is an operator example, not a claim that the shown path exists. First startup establishes WAL then applies V1-V3 once; subsequent startups validate migrations and preserve data. Only one application/migrator instance should own startup. Provision BCrypt-backed users and roles as described in [Quickstart](specs/001-change-request-workflow/quickstart.md). Never commit database files, sidecars or credentials.

Use HTTPS for Basic Authentication outside local loopback. No cookie/session login is supported. All modifying requests require `X-ChangeFlow-Client: api`; browser cross-origin requests are denied. Forwarded headers cannot supply identity or prove TLS. See [HTTP contract](specs/001-change-request-workflow/contracts/http-api.md).

## Spec Development Flow

Follow the [constitution](.specify/memory/constitution.md)

Use these commands in order:

1. `/speckit.specify` — Define feature requirements and acceptance criteria.
2. `/speckit.clarify` — Resolve unclear or missing requirements.
3. `/speckit.plan` — Design the technical approach and architecture.
4. `/speckit.tasks` — Break the plan into actionable, ordered tasks.
5. `/speckit.analyze` — Check consistency across the specification, plan, and tasks.
6. `/speckit.implement` — Execute the tasks and verify the implementation.

## Build and Test

Run from the project root:

```powershell
.\gradlew.bat build
.\gradlew.bat test
```

Java 21 offline Wrapper build and tests have been run; exact commands, results, limitations and storage evidence are in [Quickstart](specs/001-change-request-workflow/quickstart.md). Keep the original ETag for each intended command; do not automatically refresh and retry after 412 or 503.

Back up consistently using SQLite's backup facility or a quiesced application with all connections closed. Never delete WAL/SHM/journal files to resolve a lock, reset data or run Flyway clean. Restrict filesystem permissions to the service operator. A 503 may leave the whole commit outcome unknown: inspect with an authorized GET before deciding on another command.

## Documentation

- [Source repository](https://github.com/oroumand/SoftwareEngineeringinArtificialIntelligenceEra)
- [Constitution](.specify/memory/constitution.md)
- [Agent guide](AGENTS.md)
- [Assignment answers](assignments)

## Assignment scope

The additional participant-evaluation package has been removed. The submission retains the required API, SDD artifacts, conceptual answers, Analyze/Converge evidence, and automated validation. A coding agent assisted development; generated changes require project-owner review. See [scope change](specs/001-change-request-workflow/scope-change.md).
