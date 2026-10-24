package com.castlefrog.agl.domains.hex

import com.castlefrog.agl.domains.go.GoSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HexPlayoutPolicyTest {

    @Test
    fun everyPlayoutMoveIsLegalAndNullComesOnlyAtTheEnd() {
        for (pieRule in listOf(true, false)) {
            for (size in listOf(3, 5, 8)) {
                val simulator = HexSimulator(boardSize = size, pieRule = pieRule)
                repeat(20) { seed ->
                    val random = Random(seed)
                    var state = simulator.initialState
                    while (true) {
                        val actions = HexPlayoutPolicy.selectActions(state, simulator, random)
                        if (actions == null) {
                            assertTrue("pie $pieRule size $size seed $seed\n$state", simulator.isTerminalState(state))
                            break
                        }
                        val legal = simulator.calculateLegalActions(state)
                        val turn = state.agentTurn.toInt()
                        assertNull(actions[1 - turn])
                        assertTrue("${actions[turn]}\n$state", actions[turn] in legal[turn])
                        state = simulator.stateTransition(state, actions)
                    }
                }
            }
        }
    }

    @Test
    fun playsEveryEmptyCellUniformly() {
        val simulator = HexSimulator(boardSize = 3, pieRule = false)
        val state = simulator.stateTransition(simulator.initialState, listOf(HexAction(1, 1), null))
        val random = Random(4)
        val counts = HashMap<HexAction, Int>()
        repeat(8000) {
            val action = HexPlayoutPolicy.selectActions(state, simulator, random)!![1]!!
            counts[action] = (counts[action] ?: 0) + 1
        }
        assertEquals(simulator.calculateLegalActions(state)[1], counts.keys)
        assertTrue("$counts", counts.values.all { it in 800..1200 })
    }

    @Test
    fun mayTakeTheFirstStoneUnderThePieRule() {
        val simulator = HexSimulator(boardSize = 3, pieRule = true)
        val first = HexAction(1, 1)
        val state = simulator.stateTransition(simulator.initialState, listOf(first, null))
        val random = Random(2)
        val moves = (0 until 200).map { HexPlayoutPolicy.selectActions(state, simulator, random)!![1] }
        assertTrue(first in moves)
    }

    @Test
    fun returnsNullOnceAPlayerHasWon() {
        val simulator = HexSimulator(boardSize = 3, pieRule = false)
        var state = simulator.initialState
        for ((black, white) in listOf(HexAction(0, 0) to HexAction(0, 1), HexAction(1, 0) to HexAction(1, 1))) {
            state = simulator.stateTransition(state, listOf(black, null))
            state = simulator.stateTransition(state, listOf(null, white))
        }
        state = simulator.stateTransition(state, listOf(HexAction(2, 0), null))
        assertTrue(simulator.isTerminalState(state))
        assertNull(HexPlayoutPolicy.selectActions(state, simulator, Random(1)))
    }

    @Test
    fun doesNotChangeTheState() {
        val simulator = HexSimulator(boardSize = 5)
        var state = simulator.initialState
        val random = Random(3)
        repeat(10) {
            val before = state.copy()
            val actions = HexPlayoutPolicy.selectActions(state, simulator, random)!!
            assertEquals(before, state)
            state = simulator.stateTransition(state, actions)
        }
    }

    @Test
    fun rejectsOtherDomains() {
        val go = GoSimulator(boardSize = 5)
        assertThrows(IllegalArgumentException::class.java) {
            HexPlayoutPolicy.selectActions(go.initialState, go, Random(1))
        }
    }
}
