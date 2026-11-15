# Session 1 — Conceptual Answers

Assignment: [Session 1 — Specification-Driven Software Engineering](https://github.com/oroumand/SoftwareEngineeringinArtificialIntelligenceEra/blob/eda4731/Session01/Assignments/Assignment.md)

## Question 1 — Constitution and Architectural Governance

### Passing Tests vs. Architectural Compliance

Passing tests show that the behavior covered by those tests matched their expectations under the tested conditions. They do not automatically verify where business logic resides or whether dependencies follow the intended architecture.

For example, a controller might reject self-approval and pass its HTTP tests. However, another entry point could bypass that controller, and testing the rule would remain unnecessarily coupled to HTTP.

In a Spring application, controllers should handle HTTP input and output and delegate use cases to the application layer. Authorization belongs in the application use case, while business invariants belong in domain or application code. Persistence details should remain separate.

### Where the Rule Belongs

The separation-of-responsibilities principle belongs in the Constitution because it is a stable rule shared across features.

The Plan explains how a particular feature follows that principle, including component responsibilities and dependency boundaries. Tasks identify the implementation and verification work needed to preserve those boundaries.

These artifacts complement one another.

### Preventing Recurrence

- Explicitly prohibit business rules in controllers in the Constitution.
- Review the Plan for compliance before implementation.
- During code review, examine rule placement, dependencies, and whether alternative entry points can safely invoke the same use case.
- Test important business rules independently of HTTP.
- Where useful, add architecture tests, such as ArchUnit checks, for enforceable dependency restrictions.

Architecture tests can detect forbidden dependencies, but identifying all misplaced business logic still requires human review.

## Question 2 — Specify and Clarify

### Agent Authority and Product Decisions

The agent must not finalize self-approval as an allowed product behavior without approval from the responsible product owner. This decision affects security and separation of duties, and the original statement does not resolve it.

The agent should expose the ambiguity and request a decision. In the complete ChangeFlow assignment, the decision is already explicit: self-approval is forbidden, including for users who hold both roles.

### Assumptions vs. Approved Decisions

An assumption is a temporary, reviewable interpretation recorded with its rationale and consequences.

An approved decision is an explicit choice made by the responsible authority and reflected in the requirements and acceptance criteria. Recording an assumption does not turn it into an approved decision.

### Other Important Ambiguities

- Can every reviewer review every request, or only requests within an assigned department?
- If a reviewer's permission is revoked after login, can the existing session still approve requests?
- Which fields are mandatory when creating or submitting a request?
- What happens when two reviewers make conflicting decisions concurrently?
- Are draft deletion, viewing other users' requests, and repeated submissions allowed?

### The Roles of Specify and Clarify

Specify should describe known behavior through user stories and acceptance criteria while exposing unresolved questions that affect security, scope, data, or operation outcomes.

Clarify resolves those questions through focused discussion and records the answers in the specification. For example, a permission-revocation decision should produce an acceptance scenario demonstrating that a user who has lost permission cannot approve a request.

Behavior depending on an unresolved, significant ambiguity should not be finalized. Low-risk assumptions may be recorded where appropriate, but they must remain distinguishable from approved decisions.

## Question 3 — Requirement vs. Design Decision

### Distinguishing the Two

The requirement is that execution must use content covered by valid approval. It describes the outcome the system must guarantee.

Adding a numeric `ContentRevision` field is a design decision intended to satisfy that requirement. It is one possible mechanism.

With a revision-based design, approval must reference a specific content revision, and execution must verify that relationship. Merely adding or incrementing the field is insufficient. The verification and relevant state change must also be atomic so that concurrent changes cannot invalidate the check before execution.

### Why Plan and Research?

The Specification should express expected behavior and acceptance criteria without prematurely committing the product to a storage mechanism.

Research compares alternatives in terms of correctness, implementation cost, auditability, and concurrency. The Plan records the selected mechanism and how it will be implemented.

This separation allows the mechanism to change while preserving the original requirement.

### Alternative Approaches

| Approach | Benefit | Cost or limitation |
|---|---|---|
| Hash of approved content | Allows content comparison without storing a complete copy in the approval record | Requires a canonical representation and a clear definition of relevant fields; the hash alone does not prove approval authority |
| Immutable snapshot | Approval references an explicit, auditable snapshot that can be used for execution | Requires additional storage and snapshot lifecycle management |
| Separate version record for each edit | Approval references a version identifier, preserving traceable history | Introduces a more complex data model and queries |

Every approach must preserve the connection between valid approval and the content being executed.

ChangeFlow's explicit prohibition on editing an `Approved` request must remain enforced independently of the selected mechanism. The mention of execution in this conceptual question does not add an execution feature to the initial project scope.

## Question 4 — Tasks and Analyze

### Dependency and Parallelism Problems

A task that requires a prepared database cannot run before the required schema and migrations are available. The task order contains an unmet prerequisite.

Database preparation must appear earlier, and the consuming task must explicitly depend on it.

The `[P]` marker indicates that tasks can run in parallel. If two tasks modify the same file, that claim requires careful justification because conflicting changes or inconsistent assumptions may result.

The simplest correction is to execute them sequentially. If parallel execution is necessary, their responsibilities and shared contract should first be separated clearly.

### Expected Findings

Analyze should report:

- A missing dependency or incorrect task order: the consuming task precedes database preparation.
- A parallelism conflict: tasks marked `[P]` modify a shared file without a clear coordination strategy.
- Any inconsistency between the Plan's persistence setup and the task execution order.

Each finding should identify its location, impact, and recommended correction. Severity depends on the consequences; execution-blocking dependencies must be resolved before the affected implementation begins.

In the assignment's tool version, Analyze reports findings without directly modifying the artifacts.

### Why Full Requirement Coverage Is Insufficient

Mapping every requirement to a task only shows that no requirement is completely unrepresented.

It does not establish that the proposed implementation is correct, the tests are adequate, security requirements are satisfied, ambiguities are resolved, or the task order is executable.

Implementation readiness depends on the quality of those mappings and the resolution of significant findings.

## Question 5 — Implement, Validation, and Converge

### Is the Feature Complete and Accepted?

No. The unmet security rule means the implementation does not satisfy its requirements. The unevaluated human acceptance criterion must remain explicitly unevaluated.

Completed task checkboxes and 120 passing tests provide partial evidence. A missing security test or an omitted task can leave an important requirement unfulfilled.

### Build, Test, Validation, and Acceptance

| Concept | What it establishes | What it does not establish by itself |
|---|---|---|
| Build | The project can be compiled and packaged with the stated tools and configuration | Correct behavior or satisfaction of every requirement |
| Test | Defined scenarios have been checked against specified expectations | Coverage of every risk or product criterion |
| Validation | Relevant evidence has been assessed against intended behavior, needs, and constraints | Final acceptance by the responsible authority when separate approval is required |
| Acceptance | The responsible authority accepts the feature against agreed criteria and evidence | Permanent freedom from defects |

In Gradle, `build` may also execute tests. The roles of building and testing remain distinct, and reports must state the actual commands, outcomes, and tests executed.

### Converge vs. Analyze

Analyze examines the consistency and quality of the Specification, Plan, and Tasks. Its focus is on the artifacts.

Converge compares the current implementation with the documented intent to identify missing behavior and remaining work. In the assignment's tool version, it does not directly fix the code; it turns gaps into traceable tasks.

For this example, the security defect requires a corrective task and regression test. Human acceptance must remain recorded as outstanding. Converge cannot claim human acceptance on behalf of participants.

### Correcting a Newly Discovered Gap

1. Record the gap with references to the requirement or acceptance criterion and relevant implementation evidence.
2. If the intended behavior is ambiguous, clarify the specification first. Do not weaken the requirement merely to hide an implementation defect.
3. Add corrective tasks to `tasks.md`, including dependencies, tests, and completion criteria.
4. Implement the correction and rerun the relevant build, tests, and validation.
5. Reassess compliance and record the actual human acceptance status.

The assignment does not require every newly discovered Converge task to be completed before the current deadline. However, those tasks must be recorded and analyzed. This allowance does not make the feature complete or accepted.