package com.castlefrog.agl.agents

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import kotlin.random.Random

/**
 * Picks the moves in an [MctsAgent] playout, the simulated game played out from a new
 * tree node. A domain can supply a policy that plays more like a person than uniform
 * random moves, which makes each playout a better estimate.
 */
interface PlayoutPolicy {

    /**
     * Picks the next step of a playout from [state], or reports that it is terminal.
     *
     * The agent does not compute legal actions for playout states; the policy decides
     * how. A policy may call [Simulator.calculateLegalActions] once, or, where that is
     * costly, pick a candidate move and check it with [Simulator.isLegalAction]. It
     * must return null exactly when no player has a legal action, as
     * [Simulator.isTerminalState] defines.
     *
     * @param state the playout's current state
     * @param simulator the domain's rules
     * @param random the agent's source of randomness; use it, not another one, so a
     *        seeded agent stays reproducible
     * @return one entry per player: a legal action for each player who has one and
     *         null for each player who has none; or null if [state] is terminal
     */
    fun <S : State<S>, A : Action> selectActions(
        state: S,
        simulator: Simulator<S, A>,
        random: Random
    ): List<A?>?
}

/**
 * The default [PlayoutPolicy]: computes the legal actions once per step, and every player
 * with legal actions picks one uniformly at random.
 */
object UniformRandomPlayoutPolicy : PlayoutPolicy {
    override fun <S : State<S>, A : Action> selectActions(
        state: S,
        simulator: Simulator<S, A>,
        random: Random
    ): List<A?>? {
        val legalActions = simulator.calculateLegalActions(state)
        if (legalActions.all { it.isEmpty() }) {
            return null
        }
        return legalActions.map { if (it.isEmpty()) null else it.random(random) }
    }
}
