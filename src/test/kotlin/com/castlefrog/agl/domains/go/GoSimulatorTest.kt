package com.castlefrog.agl.domains.go

import com.castlefrog.agl.IllegalActionException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoSimulatorTest {

    @Test
    fun defaultBoardSizeIs19() {
        val simulator = GoSimulator()
        assertEquals(19, simulator.boardSize)
        assertEquals(19, simulator.initialState.boardSize)
        assertEquals(7.0, simulator.komi, 0.0)
        assertEquals(GoState.DEFAULT_BOARD_SIZE, 19)
    }

    @Test
    fun initialStateEmpty() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.initialState
        assertEquals(5, state.boardSize)
        assertEquals(GoState.TURN_BLACK, state.agentTurn)
        assertEquals(0, state.consecutivePasses)
        assertEquals(1, state.positionHistory.size)
    }

    @Test
    fun placeStoneAndSwitchTurn() {
        val simulator = GoSimulator(boardSize = 5)
        val next = simulator.stateTransition(
            simulator.initialState,
            listOf(GoAction.place(2, 2), null)
        )
        assertEquals(GoState.LOCATION_BLACK, next.get(2, 2))
        assertEquals(GoState.TURN_WHITE, next.agentTurn)
        assertEquals(2, next.positionHistory.size)
    }

    @Test
    fun captureSingleStone() {
        val simulator = GoSimulator(boardSize = 5)
        // Build: white stone at (1,0) surrounded after black plays (0,0) with black already on (1,1) and (2,0)
        var state = simulator.initialState
        state = simulator.stateTransition(state, listOf(GoAction.place(1, 1), null))
        state = simulator.stateTransition(state, listOf(null, GoAction.place(1, 0)))
        state = simulator.stateTransition(state, listOf(GoAction.place(2, 0), null))
        state = simulator.stateTransition(state, listOf(null, GoAction.place(3, 3))) // white elsewhere
        state = simulator.stateTransition(state, listOf(GoAction.place(0, 0), null))
        assertEquals(GoState.LOCATION_EMPTY, state.get(1, 0))
        assertEquals(1, state.capturedByBlack)
    }

    @Test
    fun singleStoneSuicideIsIllegalBecauseItRepeatsThePosition() {
        val simulator = GoSimulator(boardSize = 3)
        val state = GoState(boardSize = 3)
        // White surrounds (1,1): playing there places then self-clears, restoring this coloring.
        state.set(0, 1, GoState.LOCATION_WHITE)
        state.set(2, 1, GoState.LOCATION_WHITE)
        state.set(1, 0, GoState.LOCATION_WHITE)
        state.set(1, 2, GoState.LOCATION_WHITE)
        state.agentTurn = GoState.TURN_BLACK
        val legal = simulator.calculateLegalActions(state)[0]
        assertFalse(legal.contains(GoAction.place(1, 1)))
    }

    @Test
    fun positionalSuperkoForbidsImmediateKoRecapture() {
        val simulator = GoSimulator(boardSize = 4)
        val state = koFixture()

        val afterTake = simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))
        assertEquals(GoState.LOCATION_EMPTY, afterTake.get(1, 1))
        assertEquals(GoState.LOCATION_BLACK, afterTake.get(2, 1))

        val whiteLegal = simulator.calculateLegalActions(afterTake)[1]
        assertFalse(whiteLegal.contains(GoAction.place(1, 1)))
    }

    @Test
    fun twoPassesEndsGameWithScoring() {
        val simulator = GoSimulator(boardSize = 5, komi = 0.0)
        var state = simulator.initialState
        state = simulator.stateTransition(state, listOf(GoAction.place(0, 0), null))
        state = simulator.stateTransition(state, listOf(null, GoAction.pass()))
        state = simulator.stateTransition(state, listOf(GoAction.pass(), null))
        // Two consecutive passes after white pass + black pass → consecutivePasses == 2
        assertEquals(2, state.consecutivePasses)
        val rewards = simulator.calculateRewards(state)
        // Black has a stone and some territory; with komi 0 black should win or at least not lose blindly
        assertTrue(rewards[0] >= rewards[1])
    }

    @Test
    fun emptyActionsThrows() {
        val simulator = GoSimulator(boardSize = 5)
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, emptyList())
        }
    }

    @Test
    fun illegalMoveThrows() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.stateTransition(simulator.initialState, listOf(GoAction.place(1, 1), null))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(null, GoAction.place(1, 1)))
        }
    }

    @Test
    fun rewardsNeutralMidGame() {
        val simulator = GoSimulator(boardSize = 5)
        assertArrayEquals(
            intArrayOf(0, 0),
            simulator.calculateRewards(simulator.initialState)
        )
    }

    @Test
    fun passIsAlwaysLegalWhenGameOngoing() {
        val simulator = GoSimulator(boardSize = 5)
        val legal = simulator.calculateLegalActions(simulator.initialState)[0]
        assertTrue(legal.contains(GoAction.pass()))
    }

    @Test
    fun occupiedPointIsIllegal() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.stateTransition(simulator.initialState, listOf(GoAction.place(1, 1), null))
        assertFalse(simulator.isLegalAction(state, 1, GoAction.place(1, 1)))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(null, GoAction.place(1, 1)))
        }
    }

    @Test
    fun superkoRepeatIsIllegal() {
        val simulator = GoSimulator(boardSize = 4)
        val state = koFixture()
        val afterTake = simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))

        assertFalse(simulator.isLegalAction(afterTake, 1, GoAction.place(1, 1)))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(afterTake, listOf(null, GoAction.place(1, 1)))
        }
    }

    @Test
    fun wrongPlayerIsIllegal() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.initialState
        assertFalse(simulator.isLegalAction(state, 1, GoAction.place(2, 2)))
        assertFalse(simulator.isLegalAction(state, 1, GoAction.pass()))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(null, GoAction.place(2, 2)))
        }
    }

    @Test
    fun moveAfterTwoPassesIsIllegal() {
        val simulator = GoSimulator(boardSize = 5)
        var state = simulator.initialState
        state = simulator.stateTransition(state, listOf(GoAction.pass(), null))
        state = simulator.stateTransition(state, listOf(null, GoAction.pass()))
        assertFalse(simulator.isLegalAction(state, 0, GoAction.place(2, 2)))
        assertFalse(simulator.isLegalAction(state, 0, GoAction.pass()))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(GoAction.place(2, 2), null))
        }
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(GoAction.pass(), null))
        }
    }

    @Test
    fun offBoardPlacementIsIllegal() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.initialState
        assertFalse(simulator.isLegalAction(state, 0, GoAction.place(5, 0)))
        assertFalse(simulator.isLegalAction(state, 0, GoAction.place(-1, 2)))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(GoAction.place(5, 0), null))
        }
    }

    @Test
    fun passWithCoordinatesIsIllegalLikeCalculateLegalActions() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.initialState
        val oddPass = GoAction(x = 2, y = 2, isPass = true)
        assertFalse(simulator.calculateLegalActions(state)[0].contains(oddPass))
        assertFalse(simulator.isLegalAction(state, 0, oddPass))
    }

    @Test
    fun isLegalActionAcceptsPassAndEmptyPoint() {
        val simulator = GoSimulator(boardSize = 5)
        val state = simulator.initialState
        assertTrue(simulator.isLegalAction(state, 0, GoAction.pass()))
        assertTrue(simulator.isLegalAction(state, 0, GoAction.place(2, 2)))
    }

    @Test
    fun scoreOfEmptyBoardIsOnlyKomi() {
        val simulator = GoSimulator(boardSize = 5)
        val score = simulator.score(simulator.initialState)
        assertEquals(GoScore(0, 0, 0, 0, 7.0), score)
        assertEquals(0.0, score.black, 0.0)
        assertEquals(7.0, score.white, 0.0)
    }

    @Test
    fun scoreCountsStonesAndTerritoryForEachSide() {
        val simulator = GoSimulator(boardSize = 4, komi = 0.5)
        //   . X O .
        //   . X O .
        //   . X O .
        //   . X O .
        val state = walls(boardSize = 4, blackX = 1, whiteX = 2)
        val score = simulator.score(state)
        assertEquals(GoScore(4, 4, 4, 4, 0.5), score)
        assertEquals(8.0, score.black, 0.0)
        assertEquals(8.5, score.white, 0.0)
    }

    @Test
    fun scoreCountsRegionTouchingBothColorsForNeither() {
        val simulator = GoSimulator(boardSize = 5, komi = 0.0)
        //   . X . O .
        //   . X . O .
        //   . X . O .
        //   . X . O .
        //   . X . O .
        val state = walls(boardSize = 5, blackX = 1, whiteX = 3)
        assertEquals(GoScore(5, 5, 5, 5, 0.0), simulator.score(state))
    }

    @Test
    fun scoreDoesNotChangeTheState() {
        val simulator = GoSimulator(boardSize = 4)
        val state = walls(boardSize = 4, blackX = 1, whiteX = 2)
        val before = state.copy()
        simulator.score(state)
        assertEquals(before, state)
    }

    @Test
    fun finishedGameWinnerFromScoreMatchesRewards() {
        // Black 8, white 8: a tie with no komi, white wins by komi 0.5.
        assertScoreAgreesWithRewards(boardSize = 4, blackX = 1, whiteX = 2, komi = 0.0, expected = intArrayOf(0, 0))
        assertScoreAgreesWithRewards(boardSize = 4, blackX = 1, whiteX = 2, komi = 0.5, expected = intArrayOf(-1, 1))
        // Black 5 + 10, white 5 + 5 + 0.5.
        assertScoreAgreesWithRewards(boardSize = 5, blackX = 2, whiteX = 3, komi = 0.5, expected = intArrayOf(1, -1))
    }

    @Test
    fun defaultRulesAreTrompTaylor() {
        assertEquals(GoRules.TROMP_TAYLOR, GoSimulator().rules)
        assertEquals(GoRules.TROMP_TAYLOR, GoSimulator(boardSize = 5, komi = 7.5).rules)
    }

    @Test
    fun chineseRulesForbidSelfCapture() {
        val simulator = GoSimulator(boardSize = 4, rules = GoRules.CHINESE)
        val state = selfCaptureFixture()
        assertFalse(simulator.calculateLegalActions(state)[0].contains(GoAction.place(2, 1)))
        assertFalse(simulator.isLegalAction(state, 0, GoAction.place(2, 1)))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))
        }
    }

    @Test
    fun trompTaylorRulesAllowSelfCaptureAndRemoveTheGroup() {
        val simulator = GoSimulator(boardSize = 4, rules = GoRules.TROMP_TAYLOR)
        val state = selfCaptureFixture()
        assertTrue(simulator.calculateLegalActions(state)[0].contains(GoAction.place(2, 1)))
        assertTrue(simulator.isLegalAction(state, 0, GoAction.place(2, 1)))
        val next = simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))
        for ((x, y) in listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)) {
            assertEquals(GoState.LOCATION_EMPTY, next.get(x, y))
        }
        assertEquals(GoState.LOCATION_WHITE, next.get(0, 1))
        assertEquals(0, next.capturedByBlack)
    }

    @Test
    fun fillingOwnLastLibertyWhileCapturingIsLegalUnderBothRules() {
        for (rules in GoRules.entries) {
            val simulator = GoSimulator(boardSize = 4, rules = rules)
            // (2,0) is the black group's last liberty, and white (3,0)'s last liberty.
            //   . . . .
            //   . . . .
            //   O O O X
            //   X X . O
            val state = GoState(boardSize = 4)
            state.set(0, 0, GoState.LOCATION_BLACK)
            state.set(1, 0, GoState.LOCATION_BLACK)
            state.set(3, 0, GoState.LOCATION_WHITE)
            state.set(0, 1, GoState.LOCATION_WHITE)
            state.set(1, 1, GoState.LOCATION_WHITE)
            state.set(2, 1, GoState.LOCATION_WHITE)
            state.set(3, 1, GoState.LOCATION_BLACK)
            state.agentTurn = GoState.TURN_BLACK

            assertTrue("$rules", simulator.calculateLegalActions(state)[0].contains(GoAction.place(2, 0)))
            assertTrue("$rules", simulator.isLegalAction(state, 0, GoAction.place(2, 0)))
            val next = simulator.stateTransition(state, listOf(GoAction.place(2, 0), null))
            assertEquals("$rules", GoState.LOCATION_EMPTY, next.get(3, 0))
            for (x in 0..2) {
                assertEquals("$rules", GoState.LOCATION_BLACK, next.get(x, 0))
            }
            assertEquals("$rules", 1, next.capturedByBlack)
        }
    }

    @Test
    fun koAndSuperkoBehaveTheSameUnderBothRules() {
        for (rules in GoRules.entries) {
            val simulator = GoSimulator(boardSize = 4, rules = rules)
            val state = koFixture()

            // Taking the ko leaves the black stone with no liberties until the capture: legal.
            assertTrue("$rules", simulator.isLegalAction(state, 0, GoAction.place(2, 1)))
            val afterTake = simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))
            assertEquals("$rules", GoState.LOCATION_EMPTY, afterTake.get(1, 1))

            // Immediate retake repeats the earlier coloring: illegal.
            assertFalse("$rules", simulator.calculateLegalActions(afterTake)[1].contains(GoAction.place(1, 1)))
            assertFalse("$rules", simulator.isLegalAction(afterTake, 1, GoAction.place(1, 1)))
            assertThrows(IllegalActionException::class.java) {
                simulator.stateTransition(afterTake, listOf(null, GoAction.place(1, 1)))
            }

            // After a move elsewhere the position is new, so the retake is legal again.
            var later = simulator.stateTransition(afterTake, listOf(null, GoAction.place(3, 3)))
            later = simulator.stateTransition(later, listOf(GoAction.place(0, 3), null))
            assertTrue("$rules", simulator.isLegalAction(later, 1, GoAction.place(1, 1)))
        }
    }

    /**
     * One-stone ko, black to play. Black (2,1) captures white (1,1); white retaking
     * at (1,1) at once would repeat this coloring.
     *
     *   . X O .
     *   X O . O
     *   . X O .
     */
    private fun koFixture(): GoState {
        val state = GoState(boardSize = 4)
        state.set(1, 2, GoState.LOCATION_BLACK)
        state.set(2, 2, GoState.LOCATION_WHITE)
        state.set(0, 1, GoState.LOCATION_BLACK)
        state.set(1, 1, GoState.LOCATION_WHITE)
        state.set(3, 1, GoState.LOCATION_WHITE)
        state.set(1, 0, GoState.LOCATION_BLACK)
        state.set(2, 0, GoState.LOCATION_WHITE)
        state.agentTurn = GoState.TURN_BLACK
        return state
    }

    /**
     * Black to play (2,1), filling its own last eye. White keeps corner liberties, so
     * the move captures nothing and leaves the black group with no liberties.
     *
     *   . O O .
     *   O X X O
     *   O X . O
     *   . O O .
     */
    private fun selfCaptureFixture(): GoState {
        val state = GoState(boardSize = 4)
        state.set(1, 3, GoState.LOCATION_WHITE)
        state.set(2, 3, GoState.LOCATION_WHITE)
        state.set(0, 2, GoState.LOCATION_WHITE)
        state.set(1, 2, GoState.LOCATION_BLACK)
        state.set(2, 2, GoState.LOCATION_BLACK)
        state.set(3, 2, GoState.LOCATION_WHITE)
        state.set(0, 1, GoState.LOCATION_WHITE)
        state.set(1, 1, GoState.LOCATION_BLACK)
        state.set(3, 1, GoState.LOCATION_WHITE)
        state.set(1, 0, GoState.LOCATION_WHITE)
        state.set(2, 0, GoState.LOCATION_WHITE)
        state.agentTurn = GoState.TURN_BLACK
        return state
    }

    /** Plays a full column for each side, black first, then two passes. */
    private fun assertScoreAgreesWithRewards(
        boardSize: Int,
        blackX: Int,
        whiteX: Int,
        komi: Double,
        expected: IntArray
    ) {
        val simulator = GoSimulator(boardSize = boardSize, komi = komi)
        var state = simulator.initialState
        for (y in 0 until boardSize) {
            state = simulator.stateTransition(state, listOf(GoAction.place(blackX, y), null))
            state = simulator.stateTransition(state, listOf(null, GoAction.place(whiteX, y)))
        }
        state = simulator.stateTransition(state, listOf(GoAction.pass(), null))
        state = simulator.stateTransition(state, listOf(null, GoAction.pass()))
        assertTrue(simulator.calculateLegalActions(state).all { it.isEmpty() })

        val score = simulator.score(state)
        val fromScore = when {
            score.black > score.white -> intArrayOf(1, -1)
            score.white > score.black -> intArrayOf(-1, 1)
            else -> intArrayOf(0, 0)
        }
        assertArrayEquals(expected, fromScore)
        assertArrayEquals(fromScore, simulator.calculateRewards(state))
    }

    /** A board with a full black column at [blackX] and a full white column at [whiteX]. */
    private fun walls(boardSize: Int, blackX: Int, whiteX: Int): GoState {
        val state = GoState(boardSize = boardSize)
        for (y in 0 until boardSize) {
            state.set(blackX, y, GoState.LOCATION_BLACK)
            state.set(whiteX, y, GoState.LOCATION_WHITE)
        }
        return state
    }
}
