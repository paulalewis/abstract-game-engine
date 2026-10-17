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
    fun multiStoneSuicideIsLegal() {
        val simulator = GoSimulator(boardSize = 4)
        val state = GoState(boardSize = 4)
        // Black has a one-point eye at (2,1). White surrounds but keeps corner liberties,
        // so filling the eye kills only black (board changes → allowed by superko).
        //   . O O .
        //   O X X O
        //   O X . O
        //   . O O .
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
        val legal = simulator.calculateLegalActions(state)[0]
        assertTrue(legal.contains(GoAction.place(2, 1)))

        val next = simulator.stateTransition(state, listOf(GoAction.place(2, 1), null))
        assertEquals(GoState.LOCATION_EMPTY, next.get(1, 1))
        assertEquals(GoState.LOCATION_EMPTY, next.get(2, 1))
        assertEquals(GoState.LOCATION_EMPTY, next.get(1, 2))
        assertEquals(GoState.LOCATION_EMPTY, next.get(2, 2))
        assertEquals(GoState.LOCATION_WHITE, next.get(0, 1))
    }

    @Test
    fun positionalSuperkoForbidsImmediateKoRecapture() {
        val simulator = GoSimulator(boardSize = 4)
        val state = GoState(boardSize = 4)
        // Classic one-stone ko: white at (1,1), liberty at (2,1).
        //   . X O .
        //   X O . O
        //   . X O .
        state.set(1, 2, GoState.LOCATION_BLACK)
        state.set(2, 2, GoState.LOCATION_WHITE)
        state.set(0, 1, GoState.LOCATION_BLACK)
        state.set(1, 1, GoState.LOCATION_WHITE)
        state.set(3, 1, GoState.LOCATION_WHITE)
        state.set(1, 0, GoState.LOCATION_BLACK)
        state.set(2, 0, GoState.LOCATION_WHITE)
        state.agentTurn = GoState.TURN_BLACK

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
        val state = GoState(boardSize = 4)
        //   . X O .
        //   X O . O
        //   . X O .
        state.set(1, 2, GoState.LOCATION_BLACK)
        state.set(2, 2, GoState.LOCATION_WHITE)
        state.set(0, 1, GoState.LOCATION_BLACK)
        state.set(1, 1, GoState.LOCATION_WHITE)
        state.set(3, 1, GoState.LOCATION_WHITE)
        state.set(1, 0, GoState.LOCATION_BLACK)
        state.set(2, 0, GoState.LOCATION_WHITE)
        state.agentTurn = GoState.TURN_BLACK
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
}
