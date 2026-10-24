package com.castlefrog.agl.agents

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import com.castlefrog.agl.TestAction
import com.castlefrog.agl.TestSimulator
import com.castlefrog.agl.TestState
import com.castlefrog.agl.domains.chess.ChessSimulator
import com.castlefrog.agl.domains.connect4.Connect4Action
import com.castlefrog.agl.domains.connect4.Connect4Simulator
import com.castlefrog.agl.domains.connect4.Connect4State
import com.castlefrog.agl.domains.draughts.DraughtsSimulator
import com.castlefrog.agl.domains.go.GoRules
import com.castlefrog.agl.domains.go.GoSimulator
import com.castlefrog.agl.domains.havannah.HavannahSimulator
import com.castlefrog.agl.domains.hex.HexAction
import com.castlefrog.agl.domains.hex.HexSimulator
import com.castlefrog.agl.domains.hex.HexState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class MctsAgentTest {

    // Connect 4

    @Test
    fun connect4PlaysImmediateWin() {
        val simulator = Connect4Simulator()
        // Player 0 has three in the bottom row (columns 0-2) and is to move.
        val state = play(simulator, simulator.initialState, listOf(0, 6, 1, 6, 2, 5).map(Connect4Action::valueOf))
        val winning = winningMoves(simulator, state, 0)
        assertEquals(setOf(Connect4Action.valueOf(3)), winning)

        val action = MctsAgent(MctsConfig(iterations = 500), Random(1)).selectAction(0, state, simulator)

        assertTrue("$action", action in winning)
    }

    @Test
    fun connect4BlocksImmediateLoss() {
        val simulator = Connect4Simulator()
        // Player 0 threatens column 3 and player 1 is to move.
        val state = play(simulator, simulator.initialState, listOf(0, 6, 1, 6, 2).map(Connect4Action::valueOf))
        val safe = nonLosingMoves(simulator, state, 1)
        assertEquals(setOf(Connect4Action.valueOf(3)), safe)

        val action = MctsAgent(MctsConfig(iterations = 1000), Random(2)).selectAction(1, state, simulator)

        assertTrue("$action", action in safe)
    }

    // Hex

    @Test
    fun hexPlaysImmediateWin() {
        val simulator = HexSimulator(boardSize = 5, pieRule = false)
        val state = hexThreat(simulator, blackToMove = true)
        val winning = winningMoves(simulator, state, 0)
        assertEquals(setOf(HexAction(4, 2)), winning)

        val action = MctsAgent(MctsConfig(iterations = 500), Random(3)).selectAction(0, state, simulator)

        assertTrue("$action", action in winning)
    }

    @Test
    fun hexBlocksImmediateLoss() {
        val simulator = HexSimulator(boardSize = 5, pieRule = false)
        val state = hexThreat(simulator, blackToMove = false)
        val safe = nonLosingMoves(simulator, state, 1)
        assertEquals(setOf(HexAction(4, 2)), safe)

        val action = MctsAgent(MctsConfig(iterations = 1000), Random(4)).selectAction(1, state, simulator)

        assertTrue("$action", action in safe)
    }

    @Test
    fun hexBeatsRandomAgent() {
        val simulator = HexSimulator(boardSize = 5, pieRule = false)
        val games = 10
        var wins = 0
        for (game in 0 until games) {
            val mctsPlayer = game % 2
            val mcts = MctsAgent(MctsConfig(iterations = 200), Random(100L + game))
            val random = RandomAgent(Random(200L + game))
            var state = simulator.initialState
            while (!simulator.isTerminalState(state)) {
                val mover = mover(simulator, state)
                val agent = if (mover == mctsPlayer) mcts else random
                val action = agent.selectAction(mover, state, simulator)
                state = simulator.stateTransition(state, actionsFor(mover, action, 2))
            }
            if (simulator.calculateRewards(state)[mctsPlayer] > 0) {
                wins++
            }
        }
        assertTrue("won $wins of $games", wins >= 8)
    }

    // Every domain

    @Test
    fun returnsLegalMoveInEveryDomain() {
        assertLegalMoves(Connect4Simulator())
        assertLegalMoves(HexSimulator(boardSize = 5))
        assertLegalMoves(HavannahSimulator(base = 5))
        assertLegalMoves(ChessSimulator())
        assertLegalMoves(DraughtsSimulator())
        assertLegalMoves(GoSimulator(boardSize = 5))
        assertLegalMoves(GoSimulator(boardSize = 5, rules = GoRules.CHINESE))
    }

    private fun <S : State<S>, A : Action> assertLegalMoves(simulator: Simulator<S, A>) {
        val random = Random(7)
        val agent = MctsAgent(MctsConfig(iterations = 16, maxPlayoutLength = 40), Random(8))
        var state = simulator.initialState
        repeat(4) {
            if (simulator.isTerminalState(state)) return
            val mover = mover(simulator, state)
            val action = agent.selectAction(mover, state, simulator)
            assertTrue("$simulator: $action", action != null && simulator.isLegalAction(state, mover, action))
            // Advance with random moves so later checks are not all from the opening.
            repeat(3) {
                if (!simulator.isTerminalState(state)) {
                    val legal = simulator.calculateLegalActions(state)
                    state = simulator.stateTransition(state, legal.map { if (it.isEmpty()) null else it.random(random) })
                }
            }
        }
    }

    @Test
    fun noLegalActionReturnsNull() {
        val simulator = TestSimulator(TestState(), listOf(emptySet(), setOf(TestAction(1))), intArrayOf(0, 0))
        val result = MctsAgent(MctsConfig(iterations = 10), Random(0)).search(0, simulator.initialState, simulator)
        assertNull(result.action)
        assertEquals(0, result.iterations)
    }

    @Test
    fun singleLegalActionSkipsSearch() {
        val simulator = TestSimulator(TestState(), listOf(setOf(TestAction(5))), intArrayOf(0))
        val result = MctsAgent(MctsConfig(iterations = 10), Random(0)).search(0, simulator.initialState, simulator)
        assertEquals(TestAction(5), result.action)
        assertEquals(0, result.iterations)
    }

    // Budget and cancellation

    @Test
    fun respectsIterationBudget() {
        val simulator = Connect4Simulator()
        val result = MctsAgent(MctsConfig(iterations = 37), Random(9)).search(0, simulator.initialState, simulator)
        assertEquals(37, result.iterations)
        assertEquals(37, result.visits.values.sum())
    }

    @Test
    fun timeLimitStopsSearch() {
        val simulator = GoSimulator(boardSize = 5)
        val agent = MctsAgent(MctsConfig(iterations = null, timeLimit = 50.milliseconds), Random(10))
        val start = System.nanoTime()
        val result = agent.search(0, simulator.initialState, simulator)
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $elapsedMillis ms", elapsedMillis < 5_000)
        assertTrue(result.iterations > 0)
        assertTrue(simulator.isLegalAction(simulator.initialState, 0, result.action!!))
    }

    @Test
    fun stopsWhenShouldStopReturnsTrue() {
        val simulator = Connect4Simulator()
        var checks = 0
        val result = MctsAgent(MctsConfig(iterations = 1000), Random(11))
            .search(0, simulator.initialState, simulator) { checks++ >= 5 }
        assertEquals(5, result.iterations)
    }

    @Test
    fun cancelledBeforeStartStillReturnsLegalMove() {
        val simulator = Connect4Simulator()
        val result = MctsAgent(MctsConfig(iterations = 1000), Random(12))
            .search(0, simulator.initialState, simulator) { true }
        assertEquals(0, result.iterations)
        assertTrue(simulator.isLegalAction(simulator.initialState, 0, result.action!!))
    }

    @Test
    fun stopsWhenCancelledFromAnotherThread() {
        val simulator = Connect4Simulator()
        val agent = MctsAgent(MctsConfig(iterations = Int.MAX_VALUE), Random(13))
        val cancelled = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val result = AtomicReference<MctsResult<Connect4Action>>()
        val thread = Thread {
            result.set(
                agent.search(0, simulator.initialState, simulator) {
                    started.countDown()
                    cancelled.get()
                }
            )
        }
        thread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        cancelled.set(true)
        thread.join(5_000)
        assertFalse(thread.isAlive)
        assertTrue(simulator.isLegalAction(simulator.initialState, 0, result.get().action!!))
    }

    @Test
    fun stopsWhenThreadIsInterrupted() {
        val simulator = Connect4Simulator()
        Thread.currentThread().interrupt()
        try {
            val result = MctsAgent(MctsConfig(iterations = 1000), Random(14))
                .search(0, simulator.initialState, simulator)
            assertEquals(0, result.iterations)
        } finally {
            Thread.interrupted()
        }
    }

    // Determinism and strength

    @Test
    fun sameSeedGivesSameMove() {
        val simulator = Connect4Simulator()
        val config = MctsConfig(iterations = 200, temperature = 1.0)
        val first = MctsAgent(config, Random(15)).search(0, simulator.initialState, simulator)
        val second = MctsAgent(config, Random(15)).search(0, simulator.initialState, simulator)
        assertEquals(first, second)
    }

    @Test
    fun zeroTemperaturePlaysMostVisitedMove() {
        val simulator = prefersTwo()
        repeat(20) { seed ->
            val result = MctsAgent(MctsConfig(iterations = 60), Random(seed)).search(0, simulator.initialState, simulator)
            assertEquals(TestAction(2), result.action)
            assertEquals(result.visits.maxBy { it.value }.key, result.action)
        }
    }

    @Test
    fun highTemperatureVariesTheMove() {
        val simulator = prefersTwo()
        val config = MctsConfig(iterations = 60, temperature = 100.0)
        val actions = (0 until 30).map { MctsAgent(config, Random(it)).selectAction(0, simulator.initialState, simulator) }
        assertTrue("$actions", actions.toSet().size > 1)
    }

    @Test
    fun levelsGetStronger() {
        val levels = MctsLevel.entries
        for (i in 1 until levels.size) {
            assertTrue(levels[i].iterations > levels[i - 1].iterations)
            assertTrue(levels[i].temperature <= levels[i - 1].temperature)
        }
        assertEquals(MctsLevel.HARD.iterations, MctsLevel.HARD.config().iterations)
        assertEquals(0.0, MctsLevel.EXPERT.config().temperature, 0.0)
    }

    // Hooks

    @Test
    fun playoutPolicyPicksPlayoutMoves() {
        val simulator = Connect4Simulator()
        var calls = 0
        var terminals = 0
        val leftmost = object : PlayoutPolicy {
            override fun <S : State<S>, A : Action> selectActions(
                state: S,
                simulator: Simulator<S, A>,
                random: Random
            ): List<A?>? {
                calls++
                val legalActions = simulator.calculateLegalActions(state)
                if (legalActions.all { it.isEmpty() }) {
                    terminals++
                    return null
                }
                return legalActions.map { actions -> actions.minByOrNull { (it as Connect4Action).location } }
            }
        }
        val result = MctsAgent(MctsConfig(iterations = 20, playoutPolicy = leftmost), Random(16))
            .search(0, simulator.initialState, simulator)
        assertEquals(20, result.iterations)
        assertTrue(calls > 0)
        // Every playout from a non-terminal leaf ends when the policy reports the end.
        assertEquals(20, terminals)
    }

    @Test
    fun evaluatorEndsPlayoutsAndSteersSearch() {
        // An endless game whose state is fixed by the first move.
        val simulator = TestSimulator(
            initialState = TestState(0),
            legalActions = listOf(setOf(TestAction(1), TestAction(2), TestAction(3))),
            rewards = intArrayOf(0),
            testStateTransition = { state, actions -> if (state.value == 0) TestState(actions[0]!!.value) else state }
        )
        val lengths = mutableListOf<Int>()
        val evaluator = object : StateEvaluator {
            override fun <S : State<S>, A : Action> evaluate(
                state: S,
                simulator: Simulator<S, A>,
                playoutLength: Int,
                atCap: Boolean
            ): DoubleArray {
                lengths += playoutLength
                return doubleArrayOf(if ((state as TestState).value == 3) 1.0 else -1.0)
            }
        }
        val result = MctsAgent(MctsConfig(iterations = 50, evaluator = evaluator), Random(17))
            .search(0, simulator.initialState, simulator)
        assertEquals(TestAction(3), result.action)
        assertEquals(setOf(0), lengths.toSet())
    }

    @Test
    fun evaluatorIsToldWhenPlayoutReachesCap() {
        val simulator = endlessGame()
        val calls = mutableListOf<Pair<Int, Boolean>>()
        val evaluator = object : StateEvaluator {
            override fun <S : State<S>, A : Action> evaluate(
                state: S,
                simulator: Simulator<S, A>,
                playoutLength: Int,
                atCap: Boolean
            ): DoubleArray? {
                calls += playoutLength to atCap
                return null
            }
        }
        MctsAgent(MctsConfig(iterations = 5, maxPlayoutLength = 7, evaluator = evaluator), Random(18))
            .search(0, simulator.initialState, simulator)
        assertEquals(7, calls.maxOf { it.first })
        assertTrue(calls.all { (length, atCap) -> atCap == (length == 7) })
        assertEquals(5, calls.count { it.second })
    }

    @Test
    fun endlessGameWithoutEvaluatorEndsAtCap() {
        val simulator = endlessGame()
        val result = MctsAgent(MctsConfig(iterations = 30, maxPlayoutLength = 10), Random(19))
            .search(0, simulator.initialState, simulator)
        assertEquals(30, result.iterations)
        assertTrue(result.action in simulator.calculateLegalActions(simulator.initialState)[0])
    }

    @Test
    fun playoutAtCapStillScoresAFinishedGame() {
        // With no playout steps allowed, the search relies on the cap's terminal check.
        val simulator = Connect4Simulator()
        val state = play(simulator, simulator.initialState, listOf(0, 6, 1, 6, 2, 5).map(Connect4Action::valueOf))
        val result = MctsAgent(MctsConfig(iterations = 7, maxPlayoutLength = 0), Random(20)).search(0, state, simulator)
        assertEquals(Connect4Action.valueOf(3), result.action)
    }

    @Test
    fun terminalNodeRewardsAreComputedOnce() {
        // A one-move game: both children of the root are terminal and revisited often.
        var rewardCalls = 0
        val actions = setOf(TestAction(1), TestAction(2))
        val simulator = object : Simulator<TestState, TestAction> {
            override val initialState = TestState(0)
            override fun calculateRewards(state: TestState): IntArray {
                rewardCalls++
                return intArrayOf(if (state.value == 2) 1 else -1)
            }
            override fun calculateLegalActions(state: TestState) = listOf(if (state.value == 0) actions else emptySet())
            override fun stateTransition(state: TestState, actions: List<TestAction?>) = TestState(actions[0]!!.value)
            override fun numberOfPlayers() = 1
        }
        val result = MctsAgent(MctsConfig(iterations = 40), Random(21)).search(0, simulator.initialState, simulator)
        assertEquals(TestAction(2), result.action)
        // One call from each child's first playout and one when each is first revisited.
        assertTrue("$rewardCalls calls", rewardCalls <= 4)
    }

    @Test
    fun configRejectsInvalidSettings() {
        assertThrows(IllegalArgumentException::class.java) { MctsConfig(iterations = null, timeLimit = null) }
        assertThrows(IllegalArgumentException::class.java) { MctsConfig(iterations = 0) }
        assertThrows(IllegalArgumentException::class.java) { MctsConfig(temperature = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { MctsConfig(maxPlayoutLength = -1) }
        assertThrows(IllegalArgumentException::class.java) { MctsConfig(explorationConstant = -1.0) }
    }

    // Helpers

    /** A one-player game that never ends; each state remembers the last action. */
    private fun endlessGame() = TestSimulator(
        initialState = TestState(0),
        legalActions = listOf(setOf(TestAction(1), TestAction(2), TestAction(3))),
        rewards = intArrayOf(0),
        testStateTransition = { _, actions -> TestState(actions[0]!!.value) }
    )

    /** A one-player game of one move: action 2 scores 1, the others -1. */
    private fun prefersTwo(): Simulator<TestState, TestAction> {
        val actions = setOf(TestAction(1), TestAction(2), TestAction(3))
        return object : Simulator<TestState, TestAction> {
            override val initialState = TestState(0)
            override fun calculateRewards(state: TestState) = intArrayOf(if (state.value == 2) 1 else -1)
            override fun calculateLegalActions(state: TestState) = listOf(if (state.value == 0) actions else emptySet())
            override fun stateTransition(state: TestState, actions: List<TestAction?>) = TestState(actions[0]!!.value)
            override fun numberOfPlayers() = 1
        }
    }

    /**
     * Black has four stones in a line from one of its sides on a 5×5 board and one cell
     * left to win, (4, 2); white holds the other cell next to the line's end, (4, 3), and
     * has its other stones on its own edge row.
     */
    private fun hexThreat(simulator: HexSimulator, blackToMove: Boolean): HexState {
        val black = (0 until 4).map { HexAction(it.toByte(), 2) }
        val white = listOf(HexAction(4, 3), HexAction(0, 0), HexAction(1, 0), HexAction(2, 0))
            .take(if (blackToMove) 4 else 3)
        val moves = black.indices.flatMap { listOfNotNull(black[it], white.getOrNull(it)) }
        return play(simulator, simulator.initialState, moves)
    }

    private fun <S : State<S>, A : Action> play(simulator: Simulator<S, A>, start: S, moves: List<A>): S =
        moves.fold(start) { state, move ->
            simulator.stateTransition(state, actionsFor(mover(simulator, state), move, simulator.numberOfPlayers()))
        }

    private fun <S : State<S>, A : Action> winningMoves(simulator: Simulator<S, A>, state: S, player: Int): Set<A> =
        simulator.calculateLegalActions(state)[player].filter { action ->
            val next = simulator.stateTransition(state, actionsFor(player, action, simulator.numberOfPlayers()))
            simulator.calculateRewards(next)[player] > 0
        }.toSet()

    private fun <S : State<S>, A : Action> nonLosingMoves(simulator: Simulator<S, A>, state: S, player: Int): Set<A> =
        simulator.calculateLegalActions(state)[player].filter { action ->
            val next = simulator.stateTransition(state, actionsFor(player, action, simulator.numberOfPlayers()))
            simulator.isTerminalState(next) || winningMoves(simulator, next, 1 - player).isEmpty()
        }.toSet()

    private fun <S : State<S>, A : Action> mover(simulator: Simulator<S, A>, state: S): Int =
        simulator.calculateLegalActions(state).indexOfFirst { it.isNotEmpty() }

    private fun <A : Action> actionsFor(player: Int, action: A?, players: Int): List<A?> =
        List(players) { if (it == player) action else null }
}
