# Participant execution companion

For owner-approved revision-2 scenarios.md (2026-11-16): scored Requester R1-R7 (7/7), scored Reviewer V1-V6 (6/6), each requiring all elements correct (100%) without compensating averages. V7 is a separate non-scored facilitator demonstration. This file supplies commands, **not an answer key**. Do not read facilitator/answer-key files before submitting independent answers. Do not record passwords, hashes or Authorization headers. The facilitator supplies your account, public certificate/body kit and individual request references, not database/private key access. Revision-1 material is retained separately as history, not the current rubric.

## Common commands

Use an interactive PowerShell terminal and curl.exe. Set `$Kit` to your supplied kit copy. The certificate belongs to this evaluation only. No `-k`/`--insecure` is allowed; stop and tell the facilitator if certificate validation fails.

```powershell
$Kit='C:/path/to/your/participant-kit'
$Base='https://localhost:8443'
$User='requester-eval' # Reviewer uses reviewer-eval instead
function CF([string]$Method,[string]$Path,[string]$Body='',[string]$Tag='') {
    $arguments=@('--silent','--show-error','--include','--cacert',"$Kit/tls.crt",
      '--user',$User,'--request',$Method,'--header','X-ChangeFlow-Client: api',
      '--header','Content-Type: application/json')
    if($Body){$arguments+=@('--data-binary',"@$Kit/$Body")}
    if($Tag){$arguments+=@('--header',"If-Match: $Tag")}
    & curl.exe @arguments "$Base$Path"
    if($LASTEXITCODE -ne 0){throw 'HTTP transport/certificate failure; report it without credentials.'}
}
```

curl prompts for your password privately; never put it after `--user`. Do not use verbose/trace logging. All bodies in the kit are UTF-8 without BOM. `empty.json` is `{}`, `content.json` supplies the three content fields, `incomplete.json` omits/empties required content, edit-a/edit-b contain different complete content, and return.json supplies a nonblank return reason.

Copy each returned Location into the named path variable (a path such as `/api/v1/requests/<UUID>`) and each ETag as the **entire quoted value**. Assign values manually from your actual responses, not a fabricated example. After 204, GET is needed to obtain a new tag. When a scenario says to reuse a tag, do not refresh it. Keep old tags under their original variable names.

For every scored prompt, record the observed HTTP response(s), resulting state(s) and reason for every rejection/conflict in your own words. Do not infer success solely from absence of a response body. Ask for supplied references before running a block containing unassigned variables. Supplemental V7 discussion has no effect on either score.

## Requester: R1-R7

**R1.** Create incomplete content, assign returned Location to `$Proposal` and ETag to `$Tag`, then submit:

```powershell
CF POST '/api/v1/requests' 'empty.json'
# Assign $Proposal and $Tag from that actual response.
CF POST "$Proposal/submit" 'empty.json' $Tag
CF GET $Proposal
```

What state resulted after saving and after submission? Can submission succeed with this content, and why?

**R2.** Complete the same proposal. Assign `$Tag` from the PUT response before Submit, then GET and assign the current tag before the last edit attempt:

```powershell
CF PUT $Proposal 'content.json' $Tag
# Assign the PUT ETag to $Tag.
CF POST "$Proposal/submit" 'empty.json' $Tag
CF GET $Proposal
# Assign the current GET ETag to $Tag.
CF PUT $Proposal 'edit-a.json' $Tag
```

Identify the state after submission and explain whether/why editing is permitted.

**R3.** Wait for the Reviewer to finish V3 on the supplied `$Revision`. Inspect feedback, save incomplete content, attempt Submit, complete and resubmit. At each successful PUT, assign its new ETag to `$Tag`; a rejected command does not supply a replacement tag:

```powershell
CF GET $Revision
# Assign ETag to $Tag.
CF PUT $Revision 'incomplete.json' $Tag
# Assign new PUT ETag to $Tag.
CF POST "$Revision/submit" 'empty.json' $Tag
CF PUT $Revision 'content.json' $Tag
# Assign new PUT ETag to $Tag.
CF POST "$Revision/submit" 'empty.json' $Tag
CF GET $Revision
```

Identify every resulting state; who may see the latest reason and who may edit? Tell the facilitator when ready for V4.

**R4.** The facilitator supplies `$Private`:

```powershell
CF GET $Private
```

Explain the outcome and whether it reveals that a private request exists.

**R5.** Use a separate supplied `$Race`. Save its original GET ETag as `$RaceTag`; reuse it unchanged for both different edits:

```powershell
CF GET $Race
# Assign original ETag to $RaceTag; do not change it between the PUTs.
CF PUT $Race 'edit-a.json' $RaceTag
CF PUT $Race 'edit-b.json' $RaceTag
CF GET $Race
```

Explain the second outcome and identify which content survives.

**R6.** Inspect the supplied `$Approved`, save its current ETag as `$Tag`, then try editing:

```powershell
CF GET $Approved
# Assign current ETag to $Tag.
CF PUT $Approved 'edit-a.json' $Tag
CF GET $Approved
```

Identify its state and explain the rejection.

**R7.** After the facilitator confirms your Requester role was revoked (credentials remain valid):

```powershell
CF POST '/api/v1/requests' 'empty.json'
```

Explain why the next protected operation cannot proceed. Examine the supplied **actual R7 controlled response observations** for an already-authorized read and a later read. Contrast their outcomes with revocation before the operation check. These observations are not prewritten participant answers.

## Reviewer scored prompts: V1-V6

Change `$User` to `reviewer-eval`. The facilitator supplies `$Private`, `$ReturnedSample`, `$Approved`, `$Review`, `$Revision`, `$SelfReview`, `$Revocation` as each scenario starts.

**V1.** Inspect the queue and the supplied four-state examples:

```powershell
CF GET '/api/v1/requests'
CF GET $Private
CF GET $ReturnedSample
CF GET $Review
CF GET $Approved
```

Which states can you read and which remain private? Explain the effect of an additional Requester role/ownership.

**V2.** Get `$Review` and save its original ETag as `$DecisionTag`. Approve and repeat using that same tag:

```powershell
CF GET $Review
# Assign original ETag to $DecisionTag.
CF POST "$Review/approve" 'empty.json' $DecisionTag
CF POST "$Review/approve" 'empty.json' $DecisionTag
CF GET $Review
```

Identify the new state and explain the repeated command result.

**V3.** On `$Revision`, save the original GET ETag as `$OldReviewTag`. First omit the reason, inspect, then supply the reason and inspect again:

```powershell
CF GET $Revision
# Assign original ETag to $OldReviewTag; retain it for V4.
CF POST "$Revision/request-changes" 'empty.json' $OldReviewTag
CF GET $Revision
CF POST "$Revision/request-changes" 'return.json' $OldReviewTag
CF GET $Revision
```

Identify each result/state and explain your read access after the return. Notify the facilitator so the Requester can perform R3.

**V4.** After R3 is completed, inspect and retry the earlier decision using `$OldReviewTag`, not a new tag:

```powershell
CF GET $Revision
CF POST "$Revision/request-changes" 'return.json' $OldReviewTag
CF GET $Revision
```

Identify the state and explain why the earlier-cycle command has that outcome.

**V5.** Wait for the facilitator to confirm you temporarily have both roles. Get your supplied `$SelfReview`, save its ETag as `$SelfTag`, then try both commands:

```powershell
CF GET $SelfReview
# Assign current ETag to $SelfTag.
CF POST "$SelfReview/approve" 'empty.json' $SelfTag
CF POST "$SelfReview/request-changes" 'return.json' $SelfTag
CF GET $SelfReview
```

Explain both results despite holding both roles. The facilitator restores your normal role configuration after your answer.

**V6.** Before revocation, GET the supplied `$Revocation` and save its tag as `$RevocationTag`. After the facilitator confirms revocation of Reviewer:

```powershell
CF POST "$Revocation/approve" 'empty.json' $RevocationTag
```

Identify the returned result and observable effect of revocation. Examine the supplied **actual V6 controlled observations** and contrast an already-authorized operation with the later operation. Only these observable outcomes are scored; SQL, transaction, snapshot or internal diagnostic explanations are not required.

## V7 supplemental facilitator demonstration - not scored

The facilitator demonstrates **actual V7 controlled HTTP observations**, including the later authorized inspection, and how to inspect an uncertain mutation outcome without automatic fresh-tag replay. Do not inject faults into the ordinary evaluation server yourself. No answer is required for SC-005; participant discussion, errors or omission cannot change either score. This separation was explicitly approved by the owner in revision 2; it is not a reduction of correctness required for the scored prompts.
