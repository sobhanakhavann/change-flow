<!--
Sync Impact Report
Version change: 1.0.0 -> 2.0.0
Rationale: Apply the project owner's supplied replacement. Expanded obligations cover approved
specifications, authorization, state integrity, concurrency, evidence, and governance. This is a
major amendment because testing obligations are also redefined: all business rules become
important business rules, and the blanket external-service integration-test mandate is removed.
Modified principles (previous Persian titles translated here for comparison):
- Simplicity -> I. Simplicity and Incremental Development
- Requirements Traceability -> II. Specification-Driven Development
- Separation of Responsibilities -> III. Separation of Responsibilities
- Security and Privacy -> IV. Security and Privacy
- Risk-Based Testing -> VI. Risk-Based Testing and Verifiable Evidence
- Error Handling and Diagnostics -> VII. Error Handling and Diagnostics
- Change Compatibility -> VIII. Compatibility and Change Management
Added principle: V. State Integrity and Concurrency.
Expanded/renamed sections: Technical Constraints -> Technical Constraints and Scope;
Development Workflow and Quality Gates; Governance.
Removed sections: None. Existing principles are incorporated into the replacement's eight principles.
Impact: Specifications must define failure, authorization, concurrency, and repeated-request behavior;
plans/research must justify boundaries and database, authentication, and concurrency mechanisms;
tasks must trace remaining gaps; code and tests must be reviewed against these obligations.
Dependent artifacts were not modified or assessed for compliance in this constitution-only update.
Approval: Project owner explicitly requested application of the supplied replacement in this session.
Deferred placeholders/TODOs: None.
This report is temporary review material; remove it before committing the amended constitution.
-->

# ChangeFlow Constitution

## Core Principles

### I. Simplicity and Incremental Development

- Choose the simplest solution that satisfies the approved requirements.
- Every new dependency, abstraction, module, or service MUST have a documented purpose.
- Do not introduce infrastructure or capabilities for anticipated future needs.
- Keep changes small enough to review and validate.

### II. Specification-Driven Development

- Features MUST be implemented against reviewed and approved specifications.
- Expected behavior, important failure scenarios, scope, and acceptance criteria MUST be defined before implementation.
- Significant behavioral changes MUST first be reflected in the specification, followed by an impact review of the design, tasks, code, and tests.
- Product assumptions MUST be explicit and distinguishable from approved decisions.
- Unresolved questions affecting security, business behavior, or architecture MUST be raised before the dependent behavior is finalized.

### III. Separation of Responsibilities

- Business rules MUST remain separate from HTTP handling and persistence details.
- Controllers MUST delegate use cases to the application layer.
- Domain or application code MUST enforce business invariants and valid state transitions.
- The application layer MUST coordinate use cases and enforce their authorization requirements.
- Persistence components MUST implement data access and storage concerns without becoming the sole enforcement point for business rules.
- Business behavior MUST be testable without starting an HTTP server.
- Package and dependency boundaries MUST be documented in the implementation plan. Separate build modules are not required unless justified.

### IV. Security and Privacy

- Authentication and authorization MUST be treated as separate responsibilities.
- Sensitive operations MUST verify the caller's identity, current server-side permissions, and applicable resource access rules.
- Client-supplied roles, ownership claims, or requested states MUST NOT independently establish authority.
- External input MUST be validated at the appropriate boundary.
- Secrets and credentials MUST NOT be committed to the repository or exposed in logs and error responses.
- Rejected operations MUST NOT disclose information the caller is not authorized to access.

### V. State Integrity and Concurrency

- State-changing operations MUST enforce their documented preconditions and transition rules.
- Operations based on stale data MUST NOT silently overwrite newer changes.
- Related updates that form one business operation MUST commit atomically or leave no partial persistent effects.
- Concurrency and repeated-request behavior MUST be defined in the feature specification and technical plan.
- The chosen mechanisms MUST be validated against the selected persistence technology.

### VI. Risk-Based Testing and Verifiable Evidence

- Important business rules MUST have automated tests independent of HTTP handling.
- Persistence behavior MUST have integration tests using a database capable of representing the behavior under test.
- Tests MUST cover relevant success, failure, authorization, boundary, and concurrency scenarios.
- Tests for rejected sensitive operations MUST verify the resulting persistent state as well as the response.
- Behavioral bug fixes MUST include regression tests.
- Build success and passing tests MUST NOT be presented as proof of untested requirements or human acceptance.
- Validation reports MUST record actual commands, results, and known limitations. Unperformed checks MUST be reported as unperformed.

### VII. Error Handling and Diagnostics

- Failures MUST NOT be silently ignored.
- API failures MUST provide consistent, documented responses that clients can interpret.
- Diagnostic information MUST support troubleshooting without exposing secrets, private content, or internal implementation details to unauthorized callers.
- Operational failures MUST NOT be reported as successful business operations.

### VIII. Compatibility and Change Management

- Breaking changes to public contracts or persisted data MUST be documented.
- Affected consumers and existing data MUST have migration instructions where applicable.
- Documentation affected by a change MUST be updated.
- Planned capabilities MUST be clearly distinguished from implemented and validated capabilities.

## Technical Constraints and Scope

- Use Java 21, Spring Boot, and the Gradle Wrapper.
- Use Spec Kit and a coding agent to support specification-driven development.
- Keep environment-specific settings configurable outside application source code.
- Document the purpose of every additional dependency.
- The initial assignment delivers an HTTP API. A frontend is not required.
- Product features involving AI, RAG, MCP, or multi-agent execution are outside the initial assignment scope.
- Database, authentication, and concurrency mechanisms MUST be selected and justified in the technical plan and research artifacts.

## Development Workflow and Quality Gates

- Apply Specify, Clarify, Plan, Tasks, Analyze, Implement, Test, Review, and Converge as appropriate to the change.
- Resolve significant inconsistencies between the specification, plan, tasks, and this constitution before implementing the affected behavior.
- Coding-agent output MUST be reviewed for correctness, security, scope, and compliance with the approved artifacts.
- The project owner remains responsible for approving significant product and architectural decisions.
- Before merging implementation changes, the Gradle Wrapper build MUST succeed with the required tests enabled.
- Record the actual build and test commands and results.
- Review implementation against the documented intent and record remaining gaps as traceable work.
- A feature MUST NOT be declared accepted while required acceptance criteria remain unmet or unevaluated.
- These requirements apply equally to human-written and AI-generated changes.

## Governance

- This constitution governs the project's enduring engineering principles.
- Conflicts between this constitution and feature specifications or plans MUST be explicitly resolved before the affected implementation proceeds.
- Amendments MUST document their rationale and impact on specifications, plans, tasks, code, and tests.
- Amendments require explicit approval from the project owner before becoming effective.
- Exceptions MUST have an explicit scope, rationale, consequences, and approval. They MUST NOT silently waive mandatory assignment requirements.
- Every amendment MUST update the version, amendment date, and change summary.

Versioning rules:

- **MAJOR:** Removal or incompatible redefinition of an existing principle.
- **MINOR:** Addition of a principle or material expansion of existing obligations.
- **PATCH:** Editorial corrections or clarifications without changing obligations.

**Version**: 2.0.0 | **Ratified**: 2026-11-15 | **Last Amended**: 2026-11-15
