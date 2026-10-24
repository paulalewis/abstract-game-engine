# Architecture

How the Abstract Game Engine is put together. Library behavior is in [spec.md](spec.md).

## Layers

Everything is under `src/main/kotlin/com/castlefrog/agl`.

- **Core** (`com.castlefrog.agl`) — the interfaces `Simulator`, `State`, `Action`, `Agent`, plus `History`, `IllegalActionException`, and `requireLegalAction`, which every simulator calls to validate its actions list.
- **util** — extension helpers on `LegalActions` (a `List<Set<A>>`).
- **agents** — `Agent` implementations. Depend only on core and util. `MctsAgent` is configured by `MctsConfig`, and takes its domain-specific hooks, `PlayoutPolicy` and `StateEvaluator`, as interfaces from this package so domain code can supply them without core depending on domains.
- **domains** — one package per game, each with an `Action`, a `State`, and a `Simulator`. `domains/Util.kt` holds `AdversarialRewards` and `nextPlayerTurnSequential`, shared by the two-player domains. A domain may also hold its own agent.

Dependencies point inward: domains and agents depend on core; core depends on nothing here.

Data flow for one step: a caller asks the simulator for legal actions, an agent picks one, the caller builds a per-player actions list and calls `stateTransition`. The simulator validates the action for the player to move with `requireLegalAction`, copies the state, applies the move to the copy, and returns it. Rewards and terminality are computed from the state alone, with no cache.

`isLegalAction` defaults to generating every legal action and looking the given one up. A domain overrides it when one action can be checked more cheaply. Go does, with one trial board and one superko scan; Hex and Havannah do, by checking the one cell and the game-over test. Chess does, by checking the draw counters, generating moves for the moving piece only, and testing that one move for check. Domains on the default still pass `calculateLegalActions` to `requireLegalAction`.

Domain-specific state:

- Draughts tracks an unfinished multi-jump (`mustContinueFromX`, pending captures) in the state. For the draw rules it also keeps the positions since the last man move or capture, a king-move counter, and the endgame material group with its move counter; these update only when a turn ends.
- Go keeps the board colorings seen so far in the state for superko, and a pass counter.
- Chess keeps castling rights, en-passant file, the halfmove clock, and the position keys seen since the last pawn move or capture (for threefold repetition) in the state.
- Connect 4 and Hex use bit operations for win checks.

Domain-specific agents live in their domain's package. `ChessAlphaBetaAgent` is in `domains/chess` so it can use the simulator's `internal` move helpers. `legalMoves` is the move list behind `calculateLegalActions`. `applyMove` plays a move already known to be legal, still on a copy. `hasLegalMove` generates the pseudo-legal moves and stops checking at the first legal one, and `legalCapturesAndPromotions` tests only captures and promotions for check. They skip the per-player sets and the second legality check in `stateTransition`.

## Build

Gradle with the Kotlin DSL and the bundled wrapper. Kotlin version, JVM toolchain, and library version are in `build.gradle.kts`; Gradle version is in `gradle/wrapper/gradle-wrapper.properties`. `./gradlew build` compiles, tests, and packages the JAR.

## Tests

JUnit 4 via `kotlin-test-junit`, in `src/test/kotlin` mirroring the main packages.

- Each domain has a `*SimulatorTest` for rules and a `*StateTest` for copy, equality, and formatting.
- `SimulatorContract` holds the library-wide checks (no input mutation, deep copy, `isLegalAction` agreeing with `calculateLegalActions` over random playouts, equal states hashing equally). `CrossDomainSimulatorContractTest` runs them against every domain.
- `TestSimulator`, `TestState`, and `TestAction` are a minimal domain for testing core code.

## Benchmark

`src/benchmark/kotlin` is its own Gradle source set, `benchmark`, that depends on the main output. It is not in the library JAR and `./gradlew test` does not run it; `./gradlew check` and `build` only compile it. `./gradlew benchmark` plays random games from the initial state to the end in each domain and size it lists, with a fixed seed, and prints games per second, moves per second, and the share of time in `calculateLegalActions`, `stateTransition`, `isTerminalState`, `calculateRewards`, and the move choice. `--args` takes `--only`, `--games-scale`, and `--seed`; see `PlayoutBenchmark.kt`. Go games use `GoPlayoutPolicy`, the stopping rule in [decisions.md](decisions.md).

CI (`.github/workflows/test.yml`) runs `./gradlew check` (tests, and compiling the benchmark) on JDK 21 for every pull request and every push to `main`.
