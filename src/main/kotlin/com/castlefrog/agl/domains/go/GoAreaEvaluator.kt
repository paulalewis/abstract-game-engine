package com.castlefrog.agl.domains.go

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import com.castlefrog.agl.agents.StateEvaluator

/**
 * A [StateEvaluator] for [GoSimulator] that scores a playout stopped at the cap by area,
 * with [GoSimulator.score], instead of as a draw: 1 to the side ahead and -1 to the
 * other, 0 each on a tie. Before the cap it lets the playout go on.
 *
 * It only works with a [GoSimulator] and [GoState]; any other domain throws
 * [IllegalArgumentException].
 */
object GoAreaEvaluator : StateEvaluator {

    override fun <S : State<S>, A : Action> evaluate(
        state: S,
        simulator: Simulator<S, A>,
        playoutLength: Int,
        atCap: Boolean
    ): DoubleArray? {
        require(simulator is GoSimulator && state is GoState) { "GoAreaEvaluator scores only Go" }
        if (!atCap) return null
        val score = simulator.score(state)
        return when {
            score.black > score.white -> doubleArrayOf(1.0, -1.0)
            score.white > score.black -> doubleArrayOf(-1.0, 1.0)
            else -> doubleArrayOf(0.0, 0.0)
        }
    }
}
