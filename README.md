# ChangeFlow

An HTTP API for submitting, reviewing, and approving change requests.

## Technology

Java 21, Spring Boot, Gradle, and SQLite.

## Run

Run from the project root. No environment file is required.

Windows:

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```sh
sh gradlew bootRun
```

The database is created as `./changeflow.sqlite` inside the project when started from its root, on both Windows and Linux. No hard-coded drive or user path is required. Configuration is in `src/main/resources/application.properties`; logs go to the console.

Default configuration can also be overridden with environment variables; see `.env.example`.

## Build and Test

Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat test
```

Linux/macOS:

```sh
sh gradlew build
sh gradlew test
```

## Documentation

- [Constitution](.specify/memory/constitution.md)
- [Agent guide](AGENTS.md)
- [Assignment answers](assignments/session1/answers.md)
- [Analyze report](assignments/session1/analyze.md)
- [Converge report](assignments/session1/converge.md)
