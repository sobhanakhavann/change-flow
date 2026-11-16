# Acceptance preparation evidence - 2026-11-16

**Automated rehearsal only. No real participant answers, human scores, T048 completion or SC-005 pass.**

## What was prepared

- scripts/acceptance.ps1 launches the test-only AcceptanceTool directly in Java 21 after preparing its classpath; real terminal input is retained for hidden Console.readPassword. It does not add a provisioning or fault endpoint to the normal application.
- Fresh-directory initialization, private owner ACL/permissions, marker-to-database guard, actual Flyway/WAL, account creation using the real application encoder factory, grant/revoke/list and one-shot workflow fixtures. Revoked grants are moved to disabled sink identities; existing records/files are not deleted/reset.
- Per-run local PKCS12 keypair and public certificate, real servlet HTTPS, strict per-run trust and hostname verification, ordinary production controllers only. The test-only launch configuration explicitly imports production components instead of scanning test probes.
- Actual controlled HTTPS observations for R7/V6 authorization timing and the frozen V7 lost-commit-acknowledgement case, using existing Mockito/Spring/JDBC test facilities. Each controlled run creates another fresh guarded child database and closes its test context; normal serve has no injected fault behavior.
- Participant HTTP execution companion and blank response sheet, separate from the retained answer key. Scope mapping and pending owner decision are in scope-review.md; no prompt or scoring criterion was silently removed or reduced.

## Actual checks

The following ran with the session-local Java 21 and Gradle cache settings from quickstart.md:

```powershell
.\gradlew.bat --offline --no-daemon test --tests '*AcceptanceRehearsalTests' --tests '*AcceptanceControlledTests' prepareAcceptanceTool
.\gradlew.bat --offline --no-daemon build prepareAcceptanceTool
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 init <fresh-build-run-directory>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 list <same-guarded-directory>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 controlled <same-guarded-directory>
```

`init`/`list` executed through the real launcher against a fresh isolated file and reported no accounts before provisioning. Programmatic account preparation/revocation/seed used generated in-memory passwords on separate fresh files. Assertions checked actual encoder matching without rendering hashes; duplicate accounts, repeated init/seed and unguarded roots were refused, credentials remained valid after role revocation, and eight request fixtures plus retained feedback existed. This exercises preparation but is not a claim that hidden interactive prompts were answered by a person.

The actual facilitator-server configuration was started on a random HTTPS port. Real Basic authentication/list returned 200, `/probe` returned 404 and no probe route was registered. Java rejected the untrusted self-signed certificate and rejected a wrong hostname even with its certificate trusted; explicit per-run trust succeeded. The installed Windows curl completed `--cacert` verification and received the expected unauthenticated 401 challenge. No trust-all callback, `--insecure`, hostname bypass or global trust-store change was used.

The controlled cases recorded actual HTTPS outcomes: already-authorized read then next-read denial for each role; lost commit acknowledgement then authorized state inspection, with stored version checked and no replay. Output files explicitly label automated observations, not participant answers. An initial standalone launcher retained the cached test server after the tests finished; that task-owned process was identified/stopped, then AFTER_CLASS context disposal was added and the corrected launcher was checked again. No data was deleted to address lifecycle or ACL issues. Owner-only run directories intentionally denied access from the restricted sandbox token; checks were performed using the owning execution context rather than weakening ACLs.

**Complete build: BUILD SUCCESSFUL in 1m 3s; 188 tests in 27 suites, zero failures/errors/skips.** Existing workflow/security/concurrency tests remain included. The four additional tests cover preparation and controlled/local HTTPS behavior. Inspection of the built Boot JAR found no AcceptanceTool, AcceptanceControlled, acceptance_guard or JUnit class entries. The launcher dependency already existed for tests; it was made available at test compile time, with no new product/runtime dependency. Git whitespace checks passed (only a line-ending normalization notice on the documentation).

## Remaining prerequisites and limits

- A real facilitator must use an interactive terminal, choose private account and PKCS12 passwords, and retain the same Windows identity/elevation that owns the new directory. The automation deliberately did not fabricate terminal input or participant identity/answers.
- Resolve the V7 scoring-scope question (and avoid requiring V6 internal diagnostics) before a formal score; the current 7/7 key remains untouched meanwhile.
- Two real role representatives must independently execute/interpret the frozen material and have all required elements scored. T048 remains unchecked and results.md remains Unassessed.
- The demonstrated TLS is local, pinned-certificate evaluation TLS, not validation of remote/public/proxy deployment. Port 8443 must be available for the facilitator command; certificates expire after 14 days.
- Physical disk exhaustion/damage, production ACL audit and backup/restore rehearsal are not established by these checks. No production data, global settings, commits or pushes were changed.
