package com.castlefrog.agl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatorRequireLegalActionTest {

    private data class DummyAction(val id: Int) : Action

    @Test
    fun returnsActionWhenLegal() {
        val action = DummyAction(1)
        val result = requireLegalAction(
            actions = listOf(action, null),
            agentTurn = 0,
            legalForPlayer = setOf(action),
            state = "state"
        )
        assertEquals(action, result)
    }

    @Test
    fun throwsWhenActionsListTooShort() {
        assertThrows(IllegalActionException::class.java) {
            requireLegalAction(
                actions = emptyList(),
                agentTurn = 0,
                legalForPlayer = setOf(DummyAction(1)),
                state = "state"
            )
        }
    }

    @Test
    fun throwsWhenActionIsNull() {
        assertThrows(IllegalActionException::class.java) {
            requireLegalAction(
                actions = listOf(null, DummyAction(1)),
                agentTurn = 0,
                legalForPlayer = setOf(DummyAction(1)),
                state = "state"
            )
        }
    }

    @Test
    fun throwsWhenActionNotLegal() {
        assertThrows(IllegalActionException::class.java) {
            requireLegalAction(
                actions = listOf(DummyAction(2), null),
                agentTurn = 0,
                legalForPlayer = setOf(DummyAction(1)),
                state = "state"
            )
        }
    }

    @Test
    fun isIllegalArgumentExceptionSubclass() {
        val thrown = assertThrows(IllegalActionException::class.java) {
            requireLegalAction(
                actions = emptyList<DummyAction?>(),
                agentTurn = 0,
                legalForPlayer = emptySet(),
                state = "state"
            )
        }
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun predicateReturnsActionWhenLegal() {
        val action = DummyAction(1)
        val result = requireLegalAction(
            actions = listOf(action, null),
            agentTurn = 0,
            state = "state"
        ) { it == action }
        assertEquals(action, result)
    }

    @Test
    fun predicateIsAskedAboutTheActionForAgentTurn() {
        val asked = mutableListOf<DummyAction>()
        requireLegalAction(
            actions = listOf(null, DummyAction(2)),
            agentTurn = 1,
            state = "state"
        ) { asked.add(it); true }
        assertEquals(listOf(DummyAction(2)), asked)
    }

    @Test
    fun predicateThrowsWhenActionsListTooShort() {
        val thrown = assertThrows(IllegalActionException::class.java) {
            requireLegalAction(
                actions = listOf(DummyAction(1)),
                agentTurn = 1,
                state = "state"
            ) { true }
        }
        assertEquals("Illegal actions size, 1, expected at least 2 for state, state", thrown.message)
    }

    @Test
    fun predicateThrowsWhenActionIsNullWithoutAskingPredicate() {
        val thrown = assertThrows(IllegalActionException::class.java) {
            requireLegalAction<DummyAction>(
                actions = listOf(null, DummyAction(1)),
                agentTurn = 0,
                state = "state"
            ) { error("predicate must not be called for a null action") }
        }
        assertEquals("Illegal action, null, from state, state", thrown.message)
    }

    @Test
    fun predicateThrowsWhenActionNotLegal() {
        val thrown = assertThrows(IllegalActionException::class.java) {
            requireLegalAction(
                actions = listOf(DummyAction(2), null),
                agentTurn = 0,
                state = "state"
            ) { false }
        }
        assertEquals("Illegal action, DummyAction(id=2), from state, state", thrown.message)
    }

    @Test
    fun setAndPredicateOverloadsGiveTheSameMessages() {
        val legal = setOf(DummyAction(1))
        val cases = listOf(
            emptyList(),
            listOf<DummyAction?>(null),
            listOf<DummyAction?>(DummyAction(2))
        )
        for (actions in cases) {
            val fromSet = assertThrows(IllegalActionException::class.java) {
                requireLegalAction(actions, 0, legal, "state")
            }
            val fromPredicate = assertThrows(IllegalActionException::class.java) {
                requireLegalAction(actions, 0, "state") { it in legal }
            }
            assertEquals(fromSet.message, fromPredicate.message)
        }
    }
}
