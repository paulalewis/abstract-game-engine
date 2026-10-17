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

## Go uses Tromp–Taylor rules; draughts uses international rules

These are the rule sets the domains implement. Tromp–Taylor scores by area, so no agreement about dead stones is needed.

## Havannah finds rings in two passes

A ring may enclose empty cells, the opponent's stones, or the player's own stones. The first pass flood-fills everything that is not the mover's stone and looks for an area touching no edge; it finds any ring whose inside holds at least one cell that is not the mover's. The second pass finds the rest: if every enclosed cell is the mover's, each of them has six neighbours of the mover's colour, which is itself a ring. Flood-filling around each connected group of the mover's stones does not work, because an own stone inside a ring touches the ring and joins its group, leaving nothing enclosed.
