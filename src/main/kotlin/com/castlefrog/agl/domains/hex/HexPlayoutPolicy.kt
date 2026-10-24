package com.castlefrog.agl.domains.hex

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import com.castlefrog.agl.agents.PlayoutPolicy
import com.castlefrog.agl.agents.UniformRandomPlayoutPolicy
import com.castlefrog.agl.domains.AdversarialRewards
import kotlin.random.Random

/**
 * A fast uniform random [PlayoutPolicy] for [HexSimulator]: the mover plays an empty cell
 * picked uniformly, the same moves and odds as [UniformRandomPlayoutPolicy], without
 * building the set of every legal action. It checks the win once per step.
 *
 * On the second move, when the pie rule may allow taking the first stone, it defers to
 * [UniformRandomPlayoutPolicy]. It only works with a [HexSimulator] and [HexState]; any
 * other domain throws [IllegalArgumentException].
 */
object HexPlayoutPolicy : PlayoutPolicy {

    override fun <S : State<S>, A : Action> selectActions(
        state: S,
        simulator: Simulator<S, A>,
        random: Random
    ): List<A?>? {
        require(simulator is HexSimulator && state is HexState) { "HexPlayoutPolicy plays only Hex" }
        val turn = state.agentTurn.toInt()
        if (state.agentTurn == HexState.TURN_WHITE && state.nPieces == 1) {
            return UniformRandomPlayoutPolicy.selectActions(state, simulator, random)
        }
        if (!simulator.calculateRewards(state).contentEquals(AdversarialRewards.neutral())) {
            return null
        }
        val n = state.boardSize
        val empties = IntArray(n * n)
        var count = 0
        for (x in 0 until n) {
            for (y in 0 until n) {
                if (state.isLocationEmpty(x, y)) empties[count++] = x * n + y
            }
        }
        if (count == 0) return null
        val p = empties[random.nextInt(count)]
        val action = HexAction((p / n).toByte(), (p % n).toByte())
        @Suppress("UNCHECKED_CAST")
        return List(2) { if (it == turn) action else null } as List<A?>
    }
}
