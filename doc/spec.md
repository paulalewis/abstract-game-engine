# Product spec

What the Abstract Game Engine does. How it is built is in [architecture.md](architecture.md). Why it is this way is in [decisions.md](decisions.md).

The engine is a Kotlin library. It defines game domains (simulators) and agents that play them. It has no UI and no executable entry point; callers drive the game loop.

## Principles

- Domains are deterministic and have perfect information. The same state and actions always give the same next state.
- States are values. `Simulator.stateTransition` returns a new state and never changes the one passed in, so callers can keep old states for history and tree search.
- Every domain answers the same questions: initial state, legal actions per player, rewards per player, next state, number of players, and whether a state is terminal.
- An illegal transition always throws `IllegalActionException`, in every domain.

## Public API

- `Simulator<S, A>` — rules of one domain. Actions are passed as a list with one entry per player; `null` means that player takes no action. Legal actions come back the same way, one set per player.
- `Simulator.isLegalAction(state, agentTurn, action)` — whether one action is legal for a player. It always agrees with `calculateLegalActions`.
- `requireLegalAction` — the check simulators run in `stateTransition`. It takes either the player's set of legal actions or a predicate such as `isLegalAction`, and throws `IllegalActionException` with the same messages either way.
- `State<S>` — a domain state with a deep `copy()`. Equal states have equal hash codes, so states work as `HashSet` members and `HashMap` keys.
- `Action` — marker for a domain move. Actions are immutable and compare by value.
- `Agent` — picks an action for a player from a state, or `null` if it has none.
- `History<A>` — records the actions taken at each step, root first. It stores actions, not states.
- `LegalActions` helpers (`playerHasLegalActions`, `getPlayerActions`) in `util`.
- `GoSimulator.score(state)` — the Go area score as a `GoScore`: stones and territory for each side, and komi. Territory is empty points whose region reaches only that color; a region reaching both counts for neither. It works for any state, finished or not. `calculateRewards` decides the winner from these totals, so the two always agree, ties included.
- `ChessSimulator.isInCheck(state)` — whether the side to move is in check, meaning its king is attacked. A pinned piece still gives check. True at checkmate, false at stalemate.
- `AdversarialRewards` — two-player rewards: `[1, -1]` first player wins, `[-1, 1]` second player wins, `[0, 0]` otherwise.

A state is terminal when no player has a legal action.

## Domains

All domains are two-player and zero-sum. Player 0 moves first.

| Domain | Rules | Options |
| --- | --- | --- |
| Chess | Standard chess: castling, en passant, promotion, check, checkmate, stalemate. Stalemate, the fifty-move rule, and threefold repetition are automatic draws; mate is checked first. Repetition compares pieces, side to move, castling rights, and en passant only when a legal en passant capture exists. | none |
| Connect 4 | 7 wide, 6 high. Four in a row in any direction wins. | none |
| Draughts | International draughts on 10×10. Captures are mandatory and the longest sequence must be taken; kings fly. A multi-jump is several actions by the same player but one move. A player with no moves loses. Automatic draws (FMJD article 6): threefold repetition with the same player to move, 25 king moves each without a capture, and the endgame limits against a lone king (16 moves each when the other side has three pieces including a king, 5 moves each when it has one or two pieces including a king, counted from when that material is reached). The no-move loss is checked first. | none |
| Go | Positional superko, two passes end the game, area scoring (stones plus territory, komi to white). Two rule sets differ only in self-capture: Tromp–Taylor allows it if the position is new, and the mover's group is removed; Chinese forbids it, so a play must leave its own group a liberty after opponent captures. Player 0 is black. | board size, komi, rules (default 19, 7 and Tromp–Taylor) |
| Havannah | Hexagonal board. Win by a ring (a loop of stones around one or more cells, whatever they hold, including the player's own stones), a bridge (two corners), or a fork (three sides). Only the player who just moved is checked for a ring. | base 2 to 64, pie rule (default 10 and on) |
| Hex | Rhombus board. Player 0 connects one pair of sides, player 1 the other. | board size 3 to 128, pie rule (default 11 and on) |

Not implemented: chess draws by insufficient material.

Domain rules and options may be revised.

## Agents

- `RandomAgent` — picks a legal action uniformly at random. Takes an optional `Random` for reproducible play.
- `MctsAgent` — Monte Carlo Tree Search (UCT) for any domain, using only `Simulator`. Each player's statistics hold that player's own reward from `calculateRewards`, so it handles any number of players. Settings are an `MctsConfig`:
  - Budget: an iteration count, a time limit, or both; the search stops at the first reached. `search(playerId, state, simulator, shouldStop)` also stops when `shouldStop` returns true or the thread is interrupted, and still returns its best move so far. It returns an `MctsResult` with the move, the iterations run, and the root visit counts. A player with one legal action gets it without a search.
  - Strength: the budget, plus `temperature` for the final move: 0 plays the most visited move, above 0 draws a move with probability proportional to `visits^(1/temperature)`. `MctsLevel` gives five presets, `BEGINNER` to `EXPERT`.
  - Hooks: a `PlayoutPolicy` picks playout moves and reports, by returning null, when a playout state is truly terminal (default `UniformRandomPlayoutPolicy`); a policy that wants to stop early uses a `StateEvaluator` instead, and a joint action of the wrong shape throws `IllegalActionException` from `stateTransition`; the agent computes no legal actions for playout states itself, so a policy can avoid full legal-move generation. An optional `StateEvaluator` can end a playout with estimated rewards. Tree nodes compute their legal actions when first chosen from, not when added.
  - Playout cap: a playout stops after `maxPlayoutLength` steps. It is then scored by the evaluator if there is one, and otherwise as neutral, 0 for every player.
  - Takes a `Random`; a seeded agent repeats its moves. The search tree is local to each call, so a call can run on a background thread; one instance should not run two searches at once with a seeded `Random`.

## Non-goals

Current scope, open to change:

- Games with chance (dice, shuffled cards) or hidden information, for now. Backgammon and Yahtzee were removed; see [decisions.md](decisions.md).
- A user interface, network play, or a game-loop runner.
