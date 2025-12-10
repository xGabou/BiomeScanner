# Repository Guidelines

## Project Structure & Module Organization
- Core code: `src/main/java/net/Gabou/bensbiomelocatorbygabou` (entry `Bensbiomelocatorbygabou`, debug tools in `debug/`).
- Metadata: `src/main/resources/META-INF/mods.toml`; values mirror `gradle.properties`.
- Generated assets: `src/generated/resources` (written by data generation); avoid manual edits.
- Runtime output: `run/` and `run-data/` are for local runs only. Tests live in `src/test/java` and `src/test/resources` (currently empty).

## Build, Test, and Development Commands
- `./gradlew build` — compile and reobfuscate; jars in `build/libs/`.
- `./gradlew runClient` — launch a dev client using `run/`.
- `./gradlew runServer --args "--nogui"` — start a dedicated server for checks.
- `./gradlew runData` — write generated resources to `src/generated/resources/`.
- `./gradlew test` — run unit tests; add them under `src/test/java`.
- `./gradlew runGameTestServer` — execute GameTests when present.

## Coding Style & Naming Conventions
- Java 17, 4-space indent, brace-on-same-line per existing classes.
- Keep everything under `net.Gabou.bensbiomelocatorbygabou`; mod id stays `bensbiomelocatorbygabou`.
- Use lowerCamelCase for methods/fields, UPPER_SNAKE_CASE for constants. Favor small, focused classes and minimal constructor side effects beyond event registration.

## Testing Guidelines
- Add unit or GameTest coverage for new logic in `src/test/java`; name test classes with the `*Test` suffix.
- Prefer automated checks (`./gradlew runGameTestServer`) over manual playthroughs when possible.
- When adding commands or worldgen logic, include repro steps and expected outputs (e.g., sample `/biome_scan_all` results) in PR descriptions.

## Commit & Pull Request Guidelines
- No history yet: use concise imperative commits (e.g., `Add biome scan summary sorting`); include a scope when helpful and squash WIP before PRs.
- Run `./gradlew build` and `./gradlew runData` (if resources change) before pushing.
- PRs should link issues, summarize behavior changes, and include evidence for gameplay-facing updates (logs or screenshots). Call out config or resource edits explicitly.

## Security & Configuration Tips
- `/biome_scan_all` requires permission level 2; keep this default unless justified.
- Keep secrets and API tokens out of source; the project should build from a clean checkout with Gradle-managed dependencies only.
- Verify `mods.toml`, `gradle.properties`, and `Bensbiomelocatorbygabou.MODID` stay in sync before releases.
