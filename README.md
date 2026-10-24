# Abstract Game Engine

This is a Kotlin framework for creating turn based
domains and custom agents to play in those domains.
It is designed for deterministic domains with perfect information.

Domains:
- [Chess](https://en.wikipedia.org/wiki/Chess)
- [Connect 4](https://en.wikipedia.org/wiki/Connect4)
- [International Draughts](https://en.wikipedia.org/wiki/International_draughts)
- [Go](https://en.wikipedia.org/wiki/Go_(board_game))
- [Havannah](https://en.wikipedia.org/wiki/Havannah)
- [Hex](https://en.wikipedia.org/wiki/Hex_%28board_game%29)

Agents:
- random - agent randomly selects an action

## Docs

- [doc/spec.md](doc/spec.md) — what the library does: API, domains and their rules
- [doc/architecture.md](doc/architecture.md) — package layout, data flow, build, tests
- [doc/decisions.md](doc/decisions.md) — why it is built this way

`AGENTS.md` is the instruction file for coding agents; `CLAUDE.md` imports it.

## Setup

1. Install JDK 21. The build compiles and tests with a JDK 21 toolchain, and Gradle finds an installed JDK 21 by itself.
2. The Gradle wrapper is included; no separate Gradle install is required. It runs on any Java from 17 up, so `JAVA_HOME` can stay on a newer JDK.

## Build

```bash
./gradlew clean build
```

`build` compiles the library, runs the full test suite, and packages the JAR.

## Run Tests

```bash
./gradlew test
```

You should see each test listed as `PASSED` / `FAILED`, then a summary like:

```text
Test result: SUCCESS
Test summary: 163 tests, 163 succeeded, 0 failed, 0 skipped
```

Useful variants:

```bash
# Force tests to re-run even if Gradle thinks they are up to date
./gradlew cleanTest test
```

HTML report (after a test run):

```text
build/reports/tests/test/index.html
```

## Benchmark random playouts

```bash
./gradlew benchmark
# one domain, fewer games
./gradlew benchmark --args="--only go19 --games-scale 0.2"
```

It plays random games in every domain with a fixed seed and prints games per second, moves per second, and where the time goes. See [doc/architecture.md](doc/architecture.md#benchmark).
