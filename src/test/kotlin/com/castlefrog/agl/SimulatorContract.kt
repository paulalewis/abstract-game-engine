package com.castlefrog.agl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import kotlin.random.Random

/**
 * Shared contract checks for [Simulator] / [State] across domains.
 *
 * Asserts the library-wide rules:
 * - [Simulator.stateTransition] never mutates its input
 * - [State.copy] is a deep copy sufficient for isolation
 * - [Simulator.isLegalAction] agrees with [Simulator.calculateLegalActions]
 */
object SimulatorContract {

    /**
     * Builds an actions list of length [Simulator.numberOfPlayers] with a single
     * legal action for the first player that has moves (others null).
     */
    fun <S : State<S>, A : Action> firstLegalActions(
        simulator: Simulator<S, A>,
        state: S
    ): List<A?> {
        val legalActions = simulator.calculateLegalActions(state)
        val playerId = legalActions.indices.firstOrNull { legalActions[it].isNotEmpty() }
            ?: error("no legal actions from state (terminal?): $state")
        val action = legalActions[playerId].first()
        return List(simulator.numberOfPlayers()) { if (it == playerId) action else null }
    }

    /**
     * [Simulator.stateTransition] must leave [state] unchanged, return a different
     * instance, and produce content that differs from the input after a real move.
     */
    fun <S : State<S>, A : Action> assertStateTransitionDoesNotMutateInput(
        simulator: Simulator<S, A>,
        state: S = simulator.initialState,
        actions: List<A?> = firstLegalActions(simulator, state)
    ) {
        val snapshot = state.copy()
        val after = simulator.stateTransition(state, actions)
        assertEquals(
            "stateTransition must not mutate its input (content)",
            snapshot,
            state
        )
        assertNotSame(
            "stateTransition must return a different instance than the input",
            state,
            after
        )
        assertNotEquals(
            "stateTransition must apply the action (result differs from input)",
            state,
            after
        )
    }

    /**
     * [State.copy] must equal the original, not be the same instance, and not share
     * nested mutable structure: mutating the copy must not change the original.
     *
     * @param mutateCopy mutates nested fields on a state in a way that affects equality
     */
    fun <S : State<S>> assertCopyIsDeep(
        state: S,
        mutateCopy: (S) -> Unit
    ) {
        val copy = state.copy()
        assertEquals("copy must equal original", state, copy)
        assertNotSame("copy must be a distinct instance", state, copy)

        mutateCopy(copy)
        assertNotEquals(
            "mutating the copy must not alter the original (deep copy isolation)",
            state,
            copy
        )
        // Control: a second copy of the still-pristine original matches it.
        assertEquals(
            "original must remain equal to a fresh copy after the other copy was mutated",
            state,
            state.copy()
        )
    }

    /**
     * [Simulator.isLegalAction] must agree with [Simulator.calculateLegalActions] on the
     * states of [playouts] random playouts from [Simulator.initialState]: every legal
     * action passes, and a sample of illegal actions fails. Illegal candidates are
     * actions legal for some player somewhere in the playouts but not for this player
     * in this state (occupied points, moves out of turn, moves after the game ends).
     */
    fun <S : State<S>, A : Action> assertIsLegalActionAgreesWithLegalActions(
        simulator: Simulator<S, A>,
        random: Random = Random(0),
        playouts: Int = 3,
        maxSteps: Int = 80,
        illegalSamplesPerPlayer: Int = 10
    ) {
        val states = ArrayList<S>()
        val seenActions = LinkedHashSet<A>()
        repeat(playouts) {
            var state = simulator.initialState
            for (step in 0..maxSteps) {
                states.add(state)
                val legalActions = simulator.calculateLegalActions(state)
                legalActions.forEach { seenActions.addAll(it) }
                val playerId = legalActions.indices.firstOrNull { legalActions[it].isNotEmpty() } ?: break
                val action = legalActions[playerId].random(random)
                state = simulator.stateTransition(
                    state,
                    List(simulator.numberOfPlayers()) { if (it == playerId) action else null }
                )
            }
        }
        for (state in states) {
            val legalActions = simulator.calculateLegalActions(state)
            for (playerId in 0 until simulator.numberOfPlayers()) {
                val legal = legalActions[playerId]
                for (action in legal) {
                    assertTrue(
                        "isLegalAction must accept legal action $action for player $playerId from state $state",
                        simulator.isLegalAction(state, playerId, action)
                    )
                }
                val illegal = seenActions.filter { it !in legal }.shuffled(random).take(illegalSamplesPerPlayer)
                for (action in illegal) {
                    assertFalse(
                        "isLegalAction must reject illegal action $action for player $playerId from state $state",
                        simulator.isLegalAction(state, playerId, action)
                    )
                }
            }
        }
    }

    /**
     * Runs transition non-mutation, deep-copy isolation, and [Simulator.isLegalAction]
     * agreement for a domain fixture.
     */
    fun <S : State<S>, A : Action> assertDomainContracts(
        simulator: Simulator<S, A>,
        mutateNested: (S) -> Unit,
        state: S = simulator.initialState
    ) {
        assertTrue(
            "fixture state should have at least one legal action",
            simulator.calculateLegalActions(state).any { it.isNotEmpty() }
        )
        assertStateTransitionDoesNotMutateInput(simulator, state)
        assertCopyIsDeep(state, mutateNested)
        assertIsLegalActionAgreesWithLegalActions(simulator)
    }
}
