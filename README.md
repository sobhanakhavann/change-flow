# ChangeFlow Application

An HTTP API for submitting, reviewing, and approving change requests, developed using Spec Kit and a coding agent.

## Technology

Java 21, Spring Boot, and Gradle.

## Status

Initial setup and constitution are complete. Feature specification and implementation are pending.

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

These commands have not yet been verified in this project.

## Documentation

- [Source repository](https://github.com/oroumand/SoftwareEngineeringinArtificialIntelligenceEra)
- [Constitution](.specify/memory/constitution.md)
- [Agent guide](AGENTS.md)
- [Assignment answers](assignments)
