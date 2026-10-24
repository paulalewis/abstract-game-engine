# Decisions

Why the Abstract Game Engine is this way. The behavior itself is in [spec.md](spec.md). These are the choices a later edit will be tempted to "clean up."

These are current choices, not permanent ones. Any of them may be revisited. Change one deliberately, when asked, and update its entry in the same change.

## One instruction file for every coding agent

`AGENTS.md` is the file Grok, Codex, Cursor, and Copilot read. `CLAUDE.md` is one line, `@AGENTS.md`, so Claude Code loads the same rules. A second copy of the rules would drift. Product rules live in `doc/` so the agent file can point at them instead of becoming another spec.

## History stores actions, not states

History is kept concise: each step is the per-player action list, and states are rebuilt by replaying from the initial state. The shape of `History` may change.

## Deterministic, perfect-information domains only, for now

Chance and hidden information were dropped, along with Backgammon and Yahtzee, because they forced `History` to hold states as well as actions. They may come back. Until then, do not add random or hidden state to `Simulator` or `State`.

## State transitions never mutate their input

Agents doing tree search and callers keeping history hold on to earlier states, so a transition must leave its input alone and `State.copy()` must be deep. `SimulatorContract` enforces this for every domain.

## No caches in simulators

Earlier simulators cached legal actions, column heights, or the previous action (an LRU cache, a Connect 4 cache, the Havannah `prevActionCache`). Each cache went stale when a state was reused or reached a different way, and gave wrong wins or moves. Simulators compute everything from the state passed in.

## Actions are a list with one entry per player

`stateTransition` takes `List<A?>` and legal actions are `List<Set<A>>`, even though every current domain is turn-based. One interface covers any number of players and simultaneous moves. `requireLegalAction` checks the entry for the player to move.

## stateTransition validates only the action given

Apps rebuild a position by replaying its history through `stateTransition`. Validating by generating every legal action made replay cost grow with the number of legal moves: in Go, a trial board and a superko scan per empty point, 98% of a 300-move 19×19 replay. Validating one action with `isLegalAction` is a single trial board. Do not go back to `calculateLegalActions` inside `stateTransition` for a domain that overrides `isLegalAction`. An override must agree with `calculateLegalActions`; `SimulatorContract` checks this for every domain. Draughts keeps the default because the majority-capture rule needs every capture found anyway.

## Core does not depend on domains

`AdversarialRewards` and turn helpers live in `domains`, not core, so the core interfaces do not assume two-player zero-sum games.

## No third-party dependencies

Arrow and Google Truth were removed. The library ships with only the Kotlin standard library, and tests use `kotlin-test-junit`.

## Go uses Tromp–Taylor or Chinese rules; draughts uses international rules

These are the rule sets the domains implement. Both Go rule sets score by area, so no agreement about dead stones is needed, and both use positional superko for the Chinese rule against repeating a position. They differ only in self-capture, which Chinese rules forbid. Tromp–Taylor stays the default so existing callers keep the same legal moves and results; apps that want Chinese rules pass `GoRules.CHINESE`.

## Hex and Havannah boards are capped at byte coordinates

`HexAction` and `HavannahAction` store coordinates as signed `Byte`s to keep actions small, so a coordinate above 127 would wrap negative. Hex boards are capped at 128 and Havannah bases at 64 (127 cells wide), and larger sizes are rejected. Real boards are far smaller. Raising a cap means widening the action coordinates first.

## Havannah finds rings in two passes

A ring may enclose empty cells, the opponent's stones, or the player's own stones. The first pass flood-fills everything that is not the mover's stone and looks for an area touching no edge; it finds any ring whose inside holds at least one cell that is not the mover's. The second pass finds the rest: if every enclosed cell is the mover's, each of them has six neighbours of the mover's colour, which is itself a ring. Flood-filling around each connected group of the mover's stones does not work, because an own stone inside a ring touches the ring and joins its group, leaving nothing enclosed.

## Draws are automatic

Chess's fifty-move rule and threefold repetition, and the draughts draw rules, end the game as soon as the condition holds, with neutral rewards and no legal actions. Over the board a player must claim these draws; here no player can claim, and agents doing random playouts would otherwise never finish drawn endgames such as king against king. A player with no legal move still loses on the same turn, and a chess mate still wins even if it also completes a repetition.

## Draw history lives in the state

Draughts and chess repetition need earlier positions. They are kept in `DraughtsState` and `ChessState`, reset by any man or pawn move or capture, not in the simulator, so a state reached by different paths or reused in a search gives the same answer. See "No caches in simulators."

## MCTS weakens low levels with a temperature over visit counts

A small search budget alone does not make a weak player that feels human: with few playouts the visit counts are noisy, and the agent makes odd moves while still never missing some tactics. Low `MctsLevel`s instead pick the final move with probability proportional to `visits^(1/temperature)`, a softmax over log visit counts. Moves the search likes are still the likeliest, so the agent plays plausible moves and makes plausible mistakes; temperature 0 is the usual most-visited move. The alternative, sometimes playing a random move, was rejected because a random move in a board game is often absurd. The level presets may be retuned, but keep the weakening in the final move choice, not in the search.

## MCTS playouts are capped and score neutral at the cap

Random Go games may not end, and long random games in any domain cost time without telling the search much. A playout stops after `MctsConfig.maxPlayoutLength` steps and is scored by the `StateEvaluator` if there is one, otherwise as 0 for every player, the draw reward. A neutral score adds no bias toward either side; it only dilutes the estimate. Domains where random playouts are poor, such as Go, should supply a playout policy or evaluator rather than rely on the cap.

## Random Go playouts stop by not filling own eyes

Uniform random Go never ends: players fill their own eyes, groups die, and the board refills. In a playout the mover picks uniformly among legal placements that do not fill one of its own single-point eyes, and passes only when none is left. An eye is an empty point whose on-board orthogonal neighbours are all the mover's stones, with at most one opponent stone on its diagonals in the middle of the board and none on the edge or in a corner. The diagonal test leaves false eyes fillable, so a group that needs that point to connect can still be saved. A cap of three plies per board point backs this up. Two passes then end the game by the normal rules. `GoPlayoutPolicy` in `domains/go` implements it, and the benchmark uses its eye test. Do not drop the diagonal test or let random play pass early to make games shorter.

## The Go playout policy checks legality itself

`GoSimulator.isLegalAction` builds a trial board and scans the whole board twice for captures, which costs more than a playout can afford on every candidate. `GoPlayoutPolicy` instead maps the position's groups and liberties once per step and checks a candidate from the groups next to it, plus the positional superko scan. It must agree exactly with the simulator, superko and both self-capture rules included; `GoPlayoutPolicyTest` checks it at every point of many seeded positions, and `stateTransition` still validates each move. If a position has a group with no liberty, which play never produces, it asks the simulator instead. Do not replace this check with a heuristic that may let an illegal move through.

## Go playout tactics are off by default

`GoPlayoutPolicy(preferTactics = true)` captures groups in atari and extends its own first. In a 16-game 9×9 match between two 300-iteration MCTS agents, alternating colors, the tactical side won 6, so the bias did not show a gain at that budget. The default is the plain stopping rule. Turn it on by default only after a larger match shows it plays better.

## Playout policies only where the benchmark shows a gain

`./gradlew benchmark --args="--mode mcts"` decides. Go gains 20 to 40 times and Hex about 3 times. A fast uniform Havannah policy gained about 6%, because its win check, run again in `stateTransition`, dominates, so there is none. Draughts must find every capture for the majority rule and Chess must generate moves to test check, so a policy would not avoid the legal-move work. Add one when a measurement shows it pays.

## The benchmark is a source set, not a test

Timings vary by machine and load, so they cannot be test assertions, and a run takes about 40 seconds. Keeping the benchmark in its own source set keeps it out of the JAR and the test run while `build` still compiles it against the current API.
