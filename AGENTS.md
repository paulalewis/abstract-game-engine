# Abstract Game Engine

Read these before changing behavior:

- [doc/spec.md](doc/spec.md) — what the library does
- [doc/architecture.md](doc/architecture.md) — how the code is arranged
- [doc/decisions.md](doc/decisions.md) — choices that are easy to undo

`README.md` is the setup guide for a human. It is not the product spec.

## Working here

- Build and test with `./gradlew build`. Tests alone: `./gradlew test`. Needs JDK 21.
- When asked to work a GitHub issue, branch from `main` as `<number>-short-name` before editing. Do not commit unless asked.
- Put game rules in unit tests under `src/test/kotlin`, mirroring the main package of the code under test.
- A new domain gets an entry in `CrossDomainSimulatorContractTest` so it is checked against `SimulatorContract`.
- `stateTransition` must not mutate its input, and `State.copy()` must be a deep copy. Do not weaken these to save allocations.
- Core (`com.castlefrog.agl`, `agents`, `util`) must not import from `domains`.
- Do not add runtime dependencies. Tests use `kotlin-test-junit` only.
- Change the version in `build.gradle.kts` only when asked to release.

## Keeping the docs true

- A change to behavior, such as a game rule, reward, or public API, updates `doc/spec.md` in the same change.
- A change to structure, such as a new package or domain, updates `doc/architecture.md`.
- A choice a later edit would be tempted to undo gets an entry in `doc/decisions.md` saying why.
- Do not copy facts the code already states, such as version numbers or file lists. Point at the file instead.
