package com.castlefrog.agl.domains.go

import com.castlefrog.agl.agents.MctsAgent
import com.castlefrog.agl.agents.MctsConfig
import com.castlefrog.agl.domains.hex.HexSimulator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GoPlayoutPolicyTest {

    // Eyes

    @Test
    fun centreEyeWithNoOpponentDiagonalIsAnEye() {
        val state = board(
            ". . . . .",
            ". X X X .",
            ". X . X .",
            ". X X X .",
            ". . . . ."
        )
        assertTrue(GoPlayoutPolicy.isOwnEye(state, 2, 2, GoState.LOCATION_BLACK))
        assertFalse(GoPlayoutPolicy.isOwnEye(state, 2, 2, GoState.LOCATION_WHITE))
    }

    @Test
    fun centreEyeWithOneOpponentDiagonalIsStillAnEye() {
        val state = board(
            ". . . . .",
            ". O X X .",
            ". X . X .",
            ". X X X .",
            ". . . . ."
        )
        assertTrue(GoPlayoutPolicy.isOwnEye(state, 2, 2, GoState.LOCATION_BLACK))
    }

    @Test
    fun centrePointWithTwoOpponentDiagonalsIsAFalseEye() {
        val state = board(
            ". . . . .",
            ". O X O .",
            ". X . X .",
            ". X X X .",
            ". . . . ."
        )
        assertFalse(GoPlayoutPolicy.isOwnEye(state, 2, 2, GoState.LOCATION_BLACK))
    }

    @Test
    fun edgeEyeNeedsNoOpponentDiagonal() {
        val eye = board(
            ". . . . .",
            ". . . . .",
            ". . . . .",
            ". X X X .",
            ". X . X ."
        )
        assertTrue(GoPlayoutPolicy.isOwnEye(eye, 2, 0, GoState.LOCATION_BLACK))
        val falseEye = board(
            ". . . . .",
            ". . . . .",
            ". . . . .",
            ". O X X .",
            ". X . X ."
        )
        assertFalse(GoPlayoutPolicy.isOwnEye(falseEye, 2, 0, GoState.LOCATION_BLACK))
    }

    @Test
    fun cornerEyeNeedsNoOpponentDiagonal() {
        val eye = board(
            ". . . . .",
            ". . . . .",
            ". . . . .",
            "X . . . .",
            ". X . . ."
        )
        assertTrue(GoPlayoutPolicy.isOwnEye(eye, 0, 0, GoState.LOCATION_BLACK))
        val falseEye = board(
            ". . . . .",
            ". . . . .",
            ". . . . .",
            "X O . . .",
            ". X . . ."
        )
        assertFalse(GoPlayoutPolicy.isOwnEye(falseEye, 0, 0, GoState.LOCATION_BLACK))
    }

    @Test
    fun pointWithAnEmptyOrOpponentNeighbourOrAStoneIsNotAnEye() {
        val state = board(
            ". . . . .",
            ". X X X .",
            ". X . . .",
            ". X O X .",
            ". . . . ."
        )
        assertFalse(GoPlayoutPolicy.isOwnEye(state, 2, 2, GoState.LOCATION_BLACK))
        assertFalse(GoPlayoutPolicy.isOwnEye(state, 2, 1, GoState.LOCATION_BLACK))
        assertFalse(GoPlayoutPolicy.isOwnEye(state, 1, 1, GoState.LOCATION_BLACK))
    }

    @Test
    fun moveCapIsThreePliesPerPoint() {
        assertEquals(3 * 13 * 13, GoPlayoutPolicy.moveCap(13))
    }

    // Moves

    @Test
    fun everyPlayoutMoveIsLegalAndPassesOnlyWhenNoNonEyePlacementIsLeft() {
        for (rules in GoRules.entries) {
            for (size in listOf(4, 5, 7)) {
                for (tactics in listOf(true, false)) {
                    val simulator = GoSimulator(boardSize = size, rules = rules)
                    val policy = GoPlayoutPolicy(preferTactics = tactics)
                    repeat(12) { seed ->
                        playOut(simulator, policy, Random(seed)) { state, action ->
                            val label = "$rules size $size tactics $tactics seed $seed\n$state"
                            val legal = simulator.calculateLegalActions(state)[state.agentTurn.toInt()]
                            assertTrue("$action $label", action in legal)
                            val stone = stoneToMove(state)
                            val nonEye = legal.filter { !it.isPass && !GoPlayoutPolicy.isOwnEye(state, it.x, it.y, stone) }
                            if (action.isPass) {
                                assertTrue("passed with $nonEye left: $label", nonEye.isEmpty())
                            } else {
                                assertFalse("filled own eye $action: $label", GoPlayoutPolicy.isOwnEye(state, action.x, action.y, stone))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun fastLegalityCheckAgreesWithTheSimulatorAtEveryPoint() {
        for (rules in GoRules.entries) {
            for (size in listOf(3, 4, 5, 6)) {
                val simulator = GoSimulator(boardSize = size, rules = rules)
                repeat(10) { seed ->
                    playOut(simulator, GoPlayoutPolicy(preferTactics = seed % 2 == 0), Random(100 + seed)) { state, _ ->
                        assertAgreesWithSimulator(simulator, state)
                    }
                }
            }
        }
    }

    @Test
    fun fastLegalityCheckAgreesOnKoSuicideAndSelfCaptureFixtures() {
        val fixtures = listOf(
            // The position just after black takes a one-stone ko, with no history: here the
            // retake at (1,1) is legal. A real ko is in koRetakeFollowsPositionalSuperko.
            board(
                ". . . .",
                ". X O .",
                "X . X O",
                ". X O ."
            ).also { it.agentTurn = GoState.TURN_WHITE },
            // Single-stone suicide at the centre.
            board(
                ". O .",
                "O . O",
                ". O ."
            ),
            // Multi-stone self-capture at (2,1): legal only under Tromp–Taylor.
            board(
                ". O O .",
                "O X X O",
                "O X . O",
                ". O O ."
            ),
            // Filling the own last liberty while capturing at (2,0).
            board(
                ". . . .",
                ". . . .",
                "O O O X",
                "X X . O"
            )
        )
        for (rules in GoRules.entries) {
            val simulator = GoSimulator(boardSize = 4, rules = rules)
            for (fixture in fixtures) {
                val sim = if (fixture.boardSize == 4) simulator else GoSimulator(boardSize = fixture.boardSize, rules = rules)
                assertAgreesWithSimulator(sim, fixture)
            }
        }
    }

    @Test
    fun koRetakeFollowsPositionalSuperko() {
        val simulator = GoSimulator(boardSize = 4)
        val state = board(
            ". . . .",
            ". X O .",
            "X O . O",
            ". X O ."
        )
        state.recordCurrentColoring()
        val afterTake = simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))
        val retake = 1 * 4 + 1
        assertFalse(GoBoard(afterTake, simulator).isLegal(retake))
        assertAgreesWithSimulator(simulator, afterTake)
    }

    @Test
    fun aGroupWithNoLibertyFallsBackToTheSimulator() {
        // Not reachable in play: the white stone at (0,0) has no liberty. Black at (2,0)
        // leaves black's group no liberty, but the simulator first clears the dead white
        // stone, which gives it one, so the move is legal even under Chinese rules.
        val state = board(
            ". . . . .",
            ". . . . .",
            "O O . . .",
            "X X O . .",
            "O X . O ."
        )
        val simulator = GoSimulator(boardSize = 5, rules = GoRules.CHINESE)
        assertTrue(simulator.isLegalAction(state, 0, GoAction.place(2, 0)))
        assertTrue(GoBoard(state, simulator).isLegal(2 * 5 + 0))
        for (rules in GoRules.entries) {
            assertAgreesWithSimulator(GoSimulator(boardSize = 5, rules = rules), state)
        }
    }

    @Test
    fun capturesAnOpponentGroupInAtariFirst() {
        val simulator = GoSimulator(boardSize = 5)
        // White (1,1) has one liberty, at (2,1).
        val state = board(
            ". . . . .",
            ". . . . .",
            ". X . . .",
            "X O . . .",
            ". X . . ."
        )
        repeat(10) { seed ->
            assertEquals(GoAction.place(2, 1), GoPlayoutPolicy(preferTactics = true).selectAction(state, simulator, Random(seed)))
        }
    }

    @Test
    fun extendsAnOwnGroupInAtariWhenNothingCanBeCaptured() {
        val simulator = GoSimulator(boardSize = 5)
        // Black (1,1) has one liberty, at (2,1), where it gets three.
        val state = board(
            ". . . . .",
            ". . . . .",
            ". O . . .",
            "O X . . .",
            ". O . . ."
        )
        repeat(10) { seed ->
            assertEquals(GoAction.place(2, 1), GoPlayoutPolicy(preferTactics = true).selectAction(state, simulator, Random(seed)))
        }
    }

    @Test
    fun passesWhenOnlyOwnEyesAreLeft() {
        val simulator = GoSimulator(boardSize = 3, rules = GoRules.CHINESE)
        // Black's two eyes at (0,0) and (2,2); every other point is black.
        val state = board(
            "X X .",
            "X X X",
            ". X X"
        )
        state.recordCurrentColoring()
        assertEquals(GoAction.pass(), GoPlayoutPolicy().selectAction(state, simulator, Random(1)))
    }

    @Test
    fun returnsNullOnlyAfterTwoPasses() {
        val simulator = GoSimulator(boardSize = 5)
        var state = simulator.initialState
        assertNotNull(GoPlayoutPolicy().selectActions(state, simulator, Random(1)))
        state = simulator.stateTransition(state, listOf(GoAction.pass(), null))
        assertNotNull(GoPlayoutPolicy().selectActions(state, simulator, Random(1)))
        state = simulator.stateTransition(state, listOf(null, GoAction.pass()))
        assertNull(GoPlayoutPolicy().selectActions(state, simulator, Random(1)))
    }

    @Test
    fun selectActionsGivesTheMoveToThePlayerToMove() {
        val simulator = GoSimulator(boardSize = 5)
        val black = GoPlayoutPolicy().selectActions(simulator.initialState, simulator, Random(1))!!
        assertNotNull(black[0])
        assertNull(black[1])
        val next = simulator.stateTransition(simulator.initialState, black)
        val white = GoPlayoutPolicy().selectActions(next, simulator, Random(1))!!
        assertNull(white[0])
        assertNotNull(white[1])
    }

    @Test
    fun playoutsEndBeforeTheMoveCap() {
        for (rules in GoRules.entries) {
            for (size in listOf(5, 7, 9)) {
                val simulator = GoSimulator(boardSize = size, rules = rules)
                for (tactics in listOf(true, false)) {
                    repeat(10) { seed ->
                        val plies = playOut(simulator, GoPlayoutPolicy(tactics), Random(seed)) { _, _ -> }
                        assertTrue("$rules size $size tactics $tactics seed $seed: $plies plies", plies < GoPlayoutPolicy.moveCap(size))
                    }
                }
            }
        }
    }

    @Test
    fun doesNotChangeTheState() {
        val simulator = GoSimulator(boardSize = 5)
        var state = simulator.initialState
        val random = Random(3)
        repeat(30) {
            val before = state.copy()
            val actions = GoPlayoutPolicy().selectActions(state, simulator, random)!!
            assertEquals(before, state)
            state = simulator.stateTransition(state, actions)
        }
    }

    @Test
    fun seededPlayoutsRepeat() {
        val simulator = GoSimulator(boardSize = 7)
        val first = ArrayList<GoAction>()
        val second = ArrayList<GoAction>()
        playOut(simulator, GoPlayoutPolicy(), Random(9)) { _, action -> first.add(action) }
        playOut(simulator, GoPlayoutPolicy(), Random(9)) { _, action -> second.add(action) }
        assertEquals(first, second)
    }

    @Test
    fun rejectsOtherDomains() {
        val hex = HexSimulator(boardSize = 5)
        assertThrows(IllegalArgumentException::class.java) {
            GoPlayoutPolicy().selectActions(hex.initialState, hex, Random(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GoAreaEvaluator.evaluate(hex.initialState, hex, 0, true)
        }
    }

    // Evaluator

    @Test
    fun evaluatorWaitsForTheCap() {
        val simulator = GoSimulator(boardSize = 5)
        assertNull(GoAreaEvaluator.evaluate(simulator.initialState, simulator, 10, false))
    }

    @Test
    fun evaluatorScoresByAreaAtTheCap() {
        // Each side has 5 stones and 5 points of territory; the middle column is neither's.
        val even = board(
            ". X . O .",
            ". X . O .",
            ". X . O .",
            ". X . O .",
            ". X . O ."
        )
        assertArrayEquals(doubleArrayOf(0.0, 0.0), evaluate(even, komi = 0.0), 0.0)
        assertArrayEquals(doubleArrayOf(-1.0, 1.0), evaluate(even, komi = 0.5), 0.0)
        even.set(2, 0, GoState.LOCATION_BLACK)
        assertArrayEquals(doubleArrayOf(1.0, -1.0), evaluate(even, komi = 0.5), 0.0)
    }

    @Test
    fun mctsAgentPlaysLegalGoMovesWithThePolicyAndEvaluator() {
        val simulator = GoSimulator(boardSize = 5, rules = GoRules.CHINESE)
        val agent = MctsAgent(
            MctsConfig(
                iterations = 200,
                maxPlayoutLength = GoPlayoutPolicy.moveCap(5),
                playoutPolicy = GoPlayoutPolicy(),
                evaluator = GoAreaEvaluator
            ),
            Random(5)
        )
        var state = simulator.initialState
        repeat(6) {
            val turn = state.agentTurn.toInt()
            val action = agent.selectAction(turn, state, simulator)!!
            assertTrue("$action", simulator.isLegalAction(state, turn, action))
            state = simulator.stateTransition(state, List(2) { if (it == turn) action else null })
        }
    }

    private fun evaluate(state: GoState, komi: Double): DoubleArray =
        GoAreaEvaluator.evaluate(state, GoSimulator(boardSize = state.boardSize, komi = komi), 75, true)!!

    /** Asserts [GoBoard.isLegal] matches the simulator's legal set at every point of [state]. */
    private fun assertAgreesWithSimulator(simulator: GoSimulator, state: GoState) {
        if (state.consecutivePasses >= 2) return
        val legal = simulator.calculateLegalActions(state)[state.agentTurn.toInt()]
        val board = GoBoard(state, simulator)
        for (x in 0 until state.boardSize) {
            for (y in 0 until state.boardSize) {
                assertEquals(
                    "(${simulator.rules}) $x,$y\n$state",
                    GoAction.place(x, y) in legal,
                    board.isLegal(x * state.boardSize + y)
                )
            }
        }
    }

    /** Plays [policy] from the initial state to the end, calling [onMove] before each move. Returns the plies. */
    private fun playOut(
        simulator: GoSimulator,
        policy: GoPlayoutPolicy,
        random: Random,
        onMove: (GoState, GoAction) -> Unit
    ): Int {
        var state = simulator.initialState
        var plies = 0
        val cap = GoPlayoutPolicy.moveCap(simulator.boardSize)
        while (plies < cap) {
            val action = policy.selectAction(state, simulator, random) ?: break
            onMove(state, action)
            val turn = state.agentTurn.toInt()
            state = simulator.stateTransition(state, List(2) { if (it == turn) action else null })
            plies += 1
        }
        return plies
    }

    private fun stoneToMove(state: GoState): Byte =
        if (state.agentTurn == GoState.TURN_BLACK) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE

    /** A board from rows drawn top (highest y) first, as [GoState.toString] prints them; black to move. */
    private fun board(vararg rows: String): GoState {
        val size = rows.size
        val state = GoState(boardSize = size)
        for ((row, line) in rows.withIndex()) {
            val cells = line.split(' ')
            val y = size - 1 - row
            for (x in 0 until size) {
                when (cells[x]) {
                    "X" -> state.set(x, y, GoState.LOCATION_BLACK)
                    "O" -> state.set(x, y, GoState.LOCATION_WHITE)
                }
            }
        }
        return state
    }
}
