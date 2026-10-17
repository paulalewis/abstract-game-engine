package com.castlefrog.agl.domains.havannah

import com.castlefrog.agl.IllegalActionException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HavannahSimulatorTest {

    @Test
    fun stateTransitionEmptyActionsList() {
        val simulator = HavannahSimulator(5)
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, emptyList())
        }
    }

    @Test
    fun stateTransitionWrongArityWhenSecondPlayerToMove() {
        val simulator = HavannahSimulator(5)
        val state = simulator.stateTransition(simulator.initialState, listOf(HavannahAction(0, 0), null))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state, listOf(null))
        }
    }

    @Test
    fun stateTransitionMove1() {
        val simulator = HavannahSimulator(5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HavannahAction(0, 0), null))
        val expectedState = simulator.initialState
        expectedState.locations[0][0] = HavannahState.LOCATION_BLACK
        expectedState.agentTurn = HavannahState.TURN_WHITE
        assertEquals(expectedState, state2)
    }

    @Test
    fun stateTransitionIllegalMove() {
        val simulator = HavannahSimulator(5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HavannahAction(0, 0), null))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state2, listOf(HavannahAction(0, 0), null))
        }
    }

    @Test
    fun stateTransitionNullActionForPlayerToMove() {
        val simulator = HavannahSimulator(5)
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(simulator.initialState, listOf(null, null))
        }
    }

    @Test
    fun stateTransitionMove2SameLocationPieRuleTrue() {
        val simulator = HavannahSimulator(base = 5, pieRule = true)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HavannahAction(0, 0), null))
        val state3 = simulator.stateTransition(state2, listOf(null, HavannahAction(0, 0)))
        val expectedState = simulator.initialState
        expectedState.locations[0][0] = HavannahState.LOCATION_WHITE
        expectedState.agentTurn = HavannahState.TURN_BLACK
        assertEquals(expectedState, state3)
    }

    @Test
    fun stateTransitionMove2SameLocationPieRuleFalse() {
        val simulator = HavannahSimulator(base = 5, pieRule = false)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HavannahAction(0, 0), null))
        assertThrows(IllegalActionException::class.java) {
            simulator.stateTransition(state2, listOf(null, HavannahAction(0, 0)))
        }
    }

    @Test
    fun stateTransitionMove2DifferentLocation() {
        val simulator = HavannahSimulator(5)
        val state2 = simulator.stateTransition(simulator.initialState, listOf(HavannahAction(0, 0), null))
        val state3 = simulator.stateTransition(state2, listOf(null, HavannahAction(0, 1)))
        val expectedState = simulator.initialState
        expectedState.locations[0][0] = HavannahState.LOCATION_BLACK
        expectedState.locations[0][1] = HavannahState.LOCATION_WHITE
        expectedState.agentTurn = HavannahState.TURN_BLACK
        assertEquals(expectedState, state3)
    }

    @Test
    fun stateBlackWinsRing() {
        val locations = Array(9) { ByteArray(9) }
        locations[2][2] = HavannahState.LOCATION_BLACK
        locations[2][3] = HavannahState.LOCATION_BLACK
        locations[3][2] = HavannahState.LOCATION_BLACK
        locations[4][4] = HavannahState.LOCATION_BLACK
        locations[4][3] = HavannahState.LOCATION_BLACK
        locations[3][4] = HavannahState.LOCATION_BLACK
        locations[3][3] = HavannahState.LOCATION_WHITE
        locations[0][0] = HavannahState.LOCATION_WHITE
        locations[0][1] = HavannahState.LOCATION_WHITE
        locations[1][0] = HavannahState.LOCATION_WHITE
        locations[1][2] = HavannahState.LOCATION_WHITE
        val state = HavannahState(5, locations, HavannahState.TURN_WHITE)
        val simulator = HavannahSimulator(base = 5, pieRule = false)
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(state))
    }

    @Test
    fun stateWhiteWinsCorners() {
        val locations = Array(9) { ByteArray(9) }
        locations[2][2] = HavannahState.LOCATION_BLACK
        locations[3][2] = HavannahState.LOCATION_BLACK
        locations[4][4] = HavannahState.LOCATION_BLACK
        locations[4][3] = HavannahState.LOCATION_BLACK
        locations[3][4] = HavannahState.LOCATION_BLACK
        locations[0][0] = HavannahState.LOCATION_WHITE
        locations[0][1] = HavannahState.LOCATION_WHITE
        locations[0][2] = HavannahState.LOCATION_WHITE
        locations[0][3] = HavannahState.LOCATION_WHITE
        locations[0][4] = HavannahState.LOCATION_WHITE
        val state = HavannahState(5, locations, HavannahState.TURN_BLACK)
        val simulator = HavannahSimulator(base = 5, pieRule = false)
        assertArrayEquals(intArrayOf(-1, 1), simulator.calculateRewards(state))
    }

    @Test
    fun stateBlackWinsSides() {
        val locations = Array(9) { ByteArray(9) }
        locations[0][1] = HavannahState.LOCATION_BLACK
        locations[1][0] = HavannahState.LOCATION_BLACK
        locations[1][1] = HavannahState.LOCATION_BLACK
        locations[1][2] = HavannahState.LOCATION_BLACK
        locations[1][3] = HavannahState.LOCATION_BLACK
        locations[1][4] = HavannahState.LOCATION_BLACK
        locations[1][5] = HavannahState.LOCATION_BLACK
        locations[4][4] = HavannahState.LOCATION_WHITE
        locations[0][2] = HavannahState.LOCATION_WHITE
        locations[0][3] = HavannahState.LOCATION_WHITE
        locations[3][3] = HavannahState.LOCATION_WHITE
        val state = HavannahState(5, locations, HavannahState.TURN_WHITE)
        val simulator = HavannahSimulator(base = 5, pieRule = false)
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(state))
    }

    @Test
    fun sameSimulatorDetectsWinsOnUnrelatedStatesAfterTransition() {
        val simulator = HavannahSimulator(base = 5, pieRule = false)
        // Prime any instance-level last-move cache with a cell far from the winning groups.
        simulator.stateTransition(simulator.initialState, listOf(HavannahAction(8, 8), null))

        val ringLocations = Array(9) { ByteArray(9) }
        ringLocations[2][2] = HavannahState.LOCATION_BLACK
        ringLocations[2][3] = HavannahState.LOCATION_BLACK
        ringLocations[3][2] = HavannahState.LOCATION_BLACK
        ringLocations[4][4] = HavannahState.LOCATION_BLACK
        ringLocations[4][3] = HavannahState.LOCATION_BLACK
        ringLocations[3][4] = HavannahState.LOCATION_BLACK
        ringLocations[3][3] = HavannahState.LOCATION_WHITE
        ringLocations[0][0] = HavannahState.LOCATION_WHITE
        ringLocations[0][1] = HavannahState.LOCATION_WHITE
        ringLocations[1][0] = HavannahState.LOCATION_WHITE
        ringLocations[1][2] = HavannahState.LOCATION_WHITE
        val ringState = HavannahState(5, ringLocations, HavannahState.TURN_WHITE)
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(ringState))
        assertTrue(simulator.isTerminalState(ringState))
        assertTrue(simulator.calculateLegalActions(ringState).all { it.isEmpty() })

        val cornerLocations = Array(9) { ByteArray(9) }
        cornerLocations[2][2] = HavannahState.LOCATION_BLACK
        cornerLocations[3][2] = HavannahState.LOCATION_BLACK
        cornerLocations[4][4] = HavannahState.LOCATION_BLACK
        cornerLocations[4][3] = HavannahState.LOCATION_BLACK
        cornerLocations[3][4] = HavannahState.LOCATION_BLACK
        cornerLocations[0][0] = HavannahState.LOCATION_WHITE
        cornerLocations[0][1] = HavannahState.LOCATION_WHITE
        cornerLocations[0][2] = HavannahState.LOCATION_WHITE
        cornerLocations[0][3] = HavannahState.LOCATION_WHITE
        cornerLocations[0][4] = HavannahState.LOCATION_WHITE
        val cornerState = HavannahState(5, cornerLocations, HavannahState.TURN_BLACK)
        assertArrayEquals(intArrayOf(-1, 1), simulator.calculateRewards(cornerState))
        assertTrue(simulator.isTerminalState(cornerState))
        assertTrue(simulator.calculateLegalActions(cornerState).all { it.isEmpty() })

        val sideLocations = Array(9) { ByteArray(9) }
        sideLocations[0][1] = HavannahState.LOCATION_BLACK
        sideLocations[1][0] = HavannahState.LOCATION_BLACK
        sideLocations[1][1] = HavannahState.LOCATION_BLACK
        sideLocations[1][2] = HavannahState.LOCATION_BLACK
        sideLocations[1][3] = HavannahState.LOCATION_BLACK
        sideLocations[1][4] = HavannahState.LOCATION_BLACK
        sideLocations[1][5] = HavannahState.LOCATION_BLACK
        sideLocations[4][4] = HavannahState.LOCATION_WHITE
        sideLocations[0][2] = HavannahState.LOCATION_WHITE
        sideLocations[0][3] = HavannahState.LOCATION_WHITE
        sideLocations[3][3] = HavannahState.LOCATION_WHITE
        val sideState = HavannahState(5, sideLocations, HavannahState.TURN_WHITE)
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(sideState))
        assertTrue(simulator.isTerminalState(sideState))
        assertTrue(simulator.calculateLegalActions(sideState).all { it.isEmpty() })
    }

    @Test
    fun blackWinsRingAroundEmptyCell() {
        assertBlackRingAroundCenter(centerOwner = null)
    }

    @Test
    fun blackWinsRingAroundWhiteStone() {
        assertBlackRingAroundCenter(centerOwner = HavannahState.TURN_WHITE)
    }

    @Test
    fun blackWinsRingAroundOwnStone() {
        assertBlackRingAroundCenter(centerOwner = HavannahState.TURN_BLACK)
    }

    @Test
    fun blackWinsRingAroundMixedArea() {
        // Black rings (7, 7) black, (8, 7) white and (8, 8) empty.
        val ring = listOf(
            HavannahAction(6, 6), HavannahAction(6, 7), HavannahAction(7, 6), HavannahAction(7, 8),
            HavannahAction(8, 6), HavannahAction(9, 7), HavannahAction(9, 8), HavannahAction(8, 9),
            HavannahAction(9, 9)
        )
        val blackMoves = listOf(HavannahAction(7, 7)) + ring
        val whiteMoves = listOf(HavannahAction(8, 7)) + farWhiteMoves(6) +
            listOf(HavannahAction(1, 1), HavannahAction(1, 2))
        assertBlackWinsOnLastMove(blackMoves, whiteMoves)
    }

    @Test
    fun blackWinsRingAroundTwoOwnStones() {
        val locations = Array(15) { ByteArray(15) }
        val cells = listOf(
            7 to 7, 8 to 7,
            6 to 6, 6 to 7, 7 to 6, 7 to 8, 8 to 6, 8 to 8, 9 to 7, 9 to 8
        )
        for ((x, y) in cells) {
            locations[x][y] = HavannahState.LOCATION_BLACK
        }
        val state = HavannahState(8, locations, HavannahState.TURN_WHITE)
        val simulator = HavannahSimulator(base = 8, pieRule = false)
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(state))
    }

    @Test
    fun ownStoneRingOnlyCountsForPlayerWhoJustMoved() {
        val locations = Array(15) { ByteArray(15) }
        val cells = listOf(7 to 7, 6 to 6, 6 to 7, 7 to 6, 7 to 8, 8 to 7, 8 to 8)
        for ((x, y) in cells) {
            locations[x][y] = HavannahState.LOCATION_WHITE
        }
        val simulator = HavannahSimulator(base = 8, pieRule = false)
        assertArrayEquals(
            intArrayOf(-1, 1),
            simulator.calculateRewards(HavannahState(8, locations, HavannahState.TURN_BLACK))
        )
        assertArrayEquals(
            intArrayOf(0, 0),
            simulator.calculateRewards(HavannahState(8, locations, HavannahState.TURN_WHITE))
        )
    }

    @Test
    fun cornerStoneWithAllNeighboursOwnIsNotRing() {
        val locations = Array(15) { ByteArray(15) }
        val cells = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
        for ((x, y) in cells) {
            locations[x][y] = HavannahState.LOCATION_BLACK
        }
        val state = HavannahState(8, locations, HavannahState.TURN_WHITE)
        val simulator = HavannahSimulator(base = 8, pieRule = false)
        assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(state))
    }

    /**
     * Base 8, pie rule off. Black rings (7, 7) with six stones while white plays along the x = 0
     * edge. [centerOwner] is who takes (7, 7) first, or null to leave it empty.
     */
    private fun assertBlackRingAroundCenter(centerOwner: Byte?) {
        val ring = listOf(
            HavannahAction(6, 6), HavannahAction(6, 7), HavannahAction(7, 6),
            HavannahAction(7, 8), HavannahAction(8, 7), HavannahAction(8, 8)
        )
        val center = HavannahAction(7, 7)
        val (blackMoves, whiteMoves) = when (centerOwner) {
            HavannahState.TURN_BLACK -> (listOf(center) + ring) to farWhiteMoves(6)
            HavannahState.TURN_WHITE -> ring to (listOf(center) + farWhiteMoves(4))
            else -> ring to farWhiteMoves(5)
        }
        assertBlackWinsOnLastMove(blackMoves, whiteMoves)
    }

    /** White stones along the x = 0 edge, away from both corners. */
    private fun farWhiteMoves(n: Int): List<HavannahAction> = (1..n).map { HavannahAction(0, it.toByte()) }

    /** Alternates [blackMoves] and [whiteMoves] from the start; only black's last move wins. */
    private fun assertBlackWinsOnLastMove(blackMoves: List<HavannahAction>, whiteMoves: List<HavannahAction>) {
        assertEquals(blackMoves.size - 1, whiteMoves.size)
        val simulator = HavannahSimulator(base = 8, pieRule = false)
        var state = simulator.initialState
        for (i in blackMoves.indices) {
            assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(state))
            state = simulator.stateTransition(state, listOf(blackMoves[i], null))
            if (i < whiteMoves.size) {
                assertArrayEquals(intArrayOf(0, 0), simulator.calculateRewards(state))
                state = simulator.stateTransition(state, listOf(null, whiteMoves[i]))
            }
        }
        assertArrayEquals(intArrayOf(1, -1), simulator.calculateRewards(state))
    }
}
