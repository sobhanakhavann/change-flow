# Local facilitator runbook

Preparation only. T048 and SC-005 remain Unassessed. Read [scope review](scope-review.md) and obtain the recorded scoring decision before a formal evaluation. Keep [answer-key.md](answer-key.md) and this facilitator file away from participants until both independent answers are recorded. Do not edit frozen prompts/key during a session.

## Prerequisites and private fresh run

Use Java 21, the existing Gradle Wrapper/dependency cache, a real interactive PowerShell terminal and curl.exe. Run from repository root. Set JAVA_HOME to the installed Java 21 directory; the known workstation example is below. These environment settings affect this session only.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle/session-home'
$env:GRADLE_RO_DEP_CACHE=Join-Path $env:USERPROFILE '.gradle/caches'
$Run=Join-Path $env:LOCALAPPDATA ('changeflow-acceptance-'+[guid]::NewGuid())
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 init $Run
```

Do not create `$Run` first: init refuses any pre-existing directory. It restricts the new directory to its owner, creates a run marker and guard record, establishes real WAL/Flyway schema, and never resets/baselines an existing database. All later commands require the matching marker/guard and only manage accounts created by this tool. Unknown directories/databases fail closed. Keep `$Run` outside the checkout and preserve it after evaluation. A failed/partial preparation is retained: choose a new run directory rather than delete/reset it. Do not use a production database or copy markers onto one.

Use the same Windows identity/elevation for later commands. If another token cannot access the owner-only directory, do not weaken its ACL or copy private keys into a shared location; use the owning terminal. Share only the public participant kit.

## Accounts, roles, certificate and fixtures

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 create $Run requester-eval
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 create $Run reviewer-eval
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 grant $Run requester-eval Requester
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 grant $Run reviewer-eval Reviewer
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 tls $Run
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 seed $Run requester-eval reviewer-eval
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 list $Run
```

`create` asks twice for a hidden password in a real terminal (at least 12 characters for this local tool), using the application's actual encoder factory. It accepts no password argument/stdin file or password environment variable; a noninteractive console fails closed. Do not save or echo passwords/hashes. Only the protected evaluation database stores account hashes; neither script arguments, generated kit nor reports contain them. Give each real participant only their own account credential through a private channel. No public account-management API was added.

`tls` separately prompts for a hidden PKCS12 password. It calls the installed JDK keytool, using a child-process-only password environment value rather than a command-line password. The per-run keypair is RSA 3072, valid 14 days, SAN localhost/127.0.0.1. No OS/global trust store is changed. Keep tls.p12 private; share only participant-kit/tls.crt. Commands refuse overwriting an existing key/certificate. Expired runs require a new preparation, not bypassed verification.

`seed` is one-shot. It creates eight workflow fixtures via real domain operations and a private facilitator-fixtures.json manifest. It temporarily grants Requester to Reviewer solely to create the private and self-review fixtures, then revokes that grant. Current visible roles are listed without hashes. Role revocation moves the grant row to a new disabled unusable sink identity, preserving rows rather than deleting account/request/history data; sink identities are not participant accounts. Fixture creation failure does not erase prior work or permit resetting the file.

`participant-kit` contains only the public certificate and UTF-8 JSON bodies: empty, incomplete, content, edit-a, edit-b and return. Distribute a copy of this kit and [participant-guide.md](participant-guide.md); do NOT distribute the database, private key, facilitator manifest, controlled metadata or answer key. Supply individual fixture references as the relevant prompt begins.

## Local HTTPS and verification

In the facilitator terminal:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 serve $Run
```

Enter the PKCS12 password privately. This starts actual production controllers/security/domain/repositories on **https://localhost:8443**, bound to loopback, with locked per-run datasource/TLS settings. Explicit imports prevent test probe controllers from component-scanning into this server. No fault spies run in this ordinary facilitator server. Keep the terminal running; Ctrl+C closes normally without removing data.

In another terminal with the same session JAVA_HOME/cache settings and `$Run`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 verify $Run
curl.exe --cacert "$Run/participant-kit/tls.crt" -i https://localhost:8443/api/v1/requests
```

`verify` requires a successfully verified TLS chain/hostname and an unauthenticated Basic challenge. curl should likewise complete certificate validation and return the challenge. Never use `-k`, `--insecure`, a trust-all manager or disabled hostname checks. Copy/inspect the public certificate from the private run, not from an untrusted download. If port 8443 is occupied, stop the conflicting evaluation process or use a different approved run configuration; do not fall back silently to plaintext.

The product permits development loopback HTTP, so local HTTPS is not a newly imposed product requirement. This runbook chooses actual servlet TLS to evaluate Basic safely and consistently with the Plan. Prior mock-secure/loopback tests alone did not prove TLS; the new rehearsal verifies trusted success, untrusted rejection, wrong-host rejection and actual curl certificate validation. This remains a local self-signed evaluation setup, not proof of a production proxy/public certificate deployment. Remote participant access is not enabled by this tool.

## Sequence, references and controlled observations

Use the manifest labels: race (Draft), approved (Requester-owned Approved), review (PendingReview for V2), revision (PendingReview shared by V3/R3/V4), revocation (PendingReview for V6), returned-sample (ChangesRequested for V1), private (Reviewer-owned private Draft) and self-review (Reviewer-owned PendingReview). R1/R2 create their own new proposal. Run V1 before mutating its samples. Coordinate V3 -> R3 -> V4. Use separate race/review fixtures and retain the exact original ETags. Do R7/V6 revocations last.

For V5 only:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 grant $Run reviewer-eval Requester
# Participant performs V5 against self-review, with both roles.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 revoke $Run reviewer-eval Requester
```

For the final before-check revocation exercises (keep accounts enabled):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 revoke $Run requester-eval Requester
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 revoke $Run reviewer-eval Reviewer
```

Do not restore a role until the participant has answered; a subsequent `grant` restores the intended role without deleting history.

To generate actual controlled outcomes for the timing contrast and the currently frozen V7:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance.ps1 controlled $Run
```

This launches only AcceptanceControlledTests in a **new private child run**, never injects faults into the participants' live server, and uses independent fresh SQLite/TLS fixtures with generated in-memory test credentials. The R7/V6 files capture a real HTTPS authorized read finishing after role revocation and a subsequent concealed response. V7 captures a real HTTP storage-uncertainty response after the existing commit seam actually commits, followed by an authorized inspection; no command replay is used. An arbitrary lock timeout is not substituted for unknown commit acknowledgement.

Give participants only the relevant returned-response observation files and the frozen questions, with the stated timing context. Do not give SQL/spy/event traces or the answer key. These are **automated observations, not participant answers or scores**. Record actual independent human interpretations separately. If the controlled command fails, retain its data and do not invent the missing response.

## Recording and completion

Use [response-sheet.md](response-sheet.md) for verbatim answers and attach observed responses, date, build identifier and material revision. Keep credentials out. Score against the separate frozen key only after independent answers are recorded. Preserve the current 7/7-all-elements rubric pending the owner decision in scope-review.md. Update results.md only after real execution; do not mark T048 complete because setup, TLS or automated rehearsal passed. Physical media failure, production ACL/backup/restore and production TLS remain separate operational limits.
