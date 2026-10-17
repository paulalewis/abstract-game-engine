package com.castlefrog.agl.domains.hex

import com.castlefrog.agl.IllegalActionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HexSimulatorTest {

    @Test
    fun stateTransitionEmptyActionsList() {
        val simulator = HexSimulator(boardSize = 5)
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, emptyList())
        }
    }

    @Test
    fun stateTransitionWrongArityWhenSecondPlayerToMove() {
        val simulator = HexSimulator(boardSize = 5)
        val state = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(null))
        }
    }

    @Test
    fun stateTransitionMove1() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        val expectedState = simulator.initialState
        expectedState.setLocation(0, 0, HexState.LOCATION_BLACK)
        expectedState.agentTurn = HexState.TURN_WHITE
        assertEquals(expectedState, state2)
    }

    @Test
    fun stateTransitionIllegalMove() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state2, listOf(HexAction(0, 0), null))
        }
    }

    @Test
    fun stateTransitionNullActionForPlayerToMove() {
        val simulator = HexSimulator(boardSize = 5)
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, listOf(null, null))
        }
    }

    @Test
    fun stateTransitionMove2SameLocation() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        val state3 = simulator.stateTransition(state2, listOf(null, HexAction(0, 0)))
        val expectedState = simulator.initialState
        expectedState.setLocation(0, 0, HexState.LOCATION_WHITE)
        expectedState.agentTurn = HexState.TURN_BLACK
        assertEquals(expectedState, state3)
    }

    @Test
    fun stateTransitionMove2DifferentLocation() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        val state3 = simulator.stateTransition(state2, listOf(null, HexAction(0, 1)))
        val expectedState = simulator.initialState
        expectedState.setLocation(0, 0, HexState.LOCATION_BLACK)
        expectedState.setLocation(0, 1, HexState.LOCATION_WHITE)
        expectedState.agentTurn = HexState.TURN_BLACK
        assertEquals(expectedState, state3)
    }

    @Test
    fun isLegalActionOccupiedCell() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        val state3 = simulator.stateTransition(state2, listOf(null, HexAction(1, 1)))
        assertIllegal(simulator, state3, 0, HexAction(1, 1))
        assertIllegal(simulator, state3, 0, HexAction(0, 0))
        assertTrue(simulator.isLegalAction(state3, 0, HexAction(2, 2)))
    }

    @Test
    fun isLegalActionOccupiedCellOnSecondMoveWithoutPieRule() {
        val simulator = HexSimulator(boardSize = 5, pieRule = false)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        assertIllegal(simulator, state2, 1, HexAction(0, 0))
        assertTrue(simulator.isLegalAction(state2, 1, HexAction(0, 1)))
    }

    @Test
    fun isLegalActionSwapOnSecondMove() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(1, 3), null))
        assertTrue(simulator.isLegalAction(state2, 1, HexAction(1, 3)))
        val state3 = simulator.stateTransition(state2, listOf(null, HexAction(1, 3)))
        val expectedState = simulator.initialState
        expectedState.setLocation(3, 1, HexState.LOCATION_WHITE)
        expectedState.agentTurn = HexState.TURN_BLACK
        assertEquals(expectedState, state3)
    }

    @Test
    fun isLegalActionSwapOnThirdMove() {
        val simulator = HexSimulator(boardSize = 5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HexAction(0, 0), null))
        val state3 = simulator.stateTransition(state2, listOf(null, HexAction(0, 1)))
        assertIllegal(simulator, state3, 0, HexAction(0, 1))
        assertIllegal(simulator, state3, 0, HexAction(0, 0))
    }

    @Test
    fun isLegalActionWrongPlayer() {
        val simulator = HexSimulator(boardSize = 5)
        val state = simulator.initialState
        assertFalse(simulator.isLegalAction(state, 1, HexAction(2, 2)))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(null, HexAction(2, 2)))
        }
        val state2 = simulator.stateTransition(state, listOf(HexAction(0, 0), null))
        assertFalse(simulator.isLegalAction(state2, 0, HexAction(2, 2)))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state2, listOf(HexAction(2, 2), null))
        }
    }

    @Test
    fun isLegalActionOffBoard() {
        val simulator = HexSimulator(boardSize = 5)
        val state = simulator.initialState
        assertIllegal(simulator, state, 0, HexAction(5, 0))
        assertIllegal(simulator, state, 0, HexAction(0, 5))
        assertIllegal(simulator, state, 0, HexAction(-1, 0))
    }

    @Test
    fun isLegalActionAfterGameIsWon() {
        val simulator = HexSimulator(boardSize = 3)
        val state = simulator.initialState
        state.setLocation(0, 1, HexState.LOCATION_BLACK)
        state.setLocation(1, 1, HexState.LOCATION_BLACK)
        state.setLocation(2, 1, HexState.LOCATION_BLACK)
        state.setLocation(0, 0, HexState.LOCATION_WHITE)
        state.setLocation(1, 0, HexState.LOCATION_WHITE)
        state.agentTurn = HexState.TURN_WHITE
        assertTrue(simulator.calculateRewards(state).contentEquals(intArrayOf(1, -1)))
        assertTrue(simulator.calculateLegalActions(state).all { it.isEmpty() })
        assertIllegal(simulator, state, 1, HexAction(2, 2))
        assertIllegal(simulator, state, 0, HexAction(2, 2))
    }

    private fun assertIllegal(simulator: HexSimulator, state: HexState, agentTurn: Int, action: HexAction) {
        assertFalse(simulator.isLegalAction(state, agentTurn, action))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, List(2) { if (it == agentTurn) action else null })
        }
    }
}
