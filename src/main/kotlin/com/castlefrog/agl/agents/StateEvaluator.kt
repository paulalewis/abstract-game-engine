package com.castlefrog.agl.agents

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State

/**
 * Estimates the outcome of an unfinished [MctsAgent] playout so it can stop early.
 */
interface StateEvaluator {

    /**
     * Called before each playout step, including the first. The agent does not test
     * [state] for terminality first, to save computing legal actions, so [state] may be
     * terminal; an evaluator that cares can check, or return null and let the playout
     * policy find the end.
     *
     * @param state the playout's current state
     * @param simulator the domain's rules
     * @param playoutLength steps played in this playout so far
     * @param atCap true when the playout has reached [MctsConfig.maxPlayoutLength] and
     *        will not continue; returning null then scores the playout as neutral
     * @return the estimated reward for each player, on the same scale as
     *         [Simulator.calculateRewards], to end the playout here; or null to keep
     *         playing
     */
    fun <S : State<S>, A : Action> evaluate(
        state: S,
        simulator: Simulator<S, A>,
        playoutLength: Int,
        atCap: Boolean
    ): DoubleArray?
}
