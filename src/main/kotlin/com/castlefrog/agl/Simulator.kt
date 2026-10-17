package com.castlefrog.agl

/**
 * A simulator controls the state transitions of a given domain.
 * A simulator is associated with a domain specific state and action
 * type.
 */
interface Simulator<S : State<S>, A : Action> {
    /**
     * @return an initial state in the domain
     */
    val initialState: S

    /**
     * @param state the state from which to calculate rewards
     * @return list of rewards for each player
     */
    fun calculateRewards(state: S): IntArray

    /**
     * @param state the state from which to calculate legal actions
     * @return list of legal actions for each player
     */
    fun calculateLegalActions(state: S): List<Set<A>>

    /**
     * Whether [action] is legal for [agentTurn] in [state]. Must agree with
     * [calculateLegalActions]. The default generates every legal action; override
     * when one action can be checked without doing so.
     *
     * @param state the state to check the action from
     * @param agentTurn the player taking [action]
     * @param action the action to check
     * @return true if [action] is in `calculateLegalActions(state)[agentTurn]`
     */
    fun isLegalAction(state: S, agentTurn: Int, action: A): Boolean =
        action in calculateLegalActions(state)[agentTurn]

    /**
     * Transition from the current state to the next state
     * given a set of player actions.
     *
     * **Contract:** implementations must not mutate [state]. The input remains
     * unchanged so callers can retain pre-transition states for history, tree
     * search, and comparison. The returned value is a new state (typically via
     * [State.copy]) reflecting the result of [actions].
     *
     * @param state the state to transition from (must not be modified)
     * @param actions list of actions to be performed by each player;
     *        null entries mean that player takes no action
     * @return a new state after applying the actions; never [state] itself when
     *         a transition actually occurs
     * @throws IllegalActionException if [actions] has the wrong length for the
     *         player to move, the required action is null, or the action is not
     *         legal from [state]
     */
    fun stateTransition(state: S, actions: List<A?>): S

    /**
     * @return the number of players in the domain.
     */
    fun numberOfPlayers(): Int

    /**
     * @param state check if this state is terminal
     * @return true if no player has any legal actions from the state
     */
    fun isTerminalState(state: S): Boolean {
        val legalActions = calculateLegalActions(state)
        return (legalActions.indices).all { legalActions[it].isEmpty() }
    }
}

/**
 * Resolves the action for [agentTurn] from [actions], ensuring the list is long
 * enough and the action is legal.
 *
 * @throws IllegalActionException if the actions list is too short, the action is
 *         null, or it is not in [legalForPlayer]
 */
fun <A : Action> requireLegalAction(
    actions: List<A?>,
    agentTurn: Int,
    legalForPlayer: Set<A>,
    state: Any
): A = requireLegalAction(actions, agentTurn, state) { it in legalForPlayer }

/**
 * Resolves the action for [agentTurn] from [actions], ensuring the list is long
 * enough and the action is legal by [isLegal]. Use with [Simulator.isLegalAction]
 * to check only the action given instead of every legal action.
 *
 * @throws IllegalActionException if the actions list is too short, the action is
 *         null, or [isLegal] returns false for it
 */
fun <A : Action> requireLegalAction(
    actions: List<A?>,
    agentTurn: Int,
    state: Any,
    isLegal: (A) -> Boolean
): A {
    if (agentTurn !in actions.indices) {
        throw IllegalActionException(
            "Illegal actions size, ${actions.size}, expected at least ${agentTurn + 1} for state, $state"
        )
    }
    val action = actions[agentTurn]
    if (action === null || !isLegal(action)) {
        throw IllegalActionException("Illegal action, $action, from state, $state")
    }
    return action
}
